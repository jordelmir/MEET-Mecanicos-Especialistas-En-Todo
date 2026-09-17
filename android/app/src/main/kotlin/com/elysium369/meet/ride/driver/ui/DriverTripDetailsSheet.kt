package com.elysium369.meet.ride.driver.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ride.driver.DriverTripIntent
import com.elysium369.meet.ride.driver.DriverTripUiState
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverTripDetailsSheet(
    state: DriverTripUiState,
    onIntent: (DriverTripIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCancelDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { onIntent(DriverTripIntent.ToggleDetailsSheet(false)) },
        sheetState = sheetState,
        containerColor = MeetColors.backgroundDark,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Control del Viaje",
                    color = MeetColors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = { onIntent(DriverTripIntent.ToggleDetailsSheet(false)) },
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = MeetColors.textSecondary,
                    )
                }
            }

            // Route Points
            Surface(
                color = MeetColors.cardBackground,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MeetColors.borderBlue),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(MeetColors.neonGreen.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MeetColors.neonGreen,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Column {
                            Text(
                                text = "Recogida",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = state.pickupAddress.ifBlank { "Punto de recogida" },
                                color = MeetColors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(MeetColors.cyberCyan.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = null,
                                tint = MeetColors.cyberCyan,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Column {
                            Text(
                                text = "Destino final",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = state.destinationAddress.ifBlank { "Destino" },
                                color = MeetColors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // Future Offers Toggle (Presence separation: does NOT cancel current trip)
            Surface(
                color = MeetColors.cardBackground,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MeetColors.borderBlue),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.acceptingFutureOffers) "Aceptar viajes siguientes" else "No aceptar más viajes",
                            color = MeetColors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (state.acceptingFutureOffers) {
                                "Recibir solicitudes encadenadas mientras completas este viaje"
                            } else {
                                "Pausado para viajes futuros. El viaje activo no se altera."
                            },
                            color = MeetColors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Switch(
                        checked = state.acceptingFutureOffers,
                        onCheckedChange = { onIntent(DriverTripIntent.ToggleAcceptingFutureOffers) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MeetColors.neonGreen,
                            checkedTrackColor = MeetColors.neonGreen.copy(alpha = 0.4f),
                        ),
                    )
                }
            }

            // Safety Share Action
            Surface(
                color = MeetColors.cardBackground,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MeetColors.borderBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onIntent(DriverTripIntent.CreateSafetyShare) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(MeetColors.neonGreen.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MeetColors.neonGreen,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Compartir seguimiento seguro",
                            color = MeetColors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (state.safetyShareUrl != null) {
                                state.safetyShareUrl
                            } else {
                                "Genera un enlace cifrado sin revelar datos personales"
                            },
                            color = if (state.safetyShareUrl != null) MeetColors.neonGreen else MeetColors.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (state.safetyShareUrl != null) {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("MEET Safety Link", state.safetyShareUrl)
                                clipboard.setPrimaryClip(clip)
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copiar enlace",
                                tint = MeetColors.neonGreen,
                            )
                        }
                    }
                }
            }

            // Support and Cancellation Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Support Case
                Surface(
                    color = MeetColors.cardBackground,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MeetColors.borderBlue),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            onIntent(
                                DriverTripIntent.OpenSupport(
                                    category = "IN_TRIP_INCIDENT",
                                    summary = "Incidencia reportada por el conductor durante el viaje",
                                ),
                            )
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Help,
                            contentDescription = null,
                            tint = MeetColors.cyberCyan,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Soporte",
                            color = MeetColors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Cancel Ride
                Surface(
                    color = MeetColors.cardBackground,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MeetColors.error.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showCancelDialog = true },
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MeetColors.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Cancelar",
                            color = MeetColors.error,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showCancelDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            containerColor = MeetColors.cardBackground,
            title = {
                Text(
                    text = "¿Cancelar este viaje?",
                    color = MeetColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "La cancelación operacional quedará registrada bajo la política anti-fraude de MEET.",
                    color = MeetColors.textSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelDialog = false
                        onIntent(DriverTripIntent.ToggleDetailsSheet(false))
                        onIntent(
                            DriverTripIntent.CancelTrip(
                                reasonCode = "DRIVER_OPERATIONAL_ISSUE",
                                detail = "Cancelado por el conductor desde el Active Trip Cockpit",
                            ),
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.error),
                ) {
                    Text("Confirmar Cancelación", color = Color.White)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showCancelDialog = false }) {
                    Text("Regresar", color = MeetColors.textSecondary)
                }
            },
        )
    }
}
