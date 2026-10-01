package com.elysium.nexus.fabric.diagnostics

import com.elysium.nexus.core.device.EvidenceLevel
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.ProtocolBinding

// ─── §58 Professional Diagnostics ────────────────────────────────────────────

/**
 * Comprehensive diagnostic data for a device.
 *
 * This is the professional diagnostics screen data model.
 * It provides technicians and developers with complete
 * visibility into the device relationship.
 */
data class DeviceDiagnostics(
    val deviceId: DeviceId,

    // ── Identity ──────────────────────────────────────────
    val displayName: String,
    val manufacturer: String?,
    val model: String?,
    val modelNumber: String?,
    val serialNumber: String?,
    val firmwareVersion: String?,
    val platform: String?,

    // ── Capabilities ──────────────────────────────────────
    val capabilities: Set<String>,
    val protocolBindings: List<ProtocolBinding>,

    // ── Network ───────────────────────────────────────────
    val networkEndpoints: List<NetworkEndpointDiagnostics>,
    val ipAddress: String?,
    val macAddress: String?,
    val hostname: String?,

    // ── Pairing & Trust ───────────────────────────────────
    val trustState: String,
    val pairingMethod: String?,
    val pairedAtMs: Long?,
    val lastVerifiedMs: Long?,
    val credentialsEstablished: Boolean,

    // ── Latency ───────────────────────────────────────────
    val measuredLatencyMs: LatencyStats?,

    // ── IR Specific ───────────────────────────────────────
    val irProfile: IrDiagnostics?,

    // ── Evidence ──────────────────────────────────────────
    val evidenceLevel: EvidenceLevel,
    val evidenceCount: Int,
    val lastEvidenceAtMs: Long?,

    // ── Route History ─────────────────────────────────────
    val routeHistory: List<RouteDiagnostics>,

    // ── Errors ────────────────────────────────────────────
    val recentErrors: List<ErrorDiagnostics>,
    val errorRate: Double,

    // ── State ─────────────────────────────────────────────
    val lastKnownState: Map<String, String>,
    val stateAge: Long,

    // ── Health ────────────────────────────────────────────
    val healthScore: Double,
    val healthStatus: HealthStatus
)

// ─── Network Endpoint Diagnostics ────────────────────────────────────────────

data class NetworkEndpointDiagnostics(
    val protocol: String,
    val address: String,
    val port: Int?,
    val reachable: Boolean,
    val lastReachableMs: Long?,
    val latencyMs: Double?
)

// ─── Latency Statistics ──────────────────────────────────────────────────────

data class LatencyStats(
    val p50Ms: Double,
    val p95Ms: Double,
    val p99Ms: Double,
    val minMs: Double,
    val maxMs: Double,
    val sampleCount: Int,
    val lastMeasuredMs: Long
)

// ─── IR Diagnostics ──────────────────────────────────────────────────────────

data class IrDiagnostics(
    val profileId: String,
    val brand: String?,
    val deviceType: String?,
    val codeSetCount: Int,
    val totalSignals: Int,
    val verifiedSignals: Int,
    val unverifiedSignals: Int,
    val protocolFamilies: Set<String>,
    val carrierFrequencies: Set<Int>,
    val evidenceBreakdown: Map<String, Int>,
    val lastCalibratedMs: Long?,
    val calibrationMethod: String?
)

// ─── Route Diagnostics ───────────────────────────────────────────────────────

data class RouteDiagnostics(
    val routeType: String,
    val protocol: String,
    val endpoint: String?,
    val score: Double,
    val successCount: Long,
    val failureCount: Long,
    val successRate: Double,
    val lastUsedMs: Long?,
    val lastSuccessMs: Long?,
    val lastFailureMs: Long?,
    val circuitBreakerState: String?
)

// ─── Error Diagnostics ───────────────────────────────────────────────────────

data class ErrorDiagnostics(
    val errorType: String,
    val message: String,
    val route: String?,
    val timestampMs: Long,
    val recoveryAction: String?
)

// ─── Health Status ───────────────────────────────────────────────────────────

enum class HealthStatus {
    HEALTHY,
    DEGRADED,
    UNHEALTHY,
    UNKNOWN
}

// ─── Route Flight Recorder (§57) ────────────────────────────────────────────

/**
 * Complete trace for a single action execution.
 *
 * Records:
 * - Intent → Routes evaluated → Winning route → Command → Transport result → State observation → Latency
 *
 * Example:
 * ```
 * VOLUME_UP
 *
 * LG_WEBOS   score=922
 * IR         score=781
 * CEC        score=640
 *
 * Selected LG_WEBOS
 * RTT 32 ms
 * State 20 → 21
 * CONFIRMED
 * ```
 */
data class ActionTrace(
    val action: String,
    val deviceId: DeviceId,
    val timestampMs: Long,

    // ── Routes Evaluated ──────────────────────────────────
    val candidateRoutes: List<CandidateRoute>,

    // ── Winning Route ─────────────────────────────────────
    val selectedRoute: CandidateRoute,

    // ── Execution ─────────────────────────────────────────
    val commandSent: String?,
    val transportResult: TransportResult,
    val latencyMs: Double,

    // ── State Verification ────────────────────────────────
    val stateBefore: Map<String, String>?,
    val stateAfter: Map<String, String>?,
    val stateConfirmed: Boolean,

    // ── Evidence ──────────────────────────────────────────
    val evidenceGenerated: EvidenceLevel?,

    // ── Fallback ──────────────────────────────────────────
    val fallbackUsed: Boolean,
    val fallbackReason: String?
)

data class CandidateRoute(
    val routeType: String,
    val protocol: String,
    val endpoint: String?,
    val score: Double,
    val confidence: Double,
    val estimatedLatencyMs: Double?,
    val isAvailable: Boolean,
    val failureReason: String?
)

enum class TransportResult {
    SUCCESS,
    TIMEOUT,
    CONNECTION_REFUSED,
    AUTHENTICATION_FAILED,
    DEVICE_UNREACHABLE,
    PROTOCOL_ERROR,
    STATE_MISMATCH,
    PARTIAL_SUCCESS,
    UNKNOWN_ERROR
}

// ─── System Diagnostics ──────────────────────────────────────────────────────

/**
 * System-wide diagnostic snapshot.
 */
data class SystemDiagnostics(
    val timestampMs: Long,

    // ── Device Counts ─────────────────────────────────────
    val totalDevices: Int,
    val onlineDevices: Int,
    val pairedDevices: Int,
    val healthyDevices: Int,

    // ── Protocol Usage ────────────────────────────────────
    val protocolUsage: Map<String, ProtocolUsageStats>,

    // ── Performance ───────────────────────────────────────
    val averageLatencyMs: Double,
    val totalActionsExecuted: Long,
    val successRate: Double,

    // ── Discovery ─────────────────────────────────────────
    val discoveryProviders: List<DiscoveryProviderStatus>,

    // ── Memory ────────────────────────────────────────────
    val catalogSizeBytes: Long,
    val databaseSizeBytes: Long,
    val cacheHitRate: Double
)

data class ProtocolUsageStats(
    val protocol: String,
    val deviceCount: Int,
    val actionCount: Long,
    val successRate: Double,
    val averageLatencyMs: Double
)

data class DiscoveryProviderStatus(
    val provider: String,
    val available: Boolean,
    val lastScanMs: Long?,
    val devicesFound: Int,
    val scanDurationMs: Long?
)
