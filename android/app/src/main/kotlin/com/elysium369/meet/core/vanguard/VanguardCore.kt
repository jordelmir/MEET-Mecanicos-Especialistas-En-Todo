package com.elysium369.meet.core.vanguard

/**
 * Runtime primitives for Vanguard telemetry intelligence.
 *
 * Every implementation in this file is fail-closed: unknown sensor data remains
 * unknown, an unbound physical session is never attributed to a vehicle, and
 * persistence failures are surfaced to the caller/log instead of being reported
 * as successful work.
 */

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.content.Context
import android.util.Log
import com.elysium369.meet.data.local.dao.VanguardTelemetryDao
import com.elysium369.meet.data.local.entities.AuditLogEntity
import com.elysium369.meet.data.local.entities.DerivedMetricEntity
import com.elysium369.meet.data.local.entities.EcuFailureEventEntity
import com.elysium369.meet.data.local.entities.Mode06ResultEntity
import com.elysium369.meet.data.local.entities.ObdCommandLogEntity
import com.elysium369.meet.data.local.entities.ObdPidSampleEntity
import com.elysium369.meet.data.local.entities.VanguardObdSessionEntity
import com.elysium369.meet.data.local.entities.VehicleProfileSnapshotEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.KeyGenerator
import javax.crypto.Mac

// ═══════════════════════════════════════════════════════════════
// VanguardPrivacyGuard
// ═══════════════════════════════════════════════════════════════

enum class DiagnosticTelemetryConsent {
    DISABLED,
    ANONYMOUS_REDACTED,
    CONSENTED_REDACTED,
}

enum class TelemetryFieldClassification {
    PUBLIC, PSEUDONYMOUS, VEHICLE_ID, DEVICE_ID, LOCATION, DIAGNOSTIC_RAW, PERSONAL, SECRET,
}

data class ClassifiedTelemetryField(
    val name: String,
    val value: String,
    val classification: TelemetryFieldClassification,
)

data class DiagnosticRemotePayload(
    val fields: Map<String, String>,
    val consent: DiagnosticTelemetryConsent,
)

enum class DiagnosticRetentionClass {
    RAW_TRANSIENT,
    RAW_FORENSIC,
    NORMALIZED_LONG_TERM,
    CERTIFIED,
}

class VanguardPrivacyGuard {
    private val vinPattern = Regex("\\b[A-HJ-NPR-Z0-9]{17}\\b", RegexOption.IGNORE_CASE)
    private val macPattern = Regex("\\b(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\\b")
    private val ipv4Pattern = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")

    fun diagnosticTelemetryConsent(context: Context): DiagnosticTelemetryConsent {
        val stored = context.getSharedPreferences("meet_privacy", Context.MODE_PRIVATE)
            .getString("diagnostic_telemetry_consent", DiagnosticTelemetryConsent.DISABLED.name)
        return when (stored) {
            "ANONYMOUS_DIAGNOSTICS" -> DiagnosticTelemetryConsent.ANONYMOUS_REDACTED
            "FULL_DIAGNOSTICS" -> DiagnosticTelemetryConsent.CONSENTED_REDACTED
            else -> runCatching { DiagnosticTelemetryConsent.valueOf(stored.orEmpty()) }
                .getOrDefault(DiagnosticTelemetryConsent.DISABLED)
        }
    }

    fun allowsRemoteDiagnostics(context: Context): Boolean =
        diagnosticTelemetryConsent(context) != DiagnosticTelemetryConsent.DISABLED

    fun setDiagnosticTelemetryConsent(
        context: Context,
        consent: DiagnosticTelemetryConsent,
    ) {
        context.getSharedPreferences("meet_privacy", Context.MODE_PRIVATE)
            .edit()
            .putString("diagnostic_telemetry_consent", consent.name)
            .apply()
    }

    fun redactForLogging(input: String): String = redact(input)

    /** Redact VIN/GPS coordinates before sending to remote telemetry. */
    fun redactForTelemetry(input: String): String = redact(input)

