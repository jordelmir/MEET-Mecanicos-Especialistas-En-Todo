package com.elysium369.meet.ride.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideDriverPresencePolicyTest {
    private val zone = ZoneId.of("America/Costa_Rica")

    @Test
    fun `challenge is required on first drive each day and after twelve hours`() {
        val now = ZonedDateTime.of(2026, 8, 1, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertTrue(RideDriverPresencePolicy.requiresChallenge(null, now, zone))
        assertFalse(RideDriverPresencePolicy.requiresChallenge(now - 60_000L, now, zone))
        assertTrue(RideDriverPresencePolicy.requiresChallenge(now - 12 * 60 * 60 * 1000L, now, zone))
        assertTrue(RideDriverPresencePolicy.requiresChallenge(now - 9 * 60 * 60 * 1000L, now, zone))
    }
}
