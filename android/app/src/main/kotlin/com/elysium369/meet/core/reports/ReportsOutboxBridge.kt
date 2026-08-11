package com.elysium369.meet.core.reports

import com.elysium369.meet.core.vanguard.VanguardOutboxDispatcher
import com.elysium369.meet.core.vanguard.VanguardOutboxEvent
import com.elysium369.meet.data.local.CertifiedReportRepository
import com.elysium369.meet.data.local.entities.CertifiedReportEntity
import com.elysium369.meet.data.local.entities.ReportEvidenceEntity
import com.elysium369.meet.data.local.entities.RepairActionEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the V2 reports pipeline to the existing Vanguard outbox so
 * signed reports sync to Supabase when the device comes online.
 *
 * **Offline-first rule.** A report is NEVER lost just because the
 * network is down. The flow:
 *
 *   1. `CertifiedReportRepository.sign(...)` writes the report +
 *      signature to Room locally. The report is durable at this point.
 *   2. `ReportsOutboxBridge.enqueueSignedReport(...)` pushes a
 *      `VanguardOutboxEvent` onto the existing dispatcher with the
 *      report id + hash. The dispatcher persists to its own buffer
 *      and tries Supabase.
 *   3. If Supabase is unreachable, the event stays queued and retried
 *      by `drain()` on next connectivity.
 *
 * Privacy toggles are applied at PDF render time, NEVER at sync time —
 * the hash always covers the full payload so an auditor can re-derive
 * any redacted view from the canonical record.
 */
@Singleton
class ReportsOutboxBridge @Inject constructor(
    private val reportRepo: CertifiedReportRepository,
    private val outbox: VanguardOutboxDispatcher,
) {

    /**
     * Push a signed report to the outbox. The payload is a minimal JSON
     * envelope so the Supabase worker can re-fetch the full row by id
     * (rather than trusting a remote-replicated payload that could
     * drift from the local source of truth).
     */
    suspend fun enqueueSignedReport(report: CertifiedReportEntity) {
        val payload = buildString {
            append("{")
            append("\"reportId\":\"").append(jsonEscape(report.reportId)).append("\",")
            append("\"vehicleId\":\"").append(jsonEscape(report.vehicleId)).append("\",")
            append("\"userId\":\"").append(jsonEscape(report.userId)).append("\",")
            append("\"reportType\":\"").append(jsonEscape(report.reportType.wireValue)).append("\",")
            append("\"status\":\"").append(jsonEscape(report.status.wireValue)).append("\",")
            append("\"integrityHash\":\"").append(jsonEscape(report.integrityHash)).append("\",")
            append("\"previousHash\":").append(
                if (report.previousHash == null) "null"
                else "\"" + jsonEscape(report.previousHash) + "\""
            ).append(",")
            append("\"generatedAt\":").append(report.generatedAt).append(",")
            append("\"signedAt\":").append(report.signedAt ?: report.generatedAt)
            append("}")
        }
        outbox.enqueue(
            VanguardOutboxEvent(
                id = "report-${report.reportId}",
                topic = "certified_reports.upsert",
                payloadJson = payload,
                createdAtMs = System.currentTimeMillis(),
            )
        )
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

/**
 * Privacy / redaction toggles. Applied at PDF render time only.
 *
 * The principle: the **stored** report and its hash always cover the
 * full payload. The **rendered** PDF and the **shared** view may hide
 * fields the operator considers sensitive (full VIN, plate, exact
 * location). This is intentional — it lets an auditor with the QR
 * recover the canonical record, while the customer-facing PDF respects
 * the privacy choice.
 */
data class ReportRedaction(
    val redactVin: Boolean = false,
    val redactPlate: Boolean = false,
    val redactLocation: Boolean = false,
    val publicShare: Boolean = false,
) {
    fun visibleVin(stored: String?): String? =
        if (redactVin && !stored.isNullOrEmpty()) stored.take(3) + "•••" + stored.takeLast(3) else stored

    fun visiblePlate(stored: String?): String? =
        if (redactPlate && !stored.isNullOrEmpty()) stored.take(2) + "•" + stored.takeLast(2) else stored
}
