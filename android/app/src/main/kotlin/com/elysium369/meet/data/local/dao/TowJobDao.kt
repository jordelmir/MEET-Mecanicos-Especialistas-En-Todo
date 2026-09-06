package com.elysium369.meet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.elysium369.meet.data.local.entities.TowJobEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Towing operations providing atomic compare-and-swap (CAS)
 * mutations at the SQLite storage layer.
 */
@Dao
interface TowJobDao {
    @Query("SELECT * FROM tow_jobs ORDER BY createdAtEpochMs DESC")
    fun getAllJobsFlow(): Flow<List<TowJobEntity>>

    @Query("SELECT * FROM tow_jobs WHERE state NOT IN ('COMPLETED', 'CANCELLED', 'DISPUTED') ORDER BY createdAtEpochMs DESC")
    fun getActiveJobsFlow(): Flow<List<TowJobEntity>>

    @Query("SELECT * FROM tow_jobs WHERE jobId = :jobId LIMIT 1")
    suspend fun getJobById(jobId: String): TowJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: TowJobEntity): Long

    @Update
    suspend fun updateJob(job: TowJobEntity): Int

    /**
     * Atomic Compare-And-Swap (CAS) state mutation in SQLite.
     * Guaranteed to mutate the row ONLY if serverVersion matches expectedVersion exactly.
     * @return Number of rows updated: 1 indicates CAS success; 0 indicates a concurrency conflict.
     */
    @Query("""
        UPDATE tow_jobs 
        SET state = :newState, 
            serverVersion = serverVersion + 1, 
            updatedAtEpochMs = :updatedAtEpochMs,
            assignedOperatorName = :operatorName,
            assignedOperatorPhone = :operatorPhone,
            custodyRecordsJson = :custodyRecordsJson,
            assignedUnitJson = :assignedUnitJson
        WHERE jobId = :jobId AND serverVersion = :expectedVersion
    """)
    suspend fun compareAndSwapState(
        jobId: String,
        expectedVersion: Long,
        newState: String,
        updatedAtEpochMs: Long,
        operatorName: String?,
        operatorPhone: String?,
        custodyRecordsJson: String,
        assignedUnitJson: String?
    ): Int

    @Query("DELETE FROM tow_jobs WHERE jobId = :jobId")
    suspend fun deleteJob(jobId: String): Int

    @Query("DELETE FROM tow_jobs WHERE state IN ('COMPLETED', 'CANCELLED') AND updatedAtEpochMs < :cutoffEpochMs")
    suspend fun purgeOldJobs(cutoffEpochMs: Long): Int
}

