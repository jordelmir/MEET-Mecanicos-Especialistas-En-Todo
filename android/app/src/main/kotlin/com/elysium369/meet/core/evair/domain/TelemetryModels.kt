package com.elysium369.meet.core.evair.domain

import kotlinx.serialization.Serializable

/**
 * TelemetryPoint — A single timestamped sensor observation.
 *
 * Uses monotonic timestamps for interval calculations (never wall clock alone).
 * Wall clock is kept for display and persistence but NEVER for computing deltas.
 */
@Serializable
data class TelemetryPoint(
    val monotonicTimestampNs: Long,
    val wallClockTimestampMs: Long,
    val pid: String,
    val value: Double,
    val unit: String,
    val quality: DataQuality,
)

@Serializable
enum class DataQuality {
    GOOD,
    ESTIMATED,
    STALE,
    INVALID,
}

/**
 * TelemetryWindow — A bounded slice of telemetry samples with metadata.
 *
 * Used by FeatureExtractor and MCP tools to provide statistical summaries
 * without sending raw samples to the LLM.
 */
@Serializable
data class TelemetryWindow(
    val pid: String,
    val parameterName: String,
    val unit: String,
    val startTimestampMs: Long,
    val endTimestampMs: Long,
    val durationMs: Long,
    val sampleCount: Int,
    val samples: List<TelemetryPoint>,
    val qualitySummary: QualitySummary,
) {
    init {
        require(durationMs >= 0) { "Duration must be non-negative" }
    }
}

@Serializable
data class QualitySummary(
    val goodCount: Int = 0,
    val estimatedCount: Int = 0,
    val staleCount: Int = 0,
    val invalidCount: Int = 0,
) {
    val totalCount: Int get() = goodCount + estimatedCount + staleCount + invalidCount
    val goodRate: Double get() = if (totalCount > 0) goodCount.toDouble() / totalCount else 0.0
}

/**
 * SignalFeatures — Statistical features extracted from a TelemetryWindow.
 *
 * This is what gets sent to the AI agent instead of raw samples.
 * Much more compact and meaningful for diagnostic reasoning.
 */
@Serializable
data class SignalFeatures(
    val pid: String,
    val parameterName: String,
    val unit: String,
    val windowDurationMs: Long,
    val count: Int,
    val min: Double,
    val max: Double,
    val mean: Double,
    val variance: Double,
    val stdDev: Double,
    val slopePerSecond: Double,
    val delta: Double,
    val rateOfChange: Double,
    val p05: Double? = null,
    val p50: Double? = null,
    val p95: Double? = null,
    val missingRate: Double = 0.0,
    val staleRate: Double = 0.0,
)
