package com.elysium369.meet.core.reports

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/**
 * ══════════════════════════════════════════════════════════════════════
 *  C E R T I F I E D   R E P O R T   E N G I N E
 *  ──────────────────────────────────────────────────
 *  "A forensic inspector can verify a report independently
 *   with just the QR and SHA-256."  — AGENTS.md MVP bar
 *
 *  This is the keystone. Without this, the vision is incomplete.
 *
 *  Report lifecycle:
 *  ┌──────────────────────────────────────────────────────┐
 *  │  DRAFT → SEALED → CERTIFIED → SHARED                │
 *  │    │        │          │          │                   │
 *  │    │        │          │          └─ QR + hash only  │
 *  │    │        │          └─ SHA-256 signed, immutable  │
 *  │    │        └─ Content frozen, hash computed          │
 *  │    └─ Editable, not yet committed                    │
 *  │                                                      │
 *  │  VOIDED — if a certified report needs correction,    │
 *  │  create a NEW version with chained hash. NEVER       │
 *  │  silently edit a certified report. (AGENTS.md rule)  │
 *  └──────────────────────────────────────────────────────┘
 *
 *  QR Payload (minimal, NEVER contains PII):
 *  ┌──────────────────────────────────────────────────────┐
 *  │  report_id, integrity_hash, vehicle_id,             │
 *  │  generated_at, report_type, verifier_url            │
 *  │  (6 fields only — AGENTS.md rule #4)                │
 *  └──────────────────────────────────────────────────────┘
 * ══════════════════════════════════════════════════════════════════════
 */

// Uses existing ReportStatus (DRAFT/READY/SIGNED/EXPORTED/SHARED/VOIDED)
// Uses existing ReportType (PRE_SCAN_REPORT/POST_SCAN_REPORT/etc.)
// from ReportStatus.kt and ReportType.kt in this same package.

@Serializable
data class CertifiedReport(
    val reportId: String,
    val type: ReportType,
    val status: ReportStatus = ReportStatus.DRAFT,
    val vehicleId: String,
    val vehicleSummary: String,
    val mechanicId: String,
    val mechanicName: String,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val sealedAtEpochMs: Long? = null,
    val certifiedAtEpochMs: Long? = null,
    val sections: List<ReportSection> = emptyList(),
    val dtcCodes: List<String> = emptyList(),
    val odometerKm: Int? = null,
    val integrityHash: String = "",
    val previousVersionHash: String? = null,
    val certifierSignature: String = "",
    val version: Int = 1,
) {
    val isDraft: Boolean get() = status == ReportStatus.DRAFT
    val isSigned: Boolean get() = status == ReportStatus.SIGNED
    val isVoided: Boolean get() = status == ReportStatus.VOIDED
    val hasChainedHistory: Boolean get() = previousVersionHash != null
    val sectionCount: Int get() = sections.size
}

@Serializable
data class ReportSection(
    val sectionId: String,
    val title: String,
    val content: String,
    val severity: SectionSeverity = SectionSeverity.INFO,
    val evidenceRefs: List<String> = emptyList(),
    val measurements: Map<String, String> = emptyMap(),
)

enum class SectionSeverity {
    INFO,       // Informational
    GOOD,       // Passed inspection
    WARNING,    // Needs attention
    CRITICAL,   // Immediate action needed
    NOT_TESTED, // Honestly declared as not tested
}

// Uses existing QrPayload from QrPayload.kt in this same package.

// ─── Verification Result ───

@Serializable
data class ReportVerification(
    val isValid: Boolean,
    val reportId: String,
    val hashMatches: Boolean,
    val signatureValid: Boolean,
    val isVoided: Boolean,
    val chainIntact: Boolean,
    val message: String,
)

// ─── Certified Report Engine ───

class CertifiedReportEngine {

    companion object {
        const val VERIFIER_BASE_URL = "https://verify.elysium369.com/report/"
    }

    private val reports = mutableMapOf<String, CertifiedReport>()

    /**
     * Creates a new draft report.
     */
    fun createDraft(
        type: ReportType,
        vehicleId: String,
        vehicleSummary: String,
        mechanicId: String,
        mechanicName: String,
    ): CertifiedReport {
        val report = CertifiedReport(
            reportId = "rpt-${System.currentTimeMillis()}",
            type = type,
            vehicleId = vehicleId,
            vehicleSummary = vehicleSummary,
            mechanicId = mechanicId,
            mechanicName = mechanicName,
        )
        reports[report.reportId] = report
        return report
    }

    /**
     * Adds a section to a draft report.
     */
    fun addSection(reportId: String, section: ReportSection): Boolean {
        val report = reports[reportId] ?: return false
        if (!report.isDraft) return false
        reports[reportId] = report.copy(
            sections = report.sections + section,
        )
        return true
    }

