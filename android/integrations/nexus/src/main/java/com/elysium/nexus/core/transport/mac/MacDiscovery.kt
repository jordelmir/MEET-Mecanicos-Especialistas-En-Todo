package com.elysium.nexus.core.transport.mac

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.net.InetAddress

/**
 * Elysium Nexus — Mac/PC Bonjour discovery.
 *
 * The Mac agent publishes itself as a Bonjour /
 * DNS-SD service of type `_elysium._tcp` on
 * port 7878. This class wraps Android's
 * `NsdManager` to:
 *
 *  1. Browse for `_elysium._tcp` services on
 *     the local network.
 *  2. Resolve each found service to an
 *     IP + port (and parse the TXT record for
 *     the agent's X25519 public key, model
 *     identifier, macOS version, etc.).
 *  3. Surface every `DiscoveredHost` as a
 *     Kotlin Flow so the UI can render the
 *     scan progress and the resolved hosts.
 *
 * The flow is **lifecycle-aware**: stopping the
 * collector calls `NsdManager.stopServiceDiscovery`
 * so we don't leak listeners.
 *
 * ## Permissions
 *
 *  - `INTERNET`, `ACCESS_NETWORK_STATE`,
 *    `ACCESS_WIFI_STATE` (manifest, granted).
 *  - `NEARBY_WIFI_DEVICES` (runtime, API 33+).
 *  - `ACCESS_FINE_LOCATION` (runtime, API 26-32,
 *    since Bonjour discovery on legacy Android
 *    is tied to the location permission).
 *
 * The class does **not** request permissions
 * itself; the activity is responsible.
 *
 * ## Testing
 *
 * `MacDiscovery` depends on the platform
 * `NsdManager`. The companion `FakeMacDiscovery`
 * in the test source-set provides a deterministic
 * Flow of hosts for UI tests.
 */
class MacDiscovery(
    private val context: Context
) {
    private val nsdManager: NsdManager? by lazy {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    /**
     * Browse for `_elysium._tcp` services. The
     * flow emits each resolved host as it appears
     * (deduplicated by service name). It completes
     * when the collector is cancelled.
     */
    fun discover(
        serviceType: String = SERVICE_TYPE
    ): Flow<DiscoveredHost> = callbackFlow {
        val mgr = nsdManager
        if (mgr == null) {
            Log.w(TAG, "NsdManager not available on this device")
            close()
            return@callbackFlow
        }
        val resolvedHosts = mutableMapOf<String, DiscoveredHost>()
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Resolve failed for ${serviceInfo.serviceName}: code=$errorCode")
            }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.toDiscoveredHost() ?: return
                val existing = resolvedHosts[host.name]
                if (existing?.host != host.host || existing.port != host.port) {
                    resolvedHosts[host.name] = host
                    trySend(host)
                }
            }
        }
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Discovery started: $regType")
            }
            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped: $serviceType")
            }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Start discovery failed: $serviceType code=$errorCode")
                close()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed: $serviceType code=$errorCode")
            }
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains("_elysium._tcp")) {
                    Log.d(TAG, "Found service: ${service.serviceName}")
                    try {
                        mgr.resolveService(service, resolveListener)
                    } catch (e: IllegalArgumentException) {
                        Log.w(TAG, "Resolve threw: ${e.message}")
                    }
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "Lost service: ${service.serviceName}")
                resolvedHosts.remove(service.serviceName)
            }
        }
        try {
            mgr.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "discoverServices threw: ${e.message}")
            close(e)
            return@callbackFlow
        }
        awaitClose {
            try {
                mgr.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) {
                Log.w(TAG, "stopServiceDiscovery threw: ${e.message}")
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun NsdServiceInfo.toDiscoveredHost(): DiscoveredHost? {
        val name = serviceName ?: return null
        val hostString = host?.hostAddress ?: return null
        val port = this.port
        if (port <= 0) return null
        val txt = attributes?.mapValues { it.value?.let { v -> String(v, Charsets.UTF_8) } } ?: emptyMap()
        val model = txt["model"] ?: "Mac"
        val osVersion = txt["os"] ?: "macOS"
        val publicKeyB64 = txt["pk"]
        return DiscoveredHost(
            name = name,
            host = hostString,
            port = port,
            model = model,
            osVersion = osVersion,
            publicKeyB64 = publicKeyB64
        )
    }

    companion object {
        private const val TAG = "MacDiscovery"
        /** The Bonjour service type the Mac agent publishes. */
        const val SERVICE_TYPE = "_elysium._tcp."
        /** The TCP port the Mac agent listens on. */
        const val SERVICE_PORT = 7878

        /**
         * A reasonable API-level check for whether
         * `NsdManager` is fully functional on this
         * device.
         */
        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN
    }
}

/**
 * A resolved Mac/PC on the local network.
 *
 * The `publicKeyB64` is the base64-encoded
 * X25519 public key the Mac agent advertises in
 * its TXT record. We use it to verify that the
 * agent is genuine (not a spoofed Bonjour
 * service). The key is fetched via the TXT
 * record, not via the HELLO_ACK frame, so the
 * user sees the agent's identity *before*
 * opening the TCP socket.
 */
data class DiscoveredHost(
    val name: String,
    val host: String,
    val port: Int,
    val model: String,
    val osVersion: String,
    val publicKeyB64: String?
)

/**
 * Helper to convert an `InetAddress` to a host
 * string. The `NsdServiceInfo.host` field is
 * `InetAddress?`; we use the textual form.
 */
@Suppress("unused")
private fun InetAddress.asHostString(): String = hostAddress ?: canonicalHostName
