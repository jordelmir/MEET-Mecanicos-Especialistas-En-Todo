package com.elysium369.meet.ride.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RideMapModelsTest {

    @Test
    fun `equal coordinates keep passenger pickup destination and driver identities`() {
        val samePoint = RideGeoPoint(
            latitude = 9.9281,
            longitude = -84.0907,
            accuracyMeters = 4.5f,
            capturedAtEpochMs = 1_000,
        )

        val markers = RideMarkerRole.entries.map { role ->
            RideMapMarker(
                id = role.name,
                role = role,
                point = samePoint,
                label = role.name,
            )
        }
        val state = RideMapState(markers = markers)

        assertEquals(4, state.markers.size)
        assertEquals(RideMarkerRole.entries.toSet(), state.markers.map { it.role }.toSet())
        assertNotEquals(state.markers[0], state.markers[1])
    }

    @Test
    fun `position freshness is explicit`() {
        val point = RideGeoPoint(
            latitude = 9.9281,
            longitude = -84.0907,
            accuracyMeters = 8f,
            capturedAtEpochMs = 10_000,
        )

        assertEquals(
            RidePositionFreshness.FRESH,
            point.freshness(nowEpochMs = 14_999, staleAfterMs = 5_000),
        )
        assertEquals(
            RidePositionFreshness.STALE,
            point.freshness(nowEpochMs = 15_001, staleAfterMs = 5_000),
        )
        assertEquals(
            RidePositionFreshness.CLOCK_SKEW,
            point.freshness(nowEpochMs = 9_999, staleAfterMs = 5_000),
        )
    }

    @Test
    fun `invalid coordinates accuracy and route are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            RideGeoPoint(91.0, -84.0, 1f, 1_000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RideGeoPoint(9.0, -181.0, 1f, 1_000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RideGeoPoint(9.0, -84.0, -1f, 1_000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RideMapState(
                markers = emptyList(),
                route = listOf(RideGeoPoint(9.0, -84.0, null, 1_000)),
            )
        }
    }
}
