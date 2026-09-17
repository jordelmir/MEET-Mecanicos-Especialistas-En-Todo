package com.elysium369.meet.ride.driver

import com.elysium369.meet.data.local.entities.RideRequestEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RideDriverFeedPolicyTest {

    private fun createRide(
        requestId: String = "req-1",
        passengerId: String = "pax-1",
        status: String = "OPEN",
        assignedDriverId: String? = null,
        createdAt: Long = 100_000L
    ): RideRequestEntity {
        return RideRequestEntity(
            requestId = requestId,
            passengerId = passengerId,
            passengerName = "Test Pax",
            passengerPhone = "+50688888888",
            pickupLatitude = 9.93,
            pickupLongitude = -84.08,
            pickupAddress = "San Jose",
            pickupAccuracy = 5.0f,
            destLatitude = 9.94,
            destLongitude = -84.07,
            destAddress = "San Pedro",
            priceOffer = 3500.0,
            currency = "CRC",
            estimatedDistanceKm = 3.5,
            estimatedDurationMin = 10,
            status = status,
            assignedDriverId = assignedDriverId,
            createdAt = createdAt
        )
    }

    @Test
    fun `open unassigned ride within expiry window is eligible`() {
        val now = 100_000L
        val ride = createRide(requestId = "r1", createdAt = now - 30_000L)
        val eligible = RideDriverFeedPolicy.eligibleRides(
            rides = listOf(ride),
            actorIds = setOf("drv-1"),
            activeRideId = null,
            hiddenRideIds = emptySet(),
            nowEpochMs = now
        )
        assertEquals(1, eligible.size)
        assertEquals("r1", eligible.first().requestId)
    }

    @Test
    fun `rides with non-OPEN status are excluded`() {
        val now = 100_000L
        val acceptedRide = createRide(requestId = "r1", status = "ACCEPTED")
        val inProgressRide = createRide(requestId = "r2", status = "IN_PROGRESS")
        val cancelledRide = createRide(requestId = "r3", status = "CANCELLED")

        val eligible = RideDriverFeedPolicy.eligibleRides(
            rides = listOf(acceptedRide, inProgressRide, cancelledRide),
            actorIds = setOf("drv-1"),
            activeRideId = null,
            hiddenRideIds = emptySet(),
            nowEpochMs = now
        )
        assertTrue(eligible.isEmpty())
    }

    @Test
    fun `rides with assigned driver are excluded`() {
        val now = 100_000L
        val assignedRide = createRide(requestId = "r1", status = "OPEN", assignedDriverId = "other-drv")

        val eligible = RideDriverFeedPolicy.eligibleRides(
            rides = listOf(assignedRide),
            actorIds = setOf("drv-1"),
            activeRideId = null,
            hiddenRideIds = emptySet(),
            nowEpochMs = now
        )
        assertTrue(eligible.isEmpty())
    }

    @Test
    fun `active ride of current driver is excluded`() {
        val now = 100_000L
        val currentRide = createRide(requestId = "r-active")
        val candidate = createRide(requestId = "r-candidate")

        val eligible = RideDriverFeedPolicy.eligibleRides(
            rides = listOf(currentRide, candidate),
            actorIds = setOf("drv-1"),
            activeRideId = currentRide.requestId,
            hiddenRideIds = emptySet(),
            nowEpochMs = now
        )
        assertEquals(1, eligible.size)
        assertEquals("r-candidate", eligible.first().requestId)
    }

    @Test
    fun `driver own passenger request is excluded`() {
        val now = 100_000L
        val ownRide = createRide(requestId = "r-own", passengerId = "user-123")
        val otherRide = createRide(requestId = "r-other", passengerId = "pax-456")

        val eligible = RideDriverFeedPolicy.eligibleRides(
            rides = listOf(ownRide, otherRide),
            actorIds = setOf("user-123", "driver-profile-abc"),
            activeRideId = null,
            hiddenRideIds = emptySet(),
            nowEpochMs = now
        )
        assertEquals(1, eligible.size)
        assertEquals("r-other", eligible.first().requestId)
    }

    @Test
    fun `dismissed or hidden rides are excluded`() {
        val now = 100_000L
        val hiddenRide = createRide(requestId = "r-hidden")
        val normalRide = createRide(requestId = "r-normal")

        val eligible = RideDriverFeedPolicy.eligibleRides(
            rides = listOf(hiddenRide, normalRide),
            actorIds = setOf("drv-1"),
            activeRideId = null,
            hiddenRideIds = setOf("r-hidden"),
            nowEpochMs = now
        )
        assertEquals(1, eligible.size)
        assertEquals("r-normal", eligible.first().requestId)
    }

    @Test
    fun `expired rides beyond ttl are excluded`() {
        val now = 1_000_000L
        val veryOldRide = createRide(requestId = "r-old", createdAt = now - (24 * 3600 * 1000L))
        val freshRide = createRide(requestId = "r-fresh", createdAt = now - 5_000L)

        val eligible = RideDriverFeedPolicy.eligibleRides(
            rides = listOf(veryOldRide, freshRide),
            actorIds = setOf("drv-1"),
            activeRideId = null,
            hiddenRideIds = emptySet(),
            nowEpochMs = now
        )
        assertEquals(1, eligible.size)
        assertEquals("r-fresh", eligible.first().requestId)
    }
}
