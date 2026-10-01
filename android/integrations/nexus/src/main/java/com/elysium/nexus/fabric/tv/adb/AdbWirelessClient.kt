package com.elysium.nexus.fabric.tv.adb

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Minimal ADB wire client (protocol v1, port 5555).
 *
 * Implements exactly the packets needed to control an
 * Android TV / Google TV / Fire TV over Wi-Fi:
 *
 * 1. `CNXN` handshake (client banner + features)
 * 2. `AUTH` RSA challenge (TOKEN → SIGNATURE) with
 *    fallback to RSAPUBLICKEY registration when the
 *    device asks for the key first time.
 * 3. `OPEN "shell:input keyevent ..."` one-shot
 *    commands (no shell_v2 dependency).
 *
 * Pure JVM (no Android imports) so it is unit-testable
 * against a fake adbd server. All crypto is JCA (RSA /
 * SHA1withRSA) — matches adbd's key authorization.
 */
object AdbProtocol {

    const val PORT = 5555
    const val VERSION = 0x01000001
    const val MAXDATA = 0x00100000
    const val CLIENT_BANNER =
        "host::features=cmd,shell_v2,stat_v2,ls_v2,fixed_push_mkdir,apex,abb,fixed_push_symlink_timestamp," +
            "abb_exec,remount_shell,track_app,sendrecv_v2,sendrecv_v2_brotli,sendrecv_v2_lz4,sendrecv_v2_zstd," +
            "sendrecv_v2_dry_run_send,openscreen_mdns,devicetracker_proto_format,deveraw,app_info,server_status," +
            "track_mdns,delayed_ack"

    const val A_CNXN = 0x4E584E43
    const val A_OPEN = 0x4E4E504F
    const val A_OKAY = 0x59414B4F
    const val A_CLSE = 0x45534C43
    const val A_WRTE = 0x45545257
    const val A_AUTH = 0x48545541

    const val AUTH_TOKEN = 1
    const val AUTH_SIGNATURE = 2
    const val AUTH_RSAPUBLICKEY = 3

    private const val A_VERSION_MASK = 0xFFFFFFFF
    private const val A_MAXDATA_MASK = 0xFFFFFFFF

    /** 24-byte ADB wire header. */
    data class Header(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val dataLength: Int,
        val dataCheckOverride: Int? = null
    ) {
        val dataCheck: Int get() = dataCheckOverride ?: dataLength
        val magic: Int get() = command xor A_VERSION_MASK.toInt()

        fun toBytes(): ByteArray {
            val b = ByteArray(24)
            var i = 0
            fun put(v: Int) { b[i++] = (v and 0xFF).toByte(); b[i++] = ((v ushr 8) and 0xFF).toByte(); b[i++] = ((v ushr 16) and 0xFF).toByte(); b[i++] = ((v ushr 24) and 0xFF).toByte() }
            put(command); put(arg0); put(arg1); put(dataLength); put(dataCheck); put(magic)
            return b
        }

        companion object {
            fun fromBytes(b: ByteArray, offset: Int = 0): Header = Header(
                command = readInt(b, offset),
                arg0 = readInt(b, offset + 4),
                arg1 = readInt(b, offset + 8),
                dataLength = readInt(b, offset + 12)
            )
            private fun readInt(b: ByteArray, o: Int): Int =
                (b[o].toInt() and 0xFF) or
                    ((b[o + 1].toInt() and 0xFF) shl 8) or
                    ((b[o + 2].toInt() and 0xFF) shl 16) or
                    ((b[o + 3].toInt() and 0xFF) shl 24)
        }
    }

    internal fun commandName(cmd: Int): String = when (cmd) {
        A_CNXN -> "CNXN"; A_OPEN -> "OPEN"; A_OKAY -> "OKAY"
        A_CLSE -> "CLSE"; A_WRTE -> "WRTE"; A_AUTH -> "AUTH"
        else -> "0x%08X".format(cmd)
    }

    /** Cipher name used by adbd for RSA-SHA1. */
    const val RSA_SIGNATURE_ALGORITHM = "SHA1withRSA"
}

/**
 * One ADB connection to a TV's adbd (port 5555).
 * Not thread-safe; use one instance per TV per session.
 */
