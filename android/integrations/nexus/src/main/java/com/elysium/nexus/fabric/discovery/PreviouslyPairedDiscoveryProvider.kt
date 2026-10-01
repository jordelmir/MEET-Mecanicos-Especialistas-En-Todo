package com.elysium.nexus.fabric.discovery

import com.elysium.nexus.databases.pairing.PairedDeviceDao
import com.elysium.nexus.databases.pairing.PairedDeviceEntity
import com.elysium.nexus.fabric.canonical.Protocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * §8 Previously Paired Discovery Provider.
 *
 * Surfaces devices that were previously discovered
 * or paired from the Room database. This ensures
 * that known devices appear in discovery even when
 * mDNS/SSDP scans miss them (e.g., sleeping devices,
 * different subnet, firewall blocking multicast).
 *
 * ## Strategy
 *
 * On discovery, this provider:
 * 1. Queries all previously paired/discovered devices
 * 2. Filters to those seen within [maxAgeMs]
 * 3. Converts [PairedDeviceEntity] to [RawDiscoveryRecord]
 * 4. Marks them with `PreviouslyPaired` evidence
 *
 * ## Staleness
 *
 * Devices not seen within [maxAgeMs] are excluded
 * from active discovery but remain in the database
 * for historical reference.
 */
class PreviouslyPairedDiscoveryProvider(
    private val dao: PairedDeviceDao,
    private val maxAgeMs: Long = 7 * 24 * 60 * 60 * 1000L // 7 days
) : DiscoveryProvider {

    override val protocol: Protocol = Protocol.WiFi
    override val label: String = "Previously Paired"
    override val isAvailable: Boolean = true

    override suspend fun discover(timeoutMs: Long): List<RawDiscoveryRecord> {
        val cutoffTime = System.currentTimeMillis() - maxAgeMs

        return withContext(Dispatchers.IO) {
            try {
                // Room Flow emits the full list on first collection.
                // first() suspends until the first emission, then returns.
                val allDevices = dao.getAllDevicesFlow().first()
                allDevices
                    .filter { it.lastSeenTimestamp >= cutoffTime }
                    .map { entityToRecord(it) }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * Convert a [PairedDeviceEntity] to a [RawDiscoveryRecord].
     */
    fun entityToRecord(entity: PairedDeviceEntity): RawDiscoveryRecord {
        val brand = inferBrand(entity.brand, entity.protocolType)
        val capabilities = inferCapabilities(entity.deviceType)

        return RawDiscoveryRecord(
            providerProtocol = mapProtocol(entity.protocolType),
            hostname = entity.name,
            ipAddress = entity.ipAddress,
            port = entity.port,
            macAddress = entity.macAddress,
            manufacturer = brand,
            model = entity.deviceType,
            friendlyName = entity.customName ?: entity.name,
            capabilities = capabilities,
            rawProperties = mapOf(
                "entityId" to entity.id,
                "protocolType" to entity.protocolType,
                "authStatus" to entity.authStatus,
                "hasClientKey" to (entity.clientKey != null).toString(),
                "discoveryProtocol" to "PreviouslyPaired",
                "lastSeen" to entity.lastSeenTimestamp.toString()
            ),
            discoveredAtMs = entity.lastSeenTimestamp
        )
    }

    private fun mapProtocol(protocolType: String): Protocol {
        return when (protocolType) {
            "LG_WEBOS" -> Protocol.VendorWebSocket
            "SAMSUNG_TIZEN" -> Protocol.VendorWebSocket
            "ROKU" -> Protocol.VendorRest
            "SONY_BRAVIA" -> Protocol.VendorRest
            "ANDROID_TV" -> Protocol.VendorRest
            "MAC_AGENT" -> Protocol.ElysiumLink
            "INFRARED" -> Protocol.DirectIr
            "BLE_HID" -> Protocol.Ble
            else -> Protocol.WiFi
        }
    }

    private fun inferBrand(brand: String, protocolType: String): String {
        if (brand.isNotBlank() && brand != "Generic" && brand != "Smart TV") return brand
        return when (protocolType) {
            "LG_WEBOS" -> "LG"
            "SAMSUNG_TIZEN" -> "Samsung"
            "SONY_BRAVIA" -> "Sony"
            "ROKU" -> "Roku"
            "ANDROID_TV" -> "Google"
            "MAC_AGENT" -> "Apple"
            else -> "Unknown"
        }
    }

    private fun inferCapabilities(deviceType: String): Set<String> {
        return when (deviceType.uppercase()) {
            "TV" -> setOf("tv")
            "MEDIA_PLAYER" -> setOf("media_player")
            "SPEAKER" -> setOf("speaker")
            "DESKTOP_MAC", "DESKTOP_WIN" -> setOf("computer")
            "GAMEPAD_HOST" -> setOf("controller")
            "HUB" -> setOf("hub")
            else -> setOf("device")
        }
    }
}
