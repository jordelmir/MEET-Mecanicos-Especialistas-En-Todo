package com.elysium369.meet.safety.science.application

import com.elysium369.meet.safety.science.analysis.ResearchHypothesis
import com.elysium369.meet.safety.science.domain.ScientificClaim
import java.util.UUID

/**
 * Explicit boundary for what AI may and may NOT do in the scientific layer.
 *
 * AI produces [ClaimCandidate], never [ScientificClaim].
 * AI finds contradictions and missing evidence, never declares guilt.
 * AI proposes alternative hypotheses, never closes investigations.
 *
 * `suspend fun declareGuilt(...)` — DOES NOT EXIST.
 */
interface ScientificAiAssistant {

    /** Extract structured claim candidates from raw evidence. */
    suspend fun extractClaims(evidenceId: UUID): List<ClaimCandidateResult>

    /** Find claims that may contradict the given claim. */
    suspend fun findContradictions(claimId: UUID): List<ContradictionCandidate>

    /** Propose alternative explanations for the given claim. */
    suspend fun proposeAlternativeHypotheses(claimId: UUID): List<ResearchHypothesis>

    /** Identify what evidence would be needed to verify or falsify. */
    suspend fun identifyMissingEvidence(claimId: UUID): List<EvidenceRequest>
}

/**
 * AI output — always a candidate, never a fact.
 * Human investigator must explicitly promote to [ScientificClaim].
 */
data class ClaimCandidateResult(
    val proposition: String,
    val evidenceSpanIds: List<UUID>,
    val confidence: Double,
)

data class ContradictionCandidate(
    val contradictedClaimId: UUID,
    val contradictingEvidenceIds: List<UUID>,
    val explanation: String,
)

data class EvidenceRequest(
    val description: String,
    val requiredToResolve: List<UUID>,
    val priority: EvidenceRequestPriority,
)

enum class EvidenceRequestPriority {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
}

/**
 * AI Research Copilot system prompt — versioned, part of codebase.
 *
 * ```
 * You are an evidence-analysis assistant.
 *
 * You MUST:
 * - distinguish facts from hypotheses;
 * - cite exact evidence IDs;
 * - never invent evidence;
 * - never upgrade epistemic state;
 * - identify contradictory evidence;
 * - identify missing evidence;
 * - identify alternative explanations;
 * - distinguish temporal association from causality;
 * - distinguish documented non-action from criminal liability;
 * - preserve uncertainty;
 * - explicitly state when evidence is insufficient.
 *
 * If evidence is insufficient, return INSUFFICIENT_EVIDENCE.
 * ```
 */
const val AI_RESEARCH_COPILOT_SYSTEM_PROMPT_V1 = """You are an evidence-analysis assistant.

You MUST:
- distinguish facts from hypotheses;
- cite exact evidence IDs;
- never invent evidence;
- never upgrade epistemic state;
- identify contradictory evidence;
- identify missing evidence;
- identify alternative explanations;
- distinguish temporal association from causality;
- distinguish documented non-action from criminal liability;
- preserve uncertainty;
- explicitly state when evidence is insufficient.

If evidence is insufficient, return INSUFFICIENT_EVIDENCE."""
