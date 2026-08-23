package com.elysium369.meet.core.obd

import android.content.Context
import android.util.Log
import com.elysium369.meet.BuildConfig
import com.elysium369.meet.core.vanguard.AdapterQualityProfiler
import com.elysium369.meet.core.vanguard.ClassifiedEcuFailure
import com.elysium369.meet.core.vanguard.DerivedMetricsEngine
import com.elysium369.meet.core.vanguard.EcuFailureContext
import com.elysium369.meet.core.vanguard.EcuFailureIntelligence
import com.elysium369.meet.core.vanguard.EcuFailureType
import com.elysium369.meet.core.vanguard.ObdSessionFinishContext
import com.elysium369.meet.core.vanguard.ObdSessionRecorder
import com.elysium369.meet.core.vanguard.ObdSessionStartContext
import com.elysium369.meet.core.vanguard.ObdPollingScheduler
import com.elysium369.meet.core.vanguard.SensorSource
import com.elysium369.meet.core.vanguard.SensorValueState
import com.elysium369.meet.core.vanguard.VehicleProfileFingerprint
import com.elysium369.meet.core.vanguard.VanguardPrivacyGuard
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
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

enum class ObdState {
    DISCONNECTED,
    CONNECTING,
    NEGOTIATING,
    CONNECTED,
    ERROR
}

enum class PhysicalBusOwner {
    IDLE,
    NETWORK_DISCOVERY,
    DIAGNOSTIC_SCAN,
    FREEZE_FRAME,
    READINESS,
    MODE_06,
    OSCILLOSCOPE,
    ACTIVE_TEST,
    DTC_CLEAR,
    VIN_READ,
    EVAIR_READ,
    TERMINAL_READ,
}

class ObdBusBusyException(owner: PhysicalBusOwner) :
    IllegalStateException("OBD physical bus is exclusively owned by $owner")

private class PhysicalBusLeaseContext(
    val owner: PhysicalBusOwner,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<PhysicalBusLeaseContext>
}

internal object PhysicalBusLeasePolicy {
    fun allows(activeOwner: PhysicalBusOwner, callerOwner: PhysicalBusOwner?): Boolean =
        activeOwner == PhysicalBusOwner.IDLE || activeOwner == callerOwner
}

/**
 * ObdSession — Professional Grade OBD2 Communication Engine.
 * Handles high-frequency polling, multi-frame ISO-TP responses,
 * and robust ELM327/STN initialization.
 */
enum class NetworkType {
    CAN_HS,      // CAN High-Speed (500 kbps, ISO 11898-2)
    CAN_MS,      // CAN Mid-Speed (125-250 kbps)
    CAN_LS,      // CAN Low-Speed (33-125 kbps, ISO 11519-2)
    CAN_FD,      // CAN FD (Flexible Data-rate, 2-8 Mbps)
    LIN,         // Local Interconnect Network (19.2 kbps)
    K_LINE,      // ISO 9141-2 / ISO 14230 (KWP2000)
    FLEXRAY,     // FlexRay (10 Mbps)
    MOST,        // Media Oriented Systems Transport
    ETHERNET,    // Automotive Ethernet (100BASE-T1)
    SINGLE_WIRE, // GMLAN Single Wire CAN (33.33 kbps)
    UNKNOWN
}

/** Physical addressing mode used to communicate with the ECU */
enum class AddressingType {
    CAN_11BIT,   // Standard 11-bit CAN ID (ISO 15765-4)
    CAN_29BIT,   // Extended 29-bit CAN ID (ISO 15765-4)
    KWP_FAST,    // KWP2000 Fast Init (ISO 14230)
    KWP_SLOW,    // KWP2000 5-baud Init (ISO 14230)
    ISO9141,     // ISO 9141-2
    UNKNOWN
}

