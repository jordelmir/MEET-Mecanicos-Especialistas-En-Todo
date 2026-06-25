package com.elysium369.meet.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.elysium369.meet.ui.theme.MeetColors
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.screens.scanner.*
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteTextButton
import androidx.compose.animation.core.*
import com.elysium369.meet.ui.components.neonGlow
import com.elysium369.meet.ui.components.hud.HudData
import com.elysium369.meet.ui.components.hud.HudFaceManager
import com.elysium369.meet.ui.components.hud.HudFaceRenderer
import com.elysium369.meet.ui.components.hud.HudFaceSelector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(navController: NavController, viewModel: ObdViewModel) {
    val liveData by viewModel.liveData.collectAsState()
    val state by viewModel.connectionState.collectAsState()
    val isLogging by viewModel.isLogging.collectAsState()
    val dataLog by viewModel.dataLog.collectAsState()
    val cloudSyncState by viewModel.cloudSyncState.collectAsState()
    val highSpeedMode by viewModel.highSpeedMode.collectAsState()
    val qosMetrics by viewModel.qosMetrics.collectAsState()
    val anomalousPids by viewModel.anomalousPids.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val language by viewModel.language.collectAsState()
    var isSpanish by remember(language) { mutableStateOf(language == "es") }
    
    val voiceCopilotEnabled by viewModel.voiceCopilotEnabled.collectAsState()
    val isVoiceCopilotListening by viewModel.isVoiceCopilotListening.collectAsState()
    val context = LocalContext.current
    
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleVoiceCopilot(true)
            Toast.makeText(context, if (isSpanish) "Copiloto por voz activado" else "Voice Copilot activated", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.toggleVoiceCopilot(false)
            Toast.makeText(context, if (isSpanish) "Permiso de micrófono denegado" else "Microphone permission denied", Toast.LENGTH_LONG).show()
        }
    }

    val defaultGauges = remember {
        listOf(
            // ═══════ DIRECT OBD SENSORS ═══════
            GaugeConfig("1", "RPM", "010C", 0f, 8000f, "rpm"),
            GaugeConfig("2", "Velocidad", "010D", 0f, 255f, "km/h"),
            GaugeConfig("3", "Temp Motor", "0105", -40f, 150f, "°C"),
            GaugeConfig("4", "Carga Motor", "0104", 0f, 100f, "%"),
            GaugeConfig("5", "Voltaje Módulo OBD", "0142", 10f, 16f, "V", GaugeType.WAVE),
            GaugeConfig("6", "Presión MAP", "010B", 0f, 255f, "kPa"),
            GaugeConfig("7", "Flujo MAF", "0110", 0f, 655f, "g/s", GaugeType.WAVE),
            GaugeConfig("8", "Pos. Acelerador", "0111", 0f, 100f, "%", GaugeType.WAVE),
            GaugeConfig("9", "Temp Admisión", "010F", -40f, 150f, "°C"),
            GaugeConfig("10", "Avance Enc.", "010E", -64f, 64f, "°", GaugeType.WAVE),
            GaugeConfig("11", "Nivel Comb.", "012F", 0f, 100f, "%"),
            GaugeConfig("12", "Trim Comb CT B1", "0106", -100f, 100f, "%", GaugeType.WAVE),
            GaugeConfig("13", "Trim Comb LT B1", "0107", -100f, 100f, "%", GaugeType.WAVE),
            GaugeConfig("14", "O2 B1S1 Voltaje", "0114", 0f, 1.3f, "V", GaugeType.WAVE),
            GaugeConfig("15", "O2 B1S2 Voltaje", "0115", 0f, 1.3f, "V", GaugeType.WAVE),
            GaugeConfig("16", "Presión Comb.", "010A", 0f, 765f, "kPa"),
            GaugeConfig("17", "Presión Baro", "0133", 0f, 255f, "kPa"),
            GaugeConfig("18", "Tiempo Motor", "011F", 0f, 65535f, "s"),
            GaugeConfig("19", "Estado Sist. Comb.", "CALC_FUEL_STATUS_CODE", 0f, 16f, ""),
            GaugeConfig("50", "Estándar OBD", "CALC_OBD_STANDARD", 0f, 50f, ""),
            GaugeConfig("51", "Hora Actual", "CALC_CURRENT_TIME", 0f, 2359f, ""),
            GaugeConfig("52", "Estado MIL", "CALC_MIL_STATUS", 0f, 1f, ""),
            GaugeConfig("53", "Conteo DTCs", "CALC_DTC_COUNT", 0f, 127f, ""),
            // ═══════ CALCULATED SENSORS ═══════
            GaugeConfig("20", "Consumo Instant.", "CALC_FUEL_RATE", 0f, 30f, "L/h", GaugeType.WAVE),
            GaugeConfig("21", "Consumo L/100km", "CALC_FUEL_CONSUMPTION", 0f, 50f, "L/100km", GaugeType.WAVE),
            GaugeConfig("22", "Aceleración", "CALC_ACCELERATION", -2f, 2f, "g", GaugeType.WAVE),
            GaugeConfig("23", "Boost Calculado", "CALC_BOOST", -1f, 2f, "bar", GaugeType.WAVE),
            GaugeConfig("24", "Potencia Motor", "CALC_POWER", 0f, 300f, "hp", GaugeType.WAVE),
            GaugeConfig("25", "RPM ×1000", "CALC_RPM_K", 0f, 8f, "rpm×1k"),
            // ═══════ TRIP ACCUMULATORS ═══════
            GaugeConfig("30", "Vel. Promedio", "CALC_AVG_SPEED", 0f, 200f, "km/h"),
            GaugeConfig("31", "Distancia Viaje", "CALC_TRIP_DISTANCE", 0f, 500f, "km"),
            GaugeConfig("32", "Distancia Total", "CALC_TOTAL_DISTANCE", 0f, 9999f, "km"),
            GaugeConfig("33", "Combustible Usado", "CALC_FUEL_USED", 0f, 50f, "L"),
            GaugeConfig("34", "Combustible Total", "CALC_FUEL_USED_TOTAL", 0f, 999f, "L"),
            GaugeConfig("35", "Consumo Promedio", "CALC_AVG_CONSUMPTION", 0f, 30f, "L/100km"),
            GaugeConfig("36", "Consumo Prom. Total", "CALC_AVG_CONSUMPTION_TOTAL", 0f, 30f, "L/100km"),
            GaugeConfig("37", "Consumo Prom. 10s", "CALC_AVG_CONSUMPTION_10S", 0f, 50f, "L/100km", GaugeType.WAVE),
            // ═══════ FUEL PRICE ═══════
            GaugeConfig("40", "Precio Comb. Viaje", "CALC_FUEL_PRICE", 0f, 500f, "$"),
            GaugeConfig("41", "Precio Comb. Total", "CALC_FUEL_PRICE_TOTAL", 0f, 9999f, "$"),
            // ═══════ FUEL ECONOMIZER ═══════
            GaugeConfig("42", "Economizador Comb.", "CALC_FUEL_ECON", 0f, 1f, "")
        )
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var selectedTab by remember { mutableIntStateOf(0) }
    var hudMode by remember { mutableStateOf(false) }

    // AI Anomaly Snackbar Notification
    LaunchedEffect(anomalousPids) {
        if (anomalousPids.isNotEmpty()) {
            val sensorNames = anomalousPids.map { it.pid }.joinToString(", ")
            snackbarHostState.showSnackbar(
                message = if (isSpanish) "🚨 IA detectó anomalía en sensores: $sensorNames" else "🚨 AI detected anomaly in sensors: $sensorNames",
                actionLabel = if (isSpanish) "VER" else "VIEW",
                duration = SnackbarDuration.Long
            )
        }
    }

// HUD Mode Overlay — Modernized with 10 face types
    if (hudMode) {
        var isMirrorMode by remember { mutableStateOf(false) }
        val context = LocalContext.current
        val hudFaceManager = remember { HudFaceManager(context) }
        val currentFace by hudFaceManager.currentFace.collectAsState()
        val hudData = HudData(
            speed = liveData["010D"] ?: 0f,
            rpm = liveData["010C"] ?: 0f,
            coolantTemp = liveData["0105"] ?: 0f,
            throttle = liveData["0111"] ?: 0f,
            engineLoad = liveData["0104"] ?: 0f,
            voltage = liveData["0142"] ?: liveData.entries.firstOrNull { it.key.contains("VOLT", true) }?.value ?: 12.4f,
            fuelLevel = liveData["012F"] ?: 0f,
            intakeTemp = liveData["010F"] ?: 0f
        )

        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Face selector at top (NOT mirrored)
                Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HudFaceSelector(
                        currentFace = currentFace,
                        onFaceSelected = { hudFaceManager.selectFace(it) }
                    )
                }

                // HUD content (mirrored if toggled)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 52.dp, bottom = 56.dp)
                        .graphicsLayer {
                            scaleX = if (isMirrorMode) -1f else 1f
                        }
                ) {
                    HudFaceRenderer(
                        face = currentFace,
                        data = hudData,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Bottom controls (NOT mirrored)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
                ) {
                    EliteTextButton(
                        onClick = { isMirrorMode = !isMirrorMode },
                        text = if (isMirrorMode) (if (isSpanish) "MODO NORMAL" else "NORMAL MODE") else (if (isSpanish) "MODO ESPEJO" else "MIRROR MODE"),
                        color = MeetColors.electricBlue
                    )
                    EliteTextButton(
                        onClick = { hudMode = false },
                        text = if (isSpanish) "SALIR HUD" else "EXIT HUD",
                        color = MeetColors.textSecondary
                    )
                }
            }
        }
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                EliteTopAppBar(
                    title = buildAnnotatedString {
                        if (isSpanish) {
                            withStyle(SpanStyle(color = MeetColors.neonGreen)) {
                                append("ESCÁNER ")
                            }
                            withStyle(SpanStyle(color = MeetColors.electricBlue)) {
                                append("EN VIVO")
                            }
                        } else {
                            withStyle(SpanStyle(color = MeetColors.neonGreen)) {
                                append("LIVE ")
                            }
                            withStyle(SpanStyle(color = MeetColors.electricBlue)) {
                                append("SCANNER")
                            }
                        }
                    },
                    actions = {
                        // Voice Copilot Active / Deactive Toggle Button
                        IconButton(
                            onClick = {
                                val isChecked = !voiceCopilotEnabled
                                if (isChecked) {
                                    val hasMicPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.RECORD_AUDIO
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    
                                    if (hasMicPermission) {
                                        viewModel.toggleVoiceCopilot(true)
                                    } else {
                                        micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    }
                                } else {
                                    viewModel.toggleVoiceCopilot(false)
                                }
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (voiceCopilotEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = "Voice Copilot Toggle",
                                tint = if (voiceCopilotEnabled) {
                                    if (isVoiceCopilotListening) MeetColors.neonGreen else MeetColors.electricBlue
                                } else {
                                    MeetColors.textSecondary
                                },
                                modifier = if (voiceCopilotEnabled && isVoiceCopilotListening) {
                                    Modifier.neonGlow(MeetColors.neonGreen, CircleShape)
                                } else Modifier
                            )
                        }

                        // Language Selector
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                            Text("EN", color = if(isSpanish) MeetColors.textSecondary else Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            Switch(
                                checked = isSpanish,
                                onCheckedChange = { 
                                    isSpanish = it
                                    viewModel.setLanguage(if(it) "es" else "en")
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                                    checkedTrackColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.3f),
                                    uncheckedThumbColor = com.elysium369.meet.ui.theme.MeetColors.electricBlue,
                                    uncheckedTrackColor = com.elysium369.meet.ui.theme.MeetColors.electricBlue.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.padding(horizontal = 4.dp).height(24.dp)
                            )
                            Text("ES", color = if(isSpanish) Color.White else MeetColors.textSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }

                        // High Speed Mode Indicator
                        if (highSpeedMode) {
                            com.elysium369.meet.ui.components.EliteCard(
                                backgroundColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp),
                                borderColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen
                            ) {
                                Text(
                                    "HIGH-SPEED: ${qosMetrics.cmdsPerSecond.toInt()}Hz", 
                                    color = com.elysium369.meet.ui.theme.MeetColors.neonGreen, 
                                    fontWeight = FontWeight.Black, 
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), 
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        
                        // Logging indicator
                        if (isLogging) {
                            com.elysium369.meet.ui.components.EliteCard(
                                backgroundColor = com.elysium369.meet.ui.theme.MeetColors.error.copy(alpha = 0.2f), 
                                shape = RoundedCornerShape(4.dp), 
                                borderColor = com.elysium369.meet.ui.theme.MeetColors.error
                            ) {
                                Text(
                                    "● REC ${dataLog.size}", 
                                    color = com.elysium369.meet.ui.theme.MeetColors.error, 
                                    fontWeight = FontWeight.Black, 
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), 
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        if (state == ObdState.DISCONNECTED) {
                            com.elysium369.meet.ui.components.EliteTextButton(
                                onClick = { navController.navigate("connect") },
                                text = if (isSpanish) "CONECTAR" else "CONNECT",
                                color = com.elysium369.meet.ui.theme.MeetColors.neonGreen
                            )
                        }
                    }
                )
                
                // CLOUD SYNC INDICATOR
                if (cloudSyncState.isNotBlank() && cloudSyncState != "Desconectado") {
                    val bgColor = if (cloudSyncState.contains("❌")) com.elysium369.meet.ui.theme.MeetColors.error.copy(alpha = 0.2f) else com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.1f)
                    val textColor = if (cloudSyncState.contains("❌")) com.elysium369.meet.ui.theme.MeetColors.error else com.elysium369.meet.ui.theme.MeetColors.neonGreen
                    com.elysium369.meet.ui.components.EliteCard(
                        backgroundColor = bgColor,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(4.dp),
                        borderColor = textColor
                    ) {
                        Text(
                            text = cloudSyncState,
                            color = textColor,
                            modifier = Modifier.padding(8.dp).fillMaxWidth(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                ScrollableTabRow(
                    selectedTabIndex = selectedTab, 
                    containerColor = Color.Transparent, 
                    contentColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                    edgePadding = 8.dp,
                    indicator = { tabPositions -> 
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.Indicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]), 
                                color = com.elysium369.meet.ui.theme.MeetColors.neonGreen, 
                                height = 3.dp
                            )
                        }
                    }
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("DASHBOARD", color = if (selectedTab == 0) com.elysium369.meet.ui.theme.MeetColors.neonGreen else MeetColors.textSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(if (isSpanish) "RENDIMIENTO" else "PERFORMANCE", color = if (selectedTab == 1) com.elysium369.meet.ui.theme.MeetColors.neonGreen else MeetColors.textSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text(if (isSpanish) "DIAGNÓSTICO" else "DIAGNOSTICS", color = if (selectedTab == 2) com.elysium369.meet.ui.theme.MeetColors.neonGreen else MeetColors.textSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) })
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text(if (isSpanish) "SENSORES" else "SENSORS", color = if (selectedTab == 3) com.elysium369.meet.ui.theme.MeetColors.neonGreen else MeetColors.textSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) })
                    Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }, text = { Text(if (isSpanish) "HERRAM." else "TOOLS", color = if (selectedTab == 4) com.elysium369.meet.ui.theme.MeetColors.neonGreen else MeetColors.textSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) })
                    Tab(selected = selectedTab == 5, onClick = { selectedTab = 5 }, text = { Text(if (isSpanish) "MONITORES" else "MONITORS", color = if (selectedTab == 5) com.elysium369.meet.ui.theme.MeetColors.neonGreen else MeetColors.textSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) })
                    Tab(selected = selectedTab == 6, onClick = { selectedTab = 6 }, text = { Text(if (isSpanish) "ESTADÍSTICAS" else "STATS", color = if (selectedTab == 6) com.elysium369.meet.ui.theme.MeetColors.neonGreen else MeetColors.textSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    navController.navigate("custom_pid")
                },
                containerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark, 
                shape = RoundedCornerShape(12.dp), 
                modifier = Modifier
                    .border(1.dp, com.elysium369.meet.ui.theme.MeetColors.neonGreen, RoundedCornerShape(12.dp))
                    .neonGlow(com.elysium369.meet.ui.theme.MeetColors.neonGreen, RoundedCornerShape(12.dp), minElevation = 4f, maxElevation = 12f)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar", tint = com.elysium369.meet.ui.theme.MeetColors.neonGreen)
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> ScannerDashboardTab(viewModel, isLandscape, defaultGauges, navController)
                1 -> ScannerPerformanceTab(viewModel, isLandscape)
                2 -> ScannerDiagnosticTab(viewModel, snackbarHostState, navController)
                3 -> ScannerSensorsTab(viewModel, defaultGauges)
                4 -> ScannerToolsTab(viewModel, navController, isSpanish, onHudModeToggle = { hudMode = it })
                5 -> ScannerMonitorsTab(viewModel, isSpanish, snackbarHostState, navController)
                6 -> ScannerStatisticsTab(viewModel, isLandscape, isSpanish)
            }
        }
    }
}
