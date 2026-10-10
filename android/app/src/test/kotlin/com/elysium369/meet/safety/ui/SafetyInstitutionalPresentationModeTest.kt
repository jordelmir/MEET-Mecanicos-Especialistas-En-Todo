package com.elysium369.meet.safety.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyInstitutionalPresentationModeTest {

    @Test
    fun institutionalModeExposesOnlySafetyCore() {
        val allowedModules = SafetyInstitutionalPresentationMode.resolveAllowedModules(
            PresentationMode.INSTITUTIONAL_DEPUTIES_SAFETY,
        )

        assertEquals(1, allowedModules.size)
        assertTrue(allowedModules.contains(PlatformModuleDestination.SAFETY_CITIZEN_CORE))

        assertFalse(
            SafetyInstitutionalPresentationMode.isModuleVisible(
                PlatformModuleDestination.AUTOMOTIVE_OBD_DIAGNOSTICS,
                PresentationMode.INSTITUTIONAL_DEPUTIES_SAFETY,
            ),
        )
        assertFalse(
            SafetyInstitutionalPresentationMode.isModuleVisible(
                PlatformModuleDestination.MOBILITY_DISPATCH,
                PresentationMode.INSTITUTIONAL_DEPUTIES_SAFETY,
            ),
        )
        assertFalse(
            SafetyInstitutionalPresentationMode.isModuleVisible(
                PlatformModuleDestination.PARTS_MARKETPLACE,
                PresentationMode.INSTITUTIONAL_DEPUTIES_SAFETY,
            ),
        )
    }

    @Test
    fun fullPlatformModePreservesAllModulesIntact() {
        val allowedModules = SafetyInstitutionalPresentationMode.resolveAllowedModules(
            PresentationMode.FULL_PLATFORM_OPERATING_SYSTEM,
        )

        assertEquals(5, allowedModules.size)
        assertTrue(allowedModules.contains(PlatformModuleDestination.SAFETY_CITIZEN_CORE))
        assertTrue(allowedModules.contains(PlatformModuleDestination.AUTOMOTIVE_OBD_DIAGNOSTICS))
        assertTrue(allowedModules.contains(PlatformModuleDestination.MOBILITY_DISPATCH))
        assertTrue(allowedModules.contains(PlatformModuleDestination.PARTS_MARKETPLACE))
        assertTrue(allowedModules.contains(PlatformModuleDestination.VEHICLE_HISTORY))
    }

    @Test
    fun parliamentaryOpeningStatementIsApprovedText() {
        val statement = SafetyInstitutionalPresentationMode.PARLIAMENTARY_OPENING_STATEMENT
        assertTrue(statement.isNotBlank())
        assertTrue(statement.contains("Elysium Safety no pretende reemplazar a las autoridades"))
        assertTrue(statement.contains("trazabilidad"))
    }

    @Test
    fun institutionalSectionsContainAllSevenCoreCapabilities() {
        val sections = SafetyInstitutionalPresentationMode.institutionalSections
        assertEquals(7, sections.size)

        assertEquals("Reportar incidentes de seguridad", sections[0].title)
        assertEquals("Adjuntar y organizar evidencia", sections[1].title)
        assertEquals("Georreferenciar los acontecimientos", sections[2].title)
        assertEquals("Mantener trazabilidad de la información", sections[3].title)
        assertEquals("Construir una cadena de evidencia", sections[4].title)
        assertEquals("Crear inteligencia territorial", sections[5].title)
        assertEquals("Facilitar colaboración ciudadano-institución", sections[6].title)

        sections.forEach { section ->
            assertTrue(section.description.isNotBlank())
            assertTrue(section.demonstratedStatus in listOf("DEMOSTRADA", "PENDIENTE_DE_PILOTO", "PROPUESTA_FUTURA"))
        }
    }
}
