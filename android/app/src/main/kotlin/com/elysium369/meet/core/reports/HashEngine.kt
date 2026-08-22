package com.elysium369.meet.core.reports

import java.security.MessageDigest

/**
 * Reports Hash Engine — pure, no I/O.
 *
 * Speaks the same language as the TypeScript `lib/reports/hash.ts`.
 * The `canonicalReportString` here MUST produce the same bytes as the
 * TS side so a report signed in the web can be verified by the APK
 * (and vice versa) using the same per-vehicle chain.
 *
 * For snapshot-only hashing see `diagnostic.DiagnosticSnapshot.computeHash`
 * — that one is already byte-exact with the TS side.
 */

data class DraftReport(
    val vehicleId: String,
    val userId: String,
    val reportType: String,
    val title: String,
    val odometerKm: Long?,
    val vin: String?,
    val plate: String?,
    val privacyRedactVin: Boolean,
    val privacyRedactPlate: Boolean,
    val privacyRedactLocation: Boolean,
    val privacyPublicShare: Boolean,
    val snapshotHash: String?,
    val evidenceHashes: List<String>,
    val repairActionHashes: List<String>,
    val peritajeHash: String?,
    val previousHash: String?,
    val notes: String,
)

object HashEngine {

    fun canonicalReportString(draft: DraftReport): String {
        return listOf(
            draft.vehicleId,
            draft.userId,
            draft.reportType,
            draft.title,
            draft.odometerKm?.toString() ?: "",
            draft.vin ?: "",
            draft.plate ?: "",
            if (draft.privacyRedactVin) "1" else "0",
            if (draft.privacyRedactPlate) "1" else "0",
            if (draft.privacyRedactLocation) "1" else "0",
            if (draft.privacyPublicShare) "1" else "0",
            draft.snapshotHash ?: "NO_SNAPSHOT",
            draft.evidenceHashes.joinToString(","),
            draft.repairActionHashes.joinToString(","),
            draft.peritajeHash ?: "NO_PERITAJE",
            draft.previousHash ?: "GENESIS",
            draft.notes,
        ).joinToString("|")
    }

    fun sha256Hex(input: String): String {
        return sha256Hex(input.toByteArray(Charsets.UTF_8))
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(bytes)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun hashReport(draft: DraftReport): String =
        sha256Hex(canonicalReportString(draft))

    fun hashDeviceId(deviceId: String): String =
        sha256Hex("device::$deviceId")

    data class ChainReport(
        val id: String,
        val generatedAt: Long,
        val integrityHash: String,
        val previousHash: String?,
    )

    data class ChainResult(val ok: Boolean, val brokenAt: String?)

    fun verifyChain(reports: List<ChainReport>): ChainResult {
        val sorted = reports.sortedBy { it.generatedAt }
        var prev: String? = null
        for (r in sorted) {
            if (r.previousHash != prev) return ChainResult(ok = false, brokenAt = r.id)
            prev = r.integrityHash
        }
        return ChainResult(ok = true, brokenAt = null)
    }
}
