package com.elysium369.meet.core.twin

import android.util.Log
import com.elysium369.meet.data.local.dao.VehicleTwinDao
import com.elysium369.meet.data.local.entities.TwinAnomalyEntity
import com.elysium369.meet.data.local.entities.VehicleTwinProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

@Serializable
data class TwinParameterBaseline(
    val mean: Float,
    val stdDev: Float,
    val kalmanX: Float,
    val kalmanP: Float,
    val hwLevel: Float,
    val hwTrend: Float
)

@Singleton
class VehicleTwinEngine @Inject constructor(
    private val twinDao: VehicleTwinDao
) {
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }
    private val TAG = "VehicleTwinEngine"

    // Supported Digital Twin Parameters
    private val twinParameters = mapOf(
        "0105" to "Coolant Temperature",
        "0142" to "Battery Voltage",
        "010C" to "Engine RPM",
        "0104" to "Engine Load",
        "0107" to "Long Term Fuel Trim",
        "010B" to "MAP Sensor",
        "0110" to "MAF Sensor",
        "010F" to "Intake Air Temp",
        "010D" to "Speed",
        "0111" to "Throttle Position"
    )

    // In-memory filters for real-time smoothing
    private val kalmanStates = mutableMapOf<String, MutableMap<String, Pair<Float, Float>>>() // vehicleId -> (pid -> (x, p))
    private val ewmaStates = mutableMapOf<String, MutableMap<String, Float>>() // vehicleId -> (pid -> value)
    private val hwStates = mutableMapOf<String, MutableMap<String, Pair<Float, Float>>>() // vehicleId -> (pid -> (level, trend))

    // Real-time anomalies detected during current drive
    private val _liveAnomalies = MutableStateFlow<List<TwinAnomalyEntity>>(emptyList())
    val liveAnomalies = _liveAnomalies.asStateFlow()

    /**
     * Initializes or trains the baseline mathematical twin profile based on historical data.
     * Uses EWMA and Moving Average to compute initial parameters.
     */
    suspend fun trainOrInitializeProfile(vehicleId: String, history: List<Map<String, Float>>): VehicleTwinProfileEntity = withContext(Dispatchers.IO) {
        val existing = twinDao.getTwinProfile(vehicleId)
        if (existing != null && history.isEmpty()) {
            return@withContext existing
        }

        Log.i(TAG, "Training digital twin model for vehicle: $vehicleId with ${history.size} historical frames")

        val baselines = mutableMapOf<String, TwinParameterBaseline>()
        val defaultMean = 0f
        val defaultStd = 1f

        for (pid in twinParameters.keys) {
            val values = history.mapNotNull { it[pid] }
            if (values.size >= 10) {
                val mean = values.average().toFloat()
                val variance = values.map { (it - mean) * (it - mean) }.average()
                val std = sqrt(variance).toFloat().let { if (it == 0f) 0.01f else it }
                
                // Initialize Kalman and Holt-Winters
                baselines[pid] = TwinParameterBaseline(
                    mean = mean,
                    stdDev = std,
                    kalmanX = mean,
                    kalmanP = 1.0f,
                    hwLevel = mean,
                    hwTrend = 0.0f
                )
            } else {
                baselines[pid] = TwinParameterBaseline(
                    mean = defaultMean,
                    stdDev = defaultStd,
                    kalmanX = defaultMean,
                    kalmanP = 1.0f,
                    hwLevel = defaultMean,
                    hwTrend = 0.0f
                )
            }
        }

        val confidence = if (history.size >= 100) 95.0 else (history.size / 100.0 * 95.0).coerceAtLeast(40.0)
        
        val profile = VehicleTwinProfileEntity(
            profileId = UUID.randomUUID().toString(),
            vehicleId = vehicleId,
            baselineJson = json.encodeToString(baselines.mapValues { it.value.mean }),
            varianceJson = json.encodeToString(baselines.mapValues { it.value.stdDev }),
            confidence = confidence,
            lastTrainingDate = System.currentTimeMillis(),
            anomalyCount = existing?.anomalyCount ?: 0,
            healthScore = existing?.healthScore ?: 100
        )

        twinDao.insertTwinProfile(profile)
        
        // Seed states
        val vehicleKalman = kalmanStates.getOrPut(vehicleId) { mutableMapOf() }
        val vehicleHw = hwStates.getOrPut(vehicleId) { mutableMapOf() }
        baselines.forEach { (pid, state) ->
            vehicleKalman[pid] = Pair(state.kalmanX, state.kalmanP)
            vehicleHw[pid] = Pair(state.hwLevel, state.hwTrend)
        }

        return@withContext profile
    }

    /**
     * Evaluates a frame of live data against the Digital Twin model.
     * Computes Kalman predictions, HW trends, and Z-Scores to output real-time anomalies.
     */
    suspend fun evaluateFrame(
        vehicleId: String,
        liveData: Map<String, Float>
    ): List<TwinAnomalyEntity> = withContext(Dispatchers.IO) {
        val profile = twinDao.getTwinProfile(vehicleId)
            ?: trainOrInitializeProfile(vehicleId, emptyList())

        val baselines = try {
            json.decodeFromString<Map<String, Float>>(profile.baselineJson)
        } catch (_: Exception) { emptyMap() }
        
        val variances = try {
            json.decodeFromString<Map<String, Float>>(profile.varianceJson)
        } catch (_: Exception) { emptyMap() }

        val vehicleKalman = kalmanStates.getOrPut(vehicleId) { mutableMapOf() }
        val vehicleEwma = ewmaStates.getOrPut(vehicleId) { mutableMapOf() }
        val vehicleHw = hwStates.getOrPut(vehicleId) { mutableMapOf() }

        val newAnomalies = mutableListOf<TwinAnomalyEntity>()
        var anomalyDeduction = 0

        for ((pid, paramName) in twinParameters) {
            val actual = liveData[pid] ?: liveData[pid.lowercase()] ?: continue
            val mean = baselines[pid] ?: actual
            val std = variances[pid] ?: 1.0f

            // 1. Update EWMA (alpha = 0.15)
            val prevEwma = vehicleEwma[pid] ?: actual
            val ewma = 0.15f * actual + (1f - 0.15f) * prevEwma
            vehicleEwma[pid] = ewma

            // 2. Kalman Filter Predict & Update
            val (kx, kp) = vehicleKalman[pid] ?: Pair(mean, 1.0f)
            val q = 0.02f // process noise
            val r = 0.2f  // measurement noise
            val p_pred = kp + q
            val k_gain = p_pred / (p_pred + r)
            val next_x = kx + k_gain * (actual - kx)
            val next_p = (1f - k_gain) * p_pred
            vehicleKalman[pid] = Pair(next_x, next_p)

            // 3. Holt-Winters Double Exponential Smoothing (Level & Trend)
            val (hwL, hwT) = vehicleHw[pid] ?: Pair(mean, 0.0f)
            val alpha = 0.2f
            val beta = 0.1f
            val next_hwL = alpha * actual + (1f - alpha) * (hwL + hwT)
            val next_hwT = beta * (next_hwL - hwL) + (1f - beta) * hwT
            vehicleHw[pid] = Pair(next_hwL, next_hwT)

            // 4. Z-Score Calculation based on EWMA & Baseline StdDev
            val zScore = (ewma - mean) / (if (std == 0f) 0.01f else std)
            val absZ = abs(zScore)

            // 5. Anomaly Detection triggers:
            // - Kalman prediction error deviation is large (over 3.5 standard deviations)
            // - Or Z-Score is extreme
            // - Or Holt-Winters detects a strong persistent downward trend in voltage
            val expected = next_x
            val deviation = actual - expected
            val relativeDeviationPercent = if (expected != 0f) abs(deviation / expected) * 100f else 0f

            var isAnomalous = false
            var severity = "LOW"
            var confidence = (profile.confidence - absZ * 2).coerceIn(20.0, 99.0)

            if (pid == "0142" && next_hwT < -0.05f && actual < 13.5f) {
                // Persistent descending trend in Battery/Alternator voltage
                isAnomalous = true
                severity = "HIGH"
                confidence = 94.0
            } else if (absZ > 3.0f || relativeDeviationPercent > 15f) {
                isAnomalous = true
                severity = if (absZ > 5.0f) "HIGH" else "MEDIUM"
            }

            if (isAnomalous) {
                val anomaly = TwinAnomalyEntity(
                    anomalyId = UUID.randomUUID().toString(),
                    vehicleId = vehicleId,
                    parameter = paramName,
                    expectedValue = expected,
                    actualValue = actual,
                    deviation = deviation,
                    severity = severity,
                    confidence = confidence,
                    timestamp = System.currentTimeMillis()
                )
                newAnomalies.add(anomaly)
                twinDao.insertAnomaly(anomaly)

                anomalyDeduction += when (severity) {
                    "HIGH" -> 15
                    "MEDIUM" -> 8
                    else -> 4
                }
            }
        }

        if (newAnomalies.isNotEmpty()) {
            val currentScore = profile.healthScore
            val nextScore = (currentScore - anomalyDeduction).coerceIn(10, 100)
            val nextAnomalyCount = profile.anomalyCount + newAnomalies.size
            
            val updatedProfile = profile.copy(
                healthScore = nextScore,
                anomalyCount = nextAnomalyCount,
                lastTrainingDate = System.currentTimeMillis()
            )
            twinDao.insertTwinProfile(updatedProfile)

            val updatedList = (_liveAnomalies.value + newAnomalies).takeLast(40)
            _liveAnomalies.value = updatedList
        }

        return@withContext newAnomalies
    }

    suspend fun clearLiveAnomalies() {
        _liveAnomalies.value = emptyList()
    }
}
