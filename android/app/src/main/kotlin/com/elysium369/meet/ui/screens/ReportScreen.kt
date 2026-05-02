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

import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
            TopAppBar(
                title = { Text("Reportes Pre/Post Scan", color = Color.White) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Text("←", color = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0E1A))
            )
        },
        containerColor = Color(0xFF0A0E1A)
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            
            if (isGenerating) {
                CircularProgressIndicator(color = Color(0xFF39FF14))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Generando Reporte MEET ELITE...", color = Color(0xFF39FF14), fontWeight = FontWeight.Bold)
                Text("Compilando telemetría de alta fidelidad y DTCs", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            } else if (reportFile != null) {
                Text("✅ REPORTE GENERADO", color = Color(0xFF39FF14), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(16.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E1A)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF00AAFF).copy(alpha = 0.3f), RoundedCornerShape(12.dp))) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(reportFile?.name ?: "Report.pdf", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Guardado en /Downloads", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { reportFile?.let { generator.shareReport(it) } }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0E1A)), modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF39FF14), RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp)) {
                            Text("COMPARTIR POR WHATSAPP / CORREO", color = Color(0xFF39FF14), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(onClick = { reportFile = null }) {
                    Text("Generar otro reporte", color = Color.Gray)
                }
            } else {
                Text("📄", style = MaterialTheme.typography.displayLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Diagnóstico MEET ELITE", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Genera un reporte PDF de grado profesional con telemetría de alta resolución, escaneo profundo de módulos (${activeDtcs.size} DTCs) y análisis de rendimiento en tiempo real.", color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF39FF14), contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("GENERAR REPORTE ELITE (PDF)", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
