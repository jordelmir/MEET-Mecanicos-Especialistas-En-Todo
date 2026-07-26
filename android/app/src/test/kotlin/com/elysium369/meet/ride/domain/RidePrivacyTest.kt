package com.elysium369.meet.ride.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RidePrivacyTest {

    @Test
    fun `mechanical sharing categories are disabled by default`() {
        val defaults = RideConsentPolicy.mechanicalDefaults(
            tripId = "trip-1",
            driverId = "driver-1",
            expiresAtEpochMs = 20_000,
        )

        assertEquals(RideConsentPolicy.mechanicalCategories.size, defaults.size)
        assertTrue(defaults.all { !it.canShare(nowEpochMs = 10_000, tripIsActive = true) })
    }

    @Test
    fun `consent works only during its explicit active window`() {
        val consent = RideShareConsent(
            tripId = "trip-1",
            driverId = "driver-1",
            category = RideShareCategory.BASIC_TELEMETRY,
            grantedAtEpochMs = 1_000,
            expiresAtEpochMs = 2_000,
        )

        assertFalse(consent.canShare(nowEpochMs = 999, tripIsActive = true))
        assertTrue(consent.canShare(nowEpochMs = 1_500, tripIsActive = true))
        assertFalse(consent.canShare(nowEpochMs = 2_001, tripIsActive = true))
        assertFalse(consent.canShare(nowEpochMs = 1_500, tripIsActive = false))
    }

    @Test
    fun `revocation immediately denies mechanical sharing`() {
        val consent = RideShareConsent(
            tripId = "trip-1",
            driverId = "driver-1",
            category = RideShareCategory.DTC_HISTORY,
            grantedAtEpochMs = 1_000,
            expiresAtEpochMs = 5_000,
            revokedAtEpochMs = 1_500,
        )

        assertFalse(consent.canShare(nowEpochMs = 1_499, tripIsActive = true))
        assertFalse(consent.canShare(nowEpochMs = 2_000, tripIsActive = true))
    }

    @Test
    fun `old obd sample is labeled stale instead of live`() {
        val sample = RideMechanicalSample(
            key = "COOLANT_TEMP",
            displayValue = "91 °C",
            source = RideMechanicalSource.REAL_OBD,
            capturedAtEpochMs = 1_000,
        )

        assertEquals(
            RideSampleFreshness.FRESH,
            sample.freshness(nowEpochMs = 5_000, maxAgeMs = 5_000),
        )
        assertEquals(
            RideSampleFreshness.STALE,
            sample.freshness(nowEpochMs = 6_001, maxAgeMs = 5_000),
        )
    }
}
