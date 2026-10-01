package com.elysium.nexus.fabric.bridge

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.canonical.UniversalAction

/**
 * §23 Universal Protocol Bridge.
 *
 * Generalizes cross-protocol bridging:
 * ```
 * IR       → WiFi
 * WiFi     → IR
 * HID      → Matter
 * Bluetooth→ CEC
 * USB      → Bluetooth
 * CEC      → Elysium Link
 * ```
 *
 * Never bridges raw bytes. Always:
 * ```
 * Input protocol → UniversalAction → Output protocol
 * ```
 *
 * The bridge is the physical embodiment of
 * the Universal Intent Model: any input becomes
 * a semantic action, any output protocol executes
 * it.
 */
class ProtocolBridge(
    private val translators: Map<Protocol, ProtocolTranslator>
) {

    /**
     * Bridge an action from one protocol to another.
     *
     * @param action the universal action to bridge
     * @param sourceProtocol the protocol that originated the action
     * @param targetProtocol the protocol to deliver the action
     * @param targetDevice the target device
     * @return the bridged command, or null if unsupported
     */
    fun bridge(
        action: UniversalAction,
        sourceProtocol: Protocol,
        targetProtocol: Protocol,
        targetDevice: DeviceId
    ): BridgedCommand? {
        val translator = translators[targetProtocol]
            ?: return null

        return translator.translate(action, targetDevice)
    }

    /**
     * Get all supported bridging paths.
     */
    fun supportedPaths(): List<BridgingPath> {
        return translators.entries.map { (protocol, translator) ->
            BridgingPath(
                targetProtocol = protocol,
                supportedActions = translator.supportedActions()
            )
        }
    }

    /**
     * Check if a specific bridge is supported.
     */
    fun canBridge(
        sourceProtocol: Protocol,
        targetProtocol: Protocol,
        action: UniversalAction
    ): Boolean {
        val translator = translators[targetProtocol] ?: return false
        return translator.supportedActions().contains(action::class.simpleName)
    }
}

/**
 * A protocol translator converts a [UniversalAction]
 * into a protocol-specific command.
 */
interface ProtocolTranslator {
    /**
     * Translate a universal action into a protocol
     * command for the target device.
     */
    fun translate(action: UniversalAction, targetDevice: DeviceId): BridgedCommand?

    /**
     * Get the set of action types this translator
     * supports (by simpleName).
     */
    fun supportedActions(): Set<String>
}

/**
 * A bridged command ready for transport.
 */
data class BridgedCommand(
    val protocol: Protocol,
    val action: UniversalAction,
    val commandData: Map<String, String>,
    val priority: Int,
    val latencyEstimateMs: Long
)

/**
 * A supported bridging path.
 */
data class BridgingPath(
    val targetProtocol: Protocol,
    val supportedActions: Set<String>
)
