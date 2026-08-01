package com.elysium369.meet.ride.map

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideMapResilienceTest {
    @Test
    fun `successful place lookups are reused from a clearly labelled local cache`() = runBlocking {
        var calls = 0
        val provider = object : RidePlaceSearchProvider {
            override suspend fun search(
                query: String,
                biasLatitude: Double?,
                biasLongitude: Double?,
                limit: Int,
            ): List<RidePlaceSuggestion> {
                calls += 1
                return listOf(place("Hospital México"))
            }
        }
        val resilient = ResilientRidePlaceSearchProvider(
            candidates = listOf(placeCandidate("primary", provider)),
        )

        val first = resilient.search("hospital", 9.93, -84.09)
        val second = resilient.search("hospital", 9.93, -84.09)

        assertEquals(1, calls)
        assertEquals(RideMapDataSource.NETWORK, first.single().source)
        assertEquals(RideMapDataSource.CACHE, second.single().source)
        assertTrue(second.single().attribution.contains("caché local"))
    }

    @Test
    fun `place fallback is used only after a provider failure`() = runBlocking {
        val failing = object : RidePlaceSearchProvider {
            override suspend fun search(
                query: String,
                biasLatitude: Double?,
                biasLongitude: Double?,
                limit: Int,
            ): List<RidePlaceSuggestion> = throw RidePlaceSearchException("HTTP 503")
        }
        val fallback = object : RidePlaceSearchProvider {
            override suspend fun search(
                query: String,
                biasLatitude: Double?,
                biasLongitude: Double?,
                limit: Int,
            ): List<RidePlaceSuggestion> = listOf(place("Hospital Calderón Guardia"))
        }
        val resilient = ResilientRidePlaceSearchProvider(
            candidates = listOf(
                placeCandidate("primary", failing),
                placeCandidate("fallback", fallback),
            ),
        )

        val result = resilient.search("hospital", null, null)

        assertEquals("Hospital Calderón Guardia", result.single().primaryLabel)
        assertTrue(
            resilient.health().first { it.id == "primary" }.consecutiveFailures > 0,
        )
    }

    @Test
    fun `circuit breaker rests an unhealthy endpoint before another attempt`() {
        var now = 1_000L
        val breaker = RideMapCircuitBreaker(
            failureThreshold = 2,
            coolDownMs = 10_000L,
            nowEpochMs = { now },
        )

        breaker.recordFailure()
        assertTrue(breaker.canAttempt())
        breaker.recordFailure()
        assertFalse(breaker.canAttempt())
        now += 10_000L
        assertTrue(breaker.canAttempt())
    }

    @Test
    fun `cached route preserves real geometry and is visibly marked as cache`() = runBlocking {
        var calls = 0
        val provider = object : RideRoutingProvider {
            override suspend fun route(waypoints: List<RideGeoPoint>): RideRoadRoute {
                calls += 1
                return roadRoute()
            }
        }
        val resilient = ResilientRideRoutingProvider(
            candidates = listOf(routeCandidate("primary", provider)),
        )
        val waypoints = listOf(point(9.928, -84.09), point(9.935, -84.10))

        val first = resilient.route(waypoints)
        val second = resilient.route(waypoints)

        assertEquals(1, calls)
        assertEquals(RideMapDataSource.NETWORK, first.source)
        assertEquals(RideMapDataSource.CACHE, second.source)
        assertEquals(first.geometry, second.geometry)
        assertTrue(second.attribution.contains("caché local"))
    }

    private fun place(label: String) = RidePlaceSuggestion(
        providerId = label,
        primaryLabel = label,
        secondaryLabel = "San José, Costa Rica",
        latitude = 9.93,
        longitude = -84.09,
        attribution = "© OpenStreetMap contributors · prueba",
    )

    private fun placeCandidate(
        id: String,
        provider: RidePlaceSearchProvider,
    ) = RidePlaceSearchProviderCandidate(
        endpoint = RideMapProviderEndpoint(id, "https://$id.example", publicPilotEndpoint = false),
        provider = provider,
    )

    private fun routeCandidate(
        id: String,
        provider: RideRoutingProvider,
    ) = RideRoutingProviderCandidate(
        endpoint = RideMapProviderEndpoint(id, "https://$id.example", publicPilotEndpoint = false),
        provider = provider,
    )

    private fun point(latitude: Double, longitude: Double) = RideGeoPoint(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = null,
        capturedAtEpochMs = 1L,
    )

    private fun roadRoute() = RideRoadRoute(
        geometry = listOf(point(9.928, -84.09), point(9.935, -84.10)),
        distanceMeters = 1_200.0,
        durationSeconds = 180.0,
        attribution = "Ruta OSRM · © OpenStreetMap contributors",
    )
}
