package com.elysium.nexus.databases.ir

/**
 * In-memory [IrRepository] for unit tests.
 */
class InMemoryIrRepository : IrRepository {
    private val items = mutableListOf<LearnedIrCommandEntity>()
    private var nextId = 1L

    override suspend fun save(entity: LearnedIrCommandEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        val stored = entity.copy(id = id)
        items.removeAll { it.id == id }
        items.add(stored)
        return id
    }

    override suspend fun byId(id: Long): LearnedIrCommandEntity? =
        items.firstOrNull { it.id == id }

    override suspend fun byTemplateId(templateId: String): List<LearnedIrCommandEntity> =
        items.filter { it.templateId == templateId }

    override suspend fun all(): List<LearnedIrCommandEntity> =
        items.toList()

    override suspend fun count(): Int =
        items.size

    override suspend fun deleteById(id: Long) {
        items.removeAll { it.id == id }
    }

    override suspend fun deleteByTemplateId(templateId: String) {
        items.removeAll { it.templateId == templateId }
    }

    override suspend fun deleteAll() {
        items.clear()
    }
}
