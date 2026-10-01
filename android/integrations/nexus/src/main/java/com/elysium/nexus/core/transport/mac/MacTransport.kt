package com.elysium.nexus.core.transport.mac

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Elysium Nexus — Mac/PC transport.
 *
 * This class is the Android client side of the
 * Phase ULT.4 control fabric. It opens a TCP
 * socket to the Mac agent, runs the X25519 +
 * ChaCha20-Poly1305 handshake, drives the
 * 6-digit PIN pairing flow, and (after pairing)
 * dispatches every trackpad / keyboard gesture
 * to the agent as an encrypted frame.
 *
 * The class is the **stateful** companion to the
 * stateless [MacProtocol] + [MacCrypto] modules.
 * It is intentionally small (~350 lines) and
 * focuses on one thing: bridging the Compose
 * callbacks (`onMouseMove`, `onLeftClick`, etc.)
 * to the wire.
 *
 * ## Threading
 *
 *  - The socket I/O runs on `Dispatchers.IO`.
 *  - A single coroutine reads frames and posts
 *    state changes to the [state] `StateFlow`.
 *  - `send*` methods enqueue frames on a
 *    `Channel<ByteArray>`; a writer coroutine
 *    drains the channel and serialises the
 *    writes (one frame at a time, so the wire
 *    never interleaves two frames).
 *
 * ## Lifecycle
 *
 *  - `connect(...)` opens the socket + runs the
 *    handshake. It is a `suspend` function;
 *    cancellation closes the socket.
 *  - `disconnect()` sends GOODBYE and closes.
 *  - The transport owns a `SupervisorJob`; if a
 *    socket error throws, the read/write
 *    coroutines are cancelled and [state]
 *    transitions to [MacConnectionState.Error].
 *
 * ## The "seam" with Compose
 *
 * The activity creates **one** [MacTransport]
 * per paired Mac, passes it down through
 * `CompositionLocal` or as a parameter, and
 * the `MacControlSurfaceScreen` calls
 * `sendMouseMove(dx, dy)`, `sendLeftClick()`,
 * etc. on every gesture. The transport buffers
 * the writes; the actual TCP send is a small
 * background job.
 */
class MacTransport {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<MacConnectionState>(MacConnectionState.Idle)
    val state: StateFlow<MacConnectionState> = _state.asStateFlow()

    // Priority dual-channel: input events (mouse,
    // key, click) are ALWAYS sent before screen
    // frames. This prevents large JPEG frames
    // (~30-50KB) from blocking tiny mouse move
    // packets (~50 bytes).
    private val inputEvents = Channel<ByteArray>(capacity = Channel.UNLIMITED)
    private val outgoing = Channel<ByteArray>(capacity = Channel.UNLIMITED)
    private val connected = AtomicBoolean(false)
    private var socket: Socket? = null
    private var readJob: Job? = null
    private var writeJob: Job? = null
    private var keyPair: MacCrypto.KeyPair? = null
    private var channelKey: MacCrypto.ChannelKey? = null
    // Screen frame stream — JPEG bytes from Mac.
    private val _screenFrames = MutableSharedFlow<ByteArray>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val screenFrames: SharedFlow<ByteArray> = _screenFrames.asSharedFlow()
    // Phase ULT.4 — pending streams for the
    // handshake half-done state. The transport
    // opens the socket in `startHandshake` and
    // the read/write pumps only start in
    // `sendPin` (after the PIN is accepted).
    private var pendingInput: java.io.InputStream? = null
    private var pendingOutput: java.io.OutputStream? = null

