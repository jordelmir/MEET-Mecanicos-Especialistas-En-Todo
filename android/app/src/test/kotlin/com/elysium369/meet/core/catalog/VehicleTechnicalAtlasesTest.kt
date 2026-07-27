package com.elysium369.meet.core.catalog

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleTechnicalAtlasesTest {
    private fun asset(path: String): File = listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
        File("android/app/src/main/assets/$path"),
    ).firstOrNull(File::isFile) ?: error("Missing asset $path")

    private fun atlas(descriptor: VehicleTechnicalAtlasDescriptor): VehicleTechnicalAtlas =
        VehicleTechnicalAtlasParser.decode(asset(descriptor.assetPath).readText(), descriptor)

    @Test
    fun `all three atlases preserve exact traceable inventory`() {
        val atlases = VehicleTechnicalAtlasDescriptors.all.map(::atlas)
        assertEquals(4, atlases.size)
        assertEquals(5_985, atlases.sumOf { it.elements.size })
        assertEquals(110, atlases.sumOf { it.sections.size })
        assertEquals(setOf(838, 1_529, 1_665, 1_953), atlases.map { it.elements.size }.toSet())
        assertEquals(5_985, atlases.flatMap { it.elements }.map { it.canonicalId }.toSet().size)
    }

    @Test
    fun `every element blocks unsupported compatibility and geometry claims`() {
        VehicleTechnicalAtlasDescriptors.all.map(::atlas).forEach { atlas ->
            assertFalse(atlas.geometryPolicy.oemClaim)
            assertFalse(atlas.geometryPolicy.vehicleSpecificClaim)
            atlas.elements.forEach { element ->
                assertFalse(element.visual.oemClaim)
                assertFalse(element.visual.dimensional)
                assertFalse(element.commerce.visualMatchIsExactCompatibility)
                assertEquals("REQUIRES_VERIFICATION", element.applicability.compatibilityCeiling)
                assertTrue("ORBIT_360" in element.visual.interactionModes)
            }
        }
    }

    @Test
    fun `semantic regions always resolve to a sellable parent`() {
        VehicleTechnicalAtlasDescriptors.all.map(::atlas).forEach { atlas ->
            val byId = atlas.elements.associateBy { it.canonicalId }
            atlas.elements.filter { it.visual.renderStrategy == "SEMANTIC_REGION" }.forEach { element ->
                assertFalse(element.commerce.directlySellable)
                assertTrue(element.commerce.redirectToParent)
                val parentId = element.parentCanonicalId
                assertNotNull(parentId)
                assertTrue(byId.getValue(parentId!!).commerce.directlySellable)
            }
        }
    }
}
