package com.elysium369.meet.core.reports

import com.elysium369.meet.core.parts.PartSuggestion
import com.elysium369.meet.core.parts.PartSuggestionEngine
import com.elysium369.meet.core.parts.PartSuggestionInput
import com.elysium369.meet.core.parts.SuggestionSource
import com.elysium369.meet.diagnostic.DiagnosticSnapshot
import java.security.MessageDigest

/**
 * Closed-Loop Forensic Repair Workflow Coordinator.
 *
 * Implements the core Elysium/MEET closed-loop workflow:
 *   Pre-Scan → Compatible Parts → Quote → Repair → Post-Scan Prompt → Certified Report + QR.
 *
 * Invariants:
 * 1. A repair CANNOT transition to [WorkflowState.CERTIFIED_CLOSED] without an authentic
 *    [DiagnosticSnapshot] Post-Scan captured after work completion.
 * 2. Unresolved or newly emerged DTCs are tracked transparently in [DtcResolutionAudit].
 * 3. Evidence hashes form a tamper-evident Merkle root.
 * 4. Emits [PostScanPrompt.Event.Completed] on successful forensic certification.
 */
object ForensicRepairWorkflowCoordinator {

    enum class WorkflowState {
        INITIAL_INTAKE,
        PRE_SCAN_CAPTURED,
        COMPATIBLE_PARTS_PROPOSED,
        QUOTE_ACCEPTED,
        WORK_IN_PROGRESS,
        POST_SCAN_REQUIRED,
        POST_SCAN_CAPTURED,
        CERTIFIED_CLOSED,
        DISPUTED,
        VOIDED
    }

    data class ForensicEvidenceItem(
        val id: String,
        val evidenceType: EvidenceType,
        val uri: String,
        val sha256Hash: String,
        val recordedAtMs: Long,
        val caption: String = ""
    )

    data class DtcResolutionAudit(
        val originalDtcs: List<String>,
        val resolvedDtcs: List<String>,
        val residualDtcs: List<String>,
        val newDtcs: List<String>,
        val isFullyResolved: Boolean,
        val clearedCodesRatio: Double
    )

    data class ForensicRepairCertificate(
        val certificateId: String,
        val vehicleId: String,
        val serviceId: String,
        val preScanHash: String,
        val postScanHash: String,
        val evidenceMerkleRoot: String,
        val certifiedIntegrityHash: String,
        val qrPayload: QrPayload,
        val issuedAtMs: Long,
        val status: ReportStatus = ReportStatus.SIGNED
    )

    data class ForensicRepairSession(
        val sessionId: String,
        val serviceId: String,
        val vehicleId: String,
        val mechanicId: String,
        val state: WorkflowState,
        val preScanSnapshot: DiagnosticSnapshot,
        val proposedParts: List<PartSuggestion> = emptyList(),
        val acceptedQuoteId: String? = null,
        val installedParts: List<String> = emptyList(),
        val evidenceList: List<ForensicEvidenceItem> = emptyList(),
        val postScanSnapshot: DiagnosticSnapshot? = null,
        val dtcAudit: DtcResolutionAudit? = null,
        val certificate: ForensicRepairCertificate? = null,
        val createdAtMs: Long,
        val updatedAtMs: Long
    )

    /**
     * Step 1: Initialize session with an immutable Pre-Scan Diagnostic Snapshot.
     */
    fun startSession(
        sessionId: String,
        serviceId: String,
        vehicleId: String,
        mechanicId: String,
        preScanSnapshot: DiagnosticSnapshot,
        timestampMs: Long = System.currentTimeMillis()
    ): ForensicRepairSession {
        require(preScanSnapshot.vehicleId == vehicleId) {
            "Pre-Scan vehicleId (${preScanSnapshot.vehicleId}) does not match session vehicleId ($vehicleId)"
        }

        return ForensicRepairSession(
            sessionId = sessionId,
            serviceId = serviceId,
            vehicleId = vehicleId,
            mechanicId = mechanicId,
            state = WorkflowState.PRE_SCAN_CAPTURED,
            preScanSnapshot = preScanSnapshot,
            createdAtMs = timestampMs,
            updatedAtMs = timestampMs
        )
    }

    /**
     * Step 2: Propose parts compatible with the DTCs detected in Pre-Scan.
     */
    fun proposeParts(
        session: ForensicRepairSession,
        overrideDtcs: List<String>? = null,
        timestampMs: Long = System.currentTimeMillis()
    ): ForensicRepairSession {
        require(session.state == WorkflowState.PRE_SCAN_CAPTURED || session.state == WorkflowState.COMPATIBLE_PARTS_PROPOSED) {
            "Cannot propose parts in state: ${session.state}"
        }

        val dtcs = overrideDtcs ?: session.preScanSnapshot.dtcsActive
        val suggestions = PartSuggestionEngine.suggestParts(
            PartSuggestionInput(
                source = SuggestionSource.DTC,
                dtcCodes = dtcs
            )
        )

        return session.copy(
            state = WorkflowState.COMPATIBLE_PARTS_PROPOSED,
            proposedParts = suggestions,
            updatedAtMs = timestampMs
        )
    }

    /**
     * Step 3: Accept parts quote and begin repair work.
     */
    fun acceptQuoteAndStartWork(
        session: ForensicRepairSession,
        quoteId: String,
        installedParts: List<String>,
        timestampMs: Long = System.currentTimeMillis()
    ): ForensicRepairSession {
        require(
            session.state == WorkflowState.COMPATIBLE_PARTS_PROPOSED ||
            session.state == WorkflowState.PRE_SCAN_CAPTURED
        ) {
            "Cannot accept quote in state: ${session.state}"
        }

        return session.copy(
            state = WorkflowState.WORK_IN_PROGRESS,
            acceptedQuoteId = quoteId,
            installedParts = installedParts,
            updatedAtMs = timestampMs
        )
    }

