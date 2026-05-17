package com.elysium369.meet.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.core.obd.*
import com.elysium369.meet.data.supabase.SubscriptionRepository
import io.github.jan.supabase.gotrue.auth
import com.elysium369.meet.data.supabase.Vehicle
import com.elysium369.meet.data.supabase.VehicleRepository
import com.elysium369.meet.data.supabase.SupabaseManager
import com.elysium369.meet.data.supabase.SessionLogRepository
import com.elysium369.meet.data.supabase.DiagnosticSession
import com.elysium369.meet.data.local.dao.TripDao
import com.elysium369.meet.data.local.dao.MaintenanceAlertDao
import com.elysium369.meet.data.local.dao.CustomPidDao
import com.elysium369.meet.data.local.entities.TripEntity
import com.elysium369.meet.data.local.entities.MaintenanceAlertEntity
import com.elysium369.meet.data.local.entities.CustomPidEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.elysium369.meet.core.alerts.AlertManager
import javax.inject.Inject
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import com.elysium369.meet.data.local.entities.DtcDefinitionEntity
import com.elysium369.meet.data.local.entities.DtcEventEntity
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.elysium369.meet.core.sync.SyncWorker
import com.elysium369.meet.core.livelink.LiveLinkServer
import com.elysium369.meet.core.livelink.TelemetrySnapshot

