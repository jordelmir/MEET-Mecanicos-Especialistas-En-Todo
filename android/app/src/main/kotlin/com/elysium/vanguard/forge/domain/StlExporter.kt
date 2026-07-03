package com.elysium.vanguard.forge.domain

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Exportador STL (Stereolithography) binario para [CompiledMesh].
 *
 * STL binario es un formato simple:
 *  - 80 bytes header (puede ser cualquier string descriptivo)
 *  - 4 bytes uint32: numero de triangulos
 *  - Por cada triangulo: 12 floats (3 normales + 3 vértices × 3) = 50 bytes
 *
 * Total = 80 + 4 + N * 50 bytes.
 *
 * STL NO soporta normales por vértice (solo una normal por triángulo).
 * Promediamos las normales de los 3 vértices para producir la normal del
 * triángulo. La pérdida de fidelidad es aceptable para preview.
 *
 * Visibilidad `internal`: usado por forge module. Si en el futuro se quiere
 * exponer como utility, se eleva a `public`.
 */
internal object StlExporter {

    /**
     * Exporta una malla a bytes STL binario.
     *
     * @param mesh la malla a exportar (sus normales por vértice se promedian).
     * @param header 80 bytes de descripción que STL pone al inicio.
     *   Si es null, usa "Vanguard Forge export".
     * @return byteArray STL binario listo para escribir a archivo.
     */
    fun toBinaryStl(
        mesh: CompiledMesh,
        header: String = "Vanguard Forge export"
    ): ByteArray {
        require(mesh.isEmpty.not() || mesh.faces.isEmpty()) {
            "Malla vacía no se puede exportar"
        }
        // 80 bytes header (rellenamos con espacios, sin caracteres nulos).
        val headerPadded = (header.take(80)).padEnd(80, ' ').toByteArray(Charsets.UTF_8)

        // Buffer con little-endian (STL es little-endian por convención).
        val totalSize = 80 + 4 + mesh.faces.size * 50
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // Header (80 bytes exactos).
        buffer.put(headerPadded)

        // Numero de triangulos.
        buffer.putInt(mesh.faces.size)

        // Cada triangulo: 50 bytes.
        for (face in mesh.faces) {
            val a = mesh.vertices[face.a]
            val b = mesh.vertices[face.b]
            val c = mesh.vertices[face.c]

            // Normal promedio del triangulo.
            val nx = (a.nx + b.nx + c.nx) / 3f
            val ny = (a.ny + b.ny + c.ny) / 3f
            val nz = (a.nz + b.nz + c.nz) / 3f
            val mag = sqrt(nx * nx + ny * ny + nz * nz)
            val (fnx, fny, fnz) = if (mag > 0f) {
                Triple(nx / mag, ny / mag, nz / mag)
            } else {
                Triple(0f, 0f, 1f)  // fallback si la normal colapsa
            }

            buffer.putFloat(fnx)
            buffer.putFloat(fny)
            buffer.putFloat(fnz)

            buffer.putFloat(a.x)
            buffer.putFloat(a.y)
            buffer.putFloat(a.z)

            buffer.putFloat(b.x)
            buffer.putFloat(b.y)
            buffer.putFloat(b.z)

            buffer.putFloat(c.x)
            buffer.putFloat(c.y)
            buffer.putFloat(c.z)

            // 2 bytes attribute byte count (usualmente 0).
            buffer.putShort(0)
        }

        return buffer.array()
    }

    /**
     * Versión texto del STL (ASCII STL). Útil para debugging y diff-friendly.
     *
     * Formato:
     * ```
     * solid name
     * facet normal nx ny nz
     *   outer loop
     *     vertex x y z
     *     vertex x y z
     *     vertex x y z
     *   endloop
     * endfacet
     * ...
     * endsolid name
     * ```
     */
    fun toAsciiStl(mesh: CompiledMesh, name: String = "vanguard_forge_part"): String {
        val sb = StringBuilder()
        sb.appendLine("solid $name")
        for (face in mesh.faces) {
            val a = mesh.vertices[face.a]
            val b = mesh.vertices[face.b]
            val c = mesh.vertices[face.c]
            val nx = (a.nx + b.nx + c.nx) / 3f
            val ny = (a.ny + b.ny + c.ny) / 3f
            val nz = (a.nz + b.nz + c.nz) / 3f
            val mag = sqrt(nx * nx + ny * ny + nz * nz)
            val (fnx, fny, fnz) = if (mag > 0f) {
                Triple(nx / mag, ny / mag, nz / mag)
            } else {
                Triple(0f, 0f, 1f)
            }
            sb.appendLine("facet normal $fnx $fny $fnz")
            sb.appendLine("  outer loop")
            sb.appendLine("    vertex ${a.x} ${a.y} ${a.z}")
            sb.appendLine("    vertex ${b.x} ${b.y} ${b.z}")
            sb.appendLine("    vertex ${c.x} ${c.y} ${c.z}")
            sb.appendLine("  endloop")
            sb.appendLine("endfacet")
        }
        sb.appendLine("endsolid $name")
        return sb.toString()
    }
}