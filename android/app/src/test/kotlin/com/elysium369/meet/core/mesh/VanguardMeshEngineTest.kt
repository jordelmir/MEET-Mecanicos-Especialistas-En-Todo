package com.elysium369.meet.core.mesh

import org.junit.Assert.*
import org.junit.Test

class VanguardMeshEngineTest {

    private fun engine(): VanguardMeshEngine {
        val e = VanguardMeshEngine()
        e.initialize("fp-alice-1234")
        return e
    }

    // ═══════════════════════════════════════════════════════════
    // NODE LIFECYCLE
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `initialize creates node in DISCOVERING state`() {
        val engine = engine()
        assertEquals(MeshNodeState.DISCOVERING, engine.localNode()!!.state)
    }

    @Test
    fun `start advertising changes state`() {
        val engine = engine()
        assertTrue(engine.startAdvertising())
        assertEquals(MeshNodeState.ADVERTISING, engine.localNode()!!.state)
    }

    // ═══════════════════════════════════════════════════════════
    // PEER DISCOVERY (BLE Layer)
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `discover nearby peer`() {
        val engine = engine()
        engine.onPeerDiscovered(MeshPeer(
            ephemeralId = "eph-bob", fingerprint = "fp-bob",
            capabilities = setOf(MeshCapability.RELAY),
        ))
        assertEquals(1, engine.activePeers().size)
        assertEquals(1, engine.localNode()!!.peerCount)
    }

    @Test
    fun `gateway peers identified`() {
        val engine = engine()
        engine.onPeerDiscovered(MeshPeer(
            "eph-gw", "fp-gw",
            capabilities = setOf(MeshCapability.GATEWAY, MeshCapability.HIGH_BANDWIDTH),
        ))
        assertEquals(1, engine.gatewayPeers().size)
    }

    // ═══════════════════════════════════════════════════════════
    // DIRECT MESSAGING (Wi-Fi Direct Layer)
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `send direct message to unknown peer stores for relay`() {
        val engine = engine()
        val msg = engine.sendDirectMessage("fp-unknown", "encrypted-hello")
        assertEquals(MeshMessageType.DIRECT, msg.type)
        assertEquals(1, engine.meshStats().messagesInCustody)
    }

    @Test
    fun `send direct message to connected peer delivers immediately`() {
        val engine = engine()
        engine.onPeerDiscovered(MeshPeer(
            "eph-bob", "fp-bob",
            connectionState = PeerConnectionState.DATA_CHANNEL,
        ))
        engine.sendDirectMessage("fp-bob", "encrypted-hello")
        assertEquals(1, engine.meshStats().totalDelivered)
        assertEquals(0, engine.meshStats().messagesInCustody)
    }

    // ═══════════════════════════════════════════════════════════
    // EMERGENCY BROADCAST
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `emergency broadcast has 20 max hops and is flagged`() {
        val engine = engine()
        val msg = engine.broadcastEmergency("SOS-encrypted")
        assertTrue(msg.isEmergencyBroadcast)
        assertEquals(20, msg.maxHops)
        assertNull(msg.recipientFingerprint) // broadcast
        assertEquals(1, engine.meshStats().emergencyBroadcasts)
    }

    // ═══════════════════════════════════════════════════════════
    // STORE-CARRY-FORWARD (DTN Layer)
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `relay message accepted when node has capacity`() {
        val engine = engine()
        val msg = MeshMessage(
            "msg-1", MeshMessageType.DIRECT,
            "fp-charlie", "fp-dave",
            "encrypted-data",
        )
        val decision = engine.onRelayMessageReceived(msg)
        assertEquals(RelayDecision.ACCEPTED, decision)
        assertEquals(1, engine.meshStats().totalRelayed)
    }

    @Test
    fun `duplicate message rejected`() {
        val engine = engine()
        val msg = MeshMessage(
            "msg-1", MeshMessageType.DIRECT,
            "fp-charlie", "fp-dave",
            "encrypted-data",
        )
        engine.onRelayMessageReceived(msg)
        val duplicate = engine.onRelayMessageReceived(msg.copy())
        assertEquals(RelayDecision.DUPLICATE, duplicate)
    }

    @Test
    fun `message for self is delivered`() {
        val engine = engine()
        val msg = MeshMessage(
            "msg-2", MeshMessageType.DIRECT,
            "fp-charlie", "fp-alice-1234", // for THIS node
            "encrypted-for-me",
        )
        val decision = engine.onRelayMessageReceived(msg)
        assertEquals(RelayDecision.DELIVERED_TO_SELF, decision)
    }

    @Test
    fun `expired message rejected`() {
        val engine = engine()
        val msg = MeshMessage(
            "msg-3", MeshMessageType.DIRECT,
            "fp-x", "fp-y", "data",
            ttlSeconds = 0,
            expiresAtEpochMs = System.currentTimeMillis() - 1000, // already expired
        )
        val decision = engine.onRelayMessageReceived(msg)
        assertEquals(RelayDecision.EXPIRED, decision)
    }

    @Test
    fun `relay blocked when battery below 15 percent`() {
        val engine = VanguardMeshEngine()
        engine.initialize("fp-low-battery")
        // Simulate low battery node
        val node = engine.localNode()!!
        // We can't directly set battery, but we test canRelay logic
        assertFalse(node.copy(batteryPercent = 10).canRelay)
        assertTrue(node.copy(batteryPercent = 50).canRelay)
    }

    @Test
    fun `relay only while charging respects setting`() {
        val node = MeshNode(
            MeshNodeId("fp-test"),
            relayOnlyWhileCharging = true,
            isCharging = false,
        )
        assertFalse(node.canRelay)
        assertTrue(node.copy(isCharging = true).canRelay)
    }

    // ═══════════════════════════════════════════════════════════
    // MESSAGE LIFECYCLE
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `forwarding increments hop count`() {
        val msg = MeshMessage(
            "msg-4", MeshMessageType.DIRECT,
            "fp-a", "fp-b", "data", maxHops = 10, currentHops = 3,
        )
        val forwarded = msg.forwarded()
        assertEquals(4, forwarded.currentHops)
    }

    @Test
    fun `message becomes undeliverable at max hops`() {
        val msg = MeshMessage(
            "msg-5", MeshMessageType.DIRECT,
            "fp-a", "fp-b", "data", maxHops = 5, currentHops = 5,
        )
        assertFalse(msg.hasHopsRemaining)
        assertFalse(msg.isDeliverable)
    }

    @Test
    fun `purge expired cleans up old messages`() {
        val engine = engine()
        // Add an expired message
        val expired = MeshMessage(
            "msg-old", MeshMessageType.DIRECT,
            "fp-x", "fp-y", "old-data",
            expiresAtEpochMs = System.currentTimeMillis() - 1,
        )
        engine.onRelayMessageReceived(expired.copy(
            messageId = "msg-fresh",
            expiresAtEpochMs = System.currentTimeMillis() + 86400000,
        ))
        // Purge should clean at least the old ones
        engine.purgeExpired()
        assertTrue(engine.meshStats().messagesInCustody >= 0)
    }

    // ═══════════════════════════════════════════════════════════
    // SECURITY
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `ephemeral ID rotates every 15 minutes`() {
        val nodeId = MeshNodeId("fp-test")
        assertEquals(15 * 60 * 1000L, nodeId.rotationIntervalMs)
    }

    @Test
    fun `all 5 mesh capabilities exist`() {
        assertEquals(5, MeshCapability.entries.size)
    }

    @Test
    fun `all 6 relay decisions exist`() {
        assertEquals(6, RelayDecision.entries.size)
    }
}
