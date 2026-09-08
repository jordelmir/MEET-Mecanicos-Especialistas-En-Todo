package com.elysium369.meet.ui.ridedelegate

import android.util.Log
import com.elysium369.meet.core.operations.ActiveOperation
import com.elysium369.meet.core.operations.ActiveOperationOwner
import com.elysium369.meet.core.operations.ActiveOperationRecoverability
import com.elysium369.meet.core.operations.ActiveOperationRegistry
import com.elysium369.meet.core.operations.ActiveOperationState
import com.elysium369.meet.data.local.dao.RideDao
import com.elysium369.meet.data.local.entities.ActiveRideSelectionEntity
import com.elysium369.meet.data.local.entities.RideOfferEntity
import com.elysium369.meet.data.local.entities.RideChatMessageEntity
import com.elysium369.meet.data.local.entities.RideRequestEntity
import com.elysium369.meet.platform.SupabaseManager
import com.elysium369.meet.ride.RideCommandRepository
import com.elysium369.meet.ride.presentation.RideProjectionConnectionState
import com.elysium369.meet.ride.presentation.RideProjectionRefreshResult
import com.elysium369.meet.ride.presentation.RideProjectionSyncPolicy
import com.elysium369.meet.ride.presentation.RideRemoteProjectionRepository
import com.elysium369.meet.ride.presentation.RideShareCategory
import com.elysium369.meet.ride.presentation.RideDriverVehicleSummary
import com.elysium369.meet.ride.presentation.RideRoadIncident
import com.elysium369.meet.ride.presentation.RideSegmentSpeedSample
import com.elysium369.meet.ride.presentation.RideVerificationNotice
import com.elysium369.meet.ride.liveshare.GpsTrailRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.StateFlow
import kotlinx.coroutines.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.kotlinx.datetime.Clock
import java.time.Instant
import java.util.UUID

/**
 * RideServiceDelegate — Extracted ride domain logic from ObdViewModel.
 *
 * This is a strangler-pattern intermediate step: the ride logic lives here
 * but ObdViewModel still exposes the same public API by delegating to this class.
 * Once the delegation is clean, RideServiceScreen can inject this directly.
 *
 * Dependencies that MUST come from ObdViewModel (not injectable alone):
 * - viewModelScope (CoroutineScope)
 * - currentCloudUserId() (authenticated user ID)
 * - activePrincipalKernel (multi-principal boundary)
 * - context (Android context for GPS trails)
 */
