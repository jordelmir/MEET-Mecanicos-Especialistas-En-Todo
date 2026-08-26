package com.elysium369.meet.ride.reputation

import org.junit.Assert.*
import org.junit.Test

class DriverTrustTierTest {

    @Test
    fun from_id_maps_correctly_case_insensitively() {
        assertEquals(DriverTrustTier.VANGUARD, DriverTrustTier.fromId("vanguard"))
        assertEquals(DriverTrustTier.ELITE, DriverTrustTier.fromId("ELITE"))
        assertEquals(DriverTrustTier.TRUSTED, DriverTrustTier.fromId("Trusted"))
        assertEquals(DriverTrustTier.VERIFIED, DriverTrustTier.fromId("VERIFIED"))
        assertEquals(DriverTrustTier.VERIFIED, DriverTrustTier.fromId(null))
        assertEquals(DriverTrustTier.VERIFIED, DriverTrustTier.fromId("UNKNOWN_TIER"))
    }

    @Test
    fun driver_public_profile_formats_rating_summary_truthfully() {
        val profileWithTrips = DriverPublicProfile(
            driverId = "d1",
            displayName = "Andrés",
            totalTrips = 2952,
            bayesianRating = 4.98,
            trustTierRaw = "VANGUARD"
        )
        assertEquals("4.98 ★ · 2,952 viajes", profileWithTrips.formattedRatingSummary)

        val newDriver = DriverPublicProfile(
            driverId = "d2",
            displayName = "Carlos",
            totalTrips = 0,
            bayesianRating = null,
            trustTierRaw = "VERIFIED"
        )
        assertEquals("Conductor Verificado", newDriver.formattedRatingSummary)
    }
}
