package com.elysium369.meet.ui.screens.ride

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.ride.domain.RideFareMode
import com.elysium369.meet.ride.domain.RideState
import com.elysium369.meet.ride.payment.RidePaymentMethod
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * DriverAppScreen — Driver Cockpit and Dispatch Experience (Item 2).
 */
@Composable
fun DriverAppScreen(
    navController: NavController,
    viewModel: ObdViewModel,
    onBack: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var isOnline by remember { mutableStateOf(false) }
    var activeRide by remember { mutableStateOf<ActiveDriverRide?>(null) }
    var showEarnings by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showSafetyCenter by remember { mutableStateOf(false) }
    var showNavigation by remember { mutableStateOf(false) }

    val openRequests by viewModel.openRideRequests.collectAsState(initial = emptyList())
    val allRequests by viewModel.rideRequests.collectAsState(initial = emptyList())
    val currentDriverId = viewModel.driverVerification.value?.driverId ?: viewModel.currentRideActorId

    // Observe actual server-assigned ride for this driver
    val assignedRide = remember(allRequests, currentDriverId) {
        allRequests.firstOrNull {
            it.assignedDriverId == currentDriverId && it.status in setOf("ACCEPTED", "DRIVER_EN_ROUTE", "ARRIVED_PICKUP", "IN_TRIP")
        }?.let { req ->
            ActiveDriverRide(
                rideId = req.requestId,
                passenger = PassengerInfo(
                    passengerId = req.passengerId,
                    name = req.passengerName,
                    rating = null,
                    totalTrips = null,
                    photoUrl = null,
                    phone = req.passengerPhone
                ),
                pickup = RidePlaceInput("p_${req.requestId}", req.pickupAddress, req.pickupAddress, req.pickupLatitude, req.pickupLongitude),
                dropoff = RidePlaceInput("d_${req.requestId}", req.destAddress, req.destAddress, req.destLatitude, req.destLongitude),
                fare = req.priceOfferMinor,
                state = runCatching { RideState.valueOf(req.status) }.getOrDefault(RideState.ASSIGNED),
                startedAt = req.createdAt
            )
        }
    }

    val incomingRequests = remember(openRequests, isOnline) {
        if (!isOnline) emptyList()
        else openRequests.filter { it.assignedDriverId == null }.map { req ->
            IncomingRideRequest(
                rideId = req.requestId,
                passenger = PassengerInfo(
                    passengerId = req.passengerId,
                    name = req.passengerName,
                    rating = null,
                    totalTrips = null,
                    photoUrl = null,
                    phone = req.passengerPhone
                ),
                pickup = RidePlaceInput("p_${req.requestId}", req.pickupAddress, req.pickupAddress, req.pickupLatitude, req.pickupLongitude),
                dropoff = RidePlaceInput("d_${req.requestId}", req.destAddress, req.destAddress, req.destLatitude, req.destLongitude),
                fare = req.priceOfferMinor,
                distanceKm = req.estimatedDistanceKm,
                durationMin = req.estimatedDurationMin,
                fareMode = runCatching { RideFareMode.valueOf(req.fareMode) }.getOrDefault(RideFareMode.OPEN_BID),
                paymentMethod = runCatching { RidePaymentMethod.valueOf(req.paymentMethod) }.getOrDefault(RidePaymentMethod.CASH)
            )
        }
    }

    var dismissedRequestId by remember { mutableStateOf<String?>(null) }
    var pendingOfferSubmittedId by remember { mutableStateOf<String?>(null) }
    val activeRideRequest = incomingRequests.firstOrNull { it.rideId != dismissedRequestId }

    val effectiveActiveRide = assignedRide ?: activeRide

    val toggleOnline = {
        val next = !isOnline
        isOnline = next
        viewModel.setRideDriverMode(next)
    }

    val acceptRide = { request: IncomingRideRequest ->
        val driver = viewModel.driverVerification.value
        val dId = driver?.driverId ?: viewModel.currentRideActorId
        val dName = driver?.fullName ?: "Conductor MEET"
        val dPhone = driver?.phone ?: "+506 8000-0000"
        val veh = driver?.vehicleModel ?: (viewModel.selectedVehicle.value?.let { "${it.make} ${it.model}" } ?: "Vehículo Registrado")
        val gps = viewModel.currentGpsLocation.value
        viewModel.makeRideOffer(
            requestId = request.rideId,
            driverId = dId,
            driverName = dName,
            driverPhone = dPhone,
            driverRating = 0.0,
            driverTotalTrips = 0,
            vehicleDesc = veh,
            counterPrice = request.fare.toDouble(),
            currency = "CRC",
            estArrivalMin = 5,
            driverLat = gps?.latitude ?: 0.0,
            driverLng = gps?.longitude ?: 0.0,
            message = "Oferta de viaje enviada"
        )
        pendingOfferSubmittedId = request.rideId
    }

    val declineRide = { request: IncomingRideRequest ->
        dismissedRequestId = request.rideId
    }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Regresar", tint = MeetColors.textPrimary)
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = if (isOnline) MeetColors.neonGreen.copy(alpha = 0.15f) else MeetColors.cardBackground,
                    border = BorderStroke(1.dp, if (isOnline) MeetColors.neonGreen else MeetColors.borderSubtle),
                    modifier = Modifier.clickable { toggleOnline() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) MeetColors.neonGreen else MeetColors.textMuted)
                        )
                        Text(
                            text = if (isOnline) "EN LÍNEA" else "FUERA DE LÍNEA",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isOnline) MeetColors.neonGreen else MeetColors.textSecondary
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { showEarnings = true }) {
                        Icon(Icons.Default.AttachMoney, contentDescription = "Ganancias", tint = MeetColors.neonGreen)
                    }
                    IconButton(onClick = { showSafetyCenter = true }) {
                        Icon(Icons.Default.Security, contentDescription = "Seguridad", tint = MeetColors.error)
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Online/Offline Status Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    border = BorderStroke(1.dp, if (isOnline) MeetColors.neonGreen.copy(alpha = 0.4f) else MeetColors.borderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                if (isOnline) "Listo para recibir viajes" else "Estás fuera de línea",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MeetColors.textPrimary
                            )
                            Text(
                                if (isOnline) "Buscando pasajeros en tu zona..." else "Presiona conectar para iniciar tu turno",
                                style = MaterialTheme.typography.bodySmall,
                                color = MeetColors.textSecondary
                            )
                        }

                        FilledIconButton(
                            onClick = { toggleOnline() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (isOnline) MeetColors.neonGreen else MeetColors.borderSubtle
                            )
                        ) {
                            Icon(
                                Icons.Default.PowerSettingsNew,
                                contentDescription = "Conectar/Desconectar",
                                tint = if (isOnline) Color.Black else MeetColors.textSecondary
                            )
                        }
                    }
                }

                // Incoming Ride Request Card
                activeRideRequest?.let { req ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                        border = BorderStroke(2.dp, MeetColors.neonGreen)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = CircleShape, color = MeetColors.neonGreen.copy(alpha = 0.2f), modifier = Modifier.size(40.dp)) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = MeetColors.neonGreen)
                                        }
                                    }
                                    Column {
                                        Text(req.passenger.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MeetColors.textPrimary)
                                        if (req.passenger.rating != null) {
                                            val trips = req.passenger.totalTrips?.let { " ($it viajes)" } ?: ""
                                            Text("★ ${req.passenger.rating}$trips", style = MaterialTheme.typography.labelSmall, color = MeetColors.textSecondary)
                                        } else {
                                            Text("Pasajero registrado", style = MaterialTheme.typography.labelSmall, color = MeetColors.textSecondary)
                                        }
                                    }
                                }
                                Text(
                                    "₡${req.fare.toString().replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1,")}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MeetColors.neonGreen
                                )
                            }

                            HorizontalDivider(color = MeetColors.borderSubtle, thickness = 1.dp)

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("📍 Recogida: ${req.pickup.displayName}", style = MaterialTheme.typography.bodySmall, color = MeetColors.textPrimary)
                                Text("🏁 Destino: ${req.dropoff.displayName}", style = MaterialTheme.typography.bodySmall, color = MeetColors.textPrimary)
                                Text("Distancia: ${req.distanceKm} km • Tiempo estimado: ${req.durationMin} min", style = MaterialTheme.typography.labelSmall, color = MeetColors.electricBlue)
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { declineRide(req) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Rechazar", color = MeetColors.textSecondary)
                                }
                                Button(
                                    onClick = { acceptRide(req) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen, contentColor = Color.Black)
                                ) {
                                    Text("ENVIAR OFERTA", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Pending Offer Feedback
                if (pendingOfferSubmittedId != null && effectiveActiveRide == null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                        border = BorderStroke(1.dp, MeetColors.electricBlue)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Oferta Enviada", style = MaterialTheme.typography.labelMedium, color = MeetColors.electricBlue, fontWeight = FontWeight.Bold)
                            Text("Esperando que el pasajero confirme la asignación...", style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                        }
                    }
                }

                // Active Ride Controls (when assigned / in trip)
                effectiveActiveRide?.let { ride ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                        border = BorderStroke(1.5.dp, MeetColors.electricBlue)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Viaje en Curso", style = MaterialTheme.typography.labelMedium, color = MeetColors.electricBlue, fontWeight = FontWeight.Bold)
                            Text("Pasajero: ${ride.passenger.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MeetColors.textPrimary)
                            Text("Destino: ${ride.dropoff.displayName}", style = MaterialTheme.typography.bodyMedium, color = MeetColors.textSecondary)

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { showNavigation = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.electricBlue)
                                ) {
                                    Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("NAVEGAR", fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        activeRide = null
                                        pendingOfferSubmittedId = null
                                        showEarnings = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen, contentColor = Color.Black)
                                ) {
                                    Text("COMPLETAR", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Today's Earnings Snapshot
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ganancias de Hoy", style = MaterialTheme.typography.labelMedium, color = MeetColors.textSecondary)
                        Text("₡45,600", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MeetColors.neonGreen)
                        Text("8 viajes completados • 4h 12m conectado", style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                    }
                }

                Spacer(Modifier.height(40.dp))
            }

            // In-App Turn-By-Turn Navigation Overlay
            if (showNavigation) {
                DriverTurnByTurnNavigationOverlay(
                    nextManeuver = "Gire a la derecha en 150m hacia Escazú",
                    distanceMeters = 150,
                    speedKmh = 48f,
                    speedLimitKmh = 60,
                    etaMinutes = 11,
                    remainingKm = 4.2,
                    onCloseNavigation = { showNavigation = false }
                )
            }

            // Bottom Sheets
            if (showEarnings) {
                DriverEarningsBottomSheet(
                    todayEarnings = 45600L,
                    weekEarnings = 187500L,
                    monthEarnings = 723400L,
                    tripsToday = 8,
                    onDismiss = { showEarnings = false }
                )
            }

            if (showSettings) {
                DriverSettingsBottomSheet(
                    isOnline = isOnline,
                    onToggleOnline = { toggleOnline() },
                    onDismiss = { showSettings = false }
                )
            }

            if (showSafetyCenter) {
                SafetyCenterOverlay(
                    ride = null,
                    onDismiss = { showSafetyCenter = false },
                    onShareTrip = { /* Share trip */ },
                    onSOS = { /* SOS */ },
                    onGuardian = { /* Guardian */ }
                )
            }
        }
    }
}
