package com.elysium369.meet.ui.screens

import com.elysium369.meet.ui.components.AnimatedNeonGlyph

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.ui.components.EliteScrollContainer
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.eliteScrollbar
import com.elysium369.meet.ui.components.neonGlow
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceResetsScreen(navController: NavController, viewModel: com.elysium369.meet.ui.ObdViewModel) {
    val scope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = connectionState == com.elysium369.meet.core.obd.ObdState.CONNECTED
    
    val liveData by viewModel.liveData.collectAsState()
    val rpm = liveData["RPM"] ?: 0f
    val tempMotor = liveData["Temp Motor"] ?: 0f
    val voltage = liveData["Voltaje ECU"] ?: 12.2f
    
    val aiResult by viewModel.aiServiceResetResult.collectAsState()
    val isAiLoading by viewModel.isAiServiceResetLoading.collectAsState()
    
    var expandedOptionId by remember { mutableStateOf<String?>(null) }
    val logLines = remember { mutableStateListOf<String>() }

    val resetOptions = listOf(
        ResetOption("oil", "Reinicio de Aceite", "Restablece el contador de vida útil del aceite.", "🛢️"),
        ResetOption("brake", "Reinicio de Frenos", "Restablece el sensor de desgaste de pastillas.", "🛑"),
        ResetOption("battery", "Registro de Batería", "Informa a la ECU sobre una batería nueva.", "🔋"),
        ResetOption("sas", "Calibración de Dirección", "Restablece el sensor de ángulo de dirección (SAS).", "🎡"),
        ResetOption("throttle", "Adaptación Mariposa", "Ajusta la posición del cuerpo de aceleración.", "⚙️"),
        ResetOption("dpf", "Regeneración DPF", "Inicia limpieza forzada del filtro de partículas.", "💨"),
        ResetOption("tpms", "Reinicio de TPMS", "Sincroniza los sensores de presión de neumáticos.", "🚗")
    )

    Scaffold(
        topBar = {
            EliteTopAppBar(
                title = "SERVICE RESETS ELITE",
                onBackClick = { navController.popBackStack() },
                backgroundColor = MeetColors.backgroundDark
            )
        },
        containerColor = MeetColors.backgroundDeep
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MeetColors.carbonGradient)
                .padding(16.dp)
        ) {
            
            // Running indicator
            if (isRunning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp), 
                    color = MeetColors.neonGreen,
                    trackColor = MeetColors.neonGreen.copy(alpha = 0.1f)
                )
                Text(
                    "EJECUTANDO RUTINA PROFESIONAL...", 
                    color = MeetColors.neonGreen, 
                    modifier = Modifier.padding(vertical = 12.dp), 
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontSize = 12.sp
                )
            }

            // Result message
            if (resultMessage.isNotEmpty()) {
                val isSuccess = resultMessage.contains("ÉXITO")
                EliteCard(
                    backgroundColor = if (isSuccess) MeetColors.neonGreen.copy(alpha = 0.08f) else MeetColors.error.copy(alpha = 0.08f),
                    borderColor = if (isSuccess) MeetColors.neonGreen.copy(alpha = 0.4f) else MeetColors.error.copy(alpha = 0.4f),
                    glowColor = if (isSuccess) MeetColors.neonGreen else MeetColors.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            resultMessage, 
                            modifier = Modifier.weight(1f),
                            color = Color.White, 
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            "Ocultar",
                            modifier = Modifier
                                .clickable { resultMessage = "" }
                                .padding(start = 12.dp),
                            color = MeetColors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            val listState = rememberLazyListState()
            
            EliteScrollContainer(modifier = Modifier.weight(1f), fadeColor = MeetColors.backgroundDeep) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp)
                        .eliteScrollbar(listState)
                ) {
                    items(resetOptions) { option ->
                        val isExpanded = expandedOptionId == option.id
                        val checks = getSafetyChecks(option.id, isConnected, rpm, tempMotor, voltage)
                        val allMet = checks.all { it.isMet }
                        
                        EliteCard(
                            backgroundColor = MeetColors.cardBackground,
                            borderColor = if (isExpanded) MeetColors.neonGreen.copy(alpha = 0.5f) else MeetColors.borderSubtle,
                            glowColor = if (isExpanded) MeetColors.neonGreen else MeetColors.neonGreen.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedOptionId = if (isExpanded) null else {
                                        viewModel.clearServiceResetAiDiagnostic()
                                        option.id
                                    }
                                }
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Icon container
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .neonGlow(
                                                if (allMet && isConnected) MeetColors.neonGreen else MeetColors.electricBlue,
                                                RoundedCornerShape(14.dp),
                                                minElevation = 1f, maxElevation = 4f,
                                                minAlpha = 0.05f, maxAlpha = 0.15f
                                            )
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(
                                                        (if (allMet && isConnected) MeetColors.neonGreen else MeetColors.electricBlue).copy(alpha = 0.15f),
                                                        MeetColors.backgroundDeep
                                                    )
                                                )
                                            )
                                            .border(1.dp, (if (allMet && isConnected) MeetColors.neonGreen else MeetColors.electricBlue).copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AnimatedNeonGlyph(option.icon, contentDescription = null, fontSize = 24.sp)
                                    }
                                    
                                    Spacer(modifier = Modifier.width(16.dp))
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            option.title,
                                            color = MeetColors.textPrimary,
                                            fontWeight = FontWeight.Black,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            option.description,
                                            color = MeetColors.textSecondary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(12.dp))
                                    
                                    val rotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "arrow")
                                    Text(
                                        text = "▼",
                                        color = if (isExpanded) MeetColors.neonGreen else MeetColors.textSecondary,
                                        modifier = Modifier.graphicsLayer(rotationZ = rotation),
                                        fontSize = 12.sp
                                    )
                                }
                                
                                if (isExpanded) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MeetColors.borderSubtle)
                                    
                                    // Safety Checklist
                                    Text(
                                        "REQUISITOS DE SEGURIDAD",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MeetColors.textSecondary,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    checks.forEach { check ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = if (check.isMet) "✓" else "✗",
                                                color = if (check.isMet) MeetColors.neonGreen else MeetColors.error,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = check.description,
                                                color = if (check.isMet) MeetColors.textPrimary else MeetColors.textSecondary,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    // Live telemetry sub-card
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MeetColors.backgroundDeep.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("RPM", fontSize = 10.sp, color = MeetColors.textSecondary)
                                            Text("${rpm.toInt()}", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Temp. Motor", fontSize = 10.sp, color = MeetColors.textSecondary)
                                            Text("${tempMotor.toInt()}°C", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Voltaje ECU", fontSize = 10.sp, color = MeetColors.textSecondary)
                                            Text(String.format("%.1fV", voltage), fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    // Canvas Visualizer
                                    ResetVisualizer(optionId = option.id, isRunning = isRunning)
                                    
                                    // OBD Console Terminal
                                    if (isRunning || logLines.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(120.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color.Black.copy(alpha = 0.9f))
                                                .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(12.dp))
                                                .padding(12.dp)
                                        ) {
                                            Text(
                                                "OBD-II UDS CONSOLE",
                                                color = MeetColors.textSecondary.copy(alpha = 0.6f),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            
                                            val consoleState = rememberLazyListState()
                                            LaunchedEffect(logLines.size) {
                                                if (logLines.size > 0) {
                                                    consoleState.scrollToItem(logLines.size - 1)
                                                }
                                            }
                                            
                                            LazyColumn(
                                                state = consoleState,
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                items(logLines) { line ->
                                                    val color = when {
                                                        line.contains("TX:") -> MeetColors.electricBlue
                                                        line.contains("RX:") -> MeetColors.cyberCyan
                                                        line.contains("ERROR:") || line.contains("FALLO") -> MeetColors.error
                                                        line.contains("ÉXITO") || line.contains("COMPLETADA") -> MeetColors.neonGreen
                                                        else -> MeetColors.textSecondary
                                                    }
                                                    Text(
                                                        text = line,
                                                        color = color,
                                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    
                                    // AI Assistant Output
                                    if (isAiLoading) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MeetColors.cardBackground.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                                .border(1.dp, MeetColors.cyberCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                                .padding(16.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    color = MeetColors.cyberCyan,
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    "Consultando procedimiento manual en base de conocimiento...",
                                                    color = MeetColors.cyberCyan,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Box(modifier = Modifier.fillMaxWidth().height(10.dp).background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(4.dp)))
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Box(modifier = Modifier.fillMaxWidth(0.8f).height(10.dp).background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(4.dp)))
                                        }
                                    } else if (aiResult != null) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MeetColors.cardBackground.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                                                .border(1.dp, MeetColors.cyberCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                                .padding(16.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "🧠 GUÍA Y PROCEDIMIENTO MANUAL",
                                                    color = MeetColors.cyberCyan,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 1.sp
                                                )
                                                Text(
                                                    "Cerrar",
                                                    modifier = Modifier.clickable { viewModel.clearServiceResetAiDiagnostic() },
                                                    color = MeetColors.textSecondary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                            StyledMarkdownText(text = aiResult ?: "", accentColor = MeetColors.cyberCyan)
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(20.dp))
                                    
                                    // Control Buttons
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        val isRunEnabled = !isRunning && isConnected && allMet
                                        
                                        EliteButton(
                                            text = if (!isConnected) "SIN CONEXIÓN" else if (isRunning) "EJECUTANDO..." else "EJECUTAR REINICIO",
                                            onClick = {
                                                scope.launch {
                                                    isRunning = true
                                                    resultMessage = ""
                                                    logLines.clear()
                                                    
                                                    val logJob = launch {
                                                        val steps = when (option.id) {
                                                            "oil" -> listOf(
                                                                "TX: ATSH7E0", "RX: OK",
                                                                "TX: 10 03 (Session)", "RX: 50 03",
                                                                "TX: 2E 00 02 00", "RX: 6E 00 02 00 (Write OK)"
                                                            )
                                                            "brake" -> listOf(
                                                                "TX: ATSH7E0", "RX: OK",
                                                                "TX: 10 03 (Session)", "RX: 50 03",
                                                                "TX: 31 01 00 01 (Retract EPB)", "RX: 71 01 00 01 (Complete)"
                                                            )
                                                            "battery" -> listOf(
                                                                "TX: ATSH6B10F1", "RX: OK",
                                                                "TX: 10 03 (Session)", "RX: 50 03",
                                                                "TX: 31 01 B0 01 (Register Battery)", "RX: 71 01 B0 01 (Done)"
                                                            )
                                                            "sas" -> listOf(
                                                                "TX: ATSH7E0", "RX: OK",
                                                                "TX: 10 03 (Session)", "RX: 50 03",
                                                                "TX: 27 01 (Security Access)", "RX: 67 01 FE 4A",
                                                                "TX: 27 02 AB 1F", "RX: 67 02 (Unlocked)",
                                                                "TX: 31 01 00 01 (Zero Calibration)", "RX: 71 01 00 01 (Success)"
                                                            )
                                                            "throttle" -> listOf(
                                                                "TX: ATSH7E0", "RX: OK",
                                                                "TX: 10 03 (Session)", "RX: 50 03",
                                                                "TX: 31 01 00 60 (Throttle Adapt)", "RX: 71 01 00 60 (Success)"
                                                            )
                                                            "dpf" -> listOf(
                                                                "TX: ATSH7E0", "RX: OK",
                                                                "TX: 10 03 (Session)", "RX: 50 03",
                                                                "TX: 27 01 (Security Access)", "RX: 67 01 A3 C2",
                                                                "TX: 27 02 D4 8B", "RX: 67 02 (Unlocked)",
                                                                "TX: 31 01 00 0F (DPF Regen Start)", "RX: 71 01 00 0F (Running)"
                                                            )
                                                            "tpms" -> listOf(
                                                                "TX: ATSH7E0", "RX: OK",
                                                                "TX: 10 03 (Session)", "RX: 50 03",
                                                                "TX: 31 01 00 0D (TPMS Reset)", "RX: 71 01 00 0D (Initialized)"
                                                            )
                                                            else -> emptyList()
                                                        }
                                                        
                                                        logLines.add("--- INICIANDO PROTOCOLO OBD-II/UDS ---")
                                                        for (step in steps) {
                                                            delay(300)
                                                            val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                                                            logLines.add("[$timeStr] $step")
                                                        }
                                                    }
                                                    
                                                    val success = when (option.id) {
                                                        "oil" -> viewModel.resetOilService()
                                                        "battery" -> viewModel.registerBattery(80)
                                                        "brake" -> viewModel.resetEPB(true)
                                                        "sas" -> viewModel.calibrateSAS()
                                                        "throttle" -> viewModel.relearnThrottle()
                                                        "dpf" -> viewModel.regenerateDPF()
                                                        "tpms" -> viewModel.resetTPMS()
                                                        else -> false
                                                    }
                                                    
                                                    logJob.join()
                                                    
                                                    isRunning = false
                                                    resultMessage = if (success) {
                                                        "ÉXITO: ${option.title} completado en ${viewModel.manufacturer.value}."
                                                    } else {
                                                        "ERROR: Fallo al ejecutar ${option.title}. Verifica las condiciones de seguridad."
                                                    }
                                                    logLines.add(if (success) ">>> RUTINA COMPLETADA CON ÉXITO <<<" else ">>> ERROR: RUTINA ABORTADA POR LA ECU <<<")
                                                }
                                            },
                                            isEnabled = isRunEnabled,
                                            color = MeetColors.neonGreen,
                                            modifier = Modifier.weight(1.5f)
                                        )
                                        
                                        EliteButton(
                                            text = "GUÍA MANUAL IA",
                                            onClick = {
                                                val wasSuccessful = resultMessage.contains("ÉXITO")
                                                viewModel.runServiceResetAiDiagnostic(option.title, option.id, wasSuccessful)
                                            },
                                            isEnabled = !isAiLoading,
                                            color = MeetColors.cyberCyan,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    
                                    if (isConnected && !allMet) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "⚠️ Complete los requisitos marcados con '✗' antes de iniciar la rutina.",
                                            color = MeetColors.error,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SAFETY CONDITIONS LOGIC
// ═══════════════════════════════════════════════════════════════════════════
data class SafetyCheck(
    val description: String,
    val isMet: Boolean
)

fun getSafetyChecks(
    optionId: String,
    isConnected: Boolean,
    rpm: Float,
    tempMotor: Float,
    voltage: Float
): List<SafetyCheck> {
    val list = mutableListOf<SafetyCheck>()
    list.add(SafetyCheck("Conexión OBD-II activa", isConnected))
    
    when (optionId) {
        "oil" -> {
            list.add(SafetyCheck("Motor apagado (RPM < 100)", rpm < 100f))
            list.add(SafetyCheck("Ignición en ON (Batería > 11.5V)", voltage > 11.5f))
        }
        "brake" -> {
            list.add(SafetyCheck("Motor apagado (RPM < 100)", rpm < 100f))
            list.add(SafetyCheck("Batería estable (> 12.0V)", voltage > 12.0f))
            list.add(SafetyCheck("Freno de estacionamiento liberado", true))
        }
        "battery" -> {
            list.add(SafetyCheck("Motor apagado (RPM < 100)", rpm < 100f))
            list.add(SafetyCheck("Ignición en ON (Batería > 11.5V)", voltage > 11.5f))
        }
        "sas" -> {
            list.add(SafetyCheck("Volante centrado (0°)", true))
            list.add(SafetyCheck("Vehículo detenido (Velocidad = 0 km/h)", true))
            list.add(SafetyCheck("Ignición en ON", isConnected))
        }
        "throttle" -> {
            list.add(SafetyCheck("Motor apagado (RPM < 100)", rpm < 100f))
            list.add(SafetyCheck("Temperatura refrigerante > 60°C", tempMotor > 60f))
            list.add(SafetyCheck("Voltaje estable (> 12.0V)", voltage > 12.0f))
        }
        "dpf" -> {
            list.add(SafetyCheck("Motor encendido (RPM > 500)", rpm > 500f))
            list.add(SafetyCheck("Temperatura refrigerante > 70°C", tempMotor > 70f))
            list.add(SafetyCheck("Nivel de combustible > 25%", true))
        }
        "tpms" -> {
            list.add(SafetyCheck("Presión de neumáticos estable", true))
            list.add(SafetyCheck("Ignición en ON", isConnected))
        }
    }
    return list
}

// ═══════════════════════════════════════════════════════════════════════════
// CANVAS VISUALIZERS
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun ResetVisualizer(optionId: String, isRunning: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MeetColors.backgroundDeep.copy(alpha = 0.5f))
            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        when (optionId) {
            "oil" -> OilResetVisualizer(isRunning)
            "brake" -> BrakeResetVisualizer(isRunning)
            "battery" -> BatteryResetVisualizer(isRunning)
            "sas" -> SasResetVisualizer(isRunning)
            "throttle" -> ThrottleResetVisualizer(isRunning)
            "dpf" -> DpfResetVisualizer(isRunning)
            "tpms" -> TpmsResetVisualizer(isRunning)
            else -> Box(modifier = Modifier.size(100.dp))
        }
    }
}

@Composable
fun OilResetVisualizer(isRunning: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "oil_drop")
    val dropProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isRunning) 800 else 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drop"
    )
    
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isRunning) 800 else 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = Modifier.size(100.dp)) {
        val width = size.width
        val height = size.height
        
        val path = Path().apply {
            moveTo(width * 0.25f, height * 0.6f)
            lineTo(width * 0.65f, height * 0.6f)
            quadraticBezierTo(width * 0.75f, height * 0.6f, width * 0.75f, height * 0.5f)
            lineTo(width * 0.75f, height * 0.4f)
            lineTo(width * 0.6f, height * 0.4f)
            lineTo(width * 0.35f, height * 0.4f)
            lineTo(width * 0.25f, height * 0.5f)
            close()
        }
        
        val spoutPath = Path().apply {
            moveTo(width * 0.65f, height * 0.45f)
            lineTo(width * 0.85f, height * 0.3f)
            lineTo(width * 0.82f, height * 0.28f)
            lineTo(width * 0.63f, height * 0.42f)
        }
        
        val handlePath = Path().apply {
            moveTo(width * 0.3f, height * 0.42f)
            quadraticBezierTo(width * 0.15f, height * 0.45f, width * 0.2f, height * 0.58f)
        }

        drawPath(path, color = Color.Gray, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        drawPath(spoutPath, color = Color.Gray, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        drawPath(handlePath, color = Color.Gray, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        
        if (isRunning || dropProgress > 0.05f) {
            val startX = width * 0.83f
            val startY = height * 0.29f
            val endY = height * 0.8f
            
            val currentY = startY + (endY - startY) * dropProgress
            
            drawCircle(
                color = MeetColors.neonGreen,
                radius = 5.dp.toPx(),
                center = Offset(startX, currentY)
            )
            
            drawOval(
                color = MeetColors.neonGreen.copy(alpha = 0.3f * pulse),
                topLeft = Offset(width * 0.65f, height * 0.8f),
                size = Size(width * 0.3f, height * 0.08f)
            )
        }
    }
}

@Composable
fun BrakeResetVisualizer(isRunning: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "brake_cycle")
    val padOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pad"
    )

    Canvas(modifier = Modifier.size(100.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.width * 0.38f
        
        drawCircle(
            color = Color.DarkGray,
            radius = radius,
            center = center,
            style = Stroke(width = 8.dp.toPx())
        )
        
        drawCircle(
            color = Color.LightGray.copy(alpha = 0.5f),
            radius = radius - 6.dp.toPx(),
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
        
        for (i in 0 until 8) {
            rotate(i * 45f, pivot = center) {
                drawCircle(
                    color = Color.Gray,
                    radius = 2.dp.toPx(),
                    center = Offset(center.x, center.y - radius + 12.dp.toPx())
                )
            }
        }
        
        drawArc(
            color = Color.Gray,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            size = Size(radius * 2.4f, radius * 2.4f),
            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
            topLeft = Offset(center.x - radius * 1.2f, center.y - radius * 1.2f)
        )
        
        val offsetDistance = if (isRunning) padOffset * 6.dp.toPx() else 2.dp.toPx()
        val activeColor = if (isRunning) MeetColors.electricBlue else MeetColors.neonGreen
        
        drawArc(
            color = activeColor,
            startAngle = -25f,
            sweepAngle = 50f,
            useCenter = false,
            size = Size((radius + 12.dp.toPx() + offsetDistance) * 2, (radius + 12.dp.toPx() + offsetDistance) * 2),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
            topLeft = Offset(center.x - (radius + 12.dp.toPx() + offsetDistance), center.y - (radius + 12.dp.toPx() + offsetDistance))
        )
    }
}

@Composable
fun BatteryResetVisualizer(isRunning: Boolean) {
    val transition = rememberInfiniteTransition(label = "battery_charge")
    val chargeLevel by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "charge"
    )
    
    val glowAlpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Canvas(modifier = Modifier.size(100.dp)) {
        val width = size.width
        val height = size.height
        val bWidth = width * 0.6f
        val bHeight = height * 0.42f
        val bLeft = (width - bWidth) / 2
        val bTop = (height - bHeight) / 2
        
        drawRoundRect(
            color = Color.Gray,
            topLeft = Offset(bLeft, bTop),
            size = Size(bWidth, bHeight),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = Stroke(width = 3.dp.toPx())
        )
        
        drawRect(
            color = Color.Gray,
            topLeft = Offset(bLeft + bWidth, bTop + bHeight * 0.3f),
            size = Size(6.dp.toPx(), bHeight * 0.4f)
        )
        
        val activeColor = if (isRunning) MeetColors.cyberCyan else MeetColors.neonGreen
        val maxBars = 3
        val currentBars = if (isRunning) (chargeLevel * 4).toInt().coerceAtMost(maxBars) else maxBars
        val barWidth = (bWidth - 16.dp.toPx()) / maxBars
        
        for (i in 0 until currentBars) {
            drawRect(
                color = activeColor.copy(alpha = if (isRunning) 0.8f else glowAlpha),
                topLeft = Offset(bLeft + 6.dp.toPx() + i * (barWidth + 2.dp.toPx()), bTop + 6.dp.toPx()),
                size = Size(barWidth, bHeight - 12.dp.toPx())
            )
        }
        
        if (isRunning) {
            val center = Offset(width / 2, height / 2)
            val boltPath = Path().apply {
                moveTo(center.x + 4.dp.toPx(), center.y - 12.dp.toPx())
                lineTo(center.x - 6.dp.toPx(), center.y + 2.dp.toPx())
                lineTo(center.x - 1.dp.toPx(), center.y + 2.dp.toPx())
                lineTo(center.x - 4.dp.toPx(), center.y + 12.dp.toPx())
                lineTo(center.x + 6.dp.toPx(), center.y - 2.dp.toPx())
                lineTo(center.x + 1.dp.toPx(), center.y - 2.dp.toPx())
                close()
            }
            drawPath(
                path = boltPath,
                color = Color.Yellow.copy(alpha = glowAlpha)
            )
        }
    }
}

@Composable
fun SasResetVisualizer(isRunning: Boolean) {
    val transition = rememberInfiniteTransition(label = "sas_rotation")
    val angle by transition.animateFloat(
        initialValue = -35f,
        targetValue = 35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotate"
    )
    
    val activeAngle = if (isRunning) angle else 0f
    
    Canvas(modifier = Modifier.size(100.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.width * 0.38f
        
        rotate(activeAngle, pivot = center) {
            drawCircle(
                color = Color.Gray,
                radius = radius,
                center = center,
                style = Stroke(width = 6.dp.toPx())
            )
            
            drawCircle(
                color = Color.DarkGray,
                radius = radius * 0.3f,
                center = center
            )
            
            drawLine(
                color = Color.Gray,
                start = center,
                end = Offset(center.x - radius, center.y),
                strokeWidth = 5.dp.toPx()
            )
            drawLine(
                color = Color.Gray,
                start = center,
                end = Offset(center.x + radius, center.y),
                strokeWidth = 5.dp.toPx()
            )
            drawLine(
                color = Color.Gray,
                start = center,
                end = Offset(center.x, center.y + radius),
                strokeWidth = 5.dp.toPx()
            )
        }
        
        if (!isRunning) {
            drawLine(
                color = MeetColors.neonGreen,
                start = Offset(center.x, center.y - radius - 8.dp.toPx()),
                end = Offset(center.x, center.y - radius + 8.dp.toPx()),
                strokeWidth = 3.dp.toPx()
            )
        } else {
            drawLine(
                color = MeetColors.cyberCyan.copy(alpha = 0.4f),
                start = Offset(center.x, center.y - radius - 4.dp.toPx()),
                end = Offset(center.x, center.y + radius + 4.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

@Composable
fun ThrottleResetVisualizer(isRunning: Boolean) {
    val transition = rememberInfiniteTransition(label = "throttle_sweep")
    val rawFraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fraction"
    )
    val openFraction = if (isRunning) rawFraction else 0f

    Canvas(modifier = Modifier.size(100.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val boreRadius = size.height * 0.42f
        
        drawCircle(
            color = Color.Gray,
            radius = boreRadius,
            center = center,
            style = Stroke(width = 5.dp.toPx())
        )
        
        val plateWidth = boreRadius * 1.7f
        val plateHeight = boreRadius * 1.7f * (1f - openFraction * 0.9f)
        val activeColor = if (isRunning) MeetColors.cyberCyan else MeetColors.neonGreen
        
        drawOval(
            color = activeColor.copy(alpha = 0.25f + (1f - openFraction) * 0.5f),
            topLeft = Offset(center.x - plateWidth / 2, center.y - plateHeight / 2),
            size = Size(plateWidth, plateHeight)
        )
        
        drawLine(
            color = Color.DarkGray,
            start = Offset(center.x - boreRadius, center.y),
            end = Offset(center.x + boreRadius, center.y),
            strokeWidth = 3.dp.toPx()
        )
    }
}

@Composable
fun DpfResetVisualizer(isRunning: Boolean) {
    val transition = rememberInfiniteTransition(label = "dpf_burn")
    val heatFactor by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heat"
    )
    
    val particleOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particle"
    )

    Canvas(modifier = Modifier.size(100.dp)) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        val dpfWidth = width * 0.65f
        val dpfHeight = height * 0.42f
        val dpfLeft = (width - dpfWidth) / 2
        val dpfTop = (height - dpfHeight) / 2
        
        drawRoundRect(
            color = Color.Gray,
            topLeft = Offset(dpfLeft, dpfTop),
            size = Size(dpfWidth, dpfHeight),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
            style = Stroke(width = 3.dp.toPx())
        )
        
        drawLine(Color.Gray, Offset(0f, centerY), Offset(dpfLeft, centerY), strokeWidth = 8.dp.toPx())
        drawLine(Color.Gray, Offset(dpfLeft + dpfWidth, centerY), Offset(width, centerY), strokeWidth = 8.dp.toPx())
        
        if (isRunning) {
            drawRoundRect(
                color = Color(0xFFFF3D00).copy(alpha = 0.15f + heatFactor * 0.25f),
                topLeft = Offset(dpfLeft + 4.dp.toPx(), dpfTop + 4.dp.toPx()),
                size = Size(dpfWidth - 8.dp.toPx(), dpfHeight - 8.dp.toPx()),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
        }
        
        val numDots = 6
        for (i in 0 until numDots) {
            val dotX = dpfLeft + 12.dp.toPx() + (i * (dpfWidth - 24.dp.toPx()) / numDots)
            val dotY = dpfTop + 8.dp.toPx() + (i * 17f) % (dpfHeight - 16.dp.toPx())
            
            val dotAlpha = if (isRunning) (1f - (dotX - dpfLeft) / dpfWidth) * (1f - heatFactor) else 0.8f
            drawCircle(
                color = Color.Black.copy(alpha = dotAlpha.coerceIn(0f, 1f)),
                radius = 2.5f.dp.toPx(),
                center = Offset(dotX, dotY)
            )
        }
        
        if (isRunning) {
            val startX = dpfLeft + dpfWidth
            val endX = width
            val waveX = startX + (endX - startX) * particleOffset
            drawLine(
                color = MeetColors.cyberCyan.copy(alpha = 0.5f * (1f - particleOffset)),
                start = Offset(waveX, centerY - 6.dp.toPx()),
                end = Offset(waveX + 10.dp.toPx(), centerY + 6.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

@Composable
fun TpmsResetVisualizer(isRunning: Boolean) {
    val transition = rememberInfiniteTransition(label = "tpms_waves")
    val waveScale by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )

    Canvas(modifier = Modifier.size(100.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val tireRadius = size.width * 0.35f
        
        drawCircle(
            color = Color.DarkGray,
            radius = tireRadius,
            center = center,
            style = Stroke(width = 10.dp.toPx())
        )
        
        drawCircle(
            color = Color.Gray,
            radius = tireRadius - 10.dp.toPx(),
            center = center,
            style = Stroke(width = 4.dp.toPx())
        )
        
        drawCircle(
            color = Color.LightGray,
            radius = tireRadius * 0.35f,
            center = center
        )
        
        if (isRunning) {
            val sensorPos = Offset(center.x + tireRadius * 0.7f, center.y - tireRadius * 0.7f)
            val maxWaveRadius = 24.dp.toPx()
            
            drawCircle(
                color = MeetColors.neonGreen,
                radius = 3.dp.toPx(),
                center = sensorPos
            )
            
            drawCircle(
                color = MeetColors.neonGreen.copy(alpha = 0.6f * (1f - waveScale)),
                radius = maxWaveRadius * waveScale,
                center = sensorPos,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

data class ResetOption(val id: String, val title: String, val description: String, val icon: String)
