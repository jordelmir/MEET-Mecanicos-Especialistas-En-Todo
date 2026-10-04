package com.elysium369.meet.safety.science.data

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase implementation of SafetyScientificGateway.
 *
 * Currently a stub that returns failures — the outbox worker
 * will retry with exponential backoff until the Supabase RPCs
 * are deployed and this implementation is connected.
 *
 * TODO: Connect to Supabase RPCs:
 *   - safety_scientific_create_entity_v1
 *   - safety_scientific_create_claim_v1
 *   - safety_scientific_transition_claim_v1
 *   - etc.
 */
@Singleton
class SupabaseScientificGateway @Inject constructor() : SafetyScientificGateway {

    private fun notYetConnected(): Result<Nothing> =
        Result.failure(IllegalStateException("SUPABASE_GATEWAY_NOT_CONNECTED: RPC deployment pending"))

    override suspend fun createEntity(command: CreateEntityCommand) =
        notYetConnected()

    override suspend fun createClaim(command: CreateClaimCommand) =
        notYetConnected()

    override suspend fun transitionClaim(command: TransitionClaimCommand) =
        notYetConnected()

    override suspend fun attachEvidence(command: AttachEvidenceCommand) =
        notYetConnected()

    override suspend fun recordKnowledgeEvent(command: CreateKnowledgeEventCommand) =
        notYetConnected()

    override suspend fun createAuthorityAssertion(command: CreateAuthorityCommand) =
        notYetConnected()

    override suspend fun createDutyAssertion(command: CreateDutyCommand) =
        notYetConnected()

    override suspend fun createAccountabilityAction(command: CreateAccountabilityCommand) =
        notYetConnected()

    override suspend fun createHypothesis(command: CreateHypothesisCommand) =
        notYetConnected()

    override suspend fun createDataset(command: CreateDatasetCommand) =
        notYetConnected()

    override suspend fun submitResearchRun(command: CreateResearchRunCommand) =
        notYetConnected()

    override suspend fun submitReplication(command: CreateReplicationCommand) =
        notYetConnected()

    override suspend fun submitPeerReview(command: CreatePeerReviewCommand) =
        notYetConnected()

    override suspend fun requestPublication(command: RequestPublicationCommand) =
        notYetConnected()

    override suspend fun createCheckpoint(command: CreateCheckpointCommand) =
        notYetConnected()
}
