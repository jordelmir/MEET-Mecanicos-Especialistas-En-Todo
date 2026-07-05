package com.elysium369.meet.core.reports

/**
 * Hook invoked when a MechanicService transitions to COMPLETED.
 *
 * The repair is finished. Per the V2 spec, completion **must** be paired
 * with a Post-Scan report — otherwise we have no evidence that the
 * repair actually fixed the DTCs that triggered the service. This file
 * is the bridge between the legacy `MechanicServiceScreen` (still being
 * iterated by the parallel agent) and the V2 reports pipeline.
 *
 * The integration contract:
 *
 *   1. When the legacy screen calls
 *      `MechanicServiceDao.updateMechanicStatusAndPrice(... status = COMPLETED ...)`,
 *      it MUST also enqueue a `PostScanPrompt.requested(mechanicServiceId)`.
 *   2. The host screen observes the prompt state and, if a prompt is
 *      pending for the service, opens [InspectionSessionScreen] with
 *      `selectedType = POST_SCAN_REPORT` and the vehicle id from the
 *      service request.
 *   3. The Post-Scan is signed and persisted the same way as any other
 *      report — see [CertifiedReportRepository.sign].
 *
 * Why this is a sealed object and not a callback:
 *   - The integration point must survive the legacy screen being
 *     rewritten (Phase 7 is also when the screen consolidates with
 *     `InspectionSessionScreen`).
 *   - A static event channel keeps the integration testable as a pure
 *     function: enqueue → observe → open.
 */
object PostScanPrompt {

    sealed class Event {
        abstract val mechanicServiceId: String

        /** Emitted when a mechanic marks a service COMPLETED. */
        data class Requested(override val mechanicServiceId: String) : Event()

        /** Emitted after the operator dismisses the prompt (declined). */
        data class Dismissed(override val mechanicServiceId: String) : Event()

        /** Emitted when the Post-Scan is signed and persisted. */
        data class Completed(
            override val mechanicServiceId: String,
            val postScanReportId: String,
            val integrityHash: String,
        ) : Event()
    }

    private val pending = mutableListOf<Event.Requested>()

    @Synchronized
    fun request(mechanicServiceId: String) {
        pending += Event.Requested(mechanicServiceId)
    }

    @Synchronized
    fun consume(mechanicServiceId: String): Event.Requested? {
        val idx = pending.indexOfFirst { it.mechanicServiceId == mechanicServiceId }
        if (idx < 0) return null
        return pending.removeAt(idx)
    }

    @Synchronized
    fun pendingCount(): Int = pending.size
}