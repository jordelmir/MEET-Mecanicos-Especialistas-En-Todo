package com.elysium369.meet.core.reports

import org.junit.Assert.*
import org.junit.Test

class CertifiedReportEngineTest {

    private fun engineWithDraft(): Pair<CertifiedReportEngine, CertifiedReport> {
        val e = CertifiedReportEngine()
        val draft = e.createDraft(
            ReportType.PRE_SCAN_REPORT, "veh-123", "Toyota Corolla 2015",
            "mech-456", "José García",
        )
        e.addSection(draft.reportId, ReportSection(
            "s1", "Motor", "Sin códigos activos", SectionSeverity.GOOD,
        ))
        e.addSection(draft.reportId, ReportSection(
            "s2", "Frenos", "Pastillas al 30%", SectionSeverity.WARNING,
        ))
        return e to draft
    }

    @Test
    fun `create draft report`() {
        val (e, draft) = engineWithDraft()
        assertTrue(draft.isDraft)
        assertEquals(ReportType.PRE_SCAN_REPORT, draft.type)
    }

    @Test
    fun `add sections to draft`() {
        val (e, draft) = engineWithDraft()
        assertEquals(2, e.getReport(draft.reportId)!!.sectionCount)
    }

    @Test
    fun `cannot add section to sealed report`() {
        val (e, draft) = engineWithDraft()
        e.seal(draft.reportId)
        val result = e.addSection(draft.reportId, ReportSection("s3", "X", "Y"))
        assertFalse(result)
    }

    @Test
    fun `seal computes SHA-256 hash`() {
        val (e, draft) = engineWithDraft()
        val sealed = e.seal(draft.reportId)!!
        assertEquals(ReportStatus.READY, sealed.status)
        assertTrue(sealed.integrityHash.length == 64) // SHA-256 = 64 hex chars
    }

    @Test
    fun `certify signs the report`() {
        val (e, draft) = engineWithDraft()
        e.seal(draft.reportId)
        val certified = e.certify(draft.reportId, "certifier-789")!!
        assertTrue(certified.isSigned)
        assertTrue(certified.certifierSignature.startsWith("sig-"))
    }

    @Test
    fun `QR contains exactly 6 fields — no PII`() {
        val (e, draft) = engineWithDraft()
        e.seal(draft.reportId)
        e.certify(draft.reportId, "certifier-789")
        val qr = e.generateQr(draft.reportId)!!
        val encoded = qr.encode()
        assertEquals(7, encoded.split("|").size) // v1| prefix + 6 fields
        assertFalse(encoded.contains("José"))
        assertFalse(encoded.contains("García"))
    }

    @Test
    fun `QR roundtrip encode and decode`() {
        val (e, draft) = engineWithDraft()
        e.seal(draft.reportId)
        e.certify(draft.reportId, "certifier-789")
        val qr = e.generateQr(draft.reportId)!!
        val decoded = QrPayload.decode(qr.encode())!!
        assertEquals(qr.reportId, decoded.reportId)
        assertEquals(qr.integrityHash, decoded.integrityHash)
    }

    @Test
    fun `verify certified report passes`() {
        val (e, draft) = engineWithDraft()
        e.seal(draft.reportId)
        e.certify(draft.reportId, "certifier-789")
        val qr = e.generateQr(draft.reportId)!!
        val result = e.verify(qr)
        assertTrue(result.isValid)
        assertTrue(result.hashMatches)
        assertTrue(result.message.contains("VERIFICADO"))
    }

    @Test
    fun `void creates new version with chained hash — never silent edit`() {
        val (e, draft) = engineWithDraft()
        e.seal(draft.reportId)
        e.certify(draft.reportId, "certifier-789")
        val originalHash = e.getReport(draft.reportId)!!.integrityHash
        val newVersion = e.voidAndCreateNewVersion(draft.reportId, "Corrección de odómetro")!!
        assertTrue(e.getReport(draft.reportId)!!.isVoided)
        assertEquals(originalHash, newVersion.previousVersionHash)
        assertEquals(2, newVersion.version)
    }

    @Test
    fun `voided report fails verification`() {
        val (e, draft) = engineWithDraft()
        e.seal(draft.reportId)
        e.certify(draft.reportId, "certifier-789")
        val qr = e.generateQr(draft.reportId)!!
        e.voidAndCreateNewVersion(draft.reportId, "Error")
        val result = e.verify(qr)
        assertFalse(result.isValid)
        assertTrue(result.message.contains("ANULADO"))
    }

    @Test
    fun `all 5 report types exist`() {
        assertEquals(5, ReportType.entries.size)
    }

    @Test
    fun `all 5 section severities exist`() {
        assertEquals(5, SectionSeverity.entries.size)
    }
}
