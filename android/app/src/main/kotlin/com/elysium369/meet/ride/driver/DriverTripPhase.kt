package com.elysium369.meet.ride.driver

sealed interface DriverTripPhase {
    data object Assigned : DriverTripPhase
    data object ToPickup : DriverTripPhase
    data object AtPickup : DriverTripPhase
    data object PassengerOnboard : DriverTripPhase
    data object InProgress : DriverTripPhase
    data object Completed : DriverTripPhase
    data object Cancelled : DriverTripPhase
    data class Unsupported(
        val serverState: String,
    ) : DriverTripPhase
}

fun String.toDriverTripPhase(): DriverTripPhase = when (this) {
    "ASSIGNED", "ACCEPTED" -> DriverTripPhase.Assigned
    "DRIVER_EN_ROUTE" -> DriverTripPhase.ToPickup
    "ARRIVED" -> DriverTripPhase.AtPickup
    "PASSENGER_ONBOARD" -> DriverTripPhase.PassengerOnboard
    "IN_PROGRESS" -> DriverTripPhase.InProgress
    "COMPLETED" -> DriverTripPhase.Completed
    "CANCELLED" -> DriverTripPhase.Cancelled
    else -> DriverTripPhase.Unsupported(this)
}
