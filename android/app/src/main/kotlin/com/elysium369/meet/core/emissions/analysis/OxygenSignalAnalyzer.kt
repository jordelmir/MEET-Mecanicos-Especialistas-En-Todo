package com.elysium369.meet.core.emissions.analysis

import kotlin.math.pow
import kotlin.math.sqrt

data class OxygenSample(
    val timestampMs: Long,
    val voltage: Double
)

data class OxygenSignalFeatures(
    val sampleCount: Int,
    val sampleRateHz: Double,
    val insufficientSampleRate: Boolean,
    val p05: Double?,
    val p50: Double?,
    val p95: Double?,
    val amplitude: Double?,
    val mean: Double?,
    val variance: Double?,
    val stdDev: Double?,
    val crossCount: Int,
    val switchHz: Double?,
    val richToLeanMs: Double?,
    val leanToRichMs: Double?,
    val richDwellPct: Double?,
    val leanDwellPct: Double?,
    val quality: Double
)

/**
 * Sliding Window Temporal Analyzer for Oxygen Sensors (O2 B1S1 / B1S2).
 * Strictly guards against false precision when sampling rates are low (e.g. ISO 9141-2).
 */
class OxygenSignalAnalyzer(
    private val minSampleRateHz: Double = 2.0,
    private val crossThresholdVoltage: Double = 0.45
) {

    fun analyze(samples: List<OxygenSample>): OxygenSignalFeatures {
        if (samples.size < 4) {
            return OxygenSignalFeatures(
                sampleCount = samples.size,
                sampleRateHz = 0.0,
                insufficientSampleRate = true,
                p05 = null,
                p50 = null,
                p95 = null,
                amplitude = null,
                mean = null,
                variance = null,
                stdDev = null,
                crossCount = 0,
                switchHz = null,
                richToLeanMs = null,
                leanToRichMs = null,
                richDwellPct = null,
                leanDwellPct = null,
                quality = 0.0
            )
        }

        val durationMs = samples.last().timestampMs - samples.first().timestampMs
        val durationSec = durationMs / 1000.0
        val sampleRateHz = if (durationSec > 0.0) samples.size / durationSec else 0.0
        val isInsufficientRate = sampleRateHz < minSampleRateHz

        val voltages = samples.map { it.voltage }.sorted()
        val n = voltages.size

        fun percentile(p: Double): Double {
            val idx = (p * (n - 1)).toInt()
            return voltages[idx]
        }

        val p05 = percentile(0.05)
        val p50 = percentile(0.50)
        val p95 = percentile(0.95)
        val amplitude = (p95 - p05).coerceAtLeast(0.0)

        val mean = voltages.average()
        val variance = voltages.map { (it - mean).pow(2) }.average()
        val stdDev = sqrt(variance)

        // Count midpoint crossings (0.45V standard for narrowband zirconia O2)
        var crossings = 0
        var richCount = 0
        var leanCount = 0

        val richToLeanTransitions = mutableListOf<Double>()
        val leanToRichTransitions = mutableListOf<Double>()

        for (i in 1 until samples.size) {
            val prev = samples[i - 1]
            val curr = samples[i]

            if (curr.voltage >= crossThresholdVoltage) richCount++ else leanCount++

            val crossedRichToLean = prev.voltage >= crossThresholdVoltage && curr.voltage < crossThresholdVoltage
            val crossedLeanToRich = prev.voltage < crossThresholdVoltage && curr.voltage >= crossThresholdVoltage

            if (crossedRichToLean || crossedLeanToRich) {
                crossings++
                val dt = (curr.timestampMs - prev.timestampMs).toDouble()
                if (crossedRichToLean) richToLeanTransitions.add(dt)
                if (crossedLeanToRich) leanToRichTransitions.add(dt)
            }
        }

        val switchHz = if (durationSec > 0.0 && !isInsufficientRate) {
            (crossings / 2.0) / durationSec
        } else null

        val avgRichToLeanMs = if (!isInsufficientRate && richToLeanTransitions.isNotEmpty()) {
            richToLeanTransitions.average()
        } else null

        val avgLeanToRichMs = if (!isInsufficientRate && leanToRichTransitions.isNotEmpty()) {
            leanToRichTransitions.average()
        } else null

        val richDwell = if (samples.isNotEmpty()) (richCount.toDouble() / samples.size) * 100.0 else null
        val leanDwell = if (samples.isNotEmpty()) (leanCount.toDouble() / samples.size) * 100.0 else null

        val quality = when {
            isInsufficientRate -> 0.3
            amplitude < 0.2 -> 0.4
            else -> 0.95
        }

        return OxygenSignalFeatures(
            sampleCount = samples.size,
            sampleRateHz = sampleRateHz,
            insufficientSampleRate = isInsufficientRate,
            p05 = p05,
            p50 = p50,
            p95 = p95,
            amplitude = amplitude,
            mean = mean,
            variance = variance,
            stdDev = stdDev,
            crossCount = crossings,
            switchHz = switchHz,
            richToLeanMs = avgRichToLeanMs,
            leanToRichMs = avgLeanToRichMs,
            richDwellPct = richDwell,
            leanDwellPct = leanDwell,
            quality = quality
        )
    }
}
