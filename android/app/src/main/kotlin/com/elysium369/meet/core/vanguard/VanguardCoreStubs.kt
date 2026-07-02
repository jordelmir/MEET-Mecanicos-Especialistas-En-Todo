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
    object EcuNoResponse : SensorValueState()
    object NotAvailable : SensorValueState()
    data class InvalidFormula(val rawValue: String? = null) : SensorValueState()
    data class Supported(val value: Number, val unit: String? = null) : SensorValueState()
    data class Unsupported(val reason: String = "") : SensorValueState()

    /** Nombre legible para UI/logs. Usado por ObdSession.kt:3981. */
    val stateName: String
        get() = when (this) {
            is Pending -> "PENDING"
            is Timeout -> "TIMEOUT"
            is AdapterError -> "ADAPTER_ERROR"
            is EcuNoResponse -> "ECU_NO_RESPONSE"
            is NotAvailable -> "NOT_AVAILABLE"
            is InvalidFormula -> "INVALID_FORMULA"
            is Supported -> "SUPPORTED"
            is Unsupported -> "UNSUPPORTED"
        }

    /** Valor numérico si está soportado, null en caso contrario. Usado por ObdSession.kt:3982. */
    val numericValueOrNull: Double?
        get() = (this as? Supported)?.value?.toDouble()

    /** ¿Mostrar en UI como gauge? Default true para Supported/Pending. */
    val isDisplayable: Boolean
        get() = this is Supported || this is Pending
}

enum class SensorSource {
    OEM_OBD,
    ENHANCED_OBD,
    STANDARD_OBD,
    COMPUTED,
    USER_INPUT,
    SIMULATED
}

// ═══════════════════════════════════════════════════════════════
// ObdPollingScheduler
// ═══════════════════════════════════════════════════════════════

data class PollingPlan(
    val commandsPerSecondLimit: Double = 50.0,
    val highPerformanceMode: Boolean = false,
    val planSteps: List<String> = emptyList()
)

class ObdPollingScheduler {
    fun nextIntervalMs(pid: String): Long = 100L
    fun shouldPollNow(pid: String): Boolean = true
    fun buildPlan(
        supportedPids: Set<String> = emptySet(),
        pinnedPids: Set<String> = emptySet(),
        adapterQuality: Any? = null,
        qos: Any? = null
    ): PollingPlan = PollingPlan()
}

// ═══════════════════════════════════════════════════════════════
// AdapterQualityProfiler
// ═══════════════════════════════════════════════════════════════

class AdapterQualityProfiler {
    fun recordLatency(pid: String, latencyMs: Long) {}
    fun qualityScore(): Double = 1.0
    fun profile(
        adapterName: String? = null,
        firmware: String? = null,
        transport: String? = null,
        qos: Any? = null,
        commandSupport: Set<String> = emptySet()
    ): Map<String, Any?> = mapOf(
        "name" to (adapterName ?: "unknown"),
        "firmware" to (firmware ?: ""),
        "transport" to (transport ?: ""),
        "qualityScore" to qualityScore()
    )
}

// ═══════════════════════════════════════════════════════════════
// DerivedMetricsEngine
// ═══════════════════════════════════════════════════════════════

data class DerivedMetric(
    val id: String,
    val name: String = "",
    val value: Float? = null,
    val unit: String = "",
    val isDisplayable: Boolean = true
) {
    /** Accesor Double para callers que esperan precisión decimal. */
    val valueAsDouble: Double?
        get() = value?.toDouble()
}

class DerivedMetricsEngine {
    /** Returns map of metric name -> value. Stub returns empty. */
    fun compute(pids: Map<String, Double>): Map<String, Double> = emptyMap()

    fun calculateAll(
        pids: Map<String, Any?> = emptyMap(),
        fuelPricePerLiter: Number? = null
    ): List<DerivedMetric> = emptyList()

    fun stateFor(metric: DerivedMetric): SensorValueState = SensorValueState.Pending
}

// ═══════════════════════════════════════════════════════════════
// EcuFailureIntelligence
// ═══════════════════════════════════════════════════════════════

enum class EcuFailureType { NONE, INTERMITTENT, PERSISTENT, PENDING, HISTORICAL, ECU_TIMEOUT }

/**
 * Contexto universal de falla ECU. Absorbe tanto fallas DTC como fallas de lectura PID.
 * Campos adicionales son opcionales con defaults seguros para no romper callers existentes.
 */
