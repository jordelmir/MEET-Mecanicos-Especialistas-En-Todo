package com.elysium369.meet.core.obd

/**
 * Common data models for OBD2 diagnostics.
 */

data class ReadinessResult(
    val milOn: Boolean,
    val dtcCount: Int,
    val monitors: List<MonitorStatus>
)

data class MonitorStatus(
    val name: String,
    val available: Boolean,
    val complete: Boolean
)

enum class VinReadOutcome {
    VERIFIED,
    NOT_CONNECTED,
    NO_RESPONSE,
    INVALID_RESPONSE,
}

/** Immutable proof of a physical VIN query. A VIN is never inferred from vehicle metadata. */
data class VinReadResult(
    val outcome: VinReadOutcome,
    val vin: String? = null,
    val command: String? = null,
    val header: String? = null,
    val rawResponse: String? = null,
    val protocol: String? = null,
    val capturedAtMonotonicMs: Long,
) {
    val isVerified: Boolean
        get() = outcome == VinReadOutcome.VERIFIED && vin != null
}

internal object VinValidator {
    private val canonicalVin = Regex("^[A-HJ-NPR-Z0-9]{17}$")

    fun normalize(candidate: String): String? = candidate
        .trim()
        .uppercase()
        .takeIf(canonicalVin::matches)
}

data class PhysicalPidReadEvidence(
    val command: String,
    val rawResponse: String,
    val acknowledgedByEcu: Boolean,
    val capturedAtMonotonicMs: Long,
)


data class QosMetrics(
    val cmdsPerSecond: Float = 0f,
    val latencyMs: Int = 0,
    val isStable: Boolean = true,
    // Compatibility and advanced metrics
    val avgLatencyMs: Float = 0f,
    val reliability: Float = 100f,
    val totalRequests: Int = 0,
    val successfulRequests: Int = 0
)

data class ObdCommandDef(
    val command: String,
    val expectedResponse: String,
    val description: String,
    val isSafetyCritical: Boolean = false
)

data class ActiveTest(
    val id: String,
    val name: String,
    val description: String,
    val startCommand: String,
    val stopCommand: String,
    val manufacturer: String? = null,
    val durationMs: Long = 10000L, // Default 10s if not manual
    val monitoredPids: List<String> = emptyList(), // PIDs to watch during test
    val safetyConditions: List<SafetyCondition> = emptyList(),
    /** Source-backed capability pack that authorizes this exact ECU command. */
    val capabilityPackId: String? = null,
    /** Physical/logical ECU target validated by the capability pack. */
    val targetAddress: String? = null,
    /** Signed, operation-specific evidence requirements. Empty means unsafe. */
    val safetyEvidenceRequirements: List<SafetyEvidenceRequirement> = emptyList(),
)

enum class SafetyCondition {
    ENGINE_OFF,
    ENGINE_RUNNING,
    VEHICLE_STATIONARY,
    BATTERY_ABOVE_12V,
    TRANS_IN_PARK
}

enum class SafetySignalPredicate {
    ENGINE_STOPPED,
    ENGINE_RUNNING,
    VEHICLE_STATIONARY,
    BATTERY_MINIMUM,
    TRANSMISSION_IN_PARK,
}

data class SafetyEvidenceRequirement(
    val condition: SafetyCondition,
    val signalAliases: Set<String>,
    val maxAgeMs: Long,
    val acceptedQualities: Set<TelemetryQuality>,
    val acceptedSources: Set<ObdDataSource>,
    val predicate: SafetySignalPredicate,
    val threshold: Double? = null,
)

data class ActiveTestStatus(
    val isActive: Boolean = false,
    val progress: Float = 0f,
    val message: String = "",
    val currentValues: Map<String, Float> = emptyMap(),
    val testId: String? = null,
    val phase: ActiveDiagnosticTestPhase = ActiveDiagnosticTestPhase.IDLE,
    val stopVerified: Boolean = false,
)

data class ActiveTestEvidence(
    val evidenceId: String,
    val testId: String,
    val phase: ActiveDiagnosticTestPhase,
    val message: String,
    val monotonicTimestampMs: Long,
    val stopVerified: Boolean,
)

/** Mode 06 is monitor evidence, never an ECU-reported DTC. */
data class MisfireMonitorObservation(
    val cylinder: Int,
    val monitorId: String,
    val testId: String?,
    val observedCount: Int,
    val rawResponse: String,
    val capturedAtMonotonicMs: Long,
    val quality: TelemetryQuality,
)

