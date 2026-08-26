package com.elysium369.meet.ride.dispatch

import com.elysium369.meet.ride.presence.RideDriverAvailability

object RideEligibilityPolicy {
    data class EligibilityResult(
        val eligible: Boolean,
        val reason: String? = null,
    )

    fun evaluate(
        availability: RideDriverAvailability,
        lastSeenAtMs: Long,
        nowMs: Long,
        stalenessThresholdMs: Long = 5 * 60 * 1000L,
    ): EligibilityResult {
        if (!availability.isDispatchable) {
            return EligibilityResult(false, "Driver availability is not dispatchable: $availability")
        }

        val elapsedMs = nowMs - lastSeenAtMs
        if (elapsedMs > stalenessThresholdMs) {
            return EligibilityResult(false, "Driver is stale (last seen $elapsedMs ms ago)")
        }

        return EligibilityResult(true)
    }
}
