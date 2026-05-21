package com.elysium369.meet.ui.screens.scanner

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.components.ConnectionStatusBar
import com.elysium369.meet.ui.components.EliteScrollContainer
import com.elysium369.meet.ui.components.eliteScrollbar
import com.elysium369.meet.ui.components.WaveGraphWidget
import com.elysium369.meet.ui.components.GaugeWidget
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.neonGlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ScannerDashboardTab(
    viewModel: ObdViewModel,
    isLandscape: Boolean,
    defaultGauges: List<GaugeConfig>
) {
    val liveData by viewModel.liveData.collectAsState()
    val pinnedPids by viewModel.pinnedPids.collectAsState()
    val telemetryHistory by viewModel.telemetryHistory.collectAsState()
    val anomalousPids by viewModel.anomalousPids.collectAsState()
    val healthScore by viewModel.healthScore.collectAsState()

    val sortedGauges = remember(defaultGauges, anomalousPids, pinnedPids) {
        defaultGauges.sortedWith(
            compareByDescending<GaugeConfig> { gauge ->
                pinnedPids.contains(gauge.pid)
            }.thenByDescending { gauge ->
                anomalousPids.any { it.pid == gauge.pid }
            }
        )
    }

    val gridState = rememberLazyGridState()
    val cols = if (isLandscape) 3 else 2

    // Staggered entry animation control
    var visibleCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        for (i in 0..sortedGauges.size + 2) {
            delay(40L)
            visibleCount = i
        }
    }

    EliteScrollContainer(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(cols),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().eliteScrollbar(gridState)
        ) {
            // ─── CONNECTION STATUS BAR (Full width) ───
            item(span = { GridItemSpan(cols) }) {
                AnimatedEntryItem(index = 0, visibleCount = visibleCount) {
                    ConnectionStatusBar(viewModel = viewModel, showQos = true)
                }
            }

            // ─── VEHICLE HEALTH INDEX CARD (Full width) ───
            item(span = { GridItemSpan(cols) }) {
                AnimatedEntryItem(index = 1, visibleCount = visibleCount) {
                    HealthIndexCard(healthScore = healthScore, anomalousPids = anomalousPids)
                }
            }

            // ─── LIVE AI REASONING TERMINAL (Full width) ───
            item(span = { GridItemSpan(cols) }) {
                AnimatedEntryItem(index = 2, visibleCount = visibleCount) {
                    LiveAITerminal(anomalousPids = anomalousPids)
                }
            }

            // ─── GAUGE / WAVE WIDGETS ───
            items(sortedGauges.size, key = { index -> sortedGauges[index].pid }) { index ->
                val gauge = sortedGauges[index]
                val isAnomaly = anomalousPids.any { it.pid == gauge.pid }
                val isPinned = pinnedPids.contains(gauge.pid)

                AnimatedEntryItem(index = index + 3, visibleCount = visibleCount) {
                    GaugeCard(
                        viewModel = viewModel,
                        gauge = gauge,
                        isAnomaly = isAnomaly,
                        isPinned = isPinned,
                        onTogglePin = {
                            if (isPinned) viewModel.unpinPid(gauge.pid)
                            else viewModel.pinPid(gauge.pid)
                        }
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// ANIMATED ENTRY WRAPPER
// ═══════════════════════════════════════

@Composable
private fun AnimatedEntryItem(
    index: Int,
    visibleCount: Int,
    content: @Composable () -> Unit
) {
    val isVisible = index <= visibleCount
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "entryAlpha$index"
    )
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.85f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow),
        label = "entryScale$index"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 40f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "entryOffset$index"
    )

    Box(
        modifier = Modifier
            .alpha(alpha)
            .scale(scale)
            .offset(y = offsetY.dp)
    ) {
        content()
    }
}

// ═══════════════════════════════════════
// NEON CRT SCANLINES MODIFIER
// ═══════════════════════════════════════

fun Modifier.crtScanlines(color: Color = Color.Black.copy(alpha = 0.12f)): Modifier = this.drawBehind {
    val step = 4.dp.toPx()
    var y = 0f
    val w = this.size.width
    val h = this.size.height
    while (y < h) {
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = 1.dp.toPx()
        )
        y += step
    }
}

