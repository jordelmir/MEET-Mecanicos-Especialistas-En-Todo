package com.elysium369.meet.safety.ui.report

/**
 * Presentation state for a Safety report receipt.
 *
 * A non-null serverState string is not, by itself, a receipt. The repository
 * marks syncState as SYNCED only after the gateway accepts the server response.
 */
enum class SafetyReportReceiptStatus {
    CHECKING,
    SERVER_CONFIRMED,
    SYNCING,
    FAILED,
    LOCAL_PENDING,
}

fun safetyReportReceiptStatus(syncState: String?): SafetyReportReceiptStatus =
    when (syncState) {
        "SYNCED" -> SafetyReportReceiptStatus.SERVER_CONFIRMED
        "SYNCING" -> SafetyReportReceiptStatus.SYNCING
        "FAILED" -> SafetyReportReceiptStatus.FAILED
        null -> SafetyReportReceiptStatus.CHECKING
        else -> SafetyReportReceiptStatus.LOCAL_PENDING
    }
