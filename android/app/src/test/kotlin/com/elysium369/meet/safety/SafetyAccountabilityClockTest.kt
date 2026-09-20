package com.elysium369.meet.safety

import com.elysium369.meet.safety.data.PublicAccountabilityEvent
import com.elysium369.meet.safety.ui.accountability.AccountabilityClock
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class SafetyAccountabilityClockTest {
    private val now = Instant.parse("2026-09-19T00:00:00Z")
    private fun event(type: String, date: String, version: Long = 1) =
        PublicAccountabilityEvent(type, "case", null, "institution", type, date, version)
    @Test fun missingReceiptIsUnknown() {
        val metrics = AccountabilityClock.calculate(listOf(event("REPORT_SENT", "2026-09-17T00:00:00Z")), now)
        assertNull(metrics.timeToReceipt)
        assertNull(metrics.timeToFirstResponse)
        assertEquals(2L, metrics.daysSinceReport ?: -1L)
    }
    @Test fun futureAndUnconfirmedEventsDoNotAdvanceClock() {
        val metrics = AccountabilityClock.calculate(listOf(event("REPORT_SENT", "2026-09-17T00:00:00Z"),
            event("DELIVERY_CONFIRMED", "2026-09-18T00:00:00Z", 0),
            event("RESPONSE_DOCUMENTED", "2026-09-20T00:00:00Z")), now)
        assertNull(metrics.timeToReceipt)
        assertNull(metrics.timeToFirstResponse)
        assertEquals(2L, metrics.daysSinceLastAction ?: -1L)
    }
    @Test fun responseBeforeReportDoesNotCreateNegativeDuration() {
        val metrics = AccountabilityClock.calculate(listOf(event("REPORT_SENT", "2026-09-17T00:00:00Z"),
            event("RESPONSE_DOCUMENTED", "2026-09-16T00:00:00Z")), now)
        assertNull(metrics.timeToFirstResponse)
    }
    @Test(expected = IllegalArgumentException::class) fun differentCasesCannotBeCombined() {
        val a = event("REPORT_SENT", "2026-09-17T00:00:00Z")
        AccountabilityClock.calculate(listOf(a, a.copy(case_id = "different")), now)
    }
}
