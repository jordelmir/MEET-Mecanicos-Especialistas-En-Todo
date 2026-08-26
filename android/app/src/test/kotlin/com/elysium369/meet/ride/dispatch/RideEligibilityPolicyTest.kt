package com.elysium369.meet.ride.dispatch

import com.elysium369.meet.ride.presence.RideDriverAvailability
import org.junit.Assert.*
import org.junit.Test

class RideEligibilityPolicyTest {

    @Test
    fun available_driver_with_fresh_gps_is_eligible() {
        val now = 100_000L
        val result = RideEligibilityPolicy.evaluate(
            availability = RideDriverAvailability.AVAILABLE,
            lastSeenAtMs = now - 10_000L,
            nowMs = now,
        )
        assertTrue(result.eligible)
    }

    @Test
    fun finishing_current_trip_with_fresh_gps_is_eligible() {
        val now = 100_000L
        val result = RideEligibilityPolicy.evaluate(
            availability = RideDriverAvailability.FINISHING_CURRENT_TRIP,
            lastSeenAtMs = now - 10_000L,
            nowMs = now,
        )
        assertTrue(result.eligible)
    }

    @Test
    fun offline_driver_is_not_eligible() {
        val now = 100_000L
        val result = RideEligibilityPolicy.evaluate(
            availability = RideDriverAvailability.OFFLINE,
            lastSeenAtMs = now - 10_000L,
            nowMs = now,
        )
        assertFalse(result.eligible)
    }

    @Test
    fun in_trip_driver_is_not_eligible() {
        val now = 100_000L
        val result = RideEligibilityPolicy.evaluate(
            availability = RideDriverAvailability.IN_TRIP,
            lastSeenAtMs = now - 10_000L,
            nowMs = now,
        )
        assertFalse(result.eligible)
    }

    @Test
    fun stale_gps_driver_is_not_eligible() {
        val now = 100_000L
        val result = RideEligibilityPolicy.evaluate(
            availability = RideDriverAvailability.AVAILABLE,
            lastSeenAtMs = now - (6 * 60 * 1000L), // 6 minutes ago
            nowMs = now,
        )
        assertFalse(result.eligible)
    }

    @Test
    fun suspended_driver_is_not_eligible() {
        val now = 100_000L
        val result = RideEligibilityPolicy.evaluate(
            availability = RideDriverAvailability.SUSPENDED,
            lastSeenAtMs = now - 10_000L,
            nowMs = now,
        )
        assertFalse(result.eligible)
    }
}
