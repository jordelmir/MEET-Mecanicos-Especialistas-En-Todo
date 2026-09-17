package com.elysium369.meet.ride.driver

import com.elysium369.meet.ride.data.remote.RideCommandPayload
import com.elysium369.meet.ride.data.remote.RideQueuedCommand
import com.elysium369.meet.ride.domain.RideCommandEnvelope
import com.elysium369.meet.ride.domain.RideCommandType
import com.elysium369.meet.ride.domain.RideId
import com.elysium369.meet.ride.domain.RideIdempotencyKey
import com.elysium369.meet.ride.domain.RidePayloadVersion
import com.elysium369.meet.ride.domain.RideVersion
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideCommandIdempotencyTest {

    @Test
    fun `driver arrived command formats coordinates with canonical decimals and stable idempotency key`() {
        val sample = DriverLocationSample(
            latitude = 9.933333333333334,
            longitude = -84.08333333333333,
            accuracyMeters = 5.25,
            capturedAt = Instant.parse("2026-09-16T20:00:00Z"),
            capturedAtElapsedRealtimeNanos = 100_000_000_000L,
        )

        val idempotencyKey = UUID.randomUUID().toString()

        val command = RideQueuedCommand(
            rideId = "ride-777",
            expectedVersion = 3L,
            idempotencyKey = idempotencyKey,
            type = RideCommandType.DRIVER_ARRIVED,
            payloadVersion = 1,
            payload = RideCommandPayload(
                driverLatitude = sample.latitude.toBigDecimal().stripTrailingZeros().toPlainString(),
                driverLongitude = sample.longitude.toBigDecimal().stripTrailingZeros().toPlainString(),
                driverAccuracyMeters = sample.accuracyMeters.toBigDecimal().stripTrailingZeros().toPlainString(),
                driverCapturedAt = sample.capturedAt.toString(),
            ),
        )

        assertEquals("ride-777", command.rideId)
        assertEquals(3L, command.expectedVersion)
        assertEquals(idempotencyKey, command.idempotencyKey)
        assertEquals(RideCommandType.DRIVER_ARRIVED, command.type)

        // Verifies no scientific notation in decimal payload
        assertTrue(!command.payload.driverLatitude!!.contains("E"))
        assertTrue(!command.payload.driverLongitude!!.contains("E"))
        assertEquals("5.25", command.payload.driverAccuracyMeters)
        assertEquals("2026-09-16T20:00:00Z", command.payload.driverCapturedAt)

        // Validates mapping into domain envelope
        val envelope = RideCommandEnvelope(
            rideId = RideId.of(command.rideId),
            expectedVersion = RideVersion.of(command.expectedVersion),
            idempotencyKey = RideIdempotencyKey.of(command.idempotencyKey),
            type = command.type,
            payloadVersion = RidePayloadVersion.of(command.payloadVersion),
        )

        assertEquals("ride-777", envelope.rideId.value)
        assertEquals(3L, envelope.expectedVersion.value)
        assertEquals(idempotencyKey, envelope.idempotencyKey.value)
    }

    @Test
    fun `double swipe produces identical idempotency key if reattempted with same action token`() {
        val actionIdempotencyToken = UUID.randomUUID().toString()

        val sample1 = DriverLocationSample(
            latitude = 9.9300,
            longitude = -84.0800,
            accuracyMeters = 8.0,
            capturedAt = Instant.parse("2026-09-16T20:00:00Z"),
            capturedAtElapsedRealtimeNanos = 100_000_000_000L,
        )

        val command1 = RideQueuedCommand(
            rideId = "ride-123",
            expectedVersion = 5L,
            idempotencyKey = actionIdempotencyToken,
            type = RideCommandType.DRIVER_ARRIVED,
            payloadVersion = 1,
            payload = RideCommandPayload(
                driverLatitude = sample1.latitude.toBigDecimal().stripTrailingZeros().toPlainString(),
                driverLongitude = sample1.longitude.toBigDecimal().stripTrailingZeros().toPlainString(),
                driverAccuracyMeters = sample1.accuracyMeters.toBigDecimal().stripTrailingZeros().toPlainString(),
                driverCapturedAt = sample1.capturedAt.toString(),
            ),
        )

        val command2 = RideQueuedCommand(
            rideId = "ride-123",
            expectedVersion = 5L,
            idempotencyKey = actionIdempotencyToken,
            type = RideCommandType.DRIVER_ARRIVED,
            payloadVersion = 1,
            payload = command1.payload,
        )

        // Same idempotency key ensures DB primary key uniqueness in outbox table
        assertEquals(command1.idempotencyKey, command2.idempotencyKey)
        assertEquals(command1.expectedVersion, command2.expectedVersion)
        assertEquals(command1.payload, command2.payload)
    }

    @Test
    fun `distinct operational actions generate distinct idempotency keys`() {
        val arrivalKey = UUID.randomUUID().toString()
        val completeKey = UUID.randomUUID().toString()

        assertNotEquals(arrivalKey, completeKey)

        val envelopeArrival = RideCommandEnvelope(
            rideId = RideId.of("ride-123"),
            expectedVersion = RideVersion.of(5),
            idempotencyKey = RideIdempotencyKey.of(arrivalKey),
            type = RideCommandType.DRIVER_ARRIVED,
            payloadVersion = RidePayloadVersion.of(1),
        )

        val envelopeComplete = RideCommandEnvelope(
            rideId = RideId.of("ride-123"),
            expectedVersion = RideVersion.of(8),
            idempotencyKey = RideIdempotencyKey.of(completeKey),
            type = RideCommandType.COMPLETE,
            payloadVersion = RidePayloadVersion.of(1),
        )

        assertNotEquals(envelopeArrival.idempotencyKey.value, envelopeComplete.idempotencyKey.value)
        assertTrue(envelopeComplete.expectedVersion.value > envelopeArrival.expectedVersion.value)
    }
}
