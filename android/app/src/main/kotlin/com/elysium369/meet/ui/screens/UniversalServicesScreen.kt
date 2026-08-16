package com.elysium369.meet.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.elysium369.meet.ride.map.RideMapStateFactory
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.theme.MeetColors
import java.util.UUID

private const val UNIVERSAL_PREFIX = "universal:"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalServicesScreen(
    viewModel: ObdViewModel,
    onBack: () -> Unit,
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
        it.providerType == "SERVICE_PROVIDER" && it.isActive && it.verified
    }
    var providerMode by rememberSaveableCompat { mutableStateOf(false) }
    var query by rememberSaveableCompat { mutableStateOf("") }
    var selected by remember { mutableStateOf<UniversalServiceDefinition?>(null) }
    var showProviderRegistration by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshProviderRoles()
        viewModel.detectCurrentLocation(context)
    }

    Scaffold(
        containerColor = MeetColors.backgroundDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ELYSIUM · SERVICIOS", color = Color.White, fontWeight = FontWeight.Black)
                        Text("Físicos · digitales · híbridos", color = MeetColors.cyberCyan, fontSize = 10.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver", tint = MeetColors.cyberCyan)
                    }
                },
                actions = {
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
            if (providerMode) {
                ProviderServiceBoard(
                    viewModel = viewModel,
                    requests = allRequests.filter { it.vehicleId.startsWith(UNIVERSAL_PREFIX) },
                    providerName = myProfile?.businessName.orEmpty(),
                    providerPhone = myProfile?.phone.orEmpty(),
                    providerId = myProfile?.profileId,
                )
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("¿Qué necesitas? Plomería, diseño, tutoría…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MeetColors.cyberCyan,
                        unfocusedBorderColor = Color(0xFF6B2D91),
                    ),
                )
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            "SOLICITUD → OFERTAS → ASIGNACIÓN → EJECUCIÓN → PAGO REGISTRADO → EVIDENCIA",
                            color = Color(0xFFC85CFF),
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                        )
                    }
                    items(UniversalServiceCatalog.search(query), key = { it.id }) { service ->
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
    }

    selected?.let { service ->
        CreateUniversalRequestDialog(
            service = service,
            initialPoint = gps?.let {
                RideGeoPoint(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    accuracyMeters = it.accuracy,
                    capturedAtEpochMs = it.timestamp,
                )
            },
            onDismiss = { selected = null },
            onCreate = { title, detail, location, latitude, longitude, price, modality ->
                val metadata = buildString {
                    appendLine("[ELYSIUM_UNIVERSAL_SERVICE]")
                    appendLine("definition_id=${service.id}")
                    appendLine("domain=${service.domain}")
                    appendLine("modality=${modality.name}")
                    appendLine("risk_tier=${service.riskTier}")
                    appendLine("currency=CRC")
                    appendLine("price_minor=${(price * 100).toLong()}")
                    append("[/ELYSIUM_UNIVERSAL_SERVICE]")
                }
                viewModel.createServiceRequest(
                    vehicleId = "$UNIVERSAL_PREFIX$clientId",
                    problem = title,
                    description = detail,
                    location = location,
                    priority = "MEDIUM",
                    latitude = latitude,
                    longitude = longitude,
                    priceOffer = price,
                    serviceCategory = service.domain,
                    serviceMetadata = metadata,
                    dtcCodes = emptyList(),
                )
                Toast.makeText(context, "Solicitud publicada para recibir ofertas", Toast.LENGTH_LONG).show()
                selected = null
            },
        )
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
                providerMode = true
                showProviderRegistration = false
            },
        )
    }
}

@Composable
private fun UniversalServiceCard(service: UniversalServiceDefinition, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xCC0A1726)),
        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = .45f)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(14.dp)) {
            Text(service.icon, fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(service.name, color = Color.White, fontWeight = FontWeight.Black)
                Text(
                    "${service.domain} · ${service.modalities.joinToString { it.label }}",
                    color = MeetColors.cyberCyan,
                    fontSize = 10.sp,
                    maxLines = 2,
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
    Card(
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, if (request.status == "OPEN") MeetColors.neonGreen else MeetColors.cyberCyan),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(request.problem, color = Color.White, fontWeight = FontWeight.Black)
            Text("${request.status} · Oferta base ₡${request.priceOffer.toLong()}", color = MeetColors.cyberCyan)
            bids.forEach { bid ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(bid.shopName, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("₡${bid.price.toLong()} · garantía ${bid.warrantyDays} días", color = MeetColors.textSecondary, fontSize = 11.sp)
                    }
                    if (request.status == "OPEN") {
                        Button(onClick = { viewModel.acceptBid(request.requestId, bid.bidId, context) }) {
                            Text("ACEPTAR")
                        }
                    }
                }
            }
            Text(
                "Pago: ${request.escrowStatus ?: "NONE"}. Una oferta aceptada no se muestra como pagada hasta recibir confirmación real.",
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
private fun CreateUniversalRequestDialog(
    service: UniversalServiceDefinition,
    initialPoint: RideGeoPoint?,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, Double, Double, Double, UniversalServiceModality) -> Unit,
) {
    var title by remember(service.id) { mutableStateOf(service.name) }
    var detail by remember(service.id) { mutableStateOf("") }
    var selectedPoint by remember(service.id) { mutableStateOf(initialPoint) }
    var location by remember(service.id) {
        mutableStateOf(initialPoint?.let { "${it.latitude},${it.longitude}" }.orEmpty())
    }
    var price by remember(service.id) { mutableStateOf("") }
    var modality by remember(service.id) { mutableStateOf(service.modalities.first()) }
    var showPinPicker by remember(service.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF07131E),
        title = { Text("${service.icon} ${service.name}", color = MeetColors.cyberCyan) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Servicio") })
                OutlinedTextField(detail, { detail = it }, label = { Text("Necesidad, alcance y entregables") }, minLines = 3)
                if (modality != UniversalServiceModality.DIGITAL) {
                    OutlinedTextField(location, { location = it }, label = { Text("Ubicación / referencia") })
                    OutlinedButton(
                        onClick = { showPinPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.PinDrop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (selectedPoint == null) "MARCAR EN EL MAPA" else "AJUSTAR PIN EN EL MAPA")
                    }
                }
                OutlinedTextField(price, { price = it.filter(Char::isDigit) }, label = { Text("Oferta inicial CRC") })
                service.modalities.forEach { option ->
                    FilterChip(
                        selected = modality == option,
                        onClick = { modality = option },
                        label = { Text(option.label) },
                    )
                }
                if (service.riskTier != "STANDARD") {
                    Text("Validación reforzada requerida: ${service.riskTier}", color = MeetColors.warning, fontSize = 10.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank() && detail.isNotBlank() && (price.toDoubleOrNull() ?: 0.0) > 0,
                onClick = {
                    onCreate(
                        title,
                        detail,
                        location,
                        selectedPoint?.latitude ?: 0.0,
                        selectedPoint?.longitude ?: 0.0,
                        price.toDouble(),
                        modality,
                    )
                },
            ) { Text("PUBLICAR") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
    if (showPinPicker) {
        RidePinPickerDialog(
            targetLabel = "Ubicación del servicio",
            state = RideMapStateFactory.create(pickup = selectedPoint),
            initialPoint = selectedPoint,
            onPinChanged = { selectedPoint = it },
            onDismiss = { showPinPicker = false },
            onConfirm = {
                selectedPoint = it
                location = "${it.latitude},${it.longitude}"
                showPinPicker = false
            },
        )
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

@Composable
private fun <T> rememberSaveableCompat(calculation: () -> T): T = remember(calculation = calculation)
