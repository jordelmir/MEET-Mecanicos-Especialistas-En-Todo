package com.elysium369.meet.mobility.domain.models

import java.time.Instant
import java.util.UUID

enum class DispatchOfferState {
    PENDING,
    ACCEPTED,
    REJECTED,
    EXPIRED,
    SUPERSEDED,
}

data class DispatchOffer(
    val dispatchOfferId: UUID,
    val rideRequestId: UUID,
    val driverId: UUID,
    val vehicleId: UUID,
    val state: DispatchOfferState,
    val expiresAt: Instant,
    val createdAt: Instant,
)
