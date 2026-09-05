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
 * ActiveRideTrackingScreen — Real-time live ride tracking with driver location streaming,
 * PTT voice session, and safety guardian (Items 3, 5, 6, 8, 9).
 */
@Composable
fun ActiveRideTrackingScreen(
    ride: ActiveRideViewState,
    onCancelRide: () -> Unit = {},
    onBack: () -> Unit = {},
    onCallDriver: () -> Unit = {},
    onMessageDriver: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    var showSafetyCenter by remember { mutableStateOf(false) }
    var showPaymentConfirmation by remember { mutableStateOf(false) }
    var showRatingSheet by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var pttVoiceState by remember { mutableStateOf(PttVoiceState.IDLE) }

    val mapState = remember(ride) {
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
        ride.driverLocation?.let { loc ->
            markers.add(
                GeoMarker(
                    id = "drv_loc",
                    role = GeoMarkerRole.PROVIDER_LIVE,
                    point = GeoPoint(loc.latitude, loc.longitude),
                    label = ride.driver.name
                )
            )
        }
        val route = listOf(
            GeoRoute(
                points = listOf(
                    GeoPoint(ride.pickup.latitude, ride.pickup.longitude),
                    GeoPoint(ride.dropoff.latitude, ride.dropoff.longitude)
                ),
                distanceMeters = (ride.fareQuote.estimatedDistanceKm * 1000).toLong(),
                durationSeconds = ride.fareQuote.estimatedDurationMin * 60L
            )
        )
        CommonMapState(markers = markers, routes = route)
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
                        "Seguimiento en Vivo",
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
                // PTT Audio Session Bar (Push-To-Talk Voice Item 5)
                PttAudioSessionBar(
                    channelName = "Canal Seguro de Voz con ${ride.driver.name}",
                    state = pttVoiceState,
                    speakerName = ride.driver.name
                )

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
                                        ride.driver.name.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MeetColors.neonGreen
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(ride.driver.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MeetColors.textPrimary)
                                Text("${ride.driver.vehicle} • ${ride.driver.plate}", style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                                Text("★ ${ride.driver.rating} (${ride.driver.totalTrips} viajes)", style = MaterialTheme.typography.labelSmall, color = MeetColors.neonGreen)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedIconButton(
                                    onClick = onCallDriver,
                                    border = BorderStroke(1.dp, MeetColors.neonGreen)
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = "Llamar", tint = MeetColors.neonGreen)
                                }
                                OutlinedIconButton(
                                    onClick = onMessageDriver,
                                    border = BorderStroke(1.dp, MeetColors.electricBlue)
                                ) {
                                    Icon(Icons.Default.Chat, contentDescription = "Chat", tint = MeetColors.electricBlue)
                                }
                            }
                        }

                        // ETA & Fare Pills
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            InfoPill(
                                icon = { Icon(Icons.Default.DirectionsCar, null, tint = MeetColors.neonGreen) },
                                label = "Llegada",
                                value = "~${ride.driver.etaMinutes} min",
                                color = MeetColors.neonGreen,
                                modifier = Modifier.weight(1f)
                            )
                            InfoPill(
                                icon = { Icon(Icons.Default.AttachMoney, null, tint = MeetColors.electricBlue) },
                                label = "Tarifa",
                                value = ride.fareQuote.formattedTotal,
                                color = MeetColors.electricBlue,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Interactive PTT Voice Button Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            PttFloatingButton(
                                state = pttVoiceState,
                                activeSpeakerName = ride.driver.name,
                                onPressStart = { pttVoiceState = PttVoiceState.TRANSMITTING },
                                onPressEnd = { pttVoiceState = PttVoiceState.IDLE }
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

                        // Action Buttons: Pay / Rate / Cancel
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { showPaymentConfirmation = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen, contentColor = Color.Black)
                            ) {
                                Text("PAGAR", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { showRatingSheet = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.electricBlue)
                            ) {
                                Text("CALIFICAR", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (ride.state.isCancellable) {
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

            // Overlays
            if (showSafetyCenter) {
                SafetyCenterOverlay(
                    ride = ride,
                    onDismiss = { showSafetyCenter = false },
                    onShareTrip = { /* Share */ },
                    onSOS = { /* SOS */ },
                    onGuardian = { /* Guardian */ }
                )
            }

            if (showPaymentConfirmation) {
                RidePaymentConfirmationDialog(
                    fareQuote = ride.fareQuote,
                    paymentMethod = RidePaymentMethod.SINPE_MOVIL,
                    onConfirmPayment = {
                        showPaymentConfirmation = false
                        showRatingSheet = true
                    },
                    onDismiss = { showPaymentConfirmation = false }
                )
            }

            if (showRatingSheet) {
                RideRatingAndReviewSheet(
                    counterpartName = ride.driver.name,
                    onSubmitReview = { stars, tip, notes ->
                        showRatingSheet = false
                        onBack()
                    },
                    onDismiss = { showRatingSheet = false }
                )
            }
        }
    }
}
