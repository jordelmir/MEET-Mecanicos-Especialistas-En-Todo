package com.elysium369.meet.core.terminal

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Interactive PTY / Shell Session Controller for Elysium Vanguard Terminal.
 * Spawns an interactive shell inside PRoot (or Host) with bidirectional stdin/stdout
 * streams connected to the ElysiumTerminalEmulator.
 */
class ElysiumInteractivePtySession(
    private val context: Context,
    val distroId: String,
    val emulator: ElysiumTerminalEmulator = ElysiumTerminalEmulator(cols = 80, rows = 28)
) {
    companion object {
        private const val TAG = "ElysiumInteractivePty"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var process: Process? = null
    private var processStdin: OutputStream? = null
    private val isRunning = AtomicBoolean(false)

    private val _sessionActive = MutableStateFlow(false)
    val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()

    // Modifier states (sticky toggles from keyboard bar)
    val isCtrlActive = MutableStateFlow(false)
    val isAltActive = MutableStateFlow(false)

    fun start() {
        if (isRunning.getAndSet(true)) return

        scope.launch {
            try {
                val filesDir = context.filesDir
                val binDir = File(filesDir, "bin")
                val distroDir = File(filesDir, distroId)
                val homeDir = File(filesDir, "home").apply { mkdirs() }
                val nativeLibProot = File(context.applicationInfo.nativeLibraryDir, "libproot.so")
                val isDistro = distroId != "android" && distroDir.exists() && nativeLibProot.exists()

                val builder = if (isDistro) {
                    File(distroDir, "dev/pts").mkdirs()
                    val args = listOf(
                        nativeLibProot.absolutePath,
                        "--link2symlink",
                        "-0",
                        "-w", "/root",
                        "-r", distroDir.absolutePath,
                        "-b", "/dev",
                        "-b", "/dev/pts",
                        "-b", "/sys",
                        "-b", "/proc",
                        "-b", "${binDir.absolutePath}:/bin/meet",
                        "/bin/sh"
                    )
                    ProcessBuilder(args).directory(homeDir)
                } else {
                    ProcessBuilder("/system/bin/sh").directory(homeDir)
                }

                val env = builder.environment()
                env["TERM"] = "xterm-256color"
                env["COLORTERM"] = "truecolor"
                env["COLUMNS"] = emulator.cols.toString()
                env["LINES"] = emulator.rows.toString()
                env["HOME"] = if (isDistro) "/root" else homeDir.absolutePath
                env["LANG"] = "C.UTF-8"
                env["LC_ALL"] = "C.UTF-8"
                env["LD_LIBRARY_PATH"] = "${context.applicationInfo.nativeLibraryDir}:/system/lib64:/system/lib:/vendor/lib64:/vendor/lib"
                env["PROOT_LOADER"] = File(context.applicationInfo.nativeLibraryDir, "libproot_loader.so").absolutePath
                env["PROOT_TMP_DIR"] = File(filesDir, "tmp").apply { mkdirs() }.absolutePath
                env["TMPDIR"] = if (isDistro) "/tmp" else File(filesDir, "tmp").absolutePath
                env["TMP"] = if (isDistro) "/tmp" else File(filesDir, "tmp").absolutePath
                env["TEMP"] = if (isDistro) "/tmp" else File(filesDir, "tmp").absolutePath
                if (isDistro) {
                    env["SSL_CERT_FILE"] = "/etc/ssl/certs/ca-certificates.crt"
                    env["SSL_CERT_DIR"] = "/etc/ssl/certs"
                    env["CURL_CA_BUNDLE"] = "/etc/ssl/certs/ca-certificates.crt"
                    env.remove("ANDROID_DATA")
                    env.remove("ANDROID_ROOT")
                }
                env["PATH"] = if (isDistro) {
                    "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/root/.local/bin:/opt/elysium/bin:/bin/meet"
                } else {
                    "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:${binDir.absolutePath}:/system/bin:/system/xbin"
                }

                builder.redirectErrorStream(true)
                val proc = builder.start()
                process = proc
                processStdin = proc.outputStream
                _sessionActive.value = true

                // Send startup environment export
                writeString("export TERM=xterm-256color; export COLORTERM=truecolor; export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/root/.local/bin:/opt/elysium/bin:/bin/meet\n")
                if (isDistro) {
                    writeString("export PS1='\\[\\033[01;32m\\]root@elysium\\[\\033[00m\\]:\\[\\033[01;34m\\]\\w\\[\\033[00m\\]\\# '\n")
                } else {
                    writeString("export PS1='\\[\\033[01;36m\\]android@host\\[\\033[00m\\]:\\[\\033[01;34m\\]\\w\\[\\033[00m\\]\\$ '\n")
                }

                // Read output stream in background
                val buffer = ByteArray(4096)
                val input: InputStream = proc.inputStream

                while (isRunning.get()) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    val chunk = String(buffer, 0, read, StandardCharsets.UTF_8)
                    emulator.process(chunk)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Interactive shell session error: ${e.message}", e)
                emulator.process("\r\n\u001B[31m[Session Error: ${e.localizedMessage}]\u001B[0m\r\n")
            } finally {
                isRunning.set(false)
                _sessionActive.value = false
                process = null
                processStdin = null
            }
        }
    }

    fun stop() {
        isRunning.set(false)
        _sessionActive.value = false
        try {
            processStdin?.close()
            process?.destroy()
        } catch (ignored: Exception) {}
        scope.cancel()
    }

    fun writeBytes(bytes: ByteArray) {
        scope.launch {
            try {
                processStdin?.write(bytes)
                processStdin?.flush()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write bytes to stdin: ${e.message}")
            }
        }
    }

    fun writeString(str: String) {
        writeBytes(str.toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * Handle key input with active modifiers (Ctrl / Alt).
     */
    fun sendKey(char: Char) {
        val ctrl = isCtrlActive.value
        val alt = isAltActive.value

        // Consume sticky modifiers after key stroke
        if (ctrl) isCtrlActive.value = false
        if (alt) isAltActive.value = false

        if (ctrl) {
            val upper = char.uppercaseChar()
            if (upper in '@'..'_') {
                val ctrlCode = (upper.code - 64).toByte()
                writeBytes(byteArrayOf(ctrlCode))
                return
            }
        }

        if (alt) {
            writeBytes(byteArrayOf(0x1B, char.code.toByte()))
            return
        }

        writeString(char.toString())
    }

    // Special control sequences
    fun sendCtrlC() {
        isCtrlActive.value = false
        writeBytes(byteArrayOf(0x03)) // SIGINT
    }

    fun sendCtrlD() {
        isCtrlActive.value = false
        writeBytes(byteArrayOf(0x04)) // EOF
    }

    fun sendCtrlZ() {
        isCtrlActive.value = false
        writeBytes(byteArrayOf(0x1A)) // SIGTSTP
    }

    fun sendCtrlL() {
        isCtrlActive.value = false
        writeBytes(byteArrayOf(0x0C)) // Form Feed / Redraw Screen
    }

    fun sendCtrlA() {
        isCtrlActive.value = false
        writeBytes(byteArrayOf(0x01)) // Line start
    }

    fun sendCtrlE() {
        isCtrlActive.value = false
        writeBytes(byteArrayOf(0x05)) // Line end
    }

    fun sendCtrlR() {
        isCtrlActive.value = false
        writeBytes(byteArrayOf(0x12)) // Reverse search
    }

    fun sendCtrlU() {
        isCtrlActive.value = false
        writeBytes(byteArrayOf(0x15)) // Erase line
    }

    fun sendCtrlK() {
        isCtrlActive.value = false
        writeBytes(byteArrayOf(0x0B)) // Kill to line end
    }

    fun sendCtrlW() {
        isCtrlActive.value = false
        writeBytes(byteArrayOf(0x17)) // Erase word back
    }

    fun sendEsc() {
        writeBytes(byteArrayOf(0x1B))
    }

    fun sendTab() {
        writeBytes(byteArrayOf(0x09))
    }

    fun sendEnter() {
        writeBytes(byteArrayOf(0x0D))
    }

    fun sendBackspace() {
        writeBytes(byteArrayOf(0x7F))
    }

    fun sendArrowUp() {
        writeBytes(byteArrayOf(0x1B, '['.code.toByte(), 'A'.code.toByte()))
    }

    fun sendArrowDown() {
        writeBytes(byteArrayOf(0x1B, '['.code.toByte(), 'B'.code.toByte()))
    }

    fun sendArrowRight() {
        writeBytes(byteArrayOf(0x1B, '['.code.toByte(), 'C'.code.toByte()))
    }

    fun sendArrowLeft() {
        writeBytes(byteArrayOf(0x1B, '['.code.toByte(), 'D'.code.toByte()))
    }

    fun sendHome() {
        writeBytes(byteArrayOf(0x1B, '['.code.toByte(), 'H'.code.toByte()))
    }

    fun sendEnd() {
        writeBytes(byteArrayOf(0x1B, '['.code.toByte(), 'F'.code.toByte()))
    }

    fun sendPageUp() {
        writeBytes(byteArrayOf(0x1B, '['.code.toByte(), '5'.code.toByte(), '~'.code.toByte()))
    }

    fun sendPageDown() {
        writeBytes(byteArrayOf(0x1B, '['.code.toByte(), '6'.code.toByte(), '~'.code.toByte()))
    }

    fun sendDelete() {
        writeBytes(byteArrayOf(0x1B, '['.code.toByte(), '3'.code.toByte(), '~'.code.toByte()))
    }

    fun toggleCtrl() {
        isCtrlActive.value = !isCtrlActive.value
    }

    fun toggleAlt() {
        isAltActive.value = !isAltActive.value
    }
}
