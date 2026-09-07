package com.elysium369.meet.core.memory

import org.junit.Assert.*
import org.junit.Test

class DreamingEngineTest {

    // ─── Promotion Scoring ───

    @Test
    fun `high confidence authoritative entry scores highest relevance`() {
        val entry = makeEntry(confidence = MemoryConfidence.AUTHORITATIVE)
        val score = scoreSingle(entry)
        assertEquals(1.0, score.relevance, 0.001)
    }

    @Test
    fun `low confidence entry scores low relevance`() {
        val entry = makeEntry(confidence = MemoryConfidence.LOW)
        val score = scoreSingle(entry)
        assertEquals(0.2, score.relevance, 0.001)
    }

    @Test
    fun `entry with many evidence refs scores high frequency`() {
        val entry = makeEntry(evidenceRefs = listOf("e1", "e2", "e3", "e4", "e5"))
        val score = scoreSingle(entry)
        assertEquals(1.0, score.frequencyScore, 0.001)
    }

    @Test
    fun `entry with diverse tags scores high query diversity`() {
        val entry = makeEntry(tags = listOf("automotive", "plumbing", "electrical", "education"))
        val score = scoreSingle(entry)
        assertEquals(1.0, score.queryDiversity, 0.001)
    }

    @Test
    fun `recent entry scores high recency`() {
        val entry = makeEntry(updatedAtEpochMs = System.currentTimeMillis())
        val score = scoreSingle(entry)
        assertTrue("Recent entry should have recency > 0.9", score.recency > 0.9)
    }

    @Test
    fun `old entry scores low recency`() {
        val sixMonthsAgo = System.currentTimeMillis() - (180L * 24 * 60 * 60 * 1000)
        val entry = makeEntry(updatedAtEpochMs = sixMonthsAgo)
        val score = scoreSingle(entry)
        assertTrue("Old entry should have recency < 0.2", score.recency < 0.2)
    }

    @Test
    fun `versioned entry scores consolidation survival`() {
        val entry = makeEntry(version = 4)
        val score = scoreSingle(entry)
        assertEquals(1.0, score.consolidationSurvival, 0.001)
    }

    // ─── Promotion Threshold ───

    @Test
    fun `strong entry meets promotion threshold`() {
        val score = PromotionScore(
            relevance = 0.8,
            frequencyScore = 0.6,
            queryDiversity = 0.5,
            recency = 0.9,
            consolidationSurvival = 0.3,
            conceptualRichness = 0.4,
        )
        assertTrue(score.meetsPromotionThreshold)
        assertTrue(score.composite >= PromotionScore.PROMOTION_THRESHOLD)
    }

    @Test
    fun `weak entry does not meet promotion threshold`() {
        val score = PromotionScore(
            relevance = 0.1,
            frequencyScore = 0.0,
            queryDiversity = 0.0,
            recency = 0.1,
            consolidationSurvival = 0.0,
            conceptualRichness = 0.0,
        )
        assertFalse(score.meetsPromotionThreshold)
    }

    @Test
    fun `promotion threshold is 0_45`() {
        assertEquals(0.45, PromotionScore.PROMOTION_THRESHOLD, 0.001)
    }

    // ─── Composite Score Weights ───

    @Test
    fun `composite score weights sum to 1_0`() {
        // Verify: 0.25 + 0.20 + 0.15 + 0.15 + 0.10 + 0.15 = 1.0
        val perfect = PromotionScore(1.0, 1.0, 1.0, 1.0, 1.0, 1.0)
        assertEquals(1.0, perfect.composite, 0.001)
    }

    @Test
    fun `zero score entry has zero composite`() {
        val zero = PromotionScore()
        assertEquals(0.0, zero.composite, 0.001)
    }

    // ─── Pattern Types ───

    @Test
    fun `all 5 pattern types exist`() {
        assertEquals(5, PatternType.entries.size)
        assertNotNull(PatternType.valueOf("TEMPORAL_RECURRENCE"))
        assertNotNull(PatternType.valueOf("KNOWLEDGE_GAP"))
        assertNotNull(PatternType.valueOf("SKILL_PROGRESSION"))
    }

    // ─── Conceptual Richness ───

    @Test
    fun `entry connected to others via tags scores high conceptual richness`() {
        val target = makeEntry(id = "target", tags = listOf("plumbing", "repair"))
        val related1 = makeEntry(id = "r1", tags = listOf("plumbing", "tools"))
        val related2 = makeEntry(id = "r2", tags = listOf("repair", "automotive"))
        val unrelated = makeEntry(id = "u1", tags = listOf("education"))

        val engine = DreamingEngine(MockMemoryProvider())
        val score = engine.scoreEntry(target, listOf(target, related1, related2, unrelated))

        // 2 related entries out of 3 others → 2/5 = 0.4
        assertEquals(0.4, score.conceptualRichness, 0.001)
    }

    // ─── Helpers ───

    private fun makeEntry(
        id: String = "test-entry",
        confidence: MemoryConfidence = MemoryConfidence.MODERATE,
        evidenceRefs: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        updatedAtEpochMs: Long = System.currentTimeMillis(),
        version: Int = 1,
    ) = MemoryEntry(
        id = id,
        namespace = "test",
        memoryClass = MemoryClass.SEMANTIC,
        key = "test_key_$id",
        value = "test value",
        confidence = confidence,
        evidenceRefs = evidenceRefs,
        tags = tags,
        updatedAtEpochMs = updatedAtEpochMs,
        version = version,
    )

    private fun scoreSingle(entry: MemoryEntry): PromotionScore {
        val engine = DreamingEngine(MockMemoryProvider())
        return engine.scoreEntry(entry, listOf(entry))
    }
}

/**
 * Minimal mock for testing scoring logic without real storage.
 */
private class MockMemoryProvider : MemoryProvider {
    override val providerId = "mock"
    override suspend fun store(entry: MemoryEntry) = entry
    override suspend fun retrieve(id: String): MemoryEntry? = null
    override suspend fun query(query: MemoryQuery) = emptyList<MemoryEntry>()
    override suspend fun supersede(existingId: String, newEntry: MemoryEntry) = newEntry
    override suspend fun delete(id: String) = true
    override suspend fun export(namespace: String) = emptyList<MemoryEntry>()
    override suspend fun importEntries(entries: List<MemoryEntry>) = 0
    override suspend fun detectContradictions(namespace: String) = emptyList<MemoryContradiction>()
    override suspend fun consolidate(namespace: String) = MemoryConsolidationEvent(
        "mock", namespace, 0, 0, 0, 0,
        System.currentTimeMillis(), System.currentTimeMillis()
    )
}
