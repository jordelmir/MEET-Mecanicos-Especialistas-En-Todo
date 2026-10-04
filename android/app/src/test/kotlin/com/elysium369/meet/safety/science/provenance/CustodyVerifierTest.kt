package com.elysium369.meet.safety.science.provenance

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

/**
 * Adversarial integrity suite for the custody chain verifier.
 *
 * These tests are MANDATORY per AGENTS.md Safety rules —
 * if any of them fail, the scientific layer is not ready
 * for real investigations.
 */
class CustodyVerifierTest {

    private val verifier = CustodyVerifier()

    // ── Helpers ──────────────────────────────────────────────

    private fun buildChain(size: Int): List<CanonicalCustodyEvent> {
        val events = mutableListOf<CanonicalCustodyEvent>()
        var previousHash: String? = null

        repeat(size) { i ->
            val eventId = UUID.randomUUID()
            val evidenceId = UUID.randomUUID()
            val partial = CanonicalCustodyEvent(
                eventId = eventId,
                evidenceId = evidenceId,
                action = "IMPORTED",
                actorId = "user-$i",
                occurredAt = Instant.ofEpochMilli(1_000_000L + i),
                previousEventHash = previousHash,
                contentHash = sha256("content-$i"),
                eventHash = "", // placeholder
            )
            val hash = computeEventHash(partial)
            val complete = partial.copy(eventHash = hash)
            events += complete
            previousHash = hash
        }

        return events
    }

    // ── Valid chains ─────────────────────────────────────────

    @Test
    fun `empty chain is valid`() {
        val result = verifier.verify(emptyList())
        assertTrue(result is VerificationResult.Valid)
    }

    @Test
    fun `single event chain is valid`() {
        val chain = buildChain(1)
        assertTrue(verifier.verify(chain) is VerificationResult.Valid)
    }

    @Test
    fun `100 event chain is valid`() {
        val chain = buildChain(100)
        assertTrue(verifier.verify(chain) is VerificationResult.Valid)
    }

    // ── Tampering detection ─────────────────────────────────

    @Test
    fun `tampering with any custody event invalidates chain`() {
        val events = buildChain(100)

        val tampered = events.toMutableList().apply {
            this[47] = this[47].copy(action = "DELETED")
        }

        val result = verifier.verify(tampered)
        assertTrue(
            "Tampered event at index 47 must be detected",
            result is VerificationResult.Invalid,
        )
        val invalid = result as VerificationResult.Invalid
        assertEquals(events[47].eventId, invalid.failedEventId)
        assertEquals("EVENT_HASH_MISMATCH", invalid.reason)
    }

    @Test
    fun `tampering with first event invalidates chain`() {
        val events = buildChain(10)
        val tampered = events.toMutableList().apply {
            this[0] = this[0].copy(contentHash = sha256("forged"))
        }

        val result = verifier.verify(tampered)
        assertTrue(result is VerificationResult.Invalid)
        assertEquals("EVENT_HASH_MISMATCH", (result as VerificationResult.Invalid).reason)
    }

    @Test
    fun `tampering with last event invalidates chain`() {
        val events = buildChain(10)
        val tampered = events.toMutableList().apply {
            val last = this.last()
            this[this.lastIndex] = last.copy(actorId = "impersonator")
        }

        val result = verifier.verify(tampered)
        assertTrue(result is VerificationResult.Invalid)
    }

    @Test
    fun `removing middle event breaks chain linkage`() {
        val events = buildChain(10)
        val broken = events.toMutableList().apply { removeAt(5) }

        val result = verifier.verify(broken)
        assertTrue(result is VerificationResult.Invalid)
        assertEquals("PREVIOUS_HASH_MISMATCH", (result as VerificationResult.Invalid).reason)
    }

    @Test
    fun `swapping two events breaks chain`() {
        val events = buildChain(10)
        val swapped = events.toMutableList().apply {
            val tmp = this[3]
            this[3] = this[4]
            this[4] = tmp
        }

        val result = verifier.verify(swapped)
        assertTrue(result is VerificationResult.Invalid)
    }

    @Test
    fun `changing single byte in actorId is detected`() {
        val events = buildChain(5)
        val tampered = events.toMutableList().apply {
            this[2] = this[2].copy(actorId = this[2].actorId + "x")
        }

        val result = verifier.verify(tampered)
        assertTrue(result is VerificationResult.Invalid)
    }
}
