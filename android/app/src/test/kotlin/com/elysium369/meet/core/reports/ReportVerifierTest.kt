package com.elysium369.meet.core.reports

import com.elysium369.meet.data.local.dao.CertifiedReportDao
import com.elysium369.meet.data.local.dao.DiagnosticSnapshotDao
import com.elysium369.meet.data.local.dao.RepairActionDao
import com.elysium369.meet.data.local.dao.ReportEvidenceDao
import com.elysium369.meet.data.local.dao.ReportSignatureDao
import com.elysium369.meet.data.local.entities.CertifiedReportEntity
import com.elysium369.meet.data.local.entities.DiagnosticSnapshotEntity
import com.elysium369.meet.data.local.entities.RepairActionEntity
import com.elysium369.meet.data.local.entities.ReportEvidenceEntity
import com.elysium369.meet.data.local.entities.ReportSignatureEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QR + Verifier tests.
 *
 * The verifier is the last line of defense against tampered reports:
 * it must (a) parse the QR correctly, (b) match the local row when the
 * hash agrees, (c) flag a tampered row when the hash disagrees, and
 * (d) reject a structurally invalid QR without crashing.
 *
 * No remote probe is injected — by default [ReportVerifier.remoteProbe]
 * returns `null`, so the tests stay 100% local and JVM-only.
 */
class ReportVerifierTest {

    private fun fakeRepo(
        rows: MutableList<CertifiedReportEntity> = mutableListOf(),
    ): com.elysium369.meet.data.local.CertifiedReportRepository =
        com.elysium369.meet.data.local.CertifiedReportRepository(
            reportDao = object : CertifiedReportDao {
                override suspend fun getById(reportId: String) = rows.firstOrNull { it.reportId == reportId }
                override fun observeForVehicle(vehicleId: String) = kotlinx.coroutines.flow.flowOf(emptyList<CertifiedReportEntity>())
                override suspend fun listForVehicleAsc(vehicleId: String) = rows.filter { it.vehicleId == vehicleId }
                override suspend fun listByStatus(status: ReportStatus, limit: Int) = rows.filter { it.status == status }
                override suspend fun latestHashForVehicle(vehicleId: String, excludeReportId: String) =
                    rows.filter {
                        it.vehicleId == vehicleId &&
                            it.reportId != excludeReportId &&
                            it.integrityHash != "UNSIGNED"
                    }.maxWithOrNull(compareBy<CertifiedReportEntity> { it.signedAt }.thenBy { it.reportId })
                        ?.integrityHash
                override suspend fun insert(report: CertifiedReportEntity) {
                    require(rows.none { it.reportId == report.reportId })
                    rows.add(report)
                }
                override suspend fun update(report: CertifiedReportEntity) {
                    rows.removeAll { it.reportId == report.reportId }
                    rows.add(report)
                }
            },
            evidenceDao = object : ReportEvidenceDao {
                override suspend fun listForReport(reportId: String) = emptyList<ReportEvidenceEntity>()
                override suspend fun hashesForReport(reportId: String) = emptyList<String>()
                override suspend fun upsert(evidence: ReportEvidenceEntity) {}
                override suspend fun upsertAll(evidence: List<ReportEvidenceEntity>) {}
            },
            repairDao = object : RepairActionDao {
                override suspend fun listForReport(reportId: String) = emptyList<RepairActionEntity>()
                override suspend fun listByDtc(dtc: String) = emptyList<RepairActionEntity>()
                override suspend fun upsert(action: RepairActionEntity) {}
                override suspend fun upsertAll(actions: List<RepairActionEntity>) {}
            },
            signatureDao = object : ReportSignatureDao {
                override suspend fun getForReport(reportId: String) = null
                override suspend fun insert(signature: ReportSignatureEntity) {}
                override suspend fun countForReport(reportId: String) = 0
            },
            snapshotDao = object : DiagnosticSnapshotDao {
                override suspend fun getById(id: String) = null
                override suspend fun listForReport(reportId: String) = emptyList<DiagnosticSnapshotEntity>()
                override suspend fun latestForVehicle(vehicleId: String) = null
                override suspend fun upsert(snapshot: DiagnosticSnapshotEntity) {}
                override suspend fun attachToReport(snapshotId: String, reportId: String) {}
            },
            hashing = ReportHashingService(),
        )

