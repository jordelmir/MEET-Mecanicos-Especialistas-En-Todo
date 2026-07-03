package com.elysium.vanguard.forge.presentation.components

import com.elysium.vanguard.forge.domain.CompiledFace
import com.elysium.vanguard.forge.domain.CompiledMesh
import com.elysium.vanguard.forge.domain.CompiledVertex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * Tests del renderer isométrico.
 *
 * Cubren:
 *  - **Determinismo**: misma malla + mismos parámetros → mismo output.
 *  - **Wrap de yaw**: el renderer es invariante a `yaw + 2π` (periodicidad trig).
 *  - **Pitch ≠ yaw**: el comportamiento de pitch es ortogonal a yaw.
 *  - **Mallas vacías**: no crashea, retorna lista vacía.
 */
class IsometricMeshRendererTest {

    /**
     * Construye un cubo unitario con normales y color gris para usar en tests.
     * Es una malla mínima viable para las proyecciones.
     */
    private fun unitCubeMesh(): CompiledMesh {
        // 8 vértices de un cubo [0,1]^3
        val verts = listOf(
            CompiledVertex(0f, 0f, 0f), // 0
            CompiledVertex(1f, 0f, 0f), // 1
            CompiledVertex(1f, 1f, 0f), // 2
            CompiledVertex(0f, 1f, 0f), // 3
            CompiledVertex(0f, 0f, 1f), // 4
            CompiledVertex(1f, 0f, 1f), // 5
            CompiledVertex(1f, 1f, 1f), // 6
            CompiledVertex(0f, 1f, 1f)  // 7
        )
        // 12 triángulos (2 por cara × 6 caras)
        val faces = listOf(
            // Cara -Z (apuntando al observador en iso inicial)
            CompiledFace(0, 2, 1), CompiledFace(0, 3, 2),
            // Cara +Z
            CompiledFace(4, 5, 6), CompiledFace(4, 6, 7),
            // Cara -Y
            CompiledFace(0, 1, 5), CompiledFace(0, 5, 4),
            // Cara +Y
            CompiledFace(3, 6, 2), CompiledFace(3, 7, 6),
            // Cara -X
            CompiledFace(0, 4, 7), CompiledFace(0, 7, 3),
            // Cara +X
            CompiledFace(1, 2, 6), CompiledFace(1, 6, 5)
        )
        return CompiledMesh(
            vertices = verts,
            faces = faces,
            min = com.elysium.vanguard.forge.domain.Vector3Data(0.0, 0.0, 0.0),
            max = com.elysium.vanguard.forge.domain.Vector3Data(1.0, 1.0, 1.0)
        )
    }

    @Test
    fun `prepareTriangles is deterministic for same input`() {
        val mesh = unitCubeMesh()
        val a = prepareTriangles(mesh, yaw = 0.3f, pitch = 0.1f, applyLighting = true)
        val b = prepareTriangles(mesh, yaw = 0.3f, pitch = 0.1f, applyLighting = true)

        assertEquals("Misma entrada debe producir misma cantidad de triángulos", a.size, b.size)
        for (i in a.indices) {
            val ta = a[i]; val tb = b[i]
            assertEquals("p0.x en triángulo $i", ta.p0.x, tb.p0.x, 1e-6f)
            assertEquals("p0.y en triángulo $i", ta.p0.y, tb.p0.y, 1e-6f)
            assertEquals("p1.x en triángulo $i", ta.p1.x, tb.p1.x, 1e-6f)
            assertEquals("p1.y en triángulo $i", ta.p1.y, tb.p1.y, 1e-6f)
            assertEquals("p2.x en triángulo $i", ta.p2.x, tb.p2.x, 1e-6f)
            assertEquals("p2.y en triángulo $i", ta.p2.y, tb.p2.y, 1e-6f)
            assertEquals("avgZ en triángulo $i", ta.avgZ, tb.avgZ, 1e-6f)
        }
    }

    @Test
    fun `prepareTriangles handles empty mesh gracefully`() {
        val empty = CompiledMesh(
            vertices = emptyList(),
            faces = emptyList()
        )
        val result = prepareTriangles(empty, yaw = 0.5f, pitch = 0.2f, applyLighting = true)
        assertEquals(0, result.size)
    }

    @Test
    fun `renderer is invariant to yaw plus 2 PI`() {
        val mesh = unitCubeMesh()
        // Como sin y cos son periódicas con período 2π, rotar por 2π adicional
        // debe dar exactamente la misma salida. (La tolerancia 1e-5 cubre FP
        // error de sin/cos en valores grandes.)
        val a = prepareTriangles(mesh, yaw = 0.5f, pitch = 0f, applyLighting = false)
        val twoPi = (2 * PI).toFloat()
        val b = prepareTriangles(mesh, yaw = 0.5f + twoPi, pitch = 0f, applyLighting = false)

        assertEquals(a.size, b.size)
        for (i in a.indices) {
            assertEquals("p0.x invarianza yaw+2π tri $i", a[i].p0.x, b[i].p0.x, 1e-5f)
            assertEquals("p0.y invarianza yaw+2π tri $i", a[i].p0.y, b[i].p0.y, 1e-5f)
        }
    }

    @Test
    fun `pitch changes the projected position`() {
        val mesh = unitCubeMesh()
        val flat = prepareTriangles(mesh, yaw = 0f, pitch = 0f, applyLighting = false)
        val tilted = prepareTriangles(mesh, yaw = 0f, pitch = 0.5f, applyLighting = false)

        assertEquals(flat.size, tilted.size)
        // Por lo menos algunos triángulos deben haber cambiado.
        val anyDiff = flat.zip(tilted).any { (a, b) ->
            kotlin.math.abs(a.p0.x - b.p0.x) > 1e-4f ||
                kotlin.math.abs(a.p0.y - b.p0.y) > 1e-4f
        }
        assertTrue("Pitch=0 vs pitch=0.5 debe producir posiciones distintas", anyDiff)
    }

    @Test
    fun `lighting off produces simpler colors than lighting on`() {
        val mesh = unitCubeMesh()
        val lit = prepareTriangles(mesh, yaw = 0.3f, pitch = 0.2f, applyLighting = true)
        val unlit = prepareTriangles(mesh, yaw = 0.3f, pitch = 0.2f, applyLighting = false)

        assertEquals(lit.size, unlit.size)
        // Con iluminación, algunos triángulos deben tener colores distintos a
        // su valor sin iluminación. Verificamos que al menos uno difiera.
        val anyColorDiff = lit.zip(unlit).any { (a, b) ->
            kotlin.math.abs(a.finalColor.red - b.finalColor.red) > 1e-4f ||
                kotlin.math.abs(a.finalColor.green - b.finalColor.green) > 1e-4f ||
                kotlin.math.abs(a.finalColor.blue - b.finalColor.blue) > 1e-4f
        }
        assertTrue(
            "Iluminación on vs off debe producir colores distintos",
            anyColorDiff
        )
    }

    @Test
    fun `rotation state defaults are zero`() {
        val state = RotationState()
        assertEquals(0f, state.yaw, 0f)
        assertEquals(0f, state.pitch, 0f)
    }

    @Test
    fun `prepared triangle count matches face count for valid mesh`() {
        val mesh = unitCubeMesh()
        val tris = prepareTriangles(mesh, yaw = 0f, pitch = 0f, applyLighting = false)
        // 12 caras en el cubo unitario → 12 triángulos pre-procesados.
        assertEquals(12, tris.size)
    }
}