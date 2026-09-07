package com.elysium369.meet.core.reports

import com.elysium369.meet.diagnostic.DiagnosticProvenance
import com.elysium369.meet.diagnostic.DiagnosticSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForensicAffidavitGeneratorTest {

    private val baseVehicleId = "veh-cr-449102"
    private val preScan = DiagnosticSnapshot(
        id = "snap-pre-affidavit",
        vehicleId = baseVehicleId,
        createdAtMs = 1700000000000L,
        dtcsActive = listOf("P0171", "P0300"),
        freezeFramePidValues = mapOf("STFT" to 22.5, "LTFT" to 19.8),
        provenance = DiagnosticProvenance.Real
    )
    private val postScan = DiagnosticSnapshot(
        id = "snap-post-affidavit",
        vehicleId = baseVehicleId,
        createdAtMs = 1700005000000L,
        dtcsActive = emptyList(),
        provenance = DiagnosticProvenance.Real
    )

    private val sampleInput = ForensicAffidavitGenerator.AffidavitInput(
        affidavitNumber = "AFF-2026-CR-0091",
        inspectorName = "Ing. Manuel Brenes Chaves",
        inspectorCredentialNumber = "MEP-INA-AUT-8891",
        vehicleId = baseVehicleId,
        redactedVin = "3N1AB7AP8HY******",
        redactedPlate = "***-789",
        odometerKm = 142350L,
        preScanSnapshot = preScan,
        postScanSnapshot = postScan,
        installedParts = listOf(
            ForensicAffidavitGenerator.InstalledPartEvidence(
                partName = "Sensor MAF Hitachi OEM",
                oemPartNumber = "22680-7S000",
                invoiceOrBatchNumber = "FAC-2026-08122",
                verifiedByMultimeterOrPressure = true
            )
        ),
        physicalMeasurements = mapOf(
            "Presión de riel (psi)" to "43.5 psi",
            "Resistencia del inyector 1 (ohms)" to "12.2 Ω"
        ),
        evidenceHashes = listOf(
            "b1a9f5d37612f1234567890abcdef1234567890abcdef1234567890abcdef12",
            "c2b8e4f58723a9876543210fedcba9876543210fedcba9876543210fedcba98"
        ),
        timestampMs = 1700006000000L
    )

    @Test
    fun `generate spanish formal affidavit produces correct structure and hash`() {
        val doc = ForensicAffidavitGenerator.generate(
            sampleInput,
            ForensicAffidavitGenerator.AffidavitLanguage.SPANISH_FORMAL
        )

        assertEquals("AFF-2026-CR-0091", doc.affidavitNumber)
        assertTrue(doc.title.contains("DICTAMEN PERICIAL"))
        assertTrue(doc.bodyMarkdown.contains("P0171"))
        assertTrue(doc.bodyMarkdown.contains("22680-7S000"))
        assertTrue(doc.bodyMarkdown.contains("142350 km"))
        assertEquals(64, doc.forensicMerkleRoot.length)
        assertEquals(64, doc.overallAffidavitHash.length)

        // Verify QR compliance (Zero full VIN, 6 fields)
        val qr = doc.qrPayload
        assertEquals(doc.affidavitNumber, qr.reportId)
        assertEquals(doc.overallAffidavitHash, qr.integrityHash)
        assertEquals(baseVehicleId, qr.vehicleId)
        assertEquals(ReportType.POST_SCAN_REPORT, qr.reportType)
    }

    @Test
    fun `generate english C1 forensic affidavit contains modal hedging`() {
        val doc = ForensicAffidavitGenerator.generate(
            sampleInput,
            ForensicAffidavitGenerator.AffidavitLanguage.ENGLISH_C1_FORENSIC
        )

        assertTrue(doc.title.contains("FORENSIC ENGINEERING AFFIDAVIT"))
        assertTrue(doc.bodyMarkdown.contains("The empirical sensor telemetry strongly suggests"))
        assertTrue(doc.bodyMarkdown.contains("Under no circumstances should secondary component degradation be ruled out"))
        assertTrue(doc.bodyMarkdown.contains("Empirically Resolved Codes**: P0171, P0300"))
    }

    @Test
    fun `generate bilingual dual column affidavit includes both languages`() {
        val doc = ForensicAffidavitGenerator.generate(
            sampleInput,
            ForensicAffidavitGenerator.AffidavitLanguage.BILINGUAL_DUAL_COLUMN
        )

        assertTrue(doc.bodyMarkdown.contains("DICTAMEN PERICIAL FORENSE"))
        assertTrue(doc.bodyMarkdown.contains("AUTOMOTIVE FORENSIC ENGINEERING AFFIDAVIT"))
    }
}
