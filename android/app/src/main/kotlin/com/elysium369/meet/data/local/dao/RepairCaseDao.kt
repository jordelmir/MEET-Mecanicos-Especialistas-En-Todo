package com.elysium369.meet.data.local.dao

import androidx.room.*
import com.elysium369.meet.data.local.entities.RepairCaseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RepairCaseDao {
    @Query("SELECT * FROM repair_cases WHERE isBookmarked = 1 ORDER BY createdAt DESC")
    fun getSavedCases(): Flow<List<RepairCaseEntity>>

    @Query("SELECT * FROM repair_cases WHERE isMyContribution = 1 ORDER BY createdAt DESC")
    fun getMyCases(): Flow<List<RepairCaseEntity>>

    @Query("SELECT * FROM repair_cases ORDER BY votes DESC, successRate DESC, createdAt DESC")
    suspend fun getAllCases(): List<RepairCaseEntity>

    @Query("SELECT * FROM repair_cases WHERE id = :id")
    suspend fun getCaseById(id: String): RepairCaseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCase(repairCase: RepairCaseEntity)

    @Delete
    suspend fun deleteCase(repairCase: RepairCaseEntity)

    @Query("UPDATE repair_cases SET isBookmarked = :bookmarked WHERE id = :id")
    suspend fun updateBookmarkState(id: String, bookmarked: Boolean)
}
