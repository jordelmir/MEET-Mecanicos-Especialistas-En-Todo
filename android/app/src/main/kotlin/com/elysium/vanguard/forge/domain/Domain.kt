package com.elysium.vanguard.forge.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.sqrt

/**
 * Vector 3D inmutable para Forge. Triple-precision (Double) en dominio, Float al compilar mesh.
 * Regla: nunca propagar NaN/Infinity. isFinite() se valida en init.
 *
 * Serialización: objeto `{"x":..,"y":..,"z":..}` por defecto.
 * Acepta también la forma compacta `[x, y, z]` cuando el JSON la usa (común en seeds).
 */
@Serializable(with = Vector3DataSerializer::class)
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

/**
 * KSerializer para [Vector3Data] que acepta tanto el objeto `{"x":..,"y":..,"z":..}`
 * como la forma compacta `[x, y, z]` (común en seeds y entradas del usuario).
 *
 * Al serializar, siempre emite la forma objeto canónica.
 */
@OptIn(kotlinx.serialization.InternalSerializationApi::class)
object Vector3DataSerializer : KSerializer<Vector3Data> {
    override val descriptor: SerialDescriptor =
        buildSerialDescriptor("Vector3Data", SerialKind.CONTEXTUAL)

    override fun serialize(encoder: Encoder, value: Vector3Data) {
        val jsonEncoder = encoder as? kotlinx.serialization.json.JsonEncoder
        if (jsonEncoder != null) {
            val obj = JsonObject(mapOf(
                "x" to JsonPrimitive(value.x),
                "y" to JsonPrimitive(value.y),
                "z" to JsonPrimitive(value.z)
            ))
            jsonEncoder.encodeJsonElement(obj)
            return
        }
        // Fallback binario — serializa como 3 doubles en orden.
        val composite = encoder.beginStructure(descriptor)
        composite.encodeDoubleElement(descriptor, 0, value.x)
        composite.encodeDoubleElement(descriptor, 1, value.y)
        composite.encodeDoubleElement(descriptor, 2, value.z)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): Vector3Data {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder != null) {
            val element = jsonDecoder.decodeJsonElement()
            when (element) {
                is kotlinx.serialization.json.JsonArray -> {
                    require(element.size == 3) {
                        "Vector3Data array form must have exactly 3 elements, got ${element.size}"
                    }
                    val x = element[0].jsonPrimitive.double
                    val y = element[1].jsonPrimitive.double
                    val z = element[2].jsonPrimitive.double
                    return Vector3Data(x, y, z)
                }
                is JsonObject -> {
                    val x = element["x"]?.jsonPrimitive?.double ?: 0.0
                    val y = element["y"]?.jsonPrimitive?.double ?: 0.0
                    val z = element["z"]?.jsonPrimitive?.double ?: 0.0
                    return Vector3Data(x, y, z)
                }
                else -> error("Vector3Data must be a JSON object {x,y,z} or array [x,y,z], got: $element")
            }
        }
        var x = 0.0; var y = 0.0; var z = 0.0
        val composite = decoder.beginStructure(descriptor)
        loop@ while (true) {
            val n = composite.decodeElementIndex(descriptor)
            if (n == CompositeDecoder.DECODE_DONE) break@loop
            when (n) {
                0 -> x = composite.decodeDoubleElement(descriptor, 0)
                1 -> y = composite.decodeDoubleElement(descriptor, 1)
                2 -> z = composite.decodeDoubleElement(descriptor, 2)
            }
        }
        composite.endStructure(descriptor)
        return Vector3Data(x, y, z)
    }
}