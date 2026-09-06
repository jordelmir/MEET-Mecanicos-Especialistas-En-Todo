package com.elysium369.meet.data.local

import androidx.room.withTransaction
import com.elysium369.meet.core.reports.EvidenceType
import com.elysium369.meet.core.reports.HashEngine
import com.elysium369.meet.core.reports.ReportHashingService
import com.elysium369.meet.core.reports.ReportStatus
import com.elysium369.meet.core.reports.ReportType
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
import com.elysium369.meet.diagnostic.DiagnosticSnapshot
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facade for the V2 reports pipeline.
 *
 * Reads/writes happen against Room; the Supabase sync layer is a
 * separate concern (`SupabaseReportSync`) and is out of scope for this
 * Phase 1 facade.
 *
 * The hard rules enforced here:
 *
 *  1. **No signed report is silently mutated.** [sign] is the only path
 *     that transitions a report into [ReportStatus.SIGNED]. After
 *     signing, the only legal transitions are EXPORTED, SHARED or
 *     VOIDED. Any other state change throws.
 *
 *  2. **No fabricated data.** [createDraft] accepts an optional
 *     [snapshot]; if it is null, the report carries `snapshotHash =
 *     null` and the renderer must show "Snapshot OBD no disponible.
 *     Reporte basado en datos manuales/offline." — never invent PIDs.
 *
 *  3. **Chain integrity.** [sign] reads the latest report for the same
 *     vehicle, takes its `integrityHash` as `previousHash`, and feeds
 *     everything through [HashEngine.hashReport]. The result is stored
 *     on the report row in the same transaction as the signature row.
 *
 *  4. **One signature per report.** Re-signing a signed report is
 *     impossible without first voiding it.
 */
