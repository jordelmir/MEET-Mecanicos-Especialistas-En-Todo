package com.elysium369.meet.core.evair.domain

import kotlinx.serialization.Serializable

/**
 * VehicleEvent — Extensible sealed interface for significant vehicle events.
 *
 * Events are the primary trigger for AI agent invocation. The system does NOT
 * wake Antigravity for every PID — only when a meaningful event occurs.
 *
 * Design principles:
 * - Each event carries its own evidence (timestamps, measurements)
 * - Events are contextual (not just "value > threshold" — they consider
 *   baseline, vehicle conditions, duration, and correlated signals)
 * - Events are serializable for persistence and replay testing
 *
 * This replaces the narrow CopilotEvent (7 hardcoded types) with an
 * extensible hierarchy that EVAIR agents can reason about.
 */
@Serializable
sealed interface VehicleEvent {
    val timestampMs: Long
    val severity: EventSeverity
    val source: EventSource

    @Serializable
    data class MisfireSuspected(
        override val timestampMs: Long,
        override val severity: EventSeverity = EventSeverity.WARNING,
        override val source: EventSource = EventSource.TELEMETRY_ANALYSIS,
        val cylinders: Map<Int, Int> = emptyMap(),
        val rpmVariance: Double? = null,
        val evidence: List<String> = emptyList(),
    ) : VehicleEvent

    @Serializable
    data class OverheatRisk(
        override val timestampMs: Long,
        override val severity: EventSeverity = EventSeverity.CRITICAL,
        override val source: EventSource = EventSource.TELEMETRY_ANALYSIS,
        val coolantTempC: Double,
        val risingRateCPerMinute: Double,
        val baselineCoolantC: Double? = null,
    ) : VehicleEvent

    @Serializable
    data class ChargingSystemAnomaly(
        override val timestampMs: Long,
        override val severity: EventSeverity = EventSeverity.WARNING,
        override val source: EventSource = EventSource.TELEMETRY_ANALYSIS,
        val voltage: Double,
        val rpm: Double? = null,
        val baselineVoltage: Double? = null,
        val trend: String? = null,
    ) : VehicleEvent

    @Serializable
    data class FuelTrimAnomaly(
        override val timestampMs: Long,
        override val severity: EventSeverity = EventSeverity.WARNING,
        override val source: EventSource = EventSource.TELEMETRY_ANALYSIS,
        val stft: Double? = null,
        val ltft: Double? = null,
        val bank: Int = 1,
        val baselineStft: Double? = null,
        val baselineLtft: Double? = null,
    ) : VehicleEvent

    @Serializable
    data class SensorCorrelationFailure(
        override val timestampMs: Long,
        override val severity: EventSeverity = EventSeverity.INFO,
        override val source: EventSource = EventSource.TELEMETRY_ANALYSIS,
        val sensors: List<String>,
        val expectedCorrelation: String? = null,
        val observedBehavior: String? = null,
    ) : VehicleEvent

    @Serializable
    data class DtcAppeared(
        override val timestampMs: Long,
        override val severity: EventSeverity = EventSeverity.WARNING,
        override val source: EventSource = EventSource.DTC_SCAN,
        val code: String,
        val category: DtcCategory,
        val description: String? = null,
    ) : VehicleEvent

    @Serializable
    data class DtcDisappeared(
        override val timestampMs: Long,
        override val severity: EventSeverity = EventSeverity.INFO,
        override val source: EventSource = EventSource.DTC_SCAN,
        val code: String,
    ) : VehicleEvent

    @Serializable
    data class BaselineDrift(
        override val timestampMs: Long,
        override val severity: EventSeverity = EventSeverity.INFO,
        override val source: EventSource = EventSource.BASELINE_COMPARISON,
        val pid: String,
        val parameterName: String,
        val baselineMean: Double,
        val currentMean: Double,
        val deviationSigma: Double,
        val driftDirectionLabel: String? = null,
    ) : VehicleEvent

    @Serializable
    data class ConnectivityDegraded(
        override val timestampMs: Long,
        override val severity: EventSeverity = EventSeverity.INFO,
        override val source: EventSource = EventSource.ADAPTER_MONITORING,
        val errorRate: Double,
        val latencyMs: Long,
    ) : VehicleEvent

    @Serializable
    data class IsolationForestAnomaly(
        override val timestampMs: Long,
        override val severity: EventSeverity = EventSeverity.WARNING,
        override val source: EventSource = EventSource.ANOMALY_DETECTION,
        val anomalyScore: Double,
        val contributingPids: List<String> = emptyList(),
    ) : VehicleEvent
}

@Serializable
enum class EventSeverity {
    INFO,
    WARNING,
    CRITICAL,
}

@Serializable
enum class EventSource {
    TELEMETRY_ANALYSIS,
    DTC_SCAN,
    BASELINE_COMPARISON,
    ANOMALY_DETECTION,
    ADAPTER_MONITORING,
    USER_REPORT,
    DIAGNOSTIC_ENGINE,
}
