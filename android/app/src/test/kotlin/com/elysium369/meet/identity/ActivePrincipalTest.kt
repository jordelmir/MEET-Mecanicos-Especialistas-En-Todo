package com.elysium369.meet.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivePrincipalTest {
    @Test
    fun authenticatedPrincipalUsesServerIdentityAndCanSync() {
        val principal = ActivePrincipal.authenticated("user-a")

        assertEquals("user-a", principal.id)
        assertTrue(principal.isAuthenticated)
        assertTrue(principal.canSyncToCloud)
    }

    @Test
    fun localPrincipalIsStableAndCannotBeSilentlyAdopted() {
        val principal = ActivePrincipal.local("device-a")

        assertEquals("local_device_device-a", principal.id)
        assertFalse(principal.isAuthenticated)
        assertFalse(principal.canSyncToCloud)
        assertFalse(ActivePrincipal.legacyUnknown().canSyncToCloud)
    }

    @Test
    fun restoredOfflinePrincipalKeepsOwnershipButCannotSync() {
        val principal = ActivePrincipal.restoredOffline("user-a")

        assertEquals("user-a", principal.id)
        assertFalse(principal.isAuthenticated)
        assertFalse(principal.canSyncToCloud)
    }

    @Test
    fun onlyExactAuthenticatedOwnerCanSyncRow() {
        val active = ActivePrincipal.authenticated("user-b")

        assertTrue(OfflineOwnership.canSync("user-b", active))
        assertFalse(OfflineOwnership.canSync("user-a", active))
        assertFalse(OfflineOwnership.canSync(OfflineOwnership.OWNER_UNKNOWN_LEGACY, active))
        assertFalse(OfflineOwnership.canSync("local_device_device-a", active))
    }
}
