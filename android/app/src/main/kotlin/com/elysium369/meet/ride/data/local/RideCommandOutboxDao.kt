package com.elysium369.meet.ride.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RideCommandOutboxDao {
    @Query("SELECT * FROM ride_command_outbox WHERE rideId = :requestId AND commandType = 'CANCEL' ORDER BY createdAt DESC")
    fun cancellationCommands(requestId: String): Flow<List<RideCommandOutboxEntity>>

    @Query("UPDATE ride_command_outbox SET expectedVersion = :version WHERE idempotencyKey = :key AND commandType = 'CANCEL' AND status = 'IN_FLIGHT' AND expectedVersion = 0")
    suspend fun resolveCancellationVersion(key: String, version: Long): Int

    /** Atomic with acquireBatch: a worker either leases publication or cancellation supersedes it. */
    @Transaction
    suspend fun insertCancellation(command: RideCommandOutboxEntity): Long {
        require(command.commandType == "CANCEL")
        val inserted = insert(command)
        if (inserted == -1L) return inserted
        val suppressed = if (command.expectedVersion == 0L) supersedeUnsentPublication(
            command.rideId, command.actorSessionUserId, command.updatedAt,
        ) else 0
        if (suppressed > 0) {
            finishLocalCancellation(command.idempotencyKey, command.updatedAt)
            cancelUnpublishedRequest(command.rideId, command.actorSessionUserId)
            clearLocalCancelledSelection(command.rideId, command.actorSessionUserId)
        } else {
            markCancellationPending(command.rideId, command.actorSessionUserId)
        }
        return inserted
    }

    @Query("""
        UPDATE ride_command_outbox SET status = 'SUPERSEDED',
            lastErrorCode = 'CANCELLED_BEFORE_PUBLICATION', updatedAt = :now
        WHERE rideId = :rideId AND actorSessionUserId = :owner AND commandType = 'PUBLISH'
          AND status = 'PENDING' AND attemptCount = 0
          AND EXISTS (SELECT 1 FROM ride_requests WHERE requestId = :rideId AND passengerId = :owner AND serverVersion = 0)
          AND NOT EXISTS (SELECT 1 FROM ride_command_outbox prior
              WHERE prior.rideId = :rideId AND prior.commandType = 'PUBLISH'
                AND (prior.attemptCount > 0 OR prior.actorSessionUserId != :owner OR prior.status != 'PENDING'))
    """)
    suspend fun supersedeUnsentPublication(rideId: String, owner: String, now: Long): Int

    @Query("""
        UPDATE ride_command_outbox SET status = 'LOCAL_CANCELLED',
            lastErrorCode = 'CANCELLED_BEFORE_PUBLICATION', updatedAt = :now
        WHERE idempotencyKey = :key AND commandType = 'CANCEL' AND status = 'PENDING' AND attemptCount = 0
    """)
    suspend fun finishLocalCancellation(key: String, now: Long): Int

    @Query("UPDATE ride_requests SET status = 'CANCELLED', syncState = 'LOCAL_CANCELLED' WHERE requestId = :rideId AND passengerId = :owner AND serverVersion = 0")
    suspend fun cancelUnpublishedRequest(rideId: String, owner: String): Int

    @Query("DELETE FROM active_ride_selections WHERE rideRequestId = :rideId AND ownerPrincipalId = :owner")
    suspend fun clearLocalCancelledSelection(rideId: String, owner: String): Int

    @Query("UPDATE ride_requests SET syncState = 'PENDING' WHERE requestId = :rideId AND (passengerId = :owner OR assignedDriverId = :owner)")
    suspend fun markCancellationPending(rideId: String, owner: String): Int

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
            payloadJson = '{"redacted_after_ack":true}',
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
        UPDATE ride_command_outbox
        SET status = 'RETRYABLE',
            leaseStartedAt = NULL,
            nextAttemptAt = :now,
            lastErrorCode = 'STALE_CANCEL_LEASE_RECOVERED',
            lastErrorMessage = 'Recovered when passenger retried cancellation',
            updatedAt = :now
        WHERE rideId = :rideId
          AND actorSessionUserId = :actorId
          AND commandType = 'CANCEL'
          AND status = 'IN_FLIGHT'
          AND leaseStartedAt IS NOT NULL
          AND leaseStartedAt <= :staleBefore
        """,
    )
    suspend fun recoverStaleCancellationLease(
        rideId: String,
        actorId: String,
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

    @Query(
        """
        SELECT * FROM ride_command_outbox
        WHERE status IN ('FAILED', 'CONFLICT', 'DEAD_LETTER')
        ORDER BY updatedAt DESC
        LIMIT 20
        """,
    )
    fun recentFailures(): Flow<List<RideCommandOutboxEntity>>
}
