package com.elysium369.meet.core.health

import com.elysium369.meet.data.local.dao.HealthSnapshotDao
import com.elysium369.meet.data.local.dao.SensorHistoryDao
import com.elysium369.meet.data.local.entities.HealthSnapshotEntity
import com.elysium369.meet.data.local.entities.SensorHistoryEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * PredictiveHealthEngine — Local-first predictive analytics for vehicle health.
 *
 * This engine operates entirely on-device with ZERO cloud dependencies.
 * It uses simple but effective statistical methods:
 *   - Linear regression over sensor time-series to detect degradation slopes
 *   - Standard deviation anomaly detection for sudden spikes
 *   - Weighted subsystem scoring (Engine 30%, Fuel 25%, Cooling 20%, Electrical 15%, Emissions 10%)
 *
 * The engine produces:
 *   - An overall health score (0-100)
 *   - Per-subsystem scores
 *   - Predictive alerts ("Coolant temp trending +2°C/month → thermostat degradation")
 *   - Time-to-failure estimates when sufficient data exists
 */
@Singleton
class PredictiveHealthEngine @Inject constructor(
    private val sensorHistoryDao: SensorHistoryDao,
    private val healthSnapshotDao: HealthSnapshotDao
) {

    companion object {
        // Subsystem weight distribution (must sum to 1.0)
        private const val W_ENGINE = 0.30f
        private const val W_FUEL = 0.25f
        private const val W_COOLING = 0.20f
        private const val W_ELECTRICAL = 0.15f
        private const val W_EMISSIONS = 0.10f

        // PID → Subsystem mapping
        private val ENGINE_PIDS = setOf("010C", "0111", "0104", "010E", "010B") // RPM, ThrottlePos, Load, Timing, MAP
        private val FUEL_PIDS = setOf("0106", "0107", "0108", "0109", "010A")   // STFT/LTFT B1/B2, FuelPressure
        private val COOLING_PIDS = setOf("0105", "016C")                         // Coolant, OilTemp
        private val ELECTRICAL_PIDS = setOf("0142", "012F")                      // ControlVoltage, FuelLevel
        private val EMISSIONS_PIDS = setOf("0114", "0115", "0134", "0144")       // O2 sensors

        // Critical thresholds for scoring
        private val CRITICAL_RANGES = mapOf(
            "0105" to Pair(75f, 110f),     // Coolant: 75-110°C normal
            "0142" to Pair(12.0f, 14.8f),  // Voltage: 12-14.8V normal
            "010C" to Pair(600f, 6500f),   // RPM: 600-6500 normal
            "0107" to Pair(-10f, 10f),     // LTFT B1: ±10% normal
            "0109" to Pair(-10f, 10f),     // LTFT B2: ±10% normal
            "0111" to Pair(0f, 85f)        // Throttle: 0-85% normal
        )

        // Retention: 90 days of sensor data
        const val RETENTION_DAYS = 90L
        private const val MS_PER_DAY = 86_400_000L
    }

    /**
     * Record a batch of sensor readings from a live session.
     * Called periodically (every 10-30 seconds) during active monitoring.
     */
    suspend fun recordSensorBatch(
        vehicleId: String,
        sessionId: String,
        liveData: Map<String, Float>,
        pidLabels: Map<String, String> = emptyMap(),
        pidUnits: Map<String, String> = emptyMap()
    ) {
        if (liveData.isEmpty()) return

        val now = System.currentTimeMillis()
        val records = liveData.map { (pid, value) ->
            SensorHistoryEntity(
                vehicleId = vehicleId,
                sessionId = sessionId,
                pid = pid,
                pidLabel = pidLabels[pid] ?: resolvePidLabel(pid),
                value = value,
                unit = pidUnits[pid] ?: resolvePidUnit(pid),
                timestamp = now
            )
        }
        sensorHistoryDao.insertAll(records)
    }

    /**
     * Compute a full predictive health assessment for a vehicle.
     * Returns a PredictiveHealthReport with scores, trends, and alerts.
     */
    suspend fun computeHealthReport(
        vehicleId: String,
        currentLiveData: Map<String, Float> = emptyMap(),
        activeDtcCount: Int = 0,
        pendingDtcCount: Int = 0,
        anomalyCount: Int = 0
    ): PredictiveHealthReport {
        val recordedPids = sensorHistoryDao.getRecordedPids(vehicleId)
        val trends = mutableListOf<SensorTrend>()
        val alerts = mutableListOf<PredictiveAlert>()

        // Compute trend for each recorded PID
        for (pid in recordedPids) {
            val sessionAverages = sensorHistoryDao.getSessionAverages(vehicleId, pid)
            if (sessionAverages.size < 2) continue

            val trend = computeTrend(pid, sessionAverages)
            trends.add(trend)

            // Generate predictive alerts for dangerous trends
            val alert = evaluateTrendForAlert(trend, pid)
            if (alert != null) alerts.add(alert)
        }

        // Compute subsystem scores
        val engineScore = computeSubsystemScore(ENGINE_PIDS, trends, currentLiveData)
        val fuelScore = computeSubsystemScore(FUEL_PIDS, trends, currentLiveData)
        val coolingScore = computeSubsystemScore(COOLING_PIDS, trends, currentLiveData)
        val electricalScore = computeSubsystemScore(ELECTRICAL_PIDS, trends, currentLiveData)
        val emissionsScore = computeSubsystemScore(EMISSIONS_PIDS, trends, currentLiveData)

        // Weighted overall score
        var overall = (
            engineScore * W_ENGINE +
            fuelScore * W_FUEL +
            coolingScore * W_COOLING +
            electricalScore * W_ELECTRICAL +
            emissionsScore * W_EMISSIONS
        ).toInt()

        // DTC penalties
        overall -= (activeDtcCount * 20)
        overall -= (pendingDtcCount * 8)
        overall -= (anomalyCount * 5)
        overall = overall.coerceIn(0, 100)

        val report = PredictiveHealthReport(
            overallScore = overall,
            engineScore = engineScore.toInt(),
            fuelScore = fuelScore.toInt(),
            coolingScore = coolingScore.toInt(),
            electricalScore = electricalScore.toInt(),
            emissionsScore = emissionsScore.toInt(),
            trends = trends,
            alerts = alerts,
            dataPointCount = sensorHistoryDao.getRecordCount(vehicleId),
            recordedPidCount = recordedPids.size
        )

        // Persist snapshot
        saveSnapshot(vehicleId, report, activeDtcCount, pendingDtcCount, anomalyCount, currentLiveData)

        return report
    }

    /**
     * Get historical health score progression for charting.
     */
    suspend fun getHealthHistory(vehicleId: String, limit: Int = 30): List<HealthSnapshotEntity> {
        return healthSnapshotDao.getRecentSnapshots(vehicleId, limit)
    }

    /**
     * Get sparkline data for a specific sensor.
     */
    suspend fun getSensorSparkline(vehicleId: String, pid: String, limit: Int = 50): List<Float> {
        return sensorHistoryDao.getRecentReadings(vehicleId, pid, limit)
            .sortedBy { it.timestamp }
            .map { it.value }
    }

    /**
     * Cleanup old data beyond retention period.
     */
    suspend fun performMaintenance() {
        val cutoff = System.currentTimeMillis() - (RETENTION_DAYS * MS_PER_DAY)
        sensorHistoryDao.deleteOlderThan(cutoff)
        healthSnapshotDao.deleteOlderThan(cutoff)
    }

    // ── Private Implementation ──

    private fun computeTrend(pid: String, data: List<SensorHistoryEntity>): SensorTrend {
        val values = data.map { it.value.toDouble() }
        val timestamps = data.map { it.timestamp.toDouble() }

        // Linear regression: y = mx + b
        val regression = linearRegression(timestamps, values)
        val slope = regression.first      // units per millisecond
        val intercept = regression.second

        // Convert slope to units per day for readability
        val slopePerDay = slope * MS_PER_DAY

        // Standard deviation for anomaly detection
        val mean = values.average()
        val stdDev = sqrt(values.map { (it - mean) * (it - mean) }.average())

        // Current value vs historical
        val latestValue = values.last().toFloat()
        val firstValue = values.first().toFloat()

        // Trend direction
        val direction = when {
            slopePerDay > 0.1 -> TrendDirection.RISING
            slopePerDay < -0.1 -> TrendDirection.FALLING
            else -> TrendDirection.STABLE
        }

        return SensorTrend(
            pid = pid,
            label = data.first().pidLabel,
            unit = data.first().unit,
            currentValue = latestValue,
            historicalAverage = mean.toFloat(),
            slopePerDay = slopePerDay.toFloat(),
            standardDeviation = stdDev.toFloat(),
            direction = direction,
            dataPoints = data.size,
            firstRecordedAt = data.first().timestamp,
            lastRecordedAt = data.last().timestamp
        )
    }

    private fun evaluateTrendForAlert(trend: SensorTrend, pid: String): PredictiveAlert? {
        val range = CRITICAL_RANGES[pid] ?: return null
        val (min, max) = range

        // Check if current value is already out of range
        if (trend.currentValue > max || trend.currentValue < min) {
            return PredictiveAlert(
                pid = pid,
                label = trend.label,
                severity = AlertSeverity.CRITICAL,
                message = "${trend.label} fuera de rango: ${String.format("%.1f", trend.currentValue)}${trend.unit} " +
                    "(Normal: ${String.format("%.0f", min)}-${String.format("%.0f", max)}${trend.unit})",
                predictedDaysToFailure = 0
            )
        }

        // Predict time to reach critical threshold based on trend
        if (trend.direction == TrendDirection.RISING && trend.slopePerDay > 0) {
            val daysToMax = ((max - trend.currentValue) / trend.slopePerDay).toInt()
            if (daysToMax in 1..90) {
                return PredictiveAlert(
                    pid = pid,
                    label = trend.label,
                    severity = if (daysToMax < 14) AlertSeverity.HIGH else AlertSeverity.MODERATE,
                    message = "${trend.label} subiendo ${String.format("%.2f", trend.slopePerDay)}${trend.unit}/día. " +
                        "Alcanzará nivel crítico en ~$daysToMax días.",
                    predictedDaysToFailure = daysToMax
                )
            }
        }

        if (trend.direction == TrendDirection.FALLING && trend.slopePerDay < 0) {
            val daysToMin = ((min - trend.currentValue) / trend.slopePerDay).toInt()
            if (daysToMin in 1..90) {
                return PredictiveAlert(
                    pid = pid,
                    label = trend.label,
                    severity = if (daysToMin < 14) AlertSeverity.HIGH else AlertSeverity.MODERATE,
                    message = "${trend.label} bajando ${String.format("%.2f", abs(trend.slopePerDay))}${trend.unit}/día. " +
                        "Alcanzará nivel crítico en ~$daysToMin días.",
                    predictedDaysToFailure = daysToMin
                )
            }
        }

        return null
    }

    private fun computeSubsystemScore(
        pids: Set<String>,
        trends: List<SensorTrend>,
        currentData: Map<String, Float>
    ): Float {
        var score = 100f
        var penalties = 0

        for (pid in pids) {
            // Penalty from current live data being out of range
            val current = currentData[pid]
            val range = CRITICAL_RANGES[pid]
            if (current != null && range != null) {
                val (min, max) = range
                if (current > max) {
                    val overshoot = (current - max) / max * 100
                    score -= overshoot.coerceAtMost(30f)
                    penalties++
                } else if (current < min) {
                    val undershoot = (min - current) / min * 100
                    score -= undershoot.coerceAtMost(30f)
                    penalties++
                }
            }

            // Penalty from negative trends
            val trend = trends.find { it.pid == pid }
            if (trend != null && trend.direction != TrendDirection.STABLE) {
                val trendSeverity = abs(trend.slopePerDay)
                score -= (trendSeverity * 2f).coerceAtMost(15f)
            }
        }

        return score.coerceIn(0f, 100f)
    }

    private fun linearRegression(x: List<Double>, y: List<Double>): Pair<Double, Double> {
        val n = x.size
        if (n < 2) return Pair(0.0, y.firstOrNull() ?: 0.0)

        val sumX = x.sum()
        val sumY = y.sum()
        val sumXY = x.zip(y).sumOf { it.first * it.second }
        val sumX2 = x.sumOf { it * it }

        val denominator = n * sumX2 - sumX * sumX
        if (denominator == 0.0) return Pair(0.0, sumY / n)

        val slope = (n * sumXY - sumX * sumY) / denominator
        val intercept = (sumY - slope * sumX) / n

        return Pair(slope, intercept)
    }

    private suspend fun saveSnapshot(
        vehicleId: String,
        report: PredictiveHealthReport,
        activeDtcCount: Int,
        pendingDtcCount: Int,
        anomalyCount: Int,
        liveData: Map<String, Float>
    ) {
        val summary = try {
            Json.encodeToString(liveData.mapKeys { it.key })
        } catch (_: Exception) { "{}" }

        healthSnapshotDao.insert(
            HealthSnapshotEntity(
                vehicleId = vehicleId,
                sessionId = UUID.randomUUID().toString(),
                overallScore = report.overallScore,
                engineScore = report.engineScore,
                fuelScore = report.fuelScore,
                coolingScore = report.coolingScore,
                electricalScore = report.electricalScore,
                emissionsScore = report.emissionsScore,
                activeDtcCount = activeDtcCount,
                pendingDtcCount = pendingDtcCount,
                anomalyCount = anomalyCount,
                sensorSummaryJson = summary,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    private fun resolvePidLabel(pid: String): String = when (pid) {
        "0104" -> "Carga Motor"
        "0105" -> "Temp. Refrigerante"
        "0106" -> "STFT Banco 1"
        "0107" -> "LTFT Banco 1"
        "0108" -> "STFT Banco 2"
        "0109" -> "LTFT Banco 2"
        "010A" -> "Presión Combustible"
        "010B" -> "MAP"
        "010C" -> "RPM"
        "010D" -> "Velocidad"
        "010E" -> "Avance Encendido"
        "010F" -> "Temp. Aire Admisión"
        "0110" -> "Flujo MAF"
        "0111" -> "Pos. Mariposa"
        "0114" -> "O2 B1S1"
        "0115" -> "O2 B1S2"
        "012F" -> "Nivel Combustible"
        "0134" -> "O2 B1S1 Equiv"
        "0142" -> "Voltaje Control"
        "0144" -> "O2 B1S1 WR"
        "016C" -> "Temp. Aceite"
        else -> "PID $pid"
    }

    private fun resolvePidUnit(pid: String): String = when (pid) {
        "0104", "0106", "0107", "0108", "0109", "0111", "012F" -> "%"
        "0105", "010F", "016C" -> "°C"
        "010A", "010B" -> "kPa"
        "010C" -> "RPM"
        "010D" -> "km/h"
        "010E" -> "°"
        "0110" -> "g/s"
        "0114", "0115", "0134", "0144" -> "V"
        "0142" -> "V"
        else -> ""
    }
}

// ── Data Classes ──

data class PredictiveHealthReport(
    val overallScore: Int,
    val engineScore: Int,
    val fuelScore: Int,
    val coolingScore: Int,
    val electricalScore: Int,
    val emissionsScore: Int,
    val trends: List<SensorTrend>,
    val alerts: List<PredictiveAlert>,
    val dataPointCount: Int,
    val recordedPidCount: Int
)

data class SensorTrend(
    val pid: String,
    val label: String,
    val unit: String,
    val currentValue: Float,
    val historicalAverage: Float,
    val slopePerDay: Float,
    val standardDeviation: Float,
    val direction: TrendDirection,
    val dataPoints: Int,
    val firstRecordedAt: Long,
    val lastRecordedAt: Long
)

data class PredictiveAlert(
    val pid: String,
    val label: String,
    val severity: AlertSeverity,
    val message: String,
    val predictedDaysToFailure: Int
)

enum class TrendDirection { RISING, FALLING, STABLE }
enum class AlertSeverity { MODERATE, HIGH, CRITICAL }
