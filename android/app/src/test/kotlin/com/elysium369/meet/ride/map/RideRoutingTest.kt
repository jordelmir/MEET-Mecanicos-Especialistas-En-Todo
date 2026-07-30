package com.elysium369.meet.ride.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RideRoutingTest {
    @Test
    fun `OSRM GeoJSON route preserves road geometry and metrics`() {
        val route = parseOsrmRoute(
            raw = """
                {
                  "code":"Ok",
                  "routes":[{
                    "distance":1834.5,
                    "duration":322.0,
                    "geometry":{
                      "type":"LineString",
                      "coordinates":[
                        [-84.081,9.932],
                        [-84.086,9.938],
                        [-84.100,9.950]
                      ]
                    }
                  }]
                }
            """.trimIndent(),
            capturedAtEpochMs = 1234L,
        )

        assertEquals(3, route.geometry.size)
        assertEquals(9.932, route.geometry.first().latitude, 0.000001)
        assertEquals(-84.100, route.geometry.last().longitude, 0.000001)
        assertEquals(1834.5, route.distanceMeters, 0.01)
        assertEquals(322.0, route.durationSeconds, 0.01)
        assertTrue(route.attribution.contains("OpenStreetMap"))
    }

    @Test(expected = RideRoutingException::class)
    fun `NoRoute never becomes a straight line`() {
        parseOsrmRoute(
            raw = """{"code":"NoRoute","message":"No route found","routes":[]}""",
            capturedAtEpochMs = 1234L,
        )
    }
}
