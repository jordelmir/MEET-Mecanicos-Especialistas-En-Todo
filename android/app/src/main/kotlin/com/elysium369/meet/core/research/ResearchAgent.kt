package com.elysium369.meet.core.research

import kotlinx.serialization.Serializable

/**
 * ASTRA V6 §43 — Economic Discovery / Research Agent.
 * Extracted from OpenClaw's browser automation + research pattern.
 *
 * Enables ELYSIUM to research demand signals, market data,
 * competitor pricing, and economic opportunities in real-time.
 * The agent gathers structured data that feeds into:
 * - ECONOMIC_DISCOVERY intent (UniversalIntentRouter)
 * - DemandSignal engine (ASTRA §43: "where is the next job?")
 * - Economic Passport reputation scoring
 */

// ─── Research Task ───

enum class ResearchType {
    DEMAND_SIGNAL,          // "¿Hay demanda de plomeros en Escazú?"
    PRICE_BENCHMARK,        // "¿Cuánto cuesta cambiar pastillas de freno?"
    COMPETITOR_ANALYSIS,    // "¿Qué talleres hay en mi zona?"
    PARTS_AVAILABILITY,     // "¿Dónde consigo pastillas Bosch para Yaris 2018?"
    REGULATION_CHECK,       // "¿Necesito licencia para electricidad en CR?"
    SKILL_DEMAND,           // "¿Qué habilidades son más demandadas?"
    MARKET_TREND,           // "¿Cómo ha cambiado la demanda de EVs?"
}

@Serializable
data class ResearchTask(
    val taskId: String,
    val type: ResearchType,
    val query: String,
    val domain: String,
    val locale: String = "es-CR",
    val maxResults: Int = 10,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)

// ─── Research Results ───

@Serializable
data class ResearchResult(
    val taskId: String,
    val findings: List<ResearchFinding>,
    val sources: List<ResearchSource>,
    val confidence: ResearchConfidence,
    val completedAtEpochMs: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
) {
    val findingsCount: Int get() = findings.size
    val hasResults: Boolean get() = findings.isNotEmpty()
}

@Serializable
data class ResearchFinding(
    val title: String,
    val summary: String,
    val relevanceScore: Double,
    val dataPoints: Map<String, String> = emptyMap(),
    val sourceIndex: Int,
)

@Serializable
data class ResearchSource(
    val name: String,
    val url: String? = null,
    val sourceType: SourceType,
    val reliability: Double = 0.5,
)

enum class SourceType {
    WEB_SEARCH, GOVERNMENT_DATA, MARKETPLACE, USER_REPORTS,
    PLATFORM_DATA, INDUSTRY_REPORT, ACADEMIC,
}

enum class ResearchConfidence {
    HIGH,       // Multiple consistent sources
    MODERATE,   // Few sources, consistent
    LOW,        // Single source or inconsistent
    SPECULATIVE // Extrapolated, no direct data
}

// ─── Demand Signal ───

/**
 * §43: A demand signal tells a provider "there is work here."
 * INVARIANT: DEMAND SIGNAL ≠ GUARANTEED INCOME (constitutional invariant)
 */
@Serializable
data class DemandSignal(
    val signalId: String,
    val domain: String,
    val location: String,
    val description: String,
    val estimatedDemandLevel: DemandLevel,
    val estimatedPriceRange: String? = null,
    val competitorCount: Int? = null,
    val confidence: ResearchConfidence,
    val detectedAtEpochMs: Long = System.currentTimeMillis(),
    val expiresAtEpochMs: Long? = null,
) {
    /**
     * Constitutional invariant: demand signal is NOT guaranteed income.
     * This disclaimer MUST be shown to users.
     */
    val disclaimer: String = "Señal de demanda estimada. No garantiza ingresos."
}

enum class DemandLevel {
    VERY_HIGH, HIGH, MODERATE, LOW, UNKNOWN,
}

// ─── Research Agent ───

/**
 * Orchestrates research tasks. Can be backed by:
 * - Web search APIs (Brave, Google)
 * - Platform internal data (service history, completed jobs)
 * - Government open data APIs
 * - OpenClaw browser automation (future)
 */
class ResearchAgent {

    private val completedResearch = mutableListOf<ResearchResult>()
    private val demandSignals = mutableListOf<DemandSignal>()

    /**
     * Executes a research task and returns structured results.
     * In production, this calls external APIs. Here it defines the contract.
     */
    suspend fun research(task: ResearchTask): ResearchResult {
        // Contract: implementations call real APIs
        val result = ResearchResult(
            taskId = task.taskId,
            findings = emptyList(),
            sources = emptyList(),
            confidence = ResearchConfidence.SPECULATIVE,
        )
        completedResearch.add(result)
        return result
    }

    /**
     * Extracts demand signals from research results.
     */
    fun extractDemandSignals(result: ResearchResult, domain: String, location: String): List<DemandSignal> {
        if (!result.hasResults) return emptyList()

        val signals = result.findings
            .filter { it.relevanceScore > 0.5 }
            .map { finding ->
                DemandSignal(
                    signalId = "ds-${System.currentTimeMillis()}-${finding.hashCode()}",
                    domain = domain,
                    location = location,
                    description = finding.summary,
                    estimatedDemandLevel = when {
                        finding.relevanceScore > 0.8 -> DemandLevel.HIGH
                        finding.relevanceScore > 0.6 -> DemandLevel.MODERATE
                        else -> DemandLevel.LOW
                    },
                    confidence = result.confidence,
                )
            }

        demandSignals.addAll(signals)
        return signals
    }

    val allDemandSignals: List<DemandSignal> get() = demandSignals.toList()
    val researchHistory: List<ResearchResult> get() = completedResearch.toList()
}