// ═══════════════════════════════════════
// HEALTH INDEX CARD (Elysium V2 Militarized)
// ═══════════════════════════════════════

@Composable
private fun HealthIndexCard(
    healthScore: Int,
    anomalousPids: List<com.elysium369.meet.core.ai.HealthAnomaly>
) {
    val scoreColor = if (healthScore > 80) MeetColors.neonGreen else if (healthScore > 50) MeetColors.warning else MeetColors.error

    val infiniteTransition = rememberInfiniteTransition(label = "healthPulse")
    val borderGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "borderGlow"
    )

    EliteCard(
        backgroundColor = MeetColors.backgroundDark,
        borderColor = scoreColor.copy(alpha = borderGlow),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .crtScanlines(Color.Black.copy(alpha = 0.1f)),
        glowColor = scoreColor.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "ANALIZADOR TÁCTICO DE SALUD VEHICULAR", 
                        color = MeetColors.textMuted, 
                        style = MaterialTheme.typography.labelSmall, 
                        fontWeight = FontWeight.Black, 
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = when {
                            healthScore > 90 -> "CORE OPERATIVO ÓPTIMO"
                            healthScore > 75 -> "SISTEMA ESTABLE"
                            healthScore > 50 -> "ALERTA DE ANOMALÍA PREVENTIVA"
                            else -> "FALLA CRÍTICA EN NÚCLEO"
                        },
                        color = Color.White, 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }

                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(76.dp)) {
                    CircularProgressIndicator(
                        progress = healthScore / 100f,
                        modifier = Modifier.fillMaxSize(),
                        color = scoreColor,
                        strokeWidth = 6.dp,
                        trackColor = scoreColor.copy(alpha = 0.1f)
                    )
                    Text(
                        "$healthScore%", 
                        color = scoreColor, 
                        fontWeight = FontWeight.Black, 
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (anomalousPids.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                EliteCard(
                    backgroundColor = MeetColors.error.copy(alpha = 0.12f),
                    borderColor = MeetColors.error.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = MeetColors.error.copy(alpha = 0.1f)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️", fontSize = 16.sp, modifier = Modifier.alpha(borderGlow))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "DETECCIÓN DE IA: ${anomalousPids.size} PID(S) FUERA DE PARÁMETROS NOMINALES",
                            color = MeetColors.error, 
                            style = MaterialTheme.typography.labelSmall, 
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// LIVE AI REASONING TERMINAL (Elysium V2 military look)
// ═══════════════════════════════════════

@Composable
private fun LiveAITerminal(anomalousPids: List<com.elysium369.meet.core.ai.HealthAnomaly>) {
    val terminalLines = remember(anomalousPids) {
        if (anomalousPids.isEmpty()) {
            listOf(
                "> CORTEX AI: MONITOREO ACTIVO...",
                "> ESCANEANDO FLUJO DE DATOS OBD EN TIEMPO REAL...",
                "> TODOS LOS SENSORES OPERANDO DENTRO DEL RANGO DESIGNADO."
            )
        } else {
            val lines = mutableListOf("> CORTEX AI: ANOMALÍA DETECTADA...")
            lines.add("> ⚠️ ALERTA DE SISTEMA: ${anomalousPids.size} DESVIACIONES:")
            anomalousPids.forEach { anomaly ->
                lines.add("> [PID ${anomaly.pid}] DESVIACIÓN CRÍTICA DETECTADA.")
                lines.add("> ANÁLISIS: POSIBLE DESGASTE FÍSICO O CORROSIÓN.")
            }
            lines.add("> ACCIÓN RECOMENDADA: INSPECCIÓN SENSORIAL FÍSICA INMEDIATA.")
            lines
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "terminalGlow")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "borderGlow"
    )

    EliteCard(
        backgroundColor = Color(0xFF07111E).copy(alpha = 0.95f),
        borderColor = if (anomalousPids.isEmpty()) MeetColors.neonGreen.copy(alpha = borderAlpha) else MeetColors.error.copy(alpha = borderAlpha),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .crtScanlines(Color.Black.copy(alpha = 0.15f)),
        glowColor = if (anomalousPids.isEmpty()) MeetColors.neonGreen.copy(alpha = 0.1f) else MeetColors.error.copy(alpha = 0.15f)
    ) {
        Column(modifier = Modifier.padding(14.dp).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "CORTEX AI TELEMETRY MONITOR", 
                    color = MeetColors.textMuted, 
                    fontSize = 9.sp, 
                    fontWeight = FontWeight.Black, 
                    letterSpacing = 1.sp
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (anomalousPids.isEmpty()) MeetColors.neonGreen else MeetColors.error, RoundedCornerShape(50))
                        .alpha(borderAlpha * 2f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            terminalLines.forEach { line ->
                Text(
                    text = line,
                    color = if (line.contains("⚠️") || line.contains("ALERTA") || line.contains("DESVIACIÓN")) MeetColors.error else MeetColors.neonGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 3.dp),
                    letterSpacing = 0.2.sp
                )
            }
        }
    }
}

// ═══════════════════════════════════════
// GAUGE CARD WITH PHANTOM CARBON EFFECTS
// ═══════════════════════════════════════

@Composable
private fun GaugeCard(
    viewModel: ObdViewModel,
    gauge: GaugeConfig,
    isAnomaly: Boolean,
    isPinned: Boolean,
    onTogglePin: () -> Unit
) {
    val currentValue by remember(viewModel, gauge.pid) {
        viewModel.liveData.map { it[gauge.pid] ?: 0f }.distinctUntilChanged()
    }.collectAsState(initial = 0f)

    val telemetryHistory by remember(viewModel, gauge.pid) {
        viewModel.telemetryHistory.map { it[gauge.pid] }.distinctUntilChanged()
    }.collectAsState(initial = null)

    // Glassmorphism background with color cues
    val backgroundColor = if (isAnomaly) {
        Color(0x28FF0000) // Translucent glowing red
    } else {
        MeetColors.backgroundDark
    }

    var borderAlpha = 0.15f
    if (isAnomaly || isPinned) {
        val infiniteTransition = rememberInfiniteTransition(label = "cardPulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.25f, targetValue = 0.85f,
            animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "borderAlpha"
        )
        borderAlpha = alpha
    }

    val borderColor = when {
        isAnomaly -> MeetColors.error.copy(alpha = borderAlpha)
        isPinned -> MeetColors.neonGreen.copy(alpha = borderAlpha)
        else -> MeetColors.neonGreen.copy(alpha = borderAlpha)
    }

    val cardGlow = when {
        isAnomaly -> MeetColors.error.copy(alpha = 0.15f)
        isPinned -> MeetColors.neonGreen.copy(alpha = 0.1f)
        else -> null
    }

    EliteCard(
        backgroundColor = backgroundColor,
        borderColor = borderColor,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(2.dp)
            .crtScanlines(Color.Black.copy(alpha = 0.08f)),
        glowColor = cardGlow
    ) {
        if (gauge.type == GaugeType.WAVE) {
            WaveGraphWidget(
                label = gauge.label,
                currentValue = currentValue,
                minVal = gauge.minVal,
                maxVal = gauge.maxVal,
                unit = gauge.unit,
                isAnomaly = isAnomaly,
                historyData = telemetryHistory
            )
        } else {
            GaugeWidget(
                label = gauge.label,
                value = currentValue,
                minVal = gauge.minVal,
                maxVal = gauge.maxVal,
                unit = gauge.unit,
                warningThreshold = gauge.maxVal * 0.75f,
                criticalThreshold = gauge.maxVal * 0.90f,
                isAnomaly = isAnomaly
            )
        }

        // Pinned pin overlay button
        com.elysium369.meet.ui.components.EliteIconButton(
            onClick = onTogglePin,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp),
            icon = {
                Text(if (isPinned) "📌" else "📍", fontSize = 10.sp)
            }
        )

        // Hi-Freq Pinned badge
        if (isPinned) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(MeetColors.neonGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .border(0.5.dp, MeetColors.neonGreen.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    "HI-FREQ", 
                    color = MeetColors.neonGreen, 
                    fontSize = 7.sp, 
                    fontWeight = FontWeight.Black, 
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
