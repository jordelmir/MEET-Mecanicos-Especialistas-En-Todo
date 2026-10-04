package com.elysium369.meet.safety.science.application

import com.elysium369.meet.safety.science.data.*
import com.elysium369.meet.safety.science.domain.AssertionStateMachine
import com.elysium369.meet.safety.science.domain.EvidenceAssertionState
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Business logic layer for safety-science operations (§54).
 *
 * Every state transition goes through [AssertionStateMachine].
 * Every mutation is append-only with audit trail.
 * AI cannot call promotion operations directly.
 */
@Singleton
class SafetyScienceRepository @Inject constructor(
    private val dao: SafetyScienceDao,
    private val stateMachine: AssertionStateMachine,
) {

    // ── Entity Operations ───────────────────────────────────────

    suspend fun createEntity(
        id: String = UUID.randomUUID().toString(),
        entityType: String,
        canonicalName: String,
        aliases: List<String> = emptyList(),
    ): SciEntityEntity {
        val now = System.currentTimeMillis()
        val entity = SciEntityEntity(
            id = id,
            entityType = entityType,
            canonicalName = canonicalName,
            aliasesJson = aliases.joinToString(",", "[", "]") { "\"$it\"" },
            assertionState = EvidenceAssertionState.OBSERVED.name,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertEntity(entity)
        return entity
    }

    fun observeEntitiesByType(type: String): Flow<List<SciEntityEntity>> =
        dao.observeEntitiesByType(type)

    suspend fun searchEntities(query: String): List<SciEntityEntity> =
        dao.searchEntities(query)

    // ── Claim Operations ────────────────────────────────────────

    /**
     * Create a claim. Initial state is always OBSERVED.
     * AI candidates must go through [promoteClaim] with evidence.
     */
    suspend fun createClaim(
        proposition: String,
        predicate: String,
        subjectEntityId: String? = null,
        objectEntityId: String? = null,
        occurredAt: Long? = null,
        knownAt: Long? = null,
    ): SciClaimEntity {
        val now = System.currentTimeMillis()
        val claim = SciClaimEntity(
            id = UUID.randomUUID().toString(),
            proposition = proposition,
            predicate = predicate,
            subjectEntityId = subjectEntityId,
            objectEntityId = objectEntityId,
            occurredAt = occurredAt,
            knownAt = knownAt,
            assertionState = EvidenceAssertionState.OBSERVED.name,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertClaim(claim)
        return claim
    }

    fun observeRecentClaims(limit: Int = 100): Flow<List<SciClaimEntity>> =
        dao.observeRecentClaims(limit)

    fun observeClaimsForEntity(entityId: String): Flow<List<SciClaimEntity>> =
        dao.observeClaimsForEntity(entityId)

    // ── Evidence Attachment ─────────────────────────────────────

    suspend fun attachEvidence(
        claimId: String,
        evidenceId: String,
        relationType: String, // SUPPORTS | CONTRADICTS | CONTEXTUALIZES
    ) {
        require(relationType in setOf("SUPPORTS", "CONTRADICTS", "CONTEXTUALIZES")) {
            "Invalid relation type: $relationType"
        }
        dao.upsertClaimEvidence(
            SciClaimEvidenceEntity(
                claimId = claimId,
                evidenceId = evidenceId,
                relationType = relationType,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    // ── Corroboration ───────────────────────────────────────────

    suspend fun recordCorroboration(
        sourceClaimId: String,
        targetClaimId: String,
        relationType: String, // SUPPORTS | CONTRADICTS
    ) {
        dao.upsertClaimRelation(
            SciClaimRelationEntity(
                id = UUID.randomUUID().toString(),
                sourceClaimId = sourceClaimId,
                targetClaimId = targetClaimId,
                relationType = relationType,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    // ── State Transitions (§54-§56) ─────────────────────────────

    /**
     * Promote a claim's assertion state.
     *
     * INVARIANTS:
     * - Must pass [AssertionStateMachine.canTransition]
     * - AI actors are blocked from upward transitions
     * - Every transition is audited
     */
    suspend fun transitionClaimState(
        claimId: String,
        toState: EvidenceAssertionState,
        reason: String,
        evidenceIds: List<String>,
        actorId: String,
        actorIsAi: Boolean = false,
    ): Boolean {
        val claim = dao.getClaim(claimId) ?: return false
        val fromState = EvidenceAssertionState.valueOf(claim.assertionState)

        val result = try {
            stateMachine.transition(
                subjectId = UUID.fromString(claimId),
                from = fromState,
                to = toState,
                reason = reason,
                evidenceIds = evidenceIds.map { UUID.fromString(it) },
                actorId = UUID.fromString(actorId),
                methodologyVersion = "safety-science-v1",
                actorIsAi = actorIsAi,
            )
        } catch (_: IllegalStateException) {
            return false
        } catch (_: IllegalArgumentException) {
            return false
        }

        // Update claim
        dao.upsertClaim(
            claim.copy(
                assertionState = toState.name,
                updatedAt = System.currentTimeMillis(),
            ),
        )

        // Audit log (append-only)
        dao.insertStateTransition(
            SciStateTransitionEntity(
                id = result.id.toString(),
                subjectId = claimId,
                fromState = result.from.name,
                toState = result.to.name,
                reason = result.reason,
                evidenceIdsJson = evidenceIds.joinToString(",", "[", "]") { "\"$it\"" },
                actorId = result.actorId.toString(),
                actorIsAi = actorIsAi,
                methodologyVersion = result.methodologyVersion,
                occurredAt = result.occurredAt.toEpochMilli(),
            ),
        )

        return true
    }

    // ── Knowledge Events (§6) ───────────────────────────────────

    suspend fun recordKnowledgeEvent(
        actorEntityId: String,
        informationClaimId: String,
        receivedAt: Long,
        channel: String,
        sourceEntityId: String? = null,
    ) {
        dao.upsertKnowledgeEvent(
            SciKnowledgeEventEntity(
                id = UUID.randomUUID().toString(),
                actorEntityId = actorEntityId,
                informationClaimId = informationClaimId,
                receivedAt = receivedAt,
                channel = channel,
                sourceEntityId = sourceEntityId,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    fun observeKnowledgeForActor(actorId: String): Flow<List<SciKnowledgeEventEntity>> =
        dao.observeKnowledgeForActor(actorId)

    // ── Authority (§7) ──────────────────────────────────────────

    suspend fun recordAuthority(
        actorEntityId: String,
        authorityType: String,
        jurisdictionEntityId: String? = null,
        validFrom: Long? = null,
        validUntil: Long? = null,
        sourceEvidenceIds: List<String> = emptyList(),
    ) {
        dao.upsertAuthorityAssertion(
            SciAuthorityAssertionEntity(
                id = UUID.randomUUID().toString(),
                actorEntityId = actorEntityId,
                authorityType = authorityType,
                jurisdictionEntityId = jurisdictionEntityId,
                validFrom = validFrom,
                validUntil = validUntil,
                sourceEvidenceIdsJson = sourceEvidenceIds.joinToString(",", "[", "]") { "\"$it\"" },
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    // ── Duty (§8) ───────────────────────────────────────────────

    suspend fun recordDuty(
        actorEntityId: String,
        dutyType: String,
        jurisdictionEntityId: String? = null,
        validFrom: Long? = null,
        validUntil: Long? = null,
        legalSourceEvidenceIds: List<String> = emptyList(),
    ) {
        dao.upsertDutyAssertion(
            SciDutyAssertionEntity(
                id = UUID.randomUUID().toString(),
                actorEntityId = actorEntityId,
                dutyType = dutyType,
                jurisdictionEntityId = jurisdictionEntityId,
                validFrom = validFrom,
                validUntil = validUntil,
                legalSourceEvidenceIdsJson = legalSourceEvidenceIds.joinToString(",", "[", "]") { "\"$it\"" },
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    // ── Accountability (§9) ─────────────────────────────────────

    suspend fun recordAction(
        actorEntityId: String,
        actionType: String,
        occurredAt: Long,
        evidenceIds: List<String> = emptyList(),
    ) {
        dao.upsertAccountabilityAction(
            SciAccountabilityActionEntity(
                id = UUID.randomUUID().toString(),
                actorEntityId = actorEntityId,
                actionKind = "ACTION",
                actionType = actionType,
                occurredAt = occurredAt,
                evidenceIdsJson = evidenceIds.joinToString(",", "[", "]") { "\"$it\"" },
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Record a documented non-action.
     * INVARIANT: NonAction ≠ IllegalOmission. The system NEVER makes that jump.
     */
    suspend fun recordNonAction(
        actorEntityId: String,
        expectedAction: String,
        occurredAt: Long,
        evidenceIds: List<String> = emptyList(),
    ) {
        dao.upsertAccountabilityAction(
            SciAccountabilityActionEntity(
                id = UUID.randomUUID().toString(),
                actorEntityId = actorEntityId,
                actionKind = "NON_ACTION",
                actionType = "DOCUMENTED_NON_ACTION",
                expectedAction = expectedAction,
                occurredAt = occurredAt,
                evidenceIdsJson = evidenceIds.joinToString(",", "[", "]") { "\"$it\"" },
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    // ── Hypotheses (§20) ────────────────────────────────────────

    suspend fun createHypothesis(
        proposition: String,
        nullHypothesis: String? = null,
        falsificationCriteria: List<String> = emptyList(),
    ): SciHypothesisEntity {
        val now = System.currentTimeMillis()
        val hypothesis = SciHypothesisEntity(
            id = UUID.randomUUID().toString(),
            proposition = proposition,
            nullHypothesis = nullHypothesis,
            falsificationCriteriaJson = falsificationCriteria.joinToString(",", "[", "]") { "\"$it\"" },
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertHypothesis(hypothesis)
        return hypothesis
    }

    fun observeHypotheses(): Flow<List<SciHypothesisEntity>> =
        dao.observeHypotheses()

    // ── Events (§10) ────────────────────────────────────────────

    suspend fun recordEvent(
        eventType: String,
        occurredAt: Long? = null,
        knownAt: Long? = null,
        actorEntityIds: List<String> = emptyList(),
        locationEntityId: String? = null,
    ): SciEventEntity {
        val now = System.currentTimeMillis()
        val event = SciEventEntity(
            id = UUID.randomUUID().toString(),
            eventType = eventType,
            occurredAt = occurredAt,
            knownAt = knownAt,
            recordedAt = now,
            actorEntityIdsJson = actorEntityIds.joinToString(",", "[", "]") { "\"$it\"" },
            locationEntityId = locationEntityId,
            createdAt = now,
        )
        dao.upsertEvent(event)
        return event
    }

    fun observeRecentEvents(limit: Int = 100): Flow<List<SciEventEntity>> =
        dao.observeRecentEvents(limit)

    // ── Transition History ──────────────────────────────────────

    fun observeTransitionsForSubject(subjectId: String): Flow<List<SciStateTransitionEntity>> =
        dao.observeTransitionsForSubject(subjectId)
}
