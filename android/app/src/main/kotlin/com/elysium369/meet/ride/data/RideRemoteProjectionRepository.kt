package com.elysium369.meet.ride.data

import com.elysium369.meet.data.local.dao.RideDao
import com.elysium369.meet.data.local.entities.RideOfferEntity
import com.elysium369.meet.data.local.entities.RideRequestEntity
import com.elysium369.meet.data.remote.SupabaseModule
import com.elysium369.meet.ride.domain.RideStopSnapshot
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

sealed interface RideProjectionRefreshResult {
    data class Refreshed(val count: Int) : RideProjectionRefreshResult
    data object AuthenticationRequired : RideProjectionRefreshResult
    data class Failed(val message: String) : RideProjectionRefreshResult
}

@Serializable
private data class RemoteRideRequestProjection(
    val id: String,
    @SerialName("passenger_id")
    val passengerId: String,
    @SerialName("assigned_driver_id")
    val assignedDriverId: String? = null,
    @SerialName("assigned_vehicle_id")
    val assignedVehicleId: String? = null,
    @SerialName("pickup_latitude")
    val pickupLatitude: Double,
    @SerialName("pickup_longitude")
    val pickupLongitude: Double,
    @SerialName("pickup_address")
    val pickupAddress: String,
    @SerialName("destination_latitude")
    val destinationLatitude: Double,
    @SerialName("destination_longitude")
    val destinationLongitude: Double,
    @SerialName("destination_address")
    val destinationAddress: String,
    @SerialName("offered_fare_minor")
    val offeredFareMinor: Long,
    @SerialName("final_fare_minor")
    val finalFareMinor: Long? = null,
    val currency: String,
    val state: String,
    val version: Long,
    @SerialName("payment_method")
    val paymentMethod: String? = null,
    @SerialName("quote_version")
    val quoteVersion: Long = 1L,
    @SerialName("fare_breakdown")
    val fareBreakdown: JsonObject = JsonObject(emptyMap()),
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("completed_at")
    val completedAt: String? = null,
)

@Serializable
private data class RemoteRideStopProjection(
    @SerialName("request_id")
    val requestId: String,
    @SerialName("stop_order")
    val stopOrder: Int,
    @SerialName("provider_place_id")
    val providerPlaceId: String? = null,
    val label: String,
    val latitude: Double,
    val longitude: Double,
)

@Serializable
private data class RemoteRideOfferProjection(
    val id: String,
    @SerialName("request_id")
    val requestId: String,
    @SerialName("driver_id")
    val driverId: String,
    @SerialName("vehicle_id")
    val vehicleId: String,
    @SerialName("fare_minor")
    val fareMinor: Long,
    val currency: String,
    @SerialName("eta_seconds")
    val etaSeconds: Int? = null,
    val state: String,
    @SerialName("created_at")
    val createdAt: String,
)

@Serializable
private data class RemoteRideVehicleProjection(
    val id: String,
    @SerialName("display_name")
    val displayName: String,
)

