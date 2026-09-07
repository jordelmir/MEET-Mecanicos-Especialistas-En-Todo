package com.elysium369.meet.core.memory

import org.junit.Assert.*
import org.junit.Test

class ContradictionDetectorTest {

    private val detector = ContradictionDetector()

    private fun entry(
        id: String,
        key: String,
        value: String,
        confidence: MemoryConfidence = MemoryConfidence.MODERATE,
        provenance: MemoryProvenance = MemoryProvenance.USER_EXPLICIT,
    ) = MemoryEntry(
        id = id, namespace = "test", memoryClass = MemoryClass.IDENTITY,
        key = key, value = value, confidence = confidence, provenance = provenance,
    )

    @Test
    fun `detects contradiction when same key has different value`() {
        val existing = entry("e1", "home_city", "San José")
        val newer = entry("e2", "home_city", "Heredia")
        val contradictions = detector.detectContradictions(newer, listOf(existing))
        assertEquals(1, contradictions.size)
        assertEquals("e1", contradictions[0].existingEntryId)
    }

    @Test
    fun `no contradiction when same key same value`() {
        val existing = entry("e1", "home_city", "San José")
        val newer = entry("e2", "home_city", "San José")
        val contradictions = detector.detectContradictions(newer, listOf(existing))
        assertTrue(contradictions.isEmpty())
    }

    @Test
    fun `no contradiction when different keys`() {
        val existing = entry("e1", "home_city", "San José")
        val newer = entry("e2", "work_city", "Heredia")
        val contradictions = detector.detectContradictions(newer, listOf(existing))
        assertTrue(contradictions.isEmpty())
    }

    @Test
    fun `authoritative always wins over moderate`() {
        val existing = entry("e1", "k", "old", MemoryConfidence.AUTHORITATIVE)
        val newer = entry("e2", "k", "new", MemoryConfidence.MODERATE)
        val result = detector.autoResolve(
            MemoryContradiction("c1", "e1", "e2"), existing, newer,
        )
        assertEquals(ContradictionResolution.OLDER_WINS, result)
    }

    @Test
    fun `newer authoritative beats older moderate`() {
        val existing = entry("e1", "k", "old", MemoryConfidence.MODERATE)
        val newer = entry("e2", "k", "new", MemoryConfidence.AUTHORITATIVE)
        val result = detector.autoResolve(
            MemoryContradiction("c1", "e1", "e2"), existing, newer,
        )
        assertEquals(ContradictionResolution.NEWER_WINS, result)
    }

    @Test
    fun `user explicit beats ai inferred`() {
        val existing = entry("e1", "k", "old", provenance = MemoryProvenance.USER_EXPLICIT)
        val newer = entry("e2", "k", "new", provenance = MemoryProvenance.AI_INFERRED)
        val result = detector.autoResolve(
            MemoryContradiction("c1", "e1", "e2"), existing, newer,
        )
        assertEquals(ContradictionResolution.OLDER_WINS, result)
    }

    @Test
    fun `same confidence same provenance newer wins`() {
        val existing = entry("e1", "k", "old")
        val newer = entry("e2", "k", "new")
        val result = detector.autoResolve(
            MemoryContradiction("c1", "e1", "e2"), existing, newer,
        )
        assertEquals(ContradictionResolution.NEWER_WINS, result)
    }

    @Test
    fun `apply resolution newer wins merges evidence`() {
        val existing = entry("e1", "k", "old").copy(evidenceRefs = listOf("ref-a"))
        val newer = entry("e2", "k", "new").copy(evidenceRefs = listOf("ref-b"))
        val outcome = detector.applyResolution(
            ContradictionResolution.NEWER_WINS, existing, newer,
        )
        assertEquals("e1", outcome.supersededId)
        assertTrue(outcome.winner.evidenceRefs.containsAll(listOf("ref-a", "ref-b")))
    }

    @Test
    fun `apply resolution both valid keeps both`() {
        val existing = entry("e1", "k", "old")
        val newer = entry("e2", "k", "new")
        val outcome = detector.applyResolution(
            ContradictionResolution.BOTH_VALID_DIFFERENT_CONTEXT, existing, newer,
        )
        assertNull(outcome.supersededId) // both survive
    }
}
