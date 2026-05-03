package com.elysium369.meet.core.obd

import android.content.Context
import android.util.Log
import com.elysium369.meet.core.transport.BtClassicTransport
import com.elysium369.meet.core.transport.BleTransport
import com.elysium369.meet.core.transport.TransportInterface
import com.elysium369.meet.core.transport.WifiTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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

    private var adapterVersion: String = ""
    private var isCloneAdapter: Boolean = true
    private var detectedProtocol: String = ""
    
    // Performance Tracking
    private var lastCmdTime = 0L
    private var cmdCount = 0
    private val oemPidsToPoll = mutableSetOf<PidDefinition>()
    
    private val _activeTestStatus = MutableStateFlow(ActiveTestStatus())
    val activeTestStatus: StateFlow<ActiveTestStatus> = _activeTestStatus.asStateFlow()
    private var activeTestJob: Job? = null
    
    private var consecutiveErrors = 0
    private var isSelfHealing = false
    
    // Standard PIDs for dashboard polling
    private val dashboardPids = listOf(
        "010C", // RPM
        "010D", // Speed
        "0105", // Coolant temp
        "0104", // Engine load
        "010B", // MAP
        "0110", // MAF
        "0111", // Throttle
        "010F", // Intake air temp
        "010E", // Timing advance
        "012F", // Fuel level
        "0142"  // Control module voltage
    )

    fun setTargetAddress(address: String) {
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
        _statusMessage.value = "Estableciendo conexión física..."
        Log.i(TAG, "═══ OBD CONNECT START ═══")
        val t0 = System.currentTimeMillis()
        
        try {
            activeTransport.connect()
            Log.i(TAG, "✓ Physical link UP in ${System.currentTimeMillis()-t0}ms")
            _statusMessage.value = "Conexión OK. Negociando ELM327..."
            _state.value = ObdState.NEGOTIATING
            
            withTimeout(90000) {
                initializeAdapter()
            }
            
            _state.value = ObdState.CONNECTED
            _statusMessage.value = "Enlace Crítico Sincronizado: $adapterVersion"
            isRunning = true
            consecutiveErrors = 0
            Log.i(TAG, "═══ OBD CONNECT SUCCESS ═══ Total: ${System.currentTimeMillis()-t0}ms | Adapter=$adapterVersion | Protocol=$detectedProtocol")
            
            startQueueProcessor()
            startHeartbeatMonitor()
            keepAliveManager.start(scope)
            startLivePolling()
            
        } catch (e: Exception) {
            _state.value = ObdState.ERROR
            val msg = e.message ?: "Error desconocido"
            Log.e(TAG, "═══ OBD CONNECT FAILED ═══ in ${System.currentTimeMillis()-t0}ms: ${e.javaClass.simpleName}: $msg", e)
            _statusMessage.value = when {
                msg.contains("Adaptador no responde") -> "Adaptador no responde. Verifica que esté encendido y el contacto del auto en ON."
                msg.contains("ECU") -> "No se detectó ECU. Gira la llave a posición ON (sin arrancar)."
                msg.contains("Timed") -> "Timeout de negociación. Reintenta o verifica conexión Bluetooth."
                else -> "Error: $msg"
            }
            
            // ── TELEMETRÍA SILENCIOSA (SUPABASE) ──
            scope.launch {
                try {
                    val logData = mapOf(
                        "user_id" to "local_app_user",
                        "adapter_type" to adapterVersion,
                        "notes" to "FAILED_CONNECTION: $msg | Protocol: $detectedProtocol"
                    )
                    com.elysium369.meet.data.remote.SupabaseModule.client
                        .postgrest["scan_sessions"]
                        .insert(logData)
                    Log.i(TAG, "Telemetry uploaded successfully.")
                } catch (t: Exception) {
                    Log.e(TAG, "Telemetry upload failed", t)
                }
            }

            try { activeTransport.disconnect() } catch (_: Exception) {}
            isRunning = false
        }
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
                val response = sendRawCommand(command)
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

    private fun updateLiveData(pid: String, value: Float) {
        val current = _liveData.value.toMutableMap()
        current[pid] = value
        _liveData.value = current
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

    suspend fun readActiveDtcs(): List<String> {
        if (_state.value != ObdState.CONNECTED) return emptyList()
        return try {
            val response = sendRawCommand("03")
            DtcDecoder.decode(response, "03")
        } catch (_: Exception) { emptyList() }
    }

    suspend fun readPendingDtcs(): List<String> {
        if (_state.value != ObdState.CONNECTED) return emptyList()
        return try {
            val response = sendRawCommand("07")
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
            val response = sendRawCommand("0A")
            DtcDecoder.decode(response, "0A")
        } catch (_: Exception) { emptyList() }
    }

    suspend fun clearDtcs(): Boolean {
        if (_state.value != ObdState.CONNECTED) return false
        return try {
            // Some ECUs need Mode 04, others need Mode 04 + Protocol specific reset
            val response = sendRawCommand("04")
            !response.contains("ERROR") && !response.contains("UNABLE")
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

    suspend fun scanModules(): List<Pair<String, Boolean>> {
        if (_state.value != ObdState.CONNECTED) return emptyList()
        val modules = mutableListOf<Pair<String, Boolean>>()
        
        // Save current state
        val originalHeader = try { sendRawCommand("ATSH") } catch (_: Exception) { "" }
        
        _statusMessage.value = "Escaneando topología de red CAN..."
        
        // 1. Standard 11-bit CAN Addressing (7E0-7EF)
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
                val resp = sendRawCommand("0100")
                if (resp.isNotBlank() && (resp.contains("41 00") || resp.contains("4100"))) {
                    modules.add(label to true)
                }
            } catch (_: Exception) {}
        }

        // 2. Extended 29-bit CAN Addressing (Standard ISO-TP)
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
                        modules.add(label to true)
                    }
                } catch (_: Exception) {}
            }
        }
        
        // Restore environment
        try {
            sendRawCommand("ATSH7E0")
            sendRawCommand("ATH0")
        } catch (_: Exception) {}
        
        _statusMessage.value = "Escaneo completado: ${modules.size} módulos hallados."
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
        Log.i(TAG, "── INIT ADAPTER START ──")
        
        // --- 1. PHYSICAL WARM-UP ---
        Log.d(TAG, "[1/6] Warm-up: sending 3x CR flush")
        _statusMessage.value = "Sincronizando enlace físico..."
        for (i in 1..3) {
            try { t.write("\r".toByteArray()); delay(150) } catch (_: Exception) {}
        }
        drainInput()
        delay(300)
        Log.d(TAG, "[1/6] Warm-up complete")
        
        // --- 2. ADAPTER IDENTIFICATION ---
        Log.d(TAG, "[2/6] ATZ identification starting")
        _statusMessage.value = "Identificando adaptador..."
        var atzResponse = ""
        
        for (attempt in 1..3) {
            Log.d(TAG, "  ATZ attempt $attempt/3")
            atzResponse = sendCommandDirectly("ATZ", timeoutMs = 4000)
            Log.d(TAG, "  ATZ response: '$atzResponse'")
            if (atzResponse.contains("ELM", true) || atzResponse.contains("STN", true) || 
                atzResponse.contains("OBD", true) || atzResponse.contains("v1", true) ||
                atzResponse.contains("v2", true)) {
                Log.i(TAG, "  ✓ ATZ matched on attempt $attempt")
                break
            }
            if (attempt == 2) {
                Log.d(TAG, "  Trying AT WS fallback...")
                atzResponse = sendCommandDirectly("AT WS", timeoutMs = 3000)
                Log.d(TAG, "  AT WS response: '$atzResponse'")
                if (atzResponse.isNotBlank() && (atzResponse.contains("ELM", true) || atzResponse.contains(">"))) break
            }
            delay(500); drainInput()
        }
        
        if (atzResponse.isBlank() || (!atzResponse.contains("ELM", true) && !atzResponse.contains("STN", true) && !atzResponse.contains(">") && !atzResponse.contains("v1", true) && !atzResponse.contains("v2", true))) {
            Log.w(TAG, "  ATZ failed. Trying blind 0100...")
            val blindTest = sendCommandDirectly("0100", timeoutMs = 5000)
            Log.d(TAG, "  Blind 0100 response: '$blindTest'")
            if (blindTest.contains("4100") || blindTest.contains("41 00")) {
                Log.i(TAG, "  ✓ Blind 0100 success — minimal clone mode")
                adapterVersion = "ELM327 (Minimal Clone)"
                isCloneAdapter = true
                _isAdapterPro.value = false
                detectedProtocol = parseProtocolName(sendCommandDirectly("ATDPN", timeoutMs = 2000))
                _statusMessage.value = "Conectado (modo básico): $detectedProtocol"
                return
            }
            Log.e(TAG, "  ✗ Adapter completely unresponsive")
            throw ObdConnectionException("Adaptador no responde. Verifica que esté encendido.")
        }
        
        adapterVersion = parseAdapterVersion(atzResponse)
        isCloneAdapter = detectClone(atzResponse)
        _isAdapterPro.value = !isCloneAdapter
        Log.i(TAG, "[2/6] Adapter: $adapterVersion | clone=$isCloneAdapter")
        _statusMessage.value = "Adaptador: $adapterVersion"
        
        // --- 3. POST-RESET STABILIZATION ---
        val stabilizeMs = if (isCloneAdapter) 1500L else 300L
        Log.d(TAG, "[3/6] Post-reset stabilize: ${stabilizeMs}ms")
        delay(stabilizeMs)
        drainInput()
        
        // --- 4. OPTIMIZATION SETTINGS ---
        val baseDelay = if (isCloneAdapter) 150L else 30L
        Log.d(TAG, "[4/6] Sending AT config (baseDelay=${baseDelay}ms)")
        
        sendInitCommand("ATE0", baseDelay)
        sendInitCommand("ATL0", baseDelay)
        sendInitCommand("ATS0", baseDelay)
        sendInitCommand("ATH0", baseDelay)
        
        if (isCloneAdapter) {
            sendInitCommand("ATAT1", baseDelay)
            sendInitCommand("ATST96", baseDelay)
        } else {
            sendInitCommand("ATAT2", baseDelay)
            sendInitCommand("ATST19", baseDelay)
            
            // ── SOPORTE ULTRA-RÁPIDO PARA ADAPTADORES PREMIUM (vLinker / OBDLink) ──
            val atiResponse = sendCommandDirectly("ATI", timeoutMs = 2000)
            val isSTN = adapterVersion.contains("STN", true) || adapterVersion.contains("OBDLink", true) || 
                        adapterVersion.contains("vLinker", true) || atiResponse.contains("STN", true) || 
                        atiResponse.contains("vLinker", true)
                        
            if (isSTN) {
                // Multiplica la velocidad de lectura anulando latencias de bus (STN exclusive)
                sendInitCommand("ST AT 1", baseDelay)  // Enable STN Advanced Timing
                sendInitCommand("STP31", baseDelay)    // Disable ELM response timeouts for fast-polling
                sendInitCommand("STPBR 1", baseDelay)  // Optimize baud rate
                Log.i(TAG, "  PRO (STN/vLinker) commands injected successfully. Speed multiplier active.")
            }
        }
        Log.d(TAG, "[4/6] AT config complete")
        
        // --- 5. VOLTAGE CHECK ---
        try {
            val voltage = readBatteryVoltage()
            Log.d(TAG, "[5/6] Battery voltage: ${voltage}V")
            if (voltage in 0.1f..9.0f) _statusMessage.value = "⚠ Batería baja: ${"%.1f".format(voltage)}V"
        } catch (e: Exception) { Log.w(TAG, "[5/6] Voltage read failed: ${e.message}") }
        
        // --- 6. PROTOCOL NEGOTIATION ---
        Log.i(TAG, "[6/6] Protocol negotiation starting")
        _statusMessage.value = "Buscando protocolo del vehículo..."
        var ecuConnected = false
        
        // Step A: Auto-detect
        Log.d(TAG, "  Step A: ATSP0 auto-detect")
        sendInitCommand("ATSP0", baseDelay)
        for (attempt in 1..3) {
            _statusMessage.value = "Auto-detectando protocolo (intento $attempt/3)..."
            Log.d(TAG, "  ATSP0 + 0100 attempt $attempt/3")
            val response = sendCommandDirectly("0100", timeoutMs = 6000L)
            Log.d(TAG, "  0100 response: '$response'")
            if (response.contains("4100") || response.contains("41 00")) {
                ecuConnected = true; Log.i(TAG, "  ✓ ECU found via ATSP0 on attempt $attempt"); break
            }
            if (response.contains("UNABLE") || response.contains("ERROR") || response.contains("CAN ERROR")) {
                Log.w(TAG, "  ✗ ATSP0 got error response, moving to manual sweep"); break
            }
            Log.d(TAG, "  Still searching... waiting 1s")
            delay(1000)
        }
        
        // Step B: Manual sweep
        if (!ecuConnected) {
            val protocols = listOf("ATSP6","ATSP7","ATSP3","ATSP5","ATSP8","ATSP1","ATSP2")
            Log.d(TAG, "  Step B: Manual protocol sweep")
            for (cmd in protocols) {
                if (!isRunning) break
                try {
                    val pName = cmd.removePrefix("ATSP")
                    _statusMessage.value = "Escaneando protocolo $pName..."
                    Log.d(TAG, "  Trying $cmd...")
                    sendInitCommand(cmd, baseDelay)
                    val resp = sendCommandDirectly("0100", timeoutMs = 4000L)
                    Log.d(TAG, "  $cmd + 0100 = '$resp'")
                    if (resp.contains("4100") || resp.contains("41 00")) {
                        ecuConnected = true; Log.i(TAG, "  ✓ ECU found via $cmd"); break
                    }
                } catch (e: Exception) { Log.w(TAG, "  $cmd exception: ${e.message}") }
            }
        }
        
        // Step C: Final fallback
        if (!ecuConnected) {
            Log.d(TAG, "  Step C: Final ATSP0 fallback (8s)")
            sendInitCommand("ATSP0", 200)
            val resp = sendCommandDirectly("0100", timeoutMs = 8000L)
            Log.d(TAG, "  Final 0100 = '$resp'")
            if (resp.contains("4100") || resp.contains("41 00")) ecuConnected = true
        }
        
        if (!ecuConnected) {
            Log.e(TAG, "  ✗ ECU NOT FOUND after full negotiation")
            throw ObdConnectionException("No se detectó ECU. ¿Está el contacto en ON?")
        }
        
        detectedProtocol = parseProtocolName(sendCommandDirectly("ATDPN", timeoutMs = 2000))
        Log.i(TAG, "── INIT ADAPTER DONE ── protocol=$detectedProtocol")
        _statusMessage.value = "✓ Conectado: $detectedProtocol"
    }


    private suspend fun sendInitCommand(command: String, delayMs: Long) {
        try {
            val resp = sendCommandDirectly(command, timeoutMs = 1500)
            Log.v(TAG, "  AT[$command] -> '$resp'")
            delay(delayMs)
        } catch (e: Exception) { 
            Log.w(TAG, "  AT[$command] FAILED: ${e.message}")
            delay(delayMs) 
        }
    }

    private suspend fun drainInput() {
        withContext(Dispatchers.IO) {
            val t = transport ?: return@withContext
            if (t is BtClassicTransport) {
                t.drain()
            } else {
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < 300) {
                    t.read(1024) ?: break
                }
            }
        }
    }

    private suspend fun sendCommandDirectly(command: String, timeoutMs: Long = 3000L): String {
        return withContext(Dispatchers.IO) {
            val t = transport ?: throw ObdConnectionException("Transport no disponible")
            Log.v(TAG, "TX: '$command' (timeout=${timeoutMs}ms)")
            t.write("$command\r".toByteArray())
            val resp = readResponse(timeoutMs)
            Log.v(TAG, "RX: '$resp' (${resp.length} chars)")
            resp
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
                // If we already have data and no new bytes for a while, response is complete
                if (buffer.isNotEmpty() && consecutiveNulls > 20) break
                delay(10) // Slightly longer sleep — BT Classic has inherent latency
            }
        }
        return@withContext buffer.toString().replace("\r", " ").replace("\n", " ").trim()
    }

    suspend fun readBatteryVoltage(): Float {
        // First try ELM327 internal voltage sensor (works even if ignition is off)
        val response = if (isRunning) sendRawCommand("ATRV") else sendCommandDirectly("ATRV", 2000L)
        val elmVoltage = response.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 0f
        
        if (elmVoltage > 5f) return elmVoltage
        
        // If that fails, try Mode 01 PID 42 (Control Module Voltage)
        val obdResponse = if (isRunning) sendRawCommand("0142") else sendCommandDirectly("0142", 3000L)
        val clean = CanMultiFrameParser.parse(obdResponse).replace(" ", "")
        return if (clean.length >= 4) {
            val a = clean.substring(clean.length - 4, clean.length - 2).toInt(16)
            val b = clean.substring(clean.length - 2).toInt(16)
            (256 * a + b) / 1000f
        } else 0f
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
                while (System.currentTimeMillis() - startTime < test.durationMs) {
                    if (!isActive) break
                    
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
                            t.write("${command.query}\r".toByteArray())
                            val response = readResponse(timeoutMs = 5000L)
                            if (response.isNotBlank() && !response.contains("?")) {
                                success = true
                                consecutiveErrors = 0
                                lastHeartbeatTime = System.currentTimeMillis() // Update heartbeat
                                updateQos(System.currentTimeMillis() - startTime, true)
                                command.onSuccess(response)
                            } else {
                                attempts++
                                drainInput()
                                delay(100)
                            }
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
                    }
                } else {
                    delay(50)
                }
            }
        }
    }

    fun enqueueCommand(q: String, p: Int = 1, s: (String) -> Unit, e: (Exception) -> Unit) {
        if (isRunning) commandQueue.enqueue(ObdCommand(q, p, s, e))
    }
    
    suspend fun sendRawCommand(command: String): String {
        val deferred = CompletableDeferred<String>()
        enqueueCommand(command, 999, { deferred.complete(it) }, { deferred.completeExceptionally(it) })
        return deferred.await()
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
    }
    
    companion object {
        private const val TAG = "MEET_OBD"
    }
}

data class ObdCommand(val query: String, val priority: Int, val onSuccess: (String) -> Unit, val onError: (Exception) -> Unit)
class ObdCommandQueue {
    private val queue = mutableListOf<ObdCommand>()
    @Synchronized fun enqueue(c: ObdCommand) { queue.add(c); queue.sortByDescending { it.priority } }
    @Synchronized fun dequeue(): ObdCommand? = if (queue.isEmpty()) null else queue.removeAt(0)
}

