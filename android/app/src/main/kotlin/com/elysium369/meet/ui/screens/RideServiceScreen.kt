package com.elysium369.meet.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import coil.compose.AsyncImage
import com.elysium369.meet.data.local.entities.RideChatMessageEntity
import com.elysium369.meet.data.local.entities.RideOfferEntity
import com.elysium369.meet.data.local.entities.RideRequestEntity
import com.elysium369.meet.BuildConfig
import com.elysium369.meet.ride.domain.RidePaymentMethod
import com.elysium369.meet.ride.domain.RideFareBidPolicy
import com.elysium369.meet.ride.domain.RideFareEngine
import com.elysium369.meet.ride.domain.RideFareMode
import com.elysium369.meet.ride.domain.RideActorRole
import com.elysium369.meet.ride.domain.RideArrivalPolicy
import com.elysium369.meet.ride.domain.RideStopSnapshot
import com.elysium369.meet.ride.domain.RideTripPlanPolicy
import com.elysium369.meet.ride.map.RidePlaceSearchProvider
import com.elysium369.meet.ride.map.RidePlaceSuggestion
import com.elysium369.meet.ride.map.distanceKmFrom
import com.elysium369.meet.ride.map.RideSavedPlace
import com.elysium369.meet.ride.map.RideSavedPlacesStore
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ride.map.RideGeoPoint
import com.elysium369.meet.ride.map.RideMapStateFactory
import com.elysium369.meet.ride.map.RideMapMarker
import com.elysium369.meet.ride.map.RideMarkerRole
import com.elysium369.meet.ride.map.RideRoadRoute
import com.elysium369.meet.ride.map.RideMapDataSource
import com.elysium369.meet.ride.map.resilientRidePlaceSearchProvider
import com.elysium369.meet.ride.map.resilientRideRoutingProvider
import com.elysium369.meet.ride.domain.RideVerificationPolicy
import com.elysium369.meet.ride.domain.RideDriverPresencePolicy
import com.elysium369.meet.ride.data.RideProjectionConnectionState
import com.elysium369.meet.ride.traffic.RideRoadIncidentType
import com.elysium369.meet.ride.traffic.RideRoadSide
import com.elysium369.meet.ride.traffic.RideRoadReportAvailabilityPolicy
import com.elysium369.meet.ride.traffic.RideCollaborativeEtaEstimator
import com.elysium369.meet.ride.traffic.RideEtaEvidenceLevel
import com.elysium369.meet.ride.traffic.RideEtaSegment
import com.elysium369.meet.ride.notification.RideNotificationCoordinator
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideServiceScreen(
    viewModel: ObdViewModel,
    prefilledVehicleInfo: String? = null,
    onNavigateBack: () -> Unit = {},
    onOpenDriverRegistration: () -> Unit = {},
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.rideVerificationNotice.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Permissions check
    val permissionsToRequest = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (fineLocationGranted) {
            viewModel.detectCurrentLocation(context)
        } else {
            Toast.makeText(context, "Se requieren permisos de ubicación para la precisión GPS", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        permissionsLauncher.launch(permissionsToRequest)
        viewModel.detectCurrentLocation(context)
        viewModel.refreshOwnTrustDecisions()
        viewModel.startRideProjectionSync()
    }
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.stopRideProjectionSync()
        }
    }

    val driverMode by viewModel.rideDriverMode.collectAsState()
    val activeRide by viewModel.activeRideRequest.collectAsState()
    val projectionConnectionState by viewModel.rideProjectionConnectionState.collectAsState()
    val driverVerification by viewModel.driverVerification.collectAsState()
    val passengerVerification by viewModel.passengerVerification.collectAsState()
    val passengerRegistrationMissing = passengerVerification == null
    val driverRegistrationMissing = driverVerification == null
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var profileInitialTab by rememberSaveable { mutableIntStateOf(0) }
    var showRideMenu by remember { mutableStateOf(false) }
    var firstAccessRole by rememberSaveable { mutableStateOf<String?>(null) }
    val presencePreferences = remember(context) {
        context.getSharedPreferences("elysium_ride_driver_presence", Context.MODE_PRIVATE)
    }
    var showLiveness by rememberSaveable { mutableStateOf(false) }

    val openPassengerRegistration: () -> Unit = {
        if (driverMode) viewModel.toggleRideDriverMode()
        showProfile = false
        firstAccessRole = "PASSENGER"
    }

    LaunchedEffect(driverMode, driverVerification?.status) {
        if (
            driverMode &&
            RideVerificationPolicy.grantsAccess(driverVerification?.status) &&
            RideDriverPresencePolicy.requiresChallenge(
                lastVerifiedAtEpochMs = presencePreferences
                    .getLong("last_verified_at", 0L)
                    .takeIf { it > 0L },
                nowEpochMs = System.currentTimeMillis(),
            )
        ) {
            showLiveness = true
        }
    }

    if (showLiveness) {
        RideLivenessDialog(
            onVerified = { evidenceHash ->
                val now = System.currentTimeMillis()
                presencePreferences.edit { putLong("last_verified_at", now) }
                viewModel.recordDriverLiveness(evidenceHash, now)
                showLiveness = false
            },
            onCancel = {
                showLiveness = false
                if (driverMode) viewModel.toggleRideDriverMode()
            },
        )
    }

    LaunchedEffect(Unit) {
        viewModel.voiceFeedbackManager.speak(
            es = "Bienvenido a Elysium Viajes y Movilidad Segura. Puedes solicitar un viaje con tarifa transparente y conductores verificados.",
            en = "Welcome to Elysium Rides and Mobility. Request a safe ride with transparent fares and verified drivers."
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "ELYSIUM VANGUARD · VIAJES",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    Box {
                        IconButton(onClick = { showRideMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menú de Viajes",
                                tint = MeetColors.cyberCyan,
                            )
                        }
                        DropdownMenu(
                            expanded = showRideMenu,
                            onDismissRequest = { showRideMenu = false },
                            containerColor = Color(0xFF07131E),
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (passengerVerification == null) {
                                            "Registrarme como usuario"
                                        } else {
                                            "Cuenta de pasajero"
                                        },
                                        color = Color.White,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.PersonAdd,
                                        contentDescription = null,
                                        tint = MeetColors.neonGreen,
                                    )
                                },
                                onClick = {
                                    showRideMenu = false
                                    if (passengerVerification == null) {
                                        openPassengerRegistration()
                                    } else {
                                        if (driverMode) viewModel.toggleRideDriverMode()
                                        profileInitialTab = 0
                                        showProfile = true
                                        firstAccessRole = null
                                    }
                                },
                            )
                            listOf(
                                Triple(Icons.Default.Person, "Perfil", 0),
                                Triple(Icons.Default.History, "Historial de viajes", 1),
                                Triple(Icons.Default.SupportAgent, "Soporte", 2),
                                Triple(Icons.Default.LocationOn, "Iconos del mapa", 3),
                            ).forEach { (icon, label, destinationTab) ->
                                DropdownMenuItem(
                                    text = { Text(label, color = Color.White) },
                                    leadingIcon = { Icon(icon, null, tint = MeetColors.cyberCyan) },
                                    onClick = {
                                        profileInitialTab = destinationTab
                                        showProfile = true
                                        showRideMenu = false
                                    },
                                )
                            }
                            if (driverMode) {
                                HorizontalDivider(color = MeetColors.borderSubtle)
                                DropdownMenuItem(
                                    text = { Text("Autos y flotillas", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Default.DirectionsCar, null, tint = MeetColors.neonGreen) },
                                    onClick = {
                                        profileInitialTab = 0
                                        showProfile = true
                                        showRideMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Ganancias", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Default.Payments, null, tint = MeetColors.neonGreen) },
                                    onClick = {
                                        profileInitialTab = 0
                                        showProfile = true
                                        showRideMenu = false
                                    },
                                )
                            }
                            HorizontalDivider(color = MeetColors.borderSubtle)
                            DropdownMenuItem(
                                text = { Text("Volver a PRO", color = MeetColors.textMuted) },
                                leadingIcon = { Icon(Icons.Default.ArrowBack, null) },
                                onClick = {
                                    showRideMenu = false
                                    onNavigateBack()
                                },
                            )
                        }
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        IconButton(onClick = {
                            viewModel.voiceFeedbackManager.speak(
                                es = "Elysium Viajes: Monitoreo de seguridad satelital, telemetría y subasta de tarifas en tiempo real.",
                                en = "Elysium Rides: Real-time satellite security tracking and transparent fare bidding."
                            )
                        }) {
                            Icon(Icons.Default.VolumeUp, "Voz Asistente", tint = MeetColors.neonGreen)
                        }
                        Text(
                            text = projectionConnectionState.rideProjectionStatusLabel(),
                            color = projectionConnectionState.rideProjectionStatusColor(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        Text(
                            text = if (driverMode) "Modo Chofer" else "Modo Pasajero",
                            color = if (driverMode) MeetColors.cyberCyan else MeetColors.neonGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Switch(
                            checked = driverMode,
                            onCheckedChange = { viewModel.toggleRideDriverMode() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MeetColors.cyberCyan,
                                checkedTrackColor = MeetColors.cyberCyan.copy(alpha = 0.5f),
                                uncheckedThumbColor = MeetColors.neonGreen,
                                uncheckedTrackColor = MeetColors.neonGreen.copy(alpha = 0.5f)
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MeetColors.backgroundDark
                )
            )
        },
        containerColor = MeetColors.backgroundDark
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (requiresRideRoleRegistration(
                    driverMode = driverMode,
                    passengerRegistrationExists = !passengerRegistrationMissing,
                    driverRegistrationExists = !driverRegistrationMissing,
                ) && firstAccessRole == null
            ) {
                RideFirstAccessGateway(
                    onPassengerRegistration = openPassengerRegistration,
                    onDriverRegistration = onOpenDriverRegistration,
                )
            } else if (showProfile) {
                RideProfileScreen(
                    viewModel = viewModel,
                    isDriver = driverMode,
                    initialTab = profileInitialTab,
                    onBack = { showProfile = false },
                )
            } else if (activeRide != null) {
                // Si hay un viaje activo, mostrar la pantalla de viaje activo con el chat
                ActiveRidePanel(
                    viewModel = viewModel,
                    ride = activeRide!!,
                    isDriver = driverMode,
                    onCloseRide = { viewModel.selectActiveRide(null) }
                )
            } else {
                if (driverMode) {
                    DriverDashboard(
                        viewModel = viewModel,
                        onRegisterDriver = onOpenDriverRegistration,
                    )
                } else {
                    PassengerDashboard(
                        viewModel = viewModel,
                        forceRegistration = firstAccessRole == "PASSENGER",
                    )
                }
            }
        }
    }
}

