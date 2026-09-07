package com.elysium369.meet.core.research

import org.junit.Assert.*
import org.junit.Test

class ResearchAgentTest {

    @Test
    fun `demand signal includes constitutional disclaimer`() {
        val signal = DemandSignal(
            signalId = "ds-1", domain = "plumbing", location = "Escazú",
            description = "Alta demanda de fontaneros",
            estimatedDemandLevel = DemandLevel.HIGH,
            confidence = ResearchConfidence.MODERATE,
        )
        assertTrue(signal.disclaimer.contains("No garantiza"))
    }

    @Test
    fun `extract demand signals from high relevance findings`() {
        val agent = ResearchAgent()
        val result = ResearchResult(
            taskId = "t1",
            findings = listOf(
                ResearchFinding("High demand", "Lots of plumbing jobs", 0.9, emptyMap(), 0),
                ResearchFinding("Low demand", "Few electrical jobs", 0.3, emptyMap(), 1),
            ),
            sources = listOf(ResearchSource("Google", sourceType = SourceType.WEB_SEARCH)),
            confidence = ResearchConfidence.HIGH,
        )

        val signals = agent.extractDemandSignals(result, "plumbing", "Escazú")
        assertEquals(1, signals.size) // only the 0.9 relevance one
        assertEquals(DemandLevel.HIGH, signals[0].estimatedDemandLevel)
    }

    @Test
    fun `no signals from empty results`() {
        val agent = ResearchAgent()
        val result = ResearchResult(
            taskId = "t2", findings = emptyList(),
            sources = emptyList(), confidence = ResearchConfidence.SPECULATIVE,
        )
        val signals = agent.extractDemandSignals(result, "auto", "San José")
        assertTrue(signals.isEmpty())
    }

    @Test
    fun `all 7 research types exist`() {
        assertEquals(7, ResearchType.entries.size)
        assertNotNull(ResearchType.valueOf("DEMAND_SIGNAL"))
        assertNotNull(ResearchType.valueOf("PRICE_BENCHMARK"))
        assertNotNull(ResearchType.valueOf("SKILL_DEMAND"))
    }

    @Test
    fun `all 5 demand levels exist`() {
        assertEquals(5, DemandLevel.entries.size)
    }

    @Test
    fun `all 7 source types exist`() {
        assertEquals(7, SourceType.entries.size)
    }

    @Test
    fun `research result tracks findings count`() {
        val result = ResearchResult(
            taskId = "t3",
            findings = listOf(
                ResearchFinding("F1", "S1", 0.8, emptyMap(), 0),
                ResearchFinding("F2", "S2", 0.6, emptyMap(), 1),
            ),
            sources = emptyList(),
            confidence = ResearchConfidence.MODERATE,
        )
        assertEquals(2, result.findingsCount)
        assertTrue(result.hasResults)
    }

    @Test
    fun `moderate relevance maps to MODERATE demand`() {
        val agent = ResearchAgent()
        val result = ResearchResult(
            taskId = "t4",
            findings = listOf(
                ResearchFinding("Mid", "Medium demand", 0.65, emptyMap(), 0),
            ),
            sources = emptyList(),
            confidence = ResearchConfidence.MODERATE,
        )
        val signals = agent.extractDemandSignals(result, "electrical", "Heredia")
        assertEquals(DemandLevel.MODERATE, signals[0].estimatedDemandLevel)
    }
}
