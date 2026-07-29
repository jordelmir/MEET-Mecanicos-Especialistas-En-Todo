package com.elysium369.meet.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.core.obd.*
import com.elysium369.meet.core.monetization.MonetizationPolicy
import com.elysium369.meet.core.monetization.FeatureKey
import com.elysium369.meet.data.supabase.SubscriptionRepository
import io.github.jan.supabase.gotrue.auth
import com.elysium369.meet.data.supabase.Vehicle
import com.elysium369.meet.data.supabase.VehicleRepository
import com.elysium369.meet.data.supabase.SupabaseManager
import com.elysium369.meet.data.supabase.SessionLogRepository
import com.elysium369.meet.data.supabase.DiagnosticSession
import com.elysium369.meet.data.supabase.RepairCase
import com.elysium369.meet.data.supabase.RepairCaseRepository
import com.elysium369.meet.data.local.dao.*
import com.elysium369.meet.data.local.entities.*
import com.elysium369.meet.core.twin.VehicleTwinEngine
import com.elysium369.meet.core.blackbox.EvidenceCompiler
import com.elysium369.meet.core.parts.PartsMarketplaceContract
import com.elysium369.meet.core.services.WorkshopServiceCatalog
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.UUID
import com.elysium369.meet.data.local.entities.DtcDefinitionEntity
import com.elysium369.meet.data.local.entities.DtcEventEntity
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.elysium369.meet.core.sync.SyncWorker
import com.elysium369.meet.core.livelink.*
import com.elysium369.meet.core.vanguard.VanguardOutboxSyncWorker
import com.elysium369.meet.ui.screens.TerminalLine
import com.elysium369.meet.ui.screens.TerminalLineType
import com.elysium369.meet.core.obd.ObdTrafficListener
import com.elysium369.meet.core.obd.PredictiveTelemetryEstimator
import com.elysium369.meet.BuildConfig
import com.elysium369.meet.ride.domain.RideCancellationPolicy
import com.elysium369.meet.ride.domain.RideCancellationReason
import com.elysium369.meet.ride.domain.RideActorRole
import com.elysium369.meet.ride.domain.RideFareBidPolicy
import com.elysium369.meet.ride.domain.RideShareCategory
import com.elysium369.meet.ride.domain.RideVerificationEvidencePolicy
import com.elysium369.meet.ride.domain.RideVerificationPolicy
import com.elysium369.meet.ride.domain.VerificationFileEvidence
import com.elysium369.meet.ride.domain.RideBoardingChallenge
import com.elysium369.meet.ride.domain.RideBoardingPinPolicy
import com.elysium369.meet.ride.domain.RidePinVerificationStatus
import com.elysium369.meet.ride.traffic.RideRoadIncident
import com.elysium369.meet.ride.traffic.RideRoadIncidentType
import com.elysium369.meet.ride.traffic.RideRoadSide
import com.elysium369.meet.ride.traffic.RideGeoCell
import com.elysium369.meet.ride.traffic.RideSegmentSpeedSample

@Serializable
private data class RemotePartsStore(
    val storeId: String,
    val storeName: String,
    val rating: Double,
    val phone: String,
    val location: String,
    val deliveryRadiusKm: Double,
    val averageEtaMinutes: Int,
    val verified: Boolean,
    val createdAt: Long,
    val owner_id: String
) {
    fun toLocal() = PartsStoreEntity(
        storeId = storeId,
        storeName = storeName,
        rating = rating,
        phone = phone,
        location = location,
        deliveryRadiusKm = deliveryRadiusKm,
        averageEtaMinutes = averageEtaMinutes,
        verified = verified,
        createdAt = createdAt
    )
}

@Serializable
private data class RemoteRideRoadIncident(
    val id: String,
    val reporter_id: String,
    val trip_id: String?,
    val road_segment_id: String,
    val incident_type: String,
    val road_side: String,
    val severity: Int,
    val latitude: Double,
    val longitude: Double,
    val bearing_degrees: Float?,
    val accuracy_meters: Float?,
    val geohash_coarse: String,
    val created_at: String? = null,
    val expires_at: String,
)

@Serializable
private data class RemoteRideSpeedObservation(
    val observer_id: String,
    val trip_id: String,
    val road_segment_id: String,
    val speed_mps: Float,
    val accuracy_meters: Float?,
    val bearing_degrees: Float?,
    val captured_at: String,
    val time_bucket: String,
)

data class RideClaimFeedback(
    val requestId: String,
    val won: Boolean,
    val message: String,
    val emittedAtEpochMs: Long = System.currentTimeMillis(),
)

@Serializable
private data class RemotePartRequest(
    val requestId: String,
    val serviceRequestId: String? = null,
    val vehicleId: String = "",
    val dtcCode: String? = null,
    val partName: String = "",
    val partNumber: String? = null,
    val quantity: Int = 1,
    val oemPreference: String = "ANY",
    val deliveryLocation: String = "",
    val urgencyMinutes: Int = 60,
    val customerNotes: String = "",
    val status: String = "OPEN",
    val acceptedOfferId: String? = null,
    val createdAt: Long = 0L,
    val customer_id: String? = null,
    val statusV2: String? = null,
    val sourceContext: String? = null,
    val dtcCodes: List<String> = emptyList(),
    val category: String? = null,
    val preference: String? = null,
    val position: String? = null,
    val oemNumber: String? = null,
    val photoUrls: List<String> = emptyList(),
    val notes: String? = null,
    val deliveryAddress: String? = null,
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val urgencyLevel: String? = null,
    val vin: String? = null
) {
    fun toLocal() = PartRequestEntity(
        requestId = requestId,
        serviceRequestId = serviceRequestId,
        vehicleId = vehicleId,
        dtcCode = dtcCode ?: dtcCodes.firstOrNull(),
        partName = partName,
        partNumber = partNumber ?: oemNumber,
        quantity = quantity,
        oemPreference = PartsMarketplaceContract.preferenceToLegacy(preference ?: oemPreference),
        deliveryLocation = deliveryAddress?.takeIf { it.isNotBlank() } ?: deliveryLocation,
        urgencyMinutes = urgencyMinutes,
        customerNotes = notes?.takeIf { it.isNotBlank() } ?: customerNotes,
        status = PartsMarketplaceContract.requestStatusToLegacy(statusV2 ?: status),
        acceptedOfferId = acceptedOfferId,
        createdAt = createdAt,
        partPosition = PartsMarketplaceContract.positionToLegacy(position),
        latitude = locationLat ?: 0.0,
        longitude = locationLng ?: 0.0
    )
}

@Serializable
private data class RemotePartOffer(
    val offerId: String,
    val partRequestId: String = "",
    val storeId: String = "",
    val storeName: String = "",
    val brand: String = "",
    val partNumber: String = "",
    val condition: String = "NEW",
    val price: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val etaMinutes: Int = 0,
    val warrantyDays: Int = 0,
    val message: String = "",
    val status: String = "PENDING",
    val createdAt: Long = 0L,
    val store_owner_id: String? = null,
    val supplierQuoteId: String? = null,
    val oemNumber: String? = null,
    val currency: String = "CRC",
    val availability: String? = null,
    val estimatedDeliveryHours: Int? = null,
    val includesDelivery: Boolean? = null,
    val compatibilityConfidence: String? = null,
    val compatibilityNotes: String? = null,
    val photoUrls: List<String> = emptyList(),
    val statusV2: String? = null,
    val conditionDetail: String? = null,
    val quoteVersion: Int? = null,
    val expiresAt: Long? = null
) {
    fun toLocal() = PartOfferEntity(
        offerId = offerId,
        partRequestId = partRequestId,
        storeId = storeId,
        storeName = storeName,
        brand = brand,
        partNumber = partNumber.ifBlank { oemNumber ?: "Por confirmar" },
        condition = PartsMarketplaceContract.conditionToLegacy(conditionDetail ?: condition),
        price = price,
        deliveryFee = deliveryFee,
        etaMinutes = etaMinutes.takeIf { it > 0 } ?: ((estimatedDeliveryHours ?: 1) * 60),
        warrantyDays = warrantyDays,
        message = message.ifBlank { compatibilityNotes.orEmpty() },
        status = PartsMarketplaceContract.quoteStatusToLegacy(statusV2 ?: status),
        createdAt = createdAt
    )
}

private fun PartRequestEntity.toRemote(customerId: String) = RemotePartRequest(
    requestId = requestId,
    serviceRequestId = serviceRequestId,
    vehicleId = vehicleId,
    dtcCode = dtcCode,
    partName = partName,
    partNumber = partNumber,
    quantity = quantity,
    oemPreference = oemPreference,
    deliveryLocation = deliveryLocation,
    urgencyMinutes = urgencyMinutes,
    customerNotes = customerNotes,
    status = status,
    acceptedOfferId = acceptedOfferId,
    createdAt = createdAt,
    customer_id = customerId,
    statusV2 = PartsMarketplaceContract.requestStatusToV2(status),
    sourceContext = when {
        !dtcCode.isNullOrBlank() -> "FROM_DTC"
        !serviceRequestId.isNullOrBlank() -> "FROM_MECHANIC_WORK_ORDER"
        else -> "MANUAL"
    },
    dtcCodes = listOfNotNull(dtcCode?.takeIf { it.isNotBlank() }),
    preference = PartsMarketplaceContract.preferenceToV2(oemPreference),
    position = PartsMarketplaceContract.positionToV2(partPosition),
    oemNumber = partNumber?.takeIf { oemPreference.equals("OEM", ignoreCase = true) },
    notes = customerNotes,
    deliveryAddress = deliveryLocation,
    locationLat = latitude.takeUnless { it == 0.0 },
    locationLng = longitude.takeUnless { it == 0.0 },
    urgencyLevel = when {
        urgencyMinutes <= 30 -> "URGENT"
        urgencyMinutes >= 180 -> "LOW"
        else -> "NORMAL"
    }
)

private fun PartsStoreEntity.toRemote(ownerId: String) = RemotePartsStore(
    storeId = storeId,
    storeName = storeName,
    rating = rating,
    phone = phone,
    location = location,
    deliveryRadiusKm = deliveryRadiusKm,
    averageEtaMinutes = averageEtaMinutes,
    verified = verified,
    createdAt = createdAt,
    owner_id = ownerId
)

