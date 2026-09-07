package com.elysium369.meet.core.memory

import org.junit.Assert.*
import org.junit.Test

class ElysiumMemoryOsTest {

    // ─── MemoryEntry ───

    @Test
    fun `memory entry tracks provenance and confidence`() {
        val entry = MemoryEntry(
            id = "mem-1",
            namespace = "learning",
            memoryClass = MemoryClass.LEARNING,
            key = "math_mastery_fractions",
            value = "Mastery 0.72 on fractions concept cr_mat1_feb_fracciones",
            confidence = MemoryConfidence.HIGH,
            provenance = MemoryProvenance.LEARNING_ENGINE,
            evidenceRefs = listOf("evidence://fsrs_card_123"),
        )

        assertEquals(MemoryClass.LEARNING, entry.memoryClass)
        assertEquals(MemoryConfidence.HIGH, entry.confidence)
        assertEquals(MemoryProvenance.LEARNING_ENGINE, entry.provenance)
        assertTrue(entry.isActive)
    }

    @Test
    fun `superseded entry is not active`() {
        val entry = MemoryEntry(
            id = "mem-old",
            namespace = "identity",
            memoryClass = MemoryClass.IDENTITY,
            key = "home_address",
            value = "San José, Escazú",
            supersededById = "mem-new",
        )

        assertFalse(entry.isActive)
    }

    @Test
    fun `expired entry is not active`() {
        val entry = MemoryEntry(
            id = "mem-expired",
            namespace = "economic",
            memoryClass = MemoryClass.ECONOMIC,
            key = "temp_job_offer",
            value = "Oferta temporal de plomería",
            expiresAtEpochMs = 1_000, // expired long ago
        )

        assertFalse(entry.isActive)
    }

    @Test
    fun `all 11 memory classes exist per ASTRA V6 section 17`() {
        assertEquals(11, MemoryClass.entries.size)
        assertNotNull(MemoryClass.valueOf("IDENTITY"))
        assertNotNull(MemoryClass.valueOf("EPISODIC"))
        assertNotNull(MemoryClass.valueOf("SEMANTIC"))
        assertNotNull(MemoryClass.valueOf("PROCEDURAL"))
        assertNotNull(MemoryClass.valueOf("GOALS"))
        assertNotNull(MemoryClass.valueOf("LEARNING"))
        assertNotNull(MemoryClass.valueOf("MASTERY_EVIDENCE"))
        assertNotNull(MemoryClass.valueOf("PROJECT_HISTORY"))
        assertNotNull(MemoryClass.valueOf("ECONOMIC"))
        assertNotNull(MemoryClass.valueOf("PREFERENCES"))
        assertNotNull(MemoryClass.valueOf("RELATIONAL"))
    }

    // ─── Namespace Privacy Wall (§53) ───

    @Test
    fun `namespace enforces privacy isolation`() {
        val learningNs = MemoryNamespace(
            id = "ns-learning-child",
            ownerId = "student-1",
            label = "Learning Progress",
            accessPolicy = NamespaceAccessPolicy.GUARDIAN_VISIBLE,
            isolatedFromNamespaces = listOf("ns-economic"),
        )

        val economicNs = MemoryNamespace(
            id = "ns-economic",
            ownerId = "student-1",
            label = "Economic History",
            accessPolicy = NamespaceAccessPolicy.PRIVATE,
        )

        // Learning namespace is isolated from economic — §53 privacy wall
        assertTrue(learningNs.isolatedFromNamespaces.contains(economicNs.id))
        assertEquals(NamespaceAccessPolicy.GUARDIAN_VISIBLE, learningNs.accessPolicy)
        assertEquals(NamespaceAccessPolicy.PRIVATE, economicNs.accessPolicy)
    }

    // ─── Contradiction Detection ───

    @Test
    fun `contradiction tracks resolution state`() {
        val contradiction = MemoryContradiction(
            id = "contra-1",
            existingEntryId = "mem-old-address",
            newEntryId = "mem-new-address",
            resolution = ContradictionResolution.NEWER_WINS,
        )

        assertEquals(ContradictionResolution.NEWER_WINS, contradiction.resolution)
    }

    // ─── OpenClaw Configuration ───

    @Test
    fun `openclaw config has sensible defaults`() {
        val config = OpenClawConfig()
        assertEquals("ws://localhost:3000/ws", config.gatewayUrl)
        assertEquals("elysium-memory", config.wikiVaultId)
        assertEquals(15, config.syncIntervalMinutes)
        assertTrue(config.enableDreaming)
        assertTrue(config.enableContradictionDetection)
    }

    // ─── Provider Independence (§69) ───

    @Test
    fun `openclaw provider implements MemoryProvider interface`() {
        val config = OpenClawConfig(apiToken = "test-token")
        val provider: MemoryProvider = OpenClawMemoryProvider(config)
        assertEquals("openclaw-wiki-elysium-memory", provider.providerId)
    }

    @Test
    fun `memory provenance includes all 8 sources`() {
        assertEquals(8, MemoryProvenance.entries.size)
        assertNotNull(MemoryProvenance.valueOf("OPENCLAW_WIKI"))
        assertNotNull(MemoryProvenance.valueOf("CONSOLIDATION"))
    }
}
