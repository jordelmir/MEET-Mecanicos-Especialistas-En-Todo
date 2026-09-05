package com.elysium369.meet.ui.screens.ride

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Clock
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ride.domain.RideState
import com.elysium369.meet.ui.theme.MeetColors

@Composable
fun ActiveRideTrackingScreen(
    ride: ActiveRideViewState,
    onCancel: () -> Unit,
    onSafety: () -> Unit,
    onCallDriver: () -> Unit,
    onMessageDriver: () -> Unit,
) {
    val compactHeader = LocalConfiguration.current.screenWidthDp < 600
    val showDetails by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        // Map placeholder - in real implementation this would be RideMapPanel
        MapPlaceholder(
            pickup = ride.pickup,
            dropoff = ride.dropoff,
            driverLocation = ride.driverLocation,
            passengerLocation = ride.passengerLocation,
        )

        // Bottom sheet with ride info
        AnimatedVisibility(
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .widthIn(max = 400.dp),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                elevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header with driver info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Tu viaje", style = MaterialTheme.typography.labelMedium, color = MeetColors.textSecondary)
                            Text(getRideStatusLabel(ride.state), style = MaterialTheme.typography.titleLarge, color = MeetColors.textPrimary, fontWeight = FontWeight.Bold)
                        }
                        Surface(
                            shape = CircleShape,
                            color = MeetColors.neonGreen.copy(alpha = 0.12f),
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.padding(12.dp))
                        }
                    }

                    // Driver info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MeetColors.cardBackground,
                            border = BorderStroke(2.dp, MeetColors.neonGreen),
                            modifier = Modifier.size(56.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(ride.driver.name.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, color = MeetColors.neonGreen, fontWeight = FontWeight.Bold)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(ride.driver.name, style = MaterialTheme.typography.titleMedium, color = MeetColors.textPrimary, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RatingStars(rating = ride.driver.rating, size = 14.sp)
                                Text("${ride.driver.rating} • ${ride.driver.totalTrips} viajes", style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                            }
                            Text("${ride.driver.vehicle} • ${ride.driver.plate}", style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                        }

                        Column(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onCallDriver, shape = RoundedCornerShape(12.dp)) {
                                Row { Icon(Icons.Default.Phone, contentDescription = null, size = 18.dp); Spacer(Modifier.width(4.dp)); Text("Llamar") }
                            }
                            OutlinedButton(onClick = onMessageDriver, shape = RoundedCornerShape(12.dp)) {
                                Row { Icon(Icons.Default.Message, contentDescription = null, size = 18.dp); Spacer(Modifier.width(4.dp)); Text("Chat") }
                            }
                        }
                    }

                    // Route info cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InfoPill(
                            icon = { Icons.Default.LocationOn },
                            label = "Origen",
                            value = ride.pickup.displayName,
                            color = MeetColors.neonGreen
                        )
                        InfoPill(
                            icon = { Icons.Default.Flag },
                            label = "Destino",
                            value = ride.dropoff.displayName,
                            color = MeetColors.electricBlue
                        )
                    }

                    // Fare & ETA
                    if (ride.fareQuote != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            InfoPill(
                                icon = { Icons.Default.Clock },
                                label = "Duración",
                                value = ride.fareQuote!.formattedDuration,
                                color = MeetColors.electricPurple
                            )
                            InfoPill(
                                icon = { Icons.Default.AttachMoney },
                                label = "Tarifa",
                                value = ride.fareQuote!.formattedTotal,
                                color = MeetColors.neonGreen
                            )
                        }
                    }

                    // Safety button
                    OutlinedButton(
                        onClick = onSafety,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MeetColors.error),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.error)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = MeetColors.error)
                            Spacer(Modifier.width(8.dp))
                            Text("Centro de Seguridad", style = MaterialTheme.typography.labelLarge, color = MeetColors.error)
                        }
                    }

                    // Expandable details
                    OutlinedButton(
                        onClick = { showDetails = !showDetails },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(if (showDetails) "Ocultar detalles" else "Ver detalles del viaje", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }
                    }

                    // Expanded details
                    AnimatedVisibility(visible = showDetails) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            DetailRow("ID del viaje", ride.rideId)
                            DetailRow("Tarifa", ride.fareQuote?.formattedTotal ?: "—")
                            DetailRow("Distancia", ride.fareQuote?.formattedDistance ?: "—")
                            DetailRow("Tiempo estimado", ride.fareQuote?.formattedDuration ?: "—")
                            DetailRow("Modalidad", ride.fareQuote?.fareMode.displayName ?: "—")
                            DetailRow("Método de pago", "Efectivo") // TODO: from ride
                        }
                    }

                    // Cancel button (only for certain states)
                    if (ride.state.isCancellable) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MeetColors.error),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.error)
                        ) {
                            Text("Cancelar viaje", style = MaterialTheme.typography.labelLarge, color = MeetColors.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MapPlaceholder(
    pickup: RidePlaceInput,
    dropoff: RidePlaceInput,
    driverLocation: RideLocationPoint?,
    passengerLocation: RideLocationPoint?,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Gradient background simulating map
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to MeetColors.backgroundDeep,
                0.5f to Color(0xFF0A1528),
                1f to MeetColors.backgroundDeep
            )
        ))

        // Route line simulation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        0f to MeetColors.neonGreen,
                        1f to MeetColors.electricBlue
                    )
                )
                .padding(horizontal = 40.dp)
                .padding(top = 40.dp)
        )

        // Pickup marker
        Box(
            modifier = Modifier
                .size(40.dp)
                .padding(start = 32.dp, top = 32.dp)
                .background(MeetColors.neonGreen, CircleShape)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Black, modifier = Modifier.padding(8.dp))
        }

        // Dropoff marker
        Box(
            modifier = Modifier
                .size(40.dp)
                .padding(end = 32.dp, bottom = 120.dp)
                .background(MeetColors.electricBlue, CircleShape)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Flag, contentDescription = null, tint = Color.White, modifier = Modifier.padding(8.dp))
        }

        // Driver marker (if available)
        driverLocation?.let { _ ->
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .padding(start = 80.dp, top = 120.dp)
                    .background(MeetColors.neonGreen, CircleShape)
                    .clip(CircleShape)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = Color.Black, modifier = Modifier.padding(6.dp))
            }
        }

        // Status overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 16.dp, 16.dp, 16.dp),
            contentAlignment = Alignment.TopStart
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep.copy(alpha = 0.9f)),
                elevation = 4.dp
            ) {
                PaddingValues(12.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = MeetColors.neonGreen)
                        Column {
                            Text("Conductor en camino", style = MaterialTheme.typography.labelMedium, color = MeetColors.neonGreen, fontWeight = FontWeight.Bold)
                            Text("Llegada en ~3 min", style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoPill(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    color: Color,
) {
    Card(
        modifier = Modifier.weight(1f).height(80.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(shape = CircleShape, color = color.copy(alpha = 0.12f), modifier = Modifier.size(36.dp)) {
                icon()
            }
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MeetColors.textSecondary)
            Text(value, style = MaterialTheme.typography.labelMedium, color = MeetColors.textPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MeetColors.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MeetColors.textPrimary, fontWeight = FontWeight.Medium)
    }
}

fun RideState.isCancellable: Boolean {
    return this in listOf(RideState.REQUESTED, RideState.SEARCHING, RideState.OFFERED, RideState.ASSIGNED, RideState.DRIVER_EN_ROUTE, RideState.DRIVER_ARRIVED)
}

@Composable
fun RatingStars(
    rating: Double,
    maxStars: Int = 5,
    size: Int = 16,
    color: Color = MeetColors.neonGreen
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..maxStars) {
            val fill = when {
                rating >= i -> 1.0
                rating >= i - 0.5 -> 0.5
                else -> 0.0
            }
            Icon(
                imageVector = when {
                    fill == 1.0 -> Icons.Default.Star
                    fill == 0.5 -> Icons.Default.StarHalf
                    else -> Icons.Default.StarOutline
                },
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(size.dp)
            )
        }
    }
}

fun getRideStatusLabel(state: RideState): String {
    return when (state) {
        RideState.REQUESTED -> "Buscando conductor..."
        RideState.SEARCHING -> "Buscando conductor..."
        RideState.OFFERED -> "Oferta enviada"
        RideState.ASSIGNED -> "Conductor asignado"
        RideState.DRIVER_EN_ROUTE -> "Conductor en camino"
        RideState.DRIVER_ARRIVED -> "Conductor llegó"
        RideState.PASSENGER_ONBOARD -> "En viaje"
        RideState.IN_PROGRESS -> "En viaje"
        RideState.COMPLETED -> "Viaje completado"
        RideState.CANCELLED_BY_RIDER -> "Cancelado"
        RideState.CANCELLED_BY_DRIVER -> "Cancelado por conductor"
        RideState.CANCELLED_BY_SYSTEM -> "Cancelado"
        RideState.NO_DRIVER_FOUND -> "Sin conductor disponible"
        RideState.EXPIRED -> "Expirado"
        RideState.FAILED -> "Error"
        RideState.DISPUTED -> "En disputa"
        RideState.DRAFT -> "Borrador"
    }
}