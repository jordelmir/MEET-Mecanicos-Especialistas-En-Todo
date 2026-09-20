package com.elysium369.meet.safety

import com.elysium369.meet.core.geo.GeoMarkerRole
import com.elysium369.meet.safety.data.SafetyPrivateMapPoint
import com.elysium369.meet.safety.geo.SafetyMapAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyPrivateMapMarkerTest {
    @Test
    fun ownerReportRemainsAVisiblyPrivateTimedMarker() {
        val occurredAt = 1_700_000_000_000L
        val state = SafetyMapAdapter.build(
            points = emptyList(),
            privatePoints = listOf(
                SafetyPrivateMapPoint(
                    reportId = "7d5b0d4b-7474-4d41-91b0-2ad8d6f24978",
                    category = "THREAT",
                    latitude = 9.932,
                    longitude = -84.079,
                    accuracyMeters = 12f,
                    occurredAt = occurredAt,
                    serverState = null,
                    syncState = "QUEUED",
                ),
            ),
        )

        val marker = state.markers.single()
        assertEquals(GeoMarkerRole.PRIVATE_INCIDENT_PIN, marker.role)
        assertEquals("private:7d5b0d4b-7474-4d41-91b0-2ad8d6f24978", marker.id)
        assertEquals(occurredAt, marker.point.capturedAtEpochMs)
        assertTrue(marker.isHighlighted)
    }
}
