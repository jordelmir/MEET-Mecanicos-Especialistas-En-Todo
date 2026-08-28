package com.elysium369.meet.observability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetTelemetryTest {
    @Test
    fun vinFailureIncludesStrategyButNoVin() {
        val signal = MeetTelemetry.event(
            "obd.vin.failure",
            mapOf(
                "strategy" to "UDS_F190",
                "resultCode" to "INVALID_1HGCM82633A004352",
                "vin" to "1HGCM82633A004352",
            ),
        )

        assertEquals("UDS_F190", signal.attributes["strategy"])
        assertFalse(signal.attributes.values.any { it.contains("1HGCM82633A004352") })
        assertFalse(signal.attributes.containsKey("vin"))
    }

    @Test
    fun vinNeverAppearsInTelemetry() = vinFailureIncludesStrategyButNoVin()

    @Test
    fun legalTelemetryContainsNoNarrativeAndAuthContainsNoEmail() {
        val legal = TelemetryPrivacyPolicy.filter(
            mapOf("taxonomyVersion" to "CR-2026.1", "narrative" to "Mi caso privado"),
        )
        val auth = TelemetryPrivacyPolicy.filter(
            mapOf("operation" to "LOGIN", "email" to "person@example.com"),
        )

        assertEquals(mapOf("taxonomyVersion" to "CR-2026.1"), legal)
        assertEquals(mapOf("operation" to "LOGIN"), auth)
    }

    @Test
    fun legalNarrativeNeverLogged() = legalTelemetryContainsNoNarrativeAndAuthContainsNoEmail()

    @Test
    fun authTelemetryContainsNoEmail() = legalTelemetryContainsNoNarrativeAndAuthContainsNoEmail()

    @Test
    fun everyObdAttemptHasTraceId() {
        val signal = MeetTelemetry.event("obd.connection_attempt", traceId = "attempt-123")
        assertEquals("attempt-123", signal.traceId)
    }

    @Test
    fun telemetryContainsBuildSha() {
        MeetTelemetry.configure(TelemetryContext("4.21.0", "abc123def456", "test"))
        val signal = MeetTelemetry.event("app.startup")

        assertEquals("abc123def456", signal.attributes["buildSha"])
    }

    @Test
    fun offlineTelemetryBuffersAndRetries() {
        val queue = OfflineTelemetryBuffer(capacity = 2)
        val sample = MeetTelemetry.event("obd.connection_attempt")
        queue.enqueue(sample)

        assertFalse(queue.flush(TelemetryExporter { false }))
        assertEquals(1, queue.pending().size)
        assertTrue(queue.flush(TelemetryExporter { true }))
        assertTrue(queue.pending().isEmpty())
    }
}
