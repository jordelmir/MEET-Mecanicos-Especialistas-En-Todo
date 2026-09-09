package com.elysium369.meet.ride.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import com.elysium369.meet.ride.domain.RidePresenceChallenge.Phase

class RidePresenceChallengeTest {
    private fun face(eyes: Float = .95f, id: Int = 1) =
        RidePresenceChallenge.Observation(1, trackingId = id, leftEye = eyes, rightEye = eyes)

    @Test fun `presence cache is isolated per account and unavailable signed out`() {
        org.junit.Assert.assertNull(RideDriverPresencePolicy.storageKey(null))
        org.junit.Assert.assertNull(RideDriverPresencePolicy.storageKey(""))
        org.junit.Assert.assertNotEquals(RideDriverPresencePolicy.storageKey("alice"), RideDriverPresencePolicy.storageKey("bob"))
        assertEquals("last_verified_at:alice", RideDriverPresencePolicy.storageKey("alice"))
    }

    @Test fun `held close then reopen completes and later face loss does not discard evidence`() {
        val challenge = RidePresenceChallenge()
        assertEquals(Phase.OPEN, challenge.accept(face(), 0).phase)
        assertEquals(Phase.CLOSED, challenge.accept(face(.1f), 1000).phase)
        assertEquals(Phase.VERIFIED, challenge.accept(face(), 2000).phase)
        assertEquals(Phase.VERIFIED, challenge.accept(RidePresenceChallenge.Observation(0), 2100).phase)
        assertEquals(Phase.FIND_FACE, challenge.reset().phase)
    }

    @Test fun `open eyes alone or initially closed eyes do not pass`() {
        val challenge = RidePresenceChallenge()
        assertEquals(Phase.FIND_FACE, challenge.accept(face(.1f), 0).phase)
        assertEquals(Phase.OPEN, challenge.accept(face(), 100).phase)
        assertEquals(Phase.OPEN, challenge.accept(face(), 2000).phase)
    }

    @Test fun `changing tracked person cannot complete someone else's challenge`() {
        val challenge = RidePresenceChallenge()
        challenge.accept(face(), 0)
        challenge.accept(face(.1f), 1000)
        assertEquals(Phase.OPEN, challenge.accept(face(id = 2), 2000).phase)
    }

    @Test fun `face loss multiple faces missing classifications and pose reset incomplete challenge`() {
        for (bad in listOf(RidePresenceChallenge.Observation(0), RidePresenceChallenge.Observation(2),
            face().copy(leftEye = null), face().copy(yaw = 30f), face().copy(leftEye = Float.NaN))) {
            val challenge = RidePresenceChallenge()
            challenge.accept(face(), 0)
            challenge.accept(face(.1f), 1000)
            assertEquals(Phase.FIND_FACE, challenge.accept(bad, 1100).phase)
            assertEquals(Phase.OPEN, challenge.accept(face(), 2000).phase)
        }
    }

    @Test fun `expired challenge and implausibly short closure cannot complete`() {
        val challenge = RidePresenceChallenge()
        challenge.accept(face(), 0)
        challenge.accept(face(.1f), 1000)
        assertEquals(Phase.OPEN, challenge.accept(face(), 1100).phase)
        challenge.accept(face(.1f), 2000)
        assertEquals(Phase.OPEN, challenge.accept(face(), 16000).phase)
    }
}
