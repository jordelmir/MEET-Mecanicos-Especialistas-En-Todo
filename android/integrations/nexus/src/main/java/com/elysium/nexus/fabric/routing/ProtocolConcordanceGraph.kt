package com.elysium.nexus.fabric.routing

import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.canonical.UniversalAction

/**
 * §6 Protocol Concordance Graph.
 *
 * For the same device, a [UniversalAction] can be
 * delivered via multiple protocols. The concordance
 * graph maps each (deviceId, action) pair to the
 * set of protocol-specific representations:
 *
 * - `VOLUME_UP` on a Samsung TV:
 *   - IR: NEC extended frame (0x07, 0x01)
 *   - webOS: `{"type":"request","id":1,"uri":"ssap://audio/setVolume","payload":{"volume":...}}`
 *   - HDMI-CEC: opcode 0x44 (User Control Pressed)
 *   - Bluetooth HID: Consumer Usage 0x00E9 (Volume Increment)
 *   - Elysium Link: `{"action":"volume_up"}`
 *
 * The concordance graph is built once at device
 * pairing and updated when the device's protocol
 * bindings change. The graph does NOT store the
 * actual signal bytes — those live in the
 * protocol-specific adapter. The graph stores
 * the *mapping* so the route negotiator can
 * evaluate all available protocols.
 *
 * ## Why a graph and not a simple map
 *
 * A flat map (action → protocol → representation)
 * works for two protocols. A graph handles:
 * 1. Protocol chains: WiFi fails → IR → CEC
 * 2. Capability gaps: WiFi can set volume level,
 *    IR can only toggle mute
 * 3. State confirmation: WiFi can read back state,
 *    IR cannot
 * 4. Latency ordering: BLE (10ms) < WiFi (20ms)
 */
data class ProtocolConcordanceGraph(
    /** Node: deviceId → set of supported protocols. */
    val deviceProtocols: Map<DeviceId, Set<Protocol>> = emptyMap(),
    /** Edge: (deviceId, action) → set of protocol representations. */
    val actionMappings: Map<ActionProtocolKey, Set<ProtocolMapping>> = emptyMap(),
    /** Wall-clock nanoseconds of the last mutation. */
    val lastMutationNs: Long = 0L
) {
    init {
        require(lastMutationNs >= 0L) {
            "lastMutationNs must be non-negative."
        }
    }

    /**
     * @return all protocols that can deliver [action]
     * to [deviceId], ordered by preference (best first).
     */
    fun protocolsForAction(
        deviceId: DeviceId,
        action: UniversalAction
    ): List<Protocol> {
        val key = ActionProtocolKey(deviceId, action::class.simpleName ?: "Unknown")
        val mappings = actionMappings[key] ?: return emptyList()
        return mappings
            .sortedBy { it.routeScore }
            .map { it.protocol }
    }

    /**
     * @return true if [protocol] can deliver [action]
     * to [deviceId].
     */
    fun canDeliver(
        deviceId: DeviceId,
        action: UniversalAction,
        protocol: Protocol
    ): Boolean {
        val key = ActionProtocolKey(deviceId, action::class.simpleName ?: "Unknown")
        val mappings = actionMappings[key] ?: return false
        return mappings.any { it.protocol == protocol }
    }

    /**
     * @return the fallback chain for [deviceId] when
     * [preferredProtocol] fails. The chain is ordered
     * by reliability (most reliable first).
     */
    fun fallbackChain(
        deviceId: DeviceId,
        preferredProtocol: Protocol
    ): List<Protocol> {
        val protocols = deviceProtocols[deviceId] ?: return emptyList()
        return protocols
            .filter { it != preferredProtocol }
            .sortedBy { RouteNegotiator.protocolPriority(it) }
    }

    /**
     * Add a protocol mapping for a device + action.
     */
    fun withMapping(
        deviceId: DeviceId,
        actionName: String,
        mapping: ProtocolMapping
    ): ProtocolConcordanceGraph {
        val key = ActionProtocolKey(deviceId, actionName)
        val existing = actionMappings[key] ?: emptySet()
        return copy(
            actionMappings = actionMappings + (key to (existing + mapping)),
            lastMutationNs = System.nanoTime()
        )
    }

    /**
     * Register that a device supports a protocol.
     */
    fun withProtocol(
        deviceId: DeviceId,
        protocol: Protocol
    ): ProtocolConcordanceGraph {
        val existing = deviceProtocols[deviceId] ?: emptySet()
        return copy(
            deviceProtocols = deviceProtocols + (deviceId to (existing + protocol)),
            lastMutationNs = System.nanoTime()
        )
    }
}

/**
 * Composite key: (deviceId, actionName).
 */
data class ActionProtocolKey(
    val deviceId: DeviceId,
    val actionName: String
)

/**
 * A protocol-specific representation of an action
 * on a device.
 *
 * @param protocol which protocol delivers this action.
 * @param capabilityHint what the action looks like
 *   through this protocol (e.g. "NEC extended frame",
 *   "webOS JSON", "CEC opcode 0x44").
 * @param canConfirmState whether this protocol can
 *   read back state after a command.
 * @param routeScore a precomputed preference score
 *   (lower = better). Used for ordering within the
 *   concordance graph.
 */
data class ProtocolMapping(
    val protocol: Protocol,
    val capabilityHint: String = "",
    val canConfirmState: Boolean = false,
    val routeScore: Int = RouteNegotiator.protocolPriority(protocol)
)
