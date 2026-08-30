package com.elysium369.meet.observability

import android.util.Log
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class TrustOperationTrace(
    val operation: String,
    val correlationId: String,
    val capability: String?,
    val startedAtMonotonicMs: Long,
)

@Serializable
data class TrustLogEvent(
    val eventId: String,
    val correlationId: String,
    val operation: String,
    val outcome: String,
    val capability: String? = null,
    val queueStatus: String? = null,
    val itemCount: Int? = null,
    val connectionState: String? = null,
    val resultCode: String? = null,
    val failureCode: String? = null,
    val durationMs: Long? = null,
    val occurredAtEpochMs: Long,
)

/** Structured, low-cardinality Trust Center signals. No PII or evidence payloads. */
object TrustCenterObservability {
    private const val TAG = "MeetTrustEvent"
    private val json = Json { encodeDefaults = false; explicitNulls = false }
    private val controlled = Regex("[A-Z0-9_]{1,80}")

    fun start(operation: String, capability: String? = null): TrustOperationTrace {
        val trace = TrustOperationTrace(
            operation = safe(operation),
            correlationId = UUID.randomUUID().toString(),
            capability = capability?.let(::safe),
            startedAtMonotonicMs = monotonicNowMs(),
        )
        record(trace, "STARTED", resultCode = "STARTED")
        return trace
    }

    fun succeeded(
        trace: TrustOperationTrace,
        resultCode: String,
        queueStatus: String? = null,
        itemCount: Int? = null,
    ) = record(
        trace = trace,
        outcome = "SUCCEEDED",
        resultCode = safe(resultCode),
        queueStatus = queueStatus?.let(::safe),
        itemCount = itemCount?.coerceAtLeast(0),
        durationMs = elapsed(trace),
    )

    fun failed(trace: TrustOperationTrace, error: Throwable) = record(
        trace = trace,
        outcome = "FAILED",
        failureCode = failureCode(error),
        durationMs = elapsed(trace),
    )

    fun realtime(connectionState: String, retryOrdinal: Long? = null) {
        val trace = TrustOperationTrace(
            operation = "REALTIME",
            correlationId = UUID.randomUUID().toString(),
            capability = null,
            startedAtMonotonicMs = monotonicNowMs(),
        )
        val state = safe(connectionState)
        val event = TrustLogEvent(
            eventId = UUID.randomUUID().toString(),
            correlationId = trace.correlationId,
            operation = trace.operation,
            outcome = "STATE_CHANGED",
            connectionState = state,
            resultCode = state,
            occurredAtEpochMs = System.currentTimeMillis(),
        )
        Log.i(TAG, json.encodeToString(event))
        MeetTelemetry.event(
            name = "trust.realtime.state",
            attributes = mapOf(
                "connectionState" to state,
                "retryOrdinal" to retryOrdinal,
                "transport" to "WEBSOCKET",
                "protocol" to "SUPABASE_REALTIME",
            ),
            traceId = trace.correlationId,
            correlationId = trace.correlationId,
        )
    }

    fun failureCode(error: Throwable): String {
        val normalized = error.message.orEmpty().uppercase()
        return listOf(
            "UNAUTHENTICATED",
            "PLATFORM_OWNER_REQUIRED",
            "AAL2_REQUIRED",
            "INVALID_VERIFICATION_APPLICATION",
            "INVALID_QUEUE_FILTER",
            "INVALID_REVIEW_DECISION",
            "APPLICATION_NOT_FOUND",
        ).firstOrNull(normalized::contains)
            ?: when {
                normalized.contains("TIMEOUT") -> "TIMEOUT"
                normalized.contains("NETWORK") || normalized.contains("HOST") -> "NETWORK_UNAVAILABLE"
                else -> "REMOTE_TRANSPORT_FAILURE"
            }
    }

    private fun record(
        trace: TrustOperationTrace,
        outcome: String,
        resultCode: String? = null,
        failureCode: String? = null,
        queueStatus: String? = null,
        itemCount: Int? = null,
        durationMs: Long? = null,
    ) {
        val event = TrustLogEvent(
            eventId = UUID.randomUUID().toString(),
            correlationId = trace.correlationId,
            operation = trace.operation,
            outcome = outcome,
            capability = trace.capability,
            queueStatus = queueStatus,
            itemCount = itemCount,
            resultCode = resultCode,
            failureCode = failureCode,
            durationMs = durationMs,
            occurredAtEpochMs = System.currentTimeMillis(),
        )
        val encoded = json.encodeToString(event)
        if (failureCode == null) Log.i(TAG, encoded) else Log.w(TAG, encoded)
        val attributes = mapOf(
            "operation" to trace.operation,
            "capability" to trace.capability,
            "resultCode" to (resultCode ?: outcome),
            "failureCode" to failureCode,
            "queueStatus" to queueStatus,
            "itemCount" to itemCount,
            "durationMs" to durationMs,
        )
        if (failureCode == null) {
            MeetTelemetry.event(
                name = "trust.operation.${outcome.lowercase()}",
                attributes = attributes,
                traceId = trace.correlationId,
                correlationId = trace.correlationId,
            )
        } else {
            MeetTelemetry.recordError(
                name = "trust.operation.failed",
                failureCode = failureCode,
                attributes = attributes,
                traceId = trace.correlationId,
                correlationId = trace.correlationId,
            )
        }
    }

    private fun elapsed(trace: TrustOperationTrace): Long =
        (monotonicNowMs() - trace.startedAtMonotonicMs).coerceAtLeast(0L)

    private fun safe(value: String): String = value.trim().uppercase()
        .takeIf(controlled::matches) ?: "UNKNOWN"

    private fun monotonicNowMs(): Long = System.nanoTime() / 1_000_000L
}
