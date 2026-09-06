package com.elysium369.meet.mobility.data.gateway

import com.elysium369.meet.mobility.domain.commands.AcceptDispatchCommand
import com.elysium369.meet.mobility.domain.commands.CancelRideCommand
import com.elysium369.meet.mobility.domain.commands.RequestRideCommand
import com.elysium369.meet.mobility.domain.commands.SelectDriverOfferCommand
import com.elysium369.meet.mobility.domain.commands.SubmitDriverOfferCommand
import com.elysium369.meet.mobility.domain.commands.TransitionTripCommand
import com.elysium369.meet.mobility.domain.models.CurrencyCode
import com.elysium369.meet.mobility.domain.models.DispatchMode
import com.elysium369.meet.mobility.domain.models.DriverOfferState
import com.elysium369.meet.mobility.domain.models.DriverRideOffer
import com.elysium369.meet.mobility.domain.models.MarketId
import com.elysium369.meet.mobility.domain.models.Money
import com.elysium369.meet.mobility.domain.models.RideEta
import com.elysium369.meet.mobility.domain.models.RideRequest
import com.elysium369.meet.mobility.domain.models.RideRequestState
import com.elysium369.meet.mobility.domain.models.RideStop
import com.elysium369.meet.mobility.domain.models.RideStopType
import com.elysium369.meet.mobility.domain.models.ServiceCategoryId
import com.elysium369.meet.mobility.domain.models.Trip
import com.elysium369.meet.mobility.domain.models.TripState
import com.elysium369.meet.mobility.domain.result.MobilityCommandResult
import com.elysium369.meet.mobility.domain.result.MobilityErrorCode
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

