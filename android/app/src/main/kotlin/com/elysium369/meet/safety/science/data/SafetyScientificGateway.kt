package com.elysium369.meet.safety.science.data

import kotlinx.serialization.Serializable

// ═══════════════════════════════════════════════════════════════════
// BLOQUE 1 — SCIENTIFIC GATEWAY
//
// Room is LOCAL CACHE / OFFLINE PROJECTION.
// Postgres is AUTHORITATIVE SERVER STATE.
// SERVER AUTHORITY MUST NEVER TRUST ANDROID CLAIMS.
//
//   Android
//   ├── Room = cache/offline projection
//   └── ScientificCommandOutbox
//        ▼
//   Supabase RPC
//        ▼
//   PostgreSQL authority
//        ├── audit
//        ├── state transition
//        ├── custody
//        └── publication authority
// ═══════════════════════════════════════════════════════════════════

/**
 * Remote authority gateway for scientific operations.
 *
 * Every operation goes through Supabase RPCs.
 * Android NEVER writes authoritative state directly.
 * Server derives actor from auth.uid().
 * Server validates all invariants.
 * Server generates timestamps.
 */
interface SafetyScientificGateway {

    // ── Entity Operations ──────────────────────────────────────
    suspend fun createEntity(
        command: CreateEntityCommand,
    ): Result<ServerEntityResponse>

    // ── Claim Operations ───────────────────────────────────────
    suspend fun createClaim(
        command: CreateClaimCommand,
    ): Result<ServerClaimResponse>

    suspend fun transitionClaim(
        command: TransitionClaimCommand,
    ): Result<ServerTransitionResponse>

    // ── Evidence Operations ────────────────────────────────────
    suspend fun attachEvidence(
        command: AttachEvidenceCommand,
    ): Result<ServerAckResponse>

    // ── Knowledge Events ───────────────────────────────────────
    suspend fun recordKnowledgeEvent(
        command: CreateKnowledgeEventCommand,
    ): Result<ServerAckResponse>

    // ── Authority / Duty ───────────────────────────────────────
    suspend fun createAuthorityAssertion(
        command: CreateAuthorityCommand,
    ): Result<ServerAckResponse>

    suspend fun createDutyAssertion(
        command: CreateDutyCommand,
    ): Result<ServerAckResponse>

    // ── Accountability ─────────────────────────────────────────
    suspend fun createAccountabilityAction(
        command: CreateAccountabilityCommand,
    ): Result<ServerAckResponse>

    // ── Hypothesis ─────────────────────────────────────────────
    suspend fun createHypothesis(
        command: CreateHypothesisCommand,
    ): Result<ServerHypothesisResponse>

    // ── Research ───────────────────────────────────────────────
    suspend fun createDataset(
        command: CreateDatasetCommand,
    ): Result<ServerAckResponse>

    suspend fun submitResearchRun(
        command: CreateResearchRunCommand,
    ): Result<ServerAckResponse>

    suspend fun submitReplication(
        command: CreateReplicationCommand,
    ): Result<ServerAckResponse>

    suspend fun submitPeerReview(
        command: CreatePeerReviewCommand,
    ): Result<ServerAckResponse>

    // ── Publication ────────────────────────────────────────────
    suspend fun requestPublication(
        command: RequestPublicationCommand,
    ): Result<ServerPublicationResponse>

    // ── Checkpoint ─────────────────────────────────────────────
    suspend fun createCheckpoint(
        command: CreateCheckpointCommand,
    ): Result<ServerCheckpointResponse>
}

// ═══════════════════════════════════════════════════════════════════
// Commands — sent via outbox to server RPCs
// All include idempotencyKey for AT_LEAST_ONCE delivery
// ═══════════════════════════════════════════════════════════════════

@Serializable
data class CreateEntityCommand(
    val idempotencyKey: String,
    val entityType: String,
    val canonicalName: String,
    val aliases: List<String> = emptyList(),
)

@Serializable
data class CreateClaimCommand(
    val idempotencyKey: String,
    val proposition: String,
    val predicate: String,
    val subjectEntityId: String? = null,
    val objectEntityId: String? = null,
    val occurredAt: Long? = null,
    val knownAt: Long? = null,
)

@Serializable
data class TransitionClaimCommand(
    val idempotencyKey: String,
    val claimId: String,
    val toState: String,
    val reason: String,
    val evidenceIds: List<String>,
    val actorIsAi: Boolean = false,
    val methodologyVersion: String = "safety-science-v1",
    val expectedVersion: Long? = null,
)

@Serializable
data class AttachEvidenceCommand(
    val idempotencyKey: String,
    val claimId: String,
    val evidenceId: String,
    val relationType: String,
)

