package com.elysium369.meet.ride

import com.elysium369.meet.identity.OnboardingUsageProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingUsageProfileTest {
    @Test
    fun `ride passenger is a first class non provider profile`() {
        val profile = requireNotNull(OnboardingUsageProfile.fromStorageId("ride_passenger"))

        assertEquals(OnboardingUsageProfile.RIDE_PASSENGER, profile)
        assertEquals("Usuario de viajes", profile.displayLabel)
        assertEquals("ride_passenger", profile.platformRole)
        assertEquals("PASSENGER", profile.mobilityRole)
        assertFalse(profile.requiresProviderVerification)
    }

    @Test
    fun `ride driver requests provider verification without self approval`() {
        val profile = requireNotNull(OnboardingUsageProfile.fromStorageId("ride_driver"))

        assertEquals(OnboardingUsageProfile.RIDE_DRIVER, profile)
        assertEquals("Conductor", profile.displayLabel)
        assertEquals("ride_driver", profile.platformRole)
        assertEquals("DRIVER", profile.mobilityRole)
        assertTrue(profile.requiresProviderVerification)
    }

    @Test
    fun `legacy profiles remain supported and unknown values fail closed`() {
        assertEquals(OnboardingUsageProfile.OWNER, OnboardingUsageProfile.fromStorageId("owner"))
        assertEquals(OnboardingUsageProfile.MECHANIC, OnboardingUsageProfile.fromStorageId("mechanic"))
        assertEquals(OnboardingUsageProfile.WORKSHOP, OnboardingUsageProfile.fromStorageId("workshop"))
        assertEquals(OnboardingUsageProfile.FLEET, OnboardingUsageProfile.fromStorageId("fleet"))
        assertNull(OnboardingUsageProfile.fromStorageId("invented-role"))
    }
}
