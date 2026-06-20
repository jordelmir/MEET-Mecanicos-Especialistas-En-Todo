package com.elysium369.meet.ui.screens.scanner

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteOutlinedButton
import com.elysium369.meet.ui.components.EliteIconButton
import com.elysium369.meet.ui.components.neonGlow
import com.elysium369.meet.ui.components.hud.HudData
import com.elysium369.meet.ui.components.hud.HudFaceManager
import com.elysium369.meet.ui.components.hud.HudFaceRenderer
import com.elysium369.meet.ui.components.hud.HudFaceSelector
import kotlin.math.abs
import kotlin.math.sqrt

@Composable
fun ScannerToolsTab(
    viewModel: ObdViewModel,
    navController: NavController,
    isSpanish: Boolean,
    onHudModeToggle: (Boolean) -> Unit
) {
    val liveData by viewModel.liveData.collectAsState()
    val state by viewModel.connectionState.collectAsState()
    val isLogging by viewModel.isLogging.collectAsState()
    val dataLog by viewModel.dataLog.collectAsState()
    val highSpeedMode by viewModel.highSpeedMode.collectAsState()
    val isAdapterPro by viewModel.isAdapterPro.collectAsState()
    val isAiMonitoring by viewModel.isAiMonitoring.collectAsState()

    // HUD Face manager
    val context = androidx.compose.ui.platform.LocalContext.current
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
    
    val infiniteTransition = rememberInfiniteTransition()
    val recordAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(), 
        contentPadding = PaddingValues(16.dp), 
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { 
            Text(
                if (isSpanish) "HERRAMIENTAS DE DIAGNÓSTICO ELITE" else "ELITE DIAGNOSTIC TOOLS", 
                color = MeetColors.neonGreen.copy(alpha = 0.6f), 
                style = MaterialTheme.typography.labelSmall, 
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            ) 
        }
        
        // 1. Quick Connect Status Card
        item {
            val isConnected = state == ObdState.CONNECTED
            val statusColor = if (isConnected) MeetColors.neonGreen else MeetColors.warning
            val icon = if (isConnected) "⚡" else "🔌"
            val title = if (isConnected) {
                if (isSpanish) "Vínculo OBD2 Activo" else "OBD2 Link Active"
            } else {
                if (isSpanish) "Adaptador Desconectado" else "Adapter Disconnected"
            }
            val desc = if (isConnected) {
                if (isSpanish) "Transmisión de telemetría en tiempo real desde la ECU activa" else "Streaming real-time telemetry from active ECU"
            } else {
                if (isSpanish) "Toca para conectar tu adaptador ELM327 bluetooth/WiFi" else "Tap to connect your ELM327 bluetooth/WiFi adapter"
            }
            
            EliteCard(
                backgroundColor = MeetColors.backgroundDeep,
                borderColor = statusColor.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp),
                glowColor = statusColor.copy(alpha = 0.2f),
                onClick = {
                    if (!isConnected) {
                        navController.navigate("connect")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(statusColor.copy(alpha = 0.1f), CircleShape)
                            .border(1.dp, statusColor.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(icon, fontSize = 22.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            title, 
                            color = Color.White, 
                            fontWeight = FontWeight.Bold, 
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            desc, 
                            color = MeetColors.textSecondary, 
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        
        // 1b. Motor 3D / Localizador 3D Card
        item {
            EliteCard(
                backgroundColor = MeetColors.backgroundDeep,
                borderColor = MeetColors.cyberCyan.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp),
                glowColor = MeetColors.cyberCyan.copy(alpha = 0.15f),
                onClick = {
                    navController.navigate("component_locator")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MeetColors.cyberCyan.copy(alpha = 0.1f), CircleShape)
                            .border(1.dp, MeetColors.cyberCyan.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📦", fontSize = 22.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (isSpanish) "Diagnóstico Visual 3D" else "3D Visual Diagnostic", 
                            color = Color.White, 
                            fontWeight = FontWeight.Bold, 
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            if (isSpanish) "Localizador interactivo de motor, fusibles y arnés eléctrico" else "Interactive locator for engine, fuses, and wiring harness", 
                            color = MeetColors.textSecondary, 
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        
        // 2. AI Health Monitoring
        item {
            EliteCard(
                backgroundColor = MeetColors.backgroundDeep,
                shape = RoundedCornerShape(14.dp),
                borderColor = if (isAiMonitoring) MeetColors.neonGreen else MeetColors.borderSubtle,
                glowColor = if (isAiMonitoring) MeetColors.neonGreen.copy(alpha = 0.25f) else Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (isSpanish) "Monitoreo Inteligente IA" else "AI Health Monitoring", 
                                color = Color.White, 
                                fontWeight = FontWeight.Bold, 
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(MeetColors.neonGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            ) {
                                Text(
                                    "BETA", 
                                    color = MeetColors.neonGreen, 
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), 
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (isSpanish) "Analiza anomalías de sensores en segundo plano usando heurística neuronal." 
                            else "Analyzes sensor anomalies in the background using neural heuristics.",
                            color = MeetColors.textSecondary, 
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = isAiMonitoring,
                        onCheckedChange = { viewModel.toggleAiMonitoring(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MeetColors.neonGreen,
                            checkedTrackColor = MeetColors.neonGreen.copy(alpha = 0.3f),
                            uncheckedThumbColor = MeetColors.textSecondary,
                            uncheckedTrackColor = MeetColors.borderSubtle
                        )
                    )
                }
            }
        }

        // 3. High-Speed Mode Toggle
        item {
            EliteCard(
                backgroundColor = MeetColors.backgroundDeep,
                shape = RoundedCornerShape(14.dp),
                borderColor = if (highSpeedMode) MeetColors.cyberCyan else MeetColors.borderSubtle,
                glowColor = if (highSpeedMode) MeetColors.cyberCyan.copy(alpha = 0.25f) else Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (isSpanish) "Frecuencia de Muestreo Alta (20Hz+)" else "High-Speed Sampling Mode (20Hz+)", 
                            color = Color.White, 
                            fontWeight = FontWeight.Bold, 
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (isAdapterPro) {
                                if (isSpanish) "Optimizando para chip STN/OBDLink (Bus rápido)" else "Optimizing for STN/OBDLink controller (Fast bus)"
                            } else {
                                if (isSpanish) "ELM327 estándar detectado. Frecuencia regulada." else "Standard ELM327 detected. Frequency restricted."
                            },
                            color = MeetColors.textSecondary, 
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = highSpeedMode,
                        onCheckedChange = { viewModel.setHighSpeedMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MeetColors.cyberCyan,
                            checkedTrackColor = MeetColors.cyberCyan.copy(alpha = 0.3f),
                            uncheckedThumbColor = MeetColors.textSecondary,
                            uncheckedTrackColor = MeetColors.borderSubtle
                        )
                    )
                }
            }
        }
        
        // 4. HUD Windshield Projector — Expanded with 10 face types
        item {
            EliteCard(
                backgroundColor = MeetColors.backgroundDeep,
                borderColor = MeetColors.electricBlue.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp),
                glowColor = MeetColors.electricBlue.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // Header row
                    Row(
                        modifier = Modifier.padding(16.dp).clickable { onHudModeToggle(true) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(MeetColors.electricBlue.copy(alpha = 0.1f), CircleShape)
                                .border(1.dp, MeetColors.electricBlue.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🖥️", fontSize = 22.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (isSpanish) "Proyector HUD de Parabrisas" else "Windshield HUD Projector", 
                                color = Color.White, 
                                fontWeight = FontWeight.Bold, 
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                if (isSpanish) "Refleja velocidad y RPM sobre el cristal • 10 estilos" 
                                else "Reflects speed & RPM on windshield • 10 styles", 
                                color = MeetColors.textSecondary, 
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    // Face selector row
                    HudFaceSelector(
                        currentFace = currentFace,
                        onFaceSelected = { hudFaceManager.selectFace(it) }
                    )

                    // Live preview of selected face
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black)
                            .border(
                                0.5.dp,
                                MeetColors.electricBlue.copy(alpha = 0.25f),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { onHudModeToggle(true) }
                    ) {
                        HudFaceRenderer(
                            face = currentFace,
                            data = hudData,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Open fullscreen button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MeetColors.electricBlue.copy(alpha = 0.1f))
                            .border(0.5.dp, MeetColors.electricBlue.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .clickable { onHudModeToggle(true) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isSpanish) "▶  ABRIR HUD COMPLETO" else "▶  OPEN FULL HUD",
                            color = MeetColors.electricBlue,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            letterSpacing = 2.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        
        // 5. Data Logging & CSV Export
        item {
            EliteCard(
                backgroundColor = MeetColors.backgroundDeep,
                borderColor = MeetColors.hotMagenta.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp),
                glowColor = MeetColors.hotMagenta.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(MeetColors.hotMagenta.copy(alpha = 0.1f), CircleShape)
                                .border(1.dp, MeetColors.hotMagenta.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📊", fontSize = 22.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (isSpanish) "Grabación de Sesión (CSV)" else "Session Data Logging (CSV)", 
                                color = Color.White, 
                                fontWeight = FontWeight.Bold, 
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                if (isSpanish) "Registrar parámetros a intervalo de 500ms" else "Log telemetry at 500ms intervals", 
                                color = MeetColors.textSecondary, 
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { if (isLogging) viewModel.stopDataLogging() else viewModel.startDataLogging() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isLogging) MeetColors.error.copy(alpha = 0.15f) else MeetColors.hotMagenta.copy(alpha = 0.15f),
                                contentColor = if (isLogging) MeetColors.error else MeetColors.hotMagenta
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isLogging) MeetColors.error.copy(alpha = 0.5f) else MeetColors.hotMagenta.copy(alpha = 0.4f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isLogging) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(MeetColors.error, CircleShape)
                                            .neonGlow(MeetColors.error, CircleShape)
                                            .graphicsLayer { alpha = recordAlpha }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (isSpanish) "DETENER (${dataLog.size} pts)" else "STOP (${dataLog.size} pts)", 
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                } else {
                                    Text(
                                        if (isSpanish) "GRABAR" else "RECORD", 
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                        
                        Button(
                            onClick = { viewModel.saveCsvToFile() },
                            enabled = dataLog.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MeetColors.neonGreen.copy(alpha = 0.12f),
                                contentColor = MeetColors.neonGreen,
                                disabledContainerColor = MeetColors.backgroundDark,
                                disabledContentColor = MeetColors.textMuted
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (dataLog.isNotEmpty()) MeetColors.neonGreen.copy(alpha = 0.4f) else MeetColors.borderSubtle),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (isSpanish) "COMPARTIR CSV" else "SHARE CSV", 
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
        
        // 6. Real-Time Fuel economy calculator (HUD Style)
        item {
            val maf = liveData["0110"] ?: 0f
            val speed = liveData["010D"] ?: 0f
            val lPer100km = if (speed > 0 && maf > 0) (maf * 3600f) / (speed * 14.7f * 710f) * 100f else 0f
            
            // Smooth animated fuel consumption
            val animatedFuel by animateFloatAsState(
                targetValue = lPer100km,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 50f
                ), label = "fuelAnim"
            )
            val animatedMaf by animateFloatAsState(
                targetValue = maf,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 35f
                ), label = "mafAnim"
            )
            val animatedFuelSpeed by animateFloatAsState(
                targetValue = speed,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 35f
                ), label = "fuelSpeedAnim"
            )
            
            EliteCard(
                backgroundColor = MeetColors.backgroundDeep, 
                shape = RoundedCornerShape(14.dp), 
                borderColor = MeetColors.cyberCyan.copy(alpha = 0.3f),
                glowColor = MeetColors.cyberCyan.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        if (isSpanish) "⛽ CONSUMO INSTANTÁNEO" else "⛽ REAL-TIME CONSUMPTION", 
                        color = MeetColors.cyberCyan.copy(alpha = 0.7f), 
                        style = MaterialTheme.typography.labelSmall, 
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${String.format("%.1f", animatedFuel)}",
                            color = Color.White,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 38.sp
                        )
                        Text(
                            text = "L/100km",
                            color = MeetColors.textSecondary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Small progress bar representing fuel consumption severity
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(MeetColors.backgroundDark, RoundedCornerShape(3.dp))
                    ) {
                        val fraction = (animatedFuel / 25f).coerceIn(0f, 1f)
                        val barColor = when {
                            fraction > 0.75f -> MeetColors.error
                            fraction > 0.4f -> MeetColors.warning
                            else -> MeetColors.neonGreen
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .background(barColor, RoundedCornerShape(3.dp))
                                .neonGlow(barColor, RoundedCornerShape(3.dp))
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "MAF: ${String.format("%.1f", animatedMaf)} g/s  •  Speed: ${animatedFuelSpeed.toInt()} km/h", 
                        color = MeetColors.textMuted, 
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        
        // 7. Drag christmas tree 0-100 Performance test
        item {
            val speed = liveData["010D"] ?: 0f
            val animatedSpeed by animateFloatAsState(
                targetValue = speed,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 35f // Waze-like smooth number sweep
                ), label = "dragSpeed"
            )
            
            var testState by remember { mutableStateOf("IDLE") } // "IDLE", "WAITING_FOR_STOP", "COUNTDOWN", "RUNNING", "COMPLETED", "JUMP_START"
            var countdownProgress by remember { mutableIntStateOf(0) } // 0: Idle/Off, 1: Red 1, 2: Red 2, 3: Red 3, 4: Green (Go!), 5: Red Light (Jump Start / Falsa Salida)
            var time0to100 by remember { mutableStateOf<Long?>(null) }
            var startTime by remember { mutableStateOf<Long?>(null) }
            var currentDuration by remember { mutableStateOf(0L) }
            
            // Local ticker to update duration on-screen while running
            LaunchedEffect(testState, startTime) {
                if (testState == "RUNNING") {
                    while (testState == "RUNNING") {
                        val capturedStart = startTime
                        if (capturedStart != null) {
                            currentDuration = System.currentTimeMillis() - capturedStart
                        }
                        kotlinx.coroutines.delay(30)
                    }
                }
            }

            // State Machine controller reacting to speed and timer progress
            LaunchedEffect(testState, speed) {
                if (testState == "WAITING_FOR_STOP") {
                    if (speed == 0f) {
                        testState = "COUNTDOWN"
                        countdownProgress = 0
                        // Countdown lights
                        kotlinx.coroutines.delay(800)
                        if (testState == "COUNTDOWN") countdownProgress = 1
                        kotlinx.coroutines.delay(800)
                        if (testState == "COUNTDOWN") countdownProgress = 2
                        kotlinx.coroutines.delay(800)
                        if (testState == "COUNTDOWN") countdownProgress = 3
                        kotlinx.coroutines.delay(800)
                        if (testState == "COUNTDOWN") {
                            countdownProgress = 4
                            testState = "RUNNING"
                            startTime = System.currentTimeMillis()
                        }
                    }
                } else if (testState == "COUNTDOWN") {
                    // Check for pre-stage jump starts
                    if (speed > 1f) {
                        testState = "JUMP_START"
                        countdownProgress = 5 // Red Light warning
                    }
                } else if (testState == "RUNNING") {
                    if (speed >= 100f) {
                        val capturedStart = startTime
                        if (capturedStart != null) {
                            time0to100 = System.currentTimeMillis() - capturedStart
                            testState = "COMPLETED"
                            countdownProgress = 0
                        }
                    }
                }
            }
            
            EliteCard(
                backgroundColor = MeetColors.backgroundDeep, 
                shape = RoundedCornerShape(14.dp), 
                borderColor = when(testState) {
                    "JUMP_START" -> MeetColors.error
                    "COUNTDOWN" -> MeetColors.warning
                    "RUNNING" -> MeetColors.neonGreen
                    else -> MeetColors.borderBlue
                },
                glowColor = when(testState) {
                    "JUMP_START" -> MeetColors.error.copy(alpha = 0.3f)
                    "COUNTDOWN" -> MeetColors.warning.copy(alpha = 0.2f)
                    "RUNNING" -> MeetColors.neonGreen.copy(alpha = 0.3f)
                    else -> Color.Transparent
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween, 
                        modifier = Modifier.fillMaxWidth(), 
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (isSpanish) "🏁 DESAFÍO DE ACELERACIÓN 0-100" else "🏁 0-100 PERFORMANCE RUN", 
                            color = MeetColors.warning, 
                            style = MaterialTheme.typography.labelSmall, 
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        
                        Button(
                            onClick = { 
                                if (testState == "IDLE" || testState == "COMPLETED" || testState == "JUMP_START") {
                                    time0to100 = null
                                    startTime = null
                                    currentDuration = 0L
                                    testState = "WAITING_FOR_STOP"
                                    countdownProgress = 0
                                } else {
                                    testState = "IDLE"
                                    countdownProgress = 0
                                }
                            }, 
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (testState != "IDLE" && testState != "COMPLETED" && testState != "JUMP_START") MeetColors.error.copy(alpha = 0.15f) else MeetColors.warning.copy(alpha = 0.15f),
                                contentColor = if (testState != "IDLE" && testState != "COMPLETED" && testState != "JUMP_START") MeetColors.error else MeetColors.warning
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (testState != "IDLE" && testState != "COMPLETED" && testState != "JUMP_START") MeetColors.error.copy(alpha = 0.5f) else MeetColors.warning.copy(alpha = 0.4f))
                        ) {
                            Text(
                                if (testState != "IDLE" && testState != "COMPLETED" && testState != "JUMP_START") {
                                    if (isSpanish) "CANCELAR" else "CANCEL"
                                } else {
                                    if (isSpanish) "INICIAR" else "LAUNCH"
                                }, 
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Render Drag strip Christmas Tree lights!
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MeetColors.backgroundDark, RoundedCornerShape(8.dp))
                            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Light 1 (Red/Amber)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (countdownProgress >= 1 && countdownProgress != 5) MeetColors.warning else Color(0xFF222222))
                                .border(1.dp, if (countdownProgress >= 1 && countdownProgress != 5) MeetColors.warning else Color.DarkGray, CircleShape)
                                .neonGlow(if (countdownProgress >= 1 && countdownProgress != 5) MeetColors.warning else Color.Transparent, CircleShape)
                        )
                        // Light 2 (Red/Amber)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (countdownProgress >= 2 && countdownProgress != 5) MeetColors.warning else Color(0xFF222222))
                                .border(1.dp, if (countdownProgress >= 2 && countdownProgress != 5) MeetColors.warning else Color.DarkGray, CircleShape)
                                .neonGlow(if (countdownProgress >= 2 && countdownProgress != 5) MeetColors.warning else Color.Transparent, CircleShape)
                        )
                        // Light 3 (Red/Amber)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (countdownProgress >= 3 && countdownProgress != 5) MeetColors.warning else Color(0xFF222222))
                                .border(1.dp, if (countdownProgress >= 3 && countdownProgress != 5) MeetColors.warning else Color.DarkGray, CircleShape)
                                .neonGlow(if (countdownProgress >= 3 && countdownProgress != 5) MeetColors.warning else Color.Transparent, CircleShape)
                        )
                        // Light 4 (Green)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (countdownProgress == 4) MeetColors.neonGreen else Color(0xFF222222))
                                .border(1.dp, if (countdownProgress == 4) MeetColors.neonGreen else Color.DarkGray, CircleShape)
                                .neonGlow(if (countdownProgress == 4) MeetColors.neonGreen else Color.Transparent, CircleShape)
                        )
                        // Light 5 (Red Light Warning/Jump Start)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (countdownProgress == 5) MeetColors.error else Color(0xFF222222))
                                .border(1.dp, if (countdownProgress == 5) MeetColors.error else Color.DarkGray, CircleShape)
                                .neonGlow(if (countdownProgress == 5) MeetColors.error else Color.Transparent, CircleShape)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Speed and Timer HUD
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        when (testState) {
                            "IDLE" -> {
                                Text(
                                    if (isSpanish) "SISTEMA LISTO" else "SYSTEM READY",
                                    color = MeetColors.textSecondary,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    if (isSpanish) "Presiona iniciar para calibrar sensor" else "Press start to calibrate sensors",
                                    color = MeetColors.textMuted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            "WAITING_FOR_STOP" -> {
                                val blinkAlpha by infiniteTransition.animateFloat(
                                    initialValue = 0.4f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(600, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    )
                                )
                                Text(
                                    if (isSpanish) "DETEN EL VEHÍCULO POR COMPLETO" else "STOP VEHICLE COMPLETELY",
                                    color = MeetColors.warning,
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.graphicsLayer { alpha = blinkAlpha }
                                )
                            }
                            "COUNTDOWN" -> {
                                Text(
                                    if (isSpanish) "¡PREPÁRATE PARA ARRANCAR!" else "PREPARE TO LAUNCH!",
                                    color = MeetColors.warning,
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                            "JUMP_START" -> {
                                Text(
                                    if (isSpanish) "⚠️ ¡FALSA SALIDA DETECTADA!" else "⚠️ JUMP START DETECTED!",
                                    color = MeetColors.error,
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    if (isSpanish) "Detén el vehículo y pulsa iniciar de nuevo" else "Stop the vehicle and restart launch setup",
                                    color = MeetColors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                            "RUNNING" -> {
                                Text(
                                    text = "${String.format("%.2f", currentDuration / 1000f)}s",
                                    color = MeetColors.neonGreen,
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.Black,
                                    lineHeight = 44.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    if (isSpanish) "¡ACELERA A FONDO!" else "LAUNCH ACTIVE! GO GO GO!",
                                    color = MeetColors.neonGreen.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            "COMPLETED" -> {
                                val result = time0to100 ?: 0L
                                Text(
                                    text = "${String.format("%.2f", result / 1000f)}s",
                                    color = Color.White,
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.Black,
                                    lineHeight = 44.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    if (isSpanish) "¡CORRIDA DE ACELERACIÓN COMPLETA!" else "ACCELERATION RUN COMPLETED!",
                                    color = MeetColors.neonGreen,
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (isSpanish) "Velocidad: ${animatedSpeed.toInt()} km/h" else "Speed: ${animatedSpeed.toInt()} km/h", 
                            color = MeetColors.textSecondary, 
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        
        // 8. Battery Diagnostics (Visual gauge style)
        item {
            val rawVoltage = liveData["0142"] ?: liveData["AT RV"] ?: 0f
            val voltage = if (rawVoltage > 18f || rawVoltage < 0f) 0f else rawVoltage
            
            // Smooth animated voltage
            val animatedVoltage by animateFloatAsState(
                targetValue = voltage,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 60f
                ), label = "voltageAnim"
            )
            
            val batteryState = when {
                voltage < 0.1f -> {
                    if (isSpanish) Pair("CONECTANDO...", MeetColors.textMuted) else Pair("CONNECTING...", MeetColors.textMuted)
                }
                voltage < 10f -> {
                    if (isSpanish) Pair("REEMPLAZAR BATERÍA (Tensión Crítica)", MeetColors.error) else Pair("REPLACE BATTERY (Critical Tension)", MeetColors.error)
                }
                voltage in 10f..11.8f -> {
                    if (isSpanish) Pair("CARGA BAJA (Requiere recarga)", MeetColors.warning) else Pair("LOW CHARGE (Recharge required)", MeetColors.warning)
                }
                voltage in 11.9f..12.8f -> {
                    if (isSpanish) Pair("BATERÍA OK (Vehículo en reposo)", MeetColors.neonGreen) else Pair("BATTERY OK (Vehicle stationary)", MeetColors.neonGreen)
                }
                voltage in 12.9f..14.8f -> {
                    if (isSpanish) Pair("ALTERNADOR OK (Cargando batería)", MeetColors.neonGreen) else Pair("ALTERNATOR OK (Charging battery)", MeetColors.neonGreen)
                }
                voltage > 14.8f -> {
                    if (isSpanish) Pair("SOBREVOLTAJE (Fallo de alternador)", MeetColors.error) else Pair("OVERVOLTAGE (Alternator regulator fail)", MeetColors.error)
                }
                else -> {
                    if (isSpanish) Pair("LEYENDO...", MeetColors.textMuted) else Pair("READING...", MeetColors.textMuted)
                }
            }
            
            EliteCard(
                backgroundColor = MeetColors.backgroundDeep, 
                shape = RoundedCornerShape(14.dp), 
                borderColor = batteryState.second.copy(alpha = 0.4f),
                glowColor = batteryState.second.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        if (isSpanish) "🔋 MONITOREO DE SISTEMA ELÉCTRICO" else "🔋 ELECTRICAL SYSTEM MONITOR", 
                        color = batteryState.second.copy(alpha = 0.7f), 
                        style = MaterialTheme.typography.labelSmall, 
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${String.format("%.1f", animatedVoltage)} V",
                                color = Color.White,
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Black,
                                lineHeight = 38.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = batteryState.first,
                                color = batteryState.second,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        // Small retro battery drawing representing progress
                        Box(
                            modifier = Modifier
                                .size(width = 64.dp, height = 36.dp)
                                .border(2.dp, MeetColors.textSecondary, RoundedCornerShape(4.dp))
                                .padding(2.dp)
                        ) {
                            val fraction = (animatedVoltage / 15f).coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(batteryState.second.copy(alpha = 0.5f), batteryState.second)
                                        ),
                                        shape = RoundedCornerShape(2.dp)
                                    )
                                    .neonGlow(batteryState.second, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Technical reference guide
                    Text(
                        if (isSpanish) {
                            "Guía de Referencia:\n• 12.0V a 12.6V: Tensión normal de reposo\n• 13.5V a 14.5V: Tensión normal con motor en marcha"
                        } else {
                            "Reference Guide:\n• 12.0V to 12.6V: Normal stationary charge\n• 13.5V to 14.5V: Normal charging range (engine running)"
                        },
                        color = MeetColors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 16.sp
                    )
                }
            }
        }
        
        // ═══════════════════════════════════════════════════════════════════════
        // 9. G-FORCE & INCLINOMETER TACTICAL MODULE (Phone Sensors)
        // ═══════════════════════════════════════════════════════════════════════
        item {
            val lateralG by viewModel.lateralG.collectAsState()
            val longitudinalG by viewModel.longitudinalG.collectAsState()
            val pitch by viewModel.phonePitch.collectAsState()
            val roll by viewModel.phoneRoll.collectAsState()
            
            // Smooth animated values for ultra-fluid visual sweeps
            val animLatG by animateFloatAsState(
                targetValue = lateralG,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 35f),
                label = "latGAnim"
            )
            val animLongG by animateFloatAsState(
                targetValue = longitudinalG,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 35f),
                label = "longGAnim"
            )
            val animPitch by animateFloatAsState(
                targetValue = pitch,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 40f),
                label = "pitchAnim"
            )
            val animRoll by animateFloatAsState(
                targetValue = roll,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 40f),
                label = "rollAnim"
            )
            
            val totalG = sqrt(animLatG * animLatG + animLongG * animLongG)
            val animTotalG by animateFloatAsState(
                targetValue = totalG,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 35f),
                label = "totalGAnim"
            )
            
            // Determine G-force severity color
            val gColor = when {
                animTotalG > 1.0f -> MeetColors.error
                animTotalG > 0.5f -> MeetColors.warning
                animTotalG > 0.2f -> MeetColors.cyberCyan
                else -> MeetColors.neonGreen
            }
            
            EliteCard(
                backgroundColor = MeetColors.backgroundDeep,
                borderColor = gColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
                glowColor = gColor.copy(alpha = 0.2f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // ── Header ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                if (isSpanish) "📡 MEDIDOR TÁCTICO G-FORCE" else "📡 TACTICAL G-FORCE METER",
                                color = gColor.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                if (isSpanish) "Sensores internos del teléfono" else "Phone internal sensors",
                                color = MeetColors.textMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        // Calibrate / Zero button
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { viewModel.calibratePhoneSensors() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MeetColors.neonGreen.copy(alpha = 0.12f),
                                    contentColor = MeetColors.neonGreen
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.4f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    if (isSpanish) "CALIBRAR" else "ZERO",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Button(
                                onClick = { viewModel.resetPhoneSensorCalibration() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MeetColors.textMuted.copy(alpha = 0.1f),
                                    contentColor = MeetColors.textMuted
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.borderSubtle),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "RESET",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // ── G-Force Target Graph (Racing telemetry style) ──
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(MeetColors.backgroundDark, RoundedCornerShape(12.dp))
                            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f
                            val maxRadius = size.minDimension / 2f * 0.9f
                            val maxG = 1.5f // 1.5G max ring
                            
                            // Draw concentric target rings (0.5G, 1.0G, 1.5G)
                            val ringColors = listOf(
                                Color(0xFF1B3A2A),  // 0.5G - dark green
                                Color(0xFF3A3A1B),  // 1.0G - dark yellow
                                Color(0xFF3A1B1B)   // 1.5G - dark red
                            )
                            val ringValues = listOf(0.5f, 1.0f, 1.5f)
                            
                            for (i in ringValues.indices.reversed()) {
                                val ringRadius = (ringValues[i] / maxG) * maxRadius
                                drawCircle(
                                    color = ringColors[i],
                                    radius = ringRadius,
                                    center = Offset(centerX, centerY),
                                    style = Stroke(width = 1.5f)
                                )
                            }
                            
                            // Draw crosshairs
                            val crosshairColor = Color.White.copy(alpha = 0.08f)
                            drawLine(crosshairColor, Offset(centerX, centerY - maxRadius), Offset(centerX, centerY + maxRadius), 1f)
                            drawLine(crosshairColor, Offset(centerX - maxRadius, centerY), Offset(centerX + maxRadius, centerY), 1f)
                            
                            // Draw G-force position dot
                            val dotX = centerX + (animLatG / maxG) * maxRadius
                            val dotY = centerY - (animLongG / maxG) * maxRadius // Inverted Y: forward = up
                            
                            // Clamp to visible area
                            val clampedDotX = dotX.coerceIn(centerX - maxRadius, centerX + maxRadius)
                            val clampedDotY = dotY.coerceIn(centerY - maxRadius, centerY + maxRadius)
                            
                            // Glow trail from center to dot
                            drawLine(
                                color = gColor.copy(alpha = 0.3f),
                                start = Offset(centerX, centerY),
                                end = Offset(clampedDotX, clampedDotY),
                                strokeWidth = 3f,
                                cap = StrokeCap.Round
                            )
                            
                            // Outer glow
                            drawCircle(
                                color = gColor.copy(alpha = 0.25f),
                                radius = 18f,
                                center = Offset(clampedDotX, clampedDotY)
                            )
                            // Inner bright dot
                            drawCircle(
                                color = gColor,
                                radius = 8f,
                                center = Offset(clampedDotX, clampedDotY)
                            )
                            // Core white dot
                            drawCircle(
                                color = Color.White,
                                radius = 3f,
                                center = Offset(clampedDotX, clampedDotY)
                            )
                        }
                        
                        // Ring labels overlay
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "0.5G  |  1.0G  |  1.5G",
                                color = MeetColors.textMuted.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // ── G-Force Digital Readouts ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Total G
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${String.format("%.2f", animTotalG)}G",
                                color = gColor,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                lineHeight = 28.sp
                            )
                            Text(
                                "TOTAL",
                                color = MeetColors.textMuted,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        // Lateral G
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${String.format("%+.2f", animLatG)}G",
                                color = MeetColors.cyberCyan,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (isSpanish) "LATERAL" else "LATERAL",
                                color = MeetColors.textMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        // Longitudinal G
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${String.format("%+.2f", animLongG)}G",
                                color = MeetColors.electricBlue,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (isSpanish) "LONGITUDINAL" else "LONGITUDINAL",
                                color = MeetColors.textMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // ── Inclinometer Section (Pitch & Roll) ──
                    Text(
                        if (isSpanish) "INCLINÓMETRO" else "INCLINOMETER",
                        color = MeetColors.electricBlue.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Pitch bar
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (isSpanish) "Cabeceo (Pitch)" else "Pitch",
                                color = MeetColors.textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${String.format("%+.1f", animPitch)}°",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .background(MeetColors.backgroundDark, RoundedCornerShape(6.dp))
                                .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(6.dp))
                        ) {
                            // Center marker
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(2.dp)
                                    .align(Alignment.Center)
                                    .background(Color.White.copy(alpha = 0.15f))
                            )
                            // Pitch indicator (centered at 50%, moves left/right based on pitch)
                            val pitchFraction = (animPitch / 90f).coerceIn(-1f, 1f)
                            val pitchOffset = (0.5f + pitchFraction * 0.45f)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(8.dp)
                                    .align(Alignment.CenterStart)
                                    .offset(x = with(LocalDensity.current) { (pitchOffset * 300f).dp })
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(MeetColors.electricBlue, MeetColors.electricBlue.copy(alpha = 0.5f))
                                        ),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .neonGlow(MeetColors.electricBlue, RoundedCornerShape(4.dp))
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Roll bar
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (isSpanish) "Alabeo (Roll)" else "Roll",
                                color = MeetColors.textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${String.format("%+.1f", animRoll)}°",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .background(MeetColors.backgroundDark, RoundedCornerShape(6.dp))
                                .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(6.dp))
                        ) {
                            // Center marker
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(2.dp)
                                    .align(Alignment.Center)
                                    .background(Color.White.copy(alpha = 0.15f))
                            )
                            // Roll indicator
                            val rollFraction = (animRoll / 90f).coerceIn(-1f, 1f)
                            val rollOffsetVal = (0.5f + rollFraction * 0.45f)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(8.dp)
                                    .align(Alignment.CenterStart)
                                    .offset(x = with(LocalDensity.current) { (rollOffsetVal * 300f).dp })
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(MeetColors.hotMagenta, MeetColors.hotMagenta.copy(alpha = 0.5f))
                                        ),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .neonGlow(MeetColors.hotMagenta, RoundedCornerShape(4.dp))
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (isSpanish) "Inclina tu teléfono para ver las mediciones en vivo. Pulsa CALIBRAR para poner a cero la posición actual."
                        else "Tilt your phone to see live readings. Press ZERO to calibrate from the current position.",
                        color = MeetColors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
