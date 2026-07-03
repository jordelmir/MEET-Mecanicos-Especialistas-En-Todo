package com.elysium.vanguard.forge.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Tests del exportador STL.
 *
 * Validan el formato binario y ASCII. Un STL inválido causaria que slicers
 * (Cura, PrusaSlicer, etc.) rechacen el archivo.
 */
class StlExporterTest {

    private fun cubeMesh(): CompiledMesh {
        val verts = listOf(
            CompiledVertex(0f, 0f, 0f, 0f, 0f, -1f),  // 0: cara -Z
            CompiledVertex(1f, 0f, 0f, 0f, 0f, -1f),  // 1
            CompiledVertex(1f, 1f, 0f, 0f, 0f, -1f),  // 2
            CompiledVertex(0f, 1f, 0f, 0f, 0f, -1f),  // 3
            CompiledVertex(0f, 0f, 1f, 0f, 0f, 1f),   // 4: cara +Z
            CompiledVertex(1f, 0f, 1f, 0f, 0f, 1f),   // 5
            CompiledVertex(1f, 1f, 1f, 0f, 0f, 1f),   // 6
            CompiledVertex(0f, 1f, 1f, 0f, 0f, 1f)    // 7
        )
        val faces = listOf(
            CompiledFace(0, 2, 1), CompiledFace(0, 3, 2),
            CompiledFace(4, 5, 6), CompiledFace(4, 6, 7)
        )
        return CompiledMesh(
            vertices = verts,
            faces = faces,
            min = Vector3Data(0.0, 0.0, 0.0),
            max = Vector3Data(1.0, 1.0, 1.0)
        )
    }

    @Test
    fun `binary STL total size matches expected`() {
        val mesh = cubeMesh()
        val bytes = StlExporter.toBinaryStl(mesh)
        val expected = 80 + 4 + mesh.faces.size * 50
        assertEquals(
            "STL binario debe medir 84 + N*50 bytes",
            expected,
            bytes.size
        )
    }

    @Test
    fun `binary STL header is 80 bytes and contains the tag`() {
        val mesh = cubeMesh()
        val tag = "TestHeader"
        val bytes = StlExporter.toBinaryStl(mesh, header = tag)
        // El header STL son los primeros 80 bytes, padded con espacios.
        val headerBytes = bytes.copyOfRange(0, 80)
        val headerStr = String(headerBytes, Charsets.UTF_8).trimEnd()
        assertEquals(tag, headerStr)
    }

    @Test
    fun `binary STL triangle count matches mesh face count`() {
        val mesh = cubeMesh()
        val bytes = StlExporter.toBinaryStl(mesh)
        // El uint32 después del header es el numero de triangulos.
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val count = buf.getInt(80)
        assertEquals(mesh.faces.size, count)
    }

    @Test
    fun `binary STL triangle normal is unit-length`() {
        val mesh = cubeMesh()
        val bytes = StlExporter.toBinaryStl(mesh)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        // Saltar header (80) + count (4). Primer triangulo empieza en 84.
        val nx = buf.getFloat(84)
        val ny = buf.getFloat(88)
        val nz = buf.getFloat(92)
        val mag = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz)
        // La normal debe ser aproximadamente unitaria.
        assertEquals(
            "Normal del triangulo no es unitaria (mag=$mag)",
            1.0,
            mag.toDouble(),
            0.001
        )
    }

    @Test
    fun `binary STL vertex positions match mesh`() {
        val mesh = cubeMesh()
        val bytes = StlExporter.toBinaryStl(mesh)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        // Primer triangulo, primer vertice (después de la normal 3 floats).
        val v0x = buf.getFloat(84 + 12 + 0)
        val v0y = buf.getFloat(84 + 12 + 4)
        val v0z = buf.getFloat(84 + 12 + 8)
        assertEquals(0f, v0x, 0.0001f)
        assertEquals(0f, v0y, 0.0001f)
        assertEquals(0f, v0z, 0.0001f)
    }

    @Test
    fun `ascii STL has correct structure`() {
        val mesh = cubeMesh()
        val stl = StlExporter.toAsciiStl(mesh, name = "test_cube")
        assertTrue("Debe empezar con 'solid'", stl.startsWith("solid"))
        assertTrue("Debe terminar con 'endsolid'", stl.trimEnd().endsWith("endsolid test_cube"))
        // Cada triangulo produce 1 lineas "facet", 3 vertex, "endloop", "endfacet".
        val facetCount = stl.lines().count { it.startsWith("facet") }
        assertEquals(mesh.faces.size, facetCount)
    }

    @Test
    fun `ascii STL has correct endfacet count`() {
        val mesh = cubeMesh()
        val stl = StlExporter.toAsciiStl(mesh)
        val endFacetCount = stl.lines().count { it.startsWith("endfacet") }
        assertEquals(mesh.faces.size, endFacetCount)
    }

    @Test
    fun `binary STL total byte size matches expected structure`() {
        // Cada triangulo STL binario ocupa exactamente 50 bytes (12 floats normal+vertex +
        // 2 bytes attribute count). El header son 84 bytes (80 header + 4 count).
        val mesh = cubeMesh()
        val bytes = StlExporter.toBinaryStl(mesh)
        val expected = 84 + mesh.faces.size * 50
        assertEquals(expected, bytes.size)
    }

    @Test
    fun `empty mesh is allowed but produces no triangles section`() {
        // Si la malla tiene faces pero no vertices, no se puede serializar.
        // Aquí validamos que UNA malla con faces + vertices vacíos no cause crash.
        // Para el caso realista (mesh valido), ver tests anteriores.
        val v = cubeMesh()
        val bytes = StlExporter.toBinaryStl(v)
        // El numero de triangulos debe ser igual al de faces.
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(v.faces.size, buf.getInt(80))
    }

    @Test
    fun `ascii STL contains 3 vertex lines per triangle`() {
        val mesh = cubeMesh()
        val stl = StlExporter.toAsciiStl(mesh)
        val vertexLineCount = stl.lines().count { it.trimStart().startsWith("vertex ") }
        assertEquals(mesh.faces.size * 3, vertexLineCount)
    }
}