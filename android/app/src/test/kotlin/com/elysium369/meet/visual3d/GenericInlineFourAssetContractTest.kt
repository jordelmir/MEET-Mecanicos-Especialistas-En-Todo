package com.elysium369.meet.visual3d

import com.elysium369.meet.core.engine3d.UniversalCatalogSceneNode
import com.elysium369.meet.visual3d.domain.CatalogSemanticScenePlanner
import com.elysium369.meet.visual3d.domain.GenericInlineFourAssetContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenericInlineFourAssetContractTest {
    @Test
    fun `source backed nodes keep the first literal record per mechanical family`() {
        val nodes = listOf(
            UniversalCatalogSceneNode("first-head", "Culata", "engine", 1L),
            UniversalCatalogSceneNode("duplicate-head", "Culata", "engine", 2L),
            UniversalCatalogSceneNode("unbound", "Bujía 1", "engine", 3L),
            UniversalCatalogSceneNode("pistons", "Pistones", "engine", 4L)
        )

        assertEquals(
            listOf("first-head", "pistons"),
            GenericInlineFourAssetContract.sourceBackedNodes(nodes).map { it.id }
        )
    }

    @Test
    fun `mesh node names resolve to stable part keys`() {
        assertEquals(
            "crankshaft",
            GenericInlineFourAssetContract.meshKeyForNodeName("asset_mesh__crankshaft__main_axis")
        )
        assertNull(GenericInlineFourAssetContract.meshKeyForNodeName("BodyHood"))
    }

    @Test
    fun `literal catalog names bind exactly without absorbing table rows`() {
        assertEquals(
            "pistons",
            GenericInlineFourAssetContract.bindingForSourceName("Pistones")?.meshKey
        )
        assertEquals(
            "cylinder_head",
            GenericInlineFourAssetContract.bindingForSourceName("Culata")?.meshKey
        )
        assertNull(GenericInlineFourAssetContract.bindingForSourceName("Pistones\tNo\tSí\tSí\tCrítico"))
    }

    @Test
    fun `context geometry cannot manufacture a proprietary selection`() {
        val nodes = listOf(UniversalCatalogSceneNode("head", "Culata", "engine", 1L))
        val placements = CatalogSemanticScenePlanner.placements(nodes, null)

        assertNull(
            GenericInlineFourAssetContract.placementForNodeName(
                "asset_mesh__camshafts_context__intake_cam",
                placements
            )
        )
    }

    @Test
    fun `asset mesh resolves the exact primary literal placement`() {
        val nodes = listOf(
            UniversalCatalogSceneNode("piston-primary", "Pistones", "engine", 1L),
            UniversalCatalogSceneNode("piston-evidence", "Pistones", "engine", 2L)
        )
        val placements = CatalogSemanticScenePlanner.placements(nodes, null)

        val placement = GenericInlineFourAssetContract.placementForNodeName(
            "asset_mesh__pistons__crown_1",
            placements
        )

        assertEquals("piston-primary", placement?.node?.id)
        assertEquals(0, placement?.occurrence)
    }

    @Test
    fun `null selection never highlights unbound or bound asset meshes`() {
        val nodes = listOf(UniversalCatalogSceneNode("piston-primary", "Pistones", "engine", 1L))
        val placements = CatalogSemanticScenePlanner.placements(nodes, null)

        assertFalse(
            GenericInlineFourAssetContract.isNodeSelected(
                "asset_mesh__pistons__crown_1",
                placements,
                selectedEntityId = null
            )
        )
        assertFalse(
            GenericInlineFourAssetContract.isNodeSelected(
                "asset_mesh__engine_block__deck",
                placements,
                selectedEntityId = null
            )
        )
        assertTrue(
            GenericInlineFourAssetContract.isNodeSelected(
                "asset_mesh__pistons__crown_1",
                placements,
                selectedEntityId = "piston-primary"
            )
        )
    }

    @Test
    fun `service sequence is staged and returns exactly to origin`() {
        val assembled = GenericInlineFourAssetContract.serviceOffset(
            "asset_mesh__crankshaft__main_axis",
            0f
        )
        val beforeCrankStage = GenericInlineFourAssetContract.serviceOffset(
            "asset_mesh__crankshaft__main_axis",
            0.60f
        )
        val exploded = GenericInlineFourAssetContract.serviceOffset(
            "asset_mesh__crankshaft__main_axis",
            1f
        )

        assertEquals(0f, assembled.x, 0f)
        assertEquals(0f, assembled.y, 0f)
        assertEquals(0f, assembled.z, 0f)
        assertEquals(0f, beforeCrankStage.y, 0f)
        assertTrue(exploded.y < 0f)
    }

    @Test
    fun `service explosion remains bounded inside the inspection viewport`() {
        val finalOffsets = GenericInlineFourAssetContract.bindings.map { binding ->
            GenericInlineFourAssetContract.serviceOffset(
                "${GenericInlineFourAssetContract.MESH_NODE_PREFIX}${binding.meshKey}__audit",
                1f
            )
        }

        assertTrue(finalOffsets.all { kotlin.math.abs(it.x) <= 0.74f })
        assertTrue(finalOffsets.all { kotlin.math.abs(it.y) <= 0.70f })
        assertTrue(finalOffsets.all { kotlin.math.abs(it.z) <= 0.18f })
    }

    @Test
    fun `binding keys and literal aliases are unique`() {
        val bindings = GenericInlineFourAssetContract.bindings
        val aliases = bindings.flatMap { it.literalSourceNames }

        assertEquals(bindings.size, bindings.map { it.meshKey }.distinct().size)
        assertEquals(aliases.size, aliases.map(String::lowercase).distinct().size)
        assertFalse(GenericInlineFourAssetContract.requiredMeshKeys.isEmpty())
    }
}
