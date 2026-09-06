package com.elysium369.meet.mobility.domain.commands

import com.elysium369.meet.mobility.domain.models.DispatchMode
import com.elysium369.meet.mobility.domain.models.MarketId
import com.elysium369.meet.mobility.domain.models.Money
import com.elysium369.meet.mobility.domain.models.RideStopInput
import com.elysium369.meet.mobility.domain.models.ServiceCategoryId
import com.elysium369.meet.mobility.domain.models.TripState
import java.time.Instant
import java.util.UUID

data class RequestRideCommand(
    val commandId: UUID,
    val correlationId: UUID,
    val marketId: MarketId,
    val serviceCategoryId: ServiceCategoryId,
    val dispatchMode: DispatchMode,
    val pickup: RideStopInput,
    val intermediateStops: List<RideStopInput> = emptyList(),
    val destination: RideStopInput,
    val requestedPrice: Money?,
    val scheduledFor: Instant? = null,
)

data class SubmitDriverOfferCommand(
    val commandId: UUID,
    val correlationId: UUID,
    val rideRequestId: UUID,
    val vehicleId: UUID,
    val offeredPrice: Money,
    val pickupEtaSeconds: Long?,
    val expectedRideVersion: Long,
)

data class SelectDriverOfferCommand(
    val commandId: UUID,
    val correlationId: UUID,
    val rideRequestId: UUID,
    val offerId: UUID,
    val expectedRideVersion: Long,
)

data class AcceptDispatchCommand(
    val commandId: UUID,
    val correlationId: UUID,
    val rideRequestId: UUID,
    val vehicleId: UUID,
    val dispatchOfferId: UUID,
    val expectedRideVersion: Long,
)

data class TransitionTripCommand(
    val commandId: UUID,
    val correlationId: UUID,
    val tripId: UUID,
    val targetState: TripState,
    val expectedTripVersion: Long,
    val verificationPin: String? = null,
)

data class CancelRideCommand(
    val commandId: UUID,
    val correlationId: UUID,
    val rideRequestId: UUID,
    val reason: String,
)
