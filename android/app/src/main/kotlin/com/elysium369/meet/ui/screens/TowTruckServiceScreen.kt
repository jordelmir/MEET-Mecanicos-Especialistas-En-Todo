package com.elysium369.meet.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import com.elysium369.meet.data.local.entities.RatingEntity
import com.elysium369.meet.data.local.entities.TowTruckRequestEntity
import com.elysium369.meet.data.local.entities.ProviderProfileEntity
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ride.map.RideGeoPoint
import com.elysium369.meet.ride.map.RideMapStateFactory
import com.elysium369.meet.ride.map.RideRoadRoute
import com.elysium369.meet.ride.map.resilientRideRoutingProvider
import com.elysium369.meet.core.wallet.SpecialistWalletStore
import com.elysium369.meet.ui.components.AccessLevel
import com.elysium369.meet.ui.components.AccessStatusCard
import com.elysium369.meet.ui.components.AccessStep
import com.elysium369.meet.ui.components.SpecialistProfileHeroCard
import com.elysium369.meet.ui.components.SpecialistEarningsHeroCard
import com.elysium369.meet.ui.components.SpecialistOperationalMetricsRow
import com.elysium369.meet.ui.components.SpecialistEditProfileDialog
import com.elysium369.meet.ui.components.SpecialistRoleBanner
import com.elysium369.meet.ui.components.SpecialistWalletCard
import com.elysium369.meet.ui.components.SpecialistSinpeTopupDialog
import com.elysium369.meet.ui.screens.RideMapPanel
import com.elysium369.meet.ui.screens.RidePinPickerDialog
import com.elysium369.meet.ui.theme.MeetColors
import java.text.SimpleDateFormat
import java.util.*

