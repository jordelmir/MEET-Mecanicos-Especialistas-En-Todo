package com.elysium369.meet.core.reports

/**
 * Mirror of the Postgres enum `public.evidence_type` defined in
 *   supabase/migrations/20260704000000_reports_foundations.sql
 *   supabase/migrations/20260705000000_reports_sync_and_evidence_extend.sql
 *
 * Total: 19 values. Original 9 (PR-4) + 10 added in the evidence_extend
 * migration (BEFORE_PHOTO, AFTER_PHOTO, MULTIMETER_READING,
 * FUEL_PRESSURE_READING, PART_REPLACED, RECEIPT, CUSTOMER_SIGNATURE,
 * PROVIDER_NOTE, TEST_DRIVE_RESULT, PDF_REPORT).
 *
 * The wireValue is SCREAMING_SNAKE_CASE to match the SQL enum labels
 * exactly. Anything else breaks the hash chain because `hashReport`
 * canonicalizes evidence hashes in order, and ordering depends on the
 * wire label being identical across runtimes.
 */
enum class EvidenceType(val wireValue: String) {
    // Original 9
    PHOTO("PHOTO"),
    VIDEO("VIDEO"),
    OBD_SNAPSHOT("OBD_SNAPSHOT"),
    FREEZE_FRAME("FREEZE_FRAME"),
    SENSOR_GRAPH("SENSOR_GRAPH"),
    SIGNATURE("SIGNATURE"),
    MEASUREMENT("MEASUREMENT"),
    PART_INVOICE("PART_INVOICE"),
    REPAIR_NOTE("REPAIR_NOTE"),

    // Evidence-extend migration (2026-07-05)
    BEFORE_PHOTO("BEFORE_PHOTO"),
    AFTER_PHOTO("AFTER_PHOTO"),
    MULTIMETER_READING("MULTIMETER_READING"),
    FUEL_PRESSURE_READING("FUEL_PRESSURE_READING"),
    PART_REPLACED("PART_REPLACED"),
    RECEIPT("RECEIPT"),
    CUSTOMER_SIGNATURE("CUSTOMER_SIGNATURE"),
    PROVIDER_NOTE("PROVIDER_NOTE"),
    TEST_DRIVE_RESULT("TEST_DRIVE_RESULT"),
    PDF_REPORT("PDF_REPORT");

    companion object {
        fun fromWire(value: String?): EvidenceType? =
            value?.let { v -> entries.firstOrNull { it.wireValue == v } }
    }
}