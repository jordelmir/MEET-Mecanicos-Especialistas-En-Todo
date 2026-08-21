package com.elysium369.meet.core.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkshopServiceCatalogTest {

    @Test
    fun `catalog exposes all workshop service categories`() {
        assertEquals(
            ServiceCategory.values().toSet(),
            WorkshopServiceCatalog.services.map { it.category }.toSet(),
        )
        assertTrue(WorkshopServiceCatalog.services.size >= 80)
    }

    @Test
    fun `provider roles cover technical workshop marketplace requirements`() {
        val roleIds = WorkshopServiceCatalog.providerRoles.map { it.id }.toSet()

        assertTrue("EV specialist role is required", "HYBRID_EV_TECH" in roleIds)
        assertTrue("Tow truck role is required", "TOW_TRUCK" in roleIds)
        assertTrue("Parts store role is required", "PARTS_STORE" in roleIds)
        assertTrue("Prepurchase inspector role is required", "PREPURCHASE_INSPECTOR" in roleIds)
        assertEquals(22, WorkshopServiceCatalog.providerRoles.size)
    }

    @Test
    fun `P0230 recommendations require testing instead of direct part replacement`() {
        val suggestions = WorkshopServiceCatalog.servicesForDtc("P0230")
        val names = suggestions.map { it.name.lowercase() }

        assertTrue(names.any { it.contains("circuito primario bomba combustible") })
        assertTrue(names.any { it.contains("rele") || it.contains("fusible") })
        assertTrue(names.any { it.contains("voltaje") })
        assertTrue(names.any { it.contains("presion combustible") })
        assertTrue(names.any { it.contains("arnes") || it.contains("masa") })
        assertFalse(names.any { it.contains("cambio bomba") || it.contains("reemplazo bomba") })
    }

    @Test
    fun `P0230 services carry tools evidence and risk metadata`() {
        val suggestions = WorkshopServiceCatalog.servicesForDtc("P0230")

        assertTrue(suggestions.isNotEmpty())
        suggestions.forEach { service ->
            assertTrue(service.requiredTools.isNotEmpty())
            assertTrue(service.requiredEvidence.isNotEmpty())
            assertTrue(service.relatedDtcs.contains("P0230"))
            assertTrue(service.riskLevel == RiskLevel.HIGH || service.riskLevel == RiskLevel.CRITICAL)
        }
    }

    @Test
    fun `request summary embeds service catalog metadata for legacy service requests`() {
        val service = WorkshopServiceCatalog.servicesForDtc("P0230").first()
        val summary = WorkshopServiceCatalog.requestSummary(service, listOf("P0230"))

        assertTrue(summary.contains("service_id=${service.id}"))
        assertTrue(summary.contains("service_category=${service.category.name}"))
        assertTrue(summary.contains("dtc_codes=P0230"))
        assertTrue(summary.contains("no recomendar cambio directo"))
    }

    @Test
    fun `service packages include diagnostic pre purchase emergency and maintenance flows`() {
        val packageNames = WorkshopServiceCatalog.servicePackages.map { it.name }

        assertTrue(packageNames.contains("Diagnostico Express"))
        assertTrue(packageNames.contains("Diagnostico Pro"))
        assertTrue(packageNames.contains("Precompra Elite"))
        assertTrue(packageNames.contains("Emergencia No Arranca"))
        assertTrue(packageNames.contains("Mantenimiento 100K"))
    }
}
