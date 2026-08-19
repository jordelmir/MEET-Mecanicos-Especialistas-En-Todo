package com.elysium369.meet.core.obd

import android.content.Context
import android.content.SharedPreferences
import com.elysium369.meet.core.transport.TransportInterface
import java.util.concurrent.ConcurrentHashMap

enum class TransportType {
    BLUETOOTH_CLASSIC,
    BLUETOOTH_LE,
    WIFI,
    SIMULATED;

    companion object {
        fun fromStringOrNull(value: String?): TransportType? = values().firstOrNull {
            it.name.equals(value?.trim(), ignoreCase = true)
        }
    }
}

enum class ConnectMethod {
    INSECURE_SPP,
    STANDARD_SPP,
    REFLECTION_CH1,
    REFLECTION_CH2,
    BLE_GATT,
    TCP_SOCKET;

    companion object {
        fun fromStringOrNull(value: String?): ConnectMethod? = values().firstOrNull {
            it.name.equals(value?.trim(), ignoreCase = true)
        }
    }
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
    private var sharedPrefs: SharedPreferences? = null

    fun initialize(context: Context) {
        sharedPrefs = context.getSharedPreferences("meet_known_good_adapters", Context.MODE_PRIVATE)
        loadAllPersisted()
    }

    private fun loadAllPersisted() {
        val prefs = sharedPrefs ?: return
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("profile_") && value is String) {
                deserializeProfile(value)?.let { memoryCache[it.adapterFingerprint] = it }
            }
        }
    }

    private fun persistProfile(profile: AdapterLinkProfile) {
        val prefs = sharedPrefs ?: return
        val serialized = serializeProfile(profile)
        prefs.edit().putString("profile_${profile.adapterFingerprint}", serialized).apply()
    }

    private fun serializeProfile(profile: AdapterLinkProfile): String {
        return listOf(
            profile.adapterFingerprint,
            profile.transportType.name,
            profile.preferredConnectMethod?.name.orEmpty(),
            profile.preferredUuid.orEmpty(),
            profile.rfcommChannel?.toString().orEmpty(),
            profile.preferredProtocol.orEmpty(),
            profile.preferredInitRecipe.orEmpty(),
            profile.lastSuccessfulAt?.toString().orEmpty(),
            profile.averageConnectMs?.toString().orEmpty(),
            profile.failureCount.toString(),
        ).joinToString("|")
    }

    private fun deserializeProfile(raw: String): AdapterLinkProfile? {
        val parts = raw.split("|")
        if (parts.size < 10) return null
        return AdapterLinkProfile(
            adapterFingerprint = parts[0],
            transportType = TransportType.fromStringOrNull(parts[1]) ?: TransportType.BLUETOOTH_CLASSIC,
            preferredConnectMethod = ConnectMethod.fromStringOrNull(parts[2]),
            preferredUuid = parts[3].ifBlank { null },
            rfcommChannel = parts[4].toIntOrNull(),
            preferredProtocol = parts[5].ifBlank { null },
            preferredInitRecipe = parts[6].ifBlank { null },
            lastSuccessfulAt = parts[7].toLongOrNull(),
            averageConnectMs = parts[8].toLongOrNull(),
            failureCount = parts[9].toIntOrNull() ?: 0,
        )
    }

    fun getProfile(fingerprint: String): AdapterLinkProfile? = memoryCache[fingerprint]

    fun recordSuccess(
        fingerprint: String,
        transportType: TransportType,
        connectMethod: ConnectMethod?,
        protocol: String?,
        connectDurationMs: Long,
        preferredUuid: String? = null,
        rfcommChannel: Int? = null,
        preferredInitRecipe: String? = null,
    ) {
        val existing = memoryCache[fingerprint]
        val updated = AdapterLinkProfile(
            adapterFingerprint = fingerprint,
            transportType = transportType,
            preferredConnectMethod = connectMethod ?: existing?.preferredConnectMethod,
            preferredUuid = preferredUuid ?: existing?.preferredUuid,
            rfcommChannel = rfcommChannel ?: existing?.rfcommChannel,
            preferredProtocol = protocol ?: existing?.preferredProtocol,
            preferredInitRecipe = preferredInitRecipe ?: existing?.preferredInitRecipe,
            lastSuccessfulAt = System.currentTimeMillis(),
            averageConnectMs = if (existing?.averageConnectMs != null) (existing.averageConnectMs + connectDurationMs) / 2 else connectDurationMs,
            failureCount = 0,
        )
        memoryCache[fingerprint] = updated
        persistProfile(updated)
    }

    fun recordFailure(fingerprint: String) {
        val existing = memoryCache[fingerprint] ?: return
        val updated = existing.copy(failureCount = existing.failureCount + 1)
        memoryCache[fingerprint] = updated
        persistProfile(updated)
    }
}
