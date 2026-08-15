package com.elysium369.meet.ride.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformOwnerAccessPolicyTest {
    @Test
    fun `trust center is visible only after server grants platform owner access`() {
        assertFalse(PlatformOwnerAccessPolicy.canExposeTrustCenter(PlatformOwnerAccess.UNKNOWN))
        assertFalse(PlatformOwnerAccessPolicy.canExposeTrustCenter(PlatformOwnerAccess.SIGNED_OUT))
        assertFalse(PlatformOwnerAccessPolicy.canExposeTrustCenter(PlatformOwnerAccess.DENIED))
        assertFalse(PlatformOwnerAccessPolicy.canExposeTrustCenter(PlatformOwnerAccess.UNAVAILABLE))
        assertTrue(PlatformOwnerAccessPolicy.canExposeTrustCenter(PlatformOwnerAccess.GRANTED))
    }
}
