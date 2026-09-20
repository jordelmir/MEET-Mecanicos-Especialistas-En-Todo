package com.elysium369.meet.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.elysium369.meet.data.local.entities.ServiceRequestEntity
import com.elysium369.meet.ride.map.RideGeoPoint
import com.elysium369.meet.ride.map.RideMapMarker
import com.elysium369.meet.ride.map.RideMapStateFactory
import com.elysium369.meet.ride.map.RideMarkerRole
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.AccessLevel
import com.elysium369.meet.ui.components.AccessStatusCard
import com.elysium369.meet.ui.components.AccessStep
import com.elysium369.meet.ui.screens.RideMapPanel
import com.elysium369.meet.ui.theme.MeetColors
import java.util.UUID

private const val UNIVERSAL_PREFIX = "universal:"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalServicesScreen(
    viewModel: ObdViewModel,
    onBack: () -> Unit,
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
    val myProfile = profiles.firstOrNull {
        com.elysium369.meet.core.services.kernel.ProviderType.fromDbValue(it.providerType) ==
            com.elysium369.meet.core.services.kernel.ProviderType.SERVICE_PROVIDER &&
            it.isActive &&
            it.verified
    }
    var providerMode by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<UniversalServiceDefinition?>(null) }
    var showProviderRegistration by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshProviderRoles()
        viewModel.detectCurrentLocation(context)
        viewModel.voiceFeedbackManager.guideHardwareAndTradesStatus("WELCOME")
    }
    LaunchedEffect(myProfile?.profileId) {
        if (myProfile == null) providerMode = false
    }

    var selectedDomain by rememberSaveable { mutableStateOf("TODOS") }
    val domains = remember { listOf("TODOS", "Ferretería & Materiales", "Hogar", "Movilidad", "Automotriz", "Profesional", "Digital", "Logística") }

    val activeSelection = selected
    if (activeSelection != null) {
        com.elysium369.meet.ui.screens.universal.UniversalActivityWorkflowScreen(
            service = activeSelection,
            viewModel = viewModel,
            onNavigateBack = { selected = null },
            onOpenMessages = onOpenMessages,
        )
        return
    }

    Scaffold(
        containerColor = MeetColors.backgroundDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ELYSIUM · SERVICIOS Y FERRETERÍA", color = Color.White, fontWeight = FontWeight.Black)
                        Text("Subastas de materiales · Mano de obra · Combos llave en mano", color = MeetColors.cyberCyan, fontSize = 10.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver", tint = MeetColors.cyberCyan)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenMessages) {
                        Icon(Icons.Default.Chat, "Mensajes del servicio", tint = MeetColors.cyberCyan)
                    }
                    IconButton(onClick = { viewModel.voiceFeedbackManager.guideHardwareAndTradesStatus("WELCOME") }) {
                        Icon(Icons.Default.VolumeUp, "Voz Asistente", tint = MeetColors.neonGreen)
                    }
                    Text(if (providerMode) "PROVEEDOR" else "CLIENTE", color = MeetColors.neonGreen, fontSize = 10.sp)
                    Switch(
                        checked = providerMode,
                        onCheckedChange = {
                            if (it && myProfile == null) showProviderRegistration = true else providerMode = it
                        },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF061523), Color(0xFF080319))),
                ),
        ) {
            // ── Provider-only header: service board or registration prompt ──
            if (providerMode) {
                if (myProfile != null) {
                    ProviderServiceBoard(
                        viewModel = viewModel,
                        requests = allRequests.filter { it.vehicleId.startsWith(UNIVERSAL_PREFIX) },
                        providerName = myProfile?.businessName.orEmpty(),
                        providerPhone = myProfile?.phone.orEmpty(),
                        providerId = myProfile?.profileId,
                    )
                } else {
                    // Guided Access Status for providers
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AccessStatusCard(
                            serviceName = "Proveedor de Servicios",
                            serviceIcon = "🔧",
                            accessLevel = AccessLevel.NOT_REGISTERED,
                            steps = listOf(
                                AccessStep(1, "Crear perfil de proveedor", done = false),
                                AccessStep(2, "Enviar documents al Centro de Confianza", done = false),
                                AccessStep(3, "Esperar aprobación manual", done = false),
                            ),
                            accentColor = MeetColors.neonGreen,
                        )
                        Button(
                            onClick = { showProviderRegistration = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text("REGISTRARME COMO PROVEEDOR", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Shared content: search, map, catalog, requests (visible in BOTH modes) ──
            val infiniteTransition = rememberInfiniteTransition(label = "universal-radar-loop")
            val universalRadarPulse by infiniteTransition.animateFloat(
                initialValue = 0.88f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "univ-pulse",
            )
            val universalRadarGlowAlpha by infiniteTransition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
                label = "univ-glow",
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("¿Qué necesitas? Tubos PVC, plomero, cerradura, cables…") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = MeetColors.cyberCyan) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MeetColors.cyberCyan,
                    unfocusedBorderColor = Color(0xFF6B2D91),
                ),
            )

            // Category Filter Chips with 3D styling
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(domains) { domain ->
                    FilterChip(
                        selected = selectedDomain == domain,
                        onClick = { selectedDomain = domain },
                        label = { Text(domain, fontSize = 11.sp, fontWeight = if (selectedDomain == domain) FontWeight.Black else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MeetColors.cyberCyan.copy(alpha = 0.25f),
                            selectedLabelColor = MeetColors.cyberCyan,
                        )
                    )
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                // Live 3D Satellite Map (Visible immediately, matching Elysium Rides standard)
                item {
                    val previewState = remember(gps) {
                        val pickup = gps?.let { RideGeoPoint(it.latitude, it.longitude, it.accuracy, it.timestamp) }
                            ?: RideGeoPoint(9.9281, -84.0907, 10f, System.currentTimeMillis())
                        val lat = pickup.latitude
                        val lng = pickup.longitude
                        val dest = RideGeoPoint(lat + 0.007, lng + 0.005, 10f, System.currentTimeMillis())
                        RideMapStateFactory.create(
                            pickup = pickup,
                            destination = dest,
                        )
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .graphicsLayer {
                                shadowElevation = 16.dp.toPx()
                                cameraDistance = 16f * density
                            },
                        colors = CardDefaults.cardColors(containerColor = Color(0xCC06121F)),
                        border = BorderStroke(
                            1.5.dp,
                            Brush.horizontalGradient(
                                listOf(MeetColors.cyberCyan, Color(0xFFC85CFF))
                            )
                        ),
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
                            Row(
                                modifier = Modifier
                                    .padding(10.dp)
                                    .align(Alignment.TopStart)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xF0081326))
                                    .border(1.dp, MeetColors.cyberCyan.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .graphicsLayer {
                                            scaleX = universalRadarPulse
                                            scaleY = universalRadarPulse
                                            alpha = universalRadarGlowAlpha
                                        }
                                        .clip(CircleShape)
                                        .background(MeetColors.neonGreen),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "RADAR ELYSIUM · SERVICIOS Y FERRETERÍAS EN VIVO",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .padding(10.dp)
                                    .align(Alignment.BottomStart)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xF006121F))
                                    .border(1.dp, Color(0xFFC85CFF).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = "📍 COBERTURA: ${gps?.addressName?.take(22) ?: "Gran Área Metropolitana"}",
                                    color = MeetColors.cyberCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        "SUBASTA DUAL: FERRETERÍAS VENDEN MATERIALES + PLOMEROS/ELECTRICISTAS OFRECEN COLOCARLOS",
                        color = Color(0xFFC85CFF),
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                    )
                }
                val filteredServices = UniversalServiceCatalog.search(query).filter {
                    selectedDomain == "TODOS" || it.domain.equals(selectedDomain, ignoreCase = true)
                }
                items(filteredServices, key = { it.id }) { service ->
                    UniversalServiceCard(service) { selected = service }
                }
                item {
                    Text("MIS SOLICITUDES", color = MeetColors.cyberCyan, fontWeight = FontWeight.Black)
                }
                items(
                    allRequests.filter { it.vehicleId == "$UNIVERSAL_PREFIX$clientId" },
                    key = { it.requestId },
                ) { request ->
                    ClientServiceRequestCard(viewModel, request, context)
                }
            }
        }
    }


    if (showProviderRegistration) {
        ProviderQuickRegistrationDialog(
            onDismiss = { showProviderRegistration = false },
            onRegister = { businessName, ownerName, phone ->
                viewModel.registerAsProvider(
                    providerType = "SERVICE_PROVIDER",
                    businessName = businessName,
                    ownerName = ownerName,
                    phone = phone,
                    location = gps?.let { "${it.latitude},${it.longitude}" } ?: "Dato no capturado",
                    latitude = gps?.latitude ?: 0.0,
                    longitude = gps?.longitude ?: 0.0,
                    specialties = "Catálogo universal; requiere validar capacidades",
                    context = context,
                )
                showProviderRegistration = false
            },
        )
    }
}

