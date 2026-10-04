package com.elysium369.meet.safety.science.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production Supabase implementation of SafetyScientificGateway.
 *
 * Routes commands to PostgreSQL RPC functions via PostgREST.
 * Server derives actor from auth.uid() — Android NEVER sends actorId.
 * Every command carries idempotencyKey for AT_LEAST_ONCE.
 *
 * Architecture:
 *   Android → SupabaseScientificGateway → PostgREST → PostgreSQL RPC
 *                                                      ↓
 *                                              auth.uid() derived
 *                                              invariants validated
 *                                              timestamps generated
 *                                              audit appended
 */
@Singleton
class SupabaseScientificGateway @Inject constructor(
    private val supabase: SupabaseClient,
) : SafetyScientificGateway {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // ── Entity ──────────────────────────────────────────────────

    override suspend fun createEntity(command: CreateEntityCommand): Result<ServerEntityResponse> =
        rpcTyped("safety_scientific_create_entity_v1", buildJsonObject {
            put("p_idempotency_key", command.idempotencyKey)
            put("p_entity_type", command.entityType)
            put("p_canonical_name", command.canonicalName)
            put("p_aliases", JsonArray(command.aliases.map { JsonPrimitive(it) }))
        })

    // ── Claim ───────────────────────────────────────────────────

    override suspend fun createClaim(command: CreateClaimCommand): Result<ServerClaimResponse> =
        rpcTyped("safety_scientific_create_claim_v1", buildJsonObject {
            put("p_idempotency_key", command.idempotencyKey)
            put("p_proposition", command.proposition)
            put("p_predicate", command.predicate)
            command.subjectEntityId?.let { put("p_subject_entity_id", it) }
            command.objectEntityId?.let { put("p_object_entity_id", it) }
            command.occurredAt?.let { put("p_occurred_at", it) }
            command.knownAt?.let { put("p_known_at", it) }
        })

    override suspend fun transitionClaim(command: TransitionClaimCommand): Result<ServerTransitionResponse> =
        rpcTyped("safety_scientific_transition_claim_v1", buildJsonObject {
            put("p_claim_id", command.claimId)
            put("p_to_state", command.toState)
            put("p_reason", command.reason)
            put("p_evidence_ids", JsonArray(command.evidenceIds.map { JsonPrimitive(it) }))
            put("p_actor_is_ai", command.actorIsAi)
            put("p_methodology_version", command.methodologyVersion)
            command.expectedVersion?.let { put("p_expected_version", it) }
            put("p_idempotency_key", command.idempotencyKey)
        })

    // ── Evidence ────────────────────────────────────────────────

    override suspend fun attachEvidence(command: AttachEvidenceCommand): Result<ServerAckResponse> =
        rpcTyped("safety_scientific_attach_evidence_v1", buildJsonObject {
            put("p_idempotency_key", command.idempotencyKey)
            put("p_claim_id", command.claimId)
            put("p_evidence_id", command.evidenceId)
            put("p_relation_type", command.relationType)
        })

    // ── Knowledge Events ────────────────────────────────────────

    override suspend fun recordKnowledgeEvent(command: CreateKnowledgeEventCommand): Result<ServerAckResponse> =
        rpcTyped("safety_scientific_create_knowledge_event_v1", buildJsonObject {
            put("p_idempotency_key", command.idempotencyKey)
            put("p_actor_entity_id", command.actorEntityId)
            put("p_information_claim_id", command.informationClaimId)
            put("p_received_at", command.receivedAt)
            put("p_channel", command.channel)
            command.sourceEntityId?.let { put("p_source_entity_id", it) }
        })

    // ── Authority / Duty ────────────────────────────────────────

    override suspend fun createAuthorityAssertion(command: CreateAuthorityCommand): Result<ServerAckResponse> =
        rpcTyped("safety_scientific_create_authority_v1", buildJsonObject {
            put("p_idempotency_key", command.idempotencyKey)
            put("p_actor_entity_id", command.actorEntityId)
            put("p_authority_type", command.authorityType)
            command.jurisdictionEntityId?.let { put("p_jurisdiction_entity_id", it) }
            command.validFrom?.let { put("p_valid_from", it) }
            command.validUntil?.let { put("p_valid_until", it) }
            put("p_source_evidence_ids", JsonArray(command.sourceEvidenceIds.map { JsonPrimitive(it) }))
        })

    override suspend fun createDutyAssertion(command: CreateDutyCommand): Result<ServerAckResponse> =
        rpcTyped("safety_scientific_create_duty_v1", buildJsonObject {
            put("p_idempotency_key", command.idempotencyKey)
            put("p_actor_entity_id", command.actorEntityId)
            put("p_duty_type", command.dutyType)
            command.jurisdictionEntityId?.let { put("p_jurisdiction_entity_id", it) }
            command.validFrom?.let { put("p_valid_from", it) }
            command.validUntil?.let { put("p_valid_until", it) }
            put("p_legal_source_evidence_ids", JsonArray(command.legalSourceEvidenceIds.map { JsonPrimitive(it) }))
        })

    // ── Accountability ──────────────────────────────────────────

    override suspend fun createAccountabilityAction(command: CreateAccountabilityCommand): Result<ServerAckResponse> =
        rpcTyped("safety_scientific_create_accountability_v1", buildJsonObject {
            put("p_idempotency_key", command.idempotencyKey)
            put("p_actor_entity_id", command.actorEntityId)
            put("p_action_kind", command.actionKind)
            put("p_action_type", command.actionType)
            command.expectedAction?.let { put("p_expected_action", it) }
            put("p_occurred_at", command.occurredAt)
            put("p_evidence_ids", JsonArray(command.evidenceIds.map { JsonPrimitive(it) }))
        })

    // ── Hypothesis ──────────────────────────────────────────────

    override suspend fun createHypothesis(command: CreateHypothesisCommand): Result<ServerHypothesisResponse> =
        rpcTyped("safety_scientific_create_hypothesis_v1", buildJsonObject {
            put("p_idempotency_key", command.idempotencyKey)
            put("p_proposition", command.proposition)
            command.nullHypothesis?.let { put("p_null_hypothesis", it) }
            put("p_falsification_criteria", JsonArray(command.falsificationCriteria.map { JsonPrimitive(it) }))
        })

    // ── Research ────────────────────────────────────────────────

    override suspend fun createDataset(command: CreateDatasetCommand): Result<ServerAckResponse> =
        rpcTyped("safety_scientific_create_dataset_v1", buildJsonObject {
            put("p_idempotency_key", command.idempotencyKey)
            put("p_name", command.name)
            put("p_version", command.version)
            put("p_evidence_ids", JsonArray(command.evidenceIds.map { JsonPrimitive(it) }))
            put("p_claim_ids", JsonArray(command.claimIds.map { JsonPrimitive(it) }))
            put("p_event_ids", JsonArray(command.eventIds.map { JsonPrimitive(it) }))
            put("p_dataset_hash", command.datasetHash)
            put("p_methodology_version", command.methodologyVersion)
        })

    override suspend fun submitResearchRun(command: CreateResearchRunCommand): Result<ServerAckResponse> =
        rpcTyped("safety_scientific_create_research_run_v1", buildJsonObject {
            put("p_idempotency_key", command.idempotencyKey)
            put("p_dataset_id", command.datasetId)
            put("p_dataset_hash", command.datasetHash)
            put("p_methodology_version", command.methodologyVersion)
            put("p_code_commit", command.codeCommit)
            put("p_parameters", JsonObject(command.parameters.mapValues { JsonPrimitive(it.value) }))
            put("p_result_artifact_hash", command.resultArtifactHash)
            command.analysisPlanHash?.let { put("p_analysis_plan_hash", it) }
            command.environmentHash?.let { put("p_environment_hash", it) }
            command.randomSeed?.let { put("p_random_seed", it) }
        })

    override suspend fun submitReplication(command: CreateReplicationCommand): Result<ServerAckResponse> =
        rpcTyped("safety_scientific_create_replication_v1", buildJsonObject {
            put("p_idempotency_key", command.idempotencyKey)
            put("p_original_run_id", command.originalRunId)
            put("p_dataset_version", command.datasetVersion)
            put("p_methodology_version", command.methodologyVersion)
            command.independentDatasetHash?.let { put("p_independent_dataset_hash", it) }
            put("p_result", command.result)
            put("p_deviations", JsonArray(command.deviations.map { JsonPrimitive(it) }))
        })

    override suspend fun submitPeerReview(command: CreatePeerReviewCommand): Result<ServerAckResponse> =
        rpcTyped("safety_scientific_create_peer_review_v1", buildJsonObject {
            put("p_idempotency_key", command.idempotencyKey)
            put("p_publication_id", command.publicationId)
            put("p_verdict", command.verdict)
            put("p_comments", command.comments)
            command.conflictOfInterest?.let { put("p_conflict_of_interest", it) }
        })

    // ── Publication ─────────────────────────────────────────────

    override suspend fun requestPublication(command: RequestPublicationCommand): Result<ServerPublicationResponse> =
        rpcTyped("safety_scientific_request_publication_v1", buildJsonObject {
            put("p_idempotency_key", command.idempotencyKey)
            put("p_title", command.title)
            put("p_abstract_text", command.abstractText)
            put("p_research_run_id", command.researchRunId)
            put("p_dataset_hash", command.datasetHash)
            put("p_methodology_hash", command.methodologyHash)
            put("p_code_commit", command.codeCommit)
            put("p_evidence_manifest_hash", command.evidenceManifestHash)
            put("p_limitations", JsonArray(command.limitations.map { JsonPrimitive(it) }))
            put("p_sensitivity", command.sensitivity)
        })

    // ── Checkpoint ──────────────────────────────────────────────

    override suspend fun createCheckpoint(command: CreateCheckpointCommand): Result<ServerCheckpointResponse> =
        rpcTyped("safety_scientific_create_checkpoint_v1", buildJsonObject {
            put("p_idempotency_key", command.idempotencyKey)
            put("p_root_hash", command.rootHash)
            put("p_event_count", command.eventCount)
            command.firstEventHash?.let { put("p_first_event_hash", it) }
            command.lastEventHash?.let { put("p_last_event_hash", it) }
            put("p_signature_algorithm", command.signatureAlgorithm)
            put("p_signature_base64", command.signatureBase64)
        })

    // ── Internal RPC dispatch ───────────────────────────────────

    private suspend inline fun <reified T> rpcTyped(
        functionName: String,
        params: JsonObject,
    ): Result<T> = runCatching {
        val response = supabase.postgrest.rpc(functionName, params)
        val body = response.data
        json.decodeFromString<T>(body)
    }
}
