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
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

enum class TwinTruthState {
    UNTRAINED,
    BASELINE_INSUFFICIENT,
    BASELINE_ESTABLISHED,
    DRIFT_OBSERVED,
    ANOMALY_OBSERVED,
    CROSS_SENSOR_CORRELATED,
    DIAGNOSTIC_HYPOTHESIS,
    FAULT_CONFIRMED,
    MAINTENANCE_CONFIRMED,
}

data class TwinEpisode(
    val episodeId: String,
    val pid: String,
    val startedAtMs: Long,
    var lastObservedAtMs: Long,
    var peakDeviation: Float,
    var sampleCount: Int,
    var severity: String,
)

@Serializable
data class TwinParameterBaseline(
    val mean: Float,
    val stdDev: Float,
    val kalmanX: Float,
    val kalmanP: Float,
    val hwLevel: Float,
    val hwTrend: Float,
)

@Singleton
class VehicleTwinEngine @Inject constructor(
    private val twinDao: VehicleTwinDao,
) {
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }
    private val TAG = "VehicleTwinEngine"

    // Supported Digital Twin Parameters
    val twinParameters = mapOf(
        "0105" to "Coolant Temperature",
        "0142" to "Battery Voltage",
        "010C" to "Engine RPM",
        "0104" to "Engine Load",
        "0107" to "Long Term Fuel Trim",
        "010B" to "MAP Sensor",
        "0110" to "MAF Sensor",
        "010F" to "Intake Air Temp",
        "010D" to "Speed",
        "0111" to "Throttle Position",
    )

    // In-memory filters for real-time smoothing
    private val kalmanStates = mutableMapOf<String, MutableMap<String, Pair<Float, Float>>>()
    private val ewmaStates = mutableMapOf<String, MutableMap<String, Float>>()
    private val hwStates = mutableMapOf<String, MutableMap<String, Pair<Float, Float>>>()

    // Real-time anomalies detected during current drive
    private val _liveAnomalies = MutableStateFlow<List<TwinAnomalyEntity>>(emptyList())
    val liveAnomalies = _liveAnomalies.asStateFlow()

    // Episode deduplication window (30 seconds)
    private val activeEpisodes = ConcurrentHashMap<String, MutableMap<String, TwinEpisode>>()
    private val EPISODE_WINDOW_MS = 30_000L

    fun getTruthState(profile: VehicleTwinProfileEntity?, historyCount: Int = 0): TwinTruthState {
        if (profile == null || (historyCount == 0 && profile.confidence == 0.0)) {
            return TwinTruthState.UNTRAINED
        }
        if (profile.confidence < 50.0) {
            return TwinTruthState.BASELINE_INSUFFICIENT
        }
        if (_liveAnomalies.value.isNotEmpty()) {
            return TwinTruthState.ANOMALY_OBSERVED
        }
        return TwinTruthState.BASELINE_ESTABLISHED
    }

    /**
     * Initializes or trains the baseline mathematical twin profile based on historical data.
     * Uses EWMA and Moving Average to compute initial parameters.
     * Never manufactures synthetic 100% confidence or perfect health when history is missing.
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

        var sufficientPids = 0

        for (pid in twinParameters.keys) {
            val values = history.mapNotNull { it[pid] }
            if (values.size >= 10) {
                sufficientPids++
                val mean = values.average().toFloat()
                val variance = values.map { (it - mean) * (it - mean) }.average()
                val std = sqrt(variance).toFloat().let { if (it == 0f) 0.01f else it }

                baselines[pid] = TwinParameterBaseline(
                    mean = mean,
                    stdDev = std,
                    kalmanX = mean,
                    kalmanP = 1.0f,
                    hwLevel = mean,
                    hwTrend = 0.0f,
                )
            } else {
                baselines[pid] = TwinParameterBaseline(
                    mean = defaultMean,
                    stdDev = defaultStd,
                    kalmanX = defaultMean,
                    kalmanP = 1.0f,
                    hwLevel = defaultMean,
                    hwTrend = 0.0f,
                )
            }
        }

        // Truth-bounded confidence: 0 history -> 0.0 confidence.
        val confidence = when {
            history.isEmpty() -> 0.0
            history.size < 10 -> (history.size / 10.0 * 25.0)
            history.size >= 100 -> 95.0
            else -> (history.size / 100.0 * 95.0).coerceIn(25.0, 95.0)
        }

        val initialHealthScore = when {
            history.isEmpty() -> 0 // UNKNOWN / UNTRAINED
            history.size < 10 -> 50 // INSUFFICIENT
            else -> existing?.healthScore ?: 100
        }

        val profile = VehicleTwinProfileEntity(
            profileId = UUID.randomUUID().toString(),
            vehicleId = vehicleId,
            baselineJson = json.encodeToString(baselines.mapValues { it.value.mean }),
            varianceJson = json.encodeToString(baselines.mapValues { it.value.stdDev }),
            confidence = confidence,
            lastTrainingDate = System.currentTimeMillis(),
            anomalyCount = existing?.anomalyCount ?: 0,
            healthScore = initialHealthScore,
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
     * Implements episode deduplication with hysteresis.
     */
    suspend fun evaluateFrame(
        vehicleId: String,
        liveData: Map<String, Float>,
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
        val vehicleEpisodes = activeEpisodes.getOrPut(vehicleId) { mutableMapOf() }

        val newAnomalies = mutableListOf<TwinAnomalyEntity>()
        var newEpisodeDeduction = 0
        val now = System.currentTimeMillis()

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
            val q = 0.02f
            val r = 0.2f
            val p_pred = kp + q
            val k_gain = p_pred / (p_pred + r)
            val next_x = kx + k_gain * (actual - kx)
            val next_p = (1f - k_gain) * p_pred
            vehicleKalman[pid] = Pair(next_x, next_p)

            // 3. Holt-Winters Smoothing
            val (hwL, hwT) = vehicleHw[pid] ?: Pair(mean, 0.0f)
            val alpha = 0.2f
            val beta = 0.1f
            val next_hwL = alpha * actual + (1f - alpha) * (hwL + hwT)
            val next_hwT = beta * (next_hwL - hwL) + (1f - beta) * hwT
            vehicleHw[pid] = Pair(next_hwL, next_hwT)

            // 4. Z-Score Calculation
            val zScore = (ewma - mean) / (if (std == 0f) 0.01f else std)
            val absZ = abs(zScore)

            // 5. Anomaly Detection with Thresholds
            val expected = next_x
            val deviation = actual - expected
            val relativeDeviationPercent = if (expected != 0f) abs(deviation / expected) * 100f else 0f

            var isAnomalous = false
            var severity = "LOW"
            var confidence = (profile.confidence - absZ * 2).coerceIn(20.0, 99.0)

            if (pid == "0142" && next_hwT < -0.05f && actual < 13.5f) {
                isAnomalous = true
                severity = "HIGH"
                confidence = 94.0
            } else if (absZ > 3.0f || relativeDeviationPercent > 15f) {
                isAnomalous = true
                severity = if (absZ > 5.0f) "HIGH" else "MEDIUM"
            }

            if (isAnomalous) {
                val existingEpisode = vehicleEpisodes[pid]
                if (existingEpisode != null && now - existingEpisode.lastObservedAtMs < EPISODE_WINDOW_MS) {
                    // Update existing episode without deducting health repeatedly
                    existingEpisode.lastObservedAtMs = now
                    existingEpisode.sampleCount++
                    if (abs(deviation) > abs(existingEpisode.peakDeviation)) {
                        existingEpisode.peakDeviation = deviation
                    }
                } else {
                    // Start new anomaly episode
                    val episode = TwinEpisode(
                        episodeId = UUID.randomUUID().toString(),
                        pid = pid,
                        startedAtMs = now,
                        lastObservedAtMs = now,
                        peakDeviation = deviation,
                        sampleCount = 1,
                        severity = severity,
                    )
                    vehicleEpisodes[pid] = episode

                    val anomaly = TwinAnomalyEntity(
                        anomalyId = episode.episodeId,
                        vehicleId = vehicleId,
                        parameter = paramName,
                        expectedValue = expected,
                        actualValue = actual,
                        deviation = deviation,
                        severity = severity,
                        confidence = confidence,
                        timestamp = now,
                    )
                    newAnomalies.add(anomaly)
                    twinDao.insertAnomaly(anomaly)

                    newEpisodeDeduction += when (severity) {
                        "HIGH" -> 15
                        "MEDIUM" -> 8
                        else -> 4
                    }
                }
            }
        }

        if (newAnomalies.isNotEmpty() && profile.confidence >= 50.0) {
            val currentScore = if (profile.healthScore == 0) 100 else profile.healthScore
            val nextScore = (currentScore - newEpisodeDeduction).coerceIn(10, 100)
            val nextAnomalyCount = profile.anomalyCount + newAnomalies.size

            val updatedProfile = profile.copy(
                healthScore = nextScore,
                anomalyCount = nextAnomalyCount,
                lastTrainingDate = now,
            )
            twinDao.insertTwinProfile(updatedProfile)

            val updatedList = (_liveAnomalies.value + newAnomalies).takeLast(40)
            _liveAnomalies.value = updatedList
        }

        return@withContext newAnomalies
    }

    suspend fun clearLiveAnomalies() {
        _liveAnomalies.value = emptyList()
        activeEpisodes.clear()
    }
}

