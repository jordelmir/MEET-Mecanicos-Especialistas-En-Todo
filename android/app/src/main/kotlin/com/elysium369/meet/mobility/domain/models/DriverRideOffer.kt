package com.elysium369.meet.mobility.domain.models

import java.time.Instant
import java.util.UUID

enum class DriverOfferState {
    OPEN,
    SELECTED,
    REJECTED,
    WITHDRAWN,
    EXPIRED,
}

data class DriverRideOffer(
    val offerId: UUID,
    val rideRequestId: UUID,
    val driverId: UUID,
    val vehicleId: UUID,
    val offeredPrice: Money,
    val pickupEta: RideEta,
    val state: DriverOfferState,
    val expiresAt: Instant,
    val serverVersion: Long,
    val createdAt: Instant,
) {
    init {
        require(serverVersion >= 1L) { "Server version must be >= 1" }
    }
}
