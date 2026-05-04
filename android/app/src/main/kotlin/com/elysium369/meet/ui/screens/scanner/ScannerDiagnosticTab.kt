package com.elysium369.meet.ui.screens.scanner

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.ui.components.EliteScrollContainer
import com.elysium369.meet.ui.components.eliteScrollbar
import kotlinx.coroutines.launch

@Composable
fun ScannerDiagnosticTab(
    viewModel: ObdViewModel,
    snackbarHostState: SnackbarHostState
) {
    val state by viewModel.connectionState.collectAsState()
    val activeDtcs by viewModel.activeDtcs.collectAsState()
    val pendingDtcs by viewModel.pendingDtcs.collectAsState()
    val permanentDtcs by viewModel.permanentDtcs.collectAsState()
    val readinessMonitors by viewModel.readinessMonitors.collectAsState()
    val vin by viewModel.vin.collectAsState()
    val alerts by viewModel.maintenanceAlerts.collectAsState()
    val odometer by viewModel.currentOdometer.collectAsState()
    
    val coroutineScope = rememberCoroutineScope()
    var isScanningModules by remember { mutableStateOf(false) }
    var detectedModules by remember { mutableStateOf<List<Pair<String, Boolean>>>(emptyList()) }
    var aiAnalysisResult by remember { mutableStateOf<String?>(null) }
    var isAnalyzingAi by remember { mutableStateOf(false) }

    val isScanning by viewModel.isScanning.collectAsState()
    val isClearing by viewModel.isClearing.collectAsState()
    val syncStatus by viewModel.cloudSyncState.collectAsState()

    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        EliteScrollContainer(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .eliteScrollbar(listState), 
                contentPadding = PaddingValues(16.dp), 
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
        // Topology Section
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("TOPOLOGÍA DE RED", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                isScanningModules = true
                                detectedModules = viewModel.scanModules()
                                isScanningModules = false
                            }
                        },
                        enabled = state == ObdState.CONNECTED && !isScanningModules
                    ) {
                        if (isScanningModules) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF39FF14))
                        } else {
                            Text("ESCANEAR SISTEMAS", color = Color(0xFF39FF14), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                
                if (detectedModules.isEmpty() && !isScanningModules) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E1A)), modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(12.dp))) {
                        Text("No se han escaneado módulos aún. Inicia un escaneo completo para detectar el estado de cada sistema (Motor, Transmisión, ABS, etc).", color = Color.Gray, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 100.dp),
                        modifier = Modifier.height(180.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(detectedModules) { (name, ok) ->
                            val color = if (ok) Color(0xFF39FF14) else Color(0xFFFF003C)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E1A)),
                                modifier = Modifier.border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            ) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(name, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(if (ok) "ONLINE" else "ERROR", color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }
        }

        // VIN Section
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E1A)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF39FF14).copy(alpha = 0.2f), RoundedCornerShape(12.dp))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("IDENTIFICACIÓN DEL VEHÍCULO (VIN)", color = Color(0xFF39FF14).copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(vin ?: "Leyendo VIN...", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                }
            }
        }

        // DTC Summary
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DtcStatCard("ACTIVOS", activeDtcs.size, Color(0xFFFF003C), Modifier.weight(1f))
                DtcStatCard("PENDIENTES", pendingDtcs.size, Color(0xFFFFD700), Modifier.weight(1f))
                DtcStatCard("PERMANENTES", permanentDtcs.size, Color.Gray, Modifier.weight(1f))
            }
        }

        // DTC List
        if (activeDtcs.isEmpty() && pendingDtcs.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✅", fontSize = 48.sp)
                        Text("No se detectaron fallas", color = Color(0xFF39FF14), fontWeight = FontWeight.Bold)
                        Text("El sistema está operando correctamente", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            item { Text("CÓDIGOS DE FALLA DETECTADOS", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium) }
            items(activeDtcs) { code -> DtcItemCard(code, "Activo", Color(0xFFFF003C)) }
            items(pendingDtcs) { code -> DtcItemCard(code, "Pendiente", Color(0xFFFFD700)) }
            
            // AI Analysis Button
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Button(
                        onClick = { 
                            coroutineScope.launch {
                                isAnalyzingAi = true
                                aiAnalysisResult = viewModel.consultAi(null, null, activeDtcs + pendingDtcs)
                                isAnalyzingAi = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(60.dp)
                            .border(2.dp, Color(0xFF39FF14), RoundedCornerShape(16.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0E1A)),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isAnalyzingAi
                    ) {
                        if (isAnalyzingAi) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = Color(0xFF39FF14), modifier = Modifier.size(20.dp), strokeWidth = 3.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("ANALIZANDO FORMAS DE ONDA...", color = Color(0xFF39FF14), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("✨", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("INICIAR DIAGNÓSTICO MAESTRO AI", color = Color(0xFF39FF14), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            // AI Result Display
            if (aiAnalysisResult != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF001A1A)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                            .border(1.dp, Color(0xFF39FF14).copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                            .padding(top = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = Color(0xFF39FF14).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.border(1.dp, Color(0xFF39FF14), RoundedCornerShape(4.dp))
                                ) {
                                    Text("MEET ELITE AI", color = Color(0xFF39FF14), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = { aiAnalysisResult = null }, modifier = Modifier.size(24.dp)) { 
                                    Icon(Icons.Default.Add, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.rotate(45f)) 
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            val sections = aiAnalysisResult.orEmpty().split("\n")
                            sections.forEach { line ->
                                val isHeader = line.startsWith("#") || line.contains(":") && line.length < 50
                                Text(
                                    text = line.replace("#", "").trim(),
                                    color = if (isHeader) Color(0xFF39FF14) else Color.White,
                                    style = if (isHeader) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isHeader) FontWeight.Black else FontWeight.Normal,
                                    modifier = Modifier.padding(vertical = if (isHeader) 4.dp else 2.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { viewModel.generateFullReport(aiAnalysisResult) }) {
                                    Text("GENERAR INFORME PDF", color = Color(0xFF39FF14), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Maintenance Alerts Section
        if (alerts.isNotEmpty()) {
            item {
                Column {
                    Text("PRÓXIMOS MANTENIMIENTOS", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    alerts.forEach { alert ->
                        val isDue = odometer >= alert.nextDueKm
                        val progress = if (alert.nextDueKm > alert.lastDoneKm) {
                            ((odometer - alert.lastDoneKm) / (alert.nextDueKm - alert.lastDoneKm)).coerceIn(0f, 1f)
                        } else 0f
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E1A)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).border(
                                1.dp, 
                                if (isDue) Color(0xFFFF003C) else Color(0xFF39FF14).copy(alpha = 0.2f), 
                                RoundedCornerShape(12.dp)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(alert.type.replace("_", " "), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text(if (isDue) "VENCIDO" else "OK", color = if (isDue) Color(0xFFFF003C) else Color(0xFF39FF14), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = progress,
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    color = if (isDue) Color(0xFFFF003C) else Color(0xFF39FF14),
                                    trackColor = Color.Gray.copy(alpha = 0.1f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Actual: ${odometer.toInt()} km", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                                    Text("Meta: ${alert.nextDueKm} km", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                                }
                                
                                if (isDue) {
                                    TextButton(
                                        onClick = { viewModel.markMaintenanceDone(alert) },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text("MARCAR COMO REALIZADO", color = Color(0xFF39FF14), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Readiness Monitors
        item {
            Text("MONITORES DE PREPARACIÓN (I/M)", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
        
        readinessMonitors?.let { result ->
            items(result.monitors) { monitor ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(Color(0xFF0A0E1A), RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(monitor.name, color = Color.White, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    val statusColor = if (monitor.complete) Color(0xFF39FF14) else Color(0xFFFFD700)
                    val statusText = if (monitor.complete) "COMPLETO" else "INC."
                    Surface(color = statusColor.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp), modifier = Modifier.border(1.dp, statusColor, RoundedCornerShape(4.dp))) {
                        Text(statusText, color = statusColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } ?: item { Text("Esperando datos de monitores...", color = Color.DarkGray, style = MaterialTheme.typography.bodySmall) }

        // Clear DTCs Action
        item {
            Button(
                onClick = { 
                    coroutineScope.launch {
                        val success = viewModel.clearDtcs()
                        if (success) snackbarHostState.showSnackbar("Códigos borrados exitosamente")
                        else snackbarHostState.showSnackbar("Error al borrar códigos. Asegúrate de tener el motor apagado y el encendido en ON.")
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).border(1.dp, Color(0xFFFF003C), RoundedCornerShape(12.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0E1A)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("BORRAR CÓDIGOS DE FALLA (RESET)", color = Color(0xFFFF003C), fontWeight = FontWeight.Bold)
            }
        }
        }

        // Diagnostic Overlay Animation
        if (isScanning || isClearing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isClearing) {
                        com.elysium369.meet.ui.components.EliteDeletionAnimation()
                    } else {
                        com.elysium369.meet.ui.components.EliteScannerAnimation(
                            scanText = if (isScanning) "DIAGNÓSTICO" else "MÓDULOS"
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Text(
                        text = if (isClearing) "BORRANDO MEMORIA ECU..." else "ANALIZANDO SISTEMAS...",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Text(
                        text = syncStatus.uppercase(),
                        color = (if (isClearing) Color(0xFFFF003C) else Color(0xFF00E5FF)).copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 12.dp),
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
}
