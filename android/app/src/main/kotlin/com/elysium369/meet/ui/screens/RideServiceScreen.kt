package com.elysium369.meet.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.elysium369.meet.data.local.entities.RideChatMessageEntity
import com.elysium369.meet.data.local.entities.RideOfferEntity
import com.elysium369.meet.data.local.entities.RideRequestEntity
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ride.map.RideGeoPoint
import com.elysium369.meet.ride.map.RideMapStateFactory
import com.elysium369.meet.ride.domain.RideVerificationPolicy
import kotlinx.coroutines.launch
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
    onNavigateBack: () -> Unit = {}
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
        Manifest.permission.RECORD_AUDIO
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
    }

    val driverMode by viewModel.rideDriverMode.collectAsState()
    val activeRide by viewModel.activeRideRequest.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "MEET Rides 🚗",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Atrás",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
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
            if (activeRide != null) {
                // Si hay un viaje activo, mostrar la pantalla de viaje activo con el chat
                ActiveRidePanel(
                    viewModel = viewModel,
                    ride = activeRide!!,
                    isDriver = driverMode,
                    onCloseRide = { viewModel.selectActiveRide(null) }
                )
            } else {
                if (driverMode) {
                    DriverDashboard(viewModel = viewModel)
                } else {
                    PassengerDashboard(viewModel = viewModel)
                }
            }
        }
    }
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

