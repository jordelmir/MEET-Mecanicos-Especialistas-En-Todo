package com.elysium.nexus.fabric.adapter.ha

import android.util.Log
import com.elysium.nexus.fabric.adapter.AdapterResult
import com.elysium.nexus.fabric.adapter.AdapterState
import com.elysium.nexus.fabric.adapter.DeviceAdapter
import com.elysium.nexus.fabric.adapter.ErrorCode
import com.elysium.nexus.fabric.adapter.ReadResult
import com.elysium.nexus.fabric.adapter.ScanResult
import com.elysium.nexus.fabric.adapter.WriteResult
import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.ConnectivityState
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.DeviceTwin
import com.elysium.nexus.fabric.canonical.DeviceType
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.canonical.TrustState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext

/**
 * Home Assistant adapter.
 *
 * Connects to a Home Assistant instance via the
 * REST API and WebSocket API. Translates entity
 * states to [DeviceTwin]s and service calls to
 * canonical [DeviceState] writes.
 *
 * ## Protocol mapping
 *
 * | HA domain       | DeviceType       | Capabilities              |
 * |-----------------|------------------|---------------------------|
 * | light           | Light            | OnOff, Level, Color       |
 * | switch          | Switch           | OnOff                     |
 * | climate         | Thermostat       | Temperature, FanSpeed     |
 * | cover           | Curtain/Blind    | OpenClose, Position       |
 * | lock            | Lock             | LockUnlock                |
 * | media_player    | MediaPlayer      | MediaTransport, Volume    |
 * | fan             | Fan              | OnOff, Level              |
 * | sensor          | (varies)         | Temperature, Humidity, …  |
 * | binary_sensor   | (varies)         | ContactDetection, …       |
 * | camera          | Camera           | CameraStream              |
 * | automation      | (meta)           | StartStop                 |
 * | scene           | (meta)           | Scene                     |
 *
 * ## Why REST + WebSocket
 *
 * REST is used for one-shot reads and service calls;
 * WebSocket is used for real-time state push. The
 * adapter uses REST for scan/read/write and
 * WebSocket for subscribe.
 */
