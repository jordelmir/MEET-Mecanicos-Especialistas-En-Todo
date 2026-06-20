package com.elysium369.meet.core.dna

import android.content.Context
import android.util.Log
import com.elysium369.meet.data.local.dao.SensorHistoryDao
import com.elysium369.meet.data.local.dao.VehicleDnaDao
import com.elysium369.meet.data.local.entities.VehicleDnaProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

@Serializable
data class SensorDnaBaseline(
    val mean: Float,
    val stdDev: Float
)

@Serializable
data class SensorDnaState(
    val pid: String,
    val label: String,
    val baselineMean: Float,
    val baselineStdDev: Float,
    val currentValue: Float,
    val ewmaValue: Float,
    val zScore: Float
)

@Serializable
data class DnaEvaluationResult(
    val isCalibrated: Boolean,
    val confidence: Double = 0.0,
    val anomalyScore: Double = 0.0,
    val healthScore: Int = 100,
    val isAnomalous: Boolean = false,
    val sensorStates: List<SensorDnaState> = emptyList(),
    val message: String = ""
)

@Singleton
class MeetDnaEngine @Inject constructor(
    private val sensorHistoryDao: SensorHistoryDao,
    private val vehicleDnaDao: VehicleDnaDao
) {
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    // Map of PID -> Label
    private val dnaPids = mapOf(
        "0105" to "Coolant Temp",
        "0142" to "Battery Voltage",
        "010C" to "Engine RPM",
        "0107" to "Fuel Trim LTFT",
        "010B" to "MAP Sensor",
        "0110" to "MAF Sensor",
        "010F" to "Intake Air Temp"
    )

    // Memory cache for real-time running EWMA per vehicle and PID
    private val ewmaCache = mutableMapOf<String, MutableMap<String, Float>>()

    // Profile caching to avoid DB reads on every OBD sensor polling frame
    private var cachedVehicleId: String? = null
    private var cachedProfile: VehicleDnaProfileEntity? = null

    fun clearCache() {
        cachedVehicleId = null
        cachedProfile = null
    }

    /**
     * Train the vehicle DNA signature using historical sensor readings.
     * Aligns time-series vectors and fits a Lightweight Isolation Forest.
     */
    suspend fun trainDnaProfile(vehicleId: String): VehicleDnaProfileEntity? = withContext(Dispatchers.IO) {
        try {
            Log.d("MeetDnaEngine", "Starting DNA profile training for vehicle: $vehicleId")

            // 1. Fetch historical sensor trends
            val rawDataByPid = mutableMapOf<String, List<com.elysium369.meet.data.local.entities.SensorHistoryEntity>>()
            for (pid in dnaPids.keys) {
                rawDataByPid[pid] = sensorHistoryDao.getSensorTrend(vehicleId, pid)
            }

            // 2. Align readings by session and 5-second buckets
            val alignedVectors = alignTimeSeries(rawDataByPid)
            val sampleSize = alignedVectors.size

            Log.d("MeetDnaEngine", "Aligned vectors size for training: $sampleSize")
            if (sampleSize < 50) {
                Log.w("MeetDnaEngine", "Insufficient data to train DNA profile. Required >= 50, found $sampleSize")
                return@withContext null
            }

            // 3. Compute mean (baseline) and standard deviation (variance) for each dimension (sensor)
            val baseline = mutableMapOf<String, SensorDnaBaseline>()
            val numFeatures = dnaPids.keys.size
            val pidList = dnaPids.keys.toList()

            for (i in 0 until numFeatures) {
                val pid = pidList[i]
                val values = alignedVectors.map { it[i] }
                val mean = values.average().toFloat()
                val variance = values.map { (it - mean).let { d -> d * d } }.average()
                val stdDev = sqrt(variance).toFloat().let { if (it == 0f) 0.01f else it } // Avoid divide by zero
                baseline[pid] = SensorDnaBaseline(mean, stdDev)
            }

            // 4. Standardize the aligned vectors for Isolation Forest training
            val standardizedVectors = alignedVectors.map { vector ->
                FloatArray(numFeatures) { i ->
                    val pid = pidList[i]
                    val stats = baseline[pid]!!
                    (vector[i] - stats.mean) / stats.stdDev
                }
            }

            // 5. Fit the Isolation Forest model on standardized data
            val forest = LightweightIsolationForest(numTrees = 60, subSampleSize = 128)
            forest.fit(standardizedVectors)

            // 6. Serialize and persist DNA Profile
            val baselineJsonStr = json.encodeToString(baseline.mapValues { it.value.mean })
            val varianceJsonStr = json.encodeToString(baseline.mapValues { it.value.stdDev })
            val forestJsonStr = json.encodeToString(forest)
            
            // Confidence level ranges from 0 to 100 based on data size (max confidence at 500 samples)
            val confidence = (sampleSize / 500.0 * 100.0).coerceIn(10.0, 100.0)

            val profile = VehicleDnaProfileEntity(
                vehicleId = vehicleId,
                baselineJson = baselineJsonStr,
                varianceJson = varianceJsonStr,
                forestJson = forestJsonStr,
                confidence = confidence,
                lastTrainingDate = System.currentTimeMillis()
            )

            vehicleDnaDao.insertDnaProfile(profile)
            clearCache()
            Log.i("MeetDnaEngine", "DNA Profile trained successfully with confidence: ${String.format("%.1f", confidence)}%")
            return@withContext profile

        } catch (e: Exception) {
            Log.e("MeetDnaEngine", "Failed to train DNA profile", e)
            return@withContext null
        }
    }

    /**
     * Real-time evaluation of the vehicle status comparing live OBD data against baseline signature.
     */
    suspend fun evaluateCurrentStatus(
        vehicleId: String,
        currentLiveData: Map<String, Float>
    ): DnaEvaluationResult = withContext(Dispatchers.IO) {
        val profile = if (cachedVehicleId == vehicleId) {
            cachedProfile
        } else {
            val dbProfile = vehicleDnaDao.getDnaProfile(vehicleId)
            cachedVehicleId = vehicleId
            cachedProfile = dbProfile
            dbProfile
        } ?: return@withContext DnaEvaluationResult(isCalibrated = false)

        try {
            val baselineMeans = json.decodeFromString<Map<String, Float>>(profile.baselineJson)
            val baselineStdDevs = json.decodeFromString<Map<String, Float>>(profile.varianceJson)
            val forest = json.decodeFromString<LightweightIsolationForest>(profile.forestJson)

            val pidList = dnaPids.keys.toList()
            val numFeatures = pidList.size

            // 1. Construct current live vector and compute Z-Score + EWMA
            val currentVector = FloatArray(numFeatures)
            val standardizedVector = FloatArray(numFeatures)
            val sensorStates = mutableListOf<SensorDnaState>()

            val vehicleEwma = ewmaCache.getOrPut(vehicleId) { mutableMapOf() }

            var highestZScore = 0f
            var maxZScorePid = ""

            for (i in 0 until numFeatures) {
                val pid = pidList[i]
                val label = dnaPids[pid]!!
                val mean = baselineMeans[pid] ?: 0f
                val stdDev = baselineStdDevs[pid] ?: 1f

                // Resolve voltage PIDs gracefully if they contain different keys
                val rawVal = when (pid) {
                    "0142" -> currentLiveData["VOLTAGE"] ?: currentLiveData["voltage"] ?: currentLiveData["CTRL_VOLTAGE"] ?: currentLiveData["0142"] ?: mean
                    else -> currentLiveData[pid] ?: currentLiveData[pid.lowercase()] ?: mean
                }

                // Update EWMA (smoothing factor alpha = 0.1)
                val prevEwma = vehicleEwma[pid] ?: rawVal
                val ewma = 0.1f * rawVal + (1f - 0.1f) * prevEwma
                vehicleEwma[pid] = ewma

                // Calculate Z-Score against baseline
                val zScore = (ewma - mean) / stdDev
                val absZ = abs(zScore)
                if (absZ > highestZScore) {
                    highestZScore = absZ
                    maxZScorePid = pid
                }

                currentVector[i] = rawVal
                standardizedVector[i] = zScore

                sensorStates.add(
                    SensorDnaState(
                        pid = pid,
                        label = label,
                        baselineMean = mean,
                        baselineStdDev = stdDev,
                        currentValue = rawVal,
                        ewmaValue = ewma,
                        zScore = zScore
                    )
                )
            }

            // 2. Perform Isolation Forest anomaly inference
            val anomalyScore = forest.computeAnomalyScore(standardizedVector)

            // 3. Determine if vehicle is anomalous (Thresholds: iForest > 0.65 or Z-Score > 3.0)
            val iForestAnomalous = anomalyScore > 0.65
            val zScoreAnomalous = highestZScore > 3.0f
            val isAnomalous = iForestAnomalous || zScoreAnomalous

            // 4. Calculate Dynamic Health Score
            // Base deduction from iForest score + extra penalty if standard deviations exceed threshold
            val anomalyPenalty = (anomalyScore * 80).toInt()
            val zScorePenalty = ((highestZScore - 2.0f).coerceAtLeast(0f) * 10).toInt()
            val healthScore = (100 - anomalyPenalty - zScorePenalty).coerceIn(0, 100)

            val message = when {
                isAnomalous -> {
                    val offendingLabel = dnaPids[maxZScorePid] ?: "parámetros críticos"
                    "⚠️ Alerta: Este vehículo ya no se comporta como normalmente lo hace. Desviación detectada en $offendingLabel."
                }
                highestZScore > 2.0f -> {
                    val softOffendingLabel = dnaPids[maxZScorePid] ?: "parámetros"
                    "⚠️ Comportamiento atípico leve detectado en $softOffendingLabel. Monitoree de cerca."
                }
                else -> {
                    "✅ Comportamiento del vehículo completamente normal y alineado a su firma basal."
                }
            }

            return@withContext DnaEvaluationResult(
                isCalibrated = true,
                confidence = profile.confidence,
                anomalyScore = anomalyScore,
                healthScore = healthScore,
                isAnomalous = isAnomalous,
                sensorStates = sensorStates,
                message = message
            )

        } catch (e: Exception) {
            Log.e("MeetDnaEngine", "Error during DNA inference evaluation", e)
            return@withContext DnaEvaluationResult(isCalibrated = false)
        }
    }

    /**
     * Align historical time-series by grouping data points into 5-second windows (buckets).
     */
    private fun alignTimeSeries(
        rawDataByPid: Map<String, List<com.elysium369.meet.data.local.entities.SensorHistoryEntity>>
    ): List<FloatArray> {
        val vectors = mutableListOf<FloatArray>()
        
        // Group all database entries by sessionId and then bucket by timestamp
        // Session ID -> (Bucket Number -> Map of PID -> Sensor Value)
        val sessionsMap = mutableMapOf<String, MutableMap<Long, MutableMap<String, MutableList<Float>>>>()

        for ((pid, readings) in rawDataByPid) {
            for (reading in readings) {
                val sessionId = reading.sessionId
                // Group in 5-second interval buckets (5000 ms)
                val bucket = reading.timestamp / 5000L
                
                sessionsMap.getOrPut(sessionId) { mutableMapOf() }
                    .getOrPut(bucket) { mutableMapOf() }
                    .getOrPut(pid) { mutableListOf() }
                    .add(reading.value)
            }
        }

        // Combine buckets into full 7-dimensional vectors
        val pidList = dnaPids.keys.toList()
        val numFeatures = pidList.size

        // Calculate global means for imputation of missing variables in buckets
        val globalMeans = rawDataByPid.mapValues { (_, list) ->
            list.map { it.value }.average().toFloat().let { if (it.isNaN()) 0f else it }
        }

        for (sessionBuckets in sessionsMap.values) {
            for (bucketPids in sessionBuckets.values) {
                // To maintain quality, only use buckets that have at least 5 out of the 7 sensors
                if (bucketPids.size < 5) continue

                val vector = FloatArray(numFeatures)
                for (i in 0 until numFeatures) {
                    val pid = pidList[i]
                    val values = bucketPids[pid]
                    if (values != null && values.isNotEmpty()) {
                        vector[i] = values.average().toFloat()
                    } else {
                        // Impute missing values in the bucket with global average of that PID
                        vector[i] = globalMeans[pid] ?: 0f
                    }
                }
                vectors.add(vector)
            }
        }

        return vectors
    }
}
