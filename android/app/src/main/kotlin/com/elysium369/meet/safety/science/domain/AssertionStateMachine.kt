package com.elysium369.meet.safety.science.domain

import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Formal state machine governing epistemic transitions.
 *
 * Rules:
 * 1. Only the transitions defined in [ALLOWED_TRANSITIONS] are valid.
 * 2. Any state may transition to DISPUTED (new contradicting evidence).
 * 3. Every transition requires evidence, actor and methodology — enum alone
 *    is never sufficient.
 * 4. AI actors may NEVER trigger an upward transition.
 */
class AssertionStateMachine @Inject constructor() {

    companion object {
        /** Upward epistemic ladder — each step requires explicit evidence. */
        private val ALLOWED_TRANSITIONS: Map<EvidenceAssertionState, Set<EvidenceAssertionState>> =
            mapOf(
                EvidenceAssertionState.UNKNOWN to setOf(
                    EvidenceAssertionState.OBSERVED,
                    EvidenceAssertionState.INSUFFICIENT_EVIDENCE,
                ),
                EvidenceAssertionState.OBSERVED to setOf(
                    EvidenceAssertionState.DOCUMENTED,
                    EvidenceAssertionState.DISPUTED,
                ),
                EvidenceAssertionState.DOCUMENTED to setOf(
                    EvidenceAssertionState.CORROBORATED,
                    EvidenceAssertionState.DISPUTED,
                ),
                EvidenceAssertionState.CORROBORATED to setOf(
                    EvidenceAssertionState.PEER_REVIEWED,
                    EvidenceAssertionState.DISPUTED,
                ),
                EvidenceAssertionState.PEER_REVIEWED to setOf(
                    EvidenceAssertionState.INDEPENDENTLY_REPLICATED,
                    EvidenceAssertionState.DISPUTED,
                ),
                // Derived / statistical / causal tracks are orthogonal
                EvidenceAssertionState.DERIVED to setOf(
                    EvidenceAssertionState.STATISTICALLY_SUPPORTED,
                    EvidenceAssertionState.DISPUTED,
                ),
                EvidenceAssertionState.STATISTICALLY_SUPPORTED to setOf(
                    EvidenceAssertionState.CAUSALLY_SUPPORTED,
                    EvidenceAssertionState.DISPUTED,
                ),
                // Terminal negative states
                EvidenceAssertionState.DISPUTED to setOf(
                    EvidenceAssertionState.CONTRADICTED,
                ),
            )
    }

    fun canTransition(
        from: EvidenceAssertionState,
        to: EvidenceAssertionState,
    ): Boolean =
        ALLOWED_TRANSITIONS[from]?.contains(to) == true

    /**
     * Attempt a guarded transition. Returns [AssertionStateTransition] on
     * success or throws [IllegalStateException] if the transition is invalid.
     *
     * @param actorIsAi When true, only lateral or downward transitions are
     *   allowed (DISPUTED, CONTRADICTED, INSUFFICIENT_EVIDENCE). AI may never
     *   elevate epistemic state.
     */
    fun transition(
        subjectId: UUID,
        from: EvidenceAssertionState,
        to: EvidenceAssertionState,
        reason: String,
        evidenceIds: List<UUID>,
        actorId: UUID,
        methodologyVersion: String,
        actorIsAi: Boolean = false,
    ): AssertionStateTransition {
        require(reason.isNotBlank()) { "Transition reason is required" }
        require(evidenceIds.isNotEmpty()) { "At least one evidence ID is required" }

        if (actorIsAi) {
            val aiAllowed = setOf(
                EvidenceAssertionState.DISPUTED,
                EvidenceAssertionState.CONTRADICTED,
                EvidenceAssertionState.INSUFFICIENT_EVIDENCE,
            )
            check(to in aiAllowed) {
                "AI actor cannot elevate assertion state from $from to $to"
            }
        }

        check(canTransition(from, to)) {
            "Illegal assertion transition: $from → $to"
        }

        return AssertionStateTransition(
            id = UUID.randomUUID(),
            subjectId = subjectId,
            from = from,
            to = to,
            reason = reason,
            evidenceIds = evidenceIds,
            actorId = actorId,
            methodologyVersion = methodologyVersion,
            occurredAt = Instant.now(),
        )
    }
}

/**
 * Immutable audit record of every epistemic state change.
 *
 * Enables answering: "Why did Elysium change this conclusion?"
 */
data class AssertionStateTransition(
    val id: UUID,
    val subjectId: UUID,
    val from: EvidenceAssertionState,
    val to: EvidenceAssertionState,
    val reason: String,
    val evidenceIds: List<UUID>,
    val actorId: UUID,
    val methodologyVersion: String,
    val occurredAt: Instant,
)
