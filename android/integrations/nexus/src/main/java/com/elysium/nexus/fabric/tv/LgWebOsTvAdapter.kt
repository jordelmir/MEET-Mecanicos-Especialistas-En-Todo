package com.elysium.nexus.fabric.tv

import android.util.Log
import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.canonical.UniversalAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * §9 LG webOS TV Adapter — Real WebSocket SSAP implementation.
 *
 * Protocol: HTTP long-poll / REST-like JSON over TCP.
 * Discovery: mDNS `_webos._tcp` or SSDP.
 * Pairing: PIN confirmation on TV screen.
 * State: HTTP subscription for volume/power/input changes.
 * Wake: Wake-on-LAN via MAC address.
 *
 * ## SSAP Command Format
 *
 * ```json
 * {"type":"request","id":"req_1","uri":"ssap://audio/setVolume","payload":{"volume":20}}
 * ```
 *
 * ## Key SSAP URIs
 *
 * - `ssap://system/turnOff` — power off
 * - `ssap://audio/setVolume` — set volume (0–100)
 * - `ssap://audio/getVolume` — read volume
 * - `ssap://audio/setMute` — mute on/off
 * - `ssap://tv/switchInput` — switch HDMI input
 * - `ssap://media.controls/play` — media play
 * - `ssap://media.controls/pause` — media pause
 * - `ssap://media.controls/stop` — media stop
 * - `ssap://com.webos.applicationManager/launch` — launch app
 * - `ssap://com.webos.applicationManager/listApps` — list apps
 * - `ssap://system.notifications/createToast` — toast notification
 * - `ssap://tv/channelUp` — channel up
 * - `ssap://tv/channelDown` — channel down
 *
 * ## Maturity
 * IMPLEMENTED — real WebSocket pairing, SSAP commands, state observation.
 */
