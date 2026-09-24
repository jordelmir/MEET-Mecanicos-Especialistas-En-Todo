package com.elysium369.meet.core.emissions.preitv

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

data class StabilityResult(
    val stable: Boolean,
    val score: Double,
    val sampleCount: Int,
    val slope: Double?,
    val variation: Double?
)

/**
 * Real-time Mathematical Signal Stability Detector for Gas & RPM Stabilization.
 * Analyzes Coefficient of Variation (CV = sigma / |mu|), linear slope drift,
 * and sample depth to confirm the test vehicle has stabilized.
 */
class SignalStabilityDetector(
    private val maxVariationCv: Double = 0.08,
    private val maxSlopePerSec: Double = 5.0,
    private val minSampleCount: Int = 10
) {

    fun evaluate(samples: List<Pair<Long, Double>>): StabilityResult {
        if (samples.size < minSampleCount) {
            return StabilityResult(
                stable = false,
                score = (samples.size.toDouble() / minSampleCount).coerceIn(0.0, 0.9),
                sampleCount = samples.size,
                slope = null,
                variation = null
            )
        }

        val values = samples.map { it.second }
        val mean = values.average()
        if (abs(mean) < 1e-6) {
            return StabilityResult(stable = false, score = 0.0, sampleCount = samples.size, slope = 0.0, variation = 0.0)
        }

        val variance = values.map { (it - mean).pow(2) }.average()
        val stdDev = sqrt(variance)
        val cv = stdDev / abs(mean)

        // Linear regression slope over time (dt in seconds)
        val t0 = samples.first().first
        val timesSec = samples.map { (it.first - t0) / 1000.0 }
        val tMean = timesSec.average()

        var num = 0.0
        var den = 0.0
        for (i in samples.indices) {
            val dt = timesSec[i] - tMean
            val dy = values[i] - mean
            num += dt * dy
            den += dt * dt
        }
        val slope = if (den > 1e-9) num / den else 0.0

        val cvOk = cv <= maxVariationCv
        val slopeOk = abs(slope) <= maxSlopePerSec
        val isStable = cvOk && slopeOk

        val cvScore = (1.0 - (cv / (maxVariationCv * 2.0))).coerceIn(0.0, 1.0)
        val slopeScore = (1.0 - (abs(slope) / (maxSlopePerSec * 2.0))).coerceIn(0.0, 1.0)
        val combinedScore = (cvScore * 0.6 + slopeScore * 0.4)

        return StabilityResult(
            stable = isStable,
            score = combinedScore,
            sampleCount = samples.size,
            slope = slope,
            variation = cv
        )
    }
}
