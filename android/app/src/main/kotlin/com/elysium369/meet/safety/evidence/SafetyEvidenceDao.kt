package com.elysium369.meet.safety.evidence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SafetyEvidenceDao {
    @Insert suspend fun insert(entity: SafetyEvidenceEntity)
    @Query("SELECT * FROM safety_evidence_local WHERE ownerUserId = :owner AND reportId = :reportId ORDER BY stagedAt")
    fun observe(owner: String, reportId: String): Flow<List<SafetyEvidenceEntity>>
    @Query("SELECT * FROM safety_evidence_local WHERE ownerUserId = :owner ORDER BY stagedAt")
    fun observeOwner(owner: String): Flow<List<SafetyEvidenceEntity>>
    @Query("SELECT * FROM safety_evidence_local WHERE ownerUserId = :owner AND uploadState IN ('STAGED', 'UPLOADING', 'UPLOADED', 'RETRY') ORDER BY stagedAt LIMIT 20")
    suspend fun pending(owner: String): List<SafetyEvidenceEntity>
    @Query("UPDATE safety_evidence_local SET uploadState = :state, attemptCount = :attempts, lastErrorCode = :error, serverReceipt = COALESCE(:receipt, serverReceipt) WHERE evidenceId = :id AND ownerUserId = :owner")
    suspend fun update(id: String, owner: String, state: String, attempts: Int, error: String?, receipt: String? = null)
    @Query("SELECT * FROM safety_evidence_local WHERE evidenceId = :id AND ownerUserId = :owner")
    suspend fun get(id: String, owner: String): SafetyEvidenceEntity?
    @Query("DELETE FROM safety_evidence_local WHERE evidenceId = :id AND ownerUserId = :owner AND uploadState = 'STAGED'")
    suspend fun removeDraft(id: String, owner: String): Int
}
