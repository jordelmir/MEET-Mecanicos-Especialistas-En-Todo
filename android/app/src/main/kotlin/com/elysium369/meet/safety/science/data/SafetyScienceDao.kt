package com.elysium369.meet.safety.science.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SafetyScienceDao {

    // ── Entities ────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntity(entity: SciEntityEntity)

    @Query("SELECT * FROM safety_scientific_entities WHERE id = :id")
    suspend fun getEntity(id: String): SciEntityEntity?

    @Query("SELECT * FROM safety_scientific_entities WHERE entityType = :type ORDER BY canonicalName")
    fun observeEntitiesByType(type: String): Flow<List<SciEntityEntity>>

    @Query("SELECT * FROM safety_scientific_entities ORDER BY canonicalName")
    fun observeAllEntities(): Flow<List<SciEntityEntity>>

    @Query("SELECT * FROM safety_scientific_entities WHERE canonicalName LIKE '%' || :query || '%' ORDER BY canonicalName LIMIT 50")
    suspend fun searchEntities(query: String): List<SciEntityEntity>

    // ── Entity Relations ────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntityRelation(relation: SciEntityRelationEntity)

    @Query("SELECT * FROM safety_scientific_entity_relations WHERE subjectId = :entityId OR objectId = :entityId ORDER BY createdAt DESC")
    fun observeRelationsForEntity(entityId: String): Flow<List<SciEntityRelationEntity>>

    // ── Claims ──────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClaim(claim: SciClaimEntity)

    @Query("SELECT * FROM safety_scientific_claims WHERE id = :id")
    suspend fun getClaim(id: String): SciClaimEntity?

    @Query("SELECT * FROM safety_scientific_claims ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecentClaims(limit: Int = 100): Flow<List<SciClaimEntity>>

    @Query("SELECT * FROM safety_scientific_claims WHERE subjectEntityId = :entityId OR objectEntityId = :entityId ORDER BY updatedAt DESC")
    fun observeClaimsForEntity(entityId: String): Flow<List<SciClaimEntity>>

    // ── Claim ↔ Evidence ────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClaimEvidence(link: SciClaimEvidenceEntity)

    @Query("SELECT * FROM safety_scientific_claim_evidence WHERE claimId = :claimId")
    suspend fun getEvidenceForClaim(claimId: String): List<SciClaimEvidenceEntity>

    // ── Claim ↔ Claim Relations ─────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClaimRelation(relation: SciClaimRelationEntity)

    @Query("SELECT * FROM safety_scientific_claim_relations WHERE sourceClaimId = :claimId OR targetClaimId = :claimId ORDER BY createdAt DESC")
    suspend fun getClaimRelations(claimId: String): List<SciClaimRelationEntity>

    // ── Events ──────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: SciEventEntity)

    /**
     * Persist one report's local scientific projection atomically.
     * This is NOT the remote scientific outbox and must not be shown as remote acknowledgement.
     */
    @Transaction
    suspend fun persistReportProjection(
        claim: SciClaimEntity,
        hypothesis: SciHypothesisEntity?,
        event: SciEventEntity,
        evidenceLinks: List<SciClaimEvidenceEntity>,
        provenanceNodes: List<SciProvenanceNodeEntity>,
        provenanceEdges: List<SciProvenanceEdgeEntity>,
    ) {
        upsertClaim(claim)
        hypothesis?.let { upsertHypothesis(it) }
        upsertEvent(event)
        evidenceLinks.forEach { upsertClaimEvidence(it) }
        provenanceNodes.forEach { upsertProvenanceNode(it) }
        provenanceEdges.forEach { upsertProvenanceEdge(it) }
    }

    @Query("SELECT * FROM safety_scientific_events ORDER BY COALESCE(occurredAt, recordedAt) DESC LIMIT :limit")
    fun observeRecentEvents(limit: Int = 100): Flow<List<SciEventEntity>>

    // ── Knowledge Events ────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKnowledgeEvent(event: SciKnowledgeEventEntity)

    @Query("SELECT * FROM safety_scientific_knowledge_events WHERE actorEntityId = :actorId ORDER BY receivedAt DESC")
    fun observeKnowledgeForActor(actorId: String): Flow<List<SciKnowledgeEventEntity>>

    // ── Authority ───────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAuthorityAssertion(assertion: SciAuthorityAssertionEntity)

    @Query("SELECT * FROM safety_scientific_authority_assertions WHERE actorEntityId = :actorId ORDER BY createdAt DESC")
    fun observeAuthorityForActor(actorId: String): Flow<List<SciAuthorityAssertionEntity>>

    // ── Duty ────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDutyAssertion(assertion: SciDutyAssertionEntity)

    @Query("SELECT * FROM safety_scientific_duty_assertions WHERE actorEntityId = :actorId ORDER BY createdAt DESC")
    fun observeDutyForActor(actorId: String): Flow<List<SciDutyAssertionEntity>>

    // ── Accountability ──────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccountabilityAction(action: SciAccountabilityActionEntity)

    @Query("SELECT * FROM safety_scientific_accountability_actions WHERE actorEntityId = :actorId ORDER BY occurredAt DESC")
    fun observeAccountabilityForActor(actorId: String): Flow<List<SciAccountabilityActionEntity>>

    // ── Hypotheses ──────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHypothesis(hypothesis: SciHypothesisEntity)

    @Query("SELECT * FROM safety_scientific_hypotheses ORDER BY updatedAt DESC")
    fun observeHypotheses(): Flow<List<SciHypothesisEntity>>

    // ── Provenance ──────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProvenanceNode(node: SciProvenanceNodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProvenanceEdge(edge: SciProvenanceEdgeEntity)

    @Query("SELECT * FROM safety_scientific_provenance_edges WHERE fromId = :nodeId OR toId = :nodeId")
    suspend fun getProvenanceEdges(nodeId: String): List<SciProvenanceEdgeEntity>

    // ── Research ────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDataset(dataset: SciResearchDatasetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertResearchRun(run: SciResearchRunEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReplication(replication: SciReplicationEntity)

    @Query("SELECT * FROM safety_scientific_replications ORDER BY createdAt DESC")
    fun observeReplications(): Flow<List<SciReplicationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPublication(publication: SciPublicationEntity)

    @Query("SELECT * FROM safety_scientific_publications ORDER BY updatedAt DESC")
    fun observePublications(): Flow<List<SciPublicationEntity>>

    // ── State Transitions (audit log) ───────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStateTransition(transition: SciStateTransitionEntity)

    @Query("SELECT * FROM safety_scientific_state_transitions WHERE subjectId = :subjectId ORDER BY occurredAt DESC")
    fun observeTransitionsForSubject(subjectId: String): Flow<List<SciStateTransitionEntity>>

    // ── Checkpoints ─────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCheckpoint(checkpoint: SciCheckpointEntity)

    @Query("SELECT * FROM safety_scientific_checkpoints ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestCheckpoint(): SciCheckpointEntity?
}
