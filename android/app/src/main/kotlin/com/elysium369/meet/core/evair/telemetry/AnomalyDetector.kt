package com.elysium369.meet.core.evair.telemetry

import com.elysium369.meet.core.dna.LightweightIsolationForest
import com.elysium369.meet.core.evair.domain.EventSeverity
import com.elysium369.meet.core.evair.domain.TelemetryPoint
import com.elysium369.meet.core.obd.PidSignalRegistry
import com.elysium369.meet.core.obd.SignalAnalyzer
import com.elysium369.meet.core.twin.VehicleTwinEngine
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class DetectedAnomaly(
    val pid: String,
    val parameterName: String,
    val detectorType: DetectorType,
    val severity: EventSeverity,
    val score: Double,
    val description: String,
    val value: Double,
    val baselineValue: Double? = null,
    val timestampMs: Long = System.currentTimeMillis(),
)

@Serializable
enum class DetectorType {
    ISOLATION_FOREST,
    DIGITAL_TWIN_KALMAN,
    SIGNAL_ANALYZER,
}

@Serializable
data class AnomalyReport(
    val hasAnomaly: Boolean,
    val overallSeverity: EventSeverity,
    val isolationForestScore: Double? = null,
    val anomalies: List<DetectedAnomaly> = emptyList(),
    val contributingPids: List<String> = emptyList(),
    val timestampMs: Long = System.currentTimeMillis(),
)

/**
 * AnomalyDetector — Unified facade over MEET's 3 anomaly detection subsystems:
 * 1. LightweightIsolationForest (unsupervised multidimensional space partitioning)
 * 2. VehicleTwinEngine (per-vehicle Kalman / Holt-Winters / Z-Score tracking)
 * 3. SignalAnalyzer (waveform dynamics, THD, noise, drift, stability)
 *
 * Conflict Resolution Strategy:
 * - Domain-specific specialization:
 *   - Multidimensional sensor correlation -> Isolation Forest
 *   - Baseline deviation / Z-Score per PID -> VehicleTwin
 *   - High-frequency dynamics / THD / jitter -> SignalAnalyzer
 * - Severity Escalation: The maximum severity detected across any engine determines overall report severity.
 */
