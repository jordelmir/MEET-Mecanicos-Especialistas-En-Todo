package com.elysium369.meet.safety.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface SafetyCommandOutboxDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrThrow(entity: SafetyCommandOutboxEntity)

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
          AND actorSessionUserId = :owner
          AND nextAttemptAt <= :now
        ORDER BY createdAt
        LIMIT :limit
        """
    )
    suspend fun ready(now: Long, limit: Int, owner: String): List<SafetyCommandOutboxEntity>

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
    suspend fun acquireBatch(now: Long, limit: Int, owner: String): List<SafetyCommandOutboxEntity> {
        val result = mutableListOf<SafetyCommandOutboxEntity>()
        for (candidate in ready(now, limit, owner)) {
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
        WHERE status IN ('PENDING','RETRYABLE') AND actorSessionUserId = :owner
        """
    )
    suspend fun earliestRetryAt(owner: String): Long?

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
        UPDATE safety_command_outbox
        SET status='DEAD_LETTER',
            leaseStartedAt=NULL,
            nextAttemptAt=9223372036854775807,
            lastErrorCode='REPORT_WITHDRAWN_BY_OWNER',
            lastErrorMessage='Owner removed the local report before publication',
            updatedAt=:now
        WHERE aggregateId=:reportId
          AND actorSessionUserId=:ownerUserId
          AND commandType='CREATE_REPORT'
          AND status != 'ACKNOWLEDGED'
        """
    )
    suspend fun cancelPendingReport(reportId: String, ownerUserId: String, now: Long): Int
}
