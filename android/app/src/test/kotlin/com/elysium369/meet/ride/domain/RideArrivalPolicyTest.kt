package com.elysium369.meet.ride.domain

import com.elysium369.meet.ride.map.RideGeoPoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideArrivalPolicyTest {
    private val now = 100_000L
    private val pickup = RideGeoPoint(9.928100, -84.090700, 5f, now)

    @Test
    fun `arrival is allowed only inside one hundred meters with recent accurate gps`() {
        val near = RideGeoPoint(9.928500, -84.090700, 8f, now)
        val far = RideGeoPoint(9.930000, -84.090700, 8f, now)

        assertTrue(RideArrivalPolicy.evaluate(near, pickup, now).allowed)
        assertFalse(RideArrivalPolicy.evaluate(far, pickup, now).allowed)
    }

    @Test
    fun `arrival fails closed for stale or inaccurate gps`() {
        val stale = RideGeoPoint(9.928100, -84.090700, 8f, now - 31_000L)
        val inaccurate = RideGeoPoint(9.928100, -84.090700, 90f, now)

        assertFalse(RideArrivalPolicy.evaluate(stale, pickup, now).allowed)
        assertFalse(RideArrivalPolicy.evaluate(inaccurate, pickup, now).allowed)
    }
}
