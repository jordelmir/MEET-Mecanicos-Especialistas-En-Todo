package com.elysium369.meet.ride.notification

import android.content.Context
import android.util.Log

object MeetPushDispatcher {
    private const val TAG = "ElysiumPushDispatcher"

    /**
     * Dispatches high-priority push events to the user's phone notification tray
     * with aggressive vibration, custom sound, and head-up display.
     */
    fun handlePayload(context: Context, ownerUserId: String?, data: Map<String, String>) {
        val coordinator = RideNotificationCoordinator(context, ownerUserId)
        val eventType = data["type"] ?: data["event"] ?: return
        val tripId = data["trip_id"] ?: data["request_id"] ?: ""

        Log.i(TAG, "Dispatching notification event: $eventType for trip $tripId")

        when (eventType.uppercase()) {
            "NEW_RIDE", "RIDE_REQUEST", "DISPATCH" -> {
                val pickup = data["pickup_address"] ?: "Punto de recogida cercano"
                val dest = data["dest_address"] ?: "Destino final"
                val price = data["price_crc"]?.toDoubleOrNull() ?: 0.0
                coordinator.notifyIncomingRideDispatch(tripId, pickup, dest, price)
            }
            "OFFER_ACCEPTED", "TRIP_ACCEPTED", "ASSIGNED" -> {
                val passenger = data["passenger_name"] ?: "El pasajero"
                val price = data["price_crc"]?.toDoubleOrNull() ?: 0.0
                coordinator.notifyOfferAccepted(tripId, passenger, price)
            }
            "CHAT_MESSAGE", "NEW_MESSAGE" -> {
                val sender = data["sender_name"] ?: "Usuario"
                val message = data["message"] ?: "Nuevo mensaje de viaje"
                coordinator.notifyNewChatMessage(tripId, sender, message)
            }
            "DESTINATION_ETA" -> {
                val etaSeconds = data["eta_seconds"]?.toLongOrNull() ?: 420L
                coordinator.notifyDestinationEtaSevenMinutes(tripId, etaSeconds)
            }
        }
    }
}
