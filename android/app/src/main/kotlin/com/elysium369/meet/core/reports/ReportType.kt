package com.elysium369.meet.core.reports

/**
 * Mirror of the Postgres enum `public.report_type` defined in
 *   supabase/migrations/20260704000000_reports_foundations.sql
 *
 * Adding a new value here REQUIRES a Postgres `alter type ... add value`
 * migration and the same label in `lib/reports/types.ts` to keep
 * cross-runtime parity.
 *
 * The labels are SCREAMING_SNAKE_CASE so the on-disk JSON, the SQL enum,
 * and the Kotlin enum all serialize byte-identically. Any drift will
 * break the `integrityHash` chain.
 */
enum class ReportType(val wireValue: String) {
    PRE_SCAN_REPORT("PRE_SCAN_REPORT"),
    POST_SCAN_REPORT("POST_SCAN_REPORT"),
    REPAIR_EVIDENCE_REPORT("REPAIR_EVIDENCE_REPORT"),
    PRE_PURCHASE_INSPECTION_REPORT("PRE_PURCHASE_INSPECTION_REPORT"),
    DVIR_REPORT("DVIR_REPORT");

    companion object {
        fun fromWire(value: String?): ReportType? =
            value?.let { v -> entries.firstOrNull { it.wireValue == v } }
    }
}