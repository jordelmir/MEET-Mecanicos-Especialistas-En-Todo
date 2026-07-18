package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room mirror of `public.report_signatures`. The unique index on
 * `reportId` matches the SQL `idx_report_signatures_report_unique`
 * — a report can only carry ONE signature row. Re-signing requires
 * either a new draft or a `VOIDED` transition on the existing one.
 *
 * `integrityHash` is SHA-256 over
 *   (integrityHash + signerName + signerRole + signedAt + deviceIdHash)
 * recomputed from the report header, so it independently verifies that
 * the signature row was not tampered with after signing.
 *
 * `deviceIdHash` is `HashEngine.hashDeviceId(deviceId)` — never store
 * the raw device identifier, it leaks install context.
 */
@Entity(
    tableName = "report_signatures",
    foreignKeys = [
        ForeignKey(
            entity = CertifiedReportEntity::class,
            parentColumns = ["reportId"],
            childColumns = ["reportId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["reportId"], unique = true),
        Index(value = ["signerName"]),
    ],
)
data class ReportSignatureEntity(
    @PrimaryKey val signatureId: String,
    val reportId: String,
    val signerName: String,
    val signerRole: String,
    val signatureImageUri: String,
    val signedAt: Long,
    val deviceIdHash: String,
    val integrityHash: String,
)