    /**
     * Phase ULT.4 (refactored) — the handshake is
     * split into two phases so the user can type
     * the PIN that the Mac agent shows.
     *
     * `startHandshake` does the TCP connect,
     * sends HELLO, reads HELLO_ACK, and derives
     * the channel key. The state transitions to
     * [MacConnectionState.AwaitingPin] when the
     * transport is ready to receive the PIN.
     *
     * `sendPin` sends the 6 encrypted PIN digits
     * and reads the encrypted PAIR_OK. The state
     * transitions to [MacConnectionState.Ready]
     * on success, or [MacConnectionState.Error]
     * on mismatch.
     *
     * The split lets the UI render "Type the
     * PIN shown on your Mac" between the two
     * phases. The split is the *real* UX: the
     * previous all-in-one `connect(host, pin)`
     * forced the phone to guess the PIN (and
     * therefore could never actually pair).
     */
    suspend fun startHandshake(host: DiscoveredHost): MacConnectionState =
        withContext(Dispatchers.IO) {
            if (!connected.compareAndSet(false, true)) {
                return@withContext _state.value
            }
            _state.value = MacConnectionState.Connecting(host.name)
            try {
                // 1. Open the TCP socket.
                val sock = Socket()
                sock.connect(InetSocketAddress(host.host, host.port), 5_000)
                sock.tcpNoDelay = true
                socket = sock
                val input = sock.getInputStream()
                val output = sock.getOutputStream()

                // 2. Generate our X25519 key pair.
                val kp = MacCrypto.generateKeyPair()
                keyPair = kp

                // 3. Send HELLO (our 32-byte public key).
                val helloFrame = MacProtocol.encodeFrame(
                    MacProtocol.FrameType.HELLO,
                    kp.publicKeyBytes
                )
                output.write(helloFrame)
                output.flush()
                Log.d(TAG, "Sent HELLO (${kp.publicKeyBytes.size} B pubkey)")

                // 4. Read HELLO_ACK (server's 32-byte public key).
                val (ackType, ackPayload) = readOneFrame(input)
                    ?: throw IOException("Connection closed before HELLO_ACK")
                if (ackType != MacProtocol.FrameType.HELLO_ACK) {
                    throw IOException("Expected HELLO_ACK, got $ackType")
                }
                if (ackPayload.size != 32) {
                    throw IOException("HELLO_ACK payload must be 32 B, got ${ackPayload.size}")
                }
                val theirPublicKey = ackPayload

                // 5. Derive the channel key.
                val ck = MacCrypto.deriveChannelKey(kp, theirPublicKey)
                channelKey = ck
                Log.d(TAG, "Derived channel key")

                // Stash the streams for the PIN phase.
                pendingInput = input
                pendingOutput = output

                // Transition to AwaitingPin. The UI
                // now shows the PIN entry form.
                _state.value = MacConnectionState.AwaitingPin(host.name, host.model, host.osVersion)
                Log.d(TAG, "Handshake half-done; awaiting PIN")
                _state.value
            } catch (e: Throwable) {
                Log.e(TAG, "Handshake failed: ${e.message}", e)
                _state.value = MacConnectionState.Error(e.message ?: e::class.java.simpleName)
                connected.set(false)
                socket?.close()
                socket = null
                keyPair = null
                channelKey = null
                _state.value
            }
        }

