package com.elysium369.meet.ride.driver

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.data.local.dao.RideDao
import com.elysium369.meet.data.local.entities.RideRequestEntity
import com.elysium369.meet.data.remote.SupabaseModule
import com.elysium369.meet.ride.application.RideCommandBus
import com.elysium369.meet.ride.application.RideCommandEnqueueResult
import com.elysium369.meet.ride.data.remote.RideCommandPayload
import com.elysium369.meet.ride.data.remote.RideQueuedCommand
import com.elysium369.meet.ride.domain.RideCommandType
import com.elysium369.meet.ride.domain.RideStopSnapshot
import com.elysium369.meet.ride.map.RerouteDetector
import com.elysium369.meet.ride.map.ReroutePolicy
import com.elysium369.meet.ride.map.RideGeoPoint
import com.elysium369.meet.ride.map.RideRoadRoute
import com.elysium369.meet.ride.map.RideRouteMatcher
import com.elysium369.meet.ride.map.RideRoutingProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.postgrest.postgrest
import java.math.BigDecimal
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

interface MonotonicClock {
    fun nowNanos(): Long
    fun nowEpochMs(): Long
}

class SystemMonotonicClock : MonotonicClock {
    override fun nowNanos(): Long = SystemClock.elapsedRealtimeNanos()
    override fun nowEpochMs(): Long = System.currentTimeMillis()
}

