package com.elysium369.meet.core.obd

import android.content.Context
import com.elysium369.meet.core.transport.TransportInterface
import java.util.concurrent.ConcurrentHashMap

enum class TransportType {
    BLUETOOTH_CLASSIC,
    BLUETOOTH_LE,
    WIFI,
    SIMULATED,
}

enum class ConnectMethod {
    INSECURE_SPP,
    STANDARD_SPP,
    REFLECTION_CH1,
    REFLECTION_CH2,
    BLE_GATT,
    TCP_SOCKET,
}

/**
 * Cached physical connection profile for instant fast-path reconnections.
 */
data class AdapterLinkProfile(
    val adapterFingerprint: String,
    val transportType: TransportType,
    val preferredConnectMethod: ConnectMethod? = null,
    val preferredUuid: String? = null,
    val rfcommChannel: Int? = null,
    val preferredProtocol: String? = null,
    val preferredInitRecipe: String? = null,
    val lastSuccessfulAt: Long? = null,
    val averageConnectMs: Long? = null,
    val failureCount: Int = 0,
)

object KnownGoodAdapterStore {
    private val memoryCache = ConcurrentHashMap<String, AdapterLinkProfile>()

    fun getProfile(fingerprint: String): AdapterLinkProfile? = memoryCache[fingerprint]

    fun recordSuccess(
        fingerprint: String,
        transportType: TransportType,
        connectMethod: ConnectMethod?,
        protocol: String?,
        connectDurationMs: Long,
    ) {
        val existing = memoryCache[fingerprint]
        val updated = AdapterLinkProfile(
            adapterFingerprint = fingerprint,
            transportType = transportType,
            preferredConnectMethod = connectMethod ?: existing?.preferredConnectMethod,
            preferredProtocol = protocol ?: existing?.preferredProtocol,
            lastSuccessfulAt = System.currentTimeMillis(),
            averageConnectMs = if (existing?.averageConnectMs != null) (existing.averageConnectMs + connectDurationMs) / 2 else connectDurationMs,
            failureCount = 0,
        )
        memoryCache[fingerprint] = updated
    }

    fun recordFailure(fingerprint: String) {
        val existing = memoryCache[fingerprint] ?: return
        memoryCache[fingerprint] = existing.copy(failureCount = existing.failureCount + 1)
    }
}
