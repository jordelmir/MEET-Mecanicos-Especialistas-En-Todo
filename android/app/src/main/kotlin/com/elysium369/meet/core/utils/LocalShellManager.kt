package com.elysium369.meet.core.utils

import android.content.Context
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.URL

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

    private val _terminalLines = MutableStateFlow<List<String>>(
        listOf(
            "⚡ MEET Android Terminal Shell v2.0 (Cyber-Termux)",
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
            if (!binDir.exists()) binDir.mkdirs()
            if (!homeDir.exists()) homeDir.mkdirs()
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
        stopShell()
        try {
            val binDir = File(appContext.filesDir, "bin")
            val homeDir = File(appContext.filesDir, "home")
            
            val builder = ProcessBuilder("/system/bin/sh")
                .directory(homeDir)
                .redirectErrorStream(true)
            
            // Inject environment variables
            val env = builder.environment()
            val currentPath = env["PATH"] ?: "/sbin:/system/sbin:/system/bin:/system/xbin"
            env["PATH"] = "${binDir.absolutePath}:$currentPath"
            env["HOME"] = homeDir.absolutePath
            env["TMPDIR"] = appContext.cacheDir.absolutePath
            env["LD_LIBRARY_PATH"] = "/system/lib64:/system/lib:/vendor/lib64:/vendor/lib"
            
            val proc = builder.start()
            process = proc
            writer = BufferedWriter(OutputStreamWriter(proc.outputStream))

            readerJob = scope.launch(Dispatchers.IO) {
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                try {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        appendOutput(line ?: "")
                    }
                } catch (e: Exception) {
                    appendOutput("[Shell Error: ${e.message}]")
                } finally {
                    appendOutput("[Proceso finalizado]")
                }
            }
        } catch (e: Exception) {
            _terminalLines.update { it + "Error iniciando shell: ${e.message}" }
        }
    }

    fun executeCommand(command: String) {
        val cmdTrimmed = command.trim()
        if (cmdTrimmed.isEmpty()) return

        // Intercept Special Install Nodejs command
        if (cmdTrimmed == "pkg install nodejs" || cmdTrimmed == "pkg install node") {
            _terminalLines.update { it + "❯ $command" }
            _terminalLines.update { it + "[MEET-Termux] Android 10+ (targetSDK 34) restringe la ejecución de binarios ELF descargados dinámicamente." }
            _terminalLines.update { it + "[MEET-Termux] Para ejecutar Node.js, Claude Code o Gemini CLI y conectarse a esta APK:" }
            _terminalLines.update { it + "  1. Instale Termux en su dispositivo desde F-Droid." }
            _terminalLines.update { it + "  2. En Termux ejecute: pkg install nodejs" }
            _terminalLines.update { it + "  3. Instale sus herramientas globales (ej. npm install -g @google/generative-ai)." }
            _terminalLines.update { it + "  4. Ejecute sus herramientas apuntando al servidor local de esta app en http://127.0.0.1:8082" }
            _terminalLines.update { it + "     Ejemplo: curl -X POST -d \"SELECT * FROM trips\" http://127.0.0.1:8082/api/db" }
            return
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
        val w = writer ?: return
        scope.launch(Dispatchers.IO) {
            try {
                _terminalLines.update { it + "❯ $command" }
                w.write(command + "\n")
                w.flush()
            } catch (e: Exception) {
                _terminalLines.update { it + "[Error ejecutando: ${e.message}]" }
            }
        }
    }

    private fun checkAndInstallBusybox() {
        scope.launch(Dispatchers.IO) {
            val binDir = File(appContext.filesDir, "bin")
            val nativeLibBusybox = File(appContext.applicationInfo.nativeLibraryDir, "libbusybox.so")
            
            if (!nativeLibBusybox.exists()) {
                appendOutput("[MEET-Termux] Error: No se encontró libbusybox.so en las librerías nativas.")
                appendOutput("[MEET-Termux] Se utilizará la consola base del sistema.")
                return@launch
            }
            
            appendOutput("[MEET-Termux] Inicializando entorno de comandos de Android...")
            try {
                // Clear bin directory to avoid stale symlinks from previous app installations/hashes
                if (binDir.exists()) {
                    binDir.listFiles()?.forEach { it.delete() }
                } else {
                    binDir.mkdirs()
                }
                
                // Create busybox symlink first so busybox command itself works
                val busyboxSymlink = File(binDir, "busybox")
                try {
                    android.system.Os.symlink(nativeLibBusybox.absolutePath, busyboxSymlink.absolutePath)
                } catch (e: Exception) {
                    Log.e("LocalShellManager", "Failed to create symlink for busybox: ${e.message}")
                }

                appendOutput("[MEET-Termux] Instalando enlaces simbólicos de utilidades en bin/...")
                val proc = ProcessBuilder(nativeLibBusybox.absolutePath, "--install", "-s", binDir.absolutePath)
                    .directory(binDir)
                    .start()
                val exitCode = proc.waitFor()
                
                createCliScripts(binDir)
                if (exitCode == 0) {
                    appendOutput("[MEET-Termux] ¡BusyBox y comandos personalizados inicializados con éxito!")
                } else {
                    appendOutput("[MEET-Termux] Advertencia: BusyBox retornó código de salida $exitCode.")
                }
            } catch (e: Exception) {
                appendOutput("[MEET-Termux] Error al inicializar BusyBox: ${e.message}")
                appendOutput("[MEET-Termux] Se utilizará la consola base del sistema.")
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
                echo "[MEET-Termux] Android 10+ (targetSDK 34) impide ejecutar Node directamente en el sandbox del APK."
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
                echo "[MEET-Termux] Use npm desde la app Termux en su dispositivo."
                echo "Una vez instalado Termux, ejecute: pkg install nodejs && npm install -g <paquete>"
            """.trimIndent().trim())
            Runtime.getRuntime().exec("chmod 755 ${npmHelper.absolutePath}").waitFor()

            // npx helper script
            val npxHelper = File(binDir, "npx")
            npxHelper.writeText("""
                #!/system/bin/sh
                echo "[MEET-Termux] Use npx desde la app Termux en su dispositivo."
                echo "Una vez instalado Termux, ejecute: pkg install nodejs && npx <comando>"
            """.trimIndent().trim())
            Runtime.getRuntime().exec("chmod 755 ${npxHelper.absolutePath}").waitFor()

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
                val result = geminiDiagnostic.chat(listOf(chatMsg), "MEET Console Environment", emptyMap())
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

    fun stopShell() {
        readerJob?.cancel()
        try {
            writer?.close()
        } catch (_: Exception) {}
        process?.destroy()
        process = null
        writer = null
        
        controlServer?.stop()
    }
}

class LocalControlServer(
    private val appContext: Context,
    private val geminiDiagnostic: GeminiDiagnostic,
    private val obdSession: ObdSession,
    private val tripManager: TripManager
) {
    private var server: ApplicationEngine? = null
    
    fun start() {
        try {
            server = embeddedServer(CIO, port = 8082, host = "127.0.0.1") {
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
                            val result = geminiDiagnostic.chat(listOf(chatMsg), "MEET Console Environment", emptyMap())
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
            server?.start(wait = false)
            Log.d("LocalControlServer", "Server started on http://127.0.0.1:8082")
        } catch (e: Exception) {
            Log.e("LocalControlServer", "Error starting local server: ${e.message}")
        }
    }
    
    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }
}
