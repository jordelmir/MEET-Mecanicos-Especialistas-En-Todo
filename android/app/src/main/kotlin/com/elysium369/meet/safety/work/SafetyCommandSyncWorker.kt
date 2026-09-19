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
import com.elysium369.meet.safety.crypto.SafetyPayloadCipher
import com.elysium369.meet.safety.domain.SafetyFailure
import com.elysium369.meet.safety.domain.SafetyGatewayResult
import com.elysium369.meet.safety.domain.SafetyRetryPolicy
import com.elysium369.meet.data.remote.SupabaseModule
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.CancellationException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

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

        val commands = outboxDao.acquireBatch(now = now, limit = BATCH_SIZE)

        for (entity in commands) {
            if (entity.actorSessionUserId != currentUserId) {
                outboxDao.deadLetter(
                    key = entity.idempotencyKey,
                    code = "AUTH_SESSION_MISMATCH",
                    message = "Command belongs to another authenticated principal",
                    correlationId = null,
                    now = System.currentTimeMillis(),
                )
                continue
            }

            val payloadEntity = payloadDao.get(entity.payloadId)
            if (payloadEntity == null) {
                outboxDao.deadLetter(
                    key = entity.idempotencyKey,
                    code = "PAYLOAD_MISSING",
                    message = "Encrypted command payload not found",
                    correlationId = null,
                    now = System.currentTimeMillis(),
                )
                continue
            }

            val payloadJson = try {
                cipher.decrypt(payloadEntity.ciphertext).decodeToString()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                outboxDao.deadLetter(
                    key = entity.idempotencyKey,
                    code = "PAYLOAD_DECRYPT_FAILED",
                    message = error.message?.take(200) ?: "Decryption failed",
                    correlationId = null,
                    now = System.currentTimeMillis(),
                )
                continue
            }

            when (val result = gateway.execute(entity, payloadJson)) {
                is SafetyGatewayResult.Accepted -> {
                    reportDao.applyServerAcknowledgement(
                        reportId = entity.aggregateId,
                        serverState = result.state,
                        serverVersion = result.serverVersion,
                        now = System.currentTimeMillis(),
                    )

                    val ackResult = outboxDao.acknowledge(
                        key = entity.idempotencyKey,
                        correlationId = result.correlationId,
                        now = System.currentTimeMillis(),
                    )
                    check(ackResult == 1) {
                        "ACK failed for ${entity.idempotencyKey}: expected 1 row, got $ackResult"
                    }
                }

                is SafetyGatewayResult.Rejected -> {
                    if (result.retryable && entity.attemptCount < MAX_ATTEMPTS) {
                        finishRetry(entity.idempotencyKey, entity.attemptCount, result.code, result.message, result.correlationId)
                    } else {
                        outboxDao.deadLetter(
                            key = entity.idempotencyKey,
                            code = result.code,
                            message = result.message,
                            correlationId = result.correlationId,
                            now = System.currentTimeMillis(),
                        )
                    }
                }

                is SafetyGatewayResult.TransportFailure -> {
                    if (entity.attemptCount >= MAX_ATTEMPTS) {
                        outboxDao.deadLetter(
                            key = entity.idempotencyKey,
                            code = result.code,
                            message = result.message,
                            correlationId = null,
                            now = System.currentTimeMillis(),
                        )
                    } else {
                        finishRetry(entity.idempotencyKey, entity.attemptCount, result.code, result.message, null)
                    }
                }
            }
        }

        scheduleEarliestRetry()

        return Result.success()
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

    private suspend fun scheduleEarliestRetry() {
        val next = outboxDao.earliestRetryAt() ?: return
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
