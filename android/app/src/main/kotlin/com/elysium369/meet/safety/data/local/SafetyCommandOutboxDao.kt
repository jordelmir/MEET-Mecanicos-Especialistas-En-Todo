package com.elysium369.meet.safety.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface SafetyCommandOutboxDao {

    @Query(
        """
        UPDATE safety_command_outbox
        SET status = 'RETRYABLE',
            leaseStartedAt = NULL,
            nextAttemptAt = :now,
            updatedAt = :now,
            lastErrorCode = 'STALE_LEASE_RECOVERED'
        WHERE status = 'IN_FLIGHT'
          AND leaseStartedAt IS NOT NULL
          AND leaseStartedAt < :staleBefore
        """
    )
    suspend fun recoverStaleLeases(staleBefore: Long, now: Long): Int

    @Query(
        """
        SELECT * FROM safety_command_outbox
        WHERE status IN ('PENDING','RETRYABLE')
          AND nextAttemptAt <= :now
        ORDER BY createdAt
        LIMIT :limit
        """
    )
    suspend fun ready(now: Long, limit: Int): List<SafetyCommandOutboxEntity>

    @Query(
        """
        UPDATE safety_command_outbox
        SET status='IN_FLIGHT',
            attemptCount=attemptCount+1,
            leaseStartedAt=:now,
            updatedAt=:now
        WHERE idempotencyKey=:key
          AND status IN ('PENDING','RETRYABLE')
          AND nextAttemptAt <= :now
        """
    )
    suspend fun acquire(key: String, now: Long): Int

    @Transaction
    suspend fun acquireBatch(now: Long, limit: Int): List<SafetyCommandOutboxEntity> {
        val result = mutableListOf<SafetyCommandOutboxEntity>()
        for (candidate in ready(now, limit)) {
            if (acquire(candidate.idempotencyKey, now) == 1) {
                get(candidate.idempotencyKey)?.let(result::add)
            }
        }
        return result
    }

    @Query(
        """
        SELECT * FROM safety_command_outbox
        WHERE idempotencyKey=:key
        """
    )
    suspend fun get(key: String): SafetyCommandOutboxEntity?

    @Query(
        """
        UPDATE safety_command_outbox
        SET status='ACKNOWLEDGED',
            leaseStartedAt=NULL,
            correlationId=:correlationId,
            lastErrorCode=NULL,
            lastErrorMessage=NULL,
            updatedAt=:now
        WHERE idempotencyKey=:key
          AND status='IN_FLIGHT'
        """
    )
    suspend fun acknowledge(key: String, correlationId: String?, now: Long): Int

    @Query(
        """
        UPDATE safety_command_outbox
        SET status='RETRYABLE',
            leaseStartedAt=NULL,
            nextAttemptAt=:nextAttemptAt,
            lastErrorCode=:code,
            lastErrorMessage=:message,
            correlationId=:correlationId,
            updatedAt=:now
        WHERE idempotencyKey=:key
          AND status='IN_FLIGHT'
        """
    )
    suspend fun retry(
        key: String,
        nextAttemptAt: Long,
        code: String,
        message: String?,
        correlationId: String?,
        now: Long,
    ): Int

    @Query(
        """
        UPDATE safety_command_outbox
        SET status='DEAD_LETTER',
            leaseStartedAt=NULL,
            nextAttemptAt=9223372036854775807,
            lastErrorCode=:code,
            lastErrorMessage=:message,
            correlationId=:correlationId,
            updatedAt=:now
        WHERE idempotencyKey=:key
        """
    )
    suspend fun deadLetter(
        key: String,
        code: String,
        message: String?,
        correlationId: String?,
        now: Long,
    ): Int

    @Query(
        """
        SELECT MIN(nextAttemptAt)
        FROM safety_command_outbox
        WHERE status='RETRYABLE'
        """
    )
    suspend fun earliestRetryAt(): Long?

    @Query(
        """
        SELECT COUNT(*) FROM safety_command_outbox
        WHERE status IN ('PENDING','RETRYABLE','IN_FLIGHT')
          AND actorSessionUserId=:userId
        """
    )
    suspend fun pendingCount(userId: String): Int

    @Query(
        """
        INSERT OR IGNORE INTO safety_command_outbox
        (idempotencyKey, aggregateId, actorSessionUserId, commandType,
         expectedVersion, payloadVersion, payloadId, clientPayloadSha256,
         status, attemptCount, nextAttemptAt, leaseStartedAt,
         lastErrorCode, lastErrorMessage, correlationId, createdAt, updatedAt)
        VALUES
        (:idempotencyKey, :aggregateId, :actorUserId, :commandType,
         :expectedVersion, :payloadVersion, :payloadId, :clientSha256,
         'PENDING', 0, :now, NULL,
         NULL, NULL, NULL, :now, :now)
        """
    )
    suspend fun insert(
        idempotencyKey: String,
        aggregateId: String,
        actorUserId: String,
        commandType: String,
        expectedVersion: Long,
        payloadVersion: Int,
        payloadId: String,
        clientSha256: String,
        now: Long,
    )
}
