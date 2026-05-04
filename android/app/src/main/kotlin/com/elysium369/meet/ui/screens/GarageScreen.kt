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
            TopAppBar(
                title = {
                    Column {
                        Text("Mi Garage", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "${vehicles.size} vehículo${if (vehicles.size != 1) "s" else ""}",
                            color = Color(0xFF39FF14).copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Text("←", color = Color(0xFF39FF14), style = MaterialTheme.typography.titleLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0E1A))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("vehicle_form") },
                containerColor = Color(0xFF0A0E1A),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.border(1.dp, Color(0xFF39FF14), RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.Add, contentDescription = "Añadir", tint = Color(0xFF39FF14))
            }
        },
        containerColor = Color(0xFF0A0E1A)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (vehicles.isEmpty()) {
                // Empty State
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🚗", style = MaterialTheme.typography.displayLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No tienes vehículos registrados", color = Color.Gray,
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Registra tu primer vehículo para empezar", color = Color.DarkGray,
                            style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { navController.navigate("vehicle_form") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0E1A)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.border(1.dp, Color(0xFF39FF14), RoundedCornerShape(8.dp))
                        ) {
                            Text("＋ AÑADIR VEHÍCULO", color = Color(0xFF39FF14), fontWeight = FontWeight.Bold)
                        }
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
                            color = Color(0xFF39FF14).copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(vehicles, key = { it.id }) { vehicle ->
                        AnimatedVehicleCard(
                            vehicle = vehicle,
                            isActive = vehicle.id == activeVehicle?.id,
                            onSelect = { viewModel.startDiagnosticSession(vehicle) },
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
                            color = Color(0xFFFF003C).copy(alpha = 0.7f),
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
        AlertDialog(
            onDismissRequest = { vehicleToDelete = null },
            containerColor = Color(0xFF0D1117),
            shape = RoundedCornerShape(16.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF003C).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF003C), modifier = Modifier.size(28.dp))
                }
            },
            title = {
                Text(
                    "¿Eliminar Vehículo?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column {
                    Text(
                        "${vehicle.make} ${vehicle.model} (${vehicle.year})",
                        color = Color(0xFFFF003C),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Esta acción eliminará el vehículo de tu garage local y de la nube. No se puede deshacer.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteVehicle(vehicle)
                        vehicleToDelete = null
                    }
                ) {
                    Text("ELIMINAR", color = Color(0xFFFF003C), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { vehicleToDelete = null }) {
                    Text("CANCELAR", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
private fun AnimatedVehicleCard(
    vehicle: Vehicle,
    isActive: Boolean,
    onSelect: () -> Unit,
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

    val borderColor = if (isActive) Color(0xFF39FF14) else Color(0xFF39FF14).copy(alpha = 0.15f)
    val glowBrush = if (isActive) {
        Brush.verticalGradient(
            listOf(Color(0xFF39FF14).copy(alpha = 0.05f), Color.Transparent)
        )
    } else {
        Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E1A)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = animatedOffset)
            .alpha(animatedAlpha)
            .border(if (isActive) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onSelect() }
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
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (isActive) {
                    Surface(
                        color = Color(0xFF39FF14).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.border(1.dp, Color(0xFF39FF14), RoundedCornerShape(4.dp))
                    ) {
                        Text(
                            "● ACTIVO",
                            color = Color(0xFF39FF14),
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
                        Text("VIN: ${vehicle.vin}", color = Color.DarkGray, style = MaterialTheme.typography.bodySmall)
                    }
                    if (vehicle.plate != "NOT_SET") {
                        Text("Placa: ${vehicle.plate}", color = Color.DarkGray, style = MaterialTheme.typography.bodySmall)
                    }
                    if (vehicle.engine != "N/A") {
                        Text(
                            vehicle.engine,
                            color = Color(0xFF4FC3F7).copy(alpha = 0.6f),
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
                        tint = Color(0xFFFF003C).copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (!isActive) {
                Text(
                    "Toca para activar →",
                    color = Color(0xFF39FF14).copy(alpha = 0.3f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
