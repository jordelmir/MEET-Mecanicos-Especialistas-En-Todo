package com.elysium369.meet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elysium369.meet.data.local.entities.DiagnosticScanManifestEntity
import com.elysium369.meet.data.local.entities.TelemetryUploadQueueEntity
import com.elysium369.meet.data.local.entities.VehicleIdentityObservationEntity

@Dao
interface VanguardConvergenceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIdentityObservation(entity: VehicleIdentityObservationEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertScanManifest(entity: DiagnosticScanManifestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueTelemetry(entity: TelemetryUploadQueueEntity)

    @Query("SELECT * FROM telemetry_upload_queue WHERE status = 'PENDING' AND nextAttemptAtEpochMs <= :nowEpochMs ORDER BY createdAtEpochMs LIMIT :limit")
    suspend fun pendingTelemetry(nowEpochMs: Long, limit: Int): List<TelemetryUploadQueueEntity>

    @Query("DELETE FROM telemetry_upload_queue WHERE signalId IN (:signalIds)")
    suspend fun deleteExportedTelemetry(signalIds: List<String>)
}
