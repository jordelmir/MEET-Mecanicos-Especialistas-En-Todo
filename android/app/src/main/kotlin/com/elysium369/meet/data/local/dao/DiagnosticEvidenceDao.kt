package com.elysium369.meet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.elysium369.meet.data.local.entities.DiagnosticExchangeEntity
import com.elysium369.meet.data.local.entities.DiagnosticObservationEntity
import com.elysium369.meet.data.local.entities.FindingDiagnosticSnapshotEntity
import com.elysium369.meet.data.local.entities.FindingSnapshotExchangeRefEntity
import com.elysium369.meet.data.local.entities.DiagnosticSessionIntegrityEntity
import com.elysium369.meet.data.local.entities.EncryptedEvidenceBlobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticEvidenceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun appendExchange(exchange: DiagnosticExchangeEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun appendEncryptedBlob(blob: EncryptedEvidenceBlobEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun appendEncryptedBlobs(blobs: List<EncryptedEvidenceBlobEntity>)

    @Transaction
    suspend fun appendEncryptedExchange(
        blob: EncryptedEvidenceBlobEntity,
        exchange: DiagnosticExchangeEntity,
    ) {
        require(exchange.rawPayloadBlobId == blob.blobId)
        appendEncryptedBlob(blob)
        appendExchange(exchange)
    }

    @Transaction
    suspend fun appendEncryptedSession(
        blobs: List<EncryptedEvidenceBlobEntity>,
        exchanges: List<DiagnosticExchangeEntity>,
        integrity: DiagnosticSessionIntegrityEntity,
    ) {
        require(blobs.size == exchanges.size)
        require(exchanges.all { exchange -> blobs.any { it.blobId == exchange.rawPayloadBlobId } })
        appendEncryptedBlobs(blobs)
        appendExchanges(exchanges)
        appendSessionIntegrity(integrity)
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun appendExchanges(exchanges: List<DiagnosticExchangeEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun appendObservation(observation: DiagnosticObservationEntity)

    @Transaction
    suspend fun appendObservationWithExpectedPredecessor(
        observation: DiagnosticObservationEntity,
        expectedSessionSequence: Long,
        expectedFindingSequence: Long,
        expectedPreviousHash: String,
    ) {
        check(maxObservationSequence(observation.sessionId) + 1L == expectedSessionSequence)
        check(maxFindingSequence(observation.findingId) + 1L == expectedFindingSequence)
        check(latestObservationHash(observation.findingId).orEmpty() == expectedPreviousHash)
        appendObservation(observation)
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun appendObservations(observations: List<DiagnosticObservationEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun appendFindingSnapshot(snapshot: FindingDiagnosticSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun appendFindingSnapshotExchangeRefs(refs: List<FindingSnapshotExchangeRefEntity>)

    @Transaction
    suspend fun appendFindingSnapshotWithExchangeRefs(
        snapshot: FindingDiagnosticSnapshotEntity,
        exchangeIds: List<String>,
    ) {
        appendFindingSnapshot(snapshot)
        val normalizedIds = exchangeIds.map(String::trim).filter(String::isNotEmpty).distinct()
        if (normalizedIds.isNotEmpty()) {
            appendFindingSnapshotExchangeRefs(
                normalizedIds.mapIndexed { ordinal, exchangeId ->
                    FindingSnapshotExchangeRefEntity(
                        snapshotId = snapshot.id,
                        exchangeId = exchangeId,
                        ordinal = ordinal,
                        role = "SOURCE_RAW_EXCHANGE",
                    )
                },
            )
        }
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun appendSessionIntegrity(integrity: DiagnosticSessionIntegrityEntity)

    @Query("SELECT COALESCE(MAX(sessionSequence), 0) FROM diagnostic_exchanges WHERE sessionId = :sessionId")
    suspend fun maxExchangeSequence(sessionId: String): Long

    @Query("SELECT exchangeHash FROM diagnostic_exchanges WHERE sessionId = :sessionId ORDER BY sessionSequence DESC LIMIT 1")
    suspend fun latestExchangeHash(sessionId: String): String?

    @Query("SELECT COALESCE(MAX(sessionSequence), 0) FROM diagnostic_observations WHERE sessionId = :sessionId")
    suspend fun maxObservationSequence(sessionId: String): Long

    @Query("SELECT COALESCE(MAX(findingSequence), 0) FROM diagnostic_observations WHERE findingId = :findingId")
    suspend fun maxFindingSequence(findingId: String): Long

    @Query("SELECT observationHash FROM diagnostic_observations WHERE findingId = :findingId ORDER BY findingSequence DESC, observedAt DESC, sessionSequence DESC LIMIT 1")
    suspend fun latestObservationHash(findingId: String): String?

    @Query("SELECT * FROM diagnostic_exchanges WHERE sessionId = :sessionId ORDER BY sessionSequence ASC, id ASC")
    fun observeSessionExchanges(sessionId: String): Flow<List<DiagnosticExchangeEntity>>

    @Query("SELECT * FROM diagnostic_observations WHERE findingId = :findingId ORDER BY findingSequence ASC, observedAt ASC, sessionSequence ASC, id ASC")
    fun observeFindingTimeline(findingId: String): Flow<List<DiagnosticObservationEntity>>

    @Query("SELECT * FROM diagnostic_observations WHERE findingId = :findingId ORDER BY findingSequence ASC, observedAt ASC, sessionSequence ASC, id ASC")
    suspend fun getFindingTimeline(findingId: String): List<DiagnosticObservationEntity>

    @Query("SELECT * FROM finding_diagnostic_snapshots WHERE findingId = :findingId ORDER BY capturedAtMs DESC")
    fun observeFindingSnapshots(findingId: String): Flow<List<FindingDiagnosticSnapshotEntity>>

    @Query(
        """DELETE FROM diagnostic_exchanges
           WHERE expiresAtMs IS NOT NULL AND expiresAtMs <= :nowMs
             AND id NOT IN (SELECT exchangeId FROM diagnostic_observations WHERE exchangeId IS NOT NULL)
             AND NOT EXISTS (
                 SELECT 1 FROM finding_snapshot_exchange_refs ref
                 WHERE ref.exchangeId = diagnostic_exchanges.id
             )""",
    )
    suspend fun purgeExpiredUnreferencedExchanges(nowMs: Long): Int

    @Query(
        """DELETE FROM encrypted_evidence_blobs
           WHERE blobId NOT IN (
               SELECT rawPayloadBlobId FROM diagnostic_exchanges
               WHERE rawPayloadBlobId IS NOT NULL
           )""",
    )
    suspend fun purgeOrphanedEncryptedBlobs(): Int

    @Query(
        """DELETE FROM diagnostic_session_integrity
           WHERE sessionId NOT IN (SELECT DISTINCT sessionId FROM diagnostic_exchanges)""",
    )
    suspend fun purgeOrphanedSessionIntegrity(): Int
}
