package com.elysium369.meet.mobility.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface MobilityCommandOutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MobilityCommandOutboxEntity)

    @Update
    suspend fun update(entity: MobilityCommandOutboxEntity)

    @Query("SELECT * FROM mobility_command_outbox WHERE commandId = :commandId LIMIT 1")
    suspend fun getByCommandId(commandId: String): MobilityCommandOutboxEntity?

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
