package com.elysium369.meet.ride.driver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverTripPhaseTest {

    @Test
    fun `canonical server states map accurately to presentation phases`() {
        assertEquals(DriverTripPhase.Assigned, "ASSIGNED".toDriverTripPhase())
        assertEquals(DriverTripPhase.ToPickup, "DRIVER_EN_ROUTE".toDriverTripPhase())
        assertEquals(DriverTripPhase.AtPickup, "ARRIVED".toDriverTripPhase())
        assertEquals(DriverTripPhase.PassengerOnboard, "PASSENGER_ONBOARD".toDriverTripPhase())
        assertEquals(DriverTripPhase.InProgress, "IN_PROGRESS".toDriverTripPhase())
        assertEquals(DriverTripPhase.Completed, "COMPLETED".toDriverTripPhase())
        assertEquals(DriverTripPhase.Cancelled, "CANCELLED".toDriverTripPhase())
    }

    @Test
    fun `unknown or unhandled state produces unsupported phase with server state name`() {
        val result = "UNKNOWN_CUSTOM_STATE".toDriverTripPhase()
        assertTrue(result is DriverTripPhase.Unsupported)
        assertEquals("UNKNOWN_CUSTOM_STATE", (result as DriverTripPhase.Unsupported).serverState)
    }
}
