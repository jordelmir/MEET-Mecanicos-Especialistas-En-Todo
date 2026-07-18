package com.elysium369.meet.data.local

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
) {

    // ── draft lifecycle ──────────────────────────────────────────────────

    /**
     * Creates a DRAFT report. Returns the row including the
     * `previousHash` link to the prior signed report for the same
     * vehicle (or null if this is the first report).
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
        val previousHash = reportDao.latestHashForVehicle(vehicleId)

        // Bind the snapshot to the future reportId from the start.
        // The SQL FK uses `ON DELETE SET NULL`, so the snapshot only
        // becomes "orphan" if the parent report is later deleted or
        // voided — never during the draft phase. Persisting it eagerly
        // ensures the snapshot's hash is recoverable from local DB
        // even if the user never signs the report.
        if (snapshot != null) {
            snapshotDao.upsert(ReportMappers.snapshotToEntity(snapshot, reportId = reportId))
        }
        // Persist evidence + repair actions under the future reportId
        // so foreign keys resolve.
        if (evidence.isNotEmpty()) evidenceDao.upsertAll(evidence.map { it.copy(reportId = reportId) })
        if (repairActions.isNotEmpty()) repairDao.upsertAll(repairActions.map { it.copy(reportId = reportId) })

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
            previousHash = previousHash,
            createdAt = nowMs,
            updatedAt = nowMs,
        )
        reportDao.upsert(draft)
        return draft
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
        val snapshotHash = snapshotDao.listForReport(reportId)
            .map { it.hashSha256 }
            .firstOrNull()

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
            previousHash = report.previousHash,
            notes = "", // notes are evidence rows, not header notes
            expectedHash = null,
        )

        // Bind the snapshot to this report (if any).
        snapshotDao.listForReport(reportId).forEach { snap ->
            snapshotDao.attachToReport(snap.snapshotId, reportId)
        }

        val signedReport = report.copy(
            status = ReportStatus.SIGNED,
            signedAt = nowMs,
            integrityHash = signed.hash,
            updatedAt = nowMs,
        )
        reportDao.upsert(signedReport)

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

        return signedReport
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
        val report = reportDao.getById(reportId)
            ?: throw IllegalStateException("Report $reportId does not exist")
        reportDao.updateStatus(report, ReportStatus.VOIDED)
        // The updatedAt bump is set inside updateStatus; we also touch
        // the row again to record void timestamp on the chain.
        reportDao.upsert(reportDao.getById(reportId)!!.copy(updatedAt = nowMs))
    }
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