@Singleton
class AnomalyDetector @Inject constructor(
    private val vehicleTwinEngine: VehicleTwinEngine,
) {
    private val signalAnalyzer = SignalAnalyzer()
    private val isolationForest = LightweightIsolationForest(numTrees = 50, subSampleSize = 128)
    private var isForestTrained = false

    // Standard 7 PIDs for multidimensional vector analysis
    private val vectorPids = listOf(
        "010C", // RPM
        "0104", // Load
        "0105", // Coolant Temp
        "010B", // MAP
        "0110", // MAF
        "0111", // Throttle
        "0142", // Battery Voltage
    )

    /**
     * Trains the Isolation Forest with historical multidimensional frames.
     */
    fun trainIsolationForest(historyFrames: List<Map<String, Float>>) {
        if (historyFrames.isEmpty()) return
        val dataset = historyFrames.mapNotNull { frame ->
            val vector = FloatArray(vectorPids.size)
            var complete = true
            for (i in vectorPids.indices) {
                val pid = vectorPids[i]
                val v = frame[pid] ?: frame[pid.lowercase()]
                if (v != null) {
                    vector[i] = v
                } else {
                    complete = false
                    break
                }
            }
            if (complete) vector else null
        }

        if (dataset.size >= 10) {
            isolationForest.fit(dataset)
            isForestTrained = true
        }
    }

    /**
     * Evaluates a current telemetry frame and recent window across all 3 engines.
     */
    suspend fun evaluateFrame(
        vehicleId: String,
        currentLiveData: Map<String, Float>,
        recentPidsHistory: Map<String, List<TelemetryPoint>> = emptyMap(),
    ): AnomalyReport {
        val detectedAnomalies = mutableListOf<DetectedAnomaly>()
        val contributingPids = mutableSetOf<String>()
        var maxSeverity = EventSeverity.INFO

        // 1. Digital Twin Engine Evaluation
        val twinAnomalies = vehicleTwinEngine.evaluateFrame(vehicleId, currentLiveData)
        for (twin in twinAnomalies) {
            val severity = when (twin.severity.lowercase()) {
                "critical", "high" -> EventSeverity.CRITICAL
                "warning", "medium" -> EventSeverity.WARNING
                else -> EventSeverity.INFO
            }
            if (severity > maxSeverity) maxSeverity = severity
            contributingPids.add(twin.parameter)

            detectedAnomalies.add(
                DetectedAnomaly(
                    pid = twin.parameter,
                    parameterName = twin.parameter,
                    detectorType = DetectorType.DIGITAL_TWIN_KALMAN,
                    severity = severity,
                    score = twin.deviation.toDouble(),
                    description = "Digital twin deviation: ${twin.deviation} on ${twin.parameter}",
                    value = twin.actualValue.toDouble(),
                    baselineValue = twin.expectedValue.toDouble(),
                    timestampMs = twin.timestamp
                )
            )
        }

        // 2. Multidimensional Isolation Forest Evaluation
        var ifScore: Double? = null
        if (isForestTrained) {
            val vector = FloatArray(vectorPids.size)
            var complete = true
            for (i in vectorPids.indices) {
                val pid = vectorPids[i]
                val v = currentLiveData[pid] ?: currentLiveData[pid.lowercase()]
                if (v != null) {
                    vector[i] = v
                } else {
                    complete = false
                    break
                }
            }

            if (complete) {
                val score = isolationForest.computeAnomalyScore(vector)
                ifScore = score
                if (score > 0.65) {
                    val severity = if (score > 0.78) EventSeverity.CRITICAL else EventSeverity.WARNING
                    if (severity > maxSeverity) maxSeverity = severity

                    detectedAnomalies.add(
                        DetectedAnomaly(
                            pid = "MULTIVARIATE",
                            parameterName = "Multidimensional Sensor Correlation",
                            detectorType = DetectorType.ISOLATION_FOREST,
                            severity = severity,
                            score = score,
                            description = "Sensor vector correlation anomaly detected (score: ${"%.3f".format(score)})",
                            value = score,
                            baselineValue = 0.50
                        )
                    )
                }
            }
        }

        // 3. Waveform / Signal Analyzer Evaluation for recent histories
        for ((pid, points) in recentPidsHistory) {
            if (points.size >= 10) {
                val signalInfo = PidSignalRegistry.findByCode(pid) ?: continue
                val floatValues = points.map { it.value.toFloat() }
                val durationMs = (points.last().wallClockTimestampMs - points.first().wallClockTimestampMs).coerceAtLeast(100L)
                val diag = signalAnalyzer.analyze(floatValues, durationMs, signalInfo)

                if (diag.anomalies.isNotEmpty()) {
                    for (a in diag.anomalies) {
                        val severity = when (a.severity.lowercase()) {
                            "critical", "high" -> EventSeverity.CRITICAL
                            "warning", "medium" -> EventSeverity.WARNING
                            else -> EventSeverity.INFO
                        }
                        if (severity > maxSeverity) maxSeverity = severity
                        contributingPids.add(pid)

                        detectedAnomalies.add(
                            DetectedAnomaly(
                                pid = pid,
                                parameterName = signalInfo.name,
                                detectorType = DetectorType.SIGNAL_ANALYZER,
                                severity = severity,
                                score = diag.confidence.toDouble() / 100.0,
                                description = "${a.type.uppercase()}: ${a.description}",
                                value = points.last().value,
                                baselineValue = diag.metrics.mean.toDouble()
                            )
                        )
                    }
                }
            }
        }

        return AnomalyReport(
            hasAnomaly = detectedAnomalies.isNotEmpty(),
            overallSeverity = maxSeverity,
            isolationForestScore = ifScore,
            anomalies = detectedAnomalies,
            contributingPids = contributingPids.toList(),
            timestampMs = System.currentTimeMillis()
        )
    }
}
