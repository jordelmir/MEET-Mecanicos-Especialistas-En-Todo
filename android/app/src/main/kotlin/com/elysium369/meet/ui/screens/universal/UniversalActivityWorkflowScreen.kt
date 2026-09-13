package com.elysium369.meet.ui.screens.universal

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.elysium369.meet.core.services.UniversalServiceCatalog
import com.elysium369.meet.core.services.UniversalServiceDefinition
import com.elysium369.meet.core.services.UniversalServiceModality
import com.elysium369.meet.data.local.entities.ServiceBidEntity
import com.elysium369.meet.data.local.entities.ServiceRequestEntity
import com.elysium369.meet.ride.map.RideGeoPoint
import com.elysium369.meet.ride.map.RideMapMarker
import com.elysium369.meet.ride.map.RideMapState
import com.elysium369.meet.ride.map.RideMapStateFactory
import com.elysium369.meet.ride.map.RideMarkerRole
import com.elysium369.meet.core.wallet.SpecialistWalletStore
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.SpecialistEditProfileDialog
import com.elysium369.meet.ui.components.SpecialistEarningsHeroCard
import com.elysium369.meet.ui.components.SpecialistOperationalMetricsRow
import com.elysium369.meet.ui.components.SpecialistProfileHeroCard
import com.elysium369.meet.ui.components.SpecialistRoleBanner
import com.elysium369.meet.ui.components.SpecialistWalletCard
import com.elysium369.meet.ui.components.SpecialistSinpeTopupDialog
import com.elysium369.meet.ui.screens.RideMapPanel
import com.elysium369.meet.ui.screens.RidePinPickerDialog
import com.elysium369.meet.ui.theme.MeetColors
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val UNIVERSAL_PREFIX = "universal:"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalActivityWorkflowScreen(
    service: UniversalServiceDefinition,
    viewModel: ObdViewModel,
    onNavigateBack: () -> Unit,
    onOpenMessages: () -> Unit = {},
) {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("elysium_universal_services", Context.MODE_PRIVATE)
    }
    val clientId = remember {
        preferences.getString("client_id", null) ?: UUID.randomUUID().toString().also {
            preferences.edit { putString("client_id", it) }
        }
    }

    val allRequests by viewModel.serviceRequests.collectAsState()
    val profiles by viewModel.userProviderProfiles.collectAsState()
    val gps by viewModel.currentGpsLocation.collectAsState()
    val vehicles by viewModel.vehicles.collectAsState()
    val activeDtcs by viewModel.activeDtcs.collectAsState()

    var isAdminMode by rememberSaveable(service.id) { mutableStateOf(false) }

    // Client form state
    var selectedTasks by rememberSaveable(service.id) { mutableStateOf(setOf<String>()) }
    var taskDescription by rememberSaveable(service.id) { mutableStateOf("") }
    var offerPriceText by rememberSaveable(service.id) {
        mutableStateOf(service.defaultEstimatedPriceCrc.toLong().toString())
    }
    var selectedModality by remember(service.id) {
        mutableStateOf(service.modalities.firstOrNull() ?: UniversalServiceModality.PHYSICAL)
    }
    var urgency by rememberSaveable(service.id) { mutableStateOf("HOY") } // URGENTE, HOY, PROGRAMADO

    // Specialized domain state
    var towModality by rememberSaveable(service.id) { mutableStateOf("PLATAFORMA") } // PLATAFORMA, ARRASTRE, RAPIDO
    var vehicleCondition by rememberSaveable(service.id) { mutableStateOf("RUEDAS_LIBRES") } // RUEDAS_LIBRES, BLOQUEADO, SOTANO, VOLCADO
    var includeTurnkeyLabor by rememberSaveable(service.id) { mutableStateOf(false) }
    var linkActiveDtcs by rememberSaveable(service.id) { mutableStateOf(true) }
    var partsQualityTier by rememberSaveable(service.id) { mutableStateOf("OEM") } // OEM, AFTERMARKET, USADO

    // Pin pickers
    var servicePoint by remember(service.id) {
        mutableStateOf(
            gps?.let {
                RideGeoPoint(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    accuracyMeters = it.accuracy,
                    capturedAtEpochMs = it.timestamp,
                )
            },
        )
    }
    var serviceLocationAddress by rememberSaveable(service.id) {
        mutableStateOf(gps?.addressName ?: gps?.let { "${it.latitude},${it.longitude}" }.orEmpty())
    }
    var showServicePinPicker by remember(service.id) { mutableStateOf(false) }

    var destPoint by remember(service.id) { mutableStateOf<RideGeoPoint?>(null) }
    var destLocationAddress by rememberSaveable(service.id) { mutableStateOf("") }
    var showDestPinPicker by remember(service.id) { mutableStateOf(false) }

    // Selected vehicle for automotive/tow/parts
    var selectedVehicleId by rememberSaveable(service.id) {
        mutableStateOf(vehicles.firstOrNull()?.id.orEmpty())
    }

    LaunchedEffect(gps) {
        if (servicePoint == null && gps != null) {
            val point = RideGeoPoint(
                latitude = gps!!.latitude,
                longitude = gps!!.longitude,
                accuracyMeters = gps!!.accuracy,
                capturedAtEpochMs = gps!!.timestamp,
            )
            servicePoint = point
            if (serviceLocationAddress.isBlank()) {
                serviceLocationAddress = gps!!.addressName ?: "${gps!!.latitude},${gps!!.longitude}"
            }
        }
    }

    LaunchedEffect(service.id) {
        viewModel.detectCurrentLocation(context)
        val greeting = if (isAdminMode) {
            "Panel de administración para ${service.adminRoleName} activo."
        } else {
            "Flujo de ${service.name} activado. Localización y subasta en tiempo real listas."
        }
        viewModel.voiceFeedbackManager.speak(es = greeting, en = "${service.name} workflow ready.")
    }

    // Requests for this activity
    val myRequests = remember(allRequests, service.id, clientId) {
        allRequests.filter {
            it.vehicleId == "$UNIVERSAL_PREFIX$clientId" &&
                (it.description.contains(service.id) ||
                    it.description.contains(service.domain, ignoreCase = true) ||
                    it.problem.contains(service.name, ignoreCase = true))
        }
    }

    val openRequestsInArea = remember(allRequests, service.id) {
        allRequests.filter {
            it.status == "OPEN" &&
                (it.description.contains(service.id) ||
                    it.description.contains(service.domain, ignoreCase = true) ||
                    it.problem.contains(service.name, ignoreCase = true))
        }
    }

    val isAutomotive = service.domain.equals("Automotriz", ignoreCase = true) ||
        service.domain.equals("Movilidad", ignoreCase = true) ||
        service.id in listOf("roadside", "mechanical", "auto_parts")

    Scaffold(
        containerColor = MeetColors.backgroundDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(service.icon, fontSize = 20.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = service.name.take(22) + if (service.name.length > 22) "…" else "",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = if (isAdminMode) "PANEL PROVEEDOR · ${service.adminRoleName.uppercase()}" else "SUBASTA EN VIVO · ESCROW Y GARANTÍA",
                            color = if (isAdminMode) MeetColors.neonGreen else MeetColors.cyberCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = MeetColors.cyberCyan)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenMessages) {
                        Icon(Icons.AutoMirrored.Filled.Chat, "Mensajes", tint = MeetColors.cyberCyan)
                    }
                    IconButton(onClick = {
                        val message = if (isAdminMode) {
                            "Estás en el tablero de administración de ${service.adminRoleName}. Tienes ${openRequestsInArea.size} solicitudes abiertas en tu zona."
                        } else {
                            "Puedes solicitar ${service.name}. Selecciona ubicación satelital, requerimientos y oferta inicial."
                        }
                        viewModel.voiceFeedbackManager.speak(es = message, en = "Status update.")
                    }) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, "Voz", tint = MeetColors.neonGreen)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 4.dp)) {
                        Text(
                            text = if (isAdminMode) "PRO" else "CLI",
                            color = if (isAdminMode) MeetColors.neonGreen else MeetColors.cyberCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Switch(
                            checked = isAdminMode,
                            onCheckedChange = { isAdminMode = it },
                            modifier = Modifier.height(28.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF07111E), Color(0xFF090E17), Color(0xFF04060A)),
                    ),
                ),
        ) {
            SpecialistRoleBanner(
                isSpecialistMode = isAdminMode,
                onToggleMode = { isAdminMode = it },
                specialistLabel = service.adminRoleName.uppercase(),
                clientLabel = "CLIENTE ELYSIUM",
                specialistIcon = service.icon,
                accentColor = MeetColors.neonGreen,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )

            if (isAdminMode) {
                // ─────────────────────────────────────────────────────────────
                // PANEL DE ADMINISTRACIÓN / PROVEEDOR DEDICADO
                // ─────────────────────────────────────────────────────────────
                UniversalActivityAdminPanel(
                    service = service,
                    viewModel = viewModel,
                    openRequests = openRequestsInArea,
                    providerGps = gps,
                    profiles = profiles,
                    context = context,
                )
            } else {
                // ─────────────────────────────────────────────────────────────
                // FLUJO COMPLETO CLIENTE (ESTILO VIAJES)
                // ─────────────────────────────────────────────────────────────
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // 0. Mapa Satelital en Vivo (Estilo Viajes - Visible Inmediatamente)
                    item {
                        val previewState = remember(servicePoint, destPoint) {
                            RideMapStateFactory.create(
                                pickup = servicePoint,
                                destination = destPoint,
                            )
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xCC06121F)),
                            border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.82f)),
                            shape = RoundedCornerShape(20.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                RideMapPanel(
                                    state = previewState,
                                    modifier = Modifier.fillMaxSize(),
                                    userLocation = gps?.let { RideGeoPoint(it.latitude, it.longitude, it.accuracy, it.timestamp) },
                                    onRecenterRequested = { viewModel.detectCurrentLocation(context) },
                                )
                                // Floating Radar Badge
                                Row(
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .align(Alignment.TopStart)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color(0xEE091424))
                                        .border(1.dp, MeetColors.cyberCyan.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(MeetColors.neonGreen)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "RADAR SATELITAL · ${service.name.take(18)}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                // Floating Pin Adjust Action
                                SmallFloatingActionButton(
                                    onClick = { showServicePinPicker = true },
                                    containerColor = MeetColors.cyberCyan,
                                    contentColor = Color.Black,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(10.dp),
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

                    // 1. Tarjeta GPS Quirúrgica (Estilo Viajes)
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                            border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "📍 Ubicación del Servicio (Precisión Quirúrgica)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )

                                    val accuracy = gps?.accuracy ?: 999f
                                    val (color, label) = when {
                                        accuracy <= 5f -> Pair(MeetColors.neonGreen, "Excelente (≤5m)")
                                        accuracy <= 15f -> Pair(MeetColors.warning, "Aceptable (≤15m)")
                                        else -> Pair(MeetColors.error, "Impreciso (>15m)")
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(color.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(color),
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Text(
                                    text = serviceLocationAddress.ifBlank { "Detectando satélites y geolocalización..." },
                                    color = MeetColors.textSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = { viewModel.detectCurrentLocation(context) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.electricBlue),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).height(42.dp),
                                    ) {
                                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Actualizar Satélite", fontSize = 11.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { showServicePinPicker = true },
                                        border = BorderStroke(1.dp, MeetColors.cyberCyan),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1.3f).height(42.dp),
                                    ) {
                                        Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp), tint = MeetColors.cyberCyan)
                                        Spacer(Modifier.width(6.dp))
                                        Text("AJUSTAR EN MAPA", fontSize = 11.sp, color = MeetColors.cyberCyan)
                                    }
                                }
                            }
                        }
                    }

                    // 2. Tarjeta de Destino / Traslado (si aplica: Grúa, Mudanza, Envíos, Repuestos)
                    if (service.requiresDestination) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                                border = BorderStroke(1.dp, Color(0xFFC85CFF).copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "🏁 Destino de Traslado / Entrega (Taller, Casa, etc.)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )

                                    OutlinedTextField(
                                        value = destLocationAddress,
                                        onValueChange = { destLocationAddress = it },
                                        placeholder = { Text("Escribe dirección o selecciona en el mapa", fontSize = 12.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Color(0xFFC85CFF),
                                            unfocusedBorderColor = MeetColors.borderSubtle,
                                        ),
                                    )

                                    OutlinedButton(
                                        onClick = { showDestPinPicker = true },
                                        border = BorderStroke(1.dp, Color(0xFFC85CFF)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().height(42.dp),
                                    ) {
                                        Icon(Icons.Default.PinDrop, null, modifier = Modifier.size(16.dp), tint = Color(0xFFC85CFF))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (destPoint == null) "FIJAR DESTINO CON PIN" else "DESTINO FIJADO · CAMBIAR PIN",
                                            fontSize = 11.sp,
                                            color = Color(0xFFC85CFF),
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2.1 Módulo Especializado de Grúa (Si aplica)
                    if (service.id in listOf("roadside", "emergency_assistance") || service.name.contains("Grúa", ignoreCase = true)) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF10192A)),
                                border = BorderStroke(1.dp, Color(0xFFFF9100).copy(alpha = 0.7f)),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🚛", fontSize = 18.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "MODALIDAD DE GRÚA Y ESTADO DEL VEHÍCULO",
                                            color = Color(0xFFFF9100),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black,
                                        )
                                    }
                                    Text("Tipo de unidad requerida:", color = MeetColors.textSecondary, fontSize = 11.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(
                                            "PLATAFORMA" to "🚛 Plataforma",
                                            "ARRASTRE" to "🛞 Arrastre",
                                            "RAPIDO" to "⚡ Rápido",
                                        ).forEach { (id, label) ->
                                            FilterChip(
                                                selected = towModality == id,
                                                onClick = { towModality = id },
                                                label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = Color(0xFFFF9100).copy(alpha = 0.25f),
                                                    selectedLabelColor = Color(0xFFFF9100),
                                                ),
                                            )
                                        }
                                    }
                                    Text("Condición del vehículo:", color = MeetColors.textSecondary, fontSize = 11.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(
                                            "RUEDAS_LIBRES" to "🟢 Neutro",
                                            "BLOQUEADO" to "🟡 Bloqueado",
                                            "SOTANO" to "🅿️ Sótano",
                                            "VOLCADO" to "🔴 Winch",
                                        ).forEach { (id, label) ->
                                            FilterChip(
                                                selected = vehicleCondition == id,
                                                onClick = { vehicleCondition = id },
                                                label = { Text(label, fontSize = 9.sp) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2.2 Módulo Especializado Mecánica / OBD2
                    if ((service.id in listOf("mechanical", "automotive") || service.name.contains("Mecánica", ignoreCase = true)) && activeDtcs.isNotEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
                                border = BorderStroke(1.dp, MeetColors.hotMagenta.copy(alpha = 0.75f)),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
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
                                                "DIAGNÓSTICO OBD2 EN VIVO DETECTADO",
                                                color = MeetColors.hotMagenta,
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
                                        "Códigos detectados: ${activeDtcs.joinToString(", ")}",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        "Se enviará la telemetría para que los talleres coticen la solución exacta.",
                                        color = MeetColors.textSecondary,
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        }
                    }

                    // 2.3 Módulo Especializado Ferretería & Oficios: Combo Llave en Mano
                    if (service.domain.equals("Ferretería & Materiales", ignoreCase = true)) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF181528)),
                                border = BorderStroke(1.dp, Color(0xFFC85CFF).copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "🛠️ Combo Llave en Mano (Material + Instalación)",
                                            color = Color(0xFFC85CFF),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black,
                                        )
                                        Text(
                                            "Instalación técnica certificada con 5% de descuento en materiales.",
                                            color = MeetColors.textSecondary,
                                            fontSize = 10.sp,
                                        )
                                    }
                                    Switch(
                                        checked = includeTurnkeyLabor,
                                        onCheckedChange = { includeTurnkeyLabor = it },
                                    )
                                }
                            }
                        }
                    }

                    // 2.4 Módulo Especializado Repuestos: Grado Técnico
                    if (service.id == "auto_parts") {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1D2D)),
                                border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "⚙️ Grado Técnico de Repuesto Solicitado",
                                        color = MeetColors.neonGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(
                                            "OEM" to "🌟 OEM Original",
                                            "AFTERMARKET" to "⚙️ Aftermarket",
                                            "USADO" to "🔄 Usado Probado",
                                        ).forEach { (tier, label) ->
                                            FilterChip(
                                                selected = partsQualityTier == tier,
                                                onClick = { partsQualityTier = tier },
                                                label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Selección Rápida de Tareas (Chips inteligentes según actividad)
                    if (service.commonTasks.isNotEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "⚡ Requerimientos comunes de ${service.name}",
                                        color = MeetColors.neonGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                    )
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(service.commonTasks) { task ->
                                            val isSelected = selectedTasks.contains(task)
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    selectedTasks = if (isSelected) {
                                                        selectedTasks - task
                                                    } else {
                                                        selectedTasks + task
                                                    }
                                                    taskDescription = if (selectedTasks.isNotEmpty()) {
                                                        "Requiero: ${selectedTasks.joinToString(", ")}. "
                                                    } else ""
                                                },
                                                label = { Text(task, fontSize = 11.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MeetColors.cyberCyan.copy(alpha = 0.25f),
                                                    selectedLabelColor = MeetColors.cyberCyan,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Detalle de Necesidad, Alcance y Especificaciones
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MeetColors.borderSubtle),
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "📋 Alcance, Especificaciones y Entregables",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                )

                                OutlinedTextField(
                                    value = taskDescription,
                                    onValueChange = { taskDescription = it },
                                    placeholder = {
                                        Text(
                                            "Describe medidas, piezas requeridas, marca, o condiciones específicas del trabajo…",
                                            fontSize = 12.sp,
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = MeetColors.cyberCyan,
                                        unfocusedBorderColor = MeetColors.borderSubtle,
                                    ),
                                )

                                // Modalidad y Urgencia
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text("Modalidad", color = MeetColors.textSecondary, fontSize = 10.sp)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            service.modalities.forEach { modality ->
                                                FilterChip(
                                                    selected = selectedModality == modality,
                                                    onClick = { selectedModality = modality },
                                                    label = { Text(modality.label, fontSize = 10.sp) },
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    listOf("URGENTE" to "🚨 Urgente", "HOY" to "📅 Hoy", "PROGRAMADO" to "📆 Programado").forEach { (key, label) ->
                                        FilterChip(
                                            selected = urgency == key,
                                            onClick = { urgency = key },
                                            label = { Text(label, fontSize = 10.sp) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. Integración Vehicular (si aplica a Automotriz, Grúa o Repuestos)
                    if (isAutomotive && vehicles.isNotEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0x99101E2E)),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.3f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    val activeCar = vehicles.firstOrNull { it.id == selectedVehicleId } ?: vehicles.first()
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("🚗 Vehículo Vinculado al Servicio", color = MeetColors.cyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("${activeCar.make} ${activeCar.model} (${activeCar.year}) · Placa: ${activeCar.plate}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text("VIN Validado", color = MeetColors.neonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // 6. Oferta Inicial en CRC (Motor de Subasta Dual)
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.5f)),
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("💰 Oferta Base Sugerida (Subasta CRC)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Protección Escrow", color = MeetColors.neonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedTextField(
                                    value = offerPriceText,
                                    onValueChange = { offerPriceText = it.filter(Char::isDigit) },
                                    leadingIcon = { Text("₡", color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 18.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = MeetColors.neonGreen,
                                        unfocusedBorderColor = MeetColors.borderSubtle,
                                    ),
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(2000L, 5000L, 10000L).forEach { delta ->
                                        OutlinedButton(
                                            onClick = {
                                                val current = offerPriceText.toLongOrNull() ?: service.defaultEstimatedPriceCrc.toLong()
                                                offerPriceText = (current + delta).toString()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.6f)),
                                            modifier = Modifier.weight(1f).height(36.dp),
                                        ) {
                                            Text("+₡${delta / 1000}k", fontSize = 10.sp, color = MeetColors.neonGreen)
                                        }
                                    }
                                }

                                Text(
                                    text = "Protección Elysium Escrow: Tu pago permanece custodiado y solo se libera cuando confirmes la entrega satisfactoria.",
                                    color = MeetColors.textMuted,
                                    fontSize = 10.sp,
                                )

                                Button(
                                    onClick = {
                                        val price = offerPriceText.toDoubleOrNull() ?: service.defaultEstimatedPriceCrc
                                        val title = "${service.icon} ${service.name}"
                                        val metadata = buildString {
                                            appendLine("[ELYSIUM_UNIVERSAL_SERVICE]")
                                            appendLine("definition_id=${service.id}")
                                            appendLine("domain=${service.domain}")
                                            appendLine("modality=${selectedModality.name}")
                                            appendLine("urgency=$urgency")
                                            if (service.id in listOf("roadside", "emergency_assistance") || service.name.contains("Grúa", ignoreCase = true)) {
                                                appendLine("tow_modality=$towModality")
                                                appendLine("vehicle_condition=$vehicleCondition")
                                            }
                                            if (service.domain.equals("Ferretería & Materiales", ignoreCase = true) && includeTurnkeyLabor) {
                                                appendLine("turnkey_combo=true")
                                            }
                                            if (service.id == "auto_parts") {
                                                appendLine("parts_tier=$partsQualityTier")
                                            }
                                            appendLine("destination=${destLocationAddress.takeIf { it.isNotBlank() } ?: "N/A"}")
                                            appendLine("dest_lat=${destPoint?.latitude ?: 0.0}")
                                            appendLine("dest_lng=${destPoint?.longitude ?: 0.0}")
                                            appendLine("price_minor=${(price * 100).toLong()}")
                                            append("[/ELYSIUM_UNIVERSAL_SERVICE]")
                                        }

                                        val relevantDtcs = if (linkActiveDtcs && (service.id in listOf("mechanical", "automotive") || service.name.contains("Mecánica", ignoreCase = true))) {
                                            activeDtcs
                                        } else {
                                            emptyList()
                                        }

                                        viewModel.createServiceRequest(
                                            vehicleId = "$UNIVERSAL_PREFIX$clientId",
                                            problem = title,
                                            description = taskDescription.ifBlank { "Solicitud de servicio estándar para ${service.name}." },
                                            location = serviceLocationAddress.ifBlank { "${servicePoint?.latitude ?: 0.0},${servicePoint?.longitude ?: 0.0}" },
                                            priority = if (urgency == "URGENTE") "HIGH" else "MEDIUM",
                                            latitude = servicePoint?.latitude ?: 0.0,
                                            longitude = servicePoint?.longitude ?: 0.0,
                                            priceOffer = price,
                                            serviceCategory = service.domain,
                                            serviceMetadata = metadata,
                                            dtcCodes = relevantDtcs,
                                        )

                                        viewModel.voiceFeedbackManager.guideHardwareAndTradesStatus("REQUEST_PUBLISHED", materialName = service.name)
                                        Toast.makeText(context, "Solicitud de ${service.name} publicada en el radar de especialistas.", Toast.LENGTH_LONG).show()
                                        taskDescription = ""
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    enabled = (offerPriceText.toDoubleOrNull() ?: 0.0) > 0,
                                ) {
                                    Text(
                                        text = "🚀 PUBLICAR SOLICITUD DE ${service.name.take(20).uppercase()}",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                        }
                    }

                    // 7. Mis Solicitudes y Subastas en Vivo para esta Actividad
                    if (myRequests.isNotEmpty()) {
                        item {
                            Text(
                                text = "📡 Tus Solicitudes Activas de ${service.name}",
                                color = MeetColors.cyberCyan,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                            )
                        }

                        items(myRequests, key = { it.requestId }) { req ->
                            ActivityClientRequestCard(viewModel = viewModel, request = req, context = context)
                        }
                    }
                }
            }
        }
    }

    // Modal de selección de pin en mapa para Servicio
    if (showServicePinPicker) {
        RidePinPickerDialog(
            targetLabel = "Ubicación de ${service.name}",
            state = RideMapStateFactory.create(pickup = servicePoint),
            initialPoint = servicePoint,
            onPinChanged = { servicePoint = it },
            onDismiss = { showServicePinPicker = false },
            onConfirm = {
                servicePoint = it
                serviceLocationAddress = "${String.format("%.5f", it.latitude)}, ${String.format("%.5f", it.longitude)}"
                showServicePinPicker = false
            },
        )
    }

    // Modal de selección de pin en mapa para Destino
    if (showDestPinPicker) {
        RidePinPickerDialog(
            targetLabel = "Destino de ${service.name}",
            state = RideMapStateFactory.create(pickup = destPoint ?: servicePoint),
            initialPoint = destPoint ?: servicePoint,
            onPinChanged = { destPoint = it },
            onDismiss = { showDestPinPicker = false },
            onConfirm = {
                destPoint = it
                destLocationAddress = "${String.format("%.5f", it.latitude)}, ${String.format("%.5f", it.longitude)}"
                showDestPinPicker = false
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPONENTE: PANEL DE ADMINISTRACIÓN / PROVEEDOR POR ACTIVIDAD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun UniversalActivityAdminPanel(
    service: UniversalServiceDefinition,
    viewModel: ObdViewModel,
    openRequests: List<ServiceRequestEntity>,
    providerGps: ObdViewModel.GpsLocationInfo?,
    profiles: List<com.elysium369.meet.data.local.entities.ProviderProfileEntity>,
    context: Context,
) {
    val myProfile = profiles.firstOrNull { it.isActive }
    val specialistId = myProfile?.profileId ?: "elysium_${service.id}"
    var isOnline by remember { mutableStateOf(true) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showTopupDialog by remember { mutableStateOf(false) }

    val walletState by SpecialistWalletStore.getWalletFlow(context, specialistId, service.id).collectAsState()

    LaunchedEffect(specialistId) {
        SpecialistWalletStore.syncWithTrustCenter(context, specialistId, service.id)
    }

    var adminName by remember(myProfile) {
        mutableStateOf(myProfile?.businessName?.ifBlank { "Especialista ${service.adminRoleName}" } ?: "Especialista ${service.adminRoleName}")
    }
    var adminPhone by remember(myProfile) {
        mutableStateOf(myProfile?.phone?.ifBlank { "+506 Central Especialistas" } ?: "+506 Central Especialistas")
    }

    val adminMapState = remember(providerGps, openRequests) {
        val center = providerGps?.let { RideGeoPoint(it.latitude, it.longitude, it.accuracy, it.timestamp) }
            ?: RideGeoPoint(9.9281, -84.0907, 10f, System.currentTimeMillis())
        val markers = openRequests.mapNotNull { req ->
            val lat = req.latitude
            val lng = req.longitude
            if (lat != 0.0 && lng != 0.0) {
                com.elysium369.meet.ride.map.RideMapMarker(
                    id = req.requestId,
                    point = RideGeoPoint(lat, lng, 10f, System.currentTimeMillis()),
                    label = "${service.icon} ₡${String.format("%,.0f", req.priceOffer)} · ${req.problem.take(16)}",
                    role = com.elysium369.meet.ride.map.RideMarkerRole.STOP,
                )
            } else null
        }
        com.elysium369.meet.ride.map.RideMapState(
            markers = buildList {
                add(com.elysium369.meet.ride.map.RideMapMarker(id = "admin-gps", role = com.elysium369.meet.ride.map.RideMarkerRole.DRIVER, point = center, label = "Mi Base Operativa"))
                addAll(markers)
            }
        )
    }

    val todayEarnings = remember(openRequests) {
        val completedCount = openRequests.count { it.status == "COMPLETED" }
        (completedCount * service.defaultEstimatedPriceCrc).coerceAtLeast(service.defaultEstimatedPriceCrc * 1.5)
    }
    val todayJobs = remember(openRequests) {
        openRequests.count { it.status == "COMPLETED" }.coerceAtLeast(2)
    }
    val availablePayout = remember(todayEarnings) { todayEarnings * 0.95 }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. Hero Card: Perfil Profesional del Especialista
        item {
            SpecialistProfileHeroCard(
                roleName = service.adminRoleName.uppercase(),
                businessName = adminName,
                ownerName = adminName,
                phone = adminPhone,
                rating = myProfile?.rating ?: 4.97,
                reviewsCount = 168,
                totalJobs = 214,
                acceptanceRatePercent = 99.4,
                isVerified = myProfile?.verified ?: true,
                isOnline = isOnline,
                onToggleOnline = { isOnline = it },
                onEditProfile = { showEditProfileDialog = true },
                accentColor = MeetColors.neonGreen,
                secondaryColor = MeetColors.cyberCyan,
                icon = service.icon,
                levelTitle = "ESPECIALISTA CERTIFICADO ELYSIUM",
            )
        }

        // 2. Resumen Financiero & Ganancias de Hoy
        item {
            SpecialistEarningsHeroCard(
                todayEarningsCrc = todayEarnings,
                todayJobsCount = todayJobs,
                availablePayoutCrc = availablePayout,
                currencySymbol = "₡",
                accentColor = MeetColors.neonGreen,
                onViewDetails = {
                    showTopupDialog = true
                },
            )
        }

        // 2b. Billetera de Operación & Sistema de Saldo (5% Comisión & ₡15,000 Regalado)
        item {
            SpecialistWalletCard(
                walletState = walletState,
                roleTitle = service.adminRoleName.uppercase(),
                accentColor = MeetColors.neonGreen,
                onRechargeClick = { showTopupDialog = true },
            )
        }

        // 3. Eficiencia Operativa
        item {
            SpecialistOperationalMetricsRow(
                radiusKm = myProfile?.radiusKm ?: 25.0,
                etaMinutes = 15,
                escrowGuaranteed = true,
                accentColor = MeetColors.neonGreen,
            )
        }

        // 4. Live Radar Map for Service Dispatch
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
                border = BorderStroke(1.2.dp, MeetColors.neonGreen.copy(alpha = 0.8f)),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
            ) {
                Box(Modifier.fillMaxSize()) {
                    RideMapPanel(
                        state = adminMapState,
                        modifier = Modifier.fillMaxSize(),
                        userLocation = providerGps?.let { RideGeoPoint(it.latitude, it.longitude, it.accuracy, it.timestamp) },
                        onRecenterRequested = { viewModel.detectCurrentLocation(context) },
                    )
                    Row(
                        modifier = Modifier
                            .padding(10.dp)
                            .align(Alignment.TopStart)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xF0071322))
                            .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MeetColors.neonGreen),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "RADAR DESPACHO ${service.adminRoleName.uppercase()} · ${openRequests.size} SOLICITUDES",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Tablero de Despacho en Vivo
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "📡 Solicitudes en Vivo (${service.name})",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                )
                Text(
                    text = "${openRequests.size} disponibles",
                    color = MeetColors.neonGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (openRequests.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("🛰️", fontSize = 32.sp)
                        Text(
                            text = "No hay solicitudes pendientes en este momento",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                        Text(
                            text = "Mantén la aplicación abierta. En cuanto un cliente solicite ${service.name}, sonará la alerta y aparecerá aquí.",
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        } else {
            items(openRequests, key = { it.requestId }) { request ->
                ProviderActivityBidCard(
                    request = request,
                    service = service,
                    providerGps = providerGps,
                    providerName = myProfile?.businessName.orEmpty().ifBlank { "Especialista ${service.adminRoleName}" },
                    providerPhone = myProfile?.phone.orEmpty(),
                    providerId = myProfile?.profileId,
                    onSendBid = { price, hours, warranty, message ->
                        viewModel.placeServiceBid(
                            requestId = request.requestId,
                            price = price,
                            estimatedHours = hours,
                            warrantyDays = warranty,
                            message = message,
                            providerPhone = myProfile?.phone.orEmpty(),
                            providerName = myProfile?.businessName.orEmpty().ifBlank { "Especialista ${service.adminRoleName}" },
                            providerId = myProfile?.profileId,
                        )
                        Toast.makeText(context, "Cotización de ₡${price.toInt()} enviada al cliente.", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }

    if (showEditProfileDialog) {
        SpecialistEditProfileDialog(
            initialBusinessName = adminName,
            initialPhone = adminPhone,
            initialSpecialties = myProfile?.specialties ?: "${service.name}, Diagnóstico, Instalación, Emergencias",
            roleTitle = service.adminRoleName,
            onDismiss = { showEditProfileDialog = false },
            onSave = { newName, newPhone, newSpecialties ->
                adminName = newName
                adminPhone = newPhone
                viewModel.registerProviderProfile(
                    providerType = "UNIVERSAL_${service.id.uppercase()}",
                    businessName = newName,
                    ownerName = newName,
                    phone = newPhone,
                    location = myProfile?.location ?: "San José, Costa Rica",
                    latitude = providerGps?.latitude ?: 9.9281,
                    longitude = providerGps?.longitude ?: -84.0907,
                    specialties = newSpecialties,
                    radiusKm = 25.0,
                    licenseNumber = myProfile?.licenseNumber ?: "ELY-2026",
                    context = context,
                )
                showEditProfileDialog = false
                Toast.makeText(context, "Perfil de Especialista actualizado", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showTopupDialog) {
        SpecialistSinpeTopupDialog(
            serviceTitle = service.name,
            specialistId = specialistId,
            serviceVertical = service.id,
            onDismiss = { showTopupDialog = false },
        )
    }
}

@Composable
private fun AdminMetricCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, color = MeetColors.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ProviderActivityBidCard(
    request: ServiceRequestEntity,
    service: UniversalServiceDefinition,
    providerGps: ObdViewModel.GpsLocationInfo?,
    providerName: String,
    providerPhone: String,
    providerId: String?,
    onSendBid: (price: Double, hours: Double, warrantyDays: Int, note: String) -> Unit,
) {
    val context = LocalContext.current
    var bidPriceText by remember(request.requestId) {
        mutableStateOf(request.priceOffer.toLong().toString())
    }
    var warrantyDays by remember(request.requestId) { mutableStateOf(30) }
    var technicalNote by remember(request.requestId) {
        mutableStateOf("Oferta con repuestos garantizados y mano de obra profesional.")
    }

    val distanceKm = remember(request.latitude, request.longitude, providerGps) {
        if (providerGps != null && request.latitude != 0.0 && request.longitude != 0.0) {
            calculateDistanceKm(providerGps.latitude, providerGps.longitude, request.latitude, request.longitude)
        } else null
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xDD0D1B2A)),
        border = BorderStroke(1.dp, Color(0xFFC85CFF).copy(alpha = 0.6f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(request.problem, color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (distanceKm != null) {
                    Surface(
                        color = MeetColors.cyberCyan.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = "📍 ${String.format("%.1f", distanceKm)} km",
                            color = MeetColors.cyberCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Text("📍 ${request.location}", color = MeetColors.cyberCyan, fontSize = 11.sp)
            if (request.description.isNotBlank()) {
                Text(request.description, color = MeetColors.textSecondary, fontSize = 11.sp)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Presupuesto base cliente: ₡${String.format("%,.0f", request.priceOffer)}", color = MeetColors.neonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // Formulario de Cotización Rápida
            OutlinedTextField(
                value = bidPriceText,
                onValueChange = { bidPriceText = it.filter(Char::isDigit) },
                label = { Text("Tu precio cotizado (CRC)") },
                leadingIcon = { Text("₡", color = MeetColors.neonGreen, fontWeight = FontWeight.Bold) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(7 to "7d garantía", 15 to "15d garantía", 30 to "30d garantía").forEach { (days, label) ->
                    FilterChip(
                        selected = warrantyDays == days,
                        onClick = { warrantyDays = days },
                        label = { Text(label, fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (request.latitude != 0.0 && request.longitude != 0.0) {
                    OutlinedButton(
                        onClick = {
                            val uri = Uri.parse("https://waze.com/ul?ll=${request.latitude},${request.longitude}&navigate=yes")
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MeetColors.cyberCyan),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("VER WAZE", fontSize = 10.sp, color = MeetColors.cyberCyan)
                    }
                }

                Button(
                    onClick = {
                        val price = bidPriceText.toDoubleOrNull() ?: request.priceOffer
                        onSendBid(price, 1.0, warrantyDays, technicalNote)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1.5f),
                ) {
                    Text("ENVIAR OFERTA ⚡", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPONENTE: TARJETA DE SOLICITUD DEL CLIENTE CON SUBASTA DUAL
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ActivityClientRequestCard(
    viewModel: ObdViewModel,
    request: ServiceRequestEntity,
    context: Context,
) {
    val bids by viewModel.getBidsForRequest(request.requestId).collectAsState(initial = emptyList())
    val materialBids = bids.filter { it.shopName.contains("Ferreter", true) || it.message.contains("material", true) || it.message.contains("tubo", true) }
    val laborBids = bids.filter { it.shopName.contains("Plomer", true) || it.shopName.contains("Electr", true) || it.shopName.contains("Instal", true) || it.message.contains("mano de obra", true) || it.message.contains("colocar", true) }
    val hasDualCombo = materialBids.isNotEmpty() && laborBids.isNotEmpty()

    Card(
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, if (hasDualCombo) MeetColors.neonGreen else if (request.status == "OPEN") MeetColors.cyberCyan else Color.Gray),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(request.problem, color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Surface(
                    color = if (request.status == "OPEN") MeetColors.neonGreen.copy(alpha = 0.15f) else Color(0x33FFFFFF),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = if (request.status == "OPEN") "SUBASTA ACTIVA" else request.status,
                        color = if (request.status == "OPEN") MeetColors.neonGreen else Color.LightGray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Text("Oferta base: ₡${String.format("%,.0f", request.priceOffer)} · ${request.location}", color = MeetColors.cyberCyan, fontSize = 11.sp)
            if (request.description.isNotBlank()) {
                Text(request.description, color = MeetColors.textSecondary, fontSize = 11.sp)
            }

            // COMBO DUAL LLAVE EN MANO (Materiales + Mano de Obra con descuento)
            if (hasDualCombo && request.status == "OPEN") {
                val bestMaterial = materialBids.minByOrNull { it.price }!!
                val bestLabor = laborBids.minByOrNull { it.price }!!
                val comboPrice = (bestMaterial.price + bestLabor.price) * 0.95

                Surface(
                    color = Color(0xFF132B20),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MeetColors.neonGreen),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("⭐ COMBO LLAVE EN MANO (Material + Instalador)", color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 11.sp)
                            Text("-5% Ahorro", color = MeetColors.neonGreen, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                        Text("• Ferretería: ${bestMaterial.shopName} (₡${String.format("%,.0f", bestMaterial.price)})", color = Color.White, fontSize = 10.sp)
                        Text("• Especialista: ${bestLabor.shopName} (₡${String.format("%,.0f", bestLabor.price)})", color = Color.White, fontSize = 10.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Total Combo: ₡${String.format("%,.0f", comboPrice)}", color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 13.sp)
                            Button(
                                onClick = {
                                    viewModel.acceptBid(request.requestId, bestMaterial.bidId, context)
                                    viewModel.acceptBid(request.requestId, bestLabor.bidId, context)
                                    viewModel.voiceFeedbackManager.speak(
                                        es = "Combo llave en mano aceptado con ferretería e instalador.",
                                        en = "Turnkey combo accepted.",
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("ACEPTAR COMBO", fontWeight = FontWeight.Black, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            // Lista de Ofertas Individuales de Especialistas
            if (bids.isNotEmpty()) {
                Text("Ofertas de Profesionales (${bids.size}):", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                bids.forEach { bid ->
                    Surface(
                        color = Color(0x22FFFFFF),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(bid.shopName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("₡${String.format("%,.0f", bid.price)} · Garantía ${bid.warrantyDays} días", color = MeetColors.cyberCyan, fontSize = 10.sp)
                                if (bid.message.isNotBlank()) {
                                    Text(bid.message, color = MeetColors.textSecondary, fontSize = 9.sp, maxLines = 1)
                                }
                            }
                            if (request.status == "OPEN") {
                                Button(
                                    onClick = {
                                        viewModel.acceptBid(request.requestId, bid.bidId, context)
                                        viewModel.voiceFeedbackManager.speak(
                                            es = "Oferta de ${bid.shopName} aceptada.",
                                            en = "Offer accepted.",
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.electricBlue),
                                ) {
                                    Text("ACEPTAR", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}
