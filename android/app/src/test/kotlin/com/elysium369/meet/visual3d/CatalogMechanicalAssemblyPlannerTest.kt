package com.elysium369.meet.visual3d

import com.elysium369.meet.visual3d.domain.CatalogMechanicalAssemblyPlanner
import com.elysium369.meet.visual3d.domain.MechanicalElementShape
import com.elysium369.meet.visual3d.domain.SemanticPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogMechanicalAssemblyPlannerTest {
    @Test
    fun `engine block exposes four cylinder liners instead of one opaque box`() {
        val elements = CatalogMechanicalAssemblyPlanner.elementsFor("Bloque de motor", SemanticPrimitive.BOX)

        assertTrue(elements.size >= 7)
        assertEquals(4, elements.count { it.shape == MechanicalElementShape.CYLINDER })
    }

    @Test
    fun `crankshaft is a compound shaft with journals and counterweights`() {
        val elements = CatalogMechanicalAssemblyPlanner.elementsFor("Cigüeñal", SemanticPrimitive.SHAFT)

        assertTrue(elements.size >= 9)
        assertTrue(elements.all { it.shape == MechanicalElementShape.CYLINDER })
    }

    @Test
    fun `duplicate source records use compact selectable tokens`() {
        val token = CatalogMechanicalAssemblyPlanner.sourceRecordToken(occurrence = 1)

        assertEquals(3, token.size)
        assertEquals(1, token.count { it.shape == MechanicalElementShape.CUBE })
    }

    @Test
    fun `service explosion is staged and returns exactly to assembled origin`() {
        val assembled = CatalogMechanicalAssemblyPlanner.serviceOffset(
            name = "Cigüeñal",
            primitive = SemanticPrimitive.SHAFT,
            assembledX = 0f,
            progress = 0f
        )
        val beforeShaftStage = CatalogMechanicalAssemblyPlanner.serviceOffset(
            name = "Cigüeñal",
            primitive = SemanticPrimitive.SHAFT,
            assembledX = 0f,
            progress = 0.60f
        )
        val exploded = CatalogMechanicalAssemblyPlanner.serviceOffset(
            name = "Cigüeñal",
            primitive = SemanticPrimitive.SHAFT,
            assembledX = 0f,
            progress = 1f
        )

        assertEquals(0f, assembled.x, 0f)
        assertEquals(0f, assembled.y, 0f)
        assertEquals(0f, assembled.z, 0f)
        assertEquals(0f, beforeShaftStage.z, 0f)
        assertTrue(exploded.z < 0f)
    }
}
