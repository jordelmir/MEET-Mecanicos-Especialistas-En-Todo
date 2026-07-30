package com.elysium369.meet.ride.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideSupportPolicyTest {
    @Test
    fun `support summary has bounded useful content`() {
        assertFalse(RideSupportPolicy.isValidSummary("corto"))
        assertTrue(RideSupportPolicy.isValidSummary("Cobro no coincide"))
        assertTrue(RideSupportPolicy.isValidSummary("x".repeat(1_000)))
        assertFalse(RideSupportPolicy.isValidSummary("x".repeat(1_001)))
    }
}
