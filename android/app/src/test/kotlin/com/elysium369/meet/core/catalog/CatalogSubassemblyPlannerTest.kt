package com.elysium369.meet.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogSubassemblyPlannerTest {
    @Test
    fun `groups repeated source shards into one engine subassembly`() {
        val manifest = manifest(
            sections = listOf(
                section("block-a", "1. Motor · Bloque y conjunto inferior", 26),
                section("block-b", "1. Motor · Bloque y conjunto inferior", 9),
                section("head", "1. Motor · Culata y tren de valvulas", 22)
            )
        )

        val groups = CatalogSubassemblyPlanner.groups(manifest, "engine")

        assertEquals(2, groups.size)
        assertEquals("Bloque y conjunto inferior", groups[0].title)
        assertEquals(35, groups[0].entityCount)
        assertEquals(setOf("block-a", "block-b"), groups[0].sectionIds)
    }

    @Test
    fun `filters entities without changing their source order or wording`() {
        val group = CatalogSubassembly(
            id = "engine:block",
            title = "Bloque",
            systemId = "engine",
            sectionIds = setOf("block-a", "block-b"),
            entityCount = 2
        )
        val entities = listOf(
            entity("one", "Ciguenal", "block-a"),
            entity("two", "Bomba de aceite", "oil"),
            entity("three", "Piston", "block-b")
        )

        assertEquals(
            listOf("Ciguenal", "Piston"),
            CatalogSubassemblyPlanner.entitiesFor(entities, group).map { it.nameOriginal }
        )
    }

    private fun manifest(sections: List<ProprietaryCatalogSection>) = ProprietaryCatalogManifest(
        schemaVersion = 1,
        corpusId = "test",
        corpusVersion = "1",
        title = "test",
        vehicleLabel = "test",
        provenanceLabel = "test",
        visualAuthority = "PROCEDURAL_SCHEMATIC",
        sourceDocuments = emptyList(),
        systems = emptyList(),
        sections = sections,
        entityIndexPath = "test",
        statistics = ProprietaryCatalogStatistics(0, 0, 0, 0, 0, emptyMap()),
        contentSha256 = "test"
    )

    private fun section(id: String, title: String, entityCount: Int) = ProprietaryCatalogSection(
        id = id,
        systemId = "engine",
        titleOriginal = title,
        sourceDocumentId = "document_16",
        sourceFileName = "Document (16).docx",
        sourceDocumentSha256 = "sha",
        sourceOrderStart = 1,
        sourceOrderEnd = 2,
        blockCount = 1,
        entityCount = entityCount,
        realCaseCount = 0,
        shardPath = "path",
        contentSha256 = "sha"
    )

    private fun entity(id: String, name: String, sectionId: String) = ProprietaryCatalogEntity(
        id = id,
        nameOriginal = name,
        recordRole = "COMPONENT",
        systemId = "engine",
        sectionId = sectionId,
        shardPath = "path",
        sourceDocumentId = "document_16",
        sourceFileName = "Document (16).docx",
        sourceDocumentSha256 = "sha",
        sourceBlockId = "block",
        sourceTextHash = "hash",
        sourceOrder = 1,
        vehicleScope = "test",
        threeDimensionalBinding = ProprietaryThreeDimensionalBinding("scene", "node", "PROCEDURAL_SCHEMATIC", false, 1L)
    )
}
