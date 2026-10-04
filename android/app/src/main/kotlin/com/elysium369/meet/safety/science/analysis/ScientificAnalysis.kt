package com.elysium369.meet.safety.science.analysis

import com.elysium369.meet.safety.science.domain.CausalStatus
import com.elysium369.meet.safety.science.domain.EvidenceAssertionState
import java.time.Instant
import java.util.UUID

// ── Hypotheses & Falsification (§20-21) ─────────────────────────────

data class ResearchHypothesis(
    val id: UUID,
    val proposition: String,
    val nullHypothesis: String?,

    val supportingEvidenceIds: List<UUID>,
    val contradictingEvidenceIds: List<UUID>,
    val alternativeHypothesisIds: List<UUID>,

    val falsificationCriteria: List<String>,

    val status: HypothesisStatus,
    val methodologyVersion: String,
)

enum class HypothesisStatus {
    PROPOSED,
    TESTING,
    SUPPORTED,
    WEAKLY_SUPPORTED,
    DISPUTED,
    REFUTED,
    INCONCLUSIVE,
}

/**
 * Evidence assertion used by the falsification engine.
 *
 * No magic scoring — every assessment must carry methodology,
 * inputs, formula, version, and uncertainty.
 */
data class EvidenceAssertion(
    val id: UUID,
    val claimId: UUID?,
    val hypothesisId: UUID?,
    val assertionState: EvidenceAssertionState,
    val tags: Set<String>,
) {
    fun contradicts(hypothesis: ResearchHypothesis): Boolean =
        hypothesisId == hypothesis.id &&
            assertionState in setOf(
                EvidenceAssertionState.CONTRADICTED,
                EvidenceAssertionState.DISPUTED,
            )

    fun supports(hypothesis: ResearchHypothesis): Boolean =
        hypothesisId == hypothesis.id &&
            assertionState in setOf(
                EvidenceAssertionState.DOCUMENTED,
                EvidenceAssertionState.CORROBORATED,
                EvidenceAssertionState.AUTHORITATIVE,
                EvidenceAssertionState.PEER_REVIEWED,
                EvidenceAssertionState.INDEPENDENTLY_REPLICATED,
            )

    fun satisfies(criterion: String): Boolean =
        tags.contains(criterion)
}

data class FalsificationResult(
    val supportedEvidence: List<UUID>,
    val contradictingEvidence: List<UUID>,
    val unresolvedCriteria: List<String>,
    val status: HypothesisStatus,
)

/**
 * Falsification engine — evaluates hypotheses against evidence.
 *
 * No confidence scores without full methodology disclosure.
 * Every result must be explainable.
 */
class FalsificationEngine {

    fun evaluate(
        hypothesis: ResearchHypothesis,
        evidence: List<EvidenceAssertion>,
    ): FalsificationResult {
        val contradictions = evidence.filter { it.contradicts(hypothesis) }
        val supports = evidence.filter { it.supports(hypothesis) }
        val missing = hypothesis.falsificationCriteria.filterNot { criterion ->
            evidence.any { it.satisfies(criterion) }
        }

        return FalsificationResult(
            supportedEvidence = supports.map { it.id },
            contradictingEvidence = contradictions.map { it.id },
            unresolvedCriteria = missing,
            status = when {
                contradictions.isNotEmpty() -> HypothesisStatus.DISPUTED
                supports.isNotEmpty() && missing.isEmpty() -> HypothesisStatus.SUPPORTED
                supports.isNotEmpty() -> HypothesisStatus.WEAKLY_SUPPORTED
                else -> HypothesisStatus.INCONCLUSIVE
            },
        )
    }
}

// ── Corroboration & Source Independence (§22-23) ────────────────────

data class CorroborationRecord(
    val id: UUID,
    val claimId: UUID,
    val sourceId: UUID,
    val independenceGroup: String,
    val independenceReason: IndependenceReason,
    val relation: CorroborationRelation,
    val reviewerId: UUID?,
    val createdAt: Instant,
)

