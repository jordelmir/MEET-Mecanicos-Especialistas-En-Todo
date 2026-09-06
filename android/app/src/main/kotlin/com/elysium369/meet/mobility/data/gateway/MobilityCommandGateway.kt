package com.elysium369.meet.mobility.data.gateway

import com.elysium369.meet.mobility.domain.commands.AcceptDispatchCommand
import com.elysium369.meet.mobility.domain.commands.CancelRideCommand
import com.elysium369.meet.mobility.domain.commands.RequestRideCommand
import com.elysium369.meet.mobility.domain.commands.SelectDriverOfferCommand
import com.elysium369.meet.mobility.domain.commands.SubmitDriverOfferCommand
import com.elysium369.meet.mobility.domain.commands.TransitionTripCommand
import com.elysium369.meet.mobility.domain.models.DriverRideOffer
import com.elysium369.meet.mobility.domain.models.RideRequest
import com.elysium369.meet.mobility.domain.models.Trip
import com.elysium369.meet.mobility.domain.result.MobilityCommandResult

interface MobilityCommandGateway {
    suspend fun requestRide(command: RequestRideCommand): MobilityCommandResult<RideRequest>
    suspend fun submitDriverOffer(command: SubmitDriverOfferCommand): MobilityCommandResult<DriverRideOffer>
    suspend fun selectDriverOffer(command: SelectDriverOfferCommand): MobilityCommandResult<Trip>
    suspend fun acceptDispatch(command: AcceptDispatchCommand): MobilityCommandResult<Trip>
    suspend fun transitionTrip(command: TransitionTripCommand): MobilityCommandResult<Trip>
    suspend fun cancelRide(command: CancelRideCommand): MobilityCommandResult<RideRequest>
}
