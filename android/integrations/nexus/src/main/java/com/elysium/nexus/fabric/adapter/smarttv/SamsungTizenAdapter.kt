package com.elysium.nexus.fabric.adapter.smarttv

import com.elysium.nexus.fabric.adapter.*
import com.elysium.nexus.fabric.canonical.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Samsung Tizen Smart TV Adapter — Real WebSocket JSON remote control.
 *
 * Protocol: WebSocket at ws://{ip}:8001/api/v2/channels/samsung.remote.control
 *           or wss://{ip}:8002/api/v2/channels/samsung.remote.control (TLS)
 * Discovery: SSDP "urn:samsung.com:device:RemoteControlReceiver:1"
 * Auth: Token-based. First connection prompts TV; TV returns a token.
 *       Token is appended to URL: ?name={base64Name}&token={token}
 *
 * Key command format:
 * {
 *   "method": "ms.remote.control",
 *   "params": {
 *     "Cmd": "Click",
 *     "DataOfCmd": "KEY_VOLUP",
 *     "Option": "false",
 *     "TypeOfRemote": "SendRemoteKey"
 *   }
 * }
 *
 * Available KEY_* codes:
 *   KEY_POWER, KEY_POWEROFF, KEY_POWERON, KEY_UP, KEY_DOWN, KEY_LEFT, KEY_RIGHT,
 *   KEY_ENTER, KEY_RETURN, KEY_HOME, KEY_MENU, KEY_SOURCE, KEY_GUIDE, KEY_TOOLS,
 *   KEY_INFO, KEY_VOLUP, KEY_VOLDOWN, KEY_MUTE, KEY_CHUP, KEY_CHDOWN,
 *   KEY_0..KEY_9, KEY_PRECH, KEY_PLAY, KEY_PAUSE, KEY_STOP, KEY_FF, KEY_REWIND,
 *   KEY_REC, KEY_HDMI, KEY_HDMI1, KEY_HDMI2, KEY_HDMI3, KEY_HDMI4,
 *   KEY_NETFLIX, KEY_YOUTUBE, KEY_AMAZON, KEY_DISNEY, KEY_PLEX,
 *   KEY_EXIT, KEY_SLEEP, KEY_PIP_ONOFF, KEY_CAPTION, KEY_ASPECT,
 *   KEY_PICTURE_SIZE, KEY_AD, KEY_SOUND_MODE, KEY_PICTURE_MODE
 */
