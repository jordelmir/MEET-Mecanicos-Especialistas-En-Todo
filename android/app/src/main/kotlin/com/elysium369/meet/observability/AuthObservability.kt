package com.elysium369.meet.observability

import android.util.Log
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class AuthOperation {
    LOGIN,
    SIGN_UP,
    RECOVERY_REQUEST,
    PASSWORD_UPDATE,
}

enum class AuthOutcome {
    STARTED,
    SUCCEEDED,
    FAILED,
}

/**
 * Deliberately excludes email, password, tokens, recovery URLs and exception text.
 * Supabase owns the server-side security audit; this record describes only client health.
 */
@Serializable
data class AuthLogEvent(
    val eventId: String,
    val correlationId: String,
    val operation: String,
    val outcome: String,
    val resultCode: String? = null,
    val failureCode: String? = null,
    val durationMs: Long? = null,
    val occurredAtEpochMs: Long,
)

data class AuthAttempt(
    val operation: AuthOperation,
    val correlationId: String,
    val startedAtMonotonicMs: Long,
)

object AuthObservability {
    private const val TAG = "MeetAuthEvent"
    private val controlledCode = Regex("[A-Z0-9_]{1,80}")
    private val json = Json {
        encodeDefaults = false
        explicitNulls = false
    }

    fun start(
        operation: AuthOperation,
        nowMonotonicMs: Long = monotonicNowMs(),
        nowEpochMs: Long = System.currentTimeMillis(),
    ): AuthAttempt {
        val attempt = AuthAttempt(
            operation = operation,
            correlationId = UUID.randomUUID().toString(),
            startedAtMonotonicMs = nowMonotonicMs,
        )
        record(
            event = event(
                attempt = attempt,
                outcome = AuthOutcome.STARTED,
                nowEpochMs = nowEpochMs,
            ),
            telemetryName = "auth.attempt.started",
        )
        MeetTelemetry.incrementCounter(
            name = "auth.attempt.total",
            attributes = mapOf("operation" to operation.name, "resultCode" to "STARTED"),
            traceId = attempt.correlationId,
            correlationId = attempt.correlationId,
            nowEpochMs = nowEpochMs,
        )
        return attempt
    }

    fun succeeded(
        attempt: AuthAttempt,
        resultCode: String,
        nowMonotonicMs: Long = monotonicNowMs(),
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        val durationMs = elapsedMs(attempt, nowMonotonicMs)
        val safeResult = controlled(resultCode, "SUCCESS")
        record(
            event = event(attempt, AuthOutcome.SUCCEEDED, safeResult, null, durationMs, nowEpochMs),
            telemetryName = "auth.attempt.succeeded",
        )
        MeetTelemetry.incrementCounter(
            name = "auth.attempt.total",
            attributes = mapOf("operation" to attempt.operation.name, "resultCode" to safeResult),
            traceId = attempt.correlationId,
            correlationId = attempt.correlationId,
            nowEpochMs = nowEpochMs,
        )
        recordDuration(attempt, safeResult, durationMs, nowEpochMs)
    }

    fun failed(
        attempt: AuthAttempt,
        failureCode: String,
        nowMonotonicMs: Long = monotonicNowMs(),
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        val durationMs = elapsedMs(attempt, nowMonotonicMs)
        val safeFailure = controlled(failureCode, "UNKNOWN")
        val logEvent = event(
            attempt = attempt,
            outcome = AuthOutcome.FAILED,
            failureCode = safeFailure,
            durationMs = durationMs,
            nowEpochMs = nowEpochMs,
        )
        Log.w(TAG, encode(logEvent))
        MeetTelemetry.recordError(
            name = "auth.attempt.failed",
            failureCode = safeFailure,
            attributes = mapOf("operation" to attempt.operation.name, "durationMs" to durationMs),
            traceId = attempt.correlationId,
            correlationId = attempt.correlationId,
            nowEpochMs = nowEpochMs,
        )
        MeetTelemetry.incrementCounter(
            name = "auth.attempt.total",
            attributes = mapOf("operation" to attempt.operation.name, "resultCode" to "FAILED", "failureCode" to safeFailure),
            traceId = attempt.correlationId,
            correlationId = attempt.correlationId,
            nowEpochMs = nowEpochMs,
        )
        recordDuration(attempt, "FAILED", durationMs, nowEpochMs)
    }

    fun recoveryLink(received: Boolean, accepted: Boolean, nowEpochMs: Long = System.currentTimeMillis()) {
        if (!received) return
        val correlationId = UUID.randomUUID().toString()
        val result = if (accepted) "ACCEPTED" else "REJECTED"
        val event = AuthLogEvent(
            eventId = UUID.randomUUID().toString(),
            correlationId = correlationId,
            operation = AuthOperation.PASSWORD_UPDATE.name,
            outcome = result,
            resultCode = result,
            occurredAtEpochMs = nowEpochMs,
        )
        if (accepted) Log.i(TAG, encode(event)) else Log.w(TAG, encode(event))
        MeetTelemetry.event(
            name = "auth.recovery_link.${result.lowercase()}",
            attributes = mapOf("operation" to AuthOperation.PASSWORD_UPDATE.name, "resultCode" to result),
            traceId = correlationId,
            correlationId = correlationId,
            nowEpochMs = nowEpochMs,
        )
    }

    fun encode(event: AuthLogEvent): String = json.encodeToString(event)

    private fun event(
        attempt: AuthAttempt,
        outcome: AuthOutcome,
        resultCode: String? = null,
        failureCode: String? = null,
        durationMs: Long? = null,
        nowEpochMs: Long,
    ): AuthLogEvent = AuthLogEvent(
        eventId = UUID.randomUUID().toString(),
        correlationId = attempt.correlationId,
        operation = attempt.operation.name,
        outcome = outcome.name,
        resultCode = resultCode,
        failureCode = failureCode,
        durationMs = durationMs,
        occurredAtEpochMs = nowEpochMs,
    )

    private fun record(event: AuthLogEvent, telemetryName: String) {
        Log.i(TAG, encode(event))
        MeetTelemetry.event(
            name = telemetryName,
            attributes = mapOf(
                "operation" to event.operation,
                "resultCode" to (event.resultCode ?: event.outcome),
                "durationMs" to event.durationMs,
            ),
            traceId = event.correlationId,
            correlationId = event.correlationId,
            nowEpochMs = event.occurredAtEpochMs,
        )
    }

    private fun recordDuration(
        attempt: AuthAttempt,
        resultCode: String,
        durationMs: Long,
        nowEpochMs: Long,
    ) {
        MeetTelemetry.histogram(
            name = "auth.attempt.duration",
            durationMs = durationMs,
            attributes = mapOf("operation" to attempt.operation.name, "resultCode" to resultCode),
            traceId = attempt.correlationId,
            correlationId = attempt.correlationId,
            nowEpochMs = nowEpochMs,
        )
    }

    private fun elapsedMs(attempt: AuthAttempt, nowMonotonicMs: Long): Long =
        (nowMonotonicMs - attempt.startedAtMonotonicMs).coerceAtLeast(0L)

    private fun controlled(value: String, fallback: String): String = value
        .trim()
        .uppercase()
        .takeIf(controlledCode::matches)
        ?: fallback

    private fun monotonicNowMs(): Long = System.nanoTime() / 1_000_000L
}
