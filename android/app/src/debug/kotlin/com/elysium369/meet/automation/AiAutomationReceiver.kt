package com.elysium369.meet.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.elysium369.meet.BuildConfig

class AiAutomationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (!BuildConfig.DEBUG) {
            Log.w(TAG, "Rejected automation command in non-debug build")
            return
        }
        if (intent == null) return
        val action = intent.action ?: return
        Log.i(TAG, "AiAutomationReceiver received action: $action")

        when (action) {
            ACTION_NAVIGATE -> {
                val route = intent.getStringExtra(EXTRA_ROUTE) ?: intent.getStringExtra("dest") ?: "home"
                Log.i(TAG, "AiAutomation: Requesting navigation to route: $route")
                AiAutomationBridge.navigate(route)
            }
            ACTION_ACTION -> {
                val type = intent.getStringExtra(EXTRA_TYPE) ?: intent.getStringExtra("action") ?: return
                Log.i(TAG, "AiAutomation: Requesting action type: $type")
                when (type.uppercase()) {
                    "SWITCH_ROLE" -> {
                        val isDriver = intent.getBooleanExtra("isDriver", false)
                        AiAutomationBridge.dispatchAction(AiAction.SwitchRole(isDriver))
                    }
                    "INJECT_GPS" -> {
                        val lat = intent.getDoubleExtra("lat", intent.getDoubleExtra("latitude", 9.9333))
                        val lng = intent.getDoubleExtra("lng", intent.getDoubleExtra("longitude", -84.0833))
                        AiAutomationBridge.dispatchAction(AiAction.InjectGps(lat, lng))
                    }
                    "CREATE_RIDE" -> {
                        val pickup = intent.getStringExtra("pickup") ?: "San José Centro"
                        val dest = intent.getStringExtra("dest") ?: "Cartago Centro"
                        val pickupLat = intent.getDoubleExtra("pickupLat", 9.9333)
                        val pickupLng = intent.getDoubleExtra("pickupLng", -84.0833)
                        val destLat = intent.getDoubleExtra("destLat", 9.8644)
                        val destLng = intent.getDoubleExtra("destLng", -83.9194)
                        val price = intent.getDoubleExtra("price", 5000.0)
                        val currency = intent.getStringExtra("currency") ?: "CRC"
                        AiAutomationBridge.dispatchAction(
                            AiAction.CreateRide(
                                pickupAddress = pickup,
                                pickupLat = pickupLat,
                                pickupLng = pickupLng,
                                destAddress = dest,
                                destLat = destLat,
                                destLng = destLng,
                                priceOffer = price,
                                currency = currency,
                            )
                        )
                    }
                    "SELECT_RIDE" -> {
                        val rideId = intent.getStringExtra("rideId") ?: return
                        AiAutomationBridge.dispatchAction(AiAction.SelectRide(rideId))
                    }
                    "ADVANCE_RIDE_STATUS" -> {
                        val rideId = intent.getStringExtra("rideId") ?: return
                        val status = intent.getStringExtra("status") ?: return
                        AiAutomationBridge.dispatchAction(AiAction.AdvanceRideStatus(rideId, status))
                    }
                    "TRIGGER_OBD_DEMO" -> {
                        val vin = intent.getStringExtra("vin") ?: "1HGCR2F83HA000000"
                        val rawDtcs = intent.getStringExtra("dtcs") ?: "P0230,P0300"
                        val dtcs = rawDtcs.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        AiAutomationBridge.dispatchAction(AiAction.TriggerObdDemo(vin, dtcs))
                    }
                    "SUBMIT_OFFER" -> {
                        val rideId = intent.getStringExtra("rideId") ?: intent.getStringExtra("requestId") ?: return
                        val price = intent.getDoubleExtra("price", 0.0)
                        val eta = intent.getIntExtra("eta", 10)
                        val msg = intent.getStringExtra("message")
                        AiAutomationBridge.dispatchAction(AiAction.SubmitOffer(rideId, price, eta, msg))
                    }
                    "ACCEPT_OFFER" -> {
                        val rideId = intent.getStringExtra("rideId") ?: intent.getStringExtra("requestId") ?: return
                        val offerId = intent.getStringExtra("offerId")
                        AiAutomationBridge.dispatchAction(AiAction.AcceptOffer(rideId, offerId))
                    }
                    "DUMP_STATE" -> {
                        AiAutomationBridge.dispatchAction(AiAction.DumpState)
                    }
                }
            }
        }
    }

    companion object {
        const val TAG = "AiAutomation"
        const val ACTION_NAVIGATE = "com.elysium369.meet.AI_NAVIGATE"
        const val ACTION_ACTION = "com.elysium369.meet.AI_ACTION"
        const val EXTRA_ROUTE = "route"
        const val EXTRA_TYPE = "type"
    }
}
