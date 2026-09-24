package com.elysium369.meet.core.emissions.storage

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "emission_sessions")
data class EmissionSessionEntity(
    @PrimaryKey val id: String,
    val vehicleId: String?,
    val vin: String?,
    val startedAt: Long,
    val completedAt: Long?,
    val protocol: String?,
    val ruleSetId: String?,
    val appVersion: String,
    val modelVersion: String?,
    val overallVerdict: String?,
    val confidence: Double?
)

@Entity(
    tableName = "emission_frames",
    indices = [Index(value = ["sessionId"])]
)
data class EmissionFrameEntity(
    @PrimaryKey val frameId: String,
    val sessionId: String,
    val timestampMs: Long,
    val rpm: Double?,
    val coolantC: Double?,
    val mafGps: Double?,
    val mapKpa: Double?,
    val stftPct: Double?,
    val ltftPct: Double?,
    val o2B1S1: Double?,
    val o2B1S2: Double?,
    val lambda: Double?,
    val coEst: Double?,
    val hcEst: Double?,
    val co2Est: Double?
)

@Entity(
    tableName = "emission_phase_results",
    indices = [Index(value = ["sessionId"])]
)
data class EmissionPhaseResultEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val phaseName: String,
    val rpmMean: Double,
    val ectMean: Double,
    val coPoint: Double,
    val coLower95: Double,
    val coUpper95: Double,
    val coEval: String,
    val hcPoint: Double,
    val hcLower95: Double,
    val hcUpper95: Double,
    val hcEval: String,
    val co2Point: Double,
    val co2Eval: String,
    val lambdaVal: Double?,
    val lambdaEval: String
)

@Entity(
    tableName = "emission_estimates",
    indices = [Index(value = ["sessionId"])]
)
data class EmissionEstimateEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val metricId: String,
    val pointEstimate: Double,
    val lower95: Double,
    val upper95: Double,
    val unit: String,
    val origin: String,
    val truthClass: String,
    val quality: Double?,
    val capturedAt: Long
)

@Entity(tableName = "gas_probe_calibrations")
data class GasProbeCalibrationEntity(
    @PrimaryKey val id: String,
    val sensorSerial: String,
    val zeroTimestampMs: Long,
    val spanTimestampMs: Long,
    val referenceGasFormula: String,
    val ambientTempC: Double?,
    val ambientPressureKpa: Double?,
    val createdAt: Long
)

@Dao
interface EmissionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: EmissionSessionEntity)

    @Update
    suspend fun updateSession(session: EmissionSessionEntity)

    @Query("SELECT * FROM emission_sessions WHERE id = :id")
    suspend fun getSessionById(id: String): EmissionSessionEntity?

    @Query("SELECT * FROM emission_sessions ORDER BY startedAt DESC")
    fun getAllSessions(): Flow<List<EmissionSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFrames(frames: List<EmissionFrameEntity>)

    @Query("SELECT * FROM emission_frames WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getFramesForSession(sessionId: String): List<EmissionFrameEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhaseResults(results: List<EmissionPhaseResultEntity>)

    @Query("SELECT * FROM emission_phase_results WHERE sessionId = :sessionId")
    suspend fun getPhaseResultsForSession(sessionId: String): List<EmissionPhaseResultEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEstimates(estimates: List<EmissionEstimateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalibration(calibration: GasProbeCalibrationEntity)
}
