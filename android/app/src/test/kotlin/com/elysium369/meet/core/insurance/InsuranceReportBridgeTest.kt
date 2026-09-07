package com.elysium369.meet.core.insurance

import org.junit.Assert.*
import org.junit.Test

class InsuranceReportBridgeTest {
    @Test fun `create report with hash`() {
        val e = InsuranceReportBridge()
        val r = e.createReport("v1", "Corolla 2020", "o1", "Ana",
            InsuranceIncidentType.COLLISION, "Choque frontal", System.currentTimeMillis(),
            estimatedDamage = 500000, healthScoreBefore = 850, healthScoreAfter = 400)
        assertTrue(r.integrityHash.length == 64)
        assertTrue(r.isDraft)
        assertEquals(-450, r.healthScoreDelta)
    }
    @Test fun `attach photos and scans`() {
        val e = InsuranceReportBridge()
        val r = e.createReport("v1", "Car", "o1", "Ana", InsuranceIncidentType.COLLISION, "Test", System.currentTimeMillis())
        assertTrue(e.attachPhotos(r.reportId, listOf("photo1.jpg", "photo2.jpg")))
        assertTrue(e.attachScanReports(r.reportId, "pre-1", "post-1"))
        val updated = e.getReport(r.reportId)!!
        assertEquals(2, updated.photoUrls.size)
        assertEquals("pre-1", updated.preScanReportId)
    }
    @Test fun `submit to insurer`() {
        val e = InsuranceReportBridge()
        val r = e.createReport("v1", "Car", "o1", "Ana", InsuranceIncidentType.THEFT, "Robaron mi carro", System.currentTimeMillis())
        val submitted = e.submit(r.reportId)!!
        assertEquals(InsuranceClaimStatus.SUBMITTED, submitted.status)
        assertNotNull(submitted.submittedAtEpochMs)
    }
    @Test fun `update claim status to approved`() {
        val e = InsuranceReportBridge()
        val r = e.createReport("v1", "Car", "o1", "Ana", InsuranceIncidentType.FLOOD, "Inundación", System.currentTimeMillis(), estimatedDamage = 300000)
        e.submit(r.reportId)
        assertTrue(e.updateStatus(r.reportId, InsuranceClaimStatus.APPROVED, amount = 250000))
        val updated = e.getReport(r.reportId)!!
        assertEquals(250000L, updated.approvedAmount)
    }
    @Test fun `share text contains damage`() {
        val e = InsuranceReportBridge()
        val r = e.createReport("v1", "Toyota Corolla 2020", "o1", "Ana", InsuranceIncidentType.COLLISION, "Choque", System.currentTimeMillis(), estimatedDamage = 500000)
        val text = e.generateShareText(r.reportId)
        assertTrue(text.contains("ELYSIUM"))
        assertTrue(text.contains("Colisión"))
        assertTrue(text.contains("500"))
    }
    @Test fun `incident type labels in Spanish`() {
        assertEquals("Colisión", InsuranceIncidentType.COLLISION.displayLabel)
        assertEquals("Inundación", InsuranceIncidentType.FLOOD.displayLabel)
        assertEquals("Robo", InsuranceIncidentType.THEFT.displayLabel)
    }
    @Test fun `pending claims query`() {
        val e = InsuranceReportBridge()
        val r = e.createReport("v1", "Car", "o1", "Ana", InsuranceIncidentType.VANDALISM, "Test", System.currentTimeMillis())
        e.submit(r.reportId)
        assertEquals(1, e.getPendingClaims("o1").size)
    }
}