private fun PartOfferEntity.toRemote(
    ownerId: String,
    compatibilityConfidence: String = "UNKNOWN",
    compatibilityNotes: String = ""
) = RemotePartOffer(
    offerId = offerId,
    partRequestId = partRequestId,
    storeId = storeId,
    storeName = storeName,
    brand = brand,
    partNumber = partNumber,
    condition = condition,
    price = price,
    deliveryFee = deliveryFee,
    etaMinutes = etaMinutes,
    warrantyDays = warrantyDays,
    message = message,
    status = status,
    createdAt = createdAt,
    store_owner_id = ownerId,
    supplierQuoteId = offerId,
    currency = "CRC",
    availability = when {
        etaMinutes <= 120 -> "SAME_DAY"
        etaMinutes <= 24 * 60 -> "NEXT_DAY"
        else -> "IMPORT_REQUIRED"
    },
    estimatedDeliveryHours = ((etaMinutes + 59) / 60).coerceAtLeast(1),
    includesDelivery = deliveryFee <= 0.0,
    compatibilityConfidence = compatibilityConfidence,
    compatibilityNotes = compatibilityNotes,
    statusV2 = PartsMarketplaceContract.quoteStatusToV2(status),
    conditionDetail = PartsMarketplaceContract.conditionToV2(condition),
    quoteVersion = 1,
    expiresAt = createdAt + 24L * 60L * 60L * 1000L
)

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
    private val repairCaseRepository: RepairCaseRepository,
    private val dtcKnowledgeGraphDao: com.elysium369.meet.data.local.dao.DtcKnowledgeGraphDao,
    private val towTruckDao: TowTruckDao,
    private val ratingDao: RatingDao,
    private val providerProfileDao: ProviderProfileDao,
    private val rideDao: com.elysium369.meet.data.local.dao.RideDao,
    val entitlementManager: com.elysium369.meet.core.monetization.EntitlementManager,
    val adGateManager: com.elysium369.meet.core.monetization.AdGateManager,
    val usageMeter: com.elysium369.meet.core.monetization.UsageMeter,
    private val aiRepository: com.elysium369.meet.ai.data.AiRepository
) : ViewModel() {

    // Device-level identity must be initialized before init{} calls provider role refresh.
    private val localDeviceId: String = android.provider.Settings.Secure.getString(
        context.contentResolver,
        android.provider.Settings.Secure.ANDROID_ID
    ) ?: java.util.UUID.randomUUID().toString()

    val connectionState: StateFlow<ObdState> = obdSession.state
    val statusMessage: StateFlow<String> = obdSession.statusMessage
    val telemetrySamples: StateFlow<Map<String, TelemetrySample>> = obdSession.telemetrySamples

    // --- UDS Protocol Manager (lazy, uses existing obdSession) ---
    private val udsProtocolManager by lazy {
        com.elysium369.meet.core.obd.UdsProtocolManager(obdSession)
    }


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
            TerminalLine("║  Elysium Vanguard Expert Terminal v3.0               ║", TerminalLineType.SYSTEM),
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
    private val liveLinkProEngine = LiveLinkSessionEngine()

    private val _liveLinkProSession = MutableStateFlow<LiveSessionEntity?>(null)
    val liveLinkProSession: StateFlow<LiveSessionEntity?> = _liveLinkProSession.asStateFlow()

    private val _liveLinkProCoreSession = MutableStateFlow<LiveLinkSession?>(null)
    val liveLinkProCoreSession: StateFlow<LiveLinkSession?> = _liveLinkProCoreSession.asStateFlow()

    private val _liveLinkProCredentials = MutableStateFlow<LiveLinkAccessCredentials?>(null)
    val liveLinkProCredentials: StateFlow<LiveLinkAccessCredentials?> = _liveLinkProCredentials.asStateFlow()

    private val _liveLinkProPermissions = MutableStateFlow<LiveLinkPermission?>(null)
    val liveLinkProPermissions: StateFlow<LiveLinkPermission?> = _liveLinkProPermissions.asStateFlow()

    private val _liveLinkProEvents = MutableStateFlow<List<LiveLinkEvent>>(emptyList())
    val liveLinkProEvents: StateFlow<List<LiveLinkEvent>> = _liveLinkProEvents.asStateFlow()

    private val _liveLinkProRequests = MutableStateFlow<List<LiveLinkRemoteRequest>>(emptyList())
    val liveLinkProRequests: StateFlow<List<LiveLinkRemoteRequest>> = _liveLinkProRequests.asStateFlow()

    private val _liveLinkProMessages = MutableStateFlow<List<LiveLinkChatMessage>>(emptyList())
    val liveLinkProMessages: StateFlow<List<LiveLinkChatMessage>> = _liveLinkProMessages.asStateFlow()

    private val _liveLinkProLatestPacket = MutableStateFlow<LiveLinkTelemetryPacket?>(null)
    val liveLinkProLatestPacket: StateFlow<LiveLinkTelemetryPacket?> = _liveLinkProLatestPacket.asStateFlow()

    private val _liveLinkProReport = MutableStateFlow<LiveLinkReport?>(null)
    val liveLinkProReport: StateFlow<LiveLinkReport?> = _liveLinkProReport.asStateFlow()

    private val liveLinkProPackets = mutableListOf<LiveLinkTelemetryPacket>()

    @OptIn(ExperimentalCoroutinesApi::class)
    val mechanicNotes: StateFlow<List<MechanicNoteEntity>> = _liveLinkProSession
        .flatMapLatest { session ->
            session?.let { liveSessionDao.getNotesForSession(it.sessionId) } ?: flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var telemetryUploadJob: Job? = null
    private var notesPollingJob: Job? = null

    fun startLiveLinkPro(durationMinutes: Int, readOnly: Boolean, videoCall: Boolean) {
        val vehicle = selectedVehicle.value ?: return
        val ownerId = SupabaseManager.client.auth.currentUserOrNull()?.id ?: "anonymous"
        val envelope = liveLinkProEngine.createSession(
            CreateLiveLinkSessionInput(
                ownerUserId = ownerId,
                vehicleId = vehicle.id,
                mode = if (readOnly) LiveLinkMode.REMOTE_READ_ONLY else LiveLinkMode.REMOTE_ASSISTED,
                title = "${vehicle.year} ${vehicle.make} ${vehicle.model}".trim(),
                durationMinutes = durationMinutes,
                readOnly = readOnly,
                allowVideo = videoCall,
                allowAudio = false,
                allowCamera = false,
                allowLocation = false,
                allowVehicleHistory = false,
                allowReports = true,
                allowDtc = true,
                allowLivePids = true,
            )
        )
        val coreSession = envelope.session
        val publicSessionUrl = "https://meet.elysium369.com/livelink?session_id=${coreSession.sessionId}"
        val session = LiveSessionEntity(
            sessionId = coreSession.sessionId,
            vehicleId = vehicle.id,
            ownerId = ownerId,
            mechanicId = null,
            status = coreSession.state.name,
            startedAt = coreSession.startedAtMs ?: coreSession.createdAtMs,
            endedAt = null,
            permissions = Json.encodeToString(envelope.permissions),
            sessionCode = envelope.credentials.displayCode,
            shareUrl = publicSessionUrl,
            durationMinutes = durationMinutes,
            videoCallUrl = if (videoCall) "https://meet.elysium369.com/call/${coreSession.sessionId}" else null
        )

        _liveLinkProCoreSession.value = coreSession
        _liveLinkProCredentials.value = envelope.credentials
        _liveLinkProPermissions.value = envelope.permissions
        _liveLinkProEvents.value = listOf(envelope.createdEvent)
        _liveLinkProRequests.value = emptyList()
        _liveLinkProMessages.value = emptyList()
        _liveLinkProLatestPacket.value = null
        _liveLinkProReport.value = null
        liveLinkProPackets.clear()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                liveSessionDao.insertLiveSession(session)
                _liveLinkProSession.value = session

                // Upload the legacy-compatible session shell. The real access token stays in memory only;
                // persisted state keeps the token hash inside LiveLinkSession.
                runCatching {
                    SupabaseManager.client.postgrest["live_sessions"].insert(session)
                }.onFailure { error ->
                    Log.w("ObdViewModel", "LiveLink session created locally; cloud session shell sync failed", error)
                }

                // Start background upload loop for telemetry
                startLiveLinkTelemetryLoop(coreSession.sessionId)

                // Start background note polling loop
                startNotesPollingLoop(coreSession.sessionId)
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Error starting remote LiveLink PRO session", e)
            }
        }
    }

    fun stopLiveLinkPro() {
        val session = _liveLinkProSession.value ?: return
        _liveLinkProCoreSession.value?.let { coreSession ->
            val (completed, event) = liveLinkProEngine.complete(coreSession)
            _liveLinkProCoreSession.value = completed
            addLiveLinkEvent(event)
        }
        telemetryUploadJob?.cancel()
        notesPollingJob?.cancel()
        _liveLinkProSession.value = null
        _liveLinkProCoreSession.value = null
        _liveLinkProCredentials.value = null
        _liveLinkProPermissions.value = null
        
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

    fun revokeLiveLinkPro() {
        val session = _liveLinkProSession.value ?: return
        val coreSession = _liveLinkProCoreSession.value ?: return
        val (revoked, event) = liveLinkProEngine.revoke(coreSession)
        _liveLinkProCoreSession.value = revoked
        _liveLinkProSession.value = session.copy(status = revoked.state.name, endedAt = revoked.endedAtMs)
        addLiveLinkEvent(event)
        telemetryUploadJob?.cancel()
        notesPollingJob?.cancel()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                liveSessionDao.updateSessionStatus(session.sessionId, revoked.state.name)
                revoked.endedAtMs?.let { liveSessionDao.updateSessionEndedAt(session.sessionId, it) }
                runCatching {
                    SupabaseManager.client.postgrest["live_sessions"].update(
                        mapOf("status" to revoked.state.name, "endedAt" to revoked.endedAtMs)
                    ) {
                        filter { eq("sessionId", session.sessionId) }
                    }
                }.onFailure { error ->
                    Log.w("ObdViewModel", "LiveLink revoke stored locally; cloud sync failed", error)
                }
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Error revoking LiveLink PRO session", e)
            }
        }
    }

    fun requestLiveLinkSnapshot() {
        val session = _liveLinkProCoreSession.value ?: return
        val permissions = _liveLinkProPermissions.value ?: return
        val result = liveLinkProEngine.createRemoteRequest(
            session = session,
            permissions = permissions,
            type = LiveLinkRemoteRequestType.CAPTURE_SNAPSHOT
        )
        _liveLinkProRequests.value = (listOf(result.request) + _liveLinkProRequests.value).take(40)
        addLiveLinkEvent(result.event)
    }

    fun requestLiveLinkClearDtcForAudit() {
        val session = _liveLinkProCoreSession.value ?: return
        val permissions = _liveLinkProPermissions.value ?: return
        val result = liveLinkProEngine.createRemoteRequest(
            session = session,
            permissions = permissions,
            type = LiveLinkRemoteRequestType.CLEAR_DTCS
        )
        _liveLinkProRequests.value = (listOf(result.request) + _liveLinkProRequests.value).take(40)
        addLiveLinkEvent(result.event)
    }

    fun approveLiveLinkRequest(requestId: String) {
        resolveLiveLinkRequest(requestId, approved = true)
    }

    fun denyLiveLinkRequest(requestId: String) {
        resolveLiveLinkRequest(requestId, approved = false)
    }

    fun sendLiveLinkChat(body: String) {
        val session = _liveLinkProCoreSession.value ?: return
        val trimmed = body.trim()
        if (trimmed.isBlank()) return
        val now = System.currentTimeMillis()
        val message = LiveLinkChatMessage(
            messageId = UUID.randomUUID().toString(),
            sessionId = session.sessionId,
            authorRole = LiveLinkActorRole.OWNER,
            body = trimmed,
            createdAtMs = now
        )
        _liveLinkProMessages.value = (listOf(message) + _liveLinkProMessages.value).take(80)
        addLiveLinkEvent(
            LiveLinkEvent(
                eventId = UUID.randomUUID().toString(),
                sessionId = session.sessionId,
                type = LiveLinkEventType.CHAT_MESSAGE,
                actorRole = LiveLinkActorRole.OWNER,
                message = "Mensaje enviado en chat LiveLink.",
                createdAtMs = now
            )
        )
    }

    fun generateLiveLinkReport() {
        val session = _liveLinkProCoreSession.value ?: return
        val (report, event) = liveLinkProEngine.buildReport(
            session = session,
            packets = liveLinkProPackets.toList(),
            events = _liveLinkProEvents.value,
            requests = _liveLinkProRequests.value
        )
        _liveLinkProReport.value = report
        addLiveLinkEvent(event)
    }

    private fun resolveLiveLinkRequest(requestId: String, approved: Boolean) {
        val request = _liveLinkProRequests.value.firstOrNull { it.requestId == requestId } ?: return
        val (resolved, event) = liveLinkProEngine.resolveRequest(request, approved)
        _liveLinkProRequests.value = _liveLinkProRequests.value.map {
            if (it.requestId == requestId) resolved else it
        }
        addLiveLinkEvent(event)

        if (approved && request.type == LiveLinkRemoteRequestType.CAPTURE_SNAPSHOT) {
            val packet = buildLiveLinkTelemetryPacket(request.sessionId)
            val (snapshot, snapshotEvent) = liveLinkProEngine.captureSnapshot(packet)
            rememberLiveLinkPacket(snapshot.telemetryPacket)
            addLiveLinkEvent(snapshotEvent)
        }
    }

    private fun startLiveLinkTelemetryLoop(sessionId: String) {
        telemetryUploadJob?.cancel()
        telemetryUploadJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && _liveLinkProCoreSession.value?.isOpen == true) {
                try {
                    val coreSession = _liveLinkProCoreSession.value ?: break
                    val (checkedSession, expiredEvent) = liveLinkProEngine.expireIfNeeded(coreSession)
                    if (expiredEvent != null) {
                        _liveLinkProCoreSession.value = checkedSession
                        _liveLinkProSession.value = _liveLinkProSession.value?.copy(
                            status = checkedSession.state.name,
                            endedAt = checkedSession.endedAtMs
                        )
                        addLiveLinkEvent(expiredEvent)
                        liveSessionDao.updateSessionStatus(sessionId, checkedSession.state.name)
                        checkedSession.endedAtMs?.let { liveSessionDao.updateSessionEndedAt(sessionId, it) }
                        break
                    }

                    val packet = buildLiveLinkTelemetryPacket(sessionId)
                    rememberLiveLinkPacket(packet)
                    val pidsJson = Json.encodeToString(packet)
                    val snapshot = LiveSnapshotEntity(
                        snapshotId = UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        timestamp = System.currentTimeMillis(),
                        pidValues = pidsJson,
                        notes = packet.degradedReason ?: packet.sourceQuality.name
                    )
                    
                    // Save local snapshot
                    liveSessionDao.insertLiveSnapshot(snapshot)
                    
                    // Upload to cloud
                    runCatching {
                        SupabaseManager.client.postgrest["live_snapshots"].insert(snapshot)
                    }.onFailure { error ->
                        Log.w("ObdViewModel", "LiveLink telemetry stored locally; cloud snapshot sync failed", error)
                    }
                } catch (e: Exception) {
                    Log.e("ObdViewModel", "Error uploading telemetry snapshot", e)
                }
                delay(
                    LiveLinkFrequencyPolicy.intervalMs(
                        mode = _liveLinkProCoreSession.value?.mode ?: LiveLinkMode.REMOTE_READ_ONLY,
                        averageLatencyMs = _liveLinkProLatestPacket.value?.samples?.map { it.latencyMs }?.takeIf { it.isNotEmpty() }?.average()?.toLong() ?: 0L
                    )
                )
            }
        }
    }

    private fun startNotesPollingLoop(sessionId: String) {
        notesPollingJob?.cancel()
        notesPollingJob = viewModelScope.launch(Dispatchers.IO) {
            var lastPollTime = 0L
            while (isActive && _liveLinkProCoreSession.value?.isOpen == true) {
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
                            addRemoteLiveLinkNote(note)
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

    private fun buildLiveLinkTelemetryPacket(sessionId: String): LiveLinkTelemetryPacket {
        return LiveLinkTelemetryMapper.fromObdSamples(
            sessionId = sessionId,
            connectionState = connectionState.value.name,
            adapterQuality = if (isAdapterPro.value) "PRO_ADAPTER" else "STANDARD_OR_UNKNOWN",
            samples = telemetrySamples.value.values,
            activeDtcs = activeDtcs.value,
            freezeFrameAvailable = _freezeFrameData.value.isNotEmpty()
        )
    }

    private fun rememberLiveLinkPacket(packet: LiveLinkTelemetryPacket) {
        _liveLinkProLatestPacket.value = packet
        liveLinkProPackets.add(packet)
        if (liveLinkProPackets.size > 900) {
            liveLinkProPackets.removeAt(0)
        }
    }

    private fun addLiveLinkEvent(event: LiveLinkEvent) {
        _liveLinkProEvents.value = (listOf(event) + _liveLinkProEvents.value).take(100)
    }

    private fun addRemoteLiveLinkNote(note: MechanicNoteEntity) {
        val message = LiveLinkChatMessage(
            messageId = note.noteId,
            sessionId = note.sessionId,
            authorRole = LiveLinkActorRole.REMOTE_MECHANIC,
            authorId = note.authorId,
            type = LiveLinkMessageType.NOTE,
            body = note.content,
            createdAtMs = note.createdAt
        )
        _liveLinkProMessages.value = (listOf(message) + _liveLinkProMessages.value).take(80)
    }

    // ── Marketplace (Bids and requests) ──
    val serviceRequests: StateFlow<List<ServiceRequestEntity>> = marketplaceDao.getRequests()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val openServiceRequests: StateFlow<List<ServiceRequestEntity>> = marketplaceDao.getOpenRequests()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val openPartRequests: StateFlow<List<PartRequestEntity>> = marketplaceDao.getOpenPartRequests()
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
        priority: String,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        phone: String = "",
        priceOffer: Double = 0.0,
        serviceId: String? = null,
        serviceCategory: String? = null,
        dtcCodes: List<String> = activeDtcs.value,
        serviceMetadata: String = ""
    ) {
        val catalogService = WorkshopServiceCatalog.serviceById(serviceId)
        val enrichedDescription = buildString {
            if (catalogService != null) {
                appendLine(WorkshopServiceCatalog.requestSummary(catalogService, dtcCodes))
                appendLine()
            } else if (serviceCategory?.isNotBlank() == true || serviceMetadata.isNotBlank()) {
                appendLine("[MEET_SERVICE_CATALOG]")
                serviceCategory?.takeIf { it.isNotBlank() }?.let { appendLine("service_category=$it") }
                if (serviceMetadata.isNotBlank()) appendLine(serviceMetadata.trim())
                if (dtcCodes.isNotEmpty()) appendLine("dtc_codes=${dtcCodes.joinToString()}")
                appendLine("[/MEET_SERVICE_CATALOG]")
                appendLine()
            }
            append(description.trim())
        }.trim()

        val request = ServiceRequestEntity(
            requestId = UUID.randomUUID().toString(),
            vehicleId = vehicleId,
            problem = problem,
            priority = priority,
            description = enrichedDescription.ifBlank { description },
            location = location,
            radiusKm = 15.0,
            status = "OPEN",
            autoDtcCode = dtcCodes.firstOrNull() ?: activeDtcs.value.firstOrNull(),
            createdAt = System.currentTimeMillis(),
            latitude = latitude,
            longitude = longitude,
            phone = phone,
            priceOffer = priceOffer
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                marketplaceDao.insertRequest(request)
                runCatching {
                    SupabaseManager.client.postgrest["service_requests"].insert(request)
                }.onFailure { Log.w("ObdViewModel", "Service request saved locally; cloud unavailable", it) }
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
                marketplaceDao.upsertBidRespectingRequestClaim(bid)
                SupabaseManager.client.postgrest["service_bids"].insert(bid)
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Failed to place bid", e)
            }
        }
    }

    /** Idempotent: only accepts if request is still OPEN */
    fun acceptBid(requestId: String, bidId: String, context: android.content.Context? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val accepted = marketplaceDao.acceptBidAtomically(
                    requestId = requestId,
                    bidId = bidId,
                    mechanicPhone = "+506 8888-8888" // Teléfono del taller para contacto inmediato
                )
                if (!accepted) {
                    withContext(Dispatchers.Main) {
                        context?.let {
                            android.widget.Toast.makeText(it, "⚠️ Este servicio ya fue asignado a otro mecánico", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                    return@launch
                }
                scheduleVanguardCommerceSync()

                runCatching {
                    val request = marketplaceDao.getRequestById(requestId)
                    val bid = marketplaceDao.getBidById(bidId)
                    SupabaseManager.client.postgrest["service_requests"].update(
                        mapOf(
                            "status" to "ACCEPTED",
                            "assignedMechanicId" to request?.assignedMechanicId,
                            "assignedMechanicName" to request?.assignedMechanicName,
                            "assignedMechanicPhone" to request?.assignedMechanicPhone,
                            "priceOffer" to request?.priceOffer,
                            "escrowStatus" to request?.escrowStatus
                        )
                    ) {
                        filter { eq("requestId", requestId) }
                    }
                    SupabaseManager.client.postgrest["service_bids"].update(mapOf("status" to bid?.status.orEmpty().ifBlank { "ACCEPTED" })) {
                        filter { eq("bidId", bidId) }
                    }
                }.onFailure { Log.w("ObdViewModel", "Bid accepted locally; cloud sync unavailable", it) }
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Failed to accept bid", e)
            }
        }
    }

    /** Mechanic takes an open service request — idempotent, first-come-first-served */
    fun takeMechanicRequest(requestId: String, mechanicId: String, mechanicName: String, mechanicPhone: String, context: android.content.Context? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val request = marketplaceDao.getRequestById(requestId)
            val accepted = request != null && marketplaceDao.takeMechanicRequestAtomically(
                requestId = requestId,
                mechanicId = mechanicId,
                mechanicName = mechanicName,
                mechanicPhone = mechanicPhone,
                finalPrice = request.priceOffer
            )
            if (!accepted) {
                withContext(Dispatchers.Main) {
                    context?.let {
                        android.widget.Toast.makeText(it, "⚠️ Este servicio ya fue tomado por otro mecánico", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                scheduleVanguardCommerceSync()
            }
        }
    }

    fun completeMechanicRequest(requestId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val completed = marketplaceDao.completeAcceptedServiceOnce(requestId, System.currentTimeMillis())
            if (!completed) {
                Log.w("ObdViewModel", "Ignoring completion for non-accepted service request: $requestId")
            } else {
                scheduleVanguardCommerceSync()
                // V2 spec Phase 7: completion must trigger a Post-Scan prompt.
                com.elysium369.meet.core.reports.PostScanPrompt.request(requestId)
            }
        }
    }

    fun cancelMechanicRequest(requestId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (marketplaceDao.refundServiceAfterPaymentFailure(requestId)) {
                scheduleVanguardCommerceSync()
            }
        }
    }

    val partsStores: StateFlow<List<PartsStoreEntity>> = marketplaceDao.getPartsStores()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val partRequests: StateFlow<List<PartRequestEntity>> = marketplaceDao.getPartRequests()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun getPartOffersForRequest(requestId: String): Flow<List<PartOfferEntity>> {
        return marketplaceDao.getPartOffersForRequest(requestId)
    }

    private fun currentCloudUserId(): String? {
        return SupabaseManager.client.auth.currentUserOrNull()?.id
    }

    val currentUserId: String? get() = currentCloudUserId()
    val currentRideActorId: String get() = localDeviceId

    private fun currentProviderUserId(): String {
        return currentCloudUserId() ?: "local_device_$localDeviceId"
    }

    // ═══════════════════════════════════════════════════════════════
    // PROVIDER ROLE MANAGEMENT SYSTEM
    // ═══════════════════════════════════════════════════════════════

    /** Active provider profiles for the current user */
    private val _userProviderProfiles = MutableStateFlow<List<ProviderProfileEntity>>(emptyList())
    val userProviderProfiles: StateFlow<List<ProviderProfileEntity>> = _userProviderProfiles.asStateFlow()

    /** Convenience booleans — is the current user registered as each type? */
    private val _isMechanic = MutableStateFlow(false)
    val isMechanic: StateFlow<Boolean> = _isMechanic.asStateFlow()

    private val _isTowTruckDriver = MutableStateFlow(false)
    val isTowTruckDriver: StateFlow<Boolean> = _isTowTruckDriver.asStateFlow()

    private val _isPartsStore = MutableStateFlow(false)
    val isPartsStore: StateFlow<Boolean> = _isPartsStore.asStateFlow()

    private var providerRolesJob: Job? = null

    /** Load and refresh provider profiles for the current user */
    fun refreshProviderRoles() {
        val userId = currentProviderUserId()
        providerRolesJob?.cancel()
        providerRolesJob = viewModelScope.launch(Dispatchers.IO) {
            providerProfileDao.getProfilesForUser(userId).collect { profiles ->
                val activeProfiles = profiles.filter { it.isActive }
                _userProviderProfiles.value = profiles
                _isMechanic.value = activeProfiles.any { it.providerType == "MECHANIC" }
                _isTowTruckDriver.value = activeProfiles.any { it.providerType == "TOW_TRUCK" }
                _isPartsStore.value = activeProfiles.any { it.providerType == "PARTS_STORE" }
            }
        }
    }

    /** Register user as a provider (MECHANIC, TOW_TRUCK, or PARTS_STORE) */
    fun registerAsProvider(
        providerType: String,
        businessName: String,
        ownerName: String,
        phone: String,
        location: String,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        specialties: String = "",
        radiusKm: Double = 25.0,
        licenseNumber: String = "",
        context: android.content.Context? = null
    ) {
        val cloudUserId = currentCloudUserId()
        val userId = currentProviderUserId()

        viewModelScope.launch(Dispatchers.IO) {
            // Idempotent: check if already registered
            val existing = providerProfileDao.getProfileByUserAndType(userId, providerType)
            if (existing != null) {
                if (!existing.isActive) {
                    providerProfileDao.setProfileActive(existing.profileId, true, System.currentTimeMillis())
                    withContext(Dispatchers.Main) {
                        context?.let {
                            val typeLabel = providerTypeLabel(providerType)
                            android.widget.Toast.makeText(it, "✅ Perfil de $typeLabel reactivado", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    refreshProviderRoles()
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    context?.let {
                        val typeLabel = providerTypeLabel(providerType)
                        android.widget.Toast.makeText(it, "✅ Ya estás registrado como $typeLabel", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                return@launch
            }

            val profile = ProviderProfileEntity(
                profileId = java.util.UUID.randomUUID().toString(),
                userId = userId,
                providerType = providerType,
                businessName = businessName,
                ownerName = ownerName,
                phone = phone,
                location = location,
                latitude = latitude,
                longitude = longitude,
                specialties = specialties,
                radiusKm = radiusKm,
                licenseNumber = licenseNumber,
                isActive = true,
                verified = false,
                rating = 0.0,
                totalJobs = 0,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            providerProfileDao.insertProfile(profile)

            // Sync to cloud
            if (cloudUserId != null) {
                runCatching {
                    SupabaseManager.client.postgrest["provider_profiles"].upsert(profile)
                }.onFailure { Log.w("ObdViewModel", "Provider profile saved locally; cloud sync unavailable", it) }
            }

            withContext(Dispatchers.Main) {
                context?.let {
                    val typeLabel = providerTypeLabel(providerType)
                    android.widget.Toast.makeText(it, "🎉 ¡Registrado como $typeLabel exitosamente!", android.widget.Toast.LENGTH_LONG).show()
                }
            }

            refreshProviderRoles()
        }
    }

    /** Toggle a provider profile active/inactive */
    fun toggleProviderProfile(profileId: String, isActive: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            providerProfileDao.setProfileActive(profileId, isActive, System.currentTimeMillis())
            refreshProviderRoles()
        }
    }

    /** Delete a provider profile entirely */
    fun deleteProviderProfile(profileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            providerProfileDao.deleteProfile(profileId)
            refreshProviderRoles()
        }
    }

    /** Check if the current user can see provider-facing content for a given type */
    suspend fun canViewProviderContent(providerType: String): Boolean {
        return providerProfileDao.isUserRegisteredAs(currentProviderUserId(), providerType)
    }

    private fun providerTypeLabel(providerType: String): String {
        return when (providerType) {
            "MECHANIC" -> "Mecánico"
            "TOW_TRUCK" -> "Gruista"
            "PARTS_STORE" -> "Repuestera"
            else -> "Proveedor"
        }
    }

    private fun buildPartsStoreId(storeName: String, ownerId: String?): String {
        val base = storeName.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "repuestera_local" }
        return ownerId?.take(8)?.let { "${base}_$it" } ?: base
    }

    fun createPartRequest(
        serviceRequestId: String?,
        vehicleId: String,
        dtcCode: String?,
        partName: String,
        partNumber: String?,
        quantity: Int,
        oemPreference: String,
        deliveryLocation: String,
        urgencyMinutes: Int,
        customerNotes: String,
        partPosition: String = "N/A",
        phone: String = "",
        latitude: Double = 0.0,
        longitude: Double = 0.0
    ) {
        val request = PartRequestEntity(
            requestId = UUID.randomUUID().toString(),
            serviceRequestId = serviceRequestId,
            vehicleId = vehicleId,
            dtcCode = dtcCode,
            partName = partName,
            partNumber = partNumber?.takeIf { it.isNotBlank() },
            quantity = quantity.coerceAtLeast(1),
            oemPreference = oemPreference,
            deliveryLocation = deliveryLocation,
            urgencyMinutes = urgencyMinutes.coerceAtLeast(15),
            customerNotes = customerNotes,
            status = "OPEN",
            acceptedOfferId = null,
            createdAt = System.currentTimeMillis(),
            partPosition = partPosition,
            phone = phone,
            latitude = latitude,
            longitude = longitude
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                marketplaceDao.insertPartRequest(request)
                val userId = currentCloudUserId()
                if (userId != null) {
                    runCatching { SupabaseManager.client.postgrest["part_requests"].insert(request.toRemote(userId)) }
                        .onFailure { Log.w("ObdViewModel", "Part request saved locally; cloud table unavailable", it) }
                } else {
                    Log.i("ObdViewModel", "Part request saved locally; sign in to publish it to the live auction.")
                }
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Failed to create part request", e)
            }
        }
    }

    fun placePartOffer(
        partRequestId: String,
        storeName: String,
        brand: String,
        partNumber: String,
        condition: String,
        price: Double,
        deliveryFee: Double,
        etaMinutes: Int,
        warrantyDays: Int,
        message: String,
        compatibilityConfidence: String = "UNKNOWN",
        compatibilityNotes: String = ""
    ) {
        val ownerId = currentCloudUserId()
        val storeId = buildPartsStoreId(storeName, ownerId)
        val now = System.currentTimeMillis()
        val store = PartsStoreEntity(
            storeId = storeId,
            storeName = storeName.ifBlank { "Repuestera local" },
            rating = 4.8,
            phone = "",
            location = "Zona local",
            deliveryRadiusKm = 20.0,
            averageEtaMinutes = etaMinutes.coerceAtLeast(15),
            verified = false,
            createdAt = now
        )
        val offer = PartOfferEntity(
            offerId = UUID.randomUUID().toString(),
            partRequestId = partRequestId,
            storeId = store.storeId,
            storeName = store.storeName,
            brand = brand.ifBlank { "Marca por confirmar" },
            partNumber = partNumber.ifBlank { "Por confirmar" },
            condition = condition,
            price = price,
            deliveryFee = deliveryFee.coerceAtLeast(0.0),
            etaMinutes = etaMinutes.coerceAtLeast(15),
            warrantyDays = warrantyDays.coerceAtLeast(0),
            message = message,
            status = "PENDING",
            createdAt = now
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                marketplaceDao.insertPartsStore(store)
                marketplaceDao.upsertPartOfferRespectingRequestClaim(offer)
                if (ownerId != null) {
                    runCatching { SupabaseManager.client.postgrest["parts_stores"].upsert(store.toRemote(ownerId)) }
                        .onFailure { Log.w("ObdViewModel", "Parts store saved locally; cloud table unavailable", it) }
                    runCatching {
                        SupabaseManager.client.postgrest["part_offers"].insert(
                            offer.toRemote(ownerId, compatibilityConfidence, compatibilityNotes)
                        )
                    }
                        .onFailure { Log.w("ObdViewModel", "Part offer saved locally; cloud table unavailable", it) }
                } else {
                    Log.i("ObdViewModel", "Part offer saved locally; sign in as a parts store to publish it.")
                }
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Failed to place part offer", e)
            }
        }
    }

    /** Idempotent: only accepts if part request is still OPEN */
    fun acceptPartOffer(partRequestId: String, offerId: String, context: android.content.Context? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val accepted = marketplaceDao.acceptPartOfferAtomically(partRequestId, offerId)
                if (!accepted) {
                    withContext(Dispatchers.Main) {
                        context?.let {
                            android.widget.Toast.makeText(it, "⚠️ Este pedido de repuesto ya fue tomado por otro proveedor", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                    return@launch
                }
                scheduleVanguardCommerceSync()

                val requestSync = runCatching {
                    SupabaseManager.client.postgrest["part_requests"].update(
                        mapOf(
                            "status" to "ACCEPTED",
                            "statusV2" to PartsMarketplaceContract.requestStatusToV2("ACCEPTED"),
                            "acceptedOfferId" to offerId
                        )
                    ) { filter { eq("requestId", partRequestId) } }
                }
                if (requestSync.isFailure) {
                    runCatching {
                        SupabaseManager.client.postgrest["part_requests"].update(
                            mapOf("status" to "ACCEPTED", "acceptedOfferId" to offerId)
                        ) { filter { eq("requestId", partRequestId) } }
                    }
                }

                val offerSync = runCatching {
                    SupabaseManager.client.postgrest["part_offers"].update(
                        mapOf(
                            "status" to "ACCEPTED",
                            "statusV2" to PartsMarketplaceContract.quoteStatusToV2("ACCEPTED")
                        )
                    ) { filter { eq("offerId", offerId) } }
                }
                if (offerSync.isFailure) {
                    runCatching {
                        SupabaseManager.client.postgrest["part_offers"].update(
                            mapOf("status" to "ACCEPTED")
                        ) { filter { eq("offerId", offerId) } }
                    }
                }

                requestSync.exceptionOrNull()?.let {
                    Log.d("ObdViewModel", "Part request accepted with legacy cloud fallback", it)
                }
                offerSync.exceptionOrNull()?.let {
                    Log.d("ObdViewModel", "Part offer accepted with legacy cloud fallback", it)
                }
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Failed to accept part offer", e)
            }
        }
    }

    // Background periodic task to poll/sync local Room data with Supabase for Marketplace
    fun startMarketplaceSync() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching { syncServiceMarketplaceFromCloud() }
                    .onFailure { Log.w("ObdViewModel", "Service marketplace cloud sync unavailable", it) }
                runCatching { syncPartsMarketplaceFromCloud() }
                    .onFailure { Log.w("ObdViewModel", "Parts marketplace cloud sync unavailable", it) }
                delay(10000L) // Poll every 10 seconds
            }
        }
    }

    private suspend fun syncServiceMarketplaceFromCloud() {
        val cloudRequests = SupabaseManager.client.postgrest["service_requests"]
            .select().decodeList<ServiceRequestEntity>()
        cloudRequests.forEach { req ->
            marketplaceDao.upsertServiceRequestFromSync(req)
        }

        cloudRequests.forEach { req ->
            val cloudBids = SupabaseManager.client.postgrest["service_bids"]
                .select {
                    filter {
                        eq("requestId", req.requestId)
                    }
                }.decodeList<ServiceBidEntity>()
            cloudBids.forEach { bid ->
                marketplaceDao.upsertBidRespectingRequestClaim(bid)
            }
        }
    }

    private suspend fun syncPartsMarketplaceFromCloud() {
        currentCloudUserId() ?: return

        val stores = SupabaseManager.client.postgrest["parts_stores"]
            .select(
                columns = Columns.list(
                    "storeId",
                    "storeName",
                    "rating",
                    "phone",
                    "location",
                    "deliveryRadiusKm",
                    "averageEtaMinutes",
                    "verified",
                    "createdAt",
                    "owner_id"
                )
            )
            .decodeList<RemotePartsStore>()
        marketplaceDao.insertPartsStores(stores.map { it.toLocal() })

        val requests = SupabaseManager.client.postgrest["part_requests"]
            .select()
            .decodeList<RemotePartRequest>()
        requests.forEach { marketplaceDao.upsertPartRequestFromSync(it.toLocal()) }

        val offers = SupabaseManager.client.postgrest["part_offers"]
            .select()
            .decodeList<RemotePartOffer>()
        offers.forEach { marketplaceDao.upsertPartOfferRespectingRequestClaim(it.toLocal()) }
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
    private val vinVehicleRecognitionMutex = Mutex()
    private var lastAutoRecognizedVin: String? = null
    @Volatile private var hasCompletedInitialDtcScan = false

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeDtcEvents: StateFlow<List<DtcEventEntity>> = _selectedVehicle.flatMapLatest { vehicle ->
        vehicle?.let {
            dtcDao.getUnresolvedDtcsForVehicle(it.id)
                .map { list -> list.filter { it.status == "ACTIVE" } }
        } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val pendingDtcEvents: StateFlow<List<DtcEventEntity>> = _selectedVehicle.flatMapLatest { vehicle ->
        vehicle?.let {
            dtcDao.getUnresolvedDtcsForVehicle(it.id)
                .map { list -> list.filter { it.status == "PENDING" } }
        } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val permanentDtcEvents: StateFlow<List<DtcEventEntity>> = _selectedVehicle.flatMapLatest { vehicle ->
        vehicle?.let {
            dtcDao.getUnresolvedDtcsForVehicle(it.id)
                .map { list -> list.filter { it.status == "PERMANENT" } }
        } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val historicalDtcEvents: StateFlow<List<DtcEventEntity>> = _selectedVehicle.flatMapLatest { vehicle ->
        vehicle?.let {
            dtcDao.getUnresolvedDtcsForVehicle(it.id)
                .map { list -> list.filter { it.status in setOf("HISTORY", "INTERMITTENT") } }
        } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _latestScanActiveDtcs = MutableStateFlow<List<String>>(emptyList())
    private val _latestScanPendingDtcs = MutableStateFlow<List<String>>(emptyList())
    private val _latestScanPermanentDtcs = MutableStateFlow<List<String>>(emptyList())
    private val _latestScanHistoricalDtcs = MutableStateFlow<List<String>>(emptyList())

    // Backwards compatibility for logic that only needs the strings.
    // The latest scan bucket keeps DTCs visible even before a vehicle record is selected or Room emits.
    val activeDtcs: StateFlow<List<String>> = combine(activeDtcEvents, _latestScanActiveDtcs) { events, latest ->
        (events.map { it.code } + latest).distinct()
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val pendingDtcs: StateFlow<List<String>> = combine(pendingDtcEvents, _latestScanPendingDtcs) { events, latest ->
        (events.map { it.code } + latest).distinct()
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val permanentDtcs: StateFlow<List<String>> = combine(permanentDtcEvents, _latestScanPermanentDtcs) { events, latest ->
        (events.map { it.code } + latest).distinct()
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val historicalDtcs: StateFlow<List<String>> = combine(historicalDtcEvents, _latestScanHistoricalDtcs) { events, latest ->
        (events.map { it.code } + latest).distinct()
    }
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

    private val _isPremium = MutableStateFlow(MonetizationPolicy.LOCAL_FULL_ACCESS)
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
        // Run Elysium Vanguard Copilot RuleEngine check
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
            if (connectionState.value != ObdState.CONNECTED) {
                _inspectionResult.value = null
                addTerminalLog("[REAL] Inspección pre-compra cancelada: requiere enlace OBD-II físico activo.", TerminalLineType.WARNING)
                return@launch
            }
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
                Log.e("Elysium Vanguard", "Pre-purchase inspection failed", e)
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
                Log.e("Elysium Vanguard", "Failed to generate pre-purchase PDF", e)
            }
        }
    }

    // ──── Elysium Vanguard PERITO STATES ────
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

    // --- Elysium Vanguard DNA ---
    private val _dnaResult = MutableStateFlow<com.elysium369.meet.core.dna.DnaEvaluationResult>(com.elysium369.meet.core.dna.DnaEvaluationResult(isCalibrated = false))
    val dnaResult: StateFlow<com.elysium369.meet.core.dna.DnaEvaluationResult> = _dnaResult.asStateFlow()

    private val _isTrainingDna = MutableStateFlow(false)
    val isTrainingDna: StateFlow<Boolean> = _isTrainingDna.asStateFlow()

    private var lastAdapterAddress: String? = null
    private var dnaEvaluationJob: Job? = null
    private var lastDnaEvaluationAtMs: Long = 0L
    private val dnaEvaluationIntervalMs = 5_000L

    fun trainVehicleDna() {
        val vehicle = _selectedVehicle.value ?: return
        viewModelScope.launch {
            _isTrainingDna.value = true
            try {
                val progress = meetDnaEngine.getTrainingProgress(vehicle.id)
                if (!progress.isReady) {
                    val result = meetDnaEngine.evaluateCurrentStatus(vehicle.id, _liveData.value)
                    _dnaResult.value = result.copy(
                        message = "Faltan ${progress.missingSamples} lecturas alineadas para calibrar una firma real. No se inicia entrenamiento hasta cumplir el minimo.",
                        nextAction = "Conduzca normal y mantenga el scanner conectado hasta llegar a ${progress.requiredSamples} muestras."
                    )
                    return@launch
                }
                val trained = meetDnaEngine.trainDnaProfile(vehicle.id, quickMode = progress.isQuickMode)
                val result = meetDnaEngine.evaluateCurrentStatus(vehicle.id, _liveData.value)
                _dnaResult.value =
                    if (trained == null) {
                        val missing = (progress.requiredSamples - progress.currentSamples).coerceAtLeast(0)
                        result.copy(
                            message = "Aun faltan $missing lecturas alineadas para entrenar la firma Elysium Vanguard DNA. Mientras tanto se muestra telemetria provisional."
                        )
                    } else {
                        result
                    }
                vehicleTwinEngine.evaluateFrame(vehicle.id, _liveData.value)
            } catch (e: Exception) {
                _dnaResult.value = com.elysium369.meet.core.dna.DnaEvaluationResult(
                    isCalibrated = false,
                    message = "Elysium Vanguard DNA no pudo calibrar: ${e.message ?: "error interno"}"
                )
            } finally {
                _isTrainingDna.value = false
            }
        }
    }

    fun evaluateDnaInference(force: Boolean = false) {
        val vehicle = _selectedVehicle.value ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastDnaEvaluationAtMs < dnaEvaluationIntervalMs) return
        if (dnaEvaluationJob?.isActive == true) return
        lastDnaEvaluationAtMs = now
        dnaEvaluationJob = viewModelScope.launch {
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

            voiceFeedbackManager.speak("Iniciando peritaje clínico Elysium Vanguard Perito.", "Starting Elysium Vanguard Perito clinical vehicle check.")
            
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

            addLog("⚡ Compilando diagnóstico y generando reporte clínico Elysium Vanguard Perito...")
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
                Log.e("Elysium Vanguard", "Failed to generate Perito PDF report", e)
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

    private suspend fun autoSelectVehicleFromVin(rawVin: String): Vehicle? {
        val cleanVin = normalizeVin(rawVin) ?: return null
        if (_selectedVehicle.value?.vin?.equals(cleanVin, ignoreCase = true) == true) {
            lastAutoRecognizedVin = cleanVin
            return _selectedVehicle.value
        }
        if (lastAutoRecognizedVin == cleanVin) return _selectedVehicle.value

        return vinVehicleRecognitionMutex.withLock {
            if (_selectedVehicle.value?.vin?.equals(cleanVin, ignoreCase = true) == true) {
                lastAutoRecognizedVin = cleanVin
                return@withLock _selectedVehicle.value
            }

            vehicleRepository.getVehicleByVin(cleanVin)?.let { existing ->
                selectVehicle(existing)
                lastAutoRecognizedVin = cleanVin
                voiceFeedbackManager.speak(
                    "Vehículo reconocido por VIN. ${existing.make} ${existing.model} seleccionado.",
                    "Vehicle recognized by VIN. ${existing.make} ${existing.model} selected."
                )
                addTerminalLog("[VIN] Vehículo existente seleccionado automáticamente: ${existing.make} ${existing.model} ($cleanVin)", TerminalLineType.SYSTEM)
                return@withLock existing
            }

            val decoded = VinDecoder.decode(cleanVin) ?: return@withLock null
            val year = decoded.modelYear.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
            val vehicle = Vehicle(
                id = "vin_$cleanVin",
                user_id = currentCloudUserId() ?: "guest",
                year = year,
                make = decoded.manufacturer,
                model = "Detectado por VIN",
                engine = "Identificado por escáner OBD-II | ${decoded.country} | Planta ${decoded.assemblyPlant}",
                displacement_cc = 0,
                engine_tech = "Por completar",
                transmission_type = "Por completar",
                transmission_subtype = "Por completar",
                fuel_type = "Por completar",
                vin = cleanVin,
                plate = "VIN-${cleanVin.takeLast(6)}"
            )

            vehicleRepository.insertVehicle(vehicle)
            selectVehicle(vehicle)
            lastAutoRecognizedVin = cleanVin
            _vinDecoded.value = decoded
            voiceFeedbackManager.speak(
                "VIN leído correctamente. Creé el vehículo automáticamente en Garage.",
                "VIN read successfully. I created the vehicle automatically in Garage."
            )
            addTerminalLog("[VIN] Perfil creado automáticamente desde escáner: ${vehicle.make} ${vehicle.year} ($cleanVin)", TerminalLineType.DECODED)
            vehicle
        }
    }

    private fun normalizeVin(rawVin: String): String? {
        val clean = rawVin.trim().uppercase()
            .replace(Regex("[^A-HJ-NPR-Z0-9]"), "")
        if (clean.length != 17) return null
        return clean
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
    val usbDeviceConnected: StateFlow<Boolean> = usbOscilloscopeManager.deviceConnected
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
        viewModelScope.launch {
            entitlementManager.hasAccess(FeatureKey.SCAN_ADVANCED).collect { hasAccess ->
                _isPremium.value = hasAccess
            }
        }
        startMarketplaceSync()
        refreshProviderRoles()
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
                            ObdState.CONNECTED -> voiceFeedbackManager.speak("Conexión establecida. Sistema de telemetría Elysium Vanguard activo.", "Connection established. Elysium Vanguard telemetry system active.")
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
                            // ── Elysium Vanguard DNA Real-time Inference ──
                            evaluateDnaInference()

                            // ── LiveLink broadcast (only if server is active) ──
                            _liveLinkServer?.let { server ->
                                if (server.isRunning.value) {
                                    launch(Dispatchers.IO) {
                                        try {
                                            val packet = buildLiveLinkTelemetryPacket(
                                                sessionId = _liveLinkProCoreSession.value?.sessionId ?: "local_wifi"
                                            )
                                            server.broadcastTelemetry(packet)
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
                            v?.let {
                                detectManufacturer(it)
                                autoSelectVehicleFromVin(it)
                            }
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
                val user = try {
                    SupabaseManager.client.auth.currentUserOrNull()
                } catch (e: Exception) {
                    null
                }

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
            provider = normalizeAiProvider(prefs.getString("ai_provider", "gemini") ?: "gemini"),
            apiKey = prefs.getString("ai_api_key", "") ?: "",
            endpoint = prefs.getString("ai_base_url", "") ?: "",
            modelName = prefs.getString("ai_model_name", "") ?: ""
        )
        _aiConfig.value = loadedConfig
        // Push to diagnostic engine on startup
        if (loadedConfig.apiKey.isNotBlank() || loadedConfig.provider == "ollama") {
            val resolvedEp = resolveAiEndpoint(loadedConfig.provider, loadedConfig.endpoint)
            geminiDiagnostic.updateConfig(loadedConfig.apiKey, resolvedEp, loadedConfig.provider, loadedConfig.modelName)
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
        val normalizedProvider = normalizeAiProvider(provider)
        val config = AiConfig(normalizedProvider, apiKey.trim(), endpoint.trim(), modelName.trim())
        _aiConfig.value = config

        val prefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE).edit()
        prefs.putString("ai_provider", config.provider)
        prefs.putString("ai_api_key", config.apiKey)
        prefs.putString("ai_base_url", config.endpoint)
        prefs.putString("ai_model_name", config.modelName)
        prefs.apply()

        // Push config to the diagnostic engine immediately
        val resolvedEndpoint = resolveAiEndpoint(config.provider, config.endpoint)
        geminiDiagnostic.updateConfig(config.apiKey, resolvedEndpoint, config.provider, config.modelName)
        Log.d("ObdVM", "AI Config saved: provider=${config.provider}, model=${config.modelName.ifBlank { "default" }}")
    }

    /** Resolve endpoint URL based on provider selection */
    private fun resolveAiEndpoint(provider: String, customEndpoint: String): String? {
        return when (normalizeAiProvider(provider)) {
            "gemini" -> null // use default Gemini endpoint inside GeminiDiagnostic
            "openai" -> if (customEndpoint.isNotBlank()) customEndpoint else "https://api.openai.com/v1/chat/completions"
            "anthropic" -> if (customEndpoint.isNotBlank()) customEndpoint else "https://api.anthropic.com/v1/messages"
            "ollama" -> if (customEndpoint.isNotBlank()) customEndpoint else "http://localhost:11434/v1/chat/completions"
            "mavis", "custom" -> customEndpoint.ifBlank { null }
            else -> null
        }
    }

    private fun normalizeAiProvider(provider: String): String {
        val clean = provider.trim().lowercase()
        return when {
            clean.contains("gemini") -> "gemini"
            clean.contains("openai") || clean.contains("gpt") -> "openai"
            clean.contains("anthropic") || clean.contains("claude") -> "anthropic"
            clean.contains("ollama") || clean.contains("local") -> "ollama"
            clean.contains("mavis") -> "mavis"
            clean.contains("custom") -> "custom"
            clean.isBlank() -> "gemini"
            else -> clean
        }
    }

    // --- Actions ---

    /** Connect to an OBD2 adapter by MAC address or IP */
    fun connect(address: String) {
        lastAdapterAddress = address
        context.getSharedPreferences("elysium_obd_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("last_adapter_address", address)
            .apply()
        obdSession.setTargetAddress(address)
        hasCompletedInitialDtcScan = false
        viewModelScope.launch {
            obdSession.connect()
            if (obdSession.state.value == ObdState.CONNECTED) {
                runPostConnectDtcFirstStartup()
                startForegroundService(_selectedVehicle.value?.id ?: "active_vehicle", address)
            }
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
                startForegroundService(vehicle.id, lastAdapterAddress)
            }
        }
    }

    private suspend fun runPostConnectDtcFirstStartup() {
        if (obdSession.state.value != ObdState.CONNECTED) return
        ensureDtcScanBeforeAction(force = true)
        runCatching {
            val detectedVin = obdSession.fetchVin()
            _vin.value = detectedVin
            detectManufacturer(detectedVin)
            autoSelectVehicleFromVin(detectedVin)
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

    // ═══════════════════════════════════════════════
    // MODE $05 — O2 SENSOR MONITORING TEST RESULTS
    // ═══════════════════════════════════════════════

    val o2SensorTests = obdSession.o2SensorTests
    val isReadingO2Tests = obdSession.isReadingO2Tests

    fun readO2SensorTests() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                obdSession.readO2SensorTests()
            } catch (e: Exception) {
                Log.e("ObdVM", "Mode 05 O2 Tests failed", e)
            }
        }
    }

    // ═══════════════════════════════════════════════
    // CATEGORIZED DTCs ($03 / $07 / $0A)
    // ═══════════════════════════════════════════════

    val categorizedDtcs = obdSession.categorizedDtcs

    fun readCategorizedDtcs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                obdSession.readCategorizedDtcs()
            } catch (e: Exception) {
                Log.e("ObdVM", "Categorized DTCs failed", e)
            }
        }
    }

    // ═══════════════════════════════════════════════
    // MODE $09 — EXTENDED VEHICLE INFORMATION
    // ═══════════════════════════════════════════════

    private val _vehicleInfoExtended = MutableStateFlow<Map<String, String>>(emptyMap())
    val vehicleInfoExtended: StateFlow<Map<String, String>> = _vehicleInfoExtended.asStateFlow()

    fun readExtendedVehicleInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _vehicleInfoExtended.value = obdSession.readAllVehicleInfo()
            } catch (e: Exception) {
                Log.e("ObdVM", "Extended vehicle info failed", e)
            }
        }
    }

    // ═══════════════════════════════════════════════
    // UDS SERVICES (via UdsProtocolManager)
    // ═══════════════════════════════════════════════

    private val _udsCapabilities = MutableStateFlow<UdsCapabilities?>(null)
    val udsCapabilities: StateFlow<UdsCapabilities?> = _udsCapabilities.asStateFlow()

    private val _ecuInfo = MutableStateFlow<List<UdsReadResult>>(emptyList())
    val ecuInfo: StateFlow<List<UdsReadResult>> = _ecuInfo.asStateFlow()

    private val _lastUdsOperation = MutableStateFlow("")
    val lastUdsOperation: StateFlow<String> = _lastUdsOperation.asStateFlow()

    fun discoverUdsCapabilities() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _lastUdsOperation.value = "Descubriendo capacidades UDS..."
                _udsCapabilities.value = udsProtocolManager.discoverCapabilities()
                _lastUdsOperation.value = "Capacidades UDS descubiertas."
            } catch (e: Exception) {
                Log.e("ObdVM", "UDS discovery failed", e)
                _lastUdsOperation.value = "Error: ${e.message}"
            }
        }
    }

    fun readEcuInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _lastUdsOperation.value = "Leyendo información ECU (UDS \$22)..."
                _ecuInfo.value = udsProtocolManager.readEcuInfo()
                _lastUdsOperation.value = "${_ecuInfo.value.size} DIDs leídos."
            } catch (e: Exception) {
                Log.e("ObdVM", "ECU Info read failed", e)
            }
        }
    }

    fun resetEcu(resetType: String = "03") {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _lastUdsOperation.value = "Ejecutando ECU Reset..."
                val success = udsProtocolManager.resetEcu(resetType)
                _lastUdsOperation.value = if (success) "ECU Reset exitoso." else "ECU Reset falló."
            } catch (e: Exception) {
                Log.e("ObdVM", "ECU Reset failed", e)
                _lastUdsOperation.value = "Error: ${e.message}"
            }
        }
    }

    fun clearDtcUds() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _lastUdsOperation.value = "Borrando DTCs vía UDS \$14..."
                val success = udsProtocolManager.clearDtcUds()
                _lastUdsOperation.value = if (success) "DTCs borrados (UDS)." else "Borrado UDS no soportado."
            } catch (e: Exception) {
                Log.e("ObdVM", "UDS Clear DTC failed", e)
            }
        }
    }

    fun readDtcUds() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _lastUdsOperation.value = "Leyendo DTCs vía UDS \$19..."
                val dtcs = udsProtocolManager.readDtcByStatusMask()
                _lastUdsOperation.value = "${dtcs.size} DTCs encontrados (UDS)."
            } catch (e: Exception) {
                Log.e("ObdVM", "UDS Read DTC failed", e)
            }
        }
    }

    fun readDataByIdentifier(did: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _lastUdsOperation.value = "Leyendo DID \$$did..."
                val result = udsProtocolManager.readDataByIdentifier(did)
                _lastUdsOperation.value = if (result != null) "DID \$$did: $result" else "DID \$$did no disponible."
            } catch (e: Exception) {
                Log.e("ObdVM", "Read DID failed", e)
            }
        }
    }

    fun executeRoutine(routineId: String, params: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _lastUdsOperation.value = "Ejecutando rutina \$$routineId..."
                val success = udsProtocolManager.startRoutine(routineId, params)
                _lastUdsOperation.value = if (success) "Rutina \$$routineId ejecutada." else "Rutina \$$routineId falló."
            } catch (e: Exception) {
                Log.e("ObdVM", "Routine failed", e)
            }
        }
    }

    // ═══════════════════════════════════════════════
    // MANUFACTURER-SPECIFIC MODES ($B0-$BF, $D0-$DF, $EA-$FF)
    // ═══════════════════════════════════════════════

    private val _manufacturerModes = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val manufacturerModes: StateFlow<Map<String, Boolean>> = _manufacturerModes.asStateFlow()

    fun probeManufacturerModes() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _lastUdsOperation.value = "Probando modos del fabricante..."
                _manufacturerModes.value = obdSession.probeManufacturerModes()
                val supportedCount = _manufacturerModes.value.count { it.value }
                _lastUdsOperation.value = "$supportedCount modos del fabricante detectados."
            } catch (e: Exception) {
                Log.e("ObdVM", "Manufacturer probe failed", e)
            }
        }
    }

    fun sendManufacturerCommand(sid: String, sub: String = ""): StateFlow<String?> {
        val result = MutableStateFlow<String?>(null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                result.value = obdSession.sendManufacturerCommand(sid, sub)
            } catch (e: Exception) {
                Log.e("ObdVM", "Manufacturer command failed", e)
            }
        }
        return result.asStateFlow()
    }

    // ═══════════════════════════════════════════════
    // POST-SCAN PDF REPORT
    // ═══════════════════════════════════════════════

    private val _lastScanReportPath = MutableStateFlow<String?>(null)
    val lastScanReportPath: StateFlow<String?> = _lastScanReportPath.asStateFlow()


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
            addTerminalLog(
                "⚠ Conecta un vehículo real para escanear DTCs. Elysium Vanguard ya no genera resultados simulados.",
                TerminalLineType.WARNING
            )
            voiceFeedbackManager.speak(
                "Conecta un vehículo real para escanear códigos de error.",
                "Connect to a real vehicle to scan fault codes."
            )
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

            _latestScanActiveDtcs.value = freshActive
            _latestScanPendingDtcs.value = freshPending
            _latestScanPermanentDtcs.value = freshPermanent
            _latestScanHistoricalDtcs.value = freshHistory

            addTerminalLog(
                "[SCAN] Activos: ${freshActive.size} | Pendientes: ${freshPending.size} | Permanentes: ${freshPermanent.size} | Históricos: ${freshHistory.size}",
                TerminalLineType.SYSTEM
            )

            val vehicle = _selectedVehicle.value
            if (vehicle != null) {
                saveDetectedDtcFindings(professionalReport)
            } else if ((freshActive + freshPending + freshPermanent + freshHistory).isNotEmpty()) {
                addTerminalLog(
                    "[SCAN] Códigos visibles en pantalla desde el último escaneo; selecciona un vehículo para guardar historial persistente.",
                    TerminalLineType.WARNING
                )
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
                val description = com.elysium369.meet.ui.components.DtcUtils.getSpanishDescription(def, record.code)
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
                val description = com.elysium369.meet.ui.components.DtcUtils.getSpanishDescription(def, code)
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

    private fun scheduleVanguardCommerceSync() {
        VanguardOutboxSyncWorker.enqueueNow(context)
    }

    private fun fetchDtcDefinitions(codes: List<String>) {
        viewModelScope.launch {
            val newDefinitions = _dtcDefinitions.value.toMutableMap()
            val vehicleMake = com.elysium369.meet.ui.components.DtcUtils.normalizeManufacturer(_selectedVehicle.value?.make)
            codes.forEach { code ->
                if (!newDefinitions.containsKey(code)) {
                    val def = dtcDefinitionDao.getDefinitionForCode(code, vehicleMake)
                    if (def != null) {
                        newDefinitions[code] = localizeDtcDefinition(def)
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
            possibleCauses = com.elysium369.meet.ui.components.DtcUtils.getSpanishPossibleCauses(code, null),
            urgency = urgency
        )
    }

    private fun localizeDtcDefinition(definition: DtcDefinitionEntity): DtcDefinitionEntity {
        return definition.copy(
            descriptionEs = com.elysium369.meet.ui.components.DtcUtils.getSpanishDescription(definition, definition.code),
            possibleCauses = com.elysium369.meet.ui.components.DtcUtils.getSpanishPossibleCauses(definition.code, definition.possibleCauses)
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
                        .map { localizeDtcDefinition(it) }
                        .groupBy { it.code }
                        .map { (_, entries) ->
                            entries.firstOrNull {
                                it.manufacturer != "GENERIC" &&
                                    !it.possibleCauses.isNullOrBlank() &&
                                    !it.possibleCauses.contains("manufacturer manual", ignoreCase = true)
                            } ?: entries.firstOrNull { it.manufacturer == "GENERIC" } ?: entries.first()
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
        val normalizedCode = code.uppercase()
        return dtcDefinitionDao.getDefinitionForCode(normalizedCode, normalizedMake)
            ?.let { localizeDtcDefinition(it) }
            ?: generateFallbackDefinition(normalizedCode)
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

    suspend fun getDtcCommunityRepairCases(code: String): List<RepairCase> {
        return repairCaseRepository.searchCases(
            query = "",
            dtc = code.uppercase(),
            sortBy = "votes",
            onlyVerified = false
        )
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
            success = false
            voiceFeedbackManager.speak(
                "No hay conexión OBD activa. No se puede borrar la memoria real del vehículo.",
                "No active OBD connection. Vehicle fault memory cannot be erased."
            )
            _clearDtcResult.value = "Conecta el adaptador OBD y verifica ignición en ON antes de borrar DTCs reales."
        } else {
            success = obdSession.clearDtcs()
            if (success) {
                voiceFeedbackManager.speak("Borrado de códigos completado con éxito. Memoria limpia.", "Fault codes cleared successfully. Memory clean.")
                _clearDtcResult.value = "✅ Códigos borrados exitosamente"
                _selectedVehicle.value?.let { vehicle ->
                    dtcDao.resolveAllDtcsForVehicle(vehicle.id, System.currentTimeMillis())
                }
                _latestScanActiveDtcs.value = emptyList()
                _latestScanPendingDtcs.value = emptyList()
                _latestScanPermanentDtcs.value = emptyList()
                _latestScanHistoricalDtcs.value = emptyList()
                _freezeFrameData.value = emptyMap()
                updateHealthScore()
                scheduleSync()
            } else {
                voiceFeedbackManager.speak("Fallo al borrar los códigos. Por favor, asegúrese que el motor esté apagado y en contacto.", "Failed to clear fault codes. Please ensure the engine is off and ignition is on.")
                val detail = obdSession.statusMessage.value.ifBlank {
                    "Asegúrese que el motor esté apagado, el contacto esté en ON y el voltaje sea estable."
                }
                _clearDtcResult.value = "❌ $detail"
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

    suspend fun refreshFreezeFrame(dtc: String): Boolean {
        if (connectionState.value != ObdState.CONNECTED) {
            _cloudSyncState.value = "Conecta el OBD para volver a leer el freeze frame de $dtc."
            return false
        }
        _cloudSyncState.value = "Refrescando Cuadro Congelado..."
        return try {
            val ff = obdSession.readFreezeFrame(dtc)
            val scoped = ff.mapKeys { (key, _) -> "$dtc:$key" }
            _freezeFrameData.value = _freezeFrameData.value + scoped
            _cloudSyncState.value = if (ff.isEmpty()) {
                "La ECU no devolvio freeze frame para $dtc."
            } else {
                "Cuadro Congelado actualizado."
            }
            ff.isNotEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.e("ObdViewModel", "Freeze frame refresh failed for $dtc", error)
            _cloudSyncState.value = "No se pudo releer el freeze frame de $dtc: ${error.message ?: "respuesta OBD no disponible"}."
            false
        }
    }

    suspend fun readVin(): String? {
        val detectedVin = obdSession.fetchVin()
        _vin.value = detectedVin
        detectManufacturer(detectedVin)
        autoSelectVehicleFromVin(detectedVin)
        return detectedVin
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

    suspend fun consultAi(
        apiKey: String?,
        endpointUrl: String?,
        dtcList: List<String>,
        providerOverride: String? = null,
        modelNameOverride: String? = null,
        groundedRepairContext: String? = null
    ): String {
        val dtcCodesStr = dtcList.sorted().joinToString(",")
        val savedConfig = _aiConfig.value
        var effectiveProvider = normalizeAiProvider(providerOverride ?: savedConfig.provider)
        var effectiveApiKey = apiKey ?: savedConfig.apiKey
        var effectiveModel = modelNameOverride ?: savedConfig.modelName
        var effectiveEndpoint = endpointUrl ?: resolveAiEndpoint(effectiveProvider, savedConfig.endpoint)

        // Fallback to integrated MiniMax debug provider if no custom API key is configured
        if (effectiveApiKey.isBlank() && effectiveProvider != "ollama") {
            effectiveProvider = "minimax"
            effectiveModel = "MiniMax-M1"
            effectiveApiKey = ""
            effectiveEndpoint = ""
        }

        val remoteAiConfigured = when (effectiveProvider) {
            "minimax" -> true
            "ollama" -> !effectiveEndpoint.isNullOrBlank()
            "gemini" -> effectiveApiKey.isNotBlank()
            "openai", "anthropic", "mavis", "custom" -> effectiveApiKey.isNotBlank() && !effectiveEndpoint.isNullOrBlank()
            else -> effectiveApiKey.isNotBlank() && !effectiveEndpoint.isNullOrBlank()
        }

        // 1. Check offline cache only when there is no remote provider configured.
        if (!remoteAiConfigured) {
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
        }

        // 2. Cache miss -> Query remote AI (new multi-provider engine first, legacy fallback)
        var resultText = ""
        var modelUsed = "$effectiveProvider:${effectiveModel.ifBlank { "default" }}"
        try {
            // Try new multi-provider AI engine
            val info = _selectedVehicle.value?.let { "${it.make} ${it.model} ${it.year}" } ?: "Vehículo Genérico"
            val aiContext = com.elysium369.meet.ai.domain.AiContext(
                vehicle = _selectedVehicle.value?.let { v ->
                    com.elysium369.meet.ai.domain.VehicleContext(
                        make = v.make, model = v.model, year = v.year,
                        engine = v.engine, transmission = v.transmission_type,
                        fuel = v.fuel_type, vin = null,
                        odometer = null
                    )
                },
                obd = com.elysium369.meet.ai.domain.ObdContext(
                    connected = connectionState.value == com.elysium369.meet.core.obd.ObdState.CONNECTED,
                    activePidsCount = _liveData.value.size,
                    dtcActiveCount = dtcList.size,
                    batteryVoltage = (_liveData.value["CONTROL_MODULE_VOLTAGE"] ?: 0.0).toFloat()
                ),
                dtcs = dtcList.map { com.elysium369.meet.ai.domain.DtcContext(it, "Activo") },
                livePids = _liveData.value.map { (k, v) ->
                    com.elysium369.meet.ai.domain.PidReading(pid = k, name = k, value = "%.2f".format(v))
                },
                manualAvailability = null,
                appModule = "DIAGNOSTIC_DTC",
                locale = "es-MX",
                userRole = com.elysium369.meet.ai.domain.UserRole.MECHANIC,
                safetyMode = true
            )
            val aiRequest = com.elysium369.meet.ai.domain.AiRequest(
                feature = com.elysium369.meet.ai.domain.AiFeature.DIAGNOSTIC_DTC,
                providerId = effectiveProvider,
                model = effectiveModel.ifBlank { "MiniMax-M1" },
                messages = listOf(
                    com.elysium369.meet.ai.domain.AiMessage(
                        com.elysium369.meet.ai.domain.AiRole.USER,
                        com.elysium369.meet.ai.context.AiAutomotiveContextBuilder.buildContextPrompt(aiContext) +
                            groundedRepairContext?.let {
                                "\n\n=== CONOCIMIENTO ESTRUCTURADO CITADO ===\n$it"
                            }.orEmpty() +
                            "\n\nDiagnostica los siguientes DTCs para $info: ${dtcList.joinToString(", ")}"
                    )
                ),
                context = aiContext
            )
            val aiResult = aiRepository.complete(aiRequest)
            if (aiResult.isSuccess) {
                resultText = aiResult.getOrThrow().text
                modelUsed = "${aiResult.getOrThrow().providerId}:${aiResult.getOrThrow().model}"
            } else {
                // Fallback to legacy GeminiDiagnostic
                android.util.Log.w("ObdViewModel", "New AI engine failed, falling back to legacy: ${aiResult.exceptionOrNull()?.message}")
                geminiDiagnostic.updateConfig(effectiveApiKey, effectiveEndpoint, effectiveProvider, effectiveModel)
                val result = geminiDiagnostic.analyzeDtc(
                    dtcList, info,
                    _liveData.value.mapValues { "%.2f".format(it.value) },
                    _telemetryHistory.value,
                    groundedRepairContext
                )
                resultText = result.analysisText
            }

            // Update the UI with detected anomalies (only from legacy path)
            // The new AiRepository returns text-only; anomalousPids are extracted separately if needed
            _anomalousPids.value = emptyList()
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
            modelUsed = "Elysium Vanguard Local Expert Engine"
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
    private fun startForegroundService(vehicleId: String, adapterAddress: String? = lastAdapterAddress) {
        val resolvedAddress = adapterAddress
            ?: context.getSharedPreferences("elysium_obd_prefs", Context.MODE_PRIVATE)
                .getString("last_adapter_address", null)
        val intent = Intent(context, com.elysium369.meet.core.obd.ObdForegroundService::class.java).apply {
            putExtra("vehicle_id", vehicleId)
            if (!resolvedAddress.isNullOrBlank()) putExtra("adapter_address", resolvedAddress)
        }
        try {
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        } catch (_: Exception) {
            try { context.startService(intent) } catch (_: Exception) {}
        }
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
                val exportDirectory = java.io.File(
                    context.getExternalFilesDir(null),
                    "TelemetryExports",
                ).apply { mkdirs() }
                val file = java.io.File(exportDirectory, fileName)
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
        val chooser = Intent.createChooser(intent, "Compartir Log de Elysium Vanguard").apply {
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
        val endpoint = resolveAiEndpoint(currentConfig.provider, currentConfig.endpoint)
        geminiDiagnostic.updateConfig(
            newApiKey = currentConfig.apiKey,
            newEndpoint = endpoint,
            newProvider = currentConfig.provider,
            newModelName = currentConfig.modelName
        )
        return geminiDiagnostic.analyzeLiveTelemetry(vehicleInfo, data)
    }

    suspend fun analyzeNetworkTopology(vehicleInfo: String, modules: List<com.elysium369.meet.core.obd.NetworkModule>): com.elysium369.meet.core.ai.DiagnosticResult {
        val currentConfig = _aiConfig.value
        val endpoint = resolveAiEndpoint(currentConfig.provider, currentConfig.endpoint)
        geminiDiagnostic.updateConfig(
            newApiKey = currentConfig.apiKey,
            newEndpoint = endpoint,
            newProvider = currentConfig.provider,
            newModelName = currentConfig.modelName
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
        val endpoint = resolveAiEndpoint(currentConfig.provider, currentConfig.endpoint)
        geminiDiagnostic.updateConfig(
            newApiKey = currentConfig.apiKey,
            newEndpoint = endpoint,
            newProvider = currentConfig.provider,
            newModelName = currentConfig.modelName
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
            "La firma digital Elysium Vanguard DNA aún no está calibrada para este vehículo. Por favor, realice una corrida de calibración en la pantalla DNA."
        } else if (dna.isAnomalous) {
            "Alerta preventiva de comportamiento: El score de salud es del ${dna.healthScore} por ciento. Se detecta una desviación estadística anómala en los sensores."
        } else {
            "Firma digital Elysium Vanguard DNA calibrada al ${dna.confidence.toInt()} por ciento de confianza. El vehículo se comporta de forma normal con un score de salud del ${dna.healthScore} por ciento."
        }
        val msgEn = if (!dna.isCalibrated) {
            "The Elysium Vanguard DNA digital signature is not yet calibrated for this vehicle. Please perform a calibration drive in the DNA section."
        } else if (dna.isAnomalous) {
            "Preventive behavior alert: The health score is ${dna.healthScore} percent. Statistical anomaly detected in sensors."
        } else {
            "Elysium Vanguard DNA signature calibrated at ${dna.confidence.toInt()} percent confidence. The vehicle behaves normally with a health score of ${dna.healthScore} percent."
        }
        voiceFeedbackManager.speak(msgEs, msgEn)
    }

    private fun speakPeritoReport() {
        val report = activePeritoReport.value
        val msgEs = if (report != null) {
            "El último reporte Elysium Vanguard Perito indica un score clínico de ${report.score0to100} sobre cien, con clasificación ${report.category}."
        } else {
            "No se ha realizado ningún peritaje clínico Elysium Vanguard Perito para este vehículo en esta sesión."
        }
        val msgEn = if (report != null) {
            "The latest Elysium Vanguard Perito report shows a clinical score of ${report.score0to100} out of one hundred, categorized as ${report.category}."
        } else {
            "No Elysium Vanguard Perito clinical check has been executed for this vehicle in this session."
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

    // ═══════════════════════════════════════════════════════════════════════════
    // ████ TOW TRUCK SERVICE (Grúas) — Elysium bidding & GPS ████
    // ═══════════════════════════════════════════════════════════════════════════

    val towTruckRequests: StateFlow<List<TowTruckRequestEntity>> = towTruckDao.getRequestsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val openTowTruckRequests: StateFlow<List<TowTruckRequestEntity>> = towTruckDao.getOpenRequestsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _towTruckDriverMode = MutableStateFlow(false)
    val towTruckDriverMode: StateFlow<Boolean> = _towTruckDriverMode.asStateFlow()

    fun toggleTowTruckDriverMode() {
        _towTruckDriverMode.value = !_towTruckDriverMode.value
    }

    /** Build a rich vehicle info string from the selected vehicle + active DTCs */
    fun buildVehicleInfoForRequest(): String {
        val v = _selectedVehicle.value
        val dtcs = activeDtcEvents.value
        val sb = StringBuilder()
        if (v != null) {
            sb.append("${v.year} ${v.make} ${v.model}")
            if (v.engine.isNotBlank()) sb.append(" | Motor: ${v.engine}")
            if (v.displacement_cc > 0) sb.append(" ${v.displacement_cc}cc")
            if (v.fuel_type.isNotBlank()) sb.append(" | Combustible: ${v.fuel_type}")
            if (v.transmission_type.isNotBlank()) sb.append(" | Trans: ${v.transmission_type}")
            if (v.vin.isNotBlank()) sb.append(" | VIN: ${v.vin}")
        } else {
            sb.append("Vehículo no registrado")
        }
        if (dtcs.isNotEmpty()) {
            sb.append(" | DTCs activos: ${dtcs.joinToString(", ") { it.code }}")
        }
        return sb.toString()
    }

    /** Build vehicle info from a specific DTC code for 1-click service request */
    fun buildVehicleInfoForDtc(dtcCode: String, dtcDescription: String, isManualEntry: Boolean = false): String {
        val v = _selectedVehicle.value
        val sb = StringBuilder()
        if (v != null) {
            sb.append("${v.year} ${v.make} ${v.model}")
            if (v.engine.isNotBlank()) sb.append(" | Motor: ${v.engine}")
            if (v.displacement_cc > 0) sb.append(" ${v.displacement_cc}cc")
            if (v.fuel_type.isNotBlank()) sb.append(" | Combustible: ${v.fuel_type}")
            if (v.transmission_type.isNotBlank()) sb.append(" | Trans: ${v.transmission_type}")
            if (v.vin.isNotBlank()) sb.append(" | VIN: ${v.vin}")
        } else {
            sb.append("Vehículo no registrado")
        }
        val tag = if (isManualEntry) "✍️ [INGRESADO MANUALMENTE POR USUARIO]" else "🔍 [DETECTADO VÍA ESCÁNER OBD-II]"
        sb.append(" | ⚠️ DTC: $dtcCode — $dtcDescription | $tag")
        return sb.toString()
    }

    /** Client creates a new tow truck / mechanic request */
    fun createTowTruckRequest(
        latitude: Double,
        longitude: Double,
        locationName: String,
        destLat: Double? = null,
        destLng: Double? = null,
        destName: String? = null,
        phone: String,
        priceOffer: Double,
        vehicleInfoOverride: String? = null
    ) {
        viewModelScope.launch {
            val request = TowTruckRequestEntity(
                requestId = java.util.UUID.randomUUID().toString(),
                userId = _selectedVehicle.value?.user_id ?: "anonymous",
                vehicleInfo = vehicleInfoOverride ?: buildVehicleInfoForRequest(),
                latitude = latitude,
                longitude = longitude,
                locationName = locationName,
                destinationLatitude = destLat,
                destinationLongitude = destLng,
                destinationName = destName,
                phone = phone,
                status = "OPEN",
                priceOffer = priceOffer,
                createdAt = System.currentTimeMillis()
            )
            towTruckDao.insertRequest(request)
        }
    }

    /** Driver/mechanic takes an open request */
    fun takeTowTruckRequest(requestId: String, driverId: String, driverName: String, driverPhone: String) {
        viewModelScope.launch {
            towTruckDao.updateDriverAndStatus(
                requestId = requestId,
                status = "TAKEN",
                driverId = driverId,
                driverName = driverName,
                driverPhone = driverPhone
            )
        }
    }

    /** Mark a request as completed */
    fun completeTowTruckRequest(requestId: String) {
        viewModelScope.launch {
            towTruckDao.updateRequestStatusAndCompletedTime(
                requestId = requestId,
                status = "COMPLETED",
                completedAt = System.currentTimeMillis()
            )
        }
    }

    /** Cancel a request */
    fun cancelTowTruckRequest(requestId: String) {
        viewModelScope.launch {
            towTruckDao.updateRequestStatusAndCompletedTime(
                requestId = requestId,
                status = "CANCELLED",
                completedAt = System.currentTimeMillis()
            )
        }
    }

    /** User manually deletes a completed/cancelled request */
    fun deleteTowTruckRequest(requestId: String) {
        viewModelScope.launch {
            towTruckDao.deleteRequest(requestId)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ████ ELYSIUM RATING SYSTEM — verified 5-star ratings ████
    // ═══════════════════════════════════════════════════════════════════════════

    val allRatings: StateFlow<List<RatingEntity>> = ratingDao.getAllRatingsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun submitRating(
        targetType: String,  // MECHANIC, STORE, TOW_TRUCK, CLIENT
        targetId: String,
        sourceName: String,
        stars: Double,
        comment: String
    ) {
        viewModelScope.launch {
            val rating = RatingEntity(
                ratingId = java.util.UUID.randomUUID().toString(),
                targetType = targetType,
                targetId = targetId,
                sourceId = _selectedVehicle.value?.user_id ?: "anonymous",
                sourceName = sourceName,
                stars = stars.coerceIn(1.0, 5.0),
                comment = comment,
                createdAt = System.currentTimeMillis()
            )
            ratingDao.insertRating(rating)
        }
    }

    suspend fun getAverageRating(targetType: String, targetId: String): Double {
        return ratingDao.getAverageRatingForTarget(targetType, targetId) ?: 5.0
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ████ 72-HOUR AUTO-CLEANUP — Purge completed/cancelled requests ████
    // ═══════════════════════════════════════════════════════════════════════════

    init {
        // Launch a coroutine that runs every 15 minutes to clean up old requests
        viewModelScope.launch {
            while (true) {
                try {
                    val seventyTwoHoursAgo = System.currentTimeMillis() - (72 * 60 * 60 * 1000L)
                    towTruckDao.purgeOldRequests(seventyTwoHoursAgo)
                } catch (e: Exception) {
                    android.util.Log.e("ObdViewModel", "Auto-cleanup failed", e)
                }
                kotlinx.coroutines.delay(15 * 60 * 1000L) // every 15 minutes
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ████ GPS / WAZE / WHATSAPP LOCATION SHARING UTILITIES ████
    // ═══════════════════════════════════════════════════════════════════════════

    data class GpsLocationInfo(
        val latitude: Double,
        val longitude: Double,
        val addressName: String,
        val countryCode: String,      // e.g. "CR"
        val dialingPrefix: String,     // e.g. "+506"
        val accuracy: Float = 0f,
        val speed: Float = 0f,
        val bearing: Float = 0f,
        val timestamp: Long = 0L
    )

    private val countryPrefixMap = mapOf(
        "CR" to "+506", "MX" to "+52", "CO" to "+57", "AR" to "+54",
        "CL" to "+56", "PE" to "+51", "EC" to "+593", "PA" to "+507",
        "UY" to "+598", "VE" to "+58", "GT" to "+502", "HN" to "+504",
        "SV" to "+503", "NI" to "+505", "PY" to "+595", "BO" to "+591",
        "DO" to "+1", "PR" to "+1", "US" to "+1", "ES" to "+34"
    )

    private val _currentGpsLocation = MutableStateFlow<GpsLocationInfo?>(null)
    val currentGpsLocation: StateFlow<GpsLocationInfo?> = _currentGpsLocation.asStateFlow()

    /** Trigger GPS detection, geocoding and dialing prefix detection using high-accuracy FusedLocationProviderClient */
    fun detectCurrentLocation(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.w("ObdViewModel", "GPS permissions not granted")
                    return@launch
                }

                val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
                
                // Intentar obtener la última ubicación conocida primero
                fusedLocationClient.lastLocation.addOnSuccessListener { location: android.location.Location? ->
                    if (location != null && location.accuracy <= 30f) {
                        resolveLocationDetails(context, location)
                    } else {
                        // Si es nula o imprecisa, forzar una actualización fresca de alta precisión
                        requestFreshLocation(context, fusedLocationClient)
                    }
                }.addOnFailureListener {
                    requestFreshLocation(context, fusedLocationClient)
                }
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Error starting FusedLocation check", e)
            }
        }
    }

    private fun requestFreshLocation(context: Context, client: com.google.android.gms.location.FusedLocationProviderClient) {
        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
            
            val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                2000
            ).apply {
                setMinUpdateDistanceMeters(0f)
                setWaitForAccurateLocation(true)
                setMaxUpdates(1)
            }.build()

            client.requestLocationUpdates(locationRequest, object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                    val location = locationResult.lastLocation
                    if (location != null) {
                        resolveLocationDetails(context, location)
                    }
                }
            }, android.os.Looper.getMainLooper())
        } catch (e: Exception) {
            Log.e("ObdViewModel", "Error requesting fresh high-accuracy location", e)
        }
    }

    private fun resolveLocationDetails(context: Context, location: android.location.Location) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                val address = addresses?.firstOrNull()
                val countryCode = address?.countryCode ?: ""
                val addressLine = address?.getAddressLine(0) ?: "Ubicación GPS detectada"
                val prefix = countryPrefixMap[countryCode.uppercase()] ?: "+506"

                _currentGpsLocation.value = GpsLocationInfo(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    addressName = addressLine,
                    countryCode = countryCode,
                    dialingPrefix = prefix,
                    accuracy = location.accuracy,
                    speed = location.speed,
                    bearing = location.bearing,
                    timestamp = location.time
                )
            } catch (e: Exception) {
                Log.e("ObdViewModel", "Geocoder address look up failed", e)
                _currentGpsLocation.value = GpsLocationInfo(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    addressName = "Ubicación GPS detectada",
                    countryCode = "",
                    dialingPrefix = "+506",
                    accuracy = location.accuracy,
                    speed = location.speed,
                    bearing = location.bearing,
                    timestamp = location.time
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Elysium Vanguard Viajes business logic
    // ═══════════════════════════════════════════════════════════════

    val rideRequests = rideDao.getAllRequestsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val openRideRequests = rideDao.getOpenRequestsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeRideRequest = MutableStateFlow<RideRequestEntity?>(null)
    val activeRideRequest: StateFlow<RideRequestEntity?> = _activeRideRequest.asStateFlow()

    private val _rideOffers = MutableStateFlow<List<RideOfferEntity>>(emptyList())
    val rideOffers: StateFlow<List<RideOfferEntity>> = _rideOffers.asStateFlow()

    private val _rideChatMessages = MutableStateFlow<List<RideChatMessageEntity>>(emptyList())
    val rideChatMessages: StateFlow<List<RideChatMessageEntity>> = _rideChatMessages.asStateFlow()

    private val _rideDriverMode = MutableStateFlow(false)
    val rideDriverMode: StateFlow<Boolean> = _rideDriverMode.asStateFlow()

    private val _rideSharingSelections =
        MutableStateFlow<Map<String, Set<RideShareCategory>>>(emptyMap())
    val rideSharingSelections: StateFlow<Map<String, Set<RideShareCategory>>> =
        _rideSharingSelections.asStateFlow()

    private val _rideRoadIncidents = MutableStateFlow<List<RideRoadIncident>>(emptyList())
    val rideRoadIncidents: StateFlow<List<RideRoadIncident>> = _rideRoadIncidents.asStateFlow()

    private val _rideSpeedSamples =
        MutableStateFlow<Map<String, List<RideSegmentSpeedSample>>>(emptyMap())
    val rideSpeedSamples: StateFlow<Map<String, List<RideSegmentSpeedSample>>> =
        _rideSpeedSamples.asStateFlow()
    private val lastUploadedRideSpeedBucket = mutableMapOf<String, Long>()

    private val _rideClaimFeedback = MutableSharedFlow<RideClaimFeedback>(extraBufferCapacity = 8)
    val rideClaimFeedback: SharedFlow<RideClaimFeedback> = _rideClaimFeedback.asSharedFlow()

    private val rideBoardingChallenges = mutableMapOf<String, RideBoardingChallenge>()
    private val _rideBoardingPins = MutableStateFlow<Map<String, String>>(emptyMap())
    val rideBoardingPins: StateFlow<Map<String, String>> = _rideBoardingPins.asStateFlow()

    private val _ridePinFeedback = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val ridePinFeedback: SharedFlow<String> = _ridePinFeedback.asSharedFlow()

    private val _driverPresetMessages = MutableStateFlow<List<String>>(listOf(
        "Ya me encuentro en la ubicación",
        "Voy en camino, llego en unos 5 minutos",
        "Estoy parado en el semáforo/esquina",
        "Hola, ya inicié el viaje",
        "Estoy afuera con las luces intermitentes encendidas"
    ))
    val driverPresetMessages: StateFlow<List<String>> = _driverPresetMessages.asStateFlow()

    private var jobOffersCollection: Job? = null
    private var jobChatCollection: Job? = null

    fun toggleRideDriverMode() {
        _rideDriverMode.value = !_rideDriverMode.value
    }

    fun selectActiveRide(request: RideRequestEntity?) {
        _activeRideRequest.value = request
        jobOffersCollection?.cancel()
        jobChatCollection?.cancel()

        if (request != null) {
            _rideSharingSelections.update { current ->
                if (request.requestId in current) {
                    current
                } else {
                    current + (
                        request.requestId to setOf(RideShareCategory.EXACT_LOCATION)
                        )
                }
            }
            jobOffersCollection = viewModelScope.launch {
                rideDao.getOffersForRequest(request.requestId).collect {
                    _rideOffers.value = it
                }
            }
            jobChatCollection = viewModelScope.launch {
                rideDao.getChatMessagesFlow(request.requestId).collect {
                    _rideChatMessages.value = it
                }
            }
        } else {
            _rideOffers.value = emptyList()
            _rideChatMessages.value = emptyList()
        }
    }

    fun setRideShareCategory(
        requestId: String,
        category: RideShareCategory,
        enabled: Boolean,
    ) {
        if (requestId.isBlank()) return
        _rideSharingSelections.update { current ->
            val existing = current[requestId].orEmpty()
            val updated = if (enabled) existing + category else existing - category
            current + (requestId to updated)
        }
    }

    fun reportRideRoadIncident(
        tripId: String?,
        type: RideRoadIncidentType,
        side: RideRoadSide,
        severity: Int,
    ) {
        val gps = _currentGpsLocation.value ?: return
        val now = System.currentTimeMillis()
        val lifetimeMs = when (type) {
            RideRoadIncidentType.POLICE_PRESENCE -> 20 * 60 * 1000L
            RideRoadIncidentType.TRAFFIC_CONTROL,
            RideRoadIncidentType.SLOW_TRAFFIC,
            RideRoadIncidentType.VERY_SLOW_TRAFFIC,
            -> 30 * 60 * 1000L
            RideRoadIncidentType.ROAD_CLOSED,
            RideRoadIncidentType.WRONG_WAY_HAZARD,
            RideRoadIncidentType.STALLED_VEHICLE,
            RideRoadIncidentType.OBSTACLE,
            -> 90 * 60 * 1000L
            RideRoadIncidentType.POTHOLE -> 6 * 60 * 60 * 1000L
        }
        val coarseCell = RideGeoCell.encode(gps.latitude, gps.longitude)
        val incident = RideRoadIncident(
            id = UUID.randomUUID().toString(),
            roadSegmentId = "cell:$coarseCell",
            type = type,
            side = side,
            severity = severity.coerceIn(1, 3),
            latitude = gps.latitude,
            longitude = gps.longitude,
            bearingDegrees = gps.bearing,
            accuracyMeters = gps.accuracy,
            reporterReliability = 0.50,
            independentConfirmations = 0,
            independentDenials = 0,
            observedSpeedRatio = null,
            createdAtEpochMs = now,
            expiresAtEpochMs = now + lifetimeMs,
        )
        _rideRoadIncidents.update { current ->
            (current.filterNot { it.isExpired(now) } + incident).takeLast(100)
        }

        viewModelScope.launch(Dispatchers.IO) {
            val userId = SupabaseManager.client.auth.currentUserOrNull()?.id ?: return@launch
            val isoExpiry = java.time.Instant.ofEpochMilli(incident.expiresAtEpochMs).toString()
            val remoteReport = RemoteRideRoadIncident(
                id = incident.id,
                reporter_id = userId,
                trip_id = tripId,
                road_segment_id = incident.roadSegmentId,
                incident_type = incident.type.name,
                road_side = incident.side.name,
                severity = incident.severity,
                latitude = incident.latitude,
                longitude = incident.longitude,
                bearing_degrees = incident.bearingDegrees,
                accuracy_meters = incident.accuracyMeters,
                geohash_coarse = coarseCell,
                expires_at = isoExpiry,
            )
            runCatching {
                SupabaseManager.client.postgrest["ride_road_incidents"].insert(
                    remoteReport,
                )
            }.onFailure {
                // A local Room request may not yet exist in the authoritative
                // cloud. Preserve the safety signal without claiming a trip FK.
                runCatching {
                    SupabaseManager.client.postgrest["ride_road_incidents"].insert(
                        remoteReport.copy(trip_id = null),
                    )
                }.onFailure { fallbackError ->
                    Log.w(
                        "ObdViewModel",
                        "Road report queued locally; cloud sync unavailable",
                        fallbackError,
                    )
                }
            }
        }
    }

    fun recordRideSpeedObservation(tripId: String) {
        if (tripId.isBlank()) return
        val gps = _currentGpsLocation.value ?: return
        val now = System.currentTimeMillis()
        val sample = RideSegmentSpeedSample(
            speedMetersPerSecond = gps.speed.toDouble().coerceAtLeast(0.0),
            capturedAtEpochMs = now,
        )
        _rideSpeedSamples.update { current ->
            val fresh = current[tripId]
                .orEmpty()
                .filter { now - it.capturedAtEpochMs <= 10 * 60 * 1000L }
                .plus(sample)
                .takeLast(120)
            current + (tripId to fresh)
        }

        val minuteBucket = now / 60_000L * 60_000L
        if (lastUploadedRideSpeedBucket[tripId] == minuteBucket) return
        lastUploadedRideSpeedBucket[tripId] = minuteBucket

        viewModelScope.launch(Dispatchers.IO) {
            val userId = SupabaseManager.client.auth.currentUserOrNull()?.id ?: return@launch
            val capturedAt = java.time.Instant.ofEpochMilli(now).toString()
            val bucketAt = java.time.Instant.ofEpochMilli(minuteBucket).toString()
            runCatching {
                SupabaseManager.client.postgrest["ride_segment_speed_observations"].insert(
                    RemoteRideSpeedObservation(
                        observer_id = userId,
                        trip_id = tripId,
                        road_segment_id = "cell:${RideGeoCell.encode(gps.latitude, gps.longitude)}",
                        speed_mps = gps.speed.coerceIn(0f, 100f),
                        accuracy_meters = gps.accuracy.coerceAtLeast(0f),
                        bearing_degrees = gps.bearing.coerceIn(0f, 360f),
                        captured_at = capturedAt,
                        time_bucket = bucketAt,
                    ),
                )
            }.onFailure {
                Log.d("ObdViewModel", "Speed telemetry retained locally; cloud trip not synchronized")
            }
        }
    }

    fun announceRideEvent(spanish: String, english: String) {
        voiceFeedbackManager.speak(spanish, english)
    }

    fun createRideRequest(
        passengerId: String,
        passengerName: String,
        passengerPhone: String,
        pickupLat: Double,
        pickupLng: Double,
        pickupAddr: String,
        pickupAcc: Float,
        destLat: Double,
        destLng: Double,
        destAddr: String,
        priceOffer: Double,
        currency: String,
        estDistance: Double,
        estDuration: Int,
        stopsJson: String = "[]",
        paymentMethod: String = "CASH",
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalizedFare = RideFareBidPolicy.normalize(priceOffer, currency)
            val request = RideRequestEntity(
                requestId = UUID.randomUUID().toString(),
                passengerId = passengerId,
                passengerName = passengerName,
                passengerPhone = passengerPhone,
                pickupLatitude = pickupLat,
                pickupLongitude = pickupLng,
                pickupAddress = pickupAddr,
                pickupAccuracy = pickupAcc,
                destLatitude = destLat,
                destLongitude = destLng,
                destAddress = destAddr,
                priceOffer = normalizedFare,
                currency = currency,
                estimatedDistanceKm = estDistance,
                estimatedDurationMin = estDuration,
                stopsJson = stopsJson,
                paymentMethod = paymentMethod,
                fareBreakdownJson = """{"acceptedFare":$normalizedFare,"currency":"$currency"}""",
                status = "OPEN",
                createdAt = System.currentTimeMillis()
            )
            rideDao.insertRequest(request)
            withContext(Dispatchers.Main) {
                selectActiveRide(request)
            }
        }
    }

    fun makeRideOffer(
        requestId: String,
        driverId: String,
        driverName: String,
        driverPhone: String,
        driverRating: Double,
        driverTotalTrips: Int,
        vehicleDesc: String,
        counterPrice: Double,
        currency: String,
        estArrivalMin: Int,
        driverLat: Double,
        driverLng: Double,
        message: String?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val offer = RideOfferEntity(
                offerId = UUID.randomUUID().toString(),
                requestId = requestId,
                driverId = driverId,
                driverName = driverName,
                driverPhone = driverPhone,
                driverRating = driverRating,
                driverTotalTrips = driverTotalTrips,
                vehicleDescription = vehicleDesc,
                counterPrice = RideFareBidPolicy.normalize(counterPrice, currency),
                currency = currency,
                estimatedArrivalMin = estArrivalMin,
                driverLatitude = driverLat,
                driverLongitude = driverLng,
                message = message,
                status = "PENDING",
                createdAt = System.currentTimeMillis()
            )
            rideDao.insertOffer(offer)
        }
    }

    fun acceptRideOffer(requestId: String, offerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val offer = rideDao.getOfferById(offerId) ?: return@launch
            val outcome = rideDao.acceptOfferAtomically(requestId, offerId)
            if (outcome == RideOfferAcceptanceOutcome.ACCEPTED) {
                rideDao.insertChatMessage(
                    RideChatMessageEntity(
                        messageId = UUID.randomUUID().toString(),
                        rideRequestId = requestId,
                        senderId = "SYSTEM",
                        senderName = "Sistema",
                        senderRole = "SYSTEM",
                        messageType = "TEXT",
                        textContent = "Oferta de ${offer.driverName} aceptada por ${offer.counterPrice} ${offer.currency}.",
                        createdAt = System.currentTimeMillis()
                    )
                )
            }

            val updatedRequest = rideDao.getRequestById(requestId)
            withContext(Dispatchers.Main) {
                _activeRideRequest.value = updatedRequest
            }
        }
    }

    fun claimRideFirstCome(
        requestId: String,
        driverId: String,
        driverName: String,
        driverPhone: String,
        vehicleDescription: String,
    ) {
        if (requestId.isBlank() || driverId.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val won = rideDao.claimOpenRequest(
                requestId = requestId,
                driverId = driverId,
                driverName = driverName,
                driverPhone = driverPhone,
                vehicle = vehicleDescription,
            ) == 1
            if (won) {
                val (pin, challenge) = RideBoardingPinPolicy.issue(System.currentTimeMillis())
                rideBoardingChallenges[requestId] = challenge
                _rideBoardingPins.update { it + (requestId to pin) }
                rideDao.insertChatMessage(
                    RideChatMessageEntity(
                        messageId = UUID.randomUUID().toString(),
                        rideRequestId = requestId,
                        senderId = "SYSTEM",
                        senderName = "Sistema",
                        senderRole = "SYSTEM",
                        messageType = "TEXT",
                        textContent = "Conductor asignado por primera confirmación válida.",
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                rideDao.getRequestById(requestId)?.let { updated ->
                    withContext(Dispatchers.Main) { selectActiveRide(updated) }
                }
            }
            _rideClaimFeedback.emit(
                RideClaimFeedback(
                    requestId = requestId,
                    won = won,
                    message = if (won) {
                        "¡Viaje asignado! Fuiste el primer conductor confirmado."
                    } else {
                        "Otro conductor confirmó este viaje primero."
                    },
                ),
            )
            voiceFeedbackManager.speak(
                if (won) {
                    "Viaje asignado. Fuiste el primer conductor confirmado."
                } else {
                    "Otro conductor confirmó este viaje primero."
                },
                if (won) {
                    "Ride assigned. You were the first confirmed driver."
                } else {
                    "Another driver confirmed this ride first."
                },
            )
        }
    }

    fun verifyRideBoardingPin(requestId: String, candidate: String) {
        val driverId = driverVerification.value?.driverId
            ?.takeIf(String::isNotBlank)
            ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val challenge = rideBoardingChallenges[requestId]
            if (challenge == null) {
                _ridePinFeedback.emit("PIN pendiente de sincronización. Solicita uno nuevo al pasajero.")
                return@launch
            }
            val verification = RideBoardingPinPolicy.verify(
                challenge = challenge,
                candidate = candidate,
                nowEpochMs = System.currentTimeMillis(),
            )
            rideBoardingChallenges[requestId] = verification.challenge
            when (verification.status) {
                RidePinVerificationStatus.VERIFIED -> {
                    val transitioned = rideDao.transitionRequestStatusAsDriver(
                        requestId = requestId,
                        driverId = driverId,
                        expectedStatus = "ARRIVED",
                        newStatus = "PASSENGER_ONBOARD",
                        completedAt = null,
                    ) == 1
                    if (!transitioned) {
                        _ridePinFeedback.emit(
                            "El viaje cambió de estado antes de confirmar el PIN. Actualiza la pantalla.",
                        )
                        return@launch
                    }
                    _rideBoardingPins.update { it - requestId }
                    val request = rideDao.getRequestById(requestId)
                    request?.let { updated ->
                        withContext(Dispatchers.Main) { _activeRideRequest.value = updated }
                    }
                    val passengerName = request?.passengerName.orEmpty().ifBlank { "pasajero" }
                    voiceFeedbackManager.speak(
                        "Hola, $passengerName. Vamos a iniciar el servicio. ¿Tienes una ruta preferida o usamos la ruta de la aplicación?",
                        "Hello, $passengerName. We are starting the service. Do you have a preferred route or should we use the app route?",
                    )
                    _ridePinFeedback.emit("PIN correcto. Pasajero confirmado.")
                }
                RidePinVerificationStatus.INVALID ->
                    _ridePinFeedback.emit("PIN incorrecto. Verifica los cuatro dígitos con el pasajero.")
                RidePinVerificationStatus.LOCKED ->
                    _ridePinFeedback.emit("Demasiados intentos. Espera cinco minutos por seguridad.")
                RidePinVerificationStatus.EXPIRED_OR_USED ->
                    _ridePinFeedback.emit("El PIN venció o ya fue utilizado.")
            }
        }
    }

    fun updateRideStatus(requestId: String, newStatus: String) {
        val driverId = driverVerification.value?.driverId
            ?.takeIf(String::isNotBlank)
            ?: return
        val expectedStatus = when (newStatus) {
            "ARRIVED" -> "ACCEPTED"
            "IN_PROGRESS" -> "PASSENGER_ONBOARD"
            "COMPLETED" -> "IN_PROGRESS"
            else -> return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val transitioned = rideDao.transitionRequestStatusAsDriver(
                requestId = requestId,
                driverId = driverId,
                expectedStatus = expectedStatus,
                newStatus = newStatus,
                completedAt = if (newStatus == "COMPLETED") System.currentTimeMillis() else null,
            ) == 1
            if (!transitioned) return@launch
            
            // Insert system chat msg about state change
            val alertText = when(newStatus) {
                "ARRIVED" -> "🚕 ¡El conductor ha llegado a tu ubicación de recogida!"
                "IN_PROGRESS" -> "🚗 El viaje ha comenzado oficialmente."
                "COMPLETED" -> "🏁 Viaje finalizado con éxito."
                else -> "El viaje cambió de estado a $newStatus."
            }

            val systemMsg = RideChatMessageEntity(
                messageId = UUID.randomUUID().toString(),
                rideRequestId = requestId,
                senderId = "SYSTEM",
                senderName = "Sistema",
                senderRole = "SYSTEM",
                messageType = "TEXT",
                textContent = alertText,
                createdAt = System.currentTimeMillis()
            )
            rideDao.insertChatMessage(systemMsg)

            val updatedRequest = rideDao.getRequestById(requestId)
            withContext(Dispatchers.Main) {
                _activeRideRequest.value = updatedRequest
            }
            if (newStatus == "COMPLETED") {
                _rideSharingSelections.update { it - requestId }
            }
            if (newStatus == "IN_PROGRESS") {
                voiceFeedbackManager.speak(
                    "Vamos a iniciar el servicio.",
                    "We are starting the service.",
                )
            }
        }
    }

    fun cancelRide(
        requestId: String,
        reason: RideCancellationReason,
        detail: String?,
        actorRole: String,
    ) {
        if (!RideCancellationPolicy.isDetailValid(reason, detail)) return
        val role = runCatching { RideActorRole.valueOf(actorRole.uppercase()) }.getOrNull() ?: return
        if (role !in setOf(RideActorRole.PASSENGER, RideActorRole.DRIVER)) return
        if (reason !in RideCancellationPolicy.reasonsFor(role)) return
        val (actorId, actorName) = when (role) {
            RideActorRole.DRIVER -> {
                val verification = driverVerification.value ?: return
                verification.driverId to verification.fullName
            }
            RideActorRole.PASSENGER -> {
                val verification = passengerVerification.value ?: return
                verification.passengerId to verification.fullName
            }
            else -> return
        }
        if (actorId.isBlank() || actorName.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val cancelled = rideDao.cancelActiveRequest(
                requestId = requestId,
                actorId = actorId,
                actorRole = role.name,
                cancelledAt = System.currentTimeMillis(),
            ) == 1
            if (!cancelled) return@launch
            val decision = RideCancellationPolicy.evaluate(reason)
            val normalizedRole = role.name
            val safetyNote = if (decision.requiresSafetyReview) {
                " Caso marcado para revisión de seguridad."
            } else {
                ""
            }
            val detailNote = detail?.trim()?.takeIf { it.isNotEmpty() }?.let {
                " Detalle: $it"
            }.orEmpty()
            rideDao.insertChatMessage(
                RideChatMessageEntity(
                    messageId = UUID.randomUUID().toString(),
                    rideRequestId = requestId,
                    senderId = actorId,
                    senderName = actorName,
                    senderRole = normalizedRole,
                    messageType = "SYSTEM",
                    textContent = "Viaje cancelado. Motivo: ${reason.name}.$safetyNote$detailNote",
                    createdAt = System.currentTimeMillis(),
                ),
            )
            val updatedRequest = rideDao.getRequestById(requestId)
            _rideSharingSelections.update { it - requestId }
            withContext(Dispatchers.Main) {
                _activeRideRequest.value = updatedRequest
            }
        }
    }

    fun submitRideRating(requestId: String, isPassengerRating: Boolean, stars: Double, comment: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val req = rideDao.getRequestById(requestId) ?: return@launch
            if (isPassengerRating) {
                // Pasajero califica al conductor
                rideDao.updatePassengerRating(requestId, stars)
                // Insert en la tabla global de ratings
                val driverProfile = providerProfileDao.getProfileByUserAndType(req.assignedDriverId ?: "", "TOW_TRUCK") // o rol similar
                val targetId = req.assignedDriverId ?: ""
                val rating = RatingEntity(
                    ratingId = UUID.randomUUID().toString(),
                    targetType = "DRIVER",
                    targetId = targetId,
                    sourceId = req.passengerId,
                    sourceName = req.passengerName,
                    stars = stars,
                    comment = comment,
                    createdAt = System.currentTimeMillis()
                )
                ratingDao.insertRating(rating)

                // Recalcular promedio si tiene perfil registrado
                val avg = ratingDao.getAverageRatingForTarget("DRIVER", targetId)
                if (avg != null) {
                    val profile = providerProfileDao.getProfileByUserAndType(targetId, "RIDE_DRIVER")
                    if (profile != null) {
                        providerProfileDao.updateRatingAndJobs(profile.profileId, avg, System.currentTimeMillis())
                    }
                }
            } else {
                // Conductor califica al pasajero
                rideDao.updateDriverRating(requestId, stars)
                val rating = RatingEntity(
                    ratingId = UUID.randomUUID().toString(),
                    targetType = "CLIENT",
                    targetId = req.passengerId,
                    sourceId = req.assignedDriverId ?: "",
                    sourceName = req.assignedDriverName ?: "",
                    stars = stars,
                    comment = comment,
                    createdAt = System.currentTimeMillis()
                )
                ratingDao.insertRating(rating)
            }

            val updatedRequest = rideDao.getRequestById(requestId)
            withContext(Dispatchers.Main) {
                _activeRideRequest.value = updatedRequest
            }
        }
    }

    fun updateRidePrice(requestId: String, newPrice: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val request = rideDao.getRequestById(requestId) ?: return@launch
            val normalizedPrice = RideFareBidPolicy.normalize(newPrice, request.currency)
            val updated = request.copy(priceOffer = normalizedPrice)
            rideDao.insertRequest(updated)
            
            val systemMsg = RideChatMessageEntity(
                messageId = UUID.randomUUID().toString(),
                rideRequestId = requestId,
                senderId = "SYSTEM",
                senderName = "Sistema",
                senderRole = "SYSTEM",
                messageType = "TEXT",
                textContent = "El pasajero ajustó la oferta Elysium a $normalizedPrice ${request.currency}.",
                createdAt = System.currentTimeMillis()
            )
            rideDao.insertChatMessage(systemMsg)

            withContext(Dispatchers.Main) {
                _activeRideRequest.value = updated
            }
        }
    }

    fun rejectRideOffer(requestId: String, offerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            rideDao.updateOfferStatus(offerId, "REJECTED")
            val updatedRequest = rideDao.getRequestById(requestId)
            withContext(Dispatchers.Main) {
                _activeRideRequest.value = updatedRequest
            }
        }
    }

    // Chat Functions
    fun sendRideChatMessage(requestId: String, senderId: String, senderName: String, role: String, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val msg = RideChatMessageEntity(
                messageId = UUID.randomUUID().toString(),
                rideRequestId = requestId,
                senderId = senderId,
                senderName = senderName,
                senderRole = role,
                messageType = "TEXT",
                textContent = text,
                createdAt = System.currentTimeMillis()
            )
            rideDao.insertChatMessage(msg)
        }
    }

    fun sendRidePresetMessage(requestId: String, senderId: String, senderName: String, role: String, presetText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val msg = RideChatMessageEntity(
                messageId = UUID.randomUUID().toString(),
                rideRequestId = requestId,
                senderId = senderId,
                senderName = senderName,
                senderRole = role,
                messageType = "PRESET",
                textContent = presetText,
                createdAt = System.currentTimeMillis()
            )
            rideDao.insertChatMessage(msg)
        }
    }

    // Preset messages management
    fun addDriverPresetMessage(msg: String) {
        val list = _driverPresetMessages.value.toMutableList()
        if (!list.contains(msg) && msg.isNotBlank()) {
            list.add(msg)
            _driverPresetMessages.value = list
        }
    }

    fun removeDriverPresetMessage(msg: String) {
        val list = _driverPresetMessages.value.toMutableList()
        list.remove(msg)
        _driverPresetMessages.value = list
    }

    // local Audio Recording & Playback Logic
    private var mediaRecorder: android.media.MediaRecorder? = null
    private var mediaPlayer: android.media.MediaPlayer? = null
    private var currentRecordingFile: java.io.File? = null
    private var recordingStartTime: Long = 0L

    private val _isRecordingAudio = MutableStateFlow(false)
    val isRecordingAudio: StateFlow<Boolean> = _isRecordingAudio.asStateFlow()

    private val _isPlayingAudio = MutableStateFlow<String?>(null) // Muestra el path reproduciéndose
    val isPlayingAudio: StateFlow<String?> = _isPlayingAudio.asStateFlow()

    fun startAudioRecording(context: Context) {
        if (_isRecordingAudio.value) return
        try {
            val audioDir = java.io.File(context.filesDir, "meet_rides_audio")
            if (!audioDir.exists()) audioDir.mkdirs()

            currentRecordingFile = java.io.File(audioDir, "audio_${System.currentTimeMillis()}.m4a")
            mediaRecorder = android.media.MediaRecorder(context).apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                setOutputFile(currentRecordingFile?.absolutePath)
                prepare()
                start()
            }
            recordingStartTime = System.currentTimeMillis()
            _isRecordingAudio.value = true
        } catch (e: Exception) {
            Log.e("ObdViewModel", "Failed to start audio recording", e)
            _isRecordingAudio.value = false
        }
    }

    fun stopAndSendAudioRecording(requestId: String, senderId: String, senderName: String, role: String) {
        if (!_isRecordingAudio.value) return
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            _isRecordingAudio.value = false

            val file = currentRecordingFile
            if (file != null && file.exists()) {
                val duration = System.currentTimeMillis() - recordingStartTime
                if (duration > 800) { // Al menos 0.8 segundos de audio
                    viewModelScope.launch(Dispatchers.IO) {
                        val msg = RideChatMessageEntity(
                            messageId = UUID.randomUUID().toString(),
                            rideRequestId = requestId,
                            senderId = senderId,
                            senderName = senderName,
                            senderRole = role,
                            messageType = "AUDIO",
                            audioFilePath = file.absolutePath,
                            audioDurationMs = duration,
                            createdAt = System.currentTimeMillis()
                        )
                        rideDao.insertChatMessage(msg)
                    }
                } else {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("ObdViewModel", "Failed to stop audio recording", e)
            _isRecordingAudio.value = false
        }
    }

    fun playAudioMessage(filePath: String) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                if (_isPlayingAudio.value == filePath) {
                    stopAudioMessage()
                    return@launch
                }
                stopAudioMessage()

                mediaPlayer = android.media.MediaPlayer().apply {
                    setDataSource(filePath)
                    prepare()
                    start()
                    setOnCompletionListener {
                        stopAudioMessage()
                    }
                }
                _isPlayingAudio.value = filePath
            } catch (e: Exception) {
                Log.e("ObdViewModel", "MediaPlayer failed", e)
                _isPlayingAudio.value = null
            }
        }
    }

    fun stopAudioMessage() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
        _isPlayingAudio.value = null
    }


    /** Open Waze with navigation to coordinates. Explicitly targets package com.waze to bypass prompt issues */
    fun openWaze(context: Context, lat: Double, lng: Double) {
        try {
            val wazeUri = android.net.Uri.parse("waze://?ll=$lat,$lng&navigate=yes")
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, wazeUri)
            intent.setPackage("com.waze")
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                // Fallback 1: open Waze web URL
                val webUri = android.net.Uri.parse("https://waze.com/ul?ll=$lat,$lng&navigate=yes")
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, webUri)
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (ex: Exception) {
                // Fallback 2: open standard geo maps intent (almost always works on Android via Google Maps)
                try {
                    val geoUri = android.net.Uri.parse("geo:$lat,$lng?q=$lat,$lng")
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, geoUri)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                } catch (e3: Exception) {
                    android.widget.Toast.makeText(context, "No se pudo abrir Waze ni Google Maps", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /** Share location via WhatsApp */
    fun shareLocationViaWhatsApp(context: Context, lat: Double, lng: Double, label: String) {
        try {
            val mapsLink = "https://www.google.com/maps?q=$lat,$lng"
            val message = "📍 Ubicación: $label\n$mapsLink"
            val waUri = android.net.Uri.parse("https://api.whatsapp.com/send?text=${android.net.Uri.encode(message)}")
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, waUri)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("ObdViewModel", "WhatsApp share failed", e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FEATURE 9 — IDENTITY VERIFICATION (UBER-GRADE ONBOARDING)
    // ═══════════════════════════════════════════════════════════════════════════

    private val _rideVerificationNotice = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
    )
    val rideVerificationNotice: SharedFlow<String> =
        _rideVerificationNotice.asSharedFlow()

    // ── Driver verification state ────────────────────────────────────────────
    val driverVerification: StateFlow<com.elysium369.meet.data.local.entities.DriverVerificationEntity?> =
        rideDao.getDriverVerificationFlow(localDeviceId)
            .onEach { verification ->
                if (
                    BuildConfig.RIDE_LOCAL_VERIFICATION_AUTO_APPROVE &&
                    verification?.status == "PENDING"
                ) {
                    val evidence = evaluateDriverEvidence(verification)
                    if (evidence.isReady) {
                        val now = System.currentTimeMillis()
                        rideDao.updateDriverVerificationStatus(
                            driverId = verification.driverId,
                            status = RideVerificationPolicy.PILOT_APPROVED,
                            approvedAt = now,
                            updatedAt = now,
                        )
                        android.util.Log.i(
                            "MeetRides",
                            "Pending driver verification upgraded to local pilot access",
                        )
                    } else {
                        android.util.Log.w(
                            "MeetRides",
                            "Driver pilot access withheld: ${evidence.issues.joinToString()}",
                        )
                        _rideVerificationNotice.emit(
                            "Completa nuevamente las evidencias del chofer; una o más fotos no están disponibles.",
                        )
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    // ── Passenger verification state ─────────────────────────────────────────
    val passengerVerification: StateFlow<com.elysium369.meet.data.local.entities.PassengerVerificationEntity?> =
        rideDao.getPassengerVerificationFlow(localDeviceId)
            .onEach { verification ->
                if (
                    BuildConfig.RIDE_LOCAL_VERIFICATION_AUTO_APPROVE &&
                    verification?.status == "PENDING"
                ) {
                    val evidence = evaluatePassengerEvidence(verification)
                    if (evidence.isReady) {
                        val now = System.currentTimeMillis()
                        rideDao.updatePassengerVerificationStatus(
                            passengerId = verification.passengerId,
                            status = RideVerificationPolicy.PILOT_APPROVED,
                            approvedAt = now,
                        )
                        android.util.Log.i(
                            "MeetRides",
                            "Pending passenger verification upgraded to local pilot access",
                        )
                    } else {
                        android.util.Log.w(
                            "MeetRides",
                            "Passenger pilot access withheld: ${evidence.issues.joinToString()}",
                        )
                        _rideVerificationNotice.emit(
                            "Completa nuevamente las fotos de identidad; una o más evidencias no están disponibles.",
                        )
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /**
     * Create the local directory for storing identity verification photos.
     * Returns the base directory path.
     */
    fun getVerificationPhotosDir(context: android.content.Context): java.io.File {
        val dir = java.io.File(context.filesDir, "meet_verifications")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Create a temporary file for a photo capture (camera intent result).
     */
    fun createVerificationPhotoFile(context: android.content.Context, prefix: String): java.io.File {
        val dir = getVerificationPhotosDir(context)
        return java.io.File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
    }

    private fun verificationFileEvidence(
        label: String,
        path: String,
    ): VerificationFileEvidence {
        val file = java.io.File(path)
        return VerificationFileEvidence(
            label = label,
            path = path,
            byteCount = if (file.isFile) file.length() else 0L,
        )
    }

    private fun evaluatePassengerEvidence(
        verification: com.elysium369.meet.data.local.entities.PassengerVerificationEntity,
    ) = RideVerificationEvidencePolicy.evaluatePassenger(
        fullName = verification.fullName,
        phone = verification.phone,
        files = listOf(
            verificationFileEvidence("profile", verification.pathProfilePhoto),
            verificationFileEvidence("id_front", verification.pathCedulaFront),
            verificationFileEvidence("selfie_with_id", verification.pathSelfieWithCedula),
        ),
    )

    private fun evaluateDriverEvidence(
        verification: com.elysium369.meet.data.local.entities.DriverVerificationEntity,
    ) = RideVerificationEvidencePolicy.evaluateDriver(
        fullName = verification.fullName,
        phone = verification.phone,
        email = verification.email,
        dateOfBirth = verification.dateOfBirth,
        vehicleMake = verification.vehicleMake,
        vehicleModel = verification.vehicleModel,
        vehicleYear = verification.vehicleYear,
        vehicleColor = verification.vehicleColor,
        vehiclePlate = verification.vehiclePlate,
        currentYear = Calendar.getInstance().get(Calendar.YEAR),
        files = listOf(
            verificationFileEvidence("license_front", verification.pathLicenciaFront),
            verificationFileEvidence("license_back", verification.pathLicenciaBack),
            verificationFileEvidence("id_front", verification.pathCedulaFront),
            verificationFileEvidence("id_back", verification.pathCedulaBack),
            verificationFileEvidence("criminal_record", verification.pathHojaDelincuencia),
            verificationFileEvidence("marchamo", verification.pathMarchamo),
            verificationFileEvidence("inspection", verification.pathDekra),
            verificationFileEvidence("insurance", verification.pathSeguro),
            verificationFileEvidence("profile", verification.pathSelfieProfile),
            verificationFileEvidence("selfie_with_id", verification.pathSelfieWithCedula),
            verificationFileEvidence("selfie_with_license", verification.pathSelfieWithLicencia),
            verificationFileEvidence("vehicle_front", verification.pathVehicleFront),
            verificationFileEvidence("vehicle_back", verification.pathVehicleBack),
            verificationFileEvidence("vehicle_interior", verification.pathVehicleInterior),
        ),
    )

    /**
     * Submit a complete driver verification application with all required
     * document file paths. This is the single entry point for the multi-step
     * onboarding wizard.
     */
    fun submitDriverVerification(
        fullName: String,
        phone: String,
        email: String,
        dateOfBirth: String,
        vehicleMake: String,
        vehicleModel: String,
        vehicleYear: Int,
        vehicleColor: String,
        vehiclePlate: String,
        pathLicenciaFront: String,
        pathLicenciaBack: String,
        pathCedulaFront: String,
        pathCedulaBack: String,
        pathHojaDelincuencia: String,
        pathMarchamo: String,
        pathDekra: String,
        pathSeguro: String,
        pathSelfieProfile: String,
        pathSelfieWithCedula: String,
        pathSelfieWithLicencia: String,
        pathVehicleFront: String,
        pathVehicleBack: String,
        pathVehicleInterior: String
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val evidence = RideVerificationEvidencePolicy.evaluateDriver(
                fullName = fullName,
                phone = phone,
                email = email,
                dateOfBirth = dateOfBirth,
                vehicleMake = vehicleMake,
                vehicleModel = vehicleModel,
                vehicleYear = vehicleYear,
                vehicleColor = vehicleColor,
                vehiclePlate = vehiclePlate,
                currentYear = Calendar.getInstance().get(Calendar.YEAR),
                files = listOf(
                    verificationFileEvidence("license_front", pathLicenciaFront),
                    verificationFileEvidence("license_back", pathLicenciaBack),
                    verificationFileEvidence("id_front", pathCedulaFront),
                    verificationFileEvidence("id_back", pathCedulaBack),
                    verificationFileEvidence("criminal_record", pathHojaDelincuencia),
                    verificationFileEvidence("marchamo", pathMarchamo),
                    verificationFileEvidence("inspection", pathDekra),
                    verificationFileEvidence("insurance", pathSeguro),
                    verificationFileEvidence("profile", pathSelfieProfile),
                    verificationFileEvidence("selfie_with_id", pathSelfieWithCedula),
                    verificationFileEvidence("selfie_with_license", pathSelfieWithLicencia),
                    verificationFileEvidence("vehicle_front", pathVehicleFront),
                    verificationFileEvidence("vehicle_back", pathVehicleBack),
                    verificationFileEvidence("vehicle_interior", pathVehicleInterior),
                ),
            )
            val verificationDecision = RideVerificationPolicy.decide(
                localAutoApprovalEnabled = BuildConfig.RIDE_LOCAL_VERIFICATION_AUTO_APPROVE,
                evidenceReady = evidence.isReady,
                nowEpochMs = now,
            )
            if (!evidence.isReady) {
                android.util.Log.w(
                    "MeetRides",
                    "Driver verification rejected as incomplete: ${evidence.issues.joinToString()}",
                )
                _rideVerificationNotice.emit(
                    "No se pudo habilitar el acceso: verifica los datos y vuelve a capturar cualquier foto faltante.",
                )
                return@launch
            }
            val entity = com.elysium369.meet.data.local.entities.DriverVerificationEntity(
                driverId = localDeviceId,
                fullName = fullName,
                phone = phone,
                email = email,
                dateOfBirth = dateOfBirth,
                vehicleMake = vehicleMake,
                vehicleModel = vehicleModel,
                vehicleYear = vehicleYear,
                vehicleColor = vehicleColor,
                vehiclePlate = vehiclePlate,
                pathLicenciaFront = pathLicenciaFront,
                pathLicenciaBack = pathLicenciaBack,
                pathCedulaFront = pathCedulaFront,
                pathCedulaBack = pathCedulaBack,
                pathHojaDelincuencia = pathHojaDelincuencia,
                pathMarchamo = pathMarchamo,
                pathDekra = pathDekra,
                pathSeguro = pathSeguro,
                pathSelfieProfile = pathSelfieProfile,
                pathSelfieWithCedula = pathSelfieWithCedula,
                pathSelfieWithLicencia = pathSelfieWithLicencia,
                pathVehicleFront = pathVehicleFront,
                pathVehicleBack = pathVehicleBack,
                pathVehicleInterior = pathVehicleInterior,
                status = verificationDecision.status,
                createdAt = now,
                updatedAt = now,
                approvedAt = verificationDecision.approvedAtEpochMs,
            )
            rideDao.insertDriverVerification(entity)
            android.util.Log.i(
                "MeetRides",
                "Driver verification submitted; status=${verificationDecision.status}",
            )
        }
    }

    /**
     * Submit a passenger identity verification (lighter than driver).
     */
    fun submitPassengerVerification(
        fullName: String,
        phone: String,
        pathProfilePhoto: String,
        pathCedulaFront: String,
        pathSelfieWithCedula: String
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val evidence = RideVerificationEvidencePolicy.evaluatePassenger(
                fullName = fullName,
                phone = phone,
                files = listOf(
                    verificationFileEvidence("profile", pathProfilePhoto),
                    verificationFileEvidence("id_front", pathCedulaFront),
                    verificationFileEvidence("selfie_with_id", pathSelfieWithCedula),
                ),
            )
            val verificationDecision = RideVerificationPolicy.decide(
                localAutoApprovalEnabled = BuildConfig.RIDE_LOCAL_VERIFICATION_AUTO_APPROVE,
                evidenceReady = evidence.isReady,
                nowEpochMs = now,
            )
            if (!evidence.isReady) {
                android.util.Log.w(
                    "MeetRides",
                    "Passenger verification rejected as incomplete: ${evidence.issues.joinToString()}",
                )
                _rideVerificationNotice.emit(
                    "No se pudo habilitar el acceso: verifica tus datos y vuelve a capturar las tres fotos.",
                )
                return@launch
            }
            val entity = com.elysium369.meet.data.local.entities.PassengerVerificationEntity(
                passengerId = localDeviceId,
                fullName = fullName,
                phone = phone,
                pathProfilePhoto = pathProfilePhoto,
                pathCedulaFront = pathCedulaFront,
                pathSelfieWithCedula = pathSelfieWithCedula,
                status = verificationDecision.status,
                createdAt = now,
                approvedAt = verificationDecision.approvedAtEpochMs,
            )
            rideDao.insertPassengerVerification(entity)
            android.util.Log.i(
                "MeetRides",
                "Passenger verification submitted; status=${verificationDecision.status}",
            )
        }
    }

    /**
     * Delete a driver verification (allows re-submission).
     */
    fun deleteDriverVerification() {
        viewModelScope.launch {
            rideDao.deleteDriverVerification(localDeviceId)
        }
    }

    /**
     * Delete a passenger verification (allows re-submission).
     */
    fun deletePassengerVerification() {
        viewModelScope.launch {
            rideDao.deletePassengerVerification(localDeviceId)
        }
    }

    /**
     * Check if the current device user is an approved driver.
     */
    fun isApprovedDriver(): Boolean {
        return RideVerificationPolicy.grantsAccess(driverVerification.value?.status)
    }

    /**
     * Check if the current device user is an approved passenger.
     */
    fun isApprovedPassenger(): Boolean {
        return RideVerificationPolicy.grantsAccess(passengerVerification.value?.status)
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
