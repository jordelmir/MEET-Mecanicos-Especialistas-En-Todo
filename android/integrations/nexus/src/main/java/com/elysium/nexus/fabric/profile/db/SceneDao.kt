package com.elysium.nexus.fabric.profile.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** §36 scene persistence. */
@Dao
interface SceneDao {

    @Query("SELECT * FROM scenes WHERE sceneId = :sceneId")
    suspend fun getById(sceneId: String): SceneEntity?

    @Query("SELECT * FROM scenes ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<SceneEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SceneEntity)

    @Query("DELETE FROM scenes WHERE sceneId = :sceneId")
    suspend fun deleteById(sceneId: String)

    @Query("SELECT COUNT(*) FROM scenes")
    suspend fun count(): Int
}