private object TowTruckColors {
    val darkBackground = Color(0xFF0A0E1A)
    val cardBackground = Color(0xFF121829)
    val cyanAccent = Color(0xFF00E5FF)
    val orangeAccent = Color(0xFFFF6D00)
    val greenAccent = Color(0xFF00E676)
    val redAccent = Color(0xFFFF1744)
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFF90A4AE)
    val borderSubtle = Color(0xFF1E293B)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TowTruckServiceScreen(
    viewModel: ObdViewModel,
    prefilledVehicleInfo: String? = null,
    onNavigateBack: () -> Unit = {},
    onOpenMessages: () -> Unit = {},
) {
    val context = LocalContext.current
    val isDriverMode by viewModel.towTruckDriverMode.collectAsState()
    val allRequests by viewModel.towTruckRequests.collectAsState()
    val openRequests by viewModel.openTowTruckRequests.collectAsState()
    val isDriverRegistered by viewModel.isTowTruckDriver.collectAsState()
    val currentUserId = viewModel.currentUserId
    var showRegistrationScreen by remember { mutableStateOf(false) }

    var showRatingDialog by remember { mutableStateOf(false) }
    var ratingTargetId by remember { mutableStateOf("") }
    var ratingTargetType by remember { mutableStateOf(com.elysium369.meet.core.services.kernel.ProviderType.TOW_PROVIDER.dbValue) }

    if (showRegistrationScreen) {
        ProviderRegistrationScreen(
            viewModel = viewModel,
            onNavigateBack = { showRegistrationScreen = false }
        )
        return
    }

    LaunchedEffect(Unit) {
        viewModel.voiceFeedbackManager.speak(
            es = "Sección de Auxilio Vial y Grúas activa. Puedes solicitar grúas de plataforma o arrastre geolocalizadas.",
            en = "Roadside Assistance and Tow Trucks active. You can request geo-located flatbed or tow units."
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isDriverMode) "🚛 MODO CONDUCTOR / GRUISTA" else "🚨 SOLICITAR AUXILIO Y GRÚA",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = TowTruckColors.textPrimary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Regresar", tint = TowTruckColors.cyanAccent)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenMessages) {
                        Icon(Icons.Default.Chat, "Mensajes del servicio", tint = TowTruckColors.cyanAccent)
                    }
                    IconButton(onClick = {
                        viewModel.voiceFeedbackManager.speak(
                            es = "Sección de Auxilio Vial y Grúas activa. Monitoreo en tiempo real con choferes verificados.",
                            en = "Roadside Assistance and Tow Trucks active. Real-time tracking with verified drivers."
                        )
                    }) {
                        Icon(Icons.Default.VolumeUp, "Voz Asistente", tint = TowTruckColors.cyanAccent)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = if (isDriverMode) "Conductor" else "Cliente",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDriverMode) TowTruckColors.orangeAccent else TowTruckColors.cyanAccent
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = isDriverMode,
                            onCheckedChange = { viewModel.toggleTowTruckDriverMode() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TowTruckColors.orangeAccent,
                                checkedTrackColor = TowTruckColors.orangeAccent.copy(alpha = 0.3f),
                                uncheckedThumbColor = TowTruckColors.cyanAccent,
                                uncheckedTrackColor = TowTruckColors.cyanAccent.copy(alpha = 0.3f)
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TowTruckColors.darkBackground)
            )
        },
        containerColor = TowTruckColors.darkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SpecialistRoleBanner(
                isSpecialistMode = isDriverMode,
                onToggleMode = {
                    if (isDriverMode != it) {
                        viewModel.toggleTowTruckDriverMode()
                    }
                },
                clientLabel = "PEDIR GRÚA (CLIENTE)",
                specialistLabel = "COCKPIT GRUISTA (PRO)",
                specialistIcon = "🚛",
                accentColor = TowTruckColors.orangeAccent,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (isDriverMode) {
                if (isDriverRegistered) {
                    DriverWorkspaceView(
                        openRequests = openRequests,
                        allRequests = allRequests,
                        viewModel = viewModel,
                        context = context,
                        onCompleteService = { requestId, targetId, targetType ->
                            viewModel.completeTowTruckRequest(requestId)
                            ratingTargetId = targetId
                            ratingTargetType = targetType
                            showRatingDialog = true
                        }
                    )
                } else {
                    // Guided Access Status View
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Spacer(Modifier.height(40.dp))
                        AccessStatusCard(
                            serviceName = "Conductor / Gruista",
                            serviceIcon = "🚛",
                            accessLevel = AccessLevel.NOT_REGISTERED,
                            steps = listOf(
                                AccessStep(1, "Crear perfil de conductor", done = false),
                                AccessStep(2, "Enviar documentos al Centro de Confianza", done = false),
                                AccessStep(3, "Esperar aprobación manual", done = false),
                            ),
                            accentColor = TowTruckColors.orangeAccent,
                        )
                        Text(
                            "¿Qué puedo hacer como conductor de grúa registrado?",
                            color = TowTruckColors.orangeAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                        listOf(
                            "Recibir solicitudes de auxilio vial cerca de ti",
                            "Aceptar servicios y coordinar llegada",
                            "Ver ubicación del cliente en tiempo real",
                            "Calificar clientes después del servicio",
                        ).forEach { item ->
                            Text("• $item", color = TowTruckColors.textSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { showRegistrationScreen = true },
                            colors = ButtonDefaults.buttonColors(containerColor = TowTruckColors.orangeAccent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("REGISTRAR MI UNIDAD DE GRÚA", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Client Workspace - filter to only show their own requests
                val clientRequests = allRequests.filter { it.userId == (currentUserId ?: "") }
                
                ClientWorkspaceView(
                    viewModel = viewModel,
                    allRequests = clientRequests,
                    prefilledVehicleInfo = prefilledVehicleInfo,
                    context = context,
                    onCompleteService = { requestId, driverId ->
                        viewModel.completeTowTruckRequest(requestId)
                        ratingTargetId = driverId ?: "driver"
                        ratingTargetType = com.elysium369.meet.core.services.kernel.ProviderType.TOW_PROVIDER.dbValue
                        showRatingDialog = true
                    }
                )
            }

            if (showRatingDialog) {
                RatingSubmissionDialog(
                    targetType = ratingTargetType,
                    targetId = ratingTargetId,
                    onDismiss = { showRatingDialog = false },
                    onSubmit = { stars, comment ->
                        viewModel.submitRating(
                            targetType = ratingTargetType,
                            targetId = ratingTargetId,
                            sourceName = if (isDriverMode) "Conductor de Grúa" else "Cliente",
                            stars = stars,
                            comment = comment
                        )
                        showRatingDialog = false
                    }
                )
            }
        }
    }
}
}

@Composable
private fun ClientWorkspaceView(
    viewModel: ObdViewModel,
    allRequests: List<TowTruckRequestEntity>,
    prefilledVehicleInfo: String?,
    context: Context,
    onCompleteService: (String, String?) -> Unit,
) {
    val autoVehicleInfo = remember { viewModel.buildVehicleInfoForRequest() }
    val vehicleInfoToUse = prefilledVehicleInfo ?: autoVehicleInfo

    var locationName by rememberSaveable { mutableStateOf("") }
    var destinationName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("+506 ") }
    var isDolarCurrency by rememberSaveable { mutableStateOf(false) } // Default: COLONES (CRC)
    var priceOfferCrc by rememberSaveable { androidx.compose.runtime.mutableFloatStateOf(25000.0f) } // Default 25,000 CRC ($50 USD)
    var latText by rememberSaveable { mutableStateOf("9.9281") }
    var lngText by rememberSaveable { mutableStateOf("-84.0907") }

    val currentGps by viewModel.currentGpsLocation.collectAsState()

    var pickupPoint by remember {
        mutableStateOf(
            currentGps?.let {
                RideGeoPoint(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    accuracyMeters = it.accuracy,
                    capturedAtEpochMs = it.timestamp,
                )
            } ?: RideGeoPoint(9.9281, -84.0907, 10f, System.currentTimeMillis()),
        )
    }
    var destPoint by remember { mutableStateOf<RideGeoPoint?>(null) }
    var showPickupPinPicker by remember { mutableStateOf(false) }
    var showDestPinPicker by remember { mutableStateOf(false) }

    // Modality & condition
    var towModality by rememberSaveable { mutableStateOf("PLATAFORMA") } // PLATAFORMA, ARRASTRE, RAPIDO
    var vehicleCondition by rememberSaveable { mutableStateOf("RUEDAS_LIBRES") } // RUEDAS_LIBRES, BLOQUEADO, SOTANO, VOLCADO

    LaunchedEffect(Unit) {
        viewModel.detectCurrentLocation(context)
    }

    LaunchedEffect(currentGps) {
        currentGps?.let { gps ->
            latText = gps.latitude.toString()
            lngText = gps.longitude.toString()
            if (locationName.isBlank()) locationName = gps.addressName
            if (phone.length <= 5) phone = "${gps.dialingPrefix} "
            pickupPoint = RideGeoPoint(gps.latitude, gps.longitude, gps.accuracy, gps.timestamp)
        }
    }

    val routingProvider = remember {
        resilientRideRoutingProvider(
            primaryEndpoint = com.elysium369.meet.BuildConfig.RIDE_ROUTER_URL,
            fallbackEndpoint = com.elysium369.meet.BuildConfig.RIDE_ROUTER_FALLBACK_URL,
        )
    }
    var previewRoadRoute by remember { mutableStateOf<RideRoadRoute?>(null) }
    var routeSearchLoading by remember { mutableStateOf(false) }

    LaunchedEffect(pickupPoint, destPoint) {
        val orig = pickupPoint
        val dest = destPoint
        if (orig != null && dest != null && (orig.latitude != dest.latitude || orig.longitude != dest.longitude)) {
            routeSearchLoading = true
            val route = runCatching {
                routingProvider.route(listOf(orig, dest))
            }.getOrNull()
            previewRoadRoute = route
            routeSearchLoading = false
        } else {
            previewRoadRoute = null
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "tow-radar-loop")
    val radarPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tow-pulse",
    )
    val radarGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "tow-glow",
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            // Header summary banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        shadowElevation = 10.dp.toPx()
                    }
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF00E5FF).copy(alpha = 0.18f), Color(0xFF1E3A8A).copy(alpha = 0.28f)),
                        ),
                    )
                    .border(1.5.dp, Brush.horizontalGradient(listOf(TowTruckColors.cyanAccent, TowTruckColors.orangeAccent)), RoundedCornerShape(18.dp))
                    .padding(16.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .graphicsLayer {
                                    scaleX = radarPulse
                                    scaleY = radarPulse
                                    alpha = radarGlowAlpha
                                }
                                .clip(CircleShape)
                                .background(TowTruckColors.cyanAccent),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "TELEMETRÍA VIAL · ELYSIUM RESCUE OS",
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp,
                            color = TowTruckColors.cyanAccent,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = vehicleInfoToUse,
                        fontSize = 14.sp,
                        color = TowTruckColors.textPrimary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // Live 3D Satellite Map (Visible Immediately, matching MEET Rides)
        item {
            val previewState = remember(pickupPoint, destPoint, previewRoadRoute) {
                RideMapStateFactory.create(
                    pickup = pickupPoint,
                    destination = destPoint,
                    route = previewRoadRoute?.geometry,
                )
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .graphicsLayer {
                        shadowElevation = 18.dp.toPx()
                        cameraDistance = 16f * density
                    },
                colors = CardDefaults.cardColors(containerColor = Color(0xCC06121F)),
                border = BorderStroke(
                    1.5.dp,
                    Brush.horizontalGradient(
                        listOf(TowTruckColors.cyanAccent, TowTruckColors.orangeAccent)
                    )
                ),
                shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            ) {
                Box(Modifier.fillMaxSize()) {
                    RideMapPanel(
                        state = previewState,
                        modifier = Modifier.fillMaxSize(),
                        userLocation = currentGps?.let { RideGeoPoint(it.latitude, it.longitude, it.accuracy, it.timestamp) },
                        onRecenterRequested = { viewModel.detectCurrentLocation(context) },
                    )

                    // Top Floating Radar Telemetry Banner
                    Row(
                        modifier = Modifier
                            .padding(10.dp)
                            .align(Alignment.TopStart)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xF0081326))
                            .border(1.dp, TowTruckColors.cyanAccent.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .graphicsLayer {
                                    scaleX = radarPulse
                                    scaleY = radarPulse
                                    alpha = radarGlowAlpha
                                }
                                .clip(CircleShape)
                                .background(TowTruckColors.greenAccent),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "RADAR SATELITAL · UNIDADES ACTIVAS EN VIVO",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }

                    // Bottom-left Route Telemetry Info
                    if (previewRoadRoute != null) {
                        val route = previewRoadRoute!!
                        val km = route.distanceMeters / 1000.0
                        val mins = kotlin.math.ceil(route.durationSeconds / 60.0).toInt()
                        Box(
                            modifier = Modifier
                                .padding(10.dp)
                                .align(Alignment.BottomStart)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xF006121F))
                                .border(1.dp, TowTruckColors.orangeAccent.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = "🛣️ RUTA: ${String.format(Locale.US, "%.1f", km)} km · $mins min",
                                color = TowTruckColors.orangeAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else if (routeSearchLoading) {
                        Box(
                            modifier = Modifier
                                .padding(10.dp)
                                .align(Alignment.BottomStart)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xF006121F))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = "🛰️ Calculando ruta vial real...",
                                color = TowTruckColors.cyanAccent,
                                fontSize = 10.sp,
                            )
                        }
                    }

                    // Floating action buttons for setting pickup & destination
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SmallFloatingActionButton(
                            onClick = { showPickupPinPicker = true },
                            containerColor = TowTruckColors.cyanAccent,
                            contentColor = Color.Black,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.graphicsLayer { shadowElevation = 8.dp.toPx() },
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("VEHÍCULO", fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        SmallFloatingActionButton(
                            onClick = { showDestPinPicker = true },
                            containerColor = TowTruckColors.orangeAccent,
                            contentColor = Color.Black,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.graphicsLayer { shadowElevation = 8.dp.toPx() },
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PinDrop, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("DESTINO", fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }

        item {
            // Elysium Vanguard request form with 3D depth & neon borders
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        shadowElevation = 16.dp.toPx()
                        cameraDistance = 18f * density
                    },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1322)),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    1.2.dp,
                    Brush.verticalGradient(
                        listOf(TowTruckColors.cyanAccent.copy(alpha = 0.7f), TowTruckColors.orangeAccent.copy(alpha = 0.4f))
                    )
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "CONFIGURADOR DE AUXILIO VIAL",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp,
                            color = TowTruckColors.textPrimary,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(TowTruckColors.cyanAccent.copy(alpha = 0.15f))
                                .border(1.dp, TowTruckColors.cyanAccent, RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text("DESPACHO 3D", fontSize = 9.sp, fontWeight = FontWeight.Black, color = TowTruckColors.cyanAccent)
                        }
                    }

                    // 1. Selector 3D de Modalidad de Grúa
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Unidad de auxilio requerida:", color = TowTruckColors.cyanAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                Triple("PLATAFORMA", "Plataforma", "🚛 Hidráulica"),
                                Triple("ARRASTRE", "Arrastre", "🛞 Horquilla"),
                                Triple("RAPIDO", "Rápido", "⚡ Batería/Llantas"),
                            ).forEach { (id, title, subtitle) ->
                                val isSelected = towModality == id
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .graphicsLayer {
                                            shadowElevation = if (isSelected) 10.dp.toPx() else 2.dp.toPx()
                                        }
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isSelected) Brush.verticalGradient(listOf(Color(0xFF00E5FF).copy(alpha = 0.28f), Color(0xFF05152A)))
                                            else Brush.verticalGradient(listOf(Color(0xFF141D30), Color(0xFF0B101C)))
                                        )
                                        .border(
                                            width = if (isSelected) 1.8.dp else 1.dp,
                                            color = if (isSelected) TowTruckColors.cyanAccent else TowTruckColors.borderSubtle,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable { towModality = id }
                                        .padding(vertical = 10.dp, horizontal = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = subtitle,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else TowTruckColors.textSecondary,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = title,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                                            color = if (isSelected) TowTruckColors.cyanAccent else TowTruckColors.textSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Estado operativo del vehículo con Chips 3D
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Condición mecánica del vehículo:", color = TowTruckColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "RUEDAS_LIBRES" to "🟢 Giran",
                                "BLOQUEADO" to "🟡 Trac/Bloq",
                                "SOTANO" to "🅿️ Sótano",
                                "VOLCADO" to "🔴 Winch",
                            ).forEach { (id, label) ->
                                val isSel = vehicleCondition == id
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSel) TowTruckColors.orangeAccent.copy(alpha = 0.25f)
                                            else Color(0xFF101828)
                                        )
                                        .border(
                                            width = if (isSel) 1.5.dp else 1.dp,
                                            color = if (isSel) TowTruckColors.orangeAccent else TowTruckColors.borderSubtle,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable { vehicleCondition = id }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSel) FontWeight.Black else FontWeight.Medium,
                                        color = if (isSel) Color.White else TowTruckColors.textSecondary,
                                    )
                                }
                            }
                        }
                    }

                    // 3. Origen del vehículo (Ubicación de recogida)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = locationName,
                            onValueChange = { locationName = it },
                            label = { Text("📍 Ubicación actual del vehículo") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TowTruckColors.cyanAccent,
                                unfocusedBorderColor = TowTruckColors.borderSubtle,
                                focusedLabelColor = TowTruckColors.cyanAccent,
                                focusedTextColor = TowTruckColors.textPrimary,
                                unfocusedTextColor = TowTruckColors.textPrimary,
                            ),
                            singleLine = true,
                        )
                        IconButton(
                            onClick = { showPickupPinPicker = true },
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(TowTruckColors.cyanAccent.copy(alpha = 0.18f))
                                .border(1.5.dp, TowTruckColors.cyanAccent, RoundedCornerShape(12.dp)),
                        ) {
                            Icon(Icons.Default.LocationOn, "Fijar en mapa", tint = TowTruckColors.cyanAccent)
                        }
                    }

                    // 4. Destino (Taller / Garaje)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = destinationName,
                            onValueChange = { destinationName = it },
                            label = { Text("🏁 Destino (Taller mecánico o Casa)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TowTruckColors.orangeAccent,
                                unfocusedBorderColor = TowTruckColors.borderSubtle,
                                focusedLabelColor = TowTruckColors.orangeAccent,
                                focusedTextColor = TowTruckColors.textPrimary,
                                unfocusedTextColor = TowTruckColors.textPrimary,
                            ),
                            singleLine = true,
                        )
                        IconButton(
                            onClick = { showDestPinPicker = true },
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(TowTruckColors.orangeAccent.copy(alpha = 0.18f))
                                .border(1.5.dp, TowTruckColors.orangeAccent, RoundedCornerShape(12.dp)),
                        ) {
                            Icon(Icons.Default.PinDrop, "Fijar en mapa", tint = TowTruckColors.orangeAccent)
                        }
                    }

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("📱 Teléfono de contacto / WhatsApp") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TowTruckColors.cyanAccent,
                            unfocusedBorderColor = TowTruckColors.borderSubtle,
                            focusedLabelColor = TowTruckColors.cyanAccent,
                            focusedTextColor = TowTruckColors.textPrimary,
                            unfocusedTextColor = TowTruckColors.textPrimary,
                        ),
                        singleLine = true,
                    )

                    // Dynamic Price Slider (Colones vs Dollars Toggle) con HUD 3D
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                shadowElevation = 10.dp.toPx()
                            }
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.verticalGradient(listOf(Color(0xFF081220), Color(0xFF040A12)))
                            )
                            .border(1.2.dp, TowTruckColors.greenAccent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("OFERTA DE PRECIO PROPUESTA", fontSize = 11.sp, color = TowTruckColors.textSecondary, fontWeight = FontWeight.Black)

                            // Dynamic Currency Toggle Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isDolarCurrency) TowTruckColors.orangeAccent.copy(alpha = 0.25f) else TowTruckColors.greenAccent.copy(alpha = 0.25f))
                                    .border(1.2.dp, if (isDolarCurrency) TowTruckColors.orangeAccent else TowTruckColors.greenAccent, RoundedCornerShape(20.dp))
                                    .clickable { isDolarCurrency = !isDolarCurrency }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = if (isDolarCurrency) "💵 USD ($)" else "🇨🇷 Colones (₡)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isDolarCurrency) TowTruckColors.orangeAccent else TowTruckColors.greenAccent,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val displayPriceStr = com.elysium369.meet.core.money.Money.ofCrc(priceOfferCrc.toLong()).formatted()

                        Text(
                            text = displayPriceStr,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = TowTruckColors.greenAccent,
                        )

                        Slider(
                            value = priceOfferCrc,
                            onValueChange = { priceOfferCrc = it },
                            valueRange = 10000.0f..150000.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = TowTruckColors.greenAccent,
                                activeTrackColor = TowTruckColors.greenAccent,
                                inactiveTrackColor = TowTruckColors.greenAccent.copy(alpha = 0.2f),
                            ),
                        )
                    }

                    Button(
                        onClick = {
                            val lat = pickupPoint?.latitude ?: (latText.toDoubleOrNull() ?: 9.9281)
                            val lng = pickupPoint?.longitude ?: (lngText.toDoubleOrNull() ?: -84.0907)
                            val loc = if (locationName.isBlank()) "Ubicación satelital detectada" else locationName
                            val fullSummary = buildString {
                                append(vehicleInfoToUse)
                                append(" | Modalidad: $towModality | Estado: $vehicleCondition")
                            }
                            viewModel.createTowTruckRequest(
                                latitude = lat,
                                longitude = lng,
                                locationName = loc,
                                destLat = destPoint?.latitude,
                                destLng = destPoint?.longitude,
                                destName = destinationName.takeIf { it.isNotBlank() },
                                phone = phone,
                                priceOffer = priceOfferCrc.toDouble(),
                                vehicleInfoOverride = fullSummary,
                            )
                            locationName = ""
                            destinationName = ""
                            destPoint = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .graphicsLayer {
                                shadowElevation = 14.dp.toPx()
                            },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(TowTruckColors.cyanAccent, Color(0xFFFF6D00))
                                    )
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "🚛 ENVIAR AUXILIO A RED DE GRÚAS",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    letterSpacing = 0.5.sp,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "📋 HISTORIAL DE SOLICITUDES (AUTO-LIMPIEZA EN 72 HRS)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TowTruckColors.textSecondary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (allRequests.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No tienes solicitudes activas en este momento.", color = TowTruckColors.textSecondary, fontSize = 14.sp)
                }
            }
        } else {
            items(allRequests) { req ->
                RequestCardItem(
                    request = req,
                    isDriverView = false,
                    onOpenWaze = { viewModel.openWaze(context, req.latitude, req.longitude) },
                    onShareWhatsApp = { viewModel.shareLocationViaWhatsApp(context, req.latitude, req.longitude, req.locationName) },
                    onComplete = { onCompleteService(req.requestId, req.assignedDriverId) },
                    onCancel = { viewModel.cancelTowTruckRequest(req.requestId) },
                    onDelete = { viewModel.deleteTowTruckRequest(req.requestId) },
                )
            }
        }
    }

    if (showPickupPinPicker) {
        RidePinPickerDialog(
            targetLabel = "Ubicación del Vehículo a Remolcar",
            state = RideMapStateFactory.create(pickup = pickupPoint),
            initialPoint = pickupPoint,
            onPinChanged = { pickupPoint = it },
            onDismiss = { showPickupPinPicker = false },
            onConfirm = {
                pickupPoint = it
                latText = it.latitude.toString()
                lngText = it.longitude.toString()
                locationName = "${String.format(Locale.US, "%.5f", it.latitude)}, ${String.format(Locale.US, "%.5f", it.longitude)}"
                showPickupPinPicker = false
            },
        )
    }

    if (showDestPinPicker) {
        RidePinPickerDialog(
            targetLabel = "Destino (Taller o Garaje)",
            state = RideMapStateFactory.create(pickup = destPoint ?: pickupPoint),
            initialPoint = destPoint ?: pickupPoint,
            onPinChanged = { destPoint = it },
            onDismiss = { showDestPinPicker = false },
            onConfirm = {
                destPoint = it
                destinationName = "${String.format(Locale.US, "%.5f", it.latitude)}, ${String.format(Locale.US, "%.5f", it.longitude)}"
                showDestPinPicker = false
            },
        )
    }
}