@Singleton
class SupabaseMobilityCommandGateway @Inject constructor(
    private val supabase: SupabaseClient,
) : MobilityCommandGateway {

    override suspend fun requestRide(command: RequestRideCommand): MobilityCommandResult<RideRequest> {
        val user = supabase.auth.currentUserOrNull() ?: return MobilityCommandResult.Rejected(
            code = MobilityErrorCode.UNAUTHENTICATED,
            message = "Authentication required to request a ride"
        )

        return try {
            val params = buildJsonObject {
                put("p_market_id", command.marketId.value)
                put("p_service_category_id", command.serviceCategoryId.value)
                put("p_dispatch_mode", command.dispatchMode.name)
                put("p_pickup_lat", command.pickup.latitude)
                put("p_pickup_lng", command.pickup.longitude)
                command.pickup.accuracyMeters?.let { put("p_pickup_accuracy", it) }
                command.pickup.address?.let { put("p_pickup_address", it) }
                put("p_destination_lat", command.destination.latitude)
                put("p_destination_lng", command.destination.longitude)
                command.destination.accuracyMeters?.let { put("p_destination_accuracy", it) }
                command.destination.address?.let { put("p_destination_address", it) }
                put("p_intermediate_stops", buildJsonArray {
                    command.intermediateStops.forEach { stop ->
                        add(buildJsonObject {
                            put("latitude", stop.latitude)
                            put("longitude", stop.longitude)
                            stop.accuracyMeters?.let { put("accuracy_meters", it) }
                            stop.address?.let { put("address", it) }
                            stop.displayName?.let { put("display_name", it) }
                            stop.placeId?.let { put("place_id", it) }
                        })
                    }
                })
                command.requestedPrice?.let { put("p_requested_price_minor", it.minorUnits) }
                command.scheduledFor?.let { put("p_scheduled_for", it.toString()) }
                put("p_idempotency_key", command.commandId.toString())
                put("p_correlation_id", command.correlationId.toString())
            }

            val response = supabase.postgrest.rpc("mobility_request_ride", params).decodeAs<JsonObject>()
            val success = response["success"]?.jsonPrimitive?.booleanOrNull ?: false
            if (success) {
                val rideRequestId = UUID.fromString(response["ride_request_id"]!!.jsonPrimitive.content)
                val state = RideRequestState.valueOf(response["state"]!!.jsonPrimitive.content)
                val version = response["version"]!!.jsonPrimitive.longOrNull ?: 1L

                val rideRequest = RideRequest(
                    rideRequestId = rideRequestId,
                    riderId = UUID.fromString(user.id),
                    marketId = command.marketId,
                    serviceCategoryId = command.serviceCategoryId,
                    dispatchMode = command.dispatchMode,
                    pickup = RideStop(
                        stopId = UUID.randomUUID(),
                        sequence = 0,
                        latitude = command.pickup.latitude,
                        longitude = command.pickup.longitude,
                        accuracyMeters = command.pickup.accuracyMeters,
                        displayName = command.pickup.displayName,
                        address = command.pickup.address,
                        placeId = command.pickup.placeId,
                        type = RideStopType.PICKUP,
                    ),
                    intermediateStops = command.intermediateStops.mapIndexed { idx, stop ->
                        RideStop(
                            stopId = UUID.randomUUID(),
                            sequence = idx + 1,
                            latitude = stop.latitude,
                            longitude = stop.longitude,
                            accuracyMeters = stop.accuracyMeters,
                            displayName = stop.displayName,
                            address = stop.address,
                            placeId = stop.placeId,
                            type = RideStopType.INTERMEDIATE,
                        )
                    },
                    destination = RideStop(
                        stopId = UUID.randomUUID(),
                        sequence = command.intermediateStops.size + 1,
                        latitude = command.destination.latitude,
                        longitude = command.destination.longitude,
                        accuracyMeters = command.destination.accuracyMeters,
                        displayName = command.destination.displayName,
                        address = command.destination.address,
                        placeId = command.destination.placeId,
                        type = RideStopType.DESTINATION,
                    ),
                    requestedPrice = command.requestedPrice,
                    state = state,
                    scheduledFor = command.scheduledFor,
                    serverVersion = version,
                    correlationId = command.correlationId,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
                MobilityCommandResult.Accepted(
                    value = rideRequest,
                    serverVersion = version,
                    canonicalReceiptId = response["receipt_id"]?.jsonPrimitive?.content,
                )
            } else {
                val errorCodeStr = response["error_code"]?.jsonPrimitive?.content ?: "UNKNOWN_ERROR"
                val errorCode = runCatching { MobilityErrorCode.valueOf(errorCodeStr) }.getOrDefault(MobilityErrorCode.UNKNOWN_ERROR)
                val message = response["message"]?.jsonPrimitive?.content
                MobilityCommandResult.Rejected(errorCode, message)
            }
        } catch (t: Throwable) {
            MobilityCommandResult.RetryableFailure(t)
        }
    }

    override suspend fun submitDriverOffer(command: SubmitDriverOfferCommand): MobilityCommandResult<DriverRideOffer> {
        val user = supabase.auth.currentUserOrNull() ?: return MobilityCommandResult.Rejected(
            code = MobilityErrorCode.UNAUTHENTICATED,
            message = "Authentication required to submit driver offer"
        )

        return try {
            val params = buildJsonObject {
                put("p_ride_request_id", command.rideRequestId.toString())
                put("p_vehicle_id", command.vehicleId.toString())
                put("p_offered_price_minor", command.offeredPrice.minorUnits)
                put("p_currency_code", command.offeredPrice.currency.value)
                command.pickupEtaSeconds?.let { put("p_pickup_eta_seconds", it) }
                put("p_expected_ride_version", command.expectedRideVersion)
                put("p_idempotency_key", command.commandId.toString())
            }

            val response = supabase.postgrest.rpc("mobility_submit_driver_offer", params).decodeAs<JsonObject>()
            val success = response["success"]?.jsonPrimitive?.booleanOrNull ?: false
            if (success) {
                val offerId = UUID.fromString(response["offer_id"]!!.jsonPrimitive.content)
                val version = response["version"]!!.jsonPrimitive.longOrNull ?: 1L
                val state = DriverOfferState.valueOf(response["state"]?.jsonPrimitive?.content ?: "OPEN")
                val expiresAt = Instant.parse(response["expires_at"]!!.jsonPrimitive.content)

                val offer = DriverRideOffer(
                    offerId = offerId,
                    rideRequestId = command.rideRequestId,
                    driverId = UUID.fromString(user.id),
                    vehicleId = command.vehicleId,
                    offeredPrice = command.offeredPrice,
                    pickupEta = command.pickupEtaSeconds?.let { RideEta.Routing(it, 0L) } ?: RideEta.Unavailable,
                    state = state,
                    expiresAt = expiresAt,
                    serverVersion = version,
                    createdAt = Instant.now(),
                )
                MobilityCommandResult.Accepted(offer, version)
            } else {
                val errorCodeStr = response["error_code"]?.jsonPrimitive?.content ?: "UNKNOWN_ERROR"
                val errorCode = runCatching { MobilityErrorCode.valueOf(errorCodeStr) }.getOrDefault(MobilityErrorCode.UNKNOWN_ERROR)
                val message = response["message"]?.jsonPrimitive?.content
                MobilityCommandResult.Rejected(errorCode, message)
            }
        } catch (t: Throwable) {
            MobilityCommandResult.RetryableFailure(t)
        }
    }

    override suspend fun selectDriverOffer(command: SelectDriverOfferCommand): MobilityCommandResult<Trip> {
        return try {
            val params = buildJsonObject {
                put("p_ride_request_id", command.rideRequestId.toString())
                put("p_offer_id", command.offerId.toString())
                put("p_expected_ride_version", command.expectedRideVersion)
                put("p_idempotency_key", command.commandId.toString())
            }

            val response = supabase.postgrest.rpc("mobility_select_driver_offer", params).decodeAs<JsonObject>()
            val success = response["success"]?.jsonPrimitive?.booleanOrNull ?: false
            if (success) {
                val trip = parseTripJson(response["trip"]!!.jsonObject)
                MobilityCommandResult.Accepted(trip, trip.serverVersion)
            } else {
                val conflict = response["conflict"]?.jsonPrimitive?.booleanOrNull ?: false
                if (conflict) {
                    val curVer = response["current_version"]?.jsonPrimitive?.longOrNull ?: command.expectedRideVersion
                    MobilityCommandResult.Conflict(curVer)
                } else {
                    val errorCodeStr = response["error_code"]?.jsonPrimitive?.content ?: "UNKNOWN_ERROR"
                    val errorCode = runCatching { MobilityErrorCode.valueOf(errorCodeStr) }.getOrDefault(MobilityErrorCode.UNKNOWN_ERROR)
                    val message = response["message"]?.jsonPrimitive?.content
                    MobilityCommandResult.Rejected(errorCode, message)
                }
            }
        } catch (t: Throwable) {
            MobilityCommandResult.RetryableFailure(t)
        }
    }

    override suspend fun acceptDispatch(command: AcceptDispatchCommand): MobilityCommandResult<Trip> {
        return try {
            val params = buildJsonObject {
                put("p_ride_request_id", command.rideRequestId.toString())
                put("p_dispatch_offer_id", command.dispatchOfferId.toString())
                put("p_vehicle_id", command.vehicleId.toString())
                put("p_expected_ride_version", command.expectedRideVersion)
                put("p_idempotency_key", command.commandId.toString())
            }

            val response = supabase.postgrest.rpc("mobility_accept_dispatch", params).decodeAs<JsonObject>()
            val success = response["success"]?.jsonPrimitive?.booleanOrNull ?: false
            if (success) {
                val trip = parseTripJson(response["trip"]!!.jsonObject)
                MobilityCommandResult.Accepted(trip, trip.serverVersion)
            } else {
                val conflict = response["conflict"]?.jsonPrimitive?.booleanOrNull ?: false
                if (conflict) {
                    val curVer = response["current_version"]?.jsonPrimitive?.longOrNull ?: command.expectedRideVersion
                    MobilityCommandResult.Conflict(curVer)
                } else {
                    val errorCodeStr = response["error_code"]?.jsonPrimitive?.content ?: "UNKNOWN_ERROR"
                    val errorCode = runCatching { MobilityErrorCode.valueOf(errorCodeStr) }.getOrDefault(MobilityErrorCode.UNKNOWN_ERROR)
                    val message = response["message"]?.jsonPrimitive?.content
                    MobilityCommandResult.Rejected(errorCode, message)
                }
            }
        } catch (t: Throwable) {
            MobilityCommandResult.RetryableFailure(t)
        }
    }

    override suspend fun transitionTrip(command: TransitionTripCommand): MobilityCommandResult<Trip> {
        return try {
            val params = buildJsonObject {
                put("p_trip_id", command.tripId.toString())
                put("p_target_state", command.targetState.name)
                put("p_expected_trip_version", command.expectedTripVersion)
                command.verificationPin?.let { put("p_verification_pin", it) }
                put("p_idempotency_key", command.commandId.toString())
            }

            val response = supabase.postgrest.rpc("mobility_transition_trip", params).decodeAs<JsonObject>()
            val success = response["success"]?.jsonPrimitive?.booleanOrNull ?: false
            if (success) {
                val trip = parseTripJson(response["trip"]!!.jsonObject)
                MobilityCommandResult.Accepted(trip, trip.serverVersion)
            } else {
                val conflict = response["conflict"]?.jsonPrimitive?.booleanOrNull ?: false
                if (conflict) {
                    val curVer = response["current_version"]?.jsonPrimitive?.longOrNull ?: command.expectedTripVersion
                    MobilityCommandResult.Conflict(curVer)
                } else {
                    val errorCodeStr = response["error_code"]?.jsonPrimitive?.content ?: "UNKNOWN_ERROR"
                    val errorCode = runCatching { MobilityErrorCode.valueOf(errorCodeStr) }.getOrDefault(MobilityErrorCode.UNKNOWN_ERROR)
                    val message = response["message"]?.jsonPrimitive?.content
                    MobilityCommandResult.Rejected(errorCode, message)
                }
            }
        } catch (t: Throwable) {
            MobilityCommandResult.RetryableFailure(t)
        }
    }

    override suspend fun cancelRide(command: CancelRideCommand): MobilityCommandResult<RideRequest> {
        return try {
            val params = buildJsonObject {
                put("p_ride_request_id", command.rideRequestId.toString())
                put("p_reason", command.reason)
                put("p_idempotency_key", command.commandId.toString())
            }

            val response = supabase.postgrest.rpc("mobility_cancel_ride", params).decodeAs<JsonObject>()
            val success = response["success"]?.jsonPrimitive?.booleanOrNull ?: false
            if (success) {
                val reqJson = response["ride_request"]!!.jsonObject
                val ride = parseRideRequestJson(reqJson)
                MobilityCommandResult.Accepted(ride, ride.serverVersion)
            } else {
                val errorCodeStr = response["error_code"]?.jsonPrimitive?.content ?: "UNKNOWN_ERROR"
                val errorCode = runCatching { MobilityErrorCode.valueOf(errorCodeStr) }.getOrDefault(MobilityErrorCode.UNKNOWN_ERROR)
                val message = response["message"]?.jsonPrimitive?.content
                MobilityCommandResult.Rejected(errorCode, message)
            }
        } catch (t: Throwable) {
            MobilityCommandResult.RetryableFailure(t)
        }
    }

    private fun parseTripJson(json: JsonObject): Trip {
        return Trip(
            tripId = UUID.fromString(json["trip_id"]!!.jsonPrimitive.content),
            rideRequestId = UUID.fromString(json["ride_request_id"]!!.jsonPrimitive.content),
            riderId = UUID.fromString(json["rider_id"]!!.jsonPrimitive.content),
            driverId = UUID.fromString(json["driver_id"]!!.jsonPrimitive.content),
            vehicleId = UUID.fromString(json["vehicle_id"]!!.jsonPrimitive.content),
            state = TripState.valueOf(json["state"]!!.jsonPrimitive.content),
            verificationPinHash = json["verification_pin_hash"]?.jsonPrimitive?.content,
            quoteId = json["quote_id"]?.jsonPrimitive?.content?.let { UUID.fromString(it) },
            paymentAuthorizationId = json["payment_authorization_id"]?.jsonPrimitive?.content?.let { UUID.fromString(it) },
            settlementId = json["settlement_id"]?.jsonPrimitive?.content?.let { UUID.fromString(it) },
            serverVersion = json["version"]!!.jsonPrimitive.longOrNull ?: 1L,
            assignedAt = Instant.parse(json["assigned_at"]!!.jsonPrimitive.content),
            startedAt = json["started_at"]?.jsonPrimitive?.content?.let { Instant.parse(it) },
            completedAt = json["completed_at"]?.jsonPrimitive?.content?.let { Instant.parse(it) },
            createdAt = Instant.parse(json["created_at"]!!.jsonPrimitive.content),
            updatedAt = Instant.parse(json["updated_at"]!!.jsonPrimitive.content),
        )
    }

    private fun parseRideRequestJson(json: JsonObject): RideRequest {
        val pickup = RideStop(
            stopId = UUID.randomUUID(),
            sequence = 0,
            latitude = json["pickup_lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
            longitude = json["pickup_lng"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
            accuracyMeters = null,
            displayName = null,
            address = json["pickup_address"]?.jsonPrimitive?.content,
            placeId = null,
            type = RideStopType.PICKUP,
        )
        val dest = RideStop(
            stopId = UUID.randomUUID(),
            sequence = 1,
            latitude = json["dest_lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
            longitude = json["dest_lng"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
            accuracyMeters = null,
            displayName = null,
            address = json["destination_address"]?.jsonPrimitive?.content,
            placeId = null,
            type = RideStopType.DESTINATION,
        )
        return RideRequest(
            rideRequestId = UUID.fromString(json["ride_request_id"]!!.jsonPrimitive.content),
            riderId = UUID.fromString(json["rider_id"]!!.jsonPrimitive.content),
            marketId = MarketId(json["market_id"]!!.jsonPrimitive.content),
            serviceCategoryId = ServiceCategoryId(json["service_category_id"]!!.jsonPrimitive.content),
            dispatchMode = DispatchMode.valueOf(json["dispatch_mode"]!!.jsonPrimitive.content),
            pickup = pickup,
            intermediateStops = emptyList(),
            destination = dest,
            requestedPrice = json["requested_price_minor"]?.jsonPrimitive?.longOrNull?.let {
                Money(it, CurrencyCode.of(json["currency_code"]!!.jsonPrimitive.content))
            },
            state = RideRequestState.valueOf(json["state"]!!.jsonPrimitive.content),
            scheduledFor = json["scheduled_for"]?.jsonPrimitive?.content?.let { Instant.parse(it) },
            serverVersion = json["version"]!!.jsonPrimitive.longOrNull ?: 1L,
            correlationId = json["correlation_id"]?.jsonPrimitive?.content?.let { UUID.fromString(it) } ?: UUID.randomUUID(),
            createdAt = Instant.parse(json["created_at"]!!.jsonPrimitive.content),
            updatedAt = Instant.parse(json["updated_at"]!!.jsonPrimitive.content),
        )
    }
}
