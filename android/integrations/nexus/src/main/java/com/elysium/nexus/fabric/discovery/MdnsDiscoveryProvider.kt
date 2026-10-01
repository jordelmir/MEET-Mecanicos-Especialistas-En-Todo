package com.elysium.nexus.fabric.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.elysium.nexus.fabric.canonical.Protocol
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * §8 mDNS/Zeroconf Discovery Provider.
 *
 * Uses Android's [NsdManager] to discover services
 * via mDNS (Bonjour/Avahi/Avahi-compatible).
 *
 * ## Service types discovered
 *
 * - `_webos._tcp.` — LG webOS TVs
 * - `_googlecast._tcp.` — Chromecast / Android TV
 * - `_airplay._tcp.` — Apple AirPlay devices
 * - `_roku-ecp._tcp.` — Roku devices
 * - `_sony-_audio._tcp.` — Sony Bravia
 * - `_adb._tcp.` — Android Debug Bridge (Google TV)
 * - `_elysium._tcp.` — Elysium Nexus devices
 *
 * ## Deduplication
 *
 * Each resolved service produces a [RawDiscoveryRecord]
 * with the best available stable identifier. The
 * [DiscoveryOrchestrator] merges records from multiple
 * providers.
 *
 * ## Lifecycle
 *
 * ```
 * discover(timeoutMs)
 *   → NsdManager.discoverServices()
 *   → resolve each found service
 *   → collect into Map<stableKey, RawDiscoveryRecord>
 *   → stop discovery
 *   → return records
 * ```
 */
class MdnsDiscoveryProvider(
    private val context: Context
) : DiscoveryProvider {

    override val protocol: Protocol = Protocol.WiFi
    override val label: String = "mDNS/Bonjour"
    override val isAvailable: Boolean = true

    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private val serviceTypes = listOf(
        "_webos._tcp.",
        "_googlecast._tcp.",
        "_airplay._tcp.",
        "_roku-ecp._tcp.",
        "_sony-_audio._tcp.",
        "_adb._tcp.",
        "_elysium._tcp."
    )

    override suspend fun discover(timeoutMs: Long): List<RawDiscoveryRecord> {
        val records = ConcurrentHashMap<String, RawDiscoveryRecord>()
        val discoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()

        for (serviceType in serviceTypes) {
            val listener = createDiscoveryListener(serviceType, records)
            discoveryListeners.add(listener)
            try {
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start mDNS for $serviceType: ${e.message}")
            }
        }

        // Wait for discovery period
        kotlinx.coroutines.delay(timeoutMs)

        // Stop all discovery
        for (listener in discoveryListeners) {
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                // Already stopped or not registered
            }
        }

        return records.values.toList()
    }

    private fun createDiscoveryListener(
        serviceType: String,
        records: ConcurrentHashMap<String, RawDiscoveryRecord>
    ): NsdManager.DiscoveryListener = object : NsdManager.DiscoveryListener {

        override fun onDiscoveryStarted(regType: String) {
            Log.d(TAG, "mDNS discovery started for $regType")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            Log.d(TAG, "mDNS service found: ${service.serviceName} ($serviceType)")
            resolveService(service, serviceType, records)
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            Log.d(TAG, "mDNS service lost: ${service.serviceName}")
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.d(TAG, "mDNS discovery stopped for $serviceType")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "mDNS start failed for $serviceType: error $errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "mDNS stop failed for $serviceType: error $errorCode")
        }
    }

    private fun resolveService(
        service: NsdServiceInfo,
        serviceType: String,
        records: ConcurrentHashMap<String, RawDiscoveryRecord>
    ) {
        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "mDNS resolve failed for ${serviceInfo.serviceName}: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val ip = serviceInfo.host?.hostAddress ?: return
                val port = serviceInfo.port
                val name = serviceInfo.serviceName

                val (brand, model, capabilities) = inferFromServiceType(serviceType)
                val stableKey = "mdns_${serviceType.replace(".", "_")}_${ip.replace(".", "_")}"

                val record = RawDiscoveryRecord(
                    providerProtocol = Protocol.WiFi,
                    hostname = name,
                    ipAddress = ip,
                    port = port,
                    manufacturer = brand,
                    model = model,
                    friendlyName = name,
                    capabilities = capabilities,
                    rawProperties = mapOf(
                        "serviceType" to serviceType,
                        "discoveryProtocol" to "mDNS"
                    )
                )

                records[stableKey] = record
                Log.d(TAG, "mDNS resolved: $name @ $ip:$port ($serviceType)")
            }
        })
    }

    private fun inferFromServiceType(serviceType: String): Triple<String?, String?, Set<String>> {
        return when {
            serviceType.contains("webos") -> Triple("LG", "webOS TV", setOf("tv", "media_player"))
            serviceType.contains("googlecast") -> Triple("Google", "Chromecast", setOf("media_player", "tv"))
            serviceType.contains("airplay") -> Triple("Apple", "AirPlay", setOf("media_player"))
            serviceType.contains("roku") -> Triple("Roku", "Streaming Device", setOf("media_player", "tv"))
            serviceType.contains("sony") -> Triple("Sony", "Bravia TV", setOf("tv"))
            serviceType.contains("adb") -> Triple(null, "Android TV", setOf("tv", "media_player"))
            serviceType.contains("elysium") -> Triple("Elysium", "Nexus Device", setOf("hub", "controller"))
            else -> Triple(null, null, emptySet())
        }
    }

    companion object {
        private const val TAG = "MdnsDiscoveryProvider"
    }
}
