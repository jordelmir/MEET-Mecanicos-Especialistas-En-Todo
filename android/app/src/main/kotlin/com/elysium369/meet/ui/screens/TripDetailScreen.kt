package com.elysium369.meet.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
fun TripDetailScreen(
    trip: TripEntity,
    onBack: () -> Unit,
    onExportPdf: () -> Unit
) {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val durationMin = trip.durationSeconds / 60

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle del Viaje", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0E1A))
            )
        },
        containerColor = Color(0xFF060612)
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E1A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Text("Fecha: ${sdf.format(Date(trip.startedAt))}", color = Color(0xFFFF6B35), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Duración: $durationMin minutos", color = Color.LightGray)
                        Text("Distancia: ${String.format("%.1f", trip.distanceKm)} km", color = Color.LightGray)
                    }
                }
            }
            
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E1A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Text("Rendimiento", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Velocidad Máxima", color = Color.Gray)
                                Text("${String.format("%.1f", trip.maxSpeedKmh)} km/h", color = Color.White)
                            }
                            Column {
                                Text("Velocidad Media", color = Color.Gray)
                                Text("${String.format("%.1f", trip.avgSpeedKmh)} km/h", color = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("RPM Máximo", color = Color.Gray)
                                Text("${trip.maxRpm.toInt()} RPM", color = Color.White)
                            }
                            Column {
                                Text("Temp Máxima", color = Color.Gray)
                                Text("${String.format("%.1f", trip.maxTempC)} °C", color = if (trip.maxTempC > 105) Color.Red else Color.White)
                            }
                        }
                    }
                }
            }
            
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E1A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Eco-Score", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("${trip.ecoScore}/100", color = if (trip.ecoScore > 80) Color.Green else Color.Yellow, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("El Eco-Score evalúa la suavidad de conducción, penalizando aceleraciones bruscas y excesos de velocidad.", color = Color.Gray)
                    }
                }
            }
            
            item {
                Button(
                    onClick = onExportPdf,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Exportar PDF", color = Color.White)
                }
            }
        }
    }
}