@Composable
fun PassengerDashboard(viewModel: ObdViewModel) {
    val context = LocalContext.current
    val currentGps by viewModel.currentGpsLocation.collectAsState()
    val allRides by viewModel.rideRequests.collectAsState()

    var destAddress by remember { mutableStateOf("") }
    var destLatitude by remember { mutableStateOf(0.0) }
    var destLongitude by remember { mutableStateOf(0.0) }

    var offerPrice by remember { mutableStateOf(2500.0) }
    var isUsd by remember { mutableStateOf(false) }

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
                it.status in listOf("OPEN", "ACCEPTED", "ARRIVED", "IN_PROGRESS")
        }
    }

    var showPaxVerification by remember { mutableStateOf(false) }
    var paxName by remember { mutableStateOf("") }
    var paxPhone by remember { mutableStateOf("") }
    var paxProfilePhoto by remember { mutableStateOf("") }
    var paxCedulaFront by remember { mutableStateOf("") }
    var paxSelfieWithCedula by remember { mutableStateOf("") }

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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
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

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

                    OutlinedTextField(
                        value = destAddress,
                        onValueChange = { destAddress = it },
                        label = { Text("Escribe la dirección de destino") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeetColors.cyberCyan,
                            unfocusedBorderColor = MeetColors.borderSubtle,
                            focusedLabelColor = MeetColors.cyberCyan
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Coordenadas manuales de ayuda para precisión milimétrica sin mapa
                        OutlinedTextField(
                            value = if (destLatitude == 0.0) "" else destLatitude.toString(),
                            onValueChange = { destLatitude = it.toDoubleOrNull() ?: 0.0 },
                            label = { Text("Latitud Destino") },
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
                            label = { Text("Longitud Destino") },
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
                            text = "💰 Tu Oferta (Subasta InDriver)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MeetColors.textPrimary
                        )

                        Row(
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

                    Text(
                        text = if (isUsd) "$${String.format("%.2f", offerPrice / 500.0)} USD" else "₡${String.format("%,.0f", offerPrice)} CRC",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = MeetColors.neonGreen,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = offerPrice.toFloat(),
                        onValueChange = { offerPrice = it.toDouble() },
                        valueRange = 1000f..30000f,
                        steps = 29,
                        colors = SliderDefaults.colors(
                            thumbColor = MeetColors.neonGreen,
                            activeTrackColor = MeetColors.neonGreen,
                            inactiveTrackColor = MeetColors.borderSubtle
                        )
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

                            // Sanitizar dirección: si el geocoder falló, rawAddressName llega
                            // como "Ubicación GPS (lat, lng)" — eso filtra coords exactas al
                            // backend. Si detectamos ese patrón, usamos genérico.
                            // (RISK-3 GPS leak: lat/lon no debe ir en strings user-facing).
                            val safePickupAddress = sanitizeGpsAddress(gps.addressName)

                            // Calcular distancia estimada simple (Haversine).
                            // Si el usuario no ingreso destino, distancia = 0 (no usamos
                            // el truco "+0.05" que filtra coords raw).
                            val distance = if (destLatitude != 0.0 && destLongitude != 0.0) {
                                calculateDistance(
                                    gps.latitude, gps.longitude,
                                    destLatitude, destLongitude
                                )
                            } else 0.0

                            viewModel.createRideRequest(
                                passengerId = verifiedPassenger.passengerId,
                                passengerName = verifiedPassenger.fullName,
                                passengerPhone = verifiedPassenger.phone,
                                pickupLat = gps.latitude,
                                pickupLng = gps.longitude,
                                pickupAddr = safePickupAddress,
                                pickupAcc = gps.accuracy,
                                destLat = destLatitude,
                                destLng = destLongitude,
                                destAddr = destAddress,
                                priceOffer = if (isUsd) offerPrice / 500.0 else offerPrice,
                                currency = if (isUsd) "USD" else "CRC",
                                estDistance = distance,
                                estDuration = (distance * 2.5).toInt().coerceAtLeast(3)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Text(
                            text = "🚀 SOLICITAR VIAJE AHORA",
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
    // Passenger verification dialog
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
fun DriverDashboard(viewModel: ObdViewModel) {
    val openRides by viewModel.openRideRequests.collectAsState()
    val currentGps by viewModel.currentGpsLocation.collectAsState()
    val allRides by viewModel.rideRequests.collectAsState()
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    val activeDtcs by viewModel.activeDtcEvents.collectAsState()
    val historicalDtcs by viewModel.historicalDtcEvents.collectAsState()
    val sharingSelections by viewModel.rideSharingSelections.collectAsState()

    val driverVer by viewModel.driverVerification.collectAsState()
    val myDriverId = driverVer?.driverId

    val activeRideForDriver = remember(allRides, myDriverId) {
        myDriverId?.let { driverId ->
            allRides.firstOrNull {
                it.assignedDriverId == driverId &&
                    it.status in listOf("ACCEPTED", "ARRIVED", "IN_PROGRESS")
            }
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
                    }
                }
            }
        } else {
            item {
                RideWalletStatusCard()
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

            if (openRides.isEmpty()) {
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
                items(openRides) { request ->
                    DriverRideItem(
                        ride = request,
                        currentGps = currentGps,
                        onClick = { viewModel.selectActiveRide(request) },
                        onOffer = {
                            viewModel.selectActiveRide(request)
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
                    Text("TARIFA PROPUESTA", color = MeetColors.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${ride.priceOffer.toInt()} ${ride.currency}",
                        fontWeight = FontWeight.Black,
                        color = MeetColors.neonGreen,
                        fontSize = 20.sp
                    )
                }

                Button(
                    onClick = onOffer,
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("VER Y OFERTAR 🚀", color = MeetColors.backgroundDark, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
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
    val offers by viewModel.rideOffers.collectAsState()
    val chatMessages by viewModel.rideChatMessages.collectAsState()
    val isRecording by viewModel.isRecordingAudio.collectAsState()
    val playingPath by viewModel.isPlayingAudio.collectAsState()
    val presetMessages by viewModel.driverPresetMessages.collectAsState()
    val currentGps by viewModel.currentGpsLocation.collectAsState()
    val sharingSelections by viewModel.rideSharingSelections.collectAsState()

    var chatInputText by remember { mutableStateOf("") }
    var showRatingDialog by remember { mutableStateOf(false) }
    var showCancellationDialog by remember { mutableStateOf(false) }

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
    val mapState = remember(
        isDriver,
        pickupPoint,
        destinationPoint,
        localPoint,
        acceptedDriverPoint,
    ) {
        RideMapStateFactory.create(
            passengerGps = if (isDriver) null else localPoint,
            pickup = pickupPoint,
            destination = destinationPoint,
            driverGps = if (isDriver) localPoint else acceptedDriverPoint,
        )
    }

    if (showCancellationDialog) {
        RideCancellationDialog(
            actorLabel = if (isDriver) "Conductor" else "Pasajero",
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeetColors.backgroundDark)
    ) {
        // Ride Status Header
        val statusColor = when (ride.status) {
            "OPEN" -> MeetColors.warning
            "ACCEPTED" -> MeetColors.cyberCyan
            "ARRIVED" -> MeetColors.electricBlue
            "IN_PROGRESS" -> MeetColors.neonGreen
            "COMPLETED" -> MeetColors.neonGreen
            else -> MeetColors.error
        }
        val statusLabel = when (ride.status) {
            "OPEN" -> "Buscando Chofer ⏳"
            "ACCEPTED" -> "Chofer en Camino 🚕"
            "ARRIVED" -> "Chofer en el Punto 📍"
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
                                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.electricBlue),
                                    modifier = Modifier.weight(1.2f)
                                ) {
                                    Text("Ya Llegué 🚕", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                    onClick = { viewModel.updateRideStatus(ride.requestId, "IN_PROGRESS") },
                                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                                    modifier = Modifier.weight(1.2f)
                                ) {
                                    Text("Iniciar Viaje 🏁", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { showCancellationDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                                    modifier = Modifier.weight(0.9f)
                                ) {
                                    Text("Cancelar ❌", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            "IN_PROGRESS" -> {
                                Button(
                                    onClick = {
                                        viewModel.updateRideStatus(ride.requestId, "COMPLETED")
                                        showRatingDialog = true
                                    },
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

                    // In-app phone call (Intent)
                    val phoneToCall = if (isDriver) ride.passengerPhone else ride.assignedDriverPhone
                    if (!phoneToCall.isNullOrBlank()) {
                        IconButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneToCall"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No se pudo realizar la llamada", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MeetColors.cardBackground)
                        ) {
                            Icon(imageVector = Icons.Default.Phone, contentDescription = "Llamar", tint = MeetColors.cyberCyan)
                        }
                    }

                    // Waze link shortcut for driver
                    if (isDriver) {
                        IconButton(
                            onClick = { viewModel.openWaze(context, ride.pickupLatitude, ride.pickupLongitude) },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MeetColors.cardBackground)
                        ) {
                            Icon(imageVector = Icons.Default.LocationOn, contentDescription = "Waze", tint = MeetColors.neonGreen)
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
            border = BorderStroke(1.dp, MeetColors.borderSubtle),
            shape = RoundedCornerShape(14.dp),
        ) {
            RideMapPanel(
                state = mapState,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = if (isDriver && mapState.marker(com.elysium369.meet.ride.map.RideMarkerRole.PASSENGER_GPS) == null) {
                "GPS exacto del pasajero: esperando sincronización autenticada."
            } else {
                "U: pasajero · R: recogida · D: destino · C: conductor"
            },
            color = MeetColors.textMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
        )

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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = ride.passengerName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Teléfono: ${ride.passengerPhone}",
                                    color = MeetColors.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            IconButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${ride.passengerPhone}"))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error al llamar", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MeetColors.cyberCyan.copy(alpha = 0.15f))
                            ) {
                                Icon(Icons.Default.Phone, null, tint = MeetColors.cyberCyan)
                            }
                        }
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

                            val phone = ride.assignedDriverPhone
                            if (!phone.isNullOrBlank()) {
                                IconButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error al llamar", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(MeetColors.cyberCyan.copy(alpha = 0.15f))
                                ) {
                                    Icon(Icons.Default.Phone, null, tint = MeetColors.cyberCyan)
                                }
                            }
                        }
                    }
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
                    .weight(1f)
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
                                                        imageVector = if (isPlaying) Icons.Default.PlayArrow else Icons.Default.PlayArrow,
                                                        contentDescription = null,
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
                                        }
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
                            // Hold-to-record voice message button
                            IconButton(
                                onClick = {
                                    if (isRecording) {
                                        viewModel.stopAndSendAudioRecording(ride.requestId, myId, myName, myRole)
                                    } else {
                                        viewModel.startAudioRecording(context)
                                    }
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isRecording) MeetColors.error else MeetColors.cardBackground)
                            ) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.PlayArrow else Icons.Default.PlayArrow,
                                    contentDescription = "Grabar audio",
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
        var ratingStars by remember { mutableStateOf(5.0) }
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

                // Price Stepper (InDriver-style interactive)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val step = if (ride.currency == "USD") 1.0 else 500.0
                            if (ride.priceOffer > step) {
                                viewModel.updateRidePrice(ride.requestId, ride.priceOffer - step)
                            }
                        },
                        modifier = Modifier.background(MeetColors.borderSubtle, CircleShape)
                    ) {
                        Text("-", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = if (ride.currency == "USD") "$${ride.priceOffer.toInt()}" else "₡${String.format("%,.0f", ride.priceOffer)} CRC",
                        color = MeetColors.neonGreen,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black
                    )

                    IconButton(
                        onClick = {
                            val step = if (ride.currency == "USD") 1.0 else 500.0
                            viewModel.updateRidePrice(ride.requestId, ride.priceOffer + step)
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
                    listOf(500.0, 1000.0, 2000.0).forEach { amount ->
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
                    onClick = { viewModel.updateRideStatus(ride.requestId, "CANCELLED") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350), contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("❌ CANCELAR SOLICITUD DE VIAJE", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
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
        var counterPrice by remember { mutableStateOf(ride.priceOffer) }
        var selectedEta by remember { mutableStateOf(10) }
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
