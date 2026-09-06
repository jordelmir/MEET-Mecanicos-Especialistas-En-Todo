package com.elysium369.meet.mobility.domain.reserve

import com.elysium369.meet.mobility.domain.models.MarketId
import java.time.Duration
import java.time.Instant
import java.util.UUID

enum class ScheduledReservationState {
    CONFIRMED,
    DISPATCHING,
    FULFILLED,
    CANCELLED_FREE,
    CANCELLED_FEE,
    EXPIRED,
}

data class ScheduledRidePolicy(
    val marketId: MarketId,
    val minLeadTimeMinutes: Long = 30L,
    val maxLeadTimeDays: Long = 30L,
    val dispatchLeadTimeMinutes: Long = 25L,
    val cancellationFreeWindowMinutes: Long = 60L,
) {
    init {
        require(minLeadTimeMinutes > 0L) { "minLeadTimeMinutes must be > 0" }
        require(maxLeadTimeDays > 0L) { "maxLeadTimeDays must be > 0" }
        require(dispatchLeadTimeMinutes > 0L) { "dispatchLeadTimeMinutes must be > 0" }
        require(dispatchLeadTimeMinutes < minLeadTimeMinutes) {
            "dispatchLeadTimeMinutes ($dispatchLeadTimeMinutes) must be less than minLeadTimeMinutes ($minLeadTimeMinutes)"
        }
    }

    fun isEligibleBookingTime(now: Instant, scheduledTime: Instant): Boolean {
        val leadTime = Duration.between(now, scheduledTime)
        val minDuration = Duration.ofMinutes(minLeadTimeMinutes)
        val maxDuration = Duration.ofDays(maxLeadTimeDays)
        return leadTime >= minDuration && leadTime <= maxDuration
    }

    fun isFreeCancellation(now: Instant, scheduledTime: Instant): Boolean {
        val timeUntilPickup = Duration.between(now, scheduledTime)
        return timeUntilPickup >= Duration.ofMinutes(cancellationFreeWindowMinutes)
    }

    fun calculateDispatchTime(scheduledTime: Instant): Instant {
        return scheduledTime.minus(Duration.ofMinutes(dispatchLeadTimeMinutes))
    }
}

data class ScheduledRideReservation(
    val reservationId: UUID,
    val rideRequestId: UUID,
    val riderId: UUID,
    val scheduledPickupTime: Instant,
    val dispatchAt: Instant,
    val state: ScheduledReservationState,
    val assignedDriverId: UUID? = null,
    val createdAt: Instant,
) {
    init {
        require(dispatchAt <= scheduledPickupTime) {
            "dispatchAt ($dispatchAt) must be at or before scheduledPickupTime ($scheduledPickupTime)"
        }
    }
}
