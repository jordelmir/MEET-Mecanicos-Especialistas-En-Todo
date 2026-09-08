package com.elysium369.meet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.elysium369.meet.data.local.entities.SafeJourneyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SafeJourneyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(journey: SafeJourneyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(journeys: List<SafeJourneyEntity>)

    @Update
    suspend fun update(journey: SafeJourneyEntity)

    @Query("SELECT * FROM safe_journeys WHERE journeyId = :journeyId")
    suspend fun getById(journeyId: String): SafeJourneyEntity?

    @Query("SELECT * FROM safe_journeys WHERE principalId = :principalId ORDER BY createdAtEpochMs DESC")
    fun getByPrincipalFlow(principalId: String): Flow<List<SafeJourneyEntity>>

    @Query("SELECT * FROM safe_journeys WHERE state IN ('ACTIVE', 'MISSED_CHECK_IN') ORDER BY createdAtEpochMs DESC")
    fun getActiveJourneysFlow(): Flow<List<SafeJourneyEntity>>

    @Query("SELECT * FROM safe_journeys WHERE state IN ('ACTIVE', 'MISSED_CHECK_IN')")
    suspend fun getActiveJourneys(): List<SafeJourneyEntity>

    @Query("UPDATE safe_journeys SET state = :state, journeyState = :journeyState WHERE journeyId = :journeyId")
    suspend fun updateState(journeyId: String, state: String, journeyState: String)

    @Query("DELETE FROM safe_journeys WHERE journeyId = :journeyId")
    suspend fun delete(journeyId: String): Int

    @Query("DELETE FROM safe_journeys WHERE state IN ('COMPLETED', 'CANCELLED', 'EXPIRED') AND completedAtEpochMs < :cutoffEpochMs")
    suspend fun purgeTerminal(cutoffEpochMs: Long): Int
}
