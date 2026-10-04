package com.elysium369.meet.safety.science.domain

import java.time.Instant
import java.util.UUID

/**
 * A scientific claim is a structured proposition about the world,
 * grounded in evidence and subject to falsification.
 *
 * Does NOT duplicate `safety_claims` — this is the scientific layer
 * built on top of the existing Safety foundation.
 *
 * Example proposition:
 * "Document D was received by Office X on 2008-04-12."
 */
data class ScientificClaim(
    val id: UUID,
    val proposition: String,

    val subjectEntityId: UUID?,
    val predicate: String,
    val objectEntityId: UUID?,

    val assertionState: EvidenceAssertionState,

    val temporalScope: TemporalScope,
    val geographicScope: GeographicScope?,

    val supportingEvidenceIds: List<UUID>,
    val contradictingEvidenceIds: List<UUID>,

    val supportingClaimIds: List<UUID>,
    val contradictingClaimIds: List<UUID>,

    val alternativeHypothesisIds: List<UUID>,

    val causalStatus: CausalStatus,

    val methodologyVersion: String,

    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Four-timestamp model for precise temporal reasoning.
 *
 * - [occurredAt] — when the event actually happened
 * - [knownAt]    — when the information reached a relevant actor
 * - [validFrom]  — start of the temporal window of validity
 * - [validUntil] — end of the temporal window of validity
 */
data class TemporalScope(
    val occurredAt: Instant? = null,
    val knownAt: Instant? = null,
    val validFrom: Instant? = null,
    val validUntil: Instant? = null,
)

data class GeographicScope(
    val locationEntityId: UUID? = null,
    val exposure: LocationExposure = LocationExposure.PRIVATE_EXACT,
)

enum class CausalStatus {
    NOT_ASSESSED,
    TEMPORAL_ASSOCIATION,
    CORRELATIONAL,
    MECHANISTIC_SUPPORT,
    CAUSAL_INFERENCE,
    CAUSALLY_SUPPORTED,
    CONTRADICTED,
    UNKNOWN,
}

/**
 * AI produces candidates, never facts.
 * A human researcher must explicitly promote a candidate to a claim.
 */
data class ClaimCandidate(
    val proposition: String,
    val evidenceSpanIds: List<UUID>,
    val confidence: Double,
)

/** Claim-to-claim relations — extends existing safety_claim_relations. */
data class ScientificClaimRelation(
    val sourceClaimId: UUID,
    val targetClaimId: UUID,
    val relation: ClaimRelationType,
)

enum class ClaimRelationType {
    SUPPORTS,
    CONTRADICTS,
    DEPENDS_ON,
    QUALIFIES,
    SUPERSEDES,
}
