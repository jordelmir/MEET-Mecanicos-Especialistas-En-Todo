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
) {
    fun toKnownGoodLinkProfile(vehicleBindingId: String? = null): KnownGoodLinkProfile {
        return KnownGoodLinkProfile(
            adapterFingerprint = adapterFingerprint,
            vehicleBindingId = vehicleBindingId,
            transportType = transportType,
            connectMethod = preferredConnectMethod,
            protocol = preferredProtocol,
            initCommands = preferredInitRecipe?.split(";")?.filter { it.isNotBlank() } ?: emptyList(),
            lastSuccessfulAt = lastSuccessfulAt,
            transportReadyP50 = averageConnectMs,
            failureCount = failureCount,
        )
    }
}

/**
 * Unified known connection profile binding adapter fingerprint + vehicle identity.
 */
data class KnownGoodLinkProfile(
    val adapterFingerprint: String,
    val vehicleBindingId: String? = null,
    val transportType: TransportType = TransportType.BLUETOOTH_CLASSIC,
    val connectMethod: ConnectMethod? = null,
    val elmIdentity: String? = null,
    val protocol: String? = null,
    val requestHeader: String? = null,
    val initCommands: List<String> = emptyList(),
    val baseDelayMs: Long = 0L,
    val lastSuccessfulAt: Long? = null,
    val transportReadyP50: Long? = null,
    val ecuReadyP50: Long? = null,
    val failureCount: Int = 0,
)