@HiltViewModel
class ObdViewModel @Inject constructor(
    private val obdSession: ObdSession,
    private val vehicleRepository: VehicleRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val sessionLogRepository: SessionLogRepository,
    private val geminiDiagnostic: com.elysium369.meet.core.ai.GeminiDiagnostic,
    private val tripManager: com.elysium369.meet.core.trips.TripManager,
    private val tripDao: TripDao,
    private val maintenanceAlertDao: MaintenanceAlertDao,
    private val customPidDao: CustomPidDao,
    private val dtcDao: com.elysium369.meet.data.local.dao.DtcDao,
    private val dtcDefinitionDao: com.elysium369.meet.data.local.dao.DtcDefinitionDao,
    @ApplicationContext private val context: Context,
    private val reportGenerator: com.elysium369.meet.core.export.ReportGenerator,
    private val diagnosticManager: com.elysium369.meet.core.obd.AdvancedDiagnosticManager,
    private val localExpertSystem: com.elysium369.meet.core.obd.LocalExpertSystem,
    private val alertManager: AlertManager,
    private val predictiveHealthEngine: com.elysium369.meet.core.health.PredictiveHealthEngine
) : ViewModel() {

    val connectionState: StateFlow<ObdState> = obdSession.state
    val statusMessage: StateFlow<String> = obdSession.statusMessage
    
    // --- Force Clone Mode ---
    private val _forceCloneMode = MutableStateFlow(false)
    val forceCloneMode: StateFlow<Boolean> = _forceCloneMode.asStateFlow()

    // ── LiveLink (Opt-in remote telemetry) ──
    private var _liveLinkServer: LiveLinkServer? = null
    fun attachLiveLinkServer(server: LiveLinkServer) { _liveLinkServer = server }
    fun detachLiveLinkServer() { _liveLinkServer = null }
    
    // isAdapterPro respects forceCloneMode override
    val isAdapterPro: StateFlow<Boolean> = combine(
        obdSession.isAdapterPro,
        _forceCloneMode
    ) { realPro, forceClone ->
        if (forceClone) false else realPro
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    
    // --- AI Configuration ---
    data class AiConfig(
        val provider: String = "gemini",  // gemini, openai, anthropic, ollama, custom
        val apiKey: String = "",
        val endpoint: String = "",
        val modelName: String = ""
    )
    private val _aiConfig = MutableStateFlow(AiConfig())
    val aiConfig: StateFlow<AiConfig> = _aiConfig.asStateFlow()
    
    private val _selectedVehicle = MutableStateFlow<Vehicle?>(null)
    val selectedVehicle: StateFlow<Vehicle?> = _selectedVehicle.asStateFlow()

    fun selectVehicle(vehicle: Vehicle?) {
        _selectedVehicle.value = vehicle
        // Reset sensor smoothers when switching vehicles to prevent cross-vehicle data contamination
        sensorSmoother.resetAll()
        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            .edit().putString("selected_vehicle_id", vehicle?.id).apply()
    }

    private val _liveData = MutableStateFlow<Map<String, Float>>(emptyMap())
    val liveData: StateFlow<Map<String, Float>> = _liveData.asStateFlow()

    // Smooth sensor interpolation — eliminates erratic jumps from raw ELM327 readings
    private val sensorSmoother = SensorSmootherManager()

    private var currentSessionId: String = UUID.randomUUID().toString()

    val activeDtcEvents: StateFlow<List<DtcEventEntity>> = _selectedVehicle.flatMapLatest { vehicle ->
        vehicle?.let { 
            dtcDao.getUnresolvedDtcsForVehicle(it.id)
                .map { list -> list.filter { it.status == "ACTIVE" } }
        } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pendingDtcEvents: StateFlow<List<DtcEventEntity>> = _selectedVehicle.flatMapLatest { vehicle ->
        vehicle?.let { 
            dtcDao.getUnresolvedDtcsForVehicle(it.id)
                .map { list -> list.filter { it.status == "PENDING" } }
        } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val permanentDtcEvents: StateFlow<List<DtcEventEntity>> = _selectedVehicle.flatMapLatest { vehicle ->
        vehicle?.let { 
            dtcDao.getUnresolvedDtcsForVehicle(it.id)
                .map { list -> list.filter { it.status == "PERMANENT" } }
        } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Backwards compatibility for logic that only needs the strings
    val activeDtcs: StateFlow<List<String>> = activeDtcEvents.map { list -> list.map { it.code } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val pendingDtcs: StateFlow<List<String>> = pendingDtcEvents.map { list -> list.map { it.code } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val permanentDtcs: StateFlow<List<String>> = permanentDtcEvents.map { list -> list.map { it.code } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _readinessMonitors = MutableStateFlow<ReadinessResult?>(null)
    val readinessMonitors: StateFlow<ReadinessResult?> = _readinessMonitors.asStateFlow()

    private val _vin = MutableStateFlow<String?>(null)
    val vin: StateFlow<String?> = _vin.asStateFlow()

    private val _freezeFrameData = MutableStateFlow<Map<String, String>>(emptyMap())
    val freezeFrameData: StateFlow<Map<String, String>> = _freezeFrameData.asStateFlow()

    private val _clearDtcResult = MutableStateFlow<String?>(null)
    val clearDtcResult: StateFlow<String?> = _clearDtcResult.asStateFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isDeletingVehicle = MutableStateFlow(false)
    val isDeletingVehicle: StateFlow<Boolean> = _isDeletingVehicle.asStateFlow()

    private val _isClearing = MutableStateFlow(false)
    val isClearing: StateFlow<Boolean> = _isClearing.asStateFlow()

    private val _dtcDefinitions = MutableStateFlow<Map<String, com.elysium369.meet.data.local.entities.DtcDefinitionEntity>>(emptyMap())
    val dtcDefinitions: StateFlow<Map<String, com.elysium369.meet.data.local.entities.DtcDefinitionEntity>> = _dtcDefinitions.asStateFlow()

    private val _manualSearchResults = MutableStateFlow<List<com.elysium369.meet.data.local.entities.DtcDefinitionEntity>>(emptyList())
    val manualSearchResults: StateFlow<List<com.elysium369.meet.data.local.entities.DtcDefinitionEntity>> = _manualSearchResults.asStateFlow()

    private val _manufacturer = MutableStateFlow<String>("GENERIC")
    val manufacturer: StateFlow<String> = _manufacturer.asStateFlow()

    private val _aiDtcExplanations = MutableStateFlow<Map<String, String>>(emptyMap())
    val aiDtcExplanations: StateFlow<Map<String, String>> = _aiDtcExplanations.asStateFlow()

    fun fetchAiExplanationForDtc(dtc: String, fallbackDescription: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_aiDtcExplanations.value.containsKey(dtc)) return@launch
            
            val vehicle = _selectedVehicle.value
            val vInfo = if (vehicle != null) "${vehicle.year} ${vehicle.make} ${vehicle.model}" else "Vehículo Genérico"
            
            try {
                // Update AI status to loading
                _aiDtcExplanations.update { it + (dtc to "CARGANDO...") }
                
                val result = geminiDiagnostic.analyzeDtc(
                    dtcList = listOf(dtc),
                    vehicleInfo = vInfo,
                    liveData = emptyMap()
                )
                
                _aiDtcExplanations.update { it + (dtc to result.analysisText) }
            } catch (e: Exception) {
                _aiDtcExplanations.update { it + (dtc to "ERROR: No se pudo conectar con la IA. Use el manual offline.") }
            }
        }
    }

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _currentOdometer = MutableStateFlow(0f)
    val currentOdometer: StateFlow<Float> = _currentOdometer.asStateFlow()

    private val _cloudSyncState = MutableStateFlow("")
    val cloudSyncState: StateFlow<String> = _cloudSyncState.asStateFlow()

    private val _language = MutableStateFlow("es") // "es" or "en"
    val language: StateFlow<String> = _language.asStateFlow()

    fun setLanguage(lang: String) {
        _language.value = lang
        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            .edit().putString("app_language", lang).apply()
    }

    private val _qosMetrics = MutableStateFlow(QosMetrics())
    val qosMetrics: StateFlow<QosMetrics> = _qosMetrics.asStateFlow()
    
    // Telemetry History for Graphs
    private val _telemetryHistory = MutableStateFlow<Map<String, List<Float>>>(emptyMap())
    val telemetryHistory: StateFlow<Map<String, List<Float>>> = _telemetryHistory.asStateFlow()

    // Oscilloscope Buffer (High-Frequency with Timestamps)
    private val _oscilloscopeBuffer = MutableStateFlow<Map<String, List<Pair<Long, Float>>>>(emptyMap())
    val oscilloscopeBuffer: StateFlow<Map<String, List<Pair<Long, Float>>>> = _oscilloscopeBuffer.asStateFlow()

    val highSpeedMode: StateFlow<Boolean> = obdSession.highSpeedMode
    val pinnedPids: StateFlow<Set<String>> = obdSession.pinnedPids

    val activeTestStatus: StateFlow<ActiveTestStatus> = obdSession.activeTestStatus
    val availableActiveTests: List<ActiveTest> = PidRegistry.ACTIVE_TESTS

    // ── Vehicle Identification (Car Scanner Pro style) ──
    val detectedProtocol: StateFlow<String> = obdSession.detectedProtocolFlow
    val adapterVersion: StateFlow<String> = obdSession.adapterVersionFlow
    val isCloneAdapter: StateFlow<Boolean> = obdSession.isCloneAdapterFlow
    val calibrationId: StateFlow<String?> = obdSession.calibrationId
    val ecuName: StateFlow<String?> = obdSession.ecuName

    // --- Network Topology ---
    val networkTopology: StateFlow<List<NetworkModule>> = obdSession.networkTopology
    val isScanningTopology: StateFlow<Boolean> = obdSession.isScanningTopology

    fun scanNetworkTopology() {
        viewModelScope.launch(Dispatchers.IO) {
            obdSession.scanNetworkTopology()
        }
    }

    // --- Digital Oscilloscope ---
    val oscilloscopeStream: SharedFlow<Pair<Long, Float>> = obdSession.oscilloscopeStream

    fun startOscilloscope(pidCode: String) {
        obdSession.startOscilloscope(pidCode)
    }

    fun stopOscilloscope() {
        obdSession.stopOscilloscope()
    }

    // --- AI and Health State ---
    private val _anomalousPids = MutableStateFlow<List<com.elysium369.meet.core.ai.HealthAnomaly>>(emptyList())
    val anomalousPids: StateFlow<List<com.elysium369.meet.core.ai.HealthAnomaly>> = _anomalousPids.asStateFlow()

    private val _isAiMonitoring = MutableStateFlow(false)
    val isAiMonitoring: StateFlow<Boolean> = _isAiMonitoring.asStateFlow()
    private var aiMonitorJob: kotlinx.coroutines.Job? = null

    private val _healthScore = MutableStateFlow(100)
    val healthScore: StateFlow<Int> = _healthScore.asStateFlow()

    private val _localDiagnostics = MutableStateFlow<List<ExpertDiagnosticProcedure>>(emptyList())
    val localDiagnostics: StateFlow<List<ExpertDiagnosticProcedure>> = _localDiagnostics.asStateFlow()

    // --- Predictive Health Engine State ---
    private val _predictiveHealthReport = MutableStateFlow<com.elysium369.meet.core.health.PredictiveHealthReport?>(null)
    val predictiveHealthReport: StateFlow<com.elysium369.meet.core.health.PredictiveHealthReport?> = _predictiveHealthReport.asStateFlow()

    private val _isAnalyzingHealth = MutableStateFlow(false)
    val isAnalyzingHealth: StateFlow<Boolean> = _isAnalyzingHealth.asStateFlow()

    private val healthSessionId = UUID.randomUUID().toString()
    private var sensorRecordingCounter = 0

    // --- Logging State ---
    private val _isLogging = MutableStateFlow(false)
    val isLogging: StateFlow<Boolean> = _isLogging.asStateFlow()
    private val _dataLog = MutableStateFlow<List<DataLogEntry>>(emptyList())
    val dataLog: StateFlow<List<DataLogEntry>> = _dataLog.asStateFlow()
    private var loggingJob: kotlinx.coroutines.Job? = null

    // --- Reactive Data from Room ---
    val trips: StateFlow<List<TripEntity>> = _selectedVehicle
        .flatMapLatest { vehicle ->
            vehicle?.let { tripDao.getTripsForVehicle(it.id) } ?: flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val maintenanceAlerts: StateFlow<List<MaintenanceAlertEntity>> = _selectedVehicle
        .flatMapLatest { vehicle ->
            vehicle?.let { maintenanceAlertDao.getAlertsForVehicle(it.id) } ?: flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val customPids: StateFlow<List<CustomPidEntity>> = customPidDao.getAllCustomPids()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val vehicles: StateFlow<List<Vehicle>> = vehicleRepository.getVehiclesForUser()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // PRODUCTION-GRADE: Each collector is isolated with try-catch to prevent
        // a single flow failure from crashing the entire ViewModel during startup.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
            
            // Collect live data from session — smoothed for professional gauge transitions
            launch {
                try {
                    obdSession.liveData
                        .collect { rawData -> 
                            val timestamp = System.currentTimeMillis()
                            // Apply per-PID moving average + exponential interpolation
                            val smoothedData = sensorSmoother.smoothAll(rawData).toMutableMap()
                            
                            // Restore raw voltage for oscilloscope and fast gauges (bypass second smoothing)
                            rawData["0142"]?.let { smoothedData["0142"] = it }
                            rawData["42"]?.let { smoothedData["42"] = it }
                            
                            _liveData.value = smoothedData
                            _localDiagnostics.value = localExpertSystem.analyzeLiveTelemetry(smoothedData)
                            updateTelemetryHistory(smoothedData)
                            updateOscilloscopeBuffer(timestamp, rawData)

                            // ── LiveLink broadcast (only if server is active) ──
                            _liveLinkServer?.let { server ->
                                if (server.isRunning.value) {
                                    launch(Dispatchers.IO) {
                                        try {
                                            val snap = TelemetrySnapshot(
                                                rpm = smoothedData["010C"]?.toInt() ?: 0,
                                                speed = smoothedData["010D"]?.toInt() ?: 0,
                                                coolantTemp = smoothedData["0105"]?.toInt() ?: 0,
                                                intakeTemp = smoothedData["010F"]?.toInt() ?: 0,
                                                throttlePos = smoothedData["0111"] ?: 0f,
                                                engineLoad = smoothedData["0104"] ?: 0f,
                                                fuelPressure = smoothedData["010A"]?.toInt() ?: 0,
                                                timingAdvance = smoothedData["010E"] ?: 0f,
                                                mafRate = smoothedData["0110"] ?: 0f,
                                                voltage = smoothedData["0142"] ?: smoothedData["42"] ?: 0f,
                                                fuelTrim1 = smoothedData["0106"] ?: 0f,
                                                fuelTrim2 = smoothedData["0108"] ?: 0f,
                                                healthScore = -1,
                                                activeDtcs = activeDtcs.value,
                                                vehicleName = _selectedVehicle.value?.let { "${it.year} ${it.make} ${it.model}" } ?: ""
                                            )
                                            server.broadcastTelemetry(snap)
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                            
                            // Record sensor data every ~30 readings for predictive health
                            sensorRecordingCounter++
                            if (sensorRecordingCounter % 30 == 0) {
                                val vehicle = _selectedVehicle.value
                                if (vehicle != null) {
                                    launch(Dispatchers.IO) {
                                        try {
                                            predictiveHealthEngine.recordSensorBatch(
                                                vehicleId = vehicle.id,
                                                sessionId = healthSessionId,
                                                liveData = smoothedData
                                            )
                                        } catch (e: Exception) {
                                            Log.e("ObdVM", "Sensor recording failed", e)
                                        }
                                    }
                                }
                            }
                        }
                } catch (e: Exception) {
                    android.util.Log.e("ObdVM", "liveData collector crashed", e)
                }
            }

            // Collect VIN and detect manufacturer
            launch {
                try {
                    obdSession.vin
                        .collect { v -> 
                            _vin.value = v
                            v?.let { detectManufacturer(it) }
                            updateHealthScore()
                        }
                } catch (e: Exception) {
                    android.util.Log.e("ObdVM", "vin collector crashed", e)
                }
            }
            
            // Auto-refresh diagnostics on connection
            launch {
                try {
                    obdSession.state
                        .collect { state ->
                            if (state == ObdState.CONNECTED) {
                                try {
                                    refreshDiagnostics()
                                    obdSession.fetchVin()
                                    _currentOdometer.value = obdSession.readOdometer()
                                } catch (e: Exception) {
                                    android.util.Log.e("ObdVM", "Post-connect init error", e)
                                }
                            }
                        }
                } catch (e: Exception) {
                    android.util.Log.e("ObdVM", "state collector crashed", e)
                }
            }

            // Sync custom PIDs to session
            launch {
                try {
                    customPidDao.getAllCustomPids()
                        .collect { pids ->
                            obdSession.setCustomPids(pids)
                        }
                } catch (e: Exception) {
                    android.util.Log.e("ObdVM", "customPids collector crashed", e)
                }
            }

            // Collect QoS from session
            launch {
                try {
                    obdSession.qosMetrics
                        .collect { metrics -> 
                            _qosMetrics.value = metrics 
                        }
                } catch (e: Exception) {
                    android.util.Log.e("ObdVM", "qos collector crashed", e)
                }
            }

            // Auto-start AI monitoring if enabled
            launch {
                try {
                    isAiMonitoring.collect { enabled ->
                        if (enabled) startAiMonitoring() else aiMonitorJob?.cancel()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ObdVM", "AI monitor collector crashed", e)
                }
            }

            // Reactive Health Score calculation
            launch {
                try {
                    combine(
                        activeDtcs,
                        pendingDtcs,
                        _anomalousPids,
                        _liveData
                    ) { active, pending, anomalies, live ->
                        calculateHealthScore(active, pending, anomalies, live)
                    }.collect { score ->
                        _healthScore.value = score
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ObdVM", "healthScore collector crashed", e)
                }
            }
        }

        // Subscriptions and Cloud Sync — isolated from main flow collectors
        viewModelScope.launch {
            try {
                // ─── STEP 1: ALWAYS restore selected vehicle from local persistence ───
                // This runs BEFORE cloud sync to ensure instant UI readiness.
                val prefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
                val savedVehicleId = prefs.getString("selected_vehicle_id", null)
                
                if (savedVehicleId != null) {
                    val vehicle = vehicleRepository.getVehicleById(savedVehicleId)
                    if (vehicle != null) {
                        _selectedVehicle.value = vehicle
                        android.util.Log.d("ObdVM", "✅ Restored selected vehicle: ${vehicle.make} ${vehicle.model}")
                    } else {
                        android.util.Log.w("ObdVM", "⚠️ Saved vehicle ID not found in DB — clearing preference")
                        prefs.edit().remove("selected_vehicle_id").apply()
                    }
                }

                // ─── STEP 2: Cloud sync (only if authenticated) ───
                val user = SupabaseManager.client.auth.currentUserOrNull()
                _isPremium.value = subscriptionRepository.isPremium()
                
                if (user != null) {
                    _cloudSyncState.value = "Sincronizando garaje..."
                    vehicleRepository.syncVehiclesFromCloud(user.id)
                    _cloudSyncState.value = "Sincronización completa"
                }
            } catch (e: Exception) {
                android.util.Log.e("ObdVM", "Startup sync/restore failed", e)
                _cloudSyncState.value = "Error de sincronización"
            }

            // Monitor vehicles count for debugging
            launch {
                vehicles.collect { list ->
                    android.util.Log.d("ObdVM", "Total vehicles in DB: ${list.size}")
                }
            }
        }

        // Load persisted settings for Clone Mode & AI Config
        val prefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
        _forceCloneMode.value = prefs.getBoolean("force_clone_mode", false)
        _language.value = prefs.getString("app_language", "es") ?: "es"
        val loadedConfig = AiConfig(
            provider = prefs.getString("ai_provider", "gemini") ?: "gemini",
            apiKey = prefs.getString("ai_api_key", "") ?: "",
            endpoint = prefs.getString("ai_base_url", "") ?: "",
            modelName = prefs.getString("ai_model_name", "") ?: ""
        )
        _aiConfig.value = loadedConfig
        // Push to diagnostic engine on startup
        if (loadedConfig.apiKey.isNotBlank()) {
            val resolvedEp = resolveAiEndpoint(loadedConfig.provider, loadedConfig.endpoint, loadedConfig.modelName)
            geminiDiagnostic.updateConfig(loadedConfig.apiKey, resolvedEp, loadedConfig.provider)
        }
    }

    // --- Settings Actions ---

    /** Toggle Force Clone Mode — treats any adapter as a clone for compatibility testing */
    fun setForceCloneMode(enabled: Boolean) {
        _forceCloneMode.value = enabled
        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("force_clone_mode", enabled).apply()
        Log.d("ObdVM", "Force Clone Mode: $enabled")
    }

    /** Save AI configuration and push to diagnostic engine */
    fun saveAiConfig(provider: String, apiKey: String, endpoint: String, modelName: String) {
        val config = AiConfig(provider, apiKey, endpoint, modelName)
        _aiConfig.value = config
        
        val prefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE).edit()
        prefs.putString("ai_provider", provider)
        prefs.putString("ai_api_key", apiKey)
        prefs.putString("ai_base_url", endpoint)
        prefs.putString("ai_model_name", modelName)
        prefs.apply()

        // Push config to the diagnostic engine immediately
        val resolvedEndpoint = resolveAiEndpoint(provider, endpoint, modelName)
        geminiDiagnostic.updateConfig(apiKey, resolvedEndpoint, provider)
        Log.d("ObdVM", "AI Config saved: provider=$provider, model=$modelName")
    }

    /** Resolve endpoint URL based on provider selection */
    private fun resolveAiEndpoint(provider: String, customEndpoint: String, modelName: String): String? {
        return when (provider) {
            "gemini" -> null // use default Gemini endpoint inside GeminiDiagnostic
            "openai" -> if (customEndpoint.isNotBlank()) customEndpoint else "https://api.openai.com/v1/chat/completions"
            "anthropic" -> if (customEndpoint.isNotBlank()) customEndpoint else "https://api.anthropic.com/v1/messages"
            "ollama" -> if (customEndpoint.isNotBlank()) customEndpoint else "http://localhost:11434/v1/chat/completions"
            "custom" -> customEndpoint.ifBlank { null }
            else -> null
        }
    }

    // --- Actions ---

    /** Connect to an OBD2 adapter by MAC address or IP */
    fun connect(address: String) {
        obdSession.setTargetAddress(address)
        viewModelScope.launch {
            obdSession.connect()
        }
    }

    fun startDiagnosticSession(vehicle: Vehicle) {
        _selectedVehicle.value = vehicle
        currentSessionId = UUID.randomUUID().toString()
        viewModelScope.launch {
            obdSession.connect()
            // Wait for actual state change instead of checking synchronously
            obdSession.state
                .filter { it == ObdState.CONNECTED || it == ObdState.ERROR || it == ObdState.DISCONNECTED }
                .first()
                .let { finalState ->
                    if (finalState == ObdState.CONNECTED) {
                        startForegroundService(vehicle.id)
                    }
                }
        }
    }

    fun stopSession() {
        viewModelScope.launch {
            saveSessionResults()
            obdSession.disconnect()
            context.stopService(Intent(context, com.elysium369.meet.core.obd.ObdForegroundService::class.java))
            _selectedVehicle.value = null
            clearState()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            saveSessionResults()
            obdSession.disconnect()
            context.stopService(Intent(context, com.elysium369.meet.core.obd.ObdForegroundService::class.java))
        }
    }

    fun resetTrip() {
        obdSession.resetTrip()
    }

    // --- Mode 06 Noncontinuous Monitors ---
    val mode06Results = obdSession.mode06Results
    private val _isReadingMode06 = MutableStateFlow(false)
    val isReadingMode06: StateFlow<Boolean> = _isReadingMode06.asStateFlow()

    fun readMode06() {
        viewModelScope.launch(Dispatchers.IO) {
            _isReadingMode06.value = true
            try {
                obdSession.readMode06Results()
            } catch (e: Exception) {
                Log.e("ObdVM", "Mode 06 failed", e)
            } finally {
                _isReadingMode06.value = false
            }
        }
    }

    // --- Driving/Standing Time ---
    val drivingTimeSeconds = obdSession.drivingTimeSeconds
    val standingTimeSeconds = obdSession.standingTimeSeconds

    fun resetDrivingTime() {
        obdSession.resetDrivingTime()
    }

    private suspend fun saveSessionResults() {
        val vehicle = _selectedVehicle.value ?: return
        val currentDtcs = activeDtcs.value
        val snapshot = _liveData.value
        
        val session = DiagnosticSession(
            id = UUID.randomUUID().toString(),
            user_id = com.elysium369.meet.data.remote.SupabaseModule.client.auth.currentUserOrNull()?.id ?: "guest",
            vehicle_vin = vehicle.vin,
            vehicle_make = vehicle.make,
            vehicle_model = vehicle.model,
            vehicle_year = vehicle.year,
            dtcs_found = Json.encodeToString(currentDtcs),
            severity = when {
                permanentDtcs.value.isNotEmpty() -> "critical"
                currentDtcs.isNotEmpty() -> "high"
                pendingDtcs.value.isNotEmpty() -> "moderate"
                else -> "low"
            },
            live_data_snapshot = Json.encodeToString(snapshot.mapValues { it.value.toString() })
        )
        
        sessionLogRepository.saveSession(session)
    }

    private fun clearState() {
        // DTCs are now persistent in DB, we don't clear them here manually
        // they will reload based on _selectedVehicle change
        _readinessMonitors.value = null
        _liveData.value = emptyMap()
    }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            _isDeletingVehicle.value = true
            try {
                vehicleRepository.deleteVehicle(vehicle)
                if (_selectedVehicle.value?.id == vehicle.id) {
                    _selectedVehicle.value = null
                }
            } catch (e: Exception) {
                Log.e("ObdVM", "Error deleting vehicle", e)
            } finally {
                // Small delay to allow animation to show
                kotlinx.coroutines.delay(800)
                _isDeletingVehicle.value = false
            }
        }
    }

    suspend fun refreshDiagnostics(manageState: Boolean = true) {
        if (manageState) _isScanning.value = true
        if (obdSession.state.value != ObdState.CONNECTED) {
            // Simulate attempt to allow UI animations to play
            kotlinx.coroutines.delay(1500)
            if (manageState) _isScanning.value = false
            return
        }
        try {
            val freshActive = obdSession.readActiveDtcs()
            val freshPending = obdSession.readPendingDtcs()
            val freshPermanent = obdSession.readPermanentDtcs()
            
            saveDetectedDtcs(freshActive, "ACTIVE")
            saveDetectedDtcs(freshPending, "PENDING")
            saveDetectedDtcs(freshPermanent, "PERMANENT")
            
            _readinessMonitors.value = obdSession.readReadinessMonitors()
            
            // Read Mode 06 results to detect specific cylinder misfires
            val mode06Results = obdSession.readMode06Results()
            val allCodes = (activeDtcs.value + pendingDtcs.value + permanentDtcs.value).toMutableList()
            
            // If we have a generic misfire or we want to surface specific ones
            if (allCodes.any { it.startsWith("P030") }) {
                mode06Results.forEach { result ->
                    val midPrefix = result.mid.replace("$", "").uppercase()
                    if (midPrefix in listOf("A2", "A3", "A4", "A5", "A6", "A7", "A8", "A9", "AA", "AB", "AC", "AD")) {
                        // If it failed or has a non-zero value, it's a misfire on this cylinder
                        if (!result.passed || result.value > 0) {
                            val cylIdx = when(midPrefix) {
                                "A2" -> 1; "A3" -> 2; "A4" -> 3; "A5" -> 4;
                                "A6" -> 5; "A7" -> 6; "A8" -> 7; "A9" -> 8;
                                "AA" -> 9; "AB" -> 10; "AC" -> 11; "AD" -> 12;
                                else -> 0
                            }
                            if (cylIdx > 0) {
                                val cylCode = "P03" + String.format("%02d", cylIdx)
                                if (!allCodes.contains(cylCode)) {
                                    allCodes.add(cylCode)
                                    // Guardar en DB para persistencia
                                    saveDetectedDtcs(listOf(cylCode), "ACTIVE")
                                    saveDetectedDtcs(listOf(cylCode), "PENDING")
                                }
                            }
                        }
                    }
                }
            }
            
            // Fetch definitions for all new DTCs
            fetchDtcDefinitions((activeDtcs.value + pendingDtcs.value + permanentDtcs.value).distinct())
            
            updateHealthScore()
        } catch (e: Exception) {
            android.util.Log.e("ObdVM", "Failed to refresh diagnostics", e)
        } finally {
            if (manageState) _isScanning.value = false
        }
    }

    private suspend fun saveDetectedDtcs(codes: List<String>, status: String) {
        val vehicle = _selectedVehicle.value ?: return
        val now = System.currentTimeMillis()
        
        codes.forEach { code ->
            val existing = dtcDao.getUnresolvedDtc(vehicle.id, code, status)
            if (existing != null) {
                // Accumulate: Update freshness and count
                val isNewSession = existing.sessionId != currentSessionId
                val updated = existing.copy(
                    lastSeenAt = now,
                    occurrenceCount = if (isNewSession) existing.occurrenceCount + 1 else existing.occurrenceCount,
                    sessionId = currentSessionId, // Update to latest session that saw it
                    synced = false // Mark for re-sync
                )
                dtcDao.insertDtc(updated)
            } else {
                // Fetch definition for initial discovery
                val defs = dtcDefinitionDao.getDefinitions(code)
                val description = if (defs.isNotEmpty()) defs.first().descriptionEs else ""
                val severity = if (defs.isNotEmpty()) defs.first().severity else "UNKNOWN"

                // New discovery: Store as persistent event
                val newEvent = DtcEventEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = currentSessionId,
                    vehicleId = vehicle.id,
                    code = code,
                    description = description,
                    severity = severity,
                    status = status,
                    firstSeenAt = now,
                    lastSeenAt = now,
                    resolvedAt = null,
                    occurrenceCount = 1,
                    freezeFrameJson = null,
                    synced = false
                )
                dtcDao.insertDtc(newEvent)
                
                // Alert the user only for truly new detections
                if (status == "ACTIVE") {
                    alertManager.triggerNewDtcAlert(code)
                }
            }
        }
        
        // Trigger background sync after saving/updating DTCs
        scheduleSync()
    }

    private fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(syncRequest)
    }

    private fun fetchDtcDefinitions(codes: List<String>) {
        viewModelScope.launch {
            val newDefinitions = _dtcDefinitions.value.toMutableMap()
            val vehicleMake = _selectedVehicle.value?.make?.uppercase()
            codes.forEach { code ->
                if (!newDefinitions.containsKey(code)) {
                    val defs = dtcDefinitionDao.getDefinitions(code)
                    if (defs.isNotEmpty()) {
                        val bestDef = defs.first()
                        newDefinitions[code] = bestDef
                    } else {
                        newDefinitions[code] = generateFallbackDefinition(code)
                    }
                }
            }
            _dtcDefinitions.value = newDefinitions
        }
    }

    private fun generateFallbackDefinition(code: String): DtcDefinitionEntity {
        val letter = code.firstOrNull()?.uppercaseChar() ?: 'P'
        val digit1 = code.drop(1).firstOrNull() ?: '0'
        val digit2 = code.drop(2).firstOrNull() ?: '0'
        
        val isGeneric = digit1 == '0' || (letter == 'P' && digit1 == '2') || (letter == 'U' && digit1 == '3')
        val genericStr = if (isGeneric) "Genérico" else "Específico del Fabricante"
        
        val systemName = when (letter) {
            'P' -> "Motor/Transmisión"
            'C' -> "Chasis"
            'B' -> "Carrocería"
            'U' -> "Red/Comunicación"
            else -> "General"
        }
        
        val subsys = if (letter == 'P') {
            when (digit2.uppercaseChar()) {
                '0', '1', '2' -> " - Medición de aire y combustible"
                '3' -> " - Sistema de encendido o falla de cilindro"
                '4' -> " - Controles auxiliares de emisiones"
                '5' -> " - Control de velocidad, ralentí y entradas auxiliares"
                '6' -> " - Computadora y circuitos de salida"
                '7', '8', '9' -> " - Transmisión"
                'A', 'B', 'C', 'D', 'E', 'F' -> " - Propulsión/Híbrido"
                else -> ""
            }
        } else ""

        val desc = "DTC $genericStr de $systemName$subsys. Definición exacta no disponible localmente."
        val severity = if ((letter == 'P' || letter == 'U') && (digit1 == '0' || digit1 == '2')) "HIGH" else "MODERATE"
        val urgency = if (severity == "HIGH") "STOP_DRIVING" else "CAUTION"
        
        return DtcDefinitionEntity(
            code = code,
            descriptionEn = "DTC $code. Exact definition not available locally.",
            descriptionEs = desc,
            system = when (letter) { 'P' -> "ENGINE"; 'C' -> "CHASSIS"; 'B' -> "BODY"; 'U' -> "NETWORK"; else -> "GENERAL" },
            severity = severity,
            possibleCauses = "Requiere escaneo profesional avanzado. / Requires advanced professional scan.",
            urgency = urgency
        )
    }

    fun searchDtcManual(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (query.isBlank()) {
                _manualSearchResults.value = emptyList()
            } else {
                _manualSearchResults.value = dtcDefinitionDao.searchDefinitions(query).take(50)
            }
        }
    }

    suspend fun clearDtcs(): Boolean {
        _isClearing.value = true
        _clearDtcResult.value = "Enviando comando de borrado..."
        val success = obdSession.clearDtcs()
        if (success) {
            _clearDtcResult.value = "✅ Códigos borrados exitosamente"
            _selectedVehicle.value?.let { vehicle ->
                dtcDao.resolveAllDtcsForVehicle(vehicle.id, System.currentTimeMillis())
            }
            _freezeFrameData.value = emptyMap()
            updateHealthScore()
            // Schedule sync to push resolution events to cloud
            scheduleSync()
        } else {
            _clearDtcResult.value = "❌ Error al borrar códigos. Asegúrese que el motor esté apagado y en contacto (IGNITION ON)."
        }
        _isClearing.value = false
        return success
    }

    /**
     * Executes a "Smart Scan" — A comprehensive health check of the vehicle.
     */
    suspend fun runSmartScan() {
        if (connectionState.value != ObdState.CONNECTED) return
        
        _isScanning.value = true
        _cloudSyncState.value = "Iniciando Escaneo Inteligente Elite..."
        
        try {
            // 1. Scan DTCs
            _cloudSyncState.value = "Buscando códigos de falla (DTCs)..."
            refreshDiagnostics(manageState = false)
            
            // 2. If DTCs found, fetch Freeze Frame for the first one
            if (activeDtcs.value.isNotEmpty()) {
                _cloudSyncState.value = "Capturando Cuadro Congelado Histórico..."
                val firstDtc = activeDtcs.value.first()
                val ff = obdSession.readFreezeFrame(firstDtc)
                val scoped = ff.mapKeys { (key, _) -> "$firstDtc:$key" }
                _freezeFrameData.value = _freezeFrameData.value + scoped
            }
            
            // 3. Check Battery Voltage & Alternator health
            _cloudSyncState.value = "Analizando sistema eléctrico..."
            val voltage = obdSession.readBatteryVoltage()
            val batteryHealth = if (voltage > 12.4f) "Excelente" else if (voltage > 11.8f) "Normal" else "Baja (Cargar)"
            
            // 4. Update status with detailed report
            _cloudSyncState.value = "Escaneo completado. Batería: $batteryHealth (${voltage}V)"
            
            // 5. Auto-save session to cloud
            saveSessionResults()
        } finally {
            _isScanning.value = false
        }
    }

    private fun detectManufacturer(vin: String) {
        if (vin.length < 3) {
            _manufacturer.value = "GENERIC"
            return
        }
        
        val mfr = when {
            // North American Ford
            vin.startsWith("1FM") || vin.startsWith("1FT") || vin.startsWith("1FA") || vin.startsWith("3FA") -> "FORD"
            // Toyota / Lexus
            vin.startsWith("JTD") || vin.startsWith("JT1") || vin.startsWith("JTN") || vin.startsWith("JTH") -> "TOYOTA"
            // General Motors (Chevrolet, GMC, Cadillac, Buick)
            vin.startsWith("1GC") || vin.startsWith("1G1") || vin.startsWith("1G6") || vin.startsWith("3G1") -> "GM"
            // BMW
            vin.startsWith("WBA") || vin.startsWith("WBS") || vin.startsWith("5UX") -> "BMW"
            // Volkswagen / Audi / Seat / Skoda (VAG)
            vin.startsWith("WVW") || vin.startsWith("WV2") || vin.startsWith("WAU") || vin.startsWith("TRU") -> "VOLKSWAGEN"
            // Mercedes-Benz
            vin.startsWith("WDB") || vin.startsWith("WDC") || vin.startsWith("WDD") || vin.startsWith("55S") -> "MERCEDES"
            // Honda / Acura
            vin.startsWith("JHM") || vin.startsWith("1HG") || vin.startsWith("2HG") || vin.startsWith("SHH") -> "HONDA"
            // Nissan / Infiniti
            vin.startsWith("JN1") || vin.startsWith("1N4") || vin.startsWith("1N6") || vin.startsWith("5N1") -> "NISSAN"
            // Hyundai / Kia
            vin.startsWith("KMH") || vin.startsWith("5NP") -> "HYUNDAI"
            vin.startsWith("KNA") || vin.startsWith("KND") -> "KIA"
            // Mazda
            vin.startsWith("JM1") || vin.startsWith("JM3") || vin.startsWith("3MZ") -> "MAZDA"
            // Subaru
            vin.startsWith("JF1") || vin.startsWith("JF2") || vin.startsWith("4S3") -> "SUBARU"
            // Peugeot / Citroën
            vin.startsWith("VF3") || vin.startsWith("VF7") -> "PEUGEOT"
            // Fiat / Chrysler (Stellantis)
            vin.startsWith("ZFA") || vin.startsWith("1C4") || vin.startsWith("2C3") -> "FIAT"
            // Land Rover / Jaguar
            vin.startsWith("SAL") || vin.startsWith("SAJ") -> "LAND_ROVER"
            else -> "GENERIC"
        }
        
        _manufacturer.value = mfr
        obdSession.enableOemPids(mfr)
    }

    suspend fun refreshFreezeFrame(dtc: String) {
        _cloudSyncState.value = "Refrescando Cuadro Congelado..."
        val ff = obdSession.readFreezeFrame(dtc)
        // Merge into existing map with DTC-scoped keys to prevent cross-DTC contamination
        val scoped = ff.mapKeys { (key, _) -> "$dtc:$key" }
        _freezeFrameData.value = _freezeFrameData.value + scoped
        _cloudSyncState.value = "Cuadro Congelado actualizado."
    }

    suspend fun readVin(): String? {
        return obdSession.fetchVin()
    }

    suspend fun setProtocol(protocol: String): Boolean {
        return obdSession.setProtocol(protocol)
    }

    suspend fun scanModules(): List<com.elysium369.meet.core.obd.NetworkModule> {
        _isScanning.value = true
        return try {
            obdSession.scanModules()
        } finally {
            _isScanning.value = false
        }
    }

    suspend fun sendRawCommand(cmd: String): String {
        return obdSession.sendRawCommand(cmd)
    }

    /**
     * Executes a professional-grade diagnostic routine or active test.
     * Performs safety checks (voltage, adapter quality) before sending commands.
     */
    suspend fun runDiagnosticCommand(command: com.elysium369.meet.core.obd.ObdCommandDef): String {
        // 1. Safety Guard
        if (!obdSession.verifySafetyForProAction()) {
            return "SAFETY_ERROR"
        }
        
        // 2. Execution
        val response = obdSession.sendRawCommand(command.command)
        
        // 3. Validation
        val isSuccess = response.contains(command.expectedResponse) || 
                        response.contains("OK") || 
                        response.contains("61") || // ISO 14230-4 response prefix
                        (command.command.startsWith("31") && response.startsWith("71")) // Routine control response
        
        return if (isSuccess) "SUCCESS" else response
    }

    suspend fun consultAi(apiKey: String?, endpointUrl: String?, dtcList: List<String>): String {
        geminiDiagnostic.updateConfig(apiKey, endpointUrl)
        val info = _selectedVehicle.value?.let { "${it.make} ${it.model} ${it.year}" } ?: "Vehículo Genérico"
        val result = geminiDiagnostic.analyzeDtc(
            dtcList, 
            info, 
            _liveData.value.mapValues { "%.2f".format(it.value) },
            _telemetryHistory.value
        )
        
        // Update the UI with detected anomalies (as generic HealthAnomaly for now)
        _anomalousPids.value = result.anomalousPids.map { 
            com.elysium369.meet.core.ai.HealthAnomaly(it, "Anomalía detectada en diagnóstico profundo")
        }
        
        return result.analysisText
    }

    fun generateFullReport(aiAnalysis: String?) {
        val currentTrip = tripManager.currentTrip
        val vehicleInfo = _selectedVehicle.value?.let { "${it.make} ${it.model} ${it.year}" } ?: "Vehículo Genérico"
        val dtcs = activeDtcs.value
        val history = _telemetryHistory.value
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val tripData = if (currentTrip != null) {
                com.elysium369.meet.data.supabase.Trip(
                    id = currentTrip.id,
                    user_id = com.elysium369.meet.data.remote.SupabaseModule.client.auth.currentUserOrNull()?.id ?: "guest",
                    vehicle_id = currentTrip.vehicleId,
                    session_id = currentTrip.sessionId,
                    started_at = currentTrip.startedAt,
                    ended_at = currentTrip.endedAt,
                    distance_km = currentTrip.distanceKm,
                    duration_seconds = currentTrip.durationSeconds,
                    avg_speed_kmh = currentTrip.avgSpeedKmh,
                    max_speed_kmh = currentTrip.maxSpeedKmh,
                    max_rpm = currentTrip.maxRpm,
                    avg_rpm = currentTrip.avgRpm,
                    max_temp_c = currentTrip.maxTempC,
                    fuel_efficiency = currentTrip.fuelEfficiency,
                    eco_score = currentTrip.ecoScore,
                    gps_track_json = currentTrip.gpsTrackJson
                )
            } else {
                // Create a synthetic trip for the report if none exists
                com.elysium369.meet.data.supabase.Trip(
                    id = UUID.randomUUID().toString(),
                    user_id = com.elysium369.meet.data.remote.SupabaseModule.client.auth.currentUserOrNull()?.id ?: "guest",
                    vehicle_id = _selectedVehicle.value?.id ?: "N/A",
                    session_id = "MANUAL_DIAGNOSTIC",
                    started_at = System.currentTimeMillis(),
                    ended_at = System.currentTimeMillis(),
                    distance_km = 0f,
                    duration_seconds = 0,
                    avg_speed_kmh = 0f,
                    max_speed_kmh = 0f,
                    max_rpm = _liveData.value["010C"] ?: 0f,
                    avg_rpm = _liveData.value["010C"] ?: 0f,
                    max_temp_c = _liveData.value["0105"] ?: 0f,
                    fuel_efficiency = null,
                    eco_score = 100,
                    gps_track_json = null
                )
            }
            
            val healthScore = _healthScore.value
            val alerts = maintenanceAlerts.value
            
            val file = reportGenerator.generatePdfReport(
                trip = tripData,
                dtcs = dtcs,
                aiAnalysis = aiAnalysis,
                vehicleDetails = vehicleInfo,
                telemetryHistory = history,
                anomalies = _anomalousPids.value,
                healthScore = healthScore,
                maintenanceAlerts = alerts
            )
            
            reportGenerator.shareReport(file)
        }
    }

    fun markMaintenanceDone(alert: MaintenanceAlertEntity) {
        viewModelScope.launch {
            // Fetch real odometer from ECU if available
            val currentOdo = obdSession.readOdometer()
            val currentOdoLong = currentOdo.toLong()
            val nextDue = if (currentOdoLong > 0) currentOdoLong + alert.intervalKm else alert.nextDueKm + alert.intervalKm
            
            val updatedAlert = alert.copy(
                lastDoneKm = if (currentOdoLong > 0) currentOdoLong else alert.nextDueKm,
                nextDueKm = nextDue
            )
            maintenanceAlertDao.insertAlert(updatedAlert)
        }
    }

    fun addCustomPid(pid: CustomPidEntity) {
        viewModelScope.launch {
            customPidDao.insertCustomPid(pid)
        }
    }

    fun deleteCustomPid(pid: CustomPidEntity) {
        viewModelScope.launch {
            customPidDao.deleteCustomPid(pid)
        }
    }

    fun setHighSpeedMode(enabled: Boolean) {
        obdSession.setHighSpeedMode(enabled)
    }

    fun pinPid(pid: String) {
        obdSession.pinPid(pid)
    }

    fun unpinPid(pid: String) {
        obdSession.unpinPid(pid)
        // Optionally clear history for unpinned PID
        val current = _telemetryHistory.value.toMutableMap()
        current.remove(pid)
        _telemetryHistory.value = current
    }

    private fun updateTelemetryHistory(newData: Map<String, Float>) {
        val pinned = pinnedPids.value
        if (pinned.isEmpty()) return

        val currentHistory = _telemetryHistory.value
        val newHistory = currentHistory.toMutableMap()
        val maxPoints = 200 

        pinned.forEach { pid ->
            val value = newData[pid] ?: return@forEach
            val list = currentHistory[pid]?.toMutableList() ?: mutableListOf()
            list.add(value)
            if (list.size > maxPoints) {
                list.removeAt(0)
            }
            newHistory[pid] = list
        }
        _telemetryHistory.value = newHistory
    }

    private fun updateOscilloscopeBuffer(timestamp: Long, rawData: Map<String, Float>) {
        val pinned = pinnedPids.value
        // We always want to buffer the battery voltage for the oscilloscope
        val hasVoltage = rawData.containsKey("0142") || rawData.containsKey("42")
        if (pinned.isEmpty() && !hasVoltage) return

        val currentBuffer = _oscilloscopeBuffer.value
        val newBuffer = currentBuffer.toMutableMap()
        val maxPoints = 1000 // High frequency buffer capacity

        val targets = pinned.toMutableSet()
        if (hasVoltage) {
            targets.add(if (rawData.containsKey("0142")) "0142" else "42")
        }

        targets.forEach { pid ->
            val value = rawData[pid] ?: return@forEach
            val list = currentBuffer[pid]?.toMutableList() ?: mutableListOf()
            list.add(Pair(timestamp, value))
            if (list.size > maxPoints) {
                list.removeAt(0) // Remove oldest
            }
            newBuffer[pid] = list
        }
        _oscilloscopeBuffer.value = newBuffer
    }

    // --- Active Testing (Bidirectional) ---
    fun runActiveTest(test: ActiveTest) {
        obdSession.runActiveTest(test)
    }

    fun stopActiveTest() {
        obdSession.stopActiveTest()
    }

    suspend fun resetOilService(): Boolean {
        val mfr = _manufacturer.value
        return diagnosticManager.resetOilService(mfr)
    }

    suspend fun registerBattery(capacityAh: Int): Boolean {
        val mfr = _manufacturer.value
        return diagnosticManager.registerBattery(mfr, capacityAh)
    }

    suspend fun resetEPB(open: Boolean): Boolean {
        val mfr = _manufacturer.value
        return diagnosticManager.resetEPB(mfr, open)
    }

    suspend fun calibrateSAS(): Boolean {
        val mfr = _manufacturer.value
        return diagnosticManager.calibrateSAS(mfr)
    }

    suspend fun relearnThrottle(): Boolean {
        val mfr = _manufacturer.value
        return diagnosticManager.relearnThrottle(mfr)
    }

    suspend fun regenerateDPF(): Boolean {
        val mfr = _manufacturer.value
        return diagnosticManager.regenerateDPF(mfr)
    }

    suspend fun resetTPMS(): Boolean {
        return try {
            obdSession.sendRawCommand("ATSH7E0")
            obdSession.sendRawCommand("1003")
            val resp = obdSession.sendRawCommand("3101000D") // TPMS Relearn Routine
            resp.startsWith("71")
        } catch (e: Exception) {
            Log.e("ObdViewModel", "TPMS reset failed", e)
            false
        }
    }

    fun exportTripToPdf(trip: TripEntity) {
        viewModelScope.launch {
            val vehicleInfo = _selectedVehicle.value?.let { "${it.make} ${it.model} (${it.year})" } ?: "Vehículo Desconocido"
            
            // Convert Entity to Domain model for ReportGenerator
            val domainTrip = com.elysium369.meet.data.supabase.Trip(
                id = trip.id,
                user_id = com.elysium369.meet.data.remote.SupabaseModule.client.auth.currentUserOrNull()?.id ?: "guest",
                vehicle_id = trip.vehicleId,
                session_id = trip.sessionId,
                started_at = trip.startedAt,
                ended_at = trip.endedAt,
                distance_km = trip.distanceKm,
                duration_seconds = trip.durationSeconds,
                avg_speed_kmh = trip.avgSpeedKmh,
                max_speed_kmh = trip.maxSpeedKmh,
                max_rpm = trip.maxRpm,
                avg_rpm = trip.avgRpm,
                max_temp_c = trip.maxTempC,
                fuel_efficiency = trip.fuelEfficiency,
                eco_score = trip.ecoScore,
                gps_track_json = trip.gpsTrackJson
            )

            // In a real scenario, we might want to fetch DTCs for this trip
            // For now, use active DTCs if it's the current session, or empty
            val dtcs = if (trip.endedAt == null) activeDtcs.value else emptyList<String>()
            
            val file = reportGenerator.generatePdfReport(
                trip = domainTrip,
                dtcs = dtcs,
                aiAnalysis = null, // Could be fetched from a saved analysis
                vehicleDetails = vehicleInfo
            )
            reportGenerator.shareReport(file)
        }
    }
    
    fun getCurrentTrip(): TripEntity? {
        return tripManager.currentTrip
    }

    suspend fun runAdapterCloneTest(): List<com.elysium369.meet.ui.screens.TestResult> {
        val rawResults = obdSession.runAdapterTests()
        return rawResults.map { (name, value) ->
            val color = when {
                value.contains("ERROR", true) || value.contains("?") -> android.graphics.Color.RED
                value.contains("N/A") || value.contains("Pendiente") -> android.graphics.Color.YELLOW
                value.contains("Instrucción") || value.contains("Conecta") -> android.graphics.Color.CYAN
                value.contains("Offline") || value.contains("Sin conexión") -> android.graphics.Color.YELLOW
                value.contains("ms") && (value.replace(" ms", "").toIntOrNull() ?: 0) > 300 -> android.graphics.Color.YELLOW
                else -> android.graphics.Color.GREEN
            }
            com.elysium369.meet.ui.screens.TestResult(name, value, color.toComposeColor())
        }
    }

    private fun Int.toComposeColor() = androidx.compose.ui.graphics.Color(this)

    // --- Helpers ---
    private fun startForegroundService(vehicleId: String) {
        val intent = Intent(context, com.elysium369.meet.core.obd.ObdForegroundService::class.java).apply {
            putExtra("vehicle_id", vehicleId)
        }
        try { context.startService(intent) } catch (_: Exception) {}
    }

    fun saveVehicle(
        make: String,
        model: String,
        year: String,
        engineDisplacement: String,
        engineTech: String,
        transmission: String,
        transmissionType: String,
        fuelType: String,
        plate: String,
        vin: String?
    ) {
        viewModelScope.launch {
            val displacement = engineDisplacement.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
            val enginePart = listOf(engineDisplacement, engineTech).filter { it.isNotBlank() }.joinToString(" ")
            val transPart = listOf(transmission, transmissionType).filter { it.isNotBlank() }.joinToString(" - ")
            val fullEngineDesc = listOf(enginePart, transPart, fuelType).filter { it.isNotBlank() }.joinToString(" | ")
            
            val vehicle = Vehicle(
                id = UUID.randomUUID().toString(),
                user_id = com.elysium369.meet.data.remote.SupabaseModule.client.auth.currentUserOrNull()?.id ?: "guest",
                year = year.toIntOrNull() ?: 2024,
                make = make,
                model = model,
                engine = if (fullEngineDesc.isBlank()) "N/A" else fullEngineDesc,
                displacement_cc = displacement,
                engine_tech = engineTech,
                transmission_type = transmission,
                transmission_subtype = transmissionType,
                fuel_type = fuelType,
                vin = vin?.ifBlank { "NOT_READ" } ?: "NOT_READ",
                plate = plate.ifBlank { "NOT_SET" }
            )
            
            android.util.Log.d("ObdVM", "Saving vehicle: ${vehicle.make} ${vehicle.model} (ID: ${vehicle.id})")
            vehicleRepository.insertVehicle(vehicle)
            
            // Fix: Call selectVehicle to ensure persistence of the selected ID
            selectVehicle(vehicle)
        }
    }

    // AI and Logging moved to top

    fun toggleAiMonitoring(enabled: Boolean) {
        _isAiMonitoring.value = enabled
        if (enabled) {
            startAiMonitoring()
        } else {
            aiMonitorJob?.cancel()
        }
    }

    private fun startAiMonitoring() {
        aiMonitorJob?.cancel()
        aiMonitorJob = viewModelScope.launch {
            while (_isAiMonitoring.value) {
                if (connectionState.value == ObdState.CONNECTED && telemetryHistory.value.isNotEmpty()) {
                    val vehicleInfo = _selectedVehicle.value?.let { "${it.make} ${it.model}" } ?: "Generic Vehicle"
                    val anomalies = geminiDiagnostic.checkHealth(vehicleInfo, telemetryHistory.value)
                    _anomalousPids.value = anomalies
                    updateHealthScore()
                }
                kotlinx.coroutines.delay(30000) // Check every 30 seconds
            }
        }
    }


    fun startDataLogging() {
        _isLogging.value = true
        _dataLog.value = emptyList()
        loggingJob?.cancel()
        loggingJob = viewModelScope.launch {
            while (_isLogging.value) {
                val current = _liveData.value
                if (current.isNotEmpty()) {
                    _dataLog.value = _dataLog.value + DataLogEntry(System.currentTimeMillis(), current)
                }
                kotlinx.coroutines.delay(500)
            }
        }
    }

    fun stopDataLogging() {
        _isLogging.value = false
        loggingJob?.cancel()
    }

    fun saveCsvToFile() {
        val data = _dataLog.value
        if (data.isEmpty()) return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val fileName = "MEET_Log_${System.currentTimeMillis()}.csv"
                val file = java.io.File(context.getExternalFilesDir(null), fileName)
                java.io.FileWriter(file).use { writer ->
                    // Header
                    val pids = data.first().values.keys.toList()
                    writer.write("Timestamp," + pids.joinToString(",") + "\n")
                    
                    // Data
                    data.forEach { entry ->
                        val row = mutableListOf<String>()
                        row.add(entry.timestamp.toString())
                        pids.forEach { pid ->
                            row.add(entry.values[pid]?.toString() ?: "")
                        }
                        writer.write(row.joinToString(",") + "\n")
                    }
                }
                
                // Share file
                shareFile(file)
            } catch (e: Exception) {
                android.util.Log.e("ObdVM", "CSV export failed: ${e.message}", e)
                _cloudSyncState.value = "❌ Error al exportar CSV: ${e.message}"
            }
        }
    }

    private fun shareFile(file: java.io.File) {
        val uri: android.net.Uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Compartir Log de MEET").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }


    private fun updateHealthScore() {
        _healthScore.value = calculateHealthScore()
    }

    private fun calculateHealthScore(
        active: List<String> = activeDtcs.value,
        pending: List<String> = pendingDtcs.value,
        anomalies: List<com.elysium369.meet.core.ai.HealthAnomaly> = _anomalousPids.value,
        live: Map<String, Float> = _liveData.value
    ): Int {
        var score = 100
        
        // Subtract for DTCs (High priority)
        score -= (active.size * 25)
        score -= (pending.size * 10)
        
        // Subtract for AI Anomalies (Medium priority)
        score -= (anomalies.size * 15)
        
        // Critical Sensor Thresholds
        // 1. Engine Coolant Temperature (PID 0105)
        val temp = live["0105"]
        if (temp != null) {
            if (temp > 115f) score -= 30 // Overheating
            else if (temp > 105f) score -= 10 // Warning
        }

        // 2. Control Module Voltage (PID 0142)
        // Note: ATRV (ELM327 internal voltage) is not stored in liveData map
        val voltage = live["0142"]
        if (voltage != null) {
            if (voltage < 11.5f) score -= 20 // Low battery/alternator
            else if (voltage < 12.2f && (live["010C"] ?: 0f) < 100f) score -= 5 // Weak battery at rest
        }
        
        // 3. Fuel Trims (PID 0106, 0107) - Long term trim
        val ltft = live["0107"] ?: live["0109"]
        if (ltft != null && (ltft > 15f || ltft < -15f)) {
            score -= 10 // Fuel system richness/leanness
        }

        // 4. Misfires (Count detected or erratic RPM)
        // (Placeholder for more complex logic if misfire PIDs are available)
        
        return score.coerceIn(5, 100)
    }

    // --- Predictive Health Analysis ---
    fun runPredictiveAnalysis() {
        viewModelScope.launch(Dispatchers.IO) {
            _isAnalyzingHealth.value = true
            try {
                val vehicle = _selectedVehicle.value
                if (vehicle != null) {
                    val report = predictiveHealthEngine.computeHealthReport(
                        vehicleId = vehicle.id,
                        currentLiveData = _liveData.value,
                        activeDtcCount = activeDtcs.value.size,
                        pendingDtcCount = pendingDtcs.value.size,
                        anomalyCount = _anomalousPids.value.size
                    )
                    _predictiveHealthReport.value = report
                } else {
                    // No vehicle selected — compute with empty vehicle ID
                    val report = predictiveHealthEngine.computeHealthReport(
                        vehicleId = "default",
                        currentLiveData = _liveData.value,
                        activeDtcCount = activeDtcs.value.size,
                        pendingDtcCount = pendingDtcs.value.size,
                        anomalyCount = _anomalousPids.value.size
                    )
                    _predictiveHealthReport.value = report
                }
            } catch (e: Exception) {
                Log.e("ObdVM", "Predictive analysis failed", e)
            } finally {
                _isAnalyzingHealth.value = false
            }
        }
    }

    suspend fun analyzeOscilloscopeTelemetry(vehicleInfo: String, data: Map<String, List<Pair<Long, Float>>>): com.elysium369.meet.core.ai.DiagnosticResult {
        val currentConfig = _aiConfig.value
        geminiDiagnostic.updateConfig(
            newApiKey = currentConfig.apiKey,
            newEndpoint = currentConfig.endpoint,
            newProvider = currentConfig.provider
        )
        return geminiDiagnostic.analyzeLiveTelemetry(vehicleInfo, data)
    }
}

data class DataLogEntry(
    val timestamp: Long,
    val values: Map<String, Float>
)
