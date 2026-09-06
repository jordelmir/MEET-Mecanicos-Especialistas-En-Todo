package com.elysium369.meet.mobility.domain.models

import java.time.Instant
import java.util.UUID

enum class RideRequestState {
    REQUESTED,
    SEARCHING,
    MATCHED,
    EXPIRED,
    CANCELLED,
}

enum class DispatchMode {
    AUTO_DISPATCH,
    MARKETPLACE_OFFERS,
}

@JvmInline
value class MarketId(val value: String) {
    init {
        require(value.isNotBlank()) { "MarketId cannot be blank" }
    }
}

@JvmInline
value class ServiceCategoryId(val value: String) {
    init {
        require(value.isNotBlank()) { "ServiceCategoryId cannot be blank" }
    }
}

data class RideRequest(
    val rideRequestId: UUID,
    val riderId: UUID,
    val marketId: MarketId,
    val serviceCategoryId: ServiceCategoryId,
    val dispatchMode: DispatchMode,
    val pickup: RideStop,
    val intermediateStops: List<RideStop>,
    val destination: RideStop,
    val requestedPrice: Money?,
    val state: RideRequestState,
    val scheduledFor: Instant?,
    val serverVersion: Long,
    val correlationId: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(serverVersion >= 1L) { "Server version must be >= 1" }
    }
}
