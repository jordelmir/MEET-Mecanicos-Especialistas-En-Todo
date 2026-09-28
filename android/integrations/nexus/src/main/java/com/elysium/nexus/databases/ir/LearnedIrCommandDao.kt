package com.elysium.nexus.databases.ir

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Room DAO for learned IR commands.
 *
 * The DAO provides CRUD for the IR command
 * database. Commands are stored per-device
 * (by [templateId]) and can be listed,
 * fetched by id, or deleted.
 */
@Dao
interface LearnedIrCommandDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LearnedIrCommandEntity): Long

    @Query("SELECT * FROM learned_ir_command WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): LearnedIrCommandEntity?

    @Query("SELECT * FROM learned_ir_command WHERE templateId = :templateId ORDER BY capturedAtMs DESC")
    suspend fun byTemplateId(templateId: String): List<LearnedIrCommandEntity>

    @Query("SELECT * FROM learned_ir_command ORDER BY capturedAtMs DESC")
    suspend fun all(): List<LearnedIrCommandEntity>

    @Query("SELECT COUNT(*) FROM learned_ir_command")
    suspend fun count(): Int

    @Query("DELETE FROM learned_ir_command WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM learned_ir_command WHERE templateId = :templateId")
    suspend fun deleteByTemplateId(templateId: String)

    @Query("DELETE FROM learned_ir_command")
    suspend fun deleteAll()
}
