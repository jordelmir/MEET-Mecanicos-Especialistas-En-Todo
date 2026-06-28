package com.elysium369.meet.ui.screens

import android.content.Context
import androidx.compose.animation.*
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
import com.elysium369.meet.data.local.entities.RatingEntity
import com.elysium369.meet.data.local.entities.TowTruckRequestEntity
import com.elysium369.meet.ui.ObdViewModel
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
    onNavigateBack: () -> Unit = {}
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
    var ratingTargetType by remember { mutableStateOf("TOW_TRUCK") }

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                    // Blocked View - Requires Tow Truck Driver Registration
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🚛", fontSize = 64.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "MODO CONDUCTOR EXCLUSIVO",
                            color = TowTruckColors.orangeAccent,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Para recibir solicitudes de asistencia vial y grúa de otros usuarios y tomar servicios, debes registrarte como conductor o gruista verificado en MEET.",
                            color = TowTruckColors.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
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
                        ratingTargetType = "TOW_TRUCK"
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

@Composable
private fun ClientWorkspaceView(
    viewModel: ObdViewModel,
    allRequests: List<TowTruckRequestEntity>,
    prefilledVehicleInfo: String?,
    context: Context,
    onCompleteService: (String, String?) -> Unit
) {
    val autoVehicleInfo = remember { viewModel.buildVehicleInfoForRequest() }
    val vehicleInfoToUse = prefilledVehicleInfo ?: autoVehicleInfo

    var locationName by remember { mutableStateOf("") }
    var destinationName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("+506 ") }
    var isDolarCurrency by remember { mutableStateOf(false) } // Default: COLONES (CRC)
    var priceOfferCrc by remember { androidx.compose.runtime.mutableFloatStateOf(25000.0f) } // Default 25,000 CRC ($50 USD)
    var latText by remember { mutableStateOf("9.9281") }
    var lngText by remember { mutableStateOf("-84.0907") }

    val currentGps by viewModel.currentGpsLocation.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.detectCurrentLocation(context)
    }

    LaunchedEffect(currentGps) {
        currentGps?.let { gps ->
            latText = gps.latitude.toString()
            lngText = gps.longitude.toString()
            locationName = gps.addressName
            phone = "${gps.dialingPrefix} "
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Header summary banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF00E5FF).copy(alpha = 0.15f), Color(0xFF3D5AFE).copy(alpha = 0.15f))
                        )
                    )
                    .border(1.dp, TowTruckColors.cyanAccent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = TowTruckColors.cyanAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DATOS DEL VEHÍCULO Y DIAGNÓSTICO",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TowTruckColors.cyanAccent
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = vehicleInfoToUse,
                        fontSize = 14.sp,
                        color = TowTruckColors.textPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        item {
            // Request Form Card (Indriver Style)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TowTruckColors.cardBackground),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TowTruckColors.borderSubtle)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "⚡ PEDIR AUXILIO / GRÚA ESTILO INDRIVER",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = TowTruckColors.textPrimary
                    )

                    OutlinedTextField(
                        value = locationName,
                        onValueChange = { locationName = it },
                        label = { Text("📍 Ubicación actual (Ej: Av. Central, San José)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TowTruckColors.cyanAccent,
                            unfocusedBorderColor = TowTruckColors.borderSubtle,
                            focusedLabelColor = TowTruckColors.cyanAccent,
                            focusedTextColor = TowTruckColors.textPrimary,
                            unfocusedTextColor = TowTruckColors.textPrimary
                        ),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = destinationName,
                        onValueChange = { destinationName = it },
                        label = { Text("🏁 Destino (Opcional: Taller o Casa)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TowTruckColors.cyanAccent,
                            unfocusedBorderColor = TowTruckColors.borderSubtle,
                            focusedLabelColor = TowTruckColors.cyanAccent,
                            focusedTextColor = TowTruckColors.textPrimary,
                            unfocusedTextColor = TowTruckColors.textPrimary
                        ),
                        singleLine = true
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = latText,
                            onValueChange = { latText = it },
                            label = { Text("Latitud GPS") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TowTruckColors.cyanAccent,
                                unfocusedBorderColor = TowTruckColors.borderSubtle,
                                focusedTextColor = TowTruckColors.textPrimary,
                                unfocusedTextColor = TowTruckColors.textPrimary
                            ),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = lngText,
                            onValueChange = { lngText = it },
                            label = { Text("Longitud GPS") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TowTruckColors.cyanAccent,
                                unfocusedBorderColor = TowTruckColors.borderSubtle,
                                focusedTextColor = TowTruckColors.textPrimary,
                                unfocusedTextColor = TowTruckColors.textPrimary
                            ),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("📱 Teléfono de contacto / WhatsApp") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TowTruckColors.cyanAccent,
                            unfocusedBorderColor = TowTruckColors.borderSubtle,
                            focusedLabelColor = TowTruckColors.cyanAccent,
                            focusedTextColor = TowTruckColors.textPrimary,
                            unfocusedTextColor = TowTruckColors.textPrimary
                        ),
                        singleLine = true
                    )

                    // Dynamic Price Slider (Colones vs Dollars Toggle)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF070C16))
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("OFERTA DE PRECIO PROPUESTA", fontSize = 12.sp, color = TowTruckColors.textSecondary, fontWeight = FontWeight.Bold)
                            
                            // Dynamic Currency Toggle Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isDolarCurrency) TowTruckColors.orangeAccent.copy(alpha = 0.2f) else TowTruckColors.greenAccent.copy(alpha = 0.2f))
                                    .border(1.dp, if (isDolarCurrency) TowTruckColors.orangeAccent else TowTruckColors.greenAccent, RoundedCornerShape(20.dp))
                                    .clickable { isDolarCurrency = !isDolarCurrency }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isDolarCurrency) "💵 Pagar en USD ($)" else "🇨🇷 Pagar en Colones (₡)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isDolarCurrency) TowTruckColors.orangeAccent else TowTruckColors.greenAccent
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        val displayPriceStr = if (isDolarCurrency) {
                            val usdVal = priceOfferCrc / 500.0f
                            String.format(Locale.US, "$%.0f USD", usdVal)
                        } else {
                            val crcVal = (priceOfferCrc / 1000.0f).toInt() * 1000
                            "₡${String.format(Locale.US, "%,d", crcVal)} CRC"
                        }

                        Text(
                            text = displayPriceStr,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = TowTruckColors.greenAccent
                        )
                        
                        Text(
                            text = if (isDolarCurrency) "Equivalent a ₡${(priceOfferCrc).toInt()} CRC approx" else "Equivalente a $${(priceOfferCrc / 500).toInt()} USD approx",
                            fontSize = 11.sp,
                            color = TowTruckColors.textSecondary
                        )

                        Slider(
                            value = priceOfferCrc,
                            onValueChange = { priceOfferCrc = it },
                            valueRange = 10000.0f..150000.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = TowTruckColors.greenAccent,
                                activeTrackColor = TowTruckColors.greenAccent
                            )
                        )
                    }

                    Button(
                        onClick = {
                            val lat = latText.toDoubleOrNull() ?: 9.9281
                            val lng = lngText.toDoubleOrNull() ?: -84.0907
                            val loc = if (locationName.isBlank()) "Ubicación GPS ($lat, $lng)" else locationName
                            val priceInUsd = (priceOfferCrc / 500.0f).toDouble()
                            viewModel.createTowTruckRequest(
                                latitude = lat,
                                longitude = lng,
                                locationName = loc,
                                destLat = null,
                                destLng = null,
                                destName = destinationName.takeIf { it.isNotBlank() },
                                phone = phone,
                                priceOffer = priceInUsd,
                                vehicleInfoOverride = vehicleInfoToUse
                            )
                            locationName = ""
                            destinationName = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TowTruckColors.cyanAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("🚛 ENVIAR SOLICITUD A RED DE GRÚAS", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 14.sp)
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
                    onDelete = { viewModel.deleteTowTruckRequest(req.requestId) }
                )
            }
        }
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
    var driverName by remember { mutableStateOf("Grúas Express Pro") }
    var driverPhone by remember { mutableStateOf("+52 55 9999 8888") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TowTruckColors.cardBackground),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TowTruckColors.orangeAccent.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("👤 PERFIL DE OPERADOR / GRUISTA", fontWeight = FontWeight.Bold, color = TowTruckColors.orangeAccent, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = driverName,
                            onValueChange = { driverName = it },
                            label = { Text("Nombre Servicio / Unidad") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                        OutlinedTextField(
                            value = driverPhone,
                            onValueChange = { driverPhone = it },
                            label = { Text("Teléfono Operador") },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
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
                        viewModel.takeTowTruckRequest(req.requestId, "driver_101", driverName, driverPhone)
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TowTruckColors.cardBackground),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(request.status, color = statusColor, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
                var showInUsd by remember { mutableStateOf(false) }
                val priceCrc = (request.priceOffer * 500).toInt()
                val priceFormatted = if (showInUsd) "$${request.priceOffer.toInt()} USD" else "₡${String.format(Locale.US, "%,d", priceCrc)} CRC"
                
                Text(
                    text = priceFormatted,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = TowTruckColors.greenAccent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showInUsd = !showInUsd }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Text(request.vehicleInfo, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            Text("📍 ${request.locationName}", color = TowTruckColors.textSecondary, fontSize = 13.sp)
            if (!request.destinationName.isNull_or_blank()) {
                Text("🏁 Destino: ${request.destinationName}", color = TowTruckColors.cyanAccent, fontSize = 13.sp)
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
                    border = androidx.compose.foundation.BorderStroke(1.dp, TowTruckColors.cyanAccent),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TowTruckColors.cyanAccent)
                ) {
                    Text("🗺️ Abrir Waze", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onShareWhatsApp,
                    modifier = Modifier.weight(1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TowTruckColors.greenAccent),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TowTruckColors.greenAccent)
                ) {
                    Text("📱 WhatsApp", fontSize = 12.sp)
                }
            }

            if (isDriverView && request.status == "OPEN" && onTakeService != null) {
                Button(
                    onClick = onTakeService,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TowTruckColors.orangeAccent)
                ) {
                    Text("🙋‍♂️ TOMAR Y ACEPTAR ESTE SERVICIO", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            if (request.status == "TAKEN") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onComplete != null) {
                        Button(onClick = onComplete, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = TowTruckColors.greenAccent)) {
                            Text("✅ COMPLETAR", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (onCancel != null) {
                        Button(onClick = onCancel, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = TowTruckColors.redAccent)) {
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