    /**
     * Sends the 6 encrypted PIN digits and waits
     * for the encrypted `PAIR_OK` from the Mac.
     *
     * On success, the read/write pumps are
     * started and the state becomes
     * [MacConnectionState.Ready].
     */
    suspend fun sendPin(pin: String): MacConnectionState = withContext(Dispatchers.IO) {
        require(pin.length == 6 && pin.all { it.isDigit() }) {
            "PIN must be 6 digits"
        }
        val ck = channelKey ?: return@withContext run {
            _state.value = MacConnectionState.Error("sendPin called before handshake")
            _state.value
        }
        val sock = socket ?: return@withContext run {
            _state.value = MacConnectionState.Error("sendPin: socket closed")
            _state.value
        }
        val output = pendingOutput ?: return@withContext run {
            _state.value = MacConnectionState.Error("sendPin: output stream lost")
            _state.value
        }
        val input = pendingInput ?: return@withContext run {
            _state.value = MacConnectionState.Error("sendPin: input stream lost")
            _state.value
        }
        try {
            // 1. Send 6 encrypted PIN_DIGIT frames.
            for ((index, ch) in pin.withIndex()) {
                val digit = (ch.digitToInt() and 0xFF).toByte()
                val encryptedDigit = ck.encrypt(byteArrayOf(digit))
                val frame = MacProtocol.encodeFrame(
                    MacProtocol.FrameType.PIN_DIGIT,
                    encryptedDigit
                )
                output.write(frame)
                output.flush()
                Log.d(TAG, "Sent PIN_DIGIT $index")
            }

            // 2. Read response frame (PAIR_OK or zero-PIN auto-approved stream frame).
            val frameResult = readOneFrame(input)
            if (frameResult == null) {
                Log.w(TAG, "Server disconnected before completing pairing handshake (EOF)")
                _state.value = MacConnectionState.Error("Pairing failed: EOF during handshake")
                connected.set(false)
                sock.close()
                return@withContext _state.value
            }

            val (pairType, pairPayload) = frameResult
            if (pairType == MacProtocol.FrameType.PAIR_OK) {
                val pairPlain = ck.decrypt(pairPayload)
                if (pairPlain.isNotEmpty() && pairPlain[0] != 0x01.toByte()) {
                    Log.w(TAG, "Pairing rejected by server")
                    _state.value = MacConnectionState.Error("Pairing rejected: wrong PIN")
                    connected.set(false)
                    sock.close()
                    return@withContext _state.value
                }
            } else {
                // V0.6.3 Phase 22: Fail-closed — only PAIR_OK is accepted.
                Log.w(TAG, "Server responded with frame $pairType; rejecting non-PAIR_OK frame (fail-closed)")
                _state.value = MacConnectionState.Error("Pairing failed: server sent $pairType instead of PAIR_OK")
                connected.set(false)
                sock.close()
                return@withContext _state.value
            }

            // 3. Handshake complete — start the read/write pumps.
            readJob = scope.launch { readPump(input) }
            writeJob = scope.launch { writePump(output) }
            pendingInput = null
            pendingOutput = null
            val lastReady = _state.value as? MacConnectionState.AwaitingPin
            val name = lastReady?.hostName ?: "host"
            val model = lastReady?.model ?: "Mac"
            val os = lastReady?.osVersion ?: "macOS"
            _state.value = MacConnectionState.Ready(name, model, os)
            Log.d(TAG, "PIN accepted; transport READY")
            _state.value
        } catch (e: Throwable) {
            Log.e(TAG, "sendPin failed: ${e.message}", e)
            _state.value = MacConnectionState.Error(e.message ?: e::class.java.simpleName)
            connected.set(false)
            try {
                sock.close()
            } catch (_: Throwable) {}
            socket = null
            keyPair = null
            channelKey = null
            _state.value
        }
    }

    /** Backwards-compatible one-shot connect for tests + mocks. */
    @Suppress("unused")
    suspend fun connect(host: DiscoveredHost, pin: String): MacConnectionState {
        val r1 = startHandshake(host)
        if (r1 !is MacConnectionState.AwaitingPin && r1 !is MacConnectionState.Ready) {
            return r1
        }
        return sendPin(pin)
    }

    /**
     * Closes the transport gracefully. Sends a
     * GOODBYE frame, then closes the socket.
     */
    fun disconnect() {
        if (!connected.compareAndSet(true, false)) return
        try {
            channelKey?.let {
                val enc = it.encrypt(ByteArray(0))
                val frame = MacProtocol.encodeFrame(MacProtocol.FrameType.GOODBYE, enc)
                socket?.getOutputStream()?.write(frame)
                socket?.getOutputStream()?.flush()
            } ?: socket?.getOutputStream()?.write(
                MacProtocol.encodeFrame(MacProtocol.FrameType.GOODBYE)
            )
        } catch (_: Throwable) {
            // best effort
        }
        try {
            socket?.close()
        } catch (_: Throwable) {
        }
        socket = null
        keyPair = null
        channelKey = null
        scope.coroutineContext.cancel()
        _state.value = MacConnectionState.Disconnected
    }

    // === Send API ===

    fun sendMouseMove(dx: Float, dy: Float) {
        val payload = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .putFloat(dx)
            .putFloat(dy)
            .array()
        sendHighPriority(MacProtocol.FrameType.MOUSE_MOVE, payload)
    }

    /** Direct Touch: Send absolute normalized cursor coordinates (0.0 to 1.0) */
    fun sendMouseAbsMove(normX: Float, normY: Float) {
        val payload = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .putFloat(normX)
            .putFloat(normY)
            .array()
        sendHighPriority(MacProtocol.FrameType.MOUSE_ABS_MOVE, payload)
    }

    fun sendMouseButton(button: MacProtocol.MouseButton, state: MacProtocol.ButtonState) {
        sendHighPriority(
            MacProtocol.FrameType.MOUSE_BUTTON,
            byteArrayOf(button.byte, state.byte)
        )
    }

