package dragoncore.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DragonAccountScopeTest {
    @Test fun `different accounts cannot select the same local namespace`() {
        val first = DragonAccountScope.storageKeyFor("account-a")
        val second = DragonAccountScope.storageKeyFor("account-b")
        assertNotEquals(first, second)
        assertFalse(first.contains("account-a"))
        assertFalse(second.contains("account-b"))
    }

    @Test fun `scope is stable across process recreation`() {
        assertEquals(DragonAccountScope.storageKeyFor("account-a"), DragonAccountScope.storageKeyFor("account-a"))
        assertEquals("guest", DragonAccountScope.storageKeyFor(null))
    }
}
