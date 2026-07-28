package com.elysium369.meet.ride.domain

import kotlinx.serialization.Serializable

enum class RidePaymentMethod {
    CASH,
    SINPE,
}

@Serializable
data class RideStopSnapshot(
    val order: Int,
    val label: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val providerPlaceId: String? = null,
) {
    init {
        require(order in 1..32)
        require(label.length <= 500)
        require(latitude == null || latitude in -90.0..90.0)
        require(longitude == null || longitude in -180.0..180.0)
        require((latitude == null) == (longitude == null)) {
            "Stop coordinates must be both present or both absent"
        }
    }

    val isResolved: Boolean
        get() = label.isNotBlank() && latitude != null && longitude != null
}

object RideTripPlanPolicy {
    const val MAX_STOPS = 8

    fun normalize(stops: List<RideStopSnapshot>): List<RideStopSnapshot> {
        require(stops.size <= MAX_STOPS) { "Too many stops" }
        return stops.mapIndexed { index, stop -> stop.copy(order = index + 1) }
    }

    fun canDispatch(destinationResolved: Boolean, stops: List<RideStopSnapshot>): Boolean =
        destinationResolved &&
            stops.size <= MAX_STOPS &&
            stops.all(RideStopSnapshot::isResolved)
}

