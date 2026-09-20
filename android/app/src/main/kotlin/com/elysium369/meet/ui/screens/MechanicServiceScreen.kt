package com.elysium369.meet.ui.screens

import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import com.elysium369.meet.ride.map.RideGeoPoint
import com.elysium369.meet.ride.map.RideMapMarker
import com.elysium369.meet.ride.map.RideMarkerRole
import com.elysium369.meet.ride.map.RideMapStateFactory
import com.elysium369.meet.ui.screens.RideMapPanel
import com.elysium369.meet.core.wallet.SpecialistWalletStore
import com.elysium369.meet.ui.components.SpecialistProfileHeroCard
import com.elysium369.meet.ui.components.SpecialistEarningsHeroCard
import com.elysium369.meet.ui.components.SpecialistOperationalMetricsRow
import com.elysium369.meet.ui.components.SpecialistEditProfileDialog
import com.elysium369.meet.ui.components.SpecialistRoleBanner
import com.elysium369.meet.ui.components.SpecialistWalletCard
import com.elysium369.meet.ui.components.SpecialistSinpeTopupDialog
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.elysium369.meet.core.services.RiskLevel
import com.elysium369.meet.core.services.ServiceCategory
import com.elysium369.meet.core.services.ServiceDefinition
import com.elysium369.meet.core.services.WorkshopServiceCatalog
import com.elysium369.meet.data.local.entities.ServiceRequestEntity
import com.elysium369.meet.data.local.entities.RatingEntity
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.AccessLevel
import com.elysium369.meet.ui.components.AccessStatusCard
import com.elysium369.meet.ui.components.AccessStep
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private object MechanicColors {
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
fun MechanicServiceScreen(
    viewModel: ObdViewModel,
    prefilledVehicleInfo: String? = null,
    onNavigateBack: () -> Unit = {},
    onPostScanRequested: (vehicleId: String) -> Unit = {},
    onOpenMessages: () -> Unit = {},
) {
    val context = LocalContext.current
    var isMechanicMode by remember { mutableStateOf(false) }
    val allRequests by viewModel.serviceRequests.collectAsState()
    val openRequests by viewModel.openServiceRequests.collectAsState()
    val vehicles by viewModel.vehicles.collectAsState()
    val isMechanicRegistered by viewModel.isMechanic.collectAsState()
    var showRegistrationScreen by remember { mutableStateOf(false) }

    LaunchedEffect(allRequests) {
        for (req in allRequests) {
            val consumed = com.elysium369.meet.core.reports.PostScanPrompt.consume(req.requestId)
            if (consumed != null) {
                onPostScanRequested(req.vehicleId)
                break
            }
        }
    }

    var showRatingDialog by remember { mutableStateOf(false) }
    var ratingTargetId by remember { mutableStateOf("") }
    var ratingTargetType by remember { mutableStateOf("MECHANIC") }

    if (showRegistrationScreen) {
        ProviderRegistrationScreen(
            viewModel = viewModel,
            onNavigateBack = { showRegistrationScreen = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isMechanicMode) "🛠️ MODO MECÁNICO" else "👨‍🔧 PEDIR AYUDA A MECÁNICO",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = MechanicColors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Regresar", tint = MechanicColors.cyanAccent)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenMessages) {
                        Icon(Icons.Default.Chat, "Mensajes del servicio", tint = MechanicColors.cyanAccent)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = if (isMechanicMode) "Mecánico" else "Cliente",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMechanicMode) MechanicColors.orangeAccent else MechanicColors.cyanAccent
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = isMechanicMode,
                            onCheckedChange = { isMechanicMode = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MechanicColors.orangeAccent,
                                checkedTrackColor = MechanicColors.orangeAccent.copy(alpha = 0.3f),
                                uncheckedThumbColor = MechanicColors.cyanAccent,
                                uncheckedTrackColor = MechanicColors.cyanAccent.copy(alpha = 0.3f)
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MechanicColors.darkBackground)
            )
        },
        containerColor = MechanicColors.darkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SpecialistRoleBanner(
                isSpecialistMode = isMechanicMode,
                onToggleMode = { isMechanicMode = it },
                clientLabel = "PEDIR MECÁNICO (CLIENTE)",
                specialistLabel = "COCKPIT TALLER (PRO)",
                specialistIcon = "🛠️",
                accentColor = MechanicColors.orangeAccent,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (isMechanicMode) {
                    if (isMechanicRegistered) {
                        MechanicWorkspaceView(
                            allRequests = allRequests,
                            viewModel = viewModel,
                            context = context,
                            onCompleteService = { requestId, targetId ->
                                viewModel.completeMechanicRequest(requestId)
                                ratingTargetId = targetId
                                ratingTargetType = "CLIENT"
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
                                serviceName = "Mecánico / Taller",
                                serviceIcon = "🛠️",
                                accessLevel = AccessLevel.NOT_REGISTERED,
                                steps = listOf(
                                    AccessStep(1, "Crear perfil de proveedor", done = false),
                                    AccessStep(2, "Enviar documents al Centro de Confianza", done = false),
                                    AccessStep(3, "Esperar aprobación manual", done = false),
                                ),
                                accentColor = MechanicColors.cyanAccent,
                            )
                            Text(
                                "¿Qué puedo hacer como mecánico registrado?",
                                color = MechanicColors.cyanAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                            listOf(
                                "Recibir solicitudes de clientes cerca de ti",
                                "Enviar cotizaciones con precios reales",
                                "Aceptar trabajos y coordinar citas",
                                "Calificar clientes después del servicio",
                            ).forEach { item ->
                                Text("• $item", color = MechanicColors.textSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { showRegistrationScreen = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MechanicColors.cyanAccent),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("REGISTRAR MI TALLER / MECÁNICOS", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Client Workspace - filter to only show their own vehicles' requests
                    val userVehicleIds = vehicles.map { it.id }
                    val clientRequests = allRequests.filter { it.vehicleId in userVehicleIds }
                    
                    ClientWorkspaceView(
                        viewModel = viewModel,
                        allRequests = clientRequests,
                        prefilledVehicleInfo = prefilledVehicleInfo,
                        context = context,
                        onCompleteService = { requestId, mechanicId ->
                            viewModel.completeMechanicRequest(requestId)
                            ratingTargetId = mechanicId ?: "mechanic"
                            ratingTargetType = "MECHANIC"
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
                                sourceName = if (isMechanicMode) "Mecánico" else "Cliente",
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
    allRequests: List<ServiceRequestEntity>,
    prefilledVehicleInfo: String?,
    context: Context,
    onCompleteService: (String, String?) -> Unit
) {
    val autoVehicleInfo = remember { viewModel.buildVehicleInfoForRequest() }
    val vehicleInfoToUse = prefilledVehicleInfo ?: autoVehicleInfo

    var problemText by rememberSaveable { mutableStateOf("") }
    var descriptionText by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf("MEDIUM") }
    var locationName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("+506 ") }
    var isDolarCurrency by rememberSaveable { mutableStateOf(false) }
    var priceOfferCrc by rememberSaveable { androidx.compose.runtime.mutableFloatStateOf(25000.0f) }
    var latText by rememberSaveable { mutableStateOf("9.9281") }
    var lngText by rememberSaveable { mutableStateOf("-84.0907") }

    val currentGps by viewModel.currentGpsLocation.collectAsState()
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    val activeDtcs by viewModel.activeDtcs.collectAsState()

    var mechanicPoint by remember {
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
    var showMechanicPinPicker by remember { mutableStateOf(false) }
    var serviceModality by rememberSaveable { mutableStateOf("DOMICILIO") } // DOMICILIO, TALLER_FISICO
    var linkActiveDtcs by rememberSaveable { mutableStateOf(true) }

    var selectedServiceId by rememberSaveable {
        mutableStateOf(
            WorkshopServiceCatalog.bestServicesForDtcs(activeDtcs).firstOrNull()?.id
                ?: WorkshopServiceCatalog.enabledServicesForCategory(ServiceCategory.DIAGNOSTIC).first().id
        )
    }
    val selectedService = WorkshopServiceCatalog.serviceById(selectedServiceId)
        ?: WorkshopServiceCatalog.enabledServicesForCategory(ServiceCategory.DIAGNOSTIC).first()

    LaunchedEffect(activeDtcs.joinToString()) {
        val suggested = WorkshopServiceCatalog.bestServicesForDtcs(activeDtcs).firstOrNull()
        if (suggested != null && problemText.isBlank()) {
            selectedServiceId = suggested.id
        }
    }

    LaunchedEffect(Unit) {
        viewModel.detectCurrentLocation(context)
    }

    LaunchedEffect(currentGps) {
        currentGps?.let { gps ->
            latText = gps.latitude.toString()
            lngText = gps.longitude.toString()
            if (locationName.isBlank()) locationName = gps.addressName
            if (phone.length <= 5) phone = "${gps.dialingPrefix} "
            mechanicPoint = RideGeoPoint(gps.latitude, gps.longitude, gps.accuracy, gps.timestamp)
        }
    }

    val myRequests = remember(allRequests, selectedVehicle) {
        val vehicleId = selectedVehicle?.id ?: ""
        allRequests.filter { it.vehicleId == vehicleId }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "mechanic-radar-loop")
    val mechanicRadarPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "mechanic-pulse",
    )
    val mechanicRadarGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "mechanic-glow",
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1728)),
                border = BorderStroke(
                    1.2.dp,
                    Brush.horizontalGradient(listOf(MechanicColors.cyanAccent.copy(alpha = 0.6f), Color(0xFF3D5AFE).copy(alpha = 0.6f)))
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        shadowElevation = 10.dp.toPx()
                    }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .graphicsLayer {
                                    scaleX = mechanicRadarPulse
                                    scaleY = mechanicRadarPulse
                                    alpha = mechanicRadarGlowAlpha
                                }
                                .clip(CircleShape)
                                .background(MechanicColors.cyanAccent),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DIAGNÓSTICO & SERVICIO TÉCNICO · ELYSIUM",
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = MechanicColors.cyanAccent,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = vehicleInfoToUse,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Live 3D Satellite Map (Visible immediately, matching Elysium Rides standard)
        item {
            val previewState = remember(mechanicPoint) {
                RideMapStateFactory.create(
                    pickup = mechanicPoint,
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
                        listOf(MechanicColors.cyanAccent, MechanicColors.greenAccent)
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

                    // Top Floating Status Banner with Animated Radar Pulse
                    Row(
                        modifier = Modifier
                            .padding(10.dp)
                            .align(Alignment.TopStart)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xF0081326))
                            .border(1.dp, MechanicColors.cyanAccent.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .graphicsLayer {
                                    scaleX = mechanicRadarPulse
                                    scaleY = mechanicRadarPulse
                                    alpha = mechanicRadarGlowAlpha
                                }
                                .clip(CircleShape)
                                .background(MechanicColors.greenAccent),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "RADAR TALLERES & MECÁNICOS · EN VIVO",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }

                    // Floating bottom telemetry info
                    Box(
                        modifier = Modifier
                            .padding(10.dp)
                            .align(Alignment.BottomStart)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xF006121F))
                            .border(1.dp, MechanicColors.cyanAccent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = "📍 INSPECCIÓN: ${locationName.ifBlank { "GPS Detectado" }.take(22)}",
                            color = MechanicColors.cyanAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    // Floating button to adjust pin
                    SmallFloatingActionButton(
                        onClick = { showMechanicPinPicker = true },
                        containerColor = MechanicColors.cyanAccent,
                        contentColor = Color.Black,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .graphicsLayer { shadowElevation = 8.dp.toPx() },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("AJUSTAR PIN", fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        // Live OBD DTC Diagnostic card (si hay fallas activas)
        if (activeDtcs.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
                    border = BorderStroke(1.dp, Color(0xFFFF007F).copy(alpha = 0.75f)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⚡", fontSize = 18.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "FALLAS OBD DETECTADAS EN VIVO",
                                    color = Color(0xFFFF007F),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                            Switch(
                                checked = linkActiveDtcs,
                                onCheckedChange = { linkActiveDtcs = it },
                                modifier = Modifier.height(26.dp),
                            )
                        }
                        Text(
                            "Códigos activos: ${activeDtcs.joinToString(", ")}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Se vincularán automáticamente a la orden para que los talleres coticen con diagnóstico certero.",
                            color = MechanicColors.textSecondary,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }

        item {
            WorkshopServiceCatalogPanel(
                selectedService = selectedService,
                activeDtcs = activeDtcs,
                onServiceSelected = { service ->
                    selectedServiceId = service.id
                },
                onUseService = { service ->
                    selectedServiceId = service.id
                    problemText = service.name
                    if (descriptionText.isBlank()) {
                        descriptionText = buildServiceDescription(service, activeDtcs)
                    }
                    priority = when (service.riskLevel) {
                        RiskLevel.LOW -> "LOW"
                        RiskLevel.MEDIUM -> "MEDIUM"
                        RiskLevel.HIGH,
                        RiskLevel.CRITICAL -> "HIGH"
                    }
                    val suggestedPrice = ((service.basePriceMinCrc + service.basePriceMaxCrc) / 2)
                        .coerceIn(10000, 150000)
                    priceOfferCrc = suggestedPrice.toFloat()
                }
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MechanicColors.cardBackground),
                border = BorderStroke(1.dp, MechanicColors.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "PEDIR AYUDA A MECÁNICO · ELYSIUM",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = problemText,
                        onValueChange = { problemText = it },
                        label = { Text("¿Cuál es el problema/falla?") },
                        placeholder = { Text("Ej. El motor calienta / frenos gastados") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MechanicColors.cyanAccent,
                            unfocusedBorderColor = MechanicColors.borderSubtle,
                            focusedLabelColor = MechanicColors.cyanAccent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = descriptionText,
                        onValueChange = { descriptionText = it },
                        label = { Text("Descripción o síntomas adicionales") },
                        placeholder = { Text("Ej. Humo blanco al encender / ruido metálico") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MechanicColors.cyanAccent,
                            unfocusedBorderColor = MechanicColors.borderSubtle,
                            focusedLabelColor = MechanicColors.cyanAccent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Prioridad de la reparación:",
                        color = MechanicColors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        listOf("LOW", "MEDIUM", "HIGH").forEach { p ->
                            FilterChip(
                                selected = priority == p,
                                onClick = { priority = p },
                                label = { Text(p) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (p == "HIGH") MechanicColors.redAccent else MechanicColors.cyanAccent,
                                    selectedLabelColor = Color.Black,
                                    labelColor = Color.White
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Modalidad de Servicio 3D
                    Text(
                        text = "MODALIDAD DE ATENCIÓN TÉCNICA:",
                        color = MechanicColors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            Triple("DOMICILIO", "A Domicilio", "🏠 Rescate Móvil"),
                            Triple("TALLER_FISICO", "Taller Físico", "🏢 En Instalaciones")
                        ).forEach { (modKey, title, subtitle) ->
                            val isSel = serviceModality == modKey
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .graphicsLayer {
                                        shadowElevation = if (isSel) 8.dp.toPx() else 1.dp.toPx()
                                    }
                                    .clip(RoundedCornerShape(12.dp))
                                    .then(
                                        if (isSel) Modifier.background(Brush.verticalGradient(listOf(MechanicColors.cyanAccent.copy(alpha = 0.25f), Color(0xFF071A2A))))
                                        else Modifier.background(Color(0xFF0F1826))
                                    )
                                    .border(
                                        width = if (isSel) 1.5.dp else 1.dp,
                                        color = if (isSel) MechanicColors.cyanAccent else MechanicColors.borderSubtle,
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    .clickable { serviceModality = modKey }
                                    .padding(vertical = 10.dp, horizontal = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = subtitle,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) Color.White else MechanicColors.textSecondary,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = title,
                                        fontSize = 9.sp,
                                        fontWeight = if (isSel) FontWeight.Black else FontWeight.Medium,
                                        color = if (isSel) MechanicColors.cyanAccent else MechanicColors.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = locationName,
                        onValueChange = { locationName = it },
                        label = { Text("📍 Ubicación del vehículo / taller") },
                        placeholder = { Text("Ej. San José, Sabana Norte o GPS") },
                        trailingIcon = {
                            IconButton(onClick = { showMechanicPinPicker = true }) {
                                Icon(Icons.Default.LocationOn, contentDescription = "Fijar pin en mapa", tint = MechanicColors.cyanAccent)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MechanicColors.cyanAccent,
                            unfocusedBorderColor = MechanicColors.borderSubtle,
                            focusedLabelColor = MechanicColors.cyanAccent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GPS: %.4f, %.4f".format(mechanicPoint.latitude, mechanicPoint.longitude),
                            color = MechanicColors.textSecondary,
                            fontSize = 11.sp
                        )
                        TextButton(
                            onClick = { showMechanicPinPicker = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("📍 CAMBIAR PIN EN MAPA", color = MechanicColors.cyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("📱 Teléfono de contacto / WhatsApp") },
                        placeholder = { Text("Ej. 8888 8888") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MechanicColors.cyanAccent,
                            unfocusedBorderColor = MechanicColors.borderSubtle,
                            focusedLabelColor = MechanicColors.cyanAccent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Escrow Antifraud Protection Banner
                    Surface(
                        color = Color(0x2200E676),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MechanicColors.greenAccent.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🛡️", fontSize = 20.sp)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "GARANTÍA ANTIFRAUDE ESCROW ELYSIUM",
                                    color = MechanicColors.greenAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Los fondos se retienen de forma segura y solo se transfieren al mecánico cuando apruebes la reparación.",
                                    color = Color(0xFFE0E0E0),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Price Slider section
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Oferta de tarifa propuesta:",
                            fontWeight = FontWeight.Bold,
                            color = MechanicColors.textSecondary,
                            fontSize = 14.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("USD ($)", fontSize = 11.sp, color = if (isDolarCurrency) MechanicColors.cyanAccent else Color.Gray)
                            Spacer(modifier = Modifier.width(4.dp))
                            Switch(
                                checked = isDolarCurrency,
                                onCheckedChange = { isDolarCurrency = it },
                                modifier = Modifier.scale(0.7f)
                            )
                        }
                    }

                    val displayPrice = com.elysium369.meet.core.money.Money.ofCrc(priceOfferCrc.toLong()).formatted()

                    Text(
                        text = displayPrice,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = MechanicColors.greenAccent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        textAlign = TextAlign.Center
                    )

                    Slider(
                        value = priceOfferCrc,
                        onValueChange = { priceOfferCrc = it },
                        valueRange = 10000f..150000f, // 10k to 150k colones
                        colors = SliderDefaults.colors(
                            thumbColor = MechanicColors.cyanAccent,
                            activeTrackColor = MechanicColors.cyanAccent
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val isEnabled = selectedVehicle != null && (problemText.isNotBlank() || selectedService.name.isNotBlank()) && phone.isNotBlank()
                    Button(
                        onClick = {
                            val vehicle = selectedVehicle
                            if (vehicle == null) {
                                Toast.makeText(context, "⚠️ Debes seleccionar un vehículo registrado para enviar la solicitud", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            val effectiveProblem = problemText.ifBlank { selectedService.name }
                            val effectiveDescription = "[$serviceModality] ${descriptionText.trim()}".trim()
                            viewModel.createServiceRequest(
                                vehicleId = vehicle.id,
                                problem = effectiveProblem,
                                description = effectiveDescription,
                                location = locationName.ifBlank { "Ubicación GPS" },
                                priority = priority,
                                latitude = mechanicPoint.latitude,
                                longitude = mechanicPoint.longitude,
                                phone = phone,
                                priceOffer = priceOfferCrc.toDouble(),
                                serviceId = selectedService.id,
                                serviceCategory = selectedService.category.name,
                                dtcCodes = if (linkActiveDtcs) activeDtcs else emptyList()
                            )
                            Toast.makeText(context, "✅ Solicitud enviada a la red de talleres certificados", Toast.LENGTH_SHORT).show()
                            problemText = ""
                            descriptionText = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .graphicsLayer { shadowElevation = if (isEnabled) 12.dp.toPx() else 0f },
                        enabled = isEnabled
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (isEnabled) Brush.horizontalGradient(listOf(MechanicColors.cyanAccent, MechanicColors.greenAccent))
                                    else Brush.horizontalGradient(listOf(Color(0xFF2A3B4D), Color(0xFF1E2836)))
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "🛠️ ENVIAR SOLICITUD A RED DE MECÁNICOS",
                                color = if (isEnabled) Color.Black else Color(0xFF8899A6),
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        if (myRequests.isNotEmpty()) {
            item {
                Text(
                    text = "HISTORIAL DE SOLICITUDES DE MECÁNICO",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(myRequests) { req ->
                RequestCardItem(
                    request = req,
                    isDriverView = false,
                    onOpenWaze = { lat, lng -> viewModel.openWaze(context, lat, lng) },
                    onShareWhatsApp = { lat, lng -> viewModel.shareLocationViaWhatsApp(context, lat, lng, req.location) },
                    onComplete = { onCompleteService(req.requestId, req.assignedMechanicId) }
                )
            }
        }
    }

    if (showMechanicPinPicker) {
        RidePinPickerDialog(
            targetLabel = "Ubicación del Vehículo / Atención",
            state = RideMapStateFactory.create(pickup = mechanicPoint),
            initialPoint = mechanicPoint,
            onPinChanged = { mechanicPoint = it },
            onDismiss = { showMechanicPinPicker = false },
            onConfirm = { picked ->
                mechanicPoint = picked
                latText = picked.latitude.toString()
                lngText = picked.longitude.toString()
                locationName = "${String.format(Locale.US, "%.5f", picked.latitude)}, ${String.format(Locale.US, "%.5f", picked.longitude)}"
                showMechanicPinPicker = false
            },
        )
    }
}

@Composable
private fun WorkshopServiceCatalogPanel(
    selectedService: ServiceDefinition,
    activeDtcs: List<String>,
    onServiceSelected: (ServiceDefinition) -> Unit,
    onUseService: (ServiceDefinition) -> Unit,
    actionLabel: String = "USAR ESTE SERVICIO EN LA SOLICITUD"
) {
    val categories = remember { WorkshopServiceCatalog.categories() }
    val servicesInCategory = remember(selectedService.category) {
        WorkshopServiceCatalog.enabledServicesForCategory(selectedService.category)
    }
    val dtcSuggestions = remember(activeDtcs) { WorkshopServiceCatalog.bestServicesForDtcs(activeDtcs) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MechanicColors.cardBackground),
        border = BorderStroke(1.dp, MechanicColors.cyanAccent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CATÁLOGO TÉCNICO DE SERVICIOS",
                        color = MechanicColors.cyanAccent,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "${WorkshopServiceCatalog.services.size} servicios · ${WorkshopServiceCatalog.providerRoles.size} roles · ${WorkshopServiceCatalog.servicePackages.size} paquetes",
                        color = MechanicColors.textSecondary,
                        fontSize = 11.sp
                    )
                }
                Surface(
                    color = riskColor(selectedService.riskLevel).copy(alpha = 0.16f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, riskColor(selectedService.riskLevel).copy(alpha = 0.35f))
                ) {
                    Text(
                        text = selectedService.riskLevel.name,
                        color = riskColor(selectedService.riskLevel),
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (activeDtcs.isNotEmpty() && dtcSuggestions.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MechanicColors.cyanAccent.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, MechanicColors.cyanAccent.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "DTC activo: ${activeDtcs.joinToString()}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = dtcSuggestions.take(3).joinToString(" · ") { it.name },
                            color = MechanicColors.textSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedService.category == category,
                        onClick = {
                            WorkshopServiceCatalog.enabledServicesForCategory(category).firstOrNull()?.let(onServiceSelected)
                        },
                        label = { Text(category.displayName, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MechanicColors.cyanAccent,
                            selectedLabelColor = Color.Black,
                            labelColor = Color.White,
                            containerColor = MechanicColors.darkBackground
                        )
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(servicesInCategory) { service ->
                    WorkshopServiceMiniCard(
                        service = service,
                        selected = selectedService.id == service.id,
                        onClick = { onServiceSelected(service) }
                    )
                }
            }

            WorkshopServiceDetailCard(
                service = selectedService,
                activeDtcs = activeDtcs,
                onUseService = { onUseService(selectedService) },
                actionLabel = actionLabel
            )
        }
    }
}

@Composable
private fun WorkshopServiceMiniCard(
    service: ServiceDefinition,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) MechanicColors.cyanAccent else MechanicColors.borderSubtle
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MechanicColors.cyanAccent.copy(alpha = 0.10f) else MechanicColors.darkBackground
        ),
        border = BorderStroke(1.dp, color.copy(alpha = if (selected) 0.75f else 1f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .width(220.dp)
            .heightIn(min = 116.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = service.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                lineHeight = 15.sp
            )
            Text(
                text = "${service.estimatedDurationMin} min · ${formatCrcRange(service)}",
                color = MechanicColors.greenAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
            Text(
                text = serviceModeText(service),
                color = MechanicColors.textSecondary,
                fontSize = 10.sp,
                lineHeight = 13.sp
            )
        }
    }
}

@Composable
private fun WorkshopServiceDetailCard(
    service: ServiceDefinition,
    activeDtcs: List<String>,
    onUseService: () -> Unit,
    actionLabel: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MechanicColors.darkBackground),
        border = BorderStroke(1.dp, riskColor(service.riskLevel).copy(alpha = 0.30f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = service.name,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                lineHeight = 18.sp
            )
            Text(
                text = service.description,
                color = MechanicColors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ServiceMetric("Duración", "${service.estimatedDurationMin} min", Modifier.weight(1f))
                ServiceMetric("Base ref.", formatCrcRange(service), Modifier.weight(1f))
                ServiceMetric("Riesgo", service.riskLevel.name, Modifier.weight(1f))
            }

            ServiceFactLine("Herramientas", service.requiredTools.joinToString())
            ServiceFactLine("Evidencia", service.requiredEvidence.joinToString { evidenceLabel(it.name) })
            ServiceFactLine("Modalidad", serviceModeText(service))

            if (service.relatedDtcs.isNotEmpty()) {
                ServiceFactLine("DTCs", service.relatedDtcs.joinToString())
            } else if (activeDtcs.isNotEmpty()) {
                ServiceFactLine("DTC activo", activeDtcs.joinToString())
            }

            if (service.relatedDtcs.contains("P0230")) {
                Text(
                    text = WorkshopServiceCatalog.p0230SafetyNote(),
                    color = MechanicColors.orangeAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }

            Button(
                onClick = onUseService,
                colors = ButtonDefaults.buttonColors(containerColor = MechanicColors.cyanAccent),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = actionLabel,
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun ServiceMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MechanicColors.cardBackground, RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        Text(label, color = MechanicColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, lineHeight = 13.sp)
    }
}

@Composable
private fun ServiceFactLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label.uppercase(Locale.getDefault()), color = MechanicColors.cyanAccent, fontSize = 9.sp, fontWeight = FontWeight.Black)
        Text(value, color = MechanicColors.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

private fun buildServiceDescription(service: ServiceDefinition, activeDtcs: List<String>): String = buildString {
    appendLine(service.description)
    if (activeDtcs.isNotEmpty()) appendLine("DTCs activos: ${activeDtcs.joinToString()}")
    appendLine("Herramientas requeridas: ${service.requiredTools.joinToString()}")
    appendLine("Evidencia requerida: ${service.requiredEvidence.joinToString { evidenceLabel(it.name) }}")
    appendLine("Modalidad: ${serviceModeText(service)}")
    if (service.relatedDtcs.contains("P0230")) appendLine(WorkshopServiceCatalog.p0230SafetyNote())
}.trim()

private fun serviceModeText(service: ServiceDefinition): String {
    val modes = buildList {
        if (service.requiresObd) add("OBD")
        if (service.supportsRemote) add("remoto")
        if (service.supportsMobileService) add("domicilio")
        if (service.requiresPhysicalPresence) add("taller/presencial")
        if (service.requiresVehicleOn) add("vehiculo encendido")
    }
    return modes.ifEmpty { listOf("segun proveedor") }.joinToString(" · ")
}

private fun formatCrcRange(service: ServiceDefinition): String {
    if (service.basePriceMinCrc == 0 && service.basePriceMaxCrc == 0) return "segun pieza"
    return "₡%,d-₡%,d".format(Locale.getDefault(), service.basePriceMinCrc, service.basePriceMaxCrc)
}

private fun evidenceLabel(value: String): String =
    value.lowercase(Locale.getDefault()).replace('_', ' ')

private fun riskColor(riskLevel: RiskLevel): Color = when (riskLevel) {
    RiskLevel.LOW -> MechanicColors.greenAccent
    RiskLevel.MEDIUM -> MechanicColors.cyanAccent
    RiskLevel.HIGH -> MechanicColors.orangeAccent
    RiskLevel.CRITICAL -> MechanicColors.redAccent
}

@Composable
private fun MechanicWorkspaceView(
    allRequests: List<ServiceRequestEntity>,
    viewModel: ObdViewModel,
    context: Context,
    onCompleteService: (String, String) -> Unit
) {
    val profiles by viewModel.userProviderProfiles.collectAsState()
    val activePrincipal by viewModel.activePrincipal.collectAsState()
    val myProfile = profiles.firstOrNull {
        com.elysium369.meet.core.services.kernel.ProviderType.fromDbValue(it.providerType) in
            setOf(
                com.elysium369.meet.core.services.kernel.ProviderType.MECHANIC,
                com.elysium369.meet.core.services.kernel.ProviderType.WORKSHOP,
            )
    }

    var mechanicName by remember(myProfile) { mutableStateOf(myProfile?.businessName ?: "Mecánica Pro") }
    var mechanicPhone by remember(myProfile) { mutableStateOf(myProfile?.phone ?: "") }
    var providerServiceId by remember {
        mutableStateOf(WorkshopServiceCatalog.enabledServicesForCategory(ServiceCategory.DIAGNOSTIC).first().id)
    }
    var offeredServiceIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selectedProviderService = WorkshopServiceCatalog.serviceById(providerServiceId)
        ?: WorkshopServiceCatalog.enabledServicesForCategory(ServiceCategory.DIAGNOSTIC).first()

    val mechanicId = myProfile?.profileId ?: activePrincipal.id

    // Limit visibility: only OPEN requests OR requests accepted by THIS mechanic.
    // Requests accepted by other mechanics will not be shown.
    val visibleRequests = remember(allRequests, mechanicId) {
        allRequests.filter { req ->
            req.status == "OPEN" || (req.status == "ACCEPTED" && req.assignedMechanicId == mechanicId)
        }
    }

    val openRequests = visibleRequests.filter { it.status == "OPEN" }
    val activeServices = visibleRequests.filter { it.status == "ACCEPTED" }

    var isOnline by remember { mutableStateOf(myProfile?.isActive ?: true) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showTopupDialog by remember { mutableStateOf(false) }

    val walletState by SpecialistWalletStore.getWalletFlow(context, mechanicId, "MECHANIC").collectAsState()

    LaunchedEffect(mechanicId) {
        SpecialistWalletStore.syncWithTrustCenter(context, mechanicId, "MECHANIC")
    }

    val myCompleted = allRequests.filter { it.status == "COMPLETED" && (it.assignedMechanicId == mechanicId || it.assignedMechanicName == mechanicName) }
    val calculatedEarnings = myCompleted.sumOf { it.priceOffer }
    val todayEarnings = if (calculatedEarnings > 0) calculatedEarnings else 185000.0
    val todayJobs = myCompleted.size.coerceAtLeast(5)
    val availablePayout = todayEarnings * 0.95

    val mechanicGps by viewModel.currentGpsLocation.collectAsState()
    val mechanicMapState = remember(openRequests, mechanicGps) {
        val firstOpen = openRequests.firstOrNull()
        val pickup = mechanicGps?.let { RideGeoPoint(it.latitude, it.longitude, it.accuracy, it.timestamp) }
            ?: firstOpen?.let { RideGeoPoint(it.latitude, it.longitude, 10f, it.createdAt) }
        val markers = openRequests.filter { it.latitude != 0.0 && it.longitude != 0.0 }.map { req ->
            RideMapMarker(
                id = req.requestId,
                point = RideGeoPoint(req.latitude, req.longitude, 10f, req.createdAt),
                label = "${req.priceOffer.toInt()} CRC · ${req.problem.take(16)}",
                role = RideMarkerRole.STOP,
            )
        }
        com.elysium369.meet.ride.map.RideMapState(
            markers = buildList {
                pickup?.let { add(RideMapMarker(id = "mechanic-gps", role = RideMarkerRole.DRIVER, point = it, label = "Mi Taller / Posición")) }
                addAll(markers)
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Cockpit del Taller / Mecánico
        item {
            SpecialistProfileHeroCard(
                roleName = "Master Mecánico Automotriz & Diagnóstico",
                businessName = mechanicName,
                ownerName = mechanicName,
                phone = mechanicPhone,
                rating = myProfile?.rating?.takeIf { it > 0.0 } ?: 4.97,
                reviewsCount = 210,
                totalJobs = myProfile?.totalJobs?.takeIf { it > 0 } ?: (myCompleted.size + 240),
                acceptanceRatePercent = 98.9,
                isVerified = myProfile?.verified ?: true,
                isOnline = isOnline,
                onToggleOnline = {
                    isOnline = it
                    myProfile?.let { p -> viewModel.toggleProviderProfile(p.profileId, it) }
                },
                onEditProfile = { showEditProfileDialog = true },
                accentColor = MechanicColors.orangeAccent,
                secondaryColor = MechanicColors.cyanAccent,
                icon = "🛠️",
                levelTitle = "TALLER CERTIFICADO PRO",
            )
        }

        // 2. Ganancias y Finanzas del Taller
        item {
            SpecialistEarningsHeroCard(
                todayEarningsCrc = todayEarnings,
                todayJobsCount = todayJobs,
                availablePayoutCrc = availablePayout,
                currencySymbol = "₡",
                accentColor = MechanicColors.orangeAccent,
                onViewDetails = {
                    showTopupDialog = true
                },
            )
        }

        // 2b. Billetera de Operación & Sistema de Saldo (5% Comisión & ₡15,000 Regalado)
        item {
            SpecialistWalletCard(
                walletState = walletState,
                roleTitle = "MECÁNICOS Y TALLER",
                accentColor = MechanicColors.orangeAccent,
                onRechargeClick = { showTopupDialog = true },
            )
        }

        // 3. Métricas Operativas
        item {
            SpecialistOperationalMetricsRow(
                radiusKm = myProfile?.radiusKm ?: 25.0,
                etaMinutes = 15,
                escrowGuaranteed = true,
                accentColor = MechanicColors.orangeAccent,
            )
        }

        // 4. Radar Satelital de Diagnósticos y Averías en Vivo
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
                border = BorderStroke(1.2.dp, MechanicColors.orangeAccent.copy(alpha = 0.8f)),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
            ) {
                Box(Modifier.fillMaxSize()) {
                    RideMapPanel(
                        state = mechanicMapState,
                        modifier = Modifier.fillMaxSize(),
                        userLocation = mechanicGps?.let { RideGeoPoint(it.latitude, it.longitude, it.accuracy, it.timestamp) },
                        onRecenterRequested = { viewModel.detectCurrentLocation(context) },
                    )
                    Row(
                        modifier = Modifier
                            .padding(10.dp)
                            .align(Alignment.TopStart)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xF0071322))
                            .border(1.dp, MechanicColors.orangeAccent.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MechanicColors.orangeAccent),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "RADAR TALLER · ${openRequests.size} AVERÍAS EN ZONA",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    SmallFloatingActionButton(
                        onClick = { viewModel.detectCurrentLocation(context) },
                        containerColor = MechanicColors.orangeAccent,
                        contentColor = Color.Black,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .graphicsLayer { shadowElevation = 8.dp.toPx() },
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = "Mi Taller", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        item {
            WorkshopServiceCatalogPanel(
                selectedService = selectedProviderService,
                activeDtcs = emptyList(),
                onServiceSelected = { service ->
                    providerServiceId = service.id
                },
                onUseService = { service ->
                    offeredServiceIds = if (service.id in offeredServiceIds) {
                        offeredServiceIds - service.id
                    } else {
                        offeredServiceIds + service.id
                    }
                    val message = if (service.id in offeredServiceIds) {
                        "Servicio agregado al taller"
                    } else {
                        "Servicio removido del taller"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                },
                actionLabel = if (selectedProviderService.id in offeredServiceIds) {
                    "QUITAR SERVICIO OFRECIDO"
                } else {
                    "MARCAR COMO SERVICIO OFRECIDO"
                }
            )
        }

        if (offeredServiceIds.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MechanicColors.cardBackground),
                    border = BorderStroke(1.dp, MechanicColors.greenAccent.copy(alpha = 0.30f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "SERVICIOS OFRECIDOS POR ESTE TALLER",
                            color = MechanicColors.greenAccent,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                        Text(
                            text = offeredServiceIds
                                .mapNotNull { WorkshopServiceCatalog.serviceById(it)?.name }
                                .joinToString(" · "),
                            color = Color.White,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        if (activeServices.isNotEmpty()) {
            item {
                Text(
                    text = "🚨 SERVICIOS DE MECÁNICA ASIGNADOS / ACTIVOS",
                    fontWeight = FontWeight.Bold,
                    color = MechanicColors.cyanAccent,
                    fontSize = 14.sp
                )
            }
            items(activeServices) { req ->
                RequestCardItem(
                    request = req,
                    isDriverView = true,
                    onOpenWaze = { lat, lng -> viewModel.openWaze(context, lat, lng) },
                    onShareWhatsApp = { lat, lng -> viewModel.shareLocationViaWhatsApp(context, lat, lng, req.location) },
                    onComplete = { onCompleteService(req.requestId, req.vehicleId) },
                    onCancel = { viewModel.cancelMechanicRequest(req.requestId) }
                )
            }
        }

        item {
            Text(
                text = "🔧 SOLICITUDES DE MECÁNICOS DISPONIBLES EN COSTA RICA",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 14.sp
            )
        }

        if (openRequests.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MechanicColors.cardBackground),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No hay solicitudes de mecánico pendientes de atención.",
                        color = MechanicColors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            items(openRequests) { req ->
                RequestCardItem(
                    request = req,
                    isDriverView = true,
                    onOpenWaze = { lat, lng -> viewModel.openWaze(context, lat, lng) },
                    onShareWhatsApp = { lat, lng -> viewModel.shareLocationViaWhatsApp(context, lat, lng, req.location) },
                    onTakeService = {
                        viewModel.takeMechanicRequest(
                            requestId = req.requestId,
                            mechanicId = mechanicId,
                            mechanicName = mechanicName,
                            mechanicPhone = mechanicPhone,
                            context = context
                        )
                    }
                )
            }
        }
    }

    if (showEditProfileDialog) {
        SpecialistEditProfileDialog(
            initialBusinessName = mechanicName,
            initialPhone = mechanicPhone,
            initialSpecialties = myProfile?.specialties ?: "Mecánica General, Diagnóstico OBD2, Frenos, Motor",
            roleTitle = "Mecánico / Taller",
            onDismiss = { showEditProfileDialog = false },
            onSave = { newName, newPhone, newSpecialties ->
                mechanicName = newName
                mechanicPhone = newPhone
                viewModel.registerProviderProfile(
                    providerType = "MECHANIC",
                    businessName = newName,
                    ownerName = newName,
                    phone = newPhone,
                    location = myProfile?.location ?: "San José, Costa Rica",
                    latitude = mechanicGps?.latitude ?: 9.9281,
                    longitude = mechanicGps?.longitude ?: -84.0907,
                    specialties = newSpecialties,
                    radiusKm = 25.0,
                    licenseNumber = myProfile?.licenseNumber ?: "MEC-2026",
                    context = context,
                )
            }
        )
    }

    if (showTopupDialog) {
        SpecialistSinpeTopupDialog(
            serviceTitle = "Mecánicos y Talleres",
            specialistId = mechanicId,
            serviceVertical = "MECHANIC",
            onDismiss = { showTopupDialog = false },
        )
    }
}

@Composable
private fun RequestCardItem(
    request: ServiceRequestEntity,
    isDriverView: Boolean,
    onOpenWaze: (Double, Double) -> Unit,
    onShareWhatsApp: (Double, Double) -> Unit,
    onTakeService: (() -> Unit)? = null,
    onComplete: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null
) {
    val formatter = remember { SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()) }
    val dateStr = formatter.format(Date(request.createdAt))

    // 500 CRC = 1 USD conversion
    val priceInCrc = request.priceOffer * 500.0

    Card(
        colors = CardDefaults.cardColors(containerColor = MechanicColors.cardBackground),
        border = BorderStroke(
            1.dp,
            when (request.status) {
                "OPEN" -> MechanicColors.orangeAccent.copy(alpha = 0.3f)
                "ACCEPTED" -> MechanicColors.cyanAccent.copy(alpha = 0.5f)
                "COMPLETED" -> MechanicColors.greenAccent.copy(alpha = 0.4f)
                else -> MechanicColors.borderSubtle
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = when (request.status) {
                        "OPEN" -> MechanicColors.orangeAccent.copy(alpha = 0.15f)
                        "ACCEPTED" -> MechanicColors.cyanAccent.copy(alpha = 0.15f)
                        "COMPLETED" -> MechanicColors.greenAccent.copy(alpha = 0.15f)
                        else -> Color.DarkGray
                    },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = when (request.status) {
                            "OPEN" -> "PENDIENTE"
                            "ACCEPTED" -> "EN PROCESO"
                            "COMPLETED" -> "COMPLETADO"
                            else -> "CANCELADO"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = when (request.status) {
                            "OPEN" -> MechanicColors.orangeAccent
                            "ACCEPTED" -> MechanicColors.cyanAccent
                            "COMPLETED" -> MechanicColors.greenAccent
                            else -> Color.Gray
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = com.elysium369.meet.core.money.Money.ofCrc(request.priceOffer.toLong()).formatted(),
                    fontWeight = FontWeight.Black,
                    color = MechanicColors.greenAccent,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Falla: ${request.problem}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Síntomas: ${request.description}",
                color = MechanicColors.textSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "📍 Ubicación: ${request.location}",
                color = Color.White,
                fontSize = 13.sp
            )
            Text(
                text = "📱 Teléfono: ${request.phone}",
                color = Color.White,
                fontSize = 13.sp
            )
            Text(
                text = "⏰ Solicitado: $dateStr",
                color = MechanicColors.textSecondary,
                fontSize = 11.sp
            )

            if (request.status == "ACCEPTED" && request.assignedMechanicName != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = MechanicColors.borderSubtle)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "🔧 Mecánico Asignado:",
                    fontSize = 12.sp,
                    color = MechanicColors.cyanAccent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${request.assignedMechanicName} (${request.assignedMechanicPhone})",
                    fontSize = 13.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { onOpenWaze(request.latitude, request.longitude) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🌐 Abrir Waze", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onShareWhatsApp(request.latitude, request.longitude) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("📱 WhatsApp", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (isDriverView && request.status == "OPEN" && onTakeService != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onTakeService,
                    colors = ButtonDefaults.buttonColors(containerColor = MechanicColors.orangeAccent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🤝 TOMAR Y ACEPTAR ESTE SERVICIO", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            if (isDriverView && request.status == "ACCEPTED") {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onComplete ?: {},
                        colors = ButtonDefaults.buttonColors(containerColor = MechanicColors.greenAccent),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("✅ COMPLETAR", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    if (onCancel != null) {
                        Button(
                            onClick = onCancel,
                            colors = ButtonDefaults.buttonColors(containerColor = MechanicColors.redAccent),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("❌ CANCELAR", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (!isDriverView && request.status == "ACCEPTED" && onComplete != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onComplete,
                    colors = ButtonDefaults.buttonColors(containerColor = MechanicColors.cyanAccent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🏁 COMPLETADO CON SATISFACCIÓN", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RatingSubmissionDialog(
    targetType: String,
    targetId: String,
    onDismiss: () -> Unit,
    onSubmit: (Double, String) -> Unit
) {
    var ratingStars by remember { mutableStateOf(5.0) }
    var commentText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MechanicColors.cardBackground),
            border = BorderStroke(1.dp, MechanicColors.cyanAccent.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "⭐ CALIFICAR SERVICIO",
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "¿Cómo calificarías este servicio de reparación?",
                    color = MechanicColors.textSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 1..5) {
                        val isFilled = ratingStars >= i.toDouble()
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Estrella $i",
                            tint = if (isFilled) MechanicColors.cyanAccent else Color.Gray,
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { ratingStars = i.toDouble() }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    label = { Text("Comentarios adicionales") },
                    placeholder = { Text("Ej. Muy profesional y rápido.") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MechanicColors.cyanAccent,
                        unfocusedBorderColor = MechanicColors.borderSubtle
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Omitir", color = Color.White)
                    }
                    Button(
                        onClick = { onSubmit(ratingStars, commentText) },
                        colors = ButtonDefaults.buttonColors(containerColor = MechanicColors.cyanAccent),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Enviar", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
