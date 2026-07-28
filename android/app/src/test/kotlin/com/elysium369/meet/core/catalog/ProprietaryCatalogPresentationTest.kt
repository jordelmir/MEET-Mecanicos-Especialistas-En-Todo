package com.elysium369.meet.core.catalog

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProprietaryCatalogPresentationTest {
    private fun asset(path: String): File = listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
        File("android/app/src/main/assets/$path"),
    ).firstOrNull(File::isFile) ?: error("Missing asset $path")

    @Test
    fun `technical families cover every proprietary system exactly once`() {
        val manifest = ProprietaryCatalogParser.decodeManifest(
            asset(PROPRIETARY_CATALOG_MANIFEST_ASSET).readText(),
        )

        assertTrue(CatalogSystemFamilies.uncoveredSystemIds(manifest.systems.map { it.id }).isEmpty())
        assertTrue(CatalogSystemFamilies.duplicateSystemIds().isEmpty())
        assertEquals(
            manifest.systems.map { it.id }.toSet(),
            CatalogSystemFamilies.all.flatMap { it.systemIds }.toSet(),
        )
    }

    @Test
    fun `firewall entity resolves conservatively to the real atlas reference`() {
        val atlas = VehicleTechnicalAtlasParser.decode(
            asset(VehicleTechnicalAtlasDescriptors.body.assetPath).readText(),
            VehicleTechnicalAtlasDescriptors.body,
        )
        val candidates = atlas.elements.map {
            CanonicalPartIdentity(it.canonicalId, it.nameOriginal, it.aliases)
        }

        val resolution = resolveCanonicalIdentity(
            sourceName = "Panel cortafuego / firewall",
            preferredCanonicalPrefixes = listOf("body"),
            candidates = candidates,
        )

        assertNotNull(resolution)
        assertEquals("body-0125-panel-cortafuegos", resolution!!.identity.canonicalId)
        assertEquals(
            ProprietaryCanonicalMatchMethod.CONSERVATIVE_NOMINAL_FORM,
            resolution.method,
        )
    }

    @Test
    fun `exact alias is accepted but ambiguous identity is rejected`() {
        val aliasResolution = resolveCanonicalIdentity(
            sourceName = "Computadora de motor",
            preferredCanonicalPrefixes = listOf("electrical"),
            candidates = listOf(
                CanonicalPartIdentity("electrical-0001-ecu", "ECU", listOf("Computadora de motor")),
            ),
        )
        assertEquals(
            ProprietaryCanonicalMatchMethod.EXACT_NAME_OR_ALIAS,
            aliasResolution?.method,
        )

        val ambiguous = resolveCanonicalIdentity(
            sourceName = "Sensor de posición",
            preferredCanonicalPrefixes = listOf("electrical"),
            candidates = listOf(
                CanonicalPartIdentity("electrical-0002-sensor-a", "Sensor de posición", emptyList()),
                CanonicalPartIdentity("electrical-0003-sensor-b", "Sensor de posición", emptyList()),
            ),
        )
        assertNull(ambiguous)
    }

    @Test
    fun `unrelated names never receive a fabricated model`() {
        val resolution = resolveCanonicalIdentity(
            sourceName = "Componente experimental inexistente",
            preferredCanonicalPrefixes = listOf("body"),
            candidates = listOf(
                CanonicalPartIdentity("body-0001-panel", "Panel frontal", emptyList()),
            ),
        )

        assertNull(resolution)
    }

    @Test
    fun `physical identity is separated from tabular procedure text`() {
        assertEquals(
            "Panel frontal",
            physicalComponentName("Panel frontal\tSoldado. Revisar corrosión y reparar por secciones."),
        )
        assertEquals(
            "Sensor de velocidad",
            physicalComponentName("Sensor de velocidad\nProcedimiento de prueba"),
        )
    }
}
