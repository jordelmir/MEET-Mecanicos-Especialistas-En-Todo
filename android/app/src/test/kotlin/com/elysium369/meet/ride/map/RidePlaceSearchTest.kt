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
}
