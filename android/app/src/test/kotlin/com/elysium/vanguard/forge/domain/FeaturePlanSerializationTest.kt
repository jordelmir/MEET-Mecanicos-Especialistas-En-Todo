package com.elysium.vanguard.forge.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de serialización JSON de [FeaturePlan] y [FeaturePreset].
 *
 * Valida el contrato del ADR 0001: cada `data class` debe poder serializarse
 * a JSON y deserializarse de vuelta, produciendo instancias equivalentes.
 * Esto es la base para "templates compartibles entre piezas" — si el
 * roundtrip falla, no podemos guardar templates en disco ni compartirlos
 * entre proyectos.
 *
 * Si algún test rompe, probablemente:
 *  - Falta `@Serializable` en algún tipo.
 *  - Falta un serializador concreto para Map<String, Double>.
 *  - Hay un sealed interface que kotlinx.serialization no sabe cómo
 *    deserializar sin un polimórfico config.
 */
class FeaturePlanSerializationTest {

    /**
     * Json instance con configuración explícita. Habilitamos:
     *  - encodeDefaults: serializa campos con valor por defecto.
     *  - explicitNulls: para detectar nulos accidentales.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun `FeaturePreset roundtrips through JSON`() {
        val original = preset(PresetId.ENGINE_BLOCK)
        val encoded = json.encodeToString(original)
        assertTrue("Encoded string vacía", encoded.isNotBlank())
        val decoded = json.decodeFromString<FeaturePreset>(encoded)
        assertEquals(original.id, decoded.id)
        assertEquals(original.type, decoded.type)
        assertEquals(original.displayName, decoded.displayName)
        assertEquals(original.shortSpec, decoded.shortSpec)
        assertEquals(original.defaultParameters, decoded.defaultParameters)
    }

    @Test
    fun `SingleFeaturePlan roundtrips through JSON`() {
        val originalTyped = SingleFeaturePlan(
            preset = preset(PresetId.PISTON),
            positionOffset = Vector3Data(10.0, 20.0, 30.0)
        )
        val original: FeaturePlan = originalTyped
        val encoded = json.encodeToString(original)
        assertTrue(encoded.isNotBlank())
        val decoded = json.decodeFromString<FeaturePlan>(encoded)
        assertTrue("Tipo equivocado tras decode", decoded is SingleFeaturePlan)
        val decodedTyped = decoded as SingleFeaturePlan
        assertEquals(originalTyped.preset.id, decodedTyped.preset.id)
        assertEquals(originalTyped.positionOffset.x, decodedTyped.positionOffset.x, 1e-6)
        assertEquals(originalTyped.positionOffset.y, decodedTyped.positionOffset.y, 1e-6)
        assertEquals(originalTyped.positionOffset.z, decodedTyped.positionOffset.z, 1e-6)
    }

    @Test
    fun `LinearArrayPlan roundtrips through JSON`() {
        val originalTyped = LinearArrayPlan(
            preset = preset(PresetId.PISTON),
            count = 4,
            spacing = 88.0,
            axis = LinearArrayPlan.Axis.X
        )
        val original: FeaturePlan = originalTyped
        val encoded = json.encodeToString(original)
        assertTrue(encoded.isNotBlank())
        val decoded = json.decodeFromString<FeaturePlan>(encoded)
        assertTrue(decoded is LinearArrayPlan)
        val decodedTyped = decoded as LinearArrayPlan
        assertEquals(originalTyped.count, decodedTyped.count)
        assertEquals(originalTyped.spacing, decodedTyped.spacing, 1e-6)
        assertEquals(originalTyped.axis, decodedTyped.axis)
    }

    @Test
    fun `CircularPatternPlan roundtrips through JSON`() {
        val originalTyped = CircularPatternPlan(
            preset = preset(PresetId.PISTON),
            count = 8,
            radius = 80.0,
            axis = CircularPatternPlan.Axis.Y_PERPENDICULAR,
            startAngleRad = Math.PI / 4
        )
        val original: FeaturePlan = originalTyped
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<FeaturePlan>(encoded)
        assertTrue(decoded is CircularPatternPlan)
        val decodedTyped = decoded as CircularPatternPlan
        assertEquals(originalTyped.count, decodedTyped.count)
        assertEquals(originalTyped.radius, decodedTyped.radius, 1e-6)
        assertEquals(originalTyped.startAngleRad, decodedTyped.startAngleRad, 1e-6)
    }

    @Test
    fun `CompositePlan roundtrips through JSON`() {
        val originalTyped = CompositePlan(
            name = "Test composite",
            children = listOf(
                SingleFeaturePlan(preset(PresetId.ENGINE_BLOCK)),
                LinearArrayPlan(
                    preset = preset(PresetId.PISTON),
                    count = 4,
                    spacing = 88.0,
                    axis = LinearArrayPlan.Axis.X
                )
            ),
            centerOrigin = true
        )
        val original: FeaturePlan = originalTyped
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<FeaturePlan>(encoded)
        assertTrue(decoded is CompositePlan)
        val decodedTyped = decoded as CompositePlan
        assertEquals(originalTyped.name, decodedTyped.name)
        assertEquals(originalTyped.centerOrigin, decodedTyped.centerOrigin)
        assertEquals(originalTyped.children.size, decodedTyped.children.size)
    }

    @Test
    fun `EngineCatalog v8 roundtrips and re-instantiates identically`() {
        // El test más importante: garantizamos que un engine complejo
        // (9 features con IDs únicos + centerOrigin) sobrevive el roundtrip
        // JSON y produce las mismas features tras la deserialización.
        val original = EngineCatalog.v8
        val encoded = json.encodeToString<FeaturePlan>(original)
        assertTrue(encoded.isNotBlank())

        val decoded = json.decodeFromString<FeaturePlan>(encoded)

        // Re-instanciar ambos y comparar.
        val originalFeatures = original.instantiate()
        val decodedFeatures = decoded.instantiate()
        assertEquals(
            "Cantidad de features tras roundtrip",
            originalFeatures.size,
            decodedFeatures.size
        )
        // Comparar cada feature por id, type y posición (todos los features).
        originalFeatures.zip(decodedFeatures).forEach { (a, b) ->
            assertEquals("id", a.id, b.id)
            assertEquals("type", a.type, b.type)
            assertEquals("position.x", a.position.x, b.position.x, 1e-6)
            assertEquals("position.y", a.position.y, b.position.y, 1e-6)
            assertEquals("position.z", a.position.z, b.position.z, 1e-6)
            assertEquals("parameters", a.parameters, b.parameters)
        }
    }

    @Test
    fun `CompositePlan JSON produces valid output for all engineCatalog engines`() {
        // Cada engine del catálogo debe poder serializarse sin lanzar excepción.
        EngineCatalog.allEngines.forEach { engine ->
            val encoded = json.encodeToString<FeaturePlan>(engine)
            assertTrue(
                "${engine.name} encoded vacío",
                encoded.isNotBlank()
            )
            // Y debe poder deserializarse (roundtrip completo).
            val decoded = json.decodeFromString<FeaturePlan>(encoded)
            assertNotNull("${engine.name} decoded como null", decoded)
            assertEquals(
                "${engine.name} no es Composite tras roundtrip",
                decoded is CompositePlan,
                true
            )
        }
    }

    @Test
    fun `Manual 5-speed transmission survives JSON roundtrip`() {
        // El transmission tiene 2 SingleFeatures + 2 LinearArrays, total 12
        // features. Era donde detectamos el bug de IDs colisionando. Aquí
        // validamos que el fix de IDs + el JSON funcionan juntos.
        val original = EngineCatalog.manualTransmission5spd
        val encoded = json.encodeToString<FeaturePlan>(original)
        val decoded = json.decodeFromString<FeaturePlan>(encoded)

        val originalFeatures = original.instantiate()
        val decodedFeatures = decoded.instantiate()
        assertEquals(12, originalFeatures.size)
        assertEquals(12, decodedFeatures.size)
        originalFeatures.zip(decodedFeatures).forEach { (a, b) ->
            assertEquals("id en transmission", a.id, b.id)
        }
    }

    @Test
    fun `HybridV8 (nested composition) roundtrips`() {
        // hybridV8 contiene v8 como child — prueba la composición recursiva
        // via serialización.
        val original = EngineCatalog.hybridV8
        val encoded = json.encodeToString<FeaturePlan>(original)
        val decoded = json.decodeFromString<FeaturePlan>(encoded)
        assertEquals(original.instantiate().size, decoded.instantiate().size)
    }

    @Test
    fun `JSON encoded FeaturePlan is readable text`() {
        // Smoke test: el JSON debe ser texto legible (no binario opaco).
        // Esto valida que podríamos mostrar templates al usuario como JSON.
        val plan: FeaturePlan = EngineCatalog.v8
        val encoded = json.encodeToString(plan)
        assertTrue(encoded.startsWith("{"))
        assertTrue(encoded.endsWith("}"))
        assertTrue("Encoded contiene 'CompositePlan'", encoded.contains("CompositePlan") || encoded.contains("composite"))
    }
}