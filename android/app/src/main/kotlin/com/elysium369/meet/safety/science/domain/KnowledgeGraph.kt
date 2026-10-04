package com.elysium369.meet.safety.science.domain

import java.time.Instant
import java.util.UUID

/**
 * Records when specific information reached a specific actor.
 *
 * This is the answer to: "¿Qué información llegó a esta entidad y cuándo?"
 *
 * Knowledge ≠ Action.
 * Knowledge + Authority + Duty + NonAction = documented pattern (NOT guilt).
 */
data class KnowledgeEvent(
    val id: UUID,
    val actorEntityId: UUID,

    /** What information became available. */
    val informationClaimId: UUID,

    /** Evidence demonstrating the information reached the actor. */
    val evidenceIds: List<UUID>,

    val receivedAt: Instant,
    val channel: KnowledgeChannel,

    val sourceEntityId: UUID?,
    val authorityContextId: UUID?,

    val assertionState: EvidenceAssertionState,
    val createdAt: Instant,
)

enum class KnowledgeChannel {
    OFFICIAL_DOCUMENT,
    COURT_RECORD,
    PROSECUTORIAL_RECORD,
    POLICE_RECORD,
    PUBLIC_RECORD,
    EMAIL,
    LETTER,
    MEETING,
    TESTIMONY,
    JOURNALISTIC_REPORT,
    DATABASE,
    DIRECT_OBSERVATION,
    UNKNOWN,
}

// ── Authority Graph (§7) ────────────────────────────────────────────

/**
 * Asserts that an actor held a specific type of authority
 * over a jurisdiction during a time window.
 *
 * Fundamental for separating "sabía" from "sabía + tenía autoridad".
 */
data class AuthorityRelation(
    val id: UUID,
    val actorEntityId: UUID,
    val authorityType: AuthorityType,

    val jurisdictionEntityId: UUID?,
    val validFrom: Instant?,
    val validUntil: Instant?,

    val sourceEvidenceIds: List<UUID>,
    val assertionState: EvidenceAssertionState,
)

enum class AuthorityType {
    INVESTIGATE,
    ARREST,
    PROSECUTE,
    REFER_FOR_INVESTIGATION,
    PRESERVE_EVIDENCE,
    ISSUE_ORDER,
    SUPERVISE,
    DISCIPLINE,
    JUDICATE,
    UNKNOWN,
}

// ── Duty Graph (§8) ─────────────────────────────────────────────────

/**
 * Asserts that an actor had a legal duty.
 *
 * The system NEVER generates CRIMINAL_OMISSION.
 * Only DUTY_ASSERTED. Legal responsibility is a judicial determination.
 */
data class DutyAssertion(
    val id: UUID,
    val actorEntityId: UUID,
    val dutyType: DutyType,

    val jurisdictionEntityId: UUID?,
    val validFrom: Instant?,
    val validUntil: Instant?,

    val legalSourceEvidenceIds: List<UUID>,
    val assertionState: EvidenceAssertionState,
)

enum class DutyType {
    INVESTIGATE,
    REPORT,
    PRESERVE,
    PROTECT,
    REFER,
    PROSECUTE,
    SUPERVISE,
    DISCLOSE,
    UNKNOWN,
}

// ── Accountability Actions (§9) ─────────────────────────────────────

/**
 * Documented action or documented non-action.
 *
 * CRITICAL INVARIANT: NonAction ≠ IllegalOmission.
 * The system records what happened; legal qualification is external.
 */
sealed interface AccountabilityAction {
    val actorEntityId: UUID
    val occurredAt: Instant
    val evidenceIds: List<UUID>

    data class Action(
        override val actorEntityId: UUID,
        override val occurredAt: Instant,
        override val evidenceIds: List<UUID>,
        val actionType: String,
    ) : AccountabilityAction

    data class NonAction(
        override val actorEntityId: UUID,
        override val occurredAt: Instant,
        override val evidenceIds: List<UUID>,
        val expectedAction: String,
    ) : AccountabilityAction
}

// ── Scientific Event / Timeline (§10) ───────────────────────────────

/**
 * A scientific event with four distinct timestamps:
 * - [occurredAt]  — when the event happened
 * - [knownAt]     — when the information reached relevant actors
 * - [recordedAt]  — when the system recorded it
 * - [publishedAt] — when it was made public
 *
 * Historical investigation depends on this distinction.
 */
data class ScientificEvent(
    val id: UUID,
    val type: ScientificEventType,

    val occurredAt: Instant?,
    val knownAt: Instant?,
    val recordedAt: Instant,
    val publishedAt: Instant?,
    val verifiedAt: Instant?,

    val actorEntityIds: List<UUID>,
    val locationEntityId: UUID?,

    val evidenceIds: List<UUID>,
    val claimIds: List<UUID>,

    val assertionState: EvidenceAssertionState,
)

enum class ScientificEventType {
    INCIDENT,
    DOCUMENT_CREATED,
    DOCUMENT_RECEIVED,
    REPORT_FILED,
    INVESTIGATION_OPENED,
    INVESTIGATION_CLOSED,
    ARREST,
    PROSECUTION,
    COURT_ACTION,
    POLICY_CHANGE,
    APPOINTMENT,
    DISMISSAL,
    COMMUNICATION,
    EVIDENCE_CAPTURED,
    OTHER,
}