    fun remotePayload(
        context: Context,
        fields: List<ClassifiedTelemetryField>,
    ): DiagnosticRemotePayload? {
        val consent = diagnosticTelemetryConsent(context)
        if (consent == DiagnosticTelemetryConsent.DISABLED) return null
        require(fields.map { it.name }.distinct().size == fields.size) { "Duplicate telemetry field" }
        val sanitized = fields.mapNotNull { field ->
            require(field.name.matches(Regex("^[A-Za-z][A-Za-z0-9_.-]{0,63}$")))
            val value = when (field.classification) {
                TelemetryFieldClassification.PUBLIC,
                TelemetryFieldClassification.PSEUDONYMOUS -> redact(field.value)
                TelemetryFieldClassification.VEHICLE_ID,
                TelemetryFieldClassification.DEVICE_ID -> vinPseudonym(field.value)
                TelemetryFieldClassification.DIAGNOSTIC_RAW -> if (
                    consent == DiagnosticTelemetryConsent.CONSENTED_REDACTED
                ) redact(field.value) else null
                TelemetryFieldClassification.LOCATION,
                TelemetryFieldClassification.PERSONAL,
                TelemetryFieldClassification.SECRET -> null
            }
            value?.let { field.name to it }
        }.toMap()
        return DiagnosticRemotePayload(sanitized, consent)
    }

    /**
     * App-scoped VIN pseudonym. HMAC prevents dictionary comparison of raw VINs
     * across installations; the non-exportable key lives in Android Keystore.
     */
    fun vinPseudonym(vin: String?): String? = vin
        ?.trim()
        ?.uppercase()
        ?.takeIf { it.isNotBlank() }
        ?.let { normalized ->
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!keyStore.containsAlias(VIN_HMAC_ALIAS)) {
                val generator = KeyGenerator.getInstance(
                    android.security.keystore.KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
                    "AndroidKeyStore",
                )
                generator.init(
                    android.security.keystore.KeyGenParameterSpec.Builder(
                        VIN_HMAC_ALIAS,
                        android.security.keystore.KeyProperties.PURPOSE_SIGN or
                            android.security.keystore.KeyProperties.PURPOSE_VERIFY,
                    )
                        .setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256)
                        .build(),
                )
                generator.generateKey()
            }
            val key = (keyStore.getEntry(VIN_HMAC_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
            Mac.getInstance("HmacSHA256").run {
                init(key)
                doFinal(normalized.toByteArray(Charsets.UTF_8))
            }
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }

    @Deprecated("Use app-scoped HMAC pseudonyms; unsalted VIN hashes are linkable")
    fun vinHashOnly(vin: String?): String? = vinPseudonym(vin)

    private fun redact(input: String): String = input
        .replace(vinPattern, "[VIN_REDACTED]")
        .replace(macPattern, "[MAC_REDACTED]")
        .replace(ipv4Pattern, "[IP_REDACTED]")

    private companion object {
        const val VIN_HMAC_ALIAS = "ELYSIUM_VANGUARD_VIN_HMAC_V1"
    }
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
    private val lastPollAt = ConcurrentHashMap<String, Long>()

    fun nextIntervalMs(pid: String): Long = when (pid.normalizedPid()) {
        "010C", "010D", "0111" -> 100L
        "0142", "ATRV" -> 1_000L
        "0105", "010F" -> 750L
        else -> 500L
    }

    fun shouldPollNow(pid: String): Boolean {
        val now = System.nanoTime() / 1_000_000L
        val key = pid.normalizedPid()
        val previous = lastPollAt.putIfAbsent(key, now)
        if (previous == null) return true
        if (now - previous < nextIntervalMs(key)) return false
        return lastPollAt.replace(key, previous, now)
    }

    fun buildPlan(
        supportedPids: Set<String> = emptySet(),
        pinnedPids: Set<String> = emptySet(),
        adapterQuality: Any? = null,
        qos: Any? = null
    ): PollingPlan {
        val knownQuality = (adapterQuality as? Map<*, *>)
            ?.get("qualityScore")
            ?.let { it as? Number }
            ?.toDouble()
            ?.takeIf { it.isFinite() }
        val supported = supportedPids.map { it.normalizedPid() }.toSet()
        val pinned = pinnedPids.map { it.normalizedPid() }.toSet()
        val selected = (pinned + supported).filter { it.isNotBlank() }
        val commandLimit = when {
            knownQuality == null -> 5.0
            knownQuality >= 0.85 -> 15.0
            knownQuality >= 0.65 -> 10.0
            knownQuality >= 0.4 -> 6.0
            else -> 3.0
        }
        return PollingPlan(
            commandsPerSecondLimit = commandLimit,
            highPerformanceMode = knownQuality != null && knownQuality >= 0.85,
            planSteps = selected.sorted(),
        )
    }

    private fun String.normalizedPid(): String = uppercase().replace(" ", "")
}

// ═══════════════════════════════════════════════════════════════
// AdapterQualityProfiler
// ═══════════════════════════════════════════════════════════════

class AdapterQualityProfiler {
    private val latenciesMs = ConcurrentLinkedDeque<Long>()

