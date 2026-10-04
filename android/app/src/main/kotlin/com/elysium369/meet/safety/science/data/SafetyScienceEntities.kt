package com.elysium369.meet.safety.science.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ══════════════════════════════════════════════════════════════════════
// Room entities for Safety Scientific Core — mirrors Supabase schema.
// These are LOCAL persistence only. Server sync is separate.
// ══════════════════════════════════════════════════════════════════════

@Entity(
    tableName = "safety_scientific_entities",
    indices = [
        Index(value = ["entityType"]),
        Index(value = ["canonicalName"]),
    ],
)
data class SciEntityEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val canonicalName: String,
    val aliasesJson: String = "[]",
    val externalIdentifiersJson: String = "[]",
    val assertionState: String = "UNKNOWN",
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "safety_scientific_entity_relations",
    indices = [
        Index(value = ["subjectId"]),
        Index(value = ["objectId"]),
    ],
)
data class SciEntityRelationEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val relationType: String,
    val objectId: String,
    val validFrom: Long? = null,
    val validUntil: Long? = null,
    val supportingEvidenceIdsJson: String = "[]",
    val assertionState: String = "UNKNOWN",
    val createdAt: Long,
)

@Entity(
    tableName = "safety_scientific_claims",
    indices = [
        Index(value = ["subjectEntityId"]),
        Index(value = ["objectEntityId"]),
        Index(value = ["assertionState"]),
    ],
)
data class SciClaimEntity(
    @PrimaryKey val id: String,
    val proposition: String,
    val subjectEntityId: String? = null,
    val predicate: String,
    val objectEntityId: String? = null,
    val occurredAt: Long? = null,
    val knownAt: Long? = null,
    val validFrom: Long? = null,
    val validUntil: Long? = null,
    val assertionState: String = "UNKNOWN",
    val causalStatus: String = "NOT_ASSESSED",
    val methodologyVersion: String = "safety-science-v1",
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "safety_scientific_claim_evidence",
    primaryKeys = ["claimId", "evidenceId"],
)
data class SciClaimEvidenceEntity(
    val claimId: String,
    val evidenceId: String,
    val relationType: String, // SUPPORTS | CONTRADICTS | CONTEXTUALIZES
    val createdAt: Long,
)

@Entity(
    tableName = "safety_scientific_claim_relations",
    indices = [
        Index(value = ["sourceClaimId"]),
        Index(value = ["targetClaimId"]),
    ],
)
data class SciClaimRelationEntity(
    @PrimaryKey val id: String,
    val sourceClaimId: String,
    val targetClaimId: String,
    val relationType: String,
    val createdAt: Long,
)

@Entity(
    tableName = "safety_scientific_events",
    indices = [
        Index(value = ["occurredAt"]),
        Index(value = ["eventType"]),
    ],
)
data class SciEventEntity(
    @PrimaryKey val id: String,
    val eventType: String,
    val occurredAt: Long? = null,
    val knownAt: Long? = null,
    val recordedAt: Long,
    val publishedAt: Long? = null,
    val verifiedAt: Long? = null,
    val actorEntityIdsJson: String = "[]",
    val locationEntityId: String? = null,
    val evidenceIdsJson: String = "[]",
    val claimIdsJson: String = "[]",
    val assertionState: String = "UNKNOWN",
    val createdAt: Long,
)

@Entity(
    tableName = "safety_scientific_knowledge_events",
    indices = [
        Index(value = ["actorEntityId"]),
        Index(value = ["receivedAt"]),
    ],
)
data class SciKnowledgeEventEntity(
    @PrimaryKey val id: String,
    val actorEntityId: String,
    val informationClaimId: String,
    val receivedAt: Long,
    val channel: String,
    val sourceEntityId: String? = null,
    val authorityContextId: String? = null,
    val assertionState: String = "UNKNOWN",
    val createdAt: Long,
)

@Entity(tableName = "safety_scientific_authority_assertions")
data class SciAuthorityAssertionEntity(
    @PrimaryKey val id: String,
    val actorEntityId: String,
    val authorityType: String,
    val jurisdictionEntityId: String? = null,
    val validFrom: Long? = null,
    val validUntil: Long? = null,
    val sourceEvidenceIdsJson: String = "[]",
    val assertionState: String = "UNKNOWN",
    val createdAt: Long,
)

