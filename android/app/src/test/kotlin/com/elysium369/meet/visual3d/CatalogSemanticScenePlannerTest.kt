package com.elysium369.meet.visual3d

import com.elysium369.meet.core.engine3d.UniversalCatalogSceneNode
import com.elysium369.meet.visual3d.domain.CatalogSemanticScenePlanner
import com.elysium369.meet.visual3d.domain.SemanticPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSemanticScenePlannerTest {
    @Test
    fun `keeps the selected engine component inside the bounded scene`() {
        val nodes = (0 until 100).map { index ->
            UniversalCatalogSceneNode("part-$index", "Pieza $index", "engine", index.toLong())
        }

        val placements = CatalogSemanticScenePlanner.placements(nodes, "part-99")

        assertEquals(CatalogSemanticScenePlanner.MAX_VISIBLE_COMPONENTS, placements.size)
        assertTrue(placements.any { it.node.id == "part-99" })
    }

    @Test
    fun `lays out lower engine parts by recognizable mechanical role`() {
        val nodes = listOf(
            UniversalCatalogSceneNode("block", "Bloque de motor", "engine", 1L),
            UniversalCatalogSceneNode("crank", "Cigüeñal", "engine", 2L),
            UniversalCatalogSceneNode("piston", "Pistones", "engine", 3L),
            UniversalCatalogSceneNode("flywheel", "Volante de inercia / flexplate", "engine", 4L)
        )

        val placements = CatalogSemanticScenePlanner.placements(nodes, null)

        val block = placements.first { it.node.id == "block" }
        val crank = placements.first { it.node.id == "crank" }
        val piston = placements.first { it.node.id == "piston" }
        val flywheel = placements.first { it.node.id == "flywheel" }
        assertTrue(block.scale > crank.scale)
        assertTrue(crank.z < block.z)
        assertTrue(piston.z > block.z)
        assertTrue(flywheel.x < -0.8f)
    }

    @Test
    fun `keeps literal duplicate records as separate depth instances`() {
        val nodes = listOf(
            UniversalCatalogSceneNode("block-1", "Bloque de motor", "engine", 1L),
            UniversalCatalogSceneNode("block-2", "Bloque de motor", "engine", 2L)
        )

        val placements = CatalogSemanticScenePlanner.placements(nodes, null)

        assertEquals(2, placements.size)
        assertEquals(0, placements[0].occurrence)
        assertEquals(1, placements[1].occurrence)
        assertTrue(placements[0].z != placements[1].z)
    }

    @Test
    fun `classifies recognizable lower engine silhouettes from literal names`() {
        assertEquals(SemanticPrimitive.SHAFT, CatalogSemanticScenePlanner.primitiveFor("Cigüeñal"))
        assertEquals(SemanticPrimitive.PISTON, CatalogSemanticScenePlanner.primitiveFor("Pistones"))
        assertEquals(SemanticPrimitive.DISC, CatalogSemanticScenePlanner.primitiveFor("Volante de inercia / flexplate"))
        assertEquals(SemanticPrimitive.CHAIN, CatalogSemanticScenePlanner.primitiveFor("Cadena de eje balanceador"))
        assertEquals(SemanticPrimitive.BOX, CatalogSemanticScenePlanner.primitiveFor("Bloque de motor"))
    }
}
