package com.elysium369.meet.data.local.dao

import androidx.room.*
import com.elysium369.meet.data.local.entities.HumanityCapabilityEntity
import com.elysium369.meet.data.local.entities.HumanityEvidenceEntity
import com.elysium369.meet.data.local.entities.HumanityProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HumanityProgressDao {
    @Query("SELECT * FROM humanity_learning_progress_local WHERE userId = :userId")
    fun getAllProgressForUser(userId: String): Flow<List<HumanityProgressEntity>>

    @Query("SELECT * FROM humanity_learning_progress_local WHERE userId = :userId AND targetId = :targetId LIMIT 1")
    suspend fun getProgressForTarget(userId: String, targetId: String): HumanityProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: HumanityProgressEntity)

    @Query("DELETE FROM humanity_learning_progress_local WHERE userId = :userId")
    suspend fun clearUserProgress(userId: String)
}

@Dao
interface HumanityEvidenceDao {
    @Query("SELECT * FROM humanity_evidence_items_local WHERE userId = :userId ORDER BY createdAtEpochMs DESC")
    fun getAllEvidenceForUser(userId: String): Flow<List<HumanityEvidenceEntity>>

    @Query("SELECT * FROM humanity_evidence_items_local WHERE isSynced = 0")
    suspend fun getUnsyncedEvidence(): List<HumanityEvidenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidence(item: HumanityEvidenceEntity)

    @Query("UPDATE humanity_evidence_items_local SET isSynced = 1 WHERE id = :evidenceId")
    suspend fun markSynced(evidenceId: String)
}

@Dao
interface HumanityCapabilityDao {
    @Query("SELECT * FROM humanity_capability_records_local WHERE userId = :userId")
    fun getAllCapabilitiesForUser(userId: String): Flow<List<HumanityCapabilityEntity>>

    @Query("SELECT * FROM humanity_capability_records_local WHERE userId = :userId AND skillId = :skillId LIMIT 1")
    suspend fun getCapabilityForSkill(userId: String, skillId: String): HumanityCapabilityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCapability(record: HumanityCapabilityEntity)
}
