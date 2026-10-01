package com.elysium.nexus.fabric.discovery

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceTwin
import com.elysium.nexus.fabric.canonical.Protocol
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * §8 Universal LAN Discovery.
 *
 * The [DiscoveryOrchestrator] fuses results from
 * multiple discovery providers into a single,
 * deduplicated view of the network. Each provider
 * discovers devices on its protocol; the orchestrator
 * merges, deduplicates, and produces a unified
 * [DeviceTwin] per physical device.
 *
 * ## Providers
 *
 * - mDNS (Bonjour/Avahi)
 * - SSDP (UPnP)
 * - DIAL (YouTube, Netflix)
 * - Matter commissioning
 * - Bluetooth/BLE scanning
 * - USB enumeration
 * - Previously paired devices
 * - Elysium Link discovery
 *
 * ## Deduplication
 *
 * A Samsung TV might appear via:
 * - mDNS: `Samsung TV [LG]`
 * - SSDP: `Samsung Smart TV`
 * - WiFi identity: `Samsung QE65QN85B`
 *
 * The orchestrator merges these into ONE device
 * using stable identifiers (UPnP UDN, MAC, serial).
 *
 * ## Flow
 *
 * ```
 * DiscoveryOrchestrator.discover()
 *     → Provider1.discover() → Set<RawDiscoveryRecord>
 *     → Provider2.discover() → Set<RawDiscoveryRecord>
 *     → Merge by stable ID
 *     → Build DeviceTwin per device
 *     → Deduplicate
 *     → Emit Flow<DiscoveryResult>
 * ```
 */
class DiscoveryOrchestrator(
    private val providers: List<DiscoveryProvider>,
    private val merger: DiscoveryMerger = DefaultDiscoveryMerger(),
    // V0.6.2 PR4 Phase 17: identity merge bridge (optional — null disables identity persistence)
    private val identityBridge: DiscoveryIdentityBridge? = null
) {

    /**
     * Run a full discovery scan across all providers.
     * Returns deduplicated devices as a [Flow].
     */
    fun discover(
        timeoutMs: Long = 10_000L
    ): Flow<DiscoveryResult> = kotlinx.coroutines.flow.flow {
        val allRecords = mutableMapOf<String, MutableList<RawDiscoveryRecord>>()

        for (provider in providers) {
            try {
                val records = provider.discover(timeoutMs)
                for (record in records) {
                    val key = merger.stableKey(record)
                    allRecords.getOrPut(key) { mutableListOf() }.add(record)
                }
            } catch (e: Exception) {
                // Provider failed — continue with others
            }
        }

        // Merge records per device
        val merged = allRecords.map { (key, records) ->
            merger.merge(key, records)
        }

        // V0.6.2 PR4 Phase 17: feed observations through identity engine
        // §9: never produce different identities per protocol for the same device
        if (identityBridge != null) {
            for ((key, records) in allRecords) {
                try {
                    identityBridge.processBatch(key, records)
                } catch (e: Exception) {
                    // Identity merge failure must not block discovery
                }
            }
        }

        for (device in merged) {
            emit(DiscoveryResult.DeviceFound(device))
        }
        emit(DiscoveryResult.Complete(merged.size))
    }

    /**
     * Subscribe to real-time discovery events.
     * Providers push new/removed devices.
     */
    fun observe(): Flow<DiscoveryEvent> = kotlinx.coroutines.flow.callbackFlow {
        val knownDevices = mutableMapOf<String, DeviceTwin>()

        // Run initial scan
        discover().collect { result ->
            when (result) {
                is DiscoveryResult.DeviceFound -> {
                    val key = result.twin.deviceId.value
                    val existing = knownDevices[key]
                    if (existing == null) {
                        knownDevices[key] = result.twin
                        trySend(DiscoveryEvent.DeviceAppeared(result.twin))
                    } else {
                        knownDevices[key] = result.twin
                        if (existing != result.twin) {
                            trySend(DiscoveryEvent.DeviceUpdated(result.twin))
                        }
                    }
                }
                is DiscoveryResult.Complete -> {
                    // Check for disappeared devices
                    val currentIds = knownDevices.keys.toSet()
                    val newIds = mutableSetOf<String>()

                    // Re-scan to detect new devices
                    for (provider in providers) {
                        try {
                            val records = provider.discover(2_000L)
                            for (record in records) {
                                val mergeKey = merger.stableKey(record)
                                newIds.add(mergeKey)
                            }
                        } catch (_: Exception) {}
                    }

                    // Detect disappeared devices
                    for (oldId in currentIds) {
                        if (oldId !in newIds) {
                            val removed = knownDevices.remove(oldId)
                            if (removed != null) {
                                trySend(DiscoveryEvent.DeviceDisappeared(removed.deviceId))
                            }
                        }
                    }
                }
            }
        }

        awaitClose { /* no-op */ }
    }
}

/**
 * A discovery provider discovers devices on
 * a specific protocol.
 */
interface DiscoveryProvider {
    /** The protocol this provider discovers. */
    val protocol: Protocol

    /** Human-readable label. */
    val label: String

