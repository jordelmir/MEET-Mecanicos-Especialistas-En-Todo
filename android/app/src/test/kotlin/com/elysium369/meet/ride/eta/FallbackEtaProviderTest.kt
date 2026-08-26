package com.elysium369.meet.ride.eta

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FallbackEtaProviderTest {

    private val provider = FallbackEtaProvider()

    @Test
    fun calculates_reasonable_urban_eta() = runBlocking {
        // San José downtown coordinates (~1 km straight distance)
        val result = provider.calculateEta(
            originLat = 9.9333,
            originLon = -84.0833,
            destLat = 9.9400,
            destLon = -84.0750,
        )

        assertTrue(result.isSuccess)
        val estimate = result.getOrThrow()
        assertTrue(estimate.distanceMeters in 800..2500) // Winding urban distance
        assertTrue(estimate.etaSeconds in 100..600)
        assertEquals(RideEtaSource.HAVERSINE_FALLBACK, estimate.source)
        assertEquals(0.50, estimate.confidence, 0.01)
    }
}
