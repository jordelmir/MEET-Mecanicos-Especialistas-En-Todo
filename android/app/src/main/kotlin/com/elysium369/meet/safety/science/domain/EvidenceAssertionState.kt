package com.elysium369.meet.safety.science.domain

/**
 * Unified epistemological classification for all scientific evidence assertions.
 *
 * Invariant: No adapter may automatically elevate state. Each upward transition
 * requires explicit evidence, actor, and methodology — enforced by
 * [AssertionStateMachine].
 *
 * ```
 * EVIDENCE ≠ GUILT
 * CLAIM ≠ CONVICTION
 * CORRELATION ≠ CAUSATION
 * OMISSION ≠ CRIMINAL LIABILITY
 * AI OUTPUT ≠ FACT
 * PUBLICATION ≠ COURT JUDGMENT
 * ```
 */
enum class EvidenceAssertionState {
    // ── ascending epistemic ladder ──
    OBSERVED,
    DOCUMENTED,
    AUTHORITATIVE,
    CORROBORATED,
    DERIVED,
    STATISTICALLY_SUPPORTED,
    CAUSALLY_SUPPORTED,
    PEER_REVIEWED,
    INDEPENDENTLY_REPLICATED,

    // ── negative / indeterminate ──
    DISPUTED,
    CONTRADICTED,
    INSUFFICIENT_EVIDENCE,
    UNKNOWN,
}
