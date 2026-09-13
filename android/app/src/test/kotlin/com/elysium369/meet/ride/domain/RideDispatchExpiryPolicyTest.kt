package com.elysium369.meet.ride.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideDispatchExpiryPolicyTest {
    @Test
    fun `request leaves driver list exactly at thirty minutes`() {
        val createdAt = 1_000L
        assertTrue(RideDispatchExpiryPolicy.remainsVisible(createdAt, createdAt + RideDispatchExpiryPolicy.LEASE_MILLIS - 1L))
        assertFalse(RideDispatchExpiryPolicy.remainsVisible(createdAt, createdAt + RideDispatchExpiryPolicy.LEASE_MILLIS))
    }

    @Test
    fun `recommended retry raises open bid with currency policy`() {
        assertEquals(2_700L, RideDispatchExpiryPolicy.recommendedOpenBidMinor(2_400L, "CRC"))
        assertEquals(600L, RideDispatchExpiryPolicy.recommendedOpenBidMinor(500L, "USD"))
    }
}
