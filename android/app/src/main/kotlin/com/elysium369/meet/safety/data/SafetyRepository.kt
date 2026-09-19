package com.elysium369.meet.safety.data

import com.elysium369.meet.safety.data.local.SafetyCommandOutboxDao
import com.elysium369.meet.safety.data.local.SafetyPayloadDao
import com.elysium369.meet.safety.data.local.SafetyReportDao
import com.elysium369.meet.safety.crypto.SafetyDigest
import com.elysium369.meet.safety.crypto.SafetyPayloadCipher
import com.elysium369.meet.safety.domain.SafetyCommandType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafetyRepository @Inject constructor(
    private val reportDao: SafetyReportDao,
    private val payloadDao: SafetyPayloadDao,
    private val outboxDao: SafetyCommandOutboxDao,
    private val cipher: SafetyPayloadCipher,
) {
    suspend fun createReport(
        ownerUserId: String,
        category: String,
        payloadPlaintext: ByteArray,
        occurredAt: Long?,
    ): Result<String> {
        return try {
            val now = System.currentTimeMillis()
            val reportId = UUID.randomUUID().toString()
            val payloadId = UUID.randomUUID().toString()
            val idempotencyKey = UUID.randomUUID().toString()

            val ciphertext = cipher.encrypt(payloadPlaintext)
            val clientSha256 = SafetyDigest.reportPayload(payloadPlaintext)

            payloadDao.insert(
                payloadId = payloadId,
                ciphertext = ciphertext,
                sha256 = clientSha256,
                now = now,
            )

            reportDao.insert(
                reportId = reportId,
                ownerUserId = ownerUserId,
                category = category,
                payloadId = payloadId,
                occurredAt = occurredAt,
                localState = "DRAFT",
                now = now,
            )

            outboxDao.insert(
                idempotencyKey = idempotencyKey,
                aggregateId = reportId,
                actorUserId = ownerUserId,
                commandType = SafetyCommandType.CREATE_REPORT.name,
                expectedVersion = 0,
                payloadVersion = 1,
                payloadId = payloadId,
                clientSha256 = clientSha256,
                now = now,
            )

            Result.success(reportId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listReports(userId: String) = reportDao.listByUser(userId)

    suspend fun getReport(reportId: String) = reportDao.get(reportId)

    suspend fun pendingCount(userId: String) = outboxDao.pendingCount(userId)
}
