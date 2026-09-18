package com.elysium369.meet.ride.driver

import com.elysium369.meet.ride.domain.RideCommandType
import com.elysium369.meet.ride.domain.RidePassengerPreferences
import com.elysium369.meet.ride.map.RideGeoPoint
import com.elysium369.meet.ride.map.RideRoadRoute

enum class NavigationCameraMode {
    FOLLOWING,
    USER_CONTROLLED,
}

data class DriverTripUiState(
    val rideId: String = "",
    val serverVersion: Long = 0L,
    val serverState: String = "",
    val phase: DriverTripPhase = DriverTripPhase.Unsupported("UNINITIALIZED"),
    val passengerName: String = "",
    val passengerTripCount: Int? = null,
    val passengerPreferences: RidePassengerPreferences = RidePassengerPreferences(),
    val pickup: RideGeoPoint? = null,
    val pickupAddress: String = "",
    val destination: RideGeoPoint? = null,
    val destinationAddress: String = "",
    val stops: List<RideGeoPoint> = emptyList(),
    val driverLocation: DriverLocationSample? = null,
    val route: RideRoadRoute? = null,
    val navigation: NavigationGuidance? = null,
    val remainingDistanceMeters: Long? = null,
    val remainingDurationSeconds: Long? = null,
    val acceptingFutureOffers: Boolean = true,
    val pendingCommand: RideCommandType? = null,
    val projectionFresh: Boolean = false,
    val userMessage: String? = null,
    val driverArrivedAtEpochMs: Long? = null,
    val cameraMode: NavigationCameraMode = NavigationCameraMode.FOLLOWING,
    val activeDetailsSheet: Boolean = false,
    val showPinDialog: Boolean = false,
    val showCancelDialog: Boolean = false,
    val boardingPinInput: String = "",
    val safetyShareUrl: String? = null,
    val isVoiceConnected: Boolean = false,
    val isVoiceConnecting: Boolean = false,
    val isMicrophoneMuted: Boolean = false,
    val fareMode: String = "OPEN_BID",
    val agreedFareMinor: Long = 0L,
    val currency: String = "CRC",
    val estimatedDistanceKm: Double = 0.0,
    val tripStartedAtEpochMs: Long? = null,
    val finalFareMinor: Long? = null,
    val commissionMinor: Long? = null,
)
