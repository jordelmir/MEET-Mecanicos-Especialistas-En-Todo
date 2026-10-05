package com.elysium369.meet.safety.science.provenance

import org.junit.Assert.*
import org.junit.Test

/**
 * BLOQUE 2 — Custody V2 parity tests.
 * These exact same values MUST be reproduced in TypeScript and PostgreSQL.
 */
class CustodyProtocolV2Test {

    // Canonical fixture values
    private val eventId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    private val eventType = "EVIDENCE_CAPTURED"
    private val actorId = "11111111-2222-3333-4444-555555555555"
    private val timestampUtc = "2026-01-15T08:30:00Z"
    private val payloadHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    private val previousHash = "GENESIS"

    @Test
    fun `protocol version is SAFETY-CUSTODY-V2`() {
        assertEquals("SAFETY-CUSTODY-V2", CustodyProtocolV2.PROTOCOL_VERSION)
    }

    @Test
    fun `event hash is deterministic`() {
        val hash1 = CustodyProtocolV2.computeEventHash(
            eventId, eventType, actorId, timestampUtc, payloadHash, previousHash,
        )
        val hash2 = CustodyProtocolV2.computeEventHash(
            eventId, eventType, actorId, timestampUtc, payloadHash, previousHash,
        )
        assertEquals("Deterministic: same inputs = same hash", hash1, hash2)
    }

    @Test
    fun `event hash is 64 hex chars`() {
        val hash = CustodyProtocolV2.computeEventHash(
            eventId, eventType, actorId, timestampUtc, payloadHash, previousHash,
        )
        assertEquals(64, hash.length)
        assertTrue("Must be lowercase hex", hash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `event hash changes with different actor`() {
        val hash1 = CustodyProtocolV2.computeEventHash(
            eventId, eventType, actorId, timestampUtc, payloadHash, previousHash,
        )
        val hash2 = CustodyProtocolV2.computeEventHash(
            eventId, eventType,
            "22222222-3333-4444-5555-666666666666",
            timestampUtc, payloadHash, previousHash,
        )
        assertNotEquals("Different actor = different hash", hash1, hash2)
    }

    @Test
    fun `event hash changes with different timestamp`() {
        val hash1 = CustodyProtocolV2.computeEventHash(
            eventId, eventType, actorId, timestampUtc, payloadHash, previousHash,
        )
        val hash2 = CustodyProtocolV2.computeEventHash(
            eventId, eventType, actorId,
            "2026-01-15T08:31:00Z",
            payloadHash, previousHash,
        )
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `event hash changes with different payload`() {
        val hash1 = CustodyProtocolV2.computeEventHash(
            eventId, eventType, actorId, timestampUtc, payloadHash, previousHash,
        )
        val hash2 = CustodyProtocolV2.computeEventHash(
            eventId, eventType, actorId, timestampUtc,
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            previousHash,
        )
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `chain root is deterministic`() {
        val hashes = listOf(
            "abc123def456abc123def456abc123def456abc123def456abc123def456abc1",
            "def456abc123def456abc123def456abc123def456abc123def456abc123def4",
        )
        val root1 = CustodyProtocolV2.computeChainRoot(hashes)
        val root2 = CustodyProtocolV2.computeChainRoot(hashes)
        assertEquals(root1, root2)
    }

    @Test
    fun `chain root changes with order`() {
        val a = "abc123def456abc123def456abc123def456abc123def456abc123def456abc1"
        val b = "def456abc123def456abc123def456abc123def456abc123def456abc123def4"
        val root1 = CustodyProtocolV2.computeChainRoot(listOf(a, b))
        val root2 = CustodyProtocolV2.computeChainRoot(listOf(b, a))
        assertNotEquals("Order matters", root1, root2)
    }

    @Test
    fun `chain root is 64 hex chars`() {
        val root = CustodyProtocolV2.computeChainRoot(listOf(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        ))
        assertEquals(64, root.length)
        assertTrue(root.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `empty chain root is deterministic`() {
        val root = CustodyProtocolV2.computeChainRoot(emptyList())
        assertEquals(64, root.length)
    }

    @Test
    fun `payload hash of empty bytes is SHA-256 of empty`() {
        val hash = CustodyProtocolV2.computePayloadHash(ByteArray(0))
        // SHA-256 of empty input
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            hash,
        )
    }

    @Test
    fun `UUID casing is normalized to lowercase`() {
        val hashUpper = CustodyProtocolV2.computeEventHash(
            "A1B2C3D4-E5F6-7890-ABCD-EF1234567890",
            eventType, actorId, timestampUtc, payloadHash, previousHash,
        )
        val hashLower = CustodyProtocolV2.computeEventHash(
            "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
            eventType, actorId, timestampUtc, payloadHash, previousHash,
        )
        assertEquals("UUID casing must not affect hash", hashLower, hashUpper)
    }

    @Test
    fun `GENESIS is valid previous hash`() {
        val hash = CustodyProtocolV2.computeEventHash(
            eventId, eventType, actorId, timestampUtc, payloadHash, "GENESIS",
        )
        assertTrue(hash.isNotEmpty())
    }

    @Test
    fun `canonical fixture matches parity hash and chain root`() {
        val eventHash = CustodyProtocolV2.computeEventHash(
            eventId, eventType, actorId, timestampUtc, payloadHash, previousHash,
        )
        assertEquals(
            "f40ef568a767e9a98f79c7306c4365d4d5435fe101bb613a846b00f4a90dd62a",
            eventHash,
        )

        val chainRoot = CustodyProtocolV2.computeChainRoot(listOf(
            eventHash,
            "b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3",
        ))
        assertEquals(
            "c9d4ac3b00315e177f8387737b7ceb13b4b919d9397126e64b34d1c53c5b6ca4",
            chainRoot,
        )
    }
}
