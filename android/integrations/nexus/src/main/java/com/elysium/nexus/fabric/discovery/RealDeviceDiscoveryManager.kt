package com.elysium.nexus.fabric.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.elysium.nexus.databases.pairing.PairedDeviceEntity
import com.elysium.nexus.databases.pairing.PairedDeviceDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Real Multi-Protocol Device Discovery Engine:
 * - mDNS / Zeroconf via NsdManager (Mac Agent, Chromecast, Apple TV)
 * - SSDP / UPnP Multicast (LG webOS, Samsung Tizen, Roku, Sony TV)
 * - Persists all discovered endpoints directly into [PairedDeviceDatabase]
 */
class RealDeviceDiscoveryManager(private val context: Context) {

    private val TAG = "RealDeviceDiscovery"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val database = PairedDeviceDatabase.getInstance(context)
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _discoveredDevices = MutableStateFlow<List<PairedDeviceEntity>>(emptyList())
    val discoveredDevices: StateFlow<List<PairedDeviceEntity>> = _discoveredDevices.asStateFlow()

    private val deviceMap = ConcurrentHashMap<String, PairedDeviceEntity>()
    private var isScanning = false

    // Target mDNS services
    private val mdnsServiceTypes = listOf(
        "_elysium._tcp.",
        "_googlecast._tcp.",
        "_airplay._tcp.",
        "_roku-ecp._tcp."
    )

    private val discoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()

    fun startDiscovery() {
        if (isScanning) return
        isScanning = true
        Log.i(TAG, "Starting Real Multi-Protocol Device Discovery (mDNS + SSDP)...")

        // 1. Start mDNS Listeners
        mdnsServiceTypes.forEach { serviceType ->
            val listener = createNsdListener(serviceType)
            discoveryListeners.add(listener)
            try {
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start mDNS discovery for $serviceType: ${e.message}")
            }
        }

        // 2. Start SSDP Multicast Scanner in Background
        scope.launch {
            scanSsdpDevices()
        }
    }

    fun stopDiscovery() {
        if (!isScanning) return
        isScanning = false
        Log.i(TAG, "Stopping Device Discovery...")

        discoveryListeners.forEach { listener ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping NSD listener: ${e.message}")
            }
        }
        discoveryListeners.clear()
    }

    private fun createNsdListener(serviceType: String) = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Log.d(TAG, "mDNS Discovery started for $regType")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            Log.d(TAG, "mDNS Service Found: ${service.serviceName} (${service.serviceType})")
            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "Resolve failed for ${serviceInfo.serviceName}: code $errorCode")
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val ip = serviceInfo.host?.hostAddress ?: return
                    val port = serviceInfo.port
                    val name = serviceInfo.serviceName

                    val (protocol, devType, brand) = when {
                        serviceType.contains("elysium") -> Triple("MAC_AGENT", "DESKTOP_MAC", "Apple")
                        serviceType.contains("googlecast") -> Triple("ANDROID_TV", "TV", "Google")
                        serviceType.contains("airplay") -> Triple("AIRPLAY", "MEDIA_PLAYER", "Apple")
                        serviceType.contains("roku") -> Triple("ROKU", "MEDIA_PLAYER", "Roku")
                        else -> Triple("GENERIC_IP", "TV", "Generic")
                    }

                    val id = "mdns_${protocol.lowercase()}_${ip.replace(".", "_")}"
                    val entity = PairedDeviceEntity(
                        id = id,
                        name = name,
                        brand = brand,
                        deviceType = devType,
                        protocolType = protocol,
                        ipAddress = ip,
                        port = port,
                        authStatus = "UNPAIRED",
                        lastSeenTimestamp = System.currentTimeMillis()
                    )

                    registerDevice(entity)
                }
            })
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            Log.d(TAG, "Service lost: ${service.serviceName}")
        }

        override fun onDiscoveryStopped(serviceType: String) {}
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
    }

    private fun scanSsdpDevices() {
        val ssdpRequest = """
            M-SEARCH * HTTP/1.1
            HOST: 239.255.255.250:1900
            MAN: "ssdp:discover"
            MX: 3
            ST: ssdp:all

        """.trimIndent().replace("\n", "\r\n")

        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 3000
                val group = InetAddress.getByName("239.255.255.250")
                val packet = DatagramPacket(
                    ssdpRequest.toByteArray(),
                    ssdpRequest.length,
                    group,
                    1900
                )
                socket.send(packet)

                val rxBuffer = ByteArray(2048)
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < 3500 && isScanning) {
                    try {
                        val rxPacket = DatagramPacket(rxBuffer, rxBuffer.size)
                        socket.receive(rxPacket)
                        val response = String(rxPacket.data, 0, rxPacket.length)
                        val ip = rxPacket.address.hostAddress ?: continue

                        parseSsdpResponse(ip, response)
                    } catch (e: Exception) {
                        // Socket timeout expected after scan period
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SSDP Multicast scan failed: ${e.message}")
        }
    }

    private fun parseSsdpResponse(ip: String, response: String) {
        var protocol = "GENERIC_IP"
        var brand = "Smart TV"
        var name = "Smart TV ($ip)"

        val lower = response.lowercase()
        when {
            lower.contains("webos") || lower.contains("lg") -> {
                protocol = "LG_WEBOS"
                brand = "LG"
                name = "LG webOS Smart TV"
            }
            lower.contains("tizen") || lower.contains("samsung") -> {
                protocol = "SAMSUNG_TIZEN"
                brand = "Samsung"
                name = "Samsung Tizen Smart TV"
            }
            lower.contains("roku") -> {
                protocol = "ROKU"
                brand = "Roku"
                name = "Roku Streaming Device"
            }
            lower.contains("bravia") || lower.contains("sony") -> {
                protocol = "SONY_BRAVIA"
                brand = "Sony"
                name = "Sony Bravia Smart TV"
            }
        }

        val id = "ssdp_${protocol.lowercase()}_${ip.replace(".", "_")}"
        val entity = PairedDeviceEntity(
            id = id,
            name = name,
            brand = brand,
            deviceType = "TV",
            protocolType = protocol,
            ipAddress = ip,
            port = if (protocol == "LG_WEBOS") 3000 else if (protocol == "SAMSUNG_TIZEN") 8001 else 8060,
            authStatus = "UNPAIRED",
            lastSeenTimestamp = System.currentTimeMillis()
        )

        registerDevice(entity)
    }

    private fun registerDevice(device: PairedDeviceEntity) {
        deviceMap[device.id] = device
        val list = deviceMap.values.toList()
        _discoveredDevices.value = list

        scope.launch {
            try {
                database.pairedDeviceDao().insertOrUpdate(device)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist discovered device to Room DB: ${e.message}")
            }
        }
    }
}
