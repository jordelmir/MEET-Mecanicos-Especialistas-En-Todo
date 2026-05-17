package com.elysium369.meet.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.core.export.ReportGenerator
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteOutlinedButton
import com.elysium369.meet.ui.theme.MeetColors

import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReportScreen(navController: NavController, viewModel: ObdViewModel) {
    var isGenerating by remember { mutableStateOf(false) }
    var reportFile by remember { mutableStateOf<File?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val generator = remember { ReportGenerator(context) }
    
    val activeDtcs by viewModel.activeDtcs.collectAsState()

    Scaffold(
        topBar = {
            EliteTopAppBar(
                title = "REPORTES PRE/POST SCAN",
                onBackClick = { navController.popBackStack() },
                backgroundColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark
            )
        },
        containerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            
            if (isGenerating) {
                CircularProgressIndicator(color = com.elysium369.meet.ui.theme.MeetColors.neonGreen)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Generando Reporte MEET ELITE...", color = com.elysium369.meet.ui.theme.MeetColors.neonGreen, fontWeight = FontWeight.Bold)
                Text("Compilando telemetría de alta fidelidad y DTCs", color = MeetColors.textSecondary, style = MaterialTheme.typography.bodySmall)
            } else if (reportFile != null) {
                Text("✅ REPORTE GENERADO", color = com.elysium369.meet.ui.theme.MeetColors.neonGreen, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(16.dp))
                EliteCard(backgroundColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark, borderColor = com.elysium369.meet.ui.theme.MeetColors.electricBlue.copy(alpha = 0.3f), glowColor = com.elysium369.meet.ui.theme.MeetColors.electricBlue, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(reportFile?.name ?: "Report.pdf", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Guardado en /Downloads", color = MeetColors.textSecondary, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(16.dp))
                        EliteOutlinedButton(text = "COMPARTIR POR WHATSAPP / CORREO", onClick = { reportFile?.let { generator.shareReport(it) } }, color = com.elysium369.meet.ui.theme.MeetColors.neonGreen, modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(onClick = { reportFile = null }) {
                    Text("Generar otro reporte", color = MeetColors.textSecondary)
                }
            } else {
                Text("📄", style = MaterialTheme.typography.displayLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Diagnóstico MEET ELITE", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Genera un reporte PDF de grado profesional con telemetría de alta resolución, escaneo profundo de módulos (${activeDtcs.size} DTCs) y análisis de rendimiento en tiempo real.", color = MeetColors.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(modifier = Modifier.height(32.dp))
                
                EliteButton(
                    text = "GENERAR REPORTE ELITE (PDF)",
                    onClick = { 
                        coroutineScope.launch {
                            isGenerating = true
                            val generatedFile = withContext(Dispatchers.IO) {
                                val currentTripEntity = viewModel.getCurrentTrip()
                                val currentTrip = if (currentTripEntity != null) {
                                    com.elysium369.meet.data.supabase.Trip(
                                        id = currentTripEntity.id,
                                        user_id = "guest",
                                        vehicle_id = currentTripEntity.vehicleId,
                                        session_id = currentTripEntity.sessionId,
                                        started_at = currentTripEntity.startedAt,
                                        ended_at = currentTripEntity.endedAt ?: System.currentTimeMillis(),
                                        distance_km = currentTripEntity.distanceKm,
                                        duration_seconds = currentTripEntity.durationSeconds,
                                        avg_speed_kmh = currentTripEntity.avgSpeedKmh,
                                        max_speed_kmh = currentTripEntity.maxSpeedKmh,
                                        max_rpm = currentTripEntity.maxRpm,
                                        avg_rpm = currentTripEntity.avgRpm,
                                        max_temp_c = currentTripEntity.maxTempC,
                                        fuel_efficiency = currentTripEntity.fuelEfficiency,
                                        eco_score = currentTripEntity.ecoScore,
                                        gps_track_json = currentTripEntity.gpsTrackJson
                                    )
                                } else {
                                    com.elysium369.meet.data.supabase.Trip(
                                        id = System.currentTimeMillis().toString(),
                                        user_id = "guest",
                                        vehicle_id = viewModel.selectedVehicle.value?.id ?: "unknown",
                                        session_id = "temp",
                                        started_at = System.currentTimeMillis(),
                                        ended_at = System.currentTimeMillis(),
                                        distance_km = 0f,
                                        duration_seconds = 0,
                                        avg_speed_kmh = 0f,
                                        max_speed_kmh = viewModel.liveData.value["010D"] ?: 0f,
                                        max_rpm = viewModel.liveData.value["010C"] ?: 0f,
                                        avg_rpm = 0f,
                                        max_temp_c = viewModel.liveData.value["0105"] ?: 0f,
                                        fuel_efficiency = 0f,
                                        eco_score = 0,
                                        gps_track_json = null
                                    )
                                }
                                val vehicleInfo = viewModel.selectedVehicle.value?.let {
                                    "${it.year} ${it.make} ${it.model} — VIN: ${it.vin}"
                                } ?: "Vehículo sin identificar"
                                generator.generatePdfReport(
                                    trip = currentTrip,
                                    dtcs = activeDtcs,
                                    aiAnalysis = if (activeDtcs.isEmpty()) "Sin códigos de falla activos." else "Se encontraron ${activeDtcs.size} códigos de falla.",
                                    vehicleDetails = vehicleInfo
                                )
                            }
                            isGenerating = false
                            reportFile = generatedFile
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                )
            }
        }
    }
}
