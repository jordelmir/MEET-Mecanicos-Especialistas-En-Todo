package com.elysium.nexus.core.transport

import com.elysium.nexus.core.model.UniversalControllerState

/**
 * A test-friendly [ControllerTransport] that
 * records every `sendRealtime` call instead of
 * sending to a real host.
 *
 * `MASTER_ORDER.md` §45 says the first milestone
 * is "the APK on Honor Magic V2 that emits
 * generic HID (or Elysium Link) and the host
 * sees buttons / D-pad / sticks / triggers /
 * motion, and an abrupt disconnect neutralizes
 * everything". Phase 1.13 ships the **seam**:
 * the engine is wired to a transport, and the
 * transport is a [LocalEchoTransport] that
 * records every frame.
 *
 * The echo is the test surface for the
 * end-to-end engine→transport pipeline. The
 * activity wires the echo as the default
 * transport; the [BluetoothHidTransport] /
 * [UsbAccessoryTransport] /
 * [LocalNetworkElysiumLinkTransport] are picked
 * from the [TransportSelector] in Phase 1.14.
 *
 * ## Why an echo, not a "real" transport
 *
 * The real transports (BT HID, USB, Elysium
 * Link) require hardware or a host process. The
 * echo is a *transparent* transport that
 * captures the same frames the real transport
 * would send. The activity can swap the
 * transport at any time (Phase 1.14's
 * [TransportSelector]) without changing the
 * engine's call sites.
 */
class LocalEchoTransport : ControllerTransport {

    private val recorded: MutableList<UniversalControllerState> = mutableListOf()
    private val reliableEvents: MutableList<ReliableInputEvent> = mutableListOf()

    override val capabilities: TransportCapabilities = TransportCapabilities(
        maxRealtimeFps = 10_000, // local echo is instant
        supportsReliable = true,
        latencyMs = 0, // zero-latency
        label = "Local echo (test)"
    )

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(TransportState.IDLE)
    override val state: TransportState
        get() = _state.value

    override suspend fun start(): TransportResult {
        _state.value = TransportState.INITIALISING
        return TransportResult.Ok
    }

    override suspend fun pair(): PairingResult {
        _state.value = TransportState.PAIRED
        return PairingResult.Ok
    }

    override suspend fun connect(): ConnectionResult {
        _state.value = TransportState.CONNECTED
        return ConnectionResult.Ok
    }

    override suspend fun sendReliable(event: ReliableInputEvent): SendResult {
        reliableEvents.add(event)
        return SendResult.Ok
    }

    override suspend fun sendRealtime(state: UniversalControllerState): SendResult {
        recorded.add(state)
        return SendResult.Ok
    }

    override suspend fun releaseAll(): SendResult {
        reliableEvents.add(ReliableInputEvent.ReleaseAll)
        return SendResult.Ok
    }

    override suspend fun disconnect(): DisconnectResult {
        _state.value = TransportState.DISCONNECTED
        return DisconnectResult.Ok
    }

    override suspend fun stop(): TransportResult {
        _state.value = TransportState.IDLE
        return TransportResult.Ok
    }

    fun recordedCount(): Int = recorded.size
    fun reliableCount(): Int = reliableEvents.size
    fun recordedAt(i: Int): UniversalControllerState = recorded[i]
    fun reliableAt(i: Int): ReliableInputEvent = reliableEvents[i]
    fun recorded(): List<UniversalControllerState> = recorded.toList()
    fun clear() {
        recorded.clear()
        reliableEvents.clear()
    }
}
