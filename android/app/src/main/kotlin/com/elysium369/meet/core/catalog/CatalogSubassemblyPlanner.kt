package com.elysium369.meet.core.catalog

data class CatalogSubassembly(
    val id: String,
    val title: String,
    val systemId: String,
    val sectionIds: Set<String>,
    val entityCount: Int
)

object CatalogSubassemblyPlanner {
    fun groups(
        manifest: ProprietaryCatalogManifest?,
        systemId: String
    ): List<CatalogSubassembly> {
        if (manifest == null) return emptyList()

        return manifest.sections
            .asSequence()
            .filter { it.systemId == systemId && it.entityCount > 0 }
            .groupBy { canonicalTitle(it.titleOriginal) }
            .map { (title, sections) ->
                CatalogSubassembly(
                    id = "$systemId:${title.lowercase()}",
                    title = title,
                    systemId = systemId,
                    sectionIds = sections.mapTo(linkedSetOf()) { it.id },
                    entityCount = sections.sumOf { it.entityCount }
                )
            }
            .sortedBy { group ->
                manifest.sections.indexOfFirst { it.id in group.sectionIds }
            }
            .toList()
    }

    fun entitiesFor(
        entities: List<ProprietaryCatalogEntity>,
        subassembly: CatalogSubassembly?
    ): List<ProprietaryCatalogEntity> = if (subassembly == null) {
        entities
    } else {
        entities.filter { it.sectionId in subassembly.sectionIds }
    }

    private fun canonicalTitle(title: String): String =
        title.substringAfter('·', title).trim()
}
