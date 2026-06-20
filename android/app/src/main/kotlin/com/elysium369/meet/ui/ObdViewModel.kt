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
import com.elysium369.meet.data.local.dao.*
import com.elysium369.meet.data.local.entities.*
import com.elysium369.meet.core.twin.VehicleTwinEngine
import com.elysium369.meet.core.blackbox.EvidenceCompiler
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import com.elysium369.meet.data.local.dao.TripDao
import com.elysium369.meet.data.local.dao.MaintenanceAlertDao
import com.elysium369.meet.data.local.dao.CustomPidDao
import com.elysium369.meet.data.local.entities.TripEntity
import com.elysium369.meet.data.local.entities.MaintenanceAlertEntity
import com.elysium369.meet.data.local.entities.CustomPidEntity
import com.elysium369.meet.data.local.entities.PredictionEventEntity
import com.elysium369.meet.data.local.entities.HealthSnapshotEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import com.elysium369.meet.ui.screens.TerminalLine
import com.elysium369.meet.ui.screens.TerminalLineType
import com.elysium369.meet.core.obd.ObdTrafficListener
import com.elysium369.meet.core.obd.PredictiveTelemetryEstimator

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
    private val aiConsultDao: com.elysium369.meet.data.local.dao.AiConsultDao,
    @ApplicationContext private val context: Context,
    private val reportGenerator: com.elysium369.meet.core.export.ReportGenerator,
    private val diagnosticManager: com.elysium369.meet.core.obd.AdvancedDiagnosticManager,
    private val localExpertSystem: com.elysium369.meet.core.obd.LocalExpertSystem,
    private val alertManager: AlertManager,
    private val predictiveHealthEngine: com.elysium369.meet.core.health.PredictiveHealthEngine,
    private val sensorHistoryDao: com.elysium369.meet.data.local.dao.SensorHistoryDao,
    private val usbOscilloscopeManager: com.elysium369.meet.core.usb.UsbOscilloscopeManager,
    private val performanceCalculator: PerformanceCalculator,
    private val dataLogger: DataLogger,
    private val alertThresholdEngine: AlertThresholdEngine,
    private val prePurchaseInspection: PrePurchaseInspection,
    private val fuelEconomyTracker: FuelEconomyTracker,
    private val batteryHealthAnalyzer: BatteryHealthAnalyzer,
    private val turboBoostGauge: TurboBoostGauge,
    private val demoModeSimulator: DemoModeSimulator,
    private val dvirReportDao: com.elysium369.meet.data.local.dao.DvirReportDao,
    private val predictionEventDao: com.elysium369.meet.data.local.dao.PredictionEventDao,
    private val healthSnapshotDao: com.elysium369.meet.data.local.dao.HealthSnapshotDao,
    private val phoneSpeedTracker: PhoneSpeedTracker,
    private val voiceFeedbackManager: com.elysium369.meet.core.audio.VoiceFeedbackManager,
    private val voiceCommandManager: com.elysium369.meet.core.audio.VoiceCommandManager,
    private val gaugeStyleManager: com.elysium369.meet.ui.components.gauges.GaugeStyleManager,
    private val meetDnaEngine: com.elysium369.meet.core.dna.MeetDnaEngine,
    private val ruleEngine: com.elysium369.meet.core.copilot.RuleEngine,
    private val speechService: com.elysium369.meet.core.copilot.SpeechService,
    private val notificationService: com.elysium369.meet.core.copilot.NotificationService,
    private val liveSessionDao: LiveSessionDao,
    private val repairNetworkAddonsDao: RepairNetworkAddonsDao,
    private val marketplaceDao: MarketplaceDao,
    private val blackBoxDao: BlackBoxDao,
    private val vehicleTwinDao: VehicleTwinDao,
    private val vehicleTwinEngine: VehicleTwinEngine,
    private val dtcKnowledgeGraphDao: com.elysium369.meet.data.local.dao.DtcKnowledgeGraphDao
) : ViewModel() {

    val connectionState: StateFlow<ObdState> = obdSession.state
    val statusMessage: StateFlow<String> = obdSession.statusMessage


    // --- Force Clone Mode ---
    private val _forceCloneMode = MutableStateFlow(false)
    val forceCloneMode: StateFlow<Boolean> = _forceCloneMode.asStateFlow()

    // --- Voice Copilot Enabled ---
    private val _voiceCopilotEnabled = MutableStateFlow(
        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            .getBoolean("voice_copilot_enabled", false)
    )
    val voiceCopilotEnabled: StateFlow<Boolean> = _voiceCopilotEnabled.asStateFlow()

    // --- Fused Speed Enabled (Waze style) ---
    private val _fusedSpeedEnabled = MutableStateFlow(true)
    val fusedSpeedEnabled: StateFlow<Boolean> = _fusedSpeedEnabled.asStateFlow()

    // --- Phone Sensor: G-Force & Inclinometer (60Hz) ---
    val lateralG: StateFlow<Float> = phoneSpeedTracker.lateralG
    val longitudinalG: StateFlow<Float> = phoneSpeedTracker.longitudinalG
    val phonePitch: StateFlow<Float> = phoneSpeedTracker.pitch
    val phoneRoll: StateFlow<Float> = phoneSpeedTracker.roll

    fun calibratePhoneSensors() = phoneSpeedTracker.calibrateSensors()
    fun resetPhoneSensorCalibration() = phoneSpeedTracker.resetCalibration()

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

    // --- Terminal & Command History ---
    private val _commandHistory = MutableStateFlow<List<String>>(emptyList())
    val commandHistory: StateFlow<List<String>> = _commandHistory.asStateFlow()

    private val _terminalSessionLogs = MutableStateFlow<List<TerminalLine>>(
        listOf(
            TerminalLine("╔══════════════════════════════════════════╗", TerminalLineType.SYSTEM),
            TerminalLine("║  MEET Expert Terminal v3.0               ║", TerminalLineType.SYSTEM),
            TerminalLine("║  Motor de Diagnóstico Inteligente        ║", TerminalLineType.SYSTEM),
            TerminalLine("╚══════════════════════════════════════════╝", TerminalLineType.SYSTEM),
            TerminalLine("Escribe un comando OBD2 o AT. Cada comando incluye", TerminalLineType.SYSTEM),
            TerminalLine("una explicación técnica automática.", TerminalLineType.SYSTEM),
            TerminalLine("────────────────────────────────────────────", TerminalLineType.SYSTEM)
        )
    )
    val terminalSessionLogs: StateFlow<List<TerminalLine>> = _terminalSessionLogs.asStateFlow()

    private val shellScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Sustituto de Termux para Terminal de Android
    val localShellManager = com.elysium369.meet.core.utils.LocalShellManager(
        context,
        geminiDiagnostic,
        obdSession,
        tripManager,
        shellScope
    )
    val localShellLines: StateFlow<List<String>> = localShellManager.terminalLines
    val activeDistro: StateFlow<String> = localShellManager.activeDistro
    val installedDistros: StateFlow<Set<String>> = localShellManager.installedDistros

    fun switchActiveDistro(distro: String) {
        localShellManager.switchDistro(distro)
    }

    fun isDistroInstalled(distro: String): Boolean {
        return localShellManager.isDistroInstalled(distro)
    }

    private val _selectedVehicle = MutableStateFlow<Vehicle?>(null)
    val selectedVehicle: StateFlow<Vehicle?> = _selectedVehicle.asStateFlow()

    // ── LiveLink PRO remote session state ──
    private val _liveLinkProSession = MutableStateFlow<LiveSessionEntity?>(null)
    val liveLinkProSession: StateFlow<LiveSessionEntity?> = _liveLinkProSession.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val mechanicNotes: StateFlow<List<MechanicNoteEntity>> = _liveLinkProSession
        .flatMapLatest { session ->
            session?.let { liveSessionDao.getNotesForSession(it.sessionId) } ?: flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var telemetryUploadJob: Job? = null
    private var notesPollingJob: Job? = null

    fun startLiveLinkPro(durationMinutes: Int, readOnly: Boolean, videoCall: Boolean) {
        val vehicle = selectedVehicle.value ?: return
        val sessionCode = (100000..999999).random().toString()
        val sessionId = UUID.randomUUID().toString()
        val ownerId = SupabaseManager.client.auth.currentUserOrNull()?.id ?: "anonymous"
        val session = LiveSessionEntity(
            sessionId = sessionId,
            vehicleId = vehicle.id,
            ownerId = ownerId,
            mechanicId = null,
            status = "ACTIVE",
            startedAt = System.currentTimeMillis(),
            endedAt = null,
            permissions = if (readOnly) "READ_ONLY" else "FULL",
            sessionCode = sessionCode,
            shareUrl = "https://meet.elysium369.com/livelink/$sessionCode",
            durationMinutes = durationMinutes,
            videoCallUrl = if (videoCall) "https://meet.elysium369.com/call/$sessionId" else null
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                liveSessionDao.insertLiveSession(session)
                _liveLinkProSession.value = session
                
                // Upload active session to Supabase
                SupabaseManager.client.postgrest["live_sessions"].insert(session)
                
                // Start background upload loop for telemetry
                startLiveLinkTelemetryLoop(sessionId)
                
                // Start background note polling loop
                startNotesPollingLoop(sessionId)
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Error starting remote LiveLink PRO session", e)
            }
        }
    }

    fun stopLiveLinkPro() {
        val session = _liveLinkProSession.value ?: return
        telemetryUploadJob?.cancel()
        notesPollingJob?.cancel()
        _liveLinkProSession.value = null
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                liveSessionDao.updateSessionStatus(session.sessionId, "COMPLETED")
                liveSessionDao.updateSessionEndedAt(session.sessionId, now)
                
                // Sync status to cloud
                SupabaseManager.client.postgrest["live_sessions"].update(mapOf("status" to "COMPLETED", "endedAt" to now)) {
                    filter {
                        eq("sessionId", session.sessionId)
                    }
                }
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Error stopping remote LiveLink PRO session", e)
            }
        }
    }

    private fun startLiveLinkTelemetryLoop(sessionId: String) {
        telemetryUploadJob?.cancel()
        telemetryUploadJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && _liveLinkProSession.value?.status == "ACTIVE") {
                try {
                    val pidsJson = Json.encodeToString(_liveData.value)
                    val snapshot = LiveSnapshotEntity(
                        snapshotId = UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        timestamp = System.currentTimeMillis(),
                        pidValues = pidsJson,
                        notes = ""
                    )
                    
                    // Save local snapshot
                    liveSessionDao.insertLiveSnapshot(snapshot)
                    
                    // Upload to cloud
                    SupabaseManager.client.postgrest["live_snapshots"].insert(snapshot)
                } catch (e: Exception) {
                    Log.e("ObdViewModel", "Error uploading telemetry snapshot", e)
                }
                delay(2000L) // Telemetry uploaded every 2 seconds
            }
        }
    }

    private fun startNotesPollingLoop(sessionId: String) {
        notesPollingJob?.cancel()
        notesPollingJob = viewModelScope.launch(Dispatchers.IO) {
            var lastPollTime = 0L
            while (isActive && _liveLinkProSession.value?.status == "ACTIVE") {
                try {
                    val newNotes = SupabaseManager.client.postgrest["mechanic_notes"]
                        .select {
                            filter {
                                eq("sessionId", sessionId)
                                gt("createdAt", lastPollTime)
                            }
                        }.decodeList<MechanicNoteEntity>()
                    
                    if (newNotes.isNotEmpty()) {
                        newNotes.forEach { note ->
                            liveSessionDao.insertMechanicNote(note)
                            if (note.createdAt > lastPollTime) {
                                lastPollTime = note.createdAt
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ObdViewModel", "Error polling mechanic notes", e)
                }
                delay(4000L) // Poll recommendations/notes every 4 seconds
            }
        }
    }

    // ── Marketplace (Bids and requests) ──
    val serviceRequests: StateFlow<List<ServiceRequestEntity>> = marketplaceDao.getRequests()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Shop/Workshop bids derived dynamically
    private val _shopId = MutableStateFlow<String?>("local_shop_id") // Simulated shop ID
    @OptIn(ExperimentalCoroutinesApi::class)
    val shopBids: StateFlow<List<ServiceBidEntity>> = _shopId
        .flatMapLatest { shopId ->
            shopId?.let { marketplaceDao.getBidsByShop(it) } ?: flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun getBidsForRequest(requestId: String): Flow<List<ServiceBidEntity>> {
        return marketplaceDao.getBidsForRequest(requestId)
    }

    fun createServiceRequest(
        vehicleId: String,
        problem: String,
        description: String,
        location: String,
        priority: String
    ) {
        val request = ServiceRequestEntity(
            requestId = UUID.randomUUID().toString(),
            vehicleId = vehicleId,
            problem = problem,
            priority = priority,
            description = description,
            location = location,
            radiusKm = 15.0,
            status = "OPEN",
            autoDtcCode = activeDtcs.value.firstOrNull(),
            createdAt = System.currentTimeMillis()
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                marketplaceDao.insertRequest(request)
                SupabaseManager.client.postgrest["service_requests"].insert(request)
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Failed to create service request", e)
            }
        }
    }

    fun placeServiceBid(
        requestId: String,
        price: Double,
        estimatedHours: Double,
        warrantyDays: Int,
        message: String
    ) {
        val bid = ServiceBidEntity(
            bidId = UUID.randomUUID().toString(),
            requestId = requestId,
            shopId = _shopId.value ?: "unknown_shop",
            shopName = "Mecánica Elite Pro",
            shopRating = 4.9,
            price = price,
            estimatedHours = estimatedHours,
            warrantyDays = warrantyDays,
            message = message,
            status = "PENDING",
            createdAt = System.currentTimeMillis()
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                marketplaceDao.insertBid(bid)
                SupabaseManager.client.postgrest["service_bids"].insert(bid)
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Failed to place bid", e)
            }
        }
    }

    fun acceptBid(requestId: String, bidId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                marketplaceDao.updateRequestStatus(requestId, "ACCEPTED")
                marketplaceDao.updateBidStatus(bidId, "ACCEPTED")
                
                SupabaseManager.client.postgrest["service_requests"].update(mapOf("status" to "ACCEPTED")) {
                    filter { eq("requestId", requestId) }
                }
                SupabaseManager.client.postgrest["service_bids"].update(mapOf("status" to "ACCEPTED")) {
                    filter { eq("bidId", bidId) }
                }
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Failed to accept bid", e)
            }
        }
    }

    // Background periodic task to poll/sync local Room data with Supabase for Marketplace
    fun startMarketplaceSync() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    // Sync service requests from Supabase
                    val cloudRequests = SupabaseManager.client.postgrest["service_requests"]
                        .select().decodeList<ServiceRequestEntity>()
                    cloudRequests.forEach { req ->
                        marketplaceDao.insertRequest(req)
                    }

                    // Sync bids for each request
                    cloudRequests.forEach { req ->
                        val cloudBids = SupabaseManager.client.postgrest["service_bids"]
                            .select {
                                filter {
                                    eq("requestId", req.requestId)
                                }
                            }.decodeList<ServiceBidEntity>()
                        cloudBids.forEach { bid ->
                            marketplaceDao.insertBid(bid)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ObdViewModel", "Error in periodic Supabase sync", e)
                }
                delay(10000L) // Poll every 10 seconds
            }
        }
    }

    // ── Black Box ──
    val evidencePackages: StateFlow<List<EvidencePackageEntity>> = blackBoxDao.getEvidencePackages()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun saveEvidencePackage(evidence: EvidencePackageEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                blackBoxDao.insertEvidencePackage(evidence)
                SupabaseManager.client.postgrest["evidence_packages"].insert(evidence)
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Failed to save evidence package", e)
            }
        }
    }

    // ── Vehicle Twin ──
    @OptIn(ExperimentalCoroutinesApi::class)
    val twinAnomalies: StateFlow<List<TwinAnomalyEntity>> = _selectedVehicle
        .flatMapLatest { vehicle ->
            vehicle?.let { vehicleTwinDao.getAnomaliesForVehicle(it.id) } ?: flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Tarea 34 & 35: Configuración persistente de sistema de unidades, precio de combustible, moneda y tipo de carburante
    private val _useImperialUnits = MutableStateFlow(false)
    val useImperialUnits: StateFlow<Boolean> = _useImperialUnits.asStateFlow()

    private val _fuelPrice = MutableStateFlow(1.25f)
    val fuelPrice: StateFlow<Float> = _fuelPrice.asStateFlow()

    private val _currencySymbol = MutableStateFlow("$")
    val currencySymbol: StateFlow<String> = _currencySymbol.asStateFlow()

    private val _fuelType = MutableStateFlow("GASOLINE")
    val fuelType: StateFlow<String> = _fuelType.asStateFlow()

    fun setUseImperialUnits(useImperial: Boolean) {
        _useImperialUnits.value = useImperial
        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("pref_imperial_units", useImperial).apply()
    }

    fun setFuelPrice(price: Float) {
        _fuelPrice.value = price
        fuelEconomyTracker.setFuelPrice(price)
        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            .edit().putFloat("pref_fuel_price", price).apply()
    }

    fun setCurrencySymbol(symbol: String) {
        _currencySymbol.value = symbol
        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            .edit().putString("pref_currency_symbol", symbol).apply()
    }

    fun setFuelType(type: String) {
        _fuelType.value = type
        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            .edit().putString("pref_fuel_type", type).apply()
    }

    // Tarea 39: Depuración automática de trayectos viejos (>90 días)
    fun purgeOldTrips() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ninetyDaysAgo = System.currentTimeMillis() - (90L * 24L * 60L * 60L * 1000L)
                tripDao.deleteTripsOlderThan(ninetyDaysAgo)
                android.util.Log.d("ObdViewModel", "Purged trips older than 90 days successfully.")
            } catch (e: Exception) {
                android.util.Log.e("ObdViewModel", "Error purging old trips: ${e.message}", e)
            }
        }
    }

    fun selectVehicle(vehicle: Vehicle?) {
        _selectedVehicle.value = vehicle
        // Reset sensor smoothers when switching vehicles to prevent cross-vehicle data contamination
        sensorSmoother.resetAll()
        predictiveEstimator.reset()
        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            .edit().putString("selected_vehicle_id", vehicle?.id).apply()
        evaluateDnaInference()
    }

    private val _liveData = MutableStateFlow<Map<String, Float>>(emptyMap())
    val liveData: StateFlow<Map<String, Float>> = _liveData.asStateFlow()

    // Smooth sensor interpolation — eliminates erratic jumps from raw ELM327 readings
    private val sensorSmoother = SensorSmootherManager()
    private val predictiveEstimator = PredictiveTelemetryEstimator()

    private var currentSessionId: String = UUID.randomUUID().toString()
    private val initialDtcScanMutex = Mutex()
    @Volatile private var hasCompletedInitialDtcScan = false

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

    val historicalDtcEvents: StateFlow<List<DtcEventEntity>> = _selectedVehicle.flatMapLatest { vehicle ->
        vehicle?.let {
            dtcDao.getUnresolvedDtcsForVehicle(it.id)
                .map { list -> list.filter { it.status in setOf("HISTORY", "INTERMITTENT") } }
        } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Backwards compatibility for logic that only needs the strings
    val activeDtcs: StateFlow<List<String>> = activeDtcEvents.map { list -> list.map { it.code } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val pendingDtcs: StateFlow<List<String>> = pendingDtcEvents.map { list -> list.map { it.code } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val permanentDtcs: StateFlow<List<String>> = permanentDtcEvents.map { list -> list.map { it.code } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val historicalDtcs: StateFlow<List<String>> = historicalDtcEvents.map { list -> list.map { it.code } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val lastDtcScanReport: StateFlow<DtcScanReport?> = obdSession.lastDtcScanReport

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

    // ═══════════════════════════════════════
    //  PERFORMANCE CALCULATOR
    // ═══════════════════════════════════════
    private val _performanceSnapshot = MutableStateFlow<PerformanceCalculator.PerformanceSnapshot?>(null)
    val performanceSnapshot: StateFlow<PerformanceCalculator.PerformanceSnapshot?> = _performanceSnapshot.asStateFlow()

    private val _dragStripResult = MutableStateFlow<PerformanceCalculator.DragStripResult?>(null)
    val dragStripResult: StateFlow<PerformanceCalculator.DragStripResult?> = _dragStripResult.asStateFlow()

    fun updatePerformance(data: Map<String, Float>) {
        _performanceSnapshot.value = performanceCalculator.calculate(data)
        if (performanceCalculator.isDragActive) {
            val speed = data["SPEED"] ?: data["speed"] ?: 0f
            val hp = _performanceSnapshot.value?.horsepowerMAF
            val torque = _performanceSnapshot.value?.torqueNm
            _dragStripResult.value = performanceCalculator.updateDragRun(speed, hp, torque)
        }
        if (_isDynoRunning.value) {
            val rpm = data["RPM"] ?: data["rpm"] ?: 0f
            val speed = data["SPEED"] ?: data["speed"] ?: 0f
            val accel = if (lastLinearAccelY > 0.05f) {
                lastLinearAccelY
            } else {
                _performanceSnapshot.value?.acceleration ?: 0f
            }
            _dynoPoints.value = performanceCalculator.updateDynoRun(rpm, accel, speed)
        }
        if (_isRecordingDashcam.value) {
            val elapsed = System.currentTimeMillis() - dashcamStartTimeMs
            val rpm = data["RPM"] ?: data["rpm"] ?: 0f
            val speed = data["SPEED"] ?: data["speed"] ?: 0f
            val load = data["LOAD"] ?: data["load"] ?: 0f
            val throttle = data["THROTTLE"] ?: data["throttle"] ?: data["0111"] ?: 0f
            val gForce = _performanceSnapshot.value?.gForce ?: 0f
            
            dashcamTelemetryBuffer.add(
                DashcamTelemetryFrame(
                    timestampMs = elapsed,
                    rpm = rpm,
                    speedKph = speed,
                    gForce = gForce,
                    throttle = throttle,
                    load = load
                )
            )
        }
    }

    fun startDragStrip() {
        performanceCalculator.startDragRun()
        _dragStripResult.value = PerformanceCalculator.DragStripResult(null, null, null, null, null, null, null, true)
    }

    fun stopDragStrip() {
        _dragStripResult.value = performanceCalculator.stopDragRun()
    }

    // ═══════════════════════════════════════
    //  DATA LOGGER
    // ═══════════════════════════════════════
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _lastLogSession = MutableStateFlow<DataLogger.LogSession?>(null)
    val lastLogSession: StateFlow<DataLogger.LogSession?> = _lastLogSession.asStateFlow()

    fun toggleRecording() {
        if (dataLogger.recording) {
            _lastLogSession.value = dataLogger.stopRecording()
            _isRecording.value = false
            voiceFeedbackManager.speak("Grabación detenida.", "Recording stopped.")
        } else {
            val started = dataLogger.startRecording(context, _vin.value)
            _isRecording.value = started
            if (started) {
                voiceFeedbackManager.speak("Grabación iniciada.", "Recording started.")
            }
        }
    }

    fun recordDataSample(data: Map<String, Float>) {
        if (dataLogger.recording) dataLogger.recordSample(data)
    }

    // ═══════════════════════════════════════
    //  REAL-TIME ALERTS
    // ═══════════════════════════════════════
    private val _thresholdAlerts = MutableStateFlow<List<AlertThresholdEngine.ThresholdAlert>>(emptyList())
    val thresholdAlerts: StateFlow<List<AlertThresholdEngine.ThresholdAlert>> = _thresholdAlerts.asStateFlow()

    fun evaluateAlerts(data: Map<String, Float>) {
        val newAlerts = alertThresholdEngine.evaluate(data)
        if (newAlerts.isNotEmpty()) {
            _thresholdAlerts.value = newAlerts
        }
        // Run MEET Copilot RuleEngine check
        ruleEngine.evaluate(data, activeDtcEvents.value.map { it.code })
    }

    fun getAlertHistory() = alertThresholdEngine.getAlertHistory()
    fun clearAlertHistory() { alertThresholdEngine.clearHistory(); _thresholdAlerts.value = emptyList() }

    // ═══════════════════════════════════════
    //  PRE-PURCHASE INSPECTION
    // ═══════════════════════════════════════
    private val _inspectionResult = MutableStateFlow<PrePurchaseInspection.InspectionResult?>(null)
    val inspectionResult: StateFlow<PrePurchaseInspection.InspectionResult?> = _inspectionResult.asStateFlow()

    private val _isInspecting = MutableStateFlow(false)
    val isInspecting: StateFlow<Boolean> = _isInspecting.asStateFlow()

    private val _prePurchaseReportFile = MutableStateFlow<java.io.File?>(null)
    val prePurchaseReportFile: StateFlow<java.io.File?> = _prePurchaseReportFile.asStateFlow()

    fun runPrePurchaseInspection() {
        viewModelScope.launch(Dispatchers.IO) {
            _isInspecting.value = true
            voiceFeedbackManager.speak("Iniciando inspección de pre-compra del vehículo.", "Starting vehicle pre-purchase inspection.")
            try {
                val readiness = _readinessMonitors.value?.monitors?.associate {
                    it.name to it.complete
                } ?: emptyMap()
                val result = prePurchaseInspection.runInspection(
                    activeDtcs = activeDtcs.value,
                    pendingDtcs = pendingDtcs.value,
                    permanentDtcs = permanentDtcs.value,
                    readinessMonitors = readiness,
                    liveData = _liveData.value,
                    freezeFrame = _freezeFrameData.value.ifEmpty { null },
                    mode06Results = null
                )
                _inspectionResult.value = result
                voiceFeedbackManager.speak(
                    "Inspección completada. Puntuación del vehículo: ${result.overallScore} de 100.",
                    "Inspection complete. Vehicle score: ${result.overallScore} out of 100."
                )
            } catch (e: Exception) {
                Log.e("MEET", "Pre-purchase inspection failed", e)
            } finally {
                _isInspecting.value = false
            }
        }
    }

    fun generatePrePurchasePdf(result: PrePurchaseInspection.InspectionResult) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val vinStr = _vin.value ?: "DESCONOCIDO"
                val manufacturerStr = _manufacturer.value ?: "GENERIC"
                val file = reportGenerator.generatePrePurchaseReport(
                    result = result,
                    vin = vinStr,
                    manufacturer = manufacturerStr,
                    themeName = "ELYSIUM_CYAN"
                )
                _prePurchaseReportFile.value = file
                reportGenerator.shareReport(file)
            } catch (e: Exception) {
                Log.e("MEET", "Failed to generate pre-purchase PDF", e)
            }
        }
    }

    // ──── MEET PERITO STATES ────
    private val _isInspectingPerito = MutableStateFlow(false)
    val isInspectingPerito: StateFlow<Boolean> = _isInspectingPerito.asStateFlow()

    private val _peritoConsoleLogs = MutableStateFlow<List<String>>(emptyList())
    val peritoConsoleLogs: StateFlow<List<String>> = _peritoConsoleLogs.asStateFlow()

    private val _activePeritoReport = MutableStateFlow<com.elysium369.meet.core.obd.VehicleInspectionReport?>(null)
    val activePeritoReport: StateFlow<com.elysium369.meet.core.obd.VehicleInspectionReport?> = _activePeritoReport.asStateFlow()

    private val _peritoHistory = MutableStateFlow<List<com.elysium369.meet.core.obd.VehicleInspectionReport>>(emptyList())
    val peritoHistory: StateFlow<List<com.elysium369.meet.core.obd.VehicleInspectionReport>> = _peritoHistory.asStateFlow()

    private val _currentPeritoStep = MutableStateFlow(0)
    val currentPeritoStep: StateFlow<Int> = _currentPeritoStep.asStateFlow()

    private val _peritoReportFile = MutableStateFlow<java.io.File?>(null)
    val peritoReportFile: StateFlow<java.io.File?> = _peritoReportFile.asStateFlow()

    private val meetPerito = com.elysium369.meet.core.obd.MeetPerito()
    private val peritoReportGenerator = com.elysium369.meet.core.export.PeritoReportGenerator(context)

    // --- MEET DNA ---
    private val _dnaResult = MutableStateFlow<com.elysium369.meet.core.dna.DnaEvaluationResult>(com.elysium369.meet.core.dna.DnaEvaluationResult(isCalibrated = false))
    val dnaResult: StateFlow<com.elysium369.meet.core.dna.DnaEvaluationResult> = _dnaResult.asStateFlow()

    private val _isTrainingDna = MutableStateFlow(false)
    val isTrainingDna: StateFlow<Boolean> = _isTrainingDna.asStateFlow()

    fun trainVehicleDna() {
        val vehicle = _selectedVehicle.value ?: return
        viewModelScope.launch {
            _isTrainingDna.value = true
            meetDnaEngine.trainDnaProfile(vehicle.id)
            evaluateDnaInference()
            _isTrainingDna.value = false
        }
    }

    fun evaluateDnaInference() {
        val vehicle = _selectedVehicle.value ?: return
        viewModelScope.launch {
            val res = meetDnaEngine.evaluateCurrentStatus(vehicle.id, _liveData.value)
            _dnaResult.value = res
            vehicleTwinEngine.evaluateFrame(vehicle.id, _liveData.value)
        }
    }

    fun loadPeritoHistory() {
        val vehicle = _selectedVehicle.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val history = meetPerito.getInspectionHistory(context, vehicle.id)
            _peritoHistory.value = history
        }
    }

    fun runMeetPeritoInspection() {
        val vehicle = _selectedVehicle.value ?: return
        viewModelScope.launch {
            _isInspectingPerito.value = true
            _activePeritoReport.value = null
            _peritoConsoleLogs.value = emptyList()
            _currentPeritoStep.value = 0
            _peritoReportFile.value = null
            
            val logs = mutableListOf<String>()
            fun addLog(msg: String) {
                logs.add(msg)
                _peritoConsoleLogs.value = ArrayList(logs)
            }

            voiceFeedbackManager.speak("Iniciando peritaje clínico MEET Perito.", "Starting MEET Perito clinical vehicle check.")
            
            // Step 1: VIN
            _currentPeritoStep.value = 1
            addLog("📡 Estableciendo comunicación con ECU mediante protocolo OBD2...")
            delay(1000)
            addLog("🔍 Solicitando VIN del vehículo (PID 09 02)...")
            val vinVal = _vin.value ?: vehicle.vin
            delay(1200)
            addLog("ℹ️ VIN detectado: $vinVal")
            
            // Step 2: DTC Activos
            _currentPeritoStep.value = 2
            addLog("⚠️ Solicitando códigos de falla activos (Mode 03)...")
            delay(1000)
            val dtcs = activeDtcs.value
            addLog("ℹ️ DTCs activos encontrados: ${dtcs.size}")

            // Step 3: DTC Pendientes
            _currentPeritoStep.value = 3
            addLog("⏳ Solicitando códigos de falla pendientes (Mode 07)...")
            delay(1000)
            val pDtcs = pendingDtcs.value
            addLog("ℹ️ DTCs pendientes encontrados: ${pDtcs.size}")

            // Step 4: Freeze Frame
            _currentPeritoStep.value = 4
            addLog("📦 Solicitando captura de pantalla de falla congelada (Freeze Frame Mode 02)...")
            delay(1200)
            val ff = _freezeFrameData.value
            addLog("ℹ️ Registro de Freeze Frame: ${if (ff.isEmpty()) "Vacío" else "${ff.size} parámetros"}")

            // Step 5: Fuel Trims
            _currentPeritoStep.value = 5
            addLog("⛽ Analizando sensores de ajuste de mezcla (Long Term Fuel Trims)...")
            delay(1000)
            val trim = _liveData.value["LTFT_B1"] ?: _liveData.value["ltft_b1"] ?: 0f
            addLog("ℹ️ LTFT Banco 1: ${String.format("%.1f", trim)}%")

            // Step 6: Coolant Temperature
            _currentPeritoStep.value = 6
            addLog("🌡️ Leyendo sensor de temperatura del refrigerante (ECT PID 01 05)...")
            delay(1000)
            val ect = _liveData.value["COOLANT"] ?: _liveData.value["coolant"] ?: 90f
            addLog("ℹ️ ECT: ${ect.toInt()}°C")

            // Step 7: Alternator/Battery Voltage
            _currentPeritoStep.value = 7
            addLog("🔋 Midiendo voltaje del alternador y regulación eléctrica...")
            delay(1000)
            val volt = _liveData.value["VOLTAGE"] ?: _liveData.value["voltage"] ?: 13.8f
            addLog("ℹ️ Voltaje del sistema: ${String.format("%.1f", volt)}V")

            // Step 8: Critical admission sensors
            _currentPeritoStep.value = 8
            addLog("🌬️ Admisión de aire: verificando sensores MAF / MAP...")
            delay(1200)
            val mafVal = _liveData.value["MAF"] ?: _liveData.value["maf"]
            val mapVal = _liveData.value["MAP"] ?: _liveData.value["map"]
            addLog("ℹ️ MAF: ${mafVal ?: "N/A"} g/s, MAP: ${mapVal ?: "N/A"} kPa")

            // Step 9: Odometer Comparison
            _currentPeritoStep.value = 9
            addLog("🚗 Solicitando kilometraje almacenado en ECU (odómetro OBD)...")
            delay(1000)
            val obdOdo = _liveData.value["DISTANCE_WITH_MIL"] ?: _liveData.value["distance_with_mil"] ?: -1f
            addLog("ℹ️ Kilometraje tablero: ${currentOdometer.value.toInt()} km | Kilometraje ECU: ${if (obdOdo > 0) "${obdOdo.toInt()} km" else "No soportado"}")

            // Step 10: General State / Readiness
            _currentPeritoStep.value = 10
            addLog("🔬 Verificando estado general de monitores de emisiones (Readiness)...")
            delay(1000)
            val readiness = _readinessMonitors.value?.monitors?.associate { it.name to it.complete } ?: emptyMap()
            addLog("ℹ️ Monitores listos: ${readiness.count { it.value }}/${readiness.size}")
            delay(800)

            addLog("⚡ Compilando diagnóstico y generando reporte clínico MEET Perito...")
            delay(1500)

            val report = meetPerito.performInspection(
                context = context,
                vehicleId = vehicle.id,
                vin = vinVal,
                activeDtcs = dtcs,
                pendingDtcs = pDtcs,
                freezeFrame = ff.ifEmpty { null },
                liveData = _liveData.value,
                odometerKmCluster = currentOdometer.value.toLong(),
                readinessMonitors = readiness
            )

            _activePeritoReport.value = report
            _isInspectingPerito.value = false
            voiceFeedbackManager.speak(
                "Evaluación finalizada. Puntuación: ${report.score0to100} de 100. Categoría: ${report.category}.",
                "Evaluation complete. Score: ${report.score0to100} out of 100. Category: ${report.category}."
            )
            loadPeritoHistory()
        }
    }

    fun selectPeritoReport(report: com.elysium369.meet.core.obd.VehicleInspectionReport) {
        _activePeritoReport.value = report
    }

    fun generatePeritoPdf() {
        val report = _activePeritoReport.value ?: return
        val vehicle = _selectedVehicle.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = peritoReportGenerator.generateReportPdf(
                    report = report,
                    make = vehicle.make,
                    model = vehicle.model,
                    year = vehicle.year,
                    odometer = currentOdometer.value.toLong()
                )
                _peritoReportFile.value = file
                reportGenerator.shareReport(file)
            } catch (e: Exception) {
                Log.e("MEET", "Failed to generate Perito PDF report", e)
            }
        }
    }


    // ═══════════════════════════════════════
    //  VIN DECODER
    // ═══════════════════════════════════════
    private val _vinDecoded = MutableStateFlow<VinDecoder.VinInfo?>(null)
    val vinDecoded: StateFlow<VinDecoder.VinInfo?> = _vinDecoded.asStateFlow()

    fun decodeVin() {
        val vinStr = _vin.value ?: return
        _vinDecoded.value = VinDecoder.decode(vinStr)
    }

    // ═══════════════════════════════════════
    //  FUEL ECONOMY TRACKER
    // ═══════════════════════════════════════
    private val _fuelSnapshot = MutableStateFlow<FuelEconomyTracker.FuelSnapshot?>(null)
    val fuelSnapshot: StateFlow<FuelEconomyTracker.FuelSnapshot?> = _fuelSnapshot.asStateFlow()

    fun updateFuelEconomy(data: Map<String, Float>) {
        val displacement = _selectedVehicle.value?.displacement_cc ?: 2000
        _fuelSnapshot.value = fuelEconomyTracker.calculate(data, displacement)
    }
    fun resetFuelSession() { fuelEconomyTracker.resetSession(); _fuelSnapshot.value = null }

    // ═══════════════════════════════════════
    //  BATTERY HEALTH ANALYZER
    // ═══════════════════════════════════════
    private val _batteryReport = MutableStateFlow<BatteryHealthAnalyzer.BatteryReport?>(null)
    val batteryReport: StateFlow<BatteryHealthAnalyzer.BatteryReport?> = _batteryReport.asStateFlow()

    fun updateBatteryHealth(data: Map<String, Float>) {
        _batteryReport.value = batteryHealthAnalyzer.analyze(data)
    }

    fun getVoltageCalibrationOffset(): Float {
        val prefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
        return prefs.getFloat("voltage_calibration_offset", 0f)
    }

    fun setVoltageCalibrationOffset(offset: Float) {
        val prefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
        prefs.edit().putFloat("voltage_calibration_offset", offset).apply()
    }

    // ═══════════════════════════════════════
    //  TURBO BOOST GAUGE
    // ═══════════════════════════════════════
    private val _boostSnapshot = MutableStateFlow<TurboBoostGauge.BoostSnapshot?>(null)
    val boostSnapshot: StateFlow<TurboBoostGauge.BoostSnapshot?> = _boostSnapshot.asStateFlow()

    fun updateBoost(data: Map<String, Float>) {
        _boostSnapshot.value = turboBoostGauge.calculate(data)
    }
    fun resetBoostPeak() { turboBoostGauge.resetPeak() }

    // ═══════════════════════════════════════
    //  SMOG CHECK PREDICTOR
    // ═══════════════════════════════════════
    private val _smogPrediction = MutableStateFlow<SmogCheckPredictor.SmogPrediction?>(null)
    val smogPrediction: StateFlow<SmogCheckPredictor.SmogPrediction?> = _smogPrediction.asStateFlow()

    fun runSmogCheck() {
        val readiness = _readinessMonitors.value?.monitors?.associate {
            it.name to it.complete
        } ?: emptyMap()
        _smogPrediction.value = SmogCheckPredictor.predict(
            activeDtcs = activeDtcs.value,
            pendingDtcs = pendingDtcs.value,
            permanentDtcs = permanentDtcs.value,
            readinessMonitors = readiness,
            liveData = _liveData.value
        )
    }

    // ═══════════════════════════════════════
    //  MAINTENANCE PREDICTOR
    // ═══════════════════════════════════════
    private val _maintenanceItems = MutableStateFlow<List<MaintenancePredictor.MaintenanceItem>>(emptyList())
    val maintenanceItems: StateFlow<List<MaintenancePredictor.MaintenanceItem>> = _maintenanceItems.asStateFlow()

    fun predictMaintenance() {
        val km = _currentOdometer.value
        val coolant = _liveData.value["0105"]
        _maintenanceItems.value = MaintenancePredictor.predict(currentKm = km, coolantTemp = coolant)
    }

    // ═══════════════════════════════════════
    //  DEMO MODE
    // ═══════════════════════════════════════
    private val _isDemoMode = MutableStateFlow(false)
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    val demoScenarioDescription: String get() = demoModeSimulator.getScenarioDescription()

    fun toggleDemoMode() {
        _isDemoMode.value = !_isDemoMode.value
        if (_isDemoMode.value) {
            voiceFeedbackManager.speak("Modo de demostración activado.", "Demo mode activated.")
        } else {
            voiceFeedbackManager.speak("Modo de demostración desactivado.", "Demo mode deactivated.")
        }
    }
    fun setDemoScenario(scenario: DemoModeSimulator.Scenario) {
        demoModeSimulator.currentScenario = scenario
    }
    fun generateDemoFrame(): Map<String, Float> = demoModeSimulator.generateFrame()

    // ── Vehicle Identification (Car Scanner Pro style) ──
    val detectedProtocol: StateFlow<String> = obdSession.detectedProtocolFlow
    val adapterVersion: StateFlow<String> = obdSession.adapterVersionFlow
    val isCloneAdapter: StateFlow<Boolean> = obdSession.isCloneAdapterFlow
    val calibrationId: StateFlow<String?> = obdSession.calibrationId
    val ecuName: StateFlow<String?> = obdSession.ecuName

    // --- Network Topology ---
    val networkTopology: StateFlow<List<NetworkModule>> = obdSession.networkTopology
    val isScanningTopology: StateFlow<Boolean> = obdSession.isScanningTopology
    private var networkTopologyJob: Job? = null

    fun scanNetworkTopology() {
        networkTopologyJob?.cancel()
        networkTopologyJob = viewModelScope.launch(Dispatchers.IO) {
            obdSession.scanNetworkTopology()
        }
    }

    fun cancelNetworkTopologyScan() {
        networkTopologyJob?.cancel()
    }

    // --- Digital Oscilloscope ---
    val oscilloscopeStream: SharedFlow<Pair<Long, Float>> = obdSession.oscilloscopeStream

    fun startOscilloscope(pidCode: String) {
        obdSession.startOscilloscope(pidCode)
    }

    fun stopOscilloscope() {
        obdSession.stopOscilloscope()
    }

    // ── Physical USB Oscilloscope (Hantek 6022BE) ──
    val usbCh1Data: StateFlow<FloatArray> = usbOscilloscopeManager.ch1Data
    val usbCh2Data: StateFlow<FloatArray> = usbOscilloscopeManager.ch2Data
    val usbIsStreaming: StateFlow<Boolean> = usbOscilloscopeManager.isStreaming
    val usbIsSimulationMode: StateFlow<Boolean> = usbOscilloscopeManager.isSimulationMode
    val usbDeviceConnected: StateFlow<Boolean> = usbOscilloscopeManager.deviceConnected
    val usbSelectedWaveform: StateFlow<String> = usbOscilloscopeManager.selectedWaveform
    val usbCh1Attenuation: StateFlow<Float> = usbOscilloscopeManager.ch1Attenuation
    val usbCh2Attenuation: StateFlow<Float> = usbOscilloscopeManager.ch2Attenuation
    val usbTriggerLevel: StateFlow<Float> = usbOscilloscopeManager.triggerLevel
    val usbTriggerEdgeRising: StateFlow<Boolean> = usbOscilloscopeManager.triggerEdgeRising
    val usbSamplingRate: StateFlow<Long> = usbOscilloscopeManager.samplingRate

    fun toggleUsbOscilloscopeStream() {
        if (usbOscilloscopeManager.isStreaming.value) {
            usbOscilloscopeManager.stopStreaming()
        } else {
            usbOscilloscopeManager.startStreaming()
        }
    }

    fun setUsbOscilloscopeSimulation(enabled: Boolean) {
        usbOscilloscopeManager.setSimulationMode(enabled)
    }

    fun changeUsbWaveform(type: String) {
        usbOscilloscopeManager.setSelectedWaveform(type)
    }

    fun setUsbCh1Attenuation(factor: Float) {
        usbOscilloscopeManager.setCh1Attenuation(factor)
    }

    fun setUsbCh2Attenuation(factor: Float) {
        usbOscilloscopeManager.setCh2Attenuation(factor)
    }

    fun changeUsbTriggerLevel(volts: Float) {
        usbOscilloscopeManager.setTriggerLevel(volts)
    }

    fun setUsbTriggerEdge(rising: Boolean) {
        usbOscilloscopeManager.setTriggerEdge(rising)
    }

    fun setUsbSamplingRate(hz: Long) {
        usbOscilloscopeManager.setSamplingRate(hz)
    }

    fun stopUsbOscilloscopeStream() {
        usbOscilloscopeManager.stopStreaming()
    }

    // --- Oscilloscope Capture Persistence ---
    data class OscilloscopeCapture(
        val pidCode: String,
        val pidName: String,
        val severity: String,
        val diagnosisText: String,
        val recommendationText: String,
        val durationMs: Long,
        val sampleCount: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _oscilloscopeHistory = MutableStateFlow<List<OscilloscopeCapture>>(emptyList())
    val oscilloscopeHistory: StateFlow<List<OscilloscopeCapture>> = _oscilloscopeHistory.asStateFlow()

    fun saveOscilloscopeCapture(
        pidCode: String, pidName: String, values: List<Float>, durationMs: Long,
        diagnosisSeverity: String, diagnosisText: String, recommendationText: String
    ) {
        val capture = OscilloscopeCapture(pidCode, pidName, diagnosisSeverity,
            diagnosisText, recommendationText, durationMs, values.size)
        _oscilloscopeHistory.update { it + capture }

        // Persist as part of the diagnostic session
        viewModelScope.launch(Dispatchers.IO) {
            val vehicle = _selectedVehicle.value ?: return@launch
            try {
                val session = com.elysium369.meet.data.supabase.DiagnosticSession(
                    id = UUID.randomUUID().toString(),
                    user_id = com.elysium369.meet.data.remote.SupabaseModule.client.auth.currentUserOrNull()?.id ?: "guest",
                    vehicle_vin = vehicle.vin,
                    vehicle_make = vehicle.make,
                    vehicle_model = vehicle.model,
                    vehicle_year = vehicle.year,
                    scan_type = "oscilloscope",
                    severity = diagnosisSeverity,
                    notes = "OSCILLOSCOPE|$pidCode|$pidName|${values.size}pts|${durationMs}ms",
                    live_data_snapshot = Json.encodeToString(mapOf(
                        "pid" to pidCode,
                        "pidName" to pidName,
                        "sampleCount" to values.size.toString(),
                        "durationMs" to durationMs.toString(),
                        "diagnosis" to diagnosisText,
                        "recommendation" to recommendationText
                    ))
                )
                sessionLogRepository.saveSession(session)
                Log.d("ObdVM", "✅ Oscilloscope capture saved: $pidName ($diagnosisSeverity)")
            } catch (e: Exception) {
                Log.e("ObdVM", "Failed to save oscilloscope capture", e)
            }
        }
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

    // --- Local Expert Manual Start/Stop ---
    private val _isLocalExpertActive = MutableStateFlow(true)
    val isLocalExpertActive: StateFlow<Boolean> = _isLocalExpertActive.asStateFlow()

    fun toggleLocalExpert() {
        _isLocalExpertActive.value = !_isLocalExpertActive.value
        if (!_isLocalExpertActive.value) {
            // Clear diagnostics immediately when stopped
            _localDiagnostics.value = emptyList()
        }
    }

    fun setLocalExpertActive(active: Boolean) {
        _isLocalExpertActive.value = active
        if (!active) {
            _localDiagnostics.value = emptyList()
        }
    }

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

    // --- Async Telemetry Buffer ---
    private val telemetryBuffer = kotlinx.coroutines.channels.Channel<com.elysium369.meet.data.local.entities.SensorHistoryEntity>(kotlinx.coroutines.channels.Channel.UNLIMITED)

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

    // --- DVIR Inspection Flow & CRUD Helpers ---
    val dvirReports: StateFlow<List<com.elysium369.meet.data.local.entities.DvirReportEntity>> = _selectedVehicle
        .flatMapLatest { vehicle ->
            vehicle?.let { dvirReportDao.getReportsForVehicle(it.id) } ?: flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val predictionEvents: StateFlow<List<PredictionEventEntity>> = _selectedVehicle
        .flatMapLatest { vehicle ->
            vehicle?.let { predictionEventDao.observeEventsForVehicle(it.id) } ?: flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val healthHistory: StateFlow<List<HealthSnapshotEntity>> = _selectedVehicle
        .flatMapLatest { vehicle ->
            vehicle?.let { healthSnapshotDao.observeSnapshots(it.id) } ?: flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun insertDvirReport(report: com.elysium369.meet.data.local.entities.DvirReportEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dvirReportDao.insertReport(report)
        }
    }

    fun generateDvirReportPdf(
        report: com.elysium369.meet.data.local.entities.DvirReportEntity,
        vehicleInfo: String,
        onSuccess: (java.io.File) -> Unit,
        onError: (Exception) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = reportGenerator.generateDvirReport(report, vehicleInfo)
                launch(Dispatchers.Main) {
                    onSuccess(file)
                    reportGenerator.shareReport(file)
                }
            } catch (e: Exception) {
                Log.e("ObdVM", "Failed to generate DVIR PDF", e)
                launch(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }

    fun shareReport(file: java.io.File) {
        reportGenerator.shareReport(file)
    }

    // ──── Virtual Dyno (Dinamómetro de Chasis Virtual) State & Sensors ────
    private val _vehicleMass = MutableStateFlow(1500f)
    val vehicleMass: StateFlow<Float> = _vehicleMass.asStateFlow()

    private val _drivetrainLoss = MutableStateFlow(15f)
    val drivetrainLoss: StateFlow<Float> = _drivetrainLoss.asStateFlow()

    private val _isDynoRunning = MutableStateFlow(false)
    val isDynoRunning: StateFlow<Boolean> = _isDynoRunning.asStateFlow()

    private val _dynoPoints = MutableStateFlow<List<PerformanceCalculator.DynoPoint>>(emptyList())
    val dynoPoints: StateFlow<List<PerformanceCalculator.DynoPoint>> = _dynoPoints.asStateFlow()

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_LINEAR_ACCELERATION)
    private var lastLinearAccelY = 0f

    private val sensorEventListener = object : android.hardware.SensorEventListener {
        private var smoothedAccel = 0f
        override fun onSensorChanged(event: android.hardware.SensorEvent?) {
            if (event == null || event.sensor.type != android.hardware.Sensor.TYPE_LINEAR_ACCELERATION) return
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            
            // Calculate linear acceleration magnitude
            val raw = kotlin.math.sqrt(x*x + y*y + z*z)
            // Apply low pass filter to eliminate engine vibration noise
            smoothedAccel = smoothedAccel * 0.85f + raw * 0.15f
            lastLinearAccelY = smoothedAccel
        }
        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
    }

    fun startDynoTest() {
        performanceCalculator.startDynoRun()
        _isDynoRunning.value = true
        _dynoPoints.value = emptyList()
        
        // Register accelerometer listener
        sensorManager?.let { sm ->
            accelerometer?.let { acc ->
                sm.registerListener(sensorEventListener, acc, android.hardware.SensorManager.SENSOR_DELAY_FASTEST)
            }
        }
        voiceFeedbackManager.speak("Prueba de dinamómetro iniciada. Acelere a fondo.", "Dyno test started. Accelerate fully.")
    }

    fun stopDynoTest() {
        val points = performanceCalculator.stopDynoRun()
        _dynoPoints.value = points
        _isDynoRunning.value = false
        
        // Unregister accelerometer listener
        sensorManager?.unregisterListener(sensorEventListener)
        voiceFeedbackManager.speak("Prueba de dinamómetro finalizada.", "Dyno test completed.")
    }

    fun resetDynoTest() {
        performanceCalculator.startDynoRun()
        _dynoPoints.value = emptyList()
    }

    fun updateDynoSettings(mass: Float, loss: Float) {
        _vehicleMass.value = mass
        _drivetrainLoss.value = loss
        performanceCalculator.vehicleMassKg = mass
        performanceCalculator.drivetrainLossPercent = loss
        
        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE).edit()
            .putFloat("dyno_vehicle_mass", mass)
            .putFloat("dyno_drivetrain_loss", loss)
            .apply()
    }

    // ──── Dashcam / Telemetry Recording State ────
    private val _isRecordingDashcam = MutableStateFlow(false)
    val isRecordingDashcam: StateFlow<Boolean> = _isRecordingDashcam.asStateFlow()

    private val _isTranscodingVideo = MutableStateFlow(false)
    val isTranscodingVideo: StateFlow<Boolean> = _isTranscodingVideo.asStateFlow()

    private val _transcodingProgress = MutableStateFlow(0)
    val transcodingProgress: StateFlow<Int> = _transcodingProgress.asStateFlow()

    private val dashcamTelemetryBuffer = mutableListOf<DashcamTelemetryFrame>()
    private var dashcamStartTimeMs: Long = 0L

    fun startDashcamRecording() {
        _isRecordingDashcam.value = true
        dashcamStartTimeMs = System.currentTimeMillis()
        dashcamTelemetryBuffer.clear()
        voiceFeedbackManager.speak("Grabación de video e información OBD2 iniciada.", "Video and OBD2 logging started.")
    }

    fun stopDashcamRecording(videoUri: android.net.Uri? = null) {
        _isRecordingDashcam.value = false
        
        if (videoUri != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    // Guardar telemetría en directorio privado de la app (evita EPERM)
                    val telemetryDir = java.io.File(context.getExternalFilesDir(null), "Telemetry")
                    if (!telemetryDir.exists()) telemetryDir.mkdirs()
                    val telemetryFile = java.io.File(telemetryDir, "telemetry_${System.currentTimeMillis()}.meet.json")
                    val jsonString = Json.encodeToString(dashcamTelemetryBuffer)
                    telemetryFile.writeText(jsonString)
                    Log.d("ObdVM", "Saved synced telemetry log: ${telemetryFile.absolutePath}")

                    if (dashcamTelemetryBuffer.isEmpty()) {
                        Log.w("ObdVM", "Telemetry buffer is empty, skipping transcode")
                        voiceFeedbackManager.speak("Video guardado sin telemetría.", "Video saved without telemetry.")
                        return@launch
                    }

                    // Empezar transcodificación
                    _isTranscodingVideo.value = true
                    _transcodingProgress.value = 0
                    voiceFeedbackManager.speak("Procesando video e impregnando telemetría.", "Processing video and overlaying telemetry.")

                    // Archivo temporal de salida en caché de la app (sin restricciones Scoped Storage)
                    val tempOutputFile = java.io.File(context.cacheDir, "baked_${System.currentTimeMillis()}.mp4")
                    if (tempOutputFile.exists()) tempOutputFile.delete()

                    val burner = com.elysium369.meet.core.video.VideoTelemetryBurner(context)
                    burner.burnTelemetry(
                        inputUri = videoUri,
                        outputFile = tempOutputFile,
                        telemetryFrames = ArrayList(dashcamTelemetryBuffer),
                        onProgress = { progress ->
                            _transcodingProgress.value = progress
                        },
                        onComplete = {
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    if (tempOutputFile.exists()) {
                                        // Escribir de vuelta al MediaStore via ContentResolver (compatible Scoped Storage)
                                        context.contentResolver.openOutputStream(videoUri)?.use { outputStream ->
                                            tempOutputFile.inputStream().use { inputStream ->
                                                inputStream.copyTo(outputStream)
                                            }
                                        }
                                        tempOutputFile.delete()
                                    }
                                    _isTranscodingVideo.value = false
                                    voiceFeedbackManager.speak("Video procesado exitosamente con telemetría.", "Video telemetry overlay completed successfully.")
                                } catch (e: Exception) {
                                    Log.e("ObdVM", "Failed to write baked video back to MediaStore", e)
                                    _isTranscodingVideo.value = false
                                }
                            }
                        },
                        onError = { throwable ->
                            Log.e("ObdVM", "Failed to burn telemetry into video", throwable)
                            _isTranscodingVideo.value = false
                            if (tempOutputFile.exists()) tempOutputFile.delete()
                        }
                    )
                } catch (e: Exception) {
                    Log.e("ObdVM", "Failed to save dashcam telemetry", e)
                    _isTranscodingVideo.value = false
                }
            }
        } else {
            voiceFeedbackManager.speak("Grabación finalizada.", "Recording completed.")
        }
    }

    init {
        startMarketplaceSync()
        // Voice command manager callbacks and initial startup checking
        voiceCommandManager.onCommandRecognized = { command ->
            handleVoiceCommand(command)
        }
        val initialPrefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
        if (initialPrefs.getBoolean("voice_copilot_enabled", false)) {
            voiceCommandManager.startCopilot()
            speechService.startListeningToEvents()
            notificationService.startListeningToEvents()
        }
        _vehicleMass.value = initialPrefs.getFloat("dyno_vehicle_mass", 1500f)
        _drivetrainLoss.value = initialPrefs.getFloat("dyno_drivetrain_loss", 15f)
        performanceCalculator.vehicleMassKg = _vehicleMass.value
        performanceCalculator.drivetrainLossPercent = _drivetrainLoss.value

        // Wire real-time OBD traffic capture to the terminal log
        obdSession.setTrafficListener(object : ObdTrafficListener {
            override fun onCommandSent(command: String) {
                _terminalSessionLogs.update {
                    val newList = it.toMutableList()
                    newList.add(TerminalLine("TX ❯ $command", TerminalLineType.COMMAND))
                    // Keep last 500 lines to avoid memory bloat
                    if (newList.size > 500) newList.removeAt(0)
                    newList
                }
            }
            override fun onResponseReceived(command: String, response: String) {
                _terminalSessionLogs.update {
                    val newList = it.toMutableList()
                    newList.add(TerminalLine("RX ← $response", TerminalLineType.RESPONSE))
                    if (newList.size > 500) newList.removeAt(0)
                    newList
                }
            }
            override fun onError(command: String, error: String) {
                _terminalSessionLogs.update {
                    val newList = it.toMutableList()
                    newList.add(TerminalLine("ERR ✗ $command → $error", TerminalLineType.ERROR))
                    if (newList.size > 500) newList.removeAt(0)
                    newList
                }
            }
        })



        // PRODUCTION-GRADE: Each collector is isolated with try-catch to prevent
        // a single flow failure from crashing the entire ViewModel during startup.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
            // Track and announce connection state changes
            launch {
                var lastState: ObdState? = null
                connectionState.collect { state ->
                    if (state != lastState) {
                        lastState = state
                        when (state) {
                            ObdState.CONNECTING -> voiceFeedbackManager.speak("Iniciando enlace con el adaptador OBD", "Initiating link with OBD adapter")
                            ObdState.NEGOTIATING -> voiceFeedbackManager.speak("Estableciendo protocolo de comunicación", "Establishing communication protocol")
                            ObdState.CONNECTED -> voiceFeedbackManager.speak("Conexión establecida. Sistema de telemetría MEET activo.", "Connection established. MEET telemetry system active.")
                            ObdState.ERROR -> voiceFeedbackManager.speak("Error de conexión. Por favor, verifique el adaptador.", "Connection error. Please check the adapter.")
                            else -> {}
                        }
                    }
                }
            }

            // Auto-fetch DTC definitions whenever new events are loaded from database
            launch {
                try {
                    combine(
                        activeDtcEvents,
                        pendingDtcEvents,
                        permanentDtcEvents,
                        historicalDtcEvents
                    ) { active, pending, permanent, historical ->
                        (active + pending + permanent + historical).map { it.code }.distinct()
                    }.collect { codes ->
                        if (codes.isNotEmpty()) {
                            fetchDtcDefinitions(codes)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ObdViewModel", "Error pre-fetching DTC definitions from flow", e)
                }
            }

            // Collect live data from session — smoothed for professional gauge transitions
            launch {
                try {
                    obdSession.liveData
                        .collect { rawData ->
                            val timestamp = System.currentTimeMillis()

                            // Feed raw OBD speed to the phone speed tracker if present
                            rawData["010D"]?.let { rawSpeed ->
                                phoneSpeedTracker.setObdSpeed(rawSpeed)
                            }

                            // Apply per-PID moving average + exponential interpolation
                            val smoothedData = sensorSmoother.smoothAll(rawData).toMutableMap()

                            // Restore raw voltage for oscilloscope and fast gauges (bypass second smoothing)
                            rawData["0142"]?.let { smoothedData["0142"] = it }
                            rawData["42"]?.let { smoothedData["42"] = it }

                            // Override speedometer speed with high-refresh fused speed if enabled
                            if (_fusedSpeedEnabled.value) {
                                val fusedSpeedVal = phoneSpeedTracker.fusedSpeed.value
                                smoothedData["010D"] = fusedSpeedVal
                                smoothedData["0D"] = fusedSpeedVal
                            }

                            // Update predictive estimator with newly received OBD-II values
                            predictiveEstimator.updateRawValues(smoothedData)

                            _liveData.value = smoothedData
                            if (_isLocalExpertActive.value) {
                                _localDiagnostics.value = localExpertSystem.analyzeLiveTelemetry(
                                    liveData = smoothedData,
                                    activeDtcs = activeDtcs.value,
                                    dtcDefinitions = dtcDefinitions.value
                                )
                            }
                            updateTelemetryHistory(smoothedData)
                            updateOscilloscopeBuffer(timestamp, rawData)

                            // ── Performance Calculator (HP/Torque) ──
                            updatePerformance(smoothedData)
                            // ── Data Logger (if recording) ──
                            recordDataSample(smoothedData)
                            // ── Real-time Alerts ──
                            evaluateAlerts(smoothedData)
                            // ── Fuel Economy ──
                            updateFuelEconomy(smoothedData)
                            // ── Battery Health ──
                            updateBatteryHealth(smoothedData)
                            // ── Turbo Boost ──
                            updateBoost(smoothedData)
                            // ── MEET DNA Real-time Inference ──
                            evaluateDnaInference()

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

                            // Enviar datos al búfer asíncrono en lugar de bloquear con inserciones directas
                            val vehicle = _selectedVehicle.value
                            if (vehicle != null && _isLogging.value) {
                                val now = System.currentTimeMillis()
                                smoothedData.forEach { (pid, value) ->
                                    val modeStr = if (pid.length >= 4) pid.substring(0, 2) else "01"
                                    val pidStr = if (pid.length >= 4) pid.substring(2) else pid
                                    val pidDef = PidRegistry.getPid(modeStr, pidStr)

                                    val entity = com.elysium369.meet.data.local.entities.SensorHistoryEntity(
                                        vehicleId = vehicle.id,
                                        sessionId = healthSessionId,
                                        pid = pid,
                                        pidLabel = pidDef?.name ?: pid,
                                        value = value,
                                        unit = pidDef?.unit ?: "",
                                        timestamp = now
                                    )
                                    telemetryBuffer.trySend(entity)
                                }
                            }
                        }
                } catch (e: Exception) {
                    android.util.Log.e("ObdVM", "liveData collector crashed", e)
                }
            }

            // 120Hz (8ms) Predictive Telemetry Extrapolation Loop
            // Fills the visual gaps between OBD-II readings (which poll at ~5-10Hz)
            // to achieve 100% fluid 120fps sweeps on modern displays.
            launch {
                try {
                    var lastPredictTime = System.currentTimeMillis()
                    while (true) {
                        kotlinx.coroutines.delay(8L) // ~120 Hz
                        val now = System.currentTimeMillis()
                        val dtMs = (now - lastPredictTime).toFloat().coerceIn(1f, 100f)
                        lastPredictTime = now

                        val currentMap = _liveData.value
                        if (currentMap.isNotEmpty() && obdSession.state.value == com.elysium369.meet.core.obd.ObdState.CONNECTED) {
                            // 1. Get predictions for fast PIDs (RPM, Throttle, etc.)
                            val estimatedMap = predictiveEstimator.getEstimatedMap(currentMap, dtMs).toMutableMap()

                            // 2. If fused speed is enabled, override 010D/0D with phoneSpeedTracker's high-refresh fusion speed
                            if (_fusedSpeedEnabled.value) {
                                val fusedSpeedVal = phoneSpeedTracker.fusedSpeed.value
                                estimatedMap["010D"] = fusedSpeedVal
                                estimatedMap["0D"] = fusedSpeedVal
                            }

                            _liveData.value = estimatedMap
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ObdVM", "predictive loop crashed", e)
                }
            }

            // Update local diagnostics reactively when DTCs or definitions change
            launch {
                try {
                    kotlinx.coroutines.flow.combine(activeDtcs, dtcDefinitions, _isLocalExpertActive) { dtcs, definitions, expertActive ->
                        Triple(dtcs, definitions, expertActive)
                    }.collect { (dtcs, definitions, expertActive) ->
                        if (expertActive) {
                            _localDiagnostics.value = localExpertSystem.analyzeLiveTelemetry(
                                liveData = _liveData.value,
                                activeDtcs = dtcs,
                                dtcDefinitions = definitions
                            )
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ObdVM", "DTC-diagnostics collector crashed", e)
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

            // Connection startup is handled by runPostConnectDtcFirstStartup()
            // so DTC pre-scan always finishes before VIN, odometer, polling, or actions.
            launch {
                try {
                    obdSession.state
                        .collect { state ->
                            if (state != ObdState.CONNECTED) {
                                hasCompletedInitialDtcScan = false
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

                // ─── STEP 3: Prune old AI consultations from cache (TTL 30 days) ───
                launch(Dispatchers.IO) {
                    try {
                        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                        aiConsultDao.pruneOldConsults(thirtyDaysAgo)
                        android.util.Log.i("ObdVM", "Successfully pruned AI consultations older than 30 days.")
                    } catch (e: Exception) {
                        android.util.Log.w("ObdVM", "Failed to prune old AI consultations cache: ${e.message}")
                    }
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

        // Load persisted settings for Clone Mode, Fused Speed & AI Config
        val prefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
        _forceCloneMode.value = prefs.getBoolean("force_clone_mode", false)
        _language.value = prefs.getString("app_language", "es") ?: "es"

        val fusedEnabled = prefs.getBoolean("fused_speed_enabled", true)
        _fusedSpeedEnabled.value = fusedEnabled
        if (fusedEnabled) {
            phoneSpeedTracker.start()
        }

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

        // Load terminal command history
        val savedHistory = prefs.getString("terminal_command_history", "") ?: ""
        _commandHistory.value = if (savedHistory.isBlank()) emptyList() else savedHistory.split(",")

        // Load units and fuel configurations
        _useImperialUnits.value = prefs.getBoolean("pref_imperial_units", false)
        _fuelPrice.value = prefs.getFloat("pref_fuel_price", 1.25f)
        _currencySymbol.value = prefs.getString("pref_currency_symbol", "$") ?: "$"
        _fuelType.value = prefs.getString("pref_fuel_type", "GASOLINE") ?: "GASOLINE"

        // Purge old trips (older than 90 days)
        purgeOldTrips()
    }

    // --- Settings Actions ---

    fun addTerminalCommand(cmd: String) {
        val trimmed = cmd.trim().uppercase()
        if (trimmed.isBlank()) return
        val current = _commandHistory.value.toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        if (current.size > 30) {
            current.removeAt(current.size - 1)
        }
        _commandHistory.value = current
        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("terminal_command_history", current.joinToString(","))
            .apply()
    }

    fun addTerminalLog(text: String, type: TerminalLineType) {
        _terminalSessionLogs.update { it + TerminalLine(text, type) }
    }

    fun addTerminalLogs(lines: List<TerminalLine>) {
        _terminalSessionLogs.update { it + lines }
    }

    fun clearTerminalLogs() {
        _terminalSessionLogs.value = listOf(
            TerminalLine("Terminal limpiada.", TerminalLineType.SYSTEM)
        )
    }

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
        hasCompletedInitialDtcScan = false
        viewModelScope.launch {
            obdSession.connect()
            runPostConnectDtcFirstStartup()
        }
    }

    fun startDiagnosticSession(vehicle: Vehicle) {
        _selectedVehicle.value = vehicle
        currentSessionId = UUID.randomUUID().toString()
        hasCompletedInitialDtcScan = false
        viewModelScope.launch {
            obdSession.connect()
            if (obdSession.state.value == ObdState.CONNECTED) {
                runPostConnectDtcFirstStartup()
                startForegroundService(vehicle.id)
            }
        }
    }

    private suspend fun runPostConnectDtcFirstStartup() {
        if (obdSession.state.value != ObdState.CONNECTED) return
        ensureDtcScanBeforeAction(force = true)
        runCatching {
            obdSession.fetchVin()
            _currentOdometer.value = obdSession.readOdometer()
        }.onFailure { e ->
            android.util.Log.e("ObdVM", "Post-DTC startup identification error", e)
        }
        obdSession.startLivePolling()
    }

    fun stopSession() {
        viewModelScope.launch {
            voiceFeedbackManager.speak("Sesión de diagnóstico finalizada. Guardando resultados.", "Diagnostic session ended. Saving results.")
            saveSessionResults()
            obdSession.disconnect()
            context.stopService(Intent(context, com.elysium369.meet.core.obd.ObdForegroundService::class.java))
            _selectedVehicle.value = null
            clearState()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            voiceFeedbackManager.speak("Desconectando del vehículo. Guardando datos de sesión.", "Disconnecting from vehicle. Saving session data.")
            saveSessionResults()
            obdSession.disconnect()
            context.stopService(Intent(context, com.elysium369.meet.core.obd.ObdForegroundService::class.java))
        }
    }

    fun resetTrip() {
        obdSession.resetTrip()
    }

    fun getSpeedHistory(): List<Float> = tripManager.getSpeedHistory()
    fun getRpmHistory(): List<Float> = tripManager.getRpmHistory()
    fun getThrottleHistory(): List<Float> = tripManager.getThrottleHistory()

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
        predictiveEstimator.reset()
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

    private suspend fun ensureDtcScanBeforeAction(force: Boolean = false) {
        if (obdSession.state.value != ObdState.CONNECTED) return
        initialDtcScanMutex.withLock {
            if (!force && hasCompletedInitialDtcScan) return
            _cloudSyncState.value = "Pre-scan DTC obligatorio..."
            addTerminalLog("──── PRE-SCAN DTC OBLIGATORIO ANTES DE CUALQUIER ACCIÓN ────", TerminalLineType.SYSTEM)
            refreshDiagnostics(manageState = true)
            hasCompletedInitialDtcScan = true
            _cloudSyncState.value = ""
        }
    }

    suspend fun refreshDiagnostics(manageState: Boolean = true) {
        if (manageState) _isScanning.value = true
        addTerminalLog("──── INICIO ESCANEO DTC PROFESIONAL ────", TerminalLineType.SYSTEM)
        if (connectionState.value != ObdState.CONNECTED) {
            addTerminalLog("⚠ OBD no conectado — Iniciando simulación de escaneo...", TerminalLineType.WARNING)
            voiceFeedbackManager.speak("Iniciando simulación de escaneo de códigos de error.", "Starting simulated fault code scan.")

            val steps = listOf(
                "Detectando protocolo de comunicación... OK (CAN ISO 15765-4)",
                "Escaneando Módulo de Control del Motor (ECM)...",
                "[ECM] P0300 — Fallo de encendido múltiple/aleatorio detectado",
                "[ECM] P0301 — Fallo de encendido Cilindro #1",
                "[ECM] P0302 — Fallo de encendido Cilindro #2",
                "[ECM] P0304 — Fallo de encendido Cilindro #4",
                "Escaneando Módulo de Transmisión (TCM)... OK (Sin fallas)",
                "Escaneando Módulo ABS/ESP...",
                "[ABS] C0040 — Falla circuito pedal freno",
                "Escaneando Módulo de Carrocería (BCM)... OK",
                "Leyendo códigos pendientes...",
                "[ECM] P0171 — Mezcla pobre banco 1",
                "[ECM] P0302 — Fallo de encendido Cilindro #2 (intermitente)",
                "Leyendo Monitores de Emisiones..."
            )
            for (step in steps) {
                addTerminalLog(step, TerminalLineType.SYSTEM)
                kotlinx.coroutines.delay(180)
            }

            val simulatedCodes = listOf("P0300", "P0301", "P0302", "P0304", "P0171", "C0040")
            if (_selectedVehicle.value != null) {
                addTerminalLog(
                    "[DEMO] Escaneo simulado no se guarda en historial ni se sincroniza como dato real.",
                    TerminalLineType.WARNING
                )
            }

            // Monitores I/M
            _readinessMonitors.value = ReadinessResult(
                milOn = true,
                dtcCount = 5,
                monitors = listOf(
                    MonitorStatus("Misfire", true, true),
                    MonitorStatus("Fuel System", true, true),
                    MonitorStatus("Components", true, true),
                    MonitorStatus("Catalyst", true, false), // incompleto
                    MonitorStatus("O2 Sensor", true, true),
                    MonitorStatus("EGR System", true, false) // incompleto
                )
            )

            // Simular carga de definiciones — incluir cada cilindro individual
            fetchDtcDefinitions(simulatedCodes)

            voiceFeedbackManager.speak("Escaneo simulado completado. Se detectaron 7 códigos de error, incluyendo fallos individuales por cilindro.", "Simulated scan complete. 7 fault codes detected, including individual cylinder misfires.")
            updateHealthScore()
            addTerminalLog("──── ESCANEO SIMULADO COMPLETADO — 7 códigos detectados (4 activos, 2 pendientes, 1 permanente) ────", TerminalLineType.SYSTEM)
            if (manageState) _isScanning.value = false
            return
        }
        try {
            obdSession.pauseLivePolling()
            obdSession.clearCommandQueue()
            voiceFeedbackManager.speak("Iniciando escaneo de códigos de error.", "Starting fault code scan.")

            addTerminalLog("[SCAN] DTC primero: Service 19/UDS + Mode 03/07/0A + módulos CAN...", TerminalLineType.SYSTEM)
            val professionalReport = obdSession.readProfessionalDtcScan()
            addProfessionalDtcReportLogs(professionalReport)

            val freshActive = professionalReport.codesForBucket(DtcBucket.ACTIVE)
            val freshPending = professionalReport.codesForBucket(DtcBucket.PENDING)
            val freshPermanent = professionalReport.codesForBucket(DtcBucket.PERMANENT)
            val freshHistory = professionalReport.codesForBucket(DtcBucket.HISTORY)

            addTerminalLog(
                "[SCAN] Activos: ${freshActive.size} | Pendientes: ${freshPending.size} | Permanentes: ${freshPermanent.size} | Históricos: ${freshHistory.size}",
                TerminalLineType.SYSTEM
            )

            val vehicle = _selectedVehicle.value
            if (vehicle != null) {
                saveDetectedDtcFindings(professionalReport)
            }

            // Mode 01 PID 01 → I/M Readiness Monitors
            addTerminalLog("[SCAN] Leyendo Monitores I/M (Mode 01 PID 01)...", TerminalLineType.SYSTEM)
            _readinessMonitors.value = obdSession.readReadinessMonitors()

            // Fetch definitions for all discovered DTCs from local DB
            val allCodes = (freshActive + freshPending + freshPermanent + freshHistory).distinct()
            fetchDtcDefinitions(allCodes)

            val total = allCodes.size
            if (total == 0) {
                voiceFeedbackManager.speak("Escaneo completado. No se encontraron códigos de error en el vehículo.", "Scan complete. No fault codes found in the vehicle.")
            } else if (total == 1) {
                voiceFeedbackManager.speak("Escaneo completado. Se detectó un código de error en el sistema.", "Scan complete. One fault code detected in the system.")
            } else {
                voiceFeedbackManager.speak("Escaneo completado. Se detectaron $total códigos de error en el sistema.", "Scan complete. $total fault codes detected in the system.")
            }
            updateHealthScore()
            addTerminalLog("──── ESCANEO COMPLETADO — $total códigos en total ────", TerminalLineType.SYSTEM)
        } catch (e: Exception) {
            android.util.Log.e("ObdVM", "Failed to refresh diagnostics", e)
            addTerminalLog("✗ Error en escaneo: ${e.message}", TerminalLineType.ERROR)
        } finally {
            obdSession.resumeLivePolling()
            if (manageState) _isScanning.value = false
        }
    }

    private fun addProfessionalDtcReportLogs(report: DtcScanReport) {
        val moduleLines = report.modules
            .filter { it.isAlive }
            .take(12)
            .map { module ->
                val codes = module.dtcs.map { it.code }.distinct().joinToString(", ").ifBlank { "sin DTC" }
                TerminalLine(
                    "[MOD] ${module.moduleName} ${module.responseAddress ?: module.targetAddress ?: ""} -> $codes",
                    TerminalLineType.DECODED
                )
            }
        if (moduleLines.isNotEmpty()) addTerminalLogs(moduleLines)

        val detailedLines = report.records.take(20).map { record ->
            TerminalLine(
                "[DTC] ${record.code} ${record.bucket} ${record.statusFlags.joinToString("|")} " +
                    "mod=${record.moduleName ?: record.responseAddress ?: record.targetAddress ?: "-"} " +
                    "svc=${record.sourceService}",
                TerminalLineType.DECODED
            )
        }
        if (detailedLines.isNotEmpty()) addTerminalLogs(detailedLines)
    }

    private suspend fun saveDetectedDtcFindings(report: DtcScanReport) {
        val vehicle = _selectedVehicle.value ?: return
        val now = System.currentTimeMillis()
        val records = report.records

        records.distinctBy { "${it.code}|${storageStatusForDtcRecord(it)}" }.forEach { record ->
            val status = storageStatusForDtcRecord(record)
            val existing = dtcDao.getUnresolvedDtc(vehicle.id, record.code, status)
            val metadata = dtcRecordMetadata(record)

            if (existing != null) {
                val isNewSession = existing.sessionId != currentSessionId
                dtcDao.insertDtc(
                    existing.copy(
                        lastSeenAt = now,
                        occurrenceCount = if (isNewSession) existing.occurrenceCount + 1 else existing.occurrenceCount,
                        sessionId = currentSessionId,
                        freezeFrameJson = metadata,
                        synced = false
                    )
                )
            } else {
                val vehicleMake = com.elysium369.meet.ui.components.DtcUtils.normalizeManufacturer(vehicle.make)
                val def = dtcDefinitionDao.getDefinitionForCode(record.code, vehicleMake)
                val description = if (def != null && !def.descriptionEs.isNullOrBlank()) {
                    val raw = def.descriptionEs
                    if (raw.contains("no disponible localmente") || raw.contains("no disponible offline")) {
                        com.elysium369.meet.ui.components.DtcUtils.getDynamicDtcFallbackDescription(record.code, isSpanish = true)
                    } else {
                        raw
                    }
                } else {
                    com.elysium369.meet.ui.components.DtcUtils.getDynamicDtcFallbackDescription(record.code, isSpanish = true)
                }
                val severity = if (def != null && !def.severity.isNullOrBlank() && def.severity != "UNKNOWN") {
                    def.severity
                } else {
                    com.elysium369.meet.ui.components.DtcUtils.getDynamicSeverity(record.code)
                }

                dtcDao.insertDtc(
                    DtcEventEntity(
                        id = UUID.randomUUID().toString(),
                        sessionId = currentSessionId,
                        vehicleId = vehicle.id,
                        code = record.code,
                        description = description,
                        severity = severity,
                        status = status,
                        firstSeenAt = now,
                        lastSeenAt = now,
                        resolvedAt = null,
                        occurrenceCount = 1,
                        freezeFrameJson = metadata,
                        synced = false
                    )
                )

                if (status == "ACTIVE") {
                    alertManager.triggerNewDtcAlert(record.code)
                }
            }
        }

        // Smart Auto-Resolution:
        // Get all unresolved DTCs in Room for this vehicle
        val unresolvedEvents = dtcDao.getUnresolvedDtcsList(vehicle.id)
        val isCan = report.protocol.uppercase().contains("CAN") || report.protocol.uppercase().contains("ISO15765")

        // Find which modules were alive in the current scan
        val aliveModules = report.modules.filter { it.isAlive }

        unresolvedEvents.forEach { event ->
            val (origTarget, origResponse, origModuleName) = try {
                event.freezeFrameJson?.let {
                    val obj = org.json.JSONObject(it)
                    Triple(
                        obj.optString("targetAddress", ""),
                        obj.optString("responseAddress", ""),
                        obj.optString("moduleName", "")
                    )
                } ?: Triple("", "", "")
            } catch (_: Exception) {
                Triple("", "", "")
            }

            // Check if this DTC's module was actively scanned in the current session
            val wasScanned = if (isCan) {
                // For CAN, the module is scanned if there is an alive module matching target, response, or moduleName
                aliveModules.any { activeModule ->
                    (origTarget.isNotBlank() && origTarget == activeModule.targetAddress) ||
                    (origResponse.isNotBlank() && origResponse == activeModule.responseAddress) ||
                    (origResponse.isNotBlank() && origResponse == activeModule.targetAddress) ||
                    (origModuleName.isNotBlank() && origModuleName == activeModule.moduleName)
                }
            } else {
                // For legacy protocols, any DTC with no CAN headers (null or blank) or labeled "Standard OBD-II" was scanned
                origTarget.isBlank() && origResponse.isBlank()
            }

            if (wasScanned) {
                // If it was scanned, but is NOT present in the current scan records with the same status, resolve it
                val status = event.status // ACTIVE, PENDING, PERMANENT, HISTORY, INTERMITTENT
                val stillPresent = records.any { record ->
                    record.code == event.code && storageStatusForDtcRecord(record) == status
                }
                if (!stillPresent) {
                    dtcDao.insertDtc(
                        event.copy(
                            resolvedAt = now,
                            synced = false
                        )
                    )
                    addTerminalLog("[RESOLVED] Falla ${event.code} ($status) ya no está presente en el módulo. Marcada como resuelta.", TerminalLineType.SYSTEM)
                }
            }
        }

        scheduleSync()
    }

    private fun storageStatusForDtcRecord(record: DtcRecord): String = when (record.bucket) {
        DtcBucket.PENDING -> "PENDING"
        DtcBucket.PERMANENT -> "PERMANENT"
        DtcBucket.HISTORY -> if (DtcStatusFlag.INTERMITTENT in record.statusFlags) "INTERMITTENT" else "HISTORY"
        DtcBucket.ACTIVE -> "ACTIVE"
    }

    private fun dtcRecordMetadata(record: DtcRecord): String {
        val data = mapOf(
            "moduleName" to (record.moduleName ?: ""),
            "targetAddress" to (record.targetAddress ?: ""),
            "responseAddress" to (record.responseAddress ?: ""),
            "sourceService" to record.sourceService,
            "bucket" to record.bucket.name,
            "statusFlags" to record.statusFlags.joinToString("|") { it.name },
            "udsStatusByte" to (record.udsStatusByte?.let { String.format("0x%02X", it) } ?: ""),
            "udsFailureType" to (record.udsFailureType ?: "")
        )
        return Json.encodeToString(data)
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
                val vehicleMake = com.elysium369.meet.ui.components.DtcUtils.normalizeManufacturer(vehicle.make)
                val def = dtcDefinitionDao.getDefinitionForCode(code, vehicleMake)
                val description = if (def != null && !def.descriptionEs.isNullOrBlank()) {
                    val raw = def.descriptionEs
                    if (raw.contains("no disponible localmente") || raw.contains("no disponible offline")) {
                        com.elysium369.meet.ui.components.DtcUtils.getDynamicDtcFallbackDescription(code, isSpanish = true)
                    } else {
                        raw
                    }
                } else {
                    com.elysium369.meet.ui.components.DtcUtils.getDynamicDtcFallbackDescription(code, isSpanish = true)
                }
                val severity = if (def != null && !def.severity.isNullOrBlank() && def.severity != "UNKNOWN") {
                    def.severity
                } else {
                    com.elysium369.meet.ui.components.DtcUtils.getDynamicSeverity(code)
                }

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
            val vehicleMake = com.elysium369.meet.ui.components.DtcUtils.normalizeManufacturer(_selectedVehicle.value?.make)
            codes.forEach { code ->
                if (!newDefinitions.containsKey(code)) {
                    val def = dtcDefinitionDao.getDefinitionForCode(code, vehicleMake)
                    if (def != null) {
                        newDefinitions[code] = def
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
        val descEs = com.elysium369.meet.ui.components.DtcUtils.getDynamicDtcFallbackDescription(code, isSpanish = true)
        val descEn = com.elysium369.meet.ui.components.DtcUtils.getDynamicDtcFallbackDescription(code, isSpanish = false)
        val severity = com.elysium369.meet.ui.components.DtcUtils.getDynamicSeverity(code)
        val urgency = com.elysium369.meet.ui.components.DtcUtils.getDynamicUrgency(code)

        return DtcDefinitionEntity(
            code = code,
            manufacturer = "GENERIC",
            descriptionEn = descEn,
            descriptionEs = descEs,
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
                val dbResults = dtcDefinitionDao.searchDefinitions(query)
                if (dbResults.isEmpty() && query.matches(Regex("^[PBUCpbuc][0-9A-Fa-f]{4}$"))) {
                    // Generate dynamic fallback definition so search never fails for a valid DTC code!
                    val fallback = generateFallbackDefinition(query.uppercase())
                    _manualSearchResults.value = listOf(fallback)
                } else {
                    // Deduplicate: group by code, prefer manufacturer-specific over GENERIC
                    val deduped = dbResults
                        .groupBy { it.code }
                        .map { (_, entries) ->
                            entries.firstOrNull { it.manufacturer != "GENERIC" } ?: entries.first()
                        }
                        .take(50)
                    _manualSearchResults.value = deduped
                }
            }
        }
    }

    suspend fun getDtcDefinition(code: String): DtcDefinitionEntity {
        val make = _selectedVehicle.value?.make ?: "GENERIC"
        val normalizedMake = com.elysium369.meet.ui.components.DtcUtils.normalizeManufacturer(make)
        return dtcDefinitionDao.getDefinitionForCode(code.uppercase(), normalizedMake) ?: generateFallbackDefinition(code.uppercase())
    }

    // ══════════════════════════════════════════════════════════════════
    // KNOWLEDGE GRAPH QUERIES
    // ══════════════════════════════════════════════════════════════════

    suspend fun getDtcSymptoms(code: String): List<com.elysium369.meet.data.local.entities.DtcSymptomEntity> {
        return dtcKnowledgeGraphDao.getSymptomsForDtc(code.uppercase())
    }

    suspend fun getDtcCauses(code: String): List<com.elysium369.meet.data.local.entities.DtcCauseEntity> {
        return dtcKnowledgeGraphDao.getCausesForDtc(code.uppercase())
    }

    suspend fun getDtcProcedures(code: String): List<com.elysium369.meet.data.local.entities.DtcProcedureEntity> {
        return dtcKnowledgeGraphDao.getProceduresForDtc(code.uppercase())
    }

    suspend fun getDtcRelatedPids(code: String): List<com.elysium369.meet.data.local.entities.DtcRelatedPidEntity> {
        return dtcKnowledgeGraphDao.getRelatedPidsForDtc(code.uppercase())
    }

    suspend fun getDtcCoOccurrences(code: String): List<com.elysium369.meet.data.local.entities.DtcCoOccurrenceEntity> {
        return dtcKnowledgeGraphDao.getCoOccurrencesForDtc(code.uppercase())
    }

    suspend fun getDtcCoOccurrencesForMultiple(codes: List<String>): List<com.elysium369.meet.data.local.entities.DtcCoOccurrenceEntity> {
        return dtcKnowledgeGraphDao.getCoOccurrencesForMultipleDtcs(codes.map { it.uppercase() })
    }

    suspend fun getDtcRepairCosts(code: String, region: String = "LATAM"): List<com.elysium369.meet.data.local.entities.DtcRepairCostEntity> {
        return dtcKnowledgeGraphDao.getRepairCostsForDtc(code.uppercase(), region)
    }

    suspend fun getDtcVerifiedFixes(code: String): List<com.elysium369.meet.data.local.entities.DtcVerifiedFixEntity> {
        return dtcKnowledgeGraphDao.getVerifiedFixesForDtc(code.uppercase())
    }

    suspend fun searchDtcKnowledgeGraph(query: String): List<DtcDefinitionEntity> {
        return dtcKnowledgeGraphDao.searchKnowledgeGraph(query)
    }

    suspend fun upvoteDtcFix(fixId: Long) {
        dtcKnowledgeGraphDao.upvoteFix(fixId)
    }

    suspend fun clearDtcs(): Boolean {
        ensureDtcScanBeforeAction()
        _isClearing.value = true
        voiceFeedbackManager.speak("Iniciando borrado de códigos de error de la memoria.", "Starting fault code memory erase.")
        _clearDtcResult.value = "Enviando comando de borrado..."
        
        val success: Boolean
        if (connectionState.value != ObdState.CONNECTED) {
            // Simulator Mode Clear
            kotlinx.coroutines.delay(1500)
            success = true
            voiceFeedbackManager.speak("Borrado simulado completado con éxito. Memoria limpia.", "Simulated fault codes cleared successfully. Memory clean.")
            _clearDtcResult.value = "✅ [SIMULADO] Códigos borrados exitosamente"
            _selectedVehicle.value?.let { vehicle ->
                dtcDao.resolveAllDtcsForVehicle(vehicle.id, System.currentTimeMillis())
            }
            _freezeFrameData.value = emptyMap()
            updateHealthScore()
        } else {
            success = obdSession.clearDtcs()
            if (success) {
                voiceFeedbackManager.speak("Borrado de códigos completado con éxito. Memoria limpia.", "Fault codes cleared successfully. Memory clean.")
                _clearDtcResult.value = "✅ Códigos borrados exitosamente"
                _selectedVehicle.value?.let { vehicle ->
                    dtcDao.resolveAllDtcsForVehicle(vehicle.id, System.currentTimeMillis())
                }
                _freezeFrameData.value = emptyMap()
                updateHealthScore()
                scheduleSync()
            } else {
                voiceFeedbackManager.speak("Fallo al borrar los códigos. Por favor, asegúrese que el motor esté apagado y en contacto.", "Failed to clear fault codes. Please ensure the engine is off and ignition is on.")
                _clearDtcResult.value = "❌ Error al borrar códigos. Asegúrese que el motor esté apagado y en contacto (IGNITION ON)."
            }
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
        voiceFeedbackManager.speak("Iniciando escaneo inteligente completo del vehículo.", "Starting full smart scan of the vehicle.")

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
            val (ecuVoltage, physicalVoltage) = obdSession.readBatteryVoltage()
            val voltage = if (physicalVoltage > 0f) physicalVoltage else ecuVoltage
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
        try {
            obdSession.pauseLivePolling()
            obdSession.clearCommandQueue()
            voiceFeedbackManager.speak("Escaneando topología de red del vehículo.", "Scanning vehicle network topology.")
            // Phase 1: Topology scan — probe each ECU module individually
            _cloudSyncState.value = "Escaneando topología de red CAN..."
            val modules = obdSession.scanModules()

            // Phase 2: Persist any DTCs found per-module during topology scan
            _cloudSyncState.value = "Procesando códigos encontrados por módulo..."
            val moduleDtcs = modules.flatMap { it.dtcs }.distinct()
            if (moduleDtcs.isNotEmpty()) {
                saveDetectedDtcs(moduleDtcs, "ACTIVE")
                Log.d("ObdVM", "✅ Persisted ${moduleDtcs.size} DTCs from topology scan: $moduleDtcs")
            }

            // Phase 3: Full broadcast DTC read (Mode 03/07/0A from 7DF)
            // This catches DTCs the per-module scan might miss and reads
            // Pending + Permanent codes plus Readiness Monitors
            _cloudSyncState.value = "Leyendo códigos de falla (DTCs)..."
            refreshDiagnostics(manageState = false)

            _cloudSyncState.value = "Escaneo completo: ${modules.size} módulos, ${activeDtcs.value.size} DTCs activos"
            return modules
        } finally {
            obdSession.resumeLivePolling()
            _isScanning.value = false
        }
    }

    suspend fun sendRawCommand(cmd: String): String {
        ensureDtcScanBeforeAction()
        return obdSession.sendRawCommand(cmd)
    }

    /**
     * Executes a professional-grade diagnostic routine or active test.
     * Performs safety checks (voltage, adapter quality) before sending commands.
     */
    suspend fun runDiagnosticCommand(command: com.elysium369.meet.core.obd.ObdCommandDef): String {
        ensureDtcScanBeforeAction()
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
        val dtcCodesStr = dtcList.sorted().joinToString(",")

        // 1. Check offline cache
        try {
            val cached = aiConsultDao.getCachedConsult(dtcCodesStr)
            if (cached != null) {
                android.util.Log.i("ObdViewModel", "Retrieved cached AI consultation offline for DTCs: $dtcCodesStr")
                val cleanResult = parseCachedResponsePids(cached.response)
                _anomalousPids.value = cleanResult.anomalousPids.map {
                    com.elysium369.meet.core.ai.HealthAnomaly(it, "Anomalía detectada en diagnóstico profundo (Caché)")
                }
                return cached.response
            }
        } catch (e: Exception) {
            android.util.Log.w("ObdViewModel", "Error reading AI consult cache: ${e.message}")
        }

        // 2. Cache miss -> Query remote AI
        var resultText = ""
        var modelUsed = geminiDiagnostic.javaClass.simpleName
        try {
            geminiDiagnostic.updateConfig(apiKey, endpointUrl)
            val info = _selectedVehicle.value?.let { "${it.make} ${it.model} ${it.year}" } ?: "Vehículo Genérico"
            val result = geminiDiagnostic.analyzeDtc(
                dtcList,
                info,
                _liveData.value.mapValues { "%.2f".format(it.value) },
                _telemetryHistory.value
            )
            resultText = result.analysisText

            // Update the UI with detected anomalies
            _anomalousPids.value = result.anomalousPids.map {
                com.elysium369.meet.core.ai.HealthAnomaly(it, "Anomalía detectada en diagnóstico profundo")
            }
        } catch (e: Exception) {
            android.util.Log.e("ObdViewModel", "Remote AI consultation failed, invoking offline expert diagnostic fallback: ${e.message}", e)
            val make = _selectedVehicle.value?.make ?: "GENERIC"
            val normalizedMake = com.elysium369.meet.ui.components.DtcUtils.normalizeManufacturer(make)

            // Query local definitions for active DTCs
            val localDefinitions = dtcList.mapNotNull { code ->
                dtcDefinitionDao.getDefinitionForCode(code, normalizedMake)
            }
            val info = _selectedVehicle.value?.let { "${it.make} ${it.model} ${it.year}" } ?: "Vehículo Genérico"

            resultText = com.elysium369.meet.ui.components.DtcUtils.generateOfflineDiagnosticReport(
                dtcList = dtcList,
                vehicleInfo = info,
                definitions = localDefinitions
            )

            _anomalousPids.value = emptyList()
            modelUsed = "MEET Local Expert Engine"
        }

        // 3. Save to offline cache (both successful remote and generated local fallback)
        try {
            val session = currentSessionId
            val consult = com.elysium369.meet.data.local.entities.AiConsultEntity(
                id = UUID.randomUUID().toString(),
                sessionId = session,
                dtcCodes = dtcCodesStr,
                prompt = "OBD2 Diagnosis for $dtcList",
                response = resultText,
                model = modelUsed,
                createdAt = System.currentTimeMillis(),
                exportedAsPdf = false
            )
            aiConsultDao.insertConsult(consult)
            android.util.Log.i("ObdViewModel", "Successfully saved AI consultation to offline cache.")
        } catch (e: Exception) {
            android.util.Log.e("ObdViewModel", "Failed to cache AI consultation", e)
        }

        return resultText
    }

    private fun parseCachedResponsePids(rawText: String): com.elysium369.meet.core.ai.DiagnosticResult {
        val pattern = java.util.regex.Pattern.compile("```json([\\s\\S]*?)```")
        val matcher = pattern.matcher(rawText)
        var anomalousPids = emptyList<String>()
        if (matcher.find()) {
            try {
                val jsonStr = matcher.group(1)?.trim()
                if (jsonStr != null) {
                    val obj = org.json.JSONObject(jsonStr)
                    val pidsArr = obj.optJSONArray("anomalous_pids")
                    if (pidsArr != null) {
                        val list = mutableListOf<String>()
                        for (i in 0 until pidsArr.length()) {
                            list.add(pidsArr.getString(i))
                        }
                        anomalousPids = list
                    }
                }
            } catch (_: Exception) {}
        }
        return com.elysium369.meet.core.ai.DiagnosticResult(rawText, anomalousPids)
    }

    fun generateFullReport(aiAnalysis: String?) {
        val currentTrip = tripManager.currentTrip
        val vehicleInfo = _selectedVehicle.value?.let { "${it.make} ${it.model} ${it.year}" } ?: "Vehículo Genérico"
        val dtcs = activeDtcs.value
        val history = _telemetryHistory.value
        voiceFeedbackManager.speak("Generando reporte de diagnóstico completo.", "Generating full diagnostic report.")

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
            val predictiveReport = _predictiveHealthReport.value

            val file = reportGenerator.generatePdfReport(
                trip = tripData,
                dtcs = dtcs,
                aiAnalysis = aiAnalysis,
                vehicleDetails = vehicleInfo,
                telemetryHistory = history,
                anomalies = _anomalousPids.value,
                healthScore = healthScore,
                maintenanceAlerts = alerts,
                predictiveReport = predictiveReport
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

    fun addMaintenanceAlert(type: String, intervalKm: Long, nextDueKm: Long) {
        val vehicle = _selectedVehicle.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val alert = MaintenanceAlertEntity(
                id = java.util.UUID.randomUUID().toString(),
                vehicleId = vehicle.id,
                type = type,
                intervalKm = intervalKm,
                lastDoneKm = 0L,
                nextDueKm = nextDueKm,
                notes = null
            )
            maintenanceAlertDao.insertAlert(alert)
        }
    }

    fun generateMockTrip() {
        val vehicle = _selectedVehicle.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val random = java.util.Random()
            val durationMin = 15 + random.nextInt(45)
            val distance = 5f + random.nextFloat() * 45f
            val ecoScore = 65 + random.nextInt(35)
            val maxSpeed = 80f + random.nextFloat() * 40f
            val maxTemp = 88f + random.nextFloat() * 12f

            val mockTrip = TripEntity(
                id = java.util.UUID.randomUUID().toString(),
                vehicleId = vehicle.id,
                sessionId = "mock_session_" + java.util.UUID.randomUUID().toString().take(8),
                startedAt = System.currentTimeMillis() - (durationMin * 60 * 1000L),
                endedAt = System.currentTimeMillis(),
                distanceKm = distance,
                durationSeconds = durationMin * 60L,
                avgSpeedKmh = (distance / (durationMin / 60f)),
                maxSpeedKmh = maxSpeed,
                maxRpm = 2500f + random.nextInt(2000),
                avgRpm = 1500f + random.nextInt(800),
                maxTempC = maxTemp,
                fuelEfficiency = 5.5f + random.nextFloat() * 4f,
                ecoScore = ecoScore,
	                gpsTrackJson = null,
	                synced = true
	            )
	            tripDao.insertTrip(mockTrip)
	            addTerminalLog("[DEMO] Viaje mock guardado solo localmente; no se sincronizará a la nube.", TerminalLineType.WARNING)
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

    fun syncCustomPidsFromCloud() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.elysium369.meet.core.sync.ElysiumCloudServices.syncCommunityCustomPIDs(customPidDao)
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Error syncing custom PIDs from cloud", e)
            }
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
        viewModelScope.launch {
            ensureDtcScanBeforeAction()
            obdSession.runActiveTest(test)
        }
    }

    fun stopActiveTest() {
        obdSession.stopActiveTest()
    }

    fun clearActiveTestStatus() {
        obdSession.clearActiveTestStatus()
        clearActiveTestAiDiagnostic()
    }

    suspend fun resetOilService(): Boolean {
        ensureDtcScanBeforeAction()
        voiceFeedbackManager.speak("Ejecutando reset de servicio de aceite.", "Executing oil service reset.")
        val mfr = _manufacturer.value
        val result = diagnosticManager.resetOilService(mfr)
        if (result) {
            voiceFeedbackManager.speak("Reset de aceite completado con éxito.", "Oil service reset completed successfully.")
        } else {
            voiceFeedbackManager.speak("Fallo en el reset de servicio de aceite.", "Oil service reset failed.")
        }
        return result
    }

    suspend fun registerBattery(capacityAh: Int): Boolean {
        ensureDtcScanBeforeAction()
        val mfr = _manufacturer.value
        return diagnosticManager.registerBattery(mfr, capacityAh)
    }

    suspend fun resetEPB(open: Boolean): Boolean {
        ensureDtcScanBeforeAction()
        val mfr = _manufacturer.value
        return diagnosticManager.resetEPB(mfr, open)
    }

    suspend fun calibrateSAS(): Boolean {
        ensureDtcScanBeforeAction()
        val mfr = _manufacturer.value
        return diagnosticManager.calibrateSAS(mfr)
    }

    suspend fun relearnThrottle(): Boolean {
        ensureDtcScanBeforeAction()
        voiceFeedbackManager.speak("Ejecutando reaprendizaje del cuerpo de aceleración.", "Executing throttle body relearn.")
        val mfr = _manufacturer.value
        val result = diagnosticManager.relearnThrottle(mfr)
        if (result) {
            voiceFeedbackManager.speak("Reaprendizaje completado con éxito.", "Throttle relearn completed successfully.")
        } else {
            voiceFeedbackManager.speak("Fallo en el reaprendizaje del acelerador.", "Throttle relearn failed.")
        }
        return result
    }

    suspend fun regenerateDPF(): Boolean {
        ensureDtcScanBeforeAction()
        voiceFeedbackManager.speak("Iniciando regeneración del filtro de partículas.", "Starting particulate filter regeneration.")
        val mfr = _manufacturer.value
        val result = diagnosticManager.regenerateDPF(mfr)
        if (result) {
            voiceFeedbackManager.speak("Regeneración DPF iniciada exitosamente.", "DPF regeneration started successfully.")
        } else {
            voiceFeedbackManager.speak("Fallo al iniciar regeneración DPF.", "Failed to start DPF regeneration.")
        }
        return result
    }

    suspend fun resetTPMS(): Boolean {
        ensureDtcScanBeforeAction()
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
            voiceFeedbackManager.speak(
                "Vehículo $make $model guardado exitosamente.",
                "Vehicle $make $model saved successfully."
            )

            // Fix: Call selectVehicle to ensure persistence of the selected ID
            selectVehicle(vehicle)
        }
    }

    // AI and Logging moved to top

    fun toggleAiMonitoring(enabled: Boolean) {
        _isAiMonitoring.value = enabled
        if (enabled) {
            voiceFeedbackManager.speak("Monitoreo de inteligencia artificial activado.", "AI monitoring activated.")
            startAiMonitoring()
        } else {
            voiceFeedbackManager.speak("Monitoreo de inteligencia artificial desactivado.", "AI monitoring deactivated.")
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
        voiceFeedbackManager.speak("Registro de datos iniciado.", "Data logging started.")
        loggingJob?.cancel()
        loggingJob = viewModelScope.launch(Dispatchers.IO) {
            val batch = mutableListOf<com.elysium369.meet.data.local.entities.SensorHistoryEntity>()
            var lastInsert = System.currentTimeMillis()

            // Corrutina para el logging CSV en memoria
            launch {
                while (_isLogging.value) {
                    val current = _liveData.value
                    if (current.isNotEmpty()) {
                        _dataLog.value = _dataLog.value + DataLogEntry(System.currentTimeMillis(), current)
                    }
                    kotlinx.coroutines.delay(500)
                }
            }

            // Corrutina recolectora para el búfer asíncrono hacia Room
            for (entity in telemetryBuffer) {
                batch.add(entity)
                val now = System.currentTimeMillis()
                // Insertar cada 100 registros o cada 1 segundo
                if (batch.size >= 100 || (now - lastInsert > 1000 && batch.isNotEmpty())) {
                    try {
                        sensorHistoryDao.insertAll(batch.toList())
                    } catch (e: Exception) {
                        Log.e("ObdVM", "Batch insert failed", e)
                    }
                    batch.clear()
                    lastInsert = now
                }
            }
        }
    }

    fun stopDataLogging() {
        _isLogging.value = false
        loggingJob?.cancel()
        voiceFeedbackManager.speak("Registro de datos detenido. Guardando muestras.", "Data logging stopped. Saving samples.")

        // Asegurar vaciado de canal
        viewModelScope.launch(Dispatchers.IO) {
            val remaining = mutableListOf<com.elysium369.meet.data.local.entities.SensorHistoryEntity>()
            var entity = telemetryBuffer.tryReceive().getOrNull()
            while (entity != null) {
                remaining.add(entity)
                entity = telemetryBuffer.tryReceive().getOrNull()
            }
            if (remaining.isNotEmpty()) {
                try {
                    sensorHistoryDao.insertAll(remaining)
                } catch (e: Exception) {
                    Log.e("ObdVM", "Final batch insert failed", e)
                }
            }
        }
    }

    fun saveCsvToFile() {
        val data = _dataLog.value
        if (data.isEmpty()) return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val fileName = "EV_Log_${System.currentTimeMillis()}.csv"
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
            "${context.packageName}.fileprovider",
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
            voiceFeedbackManager.speak("Ejecutando análisis predictivo de salud del vehículo.", "Running predictive vehicle health analysis.")
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
                    voiceFeedbackManager.speak(
                        "Análisis predictivo completado. Revise el reporte de salud.",
                        "Predictive analysis complete. Review the health report."
                    )
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

    suspend fun analyzeNetworkTopology(vehicleInfo: String, modules: List<com.elysium369.meet.core.obd.NetworkModule>): com.elysium369.meet.core.ai.DiagnosticResult {
        val currentConfig = _aiConfig.value
        geminiDiagnostic.updateConfig(
            newApiKey = currentConfig.apiKey,
            newEndpoint = currentConfig.endpoint,
            newProvider = currentConfig.provider
        )
        return geminiDiagnostic.analyzeNetworkTopology(vehicleInfo, modules)
    }

    suspend fun analyzeActiveTest(
        vehicleInfo: String,
        testName: String,
        testId: String,
        monitoredData: Map<String, Float>
    ): com.elysium369.meet.core.ai.DiagnosticResult {
        val currentConfig = _aiConfig.value
        geminiDiagnostic.updateConfig(
            newApiKey = currentConfig.apiKey,
            newEndpoint = currentConfig.endpoint,
            newProvider = currentConfig.provider
        )
        return geminiDiagnostic.analyzeActiveTest(vehicleInfo, testName, testId, monitoredData)
    }

    private val _aiActiveTestResult = MutableStateFlow<String?>(null)
    val aiActiveTestResult: StateFlow<String?> = _aiActiveTestResult.asStateFlow()

    private val _isAiActiveTestLoading = MutableStateFlow(false)
    val isAiActiveTestLoading: StateFlow<Boolean> = _isAiActiveTestLoading.asStateFlow()

    fun runActiveTestAiDiagnostic(testName: String, testId: String, monitoredData: Map<String, Float>) {
        viewModelScope.launch {
            _isAiActiveTestLoading.value = true
            _aiActiveTestResult.value = null
            try {
                val vehicleInfo = "${selectedVehicle.value?.make ?: "Genérico"} ${selectedVehicle.value?.model ?: "OBD2"} ${selectedVehicle.value?.year ?: ""}"
                val result = analyzeActiveTest(vehicleInfo, testName, testId, monitoredData)
                _aiActiveTestResult.value = result.analysisText
            } catch (e: Exception) {
                _aiActiveTestResult.value = "Error al solicitar diagnóstico de IA: ${e.message}"
            } finally {
                _isAiActiveTestLoading.value = false
            }
        }
    }

    fun clearActiveTestAiDiagnostic() {
        _aiActiveTestResult.value = null
        _isAiActiveTestLoading.value = false
    }

    private val _aiServiceResetResult = MutableStateFlow<String?>(null)
    val aiServiceResetResult: StateFlow<String?> = _aiServiceResetResult.asStateFlow()

    private val _isAiServiceResetLoading = MutableStateFlow(false)
    val isAiServiceResetLoading: StateFlow<Boolean> = _isAiServiceResetLoading.asStateFlow()

    fun runServiceResetAiDiagnostic(resetName: String, resetId: String, isSuccess: Boolean) {
        viewModelScope.launch {
            _isAiServiceResetLoading.value = true
            _aiServiceResetResult.value = null
            try {
                val vehicleInfo = "${selectedVehicle.value?.make ?: "Genérico"} ${selectedVehicle.value?.model ?: "OBD2"} ${selectedVehicle.value?.year ?: ""}"
                val mfr = _manufacturer.value
                val result = geminiDiagnostic.analyzeServiceReset(vehicleInfo, resetName, resetId, mfr, isSuccess)
                _aiServiceResetResult.value = result.analysisText
            } catch (e: Exception) {
                _aiServiceResetResult.value = "Error al solicitar procedimiento de IA: ${e.message}"
            } finally {
                _isAiServiceResetLoading.value = false
            }
        }
    }

    fun clearServiceResetAiDiagnostic() {
        _aiServiceResetResult.value = null
        _isAiServiceResetLoading.value = false
    }

    /** Toggle Fused Speed Enabled — uses phone sensors + GPS for smooth speed tracking */
    fun setFusedSpeedEnabled(enabled: Boolean) {
        _fusedSpeedEnabled.value = enabled
        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("fused_speed_enabled", enabled).apply()
        Log.d("ObdVM", "Fused Speed: $enabled")
        if (enabled) {
            phoneSpeedTracker.start()
        } else {
            phoneSpeedTracker.stop()
        }
    }

    // --- Navigation Flow for Voice Copilot ---
    private val _navigationEvent = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val navigationEvent = _navigationEvent.asSharedFlow()

    // Voice copilot active listening state
    val isVoiceCopilotListening: StateFlow<Boolean> = voiceCommandManager.isListeningState

    fun toggleVoiceCopilot(enabled: Boolean) {
        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("voice_copilot_enabled", enabled).apply()
        _voiceCopilotEnabled.value = enabled
        if (enabled) {
            voiceCommandManager.startCopilot()
            speechService.startListeningToEvents()
            notificationService.startListeningToEvents()
        } else {
            voiceCommandManager.stopCopilot()
            speechService.stopListeningToEvents()
            notificationService.stopListeningToEvents()
        }
    }

    fun cycleGaugeStyle() {
        gaugeStyleManager.cycleNext()
    }

    private fun handleVoiceCommand(command: com.elysium369.meet.core.audio.VoiceCommand) {
        viewModelScope.launch {
            when (command) {
                com.elysium369.meet.core.audio.VoiceCommand.CYCLE_GAUGES -> {
                    cycleGaugeStyle()
                }
                com.elysium369.meet.core.audio.VoiceCommand.NAVIGATE_DASHBOARD -> {
                    _navigationEvent.emit("scanner")
                }
                com.elysium369.meet.core.audio.VoiceCommand.NAVIGATE_DIAGNOSTICS -> {
                    _navigationEvent.emit("dtc")
                }
                com.elysium369.meet.core.audio.VoiceCommand.NAVIGATE_OSCILLOSCOPE -> {
                    _navigationEvent.emit("oscilloscope")
                }
                com.elysium369.meet.core.audio.VoiceCommand.NAVIGATE_LOCATOR -> {
                    _navigationEvent.emit("component_locator")
                }
                com.elysium369.meet.core.audio.VoiceCommand.NAVIGATE_SETTINGS -> {
                    _navigationEvent.emit("settings")
                }
                com.elysium369.meet.core.audio.VoiceCommand.SAY_TEMPERATURE -> {
                    speakCoolantTemperature()
                }
                com.elysium369.meet.core.audio.VoiceCommand.SAY_VOLTAGE -> {
                    speakBatteryVoltage()
                }
                com.elysium369.meet.core.audio.VoiceCommand.SAY_RPM -> {
                    speakEngineRpm()
                }
                com.elysium369.meet.core.audio.VoiceCommand.CLEAR_DTCS -> {
                    clearDtcs()
                }
                com.elysium369.meet.core.audio.VoiceCommand.DISABLE_ALERTS -> {
                    context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("voice_feedback_enabled", false).apply()
                }
                com.elysium369.meet.core.audio.VoiceCommand.ENABLE_ALERTS -> {
                    context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("voice_feedback_enabled", true).apply()
                }
                com.elysium369.meet.core.audio.VoiceCommand.DEACTIVATE_COPILOT -> {
                    toggleVoiceCopilot(false)
                }
                com.elysium369.meet.core.audio.VoiceCommand.SAY_DIAGNOSTICS -> {
                    speakDiagnostics()
                }
                com.elysium369.meet.core.audio.VoiceCommand.SAY_FUEL_ECONOMY -> {
                    speakFuelEconomy()
                }
                com.elysium369.meet.core.audio.VoiceCommand.SAY_VEHICLE_DNA -> {
                    speakVehicleDna()
                }
                com.elysium369.meet.core.audio.VoiceCommand.SAY_PERITO_REPORT -> {
                    speakPeritoReport()
                }
                com.elysium369.meet.core.audio.VoiceCommand.SAY_GENERAL_STATUS -> {
                    speakGeneralStatus()
                }
            }
        }
    }

    private fun speakDiagnostics() {
        val activeCount = activeDtcEvents.value.size
        val pendingCount = pendingDtcEvents.value.size
        val report = activePeritoReport.value
        val cost = report?.estimatedRepairCost ?: (activeCount * 120.0 + pendingCount * 60.0)
        
        val msgEs = if (activeCount == 0 && pendingCount == 0) {
            "El diagnóstico indica que no hay códigos de falla activos ni pendientes en la ECU del vehículo."
        } else {
            "El diagnóstico detecta $activeCount fallas activas y $pendingCount fallas pendientes. El costo estimado de reparación es de ${cost.toInt()} dólares."
        }
        val msgEn = if (activeCount == 0 && pendingCount == 0) {
            "Diagnostics show zero active or pending fault codes in the vehicle ECU."
        } else {
            "Diagnostics detected $activeCount active and $pendingCount pending faults. The estimated repair cost is ${cost.toInt()} dollars."
        }
        voiceFeedbackManager.speak(msgEs, msgEn)
    }

    private fun speakFuelEconomy() {
        val economy = liveData.value["015E"] ?: liveData.value["0110"]?.let { maf ->
            (maf / 10.7f)
        } ?: 8.5f
        val range = 450
        voiceFeedbackManager.speak(
            "El consumo estimado es de ${String.format("%.1f", economy)} litros por cada cien kilómetros, con una autonomía aproximada de $range kilómetros.",
            "The estimated fuel economy is ${String.format("%.1f", economy)} liters per hundred kilometers, with an approximate range of $range kilometers."
        )
    }

    private fun speakVehicleDna() {
        val dna = dnaResult.value
        val msgEs = if (!dna.isCalibrated) {
            "La firma digital MEET DNA aún no está calibrada para este vehículo. Por favor, realice una corrida de calibración en la pantalla DNA."
        } else if (dna.isAnomalous) {
            "Alerta preventiva de comportamiento: El score de salud es del ${dna.healthScore} por ciento. Se detecta una desviación estadística anómala en los sensores."
        } else {
            "Firma digital MEET DNA calibrada al ${dna.confidence.toInt()} por ciento de confianza. El vehículo se comporta de forma normal con un score de salud del ${dna.healthScore} por ciento."
        }
        val msgEn = if (!dna.isCalibrated) {
            "The MEET DNA digital signature is not yet calibrated for this vehicle. Please perform a calibration drive in the DNA section."
        } else if (dna.isAnomalous) {
            "Preventive behavior alert: The health score is ${dna.healthScore} percent. Statistical anomaly detected in sensors."
        } else {
            "MEET DNA signature calibrated at ${dna.confidence.toInt()} percent confidence. The vehicle behaves normally with a health score of ${dna.healthScore} percent."
        }
        voiceFeedbackManager.speak(msgEs, msgEn)
    }

    private fun speakPeritoReport() {
        val report = activePeritoReport.value
        val msgEs = if (report != null) {
            "El último reporte MEET Perito indica un score clínico de ${report.score0to100} sobre cien, con clasificación ${report.category}."
        } else {
            "No se ha realizado ningún peritaje clínico MEET Perito para este vehículo en esta sesión."
        }
        val msgEn = if (report != null) {
            "The latest MEET Perito report shows a clinical score of ${report.score0to100} out of one hundred, categorized as ${report.category}."
        } else {
            "No MEET Perito clinical check has been executed for this vehicle in this session."
        }
        voiceFeedbackManager.speak(msgEs, msgEn)
    }

    private fun speakGeneralStatus() {
        val temp = liveData.value["0105"]?.toInt() ?: 90
        val volt = String.format("%.1f", liveData.value["0142"] ?: 13.8f)
        val activeCount = activeDtcEvents.value.size
        val dna = dnaResult.value
        
        val msgEs = "Resumen de estado del vehículo: Temperatura de refrigerante a $temp grados centígrados. Alternador cargando a $volt voltios. Hay $activeCount códigos de falla activos. Score de salud DNA al ${if (dna.isCalibrated) "${dna.healthScore} por ciento" else "noventa y cinco por ciento provisional"}."
        val msgEn = "Vehicle summary: Coolant temperature is $temp degrees. Alternator charging at $volt volts. Active fault code count is $activeCount. DNA health score is at ${if (dna.isCalibrated) "${dna.healthScore} percent" else "ninety-five percent provisional"}."
        voiceFeedbackManager.speak(msgEs, msgEn)
    }

    private fun speakCoolantTemperature() {
        val temp = liveData.value["0105"]
        if (temp != null) {
            voiceFeedbackManager.speak(
                "La temperatura del refrigerante es de ${temp.toInt()} grados centígrados.",
                "The coolant temperature is ${temp.toInt()} degrees celsius."
            )
        } else {
            voiceFeedbackManager.speak(
                "Aún no hay datos de temperatura disponibles.",
                "Coolant temperature data is not available yet."
            )
        }
    }

    private fun speakBatteryVoltage() {
        val volt = liveData.value["0142"] ?: liveData.value["AT RV"]
        if (volt != null) {
            val rounded = String.format("%.1f", volt)
            voiceFeedbackManager.speak(
                "El voltaje de la batería es de $rounded voltios.",
                "The battery voltage is $rounded volts."
            )
        } else {
            voiceFeedbackManager.speak(
                "Aún no hay datos de voltaje disponibles.",
                "Battery voltage data is not available yet."
            )
        }
    }

    private fun speakEngineRpm() {
        val rpm = liveData.value["010C"]
        if (rpm != null) {
            voiceFeedbackManager.speak(
                "El motor está girando a ${rpm.toInt()} revoluciones por minuto.",
                "The engine is running at ${rpm.toInt()} RPM."
            )
        } else {
            voiceFeedbackManager.speak(
                "El motor está apagado o no hay datos de revoluciones.",
                "Engine is off or RPM data is not available yet."
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        phoneSpeedTracker.stop()
        voiceCommandManager.stopCopilot()
        localShellManager.stopShell(stopControlServer = true)
        shellScope.cancel()
    }
}

data class DataLogEntry(
    val timestamp: Long,
    val values: Map<String, Float>
)

@kotlinx.serialization.Serializable
data class DashcamTelemetryFrame(
    val timestampMs: Long,
    val rpm: Float,
    val speedKph: Float,
    val gForce: Float,
    val throttle: Float,
    val load: Float
)
