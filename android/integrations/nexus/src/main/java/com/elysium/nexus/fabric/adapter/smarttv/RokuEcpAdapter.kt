package com.elysium.nexus.fabric.adapter.smarttv

import com.elysium.nexus.fabric.adapter.*
import com.elysium.nexus.fabric.canonical.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Roku External Control Protocol (ECP) adapter.
 *
 * Roku ECP is a REST API exposed at http://{ip}:8060.
 * Authentication: NONE. Discovery: SSDP "roku:ecp" or mDNS "_roku-ecp._tcp".
 *
 * Keypress: POST http://{ip}:8060/keypress/{key}
 * Device Info: GET http://{ip}:8060/query/device-info
 * App List: GET http://{ip}:8060/query/apps
 * Launch App: POST http://{ip}:8060/launch/{appId}
 */
class RokuEcpAdapter(
    private val ip: String,
    private val port: Int = 8060
) : DeviceAdapter {

    override val protocol = Protocol.VendorRest
    override val label = "Roku ECP"
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
    private val deviceId = DeviceId("roku_${ip.replace(".", "_")}")

    override suspend fun start(): AdapterResult {
        _state.value = AdapterState.Starting
        return try {
            val info = queryDeviceInfo()
            if (info != null) {
                val twin = DeviceTwin(
                    deviceId = deviceId,
                    manufacturer = "Roku",
                    model = info["model-name"] ?: "Roku Device",
                    deviceType = DeviceType.StreamingDevice,
                    capabilities = supportedCapabilities,
                    reportedState = DeviceState.OnOff(true),
                    connectivity = ConnectivityState.Online,
                    trust = TrustState.SelfDeclared,
                    protocolBindings = setOf(ProtocolBinding(Protocol.VendorRest, "http://$ip:$port", supportedCapabilities)),
                    lastSeenNs = System.nanoTime(),
                    label = info["user-device-name"] ?: info["model-name"] ?: "Roku"
                )
                _devices.value = listOf(twin)
                _state.value = AdapterState.Active
                AdapterResult.Ok
            } else {
                _state.value = AdapterState.Error
                AdapterResult.Error(ErrorCode.DeviceOffline, "Cannot reach Roku at $ip:$port")
            }
        } catch (e: Exception) {
            _state.value = AdapterState.Error
            AdapterResult.Error(ErrorCode.NetworkError, e.message ?: "Unknown error")
        }
    }

    override suspend fun scan(timeoutMs: Long): ScanResult {
        val info = queryDeviceInfo()
        return if (info != null) ScanResult.Ok(1) else ScanResult.Ok(0)
    }

    override suspend fun read(deviceId: DeviceId): ReadResult {
        val current = _devices.value.find { it.deviceId == deviceId }
        return if (current != null) ReadResult.Ok(current)
        else ReadResult.Error(ErrorCode.DeviceNotFound, "Device not found")
    }

    override suspend fun write(deviceId: DeviceId, state: DeviceState): WriteResult = withContext(Dispatchers.IO) {
        when (state) {
            is DeviceState.OnOff -> {
                sendKeypress("Power")
                WriteResult.Ok(state)
            }
            is DeviceState.Level -> {
                val steps = (state.value * 50).toInt()
                repeat(steps) { sendKeypress("VolumeUp") }
                WriteResult.Ok(state)
            }
            is DeviceState.Media -> {
                sendKeypress(if (state.playing) "Play" else "Pause")
                WriteResult.Ok(state)
            }
            else -> WriteResult.Error(ErrorCode.UnsupportedOperation, "Unsupported state type")
        }
    }

    /**
     * Send a keypress to the Roku device.
     * Valid keys: Home, Rev, Fwd, Play, Select, Left, Right, Down, Up,
     *   Back, InstantReplay, Info, Backspace, Search, Enter,
     *   VolumeDown, VolumeUp, VolumeMute, Power, PowerOff, PowerOn,
     *   InputTuner, InputHDMI1, InputHDMI2, InputHDMI3, InputHDMI4, InputAV1
     */
    suspend fun sendKeypress(key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$ip:$port/keypress/$key")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.doOutput = true
            conn.outputStream.close()
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Query installed apps on the Roku.
     */
    suspend fun queryApps(): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$ip:$port/query/apps")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            // Simple XML parse: <app id="12345">AppName</app>
            val apps = mutableMapOf<String, String>()
            val regex = Regex("""<app\s+id="(\d+)"[^>]*>([^<]+)</app>""")
            regex.findAll(body).forEach { match ->
                apps[match.groupValues[1]] = match.groupValues[2]
            }
            apps
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Launch an app by ID.
     */
    suspend fun launchApp(appId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$ip:$port/launch/$appId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 2000
            conn.doOutput = true
            conn.outputStream.close()
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            false
        }
    }

    private fun queryDeviceInfo(): Map<String, String>? {
        return try {
            val url = URL("http://$ip:$port/query/device-info")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            if (conn.responseCode != 200) {
                conn.disconnect()
                return null
            }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val info = mutableMapOf<String, String>()
            val regex = Regex("""<(\w[\w-]*)>([^<]*)</\1>""")
            regex.findAll(body).forEach { match ->
                info[match.groupValues[1]] = match.groupValues[2]
            }
            info
        } catch (e: Exception) {
            null
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
