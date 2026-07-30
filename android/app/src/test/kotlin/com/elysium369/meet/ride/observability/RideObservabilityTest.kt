package com.elysium369.meet.ride.observability

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideObservabilityTest {
    @Test
    fun `event encoding contains operational identifiers but no payload pii`() {
        val encoded = RideObservability.encode(
            RideObservability.event(
                type = RideTelemetryEventType.PIN_VERIFIED,
                commandId = "verify-pin:ride-1:command-1",
                tripId = "ride-1",
                version = 7,
                latencyMs = 125,
                correlationId = "correlation-1",
                nowEpochMs = 1234,
            ),
        )

        assertTrue(encoded.contains("\"eventType\":\"PIN_VERIFIED\""))
        assertTrue(encoded.contains("\"latencyMs\":125"))
        listOf(
            "boardingPin",
            "phone",
            "document",
            "address",
            "latitude",
            "longitude",
        ).forEach { forbidden ->
            assertFalse(encoded.contains(forbidden, ignoreCase = true))
        }
    }

    @Test
    fun `unsafe free text is discarded instead of logged`() {
        val encoded = RideObservability.encode(
            RideObservability.event(
                type = RideTelemetryEventType.SYNC_FAILED,
                commandId = "secret bearer token",
                tripId = "ride with spaces",
                version = 1,
                latencyMs = 1,
                errorCode = "server said phone 8888",
                nowEpochMs = 1,
            ),
        )
        assertFalse(encoded.contains("secret"))
        assertFalse(encoded.contains("8888"))
        assertFalse(encoded.contains("phone"))
    }
}
