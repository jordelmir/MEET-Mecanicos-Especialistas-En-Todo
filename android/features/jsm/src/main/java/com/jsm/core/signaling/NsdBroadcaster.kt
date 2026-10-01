package com.jsm.core.signaling

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.jsm.core.SafeLog as Log

/**
 * Registra el Honor Magic V2 en la red local LAN para emparejamiento automático cero-config.
 */
class NsdBroadcaster(private val context: Context) {

    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val SERVICE_TYPE = "_jsm_video._tcp"
    private val SERVICE_NAME = "JSM_Magic_V2_Streamer"

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
            Log.d("NsdBroadcaster", "Service registered successfully: ${NsdServiceInfo.serviceName}")
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.e("NsdBroadcaster", "Registration failed. Error: $errorCode")
        }

        override fun onServiceUnregistered(arg0: NsdServiceInfo) {
            Log.d("NsdBroadcaster", "Service unregistered.")
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.e("NsdBroadcaster", "Unregistration failed. Error: $errorCode")
        }
    }

    /**
     * Expone un socket TCP base antes de que WebRTC tome el control.
     */
    fun startBroadcasting(port: Int) {
        try {
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = SERVICE_NAME
                serviceType = SERVICE_TYPE
                this.port = port
            }
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e("NsdBroadcaster", "Failed to register NSD service", e)
        }
    }

    fun stopBroadcasting() {
        try {
            nsdManager.unregisterService(registrationListener)
        } catch (e: Exception) {
            Log.w("NsdBroadcaster", "Service allegedly already unregistered", e)
        }
    }
}