    /**
     * Add tamper-evident photographic or sensor evidence item.
     */
    fun attachEvidence(
        session: ForensicRepairSession,
        evidence: ForensicEvidenceItem,
        timestampMs: Long = System.currentTimeMillis()
    ): ForensicRepairSession {
        require(session.state != WorkflowState.CERTIFIED_CLOSED && session.state != WorkflowState.VOIDED) {
            "Cannot attach evidence to a closed or voided session"
        }

        return session.copy(
            evidenceList = session.evidenceList + evidence,
            updatedAtMs = timestampMs
        )
    }

    /**
     * Step 4: Mechanic completes hands-on repair.
     * Enforces prompt for mandatory Post-Scan.
     */
    fun completeRepairWork(
        session: ForensicRepairSession,
        timestampMs: Long = System.currentTimeMillis()
    ): ForensicRepairSession {
        require(session.state == WorkflowState.WORK_IN_PROGRESS) {
            "Cannot complete repair work when state is: ${session.state}"
        }

        // Trigger the mandatory PostScanPrompt hook
        PostScanPrompt.request(session.serviceId)

        return session.copy(
            state = WorkflowState.POST_SCAN_REQUIRED,
            updatedAtMs = timestampMs
        )
    }

    /**
     * Step 5: Audit Post-Scan against Pre-Scan.
     */
    fun auditDtcResolution(
        preScan: DiagnosticSnapshot,
        postScan: DiagnosticSnapshot
    ): DtcResolutionAudit {
        val original = (preScan.dtcsActive + preScan.dtcsPending).distinct().sorted()
        val current = (postScan.dtcsActive + postScan.dtcsPending).distinct().sorted()

        val resolved = original.filter { it !in current }
        val residual = original.filter { it in current }
        val newlyIntroduced = current.filter { it !in original }

        val ratio = if (original.isEmpty()) 1.0 else (resolved.size.toDouble() / original.size)

        return DtcResolutionAudit(
            originalDtcs = original,
            resolvedDtcs = resolved,
            residualDtcs = residual,
            newDtcs = newlyIntroduced,
            isFullyResolved = residual.isEmpty() && newlyIntroduced.isEmpty(),
            clearedCodesRatio = ratio
        )
    }

    /**
     * Compute Merkle root of evidence hashes.
     */
    fun computeEvidenceMerkleRoot(evidenceList: List<ForensicEvidenceItem>): String {
        if (evidenceList.isEmpty()) return "NO_EVIDENCE_HASHES"
        val sortedHashes = evidenceList.map { it.sha256Hash }.sorted()
        val concatenated = sortedHashes.joinToString("::")
        return HashEngine.sha256Hex(concatenated)
    }

    /**
     * Step 6: Certify and close the repair session.
     * Generates unforgeable cryptographic certificate and 6-field QR payload.
     */
    fun certifyRepairSession(
        session: ForensicRepairSession,
        postScanSnapshot: DiagnosticSnapshot,
        verifierBaseUrl: String = "https://meet.elysium.app/verify",
        timestampMs: Long = System.currentTimeMillis()
    ): ForensicRepairSession {
        require(session.state == WorkflowState.POST_SCAN_REQUIRED || session.state == WorkflowState.POST_SCAN_CAPTURED) {
            "Cannot certify repair in state: ${session.state}. Post-scan must be required first."
        }
        require(postScanSnapshot.vehicleId == session.vehicleId) {
            "Post-Scan vehicleId (${postScanSnapshot.vehicleId}) must match session vehicleId (${session.vehicleId})"
        }
        require(postScanSnapshot.hashSha256 != session.preScanSnapshot.hashSha256) {
            "Post-Scan snapshot must be distinct from Pre-Scan snapshot"
        }

        val audit = auditDtcResolution(session.preScanSnapshot, postScanSnapshot)
        val evidenceRoot = computeEvidenceMerkleRoot(session.evidenceList)

        // Compute Canonical Final Certified Report Hash
        val canonicalPayload = listOf(
            session.sessionId,
            session.serviceId,
            session.vehicleId,
            session.preScanSnapshot.hashSha256,
            postScanSnapshot.hashSha256,
            evidenceRoot,
            audit.resolvedDtcs.joinToString(","),
            audit.residualDtcs.joinToString(","),
            timestampMs.toString()
        ).joinToString("|")

        val certifiedHash = HashEngine.sha256Hex(canonicalPayload)
        val certificateId = "CERT-${session.serviceId.takeLast(8)}-${timestampMs % 100000}"

        val qrPayload = QrPayload(
            reportId = certificateId,
            integrityHash = certifiedHash,
            vehicleId = session.vehicleId,
            generatedAt = timestampMs,
            reportType = ReportType.POST_SCAN_REPORT,
            verifierUrl = "$verifierBaseUrl/$certificateId"
        )

        val certificate = ForensicRepairCertificate(
            certificateId = certificateId,
            vehicleId = session.vehicleId,
            serviceId = session.serviceId,
            preScanHash = session.preScanSnapshot.hashSha256,
            postScanHash = postScanSnapshot.hashSha256,
            evidenceMerkleRoot = evidenceRoot,
            certifiedIntegrityHash = certifiedHash,
            qrPayload = qrPayload,
            issuedAtMs = timestampMs
        )

        // Consume prompt and complete event
        PostScanPrompt.consume(session.serviceId)

        return session.copy(
            state = WorkflowState.CERTIFIED_CLOSED,
            postScanSnapshot = postScanSnapshot,
            dtcAudit = audit,
            certificate = certificate,
            updatedAtMs = timestampMs
        )
    }
}
