package com.elysium369.meet.ui.screens.scanner

import androidx.compose.foundation.border
import androidx.compose.foundation.background
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
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteOutlinedButton
import com.elysium369.meet.ui.components.EliteIconButton

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
                color = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.5f), 
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
                if (isConnected) com.elysium369.meet.ui.theme.MeetColors.neonGreen else MeetColors.warning
            ) {
                if (!isConnected) {
                    navController.navigate("connect")
                }
            }
        }
        
        // AI Health Monitoring Toggle
        item {
            EliteCard(
                backgroundColor = MeetColors.backgroundDeep,
                shape = RoundedCornerShape(12.dp),
                borderColor = if (isAiMonitoring) com.elysium369.meet.ui.theme.MeetColors.neonGreen else com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Monitoreo de Salud IA", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.background(com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))) {
                                Text("BETA", color = com.elysium369.meet.ui.theme.MeetColors.neonGreen, modifier = Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text(
                            "Análisis continuo de telemetría en segundo plano para detección proactiva de fallas.",
                            color = MeetColors.textSecondary, 
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = isAiMonitoring,
                        onCheckedChange = { viewModel.toggleAiMonitoring(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                            checkedTrackColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }

        // High-Speed Mode Toggle
        item {
            EliteCard(
                backgroundColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark,
                shape = RoundedCornerShape(12.dp),
                borderColor = if (highSpeedMode) com.elysium369.meet.ui.theme.MeetColors.neonGreen else com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Modo Alta Velocidad (20Hz+)", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (isAdapterPro) "Optimizando para hardware profesional (STN/OBDLink)" 
                            else "Adaptador ELM327 detectado. Velocidad limitada por seguridad.",
                            color = MeetColors.textSecondary, 
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = highSpeedMode,
                        onCheckedChange = { viewModel.setHighSpeedMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                            checkedTrackColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }
        
        // HUD Mode
        item {
            ToolCard("🖥️", "HUD Mode", "Velocímetro para parabrisas", com.elysium369.meet.ui.theme.MeetColors.neonGreen) { onHudModeToggle(true) }
        }
        
        // Data Logging
        item {
            ToolCard(
                if (isLogging) "⏹️" else "⏺️", 
                if (isLogging) "Detener Grabación (${dataLog.size} pts)" else "Iniciar Grabación", 
                "Grabar datos de sensores en tiempo real (cada 500ms)", 
                com.elysium369.meet.ui.theme.MeetColors.error
            ) { 
                if (isLogging) viewModel.stopDataLogging() else viewModel.startDataLogging() 
            }
        }
        
        // CSV Export
        item {
            var exportResult by remember { mutableStateOf<String?>(null) }
            Column {
                ToolCard("📄", "Exportar CSV (${dataLog.size} puntos)", "Exportar datos grabados a archivo CSV", com.elysium369.meet.ui.theme.MeetColors.electricBlue) {
                    val path = viewModel.saveCsvToFile()
                    exportResult = if (path != null) "✅ Guardado en: $path" else "⚠️ No hay datos grabados. Inicia la grabación primero."
                }
                if (exportResult != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    EliteCard(
                        backgroundColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark, 
                        shape = RoundedCornerShape(8.dp), 
                        borderColor = com.elysium369.meet.ui.theme.MeetColors.electricBlue.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(exportResult.orEmpty(), color = com.elysium369.meet.ui.theme.MeetColors.neonGreen, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        
        // Fuel Economy
        item {
            val maf = liveData["0110"] ?: 0f
            val speed = liveData["010D"] ?: 0f
            val lPer100km = if (speed > 0 && maf > 0) (maf * 3600f) / (speed * 14.7f * 710f) * 100f else 0f
            EliteCard(
                backgroundColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark, 
                shape = RoundedCornerShape(12.dp), 
                borderColor = com.elysium369.meet.ui.theme.MeetColors.electricBlue.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("⛽ CONSUMO EN TIEMPO REAL", color = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${String.format("%.1f", lPer100km)} L/100km", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("MAF: ${String.format("%.1f", maf)} g/s • Speed: ${speed.toInt()} km/h", color = MeetColors.textMuted, style = MaterialTheme.typography.bodySmall)
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
                    val capturedStart = startTime
                    if (speed >= 100f && capturedStart != null) {
                        time0to100 = System.currentTimeMillis() - capturedStart
                        isTesting = false
                    }
                }
            }
            
            EliteCard(
                backgroundColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark, 
                shape = RoundedCornerShape(12.dp), 
                borderColor = MeetColors.warning.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("🏁 TEST 0-100 KM/H", color = MeetColors.warning.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        com.elysium369.meet.ui.components.EliteTextButton(
                            onClick = { 
                                isTesting = !isTesting
                                if (isTesting) { time0to100 = null; startTime = null }
                            }, 
                            text = if (isTesting) "CANCELAR" else "INICIAR",
                            color = MeetColors.warning
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val capturedResult = time0to100
                    val capturedStartDisplay = startTime
                    if (capturedResult != null) {
                        Text("${String.format("%.2f", capturedResult / 1000f)} segundos", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    } else if (isTesting) {
                        if (capturedStartDisplay != null) {
                            val currentRunTime = System.currentTimeMillis() - capturedStartDisplay
                            Text("${String.format("%.2f", currentRunTime / 1000f)} s...", color = MeetColors.warning, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                        } else {
                            Text("Acelera para comenzar...", color = MeetColors.textMuted, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Text("Presiona iniciar y acelera", color = MeetColors.textMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Speed: ${speed.toInt()} km/h", color = MeetColors.textMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        
        // Battery Health
        item {
            val voltage = liveData["AT RV"] ?: (liveData["0142"] ?: 0f)
            val status = when {
                voltage < 10f -> "BATERÍA MUERTA" to com.elysium369.meet.ui.theme.MeetColors.error
                voltage in 10f..11.8f -> "CARGA BAJA" to MeetColors.warning
                voltage in 11.9f..12.8f -> "BATERÍA OK (Motor apagado)" to com.elysium369.meet.ui.theme.MeetColors.neonGreen
                voltage in 12.9f..14.8f -> "ALTERNADOR OK (Cargando)" to com.elysium369.meet.ui.theme.MeetColors.neonGreen
                voltage > 14.8f -> "SOBRECARGA" to com.elysium369.meet.ui.theme.MeetColors.error
                else -> "LEYENDO..." to MeetColors.textMuted
            }
            EliteCard(
                backgroundColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark, 
                shape = RoundedCornerShape(12.dp), 
                borderColor = status.second.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
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
