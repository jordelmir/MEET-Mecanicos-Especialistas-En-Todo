package com.elysium369.meet.core.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalServiceCatalogTest {
    @Test
    fun `catalog spans physical digital hybrid and custom needs`() {
        val modalities = UniversalServiceCatalog.definitions.flatMap { it.modalities }.toSet()

        assertEquals(UniversalServiceModality.entries.toSet(), modalities)
        assertTrue(UniversalServiceCatalog.domains().size >= 10)
        assertTrue(UniversalServiceCatalog.definitions.any { it.id == "custom" })
    }

    @Test
    fun `search matches both service and domain without fake fallbacks`() {
        assertTrue(UniversalServiceCatalog.search("plomería").any { it.id == "plumbing" })
        assertTrue(UniversalServiceCatalog.search("digital").any { it.domain == "Digital" })
        assertTrue(UniversalServiceCatalog.search("servicio inexistente zzz").isEmpty())
    }

    @Test
    fun `restricted work remains explicitly risk gated`() {
        assertTrue(
            UniversalServiceCatalog.definitions
                .filter { it.id in setOf("childcare", "legal", "security") }
                .all { it.riskTier == "RESTRICTED" },
        )
    }
}
