package com.elysium369.meet.ui.screens.scanner

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.ui.ObdViewModel

@Composable
fun ScannerToolsTab(
    viewModel: ObdViewModel,
    navController: NavController,
    onHudModeToggle: (Boolean) -> Unit
) {
    val liveData by viewModel.liveData.collectAsState()
    val state by viewModel.connectionState.collectAsState()
    val isLogging by viewModel.isLogging.collectAsState()
    val dataLog by viewModel.dataLog.collectAsState()
    val highSpeedMode by viewModel.highSpeedMode.collectAsState()
    val isAdapterPro by viewModel.isAdapterPro.collectAsState()
    val isAiMonitoring by viewModel.isAiMonitoring.collectAsState()
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(), 
        contentPadding = PaddingValues(16.dp), 
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { 
            Text(
                "HERRAMIENTAS PRO", 
                color = Color(0xFF00FFCC).copy(alpha = 0.5f), 
                style = MaterialTheme.typography.labelSmall, 
                fontWeight = FontWeight.Bold
            ) 
        }
        
        // Quick Connect
        item {
            val isConnected = state == ObdState.CONNECTED
            ToolCard(
                if (isConnected) "✅" else "🔌",
                if (isConnected) "Conexión Activa — Datos en tiempo real" else "Conectar Adaptador OBD2",
                if (isConnected) "Los datos de telemetría se actualizan desde la ECU del vehículo" else "Selecciona tu adaptador ELM327 para iniciar diagnóstico real",
                if (isConnected) Color(0xFF00FFCC) else Color(0xFFFFD700)
            ) {
                if (!isConnected) {
                    navController.navigate("connect")
                }
            }
        }
        
        // AI Health Monitoring Toggle
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF001A1A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().border(
                    1.dp, 
                    if (isAiMonitoring) Color(0xFF00FFCC) else Color(0xFF00FFCC).copy(alpha = 0.3f), 
                    RoundedCornerShape(12.dp)
                )
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Monitoreo de Salud IA", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(color = Color(0xFF00FFCC).copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                Text("BETA", color = Color(0xFF00FFCC), modifier = Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text(
                            "Análisis continuo de telemetría en segundo plano para detección proactiva de fallas.",
                            color = Color.Gray, 
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = isAiMonitoring,
                        onCheckedChange = { viewModel.toggleAiMonitoring(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00FFCC),
                            checkedTrackColor = Color(0xFF00FFCC).copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }

        // High-Speed Mode Toggle
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().border(
                    1.dp, 
                    if (highSpeedMode) Color(0xFF00FFCC) else Color(0xFF00FFCC).copy(alpha = 0.3f), 
                    RoundedCornerShape(12.dp)
                )
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Modo Alta Velocidad (20Hz+)", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (isAdapterPro) "Optimizando para hardware profesional (STN/OBDLink)" 
                            else "Adaptador ELM327 detectado. Velocidad limitada por seguridad.",
                            color = Color.Gray, 
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = highSpeedMode,
                        onCheckedChange = { viewModel.setHighSpeedMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00FFCC),
                            checkedTrackColor = Color(0xFF00FFCC).copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }
        
        // HUD Mode
        item {
            ToolCard("🖥️", "HUD Mode", "Velocímetro para parabrisas", Color(0xFF00FFCC)) { onHudModeToggle(true) }
        }
        
        // Data Logging
        item {
            ToolCard(
                if (isLogging) "⏹️" else "⏺️", 
                if (isLogging) "Detener Grabación (${dataLog.size} pts)" else "Iniciar Grabación", 
                "Grabar datos de sensores en tiempo real (cada 500ms)", 
                Color(0xFFFF003C)
            ) { 
                if (isLogging) viewModel.stopDataLogging() else viewModel.startDataLogging() 
            }
        }
        
        // CSV Export
        item {
            var exportResult by remember { mutableStateOf<String?>(null) }
            Column {
                ToolCard("📄", "Exportar CSV (${dataLog.size} puntos)", "Exportar datos grabados a archivo CSV", Color(0xFF00BFFF)) {
                    val path = viewModel.saveCsvToFile()
                    exportResult = if (path != null) "✅ Guardado en: $path" else "⚠️ No hay datos grabados. Inicia la grabación primero."
                }
                if (exportResult != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black), 
                        shape = RoundedCornerShape(8.dp), 
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF00FFCC).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    ) {
                        Text(exportResult!!, color = Color(0xFF00FFCC), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        
        // Fuel Economy
        item {
            val maf = liveData["0110"] ?: 0f
            val speed = liveData["010D"] ?: 0f
            val lPer100km = if (speed > 0 && maf > 0) (maf * 3600f) / (speed * 14.7f * 710f) * 100f else 0f
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black), 
                shape = RoundedCornerShape(12.dp), 
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF00FFCC).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("⛽ CONSUMO EN TIEMPO REAL", color = Color(0xFF00FFCC).copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${String.format("%.1f", lPer100km)} L/100km", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("MAF: ${String.format("%.1f", maf)} g/s • Speed: ${speed.toInt()} km/h", color = Color.DarkGray, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        
        // Performance Test 0-100
        item {
            val speed = liveData["010D"] ?: 0f
            var isTesting by remember { mutableStateOf(false) }
            var time0to100 by remember { mutableStateOf<Long?>(null) }
            var startTime by remember { mutableStateOf<Long?>(null) }
            
            // Auto-stop the timer if we hit 100
            LaunchedEffect(speed) {
                if (isTesting) {
                    if (speed > 0f && startTime == null) {
                        startTime = System.currentTimeMillis()
                    }
                    if (speed >= 100f && startTime != null) {
                        time0to100 = System.currentTimeMillis() - startTime!!
                        isTesting = false
                    }
                }
            }
            
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black), 
                shape = RoundedCornerShape(12.dp), 
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFFFD700).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("🏁 TEST 0-100 KM/H", color = Color(0xFFFFD700).copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = { 
                                isTesting = !isTesting
                                if (isTesting) { time0to100 = null; startTime = null }
                            }, 
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0A0A)), 
                            modifier = Modifier.border(1.dp, Color(0xFFFFD700), RoundedCornerShape(8.dp)), 
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (isTesting) "CANCELAR" else "INICIAR", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (time0to100 != null) {
                        Text("${String.format("%.2f", time0to100!! / 1000f)} segundos", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    } else if (isTesting) {
                        if (startTime != null) {
                            val currentRunTime = System.currentTimeMillis() - startTime!!
                            Text("${String.format("%.2f", currentRunTime / 1000f)} s...", color = Color(0xFFFFD700), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                        } else {
                            Text("Acelera para comenzar...", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Text("Presiona iniciar y acelera", color = Color.DarkGray, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Speed: ${speed.toInt()} km/h", color = Color.DarkGray, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        
        // Battery Health
        item {
            val voltage = liveData["AT RV"] ?: (liveData["0142"] ?: 0f)
            val status = when {
                voltage < 10f -> "BATERÍA MUERTA" to Color(0xFFFF003C)
                voltage in 10f..11.8f -> "CARGA BAJA" to Color(0xFFFFD700)
                voltage in 11.9f..12.8f -> "BATERÍA OK (Motor apagado)" to Color(0xFF00FFCC)
                voltage in 12.9f..14.8f -> "ALTERNADOR OK (Cargando)" to Color(0xFF00FFCC)
                voltage > 14.8f -> "SOBRECARGA" to Color(0xFFFF003C)
                else -> "LEYENDO..." to Color.Gray
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black), 
                shape = RoundedCornerShape(12.dp), 
                modifier = Modifier.fillMaxWidth().border(1.dp, status.second.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🔋 SALUD DE BATERÍA", color = status.second.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${String.format("%.1f", voltage)} V", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text(status.first, color = status.second, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
