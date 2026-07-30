package com.elysium369.meet.ride.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RideCommandEnvelopeTest {

    @Test
    fun `command envelope keeps concurrency and idempotency metadata explicit`() {
        val envelope = RideCommandEnvelope(
            rideId = RideId.of("ride-123"),
            expectedVersion = RideVersion.of(9),
            idempotencyKey = RideIdempotencyKey.of("01JRIDECOMMAND000000000001"),
            type = RideCommandType.COMPLETE,
            payloadVersion = RidePayloadVersion.of(1),
        )

        assertEquals("ride-123", envelope.rideId.value)
        assertEquals(9, envelope.expectedVersion.value)
        assertEquals(RideCommandType.COMPLETE, envelope.type)
    }

    @Test
    fun `command identifiers and versions reject unsafe input`() {
        assertThrows(IllegalArgumentException::class.java) {
            RideId.of(" ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RideVersion.of(-1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RidePayloadVersion.of(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RideIdempotencyKey.of("short")
        }
    }
}
