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
import com.elysium369.meet.mobility.data.protocol.ProtocolViolation
import com.elysium369.meet.mobility.data.protocol.optionalFloat
import com.elysium369.meet.mobility.data.protocol.optionalInstant
import com.elysium369.meet.mobility.data.protocol.optionalLong
import com.elysium369.meet.mobility.data.protocol.optionalString
import com.elysium369.meet.mobility.data.protocol.optionalUuid
import com.elysium369.meet.mobility.data.protocol.requireDouble
import com.elysium369.meet.mobility.data.protocol.requireInstant
import com.elysium369.meet.mobility.data.protocol.requireLong
import com.elysium369.meet.mobility.data.protocol.requireString
import com.elysium369.meet.mobility.data.protocol.requireUuid
import com.elysium369.meet.mobility.domain.result.GatewayFailure
import com.elysium369.meet.mobility.domain.result.MobilityCommandResult
import com.elysium369.meet.mobility.domain.result.MobilityErrorCode
import com.elysium369.meet.mobility.domain.result.MobilityFailureClassifier
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

@Singleton
class SupabaseMobilityCommandGateway @Inject constructor(
    private val supabase: SupabaseClient,
) : MobilityCommandGateway {

    private fun <T> handleFailure(t: Throwable): MobilityCommandResult<T> {
        val classified = MobilityFailureClassifier.classify(t)
        return when (classified) {
            is GatewayFailure.Retryable -> MobilityCommandResult.RetryableFailure(t)
            is GatewayFailure.Authentication -> MobilityCommandResult.Rejected(MobilityErrorCode.UNAUTHENTICATED, classified.message)
            is GatewayFailure.Protocol -> MobilityCommandResult.Rejected(MobilityErrorCode.UNKNOWN_ERROR, classified.message)
            is GatewayFailure.Terminal -> MobilityCommandResult.Rejected(MobilityErrorCode.UNKNOWN_ERROR, classified.message)
        }
    }

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
                val rideRequestId = response.requireUuid("ride_request_id")
                val state = RideRequestState.valueOf(response.requireString("state"))
                val version = response.requireLong("version")
                val stopsArray = response["stops"]?.jsonArray
                    ?: throw ProtocolViolation("stops", "missing stops array in response")
                val parsedStops = stopsArray.map { stopElem ->
                    val s = stopElem.jsonObject
                    RideStop(
                        stopId = s.requireUuid("stop_id"),
                        sequence = s["sequence"]?.jsonPrimitive?.int
                            ?: throw ProtocolViolation("sequence", "missing or invalid sequence"),
                        latitude = s.requireDouble("latitude"),
                        longitude = s.requireDouble("longitude"),
                        accuracyMeters = s.optionalFloat("accuracy_meters"),
                        displayName = s.optionalString("display_name"),
                        address = s.optionalString("address"),
                        placeId = s.optionalString("place_id"),
                        type = RideStopType.valueOf(s.requireString("stop_type")),
                    )
                }

                val pickupStop = parsedStops.firstOrNull { it.type == RideStopType.PICKUP }
                    ?: throw ProtocolViolation("stops", "missing required PICKUP stop in response")

                val intermediateStops = parsedStops.filter { it.type == RideStopType.INTERMEDIATE }

                val destinationStop = parsedStops.firstOrNull { it.type == RideStopType.DESTINATION }
                    ?: throw ProtocolViolation("stops", "missing required DESTINATION stop in response")

                val rideRequest = RideRequest(
                    rideRequestId = rideRequestId,
                    riderId = UUID.fromString(user.id),
                    marketId = command.marketId,
                    serviceCategoryId = command.serviceCategoryId,
                    dispatchMode = command.dispatchMode,
                    pickup = pickupStop,
                    intermediateStops = intermediateStops,
                    destination = destinationStop,
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            handleFailure(t)
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            handleFailure(t)
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            handleFailure(t)
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            handleFailure(t)
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            handleFailure(t)
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            handleFailure(t)
        }
    }

    private fun parseTripJson(json: JsonObject): Trip {
        return Trip(
            tripId = json.requireUuid("trip_id"),
            rideRequestId = json.requireUuid("ride_request_id"),
            riderId = json.requireUuid("rider_id"),
            driverId = json.requireUuid("driver_id"),
            vehicleId = json.requireUuid("vehicle_id"),
            state = TripState.valueOf(json.requireString("state")),
            quoteId = json.optionalUuid("quote_id"),
            paymentAuthorizationId = json.optionalUuid("payment_authorization_id"),
            settlementId = json.optionalUuid("settlement_id"),
            serverVersion = json.requireLong("version"),
            assignedAt = json.requireInstant("assigned_at"),
            startedAt = json.optionalInstant("started_at"),
            completedAt = json.optionalInstant("completed_at"),
            createdAt = json.requireInstant("created_at"),
            updatedAt = json.requireInstant("updated_at"),
        )
    }

    private fun parseRideRequestJson(json: JsonObject): RideRequest {
        val rideRequestId = json.requireUuid("ride_request_id")
        val stopsArray = json["stops"]?.jsonArray
        val (pickup, intermediateStops, destination) = if (stopsArray != null) {
            val parsed = stopsArray.map { stopElem ->
                val s = stopElem.jsonObject
                RideStop(
                    stopId = s.requireUuid("stop_id"),
                    sequence = s["sequence"]?.jsonPrimitive?.int
                        ?: throw ProtocolViolation("sequence", "missing sequence"),
                    latitude = s.requireDouble("latitude"),
                    longitude = s.requireDouble("longitude"),
                    accuracyMeters = s.optionalFloat("accuracy_meters"),
                    displayName = s.optionalString("display_name"),
                    address = s.optionalString("address"),
                    placeId = s.optionalString("place_id"),
                    type = RideStopType.valueOf(s.requireString("stop_type")),
                )
            }
            val p = parsed.firstOrNull { it.type == RideStopType.PICKUP }
                ?: throw ProtocolViolation("stops", "missing PICKUP stop")
            val d = parsed.firstOrNull { it.type == RideStopType.DESTINATION }
                ?: throw ProtocolViolation("stops", "missing DESTINATION stop")
            val i = parsed.filter { it.type == RideStopType.INTERMEDIATE }
            Triple(p, i, d)
        } else {
            val pickupStopId = json.optionalUuid("pickup_stop_id")
                ?: UUID.nameUUIDFromBytes("${rideRequestId}:pickup".toByteArray())
            val destStopId = json.optionalUuid("destination_stop_id")
                ?: UUID.nameUUIDFromBytes("${rideRequestId}:dest".toByteArray())
            val p = RideStop(
                stopId = pickupStopId,
                sequence = 0,
                latitude = json.requireDouble("pickup_latitude", "pickup_lat"),
                longitude = json.requireDouble("pickup_longitude", "pickup_lng"),
                accuracyMeters = json.optionalFloat("pickup_accuracy_meters"),
                displayName = json.optionalString("pickup_display_name"),
                address = json.optionalString("pickup_address"),
                placeId = json.optionalString("pickup_place_id"),
                type = RideStopType.PICKUP,
            )
            val d = RideStop(
                stopId = destStopId,
                sequence = 1,
                latitude = json.requireDouble("destination_latitude", "dest_lat"),
                longitude = json.requireDouble("destination_longitude", "dest_lng"),
                accuracyMeters = json.optionalFloat("destination_accuracy_meters"),
                displayName = json.optionalString("destination_display_name"),
                address = json.optionalString("destination_address"),
                placeId = json.optionalString("destination_place_id"),
                type = RideStopType.DESTINATION,
            )
            Triple(p, emptyList<RideStop>(), d)
        }

        return RideRequest(
            rideRequestId = rideRequestId,
            riderId = json.requireUuid("rider_id"),
            marketId = MarketId(json.requireString("market_id")),
            serviceCategoryId = ServiceCategoryId(json.requireString("service_category_id")),
            dispatchMode = DispatchMode.valueOf(json.requireString("dispatch_mode")),
            pickup = pickup,
            intermediateStops = intermediateStops,
            destination = destination,
            requestedPrice = json.optionalLong("requested_price_minor")?.let {
                val currency = json.optionalString("currency_code") ?: json.requireString("currency")
                Money(it, CurrencyCode.of(currency))
            },
            state = RideRequestState.valueOf(json.requireString("state")),
            scheduledFor = json.optionalInstant("scheduled_for"),
            serverVersion = json.requireLong("version"),
            correlationId = json.requireUuid("correlation_id"),
            createdAt = json.requireInstant("created_at"),
            updatedAt = json.requireInstant("updated_at"),
        )
    }
}

