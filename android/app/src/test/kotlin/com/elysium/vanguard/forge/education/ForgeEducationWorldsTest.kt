package com.elysium.vanguard.forge.education

import org.junit.Assert.*
import org.junit.Test

class ForgeEducationWorldsTest {

    // ─── Scene Creation ───

    @Test
    fun `math 1 feb spatial scene has 8 entities`() {
        val scene = MathFirstGradeSpatialWorld.createScene()
        assertEquals(8, scene.size)
    }

    @Test
    fun `scene has both reference and movable objects`() {
        val scene = MathFirstGradeSpatialWorld.createScene()
        val references = scene.filter { "reference" in it.tags }
        val movables = scene.filter { "movable" in it.tags }

        assertEquals(3, references.size) // mesa, silla, caja
        assertEquals(5, movables.size)   // pelota_roja, pelota_azul, estrella, cubo, cilindro
    }

    @Test
    fun `all entities have unique IDs`() {
        val scene = MathFirstGradeSpatialWorld.createScene()
        val ids = scene.map { it.entityId }
        assertEquals(ids.size, ids.toSet().size)
    }

    // ─── Spatial Relation Engine ───

    @Test
    fun `red ball is ABOVE the table`() {
        val scene = MathFirstGradeSpatialWorld.createScene()
        val ball = scene.first { it.entityId == "pelota_roja" }
        val table = scene.first { it.entityId == "mesa_1" }

        val relations = SpatialRelationEngine.evaluate(ball, table)
        assertTrue("Red ball should be ABOVE table", SpatialRelation.ABOVE in relations)
    }

    @Test
    fun `chair is LEFT OF the table`() {
        val scene = MathFirstGradeSpatialWorld.createScene()
        val chair = scene.first { it.entityId == "silla_1" }
        val table = scene.first { it.entityId == "mesa_1" }

        val relations = SpatialRelationEngine.evaluate(chair, table)
        assertTrue("Chair should be LEFT_OF table", SpatialRelation.LEFT_OF in relations)
    }

    @Test
    fun `star is FAR from box`() {
        val scene = MathFirstGradeSpatialWorld.createScene()
        val star = scene.first { it.entityId == "estrella" }
        val box = scene.first { it.entityId == "caja_1" }

        val distance = kotlin.math.sqrt(
            ((star.transform.positionX - box.transform.positionX).let { it * it } +
                (star.transform.positionY - box.transform.positionY).let { it * it } +
                (star.transform.positionZ - box.transform.positionZ).let { it * it }).toDouble()
        )

        // Star at (-2,3,0), Box at (2,0,-1) → distance ≈ 5.1
        assertTrue("Distance should be > 4", distance > 4.0)
    }

    @Test
    fun `validate answer correctly identifies spatial relation`() {
        val scene = MathFirstGradeSpatialWorld.createScene()
        val ball = scene.first { it.entityId == "pelota_roja" }
        val table = scene.first { it.entityId == "mesa_1" }

        assertTrue(SpatialRelationEngine.validateAnswer(ball, table, SpatialRelation.ABOVE))
        assertFalse(SpatialRelationEngine.validateAnswer(ball, table, SpatialRelation.BELOW))
    }

    // ─── Task Generation ───

    @Test
    fun `can generate ABOVE spatial task`() {
        val scene = MathFirstGradeSpatialWorld.createScene()
        val task = MathFirstGradeSpatialWorld.generateSpatialTask(scene, SpatialRelation.ABOVE)

        assertNotNull(task)
        assertTrue(task!!.question.contains("¿Dónde está"))
        assertEquals(SpatialRelation.ABOVE, task.correctAnswer)
        assertTrue(task.allValidAnswers.contains(SpatialRelation.ABOVE))
    }

    @Test
    fun `can generate LEFT_OF spatial task`() {
        val scene = MathFirstGradeSpatialWorld.createScene()
        val task = MathFirstGradeSpatialWorld.generateSpatialTask(scene, SpatialRelation.LEFT_OF)

        assertNotNull(task)
        assertEquals(SpatialRelation.LEFT_OF, task!!.correctAnswer)
    }

    // ─── Spatial Relations Coverage ───

    @Test
    fun `all 12 spatial relations have Spanish labels`() {
        assertEquals(12, SpatialRelation.entries.size)
        for (relation in SpatialRelation.entries) {
            assertTrue(
                "Relation $relation must have a non-blank Spanish label",
                relation.spanishLabel.isNotBlank()
            )
        }
    }

    @Test
    fun `world ID is valid`() {
        assertEquals("cr_math_1_feb_spatial", MathFirstGradeSpatialWorld.WORLD_ID.value)
    }
}
