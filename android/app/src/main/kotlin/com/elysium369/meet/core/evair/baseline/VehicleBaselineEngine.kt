package com.elysium369.meet.core.evair.baseline

import com.elysium369.meet.core.dna.MeetDnaEngine
import com.elysium369.meet.core.twin.VehicleTwinEngine
import com.elysium369.meet.data.local.dao.VehicleDnaDao
import com.elysium369.meet.data.local.dao.VehicleTwinDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Serializable
data class DistributionSnapshot(
    val pid: String,
    val mean: Double,
    val stdDev: Double,
    val confidence: Double,
    val sampleCount: Int = 0,
    val lastUpdatedMs: Long = 0L,
)

@Serializable
data class BaselineDeviation(
    val pid: String,
    val currentValue: Double,
    val baselineMean: Double,
    val baselineStdDev: Double,
    val zScore: Double,
    val isSignificantDeviation: Boolean,
    val deviationDirection: String, // "HIGH", "LOW", "NORMAL"
)

/**
 * VehicleBaselineEngine — Unified facade over DNA Engine (EWMA/iForest profiles) and Digital Twin (Kalman/HW profiles).
 *
 * Provides a canonical statistical distribution for any vehicle parameter,
 * enabling EVAIR agents to determine whether an observed value is normal FOR THIS SPECIFIC VEHICLE.
 */
@Singleton
class VehicleBaselineEngine @Inject constructor(
    private val dnaEngine: MeetDnaEngine,
    private val twinEngine: VehicleTwinEngine,
    private val dnaDao: VehicleDnaDao,
    private val twinDao: VehicleTwinDao,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    suspend fun getBaselineDistribution(vehicleId: String, pid: String): DistributionSnapshot? = withContext(Dispatchers.IO) {
        // 1. Try vehicle twin profile first
        val twinProfile = twinDao.getTwinProfile(vehicleId)
        if (twinProfile != null) {
            val baselines = try {
                json.decodeFromString<Map<String, Float>>(twinProfile.baselineJson)
            } catch (_: Exception) { emptyMap() }

            val variances = try {
                json.decodeFromString<Map<String, Float>>(twinProfile.varianceJson)
            } catch (_: Exception) { emptyMap() }

            val mean = baselines[pid] ?: baselines[pid.uppercase()]
            val std = variances[pid] ?: variances[pid.uppercase()]

            if (mean != null && std != null) {
                return@withContext DistributionSnapshot(
                    pid = pid,
                    mean = mean.toDouble(),
                    stdDev = std.toDouble(),
                    confidence = twinProfile.confidence,
                    lastUpdatedMs = twinProfile.lastTrainingDate
                )
            }
        }

        // 2. Try DNA profile as fallback
        val dnaProfile = dnaDao.getDnaProfile(vehicleId)
        if (dnaProfile != null) {
            val baselines = try {
                json.decodeFromString<Map<String, com.elysium369.meet.core.dna.SensorDnaBaseline>>(dnaProfile.baselineJson)
            } catch (_: Exception) { emptyMap() }

            val sensorBase = baselines[pid] ?: baselines[pid.uppercase()]
            if (sensorBase != null) {
                return@withContext DistributionSnapshot(
                    pid = pid,
                    mean = sensorBase.mean.toDouble(),
                    stdDev = sensorBase.stdDev.toDouble(),
                    confidence = dnaProfile.confidence,
                    lastUpdatedMs = dnaProfile.lastTrainingDate
                )
            }
        }

        null
    }

    suspend fun getAllBaselines(vehicleId: String): Map<String, DistributionSnapshot> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, DistributionSnapshot>()

        val twinProfile = twinDao.getTwinProfile(vehicleId)
        if (twinProfile != null) {
            val baselines = try {
                json.decodeFromString<Map<String, Float>>(twinProfile.baselineJson)
            } catch (_: Exception) { emptyMap() }
            val variances = try {
                json.decodeFromString<Map<String, Float>>(twinProfile.varianceJson)
            } catch (_: Exception) { emptyMap() }

            for ((pid, mean) in baselines) {
                val std = variances[pid] ?: 1.0f
                result[pid] = DistributionSnapshot(
                    pid = pid,
                    mean = mean.toDouble(),
                    stdDev = std.toDouble(),
                    confidence = twinProfile.confidence,
                    lastUpdatedMs = twinProfile.lastTrainingDate
                )
            }
        }

        result
    }

    suspend fun evaluateDeviation(vehicleId: String, pid: String, value: Double): BaselineDeviation? {
        val base = getBaselineDistribution(vehicleId, pid) ?: return null
        val std = if (base.stdDev > 0.001) base.stdDev else 1.0
        val z = (value - base.mean) / std
        val isSignificant = abs(z) >= 2.5
        val dir = when {
            z > 2.0 -> "HIGH"
            z < -2.0 -> "LOW"
            else -> "NORMAL"
        }

        return BaselineDeviation(
            pid = pid,
            currentValue = value,
            baselineMean = base.mean,
            baselineStdDev = base.stdDev,
            zScore = z,
            isSignificantDeviation = isSignificant,
            deviationDirection = dir
        )
    }
}