class RideServiceDelegate(
    private val scope: CoroutineScope,
    private val rideDao: RideDao,
    private val rideCommandRepository: RideCommandRepository,
    private val rideRemoteProjectionRepository: RideRemoteProjectionRepository,
    private val activeOperationsRegistry: ActiveOperationRegistry,
    private val currentCloudUserId: () -> String?,
    private val activePrincipalId: () -> String,
    private val onVerificationNotice: suspend (String) -> Unit,
) {

    // ─── Ride Requests ───

    val rideRequests = rideDao.getAllRequestsFlow()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val openRideRequests = rideDao.getOpenRequestsFlow()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeRideRequest = MutableStateFlow<RideRequestEntity?>(null)
    val activeRideRequest: StateFlow<RideRequestEntity?> = _activeRideRequest.asStateFlow()

    private val _rideOffers = MutableStateFlow<List<RideOfferEntity>>(emptyList())
    val rideOffers: StateFlow<List<RideOfferEntity>> = _rideOffers.asStateFlow()

    private val _rideChatMessages = MutableStateFlow<List<RideChatMessageEntity>>(emptyList())
    val rideChatMessages: StateFlow<List<RideChatMessageEntity>> = _rideChatMessages.asStateFlow()

    // ─── Driver Mode ───

    private val _rideDriverMode = MutableStateFlow(false)
    val rideDriverMode: StateFlow<Boolean> = _rideDriverMode.asStateFlow()
    private val _rideDriverVehicles = MutableStateFlow<List<RideDriverVehicleSummary>>(emptyList())
    val rideDriverVehicles: StateFlow<List<RideDriverVehicleSummary>> = _rideDriverVehicles.asStateFlow()

    // ─── Sharing & Incidents ───

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

    // ─── Feedback Flows ───

    private val _rideClaimFeedback = MutableSharedFlow<com.elysium369.meet.ui.RideClaimFeedback>(extraBufferCapacity = 8)
    val rideClaimFeedback: kotlinx.coroutines.flow.SharedFlow<com.elysium369.meet.ui.RideClaimFeedback> = _rideClaimFeedback.asSharedFlow()
    private val _ridePinFeedback = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val ridePinFeedback: kotlinx.coroutines.flow.SharedFlow<String> = _ridePinFeedback.asSharedFlow()
    private val _rideSafetyFeedback = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val rideSafetyFeedback: kotlinx.coroutines.flow.SharedFlow<String> = _rideSafetyFeedback.asSharedFlow()
    private val _rideRoadReportFeedback = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val rideRoadReportFeedback: kotlinx.coroutines.flow.SharedFlow<String> = _rideRoadReportFeedback.asSharedFlow()
    private val _rideSupportFeedback = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val rideSupportFeedback: kotlinx.coroutines.flow.SharedFlow<String> = _rideSupportFeedback.asSharedFlow()

    // ─── Driver Preset Messages ───

    private val _driverPresetMessages = MutableStateFlow(listOf(
        "Ya me encuentro en la ubicación",
        "Voy en camino, llego en unos 5 minutos",
        "Estoy parado en el semáforo/esquina",
        "Hola, ya inicié el viaje",
        "Estoy afuera con las luces intermitentes encendidas"
    ))
    val driverPresetMessages: StateFlow<List<String>> = _driverPresetMessages.asStateFlow()

    // ─── Projection Sync ───

    private var jobOffersCollection: Job? = null
    private var jobChatCollection: Job? = null
    private var jobChatRemoteSync: Job? = null
    private var rideProjectionJob: Job? = null
    private val _rideProjectionConnectionState =
        MutableStateFlow(RideProjectionConnectionState.IDLE)
    val rideProjectionConnectionState: StateFlow<RideProjectionConnectionState> =
        _rideProjectionConnectionState.asStateFlow()

    fun startRideProjectionSync() {
        if (rideProjectionJob?.isActive == true) return
        if (currentCloudUserId() == null) {
            _rideProjectionConnectionState.value =
                RideProjectionConnectionState.AUTHENTICATION_REQUIRED
            Log.d("MeetRides", "Ride projection deferred until authentication")
            return
        }
        val operationId = "ride-realtime-projection"
        val now = System.currentTimeMillis()
        activeOperationsRegistry.upsert(
            ActiveOperation(
                operationId = operationId,
                type = "RIDE_REALTIME_SYNC",
                vehicleId = null,
                startedAtEpochMs = now,
                state = ActiveOperationState.STARTING,
                progress = null,
                owner = ActiveOperationOwner.APPLICATION_SCOPED,
                recoverability = ActiveOperationRecoverability.ACTIVITY_RECREATION,
                lastHeartbeatEpochMs = now,
            ),
        )
        rideProjectionJob = scope.launch(Dispatchers.IO) {
            _rideProjectionConnectionState.value =
                RideProjectionConnectionState.CONNECTING
            val realtimeWakeUps = rideRemoteProjectionRepository
                .realtimeWakeUps()
                .onEach {
                    _rideProjectionConnectionState.value =
                        RideProjectionConnectionState.LIVE
                    activeOperationsRegistry.heartbeat(operationId, ActiveOperationState.RUNNING)
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
                        activeOperationsRegistry.heartbeat(operationId, ActiveOperationState.RETRYING)
                        Log.w("MeetRides", "Realtime wake-up interrupted; reconnecting in ${delayMs}ms", error)
                        delay(delayMs)
                        _rideProjectionConnectionState.value =
                            RideProjectionConnectionState.CONNECTING
                        true
                    }
                }
            val foregroundHeartbeat = kotlinx.coroutines.flow.flow {
                while (kotlinx.coroutines.currentCoroutineContext().isActive) {
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
        activeOperationsRegistry.complete("ride-realtime-projection")
    }

    fun refreshRideProjectionNow() {
        scope.launch(Dispatchers.IO) {
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

    // ─── Driver Mode ───

    fun toggleRideDriverMode() {
        _rideDriverMode.value = !_rideDriverMode.value
    }

    fun setRideDriverMode(enabled: Boolean) {
        _rideDriverMode.value = enabled
    }

    fun recordDriverLiveness(evidenceSha256: String, capturedAtEpochMs: Long) {
        if (!evidenceSha256.matches(Regex("[0-9a-f]{64}"))) return
        scope.launch(Dispatchers.IO) {
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
                onVerificationNotice("Presencia validada en el dispositivo; la nube la confirmará al recuperar conexión.")
            }
        }
    }

    fun refreshRideDriverVehicles() {
        scope.launch(Dispatchers.IO) {
            val userId = currentCloudUserId() ?: return@launch
            runCatching {
                SupabaseManager.client.postgrest["ride_driver_vehicles"]
                    .select {
                        filter { eq("driver_id", userId) }
                    }
                    .decodeList<com.elysium369.meet.ui.RemoteRideDriverVehicleSummary>()
                    .map(com.elysium369.meet.ui.RemoteRideDriverVehicleSummary::toDomain)
            }.onSuccess { _rideDriverVehicles.value = it }
                .onFailure { Log.w("MeetRides", "Vehicle fleet refresh failed", it) }
        }
    }

    fun addRideDriverVehicle(
        make: String, model: String, year: Int, color: String,
        plate: String, fleetName: String?,
    ) {
        val normalized = listOf(make, model, color, plate).map(String::trim)
        if (normalized.any(String::isBlank) || year !in 1900..2200) return
        scope.launch(Dispatchers.IO) {
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
                .onFailure { onVerificationNotice("No se pudo guardar el vehículo en la nube.") }
        }
    }

    fun activateRideDriverVehicle(vehicleId: String) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                SupabaseManager.client.postgrest.rpc(
                    "ride_set_active_vehicle_v1",
                    buildJsonObject { put("p_vehicle_id", vehicleId) },
                )
            }.onSuccess { refreshRideDriverVehicles() }
                .onFailure { onVerificationNotice("Sólo puedes activar un vehículo verificado.") }
        }
    }

    // ─── Active Ride Selection ───

    fun selectActiveRide(request: RideRequestEntity?) {
        val ownerId = activePrincipalId()
        scope.launch(Dispatchers.IO) {
            if (request == null) {
                rideDao.clearActiveRideSelection(ownerId)
            } else {
                rideDao.upsertActiveRideSelection(
                    ActiveRideSelectionEntity(
                        ownerPrincipalId = ownerId,
                        rideRequestId = request.requestId,
                        updatedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
            }
        }
        applyActiveRide(request)
    }

    private fun applyActiveRide(request: RideRequestEntity?) {
        _activeRideRequest.value = request
        jobOffersCollection?.cancel()
        jobChatCollection?.cancel()
        jobChatRemoteSync?.cancel()

        if (request != null) {
            _rideSharingSelections.update { current ->
                if (request.requestId in current) current
                else current + (request.requestId to setOf(RideShareCategory.EXACT_LOCATION))
            }
            jobOffersCollection = scope.launch {
                rideDao.getOffersForRequest(request.requestId).collect {
                    _rideOffers.value = it
                }
            }
            jobChatCollection = scope.launch {
                rideDao.getChatMessagesFlow(request.requestId).collect {
                    _rideChatMessages.value = it
                }
            }
            jobChatRemoteSync = scope.launch(Dispatchers.IO) {
                while (isActive) {
                    syncRideChat(request.requestId)
                    delay(4_000)
                }
            }
        } else {
            _rideOffers.value = emptyList()
            _rideChatMessages.value = emptyList()
        }
    }

    fun initActiveRideTracking(activePrincipalFlow: kotlinx.coroutines.flow.Flow<String>) {
        scope.launch {
            activePrincipalFlow
                .distinctUntilChanged()
                .collectLatest { ownerId ->
                    applyActiveRide(null)
                    val selection = withContext(Dispatchers.IO) {
                        rideDao.getActiveRideSelection(ownerId)
                    } ?: return@collectLatest
                    val request = withContext(Dispatchers.IO) {
                        rideDao.getRequestById(selection.rideRequestId)
                    }
                    if (request == null) {
                        Log.w("MeetRides", "Active ride unavailable locally; durable pointer retained")
                    } else {
                        applyActiveRide(request)
                    }
                }
        }
    }

    private suspend fun syncRideChat(requestId: String) {
        try {
            val userId = currentCloudUserId() ?: return
            val remoteMessages = SupabaseManager.client.postgrest["ride_chat_messages"]
                .select {
                    filter { eq("ride_request_id", requestId) }
                    order("created_at", kotlinx.serialization.json.buildJsonArray {})
                }
                .decodeList<RemoteRideChatMessage>()
            for (msg in remoteMessages) {
                rideDao.upsertChatMessage(
                    RideChatMessageEntity(
                        messageId = msg.messageId,
                        rideRequestId = requestId,
                        senderPrincipalId = msg.senderPrincipalId,
                        senderName = msg.senderName,
                        body = msg.body,
                        createdAtEpochMs = msg.createdAtEpochMs,
                        synced = true,
                    )
                )
            }
        } catch (e: Exception) {
            Log.d("MeetRides", "Chat sync skipped: ${e.message}")
        }
    }

    fun setRideShareCategory(requestId: String, category: RideShareCategory, enabled: Boolean) {
        _rideSharingSelections.update { current ->
            val existing = current[requestId] ?: emptySet()
            val updated = if (enabled) existing + category else existing - category
            current + (requestId to updated)
        }
    }

    fun cleanup() {
        stopRideProjectionSync()
        jobOffersCollection?.cancel()
        jobChatCollection?.cancel()
        jobChatRemoteSync?.cancel()
    }
}

@kotlinx.serialization.Serializable
private data class RemoteRideChatMessage(
    val message_id: String = "",
    val ride_request_id: String = "",
    val sender_principal_id: String = "",
    val sender_name: String = "",
    val body: String = "",
    val created_at_epoch_ms: Long = 0L,
)