/**
 * Professional Mode 06 Test Result.
 * Includes parsed metrics, unit conversion and expert insights.
 */
data class Mode06TestResult(
    val mid: String, // Monitor ID (e.g. "$01")
    val tid: String, // Test ID (e.g. "$01")
    val value: Float,
    val minLimit: Float?,
    val maxLimit: Float?,
    val unit: String,
    val passed: Boolean,
    val testName: String,
    val componentName: String,
    val proTip: String? = null,
    val severity: DiagnosticSeverity = DiagnosticSeverity.INFO
)

enum class DiagnosticSeverity {
    INFO, MODERATE, HIGH, CRITICAL
}

data class TopFix(
    val code: String,
    val title: String,
    val fix: String,
    val severity: DiagnosticSeverity,
    val probability: Int,
    val parts: List<String>,
    val laborHours: Float
)

data class DtcAnalysis(
    val code: String,
    val description: String,
    val topFix: TopFix? = null,
    val severity: DiagnosticSeverity = DiagnosticSeverity.INFO
)

class ObdConnectionException(message: String) : Exception(message)

// ═══════════════════════════════════════════════
// MODE $05 — O2 SENSOR MONITORING TEST RESULTS
// ═══════════════════════════════════════════════

/**
 * Result of an individual O2 Sensor Test from Mode $05 (pre-CAN vehicles).
 * Each test measures a specific characteristic of the oxygen sensor.
 */
data class O2SensorTestResult(
    val sensorId: String,           // e.g. "B1S1", "B2S2"
    val bank: Int,                  // 1 or 2
    val sensorNumber: Int,          // 1-4
    val testId: Int,                // TID 01-09
    val testDescription: String,    // Human-readable test name
    val testDescriptionEs: String,  // Spanish description
    val value: Float,               // Measured value
    val minLimit: Float?,           // Lower threshold
    val maxLimit: Float?,           // Upper threshold
    val unit: String,               // "V", "ms", etc.
    val passed: Boolean             // Within limits
)

// ═══════════════════════════════════════════════
// MODE $02 — FREEZE FRAME ENTRY (for UI display)
// ═══════════════════════════════════════════════

/**
 * A single parsed Freeze Frame parameter for display in the UI.
 */
data class FreezeFrameEntry(
    val pid: String,        // e.g. "0D"
    val name: String,       // e.g. "Vehicle Speed"
    val nameEs: String,     // e.g. "Velocidad del Vehículo"
    val value: String,      // Formatted value with unit
    val rawValue: Float?,   // Numeric value if applicable
    val unit: String,       // e.g. "km/h", "°C"
    val icon: String = "📊" // Emoji icon for the parameter
)

// ═══════════════════════════════════════════════
// UDS — UNIFIED DIAGNOSTIC SERVICES RESULTS
// ═══════════════════════════════════════════════

/**
 * Result of a UDS Read Data By Identifier ($22) operation.
 */
data class UdsReadResult(
    val did: String,            // Data Identifier (e.g. "F190")
    val didName: String,        // Human-readable name
    val rawHex: String,         // Raw hex data
    val decodedValue: String,   // Decoded/formatted value
    val success: Boolean
)

/**
 * DTC entry from UDS Service $19 with extended status information.
 */
data class UdsDtcEntry(
    val code: String,           // e.g. "P0300"
    val statusByte: Int,        // Full status byte
    val isConfirmed: Boolean,   // Bit 3
    val isActive: Boolean,      // Bit 0 (testFailed)
    val isPending: Boolean,     // Bit 2 (pendingDTC)
    val isPermanent: Boolean,   // Bit 4
    val warningIndicator: Boolean // Bit 7 (warning lamp)
)

/**
 * Categorized DTC lists separated by type for UI display.
 */
data class CategorizedDtcs(
    val confirmed: List<Pair<String, String>> = emptyList(),   // Mode $03
    val pending: List<Pair<String, String>> = emptyList(),     // Mode $07
    val permanent: List<Pair<String, String>> = emptyList()    // Mode $0A
)

/**
 * Supported UDS Services discovered from the ECU.
 */
data class UdsCapabilities(
    val supportsExtendedSession: Boolean = false,
    val supportsIOControl: Boolean = false,
    val supportsRoutineControl: Boolean = false,
    val supportsReadByIdentifier: Boolean = false,
    val supportsSecurityAccess: Boolean = false,
    val supportsCommunicationControl: Boolean = false,
    val discoveredDids: List<String> = emptyList()
)
