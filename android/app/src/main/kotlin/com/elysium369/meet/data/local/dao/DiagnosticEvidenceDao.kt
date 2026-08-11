package com.elysium369.meet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elysium369.meet.data.local.entities.DiagnosticExchangeEntity
import com.elysium369.meet.data.local.entities.DiagnosticObservationEntity
import com.elysium369.meet.data.local.entities.FindingDiagnosticSnapshotEntity
import com.elysium369.meet.data.local.entities.DiagnosticSessionIntegrityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticEvidenceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun appendExchange(exchange: DiagnosticExchangeEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun appendExchanges(exchanges: List<DiagnosticExchangeEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun appendObservation(observation: DiagnosticObservationEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun appendObservations(observations: List<DiagnosticObservationEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun appendFindingSnapshot(snapshot: FindingDiagnosticSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun appendSessionIntegrity(integrity: DiagnosticSessionIntegrityEntity)

    @Query("SELECT COALESCE(MAX(sessionSequence), 0) FROM diagnostic_exchanges WHERE sessionId = :sessionId")
    suspend fun maxExchangeSequence(sessionId: String): Long

    @Query("SELECT exchangeHash FROM diagnostic_exchanges WHERE sessionId = :sessionId ORDER BY sessionSequence DESC LIMIT 1")
    suspend fun latestExchangeHash(sessionId: String): String?

    @Query("SELECT COALESCE(MAX(sessionSequence), 0) FROM diagnostic_observations WHERE sessionId = :sessionId")
    suspend fun maxObservationSequence(sessionId: String): Long

    @Query("SELECT observationHash FROM diagnostic_observations WHERE findingId = :findingId ORDER BY observedAt DESC, sessionSequence DESC LIMIT 1")
    suspend fun latestObservationHash(findingId: String): String?

    @Query("SELECT * FROM diagnostic_exchanges WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun observeSessionExchanges(sessionId: String): Flow<List<DiagnosticExchangeEntity>>

    @Query("SELECT * FROM diagnostic_observations WHERE findingId = :findingId ORDER BY observedAt ASC, sessionSequence ASC, id ASC")
    fun observeFindingTimeline(findingId: String): Flow<List<DiagnosticObservationEntity>>

    @Query("SELECT * FROM diagnostic_observations WHERE findingId = :findingId ORDER BY observedAt ASC, sessionSequence ASC, id ASC")
    suspend fun getFindingTimeline(findingId: String): List<DiagnosticObservationEntity>

    @Query("SELECT * FROM finding_diagnostic_snapshots WHERE findingId = :findingId ORDER BY capturedAtMs DESC")
    fun observeFindingSnapshots(findingId: String): Flow<List<FindingDiagnosticSnapshotEntity>>

    @Query(
        """DELETE FROM diagnostic_exchanges
           WHERE expiresAtMs IS NOT NULL AND expiresAtMs <= :nowMs
             AND id NOT IN (SELECT exchangeId FROM diagnostic_observations WHERE exchangeId IS NOT NULL)
             AND NOT EXISTS (
                 SELECT 1 FROM finding_diagnostic_snapshots snapshot
                 WHERE instr(snapshot.rawExchangeIdsJson, diagnostic_exchanges.id) > 0
             )""",
    )
    suspend fun purgeExpiredUnreferencedExchanges(nowMs: Long): Int

    @Query(
        """DELETE FROM diagnostic_session_integrity
           WHERE sessionId NOT IN (SELECT DISTINCT sessionId FROM diagnostic_exchanges)""",
    )
    suspend fun purgeOrphanedSessionIntegrity(): Int
}
