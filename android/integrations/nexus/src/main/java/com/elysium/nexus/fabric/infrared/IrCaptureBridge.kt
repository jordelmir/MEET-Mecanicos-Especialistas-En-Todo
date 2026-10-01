package com.elysium.nexus.fabric.infrared

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real IR learning bridge.
 *
 * The phone's Android IR API (ConsumerIrManager) exposes **no public
 * receiver**. Real capture therefore happens on a sensor we control:
 * the Nexus Receiver (Wi-Fi) or the desktop agent wired via USB-C,
 * both of which sample the remote's IR and stream raw waveforms here.
 *
 * This object opens a TCP server on the phone, accepts one frame per
 * line, decodes it with [IrLearner] and hands the [IrLearner.LearnResult]
 * to the caller. It is the transport half of §5 / §46 learning.
 *
 * Frame format (one JSON object per line):
 *
 * ```
 * {"carrierHz":38000,"pattern":[9000,4500,560,1690,...]}
 * ```
 */
object IrCaptureBridge {

    const val DEFAULT_PORT = 7879

    private const val TAG = "IrCaptureBridge"

    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    data class CapturedFrame(
        val carrierHz: Int,
        val pattern: IntArray
    )

    fun isRunning(): Boolean = running.get()

    fun start(
        port: Int = DEFAULT_PORT,
        scope: CoroutineScope,
        onLearned: (IrLearner.LearnResult) -> Unit
    ) {
        if (!running.compareAndSet(false, true)) {
            Log.i(TAG, "DIAG LEARN_ALREADY_RUNNING port=$port")
            return
        }
        Log.i(TAG, "DIAG LEARN_LISTEN port=$port")
        serverJob = scope.launch(Dispatchers.IO) {
            var server: ServerSocket? = null
            try {
                // V0.7 Phase 11: Bind to loopback 127.0.0.1 by default to prevent LAN payload injection.
                // Enforce frame size limit (max 8192 bytes) and max pattern length (512 slices).
                val loopback = java.net.InetAddress.getByName("127.0.0.1")
                server = ServerSocket(port, 50, loopback)
                serverSocket = server
                Log.i(TAG, "DIAG LEARN_LISTEN_OK port=$port interface=127.0.0.1 (SECURE)")
                while (running.get()) {
                    val client: Socket = try {
                        server.accept()
                    } catch (e: java.net.SocketException) {
                        break
                    }
                    Log.i(
                        TAG,
                        "DIAG LEARN_CLIENT connected=${client.inetAddress?.hostAddress}"
                    )
                    client.use { c ->
                        c.soTimeout = 5000 // 5-second socket timeout
                        val reader = BufferedReader(InputStreamReader(c.getInputStream()))
                        while (running.get()) {
                            val line = reader.readLine() ?: break
                            if (line.length > 8192) {
                                Log.w(TAG, "DIAG LEARN_FRAME_REJECTED line length ${line.length} exceeds max 8192 bytes")
                                break
                            }
                            val frame = parseFrame(line)
                            if (frame == null || frame.pattern.size > 512) {
                                Log.w(TAG, "DIAG LEARN_FRAME_INVALID len=${line.length} patternSlices=${frame?.pattern?.size}")
                                continue
                            }
                            Log.i(
                                TAG,
                                "DIAG LEARN_FRAME carrier=${frame.carrierHz} slices=${frame.pattern.size}"
                            )
                            val result = IrLearner.learn(frame.pattern, 1_000_000, frame.carrierHz)
                            val proto = result.command?.protocol?.name ?: "UNKNOWN"
                            Log.i(
                                TAG,
                                "DIAG LEARN_DECODED protocol=$proto " +
                                    "addr=${result.command?.address?.let { "0x${it.toString(16)}" } ?: "-"} " +
                                    "cmd=${result.command?.command?.let { "0x${it.toString(16)}" } ?: "-"} " +
                                    "conf=${result.confidence} carrier=${result.carrierHz}"
                            )
                            withContext(Dispatchers.Main) { onLearned(result) }
                        }
                    }
                }
            } catch (e: Throwable) {
                if (running.get()) {
                    Log.e(TAG, "DIAG LEARN_ERROR ${e.message}", e)
                }
            } finally {
                running.set(false)
                serverSocket = null
                Log.i(TAG, "DIAG LEARN_STOPPED")
            }
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        Log.i(TAG, "DIAG LEARN_STOP_REQUESTED")
        try {
            serverSocket?.close()
        } catch (_: Throwable) {
        }
        serverJob?.cancel()
        serverJob = null
    }

    /**
     * Parses one frame line. Pure function so JVM tests can
     * validate it without Android mocks.
     */
    fun parseFrame(line: String): CapturedFrame? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("{")) return null
        val carrierMatch = Regex("\"carrierHz\"\\s*:\\s*(\\d+)").find(trimmed)
            ?: Regex("\"carrier\"\\s*:\\s*(\\d+)").find(trimmed)
            ?: return null
        val patternMatch = Regex("\"pattern\"\\s*:\\s*\\[([0-9,\\s]+)\\]").find(trimmed)
            ?: return null
        val carrierHz = carrierMatch.groupValues[1].trim().toIntOrNull() ?: return null
        val pattern = patternMatch.groupValues[1]
            .split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .toIntArray()
        if (carrierHz <= 0 || pattern.isEmpty() || pattern.any { it <= 0 }) return null
        return CapturedFrame(carrierHz = carrierHz, pattern = pattern)
    }
}