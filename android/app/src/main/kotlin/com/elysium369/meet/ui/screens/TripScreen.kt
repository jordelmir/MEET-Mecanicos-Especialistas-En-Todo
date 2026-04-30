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
import com.elysium369.meet.data.local.entities.TripEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripScreen(
    trips: List<TripEntity>,
    isPremium: Boolean,
    onExportPdf: (TripEntity) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial de Viajes", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0A))
            )
        },
        containerColor = Color.Black
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
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⭐", style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Funciones Pro Bloqueadas", color = Color(0xFF00BFFF), fontWeight = FontWeight.Bold)
                                    Text("Exportación PDF, consumo de L/100km, y puntuación Eco-Score requieren suscripción.", 
                                        color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                items(trips.sortedByDescending { it.startedAt }) { trip ->
                    TripCard(trip, isPremium, onExportPdf)
                }
            }
        }
    }
}

@Composable
fun TripCard(trip: TripEntity, isPremium: Boolean, onExportPdf: (TripEntity) -> Unit) {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val durationMin = trip.durationSeconds / 60

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(sdf.format(Date(trip.startedAt)), color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold)
                if (isPremium) {
                    Text("Eco: ${trip.ecoScore}/100", color = if (trip.ecoScore > 80) Color(0xFF00FFCC) else Color.Yellow, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TripStatBox("Duración", "$durationMin min")
                TripStatBox("Max Vel", "${String.format("%.1f", trip.maxSpeedKmh)} km/h")
                TripStatBox("Max Temp", "${String.format("%.1f", trip.maxTempC)} °C")
            }
            if (isPremium) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onExportPdf(trip) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC).copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📄 Exportar Reporte PDF", color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold)
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