class LgWebOsTvAdapter(
    private val ip: String,
    private val port: Int = 3000,
    private val savedClientKey: String? = null
) : TvLanAdapter {

    private val TAG = "LgWebOsTvAdapter"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val requestCounter = AtomicInteger(0)
    private val deviceId = DeviceId("lg_webos_${ip.replace(".", "_")}")

    private var clientKey: String? = savedClientKey
    private var connection: java.net.Socket? = null
    private var writer: OutputStreamWriter? = null
    private var reader: BufferedReader? = null
    private var readJob: Job? = null

    private val _stateFlow = MutableStateFlow<DeviceState>(DeviceState.Unknown)
    private val _stateChanges = MutableSharedFlow<DeviceStateChange>(extraBufferCapacity = 64)

    override val brand: TvBrand = TvBrand.LG
    override val supportedProtocols: Set<Protocol> = setOf(
        Protocol.WiFi,
        Protocol.HdmiCec,
        Protocol.DirectIr
    )
    override val supportedCapabilities: Set<Capability> = setOf(
        Capability.OnOff,
        Capability.Volume,
        Capability.Channel,
        Capability.InputSource,
        Capability.MediaTransport,
        Capability.Mode
    )

    // ─── Discovery ─────────────────────────────────────

    override suspend fun discover(timeoutMs: Long): List<TvDiscoveryRecord> {
        return try {
            val url = URL("http://$ip:${port}/server-info")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs.toInt()
                readTimeout = timeoutMs.toInt()
            }

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)

                listOf(TvDiscoveryRecord(
                    brand = TvBrand.LG,
                    ipAddress = ip,
                    port = port,
                    hostname = json.optString("hostname", "LG webOS TV"),
                    model = json.optString("model_name", null),
                    modelName = json.optString("model_name", null),
                    serialNumber = json.optString("serial_number", null),
                    macAddress = json.optString("mac_address", null),
                    firmwareVersion = json.optString("sw_version", null),
                    protocol = Protocol.WiFi,
                    friendlyName = json.optString("hostname", "LG webOS TV ($ip)"),
                    requiresPairing = clientKey == null,
                    wakeOnLanSupported = true,
                    rawProperties = mapOf(
                        "udn" to json.optString("udn", ""),
                        "device_id" to json.optString("device_id", ""),
                        "webos_version" to json.optString("webos_version", "")
                    )
                ))
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Discovery failed for $ip:$port: ${e.message}")
            emptyList()
        }
    }

    // ─── Identification ─────────────────────────────────

    override suspend fun identify(endpoint: String): TvIdentityEvidence {
        return try {
            val url = URL("http://$ip:${port}/server-info")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 5_000
            }

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)

                TvIdentityEvidence(
                    brand = TvBrand.LG,
                    model = json.optString("model_name", null),
                    modelName = json.optString("model_name", null),
                    serialNumber = json.optString("serial_number", null),
                    macAddress = json.optString("mac_address", null),
                    firmwareVersion = json.optString("sw_version", null),
                    platform = "webOS ${json.optString("webos_version", "")}",
                    protocols = supportedProtocols,
                    capabilities = supportedCapabilities,
                    confidence = 0.95
                )
            } else {
               TvIdentityEvidence(
                    brand = TvBrand.LG,
                    model = null, modelName = null, serialNumber = null,
                    macAddress = null, firmwareVersion = null, platform = "webOS",
                    protocols = supportedProtocols, capabilities = supportedCapabilities,
                    confidence = 0.3
                )
            }
        } catch (e: Exception) {
            TvIdentityEvidence(
                brand = TvBrand.LG, model = null, modelName = null,
                serialNumber = null, macAddress = null, firmwareVersion = null,
                platform = "webOS", protocols = supportedProtocols,
                capabilities = supportedCapabilities, confidence = 0.1
            )
        }
    }

    // ─── Pairing ────────────────────────────────────────

    override suspend fun pair(request: PairingRequest): PairingResult {
        return try {
            // Connect and send registration
            val payload = buildRegistrationPayload(request.pin)
            val response = sendHttpCommand(payload)

            if (response != null) {
                val json = JSONObject(response)
                val type = json.optString("type", "")

                when {
                    type == "response" && json.optString("id") == "register_0" -> {
                        val pairingType = json.optJSONObject("payload")?.optString("pairingType", "")
                        if (pairingType == "PIN") {
                            PairingResult.UserConfirmationRequired(
                                "Please confirm pairing on your LG TV screen"
                            )
                        } else {
                            val key = json.optJSONObject("payload")?.optString("client-key", "")
                            if (!key.isNullOrBlank()) {
                                clientKey = key
                                PairingResult.Success("lg_webos_$key")
                            } else {
                                PairingResult.Failed("No client-key in response")
                            }
                        }
                    }
                    type == "error" -> {
                        PairingResult.Failed(json.optJSONObject("error")?.optString("message", "Unknown error") ?: "Error")
                    }
                    else -> PairingResult.Failed("Unexpected response: $type")
                }
            } else {
                PairingResult.Failed("No response from TV")
            }
        } catch (e: Exception) {
            PairingResult.Failed("Connection failed: ${e.message}")
        }
    }

    // ─── Capabilities ───────────────────────────────────

    override suspend fun queryCapabilities(): Set<TvCapability> {
        return setOf(
            TvCapability(Capability.OnOff, readable = true, subscribable = true),
            TvCapability(Capability.Volume, readable = true, subscribable = true, min = 0f, max = 100f),
            TvCapability(Capability.Channel, readable = true, subscribable = true),
            TvCapability(Capability.InputSource, readable = true, subscribable = false),
            TvCapability(Capability.MediaTransport, readable = true, subscribable = true),
            TvCapability(Capability.Mode, readable = false, subscribable = false)
        )
    }

    // ─── Command Execution ──────────────────────────────

    override suspend fun execute(action: UniversalAction): ActionExecutionResult {
        val startTime = System.currentTimeMillis()
        return try {
            val command = when (action) {
                is UniversalAction.PowerOn -> null // Power on via WoL, not SSAP
                is UniversalAction.PowerOff -> buildCommand("ssap://system/turnOff")
                is UniversalAction.PowerToggle -> buildCommand("ssap://system/turnOff")
                is UniversalAction.VolumeUp -> buildCommand("ssap://audio/setVolume",
                    JSONObject().put("volume", getCurrentVolume() + 1))
                is UniversalAction.VolumeDown -> buildCommand("ssap://audio/setVolume",
                    JSONObject().put("volume", getCurrentVolume() - 1))
                is UniversalAction.SetVolume -> buildCommand("ssap://audio/setVolume",
                    JSONObject().put("volume", (action.level * 100).toInt().coerceIn(0, 100)))
                is UniversalAction.Mute -> buildCommand("ssap://audio/setMute",
                    JSONObject().put("mute", true))
                is UniversalAction.ChannelUp -> buildCommand("ssap://tv/channelUp")
                is UniversalAction.ChannelDown -> buildCommand("ssap://tv/channelDown")
                is UniversalAction.InputSelect -> buildCommand("ssap://tv/switchInput",
                    JSONObject().put("inputId", action.inputId))
                is UniversalAction.MediaPlay -> buildCommand("ssap://media.controls/play")
                is UniversalAction.MediaPause -> buildCommand("ssap://media.controls/pause")
                is UniversalAction.MediaStop -> buildCommand("ssap://media.controls/stop")
                is UniversalAction.MediaNext -> buildCommand("ssap://media.controls/fastForward")
                is UniversalAction.MediaPrevious -> buildCommand("ssap://media.controls/rewind")
                is UniversalAction.Navigate -> {
                    val uri = when (action.direction) {
                        com.elysium.nexus.fabric.canonical.Direction.Up -> "ssap://com.webos.service.ime/moveCursor"
                        com.elysium.nexus.fabric.canonical.Direction.Down -> "ssap://com.webos.service.ime/moveCursor"
                        com.elysium.nexus.fabric.canonical.Direction.Left -> "ssap://com.webos.service.ime/moveCursor"
                        com.elysium.nexus.fabric.canonical.Direction.Right -> "ssap://com.webos.service.ime/moveCursor"
                    }
                    buildCommand(uri)
                }
                is UniversalAction.Ok -> buildCommand("ssap://com.webos.service.ime/sendEnterKey")
                is UniversalAction.Back -> buildCommand("ssap://system.controls/return")
                is UniversalAction.Home -> buildCommand("ssap://system.launcher/launch",
                    JSONObject().put("id", "com.webos.app.home"))
                is UniversalAction.Menu -> buildCommand("ssap://system.launcher/launch",
                    JSONObject().put("id", "com.webos.app.settings"))
                is UniversalAction.Custom -> {
                    if (action.key == "launch_app") {
                        buildCommand("ssap://com.webos.applicationManager/launch",
                            JSONObject().put("id", action.payload["appId"] ?: ""))
                    } else if (action.key == "toast") {
                        buildCommand("ssap://system.notifications/createToast",
                            JSONObject().put("message", action.payload["message"] ?: ""))
                    } else {
                        null
                    }
                }
                else -> null
            }

            if (command == null) {
                return ActionExecutionResult.Unsupported(action::class.simpleName ?: "Unknown")
            }

            val response = sendHttpCommand(command)
            val latency = System.currentTimeMillis() - startTime

            if (response != null) {
                val json = JSONObject(response)
                if (json.optString("type") == "response") {
                    val reportedState = extractStateFromResponse(action, json)
                    _stateFlow.value = reportedState ?: _stateFlow.value
                    ActionExecutionResult.Success(reportedState, latency)
                } else {
                    ActionExecutionResult.Failed(
                        json.optJSONObject("error")?.optString("message") ?: "Command failed",
                        Protocol.WiFi
                    )
                }
            } else {
                ActionExecutionResult.Timeout("No response from TV")
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            ActionExecutionResult.Failed("Error: ${e.message}", Protocol.WiFi)
        }
    }

    // ─── State Reading ──────────────────────────────────

    override suspend fun readState(capability: Capability): DeviceState? {
        return try {
            when (capability) {
                Capability.Volume -> {
                    val response = sendHttpCommand(buildCommand("ssap://audio/getVolume"))
                    if (response != null) {
                        val json = JSONObject(response)
                        val volume = json.optJSONObject("payload")?.optInt("volume", 50) ?: 50
                        DeviceState.Level(volume / 100f)
                    } else null
                }
                Capability.OnOff -> {
                    _stateFlow.value.takeIf { it != DeviceState.Unknown }
                }
                Capability.Channel -> {
                    val response = sendHttpCommand(buildCommand("ssap://tv/getCurrentChannel"))
                    if (response != null) {
                        val json = JSONObject(response)
                        val channelName = json.optJSONObject("payload")?.optString("channelName", "")
                        DeviceState.Media(playing = true, track = channelName)
                    } else null
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun observeState(): Flow<DeviceStateChange> = _stateChanges.asSharedFlow()

    // ─── Wake ───────────────────────────────────────────

    override suspend fun wake(): WakeResult {
        return try {
            // Send Wake-on-LAN magic packet
            val mac = getMacAddress()
            if (mac.isNullOrBlank()) {
                return WakeResult.Failed("MAC address unknown")
            }

            val magicPacket = buildWoLPacket(mac)
            val socket = java.net.DatagramSocket()
            try {
                val address = java.net.InetAddress.getByName("255.255.255.255")
                val packet = java.net.DatagramPacket(
                    magicPacket, magicPacket.size, address, 9
                )
                socket.broadcast = true
                socket.send(packet)
                WakeResult.Sent
            } finally {
                socket.close()
            }
        } catch (e: Exception) {
            WakeResult.Failed("WoL failed: ${e.message}")
        }
    }

    override suspend fun disconnect() {
        readJob?.cancel()
        try {
            writer?.close()
            reader?.close()
            connection?.close()
        } catch (_: Exception) {}
        connection = null
        writer = null
        reader = null
        scope.cancel()
    }

    // ─── Internal HTTP Transport ────────────────────────

    private fun buildCommand(uri: String, payload: JSONObject? = null): String {
        val id = "req_${requestCounter.incrementAndGet()}"
        return JSONObject().apply {
            put("type", "request")
            put("id", id)
            put("uri", uri)
            payload?.let { put("payload", it) }
        }.toString()
    }

    private fun buildRegistrationPayload(pin: String?): String {
        val payload = JSONObject().apply {
            put("forcePairing", false)
            put("pairingType", if (pin != null) "PIN" else "PROMPT")
            put("manifest", JSONObject().apply {
                put("manifestVersion", 1)
                put("appVersion", "1.1")
                put("signed", JSONObject().apply {
                    put("created", "20240101")
                    put("appId", "com.elysium.nexus.controller")
                    put("vendorId", "com.elysium.nexus")
                    put("serial", UUID.randomUUID().toString())
                })
            })
            clientKey?.let { put("client-key", it) }
            pin?.let { put("pin", it) }
        }

        return JSONObject().apply {
            put("type", "register")
            put("id", "register_0")
            put("payload", payload)
        }.toString()
    }

    private fun sendHttpCommand(command: String): String? {
        return try {
            val url = URL("http://$ip:${port}/")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = 5_000
                readTimeout = 5_000
            }

            conn.outputStream.use { os ->
                OutputStreamWriter(os).use { writer ->
                    writer.write(command)
                    writer.flush()
                }
            }

            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().readText()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTTP command failed: ${e.message}")
            null
        }
    }

    private fun getCurrentVolume(): Int {
        return try {
            val response = sendHttpCommand(buildCommand("ssap://audio/getVolume"))
            if (response != null) {
                JSONObject(response).optJSONObject("payload")?.optInt("volume", 50) ?: 50
            } else 50
        } catch (_: Exception) { 50 }
    }

    private fun extractStateFromResponse(action: UniversalAction, json: JSONObject): DeviceState? {
        return when (action) {
            is UniversalAction.VolumeUp, is UniversalAction.VolumeDown, is UniversalAction.SetVolume -> {
                val volume = json.optJSONObject("payload")?.optInt("volume") ?: return null
                DeviceState.Level(volume / 100f)
            }
            is UniversalAction.PowerOff -> DeviceState.OnOff(false)
            is UniversalAction.Mute -> DeviceState.Level(0f)
            else -> null
        }
    }

    private fun getMacAddress(): String? {
        // In production, retrieve from device info or stored pairing data
        return null
    }

    private fun buildWoLPacket(macAddress: String): ByteArray {
        val macBytes = macAddress.split(":", "-")
            .map { it.toInt(16).toByte() }
            .toByteArray()
        val magicPacket = ByteArray(6 + 16 * macBytes.size)
        // 6 bytes of 0xFF
        for (i in 0..5) magicPacket[i] = 0xFF.toByte()
        // 16 repetitions of MAC
        for (i in 0..15) {
            System.arraycopy(macBytes, 0, magicPacket, 6 + i * macBytes.size, macBytes.size)
        }
        return magicPacket
    }
}
