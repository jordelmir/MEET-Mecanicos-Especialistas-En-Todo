package com.elysium369.meet.core.catalog

import com.elysium369.meet.core.engine3d.ElysiumProceduralModels
import com.elysium369.meet.core.engine3d.UniversalCatalogSceneNode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProprietaryPartsCatalogTest {
    private fun asset(path: String): File {
        val candidates = listOf(
            File("src/main/assets/$path"),
            File("app/src/main/assets/$path"),
            File("android/app/src/main/assets/$path")
        )
        return candidates.firstOrNull(File::isFile) ?: error("Missing asset $path")
    }

    private val manifest by lazy {
        ProprietaryCatalogParser.decodeManifest(asset(PROPRIETARY_CATALOG_MANIFEST_ASSET).readText())
    }
    private val index by lazy {
        ProprietaryCatalogParser.decodeEntityIndex(asset(PROPRIETARY_CATALOG_ENTITY_INDEX_ASSET).readText())
    }

    @Test
    fun `manifest covers both owner documents literally`() {
        assertEquals(74_648, manifest.statistics.blockCount)
        assertEquals(44_106, manifest.sourceDocuments.first { it.id == "document_16" }.blockCount)
        assertEquals(30_542, manifest.sourceDocuments.first { it.id == "document_17" }.blockCount)
        assertEquals(PROPRIETARY_VEHICLE_LABEL, manifest.vehicleLabel)
        assertTrue(manifest.statistics.entityCount > 4_500)
        assertTrue(manifest.systems.size >= 25)
    }

    @Test
    fun `every indexed entry has a deterministic honest 3D binding`() {
        assertEquals(
            manifest.statistics.entityCount + manifest.statistics.realCaseCount,
            index.entities.size
        )
        index.entities.forEach { entity ->
            assertEquals(entity.id, entity.threeDimensionalBinding.nodeId)
            assertEquals(entity.systemId, entity.threeDimensionalBinding.sceneId)
            assertEquals("PROCEDURAL_SCHEMATIC", entity.threeDimensionalBinding.visualAuthority)
            assertFalse(entity.threeDimensionalBinding.isDimensionalModel)
            val node = UniversalCatalogSceneNode(
                entity.id,
                entity.nameOriginal,
                entity.systemId,
                entity.threeDimensionalBinding.seed
            )
            val first = ElysiumProceduralModels.buildUniversalCatalogScene(listOf(node), entity.id).single()
            val second = ElysiumProceduralModels.buildUniversalCatalogScene(listOf(node), entity.id).single()
            assertEquals(entity.id, first.id)
            assertEquals(first, second)
        }
    }

    @Test
    fun `all sections exist and preserve source block counts`() {
        var blockCount = 0
        manifest.sections.forEach { section ->
            val shard = ProprietaryCatalogParser.decodeSection(asset(section.shardPath).readText())
            assertEquals(section.id, shard.sectionId)
            assertEquals(section.blockCount, shard.blocks.size)
            assertEquals(section.sourceDocumentSha256, shard.sourceDocumentSha256)
            blockCount += shard.blocks.size
        }
        assertEquals(74_648, blockCount)
    }
}