object KnownGoodAdapterStore {
    private val memoryCache = ConcurrentHashMap<String, AdapterLinkProfile>()
    private val memoryLinkCache = ConcurrentHashMap<String, KnownGoodLinkProfile>()
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
            } else if (key.startsWith("link_profile_") && value is String) {
                deserializeLinkProfile(value)?.let {
                    val cacheKey = if (it.vehicleBindingId != null) "${it.adapterFingerprint}#${it.vehicleBindingId}" else it.adapterFingerprint
                    memoryLinkCache[cacheKey] = it
                }
            }
        }
    }

    private fun persistProfile(profile: AdapterLinkProfile) {
        val prefs = sharedPrefs ?: return
        val serialized = serializeProfile(profile)
        prefs.edit().putString("profile_${profile.adapterFingerprint}", serialized).apply()
    }

    private fun persistLinkProfile(profile: KnownGoodLinkProfile) {
        val prefs = sharedPrefs ?: return
        val serialized = serializeLinkProfile(profile)
        val prefKey = if (profile.vehicleBindingId != null) "link_profile_${profile.adapterFingerprint}#${profile.vehicleBindingId}" else "link_profile_${profile.adapterFingerprint}"
        prefs.edit().putString(prefKey, serialized).apply()
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

    private fun serializeLinkProfile(profile: KnownGoodLinkProfile): String {
        return listOf(
            profile.adapterFingerprint,
            profile.vehicleBindingId.orEmpty(),
            profile.transportType.name,
            profile.connectMethod?.name.orEmpty(),
            profile.elmIdentity.orEmpty(),
            profile.protocol.orEmpty(),
            profile.requestHeader.orEmpty(),
            profile.initCommands.joinToString(";"),
            profile.baseDelayMs.toString(),
            profile.lastSuccessfulAt?.toString().orEmpty(),
            profile.transportReadyP50?.toString().orEmpty(),
            profile.ecuReadyP50?.toString().orEmpty(),
            profile.failureCount.toString(),
        ).joinToString("|")
    }

    private fun deserializeLinkProfile(raw: String): KnownGoodLinkProfile? {
        val parts = raw.split("|")
        if (parts.size < 13) return null
        return KnownGoodLinkProfile(
            adapterFingerprint = parts[0],
            vehicleBindingId = parts[1].ifBlank { null },
            transportType = TransportType.fromStringOrNull(parts[2]) ?: TransportType.BLUETOOTH_CLASSIC,
            connectMethod = ConnectMethod.fromStringOrNull(parts[3]),
            elmIdentity = parts[4].ifBlank { null },
            protocol = parts[5].ifBlank { null },
            requestHeader = parts[6].ifBlank { null },
            initCommands = parts[7].split(";").filter { it.isNotBlank() },
            baseDelayMs = parts[8].toLongOrNull() ?: 0L,
            lastSuccessfulAt = parts[9].toLongOrNull(),
            transportReadyP50 = parts[10].toLongOrNull(),
            ecuReadyP50 = parts[11].toLongOrNull(),
            failureCount = parts[12].toIntOrNull() ?: 0,
        )
    }

    fun getProfile(fingerprint: String): AdapterLinkProfile? = memoryCache[fingerprint]

    fun getLinkProfile(adapterFingerprint: String, vehicleBindingId: String? = null): KnownGoodLinkProfile? {
        if (vehicleBindingId != null) {
            val pairKey = "${adapterFingerprint}#${vehicleBindingId}"
            val pairProfile = memoryLinkCache[pairKey]
            if (pairProfile != null) return pairProfile
        }
        return memoryLinkCache[adapterFingerprint] ?: memoryCache[adapterFingerprint]?.toKnownGoodLinkProfile(vehicleBindingId)
    }

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

        // Also update the general link profile
        recordLinkSuccess(
            adapterFingerprint = fingerprint,
            vehicleBindingId = null,
            transportType = transportType,
            connectMethod = connectMethod,
            protocol = protocol,
            initCommands = preferredInitRecipe?.split(";")?.filter { it.isNotBlank() } ?: emptyList(),
            transportDurationMs = connectDurationMs,
        )
    }

    fun recordLinkSuccess(
        adapterFingerprint: String,
        vehicleBindingId: String?,
        transportType: TransportType,
        connectMethod: ConnectMethod?,
        elmIdentity: String? = null,
        protocol: String? = null,
        requestHeader: String? = null,
        initCommands: List<String> = emptyList(),
        baseDelayMs: Long = 0L,
        transportDurationMs: Long? = null,
        ecuDurationMs: Long? = null,
    ) {
        val cacheKey = if (vehicleBindingId != null) "${adapterFingerprint}#${vehicleBindingId}" else adapterFingerprint
        val existing = memoryLinkCache[cacheKey]
        val updated = KnownGoodLinkProfile(
            adapterFingerprint = adapterFingerprint,
            vehicleBindingId = vehicleBindingId,
            transportType = transportType,
            connectMethod = connectMethod ?: existing?.connectMethod,
            elmIdentity = elmIdentity ?: existing?.elmIdentity,
            protocol = protocol ?: existing?.protocol,
            requestHeader = requestHeader ?: existing?.requestHeader,
            initCommands = if (initCommands.isNotEmpty()) initCommands else existing?.initCommands ?: emptyList(),
            baseDelayMs = if (baseDelayMs > 0) baseDelayMs else existing?.baseDelayMs ?: 0L,
            lastSuccessfulAt = System.currentTimeMillis(),
            transportReadyP50 = if (existing?.transportReadyP50 != null && transportDurationMs != null) {
                (existing.transportReadyP50 + transportDurationMs) / 2
            } else transportDurationMs ?: existing?.transportReadyP50,
            ecuReadyP50 = if (existing?.ecuReadyP50 != null && ecuDurationMs != null) {
                (existing.ecuReadyP50 + ecuDurationMs) / 2
            } else ecuDurationMs ?: existing?.ecuReadyP50,
            failureCount = 0,
        )
        memoryLinkCache[cacheKey] = updated
        persistLinkProfile(updated)
    }

    fun recordFailure(fingerprint: String) {
        val existing = memoryCache[fingerprint]
        if (existing != null) {
            val updated = existing.copy(failureCount = existing.failureCount + 1)
            memoryCache[fingerprint] = updated
            persistProfile(updated)
        }
        val existingLink = memoryLinkCache[fingerprint]
        if (existingLink != null) {
            val updatedLink = existingLink.copy(failureCount = existingLink.failureCount + 1)
            memoryLinkCache[fingerprint] = updatedLink
            persistLinkProfile(updatedLink)
        }
    }
}
