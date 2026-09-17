package com.elysium369.meet.ride.driver

sealed interface DriverTripIntent {
    data object StartDrivingToPickup : DriverTripIntent
    data object ConfirmArrival : DriverTripIntent
    data object OpenBoardingPin : DriverTripIntent
    data object DismissBoardingPin : DriverTripIntent
    data class UpdateBoardingPinInput(val pin: String) : DriverTripIntent
    data class SubmitBoardingPin(val pin: String) : DriverTripIntent
    data object StartTrip : DriverTripIntent
    data object CompleteTrip : DriverTripIntent
    data object ToggleAcceptingFutureOffers : DriverTripIntent
    data class CancelTrip(val reasonCode: String, val detail: String) : DriverTripIntent
    data class OpenSupport(val category: String, val summary: String) : DriverTripIntent
    data object CreateSafetyShare : DriverTripIntent
    data object RecenterMap : DriverTripIntent
    data object UserMovedMap : DriverTripIntent
    data class ToggleDetailsSheet(val show: Boolean) : DriverTripIntent
    data object DismissUserMessage : DriverTripIntent
    data class UpdateDriverLocation(val sample: DriverLocationSample) : DriverTripIntent
}