class HomeAssistantAdapter(
    /** The HA base URL (e.g. "https://my-ha.duckdns.org"). */
    private val baseUrl: String,
    /** A long-lived access token. */
    private val accessToken: String
) : DeviceAdapter {

    override val protocol: Protocol = Protocol.VendorRest
    override val label: String = "Home Assistant"
    override val supportedCapabilities: Set<Capability> = setOf(
        Capability.OnOff,
        Capability.Toggle,
        Capability.Level,
        Capability.Color,
        Capability.ColorTemperature,
        Capability.Temperature,
        Capability.TargetTemperature,
        Capability.FanSpeed,
        Capability.Position,
        Capability.OpenClose,
        Capability.LockUnlock,
        Capability.MediaTransport,
        Capability.Volume,
        Capability.Channel,
        Capability.Scene,
        Capability.CameraStream,
        Capability.MotionDetection,
        Capability.ContactDetection,
        Capability.SmokeDetection,
        Capability.StartStop,
        Capability.PauseResume,
        Capability.EnergyRead
    )

    private val _state = MutableStateFlow(AdapterState.Idle)
    override val state: StateFlow<AdapterState> = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<DeviceTwin>>(emptyList())
    override val devices: StateFlow<List<DeviceTwin>> = _devices.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private val deviceMap = ConcurrentHashMap<DeviceId, DeviceTwin>()

    override suspend fun start(): AdapterResult {
        if (_state.value != AdapterState.Idle) {
            return AdapterResult.Error(ErrorCode.AlreadyStarted, "Adapter is already started.")
        }
        _state.value = AdapterState.Starting
        return try {
            val result = withContext(Dispatchers.IO) {
                val conn = openConnection("$baseUrl/api/")
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                conn.connectTimeout = 5_000
                conn.readTimeout = 5_000
                val code = conn.responseCode
                conn.disconnect()
                if (code == 200) AdapterResult.Ok
                else AdapterResult.Error(
                    ErrorCode.AuthFailed,
                    "HA returned HTTP $code"
                )
            }
            if (result is AdapterResult.Ok) {
                _state.value = AdapterState.Active
                startPolling()
            }
            result
        } catch (e: Exception) {
            _state.value = AdapterState.Error
            AdapterResult.Error(ErrorCode.NetworkError, e.message ?: "Unknown error")
        }
    }

    override suspend fun scan(timeoutMs: Long): ScanResult {
        if (_state.value != AdapterState.Active) {
            return ScanResult.Error(ErrorCode.NotStarted, "Adapter not started.")
        }
        _state.value = AdapterState.Scanning
        return try {
            val entities = withContext(Dispatchers.IO) {
                fetchAllEntities()
            }
            val twins = entities.mapNotNull { entityToTwin(it) }
            twins.forEach { deviceMap[it.deviceId] = it }
            _devices.value = deviceMap.values.toList()
            _state.value = AdapterState.Active
            ScanResult.Ok(twins.size)
        } catch (e: Exception) {
            _state.value = AdapterState.Active
            ScanResult.Error(ErrorCode.NetworkError, e.message ?: "Scan failed")
        }
    }

    override suspend fun read(deviceId: DeviceId): ReadResult {
        val existing = deviceMap[deviceId]
            ?: return ReadResult.Error(ErrorCode.DeviceNotFound, "Device $deviceId not found.")
        return try {
            val entityId = existing.protocolBindings
                .firstOrNull { it.protocol == Protocol.VendorRest }
                ?.endpoint
                ?: return ReadResult.Error(ErrorCode.DeviceNotFound, "No HA entity id for $deviceId")
            val stateObj = withContext(Dispatchers.IO) {
                fetchEntityState(entityId)
            }
            val twin = stateObj?.let { parseEntityState(it, existing) } ?: existing
            deviceMap[deviceId] = twin
            _devices.value = deviceMap.values.toList()
            ReadResult.Ok(twin)
        } catch (e: Exception) {
            ReadResult.Error(ErrorCode.NetworkError, e.message ?: "Read failed")
        }
    }

    override suspend fun write(deviceId: DeviceId, state: DeviceState): WriteResult {
        val twin = deviceMap[deviceId]
            ?: return WriteResult.Error(ErrorCode.DeviceNotFound, "Device $deviceId not found.")
        val entityId = twin.protocolBindings
            .firstOrNull { it.protocol == Protocol.VendorRest }
            ?.endpoint
            ?: return WriteResult.Error(ErrorCode.DeviceNotFound, "No HA entity id for $deviceId")
        return try {
            withContext(Dispatchers.IO) {
                callService(entityId, state)
            }
            // Re-read the entity to get the updated state.
            val freshState = withContext(Dispatchers.IO) {
                fetchEntityState(entityId)
            }
            val freshTwin = freshState?.let { parseEntityState(it, twin) } ?: twin
            deviceMap[deviceId] = freshTwin
            _devices.value = deviceMap.values.toList()
            WriteResult.Ok(freshTwin.reportedState)
        } catch (e: Exception) {
            WriteResult.Error(ErrorCode.NetworkError, e.message ?: "Write failed")
        }
    }

    override suspend fun subscribe(deviceId: DeviceId): AdapterResult {
        // Home Assistant WebSocket push is a Phase 2+ feature.
        // For now, polling via startPolling() covers all devices.
        return AdapterResult.Ok
    }

    override suspend fun unsubscribe(deviceId: DeviceId): AdapterResult {
        return AdapterResult.Ok
    }

    override suspend fun stop(): AdapterResult {
        _state.value = AdapterState.Stopping
        pollJob?.cancel()
        pollJob = null
        scope.cancel()
        deviceMap.clear()
        _devices.value = emptyList()
        _state.value = AdapterState.Released
        return AdapterResult.Ok
    }

    // ── Internal ──────────────────────────────────────

    /**
     * Poll all entities every 30 seconds. The
     * WebSocket push path is Phase 2+.
     */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000L)
                try {
                    val entities = withContext(Dispatchers.IO) {
                        fetchAllEntities()
                    }
                    val twins = entities.mapNotNull { entityToTwin(it) }
                    twins.forEach { deviceMap[it.deviceId] = it }
                    _devices.value = deviceMap.values.toList()
                } catch (e: Exception) {
                    Log.w(TAG, "Poll failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Fetch all states from HA REST API.
     * GET /api/states
     */
    private fun fetchAllEntities(): List<JSONObject> {
        val conn = openConnection("$baseUrl/api/states")
        conn.setRequestProperty("Authorization", "Bearer $accessToken")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        val response = readResponse(conn)
        conn.disconnect()
        val arr = JSONArray(response)
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    /**
     * Fetch a single entity state.
     * GET /api/states/{entity_id}
     */
    private fun fetchEntityState(entityId: String): JSONObject? {
        val conn = openConnection("$baseUrl/api/states/$entityId")
        conn.setRequestProperty("Authorization", "Bearer $accessToken")
        conn.connectTimeout = 5_000
        conn.readTimeout = 5_000
        val code = conn.responseCode
        return if (code == 200) {
            val response = readResponse(conn)
            conn.disconnect()
            JSONObject(response)
        } else {
            conn.disconnect()
            null
        }
    }

    /**
     * Call a HA service.
     * POST /api/services/{domain}/{service}
     * Body: { "entity_id": "..." }
     */
    private fun callService(entityId: String, targetState: DeviceState) {
        val parts = entityId.split(".", limit = 2)
        if (parts.size < 2) return
        val domain = parts[0]
        val (service, payload) = serviceForState(domain, targetState)
        val url = "$baseUrl/api/services/$domain/$service"
        val conn = openConnection(url)
        conn.setRequestProperty("Authorization", "Bearer $accessToken")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 5_000
        conn.readTimeout = 5_000
        val writer = OutputStreamWriter(conn.outputStream)
        writer.write(payload.toString())
        writer.flush()
        writer.close()
        conn.responseCode // trigger the request
        conn.disconnect()
    }

    /**
     * Map a canonical [DeviceState] to an HA service
     * call + JSON body for a given domain.
     */
    private fun serviceForState(
        domain: String,
        targetState: DeviceState
    ): Pair<String, JSONObject> {
        val body = JSONObject()
        return when (targetState) {
            is DeviceState.OnOff -> {
                val service = if (targetState.isOn) "turn_on" else "turn_off"
                service to body
            }
            is DeviceState.Level -> {
                body.put("brightness", (targetState.value * 255).toInt())
                "turn_on" to body
            }
            is DeviceState.Color -> {
                body.put("hs_color", JSONArray().apply {
                    put(targetState.hueDegrees.toDouble())
                    put(targetState.saturation.toDouble())
                })
                "turn_on" to body
            }
            is DeviceState.ColorTemperature -> {
                body.put("color_temp_kelvin", targetState.kelvin)
                "turn_on" to body
            }
            is DeviceState.Climate -> {
                body.put("temperature", targetState.targetCelsius.toDouble())
                body.put("hvac_mode", targetState.mode.name.lowercase())
                "set_temperature" to body
            }
            is DeviceState.Position -> {
                body.put("position", (targetState.percentOpen * 100).toInt())
                "set_cover_position" to body
            }
            is DeviceState.Lock -> {
                val service = if (targetState.locked) "lock" else "unlock"
                service to body
            }
            is DeviceState.Media -> {
                val service = if (targetState.playing) "media_play" else "media_pause"
                service to body
            }
            else -> "turn_on" to body
        }
    }

    /**
     * Convert a HA entity JSON to a [DeviceTwin].
     * Returns `null` for entities we don't map.
     */
    private fun entityToTwin(obj: JSONObject): DeviceTwin? {
        val entityId = obj.optString("entity_id", "")
        if (entityId.isBlank()) return null
        val domain = entityId.split(".", limit = 2).firstOrNull() ?: return null
        val deviceType = domainToDeviceType(domain) ?: return null
        val capabilities = domainToCapabilities(domain)
        val state = parseState(domain, obj)
        val friendlyName = obj.optJSONObject("attributes")
            ?.optString("friendly_name", "")
            ?: ""
        val haDeviceId = obj.optJSONObject("attributes")
            ?.optJSONObject("device_class")
            ?.toString() ?: entityId

        return DeviceTwin(
            deviceId = DeviceId("ha:$entityId"),
            manufacturer = "Home Assistant",
            model = domain,
            deviceType = deviceType,
            capabilities = capabilities,
            reportedState = state,
            desiredState = state,
            connectivity = ConnectivityState.Online,
            trust = TrustState.SelfDeclared,
            protocolBindings = setOf(
                com.elysium.nexus.fabric.canonical.ProtocolBinding(
                    protocol = Protocol.VendorRest,
                    endpoint = entityId,
                    capabilities = capabilities
                )
            ),
            lastSeenNs = System.nanoTime(),
            label = friendlyName.ifBlank { entityId }
        )
    }

    /**
     * Parse a fresh HA entity state JSON into an
     * updated [DeviceTwin], preserving the original's
     * metadata.
     */
    private fun parseEntityState(
        stateObj: JSONObject,
        original: DeviceTwin
    ): DeviceTwin {
        val domain = original.model ?: "unknown"
        val newState = parseState(domain, stateObj)
        return original.copy(
            reportedState = newState,
            lastSeenNs = System.nanoTime()
        )
    }

    /**
     * Parse HA entity state + attributes into a
     * canonical [DeviceState].
     */
    private fun parseState(domain: String, obj: JSONObject): DeviceState {
        val stateStr = obj.optString("state", "unknown")
        val attrs = obj.optJSONObject("attributes") ?: JSONObject()
        return when (domain) {
            "light" -> {
                if (stateStr == "on") {
                    val brightness = attrs.optDouble("brightness", -1.0)
                    if (brightness >= 0) {
                        DeviceState.Level((brightness / 255.0).toFloat())
                    } else {
                        DeviceState.OnOff(isOn = true)
                    }
                } else {
                    DeviceState.OnOff(isOn = false)
                }
            }
            "switch", "fan", "automation" -> {
                DeviceState.OnOff(isOn = stateStr == "on")
            }
            "climate" -> {
                val temp = attrs.optDouble("temperature", 20.0).toFloat()
                val mode = when (stateStr.lowercase()) {
                    "heat" -> com.elysium.nexus.fabric.canonical.ClimateMode.Heat
                    "cool" -> com.elysium.nexus.fabric.canonical.ClimateMode.Cool
                    "auto" -> com.elysium.nexus.fabric.canonical.ClimateMode.Auto
                    "dry" -> com.elysium.nexus.fabric.canonical.ClimateMode.Dry
                    "fan_only" -> com.elysium.nexus.fabric.canonical.ClimateMode.FanOnly
                    else -> com.elysium.nexus.fabric.canonical.ClimateMode.Off
                }
                DeviceState.Climate(targetCelsius = temp, mode = mode)
            }
            "cover" -> {
                val position = attrs.optDouble("current_position", 0.0) / 100.0
                DeviceState.Position(percentOpen = position.toFloat().coerceIn(0f, 1f))
            }
            "lock" -> {
                DeviceState.Lock(locked = stateStr == "locked", source = com.elysium.nexus.fabric.canonical.LockSource.App)
            }
            "media_player" -> {
                DeviceState.Media(playing = stateStr == "playing")
            }
            "sensor" -> {
                val value = stateStr.toFloatOrNull()
                val unit = attrs.optString("unit_of_measurement", "")
                when {
                    unit.contains("°C") || unit.contains("°F") ->
                        DeviceState.Level(value = (value ?: 0f) / 100f)
                    else -> DeviceState.Level(value = (value ?: 0f) / 100f)
                }
            }
            else -> DeviceState.Unknown
        }
    }

    private fun domainToDeviceType(domain: String): DeviceType? = when (domain) {
        "light" -> DeviceType.Light
        "switch" -> DeviceType.Switch
        "fan" -> DeviceType.Fan
        "climate" -> DeviceType.Thermostat
        "cover" -> DeviceType.Curtain
        "lock" -> DeviceType.Lock
        "media_player" -> DeviceType.MediaPlayer
        "sensor" -> DeviceType.SensorTemperature
        "binary_sensor" -> DeviceType.SensorContact
        "camera" -> DeviceType.Camera
        "automation" -> DeviceType.Switch
        "scene" -> DeviceType.Switch
        else -> null
    }

    private fun domainToCapabilities(domain: String): Set<Capability> = when (domain) {
        "light" -> setOf(Capability.OnOff, Capability.Level, Capability.Color)
        "switch" -> setOf(Capability.OnOff)
        "fan" -> setOf(Capability.OnOff, Capability.Level)
        "climate" -> setOf(Capability.Temperature, Capability.TargetTemperature, Capability.FanSpeed)
        "cover" -> setOf(Capability.OpenClose, Capability.Position)
        "lock" -> setOf(Capability.LockUnlock)
        "media_player" -> setOf(Capability.MediaTransport, Capability.Volume)
        "sensor" -> setOf(Capability.Temperature)
        "binary_sensor" -> setOf(Capability.ContactDetection)
        "camera" -> setOf(Capability.CameraStream)
        "automation" -> setOf(Capability.StartStop)
        "scene" -> setOf(Capability.Scene)
        else -> emptySet()
    }

    private fun openConnection(urlStr: String): HttpURLConnection {
        val url = URL(urlStr)
        val conn = url.openConnection()
        return when (conn) {
            is HttpsURLConnection -> conn
            else -> conn as HttpURLConnection
        }
    }

    private fun readResponse(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) {
            conn.inputStream
        } else {
            conn.errorStream ?: return ""
        }
        val reader = BufferedReader(InputStreamReader(stream))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line)
        }
        reader.close()
        return sb.toString()
    }

    companion object {
        private const val TAG = "HomeAssistantAdapter"
    }
}
