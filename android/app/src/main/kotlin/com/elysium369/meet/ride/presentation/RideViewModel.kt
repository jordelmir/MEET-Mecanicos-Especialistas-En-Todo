package com.elysium369.meet.ride.presentation

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.BuildConfig
import com.elysium369.meet.core.audio.VoiceFeedbackManager
import com.elysium369.meet.identity.ActivePrincipalKernel
import com.elysium369.meet.data.local.dao.ProviderProfileDao
import com.elysium369.meet.data.local.dao.RatingDao
import com.elysium369.meet.data.local.dao.RideDao
import com.elysium369.meet.data.local.entities.DriverVerificationEntity
import com.elysium369.meet.data.local.entities.PassengerVerificationEntity
import com.elysium369.meet.data.local.entities.RatingEntity
import com.elysium369.meet.data.local.entities.RideChatMessageEntity
import com.elysium369.meet.data.local.entities.RideOfferEntity
import com.elysium369.meet.data.local.entities.RideRequestEntity
import com.elysium369.meet.data.supabase.SupabaseManager
import com.elysium369.meet.ride.data.RideCommandEnqueueResult
import com.elysium369.meet.ride.data.remote.RideCommandPayload
import com.elysium369.meet.ride.data.RideCommandRepository
import com.elysium369.meet.ride.data.RideProjectionConnectionState
import com.elysium369.meet.ride.data.RideProjectionRefreshResult
import com.elysium369.meet.ride.data.RideProjectionSyncPolicy
import com.elysium369.meet.ride.data.RideRemoteProjectionRepository
import com.elysium369.meet.ride.data.remote.PlatformTrustCenterGateway
import com.elysium369.meet.ride.data.remote.RideDriverPilotEnrollment
import com.elysium369.meet.ride.data.remote.TrustEvidenceFile
import com.elysium369.meet.ride.data.remote.ServiceVerificationSubmission
import com.elysium369.meet.ride.dispatch.RideExposureGateway
import com.elysium369.meet.ride.dispatch.RideExposureTracker
import com.elysium369.meet.ride.domain.RideActorRole
import com.elysium369.meet.ride.domain.RideArrivalPolicy
import com.elysium369.meet.ride.domain.RideCancellationPolicy
import com.elysium369.meet.ride.domain.RideCancellationReason
import com.elysium369.meet.ride.domain.RideCommandEnvelope

import com.elysium369.meet.ride.domain.RideCommandType
import com.elysium369.meet.ride.domain.RideDriverPresencePolicy
import com.elysium369.meet.ride.domain.RideDriverVehicleSummary
import com.elysium369.meet.ride.domain.RideFareBidPolicy
import com.elysium369.meet.ride.domain.RideFareEngine
import com.elysium369.meet.ride.domain.RideFareMode
import com.elysium369.meet.ride.domain.RideGuardianPolicy
import com.elysium369.meet.ride.domain.RideId
import com.elysium369.meet.ride.domain.RideIdempotencyKey
import com.elysium369.meet.ride.domain.RidePayloadVersion
import com.elysium369.meet.ride.domain.RideSafetySignalType
import com.elysium369.meet.ride.domain.RideShareCategory
import com.elysium369.meet.ride.domain.RideSupportCategory
import com.elysium369.meet.ride.domain.RideSupportPolicy
import com.elysium369.meet.ride.domain.RideVerificationEvidencePolicy
import com.elysium369.meet.ride.domain.RideVerificationPolicy
import com.elysium369.meet.ride.domain.RideVersion
import com.elysium369.meet.ride.domain.VerificationFileEvidence
import com.elysium369.meet.ride.map.RideGeoPoint
import com.elysium369.meet.ride.presence.RideDriverAvailability
import com.elysium369.meet.ride.presence.RidePresenceGateway
import com.elysium369.meet.ride.automatch.RideAutoMatchGateway
import com.elysium369.meet.ride.automatch.RideAutoMatchPolicy
import com.elysium369.meet.ride.automatch.RideAutoMatchStrategy
import com.elysium369.meet.ride.demand.RideDemandGateway
import com.elysium369.meet.ride.demand.RideDemandSnapshot
import com.elysium369.meet.ride.demand.RidePricingIntelligence
import com.elysium369.meet.ride.eta.RideEtaGateway
import com.elysium369.meet.ride.nextjob.NextJobPrivacyProjection
import com.elysium369.meet.ride.nextjob.RideNextJobGateway
import com.elysium369.meet.ride.payment.RidePaymentGateway
import com.elysium369.meet.ride.payment.RidePaymentStatus
import com.elysium369.meet.ride.reputation.DriverPublicProfile
import com.elysium369.meet.ride.reputation.RideReputationGateway
import com.elysium369.meet.ride.safety.GuardianRideMonitor
import com.elysium369.meet.ride.safety.RideSafetyGateway
import com.elysium369.meet.ride.safety.SafetySignalSeverity
import com.elysium369.meet.ride.safety.SafetySignalType
import com.elysium369.meet.ride.traffic.RideGeoCell
import com.elysium369.meet.ride.traffic.RideRoadIncident
import com.elysium369.meet.ride.traffic.RideRoadIncidentType
import com.elysium369.meet.ride.traffic.RideRoadReportAvailabilityPolicy
import com.elysium369.meet.ride.traffic.RideRoadSide
import com.elysium369.meet.ride.traffic.RideSegmentSpeedSample
import com.elysium369.meet.ride.work.RideDriverEnrollmentWorker
import com.elysium369.meet.ui.ObdViewModel.GpsLocationInfo
import com.elysium369.meet.ui.RideClaimFeedback
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.Instant
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

@Serializable
private data class RemoteActiveRideVehicle(
    val id: String,
    @SerialName("verification_method") val verificationMethod: String? = null,
    @SerialName("pilot_access_expires_at") val pilotAccessExpiresAt: String? = null,
)

@Serializable
private data class RemoteRideDriverVehicleSummary(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val make: String? = null,
    val model: String? = null,
    @SerialName("model_year") val modelYear: Int? = null,
    val color: String? = null,
    @SerialName("plate_masked") val plateMasked: String? = null,
    @SerialName("fleet_name") val fleetName: String? = null,
    val seats: Int,
    @SerialName("verification_status") val verificationStatus: String,
    @SerialName("is_active") val active: Boolean,
) {
    fun toDomain(): RideDriverVehicleSummary = RideDriverVehicleSummary(
        id = id,
        displayName = displayName,
        make = make,
        model = model,
        modelYear = modelYear,
        color = color,
        plateMasked = plateMasked,
        fleetName = fleetName,
        seats = seats,
        verificationStatus = verificationStatus,
        active = active,
    )
}

