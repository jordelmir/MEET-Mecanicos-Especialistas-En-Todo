package com.elysium369.meet.safety.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.elysium369.meet.safety.data.local.SafetyCommandOutboxDao
import com.elysium369.meet.safety.data.local.SafetyPayloadDao
import com.elysium369.meet.safety.data.local.SafetyReportDao
import com.elysium369.meet.safety.data.remote.SafetyCommandGateway
import com.elysium369.meet.safety.crypto.AeadBlob
import com.elysium369.meet.safety.crypto.SafetyDigest
import com.elysium369.meet.safety.crypto.SafetyPayloadAad
import com.elysium369.meet.safety.crypto.SafetyPayloadCipher
import com.elysium369.meet.safety.domain.SafetyGatewayResult
import com.elysium369.meet.safety.domain.SafetyRetryPolicy
import com.elysium369.meet.data.remote.SupabaseModule
import com.elysium369.meet.observability.MeetTelemetry
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.CancellationException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SafetyCommandSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val outboxDao: SafetyCommandOutboxDao,
    private val payloadDao: SafetyPayloadDao,
    private val reportDao: SafetyReportDao,
    private val gateway: SafetyCommandGateway,
    private val cipher: SafetyPayloadCipher,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()

        outboxDao.recoverStaleLeases(
            staleBefore = now - STALE_LEASE_MS,
            now = now,
        )

        val currentUserId = try {
            SupabaseModule.client.auth.currentUserOrNull()?.id
        } catch (_: Exception) {
            null
        }

        if (currentUserId == null) {
            SafetyCommandScheduler.schedule(applicationContext, delayMs = AUTH_RETRY_MS)
            return Result.success()
        }

        val commands = outboxDao.acquireBatch(now = now, limit = BATCH_SIZE, owner = currentUserId)

        for (entity in commands) {
            if (SupabaseModule.client.auth.currentUserOrNull()?.id != entity.actorSessionUserId) {
                finishRetry(entity.idempotencyKey, entity.attemptCount, "AUTH_SESSION_CHANGED", null, null)
                continue
            }
            val isReportCommand = entity.commandType == com.elysium369.meet.safety.domain.SafetyCommandType.CREATE_REPORT.name
            if (isReportCommand) reportDao.markSyncing(entity.aggregateId, now)

            val payloadEntity = payloadDao.get(entity.payloadId)
            if (payloadEntity == null) {
                deadLetter(
                    entity = entity,
                    code = "PAYLOAD_MISSING",
                    message = "Encrypted command payload not found",
                )
                continue
            }

            val plaintext = try {
                cipher.decryptAead(
                    blob = AeadBlob.fromWire(
                        payloadEntity.ciphertext,
                    ),
                    associatedData = SafetyPayloadAad.report(
                        principalId = entity.actorSessionUserId,
                        reportId = entity.aggregateId,
                        payloadId = entity.payloadId,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                deadLetter(
                    entity = entity,
                    code = "PAYLOAD_DECRYPT_FAILED",
                    message = error.message?.take(200) ?: "Authenticated decryption failed",
                )
                continue
            }

            if (
                payloadEntity.sha256 != entity.clientPayloadSha256 ||
                !SafetyDigest.verify(plaintext, payloadEntity.sha256)
            ) {
                deadLetter(
                    entity = entity,
                    code = "PAYLOAD_DIGEST_MISMATCH",
                    message = "Local encrypted payload failed integrity verification",
                )
                continue
            }

            val payloadJson = plaintext.decodeToString()

            when (val result = gateway.execute(entity, payloadJson)) {
                is SafetyGatewayResult.Accepted -> {
                    if (isReportCommand) {
                        reportDao.applyServerAcknowledgement(
                            reportId = entity.aggregateId,
                            serverState = result.state,
                            serverVersion = result.serverVersion,
                            now = System.currentTimeMillis(),
                        )
                    }

                    val ackResult = outboxDao.acknowledge(
                        key = entity.idempotencyKey,
                        correlationId = result.correlationId,
                        now = System.currentTimeMillis(),
                    )
                    check(ackResult == 1) {
                        "ACK failed for ${entity.idempotencyKey}: expected 1 row, got $ackResult"
                    }
                    // Privacy-safe telemetry: no report IDs, GPS, or narrative content.
                    MeetTelemetry.event("safety.command.synced", mapOf(
                        "commandType" to entity.commandType,
                        "attemptCount" to entity.attemptCount,
                    ))
                }

                is SafetyGatewayResult.Rejected -> {
                    if (result.retryable && entity.attemptCount < MAX_ATTEMPTS) {
                        if (isReportCommand) reportDao.markQueued(entity.aggregateId, System.currentTimeMillis())
                        finishRetry(entity.idempotencyKey, entity.attemptCount, result.code, result.message, result.correlationId)
                    } else {
                                deadLetter(
                            entity = entity,
                            code = result.code,
                            message = result.message,
                            correlationId = result.correlationId,
                        )
                    }
                }

                is SafetyGatewayResult.TransportFailure -> {
                    if (entity.attemptCount >= MAX_ATTEMPTS) {
                                deadLetter(
                            entity = entity,
                            code = result.code,
                            message = result.message,
                        )
                    } else {
                        if (isReportCommand) reportDao.markQueued(entity.aggregateId, System.currentTimeMillis())
                        finishRetry(entity.idempotencyKey, entity.attemptCount, result.code, result.message, null)
                    }
                }
            }
        }

        scheduleEarliestRetry(currentUserId)

        return Result.success()
    }

    private suspend fun deadLetter(
        entity: com.elysium369.meet.safety.data.local.SafetyCommandOutboxEntity,
        code: String,
        message: String?,
        correlationId: String? = null,
    ) {
        val now = System.currentTimeMillis()

        outboxDao.deadLetter(
            key = entity.idempotencyKey,
            code = code,
            message = message,
            correlationId = correlationId,
            now = now,
        )

        if (entity.commandType == com.elysium369.meet.safety.domain.SafetyCommandType.CREATE_REPORT.name) {
            reportDao.markFailed(reportId = entity.aggregateId, now = now)
        }

        // Privacy-safe: only failure code + command type, never report content or GPS.
        MeetTelemetry.event("safety.command.dead_letter", mapOf(
            "commandType" to entity.commandType,
            "failureCode" to code,
            "attemptCount" to entity.attemptCount,
        ))
    }

    private suspend fun finishRetry(
        key: String,
        attemptCount: Int,
        code: String,
        message: String?,
        correlationId: String?,
    ) {
        val delay = SafetyRetryPolicy.delayMillis(
            attempt = attemptCount,
            idempotencyKey = key,
        )
        outboxDao.retry(
            key = key,
            nextAttemptAt = System.currentTimeMillis() + delay,
            code = code,
            message = message,
            correlationId = correlationId,
            now = System.currentTimeMillis(),
        )
    }

    private suspend fun scheduleEarliestRetry(owner: String) {
        val next = outboxDao.earliestRetryAt(owner) ?: return
        val delayMs = (next - System.currentTimeMillis()).coerceAtLeast(0)
        SafetyCommandScheduler.schedule(applicationContext, delayMs = delayMs)
    }

    companion object {
        private const val BATCH_SIZE = 10
        private const val MAX_ATTEMPTS = 12
        private const val STALE_LEASE_MS = 5 * 60_000L
        private const val AUTH_RETRY_MS = 60_000L
    }
}
