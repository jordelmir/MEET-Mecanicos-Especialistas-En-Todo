package com.elysium369.meet.ui.screens.ride

import com.elysium369.meet.ride.domain.RideFareMode
import com.elysium369.meet.ride.domain.RideState
import com.elysium369.meet.ride.payment.RidePaymentMethod

enum class PlaceType {
    CURRENT, SEARCH, SAVED, HOME, WORK
}

data class RidePlaceInput(
    val placeId: String,
    val displayName: String,
    val address: String? = null,
    val latitude: Double,
    val longitude: Double,
    val placeType: PlaceType = PlaceType.SEARCH,
) {
    companion object {
        fun fromCurrentLocation(lat: Double, lng: Double): RidePlaceInput {
            return RidePlaceInput(
                placeId = "current_${System.currentTimeMillis()}",
                displayName = "Mi ubicación actual",
                address = "Lat: %.4f, Lng: %.4f".format(lat, lng),
                latitude = lat,
                longitude = lng,
                placeType = PlaceType.CURRENT,
            )
        }
    }
}

data class FareQuote(
    val baseFare: Long,
    val distanceFare: Long,
    val timeFare: Long,
    val totalFare: Long,
    val currency: String = "CRC",
    val estimatedDistanceKm: Double,
    val estimatedDurationMin: Int,
    val fareMode: RideFareMode = RideFareMode.METERED_TIME_DISTANCE,
) {
    val formattedTotal: String
        get() = "₡${totalFare.toString().replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1,")}"

    val formattedDistance: String
        get() = "%.1f km".format(estimatedDistanceKm)

    val formattedDuration: String
        get() = "$estimatedDurationMin min"
}

data class MatchedDriver(
    val driverId: String,
    val name: String,
    val rating: Double? = null,
    val totalTrips: Int? = null,
    val vehicle: String? = null,
    val plate: String? = null,
    val photoUrl: String? = null,
    val phone: String? = null,
    val etaMinutes: Int? = null,
    val distanceMeters: Int? = null,
)

data class PassengerInfo(
    val passengerId: String,
    val name: String,
    val rating: Double? = null,
    val totalTrips: Int? = null,
    val photoUrl: String? = null,
    val phone: String? = null,
)

data class RideLocationPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float = 5.0f,
    val timestamp: Long = System.currentTimeMillis(),
    val speed: Float? = null,
    val heading: Float? = null,
)

data class ActiveRideViewState(
    val rideId: String,
    val driver: MatchedDriver? = null,
    val pickup: RidePlaceInput,
    val dropoff: RidePlaceInput,
    val fareQuote: FareQuote,
    val state: RideState,
    val driverLocation: RideLocationPoint? = null,
    val passengerLocation: RideLocationPoint? = null,
    val startedAt: Long = System.currentTimeMillis(),
)

data class IncomingRideRequest(
    val rideId: String,
    val passenger: PassengerInfo,
    val pickup: RidePlaceInput,
    val dropoff: RidePlaceInput,
    val fare: Long,
    val distanceKm: Double,
    val durationMin: Int,
    val fareMode: RideFareMode = RideFareMode.METERED_TIME_DISTANCE,
    val paymentMethod: RidePaymentMethod = RidePaymentMethod.CASH,
    val requestTime: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 30_000,
)

data class ActiveDriverRide(
    val rideId: String,
    val passenger: PassengerInfo,
    val pickup: RidePlaceInput,
    val dropoff: RidePlaceInput,
    val fare: Long,
    val state: RideState,
    val startedAt: Long = System.currentTimeMillis(),
    var passengerLocation: RideLocationPoint? = null,
)

val RideFareMode.displayName: String
    get() = when (this) {
        RideFareMode.OPEN_BID -> "Oferta libre"
        RideFareMode.METERED_TIME_DISTANCE -> "Taxímetro / Medido"
    }

val RideFareMode.shortDescription: String
    get() = when (this) {
        RideFareMode.OPEN_BID -> "Negocia la tarifa directamente con conductores cercanos"
        RideFareMode.METERED_TIME_DISTANCE -> "Tarifa calculada por tiempo y distancia exacta"
    }

val RidePaymentMethod.displayName: String
    get() = displayLabelEs

val RideState.isCancellable: Boolean
    get() = this in listOf(
        RideState.DRAFT,
        RideState.SEARCHING,
        RideState.OFFERED,
        RideState.ASSIGNED,
        RideState.DRIVER_EN_ROUTE,
        RideState.ARRIVED
    )

fun getRideStatusLabel(state: RideState): String = when (state) {
    RideState.DRAFT -> "Borrador"
    RideState.SEARCHING -> "Buscando conductor..."
    RideState.OFFERED -> "Oferta disponible"
    RideState.ASSIGNED -> "Conductor asignado"
    RideState.DRIVER_EN_ROUTE -> "Conductor en camino"
    RideState.ARRIVED -> "Conductor llegó"
    RideState.PASSENGER_ONBOARD -> "En viaje"
    RideState.IN_PROGRESS -> "En viaje"
    RideState.COMPLETED -> "Viaje completado"
    RideState.CANCELLED -> "Viaje cancelado"
    RideState.EXPIRED -> "Expirado"
    RideState.DISPUTED -> "En revisión"
    RideState.UNKNOWN -> "Estado desconocido"
}
