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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ContactSupport
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors

@Composable
fun SafetyCenterOverlay(
    ride: ActiveRideViewState?,
    onDismiss: () -> Unit,
    onShareTrip: () -> Unit,
    onSOS: () -> Unit,
    onGuardian: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
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
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Centro de Seguridad", style = MaterialTheme.typography.headlineSmall, color = MeetColors.textPrimary, fontWeight = FontWeight.Bold)
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MeetColors.textSecondary)
                        }
                    }

                    // SOS Button - Prominent
                    Button(
                        onClick = onSOS,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MeetColors.error, contentColor = Color.White)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Emergency, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("LLAMAR AL 911 / SOS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }

                    Divider(color = MeetColors.outlineVariant, thickness = 1.dp)

                    // Safety actions grid
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SafetyActionCard(
                            icon = { Icons.Default.Share },
                            title = "Compartir viaje",
                            subtitle = "Enviar tu ubicación en tiempo real a contactos de confianza",
                            color = MeetColors.neonGreen,
                            onClick = onShareTrip
                        )

                        SafetyActionCard(
                            icon = { Icons.Default.Shield },
                            title = "Señales de Guardián",
                            subtitle = "Reportar: ruta inusual, parada larga, verificación PIN, emergencia",
                            color = MeetColors.electricBlue,
                            onClick = onGuardian
                        )

                        SafetyActionCard(
                            icon = { Icons.Default.Phone },
                            title = "Contactos de emergencia",
                            subtitle = "Llamar a contactos predefinidos con un toque",
                            color = MeetColors.electricPurple,
                            onClick = { /* Open emergency contacts */ }
                        )

                        SafetyActionCard(
                            icon = { Icons.Default.Info },
                            title = "Detalles del conductor",
                            subtitle = "Ver nombre, placa, vehículo, calificación y foto",
                            color = MeetColors.electricBlue.copy(alpha = 0.8f),
                            onClick = { /* Show driver profile */ }
                        )

                        SafetyActionCard(
                            icon = { Icons.Default.Description },
                            title = "Verificar PIN de abordaje",
                            subtitle = "Confirmar el código de 4 dígitos antes de subir",
                            color = MeetColors.electricPurple.copy(alpha = 0.8f),
                            onClick = { /* Show PIN verification */ }
                        )

                        SafetyActionCard(
                            icon = { Icons.Default.ContactSupport },
                            title = "Soporte 24/7",
                            subtitle = "Chatear con nuestro equipo de seguridad",
                            color = MeetColors.textSecondary,
                            onClick = { /* Open support chat */ }
                        )
                    }

                    // Ride sharing status (if active)
                    ride?.let { r ->
                        Divider(color = MeetColors.outlineVariant, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                        if (r.state.isActive) {
                            ShareTripStatusCard(
                                isSharing = false, // TODO: track sharing state
                                onToggleShare = { /* Toggle share */ },
                                sharedWith = emptyList(), // TODO: from ride
                            )
                        }
                    }

                    // Safety tip
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.neonGreen.copy(alpha = 0.08f)),
                        border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, tint = MeetColors.neonGreen)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Consejo de seguridad", style = MaterialTheme.typography.labelMedium, color = MeetColors.neonGreen, fontWeight = FontWeight.Bold)
                                Text("Siempre verifica la placa y el nombre del conductor antes de subir. Comparte tu viaje con alguien de confianza.", style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun SafetyActionCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.12f),
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MeetColors.textPrimary, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary, maxLines = 2)
            }
            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MeetColors.textSecondary)
        }
    }
}

@Composable
fun ShareTripStatusCard(
    isSharing: Boolean,
    onToggleShare: () -> Unit,
    sharedWith: List<String>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.electricBlue.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, MeetColors.electricBlue.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (isSharing) MeetColors.electricBlue else MeetColors.outlineVariant,
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = if (isSharing) Color.White else MeetColors.textSecondary, modifier = Modifier.padding(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(if (isSharing) "Compartiendo viaje en vivo" : "Viaje no compartido", style = MaterialTheme.typography.titleMedium, color = if (isSharing) MeetColors.electricBlue else MeetColors.textSecondary, fontWeight = FontWeight.Medium)
                Text(if (isSharing) "${sharedWith.size} contactos ven tu ubicación" : "Toca para compartir tu ubicación en tiempo real", style = MaterialTheme.typography.bodySmall, color = MeetColors.textSecondary)
            }
            androidx.compose.material3.Switch(
                checked = isSharing,
                onCheckedChange = { onToggleShare() },
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = MeetColors.electricBlue, checkedTrackColor = MeetColors.electricBlue.copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
fun DriverProfileOverlay(
    driver: MatchedDriver,
    onDismiss: () -> Unit,
    onCall: () -> Unit,
    onMessage: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
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
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Perfil del Conductor", style = MaterialTheme.typography.headlineSmall, color = MeetColors.textPrimary, fontWeight = FontWeight.Bold)
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MeetColors.textSecondary)
                        }
                    }

                    // Avatar & Name
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = MeetColors.neonGreen.copy(alpha = 0.12f),
                            modifier = Modifier.size(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(driver.name.take(1).uppercase(), style = MaterialTheme.typography.displaySmall, color = MeetColors.neonGreen, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(driver.name, style = MaterialTheme.typography.headlineMedium, color = MeetColors.textPrimary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RatingStars(rating = driver.rating, size = 20.sp)
                            Spacer(Modifier.width(8.dp))
                            Text("${driver.rating}", style = MaterialTheme.typography.titleMedium, color = MeetColors.textPrimary, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text("(${driver.totalTrips} viajes)", style = MaterialTheme.typography.bodyMedium, color = MeetColors.textSecondary)
                        }
                    }

                    Divider(color = MeetColors.outlineVariant)

                    // Vehicle info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ProfileInfoItem(
                            icon = { Icons.Default.DirectionsCar },
                            label = "Vehículo",
                            value = driver.vehicle,
                            color = MeetColors.neonGreen
                        )
                        ProfileInfoItem(
                            icon = { Icons.Default.DirectionsCar },
                            label = "Placa",
                            value = driver.plate,
                            color = MeetColors.electricBlue
                        )
                    }

                    // Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatChip(label = "Viajes", value = driver.totalTrips.toString(), color = MeetColors.neonGreen)
                        StatChip(label = "Calificación", value = String.format("%.1f", driver.rating), color = MeetColors.electricBlue)
                        StatChip(label = "Años", value = "3+", color = MeetColors.electricPurple)
                    }

                    // Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onCall,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen, contentColor = Color.Black)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = Color.Black)
                                Spacer(Modifier.width(8.dp))
                                Text("Llamar", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                        OutlinedButton(
                            onClick = onMessage,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Message, contentDescription = null, tint = MeetColors.electricBlue)
                                Spacer(Modifier.width(8.dp))
                                Text("Mensaje", style = MaterialTheme.typography.labelLarge, color = MeetColors.electricBlue)
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun ProfileInfoItem(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    color: Color,
) {
    Card(
        modifier = Modifier
            .weight(1f)
            .height(80.dp),
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
            Text(value, style = MaterialTheme.typography.titleMedium, color = MeetColors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun StatChip(
    label: String,
    value: String,
    color: Color,
) {
    Card(
        modifier = Modifier
            .weight(1f)
            .height(60.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.Center
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}