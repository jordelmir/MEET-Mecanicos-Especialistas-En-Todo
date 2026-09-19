package com.elysium369.meet.safety.data.local

import androidx.room.Dao
import androidx.room.Query

@Dao
interface SafetyReportDao {

    @Query(
        """
        INSERT OR IGNORE INTO safety_reports
        (reportId, ownerUserId, category, payloadId, occurredAt,
         localState, serverState, serverVersion, syncState, createdAt, updatedAt)
        VALUES
        (:reportId, :ownerUserId, :category, :payloadId, :occurredAt,
         :localState, NULL, 0, 'QUEUED', :now, :now)
        """
    )
    suspend fun insert(
        reportId: String,
        ownerUserId: String,
        category: String,
        payloadId: String,
        occurredAt: Long?,
        localState: String,
        now: Long,
    )

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
}
