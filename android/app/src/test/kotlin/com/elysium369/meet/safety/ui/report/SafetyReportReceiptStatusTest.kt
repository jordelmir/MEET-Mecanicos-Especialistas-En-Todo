package com.elysium369.meet.safety.ui.report

import org.junit.Assert.assertEquals
import org.junit.Test

class SafetyReportReceiptStatusTest {

    @Test
    fun onlySyncedStateMeansServerConfirmed() {
        assertEquals(
            SafetyReportReceiptStatus.SERVER_CONFIRMED,
            safetyReportReceiptStatus("SYNCED"),
        )
        assertEquals(
            SafetyReportReceiptStatus.SYNCING,
            safetyReportReceiptStatus("SYNCING"),
        )
        assertEquals(
            SafetyReportReceiptStatus.FAILED,
            safetyReportReceiptStatus("FAILED"),
        )
        assertEquals(
            SafetyReportReceiptStatus.LOCAL_PENDING,
            safetyReportReceiptStatus("QUEUED"),
        )
        assertEquals(
            SafetyReportReceiptStatus.CHECKING,
            safetyReportReceiptStatus(null),
        )
    }

    @Test
    fun unknownOrFutureStatusesNeverBecomeAFalseRemoteSuccess() {
        listOf("UNKNOWN", "RETRYABLE_FAILURE", "CONFLICT", "PENDING").forEach { state ->
            assertEquals(
                "State $state must not look like a remote receipt",
                SafetyReportReceiptStatus.LOCAL_PENDING,
                safetyReportReceiptStatus(state),
            )
        }
    }
}