@Composable
private fun RideFirstAccessGateway(
    onPassengerRegistration: () -> Unit,
    onDriverRegistration: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF030712), Color(0xFF071527), Color(0xFF09051A)),
                ),
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xD90A1726)),
            border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = .58f)),
            shape = RoundedCornerShape(26.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 18.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(MeetColors.cyberCyan.copy(alpha = .42f), Color.Transparent),
                            ),
                            CircleShape,
                        )
                        .border(1.dp, MeetColors.neonGreen.copy(alpha = .65f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Route,
                        contentDescription = null,
                        tint = MeetColors.neonGreen,
                        modifier = Modifier.size(42.dp),
                    )
                }
                Text(
                    "ACTIVA TU CUENTA DE VIAJES",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Antes de mostrar el mapa necesitamos saber cómo usarás Elysium Vanguard. El registro protege viajes, pagos y soporte.",
                    color = MeetColors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onPassengerRegistration,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeetColors.neonGreen,
                        contentColor = Color(0xFF02100B),
                    ),
                ) {
                    Icon(Icons.Default.Person, null)
                    Spacer(Modifier.width(9.dp))
                    Text("REGISTRARME PARA VIAJAR", fontWeight = FontWeight.Black)
                }
                OutlinedButton(
                    onClick = onDriverRegistration,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, MeetColors.cyberCyan),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.cyberCyan),
                ) {
                    Icon(Icons.Default.DirectionsCar, null)
                    Spacer(Modifier.width(9.dp))
                    Text("REGISTRARME COMO CHOFER", fontWeight = FontWeight.Black)
                }
                Text(
                    "Puedes tener ambos perfiles en la misma cuenta. La verificación de usuario y la documentación de chofer se administran por separado.",
                    color = MeetColors.textMuted,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun RideProjectionConnectionState.rideProjectionStatusLabel(): String =
    when (this) {
        RideProjectionConnectionState.IDLE -> "LOCAL"
        RideProjectionConnectionState.CONNECTING -> "CONECTANDO"
        RideProjectionConnectionState.LIVE -> "EN VIVO"
        RideProjectionConnectionState.RECOVERING -> "RECUPERANDO"
        RideProjectionConnectionState.AUTHENTICATION_REQUIRED -> "SIN SESIÓN"
    }

internal fun requiresRideRoleRegistration(
    driverMode: Boolean,
    passengerRegistrationExists: Boolean,
    driverRegistrationExists: Boolean,
): Boolean = if (driverMode) {
    !driverRegistrationExists
} else {
    !passengerRegistrationExists
}

private fun RideProjectionConnectionState.rideProjectionStatusColor(): Color =
    when (this) {
        RideProjectionConnectionState.LIVE -> MeetColors.neonGreen
        RideProjectionConnectionState.CONNECTING,
        RideProjectionConnectionState.RECOVERING -> MeetColors.warning
        RideProjectionConnectionState.IDLE,
        RideProjectionConnectionState.AUTHENTICATION_REQUIRED -> MeetColors.textMuted
    }

private fun rideGeoPointOrNull(
    latitude: Double,
    longitude: Double,
    accuracyMeters: Float?,
    capturedAtEpochMs: Long,
): RideGeoPoint? =
    runCatching {
        RideGeoPoint(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            capturedAtEpochMs = capturedAtEpochMs.coerceAtLeast(0L),
        )
    }.getOrNull()

private enum class RidePinTarget { PICKUP, DESTINATION }

@Composable
fun PassengerDashboard(
    viewModel: ObdViewModel,
    forceRegistration: Boolean = false,
) {
    val context = LocalContext.current
    val currentLocale = rememberRideJavaLocale()
    val currentGps by viewModel.currentGpsLocation.collectAsState()
    val allRides by viewModel.rideRequests.collectAsState()

    var destAddress by remember { mutableStateOf("") }
    var destLatitude by remember { mutableDoubleStateOf(0.0) }
    var destLongitude by remember { mutableDoubleStateOf(0.0) }
    var destinationPlaceId by remember { mutableStateOf<String?>(null) }
    var destinationSuggestions by remember { mutableStateOf(emptyList<RidePlaceSuggestion>()) }
    var destinationSearchLoading by remember { mutableStateOf(false) }
    var destinationSearchFailed by remember { mutableStateOf(false) }
    var pinTarget by remember { mutableStateOf<RidePinTarget?>(null) }
    var pickupPin by remember { mutableStateOf<RideGeoPoint?>(null) }
    var pendingMapPin by remember { mutableStateOf<RideGeoPoint?>(null) }
    var stops by remember { mutableStateOf(emptyList<RideStopSnapshot>()) }
    var paymentMethod by remember { mutableStateOf(RidePaymentMethod.CASH) }
    val placeSearchProvider = remember {
        resilientRidePlaceSearchProvider(
            primaryEndpoint = BuildConfig.RIDE_GEOCODER_URL,
            fallbackEndpoint = BuildConfig.RIDE_GEOCODER_FALLBACK_URL,
        )
    }
    val routingProvider = remember {
        resilientRideRoutingProvider(
            primaryEndpoint = BuildConfig.RIDE_ROUTER_URL,
            fallbackEndpoint = BuildConfig.RIDE_ROUTER_FALLBACK_URL,
        )
    }
    var previewRoadRoute by remember { mutableStateOf<RideRoadRoute?>(null) }
    var routeSearchLoading by remember { mutableStateOf(false) }
    var routeSearchFailed by remember { mutableStateOf(false) }
    val savedPlacesStore = remember(context) { RideSavedPlacesStore(context) }
    var savedPlaces by remember { mutableStateOf(savedPlacesStore.load()) }

    var offerPrice by remember { mutableDoubleStateOf(2_400.0) }
    var isUsd by remember { mutableStateOf(false) }
    var fareMode by rememberSaveable { mutableStateOf(RideFareMode.OPEN_BID) }

    // Passenger verification state
    val passengerVer by viewModel.passengerVerification.collectAsState()

    val userRides = remember(allRides, passengerVer) {
        passengerVer?.passengerId?.let { passengerId ->
            allRides.filter { it.passengerId == passengerId }
        }.orEmpty()
    }

    val activeRideForPassenger = remember(allRides, passengerVer) {
        val myId = passengerVer?.passengerId ?: return@remember null
        allRides.firstOrNull {
            it.passengerId == myId &&
                it.status in listOf("OPEN", "ACCEPTED", "ARRIVED", "PASSENGER_ONBOARD", "IN_PROGRESS")
        }
    }

    var showPaxVerification by remember { mutableStateOf(false) }
    var paxName by remember { mutableStateOf("") }
    var paxPhone by remember { mutableStateOf("") }
    var paxProfilePhoto by remember { mutableStateOf("") }
    var paxCedulaFront by remember { mutableStateOf("") }
    var paxSelfieWithCedula by remember { mutableStateOf("") }

    LaunchedEffect(forceRegistration, passengerVer) {
        if (forceRegistration && passengerVer == null) {
            showPaxVerification = true
        }
    }

    LaunchedEffect(destAddress, destinationPlaceId, currentGps) {
        if (destinationPlaceId != null || destAddress.trim().length < 3) {
            destinationSuggestions = emptyList()
            return@LaunchedEffect
        }
        delay(350)
        destinationSearchLoading = true
        destinationSearchFailed = false
        val searchResult = runCatching {
            placeSearchProvider.search(
                query = destAddress,
                biasLatitude = currentGps?.latitude,
                biasLongitude = currentGps?.longitude,
            )
        }
        destinationSuggestions = searchResult.getOrDefault(emptyList())
            .sortedBy { it.distanceKmFrom(currentGps?.latitude, currentGps?.longitude) ?: Double.MAX_VALUE }
        destinationSearchFailed = searchResult.isFailure
        destinationSearchLoading = false
    }

    LaunchedEffect(
        currentGps?.latitude,
        currentGps?.longitude,
        pickupPin,
        destinationPlaceId,
        destLatitude,
        destLongitude,
        stops,
    ) {
        val gps = currentGps
        val resolvedStops = stops.mapNotNull { stop ->
            if (!stop.isResolved) null else rideGeoPointOrNull(
                latitude = requireNotNull(stop.latitude),
                longitude = requireNotNull(stop.longitude),
                accuracyMeters = null,
                capturedAtEpochMs = System.currentTimeMillis(),
            )
        }
        if (
            gps == null ||
            destinationPlaceId == null ||
            (destLatitude == 0.0 && destLongitude == 0.0) ||
            resolvedStops.size != stops.size
        ) {
            previewRoadRoute = null
            routeSearchLoading = false
            routeSearchFailed = false
            return@LaunchedEffect
        }
        val pickup = pickupPin ?: rideGeoPointOrNull(
            latitude = gps.latitude,
            longitude = gps.longitude,
            accuracyMeters = gps.accuracy,
            capturedAtEpochMs = gps.timestamp.coerceAtLeast(0L),
        )
        val destination = rideGeoPointOrNull(
            latitude = destLatitude,
            longitude = destLongitude,
            accuracyMeters = null,
            capturedAtEpochMs = System.currentTimeMillis(),
        )
        if (pickup == null || destination == null) return@LaunchedEffect
        routeSearchLoading = true
        routeSearchFailed = false
        val result = runCatching {
            routingProvider.route(listOf(pickup) + resolvedStops + destination)
        }
        previewRoadRoute = result.getOrNull()
        routeSearchFailed = result.isFailure
        routeSearchLoading = false
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // ── Identity Verification Gate ────────────────────────────────────
        if (!RideVerificationPolicy.grantsAccess(passengerVer?.status)) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                    border = BorderStroke(1.5.dp, MeetColors.warning.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("🛡️", fontSize = 48.sp)
                        Text(
                            "VERIFICACIÓN DE IDENTIDAD REQUERIDA",
                            color = MeetColors.warning,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Para tu seguridad y la del conductor, necesitamos verificar tu identidad antes de que puedas solicitar viajes.",
                            color = MeetColors.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        when (passengerVer?.status) {
                            null -> {
                                Button(
                                    onClick = { showPaxVerification = true },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MeetColors.electricBlue,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("🔐 VERIFICAR MI IDENTIDAD", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                                }
                            }
                            "PENDING" -> {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MeetColors.warning.copy(alpha = 0.1f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("⏳ Tu verificación está siendo revisada...", color = MeetColors.warning, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Esto puede tardar unas horas.", color = MeetColors.textMuted, fontSize = 12.sp)
                                    }
                                }
                            }
                            "REJECTED" -> {
                                Text(
                                    passengerVer?.rejectionReason ?: "Verificación rechazada. Intenta de nuevo.",
                                    color = Color(0xFFEF5350),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { viewModel.deletePassengerVerification() },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFEF5350),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("🔄 ELIMINAR Y REINTENTAR", fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
            }
            // Don't show ride request form if not verified
        } else {
            // Banner de viaje activo si lo hay
            if (activeRideForPassenger != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectActiveRide(activeRideForPassenger) },
                        colors = CardDefaults.cardColors(containerColor = MeetColors.electricBlue.copy(alpha = 0.15f)),
                        border = BorderStroke(1.5.dp, MeetColors.electricBlue),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🚨 VIAJE ACTIVO EN CURSO", color = MeetColors.electricBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MeetColors.electricBlue)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("VER VIAJE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Desde: ${activeRideForPassenger.pickupAddress}", color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Hasta: ${activeRideForPassenger.destAddress}", color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Estado: ${activeRideForPassenger.status} | Oferta: ${activeRideForPassenger.priceOffer.toInt()} ${activeRideForPassenger.currency}", color = MeetColors.textSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }

            // GPS Quirúrgico Card
            item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "📍 Ubicación de Salida (Precisión Quirúrgica)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MeetColors.textPrimary
                        )

                        // Indicador milimétrico de precisión
                        val accuracy = currentGps?.accuracy ?: 999f
                        val (color, label) = when {
                            accuracy <= 5f -> Pair(MeetColors.neonGreen, "Excelente (≤5m)")
                            accuracy <= 15f -> Pair(MeetColors.warning, "Aceptable (≤15m)")
                            else -> Pair(MeetColors.error, "Impreciso (>15m)")
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(color.copy(alpha = 0.10f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = label,
                                color = color,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = currentGps?.addressName ?: "Detectando satélites y geolocalización...",
                        color = MeetColors.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.detectCurrentLocation(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.electricBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Actualizar Localización Satelital", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            val initial = pickupPin ?: currentGps?.let {
                                rideGeoPointOrNull(
                                    it.latitude,
                                    it.longitude,
                                    it.accuracy,
                                    it.timestamp.coerceAtLeast(0L),
                                )
                            }
                            pendingMapPin = initial
                            pinTarget = RidePinTarget.PICKUP
                        },
                        enabled = currentGps != null,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        border = BorderStroke(1.dp, MeetColors.cyberCyan),
                    ) {
                        Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (pickupPin == null) "AJUSTAR RECOGIDA EN MAPA" else "RECOGIDA FIJADA · CAMBIAR PIN")
                    }
                }
            }
        }

        // Destino Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🏁 Destino del Viaje",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MeetColors.textPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (savedPlaces.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(savedPlaces, key = RideSavedPlace::slot) { place ->
                                AssistChip(
                                    onClick = {
                                        destAddress = place.address
                                        destLatitude = place.latitude
                                        destLongitude = place.longitude
                                        destinationPlaceId = place.providerId
                                        destinationSuggestions = emptyList()
                                    },
                                    label = { Text("${place.label} · ${place.address}", maxLines = 1) },
                                    leadingIcon = {
                                        Icon(
                                            if (place.slot == "HOME") Icons.Default.Home else Icons.Default.Star,
                                            null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    OutlinedTextField(
                        value = destAddress,
                        onValueChange = {
                            destAddress = it
                            destinationPlaceId = null
                            destLatitude = 0.0
                            destLongitude = 0.0
                        },
                        label = { Text("Escribe la dirección de destino") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeetColors.cyberCyan,
                            unfocusedBorderColor = MeetColors.borderSubtle,
                            focusedLabelColor = MeetColors.cyberCyan
                        ),
                        singleLine = true
                    )
                    if (destinationSearchLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MeetColors.cyberCyan,
                        )
                    }
                    destinationSuggestions.forEach { suggestion ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    destAddress = suggestion.displayLabel
                                    destLatitude = suggestion.latitude
                                    destLongitude = suggestion.longitude
                                    destinationPlaceId = suggestion.providerId
                                    destinationSuggestions = emptyList()
                                },
                            color = MeetColors.cardBackground,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.25f)),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = null,
                                    tint = MeetColors.cyberCyan,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        suggestion.primaryLabel,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                if (suggestion.secondaryLabel.isNotBlank()) {
                                    Text(
                                        suggestion.secondaryLabel,
                                        color = MeetColors.textMuted,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (suggestion.source == RideMapDataSource.CACHE) {
                                    Text(
                                        "Caché local reciente",
                                        color = MeetColors.cyberCyan,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                }
                                suggestion.distanceKmFrom(
                                    currentGps?.latitude,
                                    currentGps?.longitude,
                                )?.let { distance ->
                                    Text(
                                        String.format(currentLocale, "%.1f km", distance),
                                        color = MeetColors.textMuted,
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                    }
                    if (
                        !destinationSearchLoading &&
                        destAddress.trim().length >= 3 &&
                        destinationPlaceId == null &&
                        destinationSuggestions.isEmpty()
                    ) {
                        Text(
                            if (destinationSearchFailed) {
                                "No se pudo consultar el mapa. Revisa internet e inténtalo de nuevo."
                            } else {
                                "Sin coincidencias. Escribe lugar + cantón o fija el punto en el mapa."
                            },
                            color = MeetColors.warning,
                            fontSize = 10.sp,
                        )
                    }
                    if (destinationPlaceId != null) {
                        Text(
                            "✓ Ubicación real seleccionada · © OpenStreetMap contributors",
                            color = MeetColors.neonGreen,
                            fontSize = 9.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                Triple("HOME", "Casa", "Casa"),
                                Triple("WORK", "Trabajo", "Trabajo"),
                                Triple("FAVORITE", "Favorito", "Favorito"),
                            ).forEach { (slot, label, button) ->
                                TextButton(
                                    onClick = {
                                        savedPlaces = savedPlacesStore.save(
                                            RideSavedPlace(
                                                slot = slot,
                                                label = label,
                                                address = destAddress,
                                                latitude = destLatitude,
                                                longitude = destLongitude,
                                                providerId = requireNotNull(destinationPlaceId),
                                            ),
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 5.dp),
                                ) {
                                    Text("Guardar $button", fontSize = 9.sp)
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val initial = rideGeoPointOrNull(
                                latitude = destLatitude.takeIf { it != 0.0 }
                                    ?: currentGps?.latitude ?: 9.9281,
                                longitude = destLongitude.takeIf { it != 0.0 }
                                    ?: currentGps?.longitude ?: -84.0907,
                                accuracyMeters = null,
                                capturedAtEpochMs = System.currentTimeMillis(),
                            )
                            pendingMapPin = initial
                            pinTarget = RidePinTarget.DESTINATION
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        border = BorderStroke(1.dp, MeetColors.cyberCyan),
                    ) {
                        Icon(Icons.Default.Place, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("FIJAR DESTINO CON PIN")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Coordenadas manuales de ayuda para precisión milimétrica sin mapa
                        OutlinedTextField(
                            value = if (destLatitude == 0.0) "" else destLatitude.toString(),
                            onValueChange = { destLatitude = it.toDoubleOrNull() ?: 0.0 },
                            label = { Text("Latitud") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MeetColors.cyberCyan,
                                unfocusedBorderColor = MeetColors.borderSubtle
                            )
                        )
                        OutlinedTextField(
                            value = if (destLongitude == 0.0) "" else destLongitude.toString(),
                            onValueChange = { destLongitude = it.toDoubleOrNull() ?: 0.0 },
                            label = { Text("Longitud") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MeetColors.cyberCyan,
                                unfocusedBorderColor = MeetColors.borderSubtle
                            )
                        )
                    }
                }
            }
        }


        item {
            RideFareModeSelector(
                selected = fareMode,
                onSelected = {
                    fareMode = it
                    if (it == RideFareMode.METERED_TIME_DISTANCE) isUsd = false
                },
            )
        }

        item {
            RideStopsEditor(
                stops = stops,
                provider = placeSearchProvider,
                biasLatitude = currentGps?.latitude,
                biasLongitude = currentGps?.longitude,
                onStopsChanged = { stops = RideTripPlanPolicy.normalize(it) },
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "FORMA DE PAGO",
                        color = MeetColors.cyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RidePaymentMethod.entries.forEach { method ->
                            FilterChip(
                                selected = paymentMethod == method,
                                onClick = { paymentMethod = method },
                                label = {
                                    Text(
                                        if (method == RidePaymentMethod.CASH) "💵 Efectivo" else "📲 SINPE",
                                        fontWeight = FontWeight.Bold,
                                    )
                                },
                            )
                        }
                    }
                    Text(
                        "La selección declara cómo se pagará; no confirma por sí sola que el pago fue recibido.",
                        color = MeetColors.textMuted,
                        fontSize = 9.sp,
                    )
                }
            }
        }

        if (currentGps != null && destinationPlaceId != null) {
            item {
                val stopPoints = stops.mapNotNull { stop ->
                    if (!stop.isResolved) null else rideGeoPointOrNull(
                        latitude = requireNotNull(stop.latitude),
                        longitude = requireNotNull(stop.longitude),
                        accuracyMeters = null,
                        capturedAtEpochMs = System.currentTimeMillis(),
                    )
                }
                val previewState = RideMapStateFactory.create(
                    pickup = pickupPin ?: rideGeoPointOrNull(
                        currentGps!!.latitude,
                        currentGps!!.longitude,
                        currentGps!!.accuracy,
                        currentGps!!.timestamp.coerceAtLeast(0L),
                    ),
                    stops = stopPoints,
                    destination = rideGeoPointOrNull(
                        destLatitude,
                        destLongitude,
                        null,
                        System.currentTimeMillis(),
                    ),
                    route = previewRoadRoute?.geometry,
                )
                Card(
                    Modifier.fillMaxWidth().height(280.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xCC06121F)),
                    border = BorderStroke(1.5.dp, MeetColors.cyberCyan.copy(alpha = 0.82f)),
                    shape = RoundedCornerShape(22.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                ) {
                    RideMapPanel(state = previewState, modifier = Modifier.fillMaxSize())
                }
                Text(
                    text = when {
                        routeSearchLoading -> "Calculando ruta vial real…"
                        previewRoadRoute != null -> {
                            val route = requireNotNull(previewRoadRoute)
                            val km = route.distanceMeters / 1_000.0
                            val minutes = kotlin.math.ceil(route.durationSeconds / 60.0).toInt()
                            "Ruta vial: ${String.format(currentLocale, "%.1f", km)} km · $minutes min · ${route.attribution}"
                        }
                        routeSearchFailed ->
                            "Ruta vial no disponible. No se dibujará una línea falsa ni se inventará un ETA."
                        else -> "Selecciona destino y paradas para calcular la ruta vial."
                    },
                    color = if (routeSearchFailed) MeetColors.warning else MeetColors.textMuted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }

        // Subasta de Precio Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (fareMode == RideFareMode.OPEN_BID) {
                                "PON TU PRECIO · SUBASTA JUSTA"
                            } else {
                                "TIEMPO + DISTANCIA · TARIFA CLARA"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MeetColors.textPrimary
                        )

                        if (fareMode == RideFareMode.OPEN_BID) Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MeetColors.cardBackground)
                                .clickable { isUsd = !isUsd }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isUsd) "USD $" else "CRC ₡",
                                color = MeetColors.cyberCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val meteredQuote = previewRoadRoute?.let { route ->
                        runCatching {
                            RideFareEngine.quoteCostaRica(
                                distanceMeters = route.distanceMeters.toLong(),
                                durationSeconds = route.durationSeconds.toLong(),
                            )
                        }.getOrNull()
                    }
                    Text(
                        text = if (fareMode == RideFareMode.METERED_TIME_DISTANCE) {
                            meteredQuote?.let {
                                "₡${String.format(currentLocale, "%,d", it.estimatedTotalMinor)} estimados"
                            } ?: "Calculando estimado…"
                        } else if (isUsd) {
                            "$${String.format(currentLocale, "%.2f", offerPrice / 500.0)} USD"
                        } else {
                            "₡${String.format(currentLocale, "%,.0f", offerPrice)} CRC"
                        },
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = MeetColors.neonGreen,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (fareMode == RideFareMode.OPEN_BID) Slider(
                        value = offerPrice.toFloat(),
                        onValueChange = {
                            offerPrice = RideFareBidPolicy.normalize(
                                it.toDouble(),
                                "CRC",
                            )
                        },
                        valueRange = 900f..30000f,
                        steps = 96,
                        colors = SliderDefaults.colors(
                            thumbColor = MeetColors.neonGreen,
                            activeTrackColor = MeetColors.neonGreen,
                            inactiveTrackColor = MeetColors.borderSubtle
                        )
                    )
                    Text(
                        if (fareMode == RideFareMode.METERED_TIME_DISTANCE) {
                            meteredQuote?.let {
                                "${String.format(currentLocale, "%.1f", it.estimatedDistanceMeters / 1_000.0)} km × ₡300 = ₡${it.distanceFareMinor}  ·  " +
                                    "${String.format(currentLocale, "%.1f", it.estimatedDurationSeconds / 60.0)} min × ₡60 = ₡${it.timeFareMinor}\n" +
                                    "El total mostrado es estimado; el definitivo usa distancia y tiempo reales registrados. Puedes añadir paradas durante el viaje."
                            } ?: "Selecciona una ruta real para obtener el desglose."
                        } else if (isUsd) {
                            "Equivalencia referencial; la base se ajusta en saltos de ₡300"
                        } else {
                            "Ajuste exacto en saltos de ₡300"
                        },
                        color = MeetColors.textMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val verifiedPassenger = passengerVer
                            if (
                                verifiedPassenger == null ||
                                !RideVerificationPolicy.grantsAccess(verifiedPassenger.status)
                            ) {
                                Toast.makeText(
                                    context,
                                    "Completa la verificación de identidad antes de solicitar",
                                    Toast.LENGTH_LONG,
                                ).show()
                                return@Button
                            }
                            if (verifiedPassenger.phone.isBlank()) {
                                Toast.makeText(
                                    context,
                                    "Falta un teléfono verificado",
                                    Toast.LENGTH_LONG,
                                ).show()
                                return@Button
                            }
                            val gps = currentGps
                            if (gps == null) {
                                Toast.makeText(context, "Espere a obtener coordenadas GPS válidas", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            if (destAddress.isBlank()) {
                                Toast.makeText(context, "Por favor ingrese la dirección de destino", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (!RideTripPlanPolicy.canDispatch(
                                    destinationResolved = destinationPlaceId != null ||
                                        (destLatitude != 0.0 || destLongitude != 0.0),
                                    stops = stops,
                                )
                            ) {
                                Toast.makeText(
                                    context,
                                    "Selecciona cada destino y parada desde resultados reales del mapa",
                                    Toast.LENGTH_LONG,
                                ).show()
                                return@Button
                            }

                            // Sanitizar dirección: si el geocoder falló, rawAddressName llega
                            // como "Ubicación GPS (lat, lng)" — eso filtra coords exactas al
                            // backend. Si detectamos ese patrón, usamos genérico.
                            // (RISK-3 GPS leak: lat/lon no debe ir en strings user-facing).
                            val selectedPickup = pickupPin
                            val safePickupAddress = if (selectedPickup != null) {
                                "Punto de recogida fijado por el pasajero"
                            } else {
                                sanitizeGpsAddress(gps.addressName)
                            }

                            val distance = previewRoadRoute
                                ?.distanceMeters
                                ?.div(1_000.0)
                                ?: 0.0
                            val durationMinutes = previewRoadRoute
                                ?.durationSeconds
                                ?.div(60.0)
                                ?.let { kotlin.math.ceil(it) }
                                ?.toInt()
                                ?: 0
                            if (
                                fareMode == RideFareMode.METERED_TIME_DISTANCE &&
                                previewRoadRoute == null
                            ) {
                                Toast.makeText(
                                    context,
                                    "Espera el cálculo de la ruta para usar tiempo y distancia",
                                    Toast.LENGTH_LONG,
                                ).show()
                                return@Button
                            }

                            viewModel.createRideRequest(
                                passengerId = verifiedPassenger.passengerId,
                                passengerName = verifiedPassenger.fullName,
                                passengerPhone = verifiedPassenger.phone,
                                countryCode = gps.countryCode,
                                pickupLat = selectedPickup?.latitude ?: gps.latitude,
                                pickupLng = selectedPickup?.longitude ?: gps.longitude,
                                pickupAddr = safePickupAddress,
                                pickupAcc = selectedPickup?.accuracyMeters ?: gps.accuracy,
                                destLat = destLatitude,
                                destLng = destLongitude,
                                destAddr = destAddress,
                                priceOffer = if (fareMode == RideFareMode.METERED_TIME_DISTANCE) {
                                    meteredQuote?.estimatedTotalMinor?.toDouble() ?: 0.0
                                } else if (isUsd) offerPrice / 500.0 else offerPrice,
                                currency = if (fareMode == RideFareMode.METERED_TIME_DISTANCE) {
                                    "CRC"
                                } else if (isUsd) "USD" else "CRC",
                                estDistance = distance,
                                estDuration = durationMinutes,
                                estimatedDistanceMeters = previewRoadRoute
                                    ?.distanceMeters?.toLong() ?: 0L,
                                estimatedDurationSeconds = previewRoadRoute
                                    ?.durationSeconds?.toLong() ?: 0L,
                                stopsJson = Json.encodeToString(stops),
                                paymentMethod = paymentMethod.name,
                                fareMode = fareMode,
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Text(
                            text = if (fareMode == RideFareMode.OPEN_BID) {
                                "🚀 PUBLICAR MI OFERTA"
                            } else {
                                "⚡ SOLICITAR CON TARIFA MEDIDA"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MeetColors.backgroundDark
                        )
                    }
                }
            }
        }

        // Historial / Solicitudes Activas
        if (userRides.isNotEmpty()) {
            item {
                Text(
                    text = "📋 Tus Solicitudes Activas",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            items(userRides) { ride ->
                PassengerRideItem(
                    ride = ride,
                    onSelect = { viewModel.selectActiveRide(ride) },
                    onCancel = { viewModel.selectActiveRide(ride) }
                )
            }
        }
    }
    }

    pinTarget?.let { target ->
        val mapPinState = RideMapStateFactory.create(
            pickup = pickupPin ?: currentGps?.let {
                rideGeoPointOrNull(
                    it.latitude,
                    it.longitude,
                    it.accuracy,
                    it.timestamp.coerceAtLeast(0L),
                )
            },
            destination = if (destLatitude == 0.0 && destLongitude == 0.0) {
                null
            } else {
                rideGeoPointOrNull(
                    destLatitude,
                    destLongitude,
                    null,
                    System.currentTimeMillis(),
                )
            },
        )
        RidePinPickerDialog(
            targetLabel = if (target == RidePinTarget.PICKUP) {
                "PUNTO EXACTO DE RECOGIDA"
            } else {
                "DESTINO EXACTO"
            },
            state = mapPinState,
            initialPoint = pendingMapPin,
            onPinChanged = { pendingMapPin = it },
            onDismiss = {
                pendingMapPin = null
                pinTarget = null
            },
            onConfirm = { point ->
                if (target == RidePinTarget.PICKUP) {
                    pickupPin = point
                } else {
                    destLatitude = point.latitude
                    destLongitude = point.longitude
                    destAddress = "Punto seleccionado en el mapa"
                    destinationPlaceId = "elysium-map-pin"
                    destinationSuggestions = emptyList()
                }
                pendingMapPin = null
                pinTarget = null
            },
        )
    }

    if (showPaxVerification) {
        PaxVerificationDialog(
            paxName = paxName,
            onNameChange = { paxName = it },
            paxPhone = paxPhone,
            onPhoneChange = { paxPhone = it },
            paxProfilePhoto = paxProfilePhoto,
            onProfileCapture = { paxProfilePhoto = it },
            paxCedulaFront = paxCedulaFront,
            onCedulaCapture = { paxCedulaFront = it },
            paxSelfieWithCedula = paxSelfieWithCedula,
            onSelfieCapture = { paxSelfieWithCedula = it },
            onDismiss = { showPaxVerification = false },
            onSubmit = {
                viewModel.submitPassengerVerification(
                    fullName = paxName, phone = paxPhone,
                    pathProfilePhoto = paxProfilePhoto,
                    pathCedulaFront = paxCedulaFront,
                    pathSelfieWithCedula = paxSelfieWithCedula
                )
                showPaxVerification = false
            }
        )
    }
}

@Composable
private fun RideFareModeSelector(
    selected: RideFareMode,
    onSelected: (RideFareMode) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xE6081323)),
        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "ELIGE CÓMO SE CALCULA TU VIAJE",
                color = MeetColors.cyberCyan,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
            )
            Text(
                "La modalidad queda registrada desde la solicitud y siempre será visible para ambas partes.",
                color = MeetColors.textSecondary,
                fontSize = 11.sp,
            )
            RideFareMode.entries.forEach { mode ->
                val active = selected == mode
                val accent = if (mode == RideFareMode.OPEN_BID) {
                    Color(0xFFBE35FF)
                } else {
                    MeetColors.neonGreen
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelected(mode) },
                    shape = RoundedCornerShape(18.dp),
                    color = accent.copy(alpha = if (active) 0.16f else 0.04f),
                    border = BorderStroke(if (active) 2.dp else 1.dp, accent.copy(alpha = 0.8f)),
                    shadowElevation = if (active) 10.dp else 0.dp,
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(
                            selected = active,
                            onClick = { onSelected(mode) },
                            colors = RadioButtonDefaults.colors(selectedColor = accent),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (mode == RideFareMode.OPEN_BID) {
                                    "PON TU PRECIO"
                                } else {
                                    "TIEMPO + DISTANCIA"
                                },
                                color = if (active) accent else MeetColors.textPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                            )
                            Text(
                                if (mode == RideFareMode.OPEN_BID) {
                                    "Tú propones el monto. Paradas solo antes de publicar."
                                } else {
                                    "₡300/km + ₡60/min. Permite añadir paradas durante el viaje."
                                },
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RidePinPickerDialog(
    targetLabel: String,
    state: com.elysium369.meet.ride.map.RideMapState,
    initialPoint: RideGeoPoint?,
    onPinChanged: (RideGeoPoint) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (RideGeoPoint) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MeetColors.backgroundDark,
        ) {
            Column(Modifier.fillMaxSize()) {
                Surface(
                    color = MeetColors.backgroundDeep,
                    shadowElevation = 14.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Cerrar selector", tint = MeetColors.cyberCyan)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                targetLabel,
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                            )
                            Text(
                                "Arrastra el mapa bajo el pin. Pellizca o usa + / − para afinar.",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
                RideMapPanel(
                    state = state,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    pinSelectionEnabled = true,
                    pinSelectionLabel = "El pin permanece fijo; mueve el mapa",
                    pinSelectionInitialPoint = initialPoint,
                    onPinSelectionChanged = onPinChanged,
                    onPinSelectionCancelled = onDismiss,
                    onPinSelectionConfirmed = onConfirm,
                )
            }
        }
    }
}

@Composable
private fun RideStopsEditor(
    stops: List<RideStopSnapshot>,
    provider: RidePlaceSearchProvider,
    biasLatitude: Double?,
    biasLongitude: Double?,
    onStopsChanged: (List<RideStopSnapshot>) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PARADAS", color = MeetColors.cyberCyan, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    Text("El conductor las verá antes de aceptar", color = MeetColors.textMuted, fontSize = 9.sp)
                }
                Button(
                    onClick = {
                        if (stops.size < RideTripPlanPolicy.MAX_STOPS) {
                            onStopsChanged(stops + RideStopSnapshot(stops.size + 1, ""))
                        }
                    },
                    enabled = stops.size < RideTripPlanPolicy.MAX_STOPS,
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.electricBlue),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("AÑADIR", fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
            stops.forEachIndexed { index, stop ->
                RideStopField(
                    stop = stop,
                    provider = provider,
                    biasLatitude = biasLatitude,
                    biasLongitude = biasLongitude,
                    onChanged = { updated ->
                        onStopsChanged(stops.toMutableList().also { it[index] = updated })
                    },
                    onRemove = {
                        onStopsChanged(stops.filterIndexed { candidate, _ -> candidate != index })
                    },
                )
            }
        }
    }
}

@Composable
private fun RideStopField(
    stop: RideStopSnapshot,
    provider: RidePlaceSearchProvider,
    biasLatitude: Double?,
    biasLongitude: Double?,
    onChanged: (RideStopSnapshot) -> Unit,
    onRemove: () -> Unit,
) {
    var suggestions by remember(stop.order) { mutableStateOf(emptyList<RidePlaceSuggestion>()) }
    LaunchedEffect(stop.label, stop.providerPlaceId, biasLatitude, biasLongitude) {
        if (stop.providerPlaceId != null || stop.label.trim().length < 3) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(350)
        suggestions = runCatching {
            provider.search(stop.label, biasLatitude, biasLongitude)
        }.getOrDefault(emptyList())
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MeetColors.cardBackground, RoundedCornerShape(12.dp))
            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = stop.label,
                onValueChange = {
                    onChanged(
                        stop.copy(
                            label = it,
                            latitude = null,
                            longitude = null,
                            providerPlaceId = null,
                        ),
                    )
                },
                label = { Text("Parada ${stop.order}") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar parada", tint = MeetColors.error)
            }
        }
        suggestions.forEach { suggestion ->
            Text(
                suggestion.displayLabel,
                color = Color.White,
                fontSize = 10.sp,
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onChanged(
                            stop.copy(
                                label = suggestion.displayLabel,
                                latitude = suggestion.latitude,
                                longitude = suggestion.longitude,
                                providerPlaceId = suggestion.providerId,
                            ),
                        )
                        suggestions = emptyList()
                    }
                    .padding(vertical = 8.dp),
            )
        }
        if (stop.isResolved) {
            Text("✓ Parada ubicada en el mapa", color = MeetColors.neonGreen, fontSize = 9.sp)
        }
    }
}

@Composable
fun DriverDashboard(
    viewModel: ObdViewModel,
    onRegisterDriver: () -> Unit = {},
) {
    val context = LocalContext.current
    val openRides by viewModel.openRideRequests.collectAsState()
    val currentGps by viewModel.currentGpsLocation.collectAsState()
    val allRides by viewModel.rideRequests.collectAsState()
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    val activeDtcs by viewModel.canonicalActiveFindingSummaries.collectAsState()
    val historicalDtcs by viewModel.canonicalHistoricalFindingSummaries.collectAsState()
    val sharingSelections by viewModel.rideSharingSelections.collectAsState()

    val driverVer by viewModel.driverVerification.collectAsState()
    val myDriverId = viewModel.currentUserId ?: driverVer?.driverId
    val driverPrefs = remember(context) {
        context.getSharedPreferences("elysium_ride_driver_ops", Context.MODE_PRIVATE)
    }
    val rideNotifications = remember(context) { RideNotificationCoordinator(context) }
    var destinationHomeEnabled by remember {
        mutableStateOf(driverPrefs.getBoolean("destination_home_enabled", false))
    }
    var homeLatitude by remember {
        mutableStateOf(driverPrefs.getString("home_latitude", null)?.toDoubleOrNull())
    }
    var homeLongitude by remember {
        mutableStateOf(driverPrefs.getString("home_longitude", null)?.toDoubleOrNull())
    }

    LaunchedEffect(viewModel) {
        viewModel.rideClaimFeedback.collect { feedback ->
            Toast.makeText(
                context,
                when {
                    feedback.won -> "🎉 ${feedback.message}"
                    feedback.pending -> "⏳ ${feedback.message}"
                    else -> "⚡ ${feedback.message}"
                },
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val activeRideForDriver = remember(allRides, myDriverId) {
        myDriverId?.let { driverId ->
            allRides.firstOrNull {
                it.assignedDriverId == driverId &&
                    it.status in listOf("ACCEPTED", "ARRIVED", "PASSENGER_ONBOARD", "IN_PROGRESS")
            }
        }
    }
    val rankedOpenRides = remember(openRides, destinationHomeEnabled, homeLatitude, homeLongitude) {
        if (!destinationHomeEnabled || homeLatitude == null || homeLongitude == null) {
            openRides
        } else {
            openRides.sortedBy { ride ->
                calculateDistance(
                    ride.destLatitude,
                    ride.destLongitude,
                    requireNotNull(homeLatitude),
                    requireNotNull(homeLongitude),
                )
            }
        }
    }
    LaunchedEffect(driverVer?.status, activeRideForDriver, rankedOpenRides.isEmpty()) {
        if (
            RideVerificationPolicy.grantsAccess(driverVer?.status) &&
            activeRideForDriver == null &&
            rankedOpenRides.isEmpty()
        ) {
            rideNotifications.notifyIdleDriver()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        if (!RideVerificationPolicy.grantsAccess(driverVer?.status)) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                    border = BorderStroke(1.5.dp, MeetColors.warning.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("🚨", fontSize = 48.sp)
                        Text(
                            "ACCESO RESTRINGIDO A CHOFERES",
                            color = MeetColors.warning,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Debes completar el registro de chofer y adjuntar la documentación requerida para visualizar solicitudes y ofertar.",
                            color = MeetColors.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            "Estado actual: ${driverVer?.status ?: "No registrado"}",
                            color = if (driverVer?.status == "PENDING") MeetColors.warning else MeetColors.textMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        if (driverVer == null) {
                            Button(
                                onClick = onRegisterDriver,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MeetColors.cyberCyan,
                                    contentColor = Color(0xFF02131E),
                                ),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Icon(Icons.Default.Badge, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("COMPLETAR REGISTRO DE CHOFER", fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        } else {
            item {
                RideWalletStatusCard()
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF07131E)),
                    border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Home, null, tint = MeetColors.neonGreen)
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text("DESTINO CASA", color = Color.White, fontWeight = FontWeight.Black)
                                Text(
                                    "Prioriza solicitudes cuyo destino se acerque a tu hogar.",
                                    color = MeetColors.textMuted,
                                    fontSize = 10.sp,
                                )
                            }
                            Switch(
                                checked = destinationHomeEnabled,
                                onCheckedChange = { enabled ->
                                    destinationHomeEnabled = enabled && homeLatitude != null
                                    driverPrefs.edit {
                                        putBoolean("destination_home_enabled", destinationHomeEnabled)
                                    }
                                },
                                enabled = homeLatitude != null,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                currentGps?.let { gps ->
                                    homeLatitude = gps.latitude
                                    homeLongitude = gps.longitude
                                    driverPrefs.edit {
                                        putString("home_latitude", gps.latitude.toString())
                                        putString("home_longitude", gps.longitude.toString())
                                    }
                                }
                            },
                            enabled = currentGps != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (homeLatitude == null) "FIJAR CASA EN MI GPS" else "ACTUALIZAR UBICACIÓN DE CASA")
                        }
                        if (destinationHomeEnabled) {
                            Text(
                                "Recomendación activa · la asignación sigue siendo autoritativa y de un solo conductor.",
                                color = MeetColors.neonGreen,
                                fontSize = 9.sp,
                            )
                        }
                    }
                }
            }

            // Banner de viaje asignado activo
            if (activeRideForDriver != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectActiveRide(activeRideForDriver) },
                        colors = CardDefaults.cardColors(containerColor = MeetColors.neonGreen.copy(alpha = 0.15f)),
                        border = BorderStroke(1.5.dp, MeetColors.neonGreen),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🟢 VIAJE ASIGNADO ACTIVO", color = MeetColors.neonGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MeetColors.neonGreen)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("ABRIR PANEL", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Pasajero: ${activeRideForDriver.passengerName}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Desde: ${activeRideForDriver.pickupAddress}", color = MeetColors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Hasta: ${activeRideForDriver.destAddress}", color = MeetColors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Estado: ${activeRideForDriver.status} | Precio: ${activeRideForDriver.priceOffer.toInt()} ${activeRideForDriver.currency}", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }

                item {
                    RideSharingCenter(
                        enabledCategories = sharingSelections[activeRideForDriver.requestId].orEmpty(),
                        vehicle = selectedVehicle,
                        activeDtcs = activeDtcs,
                        historicalDtcs = historicalDtcs,
                        currentGps = currentGps,
                        onCategoryChanged = { category, enabled ->
                            viewModel.setRideShareCategory(
                                requestId = activeRideForDriver.requestId,
                                category = category,
                                enabled = enabled,
                            )
                        },
                    )
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MeetColors.cyberCyan)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Tablero de Chofer", fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                "Abajo se listan las solicitudes disponibles. La nube debe estar autenticada para sincronización entre dispositivos.",
                                color = MeetColors.textSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            if (rankedOpenRides.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = MeetColors.textMuted, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Buscando solicitudes de viaje en tu zona...", color = MeetColors.textMuted, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                items(rankedOpenRides) { request ->
                    DriverRideItem(
                        ride = request,
                        currentGps = currentGps,
                        onClick = { viewModel.selectActiveRide(request) },
                        onOffer = {
                            val driver = driverVer
                            if (driver != null) {
                                viewModel.claimRideFirstCome(
                                    requestId = request.requestId,
                                    driverId = driver.driverId,
                                    driverName = driver.fullName,
                                    driverPhone = driver.phone,
                                    vehicleDescription = listOf(
                                        driver.vehicleMake,
                                        driver.vehicleModel,
                                        driver.vehicleYear.toString(),
                                        driver.vehicleColor,
                                    ).filter(String::isNotBlank).joinToString(" "),
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PassengerRideItem(
    ride: RideRequestEntity,
    onSelect: () -> Unit,
    onCancel: () -> Unit
) {
    val elapsedMs = System.currentTimeMillis() - ride.createdAt
    val elapsedMins = (elapsedMs / (1000 * 60)).toInt()
    val timeText = if (elapsedMins <= 0) "Hace un momento" else "Hace $elapsedMins min"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Viaje a ${ride.destAddress}",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = timeText,
                        fontSize = 11.sp,
                        color = MeetColors.textMuted
                    )
                }

                val statusColor = when (ride.status) {
                    "OPEN" -> MeetColors.warning
                    "ACCEPTED" -> MeetColors.cyberCyan
                    "ARRIVED" -> MeetColors.electricBlue
                    "IN_PROGRESS" -> MeetColors.neonGreen
                    "COMPLETED" -> MeetColors.neonGreen
                    else -> MeetColors.error
                }

                Text(
                    text = ride.status,
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Recogida: ${ride.pickupAddress}",
                fontSize = 12.sp,
                color = MeetColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Oferta: ${ride.priceOffer.toInt()} ${ride.currency}",
                    fontWeight = FontWeight.Bold,
                    color = MeetColors.neonGreen,
                    fontSize = 14.sp
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (ride.status == "OPEN") {
                        TextButton(
                            onClick = { onCancel() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF5350)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Cancelar ❌", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        text = "Ver detalles 💬",
                        color = MeetColors.cyberCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onSelect() }
                    )
                }
            }
        }
    }
}

@Composable
fun DriverRideItem(
    ride: RideRequestEntity,
    currentGps: ObdViewModel.GpsLocationInfo?,
    onClick: () -> Unit,
    onOffer: () -> Unit
) {
    val orderedStops = remember(ride.stopsJson) {
        runCatching { Json.decodeFromString<List<RideStopSnapshot>>(ride.stopsJson) }
            .getOrDefault(emptyList())
            .sortedBy(RideStopSnapshot::order)
    }
    val elapsedMs = System.currentTimeMillis() - ride.createdAt
    val elapsedMins = (elapsedMs / (1000 * 60)).toInt()
    val timeText = if (elapsedMins <= 0) "Hace un momento" else "Hace $elapsedMins min"

    val distanceText = remember(currentGps, ride) {
        if (currentGps != null) {
            val dist = calculateDistance(
                currentGps.latitude, currentGps.longitude,
                ride.pickupLatitude, ride.pickupLongitude
            )
            String.format(java.util.Locale.US, "📍 A %.1f km", dist)
        } else {
            null
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Passenger Name, Elapsed Time, Distance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Pasajero: ${ride.passengerName}",
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                    Text(
                        text = timeText,
                        color = MeetColors.textMuted,
                        fontSize = 11.sp
                    )
                }

                if (distanceText != null) {
                    Surface(
                        color = MeetColors.cyberCyan.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = distanceText,
                            color = MeetColors.cyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Route Details
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MeetColors.neonGreen)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Desde: ${ride.pickupAddress}",
                    fontSize = 13.sp,
                    color = MeetColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            orderedStops.forEach { stop ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MeetColors.warning),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Parada ${stop.order}: ${stop.label}",
                        fontSize = 12.sp,
                        color = MeetColors.warning,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF1744))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Hasta: ${ride.destAddress}",
                    fontSize = 13.sp,
                    color = MeetColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Price and Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        if (ride.fareMode == RideFareMode.METERED_TIME_DISTANCE.name) {
                            "TIEMPO + DISTANCIA · ESTIMADO"
                        } else {
                            "PON TU PRECIO · OFERTA"
                        },
                        color = MeetColors.textMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${ride.priceOffer.toInt()} ${ride.currency}",
                        fontWeight = FontWeight.Black,
                        color = MeetColors.neonGreen,
                        fontSize = 20.sp
                    )
                    Text(
                        "${if (ride.paymentMethod == "SINPE") "📲 SINPE" else "💵 EFECTIVO"} · " +
                            "${orderedStops.size} parada(s)",
                        color = MeetColors.cyberCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (ride.allowsInTripStops) {
                            "Paradas pueden cambiar; el estimado se recalcula"
                        } else {
                            "Paradas cerradas al publicar"
                        },
                        color = if (ride.allowsInTripStops) MeetColors.neonGreen else MeetColors.warning,
                        fontSize = 9.sp,
                    )
                    Text(
                        "Viajes/calificación: dato no capturado",
                        color = MeetColors.textMuted,
                        fontSize = 9.sp,
                    )
                }

                Button(
                    onClick = onOffer,
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("ACEPTAR AHORA ⚡", color = MeetColors.backgroundDark, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun ActiveRidePanel(
    viewModel: ObdViewModel,
    ride: RideRequestEntity,
    isDriver: Boolean,
    onCloseRide: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val offers by viewModel.rideOffers.collectAsState()
    val chatMessages by viewModel.rideChatMessages.collectAsState()
    val isRecording by viewModel.isRecordingAudio.collectAsState()
    val playingPath by viewModel.isPlayingAudio.collectAsState()
    val presetMessages by viewModel.driverPresetMessages.collectAsState()
    val currentGps by viewModel.currentGpsLocation.collectAsState()
    val sharingSelections by viewModel.rideSharingSelections.collectAsState()
    val roadIncidents by viewModel.rideRoadIncidents.collectAsState()
    val speedSamplesByTrip by viewModel.rideSpeedSamples.collectAsState()
    val rideNotifications = remember(context) { RideNotificationCoordinator(context) }

    var chatInputText by remember { mutableStateOf("") }
    var showRatingDialog by remember { mutableStateOf(false) }
    var showCancellationDialog by remember { mutableStateOf(false) }
    var showGuardianDialog by remember { mutableStateOf(false) }
    var showRoadReportDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showActiveStopsDialog by remember { mutableStateOf(false) }
    var pendingActiveStops by remember(ride.requestId, ride.stopsJson) {
        mutableStateOf(
            runCatching { Json.decodeFromString<List<RideStopSnapshot>>(ride.stopsJson) }
                .getOrDefault(emptyList()),
        )
    }
    var pinInput by remember { mutableStateOf("") }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startAudioRecording(context)
        } else {
            Toast.makeText(
                context,
                "El micrófono solo es necesario para enviar mensajes de voz.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let {
            viewModel.sendRideChatImage(
                context = context,
                requestId = ride.requestId,
                senderId = if (isDriver) {
                    viewModel.driverVerification.value?.driverId.orEmpty()
                } else {
                    viewModel.passengerVerification.value?.passengerId.orEmpty()
                },
                senderName = if (isDriver) {
                    viewModel.driverVerification.value?.fullName.orEmpty()
                } else {
                    viewModel.passengerVerification.value?.fullName.orEmpty()
                },
                role = if (isDriver) "DRIVER" else "PASSENGER",
                source = it,
            )
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.ridePinFeedback.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.rideSafetyFeedback.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.rideRoadReportFeedback.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(isDriver, ride.requestId, ride.status, ride.boardingPin) {
        if (!isDriver && ride.status == "ARRIVED" && ride.boardingPin == null) {
            viewModel.issueRideBoardingPin(ride.requestId)
        }
    }
    LaunchedEffect(isDriver, ride.requestId, ride.status) {
        if (isDriver && ride.status in setOf(
                "ACCEPTED",
                "ARRIVED",
                "PASSENGER_ONBOARD",
                "IN_PROGRESS",
            )
        ) {
            while (true) {
                // Arrival authorization requires a fresh fix; keep the shared
                // trip position current instead of trusting the entry snapshot.
                viewModel.detectCurrentLocation(context)
                viewModel.recordRideSpeedObservation(ride.requestId)
                delay(5_000)
            }
        }
    }

    val driverVer by viewModel.driverVerification.collectAsState()
    val passengerVer by viewModel.passengerVerification.collectAsState()
    val myDriverId = driverVer?.driverId
    val myPassengerId = passengerVer?.passengerId

    val myId = if (isDriver) myDriverId else myPassengerId
    val myName = if (isDriver) driverVer?.fullName else passengerVer?.fullName
    val myRole = if (isDriver) "DRIVER" else "PASSENGER"

    if (myId == null || myName.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MeetColors.backgroundDark)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No se puede abrir este viaje sin una identidad verificada.",
                color = MeetColors.warning,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
            )
        }
        return
    }

    val acceptedOffer = remember(offers, ride.acceptedOfferId) {
        offers.firstOrNull {
            it.offerId == ride.acceptedOfferId || it.status == "ACCEPTED"
        }
    }
    val orderedStops = remember(ride.stopsJson) {
        runCatching { Json.decodeFromString<List<RideStopSnapshot>>(ride.stopsJson) }
            .getOrDefault(emptyList())
            .sortedBy(RideStopSnapshot::order)
    }
    val pickupPoint = remember(ride) {
        rideGeoPointOrNull(
            latitude = ride.pickupLatitude,
            longitude = ride.pickupLongitude,
            accuracyMeters = ride.pickupAccuracy,
            capturedAtEpochMs = ride.createdAt,
        )
    }
    val destinationPoint = remember(ride) {
        if (ride.destLatitude == 0.0 && ride.destLongitude == 0.0) {
            null
        } else {
            rideGeoPointOrNull(
                latitude = ride.destLatitude,
                longitude = ride.destLongitude,
                accuracyMeters = null,
                capturedAtEpochMs = ride.createdAt,
            )
        }
    }
    val localPoint = currentGps?.let {
        rideGeoPointOrNull(
            latitude = it.latitude,
            longitude = it.longitude,
            accuracyMeters = it.accuracy,
            capturedAtEpochMs = it.timestamp.coerceAtLeast(0L),
        )
    }
    val acceptedDriverPoint = acceptedOffer
        ?.takeIf { it.driverLatitude != 0.0 || it.driverLongitude != 0.0 }
        ?.let {
            rideGeoPointOrNull(
                latitude = it.driverLatitude,
                longitude = it.driverLongitude,
                accuracyMeters = null,
                capturedAtEpochMs = it.createdAt,
            )
        }
    val arrivalDecision = remember(localPoint, pickupPoint, ride.status) {
        pickupPoint?.let {
            RideArrivalPolicy.evaluate(
                driver = localPoint,
                pickup = it,
                nowEpochMs = System.currentTimeMillis(),
            )
        }
    }
    val routingProvider = remember {
        resilientRideRoutingProvider(
            primaryEndpoint = BuildConfig.RIDE_ROUTER_URL,
            fallbackEndpoint = BuildConfig.RIDE_ROUTER_FALLBACK_URL,
        )
    }
    val activePlaceSearchProvider = remember {
        resilientRidePlaceSearchProvider(
            primaryEndpoint = BuildConfig.RIDE_GEOCODER_URL,
            fallbackEndpoint = BuildConfig.RIDE_GEOCODER_FALLBACK_URL,
        )
    }
    var activeRoadRoute by remember(ride.requestId) {
        mutableStateOf<RideRoadRoute?>(null)
    }
    var activeRouteUnavailable by remember(ride.requestId) {
        mutableStateOf(false)
    }
    LaunchedEffect(ride.requestId, ride.status, pickupPoint, localPoint, orderedStops, destinationPoint) {
        val pickup = if (ride.status == "IN_PROGRESS") localPoint else pickupPoint
        val destination = destinationPoint
        val relevantStops = orderedStops
        val resolvedStops = relevantStops.mapNotNull { stop ->
            if (!stop.isResolved) null else rideGeoPointOrNull(
                latitude = requireNotNull(stop.latitude),
                longitude = requireNotNull(stop.longitude),
                accuracyMeters = null,
                capturedAtEpochMs = ride.createdAt,
            )
        }
        if (
            pickup == null ||
            destination == null ||
            resolvedStops.size != relevantStops.size
        ) {
            activeRoadRoute = null
            activeRouteUnavailable = false
            return@LaunchedEffect
        }
        val result = runCatching {
            routingProvider.route(listOf(pickup) + resolvedStops + destination)
        }
        activeRoadRoute = result.getOrNull()
        activeRouteUnavailable = result.isFailure
    }
    val mapState = remember(
        isDriver,
        pickupPoint,
        orderedStops,
        destinationPoint,
        localPoint,
        acceptedDriverPoint,
        roadIncidents,
        activeRoadRoute,
    ) {
        val baseState = RideMapStateFactory.create(
            passengerGps = if (isDriver) null else localPoint,
            pickup = pickupPoint,
            stops = orderedStops.mapNotNull { stop ->
                if (!stop.isResolved) null else rideGeoPointOrNull(
                    latitude = requireNotNull(stop.latitude),
                    longitude = requireNotNull(stop.longitude),
                    accuracyMeters = null,
                    capturedAtEpochMs = ride.createdAt,
                )
            },
            destination = destinationPoint,
            driverGps = if (isDriver) localPoint else acceptedDriverPoint,
            route = activeRoadRoute?.geometry,
        )
        baseState.copy(
            markers = baseState.markers + roadIncidents
                .filterNot { it.isExpired(System.currentTimeMillis()) }
                .map { incident ->
                    RideMapMarker(
                        id = "incident-${incident.id}",
                        role = RideMarkerRole.ROAD_INCIDENT,
                        point = RideGeoPoint(
                            latitude = incident.latitude,
                            longitude = incident.longitude,
                            accuracyMeters = incident.accuracyMeters,
                            capturedAtEpochMs = incident.createdAtEpochMs,
                        ),
                        label = incident.type.rideRoadLabel(),
                    )
                },
        )
    }
    val collaborativeEta = remember(
        ride.estimatedDistanceKm,
        ride.estimatedDurationMin,
        activeRoadRoute,
        roadIncidents,
        speedSamplesByTrip,
    ) {
        val distanceMeters = activeRoadRoute?.distanceMeters
            ?: ride.estimatedDistanceKm.coerceAtLeast(0.0) * 1_000.0
        val baselineSeconds = activeRoadRoute?.durationSeconds
            ?: ride.estimatedDurationMin.coerceAtLeast(1) * 60.0
        if (distanceMeters <= 0.0) {
            null
        } else {
            RideCollaborativeEtaEstimator.estimate(
                segments = listOf(
                    RideEtaSegment(
                        id = "active-route",
                        distanceMeters = distanceMeters,
                        baselineSpeedMetersPerSecond =
                            (distanceMeters / baselineSeconds).coerceAtLeast(1.4),
                        bearingDegrees = currentGps?.bearing,
                        speedSamples = speedSamplesByTrip[ride.requestId].orEmpty(),
                        incidents = roadIncidents
                            .filterNot { it.isExpired(System.currentTimeMillis()) }
                            .map { it.copy(roadSegmentId = "active-route") },
                    ),
                ),
                nowEpochMs = System.currentTimeMillis(),
            )
        }
    }
    LaunchedEffect(isDriver, ride.requestId, ride.status, collaborativeEta?.durationSeconds) {
        if (isDriver && ride.status == "IN_PROGRESS") {
            collaborativeEta?.durationSeconds?.let { seconds ->
                rideNotifications.notifyDestinationEtaSevenMinutes(ride.requestId, seconds)
            }
        }
    }
    val roadReportAvailability = remember(
        isDriver,
        ride.status,
        ride.serverState,
        ride.serverVersion,
        currentGps != null,
    ) {
        RideRoadReportAvailabilityPolicy.evaluate(
            isDriver = isDriver,
            localStatus = ride.status,
            serverState = ride.serverState,
            serverVersion = ride.serverVersion,
            hasCurrentGps = currentGps != null,
        )
    }
    LaunchedEffect(roadReportAvailability.allowed) {
        if (!roadReportAvailability.allowed) showRoadReportDialog = false
    }

    if (showRoadReportDialog && roadReportAvailability.allowed) {
        RideRoadReportDialog(
            onDismiss = { showRoadReportDialog = false },
            onReport = { type, side, severity ->
                viewModel.reportRideRoadIncident(
                    tripId = ride.requestId,
                    type = type,
                    side = side,
                    severity = severity,
                )
                showRoadReportDialog = false
            },
        )
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            containerColor = Color(0xFF071019),
            title = { Text("CONFIRMAR PASAJERO", color = MeetColors.cyberCyan, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Solicita al pasajero su código privado de cuatro dígitos.",
                        color = MeetColors.textSecondary,
                        fontSize = 12.sp,
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it.filter(Char::isDigit).take(4) },
                        label = { Text("PIN del viaje") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.verifyRideBoardingPin(ride.requestId, pinInput)
                        pinInput = ""
                        showPinDialog = false
                    },
                    enabled = pinInput.length == 4,
                ) {
                    Text("VERIFICAR E INICIAR ABORDAJE", fontWeight = FontWeight.Black)
                }
            },
            dismissButton = { TextButton(onClick = { showPinDialog = false }) { Text("Cancelar") } },
        )
    }

    if (showActiveStopsDialog && !isDriver) {
        AlertDialog(
            onDismissRequest = { showActiveStopsDialog = false },
            containerColor = Color(0xFF071019),
            title = {
                Text(
                    "ACTUALIZAR PARADAS",
                    color = MeetColors.neonGreen,
                    fontWeight = FontWeight.Black,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "La ruta y el estimado se recalcularán. El total definitivo seguirá el tiempo y la distancia reales.",
                        color = MeetColors.textSecondary,
                        fontSize = 11.sp,
                    )
                    RideStopsEditor(
                        stops = pendingActiveStops,
                        provider = activePlaceSearchProvider,
                        biasLatitude = currentGps?.latitude,
                        biasLongitude = currentGps?.longitude,
                        onStopsChanged = {
                            pendingActiveStops = RideTripPlanPolicy.normalize(it)
                        },
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = pendingActiveStops.all(RideStopSnapshot::isResolved),
                    onClick = {
                        val origin = if (ride.status == "IN_PROGRESS") localPoint else pickupPoint
                        val destination = destinationPoint
                        if (origin == null || destination == null) {
                            Toast.makeText(context, "Ubicación de ruta incompleta", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        coroutineScope.launch {
                            val stopPoints = pendingActiveStops.mapNotNull { stop ->
                                if (!stop.isResolved) null else rideGeoPointOrNull(
                                    requireNotNull(stop.latitude),
                                    requireNotNull(stop.longitude),
                                    null,
                                    System.currentTimeMillis(),
                                )
                            }
                            val route = runCatching {
                                routingProvider.route(listOf(origin) + stopPoints + destination)
                            }.getOrNull()
                            if (route == null) {
                                Toast.makeText(
                                    context,
                                    "No se pudo verificar una ruta vial real; no se guardó el cambio.",
                                    Toast.LENGTH_LONG,
                                ).show()
                                return@launch
                            }
                            viewModel.replaceRideStops(
                                requestId = ride.requestId,
                                stopsJson = Json.encodeToString(pendingActiveStops),
                                estimatedDistanceMeters = route.distanceMeters.toLong(),
                                estimatedDurationSeconds = route.durationSeconds.toLong(),
                            )
                            showActiveStopsDialog = false
                        }
                    },
                ) {
                    Text("RECALCULAR Y GUARDAR", fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showActiveStopsDialog = false }) { Text("Cancelar") }
            },
        )
    }

    if (showCancellationDialog) {
        RideCancellationDialog(
            actorRole = if (isDriver) RideActorRole.DRIVER else RideActorRole.PASSENGER,
            onDismiss = { showCancellationDialog = false },
            onConfirm = { reason, detail ->
                viewModel.cancelRide(
                    requestId = ride.requestId,
                    reason = reason,
                    detail = detail,
                    actorRole = myRole,
                )
                showCancellationDialog = false
                onCloseRide()
            },
        )
    }
    if (showGuardianDialog) {
        RideGuardianDialog(
            onDismiss = { showGuardianDialog = false },
            onConfirm = { signalType, detail ->
                viewModel.activateRideGuardian(
                    requestId = ride.requestId,
                    signalType = signalType,
                    detail = detail,
                )
                showGuardianDialog = false
            },
            onShareTrip = {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Elysium Guardian · Viaje ${ride.requestId.take(8)} · Estado ${ride.status}. " +
                            "No contiene teléfono ni ubicación exacta.",
                    )
                }
                runCatching {
                    context.startActivity(
                        Intent.createChooser(shareIntent, "Compartir estado del viaje"),
                    )
                }.onFailure {
                    Toast.makeText(
                        context,
                        "No hay una aplicación disponible para compartir.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
            onOpenEmergencyDialer = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_DIAL))
                }.onFailure {
                    Toast.makeText(
                        context,
                        "No se pudo abrir el marcador del dispositivo.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeetColors.backgroundDark)
            .verticalScroll(rememberScrollState())
    ) {
        // Ride Status Header
        val statusColor = when (ride.status) {
            "OPEN" -> MeetColors.warning
            "ACCEPTED" -> MeetColors.cyberCyan
            "ARRIVED" -> MeetColors.electricBlue
            "PASSENGER_ONBOARD" -> MeetColors.neonGreen
            "IN_PROGRESS" -> MeetColors.neonGreen
            "COMPLETED" -> MeetColors.neonGreen
            else -> MeetColors.error
        }
        val statusLabel = when (ride.status) {
            "OPEN" -> "Buscando Chofer ⏳"
            "ACCEPTED" -> "Chofer en Camino 🚕"
            "ARRIVED" -> "Chofer en el Punto 📍"
            "PASSENGER_ONBOARD" -> "Pasajero confirmado 🔐"
            "IN_PROGRESS" -> "Viaje en Curso 🏁"
            "COMPLETED" -> "Viaje Completado 🎉"
            "CANCELLED" -> "Viaje Cancelado ❌"
            else -> ride.status
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
            border = BorderStroke(1.dp, MeetColors.borderSubtle)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Estado: $statusLabel",
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        fontSize = 15.sp
                    )

                    IconButton(onClick = onCloseRide) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = MeetColors.textMuted)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Desde: ${ride.pickupAddress}",
                    fontSize = 12.sp,
                    color = MeetColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Hasta: ${ride.destAddress}",
                    fontSize = 12.sp,
                    color = MeetColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                orderedStops.forEach { stop ->
                    Text(
                        text = "Parada ${stop.order}: ${stop.label}",
                        fontSize = 11.sp,
                        color = MeetColors.warning,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "Pago: ${if (ride.paymentMethod == "SINPE") "SINPE" else "Efectivo"} · " +
                        if (ride.fareMode == RideFareMode.METERED_TIME_DISTANCE.name) {
                            "Estimado actual: ${ride.estimatedFareMinor} CRC"
                        } else {
                            "Oferta aceptada: ${ride.finalPrice ?: ride.priceOffer} ${ride.currency}"
                        },
                    color = MeetColors.neonGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (!isDriver && ride.status !in setOf("COMPLETED", "CANCELLED")) {
                    if (ride.allowsInTripStops) {
                        OutlinedButton(
                            onClick = { showActiveStopsDialog = true },
                            border = BorderStroke(1.dp, MeetColors.neonGreen),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Icon(Icons.Default.AddLocation, null)
                            Spacer(Modifier.width(8.dp))
                            Text("AÑADIR O CAMBIAR PARADAS", fontWeight = FontWeight.Black)
                        }
                    } else {
                        Text(
                            "🔒 Pon tu precio: las paradas quedaron cerradas al publicar.",
                            color = MeetColors.warning,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                collaborativeEta?.let { estimate ->
                    val minutes = ((estimate.durationSeconds + 59) / 60).coerceAtLeast(1)
                    val evidence = when (estimate.evidenceLevel) {
                        RideEtaEvidenceLevel.BASELINE_ONLY -> "ruta base"
                        RideEtaEvidenceLevel.LIVE_SPEED -> "velocidad reciente"
                        RideEtaEvidenceLevel.COMMUNITY_CORROBORATED -> "tráfico colaborativo"
                    }
                    Text(
                        text = "ETA MEET: $minutes min · $evidence",
                        color = if (estimate.blockingSegmentIds.isEmpty()) {
                            MeetColors.cyberCyan
                        } else {
                            MeetColors.error
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                    )
                    if (estimate.blockingSegmentIds.isNotEmpty()) {
                        Text(
                            text = "Cierre corroborado: recalcular ruta antes de continuar.",
                            color = MeetColors.error,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (ride.status == "COMPLETED") {
                    Surface(
                        color = MeetColors.neonGreen.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "DESGLOSE FINAL",
                                color = MeetColors.neonGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                "Tarifa aceptada: ${ride.priceOffer} ${ride.currency}",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                            )
                            Text(
                                "Ajustes registrados: ninguno",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                            )
                            Text(
                                "TOTAL: ${ride.finalPrice ?: ride.priceOffer} ${ride.currency} · " +
                                    if (ride.paymentMethod == "SINPE") "SINPE" else "Efectivo",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }
                if (!isDriver && ride.status in listOf("ACCEPTED", "ARRIVED")) {
                    ride.boardingPin?.let { pin ->
                        Surface(
                            color = MeetColors.neonGreen.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MeetColors.neonGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Column(
                                Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("TU PIN PRIVADO DE ABORDAJE", color = MeetColors.textMuted, fontSize = 9.sp)
                                Text(
                                    pin.chunked(1).joinToString("  "),
                                    color = MeetColors.neonGreen,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Black,
                                )
                                Text("Díselo únicamente al conductor asignado", color = MeetColors.warning, fontSize = 9.sp)
                            }
                        }
                    }
                    if (ride.boardingPin == null && ride.status == "ACCEPTED") {
                        Surface(
                            color = MeetColors.cyberCyan.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("PIN DE ABORDAJE PROTEGIDO", color = MeetColors.cyberCyan, fontWeight = FontWeight.Black)
                                Text(
                                    "Se mostrará automáticamente cuando el conductor llegue al pin de recogida.",
                                    color = MeetColors.textMuted,
                                    fontSize = 9.sp,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions according to state and role
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isDriver) {
                        when (ride.status) {
                            "ACCEPTED" -> {
                                Button(
                                    onClick = { viewModel.updateRideStatus(ride.requestId, "ARRIVED") },
                                    enabled = ride.syncState != "PENDING" &&
                                        ride.serverVersion > 0L &&
                                        (ride.serverState == "ASSIGNED" || arrivalDecision?.allowed == true),
                                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.electricBlue),
                                    modifier = Modifier.weight(1.2f)
                                ) {
                                    Text(
                                        if (ride.serverState == "ASSIGNED") {
                                            "INICIAR RUTA 🚗"
                                        } else {
                                            arrivalDecision?.distanceMeters?.let { "YA LLEGUÉ · ${it.toInt()} m" }
                                                ?: "YA LLEGUÉ · GPS"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                if (ride.serverState == "DRIVER_EN_ROUTE" && arrivalDecision?.allowed != true) {
                                    Text(
                                        arrivalDecision?.reason ?: "Acércate al pin de recogida",
                                        color = MeetColors.warning,
                                        fontSize = 9.sp,
                                        modifier = Modifier.weight(0.8f),
                                    )
                                }
                                Button(
                                    onClick = { showCancellationDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                                    modifier = Modifier.weight(0.9f)
                                ) {
                                    Text("Cancelar ❌", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            "ARRIVED" -> {
                                Button(
                                    onClick = { showPinDialog = true },
                                    enabled = ride.syncState != "PENDING" &&
                                        ride.serverVersion > 0L,
                                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                                    modifier = Modifier.weight(1.2f)
                                ) {
                                    Text("Ingresar PIN 🔐", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { showCancellationDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                                    modifier = Modifier.weight(0.9f)
                                ) {
                                    Text("Cancelar ❌", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            "PASSENGER_ONBOARD" -> {
                                Button(
                                    onClick = {
                                        viewModel.updateRideStatus(ride.requestId, "IN_PROGRESS")
                                    },
                                    enabled = ride.syncState != "PENDING" &&
                                        ride.serverVersion > 0L,
                                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("INICIAR SERVICIO 🏁", fontSize = 12.sp, fontWeight = FontWeight.Black)
                                }
                            }
                            "IN_PROGRESS" -> {
                                Button(
                                    onClick = {
                                        viewModel.updateRideStatus(ride.requestId, "COMPLETED")
                                    },
                                    enabled = ride.syncState != "PENDING" &&
                                        ride.serverVersion > 0L,
                                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Completar Viaje ✅", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        // Pasajero
                        if (ride.status == "OPEN") {
                            Button(
                                onClick = { showCancellationDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                                modifier = Modifier.weight(1.2f),
                            ) {
                                Text("Cancelar solicitud", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (ride.status == "ARRIVED" && ride.boardingPin == null) {
                            Button(
                                onClick = {
                                    viewModel.issueRideBoardingPin(ride.requestId)
                                },
                                enabled = ride.syncState != "PENDING" &&
                                    ride.serverVersion > 0L,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MeetColors.neonGreen,
                                ),
                                modifier = Modifier.weight(1.2f),
                            ) {
                                Text(
                                    "GENERAR PIN 🔐",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                        }
                        if (ride.status == "COMPLETED" && ride.passengerRating == null) {
                            Button(
                                onClick = { showRatingDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.warning),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Calificar Chofer ⭐", fontWeight = FontWeight.Bold)
                            }
                        }
                        if (ride.status in listOf("ACCEPTED", "ARRIVED")) {
                            Button(
                                onClick = { showCancellationDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Text("Cancelar Viaje ❌", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
            border = BorderStroke(1.5.dp, MeetColors.cyberCyan.copy(alpha = .72f)),
            shape = RoundedCornerShape(22.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
        ) {
            Box(Modifier.fillMaxSize()) {
                RideMapPanel(
                    state = mapState,
                    modifier = Modifier.fillMaxSize(),
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .size(68.dp),
                    shape = CircleShape,
                    color = Color(0xEE07131E),
                    border = BorderStroke(2.dp, MeetColors.cyberCyan),
                    shadowElevation = 10.dp,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            ((currentGps?.speed ?: 0f) * 3.6f).toInt().toString(),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text("km/h", color = MeetColors.cyberCyan, fontSize = 9.sp)
                    }
                }
            }
        }
        Text(
            text = if (isDriver && mapState.marker(com.elysium369.meet.ride.map.RideMarkerRole.PASSENGER_GPS) == null) {
                "GPS exacto del pasajero: esperando sincronización autenticada."
            } else {
                "U: pasajero · R: recogida · P: parada · D: destino · C: conductor"
            },
            color = MeetColors.textMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
        )
        Text(
            text = when {
                activeRoadRoute != null -> {
                    val route = requireNotNull(activeRoadRoute)
                    val km = route.distanceMeters / 1_000.0
                    val roundedKm = kotlin.math.round(km * 10.0) / 10.0
                    val minutes = kotlin.math.ceil(route.durationSeconds / 60.0).toInt()
                    "Ruta vial $roundedKm km · $minutes min · ${route.attribution}"
                }
                activeRouteUnavailable ->
                    "Ruta vial temporalmente no disponible; el mapa conserva puntos reales sin unirlos con una línea falsa."
                else -> "Esperando puntos suficientes para calcular la ruta vial."
            },
            color = if (activeRouteUnavailable) MeetColors.warning else MeetColors.textMuted,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp),
        )
        if (roadReportAvailability.allowed) {
            OutlinedButton(
                onClick = { showRoadReportDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                border = BorderStroke(1.dp, Color(0xFFFF2D55)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B81)),
            ) {
                Icon(Icons.Default.ReportProblem, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("REPORTAR CONDICIÓN DE LA VÍA", fontWeight = FontWeight.Black, fontSize = 11.sp)
            }
        } else if (isDriver && ride.status in setOf("ARRIVED", "PASSENGER_ONBOARD")) {
            Text(
                text = "Los reportes viales aparecerán al iniciar la ruta confirmada.",
                color = MeetColors.textMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )
        }
        OutlinedButton(
            onClick = { showGuardianDialog = true },
            enabled = ride.serverVersion > 0L &&
                ride.serverState in setOf(
                    "ASSIGNED",
                    "DRIVER_EN_ROUTE",
                    "ARRIVED",
                    "PASSENGER_ONBOARD",
                    "IN_PROGRESS",
                ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            border = BorderStroke(1.dp, Color(0xFFFF2D55)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B81)),
        ) {
            Icon(Icons.Default.Security, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("ELYSIUM GUARDIAN · SEGURIDAD", fontWeight = FontWeight.Black, fontSize = 11.sp)
        }

        // Partner Info Card (Visible if ride is ACCEPTED or later status)
        if (ride.status != "OPEN") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (isDriver) {
                        Text(
                            text = "🧑🏻‍💻 PASAJERO ASIGNADO",
                            color = MeetColors.cyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = ride.passengerName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                        Text(
                            text = "Contacto protegido: usa chat o nota de voz dentro del viaje.",
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp,
                        )
                    } else {
                        // Pasajero viendo chofer
                        Text(
                            text = "👨🏻‍✈️ TU CHOFER ASIGNADO",
                            color = MeetColors.cyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val acceptedOffer = remember(offers) { offers.firstOrNull { it.status == "ACCEPTED" } }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ride.assignedDriverName ?: "Chofer MEET",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = ride.assignedDriverVehicle ?: "Vehículo: dato no capturado",
                                    color = MeetColors.textSecondary,
                                    fontSize = 12.sp
                                )
                                if (acceptedOffer != null) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (acceptedOffer.driverTotalTrips > 0) {
                                            "⭐ ${acceptedOffer.driverRating} (${acceptedOffer.driverTotalTrips} viajes)"
                                        } else {
                                            "Conductor nuevo · sin historial de viajes capturado"
                                        },
                                        color = MeetColors.warning,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                        }
                        Text(
                            text = "Contacto protegido: usa chat o nota de voz dentro del viaje.",
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            val phone = if (isDriver) ride.passengerPhone else ride.assignedDriverPhone
                            if (!viewModel.openRideCallDialer(context, phone)) {
                                Toast.makeText(
                                    context,
                                    "Número de contacto no disponible. Usa el chat del viaje.",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = .75f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.neonGreen),
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("LLAMAR CON EL TELÉFONO", fontWeight = FontWeight.Black, fontSize = 11.sp)
                    }
                    Text(
                        text = "Abre el marcador del dispositivo; la llamada no se presenta como anónima ni enmascarada.",
                        color = MeetColors.textMuted,
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                    )
                }
            }
        }

        if (!isDriver && ride.status != "OPEN") {
            RidePassengerTrustCard(
                vehicleDescription = ride.assignedDriverVehicle,
                sharedCategories = sharingSelections[ride.requestId].orEmpty(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // Bids / Negotiation Panel if status is OPEN
        if (ride.status == "OPEN") {
            if (isDriver) {
                DriverNegotiationPanel(
                    viewModel = viewModel,
                    ride = ride,
                    offers = offers,
                    onCloseRide = onCloseRide
                )
            } else {
                PassengerLiveOffersPanel(
                    viewModel = viewModel,
                    ride = ride,
                    offers = offers
                )
            }
        }

        // Chat View (Visible if ride is ACCEPTED or later status)
        if (ride.status != "OPEN") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .background(MeetColors.backgroundDark)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Chat messages list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(chatMessages) { message ->
                            val isMe = message.senderId == myId
                            val alignment = if (isMe) Alignment.End else Alignment.Start
                            val bubbleColor = if (isMe) MeetColors.electricBlue.copy(alpha = 0.25f) else MeetColors.cardBackground

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = alignment
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = 12.dp,
                                                topEnd = 12.dp,
                                                bottomStart = if (isMe) 12.dp else 0.dp,
                                                bottomEnd = if (isMe) 0.dp else 12.dp
                                            )
                                        )
                                        .background(bubbleColor)
                                        .border(
                                            width = 1.dp,
                                            color = if (isMe) MeetColors.electricBlue else MeetColors.borderSubtle,
                                            shape = RoundedCornerShape(
                                                topStart = 12.dp,
                                                topEnd = 12.dp,
                                                bottomStart = if (isMe) 12.dp else 0.dp,
                                                bottomEnd = if (isMe) 0.dp else 12.dp
                                            )
                                        )
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        if (!isMe) {
                                            Text(
                                                text = message.senderName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = MeetColors.cyberCyan
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }

                                        when (message.messageType) {
                                            "TEXT", "PRESET" -> {
                                                Text(
                                                    text = message.textContent ?: "",
                                                    color = Color.White,
                                                    fontSize = 14.sp
                                                )
                                            }
                                            "AUDIO" -> {
                                                val isPlaying = playingPath == message.audioFilePath
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.clickable {
                                                        message.audioFilePath?.let { viewModel.playAudioMessage(it) }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                        contentDescription = if (isPlaying) "Pausar audio" else "Reproducir audio",
                                                        tint = MeetColors.neonGreen
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "Mensaje de voz (${(message.audioDurationMs ?: 0L) / 1000}s)",
                                                        color = Color.White,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                            }
                                            "IMAGE" -> {
                                                val imagePath = message.imageFilePath
                                                if (imagePath != null) {
                                                    AsyncImage(
                                                        model = java.io.File(imagePath),
                                                        contentDescription = "Imagen enviada en el chat del viaje",
                                                        modifier = Modifier
                                                            .widthIn(max = 260.dp)
                                                            .heightIn(min = 120.dp, max = 240.dp)
                                                            .clip(RoundedCornerShape(12.dp)),
                                                    )
                                                } else {
                                                    Text(
                                                        "Imagen pendiente de descarga",
                                                        color = MeetColors.warning,
                                                        fontSize = 12.sp,
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = when (message.syncState) {
                                                "SYNCED" -> "Entregado"
                                                "FAILED" -> "No sincronizado · toca para reintentar"
                                                "LOCAL_ONLY" -> "Guardado en este dispositivo"
                                                else -> "Pendiente de sincronización"
                                            },
                                            color = MeetColors.textMuted,
                                            fontSize = 8.sp,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Presets for Quick Messaging (For Driver to click in one-tap)
                    if (isDriver && ride.status != "COMPLETED") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MeetColors.backgroundDeep)
                        ) {
                            items(presetMessages) { preset ->
                                SuggestionChip(
                                    onClick = {
                                        viewModel.sendRidePresetMessage(
                                            requestId = ride.requestId,
                                            senderId = myId,
                                            senderName = myName,
                                            role = myRole,
                                            presetText = preset
                                        )
                                    },
                                    label = { Text(preset, color = MeetColors.cyberCyan, fontSize = 11.sp) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MeetColors.cardBackground
                                    )
                                )
                            }
                        }
                    }

                    // Input chat controls
                    if (ride.status != "COMPLETED") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MeetColors.backgroundDeep)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MeetColors.cardBackground),
                            ) {
                                Icon(
                                    Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Enviar imagen",
                                    tint = Color(0xFFC85CFF),
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            // Hold-to-record voice message button
                            IconButton(
                                onClick = {
                                    if (isRecording) {
                                        viewModel.stopAndSendAudioRecording(ride.requestId, myId, myName, myRole)
                                    } else if (
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO,
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        viewModel.startAudioRecording(context)
                                    } else {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isRecording) MeetColors.error else MeetColors.cardBackground)
                            ) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = if (isRecording) "Detener y enviar audio" else "Grabar audio",
                                    tint = if (isRecording) Color.White else MeetColors.cyberCyan
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            OutlinedTextField(
                                value = chatInputText,
                                onValueChange = { chatInputText = it },
                                label = { Text("Escribe un mensaje...", color = MeetColors.textMuted) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MeetColors.cyberCyan,
                                    unfocusedBorderColor = MeetColors.borderSubtle,
                                    focusedLabelColor = MeetColors.cyberCyan
                                )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    if (chatInputText.isNotBlank()) {
                                        viewModel.sendRideChatMessage(
                                            ride.requestId,
                                            myId,
                                            myName,
                                            myRole,
                                            chatInputText
                                        )
                                        chatInputText = ""
                                    }
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MeetColors.cyberCyan)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Enviar",
                                    tint = MeetColors.backgroundDark
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRatingDialog) {
        var ratingStars by remember { mutableDoubleStateOf(5.0) }
        var ratingComment by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showRatingDialog = false },
            containerColor = MeetColors.backgroundDeep,
            title = {
                Text(
                    text = if (isDriver) "Calificar al Pasajero" else "Calificar al Conductor",
                    color = Color.White
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("¡Tu opinión ayuda a mantener la comunidad segura!", color = MeetColors.textSecondary)

                    // 5 Star rating selection
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 1..5) {
                            val active = ratingStars >= i.toDouble()
                            IconButton(onClick = { ratingStars = i.toDouble() }) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = if (active) MeetColors.warning else MeetColors.textMuted,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = ratingComment,
                        onValueChange = { ratingComment = it },
                        label = { Text("Escribe un comentario...") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeetColors.cyberCyan,
                            unfocusedBorderColor = MeetColors.borderSubtle
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.submitRideRating(ride.requestId, !isDriver, ratingStars, ratingComment)
                        showRatingDialog = false
                        Toast.makeText(context, "Calificación guardada con éxito", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen)
                ) {
                    Text("Guardar", color = MeetColors.backgroundDark)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRatingDialog = false }) {
                    Text("Omitir", color = MeetColors.textMuted)
                }
            }
        )
    }
}

private fun RideRoadIncidentType.rideRoadLabel(): String = when (this) {
    RideRoadIncidentType.SLOW_TRAFFIC -> "Tráfico lento"
    RideRoadIncidentType.VERY_SLOW_TRAFFIC -> "Tráfico muy lento"
    RideRoadIncidentType.STALLED_VEHICLE -> "Vehículo varado"
    RideRoadIncidentType.POTHOLE -> "Bache"
    RideRoadIncidentType.OBSTACLE -> "Obstáculo"
    RideRoadIncidentType.ROAD_CLOSED -> "Calle cerrada"
    RideRoadIncidentType.WRONG_WAY_HAZARD -> "Riesgo de contravía"
    RideRoadIncidentType.POLICE_PRESENCE -> "Presencia policial"
    RideRoadIncidentType.TRAFFIC_CONTROL -> "Control de tránsito"
    RideRoadIncidentType.PUBLIC_POLICE -> "Policía pública"
    RideRoadIncidentType.TRAFFIC_POLICE -> "Policía de tránsito"
    RideRoadIncidentType.SPEED_BUMP -> "Reductor / muerto"
    RideRoadIncidentType.FLOODING -> "Calle inundada"
}

@Composable
private fun RideRoadReportDialog(
    onDismiss: () -> Unit,
    onReport: (RideRoadIncidentType, RideRoadSide, Int) -> Unit,
) {
    var type by remember { mutableStateOf(RideRoadIncidentType.SLOW_TRAFFIC) }
    var side by remember { mutableStateOf(RideRoadSide.NOT_APPLICABLE) }
    var severity by remember { mutableIntStateOf(2) }
    val sideRelevant = type in setOf(
        RideRoadIncidentType.STALLED_VEHICLE,
        RideRoadIncidentType.OBSTACLE,
        RideRoadIncidentType.POTHOLE,
        RideRoadIncidentType.SPEED_BUMP,
        RideRoadIncidentType.FLOODING,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xEE071019),
        title = {
            Column {
                Text("INTELIGENCIA VIAL", color = MeetColors.cyberCyan, fontWeight = FontWeight.Black)
                Text("Reporta solo cuando sea seguro y estés detenido.", color = MeetColors.warning, fontSize = 10.sp)
            }
        },
        text = {
            Column(
                Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RideRoadIncidentType.entries.forEach { candidate ->
                    Surface(
                        color = if (type == candidate) {
                            MeetColors.cyberCyan.copy(alpha = 0.18f)
                        } else {
                            MeetColors.cardBackground
                        },
                        border = BorderStroke(
                            1.dp,
                            if (type == candidate) MeetColors.cyberCyan else MeetColors.borderSubtle,
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().clickable {
                            type = candidate
                            if (candidate !in setOf(
                                    RideRoadIncidentType.STALLED_VEHICLE,
                                    RideRoadIncidentType.OBSTACLE,
                                    RideRoadIncidentType.POTHOLE,
                                    RideRoadIncidentType.SPEED_BUMP,
                                    RideRoadIncidentType.FLOODING,
                                )
                            ) side = RideRoadSide.NOT_APPLICABLE
                        },
                    ) {
                        Text(
                            candidate.rideRoadLabel(),
                            color = Color.White,
                            modifier = Modifier.padding(11.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                    }
                }
                if (sideRelevant) {
                    Text("UBICACIÓN EN LA VÍA", color = MeetColors.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            RideRoadSide.LEFT to "Izquierda",
                            RideRoadSide.CENTER to "Centro",
                            RideRoadSide.RIGHT to "Derecha",
                        ).forEach { (candidate, label) ->
                            FilterChip(
                                selected = side == candidate,
                                onClick = { side = candidate },
                                label = { Text(label, fontSize = 9.sp) },
                            )
                        }
                    }
                }
                Text("SEVERIDAD $severity/3", color = MeetColors.warning, fontSize = 10.sp, fontWeight = FontWeight.Black)
                Slider(
                    value = severity.toFloat(),
                    onValueChange = { severity = it.toInt().coerceIn(1, 3) },
                    valueRange = 1f..3f,
                    steps = 1,
                )
                Text(
                    "El reporte caduca automáticamente y se compara con confirmaciones, dirección, precisión GPS y velocidades observadas.",
                    color = MeetColors.textMuted,
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onReport(type, if (sideRelevant) side else RideRoadSide.NOT_APPLICABLE, severity) },
                enabled = !sideRelevant || side != RideRoadSide.NOT_APPLICABLE,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2D55)),
            ) {
                Text("PUBLICAR REPORTE", fontWeight = FontWeight.Black)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// Haversine formula to compute distance in km between two GPS coordinates
private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0 // Earth radius in km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return r * c
}

/**
 * Sanitiza un `addressName` para que no filtre lat/lon cuando el geocoder falló.
 *
 * Defensa en profundidad: builds anteriores podian guardar
 * `addressName = "Ubicación GPS (lat, lng)"` cuando fallaba el geocoder.
 * Si eso se persiste en una Room row o se envía al backend, las coords exactas
 * quedan expuestas en un canal que no debería tenerlas.
 *
 * Detecta el patrón "Ubicación GPS (<number>, <number>)" y lo reemplaza por un label
 * genérico. Si no coincide el patrón, devuelve la entrada sin modificar.
 *
 * `internal` para que SanitizeGpsAddressTest del mismo módulo pueda validar el regex.
 *
 * (RISK-3: GPS data leak via fallback address string)
 */
internal fun sanitizeGpsAddress(raw: String): String {
    val regex = Regex("^Ubicación GPS \\(-?\\d+\\.?\\d*, -?\\d+\\.?\\d*\\)$")
    return if (regex.matches(raw)) "Ubicación GPS detectada" else raw
}

// ─── Passenger Verification Dialog ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaxVerificationDialog(
    paxName: String,
    onNameChange: (String) -> Unit,
    paxPhone: String,
    onPhoneChange: (String) -> Unit,
    paxProfilePhoto: String,
    onProfileCapture: (String) -> Unit,
    paxCedulaFront: String,
    onCedulaCapture: (String) -> Unit,
    paxSelfieWithCedula: String,
    onSelfieCapture: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    val accent = MeetColors.electricBlue
    val isValid = paxName.isNotBlank() && paxPhone.isNotBlank() &&
            paxProfilePhoto.isNotBlank() && paxCedulaFront.isNotBlank() &&
            paxSelfieWithCedula.isNotBlank()

    val launchVerificationPhoto = rememberVerificationPhotoCapture()

    var captureGuideType by remember { mutableStateOf<String?>(null) }
    var onCaptureGuideProceed by remember { mutableStateOf<(() -> Unit)?>(null) }

    val triggerPhotoCapture = { docType: String, callback: (String) -> Unit ->
        captureGuideType = docType
        onCaptureGuideProceed = {
            captureGuideType = null
            launchVerificationPhoto(
                "passenger",
                docType,
                callback,
            )
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            containerColor = MeetColors.backgroundDark,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Verificación de Pasajero",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, "Cerrar", tint = accent)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep)
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            Text(
                                text = "Por favor, completa los siguientes datos para verificar tu identidad y mantener los viajes seguros:",
                                color = MeetColors.textSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }

                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MeetColors.electricBlue.copy(alpha = 0.10f),
                                border = BorderStroke(
                                    1.dp,
                                    MeetColors.electricBlue.copy(alpha = 0.35f),
                                ),
                            ) {
                                Text(
                                    text = "🧪 Piloto Costa Rica: al completar estos datos y fotos se habilitarán los viajes inmediatamente. La evidencia queda guardada para validación posterior.",
                                    color = MeetColors.textSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.padding(14.dp),
                                )
                            }
                        }

                        item {
                            OutlinedTextField(
                                value = paxName,
                                onValueChange = onNameChange,
                                label = { Text("Nombre Completo") },
                                placeholder = { Text("Ej: María López Rodríguez", color = MeetColors.textMuted) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MeetColors.textPrimary,
                                    unfocusedTextColor = MeetColors.textPrimary,
                                    cursorColor = accent,
                                    focusedBorderColor = accent,
                                    unfocusedBorderColor = MeetColors.borderSubtle,
                                    focusedLabelColor = accent,
                                    unfocusedLabelColor = MeetColors.textSecondary,
                                    focusedContainerColor = MeetColors.cardBackground,
                                    unfocusedContainerColor = MeetColors.cardBackground
                                )
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = paxPhone,
                                onValueChange = onPhoneChange,
                                label = { Text("Teléfono") },
                                placeholder = { Text("Ej: +506 8888-8888", color = MeetColors.textMuted) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MeetColors.textPrimary,
                                    unfocusedTextColor = MeetColors.textPrimary,
                                    cursorColor = accent,
                                    focusedBorderColor = accent,
                                    unfocusedBorderColor = MeetColors.borderSubtle,
                                    focusedLabelColor = accent,
                                    unfocusedLabelColor = MeetColors.textSecondary,
                                    focusedContainerColor = MeetColors.cardBackground,
                                    unfocusedContainerColor = MeetColors.cardBackground
                                )
                            )
                        }

                        item {
                            PaxDocButton("📸 Foto de Perfil", "Foto frontal clara de tu rostro", paxProfilePhoto.isNotBlank()) {
                                triggerPhotoCapture("SELFIE_PROFILE", onProfileCapture)
                            }
                        }

                        item {
                            PaxDocButton("🪪 Cédula de Identidad (Frente)", "Foto legible de tu documento de identidad", paxCedulaFront.isNotBlank()) {
                                triggerPhotoCapture("CEDULA_FRONT", onCedulaCapture)
                            }
                        }

                        item {
                            PaxDocButton("🤳 Selfie Sosteniendo Cédula", "Foto tuya sosteniendo la cédula junto a tu cara", paxSelfieWithCedula.isNotBlank()) {
                                triggerPhotoCapture("SELFIE_WITH_CEDULA", onSelfieCapture)
                            }
                        }
                    }

                    Button(
                        onClick = onSubmit,
                        enabled = isValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MeetColors.neonGreen,
                            contentColor = Color.Black,
                            disabledContainerColor = MeetColors.neonGreen.copy(alpha = 0.2f),
                            disabledContentColor = MeetColors.textMuted
                        )
                    ) {
                        Text("🚀 ENVIAR VERIFICACIÓN", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    }
                }

                // Overlay Guide Dialog
                if (captureGuideType != null) {
                    CaptureGuideOverlay(
                        documentType = captureGuideType!!,
                        onDismiss = { captureGuideType = null },
                        onProceed = { onCaptureGuideProceed?.invoke() }
                    )
                }
            }
        }
    }
}

@Composable
private fun PaxDocButton(
    label: String,
    description: String,
    isCaptured: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCaptured) Color(0xFF0D2818) else MeetColors.cardBackground
        ),
        border = BorderStroke(
            1.dp,
            if (isCaptured) MeetColors.neonGreen.copy(alpha = 0.5f) else MeetColors.borderSubtle
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    color = if (isCaptured) MeetColors.neonGreen else MeetColors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(description, color = MeetColors.textSecondary, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (isCaptured) {
                Icon(Icons.Filled.CheckCircle, null, tint = MeetColors.neonGreen, modifier = Modifier.size(28.dp))
            } else {
                Icon(Icons.Filled.CameraAlt, null, tint = MeetColors.electricBlue, modifier = Modifier.size(28.dp))
            }
        }
    }
}

// ─── Animations and Indicators ───────────────────────────────────────────────

@Composable
fun BouncingRadarIndicator() {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "radar")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.3f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1500, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1500, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
            .clip(CircleShape)
            .background(MeetColors.cyberCyan.copy(alpha = 0.2f))
            .border(2.dp, MeetColors.cyberCyan, CircleShape)
    )
}

// ─── Passenger Live Bids Panel ───────────────────────────────────────────────

@Composable
fun PassengerLiveOffersPanel(
    viewModel: ObdViewModel,
    ride: RideRequestEntity,
    offers: List<RideOfferEntity>
) {
    val currentLocale = rememberRideJavaLocale()
    var showCancellationDialog by remember { mutableStateOf(false) }
    val pendingOffers = remember(offers) { offers.filter { it.status == "PENDING" } }
    val elapsedMs = System.currentTimeMillis() - ride.createdAt
    val elapsedMins = (elapsedMs / (1000 * 60)).toInt()
    val timeText = if (elapsedMins <= 0) "hace un momento" else "hace $elapsedMins min"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Current Bid Display Card with +/- stepper
        Card(
            colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
            border = BorderStroke(1.dp, MeetColors.borderSubtle),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Tu Oferta Actual",
                    color = MeetColors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                // Elysium Vanguard fare stepper
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            viewModel.updateRidePrice(
                                ride.requestId,
                                RideFareBidPolicy.adjust(ride.priceOffer, ride.currency, -1),
                            )
                        },
                        modifier = Modifier.background(MeetColors.borderSubtle, CircleShape)
                    ) {
                        Text("-", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = if (ride.currency == "USD") {
                            "$${ride.priceOffer.toInt()}"
                        } else {
                            "₡${String.format(currentLocale, "%,.0f", ride.priceOffer)} CRC"
                        },
                        color = MeetColors.neonGreen,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black
                    )

                    IconButton(
                        onClick = {
                            viewModel.updateRidePrice(
                                ride.requestId,
                                RideFareBidPolicy.adjust(ride.priceOffer, ride.currency, 1),
                            )
                        },
                        modifier = Modifier.background(MeetColors.borderSubtle, CircleShape)
                    ) {
                        Text("+", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "⚡ Incrementar tarifa rápidamente:",
                    color = MeetColors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(300.0, 900.0, 1500.0).forEach { amount ->
                        val label = if (ride.currency == "USD") "+$${(amount / 500).toInt()}" else "+₡${amount.toInt()}"
                        val valToAdd = if (ride.currency == "USD") amount / 500.0 else amount
                        Button(
                            onClick = { viewModel.updateRidePrice(ride.requestId, ride.priceOffer + valToAdd) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MeetColors.cardBackground,
                                contentColor = MeetColors.cyberCyan
                            ),
                            border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.3f)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { showCancellationDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350), contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("❌ CANCELAR SOLICITUD DE VIAJE", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
        }

        if (showCancellationDialog) {
            RideCancellationDialog(
                actorRole = RideActorRole.PASSENGER,
                onDismiss = { showCancellationDialog = false },
                onConfirm = { reason, detail ->
                    viewModel.cancelRide(
                        requestId = ride.requestId,
                        reason = reason,
                        detail = detail,
                        actorRole = RideActorRole.PASSENGER.name,
                    )
                    showCancellationDialog = false
                },
            )
        }

        // Radar search indicator with elapsed time
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            BouncingRadarIndicator()
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    "Buscando choferes...",
                    color = MeetColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    "Iniciado $timeText. Ofertas abajo.",
                    color = MeetColors.textMuted,
                    fontSize = 12.sp
                )
            }
        }

        // Offers list
        Text(
            text = "Ofertas recibidas (${pendingOffers.size})",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )

        if (pendingOffers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Esperando contraofertas de conductores...",
                    color = MeetColors.textMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(pendingOffers) { offer ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                            border = BorderStroke(1.dp, MeetColors.borderSubtle),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = offer.driverName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = MeetColors.cyberCyan.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    "Identidad revisada",
                                                    color = MeetColors.cyberCyan,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (offer.driverTotalTrips > 0) {
                                                "⭐ ${offer.driverRating} (${offer.driverTotalTrips} viajes)"
                                            } else {
                                                "Conductor nuevo · sin historial capturado"
                                            },
                                            color = MeetColors.warning,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(text = offer.vehicleDescription, color = MeetColors.textSecondary, fontSize = 12.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(text = "${offer.counterPrice.toInt()} ${offer.currency}", fontWeight = FontWeight.Black, color = MeetColors.neonGreen, fontSize = 18.sp)
                                        Text(text = "Llega en: ${offer.estimatedArrivalMin} min", color = MeetColors.cyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }

                                if (!offer.message.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = "\"${offer.message}\"", color = MeetColors.textMuted, fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.rejectRideOffer(ride.requestId, offer.offerId) },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350)),
                                        border = BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.5f)),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Rechazar", fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { viewModel.acceptRideOffer(ride.requestId, offer.offerId) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen, contentColor = Color.Black),
                                        modifier = Modifier.weight(1.3f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Aceptar Chofer 🚕", fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
// ─── Driver Negotiation Panel ────────────────────────────────────────────────

@Composable
fun DriverNegotiationPanel(
    viewModel: ObdViewModel,
    ride: RideRequestEntity,
    offers: List<RideOfferEntity>,
    onCloseRide: () -> Unit
) {
    val context = LocalContext.current
    val currentGps by viewModel.currentGpsLocation.collectAsState()
    val driverVer by viewModel.driverVerification.collectAsState()

    val myDriverId = driverVer?.driverId
    // Only find pending offer from this driver
    val myOffer = offers.firstOrNull { it.driverId == myDriverId && it.status == "PENDING" }
    // Detect if this driver's offer was recently rejected
    val wasRejected = remember(offers) { offers.any { it.driverId == myDriverId && it.status == "REJECTED" } }

    if (myOffer != null) {
        // Driver has submitted an offer and is waiting for response
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                border = BorderStroke(1.dp, MeetColors.warning.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BouncingRadarIndicator()
                    
                    Text(
                        "OFERTA ENVIADA CON ÉXITO",
                        color = MeetColors.warning,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )

                    androidx.compose.material3.HorizontalDivider(color = MeetColors.borderSubtle, thickness = 1.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tu tarifa propuesta:", color = MeetColors.textSecondary, fontSize = 13.sp)
                        Text("${myOffer.counterPrice.toInt()} ${myOffer.currency}", color = MeetColors.neonGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tiempo estimado:", color = MeetColors.textSecondary, fontSize = 13.sp)
                        Text("${myOffer.estimatedArrivalMin} min", color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Text(
                        text = "Esperando que el cliente acepte tu oferta. Si es aceptada, la app te notificará y abrirá el chat de inmediato.",
                        color = MeetColors.textMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }

            Button(
                onClick = {
                    viewModel.rejectRideOffer(ride.requestId, myOffer.offerId)
                    onCloseRide()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350), contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("❌ RETIRAR MI OFERTA", fontWeight = FontWeight.Bold)
            }
        }
    } else {
        // Driver has not made an offer yet, show the auction bidding UI
        var counterPrice by remember { mutableDoubleStateOf(ride.priceOffer) }
        var selectedEta by remember { mutableIntStateOf(10) }
        var driverMsg by remember { mutableStateOf("") }

        val distanceText = remember(currentGps) {
            if (currentGps != null) {
                val dist = calculateDistance(
                    currentGps!!.latitude, currentGps!!.longitude,
                    ride.pickupLatitude, ride.pickupLongitude
                )
                String.format(java.util.Locale.US, "📍 A %.1f km de tu posición", dist)
            } else {
                "📍 Ubicación de recogida disponible"
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (wasRejected) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3E1F21)),
                    border = BorderStroke(1.dp, Color(0xFFEF5350)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = Color(0xFFEF5350))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "El pasajero rechazó tu oferta anterior. Puedes proponer una nueva tarifa si lo deseas.",
                            color = Color(0xFFFFCDD2),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Ride details card
            Card(
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = distanceText, color = MeetColors.cyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Tarifa propuesta por cliente: ${ride.priceOffer.toInt()} ${ride.currency}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            // Price negotiation
            Text(
                text = "Determina tu Tarifa:",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            // Current price display
            Card(
                colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { 
                            val step = if (ride.currency == "USD") 1.0 else 500.0
                            if (counterPrice > step) counterPrice -= step 
                        },
                        modifier = Modifier.background(MeetColors.borderSubtle, CircleShape)
                    ) {
                        Text("-", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = "${counterPrice.toInt()} ${ride.currency}",
                        color = MeetColors.neonGreen,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )

                    IconButton(
                        onClick = { 
                            val step = if (ride.currency == "USD") 1.0 else 500.0
                            counterPrice += step 
                        },
                        modifier = Modifier.background(MeetColors.borderSubtle, CircleShape)
                    ) {
                        Text("+", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Quick bid chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val offsets = if (ride.currency == "USD") listOf(0.0, 1.0, 2.0, 4.0) else listOf(0.0, 500.0, 1000.0, 2000.0)
                offsets.forEach { offset ->
                    val total = ride.priceOffer + offset
                    val label = if (offset == 0.0) "Aceptar" else {
                        if (ride.currency == "USD") "+$${offset.toInt()}" else "+₡${offset.toInt()}"
                    }
                    Button(
                        onClick = { counterPrice = total },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (counterPrice == total) MeetColors.cyberCyan else MeetColors.cardBackground,
                            contentColor = if (counterPrice == total) Color.Black else Color.White
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }

            // ETA Selector
            Text(
                text = "Tiempo estimado de llegada (ETA):",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(5, 10, 15, 20).forEach { mins ->
                    Button(
                        onClick = { selectedEta = mins },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedEta == mins) MeetColors.cyberCyan else MeetColors.cardBackground,
                            contentColor = if (selectedEta == mins) Color.Black else Color.White
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("${mins} min", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }

            // Message field
            OutlinedTextField(
                value = driverMsg,
                onValueChange = { driverMsg = it },
                label = { Text("Nota al pasajero (opcional)") },
                placeholder = { Text("Ej: Llevo aire acondicionado, auto limpio") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MeetColors.cyberCyan,
                    unfocusedBorderColor = MeetColors.borderSubtle
                )
            )

            // Submit offer button
            Button(
                onClick = {
                    val verifiedDriver = driverVer
                    val gps = currentGps
                    if (
                        verifiedDriver == null ||
                        !RideVerificationPolicy.grantsAccess(verifiedDriver.status)
                    ) {
                        Toast.makeText(
                            context,
                            "Se requiere identidad de conductor aprobada",
                            Toast.LENGTH_LONG,
                        ).show()
                        return@Button
                    }
                    if (gps == null) {
                        Toast.makeText(
                            context,
                            "Esperando una ubicación GPS válida",
                            Toast.LENGTH_LONG,
                        ).show()
                        return@Button
                    }
                    viewModel.makeRideOffer(
                        requestId = ride.requestId,
                        driverId = verifiedDriver.driverId,
                        driverName = verifiedDriver.fullName,
                        driverPhone = verifiedDriver.phone,
                        driverRating = 0.0,
                        driverTotalTrips = 0,
                        vehicleDesc = "${verifiedDriver.vehicleMake} ${verifiedDriver.vehicleModel} ${verifiedDriver.vehicleYear} (${verifiedDriver.vehicleColor}) [${verifiedDriver.vehiclePlate}]",
                        counterPrice = counterPrice,
                        currency = ride.currency,
                        estArrivalMin = selectedEta,
                        driverLat = gps.latitude,
                        driverLng = gps.longitude,
                        message = driverMsg.takeIf { it.isNotBlank() }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🚀 ENVIAR TARIFACIÓN", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
        }
    }
}

// ─── Capture Guide Overlay with Cyber Scanner Animation ──────────────────────

private @Composable
fun CaptureGuideOverlay(
    documentType: String,
    onDismiss: () -> Unit,
    onProceed: () -> Unit
) {
    val accent = MeetColors.cyberCyan
    val infiniteTransition = rememberInfiniteTransition(label = "scanline")
    val scanLineProgress by infiniteTransition.animateFloat(
        initialValue = 0.0f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLineProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(enabled = true, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
            border = BorderStroke(1.dp, MeetColors.borderSubtle)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val title = when (documentType) {
                    "CEDULA_FRONT" -> "🆔 Cédula de Identidad (Frente)"
                    "CEDULA_BACK" -> "🆔 Cédula de Identidad (Reverso)"
                    "LICENCIA_FRONT" -> "🪪 Licencia de Conducir (Frente)"
                    "LICENCIA_BACK" -> "🪪 Licencia de Conducir (Reverso)"
                    "SELFIE_PROFILE" -> "📸 Foto de Perfil (Selfie)"
                    "SELFIE_WITH_CEDULA" -> "🤳 Selfie con Cédula al Lado del Rostro"
                    "SELFIE_WITH_LICENCIA" -> "🤳 Selfie con Licencia Lado del Rostro"
                    "MARCHAMO" -> "📜 Foto de Marchamo"
                    "DEKRA" -> "🔧 Foto de DEKRA / RTV"
                    "SEGURO" -> "🛡️ Foto de Seguro Vehicular"
                    "HOJA" -> "📋 Hoja de Delincuencia"
                    "VEHICLE_FRONT" -> "🚗 Foto Frontal del Vehículo"
                    "VEHICLE_BACK" -> "🚗 Foto Trasera del Vehículo"
                    "VEHICLE_INT" -> "🪑 Foto Interior del Vehículo"
                    else -> "Toma de Foto"
                }

                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                // Interactive Scanner Animation
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MeetColors.cardBackground)
                        .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // Draw corner guidelines
                        val strokeW = 3.dp.toPx()
                        val cornerLen = 20.dp.toPx()

                        // Guidelines corners
                        listOf(
                            Offset(12.dp.toPx(), 12.dp.toPx()) to Offset(12.dp.toPx() + cornerLen, 12.dp.toPx()),
                            Offset(12.dp.toPx(), 12.dp.toPx()) to Offset(12.dp.toPx(), 12.dp.toPx() + cornerLen),
                            Offset(w - 12.dp.toPx(), 12.dp.toPx()) to Offset(w - 12.dp.toPx() - cornerLen, 12.dp.toPx()),
                            Offset(w - 12.dp.toPx(), 12.dp.toPx()) to Offset(w - 12.dp.toPx(), 12.dp.toPx() + cornerLen),
                            Offset(12.dp.toPx(), h - 12.dp.toPx()) to Offset(12.dp.toPx() + cornerLen, h - 12.dp.toPx()),
                            Offset(12.dp.toPx(), h - 12.dp.toPx()) to Offset(12.dp.toPx(), h - 12.dp.toPx() - cornerLen),
                            Offset(w - 12.dp.toPx(), h - 12.dp.toPx()) to Offset(w - 12.dp.toPx() - cornerLen, h - 12.dp.toPx()),
                            Offset(w - 12.dp.toPx(), h - 12.dp.toPx()) to Offset(w - 12.dp.toPx(), h - 12.dp.toPx() - cornerLen)
                        ).forEach { (start, end) ->
                            drawLine(accent, start, end, strokeW)
                        }

                        // Draw silhouettes
                        when (documentType) {
                            "CEDULA_FRONT", "CEDULA_BACK", "LICENCIA_FRONT", "LICENCIA_BACK", "MARCHAMO", "DEKRA", "SEGURO", "HOJA" -> {
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.15f),
                                    topLeft = Offset(30.dp.toPx(), 50.dp.toPx()),
                                    size = Size(w - 60.dp.toPx(), h - 100.dp.toPx()),
                                    cornerRadius = CornerRadius(6.dp.toPx()),
                                    style = Stroke(2.dp.toPx())
                                )
                                drawLine(
                                    color = Color.White.copy(alpha = 0.2f),
                                    start = Offset(42.dp.toPx(), 70.dp.toPx()),
                                    end = Offset(w - 55.dp.toPx(), 70.dp.toPx()),
                                    strokeWidth = 3.dp.toPx()
                                )
                                drawLine(
                                    color = Color.White.copy(alpha = 0.2f),
                                    start = Offset(42.dp.toPx(), 85.dp.toPx()),
                                    end = Offset(w - 85.dp.toPx(), 85.dp.toPx()),
                                    strokeWidth = 3.dp.toPx()
                                )
                            }
                            "SELFIE_PROFILE" -> {
                                drawOval(
                                    color = Color.White.copy(alpha = 0.15f),
                                    topLeft = Offset(w/2 - 30.dp.toPx(), h/2 - 45.dp.toPx()),
                                    size = Size(60.dp.toPx(), 80.dp.toPx()),
                                    style = Stroke(2.dp.toPx())
                                )
                                drawPath(Path().apply {
                                    moveTo(w/2 - 45.dp.toPx(), h - 25.dp.toPx())
                                    quadraticBezierTo(w/2, h - 60.dp.toPx(), w/2 + 45.dp.toPx(), h - 25.dp.toPx())
                                }, Color.White.copy(alpha = 0.15f), style = Stroke(2.dp.toPx()))
                            }
                            "SELFIE_WITH_CEDULA", "SELFIE_WITH_LICENCIA" -> {
                                drawOval(
                                    color = Color.White.copy(alpha = 0.15f),
                                    topLeft = Offset(w/2 - 40.dp.toPx(), h/2 - 40.dp.toPx()),
                                    size = Size(50.dp.toPx(), 70.dp.toPx()),
                                    style = Stroke(2.dp.toPx())
                                )
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.25f),
                                    topLeft = Offset(w/2 + 15.dp.toPx(), h/2 - 5.dp.toPx()),
                                    size = Size(35.dp.toPx(), 22.dp.toPx()),
                                    cornerRadius = CornerRadius(4.dp.toPx()),
                                    style = Stroke(1.5f.dp.toPx())
                                )
                            }
                            "VEHICLE_FRONT", "VEHICLE_BACK" -> {
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.15f),
                                    topLeft = Offset(25.dp.toPx(), 60.dp.toPx()),
                                    size = Size(w - 50.dp.toPx(), h - 110.dp.toPx()),
                                    cornerRadius = CornerRadius(10.dp.toPx()),
                                    style = Stroke(2.dp.toPx())
                                )
                                drawCircle(Color.White.copy(alpha = 0.15f), 10.dp.toPx(), Offset(50.dp.toPx(), h - 50.dp.toPx()))
                                drawCircle(Color.White.copy(alpha = 0.15f), 10.dp.toPx(), Offset(w - 50.dp.toPx(), h - 50.dp.toPx()))
                            }
                            "VEHICLE_INT" -> {
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.15f),
                                    radius = 30.dp.toPx(),
                                    center = Offset(w/2, h/2),
                                    style = Stroke(3.dp.toPx())
                                )
                            }
                        }

                        // Scanning green bar
                        val scanY = 16.dp.toPx() + (h - 32.dp.toPx()) * scanLineProgress
                        drawLine(
                            color = MeetColors.neonGreen,
                            start = Offset(16.dp.toPx(), scanY),
                            end = Offset(w - 16.dp.toPx(), scanY),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }

                // Guidelines
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val guidelines = when (documentType) {
                        "CEDULA_FRONT", "CEDULA_BACK", "LICENCIA_FRONT", "LICENCIA_BACK", "MARCHAMO", "DEKRA", "SEGURO", "HOJA" -> listOf(
                            "Coloca el documento sobre una superficie plana y oscura.",
                            "Evita los reflejos directos de luz y destellos de flash.",
                            "Asegúrate de que todo el texto sea legible y no esté borroso."
                        )
                        "SELFIE_PROFILE" -> listOf(
                            "Busca un entorno con buena iluminación frontal.",
                            "Mira fijamente a la cámara con una expresión neutra.",
                            "Quítate gorras, lentes oscuros y mascarillas."
                        )
                        "SELFIE_WITH_CEDULA", "SELFIE_WITH_LICENCIA" -> listOf(
                            "Sostén tu documento al lado de tu cara sin cubrir tu rostro.",
                            "Asegúrate de no tapar tus ojos, boca u orejas con el documento.",
                            "Tanto tu cara como el texto de la identificación deben ser nítidos."
                        )
                        "VEHICLE_FRONT", "VEHICLE_BACK" -> listOf(
                            "Captura el vehículo completo a una distancia adecuada.",
                            "Asegúrate de que las placas sean perfectamente visibles.",
                            "Toma la foto a la luz del día o con buena iluminación."
                        )
                        else -> listOf(
                            "Busca un lugar iluminado de frente.",
                            "Sostén firmemente el celular para evitar fotos borrosas.",
                            "Verifica que el elemento principal esté enfocado."
                        )
                    }

                    guidelines.forEach { tip ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("💡 ", fontSize = 13.sp)
                            Text(
                                text = tip,
                                color = MeetColors.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar", color = MeetColors.textMuted)
                    }
                    Button(
                        onClick = onProceed,
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text("ABRIR CÁMARA 📸", color = MeetColors.backgroundDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
