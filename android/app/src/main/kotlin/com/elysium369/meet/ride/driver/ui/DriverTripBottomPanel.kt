package com.elysium369.meet.ride.driver.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.elysium369.meet.ride.domain.RideFareEngine
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ride.domain.RideCommandType
import com.elysium369.meet.ride.domain.RidePassengerPreferences
import com.elysium369.meet.ride.driver.DriverTripIntent
import com.elysium369.meet.ride.driver.DriverTripPhase
import com.elysium369.meet.ride.driver.DriverTripUiState
import com.elysium369.meet.ride.map.RideManeuverFormatter
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.delay

@Composable
fun DriverTripBottomPanel(
    state: DriverTripUiState,
    onIntent: (DriverTripIntent) -> Unit,
    onOpenMessages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MeetColors.cardBackground.copy(alpha = 0.98f),
        ),
        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Drag handle / Accent line
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MeetColors.textMuted)
                    .align(Alignment.CenterHorizontally),
            )

            // Top Row: ETA or Waiting Timer
            when (state.phase) {
                DriverTripPhase.AtPickup -> {
                    WaitingPassengerHeader(driverArrivedAtEpochMs = state.driverArrivedAtEpochMs)
                }
                else -> {
                    ActiveNavigationMetricHeader(
                        remainingMeters = state.remainingDistanceMeters,
                        remainingSeconds = state.remainingDurationSeconds,
                    )
                }
            }

            // Middle Row: Passenger & Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Passenger Avatar
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(MeetColors.backgroundDark, CircleShape)
                        .border(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.passengerName.take(1).uppercase(),
                        color = MeetColors.neonGreen,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Passenger Details
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = state.passengerName,
                        color = MeetColors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.passengerTripCount?.let { "$it viajes completados" } ?: "Pasajero verificado",
                        color = MeetColors.textSecondary,
                        fontSize = 12.sp,
                    )
                }

                // LiveKit Voice Call Button (Zero phone leakage)
                Surface(
                    shape = CircleShape,
                    color = when {
                        state.isVoiceConnected -> MeetColors.neonGreen.copy(alpha = 0.2f)
                        state.isVoiceConnecting -> MeetColors.warning.copy(alpha = 0.2f)
                        else -> MeetColors.backgroundDark
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            state.isVoiceConnected -> MeetColors.neonGreen
                            state.isVoiceConnecting -> MeetColors.warning
                            else -> MeetColors.neonGreen.copy(alpha = 0.4f)
                        }
                    ),
                    modifier = Modifier
                        .size(42.dp)
                        .clickable { onIntent(DriverTripIntent.ToggleVoiceCall) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (state.isMicrophoneMuted) Icons.Default.MicOff else Icons.Default.Phone,
                            contentDescription = when {
                                state.isVoiceConnected -> "Enlace de voz activo. Toca para finalizar."
                                state.isVoiceConnecting -> "Conectando enlace de voz..."
                                else -> "Iniciar llamada de voz segura con el pasajero"
                            },
                            tint = when {
                                state.isVoiceConnected -> MeetColors.neonGreen
                                state.isVoiceConnecting -> MeetColors.warning
                                else -> MeetColors.neonGreen
                            },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Chat Action Button (Zero phone leakage)
                Surface(
                    shape = CircleShape,
                    color = MeetColors.backgroundDark,
                    border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .size(42.dp)
                        .clickable { onOpenMessages() },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "Contactar al pasajero por chat",
                            tint = MeetColors.cyberCyan,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Waze Quick Navigation Button
                Surface(
                    shape = CircleShape,
                    color = MeetColors.backgroundDark,
                    border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .size(42.dp)
                        .clickable { onIntent(DriverTripIntent.OpenExternalNavigation) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = com.elysium369.meet.R.drawable.ic_waze_logo),
                            contentDescription = "Navegar con Waze a la ubicación del viaje",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Details / Trip Control Sheet Button
                Surface(
                    shape = CircleShape,
                    color = MeetColors.backgroundDark,
                    border = BorderStroke(1.dp, MeetColors.borderBlue),
                    modifier = Modifier
                        .size(42.dp)
                        .clickable { onIntent(DriverTripIntent.ToggleDetailsSheet(true)) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Detalles y opciones operativas",
                            tint = MeetColors.textPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Passenger Preferences Badges (if any)
            if (state.passengerPreferences.hasSpecialPreferences) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    state.passengerPreferences.toBadges().forEach { badge ->
                        Surface(
                            color = badge.color.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, badge.color.copy(alpha = 0.5f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(badge.icon, fontSize = 12.sp)
                                Text(
                                    badge.label,
                                    color = badge.color,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }

            if (state.phase == DriverTripPhase.InProgress) {
                val elapsedSeconds = state.tripStartedAtEpochMs?.let {
                    ((System.currentTimeMillis() - it) / 1000L).coerceAtLeast(0L)
                } ?: 0L
                val elapsedMinutes = (elapsedSeconds / 60).toInt()
                val elapsedSecs = (elapsedSeconds % 60).toInt()
                val isMetered = state.fareMode == "METERED_TIME_DISTANCE"

                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "MÉTRICAS EN VIVO · SINCRONIZADO",
                                color = MeetColors.cyberCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                            )
                            Surface(
                                color = MeetColors.neonGreen.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "EN CURSO 🏁",
                                    color = MeetColors.neonGreen,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "%02d:%02d".format(elapsedMinutes, elapsedSecs),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                )
                                Text("TIEMPO VIAJE", color = MeetColors.textMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "${String.format(java.util.Locale.US, "%.1f", state.estimatedDistanceKm)} km",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                )
                                Text("DISTANCIA", color = MeetColors.textMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val fareDisplay = if (isMetered) {
                                    val quote = RideFareEngine.quoteCostaRica(
                                        distanceMeters = (state.estimatedDistanceKm * 1000).toLong(),
                                        durationSeconds = elapsedSeconds,
                                    )
                                    "₡${quote.estimatedTotalMinor}"
                                } else {
                                    "₡${state.agreedFareMinor}"
                                }
                                Text(
                                    fareDisplay,
                                    color = MeetColors.neonGreen,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                )
                                Text(
                                    if (isMetered) "TAXÍMETRO" else "TARIFA FIJA",
                                    color = MeetColors.textMuted,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (!isMetered) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "🤝 Modalidad 'Pon tu precio': Tarifa fija acordada — No varía con tiempo ni distancia.",
                                color = Color(0xFFFFCC80),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Divider(color = MeetColors.borderSubtle.copy(alpha = 0.4f), thickness = 0.5.dp)
                        Spacer(Modifier.height(6.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "⚠️ Los peajes los paga siempre el usuario, no el chofer.",
                                color = Color(0xFFFFCC80),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "⚠️ No se permite dejar viajes pendientes.",
                                color = Color(0xFFFFAB91),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Bottom Row: Contextual Operational CTA
            DriverContextualAction(
                state = state,
                onIntent = onIntent,
            )
        }
    }

    // Boarding PIN Dialog
    if (state.showPinDialog) {
        BoardingPinDialog(
            pinInput = state.boardingPinInput,
            onPinChange = { onIntent(DriverTripIntent.UpdateBoardingPinInput(it)) },
            onConfirm = { onIntent(DriverTripIntent.SubmitBoardingPin(state.boardingPinInput)) },
            onDismiss = { onIntent(DriverTripIntent.DismissBoardingPin) },
            enabled = state.pendingCommand == null,
        )
    }

    // Driver En-Route Cancellation Dialog
    if (state.showCancelDialog) {
        DriverCancelTripDialog(
            onConfirm = { reasonCode, detail ->
                onIntent(DriverTripIntent.ToggleCancelDialog(false))
                onIntent(DriverTripIntent.CancelTrip(reasonCode = reasonCode, detail = detail))
            },
            onDismiss = { onIntent(DriverTripIntent.ToggleCancelDialog(false)) },
            enabled = state.pendingCommand == null,
            isAfterArrival = state.phase in setOf(
                DriverTripPhase.AtPickup,
                DriverTripPhase.PassengerOnboard,
                DriverTripPhase.InProgress
            ),
        )
    }
}

@Composable
private fun ActiveNavigationMetricHeader(
    remainingMeters: Long?,
    remainingSeconds: Long?,
) {
    val distanceText = remainingMeters?.let { RideManeuverFormatter.formatDistance(it) } ?: "--"
    val durationText = remainingSeconds?.let { RideManeuverFormatter.formatDuration(it) } ?: "--"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = durationText,
                color = MeetColors.cyberCyan,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "•  $distanceText",
                color = MeetColors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MeetColors.backgroundDark,
            border = BorderStroke(1.dp, MeetColors.borderBlue),
        ) {
            Text(
                text = "Ruta en curso",
                color = MeetColors.neonGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun WaitingPassengerHeader(
    driverArrivedAtEpochMs: Long?,
) {
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(driverArrivedAtEpochMs) {
        if (driverArrivedAtEpochMs != null && driverArrivedAtEpochMs > 0L) {
            while (true) {
                val now = System.currentTimeMillis()
                elapsedSeconds = ((now - driverArrivedAtEpochMs) / 1000L).coerceAtLeast(0L)
                delay(1000L)
            }
        }
    }

    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val timeFormatted = "%02d:%02d".format(minutes, seconds)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Has llegado al punto",
                color = MeetColors.neonGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Esperando al pasajero",
                color = MeetColors.textSecondary,
                fontSize = 12.sp,
            )
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MeetColors.backgroundDark,
            border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.5f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "⏱ $timeFormatted",
                    color = MeetColors.neonGreen,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun DriverContextualAction(
    state: DriverTripUiState,
    onIntent: (DriverTripIntent) -> Unit,
) {
    val isPending = state.pendingCommand != null

    when (state.phase) {
        DriverTripPhase.Assigned -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { onIntent(DriverTripIntent.StartDrivingToPickup) },
                    enabled = !isPending,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeetColors.neonGreen,
                        contentColor = Color.Black,
                    ),
                ) {
                    Text(
                        text = if (isPending) "Iniciando…" else "Iniciar hacia recogida",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MeetColors.error.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, MeetColors.error.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .size(54.dp)
                        .clickable(enabled = !isPending) { onIntent(DriverTripIntent.ToggleCancelDialog(true)) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancelar viaje",
                            tint = MeetColors.error,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
        DriverTripPhase.ToPickup -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    DriverArrivalSlider(
                        enabled = !isPending,
                        label = if (state.pendingCommand == RideCommandType.DRIVER_ARRIVED) {
                            "Confirmando llegada…"
                        } else {
                            "Desliza para confirmar llegada"
                        },
                        onConfirmed = { onIntent(DriverTripIntent.ConfirmArrival) },
                        accentColor = MeetColors.neonGreen,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MeetColors.error.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, MeetColors.error.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .size(54.dp)
                        .clickable(enabled = !isPending) { onIntent(DriverTripIntent.ToggleCancelDialog(true)) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancelar viaje en camino",
                            tint = MeetColors.error,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
        DriverTripPhase.AtPickup -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { onIntent(DriverTripIntent.OpenBoardingPin) },
                    enabled = !isPending,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeetColors.cyberCyan,
                        contentColor = Color.Black,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Pin,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPending) "Verificando…" else "Verificar PIN de abordaje",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MeetColors.error.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, MeetColors.error.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .size(54.dp)
                        .clickable(enabled = !isPending) { onIntent(DriverTripIntent.ToggleCancelDialog(true)) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancelar viaje en punto de recogida",
                            tint = MeetColors.error,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
        DriverTripPhase.PassengerOnboard -> {
            Button(
                onClick = { onIntent(DriverTripIntent.StartTrip) },
                enabled = !isPending,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeetColors.neonGreen,
                    contentColor = Color.Black,
                ),
            ) {
                Text(
                    text = if (isPending) "Iniciando…" else "Iniciar viaje con pasajero",
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                )
            }
        }
        DriverTripPhase.InProgress -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    DriverArrivalSlider(
                        enabled = !isPending,
                        label = if (state.pendingCommand == RideCommandType.COMPLETE) {
                            "Finalizando viaje…"
                        } else {
                            "Desliza para finalizar viaje"
                        },
                        onConfirmed = { onIntent(DriverTripIntent.CompleteTrip) },
                        accentColor = MeetColors.cyberCyan,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MeetColors.error.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, MeetColors.error.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .size(54.dp)
                        .clickable(enabled = !isPending) { onIntent(DriverTripIntent.ToggleCancelDialog(true)) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancelar viaje en curso",
                            tint = MeetColors.error,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
        DriverTripPhase.Completed -> {
            val finalFare = state.finalFareMinor ?: state.agreedFareMinor
            val commission = state.commissionMinor ?: (finalFare * 500L / 10000L)
            val netEarnings = (finalFare - commission).coerceAtLeast(0L)

            Card(
                colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                border = BorderStroke(1.5.dp, MeetColors.neonGreen.copy(alpha = 0.8f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MeetColors.neonGreen,
                            modifier = Modifier.size(28.dp),
                        )
                        Text(
                            text = "¡VIAJE FINALIZADO!",
                            color = MeetColors.neonGreen,
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            letterSpacing = 0.5.sp,
                        )
                    }

                    Text(
                        text = "Pasajero: ${state.passengerName}",
                        color = MeetColors.textSecondary,
                        fontSize = 13.sp,
                    )

                    Surface(
                        color = MeetColors.backgroundDark,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MeetColors.borderSubtle),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Tarifa Cobrada", color = MeetColors.textSecondary, fontSize = 13.sp)
                                Text("₡%,d".format(finalFare), color = MeetColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Comisión MEET (5%)", color = MeetColors.textSecondary, fontSize = 13.sp)
                                Text("-₡%,d".format(commission), color = MeetColors.error, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Divider(color = MeetColors.borderSubtle, thickness = 1.dp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Ganancia Neta", color = MeetColors.neonGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("₡%,d".format(netEarnings), color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 20.sp)
                            }
                        }
                    }

                    Button(
                        onClick = { onIntent(DriverTripIntent.DismissCompletedTrip) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MeetColors.neonGreen,
                            contentColor = Color.Black,
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    ) {
                        Text(
                            text = "FINALIZAR Y VOLVER AL RADAR",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun BoardingPinDialog(
    pinInput: String,
    onPinChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    enabled: Boolean,
) {
    AlertDialog(
        onDismissRequest = { if (enabled) onDismiss() },
        containerColor = MeetColors.cardBackground,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "PIN de abordaje",
                color = MeetColors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Solicita al pasajero el PIN numérico de 4 dígitos para verificar su identidad.",
                    color = MeetColors.textSecondary,
                    fontSize = 13.sp,
                )

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) onPinChange(it) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    placeholder = { Text("••••", color = MeetColors.textMuted, fontSize = 24.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = MeetColors.neonGreen,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MeetColors.neonGreen,
                        unfocusedBorderColor = MeetColors.borderBlue,
                    ),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = enabled && pinInput.length == 4,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeetColors.neonGreen,
                    contentColor = Color.Black,
                ),
            ) {
                Text("Validar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = enabled,
            ) {
                Text("Cancelar", color = MeetColors.textSecondary)
            }
        },
    )
}
