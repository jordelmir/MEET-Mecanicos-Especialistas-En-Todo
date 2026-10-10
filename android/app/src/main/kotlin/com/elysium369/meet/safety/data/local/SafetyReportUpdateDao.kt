package com.elysium369.meet.safety.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SafetyReportUpdateDao {

    @Query(
        """
        SELECT * FROM safety_report_updates
        WHERE reportId = :reportId
        ORDER BY occurredAt ASC, recordedAt ASC
        """
    )
    fun observeUpdatesForReport(reportId: String): Flow<List<SafetyReportUpdateEntity>>

    @Query(
        """
        SELECT * FROM safety_report_updates
        WHERE reportId = :reportId
        ORDER BY occurredAt ASC, recordedAt ASC
        """
    )
    suspend fun getUpdatesForReport(reportId: String): List<SafetyReportUpdateEntity>

    @Query(
        """
        SELECT * FROM safety_report_updates
        ORDER BY occurredAt ASC, recordedAt ASC
        """
    )
    fun observeAllUpdates(): Flow<List<SafetyReportUpdateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SafetyReportUpdateEntity)

    @Upsert
    suspend fun upsert(entity: SafetyReportUpdateEntity)

    @Query("DELETE FROM safety_report_updates WHERE reportId = :reportId")
    suspend fun deleteForReport(reportId: String)

    @Query("DELETE FROM safety_report_updates WHERE updateId = :updateId")
    suspend fun deleteById(updateId: String)
}
