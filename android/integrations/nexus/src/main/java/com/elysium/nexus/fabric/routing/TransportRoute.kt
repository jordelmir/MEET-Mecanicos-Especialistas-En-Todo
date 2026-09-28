package com.elysium.nexus.fabric.routing

import com.elysium.nexus.fabric.adapter.DeviceAdapter
import com.elysium.nexus.fabric.adapter.AdapterState
import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.DeviceTwin
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.canonical.ProtocolBinding
import com.elysium.nexus.fabric.canonical.UniversalAction

/**
 * A scored, prioritised transport route for delivering
 * a [UniversalAction] to a target device.
 *
 * The [RouteNegotiator] produces a ranked list of
 * [TransportRoute]s; the [ActionDispatcher] tries
 * them in order until one succeeds.
 *
 * ## Priority order (§2 hierarchy)
 *
 * 1. DirectIr (lowest latency, no pairing)
 * 2. HidOverBle / HidOverUsb (gamepad/keyboard)
 * 3. HdmiCec (TV-specific)
 * 4. Matter / Thread (standard IoT)
 * 5. Ble / WiFi (generic)
 * 6. VendorRest / VendorWebSocket (cloud-backed)
 * 7. ElysiumLink (proprietary)
 */
data class TransportRoute(
    /** The protocol this route uses. */
    val protocol: Protocol,
    /** The adapter instance that can execute the route. */
    val adapter: DeviceAdapter,
    /** The protocol binding on the target device. */
    val binding: ProtocolBinding,
    /** Priority rank (lower = better). */
    val priority: Int,
    /** Estimated round-trip latency in milliseconds. */
    val latencyEstimateMs: Long,
    /** Whether the route is currently available. */
    val isAvailable: Boolean
) {
    init {
        require(priority >= 0) { "Route priority must be non-negative (got $priority)." }
        require(latencyEstimateMs >= 0) { "Latency estimate must be non-negative (got $latencyEstimateMs)." }
    }
}

/**
 * Route negotiator: given a [UniversalAction] and
 * a [DeviceTwin], produces a ranked list of
 * [TransportRoute]s.
 *
 * The negotiator filters routes by:
 * 1. The action's required [Capability] must be in
 *    the binding's capabilities.
 * 2. The adapter must be in [AdapterState.Active].
 * 3. Routes are sorted by priority (ascending).
 */
class RouteNegotiator(
    private val adapters: List<DeviceAdapter>
) {

    /**
     * Negotiate routes for a given action and target device.
     *
     * @return ranked list of available routes (best first),
     * or empty if no route can deliver the action.
     */
    fun negotiate(
        action: UniversalAction,
        target: DeviceTwin
    ): List<TransportRoute> {
        val requiredCapability = action.requiredCapability()
        val routes = mutableListOf<TransportRoute>()

        for (binding in target.protocolBindings) {
            // Skip bindings that don't support the required capability
            if (requiredCapability !in binding.capabilities) continue

            // Find an adapter that speaks this protocol
            val adapter = adapters.firstOrNull { it.protocol == binding.protocol }
                ?: continue

            // Only active adapters can deliver
            val adapterActive = adapter.state.value == AdapterState.Active
            val priority = protocolPriority(binding.protocol)
            val latency = protocolLatencyEstimate(binding.protocol)

            routes.add(
                TransportRoute(
                    protocol = binding.protocol,
                    adapter = adapter,
                    binding = binding,
                    priority = priority,
                    latencyEstimateMs = latency,
                    isAvailable = adapterActive
                )
            )
        }

        // Sort: available first, then by priority, then by latency
        return routes.sortedWith(
            compareByDescending<TransportRoute> { it.isAvailable }
                .thenBy { it.priority }
                .thenBy { it.latencyEstimateMs }
        )
    }

    companion object {
        /**
         * §2 protocol priority (lower = better).
         * Local > standard > vendor > cloud.
         */
        fun protocolPriority(protocol: Protocol): Int = when (protocol) {
            Protocol.DirectIr -> 10
            Protocol.HubIr -> 15
            Protocol.HidOverUsb -> 20
            Protocol.HidOverBle -> 25
            Protocol.HidOverBluetooth -> 30
            Protocol.HdmiCec -> 35
            Protocol.Matter -> 40
            Protocol.Thread -> 45
            Protocol.Zigbee -> 50
            Protocol.ZWave -> 55
            Protocol.ZWaveLongRange -> 56
            Protocol.Ble -> 60
            Protocol.WiFi -> 65
            Protocol.Ethernet -> 66
            Protocol.Mqtt -> 70
            Protocol.Onvif -> 75
            Protocol.Rtsp -> 76
            Protocol.Rtsps -> 77
            Protocol.WebRtc -> 78
            Protocol.VendorRest -> 80
            Protocol.VendorWebSocket -> 85
            Protocol.ElysiumLink -> 90
            Protocol.Unknown -> 999
        }

        /**
         * Rough latency estimate per protocol in milliseconds.
         * Used for initial ranking; actual latency is measured
         * at runtime and fed back into the evidence store.
         */
        fun protocolLatencyEstimate(protocol: Protocol): Long = when (protocol) {
            Protocol.DirectIr -> 5L
            Protocol.HubIr -> 50L
            Protocol.HidOverUsb -> 1L
            Protocol.HidOverBle -> 10L
            Protocol.HidOverBluetooth -> 15L
            Protocol.HdmiCec -> 20L
            Protocol.Matter -> 30L
            Protocol.Thread -> 35L
            Protocol.Zigbee -> 40L
            Protocol.ZWave -> 80L
            Protocol.ZWaveLongRange -> 90L
            Protocol.Ble -> 25L
            Protocol.WiFi -> 20L
            Protocol.Ethernet -> 5L
            Protocol.Mqtt -> 50L
            Protocol.Onvif -> 100L
            Protocol.Rtsp -> 100L
            Protocol.Rtsps -> 110L
            Protocol.WebRtc -> 50L
            Protocol.VendorRest -> 200L
            Protocol.VendorWebSocket -> 150L
            Protocol.ElysiumLink -> 10L
            Protocol.Unknown -> 1000L
        }
    }
}
