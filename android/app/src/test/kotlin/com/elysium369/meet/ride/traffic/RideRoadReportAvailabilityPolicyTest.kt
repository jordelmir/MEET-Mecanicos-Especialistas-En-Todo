package com.elysium369.meet.ride.traffic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideRoadReportAvailabilityPolicyTest {
    @Test
    fun `driver may report only during server confirmed route`() {
        val result = RideRoadReportAvailabilityPolicy.evaluate(
            isDriver = true,
            localStatus = "IN_PROGRESS",
            serverState = "IN_PROGRESS",
            serverVersion = 7L,
            hasCurrentGps = true,
        )

        assertTrue(result.allowed)
    }

    @Test
    fun `accepted arrived and completed trips cannot report`() {
        listOf("ACCEPTED", "ARRIVED", "PASSENGER_ONBOARD", "COMPLETED", "CANCELLED")
            .forEach { status ->
                assertFalse(
                    RideRoadReportAvailabilityPolicy.evaluate(
                        isDriver = true,
                        localStatus = status,
                        serverState = status,
                        serverVersion = 3L,
                        hasCurrentGps = true,
                    ).allowed,
                )
            }
    }

    @Test
    fun `passenger stale projection and missing gps fail closed`() {
        assertFalse(
            RideRoadReportAvailabilityPolicy.evaluate(
                isDriver = false,
                localStatus = "IN_PROGRESS",
                serverState = "IN_PROGRESS",
                serverVersion = 9L,
                hasCurrentGps = true,
            ).allowed,
        )
        assertFalse(
            RideRoadReportAvailabilityPolicy.evaluate(
                isDriver = true,
                localStatus = "IN_PROGRESS",
                serverState = "PASSENGER_ONBOARD",
                serverVersion = 9L,
                hasCurrentGps = true,
            ).allowed,
        )
        assertFalse(
            RideRoadReportAvailabilityPolicy.evaluate(
                isDriver = true,
                localStatus = "IN_PROGRESS",
                serverState = "IN_PROGRESS",
                serverVersion = 9L,
                hasCurrentGps = false,
            ).allowed,
        )
    }
}
