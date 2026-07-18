package com.elysium369.meet.data.local

import com.elysium369.meet.core.reports.EvidenceType
import com.elysium369.meet.core.reports.HashEngine
import com.elysium369.meet.core.reports.ReportStatus
import com.elysium369.meet.core.reports.ReportType
import com.elysium369.meet.diagnostic.DiagnosticProvenance
import com.elysium369.meet.diagnostic.DiagnosticSnapshot
import com.elysium369.meet.data.local.entities.CertifiedReportEntity
import com.elysium369.meet.data.local.entities.DiagnosticSnapshotEntity
import com.elysium369.meet.data.local.entities.RepairActionEntity
import com.elysium369.meet.data.local.entities.ReportEvidenceEntity
import com.elysium369.meet.data.local.entities.ReportSignatureEntity

/**
 * Translators between the in-memory domain objects (`DiagnosticSnapshot`,
 * `DraftReport`) and the Room entities. Kept as `object` because the
 * conversions are pure and stateless; the Hilt graph does not need to
 * inject them.
 *
 * IMPORTANT — wireValue discipline:
 *   The Room entity stores the Kotlin enum by name (`PHOTO`, `DRAFT`).
 *   The Supabase sync layer expects the SCREAMING_SNAKE_CASE wire label
 *   which is identical for every value defined here. If a future enum
 *   value uses a different convention, [toWire] / [fromWire] must be
 *   updated and the SQL migration must add a matching enum label.
 */
object ReportMappers {

    // ── evidence ───────────────────────────────────────────────────────────

    fun evidenceToEntity(
        evidenceId: String,
        reportId: String,
        type: EvidenceType,
        label: String,
        description: String,
        uri: String,
        hash: String?,
        capturedAt: Long,
        lat: Double?,
        lng: Double?,
    ): ReportEvidenceEntity = ReportEvidenceEntity(
        evidenceId = evidenceId,
        reportId = reportId,
        evidenceType = type,
        label = label,
        description = description,
        uri = uri,
        hash = hash,
        capturedAt = capturedAt,
        lat = lat,
        lng = lng,
    )

    // ── repair actions ────────────────────────────────────────────────────

    fun repairToEntity(
        actionId: String,
        reportId: String,
        actionType: String,
        component: String,
        dtcRelated: String?,
        description: String,
        partUsed: String?,
        supplier: String?,
        mechanic: String?,
        cost: Double?,
        currency: String,
        warrantyDays: Int?,
        createdAt: Long,
    ): RepairActionEntity = RepairActionEntity(
        actionId = actionId,
        reportId = reportId,
        actionType = actionType,
        component = component,
        dtcRelated = dtcRelated,
        description = description,
        partUsed = partUsed,
        supplier = supplier,
        mechanic = mechanic,
        cost = cost,
        currency = currency,
        warrantyDays = warrantyDays,
        createdAt = createdAt,
    )

    // ── signatures ───────────────────────────────────────────────────────

    fun signatureToEntity(
        signatureId: String,
        reportId: String,
        signerName: String,
        signerRole: String,
        signatureImageUri: String,
        signedAt: Long,
        deviceIdHash: String,
        integrityHash: String,
    ): ReportSignatureEntity = ReportSignatureEntity(
        signatureId = signatureId,
        reportId = reportId,
        signerName = signerName,
        signerRole = signerRole,
        signatureImageUri = signatureImageUri,
        signedAt = signedAt,
        deviceIdHash = deviceIdHash,
        integrityHash = integrityHash,
    )

    // ── diagnostic snapshots ──────────────────────────────────────────────

    /**
     * Persist a [DiagnosticSnapshot] to Room. Note: the in-memory class
     * stores `provenance` as a sealed-class instance; Room only knows
     * the display label, so we serialize the label and trust the
     * `hashSha256` (which does not depend on provenance) for chain
     * verification.
     *
     * `livePids` (Map<String, DiagnosticValue<Double>>) is dropped here
     * because the [DiagnosticSnapshot.hashSha256] also does not include
     * it — the live values are a UI-only projection, not a chain input.
     */
    fun snapshotToEntity(
        snap: DiagnosticSnapshot,
        reportId: String?,
    ): DiagnosticSnapshotEntity = DiagnosticSnapshotEntity(
        snapshotId = snap.id,
        vehicleId = snap.vehicleId,
        sessionId = snap.sessionId,
        createdAtMs = snap.createdAtMs,
        dtcsActiveJson = jsonEncodeStringList(snap.dtcsActive),
        dtcsPendingJson = jsonEncodeStringList(snap.dtcsPending),
        dtcsPermanentJson = jsonEncodeStringList(snap.dtcsPermanent),
        freezeFramePidValuesJson = jsonEncodeDoubleMap(snap.freezeFramePidValues),
        livePidsJson = "{}", // not in chain — UI-only projection
        readinessJson = jsonEncodeBooleanMap(snap.readiness),
        ecuVoltage = snap.ecuVoltage,
        rpm = snap.rpm,
        coolantTempC = snap.coolantTempC,
        speedKph = snap.speedKph,
        engineLoadPct = snap.engineLoadPct,
        fuelTrimStft = snap.fuelTrimStft,
        fuelTrimLtft = snap.fuelTrimLtft,
        rawFramesJson = jsonEncodeStringList(snap.rawFrames),
        notes = snap.notes,
        liveFromAdapter = snap.provenance is DiagnosticProvenance.Real,
        provenanceLabel = snap.provenance.displayLabel,
        hashSha256 = snap.hashSha256,
        reportId = reportId,
    )

    // ── min-JSON helpers ──────────────────────────────────────────────────
    //
    // We deliberately avoid pulling kotlinx.serialization into the
    // mappers to keep the entity-construction path free of
    // reflective lookups. The shapes are small and stable; a hand-rolled
    // JSON encoder is easier to audit and faster to compile.

    private fun jsonEncodeStringList(values: List<String>): String =
        values.joinToString(prefix = "[", postfix = "]") { jsonEscape(it) }

    private fun jsonEncodeDoubleMap(values: Map<String, Double>): String =
        values.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
            "\"${jsonEscape(k)}\":$v"
        }

    private fun jsonEncodeBooleanMap(values: Map<String, Boolean>): String =
        values.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
            "\"${jsonEscape(k)}\":$v"
        }

    private fun jsonEscape(s: String): String = buildString(s.length + 2) {
        for (c in s) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
    }
}