class AdbWirelessClient(
    private val host: String,
    private val port: Int = AdbProtocol.PORT,
    private val connectTimeoutMs: Int = 2500
) {
    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private var localId = 1

    /** Device's CNXN banner (`device::ro.product...` + features), set after a successful connect. */
    var deviceBanner: String? = null

    val isConnected: Boolean get() = socket?.isConnected == true && socket?.isClosed == false

    /**
     * Open the socket, run the CNXN/AUTH handshake.
     * Returns immediately after the device's CNXN is
     * seen (authorized). Throws on failure.
     *
     * First contact: adbd challenges with a TOKEN; after
     * two signature attempts it advertises AUTH
     * RSAPUBLICKEY (or accepts our proactive key offer),
     * the TV shows the standard "Allow USB debugging"
     * dialog, and once the user accepts adbd sends CNXN.
     * The caller waits up to [authorizationTimeoutMs]
     * for that human step.
     */
    fun connect(authorization: AdbAuthorization, authorizationTimeoutMs: Int = 60_000) {
        val sock = Socket()
        sock.connect(InetSocketAddress(host, port), connectTimeoutMs)
        sock.tcpNoDelay = true
        sock.soTimeout = 6000
        socket = sock
        input = DataInputStream(sock.getInputStream())
        output = DataOutputStream(sock.getOutputStream())

        val banner = AdbProtocol.CLIENT_BANNER.toByteArray(Charsets.UTF_8)
        sendPacket(AdbProtocol.A_CNXN, AdbProtocol.VERSION, AdbProtocol.MAXDATA, banner)

        var signatureAttempts = 0
        var dialogPending = false
        val deadline = System.currentTimeMillis() + authorizationTimeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (dialogPending) sock.soTimeout = authorizationTimeoutMs
            val (header, data) = readPacket() ?: throw IllegalStateException("Connection closed during handshake")
            when (header.command) {
                AdbProtocol.A_CNXN -> {
                    deviceBanner = String(data, Charsets.UTF_8)
                    return // authorized
                }
                AdbProtocol.A_AUTH -> {
                    when (header.arg0) {
                        AdbProtocol.AUTH_TOKEN -> {
                            if (signatureAttempts >= 2) {
                                // Unknown key: stop signing, offer the public key for the pairing dialog.
                                dialogPending = true
                                sendPacket(AdbProtocol.A_AUTH, AdbProtocol.AUTH_RSAPUBLICKEY, 0, authorization.publicKeyAdbFormat())
                            } else {
                                val signature = authorization.sign(data)
                                sendPacket(AdbProtocol.A_AUTH, AdbProtocol.AUTH_SIGNATURE, 0, signature)
                                signatureAttempts++
                            }
                        }
                        AdbProtocol.AUTH_RSAPUBLICKEY -> {
                            // Device wants our public key (first-time pairing).
                            dialogPending = true
                            sendPacket(AdbProtocol.A_AUTH, AdbProtocol.AUTH_RSAPUBLICKEY, 0, authorization.publicKeyAdbFormat())
                        }
                        else -> throw IllegalStateException("Unknown AUTH arg0=${header.arg0}")
                    }
                }
                AdbProtocol.A_CLSE -> throw IllegalStateException("adbd closed the handshake")
                else -> throw IllegalStateException("Unexpected ${AdbProtocol.commandName(header.command)} during handshake")
            }
        }
        throw IllegalStateException("Auth handshake did not settle within ${authorizationTimeoutMs}ms")
    }

    /**
     * Execute a command on the TV's shell and return
     * stdout as text (or "" when the service produced
     * no output).
     *
     * Uses the `shell,v2,raw:` service (no NUL
     * terminator) — the same service the stock adb
     * client opens; `raw` means no shell_v2 binary
     * framing in the output.
     * Reconnects once if the socket died between calls.
     */
    @Synchronized
    fun shell(command: String, authorization: AdbAuthorization, timeoutMs: Int = 5000): String {
        if (!isConnected) connect(authorization)
        val socket = requireNotNull(socket)
        val input = requireNotNull(input)
        val output = requireNotNull(output)
        socket.soTimeout = timeoutMs

        val id = localId++
        sendPacket(AdbProtocol.A_OPEN, id, 0, "shell,v2,raw:$command".toByteArray(Charsets.UTF_8), dataCheck = 0)

        val out = ByteArrayOutputStream()
        var opened = false
        var closed = false
        while (!closed) {
            val (header, data) = readPacket() ?: break
            when (header.command) {
                AdbProtocol.A_OKAY -> opened = true
                AdbProtocol.A_WRTE -> {
                    out.write(data)
                    // ACK so adbd keeps streaming — mirror (arg0, arg1) per OVERVIEW.TXT
                    sendPacket(AdbProtocol.A_OKAY, header.arg0, header.arg1, ByteArray(0))
                }
                AdbProtocol.A_CLSE -> closed = true
                else -> { /* ignore */ }
            }
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    fun disconnect() {
        runCatching { socket?.close() }
        socket = null; input = null; output = null
    }

    private fun sendPacket(command: Int, arg0: Int, arg1: Int, data: ByteArray, dataCheck: Int? = null) {
        val header = AdbProtocol.Header(command, arg0, arg1, data.size, dataCheckOverride = dataCheck)
        val head = header.toBytes()
        output!!.write(head)
        if (data.isNotEmpty()) output!!.write(data)
        output!!.flush()
    }

    private fun readPacket(): Pair<AdbProtocol.Header, ByteArray>? {
        val input = requireNotNull(input)
        val head = ByteArray(24)
        var read = 0
        while (read < 24) {
            val n = input.read(head, read, 24 - read)
            if (n < 0) return null
            if (n == 0) continue
            read += n
        }
        val header = AdbProtocol.Header.fromBytes(head)
        val data = ByteArray(header.dataLength)
        read = 0
        while (read < data.size) {
            val n = input.read(data, read, data.size - read)
            if (n < 0) return null
            if (n == 0) continue
            read += n
        }
        return header to data
    }
}