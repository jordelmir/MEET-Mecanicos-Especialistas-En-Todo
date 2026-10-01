package com.elysium369.meet.core.mesh

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

/** Pure protocol/resource tests, not radio, E2EE, physical delivery or protocol-review evidence. */
class MeshProtocolTest {
    private val now = 1_800_000_000_000L
    private fun envelope() = MeshEnvelope("message_1", "origin_1", "recipient_1", now, now + 60_000, 5, 1, byteArrayOf(1,2,3), ByteArray(16) { 7 })
    @Test fun boundedRoundTripPreservesAuthenticatedFields() {
        val original = envelope()
        val decoded = MeshWire.decode(MeshWire.encode(original))
        assertEquals(original.messageId, decoded.messageId)
        assertArrayEquals(original.associatedData(), decoded.associatedData())
        assertArrayEquals(original.ciphertext, decoded.ciphertext)
    }
    @Test fun trailingBytesAndUnknownVersionRejected() {
        rejected { MeshWire.decode(MeshWire.encode(envelope()) + byteArrayOf(0)) }
        rejected { MeshWire.decode(ByteArray(20)) }
    }
    @Test fun hostileLengthCannotAllocateLargePayload() {
        val wire = MeshWire.encode(envelope())
        val position = envelope().associatedData().size + 4
        ByteBuffer.wrap(wire).putInt(position, Int.MAX_VALUE)
        rejected { MeshWire.decode(wire) }
        rejected { MeshPacketWire.decode(ByteBuffer.allocate(9).putInt(0x45565031).put(1).putInt(Int.MAX_VALUE).array()) }
    }
    @Test fun expiryFutureSkewAndMalformedLifetimeReject() {
        val limits = MeshLimits()
        assertEquals(MeshIntake.EXPIRED, limits.rejection(envelope().copy(createdAt = now - 60_000, expiresAt = now), now))
        assertEquals(MeshIntake.EXPIRED, limits.rejection(envelope().copy(createdAt = now + 300_001, expiresAt = now + 360_001), now))
        assertEquals(MeshIntake.MALFORMED, limits.rejection(envelope().copy(expiresAt = now + 86_400_001), now))
        assertNull(limits.rejection(envelope(), now))
    }
    @Test fun attackerCannotRequestUnboundedStorageOrHops() {
        val limits = MeshLimits()
        assertEquals(MeshIntake.MALFORMED, limits.rejection(envelope().copy(maxHops = Int.MAX_VALUE), now))
        assertEquals(MeshIntake.MALFORMED, limits.rejection(envelope().copy(ciphertext = ByteArray(65537)), now))
        assertEquals(MeshIntake.MALFORMED, limits.rejection(envelope().copy(attachmentBytes = Int.MAX_VALUE), now))
        assertEquals(MeshIntake.MALFORMED, limits.rejection(envelope().copy(attachmentBytes = 4, attachmentDigest = "unproven"), now))
    }
    @Test fun replayIdentityBoundToOriginAndImmutableMessage() {
        val e = envelope()
        assertEquals(e.replayId(), e.copy(hops = 2).replayId())
        assertNotEquals(e.replayId(), e.copy(originKeyId = "another").replayId())
        assertNotEquals(e.replayId(), e.copy(messageId = "other").replayId())
    }
    @Test fun mutationOfAuthenticatedMetadataChangesAad() {
        val e = envelope()
        assertArrayEquals(e.associatedData(), e.copy(hops = 2).associatedData())
        assertFalse(e.associatedData().contentEquals(e.copy(recipientKeyId = "other").associatedData()))
        assertFalse(e.associatedData().contentEquals(e.copy(priority = 2).associatedData()))
    }
    @Test fun unavailableProviderFailsClosedForAllOperations() = runBlocking {
        val unavailable = UnavailableMeshCrypto()
        assertFalse(unavailable.independentlyReviewed)
        rejectedSuspend { unavailable.encrypt("recipient", byteArrayOf(1), byteArrayOf(2)) }
        rejectedSuspend { unavailable.decrypt(envelope()) }
        rejectedSuspend { unavailable.authenticate(envelope()) }
        rejectedSuspend { unavailable.verifyReceipt(MeshCustodyReceipt("id", "peer", "0".repeat(64), ByteArray(16))) }
    }
    @Test fun packetsRoundTripAndBoundChunks() {
        val c = MeshPacket.Chunk("id", 2, ByteArray(65536) { 3 })
        val decoded = MeshPacketWire.decode(MeshPacketWire.encode(c)) as MeshPacket.Chunk
        assertEquals(2, decoded.index); assertArrayEquals(c.bytes, decoded.bytes)
        rejected { MeshPacketWire.encode(c.copy(index = Int.MAX_VALUE)) }
        rejected { MeshPacketWire.encode(c.copy(bytes = ByteArray(65537))) }
    }
    @Test fun relayConsentAndBatteryCannotBeBypassedByPriority() {
        assertFalse(MeshRelayPolicy().allowed(100, true))
        val opted = MeshRelayPolicy(optedIn = true)
        assertFalse(opted.allowed(100, false)); assertFalse(opted.allowed(10, true)); assertTrue(opted.allowed(100, true))
    }
    private fun rejected(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) { } catch (_: IllegalStateException) { } }
    private suspend fun rejectedSuspend(block: suspend () -> Unit) { try { block(); fail("Expected fail-closed provider") } catch (e: IllegalStateException) { assertEquals("MESH_CRYPTO_PROVIDER_NOT_VERIFIED", e.message) } }
}
