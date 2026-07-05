package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room mirror of `public.diagnostic_snapshots`. Until V2, snapshots
 * lived only in-memory as `com.elysium369.meet.diagnostic.DiagnosticSnapshot`
 * and were signed via `ReportHashingService` without persistence.
 *
 * V2 persists the snapshot when it is bound to a `certified_reports`
 * row, so:
 *   - the QR can include the snapshot hash
 *   - Post-Scan can pull the matching Pre-Scan snapshot for comparison
 *   - the chain includes the snapshot hash, not just the report header
 *
 * The `onDelete = SET NULL` on `reportId` matches the SQL migration —
 * an orphan snapshot (not yet bound to a report) survives its parent
 * report deletion so the audit trail is not silently destroyed.
 *
 * `dtcsActive`/`dtcsPending`/`dtcsPermanent` are stored as JSON arrays
 * (text[]) on the SQL side. Room has no native array type, so we
 * serialize as a JSON string and decode via `ReportMappers`.
 */
@Entity(
    tableName = "diagnostic_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = CertifiedReportEntity::class,
            parentColumns = ["reportId"],
            childColumns = ["reportId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["vehicleId", "createdAtMs"]),
        Index(value = ["reportId"]),
        Index(value = ["hashSha256"]),
    ],
)
data class DiagnosticSnapshotEntity(
    @PrimaryKey val snapshotId: String,
    val vehicleId: String,
    val sessionId: String?,
    val createdAtMs: Long,
    val dtcsActiveJson: String,     // JSON-encoded List<String>
    val dtcsPendingJson: String,    // JSON-encoded List<String>
    val dtcsPermanentJson: String,  // JSON-encoded List<String>
    val freezeFramePidValuesJson: String, // JSON-encoded Map<String, Double>
    val livePidsJson: String,       // JSON-encoded Map<String, Double>
    val readinessJson: String,      // JSON-encoded Map<String, Boolean>
    val ecuVoltage: Double?,
    val rpm: Double?,
    val coolantTempC: Double?,
    val speedKph: Double?,
    val engineLoadPct: Double?,
    val fuelTrimStft: Double?,
    val fuelTrimLtft: Double?,
    val rawFramesJson: String,      // JSON-encoded List<String>
    val notes: String,
    val liveFromAdapter: Boolean,
    /**
     * Display label of the Kotlin `DiagnosticProvenance` sealed class
     * (e.g. "REAL", "OFFLINE", "SIN ENLACE", "INFERIDO (rule, 87%)").
     * Stored as a string because Room has no native sealed-class mapper.
     * The sync layer is responsible for translating between this label
     * and the Postgres enum (`LIVE_OBD / CACHED_OBD / MANUAL /
     * OFFLINE_FIXTURE`) when writing to `public.diagnostic_snapshots`.
     */
    val provenanceLabel: String,
    val hashSha256: String,
    val reportId: String?,
)