package com.elysium369.meet.observability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthObservabilityTest {
    @Test
    fun `structured auth log has controlled fields and no secret surface`() {
        val encoded = AuthObservability.encode(
            AuthLogEvent(
                eventId = "event-1",
                correlationId = "trace-1",
                operation = "LOGIN",
                outcome = "FAILED",
                failureCode = "INVALID_CREDENTIALS",
                durationMs = 125,
                occurredAtEpochMs = 1_000,
            ),
        )

        assertTrue(encoded.contains("INVALID_CREDENTIALS"))
        assertFalse(encoded.contains("email", ignoreCase = true))
        assertFalse(encoded.contains("password", ignoreCase = true))
        assertFalse(encoded.contains("token", ignoreCase = true))
        assertFalse(encoded.contains("url", ignoreCase = true))
    }

    @Test
    fun `counter and histogram preserve one auth trace`() {
        val counter = MeetTelemetry.incrementCounter(
            name = "auth.attempt.total",
            attributes = mapOf("operation" to "LOGIN", "resultCode" to "FAILED"),
            traceId = "auth-trace",
            correlationId = "auth-trace",
        )
        val duration = MeetTelemetry.histogram(
            name = "auth.attempt.duration",
            durationMs = 321,
            attributes = mapOf("operation" to "LOGIN", "resultCode" to "FAILED"),
            traceId = "auth-trace",
            correlationId = "auth-trace",
        )

        assertEquals(TelemetrySignalType.COUNTER, counter.type)
        assertEquals(TelemetrySignalType.HISTOGRAM, duration.type)
        assertEquals("auth-trace", counter.correlationId)
        assertEquals("auth-trace", duration.correlationId)
        assertEquals("321", duration.attributes["durationMs"])
    }
}
