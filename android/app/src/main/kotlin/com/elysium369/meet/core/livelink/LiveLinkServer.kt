package com.elysium369.meet.core.livelink

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.Inet4Address
import java.net.NetworkInterface
import java.time.Duration
import java.util.Collections

/**
 * LiveLinkServer — Embedded Ktor WebSocket server running on the phone.
 * 
 * This is 100% OPT-IN. The server only starts when the user explicitly
 * enables sharing from the LiveLink screen. It can be stopped at any time.
 * 
 * When active, it broadcasts real-time OBD telemetry to any connected
 * browser (the MEET web dashboard) on the same WiFi network.
 */
class LiveLinkServer {

    companion object {
        const val DEFAULT_PORT = 8765
    }

    private var server: ApplicationEngine? = null
    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _connectedClients = MutableStateFlow(0)
    val connectedClients = _connectedClients.asStateFlow()

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl = _serverUrl.asStateFlow()

    private val sessions = Collections.synchronizedSet(mutableSetOf<WebSocketServerSession>())

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    /** Starts the embedded server. Call only after user opt-in. */
    fun start(port: Int = DEFAULT_PORT) {
        if (_isRunning.value) return

        val localIp = getLocalIpAddress() ?: "0.0.0.0"

        server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(WebSockets) {
                pingPeriod = Duration.ofSeconds(15)
                timeout = Duration.ofSeconds(30)
                maxFrameSize = Long.MAX_VALUE
            }
            install(ContentNegotiation) { json(json) }
            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.ContentType)
                allowMethod(HttpMethod.Get)
            }

            routing {
                // Health check endpoint
                get("/") {
                    call.respondText(
                        """{"status":"online","app":"Elysium LiveLink","version":"1.0"}""",
                        ContentType.Application.Json
                    )
                }

                // WebSocket telemetry stream
                webSocket("/live") {
                    sessions.add(this)
                    _connectedClients.value = sessions.size

                    // Send welcome
                    send(Frame.Text(json.encodeToString(LiveLinkMessage(
                        type = "welcome",
                        payload = """{"message":"Conectado a MEET LiveLink"}"""
                    ))))

                    try {
                        for (frame in incoming) {
                            // We mostly broadcast, but can receive commands
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                // Future: handle remote commands
                            }
                        }
                    } catch (_: Exception) {
                    } finally {
                        sessions.remove(this)
                        _connectedClients.value = sessions.size
                    }
                }
            }
        }

        server?.start(wait = false)
        _isRunning.value = true
        _serverUrl.value = "http://$localIp:$port"
    }

    /** Stops the server. User can stop sharing at any time. */
    fun stop() {
        server?.stop(1000, 2000)
        server = null
        sessions.clear()
        _isRunning.value = false
        _connectedClients.value = 0
        _serverUrl.value = null
    }

    /** Broadcasts a telemetry snapshot to all connected browsers. */
    suspend fun broadcastTelemetry(data: TelemetrySnapshot) {
        if (!_isRunning.value || sessions.isEmpty()) return
        val message = json.encodeToString(LiveLinkMessage(
            type = "telemetry",
            payload = json.encodeToString(data)
        ))
        val frame = Frame.Text(message)
        sessions.forEach { session ->
            try { session.send(frame.copy()) } catch (_: Exception) {}
        }
    }

    /** Broadcasts a DTC alert. */
    suspend fun broadcastDtcAlert(dtcs: List<String>) {
        if (!_isRunning.value || sessions.isEmpty()) return
        val message = json.encodeToString(LiveLinkMessage(
            type = "dtc_alert",
            payload = json.encodeToString(dtcs)
        ))
        sessions.forEach { session ->
            try { session.send(Frame.Text(message)) } catch (_: Exception) {}
        }
    }

    private fun getLocalIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress
        } catch (_: Exception) { null }
    }
}

@Serializable
data class LiveLinkMessage(
    val type: String,       // "welcome" | "telemetry" | "dtc_alert"
    val payload: String,    // JSON string of the payload
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class TelemetrySnapshot(
    val rpm: Int = 0,
    val speed: Int = 0,
    val coolantTemp: Int = 0,
    val intakeTemp: Int = 0,
    val throttlePos: Float = 0f,
    val engineLoad: Float = 0f,
    val fuelPressure: Int = 0,
    val timingAdvance: Float = 0f,
    val mafRate: Float = 0f,
    val voltage: Float = 0f,
    val fuelTrim1: Float = 0f,
    val fuelTrim2: Float = 0f,
    val healthScore: Int = -1,
    val activeDtcs: List<String> = emptyList(),
    val vehicleName: String = ""
)
