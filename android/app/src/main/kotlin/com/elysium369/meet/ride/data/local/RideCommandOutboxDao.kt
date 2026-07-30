package com.elysium369.meet.ride.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RideCommandOutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(command: RideCommandOutboxEntity): Long

    @Query(
        """
        SELECT * FROM ride_command_outbox
        WHERE status IN ('PENDING', 'RETRYABLE')
          AND nextAttemptAt <= :now
        ORDER BY createdAt ASC
        LIMIT :limit
        """,
    )
    suspend fun ready(now: Long, limit: Int): List<RideCommandOutboxEntity>

    @Query(
        """
        UPDATE ride_command_outbox
        SET status = 'IN_FLIGHT',
            attemptCount = attemptCount + 1,
            leaseStartedAt = :now,
            updatedAt = :now
        WHERE idempotencyKey = :idempotencyKey
          AND status IN ('PENDING', 'RETRYABLE')
          AND nextAttemptAt <= :now
        """,
    )
    suspend fun acquire(
        idempotencyKey: String,
        now: Long,
    ): Int

    @Query(
        """
        SELECT * FROM ride_command_outbox
        WHERE idempotencyKey = :idempotencyKey
        LIMIT 1
        """,
    )
    suspend fun byIdempotencyKey(
        idempotencyKey: String,
    ): RideCommandOutboxEntity?

    @Transaction
    suspend fun acquireBatch(
        now: Long,
        limit: Int = 20,
    ): List<RideCommandOutboxEntity> = buildList {
        ready(now, limit).forEach { candidate ->
            if (acquire(candidate.idempotencyKey, now) == 1) {
                byIdempotencyKey(candidate.idempotencyKey)?.let(::add)
            }
        }
    }

    @Query(
        """
        UPDATE ride_command_outbox
        SET status = 'ACKNOWLEDGED',
            leaseStartedAt = NULL,
            lastErrorCode = NULL,
            lastErrorMessage = NULL,
            correlationId = :correlationId,
            updatedAt = :now
        WHERE idempotencyKey = :idempotencyKey
          AND status = 'IN_FLIGHT'
        """,
    )
    suspend fun acknowledge(
        idempotencyKey: String,
        correlationId: String?,
        now: Long,
    ): Int

    @Query(
        """
        UPDATE ride_command_outbox
        SET status = :status,
            leaseStartedAt = NULL,
            nextAttemptAt = :nextAttemptAt,
            lastErrorCode = :errorCode,
            lastErrorMessage = :errorMessage,
            correlationId = :correlationId,
            updatedAt = :now
        WHERE idempotencyKey = :idempotencyKey
          AND status = 'IN_FLIGHT'
        """,
    )
    suspend fun finishFailure(
        idempotencyKey: String,
        status: String,
        nextAttemptAt: Long,
        errorCode: String,
        errorMessage: String,
        correlationId: String?,
        now: Long,
    ): Int

    @Query(
        """
        UPDATE ride_command_outbox
        SET status = 'RETRYABLE',
            leaseStartedAt = NULL,
            nextAttemptAt = :now,
            lastErrorCode = 'STALE_LEASE_RECOVERED',
            lastErrorMessage = 'Recovered after interrupted worker',
            updatedAt = :now
        WHERE status = 'IN_FLIGHT'
          AND leaseStartedAt IS NOT NULL
          AND leaseStartedAt <= :staleBefore
        """,
    )
    suspend fun recoverStaleLeases(
        staleBefore: Long,
        now: Long,
    ): Int

    @Query(
        """
        SELECT COUNT(*) FROM ride_command_outbox
        WHERE status IN ('PENDING', 'IN_FLIGHT', 'RETRYABLE')
        """,
    )
    fun pendingCount(): Flow<Int>
}
