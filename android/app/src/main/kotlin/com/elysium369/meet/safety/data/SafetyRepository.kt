package com.elysium369.meet.safety.data

import android.content.Context
import androidx.room.withTransaction
import com.elysium369.meet.data.local.MeetDatabase
import com.elysium369.meet.identity.ActivePrincipalKernel
import com.elysium369.meet.safety.crypto.SafetyDigest
import com.elysium369.meet.safety.crypto.SafetyPayloadCipher
import com.elysium369.meet.safety.data.local.SafetyCommandOutboxDao
import com.elysium369.meet.safety.data.local.SafetyCommandOutboxEntity
import com.elysium369.meet.safety.data.local.SafetyLocalPayloadEntity
import com.elysium369.meet.safety.data.local.SafetyPayloadDao
import com.elysium369.meet.safety.data.local.SafetyReportDao
import com.elysium369.meet.safety.data.local.SafetyReportEntity
import com.elysium369.meet.safety.domain.CreateSafetyReportPayload
import com.elysium369.meet.safety.domain.SafetyCommandType
import com.elysium369.meet.safety.domain.SafetyOutboxStatus
import com.elysium369.meet.safety.work.SafetyCommandScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafetyRepository @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val database: MeetDatabase,
    private val reportDao: SafetyReportDao,
    private val payloadDao: SafetyPayloadDao,
    private val outboxDao: SafetyCommandOutboxDao,
    private val principalKernel: ActivePrincipalKernel,
    private val cipher: SafetyPayloadCipher,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    suspend fun createReport(
        payload: CreateSafetyReportPayload,
    ): String {
        validate(payload)

        val principal = principalKernel.current()
        require(principal.canSyncToCloud) {
            "SAFETY_AUTHENTICATED_PRINCIPAL_REQUIRED"
        }

        val reportId = UUID.randomUUID().toString()
        val payloadId = UUID.randomUUID().toString()
        val idempotencyKey = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val canonical = json.encodeToString(CreateSafetyReportPayload.serializer(), payload)
            .toByteArray(Charsets.UTF_8)

        val aad = "safety-report:v1:${principal.id}:$reportId:$payloadId".toByteArray(Charsets.UTF_8)
        val encrypted = cipher.encryptAead(plaintext = canonical, associatedData = aad)
        val digest = SafetyDigest.reportPayload(canonical)

        try {
            database.withTransaction {
                payloadDao.insertOrThrow(
                    SafetyLocalPayloadEntity(
                        payloadId = payloadId,
                        ciphertext = encrypted.toWire(),
                        sha256 = digest,
                        createdAt = now,
                        redactedAt = null,
                    ),
                )

                reportDao.insertOrThrow(
                    SafetyReportEntity(
                        reportId = reportId,
                        ownerUserId = principal.id,
                        category = payload.category.name,
                        payloadId = payloadId,
                        occurredAt = null,
                        localState = "LOCAL_ONLY",
                        serverState = null,
                        serverVersion = 0,
                        syncState = "QUEUED",
                        createdAt = now,
                        updatedAt = now,
                    ),
                )

                outboxDao.insertOrThrow(
                    SafetyCommandOutboxEntity(
                        idempotencyKey = idempotencyKey,
                        aggregateId = reportId,
                        actorSessionUserId = principal.id,
                        commandType = SafetyCommandType.CREATE_REPORT.name,
                        expectedVersion = 0,
                        payloadVersion = 1,
                        payloadId = payloadId,
                        clientPayloadSha256 = digest,
                        status = SafetyOutboxStatus.PENDING.name,
                        attemptCount = 0,
                        nextAttemptAt = now,
                        leaseStartedAt = null,
                        lastErrorCode = null,
                        lastErrorMessage = null,
                        correlationId = null,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        }

        SafetyCommandScheduler.enqueueNow(context)

        return reportId
    }

    fun observeMyReports(): Flow<List<SafetyReportEntity>> {
        val principal = principalKernel.current()
        return reportDao.observeByUser(principal.id)
    }

    suspend fun listReports(): List<SafetyReportEntity> {
        val principal = principalKernel.current()
        return reportDao.listByUser(principal.id)
    }

    suspend fun pendingCount(): Int {
        val principal = principalKernel.current()
        return outboxDao.pendingCount(principal.id)
    }

    suspend fun totalReportCount(): Int {
        val principal = principalKernel.current()
        return reportDao.countByUser(principal.id)
    }

    private fun validate(payload: CreateSafetyReportPayload) {
        require(payload.narrative.trim().length in 10..10_000) {
            "SAFETY_INVALID_NARRATIVE_LENGTH"
        }
        payload.latitude?.let {
            require(it in -90.0..90.0) { "SAFETY_INVALID_LATITUDE" }
        }
        payload.longitude?.let {
            require(it in -180.0..180.0) { "SAFETY_INVALID_LONGITUDE" }
        }
        require((payload.latitude == null) == (payload.longitude == null)) {
            "SAFETY_PARTIAL_LOCATION"
        }
    }
}