@Serializable
data class CreateKnowledgeEventCommand(
    val idempotencyKey: String,
    val actorEntityId: String,
    val informationClaimId: String,
    val receivedAt: Long,
    val channel: String,
    val sourceEntityId: String? = null,
)

@Serializable
data class CreateAuthorityCommand(
    val idempotencyKey: String,
    val actorEntityId: String,
    val authorityType: String,
    val jurisdictionEntityId: String? = null,
    val validFrom: Long? = null,
    val validUntil: Long? = null,
    val sourceEvidenceIds: List<String> = emptyList(),
)

@Serializable
data class CreateDutyCommand(
    val idempotencyKey: String,
    val actorEntityId: String,
    val dutyType: String,
    val jurisdictionEntityId: String? = null,
    val validFrom: Long? = null,
    val validUntil: Long? = null,
    val legalSourceEvidenceIds: List<String> = emptyList(),
)

@Serializable
data class CreateAccountabilityCommand(
    val idempotencyKey: String,
    val actorEntityId: String,
    val actionKind: String, // ACTION or NON_ACTION
    val actionType: String,
    val expectedAction: String? = null,
    val occurredAt: Long,
    val evidenceIds: List<String> = emptyList(),
)

@Serializable
data class CreateHypothesisCommand(
    val idempotencyKey: String,
    val proposition: String,
    val nullHypothesis: String? = null,
    val falsificationCriteria: List<String> = emptyList(),
)

@Serializable
data class CreateDatasetCommand(
    val idempotencyKey: String,
    val name: String,
    val version: String,
    val evidenceIds: List<String> = emptyList(),
    val claimIds: List<String> = emptyList(),
    val eventIds: List<String> = emptyList(),
    val datasetHash: String,
    val methodologyVersion: String = "safety-science-v1",
)

@Serializable
data class CreateResearchRunCommand(
    val idempotencyKey: String,
    val datasetId: String,
    val datasetHash: String,
    val methodologyVersion: String,
    val codeCommit: String,
    val parameters: Map<String, String> = emptyMap(),
    val resultArtifactHash: String,
    val analysisPlanHash: String? = null,
    val environmentHash: String? = null,
    val randomSeed: Long? = null,
)

@Serializable
data class CreateReplicationCommand(
    val idempotencyKey: String,
    val originalRunId: String,
    val datasetVersion: String,
    val methodologyVersion: String,
    val independentDatasetHash: String? = null,
    val result: String,
    val deviations: List<String> = emptyList(),
)

@Serializable
data class CreatePeerReviewCommand(
    val idempotencyKey: String,
    val publicationId: String,
    val verdict: String,
    val comments: String,
    val conflictOfInterest: String? = null,
)

@Serializable
data class RequestPublicationCommand(
    val idempotencyKey: String,
    val title: String,
    val abstractText: String,
    val researchRunId: String,
    val datasetHash: String,
    val methodologyHash: String,
    val codeCommit: String,
    val evidenceManifestHash: String,
    val limitations: List<String> = emptyList(),
    val sensitivity: String = "NORMAL",
)

@Serializable
data class CreateCheckpointCommand(
    val idempotencyKey: String,
    val rootHash: String,
    val eventCount: Int,
    val firstEventHash: String?,
    val lastEventHash: String?,
    val signatureAlgorithm: String,
    val signatureBase64: String,
)

// ═══════════════════════════════════════════════════════════════════
// Server Responses
// ═══════════════════════════════════════════════════════════════════

@Serializable
data class ServerAckResponse(
    val status: String, // CREATED, ALREADY_EXISTS
    val serverId: String? = null,
    val serverVersion: Long? = null,
)

@Serializable
data class ServerEntityResponse(
    val status: String,
    val entityId: String,
    val serverVersion: Long,
)

@Serializable
data class ServerClaimResponse(
    val status: String,
    val claimId: String,
    val serverVersion: Long,
)

@Serializable
data class ServerTransitionResponse(
    val status: String,
    val transitionId: String,
    val fromState: String,
    val toState: String,
    val serverVersion: Long,
)

@Serializable
data class ServerHypothesisResponse(
    val status: String,
    val hypothesisId: String,
    val serverVersion: Long,
)

@Serializable
data class ServerPublicationResponse(
    val status: String, // DRAFT_CREATED, PUBLICATION_REQUESTED, ALREADY_EXISTS, INELIGIBLE
    val publicationId: String?,
    val serverVersion: Long?,
    val reason: String? = null,
)

@Serializable
data class ServerCheckpointResponse(
    val status: String,
    val checkpointId: String,
    val serverVersion: Long,
)
