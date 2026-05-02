package com.elysium369.meet.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.data.supabase.Vehicle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageScreen(
    navController: NavController,
    viewModel: ObdViewModel
) {
    val activeVehicle by viewModel.selectedVehicle.collectAsState()
    val vehicles by viewModel.vehicles.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi Garage", color = Color.White, fontWeight = FontWeight.Bold) },
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
        if (vehicles.isEmpty()) {
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
                    Text("${vehicles.size} VEHÍCULO${if (vehicles.size > 1) "S" else ""} REGISTRADO${if (vehicles.size > 1) "S" else ""}",
                        color = Color(0xFF39FF14).copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold)
                }
                items(vehicles) { vehicle ->
                    val isActive = vehicle.id == activeVehicle?.id
                    val borderColor = if (isActive) Color(0xFF39FF14) else Color(0xFF39FF14).copy(alpha = 0.15f)
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E1A)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(if (isActive) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
                            .clickable { viewModel.startDiagnosticSession(vehicle) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
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
                                        style = MaterialTheme.typography.titleMedium
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
                            Text("VIN: ${vehicle.vin}", color = Color.DarkGray, style = MaterialTheme.typography.bodySmall)
                            
                            if (!isActive) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Toca para activar →",
                                    color = Color(0xFF39FF14).copy(alpha = 0.4f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
