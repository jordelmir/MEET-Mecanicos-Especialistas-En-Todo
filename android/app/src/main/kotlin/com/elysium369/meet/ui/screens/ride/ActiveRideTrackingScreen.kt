package com.elysium369.meet.ui.screens.ride

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.agent.laya.ActionType
import com.elysium369.meet.core.agent.laya.RideAssistantContext
import com.elysium369.meet.core.geo.CommonMapState
import com.elysium369.meet.core.geo.GeoMarker
import com.elysium369.meet.core.geo.GeoMarkerRole
import com.elysium369.meet.core.geo.GeoPoint
import com.elysium369.meet.core.geo.GeoRoute
import com.elysium369.meet.core.geo.runtime.CommonMapPanel
import com.elysium369.meet.ride.domain.RideState
import com.elysium369.meet.ride.payment.RidePaymentMethod
import com.elysium369.meet.ui.agent.laya.EvairAssistantSheet
import com.elysium369.meet.ui.theme.MeetColors

/**
 * Tracking projection. Live markers require recent source evidence; commands stay externally owned.
 */
@Composable
fun ActiveRideTrackingScreen(
    ride: ActiveRideViewState,
    onCancelRide: () -> Unit,
    notice: String? = null,
    onBack: () -> Unit,
    onCallDriver: (() -> Unit)? = null,
    onMessageDriver: (() -> Unit)? = null,
    onPay: (() -> Unit)? = null,
    onRate: (() -> Unit)? = null,
    onGeneratePin: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var showSafetyCenter by remember { mutableStateOf(false) }
    var showEvairAssistant by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var now by remember(ride.rideId) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ride.rideId) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(5_000L)
        }
    }
    val trackingTruth = ride.driverLocation.trackingFreshness(now)
    val actions = com.elysium369.meet.ride.domain.RideTrackingTruthPolicy.actions(
        ride.state, ride.paymentSettled, onPay != null, onRate != null
    )

    val mapState = remember(ride, trackingTruth) {
        val markers = mutableListOf<GeoMarker>()
        markers.add(
            GeoMarker(
                id = "pax_pickup",
                role = GeoMarkerRole.USER_LOCATION,
                point = GeoPoint(ride.pickup.latitude, ride.pickup.longitude),
                label = ride.pickup.displayName,
                isHighlighted = true
            )
        )
        markers.add(
            GeoMarker(
                id = "dest_dropoff",
                role = GeoMarkerRole.DESTINATION,
                point = GeoPoint(ride.dropoff.latitude, ride.dropoff.longitude),
                label = ride.dropoff.displayName
            )
        )
        val driverPt = when {
            ride.driverLocation != null -> GeoPoint(ride.driverLocation.latitude, ride.driverLocation.longitude)
            ride.state == RideState.ARRIVED -> GeoPoint(ride.pickup.latitude, ride.pickup.longitude)
            else -> null
        }
        driverPt?.let { pt ->
            markers.add(
                GeoMarker(
                    id = "drv_loc",
                    role = GeoMarkerRole.PROVIDER_LIVE,
                    point = pt,
                    label = ride.driver?.name ?: "Conductor",
                    isHighlighted = true
                )
            )
        }
        // Do not render synthetic 2-point straight lines as navigation routes (Charter Rule: route truth)
        CommonMapState(markers = markers, routes = emptyList())
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
                    Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = MeetColors.textPrimary)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val statusHeader = when {
                        ride.state == RideState.ARRIVED -> "Conductor en el sitio"
                        trackingTruth == com.elysium369.meet.ride.domain.TrackingFreshness.LIVE -> "Ubicación en vivo"
                        trackingTruth == com.elysium369.meet.ride.domain.TrackingFreshness.RECENT -> "Ubicación reciente"
                        ride.driverLocation != null -> "Ubicación en vivo"
                        else -> "Ubicación GPS activa"
                    }
                    Text(
                        statusHeader,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MeetColors.textPrimary
                    )
                    Text(
                        getRideStatusLabel(ride.state),
                        style = MaterialTheme.typography.labelSmall,
                        color = MeetColors.neonGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(onClick = { showSafetyCenter = true }) {
                    Icon(Icons.Default.Security, contentDescription = "Seguridad", tint = MeetColors.error)
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Real Authoritative Map Panel (fail-honest; MapLibre CommonMapPanel)
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                CommonMapPanel(
                    state = mapState,
                    modifier = Modifier.fillMaxSize(),
                    recenterAlignment = Alignment.CenterEnd,
                    recenterPadding = PaddingValues(end = 16.dp),
                    userLocation = ride.passengerLocation?.let {
                        GeoPoint(it.latitude, it.longitude, it.accuracy, it.timestamp)
                    } ?: GeoPoint(ride.pickup.latitude, ride.pickup.longitude),
                )
            }

            // Bottom Floating Controls & Ride Info Sheet
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                    border = BorderStroke(1.dp, MeetColors.borderSubtle)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Driver summary row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MeetColors.neonGreen.copy(alpha = 0.15f),
                                border = BorderStroke(1.5.dp, MeetColors.neonGreen),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        ride.driver?.name?.take(1)?.uppercase() ?: "?",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MeetColors.neonGreen
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    ride.driver?.name ?: "Buscando conductor...",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MeetColors.textPrimary
                                )
                                if (ride.driver != null) {
                                    val vehicleDesc = listOfNotNull(ride.driver.vehicle, ride.driver.plate).joinToString(" • ")
                                    if (vehicleDesc.isNotBlank()) {
                                        Text(vehicleDesc, style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                                    }
                                    if (ride.driver.rating != null) {
                                        val tripsText = ride.driver.totalTrips?.let { " ($it viajes)" } ?: ""
                                        Text("★ ${ride.driver.rating}$tripsText", style = MaterialTheme.typography.labelSmall, color = MeetColors.neonGreen)
                                    }
                                } else {
                                    Text("Asignación en curso en la red", style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                                }
                            }

                            if (ride.driver != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedIconButton(
                                        onClick = { onCallDriver?.invoke() },
                                        enabled = onCallDriver != null,
                                        border = BorderStroke(1.dp, MeetColors.neonGreen)
                                    ) {
                                        Icon(Icons.Default.Phone, contentDescription = "Llamar", tint = MeetColors.neonGreen)
                                    }
                                    OutlinedIconButton(
                                        onClick = { onMessageDriver?.invoke() },
                                        enabled = onMessageDriver != null,
                                        border = BorderStroke(1.dp, MeetColors.electricBlue)
                                    ) {
                                        Icon(Icons.Default.Chat, contentDescription = "Chat", tint = MeetColors.electricBlue)
                                    }
                                }
                            }
                        }

                        // ETA & Fare Pills
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val etaText = when {
                                ride.state == RideState.ARRIVED -> "¡En el punto!"
                                ride.state in listOf(RideState.PASSENGER_ONBOARD, RideState.IN_PROGRESS) -> {
                                    val duration = ride.fareQuote.estimatedDurationMin.takeIf { it > 0 } ?: 10
                                    "~$duration min al destino"
                                }
                                ride.driver?.etaMinutes != null -> "~${ride.driver.etaMinutes} min"
                                ride.fareQuote.estimatedDurationMin > 0 -> "~${ride.fareQuote.estimatedDurationMin} min"
                                else -> "En camino"
                            }
                            InfoPill(
                                icon = { Icon(Icons.Default.DirectionsCar, null, tint = MeetColors.neonGreen) },
                                label = "Llegada",
                                value = etaText,
                                color = MeetColors.neonGreen,
                                modifier = Modifier.weight(1f)
                            )
                            InfoPill(
                                icon = { Icon(Icons.Default.AttachMoney, null, tint = MeetColors.electricBlue) },
                                label = "Tarifa estimada",
                                value = ride.fareQuote.formattedTotal,
                                color = MeetColors.electricBlue,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // EVAIR Smart Assistant card (Laya AI)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showEvairAssistant = true },
                            shape = RoundedCornerShape(14.dp),
                            color = MeetColors.cardBackgroundLighter,
                            border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.SmartToy,
                                        contentDescription = null,
                                        tint = MeetColors.neonGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            "Asistente EVAIR (Laya AI)",
                                            color = MeetColors.textPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "¿Dudas de ETA, Sinpe Móvil o tu viaje?",
                                            color = MeetColors.cyberCyan,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                Text(
                                    "Consultar →",
                                    color = MeetColors.neonGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Boarding PIN card when driver has arrived
                        if (ride.state == RideState.ARRIVED) {
                            if (ride.boardingPin != null) {
                                Surface(
                                    color = MeetColors.neonGreen.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, MeetColors.neonGreen),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        Modifier.padding(14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text("TU PIN PRIVADO DE ABORDAJE", color = MeetColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            ride.boardingPin.chunked(1).joinToString("  "),
                                            color = MeetColors.neonGreen,
                                            fontSize = 32.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 2.sp
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text("Díselo al conductor asignado para validar el abordaje", color = MeetColors.warning, fontSize = 10.sp)
                                    }
                                }
                            } else if (onGeneratePin != null) {
                                Button(
                                    onClick = onGeneratePin,
                                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen, contentColor = Color.Black),
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("GENERAR PIN DE ABORDAJE 🔐", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }

                        // Expandable details button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDetails = !showDetails }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (showDetails) "Ocultar detalles del viaje" else "Ver detalles y desglose",
                                style = MaterialTheme.typography.labelMedium,
                                color = MeetColors.textSecondary
                            )
                            Icon(
                                if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MeetColors.textSecondary
                            )
                        }

                        AnimatedVisibility(visible = showDetails) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                DetailRow("Origen", ride.pickup.displayName)
                                DetailRow("Destino", ride.dropoff.displayName)
                                DetailRow("Distancia total", ride.fareQuote.formattedDistance)
                                DetailRow("Duración estimada", ride.fareQuote.formattedDuration)
                                DetailRow("Modalidad", ride.fareQuote.fareMode.displayName)
                            }
                        }

                        if (actions.canPay && onPay != null) {
                            Button(onClick = onPay, modifier = Modifier.fillMaxWidth()) { Text("Pagar") }
                        }
                        if (actions.canRate && onRate != null) {
                            Button(onClick = onRate, modifier = Modifier.fillMaxWidth()) { Text("Calificar") }
                        }
                        if (ride.state == RideState.COMPLETED && !actions.canPay && !actions.canRate) {
                            Text("Pago y calificación: pendientes de integración y confirmación del servidor", color = MeetColors.textSecondary)
                        }

                        if (ride.state.isCancellable) {
                            notice?.let {
                                Text(
                                    text = it,
                                    color = MeetColors.warning,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            OutlinedButton(
                                onClick = onCancelRide,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MeetColors.error)
                            ) {
                                Text("Cancelar Viaje", color = MeetColors.error, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            if (showSafetyCenter) {
                AlertDialog(
                    onDismissRequest = { showSafetyCenter = false },
                    title = { Text("Ayuda y seguridad") },
                    text = { Text("El monitoreo Guardian, las alertas automáticas y compartir ubicación en vivo no están disponibles en este viaje. Si necesitas ayuda urgente, abre el teléfono y llama al servicio de emergencias de tu ubicación. Esta acción no transmite datos del viaje.") },
                    confirmButton = {
                        TextButton(onClick = {
                            val dial = android.content.Intent(android.content.Intent.ACTION_DIAL)
                            if (dial.resolveActivity(context.packageManager) != null) context.startActivity(dial)
                        }) { Text("Abrir teléfono") }
                    },
                    dismissButton = { TextButton(onClick = { showSafetyCenter = false }) { Text("Cerrar") } }
                )
            }

            // Laya AI Assistant Modal Bottom Sheet
            EvairAssistantSheet(
                isOpen = showEvairAssistant,
                onDismiss = { showEvairAssistant = false },
                rideContext = RideAssistantContext(
                    rideId = ride.rideId,
                    driverName = ride.driver?.name,
                    driverPlate = ride.driver?.plate,
                    driverVehicle = ride.driver?.vehicle,
                    state = ride.state.name,
                    etaMinutes = ride.driver?.etaMinutes ?: ride.fareQuote.estimatedDurationMin,
                    pickupAddress = ride.pickup.displayName,
                    dropoffAddress = ride.dropoff.displayName,
                    fareFormatted = ride.fareQuote.formattedTotal,
                    paymentMethod = "SINPE_MOVIL"
                ),
                onActionTriggered = { action ->
                    when (action.type) {
                        ActionType.CALL_DRIVER -> onCallDriver?.invoke()
                        ActionType.MESSAGE_DRIVER -> onMessageDriver?.invoke()
                        ActionType.SAFETY_CENTER -> showSafetyCenter = true
                        ActionType.CANCEL_RIDE -> onCancelRide()
                        else -> {}
                    }
                }
            )
        }
    }
}