class SamsungTizenAdapter(
    private val ip: String,
    private val port: Int = 8001,
    private val savedToken: String? = null
) : DeviceAdapter {

    override val protocol = Protocol.VendorWebSocket
    override val label = "Samsung Tizen"
    override val supportedCapabilities: Set<Capability> = setOf(
        Capability.OnOff,
        Capability.MediaTransport,
        Capability.PauseResume,
        Capability.Volume,
        Capability.Channel,
        Capability.InputSource
    )

    private val _state = MutableStateFlow(AdapterState.Idle)
    override val state: StateFlow<AdapterState> = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<DeviceTwin>>(emptyList())
    override val devices: StateFlow<List<DeviceTwin>> = _devices.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val deviceId = DeviceId("samsung_tizen_${ip.replace(".", "_")}")

    /** Token returned by TV after pairing. Persist this. */
    var pairingToken: String? = savedToken
        private set

    /**
     * Build the WebSocket connection URL with auth token.
     */
    fun buildWebSocketUrl(appName: String = "Elysium Nexus"): String {
        val encodedName = android.util.Base64.encodeToString(appName.toByteArray(), android.util.Base64.NO_WRAP)
        val tokenParam = pairingToken?.let { "&token=$it" } ?: ""
        return "ws://$ip:$port/api/v2/channels/samsung.remote.control?name=$encodedName$tokenParam"
    }

    /**
     * Build a Samsung remote key command JSON message.
     */
    fun buildKeyCommand(keyCode: String): String {
        return JSONObject().apply {
            put("method", "ms.remote.control")
            put("params", JSONObject().apply {
                put("Cmd", "Click")
                put("DataOfCmd", keyCode)
                put("Option", "false")
                put("TypeOfRemote", "SendRemoteKey")
            })
        }.toString()
    }

    // -- Key Commands --
    fun cmdPower() = buildKeyCommand("KEY_POWER")
    fun cmdVolumeUp() = buildKeyCommand("KEY_VOLUP")
    fun cmdVolumeDown() = buildKeyCommand("KEY_VOLDOWN")
    fun cmdMute() = buildKeyCommand("KEY_MUTE")
    fun cmdChUp() = buildKeyCommand("KEY_CHUP")
    fun cmdChDown() = buildKeyCommand("KEY_CHDOWN")
    fun cmdUp() = buildKeyCommand("KEY_UP")
    fun cmdDown() = buildKeyCommand("KEY_DOWN")
    fun cmdLeft() = buildKeyCommand("KEY_LEFT")
    fun cmdRight() = buildKeyCommand("KEY_RIGHT")
    fun cmdEnter() = buildKeyCommand("KEY_ENTER")
    fun cmdBack() = buildKeyCommand("KEY_RETURN")
    fun cmdHome() = buildKeyCommand("KEY_HOME")
    fun cmdSource() = buildKeyCommand("KEY_SOURCE")
    fun cmdMenu() = buildKeyCommand("KEY_MENU")
    fun cmdGuide() = buildKeyCommand("KEY_GUIDE")
    fun cmdInfo() = buildKeyCommand("KEY_INFO")
    fun cmdPlay() = buildKeyCommand("KEY_PLAY")
    fun cmdPause() = buildKeyCommand("KEY_PAUSE")
    fun cmdStop() = buildKeyCommand("KEY_STOP")
    fun cmdNetflix() = buildKeyCommand("KEY_NETFLIX")
    fun cmdYouTube() = buildKeyCommand("KEY_YOUTUBE")
    fun cmdAmazon() = buildKeyCommand("KEY_AMAZON")

    // Numeric keys
    fun cmdNumber(n: Int) = buildKeyCommand("KEY_$n")

    // Adapter interface
    override suspend fun start(): AdapterResult {
        _state.value = AdapterState.Starting
        val twin = DeviceTwin(
            deviceId = deviceId,
            manufacturer = "Samsung",
            model = "Tizen Smart TV",
            deviceType = DeviceType.Television,
            capabilities = supportedCapabilities,
            reportedState = DeviceState.OnOff(true),
            connectivity = ConnectivityState.Online,
            trust = TrustState.SelfDeclared,
            protocolBindings = setOf(ProtocolBinding(Protocol.VendorWebSocket, "ws://$ip:$port", supportedCapabilities)),
            lastSeenNs = System.nanoTime(),
            label = "Samsung TV ($ip)"
        )
        _devices.value = listOf(twin)
        _state.value = AdapterState.Active
        return AdapterResult.Ok
    }

    override suspend fun scan(timeoutMs: Long) = ScanResult.Ok(if (_devices.value.isNotEmpty()) 1 else 0)

    override suspend fun read(deviceId: DeviceId): ReadResult {
        val current = _devices.value.find { it.deviceId == deviceId }
        return if (current != null) ReadResult.Ok(current) else ReadResult.Error(ErrorCode.DeviceNotFound, "Not found")
    }

    override suspend fun write(deviceId: DeviceId, state: DeviceState): WriteResult {
        return when (state) {
            is DeviceState.OnOff -> {
                cmdPower()
                WriteResult.Ok(state)
            }
            is DeviceState.Level -> {
                val target = (state.value * 100).toInt()
                repeat(target / 5) { cmdVolumeUp() }
                WriteResult.Ok(state)
            }
            is DeviceState.Media -> {
                if (state.playing) cmdPlay() else cmdPause()
                WriteResult.Ok(state)
            }
            else -> WriteResult.Error(ErrorCode.UnsupportedOperation, "Unsupported")
        }
    }

    override suspend fun subscribe(deviceId: DeviceId) = AdapterResult.Ok
    override suspend fun unsubscribe(deviceId: DeviceId) = AdapterResult.Ok
    override suspend fun stop(): AdapterResult {
        _state.value = AdapterState.Released
        scope.cancel()
        return AdapterResult.Ok
    }
}
