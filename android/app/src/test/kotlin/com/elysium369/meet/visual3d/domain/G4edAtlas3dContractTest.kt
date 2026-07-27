package com.elysium369.meet.visual3d.domain

import com.elysium369.meet.core.catalog.G4ED_ENGINE_ATLAS_ASSET
import com.elysium369.meet.core.catalog.G4edEngineAtlasParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class G4edAtlas3dContractTest {
    private fun asset(relativePath: String): File = listOf(
        File("src/main/assets/$relativePath"),
        File("app/src/main/assets/$relativePath"),
        File("android/app/src/main/assets/$relativePath"),
    ).firstOrNull(File::isFile) ?: error("Missing asset $relativePath")

    private val atlas by lazy {
        G4edEngineAtlasParser.decode(asset(G4ED_ENGINE_ATLAS_ASSET).readText())
    }

    private fun manifest(packId: String): G4edAtlas3dManifest =
        G4edAtlas3dManifestParser.decode(
            asset(G4edAtlas3dCatalog.manifestAssetPath(packId)).readText(),
        )

    @Test
    fun `first thirty elements have deterministic 3d bindings`() {
        val manifests = listOf(
            manifest("g4ed_engine_structure"),
            manifest("g4ed_crank_pistons_rods"),
        )
        val bindingByOrdinal = manifests
            .flatMap { it.bindings }
            .associateBy { it.ordinal }

        assertEquals((1..30).toSet(), bindingByOrdinal.keys)
        atlas.elements.take(30).forEach { element ->
            val pack = manifests.single { it.packId == element.visual.packId }
            val binding = G4edAtlas3dCatalog.bindingFor(element, pack)
            assertNotNull("Missing 3D binding for ${element.ordinal}", binding)
            assertTrue(binding!!.interactionModes.contains("ORBIT_360"))
            assertFalse(binding.oemClaim)
            assertFalse(binding.dimensional)
        }
    }

    @Test
    fun `semantic regions preserve parent and commerce boundaries`() {
        val manifests = listOf(
            manifest("g4ed_engine_structure"),
            manifest("g4ed_crank_pistons_rods"),
        )
        val semantic = manifests.flatMap { it.bindings }
            .filter { it.renderStrategy == "SEMANTIC_REGION" }

        assertEquals(setOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 28, 29, 30), semantic.map { it.ordinal }.toSet())
        semantic.forEach { binding ->
            assertFalse(binding.directlySellable)
            assertNotNull(binding.parentCanonicalId)
            assertTrue(binding.authority == "SCHEMATIC_REGION")
        }
    }

    @Test
    fun `explode reset source transforms are identity and finite`() {
        listOf(
            manifest("g4ed_engine_structure"),
            manifest("g4ed_crank_pistons_rods"),
        ).flatMap { it.bindings }.forEach { binding ->
            assertEquals(listOf(0f, 0f, 0f), binding.originalTransform.position)
            assertEquals(listOf(0f, 0f, 0f), binding.originalTransform.rotation)
            assertEquals(listOf(1f, 1f, 1f), binding.originalTransform.scale)
            assertTrue(binding.explodeVector.all(Float::isFinite))
            assertTrue(binding.bounds.radius.isFinite())
        }
    }

    @Test
    fun `node matching never crosses element boundaries`() {
        val bindings = manifest("g4ed_engine_structure").bindings
        val block = bindings.single { it.ordinal == 1 }
        val cylinderOne = bindings.single { it.ordinal == 2 }

        assertTrue(G4edAtlas3dCatalog.isNodeForBinding("asset_mesh__g4ed_001__outer_casting", block))
        assertFalse(G4edAtlas3dCatalog.isNodeForBinding("asset_mesh__g4ed_002__cylinder_1", block))
        assertTrue(G4edAtlas3dCatalog.isNodeForBinding("asset_part__g4ed_002", cylinderOne))
    }
}
