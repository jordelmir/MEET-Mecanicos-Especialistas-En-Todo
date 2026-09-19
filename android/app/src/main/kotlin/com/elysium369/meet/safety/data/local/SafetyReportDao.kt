package com.elysium369.meet.safety.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SafetyReportDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrThrow(entity: SafetyReportEntity)

    @Query("SELECT * FROM safety_reports WHERE reportId = :reportId")
    suspend fun get(reportId: String): SafetyReportEntity?

    @Query(
        """
        SELECT * FROM safety_reports
        WHERE ownerUserId = :userId
        ORDER BY createdAt DESC
        """
    )
    suspend fun listByUser(userId: String): List<SafetyReportEntity>

    @Query(
        """
        SELECT * FROM safety_reports
        WHERE ownerUserId = :userId
        ORDER BY createdAt DESC
        """
    )
    fun observeByUser(userId: String): Flow<List<SafetyReportEntity>>

    @Query(
        """
        UPDATE safety_reports
        SET serverState = :serverState,
            serverVersion = :serverVersion,
            syncState = 'SYNCED',
            updatedAt = :now
        WHERE reportId = :reportId
        """
    )
    suspend fun applyServerAcknowledgement(
        reportId: String,
        serverState: String,
        serverVersion: Long,
        now: Long,
    ): Int

    @Query(
        """
        UPDATE safety_reports
        SET syncState = 'FAILED',
            updatedAt = :now
        WHERE reportId = :reportId AND syncState != 'SYNCED'
        """
    )
    suspend fun markFailed(reportId: String, now: Long): Int

    @Query("SELECT COUNT(*) FROM safety_reports WHERE ownerUserId = :userId")
    suspend fun countByUser(userId: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM safety_reports
        WHERE ownerUserId = :userId
          AND syncState IN ('QUEUED','SYNCING')
        """
    )
    suspend fun pendingCount(userId: String): Int
}