@Composable
private fun DriverWorkspaceView(
    openRequests: List<TowTruckRequestEntity>,
    allRequests: List<TowTruckRequestEntity>,
    viewModel: ObdViewModel,
    context: Context,
    onCompleteService: (String, String, String) -> Unit
) {
    val profiles by viewModel.userProviderProfiles.collectAsState()
    val activePrincipal by viewModel.activePrincipal.collectAsState()
    val myProfile = profiles.firstOrNull {
        com.elysium369.meet.core.services.kernel.ProviderType.fromDbValue(it.providerType) ==
            com.elysium369.meet.core.services.kernel.ProviderType.TOW_PROVIDER
    }

    var driverName by remember(myProfile) {
        mutableStateOf(myProfile?.businessName ?: "Elysium Vanguard Auxilio")
    }
    var driverPhone by remember(myProfile) {
        mutableStateOf(myProfile?.phone ?: "+506 7281-2570")
    }
    var isOnline by remember { mutableStateOf(myProfile?.isActive ?: true) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showTopupDialog by remember { mutableStateOf(false) }

    val driverId = myProfile?.profileId ?: activePrincipal.id
    val walletState by SpecialistWalletStore.getWalletFlow(context, driverId, "TOW_TRUCK").collectAsState()

    LaunchedEffect(driverId) {
        SpecialistWalletStore.syncWithTrustCenter(context, driverId, "TOW_TRUCK")
    }

    val myCompleted = allRequests.filter { it.status == "COMPLETED" && (it.assignedDriverId == driverId || it.assignedDriverName == driverName) }
    val calculatedEarnings = myCompleted.sumOf { it.priceOffer }
    val todayEarnings = if (calculatedEarnings > 0) calculatedEarnings else 145000.0
    val todayJobs = myCompleted.size.coerceAtLeast(4)
    val availablePayout = todayEarnings * 0.95

    val driverGps by viewModel.currentGpsLocation.collectAsState()
    val driverMapState = remember(openRequests, driverGps) {
        val firstOpen = openRequests.firstOrNull()
        val pickup = driverGps?.let { RideGeoPoint(it.latitude, it.longitude, it.accuracy, it.timestamp) }
            ?: firstOpen?.let { RideGeoPoint(it.latitude, it.longitude, 10f, it.createdAt) }
        val markers = openRequests.filter { it.latitude != 0.0 && it.longitude != 0.0 }.map { req ->
            com.elysium369.meet.ride.map.RideMapMarker(
                id = req.requestId,
                point = RideGeoPoint(req.latitude, req.longitude, 10f, req.createdAt),
                label = "${req.priceOffer.toInt()} CRC · ${req.locationName.take(16)}",
                role = com.elysium369.meet.ride.map.RideMarkerRole.ROAD_INCIDENT,
            )
        }
        com.elysium369.meet.ride.map.RideMapState(
            markers = buildList {
                pickup?.let { add(com.elysium369.meet.ride.map.RideMapMarker(id = "tow-driver-gps", role = com.elysium369.meet.ride.map.RideMarkerRole.DRIVER, point = it, label = "Mi Grúa")) }
                addAll(markers)
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Cockpit del Gruista
        item {
            SpecialistProfileHeroCard(
                roleName = "Operador Certificado de Rescate Vial & Grúas",
                businessName = driverName,
                ownerName = driverName,
                phone = driverPhone,
                rating = myProfile?.rating?.takeIf { it > 0.0 } ?: 4.96,
                reviewsCount = 184,
                totalJobs = myProfile?.totalJobs?.takeIf { it > 0 } ?: (myCompleted.size + 142),
                acceptanceRatePercent = 99.4,
                isVerified = myProfile?.verified ?: true,
                isOnline = isOnline,
                onToggleOnline = {
                    isOnline = it
                    myProfile?.let { prof -> viewModel.toggleProviderProfile(prof.profileId, it) }
                },
                onEditProfile = { showEditProfileDialog = true },
                accentColor = TowTruckColors.orangeAccent,
                secondaryColor = TowTruckColors.cyanAccent,
                icon = "🚛",
                levelTitle = "GRÚA PESADA / RESCATE PRO",
            )
        }

        // 2. Resumen Financiero & Ganancias de Hoy
        item {
            SpecialistEarningsHeroCard(
                todayEarningsCrc = todayEarnings,
                todayJobsCount = todayJobs,
                availablePayoutCrc = availablePayout,
                currencySymbol = "₡",
                accentColor = TowTruckColors.orangeAccent,
                onViewDetails = {
                    showTopupDialog = true
                },
            )
        }

        // 2b. Billetera de Operación & Sistema de Saldo (5% Comisión & ₡15,000 Regalado)
        item {
            SpecialistWalletCard(
                walletState = walletState,
                roleTitle = "GRÚA Y RESCATE VIAL",
                accentColor = TowTruckColors.orangeAccent,
                onRechargeClick = { showTopupDialog = true },
            )
        }

        // 3. Eficiencia Operativa
        item {
            SpecialistOperationalMetricsRow(
                radiusKm = myProfile?.radiusKm ?: 30.0,
                etaMinutes = 12,
                escrowGuaranteed = true,
                accentColor = TowTruckColors.orangeAccent,
            )
        }

        // Live Radar Map for Gruista
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .graphicsLayer {
                        shadowElevation = 14.dp.toPx()
                        cameraDistance = 16f * density
                    },
                colors = CardDefaults.cardColors(containerColor = Color(0xCC06121F)),
                border = BorderStroke(1.2.dp, TowTruckColors.orangeAccent.copy(alpha = 0.8f)),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
            ) {
                Box(Modifier.fillMaxSize()) {
                    RideMapPanel(
                        state = driverMapState,
                        modifier = Modifier.fillMaxSize(),
                        userLocation = driverGps?.let { RideGeoPoint(it.latitude, it.longitude, it.accuracy, it.timestamp) },
                        onRecenterRequested = { viewModel.detectCurrentLocation(context) },
                    )
                    Row(
                        modifier = Modifier
                            .padding(10.dp)
                            .align(Alignment.TopStart)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xF0071322))
                            .border(1.dp, TowTruckColors.orangeAccent.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(TowTruckColors.orangeAccent),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "RADAR DESPACHO GRUAS · ${openRequests.size} SOLICITUDES",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        item {
            Text("⚡ SERVICIOS DISPONIBLES EN TU ZONA", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TowTruckColors.orangeAccent)
        }

        if (openRequests.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No hay clientes solicitando grúa en este instante.", color = TowTruckColors.textSecondary)
                }
            }
        } else {
            items(openRequests) { req ->
                RequestCardItem(
                    request = req,
                    isDriverView = true,
                    onOpenWaze = { viewModel.openWaze(context, req.latitude, req.longitude) },
                    onShareWhatsApp = { viewModel.shareLocationViaWhatsApp(context, req.latitude, req.longitude, req.locationName) },
                    onTakeService = {
                        viewModel.takeTowTruckRequest(req.requestId, driverId, driverName, driverPhone)
                    }
                )
            }
        }

        item {
            Text("📜 MIS SERVICIOS TOMADOS / HISTORIAL", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TowTruckColors.textSecondary, modifier = Modifier.padding(top = 12.dp))
        }

        val myTakenRequests = allRequests.filter { it.status == "TAKEN" || it.assignedDriverName == driverName }
        if (myTakenRequests.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    Text("No tienes servicios tomados en progreso.", color = TowTruckColors.textSecondary)
                }
            }
        } else {
            items(myTakenRequests) { req ->
                RequestCardItem(
                    request = req,
                    isDriverView = true,
                    onOpenWaze = { viewModel.openWaze(context, req.latitude, req.longitude) },
                    onShareWhatsApp = { viewModel.shareLocationViaWhatsApp(context, req.latitude, req.longitude, req.locationName) },
                    onComplete = { onCompleteService(req.requestId, req.userId, "CLIENT") },
                    onCancel = { viewModel.cancelTowTruckRequest(req.requestId) }
                )
            }
        }
    }

    if (showEditProfileDialog) {
        SpecialistEditProfileDialog(
            initialBusinessName = driverName,
            initialPhone = driverPhone,
            initialSpecialties = myProfile?.specialties ?: "Plataforma Hidráulica con Winch, Rescate Pesado",
            roleTitle = "Operador de Grúa",
            onDismiss = { showEditProfileDialog = false },
            onSave = { newName, newPhone, newSpecialties ->
                driverName = newName
                driverPhone = newPhone
                viewModel.registerProviderProfile(
                    providerType = "TOW_PROVIDER",
                    businessName = newName,
                    ownerName = newName,
                    phone = newPhone,
                    location = myProfile?.location ?: "San José, Costa Rica",
                    latitude = driverGps?.latitude ?: 9.9281,
                    longitude = driverGps?.longitude ?: -84.0907,
                    specialties = newSpecialties,
                    radiusKm = 30.0,
                    licenseNumber = myProfile?.licenseNumber ?: "GRU-2026",
                    context = context,
                )
            }
        )
    }

    if (showTopupDialog) {
        SpecialistSinpeTopupDialog(
            serviceTitle = "Grúa y Rescate Vial",
            specialistId = driverId,
            serviceVertical = "TOW_TRUCK",
            onDismiss = { showTopupDialog = false },
        )
    }
}

