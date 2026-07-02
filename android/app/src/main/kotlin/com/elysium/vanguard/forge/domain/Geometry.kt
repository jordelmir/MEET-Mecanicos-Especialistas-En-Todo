package com.elysium.vanguard.forge.domain

import kotlinx.serialization.Serializable

/**
 * Vértice de malla 3D generada por ForgeGeometryCompiler.
 * Se mantiene mínimo: posición, normal opcional, color opcional.
 */
@Serializable
data class CompiledVertex(
    val x: Float, val y: Float, val z: Float,
    val nx: Float = 0f, val ny: Float = 0f, val nz: Float = 1f,
    val r: Float = 0.7f, val g: Float = 0.7f, val b: Float = 0.72f,
    val a: Float = 1f
) {
    init {
        listOf(x, y, z, nx, ny, nz, r, g, b, a).forEach {
            require(it.isFinite()) { "CompiledVertex fields must be finite" }
        }
        require(a in 0f..1f) { "alpha must be in [0,1]" }
    }
}

/**
 * Cara triangular de la malla.
 */
@Serializable
data class CompiledFace(val a: Int, val b: Int, val c: Int) {
    init {
        require(a >= 0 && b >= 0 && c >= 0) { "Face indices must be non-negative" }
    }
}

/**
 * Malla compilada: lista de vértices + caras + metadata mínima.
 */
@Serializable
data class CompiledMesh(
    val vertices: List<CompiledVertex>,
    val faces: List<CompiledFace>,
    val min: Vector3Data = Vector3Data.ZERO,
    val max: Vector3Data = Vector3Data.ZERO
) {
    val isEmpty: Boolean get() = vertices.isEmpty() || faces.isEmpty()
}

/**
 * Resultado de compilar una ForgePart a mallas. Soporta fallback a placeholder.
 */
@Serializable
data class GeometryCompileResult(
    val mesh: CompiledMesh,
    val boundingBox: BoundingBox,
    val collisionShape: CollisionShape,
    val cacheKey: String,
    val usedFallback: Boolean = false,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList()
) {
    val isValid: Boolean get() = errors.isEmpty() && !mesh.isEmpty
}

/**
 * Resultado agregado de compilar un assembly: mapa instanceId → GeometryCompileResult.
 */
@Serializable
data class AssemblyGeometryResult(
    val instances: Map<String, GeometryCompileResult>,
    val aggregateBoundingBox: BoundingBox
) {
    val allValid: Boolean get() = instances.values.all { it.isValid }
}

@Serializable
data class BoundingBox(
    val min: Vector3Data,
    val max: Vector3Data
) {
    val size: Vector3Data get() = max - min
    val center: Vector3Data get() = (min + max) * 0.5

    fun intersects(other: BoundingBox): Boolean {
        return min.x <= other.max.x && max.x >= other.min.x &&
                min.y <= other.max.y && max.y >= other.min.y &&
                min.z <= other.max.z && max.z >= other.min.z
    }
}

@Serializable
sealed class CollisionShape {
    @Serializable
    data class BoxShape(val halfExtents: Vector3Data) : CollisionShape()
    @Serializable
    data class SphereShape(val radius: Double) : CollisionShape()
    @Serializable
    data class CylinderShape(val radius: Double, val height: Double) : CollisionShape()
    @Serializable
    data class CompoundShape(val shapes: List<CollisionShape>) : CollisionShape()
}

/**
 * Resultado de validación geométrica. Nunca lanza excepción — siempre devuelve
 * el resultado para que la UI pueda mostrar advertencias en lugar de crashear.
 */
@Serializable
data class GeometryValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val suggestedDefaults: Map<String, Double> = emptyMap()
) {
    companion object {
        val OK = GeometryValidationResult(true)
        fun invalid(reason: String, suggestion: Map<String, Double> = emptyMap()) =
            GeometryValidationResult(false, errors = listOf(reason), suggestedDefaults = suggestion)
    }
}