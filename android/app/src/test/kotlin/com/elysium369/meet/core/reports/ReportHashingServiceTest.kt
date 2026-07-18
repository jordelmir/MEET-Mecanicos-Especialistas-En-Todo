package com.elysium369.meet.core.reports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ReportHashingService unit tests.
 *
 * The first test is the linchpin: it pins the Kotlin implementation to
 * the golden P0230 hash emitted by the TypeScript side. If this test
 * starts failing, the cross-runtime parity contract has drifted and the
 * CI parity verifier (`tests/parity/ci-verify.sh`) should also fail when
 * it runs against the same fixture.
 */
class ReportHashingServiceTest {

    private val service = ReportHashingService()

    private val tsExpectedP0230 =
        "71b393aeb4ddbb23dc4fdeb3720450a91734ebf567a0698620b273f4b545072e"

    @Test
    fun `p0230 parity demo reproduces TS golden hash byte-exact`() {
        val result = service.p0230ParityDemo()
        assertEquals(
            "Kotlin implementation drifted from TS — check DiagnosticSnapshot.computeHash",
            tsExpectedP0230,
            result.computedHash,
        )
        assertEquals(tsExpectedP0230, result.expectedHash)
        assertTrue("Cross-runtime parity broken", result.match)
        assertEquals("MATCH ✓ byte-exact with TS", result.summary)
    }

    @Test
    fun `p0230 reference snapshot has the expected DTCs and readiness`() {
        val snap = service.buildP0230ReferenceSnapshot()
        assertEquals("v-accent-verna-2005", snap.vehicleId)
        assertEquals(1700000000000L, snap.createdAtMs)
        assertEquals(listOf("P0230", "P1709"), snap.dtcsActive)
        assertTrue(snap.dtcsPending.isEmpty())
        assertTrue(snap.dtcsPermanent.isEmpty())
        assertEquals(mapOf("RPM" to 850.0, "ECT" to 88.0), snap.freezeFramePidValues)
        assertEquals(mapOf("Misfire" to true, "Fuel" to true), snap.readiness)
        assertEquals(14.1, snap.ecuVoltage!!, 0.0001)
        assertEquals(850.0, snap.rpm!!, 0.0001)
        assertEquals(88.0, snap.coolantTempC!!, 0.0001)
        assertEquals(0.0, snap.speedKph!!, 0.0001)
        assertNull(snap.engineLoadPct)
        assertEquals(0.5, snap.fuelTrimStft!!, 0.0001)
        assertEquals(-1.2, snap.fuelTrimLtft!!, 0.0001)
    }

    @Test
    fun `canonical snapshot preview is deterministic`() {
        val a = service.canonicalSnapshotPreview(service.buildP0230ReferenceSnapshot())
        val b = service.canonicalSnapshotPreview(service.buildP0230ReferenceSnapshot())
        assertEquals(a, b)
        // Starts with vehicleId|sessionId|createdAtMs to lock ordering.
        assertTrue(
            "Canonical should be pipe-delimited, got: $a",
            a.startsWith("v-accent-verna-2005|s-2026-07-04-001|1700000000000|"),
        )
    }

    @Test
    fun `signDraftReport is deterministic for identical inputs`() {
        val a = service.signDraftReport(
            vehicleId = "v-1",
            userId = "u-1",
            reportType = "scan",
            title = "T",
            odometerKm = 50000L,
            vin = "VIN1",
            plate = "ABC123",
            privacyRedactVin = true,
            privacyRedactPlate = false,
            privacyRedactLocation = true,
            privacyPublicShare = false,
            snapshotHash = "snap-hash",
            evidenceHashes = listOf("ev1", "ev2"),
            repairActionHashes = listOf("ra1"),
            peritajeHash = "per1",
            previousHash = "prev",
            notes = "n",
            expectedHash = null,
        )
        val b = service.signDraftReport(
            vehicleId = "v-1",
            userId = "u-1",
            reportType = "scan",
            title = "T",
            odometerKm = 50000L,
            vin = "VIN1",
            plate = "ABC123",
            privacyRedactVin = true,
            privacyRedactPlate = false,
            privacyRedactLocation = true,
            privacyPublicShare = false,
            snapshotHash = "snap-hash",
            evidenceHashes = listOf("ev1", "ev2"),
            repairActionHashes = listOf("ra1"),
            peritajeHash = "per1",
            previousHash = "prev",
            notes = "n",
            expectedHash = null,
        )
        assertEquals(a.hash, b.hash)
        assertEquals(a.canonical, b.canonical)
        assertNull(a.match)
    }

    @Test
    fun `signDraftReport matches expected hash when supplied`() {
        val first = service.signDraftReport(
            vehicleId = "v-1", userId = "u-1", reportType = "scan", title = "T",
            odometerKm = 1L, vin = "x", plate = "y",
            privacyRedactVin = false, privacyRedactPlate = false,
            privacyRedactLocation = false, privacyPublicShare = true,
            snapshotHash = null, evidenceHashes = emptyList(),
            repairActionHashes = emptyList(), peritajeHash = null,
            previousHash = null, notes = "",
        )
        val second = service.signDraftReport(
            vehicleId = "v-1", userId = "u-1", reportType = "scan", title = "T",
            odometerKm = 1L, vin = "x", plate = "y",
            privacyRedactVin = false, privacyRedactPlate = false,
            privacyRedactLocation = false, privacyPublicShare = true,
            snapshotHash = null, evidenceHashes = emptyList(),
            repairActionHashes = emptyList(), peritajeHash = null,
            previousHash = null, notes = "",
            expectedHash = first.hash,
        )
        assertEquals(first.hash, second.hash)
        assertNotNull(second.match)
        assertTrue(second.match!!)
    }

    @Test
    fun `demoReportChainOk returns ok`() {
        val res = service.demoReportChainOk()
        assertTrue(res.ok)
        assertNull(res.brokenAt)
    }

    @Test
    fun `demoReportChainBroken returns brokenAt`() {
        val res = service.demoReportChainBroken()
        assertFalse(res.ok)
        assertNotNull(res.brokenAt)
        assertEquals("bad-2", res.brokenAt)
    }
}
