package com.elysium369.meet.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.elysium369.meet.ui.theme.MeetColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elysium369.meet.data.local.entities.TripEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteOutlinedButton

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
            EliteTopAppBar(
                title = "DETALLE DEL VIAJE",
                onBackClick = onBack,
                backgroundColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark
            )
        },
        containerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDeep
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                EliteCard(
                    backgroundColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Text("Fecha: ${sdf.format(Date(trip.startedAt))}", color = MeetColors.warning, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Duración: $durationMin minutos", color = Color.LightGray)
                        Text("Distancia: ${String.format("%.1f", trip.distanceKm)} km", color = Color.LightGray)
                    }
                }
            }
            
            item {
                EliteCard(
                    backgroundColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Text("Rendimiento", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Velocidad Máxima", color = MeetColors.textSecondary)
                                Text("${String.format("%.1f", trip.maxSpeedKmh)} km/h", color = Color.White)
                            }
                            Column {
                                Text("Velocidad Media", color = MeetColors.textSecondary)
                                Text("${String.format("%.1f", trip.avgSpeedKmh)} km/h", color = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("RPM Máximo", color = MeetColors.textSecondary)
                                Text("${trip.maxRpm.toInt()} RPM", color = Color.White)
                            }
                            Column {
                                Text("Temp Máxima", color = MeetColors.textSecondary)
                                Text("${String.format("%.1f", trip.maxTempC)} °C", color = if (trip.maxTempC > 105) com.elysium369.meet.ui.theme.MeetColors.error else Color.White)
                            }
                        }
                    }
                }
            }
            
            item {
                EliteCard(
                    backgroundColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Eco-Score", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("${trip.ecoScore}/100", color = if (trip.ecoScore > 80) com.elysium369.meet.ui.theme.MeetColors.neonGreen else com.elysium369.meet.ui.theme.MeetColors.warning, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("El Eco-Score evalúa la suavidad de conducción, penalizando aceleraciones bruscas y excesos de velocidad.", color = MeetColors.textSecondary)
                    }
                }
            }
            
            item {
                EliteOutlinedButton(
                    text = "Exportar PDF",
                    onClick = onExportPdf,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
