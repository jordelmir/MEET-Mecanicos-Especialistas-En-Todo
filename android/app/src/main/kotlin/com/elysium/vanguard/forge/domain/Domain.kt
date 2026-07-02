package com.elysium.vanguard.forge.domain

import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/**
 * Vector 3D inmutable para Forge. Triple-precision (Double) en dominio, Float al compilar mesh.
 * Regla: nunca propagar NaN/Infinity. isFinite() se valida en init.
 */
@Serializable
data class Vector3Data(
    val x: Double,
    val y: Double,
    val z: Double
) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite()) {
            "Vector3Data fields must be finite (no NaN/Infinity)"
        }
    }

    operator fun plus(o: Vector3Data) = Vector3Data(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vector3Data) = Vector3Data(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = Vector3Data(x * s, y * s, z * s)

    val length: Double get() = sqrt(x * x + y * y + z * z)

    fun normalized(): Vector3Data {
        val l = length
        if (l == 0.0) return ZERO
        return Vector3Data(x / l, y / l, z / l)
    }

    fun cross(o: Vector3Data): Vector3Data = Vector3Data(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x
    )

    fun dot(o: Vector3Data): Double = x * o.x + y * o.y + z * o.z

    companion object {
        val ZERO = Vector3Data(0.0, 0.0, 0.0)
        val UNIT_X = Vector3Data(1.0, 0.0, 0.0)
        val UNIT_Y = Vector3Data(0.0, 1.0, 0.0)
        val UNIT_Z = Vector3Data(0.0, 0.0, 1.0)
    }
}

/**
 * Transform afín: traslación + rotación Euler en grados. Sin escala V1 (todo en mm).
 */
@Serializable
data class TransformData(
    val position: Vector3Data = Vector3Data.ZERO,
    val rotationDeg: Vector3Data = Vector3Data.ZERO
) {
    init {
        listOf(rotationDeg.x, rotationDeg.y, rotationDeg.z).forEach {
            require(it.isFinite()) { "rotationDeg components must be finite" }
        }
    }

    fun translatedBy(delta: Vector3Data) = copy(position = position + delta)
}

/**
 * Rango numérico educativo (para ValueRange esperado / observado en diagnóstico).
 */
@Serializable
data class ValueRange(
    val min: Double,
    val max: Double,
    val unit: String = ""
) {
    init {
        require(min.isFinite() && max.isFinite()) { "ValueRange must be finite" }
        require(min <= max) { "ValueRange.min must be <= max" }
    }

    fun contains(value: Double): Boolean = value in min..max

    companion object {
        fun single(value: Double, unit: String = "") = ValueRange(value, value, unit)
        fun celsius(min: Double, max: Double) = ValueRange(min, max, "°C")
        fun rpm(min: Double, max: Double) = ValueRange(min, max, "rpm")
        fun kpa(min: Double, max: Double) = ValueRange(min, max, "kPa")
        fun volts(min: Double, max: Double) = ValueRange(min, max, "V")
    }
}