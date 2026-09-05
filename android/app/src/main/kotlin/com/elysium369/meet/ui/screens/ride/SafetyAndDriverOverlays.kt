package com.elysium369.meet.ui.screens.ride

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.elysium369.meet.ui.theme.MeetColors

@Composable
fun SafetyCenterOverlay(
    ride: ActiveRideViewState?,
    onDismiss: () -> Unit,
    onShareTrip: () -> Unit,
    onSOS: () -> Unit,
    onGuardian: () -> Unit,
) {
    var showSosDialog by remember { mutableStateOf(false) }

    if (showSosDialog) {
        SOSConfirmDialog(
            onConfirm = {
                showSosDialog = false
                onSOS()
            },
            onDismiss = { showSosDialog = false }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {}
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                border = BorderStroke(1.dp, MeetColors.error.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Centro de Seguridad",
                            style = MaterialTheme.typography.titleLarge,
                            color = MeetColors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MeetColors.textSecondary)
                        }
                    }

                    // 911 SOS Button
                    Button(
                        onClick = { showSosDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.error, contentColor = Color.White)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Emergency, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("LLAMAR AL 911 / ALERTA SOS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        }
                    }

                    HorizontalDivider(color = MeetColors.borderSubtle, thickness = 1.dp)

                    // Safety Actions
                    SafetyActionItem(
                        icon = Icons.Default.Share,
                        title = "Compartir viaje en vivo",
                        subtitle = "Envía tu ubicación y ruta en tiempo real a tus contactos",
                        tint = MeetColors.neonGreen,
                        onClick = onShareTrip
                    )

                    SafetyActionItem(
                        icon = Icons.Default.Security,
                        title = "Protección Guardian MEET",
                        subtitle = "Monitoreo activo de desviación de ruta y paradas no planeadas",
                        tint = MeetColors.electricBlue,
                        onClick = onGuardian
                    )

                    SafetyActionItem(
                        icon = Icons.Default.ReportProblem,
                        title = "Reportar incidente en ruta",
                        subtitle = "Informa sobre bloqueos, accidentes o situaciones de riesgo",
                        tint = Color(0xFFFFB74D),
                        onClick = { /* Report incident */ }
                    )
                }
            }
        }
    }
}

@Composable
private fun SafetyActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = tint.copy(alpha = 0.15f), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MeetColors.textPrimary, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun SOSConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("¿Transmitir Emergencia 911?", fontWeight = FontWeight.Bold, color = MeetColors.error)
        },
        text = {
            Text(
                "Se contactará de inmediato al servicio de emergencias 911 y se transmitirá tu posición GPS, matrícula del vehículo y perfil de conductor/pasajero a la central de seguridad.",
                color = MeetColors.textPrimary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.error, contentColor = Color.White)
            ) {
                Text("SÍ, LLAMAR AL 911", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
        containerColor = MeetColors.backgroundDeep
    )
}

/**
 * DriverTurnByTurnNavigationOverlay — Turn-by-turn in-app navigation overlay (Item 7).
 */
@Composable
fun DriverTurnByTurnNavigationOverlay(
    nextManeuver: String = "Gire a la derecha en Av. Central",
    distanceMeters: Int = 180,
    speedKmh: Float = 42f,
    speedLimitKmh: Int = 50,
    etaMinutes: Int = 7,
    remainingKm: Double = 2.8,
    onCloseNavigation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Turn Instruction Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep.copy(alpha = 0.95f)),
            border = BorderStroke(1.dp, MeetColors.neonGreen)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MeetColors.neonGreen.copy(alpha = 0.2f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Default.Navigation, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(28.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "En ${distanceMeters}m",
                        style = MaterialTheme.typography.labelMedium,
                        color = MeetColors.neonGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = nextManeuver,
                        style = MaterialTheme.typography.titleMedium,
                        color = MeetColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Bottom Navigation Strip & Speedometer
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep.copy(alpha = 0.95f)),
            border = BorderStroke(1.dp, MeetColors.borderSubtle)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speedometer
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (speedKmh > speedLimitKmh) MeetColors.error.copy(alpha = 0.2f) else MeetColors.cardBackground,
                        border = BorderStroke(2.dp, if (speedKmh > speedLimitKmh) MeetColors.error else MeetColors.neonGreen),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "${speedKmh.toInt()}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black,
                                color = if (speedKmh > speedLimitKmh) MeetColors.error else MeetColors.textPrimary
                            )
                        }
                    }
                    Column {
                        Text("km/h", style = MaterialTheme.typography.labelSmall, color = MeetColors.textSecondary)
                        Text("Límite: $speedLimitKmh", style = MaterialTheme.typography.labelSmall, color = MeetColors.textMuted, fontSize = 9.sp)
                    }
                }

                // ETA & Distance
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$etaMinutes min", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MeetColors.neonGreen)
                    Text("%.1f km restantes".format(remainingKm), style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                }

                IconButton(
                    onClick = onCloseNavigation,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Salir de navegación", tint = MeetColors.textSecondary)
                }
            }
        }
    }
}

@Composable
fun DriverEarningsBottomSheet(
    todayEarnings: Long = 45600L,
    weekEarnings: Long = 187500L,
    monthEarnings: Long = 723400L,
    tripsToday: Int = 8,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {}
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                border = BorderStroke(1.dp, MeetColors.borderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ganancias del Conductor", style = MaterialTheme.typography.titleLarge, color = MeetColors.textPrimary, fontWeight = FontWeight.Bold)
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MeetColors.textSecondary)
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                        border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Hoy", style = MaterialTheme.typography.labelMedium, color = MeetColors.textSecondary)
                            Text("₡${todayEarnings.toString().replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1,")}", style = MaterialTheme.typography.headlineMedium, color = MeetColors.neonGreen, fontWeight = FontWeight.Bold)
                            Text("$tripsToday viajes completados", style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Esta semana", style = MaterialTheme.typography.labelSmall, color = MeetColors.textSecondary)
                                Text("₡${weekEarnings.toString().replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1,")}", style = MaterialTheme.typography.titleSmall, color = MeetColors.textPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                        Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Este mes", style = MaterialTheme.typography.labelSmall, color = MeetColors.textSecondary)
                                Text("₡${monthEarnings.toString().replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1,")}", style = MaterialTheme.typography.titleSmall, color = MeetColors.textPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DriverSettingsBottomSheet(
    isOnline: Boolean,
    onToggleOnline: () -> Unit,
    onDismiss: () -> Unit,
) {
    var autoAccept by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {}
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                border = BorderStroke(1.dp, MeetColors.borderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ajustes de Conducción", style = MaterialTheme.typography.titleLarge, color = MeetColors.textPrimary, fontWeight = FontWeight.Bold)
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MeetColors.textSecondary)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Aceptación Automática", style = MaterialTheme.typography.titleSmall, color = MeetColors.textPrimary, fontWeight = FontWeight.SemiBold)
                            Text("Acepta viajes dentro de un radio de 3km", style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                        }
                        Switch(checked = autoAccept, onCheckedChange = { autoAccept = it })
                    }
                }
            }
        }
    }
}
