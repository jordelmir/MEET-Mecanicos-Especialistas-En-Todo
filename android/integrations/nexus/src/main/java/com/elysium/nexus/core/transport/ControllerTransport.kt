package com.elysium.nexus.core.transport

import com.elysium.nexus.core.model.UniversalControllerState

/**
 * The §17 transport multiplexer interface.
 *
 * `MASTER_ORDER.md` §17 specifies the transport
 * multiplexer:
 *
 * ```kotlin
 * interface ControllerTransport {
 *     val capabilities: TransportCapabilities
 *     suspend fun start(): TransportResult
 *     suspend fun pair(): PairingResult
 *     suspend fun connect(): ConnectionResult
 *     suspend fun sendReliable(event: ReliableInputEvent): SendResult
 *     suspend fun sendRealtime(frame: RealtimeInputFrame): SendResult
 *     suspend fun disconnect(): DisconnectResult
 *     suspend fun stop(): TransportResult
 * }
 * ```
 *
 * The interface is the **seam** between the
 * canonical input engine and the host's transport
 * layer. The production implementations
 * (`BluetoothClassicHidTransport`,
 * `BluetoothLeElysiumTransport`,
 * `UsbAccessoryTransport`, `MacAgentTransport`,
 * `WindowsAgentTransport`, `LinuxAgentTransport`,
 * `ReceiverTransport`, `RemotePlayCompanionTransport`)
 * live in their own files; each is a thin adapter
 * over the platform's native transport API.
 *
 * Phase 1.6 ships the interface and the result
 * types. Phase 1.7+ ships the first real
 * implementation (Bluetooth HID, with the §18
 * `Elysium Nexus Gamepad` descriptor). The
 * transport's job is to translate a
 * [UniversalControllerState] (the canonical input
 * model) into the transport's wire format and to
 * deliver it to the host.
 *
 * ## Why an interface, not a class
 *
 * The interface is the testable surface. A unit
 * test uses a `FakeTransport` that captures the
 * `sendRealtime` calls; the production
 * implementation is the Android adapter. The
 * agent-memory rule applies.
 *
 * ## Why a `suspend` API
 *
 * The transport's operations are I/O-bound
 * (Bluetooth RFCOMM, USB bulk transfer, network
 * socket). `suspend` is the idiomatic Kotlin
 * abstraction for asynchronous I/O; the
 * implementation chooses its own dispatchers.
 */
interface ControllerTransport {

    /**
     * The transport's capabilities (bandwidth,
     * latency class, host type, etc.). Used by
     * the activity to choose the best transport
     * when multiple are available.
     */
    val capabilities: TransportCapabilities

    /**
     * @return the transport's state. The
     * transport's state machine is documented
     * at the interface level; each
     * implementation has its own. The activity
     * observes the state via [state] and reacts
     * to changes (e.g. on `CONNECTED`, the
     * activity starts forwarding state to the
     * transport).
     */
    val state: TransportState

    /**
     * Initialise the transport (e.g. open the
     * Bluetooth adapter, claim the USB device,
     * open the socket). Returns [TransportResult.Ok]
     * on success; [TransportResult.Error] on
     * failure (with a reason code).
     */
    suspend fun start(): TransportResult

    /**
     * Pair the host (e.g. Bluetooth pairing, USB
     * handshake, Elysium Link pairing). Returns
     * [PairingResult.Ok] on success; the transport
     * moves to [TransportState.PAIRED] on success.
     */
    suspend fun pair(): PairingResult

    /**
     * Open the data channel. Returns
     * [ConnectionResult.Ok] on success; the
     * transport moves to
     * [TransportState.CONNECTED] on success. The
     * activity can now call [sendRealtime] to
     * forward input to the host.
     */
    suspend fun connect(): ConnectionResult

    /**
     * Send a reliable event (button down/up,
     * key down/up, mouse down/up, profile change,
     * release all, pairing, revocation). The
     * implementation must guarantee delivery
     * (e.g. via an acknowledged packet, a retry
     * loop, or a transport-level reliability
     * mechanism).
     */
    suspend fun sendReliable(event: ReliableInputEvent): SendResult

    /**
     * Send a realtime frame (the latest
     * [UniversalControllerState]). Realtime
     * frames are "latest-wins" — the host
     * discards frames older than the latest. The
     * transport may coalesce, batch, or drop
     * frames under load.
     */
    suspend fun sendRealtime(state: UniversalControllerState): SendResult

    /**
     * Send a §38 "release all" frame. The host
     * neutralizes its state. The implementation
     * must guarantee delivery (reliable
     * semantics, even if [sendRealtime] is
     * best-effort).
     */
    suspend fun releaseAll(): SendResult

    /**
     * Disconnect gracefully. The transport moves
     * to [TransportState.DISCONNECTED] but is
     * still initialised (the activity can call
     * [connect] again to reconnect).
     */
    suspend fun disconnect(): DisconnectResult

    /**
     * Release the transport. After [stop], the
     * transport is unusable. The activity must
     * call [start] again to reuse it.
     */
    suspend fun stop(): TransportResult
}

/**
 * The transport's capabilities. A static
 * description of what the transport *can* do;
 * the [ControllerTransport.state] is what the
 * transport *is currently* doing.
 */
data class TransportCapabilities(
    /** Maximum realtime frames per second. */
    val maxRealtimeFps: Int,
    /** Whether the transport supports reliable events. */
    val supportsReliable: Boolean,
    /** The transport's typical latency class (in milliseconds). */
    val latencyMs: Int,
    /** A human-readable label (e.g. "Bluetooth HID", "USB", "Elysium Link"). */
    val label: String
)

/**
 * The transport's state machine. Mirrors the
 * engine's state machine but at the transport
 * layer.
 */
enum class TransportState {
    IDLE,
    INITIALISING,
    PAIRED,
    CONNECTED,
    DISCONNECTED,
    ERROR
}

/** A generic result type with an error reason. */
sealed class TransportResult {
    object Ok : TransportResult()
    data class Error(val reason: String) : TransportResult()
}

sealed class PairingResult {
    object Ok : PairingResult()
    data class Error(val reason: String) : PairingResult()
}

sealed class ConnectionResult {
    object Ok : ConnectionResult()
    data class Error(val reason: String) : ConnectionResult()
}

sealed class SendResult {
    object Ok : SendResult()
    data class Error(val reason: String) : SendResult()
}

sealed class DisconnectResult {
    object Ok : DisconnectResult()
    data class Error(val reason: String) : DisconnectResult()
}

/**
 * The set of reliable events. The transport
 * guarantees delivery (acknowledged / retried).
 *
 * Phase 1.6 ships the closed set. The full §19
 * "Elysium Link" protocol may add more.
 */
sealed class ReliableInputEvent {
    object ReleaseAll : ReliableInputEvent()
    data class ButtonDown(val button: com.elysium.nexus.core.model.CanonicalButton) : ReliableInputEvent()
    data class ButtonUp(val button: com.elysium.nexus.core.model.CanonicalButton) : ReliableInputEvent()
    data class ProfileChanged(val profileId: Int) : ReliableInputEvent()
    data class PairingRequest(val hostName: String) : ReliableInputEvent()
    data class Revocation(val hostName: String) : ReliableInputEvent()
}
