package com.elysium369.meet.core.reports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HashEngineTest {

    private val baseDraft = DraftReport(
        vehicleId = "v1",
        userId = "u1",
        reportType = "PRE_SCAN_REPORT",
        title = "Pre-Scan",
        odometerKm = 100_000L,
        vin = "KMHCN46C18U123456",
        plate = "ABC-123",
        privacyRedactVin = false,
        privacyRedactPlate = false,
        privacyRedactLocation = false,
        privacyPublicShare = false,
        snapshotHash = null,
        evidenceHashes = emptyList(),
        repairActionHashes = emptyList(),
        peritajeHash = null,
        previousHash = null,
        notes = "Inspección pre-compra",
    )

    @Test
    fun `canonicalReportString is stable for the same input`() {
        assertEquals(
            HashEngine.canonicalReportString(baseDraft),
            HashEngine.canonicalReportString(baseDraft.copy()),
        )
    }

    @Test
    fun `hash changes when previousHash changes (chain)`() {
        val a = HashEngine.hashReport(baseDraft)
        val b = HashEngine.hashReport(baseDraft.copy(previousHash = "PREV-A"))
        assertFalse(a == b)
    }

    @Test
    fun `hash changes when vin changes`() {
        val a = HashEngine.hashReport(baseDraft)
        val b = HashEngine.hashReport(baseDraft.copy(vin = "KMHCN46C18U999999"))
        assertFalse(a == b)
    }

    @Test
    fun `hash changes when snapshot hash changes`() {
        val a = HashEngine.hashReport(baseDraft)
        val b = HashEngine.hashReport(baseDraft.copy(snapshotHash = "snap-h2"))
        assertFalse(a == b)
    }

    @Test
    fun `verifyChain accepts empty list`() {
        val r = HashEngine.verifyChain(emptyList())
        assertTrue(r.ok)
        assertEquals(null, r.brokenAt)
    }

    @Test
    fun `verifyChain accepts a 2-link chain`() {
        val r = HashEngine.verifyChain(
            listOf(
                HashEngine.ChainReport(id = "r1", generatedAt = 1, integrityHash = "A", previousHash = null),
                HashEngine.ChainReport(id = "r2", generatedAt = 2, integrityHash = "B", previousHash = "A"),
            ),
        )
        assertTrue(r.ok)
    }

    @Test
    fun `verifyChain detects a broken link`() {
        val r = HashEngine.verifyChain(
            listOf(
                HashEngine.ChainReport(id = "r1", generatedAt = 1, integrityHash = "A", previousHash = null),
                HashEngine.ChainReport(id = "r2", generatedAt = 2, integrityHash = "B", previousHash = "WRONG"),
            ),
        )
        assertFalse(r.ok)
        assertEquals("r2", r.brokenAt)
    }

    @Test
    fun `verifyChain sorts by generatedAt regardless of input order`() {
        val r = HashEngine.verifyChain(
            listOf(
                HashEngine.ChainReport(id = "r2", generatedAt = 2, integrityHash = "B", previousHash = "A"),
                HashEngine.ChainReport(id = "r1", generatedAt = 1, integrityHash = "A", previousHash = null),
            ),
        )
        assertTrue(r.ok)
    }
}
