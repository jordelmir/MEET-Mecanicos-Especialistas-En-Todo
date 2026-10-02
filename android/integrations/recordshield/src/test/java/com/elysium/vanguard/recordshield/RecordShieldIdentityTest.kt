package com.elysium.vanguard.recordshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordShieldIdentityTest {
    @Test fun storageNamesArePrincipalScopedAndDoNotExposeIds() {
        RecordShieldIdentity.install { "account-a" }
        val a = RecordShieldIdentity.storageScope()
        RecordShieldIdentity.install { "account-b" }
        val b = RecordShieldIdentity.storageScope()
        assertNotEquals(a, b)
        assertFalse(a.contains("account"))
        assertFalse(b.contains("account"))
    }

    @Test fun anonymousStorageProjected() {
        RecordShieldIdentity.install { null }
        val scope = RecordShieldIdentity.storageScope()
        assertTrue("scope must be non-empty hex", scope.isNotEmpty())
        assertFalse("scope must not leak fallback string", scope.contains("local"))
        assertFalse("scope must not leak fallback string", scope.contains("sovereign"))
        // Deterministic: same fallback yields same scope
        val again = RecordShieldIdentity.storageScope()
        assertEquals(scope, again)
    }
}
