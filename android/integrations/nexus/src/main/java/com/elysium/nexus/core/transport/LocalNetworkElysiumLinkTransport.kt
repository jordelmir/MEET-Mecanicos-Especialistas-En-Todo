package com.elysium.nexus.core.transport

import com.elysium.nexus.core.model.UniversalControllerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.ServerSocket
import java.net.Socket

/**
 * The §17 Local Network Elysium Link transport
 * (skeleton).
 *
 * `MASTER_ORDER.md` §17 lists
 * `LocalNetworkQuicTransport` as one of the
 * transport implementations. The full
 * implementation uses QUIC over Wi-Fi / LAN; the
 * §19 "Elysium Link" protocol is the wire format.
 *
 * Phase 1.10 ships the **skeleton**: a TCP server
 * that accepts a single connection and reads /
 * writes raw bytes. The skeleton's protocol is a
 * placeholder; the real protocol is the §19
 * Elysium Link (Phase 5+). The skeleton's goal is
 * to validate the transport's lifecycle
 * (start / pair / connect / send / disconnect /
 * stop) and the [ControllerTransport] interface.
 *
 * ## Why TCP, not QUIC, in the skeleton
 *
 * The Java standard library ships `ServerSocket`
 * and `Socket`; QUIC requires a third-party
 * library (`okhttp` + `quiche` / `cronet`). The
 * skeleton's `start` / `connect` lifecycle is
 * platform-agnostic; Phase 5+ replaces the wire
 * layer with QUIC without changing the
 * transport's public API.
 */
class LocalNetworkElysiumLinkTransport(
    private val port: Int = 7777,
    private val scope: CoroutineScope
) : ControllerTransport {

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var readJob: kotlinx.coroutines.Job? = null

    private val _state = MutableStateFlow(TransportState.IDLE)
    override val state: TransportState
        get() = _state.value

    override val capabilities: TransportCapabilities = TransportCapabilities(
        maxRealtimeFps = 250, // 4ms latency floor
        supportsReliable = true,
        latencyMs = 5, // LAN, < 5ms typical
        label = "Elysium Link (LAN)"
    )

    override suspend fun start(): TransportResult = withContext(Dispatchers.IO) {
        try {
            serverSocket = ServerSocket(port)
            _state.value = TransportState.INITIALISING
            // Accept the first connection. The
            // skeleton's API is single-connection
            // (one host at a time). Multi-connection
            // is a Phase 5+ feature.
            readJob = scope.launch(Dispatchers.IO) {
                try {
                    val sock = serverSocket?.accept() ?: return@launch
                    clientSocket = sock
                    _state.value = TransportState.PAIRED
                    // The skeleton's read loop is a
                    // no-op; the real protocol (Phase 5+)
                    // parses the §19 frames and emits
                    // UniversalControllerState values.
                } catch (e: Throwable) {
                    _state.value = TransportState.ERROR
                }
            }
            TransportResult.Ok
        } catch (e: Throwable) {
            TransportResult.Error("Failed to start server: ${e.message}")
        }
    }

    override suspend fun pair(): PairingResult {
        // The skeleton's "pair" is a no-op: the
        // host connects and the server accepts.
        // The real pairing is the §19 Elysium Link
        // pairing (challenge / response).
        if (clientSocket == null) {
            return PairingResult.Error("No host connected")
        }
        _state.value = TransportState.PAIRED
        return PairingResult.Ok
    }

    override suspend fun connect(): ConnectionResult {
        if (clientSocket == null) {
            return ConnectionResult.Error("No host connected")
        }
        _state.value = TransportState.CONNECTED
        return ConnectionResult.Ok
    }

    override suspend fun sendReliable(event: ReliableInputEvent): SendResult = withContext(Dispatchers.IO) {
        try {
            val sock = clientSocket ?: return@withContext SendResult.Error("Not connected")
            val frame = encodeFrame(event)
            sock.outputStream.write(frame)
            sock.outputStream.flush()
            SendResult.Ok
        } catch (e: Throwable) {
            SendResult.Error("sendReliable failed: ${e.message}")
        }
    }

    override suspend fun sendRealtime(state: UniversalControllerState): SendResult = withContext(Dispatchers.IO) {
        try {
            val sock = clientSocket ?: return@withContext SendResult.Error("Not connected")
            val frame = encodeFrame(state)
            sock.outputStream.write(frame)
            // The skeleton's stream is unbuffered;
            // the real protocol (Phase 5+) batches
            // frames for throughput.
            SendResult.Ok
        } catch (e: Throwable) {
            SendResult.Error("sendRealtime failed: ${e.message}")
        }
    }

    override suspend fun releaseAll(): SendResult = sendReliable(ReliableInputEvent.ReleaseAll)

    override suspend fun disconnect(): DisconnectResult {
        readJob?.cancel()
        try {
            clientSocket?.close()
        } catch (_: Throwable) { /* no-op */ }
        clientSocket = null
        _state.value = TransportState.DISCONNECTED
        return DisconnectResult.Ok
    }

    override suspend fun stop(): TransportResult {
        disconnect()
        try {
            serverSocket?.close()
        } catch (_: Throwable) { /* no-op */ }
        serverSocket = null
        _state.value = TransportState.IDLE
        return TransportResult.Ok
    }

    private fun encodeFrame(event: ReliableInputEvent): ByteArray = when (event) {
        is ReliableInputEvent.ReleaseAll -> byteArrayOf(0x01)
        is ReliableInputEvent.ButtonDown -> byteArrayOf(0x02, event.button.ordinal.toByte())
        is ReliableInputEvent.ButtonUp -> byteArrayOf(0x03, event.button.ordinal.toByte())
        is ReliableInputEvent.ProfileChanged -> byteArrayOf(0x04, event.profileId.toByte())
        is ReliableInputEvent.PairingRequest -> byteArrayOf(0x05) + event.hostName.toByteArray()
        is ReliableInputEvent.Revocation -> byteArrayOf(0x06) + event.hostName.toByteArray()
    }

    private fun encodeFrame(state: UniversalControllerState): ByteArray {
        // The skeleton's encoding is a 1-byte tag +
        // 8-byte button mask + 2-byte stick (x, y).
        // The §19 Elysium Link encoding (Phase 5+)
        // is a length-prefixed frame with a more
        // elaborate header.
        val mask = state.buttons.bits
        val buttons = byteArrayOf(
            (mask ushr 56 and 0xff).toByte(),
            (mask ushr 48 and 0xff).toByte(),
            (mask ushr 40 and 0xff).toByte(),
            (mask ushr 32 and 0xff).toByte(),
            (mask ushr 24 and 0xff).toByte(),
            (mask ushr 16 and 0xff).toByte(),
            (mask ushr 8 and 0xff).toByte(),
            (mask and 0xff).toByte()
        )
        val stick = byteArrayOf(
            ((state.leftStick.x + 1f) * 127.5f).toInt().coerceIn(0, 255).toByte(),
            ((state.leftStick.y + 1f) * 127.5f).toInt().coerceIn(0, 255).toByte()
        )
        return byteArrayOf(0x10) + buttons + stick
    }
}
