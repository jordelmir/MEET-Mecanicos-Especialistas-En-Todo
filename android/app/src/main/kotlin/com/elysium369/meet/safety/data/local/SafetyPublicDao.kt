package com.elysium369.meet.safety.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SafetyPublicDao {

    @Query(
        """
        SELECT * FROM safety_public_points_local
        ORDER BY publishedAt DESC
        """
    )
    fun observePoints(): Flow<List<SafetyPublicPointEntity>>

    @Query(
        """
        SELECT * FROM safety_public_cases_local
        ORDER BY lastUpdatedAt DESC
        """
    )
    fun observeCases(): Flow<List<SafetyPublicCaseEntity>>

    @Query(
        """
        SELECT * FROM safety_public_cases_local
        WHERE caseId = :caseId
        LIMIT 1
        """
    )
    fun observeCase(caseId: String): Flow<SafetyPublicCaseEntity?>

    @Query(
        """
        SELECT * FROM safety_public_timeline_local
        WHERE caseId = :caseId
        ORDER BY COALESCE(occurredAt, recordedAt)
        """
    )
    fun observeTimeline(caseId: String): Flow<List<SafetyPublicTimelineEntity>>

    @Query(
        """
        SELECT * FROM safety_public_claims_local
        WHERE caseId = :caseId
        """
    )
    fun observeClaims(caseId: String): Flow<List<SafetyPublicClaimEntity>>

    @Query(
        """
        SELECT * FROM safety_public_points_local
        WHERE publicPointId = :id
        LIMIT 1
        """
    )
    suspend fun getPoint(id: String): SafetyPublicPointEntity?

    @Query(
        """
        SELECT * FROM safety_public_cases_local
        WHERE caseId = :id
        LIMIT 1
        """
    )
    suspend fun getCase(id: String): SafetyPublicCaseEntity?

    @Upsert
    suspend fun upsertPoints(rows: List<SafetyPublicPointEntity>)

    @Upsert
    suspend fun upsertPoint(row: SafetyPublicPointEntity)

    @Upsert
    suspend fun upsertCases(rows: List<SafetyPublicCaseEntity>)

    @Upsert
    suspend fun upsertTimeline(rows: List<SafetyPublicTimelineEntity>)

    @Upsert
    suspend fun upsertClaims(rows: List<SafetyPublicClaimEntity>)

    @Query(
        """
        DELETE FROM safety_public_points_local
        WHERE publicPointId = :id
        """
    )
    suspend fun deletePoint(id: String)

    @Query(
        """
        DELETE FROM safety_public_cases_local
        WHERE caseId = :id
        """
    )
    suspend fun deleteCase(id: String)
    @Query("DELETE FROM safety_public_points_local WHERE publicPointId NOT IN (:ids)")
    suspend fun removeMissingPoints(ids: List<String>)

    @Query("DELETE FROM safety_public_cases_local WHERE caseId NOT IN (:ids)")
    suspend fun removeMissingCases(ids: List<String>)

    @Query("DELETE FROM safety_public_timeline_local WHERE caseId = :caseId")
    suspend fun clearTimeline(caseId: String)

    @Query("DELETE FROM safety_public_claims_local WHERE caseId = :caseId")
    suspend fun clearClaims(caseId: String)

    @Query("DELETE FROM safety_public_timeline_local WHERE caseId NOT IN (SELECT caseId FROM safety_public_cases_local)")
    suspend fun removeOrphanTimeline()

    @Query("DELETE FROM safety_public_claims_local WHERE caseId NOT IN (SELECT caseId FROM safety_public_cases_local)")
    suspend fun removeOrphanClaims()
}