@HiltViewModel
class DriverActiveTripViewModel @Inject constructor(
    private val rideDao: RideDao,
    private val commandBus: RideCommandBus,
    private val routingProvider: RideRoutingProvider,
    @ApplicationContext private val context: Context? = null,
) : ViewModel() {

    internal var clock: MonotonicClock = SystemMonotonicClock()
    internal var arrivalPolicy: ArrivalPolicy = ArrivalPolicy()
    internal var reroutePolicy: ReroutePolicy = ReroutePolicy()

    private val commandMutex = Mutex()
    private val routeMatcher = RideRouteMatcher()
    private val rerouteDetector by lazy { RerouteDetector(reroutePolicy) }

    private val _state = MutableStateFlow(DriverTripUiState())
    val state: StateFlow<DriverTripUiState> = _state.asStateFlow()

    private var currentRideId: String = ""
    private var routeGeneration: Long = 0L

    fun initialize(rideId: String, initialEntity: RideRequestEntity? = null) {
        if (currentRideId == rideId && _state.value.rideId == rideId) return
        currentRideId = rideId
        routeMatcher.reset()
        rerouteDetector.reset()

        if (initialEntity != null) {
            applyEntityUpdate(initialEntity)
        }

        viewModelScope.launch {
            rideDao.observeRequest(rideId)
                .collect { entity ->
                    if (entity != null) {
                        applyEntityUpdate(entity)
                    }
                }
        }
    }

    private fun applyEntityUpdate(entity: RideRequestEntity) {
        val previousPhase = _state.value.phase
        val canonicalServerState = entity.serverState ?: entity.status
        val newPhase = canonicalServerState.toDriverTripPhase()

        val parsedStops = runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString<List<RideStopSnapshot>>(entity.stopsJson)
                .mapNotNull {
                    val lat = it.latitude ?: return@mapNotNull null
                    val lng = it.longitude ?: return@mapNotNull null
                    RideGeoPoint(
                        latitude = lat,
                        longitude = lng,
                        accuracyMeters = null,
                        capturedAtEpochMs = 0L,
                    )
                }
        }.getOrDefault(emptyList())

        val pickup = RideGeoPoint(
            latitude = entity.pickupLatitude,
            longitude = entity.pickupLongitude,
            accuracyMeters = entity.pickupAccuracy,
            capturedAtEpochMs = entity.createdAt,
        )

        val destination = RideGeoPoint(
            latitude = entity.destLatitude,
            longitude = entity.destLongitude,
            accuracyMeters = null,
            capturedAtEpochMs = entity.createdAt,
        )

        val pendingResolved = if (entity.syncState != "COMMAND_PENDING" && entity.serverVersion > _state.value.serverVersion) {
            null
        } else {
            _state.value.pendingCommand
        }

        _state.update { current ->
            current.copy(
                rideId = entity.requestId,
                serverVersion = entity.serverVersion,
                serverState = canonicalServerState,
                phase = newPhase,
                passengerName = entity.passengerName.takeIf { it.isNotBlank() } ?: "Pasajero",
                pickup = pickup,
                pickupAddress = entity.pickupAddress,
                destination = destination,
                destinationAddress = entity.destAddress,
                stops = parsedStops,
                pendingCommand = pendingResolved,
                projectionFresh = true,
                driverArrivedAtEpochMs = entity.driverArrivedAt,
            )
        }

        if (previousPhase != newPhase) {
            requestRouteForCurrentPhase()
        }
    }

    fun dispatch(intent: DriverTripIntent) {
        when (intent) {
            DriverTripIntent.StartDrivingToPickup -> handleStartDrivingToPickup()
            DriverTripIntent.ConfirmArrival -> handleConfirmArrival()
            DriverTripIntent.OpenBoardingPin -> _state.update { it.copy(showPinDialog = true) }
            DriverTripIntent.DismissBoardingPin -> _state.update { it.copy(showPinDialog = false, boardingPinInput = "") }
            is DriverTripIntent.UpdateBoardingPinInput -> _state.update { it.copy(boardingPinInput = intent.pin) }
            is DriverTripIntent.SubmitBoardingPin -> handleSubmitBoardingPin(intent.pin)
            DriverTripIntent.StartTrip -> handleStartTrip()
            DriverTripIntent.CompleteTrip -> handleCompleteTrip()
            DriverTripIntent.ToggleAcceptingFutureOffers -> handleToggleFutureOffers()
            is DriverTripIntent.CancelTrip -> handleCancelTrip(intent.reasonCode, intent.detail)
            is DriverTripIntent.OpenSupport -> handleOpenSupport(intent.category, intent.summary)
            DriverTripIntent.CreateSafetyShare -> handleCreateSafetyShare()
            DriverTripIntent.RecenterMap -> _state.update { it.copy(cameraMode = NavigationCameraMode.FOLLOWING) }
            DriverTripIntent.UserMovedMap -> _state.update { it.copy(cameraMode = NavigationCameraMode.USER_CONTROLLED) }
            is DriverTripIntent.ToggleDetailsSheet -> _state.update { it.copy(activeDetailsSheet = intent.show) }
            DriverTripIntent.DismissUserMessage -> _state.update { it.copy(userMessage = null) }
            is DriverTripIntent.UpdateDriverLocation -> handleLocationUpdate(intent.sample)
        }
    }

    private suspend fun submitExclusive(operation: suspend () -> Unit) {
        if (!commandMutex.tryLock()) {
            return
        }
        try {
            operation()
        } finally {
            commandMutex.unlock()
        }
    }

    private fun handleStartDrivingToPickup() {
        viewModelScope.launch {
            submitExclusive {
                val snapshot = _state.value
                if (snapshot.phase != DriverTripPhase.Assigned) return@submitExclusive

                val command = RideQueuedCommand(
                    rideId = snapshot.rideId,
                    expectedVersion = snapshot.serverVersion,
                    idempotencyKey = UUID.randomUUID().toString(),
                    type = RideCommandType.DRIVER_EN_ROUTE,
                    payloadVersion = 1,
                    payload = RideCommandPayload(),
                )

                enqueueCommand(command, "Iniciando ruta a recogida…")
            }
        }
    }

    private fun handleConfirmArrival() {
        viewModelScope.launch {
            submitExclusive {
                val snapshot = _state.value
                if (snapshot.phase != DriverTripPhase.ToPickup) return@submitExclusive

                val location = snapshot.driverLocation
                if (location == null) {
                    _state.update { it.copy(userMessage = "Esperando señal GPS precisa") }
                    return@submitExclusive
                }

                val pickup = snapshot.pickup
                if (pickup == null) {
                    _state.update { it.copy(userMessage = "Punto de recogida no disponible") }
                    return@submitExclusive
                }

                when (val preflight = DriverArrivalPreflight.evaluate(
                    location = location,
                    pickup = pickup,
                    nowElapsedRealtimeNanos = clock.nowNanos(),
                    policy = arrivalPolicy,
                )) {
                    ArrivalPreflightResult.Allowed -> Unit
                    is ArrivalPreflightResult.StaleLocation -> {
                        _state.update { it.copy(userMessage = "Actualizando tu ubicación GPS…") }
                        return@submitExclusive
                    }
                    is ArrivalPreflightResult.PoorAccuracy -> {
                        _state.update { it.copy(userMessage = "La precisión GPS (${preflight.accuracyMeters.toInt()} m) aún no es suficiente") }
                        return@submitExclusive
                    }
                    is ArrivalPreflightResult.TooFar -> {
                        _state.update { it.copy(userMessage = "Aún estás lejos del punto de recogida (${preflight.distanceMeters.toInt()} m)") }
                        return@submitExclusive
                    }
                }

                val command = buildDriverArrivedCommand(
                    rideId = snapshot.rideId,
                    expectedVersion = snapshot.serverVersion,
                    location = location,
                    idempotencyKey = UUID.randomUUID().toString(),
                )

                enqueueCommand(command, "Confirmando llegada con el servidor…")
            }
        }
    }

    private fun handleSubmitBoardingPin(rawPin: String) {
        val pin = rawPin.trim()
        if (!Regex("^\\d{4}$").matches(pin)) {
            _state.update { it.copy(userMessage = "El PIN debe tener 4 dígitos numéricos") }
            return
        }

        viewModelScope.launch {
            submitExclusive {
                val snapshot = _state.value
                if (snapshot.phase != DriverTripPhase.AtPickup) return@submitExclusive

                val command = RideQueuedCommand(
                    rideId = snapshot.rideId,
                    expectedVersion = snapshot.serverVersion,
                    idempotencyKey = UUID.randomUUID().toString(),
                    type = RideCommandType.VERIFY_BOARDING_PIN,
                    payloadVersion = 1,
                    payload = RideCommandPayload(boardingPin = pin),
                )

                _state.update { it.copy(showPinDialog = false, boardingPinInput = "") }
                enqueueCommand(command, "Verificando PIN de abordaje…")
            }
        }
    }

    private fun handleStartTrip() {
        viewModelScope.launch {
            submitExclusive {
                val snapshot = _state.value
                if (snapshot.phase != DriverTripPhase.PassengerOnboard) return@submitExclusive

                val command = RideQueuedCommand(
                    rideId = snapshot.rideId,
                    expectedVersion = snapshot.serverVersion,
                    idempotencyKey = UUID.randomUUID().toString(),
                    type = RideCommandType.START,
                    payloadVersion = 1,
                    payload = RideCommandPayload(),
                )

                enqueueCommand(command, "Iniciando viaje…")
            }
        }
    }

    private fun handleCompleteTrip() {
        viewModelScope.launch {
            submitExclusive {
                val snapshot = _state.value
                if (snapshot.phase != DriverTripPhase.InProgress) return@submitExclusive

                val destination = snapshot.destination
                val location = snapshot.driverLocation
                if (destination != null && location != null) {
                    val dist = DriverArrivalPreflight.haversineMeters(
                        location.latitude,
                        location.longitude,
                        destination.latitude,
                        destination.longitude,
                    )
                    if (dist > 250.0) {
                        _state.update { it.copy(userMessage = "Aún estás a ${dist.toInt()} m del destino final") }
                        return@submitExclusive
                    }
                }

                val command = RideQueuedCommand(
                    rideId = snapshot.rideId,
                    expectedVersion = snapshot.serverVersion,
                    idempotencyKey = UUID.randomUUID().toString(),
                    type = RideCommandType.COMPLETE,
                    payloadVersion = 1,
                    payload = RideCommandPayload(),
                )

                enqueueCommand(command, "Finalizando viaje y liquidando…")
            }
        }
    }

    private fun handleToggleFutureOffers() {
        _state.update { current ->
            val updated = !current.acceptingFutureOffers
            current.copy(
                acceptingFutureOffers = updated,
                userMessage = if (updated) "Aceptando solicitudes futuras" else "Pausadas nuevas solicitudes durante el viaje",
            )
        }
    }

    private fun handleCancelTrip(reasonCode: String, detail: String) {
        viewModelScope.launch {
            submitExclusive {
                val snapshot = _state.value
                val command = RideQueuedCommand(
                    rideId = snapshot.rideId,
                    expectedVersion = snapshot.serverVersion,
                    idempotencyKey = UUID.randomUUID().toString(),
                    type = RideCommandType.CANCEL,
                    payloadVersion = 1,
                    payload = RideCommandPayload(
                        reasonCode = reasonCode,
                        detail = detail.takeIf { it.isNotBlank() },
                    ),
                )

                enqueueCommand(command, "Cancelando viaje…")
            }
        }
    }

    private fun handleOpenSupport(category: String, summary: String) {
        viewModelScope.launch {
            submitExclusive {
                val snapshot = _state.value
                val command = RideQueuedCommand(
                    rideId = snapshot.rideId,
                    expectedVersion = snapshot.serverVersion,
                    idempotencyKey = UUID.randomUUID().toString(),
                    type = RideCommandType.OPEN_SUPPORT_CASE,
                    payloadVersion = 1,
                    payload = RideCommandPayload(
                        supportCategory = category,
                        supportSummary = summary,
                    ),
                )

                enqueueCommand(command, "Enviando reporte a soporte…")
            }
        }
    }

    private fun handleCreateSafetyShare() {
        viewModelScope.launch {
            val snapshot = _state.value
            if (snapshot.rideId.isBlank()) return@launch

            val randomBytes = ByteArray(32)
            SecureRandom().nextBytes(randomBytes)
            val rawToken = randomBytes.joinToString("") { "%02x".format(it) }

            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(rawToken.toByteArray(Charsets.UTF_8))
            val hashHex = "\\x" + hashBytes.joinToString("") { "%02x".format(it) }

            val shareUrl = "https://meet.cr/trip-share/$rawToken"

            withContext(Dispatchers.IO) {
                runCatching {
                    SupabaseModule.client.postgrest.from("ride_share_sessions").insert(
                        mapOf(
                            "ride_id" to snapshot.rideId,
                            "token_hash" to hashHex,
                            "expires_at" to Instant.now().plusSeconds(7200).toString(),
                        )
                    )
                }
            }

            _state.update {
                it.copy(
                    safetyShareUrl = shareUrl,
                    userMessage = "Enlace de viaje seguro generado",
                )
            }
        }
    }

    private suspend fun enqueueCommand(command: RideQueuedCommand, pendingMessage: String) {
        _state.update { it.copy(pendingCommand = command.type, userMessage = pendingMessage) }
        when (val res = commandBus.enqueue(command)) {
            RideCommandEnqueueResult.Enqueued,
            RideCommandEnqueueResult.AlreadyQueued -> Unit
            is RideCommandEnqueueResult.Rejected -> {
                _state.update {
                    it.copy(
                        pendingCommand = null,
                        userMessage = res.message,
                    )
                }
            }
        }
    }

    private fun handleLocationUpdate(sample: DriverLocationSample) {
        _state.update { it.copy(driverLocation = sample) }

        val currentRoute = _state.value.route
        if (currentRoute == null || currentRoute.geometry.size < 2) return

        val locationPoint = sample.toGeoPoint()
        val match = routeMatcher.match(currentRoute.geometry, locationPoint)

        if (rerouteDetector.evaluate(match, sample.accuracyMeters, clock.nowEpochMs())) {
            requestRouteForCurrentPhase()
            return
        }

        if (match != null) {
            val remainingDist = routeMatcher.remainingDistanceMeters(currentRoute.geometry, match).toLong()
            val nextManeuverPair = routeMatcher.nextManeuver(currentRoute, match)

            val speedMps = (currentRoute.distanceMeters / currentRoute.durationSeconds.coerceAtLeast(1.0)).coerceAtLeast(4.0)
            val remainingSec = (remainingDist / speedMps).toLong()

            val guidance = nextManeuverPair?.let { (maneuver, distToManeuver) ->
                NavigationGuidance(
                    maneuverType = maneuver.type,
                    maneuverModifier = maneuver.modifier,
                    streetName = maneuver.streetName,
                    distanceToManeuverMeters = distToManeuver,
                    remainingDistanceMeters = remainingDist,
                    remainingDurationSeconds = remainingSec,
                )
            }

            _state.update {
                it.copy(
                    navigation = guidance,
                    remainingDistanceMeters = remainingDist,
                    remainingDurationSeconds = remainingSec,
                )
            }
        }
    }

    private fun requestRouteForCurrentPhase() {
        val snapshot = _state.value
        val driverLoc = snapshot.driverLocation?.toGeoPoint() ?: return

        val targetWaypoints = when (snapshot.phase) {
            DriverTripPhase.Assigned,
            DriverTripPhase.ToPickup -> {
                val pickup = snapshot.pickup ?: return
                listOf(driverLoc, pickup)
            }
            DriverTripPhase.PassengerOnboard,
            DriverTripPhase.InProgress -> {
                val destination = snapshot.destination ?: return
                buildList {
                    add(driverLoc)
                    addAll(snapshot.stops)
                    add(destination)
                }
            }
            else -> return
        }

        val currentGen = ++routeGeneration
        viewModelScope.launch {
            val routeResult = runCatching {
                withContext(Dispatchers.IO) {
                    routingProvider.route(targetWaypoints)
                }
            }.getOrNull()

            if (routeResult != null && routeGeneration == currentGen) {
                routeMatcher.reset()
                rerouteDetector.reset()
                _state.update {
                    it.copy(
                        route = routeResult,
                        remainingDistanceMeters = routeResult.distanceMeters.toLong(),
                        remainingDurationSeconds = routeResult.durationSeconds.toLong(),
                    )
                }
            }
        }
    }

    private fun buildDriverArrivedCommand(
        rideId: String,
        expectedVersion: Long,
        location: DriverLocationSample,
        idempotencyKey: String,
    ): RideQueuedCommand = RideQueuedCommand(
        rideId = rideId,
        expectedVersion = expectedVersion,
        idempotencyKey = idempotencyKey,
        type = RideCommandType.DRIVER_ARRIVED,
        payloadVersion = 1,
        payload = RideCommandPayload(
            driverLatitude = BigDecimal.valueOf(location.latitude).stripTrailingZeros().toPlainString(),
            driverLongitude = BigDecimal.valueOf(location.longitude).stripTrailingZeros().toPlainString(),
            driverAccuracyMeters = BigDecimal.valueOf(location.accuracyMeters).stripTrailingZeros().toPlainString(),
            driverCapturedAt = location.capturedAt.toString(),
        ),
    )

    private fun DriverLocationSample.toGeoPoint(): RideGeoPoint = RideGeoPoint(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters.toFloat(),
        capturedAtEpochMs = capturedAt.toEpochMilli(),
    )
}
