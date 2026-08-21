package com.elysium369.meet.core.evair.telemetry

import com.elysium369.meet.core.evair.domain.DataQuality
import com.elysium369.meet.core.evair.domain.SignalFeatures
import com.elysium369.meet.core.evair.domain.TelemetryPoint
import com.elysium369.meet.core.evair.domain.TelemetryWindow
import kotlin.math.sqrt

/**
 * FeatureExtractor — Computes compact statistical and temporal features from telemetry windows.
 *
 * This provides the cognitive layer (Antigravity/EVAIR agents) with dense, high-signal
 * representations of sensor trends rather than flooding context with raw PID points.
 */
object FeatureExtractor {

    fun extract(window: TelemetryWindow): SignalFeatures {
        return extractFromPoints(
            pid = window.pid,
            parameterName = window.parameterName,
            unit = window.unit,
            samples = window.samples,
            windowDurationMs = window.durationMs
        )
    }

    fun extractFromPoints(
        pid: String,
        parameterName: String,
        unit: String,
        samples: List<TelemetryPoint>,
        windowDurationMs: Long = 0L,
    ): SignalFeatures {
        val totalCount = samples.size
        if (totalCount == 0) {
            return SignalFeatures(
                pid = pid,
                parameterName = parameterName,
                unit = unit,
                windowDurationMs = windowDurationMs,
                count = 0,
                min = 0.0,
                max = 0.0,
                mean = 0.0,
                variance = 0.0,
                stdDev = 0.0,
                slopePerSecond = 0.0,
                delta = 0.0,
                rateOfChange = 0.0,
                p05 = null,
                p50 = null,
                p95 = null,
                missingRate = 1.0,
                staleRate = 0.0
            )
        }

        var staleCount = 0
        var invalidCount = 0
        val validValues = ArrayList<Double>(totalCount)
        val validPoints = ArrayList<TelemetryPoint>(totalCount)

        for (p in samples) {
            when (p.quality) {
                DataQuality.STALE -> staleCount++
                DataQuality.INVALID -> invalidCount++
                else -> {
                    validValues.add(p.value)
                    validPoints.add(p)
                }
            }
        }

        val validCount = validValues.size
        if (validCount == 0) {
            return SignalFeatures(
                pid = pid,
                parameterName = parameterName,
                unit = unit,
                windowDurationMs = windowDurationMs,
                count = totalCount,
                min = 0.0,
                max = 0.0,
                mean = 0.0,
                variance = 0.0,
                stdDev = 0.0,
                slopePerSecond = 0.0,
                delta = 0.0,
                rateOfChange = 0.0,
                p05 = null,
                p50 = null,
                p95 = null,
                missingRate = invalidCount.toDouble() / totalCount,
                staleRate = staleCount.toDouble() / totalCount
            )
        }

        var minVal = Double.MAX_VALUE
        var maxVal = -Double.MAX_VALUE
        var sum = 0.0

        for (v in validValues) {
            if (v < minVal) minVal = v
            if (v > maxVal) maxVal = v
            sum += v
        }

        val mean = sum / validCount

        var sumSquaredDiff = 0.0
        for (v in validValues) {
            val diff = v - mean
            sumSquaredDiff += diff * diff
        }

        val variance = if (validCount > 1) sumSquaredDiff / (validCount - 1) else 0.0
        val stdDev = sqrt(variance)

        // Quantiles
        validValues.sort()
        val p05 = getPercentile(validValues, 0.05)
        val p50 = getPercentile(validValues, 0.50)
        val p95 = getPercentile(validValues, 0.95)

        // Temporal dynamics: slope and delta
        val firstPoint = validPoints.first()
        val lastPoint = validPoints.last()
        val delta = lastPoint.value - firstPoint.value

        val effectiveDurationMs = if (windowDurationMs > 0) {
            windowDurationMs
        } else {
            (lastPoint.wallClockTimestampMs - firstPoint.wallClockTimestampMs).coerceAtLeast(0)
        }

        val durationSeconds = effectiveDurationMs / 1000.0
        val slopePerSec = if (durationSeconds > 0.05) delta / durationSeconds else 0.0
        val rateOfChange = if (firstPoint.value != 0.0) delta / firstPoint.value else 0.0

        return SignalFeatures(
            pid = pid,
            parameterName = parameterName,
            unit = unit,
            windowDurationMs = effectiveDurationMs,
            count = totalCount,
            min = minVal,
            max = maxVal,
            mean = mean,
            variance = variance,
            stdDev = stdDev,
            slopePerSecond = slopePerSec,
            delta = delta,
            rateOfChange = rateOfChange,
            p05 = p05,
            p50 = p50,
            p95 = p95,
            missingRate = invalidCount.toDouble() / totalCount,
            staleRate = staleCount.toDouble() / totalCount
        )
    }

    private fun getPercentile(sortedValues: List<Double>, p: Double): Double {
        if (sortedValues.isEmpty()) return 0.0
        if (sortedValues.size == 1) return sortedValues[0]
        val index = (sortedValues.size - 1) * p
        val lower = index.toInt()
        val upper = (lower + 1).coerceAtMost(sortedValues.size - 1)
        val weight = index - lower
        return sortedValues[lower] * (1.0 - weight) + sortedValues[upper] * weight
    }
}