data class NetworkModule(
    val id: String,                          // CAN ID (e.g. "7E0") or ECU address
    val name: String,                        // Human-readable name
    val isAlive: Boolean,                    // Responded to ping
    val networkType: NetworkType = NetworkType.CAN_HS,
    val addressing: AddressingType = AddressingType.CAN_11BIT,
    val latencyMs: Long = 0,                 // Response time in ms
    val dtcs: List<String> = emptyList(),     // Active DTCs on this ECU
    // ── Real ECU identification data ──
    val responseId: String = "",             // ECU response CAN ID (e.g. "7E8")
    val supportedPids: List<String> = emptyList(), // PIDs that responded
    val ecuPartNumber: String? = null,       // From UDS $22 F187
    val ecuSoftwareVersion: String? = null,  // From UDS $22 F189
    val ecuHardwareVersion: String? = null,  // From UDS $22 F191
    val ecuSerialNumber: String? = null,     // From UDS $22 F18C
    val vinFromEcu: String? = null,          // VIN read from this specific ECU
    val supportsUds: Boolean = false,        // Responded to UDS $10/$22
    val supportedUdsServices: List<String> = emptyList(), // e.g. ["10","22","31"]
    val calibrationId: String? = null,       // Calibration ID (Mode $09 InfoType 04)
    val cvn: String? = null,                 // Calibration Verification Number
    val busSpeed: String? = null,            // Detected bus speed
    val protocolDetected: String? = null     // ELM protocol string
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
    private val context: Context,
    private val sessionRecorder: ObdSessionRecorder
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
    private val physicalBusActor = PhysicalBusActor()
    val physicalBusOwner: StateFlow<PhysicalBusOwner> = physicalBusActor.owner
    private val keepAliveManager = KeepAliveManager(this)

    private val _liveData = MutableStateFlow<Map<String, Float>>(emptyMap())
    val liveData: StateFlow<Map<String, Float>> = _liveData.asStateFlow()

    private val _liveSensorStates = MutableStateFlow<Map<String, SensorValueState>>(emptyMap())
    val liveSensorStates: StateFlow<Map<String, SensorValueState>> = _liveSensorStates.asStateFlow()

    private val _telemetrySamples = MutableStateFlow<Map<String, TelemetrySample>>(emptyMap())
    val telemetrySamples: StateFlow<Map<String, TelemetrySample>> = _telemetrySamples.asStateFlow()

    private val _freezeFrame = MutableStateFlow<Map<String, String>>(emptyMap())
    val freezeFrame: StateFlow<Map<String, String>> = _freezeFrame.asStateFlow()

    private val _vin = MutableStateFlow<String?>(null)
    val vin: StateFlow<String?> = _vin.asStateFlow()

    private var transport: TransportInterface? = null
    val activeTransportName: String?
        get() = transport?.javaClass?.simpleName
    private var isRunning = false
    @Volatile
    private var isPollingPaused = false
    @Volatile
    private var currentVanguardSessionId: String? = null
    private val derivedMetricsEngine = DerivedMetricsEngine()
    private val ecuFailureIntelligence = EcuFailureIntelligence()
    private val adapterQualityProfiler = AdapterQualityProfiler()
    private val pollingScheduler = ObdPollingScheduler()
    private val privacyGuard = VanguardPrivacyGuard()
    private val lastSensorRecordMs = mutableMapOf<String, Long>()

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

    private suspend fun <T> withExclusivePhysicalBus(
        owner: PhysicalBusOwner,
        block: suspend () -> T,
    ): T {
        val pollingWasPaused = isPollingPaused
        return physicalBusActor.withLease(
            owner = owner,
            onAcquire = {
                pauseLivePolling()
                clearCommandQueue()
            },
            onRelease = { if (!pollingWasPaused) resumeLivePolling() },
        ) {
            withContext(PhysicalBusLeaseContext(owner)) { block() }
        }
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
    var adapterVersion: String = ""
        set(value) { field = value; _adapterVersion.value = value }

    var isSTN: Boolean = false

    private val _isCloneAdapter = MutableStateFlow(true)
    val isCloneAdapterFlow: StateFlow<Boolean> = _isCloneAdapter.asStateFlow()
    var isCloneAdapter: Boolean = true
        set(value) { field = value; _isCloneAdapter.value = value }

    private val _detectedProtocol = MutableStateFlow("")
    val detectedProtocolFlow: StateFlow<String> = _detectedProtocol.asStateFlow()
    var detectedProtocol: String = ""
        set(value) { field = value; _detectedProtocol.value = value }

    suspend fun scanDtcErrors(): List<String> {
        val report = readProfessionalDtcScan()
        return report.records.map { it.code }.distinct()
    }

    @Deprecated(
        message = "Boolean cannot represent accepted, partial, inconclusive and verified clear outcomes. Use clearDtcs().",
        level = DeprecationLevel.ERROR,
    )
    suspend fun clearDtcErrors(): Boolean =
        error("Legacy clear API retired: use clearDtcs() and inspect ClearDtcResult")

    private val _calibrationId = MutableStateFlow<String?>(null)
    val calibrationId: StateFlow<String?> = _calibrationId.asStateFlow()

    private val _ecuName = MutableStateFlow<String?>(null)
    val ecuName: StateFlow<String?> = _ecuName.asStateFlow()

    @Volatile
    private var vehicleCapabilityContext: DiagnosticCapabilityContext = DiagnosticCapabilityContext.UNKNOWN

    fun setVehicleCapabilityContext(
        manufacturer: String?,
        modelFamily: String?,
        year: Int?,
        market: String? = null,
    ) {
        vehicleCapabilityContext = DiagnosticCapabilityContext(
            manufacturer = manufacturer?.takeIf(String::isNotBlank),
            modelFamily = modelFamily?.takeIf(String::isNotBlank),
            year = year,
            market = market?.takeIf(String::isNotBlank),
            ecuFamily = null,
            ecuAddress = null,
            hardwareVersion = null,
            softwareVersion = null,
            calibrationId = null,
        )
    }

    private var baseDelayMs: Long = 50L
    private var maxLineLength: Int = 128

    // Performance Tracking
    private var lastCmdTime = 0L
    private var cmdCount = 0
    private val oemPidsToPoll = CopyOnWriteArraySet<PidDefinition>()

    private val _activeTestStatus = MutableStateFlow(ActiveTestStatus())
    val activeTestStatus: StateFlow<ActiveTestStatus> = _activeTestStatus.asStateFlow()
    private val _activeTestEvidence = MutableStateFlow<List<ActiveTestEvidence>>(emptyList())
    val activeTestEvidence: StateFlow<List<ActiveTestEvidence>> = _activeTestEvidence.asStateFlow()
    @Suppress("unused")
    private val activeTestEvidenceCollector = scope.launch {
        activeTestStatus.drop(1).collect { status ->
            val testId = status.testId ?: return@collect
            val event = ActiveTestEvidence(
                evidenceId = java.util.UUID.randomUUID().toString(),
                testId = testId,
                phase = status.phase,
                message = status.message,
                monotonicTimestampMs = System.nanoTime() / 1_000_000L,
                stopVerified = status.stopVerified,
            )
            _activeTestEvidence.update { existing -> (existing + event).takeLast(2_000) }
            sessionRecorder.recordActiveTestEvidence(
                evidenceId = event.evidenceId,
                testId = event.testId,
                phase = event.phase.name,
                message = event.message,
                stopVerified = event.stopVerified,
            )
        }
    }
    private var activeTestJob: Job? = null

    private var consecutiveErrors = 0
    private var isSelfHealing = false
    @Volatile private var lastLiveDataUpdateMs = 0L
    @Volatile private var lastRecoveryAttemptMs = 0L
    private var recoveryFailureCount = 0

    private val _isUdsSessionActive = MutableStateFlow(false)
    val isUdsSessionActive: StateFlow<Boolean> = _isUdsSessionActive.asStateFlow()

    private val _allDetectedDtcs = MutableStateFlow<Set<String>>(emptySet())
    val allDetectedDtcs: StateFlow<Set<String>> = _allDetectedDtcs.asStateFlow()

    private val _lastDtcScanReport = MutableStateFlow<DtcScanReport?>(null)
    val lastDtcScanReport: StateFlow<DtcScanReport?> = _lastDtcScanReport.asStateFlow()
    private val _diagnosticScanEvents = MutableSharedFlow<DiagnosticScanEvent>(extraBufferCapacity = 128)
    val diagnosticScanEvents: SharedFlow<DiagnosticScanEvent> = _diagnosticScanEvents.asSharedFlow()
    @Volatile
    private var diagnosticScanCancellationRequested = false

    fun cancelDiagnosticScan() {
        if (physicalBusActor.currentOwner == PhysicalBusOwner.DIAGNOSTIC_SCAN) {
            diagnosticScanCancellationRequested = true
            _statusMessage.value = "Deteniendo escaneo de forma segura; conservando evidencia parcial..."
        }
    }

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
    private val doIpSourceLogicalAddress: Int = 0x0E00
    private var doIpTargetLogicalAddress: Int = 0x1000
    private val bluetoothMacRegex = Regex("(?i)^([0-9A-F]{2}:){5}[0-9A-F]{2}$")

    private val transportLifecycleMutex = Mutex()
    private val transportGenerationId = java.util.concurrent.atomic.AtomicLong(0L)

    suspend fun setTargetAddressSequentially(address: String) {
        val normalizedAddress = address.trim()
        this.targetAddress = normalizedAddress
        val currentGen = transportGenerationId.incrementAndGet()

        transportLifecycleMutex.withLock {
            val oldTransport = transport
            transport = null
            if (oldTransport != null) {
                runCatching { oldTransport.disconnect() }
            }

            val isBleAddress = normalizedAddress.startsWith("ble://", ignoreCase = true)
            val bleMac = normalizedAddress.substringAfter("://", missingDelimiterValue = "")
            val isBluetoothMac = bluetoothMacRegex.matches(normalizedAddress)
            val networkAddress = normalizedAddress.removePrefix("tcp://")
            isDoIpMode = !isBluetoothMac && !isBleAddress && networkAddress.endsWith(":13400")

            val newTransport: TransportInterface? = when {
                normalizedAddress == "SIMULATOR" -> com.elysium369.meet.core.transport.SimulatedTransport()
                isBleAddress && bluetoothAdapter != null && bluetoothMacRegex.matches(bleMac) -> {
                    val device = bluetoothAdapter.getRemoteDevice(bleMac)
                    BleTransport(context, device)
                }
                !isBluetoothMac && (networkAddress.contains(".") || networkAddress.contains(":")) -> {
                    val separatorIndex = networkAddress.lastIndexOf(':').takeIf { it > 0 }
                    val ip = separatorIndex?.let { networkAddress.substring(0, it) } ?: networkAddress
                    val port = separatorIndex
                        ?.let { networkAddress.substring(it + 1).toIntOrNull() }
                        ?: if (isDoIpMode) 13400 else 35000
                    WifiTransport(ip, port)
                }
                bluetoothAdapter != null -> BtClassicTransport(normalizedAddress, bluetoothAdapter)
                else -> null
            }

            if (currentGen == transportGenerationId.get()) {
                transport = newTransport
            }
        }
    }

    suspend fun setTargetAddress(address: String) = setTargetAddressSequentially(address)

    suspend fun connect() = transportLifecycleMutex.withLock {
        connectOwned()
    }

    private suspend fun connectOwned() {
        if (_state.value == ObdState.CONNECTED || _state.value == ObdState.CONNECTING) return

        val currentGen = transportGenerationId.get()
        val activeTransport = transport
        if (activeTransport == null) {
            _state.value = ObdState.ERROR
            _statusMessage.value = "Selecciona un dispositivo para conectar."
            return
        }

        _state.value = ObdState.CONNECTING
        Log.i(TAG, "═══ OBD CONNECT START (max $MAX_CONNECT_ATTEMPTS attempts, generation $currentGen) ═══")
        val t0 = System.currentTimeMillis()

        var lastException: Exception? = null

        for (attempt in 1..MAX_CONNECT_ATTEMPTS) {
            if (currentGen != transportGenerationId.get()) {
                Log.w(TAG, "Transport target changed during connect loop, aborting attempt for stale generation $currentGen")
                return
            }
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

                if (currentGen != transportGenerationId.get() || transport !== activeTransport) {
                    Log.w(TAG, "Discarding stale connect success for generation $currentGen")
                    runCatching { activeTransport.disconnect() }
                    return
                }

                // Reset smoother on new successful connection
                sensorSmoother.resetAll()

                // 3. SUCCESS — Connection fully established
                _state.value = ObdState.CONNECTED
                _statusMessage.value = "Enlace Crítico Sincronizado: $adapterVersion"
                isRunning = true
                consecutiveErrors = 0
                val connectedAt = System.currentTimeMillis()
                lastHeartbeatTime = connectedAt
                lastLiveDataUpdateMs = connectedAt
                recoveryFailureCount = 0
                Log.i(TAG, "═══ OBD CONNECT SUCCESS ═══ Attempt=$attempt | Total: ${System.currentTimeMillis()-t0}ms | Adapter=$adapterVersion | Protocol=$detectedProtocol")

                scope.launch {
                    currentVanguardSessionId = runCatching {
                        sessionRecorder.startSession(
                            ObdSessionStartContext(
                                appVersion = BuildConfig.VERSION_NAME,
                                vinHash = privacyGuard.vinPseudonym(_vin.value),
                                adapterName = targetAddress?.let { if (bluetoothMacRegex.matches(it)) "Bluetooth OBD" else it },
                                adapterMacHash = targetAddress?.takeIf { bluetoothMacRegex.matches(it) }?.let { privacyGuard.vinPseudonym(it) },
                                adapterFirmware = adapterVersion.ifBlank { null },
                                protocolDetected = detectedProtocol.ifBlank { null },
                                consentGranted = privacyGuard.allowsRemoteDiagnostics(context),
                                startedAtMs = connectedAt
                            )
                        )
                    }.getOrElse { e ->
                        Log.w(TAG, "Vanguard session recorder start failed: ${e.message}")
                        null
                    }
                }

                privacyGuard.remotePayload(
                    context,
                    listOf(
                        com.elysium369.meet.core.vanguard.ClassifiedTelemetryField("adapterType", adapterVersion, com.elysium369.meet.core.vanguard.TelemetryFieldClassification.DEVICE_ID),
                        com.elysium369.meet.core.vanguard.ClassifiedTelemetryField("notes", "SUCCESS (attempt $attempt/$MAX_CONNECT_ATTEMPTS)", com.elysium369.meet.core.vanguard.TelemetryFieldClassification.PUBLIC),
                        com.elysium369.meet.core.vanguard.ClassifiedTelemetryField("protocol", detectedProtocol, com.elysium369.meet.core.vanguard.TelemetryFieldClassification.PUBLIC),
                    ),
                )?.let { payload ->
                    scope.launch {
                        com.elysium369.meet.data.remote.CloudSyncRepository.logSessionTelemetry(
                            userId = "anonymous_diagnostics",
                            adapterType = payload.fields["adapterType"].orEmpty(),
                            notes = payload.fields["notes"].orEmpty(),
                            protocol = payload.fields["protocol"].orEmpty(),
                            isSuccess = true
                        )
                    }
                }

                startQueueProcessor()
                startHeartbeatMonitor()
                keepAliveManager.start(scope)

                // ── AUTO-IDENTIFICATION: VIN + Calibration ID + ECU ──
                _statusMessage.value = "Identificando vehículo..."
                scope.launch {
                    try {
                        readVinFromVehicle()
                        fetchCalibrationId()
                        fetchEcuName()
                        _statusMessage.value = if (_vin.value != null && _vin.value != "N/A") {
                            "Vehículo identificado ✓ VIN capturado"
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

                // Only disconnect physical transport if the physical transport itself dropped or failed.
                // If Bluetooth is healthy and only ECU/Protocol handshake failed, preserve physical link.
                val isPhysicalFailure = !activeTransport.isConnected || e is com.elysium369.meet.core.transport.TransportRemoteClosed || (e is java.io.IOException && !msg.contains("ECU"))
                if (isPhysicalFailure) {
                    try { activeTransport.disconnect() } catch (_: Exception) {}
                } else {
                    try { activeTransport.drain() } catch (_: Exception) {}
                }

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

        privacyGuard.remotePayload(
            context,
            listOf(
                com.elysium369.meet.core.vanguard.ClassifiedTelemetryField("adapterType", adapterVersion.ifBlank { "Unknown" }, com.elysium369.meet.core.vanguard.TelemetryFieldClassification.DEVICE_ID),
                com.elysium369.meet.core.vanguard.ClassifiedTelemetryField("notes", "FAILED_CONNECTION ($MAX_CONNECT_ATTEMPTS attempts): $msg", com.elysium369.meet.core.vanguard.TelemetryFieldClassification.DIAGNOSTIC_RAW),
                com.elysium369.meet.core.vanguard.ClassifiedTelemetryField("protocol", detectedProtocol, com.elysium369.meet.core.vanguard.TelemetryFieldClassification.PUBLIC),
            ),
        )?.let { payload ->
            scope.launch {
                com.elysium369.meet.data.remote.CloudSyncRepository.logSessionTelemetry(
                    userId = "anonymous_diagnostics",
                    adapterType = payload.fields["adapterType"].orEmpty(),
                    notes = payload.fields["notes"].orEmpty(),
                    protocol = payload.fields["protocol"].orEmpty(),
                    isSuccess = false
                )
            }
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
            _statusMessage.value = "Modo Alta Velocidad Activado"
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
        if (lastLiveDataUpdateMs == 0L) lastLiveDataUpdateMs = System.currentTimeMillis()
        pollingJob = scope.launch {
            var supportedPids: Set<Int> = emptySet()
            var supportDiscoveryComplete = false
            val supportedPidsDeferred = async(Dispatchers.IO) { detectSupportedPids() }

            // Priority 1: High-frequency PIDs (RPM, Speed, Throttle)
            val baseHighPriority = listOf("0C", "0D", "11") // PID codes without '01'

            var cycleCount = 0

            while (isRunning && isActive) {
                if (isPollingPaused) {
                    delay(100)
                    continue
                }
                val cycleStartTime = System.currentTimeMillis()

                if (!supportDiscoveryComplete && supportedPidsDeferred.isCompleted) {
                    supportedPids = runCatching { supportedPidsDeferred.await() }.getOrDefault(emptySet())
                    supportDiscoveryComplete = true
                    Log.i(TAG, "Supported PID discovery complete: ${supportedPids.size} PIDs")
                }

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
                            !supportDiscoveryComplete || supportedPids.contains(pidInt)
                        }

                    val normalPriorityPids = dashboardPids.map { it.removePrefix("01") }
                        .filter { pid ->
                            val pidInt = pid.toIntOrNull(16) ?: return@filter true
                            !supportDiscoveryComplete || supportedPids.contains(pidInt)
                        }
                        .filter { pid ->
                            !highPriorityPids.contains(pid)
                        }

                    // 1. Poll High Priority (Every Cycle) - Use Multi-PID request if on CAN
                    if (detectedProtocol.contains("CAN", ignoreCase = true) && highPriorityPids.size > 1) {
                        highPriorityPids.chunked(6).forEach { chunk ->
                            pollMultiPidBatch(chunk)
                        }
                    } else {
                        pollBatch(highPriorityPids.map { "01$it" })
                    }

                    // 2. Poll Normal Priority (Every 3 cycles) - Chunked to prevent truncation
                    if (cycleCount % 3 == 0) {
                        if (detectedProtocol.contains("CAN", ignoreCase = true) && normalPriorityPids.size > 1) {
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

                // Dynamic Adaptive Auto-Pacing based on physical bus speed (CAN 500k vs K-Line/J1850 10.4k)
                val supportedPidKeys = supportedPids.map { "01${String.format("%02X", it)}" }.toSet()
                val adapterProfile = adapterQualityProfiler.profile(
                    adapterName = targetAddress,
                    firmware = adapterVersion,
                    transport = transport?.javaClass?.simpleName,
                    qos = _qosMetrics.value,
                    commandSupport = setOf("ATH1", "ATSP")
                )
                val pollingPlan = pollingScheduler.buildPlan(
                    supportedPids = supportedPidKeys,
                    pinnedPids = _pinnedPids.value,
                    adapterQuality = adapterProfile,
                    qos = _qosMetrics.value
                )
                val isSlowPhysicalBus = detectedProtocol.contains("ISO 9141", ignoreCase = true) ||
                    detectedProtocol.contains("ISO9141", ignoreCase = true) ||
                    detectedProtocol.contains("KWP", ignoreCase = true) ||
                    detectedProtocol.contains("J1850", ignoreCase = true)
                val minDelay = when {
                    isSlowPhysicalBus -> 65L // Safe cadence for slow UART/K-Line buffers
                    _highSpeedMode.value && (pollingPlan.highPerformanceMode || isSTN) -> 12L // High-speed CAN 500k burst
                    else -> 40L
                }
                val maxDelay = if (isSlowPhysicalBus) 350L else 200L
                val targetDelay = (1000f / pollingPlan.commandsPerSecondLimit)
                    .toLong()
                    .coerceIn(minDelay, maxDelay)
                if (targetDelay > 0) delay(timeMillis = targetDelay)
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
                val response = withTimeoutOrNull(800) { result.await() }
                if (response == null) {
                    markSensorState(pid, SensorValueState.Timeout)
                    recordClassifiedFailure(pid, null, timeoutMs = 800L, latencyMs = 800L)
                    continue
                }
                val parsed = parsePidResponse(pid, response)
                if (parsed != null) {
                    updateLiveData(pid, parsed)
                } else {
                    val state = stateForFailedResponse(response)
                    markSensorState(pid, state, rawValue = response)
                    recordClassifiedFailure(pid, response, timeoutMs = 800L, latencyMs = null)
                }
            } catch (e: Exception) {
                markSensorState(pid, SensorValueState.AdapterError)
                recordClassifiedFailure(pid, e.message, timeoutMs = 800L, latencyMs = null)
            }
        }
    }

    private suspend fun pollCustomBatch() {
        for (cp in customPidsToPoll) {
            if (!isRunning) return
            try {
                val result = CompletableDeferred<String>()
                val command = cp.mode + cp.pid
                commandQueue.enqueue(ObdCommand(command, 0, { result.complete(it) }, { result.complete("") }))
                val response = withTimeoutOrNull(2000) { result.await() }
                if (response == null) {
                    markSensorState(cp.id.toString(), SensorValueState.Timeout, source = SensorSource.OEM_OBD)
                    recordClassifiedFailure(command, null, timeoutMs = 2000L, latencyMs = 2000L)
                    continue
                }

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
                        val value = FormulaEvaluator.evaluateOrNull(cp.formula, bytes)
                        if (value != null) {
                            updateLiveData(cp.id.toString(), value)
                        } else {
                            markSensorState(cp.id.toString(), SensorValueState.InvalidFormula(rawValue = response), source = SensorSource.OEM_OBD)
                            recordClassifiedFailure(command, response, timeoutMs = 2000L, latencyMs = null)
                        }
                    }
                } else {
                    val state = stateForFailedResponse(response)
                    markSensorState(cp.id.toString(), state, rawValue = response, source = SensorSource.OEM_OBD)
                    recordClassifiedFailure(command, response, timeoutMs = 2000L, latencyMs = null)
                }
            } catch (e: Exception) {
                markSensorState(cp.id.toString(), SensorValueState.AdapterError, source = SensorSource.OEM_OBD)
                recordClassifiedFailure(cp.mode + cp.pid, e.message, timeoutMs = 2000L, latencyMs = null)
            }
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
        lastLiveDataUpdateMs = System.currentTimeMillis()
        // 1. Apply user/auto calibration offset
        val normalizedPid = pid.uppercase().replace(" ", "")
        val corePid = normalizedPid.removePrefix("01")
        val offset = calibrationOffsets.value[pid]
            ?: calibrationOffsets.value[normalizedPid]
            ?: calibrationOffsets.value[corePid]
            ?: 0f
        val calibratedValue = value + offset

        // 2. Apply smoothing and outlier rejection
        // The PID to smooth is usually the hex code.
        val smoothedValue = if (corePid == "42" || corePid == "AT RV" || corePid == "ATRV") {
            calibratedValue // Bypass smoothing for voltage to allow oscilloscope raw reading
        } else {
            sensorSmoother.smooth(corePid, calibratedValue)
        }

        val current = _liveData.value.toMutableMap()
        putLiveDataAliases(current, pid, smoothedValue)
        val stateCurrent = _liveSensorStates.value.toMutableMap()
        val unit = unitForPid(pid)
        val supportedState = SensorValueState.Supported(smoothedValue, unit)
        putSensorStateAliases(stateCurrent, pid, supportedState)
        publishTelemetrySample(pid, supportedState, rawValue = null, valueOverride = smoothedValue)
        recordSensorStateIfDue(pid, null, supportedState, SensorSource.STANDARD_OBD)

        // ── Compute derived/calculated sensors ──
        computeCalculatedSensors(current)
        val derivedMetrics = derivedMetricsEngine.calculateAll(stateCurrent, fuelPricePerLiter)
        derivedMetrics.forEach { metric ->
            val state = derivedMetricsEngine.stateFor(metric)
            putSensorStateAliases(stateCurrent, metric.id, state)
            if (metric.value != null && state.isDisplayable) {
                current[metric.id] = metric.value
            } else {
                current.remove(metric.id)
            }
        }
        recordDerivedMetricsIfDue(derivedMetrics)

        _liveSensorStates.value = stateCurrent
        _liveData.value = current
    }

    private fun putSensorStateAliases(
        data: MutableMap<String, SensorValueState>,
        pid: String,
        state: SensorValueState
    ) {
        val rawKey = pid.trim()
        val compactKey = rawKey.uppercase().replace(" ", "")
        val coreKey = compactKey.removePrefix("01")
        data[rawKey] = state
        data[compactKey] = state
        if (coreKey.isNotBlank()) data[coreKey] = state
        if (coreKey.length == 2) data["01$coreKey"] = state

        when (coreKey) {
            "0C" -> {
                data["RPM"] = state
                data["rpm"] = state
            }
            "0D" -> {
                data["SPEED"] = state
                data["speed"] = state
                data["VELOCIDAD"] = state
            }
            "05" -> {
                data["COOLANT"] = state
                data["coolant"] = state
                data["ECT"] = state
            }
            "04" -> data["ENGINE_LOAD"] = state
            "0B" -> {
                data["MAP"] = state
                data["map"] = state
            }
            "10" -> {
                data["MAF"] = state
                data["maf"] = state
            }
            "11" -> {
                data["THROTTLE"] = state
                data["throttle"] = state
            }
            "0F" -> data["IAT"] = state
            "0E" -> data["TIMING_ADVANCE"] = state
            "2F" -> data["FUEL_LEVEL"] = state
            "42" -> {
                data["VOLTAGE"] = state
                data["voltage"] = state
                data["CTRL_VOLTAGE"] = state
            }
        }
        if (compactKey == "ATRV") {
            data["AT RV"] = state
            data["ELM_VOLTAGE"] = state
            data.putIfAbsent("VOLTAGE", state)
            data.putIfAbsent("voltage", state)
        }
    }

    private fun markSensorState(
        pid: String,
        state: SensorValueState,
        rawValue: String? = null,
        source: SensorSource = SensorSource.STANDARD_OBD
    ) {
        val current = _liveSensorStates.value.toMutableMap()
        putSensorStateAliases(current, pid, state)
        _liveSensorStates.value = current
        publishTelemetrySample(pid, state, rawValue = rawValue)
        recordSensorStateIfDue(pid, rawValue, state, source)
    }

    private fun publishTelemetrySample(
        pid: String,
        state: SensorValueState,
        rawValue: String?,
        valueOverride: Float? = null
    ) {
        val normalizedPid = pid.uppercase().replace(" ", "")
        val corePid = normalizedPid.removePrefix("01")
        val key = if (corePid.length == 2) "01$corePid" else normalizedPid
        val quality = telemetryQualityFor(state)
        val value = when {
            valueOverride != null -> valueOverride.toDouble()
            state is SensorValueState.Supported -> state.value.toDouble()
            else -> null
        }
        val sample = TelemetrySample(
            pid = key,
            name = PidRegistry.getPid("01", corePid)?.name ?: pid,
            value = value,
            unit = unitForPid(pid).orEmpty(),
            timestampMonotonicMs = System.nanoTime() / 1_000_000L,
            source = if (quality == TelemetryQuality.VALID || quality == TelemetryQuality.OUT_OF_RANGE) {
                ObdDataSource.REAL_OBD
            } else {
                ObdDataSource.NO_REAL_OBD
            },
            quality = quality,
            latencyMs = _qosMetrics.value.latencyMs.toLong(),
            rawResponse = rawValue.orEmpty()
        )
        val current = _telemetrySamples.value.toMutableMap()
        putTelemetrySampleAliases(current, pid, sample)
        _telemetrySamples.value = current
    }

    private fun putTelemetrySampleAliases(
        data: MutableMap<String, TelemetrySample>,
        pid: String,
        sample: TelemetrySample
    ) {
        val rawKey = pid.trim()
        val compactKey = rawKey.uppercase().replace(" ", "")
        val coreKey = compactKey.removePrefix("01")
        data[rawKey] = sample
        data[compactKey] = sample
        if (coreKey.isNotBlank()) data[coreKey] = sample
        if (coreKey.length == 2) data["01$coreKey"] = sample

        when (coreKey) {
            "0C" -> {
                data["RPM"] = sample
                data["rpm"] = sample
            }
            "0D" -> {
                data["SPEED"] = sample
                data["speed"] = sample
                data["VELOCIDAD"] = sample
            }
            "05" -> {
                data["COOLANT"] = sample
                data["coolant"] = sample
                data["ECT"] = sample
            }
            "04" -> data["ENGINE_LOAD"] = sample
            "0B" -> {
                data["MAP"] = sample
                data["map"] = sample
            }
            "10" -> {
                data["MAF"] = sample
                data["maf"] = sample
            }
            "11" -> {
                data["THROTTLE"] = sample
                data["throttle"] = sample
            }
            "0F" -> data["IAT"] = sample
            "0E" -> data["TIMING_ADVANCE"] = sample
            "2F" -> data["FUEL_LEVEL"] = sample
            "42" -> {
                data["VOLTAGE"] = sample
                data["voltage"] = sample
                data["CTRL_VOLTAGE"] = sample
            }
        }
        if (compactKey == "ATRV") {
            data["AT RV"] = sample
            data["ELM_VOLTAGE"] = sample
            data.putIfAbsent("VOLTAGE", sample)
            data.putIfAbsent("voltage", sample)
        }
    }

    private fun telemetryQualityFor(state: SensorValueState): TelemetryQuality = when (state) {
        is SensorValueState.Supported -> TelemetryQuality.VALID
        SensorValueState.Timeout -> TelemetryQuality.TIMEOUT
        SensorValueState.AdapterError,
        SensorValueState.EcuNoResponse,
        SensorValueState.NotAvailable -> TelemetryQuality.TIMEOUT
        is SensorValueState.Unsupported -> TelemetryQuality.UNSUPPORTED
        is SensorValueState.InvalidFormula -> TelemetryQuality.PARSE_ERROR
        SensorValueState.Pending -> TelemetryQuality.STALE
    }

    private fun stateForFailedResponse(raw: String): SensorValueState {
        val normalized = raw.uppercase().replace(" ", "")
        return when {
            normalized.isBlank() -> SensorValueState.EcuNoResponse
            normalized.contains("NODATA") || normalized.contains("NO DATA") -> SensorValueState.Unsupported()
            normalized.contains("UNABLE") -> SensorValueState.EcuNoResponse
            normalized.contains("ERROR") || normalized.contains("CANERROR") -> SensorValueState.AdapterError
            normalized.contains("?") -> SensorValueState.Unsupported()
            normalized.contains("7F") -> SensorValueState.NotAvailable
            else -> SensorValueState.NotAvailable
        }
    }

    private fun recordClassifiedFailure(
        command: String,
        rawResponse: String?,
        timeoutMs: Long?,
        latencyMs: Long?
    ) {
        val sessionId = currentVanguardSessionId ?: return
        val now = System.currentTimeMillis()
        val context = EcuFailureContext(
            eventType = "PID_READ_FAILURE",
            sessionId = sessionId,
            adapterType = if (isCloneAdapter) "ELM327_CLONE" else "ELM327_OR_STN",
            adapterFirmware = adapterVersion.ifBlank { null },
            transport = transport?.javaClass?.simpleName,
            protocolSelected = detectedProtocol.ifBlank { null },
            commandSent = command,
            rawResponse = rawResponse,
            normalizedResponse = rawResponse?.uppercase()?.replace(Regex("\\s+"), " ")?.trim(),
            timeoutMs = timeoutMs,
            latencyMs = latencyMs,
            retryCount = 0,
            serviceMode = command.take(2).takeIf { it.matches(Regex("[0-9A-Fa-f]{2}")) },
            pid = command.drop(2).takeIf { it.isNotBlank() },
            negativeResponseCode = rawResponse?.let { negativeResponseCode(it) },
            batteryVoltage = _liveData.value["VOLTAGE"] ?: _liveData.value["0142"],
            engineRunningState = _liveData.value["RPM"]?.let { if (it > 0f) "RUNNING" else "STOPPED" },
            vehicle = VehicleProfileFingerprint(vinHash = privacyGuard.vinHashOnly(_vin.value)),
            appVersion = BuildConfig.VERSION_NAME,
            androidVersion = android.os.Build.VERSION.RELEASE,
            deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim(),
            timestampMs = now
        )
        val classified = ecuFailureIntelligence.classify(context)
        scope.launch {
            runCatching { sessionRecorder.recordFailure(classified) }
                .onFailure { Log.w(TAG, "Vanguard failure record failed: ${it.message}") }
        }
    }

    private fun recordCommandLog(
        command: String,
        rawResponse: String?,
        success: Boolean,
        latencyMs: Long?,
        retryCount: Int,
        errorType: EcuFailureType? = null
    ) {
        val sessionId = currentVanguardSessionId ?: return
        scope.launch {
            runCatching {
                sessionRecorder.recordCommand(
                    sessionId = sessionId,
                    command = command,
                    rawResponse = rawResponse,
                    success = success,
                    latencyMs = latencyMs,
                    timeoutMs = if (success) null else 2000L,
                    retryCount = retryCount,
                    errorType = errorType
                )
            }.onFailure { Log.w(TAG, "Vanguard command record failed: ${it.message}") }
        }
    }

    private fun negativeResponseCode(raw: String): String? {
        val clean = raw.uppercase().replace(Regex("[^0-9A-F]"), "")
        val idx = clean.indexOf("7F")
        return if (idx >= 0 && idx + 6 <= clean.length) clean.substring(idx + 4, idx + 6) else null
    }

    private fun unitForPid(pid: String): String? {
        val normalized = pid.uppercase().replace(" ", "")
        val core = normalized.removePrefix("01")
        return PidRegistry.getPid("01", core)?.unit
    }

    private fun recordSensorStateIfDue(
        pid: String,
        rawValue: String?,
        state: SensorValueState,
        source: SensorSource
    ) {
        val sessionId = currentVanguardSessionId ?: return
        val now = System.currentTimeMillis()
        val key = pid.uppercase().replace(" ", "")
        val last = lastSensorRecordMs[key] ?: 0L
        if (now - last < 1000L && state is SensorValueState.Supported) return
        lastSensorRecordMs[key] = now
        scope.launch {
            runCatching {
                sessionRecorder.recordSensorState(
                    sessionId = sessionId,
                    vehicleId = null,
                    pid = pid,
                    label = PidRegistry.getPid("01", pid.uppercase().removePrefix("01"))?.name,
                    state = state,
                    source = source,
                    rawValue = rawValue,
                    timestampMs = now
                )
            }.onFailure { Log.w(TAG, "Vanguard sensor record failed: ${it.message}") }
        }
    }

    private fun recordDerivedMetricsIfDue(metrics: List<com.elysium369.meet.core.vanguard.DerivedMetric>) {
        val sessionId = currentVanguardSessionId ?: return
        val now = System.currentTimeMillis()
        val last = lastSensorRecordMs["__derived_metrics"] ?: 0L
        if (now - last < 1500L) return
        lastSensorRecordMs["__derived_metrics"] = now
        scope.launch {
            runCatching {
                sessionRecorder.recordDerivedMetrics(sessionId, null, metrics, now)
            }.onFailure { Log.w(TAG, "Vanguard derived metrics record failed: ${it.message}") }
        }
    }

    private fun putLiveDataAliases(data: MutableMap<String, Float>, pid: String, value: Float) {
        val rawKey = pid.trim()
        val compactKey = rawKey.uppercase().replace(" ", "")
        val coreKey = compactKey.removePrefix("01")

        data[rawKey] = value
        data[compactKey] = value
        if (coreKey.isNotBlank()) data[coreKey] = value
        if (coreKey.length == 2) data["01$coreKey"] = value

        when (coreKey) {
            "0C" -> {
                data["RPM"] = value
                data["rpm"] = value
            }
            "0D" -> {
                data["SPEED"] = value
                data["speed"] = value
                data["VELOCIDAD"] = value
            }
            "05" -> {
                data["COOLANT"] = value
                data["coolant"] = value
                data["ECT"] = value
            }
            "04" -> data["ENGINE_LOAD"] = value
            "0B" -> {
                data["MAP"] = value
                data["map"] = value
            }
            "10" -> {
                data["MAF"] = value
                data["maf"] = value
            }
            "11" -> {
                data["THROTTLE"] = value
                data["throttle"] = value
            }
            "0F" -> data["IAT"] = value
            "0E" -> data["TIMING_ADVANCE"] = value
            "2F" -> data["FUEL_LEVEL"] = value
            "42" -> {
                data["VOLTAGE"] = value
                data["voltage"] = value
                data["CTRL_VOLTAGE"] = value
            }
        }

        if (compactKey == "ATRV") {
            data["AT RV"] = value
            data["ELM_VOLTAGE"] = value
            data["VOLTAGE"] = data["VOLTAGE"] ?: value
            data["voltage"] = data["voltage"] ?: value
        }
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
    @Deprecated(
        message = "Use DiagnosticAcquisitionEngine; legacy topology probing changes sessions and duplicates DTC parsing.",
        level = DeprecationLevel.ERROR,
    )
    suspend fun scanNetworkTopology(): Unit =
        error("Legacy topology scanner retired: use canonical diagnostic acquisition")

    private suspend fun scanNetworkTopologyOwned() {
        if (_state.value != ObdState.CONNECTED) {
            _statusMessage.value = "Error: El escaneo de topología requiere conexión activa."
            return
        }
        _isScanningTopology.value = true
        val discovered = mutableListOf<NetworkModule>()
        _networkTopology.value = emptyList()

        if (isDoIpMode) {
            try {
                _statusMessage.value = "Sondeando gateway DoIP ISO 13400..."
                val startTime = System.currentTimeMillis()
                val sessionResp = sendRawCommand("1001")
                val isAlive = sessionResp.isNotBlank() &&
                        !sessionResp.contains("NO DATA", ignoreCase = true) &&
                        !sessionResp.contains("UNABLE", ignoreCase = true) &&
                        !sessionResp.contains("ERROR", ignoreCase = true) &&
                        (sessionResp.contains("50 01") || sessionResp.contains("5001") ||
                            sessionResp.contains("50 03") || sessionResp.contains("5003"))

                if (isAlive) {
                    var vinEcu: String? = null
                    val udsServices = mutableListOf("10")
                    runCatching {
                        val vinResp = sendRawCommand("22F190")
                        if (vinResp.contains("62") && vinResp.contains("F190", ignoreCase = true)) {
                            udsServices.add("22")
                            val hexPart = vinResp.replace(" ", "")
                                .substringAfter("62F190", "")
                                .substringAfter("62f190", "")
                                .take(34)
                            if (hexPart.length >= 34) {
                                vinEcu = hexPart.chunked(2)
                                    .mapNotNull { it.toIntOrNull(16)?.toChar() }
                                    .joinToString("")
                                    .filter { it.isLetterOrDigit() }
                            }
                        }
                    }

                    _networkTopology.value = listOf(
                        NetworkModule(
                            id = "1000",
                            name = "DoIP Gateway / Diagnostic Server",
                            isAlive = true,
                            networkType = NetworkType.ETHERNET,
                            addressing = AddressingType.UNKNOWN,
                            latencyMs = System.currentTimeMillis() - startTime,
                            responseId = "1000",
                            vinFromEcu = vinEcu,
                            supportsUds = true,
                            supportedUdsServices = udsServices,
                            busSpeed = "Ethernet / TCP 13400",
                            protocolDetected = ObdProtocol.DOIP_ISO13400.displayName
                        )
                    )
                    _statusMessage.value = "Topología DoIP real: gateway respondió por UDS."
                } else {
                    _statusMessage.value = "Topología DoIP: el gateway no respondió a sesión UDS."
                }
            } catch (e: CancellationException) {
                _statusMessage.value = "Escaneo cancelado por el usuario."
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "DoIP topology scan failed", e)
                _statusMessage.value = "Error en topología DoIP: ${e.message}"
            } finally {
                _isScanningTopology.value = false
            }
            return
        }

        // ── Detect active protocol & bus speed before scanning ──
        val protocolString = runCatching { sendRawCommand("AT DP").trim() }.getOrDefault("Unknown")
        val busSpeedStr = when {
            protocolString.contains("500", ignoreCase = true) -> "500 kbps"
            protocolString.contains("250", ignoreCase = true) -> "250 kbps"
            protocolString.contains("125", ignoreCase = true) -> "125 kbps"
            protocolString.contains("CAN", ignoreCase = true) -> "500 kbps (CAN)"
            protocolString.contains("9141", ignoreCase = true) -> "10.4 kbps (ISO 9141)"
            protocolString.contains("KWP", ignoreCase = true) -> "10.4 kbps (KWP2000)"
            else -> null
        }

        // ── Determine if the protocol uses 29-bit addressing ──
        val is29bit = protocolString.contains("29", ignoreCase = true) ||
                protocolString.contains("EXT", ignoreCase = true)
        val addressing = if (is29bit) AddressingType.CAN_29BIT else AddressingType.CAN_11BIT

        // ── Standard 11-bit CAN IDs for physical ECU addressing ──
        // ISO 15765-4: Request IDs 7E0-7E7, Response IDs 7E8-7EF
        val targetNodes = listOf(
            Triple("7E0", "Engine Control Module (ECM)", NetworkType.CAN_HS),
            Triple("7E1", "Transmission Control Module (TCM)", NetworkType.CAN_HS),
            Triple("7E2", "ABS / Stability Control (ABS/ESC)", NetworkType.CAN_HS),
            Triple("7E3", "Supplemental Restraint System (SRS/Airbag)", NetworkType.CAN_LS),
            Triple("7E4", "Body Control Module (BCM)", NetworkType.CAN_LS),
            Triple("7E5", "Instrument Cluster (IPC)", NetworkType.CAN_LS),
            Triple("7E6", "HVAC / Climate Control Module", NetworkType.CAN_LS),
            Triple("7E7", "Power Steering Control Module (EPS)", NetworkType.CAN_HS),
            // Extended range — common OEM-specific ECU addresses
            Triple("7A0", "Park Assist / ADAS Module", NetworkType.CAN_HS),
            Triple("7A1", "Tire Pressure Monitor (TPMS)", NetworkType.CAN_LS),
            Triple("7B0", "Telematics / Gateway Module", NetworkType.CAN_HS),
            Triple("7C0", "Hybrid/EV Battery Management (BMS)", NetworkType.CAN_HS),
            Triple("7C4", "Electric Motor Controller (MCU)", NetworkType.CAN_HS),
            Triple("7D0", "Infotainment / Head Unit (IVI)", NetworkType.CAN_LS),
            Triple("7D4", "Keyless Entry / Immobilizer", NetworkType.CAN_LS)
        )

        try {
            for ((id, name, netType) in targetNodes) {
                currentCoroutineContext().ensureActive()
                _statusMessage.value = "Escaneando Nodo: $name ($id)..."

                val startTime = System.currentTimeMillis()
                var success = false
                var dtcs = emptyList<String>()
                var responseId = ""
                val pidList = mutableListOf<String>()

                // UDS identification fields
                var ecuPart: String? = null
                var ecuSw: String? = null
                var ecuHw: String? = null
                var ecuSerial: String? = null
                var vinEcu: String? = null
                var hasUds = false
                val udsServices = mutableListOf<String>()

                try {
                    withTimeoutOrNull(3000L) {
                        // ── Set physical addressing to this ECU ──
                        sendRawCommand("AT SH $id")
                        val computedResponse = id.toIntOrNull(16)?.let { reqHex ->
                            String.format("%03X", reqHex + 8)
                        } ?: ""
                        if (computedResponse.isNotBlank()) {
                            sendRawCommand("AT CRA $computedResponse")
                        }
                        responseId = computedResponse

                        // ── Phase 1: OBD-II PID 0100 — Basic alive check ──
                        val resp = sendRawCommand("0100")
                        success = resp.isNotBlank() &&
                                !resp.contains("NO DATA", ignoreCase = true) &&
                                !resp.contains("UNABLE", ignoreCase = true) &&
                                !resp.contains("ERROR", ignoreCase = true) &&
                                (resp.contains("41 00") || resp.contains("4100"))

                        if (success) {
                            pidList.add("0100")

                            // ── Phase 2: PID range discovery (0120, 0140, 0160) ──
                            for (pidGroup in listOf("0120", "0140", "0160")) {
                                try {
                                    val pidResp = sendRawCommand(pidGroup)
                                    if (pidResp.isNotBlank() &&
                                        !pidResp.contains("NO DATA", ignoreCase = true) &&
                                        pidResp.contains("41")) {
                                        pidList.add(pidGroup)
                                    }
                                } catch (_: Exception) { /* Non-critical */ }
                            }

                            // ── Phase 3: Read DTCs from this ECU ──
                            try {
                                val dtcResp = sendRawCommand("03")
                                dtcs = DtcDecoder.decode(dtcResp, "03")
                            } catch (_: Exception) { /* Non-critical */ }
                        }

                        // ── Phase 4: UDS Identification (ISO 14229) ──
                        // Some safety/body/ADAS ECUs do not answer generic OBD-II PID 0100,
                        // but they are real topology nodes if they answer a physical UDS session.
                        try {
                            var sessionResp = sendRawCommand("1003")
                            if (sessionResp.isBlank() ||
                                sessionResp.contains("NO DATA", ignoreCase = true) ||
                                sessionResp.contains("7F10", ignoreCase = true)
                            ) {
                                sessionResp = sendRawCommand("1001")
                            }
                            if (sessionResp.isNotBlank() &&
                                !sessionResp.contains("NO DATA", ignoreCase = true) &&
                                !sessionResp.contains("UNABLE", ignoreCase = true) &&
                                !sessionResp.contains("ERROR", ignoreCase = true) &&
                                (sessionResp.contains("50 03") || sessionResp.contains("5003") ||
                                    sessionResp.contains("50 01") || sessionResp.contains("5001"))) {
                                    success = true
                                    hasUds = true
                                    udsServices.add("10")

                                    // Read VIN ($22 F190)
                                    try {
                                        val vinResp = sendRawCommand("22F190")
                                        if (vinResp.contains("62") && vinResp.contains("F190", ignoreCase = true)) {
                                            udsServices.add("22")
                                            val hexPart = vinResp.replace(" ", "")
                                                .substringAfter("62F190", "")
                                                .substringAfter("62f190", "")
                                                .take(34) // 17 chars * 2 hex digits
                                            if (hexPart.length >= 34) {
                                                vinEcu = hexPart.chunked(2)
                                                    .mapNotNull { it.toIntOrNull(16)?.toChar() }
                                                    .joinToString("")
                                                    .filter { it.isLetterOrDigit() }
                                            }
                                        }
                                    } catch (_: Exception) { }

                                    // Read ECU Part Number ($22 F187)
                                    try {
                                        val partResp = sendRawCommand("22F187")
                                        if (partResp.contains("62") && partResp.contains("F187", ignoreCase = true)) {
                                            val hexPart = partResp.replace(" ", "")
                                                .substringAfter("62F187", "")
                                                .substringAfter("62f187", "")
                                                .take(32)
                                            ecuPart = hexPart.chunked(2)
                                                .mapNotNull { it.toIntOrNull(16)?.toChar() }
                                                .joinToString("")
                                                .trim()
                                                .ifBlank { null }
                                        }
                                    } catch (_: Exception) { }

                                    // Read Software Version ($22 F189)
                                    try {
                                        val swResp = sendRawCommand("22F189")
                                        if (swResp.contains("62") && swResp.contains("F189", ignoreCase = true)) {
                                            val hexPart = swResp.replace(" ", "")
                                                .substringAfter("62F189", "")
                                                .substringAfter("62f189", "")
                                                .take(32)
                                            ecuSw = hexPart.chunked(2)
                                                .mapNotNull { it.toIntOrNull(16)?.toChar() }
                                                .joinToString("")
                                                .trim()
                                                .ifBlank { null }
                                        }
                                    } catch (_: Exception) { }

                                    // Read Hardware Version ($22 F191)
                                    try {
                                        val hwResp = sendRawCommand("22F191")
                                        if (hwResp.contains("62") && hwResp.contains("F191", ignoreCase = true)) {
                                            val hexPart = hwResp.replace(" ", "")
                                                .substringAfter("62F191", "")
                                                .substringAfter("62f191", "")
                                                .take(32)
                                            ecuHw = hexPart.chunked(2)
                                                .mapNotNull { it.toIntOrNull(16)?.toChar() }
                                                .joinToString("")
                                                .trim()
                                                .ifBlank { null }
                                        }
                                    } catch (_: Exception) { }

                                    // Read Serial Number ($22 F18C)
                                    try {
                                        val serialResp = sendRawCommand("22F18C")
                                        if (serialResp.contains("62") && serialResp.contains("F18C", ignoreCase = true)) {
                                            val hexPart = serialResp.replace(" ", "")
                                                .substringAfter("62F18C", "")
                                                .substringAfter("62f18c", "")
                                                .take(32)
                                            ecuSerial = hexPart.chunked(2)
                                                .mapNotNull { it.toIntOrNull(16)?.toChar() }
                                                .joinToString("")
                                                .trim()
                                                .ifBlank { null }
                                        }
                                    } catch (_: Exception) { }

                                    // Never probe RoutineControl ($31) or IOControl ($2F)
                                    // during discovery. Those capabilities may only come from
                                    // a source-backed OEM capability pack for this exact ECU.

                                    // Return to default session
                                    runCatching { sendRawCommand("1001") }
                            }
                        } catch (_: Exception) { /* UDS not supported — OK */ }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    success = false
                } finally {
                    runCatching { sendRawCommand("AT CRA") }
                }

                val latency = System.currentTimeMillis() - startTime
                if (success) {
                    discovered.add(
                        NetworkModule(
                            id = id,
                            name = name,
                            isAlive = true,
                            networkType = netType,
                            addressing = addressing,
                            latencyMs = latency,
                            dtcs = dtcs,
                            responseId = responseId,
                            supportedPids = pidList.toList(),
                            ecuPartNumber = ecuPart,
                            ecuSoftwareVersion = ecuSw,
                            ecuHardwareVersion = ecuHw,
                            ecuSerialNumber = ecuSerial,
                            vinFromEcu = vinEcu,
                            supportsUds = hasUds,
                            supportedUdsServices = udsServices.toList(),
                            busSpeed = busSpeedStr,
                            protocolDetected = protocolString
                        )
                    )
                }
            }

            _networkTopology.value = discovered
            val udsCount = discovered.count { it.supportsUds }
            _statusMessage.value = "Topología real: ${discovered.size}/${targetNodes.size} direcciones respondieron, $udsCount con UDS."
        } catch (e: CancellationException) {
            _statusMessage.value = "Escaneo cancelado por el usuario."
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Topology scan failed", e)
            _statusMessage.value = "Error en escaneo de topología: ${e.message}"
        } finally {
            // ── Restore OBD-II functional addressing ──
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
            withExclusivePhysicalBus(PhysicalBusOwner.OSCILLOSCOPE) {
                runOscilloscopeOwned(pidCode)
            }
        }
    }

    private suspend fun runOscilloscopeOwned(pidCode: String) {
        try {
                _statusMessage.value = "Iniciando ráfaga de alta velocidad (Osciloscopio)..."

                // Check if adapter is STN (Professional)
                if (!_isAdapterPro.value) {
                    // Fallback to high-frequency standard polling if not STN
                    while (currentCoroutineContext().isActive && isRunning) {
                        val t = System.currentTimeMillis()
                        val resp = sendRawCommand("01$pidCode", priority = 0)
                        val value = parsePidResponse("01$pidCode", resp)
                        if (value != null) _oscilloscopeStream.emit(t to value)
                        delay(25)
                    }
                    return
                }

                // PROFESSIONAL STN PATH: STP (Real Time Protocol)
                // STP provides timestamped data at up to 1000 samples/sec
                // Sequence: 1. Set Protocol 2. Start STP 3. Monitor data stream

                // Note: STP implementation varies by chip version.
                // Using a simplified ráfaga pattern for implementation.
                sendCommandDirectly("STP $pidCode")

                communicationMutex.withLock {
                    while (currentCoroutineContext().isActive && isRunning) {
                        // The stream owns the transport for its entire lifetime.
                        val rawBytes = transport?.read(256, 50L)
                        val rawData = rawBytes?.toString(Charsets.US_ASCII) ?: ""
                        if (rawData.isNotBlank()) {
                            val lines = rawData.split("\r", "\n")
                            for (line in lines) {
                                if (line.length >= 4) {
                                    val value = parsePidResponse("01$pidCode", line)
                                    if (value != null) {
                                        _oscilloscopeStream.emit(System.currentTimeMillis() to value)
                                    }
                                }
                            }
                        }
                        delay(10)
                    }
                }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Oscilloscope mode error", e)
        } finally {
            withContext(NonCancellable) {
                runCatching { sendCommandDirectly("STP STOP", timeoutMs = 1000L) }
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

    @Deprecated(
        message = "Use readProfessionalDtcScan(); raw List<String> loses ECU, service, status and evidence.",
        level = DeprecationLevel.ERROR,
    )
    suspend fun readActiveDtcs(): List<String> =
        error("Legacy DTC API retired: use readProfessionalDtcScan()")

    /**
     * Realiza un escaneo profundo en la memoria Mode 06 (Resultados de Pruebas de Monitoreo)
     * para extraer conteos de misfire por cilindro.
     */
    private suspend fun deepScanMisfires(): List<MisfireMonitorObservation> {
        val observations = mutableListOf<MisfireMonitorObservation>()
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
                        val testId = clean.substring(idx + 4, idx + 6)
                        val valueHex = clean.substring(idx + 6, idx + 10)
                        val value = valueHex.toIntOrNull(16) ?: 0
                        observations += MisfireMonitorObservation(
                            cylinder = cyl,
                            monitorId = hexMid,
                            testId = testId,
                            observedCount = value,
                            rawResponse = response,
                            capturedAtMonotonicMs = System.nanoTime() / 1_000_000L,
                            quality = TelemetryQuality.VALID,
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "deepScanMisfires failed: ${e.message}")
        }
        return observations
    }

    @Deprecated(
        message = "Use readProfessionalDtcScan(); raw List<String> loses ECU, service, status and evidence.",
        level = DeprecationLevel.ERROR,
    )
    suspend fun readPendingDtcs(): List<String> =
        error("Legacy DTC API retired: use readProfessionalDtcScan()")

    /**
     * Reads Freeze Frame data for a specific DTC (Mode 02).
     * @param dtc The fault code to query.
     */
    suspend fun readFreezeFrame(dtc: String): FreezeFrameReadResult =
        withExclusivePhysicalBus(PhysicalBusOwner.FREEZE_FRAME) { readFreezeFrameOwned(dtc) }

    private suspend fun readFreezeFrameOwned(dtc: String): FreezeFrameReadResult {
        val results = mutableMapOf<String, String>()
        val rawExchanges = linkedMapOf<String, String>()
        val requestedDtc = dtc.trim().uppercase()

        // Mode 02 PID 02: DTC that caused freeze frame
        val dtcResp = sendRawCommand("020200") // Frame 0
        rawExchanges["020200"] = dtcResp
        val normalizedIdentityResponse = dtcResp.uppercase().replace(" ", "")
        if (normalizedIdentityResponse.contains("NODATA") || normalizedIdentityResponse.contains("?")) {
            return FreezeFrameReadResult(
                requestedDtc = requestedDtc,
                actualDtc = null,
                outcome = FreezeFrameOutcome.NO_RESPONSE,
                identityRawResponse = dtcResp,
                rawExchanges = rawExchanges,
            )
        }
        val actualDtc = DtcScanEngine.parseFreezeFrameIdentity(dtcResp)
            ?: return FreezeFrameReadResult(
                requestedDtc = requestedDtc,
                actualDtc = null,
                outcome = FreezeFrameOutcome.MALFORMED_RESPONSE,
                identityRawResponse = dtcResp,
                rawExchanges = rawExchanges,
            )
        if (actualDtc != requestedDtc) {
            return FreezeFrameReadResult(
                requestedDtc = requestedDtc,
                actualDtc = actualDtc,
                outcome = FreezeFrameOutcome.BELONGS_TO_ANOTHER_DTC,
                identityRawResponse = dtcResp,
                rawExchanges = rawExchanges,
            )
        }

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
            rawExchanges[cmd] = res
            if (!res.contains("NODATA") && !res.contains("?")) {
                results[pid] = parseMode02Response(pid, res)
            }
        }

        _freezeFrame.value = results
        return FreezeFrameReadResult(
            requestedDtc = requestedDtc,
            actualDtc = actualDtc,
            outcome = FreezeFrameOutcome.MATCHED,
            values = results,
            identityRawResponse = dtcResp,
            rawExchanges = rawExchanges,
        )
    }

    private fun parseMode02Response(pid: String, response: String): String {
        val clean = CanMultiFrameParser.parse(response).replace(" ", "")
        val prefix = "42$pid"
        val idx = clean.uppercase().indexOf(prefix)
        if (idx < 0) return "N/A"

        val dataWithFrame = clean.substring(idx + prefix.length)
        // Every Mode 02 response echoes the freeze-frame number before PID data.
        if (dataWithFrame.length < 4) return "N/A"
        val data = dataWithFrame.substring(2)

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


    @Deprecated(
        message = "Use readProfessionalDtcScan(); raw List<String> loses ECU, service, status and evidence.",
        level = DeprecationLevel.ERROR,
    )
    suspend fun readPermanentDtcs(): List<String> =
        error("Legacy DTC API retired: use readProfessionalDtcScan()")

    suspend fun readProfessionalDtcScan(
        mode: DiagnosticScanMode = DiagnosticScanMode.FULL_VEHICLE,
    ): DtcScanReport = withExclusivePhysicalBus(
        PhysicalBusOwner.DIAGNOSTIC_SCAN,
    ) {
        readProfessionalDtcScanOwned(mode)
    }

    private suspend fun readProfessionalDtcScanOwned(
        mode: DiagnosticScanMode,
        verificationPlan: ClearVerificationPlan? = null,
    ): DtcScanReport {
        val startedAt = System.currentTimeMillis()
        diagnosticScanCancellationRequested = false
        _diagnosticScanEvents.tryEmit(DiagnosticScanEvent.ScanStarted(mode))
        if (_state.value != ObdState.CONNECTED) {
            val report = DtcScanReport(
                startedAtMs = startedAt,
                endedAtMs = System.currentTimeMillis(),
                protocol = detectedProtocol,
                records = emptyList(),
                modules = emptyList(),
                rawExchanges = emptyList(),
                completeness = ScanCompleteness.FAILED,
                warnings = listOf("OBD no conectado; ningún módulo fue consultado."),
                mode = mode,
            )
            _diagnosticScanEvents.tryEmit(DiagnosticScanEvent.ScanCompleted(report))
            return report
        }

        val records = mutableListOf<DtcRecord>()
        val rawExchanges = mutableListOf<DtcRawExchange>()
        val aliveModules = linkedMapOf<String, String>()
        val moduleReads = linkedMapOf<String, MutableList<DtcServiceRead>>()
        val attemptedModules = linkedMapOf<String, String>()
        val modulesCompletedDuringScan = linkedSetOf<String>()
        val protocolWarnings = mutableListOf<String>()
        var modulesPlanned = 0
        var modulesCompleted = 0
        var servicesPlanned = 0
        var servicesCompleted = 0

        fun emitProgress() {
            _diagnosticScanEvents.tryEmit(
                DiagnosticScanEvent.ProgressUpdated(
                    ScanProgressState(modulesCompleted, modulesPlanned, servicesCompleted, servicesPlanned),
                ),
            )
        }

        fun normalizeRecord(record: DtcRecord, fallbackName: String?): DtcRecord {
            val responseName = moduleNameForResponse(record.responseAddress)
            return DiagnosticFindingFactory.withResolvedModuleName(
                record = record,
                moduleName = responseName ?: record.moduleName ?: fallbackName,
            )
        }

        fun diagnosticRequestScope(target: String?, moduleName: String?): DiagnosticRequestScope = when {
            target == "7DF" -> DiagnosticRequestScope.Functional("7DF")
            target.isNullOrBlank() || target == "LEGACY" -> DiagnosticRequestScope.LegacyUnaddressed
            isDoIpMode -> DiagnosticRequestScope.Logical(
                EcuEndpoint(
                    busId = "DOIP",
                    networkType = DiagnosticTransport.DOIP,
                    addressingMode = DiagnosticAddressingMode.LOGICAL,
                    requestAddress = null,
                    responseAddress = null,
                    logicalAddress = target,
                    moduleRole = moduleName,
                    discoveryProvenance = "DOIP_ROUTING_ACTIVATION",
                ),
            )
            else -> DiagnosticRequestScope.Physical(
                EcuEndpoint(
                    busId = "CAN0",
                    networkType = DiagnosticTransport.CAN,
                    addressingMode = DiagnosticAddressingMode.PHYSICAL,
                    requestAddress = target,
                    responseAddress = null,
                    moduleRole = moduleName,
                    discoveryProvenance = "SCAN_PLAN",
                ),
            )
        }

        suspend fun queryStandard(command: String, mode: String, target: String?, moduleName: String?): DtcServiceRead {
            val moduleIdentity = DiagnosticModuleIdentity.canonical(target, null, moduleName)
            _diagnosticScanEvents.tryEmit(DiagnosticScanEvent.ServiceStarted(moduleIdentity, command, 1, 3))
            val requestStartedAt = System.currentTimeMillis()
            var failed = false
            var forcedOutcome: ModuleScanOutcome? = null
            val raw = try {
                sendRawCommand(command, priority = 999)
            } catch (_: TimeoutCancellationException) {
                forcedOutcome = ModuleScanOutcome.TIMEOUT
                ""
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                failed = true
                ""
            }
            val parsed = DtcScanEngine.parseStandardByEcu(raw, mode, target, moduleName)
                .map { normalizeRecord(it, moduleName) }
            val positiveService = when (mode) {
                "03" -> "43"
                "07" -> "47"
                "0A" -> "4A"
                else -> "4$mode"
            }
            val classification = if (forcedOutcome != null) {
                DiagnosticExchangeClassification(forcedOutcome)
            } else {
                DtcScanEngine.classifyExchangeDetailed(raw, positiveService, parsed.size, failed)
            }
            val outcome = classification.outcome
            rawExchanges += DtcRawExchange(
                command,
                target,
                raw,
                parsed.size,
                outcome,
                classification.negativeResponse,
                sessionId = startedAt.toString(),
                transport = when {
                    isDoIpMode -> DiagnosticTransport.DOIP
                    detectedProtocol.contains("CAN", ignoreCase = true) -> DiagnosticTransport.CAN
                    else -> DiagnosticTransport.K_LINE
                },
                requestScope = when {
                    target == "7DF" -> DiagnosticRequestScope.Functional("7DF")
                    target.isNullOrBlank() || target == "LEGACY" -> DiagnosticRequestScope.LegacyUnaddressed
                    else -> DiagnosticRequestScope.Physical(
                        EcuEndpoint("CAN0", DiagnosticTransport.CAN, DiagnosticAddressingMode.PHYSICAL, target, null, moduleRole = moduleName, discoveryProvenance = "SCAN_PLAN"),
                    )
                },
                latencyMs = System.currentTimeMillis() - requestStartedAt,
            )
            records += parsed
            parsed.forEach { _diagnosticScanEvents.tryEmit(DiagnosticScanEvent.FindingObserved(it)) }
            val bucket = when (mode) {
                "07" -> DtcBucket.PENDING
                "0A" -> DtcBucket.PERMANENT
                else -> DtcBucket.ACTIVE
            }
            val read = DtcServiceRead(
                command = command,
                coverage = DiagnosticCoverage.sae(bucket),
                outcome = outcome,
                negativeResponse = classification.negativeResponse,
            )
            moduleReads.getOrPut(target ?: "LEGACY") { mutableListOf() } += read
            if (target == "7DF") {
                DtcScanEngine.groupRawByEcu(raw).forEach { (responseAddress, responseLines) ->
                    val requestAddress = responseAddress.toIntOrNull(16)
                        ?.minus(8)
                        ?.takeIf { it >= 0 }
                        ?.let { String.format("%03X", it) }
                        ?: return@forEach
                    val discoveredName = moduleNameForResponse(responseAddress) ?: "ECU $responseAddress"
                    attemptedModules.putIfAbsent(requestAddress, discoveredName)
                    aliveModules.putIfAbsent(requestAddress, discoveredName)
                    val perEcuRaw = responseLines.joinToString("\n")
                    val perEcuRecordCount = parsed.count { it.responseAddress == responseAddress }
                    val perEcuClassification = DtcScanEngine.classifyExchangeDetailed(
                        rawResponse = perEcuRaw,
                        positiveResponseService = positiveService,
                        parsedRecordCount = perEcuRecordCount,
                    )
                    val perEcuRead = read.copy(
                        outcome = perEcuClassification.outcome,
                        negativeResponse = perEcuClassification.negativeResponse,
                    )
                    moduleReads.getOrPut(requestAddress) { mutableListOf() } += perEcuRead
                    rawExchanges += DtcRawExchange(
                        command = command,
                        targetAddress = "7DF",
                        rawResponse = perEcuRaw,
                        parsedRecordCount = perEcuRecordCount,
                        outcome = perEcuClassification.outcome,
                        negativeResponse = perEcuClassification.negativeResponse,
                        sessionId = startedAt.toString(),
                        transport = DiagnosticTransport.CAN,
                        requestScope = DiagnosticRequestScope.Functional("7DF"),
                        responseAddress = responseAddress,
                        latencyMs = System.currentTimeMillis() - requestStartedAt,
                    )
                    _diagnosticScanEvents.tryEmit(
                        DiagnosticScanEvent.ModuleDiscovered(
                            EcuEndpoint(
                                busId = "CAN0",
                                networkType = DiagnosticTransport.CAN,
                                addressingMode = DiagnosticAddressingMode.PHYSICAL,
                                requestAddress = requestAddress,
                                responseAddress = responseAddress,
                                moduleRole = discoveredName,
                                discoveryProvenance = "FUNCTIONAL_RESPONSE",
                            ),
                        ),
                    )
                }
            }
            _diagnosticScanEvents.tryEmit(DiagnosticScanEvent.CoverageUpdated(moduleIdentity, read.coverage))
            _diagnosticScanEvents.tryEmit(DiagnosticScanEvent.ServiceCompleted(moduleIdentity, command, outcome, 1, 3))
            servicesCompleted++
            emitProgress()
            return read
        }

        suspend fun queryUds(command: String, target: String?, moduleName: String?): Pair<List<DtcRecord>, DtcServiceRead> {
            val moduleIdentity = DiagnosticModuleIdentity.canonical(target, null, moduleName)
            _diagnosticScanEvents.tryEmit(DiagnosticScanEvent.ServiceStarted(moduleIdentity, command, 1, 1))
            var failed = false
            var forcedOutcome: ModuleScanOutcome? = null
            var retryCount = 0
            var raw = ""
            var requestStartedAt = System.currentTimeMillis()
            var awaitPendingResponse = false
            var pendingResponseTimeoutMs = 1_500L
            while (true) {
                requestStartedAt = System.currentTimeMillis()
                raw = try {
                    if (awaitPendingResponse) {
                        awaitDiagnosticResponseWithoutRequest(
                            timeoutMs = pendingResponseTimeoutMs,
                            doIpTarget = target?.toIntOrNull(16),
                        )
                    } else {
                        if (isDoIpMode) {
                            val logicalTarget = target?.toIntOrNull(16)
                                ?: throw IllegalStateException("DoIP request requires an explicit logical target")
                            sendDoIpDiagnostic(logicalTarget, command, pendingResponseTimeoutMs)
                        } else {
                            sendRawCommand(command, priority = 999)
                        }
                    }
                } catch (_: TimeoutCancellationException) {
                    forcedOutcome = ModuleScanOutcome.TIMEOUT
                    ""
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    failed = true
                    ""
                }
                awaitPendingResponse = false
                if (forcedOutcome != null || failed) break
                val negative = DiagnosticPduDecoder.decodeResponses(raw, 0x59, 0x19)
                    .filterIsInstance<ProtocolResponse.Negative>()
                    .firstOrNull()
                    ?.response
                    ?: break
                val action = UdsNegativeResponsePolicy.actionFor(negative, retryCount + 1)
                when (action) {
                    is UdsNrcAction.AwaitFinalResponse -> {
                        if (retryCount >= 2) break
                        rawExchanges += DtcRawExchange(
                            command, target, raw, 0, ModuleScanOutcome.NEGATIVE_RESPONSE, negative,
                            sessionId = startedAt.toString(),
                            transport = if (isDoIpMode) DiagnosticTransport.DOIP else DiagnosticTransport.CAN,
                            requestScope = diagnosticRequestScope(target, moduleName),
                            latencyMs = System.currentTimeMillis() - requestStartedAt,
                            retryCount = retryCount,
                        )
                        retryCount++
                        pendingResponseTimeoutMs = action.p2StarDelayMs.coerceAtLeast(500L)
                        awaitPendingResponse = true
                    }
                    is UdsNrcAction.RetryAfterDelay -> {
                        if (action.remainingAttempts <= 0) break
                        rawExchanges += DtcRawExchange(
                            command, target, raw, 0, ModuleScanOutcome.NEGATIVE_RESPONSE, negative,
                            sessionId = startedAt.toString(),
                            transport = if (isDoIpMode) DiagnosticTransport.DOIP else DiagnosticTransport.CAN,
                            requestScope = diagnosticRequestScope(target, moduleName),
                            latencyMs = System.currentTimeMillis() - requestStartedAt,
                            retryCount = retryCount,
                        )
                        delay(action.delayMs)
                        retryCount++
                    }
                    else -> break
                }
            }
            val parsed = DtcScanEngine.parseUdsService19ByEcu(raw, target, moduleName)
                .map { normalizeRecord(it, moduleName) }
            val classification = if (forcedOutcome != null) {
                DiagnosticExchangeClassification(forcedOutcome)
            } else {
                DtcScanEngine.classifyExchangeDetailed(raw, "59", parsed.size, failed)
            }
            val outcome = classification.outcome
            rawExchanges += DtcRawExchange(
                command,
                target,
                raw,
                parsed.size,
                outcome,
                classification.negativeResponse,
                sessionId = startedAt.toString(),
                transport = if (isDoIpMode) DiagnosticTransport.DOIP else DiagnosticTransport.CAN,
                requestScope = diagnosticRequestScope(target, moduleName),
                latencyMs = System.currentTimeMillis() - requestStartedAt,
                retryCount = retryCount,
            )
            records += parsed
            parsed.forEach { _diagnosticScanEvents.tryEmit(DiagnosticScanEvent.FindingObserved(it)) }
            val statusMask = command.takeLast(2).toIntOrNull(16) ?: 0xFF
            val read = DtcServiceRead(
                command = command,
                coverage = DiagnosticCoverage.udsForStatusMask(statusMask),
                outcome = outcome,
                negativeResponse = classification.negativeResponse,
            )
            moduleReads.getOrPut(target ?: "LEGACY") { mutableListOf() } += read
            _diagnosticScanEvents.tryEmit(DiagnosticScanEvent.CoverageUpdated(moduleIdentity, read.coverage))
            _diagnosticScanEvents.tryEmit(DiagnosticScanEvent.ServiceCompleted(moduleIdentity, command, outcome, 1, 1))
            servicesCompleted++
            emitProgress()
            return parsed to read
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

        fun emitModuleCompletedNow(
            target: String,
            moduleName: String,
            responseAddress: String? = null,
            readsOverride: List<DtcServiceRead>? = null,
        ) {
            val identity = DiagnosticModuleIdentity.canonical(target, responseAddress, moduleName)
            if (!modulesCompletedDuringScan.add(identity)) return
            val reads = readsOverride ?: moduleReads[target].orEmpty()
            val moduleRecords = records.filter {
                DiagnosticModuleIdentity.canonical(it.targetAddress, it.responseAddress, it.moduleName) == identity
            }
            val outcome = when {
                reads.isEmpty() -> ModuleScanOutcome.NO_RESPONSE
                reads.all { it.outcome.provesBucketWasRead } ->
                    if (moduleRecords.isEmpty()) ModuleScanOutcome.NO_DTC else ModuleScanOutcome.COMPLETE
                reads.any { it.outcome.provesBucketWasRead } -> ModuleScanOutcome.PARTIAL_RESPONSE
                else -> reads.first().outcome
            }
            _diagnosticScanEvents.tryEmit(
                DiagnosticScanEvent.ModuleCompleted(identity, moduleName, moduleRecords.size, outcome),
            )
        }

        val diagnosticStack = DiagnosticProtocolRegistry.resolve(detectedProtocol, isDoIpMode)
        val isCan = diagnosticStack.transport == DiagnosticTransport.CAN
        var wasCancelled = false
        try {
            if (diagnosticStack.transport == DiagnosticTransport.DOIP) {
                val doIpPlan = DiagnosticStrategyRegistry.forStack(diagnosticStack)
                    .compileDtcPlan(mode)
                if (!doIpPlan.isSupported) {
                    throw IllegalStateException(doIpPlan.unsupportedReason ?: "DoIP UDS no disponible")
                }
                modulesPlanned = 1
                servicesPlanned = doIpPlan.primaryRequests.size + doIpPlan.fallbackRequests.size
                _diagnosticScanEvents.tryEmit(DiagnosticScanEvent.ScanPlanCompiled(1, servicesPlanned))
                emitProgress()
                val logicalTarget = "%04X".format(doIpTargetLogicalAddress)
                val moduleName = "DoIP Diagnostic Server"
                attemptedModules[logicalTarget] = moduleName
                aliveModules[logicalTarget] = moduleName
                _diagnosticScanEvents.tryEmit(
                    DiagnosticScanEvent.ModuleDiscovered(
                        EcuEndpoint(
                            busId = "DOIP",
                            networkType = DiagnosticTransport.DOIP,
                            addressingMode = DiagnosticAddressingMode.LOGICAL,
                            requestAddress = null,
                            responseAddress = null,
                            logicalAddress = logicalTarget,
                            moduleRole = moduleName,
                            discoveryProvenance = "ISO13400_ROUTING_ACTIVATION",
                        ),
                    ),
                )
                _diagnosticScanEvents.tryEmit(DiagnosticScanEvent.ModuleReading(logicalTarget, moduleName))
                val (_, allRead) = queryUds(doIpPlan.primaryRequests.first(), logicalTarget, moduleName)
                if (!allRead.outcome.provesBucketWasRead && !diagnosticScanCancellationRequested) {
                    doIpPlan.fallbackRequests.forEach { fallback ->
                        if (!diagnosticScanCancellationRequested) queryUds(fallback, logicalTarget, moduleName)
                    }
                }
                emitModuleCompletedNow(logicalTarget, moduleName)
                modulesCompleted = 1
                emitProgress()
                wasCancelled = diagnosticScanCancellationRequested
            } else if (isCan) {
                _statusMessage.value = "Escaneo DTC profesional: configurando bus CAN..."
                runCatching { sendRawCommand("ATH1", priority = 999) }
                runCatching { sendRawCommand("ATAL", priority = 999) }
                runCatching { sendRawCommand("ATCAF1", priority = 999) }

                val physicalTargets = DiagnosticScanPlanCompiler.compile(
                    mode = mode,
                    confirmedModules = _networkTopology.value,
                    discoveryCandidates = professionalDtcTargets(),
                    clearVerificationPlan = verificationPlan,
                )
                modulesPlanned = physicalTargets.size.coerceAtLeast(1)
                // Functional SAE reads plus at most three evidence services
                // per physical endpoint (SAE 03/07/0A or UDS primary/fallback).
                servicesPlanned = 3 + physicalTargets.size * 3
                _diagnosticScanEvents.tryEmit(
                    DiagnosticScanEvent.ScanPlanCompiled(
                        modulesPlanned = modulesPlanned,
                        servicesPlanned = servicesPlanned,
                    ),
                )
                emitProgress()

                // Phase 1: functional broadcast with headers.
                runCatching { sendRawCommand("ATSH7DF", priority = 999) }
                _diagnosticScanEvents.tryEmit(
                    DiagnosticScanEvent.ModuleReading("7DF", "Solicitud funcional CAN"),
                )
                queryStandard("03", "03", "7DF", "Respuesta ECU")
                if (!diagnosticScanCancellationRequested) queryStandard("07", "07", "7DF", "Respuesta ECU")
                if (!diagnosticScanCancellationRequested) queryStandard("0A", "0A", "7DF", "Respuesta ECU")
                if (diagnosticScanCancellationRequested) wasCancelled = true

                // Phase 2: physical module sweep
                for (scanTarget in physicalTargets) {
                    val target = scanTarget.requestAddress
                    val moduleName = scanTarget.moduleName
                    if (diagnosticScanCancellationRequested) {
                        wasCancelled = true
                        break
                    }
                    attemptedModules[target] = moduleName
                    _statusMessage.value = "Escaneando $moduleName ($target)..."
                    _diagnosticScanEvents.tryEmit(
                        DiagnosticScanEvent.ModuleReading(target, moduleName),
                    )
                    runCatching { sendRawCommand("ATSH$target", priority = 999) }
                    target.toIntOrNull(16)?.let { requestHex ->
                        val responseId = String.format("%03X", requestHex + 8)
                        runCatching { sendRawCommand("ATCRA$responseId", priority = 999) }
                    }

                    try {
                        val probeRaw = runCatching { sendRawCommand("0100", priority = 999) }.getOrDefault("")
                        val saeObdCapable = isAliveResponse(probeRaw) &&
                            DiagnosticPduDecoder.decodeResponses(probeRaw, 0x41, 0x01)
                                .any { it is ProtocolResponse.Positive }
                        var udsCapable = false
                        var alive = saeObdCapable
                        rawExchanges += DtcRawExchange(
                            "0100", target, probeRaw, 0,
                            if (alive) ModuleScanOutcome.COMPLETE else ModuleScanOutcome.NO_RESPONSE,
                        )

                        if (!alive) {
                            val testerRaw = runCatching { sendRawCommand("3E00", priority = 999) }.getOrDefault("")
                            udsCapable = DiagnosticPduDecoder.decodeResponses(testerRaw, 0x7E, 0x3E)
                                .any { it is ProtocolResponse.Positive || it is ProtocolResponse.Negative }
                            alive = udsCapable
                            rawExchanges += DtcRawExchange(
                                "3E00", target, testerRaw, 0,
                                if (alive) ModuleScanOutcome.COMPLETE else ModuleScanOutcome.NO_RESPONSE,
                            )
                        }

                        if (!alive) continue
                        aliveModules[target] = moduleName

                        if (udsCapable) {
                            runCatching { sendRawCommand("1003", priority = 999) }
                            val (_, udsAllRead) = queryUds("1902FF", target, moduleName)
                            if (!udsAllRead.outcome.provesBucketWasRead) {
                                queryUds("19020D", target, moduleName)
                            }
                        } else if (saeObdCapable) {
                            queryStandard("03", "03", target, moduleName)
                            queryStandard("07", "07", target, moduleName)
                            queryStandard("0A", "0A", target, moduleName)
                        }
                    } finally {
                        emitModuleCompletedNow(target, moduleName)
                        modulesCompleted = (modulesCompleted + 1).coerceAtMost(modulesPlanned)
                        emitProgress()
                        runCatching { sendRawCommand("ATCRA", priority = 999) }
                    }
                }
                if (physicalTargets.isEmpty()) {
                    modulesCompleted = 1
                    emitProgress()
                }
            } else if (diagnosticStack.applicationProtocol in setOf(
                    DiagnosticApplicationProtocol.KWP2000,
                    DiagnosticApplicationProtocol.OBD_ON_UDS,
                    DiagnosticApplicationProtocol.OEM,
                )
            ) {
                val unsupported = DiagnosticStrategyRegistry.forStack(diagnosticStack).compileDtcPlan(mode)
                modulesPlanned = 0
                servicesPlanned = 0
                _diagnosticScanEvents.tryEmit(DiagnosticScanEvent.ScanPlanCompiled(0, 0))
                val unsupportedReason = unsupported.unsupportedReason
                    ?: "Protocolo detectado sin estrategia diagnóstica verificada; no se enviaron comandos especulativos."
                protocolWarnings += unsupportedReason
                _statusMessage.value = unsupportedReason
            } else {
                _statusMessage.value = "Escaneo DTC estándar: consultando protocolo legado..."
                modulesPlanned = 1
                servicesPlanned = 3
                _diagnosticScanEvents.tryEmit(DiagnosticScanEvent.ScanPlanCompiled(1, 3))
                emitProgress()
                runCatching { sendRawCommand("ATH0", priority = 999) }
                attemptedModules["LEGACY"] = "Standard OBD-II"
                _diagnosticScanEvents.tryEmit(
                    DiagnosticScanEvent.ModuleReading("LEGACY", "Standard OBD-II"),
                )
                // Consult standard Modes globally without CAN addressing
                queryStandard("03", "03", null, "Standard OBD-II")
                if (!diagnosticScanCancellationRequested) queryStandard("07", "07", null, "Standard OBD-II")
                if (!diagnosticScanCancellationRequested) queryStandard("0A", "0A", null, "Standard OBD-II")
                if (diagnosticScanCancellationRequested) wasCancelled = true
                emitModuleCompletedNow("LEGACY", "Standard OBD-II")
                modulesCompleted = 1
                emitProgress()
                if (moduleReads["LEGACY"].orEmpty().any { read ->
                        read.outcome in setOf(
                            ModuleScanOutcome.COMPLETE,
                            ModuleScanOutcome.NO_DTC,
                            ModuleScanOutcome.NEGATIVE_RESPONSE,
                            ModuleScanOutcome.PARTIAL_RESPONSE,
                            ModuleScanOutcome.MALFORMED_RESPONSE,
                        )
                    }
                ) {
                    aliveModules["LEGACY"] = "Standard OBD-II"
                }
            }
        } catch (_: CancellationException) {
            wasCancelled = true
        } finally {
            if (isCan) {
                withContext(NonCancellable) {
                    runCatching { sendRawCommand("ATSH7DF", priority = 999) }
                    runCatching { sendRawCommand("ATCRA", priority = 999) }
                    runCatching { sendRawCommand("ATH0", priority = 999) }
                }
            }
        }

        val distinctRecords = records.distinctBy {
            "${it.namespace}|${DiagnosticModuleIdentity.canonical(it.targetAddress, it.responseAddress, it.moduleName)}|" +
                "${it.code}|${it.bucket}|${it.udsStatusByte}|${it.udsFailureType}|${it.sourceService}"
        }
        val moduleKeys = (
            attemptedModules.keys.filterNot { it == "7DF" } +
                aliveModules.keys.filterNot { it == "7DF" } +
                distinctRecords.mapNotNull {
                    if (it.targetAddress == null && it.responseAddress == null) null
                    else DiagnosticModuleIdentity.canonical(it.targetAddress, it.responseAddress, it.moduleName)
                }.filterNot { it == "7DF" }
            ).distinct()
        val modules = moduleKeys.map { key ->
            val responseOnlyEndpoint = key !in attemptedModules && distinctRecords.any { it.responseAddress == key }
            val requestKey = if (responseOnlyEndpoint && key.length == 3) {
                key.toIntOrNull(16)?.minus(8)?.takeIf { it >= 0 }?.let { String.format("%03X", it) } ?: key
            } else {
                key
            }
            val expectedResponse = when {
                responseOnlyEndpoint -> key
                key.length == 3 -> key.toIntOrNull(16)?.let { String.format("%03X", it + 8) }
                else -> null
            }
            val moduleRecords = distinctRecords.filter {
                if (key == "LEGACY") {
                    it.targetAddress == null && it.responseAddress == null
                } else {
                    it.targetAddress == requestKey || it.responseAddress == key || it.responseAddress == expectedResponse
                }
            }
            val reads = moduleReads[requestKey].orEmpty().ifEmpty {
                if (responseOnlyEndpoint) moduleReads["7DF"].orEmpty() else emptyList()
            }
            val moduleOutcome = when {
                reads.isEmpty() -> ModuleScanOutcome.NO_RESPONSE
                reads.all { it.outcome.provesBucketWasRead } ->
                    if (moduleRecords.isEmpty()) ModuleScanOutcome.NO_DTC else ModuleScanOutcome.COMPLETE
                reads.any { it.outcome.provesBucketWasRead } -> ModuleScanOutcome.PARTIAL_RESPONSE
                else -> reads.first().outcome
            }
            val demonstratedAlive = key in aliveModules || requestKey in aliveModules || reads.any {
                it.outcome in setOf(
                    ModuleScanOutcome.COMPLETE,
                    ModuleScanOutcome.NO_DTC,
                    ModuleScanOutcome.NEGATIVE_RESPONSE,
                    ModuleScanOutcome.PARTIAL_RESPONSE,
                    ModuleScanOutcome.MALFORMED_RESPONSE,
                )
            }
            val requiredForCompleteness = key in aliveModules || requestKey in aliveModules || responseOnlyEndpoint
            DtcModuleReport(
                targetAddress = requestKey.takeUnless { it == "LEGACY" },
                responseAddress = expectedResponse,
                moduleName = attemptedModules[requestKey] ?: aliveModules[requestKey] ?: moduleNameForResponse(expectedResponse ?: key) ?: "ECU $key",
                isAlive = demonstratedAlive,
                dtcs = moduleRecords,
                rawExchanges = rawExchanges.filter {
                    it.targetAddress == requestKey ||
                        it.responseAddress == expectedResponse ||
                        (responseOnlyEndpoint && it.targetAddress == "7DF")
                },
                serviceReads = reads,
                outcome = moduleOutcome,
                discoveryState = when {
                    demonstratedAlive -> DiagnosticModuleDiscoveryState.CONFIRMED
                    key == "7DF" -> DiagnosticModuleDiscoveryState.EXPECTED
                    else -> DiagnosticModuleDiscoveryState.DISCOVERY_CANDIDATE
                },
                requiredForCompleteness = requiredForCompleteness,
            )
        }

        val requiredCoverageModules = modules.filter {
            it.requiredForCompleteness && it.serviceReads.isNotEmpty()
        }
        val completeReads = requiredCoverageModules.count {
            it.outcome == ModuleScanOutcome.COMPLETE || it.outcome == ModuleScanOutcome.NO_DTC
        }
        val completeness = when {
            wasCancelled && completeReads > 0 -> ScanCompleteness.PARTIAL
            wasCancelled -> ScanCompleteness.INCONCLUSIVE
            requiredCoverageModules.isEmpty() -> ScanCompleteness.FAILED
            completeReads == requiredCoverageModules.size -> ScanCompleteness.COMPLETE
            completeReads > 0 -> ScanCompleteness.PARTIAL
            requiredCoverageModules.any { it.isAlive } -> ScanCompleteness.INCONCLUSIVE
            else -> ScanCompleteness.FAILED
        }
        val warnings = modules.filter {
            (it.requiredForCompleteness || it.discoveryState == DiagnosticModuleDiscoveryState.CONFIRMED) &&
                it.outcome !in setOf(ModuleScanOutcome.COMPLETE, ModuleScanOutcome.NO_DTC)
        }.map { "${it.moduleName}: ${it.outcome.name}" }.toMutableList().apply {
            addAll(protocolWarnings)
            if (wasCancelled) add("Escaneo detenido por el usuario; resultados parciales conservados.")
        }

        modules.filter {
            (it.isAlive || it.serviceReads.isNotEmpty()) && it.moduleIdentity !in modulesCompletedDuringScan
        }.forEach { module ->
            _diagnosticScanEvents.tryEmit(
                DiagnosticScanEvent.ModuleCompleted(
                    moduleIdentity = module.moduleIdentity,
                    moduleName = module.moduleName,
                    findingCount = module.dtcs.size,
                    outcome = if (wasCancelled && module.serviceReads.isEmpty()) {
                        ModuleScanOutcome.CANCELLED
                    } else {
                        module.outcome
                    },
                ),
            )
        }

        val report = DtcScanReport(
            startedAtMs = startedAt,
            endedAtMs = System.currentTimeMillis(),
            protocol = detectedProtocol,
            records = distinctRecords,
            modules = modules,
            rawExchanges = rawExchanges,
            completeness = completeness,
            warnings = warnings,
            mode = mode,
            wasCancelled = wasCancelled,
        )
        _lastDtcScanReport.value = report
        _allDetectedDtcs.value = _allDetectedDtcs.value + distinctRecords.map { it.code }
        _statusMessage.value = "Escaneo DTC ${completeness.name.lowercase()}: ${distinctRecords.size} hallazgos."
        if (wasCancelled) {
            _diagnosticScanEvents.tryEmit(
                DiagnosticScanEvent.ScanCancelled(requiredCoverageModules.size, distinctRecords.size),
            )
        }
        _diagnosticScanEvents.tryEmit(DiagnosticScanEvent.ScanCompleted(report))
        return report
    }

    suspend fun clearDtcs(
        verificationPlan: ClearVerificationPlan = ClearVerificationPlan.empty(),
    ): ClearDtcResult = withExclusivePhysicalBus(PhysicalBusOwner.DTC_CLEAR) {
        clearDtcsOwned(verificationPlan)
    }

    private suspend fun clearDtcsOwned(verificationPlan: ClearVerificationPlan): ClearDtcResult {
        if (_state.value != ObdState.CONNECTED) {
            return ClearDtcResult.Rejected(
                commandEvidence = emptyList(),
                message = "OBD no conectado; no se envió ninguna solicitud de borrado.",
            )
        }
        if (verificationPlan.targets.isEmpty()) {
            return ClearDtcResult.Rejected(
                commandEvidence = emptyList(),
                message = "No existe un snapshot previo con hallazgos identificados por ECU; no se envió un borrado ciego.",
            )
        }
        val commandEvidence = mutableListOf<ClearCommandEvidence>()
        return try {
            pauseLivePolling()
            clearCommandQueue()

            val targetNamespaces = verificationPlan.targets.mapTo(linkedSetOf()) {
                it.findingKey.namespace
            }
            if (DiagnosticNamespace.SAE_OBD in targetNamespaces) {
                _statusMessage.value = "Enviando borrado SAE OBD únicamente para los hallazgos SAE del plan..."
                runCatching { sendRawCommand("ATSH7DF", priority = 1) }
                val response = sendRawCommand("04", priority = 1)
                commandEvidence += decodeClearCommandEvidence(
                    command = "04",
                    response = response,
                    protocol = DiagnosticApplicationProtocol.SAE_OBD,
                    scope = DiagnosticRequestScope.Functional("7DF"),
                )
            }

            if (DiagnosticNamespace.UDS in targetNamespaces) {
                _statusMessage.value = "Enviando UDS Service 14 solo a las ECU objetivo demostradas..."
                commandEvidence += tryClearDtcsWithUds(verificationPlan)
            }

            if (commandEvidence.none(ClearCommandEvidence::acceptedByEcu)) {
                val message = explainClearDtcFailure(commandEvidence)
                _statusMessage.value = message
                return ClearDtcResult.Rejected(commandEvidence, message = message)
            }

            _statusMessage.value = "✓ ECU aceptó comando de borrado. Verificando ausencia de fallas dirigida..."
            delay(150)
            val postClearReport = readProfessionalDtcScanOwned(
                mode = DiagnosticScanMode.CLEAR_VERIFY,
                verificationPlan = verificationPlan,
            )
            if (postClearReport.wasCancelled) {
                val message = "Borrado aceptado; verificación cancelada. Ningún hallazgo fue resuelto."
                _statusMessage.value = message
                return ClearDtcResult.Cancelled(commandEvidence, postClearReport, message = message)
            }

            val evaluation = ClearVerificationEvaluator.evaluate(
                plan = verificationPlan,
                postClearReport = postClearReport,
                commandEvidence = commandEvidence,
            )
            val verifiedIds = evaluation.verifiedFindingIds
            val unverifiedIds = evaluation.unverifiedFindingIds

            when {
                verifiedIds.size == verificationPlan.targets.size -> {
                    _allDetectedDtcs.value = postClearReport.records.mapTo(linkedSetOf()) { it.code }
                    val message = "Borrado y ausencia verificados para ${verifiedIds.size} hallazgos con cobertura ECU/servicio."
                    _statusMessage.value = message
                    ClearDtcResult.Verified(commandEvidence, postClearReport, verifiedIds, message)
                }
                verifiedIds.isNotEmpty() -> {
                    val message = "Borrado aceptado; verificación parcial: ${verifiedIds.size} verificados, ${unverifiedIds.size} sin cobertura concluyente."
                    _statusMessage.value = message
                    ClearDtcResult.PartiallyVerified(
                        commandEvidence,
                        postClearReport,
                        verifiedIds,
                        unverifiedIds,
                        message,
                    )
                }
                else -> {
                    val message = "Borrado aceptado, pero ninguna identidad obtuvo verificación post-borrado suficiente."
                    _statusMessage.value = message
                    ClearDtcResult.AcceptedButNotVerified(commandEvidence, postClearReport, message = message)
                }
            }
        } catch (cancelled: CancellationException) {
            val message = "Borrado/verificación cancelados; la evidencia previa permanece sin resolver."
            _statusMessage.value = message
            ClearDtcResult.Cancelled(commandEvidence, _lastDtcScanReport.value, message = message)
        } catch (e: Exception) {
            val message = "Borrado inconcluso: ${e.message ?: "error de transporte"}. Ningún hallazgo fue resuelto."
            _statusMessage.value = message
            ClearDtcResult.Inconclusive(commandEvidence, _lastDtcScanReport.value, message = message)
        } finally {
            // Always restore clean state for live polling
            runCatching { sendRawCommand("ATSH7DF", priority = 1) }
            runCatching { sendRawCommand("ATCRA", priority = 1) }
            runCatching { sendRawCommand("ATH0", priority = 1) }
            runCatching { sendRawCommand("ATCAF1", priority = 1) }
            resumeLivePolling()
        }
    }

    private fun decodeClearCommandEvidence(
        command: String,
        response: String,
        protocol: DiagnosticApplicationProtocol,
        scope: DiagnosticRequestScope,
    ): ClearCommandEvidence {
        val requestedService = if (protocol == DiagnosticApplicationProtocol.UDS) 0x14 else 0x04
        val expectedPositive = requestedService + 0x40
        val decoded = DiagnosticPduDecoder.decodeResponses(response, expectedPositive, requestedService)
        val positive = decoded.filterIsInstance<ProtocolResponse.Positive>().firstOrNull()
        val negative = decoded.filterIsInstance<ProtocolResponse.Negative>().firstOrNull()?.response
        val adapterAcknowledged = response.trim().uppercase().lines().any { it.trim() == "OK" }
        return ClearCommandEvidence(
            protocol = protocol,
            requestScope = scope,
            command = command,
            rawResponse = response,
            positiveService = positive?.serviceId,
            acceptedByEcu = positive != null,
            adapterAcknowledged = adapterAcknowledged,
            negativeResponse = negative,
        )
    }

    private suspend fun tryClearDtcsWithUds(
        verificationPlan: ClearVerificationPlan,
    ): List<ClearCommandEvidence> {
        val isCan = detectedProtocol.contains("CAN", ignoreCase = true)
        if (!isCan && !isDoIpMode) return emptyList()

        // A destructive UDS request is only sent to endpoints demonstrated by
        // current evidence and named by the immutable clear plan. The
        // functional broadcast address is not an ECU.
        val targetModuleIdentities = verificationPlan.targets
            .filter { it.findingKey.namespace == DiagnosticNamespace.UDS }
            .mapTo(linkedSetOf()) { it.findingKey.moduleIdentity }
        val targets = _lastDtcScanReport.value?.modules.orEmpty()
            .filter { it.isAlive && !it.targetAddress.isNullOrBlank() && it.targetAddress != "7DF" }
            .filter { it.moduleIdentity in targetModuleIdentities }
            .map { it.targetAddress!! to it.responseAddress }
            .distinctBy { it.first }
        val evidence = mutableListOf<ClearCommandEvidence>()
        try {
            if (!isDoIpMode) {
                runCatching { sendRawCommand("ATH1", priority = 1) }
                runCatching { sendRawCommand("ATAL", priority = 1) }
                runCatching { sendRawCommand("ATCAF1", priority = 1) }
            }
            for ((target, demonstratedResponseAddress) in targets) {
                if (!isDoIpMode) runCatching { sendRawCommand("ATSH$target", priority = 1) }
                val responseId = demonstratedResponseAddress ?: when (target) {
                    "7E0" -> "7E8"
                    "7E1" -> "7E9"
                    "7E2" -> "7EA"
                    "7E3" -> "7EB"
                    "7E4" -> "7EC"
                    "7E5" -> "7ED"
                    else -> ""
                }
                if (!isDoIpMode && responseId.isNotBlank()) {
                    runCatching { sendRawCommand("ATCRA$responseId", priority = 1) }
                }

                val logicalTarget = target.toIntOrNull(16)
                if (isDoIpMode && logicalTarget == null) continue
                if (isDoIpMode) {
                    // Exact response endpoint is checked inside sendDoIpDiagnostic.
                    runCatching { sendDoIpDiagnostic(requireNotNull(logicalTarget), "1003") }
                } else {
                    runCatching { sendRawCommand("1003", priority = 1) }
                        .recoverCatching { sendRawCommand("1001", priority = 1) }
                }

                val udsResponse = runCatching {
                    if (isDoIpMode) {
                        sendDoIpDiagnostic(requireNotNull(logicalTarget), "14FFFFFF")
                    } else {
                        sendRawCommand("14FFFFFF", priority = 1)
                    }
                }
                    .getOrElse { "ERR:${it.message}" }
                val endpoint = EcuEndpoint(
                    busId = if (isDoIpMode) "DOIP" else "CAN0",
                    networkType = if (isDoIpMode) DiagnosticTransport.DOIP else DiagnosticTransport.CAN,
                    addressingMode = if (isDoIpMode) DiagnosticAddressingMode.LOGICAL else DiagnosticAddressingMode.PHYSICAL,
                    requestAddress = target.takeUnless { isDoIpMode },
                    responseAddress = responseId.ifBlank { null },
                    logicalAddress = target.takeIf { isDoIpMode },
                    moduleRole = null,
                    discoveryProvenance = "CLEAR_PLAN",
                )
                evidence += decodeClearCommandEvidence(
                    command = "14FFFFFF",
                    response = udsResponse,
                    protocol = DiagnosticApplicationProtocol.UDS,
                    scope = if (isDoIpMode) {
                        DiagnosticRequestScope.Logical(endpoint)
                    } else {
                        DiagnosticRequestScope.Physical(endpoint)
                    },
                )
            }
        } finally {
            if (!isDoIpMode) {
                runCatching { sendRawCommand("ATSH7DF", priority = 1) }
                runCatching { sendRawCommand("ATCRA", priority = 1) }
                runCatching { sendRawCommand("ATH0", priority = 1) }
            }
        }
        return evidence
    }

    private fun explainClearDtcFailure(evidence: List<ClearCommandEvidence>): String {
        val joined = evidence.joinToString(" | ") { "${it.command}=${it.rawResponse}" }.uppercase()
        val reason = when {
            joined.contains("7F1433") || joined.contains("SECURITY") ->
                "el ECU exige acceso de seguridad para UDS \$14"
            joined.contains("7F1422") || joined.contains("7F0422") ->
                "condiciones incorrectas: usa contacto ON, motor apagado y voltaje estable"
            joined.contains("7F1431") || joined.contains("7F0431") ->
                "solicitud fuera de rango para ese módulo"
            joined.contains("7F1411") || joined.contains("7F0411") ->
                "servicio no soportado por ese ECU/adaptador"
            joined.contains("NODATA") || joined.contains("NO DATA") ->
                "el adaptador no recibió confirmación del ECU"
            else -> "respuesta no positiva del ECU"
        }
        val adapterOnly = evidence.any { it.adapterAcknowledged } && evidence.none { it.acceptedByEcu }
        val adapterNotice = if (adapterOnly) " El adaptador respondió OK, pero eso no prueba aceptación del ECU." else ""
        return "No se pudo verificar aceptación de borrado: $reason.$adapterNotice"
    }

    /**
     * Reads a VIN from a physical ECU and returns the exact exchange that proved it.
     * Metadata, cached profiles and user-entered VINs are deliberately not fallbacks.
     */
    suspend fun readVinFromVehicle(): VinReadResult {
        if (_state.value != ObdState.CONNECTED) {
            return VinReadResult(
                outcome = VinReadOutcome.NOT_CONNECTED,
                capturedAtMonotonicMs = System.nanoTime() / 1_000_000L,
            )
        }
        return withExclusivePhysicalBus(PhysicalBusOwner.VIN_READ) {
            readVinFromVehicleOwned()
        }
    }

    private suspend fun readVinFromVehicleOwned(): VinReadResult {
        var receivedResponse = false
        var lastInvalidResponse: String? = null

        // Multi-protocol VIN candidate commands
        val vinCommands = listOf("0902", "22F190", "1A90", "2100", "2101")
        for (cmd in vinCommands) {
            try {
                val response = sendRawCommand(cmd)
                receivedResponse = receivedResponse || response.isMeaningfulObdResponse()
                lastInvalidResponse = response.takeIf { it.isMeaningfulObdResponse() }
                val vin = response
                    .takeIf { it.acknowledgesVinCommand(cmd) }
                    ?.let(CanMultiFrameParser::decodeVin)
                    ?.let(VinValidator::normalize)
                if (vin != null) {
                    persistVerifiedVin(vin)
                    return VinReadResult(
                        outcome = VinReadOutcome.VERIFIED,
                        vin = vin,
                        command = cmd,
                        rawResponse = response,
                        protocol = detectedProtocol,
                        capturedAtMonotonicMs = System.nanoTime() / 1_000_000L,
                    )
                }
            } catch (e: Exception) {
                Log.d(TAG, "VIN probe $cmd failed: ${e.message}")
            }
        }

        // Dedicated CAN Header probe for stubborn/split-bus ECUs (7E0 Engine, 7DF Broadcast, 18DAF110 29-bit)
        val canHeaderProbes = listOf(
            "ATSH 7E0" to "0902",
            "ATSH 7E0" to "22F190",
            "ATSH 7DF" to "0902",
            "ATSH 18DAF110" to "0902",
            "ATSH 18DB33F1" to "0902"
        )
        for ((headerCmd, readCmd) in canHeaderProbes) {
            try {
                sendRawCommand(headerCmd)
                val response = sendRawCommand(readCmd)
                receivedResponse = receivedResponse || response.isMeaningfulObdResponse()
                lastInvalidResponse = response.takeIf { it.isMeaningfulObdResponse() }
                val vin = response
                    .takeIf { it.acknowledgesVinCommand(readCmd) }
                    ?.let(CanMultiFrameParser::decodeVin)
                    ?.let(VinValidator::normalize)
                if (vin != null) {
                    persistVerifiedVin(vin)
                    return VinReadResult(
                        outcome = VinReadOutcome.VERIFIED,
                        vin = vin,
                        command = readCmd,
                        header = headerCmd,
                        rawResponse = response,
                        protocol = detectedProtocol,
                        capturedAtMonotonicMs = System.nanoTime() / 1_000_000L,
                    )
                }
            } catch (_: Exception) {}
            finally {
                runCatching { sendRawCommand("ATSH 7DF") }
            }
        }
        return VinReadResult(
            outcome = if (receivedResponse) VinReadOutcome.INVALID_RESPONSE else VinReadOutcome.NO_RESPONSE,
            rawResponse = lastInvalidResponse,
            protocol = detectedProtocol,
            capturedAtMonotonicMs = System.nanoTime() / 1_000_000L,
        )
    }

    private fun String.isMeaningfulObdResponse(): Boolean {
        val normalized = uppercase().replace(" ", "")
        return isNotBlank() && normalized !in setOf("NODATA", "?", "ERROR", "STOPPED")
    }

    private fun String.acknowledgesVinCommand(command: String): Boolean {
        val compact = uppercase().replace(Regex("[^A-F0-9]"), "")
        val positivePrefix = when (command.uppercase().replace(" ", "")) {
            "0902" -> "4902"
            "22F190" -> "62F190"
            "1A90" -> "5A90"
            "2100" -> "6100"
            "2101" -> "6101"
            else -> return false
        }
        if (compact.contains(positivePrefix)) return true

        // Some gateways return a decoded ASCII VIN without the service bytes.
        // Accept only a standalone canonical VIN line, never arbitrary banner text.
        return lineSequence()
            .map(String::trim)
            .any { VinValidator.normalize(it) != null }
    }

    private fun persistVerifiedVin(vin: String) {
        _vin.value = vin
        Log.i(TAG, "✓ VIN physically verified: $vin")
        val address = targetAddress ?: "unknown"
        val protoEnum = ObdProtocol.values().find {
            it.displayName.equals(detectedProtocol, ignoreCase = true)
        } ?: ObdProtocol.AUTO
        val profile = ElmNegotiator.AdapterProfile(
            chipVersion = adapterVersion,
            isClone = isCloneAdapter,
            isSTN = adapterVersion.contains("STN", true) || adapterVersion.contains("vLinker", true),
            detectedProtocol = protoEnum,
            baseDelayMs = baseDelayMs,
            maxLineLength = maxLineLength,
            vin = vin,
        )
        AdapterFingerprint(context).saveProfile(address, profile, vin)
    }

    @Deprecated("Use readVinFromVehicle() and inspect its physical evidence outcome")
    suspend fun fetchVin(): String {
        return readVinFromVehicle().vin ?: "N/A"
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

    suspend fun readReadinessMonitors(): ReadinessResult? =
        withExclusivePhysicalBus(PhysicalBusOwner.READINESS) { readReadinessMonitorsOwned() }

    private suspend fun readReadinessMonitorsOwned(): ReadinessResult? {
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

            ReadinessResult(milOn, dtcCount, monitors.filter { it.available }).also {
                _readinessResult.value = it
            }
        } catch (_: Exception) { null }
    }

    private val _readinessResult = MutableStateFlow<ReadinessResult?>(null)
    val readinessResult: StateFlow<ReadinessResult?> = _readinessResult.asStateFlow()

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
    suspend fun readMode06Results(): List<Mode06TestResult> =
        withExclusivePhysicalBus(PhysicalBusOwner.MODE_06) { readMode06ResultsOwned() }

    private suspend fun readMode06ResultsOwned(): List<Mode06TestResult> {
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
        currentVanguardSessionId?.let { sessionId ->
            scope.launch {
                runCatching { sessionRecorder.recordMode06Results(sessionId, null, allResults) }
                    .onFailure { Log.w(TAG, "Vanguard Mode 06 record failed: ${it.message}") }
            }
        }
        _statusMessage.value = "Escaneo profundo completado. ${allResults.size} pruebas procesadas."
        Log.i(TAG, "Mode 06: ${allResults.size} test results read")
        return allResults
    }

    // ═══════════════════════════════════════════════
    // MODE $05 — O2 SENSOR MONITORING TEST RESULTS
    // ═══════════════════════════════════════════════

    private val o2SensorParser = O2SensorTestParser()

    private val _o2SensorTests = MutableStateFlow<List<O2SensorTestResult>>(emptyList())
    val o2SensorTests: StateFlow<List<O2SensorTestResult>> = _o2SensorTests.asStateFlow()

    private val _isReadingO2Tests = MutableStateFlow(false)
    val isReadingO2Tests: StateFlow<Boolean> = _isReadingO2Tests.asStateFlow()

    /**
     * Read O2 Sensor Monitoring Test Results (Mode $05).
     * Only applicable to non-CAN (pre-2008) vehicles.
     * CAN vehicles should use Mode $06 instead.
     */
    suspend fun readO2SensorTests(): List<O2SensorTestResult> {
        if (_isReadingO2Tests.value) return _o2SensorTests.value
        _isReadingO2Tests.value = true
        val rawResponses = mutableListOf<String>()

        try {
            _statusMessage.value = "Escaneando sensores O2 (Mode \$05)..."

            // Use quick commands (4 most common sensors × 9 TIDs = 36 commands)
            val commands = o2SensorParser.generateQuickCommands()

            for ((index, cmd) in commands.withIndex()) {
                if (index % 9 == 0) {
                    val sensorNum = (index / 9) + 1
                    _statusMessage.value = "Probando sensor O2 #$sensorNum..."
                }
                try {
                    val response = sendRawCommand(cmd)
                    if (!response.contains("NO DATA", true) &&
                        !response.contains("ERROR", true) &&
                        !response.contains("UNABLE", true)) {
                        rawResponses.add(response)
                    }
                } catch (_: Exception) {}
            }

            val results = o2SensorParser.parse(rawResponses)
            _o2SensorTests.value = results
            _statusMessage.value = "Prueba O2 completada. ${results.size} resultados."
            Log.i(TAG, "Mode 05: ${results.size} O2 sensor test results read")
            return results
        } catch (e: Exception) {
            Log.e(TAG, "Mode 05 O2 Sensor Test failure: ${e.message}")
            _statusMessage.value = "Error en prueba de sensores O2."
            return emptyList()
        } finally {
            _isReadingO2Tests.value = false
        }
    }

    // ═══════════════════════════════════════════════
    // CATEGORIZED DTCs ($03 / $07 / $0A)
    // ═══════════════════════════════════════════════

    private val _categorizedDtcs = MutableStateFlow(CategorizedDtcs())
    val categorizedDtcs: StateFlow<CategorizedDtcs> = _categorizedDtcs.asStateFlow()

    /**
     * Read DTCs separated by type: Confirmed ($03), Pending ($07), Permanent ($0A).
     */
    @Deprecated(
        message = "Use RunDiagnosticScan; categorized lists are a UI projection of the canonical scan report.",
        level = DeprecationLevel.ERROR,
    )
    suspend fun readCategorizedDtcs(): CategorizedDtcs =
        error("Legacy categorized DTC reader retired: use RunDiagnosticScan")

    /**
     * Parse a standard DTC response (Modes $03/$07/$0A) into a list of DTC code strings.
     */
    private fun parseDtcResponseToList(response: String, expectedHeader: String): List<String> {
        val clean = response.replace(Regex("[\\s\\r\\n>]+"), "").uppercase()
        val dtcs = mutableListOf<String>()

        val idx = clean.indexOf(expectedHeader)
        if (idx < 0) return emptyList()

        // Skip header (2 chars) — DTCs start after
        val dtcData = clean.substring(idx + 2)
        var i = 0
        while (i + 3 < dtcData.length) {
            val highByte = dtcData.substring(i, i + 2).toIntOrNull(16) ?: break
            val lowByte = dtcData.substring(i + 2, i + 4).toIntOrNull(16) ?: break

            val firstChar = when ((highByte shr 6) and 0x03) {
                0 -> 'P'; 1 -> 'C'; 2 -> 'B'; 3 -> 'U'; else -> 'P'
            }
            val secondDigit = (highByte shr 4) and 0x03
            val thirdDigit = highByte and 0x0F
            val dtcCode = "$firstChar$secondDigit${String.format("%X", thirdDigit)}${String.format("%02X", lowByte)}"
            if (dtcCode != "P0000") dtcs.add(dtcCode)
            i += 4
        }
        return dtcs
    }

    /**
     * Lookup DTC description from the local database.
     * Returns null if not found.
     */
    private fun dtcLookup(code: String): String? {
        return try {
            DtcDecoder.getLocalDescription(code)
        } catch (_: Exception) { null }
    }

    // ═══════════════════════════════════════════════
    // MODE $08 — CONTROL OF ON-BOARD SYSTEM/TEST
    // ═══════════════════════════════════════════════

    /**
     * Mode $08 — Request control of on-board system, test or component.
     * ⚠️ Manufacturer-specific. Only safe for read-type operations.
     * @param tid Test ID
     * @return Raw response or null
     */
    suspend fun requestOnBoardSystemControl(tid: String): String? {
        Log.d(TAG, "Mode 08: Requesting on-board system control TID=$tid")
        val response = sendRawCommand("08$tid")
        val clean = response.replace(Regex("[\\s\\r\\n>]+"), "").uppercase()
        if (clean.contains("NODATA") || clean.contains("ERROR")) return null
        val idx = clean.indexOf("48")
        if (idx < 0) return null
        return clean.substring(idx + 2)
    }

    // ═══════════════════════════════════════════════
    // MODE $09 — VEHICLE INFORMATION (EXTENDED)
    // ═══════════════════════════════════════════════

    /**
     * Read all available Mode $09 InfoTypes.
     * Beyond VIN, includes Calibration IDs, CVN, ECU name, etc.
     */
    suspend fun readAllVehicleInfo(): Map<String, String> {
        val info = mutableMapOf<String, String>()
        val infoTypes = mapOf(
            "02" to "VIN",
            "04" to "Calibration ID",
            "06" to "Calibration Verification Number (CVN)",
            "08" to "In-use Performance Tracking (Spark)",
            "0A" to "ECU Name",
            "0B" to "In-use Performance Tracking (Compression)"
        )

        for ((infotype, label) in infoTypes) {
            try {
                val response = sendRawCommand("09$infotype")
                val clean = response.replace(Regex("[\\s\\r\\n>]+"), "").uppercase()
                val marker = "49${infotype.uppercase()}"
                val idx = clean.indexOf(marker)
                if (idx >= 0) {
                    val data = clean.substring(idx + marker.length)
                    // Decode as ASCII for text fields, hex for numeric
                    val decoded = if (infotype in listOf("02", "04", "0A")) {
                        hexToAsciiSafe(data)
                    } else {
                        data
                    }
                    if (decoded.isNotBlank()) info[label] = decoded
                }
            } catch (_: Exception) {}
        }

        return info
    }

    private fun hexToAsciiSafe(hex: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i + 1 < hex.length) {
            val byte = hex.substring(i, i + 2).toIntOrNull(16) ?: break
            if (byte in 32..126) sb.append(byte.toChar())
            i += 2
        }
        return sb.toString().trim()
    }

    // ═══════════════════════════════════════════════
    // MANUFACTURER-SPECIFIC MODES ($B0-$BF, $D0-$DF, $EA-$FF)
    // ═══════════════════════════════════════════════

    /**
     * Send a raw manufacturer-specific diagnostic command.
     * Used for OEM-specific diagnostics in ranges:
     *   $B0-$BF: Manufacturer-defined enhanced services
     *   $D0-$DF: Manufacturer-defined enhanced services
     *   $EA-$FF: System supplier specific
     *
     * @param serviceId The service byte (e.g. "B0", "D5", "F1")
     * @param subFunction Optional sub-function or parameter bytes
     * @return Raw hex response or null if no response / error
     */
    suspend fun sendManufacturerCommand(serviceId: String, subFunction: String = ""): String? {
        Log.w(TAG, "Blocked unsourced OEM command SID=$serviceId sub=$subFunction")
        _statusMessage.value =
            "Comando OEM bloqueado: requiere paquete firmado, ECU objetivo y respuesta esperada verificables."
        return null
    }

    /**
     * Probe manufacturer-specific modes to discover which ones the ECU supports.
     * Sends each SID with no sub-function and checks for any response.
     */
    suspend fun probeManufacturerModes(): Map<String, Boolean> {
        _statusMessage.value =
            "Sondeo OEM deshabilitado: las capacidades solo se cargan desde paquetes verificados."
        return emptyMap()
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

    @Deprecated(
        message = "Legacy parallel module probing retired; use readProfessionalDtcScan().",
        level = DeprecationLevel.ERROR,
    )
    suspend fun scanModules(): List<NetworkModule> =
        error("Legacy module scanner retired: use readProfessionalDtcScan()")

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
    suspend fun readOdometer(): Float? {
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

        return null
    }

    // ═══════════════════════════════════════════════════
    // INITIALIZATION & INFRASTRUCTURE
    // ═══════════════════════════════════════════════════

    private suspend fun initializeAdapter() {
        val t = transport ?: throw ObdConnectionException("Transport no disponible")
        val address = targetAddress ?: "unknown"
        val activeVin = _vin.value?.takeIf { it.isNotBlank() && it != "N/A" }
        val fingerprint = AdapterFingerprint(context)
        val cachedProfile = fingerprint.getProfile(address, activeVin)

        Log.i(TAG, "── INIT ADAPTER START ── (cached=${cachedProfile != null}, vin=$activeVin)")

        val negotiator = ElmNegotiator(t)
        var profile: ElmNegotiator.AdapterProfile? = null

        if (cachedProfile != null && cachedProfile.detectedProtocol != ObdProtocol.AUTO) {
            profile = negotiator.negotiateFastPath(cachedProfile) { status ->
                _statusMessage.value = status
            }
        }

        if (profile == null) {
            profile = negotiator.negotiate(
                hintProtocol = cachedProfile?.detectedProtocol ?: ObdProtocol.AUTO
            ) { status ->
                _statusMessage.value = status
            }
        }

        // Save for next time
        fingerprint.saveProfile(address, profile, activeVin)
        runCatching {
            KnownGoodAdapterStore.recordSuccess(
                fingerprint = address,
                transportType = if (targetAddress?.startsWith("BLE_") == true) TransportType.BLUETOOTH_LE else TransportType.BLUETOOTH_CLASSIC,
                connectMethod = ConnectMethod.REFLECTION_CH1,
                protocol = profile.detectedProtocol.name,
                connectDurationMs = profile.baseDelayMs,
                preferredInitRecipe = profile.recipeId
            )
        }

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
            // ISO 13400 routing activation response echoes the tester address at
            // payload bytes 0..1 and provides the entity logical address at 2..3.
            doIpTargetLogicalAddress = ((respBytes[10].toInt() and 0xFF) shl 8) or
                (respBytes[11].toInt() and 0xFF)
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

    private fun wrapDoIpDiagnostics(
        targetLogicalAddress: Int,
        udsHex: String,
    ): ByteArray {
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
        
        packet[8] = ((doIpSourceLogicalAddress ushr 8) and 0xFF).toByte()
        packet[9] = (doIpSourceLogicalAddress and 0xFF).toByte()
        packet[10] = ((targetLogicalAddress ushr 8) and 0xFF).toByte()
        packet[11] = (targetLogicalAddress and 0xFF).toByte()
        
        System.arraycopy(udsBytes, 0, packet, 12, udsBytes.size)
        return packet
    }

    private fun unwrapDoIpDiagnostics(
        doipBytes: ByteArray?,
        expectedSourceLogicalAddress: Int? = null,
        expectedTargetLogicalAddress: Int? = null,
    ): String {
        if (doipBytes == null || doipBytes.size < 12) return ""
        val payloadType = ((doipBytes[2].toInt() and 0xFF) shl 8) or (doipBytes[3].toInt() and 0xFF)
        if (payloadType != 0x8001) return ""
        val sourceLogicalAddress = ((doipBytes[8].toInt() and 0xFF) shl 8) or (doipBytes[9].toInt() and 0xFF)
        val targetLogicalAddress = ((doipBytes[10].toInt() and 0xFF) shl 8) or (doipBytes[11].toInt() and 0xFF)
        if (expectedSourceLogicalAddress != null && sourceLogicalAddress != expectedSourceLogicalAddress) return ""
        if (expectedTargetLogicalAddress != null && targetLogicalAddress != expectedTargetLogicalAddress) return ""
        
        val udsLength = doipBytes.size - 12
        if (udsLength <= 0) return ""
        
        val sb = StringBuilder()
        for (i in 12 until doipBytes.size) {
            sb.append("%02X".format(doipBytes[i]))
        }
        return sb.toString()
    }

    suspend fun sendDoIpDiagnostic(
        targetLogicalAddress: Int,
        payload: String,
        timeoutMs: Long = 1_500L,
    ): String = communicationMutex.withLock {
        require(isDoIpMode) { "DoIP transport is not active" }
        require(targetLogicalAddress in 0..0xFFFF) { "Invalid DoIP logical target" }
        val t = transport ?: throw ObdConnectionException("Transport no disponible para DoIP")
        t.write(wrapDoIpDiagnostics(targetLogicalAddress, payload))
        unwrapDoIpDiagnostics(
            doipBytes = t.read(4096, timeoutMs),
            expectedSourceLogicalAddress = targetLogicalAddress,
            expectedTargetLogicalAddress = doIpSourceLogicalAddress,
        )
    }

    private suspend fun readResponseBytes(timeoutMs: Long = 1500L): ByteArray? =
        withContext(Dispatchers.IO) {
            val t = transport ?: return@withContext null
            t.read(1024, timeoutMs)
        }

    /** Continue receiving after UDS NRC 0x78 without retransmitting the request. */
    private suspend fun awaitDiagnosticResponseWithoutRequest(
        timeoutMs: Long,
        doIpTarget: Int? = null,
    ): String =
        communicationMutex.withLock {
            if (isDoIpMode) {
                unwrapDoIpDiagnostics(
                    doipBytes = readResponseBytes(timeoutMs),
                    expectedSourceLogicalAddress = doIpTarget,
                    expectedTargetLogicalAddress = doIpSourceLogicalAddress,
                )
            } else {
                readResponse(timeoutMs)
            }
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
            throw ObdConnectionException(
                "Comando DoIP bloqueado: falta destino lógico explícito por solicitud. " +
                    "Use sendDoIpDiagnostic(targetLogicalAddress, payload).",
            )
        }

        return communicationMutex.withLock {
            withContext(Dispatchers.IO) {
                val t = transport ?: throw ObdConnectionException("Transport no disponible")
                Log.v(TAG, "TX (Direct): '$command' (timeout=${timeoutMs}ms)")
                runCatching { t.drain() }
                t.write("$command\r".toByteArray())
                val resp = readResponse(timeoutMs)
                Log.v(TAG, "RX (Direct): '$resp' (${resp.length} chars)")
                delay(baseDelayMs) // Elysium Vanguard: adaptive delay
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
    suspend fun verifySafetyForProAction(test: ActiveTest): ActiveDiagnosticSafetyDecision {
        // Refresh only the observations required by the policy. A failed read is
        // preserved as UNKNOWN and can never silently become zero/safe.
        test.safetyEvidenceRequirements.forEach { requirement ->
            val command = requirement.signalAliases.firstOrNull { alias ->
                alias.matches(Regex("^[0-9A-Fa-f]{4,}$")) || alias.equals("ATRV", true)
            }
            if (command != null) refreshSafetyTelemetry(command)
        }
        val decision = ActiveDiagnosticSafetyKernel.evaluate(
            test = test,
            telemetry = telemetrySamples.value,
            nowMonotonicMs = System.nanoTime() / 1_000_000L,
            capabilityAuthorization = ActiveDiagnosticCapabilityRegistry.authorize(
                test = test,
                context = vehicleCapabilityContext.copy(
                    ecuFamily = _ecuName.value,
                    ecuAddress = test.targetAddress,
                    calibrationId = _calibrationId.value,
                ),
            ),
        )
        if (!decision.allowed) {
            _statusMessage.value = decision.blockingReasons.joinToString(separator = " ")
        }
        return decision
    }

    private suspend fun refreshSafetyTelemetry(pid: String) {
        try {
            val response = sendRawCommand(pid)
            val value = parsePidResponse(pid, response)
            if (value != null) {
                updateLiveData(pid, value)
            } else {
                markSensorState(pid, stateForFailedResponse(response), rawValue = response)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            markSensorState(pid, SensorValueState.EcuNoResponse, rawValue = error.message)
        }
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
        activeTestJob?.cancel()
        activeTestJob = scope.launch(Dispatchers.IO) {
            withExclusivePhysicalBus(PhysicalBusOwner.ACTIVE_TEST) {
                runActiveTestOwned(test)
            }
        }
    }

    /** Synchronous active-test entry point for callers that must retain terminal proof. */
    suspend fun executeActiveTestForEvair(test: ActiveTest): ActiveTestStatus {
        withExclusivePhysicalBus(PhysicalBusOwner.ACTIVE_TEST) {
            runActiveTestOwned(test)
        }
        return _activeTestStatus.value
    }

    private suspend fun runActiveTestOwned(test: ActiveTest) {
        var activationRequested = false
        var activationAcknowledged = false
        var completedMonitoring = false
        var finalData = emptyMap<String, Float>()
        try {
                _activeTestStatus.value = ActiveTestStatus(
                    isActive = false,
                    message = "Verificando seguridad: ${test.name}...",
                    progress = 0.05f,
                    testId = test.id,
                    phase = ActiveDiagnosticTestPhase.PRECHECK,
                )

                if (_state.value != ObdState.CONNECTED) {
                    _activeTestStatus.value = ActiveTestStatus(
                        isActive = false,
                        message = "Conecta un vehículo real antes de ejecutar pruebas activas.",
                        progress = 0f,
                        testId = test.id
                    )
                    return
                }

                val safetyDecision = verifySafetyForProAction(test)
                if (!safetyDecision.allowed) {
                    _activeTestStatus.value = ActiveTestStatus(
                        isActive = false,
                        message = safetyDecision.blockingReasons.joinToString(" "),
                        testId = test.id,
                        phase = ActiveDiagnosticTestPhase.ABORTED,
                    )
                    return
                }

                _activeTestStatus.value = _activeTestStatus.value.copy(
                    message = "Condiciones verificadas.",
                    progress = 0.1f,
                    phase = ActiveDiagnosticTestPhase.READY,
                )

                // Activation commands are never retried: an ambiguous response
                // may still mean that the actuator moved.
                _statusMessage.value = "Enviando comando de activación: ${test.startCommand}"
                _activeTestStatus.value = _activeTestStatus.value.copy(
                    phase = ActiveDiagnosticTestPhase.ACTIVATION_REQUESTED,
                )
                activationRequested = true
                val startResp = sendRawCommand(test.startCommand)
                activationAcknowledged = isTypedPositiveResponse(test.startCommand, startResp)
                if (!activationAcknowledged) {
                    _activeTestStatus.value = ActiveTestStatus(
                        isActive = false,
                        message = "Activación no confirmada por una PDU positiva válida.",
                        testId = test.id,
                        phase = ActiveDiagnosticTestPhase.ABORTED,
                    )
                    return
                }

                _activeTestStatus.value = ActiveTestStatus(
                    isActive = true,
                    message = "PRUEBA ACTIVA: ${test.name}",
                    progress = 0.5f,
                    testId = test.id,
                    phase = ActiveDiagnosticTestPhase.ACTIVE,
                )

                // Monitor loop
                val startTime = System.currentTimeMillis()
                var lastTesterPresentTime = startTime
                while (System.currentTimeMillis() - startTime < test.durationMs) {
                    if (!currentCoroutineContext().isActive) break

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
                completedMonitoring = true

        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            _activeTestStatus.value = ActiveTestStatus(
                isActive = false,
                message = "Excepción en prueba: ${e.message}",
                testId = test.id,
                phase = ActiveDiagnosticTestPhase.ABORTED,
            )
        } finally {
            if (activationRequested) {
                withContext(NonCancellable) {
                    stopAndVerifyActiveTest(
                        test = test,
                        activationAcknowledged = activationAcknowledged,
                        completedMonitoring = completedMonitoring,
                        finalData = finalData,
                    )
                }
            }
        }
    }

    private fun isTypedPositiveResponse(command: String, rawResponse: String): Boolean {
        val requestedService = command.take(2).toIntOrNull(16) ?: return false
        val expectedPositiveService = (requestedService + 0x40) and 0xFF
        return DiagnosticPduDecoder.decodeResponses(
            rawResponse = rawResponse,
            expectedPositiveService = expectedPositiveService,
            requestedService = requestedService,
        ).any { it is ProtocolResponse.Positive }
    }

    private suspend fun stopAndVerifyActiveTest(
        test: ActiveTest,
        activationAcknowledged: Boolean,
        completedMonitoring: Boolean,
        finalData: Map<String, Float>,
    ) {
        _activeTestStatus.value = _activeTestStatus.value.copy(
            isActive = activationAcknowledged,
            message = "Deteniendo prueba y verificando respuesta...",
            phase = ActiveDiagnosticTestPhase.STOP_REQUESTED,
        )
        _statusMessage.value = "Deteniendo prueba: ${test.stopCommand}"
        val stopResponse = runCatching { sendRawCommand(test.stopCommand) }.getOrNull()
        val stopVerified = stopResponse != null && isTypedPositiveResponse(test.stopCommand, stopResponse)
        _activeTestStatus.value = if (stopVerified) {
            ActiveTestStatus(
                isActive = false,
                message = if (completedMonitoring) {
                    "Prueba completada; detención verificada por ECU."
                } else {
                    "Prueba interrumpida; detención verificada por ECU."
                },
                progress = if (completedMonitoring) 1.0f else _activeTestStatus.value.progress,
                testId = test.id,
                currentValues = finalData,
                phase = ActiveDiagnosticTestPhase.STOP_VERIFIED,
                stopVerified = true,
            )
        } else {
            ActiveTestStatus(
                isActive = activationAcknowledged,
                message = "CRÍTICO: detención no confirmada. Apaga el contacto y verifica físicamente el actuador.",
                progress = _activeTestStatus.value.progress,
                testId = test.id,
                currentValues = finalData,
                phase = ActiveDiagnosticTestPhase.STOP_FAILED,
                stopVerified = false,
            )
        }
    }

    fun stopActiveTest() {
        _activeTestStatus.value = _activeTestStatus.value.copy(
            message = "Solicitando detención segura...",
            phase = ActiveDiagnosticTestPhase.STOP_REQUESTED,
        )
        activeTestJob?.cancel()
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
        // e.g. "A6" means auto-detected protocol 6, "AD" means auto-detected CAN FD 11bit
        val clean = response.uppercase().trim().removePrefix("A").trim()
        val matchedProtocol = ObdProtocol.values().firstOrNull { it.atspCode.equals(clean, ignoreCase = true) }
        if (matchedProtocol != null && matchedProtocol != ObdProtocol.AUTO) {
            return matchedProtocol.displayName
        }
        return when (clean) {
            "6" -> "ISO 15765-4 (CAN 11/500)"
            "7" -> "ISO 15765-4 (CAN 29/500)"
            "8" -> "ISO 15765-4 (CAN 11/250)"
            "9" -> "ISO 15765-4 (CAN 29/250)"
            "A" -> "SAE J1939 CAN 29bit 250K"
            "B" -> "User CAN 11bit"
            "C" -> "User CAN 29bit"
            "D" -> "CAN FD 11bit 500K/2M"
            "E" -> "CAN FD 29bit 500K/2M"
            "F" -> "DoIP ISO 13400 (Ethernet)"
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
                    val maxAttempts = if (command.retryPolicy == RetryPolicy.NEVER_AFTER_WRITE) 1 else 2

                    while (!success && attempts < maxAttempts && isRunning) {
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
                                    } else throw ObdConnectionException(
                                        "Cola DoIP bloqueada: el comando no incluye destino lógico explícito.",
                                    )
                                } else {
                                    runCatching { t.drain() }
                                    t.write("${command.query}\r".toByteArray())
                                    readResponse(timeoutMs = 2000L)
                                }

                                val clean = response.trim().uppercase()
                                val isHardwareOrBusError = clean.contains("CAN ERROR") || clean.contains("BUS ERROR") ||
                                        clean.contains("STOPPED") || clean.contains("BUS BUSY") || clean.contains("FB ERROR") ||
                                        clean.contains("BUFFER FULL") || clean.contains("UNABLE TO CONNECT") ||
                                        clean.contains("ERR1") || clean.contains("ERR2") || clean == "?"

                                val isNoData = clean == "NO DATA" || clean == "NODATA"

                                if (!isHardwareOrBusError && response.isNotBlank()) {
                                    success = true
                                    consecutiveErrors = 0
                                    lastHeartbeatTime = System.currentTimeMillis()
                                    val latency = System.currentTimeMillis() - startTime
                                    updateQos(latency, true)
                                    recordCommandLog(command.query, response, success = !isNoData, latencyMs = latency, retryCount = attempts)
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
                        val latency = System.currentTimeMillis() - startTime
                        updateQos(latency, false)
                        trafficListener?.onError(command.query, "Timeout after $attempts attempts")
                        recordCommandLog(
                            command = command.query,
                            rawResponse = null,
                            success = false,
                            latencyMs = latency,
                            retryCount = attempts,
                            errorType = EcuFailureType.ECU_TIMEOUT
                        )
                        recordClassifiedFailure(command.query, null, timeoutMs = 2000L, latencyMs = latency)

                        if (consecutiveErrors >= 3 && !isSelfHealing) {
                            scope.launch { attemptSelfHealing("command timeouts") }
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
        val activeOwner = physicalBusActor.currentOwner
        val callerOwner = currentCoroutineContext()[PhysicalBusLeaseContext]?.owner
        if (!PhysicalBusLeasePolicy.allows(activeOwner, callerOwner)) {
            throw ObdBusBusyException(activeOwner)
        }
        return withTimeout(15000) { // Safety timeout for all raw commands
            val deferred = CompletableDeferred<String>()
            enqueueCommand(command, priority, { deferred.complete(it) }, { deferred.completeExceptionally(it) })
            deferred.await()
        }
    }

    /** Restricted physical read surface for EVAIR. Arbitrary adapter commands are not accepted. */
    suspend fun readPidsForEvair(pids: Set<String>): List<PhysicalPidReadEvidence> {
        require(pids.isNotEmpty()) { "At least one PID is required" }
        require(_state.value == ObdState.CONNECTED) { "OBD vehicle is not connected" }
        val canonical = pids.map { it.trim().uppercase() }.distinct()
        canonical.forEach { command ->
            require(command.matches(Regex("^[0-9A-F]{4,6}$"))) { "Malformed PID command: $command" }
            val mode = command.take(2)
            val pid = command.drop(2)
            require(PidRegistry.getPid(mode, pid) != null) {
                "PID is not registered for physical reading: $command"
            }
        }
        return withExclusivePhysicalBus(PhysicalBusOwner.EVAIR_READ) {
            canonical.map { command ->
                val response = sendRawCommand(command)
                val requestedService = command.take(2).toInt(16)
                val expectedPrefix = "%02X%s".format(
                    (requestedService + 0x40) and 0xFF,
                    command.drop(2),
                )
                val normalized = CanMultiFrameParser.parse(response)
                    .replace(Regex("[^0-9A-Fa-f]"), "")
                    .uppercase()
                PhysicalPidReadEvidence(
                    command = command,
                    rawResponse = response,
                    acknowledgedByEcu = normalized.contains(expectedPrefix),
                    capturedAtMonotonicMs = System.nanoTime() / 1_000_000L,
                )
            }
        }
    }

    internal suspend fun executeTerminalRead(command: String): String =
        withExclusivePhysicalBus(PhysicalBusOwner.TERMINAL_READ) {
            val protocolBefore = detectedProtocol
            val targetBefore = targetAddress
            val response = sendRawCommand(command)
            check(detectedProtocol == protocolBefore && targetAddress == targetBefore) {
                "Terminal read altered adapter/session state; result rejected"
            }
            response
        }

    /**
     * Sends a command directly to the transport bypassing the queue.
     * ONLY use this for keep-alive or low-level negotiation.
     */
    suspend fun sendKeepAliveDirectly(command: String): String {
        val activeOwner = physicalBusActor.currentOwner
        val callerOwner = currentCoroutineContext()[PhysicalBusLeaseContext]?.owner
        if (!PhysicalBusLeasePolicy.allows(activeOwner, callerOwner)) return ""
        return sendCommandDirectly(command, timeoutMs = 1000L)
    }

    fun liveDataSilenceMs(nowMs: Long = System.currentTimeMillis()): Long {
        val last = lastLiveDataUpdateMs
        return if (last <= 0L) Long.MAX_VALUE else nowMs - last
    }

    suspend fun recoverFrozenLink(reason: String): Boolean {
        val now = System.currentTimeMillis()
        if (_state.value != ObdState.CONNECTED || !isRunning) return false
        if (now - lastRecoveryAttemptMs < RECOVERY_COOLDOWN_MS) return false
        return attemptSelfHealing(reason)
    }

    private suspend fun attemptSelfHealing(reason: String = "link instability"): Boolean {
        if (isSelfHealing || !isRunning) return false
        isSelfHealing = true
        lastRecoveryAttemptMs = System.currentTimeMillis()
        val wasPollingPaused = isPollingPaused
        _statusMessage.value = "Enlace inestable. Recuperando telemetría..."
        Log.w(TAG, "Self-healing started: $reason")

        try {
            commandQueue.clear()
            pollingJob?.cancelAndJoin()
            currentJob?.cancelAndJoin()
            keepAliveManager.stop()
            drainInput()
            transport?.reconnect()
            delay(700)
            initializeAdapter()
            consecutiveErrors = 0
            recoveryFailureCount = 0
            val recoveredAt = System.currentTimeMillis()
            lastHeartbeatTime = recoveredAt
            lastLiveDataUpdateMs = recoveredAt
            _state.value = ObdState.CONNECTED
            _statusMessage.value = "Telemetría recuperada. Polling activo."
            startQueueProcessor()
            if (!wasPollingPaused) startLivePolling()
            keepAliveManager.start(scope)
            Log.i(TAG, "Self-healing completed successfully")
            return true
        } catch (e: Exception) {
            recoveryFailureCount++
            _statusMessage.value = "Recuperación OBD falló: ${e.message ?: "sin respuesta"}"
            consecutiveErrors = 0
            Log.e(TAG, "Self-healing failed ($recoveryFailureCount): $reason", e)
            if (recoveryFailureCount >= 2) {
                isRunning = false
                _state.value = ObdState.ERROR
                runCatching { transport?.disconnect() }
            }
            return false
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
                    attemptSelfHealing("heartbeat silence")
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
            // Offline diagnostic mode — provide meaningful feedback from real connection state.
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
        transportGenerationId.incrementAndGet()
        val sessionIdToFinish = currentVanguardSessionId
        if (sessionIdToFinish != null) {
            val modulesJson = networkModulesJson()
            val dtcsJson = org.json.JSONArray(_allDetectedDtcs.value.toList()).toString()
            val mode06Json = mode06Json()
            val derivedJson = derivedStatesJson()
            scope.launch {
                runCatching {
                    sessionRecorder.finishSession(
                        ObdSessionFinishContext(
                            sessionId = sessionIdToFinish,
                            ecuModulesJson = modulesJson,
                            dtcsJson = dtcsJson,
                            mode06Json = mode06Json,
                            derivedMetricsJson = derivedJson,
                            reconnectCount = recoveryFailureCount
                        )
                    )
                }.onFailure { Log.w(TAG, "Vanguard session finish failed: ${it.message}") }
            }
            currentVanguardSessionId = null
        }
        isRunning = false
        currentJob?.cancel()
        pollingJob?.cancel()
        heartbeatJob?.cancel()
        activeTestJob?.cancel()
        keepAliveManager.stop()
        
        val activeTransport = transport
        transport = null
        scope.launch(Dispatchers.IO) {
            transportLifecycleMutex.withLock {
                try {
                    activeTransport?.disconnect()
                } catch (e: Exception) {
                    Log.w(TAG, "Error during transport disconnect: ${e.message}")
                }
            }
        }
        
        _state.value = ObdState.DISCONNECTED
        _statusMessage.value = "Desconectado"
        detectedProtocol = ""
        adapterVersion = ""
        _vin.value = null
        _liveData.value = emptyMap()
        _liveSensorStates.value = emptyMap()
        clearVehicleScopedTruth()
        _activeTestStatus.value = ActiveTestStatus()
        sensorSmoother.resetAll()
        commandQueue.clear()
        
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

    suspend fun disconnectSequentially() {
        transportGenerationId.incrementAndGet()
        val sessionIdToFinish = currentVanguardSessionId
        if (sessionIdToFinish != null) {
            val modulesJson = networkModulesJson()
            val dtcsJson = org.json.JSONArray(_allDetectedDtcs.value.toList()).toString()
            val mode06Json = mode06Json()
            val derivedJson = derivedStatesJson()
            runCatching {
                sessionRecorder.finishSession(
                    ObdSessionFinishContext(
                        sessionId = sessionIdToFinish,
                        ecuModulesJson = modulesJson,
                        dtcsJson = dtcsJson,
                        mode06Json = mode06Json,
                        derivedMetricsJson = derivedJson,
                        reconnectCount = recoveryFailureCount
                    )
                )
            }.onFailure { Log.w(TAG, "Vanguard session finish failed: ${it.message}") }
            currentVanguardSessionId = null
        }
        isRunning = false
        currentJob?.cancel()
        pollingJob?.cancel()
        heartbeatJob?.cancel()
        activeTestJob?.cancel()
        keepAliveManager.stop()

        transportLifecycleMutex.withLock {
            val activeTransport = transport
            transport = null
            try {
                activeTransport?.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing transport: ${e.message}")
            }
        }

        _state.value = ObdState.DISCONNECTED
        _statusMessage.value = "Desconectado"
        detectedProtocol = ""
        adapterVersion = ""
        _vin.value = null
        _liveData.value = emptyMap()
        _liveSensorStates.value = emptyMap()
        clearVehicleScopedTruth()
        _activeTestStatus.value = ActiveTestStatus()
        sensorSmoother.resetAll()
        commandQueue.clear()

        _calibrationId.value = null
        _ecuName.value = null
        lastSpeedKph = 0f
        lastSpeedTimestampMs = 0L
        tripDistanceKm = 0.0
        tripFuelUsedL = 0.0
        tripStartTimeMs = 0L
        speedAccumulator = 0.0
        speedSampleCount = 0
    }

    private fun clearVehicleScopedTruth() {
        _telemetrySamples.value = emptyMap()
        _freezeFrame.value = emptyMap()
        _allDetectedDtcs.value = emptySet()
        _lastDtcScanReport.value = null
        _mode06Results.value = emptyList()
        _readinessResult.value = null
        _activeTestEvidence.value = emptyList()
    }

    private fun networkModulesJson(): String {
        val array = org.json.JSONArray()
        _networkTopology.value.forEach { module ->
            array.put(
                org.json.JSONObject()
                    .put("id", module.id)
                    .put("name", module.name)
                    .put("isAlive", module.isAlive)
                    .put("latencyMs", module.latencyMs)
                    .put("dtcCount", module.dtcs.size)
                    .put("protocol", module.protocolDetected)
            )
        }
        return array.toString()
    }

    private fun mode06Json(): String {
        val array = org.json.JSONArray()
        _mode06Results.value.forEach { result ->
            array.put(
                org.json.JSONObject()
                    .put("mid", result.mid)
                    .put("tid", result.tid)
                    .put("value", result.value)
                    .put("minLimit", result.minLimit)
                    .put("maxLimit", result.maxLimit)
                    .put("unit", result.unit)
                    .put("passed", result.passed)
                    .put("componentName", result.componentName)
                    .put("testName", result.testName)
                    .put("severity", result.severity.name)
            )
        }
        return array.toString()
    }

    private fun derivedStatesJson(): String {
        val array = org.json.JSONArray()
        _liveSensorStates.value
            .filterKeys { it.startsWith("CALC_") }
            .forEach { (key, state) ->
                array.put(
                    org.json.JSONObject()
                        .put("id", key)
                        .put("state", state.stateName)
                        .put("value", state.numericValueOrNull)
                )
            }
        return array.toString()
    }

    companion object {
        private const val TAG = "EV_OBD"
        private const val MAX_CONNECT_ATTEMPTS = 3
        private const val RECOVERY_COOLDOWN_MS = 15_000L
    }
}

enum class RetryPolicy {
    SAFE_IDEMPOTENT,
    RETRY_ON_TRANSPORT_BEFORE_WRITE_ONLY,
    NEVER_AFTER_WRITE
}

data class ObdCommand(
    val query: String,
    val priority: Int = 1,
    val onSuccess: (String) -> Unit = {},
    val onError: (Exception) -> Unit = {},
    val retryPolicy: RetryPolicy = RetryPolicy.SAFE_IDEMPOTENT
)
class ObdCommandQueue {
    private val queue = mutableListOf<ObdCommand>()
    @Synchronized fun enqueue(c: ObdCommand) { queue.add(c); queue.sortByDescending { it.priority } }
    @Synchronized fun dequeue(): ObdCommand? = if (queue.isEmpty()) null else queue.removeAt(0)
    @Synchronized fun clear() { queue.clear() }
}