    @Test
    fun `QrPayload round-trips through encode and decode`(): Unit = runBlocking {
        val original = QrPayload(
            reportId = "r-2026-07-04-001",
            integrityHash = "71b393aeb4ddbb23dc4fdeb3720450a91734ebf567a0698620b273f4b545072e",
            vehicleId = "v-accent-verna-2005",
            generatedAt = 1700000000L,
            reportType = ReportType.PRE_SCAN_REPORT,
            verifierUrl = "https://kluumjhzncitjayvvwtj.supabase.co/functions/v1/verify/r-2026-07-04-001",
        )
        val encoded = original.encode()
        assertTrue("payload must start with v1|", encoded.startsWith("v1|"))
        val decoded = QrPayload.decode(encoded)
        assertNotNull(decoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `QrPayload decode returns null on missing v1 prefix`() {
        val bad = "v2|abc|def|ghi|1700000000|PRE_SCAN_REPORT|"
        assertNull(QrPayload.decode(bad))
    }

    @Test
    fun `QrPayload decode returns null on wrong field count`() {
        val bad = "v1|abc|def" // only 3 fields
        assertNull(QrPayload.decode(bad))
    }

    @Test
    fun `QrPayload decode returns null on malformed generatedAt`() {
        val bad = "v1|abc|def|ghi|notanumber|PRE_SCAN_REPORT|"
        assertNull(QrPayload.decode(bad))
    }

    @Test
    fun `QrPayload decode returns null on unknown reportType`() {
        val bad = "v1|abc|def|ghi|1700000000|NOT_A_REAL_TYPE|"
        assertNull(QrPayload.decode(bad))
    }

    @Test
    fun `verify matches local row when hash agrees`(): Unit = runBlocking {
        val rows = mutableListOf(
            CertifiedReportEntity(
                reportId = "r1", vehicleId = "v1", userId = "u1",
                reportType = ReportType.PRE_SCAN_REPORT, title = "Pre-Scan",
                status = ReportStatus.SIGNED,
                odometerKm = 100_000, vin = null, plate = null,
                generatedAt = 1_700_000_000_000L, signedAt = 1_700_000_000_000L,
                pdfUri = null, qrVerificationUrl = null,
                integrityHash = "abc123",
                previousHash = null,
                createdAt = 1_700_000_000_000L, updatedAt = 1_700_000_000_000L,
            )
        )
        val verifier = ReportVerifier(fakeRepo(rows))
        val payload = QrPayload(
            reportId = "r1", integrityHash = "abc123",
            vehicleId = "v1", generatedAt = 1_700_000_000_000L,
            reportType = ReportType.PRE_SCAN_REPORT, verifierUrl = null,
        )
        val result = verifier.verify(payload)
        assertTrue(result is ReportVerifier.VerifyResult.ValidLocal)
    }

    @Test
    fun `verify flags tampering when local hash differs from QR`(): Unit = runBlocking {
        val rows = mutableListOf(
            CertifiedReportEntity(
                reportId = "r1", vehicleId = "v1", userId = "u1",
                reportType = ReportType.PRE_SCAN_REPORT, title = "Pre-Scan",
                status = ReportStatus.SIGNED,
                odometerKm = 100_000, vin = null, plate = null,
                generatedAt = 1_700_000_000_000L, signedAt = 1_700_000_000_000L,
                pdfUri = null, qrVerificationUrl = null,
                integrityHash = "real-hash",
                previousHash = null,
                createdAt = 1_700_000_000_000L, updatedAt = 1_700_000_000_000L,
            )
        )
        val verifier = ReportVerifier(fakeRepo(rows))
        val payload = QrPayload(
            reportId = "r1", integrityHash = "QR-HASH-FROM-ATTACKER",
            vehicleId = "v1", generatedAt = 1_700_000_000_000L,
            reportType = ReportType.PRE_SCAN_REPORT, verifierUrl = null,
        )
        val result = verifier.verify(payload)
        assertTrue("expected Tampered, got $result", result is ReportVerifier.VerifyResult.Tampered)
        result as ReportVerifier.VerifyResult.Tampered
        assertEquals("real-hash", result.expectedHash)
        assertEquals("QR-HASH-FROM-ATTACKER", result.qrHash)
    }

    @Test
    fun `verify returns Invalid when no local row and no remote probe`(): Unit = runBlocking {
        val verifier = ReportVerifier(fakeRepo(rows = mutableListOf()))
        val payload = QrPayload(
            reportId = "missing", integrityHash = "anything",
            vehicleId = "v1", generatedAt = 1_700_000_000_000L,
            reportType = ReportType.PRE_SCAN_REPORT, verifierUrl = null,
        )
        val result = verifier.verify(payload)
        assertTrue(result is ReportVerifier.VerifyResult.Invalid)
    }

    @Test
    fun `verifyRaw rejects structurally invalid QR`(): Unit = runBlocking {
        val verifier = ReportVerifier(fakeRepo())
        val result = verifier.verifyRaw("not a valid qr string")
        assertTrue(result is ReportVerifier.VerifyResult.Invalid)
        // The reason must explicitly call out the malformed format.
        result as ReportVerifier.VerifyResult.Invalid
        assertTrue("reason must mention format expectation: ${result.reason}",
            result.reason.contains("v1|") || result.reason.contains("esquema"))
    }

    @Test
    fun `verify hash check is case-insensitive hex`(): Unit = runBlocking {
        val rows = mutableListOf(
            CertifiedReportEntity(
                reportId = "r1", vehicleId = "v1", userId = "u1",
                reportType = ReportType.PRE_SCAN_REPORT, title = "Pre-Scan",
                status = ReportStatus.SIGNED,
                odometerKm = null, vin = null, plate = null,
                generatedAt = 1_700_000_000_000L, signedAt = 1_700_000_000_000L,
                pdfUri = null, qrVerificationUrl = null,
                integrityHash = "abcdef0123456789",
                previousHash = null,
                createdAt = 1_700_000_000_000L, updatedAt = 1_700_000_000_000L,
            )
        )
        val verifier = ReportVerifier(fakeRepo(rows))
        val payload = QrPayload(
            reportId = "r1",
            integrityHash = "ABCDEF0123456789", // uppercase
            vehicleId = "v1", generatedAt = 1_700_000_000_000L,
            reportType = ReportType.PRE_SCAN_REPORT, verifierUrl = null,
        )
        assertTrue(verifier.verify(payload) is ReportVerifier.VerifyResult.ValidLocal)
    }

    @Test
    fun `qr payload does not leak vin or plate`(): Unit = runBlocking {
        // The whole point of the 6-field minimal payload. This test
        // pins the contract: even if the source report has VIN/plate,
        // the QR must not embed them.
        val payload = QrPayload(
            reportId = "r1", integrityHash = "abc",
            vehicleId = "v1", generatedAt = 1L,
            reportType = ReportType.PRE_SCAN_REPORT, verifierUrl = null,
        )
        val encoded = payload.encode()
        assertFalse("VIN must never appear in QR: $encoded", encoded.contains("vin", ignoreCase = true))
        assertFalse("plate must never appear in QR: $encoded", encoded.contains("plate", ignoreCase = true))
        // 7 fields: v1 + reportId + hash + vehicleId + ts + type + url.
        assertEquals(7, encoded.split('|').size)
    }
}
