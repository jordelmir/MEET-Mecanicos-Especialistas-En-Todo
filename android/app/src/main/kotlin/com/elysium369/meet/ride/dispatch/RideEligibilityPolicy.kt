package com.elysium369.meet.ride.dispatch

import com.elysium369.meet.ride.presence.RideDriverAvailability

object RideEligibilityPolicy {
    data class EligibilityResult(
        val eligible: Boolean,
        val reason: String? = null,
    )

    data class DriverOperationalQualifications(
        val identityVerified: Boolean,
        val driverApproved: Boolean,
        val vehicleAssigned: Boolean,
        val vehicleEligible: Boolean,
        val availability: RideDriverAvailability,
        val lastSeenAtMs: Long,
    )

    fun evaluateQualifications(
        qualifications: DriverOperationalQualifications,
        nowMs: Long,
        stalenessThresholdMs: Long = 5 * 60 * 1000L,
    ): EligibilityResult {
        if (!qualifications.identityVerified) {
            return EligibilityResult(false, "IDENTITY_NOT_VERIFIED: Identity verification required")
        }
        if (!qualifications.driverApproved) {
            return EligibilityResult(false, "DRIVER_NOT_APPROVED: Driver background and license review required")
        }
        if (!qualifications.vehicleAssigned) {
            return EligibilityResult(false, "NO_VEHICLE_ASSIGNED: Active vehicle must be linked")
        }
        if (!qualifications.vehicleEligible) {
            return EligibilityResult(false, "VEHICLE_NOT_ELIGIBLE: Vehicle inspection (Dekra) or mandatory insurance expired")
        }
        return evaluate(
            availability = qualifications.availability,
            lastSeenAtMs = qualifications.lastSeenAtMs,
            nowMs = nowMs,
            stalenessThresholdMs = stalenessThresholdMs,
        )
    }

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
