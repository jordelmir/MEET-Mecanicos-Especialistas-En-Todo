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
enum class DnaBusinessStage(val label: String) {
    DISCONNECTED("Sin enlace OBD"),
    COLLECTING("Recolectando firma"),
    READY_TO_TRAIN("Listo para calibrar"),
    QUICK_PROFILE("Perfil rapido activo"),
    MONITORING("Monitoreo DNA activo"),
    ATTENTION("Atencion preventiva"),
    ERROR("Revision requerida")
}

@Serializable
data class DnaEvaluationResult(
    val isCalibrated: Boolean,
    val confidence: Double = 0.0,
    val anomalyScore: Double = 0.0,
    val healthScore: Int = 100,
    val isAnomalous: Boolean = false,
    val sensorStates: List<SensorDnaState> = emptyList(),
    val message: String = "",
    val stage: DnaBusinessStage = DnaBusinessStage.DISCONNECTED,
    val sampleCount: Int = 0,
    val requiredSamples: Int = 15,
    val liveSensorCount: Int = 0,
    val requiredLiveSensors: Int = 5,
    val canTrain: Boolean = false,
    val nextAction: String = ""
)

@Singleton
class MeetDnaEngine @Inject constructor(
    private val sensorHistoryDao: SensorHistoryDao,
    private val vehicleDnaDao: VehicleDnaDao
) {
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    // Map of PID -> Label
    private val dnaPids = mapOf(
        "0105" to "Temperatura refrigerante",
        "0142" to "Voltaje de bateria",
        "010C" to "RPM del motor",
        "0107" to "Ajuste combustible LTFT",
        "010B" to "Sensor MAP",
        "0110" to "Sensor MAF",
        "010F" to "Temperatura aire admision"
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
    /** Minimum vectors needed for DNA calibration — lowered from 50 for faster initial results */
    private val MIN_VECTORS_QUICK = 15
    private val MIN_VECTORS_FULL = 50

    /** Observable progress for the UI */
    data class TrainingProgress(val currentSamples: Int, val requiredSamples: Int, val isQuickMode: Boolean) {
        val percentage: Float get() = (currentSamples.toFloat() / requiredSamples).coerceIn(0f, 1f)
        val isReady: Boolean get() = currentSamples >= requiredSamples
        val missingSamples: Int get() = (requiredSamples - currentSamples).coerceAtLeast(0)
    }

    /**
     * Check how many aligned vectors are available for a vehicle without training.
     * Used by the UI to show progress.
     */
    suspend fun getTrainingProgress(vehicleId: String): TrainingProgress = withContext(Dispatchers.IO) {
        val rawDataByPid = mutableMapOf<String, List<com.elysium369.meet.data.local.entities.SensorHistoryEntity>>()
        for (pid in dnaPids.keys) {
            rawDataByPid[pid] = sensorHistoryDao.getSensorTrend(vehicleId, pid)
        }
        val alignedVectors = alignTimeSeries(rawDataByPid)
        val existing = vehicleDnaDao.getDnaProfile(vehicleId)
        val isQuick = existing == null // First time = quick mode
        val required = if (isQuick) MIN_VECTORS_QUICK else MIN_VECTORS_FULL
        TrainingProgress(alignedVectors.size, required, isQuick)
    }

    /**
     * Train the vehicle DNA signature using historical sensor readings.
     * Aligns time-series vectors and fits a Lightweight Isolation Forest.
     * @param quickMode If true, use the lower threshold (15 vectors) for rapid initial calibration
     */
    suspend fun trainDnaProfile(vehicleId: String, quickMode: Boolean = false): VehicleDnaProfileEntity? = withContext(Dispatchers.IO) {
        try {
            val minRequired = if (quickMode) MIN_VECTORS_QUICK else MIN_VECTORS_FULL
            Log.d("ElysiumDnaEngine", "Starting DNA profile training for vehicle: $vehicleId (quickMode=$quickMode, minRequired=$minRequired)")

            // 1. Fetch historical sensor trends
            val rawDataByPid = mutableMapOf<String, List<com.elysium369.meet.data.local.entities.SensorHistoryEntity>>()
            for (pid in dnaPids.keys) {
                rawDataByPid[pid] = sensorHistoryDao.getSensorTrend(vehicleId, pid)
            }

            // 2. Align readings by session and 5-second buckets
            val alignedVectors = alignTimeSeries(rawDataByPid)
            val sampleSize = alignedVectors.size

            Log.d("ElysiumDnaEngine", "Aligned vectors size for training: $sampleSize (need $minRequired)")
            if (sampleSize < minRequired) {
                Log.w("ElysiumDnaEngine", "Insufficient data to train DNA profile. Required >= $minRequired, found $sampleSize")
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
            // Use fewer trees for quick mode to speed up initial calibration
            val numTrees = if (quickMode) 30 else 60
            val forest = LightweightIsolationForest(numTrees = numTrees, subSampleSize = 128.coerceAtMost(sampleSize))
            forest.fit(standardizedVectors)

            // 6. Serialize and persist DNA Profile
            val baselineJsonStr = json.encodeToString(baseline.mapValues { it.value.mean })
            val varianceJsonStr = json.encodeToString(baseline.mapValues { it.value.stdDev })
            val forestJsonStr = json.encodeToString(forest)
            
            // Confidence: quick mode maxes out at 60%, full mode at 100%
            val maxConfidence = if (quickMode) 60.0 else 100.0
            val confidence = (sampleSize / 500.0 * 100.0).coerceIn(5.0, maxConfidence)

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
            Log.i("ElysiumDnaEngine", "DNA Profile trained successfully with confidence: ${String.format("%.1f", confidence)}% (quickMode=$quickMode)")
            return@withContext profile

        } catch (e: Exception) {
            Log.e("ElysiumDnaEngine", "Failed to train DNA profile", e)
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
        } ?: return@withContext buildProvisionalEvaluation(
            vehicleId = vehicleId,
            currentLiveData = currentLiveData,
            progress = getTrainingProgress(vehicleId)
        )

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

                val rawVal = resolveLiveValue(currentLiveData, pid) ?: mean

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
                message = message,
                stage = when {
                    isAnomalous -> DnaBusinessStage.ATTENTION
                    profile.confidence < 65.0 -> DnaBusinessStage.QUICK_PROFILE
                    else -> DnaBusinessStage.MONITORING
                },
                liveSensorCount = sensorStates.count { resolveLiveValue(currentLiveData, it.pid) != null },
                requiredLiveSensors = 5,
                canTrain = true,
                nextAction = when {
                    isAnomalous -> "Revise el sensor dominante, DTCs activos y condiciones fisicas antes de borrar codigos."
                    profile.confidence < 65.0 -> "Siga conduciendo en condiciones normales y re-entrene para convertir el perfil rapido en perfil completo."
                    else -> "Mantenga el monitoreo activo durante la prueba de ruta o diagnostico en taller."
                }
            )

        } catch (e: Exception) {
            Log.e("ElysiumDnaEngine", "Error during DNA inference evaluation", e)
            return@withContext DnaEvaluationResult(
                isCalibrated = false,
                healthScore = 0,
                stage = DnaBusinessStage.ERROR,
                message = "Elysium Vanguard DNA no pudo evaluar esta lectura: ${e.message ?: "error interno"}",
                nextAction = "Reintente la lectura y valide que haya telemetria OBD estable."
            )
        }
    }

    private fun buildProvisionalEvaluation(
        vehicleId: String,
        currentLiveData: Map<String, Float>,
        progress: TrainingProgress
    ): DnaEvaluationResult {
        if (currentLiveData.isEmpty()) {
            return DnaEvaluationResult(
                isCalibrated = false,
                healthScore = 0,
                stage = DnaBusinessStage.DISCONNECTED,
                sampleCount = progress.currentSamples,
                requiredSamples = progress.requiredSamples,
                liveSensorCount = 0,
                requiredLiveSensors = 5,
                canTrain = progress.isReady,
                message = if (progress.isReady) {
                    "Hay historial suficiente para calibrar, pero la lectura actual requiere conectar el scanner OBD-II."
                } else {
                    "Conecta el scanner OBD-II y espera telemetria viva para iniciar la firma Elysium Vanguard DNA."
                },
                nextAction = if (progress.isReady) {
                    "Conecta el adaptador y presiona calibrar para cerrar la firma."
                } else {
                    "Realiza una ruta normal hasta reunir ${progress.missingSamples} muestras alineadas mas."
                }
            )
        }

        val vehicleEwma = ewmaCache.getOrPut(vehicleId) { mutableMapOf() }
        val sensorStates = dnaPids.mapNotNull { (pid, label) ->
            val rawVal = resolveLiveValue(currentLiveData, pid) ?: return@mapNotNull null
            val previous = vehicleEwma[pid] ?: rawVal
            val ewma = 0.2f * rawVal + 0.8f * previous
            vehicleEwma[pid] = ewma
            SensorDnaState(
                pid = pid,
                label = label,
                baselineMean = rawVal,
                baselineStdDev = provisionalStdDev(pid),
                currentValue = rawVal,
                ewmaValue = ewma,
                zScore = 0f
            )
        }

        if (sensorStates.isEmpty()) {
            return DnaEvaluationResult(
                isCalibrated = false,
                healthScore = 0,
                stage = DnaBusinessStage.COLLECTING,
                sampleCount = progress.currentSamples,
                requiredSamples = progress.requiredSamples,
                liveSensorCount = 0,
                requiredLiveSensors = 5,
                canTrain = progress.isReady,
                message = "El enlace OBD esta activo, pero todavia no llegaron PIDs suficientes para Elysium Vanguard DNA.",
                nextAction = "Mantenga contacto ON o motor encendido segun el sensor y espere lecturas de RPM, voltaje, temperatura y carga."
            )
        }

        val penalty = sensorStates.sumOf { provisionalPenalty(it.pid, it.currentValue) }
        val healthScore = (100 - penalty).coerceIn(35, 100)
        val coverage = sensorStates.size.toDouble() / dnaPids.size.toDouble()

        return DnaEvaluationResult(
            isCalibrated = false,
            confidence = (coverage * 35.0).coerceIn(10.0, 35.0),
            anomalyScore = 0.0,
            healthScore = healthScore,
            isAnomalous = false,
            sensorStates = sensorStates,
            message = if (progress.isReady) {
                "Elysium Vanguard DNA ya tiene ${progress.currentSamples}/${progress.requiredSamples} muestras alineadas. Puede calibrar la firma real."
            } else {
                "Elysium Vanguard DNA esta recolectando base real: ${progress.currentSamples}/${progress.requiredSamples} muestras y ${sensorStates.size}/${dnaPids.size} sensores vivos."
            },
            stage = if (progress.isReady) DnaBusinessStage.READY_TO_TRAIN else DnaBusinessStage.COLLECTING,
            sampleCount = progress.currentSamples,
            requiredSamples = progress.requiredSamples,
            liveSensorCount = sensorStates.size,
            requiredLiveSensors = 5,
            canTrain = progress.isReady,
            nextAction = if (progress.isReady) {
                "Calibre ahora; luego re-entrene despues de una ruta completa para subir confianza."
            } else {
                "Conduzca suave en ralenti, ciudad y crucero hasta reunir ${progress.missingSamples} muestras alineadas mas."
            }
        )
    }

    private fun resolveLiveValue(data: Map<String, Float>, pid: String): Float? {
        val compact = pid.uppercase().replace(" ", "")
        val core = compact.removePrefix("01")
        val aliases = buildList {
            add(pid)
            add(compact)
            add(core)
            if (core.length == 2) add("01$core")
            when (core) {
                "0C" -> addAll(listOf("RPM", "rpm"))
                "0D" -> addAll(listOf("SPEED", "speed", "VELOCIDAD"))
                "05" -> addAll(listOf("COOLANT", "coolant", "ECT"))
                "07" -> addAll(listOf("LTFT", "long_fuel_trim"))
                "0B" -> addAll(listOf("MAP", "map"))
                "10" -> addAll(listOf("MAF", "maf"))
                "0F" -> addAll(listOf("IAT", "iat"))
                "42" -> addAll(listOf("VOLTAGE", "voltage", "CTRL_VOLTAGE", "AT RV", "ATRV", "ELM_VOLTAGE"))
            }
        }
        return aliases.firstNotNullOfOrNull { data[it] }
    }

    private fun provisionalStdDev(pid: String): Float = when (pid) {
        "010C" -> 150f
        "0105", "010F" -> 4f
        "0142" -> 0.25f
        "0107" -> 3f
        "010B" -> 5f
        "0110" -> 2f
        else -> 1f
    }

    private fun provisionalPenalty(pid: String, value: Float): Int = when (pid) {
        "0105" -> when {
            value > 112f || value < -20f -> 35
            value > 104f -> 18
            else -> 0
        }
        "0142" -> when {
            value < 11.5f || value > 15.3f -> 30
            value < 12.0f || value > 14.9f -> 12
            else -> 0
        }
        "010C" -> if (value > 6500f) 15 else 0
        "0107" -> if (abs(value) > 18f) 18 else if (abs(value) > 10f) 8 else 0
        "010B" -> if (value > 115f) 8 else 0
        "0110" -> if (value < 0f) 10 else 0
        "010F" -> if (value > 80f || value < -30f) 8 else 0
        else -> 0
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
