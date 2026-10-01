package com.elysium.nexus.databases.ir

/**
 * Room-backed implementation of [IrRepository].
 */
class RoomIrRepository(
    private val dao: LearnedIrCommandDao
) : IrRepository {

    override suspend fun save(entity: LearnedIrCommandEntity): Long =
        dao.insert(entity)

    override suspend fun byId(id: Long): LearnedIrCommandEntity? =
        dao.byId(id)

    override suspend fun byTemplateId(templateId: String): List<LearnedIrCommandEntity> =
        dao.byTemplateId(templateId)

    override suspend fun all(): List<LearnedIrCommandEntity> =
        dao.all()

    override suspend fun count(): Int =
        dao.count()

    override suspend fun deleteById(id: Long) =
        dao.deleteById(id)

    override suspend fun deleteByTemplateId(templateId: String) =
        dao.deleteByTemplateId(templateId)

    override suspend fun deleteAll() =
        dao.deleteAll()
}
