package com.elysium369.meet.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.data.supabase.Vehicle
import kotlinx.coroutines.delay
import com.elysium369.meet.ui.components.EliteDialog
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageScreen(
    navController: NavController,
    viewModel: ObdViewModel
) {
    val activeVehicle by viewModel.selectedVehicle.collectAsState()
    val vehicles by viewModel.vehicles.collectAsState()
    val isDeleting by viewModel.isDeletingVehicle.collectAsState()

    // Confirmation dialog state
    var vehicleToDelete by remember { mutableStateOf<Vehicle?>(null) }

    Scaffold(
        topBar = {
            com.elysium369.meet.ui.components.EliteTopAppBar(
                title = "Mi Garage\n${vehicles.size} vehículo${if (vehicles.size != 1) "s" else ""}",
                onBackClick = { navController.popBackStack() },
                backgroundColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("vehicle_form") },
                containerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.border(1.dp, com.elysium369.meet.ui.theme.MeetColors.neonGreen, RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.Add, contentDescription = "Añadir", tint = com.elysium369.meet.ui.theme.MeetColors.neonGreen)
            }
        },
        containerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (vehicles.isEmpty()) {
                // Empty State
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🚗", style = MaterialTheme.typography.displayLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No tienes vehículos registrados", color = MeetColors.textSecondary,
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Registra tu primer vehículo para empezar", color = MeetColors.textMuted,
                            style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(24.dp))
                        com.elysium369.meet.ui.components.EliteButton(
                            onClick = { navController.navigate("vehicle_form") },
                            text = "＋ AÑADIR VEHÍCULO",
                            color = com.elysium369.meet.ui.theme.MeetColors.neonGreen
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            "${vehicles.size} VEHÍCULO${if (vehicles.size > 1) "S" else ""} REGISTRADO${if (vehicles.size > 1) "S" else ""}",
                            color = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(vehicles, key = { it.id }) { vehicle ->
                        AnimatedVehicleCard(
                            vehicle = vehicle,
                            isActive = vehicle.id == activeVehicle?.id,
                            onSelect = { viewModel.startDiagnosticSession(vehicle) },
                            onDetails = { navController.navigate("vehicle_detail/${vehicle.id}") },
                            onDelete = { vehicleToDelete = vehicle }
                        )
                    }
                    // Bottom spacer for FAB clearance
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }

            // ─── Deletion Animation Overlay ───
            AnimatedVisibility(
                visible = isDeleting,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        com.elysium369.meet.ui.components.EliteDeletionAnimation()
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            "ELIMINANDO VEHÍCULO...",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            "SINCRONIZANDO NUBE",
                            color = com.elysium369.meet.ui.theme.MeetColors.error.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }

    // ─── Delete Confirmation Dialog ───
    vehicleToDelete?.let { vehicle ->
        EliteDialog(
            title = "¿Eliminar Vehículo?",
            message = "${vehicle.make} ${vehicle.model} (${vehicle.year})\n\nEsta acción eliminará el vehículo de tu garage local y de la nube. No se puede deshacer.",
            onDismiss = { vehicleToDelete = null },
            onConfirm = {
                viewModel.deleteVehicle(vehicle)
                vehicleToDelete = null
            },
            confirmText = "ELIMINAR",
            isDestructive = true
        )
    }
}

@Composable
private fun AnimatedVehicleCard(
    vehicle: Vehicle,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit
) {
    // Entry animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(50)
        visible = true
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "card_alpha"
    )
    val animatedOffset by animateDpAsState(
        targetValue = if (visible) 0.dp else 24.dp,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "card_offset"
    )

    val borderColor = if (isActive) com.elysium369.meet.ui.theme.MeetColors.neonGreen else com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.15f)
    val glowBrush = if (isActive) {
        Brush.verticalGradient(
            listOf(com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.05f), Color.Transparent)
        )
    } else {
        Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
    }

    com.elysium369.meet.ui.components.EliteCard(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = animatedOffset)
            .alpha(animatedAlpha)
            .clickable { onSelect() },
        backgroundColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark,
        glowColor = if (isActive) com.elysium369.meet.ui.theme.MeetColors.neonGreen else Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .background(glowBrush)
                .padding(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${vehicle.make} ${vehicle.model}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${vehicle.year}",
                        color = MeetColors.textMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (isActive) {
                    Surface(
                        color = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.border(1.dp, com.elysium369.meet.ui.theme.MeetColors.neonGreen, RoundedCornerShape(4.dp))
                    ) {
                        Text(
                            "● ACTIVO",
                            color = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Vehicle info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (vehicle.vin != "NOT_READ") {
                        Text("VIN: ${vehicle.vin}", color = MeetColors.textMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    if (vehicle.plate != "NOT_SET") {
                        Text("Placa: ${vehicle.plate}", color = MeetColors.textMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    if (vehicle.engine != "N/A") {
                        Text(
                            vehicle.engine,
                            color = MeetColors.cyberCyan.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Delete button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = com.elysium369.meet.ui.theme.MeetColors.error.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (!isActive) {
                Text(
                    "Toca para activar →",
                    color = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.3f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onDetails,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.cyberCyan),
                border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("HISTORIAL DE SERVICIO", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
            }
        }
    }
}