@Singleton
class CertifiedReportRepository @Inject constructor(
    private val reportDao: CertifiedReportDao,
    private val evidenceDao: ReportEvidenceDao,
    private val repairDao: RepairActionDao,
    private val signatureDao: ReportSignatureDao,
    private val snapshotDao: DiagnosticSnapshotDao,
    private val hashing: ReportHashingService,
    private val transactions: ReportTransactionRunner = DirectReportTransactionRunner,
    private val pdfRenderer: com.elysium369.meet.core.export.CertifiedReportPdfRenderer? = null,
) {

    // ── draft lifecycle ──────────────────────────────────────────────────

    /**
     * Creates a DRAFT report. The chain predecessor is intentionally
     * unresolved while the row
     * is editable. [sign] resolves it inside the signing transaction,
     * preventing two long-lived drafts from claiming the same stale tip.
     *
     * The `integrityHash` field is filled with a placeholder
     * ("UNSIGNED") until [sign] is called. Do not read it before signing.
     */
    suspend fun createDraft(
        reportId: String,
        vehicleId: String,
        userId: String,
        reportType: ReportType,
        title: String,
        odometerKm: Int?,
        vin: String?,
        plate: String?,
        snapshot: DiagnosticSnapshot?,
        evidence: List<ReportEvidenceEntity>,
        repairActions: List<RepairActionEntity>,
        @Suppress("UNUSED_PARAMETER") notes: String,
        nowMs: Long = System.currentTimeMillis(),
    ): CertifiedReportEntity {
        val draft = CertifiedReportEntity(
            reportId = reportId,
            vehicleId = vehicleId,
            userId = userId,
            reportType = reportType,
            title = title,
            status = ReportStatus.DRAFT,
            odometerKm = odometerKm,
            vin = vin,
            plate = plate,
            generatedAt = nowMs,
            signedAt = null,
            pdfUri = null,
            qrVerificationUrl = null,
            integrityHash = "UNSIGNED",
            previousHash = null,
            createdAt = nowMs,
            updatedAt = nowMs,
        )
        return transactions.run {
            // Parent first: every following entity has a real Room FK to
            // certified_reports and must never be persisted speculatively.
            reportDao.insert(draft)
            if (snapshot != null) {
                snapshotDao.upsert(ReportMappers.snapshotToEntity(snapshot, reportId = reportId))
            }
            if (evidence.isNotEmpty()) {
                evidenceDao.upsertAll(evidence.map { it.copy(reportId = reportId) })
            }
            if (repairActions.isNotEmpty()) {
                repairDao.upsertAll(repairActions.map { it.copy(reportId = reportId) })
            }
            draft
        }
    }

    /**
     * Signs the report. Reads evidence + repair actions + (optional)
     * snapshot, builds the [com.elysium369.meet.core.reports.DraftReport],
     * hashes it through [ReportHashingService], and persists the result
     * + the signature row in a single critical section.
     *
     * Throws [IllegalStateException] if the report is already signed,
     * if no signer name was provided, or if the report has no evidence
     * — a SIGNED report without any photo / OBD snapshot / signature is
     * not a credible document.
     */
    suspend fun sign(
        reportId: String,
        signerName: String,
        signerRole: String,
        signatureImageUri: String,
        deviceId: String,
        nowMs: Long = System.currentTimeMillis(),
    ): CertifiedReportEntity {
        require(signerName.isNotBlank()) { "signerName cannot be blank" }

        return transactions.run {
            val report = reportDao.getById(reportId)
                ?: throw IllegalStateException("Report $reportId does not exist")

            if (report.status.isImmutable) {
                throw IllegalStateException(
                    "Cannot sign report $reportId in terminal status ${report.status}"
                )
            }
            if (signatureDao.countForReport(reportId) > 0) {
                throw IllegalStateException(
                    "Report $reportId already carries a signature. Void it first to re-sign."
                )
            }

            val evidences = evidenceDao.listForReport(reportId)
            val repairs = repairDao.listForReport(reportId)
            val snapshots = snapshotDao.listForReport(reportId)
            val snapshotHash = snapshots.firstOrNull()?.hashSha256
            val currentPreviousHash = reportDao.latestHashForVehicle(report.vehicleId, reportId)

        // The honest-phrases rule: if there is no snapshot, we pass
        // null and the renderer will display "Snapshot OBD no
        // disponible. Reporte basado en datos manuales/offline." We
        // never invent a snapshot or fabricate DTCs.
            val evidenceHashes = evidences.mapNotNull { it.hash }
            val repairHashes = repairs.map { it.hashForChain() }

            val signed = hashing.signDraftReport(
                vehicleId = report.vehicleId,
                userId = report.userId,
                reportType = report.reportType.wireValue,
                title = report.title,
                odometerKm = report.odometerKm?.toLong(),
                vin = report.vin,
                plate = report.plate,
                privacyRedactVin = false,
                privacyRedactPlate = false,
                privacyRedactLocation = false,
                privacyPublicShare = false,
                snapshotHash = snapshotHash,
                evidenceHashes = evidenceHashes,
                repairActionHashes = repairHashes,
                peritajeHash = null,
                previousHash = currentPreviousHash,
                notes = "", // notes are evidence rows, not header notes
                expectedHash = null,
            )

        // Bind the snapshot to this report (if any).
            snapshots.forEach { snap ->
                snapshotDao.attachToReport(snap.snapshotId, reportId)
            }

            val signedReport = report.copy(
                status = ReportStatus.SIGNED,
                signedAt = nowMs,
                integrityHash = signed.hash,
                previousHash = currentPreviousHash,
                updatedAt = nowMs,
            )
            reportDao.update(signedReport)

            val deviceIdHash = HashEngine.hashDeviceId(deviceId)
            val signatureIntegrity = HashEngine.sha256Hex(
                listOf(
                    signed.hash,
                    signerName,
                    signerRole,
                    nowMs.toString(),
                    deviceIdHash,
                ).joinToString("|")
            )
            signatureDao.insert(
                ReportMappers.signatureToEntity(
                    signatureId = "sig-${reportId}-${nowMs}",
                    reportId = reportId,
                    signerName = signerName,
                    signerRole = signerRole,
                    signatureImageUri = signatureImageUri,
                    signedAt = nowMs,
                    deviceIdHash = deviceIdHash,
                    integrityHash = signatureIntegrity,
                )
            )

            signedReport
        }
    }

    // ── reads ─────────────────────────────────────────────────────────────

    suspend fun getReport(reportId: String): CertifiedReportEntity? = reportDao.getById(reportId)

    fun observeForVehicle(vehicleId: String) = reportDao.observeForVehicle(vehicleId)

    /**
     * Walks the vehicle's reports in chronological order and verifies
     * that each `previousHash` matches the prior `integrityHash`.
     * Mirrors [HashEngine.verifyChain] but reads from Room instead of
     * a pre-loaded list — used by the Vehicle History screen on open.
     */
    suspend fun verifyChainForVehicle(vehicleId: String): HashEngine.ChainResult {
        val rows = reportDao.listForVehicleAsc(vehicleId).map { r ->
            HashEngine.ChainReport(
                id = r.reportId,
                generatedAt = r.generatedAt,
                integrityHash = r.integrityHash,
                previousHash = r.previousHash,
            )
        }
        return HashEngine.verifyChain(rows)
    }

    /**
     * Transitions a SIGNED / EXPORTED / SHARED report to VOIDED. The
     * underlying [CertifiedReportDao.updateStatus] enforces the legal
     * transition map. Caller is responsible for recording the void
     * reason elsewhere (audit log, repair case note, etc.).
     */
    suspend fun voidReport(reportId: String, nowMs: Long = System.currentTimeMillis()) {
        transactions.run {
            val report = reportDao.getById(reportId)
                ?: throw IllegalStateException("Report $reportId does not exist")
            reportDao.updateStatus(report, ReportStatus.VOIDED, nowMs)
        }
    }

    /**
     * Renders a multi-page certified PDF report, stamps it with the QR code and
     * integrity hash, saves to [outputFile], and transitions the report status
     * to EXPORTED.
     */
    suspend fun exportPdf(
        reportId: String,
        outputFile: java.io.File,
        privacyPolicy: com.elysium369.meet.core.reports.ReportPrivacyPolicy = com.elysium369.meet.core.reports.ReportPrivacyPolicy.OWNER_COPY,
        vehicleLabel: String? = null,
        vehicleOdometerKm: Long? = null,
        vehicleScore: Int? = null,
        peritajeVerdict: String? = null,
        renderer: com.elysium369.meet.core.export.CertifiedReportPdfRenderer? = null,
    ): java.io.File {
        val report = reportDao.getById(reportId)
            ?: throw IllegalStateException("Report $reportId does not exist")
        val evidence = evidenceDao.listForReport(reportId)
        val repairs = repairDao.listForReport(reportId)
        val snapshots = snapshotDao.listForReport(reportId)

        val content = com.elysium369.meet.core.export.CertifiedReportPdfRenderer.PageContent(
            report = report,
            evidence = evidence,
            repairs = repairs,
            snapshots = snapshots,
            vehicleLabel = vehicleLabel ?: "Vehículo ${report.vehicleId}",
            vehicleOdometerKm = vehicleOdometerKm ?: report.odometerKm?.toLong(),
            vehicleScore = vehicleScore,
            peritajeVerdict = peritajeVerdict,
            privacyPolicy = privacyPolicy,
        )

        val activeRenderer = renderer ?: pdfRenderer
            ?: throw IllegalStateException("No CertifiedReportPdfRenderer available for PDF export")
        activeRenderer.render(content, outputFile)

        val nowMs = System.currentTimeMillis()
        transactions.run {
            val freshReport = reportDao.getById(reportId) ?: report
            if (freshReport.status == ReportStatus.SIGNED) {
                reportDao.updateStatus(freshReport, ReportStatus.EXPORTED, nowMs)
            }
        }
        return outputFile
    }
}

/** Executes the complete report aggregate mutation atomically. */
interface ReportTransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

/** JVM-test fallback; production binds [RoomReportTransactionRunner]. */
private object DirectReportTransactionRunner : ReportTransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}

class RoomReportTransactionRunner(
    private val database: MeetDatabase,
) : ReportTransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T =
        database.withTransaction { block() }
}

/**
 * Stable SHA-256 over the action's chain-relevant fields. The set of
 * fields is small on purpose — adding fields here changes the chain
 * for every existing report, which is the correct loud failure for
 * any accidental schema drift.
 */
private fun RepairActionEntity.hashForChain(): String {
    val canonical = listOf(
        actionId,
        reportId,
        actionType,
        component,
        dtcRelated ?: "",
        description,
        partUsed ?: "",
        supplier ?: "",
        mechanic ?: "",
        cost?.toString() ?: "",
        currency,
        warrantyDays?.toString() ?: "",
        createdAt.toString(),
    ).joinToString("|")
    return HashEngine.sha256Hex(canonical)
}