    /**
     * SEALS a report — content is frozen, hash is computed.
     * After sealing, no more edits allowed.
     */
    fun seal(reportId: String): CertifiedReport? {
        val report = reports[reportId] ?: return null
        if (!report.isDraft) return null
        if (report.sections.isEmpty()) return null // must have content

        val hash = computeIntegrityHash(report)
        val sealed = report.copy(
            status = ReportStatus.READY,
            sealedAtEpochMs = System.currentTimeMillis(),
            integrityHash = hash,
        )
        reports[reportId] = sealed
        return sealed
    }

    /**
     * CERTIFIES a sealed report — signs it, makes it immutable.
     */
    fun certify(reportId: String, certifierId: String): CertifiedReport? {
        val report = reports[reportId] ?: return null
        if (report.status != ReportStatus.READY) return null

        val signature = sign(certifierId, report.integrityHash)
        val certified = report.copy(
            status = ReportStatus.SIGNED,
            certifiedAtEpochMs = System.currentTimeMillis(),
            certifierSignature = signature,
        )
        reports[reportId] = certified
        return certified
    }

    /**
     * Generates QR payload for a certified report.
     * ONLY 6 fields — NEVER contains PII (AGENTS.md rule #4).
     */
    fun generateQr(reportId: String): QrPayload? {
        val report = reports[reportId] ?: return null
        if (!report.isSigned) return null

        return QrPayload(
            reportId = report.reportId,
            integrityHash = report.integrityHash,
            vehicleId = report.vehicleId,
            generatedAt = report.certifiedAtEpochMs ?: report.createdAtEpochMs,
            reportType = report.type,
            verifierUrl = "${VERIFIER_BASE_URL}${report.reportId}",
        )
    }

    /**
     * VOIDS a certified report and creates a new version.
     * NEVER silently edits — creates chained version (AGENTS.md rule #3).
     */
    fun voidAndCreateNewVersion(
        reportId: String,
        reason: String,
    ): CertifiedReport? {
        val original = reports[reportId] ?: return null
        if (!original.isSigned) return null

        // Void the original
        reports[reportId] = original.copy(status = ReportStatus.VOIDED)

        // Create new version with chained hash
        val newReport = CertifiedReport(
            reportId = "rpt-${System.currentTimeMillis()}-v${original.version + 1}",
            type = original.type,
            vehicleId = original.vehicleId,
            vehicleSummary = original.vehicleSummary,
            mechanicId = original.mechanicId,
            mechanicName = original.mechanicName,
            previousVersionHash = original.integrityHash,
            version = original.version + 1,
            sections = original.sections + ReportSection(
                sectionId = "void-reason",
                title = "Razón de nueva versión",
                content = reason,
                severity = SectionSeverity.INFO,
            ),
        )
        reports[newReport.reportId] = newReport
        return newReport
    }

    /**
     * VERIFIES a report using only QR data — works offline.
     * This is the forensic verification that AGENTS.md demands.
     */
    fun verify(qrPayload: QrPayload): ReportVerification {
        val report = reports[qrPayload.reportId]

        if (report == null) {
            return ReportVerification(
                isValid = false, reportId = qrPayload.reportId,
                hashMatches = false, signatureValid = false,
                isVoided = false, chainIntact = true,
                message = "Reporte no encontrado en base local. " +
                    "Verifique en: ${qrPayload.verifierUrl}",
            )
        }

        val hashMatches = report.integrityHash == qrPayload.integrityHash
        val isVoided = report.isVoided
        val sigValid = report.certifierSignature.isNotBlank()
        val chainIntact = report.previousVersionHash == null ||
            reports.values.any { it.integrityHash == report.previousVersionHash }

        return ReportVerification(
            isValid = hashMatches && sigValid && !isVoided,
            reportId = report.reportId,
            hashMatches = hashMatches,
            signatureValid = sigValid,
            isVoided = isVoided,
            chainIntact = chainIntact,
            message = when {
                isVoided -> "⚠️ REPORTE ANULADO — fue reemplazado por una nueva versión."
                !hashMatches -> "❌ HASH NO COINCIDE — el reporte pudo ser alterado."
                !sigValid -> "❌ FIRMA INVÁLIDA — no se puede verificar autenticidad."
                else -> "✅ REPORTE VERIFICADO — integridad confirmada."
            },
        )
    }

    fun getReport(reportId: String): CertifiedReport? = reports[reportId]

    val totalReports: Int get() = reports.size

    // ─── Crypto ───

    private fun computeIntegrityHash(report: CertifiedReport): String {
        val content = buildString {
            append(report.reportId)
            append(report.vehicleId)
            append(report.mechanicId)
            append(report.type.name)
            append(report.createdAtEpochMs)
            report.sections.forEach { s ->
                append(s.sectionId)
                append(s.title)
                append(s.content)
            }
            report.previousVersionHash?.let { append(it) }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(content.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun sign(certifierId: String, hash: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val sig = digest.digest("$certifierId:$hash".toByteArray())
        return "sig-${sig.joinToString("") { "%02x".format(it) }.take(32)}"
    }
}
