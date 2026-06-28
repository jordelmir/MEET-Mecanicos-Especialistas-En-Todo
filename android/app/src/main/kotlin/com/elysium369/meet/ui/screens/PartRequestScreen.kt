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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
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
import com.elysium369.meet.data.local.entities.PartRequestEntity
import com.elysium369.meet.data.local.entities.PartOfferEntity
import com.elysium369.meet.data.local.entities.RatingEntity
import com.elysium369.meet.ui.ObdViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private object PartColors {
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
fun PartRequestScreen(
    viewModel: ObdViewModel,
    prefilledVehicleInfo: String? = null,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var isStoreMode by remember { mutableStateOf(false) }
    val allRequests by viewModel.partRequests.collectAsState()
    val openRequests by viewModel.openPartRequests.collectAsState()
    val isStoreRegistered by viewModel.isPartsStore.collectAsState()
    val vehicles by viewModel.vehicles.collectAsState()
    var showRegistrationScreen by remember { mutableStateOf(false) }

    var showRatingDialog by remember { mutableStateOf(false) }
    var ratingTargetId by remember { mutableStateOf("") }
    var ratingTargetType by remember { mutableStateOf("STORE") }

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
                        text = if (isStoreMode) "🧩 MODO REPUESTERA" else "📦 PEDIR REPUESTOS",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = PartColors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Regresar", tint = PartColors.cyanAccent)
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = if (isStoreMode) "Repuestera" else "Cliente",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isStoreMode) PartColors.orangeAccent else PartColors.cyanAccent
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = isStoreMode,
                            onCheckedChange = { isStoreMode = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PartColors.orangeAccent,
                                checkedTrackColor = PartColors.orangeAccent.copy(alpha = 0.3f),
                                uncheckedThumbColor = PartColors.cyanAccent,
                                uncheckedTrackColor = PartColors.cyanAccent.copy(alpha = 0.3f)
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PartColors.darkBackground)
            )
        },
        containerColor = PartColors.darkBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isStoreMode) {
                if (isStoreRegistered) {
                    StoreWorkspaceView(
                        openRequests = openRequests,
                        viewModel = viewModel,
                        context = context,
                        onCompleteService = { requestId, targetId ->
                            // Parts delivered
                            ratingTargetId = targetId
                            ratingTargetType = "CLIENT"
                            showRatingDialog = true
                        }
                    )
                } else {
                    // Blocked View - Requires Parts Store Registration
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🧩", fontSize = 64.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "MODO REPUESTERA EXCLUSIVO",
                            color = PartColors.greenAccent,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Para recibir solicitudes de repuestos de otros usuarios y enviar cotizaciones de autopartes, debes registrarte como repuestera verificado en MEET.",
                            color = PartColors.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showRegistrationScreen = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PartColors.greenAccent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("REGISTRAR MI TIENDA DE REPUESTOS", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Client Workspace - filter to only show their own requests
                val userVehicleIds = vehicles.map { it.id }
                val clientRequests = allRequests.filter { it.vehicleId in userVehicleIds }
                
                ClientWorkspaceView(
                    viewModel = viewModel,
                    allRequests = clientRequests,
                    prefilledVehicleInfo = prefilledVehicleInfo,
                    context = context,
                    onCompleteService = { requestId, storeId ->
                        ratingTargetId = storeId ?: "store"
                        ratingTargetType = "STORE"
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
                            sourceName = if (isStoreMode) "Repuestera" else "Cliente",
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
    allRequests: List<PartRequestEntity>,
    prefilledVehicleInfo: String?,
    context: Context,
    onCompleteService: (String, String?) -> Unit
) {
    val autoVehicleInfo = remember { viewModel.buildVehicleInfoForRequest() }
    val vehicleInfoToUse = prefilledVehicleInfo ?: autoVehicleInfo

    var partName by remember { mutableStateOf("") }
    var partNumber by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf(1) }
    var partPosition by remember { mutableStateOf("N/A") }
    var oemPreference by remember { mutableStateOf("ANY") }
    var locationName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("+506 ") }
    var customerNotes by remember { mutableStateOf("") }
    var latText by remember { mutableStateOf("9.9281") }
    var lngText by remember { mutableStateOf("-84.0907") }

    val currentGps by viewModel.currentGpsLocation.collectAsState()
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()

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
                colors = CardDefaults.cardColors(containerColor = PartColors.cardBackground),
                border = BorderStroke(1.dp, PartColors.cyanAccent.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🚗 DETALLES DEL VEHÍCULO",
                        fontWeight = FontWeight.Bold,
                        color = PartColors.cyanAccent,
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
            Card(
                colors = CardDefaults.cardColors(containerColor = PartColors.cardBackground),
                border = BorderStroke(1.dp, PartColors.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🧩 PEDIR REPUESTO A LA RED (INDRIVER INVERSO)",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = partName,
                        onValueChange = { partName = it },
                        label = { Text("Nombre de la pieza requerida") },
                        placeholder = { Text("Ej. Pastillas de freno delanteras / Sensor MAP") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PartColors.cyanAccent,
                            unfocusedBorderColor = PartColors.borderSubtle,
                            focusedLabelColor = PartColors.cyanAccent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = partNumber,
                        onValueChange = { partNumber = it },
                        label = { Text("Número de parte (Opcional)") },
                        placeholder = { Text("Ej. 37880-PLC-004") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PartColors.cyanAccent,
                            unfocusedBorderColor = PartColors.borderSubtle,
                            focusedLabelColor = PartColors.cyanAccent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Quantity stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Unidades requeridas:",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            IconButton(
                                onClick = { if (quantity > 1) quantity-- },
                                modifier = Modifier.background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Menos", tint = Color.White)
                            }
                            Text(
                                text = quantity.toString(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            IconButton(
                                onClick = { if (quantity < 10) quantity++ },
                                modifier = Modifier.background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Más", tint = Color.White)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Part position grid
                    Text(
                        text = "Posición de la pieza en el vehículo:",
                        color = PartColors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val positions = listOf(
                            listOf("DELANTERA_DERECHA" to "Delantera Der.", "DELANTERA_IZQUIERDA" to "Delantera Izq."),
                            listOf("TRASERA_DERECHA" to "Trasera Der.", "TRASERA_IZQUIERDA" to "Trasera Izq."),
                            listOf("CENTRAL" to "Central", "N/A" to "N/A / Motor / Otro")
                        )
                        positions.forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                row.forEach { (value, label) ->
                                    FilterChip(
                                        selected = partPosition == value,
                                        onClick = { partPosition = value },
                                        label = { Text(label, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = PartColors.cyanAccent,
                                            selectedLabelColor = Color.Black,
                                            labelColor = Color.White
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // OEM Preference
                    Text(
                        text = "Preferencia de fabricante:",
                        color = PartColors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        listOf("ANY" to "Cualquiera", "OEM" to "Original (OEM)", "AFTERMARKET" to "Genérico").forEach { (value, label) ->
                            FilterChip(
                                selected = oemPreference == value,
                                onClick = { oemPreference = value },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PartColors.cyanAccent,
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
                        label = { Text("📍 Lugar de entrega / Taller / Casa") },
                        placeholder = { Text("Ej. Taller El Centauro, Escazú") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PartColors.cyanAccent,
                            unfocusedBorderColor = PartColors.borderSubtle,
                            focusedLabelColor = PartColors.cyanAccent
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
                                focusedBorderColor = PartColors.cyanAccent,
                                unfocusedBorderColor = PartColors.borderSubtle,
                                focusedLabelColor = PartColors.cyanAccent
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
                                focusedBorderColor = PartColors.cyanAccent,
                                unfocusedBorderColor = PartColors.borderSubtle,
                                focusedLabelColor = PartColors.cyanAccent
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("📱 Teléfono / WhatsApp") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PartColors.cyanAccent,
                            unfocusedBorderColor = PartColors.borderSubtle,
                            focusedLabelColor = PartColors.cyanAccent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = customerNotes,
                        onValueChange = { customerNotes = it },
                        label = { Text("Notas de compatibilidad / Detalles") },
                        placeholder = { Text("Ej. Motor 2.0L automático de 4 puertas") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PartColors.cyanAccent,
                            unfocusedBorderColor = PartColors.borderSubtle,
                            focusedLabelColor = PartColors.cyanAccent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val parsedLat = latText.toDoubleOrNull() ?: 0.0
                            val parsedLng = lngText.toDoubleOrNull() ?: 0.0
                            val vehicleId = selectedVehicle?.id ?: "demo_vehicle"
                            
                            viewModel.createPartRequest(
                                serviceRequestId = null,
                                vehicleId = vehicleId,
                                dtcCode = viewModel.activeDtcs.value.firstOrNull(),
                                partName = partName,
                                partNumber = partNumber.takeIf { it.isNotBlank() },
                                quantity = quantity,
                                oemPreference = oemPreference,
                                deliveryLocation = locationName,
                                urgencyMinutes = 60, // Default hidden ETA
                                customerNotes = "$customerNotes [Posición: $partPosition] [Tel: $phone]"
                            )

                            // Note: Locally insert custom fields into the latest request via DAO if needed,
                            // or append to customerNotes as we did above. Let's make sure it's updated.
                            Toast.makeText(context, "✅ Solicitud de repuesto publicada en la red", Toast.LENGTH_SHORT).show()
                            partName = ""
                            partNumber = ""
                            customerNotes = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PartColors.cyanAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = partName.isNotBlank() && phone.isNotBlank()
                    ) {
                        Text(
                            text = "🧩 ENVIAR SOLICITUD A RED DE REPUESTERAS",
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
                    text = "MIS SOLICITUDES DE REPUESTOS",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(myRequests) { req ->
                ClientRequestCard(
                    request = req,
                    viewModel = viewModel,
                    context = context,
                    onComplete = { storeId -> onCompleteService(req.requestId, storeId) }
                )
            }
        }
    }
}

@Composable
private fun ClientRequestCard(
    request: PartRequestEntity,
    viewModel: ObdViewModel,
    context: Context,
    onComplete: (String?) -> Unit
) {
    val offersFlow = remember(request.requestId) { viewModel.getPartOffersForRequest(request.requestId) }
    val offers by offersFlow.collectAsState(initial = emptyList())

    Card(
        colors = CardDefaults.cardColors(containerColor = PartColors.cardBackground),
        border = BorderStroke(1.dp, if (request.status == "OPEN") PartColors.orangeAccent.copy(alpha = 0.3f) else PartColors.cyanAccent.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = if (request.status == "OPEN") PartColors.orangeAccent.copy(alpha = 0.15f) else PartColors.cyanAccent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (request.status == "OPEN") "BUSCANDO OFERTAS" else "COMPRA REALIZADA",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (request.status == "OPEN") PartColors.orangeAccent else PartColors.cyanAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = "${request.quantity} unidades",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = request.partName,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 16.sp
            )

            val cleanNotes = request.customerNotes.substringBefore(" [Posición:")
            val positionText = request.customerNotes.substringAfter("[Posición: ").substringBefore("]")

            if (positionText.isNotBlank() && positionText != request.customerNotes) {
                Text(
                    text = "Posición: $positionText",
                    color = PartColors.cyanAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (cleanNotes.isNotBlank()) {
                Text(
                    text = "Detalles: $cleanNotes",
                    color = PartColors.textSecondary,
                    fontSize = 13.sp
                )
            }
            if (request.partNumber != null) {
                Text(
                    text = "N/Parte: ${request.partNumber}",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "📥 Ofertas recibidas de repuesteras (${offers.size}):",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 13.sp
            )

            if (offers.isEmpty()) {
                Text(
                    text = "Esperando ofertas de repuestos locales...",
                    color = PartColors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                offers.forEach { offer ->
                    val isSelected = request.acceptedOfferId == offer.offerId
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, if (isSelected) PartColors.greenAccent else PartColors.borderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = offer.storeName,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = String.format("₡%,.0f CRC", offer.price),
                                    fontWeight = FontWeight.Black,
                                    color = PartColors.greenAccent,
                                    fontSize = 14.sp
                                )
                            }
                            Text(
                                text = "Marca: ${offer.brand} | Condición: ${offer.condition}",
                                color = PartColors.textSecondary,
                                fontSize = 12.sp
                            )
                            if (offer.message.isNotBlank()) {
                                Text(
                                    text = "\"${offer.message}\"",
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            if (request.status == "OPEN") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.acceptPartOffer(request.requestId, offer.offerId, context)
                                        onComplete(offer.storeId)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PartColors.cyanAccent),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("🤝 ELEGIR Y COMPRAR ESTE REPUESTO", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            } else if (isSelected) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "✅ OFERTA ELEGIDA Y COMPRADA",
                                    color = PartColors.greenAccent,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreWorkspaceView(
    openRequests: List<PartRequestEntity>,
    viewModel: ObdViewModel,
    context: Context,
    onCompleteService: (String, String) -> Unit
) {
    var storeName by remember { mutableStateOf("Repuestos El Atlántico") }
    var storePhone by remember { mutableStateOf("+506 7777 7777") }

    val storeId = "store_101"

    var offeringRequestId by remember { mutableStateOf<String?>(null) }
    var brand by remember { mutableStateOf("") }
    var partNumberOffer by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf("NEW") }
    var priceOfferCrc by remember { androidx.compose.runtime.mutableFloatStateOf(15000.0f) }
    var warrantyDays by remember { androidx.compose.runtime.mutableFloatStateOf(30.0f) }
    var offerMessage by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PartColors.cardBackground),
                border = BorderStroke(1.dp, PartColors.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🧩 CONFIGURAR MI REPUESTERA",
                        color = PartColors.orangeAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = storeName,
                        onValueChange = { storeName = it },
                        label = { Text("Nombre de la Distribuidora / Repuestera") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PartColors.orangeAccent,
                            unfocusedBorderColor = PartColors.borderSubtle,
                            focusedLabelColor = PartColors.orangeAccent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = storePhone,
                        onValueChange = { storePhone = it },
                        label = { Text("Teléfono / WhatsApp") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PartColors.orangeAccent,
                            unfocusedBorderColor = PartColors.borderSubtle,
                            focusedLabelColor = PartColors.orangeAccent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            Text(
                text = "📥 PEDIDOS DE REPUESTOS DISPONIBLES EN COSTA RICA",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 14.sp
            )
        }

        if (openRequests.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PartColors.cardBackground),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No hay solicitudes de repuestos abiertas actualmente.",
                        color = PartColors.textSecondary,
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
                val isOffering = offeringRequestId == req.requestId

                val cleanNotes = req.customerNotes.substringBefore(" [Posición:")
                val positionText = req.customerNotes.substringAfter("[Posición: ").substringBefore("]")

                Card(
                    colors = CardDefaults.cardColors(containerColor = PartColors.cardBackground),
                    border = BorderStroke(1.dp, if (isOffering) PartColors.orangeAccent else PartColors.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = req.partName,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "${req.quantity} piezas",
                                color = PartColors.orangeAccent,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                        }

                        if (positionText.isNotBlank() && positionText != req.customerNotes) {
                            Text(
                                text = "Lado/Posición: $positionText",
                                color = PartColors.cyanAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (cleanNotes.isNotBlank()) {
                            Text(
                                text = "Detalles: $cleanNotes",
                                color = PartColors.textSecondary,
                                fontSize = 13.sp
                            )
                        }
                        if (req.partNumber != null) {
                            Text(
                                text = "Código/N/Parte: ${req.partNumber}",
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = "Compatibilidad OEM: ${req.oemPreference}",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { viewModel.openWaze(context, req.latitude, req.longitude) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🌐 Abrir Waze", color = Color.White, fontSize = 10.sp)
                            }
                            Button(
                                onClick = { viewModel.shareLocationViaWhatsApp(context, req.latitude, req.longitude, req.partName) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("📱 WhatsApp", color = Color.White, fontSize = 10.sp)
                            }
                        }

                        if (!isOffering) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    offeringRequestId = req.requestId
                                    brand = ""
                                    partNumberOffer = req.partNumber ?: ""
                                    condition = "NEW"
                                    priceOfferCrc = 15000.0f
                                    warrantyDays = 30f
                                    offerMessage = ""
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PartColors.orangeAccent),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("🙋‍♂️ OFERTA COMO REPUESTERA (INDRIVER)", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(color = PartColors.orangeAccent.copy(alpha = 0.4f))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "💵 COTIZACIÓN DE REPUESTO",
                                fontWeight = FontWeight.Bold,
                                color = PartColors.orangeAccent,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = brand,
                                onValueChange = { brand = it },
                                label = { Text("Marca del repuesto ofrecido") },
                                placeholder = { Text("Ej. Denso / Bosch / OEM Honda") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = PartColors.orangeAccent,
                                    unfocusedBorderColor = PartColors.borderSubtle
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = partNumberOffer,
                                onValueChange = { partNumberOffer = it },
                                label = { Text("Número de parte ofrecido") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = PartColors.orangeAccent,
                                    unfocusedBorderColor = PartColors.borderSubtle
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Condición:",
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("NEW" to "Nuevo", "OEM" to "Original Usado", "REMAN" to "Reconstruido").forEach { (value, label) ->
                                    FilterChip(
                                        selected = condition == value,
                                        onClick = { condition = value },
                                        label = { Text(label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = PartColors.orangeAccent,
                                            selectedLabelColor = Color.White,
                                            labelColor = Color.LightGray
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = String.format("Precio: ₡%,.0f CRC", priceOfferCrc),
                                fontWeight = FontWeight.Black,
                                color = PartColors.greenAccent,
                                fontSize = 16.sp
                            )
                            Slider(
                                value = priceOfferCrc,
                                onValueChange = { priceOfferCrc = it },
                                valueRange = 1000f..150000f,
                                colors = SliderDefaults.colors(
                                    thumbColor = PartColors.orangeAccent,
                                    activeTrackColor = PartColors.orangeAccent
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Garantía: ${warrantyDays.toInt()} días",
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                            Slider(
                                value = warrantyDays,
                                onValueChange = { warrantyDays = it },
                                valueRange = 0f..360f,
                                steps = 11,
                                colors = SliderDefaults.colors(
                                    thumbColor = PartColors.orangeAccent,
                                    activeTrackColor = PartColors.orangeAccent
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = offerMessage,
                                onValueChange = { offerMessage = it },
                                label = { Text("Comentarios o detalles de entrega") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = PartColors.orangeAccent,
                                    unfocusedBorderColor = PartColors.borderSubtle
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { offeringRequestId = null },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cancelar", color = Color.White)
                                }
                                Button(
                                    onClick = {
                                        viewModel.placePartOffer(
                                            partRequestId = req.requestId,
                                            storeName = storeName,
                                            brand = brand,
                                            partNumber = partNumberOffer,
                                            condition = condition,
                                            price = priceOfferCrc.toDouble(),
                                            deliveryFee = 0.0,
                                            etaMinutes = 60,
                                            warrantyDays = warrantyDays.toInt(),
                                            message = offerMessage
                                        )
                                        Toast.makeText(context, "✅ Oferta enviada al cliente", Toast.LENGTH_SHORT).show()
                                        offeringRequestId = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PartColors.orangeAccent),
                                    modifier = Modifier.weight(1f),
                                    enabled = brand.isNotBlank()
                                ) {
                                    Text("Enviar", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
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
            colors = CardDefaults.cardColors(containerColor = PartColors.cardBackground),
            border = BorderStroke(1.dp, PartColors.cyanAccent.copy(alpha = 0.5f)),
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
                    text = "⭐ CALIFICAR PIEZA / SERVICIO",
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "¿Cómo calificarías este repuesto y entrega?",
                    color = PartColors.textSecondary,
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
                            tint = if (isFilled) PartColors.cyanAccent else Color.Gray,
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
                    placeholder = { Text("Ej. Excelente calidad y rápido.") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = PartColors.cyanAccent,
                        unfocusedBorderColor = PartColors.borderSubtle
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
                        colors = ButtonDefaults.buttonColors(containerColor = PartColors.cyanAccent),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Enviar", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
