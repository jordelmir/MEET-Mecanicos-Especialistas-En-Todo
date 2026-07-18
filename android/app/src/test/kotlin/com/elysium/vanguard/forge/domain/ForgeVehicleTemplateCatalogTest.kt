package com.elysium.vanguard.forge.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgeVehicleTemplateCatalogTest {
    @Test
    fun `publishes the five named conceptual vehicle foundations`() {
        assertEquals(
            listOf("AETHER X1", "IGNIS H1", "VORTEX R10", "TEMPEST R6", "OBSIDIAN GT-X"),
            ForgeVehicleTemplateCatalog.templates.map { it.name }
        )
        assertTrue(ForgeVehicleTemplateCatalog.templates.all { it.authority == ForgeTemplateAuthority.CONCEPTUAL })
    }

    @Test
    fun `instantiation never invents powertrain values or manufacturing readiness`() {
        val vehicle = ForgeVehicleTemplateCatalog.createConceptVehicle(
            templateId = "aether_x1",
            projectId = "project-aether",
            createdAt = 100L
        )

        assertNull(vehicle.powertrain)
        assertNull(vehicle.diagnosticProfile)
        assertTrue(vehicle.simulationScenarios.isEmpty())
        assertFalse(vehicle.systems.any { it.isComplete })
        assertEquals(
            SafetyClassification.REQUIRES_PROFESSIONAL_VALIDATION,
            vehicle.artifact.safetyClassification
        )
        assertTrue(vehicle.artifact.description.contains("Estado conceptual"))
    }
}
