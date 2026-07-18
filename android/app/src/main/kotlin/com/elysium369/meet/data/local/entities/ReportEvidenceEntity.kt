package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.elysium369.meet.core.reports.EvidenceType

/**
 * Room mirror of `public.report_evidence`. The FK to `certified_reports`
 * uses `onDelete = CASCADE` to match the SQL migration — deleting a
 * report cleans up its evidence rows locally too.
 *
 * `hash` is the SHA-256 of the evidence bytes (image, measurement value,
 * OBD frame, etc.). The `hashReport` canonicalization joins these in
 * `evidenceHashes` order, so the order of `getEvidenceForReport`
 * results MUST be stable (sorted by capturedAt asc by convention).
 */
@Entity(
    tableName = "report_evidence",
    foreignKeys = [
        ForeignKey(
            entity = CertifiedReportEntity::class,
            parentColumns = ["reportId"],
            childColumns = ["reportId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["reportId", "capturedAt"]),
        Index(value = ["evidenceType"]),
    ],
)
data class ReportEvidenceEntity(
    @PrimaryKey val evidenceId: String,
    val reportId: String,
    val evidenceType: EvidenceType,
    val label: String,
    val description: String,
    val uri: String,
    val hash: String?,
    val capturedAt: Long,
    val lat: Double?,
    val lng: Double?,
)