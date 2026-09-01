package com.elysium369.meet.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.obd.*
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.*
import com.elysium369.meet.ui.theme.MeetColors
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OscilloscopeScreen(
    onNavigateBack: () -> Unit,
    viewModel: ObdViewModel
) {
    // Mode selection: 0 = OBD live stream, 1 = physical USB Hantek.
    var activeTab by remember { mutableIntStateOf(0) }

    // Safety warning states for physical mode
    var safetyAcknowledged by remember { mutableStateOf(false) }
    var showSafetyDialog by remember { mutableStateOf(false) }
    var safetyChecked by remember { mutableStateOf(false) }

    // AI Analysis states for physical mode
    var isAnalyzingUsb by remember { mutableStateOf(false) }
    var usbDiagnosis by remember { mutableStateOf<com.elysium369.meet.core.ai.DiagnosticResult?>(null) }

    // ─── 1. STATE FOR OBD VIRTUAL OSCILLOSCOPE ───
    val allPids = remember { PidSignalRegistry.SIGNALS }
    var selectedPid by remember { mutableStateOf(allPids[0]) }
    val obdIsRunning by viewModel.isOscilloscopeRunning.collectAsState()
    val activeOscilloscopePid by viewModel.activeOscilloscopePid.collectAsState()
    val dataBuffer by viewModel.oscilloscopeCapture.collectAsState()
    val captureStartTime by viewModel.oscilloscopeStartedAt.collectAsState()
    var showPidPicker by remember { mutableStateOf(false) }
    val analyzer = remember { SignalAnalyzer() }
    var obdDiagnosis by remember { mutableStateOf<SignalDiagnosis?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var captureCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(activeOscilloscopePid) {
        activeOscilloscopePid?.let { activeCode ->
            allPids.firstOrNull { it.code == activeCode }?.let { selectedPid = it }
        }
    }

    // Auto-analyze when stopped with data
    LaunchedEffect(obdIsRunning) {
        if (activeTab == 0 && !obdIsRunning && dataBuffer.size > 20) {
            isAnalyzing = true
            kotlinx.coroutines.delay(800)
            val dur = System.currentTimeMillis() - captureStartTime
            obdDiagnosis = analyzer.analyze(dataBuffer.map { it.second }, dur, selectedPid)
            isAnalyzing = false
            captureCount++
            // Save to session
            viewModel.saveOscilloscopeCapture(
                pidCode = selectedPid.code,
                pidName = selectedPid.name,
                values = dataBuffer.map { it.second },
                durationMs = dur,
                diagnosisSeverity = obdDiagnosis?.severity ?: "normal",
                diagnosisText = obdDiagnosis?.diagnosisText ?: "",
                recommendationText = obdDiagnosis?.recommendationText ?: ""
            )
        }
    }

    // ─── 2. STATE FOR PHYSICAL USB OSCILLOSCOPE (Hantek 6022BE) ───
    val usbCh1Data by viewModel.usbCh1Data.collectAsState()
    val usbCh2Data by viewModel.usbCh2Data.collectAsState()
    val usbIsStreaming by viewModel.usbIsStreaming.collectAsState()
    val usbDeviceConnected by viewModel.usbDeviceConnected.collectAsState()
    val usbCh1Attenuation by viewModel.usbCh1Attenuation.collectAsState()
    val usbCh2Attenuation by viewModel.usbCh2Attenuation.collectAsState()
    val usbTriggerLevel by viewModel.usbTriggerLevel.collectAsState()
    val usbTriggerEdgeRising by viewModel.usbTriggerEdgeRising.collectAsState()
    val usbSamplingRate by viewModel.usbSamplingRate.collectAsState()

    val scrollState = rememberScrollState()

    if (showSafetyDialog) {
        AlertDialog(
            onDismissRequest = { showSafetyDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedNeonIcon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF3333),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "ADVERTENCIA DE SEGURIDAD ELÉCTRICA",
                        color = Color(0xFFFF3333),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            text = {
                Column {
                    Text(
                        "El osciloscopio físico Hantek 6022BE soporta un rango máximo de ±5V en modo directo (1x). Medir señales automotrices de alta tensión (primarios de bobina, inyectores, sensores inductivos CKP/CMP) sin atenuación quemará permanentemente el osciloscopio o el puerto USB de su teléfono móvil.",
                        color = Color.White,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { safetyChecked = !safetyChecked }
                    ) {
                        Checkbox(
                            checked = safetyChecked,
                            onCheckedChange = { safetyChecked = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MeetColors.neonGreen,
                                uncheckedColor = Color.Gray
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "He conectado un atenuador de hardware adecuado (10:1 o 20:1) en mis cables de prueba.",
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (safetyChecked) {
                            safetyAcknowledged = true
                            showSafetyDialog = false
                            viewModel.toggleUsbOscilloscopeStream()
                            usbDiagnosis = null
                        }
                    },
                    enabled = safetyChecked
                ) {
                    Text(
                        "ENTENDIDO Y DESBLOQUEAR",
                        color = if (safetyChecked) MeetColors.neonGreen else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSafetyDialog = false }) {
                    Text("CANCELAR", color = Color.White)
                }
            },
            containerColor = Color(0xFF140F0F),
            shape = RoundedCornerShape(12.dp)
        )
    }

    Scaffold(
        topBar = {
            EliteTopAppBar(
                title = "COCKPIT OSCILOSCOPIO",
                onBackClick = {
                    onNavigateBack()
                },
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
                .verticalScroll(scrollState)
                .padding(12.dp)
        ) {
            // ─── NAVIGATION TABS (Virtual OBD vs. Physical USB Scope) ───
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = Color(0xFF0F141C),
                contentColor = MeetColors.neonGreen,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                        color = MeetColors.neonGreen
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = {
                        Text(
                            "VIRTUAL ELM327",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = {
                        Text(
                            "FÍSICO USB HANTEK",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                )
            }

            Spacer(Modifier.height(14.dp))

            if (activeTab == 0) {
                // ═════════════════════════════════════════════════════════════
                // ─── VIRTUAL OBD OSCILLOSCOPE INTERFACE ───
                // ═════════════════════════════════════════════════════════════
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            selectedPid.name.uppercase(),
                            color = MeetColors.neonGreen,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            if (obdIsRunning) "⏺ CAPTURANDO VIRTUAL" else if (dataBuffer.isEmpty()) "LISTO" else "CAPTURA #$captureCount",
                            color = if (obdIsRunning) Color(0xFFFF4444) else MeetColors.textSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (dataBuffer.isNotEmpty()) {
                        Text(
                            "${String.format("%.1f", dataBuffer.last().second)} ${selectedPid.unit}",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { showPidPicker = !showPidPicker }) {
                        AnimatedNeonIcon(Icons.Default.Tune, null, tint = MeetColors.electricBlue)
                    }
                }

                // PID Picker
                AnimatedVisibility(showPidPicker) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        allPids.forEach { pid ->
                            val isSelected = pid.code == selectedPid.code
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MeetColors.neonGreen.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                                modifier = Modifier.clickable {
                                    if (!obdIsRunning) {
                                        selectedPid = pid
                                        viewModel.clearOscilloscopeCapture()
                                        obdDiagnosis = null
                                        showPidPicker = false
                                    }
                                }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        pid.name,
                                        color = if (isSelected) MeetColors.neonGreen else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(pid.unit, color = MeetColors.textSecondary, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // VIEWPORT
                EliteCard(
                    backgroundColor = Color(0xFF0A0E14),
                    glowColor = MeetColors.neonGreen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        OscilloscopeGrid()
                        OscilloscopeWaveform(dataPoints = dataBuffer.takeLast(300))
                        if (obdIsRunning) ScanningLineEffect()

                        // HUD Overlay
                        if (dataBuffer.size > 5) {
                            val m = dataBuffer.map { it.second }
                            Column(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                                HudLabel("MAX", "${String.format("%.1f", m.max())} ${selectedPid.unit}")
                                HudLabel("MIN", "${String.format("%.1f", m.min())} ${selectedPid.unit}")
                                HudLabel("Vpp", String.format("%.2f", m.max() - m.min()))
                                HudLabel("Pts", "${dataBuffer.size}")
                            }
                        }

                        // Trigger level indicator
                        if (dataBuffer.size > 2) {
                            val mean = dataBuffer.map { it.second }.average().toFloat()
                            val min = dataBuffer.minOf { it.second }
                            val max = dataBuffer.maxOf { it.second }
                            val range = maxOf(1f, (max - min) * 1.1f)
                            val normY = (mean - min * 0.95f) / range
                            Canvas(Modifier.fillMaxSize()) {
                                val y = size.height - (normY * size.height)
                                drawLine(
                                    Color(0xFFFF6600).copy(alpha = 0.5f),
                                    Offset(0f, y),
                                    Offset(size.width, y),
                                    strokeWidth = 1.5f
                                )
                            }
                            Text(
                                "T",
                                color = Color(0xFFFF6600),
                                fontSize = 8.sp,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 2.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // CONTROLS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    EliteButton(
                        text = if (obdIsRunning) "⏹ DETENER" else "▶ CAPTURAR OBD",
                        onClick = {
                            if (obdIsRunning) viewModel.stopOscilloscope()
                            else viewModel.startOscilloscope(selectedPid.code)
                        },
                        color = if (obdIsRunning) MeetColors.error else MeetColors.neonGreen,
                        modifier = Modifier.weight(1f)
                    )
                    EliteButton(
                        text = "🗑 LIMPIAR",
                        onClick = { viewModel.clearOscilloscopeCapture(); obdDiagnosis = null },
                        isEnabled = !obdIsRunning && dataBuffer.isNotEmpty(),
                        color = Color(0xFF666666),
                        modifier = Modifier.weight(0.6f)
                    )
                }

                if (!obdIsRunning && dataBuffer.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    val context = LocalContext.current
                    EliteButton(
                        text = "💾 COMPARTIR CSV",
                        onClick = {
                            shareCsvCapture(
                                context = context,
                                pidName = selectedPid.name,
                                ch1Data = dataBuffer.map { it.second }.toFloatArray(),
                                ch2Data = FloatArray(0),
                                isHantek = false
                            )
                        },
                        color = Color(0xFF666666),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(14.dp))

                // ANALYSIS RESULTS
                AnimatedVisibility(isAnalyzing || obdDiagnosis != null) {
                    Column {
                        if (isAnalyzing) {
                            EliteCard(
                                backgroundColor = MeetColors.backgroundDark,
                                glowColor = MeetColors.electricBlue,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    LinearProgressIndicator(color = MeetColors.neonGreen, modifier = Modifier.fillMaxWidth())
                                    Spacer(Modifier.height(8.dp))
                                    Text("Procesando señal...", color = MeetColors.textSecondary, fontSize = 12.sp)
                                }
                            }
                        }

                        obdDiagnosis?.let { d ->
                            val sevColor = when (d.severity) {
                                "critical" -> Color(0xFFFF2222)
                                "warning" -> Color(0xFFFFAA00)
                                else -> MeetColors.neonGreen
                            }

                            Spacer(Modifier.height(8.dp))

                            EliteCard(
                                backgroundColor = sevColor.copy(alpha = 0.1f),
                                glowColor = sevColor,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val icon = when (d.severity) {
                                        "critical" -> Icons.Default.Error
                                        "warning" -> Icons.Default.Warning
                                        else -> Icons.Default.CheckCircle
                                    }
                                    AnimatedNeonIcon(icon, null, tint = sevColor, modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            when (d.severity) {
                                                "critical" -> "ALERTA CRÍTICA"
                                                "warning" -> "ATENCIÓN REQUERIDA"
                                                else -> "SEÑAL NOMINAL"
                                            },
                                            color = sevColor,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp
                                        )
                                        Text("Confianza: ${d.confidence}%", color = MeetColors.textSecondary, fontSize = 10.sp)
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            EliteCard(
                                backgroundColor = MeetColors.backgroundDark,
                                glowColor = MeetColors.electricBlue,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        "MÉTRICAS",
                                        color = MeetColors.electricBlue,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(Modifier.fillMaxWidth()) {
                                        MetricCell("FREQ", "${String.format("%.1f", d.metrics.frequency)} Hz", Modifier.weight(1f))
                                        MetricCell("RMS", "${String.format("%.2f", d.metrics.rms)} ${selectedPid.unit}", Modifier.weight(1f))
                                        MetricCell("Vpp", String.format("%.2f", d.metrics.vpp), Modifier.weight(1f))
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Row(Modifier.fillMaxWidth()) {
                                        MetricCell("THD", "${String.format("%.1f", d.metrics.thd * 100)}%", Modifier.weight(1f))
                                        MetricCell("ESTAB", "${d.metrics.stability.toInt()}%", Modifier.weight(1f))
                                        MetricCell("DUTY", "${String.format("%.0f", d.metrics.dutyCycle)}%", Modifier.weight(1f))
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            EliteCard(
                                backgroundColor = MeetColors.backgroundDark,
                                glowColor = sevColor.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("DIAGNÓSTICO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        d.diagnosisText,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 17.sp
                                    )
                                    if (d.recommendationText.isNotBlank()) {
                                        Spacer(Modifier.height(10.dp))
                                        Divider(color = Color.White.copy(alpha = 0.1f))
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "🔧 RECOMENDACIÓN",
                                            color = MeetColors.neonGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            d.recommendationText,
                                            color = MeetColors.textSecondary,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ═════════════════════════════════════════════════════════════
                // ─── PHYSICAL HANTEK USB / AUTOMOTIVE SIMULATOR INTERFACE ───
                // ═════════════════════════════════════════════════════════════

                // Header status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "MODO HANTEK 6022BE USB",
                            color = MeetColors.neonGreen,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        if (usbIsStreaming) Color.Green else if (usbDeviceConnected) Color.Yellow else Color.Red
                                    )
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (usbIsStreaming) "STREAM ACTIVO" else if (usbDeviceConnected) "CONECTADO" else "DESCONECTADO",
                                color = MeetColors.textSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Attenuation Badge
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "CH1: ${usbCh1Attenuation.toInt()}x",
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .background(Color(0xFF00E5FF).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Text(
                            "CH2: ${usbCh2Attenuation.toInt()}x",
                            color = Color(0xFFFFB300),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .background(Color(0xFFFFB300).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Calculate the max absolute peak to perform dynamic center-symmetrical autoscaling
                val ch1Max = usbCh1Data.maxOrNull() ?: 1f
                val ch1Min = usbCh1Data.minOrNull() ?: -1f
                val ch2Max = usbCh2Data.maxOrNull() ?: 1f
                val ch2Min = usbCh2Data.minOrNull() ?: -1f

                val maxAbsVal = maxOf(
                    5f,
                    maxOf(ch1Max.absoluteValue, ch1Min.absoluteValue, ch2Max.absoluteValue, ch2Min.absoluteValue)
                )

                // Software Over-voltage warning calculation:
                // If physical voltage divided by attenuation > 4.5V, it's saturating the ±5V inputs.
                val hasCh1Overvoltage = remember(usbCh1Data, usbCh1Attenuation) {
                    var maxVal = 0f
                    for (v in usbCh1Data) {
                        val abs = v.absoluteValue
                        if (abs > maxVal) maxVal = abs
                    }
                    (maxVal / usbCh1Attenuation) > 4.5f
                }
                val hasCh2Overvoltage = remember(usbCh2Data, usbCh2Attenuation) {
                    var maxVal = 0f
                    for (v in usbCh2Data) {
                        val abs = v.absoluteValue
                        if (abs > maxVal) maxVal = abs
                    }
                    (maxVal / usbCh2Attenuation) > 4.5f
                }
                val showOvervoltageBanner = (hasCh1Overvoltage || hasCh2Overvoltage) && usbIsStreaming

                AnimatedVisibility(
                    visible = showOvervoltageBanner,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseAlpha"
                    )
                    Surface(
                        color = Color(0xFF4A0E0E).copy(alpha = alpha),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFFF2222)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            AnimatedNeonIcon(Icons.Default.FlashOn, null, tint = Color(0xFFFF2222))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "🚨 SOBRETENSIÓN DETECTADA. CONECTE ATENUADOR FÍSICO YA.",
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // ─── SCREEN VIEWPORT (CYAN & AMBER WAVEFORMS) ───
                EliteCard(
                    backgroundColor = Color(0xFF070A0F),
                    glowColor = if (usbIsStreaming) MeetColors.neonGreen else Color.DarkGray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        OscilloscopeGrid()

                        // Cyan for CH1, Amber for CH2
                        UsbOscilloscopeWaveforms(
                            ch1Data = usbCh1Data,
                            ch2Data = usbCh2Data,
                            maxVal = maxAbsVal
                        )

                        if (usbIsStreaming) ScanningLineEffect()

                        // Trigger Line indicators
                        val triggerV = usbTriggerLevel
                        if (triggerV.absoluteValue <= maxAbsVal) {
                            val triggerNormY = triggerV / maxAbsVal // ranges from -1.0 to +1.0
                            Canvas(Modifier.fillMaxSize()) {
                                val y = size.height / 2f - (triggerNormY * (size.height / 2f))
                                drawLine(
                                    Color(0xFFFF6600).copy(alpha = 0.4f),
                                    Offset(0f, y),
                                    Offset(size.width, y),
                                    strokeWidth = 1f
                                )
                            }
                            Text(
                                "T: ${String.format("%.1fV", triggerV)}",
                                color = Color(0xFFFF6600),
                                fontSize = 8.sp,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 2.dp)
                            )
                        }

                        // HUD Grid
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(6.dp)
                        ) {
                            Text(
                                "ESCALA: ±${String.format("%.1f", maxAbsVal)} V (CENTRO 0V)",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(Modifier.height(4.dp))
                            HudLabel("CH1 MAX", "${String.format("%.1f", ch1Max)}V", Color(0xFF00E5FF))
                            HudLabel("CH1 MIN", "${String.format("%.1f", ch1Min)}V", Color(0xFF00E5FF))
                            HudLabel("CH1 Vpp", "${String.format("%.2f", ch1Max - ch1Min)}V", Color(0xFF00E5FF))
                            Spacer(Modifier.height(4.dp))
                            HudLabel("CH2 MAX", "${String.format("%.1f", ch2Max)}V", Color(0xFFFFB300))
                            HudLabel("CH2 MIN", "${String.format("%.1f", ch2Min)}V", Color(0xFFFFB300))
                            HudLabel("CH2 Vpp", "${String.format("%.2f", ch2Max - ch2Min)}V", Color(0xFFFFB300))
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // HIGH VOLTAGE / ELECTRICAL SAFETY WARNING CARD
                EliteCard(
                    backgroundColor = Color(0xFF3A0000).copy(alpha = 0.2f),
                    glowColor = Color(0xFFFF2222),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimatedNeonIcon(Icons.Default.Warning, null, tint = Color(0xFFFF3333), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "ADVERTENCIA DE SEGURIDAD ELÉCTRICA",
                                color = Color(0xFFFF3333),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "El osciloscopio Hantek 6022BE soporta únicamente ±5V en modo 1x. Para medir señales automotrices de alta tensión (primarios de bobina >300V, picos inductivos de inyectores ~80V, CKP inductivo) DEBE conectar un atenuador de hardware (10x o 20:1) en su cable de prueba para evitar dañar permanentemente su teléfono móvil o el hardware del osciloscopio.",
                            color = MeetColors.textSecondary,
                            fontSize = 9.5.sp,
                            lineHeight = 13.sp
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // CAPTURE / PLAY BUTTONS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EliteButton(
                        text = if (usbIsStreaming) "⏹ DETENER CAPTURA" else "▶ EMPEZAR CAPTURA",
                        onClick = {
                            if (!safetyAcknowledged) {
                                showSafetyDialog = true
                            } else {
                                viewModel.toggleUsbOscilloscopeStream()
                                if (!usbIsStreaming) {
                                    usbDiagnosis = null
                                }
                            }
                        },
                        color = if (usbIsStreaming) MeetColors.error else MeetColors.neonGreen,
                        modifier = Modifier.weight(1.3f)
                    )

                }

                val hasUsbData = usbCh1Data.any { it != 0f } || usbCh2Data.any { it != 0f }
                if (!usbIsStreaming && hasUsbData) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val context = LocalContext.current
                        val coroutineScope = rememberCoroutineScope()
                        EliteButton(
                            text = "💾 COMPARTIR CSV",
                            onClick = {
                                shareCsvCapture(
                                    context = context,
                                    pidName = "Hantek 6022BE",
                                    ch1Data = usbCh1Data,
                                    ch2Data = usbCh2Data,
                                    isHantek = true
                                )
                            },
                            color = Color(0xFF666666),
                            modifier = Modifier.weight(1f)
                        )
                        EliteButton(
                            text = "🧠 CONSULTAR IA",
                            onClick = {
                                coroutineScope.launch {
                                    isAnalyzingUsb = true
                                    usbDiagnosis = null
                                    try {
                                        val timeStepMs = 1L
                                        val ch1List = usbCh1Data.mapIndexed { idx, v -> idx * timeStepMs to v }
                                        val ch2List = usbCh2Data.mapIndexed { idx, v -> idx * timeStepMs to v }
                                        val telemetryData = mapOf("CH1" to ch1List, "CH2" to ch2List)
                                        val vehicleInfo = viewModel.selectedVehicle.value?.let { "${it.make} ${it.model} ${it.year}" } ?: "Vehículo Genérico"
                                        usbDiagnosis = viewModel.analyzeOscilloscopeTelemetry(vehicleInfo, telemetryData)
                                    } catch (e: Exception) {
                                        android.util.Log.e("OscScope", "AI Analysis Error: ${e.message}")
                                    } finally {
                                        isAnalyzingUsb = false
                                    }
                                }
                            },
                            color = MeetColors.neonGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ─── PHYSICAL DSO CONTROL PANEL KNOBS ───
                EliteCard(
                    backgroundColor = MeetColors.backgroundDark,
                    glowColor = MeetColors.electricBlue,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "PARÁMETROS HARDWARE HANTEK",
                            color = MeetColors.electricBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.height(10.dp))

                        // Sampling Rate Timebase Selection
                        Text(
                            "TASA DE MUESTREO (TIEMPO / DIV)",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val rates = listOf(
                                100_000L to "100 KSa/s",
                                500_000L to "500 KSa/s",
                                1_000_000L to "1 MSa/s",
                                4_000_000L to "4 MSa/s",
                                16_000_000L to "16 MSa/s"
                            )
                            rates.forEach { (hz, label) ->
                                val isSelected = usbSamplingRate == hz
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) MeetColors.neonGreen.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                                    modifier = Modifier.clickable { viewModel.setUsbSamplingRate(hz) }
                                ) {
                                    Text(
                                        label,
                                        color = if (isSelected) MeetColors.neonGreen else Color.White,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Attenuation factor buttons CH1 vs CH2
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "ATENUACIÓN CANAL 1",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(1f to "1x", 10f to "10x", 20f to "20x").forEach { (f, label) ->
                                        val isSel = usbCh1Attenuation == f
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isSel) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                                            modifier = Modifier.weight(1f).clickable { viewModel.setUsbCh1Attenuation(f) }
                                        ) {
                                            Text(
                                                label,
                                                color = if (isSel) Color(0xFF00E5FF) else Color.White,
                                                fontSize = 9.5.sp,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 5.dp),
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "ATENUACIÓN CANAL 2",
                                    color = Color(0xFFFFB300),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(1f to "1x", 10f to "10x", 20f to "20x").forEach { (f, label) ->
                                        val isSel = usbCh2Attenuation == f
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isSel) Color(0xFFFFB300).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                                            modifier = Modifier.weight(1f).clickable { viewModel.setUsbCh2Attenuation(f) }
                                        ) {
                                            Text(
                                                label,
                                                color = if (isSel) Color(0xFFFFB300) else Color.White,
                                                fontSize = 9.5.sp,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 5.dp),
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Trigger Edge selection & Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1.2f)) {
                                Text(
                                    "UMBRAL DISPARO (TRIGGER): ${String.format("%.1fV", usbTriggerLevel)}",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Slider(
                                    value = usbTriggerLevel,
                                    onValueChange = { viewModel.changeUsbTriggerLevel(it) },
                                    valueRange = -50f..50f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFFFF6600),
                                        activeTrackColor = Color(0xFFFF6600).copy(alpha = 0.5f),
                                        inactiveTrackColor = Color.Gray.copy(alpha = 0.2f)
                                    )
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(0.8f)) {
                                Text(
                                    "FLANCO DISPARO",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val edges = listOf(true to "▲ SUB", false to "▼ BAJ")
                                    edges.forEach { (rising, label) ->
                                        val isSel = usbTriggerEdgeRising == rising
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isSel) Color(0xFFFF6600).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                                            modifier = Modifier.weight(1f).clickable { viewModel.setUsbTriggerEdge(rising) }
                                        ) {
                                            Text(
                                                label,
                                                color = if (isSel) Color(0xFFFF6600) else Color.White,
                                                fontSize = 9.5.sp,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 5.dp),
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // AI diagnosis for physical Hantek waveforms.
                if (isAnalyzingUsb) {
                    Spacer(Modifier.height(14.dp))
                    EliteCard(
                        backgroundColor = MeetColors.backgroundDark,
                        glowColor = MeetColors.electricBlue,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(color = MeetColors.neonGreen, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Text("Elysium AI analizando formas de onda...", color = MeetColors.textSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                usbDiagnosis?.let { d ->
                    Spacer(Modifier.height(14.dp))
                    val sevColor = when (d.confidence) {
                        in 0f..0.6f -> Color(0xFFFFAA00)
                        else -> MeetColors.neonGreen
                    }

                    EliteCard(
                        backgroundColor = MeetColors.backgroundDark,
                        glowColor = sevColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AnimatedNeonIcon(Icons.Default.CheckCircle, null, tint = sevColor, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "INFORME MAESTRO DE IA (DSO)",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text("Confianza del diagnóstico: ${String.format("%.0f", d.confidence * 100)}%", color = MeetColors.textSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Divider(color = Color.White.copy(alpha = 0.1f))
                            Spacer(Modifier.height(8.dp))
                            
                            Text(
                                d.analysisText,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun HudLabel(label: String, value: String, color: Color = MeetColors.neonGreen) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label:", color = MeetColors.textSecondary, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(3.dp))
        Text(value, color = color, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MetricCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = MeetColors.textSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun OscilloscopeGrid() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stepX = size.width / 10
        val stepY = size.height / 8
        for (i in 1..9) drawLine(
            Color.White.copy(alpha = 0.08f),
            Offset(i * stepX, 0f),
            Offset(i * stepX, size.height),
            1f
        )
        for (i in 1..7) drawLine(
            Color.White.copy(alpha = 0.08f),
            Offset(0f, i * stepY),
            Offset(size.width, i * stepY),
            1f
        )
        // Center cross
        drawLine(Color.White.copy(alpha = 0.15f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.5f)
        drawLine(Color.White.copy(alpha = 0.15f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.5f)
    }
}

@Composable
fun OscilloscopeWaveform(dataPoints: List<Pair<Long, Float>>) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 2.dp)
    ) {
        if (dataPoints.size < 2) return@Canvas
        val minVal = dataPoints.minOf { it.second } * 0.95f
        val maxVal = dataPoints.maxOf { it.second } * 1.05f
        val range = maxOf(0.01f, maxVal - minVal)
        val w = size.width
        val h = size.height
        val step = w / (dataPoints.size - 1)

        val path = Path()
        dataPoints.forEachIndexed { i, p ->
            val x = i * step
            val y = h - ((p.second - minVal) / range * h)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        // Glow
        drawPath(path, MeetColors.neonGreen.copy(alpha = 0.15f), style = Stroke(width = 10f))
        drawPath(path, MeetColors.neonGreen.copy(alpha = 0.3f), style = Stroke(width = 4f))
        drawPath(path, MeetColors.neonGreen, style = Stroke(width = 2f))
    }
}

@Composable
fun UsbOscilloscopeWaveforms(
    ch1Data: FloatArray,
    ch2Data: FloatArray,
    maxVal: Float
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 2.dp)
    ) {
        if (ch1Data.isEmpty() || ch2Data.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height

        // Symmetrical scale centered around 0V at h/2:
        // Voltage v maps to Y coordinate: y = h/2 - (v / maxVal) * (h/2)

        // Render CH1 (Neon Cyan)
        val path1 = Path()
        val step1 = w / (ch1Data.size - 1)
        ch1Data.forEachIndexed { i, v ->
            val x = i * step1
            val y = h / 2f - (v / maxVal) * (h / 2f)
            if (i == 0) path1.moveTo(x, y) else path1.lineTo(x, y)
        }
        drawPath(path1, Color(0xFF00E5FF).copy(alpha = 0.12f), style = Stroke(width = 9f))
        drawPath(path1, Color(0xFF00E5FF).copy(alpha = 0.3f), style = Stroke(width = 3.5f))
        drawPath(path1, Color(0xFF00E5FF), style = Stroke(width = 1.8f))

        // Render CH2 (Neon Amber)
        val path2 = Path()
        val step2 = w / (ch2Data.size - 1)
        ch2Data.forEachIndexed { i, v ->
            val x = i * step2
            val y = h / 2f - (v / maxVal) * (h / 2f)
            if (i == 0) path2.moveTo(x, y) else path2.lineTo(x, y)
        }
        drawPath(path2, Color(0xFFFFB300).copy(alpha = 0.12f), style = Stroke(width = 9f))
        drawPath(path2, Color(0xFFFFB300).copy(alpha = 0.3f), style = Stroke(width = 3.5f))
        drawPath(path2, Color(0xFFFFB300), style = Stroke(width = 1.8f))
    }
}

@Composable
fun ScanningLineEffect() {
    val transition = rememberInfiniteTransition(label = "scan")
    val x by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "scanX"
    )
    Canvas(Modifier.fillMaxSize()) {
        val px = x * size.width
        drawLine(MeetColors.neonGreen.copy(alpha = 0.35f), Offset(px, 0f), Offset(px, size.height), 2f)
    }
}

fun shareCsvCapture(
    context: android.content.Context,
    pidName: String,
    ch1Data: FloatArray,
    ch2Data: FloatArray,
    isHantek: Boolean
) {
    try {
        val fileName = "meet_osc_capture_${System.currentTimeMillis()}.csv"
        val reportsDir = context.getExternalFilesDir("Reports")
        if (reportsDir != null && !reportsDir.exists()) {
            reportsDir.mkdirs()
        }
        val cacheFile = java.io.File(reportsDir, fileName)
        cacheFile.bufferedWriter().use { writer ->
            if (isHantek) {
                writer.write("Timestamp_ms,CH1_Volts,CH2_Volts\n")
                val sampleRateHz = 1_000_000L
                val timeStepMs = 1000.0 / sampleRateHz
                for (i in ch1Data.indices) {
                    val t = i * timeStepMs
                    val v1 = ch1Data.getOrElse(i) { 0f }
                    val v2 = ch2Data.getOrElse(i) { 0f }
                    writer.write(String.format(java.util.Locale.US, "%.4f,%.3f,%.3f\n", t, v1, v2))
                }
            } else {
                writer.write("Sample_Index,Volts\n")
                for (i in ch1Data.indices) {
                    val v1 = ch1Data[i]
                    writer.write(String.format(java.util.Locale.US, "%d,%.3f\n", i, v1))
                }
            }
        }

        val authority = "${context.packageName}.fileprovider"
        val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, cacheFile)

        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Elysium Vanguard Osciloscopio - Captura $pidName")
            putExtra(android.content.Intent.EXTRA_TEXT, "Adjunto reporte de oscilograma para la señal: $pidName, capturado con la app Elysium Vanguard.")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(android.content.Intent.createChooser(intent, "Compartir oscilograma CSV"))
    } catch (e: Exception) {
        android.util.Log.e("OscScope", "Failed to share CSV", e)
    }
}
