package com.elysium369.meet.safety.science.domain

import com.elysium369.meet.core.truth.TruthState

/**
 * Conservative bridge from OS truth-state into the scientific assertion domain.
 *
 * The original state is retained beside the projection, so SIMULATED,
 * NOT_INTEGRATED and NOT_EXECUTED remain distinguishable in audit/UI.
 * This mapping never independently validates a source and never promotes claims.
 */
data class ScientificTruthAssessment(
    val sourceState: TruthState,
    val scientificState: EvidenceAssertionState,
    val explanation: String,
)

fun TruthState.toScientificAssessment(): ScientificTruthAssessment {
    val scientific = when (this) {
        TruthState.OBSERVED -> EvidenceAssertionState.OBSERVED
        TruthState.AUTHORITATIVE -> EvidenceAssertionState.AUTHORITATIVE
        TruthState.DERIVED -> EvidenceAssertionState.DERIVED
        TruthState.ESTIMATED,
        TruthState.SIMULATED,
        TruthState.NOT_INTEGRATED,
        TruthState.NOT_EXECUTED -> EvidenceAssertionState.INSUFFICIENT_EVIDENCE
        TruthState.UNKNOWN -> EvidenceAssertionState.UNKNOWN
    }
    val explanation = when (this) {
        TruthState.OBSERVED -> "Origin state is OBSERVED; the mapping does not establish independent corroboration."
        TruthState.AUTHORITATIVE -> "Origin state is AUTHORITATIVE; this mapping does not independently verify the source authority."
        TruthState.DERIVED -> "Origin state is DERIVED; the source calculation and inputs still require traceable provenance."
        TruthState.ESTIMATED -> "Origin state is ESTIMATED; estimates are not promoted to verified evidence."
        TruthState.SIMULATED -> "Origin state is SIMULATED; test or demo output is not a physical occurrence."
        TruthState.UNKNOWN -> "Origin state is UNKNOWN; the available source does not establish the value."
        TruthState.NOT_INTEGRATED -> "Origin state is NOT_INTEGRATED; the required integration is absent or unconfigured."
        TruthState.NOT_EXECUTED -> "Origin state is NOT_EXECUTED; no completed execution or acknowledgement was observed."
    }
    return ScientificTruthAssessment(this, scientific, explanation)
}

/**
 * Backwards-compatible projection for callers that only need the scientific
 * state. New audit/UI surfaces should retain [ScientificTruthAssessment.sourceState].
 */
fun TruthState.toScientificState(): EvidenceAssertionState =
    toScientificAssessment().scientificState
