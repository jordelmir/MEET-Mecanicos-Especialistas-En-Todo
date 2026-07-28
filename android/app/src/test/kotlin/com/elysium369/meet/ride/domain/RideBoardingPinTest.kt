package com.elysium369.meet.ride.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RideBoardingPinTest {
    @Test
    fun `pin verifies once and cannot be replayed`() {
        val now = 50_000L
        val (pin, challenge) = RideBoardingPinPolicy.issue(now)

        val verified = RideBoardingPinPolicy.verify(challenge, pin, now + 1)
        val replay = RideBoardingPinPolicy.verify(verified.challenge, pin, now + 2)

        assertEquals(RidePinVerificationStatus.VERIFIED, verified.status)
        assertEquals(RidePinVerificationStatus.EXPIRED_OR_USED, replay.status)
        assertNotEquals(pin.toByteArray().toList(), challenge.pinHash.toList())
    }

    @Test
    fun `five invalid attempts lock challenge`() {
        val now = 100_000L
        val (pin, issued) = RideBoardingPinPolicy.issue(now)
        var challenge = issued
        val wrong = if (pin == "9999") "0000" else "9999"
        repeat(5) {
            challenge = RideBoardingPinPolicy.verify(challenge, wrong, now + it).challenge
        }

        val result = RideBoardingPinPolicy.verify(challenge, "0000", now + 10)

        assertEquals(RidePinVerificationStatus.LOCKED, result.status)
    }
}
