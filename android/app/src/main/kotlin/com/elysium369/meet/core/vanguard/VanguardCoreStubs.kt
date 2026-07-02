package com.elysium369.meet.core.vanguard

/**
 * STUB FILE — Created to satisfy compile-time references in MeetApplication.kt
 * and ObdSession.kt. These classes were referenced by main branch code but never
 * committed. This file provides minimum viable signatures so the build can pass.
 *
 * TODO: Replace stubs with real implementations (see Vanguard Forge project plan
 * in .mavis/reports/latest-loop-report.md and the VanguardEntities.kt companion).
 */

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

// ═══════════════════════════════════════════════════════════════
// VanguardPrivacyGuard
// ═══════════════════════════════════════════════════════════════

class VanguardPrivacyGuard {
    /** Redact VIN/GPS coordinates before logging. Stub returns input unchanged. */
    fun redactForLogging(input: String): String = input

    /** Redact VIN/GPS coordinates before sending to remote telemetry. */
    fun redactForTelemetry(input: String): String = input

    /** Hash the VIN for privacy-preserving identification. */
    fun vinHashOnly(vin: String?): String? = vin?.let { "stub-hash:${it.hashCode()}" }
}

// ═══════════════════════════════════════════════════════════════
// SensorValueState
// ═══════════════════════════════════════════════════════════════

sealed class SensorValueState {
    object Pending : SensorValueState()
    object Timeout : SensorValueState()
    object AdapterError : SensorValueState()
    data class InvalidFormula(val rawValue: String? = null) : SensorValueState()
    data class Supported(val value: Double, val unit: String) : SensorValueState()
    data class Unsupported(val reason: String) : SensorValueState()
}

enum class SensorSource {
    OEM_OBD,
    ENHANCED_OBD,
    COMPUTED,
    USER_INPUT,
    SIMULATED
}

// ═══════════════════════════════════════════════════════════════
// ObdPollingScheduler
// ═══════════════════════════════════════════════════════════════

class ObdPollingScheduler {
    fun nextIntervalMs(pid: String): Long = 100L
    fun shouldPollNow(pid: String): Boolean = true
}

// ═══════════════════════════════════════════════════════════════
// AdapterQualityProfiler
// ═══════════════════════════════════════════════════════════════

class AdapterQualityProfiler {
    fun recordLatency(pid: String, latencyMs: Long) {}
    fun qualityScore(): Double = 1.0
}

// ═══════════════════════════════════════════════════════════════
// DerivedMetricsEngine
// ═══════════════════════════════════════════════════════════════

class DerivedMetricsEngine {
    /** Returns map of metric name -> value. Stub returns empty. */
    fun compute(pids: Map<String, Double>): Map<String, Double> = emptyMap()
}

// ═══════════════════════════════════════════════════════════════
// EcuFailureIntelligence
// ═══════════════════════════════════════════════════════════════

enum class EcuFailureType { NONE, INTERMITTENT, PERSISTENT, PENDING, HISTORICAL }

data class EcuFailureContext(
    val dtcCode: String,
    val freezeFramePidValues: Map<String, Double>,
    val occurredAt: Long
)

data class ClassifiedEcuFailure(
    val failure: EcuFailureContext,
    val type: EcuFailureType,
    val confidence: Double,
    val recommendation: String?
)

class EcuFailureIntelligence {
    fun classify(context: EcuFailureContext): ClassifiedEcuFailure =
        ClassifiedEcuFailure(context, EcuFailureType.NONE, 0.0, null)
}

// ═══════════════════════════════════════════════════════════════
// ObdSessionRecorder / VehicleProfileFingerprint
// ═══════════════════════════════════════════════════════════════

data class ObdSessionStartContext(
    val appVersion: String,
    val vinHash: String?,
    val adapterName: String?,
    val adapterMacHash: String?,
    val adapterFirmware: String?,
    val protocolDetected: String?,
    val consentGranted: Boolean,
    val startedAtMs: Long
)

data class ObdSessionFinishContext(
    val sessionId: String,
    val endedAt: Long,
    val totalPidsRead: Int,
    val errorCount: Int
)

class ObdSessionRecorder {
    fun onStart(ctx: ObdSessionStartContext) {}
    fun onFinish(ctx: ObdSessionFinishContext) {}

    /** Start a Vanguard session and return its sessionId. Stub returns synthetic id. */
    fun startSession(ctx: ObdSessionStartContext): String = "vanguard-stub-${System.currentTimeMillis()}"
}

data class VehicleProfileFingerprint(
    val vehicleId: String,
    val make: String?,
    val model: String?,
    val year: Int?,
    val vin: String? = null,
    val capturedAt: Long
)

// ═══════════════════════════════════════════════════════════════
// VanguardOutboxSyncWorker
// ═══════════════════════════════════════════════════════════════

class VanguardOutboxSyncWorker(
    appContext: android.content.Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val PERIODIC_WORK_NAME = "vanguard_outbox_sync_periodic"
    }

    override suspend fun doWork(): Result {
        // Stub: no-op. Real impl drains VanguardOutboxEntity where status = 'PENDING'
        // and POSTs to Supabase via the commerce API.
        return Result.success()
    }
}