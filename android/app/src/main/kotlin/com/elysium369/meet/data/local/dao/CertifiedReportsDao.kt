package com.elysium369.meet.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.elysium369.meet.core.reports.ReportStatus
import com.elysium369.meet.data.local.entities.CertifiedReportEntity
import com.elysium369.meet.data.local.entities.DiagnosticSnapshotEntity
import com.elysium369.meet.data.local.entities.RepairActionEntity
import com.elysium369.meet.data.local.entities.ReportEvidenceEntity
import com.elysium369.meet.data.local.entities.ReportSignatureEntity
import kotlinx.coroutines.flow.Flow

/**
 * V2 Reports DAOs.
 *
 * Mirrors the 5 tables introduced in `supabase/migrations/20260704_reports_foundations.sql`
 * and `supabase/migrations/20260705_reports_sync_and_evidence_extend.sql`.
 *
 * Status transitions are enforced in [CertifiedReportDao.updateStatus],
 * which throws if a caller tries to mutate a SIGNED / EXPORTED / SHARED /
 * VOIDED report without going through the void path. This is a
 * client-side mirror of the SQL trigger
 * `trg_certified_reports_no_silent_mutation`.
 */
@Dao
interface CertifiedReportDao {

    @Query("SELECT * FROM certified_reports WHERE reportId = :reportId")
    suspend fun getById(reportId: String): CertifiedReportEntity?

    @Query("SELECT * FROM certified_reports WHERE vehicleId = :vehicleId ORDER BY generatedAt DESC")
    fun observeForVehicle(vehicleId: String): Flow<List<CertifiedReportEntity>>

    @Query(
        """SELECT * FROM certified_reports
           WHERE vehicleId = :vehicleId
             AND status IN ('SIGNED', 'EXPORTED', 'SHARED', 'VOIDED')
             AND integrityHash != 'UNSIGNED'
           ORDER BY signedAt ASC, generatedAt ASC, reportId ASC""",
    )
    suspend fun listForVehicleAsc(vehicleId: String): List<CertifiedReportEntity>

    @Query("SELECT * FROM certified_reports WHERE status = :status ORDER BY generatedAt DESC LIMIT :limit")
    suspend fun listByStatus(status: ReportStatus, limit: Int = 50): List<CertifiedReportEntity>

    @Query(
        """SELECT integrityHash FROM certified_reports
           WHERE vehicleId = :vehicleId
             AND reportId != :excludeReportId
             AND status IN ('SIGNED', 'EXPORTED', 'SHARED', 'VOIDED')
             AND integrityHash != 'UNSIGNED'
           ORDER BY signedAt DESC, generatedAt DESC, reportId DESC
           LIMIT 1""",
    )
    suspend fun latestHashForVehicle(vehicleId: String, excludeReportId: String): String?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(report: CertifiedReportEntity)

    /**
     * Hard status guard. Only allows:
     *   DRAFT    → READY, SIGNED, VOIDED
     *   READY    → SIGNED, VOIDED
     *   SIGNED   → EXPORTED, SHARED, VOIDED (terminal transitions)
     *   EXPORTED → SHARED, VOIDED
     *   SHARED   → VOIDED
     *   VOIDED   → (none)
     *
     * Any other transition throws [IllegalStateException]. This is the
     * Kotlin-side mirror of the SQL trigger; both layers must agree or
     * one of them is wrong.
     */
    @Update
    suspend fun update(report: CertifiedReportEntity)

    @Transaction
    suspend fun updateStatus(
        report: CertifiedReportEntity,
        newStatus: ReportStatus,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val current = getById(report.reportId) ?: return
        val ok = when (current.status) {
            ReportStatus.DRAFT -> newStatus in setOf(ReportStatus.READY, ReportStatus.SIGNED, ReportStatus.VOIDED)
            ReportStatus.READY -> newStatus in setOf(ReportStatus.SIGNED, ReportStatus.VOIDED)
            ReportStatus.SIGNED -> newStatus in setOf(ReportStatus.EXPORTED, ReportStatus.SHARED, ReportStatus.VOIDED)
            ReportStatus.EXPORTED -> newStatus in setOf(ReportStatus.SHARED, ReportStatus.VOIDED)
            ReportStatus.SHARED -> newStatus == ReportStatus.VOIDED
            ReportStatus.VOIDED -> false
        }
        if (!ok) {
            throw IllegalStateException(
                "Illegal status transition for report ${report.reportId}: ${current.status} → $newStatus"
            )
        }
        update(report.copy(status = newStatus, updatedAt = nowMs))
    }
}

@Dao
interface ReportEvidenceDao {

    @Query("SELECT * FROM report_evidence WHERE reportId = :reportId ORDER BY capturedAt ASC")
    suspend fun listForReport(reportId: String): List<ReportEvidenceEntity>

    @Query("SELECT hash FROM report_evidence WHERE reportId = :reportId AND hash IS NOT NULL ORDER BY capturedAt ASC")
    suspend fun hashesForReport(reportId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(evidence: ReportEvidenceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(evidence: List<ReportEvidenceEntity>)
}

@Dao
interface RepairActionDao {

    @Query("SELECT * FROM repair_actions WHERE reportId = :reportId ORDER BY createdAt ASC")
    suspend fun listForReport(reportId: String): List<RepairActionEntity>

    @Query("SELECT * FROM repair_actions WHERE dtcRelated = :dtc ORDER BY createdAt DESC")
    suspend fun listByDtc(dtc: String): List<RepairActionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(action: RepairActionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(actions: List<RepairActionEntity>)
}

@Dao
interface ReportSignatureDao {

    @Query("SELECT * FROM report_signatures WHERE reportId = :reportId LIMIT 1")
    suspend fun getForReport(reportId: String): ReportSignatureEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(signature: ReportSignatureEntity)

    /**
     * Composite uniqueness check used by [CertifiedReportRepository.sign]
     * before insert. If a signature row already exists for the report,
     * the only legal action is to VOID the prior report and start a new
     * draft — re-signing the same row would silently mutate a signed
     * report, which the SQL trigger also rejects.
     */
    @Query("SELECT COUNT(*) FROM report_signatures WHERE reportId = :reportId")
    suspend fun countForReport(reportId: String): Int
}

@Dao
interface DiagnosticSnapshotDao {

    @Query("SELECT * FROM diagnostic_snapshots WHERE snapshotId = :id")
    suspend fun getById(id: String): DiagnosticSnapshotEntity?

    @Query("SELECT * FROM diagnostic_snapshots WHERE reportId = :reportId ORDER BY createdAtMs ASC")
    suspend fun listForReport(reportId: String): List<DiagnosticSnapshotEntity>

    @Query("SELECT * FROM diagnostic_snapshots WHERE vehicleId = :vehicleId ORDER BY createdAtMs DESC LIMIT 1")
    suspend fun latestForVehicle(vehicleId: String): DiagnosticSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: DiagnosticSnapshotEntity)

    /**
     * Set `reportId` on a previously orphan snapshot. Used by
     * [com.elysium369.meet.data.local.CertifiedReportRepository.sign]
     * to bind the snapshot to the report at signing time. We do not
     * update `hashSha256` — that was computed at snapshot creation
     * and is part of the chain.
     */
    @Query("UPDATE diagnostic_snapshots SET reportId = :reportId WHERE snapshotId = :snapshotId")
    suspend fun attachToReport(snapshotId: String, reportId: String)
}
