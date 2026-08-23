package com.elysium369.meet.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                    // Blocked View - Requires Mechanic Registration
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🛠️", fontSize = 64.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "MODO PROVEEDOR EXCLUSIVO",
                            color = MechanicColors.cyanAccent,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Para recibir solicitudes de servicio mecánico de otros usuarios y enviar cotizaciones, debes registrarte como mecánico o taller en MEET.",
                            color = MechanicColors.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
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

    var problemText by remember { mutableStateOf("") }
    var descriptionText by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("MEDIUM") }
    var locationName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("+506 ") }
    var isDolarCurrency by remember { mutableStateOf(false) }
    var priceOfferCrc by remember { androidx.compose.runtime.mutableFloatStateOf(25000.0f) }
    var latText by remember { mutableStateOf("9.9281") }
    var lngText by remember { mutableStateOf("-84.0907") }

    val currentGps by viewModel.currentGpsLocation.collectAsState()
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    val activeDtcs by viewModel.activeDtcs.collectAsState()
    var selectedServiceId by remember {
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
            locationName = gps.addressName
            phone = "${gps.dialingPrefix} "
        }
    }

    val myRequests = remember(allRequests, selectedVehicle) {
        val vehicleId = selectedVehicle?.id ?: ""
        allRequests.filter { it.vehicleId == vehicleId }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MechanicColors.cardBackground),
                border = BorderStroke(1.dp, MechanicColors.cyanAccent.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🚗 DETALLES DEL VEHÍCULO",
                        fontWeight = FontWeight.Bold,
                        color = MechanicColors.cyanAccent,
                        fontSize = 12.sp
                    )
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

                    OutlinedTextField(
                        value = locationName,
                        onValueChange = { locationName = it },
                        label = { Text("📍 Ubicación actual") },
                        placeholder = { Text("Ej. Frente al parque central, San José") },
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

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = latText,
                            onValueChange = { latText = it },
                            label = { Text("Latitud") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MechanicColors.cyanAccent,
                                unfocusedBorderColor = MechanicColors.borderSubtle,
                                focusedLabelColor = MechanicColors.cyanAccent
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = lngText,
                            onValueChange = { lngText = it },
                            label = { Text("Longitud") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MechanicColors.cyanAccent,
                                unfocusedBorderColor = MechanicColors.borderSubtle,
                                focusedLabelColor = MechanicColors.cyanAccent
                            ),
                            modifier = Modifier.weight(1f)
                        )
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

                    val displayPrice = com.elysium369.meet.core.services.kernel.Money.ofCrc(priceOfferCrc.toLong()).formatted()

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

                    Button(
                        onClick = {
                            val vehicle = selectedVehicle
                            if (vehicle == null) {
                                Toast.makeText(context, "⚠️ Debes seleccionar un vehículo registrado para enviar la solicitud", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            val parsedLat = latText.toDoubleOrNull() ?: 0.0
                            val parsedLng = lngText.toDoubleOrNull() ?: 0.0
                            viewModel.createServiceRequest(
                                vehicleId = vehicle.id,
                                problem = problemText.ifBlank { selectedService.name },
                                description = descriptionText,
                                location = locationName,
                                priority = priority,
                                latitude = parsedLat,
                                longitude = parsedLng,
                                phone = phone,
                                priceOffer = priceOfferCrc.toDouble(),
                                serviceId = selectedService.id,
                                serviceCategory = selectedService.category.name,
                                dtcCodes = activeDtcs
                            )
                            Toast.makeText(context, "✅ Solicitud registrada para sincronización", Toast.LENGTH_SHORT).show()
                            problemText = ""
                            descriptionText = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MechanicColors.cyanAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedVehicle != null && (problemText.isNotBlank() || selectedService.name.isNotBlank()) && phone.isNotBlank()
                    ) {
                        Text(
                            text = "🛠️ ENVIAR SOLICITUD A RED DE MECÁNICOS",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
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
    val myProfile = profiles.firstOrNull { it.providerType == "MECHANIC" || it.providerType == "WORKSHOP" }

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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MechanicColors.cardBackground),
                border = BorderStroke(1.dp, MechanicColors.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🛠️ PERFIL DE MECÁNICO / TALLER",
                        color = MechanicColors.orangeAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = mechanicName,
                        onValueChange = { mechanicName = it },
                        label = { Text("Nombre del Taller / Mecánico") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MechanicColors.orangeAccent,
                            unfocusedBorderColor = MechanicColors.borderSubtle,
                            focusedLabelColor = MechanicColors.orangeAccent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = mechanicPhone,
                        onValueChange = { mechanicPhone = it },
                        label = { Text("Teléfono de contacto") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MechanicColors.orangeAccent,
                            unfocusedBorderColor = MechanicColors.borderSubtle,
                            focusedLabelColor = MechanicColors.orangeAccent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
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
                    text = com.elysium369.meet.core.services.kernel.Money.ofCrc(request.priceOffer.toLong()).formatted(),
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
