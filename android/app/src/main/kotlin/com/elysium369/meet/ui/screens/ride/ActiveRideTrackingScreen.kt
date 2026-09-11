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
import com.elysium369.meet.core.geo.CommonMapState
import com.elysium369.meet.core.geo.GeoMarker
import com.elysium369.meet.core.geo.GeoMarkerRole
import com.elysium369.meet.core.geo.GeoPoint
import com.elysium369.meet.core.geo.GeoRoute
import com.elysium369.meet.core.geo.runtime.CommonMapPanel
import com.elysium369.meet.ride.domain.RideState
import com.elysium369.meet.ride.payment.RidePaymentMethod
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
    onCallDriver: (() -> Unit)?,
    onMessageDriver: (() -> Unit)?,
    onPay: (() -> Unit)?,
    onRate: (() -> Unit)?,
) {
    val context = LocalContext.current
    var showSafetyCenter by remember { mutableStateOf(false) }
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
        ride.driverLocation?.takeIf { trackingTruth == com.elysium369.meet.ride.domain.TrackingFreshness.LIVE }?.let { loc ->
            markers.add(
                GeoMarker(
                    id = "drv_loc",
                    role = GeoMarkerRole.PROVIDER_LIVE,
                    point = GeoPoint(loc.latitude, loc.longitude),
                    label = ride.driver?.name ?: "Conductor"
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
                    Text(
                        trackingTruth.label,
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
                    modifier = Modifier.fillMaxSize()
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
                            InfoPill(
                                icon = { Icon(Icons.Default.DirectionsCar, null, tint = MeetColors.neonGreen) },
                                label = "Llegada",
                                value = ride.driver?.etaMinutes?.let { "~$it min" } ?: "No disponible",
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
        }
    }
}
