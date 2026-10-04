package com.elysium369.meet.safety.science.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ═══════════════════════════════════════════════════════════════════
// BLOQUE 1 — SCIENTIFIC COMMAND OUTBOX
//
// Room is LOCAL CACHE / OFFLINE PROJECTION.
// Postgres is AUTHORITATIVE SERVER STATE.
//
// Pattern:
//   Android → Room outbox → WorkManager → Supabase RPC → Postgres
//   Postgres → projection → Room cache
//
// Delivery: AT_LEAST_ONCE → every command MUST be idempotent.
// ═══════════════════════════════════════════════════════════════════

@Entity(
    tableName = "safety_scientific_command_outbox",
    indices = [
        Index("status"),
        Index("nextAttemptAt"),
        Index("commandType"),
    ],
)
data class ScientificCommandEntity(
    @PrimaryKey val id: String,
    val commandId: String,    // idempotency key
    val actorId: String,
    val commandType: String,  // matches ScientificCommandType
    val payload: String,      // JSON
    val payloadHash: String,  // SHA-256 of payload
    val createdAt: Long,
    val attemptCount: Int = 0,
    val nextAttemptAt: Long = 0,
    val lastError: String? = null,
    val status: String = "PENDING",  // PENDING, IN_FLIGHT, ACKNOWLEDGED, RETRYABLE_FAILURE, PERMANENT_FAILURE, CONFLICT
    val serverVersion: Long? = null,
    val completedAt: Long? = null,
)

enum class ScientificCommandType {
    CREATE_ENTITY,
    CREATE_CLAIM,
    ATTACH_EVIDENCE,
    CREATE_EVENT,
    CREATE_KNOWLEDGE_EVENT,
    CREATE_AUTHORITY_ASSERTION,
    CREATE_DUTY_ASSERTION,
    CREATE_ACCOUNTABILITY_ACTION,
    CREATE_HYPOTHESIS,
    CREATE_DATASET,
    CREATE_RESEARCH_RUN,
    CREATE_REPLICATION,
    CREATE_PEER_REVIEW,
    REQUEST_PUBLICATION,
    TRANSITION_CLAIM,
    CREATE_CHECKPOINT,
}

enum class CommandStatus {
    PENDING,
    IN_FLIGHT,
    ACKNOWLEDGED,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE,
    CONFLICT,
}

// ═══════════════════════════════════════════════════════════════════
// BLOQUE 4 — EVIDENCE AUTHORITY BRIDGE
//
// Every scientific evidence reference MUST resolve to
// safety_evidence_objects and validate existence, hash,
// custody, verification, withdrawal, quarantine.
//
// Do NOT trust caller-provided verification state.
// ═══════════════════════════════════════════════════════════════════

@Entity(
    tableName = "safety_scientific_evidence_references",
    indices = [
        Index("evidenceId"),
        Index("scientificObjectId"),
    ],
)
data class SciEvidenceReferenceEntity(
    @PrimaryKey val id: String,
    val scientificObjectId: String, // claim/event/hypothesis that references this
    val scientificObjectType: String, // CLAIM, EVENT, HYPOTHESIS
    val evidenceId: String,          // FK → safety_evidence_objects
    val relation: String,            // SUPPORTS, CONTRADICTS, CONTEXTUALIZES
    val verificationState: String,   // PENDING, VERIFIED, HASH_MISMATCH, WITHDRAWN, QUARANTINED
    val evidenceHash: String?,       // expected SHA-256
    val createdAt: Long,
)

// ═══════════════════════════════════════════════════════════════════
// Phase 7 — TEMPORAL INTEGRITY
// ═══════════════════════════════════════════════════════════════════

@Entity(
    tableName = "safety_scientific_temporal_integrity",
    indices = [Index("subjectId")],
)
data class SciTemporalIntegrityEntity(
    @PrimaryKey val id: String,
    val subjectId: String,        // evidence/event/claim ID
    val subjectType: String,
    val deviceCapturedAt: Long?,
    val serverReceivedAt: Long,
    val serverVerifiedAt: Long?,
    val clockSkewMs: Long?,
    val temporalState: String,    // CONSISTENT, CLOCK_SKEW, FUTURE_DEVICE_TIME, MISSING_DEVICE_TIME, SERVER_AUTHORITATIVE
)

// ═══════════════════════════════════════════════════════════════════
// Phase 8 — SOURCE LINEAGE
// ═══════════════════════════════════════════════════════════════════

@Entity(
    tableName = "safety_scientific_source_lineage",
    indices = [
        Index("sourceLineageId"),
        Index("derivationParentId"),
    ],
)
data class SciSourceLineageEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val sourceLineageId: String,     // groups all instances of same underlying source
    val sourceInstanceId: String,    // this specific instance
    val derivationParentId: String?, // if derived from another source
    val derivationType: String,      // ORIGINAL, DERIVED, REPUBLICATION, INDEPENDENT_ACQUISITION, UNKNOWN
    val createdAt: Long,
)

// ═══════════════════════════════════════════════════════════════════
// Phase 9 — CASE AGGREGATE
// ═══════════════════════════════════════════════════════════════════

@Entity(
    tableName = "safety_scientific_cases",
    indices = [
        Index("status"),
        Index("sensitivity"),
    ],
)
data class SciCaseEntity(
    @PrimaryKey val id: String,
    val title: String,
    val jurisdiction: String?,
    val status: String,      // OPEN, EVIDENCE_COLLECTION, ANALYSIS, REPLICATION, UNDER_REVIEW, PUBLICATION_ELIGIBLE, PUBLISHED, REFERRED, CLOSED, DISPUTED
    val sensitivity: String, // NORMAL, SENSITIVE, HIGH_IMPACT, LEGAL_HOLD
    val evidenceCount: Int = 0,
    val claimCount: Int = 0,
    val hypothesisCount: Int = 0,
    val researchRunCount: Int = 0,
    val replicationCount: Int = 0,
    val publicationStatus: String?, // DRAFT, PUBLISHED, RETRACTED...
    val legalReferralStatus: String = "NOT_REFERRED", // NOT_REFERRED, REFERRED, ACKNOWLEDGED, IN_REVIEW
    val serverVersion: Long = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

// ═══════════════════════════════════════════════════════════════════
// Phase 9 — Case ↔ entity links
// ═══════════════════════════════════════════════════════════════════

@Entity(
    tableName = "safety_scientific_case_items",
    primaryKeys = ["caseId", "itemId", "itemType"],
)
data class SciCaseItemEntity(
    val caseId: String,
    val itemId: String,
    val itemType: String, // EVIDENCE, CLAIM, EVENT, HYPOTHESIS, ENTITY, RESEARCH_RUN, REPLICATION, PUBLICATION
    val addedAt: Long,
)