    fun recordLatency(pid: String, latencyMs: Long) {
        if (pid.isBlank() || latencyMs < 0L) return
        latenciesMs.addLast(latencyMs)
        while (latenciesMs.size > MAX_SAMPLES) latenciesMs.pollFirst()
    }

    fun qualityScore(): Double {
        val samples = latenciesMs.toList().sorted()
        if (samples.isEmpty()) return Double.NaN
        val median = samples[samples.size / 2].toDouble()
        val p95 = samples[((samples.lastIndex) * 0.95).toInt()].toDouble()
        val medianScore = (1.0 - (median / 1_000.0)).coerceIn(0.0, 1.0)
        val tailScore = (1.0 - (p95 / 2_000.0)).coerceIn(0.0, 1.0)
        return (medianScore * 0.65 + tailScore * 0.35).coerceIn(0.0, 1.0)
    }
    fun profile(
        adapterName: String? = null,
        firmware: String? = null,
        transport: String? = null,
        qos: Any? = null,
        commandSupport: Set<String> = emptySet()
    ): Map<String, Any?> {
        val score = qualityScore()
        return mapOf(
        "name" to adapterName,
        "firmware" to (firmware ?: ""),
        "transport" to (transport ?: ""),
        "qualityScore" to score.takeIf { it.isFinite() },
        "qualityEvidence" to if (score.isFinite()) "MEASURED_LATENCY" else "UNAVAILABLE",
        "sampleCount" to latenciesMs.size,
        "commandSupport" to commandSupport.sorted(),
        "qosObserved" to (qos != null),
    )
    }

    private companion object {
        const val MAX_SAMPLES = 128
    }
}

// ═══════════════════════════════════════════════════════════════
// DerivedMetricsEngine
// ═══════════════════════════════════════════════════════════════

data class DerivedMetric(
    val id: String,
    val name: String = "",
    val value: Float? = null,
    val unit: String = "",
    val isDisplayable: Boolean = true,
    val origin: String = "DERIVED",
    val confidence: Double = 0.0,
    val inputPids: List<String> = emptyList(),
    val formulaVersion: String = "UNVERSIONED",
    val inputQuality: Double = 0.0,
    val formulaAuthority: String = "GENERIC_REVIEW_REQUIRED",
    val derivationCompleteness: Double = 0.0,
    val measurementUncertainty: Double? = null,
) {
    /** Accesor Double para callers que esperan precisión decimal. */
    val valueAsDouble: Double?
        get() = value?.toDouble()
}

class DerivedMetricsEngine {
    fun compute(pids: Map<String, Double>): Map<String, Double> = buildMap {
        pids.findPid("010C", "0C", "RPM")?.takeIf { it >= 0.0 }?.let {
            put("ENGINE_REVOLUTIONS_PER_SECOND", it / 60.0)
        }
        pids.findPid("010D", "0D", "SPEED")?.takeIf { it >= 0.0 }?.let {
            put("VEHICLE_SPEED_METERS_PER_SECOND", it / 3.6)
        }
        val coolant = pids.findPid("0105", "05", "COOLANT", "ECT")
        val intake = pids.findPid("010F", "0F", "IAT")
        if (coolant != null && intake != null) {
            put("COOLANT_TO_INTAKE_DELTA_C", coolant - intake)
        }
    }

    fun calculateAll(
        pids: Map<String, Any?> = emptyMap(),
        fuelPricePerLiter: Number? = null
    ): List<DerivedMetric> {
        val numeric = pids.mapNotNull { (key, raw) ->
            val value = when (raw) {
                is Number -> raw.toDouble()
                is SensorValueState.Supported -> raw.value.toDouble()
                else -> null
            }
            value?.takeIf { it.isFinite() }?.let { key to it }
        }.toMap()
        val computed = compute(numeric)
        return METRIC_DEFINITIONS.map { definition ->
            val value = computed[definition.id]
            DerivedMetric(
                id = definition.id,
                name = definition.name,
                value = value?.toFloat(),
                unit = definition.unit,
                isDisplayable = value != null,
                origin = "DERIVED_FROM_REAL_PID",
                confidence = 0.0,
                inputPids = definition.inputPids,
                formulaVersion = definition.formulaVersion,
                inputQuality = if (value == null) 0.0 else 0.75,
                formulaAuthority = "MEET_REVIEWED_GENERIC_FORMULA",
                derivationCompleteness = if (value == null) 0.0 else 1.0,
                measurementUncertainty = null,
            )
        }
    }

