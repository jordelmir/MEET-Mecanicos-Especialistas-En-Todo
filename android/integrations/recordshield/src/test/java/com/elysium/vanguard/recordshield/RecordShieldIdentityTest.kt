package com.elysium.vanguard.recordshield

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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

    @Test fun anonymousStorageIsRejected() {
        RecordShieldIdentity.install { null }
        assertThrows(IllegalStateException::class.java) { RecordShieldIdentity.storageScope() }
    }
}
