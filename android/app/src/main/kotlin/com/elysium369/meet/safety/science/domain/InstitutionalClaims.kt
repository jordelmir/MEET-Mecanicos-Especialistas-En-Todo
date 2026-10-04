package com.elysium369.meet.safety.science.domain

import kotlinx.serialization.Serializable

// ═══════════════════════════════════════════════════════════════════
// §58 — Institutional Claims
//
// CRIMINAL_LIABILITY_ASSERTION is restricted:
// The app can store that a legal authority holds this conclusion,
// but MUST NOT present it as scientific fact just because a user
// typed it.
// ═══════════════════════════════════════════════════════════════════

@Serializable
enum class InstitutionalClaimType {
    DOCUMENTED_ACTION,
    DOCUMENTED_NON_ACTION,
    KNOWLEDGE_ASSERTION,
    AUTHORITY_ASSERTION,
    DUTY_ASSERTION,
    PATTERN_ASSERTION,
    CAUSAL_ASSERTION,

    /**
     * RESTRICTED: Only allowed when the assertion originates
     * from an external legal authority (court, prosecutor).
     * The system NEVER generates this from evidence alone.
     */
    CRIMINAL_LIABILITY_ASSERTION,
}

/**
 * Validates that a claim of institutional type has proper sourcing.
 */
object InstitutionalClaimValidator {

    /**
     * Returns true if the claim type is allowed given the source context.
     *
     * [CRIMINAL_LIABILITY_ASSERTION] requires:
     * - Source entity must be a JUDICIAL_BODY, COURT, or PROSECUTORIAL_BODY
     * - Cannot be created by AI
     * - Cannot be created without supporting evidence
     */
    fun isAllowed(
        claimType: InstitutionalClaimType,
        sourceEntityType: EntityType?,
        createdByAi: Boolean,
        hasEvidence: Boolean,
    ): Boolean {
        if (claimType != InstitutionalClaimType.CRIMINAL_LIABILITY_ASSERTION) {
            return true
        }

        // CRIMINAL_LIABILITY_ASSERTION restrictions
        if (createdByAi) return false
        if (!hasEvidence) return false

        val allowedSources = setOf(
            EntityType.JUDICIAL_BODY,
            EntityType.COURT,
            EntityType.PROSECUTORIAL_BODY,
        )

        return sourceEntityType in allowedSources
    }
}

// ═══════════════════════════════════════════════════════════════════
// §82 — Source Law Separation
//
// Elysium can store legal frameworks but MUST distinguish:
//   SOURCE_LAW
//   LEGAL_INTERPRETATION
//   RESEARCHER_INTERPRETATION
//   AI_SUMMARY
//   COURT_DETERMINATION
// Never fuse them.
// ═══════════════════════════════════════════════════════════════════

@Serializable
enum class LegalSourceType {
    SOURCE_LAW,
    CONSTITUTIONAL_PROVISION,
    REGULATION,
    COURT_DECISION,
    LEGAL_INTERPRETATION,
    RESEARCHER_INTERPRETATION,
    AI_SUMMARY,
    COURT_DETERMINATION,
}

// ═══════════════════════════════════════════════════════════════════
// §84 — Population Incident Aggregates
//
// Distinguish: reported count, verified count, estimated count,
// duplicate-adjusted count.
// 5000 reports ≠ 5000 victims automatically.
// ═══════════════════════════════════════════════════════════════════

@Serializable
data class PopulationIncidentAggregate(
    val incidentCount: Long,
    val victimCount: Long,
    val uniqueVictimCount: Long?,
    val confidenceInterval: com.elysium369.meet.safety.science.analysis.ConfidenceInterval?,
    val countType: VictimCountType,
    val deduplicationApplied: Boolean,
    val methodologyVersion: String,
)

@Serializable
enum class VictimCountType {
    REPORTED,
    VERIFIED,
    ESTIMATED,
    DUPLICATE_ADJUSTED,
}

// ═══════════════════════════════════════════════════════════════════
// §85 — Victim Record Deduplication
//
// A probabilistic match is NEVER confirmed identity.
// ═══════════════════════════════════════════════════════════════════

@Serializable
enum class IdentityMatchLevel {
    POSSIBLE_MATCH,
    LIKELY_MATCH,
    VERIFIED_MATCH,
}

@Serializable
data class VictimRecord(
    val id: String,
    val caseId: String,
    val anonymizedToken: String, // never raw PII
    val matchLevel: IdentityMatchLevel?,
    val matchedRecordId: String?,
    val matchEvidence: String?,
)

// ═══════════════════════════════════════════════════════════════════
// §86 — Death Attribution
//
// Three completely distinct levels:
//   1. death occurred
//   2. death caused by X
//   3. X criminally responsible
// ═══════════════════════════════════════════════════════════════════

@Serializable
data class DeathAttribution(
    val incidentId: String,
    val victimRecordId: String,
    val deathOccurred: EvidenceAssertionState,
    val causeOfDeath: String?,
    val causeAssertionState: EvidenceAssertionState,
    val attributedToEntityId: String?,
    val attributionAssertionState: EvidenceAssertionState,
    val officialDisposition: String?,
    val officialDispositionSource: LegalSourceType?,
)

// ═══════════════════════════════════════════════════════════════════
// §87 — VictimCount is descriptive. It does NOT determine
//        LegalLiability.
//
// A huge number may increase the importance of an investigation
// but does NOT substitute for legal elements.
// ═══════════════════════════════════════════════════════════════════
// (Enforced by separating PopulationIncidentAggregate from
//  any LegalReferralPackage.)
