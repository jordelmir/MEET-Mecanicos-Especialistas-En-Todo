package com.elysium369.meet.visual3d

import com.elysium369.meet.domain.visualdiagnostics.ComponentCategory
import com.elysium369.meet.visual3d.domain.VehicleTwinRenderPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleTwinRenderPolicyTest {
    @Test
    fun `renders engine diagnostic geometry throughout x ray mode`() {
        assertTrue(
            VehicleTwinRenderPolicy.shouldRenderProgrammaticAssembly(
                category = ComponentCategory.COOLING,
                selectedSystemId = "engine",
                isSelected = false,
                hasDtc = false,
                xRayEnabled = true,
                hasCatalogSemanticGeometry = false
            )
        )
    }

    @Test
    fun `catalog geometry replaces duplicate diagnostic overview geometry`() {
        assertFalse(
            VehicleTwinRenderPolicy.shouldRenderProgrammaticAssembly(
                category = ComponentCategory.COOLING,
                selectedSystemId = "engine",
                isSelected = false,
                hasDtc = false,
                xRayEnabled = true,
                hasCatalogSemanticGeometry = true
            )
        )
    }

    @Test
    fun `selected and dtc parts always remain visible`() {
        assertTrue(
            VehicleTwinRenderPolicy.shouldRenderProgrammaticAssembly(
                category = ComponentCategory.SUSPENSION,
                selectedSystemId = "engine",
                isSelected = true,
                hasDtc = false,
                xRayEnabled = false,
                hasCatalogSemanticGeometry = true
            )
        )
    }

    @Test
    fun `detailed asset owns assembled inspection while fallback waits for service mode`() {
        assertFalse(
            VehicleTwinRenderPolicy.shouldShowCatalogFallback(
                sourceName = "Cojinetes principales",
                renderedByDetailedAsset = false,
                hasDetailedSystemAsset = true,
                xRayEnabled = true,
                explodedProgress = 0f,
                isSelected = false
            )
        )
        assertTrue(
            VehicleTwinRenderPolicy.shouldShowCatalogFallback(
                sourceName = "Cojinetes principales",
                renderedByDetailedAsset = false,
                hasDetailedSystemAsset = true,
                xRayEnabled = true,
                explodedProgress = 1f,
                isSelected = false
            )
        )
    }

    @Test
    fun `literal table rows remain data and never become physical geometry`() {
        assertFalse(VehicleTwinRenderPolicy.isPhysicalComponentName("Pistones\tNo\tSí\tSí\tCrítico"))
        assertFalse(
            VehicleTwinRenderPolicy.shouldShowCatalogFallback(
                sourceName = "Pistones\tNo\tSí\tSí\tCrítico",
                renderedByDetailedAsset = false,
                hasDetailedSystemAsset = true,
                xRayEnabled = true,
                explodedProgress = 1f,
                isSelected = true
            )
        )
    }
}
