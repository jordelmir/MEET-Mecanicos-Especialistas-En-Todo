package com.elysium369.meet.ride.driver

import com.elysium369.meet.ride.map.RideManeuverFormatter
import com.elysium369.meet.ride.map.parseOsrmRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RideRoutingManeuverParserTest {

    @Test
    fun `parseOsrmRoute extracts turn-by-turn maneuvers from steps payload`() {
        val json = """
            {
              "code": "Ok",
              "routes": [{
                "distance": 2400.0,
                "duration": 480.0,
                "geometry": {
                  "type": "LineString",
                  "coordinates": [
                    [-84.0800, 9.9300],
                    [-84.0810, 9.9310],
                    [-84.0850, 9.9350]
                  ]
                },
                "legs": [{
                  "steps": [
                    {
                      "distance": 34.0,
                      "duration": 12.0,
                      "name": "Radial San Antonio",
                      "maneuver": {
                        "type": "turn",
                        "modifier": "left",
                        "location": [-84.0800, 9.9300]
                      }
                    },
                    {
                      "distance": 180.0,
                      "duration": 30.0,
                      "name": "Avenida 2",
                      "maneuver": {
                        "type": "turn",
                        "modifier": "right",
                        "location": [-84.0810, 9.9310]
                      }
                    },
                    {
                      "distance": 0.0,
                      "duration": 0.0,
                      "name": "",
                      "maneuver": {
                        "type": "arrive",
                        "modifier": null,
                        "location": [-84.0850, 9.9350]
                      }
                    }
                  ]
                }]
              }]
            }
        """.trimIndent()

        val route = parseOsrmRoute(json, capturedAtEpochMs = 1000L)

        assertEquals(3, route.maneuvers.size)

        val first = route.maneuvers[0]
        assertEquals("Radial San Antonio", first.streetName)
        assertEquals("turn", first.type)
        assertEquals("left", first.modifier)
        assertEquals(34.0, first.distanceMeters, 0.01)

        val symbol = RideManeuverFormatter.formatManeuverSymbol(first.type, first.modifier)
        assertEquals("↰", symbol)

        val instruction = RideManeuverFormatter.formatInstruction(first.type, first.modifier, first.streetName)
        assertTrue(instruction.contains("Radial San Antonio"))

        val second = route.maneuvers[1]
        assertEquals("Avenida 2", second.streetName)
        assertEquals("↱", RideManeuverFormatter.formatManeuverSymbol(second.type, second.modifier))

        val third = route.maneuvers[2]
        assertEquals("🏁", RideManeuverFormatter.formatManeuverSymbol(third.type, third.modifier))
    }

    @Test
    fun `parseOsrmRoute handles payload with no legs gracefully`() {
        val json = """
            {
              "code": "Ok",
              "routes": [{
                "distance": 100.0,
                "duration": 20.0,
                "geometry": {
                  "type": "LineString",
                  "coordinates": [
                    [-84.0800, 9.9300],
                    [-84.0810, 9.9310]
                  ]
                }
              }]
            }
        """.trimIndent()

        val route = parseOsrmRoute(json, capturedAtEpochMs = 1000L)
        assertTrue(route.maneuvers.isEmpty())
        assertEquals(2, route.geometry.size)
    }
}
