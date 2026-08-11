package com.elysium369.meet.ui.screens.scanner

import com.elysium369.meet.ui.components.AnimatedNeonGlyph

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.core.obd.ObdState
import androidx.compose.ui.draw.alpha
import com.elysium369.meet.ui.components.EliteScrollContainer
import com.elysium369.meet.ui.components.eliteScrollbar
import kotlinx.coroutines.launch
import androidx.navigation.NavController

@Composable
fun ScannerDiagnosticTab(
    viewModel: ObdViewModel,
    snackbarHostState: SnackbarHostState,
    navController: NavController? = null
) {
    val state by viewModel.connectionState.collectAsState()
    val activeDtcEvents by viewModel.canonicalActiveFindingSummaries.collectAsState()
    val pendingDtcEvents by viewModel.canonicalPendingFindingSummaries.collectAsState()
    val permanentDtcEvents by viewModel.canonicalPermanentFindingSummaries.collectAsState()
    val historicalDtcEvents by viewModel.canonicalHistoricalFindingSummaries.collectAsState()

    val activeDtcs by viewModel.activeDtcs.collectAsState()
    val pendingDtcs by viewModel.pendingDtcs.collectAsState()
    val permanentDtcs by viewModel.permanentDtcs.collectAsState()
    val readinessMonitors by viewModel.readinessMonitors.collectAsState()
    val vin by viewModel.vin.collectAsState()
    val alerts by viewModel.maintenanceAlerts.collectAsState()
    val odometer by viewModel.currentOdometer.collectAsState()
    val freezeFrameData by viewModel.freezeFrameData.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var isScanningModules by remember { mutableStateOf(false) }
    var detectedModules by remember { mutableStateOf<List<com.elysium369.meet.core.obd.NetworkModule>>(emptyList()) }
    var aiAnalysisResult by remember { mutableStateOf<String?>(null) }
    var isAnalyzingAi by remember { mutableStateOf(false) }

    // Local states for granular DTC operations
    var refreshingFreezeFrames by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var consultingDtcAi by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var dtcAiResults by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

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
                            com.elysium369.meet.ui.components.EliteTextButton(
                                text = if (isScanningModules) "ESCANEA..." else "ESCANEAR SISTEMAS",
                                onClick = {
                                    if (state != ObdState.CONNECTED) {
                                        coroutineScope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "OBD Desconectado. Conéctate a tu adaptador primero.",
                                                actionLabel = "CONECTAR",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                navController?.navigate("connect")
                                            }
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            isScanningModules = true
                                            detectedModules = viewModel.scanModules()
                                            isScanningModules = false
                                        }
                                    }
                                },
                                isEnabled = !isScanningModules,
                                color = MeetColors.neonGreen
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (detectedModules.isEmpty() && !isScanningModules) {
                            com.elysium369.meet.ui.components.EliteCard(
                                backgroundColor = MeetColors.backgroundDark,
                                borderColor = MeetColors.textMuted.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    EcuTopologyMap(
                                        isScanning = false,
                                        detectedModules = emptyList(),
                                        modifier = Modifier.alpha(0.4f)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "No se han escaneado módulos aún. Inicia un escaneo completo para detectar el estado de cada sistema (Motor, Transmisión, ABS, etc).",
                                        color = MeetColors.textSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            EcuTopologyMap(
                                isScanning = isScanningModules,
                                detectedModules = detectedModules
                            )
                        }
                    }
                }

                // VIN Section
                item {
                    com.elysium369.meet.ui.components.EliteCard(
                        backgroundColor = MeetColors.backgroundDark,
                        borderColor = MeetColors.neonGreen.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                        glowColor = MeetColors.neonGreen,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("IDENTIFICACIÓN DEL VEHÍCULO (VIN)", color = MeetColors.neonGreen.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(vin ?: "Leyendo VIN...", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        }
                    }
                }

                // DTC Summary
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DtcStatCard("ACTIVOS", activeDtcEvents.size, MeetColors.error, Modifier.weight(1f))
                        DtcStatCard("PENDIENTES", pendingDtcEvents.size, MeetColors.warning, Modifier.weight(1f))
                        DtcStatCard("PERMANENTES", permanentDtcEvents.size, MeetColors.textMuted, Modifier.weight(1f))
                        DtcStatCard("HIST.", historicalDtcEvents.size, MeetColors.cyberCyan, Modifier.weight(1f))
                    }
                }

                // DTC List
                if (activeDtcEvents.isEmpty() && pendingDtcEvents.isEmpty() && permanentDtcEvents.isEmpty() && historicalDtcEvents.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AnimatedNeonGlyph("✅", contentDescription = null, fontSize = 48.sp)
                                Text("Sin DTC detectados", color = MeetColors.neonGreen, fontWeight = FontWeight.Bold)
                                Text(
                                    "Los módulos cubiertos no reportaron códigos. Esto no descarta fallas fuera del monitoreo OBD.",
                                    color = MeetColors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                } else {
                    item { Text("CÓDIGOS DE FALLA DETECTADOS", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium) }

                    items(activeDtcEvents, key = { it.id }) { event ->
                        DtcItemCard(
                            code = event.code,
                            type = "Activo",
                            color = MeetColors.error,
                            description = event.description,
                            occurrenceCount = event.occurrenceCount,
                            lastSeenAt = event.lastSeenAt,
                            freezeFrameData = freezeFrameData,
                            onFreezeFrameClick = {
                                coroutineScope.launch {
                                    refreshingFreezeFrames = refreshingFreezeFrames + (event.code to true)
                                    viewModel.refreshFreezeFrame(event.code)
                                    refreshingFreezeFrames = refreshingFreezeFrames + (event.code to false)
                                }
                            },
                            isRefreshingFreezeFrame = refreshingFreezeFrames[event.code] == true,
                            onAiConsultClick = {
                                coroutineScope.launch {
                                    consultingDtcAi = consultingDtcAi + (event.code to true)
                                    val result = viewModel.consultAi(null, null, listOf(event.code))
                                    dtcAiResults = dtcAiResults + (event.code to result)
                                    consultingDtcAi = consultingDtcAi + (event.code to false)
                                }
                            },
                            isConsultingAi = consultingDtcAi[event.code] == true,
                            aiAnalysis = dtcAiResults[event.code],
                            onRepairGuideClick = {
                                navController?.navigate("repair/${event.code}?findingId=${java.net.URLEncoder.encode(event.id, "UTF-8")}")
                            }
                        )
                    }

                    items(pendingDtcEvents, key = { it.id }) { event ->
                        DtcItemCard(
                            code = event.code,
                            type = "Pendiente",
                            color = MeetColors.warning,
                            description = event.description,
                            occurrenceCount = event.occurrenceCount,
                            lastSeenAt = event.lastSeenAt,
                            freezeFrameData = freezeFrameData,
                            onFreezeFrameClick = {
                                coroutineScope.launch {
                                    refreshingFreezeFrames = refreshingFreezeFrames + (event.code to true)
                                    viewModel.refreshFreezeFrame(event.code)
                                    refreshingFreezeFrames = refreshingFreezeFrames + (event.code to false)
                                }
                            },
                            isRefreshingFreezeFrame = refreshingFreezeFrames[event.code] == true,
                            onAiConsultClick = {
                                coroutineScope.launch {
                                    consultingDtcAi = consultingDtcAi + (event.code to true)
                                    val result = viewModel.consultAi(null, null, listOf(event.code))
                                    dtcAiResults = dtcAiResults + (event.code to result)
                                    consultingDtcAi = consultingDtcAi + (event.code to false)
                                }
                            },
                            isConsultingAi = consultingDtcAi[event.code] == true,
                            aiAnalysis = dtcAiResults[event.code],
                            onRepairGuideClick = {
                                navController?.navigate("repair/${event.code}?findingId=${java.net.URLEncoder.encode(event.id, "UTF-8")}")
                            }
                        )
                    }

                    items(permanentDtcEvents, key = { it.id }) { event ->
                        DtcItemCard(
                            code = event.code,
                            type = "Permanente",
                            color = MeetColors.textMuted,
                            description = event.description,
                            occurrenceCount = event.occurrenceCount,
                            lastSeenAt = event.lastSeenAt,
                            freezeFrameData = freezeFrameData,
                            onFreezeFrameClick = {
                                coroutineScope.launch {
                                    refreshingFreezeFrames = refreshingFreezeFrames + (event.code to true)
                                    viewModel.refreshFreezeFrame(event.code)
                                    refreshingFreezeFrames = refreshingFreezeFrames + (event.code to false)
                                }
                            },
                            isRefreshingFreezeFrame = refreshingFreezeFrames[event.code] == true,
                            onAiConsultClick = {
                                coroutineScope.launch {
                                    consultingDtcAi = consultingDtcAi + (event.code to true)
                                    val result = viewModel.consultAi(null, null, listOf(event.code))
                                    dtcAiResults = dtcAiResults + (event.code to result)
                                    consultingDtcAi = consultingDtcAi + (event.code to false)
                                }
                            },
                            isConsultingAi = consultingDtcAi[event.code] == true,
                            aiAnalysis = dtcAiResults[event.code],
                            onRepairGuideClick = {
                                navController?.navigate("repair/${event.code}?findingId=${java.net.URLEncoder.encode(event.id, "UTF-8")}")
                            }
                        )
                    }

                    items(historicalDtcEvents, key = { it.id }) { event ->
                        DtcItemCard(
                            code = event.code,
                            type = if (event.status == "INTERMITTENT") "Intermitente" else "Historico",
                            color = MeetColors.cyberCyan,
                            description = event.description,
                            occurrenceCount = event.occurrenceCount,
                            lastSeenAt = event.lastSeenAt,
                            freezeFrameData = freezeFrameData,
                            onFreezeFrameClick = {
                                coroutineScope.launch {
                                    refreshingFreezeFrames = refreshingFreezeFrames + (event.code to true)
                                    viewModel.refreshFreezeFrame(event.code)
                                    refreshingFreezeFrames = refreshingFreezeFrames + (event.code to false)
                                }
                            },
                            isRefreshingFreezeFrame = refreshingFreezeFrames[event.code] == true,
                            onAiConsultClick = {
                                coroutineScope.launch {
                                    consultingDtcAi = consultingDtcAi + (event.code to true)
                                    val result = viewModel.consultAi(null, null, listOf(event.code))
                                    dtcAiResults = dtcAiResults + (event.code to result)
                                    consultingDtcAi = consultingDtcAi + (event.code to false)
                                }
                            },
                            isConsultingAi = consultingDtcAi[event.code] == true,
                            aiAnalysis = dtcAiResults[event.code],
                            onRepairGuideClick = {
                                navController?.navigate("repair/${event.code}?findingId=${java.net.URLEncoder.encode(event.id, "UTF-8")}")
                            }
                        )
                    }

                    // AI Analysis Button
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            val isEnabled = !isAnalyzingAi
                            com.elysium369.meet.ui.components.EliteCard(
                                backgroundColor = MeetColors.backgroundDark,
                                borderColor = MeetColors.neonGreen,
                                glowColor = MeetColors.neonGreen,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .then(if (isEnabled) Modifier.clickable {
                                        coroutineScope.launch {
                                            isAnalyzingAi = true
                                            aiAnalysisResult = viewModel.consultAi(null, null, activeDtcs + pendingDtcs + permanentDtcs + historicalDtcEvents.map { it.code })
                                            isAnalyzingAi = false
                                        }
                                    } else Modifier)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    if (isAnalyzingAi) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(color = MeetColors.neonGreen, modifier = Modifier.size(20.dp), strokeWidth = 3.dp)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("ANALIZANDO FORMAS DE ONDA...", color = MeetColors.neonGreen, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                                        }
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AnimatedNeonGlyph("✨", contentDescription = null, fontSize = 20.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("INICIAR DIAGNÓSTICO MAESTRO AI", color = MeetColors.neonGreen, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // AI Result Display (Formatted Card)
                    if (aiAnalysisResult != null) {
                        item {
                            AiDiagnosisReportCard(
                                aiAnalysisResult = aiAnalysisResult.orEmpty(),
                                onClose = { aiAnalysisResult = null },
                                onGeneratePdf = { viewModel.generateFullReport(aiAnalysisResult) }
                            )
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

                                com.elysium369.meet.ui.components.EliteCard(
                                    backgroundColor = MeetColors.backgroundDark,
                                    borderColor = if (isDue) MeetColors.error else MeetColors.neonGreen.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(12.dp),
                                    glowColor = if (isDue) MeetColors.error else MeetColors.neonGreen,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(alert.type.replace("_", " "), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                            Text(if (isDue) "VENCIDO" else "OK", color = if (isDue) MeetColors.error else MeetColors.neonGreen, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier.fillMaxWidth().height(4.dp),
                                            color = if (isDue) MeetColors.error else MeetColors.neonGreen,
                                            trackColor = MeetColors.textMuted.copy(alpha = 0.1f)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text("Actual: ${odometer.toInt()} km", color = MeetColors.textMuted, style = MaterialTheme.typography.labelSmall)
                                            Text("Meta: ${alert.nextDueKm} km", color = MeetColors.textMuted, style = MaterialTheme.typography.labelSmall)
                                        }

                                        if (isDue) {
                                            com.elysium369.meet.ui.components.EliteTextButton(
                                                text = "MARCAR COMO REALIZADO",
                                                onClick = { viewModel.markMaintenanceDone(alert) },
                                                modifier = Modifier.align(Alignment.End),
                                                color = MeetColors.neonGreen
                                            )
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
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(MeetColors.backgroundDark, RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(monitor.name, color = Color.White, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            val statusColor = if (monitor.complete) MeetColors.neonGreen else MeetColors.warning
                            val statusText = if (monitor.complete) "COMPLETO" else "INC."
                            Box(modifier = Modifier.background(statusColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).border(1.dp, statusColor, RoundedCornerShape(4.dp))) {
                                Text(statusText, color = statusColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } ?: item { Text("Esperando datos de monitores...", color = MeetColors.textMuted, style = MaterialTheme.typography.bodySmall) }

                // Clear DTCs Action
                item {
                    com.elysium369.meet.ui.components.EliteButton(
                        text = "BORRAR CÓDIGOS DE FALLA (RESET)",
                        onClick = {
                            coroutineScope.launch {
                                val result = viewModel.clearDtcs()
                                snackbarHostState.showSnackbar(result.message)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        color = MeetColors.error
                    )
                }

                // OBD Communication Logs Quick Access
                if (navController != null) {
                    item {
                        com.elysium369.meet.ui.components.EliteOutlinedButton(
                            text = "⚡ VER LOGS DE COMUNICACIÓN OBD",
                            onClick = { navController.navigate("terminal") },
                            color = MeetColors.cyberCyan,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }
            }
        }

        // Diagnostic Overlay Animation
        if (isScanning || isClearing || isScanningModules) {
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
                        text = if (isClearing) "BORRANDO MEMORIA ECU..." 
                               else if (isScanning && isScanningModules) syncStatus.ifBlank { "ESCANEANDO SISTEMAS..." }
                               else if (isScanning) "ANALIZANDO SISTEMAS..." 
                               else "ESCANEANDO MÓDULOS...",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = if (isScanningModules && syncStatus.isNotBlank()) syncStatus.uppercase() else syncStatus.uppercase(),
                        color = (if (isClearing) MeetColors.error else MeetColors.electricBlue).copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 12.dp),
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
