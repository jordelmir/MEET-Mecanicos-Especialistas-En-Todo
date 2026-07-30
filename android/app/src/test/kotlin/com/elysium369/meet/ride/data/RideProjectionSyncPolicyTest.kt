package com.elysium369.meet.ride.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RideProjectionSyncPolicyTest {
    @Test
    fun `reconnect delay grows exponentially and remains bounded`() {
        assertEquals(1_000L, RideProjectionSyncPolicy.reconnectDelayMs(0))
        assertEquals(2_000L, RideProjectionSyncPolicy.reconnectDelayMs(1))
        assertEquals(16_000L, RideProjectionSyncPolicy.reconnectDelayMs(4))
        assertEquals(30_000L, RideProjectionSyncPolicy.reconnectDelayMs(5))
        assertEquals(30_000L, RideProjectionSyncPolicy.reconnectDelayMs(100))
    }
}
