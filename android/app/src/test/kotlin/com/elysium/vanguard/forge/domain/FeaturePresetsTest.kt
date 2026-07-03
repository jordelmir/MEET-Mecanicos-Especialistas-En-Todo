package com.elysium.vanguard.forge.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de los FeaturePresets.
 *
 * Cubren:
 * - Catálogo completo (8 presets, todos V1-supported).
 * - Integridad de los datos (sin negativos, sin NaN/Inf, sin blank names).
 * - Generación de ParametricFeature a partir de un preset.
 */
class FeaturePresetsTest {

    @Test
    fun `catalog has at least 30 presets (8 generic + automotive)`() {
        // 8 genéricos + 25 automotriz = 33 al momento de este test.
        // Ajustar floor según campañas de adición; este test es anti-regresión
        // para que nadie borre presets accidentalmente.
        assertTrue(
            "Catálogo demasiado pequeño: ${featurePresets.size} presets (esperado >= 30)",
            featurePresets.size >= 30
        )
    }

    @Test
    fun `catalog has at least 8 generic primitive presets`() {
        // Caja, Cilindro, Tubo, Placa, Esfera, Cono, Perfil L, Perfil U.
        val generics = listOf(
            "Caja", "Cilindro", "Tubo", "Placa",
            "Esfera", "Cono", "Perfil L", "Perfil U"
        )
        val present = generics.filter { name ->
            featurePresets.any { it.displayName == name }
        }
        assertEquals(
            "Faltan genéricos: ${generics - present.toSet()}",
            generics.size, present.size
        )
    }

    @Test
    fun `every preset has a non-empty defaultParameters map`() {
        val empty = featurePresets.filter { it.defaultParameters.isEmpty() }
        assertTrue(
            "Presets sin parámetros por defecto: ${empty.map { it.displayName }}",
            empty.isEmpty()
        )
    }

    @Test
    fun `every parameter key is non-blank`() {
        featurePresets.forEach { preset ->
            preset.defaultParameters.keys.forEach { key ->
                assertTrue(
                    "Preset '${preset.displayName}' tiene clave vacía",
                    key.isNotBlank()
                )
            }
        }
    }

    @Test
    fun `no parameter value is negative`() {
        featurePresets.forEach { preset ->
            preset.defaultParameters.forEach { (key, value) ->
                assertTrue(
                    "Preset '${preset.displayName}' parámetro '$key' = $value (negativo)",
                    value >= 0.0
                )
            }
        }
    }

    @Test
    fun `no parameter value is NaN or infinite`() {
        featurePresets.forEach { preset ->
            preset.defaultParameters.forEach { (key, value) ->
                assertTrue(
                    "Preset '${preset.displayName}' parámetro '$key' = $value (no finito)",
                    value.isFinite()
                )
            }
        }
    }

    @Test
    fun `every preset has a non-blank display name`() {
        val blanks = featurePresets.filter { it.displayName.isBlank() }
        assertTrue(blanks.isEmpty())
    }

    @Test
    fun `every preset has a non-blank short spec`() {
        val blanks = featurePresets.filter { it.shortSpec.isBlank() }
        assertTrue(blanks.isEmpty())
    }

    @Test
    fun `every preset type is V1-supported by the geometry compiler`() {
        val unsupported = featurePresets.filter { !it.type.supportedV1 }
        assertTrue(
            "Presets con tipos no soportados V1: ${unsupported.map { it.type.name }}",
            unsupported.isEmpty()
        )
    }

    @Test
    fun `catalog covers all V1 primitive and profile types`() {
        // Solo verificamos los tipos que prometen tener preset dedicado:
        // las 6 primitivas básicas + los 2 profiles.
        // HOLE y CIRCULAR_PATTERN están soportados V1 pero no son piezas
        // completas (son operaciones sobre otras features), por lo que no
        // tienen preset dedicado.
        val expectedTypes = setOf(
            FeatureType.BOX, FeatureType.CYLINDER, FeatureType.TUBE,
            FeatureType.PLATE, FeatureType.SPHERE, FeatureType.CONE,
            FeatureType.PROFILE_L, FeatureType.PROFILE_U
        )
        val coveredTypes = featurePresets.map { it.type }.toSet()
        expectedTypes.forEach { type ->
            assertTrue(
                "Tipo V1 '${type.name}' no tiene preset dedicado",
                type in coveredTypes
            )
        }
    }

    @Test
    fun `multiple presets per type are allowed`() {
        // En el catálogo automotriz varios tipos se repiten (ej. CYLINDER
        // aparece en Pistón, Árbol, Disco de freno, etc.). Esto es intencional:
        // cada preset es una variante con dimensiones distintas.
        val byType = featurePresets.groupBy { it.type }
        val withMultiple = byType.filter { it.value.size > 1 }
        assertTrue(
            "Esperábamos al menos un tipo con múltiples presets: ${withMultiple.keys.map { it.name }}",
            withMultiple.isNotEmpty()
        )
    }

    @Test
    fun `preset produces a valid ParametricFeature when applied`() {
        val preset = featurePresets.first { it.type == FeatureType.BOX }
        val feature = ParametricFeature(
            id = "f_test_1",
            type = preset.type,
            name = preset.displayName,
            parameters = preset.defaultParameters
        )
        assertEquals(FeatureType.BOX, feature.type)
        assertEquals("Caja", feature.name)
        assertEquals(3, feature.parameters.size)
        assertEquals(100.0, feature.parameters["length"] ?: 0.0, 0.001)
        assertEquals(50.0, feature.parameters["width"] ?: 0.0, 0.001)
        assertEquals(30.0, feature.parameters["height"] ?: 0.0, 0.001)
    }

    @Test
    fun `every preset can be turned into a ParametricFeature without throwing`() {
        featurePresets.forEach { preset ->
            val feature = ParametricFeature(
                id = "f_${preset.type.name.lowercase()}",
                type = preset.type,
                name = preset.displayName,
                parameters = preset.defaultParameters
            )
            assertNotNull(feature)
            assertFalse(
                "Feature id vacío para preset ${preset.displayName}",
                feature.id.isBlank()
            )
        }
    }

    @Test
    fun `BOX preset has length width and height`() {
        val preset = presetsById[PresetId.BOX]!!
        assertTrue(preset.defaultParameters.containsKey("length"))
        assertTrue(preset.defaultParameters.containsKey("width"))
        assertTrue(preset.defaultParameters.containsKey("height"))
    }

    @Test
    fun `CYLINDER preset has diameter and height`() {
        val preset = presetsById[PresetId.CYLINDER]!!
        assertTrue(preset.defaultParameters.containsKey("diameter"))
        assertTrue(preset.defaultParameters.containsKey("height"))
    }

    @Test
    fun `TUBE preset has inner and outer diameters`() {
        val preset = presetsById[PresetId.TUBE]!!
        assertTrue(preset.defaultParameters.containsKey("innerDiameter"))
        assertTrue(preset.defaultParameters.containsKey("outerDiameter"))
        val inner = preset.defaultParameters["innerDiameter"]!!
        val outer = preset.defaultParameters["outerDiameter"]!!
        assertTrue(
            "Tubo con inner ($inner) >= outer ($outer)",
            inner < outer
        )
    }
}