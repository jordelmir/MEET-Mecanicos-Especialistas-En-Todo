package com.elysium369.meet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elysium369.meet.core.trips.Trip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripScreen(
    trips: List<Trip>,
    isPremium: Boolean,
    onExportPdf: (Trip) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial de Viajes", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E))
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        if (trips.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay viajes registrados aún.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!isPremium) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⭐", style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Funciones Pro Bloqueadas", color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("Exportación PDF, consumo de L/100km, y puntuación Eco-Score requieren suscripción.", 
                                        color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                items(trips.sortedByDescending { it.startTime }) { trip ->
                    TripCard(trip, isPremium, onExportPdf)
                }
            }
        }
    }
}

@Composable
fun TripCard(trip: Trip, isPremium: Boolean, onExportPdf: (Trip) -> Unit) {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val durationMin = trip.durationMs / 60000

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(sdf.format(Date(trip.startTime)), color = Color(0xFFFF6B35), fontWeight = FontWeight.Bold)
                if (isPremium) {
                    Text("Eco: ${trip.ecoScore}/100", color = if (trip.ecoScore > 80) Color.Green else Color.Yellow, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TripStatBox("Duración", "$durationMin min")
                TripStatBox("Max Vel", "${String.format("%.1f", trip.maxSpeed)} km/h")
                TripStatBox("Max Temp", "${String.format("%.1f", trip.maxTemp)} °C")
            }
            if (isPremium) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onExportPdf(trip) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📄 Exportar Reporte PDF", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun TripStatBox(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(label, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
    }
}
