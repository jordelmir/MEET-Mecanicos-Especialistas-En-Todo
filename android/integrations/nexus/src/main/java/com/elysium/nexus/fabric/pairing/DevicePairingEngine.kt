package com.elysium.nexus.fabric.pairing

import android.content.Context
import com.elysium.nexus.databases.pairing.PairedDeviceEntity
import com.elysium.nexus.databases.pairing.PairedDeviceDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

sealed class PairingResult {
    data class Success(val device: PairedDeviceEntity, val pairingToken: String) : PairingResult()
    data class PinRequired(val device: PairedDeviceEntity, val pinMessage: String) : PairingResult()
    data class Error(val message: String) : PairingResult()
}

/**
 * Persists only verified pairing outcomes. Unsupported protocols fail closed
 * until their device-specific handshake supplies a real credential.
 */
class DevicePairingEngine(private val context: Context) {

    private val database = PairedDeviceDatabase.getInstance(context)

    suspend fun pairDevice(device: PairedDeviceEntity, userPin: String? = null): PairingResult = withContext(Dispatchers.IO) {
        val result = when (device.protocolType) {
            "ROKU" -> pairRokuDevice(device)
            "MAC_AGENT" -> if (userPin.isNullOrBlank()) PairingResult.PinRequired(device, "Ingresa el PIN mostrado en tu Mac")
                else PairingResult.Error("El Mac no confirmó el PIN; emparejamiento pendiente")
            "LG_WEBOS", "SAMSUNG_TIZEN" -> PairingResult.Error("Emparejamiento pendiente de confirmación del televisor")
            "INFRARED" -> PairingResult.Error("Se requiere un emisor infrarrojo verificado")
            else -> PairingResult.Error("Este protocolo aún no confirmó un enlace")
        }

        if (result is PairingResult.Success) {
            val updated = result.device.copy(
                authStatus = "PAIRED",
                pairingToken = result.pairingToken.takeIf(String::isNotBlank),
                lastSeenTimestamp = System.currentTimeMillis()
            )
            database.pairedDeviceDao().insertOrUpdate(updated)
        }

        return@withContext result
    }

    private fun pairRokuDevice(device: PairedDeviceEntity): PairingResult {
        val ip = device.ipAddress ?: return PairingResult.Error("No IP address provided for Roku device")
        val urlStr = "http://$ip:8060/query/device-info"

        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"

            if (conn.responseCode == 200) {
                // Roku ECP does not exchange a pairing token. HTTP 200 proves
                // reachability; commands still require a fresh device response.
                PairingResult.Success(device, "")
            } else {
                PairingResult.Error("Roku device returned HTTP status ${conn.responseCode}")
            }
        } catch (e: Exception) {
            PairingResult.Error("Failed to reach Roku device at $ip: ${e.message}")
        }
    }

}
