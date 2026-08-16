package com.elysium369.meet.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.elysium369.meet.core.reports.ReportStatus
import com.elysium369.meet.core.reports.ReportType

/**
 * Room mirror of `public.certified_reports` defined in
 *   supabase/migrations/20260704001000_reports_foundations.sql
 *
 * The column names match the SQL labels exactly (camelCase quoted in
 * Postgres) so a future sync layer can map row → entity without a
 * translation table.
 *
 * Status transitions are guarded by:
 *   - DB trigger `trg_certified_reports_no_silent_mutation`
 *   - `ReportStatus.isImmutable` client-side
 *   - `CertifiedReportRepository` (write methods reject bad transitions)
 *
 * Indexes mirror the SQL indexes for the same query shapes:
 *   - per-vehicle timeline (vehicleId, generatedAt desc)
 *   - chain verification (vehicleId, integrityHash)
 */
@Entity(
    tableName = "certified_reports",
    indices = [
        Index(value = ["vehicleId", "generatedAt"]),
        Index(value = ["userId", "generatedAt"]),
        Index(value = ["vehicleId", "integrityHash"]),
        Index(value = ["status"]),
        Index(value = ["reportType"]),
    ],
)
data class CertifiedReportEntity(
    @PrimaryKey val reportId: String,
    val vehicleId: String,
    val userId: String,
    @ColumnInfo(name = "reportType") val reportType: ReportType,
    val title: String,
    @ColumnInfo(name = "status") val status: ReportStatus,
    val odometerKm: Int?,
    val vin: String?,
    val plate: String?,
    val generatedAt: Long,
    val signedAt: Long?,
    val pdfUri: String?,
    val qrVerificationUrl: String?,
    val integrityHash: String,
    val previousHash: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
