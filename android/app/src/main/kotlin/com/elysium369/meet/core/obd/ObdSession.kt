package com.elysium369.meet.core.obd

import android.content.Context
import android.util.Log
import com.elysium369.meet.core.transport.BtClassicTransport
import com.elysium369.meet.core.transport.BleTransport
import com.elysium369.meet.core.transport.TransportInterface
import com.elysium369.meet.core.transport.WifiTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.github.jan.supabase.postgrest.postgrest
import java.util.concurrent.CopyOnWriteArraySet

enum class ObdState {
    DISCONNECTED,
    CONNECTING,
    NEGOTIATING,
    CONNECTED,
    ERROR
}

/**
 * ObdSession — Professional Grade OBD2 Communication Engine.
 * Handles high-frequency polling, multi-frame ISO-TP responses,
 * and robust ELM327/STN initialization.
 */
enum class NetworkType {
    CAN_HIGH,
    CAN_LOW,
    LIN,
    K_LINE,
    UNKNOWN
}

data class NetworkModule(
    val id: String,
    val name: String,
    val isAlive: Boolean,
    val networkType: NetworkType = NetworkType.CAN_HIGH,
    val latencyMs: Long = 0,
    val dtcs: List<String> = emptyList()
)

/** Callback for real-time OBD traffic monitoring */
interface ObdTrafficListener {
    fun onCommandSent(command: String)
    fun onResponseReceived(command: String, response: String)
    fun onError(command: String, error: String)
}

