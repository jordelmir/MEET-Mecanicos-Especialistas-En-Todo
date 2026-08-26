package com.elysium369.meet.ride.eta

import org.junit.Assert.*
import org.junit.Test

class RideEtaEstimateTest {

    @Test
    fun formats_display_with_honest_source_label() {
        val googleEta = RideEtaEstimate(
            etaSeconds = 180, // 3 min
            distanceMeters = 1200,
            sourceRaw = "GOOGLE_TRAFFIC",
            confidence = 0.95,
        )
        assertEquals("~3 min (Tráfico en tiempo real)", googleEta.formattedDisplay)

        val fallbackEta = RideEtaEstimate(
            etaSeconds = 240, // 4 min
            distanceMeters = 1500,
            sourceRaw = "HAVERSINE_FALLBACK",
            confidence = 0.50,
        )
        assertEquals("~4 min (Estimado geométrico)", fallbackEta.formattedDisplay)
    }

    @Test
    fun formats_1_min_display_properly() {
        val eta = RideEtaEstimate(
            etaSeconds = 45,
            distanceMeters = 200,
            sourceRaw = "GOOGLE_TRAFFIC",
        )
        assertEquals("~1 min (Tráfico en tiempo real)", eta.formattedDisplay)
    }
}
