package com.elysium.nexus.core.transport.mac

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections

/**
 * Fast Zero-Touch Host Scanner & Network Gateway Probe.
 *
 * Provides sub-second auto-discovery across:
 * 1. USB-C ADB Reverse (127.0.0.1:7878)
 * 2. USB Tethering / NCM Gateway IPs (192.168.42.x, 192.168.43.x, etc.)
 * 3. Last Known Working IP
 * 4. Parallel Wi-Fi Subnet Sweep (/24 range, 254 IPs swept in parallel)
 */
object FastHostScanner {

    private const val TAG = "FastHostScanner"
    private const val DEFAULT_PORT = 7878

    private var lastKnownWorkingIp: String? = null

    fun saveWorkingIp(ip: String) {
        if (ip != "127.0.0.1" && ip.isNotBlank()) {
            lastKnownWorkingIp = ip
        }
    }

    /**
     * Probes all available connection channels in parallel and returns
     * the first active DiscoveredHost on port 7878 within [timeoutMs].
     */
    suspend fun findFirstActiveHost(
        context: Context,
        preferredHostIp: String? = null,
        timeoutMs: Long = 2500
    ): DiscoveredHost? = withContext(Dispatchers.IO) {
        val priorityTargets = mutableListOf<String>()

        // 1. USB-C ADB Reverse localhost (127.0.0.1)
        priorityTargets.add("127.0.0.1")

        // 2. Preferred / Last known working IP
        val saved = preferredHostIp ?: lastKnownWorkingIp
        if (!saved.isNullOrBlank() && saved != "127.0.0.1" && saved !in priorityTargets) {
            priorityTargets.add(saved)
        }

        // 3. Network interface default gateways (USB tethering/NCM gateways)
        val gateways = getNetworkGateways(context)
        for (gw in gateways) {
            if (gw !in priorityTargets) {
                priorityTargets.add(gw)
            }
        }

        // Check high priority targets first with fast 350ms connect timeout
        for (ip in priorityTargets) {
            if (isPortOpen(ip, DEFAULT_PORT, 350)) {
                Log.i(TAG, "FastHostScanner: Found active host on priority IP: $ip")
                saveWorkingIp(ip)
                return@withContext DiscoveredHost(
                    name = if (ip == "127.0.0.1") "USB-C Direct" else "Mac ($ip)",
                    host = ip,
                    port = DEFAULT_PORT,
                    model = "Mac",
                    osVersion = "macOS",
                    publicKeyB64 = null
                )
            }
        }

        // 4. Parallel LAN Subnet Sweep on local Wi-Fi
        val wifiIp = getWifiIpAddress(context)
        if (!wifiIp.isNullOrBlank() && wifiIp.contains(".")) {
            val prefix = wifiIp.substringBeforeLast(".")
            Log.i(TAG, "FastHostScanner: Starting parallel subnet sweep for $prefix.1-254...")
            val scannedIp = scanSubnetParallel(prefix, DEFAULT_PORT, timeoutMs = 500)
            if (!scannedIp.isNullOrBlank()) {
                Log.i(TAG, "FastHostScanner: Subnet sweep found active host on $scannedIp")
                saveWorkingIp(scannedIp)
                return@withContext DiscoveredHost(
                    name = "Mac (Wi-Fi Auto)",
                    host = scannedIp,
                    port = DEFAULT_PORT,
                    model = "Mac",
                    osVersion = "macOS",
                    publicKeyB64 = null
                )
            }
        }

        return@withContext null
    }

    /**
     * Scans a /24 subnet (e.g. 192.168.1.1 to 192.168.1.254) in parallel using coroutines.
     */
    private suspend fun scanSubnetParallel(
        subnetPrefix: String,
        port: Int,
        timeoutMs: Int
    ): String? = withContext(Dispatchers.IO) {
        val jobs = (1..254).map { i ->
            val targetIp = "$subnetPrefix.$i"
            async {
                if (isPortOpen(targetIp, port, timeoutMs)) targetIp else null
            }
        }
        val results = jobs.awaitAll()
        return@withContext results.firstOrNull { it != null }
    }

    /**
     * Checks if a TCP port is open at [ip]:[port] with [timeoutMs] connect timeout.
     */
    fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Returns a list of default gateway IPs across active network interfaces
     * (especially USB RNDIS/NCM tethering interfaces).
     */
    private fun getNetworkGateways(context: Context): List<String> {
        val gateways = mutableListOf<String>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val hostAddr = addr.hostAddress ?: continue
                        if (hostAddr.contains(".")) {
                            val prefix = hostAddr.substringBeforeLast(".")
                            // Common gateway address on USB tethering subnets (e.g. .1 or .129 or .254)
                            gateways.add("$prefix.1")
                            gateways.add("$prefix.129")
                            gateways.add("$prefix.254")
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Error getting network gateways: ${e.message}")
        }
        return gateways.distinct()
    }

    /**
     * Retrieves the IPv4 address of the active Wi-Fi interface.
     */
    private fun getWifiIpAddress(context: Context): String? {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }
            // Fallback via NetworkInterfaces
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                if (intf.name.startsWith("wlan") || intf.name.startsWith("swlan")) {
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Error getting Wi-Fi IP: ${e.message}")
        }
        return null
    }
}
