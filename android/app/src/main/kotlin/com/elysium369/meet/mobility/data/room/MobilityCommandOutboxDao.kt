package com.elysium369.meet.mobility.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface MobilityCommandOutboxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: MobilityCommandOutboxEntity)

    @Update
    suspend fun update(entity: MobilityCommandOutboxEntity)

    @Query("SELECT * FROM mobility_command_outbox WHERE commandId = :commandId LIMIT 1")
    suspend fun getByCommandId(commandId: String): MobilityCommandOutboxEntity?

    @Query(
        """
        SELECT commandId
        FROM mobility_command_outbox
        WHERE
            (
                state IN ('PENDING', 'FAILED_RETRYABLE')
                OR (
                    state = 'SENDING'
                    AND leaseExpiresAtEpochMs < :now
                )
            )
            AND (
                nextAttemptAtEpochMs IS NULL
                OR nextAttemptAtEpochMs <= :now
            )
        ORDER BY createdAtEpochMs ASC
        LIMIT :limit
        """
    )
    suspend fun findClaimableIds(
        now: Long,
        limit: Int,
    ): List<String>

    @Query(
        """
        UPDATE mobility_command_outbox
        SET
            state = 'SENDING',
            leaseOwner = :owner,
            leaseExpiresAtEpochMs = :leaseUntil,
            lastAttemptAtEpochMs = :now
        WHERE commandId IN (:ids)
          AND (
              state IN ('PENDING', 'FAILED_RETRYABLE')
              OR (
                  state = 'SENDING'
                  AND leaseExpiresAtEpochMs < :now
              )
          )
        """
    )
    suspend fun claim(
        ids: List<String>,
        owner: String,
        now: Long,
        leaseUntil: Long,
    ): Int

    @Query(
        """
        SELECT *
        FROM mobility_command_outbox
        WHERE commandId IN (:ids)
          AND leaseOwner = :owner
          AND state = 'SENDING'
        """
    )
    suspend fun loadClaimed(
        ids: List<String>,
        owner: String,
    ): List<MobilityCommandOutboxEntity>

    @Transaction
    suspend fun claimDue(
        owner: String,
        now: Long,
        leaseUntil: Long,
        limit: Int,
    ): List<MobilityCommandOutboxEntity> {
        val ids = findClaimableIds(
            now = now,
            limit = limit,
        )

        if (ids.isEmpty()) {
            return emptyList()
        }

        claim(
            ids = ids,
            owner = owner,
            now = now,
            leaseUntil = leaseUntil,
        )

        return loadClaimed(
            ids = ids,
            owner = owner,
        )
    }

    @Query(
        """
        DELETE FROM mobility_command_outbox
        WHERE commandId = :commandId
          AND leaseOwner = :owner
          AND state = 'SENDING'
        """
    )
    suspend fun complete(
        commandId: String,
        owner: String,
    ): Int

    @Query(
        """
        UPDATE mobility_command_outbox
        SET
            state = 'FAILED_RETRYABLE',
            attemptCount = attemptCount + 1,
            nextAttemptAtEpochMs = :retryAt,
            leaseOwner = NULL,
            leaseExpiresAtEpochMs = NULL,
            lastErrorCode = :errorCode
        WHERE commandId = :commandId
          AND leaseOwner = :owner
        """
    )
    suspend fun retry(
        commandId: String,
        owner: String,
        retryAt: Long,
        errorCode: String,
    ): Int

    @Query(
        """
        SELECT * FROM mobility_command_outbox
        WHERE state IN ('PENDING', 'FAILED_RETRYABLE')
          AND (nextAttemptAtEpochMs IS NULL OR nextAttemptAtEpochMs <= :nowEpochMs)
        ORDER BY createdAtEpochMs ASC
        LIMIT :limit
        """
    )
    suspend fun getPendingCommands(nowEpochMs: Long, limit: Int = 10): List<MobilityCommandOutboxEntity>

    @Query(
        """
        UPDATE mobility_command_outbox
        SET state = :state, lastAttemptAtEpochMs = :attemptAtEpochMs, nextAttemptAtEpochMs = :nextAttemptAtEpochMs, attemptCount = attemptCount + 1
        WHERE commandId = :commandId
        """
    )
    suspend fun recordAttempt(
        commandId: String,
        state: String,
        attemptAtEpochMs: Long,
        nextAttemptAtEpochMs: Long?,
    )

    @Query("DELETE FROM mobility_command_outbox WHERE state = 'SERVER_ACCEPTED' AND createdAtEpochMs < :cutoffEpochMs")
    suspend fun pruneAccepted(cutoffEpochMs: Long): Int
}
