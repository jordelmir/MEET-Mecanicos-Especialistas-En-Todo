package com.elysium369.meet.data.local.dao

import androidx.room.*
import com.elysium369.meet.data.local.entities.SavedGaugeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedGaugeDao {
    @Query("SELECT * FROM saved_gauges ORDER BY updatedAt DESC")
    fun getAllFlow(): Flow<List<SavedGaugeEntity>>

    @Query("SELECT * FROM saved_gauges ORDER BY updatedAt DESC")
    suspend fun getAll(): List<SavedGaugeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(gauge: SavedGaugeEntity)

    @Delete
    suspend fun delete(gauge: SavedGaugeEntity)

    @Query("SELECT * FROM saved_gauges WHERE id = :id")
    suspend fun getById(id: String): SavedGaugeEntity?

    @Query("SELECT COUNT(*) FROM saved_gauges")
    suspend fun count(): Int

    @Query("UPDATE saved_gauges SET isPublished = 1, marketplaceId = :marketplaceId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markAsPublished(id: String, marketplaceId: String, updatedAt: Long)
}
