package com.elysium369.meet.mobility.domain.models

import java.time.Instant
import java.util.UUID

enum class TripState {
    ASSIGNED,
    DRIVER_EN_ROUTE,
    DRIVER_ARRIVED,
    WAITING_FOR_RIDER,
    RIDER_ONBOARD,
    IN_PROGRESS,
    ARRIVED_DESTINATION,
    COMPLETED,
    CANCELLED,
    DISPUTED,
}

data class Trip(
    val tripId: UUID,
    val rideRequestId: UUID,
    val riderId: UUID,
    val driverId: UUID,
    val vehicleId: UUID,
    val state: TripState,
    val verificationPinHash: String?,
    val quoteId: UUID?,
    val paymentAuthorizationId: UUID?,
    val settlementId: UUID?,
    val serverVersion: Long,
    val assignedAt: Instant,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(serverVersion >= 1L) { "Server version must be >= 1" }
    }
}