@Entity(tableName = "safety_scientific_duty_assertions")
data class SciDutyAssertionEntity(
    @PrimaryKey val id: String,
    val actorEntityId: String,
    val dutyType: String,
    val jurisdictionEntityId: String? = null,
    val validFrom: Long? = null,
    val validUntil: Long? = null,
    val legalSourceEvidenceIdsJson: String = "[]",
    val assertionState: String = "UNKNOWN",
    val createdAt: Long,
)

@Entity(tableName = "safety_scientific_accountability_actions")
data class SciAccountabilityActionEntity(
    @PrimaryKey val id: String,
    val actorEntityId: String,
    val actionKind: String, // ACTION | NON_ACTION
    val actionType: String,
    val expectedAction: String? = null,
    val occurredAt: Long,
    val evidenceIdsJson: String = "[]",
    val assertionState: String = "UNKNOWN",
    val createdAt: Long,
)

@Entity(
    tableName = "safety_scientific_hypotheses",
    indices = [Index(value = ["status"])],
)
data class SciHypothesisEntity(
    @PrimaryKey val id: String,
    val proposition: String,
    val nullHypothesis: String? = null,
    val supportingEvidenceIdsJson: String = "[]",
    val contradictingEvidenceIdsJson: String = "[]",
    val alternativeHypothesisIdsJson: String = "[]",
    val falsificationCriteriaJson: String = "[]",
    val status: String = "PROPOSED",
    val methodologyVersion: String = "safety-science-v1",
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "safety_scientific_provenance_nodes",
    indices = [Index(value = ["nodeType"])],
)
data class SciProvenanceNodeEntity(
    @PrimaryKey val id: String,
    val nodeType: String,
    val contentHash: String? = null,
    val createdAt: Long,
)

@Entity(
    tableName = "safety_scientific_provenance_edges",
    primaryKeys = ["fromId", "toId", "relation"],
)
data class SciProvenanceEdgeEntity(
    val fromId: String,
    val toId: String,
    val relation: String,
    val createdAt: Long,
)

@Entity(tableName = "safety_scientific_research_datasets")
data class SciResearchDatasetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    val evidenceIdsJson: String = "[]",
    val claimIdsJson: String = "[]",
    val eventIdsJson: String = "[]",
    val datasetHash: String,
    val methodologyVersion: String = "safety-science-v1",
    val createdAt: Long,
)

@Entity(tableName = "safety_scientific_research_runs")
data class SciResearchRunEntity(
    @PrimaryKey val id: String,
    val datasetId: String,
    val datasetHash: String,
    val methodologyVersion: String,
    val codeCommit: String,
    val parametersJson: String = "{}",
    val resultArtifactHash: String,
    val biasAssessmentJson: String? = null,
    val createdAt: Long,
)

@Entity(tableName = "safety_scientific_replications")
data class SciReplicationEntity(
    @PrimaryKey val id: String,
    val originalRunId: String,
    val replicatorEntityId: String,
    val institutionEntityId: String? = null,
    val datasetVersion: String,
    val methodologyVersion: String,
    val independentDatasetHash: String? = null,
    val result: String,
    val deviationsJson: String = "[]",
    val createdAt: Long,
)

@Entity(tableName = "safety_scientific_publications")
data class SciPublicationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val abstractText: String,
    val researchRunId: String,
    val datasetHash: String,
    val methodologyHash: String,
    val codeCommit: String,
    val evidenceManifestHash: String,
    val limitationsJson: String = "[]",
    val sensitivity: String = "NORMAL",
    val supersedesPublicationId: String? = null,
    val status: String = "DRAFT",
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "safety_scientific_state_transitions")
data class SciStateTransitionEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val fromState: String,
    val toState: String,
    val reason: String,
    val evidenceIdsJson: String = "[]",
    val actorId: String,
    val actorIsAi: Boolean = false,
    val methodologyVersion: String,
    val occurredAt: Long,
)

@Entity(tableName = "safety_scientific_checkpoints")
data class SciCheckpointEntity(
    @PrimaryKey val checkpointId: String,
    val rootHash: String,
    val eventCount: Long,
    val firstEventHash: String? = null,
    val lastEventHash: String? = null,
    val signatureAlgorithm: String,
    val signatureBase64: String,
    val createdAt: Long,
)
