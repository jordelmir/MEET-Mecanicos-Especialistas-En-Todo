package com.elysium369.meet.core.utils

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import com.elysium369.meet.core.ai.GeminiDiagnostic
import com.elysium369.meet.core.obd.ObdSession
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.core.trips.TripManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class LocalShellManager(
    private val appContext: Context,
    private val geminiDiagnostic: GeminiDiagnostic,
    private val obdSession: ObdSession,
    private val tripManager: TripManager,
    private val scope: CoroutineScope
) {
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var readerJob: Job? = null
    private var controlServer: LocalControlServer? = null

    private val shellMutex = Mutex()
    private val isStopping = AtomicBoolean(false)
    @Volatile private var currentSessionId: String = ""
    private var lastRestartTime = 0L
    private var restartCount = 0
    private var lastRestartAttempt = 0L

    private val _activeDistro = MutableStateFlow("android")
    val activeDistro: StateFlow<String> = _activeDistro.asStateFlow()

    private val _installedDistros = MutableStateFlow<Set<String>>(setOf("android"))
    val installedDistros: StateFlow<Set<String>> = _installedDistros.asStateFlow()

    private val _terminalLines = MutableStateFlow<List<String>>(
        listOf(
            "⚡ Elysium Vanguard Android Terminal Shell v2.0 (Cyber-Termux)",
            "Sustituto avanzado de Termux para desarrollo e IA.",
            "Inicializando directorio de trabajo privado (sandbox)...",
            ""
        )
    )
    val terminalLines: StateFlow<List<String>> = _terminalLines.asStateFlow()

    init {
        setupDirectories()
        startControlServer()
        startShell()
        checkAndInstallBusybox()
    }

    private fun setupDirectories() {
        try {
            val binDir = File(appContext.filesDir, "bin")
            val homeDir = File(appContext.filesDir, "home")
            val tmpDir = File(appContext.filesDir, "tmp")
            if (!binDir.exists()) binDir.mkdirs()
            if (!homeDir.exists()) homeDir.mkdirs()
            if (!tmpDir.exists()) tmpDir.mkdirs()
        } catch (e: Exception) {
            Log.e("LocalShellManager", "Error setting up directories: ${e.message}")
        }
    }

    private fun startControlServer() {
        try {
            controlServer = LocalControlServer(appContext, geminiDiagnostic, obdSession, tripManager)
            controlServer?.start()
        } catch (e: Exception) {
            Log.e("LocalShellManager", "Failed to start local control server: ${e.message}")
        }
    }

    fun startShell() {
        scope.launch(Dispatchers.IO) {
            shellMutex.withLock {
                startShellInternal()
            }
        }
    }

    private suspend fun startShellInternal() {
        stopShellInternal(stopControlServer = false)
        try {
            val binDir = File(appContext.filesDir, "bin")
            val homeDir = File(appContext.filesDir, "home")
            
            val shellCommand = if (_activeDistro.value == "android") {
                "/system/bin/sh"
            } else {
                val bootScript = File(binDir, _activeDistro.value)
                if (bootScript.exists() && isDistroInstalled(_activeDistro.value)) {
                    bootScript.absolutePath
                } else {
                    "/system/bin/sh"
                }
            }
            
            val builder = ProcessBuilder(shellCommand)
                .directory(homeDir)
                .redirectErrorStream(true)
            
            // Inject environment variables
            val env = builder.environment()
            val currentPath = env["PATH"] ?: "/sbin:/system/sbin:/system/bin:/system/xbin"
            env["PATH"] = "${binDir.absolutePath}:$currentPath"
            env["HOME"] = homeDir.absolutePath
            env["TMPDIR"] = File(appContext.filesDir, "tmp").absolutePath
            env["PROOT_TMP_DIR"] = File(appContext.filesDir, "tmp").absolutePath
            env["PROOT_LOADER"] = File(appContext.applicationInfo.nativeLibraryDir, "libproot_loader.so").absolutePath
            env["LD_LIBRARY_PATH"] = "${appContext.applicationInfo.nativeLibraryDir}:/system/lib64:/system/lib:/vendor/lib64:/vendor/lib"
            
            val sessionId = UUID.randomUUID().toString()
            currentSessionId = sessionId
            isStopping.set(false)
            
            Log.d("LocalShellManager", "[$sessionId] Starting shell: $shellCommand")
            val proc = builder.start()
            process = proc
            writer = BufferedWriter(OutputStreamWriter(proc.outputStream))

            readerJob = scope.launch(Dispatchers.IO) {
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                try {
                    var line: String?
                    while (coroutineContext.isActive) {
                        line = try {
                            reader.readLine()
                        } catch (e: IOException) {
                            if (currentSessionId == sessionId && isStopping.get()) {
                                Log.d("LocalShellManager", "[$sessionId] Stream closed during shutdown (expected)")
                            } else {
                                Log.e("LocalShellManager", "[$sessionId] Error reading from shell: ${e.message}")
                            }
                            null
                        }
                        if (line == null) break
                        if (currentSessionId == sessionId) {
                            appendOutput(line)
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        Log.d("LocalShellManager", "[$sessionId] Reader job cancelled")
                    } else {
                        Log.e("LocalShellManager", "[$sessionId] Exception in reader loop: ${e.message}", e)
                        if (currentSessionId == sessionId) {
                            appendOutput("[Shell Error: ${e.message}]")
                        }
                    }
                } finally {
                    try {
                        reader.close()
                    } catch (_: Exception) {}
                    
                    if (currentSessionId == sessionId) {
                        val exitCode = try { proc.exitValue() } catch (e: IllegalThreadStateException) { null }
                        if (exitCode != null) {
                            appendOutput("[Proceso finalizado con código: $exitCode]")
                            if (!isStopping.get()) {
                                Log.w("LocalShellManager", "[$sessionId] Shell process exited unexpectedly with code $exitCode")
                                handleUnexpectedExit(sessionId)
                            }
                        } else {
                            appendOutput("[Proceso finalizado]")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("LocalShellManager", "Failed to start shell: ${e.message}", e)
            _terminalLines.update { it + "Error iniciando shell: ${e.message}" }
        }
    }

    private suspend fun stopShellInternal(stopControlServer: Boolean) {
        isStopping.set(true)
        val proc = process
        val w = writer
        val job = readerJob

        try {
            w?.close()
        } catch (e: Exception) {
            Log.e("LocalShellManager", "Error closing shell writer: ${e.message}")
        }
        writer = null

        if (proc != null) {
            val exitedGracefully = withContext(Dispatchers.IO) {
                try {
                    var count = 0
                    while (count < 15) {
                        try {
                            proc.exitValue()
                            return@withContext true
                        } catch (e: IllegalThreadStateException) {
                            delay(100)
                            count++
                        }
                    }
                    false
                } catch (e: Exception) {
                    false
                }
            }
            if (!exitedGracefully) {
                Log.w("LocalShellManager", "Process did not exit gracefully; destroying...")
                proc.destroy()
                withContext(Dispatchers.IO) {
                    try {
                        var count = 0
                        while (count < 10) {
                            try {
                                proc.exitValue()
                                break
                            } catch (e: IllegalThreadStateException) {
                                delay(50)
                                count++
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        process = null

        job?.cancel()
        readerJob = null

        if (stopControlServer) {
            Log.d("LocalShellManager", "Stopping local control server")
            controlServer?.stop()
        }
    }

    fun stopShell(stopControlServer: Boolean = false) {
        val active = scope.isActive
        if (!active) {
            runBlocking(Dispatchers.IO) {
                shellMutex.withLock {
                    isStopping.set(true)
                    try {
                        writer?.close()
                    } catch (_: Exception) {}
                    process?.destroy()
                    process = null
                    writer = null
                    readerJob?.cancel()
                    readerJob = null
                    if (stopControlServer) {
                        controlServer?.stop()
                    }
                    isStopping.set(false)
                }
            }
        } else {
            scope.launch(Dispatchers.IO) {
                shellMutex.withLock {
                    stopShellInternal(stopControlServer)
                }
            }
        }
    }

    fun restartShell() {
        val now = System.currentTimeMillis()
        if (now - lastRestartAttempt < 2000) {
            Log.d("LocalShellManager", "Restart shell request ignored due to debouncing")
            return
        }
        lastRestartAttempt = now
        appendOutput("[Elysium Vanguard-Termux] Reiniciando la terminal...")
        startShell()
    }

    private fun handleUnexpectedExit(sessionId: String) {
        if (currentSessionId != sessionId) return
        val now = System.currentTimeMillis()
        if (now - lastRestartTime < 10000) {
            restartCount++
        } else {
            restartCount = 1
        }
        lastRestartTime = now
        
        if (restartCount > 3) {
            appendOutput("[Elysium Vanguard-Termux] Reinicios automáticos deshabilitados para evitar bucle infinito.")
            return
        }
        
        appendOutput("[Elysium Vanguard-Termux] Reintentando iniciar la consola en 2 segundos...")
        scope.launch {
            delay(2000)
            if (currentSessionId == sessionId) {
                startShell()
            }
        }
    }

    fun isDistroInstalled(distro: String): Boolean {
        if (distro == "android") return true
        val distroDir = File(appContext.filesDir, distro)
        return distroDir.exists() && (distroDir.list()?.size ?: 0) > 1
    }

    fun updateInstalledDistros() {
        val set = mutableSetOf("android")
        listOf("alpine", "debian", "ubuntu").forEach { distro ->
            if (isDistroInstalled(distro)) {
                set.add(distro)
            }
        }
        _installedDistros.value = set
    }

    fun switchDistro(distro: String) {
        val validDistros = listOf("android", "alpine", "debian", "ubuntu")
        if (distro !in validDistros) return
        _activeDistro.value = distro
        
        val capName = when (distro) {
            "android" -> "Android Host"
            "alpine" -> "Alpine Linux"
            "debian" -> "Debian GNU/Linux"
            "ubuntu" -> "Ubuntu Linux"
            else -> distro
        }
        
        _terminalLines.update {
            listOf(
                "⚡ Entorno cambiado a: $capName",
                "Conectando a la terminal virtual...",
                ""
            )
        }
        
        startShell()
    }

    fun executeCommand(command: String) {
        val cmdTrimmed = command.trim()
        if (cmdTrimmed.isEmpty()) return

        // Intercept Special Install Nodejs command
        if (cmdTrimmed == "pkg install nodejs" || cmdTrimmed == "pkg install node") {
            _terminalLines.update { it + "❯ $command" }
            _terminalLines.update { it + "[Elysium Vanguard-Termux] Android 10+ (targetSDK 34) restringe la ejecución de binarios ELF descargados dinámicamente." }
            _terminalLines.update { it + "[Elysium Vanguard-Termux] Para ejecutar Node.js, Claude Code o Gemini CLI y conectarse a esta APK:" }
            _terminalLines.update { it + "  1. Instale Termux en su dispositivo desde F-Droid." }
            _terminalLines.update { it + "  2. En Termux ejecute: pkg install nodejs" }
            _terminalLines.update { it + "  3. Instale sus herramientas globales (ej. npm install -g @google/generative-ai)." }
            _terminalLines.update { it + "  4. Ejecute sus herramientas apuntando al servidor local de esta app en http://127.0.0.1:8082" }
            _terminalLines.update { it + "     Ejemplo: curl -X POST -d \"SELECT * FROM trips\" http://127.0.0.1:8082/api/db" }
            return
        }

        // Intercept Special Install Linux command
        if (cmdTrimmed.startsWith("pkg install ")) {
            val distro = cmdTrimmed.substring(12).trim().lowercase()
            if (distro == "alpine" || distro == "linux" || distro == "ubuntu" || distro == "debian") {
                _terminalLines.update { it + "❯ $command" }
                val targetDistro = if (distro == "linux") "alpine" else distro
                val capName = when (targetDistro) {
                    "alpine" -> "Alpine"
                    "debian" -> "Debian"
                    "ubuntu" -> "Ubuntu"
                    else -> "Linux"
                }
                _terminalLines.update { it + "[Elysium Vanguard-Termux] Iniciando instalación de $capName..." }
                
                val downloadUrl = when (targetDistro) {
                    "alpine" -> "https://dl-cdn.alpinelinux.org/alpine/v3.19/releases/aarch64/alpine-minirootfs-3.19.1-aarch64.tar.gz"
                    "debian" -> "https://github.com/termux/proot-distro/releases/download/v4.17.3/debian-bookworm-aarch64-pd-v4.17.3.tar.xz"
                    "ubuntu" -> "http://cdimage.ubuntu.com/ubuntu-base/releases/22.04/release/ubuntu-base-22.04-base-arm64.tar.gz"
                    else -> ""
                }
                
                val archiveName = when (targetDistro) {
                    "alpine" -> "alpine.tar.gz"
                    "debian" -> "debian.tar.xz"
                    "ubuntu" -> "ubuntu.tar.gz"
                    else -> "distro.tar.gz"
                }
                val sizeStr = when (targetDistro) {
                    "alpine" -> "~3.2MB"
                    "debian" -> "~40.9MB"
                    "ubuntu" -> "~26.3MB"
                    else -> ""
                }
                
                _terminalLines.update { it + "[Elysium Vanguard-Termux] Descargando $capName rootfs ($sizeStr)..." }
                
                scope.launch(Dispatchers.IO) {
                    try {
                        val cacheDir = appContext.cacheDir
                        val distroArchive = File(cacheDir, archiveName)
                        val distroDir = File(appContext.filesDir, targetDistro)
                        
                        downloadBinary(downloadUrl, distroArchive)
                        appendOutput("[Elysium Vanguard-Termux] Descarga completada. Extrayendo rootfs...")
                        
                        if (distroDir.exists()) {
                            distroDir.deleteRecursively()
                        }
                        distroDir.mkdirs()
                        val isXz = archiveName.endsWith(".xz")
                        val tarFlags = if (isXz) "-xJf" else "-xzf"
                        
                        val nativeLibProot = File(appContext.applicationInfo.nativeLibraryDir, "libproot.so")
                        val nativeLibBusybox = File(appContext.applicationInfo.nativeLibraryDir, "libbusybox.so")
                        
                        if (!nativeLibProot.exists() || !nativeLibBusybox.exists()) {
                            throw IOException("Falta libproot.so o libbusybox.so en las librerías nativas de la aplicación.")
                        }
                        
                        val pb = ProcessBuilder(
                            nativeLibProot.absolutePath,
                            "--link2symlink",
                            "-r", distroDir.absolutePath,
                            "-b", "${nativeLibBusybox.absolutePath}:/tar",
                            "-b", "${appContext.cacheDir.absolutePath}:/cache",
                            "-0",
                            "-w", "/",
                            "/tar", tarFlags, "/cache/$archiveName", "-C", "/"
                        )
                        pb.environment()["PROOT_TMP_DIR"] = File(appContext.filesDir, "tmp").absolutePath
                        pb.environment()["PROOT_LOADER"] = File(appContext.applicationInfo.nativeLibraryDir, "libproot_loader.so").absolutePath
                        val proc = pb.start()
                        
                        val exitCode = proc.waitFor()
                        if (exitCode != 0) {
                            throw IOException("La extracción falló con código de salida: $exitCode")
                        }
                        
                        // Create resolv.conf for DNS
                        val resolvConf = File(distroDir, "etc/resolv.conf")
                        resolvConf.parentFile?.mkdirs()
                        val dnsText = getSystemDnsServers().joinToString("\n") { "nameserver $it" } + "\n"
                        resolvConf.writeText(dnsText)
                        
                        distroArchive.delete()
                        
                        // Re-create CLI scripts to include boot script
                        createCliScripts(File(appContext.filesDir, "bin"))
                        updateInstalledDistros()
                        
                        appendOutput("[Elysium Vanguard-Termux] ¡$capName instalado con éxito!")
                        appendOutput("[Elysium Vanguard-Termux] Escribe '$targetDistro' para iniciar el contenedor.")
                    } catch (e: Exception) {
                        appendOutput("[Elysium Vanguard-Termux] Error al instalar $capName: ${e.message}")
                    }
                }
                return
            }
        }

        // Intercept Special Database Query CLI
        if (cmdTrimmed.startsWith("db ") || cmdTrimmed.startsWith("meet-db ")) {
            _terminalLines.update { it + "❯ $command" }
            val sql = cmdTrimmed.substring(if (cmdTrimmed.startsWith("db ")) 3 else 8).trim().removeSurrounding("\"").removeSurrounding("'")
            executeDbQuery(sql)
            return
        }

        // Intercept Special Gemini AI CLI
        if (cmdTrimmed.startsWith("ai ") || cmdTrimmed.startsWith("meet-ai ")) {
            _terminalLines.update { it + "❯ $command" }
            val prompt = cmdTrimmed.substring(if (cmdTrimmed.startsWith("ai ")) 3 else 8).trim().removeSurrounding("\"").removeSurrounding("'")
            executeAiQuery(prompt)
            return
        }

        // Normal shell command execution
        scope.launch(Dispatchers.IO) {
            shellMutex.withLock {
                val w = writer
                if (w == null || process?.isAlive != true) {
                    _terminalLines.update { it + "❯ $command" }
                    _terminalLines.update { it + "[Error: La consola no está activa. Pulsa REINICIAR ENTORNOS para reactivarla.]" }
                    return@withLock
                }
                try {
                    _terminalLines.update { it + "❯ $command" }
                    w.write(command + "\n")
                    w.flush()
                } catch (e: Exception) {
                    _terminalLines.update { it + "[Error ejecutando: ${e.message}]" }
                }
            }
        }
    }

    private fun checkAndInstallBusybox() {
        scope.launch(Dispatchers.IO) {
            val binDir = File(appContext.filesDir, "bin")
            val nativeLibBusybox = File(appContext.applicationInfo.nativeLibraryDir, "libbusybox.so")
            
            if (!nativeLibBusybox.exists()) {
                appendOutput("[Elysium Vanguard-Termux] Error: No se encontró libbusybox.so en las librerías nativas.")
                appendOutput("[Elysium Vanguard-Termux] Se utilizará la consola base del sistema.")
                return@launch
            }
            
            appendOutput("[Elysium Vanguard-Termux] Inicializando entorno de comandos de Android...")
            try {
                if (binDir.exists()) {
                    binDir.listFiles()?.forEach { it.delete() }
                } else {
                    binDir.mkdirs()
                }
                
                val busyboxSymlink = File(binDir, "busybox")
                try {
                    android.system.Os.symlink(nativeLibBusybox.absolutePath, busyboxSymlink.absolutePath)
                } catch (e: Exception) {
                    Log.e("LocalShellManager", "Failed to create symlink for busybox: ${e.message}")
                }

                appendOutput("[Elysium Vanguard-Termux] Instalando enlaces simbólicos de utilidades en bin/...")
                val proc = ProcessBuilder(nativeLibBusybox.absolutePath, "--install", "-s", binDir.absolutePath)
                    .directory(binDir)
                    .start()
                val exitCode = proc.waitFor()
                
                createCliScripts(binDir)
                updateInstalledDistros()
                if (exitCode == 0) {
                    appendOutput("[Elysium Vanguard-Termux] ¡BusyBox y comandos personalizados inicializados con éxito!")
                } else {
                    appendOutput("[Elysium Vanguard-Termux] Advertencia: BusyBox retornó código de salida $exitCode.")
                }
            } catch (e: Exception) {
                appendOutput("[Elysium Vanguard-Termux] Error al inicializar BusyBox: ${e.message}")
                appendOutput("[Elysium Vanguard-Termux] Se utilizará la consola base del sistema.")
            }
        }
    }

    private fun downloadBinary(urlStr: String, destFile: File) {
        var currentUrl = urlStr
        var conn: java.net.HttpURLConnection? = null
        var redirectCount = 0
        val maxRedirects = 5
        
        while (redirectCount < maxRedirects) {
            val url = java.net.URL(currentUrl)
            conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            
            val status = conn.responseCode
            if (status == java.net.HttpURLConnection.HTTP_MOVED_TEMP || 
                status == java.net.HttpURLConnection.HTTP_MOVED_PERM || 
                status == 307 || status == 308 || status == 303) {
                val newUrl = conn.getHeaderField("Location")
                if (newUrl != null) {
                    currentUrl = newUrl
                    redirectCount++
                    conn.disconnect()
                    continue
                }
            }
            break
        }
        
        val finalConn = conn ?: throw java.io.IOException("Failed to connect to $urlStr")
        if (finalConn.responseCode != java.net.HttpURLConnection.HTTP_OK) {
            throw java.io.IOException("Server returned HTTP response code: ${finalConn.responseCode} for URL: $currentUrl")
        }
        
        finalConn.inputStream.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun createCliScripts(binDir: File) {
        try {
            // meet-db (alias db)
            val dbFile = File(binDir, "db")
            dbFile.writeText("""
                #!/system/bin/sh
                if [ -z "$1" ]; then
                    echo "Uso: db \"SQL_QUERY\""
                    exit 1
                fi
                if command -v curl >/dev/null 2>&1; then
                    curl -s -X POST -d "$1" http://127.0.0.1:8082/api/db
                else
                    wget -qO- --post-data="$1" http://127.0.0.1:8082/api/db
                fi
            """.trimIndent().trim())
            Runtime.getRuntime().exec("chmod 755 ${dbFile.absolutePath}").waitFor()

            // meet-ai (alias ai)
            val aiFile = File(binDir, "ai")
            aiFile.writeText("""
                #!/system/bin/sh
                if [ -z "$1" ]; then
                    echo "Uso: ai \"PREGUNTA\""
                    exit 1
                fi
                if command -v curl >/dev/null 2>&1; then
                    curl -s -X POST -d "$1" http://127.0.0.1:8082/api/ai
                else
                    wget -qO- --post-data="$1" http://127.0.0.1:8082/api/ai
                fi
            """.trimIndent().trim())
            Runtime.getRuntime().exec("chmod 755 ${aiFile.absolutePath}").waitFor()

            // meet-telemetry (alias telemetry)
            val telFile = File(binDir, "telemetry")
            telFile.writeText("""
                #!/system/bin/sh
                if command -v curl >/dev/null 2>&1; then
                    curl -s http://127.0.0.1:8082/api/telemetry
                else
                    wget -qO- http://127.0.0.1:8082/api/telemetry
                fi
            """.trimIndent().trim())
            Runtime.getRuntime().exec("chmod 755 ${telFile.absolutePath}").waitFor()

            // meet-obd (alias obd-send)
            val obdFile = File(binDir, "obd-send")
            obdFile.writeText("""
                #!/system/bin/sh
                if [ -z "$1" ]; then
                    echo "Uso: obd-send \"COMMAND\""
                    exit 1
                fi
                if command -v curl >/dev/null 2>&1; then
                    curl -s -X POST -d "$1" http://127.0.0.1:8082/api/obd
                else
                    wget -qO- --post-data="$1" http://127.0.0.1:8082/api/obd
                fi
            """.trimIndent().trim())
            Runtime.getRuntime().exec("chmod 755 ${obdFile.absolutePath}").waitFor()

            // node helper script
            val nodeHelper = File(binDir, "node")
            nodeHelper.writeText("""
                #!/system/bin/sh
                echo "[Elysium Vanguard-Termux] Android 10+ (targetSDK 34) impide ejecutar Node directamente en el sandbox del APK."
                echo "Para usar Node.js/Claude Code/Gemini CLI con los datos de esta app:"
                echo "1. Instale la app Termux desde F-Droid."
                echo "2. Ejecute en Termux: pkg install nodejs"
                echo "3. Use las APIs locales de esta app desde su terminal Termux:"
                echo "   - Telemetria: http://127.0.0.1:8082/api/telemetry"
                echo "   - Base de Datos (SQL): http://127.0.0.1:8082/api/db"
                echo "   - Comandos OBD: http://127.0.0.1:8082/api/obd"
            """.trimIndent().trim())
            Runtime.getRuntime().exec("chmod 755 ${nodeHelper.absolutePath}").waitFor()

            // npm helper script
            val npmHelper = File(binDir, "npm")
            npmHelper.writeText("""
                #!/system/bin/sh
                echo "[Elysium Vanguard-Termux] Use npm desde la app Termux en su dispositivo."
                echo "Una vez instalado Termux, ejecute: pkg install nodejs && npm install -g <paquete>"
            """.trimIndent().trim())
            Runtime.getRuntime().exec("chmod 755 ${npmHelper.absolutePath}").waitFor()

            // npx helper script
            val npxHelper = File(binDir, "npx")
            npxHelper.writeText("""
                #!/system/bin/sh
                echo "[Elysium Vanguard-Termux] Use npx desde la app Termux en su dispositivo."
                echo "Una vez instalado Termux, ejecute: pkg install nodejs && npx <comando>"
            """.trimIndent().trim())
            Runtime.getRuntime().exec("chmod 755 ${npxHelper.absolutePath}").waitFor()

            // alpine, debian & ubuntu boot scripts
            val nativeLibProot = File(appContext.applicationInfo.nativeLibraryDir, "libproot.so")
            val distros = listOf("alpine", "debian", "ubuntu")
            
            for (distro in distros) {
                val distroDir = File(appContext.filesDir, distro)
                val bootFile = File(binDir, distro)
                
                if (distroDir.exists() && nativeLibProot.exists()) {
                    val dnsSetupLines = (listOf("8.8.8.8", "8.8.4.4", "1.1.1.1") + getSystemDnsServers()).distinct().joinToString("\n") {
                        "echo \"nameserver $it\" >> ${distroDir.absolutePath}/etc/resolv.conf"
                    }
                    bootFile.writeText("""
                        #!/system/bin/sh
                        export PROOT_TMP_DIR=${File(appContext.filesDir, "tmp").absolutePath}
                        export PROOT_LOADER=${File(appContext.applicationInfo.nativeLibraryDir, "libproot_loader.so").absolutePath}
                        export LD_LIBRARY_PATH=${appContext.applicationInfo.nativeLibraryDir}
                        unset ANDROID_DATA
                        unset ANDROID_ROOT
                        rm -f ${distroDir.absolutePath}/etc/resolv.conf
                        $dnsSetupLines
                        
                        if [ $# -gt 0 ]; then
                            exec ${nativeLibProot.absolutePath} \
                                --link2symlink \
                                -0 \
                                -w /root \
                                -r ${distroDir.absolutePath} \
                                -b /dev \
                                -b /sys \
                                -b /proc \
                                -b ${binDir.absolutePath}:/bin/meet \
                                /bin/sh -c 'export PATH=/bin/meet:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; export HOME=/root; cd /root; exec "$@"' -- "$@"
                        else
                            exec ${nativeLibProot.absolutePath} \
                                --link2symlink \
                                -0 \
                                -w /root \
                                -r ${distroDir.absolutePath} \
                                -b /dev \
                                -b /sys \
                                -b /proc \
                                -b ${binDir.absolutePath}:/bin/meet \
                                /bin/sh -c 'export PATH=/bin/meet:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; export HOME=/root; cd /root; exec /bin/sh'
                        fi
                    """.trimIndent().trim())
                    Runtime.getRuntime().exec("chmod 755 ${bootFile.absolutePath}").waitFor()
                } else {
                    val capName = if (distro == "alpine") "Alpine" else if (distro == "debian") "Debian" else "Ubuntu"
                    bootFile.writeText("""
                        #!/system/bin/sh
                        echo "[Elysium Vanguard-Termux] $capName no está instalado."
                        echo "Ejecute el comando: pkg install $distro"
                    """.trimIndent().trim())
                    Runtime.getRuntime().exec("chmod 755 ${bootFile.absolutePath}").waitFor()
                }
            }
            
            // linux boot script
            val linuxBootFile = File(binDir, "linux")
            val installedDistro = distros.firstOrNull { File(appContext.filesDir, it).exists() }
            if (installedDistro != null) {
                linuxBootFile.writeText("""
                    #!/system/bin/sh
                    exec ${File(binDir, installedDistro).absolutePath} "$@"
                """.trimIndent().trim())
            } else {
                linuxBootFile.writeText("""
                    #!/system/bin/sh
                    echo "[Elysium Vanguard-Termux] Ninguna distribución de Linux está instalada."
                    echo "Ejecute el comando: pkg install alpine (o debian, o ubuntu)"
                """.trimIndent().trim())
            }
            Runtime.getRuntime().exec("chmod 755 ${linuxBootFile.absolutePath}").waitFor()

        } catch (e: Exception) {
            Log.e("LocalShellManager", "Error creating CLI scripts: ${e.message}")
        }
    }

    private fun executeDbQuery(sql: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val dbFile = appContext.getDatabasePath("meet_dtc.db")
                if (!dbFile.exists()) {
                    appendOutput("[Error: La base de datos no existe en ${dbFile.absolutePath}]")
                    return@launch
                }
                android.database.sqlite.SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
                ).use { db ->
                    db.rawQuery(sql, null).use { cursor ->
                        val columnCount = cursor.columnCount
                        val headers = (0 until columnCount).map { cursor.getColumnName(it) }
                        
                        val headerLine = headers.joinToString(" | ")
                        val separator = headers.joinToString(" | ") { "---" }
                        appendOutput(headerLine)
                        appendOutput(separator)
                        
                        var count = 0
                        while (cursor.moveToNext() && count < 50) {
                            val row = (0 until columnCount).map {
                                try {
                                    when (cursor.getType(it)) {
                                        android.database.sqlite.SQLiteCursor.FIELD_TYPE_NULL -> "NULL"
                                        android.database.sqlite.SQLiteCursor.FIELD_TYPE_INTEGER -> cursor.getLong(it).toString()
                                        android.database.sqlite.SQLiteCursor.FIELD_TYPE_FLOAT -> cursor.getDouble(it).toString()
                                        android.database.sqlite.SQLiteCursor.FIELD_TYPE_STRING -> cursor.getString(it)
                                        android.database.sqlite.SQLiteCursor.FIELD_TYPE_BLOB -> "[BLOB]"
                                        else -> "UNKNOWN"
                                    }
                                } catch (e: Exception) {
                                    "ERR"
                                }
                            }
                            appendOutput(row.joinToString(" | "))
                            count++
                        }
                        appendOutput("[Filas devueltas: $count]")
                    }
                }
            } catch (e: Exception) {
                appendOutput("[Error DB: ${e.message}]")
            }
        }
    }

    private fun executeAiQuery(prompt: String) {
        scope.launch(Dispatchers.IO) {
            appendOutput("[AI Engine] Consultando a Gemini Pro...")
            try {
                var enrichedPrompt = prompt
                val live = obdSession.liveData.value
                if (live.isNotEmpty()) {
                    val telemetryStr = live.entries.joinToString(", ") { "${it.key}: ${it.value}" }
                    enrichedPrompt += "\n\n[Contexto de Telemetría OBD-II en vivo: $telemetryStr]"
                }
                
                val chatMsg = com.elysium369.meet.core.ai.ChatMessage("user", enrichedPrompt)
                val result = geminiDiagnostic.chat(listOf(chatMsg), "Elysium Vanguard Console Environment", emptyMap())
                appendOutput("[AI Response]")
                result.split("\n").forEach { appendOutput(it) }
            } catch (e: Exception) {
                appendOutput("[AI Error: ${e.message}]")
            }
        }
    }

    private fun appendOutput(text: String) {
        _terminalLines.update {
            val list = it.toMutableList()
            list.add(text)
            if (list.size > 1000) list.removeAt(0)
            list
        }
    }

    fun clearTerminal() {
        _terminalLines.value = listOf("❯ ")
    }

    private fun getSystemDnsServers(): List<String> {
        val servers = mutableListOf<String>()
        try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.activeNetwork?.let { network ->
                cm.getLinkProperties(network)?.dnsServers?.forEach { dns ->
                    dns.hostAddress?.let { servers.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.e("LocalShellManager", "Error getting system DNS: ${e.message}")
        }
        if (servers.isEmpty()) {
            servers.add("8.8.8.8")
            servers.add("8.8.4.4")
        }
        return servers
    }
}

class LocalControlServer(
    private val appContext: Context,
    private val geminiDiagnostic: GeminiDiagnostic,
    private val obdSession: ObdSession,
    private val tripManager: TripManager
) {
    private val serverScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            handleAsyncServerFailure(error)
        }
    )
    private var server: ApplicationEngine? = null

    @Synchronized
    fun start() {
        if (server != null) return
        if (!isPortAvailable(CONTROL_PORT)) {
            Log.w("LocalControlServer", "Port $CONTROL_PORT is already in use; local control server disabled")
            return
        }

        try {
            val engine = serverScope.embeddedServer(CIO, port = CONTROL_PORT, host = CONTROL_HOST) {
                routing {
                    get("/api/telemetry") {
                        try {
                            val live = obdSession.liveData.value
                            val trip = tripManager.currentTrip
                            val json = JSONObject().apply {
                                put("status", if (obdSession.state.value == ObdState.CONNECTED) "connected" else "disconnected")
                                put("rpm", live["010C"] ?: 0f)
                                put("speed", live["010D"] ?: 0f)
                                put("coolantTemp", live["0105"] ?: 0f)
                                put("voltage", live["BATTERY_VOLTAGE"] ?: live["12V"] ?: 13.8f)
                                put("ecoScore", trip?.ecoScore ?: 100)
                                put("distanceKm", trip?.distanceKm ?: 0f)
                                put("durationSeconds", trip?.durationSeconds ?: 0)
                                
                                val liveDataJson = JSONObject()
                                live.forEach { (k, v) -> liveDataJson.put(k, v) }
                                put("liveData", liveDataJson)
                            }
                            call.respondText(json.toString(), ContentType.Application.Json)
                        } catch (e: Exception) {
                            call.respondText("{\"error\":\"${e.message}\"}", ContentType.Application.Json, HttpStatusCode.InternalServerError)
                        }
                    }
                    
                    post("/api/db") {
                        try {
                            val sql = call.receiveText()
                            if (sql.isBlank()) {
                                call.respondText("{\"error\":\"Missing SQL statement\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                                return@post
                            }
                            val dbFile = appContext.getDatabasePath("meet_dtc.db")
                            val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                                dbFile.absolutePath,
                                null,
                                android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
                            )
                            val jsonArray = JSONArray()
                            db.use { database ->
                                database.rawQuery(sql, null).use { cursor ->
                                    val columnCount = cursor.columnCount
                                    while (cursor.moveToNext()) {
                                        val rowObj = JSONObject()
                                        for (i in 0 until columnCount) {
                                            val colName = cursor.getColumnName(i)
                                            when (cursor.getType(i)) {
                                                android.database.sqlite.SQLiteCursor.FIELD_TYPE_NULL -> rowObj.put(colName, JSONObject.NULL)
                                                android.database.sqlite.SQLiteCursor.FIELD_TYPE_INTEGER -> rowObj.put(colName, cursor.getLong(i))
                                                android.database.sqlite.SQLiteCursor.FIELD_TYPE_FLOAT -> rowObj.put(colName, cursor.getDouble(i))
                                                android.database.sqlite.SQLiteCursor.FIELD_TYPE_STRING -> rowObj.put(colName, cursor.getString(i))
                                                android.database.sqlite.SQLiteCursor.FIELD_TYPE_BLOB -> rowObj.put(colName, "[BLOB]")
                                            }
                                        }
                                        jsonArray.put(rowObj)
                                    }
                                }
                            }
                            call.respondText(jsonArray.toString(), ContentType.Application.Json)
                        } catch (e: Exception) {
                            call.respondText("{\"error\":\"${e.message}\"}", ContentType.Application.Json, HttpStatusCode.InternalServerError)
                        }
                    }
                    
                    post("/api/ai") {
                        try {
                            val prompt = call.receiveText()
                            if (prompt.isBlank()) {
                                call.respondText("{\"error\":\"Missing prompt\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                                return@post
                            }
                            val live = obdSession.liveData.value
                            var enrichedPrompt = prompt
                            if (live.isNotEmpty()) {
                                val telemetryStr = live.entries.joinToString(", ") { "${it.key}: ${it.value}" }
                                enrichedPrompt += "\n\n[Contexto de Telemetría OBD-II en vivo: $telemetryStr]"
                            }
                            
                            val chatMsg = com.elysium369.meet.core.ai.ChatMessage("user", enrichedPrompt)
                            val result = geminiDiagnostic.chat(listOf(chatMsg), "Elysium Vanguard Console Environment", emptyMap())
                            val response = JSONObject().apply {
                                put("response", result)
                            }.toString()
                            call.respondText(response, ContentType.Application.Json)
                        } catch (e: Exception) {
                            call.respondText("{\"error\":\"${e.message}\"}", ContentType.Application.Json, HttpStatusCode.InternalServerError)
                        }
                    }
                    
                    post("/api/obd") {
                        try {
                            val command = call.receiveText()
                            if (command.isBlank()) {
                                call.respondText("{\"error\":\"Missing command\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                                return@post
                            }
                            val result = obdSession.sendRawCommand(command)
                            val response = JSONObject().apply {
                                put("command", command)
                                put("response", result)
                            }.toString()
                            call.respondText(response, ContentType.Application.Json)
                        } catch (e: Exception) {
                            call.respondText("{\"error\":\"${e.message}\"}", ContentType.Application.Json, HttpStatusCode.InternalServerError)
                        }
                    }
                }
            }
            server = engine
            engine.start(wait = false)
            Log.d("LocalControlServer", "Server starting on http://$CONTROL_HOST:$CONTROL_PORT")
        } catch (e: Exception) {
            handleServerFailure("Error starting local server", e)
        }
    }

    @Synchronized
    fun stop() {
        runCatching { server?.stop(1000, 2000) }
            .onFailure { Log.w("LocalControlServer", "Error stopping local server", it) }
        server = null
        serverScope.cancel()
    }

    private fun handleAsyncServerFailure(error: Throwable) {
        handleServerFailure("Local server stopped after asynchronous startup failure", error)
    }

    @Synchronized
    private fun handleServerFailure(message: String, error: Throwable) {
        Log.e("LocalControlServer", message, error)
        runCatching { server?.stop(0, 250) }
        server = null
    }

    private fun isPortAvailable(port: Int): Boolean {
        return runCatching {
            ServerSocket().use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(CONTROL_HOST, port))
            }
            true
        }.getOrDefault(false)
    }

    private companion object {
        const val CONTROL_HOST = "127.0.0.1"
        const val CONTROL_PORT = 8082
    }
}