    fun sendScroll(dx: Float, dy: Float) {
        val payload = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .putFloat(dx)
            .putFloat(dy)
            .array()
        sendHighPriority(MacProtocol.FrameType.SCROLL, payload)
    }

    fun sendKey(action: MacProtocol.KeyAction, hidUsage: Int, modifiers: Int) {
        val payload = ByteBuffer.allocate(9).order(ByteOrder.BIG_ENDIAN)
            .put(action.byte)
            .putInt(hidUsage)
            .putInt(modifiers)
            .array()
        sendHighPriority(MacProtocol.FrameType.KEY, payload)
    }

    fun sendPinch(factor: Float) {
        val payload = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            .putFloat(factor)
            .array()
        sendHighPriority(MacProtocol.FrameType.PINCH, payload)
    }

    /**
     * Send a media key (volume up / down / mute,
     * play / pause, next / previous). The
     * `keyCode` is the standard macOS media key
     * constant: 0 = volume up, 1 = volume down,
     * 7 = mute, 16 = play, 17 = previous,
     * 18 = next.
     */
    fun sendMedia(keyCode: Int) {
        sendHighPriority(MacProtocol.FrameType.MEDIA, byteArrayOf(keyCode.toByte()))
    }

    fun sendHeartbeat() {
        // Heartbeat is unencrypted (per protocol).
        try {
            outgoing.trySend(MacProtocol.encodeFrame(MacProtocol.FrameType.HEARTBEAT))
        } catch (_: Throwable) {}
    }

    /**
     * Request the Mac agent to start or stop
     * streaming its screen as JPEG frames.
     *
     * @param start true to start, false to stop
     */
    fun sendScreenRequest(start: Boolean) {
        val cmd: Byte = if (start) 0x01 else 0x00
        sendEncrypted(MacProtocol.FrameType.SCREEN_REQUEST, byteArrayOf(cmd))
    }

    // === Internals ===

    /** High-priority send for input events (mouse, key, click). */
    private fun sendHighPriority(type: MacProtocol.FrameType, payload: ByteArray) {
        val ck = channelKey ?: return
        val enc = ck.encrypt(payload)
        val frame = MacProtocol.encodeFrame(type, enc)
        inputEvents.trySend(frame)
    }

    /** Normal-priority send for non-input frames. */
    private fun sendEncrypted(type: MacProtocol.FrameType, payload: ByteArray) {
        val ck = channelKey ?: return
        val enc = ck.encrypt(payload)
        val frame = MacProtocol.encodeFrame(type, enc)
        outgoing.trySend(frame)
    }

