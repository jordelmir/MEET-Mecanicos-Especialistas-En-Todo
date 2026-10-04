package com.elysium369.meet.safety.science.domain

import java.time.Instant
import java.util.UUID

/**
 * An entity in the scientific knowledge graph.
 *
 * Invariant: automatic identity resolution is PROHIBITED.
 * "Jorge Smith" → person_id=123 requires explicit IDENTITY_MATCH evidence.
 */
data class ScientificEntity(
    val id: UUID,
    val type: EntityType,
    val canonicalName: String,
    val aliases: List<String>,
    val externalIdentifiers: List<ExternalIdentifier>,
    val truthState: EvidenceAssertionState,
    val createdAt: Instant,
)

enum class EntityType {
    PERSON,
    ORGANIZATION,
    GOVERNMENT_INSTITUTION,
    JUDICIAL_BODY,
    COURT,
    PROSECUTORIAL_BODY,
    POLICE_BODY,
    OFFICE,
    POSITION,
    BUSINESS,
    LOCATION,
    DOCUMENT,
    CASE,
    VEHICLE,
    PHONE,
    EMAIL,
    EVENT,
    UNKNOWN,
}

data class ExternalIdentifier(
    val namespace: String,
    val value: String,
)

/**
 * Directed, time-bounded relation between two entities.
 */
data class EntityRelation(
    val id: UUID,
    val subjectId: UUID,
    val relation: EntityRelationType,
    val objectId: UUID,

    val validFrom: Instant?,
    val validUntil: Instant?,

    val supportingEvidenceIds: List<UUID>,
    val assertionState: EvidenceAssertionState,
)

enum class EntityRelationType {
    EMPLOYED_BY,
    HELD_POSITION,
    MEMBER_OF,
    JURISDICTION_OVER,
    REPORTS_TO,
    AUTHORIZED_BY,
    LOCATED_AT,
    OWNED_BY,
    ASSOCIATED_WITH,
    RECEIVED,
    CREATED,
    SIGNED,
    REVIEWED,
    REFERRED,
    UNKNOWN,
}
