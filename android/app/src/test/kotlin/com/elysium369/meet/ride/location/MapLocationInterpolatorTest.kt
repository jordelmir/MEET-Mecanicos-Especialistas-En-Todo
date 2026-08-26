package com.elysium369.meet.ride.location

import org.junit.Assert.*
import org.junit.Test

class MapLocationInterpolatorTest {

    private val pointA = MapLocationInterpolator.LocationPoint(
        latitude = 9.9300,
        longitude = -84.0800,
        bearing = 0f,
        timestampMs = 10_000L,
    )

    private val pointB = MapLocationInterpolator.LocationPoint(
        latitude = 9.9400,
        longitude = -84.0800,
        bearing = 0f,
        timestampMs = 20_000L, // 10s later
    )

    @Test
    fun interpolates_halfway_at_midpoint_time() {
        val mid = MapLocationInterpolator.interpolate(
            from = pointA,
            to = pointB,
            currentRenderTimeMs = 15_000L, // exactly half
        )

        assertEquals(9.9350, mid.latitude, 0.0001)
        assertEquals(-84.0800, mid.longitude, 0.0001)
        assertFalse(mid.isStale)
    }

    @Test
    fun ceases_animation_and_marks_stale_when_gps_is_old() {
        // Point B was at 20,000ms, but current render time is 40,000ms (20s later > 15s cutoff)
        val stalePoint = MapLocationInterpolator.interpolate(
            from = pointA,
            to = pointB,
            currentRenderTimeMs = 40_000L,
        )

        assertTrue(stalePoint.isStale)
        assertEquals(pointB.latitude, stalePoint.latitude, 0.0001) // Fixed at last known point
        assertEquals("Ubicación actualizada hace 20s", stalePoint.stalenessDisplay)
    }

    @Test
    fun marks_live_when_less_than_3_seconds_old() {
        val livePoint = MapLocationInterpolator.interpolate(
            from = pointA,
            to = pointB,
            currentRenderTimeMs = 21_000L, // 1s old
        )
        assertFalse(livePoint.isStale)
        assertEquals("Ubicación en vivo", livePoint.stalenessDisplay)
    }
}
