package com.elysium369.meet.ride.driver

import com.elysium369.meet.ride.map.RerouteDetector
import com.elysium369.meet.ride.map.ReroutePolicy
import com.elysium369.meet.ride.map.RideGeoPoint
import com.elysium369.meet.ride.map.RideRoadRoute
import com.elysium369.meet.ride.map.RideRouteManeuver
import com.elysium369.meet.ride.map.RideRouteMatch
import com.elysium369.meet.ride.map.RideRouteMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideRouteMatcherTest {

    private val routePoints = listOf(
        RideGeoPoint(9.9300, -84.0800, null, 0L),
        RideGeoPoint(9.9320, -84.0800, null, 0L),
        RideGeoPoint(9.9340, -84.0800, null, 0L),
        RideGeoPoint(9.9340, -84.0780, null, 0L),
    )

    @Test
    fun `matcher finds closest segment and advances cursor`() {
        val matcher = RideRouteMatcher(localWindow = 2)

        // Point near first segment
        val sample1 = RideGeoPoint(9.9310, -84.0801, 5f, 1000L)
        val match1 = matcher.match(routePoints, sample1)

        assertNotNull(match1)
        assertEquals(0, match1!!.segmentIndex)
        assertTrue(match1.distanceMeters < 25.0)

        // Point near second segment
        val sample2 = RideGeoPoint(9.9330, -84.0800, 5f, 2000L)
        val match2 = matcher.match(routePoints, sample2)

        assertNotNull(match2)
        assertEquals(1, match2!!.segmentIndex)
        assertTrue(match2.alongTrackDistanceMeters > match1.alongTrackDistanceMeters)
    }

    @Test
    fun `nextManeuver returns upcoming maneuver based on progress`() {
        val maneuvers = listOf(
            RideRouteManeuver(
                distanceMeters = 440.0,
                durationSeconds = 60.0,
                streetName = "Avenida Central",
                type = "turn",
                modifier = "right",
                location = routePoints[2],
            ),
            RideRouteManeuver(
                distanceMeters = 220.0,
                durationSeconds = 30.0,
                streetName = "Calle 1",
                type = "arrive",
                modifier = null,
                location = routePoints[3],
            ),
        )

        val route = RideRoadRoute(
            geometry = routePoints,
            distanceMeters = 660.0,
            durationSeconds = 90.0,
            attribution = "Test",
            maneuvers = maneuvers,
        )

        val matcher = RideRouteMatcher()
        val match = matcher.match(routePoints, RideGeoPoint(9.9310, -84.0800, 5f, 1000L))!!

        val next = matcher.nextManeuver(route, match)
        assertNotNull(next)
        assertEquals("Avenida Central", next!!.first.streetName)
        assertEquals("turn", next.first.type)
        assertTrue(next.second > 0L)
    }

    @Test
    fun `reroute detector requires consecutive samples and respects cooldown`() {
        val policy = ReroutePolicy(
            minOffRouteMeters = 35.0,
            requiredConsecutiveSamples = 3,
            maxAccuracyMeters = 25.0,
            cooldownMillis = 10_000L,
        )
        val detector = RerouteDetector(policy)

        val onRouteMatch = RideRouteMatch(
            segmentIndex = 0,
            projectedPoint = routePoints[0],
            distanceMeters = 10.0,
            alongTrackDistanceMeters = 0.0,
        )
        val offRouteMatch = RideRouteMatch(
            segmentIndex = 0,
            projectedPoint = routePoints[0],
            distanceMeters = 60.0, // > 35m
            alongTrackDistanceMeters = 0.0,
        )

        // 1. On route -> no reroute
        assertFalse(detector.evaluate(onRouteMatch, accuracyMeters = 10.0, nowEpochMs = 1000L))

        // 2. Off route sample 1 -> no reroute yet
        assertFalse(detector.evaluate(offRouteMatch, accuracyMeters = 10.0, nowEpochMs = 2000L))

        // 3. Off route sample 2 -> no reroute yet
        assertFalse(detector.evaluate(offRouteMatch, accuracyMeters = 10.0, nowEpochMs = 3000L))

        // 4. If an on-route sample arrives, it resets counter!
        assertFalse(detector.evaluate(onRouteMatch, accuracyMeters = 10.0, nowEpochMs = 4000L))

        // 5. Off route sample 1 again
        assertFalse(detector.evaluate(offRouteMatch, accuracyMeters = 10.0, nowEpochMs = 5000L))
        // Sample 2
        assertFalse(detector.evaluate(offRouteMatch, accuracyMeters = 10.0, nowEpochMs = 6000L))
        // Sample 3 -> REROUTE TRIGGERED!
        assertTrue(detector.evaluate(offRouteMatch, accuracyMeters = 10.0, nowEpochMs = 7000L))

        // 6. Within cooldown (e.g. 2s after 7000L < 10000L cooldown) -> no reroute
        assertFalse(detector.evaluate(offRouteMatch, accuracyMeters = 10.0, nowEpochMs = 9000L))
    }
}
