package com.elysium.nexus.fabric.tv

import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.DeviceTwin
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.canonical.UniversalAction
import kotlinx.coroutines.flow.Flow

/**
 * §9 Universal TV LAN Fabric.
 *
 * The [TvLanAdapter] is the protocol-specific
 * bridge for controlling a TV over LAN. Each
 * TV brand has its own adapter implementation
 * that handles discovery, pairing, state
 * observation, and command execution.
 *
 * ## Lifecycle
 *
 * ```
 * Created → discover() → identify() → pair()
 *         → [Ready]
 *            ↕  execute() / readState()
 *         disconnect()
 * ```
 *
 * ## State observation
 *
 * Adapters that support state subscriptions
 * (webOS WebSocket, Samsung Tizen, Sony Bravia)
 * push [DeviceStateChange]s via [observeState].
 * Adapters that only support request-response
 * (IR-only) return null from [readState] and
 * emit nothing from [observeState].
 *
 * ## Security
 *
 * Some TVs require a pairing step (PIN confirm,
 * key exchange). The adapter handles the protocol;
 * the [CredentialVault] stores the result.
 */
interface TvLanAdapter {

    /** The TV brand/protocol this adapter handles. */
    val brand: TvBrand

    /** Supported protocols for this TV. */
    val supportedProtocols: Set<Protocol>

    /** Supported capabilities. */
    val supportedCapabilities: Set<Capability>

    /**
     * Discover TV devices on the local network.
     * Returns raw discovery records.
     */
    suspend fun discover(timeoutMs: Long = 10_000L): List<TvDiscoveryRecord>

    /**
     * Identify a TV at a network endpoint.
     * Returns detailed identity evidence.
     */
    suspend fun identify(endpoint: String): TvIdentityEvidence

    /**
     * Pair with the TV. May require user
     * confirmation (PIN display, button press).
     */
    suspend fun pair(request: PairingRequest): PairingResult

    /**
     * Query the TV's full capability set.
     */
    suspend fun queryCapabilities(): Set<TvCapability>

    /**
     * Execute a universal action on the TV.
     */
    suspend fun execute(action: UniversalAction): ActionExecutionResult

    /**
     * Read the current state of a capability.
     * Returns null if the TV doesn't support
     * state readback.
     */
    suspend fun readState(capability: Capability): DeviceState?

    /**
     * Observe real-time state changes.
     * Emits nothing if the TV doesn't support
     * state subscriptions.
     */
    fun observeState(): Flow<DeviceStateChange>

    /**
     * Send a wake-on-LAN packet to power on
     * the TV.
     */
    suspend fun wake(): WakeResult

    /**
     * Disconnect from the TV.
     */
    suspend fun disconnect()
}

/**
 * TV brands with specific adapters.
 */
enum class TvBrand(val displayName: String) {
    LG("LG webOS"),
    Samsung("Samsung Tizen"),
    Sony("Sony Bravia"),
    AndroidGoogle("Android / Google TV"),
    Vizio("Vizio SmartCast"),
    Hisense("Hisense Vidaa"),
    Philips("Philips"),
    Panasonic("Panasonic"),
    TCL("TCL"),
    Roku("Roku"),
    Unknown("Unknown TV")
}

/**
 * A TV discovery record from LAN scanning.
 */
data class TvDiscoveryRecord(
    val brand: TvBrand,
    val ipAddress: String,
    val port: Int,
    val hostname: String?,
    val model: String?,
    val modelName: String?,
    val serialNumber: String?,
    val macAddress: String?,
    val firmwareVersion: String?,
    val protocol: Protocol,
    val friendlyName: String?,
    val requiresPairing: Boolean,
    val wakeOnLanSupported: Boolean,
    val rawProperties: Map<String, String> = emptyMap()
) {
    val displayName: String
        get() = friendlyName ?: hostname ?: modelName ?: model ?: "Unknown TV"
}

/**
 * Detailed identity evidence for a TV.
 */
data class TvIdentityEvidence(
    val brand: TvBrand,
    val model: String?,
    val modelName: String?,
    val serialNumber: String?,
    val macAddress: String?,
    val firmwareVersion: String?,
    val platform: String?,
    val protocols: Set<Protocol>,
    val capabilities: Set<Capability>,
    val confidence: Double
)

/**
 * A pairing request.
 */
data class PairingRequest(
    val endpoint: String,
    val pin: String? = null,
    val acceptButtonPressed: Boolean = false,
    val timeoutMs: Long = 30_000L
)

/**
 * Pairing result.
 */
sealed class PairingResult {
    data class Success(val credentialAlias: String) : PairingResult()
    data class Failed(val reason: String) : PairingResult()
    data class Timeout(val message: String) : PairingResult()
    data class UserConfirmationRequired(val message: String) : PairingResult()
}

/**
 * A TV capability.
 */
data class TvCapability(
    val capability: Capability,
    val readable: Boolean,
    val subscribable: Boolean,
    val min: Float? = null,
    val max: Float? = null
)

/**
 * Action execution result.
 */
sealed class ActionExecutionResult {
    data class Success(val reportedState: DeviceState?, val latencyMs: Long) : ActionExecutionResult()
    data class Failed(val error: String, val protocol: Protocol?) : ActionExecutionResult()
    data class Unsupported(val action: String) : ActionExecutionResult()
    data class Timeout(val message: String) : ActionExecutionResult()
}

/**
 * A device state change event.
 */
data class DeviceStateChange(
    val deviceId: DeviceId,
    val capability: Capability,
    val previousState: DeviceState?,
    val newState: DeviceState,
    val timestampNs: Long = System.nanoTime()
)

/**
 * Wake result.
 */
sealed class WakeResult {
    object Sent : WakeResult()
    object Unsupported : WakeResult()
    data class Failed(val reason: String) : WakeResult()
}