enum class IndependenceReason {
    DIFFERENT_AUTHOR,
    DIFFERENT_INSTITUTION,
    DIFFERENT_DATASET,
    DIFFERENT_ACQUISITION_CHANNEL,
    DIFFERENT_METHOD,
    UNKNOWN,
}

enum class CorroborationRelation {
    CORROBORATES,
    PARTIALLY_CORROBORATES,
    CONTRADICTS,
}

/**
 * Source for independence analysis.
 *
 * Rule: 10 reproductions of the same document = 1 underlying source, not 10.
 */
data class ScientificSource(
    val id: UUID,
    val author: String,
    val institution: String?,
    val dataset: String?,
    val acquisitionChannel: String?,
    val method: String?,
)

data class SourceIndependenceGroup(
    val groupKey: String,
    val sourceIds: List<UUID>,
)

/**
 * Groups sources by provenance to detect duplicated evidence
 * masquerading as independent sources.
 */
class SourceIndependenceAnalyzer {

    fun group(sources: List<ScientificSource>): List<SourceIndependenceGroup> {
        return sources
            .groupBy { groupKey(it) }
            .map { (key, grouped) ->
                SourceIndependenceGroup(
                    groupKey = key,
                    sourceIds = grouped.map { it.id },
                )
            }
    }

    private fun groupKey(source: ScientificSource): String =
        listOfNotNull(
            source.dataset,
            source.institution,
            source.acquisitionChannel,
        ).joinToString("|").lowercase().ifEmpty { "ungrouped-${source.id}" }
}

// ── Causal Assessment (§38) ─────────────────────────────────────────

/**
 * Causal analysis is NEVER mixed with claim scoring.
 *
 * Temporal association alone CANNOT establish causality.
 */
data class CausalAssessment(
    val claimId: UUID,
    val temporalOrderingSatisfied: Boolean,
    val alternativeCauses: List<String>,
    val confounders: List<String>,
    val mechanismEvidenceIds: List<UUID>,
    val causalStatus: CausalStatus,
    val methodologyVersion: String,
)

/**
 * Evaluates causal claims conservatively.
 */
class CausalEngine {

    fun evaluate(
        temporalOrdering: Boolean,
        mechanismEvidence: List<UUID>,
        alternativeCauses: List<String>,
        confounders: List<String>,
    ): CausalAssessment = CausalAssessment(
        claimId = UUID.randomUUID(), // placeholder — caller sets real ID
        temporalOrderingSatisfied = temporalOrdering,
        alternativeCauses = alternativeCauses,
        confounders = confounders,
        mechanismEvidenceIds = mechanismEvidence,
        causalStatus = when {
            !temporalOrdering -> CausalStatus.UNKNOWN
            mechanismEvidence.isEmpty() -> CausalStatus.TEMPORAL_ASSOCIATION
            alternativeCauses.isNotEmpty() || confounders.isNotEmpty() ->
                CausalStatus.CORRELATIONAL
            else -> CausalStatus.MECHANISTIC_SUPPORT
        },
        methodologyVersion = "safety-science-v1",
    )
}

// ── Bias Assessment (§41) ───────────────────────────────────────────

data class BiasFinding(
    val present: Boolean,
    val description: String?,
    val mitigationApplied: String?,
)

/**
 * Every research run MUST have a bias assessment before publication.
 */
data class BiasAssessment(
    val selectionBias: BiasFinding,
    val survivorshipBias: BiasFinding,
    val reportingBias: BiasFinding,
    val geographicBias: BiasFinding,
    val institutionalBias: BiasFinding,
    val missingDataBias: BiasFinding,
)

// ── Counterfactual Scenarios (§40) ──────────────────────────────────

data class CounterfactualScenario(
    val id: UUID,
    val factualBaselineRunId: UUID,
    val intervention: String,
    val assumptions: List<String>,
    val expectedOutcome: String?,
    val uncertainty: String,
    val status: CounterfactualStatus,
)

enum class CounterfactualStatus {
    PROPOSED,
    SIMULATED,
    CAUSAL_MODEL_ESTIMATE,
    INCONCLUSIVE,
}
