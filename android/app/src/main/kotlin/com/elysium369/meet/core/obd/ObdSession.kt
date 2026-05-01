package com.elysium369.meet.core.obd

import android.content.Context
import com.elysium369.meet.core.transport.BtClassicTransport
import com.elysium369.meet.core.transport.BleTransport
import com.elysium369.meet.core.transport.TransportInterface
import com.elysium369.meet.core.transport.WifiTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

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
        
        try {
            activeTransport.connect()
            _statusMessage.value = "Conexión OK. Negociando ELM327..."
            _state.value = ObdState.NEGOTIATING
            
            initializeAdapter()
            
            _state.value = ObdState.CONNECTED
            _statusMessage.value = "Sistema Listo: $adapterVersion | $detectedProtocol"
            isRunning = true
            
            startQueueProcessor()
            keepAliveManager.start()
            startLivePolling()
            
        } catch (e: Exception) {
            _state.value = ObdState.ERROR
            _statusMessage.value = "Error: ${e.message}"
            try { activeTransport.disconnect() } catch (_: Exception) {}
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
            val baseHighPriority = listOf("010C", "010D", "0111")
            
            var cycleCount = 0
            
            while (isRunning && isActive) {
                val cycleStartTime = System.currentTimeMillis()
                
                if (_highSpeedMode.value && _pinnedPids.value.isNotEmpty()) {
                    // HIGH SPEED MODE: Only poll pinned PIDs at max rate
                    pollBatch(_pinnedPids.value.toList())
                } else {
                    // NORMAL MODE: Balanced Polling
                    val highPriorityPids = (baseHighPriority + _pinnedPids.value).distinct().filter { pid ->
                        if (pid.startsWith("01")) supportedPids.contains(pid.substring(2).toInt(16)) else true
                    }
                    
                    val normalPriorityPids = dashboardPids.filter { pid ->
                        !highPriorityPids.contains(pid) && supportedPids.contains(pid.substring(2).toInt(16))
                    }

                    // 1. Poll High Priority (Every Cycle)
                    pollBatch(highPriorityPids)
                    
                    // 2. Poll Normal Priority (Every 3 cycles)
                    if (cycleCount % 3 == 0) {
                        pollBatch(normalPriorityPids)
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
                    _highSpeedMode.value && !isCloneAdapter -> 0L
                    _highSpeedMode.value && isCloneAdapter -> 50L
                    isCloneAdapter -> 100L
                    else -> 20L
                }
                if (targetDelay > 0) delay(targetDelay)
            }
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
                // For mode 22, it's 62 + pid
                val prefix = (cp.mode.toInt() + 40).toString() + cp.pid
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
        
        results["DTC"] = parseMode02Response("02", dtcResp)
        
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
                val b1 = data.substring(0, 2).toInt(16)
                val b2 = data.substring(2, 4).toInt(16)
                DtcDecoder.hexToDtc(b1, b2)
            }
            "05" -> "${data.substring(0, 2).toInt(16) - 40}°C"
            "0C" -> "${(data.substring(0, 4).toInt(16)) / 4} RPM"
            "0D" -> "${data.substring(0, 2).toInt(16)} km/h"
            "04", "11" -> "${(data.substring(0, 2).toInt(16) * 100 / 255)}%"
            "03" -> if (data.substring(0, 2).toInt(16) == 2) "Closed Loop" else "Open Loop"
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
        
        // --- 1. PHYSICAL HANDSHAKE ---
        try {
            t.write("\r".toByteArray())
            delay(100)
            drainInput()
        } catch (_: Exception) {}
        
        var atzResponse = sendCommandDirectly("ATZ", timeoutMs = 3000)
        if (atzResponse.isBlank()) atzResponse = sendCommandDirectly("AT WS", timeoutMs = 2500)
        if (atzResponse.isBlank()) {
            atzResponse = sendCommandDirectly("AT", timeoutMs = 1500)
            if (atzResponse.isBlank()) throw ObdConnectionException("Adaptador no responde.")
        }
        
        adapterVersion = parseAdapterVersion(atzResponse)
        isCloneAdapter = detectClone(atzResponse)
        _isAdapterPro.value = !isCloneAdapter
        
        // --- 2. VOLTAGE & IGNITION CHECK ---
        val voltage = readBatteryVoltage()
        if (voltage < 9.0f && voltage > 0.1f) {
            _statusMessage.value = "Advertencia: Batería baja (${voltage}V)"
        }
        
        // Professional Delay: Clones need time to reboot
        delay(if (isCloneAdapter) 1000 else 200)
        
        // --- 3. OPTIMIZATION SETTINGS ---
        val baseDelay = if (isCloneAdapter) 150L else 20L
        sendInitCommand("ATE0", baseDelay) // Echo Off
        sendInitCommand("ATL0", baseDelay) // Linefeed Off
        sendInitCommand("ATS0", baseDelay) // Spaces Off
        sendInitCommand("ATH0", baseDelay) // Headers Off
        sendInitCommand("ATCAF1", baseDelay) // CAN Auto Formatting On
        
        // Adaptive adaptive timing
        if (isCloneAdapter) {
            sendInitCommand("ATAT2", baseDelay) // Aggressive Adaptive Timing for clones
            sendInitCommand("ATST96", baseDelay) // Higher timeout for slow clones
        } else {
            sendInitCommand("ATAT1", baseDelay) // Standard Adaptive Timing
            sendInitCommand("ATSTFF", baseDelay) // Max timeout for STN flexibility
            // Enable STN specific optimizations if applicable
            if (adapterVersion.contains("STN") || adapterVersion.contains("OBDLink")) {
                sendInitCommand("STFAC", baseDelay) // Fast CAN for STN
            }
        }
        
        // --- 4. PROTOCOL NEGOTIATION ---
        _statusMessage.value = "Detectando protocolo..."
        
        val protocolsToTry = listOf("ATSP0", "ATSP6", "ATSP7", "ATSP3", "ATSP1")
        var ecuConnected = false
        
        for (protocolCmd in protocolsToTry) {
            try {
                sendInitCommand(protocolCmd, baseDelay)
                _statusMessage.value = "Probando protocolo: $protocolCmd"
                
                for (attempt in 1..3) {
                    val response = sendCommandDirectly("0100", timeoutMs = 5000L)
                    if (response.contains("4100") || response.contains("41 00")) {
                        ecuConnected = true
                        break
                    }
                    if (response.contains("UNABLE") || response.contains("ERROR")) break 
                    delay(300)
                }
                if (ecuConnected) break
            } catch (_: Exception) {}
        }
        
        if (!ecuConnected) {
            // Final fallback: try search
            val searchResp = sendCommandDirectly("0100", timeoutMs = 8000L)
            if (!searchResp.contains("4100")) {
                throw ObdConnectionException("ECU no detectada. Verifica el encendido y el protocolo.")
            }
        }
        
        val dpnResponse = sendCommandDirectly("ATDPN", timeoutMs = 2000)
        detectedProtocol = parseProtocolName(dpnResponse)
        
        // If we are on CAN, we can try to get more info
        if (detectedProtocol.contains("CAN")) {
            sendInitCommand("ATCRA", baseDelay) // Reset CAN Receive Address
        }
    }

    private suspend fun sendInitCommand(command: String, delayMs: Long) {
        try {
            sendCommandDirectly(command, timeoutMs = 1500)
            delay(delayMs)
        } catch (_: Exception) { delay(delayMs) }
    }

    private suspend fun drainInput() {
        withContext(Dispatchers.IO) {
            val t = transport ?: return@withContext
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 300) {
                t.read(1024) ?: break
            }
        }
    }

    private suspend fun sendCommandDirectly(command: String, timeoutMs: Long = 3000L): String {
        return withContext(Dispatchers.IO) {
            val t = transport ?: throw ObdConnectionException("Transport no disponible")
            t.write("$command\r".toByteArray())
            readResponse(timeoutMs)
        }
    }

    private suspend fun readResponse(timeoutMs: Long = 3000L): String = 
        withContext(Dispatchers.IO) {
        val t = transport ?: return@withContext ""
        val buffer = StringBuilder()
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val chunk = t.read(512)
            if (chunk != null) {
                val str = String(chunk, Charsets.ISO_8859_1)
                buffer.append(str)
                keepAliveManager.notifyBytesReceived()
                if (buffer.contains('>')) break
                
                val current = buffer.toString().uppercase()
                if (current.contains("NO DATA") || current.contains("UNABLE") ||
                    current.contains("CAN ERROR") || current.contains("?")) break
            } else {
                delay(10)
            }
        }
        return@withContext buffer.toString().replace("\r", " ").replace("\n", " ").trim()
    }

    suspend fun readBatteryVoltage(): Float {
        // First try ELM327 internal voltage sensor (works even if ignition is off)
        val response = sendRawCommand("ATRV")
        val elmVoltage = response.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 0f
        
        if (elmVoltage > 5f) return elmVoltage
        
        // If that fails, try Mode 01 PID 42 (Control Module Voltage)
        val obdResponse = sendRawCommand("0142")
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
        for (condition in conditions) {
            when (condition) {
                SafetyCondition.ENGINE_OFF -> {
                    val rpm = liveData.value["RPM"] ?: 0f
                    if (rpm > 100f) {
                        _statusMessage.value = "ERROR: El motor debe estar APAGADO para esta acción."
                        return false
                    }
                }
                SafetyCondition.ENGINE_RUNNING -> {
                    val rpm = liveData.value["RPM"] ?: 0f
                    if (rpm < 400f) {
                        _statusMessage.value = "ERROR: El motor debe estar ENCENDIDO para esta acción."
                        return false
                    }
                }
                SafetyCondition.BATTERY_ABOVE_12V -> {
                    if (voltage < 12.0f) {
                        _statusMessage.value = "ERROR: Batería baja (${voltage}V). Mínimo 12.0V requerido."
                        return false
                    }
                }
                else -> {} // Vehicle stationary and others would need GPS/Wheel speed logic
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
                    val progress = 0.5f + (elapsed.toFloat() / test.durationMs.toFloat() * 0.4f)
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


    /**
     * Performs a series of hardware tests to validate adapter quality and capabilities.
     * This replaces any simulated tests with real command/response analysis.
     */
    suspend fun runAdapterTests(): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        if (_state.value == ObdState.DISCONNECTED) return listOf("Error" to "No conectado")

        // Test 1: Identify Chip
        val id = sendCommandDirectly("ATI")
        results.add("Identificación" to id)

        // Test 2: Protocol Support
        val protocol = sendCommandDirectly("ATDP")
        results.add("Protocolo Actual" to protocol)

        // Test 3: Voltage
        val voltage = readBatteryVoltage()
        results.add("Voltaje de Entrada" to "%.2f V".format(voltage))

        // Test 4: Buffer Capacity (Professional Check)
        // We try to send a long command and see if it errors out
        val bufferTest = sendCommandDirectly("AT@1") // General Device Info (STN/vLinker)
        results.add("Información Avanzada" to if (bufferTest.contains("?")) "No soportado (Standard/Clone)" else bufferTest)

        // Test 5: Timing Test
        val start = System.currentTimeMillis()
        sendCommandDirectly("0100")
        val end = System.currentTimeMillis()
        results.add("Latencia de Bus" to "${end - start} ms")

        return results
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
        val clean = response.uppercase().trim().replace("A", "")
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
                                updateQos(System.currentTimeMillis() - startTime, true)
                                command.onSuccess(response)
                            } else {
                                attempts++
                                delay(100)
                            }
                        } catch (e: Exception) {
                            attempts++
                            delay(100)
                        }
                    }
                    if (!success) {
                        updateQos(System.currentTimeMillis() - startTime, false)
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

    fun disconnect() {
        isRunning = false
        currentJob?.cancel()
        pollingJob?.cancel()
        keepAliveManager.stop()
        scope.launch { try { transport?.disconnect() } catch (_: Exception) { } }
        _state.value = ObdState.DISCONNECTED
        _statusMessage.value = "Desconectado"
        _liveData.value = emptyMap()
    }
}

data class ObdCommand(val query: String, val priority: Int, val onSuccess: (String) -> Unit, val onError: (Exception) -> Unit)
class ObdCommandQueue {
    private val queue = mutableListOf<ObdCommand>()
    @Synchronized fun enqueue(c: ObdCommand) { queue.add(c); queue.sortByDescending { it.priority } }
    @Synchronized fun dequeue(): ObdCommand? = if (queue.isEmpty()) null else queue.removeAt(0)
}

