package com.elysium369.meet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.elysium369.meet.data.local.entities.DiagnosticExchangeEntity
import com.elysium369.meet.data.local.entities.DiagnosticObservationEntity
import com.elysium369.meet.data.local.entities.FindingDiagnosticSnapshotEntity
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

    @Query("SELECT * FROM diagnostic_exchanges WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun observeSessionExchanges(sessionId: String): Flow<List<DiagnosticExchangeEntity>>

    @Query("SELECT * FROM diagnostic_observations WHERE findingId = :findingId ORDER BY observedAt ASC")
    fun observeFindingTimeline(findingId: String): Flow<List<DiagnosticObservationEntity>>

    @Query("SELECT * FROM diagnostic_observations WHERE findingId = :findingId ORDER BY observedAt ASC")
    suspend fun getFindingTimeline(findingId: String): List<DiagnosticObservationEntity>

    @Query("SELECT * FROM finding_diagnostic_snapshots WHERE findingId = :findingId ORDER BY capturedAtMs DESC")
    fun observeFindingSnapshots(findingId: String): Flow<List<FindingDiagnosticSnapshotEntity>>
}