data class EcuFailureContext(
    val dtcCode: String = "",
    val freezeFramePidValues: Map<String, Double> = emptyMap(),
    val occurredAt: Long = 0L,
    // Campos extendidos para fallas de lectura PID y telemetría general.
    val eventType: String = "",
    val sessionId: String = "",
    val adapterType: String? = null,
    val adapterFirmware: String? = null,
    val transport: String? = null,
    val protocolSelected: String? = null,
    val commandSent: String? = null,
    val rawResponse: String? = null,
    val normalizedResponse: String? = null,
    val timeoutMs: Long? = null,
    val latencyMs: Long? = null,
    val retryCount: Int = 0,
    val serviceMode: String? = null,
    val pid: String? = null,
    val negativeResponseCode: String? = null,
    val batteryVoltage: Number? = null,
    val engineRunningState: String? = null,
    val vehicle: VehicleProfileFingerprint? = null,
    val appVersion: String = "",
    val androidVersion: String = "",
    val deviceModel: String = "",
    val timestampMs: Long = occurredAt
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

/**
 * Contexto de inicio de sesión OBD. Incluye todos los parámetros que ObdSession.kt
 * pasa en línea 1007-1024. Cualquier campo nuevo se agrega aquí como opcional.
 */
data class ObdSessionStartContext(
    val sessionId: String = "",
    val eventType: String = "obd_session_start",
    val appVersion: String,
    val androidVersion: String = "",
    val deviceModel: String = "",
    val vinHash: String?,
    val adapterName: String?,
    val adapterType: String? = null,
    val adapterMacHash: String? = null,
    val adapterFirmware: String?,
    val transport: String? = null,
    val protocolSelected: String? = null,
    val protocolDetected: String?,
    val consentGranted: Boolean,
    val startedAtMs: Long,
    val timestampMs: Long = startedAtMs
)

/**
 * Contexto de fin de sesión OBD. Campos adicionales son opcionales con defaults seguros.
 * endedAt, totalPidsRead, errorCount tienen defaults para callers legacy que no los pasan.
 */
data class ObdSessionFinishContext(
    val sessionId: String,
    val endedAt: Long = System.currentTimeMillis(),
    val totalPidsRead: Int = 0,
    val errorCount: Int = 0,
    val ecuModulesJson: String? = null,
    val dtcsJson: String? = null,
    val mode06Json: String? = null,
    val derivedMetricsJson: String? = null,
    val reconnectCount: Int = 0
)

/**
 * Evento de comando OBD registrado en la sesión (Mode 01 read, Mode 03 read DTC, etc).
 */
data class ObdCommandRecord(
    val commandSent: String,
    val rawResponse: String,
    val normalizedResponse: String? = null,
    val timeoutMs: Long = 2000L,
    val latencyMs: Long? = null,
    val retryCount: Int = 0,
    val serviceMode: String = "",
    val pid: String? = null,
    val negativeResponseCode: String? = null,
    val timestampMs: Long = 0L
)

/**
 * Estado de sensor (PID) registrado en la sesión.
 */
data class ObdSensorStateRecord(
    val pid: String,
    val stateName: String,
    val numericValue: Double? = null,
    val rawResponse: String? = null,
    val source: SensorSource = SensorSource.STANDARD_OBD,
    val timestampMs: Long = 0L
) {
    val numericValueOrNull: Double? get() = numericValue
}

/**
 * Falla detectada durante la sesión OBD.
 */
data class ObdFailureRecord(
    val dtcCode: String,
    val freezeFramePidValues: Map<String, Double> = emptyMap(),
    val occurredAt: Long,
    val severity: String = "UNKNOWN",
    val description: String = ""
)

/**
 * Métricas derivadas (consumo, eficiencia, etc.).
 */
data class ObdDerivedMetricRecord(
    val metricName: String,
    val value: Double,
    val unit: String = "",
    val formula: String = "",
    val timestampMs: Long = 0L
)

/**
 * Resultado Mode 06 (resultados de tests de monitor).
 */
data class ObdMode06Record(
    val testId: String,
    val componentId: String = "",
    val value: Double? = null,
    val minLimit: Double? = null,
    val maxLimit: Double? = null,
    val unit: String = "",
    val status: String = ""
)

class ObdSessionRecorder @javax.inject.Inject constructor() {
    fun onStart(ctx: ObdSessionStartContext) {}
    fun onFinish(ctx: ObdSessionFinishContext) {}

    /** Start a Vanguard session and return its sessionId. Stub returns synthetic id. */
    fun startSession(ctx: ObdSessionStartContext): String =
        if (ctx.sessionId.isNotBlank()) ctx.sessionId
        else "vanguard-stub-${System.currentTimeMillis()}"

    // API adicional requerida por ObdSession.kt — overloads para absorber ambas firmas.

    /** Overload 1: acepta ClassifiedEcuFailure (estilo legacy de ObdSession.kt:1032). */
    fun recordFailure(failure: ClassifiedEcuFailure) {}
    /** Overload 2: acepta ObdFailureRecord (estilo nuevo). */
    fun recordFailure(failure: ObdFailureRecord) {}

    /** Overload 1: acepta ObdCommandRecord (estilo nuevo). */
    fun recordCommand(record: ObdCommandRecord) {}
    /** Overload 2: positional args legacy (estilo ObdSession.kt:1049). */
    fun recordCommand(
        sessionId: String,
        command: String,
        rawResponse: String?,
        success: Boolean,
        latencyMs: Long? = null,
        timeoutMs: Long? = null,
        retryCount: Int = 0,
        errorType: EcuFailureType? = null
    ) {}

    /** Overload 1: acepta ObdSensorStateRecord (estilo nuevo). */
    fun recordSensorState(record: ObdSensorStateRecord) {}
    /** Overload 2: positional args legacy (estilo ObdSession.kt:1089). */
    fun recordSensorState(
        sessionId: String = "",
        vehicleId: String? = null,
        pid: String = "",
        label: String? = null,
        state: SensorValueState,
        source: SensorSource = SensorSource.STANDARD_OBD,
        rawValue: String? = null,
        timestampMs: Long = 0L
    ) {}

    /** Overload 1: acepta List<ObdDerivedMetricRecord>. */
    fun recordDerivedMetrics(records: List<ObdDerivedMetricRecord>) {}
    /** Overload 2: legacy (ObdSession.kt:1110). Acepta List<DerivedMetric>.
     *  JVM no distingue List<DerivedMetric> vs List<CalculatedMetric> (genéricos borrados),
     *  así que usamos un único overload. */
    fun recordDerivedMetrics(
        sessionId: String = "",
        vehicleId: String? = null,
        metrics: List<*>,
        timestampMs: Long = 0L
    ) {}

    /** Overload 1: acepta List<ObdMode06Record>. */
    fun recordMode06Results(records: List<ObdMode06Record>) {}
    /** Overload 2: legacy (ObdSession.kt:2456). Acepta List<Any>. */
    fun recordMode06Results(
        sessionId: String = "",
        vehicleId: String? = null,
        results: List<*>? = null,
        timestampMs: Long = 0L
    ) {}

    fun finishSession(ctx: ObdSessionFinishContext) {}
    fun endSession() {}

    // API mínima usada por callers fuera de ObdSession.
    fun recordVehicleState(profile: VehicleProfileFingerprint, dtcs: List<String> = emptyList()) {}
}

/**
 * Métrica calculada/derivada usada por DerivedMetricsEngine.
 */
data class CalculatedMetric(
    val id: String,
    val name: String = "",
    val value: Float? = null,
    val unit: String = "",
    val formula: String = "",
    val isDisplayable: Boolean = true
)

/**
 * Snapshot del perfil del vehículo. Campos no requeridos son opcionales para absorber
 * las llamadas de ObdSession.kt que sólo pasan vinHash o sólo pasan IDs.
 */
data class VehicleProfileFingerprint(
    val vehicleId: String = "",
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val vin: String? = null,
    val vinHash: String? = null,
    val capturedAt: Long = 0L
)

/**
 * Evento DTC registrado en sesión OBD.
 */
data class DtcSessionEvent(
    val timestampMs: Long,
    val dtcCode: String,
    val freezeFramePidValues: Map<String, Double> = emptyMap(),
    val occurredAt: Long = timestampMs,
    val sessionId: String = "",
    val status: String = "ACTIVE"
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

        /**
         * Encola el trabajo de sincronización inmediato. Usado por ObdViewModel.kt:3457.
         * Implementación real usa WorkManager.enqueueOneTimeWork; stub no-op.
         */
        fun enqueueNow(context: android.content.Context) {
            // No-op stub. La implementación real haría:
            // WorkManager.getInstance(context).enqueueUniqueWork(
            //     "vanguard_outbox_sync_now",
            //     ExistingWorkPolicy.REPLACE,
            //     OneTimeWorkRequestBuilder<VanguardOutboxSyncWorker>().build()
            // )
        }
    }

    override suspend fun doWork(): Result {
        // Stub: no-op. Real impl drains VanguardOutboxEntity where status = 'PENDING'
        // and POSTs to Supabase via the commerce API.
        return Result.success()
    }
}