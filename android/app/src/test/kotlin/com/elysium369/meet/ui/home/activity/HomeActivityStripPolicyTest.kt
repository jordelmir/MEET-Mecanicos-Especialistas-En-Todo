package com.elysium369.meet.ui.home.activity

import com.elysium369.meet.ui.navigation.MeetDestinations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeActivityStripPolicyTest {
    @Test
    fun `persisted active ride becomes a truthful actionable Home item`() {
        val activeRide = requireNotNull(
            HomeActivityStripPolicy.activeRide(
                rideId = "ride-123",
                vehicleName = null,
                stateName = "IN_PROGRESS",
            ),
        )

        val strip = HomeActivityStripPolicy.buildFromState(
            activeRides = listOf(activeRide),
            fuelAlerts = emptyList(),
            activeJourneys = emptyList(),
            activePttChannels = emptyList(),
            pendingMessages = 0,
            activeListings = 0,
            vehicleAlerts = emptyList(),
        )

        assertTrue(strip.hasActiveOperations)
        assertEquals("ride-123", activeRide.rideId)
        assertEquals("Conductor pendiente", strip.items.single().subtitle)
        assertEquals("IN_PROGRESS", strip.items.single().state)
        assertEquals(MeetDestinations.RIDE_ACTIVE_TRACKING, strip.items.single().actionRoute)
    }

    @Test
    fun `terminal and unknown ride states never appear active`() {
        listOf("COMPLETED", "CANCELLED", "EXPIRED", "UNKNOWN", "").forEach { state ->
            assertNull(
                HomeActivityStripPolicy.activeRide(
                    rideId = "ride-123",
                    vehicleName = "Vehículo",
                    stateName = state,
                ),
            )
        }
    }
}
