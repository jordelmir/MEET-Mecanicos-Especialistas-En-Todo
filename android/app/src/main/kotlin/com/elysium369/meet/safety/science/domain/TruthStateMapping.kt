package com.elysium369.meet.safety.science.domain

import com.elysium369.meet.core.truth.TruthState

/**
 * Deterministic, non-elevating bridge from the existing [TruthState] universe
 * to [EvidenceAssertionState].
 *
 * This mapping is conservative on purpose: ESTIMATED, SIMULATED, NOT_INTEGRATED
 * and NOT_EXECUTED all collapse to INSUFFICIENT_EVIDENCE because the scientific
 * layer must never inherit an unverified assumption from the OS layer.
 */
fun TruthState.toScientificState(): EvidenceAssertionState =
    when (this) {
        TruthState.OBSERVED      -> EvidenceAssertionState.OBSERVED
        TruthState.AUTHORITATIVE -> EvidenceAssertionState.AUTHORITATIVE
        TruthState.DERIVED       -> EvidenceAssertionState.DERIVED
        TruthState.ESTIMATED     -> EvidenceAssertionState.INSUFFICIENT_EVIDENCE
        TruthState.SIMULATED     -> EvidenceAssertionState.INSUFFICIENT_EVIDENCE
        TruthState.UNKNOWN       -> EvidenceAssertionState.UNKNOWN
        TruthState.NOT_INTEGRATED,
        TruthState.NOT_EXECUTED  -> EvidenceAssertionState.INSUFFICIENT_EVIDENCE
    }