    /**
     * Discover devices. Returns raw records that
     * may be partial (e.g. only IP + hostname from
     * mDNS, no model info).
     */
    suspend fun discover(timeoutMs: Long): List<RawDiscoveryRecord>

    /**
     * Whether this provider is available on
     * the current platform.
     */
    val isAvailable: Boolean
}

/**
 * A raw discovery record from a single provider.
 * May be incomplete; the merger fills gaps.
 */
data class RawDiscoveryRecord(
    val providerProtocol: Protocol,
    val hostname: String? = null,
    val ipAddress: String? = null,
    val port: Int? = null,
    val macAddress: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val modelNumber: String? = null,
    val serialNumber: String? = null,
    val upnpUdn: String? = null,
    val matterNodeId: String? = null,
    val bluetoothAddress: String? = null,
    val friendlyName: String? = null,
    val capabilities: Set<String> = emptySet(),
    val rawProperties: Map<String, String> = emptyMap(),
    val discoveredAtMs: Long = System.currentTimeMillis()
) {
    /**
     * Best available stable identifier for deduplication.
     */
    val stableId: String?
        get() = serialNumber ?: upnpUdn ?: matterNodeId ?: macAddress ?: bluetoothAddress

    /**
     * Best available display name.
     */
    val displayName: String
        get() = friendlyName ?: hostname ?: model ?: ipAddress ?: "Unknown Device"
}

/**
 * Merging result for a deduplicated device.
 */
sealed class DiscoveryResult {
    data class DeviceFound(val twin: DeviceTwin) : DiscoveryResult()
    data class Complete(val deviceCount: Int) : DiscoveryResult()
}

/**
 * Real-time discovery events.
 */
sealed class DiscoveryEvent {
    data class DeviceAppeared(val twin: DeviceTwin) : DiscoveryEvent()
    data class DeviceDisappeared(val deviceId: DeviceId) : DiscoveryEvent()
    data class DeviceUpdated(val twin: DeviceTwin) : DiscoveryEvent()
}

/**
 * Interface for merging multiple raw records
 * into a single [DeviceTwin].
 */
interface DiscoveryMerger {
    fun stableKey(record: RawDiscoveryRecord): String
    fun merge(key: String, records: List<RawDiscoveryRecord>): DeviceTwin
}

/**
 * Default merger: uses stable ID for dedup,
 * merges properties from all records.
 */
class DefaultDiscoveryMerger : DiscoveryMerger {

    override fun stableKey(record: RawDiscoveryRecord): String {
        return record.stableId
            ?: "${record.ipAddress ?: "unknown"}_${record.hostname ?: "unknown"}"
    }

    override fun merge(key: String, records: List<RawDiscoveryRecord>): DeviceTwin {
        val primary = records.first()
        val allProperties = records.flatMap { it.rawProperties.entries }
            .associate { it.key to it.value }

        return DeviceTwin(
            deviceId = DeviceId(key),
            manufacturer = records.mapNotNull { it.manufacturer }.firstOrNull(),
            model = records.mapNotNull { it.model }.firstOrNull(),
            deviceType = inferDeviceType(records),
            capabilities = records.flatMap { it.capabilities }.map {
                com.elysium.nexus.fabric.canonical.Capability.Custom
            }.toSet().ifEmpty { setOf(com.elysium.nexus.fabric.canonical.Capability.OnOff) },
            protocolBindings = buildBindings(records),
            lastSeenNs = System.nanoTime(),
            label = records.map { it.displayName }.firstOrNull { it != "Unknown Device" } ?: key
        )
    }

    private fun inferDeviceType(records: List<RawDiscoveryRecord>): com.elysium.nexus.fabric.canonical.DeviceType {
        val allCaps = records.flatMap { it.capabilities }.map { it.lowercase() }
        return when {
            allCaps.any { it.contains("tv") || it.contains("television") } ->
                com.elysium.nexus.fabric.canonical.DeviceType.Television
            allCaps.any { it.contains("speaker") || it.contains("audio") } ->
                com.elysium.nexus.fabric.canonical.DeviceType.Speaker
            allCaps.any { it.contains("light") } ->
                com.elysium.nexus.fabric.canonical.DeviceType.Light
            allCaps.any { it.contains("thermostat") || it.contains("hvac") } ->
                com.elysium.nexus.fabric.canonical.DeviceType.Thermostat
            allCaps.any { it.contains("switch") || it.contains("outlet") } ->
                com.elysium.nexus.fabric.canonical.DeviceType.Switch
            else -> com.elysium.nexus.fabric.canonical.DeviceType.Unknown
        }
    }

    private fun buildBindings(records: List<RawDiscoveryRecord>): Set<com.elysium.nexus.fabric.canonical.ProtocolBinding> {
        return records.mapNotNull { record ->
            val ip = record.ipAddress ?: return@mapNotNull null
            val protocol = record.providerProtocol
            val endpoint = "$protocol://${ip}${record.port?.let { ":$it" } ?: ""}"
            com.elysium.nexus.fabric.canonical.ProtocolBinding(
                protocol = protocol,
                endpoint = endpoint,
                capabilities = setOf(com.elysium.nexus.fabric.canonical.Capability.OnOff)
            )
        }.toSet()
    }
}
