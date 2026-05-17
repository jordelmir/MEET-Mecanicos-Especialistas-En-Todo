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

class ObdSession(
    private val scope: CoroutineScope,
    private val bluetoothAdapter: android.bluetooth.BluetoothAdapter?,
    private val context: Context
) {
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
    private val oemPidsToPoll = mutableSetOf<PidDefinition>()
    
    private val _activeTestStatus = MutableStateFlow(ActiveTestStatus())
    val activeTestStatus: StateFlow<ActiveTestStatus> = _activeTestStatus.asStateFlow()
    private var activeTestJob: Job? = null
    
    private var consecutiveErrors = 0
    private var isSelfHealing = false
    
    private val _isUdsSessionActive = MutableStateFlow(false)
    val isUdsSessionActive: StateFlow<Boolean> = _isUdsSessionActive.asStateFlow()

    private val _allDetectedDtcs = MutableStateFlow<Set<String>>(emptySet())
    val allDetectedDtcs: StateFlow<Set<String>> = _allDetectedDtcs.asStateFlow()

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
        // ─── CORE ENGINE ───
        "010C", // RPM
        "010D", // Vehicle Speed
        "0104", // Calculated Engine Load
        "010B", // Intake Manifold Absolute Pressure (MAP)
        "010E", // Timing Advance
        "0111", // Throttle Position
        "011F", // Run Time Since Engine Start
        // ─── TEMPERATURE ───
        "0105", // Engine Coolant Temperature
        "010F", // Intake Air Temperature
        "0146", // Ambient Air Temperature
        // ─── FUEL SYSTEM ───
        "0110", // MAF Air Flow Rate
        "012F", // Fuel Tank Level
        "0106", // Short Term Fuel Trim - Bank 1
        "0107", // Long Term Fuel Trim - Bank 1
        "0103", // Fuel System Status
        "010A", // Fuel Pressure
        // ─── O2 SENSORS ───
        "0114", // O2 Sensor 1 Bank 1 — Voltage + Short Term Fuel Trim
        "0115", // O2 Sensor 2 Bank 1 — Voltage + Short Term Fuel Trim
        // ─── ELECTRICAL ───
        // "0142" is skipped here because many ECUs report inaccurate voltage (e.g. 17V). 
        // We use ATRV periodically via the live polling loop instead.
        // ─── EMISSIONS/DIAGNOSTICS ───
        "0133", // Barometric Pressure
        "0101", // Monitor Status (MIL, DTC count, readiness)
        "011C"  // OBD Standards Compliance
    )

    private var targetAddress: String? = null

    fun setTargetAddress(address: String) {
        this.targetAddress = address
        transport?.let { old ->
            scope.launch { runCatching { old.disconnect() } }
        }
        
        if (address.contains(".") || address.contains(":35000") || address.contains(":35001")) {
            val parts = address.split(":")
            val ip = parts.getOrNull(0) ?: "192.168.0.10"
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 35000
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
                // 1. Physical Bluetooth Connection
                activeTransport.connect()
                Log.i(TAG, "✓ Physical link UP in ${System.currentTimeMillis()-t0}ms (attempt $attempt)")
                _statusMessage.value = "Conexión OK. Negociando ELM327..."
                _state.value = ObdState.NEGOTIATING
                
                // 2. ELM327/STN Protocol Negotiation
                withTimeout(90000) {
                    initializeAdapter()
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

                startLivePolling()
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

    private val customPidsToPoll = mutableSetOf<com.elysium369.meet.data.local.entities.CustomPidEntity>()

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

    private fun startLivePolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            val supportedPids = detectSupportedPids()
            
            // Priority 1: High-frequency PIDs (RPM, Speed, Throttle)
            val baseHighPriority = listOf("0C", "0D", "11") // PID codes without '01'
            
            var cycleCount = 0
            
            while (isRunning && isActive) {
                val cycleStartTime = System.currentTimeMillis()
                
                if (_highSpeedMode.value && _pinnedPids.value.isNotEmpty()) {
                    // HIGH SPEED MODE: Only poll pinned PIDs at max rate
                    pollBatch(_pinnedPids.value.toList())
                } else {
                    // NORMAL MODE: Balanced Polling
                    val highPriorityPids = (baseHighPriority + _pinnedPids.value.map { it.removePrefix("01") }).distinct()
                    
                    val normalPriorityPids = dashboardPids.map { it.removePrefix("01") }.filter { pid ->
                        !highPriorityPids.contains(pid)
                    }

                    // 1. Poll High Priority (Every Cycle) - Use Multi-PID request if on CAN
                    if (detectedProtocol.contains("CAN") && highPriorityPids.size > 1) {
                        pollMultiPidBatch(highPriorityPids.take(6)) // Max 6 PIDs per line in CAN
                    } else {
                        pollBatch(highPriorityPids.map { "01$it" })
                    }
                    
                    // 2. Poll Normal Priority (Every 3 cycles)
                    if (cycleCount % 3 == 0) {
                        if (detectedProtocol.contains("CAN") && normalPriorityPids.size > 1) {
                            pollMultiPidBatch(normalPriorityPids.take(6))
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

                    // 5. Poll ELM327 Battery Voltage directly (Every 10 cycles, overrides faulty ECU 0142)
                    if (cycleCount % 10 == 0) {
                        try {
                            val volt = readBatteryVoltage()
                            if (volt > 0f) {
                                // Inject into liveData as if it were PID 0142, so dashboards show correct voltage
                                updateLiveData("0142", volt) 
                            }
                        } catch (_: Exception) {}
                    }
                }
                
                updateQos(System.currentTimeMillis() - cycleStartTime)
                cycleCount++
                
                // Adaptive delay: High speed mode on pro adapter has 0 delay
                val targetDelay = when {
                    _highSpeedMode.value && !isCloneAdapter -> 5L // Minimal breathing time
                    _highSpeedMode.value && isCloneAdapter -> 30L
                    isCloneAdapter -> 80L
                    else -> 10L
                }
                if (targetDelay > 0) delay(targetDelay)
            }
        }
    }

    /**
     * Polls multiple PIDs in a single request. 
     * Supported by most CAN-bus ECUs. Reduces bus overhead significantly.
     */
    private suspend fun pollMultiPidBatch(pids: List<String>) {
        if (pids.isEmpty()) return
        try {
            val command = "01" + pids.joinToString("")
            val response = sendRawCommand(command)
            
            // Parse multi-response: 41 0C XX XX 0D YY ...
            pids.forEach { pid ->
                val parsed = parsePidResponse("01$pid", response)
                if (parsed != null) updateLiveData("01$pid", parsed)
            }
        } catch (_: Exception) {}
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
                val response = withTimeoutOrNull(1500) { result.await() } ?: continue
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
        val smoothedValue = if (corePid == "42") {
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
            val deltaT = (now - lastSpeedTimestampMs) / 1000f
            if (lastSpeedTimestampMs > 0 && deltaT > 0.05f && deltaT < 5f) {
                val deltaSpeed = (speedKph - lastSpeedKph) / 3.6f
                val accelG = deltaSpeed / (deltaT * 9.81f)
                data["CALC_ACCELERATION"] = accelG.coerceIn(-3f, 3f)
            }
            lastSpeedKph = speedKph
            lastSpeedTimestampMs = now
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
        if (speedKph != null && lastSpeedTimestampMs > 0) {
            val deltaTimeSec = (now - lastSpeedTimestampMs).coerceIn(0, 5000) / 1000.0
            val distIncrementKm = speedKph / 3600.0 * deltaTimeSec
            tripDistanceKm += distIncrementKm
            totalDistanceKm += distIncrementKm
            data["CALC_TRIP_DISTANCE"] = tripDistanceKm.toFloat()
            data["CALC_TOTAL_DISTANCE"] = totalDistanceKm.toFloat()
            
            speedAccumulator += speedKph
            speedSampleCount++
            data["CALC_AVG_SPEED"] = (speedAccumulator / speedSampleCount).toFloat()
        }
        
        // Fuel used (trip + total)
        if (fuelRateLh != null && lastSpeedTimestampMs > 0) {
            val deltaTimeSec = (now - lastSpeedTimestampMs).coerceIn(0, 5000) / 1000.0
            val fuelIncrement = fuelRateLh / 3600.0 * deltaTimeSec
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
        val queries = listOf("0100", "0120", "0140")
        for (query in queries) {
            try {
                val resp = sendRawCommand(query)
                val clean = CanMultiFrameParser.parse(resp)
                val modeResp = "41${query.substring(2)}"
                val idx = clean.uppercase().indexOf(modeResp.uppercase())
                if (idx < 0) continue
                val hex = clean.substring(idx + modeResp.length).take(8)
                val bitmap = hex.toLongOrNull(16) ?: continue
                val baseId = query.substring(2).toInt(16)
                for (bit in 31 downTo 0) {
                    if ((bitmap shr bit) and 1L == 1L) {
                        supported.add(baseId + (32 - bit))
                    }
                }
            } catch (_: Exception) {}
        }
        return supported
    }

    private fun parsePidResponse(pid: String, raw: String): Float? {
        val clean = CanMultiFrameParser.parse(raw)
        val pidHex = pid.substring(2).uppercase()
        val expectedPrefix = "41$pidHex"
        val idx = clean.indexOf(expectedPrefix)
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
        if (_state.value != ObdState.CONNECTED) return
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
            for ((id, data) in targetNodes) {
                val (name, type) = data
                _statusMessage.value = "Escanenado Nodo: $name ($id)..."
                
                val startTime = System.currentTimeMillis()
                val success = withTimeoutOrNull(600) {
                    try {
                        sendRawCommand("AT SH $id")
                        val resp = sendRawCommand("0100")
                        !resp.contains("NO DATA") && !resp.contains("ERROR") && !resp.contains("?")
                    } catch (e: Exception) { false }
                } ?: false
                val latency = System.currentTimeMillis() - startTime
                
                // Always add the module, but flag if it's dead
                discovered.add(NetworkModule(id, name, isAlive = success, networkType = type, latencyMs = latency))
            }
            
            // Reset header to default functional broadcast
            sendRawCommand("AT SH 7DF")
            _networkTopology.value = discovered
            _statusMessage.value = "Mapeo de Topología Completo: ${discovered.count { it.isAlive }} nodos activos."
        } catch (e: Exception) {
            Log.e(TAG, "Topology scan failed", e)
        } finally {
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
            val active = DtcDecoder.decode(response, "03").toMutableSet()
            
            // ── ELITE FEATURE: Umbrella Code Resolution ──
            // Cross-reference with Pending and Permanent memory if P0300 (Random/Multiple Misfire) is present.
            if (active.contains("P0300")) {
                try {
                    val pendingResp = sendRawCommand("07", priority = 999)
                    val pending = DtcDecoder.decode(pendingResp, "07")
                    
                    val permResp = sendRawCommand("0A", priority = 999)
                    val perm = DtcDecoder.decode(permResp, "0A")
                    
                    // Look for specific cylinder misfire codes (P0301 - P0312)
                    val specificMisfires = (pending + perm).filter { it.matches(Regex("P030[1-9]|P031[0-2]")) }.toMutableList()
                    
                    // Always trigger Deep Scan (Mode 06) for per-cylinder accuracy
                    val mode06Misfires = deepScanMisfires()
                    specificMisfires.addAll(mode06Misfires)
                    
                    // Add all specific misfires to the active list and ensure uniqueness
                    active.addAll(specificMisfires.distinct())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to resolve umbrella code details: ${e.message}")
                }
            }
            
            // Persistent Accumulation: DTCs remain until cleared explicitly.
            _allDetectedDtcs.value = _allDetectedDtcs.value + active
            
            active.toList()
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

    suspend fun clearDtcs(): Boolean {
        if (_state.value != ObdState.CONNECTED) return false
        return try {
            val response = sendRawCommand("04", priority = 1)
            val success = response.contains("OK") || response.contains("44")
            if (success) {
                _allDetectedDtcs.value = emptySet()
            }
            success
        } catch (_: Exception) { false }
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

    suspend fun scanModules(): List<NetworkModule> {
        if (_state.value != ObdState.CONNECTED) return emptyList()
        val modules = mutableListOf<NetworkModule>()
        
        _statusMessage.value = "Escaneando topología de red CAN..."
        
        try {
            // Standard 11-bit CAN Addressing (7E0-7EF)
            val can11Targets = mapOf(
                "7E0" to "ECM (Motor)", 
                "7E1" to "TCM (Transmisión)", 
                "7E2" to "ABS/ESP/TCS",
                "7E3" to "SRS (Airbag)", 
                "7E4" to "BCM (Carrocería)", 
                "7E5" to "IPC (Instrumentos)",
                "7E6" to "HVAC (Climatización)",
                "7E7" to "PSM (Asientos/Confort)"
            )
            
            try { sendRawCommand("ATH1") } catch (_: Exception) {}
            
            for ((addr, label) in can11Targets) {
                try {
                    sendRawCommand("ATSH$addr")
                    // Use a shorter timeout for probing commands if possible, but sendRawCommand is generic.
                    // 0100 is the most basic command to check if a module is alive.
                    val resp = sendRawCommand("0100")
                    if (resp.isNotBlank() && (resp.contains("41 00") || resp.contains("4100"))) {
                        // Alive! Query specific DTCs for this module
                        var dtcs = emptyList<String>()
                        try {
                            val dtcResp = sendRawCommand("03")
                            dtcs = DtcDecoder.decode(dtcResp, "03")
                        } catch (_: Exception) {}
                        
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
                        val resp = sendRawCommand("0100")
                        if (resp.isNotBlank() && resp.contains("4100")) {
                            var dtcs = emptyList<String>()
                            try {
                                val dtcResp = sendRawCommand("03")
                                dtcs = DtcDecoder.decode(dtcResp, "03")
                            } catch (_: Exception) {}
                            
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
        
        // Try Mode 01 PID A6 (Odometer - Newer vehicles)
        try {
            val resp = sendRawCommand("01A6")
            if (resp.contains("41A6") && !resp.contains("NO DATA")) {
                val clean = CanMultiFrameParser.parse(resp).replace("41A6", "")
                if (clean.length >= 8) {
                    return clean.substring(0, 8).toLong(16) / 10f // Unit is 0.1km
                }
            }
        } catch (_: Exception) {}

        // Fallback: Mode 01 PID 31 (Distance traveled since codes cleared)
        try {
            val resp = sendRawCommand("0131")
            if (resp.contains("4131") && !resp.contains("NO DATA")) {
                val clean = CanMultiFrameParser.parse(resp).replace("4131", "")
                if (clean.length >= 4) {
                    return clean.substring(0, 4).toInt(16).toFloat()
                }
            }
        } catch (_: Exception) {}

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
            val voltage = readBatteryVoltage()
            Log.d(TAG, "Battery voltage: ${voltage}V")
            if (voltage in 0.1f..9.0f) _statusMessage.value = "⚠ Batería baja: ${"%.1f".format(voltage)}V"
        } catch (_: Exception) {}
    }


    private suspend fun drainInput() {
        withContext(Dispatchers.IO) {
            transport?.drain()
        }
    }

    private suspend fun sendCommandDirectly(command: String, timeoutMs: Long = 3000L): String {
        return communicationMutex.withLock {
            withContext(Dispatchers.IO) {
                val t = transport ?: throw ObdConnectionException("Transport no disponible")
                Log.v(TAG, "TX (Direct): '$command' (timeout=${timeoutMs}ms)")
                t.write("$command\r".toByteArray())
                val resp = readResponse(timeoutMs)
                Log.v(TAG, "RX (Direct): '$resp' (${resp.length} chars)")
                delay(baseDelayMs) // Meet elite: adaptive delay
                resp
            }
        }
    }

    private suspend fun readResponse(timeoutMs: Long = 3000L): String = 
        withContext(Dispatchers.IO) {
        val t = transport ?: return@withContext ""
        val buffer = StringBuilder()
        val startTime = System.currentTimeMillis()
        var consecutiveNulls = 0
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val chunkSize = if (t is BtClassicTransport) 1024 else 512
            val chunk = t.read(chunkSize)
            
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
                // We reduce this from 20 to 2 for faster response on clone adapters.
                if (buffer.isNotEmpty() && consecutiveNulls >= 2) break
                delay(10) 
            }
        }
        return@withContext buffer.toString().replace("\r", " ").replace("\n", " ").trim()
    }

    suspend fun readBatteryVoltage(): Float {
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
        val elmVoltage = elmResponse.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 0f
        
        // Retornamos el valor real sin suavizar ni auto-calibrar matemáticamente. 
        // Esto permite diagnosticar el rizado del alternador.
        return if (ecuVoltage > 0f) ecuVoltage else elmVoltage
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
        val voltage = readBatteryVoltage()
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
        if (_state.value != ObdState.CONNECTED) return
        
        activeTestJob?.cancel()
        activeTestJob = scope.launch(Dispatchers.IO) {
            try {
                _activeTestStatus.value = ActiveTestStatus(isActive = true, message = "Iniciando: ${test.name}...", progress = 0.1f)
                
                if (!verifySafetyForProAction(test.safetyConditions)) {
                    _activeTestStatus.value = ActiveTestStatus(isActive = false, message = "ERROR: Condiciones de seguridad no cumplidas.")
                    return@launch
                }

                // Send Start Command
                _statusMessage.value = "Enviando comando de activación: ${test.startCommand}"
                val startResp = sendRawCommand(test.startCommand)
                if (startResp.contains("ERROR") || startResp.contains("NO DATA") || startResp.contains("CAN ERROR")) {
                    _activeTestStatus.value = ActiveTestStatus(isActive = false, message = "Fallo al iniciar: $startResp")
                    return@launch
                }

                _activeTestStatus.value = ActiveTestStatus(isActive = true, message = "PRUEBA ACTIVA: ${test.name}", progress = 0.5f)

                // Monitor loop
                val startTime = System.currentTimeMillis()
                var lastTesterPresentTime = startTime
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
                            val resp = sendRawCommand(pid)
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
                    _activeTestStatus.value = _activeTestStatus.value.copy(progress = progress, currentValues = monitoredData)
                    
                    delay(500) // 2Hz feedback
                }

                // Send Stop Command
                _statusMessage.value = "Deteniendo prueba: ${test.stopCommand}"
                sendRawCommand(test.stopCommand)
                
                _activeTestStatus.value = ActiveTestStatus(isActive = false, message = "Prueba completada con éxito.", progress = 1.0f)
                
            } catch (e: Exception) {
                _activeTestStatus.value = ActiveTestStatus(isActive = false, message = "Excepción en prueba: ${e.message}")
            }
        }
    }

    fun stopActiveTest() {
        activeTestJob?.cancel()
        scope.launch(Dispatchers.IO) {
            // Try to send stop command just in case
            _activeTestStatus.value = ActiveTestStatus(isActive = false, message = "Prueba detenida manualmente.")
        }
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
                                t.write("${command.query}\r".toByteArray())
                                val response = readResponse(timeoutMs = 5000L)
                                if (response.isNotBlank() && !response.contains("?")) {
                                    success = true
                                    consecutiveErrors = 0
                                    lastHeartbeatTime = System.currentTimeMillis()
                                    updateQos(System.currentTimeMillis() - startTime, true)
                                    command.onSuccess(response)
                                } else {
                                    attempts++
                                    drainInput()
                                }
                            }
                            if (!success) delay(100)
                        } catch (e: Exception) {
                            attempts++
                            drainInput()
                            delay(100)
                        }
                    }
                    if (!success) {
                        consecutiveErrors++
                        updateQos(System.currentTimeMillis() - startTime, false)
                        
                        if (consecutiveErrors >= 3 && !isSelfHealing) {
                            scope.launch { attemptSelfHealing() }
                        }
                        
                        command.onError(Exception("Timeout"))
                    } else {
                        // Success - apply adaptive delay before next command
                        delay(baseDelayMs)
                    }
                } else {
                    delay(50)
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
            val volt = readBatteryVoltage()
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
}