    private suspend fun readPump(input: java.io.InputStream) {
        val buf = ByteArray(64 * 1024)
        var inBuf = ByteArray(64 * 1024)
        var inLen = 0
        var consumed = 0
        try {
            while (scope.isActive && connected.get()) {
                val n = input.read(buf)
                if (n < 0) break
                if (n == 0) continue
                // Grow the input buffer if needed.
                if (inLen + n > inBuf.size) {
                    val newBuf = ByteArray(inBuf.size * 2)
                    System.arraycopy(inBuf, 0, newBuf, 0, inLen)
                    inBuf = newBuf
                }
                System.arraycopy(buf, 0, inBuf, inLen, n)
                inLen += n
                // Drain frames.
                while (true) {
                    val r = MacProtocol.readFrameFromStream(inBuf, inLen, consumed)
                    when (r) {
                        is MacProtocol.StreamReadResult.Ok -> {
                            handleFrame(r.frame)
                            consumed = r.newConsumed
                        }
                        is MacProtocol.StreamReadResult.NeedMore -> {
                            consumed = r.stillConsumed
                            break
                        }
                        is MacProtocol.StreamReadResult.Error -> {
                            Log.w(TAG, "Read error: ${r.reason}")
                            consumed = r.newConsumed
                            break
                        }
                    }
                }
                // Compact: copy remaining unconsumed bytes
                // to the front of the buffer.
                if (consumed > 0) {
                    val remaining = inLen - consumed
                    if (remaining > 0) {
                        System.arraycopy(inBuf, consumed, inBuf, 0, remaining)
                    }
                    inLen = remaining
                    consumed = 0
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "readPump: ${e.message}")
        }
        _state.value = MacConnectionState.Disconnected
    }

    private fun handleFrame(frame: MacProtocol.Frame) {
        when (frame.type) {
            MacProtocol.FrameType.HEARTBEAT,
            MacProtocol.FrameType.GOODBYE -> Unit
            MacProtocol.FrameType.SCREEN_FRAME -> {
                // JPEG bytes from the Mac. Emit to
                // the screen frame flow for the UI
                // to render. NOT encrypted for perf.
                _screenFrames.tryEmit(frame.payload)
            }
            else -> {
                Log.d(TAG, "Received ${frame.type} (${frame.payload.size} B)")
                _state.value = MacConnectionState.ReadyEvent(
                    lastEventDescription = "RX ${frame.type}",
                    lastEventAt = System.currentTimeMillis()
                )
            }
        }
    }

    private suspend fun writePump(output: java.io.OutputStream) {
        try {
            while (connected.get()) {
                // 1. ALWAYS drain ALL queued input
                //    events first (mouse, key, click).
                //    These are tiny (~50 bytes) and
                //    latency-critical.
                var inputFrame = inputEvents.tryReceive().getOrNull()
                while (inputFrame != null) {
                    output.write(inputFrame)
                    inputFrame = inputEvents.tryReceive().getOrNull()
                }
                output.flush()

                // 2. Then send ONE low-priority frame
                //    (heartbeat, screen request) if any.
                val bulkFrame = outgoing.tryReceive().getOrNull()
                if (bulkFrame != null) {
                    output.write(bulkFrame)
                    output.flush()
                }

                // 3. If both channels are empty, suspend
                //    until either has data. Use select to
                //    wake on whichever fires first.
                if (inputEvents.isEmpty && outgoing.isEmpty) {
                    kotlinx.coroutines.selects.select<Unit> {
                        inputEvents.onReceive { frame ->
                            output.write(frame)
                            output.flush()
                        }
                        outgoing.onReceive { frame ->
                            output.write(frame)
                            output.flush()
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "writePump: ${e.message}")
        }
    }

    /**
     * Reads a single complete frame from the input
     * stream. Returns null on EOF.
     */
    private fun readOneFrame(input: java.io.InputStream): Pair<MacProtocol.FrameType, ByteArray>? {
        val header = ByteArray(4)
        var read = 0
        while (read < 4) {
            val n = input.read(header, read, 4 - read)
            if (n < 0) return null
            read += n
        }
        val length = ((header[0].toInt() and 0xFF) shl 24) or
            ((header[1].toInt() and 0xFF) shl 16) or
            ((header[2].toInt() and 0xFF) shl 8) or
            (header[3].toInt() and 0xFF)
        if (length < 1 || length > MacProtocol.MAX_FRAME_SIZE) {
            throw IOException("refusing frame of length $length")
        }
        val rest = ByteArray(length)
        read = 0
        while (read < length) {
            val n = input.read(rest, read, length - read)
            if (n < 0) return null
            read += n
        }
        val type = MacProtocol.FrameType.fromByte(rest[0]) ?: throw IOException("unknown type ${rest[0]}")
        val payload = ByteArray(length - 1)
        System.arraycopy(rest, 1, payload, 0, payload.size)
        return type to payload
    }

    companion object {
        private const val TAG = "MacTransport"
    }
}

/**
 * The transport's connection state. Observed by
 * the UI (status pill, "Connecting..." overlay,
 * etc.).
 */
sealed class MacConnectionState {
    object Idle : MacConnectionState()
    data class Connecting(val hostName: String) : MacConnectionState()
    /**
     * The X25519 handshake is done. The Mac is
     * showing a 6-digit PIN; the user must type
     * it on the phone to confirm physical
     * presence.
     */
    data class AwaitingPin(
        val hostName: String,
        val model: String,
        val osVersion: String
    ) : MacConnectionState()
    data class Ready(
        val hostName: String,
        val model: String,
        val osVersion: String
    ) : MacConnectionState()
    /**
     * The transport is still connected but the
     * last received frame is described. Used for
     * the "live event" pill on the UI.
     */
    data class ReadyEvent(
        val lastEventDescription: String,
        val lastEventAt: Long
    ) : MacConnectionState()
    object Disconnected : MacConnectionState()
    data class Error(val reason: String) : MacConnectionState()
}