@Serializable
private data class RemoteRideRoadIncident(
    val id: String,
    val reporter_id: String,
    val trip_id: String,
    val road_segment_id: String,
    val incident_type: String,
    val road_side: String,
    val severity: Int,
    val latitude: Double,
    val longitude: Double,
    val bearing_degrees: Float?,
    val accuracy_meters: Float?,
    val geohash_coarse: String,
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

@Serializable
private data class RideChatWireMessage(
    val id: String,
    @SerialName("ride_request_id") val rideRequestId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("sender_name") val senderName: String,
    @SerialName("sender_role") val senderRole: String,
    @SerialName("message_type") val messageType: String,
    @SerialName("text_content") val textContent: String? = null,
    @SerialName("media_path") val mediaPath: String? = null,
    @SerialName("media_mime_type") val mediaMimeType: String? = null,
    @SerialName("audio_duration_ms") val audioDurationMs: Long? = null,
    @SerialName("created_at_epoch_ms") val createdAtEpochMs: Long,
)

@HiltViewModel
class RideViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val rideDao: RideDao,
    val rideCommandRepository: RideCommandRepository,
    val rideRemoteProjectionRepository: RideRemoteProjectionRepository,
    val ratingDao: RatingDao,
    val providerProfileDao: ProviderProfileDao,
    val voiceFeedbackManager: VoiceFeedbackManager,
    val activePrincipalKernel: ActivePrincipalKernel,
    val presenceGateway: RidePresenceGateway,
    val exposureGateway: RideExposureGateway,
    val reputationGateway: RideReputationGateway,
    val autoMatchGateway: RideAutoMatchGateway,
    val etaGateway: RideEtaGateway,
    val nextJobGateway: RideNextJobGateway,
    val demandGateway: RideDemandGateway,
    val paymentGateway: RidePaymentGateway,
    val safetyGateway: RideSafetyGateway,
) : ViewModel() {

    val localDeviceId: String = activePrincipalKernel.localDeviceId
    val activePrincipal = activePrincipalKernel.activePrincipal
    val currentRideActorId: String get() = activePrincipalKernel.current().id

    private val _currentGpsLocation = MutableStateFlow<GpsLocationInfo?>(null)
    val currentGpsLocation: StateFlow<GpsLocationInfo?> = _currentGpsLocation.asStateFlow()

    private val _currentDemandSnapshot = MutableStateFlow<RideDemandSnapshot?>(null)
    val currentDemandSnapshot: StateFlow<RideDemandSnapshot?> = _currentDemandSnapshot.asStateFlow()

    private val _currentMarketPricingRange = MutableStateFlow<RidePricingIntelligence.MarketRange?>(null)
    val currentMarketPricingRange: StateFlow<RidePricingIntelligence.MarketRange?> = _currentMarketPricingRange.asStateFlow()

    private val _currentNextJobProjection = MutableStateFlow<NextJobPrivacyProjection?>(null)
    val currentNextJobProjection: StateFlow<NextJobPrivacyProjection?> = _currentNextJobProjection.asStateFlow()

    private val _currentDriverPublicProfile = MutableStateFlow<DriverPublicProfile?>(null)
    val currentDriverPublicProfile: StateFlow<DriverPublicProfile?> = _currentDriverPublicProfile.asStateFlow()

    private val _activeRideRequest = MutableStateFlow<RideRequestEntity?>(null)
    val activeRideRequest: StateFlow<RideRequestEntity?> = _activeRideRequest.asStateFlow()

    private val _rideOffers = MutableStateFlow<List<RideOfferEntity>>(emptyList())
    val rideOffers: StateFlow<List<RideOfferEntity>> = _rideOffers.asStateFlow()

    private val _rideChatMessages = MutableStateFlow<List<RideChatMessageEntity>>(emptyList())
    val rideChatMessages: StateFlow<List<RideChatMessageEntity>> = _rideChatMessages.asStateFlow()

    private val _rideDriverMode = MutableStateFlow(false)
    val rideDriverMode: StateFlow<Boolean> = _rideDriverMode.asStateFlow()

    private val _rideDriverVehicles = MutableStateFlow<List<RideDriverVehicleSummary>>(emptyList())
    val rideDriverVehicles: StateFlow<List<RideDriverVehicleSummary>> = _rideDriverVehicles.asStateFlow()

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

    private val _ridePinFeedback = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val ridePinFeedback: SharedFlow<String> = _ridePinFeedback.asSharedFlow()
    private val _rideSafetyFeedback = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val rideSafetyFeedback: SharedFlow<String> = _rideSafetyFeedback.asSharedFlow()
    private val _rideRoadReportFeedback = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val rideRoadReportFeedback: SharedFlow<String> = _rideRoadReportFeedback.asSharedFlow()
    private val _rideSupportFeedback = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val rideSupportFeedback: SharedFlow<String> = _rideSupportFeedback.asSharedFlow()

    private val _seenDriversCount = MutableStateFlow<Int>(0)
    val seenDriversCount: StateFlow<Int> = _seenDriversCount.asStateFlow()

    val exposureTracker = RideExposureTracker()

    private val _driverPresetMessages = MutableStateFlow(
        listOf(
            "Ya me encuentro en la ubicación",
            "Voy en camino, llego en unos 5 minutos",
            "Estoy parado en el semáforo/esquina",
            "Hola, ya inicié el viaje",
            "Estoy afuera con las luces intermitentes encendidas"
        )
    )
    val driverPresetMessages: StateFlow<List<String>> = _driverPresetMessages.asStateFlow()

    private var jobOffersCollection: Job? = null
    private var jobChatCollection: Job? = null
    private var jobChatRemoteSync: Job? = null
    private var rideProjectionJob: Job? = null
    private var seenCountPollingJob: Job? = null

    private val _rideProjectionConnectionState =
        MutableStateFlow(RideProjectionConnectionState.IDLE)
    val rideProjectionConnectionState: StateFlow<RideProjectionConnectionState> =
        _rideProjectionConnectionState.asStateFlow()

    private val _rideVerificationNotice = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val rideVerificationNotice: SharedFlow<String> = _rideVerificationNotice.asSharedFlow()

    val driverVerification: StateFlow<DriverVerificationEntity?> =
        activePrincipalKernel.activePrincipal.flatMapLatest { principal ->
            if (principal.isAuthenticated) {
                rideDao.getDriverVerificationFlow(principal.id)
            } else {
                flowOf(null)
            }
        }
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
                        Log.i("MeetRides", "Pending driver verification upgraded to local pilot access")
                    } else {
                        Log.w("MeetRides", "Driver pilot access withheld: ${evidence.issues.joinToString()}")
                        _rideVerificationNotice.emit(
                            "Completa nuevamente las evidencias del chofer; una o más fotos no están disponibles.",
                        )
                    }
                }
                if (
                    verification?.status == RideVerificationPolicy.PILOT_APPROVED &&
                    evaluateDriverEvidence(verification).isReady
                ) {
                    enqueueDriverPilotEnrollment(verification)
                }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val passengerVerification: StateFlow<PassengerVerificationEntity?> =
        activePrincipalKernel.activePrincipal.flatMapLatest { principal ->
            if (principal.isAuthenticated) {
                rideDao.getPassengerVerificationFlow(principal.id)
            } else {
                flowOf(null)
            }
        }
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
                        Log.i("MeetRides", "Pending passenger verification upgraded to local pilot access")
                    } else {
                        Log.w("MeetRides", "Passenger pilot access withheld: ${evidence.issues.joinToString()}")
                        _rideVerificationNotice.emit(
                            "Completa nuevamente las fotos de identidad; una o más evidencias no están disponibles.",
                        )
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun updateCurrentLocation(gps: GpsLocationInfo) {
        _currentGpsLocation.value = gps
    }

    fun currentCloudUserId(): String? =
        SupabaseManager.client.auth.currentUserOrNull()?.id

    fun startRideProjectionSync() {
        if (rideProjectionJob?.isActive == true) return
        if (currentCloudUserId() == null) {
            _rideProjectionConnectionState.value =
                RideProjectionConnectionState.AUTHENTICATION_REQUIRED
            Log.d("MeetRides", "Ride projection deferred until authentication")
            return
        }
        rideProjectionJob = viewModelScope.launch(Dispatchers.IO) {
            _rideProjectionConnectionState.value =
                RideProjectionConnectionState.CONNECTING
            val realtimeWakeUps = rideRemoteProjectionRepository
                .realtimeWakeUps()
                .onEach {
                    _rideProjectionConnectionState.value =
                        RideProjectionConnectionState.LIVE
                }
                .retryWhen { error, attempt ->
                    if (currentCloudUserId() == null) {
                        _rideProjectionConnectionState.value =
                            RideProjectionConnectionState.AUTHENTICATION_REQUIRED
                        false
                    } else {
                        val delayMs = RideProjectionSyncPolicy.reconnectDelayMs(attempt)
                        _rideProjectionConnectionState.value =
                            RideProjectionConnectionState.RECOVERING
                        Log.w("MeetRides", "Realtime wake-up interrupted; reconnecting in ${delayMs}ms", error)
                        delay(delayMs)
                        _rideProjectionConnectionState.value =
                            RideProjectionConnectionState.CONNECTING
                        true
                    }
                }
            val foregroundHeartbeat = flow {
                while (currentCoroutineContext().isActive) {
                    delay(RideProjectionSyncPolicy.HEARTBEAT_INTERVAL_MS)
                    emit(Unit)
                }
            }
            merge(realtimeWakeUps, foregroundHeartbeat)
                .onStart { emit(Unit) }
                .conflate()
                .collect {
                    refreshRideProjection()
                }
        }
    }

    fun stopRideProjectionSync() {
        rideProjectionJob?.cancel()
        rideProjectionJob = null
        _rideProjectionConnectionState.value = RideProjectionConnectionState.IDLE
    }

    fun refreshRideProjectionNow() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshRideProjection()
        }
    }

    private suspend fun refreshRideProjection() {
        when (val result = rideRemoteProjectionRepository.refreshVisibleRides()) {
            is RideProjectionRefreshResult.Refreshed -> {
                Log.d("MeetRides", "Remote ride projection refreshed: ${result.count}")
            }
            RideProjectionRefreshResult.AuthenticationRequired -> {
                _rideProjectionConnectionState.value =
                    RideProjectionConnectionState.AUTHENTICATION_REQUIRED
                Log.d("MeetRides", "Ride projection waiting for authenticated session")
            }
            is RideProjectionRefreshResult.Failed -> {
                Log.w("MeetRides", "Ride projection refresh failed: ${result.message}")
            }
        }
    }

    fun toggleRideDriverMode() {
        _rideDriverMode.value = !_rideDriverMode.value
        val newAvailability = if (_rideDriverMode.value) RideDriverAvailability.AVAILABLE else RideDriverAvailability.OFFLINE
        viewModelScope.launch(Dispatchers.IO) {
            presenceGateway.setAvailability(newAvailability)
        }
    }

    fun recordDriverLiveness(evidenceSha256: String, capturedAtEpochMs: Long) {
        if (!evidenceSha256.matches(Regex("[0-9a-f]{64}"))) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                SupabaseManager.client.postgrest.rpc(
                    "ride_record_driver_liveness_v1",
                    buildJsonObject {
                        put("p_evidence_sha256", evidenceSha256)
                        put("p_captured_at", Instant.ofEpochMilli(capturedAtEpochMs).toString())
                    },
                )
            }.onFailure { error ->
                Log.w("MeetRides", "Liveness evidence pending remote confirmation", error)
                _rideVerificationNotice.emit(
                    "Presencia validada en el dispositivo; la nube la confirmará al recuperar conexión.",
                )
            }
        }
    }

    fun refreshRideDriverVehicles() {
        viewModelScope.launch(Dispatchers.IO) {
            val userId = currentCloudUserId() ?: return@launch
            runCatching {
                SupabaseManager.client.postgrest["ride_driver_vehicles"]
                    .select {
                        filter { eq("driver_id", userId) }
                    }
                    .decodeList<RemoteRideDriverVehicleSummary>()
                    .map(RemoteRideDriverVehicleSummary::toDomain)
            }.onSuccess { _rideDriverVehicles.value = it }
                .onFailure { Log.w("MeetRides", "Vehicle fleet refresh failed", it) }
        }
    }

    fun addRideDriverVehicle(
        make: String,
        model: String,
        year: Int,
        color: String,
        plate: String,
        fleetName: String?,
    ) {
        val normalized = listOf(make, model, color, plate).map(String::trim)
        if (normalized.any(String::isBlank) || year !in 1900..2200) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                SupabaseManager.client.postgrest.rpc(
                    "ride_upsert_driver_vehicle_v1",
                    buildJsonObject {
                        put("p_vehicle_id", UUID.randomUUID().toString())
                        put("p_display_name", "$make $model $year $color")
                        put("p_seats", 4)
                        put("p_make", make.trim())
                        put("p_model", model.trim())
                        put("p_model_year", year)
                        put("p_color", color.trim())
                        put("p_plate_masked", plate.trim().uppercase())
                        fleetName?.trim()?.takeIf(String::isNotBlank)?.let { put("p_fleet_name", it) }
                    },
                )
            }.onSuccess { refreshRideDriverVehicles() }
                .onFailure { _rideVerificationNotice.emit("No se pudo guardar el vehículo en la nube.") }
        }
    }

    fun activateRideDriverVehicle(vehicleId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                SupabaseManager.client.postgrest.rpc(
                    "ride_set_active_vehicle_v1",
                    buildJsonObject { put("p_vehicle_id", vehicleId) },
                )
            }.onSuccess { refreshRideDriverVehicles() }
                .onFailure { _rideVerificationNotice.emit("Sólo puedes activar un vehículo verificado.") }
        }
    }

    fun selectActiveRide(request: RideRequestEntity?) {
        _activeRideRequest.value = request
        jobOffersCollection?.cancel()
        jobChatCollection?.cancel()
        jobChatRemoteSync?.cancel()
        seenCountPollingJob?.cancel()

        if (request != null) {
            _rideSharingSelections.update { current ->
                if (request.requestId in current) {
                    current
                } else {
                    current + (request.requestId to setOf(RideShareCategory.EXACT_LOCATION))
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
            jobChatRemoteSync = viewModelScope.launch(Dispatchers.IO) {
                while (isActive) {
                    syncRideChat(request.requestId)
                    delay(4_000)
                }
            }
            seenCountPollingJob = viewModelScope.launch(Dispatchers.IO) {
                while (isActive) {
                    exposureGateway.countSeenDrivers(request.requestId)
                        .onSuccess { count ->
                            _seenDriversCount.value = count
                        }
                    delay(5_000)
                }
            }
        } else {
            _rideOffers.value = emptyList()
            _rideChatMessages.value = emptyList()
            _seenDriversCount.value = 0
        }
    }

    private suspend fun syncRideChat(requestId: String) {
        val cloudUserId = currentCloudUserId() ?: return
        runCatching {
            rideDao.getPendingChatMessages().filter {
                it.rideRequestId == requestId && it.senderRole in setOf("PASSENGER", "DRIVER")
            }.forEach { local ->
                var remotePath = local.remoteMediaPath
                val localMediaPath = local.imageFilePath ?: local.audioFilePath
                if (remotePath == null && localMediaPath != null) {
                    val file = File(localMediaPath)
                    if (file.exists()) {
                        val extension = file.extension.ifBlank { if (local.messageType == "AUDIO") "m4a" else "jpg" }
                        remotePath = "$requestId/$cloudUserId/${local.messageId}.$extension"
                        SupabaseManager.client.storage.from("ride-media").upload(remotePath, file.readBytes())
                    }
                }
                SupabaseManager.client.postgrest["ride_messages"].upsert(
                    RideChatWireMessage(
                        id = local.messageId,
                        rideRequestId = local.rideRequestId,
                        senderId = cloudUserId,
                        senderName = local.senderName,
                        senderRole = local.senderRole,
                        messageType = local.messageType,
                        textContent = local.textContent,
                        mediaPath = remotePath,
                        mediaMimeType = local.mediaMimeType,
                        audioDurationMs = local.audioDurationMs,
                        createdAtEpochMs = local.createdAt,
                    ),
                )
                rideDao.updateChatMessageSyncState(local.messageId, "SYNCED", remotePath)
            }

            val remoteMessages = SupabaseManager.client.postgrest["ride_messages"].select {
                filter { eq("ride_request_id", requestId) }
            }.decodeList<RideChatWireMessage>()
            remoteMessages.forEach { remote ->
                val localMediaFile = remote.mediaPath?.let { path ->
                    val extension = path.substringAfterLast('.', "bin")
                    val directory = File(
                        context.filesDir,
                        if (remote.messageType == "AUDIO") "meet_rides_audio" else "meet_rides_images",
                    ).apply { mkdirs() }
                    File(directory, "remote_${remote.id}.$extension").also { target ->
                        if (!target.exists()) {
                            target.writeBytes(
                                SupabaseManager.client.storage.from("ride-media").downloadAuthenticated(path),
                            )
                        }
                    }
                }
                val localSenderId = if (remote.senderId == cloudUserId) {
                    if (remote.senderRole == "DRIVER") {
                        driverVerification.value?.driverId ?: remote.senderId
                    } else {
                        passengerVerification.value?.passengerId ?: remote.senderId
                    }
                } else remote.senderId
                rideDao.insertChatMessage(
                    RideChatMessageEntity(
                        messageId = remote.id,
                        rideRequestId = remote.rideRequestId,
                        senderId = localSenderId,
                        senderName = remote.senderName,
                        senderRole = remote.senderRole,
                        messageType = remote.messageType,
                        textContent = remote.textContent,
                        audioFilePath = localMediaFile?.takeIf { remote.messageType == "AUDIO" }?.absolutePath,
                        imageFilePath = localMediaFile?.takeIf { remote.messageType == "IMAGE" }?.absolutePath,
                        remoteMediaPath = remote.mediaPath,
                        mediaMimeType = remote.mediaMimeType,
                        audioDurationMs = remote.audioDurationMs,
                        syncState = "SYNCED",
                        createdAt = remote.createdAtEpochMs,
                    ),
                )
            }
        }.onFailure { error ->
            Log.w("MeetRides", "Ride chat sync deferred", error)
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
        tripId: String,
        type: RideRoadIncidentType,
        side: RideRoadSide,
        severity: Int,
    ) {
        if (tripId.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val request = rideDao.getRequestById(tripId)
            if (request == null) {
                _rideRoadReportFeedback.emit("No se encontró el viaje activo para registrar el reporte.")
                return@launch
            }
            val gps = _currentGpsLocation.value
            val availability = RideRoadReportAvailabilityPolicy.evaluate(
                isDriver = _rideDriverMode.value,
                localStatus = request.status,
                serverState = request.serverState,
                serverVersion = request.serverVersion,
                hasCurrentGps = gps != null,
            )
            if (!availability.allowed || gps == null) {
                _rideRoadReportFeedback.emit(availability.message)
                return@launch
            }
            val now = System.currentTimeMillis()
            val lifetimeMs = when (type) {
                RideRoadIncidentType.POLICE_PRESENCE,
                RideRoadIncidentType.PUBLIC_POLICE,
                RideRoadIncidentType.TRAFFIC_POLICE,
                -> 20 * 60 * 1000L
                RideRoadIncidentType.TRAFFIC_CONTROL,
                RideRoadIncidentType.SLOW_TRAFFIC,
                RideRoadIncidentType.VERY_SLOW_TRAFFIC,
                -> 30 * 60 * 1000L
                RideRoadIncidentType.ROAD_CLOSED,
                RideRoadIncidentType.WRONG_WAY_HAZARD,
                RideRoadIncidentType.STALLED_VEHICLE,
                RideRoadIncidentType.OBSTACLE,
                -> 90 * 60 * 1000L
                RideRoadIncidentType.POTHOLE,
                RideRoadIncidentType.SPEED_BUMP,
                RideRoadIncidentType.FLOODING,
                -> 6 * 60 * 60 * 1000L
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
            _rideRoadReportFeedback.emit(
                "Condición vial registrada durante la ruta. Gracias por ayudar a la comunidad.",
            )
            voiceFeedbackManager.speak(
                "Incidencia vial reportada. Gracias por ayudar a la comunidad.",
                "Road incident reported. Thank you for helping the community.",
            )

            val userId = SupabaseManager.client.auth.currentUserOrNull()?.id
            if (userId == null) {
                Log.w("RideViewModel", "Road report retained locally; authenticated sync unavailable")
                return@launch
            }
            val isoExpiry = Instant.ofEpochMilli(incident.expiresAtEpochMs).toString()
            val remoteReport = RemoteRideRoadIncident(
                id = incident.id,
                reporter_id = userId,
                trip_id = request.requestId,
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
                SupabaseManager.client.postgrest["ride_road_incidents"].insert(remoteReport)
            }.onFailure { error ->
                Log.w("RideViewModel", "Road report retained locally; cloud sync unavailable", error)
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
            val capturedAt = Instant.ofEpochMilli(now).toString()
            val bucketAt = Instant.ofEpochMilli(minuteBucket).toString()
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
                Log.d("RideViewModel", "Speed telemetry retained locally; cloud trip not synchronized")
            }
        }
    }

    fun announceRideEvent(spanish: String, english: String) {
        voiceFeedbackManager.speak(spanish, english)
    }

    private fun rideCommandEnvelope(
        requestId: String,
        serverVersion: Long,
        type: RideCommandType,
    ): RideCommandEnvelope = RideCommandEnvelope(
        rideId = RideId.of(requestId),
        expectedVersion = RideVersion.of(serverVersion),
        idempotencyKey = RideIdempotencyKey.of(
            "${type.name.lowercase()}:$requestId:${UUID.randomUUID()}",
        ),
        type = type,
        payloadVersion = RidePayloadVersion.of(1),
    )

    private fun rideFareToMinorUnits(
        majorUnits: Double,
        currency: String,
    ): Long {
        val fractionDigits = when (currency.uppercase()) {
            "CRC" -> 0
            else -> 2
        }
        return BigDecimal.valueOf(majorUnits)
            .movePointRight(fractionDigits)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    private suspend fun activeVerifiedRemoteVehicleId(): String? {
        val userId = currentCloudUserId() ?: return null
        return runCatching {
            SupabaseManager.client.postgrest["ride_driver_vehicles"]
                .select(
                    columns = Columns.list(
                        "id",
                        "verification_method",
                        "pilot_access_expires_at",
                    ),
                ) {
                    filter {
                        eq("driver_id", userId)
                        eq("is_active", true)
                        eq("verification_status", "VERIFIED")
                    }
                    limit(1)
                }
                .decodeList<RemoteActiveRideVehicle>()
                .firstOrNull { vehicle ->
                    vehicle.verificationMethod != "PILOT_EVIDENCE_ATTESTATION" ||
                        vehicle.pilotAccessExpiresAt
                            ?.let { expiresAt ->
                                runCatching { Instant.parse(expiresAt) }
                                    .getOrNull()
                                    ?.isAfter(Instant.now()) == true
                            } == true
                }
                ?.id
        }.getOrNull()
    }

    private suspend fun enqueueAuthoritativeRideCommand(
        request: RideRequestEntity,
        type: RideCommandType,
        payload: RideCommandPayload = RideCommandPayload(),
        expectedVersion: Long = request.serverVersion,
    ): RideCommandEnqueueResult = rideCommandRepository.enqueue(
        envelope = rideCommandEnvelope(
            requestId = request.requestId,
            serverVersion = expectedVersion,
            type = type,
        ),
        payload = payload,
    )

    private suspend fun reportRideCommandEnqueue(
        result: RideCommandEnqueueResult,
        acceptedMessage: String,
    ): Boolean {
        val message = when (result) {
            RideCommandEnqueueResult.Enqueued -> acceptedMessage
            RideCommandEnqueueResult.AlreadyQueued ->
                "La operación ya estaba pendiente de confirmación."
            RideCommandEnqueueResult.AuthenticationRequired ->
                "Inicia sesión para usar Viajes con autoridad y protección de cuenta."
            is RideCommandEnqueueResult.IdempotencyConflict ->
                "La operación local entró en conflicto y no fue reenviada."
            is RideCommandEnqueueResult.InvalidCommand ->
                result.message
        }
        _rideVerificationNotice.emit(message)
        return result is RideCommandEnqueueResult.Enqueued ||
            result is RideCommandEnqueueResult.AlreadyQueued
    }

    fun createRideRequest(
        passengerId: String,
        passengerName: String,
        passengerPhone: String,
        countryCode: String,
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
        estimatedDistanceMeters: Long = (estDistance * 1_000.0).toLong(),
        estimatedDurationSeconds: Long = estDuration * 60L,
        stopsJson: String = "[]",
        paymentMethod: String = "CASH",
        fareMode: RideFareMode = RideFareMode.OPEN_BID,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalizedCountryCode = countryCode
                .trim()
                .uppercase()
                .takeIf { it.matches(Regex("[A-Z]{2}")) }
            if (normalizedCountryCode == null) {
                _rideVerificationNotice.emit("No se pudo validar el país de recogida. Actualiza el GPS.")
                return@launch
            }
            val meteredQuote = if (fareMode == RideFareMode.METERED_TIME_DISTANCE) {
                runCatching {
                    require(currency.equals("CRC", ignoreCase = true)) {
                        "La tarifa por tiempo y distancia inicia en CRC"
                    }
                    RideFareEngine.quoteCostaRica(
                        distanceMeters = estimatedDistanceMeters,
                        durationSeconds = estimatedDurationSeconds,
                    )
                }.getOrElse {
                    _rideVerificationNotice.emit(it.message ?: "No se pudo calcular la tarifa por tiempo y distancia.")
                    return@launch
                }
            } else null

            val normalizedFare = meteredQuote?.estimatedTotalMinor?.toDouble()
                ?: RideFareBidPolicy.normalize(priceOffer, currency)
            val offeredFareMinor = runCatching {
                rideFareToMinorUnits(normalizedFare, currency)
            }.getOrElse {
                _rideVerificationNotice.emit("La tarifa ingresada no es válida.")
                return@launch
            }
            if (offeredFareMinor <= 0L) {
                _rideVerificationNotice.emit("La tarifa debe ser mayor que cero.")
                return@launch
            }
            val breakdown = if (meteredQuote == null) {
                """{"mode":"OPEN_BID","acceptedFareMinor":""" + offeredFareMinor + ""","currency":"""" + currency.uppercase() + """"}"""
            } else {
                """{"mode":"METERED_TIME_DISTANCE","distanceFareMinor":""" + meteredQuote.distanceFareMinor + ""","timeFareMinor":""" + meteredQuote.timeFareMinor + ""","estimatedTotalMinor":""" + meteredQuote.estimatedTotalMinor + ""","currency":"CRC","rateCardVersion":""" + meteredQuote.rateCardVersion + """}"""
            }
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
                priceOfferMinor = offeredFareMinor,
                currency = currency,
                estimatedDistanceKm = estDistance,
                estimatedDurationMin = estDuration,
                stopsJson = stopsJson,
                paymentMethod = paymentMethod,
                fareMode = fareMode.name,
                distanceRateMinorPerKm = meteredQuote?.distanceRateMinorPerKm ?: 0L,
                timeRateMinorPerMinute = meteredQuote?.timeRateMinorPerMinute ?: 0L,
                estimatedFareMinor = offeredFareMinor,
                fareRateCardVersion = meteredQuote?.rateCardVersion ?: 1L,
                allowsInTripStops = RideFareEngine.allowsStopsDuringTrip(fareMode),
                fareBreakdownJson = breakdown,
                status = "OPEN",
                serverState = "SEARCHING",
                serverVersion = 0L,
                syncState = "PENDING",
                createdAt = System.currentTimeMillis()
            )
            rideDao.insertRequest(request)
            val result = rideCommandRepository.enqueue(
                envelope = rideCommandEnvelope(
                    requestId = request.requestId,
                    serverVersion = 0L,
                    type = RideCommandType.PUBLISH,
                ),
                payload = RideCommandPayload(
                    displayName = passengerName,
                    countryCode = normalizedCountryCode,
                    pickupLatitude = pickupLat.toString(),
                    pickupLongitude = pickupLng.toString(),
                    pickupAddress = pickupAddr,
                    destinationLatitude = destLat.toString(),
                    destinationLongitude = destLng.toString(),
                    destinationAddress = destAddr,
                    offeredFareMinor = offeredFareMinor,
                    currency = currency.uppercase(),
                    paymentMethod = paymentMethod.uppercase(),
                    stopsJson = stopsJson,
                    fareMode = fareMode.name,
                    distanceRateMinorPerKm = meteredQuote?.distanceRateMinorPerKm ?: 0L,
                    timeRateMinorPerMinute = meteredQuote?.timeRateMinorPerMinute ?: 0L,
                    estimatedDistanceMeters = estimatedDistanceMeters,
                    estimatedDurationSeconds = estimatedDurationSeconds,
                    fareRateCardVersion = meteredQuote?.rateCardVersion ?: 1L,
                    allowsInTripStops = RideFareEngine.allowsStopsDuringTrip(fareMode),
                ),
            )
            val queued = reportRideCommandEnqueue(
                result = result,
                acceptedMessage = "Solicitud enviada. La autoridad de Viajes está confirmándola.",
            )
            if (!queued) {
                rideDao.deleteRequest(request.requestId)
                return@launch
            }
            withContext(Dispatchers.Main) {
                selectActiveRide(request)
            }
            exposureGateway.publishDispatch(request.requestId)
        }
    }

    fun replaceRideStops(
        requestId: String,
        stopsJson: String,
        estimatedDistanceMeters: Long,
        estimatedDurationSeconds: Long,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val request = rideDao.getRequestById(requestId) ?: return@launch
            if (request.fareMode != RideFareMode.METERED_TIME_DISTANCE.name) {
                _rideVerificationNotice.emit("Pon tu precio bloquea las paradas después de publicar la solicitud.")
                return@launch
            }
            val result = enqueueAuthoritativeRideCommand(
                request = request,
                type = RideCommandType.UPDATE_ROUTE,
                payload = RideCommandPayload(
                    stopsJson = stopsJson,
                    estimatedDistanceMeters = estimatedDistanceMeters,
                    estimatedDurationSeconds = estimatedDurationSeconds,
                ),
            )
            reportRideCommandEnqueue(result, "Cambio de paradas enviado. El servidor recalculará el estimado.")
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
            val request = rideDao.getRequestById(requestId) ?: return@launch
            if (request.serverVersion <= 0L) {
                _rideVerificationNotice.emit("Espera la confirmación del servidor antes de ofertar.")
                return@launch
            }
            val remoteVehicleId = activeVerifiedRemoteVehicleId()
            if (remoteVehicleId == null) {
                _rideVerificationNotice.emit("No hay un vehículo remoto activo y verificado para ofertar.")
                return@launch
            }
            val normalizedPrice = RideFareBidPolicy.normalize(counterPrice, currency)
            val fareMinor = runCatching {
                rideFareToMinorUnits(normalizedPrice, currency)
            }.getOrElse {
                _rideVerificationNotice.emit("La contraoferta no es válida.")
                return@launch
            }
            val offerId = UUID.randomUUID().toString()
            val offer = RideOfferEntity(
                offerId = offerId,
                requestId = requestId,
                driverId = driverId,
                driverName = driverName,
                driverPhone = driverPhone,
                driverRating = driverRating,
                driverTotalTrips = driverTotalTrips,
                vehicleDescription = vehicleDesc,
                counterPrice = normalizedPrice,
                currency = currency,
                estimatedArrivalMin = estArrivalMin,
                driverLatitude = driverLat,
                driverLongitude = driverLng,
                message = message,
                status = "PENDING",
                createdAt = System.currentTimeMillis()
            )
            val queued = reportRideCommandEnqueue(
                result = enqueueAuthoritativeRideCommand(
                    request = request,
                    type = RideCommandType.SUBMIT_OFFER,
                    payload = RideCommandPayload(
                        offerId = offerId,
                        vehicleId = remoteVehicleId,
                        fareMinor = fareMinor,
                        currency = currency.uppercase(),
                        etaSeconds = estArrivalMin.coerceAtLeast(0) * 60,
                    ),
                ),
                acceptedMessage = "Oferta enviada; el servidor está validando vehículo, saldo y versión.",
            )
            if (queued) rideDao.insertOffer(offer)
        }
    }

    fun acceptRideOffer(requestId: String, offerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val request = rideDao.getRequestById(requestId) ?: return@launch
            if (request.serverVersion <= 0L) {
                _rideVerificationNotice.emit("La solicitud aún no tiene versión autoritativa.")
                return@launch
            }
            reportRideCommandEnqueue(
                result = enqueueAuthoritativeRideCommand(
                    request = request,
                    type = RideCommandType.ACCEPT_OFFER,
                    payload = RideCommandPayload(offerId = offerId),
                ),
                acceptedMessage = "Aceptación enviada; la asignación sólo será válida al confirmarla el servidor.",
            )
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
            val request = rideDao.getRequestById(requestId) ?: return@launch
            if (request.serverVersion <= 0L) {
                _rideClaimFeedback.emit(
                    RideClaimFeedback(
                        requestId = requestId,
                        won = false,
                        message = "La solicitud todavía no fue confirmada por el servidor.",
                    ),
                )
                return@launch
            }
            val remoteVehicleId = activeVerifiedRemoteVehicleId()
            if (remoteVehicleId == null) {
                _rideClaimFeedback.emit(
                    RideClaimFeedback(
                        requestId = requestId,
                        won = false,
                        message = "Activa un vehículo verificado antes de confirmar.",
                    ),
                )
                return@launch
            }
            val result = enqueueAuthoritativeRideCommand(
                request = request,
                type = RideCommandType.CLAIM,
                payload = RideCommandPayload(vehicleId = remoteVehicleId),
            )
            val queued = result is RideCommandEnqueueResult.Enqueued ||
                result is RideCommandEnqueueResult.AlreadyQueued
            _rideClaimFeedback.emit(
                RideClaimFeedback(
                    requestId = requestId,
                    won = false,
                    pending = queued,
                    message = if (queued) {
                        "Confirmación enviada. El servidor decidirá un único ganador."
                    } else {
                        when (result) {
                            RideCommandEnqueueResult.AuthenticationRequired ->
                                "Inicia sesión antes de confirmar el viaje."
                            is RideCommandEnqueueResult.InvalidCommand ->
                                result.message
                            is RideCommandEnqueueResult.IdempotencyConflict ->
                                result.message
                            else -> "No se pudo encolar la confirmación."
                        }
                    },
                ),
            )
            voiceFeedbackManager.speak(
                if (queued) {
                    "Confirmación enviada. Esperando decisión segura del servidor."
                } else {
                    "No se pudo enviar la confirmación del viaje."
                },
                if (queued) {
                    "Confirmation sent. Waiting for the secure server decision."
                } else {
                    "The ride confirmation could not be sent."
                },
            )
        }
    }

    fun verifyRideBoardingPin(requestId: String, candidate: String) {
        if (!candidate.matches(Regex("[0-9]{4}"))) {
            _ridePinFeedback.tryEmit("El PIN debe contener exactamente cuatro dígitos.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val request = rideDao.getRequestById(requestId) ?: return@launch
            if (request.serverVersion <= 0L || request.serverState != "ARRIVED") {
                _ridePinFeedback.emit("Actualiza el viaje: el servidor debe confirmar que el conductor llegó.")
                return@launch
            }
            val queued = reportRideCommandEnqueue(
                result = enqueueAuthoritativeRideCommand(
                    request = request,
                    type = RideCommandType.VERIFY_BOARDING_PIN,
                    payload = RideCommandPayload(boardingPin = candidate),
                ),
                acceptedMessage = "PIN enviado por canal seguro. Esperando validación del servidor.",
            )
            if (queued) {
                _ridePinFeedback.emit("Verificando PIN. El viaje no iniciará hasta recibir confirmación.")
            }
        }
    }

    fun issueRideBoardingPin(requestId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val request = rideDao.getRequestById(requestId) ?: return@launch
            if (request.serverVersion <= 0L || request.serverState != "ARRIVED") {
                _ridePinFeedback.emit("El PIN se habilita después de confirmar la llegada del conductor.")
                return@launch
            }
            reportRideCommandEnqueue(
                result = enqueueAuthoritativeRideCommand(
                    request = request,
                    type = RideCommandType.ISSUE_BOARDING_PIN,
                ),
                acceptedMessage = "Generando PIN privado en el servidor. No lo compartas antes de abordar.",
            )
        }
    }

    fun updateRideStatus(requestId: String, newStatus: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val request = rideDao.getRequestById(requestId) ?: return@launch
            if (request.serverVersion <= 0L) {
                _rideVerificationNotice.emit("El viaje todavía no tiene versión confirmada por el servidor.")
                return@launch
            }
            val command = when (newStatus) {
                "ARRIVED" -> when (request.serverState) {
                    "ASSIGNED" -> RideCommandType.DRIVER_EN_ROUTE
                    "DRIVER_EN_ROUTE" -> RideCommandType.DRIVER_ARRIVED
                    else -> null
                }
                "IN_PROGRESS" -> RideCommandType.START
                "COMPLETED" -> RideCommandType.COMPLETE
                else -> null
            }
            if (command == null) {
                _rideVerificationNotice.emit("La autoridad rechazó esta transición desde ${request.serverState ?: request.status}.")
                return@launch
            }
            val commandPayload = if (command == RideCommandType.DRIVER_ARRIVED) {
                val gps = _currentGpsLocation.value
                val pickup = RideGeoPoint(
                    latitude = request.pickupLatitude,
                    longitude = request.pickupLongitude,
                    accuracyMeters = request.pickupAccuracy,
                    capturedAtEpochMs = request.createdAt,
                )
                val driverPoint = gps?.let {
                    RideGeoPoint(
                        latitude = it.latitude,
                        longitude = it.longitude,
                        accuracyMeters = it.accuracy,
                        capturedAtEpochMs = it.timestamp.coerceAtLeast(0L),
                    )
                }
                val arrival = RideArrivalPolicy.evaluate(
                    driver = driverPoint,
                    pickup = pickup,
                    nowEpochMs = System.currentTimeMillis(),
                )
                if (!arrival.allowed || gps == null) {
                    val distance = arrival.distanceMeters?.let { " · ${it.toInt()} m" }.orEmpty()
                    _rideVerificationNotice.emit(arrival.reason + distance)
                    return@launch
                }
                RideCommandPayload(
                    driverLatitude = gps.latitude.toString(),
                    driverLongitude = gps.longitude.toString(),
                    driverAccuracyMeters = gps.accuracy.toString(),
                    driverCapturedAt = Instant.ofEpochMilli(gps.timestamp).toString(),
                )
            } else {
                RideCommandPayload()
            }
            val queued = reportRideCommandEnqueue(
                result = enqueueAuthoritativeRideCommand(
                    request = request,
                    type = command,
                    payload = commandPayload,
                ),
                acceptedMessage = when (command) {
                    RideCommandType.DRIVER_EN_ROUTE -> "Salida hacia el pasajero pendiente de confirmación."
                    RideCommandType.DRIVER_ARRIVED -> "Llegada enviada; el pasajero podrá generar su PIN."
                    RideCommandType.START -> "Inicio enviado; esperando confirmación autoritativa."
                    RideCommandType.COMPLETE -> "Cierre enviado; calculando total y comisión exacta."
                    else -> "Operación enviada."
                },
            )
            if (queued && command == RideCommandType.START) {
                voiceFeedbackManager.speak(
                    "Validando el inicio del servicio.",
                    "Validating the start of the service.",
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
            val request = rideDao.getRequestById(requestId) ?: return@launch
            if (request.serverVersion <= 0L) {
                _rideVerificationNotice.emit("La solicitud aún no fue confirmada; espera antes de cancelar.")
                return@launch
            }
            reportRideCommandEnqueue(
                result = enqueueAuthoritativeRideCommand(
                    request = request,
                    type = RideCommandType.CANCEL,
                    payload = RideCommandPayload(
                        reasonCode = reason.name,
                        detail = detail?.trim()?.takeIf(String::isNotEmpty),
                    ),
                ),
                acceptedMessage = "Cancelación enviada. El servidor aplicará estado, seguridad y liberación de saldo.",
            )
        }
    }

    fun activateRideGuardian(
        requestId: String,
        signalType: RideSafetySignalType,
        detail: String?,
    ) {
        if (detail != null && detail.length > 500) return
        viewModelScope.launch(Dispatchers.IO) {
            val request = rideDao.getRequestById(requestId) ?: return@launch
            if (!RideGuardianPolicy.canSignal(request.serverState, request.serverVersion)) {
                _rideSafetyFeedback.emit("Guardian requiere un viaje activo confirmado por el servidor.")
                return@launch
            }
            val result = enqueueAuthoritativeRideCommand(
                request = request,
                type = RideCommandType.SAFETY_SIGNAL,
                payload = RideCommandPayload(
                    safetySignalType = signalType.name,
                    detail = detail?.trim()?.takeIf(String::isNotEmpty),
                ),
            )
            val message = when (result) {
                RideCommandEnqueueResult.Enqueued ->
                    "Guardian envió la señal para registro y revisión. La app no llamó automáticamente a autoridades."
                RideCommandEnqueueResult.AlreadyQueued ->
                    "Esta señal de Guardian ya está pendiente de confirmación."
                RideCommandEnqueueResult.AuthenticationRequired ->
                    "Guardian necesita una sesión autenticada para proteger el historial."
                is RideCommandEnqueueResult.IdempotencyConflict ->
                    "La señal no se duplicó porque existe un conflicto local."
                is RideCommandEnqueueResult.InvalidCommand -> result.message
            }
            _rideSafetyFeedback.emit(message)
        }
    }

    fun openRideSupportCase(
        requestId: String,
        category: RideSupportCategory,
        summary: String,
    ) {
        if (!RideSupportPolicy.isValidSummary(summary)) return
        viewModelScope.launch(Dispatchers.IO) {
            val request = rideDao.getRequestById(requestId) ?: return@launch
            if (request.serverVersion <= 0L) {
                _rideSupportFeedback.emit("Este viaje aún no tiene una versión confirmada por el servidor.")
                return@launch
            }
            val result = enqueueAuthoritativeRideCommand(
                request = request,
                type = RideCommandType.OPEN_SUPPORT_CASE,
                payload = RideCommandPayload(
                    supportCategory = category.name,
                    supportSummary = summary.trim(),
                ),
            )
            val message = when (result) {
                RideCommandEnqueueResult.Enqueued ->
                    "Caso enviado. Soporte lo confirmará con un identificador autoritativo."
                RideCommandEnqueueResult.AlreadyQueued ->
                    "Este caso ya está pendiente de confirmación."
                RideCommandEnqueueResult.AuthenticationRequired ->
                    "Inicia sesión para abrir un caso protegido."
                is RideCommandEnqueueResult.IdempotencyConflict ->
                    "El caso no se duplicó porque existe un conflicto local."
                is RideCommandEnqueueResult.InvalidCommand -> result.message
            }
            _rideSupportFeedback.emit(message)
        }
    }

    fun submitRideRating(requestId: String, isPassengerRating: Boolean, stars: Double, comment: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val req = rideDao.getRequestById(requestId) ?: return@launch
            if (isPassengerRating) {
                rideDao.updatePassengerRating(requestId, stars)
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

                val avg = ratingDao.getAverageRatingForTarget("DRIVER", targetId)
                if (avg != null) {
                    val profile = providerProfileDao.getProfileByUserAndTypes(
                        targetId,
                        listOf("ride_driver", "driver", "ride"),
                    )
                    if (profile != null) {
                        providerProfileDao.updateRatingAndJobs(profile.profileId, avg, System.currentTimeMillis())
                    }
                }
            } else {
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
                syncState = initialRideChatSyncState(),
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
                syncState = initialRideChatSyncState(),
                createdAt = System.currentTimeMillis()
            )
            rideDao.insertChatMessage(msg)
        }
    }

    fun sendRideChatImage(
        context: Context,
        requestId: String,
        senderId: String,
        senderName: String,
        role: String,
        source: Uri,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val imageDirectory = File(context.filesDir, "meet_rides_images").apply { mkdirs() }
            val mimeType = context.contentResolver.getType(source) ?: "image/jpeg"
            val extension = when (mimeType.lowercase()) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val destination = File(
                imageDirectory,
                "ride_${requestId.take(12)}_${System.currentTimeMillis()}.$extension",
            )
            try {
                context.contentResolver.openInputStream(source)?.use { input ->
                    destination.outputStream().use(input::copyTo)
                } ?: error("No fue posible abrir la imagen seleccionada")
                rideDao.insertChatMessage(
                    RideChatMessageEntity(
                        messageId = UUID.randomUUID().toString(),
                        rideRequestId = requestId,
                        senderId = senderId,
                        senderName = senderName,
                        senderRole = role,
                        messageType = "IMAGE",
                        imageFilePath = destination.absolutePath,
                        mediaMimeType = mimeType,
                        syncState = initialRideChatSyncState(),
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            } catch (error: Exception) {
                destination.delete()
                Log.e("RideViewModel", "Failed to persist ride chat image", error)
            }
        }
    }

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

    private var mediaRecorder: android.media.MediaRecorder? = null
    private var mediaPlayer: android.media.MediaPlayer? = null
    private var currentRecordingFile: File? = null
    private var recordingStartTime: Long = 0L

    private val _isRecordingAudio = MutableStateFlow(false)
    val isRecordingAudio: StateFlow<Boolean> = _isRecordingAudio.asStateFlow()

    private val _isPlayingAudio = MutableStateFlow<String?>(null)
    val isPlayingAudio: StateFlow<String?> = _isPlayingAudio.asStateFlow()

    fun startAudioRecording(context: Context) {
        if (_isRecordingAudio.value) return
        try {
            val audioDir = File(context.filesDir, "meet_rides_audio")
            if (!audioDir.exists()) audioDir.mkdirs()

            currentRecordingFile = File(audioDir, "audio_${System.currentTimeMillis()}.m4a")
            mediaRecorder = createCompatibleMediaRecorder(context).apply {
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
            Log.e("RideViewModel", "Failed to start audio recording", e)
            _isRecordingAudio.value = false
        }
    }

    @Suppress("DEPRECATION")
    private fun createCompatibleMediaRecorder(context: Context): android.media.MediaRecorder =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.media.MediaRecorder(context)
        } else {
            android.media.MediaRecorder()
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
                if (duration > 800) {
                    viewModelScope.launch(Dispatchers.IO) {
                        val msg = RideChatMessageEntity(
                            messageId = UUID.randomUUID().toString(),
                            rideRequestId = requestId,
                            senderId = senderId,
                            senderName = senderName,
                            senderRole = role,
                            messageType = "AUDIO",
                            audioFilePath = file.absolutePath,
                            mediaMimeType = "audio/mp4",
                            audioDurationMs = duration,
                            syncState = initialRideChatSyncState(),
                            createdAt = System.currentTimeMillis()
                        )
                        rideDao.insertChatMessage(msg)
                    }
                } else {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("RideViewModel", "Failed to stop audio recording", e)
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
                Log.e("RideViewModel", "MediaPlayer failed", e)
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

    private fun initialRideChatSyncState(): String =
        if (currentCloudUserId() == null) "LOCAL_ONLY" else "PENDING"

    fun openWaze(context: Context, lat: Double, lng: Double) {
        try {
            val wazeUri = Uri.parse("waze://?ll=$lat,$lng&navigate=yes")
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, wazeUri)
            intent.setPackage("com.waze")
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val webUri = Uri.parse("https://waze.com/ul?ll=$lat,$lng&navigate=yes")
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, webUri)
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (ex: Exception) {
                try {
                    val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng")
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, geoUri)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                } catch (e3: Exception) {
                    android.widget.Toast.makeText(context, "No se pudo abrir Waze ni Google Maps", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun shareLocationViaWhatsApp(context: Context, lat: Double, lng: Double, label: String) {
        try {
            val mapsLink = "https://www.google.com/maps?q=$lat,$lng"
            val message = "📍 Ubicación: $label\n" + mapsLink
            val waUri = Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(message)}")
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, waUri)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("RideViewModel", "WhatsApp share failed", e)
        }
    }

    fun getVerificationPhotosDir(context: Context): File {
        val dir = File(context.filesDir, "meet_verifications")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun createVerificationPhotoFile(context: Context, prefix: String): File {
        val dir = getVerificationPhotosDir(context)
        return File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
    }

    private fun verificationFileEvidence(label: String, path: String): VerificationFileEvidence {
        val file = File(path)
        return VerificationFileEvidence(
            label = label,
            path = path,
            byteCount = if (file.isFile) file.length() else 0L,
        )
    }

    private fun evaluatePassengerEvidence(verification: PassengerVerificationEntity) =
        RideVerificationEvidencePolicy.evaluatePassenger(
            fullName = verification.fullName,
            phone = verification.phone,
            files = listOf(
                verificationFileEvidence("profile", verification.pathProfilePhoto),
                verificationFileEvidence("id_front", verification.pathCedulaFront),
                verificationFileEvidence("selfie_with_id", verification.pathSelfieWithCedula),
            ),
        )

    private fun evaluateDriverEvidence(verification: DriverVerificationEntity) =
        RideVerificationEvidencePolicy.evaluateDriver(
            fullName = verification.fullName,
            phone = verification.phone,
            email = verification.email,
            dateOfBirth = verification.dateOfBirth,
            vehicleMake = verification.vehicleMake,
            vehicleModel = verification.vehicleModel,
            vehicleYear = verification.vehicleYear,
            vehicleColor = verification.vehicleColor,
            vehiclePlate = verification.vehiclePlate,
            vehicleSeats = verification.vehicleSeats,
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
        vehicleSeats: Int,
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
            val actorId = currentCloudUserId()
            if (actorId == null) {
                _rideVerificationNotice.emit(
                    "Inicia sesión antes de enviar tu expediente de chofer.",
                )
                return@launch
            }
            val now = System.currentTimeMillis()
            val evidenceFiles = listOf(
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
            )
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
                vehicleSeats = vehicleSeats,
                currentYear = Calendar.getInstance().get(Calendar.YEAR),
                files = evidenceFiles,
            )
            val verificationDecision = RideVerificationPolicy.decide(
                localAutoApprovalEnabled = BuildConfig.RIDE_LOCAL_VERIFICATION_AUTO_APPROVE,
                evidenceReady = evidence.isReady,
                nowEpochMs = now,
            )
            if (!evidence.isReady) {
                Log.w("MeetRides", "Driver verification rejected as incomplete: ${evidence.issues.joinToString()}")
                _rideVerificationNotice.emit(
                    "No se pudo habilitar el acceso: verifica los datos y vuelve a capturar cualquier foto faltante.",
                )
                return@launch
            }
            val entity = DriverVerificationEntity(
                driverId = actorId,
                fullName = fullName,
                phone = phone,
                email = email,
                dateOfBirth = dateOfBirth,
                vehicleMake = vehicleMake,
                vehicleModel = vehicleModel,
                vehicleYear = vehicleYear,
                vehicleColor = vehicleColor,
                vehiclePlate = vehiclePlate,
                vehicleSeats = vehicleSeats,
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
            enqueueDriverPilotEnrollment(entity, evidenceFiles)
            _rideVerificationNotice.emit(
                "Expediente guardado y enviado a revisión. El modo chofer seguirá bloqueado hasta la aprobación remota.",
            )
            Log.i("MeetRides", "Driver verification submitted; status=${verificationDecision.status}")
        }
    }

    private suspend fun enqueueDriverPilotEnrollment(
        verification: DriverVerificationEntity,
        evidenceFiles: List<VerificationFileEvidence> = listOf(
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
    ) {
        val evidenceManifest = buildDriverEvidenceManifestSha256(
            verification = verification,
            evidenceFiles = evidenceFiles,
        )
        val countryCode = _currentGpsLocation.value?.countryCode
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.matches(Regex("[A-Z]{2}")) }
            ?: "CR"
        val currency = if (countryCode == "CR") "CRC" else "USD"
        RideDriverEnrollmentWorker.enqueue(
            context = context,
            enrollment = RideDriverPilotEnrollment(
                driverDisplayName = verification.fullName.trim(),
                countryCode = countryCode,
                currency = currency,
                vehicleReference = sha256Hex(
                    listOf(
                        "vehicle",
                        verification.vehicleMake.trim().uppercase(),
                        verification.vehicleModel.trim().uppercase(),
                        verification.vehicleYear.toString(),
                        verification.vehiclePlate.trim().uppercase(),
                    ).joinToString("|").toByteArray(),
                ),
                vehicleDisplayName = listOf(
                    verification.vehicleMake.trim(),
                    verification.vehicleModel.trim(),
                    verification.vehicleYear.toString(),
                    verification.vehicleColor.trim(),
                ).joinToString(" "),
                seats = verification.vehicleSeats,
                evidenceManifestSha256 = evidenceManifest,
                evidenceFiles = evidenceFiles.map {
                    TrustEvidenceFile(kind = it.label, localPath = it.path)
                },
                phone = verification.phone,
                email = verification.email,
                vehicleMake = verification.vehicleMake,
                vehicleModel = verification.vehicleModel,
                vehicleYear = verification.vehicleYear,
                vehicleColor = verification.vehicleColor,
                vehiclePlate = verification.vehiclePlate,
            ),
        )
    }

    private suspend fun buildDriverEvidenceManifestSha256(
        verification: DriverVerificationEntity,
        evidenceFiles: List<VerificationFileEvidence>,
    ): String = withContext(Dispatchers.IO) {
        val manifestDigest = MessageDigest.getInstance("SHA-256")
        fun append(value: String) {
            manifestDigest.update(value.toByteArray(Charsets.UTF_8))
            manifestDigest.update(0)
        }
        append("meet-rides-driver-evidence-v1")
        append(verification.fullName.trim())
        append(verification.dateOfBirth.trim())
        append(verification.vehicleMake.trim())
        append(verification.vehicleModel.trim())
        append(verification.vehicleYear.toString())
        append(verification.vehicleColor.trim())
        append(verification.vehiclePlate.trim())
        append(verification.vehicleSeats.toString())
        evidenceFiles.sortedBy(VerificationFileEvidence::label).forEach { evidence ->
            append(evidence.label)
            append(evidence.byteCount.toString())
            val file = File(evidence.path)
            val contentHash = if (file.isFile) {
                file.inputStream().buffered().use { input ->
                    val fileDigest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        fileDigest.update(buffer, 0, read)
                    }
                    fileDigest.digest().toHex()
                }
            } else {
                "missing"
            }
            append(contentHash)
        }
        manifestDigest.digest().toHex()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }

    fun submitPassengerVerification(
        fullName: String,
        phone: String,
        pathProfilePhoto: String,
        pathCedulaFront: String,
        pathSelfieWithCedula: String
    ) {
        viewModelScope.launch {
            val actorId = currentCloudUserId()
            if (actorId == null) {
                _rideVerificationNotice.emit(
                    "Inicia sesión antes de enviar tu verificación de pasajero.",
                )
                return@launch
            }
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
                Log.w("MeetRides", "Passenger verification rejected as incomplete: ${evidence.issues.joinToString()}")
                _rideVerificationNotice.emit(
                    "No se pudo habilitar el acceso: verifica tus datos y vuelve a capturar las tres fotos.",
                )
                return@launch
            }
            val entity = PassengerVerificationEntity(
                passengerId = actorId,
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
            val cloudUserId = currentCloudUserId()
            if (cloudUserId != null) {
                val evidenceFiles = listOf(
                    verificationFileEvidence("profile", pathProfilePhoto),
                    verificationFileEvidence("id_front", pathCedulaFront),
                    verificationFileEvidence("selfie_with_id", pathSelfieWithCedula),
                )
                val manifest = buildPassengerEvidenceManifestSha256(
                    fullName = fullName,
                    phone = phone,
                    evidenceFiles = evidenceFiles,
                )
                runCatching {
                    PlatformTrustCenterGateway.submit(
                        ServiceVerificationSubmission(
                            serviceType = "PASSENGER",
                            profileReference = "primary",
                            displayName = fullName,
                            phone = phone,
                            evidenceManifestSha256 = manifest,
                        ),
                    )
                }.onFailure {
                    Log.w("MeetTrustCenter", "Passenger review submission unavailable", it)
                    _rideVerificationNotice.emit(
                        "Registro local guardado; la revisión remota está pendiente de sincronización.",
                    )
                }
            }
            Log.i("MeetRides", "Passenger verification submitted; status=${verificationDecision.status}")
        }
    }

    private suspend fun buildPassengerEvidenceManifestSha256(
        fullName: String,
        phone: String,
        evidenceFiles: List<VerificationFileEvidence>,
    ): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        fun append(value: String) {
            digest.update(value.toByteArray(Charsets.UTF_8))
            digest.update(0)
        }
        append("meet-rides-passenger-evidence-v1")
        append(fullName.trim())
        append(phone.trim())
        evidenceFiles.sortedBy(VerificationFileEvidence::label).forEach { evidence ->
            append(evidence.label)
            append(evidence.byteCount.toString())
            val file = File(evidence.path)
            val contentHash = if (file.isFile) {
                file.inputStream().buffered().use { input ->
                    val fileDigest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        fileDigest.update(buffer, 0, read)
                    }
                    fileDigest.digest().toHex()
                }
            } else {
                "missing"
            }
            append(contentHash)
        }
        digest.digest().toHex()
    }

    fun deleteDriverVerification() {
        viewModelScope.launch {
            currentCloudUserId()?.let { rideDao.deleteDriverVerification(it) }
        }
    }

    fun deletePassengerVerification() {
        viewModelScope.launch {
            currentCloudUserId()?.let { rideDao.deletePassengerVerification(it) }
        }
    }

    fun isApprovedDriver(): Boolean {
        return RideVerificationPolicy.grantsAccess(driverVerification.value?.status)
    }

    fun isApprovedPassenger(): Boolean {
        return RideVerificationPolicy.grantsAccess(passengerVerification.value?.status)
    }

    fun refreshDemandSnapshot(h3R8: String) {
        viewModelScope.launch {
            demandGateway.getDemandSnapshot(h3R8).onSuccess { snapshot ->
                _currentDemandSnapshot.value = snapshot
            }
        }
    }

    fun loadDriverPublicProfile(driverId: String) {
        viewModelScope.launch {
            reputationGateway.getDriverPublicProfile(driverId).onSuccess { profile ->
                _currentDriverPublicProfile.value = profile
            }
        }
    }

    fun loadNextJobPrivacyProjection(nextTripId: String) {
        viewModelScope.launch {
            nextJobGateway.getPrivacyProjection(nextTripId).onSuccess { projection ->
                _currentNextJobProjection.value = projection
            }
        }
    }

    fun configureAutoMatch(
        requestId: String,
        strategy: RideAutoMatchStrategy,
        maxFareMinor: Long,
        minimumTrustTier: String = "VERIFIED",
        maximumEtaSeconds: Int = 600,
        allowFinishingPreviousTrip: Boolean = false,
    ) {
        viewModelScope.launch {
            val policy = RideAutoMatchPolicy(
                requestId = requestId,
                enabled = true,
                strategyRaw = strategy.id,
                maxFareMinor = maxFareMinor,
                minimumTrustTierRaw = minimumTrustTier,
                maximumEtaSeconds = maximumEtaSeconds,
                allowFinishingPreviousTrip = allowFinishingPreviousTrip,
            )
            autoMatchGateway.configurePolicy(policy)
        }
    }

    fun tryAutoMatch(requestId: String, expectedVersion: Long) {
        viewModelScope.launch {
            autoMatchGateway.tryAutoMatch(requestId, expectedVersion)
        }
    }

    fun attestPayment(tripId: String, newStatus: RidePaymentStatus, ref: String? = null) {
        viewModelScope.launch {
            paymentGateway.attestPaymentEvent(tripId, newStatus, ref)
        }
    }

    fun emitGuardianSafetySignal(
        tripId: String,
        signalType: SafetySignalType,
        severity: SafetySignalSeverity,
        details: String,
    ) {
        viewModelScope.launch {
            safetyGateway.emitSafetySignal(tripId, signalType, severity, details)
        }
    }
}