class ObdSession(
    private val scope: CoroutineScope,
    private val bluetoothAdapter: android.bluetooth.BluetoothAdapter?,
    private val context: Context
) {
    private var trafficListener: ObdTrafficListener? = null

    fun setTrafficListener(listener: ObdTrafficListener?) {
        trafficListener = listener
    }
    private val _state = MutableStateFlow(ObdState.DISCONNECTED)
    val state: StateFlow<ObdState> = _state.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val commandQueue = ObdCommandQueue()
    private val communicationMutex = Mutex()
    private val keepAliveManager = KeepAliveManager(this)

    private val _liveData = MutableStateFlow<Map<String, Float>>(emptyMap())
    val liveData: StateFlow<Map<String, Float>> = _liveData.asStateFlow()

    private val _freezeFrame = MutableStateFlow<Map<String, String>>(emptyMap())
    val freezeFrame: StateFlow<Map<String, String>> = _freezeFrame.asStateFlow()

    private val _vin = MutableStateFlow<String?>(null)
    val vin: StateFlow<String?> = _vin.asStateFlow()

    private var transport: TransportInterface? = null
    private var isRunning = false
    @Volatile
    private var isPollingPaused = false

    val isLivePollingPaused: Boolean
        get() = isPollingPaused

    fun pauseLivePolling() {
        isPollingPaused = true
        Log.d(TAG, "Live polling paused")
    }

    fun resumeLivePolling() {
        isPollingPaused = false
        Log.d(TAG, "Live polling resumed")
    }

    fun clearCommandQueue() {
        commandQueue.clear()
        Log.d(TAG, "Command queue cleared")
    }
    private var currentJob: Job? = null
    private var pollingJob: Job? = null

    private val _isAdapterPro = MutableStateFlow(false)
    val isAdapterPro: StateFlow<Boolean> = _isAdapterPro.asStateFlow()

    private val _highSpeedMode = MutableStateFlow(false)
    val highSpeedMode: StateFlow<Boolean> = _highSpeedMode.asStateFlow()

    private val _pinnedPids = MutableStateFlow<Set<String>>(emptySet())
    val pinnedPids: StateFlow<Set<String>> = _pinnedPids.asStateFlow()

    private val _qosMetrics = MutableStateFlow(QosMetrics())
    val qosMetrics: StateFlow<QosMetrics> = _qosMetrics.asStateFlow()

    // ── Adapter & Vehicle Identification (Exposed as StateFlows for UI) ──
    private val _adapterVersion = MutableStateFlow("")
    val adapterVersionFlow: StateFlow<String> = _adapterVersion.asStateFlow()
    private var adapterVersion: String = ""
        set(value) { field = value; _adapterVersion.value = value }

    private val _isCloneAdapter = MutableStateFlow(true)
    val isCloneAdapterFlow: StateFlow<Boolean> = _isCloneAdapter.asStateFlow()
    private var isCloneAdapter: Boolean = true
        set(value) { field = value; _isCloneAdapter.value = value }

    private val _detectedProtocol = MutableStateFlow("")
    val detectedProtocolFlow: StateFlow<String> = _detectedProtocol.asStateFlow()
    private var detectedProtocol: String = ""
        set(value) { field = value; _detectedProtocol.value = value }

    private val _calibrationId = MutableStateFlow<String?>(null)
    val calibrationId: StateFlow<String?> = _calibrationId.asStateFlow()

    private val _ecuName = MutableStateFlow<String?>(null)
    val ecuName: StateFlow<String?> = _ecuName.asStateFlow()

    private var baseDelayMs: Long = 50L
    private var maxLineLength: Int = 128

    // Performance Tracking
    private var lastCmdTime = 0L
    private var cmdCount = 0
    private val oemPidsToPoll = CopyOnWriteArraySet<PidDefinition>()

    private val _activeTestStatus = MutableStateFlow(ActiveTestStatus())
    val activeTestStatus: StateFlow<ActiveTestStatus> = _activeTestStatus.asStateFlow()
    private var activeTestJob: Job? = null

    private var consecutiveErrors = 0
    private var isSelfHealing = false

    private val _isUdsSessionActive = MutableStateFlow(false)
    val isUdsSessionActive: StateFlow<Boolean> = _isUdsSessionActive.asStateFlow()

    private val _allDetectedDtcs = MutableStateFlow<Set<String>>(emptySet())
    val allDetectedDtcs: StateFlow<Set<String>> = _allDetectedDtcs.asStateFlow()

    private val _lastDtcScanReport = MutableStateFlow<DtcScanReport?>(null)
    val lastDtcScanReport: StateFlow<DtcScanReport?> = _lastDtcScanReport.asStateFlow()

    private val _networkTopology = MutableStateFlow<List<NetworkModule>>(emptyList())
    val networkTopology: StateFlow<List<NetworkModule>> = _networkTopology.asStateFlow()

    private val _isScanningTopology = MutableStateFlow(false)
    val isScanningTopology: StateFlow<Boolean> = _isScanningTopology.asStateFlow()

    private val _oscilloscopeStream = MutableSharedFlow<Pair<Long, Float>>(extraBufferCapacity = 100)
    val oscilloscopeStream: SharedFlow<Pair<Long, Float>> = _oscilloscopeStream.asSharedFlow()

    private var oscilloscopeJob: Job? = null

    fun setUdsSessionActive(active: Boolean) {
        _isUdsSessionActive.value = active
    }

    // Smooth sensors & Calibration
    private val sensorSmoother = SensorSmootherManager()
    private val calibrationOffsets = MutableStateFlow<Map<String, Float>>(emptyMap())

    fun setCalibrationOffset(pid: String, offset: Float) {
        val current = calibrationOffsets.value.toMutableMap()
        current[pid] = offset
        calibrationOffsets.value = current
    }

    fun getCalibrationOffset(pid: String): Float {
        return calibrationOffsets.value[pid] ?: 0f
    }

    // Standard PIDs for dashboard polling — comprehensive list matching Car Scanner Pro
    private val dashboardPids = listOf(
        // ─── CORE ENGINE (High-frequency) ───
        "010C", // RPM
        "010D", // Vehicle Speed
        "0104", // Calculated Engine Load
        "0111", // Throttle Position
        "010B", // Intake Manifold Absolute Pressure (MAP)
        "010E", // Timing Advance
        // ─── TEMPERATURE ───
        "0105", // Engine Coolant Temperature
        "010F", // Intake Air Temperature
        // ─── FUEL SYSTEM ───
        "0110", // MAF Air Flow Rate
        "012F", // Fuel Tank Level
        "0106", // Short Term Fuel Trim - Bank 1
        "0107", // Long Term Fuel Trim - Bank 1
        // ─── O2 SENSORS ───
        "0114", // O2 Sensor 1 Bank 1 — Voltage + Short Term Fuel Trim
        "0115", // O2 Sensor 2 Bank 1 — Voltage + Short Term Fuel Trim
        // ─── DIAGNOSTICS (Low-frequency — polled every 3 cycles) ───
        "0101", // Monitor Status (MIL, DTC count, readiness)
        "011F", // Run Time Since Engine Start
        "0133"  // Presión Barométrica (Barometric Pressure)
        // NOTE: 011C (OBD Standards), 010A (Fuel Pressure),
        //       0103 (Fuel System Status), 0146 (Ambient Temp) removed from
        //       high-frequency loop — they rarely change and waste bus time.
    )

    private var targetAddress: String? = null
    private var isDoIpMode = false

    fun setTargetAddress(address: String) {
        this.targetAddress = address
        transport?.let { old ->
            scope.launch { runCatching { old.disconnect() } }
        }

        isDoIpMode = address.contains(":13400")

        if (address.contains(".") || address.contains(":") || address.startsWith("192.168.")) {
            val parts = address.split(":")
            val ip = parts.getOrNull(0) ?: "192.168.0.10"
            val port = parts.getOrNull(1)?.toIntOrNull() ?: if (isDoIpMode) 13400 else 35000
            transport = WifiTransport(ip, port)
        } else {
            if (bluetoothAdapter != null) {
                transport = BtClassicTransport(address, bluetoothAdapter)
            }
        }
    }

    suspend fun connect() {
        if (_state.value == ObdState.CONNECTED || _state.value == ObdState.CONNECTING) return

        val activeTransport = transport
        if (activeTransport == null) {
            _state.value = ObdState.ERROR
            _statusMessage.value = "Selecciona un dispositivo para conectar."
            return
        }

        _state.value = ObdState.CONNECTING
        Log.i(TAG, "═══ OBD CONNECT START (max $MAX_CONNECT_ATTEMPTS attempts) ═══")
        val t0 = System.currentTimeMillis()

        var lastException: Exception? = null

        for (attempt in 1..MAX_CONNECT_ATTEMPTS) {
            Log.i(TAG, "── ATTEMPT $attempt/$MAX_CONNECT_ATTEMPTS ──")
            _state.value = ObdState.CONNECTING
            _statusMessage.value = if (attempt == 1) {
                "Estableciendo conexión física..."
            } else {
                "Reintentando conexión ($attempt/$MAX_CONNECT_ATTEMPTS)..."
            }

            try {
                // 1. Physical Bluetooth/WiFi Connection
                activeTransport.connect()
                Log.i(TAG, "✓ Physical link UP in ${System.currentTimeMillis()-t0}ms (attempt $attempt)")
                
                if (isDoIpMode) {
                    _statusMessage.value = "Conexión DoIP OK. Activando enrutamiento..."
                    _state.value = ObdState.NEGOTIATING
                    withTimeout(20000) {
                        initializeDoIpConnection()
                    }
                } else {
                    _statusMessage.value = "Conexión OK. Negociando ELM327..."
                    _state.value = ObdState.NEGOTIATING
                    withTimeout(90000) {
                        initializeAdapter()
                    }
                }

                // Reset smoother on new successful connection
                sensorSmoother.resetAll()

                // 3. SUCCESS — Connection fully established
                _state.value = ObdState.CONNECTED
                _statusMessage.value = "Enlace Crítico Sincronizado: $adapterVersion"
                isRunning = true
                consecutiveErrors = 0
                Log.i(TAG, "═══ OBD CONNECT SUCCESS ═══ Attempt=$attempt | Total: ${System.currentTimeMillis()-t0}ms | Adapter=$adapterVersion | Protocol=$detectedProtocol")

                // ── TELEMETRÍA SILENCIOSA (SUPABASE) SUCCESS ──
                scope.launch {
                    com.elysium369.meet.data.remote.CloudSyncRepository.logSessionTelemetry(
                        userId = "local_app_user",
                        adapterType = adapterVersion,
                        notes = "SUCCESS (attempt $attempt/$MAX_CONNECT_ATTEMPTS)",
                        protocol = detectedProtocol,
                        isSuccess = true
                    )
                }

                startQueueProcessor()
                startHeartbeatMonitor()
                keepAliveManager.start(scope)

                // ── AUTO-IDENTIFICATION: VIN + Calibration ID + ECU ──
                _statusMessage.value = "Identificando vehículo..."
                scope.launch {
                    try {
                        fetchVin()
                        fetchCalibrationId()
                        fetchEcuName()
                        _statusMessage.value = if (_vin.value != null && _vin.value != "N/A") {
                            "Vehículo identificado ✓ VIN: ${_vin.value}"
                        } else {
                            "Enlace activo. VIN no disponible."
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Auto-identification partial: ${e.message}")
                        _statusMessage.value = "Enlace activo. Identificación parcial."
                    }
                }

                return // ← EXIT: Connection successful

            } catch (e: Exception) {
                lastException = e
                val msg = e.message ?: "Error desconocido"
                Log.w(TAG, "── ATTEMPT $attempt FAILED: ${e.javaClass.simpleName}: $msg", e)

                // Disconnect cleanly before retrying
                try { activeTransport.disconnect() } catch (_: Exception) {}

                if (attempt < MAX_CONNECT_ATTEMPTS) {
                    // Show retry UI with countdown
                    val retryDelaySec = attempt + 1 // Progressive: 2s, 3s
                    for (sec in retryDelaySec downTo 1) {
                        _statusMessage.value = "Intento $attempt falló. Reintentando en ${sec}s... ($attempt/$MAX_CONNECT_ATTEMPTS)"
                        _state.value = ObdState.CONNECTING
                        kotlinx.coroutines.delay(1000)
                    }
                }
            }
        }

        // ALL ATTEMPTS EXHAUSTED — Report final failure
        _state.value = ObdState.ERROR
        val msg = lastException?.message ?: "Error desconocido"
        Log.e(TAG, "═══ OBD CONNECT FAILED ═══ All $MAX_CONNECT_ATTEMPTS attempts exhausted in ${System.currentTimeMillis()-t0}ms: $msg", lastException)
        _statusMessage.value = when {
            msg.contains("Adaptador no responde") -> "Adaptador no responde tras $MAX_CONNECT_ATTEMPTS intentos. Verifica que esté encendido y el contacto del auto en ON."
            msg.contains("ECU") -> "No se detectó ECU tras $MAX_CONNECT_ATTEMPTS intentos. Gira la llave a posición ON (sin arrancar)."
            msg.contains("Timed") -> "Timeout de negociación tras $MAX_CONNECT_ATTEMPTS intentos. Verifica conexión Bluetooth."
            msg.contains("enlazar") -> "No se pudo enlazar al ELM327 tras $MAX_CONNECT_ATTEMPTS intentos. Desvincula el dispositivo en Bluetooth y vuelve a emparejarlo."
            else -> "Error tras $MAX_CONNECT_ATTEMPTS intentos: $msg"
        }

        // ── TELEMETRÍA SILENCIOSA (SUPABASE) FAILURE ──
        scope.launch {
            com.elysium369.meet.data.remote.CloudSyncRepository.logSessionTelemetry(
                userId = "local_app_user",
                adapterType = adapterVersion.ifBlank { "Unknown" },
                notes = "FAILED_CONNECTION ($MAX_CONNECT_ATTEMPTS attempts): $msg",
                protocol = detectedProtocol,
                isSuccess = false
            )
        }

        isRunning = false
    }

    private val customPidsToPoll = CopyOnWriteArraySet<com.elysium369.meet.data.local.entities.CustomPidEntity>()

    fun setCustomPids(pids: List<com.elysium369.meet.data.local.entities.CustomPidEntity>) {
        customPidsToPoll.clear()
        customPidsToPoll.addAll(pids)
    }

    fun setHighSpeedMode(enabled: Boolean) {
        _highSpeedMode.value = enabled
        if (enabled) {
            _statusMessage.value = "Modo Alta Velocidad Activado (20Hz+)"
        }
        // Restart polling to apply mode
        if (isRunning) startLivePolling()
    }

    fun pinPid(pid: String) {
        val current = _pinnedPids.value.toMutableSet()
        current.add(pid)
        _pinnedPids.value = current
    }

    fun unpinPid(pid: String) {
        val current = _pinnedPids.value.toMutableSet()
        current.remove(pid)
        _pinnedPids.value = current
    }

    fun startLivePolling() {
        if (!isRunning || _state.value != ObdState.CONNECTED) return
        pollingJob?.cancel()
        pollingJob = scope.launch {
            val supportedPids = detectSupportedPids()

            // Priority 1: High-frequency PIDs (RPM, Speed, Throttle)
            val baseHighPriority = listOf("0C", "0D", "11") // PID codes without '01'

            var cycleCount = 0

            while (isRunning && isActive) {
                if (isPollingPaused) {
                    delay(100)
                    continue
                }
                val cycleStartTime = System.currentTimeMillis()

                if (_highSpeedMode.value && _pinnedPids.value.isNotEmpty()) {
                    // HIGH SPEED MODE: Only poll pinned PIDs at max rate (Use Multi-PID on CAN for up to 6x faster refresh)
                    val pinnedList = _pinnedPids.value.toList()
                    if (detectedProtocol.contains("CAN") && pinnedList.size > 1) {
                        val cleanPinned = pinnedList.map { it.removePrefix("01") }
                        cleanPinned.chunked(6).forEach { chunk ->
                            pollMultiPidBatch(chunk)
                        }
                    } else {
                        pollBatch(pinnedList)
                    }
                } else {
                    // NORMAL MODE: Balanced Polling (filtered by supported PIDs to optimize bus bandwidth)
                    val highPriorityPids = (baseHighPriority + _pinnedPids.value.map { it.removePrefix("01") })
                        .distinct()
                        .filter { pid ->
                            val pidInt = pid.toIntOrNull(16) ?: return@filter true
                            supportedPids.isEmpty() || supportedPids.contains(pidInt)
                        }

                    val normalPriorityPids = dashboardPids.map { it.removePrefix("01") }
                        .filter { pid ->
                            val pidInt = pid.toIntOrNull(16) ?: return@filter true
                            supportedPids.isEmpty() || supportedPids.contains(pidInt)
                        }
                        .filter { pid ->
                            !highPriorityPids.contains(pid)
                        }

                    // 1. Poll High Priority (Every Cycle) - Use Multi-PID request if on CAN
                    if (detectedProtocol.contains("CAN") && highPriorityPids.size > 1) {
                        highPriorityPids.chunked(6).forEach { chunk ->
                            pollMultiPidBatch(chunk)
                        }
                    } else {
                        pollBatch(highPriorityPids.map { "01$it" })
                    }

                    // 2. Poll Normal Priority (Every 3 cycles) - Chunked to prevent truncation
                    if (cycleCount % 3 == 0) {
                        if (detectedProtocol.contains("CAN") && normalPriorityPids.size > 1) {
                            normalPriorityPids.chunked(6).forEach { chunk ->
                                pollMultiPidBatch(chunk)
                            }
                        } else {
                            pollBatch(normalPriorityPids.map { "01$it" })
                        }
                    }

                    // 3. Poll OEM PIDs (Every 4 cycles)
                    if (cycleCount % 4 == 0 && oemPidsToPoll.isNotEmpty()) {
                        pollOemBatch()
                    }

                    // 4. Poll Custom PIDs (Every 5 cycles)
                    if (cycleCount % 5 == 0 && customPidsToPoll.isNotEmpty()) {
                        pollCustomBatch()
                    }

                    // 5. Poll ELM327 Battery Voltage directly (Every 10 cycles)
                    if (cycleCount % 10 == 0) {
                        try {
                            val (ecuVolt, elmVolt) = readBatteryVoltage()
                            if (ecuVolt > 0f) {
                                updateLiveData("0142", ecuVolt)
                            }
                            if (elmVolt > 0f) {
                                updateLiveData("AT RV", elmVolt)
                            }
                        } catch (_: Exception) {}
                    }
                }

                updateQos(System.currentTimeMillis() - cycleStartTime)
                cycleCount++

                // Adaptive delay: High speed mode on pro adapter has 0 delay
                val targetDelay = when {
                    _highSpeedMode.value && !isCloneAdapter -> 2L // Minimal breathing time
                    _highSpeedMode.value && isCloneAdapter -> 15L
                    isCloneAdapter -> 40L  // Was 80L — modern clones handle 25Hz+
                    else -> 5L
                }
                if (targetDelay > 0) delay(targetDelay)
            }
        }
    }

    private fun getPidDataSize(pid: String): Int {
        val pidHex = pid.uppercase().removePrefix("01")
        return when (pidHex) {
            "00", "20", "40", "60", "80", "A0", "C0" -> 4
            "01", "41" -> 4
            "02", "03" -> 2
            "0C", "10", "1F", "21", "22", "23", "31", "32", "3C", "3D", "3E", "3F", "42", "43", "44", "5E", "63" -> 2
            "24", "25", "26", "27", "28", "29", "2A", "2B", "34", "35", "36", "37", "38", "39", "3A", "3B" -> 4
            else -> 1
        }
    }

    private fun parseMultiPidResponse(pids: List<String>, response: String) {
        val clean = CanMultiFrameParser.parse(response).uppercase().replace(" ", "")
        Log.d(TAG, "Parsing multi-PID response: clean=$clean for pids=$pids")

        if (clean.isBlank() || clean.contains("NODATA") || clean.contains("ERROR")) return

        var parsedCount = 0

        // Strategy 1: Look for repeated "41 XX DATA" patterns (most common ECU behavior)
        // Each PID in the multi-request gets its own "41" service-response prefix
        for (pid in pids) {
            val pidHex = pid.uppercase()
            val marker = "41$pidHex"
            var idx = clean.indexOf(marker)
            while (idx >= 0 && idx % 2 != 0) {
                idx = clean.indexOf(marker, idx + 1)
            }
            if (idx >= 0) {
                val dataStart = idx + marker.length
                val size = getPidDataSize(pidHex)
                val dataEnd = dataStart + (size * 2)
                if (dataEnd <= clean.length) {
                    val dataHex = clean.substring(dataStart, dataEnd)
                    try {
                        val def = PidRegistry.getPid("01", pidHex)
                        if (def != null) {
                            val a = if (dataHex.length >= 2) dataHex.substring(0, 2).toInt(16) else 0
                            val b = if (dataHex.length >= 4) dataHex.substring(2, 4).toInt(16) else 0
                            val c = if (dataHex.length >= 6) dataHex.substring(4, 6).toInt(16) else 0
                            val d = if (dataHex.length >= 8) dataHex.substring(6, 8).toInt(16) else 0
                            val value = def.formula(a, b, c, d)
                            updateLiveData("01$pidHex", value)
                            parsedCount++
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Multi-PID parse error for $pidHex: ${e.message}")
                    }
                }
            }
        }

        if (parsedCount > 0) {
            Log.d(TAG, "Multi-PID parsed $parsedCount/${pids.size} PIDs successfully")
        } else {
            // Strategy 2: Fallback — use existing single-PID parser per PID
            Log.w(TAG, "Multi-PID structured parse failed, falling back to per-PID parsePidResponse")
            for (pid in pids) {
                val parsed = parsePidResponse("01${pid.uppercase()}", response)
                if (parsed != null) {
                    updateLiveData("01${pid.uppercase()}", parsed)
                    parsedCount++
                }
            }
            Log.d(TAG, "Fallback parsed $parsedCount/${pids.size} PIDs")
        }
    }

    /**
     * Polls multiple PIDs in a single request.
     * Supported by most CAN-bus ECUs. Reduces bus overhead significantly.
     * Falls back to individual polling if multi-PID parsing fails entirely.
     */
    private suspend fun pollMultiPidBatch(pids: List<String>) {
        if (pids.isEmpty()) return
        try {
            val command = "01" + pids.joinToString("")
            val response = sendRawCommand(command)
            parseMultiPidResponse(pids, response)
        } catch (e: Exception) {
            // Multi-PID request failed entirely — fall back to individual polling
            Log.w(TAG, "Multi-PID batch failed, falling back to individual: ${e.message}")
            pollBatch(pids.map { "01$it" })
        }
    }

    private fun updateQos(latencyMs: Long, success: Boolean = true) {
        val now = System.currentTimeMillis()
        cmdCount++

        val current = _qosMetrics.value
        val total = current.totalRequests + 1
        val successful = if (success) current.successfulRequests + 1 else current.successfulRequests
        val reliability = (successful.toFloat() / total.toFloat()) * 100f

        if (now - lastCmdTime >= 1000) {
            _qosMetrics.value = current.copy(
                cmdsPerSecond = cmdCount.toFloat(),
                latencyMs = latencyMs.toInt(),
                isStable = latencyMs < 500,
                avgLatencyMs = if (current.avgLatencyMs == 0f) latencyMs.toFloat() else (current.avgLatencyMs * 0.9f + latencyMs * 0.1f),
                totalRequests = total,
                successfulRequests = successful,
                reliability = reliability
            )
            cmdCount = 0
            lastCmdTime = now
        } else {
            _qosMetrics.value = current.copy(
                totalRequests = total,
                successfulRequests = successful,
                reliability = reliability,
                avgLatencyMs = if (current.avgLatencyMs == 0f) latencyMs.toFloat() else (current.avgLatencyMs * 0.9f + latencyMs * 0.1f)
            )
        }
    }

    private suspend fun pollOemBatch() {
        for (pidDef in oemPidsToPoll) {
            if (!isRunning) return
            try {
                val command = pidDef.mode + pidDef.pid
                val response = sendRawCommand(command, priority = 1)
                val parsed = parseOemResponse(pidDef, response)
                if (parsed != null) updateLiveData(pidDef.name, parsed)
            } catch (_: Exception) {}
        }
    }

    private fun parseOemResponse(def: PidDefinition, raw: String): Float? {
        val clean = CanMultiFrameParser.parse(raw)
        val expectedPrefix = (def.mode.toInt(16) + 0x40).toString(16).uppercase() + def.pid.uppercase()
        val idx = clean.uppercase().indexOf(expectedPrefix)
        if (idx < 0) return null
        val dataHex = clean.substring(idx + expectedPrefix.length)

        return try {
            val a = if (dataHex.length >= 2) dataHex.substring(0, 2).toInt(16) else 0
            val b = if (dataHex.length >= 4) dataHex.substring(2, 4).toInt(16) else 0
            val c = if (dataHex.length >= 6) dataHex.substring(4, 6).toInt(16) else 0
            val d = if (dataHex.length >= 8) dataHex.substring(6, 8).toInt(16) else 0
            def.formula(a, b, c, d)
        } catch (_: Exception) { null }
    }

    private suspend fun pollBatch(pids: List<String>) {
        for (pid in pids) {
            if (!isRunning) return
            try {
                val result = CompletableDeferred<String>()
                commandQueue.enqueue(ObdCommand(pid, 0, { result.complete(it) }, { result.complete("") }))
                val response = withTimeoutOrNull(800) { result.await() } ?: continue
                val parsed = parsePidResponse(pid, response)
                if (parsed != null) updateLiveData(pid, parsed)
            } catch (_: Exception) {}
        }
    }

    private suspend fun pollCustomBatch() {
        for (cp in customPidsToPoll) {
            if (!isRunning) return
            try {
                val result = CompletableDeferred<String>()
                val command = cp.mode + cp.pid
                commandQueue.enqueue(ObdCommand(command, 0, { result.complete(it) }, { result.complete("") }))
                val response = withTimeoutOrNull(2000) { result.await() } ?: continue

                val clean = CanMultiFrameParser.parse(response)
                // Extract bytes after mode + pid
                // OBD response prefix = request mode + 0x40 (hex), e.g. mode 22 → response 62
                val responseMode = (cp.mode.toInt(16) + 0x40).toString(16).uppercase()
                val prefix = responseMode + cp.pid
                val idx = clean.uppercase().indexOf(prefix.uppercase())
                if (idx >= 0) {
                    val dataHex = clean.substring(idx + prefix.length)
                    val bytes = mutableListOf<Int>()
                    for (i in 0 until dataHex.length - 1 step 2) {
                        bytes.add(dataHex.substring(i, i + 2).toInt(16))
                    }

                    if (cp.formula.isNotBlank()) {
                        val value = FormulaEvaluator.evaluate(cp.formula, bytes)
                        updateLiveData(cp.id.toString(), value)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // ═══════════════════════════════════════════════
    // CALCULATED SENSORS ENGINE (Car Scanner Pro Full)
    // ═══════════════════════════════════════════════
    private var lastSpeedKph = 0f
    private var lastSpeedTimestampMs = 0L
    private var tripDistanceKm = 0.0
    private var tripFuelUsedL = 0.0
    private var tripStartTimeMs = 0L
    private var speedAccumulator = 0.0
    private var speedSampleCount = 0
    // Total accumulators (persist across trips within session)
    private var totalDistanceKm = 0.0
    private var totalFuelUsedL = 0.0
    // Rolling 10-second fuel consumption window
    private val fuelRateHistory = ArrayDeque<Pair<Long, Float>>(100) // timestamp to L/h
    // Fuel price per liter (configurable)
    var fuelPricePerLiter: Float = 1.0f // default $1/L, user can change

    private fun updateLiveData(pid: String, value: Float) {
        // 1. Apply user/auto calibration offset
        val offset = calibrationOffsets.value[pid] ?: 0f
        val calibratedValue = value + offset

        // 2. Apply smoothing and outlier rejection
        // The PID to smooth is usually the hex code.
        val corePid = pid.removePrefix("01")
        val smoothedValue = if (corePid == "42" || corePid == "AT RV" || corePid == "ATRV") {
            calibratedValue // Bypass smoothing for voltage to allow oscilloscope raw reading
        } else {
            sensorSmoother.smooth(corePid, calibratedValue)
        }

        val current = _liveData.value.toMutableMap()
        current[pid] = smoothedValue

        // ── Compute derived/calculated sensors ──
        computeCalculatedSensors(current)

        _liveData.value = current
    }

    private fun computeCalculatedSensors(data: MutableMap<String, Float>) {
        val mafGps = data["0110"]     // MAF flow (g/s)
        val speedKph = data["010D"]   // Vehicle speed (km/h)
        val rpmVal = data["010C"]     // Engine RPM
        val mapKpa = data["010B"]     // MAP pressure (kPa)
        val baroKpa = data["0133"]    // Barometric pressure (kPa)
        val loadPct = data["0104"]    // Engine load (%)

        val now = System.currentTimeMillis()
        val previousSpeedTimestampMs = lastSpeedTimestampMs
        val previousSpeedKph = lastSpeedKph
        val sampleDeltaTimeSec = if (previousSpeedTimestampMs > 0L) {
            (now - previousSpeedTimestampMs).coerceIn(0L, 5000L) / 1000.0
        } else {
            0.0
        }
        if (tripStartTimeMs == 0L) tripStartTimeMs = now

        // ── 1. Instant Fuel Rate (L/h) ──
        var fuelRateLh: Float? = null
        if (mafGps != null && mafGps > 0) {
            fuelRateLh = (mafGps * 3600f) / (14.7f * 820f)
            data["CALC_FUEL_RATE"] = fuelRateLh
        } else if (loadPct != null && rpmVal != null && rpmVal > 0) {
            val volEfficiency = loadPct / 100f
            val airflowEstimate = rpmVal / 120f * 2.0f * volEfficiency * 1.184f
            fuelRateLh = (airflowEstimate * 3600f) / (14.7f * 820f)
            data["CALC_FUEL_RATE"] = fuelRateLh
        }

        // ── 2. Instant Fuel Consumption (L/100km) ──
        if (fuelRateLh != null && speedKph != null && speedKph > 2f) {
            val consumption = fuelRateLh * 100f / speedKph
            data["CALC_FUEL_CONSUMPTION"] = consumption.coerceIn(0f, 99.9f)
        } else {
            data["CALC_FUEL_CONSUMPTION"] = 0f
        }

        // ── 3. Vehicle Acceleration (g) ──
        if (speedKph != null) {
            val deltaT = (now - previousSpeedTimestampMs) / 1000f
            if (previousSpeedTimestampMs > 0 && deltaT > 0.05f && deltaT < 5f) {
                val deltaSpeed = (speedKph - previousSpeedKph) / 3.6f
                val accelG = deltaSpeed / (deltaT * 9.81f)
                data["CALC_ACCELERATION"] = accelG.coerceIn(-3f, 3f)
            }
        }

        // ── 4. Calculated Boost (bar) ──
        if (mapKpa != null) {
            val referencePressure = baroKpa ?: 101.325f
            val boostBar = (mapKpa - referencePressure) / 100f
            data["CALC_BOOST"] = boostBar
        }

        // ── 5. Instant Engine Power (hp) ──
        if (fuelRateLh != null) {
            val powerKw = fuelRateLh * 820f * 43000f * 0.25f / 3600f / 1000f
            data["CALC_POWER"] = powerKw * 1.341f
        }

        // ── 6. Engine RPM × 1000 ──
        if (rpmVal != null) {
            data["CALC_RPM_K"] = rpmVal / 1000f
        }

        // ── 7. Trip + Total Accumulators ──
        if (speedKph != null && previousSpeedTimestampMs > 0 && sampleDeltaTimeSec > 0.0) {
            val distIncrementKm = speedKph / 3600.0 * sampleDeltaTimeSec
            tripDistanceKm += distIncrementKm
            totalDistanceKm += distIncrementKm
            data["CALC_TRIP_DISTANCE"] = tripDistanceKm.toFloat()
            data["CALC_TOTAL_DISTANCE"] = totalDistanceKm.toFloat()

            speedAccumulator += speedKph
            speedSampleCount++
            data["CALC_AVG_SPEED"] = (speedAccumulator / speedSampleCount).toFloat()
        }

        // Fuel used (trip + total)
        if (fuelRateLh != null && previousSpeedTimestampMs > 0 && sampleDeltaTimeSec > 0.0) {
            val fuelIncrement = fuelRateLh / 3600.0 * sampleDeltaTimeSec
            tripFuelUsedL += fuelIncrement
            totalFuelUsedL += fuelIncrement
            data["CALC_FUEL_USED"] = tripFuelUsedL.toFloat()
            data["CALC_FUEL_USED_TOTAL"] = totalFuelUsedL.toFloat()

            // Trip average consumption
            if (tripDistanceKm > 0.01) {
                data["CALC_AVG_CONSUMPTION"] = (tripFuelUsedL / tripDistanceKm * 100.0).toFloat()
            }
            // Total average consumption
            if (totalDistanceKm > 0.01) {
                data["CALC_AVG_CONSUMPTION_TOTAL"] = (totalFuelUsedL / totalDistanceKm * 100.0).toFloat()
            }

            // ── Fuel Price ──
            data["CALC_FUEL_PRICE"] = (tripFuelUsedL * fuelPricePerLiter).toFloat()
            data["CALC_FUEL_PRICE_TOTAL"] = (totalFuelUsedL * fuelPricePerLiter).toFloat()
        }

        // ── 8. Rolling 10-second Average Fuel Consumption ──
        if (fuelRateLh != null) {
            fuelRateHistory.addLast(Pair(now, fuelRateLh))
            // Remove entries older than 10 seconds
            while (fuelRateHistory.isNotEmpty() && now - fuelRateHistory.first().first > 10000) {
                fuelRateHistory.removeFirst()
            }
            if (fuelRateHistory.isNotEmpty() && speedKph != null && speedKph > 2f) {
                val avgRate = fuelRateHistory.map { it.second }.average().toFloat()
                val consumption10s = avgRate * 100f / speedKph
                data["CALC_AVG_CONSUMPTION_10S"] = consumption10s.coerceIn(0f, 99.9f)
            } else {
                data["CALC_AVG_CONSUMPTION_10S"] = 0f
            }
        }

        // ── 9. Fuel Economizer ──
        val fuelStatus = data["0103"]
        val throttle = data["0111"]
        if (fuelStatus != null && throttle != null) {
            val isClosedLoop = fuelStatus.toInt() == 2
            data["CALC_FUEL_ECON"] = if (isClosedLoop && throttle < 30) 1f else 0f
        }

        // ── 10. Monitor Status (PID 01 01) ──
        // Byte A: bit7=MIL, bits0-6=DTC count
        // Bytes B,C,D encode readiness tests
        val monitorRaw = data["0101"]
        if (monitorRaw != null) {
            val rawInt = monitorRaw.toInt()
            // This is byte A — MIL is bit 7, DTC count is bits 0-6
            data["CALC_MIL_STATUS"] = if (rawInt and 0x80 != 0) 1f else 0f
            data["CALC_DTC_COUNT"] = (rawInt and 0x7F).toFloat()
        }

        // ── 11. Fuel System Status Descriptor (PID 0103) ──
        // 1=OL-cold, 2=CL-O2, 4=OL-drive, 8=OL-fault, 16=CL-fault
        if (fuelStatus != null) {
            data["CALC_FUEL_STATUS_CODE"] = fuelStatus
        }

        // ── 12. OBD Standard Descriptor (PID 011C) ──
        val obdStd = data["011C"]
        if (obdStd != null) {
            data["CALC_OBD_STANDARD"] = obdStd
        }

        // ── 13. Current Time (display) ──
        val cal = java.util.Calendar.getInstance()
        data["CALC_CURRENT_TIME"] = (cal.get(java.util.Calendar.HOUR_OF_DAY) * 100f + cal.get(java.util.Calendar.MINUTE))

        // ── 14. Driving/Standing Time Tracking ──
        updateDrivingTime(speedKph)

        if (speedKph != null) {
            lastSpeedKph = speedKph
            lastSpeedTimestampMs = now
        }
    }

    /**
     * Resets trip accumulators (distance, fuel used, avg speed, avg consumption)
     * but preserves total accumulators.
     */
    fun resetTrip() {
        tripDistanceKm = 0.0
        tripFuelUsedL = 0.0
        speedAccumulator = 0.0
        speedSampleCount = 0
        tripStartTimeMs = System.currentTimeMillis()
        fuelRateHistory.clear()
        Log.i(TAG, "Trip accumulators reset")
    }


    private suspend fun detectSupportedPids(): Set<Int> {
        val supported = mutableSetOf<Int>()
        var nextQueryPid = 0x00
        
        while (nextQueryPid <= 0xE0) {
            val queryStr = "01" + String.format("%02X", nextQueryPid)
            var pageSupported = false
            try {
                val resp = sendRawCommand(queryStr)
                val clean = CanMultiFrameParser.parse(resp)
                val modeResp = "41" + String.format("%02X", nextQueryPid)
                val idx = clean.uppercase().indexOf(modeResp.uppercase())
                if (idx >= 0) {
                    val hex = clean.substring(idx + modeResp.length).take(8)
                    val bitmap = hex.toLongOrNull(16)
                    if (bitmap != null) {
                        for (bit in 31 downTo 0) {
                            if ((bitmap shr bit) and 1L == 1L) {
                                supported.add(nextQueryPid + (32 - bit))
                            }
                        }
                        pageSupported = true
                    }
                }
            } catch (_: Exception) {}
            
            val nextIndicatorPid = nextQueryPid + 32
            if (pageSupported && supported.contains(nextIndicatorPid)) {
                nextQueryPid = nextIndicatorPid
            } else {
                break
            }
        }
        return supported
    }

    private fun parsePidResponse(pid: String, raw: String): Float? {
        val clean = CanMultiFrameParser.parse(raw)
        val pidHex = pid.substring(2).uppercase()
        val expectedPrefix = "41$pidHex"
        var idx = clean.indexOf(expectedPrefix)
        while (idx >= 0 && idx % 2 != 0) {
            idx = clean.indexOf(expectedPrefix, idx + 1)
        }
        if (idx < 0) return null
        val dataHex = clean.substring(idx + expectedPrefix.length)

        return try {
            val def = PidRegistry.getPid("01", pidHex)
            if (def != null) {
                val a = if (dataHex.length >= 2) dataHex.substring(0, 2).toInt(16) else 0
                val b = if (dataHex.length >= 4) dataHex.substring(2, 4).toInt(16) else 0
                val c = if (dataHex.length >= 6) dataHex.substring(4, 6).toInt(16) else 0
                val d = if (dataHex.length >= 8) dataHex.substring(6, 8).toInt(16) else 0
                def.formula(a, b, c, d)
            } else null
        } catch (_: Exception) { null }
    }

    // ═══════════════════════════════════════════════
    // PROFESSIONAL DIAGNOSTIC MODES
    // ═══════════════════════════════════════════════

    /**
     * Scans for active ECUs on the CAN bus by pinging standard addresses.
     * Disruptive high-end feature: Topology Mapping.
     */
    suspend fun scanNetworkTopology() {
        if (_state.value != ObdState.CONNECTED) {
            _statusMessage.value = "Error: El escaneo de topología requiere conexión activa."
            return
        }
        _isScanningTopology.value = true
        val discovered = mutableListOf<NetworkModule>()

        // Standard CAN IDs (11-bit) for physical addressing
        val targetNodes = mapOf(
            "7E0" to ("Engine Control Module (ECM)" to NetworkType.CAN_HIGH),
            "7E1" to ("Transmission Control Module (TCM)" to NetworkType.CAN_HIGH),
            "7E2" to ("Anti-lock Braking System (ABS)" to NetworkType.CAN_HIGH),
            "7E3" to ("Supplemental Restraint System (SRS)" to NetworkType.CAN_LOW),
            "7E4" to ("Body Control Module (BCM)" to NetworkType.CAN_LOW),
            "7E5" to ("Instrument Cluster (IPC)" to NetworkType.CAN_LOW),
            "7E6" to ("HVAC Module" to NetworkType.LIN),
            "7E7" to ("Power Steering Control Module" to NetworkType.CAN_HIGH),
            "7E8" to ("ECM Secondary" to NetworkType.CAN_HIGH),
            "7E9" to ("Hybrid/EV Battery Pack" to NetworkType.CAN_HIGH),
            "7EA" to ("Active Suspension" to NetworkType.CAN_HIGH),
            "7EB" to ("Gateway Module" to NetworkType.CAN_HIGH)
        )

        try {
            // Physical/Real scan
            for ((id, data) in targetNodes) {
                currentCoroutineContext().ensureActive()
                val (name, type) = data
                _statusMessage.value = "Escaneando Nodo: $name ($id)..."

                val startTime = System.currentTimeMillis()
                var success = false
                var dtcs = emptyList<String>()

                try {
                    withTimeoutOrNull(2000L) { // Safe 2000ms timeout for slow/genuine adapters
                        sendRawCommand("AT SH $id")
                        id.toIntOrNull(16)?.let { requestHex ->
                            val responseId = String.format("%03X", requestHex + 8)
                            sendRawCommand("AT CRA $responseId")
                        }
                        val resp = sendRawCommand("0100")
                        success = resp.isNotBlank() && (resp.contains("41 00") || resp.contains("4100"))
                        if (success) {
                            // Query DTCs immediately if alive
                            val dtcResp = sendRawCommand("03")
                            dtcs = DtcDecoder.decode(dtcResp, "03")
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    success = false
                } finally {
                    runCatching { sendRawCommand("AT CRA") }
                }

                val latency = System.currentTimeMillis() - startTime
                discovered.add(NetworkModule(id, name, isAlive = success, networkType = type, latencyMs = latency, dtcs = dtcs))
            }

            _networkTopology.value = discovered
            _statusMessage.value = "Mapeo de Topología Completo: ${discovered.count { it.isAlive }} nodos activos."
        } catch (e: CancellationException) {
            _statusMessage.value = "Escaneo cancelado por el usuario."
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Topology scan failed", e)
        } finally {
            runCatching { sendRawCommand("AT SH 7DF") }
            runCatching { sendRawCommand("AT CRA") }
            _isScanningTopology.value = false
        }
    }

    /**
     * High-speed burst data capture using STN-specific STP commands.
     * Emulates a digital oscilloscope.
     */
    fun startOscilloscope(pidCode: String) {
        oscilloscopeJob?.cancel()
        oscilloscopeJob = scope.launch(Dispatchers.IO) {
            try {
                _statusMessage.value = "Iniciando ráfaga de alta velocidad (Osciloscopio)..."

                // Check if adapter is STN (Professional)
                if (!_isAdapterPro.value) {
                    // Fallback to high-frequency standard polling if not STN
                    while (isActive && isRunning) {
                        val t = System.currentTimeMillis()
                        val result = CompletableDeferred<String>()
                        commandQueue.enqueue(ObdCommand("01$pidCode", 0, { result.complete(it) }, { result.complete("") }))
                        val resp = result.await()
                        val value = parsePidResponse("01$pidCode", resp)
                        if (value != null) _oscilloscopeStream.emit(t to value)
                        delay(5) // Max 200Hz fallback
                    }
                    return@launch
                }

                // PROFESSIONAL STN PATH: STP (Real Time Protocol)
                // STP provides timestamped data at up to 1000 samples/sec
                // Sequence: 1. Set Protocol 2. Start STP 3. Monitor data stream

                // Note: STP implementation varies by chip version.
                // Using a simplified ráfaga pattern for implementation.
                sendRawCommand("STP $pidCode")

                while (isActive && isRunning) {
                    // Read raw stream directly from transport (bypassing queue)
                    val rawBytes = transport?.read(256, 50L)
                    val rawData = rawBytes?.toString(Charsets.US_ASCII) ?: ""
                    if (rawData.isNotBlank()) {
                        val lines = rawData.split("\r", "\n")
                        for (line in lines) {
                            if (line.length >= 4) {
                                // Parse high-speed packet: [Timestamp][Value]
                                // Simplified for this implementation
                                val value = parsePidResponse("01$pidCode", line)
                                if (value != null) {
                                    _oscilloscopeStream.emit(System.currentTimeMillis() to value)
                                }
                            }
                        }
                    }
                    delay(1)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Oscilloscope mode error", e)
            } finally {
                // Stop STP
                try { sendRawCommand("STP STOP") } catch (_: Exception) {}
            }
        }
    }

    fun stopOscilloscope() {
        oscilloscopeJob?.cancel()
        oscilloscopeJob = null
        if (_state.value == ObdState.CONNECTED) {
            scope.launch {
                try { sendRawCommand("STP STOP") } catch (_: Exception) { }
            }
        }
    }

    suspend fun readActiveDtcs(): List<String> {
        if (_state.value != ObdState.CONNECTED) return emptyList()
        return try {
            val response = sendRawCommand("03", priority = 999)
            val active = DtcDecoder.decode(response, "03")
            _allDetectedDtcs.value = _allDetectedDtcs.value + active
            active
        } catch (_: Exception) { emptyList() }
    }

    /**
     * Realiza un escaneo profundo en la memoria Mode 06 (Resultados de Pruebas de Monitoreo)
     * para extraer conteos de misfire por cilindro.
     */
    private suspend fun deepScanMisfires(): List<String> {
        val detectedCylinders = mutableListOf<String>()
        try {
            // Mode 06 Misfire Cylinder IDs (A2 = Cyl 1 ... A9 = Cyl 8)
            for (cyl in 1..8) {
                val hexMid = String.format("%02X", 0xA1 + cyl)
                val response = sendRawCommand("06$hexMid", priority = 999)
                // Usa CanMultiFrameParser para limpiar headers PCI si es necesario.
                val clean = CanMultiFrameParser.parse(response).replace(Regex("[^0-9A-F]"), "")

                // Formato CAN/KWP típico: 46 A2 0B [VAL] [VAL] [MIN] [MIN] [MAX] [MAX]
                // Buscamos si hay datos y extraemos el valor (simplificado)
                if (clean.contains("46$hexMid")) {
                    val idx = clean.indexOf("46$hexMid")
                    if (clean.length >= idx + 10) {
                        // Saltamos "46" + MID + TID (6 caracteres = 3 bytes)
                        // Extraemos los siguientes 4 caracteres (2 bytes) como el valor actual.
                        val valueHex = clean.substring(idx + 6, idx + 10)
                        val value = valueHex.toIntOrNull(16) ?: 0
                        if (value > 0) {
                            detectedCylinders.add(String.format("P03%02d", cyl))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "deepScanMisfires failed: ${e.message}")
        }
        return detectedCylinders
    }

    suspend fun readPendingDtcs(): List<String> {
        if (_state.value != ObdState.CONNECTED) return emptyList()
        return try {
            val response = sendRawCommand("07", priority = 999)
            DtcDecoder.decode(response, "07")
        } catch (_: Exception) { emptyList() }
    }

    /**
     * Reads Freeze Frame data for a specific DTC (Mode 02).
     * @param dtc The fault code to query.
     */
    suspend fun readFreezeFrame(dtc: String): Map<String, String> {
        val results = mutableMapOf<String, String>()

        // Mode 02 PID 02: DTC that caused freeze frame
        val dtcResp = sendRawCommand("020200") // Frame 0
        if (dtcResp.contains("NODATA") || dtcResp.contains("?")) return emptyMap()

        // NOTE: "DTC" key removed — ViewModel now uses scoped keys (dtc:param).
        // The DTC identity is managed by the caller, not embedded in the frame map.

        // Common PIDs for engine snapshot
        val pids = listOf(
            "020300", // Fuel system status
            "020400", // Calculated load
            "020500", // Coolant temp
            "020600", // Short term fuel trim
            "020700", // Long term fuel trim
            "020C00", // Engine RPM
            "020D00", // Vehicle speed
            "021100"  // Throttle position
        )

        for (cmd in pids) {
            val pid = cmd.substring(2, 4)
            val res = sendRawCommand(cmd)
            if (!res.contains("NODATA") && !res.contains("?")) {
                results[pid] = parseMode02Response(pid, res)
            }
        }

        _freezeFrame.value = results
        return results
    }

    private fun parseMode02Response(pid: String, response: String): String {
        val clean = CanMultiFrameParser.parse(response).replace(" ", "")
        val prefix = "42$pid"
        val idx = clean.uppercase().indexOf(prefix)
        if (idx < 0) return "N/A"

        val data = clean.substring(idx + prefix.length)
        if (data.length < 2) return "N/A"

        return when(pid) {
            "02" -> {
                // DTCs in Mode 02 are encoded differently
                if (data.length < 4) return "N/A"
                val b1 = data.substring(0, 2).toInt(16)
                val b2 = data.substring(2, 4).toInt(16)
                DtcDecoder.hexToDtc(b1, b2)
            }
            "05" -> if (data.length >= 2) "${data.substring(0, 2).toInt(16) - 40}°C" else "N/A"
            "0C" -> if (data.length >= 4) "${(data.substring(0, 4).toInt(16)) / 4} RPM" else "N/A"
            "0D" -> if (data.length >= 2) "${data.substring(0, 2).toInt(16)} km/h" else "N/A"
            "04", "11" -> if (data.length >= 2) "${(data.substring(0, 2).toInt(16) * 100 / 255)}%" else "N/A"
            "03" -> if (data.length >= 2) { if (data.substring(0, 2).toInt(16) == 2) "Closed Loop" else "Open Loop" } else "N/A"
            else -> data
        }
    }

    // Consolidated safety guard moved to line 824 area


    suspend fun readPermanentDtcs(): List<String> {
        if (_state.value != ObdState.CONNECTED) return emptyList()
        return try {
            val response = sendRawCommand("0A", priority = 999)
            DtcDecoder.decode(response, "0A")
        } catch (_: Exception) { emptyList() }
    }

    suspend fun readProfessionalDtcScan(): DtcScanReport {
        val startedAt = System.currentTimeMillis()
        if (_state.value != ObdState.CONNECTED) {
            return DtcScanReport(startedAt, System.currentTimeMillis(), detectedProtocol, emptyList(), emptyList(), emptyList())
        }

        val records = mutableListOf<DtcRecord>()
        val rawExchanges = mutableListOf<DtcRawExchange>()
        val aliveModules = linkedMapOf<String, String>()

        fun normalizeRecord(record: DtcRecord, fallbackName: String?): DtcRecord {
            val responseName = moduleNameForResponse(record.responseAddress)
            return record.copy(moduleName = record.moduleName ?: fallbackName ?: responseName)
        }

        suspend fun queryStandard(command: String, mode: String, target: String?, moduleName: String?) {
            val raw = runCatching { sendRawCommand(command, priority = 999) }.getOrDefault("")
            val parsed = DtcScanEngine.parseStandardByEcu(raw, mode, target, moduleName)
                .map { normalizeRecord(it, moduleName) }
            rawExchanges += DtcRawExchange(command, target, raw, parsed.size)
            records += parsed
        }

        suspend fun queryUds(command: String, target: String?, moduleName: String?): List<DtcRecord> {
            val raw = runCatching { sendRawCommand(command, priority = 999) }.getOrDefault("")
            val parsed = DtcScanEngine.parseUdsService19ByEcu(raw, target, moduleName)
                .map { normalizeRecord(it, moduleName) }
            rawExchanges += DtcRawExchange(command, target, raw, parsed.size)
            records += parsed
            return parsed
        }

        fun isAliveResponse(raw: String): Boolean {
            val u = raw.uppercase()
            if (u.isBlank() || u.contains("NO DATA") || u.contains("UNABLE") || u.contains("ERROR") || u.trim() == "?") {
                return false
            }
            return u.contains("41 00") ||
                u.contains("4100") ||
                u.contains("50 03") ||
                u.contains("5003") ||
                u.contains("50 01") ||
                u.contains("5001") ||
                u.contains("7F") ||
                u.contains("59")
        }

        val isCan = detectedProtocol.uppercase().contains("CAN") || detectedProtocol.uppercase().contains("ISO15765")
        try {
            if (isCan) {
                _statusMessage.value = "Escaneo DTC profesional: configurando bus CAN..."
                runCatching { sendRawCommand("ATH1", priority = 999) }
                runCatching { sendRawCommand("ATAL", priority = 999) }
                runCatching { sendRawCommand("ATCAF1", priority = 999) }

                // Phase 1: functional broadcast with headers.
                runCatching { sendRawCommand("ATSH7DF", priority = 999) }
                queryStandard("03", "03", "7DF", "Functional Broadcast")
                queryStandard("07", "07", "7DF", "Functional Broadcast")
                queryStandard("0A", "0A", "7DF", "Functional Broadcast")

                // Phase 2: physical module sweep
                for ((target, moduleName) in professionalDtcTargets()) {
                    _statusMessage.value = "Escaneando $moduleName ($target)..."
                    runCatching { sendRawCommand("ATSH$target", priority = 999) }
                    target.toIntOrNull(16)?.let { requestHex ->
                        val responseId = String.format("%03X", requestHex + 8)
                        runCatching { sendRawCommand("ATCRA$responseId", priority = 999) }
                    }

                    try {
                        val probeRaw = runCatching { sendRawCommand("0100", priority = 999) }.getOrDefault("")
                        var alive = isAliveResponse(probeRaw)
                        rawExchanges += DtcRawExchange("0100", target, probeRaw, 0)

                        if (!alive) {
                            val testerRaw = runCatching { sendRawCommand("3E00", priority = 999) }.getOrDefault("")
                            alive = isAliveResponse(testerRaw)
                            rawExchanges += DtcRawExchange("3E00", target, testerRaw, 0)
                        }

                        if (!alive) continue
                        aliveModules[target] = moduleName

                        runCatching { sendRawCommand("1003", priority = 999) }
                        val udsAll = queryUds("1902FF", target, moduleName)
                        if (udsAll.isEmpty()) {
                            queryUds("19020D", target, moduleName)
                        }
                        queryStandard("03", "03", target, moduleName)
                        queryStandard("07", "07", target, moduleName)
                        queryStandard("0A", "0A", target, moduleName)
                    } finally {
                        runCatching { sendRawCommand("ATCRA", priority = 999) }
                    }
                }
            } else {
                _statusMessage.value = "Escaneo DTC estándar: consultando protocolo legado..."
                runCatching { sendRawCommand("ATH0", priority = 999) }
                // Consult standard Modes globally without CAN addressing
                queryStandard("03", "03", null, "Standard OBD-II")
                queryStandard("07", "07", null, "Standard OBD-II")
                queryStandard("0A", "0A", null, "Standard OBD-II")
            }
        } finally {
            if (isCan) {
                runCatching { sendRawCommand("ATSH7DF", priority = 999) }
                runCatching { sendRawCommand("ATCRA", priority = 999) }
                runCatching { sendRawCommand("ATH0", priority = 999) }
            }
        }

        val distinctRecords = records.distinctBy {
            "${it.code}|${it.bucket}|${it.responseAddress}|${it.targetAddress}|${it.udsStatusByte}|${it.udsFailureType}"
        }
        val moduleKeys = (aliveModules.keys + distinctRecords.mapNotNull { it.responseAddress ?: it.targetAddress }).distinct()
        val modules = moduleKeys.map { key ->
            val moduleRecords = distinctRecords.filter { it.responseAddress == key || (it.responseAddress == null && it.targetAddress == key) }
            DtcModuleReport(
                targetAddress = aliveModules.keys.firstOrNull { it == key },
                responseAddress = if (key in aliveModules.keys) null else key,
                moduleName = aliveModules[key] ?: moduleNameForResponse(key) ?: "ECU $key",
                isAlive = true,
                dtcs = moduleRecords,
                rawExchanges = rawExchanges.filter { it.targetAddress == key || it.targetAddress == "7DF" }
            )
        }

        val report = DtcScanReport(
            startedAtMs = startedAt,
            endedAtMs = System.currentTimeMillis(),
            protocol = detectedProtocol,
            records = distinctRecords,
            modules = modules,
            rawExchanges = rawExchanges
        )
        _lastDtcScanReport.value = report
        _allDetectedDtcs.value = _allDetectedDtcs.value + distinctRecords.map { it.code }
        _statusMessage.value = "Escaneo DTC profesional completado: ${distinctRecords.size} hallazgos."
        return report
    }

    suspend fun clearDtcs(): Boolean {
        if (_state.value != ObdState.CONNECTED) return false
        return try {
            _statusMessage.value = "Enviando comando de borrado (Mode 04)..."
            val response = sendRawCommand("04", priority = 1)
            val success = response.contains("OK") || response.contains("44")
            if (success) {
                _statusMessage.value = "Verificando eliminación física (Mode 03)..."
                val checkResponse = sendRawCommand("03", priority = 1)
                val remainingCodes = DtcDecoder.decode(checkResponse, "03")
                if (remainingCodes.isNotEmpty()) {
                    _statusMessage.value = "Advertencia: ${remainingCodes.size} códigos no pudieron ser borrados."
                } else {
                    _statusMessage.value = "Borrado verificado exitosamente."
                    _allDetectedDtcs.value = emptySet()
                }
            } else {
                _statusMessage.value = "Error al borrar códigos: respuesta inesperada."
            }
            success
        } catch (e: Exception) {
            _statusMessage.value = "Error en borrado: ${e.message}"
            false
        }
    }

    suspend fun fetchVin(): String {
        if (_state.value != ObdState.CONNECTED) return "N/A"
        return try {
            val response = sendRawCommand("0902")
            val vin = CanMultiFrameParser.decodeVin(response)
            if (vin.isNotBlank() && vin != "N/A") {
                _vin.value = vin
                vin
            } else "N/A"
        } catch (_: Exception) { "Error al leer VIN" }
    }

    /**
     * Mode 09 PID 04 — Calibration ID.
     * Returns the ECU calibration identifier string.
     */
    suspend fun fetchCalibrationId(): String? {
        if (_state.value != ObdState.CONNECTED) return null
        return try {
            val response = sendRawCommand("0904")
            val parsed = CanMultiFrameParser.parse(response)
            // Strip response header (4904) and decode ASCII
            val clean = parsed.replace(Regex("\\s+"), "")
                .uppercase()
                .let { raw ->
                    val idx = raw.indexOf("4904")
                    if (idx >= 0) raw.substring(idx + 4) else raw
                }
            // Convert hex pairs to ASCII characters
            val calId = clean.chunked(2)
                .filter { it.length == 2 }
                .mapNotNull { runCatching { it.toInt(16).toChar() }.getOrNull() }
                .joinToString("")
                .replace(Regex("[^\\x20-\\x7E]"), "")
                .trim()
            if (calId.isNotBlank()) {
                _calibrationId.value = calId
                Log.i(TAG, "Calibration ID: $calId")
                calId
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "CalibrationId read failed: ${e.message}")
            null
        }
    }

    /**
     * Mode 09 PID 0A — ECU Name.
     * Returns the ECU name/identifier string (20 chars max per ISO 15031-5).
     */
    suspend fun fetchEcuName(): String? {
        if (_state.value != ObdState.CONNECTED) return null
        return try {
            val response = sendRawCommand("090A")
            val parsed = CanMultiFrameParser.parse(response)
            val clean = parsed.replace(Regex("\\s+"), "")
                .uppercase()
                .let { raw ->
                    val idx = raw.indexOf("490A")
                    if (idx >= 0) raw.substring(idx + 4) else raw
                }
            val ecuNameStr = clean.chunked(2)
                .filter { it.length == 2 }
                .mapNotNull { runCatching { it.toInt(16).toChar() }.getOrNull() }
                .joinToString("")
                .replace(Regex("[^\\x20-\\x7E]"), "")
                .trim()
            if (ecuNameStr.isNotBlank()) {
                _ecuName.value = ecuNameStr
                Log.i(TAG, "ECU Name: $ecuNameStr")
                ecuNameStr
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "ECU Name read failed: ${e.message}")
            null
        }
    }

    suspend fun readReadinessMonitors(): ReadinessResult? {
        if (_state.value != ObdState.CONNECTED) return null
        return try {
            val response = sendRawCommand("0101")
            val clean = CanMultiFrameParser.parse(response).replace("4101", "")
            if (clean.length < 8) return null

            val a = clean.substring(0, 2).toInt(16)
            val b = clean.substring(2, 4).toInt(16)
            val c = clean.substring(4, 6).toInt(16)
            val d = clean.substring(6, 8).toInt(16)

            val milOn = (a and 0x80) != 0
            val dtcCount = a and 0x7F

            val monitors = mutableListOf<MonitorStatus>()
            // Test de chispa vs compresión (Byte B bit 3)
            val isSpark = (b and 0x08) == 0

            // Monitores continuos (Byte B)
            monitors.add(MonitorStatus("Misfire", (b and 0x01) != 0, (b and 0x10) == 0))
            monitors.add(MonitorStatus("Fuel System", (b and 0x02) != 0, (b and 0x20) == 0))
            monitors.add(MonitorStatus("Components", (b and 0x04) != 0, (b and 0x40) == 0))

            // Monitores no continuos (Byte C y D)
            if (isSpark) {
                monitors.add(MonitorStatus("Catalyst", (c and 0x01) != 0, (d and 0x01) == 0))
                monitors.add(MonitorStatus("Heated Catalyst", (c and 0x02) != 0, (d and 0x02) == 0))
                monitors.add(MonitorStatus("EVAP System", (c and 0x04) != 0, (d and 0x04) == 0))
                monitors.add(MonitorStatus("Secondary Air", (c and 0x08) != 0, (d and 0x08) == 0))
                monitors.add(MonitorStatus("A/C Refrig.", (c and 0x10) != 0, (d and 0x10) == 0))
                monitors.add(MonitorStatus("O2 Sensor", (c and 0x20) != 0, (d and 0x20) == 0))
                monitors.add(MonitorStatus("O2 Heater", (c and 0x40) != 0, (d and 0x40) == 0))
                monitors.add(MonitorStatus("EGR System", (c and 0x80) != 0, (d and 0x80) == 0))
            } else {
                monitors.add(MonitorStatus("NMHC Cat", (c and 0x01) != 0, (d and 0x01) == 0))
                monitors.add(MonitorStatus("NOx/SCR", (c and 0x02) != 0, (d and 0x02) == 0))
                monitors.add(MonitorStatus("Boost Pres", (c and 0x08) != 0, (d and 0x08) == 0))
                monitors.add(MonitorStatus("Exhaust Gas", (c and 0x10) != 0, (d and 0x10) == 0))
                monitors.add(MonitorStatus("PM Filter", (c and 0x20) != 0, (d and 0x20) == 0))
                monitors.add(MonitorStatus("EGR/VVT", (c and 0x80) != 0, (d and 0x80) == 0))
            }

            ReadinessResult(milOn, dtcCount, monitors.filter { it.available })
        } catch (_: Exception) { null }
    }

    // ═══════════════════════════════════════════════
    // MODE $06 — NONCONTINUOUS MONITOR TEST RESULTS
    // ═══════════════════════════════════════════════

    private val mode06Parser = Mode06Parser()

    private val _mode06Results = MutableStateFlow<List<Mode06TestResult>>(emptyList())
    val mode06Results: StateFlow<List<Mode06TestResult>> = _mode06Results.asStateFlow()

    // Driving/standing time tracking
    private var drivingTimeMs = 0L
    private var standingTimeMs = 0L
    private var lastTimeCheckMs = 0L
    private var lastDrivingState = false // true = driving, false = standing

    private val _drivingTimeSeconds = MutableStateFlow(0L)
    val drivingTimeSeconds: StateFlow<Long> = _drivingTimeSeconds.asStateFlow()
    private val _standingTimeSeconds = MutableStateFlow(0L)
    val standingTimeSeconds: StateFlow<Long> = _standingTimeSeconds.asStateFlow()

    /**
     * Updates driving/standing time based on current speed.
     * Called from computeCalculatedSensors().
     */
    private fun updateDrivingTime(speedKph: Float?) {
        val now = System.currentTimeMillis()
        if (lastTimeCheckMs > 0) {
            val delta = (now - lastTimeCheckMs).coerceIn(0, 5000)
            val isDriving = speedKph != null && speedKph > 2f
            if (isDriving) {
                drivingTimeMs += delta
            } else {
                standingTimeMs += delta
            }
            _drivingTimeSeconds.value = drivingTimeMs / 1000
            _standingTimeSeconds.value = standingTimeMs / 1000
        }
        lastTimeCheckMs = now
    }

    /**
     * Professional Mode 06 Reader.
     * 1. Discovers supported MIDs via bitmaps ($00, $20, etc.)
     * 2. Queries each supported MID.
     * 3. Uses Mode06Parser for expert decoding and pro-tips.
     */
    suspend fun readMode06Results(): List<Mode06TestResult> {
        if (_state.value != ObdState.CONNECTED) return emptyList()

        _statusMessage.value = "Iniciando escaneo profundo de monitores (Mode $06)..."
        val allResults = mutableListOf<Mode06TestResult>()

        try {
            // Step 1: Discover available MIDs
            val availableMids = mutableListOf<String>()
            for (i in 0..7) {
                val basePid = i * 0x20
                val pidHex = String.format("%02X", basePid)
                val response = sendRawCommand("06$pidHex")
                val clean = CanMultiFrameParser.parse(response).replace(" ", "").uppercase()

                // Response should be 46 [PID] [4 bytes bitmap]
                val marker = "46$pidHex"
                if (clean.contains(marker)) {
                    val idx = clean.indexOf(marker)
                    val bitmapHex = clean.substring(idx + marker.length).take(8)
                    if (bitmapHex.length == 8) {
                        try {
                            val bitmap = bitmapHex.toLong(16)
                            for (bit in 0..30) {
                                if ((bitmap shr (31 - bit)) and 1L == 1L) {
                                    val foundMid = String.format("%02X", basePid + bit + 1)
                                    availableMids.add(foundMid)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            if (availableMids.isEmpty()) {
                Log.w(TAG, "No supported Mode 06 MIDs found via bitmaps. Falling back to standard list.")
                availableMids.addAll(listOf("01", "02", "05", "06", "21", "31", "A1", "A2"))
            }

            // Step 2: Query each available MID
            for ((index, mid) in availableMids.withIndex()) {
                _statusMessage.value = "Analizando monitor ${index + 1}/${availableMids.size} (MID \$$mid)..."
                try {
                    val response = sendRawCommand("06$mid")
                    val parsed = mode06Parser.parse(response)
                    allResults.addAll(parsed)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read Mode 06 MID $mid: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Mode 06 Scan Critical Failure: ${e.message}")
        }

        _mode06Results.value = allResults
        _statusMessage.value = "Escaneo profundo completado. ${allResults.size} pruebas procesadas."
        Log.i(TAG, "Mode 06: ${allResults.size} test results read")
        return allResults
    }

    fun resetDrivingTime() {
        drivingTimeMs = 0L
        standingTimeMs = 0L
        lastTimeCheckMs = 0L
        _drivingTimeSeconds.value = 0
        _standingTimeSeconds.value = 0
    }

    private fun professionalDtcTargets(): Map<String, String> {
        val vinVal = vin.value ?: ""
        val mfr = when {
            vinVal.startsWith("1FM") || vinVal.startsWith("1FT") || vinVal.startsWith("1FA") || vinVal.startsWith("3FA") -> "FORD"
            vinVal.startsWith("JTD") || vinVal.startsWith("JT1") || vinVal.startsWith("JTN") || vinVal.startsWith("JTH") -> "TOYOTA"
            vinVal.startsWith("1GC") || vinVal.startsWith("1G1") || vinVal.startsWith("1G6") || vinVal.startsWith("3G1") -> "GM"
            vinVal.startsWith("WVW") || vinVal.startsWith("WV2") || vinVal.startsWith("WAU") || vinVal.startsWith("TRU") -> "VOLKSWAGEN"
            else -> "GENERIC"
        }

        val targets = linkedMapOf(
            "7E0" to "ECM (Motor)",
            "7E1" to "TCM (Transmision)",
            "7E2" to "ABS/ESP/TCS",
            "7E3" to "SRS (Airbag)",
            "7E4" to "BCM (Carroceria)",
            "7E5" to "IPC (Instrumentos)",
            "7E6" to "HVAC (Climatizacion)",
            "7E7" to "PSM/Confort"
        )

        when (mfr) {
            "TOYOTA" -> {
                targets["7B0"] = "Toyota ABS/VSC"
                targets["7B4"] = "Toyota SRS Airbag"
                targets["7C0"] = "Toyota HVAC"
                targets["7C4"] = "Toyota BCM"
            }
            "GM" -> {
                targets["7A0"] = "GM Chassis/ABS"
                targets["7A4"] = "GM Body/BCM"
            }
            "VOLKSWAGEN" -> {
                targets["721"] = "VAG Transmission"
                targets["722"] = "VAG ABS/ESP"
                targets["723"] = "VAG Airbag/SRS"
            }
            "FORD" -> {
                targets["760"] = "Ford ABS Module"
                targets["764"] = "Ford RCM (Airbag)"
            }
        }

        if (detectedProtocol.contains("29")) {
            targets["18DAF110"] = "ECM (Extended 29-bit)"
            targets["18DAF118"] = "TCM (Extended 29-bit)"
            targets["18DAF128"] = "ABS (Extended 29-bit)"
            targets["18DAF158"] = "SRS (Extended 29-bit)"
        }

        return targets
    }

    private fun moduleNameForResponse(responseAddress: String?): String? = when (responseAddress?.uppercase()) {
        "7E8" -> "ECM (Motor)"
        "7E9" -> "TCM (Transmision)"
        "7EA" -> "ABS/ESP/TCS"
        "7EB" -> "SRS/BCM"
        "7EC" -> "BCM (Carroceria)"
        "7ED" -> "IPC (Instrumentos)"
        "7EE" -> "HVAC/Confort"
        "7EF" -> "Modulo auxiliar"
        "18DA10F1" -> "ECM (Extended 29-bit)"
        "18DA18F1" -> "TCM (Extended 29-bit)"
        "18DA28F1" -> "ABS (Extended 29-bit)"
        "18DA58F1" -> "SRS (Extended 29-bit)"
        else -> null
    }

    suspend fun scanModules(): List<NetworkModule> {
        if (_state.value != ObdState.CONNECTED) return emptyList()
        val modules = mutableListOf<NetworkModule>()

        _statusMessage.value = "Escaneando topología de red CAN..."

        try {
            // Detect manufacturer based on VIN
            val vinVal = vin.value ?: ""
            val mfr = when {
                vinVal.startsWith("1FM") || vinVal.startsWith("1FT") || vinVal.startsWith("1FA") || vinVal.startsWith("3FA") -> "FORD"
                vinVal.startsWith("JTD") || vinVal.startsWith("JT1") || vinVal.startsWith("JTN") || vinVal.startsWith("JTH") -> "TOYOTA"
                vinVal.startsWith("1GC") || vinVal.startsWith("1G1") || vinVal.startsWith("1G6") || vinVal.startsWith("3G1") -> "GM"
                vinVal.startsWith("WVW") || vinVal.startsWith("WV2") || vinVal.startsWith("WAU") || vinVal.startsWith("TRU") -> "VOLKSWAGEN"
                else -> "GENERIC"
            }

            android.util.Log.i("ObdSession", "scanModules detected manufacturer: $mfr")

            // Standard 11-bit CAN Addressing (7E0-7EF) + OEM Modules
            val can11Targets = mutableMapOf(
                "7E0" to "ECM (Motor)",
                "7E1" to "TCM (Transmisión)",
                "7E2" to "ABS/ESP/TCS",
                "7E3" to "SRS (Airbag)",
                "7E4" to "BCM (Carrocería)",
                "7E5" to "IPC (Instrumentos)",
                "7E6" to "HVAC (Climatización)",
                "7E7" to "PSM (Asientos/Confort)"
            )

            // Add brand-specific advanced modules
            when (mfr) {
                "TOYOTA" -> {
                    can11Targets["7B0"] = "Toyota ABS/VSC"
                    can11Targets["7B4"] = "Toyota SRS Airbag"
                    can11Targets["7C0"] = "Toyota HVAC"
                    can11Targets["7C4"] = "Toyota BCM"
                }
                "GM" -> {
                    can11Targets["7A0"] = "GM Chassis/ABS"
                    can11Targets["7A4"] = "GM Body/BCM"
                }
                "VOLKSWAGEN" -> {
                    can11Targets["721"] = "VAG Transmission"
                    can11Targets["722"] = "VAG ABS/ESP"
                    can11Targets["723"] = "VAG Airbag/SRS"
                }
                "FORD" -> {
                    can11Targets["760"] = "Ford ABS Module"
                    can11Targets["764"] = "Ford RCM (Airbag)"
                }
            }

            try { sendRawCommand("ATH1") } catch (_: Exception) {}

            for ((addr, label) in can11Targets) {
                try {
                    sendRawCommand("ATSH$addr")

                    // Initialize diagnostic session for non-generic manufacturer CAN queries
                    if (mfr != "GENERIC") {
                        try { sendRawCommand("1003") } catch (_: Exception) {
                            try { sendRawCommand("1001") } catch (_: Exception) {}
                        }
                    }

                    // Probe if module is alive (0100 standard query)
                    var isAlive = false
                    var resp = ""
                    try {
                        resp = sendRawCommand("0100")
                        if (resp.isNotBlank() && (resp.contains("41 00") || resp.contains("4100") || resp.contains("7F") || resp.contains("50"))) {
                            isAlive = true
                        }
                    } catch (_: Exception) {}

                    // Alternate probe for OEM modules that reject standard Mode 01
                    if (!isAlive && mfr != "GENERIC") {
                        try {
                            val testerResp = sendRawCommand("3E00")
                            if (testerResp.isNotBlank() && !testerResp.contains("UNABLE") && !testerResp.contains("ERROR")) {
                                isAlive = true
                            }
                        } catch (_: Exception) {}
                    }

                    if (isAlive) {
                        var dtcs = emptyList<String>()

                        // Query DTCs using UDS Service 19 first
                        try {
                            val udsResp = sendRawCommand("19020D")
                            if (udsResp.isNotBlank() && udsResp.contains("5902")) {
                                dtcs = DtcScanEngine.parseUdsService19ByEcu(
                                    rawResponse = udsResp,
                                    targetAddress = addr,
                                    moduleName = label
                                ).map { it.code }.distinct()
                            }
                        } catch (_: Exception) {}

                        // Fallback to Mode 03 if UDS fails or is empty
                        if (dtcs.isEmpty()) {
                            try {
                                val dtcResp = sendRawCommand("03")
                                dtcs = DtcDecoder.decode(dtcResp, "03")
                            } catch (_: Exception) {}
                        }

                        modules.add(NetworkModule(id = addr, name = label, isAlive = true, dtcs = dtcs))
                    }
                } catch (_: Exception) {}
            }

            // Extended 29-bit CAN Addressing (Standard ISO-TP)
            if (detectedProtocol.contains("29")) {
                val can29Targets = mapOf(
                    "18DAF110" to "ECM (Extended)",
                    "18DAF118" to "TCM (Extended)",
                    "18DAF128" to "ABS (Extended)",
                    "18DAF158" to "SRS (Extended)"
                )
                for ((addr, label) in can29Targets) {
                    try {
                        sendRawCommand("ATSH$addr")

                        if (mfr != "GENERIC") {
                            try { sendRawCommand("1003") } catch (_: Exception) {}
                        }

                        val resp = sendRawCommand("0100")
                        if (resp.isNotBlank() && (resp.contains("4100") || resp.contains("7F") || resp.contains("50"))) {
                            var dtcs = emptyList<String>()
                            try {
                                val udsResp = sendRawCommand("19020D")
                                if (udsResp.isNotBlank() && udsResp.contains("5902")) {
                                    dtcs = DtcScanEngine.parseUdsService19ByEcu(
                                        rawResponse = udsResp,
                                        targetAddress = addr,
                                        moduleName = label
                                    ).map { it.code }.distinct()
                                }
                            } catch (_: Exception) {}

                            if (dtcs.isEmpty()) {
                                try {
                                    val dtcResp = sendRawCommand("03")
                                    dtcs = DtcDecoder.decode(dtcResp, "03")
                                } catch (_: Exception) {}
                            }

                            modules.add(NetworkModule(id = addr, name = label, isAlive = true, dtcs = dtcs))
                        }
                    } catch (_: Exception) {}
                }
            }
        } finally {
            // Restore environment to Functional Broadcast (7DF) or Auto
            try {
                sendRawCommand("ATSH7DF")
                sendRawCommand("ATH0")
            } catch (_: Exception) {}
            _statusMessage.value = "Escaneo completado: ${modules.size} módulos hallados."
        }

        return modules
    }

    /**
     * Allows manual protocol override. Essential for professional diagnostics
     * where auto-detection might fail on specific hardware modifications.
     */
    suspend fun setProtocol(protocol: String): Boolean {
        return try {
            val cmd = when (protocol.uppercase()) {
                "AUTO" -> "ATSP0"
                "ISO15765-4_11_500" -> "ATSP6"
                "ISO15765-4_29_500" -> "ATSP7"
                "ISO15765-4_11_250" -> "ATSP8"
                "ISO15765-4_29_250" -> "ATSP9"
                "ISO9141-2" -> "ATSP3"
                "ISO14230-4_5BAUD" -> "ATSP4"
                "ISO14230-4_FAST" -> "ATSP5"
                "J1850_PWM" -> "ATSP1"
                "J1850_VPW" -> "ATSP2"
                else -> protocol
            }
            val resp = sendRawCommand(cmd)
            if (!resp.contains("OK")) return false

            // Validate connection with protocol
            val check = sendRawCommand("0100")
            val success = check.contains("4100") || check.contains("41 00")
            if (success) {
                detectedProtocol = parseProtocolName(sendCommandDirectly("ATDPN"))
            }
            success
        } catch (_: Exception) { false }
    }

    suspend fun readFreezeFrame(pid: String, frame: Int = 0): Float? {
        if (_state.value != ObdState.CONNECTED) return null
        return try {
            val response = sendRawCommand("02${pid}${String.format("%02X", frame)}")
            if (response.contains("42") && !response.contains("NO DATA")) {
                parsePidResponse("01${pid}", response.replace("42", "41"))
            } else null
        } catch (_: Exception) { null }
    }

    /**
     * Attempts to read the vehicle odometer.
     * Uses Standard Mode 01 PID A6 if supported, or common fallback PIDs.
     */
    suspend fun readOdometer(): Float {
        if (_state.value != ObdState.CONNECTED) return 0f

        val vinVal = vin.value ?: ""
        val manufacturer = when {
            vinVal.startsWith("1FM") || vinVal.startsWith("1FT") || vinVal.startsWith("1FA") || vinVal.startsWith("3FA") -> "FORD"
            vinVal.startsWith("JTD") || vinVal.startsWith("JT1") || vinVal.startsWith("JTN") || vinVal.startsWith("JTH") -> "TOYOTA"
            vinVal.startsWith("1GC") || vinVal.startsWith("1G1") || vinVal.startsWith("1G6") || vinVal.startsWith("3G1") -> "GM"
            vinVal.startsWith("WVW") || vinVal.startsWith("WV2") || vinVal.startsWith("WAU") || vinVal.startsWith("TRU") -> "VOLKSWAGEN"
            else -> "GENERIC"
        }

        val commandsToTry = mutableListOf<Pair<String, String>>()

        // Priority 1: Standard OBD2 Odometer PIDs
        commandsToTry.add("01A6" to "41A6")
        commandsToTry.add("090D" to "490D")

        // Priority 2: Manufacturer specific PIDs/DIDs
        when (manufacturer) {
            "TOYOTA" -> {
                commandsToTry.add("21C4" to "61C4")
                commandsToTry.add("2101" to "6101")
            }
            "FORD" -> {
                commandsToTry.add("22DD01" to "62DD01")
                commandsToTry.add("220200" to "620200")
                commandsToTry.add("220201" to "620201")
            }
            "GM" -> {
                commandsToTry.add("221A6C" to "621A6C")
                commandsToTry.add("2211A6" to "6211A6")
            }
            "VOLKSWAGEN" -> {
                commandsToTry.add("222203" to "622203")
                commandsToTry.add("22F40D" to "62F40D")
            }
        }

        // Priority 3: Fallback distance indicators
        commandsToTry.add("0131" to "4131")
        commandsToTry.add("0121" to "4121")

        for ((cmd, prefix) in commandsToTry) {
            try {
                val resp = sendRawCommand(cmd)
                if (resp.contains(prefix) && !resp.contains("NO DATA") && !resp.contains("ERROR")) {
                    val clean = CanMultiFrameParser.parse(resp)
                        .replace(prefix, "")
                        .replace(" ", "")
                        .replace("\r", "")
                        .replace("\n", "")
                    
                    val value = when {
                        cmd == "01A6" || cmd == "090D" || cmd == "22DD01" -> {
                            if (clean.length >= 8) {
                                val odoVal = clean.substring(0, 8).toLong(16) / 10f
                                if (cmd == "22DD01" && odoVal > 2000000f) {
                                    odoVal / 10f
                                } else odoVal
                            } else null
                        }
                        cmd == "21C4" || cmd == "221A6C" -> {
                            if (clean.length >= 6) {
                                clean.substring(0, 6).toInt(16).toFloat()
                            } else null
                        }
                        cmd == "222203" || cmd == "22F40D" -> {
                            if (clean.length >= 8) {
                                clean.substring(0, 8).toLong(16) / 1000f // meters to km
                            } else null
                        }
                        cmd == "2101" -> {
                            if (clean.length >= 22) {
                                clean.substring(16, 22).toInt(16).toFloat()
                            } else null
                        }
                        cmd == "0131" || cmd == "0121" -> {
                            if (clean.length >= 4) {
                                clean.substring(0, 4).toInt(16).toFloat()
                            } else null
                        }
                        else -> null
                    }

                    if (value != null && value in 10.0f..2500000.0f) {
                        Log.i(TAG, "Odómetro leído con éxito ($cmd): $value km")
                        return value
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Fallo comando odómetro $cmd: ${e.message}")
            }
        }

        return 0f
    }

    // ═══════════════════════════════════════════════════
    // INITIALIZATION & INFRASTRUCTURE
    // ═══════════════════════════════════════════════════

    private suspend fun initializeAdapter() {
        val t = transport ?: throw ObdConnectionException("Transport no disponible")
        val address = targetAddress ?: "unknown"
        val fingerprint = AdapterFingerprint(context)
        val cachedProfile = fingerprint.getProfile(address)

        Log.i(TAG, "── INIT ADAPTER START ── (cached=${cachedProfile != null})")

        val negotiator = ElmNegotiator(t)
        val profile = negotiator.negotiate(
            hintProtocol = cachedProfile?.detectedProtocol ?: ObdProtocol.AUTO
        ) { status ->
            _statusMessage.value = status
        }

        // Save for next time
        fingerprint.saveProfile(address, profile)

        // Apply profile to session
        adapterVersion = profile.chipVersion
        isCloneAdapter = profile.isClone
        detectedProtocol = profile.detectedProtocol.displayName
        _isAdapterPro.value = !profile.isClone
        baseDelayMs = profile.baseDelayMs
        maxLineLength = profile.maxLineLength

        // Final Voltage Check
        try {
            val (ecuVolt, elmVolt) = readBatteryVoltage()
            val voltage = if (elmVolt > 0f) elmVolt else ecuVolt
            Log.d(TAG, "Battery voltage: ${voltage}V")
            if (voltage in 0.1f..9.0f) _statusMessage.value = "⚠ Batería baja: ${"%.1f".format(voltage)}V"
        } catch (_: Exception) {}
    }

    private suspend fun initializeDoIpConnection() {
        val t = transport ?: throw ObdConnectionException("Transport no disponible para DoIP")
        Log.i(TAG, "── INIT DoIP ROUTING ACTIVATION ──")
        
        val requestPacket = byteArrayOf(
            0x02.toByte(), 0xFD.toByte(), // Header
            0x00.toByte(), 0x05.toByte(), // Payload Type: Routing Activation
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x07.toByte(), // Payload Length
            0x0E.toByte(), 0x00.toByte(), // Source Address: 0x0E00
            0x00.toByte(), // Activation Type: 0x00 (Default)
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte() // Reserved
        )
        
        t.write(requestPacket)
        
        val respBytes = t.read(1024, 5000L)
        if (respBytes == null || respBytes.size < 13) {
            throw ObdConnectionException("Sin respuesta de activación DoIP. Verifica el gateway DoIP.")
        }
        
        val payloadType = ((respBytes[2].toInt() and 0xFF) shl 8) or (respBytes[3].toInt() and 0xFF)
        val status = respBytes[12].toInt() and 0xFF
        
        if (payloadType == 0x0006 && (status == 0x10 || status == 0x00)) {
            Log.i(TAG, "DoIP Routing Activation SUCCESS. Status: $status")
            adapterVersion = "DoIP Gateway (ISO 13400)"
            isCloneAdapter = false
            detectedProtocol = ObdProtocol.DOIP_ISO13400.displayName
            _isAdapterPro.value = true
            baseDelayMs = 5L
            maxLineLength = 4096
        } else {
            throw ObdConnectionException("Fallo en activación DoIP: Tipo=${"%04X".format(payloadType)}, Estado=${"%02X".format(status)}")
        }
    }

    private fun wrapDoIpDiagnostics(udsHex: String): ByteArray {
        val cleanHex = udsHex.replace(" ", "").replace("\r", "").replace("\n", "")
        val udsBytes = cleanHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val payloadSize = 4 + udsBytes.size
        
        val packet = ByteArray(8 + payloadSize)
        packet[0] = 0x02.toByte()
        packet[1] = 0xFD.toByte()
        packet[2] = 0x80.toByte()
        packet[3] = 0x01.toByte()
        
        packet[4] = ((payloadSize shl 24) and 0xFF).toByte()
        packet[5] = ((payloadSize shl 16) and 0xFF).toByte()
        packet[6] = ((payloadSize shl 8) and 0xFF).toByte()
        packet[7] = (payloadSize and 0xFF).toByte()
        
        packet[8] = 0x0E.toByte()
        packet[9] = 0x00.toByte()
        packet[10] = 0x10.toByte()
        packet[11] = 0x00.toByte()
        
        System.arraycopy(udsBytes, 0, packet, 12, udsBytes.size)
        return packet
    }

    private fun unwrapDoIpDiagnostics(doipBytes: ByteArray?): String {
        if (doipBytes == null || doipBytes.size < 12) return ""
        val payloadType = ((doipBytes[2].toInt() and 0xFF) shl 8) or (doipBytes[3].toInt() and 0xFF)
        if (payloadType != 0x8001) return ""
        
        val udsLength = doipBytes.size - 12
        if (udsLength <= 0) return ""
        
        val sb = StringBuilder()
        for (i in 12 until doipBytes.size) {
            sb.append("%02X".format(doipBytes[i]))
        }
        return sb.toString()
    }

    private suspend fun readResponseBytes(timeoutMs: Long = 1500L): ByteArray? =
        withContext(Dispatchers.IO) {
            val t = transport ?: return@withContext null
            t.read(1024, timeoutMs)
        }


    private suspend fun drainInput() {
        withContext(Dispatchers.IO) {
            transport?.drain()
        }
    }

    private suspend fun sendCommandDirectly(command: String, timeoutMs: Long = 3000L): String {
        if (isDoIpMode) {
            val cmd = command.trim().uppercase()
            if (cmd.startsWith("AT")) {
                return when {
                    cmd == "ATRV" -> "12.6V"
                    cmd == "ATZ" || cmd == "ATI" -> "DoIP Gateway v1.0"
                    cmd == "ATDPN" -> "F"
                    cmd == "ATDP" -> "DOIP"
                    else -> "OK"
                }
            }
            return communicationMutex.withLock {
                withContext(Dispatchers.IO) {
                    val t = transport ?: throw ObdConnectionException("Transport no disponible")
                    Log.v(TAG, "TX (DoIP Direct): '$command'")
                    val packet = wrapDoIpDiagnostics(command)
                    runCatching { t.drain() }
                    t.write(packet)
                    val respBytes = readResponseBytes(timeoutMs)
                    val resp = unwrapDoIpDiagnostics(respBytes)
                    Log.v(TAG, "RX (DoIP Direct): '$resp'")
                    delay(baseDelayMs)
                    resp
                }
            }
        }

        return communicationMutex.withLock {
            withContext(Dispatchers.IO) {
                val t = transport ?: throw ObdConnectionException("Transport no disponible")
                Log.v(TAG, "TX (Direct): '$command' (timeout=${timeoutMs}ms)")
                runCatching { t.drain() }
                t.write("$command\r".toByteArray())
                val resp = readResponse(timeoutMs)
                Log.v(TAG, "RX (Direct): '$resp' (${resp.length} chars)")
                delay(baseDelayMs) // Meet elite: adaptive delay
                resp
            }
        }
    }

    private suspend fun readResponse(timeoutMs: Long = 1500L): String =
        withContext(Dispatchers.IO) {
        val t = transport ?: return@withContext ""
        val buffer = StringBuilder()
        val startTime = System.currentTimeMillis()
        var consecutiveNulls = 0

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val chunkSize = if (t is BtClassicTransport) 1024 else 512
            val remainingTime = timeoutMs - (System.currentTimeMillis() - startTime)
            if (remainingTime <= 0) break

            // Adaptive Silence Detection: Use a small read timeout once we have data,
            // avoiding long locks while waiting for subsequent frames or command prompt
            val readTimeout = if (buffer.isEmpty()) {
                remainingTime.coerceAtLeast(100L)
            } else {
                if (isCloneAdapter) 40L else 15L
            }

            val chunk = t.read(chunkSize, readTimeout)

            if (chunk != null) {
                consecutiveNulls = 0
                val str = String(chunk, Charsets.ISO_8859_1)
                buffer.append(str)
                keepAliveManager.notifyBytesReceived()

                // Exit fast if we see the ELM327 prompt
                if (buffer.contains('>')) break

                val current = buffer.toString().uppercase()
                // Fast exit on definitive error/empty responses
                if (current.contains("NO DATA") || current.contains("UNABLE") ||
                    current.contains("CAN ERROR") || current.contains("STOPPED") ||
                    current.contains("ERROR")) break
                // "?" alone means invalid command — exit
                if (current.trimEnd().endsWith("?")) break
            } else {
                consecutiveNulls++
                // If we already have data and no new bytes for a while, response is complete.
                val silenceThreshold = if (isCloneAdapter) 6 else 3
                if (buffer.isNotEmpty() && consecutiveNulls >= silenceThreshold) break
                delay(5)
            }
        }
        val responseStr = buffer.toString()
        val isComplete = responseStr.contains('>') || 
                responseStr.contains("NO DATA", ignoreCase = true) || 
                responseStr.contains("UNABLE", ignoreCase = true) ||
                responseStr.contains("CAN ERROR", ignoreCase = true) || 
                responseStr.contains("STOPPED", ignoreCase = true) ||
                responseStr.contains("ERROR", ignoreCase = true) ||
                responseStr.trimEnd().endsWith("?")

        if (!isComplete) {
            Log.w(TAG, "Incomplete response or timeout. Draining transport input...")
            try {
                t.drain()
            } catch (_: Exception) {}
        }

        return@withContext responseStr.replace("\r", " ").replace("\n", " ").trim()
    }

    suspend fun readBatteryVoltage(): Pair<Float, Float> {
        // PRIORIDAD 1: Modo 01 PID 42 (Control Module Voltage desde la ECU)
        val obdResponse = if (isRunning) sendRawCommand("0142") else sendCommandDirectly("0142", 3000L)
        val clean = CanMultiFrameParser.parse(obdResponse).replace(" ", "")

        var ecuVoltage = 0f
        if (clean.length >= 4 && clean.contains("4142")) {
            try {
                val idx = clean.indexOf("4142")
                val data = clean.substring(idx + 4)
                if (data.length >= 4) {
                    val a = data.substring(0, 2).toInt(16)
                    val b = data.substring(2, 4).toInt(16)
                    ecuVoltage = (256 * a + b) / 1000f
                }
            } catch (_: Exception) {}
        }

        // PRIORIDAD 2: Sensor de voltaje interno del ELM327 (AT RV).
        val elmResponse = if (isRunning) sendRawCommand("ATRV") else sendCommandDirectly("ATRV", 2000L)
        val rawElmVoltage = elmResponse.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 0f
        
        val prefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
        val offset = prefs.getFloat("voltage_calibration_offset", 0f)
        val elmVoltage = if (rawElmVoltage > 0f) rawElmVoltage + offset else 0f

        return Pair(ecuVoltage, elmVoltage)
    }

    fun enableOemPids(manufacturer: String) {
        oemPidsToPoll.clear()
        val pids = PidRegistry.getOemPids(manufacturer)
        if (pids.isNotEmpty()) {
            oemPidsToPoll.addAll(pids)
        }
    }

    /**
     * Professional Safety Guard: Verifies that sensitive operations
     * are only performed on high-quality adapters and under safe conditions.
     */
    suspend fun verifySafetyForProAction(conditions: List<SafetyCondition> = emptyList()): Boolean {
        val (ecuVolt, elmVolt) = readBatteryVoltage()
        val voltage = if (elmVolt > 0f) elmVolt else ecuVolt
        if (voltage < 11.5f) {
            _statusMessage.value = "ERROR: Voltaje insuficiente (${voltage}V). Conecta un cargador."
            return false
        }

        // Check specific conditions
        // NOTE: Live polling stores RPM under key "010C" (Mode 01 PID 0C)
        for (condition in conditions) {
            when (condition) {
                SafetyCondition.ENGINE_OFF -> {
                    val rpm = liveData.value["010C"] ?: liveData.value["RPM"] ?: 0f
                    if (rpm > 100f) {
                        _statusMessage.value = "ERROR: El motor debe estar APAGADO para esta acción."
                        return false
                    }
                }
                SafetyCondition.ENGINE_RUNNING -> {
                    val rpm = liveData.value["010C"] ?: liveData.value["RPM"] ?: 0f
                    if (rpm < 400f) {
                        _statusMessage.value = "ERROR: El motor debe estar ENCENDIDO para esta acción."
                        return false
                    }
                }
                SafetyCondition.VEHICLE_STATIONARY -> {
                    val speed = liveData.value["010D"] ?: liveData.value["Speed"] ?: 0f
                    if (speed > 3f) {
                        _statusMessage.value = "ERROR: El vehículo debe estar DETENIDO para esta acción."
                        return false
                    }
                }
                SafetyCondition.BATTERY_ABOVE_12V -> {
                    if (voltage < 12.0f) {
                        _statusMessage.value = "ERROR: Batería baja (${voltage}V). Mínimo 12.0V requerido."
                        return false
                    }
                }
                SafetyCondition.TRANS_IN_PARK -> {
                    // Cannot verify via OBD on most vehicles — log warning only
                    Log.w(TAG, "TRANS_IN_PARK check not available via OBD — proceeding with caution")
                }
            }
        }

        if (isCloneAdapter) {
            _statusMessage.value = "AVISO: Usando adaptador clon. Proceder con extrema cautela."
            // We allow it but with a warning in logs, or return false to lock it
            // return false
        }
        return true
    }

    /**
     * UDS Protocol Manager
     */
    private val udsProtocolManager by lazy { UdsProtocolManager(this) }

    /**
     * Executes a professional bidirectional Active Test.
     * This commands an actuator (pump, fan, valve) and monitors feedback.
     */
    fun runActiveTest(test: ActiveTest) {
        val isOfflineSim = _state.value != ObdState.CONNECTED

        activeTestJob?.cancel()
        activeTestJob = scope.launch(Dispatchers.IO) {
            try {
                _activeTestStatus.value = ActiveTestStatus(isActive = true, message = "Iniciando: ${test.name}...", progress = 0.1f, testId = test.id)

                if (isOfflineSim) {
                    runOfflineActiveTestSim(test)
                    return@launch
                }

                if (!verifySafetyForProAction(test.safetyConditions)) {
                    _activeTestStatus.value = ActiveTestStatus(isActive = false, message = "ERROR: Condiciones de seguridad no cumplidas.")
                    return@launch
                }

                // Send Start Command
                _statusMessage.value = "Enviando comando de activación: ${test.startCommand}"
                var startResp = sendRawCommand(test.startCommand)
                if (startResp.contains("ERROR") || startResp.contains("NO DATA") || startResp.contains("CAN ERROR")) {
                    _statusMessage.value = "Fallo inicial. Recuperando canal físico..."
                    attemptSelfHealing()
                    startResp = sendRawCommand(test.startCommand)
                    if (startResp.contains("ERROR") || startResp.contains("NO DATA") || startResp.contains("CAN ERROR")) {
                        _activeTestStatus.value = ActiveTestStatus(isActive = false, message = "Fallo al iniciar: $startResp")
                        return@launch
                    }
                }

                _activeTestStatus.value = ActiveTestStatus(isActive = true, message = "PRUEBA ACTIVA: ${test.name}", progress = 0.5f, testId = test.id)

                // Monitor loop
                val startTime = System.currentTimeMillis()
                var lastTesterPresentTime = startTime
                var finalData = emptyMap<String, Float>()
                while (System.currentTimeMillis() - startTime < test.durationMs) {
                    if (!isActive) break

                    // Keep-Alive for UDS Sessions (Send Tester Present 3E00 every 2 seconds)
                    val isUdsCommand = test.startCommand.startsWith(UdsProtocolManager.SID_INPUT_OUTPUT_CONTROL) ||
                                       test.startCommand.startsWith(UdsProtocolManager.SID_ROUTINE_CONTROL)
                    if (isUdsCommand && (System.currentTimeMillis() - lastTesterPresentTime > 2000)) {
                        udsProtocolManager.sendTesterPresent()
                        lastTesterPresentTime = System.currentTimeMillis()
                    }

                    // Poll monitored PIDs if any
                    val monitoredData = mutableMapOf<String, Float>()
                    test.monitoredPids.forEach { pid ->
                        val mode = pid.substring(0, 2)
                        val code = pid.substring(2)
                        val def = PidRegistry.getPid(mode, code)
                        if (def != null) {
                            var resp = sendRawCommand(pid)
                            if (resp.contains("ERROR") || resp.contains("NO DATA") || resp.contains("CAN ERROR")) {
                                Log.w(TAG, "Active test PID poll failed: $pid. Reconnecting...")
                                attemptSelfHealing()
                                resp = sendRawCommand(pid)
                            }
                            val clean = CanMultiFrameParser.parse(resp).replace(" ", "")
                            val expectedPrefix = (mode.toInt(16) + 0x40).toString(16).uppercase() + code

                            val idx = clean.uppercase().indexOf(expectedPrefix)
                            if (idx >= 0) {
                                val dataPart = clean.substring(idx + expectedPrefix.length)
                                val bytes = mutableListOf<Int>()
                                for (i in 0 until dataPart.length step 2) {
                                    if (i + 2 <= dataPart.length) {
                                        bytes.add(dataPart.substring(i, i + 2).toInt(16))
                                    }
                                }
                                if (bytes.isNotEmpty()) {
                                    val a = bytes.getOrNull(0) ?: 0
                                    val b = bytes.getOrNull(1) ?: 0
                                    val c = bytes.getOrNull(2) ?: 0
                                    val d = bytes.getOrNull(3) ?: 0
                                    monitoredData[def.name] = def.formula(a, b, c, d)
                                }
                            }
                        }
                    }

                    val elapsed = System.currentTimeMillis() - startTime
                    val safeDuration = test.durationMs.coerceAtLeast(1L)
                    val progress = 0.5f + (elapsed.toFloat() / safeDuration.toFloat() * 0.4f)
                    finalData = monitoredData
                    _activeTestStatus.value = _activeTestStatus.value.copy(progress = progress, currentValues = monitoredData)

                    delay(500) // 2Hz feedback
                }

                // Send Stop Command
                _statusMessage.value = "Deteniendo prueba: ${test.stopCommand}"
                var stopResp = sendRawCommand(test.stopCommand)
                if (stopResp.contains("ERROR") || stopResp.contains("NO DATA") || stopResp.contains("CAN ERROR")) {
                    Log.w(TAG, "Failed to stop active test. Self-healing...")
                    attemptSelfHealing()
                    sendRawCommand(test.stopCommand)
                }

                _activeTestStatus.value = ActiveTestStatus(
                    isActive = false,
                    message = "Prueba completada con éxito.",
                    progress = 1.0f,
                    testId = test.id,
                    currentValues = finalData
                )

            } catch (e: Exception) {
                _activeTestStatus.value = ActiveTestStatus(
                    isActive = false,
                    message = "Excepción en prueba: ${e.message}",
                    testId = test.id
                )
            }
        }
    }

    private suspend fun runOfflineActiveTestSim(test: ActiveTest) {
        delay(1000) // Simulated startup handshake
        _activeTestStatus.value = ActiveTestStatus(isActive = true, message = "PRUEBA ACTIVA (SIMULADOR): ${test.name}", progress = 0.3f, testId = test.id)

        val startTime = System.currentTimeMillis()
        var finalData = emptyMap<String, Float>()
        while (System.currentTimeMillis() - startTime < test.durationMs) {
            currentCoroutineContext().ensureActive()

            val elapsed = System.currentTimeMillis() - startTime
            val safeDuration = test.durationMs.coerceAtLeast(1L)
            val progress = 0.3f + (elapsed.toFloat() / safeDuration.toFloat() * 0.6f)

            val monitoredData = mutableMapOf<String, Float>()
            when (test.id) {
                "FUEL_PUMP" -> {
                    val noise = (-5..5).random().toFloat()
                    monitoredData["Presión Comb."] = 320f + noise
                }
                "INJECTOR_BALANCE" -> {
                    val dropIndex = (elapsed / 3000).toInt() % 4
                    val basePress = 320f - (dropIndex * 40f)
                    val noise = (-3..3).random().toFloat()
                    monitoredData["Presión Comb."] = basePress + noise
                    monitoredData["Trim Comb CT B1"] = -12f + (dropIndex * 3f)
                }
                "EVAP_PURGE" -> {
                    val timeFactor = Math.sin(elapsed.toDouble() / 1000.0).toFloat()
                    monitoredData["RPM"] = 750f - (30f * timeFactor)
                    monitoredData["Trim Comb CT B1"] = -5f + (8f * timeFactor)
                }
                "EGR_VALVE" -> {
                    val ratio = elapsed.toFloat() / safeDuration.toFloat()
                    monitoredData["RPM"] = 750f - (120f * ratio)
                    monitoredData["Carga Motor"] = 15f + (10f * ratio)
                    monitoredData["Ciclo EGR"] = 80f * ratio
                }
                "SECONDARY_AIR" -> {
                    monitoredData["O2 B1S1 (V)"] = 0.85f + ((-10..10).random() / 200f)
                    monitoredData["Trim Comb CT B1"] = -15f
                }
                "COOLING_FAN_LOW", "COOLING_FAN_HIGH" -> {
                    val coolingRate = if (test.id == "COOLING_FAN_HIGH") 0.3f else 0.15f
                    val seconds = elapsed / 1000f
                    monitoredData["Temp Motor"] = (98f - seconds * coolingRate).coerceAtLeast(85f)
                }
                "IDLE_SPEED_UP" -> {
                    val ratio = (elapsed.toFloat() / safeDuration.toFloat()).coerceAtMost(1f)
                    val targetRpm = 750f + (300f * ratio)
                    monitoredData["RPM"] = targetRpm + (-5..5).random()
                    monitoredData["Pos. Mariposa"] = 12f + (8f * ratio)
                }
                "THROTTLE_BODY" -> {
                    val halfDuration = safeDuration / 2
                    val angle = if (elapsed < halfDuration) {
                        (elapsed.toFloat() / halfDuration.toFloat()) * 25f
                    } else {
                        25f - ((elapsed - halfDuration).toFloat() / halfDuration.toFloat()) * 25f
                    }
                    monitoredData["Pos. Mariposa"] = angle.coerceIn(0f, 25f)
                    monitoredData["RPM"] = 0f
                }
                "TCC_SOLENOID" -> {
                    monitoredData["Velocidad"] = 60f
                    monitoredData["RPM"] = 2200f - (elapsed / 20)
                }
                "AC_COMPRESSOR" -> {
                    monitoredData["Carga Motor"] = 22f + ((-2..2).random())
                    monitoredData["RPM"] = 720f + ((-10..10).random())
                }
                "GLOW_PLUGS" -> {
                    val ratio = (elapsed.toFloat() / safeDuration.toFloat())
                    monitoredData["Voltaje ECU"] = 14.1f - (1.3f * ratio)
                }
                "TURBO_WASTEGATE" -> {
                    monitoredData["Presión MAP"] = 101f + (elapsed / 100)
                }
                "HORN_TEST" -> {
                    val isActivePulse = (elapsed / 1000) % 2 == 0L
                    monitoredData["Consumo Amps"] = if (isActivePulse) 8.5f else 0f
                    monitoredData["Voltaje Bat"] = if (isActivePulse) 12.2f else 12.5f
                }
                "HEADLIGHT_TEST" -> {
                    monitoredData["Consumo Faros"] = 11.8f
                    monitoredData["Voltaje Bat"] = 12.3f
                }
                "WIPER_TEST" -> {
                    monitoredData["Consumo Limpia"] = 4.5f + ((-5..5).random() * 0.1f)
                    monitoredData["Voltaje Bat"] = 12.4f
                }
                "RADIATOR_FAN_TEST" -> {
                    monitoredData["Velocidad Fan RPM"] = 2850f + (-20..20).random()
                    monitoredData["Temp Refrigerante"] = (96f - (elapsed / 1000f) * 0.5f).coerceAtLeast(88f)
                }
                else -> {
                    test.monitoredPids.forEach { pid ->
                        val def = PidRegistry.getPid(pid.substring(0, 2), pid.substring(2))
                        if (def != null) {
                            monitoredData[def.name] = (def.minValue + def.maxValue) / 2f
                        }
                    }
                }
            }

            finalData = monitoredData
            _activeTestStatus.value = _activeTestStatus.value.copy(progress = progress, currentValues = monitoredData)
            delay(500)
        }

        _activeTestStatus.value = ActiveTestStatus(
            isActive = false,
            message = "Prueba completada con éxito (Simulación).",
            progress = 1.0f,
            testId = test.id,
            currentValues = finalData
        )
    }

    fun stopActiveTest() {
        val currentTestId = _activeTestStatus.value.testId
        val currentValues = _activeTestStatus.value.currentValues
        activeTestJob?.cancel()
        scope.launch(Dispatchers.IO) {
            _activeTestStatus.value = ActiveTestStatus(
                isActive = false,
                message = "Prueba detenida manualmente.",
                testId = currentTestId,
                currentValues = currentValues
            )
        }
    }

    fun clearActiveTestStatus() {
        _activeTestStatus.value = ActiveTestStatus()
    }




    private fun parseAdapterVersion(response: String): String {
        val regex = Regex("(ELM327|STN\\d+|OBDLink|vLinker)[\\s]*v?[\\d.]+", RegexOption.IGNORE_CASE)
        return regex.find(response)?.value ?: response.take(20).trim()
    }

    private fun detectClone(response: String): Boolean {
        val upper = response.uppercase()
        if (upper.contains("STN") || upper.contains("OBDLINK") || upper.contains("VLINKER")) return false
        return upper.contains("V1.5") || upper.contains("V2.1")
    }

    private fun parseProtocolName(response: String): String {
        // ATDPN returns protocol number prefixed with optional "A" (for auto-detected)
        // e.g. "A6" means auto-detected protocol 6, "6" means manually set protocol 6
        val clean = response.uppercase().trim().removePrefix("A")
        return when (clean) {
            "6" -> "ISO 15765-4 (CAN 11/500)"
            "7" -> "ISO 15765-4 (CAN 29/500)"
            "8" -> "ISO 15765-4 (CAN 11/250)"
            "9" -> "ISO 15765-4 (CAN 29/250)"
            "3" -> "ISO 9141-2"
            "4" -> "KWP 2000 (5 baud)"
            "5" -> "KWP 2000 (Fast)"
            "1" -> "SAE J1850 PWM"
            "2" -> "SAE J1850 VPW"
            else -> "Protocolo $clean"
        }
    }

    private fun startQueueProcessor() {
        currentJob = scope.launch(Dispatchers.IO) {
            while (isRunning && isActive) {
                val command = commandQueue.dequeue()
                if (command != null) {
                    val t = transport ?: break
                    var success = false
                    var attempts = 0
                    val startTime = System.currentTimeMillis()

                    while (!success && attempts < 2 && isRunning) {
                        try {
                            communicationMutex.withLock {
                                trafficListener?.onCommandSent(command.query)
                                val response = if (isDoIpMode) {
                                    val cmd = command.query.trim().uppercase()
                                    if (cmd.startsWith("AT")) {
                                        when {
                                            cmd == "ATRV" -> "12.6V"
                                            cmd == "ATZ" || cmd == "ATI" -> "DoIP Gateway v1.0"
                                            cmd == "ATDPN" -> "F"
                                            cmd == "ATDP" -> "DOIP"
                                            else -> "OK"
                                        }
                                    } else {
                                        val packet = wrapDoIpDiagnostics(command.query)
                                        runCatching { t.drain() }
                                        t.write(packet)
                                        val respBytes = readResponseBytes(timeoutMs = 2000L)
                                        unwrapDoIpDiagnostics(respBytes)
                                    }
                                } else {
                                    runCatching { t.drain() }
                                    t.write("${command.query}\r".toByteArray())
                                    readResponse(timeoutMs = 2000L)
                                }
                                if (response.isNotBlank() && !response.contains("?")) {
                                    success = true
                                    consecutiveErrors = 0
                                    lastHeartbeatTime = System.currentTimeMillis()
                                    updateQos(System.currentTimeMillis() - startTime, true)
                                    trafficListener?.onResponseReceived(command.query, response)
                                    command.onSuccess(response)
                                } else {
                                    attempts++
                                    drainInput()
                                }
                            }
                            if (!success) delay(100)
                        } catch (e: Exception) {
                            attempts++
                            trafficListener?.onError(command.query, e.message ?: "Unknown error")
                            drainInput()
                            delay(100)
                        }
                    }
                    if (!success) {
                        consecutiveErrors++
                        updateQos(System.currentTimeMillis() - startTime, false)
                        trafficListener?.onError(command.query, "Timeout after $attempts attempts")

                        if (consecutiveErrors >= 3 && !isSelfHealing) {
                            scope.launch { attemptSelfHealing() }
                        }

                        command.onError(Exception("Timeout"))
                    } else {
                        // Success - apply adaptive delay before next command (halved for speed)
                        delay(baseDelayMs / 2)
                    }
                } else {
                    delay(15)
                }
            }
        }
    }

    fun enqueueCommand(q: String, p: Int = 1, s: (String) -> Unit, e: (Exception) -> Unit) {
        if (isRunning) {
            commandQueue.enqueue(ObdCommand(q, p, s, e))
        } else {
            e(ObdConnectionException("OBD session not running"))
        }
    }

    suspend fun sendRawCommand(command: String, priority: Int = 10): String {
        return withTimeout(15000) { // Safety timeout for all raw commands
            val deferred = CompletableDeferred<String>()
            enqueueCommand(command, priority, { deferred.complete(it) }, { deferred.completeExceptionally(it) })
            deferred.await()
        }
    }

    /**
     * Sends a command directly to the transport bypassing the queue.
     * ONLY use this for keep-alive or low-level negotiation.
     */
    suspend fun sendKeepAliveDirectly(command: String): String {
        return sendCommandDirectly(command, timeoutMs = 1000L)
    }

    private suspend fun attemptSelfHealing() {
        if (isSelfHealing || !isRunning) return
        isSelfHealing = true
        _statusMessage.value = "Enlace inestable. Intentando autorecuperación..."

        try {
            transport?.reconnect()
            delay(500)
            initializeAdapter()
            consecutiveErrors = 0
            _statusMessage.value = "Enlace recuperado exitosamente."
        } catch (e: Exception) {
            _statusMessage.value = "Fallo crítico en recuperación: ${e.message}"
            // Reset errors and add cooldown to prevent infinite reconnection loop
            consecutiveErrors = 0
            Log.e(TAG, "Self-healing failed, entering cooldown", e)
            delay(10000) // 10s cooldown before allowing another healing attempt
        } finally {
            isSelfHealing = false
        }
    }

    private var lastHeartbeatTime = 0L
    private var heartbeatJob: Job? = null

    private fun startHeartbeatMonitor() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            lastHeartbeatTime = System.currentTimeMillis()
            while (isRunning && isActive) {
                delay(5000)
                val now = System.currentTimeMillis()
                // If no successful command in 15s while running, the link is likely frozen
                if (now - lastHeartbeatTime > 15000 && !isSelfHealing && _state.value == ObdState.CONNECTED) {
                    _statusMessage.value = "Enlace inactivo. Re-sincronizando..."
                    attemptSelfHealing()
                }
            }
        }
    }

    /**
     * Professional Hardware Benchmark.
     * Tests throughput, command latency, and ELM327 instruction set compatibility.
     */
    suspend fun runAdapterTests(): Map<String, String> {
        val results = mutableMapOf<String, String>()

        if (_state.value != ObdState.CONNECTED) {
            // Offline diagnostic mode — provide meaningful feedback
            kotlinx.coroutines.delay(500) // Simulate processing
            results["Estado"] = "Sin conexión OBD"
            results["Modo"] = "Diagnóstico Offline"
            results["Bluetooth"] = if (transport != null) "Adaptador detectado" else "No hay adaptador vinculado"
            results["Instrucción"] = "Conecta el adaptador ELM327 al puerto OBD2 del vehículo y enciende el contacto (ACC ON). Luego presiona CONECTAR en el Scanner."
            results["Verificación Hardware"] = "Pendiente — requiere conexión activa"
            results["Latencia"] = "N/A — sin enlace activo"
            results["Protocolo"] = "N/A — sin negociación"
            results["Voltaje"] = "N/A — sin lectura"
            return results
        }

        try {
            // 1. Latency Test
            val start = System.currentTimeMillis()
            repeat(5) { sendRawCommand("ATRV") }
            val avgLatency = (System.currentTimeMillis() - start) / 5
            results["Latencia Promedio"] = "$avgLatency ms"

            // 2. Protocol Compatibility
            val dpn = sendRawCommand("ATDPN")
            results["Protocolo Activo"] = parseProtocolName(dpn)

            // 3. Chipset Identification
            val version = sendRawCommand("ATI")
            results["Hardware ID"] = version.replace("\r", " ").trim()

            // 4. Voltage Precision
            val (ecuVolt, elmVolt) = readBatteryVoltage()
            val volt = if (elmVolt > 0f) elmVolt else ecuVolt
            results["Voltaje Sistema"] = "%.2fV".format(volt)

            // 5. Link Stability
            results["Estado de Enlace"] = if (avgLatency < 150) "Excelente (ELITE)" else "Estable"

        } catch (e: Exception) {
            results["Test Error"] = e.message ?: "Error desconocido"
        }

        return results
    }

    fun disconnect() {
        isRunning = false
        currentJob?.cancel()
        pollingJob?.cancel()
        heartbeatJob?.cancel()
        activeTestJob?.cancel()
        keepAliveManager.stop()
        scope.launch { try { transport?.disconnect() } catch (_: Exception) { } }
        _state.value = ObdState.DISCONNECTED
        _statusMessage.value = "Desconectado"
        _liveData.value = emptyMap()
        _activeTestStatus.value = ActiveTestStatus()
        // Clear identification data
        _calibrationId.value = null
        _ecuName.value = null
        // Reset calculated sensor accumulators
        lastSpeedKph = 0f
        lastSpeedTimestampMs = 0L
        tripDistanceKm = 0.0
        tripFuelUsedL = 0.0
        tripStartTimeMs = 0L
        speedAccumulator = 0.0
        speedSampleCount = 0
    }

    companion object {
        private const val TAG = "MEET_OBD"
        private const val MAX_CONNECT_ATTEMPTS = 3
    }
}

data class ObdCommand(val query: String, val priority: Int, val onSuccess: (String) -> Unit, val onError: (Exception) -> Unit)
class ObdCommandQueue {
    private val queue = mutableListOf<ObdCommand>()
    @Synchronized fun enqueue(c: ObdCommand) { queue.add(c); queue.sortByDescending { it.priority } }
    @Synchronized fun dequeue(): ObdCommand? = if (queue.isEmpty()) null else queue.removeAt(0)
    @Synchronized fun clear() { queue.clear() }
}
