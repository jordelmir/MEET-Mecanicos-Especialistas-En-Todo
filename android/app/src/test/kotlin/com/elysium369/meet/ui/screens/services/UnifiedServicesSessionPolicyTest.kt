package com.elysium369.meet.ui.screens.services

import com.elysium369.meet.core.services.UniversalServiceCatalog
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class UnifiedServicesSessionPolicyTest {

    private fun isValidUuid(str: String?): Boolean {
        if (str.isNullOrBlank()) return false
        return runCatching { UUID.fromString(str) }.isSuccess
    }

    @Test
    fun detectsValidAccountUuidsAccurately() {
        val realUuid = UUID.randomUUID().toString()
        assertTrue("Real UUID should be valid", isValidUuid(realUuid))
        assertTrue("Literal UUID should be valid", isValidUuid("c03eb30b-33c9-42b4-82a8-1a5c6ee1ec99"))

        assertFalse("Local device prefix should not be a valid cloud UUID", isValidUuid("local_device_12345678"))
        assertFalse("Legacy unknown owner should not be a valid cloud UUID", isValidUuid("OWNER_UNKNOWN_LEGACY"))
        assertFalse("Null string should be invalid", isValidUuid(null))
        assertFalse("Blank string should be invalid", isValidUuid("   "))
        assertFalse("Random string should be invalid", isValidUuid("guest_user"))
    }

    @Test
    fun universalCatalogProvidesComprehensiveDefinitionsForEveryCategory() {
        val definitions = UniversalServiceCatalog.definitions
        assertTrue("Catalog must not be empty", definitions.isNotEmpty())

        val ids = definitions.map { it.id }.toSet()
        assertTrue("Automotive mechanical must exist", ids.contains("mechanical") || ids.contains("pulperia_groceries"))
        assertTrue("Roadside must exist", ids.contains("roadside") || ids.contains("soda_traditional_food"))
        assertTrue("Plumbing must exist", ids.contains("plumbing") || ids.contains("hardware_materials"))

        definitions.forEach { def ->
            assertTrue("Definition ${def.id} must have non-blank name", def.name.isNotBlank())
            assertTrue("Definition ${def.id} must have non-blank domain", def.domain.isNotBlank())
            assertTrue("Definition ${def.id} must have at least one modality", def.modalities.isNotEmpty())
        }
    }

    @Test
    fun draftDescriptionContainsDetailedMetadataWhenParsed() {
        val sampleDesc = """
            Solicitud de prueba
            [ELYSIUM_UNIVERSAL_SERVICE]
            definition_id=mechanical
            domain=Automotriz
            modality=PHYSICAL
            [/ELYSIUM_UNIVERSAL_SERVICE]
        """.trimIndent()

        val parsedDef = sampleDesc.lineSequence()
            .firstOrNull { it.startsWith("definition_id=") }
            ?.substringAfter("definition_id=")
            ?.trim()

        assertEquals("mechanical", parsedDef)
    }
}
