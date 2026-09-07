package com.elysium369.meet.core.reports

import com.elysium369.meet.diagnostic.DiagnosticProvenance
import com.elysium369.meet.diagnostic.DiagnosticSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForensicRepairWorkflowCoordinatorTest {

    private val vehicleId = "veh-cr-492011"
    private val serviceId = "srv-taller-sanjose-001"
    private val mechanicId = "mech-eladio-mep-certified"

    private fun buildSnapshot(
        id: String,
        dtcs: List<String>,
        timestampMs: Long,
        vId: String = vehicleId
    ): DiagnosticSnapshot = DiagnosticSnapshot(
        id = id,
        vehicleId = vId,
        sessionId = "session-$serviceId",
        createdAtMs = timestampMs,
        dtcsActive = dtcs,
        ecuVoltage = 14.1,
        rpm = 820.0,
        coolantTempC = 89.0,
        provenance = DiagnosticProvenance.Real
    )

    @Test
    fun `full closed-loop forensic workflow from prescan to certified report`() {
        val preScan = buildSnapshot("pre-001", listOf("P0230", "P1709"), 1700000000000L)

        // 1. Start Session
        var session = ForensicRepairWorkflowCoordinator.startSession(
            sessionId = "sess-001",
            serviceId = serviceId,
            vehicleId = vehicleId,
            mechanicId = mechanicId,
            preScanSnapshot = preScan,
            timestampMs = 1700000001000L
        )
        assertEquals(ForensicRepairWorkflowCoordinator.WorkflowState.PRE_SCAN_CAPTURED, session.state)

        // 2. Propose Parts based on DTCs
        session = ForensicRepairWorkflowCoordinator.proposeParts(session)
        assertEquals(ForensicRepairWorkflowCoordinator.WorkflowState.COMPATIBLE_PARTS_PROPOSED, session.state)
        assertTrue(session.proposedParts.isNotEmpty())

        // 3. Accept Quote and Start Work
        session = ForensicRepairWorkflowCoordinator.acceptQuoteAndStartWork(
            session = session,
            quoteId = "quote-approved-99",
            installedParts = listOf("fuel_pump_relay", "fuel_pump_fuse"),
            timestampMs = 1700000005000L
        )
        assertEquals(ForensicRepairWorkflowCoordinator.WorkflowState.WORK_IN_PROGRESS, session.state)

        // 4. Attach Evidence
        val evidencePhoto = ForensicRepairWorkflowCoordinator.ForensicEvidenceItem(
            id = "ev-photo-1",
            evidenceType = EvidenceType.AFTER_PHOTO,
            uri = "content://media/photos/relay_installed.jpg",
            sha256Hash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            recordedAtMs = 1700000010000L,
            caption = "Relé OEM sustituido y verificado con multímetro"
        )
        session = ForensicRepairWorkflowCoordinator.attachEvidence(session, evidencePhoto)
        assertEquals(1, session.evidenceList.size)

        // 5. Complete Physical Work -> Post-Scan Prompt triggered
        session = ForensicRepairWorkflowCoordinator.completeRepairWork(session, 1700000015000L)
        assertEquals(ForensicRepairWorkflowCoordinator.WorkflowState.POST_SCAN_REQUIRED, session.state)
        assertTrue(PostScanPrompt.pendingCount() > 0)

        // 6. Capture Post-Scan (P0230 resolved, P1709 resolved)
        val postScan = buildSnapshot("post-002", emptyList(), 1700000020000L)

        // 7. Certify Repair Session
        val closedSession = ForensicRepairWorkflowCoordinator.certifyRepairSession(
            session = session,
            postScanSnapshot = postScan,
            verifierBaseUrl = "https://meet.elysium.app/verify",
            timestampMs = 1700000025000L
        )

        assertEquals(ForensicRepairWorkflowCoordinator.WorkflowState.CERTIFIED_CLOSED, closedSession.state)
        assertNotNull(closedSession.certificate)

        val cert = closedSession.certificate!!
        assertEquals(vehicleId, cert.vehicleId)
        assertEquals(serviceId, cert.serviceId)
        assertEquals(preScan.hashSha256, cert.preScanHash)
        assertEquals(postScan.hashSha256, cert.postScanHash)
        assertNotNull(cert.certifiedIntegrityHash)
        assertEquals(64, cert.certifiedIntegrityHash.length) // valid SHA-256

        // Validate 6-field QR payload (Zero PII, forensic integrity)
        val qr = cert.qrPayload
        assertEquals(cert.certificateId, qr.reportId)
        assertEquals(cert.certifiedIntegrityHash, qr.integrityHash)
        assertEquals(vehicleId, qr.vehicleId)
        assertEquals(ReportType.POST_SCAN_REPORT, qr.reportType)

        val encodedQr = qr.encode()
        assertTrue(encodedQr.startsWith("v1|${cert.certificateId}|${cert.certifiedIntegrityHash}|$vehicleId|"))
        assertFalse(encodedQr.contains("toyota")) // No PII in QR payload

        // Validate DTC Audit
        val audit = closedSession.dtcAudit!!
        assertTrue(audit.isFullyResolved)
        assertEquals(listOf("P0230", "P1709"), audit.resolvedDtcs)
        assertTrue(audit.residualDtcs.isEmpty())
        assertTrue(audit.newDtcs.isEmpty())
        assertEquals(1.0, audit.clearedCodesRatio, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cannot certify if post-scan snapshot has same hash as pre-scan`() {
        val snap = buildSnapshot("same-001", listOf("P0300"), 1000L)
        val session = ForensicRepairWorkflowCoordinator.ForensicRepairSession(
            sessionId = "s1",
            serviceId = "srv-1",
            vehicleId = vehicleId,
            mechanicId = mechanicId,
            state = ForensicRepairWorkflowCoordinator.WorkflowState.POST_SCAN_REQUIRED,
            preScanSnapshot = snap,
            createdAtMs = 1000L,
            updatedAtMs = 1000L
        )
        // Must reject duplicate/unchanged snapshot
        ForensicRepairWorkflowCoordinator.certifyRepairSession(session, snap)
    }

    @Test
    fun `dtc resolution audit correctly flags residual and new dtcs`() {
        val preScan = buildSnapshot("pre", listOf("P0300", "P0301"), 1000L)
        val postScan = buildSnapshot("post", listOf("P0301", "P0420"), 2000L)

        val audit = ForensicRepairWorkflowCoordinator.auditDtcResolution(preScan, postScan)
        assertEquals(listOf("P0300"), audit.resolvedDtcs)
        assertEquals(listOf("P0301"), audit.residualDtcs)
        assertEquals(listOf("P0420"), audit.newDtcs)
        assertFalse(audit.isFullyResolved)
        assertEquals(0.5, audit.clearedCodesRatio, 0.001)
    }
}
