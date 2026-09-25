package com.elysium369.meet.automation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable

sealed interface AiAction {
    data class SwitchRole(val isDriver: Boolean) : AiAction
    data class InjectGps(val latitude: Double, val longitude: Double) : AiAction
    data class CreateRide(
        val pickupAddress: String,
        val pickupLat: Double,
        val pickupLng: Double,
        val destAddress: String,
        val destLat: Double,
        val destLng: Double,
        val priceOffer: Double,
        val currency: String = "CRC",
    ) : AiAction
    data class SelectRide(val rideId: String) : AiAction
    data class AdvanceRideStatus(val rideId: String, val newStatus: String) : AiAction
    data class TriggerObdDemo(
        val vin: String = "1HGCR2F83HA000000",
        val dtcs: List<String> = listOf("P0230", "P0300"),
    ) : AiAction
    data class SubmitOffer(
        val requestId: String,
        val counterPrice: Double,
        val estArrivalMin: Int = 10,
        val message: String? = null,
    ) : AiAction
    data class AcceptOffer(
        val requestId: String,
        val offerId: String? = null,
    ) : AiAction
    data class VoiceCommand(val command: String) : AiAction
    data class CancelRide(val rideId: String? = null) : AiAction
    object ClearStuckRides : AiAction
    object DumpState : AiAction
}

@Serializable
data class AiStateSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val currentRoute: String = "unknown",
    val isDriverMode: Boolean = false,
    val activeRideId: String? = null,
    val activeRideStatus: String? = null,
    val activeRidePickup: String? = null,
    val activeRideDest: String? = null,
    val activeRidePrice: Double? = null,
    val activeRideCurrency: String? = null,
    val activeRideDriverId: String? = null,
    val activeRidePassengerId: String? = null,
    val openRidesCount: Int = 0,
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val connectedVehicleVin: String? = null,
    val activeDtcsCount: Int = 0,
    val driverVerificationStatus: String? = null,
    val passengerVerificationStatus: String? = null,
    val walletBalanceCrc: Long = 0L,
)

object AiAutomationBridge {
    private val _navEvents = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val navEvents = _navEvents.asSharedFlow()

    private val _actionEvents = MutableSharedFlow<AiAction>(extraBufferCapacity = 32)
    val actionEvents = _actionEvents.asSharedFlow()

    @Volatile
    var currentRoute: String = "home"

    fun navigate(route: String) {
        currentRoute = route
        _navEvents.tryEmit(route)
    }

    fun dispatchAction(action: AiAction) {
        _actionEvents.tryEmit(action)
    }
}