@Composable
private fun UniversalServiceCard(service: UniversalServiceDefinition, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                shadowElevation = 8.dp.toPx()
            }
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xEE0B1728)),
        border = BorderStroke(
            1.2.dp,
            Brush.horizontalGradient(listOf(MeetColors.cyberCyan.copy(alpha = 0.6f), Color(0xFFC85CFF).copy(alpha = 0.5f)))
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF132238))
                    .border(1.dp, MeetColors.cyberCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(service.icon, fontSize = 24.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(service.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${service.domain} · ${service.modalities.joinToString { it.label }}",
                    color = MeetColors.cyberCyan,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFC85CFF))
        }
    }
}

@Composable
private fun ClientServiceRequestCard(viewModel: ObdViewModel, request: ServiceRequestEntity, context: Context) {
    val bids by viewModel.getBidsForRequest(request.requestId).collectAsState(initial = emptyList())
    val materialBids = bids.filter { it.shopName.contains("Ferreter", true) || it.message.contains("material", true) || it.message.contains("tubo", true) }
    val laborBids = bids.filter { it.shopName.contains("Plomer", true) || it.shopName.contains("Electr", true) || it.shopName.contains("Instalad", true) || it.message.contains("mano de obra", true) || it.message.contains("colocar", true) || it.message.contains("instal", true) }
    val hasDualCombo = materialBids.isNotEmpty() && laborBids.isNotEmpty()

    LaunchedEffect(bids.size) {
        if (hasDualCombo) {
            viewModel.voiceFeedbackManager.guideHardwareAndTradesStatus("DUAL_OFFER_READY")
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, if (hasDualCombo) MeetColors.neonGreen else if (request.status == "OPEN") MeetColors.cyberCyan else Color.Gray),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(request.problem, color = Color.White, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Surface(
                    color = if (request.status == "OPEN") MeetColors.neonGreen.copy(alpha = 0.15f) else Color(0x33FFFFFF),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        if (request.status == "OPEN") "SUBASTA ACTIVA" else request.status,
                        color = if (request.status == "OPEN") MeetColors.neonGreen else Color.LightGray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text("Oferta base: ₡${String.format("%,.0f", request.priceOffer)} · ${request.location}", color = MeetColors.cyberCyan, fontSize = 11.sp)
            if (request.description.isNotBlank()) {
                Text(request.description, color = MeetColors.textSecondary, fontSize = 11.sp)
            }

            // ── Combo Dual Llave en Mano Card (Ferretería + Mano de Obra) ──
            if (hasDualCombo && request.status == "OPEN") {
                val bestMaterial = materialBids.minByOrNull { it.price }!!
                val bestLabor = laborBids.minByOrNull { it.price }!!
                val comboPrice = (bestMaterial.price + bestLabor.price) * 0.95 // 5% combo discount

                Surface(
                    color = Color(0xFF132B20),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MeetColors.neonGreen)
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("⭐ COMBO LLAVE EN MANO (Material + Instalación)", color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 11.sp)
                            Text("-5% Ahorro", color = MeetColors.neonGreen, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                        Text("• Ferretería: ${bestMaterial.shopName} (₡${String.format("%,.0f", bestMaterial.price)})", color = Color.White, fontSize = 11.sp)
                        Text("• Especialista: ${bestLabor.shopName} (₡${String.format("%,.0f", bestLabor.price)})", color = Color.White, fontSize = 11.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Total Combo: ₡${String.format("%,.0f", comboPrice)}", color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 14.sp)
                            Button(
                                onClick = {
                                    viewModel.acceptBid(request.requestId, bestMaterial.bidId, context)
                                    viewModel.acceptBid(request.requestId, bestLabor.bidId, context)
                                    viewModel.voiceFeedbackManager.speak(
                                        es = "Combo llave en mano aceptado. Se ha notificado a la ferretería y al instalador calificado.",
                                        en = "Turnkey combo accepted. Notified hardware store and certified installer."
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("ACEPTAR COMBO", fontWeight = FontWeight.Black, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Individual Bids List
            bids.forEach { bid ->
                val isHardware = bid.shopName.contains("Ferreter", true) || bid.message.contains("material", true)
                val isLabor = bid.shopName.contains("Plomer", true) || bid.shopName.contains("Electr", true) || bid.shopName.contains("Instal", true)

                Surface(
                    color = Color(0x22FFFFFF),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (isHardware) "🔩" else if (isLabor) "🛠️" else "💼", fontSize = 12.sp)
                                Text(bid.shopName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Text("₡${String.format("%,.0f", bid.price)} · Garantía ${bid.warrantyDays} días", color = MeetColors.cyberCyan, fontSize = 11.sp)
                            if (bid.message.isNotBlank()) {
                                Text(bid.message, color = MeetColors.textSecondary, fontSize = 10.sp, maxLines = 1)
                            }
                        }
                        if (request.status == "OPEN") {
                            Button(
                                onClick = {
                                    viewModel.acceptBid(request.requestId, bid.bidId, context)
                                    viewModel.voiceFeedbackManager.speak(
                                        es = "Propuesta de ${bid.shopName} aceptada exitosamente.",
                                        en = "Proposal from ${bid.shopName} accepted successfully."
                                    )
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("ACEPTAR", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
            Text(
                "Protección de Pago Elysium Escrow: fondos liberados contra entrega y verificación de evidencia.",
                color = MeetColors.textMuted,
                fontSize = 9.sp,
            )
        }
    }
}

@Composable
private fun ProviderServiceBoard(
    viewModel: ObdViewModel,
    requests: List<ServiceRequestEntity>,
    providerName: String,
    providerPhone: String,
    providerId: String?,
) {
    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("OPORTUNIDADES ABIERTAS", color = MeetColors.neonGreen, fontWeight = FontWeight.Black)
            Text("La primera aceptación atómica gana; el chat no cambia precios ni contratos.", color = MeetColors.textMuted, fontSize = 10.sp)
        }
        items(requests.filter { it.status == "OPEN" }, key = { it.requestId }) { request ->
            var offerText by remember(request.requestId) { mutableStateOf(request.priceOffer.toLong().toString()) }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xCC0A1726)),
                border = BorderStroke(1.dp, Color(0xFFC85CFF)),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(request.problem, color = Color.White, fontWeight = FontWeight.Black)
                    Text(request.location, color = MeetColors.cyberCyan, fontSize = 11.sp)
                    Text(request.description.take(180), color = MeetColors.textSecondary, fontSize = 11.sp)
                    OutlinedTextField(
                        value = offerText,
                        onValueChange = { offerText = it.filter(Char::isDigit) },
                        label = { Text("Oferta CRC") },
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            viewModel.placeServiceBid(
                                requestId = request.requestId,
                                price = offerText.toDoubleOrNull() ?: request.priceOffer,
                                estimatedHours = 1.0,
                                warrantyDays = 7,
                                message = "Oferta Elysium; alcance final sujeto a confirmar evidencia y entregables.",
                                providerPhone = providerPhone,
                                providerName = providerName.ifBlank { "Proveedor Elysium" },
                                providerId = providerId,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("ENVIAR OFERTA") }
                }
            }
        }
    }
}



@Composable
private fun ProviderQuickRegistrationDialog(
    onDismiss: () -> Unit,
    onRegister: (String, String, String) -> Unit,
) {
    var business by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registro de proveedor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tu perfil inicia sin verificación. Los servicios restringidos requieren revisión documental.")
                OutlinedTextField(business, { business = it }, label = { Text("Nombre comercial") })
                OutlinedTextField(owner, { owner = it }, label = { Text("Responsable") })
                OutlinedTextField(phone, { phone = it }, label = { Text("Teléfono") })
            }
        },
        confirmButton = {
            Button(
                enabled = business.isNotBlank() && owner.isNotBlank() && phone.count(Char::isDigit) >= 7,
                onClick = { onRegister(business, owner, phone) },
            ) { Text("REGISTRAR") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
