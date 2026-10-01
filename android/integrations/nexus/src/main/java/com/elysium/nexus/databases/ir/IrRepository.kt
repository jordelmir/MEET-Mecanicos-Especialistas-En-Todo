package com.elysium.nexus.databases.ir

/**
 * Repository interface for learned IR commands.
 *
 * Domain-side API hiding the Room persistence.
 * Mirrors the [com.elysium.nexus.databases.profile.ProfileRepository]
 * pattern.
 */
interface IrRepository {

    /**
     * Save a learned IR command. Returns the
     * assigned row id.
     */
    suspend fun save(entity: LearnedIrCommandEntity): Long

    /**
     * Get a command by its row id.
     */
    suspend fun byId(id: Long): LearnedIrCommandEntity?

    /**
     * Get all commands for a device template.
     */
    suspend fun byTemplateId(templateId: String): List<LearnedIrCommandEntity>

    /**
     * Get all saved commands.
     */
    suspend fun all(): List<LearnedIrCommandEntity>

    /**
     * Count of saved commands.
     */
    suspend fun count(): Int

    /**
     * Delete a command by id.
     */
    suspend fun deleteById(id: Long)

    /**
     * Delete all commands for a template.
     */
    suspend fun deleteByTemplateId(templateId: String)

    /**
     * Delete all commands.
     */
    suspend fun deleteAll()
}
