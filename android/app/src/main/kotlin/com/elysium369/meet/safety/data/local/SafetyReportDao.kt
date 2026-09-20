package com.elysium369.meet.safety.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class SafetyPrivateMapRow(
    val reportId: String,
    val ownerUserId: String,
    val category: String,
    val payloadId: String,
    val occurredAt: Long?,
    val serverState: String?,
    val syncState: String,
    val createdAt: Long,
    val ciphertext: ByteArray,
    val payloadSha256: String,
)

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
        SELECT r.reportId, r.ownerUserId, r.category, r.payloadId,
               r.occurredAt, r.serverState, r.syncState, r.createdAt,
               p.ciphertext, p.sha256 AS payloadSha256
        FROM safety_reports r
        INNER JOIN safety_local_payloads p ON p.payloadId = r.payloadId
        WHERE r.ownerUserId = :userId
          AND p.redactedAt IS NULL
        ORDER BY COALESCE(r.occurredAt, r.createdAt) DESC
        """
    )
    fun observePrivateMapRows(userId: String): Flow<List<SafetyPrivateMapRow>>

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

    @Query(
        """
        UPDATE safety_reports
        SET syncState = 'SYNCING',
            updatedAt = :now
        WHERE reportId = :reportId
          AND syncState != 'SYNCED'
        """
    )
    suspend fun markSyncing(reportId: String, now: Long): Int

    @Query(
        """
        UPDATE safety_reports
        SET syncState = 'QUEUED',
            updatedAt = :now
        WHERE reportId = :reportId
          AND syncState != 'SYNCED'
        """
    )
    suspend fun markQueued(reportId: String, now: Long): Int

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

    @Query("DELETE FROM safety_reports WHERE reportId = :reportId AND ownerUserId = :ownerUserId")
    suspend fun deleteOwned(reportId: String, ownerUserId: String): Int
}
