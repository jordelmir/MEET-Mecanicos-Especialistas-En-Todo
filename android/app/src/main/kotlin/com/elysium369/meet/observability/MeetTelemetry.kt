package com.elysium369.meet.observability

import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

data class TelemetryContext(
    val appVersion: String,
    val buildSha: String,
    val environment: String,
)

enum class TelemetrySignalType { EVENT, COUNTER, HISTOGRAM, ERROR }

data class TelemetrySignal(
    val type: TelemetrySignalType,
    val name: String,
    val traceId: String,
    val spanId: String,
    val correlationId: String,
    val attributes: Map<String, String>,
    val occurredAtEpochMs: Long,
)

object TelemetryPrivacyPolicy {
    private val allowedKeys = setOf(
        "appVersion", "buildSha", "environment", "vertical", "operation",
        "resultCode", "failureCode", "transport", "protocol", "adapterClass",
        "strategy", "scanType", "completeness", "disconnectReason", "expected",
        "durationMs", "latencyMs", "attemptOrdinal", "plannedModules",
        "confirmedModules", "attemptedModules", "completedModules", "plannedServices",
        "completedServices", "taxonomyVersion", "triageState", "urgency",
        "capability", "activationState", "reviewDecision", "queueAgeBucket",
        "androidVersion", "deviceFamily", "networkType", "sampled",
    )
    private val controlledValue = Regex("[A-Za-z0-9_.:/-]{1,96}")
    private val vin = Regex("(?i)[A-HJ-NPR-Z0-9]{17}")
    private val email = Regex("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}")

    fun filter(attributes: Map<String, Any?>): Map<String, String> = attributes
        .asSequence()
        .filter { (key, _) -> key in allowedKeys }
        .mapNotNull { (key, raw) ->
            val value = raw?.toString()?.trim() ?: return@mapNotNull null
            if (!controlledValue.matches(value) || vin.containsMatchIn(value) || email.containsMatchIn(value)) {
                null
            } else {
                key to value
            }
        }
        .toMap()

    /** Use only when a pseudonymous correlation key is explicitly needed. */
    fun pseudonymize(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

fun interface TelemetryExporter {
    /** Returns true only after the complete batch was accepted. */
    fun export(signals: List<TelemetrySignal>): Boolean
}

class OfflineTelemetryBuffer(private val capacity: Int = 2_000) {
    init { require(capacity > 0) }

    private val queue = ConcurrentLinkedQueue<TelemetrySignal>()

    fun enqueue(signal: TelemetrySignal) {
        while (queue.size >= capacity) queue.poll()
        queue.offer(signal)
    }

    fun pending(): List<TelemetrySignal> = queue.toList()

    fun flush(exporter: TelemetryExporter): Boolean {
        val batch = pending()
        if (batch.isEmpty()) return true
        if (!exporter.export(batch)) return false
        repeat(batch.size) { queue.poll() }
        return true
    }
}

object MeetTelemetry {
    private val context = AtomicReference(
        TelemetryContext("UNKNOWN", "UNKNOWN", "UNKNOWN"),
    )
    private val buffer = OfflineTelemetryBuffer()

    fun configure(value: TelemetryContext) {
        require(value.appVersion.isNotBlank())
        require(value.buildSha.isNotBlank())
        require(value.environment.isNotBlank())
        context.set(value)
    }

    fun event(
        name: String,
        attributes: Map<String, Any?> = emptyMap(),
        traceId: String = UUID.randomUUID().toString(),
        correlationId: String = traceId,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): TelemetrySignal = record(
        type = TelemetrySignalType.EVENT,
        name = name,
        attributes = attributes,
        traceId = traceId,
        correlationId = correlationId,
        nowEpochMs = nowEpochMs,
    )

    fun recordError(
        name: String,
        failureCode: String,
        attributes: Map<String, Any?> = emptyMap(),
        traceId: String = UUID.randomUUID().toString(),
    ): TelemetrySignal = record(
        TelemetrySignalType.ERROR,
        name,
        attributes + ("failureCode" to failureCode),
        traceId,
        traceId,
        System.currentTimeMillis(),
    )

    fun pendingSignals(): List<TelemetrySignal> = buffer.pending()

    fun flush(exporter: TelemetryExporter): Boolean = buffer.flush(exporter)

    private fun record(
        type: TelemetrySignalType,
        name: String,
        attributes: Map<String, Any?>,
        traceId: String,
        correlationId: String,
        nowEpochMs: Long,
    ): TelemetrySignal {
        require(name.matches(Regex("[a-z0-9_.]{3,96}"))) { "Controlled telemetry name required" }
        val platform = context.get()
        val filtered = TelemetryPrivacyPolicy.filter(
            attributes + mapOf(
                "appVersion" to platform.appVersion,
                "buildSha" to platform.buildSha,
                "environment" to platform.environment,
            ),
        )
        return TelemetrySignal(
            type = type,
            name = name,
            traceId = traceId,
            spanId = UUID.randomUUID().toString(),
            correlationId = correlationId,
            attributes = filtered,
            occurredAtEpochMs = nowEpochMs,
        ).also(buffer::enqueue)
    }
}