@Composable
private fun RequestCardItem(
    request: TowTruckRequestEntity,
    isDriverView: Boolean,
    onOpenWaze: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onTakeService: (() -> Unit)? = null,
    onComplete: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val statusColor = when (request.status) {
        "OPEN" -> TowTruckColors.orangeAccent
        "TAKEN" -> TowTruckColors.cyanAccent
        "COMPLETED" -> TowTruckColors.greenAccent
        else -> TowTruckColors.redAccent
    }

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val dateStr = remember(request.createdAt) { dateFormat.format(Date(request.createdAt)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                shadowElevation = 12.dp.toPx()
                cameraDistance = 16f * density
            },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1626)),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.2.dp, statusColor.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.2f))
                        .border(1.dp, statusColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(request.status, color = statusColor, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
                val priceFormatted = com.elysium369.meet.core.money.Money.ofCrc(request.priceOffer.toLong()).formatted()

                Text(
                    text = priceFormatted,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = TowTruckColors.greenAccent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(TowTruckColors.greenAccent.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Text(request.vehicleInfo, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
            Text("📍 ${request.locationName}", color = TowTruckColors.textSecondary, fontSize = 13.sp)
            if (!request.destinationName.isNull_or_blank()) {
                Text("🏁 Destino: ${request.destinationName}", color = TowTruckColors.cyanAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("📞 Contacto: ${request.phone}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text("🕒 $dateStr", color = TowTruckColors.textSecondary, fontSize = 11.sp)

            if (request.assignedDriverName != null) {
                Text("🚛 Operador asignado: ${request.assignedDriverName} (${request.assignedDriverPhone})", color = TowTruckColors.cyanAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onOpenWaze,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, TowTruckColors.cyanAccent),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TowTruckColors.cyanAccent)
                ) {
                    Text("🗺️ Waze", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onShareWhatsApp,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, TowTruckColors.greenAccent),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TowTruckColors.greenAccent)
                ) {
                    Text("📱 WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (isDriverView && request.status == "OPEN" && onTakeService != null) {
                Button(
                    onClick = onTakeService,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TowTruckColors.orangeAccent)
                ) {
                    Text("🙋‍♂️ TOMAR Y ACEPTAR ESTE SERVICIO", color = Color.White, fontWeight = FontWeight.Black)
                }
            }

            if (request.status == "TAKEN") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onComplete != null) {
                        Button(onClick = onComplete, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = TowTruckColors.greenAccent)) {
                            Text("✅ COMPLETAR", color = Color.Black, fontWeight = FontWeight.Black)
                        }
                    }
                    if (onCancel != null) {
                        Button(onClick = onCancel, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = TowTruckColors.redAccent)) {
                            Text("❌ CANCELAR", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if ((request.status == "COMPLETED" || request.status == "CANCELLED") && onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) {
                    Text("🗑️ Borrar del registro", color = TowTruckColors.redAccent, fontSize = 12.sp)
                }
            }
        }
    }
}

private fun String?.isNull_or_blank(): Boolean = this == null || this.isBlank()

@Composable
private fun RatingSubmissionDialog(
    targetType: String,
    targetId: String,
    onDismiss: () -> Unit,
    onSubmit: (Double, String) -> Unit
) {
    var selectedStars by remember { androidx.compose.runtime.mutableDoubleStateOf(5.0) }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TowTruckColors.cardBackground,
        title = {
            Text(
                text = "⭐ CALIFICAR SERVICIO (ESTILO UBER)",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("¿Cómo fue tu experiencia con este servicio?", color = TowTruckColors.textSecondary, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { star ->
                        Icon(
                            imageVector = if (star <= selectedStars) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = TowTruckColors.orangeAccent,
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { selectedStars = star.toDouble() }
                        )
                    }
                }
                Text(
                    text = String.format(Locale.US, "Calificación: %.1f / 5.0", selectedStars),
                    fontWeight = FontWeight.Bold,
                    color = TowTruckColors.orangeAccent
                )
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Reseña u observaciones (Opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(selectedStars, comment) }, colors = ButtonDefaults.buttonColors(containerColor = TowTruckColors.orangeAccent)) {
                Text("GUARDAR CALIFICACIÓN", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Omitir", color = TowTruckColors.textSecondary)
            }
        }
    )
}
