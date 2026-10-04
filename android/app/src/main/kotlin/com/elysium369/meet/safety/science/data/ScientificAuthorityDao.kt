package com.elysium369.meet.safety.science.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for authority infrastructure: outbox, evidence bridge,
 * temporal integrity, source lineage, case aggregate.
 */
@Dao
interface ScientificAuthorityDao {

    // ── Command Outbox ──────────────────────────────────────────

    @Upsert
    suspend fun upsertCommand(command: ScientificCommandEntity)

    @Query("SELECT * FROM safety_scientific_command_outbox WHERE status = 'PENDING' ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getPendingCommands(limit: Int = 50): List<ScientificCommandEntity>

    @Query("SELECT * FROM safety_scientific_command_outbox WHERE status = 'RETRYABLE_FAILURE' AND nextAttemptAt <= :now ORDER BY nextAttemptAt ASC LIMIT :limit")
    suspend fun getRetryableCommands(now: Long, limit: Int = 20): List<ScientificCommandEntity>

    @Query("UPDATE safety_scientific_command_outbox SET status = :status, lastError = :error, attemptCount = attemptCount + 1, nextAttemptAt = :nextAttempt WHERE id = :id")
    suspend fun updateCommandStatus(id: String, status: String, error: String?, nextAttempt: Long)

    @Query("UPDATE safety_scientific_command_outbox SET status = 'ACKNOWLEDGED', serverVersion = :serverVersion, completedAt = :completedAt WHERE id = :id")
    suspend fun acknowledgeCommand(id: String, serverVersion: Long?, completedAt: Long)

    @Query("SELECT COUNT(*) FROM safety_scientific_command_outbox WHERE status IN ('PENDING', 'IN_FLIGHT', 'RETRYABLE_FAILURE')")
    fun observePendingCount(): Flow<Int>

    // ── Evidence References ─────────────────────────────────────

    @Upsert
    suspend fun upsertEvidenceReference(ref: SciEvidenceReferenceEntity)

    @Query("SELECT * FROM safety_scientific_evidence_references WHERE scientificObjectId = :objectId")
    suspend fun getEvidenceReferences(objectId: String): List<SciEvidenceReferenceEntity>

    @Query("SELECT * FROM safety_scientific_evidence_references WHERE evidenceId = :evidenceId")
    suspend fun getReferencesForEvidence(evidenceId: String): List<SciEvidenceReferenceEntity>

    @Query("SELECT * FROM safety_scientific_evidence_references WHERE verificationState = 'HASH_MISMATCH'")
    suspend fun getMismatchedReferences(): List<SciEvidenceReferenceEntity>

    @Query("SELECT * FROM safety_scientific_evidence_references WHERE verificationState = 'WITHDRAWN'")
    suspend fun getWithdrawnReferences(): List<SciEvidenceReferenceEntity>

    // ── Temporal Integrity ──────────────────────────────────────

    @Upsert
    suspend fun upsertTemporalIntegrity(entry: SciTemporalIntegrityEntity)

    @Query("SELECT * FROM safety_scientific_temporal_integrity WHERE subjectId = :subjectId")
    suspend fun getTemporalIntegrity(subjectId: String): SciTemporalIntegrityEntity?

    @Query("SELECT * FROM safety_scientific_temporal_integrity WHERE temporalState != 'CONSISTENT'")
    suspend fun getInconsistentTimestamps(): List<SciTemporalIntegrityEntity>

    // ── Source Lineage ──────────────────────────────────────────

    @Upsert
    suspend fun upsertSourceLineage(lineage: SciSourceLineageEntity)

    @Query("SELECT * FROM safety_scientific_source_lineage WHERE sourceLineageId = :lineageId")
    suspend fun getSourcesByLineage(lineageId: String): List<SciSourceLineageEntity>

    @Query("SELECT COUNT(DISTINCT sourceLineageId) FROM safety_scientific_source_lineage")
    suspend fun countIndependentSources(): Int

    // ── Cases ───────────────────────────────────────────────────

    @Upsert
    suspend fun upsertCase(case_: SciCaseEntity)

    @Query("SELECT * FROM safety_scientific_cases ORDER BY updatedAt DESC")
    fun observeCases(): Flow<List<SciCaseEntity>>

    @Query("SELECT * FROM safety_scientific_cases WHERE id = :caseId")
    suspend fun getCase(caseId: String): SciCaseEntity?

    @Upsert
    suspend fun upsertCaseItem(item: SciCaseItemEntity)

    @Query("SELECT * FROM safety_scientific_case_items WHERE caseId = :caseId ORDER BY addedAt DESC")
    suspend fun getCaseItems(caseId: String): List<SciCaseItemEntity>

    @Query("SELECT COUNT(*) FROM safety_scientific_case_items WHERE caseId = :caseId AND itemType = :type")
    suspend fun countCaseItemsByType(caseId: String, type: String): Int
}
