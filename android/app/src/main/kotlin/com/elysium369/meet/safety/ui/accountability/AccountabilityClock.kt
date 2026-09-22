package com.elysium369.meet.safety.ui.accountability

import com.elysium369.meet.safety.data.PublicAccountabilityEvent
import java.time.Duration
import java.time.Instant

data class AccountabilityMetrics(
    val timeToReceipt: Duration?, val timeToFirstResponse: Duration?,
    val daysSinceReport: Long?, val daysSinceLastAction: Long?,
)

object AccountabilityClock {
    /** Call separately for each case and institution. Missing records never imply inaction. */
    fun calculate(events: List<PublicAccountabilityEvent>, now: Instant): AccountabilityMetrics {
        require(events.map { it.case_id to it.institution_ref }.distinct().size <= 1)
        val ordered = events.filter { it.server_version > 0 }.mapNotNull { event ->
            runCatching { event.event_type to Instant.parse(event.occurred_at) }.getOrNull()
        }.filter { it.second <= now }.sortedBy { it.second }
        val report = ordered.firstOrNull { it.first == "REPORT_SENT" }?.second
        fun duration(type: String): Duration? {
            val start = report ?: return null
            val end = ordered.firstOrNull { it.first == type && it.second >= start }?.second ?: return null
            return Duration.between(start, end)
        }
        return AccountabilityMetrics(duration("DELIVERY_CONFIRMED"), duration("RESPONSE_DOCUMENTED"),
            report?.let { Duration.between(it, now).toDays() },
            ordered.lastOrNull()?.second?.let { Duration.between(it, now).toDays() })
    }
}
