package com.elysium369.meet.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrincipalAccessPolicyTest {
    @Test
    fun freshInstallCannotEnterHomeWithoutAccount() {
        assertEquals(
            PrincipalAccessPolicy.Decision.REQUIRE_AUTHENTICATION,
            PrincipalAccessPolicy.decide(
                PrincipalAccessPolicy.SessionEvidence.NOT_AUTHENTICATED,
                provisionedPrincipalId = null,
            ),
        )
    }

    @Test
    fun registeredDeviceCanRestoreOfflineIdentity() {
        assertEquals(
            PrincipalAccessPolicy.Decision.ENTER_PROVISIONED_OFFLINE,
            PrincipalAccessPolicy.decide(
                PrincipalAccessPolicy.SessionEvidence.NETWORK_UNAVAILABLE,
                provisionedPrincipalId = "principal-verified-before-offline",
            ),
        )
    }

    @Test
    fun explicitSignedOutStateDoesNotUseOfflineContinuity() {
        assertEquals(
            PrincipalAccessPolicy.Decision.REQUIRE_AUTHENTICATION,
            PrincipalAccessPolicy.decide(
                PrincipalAccessPolicy.SessionEvidence.NOT_AUTHENTICATED,
                provisionedPrincipalId = "old-principal",
            ),
        )
    }

    @Test
    fun anonymousPrincipalCannotBecomeProvider() {
        assertFalse(
            PrincipalCapabilityPolicy.grantsProviderAuthority(
                principalAuthenticated = false,
                principalStatus = PrincipalStatus.ACTIVE,
                activationState = CapabilityActivationState.APPROVED,
            ),
        )
        assertFalse(
            PrincipalCapabilityPolicy.grantsProviderAuthority(
                principalAuthenticated = true,
                principalStatus = PrincipalStatus.ACTIVE,
                activationState = CapabilityActivationState.SUBMITTED,
            ),
        )
        assertTrue(
            PrincipalCapabilityPolicy.grantsProviderAuthority(
                principalAuthenticated = true,
                principalStatus = PrincipalStatus.ACTIVE,
                activationState = CapabilityActivationState.APPROVED,
            ),
        )
    }

    @Test
    fun clientCannotAssignPlatformOwner() {
        assertFalse(PlatformAuthorityPolicy.clientMayAssignAuthority())
    }

    @Test
    fun ownerSensitiveActionRequiresAal2() {
        val owner = setOf(PlatformAuthorityRole.PLATFORM_OWNER)
        assertFalse(PlatformAuthorityPolicy.sensitiveActionAllowed(owner, 1))
        assertTrue(PlatformAuthorityPolicy.sensitiveActionAllowed(owner, 2))
    }
}
