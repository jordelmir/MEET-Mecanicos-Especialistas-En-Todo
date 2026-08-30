package com.elysium369.meet.identity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainGraphRetentionPolicyTest {
    @Test
    fun `storage reload keeps an established graph alive`() {
        assertTrue(
            MainGraphRetentionPolicy.shouldRender(
                PrincipalAccessPolicy.Decision.RESOLVING,
                graphEstablished = true,
            ),
        )
    }

    @Test
    fun `fresh launch does not enter while session is unresolved`() {
        assertFalse(
            MainGraphRetentionPolicy.shouldRender(
                PrincipalAccessPolicy.Decision.RESOLVING,
                graphEstablished = false,
            ),
        )
    }

    @Test
    fun `explicit unauthenticated result discards the retained graph`() {
        assertFalse(
            MainGraphRetentionPolicy.nextEstablished(
                PrincipalAccessPolicy.Decision.REQUIRE_AUTHENTICATION,
                graphEstablished = true,
            ),
        )
    }
}
