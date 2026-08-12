package com.elysium369.meet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elysium369.meet.data.local.entities.AiDiagnosticResultEntity
import com.elysium369.meet.data.local.entities.AuditLogEntity
import com.elysium369.meet.data.local.entities.CompatibilityRuleEntity
import com.elysium369.meet.data.local.entities.DerivedMetricEntity
import com.elysium369.meet.data.local.entities.EcuFailureEventEntity
import com.elysium369.meet.data.local.entities.FixRolloutEntity
import com.elysium369.meet.data.local.entities.FreezeFrameEntity
import com.elysium369.meet.data.local.entities.HealthScoreEntity
import com.elysium369.meet.data.local.entities.Mode06ResultEntity
import com.elysium369.meet.data.local.entities.ObdCommandLogEntity
import com.elysium369.meet.data.local.entities.ObdPidSampleEntity
import com.elysium369.meet.data.local.entities.PdfReportEntity
import com.elysium369.meet.data.local.entities.RepairRecommendationEntity
import com.elysium369.meet.data.local.entities.VanguardObdSessionEntity
import com.elysium369.meet.data.local.entities.VehicleHistoryEntity
import com.elysium369.meet.data.local.entities.VehicleProfileSnapshotEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Elysium Vanguard Telemetry Intelligence entities.
 * Companion for [com.elysium369.meet.data.local.entities.VanguardEntities].
 */
@Dao
interface VanguardTelemetryDao {

    // ----- Sessions -----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: VanguardObdSessionEntity)

    @Query("SELECT * FROM vanguard_obd_sessions WHERE sessionId = :sessionId")
    suspend fun getSession(sessionId: String): VanguardObdSessionEntity?

    @Query("SELECT * FROM vanguard_obd_sessions WHERE vehicleId = :vehicleId ORDER BY startedAt DESC LIMIT :limit")
    fun getSessionsForVehicle(vehicleId: String, limit: Int = 50): Flow<List<VanguardObdSessionEntity>>

    @Query(
        """UPDATE vanguard_obd_sessions
           SET endedAt = :endedAt,
               status = :status,
               totalPidsRead = :totalPidsRead,
               errorCount = :errorCount,
               lastError = :lastError
           WHERE sessionId = :sessionId"""
    )
    suspend fun finishSession(
        sessionId: String,
        endedAt: Long,
        status: String,
        totalPidsRead: Int,
        errorCount: Int,
        lastError: String?,
    ): Int

    // ----- PID samples -----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPidSample(sample: ObdPidSampleEntity)

    @Query("SELECT * FROM obd_pid_samples WHERE sessionId = :sessionId ORDER BY capturedAt ASC")
    fun getPidSamplesForSession(sessionId: String): Flow<List<ObdPidSampleEntity>>

    // ----- Command log -----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCommandLog(log: ObdCommandLogEntity)

    @Query("SELECT COUNT(*) FROM obd_command_log WHERE sessionId = :sessionId")
    suspend fun countCommandsForSession(sessionId: String): Int

    // ----- ECU failures -----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEcuFailure(failure: EcuFailureEventEntity)

    @Query("SELECT * FROM ecu_failure_events WHERE vehicleId = :vehicleId AND resolvedAt IS NULL ORDER BY detectedAt DESC")
    fun getActiveFailuresForVehicle(vehicleId: String): Flow<List<EcuFailureEventEntity>>

    // ----- Compatibility -----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompatibilityRule(rule: CompatibilityRuleEntity)

    @Query("SELECT * FROM compatibility_rules WHERE vehicleMake = :make AND vehicleModel = :model")
    suspend fun getRulesForVehicle(make: String, model: String): List<CompatibilityRuleEntity>

    // ----- Vehicle snapshots -----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfileSnapshot(snapshot: VehicleProfileSnapshotEntity)

    @Query("SELECT * FROM vehicle_profile_snapshots WHERE vehicleId = :vehicleId ORDER BY capturedAt DESC LIMIT 1")
    suspend fun getLatestSnapshot(vehicleId: String): VehicleProfileSnapshotEntity?

    // ----- Mode 06 -----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMode06Result(result: Mode06ResultEntity)

    @Query("SELECT * FROM mode06_results WHERE sessionId = :sessionId AND status = 'FAIL'")
    fun getFailingMode06ForSession(sessionId: String): Flow<List<Mode06ResultEntity>>

    // ----- Freeze frames -----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFreezeFrame(frame: FreezeFrameEntity)

    @Query("SELECT * FROM freeze_frames WHERE vehicleId = :vehicleId ORDER BY capturedAt DESC LIMIT 50")
    fun getFreezeFramesForVehicle(vehicleId: String): Flow<List<FreezeFrameEntity>>

    // ----- Derived metrics -----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDerivedMetric(metric: DerivedMetricEntity)

    @Query("SELECT * FROM derived_metrics WHERE vehicleId = :vehicleId AND metricName = :name ORDER BY computedAt DESC LIMIT 1")
    suspend fun getLatestDerivedMetric(vehicleId: String, name: String): DerivedMetricEntity?

    // ----- Health scores -----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHealthScore(score: HealthScoreEntity)

    @Query("SELECT * FROM health_scores WHERE vehicleId = :vehicleId ORDER BY computedAt DESC LIMIT 1")
    suspend fun getLatestHealthScore(vehicleId: String): HealthScoreEntity?

    // ----- Repair recommendations -----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepairRecommendation(rec: RepairRecommendationEntity)

    @Query("SELECT * FROM repair_recommendations WHERE vehicleId = :vehicleId ORDER BY createdAt DESC")
    fun getRepairRecommendationsForVehicle(vehicleId: String): Flow<List<RepairRecommendationEntity>>

    // ----- AI diagnostic -----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAiDiagnosticResult(result: AiDiagnosticResultEntity)

    @Query("SELECT * FROM ai_diagnostic_results WHERE vehicleId = :vehicleId ORDER BY generatedAt DESC LIMIT 10")
    fun getRecentAiResultsForVehicle(vehicleId: String): Flow<List<AiDiagnosticResultEntity>>

    // ----- Vehicle history -----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVehicleHistory(history: VehicleHistoryEntity)

    @Query("SELECT * FROM vehicle_history WHERE vehicleId = :vehicleId ORDER BY eventAt DESC LIMIT 100")
    fun getVehicleHistory(vehicleId: String): Flow<List<VehicleHistoryEntity>>

    // ----- PDF reports -----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPdfReport(report: PdfReportEntity)

    @Query("SELECT * FROM pdf_reports WHERE vehicleId = :vehicleId ORDER BY generatedAt DESC")
    fun getPdfReportsForVehicle(vehicleId: String): Flow<List<PdfReportEntity>>

    // ----- Audit logs -----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAuditLog(log: AuditLogEntity)

    @Query("SELECT * FROM audit_logs WHERE resourceType = :resourceType AND resourceId = :resourceId ORDER BY occurredAt DESC")
    fun getAuditTrailForResource(resourceType: String, resourceId: String): Flow<List<AuditLogEntity>>

    // ----- Fix rollouts -----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFixRollout(rollout: FixRolloutEntity)

    @Query("SELECT * FROM fix_rollouts WHERE status = :status ORDER BY rolledOutAt DESC")
    fun getFixRolloutsByStatus(status: String): Flow<List<FixRolloutEntity>>
}