@Singleton
class RideRemoteProjectionRepository @Inject constructor(
    private val rideDao: RideDao,
) {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    suspend fun refreshVisibleRides(): RideProjectionRefreshResult {
        val client = SupabaseModule.client
        if (client.auth.currentUserOrNull() == null) {
            return RideProjectionRefreshResult.AuthenticationRequired
        }
        return try {
            val remoteRides = client.postgrest["ride_requests"]
                .select {
                    order("created_at", Order.DESCENDING)
                    limit(MAX_VISIBLE_RIDES)
                }
                .decodeList<RemoteRideRequestProjection>()
            val stops = client.postgrest["ride_request_stops"]
                .select {
                    order("stop_order", Order.ASCENDING)
                    limit(MAX_VISIBLE_STOPS)
                }
                .decodeList<RemoteRideStopProjection>()
                .groupBy(RemoteRideStopProjection::requestId)
            val offers = client.postgrest["ride_offers"]
                .select {
                    order("created_at", Order.DESCENDING)
                    limit(MAX_VISIBLE_OFFERS)
                }
                .decodeList<RemoteRideOfferProjection>()
            val vehicles = client.postgrest["ride_driver_vehicles"]
                .select {
                    limit(MAX_VISIBLE_VEHICLES)
                }
                .decodeList<RemoteRideVehicleProjection>()
                .associateBy(RemoteRideVehicleProjection::id)
            val acceptedOfferByRequest = offers
                .asSequence()
                .filter { it.state == "ACCEPTED" }
                .associateBy(RemoteRideOfferProjection::requestId)

            remoteRides.forEach { remote ->
                val existing = rideDao.getRequestById(remote.id)
                val orderedStops = stops[remote.id].orEmpty().map { stop ->
                    RideStopSnapshot(
                        order = stop.stopOrder,
                        label = stop.label,
                        latitude = stop.latitude,
                        longitude = stop.longitude,
                        providerPlaceId = stop.providerPlaceId,
                    )
                }
                rideDao.insertRequest(
                    remote.toLocal(
                        existing = existing,
                        stops = orderedStops,
                        acceptedOfferId = acceptedOfferByRequest[remote.id]?.id,
                    ),
                )
            }
            offers.forEach { offer ->
                rideDao.insertOffer(
                    offer.toLocal(vehicles[offer.vehicleId]?.displayName),
                )
            }
            RideProjectionRefreshResult.Refreshed(remoteRides.size + offers.size)
        } catch (error: Exception) {
            RideProjectionRefreshResult.Failed(
                (error.message ?: "No se pudo actualizar Viajes").take(300),
            )
        }
    }

    fun realtimeWakeUps(): Flow<Unit> = flow {
        val client = SupabaseModule.client
        val channel = client.channel("elysium-rides-projection")
        val changes = channel
            .postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "ride_requests"
            }
            .map { Unit }
        val offerChanges = channel
            .postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "ride_offers"
            }
            .map { Unit }
        val stopChanges = channel
            .postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "ride_request_stops"
            }
            .map { Unit }
        val vehicleChanges = channel
            .postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "ride_driver_vehicles"
            }
            .map { Unit }
        try {
            channel.subscribe()
            // Subscription events are only wake-ups. Every wake-up is followed
            // by an authenticated RLS-filtered catch-up query.
            emit(Unit)
            emitAll(merge(changes, offerChanges, stopChanges, vehicleChanges))
        } finally {
            channel.unsubscribe()
        }
    }

    private fun RemoteRideRequestProjection.toLocal(
        existing: RideRequestEntity?,
        stops: List<RideStopSnapshot>,
        acceptedOfferId: String?,
    ): RideRequestEntity {
        val offeredMajor = offeredFareMinor.toLegacyMajor(currency)
        val finalMajor = finalFareMinor?.toLegacyMajor(currency)
        return RideRequestEntity(
            requestId = id,
            passengerId = existing?.passengerId ?: passengerId,
            passengerName = existing?.passengerName ?: "Pasajero verificado",
            passengerPhone = existing?.passengerPhone.orEmpty(),
            pickupLatitude = pickupLatitude,
            pickupLongitude = pickupLongitude,
            pickupAddress = pickupAddress,
            pickupAccuracy = existing?.pickupAccuracy ?: 0f,
            destLatitude = destinationLatitude,
            destLongitude = destinationLongitude,
            destAddress = destinationAddress,
            priceOffer = offeredMajor,
            priceOfferMinor = offeredFareMinor,
            currency = currency,
            estimatedDistanceKm = existing?.estimatedDistanceKm ?: 0.0,
            estimatedDurationMin = existing?.estimatedDurationMin ?: 0,
            stopsJson = if (stops.isEmpty()) {
                existing?.stopsJson ?: "[]"
            } else {
                json.encodeToString(stops)
            },
            paymentMethod = paymentMethod ?: existing?.paymentMethod ?: "CASH",
            quoteVersion = quoteVersion,
            fareBreakdownJson = fareBreakdown.toString(),
            status = state.toLegacyStatus(),
            acceptedOfferId = acceptedOfferId ?: existing?.acceptedOfferId,
            assignedDriverId = assignedDriverId,
            assignedDriverName = existing?.assignedDriverName,
            assignedDriverPhone = existing?.assignedDriverPhone,
            assignedDriverVehicle = existing?.assignedDriverVehicle,
            finalPrice = finalMajor,
            finalPriceMinor = finalFareMinor,
            serverState = state,
            serverVersion = version,
            serverAssignedVehicleId = assignedVehicleId,
            syncState = "SYNCED",
            lastSyncedAt = System.currentTimeMillis(),
            lastCorrelationId = existing?.lastCorrelationId,
            boardingPin = existing?.boardingPin,
            boardingPinExpiresAt = existing?.boardingPinExpiresAt,
            passengerRating = existing?.passengerRating,
            driverRating = existing?.driverRating,
            createdAt = createdAt.toEpochMillisOr(existing?.createdAt ?: 0L),
            completedAt = completedAt?.toEpochMillisOr(
                existing?.completedAt ?: 0L,
            )?.takeIf { it > 0L },
        )
    }

    private fun RemoteRideOfferProjection.toLocal(
        vehicleDisplayName: String?,
    ): RideOfferEntity = RideOfferEntity(
        offerId = id,
        requestId = requestId,
        driverId = driverId,
        // Before assignment the passenger receives only an aggregate identity.
        // This avoids widening profile RLS just to render an offer card.
        driverName = "Conductor verificado",
        driverPhone = "",
        driverRating = 0.0,
        driverTotalTrips = 0,
        vehicleDescription = vehicleDisplayName ?: "Vehículo verificado",
        counterPrice = fareMinor.toLegacyMajor(currency),
        currency = currency,
        estimatedArrivalMin = ((etaSeconds ?: 0) / 60.0).toInt(),
        driverLatitude = 0.0,
        driverLongitude = 0.0,
        message = null,
        status = state,
        createdAt = createdAt.toEpochMillisOr(0L),
    )

    private fun Long.toLegacyMajor(currency: String): Double =
        if (currency == "CRC") toDouble() else toDouble() / 100.0

    private fun String.toEpochMillisOr(fallback: Long): Long =
        runCatching { Instant.parse(this).toEpochMilli() }.getOrDefault(fallback)

    private fun String.toLegacyStatus(): String = when (this) {
        "SEARCHING", "OFFERED" -> "OPEN"
        "ASSIGNED", "DRIVER_EN_ROUTE" -> "ACCEPTED"
        else -> this
    }

    private companion object {
        const val MAX_VISIBLE_RIDES = 100L
        const val MAX_VISIBLE_STOPS = 512L
        const val MAX_VISIBLE_OFFERS = 300L
        const val MAX_VISIBLE_VEHICLES = 100L
    }
}
