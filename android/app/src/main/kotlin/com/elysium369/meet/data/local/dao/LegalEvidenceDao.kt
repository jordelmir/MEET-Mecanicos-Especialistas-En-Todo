package com.elysium369.meet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.elysium369.meet.data.local.entities.LegalCaseEntity
import com.elysium369.meet.data.local.entities.LegalCustodyEventEntity
import com.elysium369.meet.data.local.entities.LegalEvidenceItemEntity
import com.elysium369.meet.data.local.entities.LegalExpenseEntity
import com.elysium369.meet.data.local.entities.LegalJournalEventEntity
import com.elysium369.meet.data.local.entities.LegalReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LegalEvidenceDao {
    @Query("SELECT * FROM legal_cases_local WHERE ownerPrincipalId = :ownerId ORDER BY updatedAtEpochMs DESC")
    fun observeCases(ownerId: String): Flow<List<LegalCaseEntity>>

    @Query("SELECT * FROM legal_journal_events WHERE ownerPrincipalId = :ownerId AND tombstonedAtEpochMs IS NULL ORDER BY occurredAtEpochMs DESC, capturedAtEpochMs DESC")
    fun observeTimeline(ownerId: String): Flow<List<LegalJournalEventEntity>>

    @Query("SELECT * FROM legal_evidence_items WHERE ownerPrincipalId = :ownerId ORDER BY capturedAtEpochMs DESC")
    fun observeEvidence(ownerId: String): Flow<List<LegalEvidenceItemEntity>>

    @Query("SELECT * FROM legal_journal_events WHERE ownerPrincipalId = :ownerId AND eventId = :eventId LIMIT 1")
    suspend fun getJournalEvent(ownerId: String, eventId: String): LegalJournalEventEntity?

    @Query("SELECT * FROM legal_evidence_items WHERE ownerPrincipalId = :ownerId AND evidenceId = :evidenceId LIMIT 1")
    suspend fun getEvidence(ownerId: String, evidenceId: String): LegalEvidenceItemEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCase(legalCase: LegalCaseEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertJournalEvent(event: LegalJournalEventEntity)

    @Query("UPDATE legal_journal_events SET tombstonedAtEpochMs = :deletedAt WHERE ownerPrincipalId = :ownerId AND eventId = :eventId AND tombstonedAtEpochMs IS NULL")
    suspend fun tombstoneJournalEvent(ownerId: String, eventId: String, deletedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvidence(evidence: LegalEvidenceItemEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCustodyEvent(event: LegalCustodyEventEntity)

    @Query("SELECT * FROM legal_custody_events WHERE ownerPrincipalId = :ownerId AND evidenceId = :evidenceId ORDER BY occurredAtEpochMs DESC LIMIT 1")
    suspend fun latestCustodyEvent(ownerId: String, evidenceId: String): LegalCustodyEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminder(reminder: LegalReminderEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExpense(expense: LegalExpenseEntity)

    @Transaction
    suspend fun attachOriginalEvidence(
        evidence: LegalEvidenceItemEntity,
        custodyEvent: LegalCustodyEventEntity,
    ) {
        insertEvidence(evidence)
        insertCustodyEvent(custodyEvent)
    }
}
