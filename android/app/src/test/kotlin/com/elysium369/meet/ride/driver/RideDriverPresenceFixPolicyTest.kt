package com.elysium369.meet.ride.driver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideDriverPresenceFixPolicyTest {
    @Test fun freshAccurateFixCanBePublished() {
        assertTrue(RideDriverPresenceFixPolicy.mayPublish(9.93, -84.08, 12f, 90_000L, 100_000L))
    }

    @Test fun staleFutureAndImpreciseFixesCannotRefreshPresence() {
        assertFalse(RideDriverPresenceFixPolicy.mayPublish(9.93, -84.08, 12f, 39_999L, 100_000L))
        assertFalse(RideDriverPresenceFixPolicy.mayPublish(9.93, -84.08, 12f, 100_001L, 100_000L))
        assertFalse(RideDriverPresenceFixPolicy.mayPublish(9.93, -84.08, 101f, 99_000L, 100_000L))
        assertFalse(RideDriverPresenceFixPolicy.mayPublish(Double.NaN, -84.08, 12f, 99_000L, 100_000L))
    }
}
