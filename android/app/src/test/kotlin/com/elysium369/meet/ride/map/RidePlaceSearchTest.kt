package com.elysium369.meet.ride.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RidePlaceSearchTest {
    @Test
    fun `photon response keeps real coordinates and attribution`() {
        val raw = """
            {
              "features": [{
                "geometry": {"coordinates": [-84.0907, 9.9281]},
                "properties": {
                  "osm_type": "W",
                  "osm_id": 42,
                  "name": "Teatro Nacional",
                  "city": "San José",
                  "country": "Costa Rica"
                }
              }]
            }
        """.trimIndent()

        val result = parsePhotonResponse(raw).single()

        assertEquals("Teatro Nacional", result.primaryLabel)
        assertEquals(9.9281, result.latitude, 0.00001)
        assertEquals(-84.0907, result.longitude, 0.00001)
        assertTrue(result.attribution.contains("OpenStreetMap"))
    }

    @Test
    fun `distance is calculated from the GPS bias without changing coordinates`() {
        val suggestion = RidePlaceSuggestion(
            providerId = "hospital",
            primaryLabel = "Hospital San Juan de Dios",
            secondaryLabel = "San José, Costa Rica",
            latitude = 9.9345,
            longitude = -84.0919,
            attribution = "OpenStreetMap",
        )

        val distance = requireNotNull(suggestion.distanceKmFrom(9.9281, -84.0907))

        assertTrue(distance in 0.6..0.9)
        assertEquals(9.9345, suggestion.latitude, 0.000001)
    }
}