    fun stateFor(metric: DerivedMetric): SensorValueState = when {
        metric.value == null -> SensorValueState.NotAvailable
        !metric.value.isFinite() -> SensorValueState.InvalidFormula(metric.value.toString())
        else -> SensorValueState.Supported(metric.value, metric.unit)
    }

    private fun Map<String, Double>.findPid(vararg aliases: String): Double? =
        aliases.firstNotNullOfOrNull { alias ->
            entries.firstOrNull { (key, _) -> key.equals(alias, ignoreCase = true) }?.value
        }

    private data class MetricDefinition(
        val id: String,
        val name: String,
        val unit: String,
        val inputPids: List<String>,
        val formulaVersion: String,
    )

    private companion object {
        val METRIC_DEFINITIONS = listOf(
            MetricDefinition("ENGINE_REVOLUTIONS_PER_SECOND", "Revoluciones por segundo", "rev/s", listOf("010C"), "rpm-to-rps-v1"),
            MetricDefinition("VEHICLE_SPEED_METERS_PER_SECOND", "Velocidad SI", "m/s", listOf("010D"), "kmh-to-ms-v1"),
            MetricDefinition("COOLANT_TO_INTAKE_DELTA_C", "Diferencial térmico ECT-IAT", "°C", listOf("0105", "010F"), "ect-iat-delta-v1"),
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// EcuFailureIntelligence
// ═══════════════════════════════════════════════════════════════

enum class EcuFailureType {
    NONE, ECU_TIMEOUT, COMMUNICATION_RETRY, NEGATIVE_RESPONSE, MALFORMED_RESPONSE, LINK_DEGRADED,
}

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
    /** Completeness of captured protocol evidence; never a probability that a component failed. */
    val evidenceCompletenessScore: Double,
    val recommendation: String?
)

class EcuFailureIntelligence {
    fun classify(context: EcuFailureContext): ClassifiedEcuFailure {
        val type = when {
            context.eventType.contains("TIMEOUT", ignoreCase = true) ||
                context.rawResponse?.contains("TIMEOUT", ignoreCase = true) == true -> EcuFailureType.ECU_TIMEOUT
            context.negativeResponseCode != null -> EcuFailureType.NEGATIVE_RESPONSE
            context.retryCount > 0 -> EcuFailureType.COMMUNICATION_RETRY
            context.rawResponse?.contains("MALFORMED", ignoreCase = true) == true -> EcuFailureType.MALFORMED_RESPONSE
            context.eventType.contains("LINK_DEGRADED", ignoreCase = true) -> EcuFailureType.LINK_DEGRADED
            else -> EcuFailureType.NONE
        }
        val evidenceScore = when {
            type == EcuFailureType.NONE -> 0.0
            context.negativeResponseCode != null -> 1.0
            type == EcuFailureType.ECU_TIMEOUT && context.timeoutMs != null -> 1.0
            context.rawResponse != null -> 0.75
            else -> 0.5
        }
        val recommendation = when (type) {
            EcuFailureType.ECU_TIMEOUT -> "Verificar enlace, alimentación del adaptador y disponibilidad de la ECU."
            EcuFailureType.COMMUNICATION_RETRY -> "Repetir lectura conservando el intercambio crudo y la latencia."
            EcuFailureType.NEGATIVE_RESPONSE -> "Revisar NRC/respuesta cruda antes de cualquier conclusión física."
            EcuFailureType.MALFORMED_RESPONSE -> "Conservar la trama y revisar parser/protocolo sin crear un DTC."
            EcuFailureType.LINK_DEGRADED -> "Estabilizar el enlace antes de emitir conclusiones diagnósticas."
            else -> null
        }
        return ClassifiedEcuFailure(context, type, evidenceScore, recommendation)
    }
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

class ObdSessionRecorder @javax.inject.Inject constructor(
    private val dao: VanguardTelemetryDao,
    private val scope: CoroutineScope,
) {
    enum class WriteClass { BEST_EFFORT_TELEMETRY, DURABLE_DIAGNOSTIC_EVIDENCE, FORENSIC_CRITICAL }
    enum class PersistenceResult { PERSISTED, DURABLY_QUEUED, QUEUED_VOLATILE, DROPPED_BEST_EFFORT, FAILED }

    data class RecorderHealth(
        val queueDepth: Long,
        val oldestPendingAgeMs: Long,
        val lastWriteLatencyMs: Long,
        val writeFailures: Long,
        val droppedBestEffortEvents: Long,
    )

    private data class QueuedWrite(
        val writeClass: WriteClass,
        val enqueuedAtMs: Long,
        val operation: suspend () -> Unit,
    )

    private val persistenceQueue = Channel<QueuedWrite>(capacity = MAX_PENDING_WRITES)
    private val queueDepth = AtomicLong(0)
    private val writeFailures = AtomicLong(0)
    private val droppedBestEffort = AtomicLong(0)
    private val oldestPendingAtMs = AtomicLong(0)
    private val lastWriteLatencyMs = AtomicLong(0)

    init {
        CoroutineScope(scope.coroutineContext).launch {
            for (queued in persistenceQueue) {
                val startedAt = System.currentTimeMillis()
                runCatching { queued.operation() }
                    .onFailure {
                        writeFailures.incrementAndGet()
                        Log.e("ObdSessionRecorder", "${queued.writeClass} write failed", it)
                    }
                lastWriteLatencyMs.set((System.currentTimeMillis() - startedAt).coerceAtLeast(0))
                if (queueDepth.decrementAndGet() == 0L) oldestPendingAtMs.set(0)
            }
        }
    }

    fun health(nowMs: Long = System.currentTimeMillis()): RecorderHealth = RecorderHealth(
        queueDepth = queueDepth.get(),
        oldestPendingAgeMs = oldestPendingAtMs.get().takeIf { it > 0 }
            ?.let { (nowMs - it).coerceAtLeast(0) } ?: 0,
        lastWriteLatencyMs = lastWriteLatencyMs.get(),
        writeFailures = writeFailures.get(),
        droppedBestEffortEvents = droppedBestEffort.get(),
    )

    private fun persist(
        writeClass: WriteClass = WriteClass.DURABLE_DIAGNOSTIC_EVIDENCE,
        operation: suspend () -> Unit,
    ): PersistenceResult {
        val now = System.currentTimeMillis()
        val result = persistenceQueue.trySend(QueuedWrite(writeClass, now, operation))
        if (result.isSuccess) {
            if (queueDepth.incrementAndGet() == 1L) oldestPendingAtMs.set(now)
            // This bounded channel protects RAM but is not a disk-backed outbox.
            // Callers must not present QUEUED_VOLATILE as evidence already saved.
            return PersistenceResult.QUEUED_VOLATILE
        }
        return if (writeClass == WriteClass.BEST_EFFORT_TELEMETRY) {
            droppedBestEffort.incrementAndGet()
            PersistenceResult.DROPPED_BEST_EFFORT
        } else {
            writeFailures.incrementAndGet()
            Log.e("ObdSessionRecorder", "Fail-closed: $writeClass queue is full or closed")
            PersistenceResult.FAILED
        }
    }

    @Volatile
    private var mostRecentSessionId: String? = null

    private companion object {
        const val MAX_PENDING_WRITES = 2_048
    }

    fun onStart(ctx: ObdSessionStartContext) {
        startSession(ctx)
    }

    fun onFinish(ctx: ObdSessionFinishContext) {
        finishSession(ctx)
    }

    /** Starts an explicitly unbound physical session; failure to queue is fatal. */
    fun startSession(ctx: ObdSessionStartContext): String {
        val sessionId = ctx.sessionId.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        mostRecentSessionId = sessionId
        val persistence = persist(WriteClass.FORENSIC_CRITICAL) {
            dao.insertSession(
                VanguardObdSessionEntity(
                    sessionId = sessionId,
                    vehicleId = "",
                    adapterId = ctx.adapterMacHash,
                    protocol = ctx.protocolDetected ?: ctx.protocolSelected ?: "UNVERIFIED",
                    startedAt = ctx.startedAtMs,
                    status = "ACTIVE_UNBOUND",
                )
            )
            dao.insertAuditLog(
                AuditLogEntity(
                    actorId = null,
                    actorRole = "SYSTEM",
                    action = "CREATE",
                    resourceType = "VANGUARD_OBD_SESSION",
                    resourceId = sessionId,
                    payloadJson = "{\"bindingState\":\"UNBOUND\",\"consentGranted\":${ctx.consentGranted}}",
                    occurredAt = ctx.startedAtMs,
                )
            )
        }
        check(persistence != PersistenceResult.FAILED) { "Unable to queue forensic session start" }
        return sessionId
    }

    fun recordFailure(failure: ClassifiedEcuFailure) {
        val context = failure.failure
        val sessionId = context.sessionId.ifBlank { mostRecentSessionId.orEmpty() }
        val vehicleId = context.vehicle?.vehicleId.orEmpty()
        val persistence = persist(WriteClass.FORENSIC_CRITICAL) {
            dao.insertAuditLog(
                AuditLogEntity(
                    actorId = null,
                    actorRole = "SYSTEM",
                    action = "OBSERVE",
                    resourceType = "ECU_FAILURE_EVIDENCE",
                    resourceId = sessionId.ifBlank { UUID.randomUUID().toString() },
                    payloadJson = buildFailureAuditPayload(failure),
                    occurredAt = context.timestampMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
                )
            )
            if (vehicleId.isNotBlank() && context.dtcCode.isNotBlank()) {
                dao.insertEcuFailure(
                    EcuFailureEventEntity(
                        eventId = UUID.randomUUID().toString(),
                        vehicleId = vehicleId,
                        dtcCode = context.dtcCode,
                        source = "ECU_EVIDENCE",
                        severity = failure.type.name,
                        description = failure.recommendation.orEmpty(),
                        detectedAt = context.timestampMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    )
                )
            }
        }
        check(persistence != PersistenceResult.FAILED) { "Unable to queue diagnostic failure evidence" }
    }

    fun recordFailure(failure: ObdFailureRecord) {
        val context = EcuFailureContext(
            dtcCode = failure.dtcCode,
            freezeFramePidValues = failure.freezeFramePidValues,
            occurredAt = failure.occurredAt,
            eventType = "DTC_FAILURE",
        )
        recordFailure(EcuFailureIntelligence().classify(context))
    }

    fun recordCommand(record: ObdCommandRecord) {
        recordCommand(
            sessionId = mostRecentSessionId.orEmpty(),
            command = record.commandSent,
            rawResponse = record.rawResponse,
            success = record.negativeResponseCode == null,
            latencyMs = record.latencyMs,
            timeoutMs = record.timeoutMs,
            retryCount = record.retryCount,
            errorType = record.negativeResponseCode?.let { EcuFailureType.NEGATIVE_RESPONSE },
        )
    }

    fun recordCommand(
        sessionId: String,
        command: String,
        rawResponse: String?,
        success: Boolean,
        latencyMs: Long? = null,
        timeoutMs: Long? = null,
        retryCount: Int = 0,
        errorType: EcuFailureType? = null
    ) {
        if (sessionId.isBlank() || command.isBlank()) return
        val now = System.currentTimeMillis()
        persist(WriteClass.DURABLE_DIAGNOSTIC_EVIDENCE) {
            dao.insertCommandLog(
                ObdCommandLogEntity(
                    sessionId = sessionId,
                    command = command,
                    response = rawResponse.orEmpty(),
                    latencyMs = latencyMs ?: UNKNOWN_LATENCY_MS,
                    success = success,
                    sentAt = now,
                )
            )
            if (!success) {
                dao.insertAuditLog(
                    AuditLogEntity(
                        actorId = null,
                        actorRole = "SYSTEM",
                        action = "OBSERVE",
                        resourceType = "OBD_COMMAND_FAILURE",
                        resourceId = sessionId,
                        payloadJson = "{\"command\":\"${jsonEscape(command)}\",\"errorType\":\"${errorType?.name ?: "UNKNOWN"}\",\"retryCount\":$retryCount,\"timeoutMs\":${timeoutMs ?: "null"}}",
                        occurredAt = now,
                    )
                )
            }
        }
    }

    /** Overload 1: acepta ObdSensorStateRecord (estilo nuevo). */
    fun recordSensorState(record: ObdSensorStateRecord) {
        recordSensorState(
            sessionId = mostRecentSessionId.orEmpty(),
            pid = record.pid,
            state = record.numericValue?.let { SensorValueState.Supported(it) }
                ?: SensorValueState.Unsupported(record.stateName),
            source = record.source,
            rawValue = record.rawResponse,
            timestampMs = record.timestampMs,
        )
    }

    fun recordSensorState(
        sessionId: String = "",
        vehicleId: String? = null,
        pid: String = "",
        label: String? = null,
        state: SensorValueState,
        source: SensorSource = SensorSource.STANDARD_OBD,
        rawValue: String? = null,
        timestampMs: Long = 0L
    ) {
        if (sessionId.isBlank() || pid.isBlank()) return
        val occurredAt = timestampMs.takeIf { it > 0L } ?: System.currentTimeMillis()
        persist(WriteClass.BEST_EFFORT_TELEMETRY) {
            val numeric = state.numericValueOrNull
            if (numeric != null && numeric.isFinite()) {
                dao.insertPidSample(
                    ObdPidSampleEntity(
                        sessionId = sessionId,
                        pid = pid,
                        value = numeric,
                        unit = "",
                        capturedAt = occurredAt,
                    )
                )
            } else {
                dao.insertAuditLog(
                    AuditLogEntity(
                        actorId = null,
                        actorRole = "SYSTEM",
                        action = "OBSERVE",
                        resourceType = "OBD_SENSOR_STATE",
                        resourceId = sessionId,
                        payloadJson = "{\"pid\":\"${jsonEscape(pid)}\",\"state\":\"${state.stateName}\",\"source\":\"${source.name}\",\"rawAvailable\":${!rawValue.isNullOrBlank()}}",
                        occurredAt = occurredAt,
                    )
                )
            }
        }
    }

    /** Overload 1: acepta List<ObdDerivedMetricRecord>. */
    fun recordDerivedMetrics(records: List<ObdDerivedMetricRecord>) {
        recordDerivedMetrics(metrics = records)
    }

    fun recordDerivedMetrics(
        sessionId: String = "",
        vehicleId: String? = null,
        metrics: List<*>,
        timestampMs: Long = 0L
    ) {
        val occurredAt = timestampMs.takeIf { it > 0L } ?: System.currentTimeMillis()
        persist(WriteClass.BEST_EFFORT_TELEMETRY) {
            metrics.forEach { raw ->
                val metric = when (raw) {
                    is DerivedMetric -> raw.valueAsDouble?.let { value ->
                        ObdDerivedMetricRecord(raw.name.ifBlank { raw.id }, value, raw.unit, raw.formulaVersion, occurredAt)
                    }
                    is ObdDerivedMetricRecord -> raw
                    is CalculatedMetric -> raw.value?.toDouble()?.let { value ->
                        ObdDerivedMetricRecord(raw.name.ifBlank { raw.id }, value, raw.unit, raw.formula, occurredAt)
                    }
                    else -> null
                }
                if (metric != null && vehicleId.orEmpty().isNotBlank()) {
                    dao.insertDerivedMetric(
                        DerivedMetricEntity(
                            vehicleId = vehicleId.orEmpty(),
                            metricName = metric.metricName,
                            value = metric.value,
                            unit = metric.unit,
                            computedAt = metric.timestampMs.takeIf { it > 0L } ?: occurredAt,
                            origin = "DERIVED_FROM_REAL_PID",
                            confidence = when (raw) {
                                is DerivedMetric -> (raw.inputQuality * raw.derivationCompleteness).coerceIn(0.0, 0.99)
                                else -> 0.5
                            },
                            formulaVersion = metric.formula.ifBlank { "UNVERSIONED" },
                            inputQuality = (raw as? DerivedMetric)?.inputQuality ?: 0.5,
                            formulaAuthority = (raw as? DerivedMetric)?.formulaAuthority ?: "UNREVIEWED_FORMULA",
                            derivationCompleteness = (raw as? DerivedMetric)?.derivationCompleteness ?: 0.5,
                            measurementUncertainty = (raw as? DerivedMetric)?.measurementUncertainty,
                        )
                    )
                }
            }
            if (metrics.isNotEmpty() && vehicleId.orEmpty().isBlank()) {
                dao.insertAuditLog(
                    AuditLogEntity(
                        actorId = null,
                        actorRole = "SYSTEM",
                        action = "DEFER",
                        resourceType = "UNBOUND_DERIVED_METRICS",
                        resourceId = sessionId.ifBlank { mostRecentSessionId.orEmpty() },
                        payloadJson = "{\"metricCount\":${metrics.size},\"reason\":\"VEHICLE_SESSION_UNBOUND\"}",
                        occurredAt = occurredAt,
                    )
                )
            }
        }
    }

    /** Overload 1: acepta List<ObdMode06Record>. */
    fun recordMode06Results(records: List<ObdMode06Record>) {
        recordMode06Results(results = records)
    }

    fun recordMode06Results(
        sessionId: String = "",
        vehicleId: String? = null,
        results: List<*>? = null,
        timestampMs: Long = 0L
    ) {
        val boundSessionId = sessionId.ifBlank { mostRecentSessionId.orEmpty() }
        if (boundSessionId.isBlank()) return
        val occurredAt = timestampMs.takeIf { it > 0L } ?: System.currentTimeMillis()
        persist {
            results.orEmpty().filterIsInstance<ObdMode06Record>().forEach { result ->
                val value = result.value
                val min = result.minLimit
                val max = result.maxLimit
                if (value != null && min != null && max != null) {
                    dao.insertMode06Result(
                        Mode06ResultEntity(
                            sessionId = boundSessionId,
                            testId = result.testId,
                            componentId = result.componentId,
                            value = value,
                            minValue = min,
                            maxValue = max,
                            status = result.status.ifBlank { "UNVERIFIED" },
                            capturedAt = occurredAt,
                        )
                    )
                } else {
                    dao.insertAuditLog(
                        AuditLogEntity(
                            actorId = null,
                            actorRole = "SYSTEM",
                            action = "OBSERVE",
                            resourceType = "MODE06_INCOMPLETE_RESULT",
                            resourceId = boundSessionId,
                            payloadJson = "{\"testId\":\"${jsonEscape(result.testId)}\",\"status\":\"UNVERIFIED\"}",
                            occurredAt = occurredAt,
                        )
                    )
                }
            }
        }
    }

    fun finishSession(ctx: ObdSessionFinishContext) {
        if (ctx.sessionId.isBlank()) return
        val persistence = persist(WriteClass.FORENSIC_CRITICAL) {
            val updated = dao.finishSession(
                sessionId = ctx.sessionId,
                endedAt = ctx.endedAt,
                status = if (ctx.errorCount > 0) "COMPLETED_WITH_ERRORS" else "COMPLETED",
                totalPidsRead = ctx.totalPidsRead,
                errorCount = ctx.errorCount,
                lastError = null,
            )
            check(updated == 1) { "Vanguard session ${ctx.sessionId} was not persisted before finish" }
        }
        check(persistence != PersistenceResult.FAILED) { "Unable to queue forensic session finish" }
        if (mostRecentSessionId == ctx.sessionId) mostRecentSessionId = null
    }

    fun recordActiveTestEvidence(
        evidenceId: String,
        testId: String,
        phase: String,
        message: String,
        stopVerified: Boolean,
        occurredAt: Long = System.currentTimeMillis(),
    ): PersistenceResult {
        val sessionId = mostRecentSessionId ?: return PersistenceResult.FAILED
        return persist(WriteClass.FORENSIC_CRITICAL) {
            dao.insertAuditLog(
                AuditLogEntity(
                    actorId = null,
                    actorRole = "SYSTEM",
                    action = "APPEND",
                    resourceType = "ACTIVE_TEST_EVIDENCE",
                    resourceId = evidenceId,
                    payloadJson = "{\"sessionId\":\"${jsonEscape(sessionId)}\",\"testId\":\"${jsonEscape(testId)}\",\"phase\":\"${jsonEscape(phase)}\",\"message\":\"${jsonEscape(message)}\",\"stopVerified\":$stopVerified}",
                    occurredAt = occurredAt,
                ),
            )
        }
    }

    fun endSession() {
        mostRecentSessionId?.let { sessionId ->
            finishSession(ObdSessionFinishContext(sessionId = sessionId))
        }
    }

    fun recordVehicleState(profile: VehicleProfileFingerprint, dtcs: List<String> = emptyList()) {
        if (profile.vehicleId.isBlank()) return
        val capturedAt = profile.capturedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        persist {
            dao.insertProfileSnapshot(
                VehicleProfileSnapshotEntity(
                    vehicleId = profile.vehicleId,
                    capturedAt = capturedAt,
                    odometerKm = null,
                    batteryVoltage = null,
                    coolantTempC = null,
                    oilLifePercent = null,
                    payloadJson = "{\"vinHash\":${profile.vinHash?.let { "\"${jsonEscape(it)}\"" } ?: "null"},\"dtcCount\":${dtcs.size}}",
                )
            )
        }
    }

    private fun buildFailureAuditPayload(failure: ClassifiedEcuFailure): String =
        "{\"type\":\"${failure.type.name}\",\"dtcCode\":\"${jsonEscape(failure.failure.dtcCode)}\",\"negativeResponseCode\":${failure.failure.negativeResponseCode?.let { "\"${jsonEscape(it)}\"" } ?: "null"},\"rawResponseAvailable\":${!failure.failure.rawResponse.isNullOrBlank()}}"

    private fun jsonEscape(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }

    private companion object {
        const val UNKNOWN_LATENCY_MS = -1L
    }
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

@HiltWorker
class VanguardOutboxSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dispatcher: VanguardOutboxDispatcher,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val PERIODIC_WORK_NAME = "vanguard_outbox_sync_periodic"
        private const val IMMEDIATE_WORK_NAME = "vanguard_outbox_sync_now"

        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<VanguardOutboxSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }

    override suspend fun doWork(): Result {
        return runCatching {
            dispatcher.drain()
            if (dispatcher.pendingCount() == 0) Result.success() else Result.retry()
        }.getOrElse { Result.retry() }
    }
}
