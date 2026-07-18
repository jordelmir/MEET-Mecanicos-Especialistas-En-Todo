package com.elysium369.meet.core.reports

/**
 * Mirror of the Postgres enum `public.report_status` defined in
 *   supabase/migrations/20260704000000_reports_foundations.sql
 *
 * Lifecycle:
 *   DRAFT → READY → SIGNED → EXPORTED → SHARED
 *                     ↘ VOIDED (terminal, retained for audit)
 *
 * A `SIGNED` report is immutable. Any change forces either a new draft
 * (with chained hash) or a transition to `VOIDED`. The DB trigger
 * `trg_certified_reports_no_silent_mutation` enforces this server-side
 * and the Kotlin repository enforces it client-side.
 */
enum class ReportStatus(val wireValue: String) {
    DRAFT("DRAFT"),
    READY("READY"),
    SIGNED("SIGNED"),
    EXPORTED("EXPORTED"),
    SHARED("SHARED"),
    VOIDED("VOIDED");

    val isTerminal: Boolean get() = this == VOIDED

    val isImmutable: Boolean get() = this == SIGNED || this == EXPORTED || this == SHARED || this == VOIDED

    companion object {
        fun fromWire(value: String?): ReportStatus? =
            value?.let { v -> entries.firstOrNull { it.wireValue == v } }
    }
}