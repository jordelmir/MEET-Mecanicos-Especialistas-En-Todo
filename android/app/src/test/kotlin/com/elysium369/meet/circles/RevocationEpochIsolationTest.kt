package com.elysium369.meet.circles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RevocationEpochIsolationTest {

    private lateinit var epochManager: CircleAuthorizationEpochManager

    @Before
    fun setup() {
        epochManager = CircleAuthorizationEpochManager()
    }

    @Test
    fun `revocation rotates accessEpoch and invalidates cached topic for revoked member`() {
        val circleId = "circle-alpha"
        val alice = "user-alice"
        val mallory = "user-mallory"

        epochManager.registerCircle(circleId, initialEpoch = 1L, initialMembers = setOf(alice, mallory))

        val initialTopic = epochManager.currentBroadcastTopic(circleId)
        assertEquals("circle:circle-alpha:epoch:1", initialTopic)

        // Both initially authorized for epoch 1
        assertTrue(epochManager.isAuthorizedForTopic(alice, initialTopic))
        assertTrue(epochManager.isAuthorizedForTopic(mallory, initialTopic))

        // Alice revokes Mallory -> accessEpoch increments to 2
        val newTopic = epochManager.revokeMemberAndRotateEpoch(circleId, mallory)
        assertEquals("circle:circle-alpha:epoch:2", newTopic)

        // 1. Mallory's cached WebSocket topic (epoch 1) is now STALE and receives 0 new events
        assertFalse(epochManager.isAuthorizedForTopic(mallory, "circle:circle-alpha:epoch:1"))
        assertFalse(epochManager.isAuthorizedForTopic(alice, "circle:circle-alpha:epoch:1"))

        // 2. Alice is authorized for the new topic (epoch 2)
        assertTrue(epochManager.isAuthorizedForTopic(alice, newTopic))

        // 3. Mallory CANNOT join the new topic (epoch 2) because membership was severed
        assertFalse(epochManager.isAuthorizedForTopic(mallory, newTopic))
    }

    @Test
    fun `blocking principal immediately rotates epochs on all shared circles and evicts blocked user`() {
        val circleA = "circle-family"
        val circleB = "circle-roadtrip"
        val alice = "user-alice"
        val bob = "user-bob"

        epochManager.registerCircle(circleA, initialEpoch = 1L, initialMembers = setOf(alice, bob))
        epochManager.registerCircle(circleB, initialEpoch = 5L, initialMembers = setOf(alice, bob))

        // Alice records a block against Bob
        val rotatedTopics = epochManager.recordBlock(blockerPrincipalId = alice, blockedPrincipalId = bob)

        assertEquals(2, rotatedTopics.size)
        assertTrue(rotatedTopics.contains("circle:circle-family:epoch:2"))
        assertTrue(rotatedTopics.contains("circle:circle-roadtrip:epoch:6"))

        // Bob cannot access any rotated topic
        assertFalse(epochManager.isAuthorizedForTopic(bob, "circle:circle-family:epoch:2"))
        assertFalse(epochManager.isAuthorizedForTopic(bob, "circle:circle-roadtrip:epoch:6"))

        // Alice retains access to the new topics
        assertTrue(epochManager.isAuthorizedForTopic(alice, "circle:circle-family:epoch:2"))
        assertTrue(epochManager.isAuthorizedForTopic(alice, "circle:circle-roadtrip:epoch:6"))
    }
}
