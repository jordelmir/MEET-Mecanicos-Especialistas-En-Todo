package com.elysium369.meet.core.transport

import kotlinx.coroutines.delay
import java.io.IOException
import java.util.ArrayDeque

/**
 * Deterministic byte-level transport for replaying physically captured
 * diagnostic sessions through the production parser stack.
 *
 * A replay is intentionally strict: requests must arrive in the captured
 * order and match exactly. It never synthesizes an adapter response, retries
 * a frame or silently skips a mismatch.
 */
class RecordedDiagnosticTransport(
    frames: List<RecordedDiagnosticFrame>,
) : TransportInterface {
    private val remainingFrames = ArrayDeque(frames)
    private val pendingChunks = ArrayDeque<ByteArray>()
    private var pendingReadError: String? = null
    private var connected = false

    override val isConnected: Boolean
        get() = connected

    val isFullyConsumed: Boolean
        get() = remainingFrames.isEmpty() && pendingChunks.isEmpty() && pendingReadError == null

    val remainingFrameCount: Int
        get() = remainingFrames.size

    override suspend fun connect() {
        connected = true
    }

    override suspend fun disconnect() {
        connected = false
        pendingChunks.clear()
        pendingReadError = null
    }

    override suspend fun reconnect() {
        disconnect()
        connect()
    }

    override suspend fun write(data: ByteArray) {
        requireConnected()
        check(pendingChunks.isEmpty() && pendingReadError == null) {
            "Replay request arrived before the previous captured response was consumed"
        }
        val frame = remainingFrames.pollFirst()
            ?: throw RecordedTraceMismatchException("Unexpected request after end of captured trace")
        if (!data.contentEquals(frame.expectedRequest)) {
            throw RecordedTraceMismatchException(
                "Request mismatch at frame ${frame.sequence}: expected=${frame.expectedRequest.toHex()} actual=${data.toHex()}",
            )
        }
        pendingChunks.addAll(frame.responseChunks.map { it.copyOf() })
        pendingReadError = frame.readError
        if (frame.responseDelayMs > 0L) delay(frame.responseDelayMs)
    }

    override suspend fun read(maxBytes: Int, timeoutMs: Long): ByteArray? {
        requireConnected()
        require(maxBytes > 0) { "maxBytes must be positive" }
        pendingReadError?.let { message ->
            pendingReadError = null
            throw IOException("Recorded transport failure: $message")
        }
        val chunk = pendingChunks.pollFirst() ?: return null
        if (chunk.size <= maxBytes) return chunk.copyOf()

        val head = chunk.copyOfRange(0, maxBytes)
        pendingChunks.addFirst(chunk.copyOfRange(maxBytes, chunk.size))
        return head
    }

    override suspend fun drain() {
        requireConnected()
        pendingChunks.clear()
        pendingReadError = null
    }

    private fun requireConnected() {
        if (!connected) throw IOException("Recorded transport is not connected")
    }
}

data class RecordedDiagnosticFrame(
    val sequence: Long,
    val expectedRequest: ByteArray,
    val responseChunks: List<ByteArray>,
    val responseDelayMs: Long = 0L,
    val readError: String? = null,
) {
    init {
        require(sequence >= 0L) { "sequence must be non-negative" }
        require(expectedRequest.isNotEmpty()) { "captured request must not be empty" }
        require(responseDelayMs >= 0L) { "responseDelayMs must be non-negative" }
        require(responseChunks.isNotEmpty() || readError != null) {
            "a captured frame must contain response bytes or an explicit read failure"
        }
    }
}

class RecordedTraceMismatchException(message: String) : IllegalStateException(message)

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02X".format(byte) }
