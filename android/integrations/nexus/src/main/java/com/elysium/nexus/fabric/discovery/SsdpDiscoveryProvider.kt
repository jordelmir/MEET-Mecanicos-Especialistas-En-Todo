package com.elysium.nexus.fabric.discovery

import android.util.Log
import com.elysium.nexus.fabric.canonical.Protocol
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * §8 SSDP/UPnP Discovery Provider.
 *
 * Sends SSDP M-SEARCH multicast queries and parses
 * responses to discover UPnP-enabled devices on the
 * local network.
 *
 * ## Protocol
 *
 * ```
 * M-SEARCH * HTTP/1.1
 * HOST: 239.255.255.250:1900
 * MAN: "ssdp:discover"
 * MX: 3
 * ST: ssdp:all
 * ```
 *
 * ## Known device types
 *
 * - `urn:schemas-upnp-org:device:MediaRenderer:1` — LG webOS
 * - `urn:samsung-com:device:ScreenCast:1` — Samsung Tizen
 * - `urn:roku-com:device:Player:1` — Roku
 * - `urn:schemas-sony-com:device:TV:1` — Sony Bravia
 * - General UPnP devices
 *
 * ## Response parsing
 *
 * Extracts from headers:
 * - `LOCATION` → URL with model/firmware info
 * - `SERVER` → OS/version/brand hints
 * - `USN` → Unique Service Name (stable ID)
 * - `ST` → Service type (device identification)
 */
class SsdpDiscoveryProvider : DiscoveryProvider {

    override val protocol: Protocol = Protocol.WiFi
    override val label: String = "SSDP/UPnP"
    override val isAvailable: Boolean = true

    override suspend fun discover(timeoutMs: Long): List<RawDiscoveryRecord> {
        val records = ConcurrentHashMap<String, RawDiscoveryRecord>()
        val scanDurationMs = minOf(timeoutMs, 4_000L)

        // Send M-SEARCH to ssdp:all
        sendSsdpSearch(records, "ssdp:all", scanDurationMs)

        // Also search for specific media renderers (TVs)
        sendSsdpSearch(records, "urn:schemas-upnp-org:device:MediaRenderer:1", scanDurationMs / 2)

        return records.values.toList()
    }

    private fun sendSsdpSearch(
        records: ConcurrentHashMap<String, RawDiscoveryRecord>,
        searchTarget: String,
        timeoutMs: Long
    ) {
        val ssdpRequest = buildMSearchRequest(searchTarget)

        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs.toInt()
                val group = InetAddress.getByName(SSDP_MULTICAST_ADDRESS)
                val packet = DatagramPacket(
                    ssdpRequest.toByteArray(),
                    ssdpRequest.length,
                    group,
                    SSDP_PORT
                )
                socket.send(packet)

                val rxBuffer = ByteArray(4096)
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    try {
                        val rxPacket = DatagramPacket(rxBuffer, rxBuffer.size)
                        socket.receive(rxPacket)
                        val response = String(rxPacket.data, 0, rxPacket.length)
                        val sourceIp = rxPacket.address.hostAddress ?: continue

                        parseSsdpResponse(sourceIp, response, records)
                    } catch (e: java.net.SocketTimeoutException) {
                        break
                    } catch (e: Exception) {
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SSDP search failed for $searchTarget: ${e.message}")
        }
    }

    private fun buildMSearchRequest(searchTarget: String): String {
        return buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: $SSDP_MULTICAST_ADDRESS:$SSDP_PORT\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 3\r\n")
            append("ST: $searchTarget\r\n")
            append("\r\n")
        }
    }

    private fun parseSsdpResponse(
        sourceIp: String,
        response: String,
        records: ConcurrentHashMap<String, RawDiscoveryRecord>
    ) {
        val headers = parseHeaders(response)
        val server = headers["SERVER"] ?: headers["server"] ?: ""
        val location = headers["LOCATION"] ?: headers["location"] ?: ""
        val usn = headers["USN"] ?: headers["usn"] ?: ""
        val st = headers["ST"] ?: headers["st"] ?: ""

        val lowerServer = server.lowercase()
        val lowerResponse = response.lowercase()

        val (brand, model, port) = identifyDevice(lowerServer, lowerResponse, location)
        val capabilities = inferCapabilities(lowerServer, lowerResponse)

        val stableKey = usn.ifBlank {
            "ssdp_${sourceIp.replace(".", "_")}_${st.replace(":", "_")}"
        }

        val record = RawDiscoveryRecord(
            providerProtocol = Protocol.WiFi,
            hostname = brand ?: "UPnP Device",
            ipAddress = sourceIp,
            port = port,
            manufacturer = brand,
            model = model,
            friendlyName = "$brand ${model ?: "Device"} ($sourceIp)",
            capabilities = capabilities,
            rawProperties = mapOf(
                "server" to server,
                "location" to location,
                "usn" to usn,
                "serviceType" to st,
                "discoveryProtocol" to "SSDP"
            )
        )

        records[stableKey]?.let { existing ->
            // Merge: keep the one with more data
            if (record.rawProperties.size > existing.rawProperties.size) {
                records[stableKey] = record
            }
        } ?: run {
            records[stableKey] = record
        }
    }

    private fun identifyDevice(
        lowerServer: String,
        lowerResponse: String,
        location: String
    ): Triple<String?, String?, Int?> {
        return when {
            lowerServer.contains("webos") || lowerResponse.contains("webos") ->
                Triple("LG", "webOS TV", LG_WEBOS_PORT)
            lowerServer.contains("tizen") || lowerServer.contains("samsung") ||
                lowerResponse.contains("samsung") ->
                Triple("Samsung", "Tizen TV", SAMSUNG_TIZEN_PORT)
            lowerServer.contains("roku") || lowerResponse.contains("roku") ->
                Triple("Roku", "Streaming Device", ROKU_PORT)
            lowerServer.contains("bravia") || lowerServer.contains("sony") ||
                lowerResponse.contains("bravia") ->
                Triple("Sony", "Bravia TV", null)
            lowerServer.contains("vizio") || lowerResponse.contains("vizio") ->
                Triple("Vizio", "SmartCast TV", null)
            lowerServer.contains("philips") || lowerResponse.contains("philips") ->
                Triple("Philips", "Smart TV", null)
            else -> Triple(null, null, null)
        }
    }

    private fun inferCapabilities(lowerServer: String, lowerResponse: String): Set<String> {
        val caps = mutableSetOf<String>()
        if (lowerServer.contains("renderer") || lowerResponse.contains("renderer")) {
            caps.add("media_player")
        }
        if (lowerServer.contains("tv") || lowerResponse.contains("tv")) {
            caps.add("tv")
        }
        if (lowerServer.contains("speaker") || lowerResponse.contains("speaker")) {
            caps.add("speaker")
        }
        if (caps.isEmpty()) caps.add("upnp_device")
        return caps
    }

    private fun parseHeaders(response: String): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        for (line in response.split("\r\n", "\n")) {
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim().uppercase()
                val value = line.substring(colonIndex + 1).trim()
                headers[key] = value
            }
        }
        return headers
    }

    companion object {
        private const val TAG = "SsdpDiscoveryProvider"
        private const val SSDP_MULTICAST_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val LG_WEBOS_PORT = 3000
        private const val SAMSUNG_TIZEN_PORT = 8001
        private const val ROKU_PORT = 8060
    }
}
