package com.elysium369.meet.ui.screens.scanner

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.components.ConnectionStatusBar
import com.elysium369.meet.ui.components.EliteScrollContainer
import com.elysium369.meet.ui.components.eliteScrollbar
import com.elysium369.meet.ui.components.WaveGraphWidget
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.neonGlow
import com.elysium369.meet.ui.components.gauges.GaugeStyleManager
import com.elysium369.meet.ui.components.gauges.GaugeStyleSet
import com.elysium369.meet.ui.components.gauges.StyledGauge
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.min
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
@Suppress("UNUSED_PARAMETER")
fun ScannerDashboardTab(
    viewModel: ObdViewModel,
    isLandscape: Boolean,
    defaultGauges: List<GaugeConfig>
) {
    val pinnedPids by viewModel.pinnedPids.collectAsState()
    val anomalousPids by viewModel.anomalousPids.collectAsState()
    val healthScore by viewModel.healthScore.collectAsState()

    // Gauge style management
    val context = LocalContext.current
    val gaugeStyleManager = remember { GaugeStyleManager(context) }
    val currentStyle by gaugeStyleManager.currentStyle.collectAsState()

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

    // ── SINGLE-GAUGE FULLSCREEN STATE ──
    var selectedGauge by remember { mutableStateOf<GaugeConfig?>(null) }

    // ── MULTI-GAUGE FULLSCREEN STATE (long-press to select up to 3) ──
    val multiSelected = remember { mutableStateListOf<GaugeConfig>() }
    var showMultiFullscreen by remember { mutableStateOf(false) }

    val isFullscreen = selectedGauge != null || showMultiFullscreen

    // Staggered entry animation control
    var visibleCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        for (i in 0..sortedGauges.size + 2) {
            delay(40L)
            visibleCount = i
        }
    }

    // ── Grid zoom-out when fullscreen opens ──
    val gridScale by animateFloatAsState(
        targetValue = if (isFullscreen) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "gridScale"
    )
    val gridAlpha by animateFloatAsState(
        targetValue = if (isFullscreen) 0f else 1f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "gridAlpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // ── RESPONSIVE LAYOUT using BoxWithConstraints (animates out when fullscreen opens) ──
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = gridScale
                    scaleY = gridScale
                    alpha = gridAlpha
                }
        ) {
            // Dynamically calculate columns based on available width
            val cols = when {
                maxWidth < 360.dp  -> 1   // Very small phones
                maxWidth < 600.dp  -> 2   // Normal phones portrait
                maxWidth < 840.dp  -> 3   // Large phones landscape / small tablets
                else               -> 4   // Tablets / large landscape
            }

            // Responsive spacing
            val gridPadding = when {
                maxWidth < 360.dp  -> 8.dp
                maxWidth < 600.dp  -> 12.dp
                else               -> 16.dp
            }
            val itemSpacing = when {
                maxWidth < 360.dp  -> 6.dp
                maxWidth < 600.dp  -> 10.dp
                else               -> 14.dp
            }

            EliteScrollContainer(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(cols),
                    contentPadding = PaddingValues(gridPadding),
                    horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                    verticalArrangement = Arrangement.spacedBy(itemSpacing),
                    modifier = Modifier.fillMaxSize().eliteScrollbar(gridState)
                ) {
                    // ─── CONNECTION STATUS BAR (Full width) ───
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        AnimatedEntryItem(index = 0, visibleCount = visibleCount) {
                            ConnectionStatusBar(viewModel = viewModel, showQos = true)
                        }
                    }

                    // ─── VEHICLE HEALTH INDEX CARD (Full width) ───
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        AnimatedEntryItem(index = 1, visibleCount = visibleCount) {
                            HealthIndexCard(healthScore = healthScore, anomalousPids = anomalousPids)
                        }
                    }

                    // ─── LIVE AI REASONING TERMINAL (Full width) ───
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        AnimatedEntryItem(index = 2, visibleCount = visibleCount) {
                            LiveAITerminal(anomalousPids = anomalousPids)
                        }
                    }

                    // ─── GAUGE STYLE SWITCHER (Full width) ───
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        AnimatedEntryItem(index = 3, visibleCount = visibleCount) {
                            GaugeStyleSwitcher(
                                currentStyle = currentStyle,
                                onCycleNext = { gaugeStyleManager.cycleNext() },
                                onCyclePrevious = { gaugeStyleManager.cyclePrevious() }
                            )
                        }
                    }

                    // ─── GAUGE / WAVE WIDGETS ───
                    items(sortedGauges.size, key = { index -> sortedGauges[index].pid }) { index ->
                        val gauge = sortedGauges[index]
                        val isAnomaly = anomalousPids.any { it.pid == gauge.pid }
                        val isPinned = pinnedPids.contains(gauge.pid)
                        val isSelectedForFs = multiSelected.contains(gauge)

                        AnimatedEntryItem(index = index + 4, visibleCount = visibleCount) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                GaugeCard(
                                    viewModel = viewModel,
                                    gauge = gauge,
                                    gaugeStyle = currentStyle,
                                    isAnomaly = isAnomaly,
                                    isPinned = isPinned,
                                    isSelectedForFullscreen = isSelectedForFs,
                                    onTogglePin = {
                                        if (isPinned) viewModel.unpinPid(gauge.pid)
                                        else viewModel.pinPid(gauge.pid)
                                    },
                                    onTap = { selectedGauge = gauge },
                                    onLongPress = {
                                        if (isSelectedForFs) {
                                            multiSelected.remove(gauge)
                                        } else if (multiSelected.size < 3) {
                                            multiSelected.add(gauge)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── MULTI-FULLSCREEN FLOATING ACTION BUTTON ──
        if (multiSelected.isNotEmpty() && !isFullscreen) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .zIndex(50f)
            ) {
                val fabInf = rememberInfiniteTransition(label = "fabPulse")
                val fabGlow by fabInf.animateFloat(
                    0.4f, 1f,
                    infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "fabGlowPulse"
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF00E5FF),
                                    Color(0xFF00B0FF),
                                    Color(0xFF2979FF)
                                )
                            )
                        )
                        .border(
                            1.5.dp,
                            Color.White.copy(alpha = fabGlow * 0.4f),
                            RoundedCornerShape(28.dp)
                        )
                        .clickable { showMultiFullscreen = true }
                        .padding(horizontal = 28.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "▶  FULLSCREEN  ${multiSelected.size}/3",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                }
            }
        }

        // ── SINGLE FULLSCREEN GAUGE OVERLAY ──
        AnimatedContent(
            targetState = selectedGauge,
            transitionSpec = {
                if (targetState != null) {
                    (fadeIn(tween(350, easing = FastOutSlowInEasing)) +
                     scaleIn(initialScale = 0.82f, animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)))
                        .togetherWith(
                    fadeOut(tween(200, easing = FastOutSlowInEasing)))
                } else {
                    fadeIn(tween(200, easing = FastOutSlowInEasing))
                        .togetherWith(
                    fadeOut(tween(320, easing = FastOutSlowInEasing)) +
                     scaleOut(targetScale = 0.88f, animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)))
                }
            },
            label = "gaugeFullscreenTransition"
        ) { gauge ->
            if (gauge != null) {
                val isAnomaly = anomalousPids.any { it.pid == gauge.pid }
                FullScreenGaugeOverlay(
                    viewModel = viewModel,
                    gauge = gauge,
                    gaugeStyle = currentStyle,
                    isAnomaly = isAnomaly,
                    onDismiss = { selectedGauge = null }
                )
            }
        }

        // ── MULTI-GAUGE FULLSCREEN OVERLAY ──
        AnimatedContent(
            targetState = showMultiFullscreen,
            transitionSpec = {
                if (targetState) {
                    (fadeIn(tween(400, easing = FastOutSlowInEasing)) +
                     scaleIn(initialScale = 0.85f, animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)))
                        .togetherWith(fadeOut(tween(200)))
                } else {
                    fadeIn(tween(200)).togetherWith(
                    fadeOut(tween(350, easing = FastOutSlowInEasing)) +
                     scaleOut(targetScale = 0.88f, animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)))
                }
            },
            label = "multiGaugeFullscreen"
        ) { showMulti ->
            if (showMulti && multiSelected.isNotEmpty()) {
                MultiGaugeFullscreenOverlay(
                    viewModel = viewModel,
                    gauges = multiSelected.toList(),
                    gaugeStyle = currentStyle,
                    anomalousPids = anomalousPids,
                    onDismiss = { showMultiFullscreen = false },
                    onClearSelection = {
                        showMultiFullscreen = false
                        multiSelected.clear()
                    }
                )
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

// ═══════════════════════════════════════
// GAUGE STYLE SWITCHER BAR
// ═══════════════════════════════════════

@Composable
private fun GaugeStyleSwitcher(
    currentStyle: GaugeStyleSet,
    onCycleNext: () -> Unit,
    onCyclePrevious: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "styleSwitcherGlow")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "switcherBorder"
    )

    EliteCard(
        backgroundColor = Color(0xFF080E1A),
        borderColor = MeetColors.cyberCyan.copy(alpha = borderAlpha),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        glowColor = MeetColors.cyberCyan.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Previous button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MeetColors.cyberCyan.copy(alpha = 0.08f))
                    .border(0.5.dp, MeetColors.cyberCyan.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .clickable { onCyclePrevious() },
                contentAlignment = Alignment.Center
            ) {
                Text("◀", color = MeetColors.cyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // Style info (center)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            ) {
                Text(
                    "GAUGE STYLE",
                    color = MeetColors.textMuted,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        currentStyle.icon,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        currentStyle.displayName.uppercase(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }
                Text(
                    currentStyle.description,
                    color = MeetColors.textSecondary,
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }

            // Next button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MeetColors.cyberCyan.copy(alpha = 0.08f))
                    .border(0.5.dp, MeetColors.cyberCyan.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .clickable { onCycleNext() },
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = MeetColors.cyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ═══════════════════════════════════════
// GAUGE CARD — EXACT FULLSCREEN REPLICA
// Same radial gradient, glow, and rendering
// ═══════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GaugeCard(
    viewModel: ObdViewModel,
    gauge: GaugeConfig,
    gaugeStyle: GaugeStyleSet,
    isAnomaly: Boolean,
    isPinned: Boolean,
    isSelectedForFullscreen: Boolean = false,
    onTogglePin: () -> Unit,
    onTap: () -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    val currentValue by remember(viewModel, gauge.pid) {
        viewModel.liveData.map { it[gauge.pid] ?: 0f }.distinctUntilChanged()
    }.collectAsState(initial = 0f)

    val telemetryHistory by remember(viewModel, gauge.pid) {
        viewModel.telemetryHistory.map { it[gauge.pid] }.distinctUntilChanged()
    }.collectAsState(initial = null)

    // ── Per-style accent color (identical to fullscreen) ──
    val accentColor = when (gaugeStyle) {
        GaugeStyleSet.ELITE      -> Color(0xFF00E5FF)
        GaugeStyleSet.CLASSIC    -> Color(0xFF4CAF50)
        GaugeStyleSet.CYBER      -> Color(0xFF00FFFF)
        GaugeStyleSet.RACING     -> Color(0xFFFF1744)
        GaugeStyleSet.RADIAL     -> Color(0xFF448AFF)
        GaugeStyleSet.THERMO     -> Color(0xFFFF6D00)
        GaugeStyleSet.HOLOGRAM   -> Color(0xFF00E5FF)
        GaugeStyleSet.NEON_RETRO -> Color(0xFFFF00FF)
        GaugeStyleSet.LAMBO      -> Color(0xFFFF9100)
        GaugeStyleSet.PLASMA     -> Color(0xFF7C4DFF)
        GaugeStyleSet.AURORA     -> Color(0xFF1DE9B6)
        GaugeStyleSet.FERRARI    -> Color(0xFFD50000)
        GaugeStyleSet.TOKYO      -> Color(0xFFFF4081)
        GaugeStyleSet.MILITARY   -> Color(0xFF76FF03)
        GaugeStyleSet.DIAMOND    -> Color(0xFF80D8FF)
        GaugeStyleSet.COCKPIT    -> Color(0xFFFFAB00)
    }

    val effectiveAccent = if (isAnomaly) MeetColors.error else accentColor

    // ── Continuous breathing animations (identical to fullscreen) ──
    val inf = rememberInfiniteTransition(label = "cardBreath_${gauge.pid}")
    val glowPulse by inf.animateFloat(
        0.4f, 1f,
        infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow_${gauge.pid}"
    )
    val bgGlowScale by inf.animateFloat(
        0.92f, 1.08f,
        infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bgScale_${gauge.pid}"
    )

    // ── OUTER CONTAINER — exact same structure as fullscreen overlay ──
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f) // Square like fullscreen gauge area
            .padding(2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                // Same radial gradient as fullscreen background
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0A0E1A),
                        Color(0xFF050810),
                        Color.Black
                    ),
                    radius = 500f
                )
            )
            .border(
                width = 1.dp,
                color = when {
                    isAnomaly -> MeetColors.error.copy(alpha = glowPulse * 0.6f)
                    isPinned  -> MeetColors.neonGreen.copy(alpha = glowPulse * 0.5f)
                    else      -> effectiveAccent.copy(alpha = glowPulse * 0.2f)
                },
                shape = RoundedCornerShape(16.dp)
            )
            .combinedClickable(
                onClick = { onTap() },
                onLongClick = { onLongPress() }
            ),
        contentAlignment = Alignment.Center
    ) {
        // ── Breathing ambient glow behind gauge (same as fullscreen) ──
        Box(
            modifier = Modifier
                .size(160.dp)
                .scale(bgGlowScale)
                .alpha(glowPulse * 0.15f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            effectiveAccent.copy(alpha = 0.35f),
                            effectiveAccent.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        radius = 400f
                    ),
                    CircleShape
                )
        )

        // ── The gauge itself (same rendering as fullscreen) ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
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
                StyledGauge(
                    style = gaugeStyle,
                    label = gauge.label,
                    value = currentValue,
                    minVal = gauge.minVal,
                    maxVal = gauge.maxVal,
                    unit = gauge.unit,
                    warningThreshold = gauge.maxVal * 0.75f,
                    criticalThreshold = gauge.maxVal * 0.90f,
                    isAnomaly = isAnomaly,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // ── Pin toggle button ──
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFF1A1A2E).copy(alpha = 0.8f))
                .border(0.5.dp, effectiveAccent.copy(alpha = 0.3f), CircleShape)
                .clickable { onTogglePin() },
            contentAlignment = Alignment.Center
        ) {
            Text(if (isPinned) "📌" else "📍", fontSize = 10.sp)
        }

        // ── HI-FREQ pinned badge ──
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

        // ── Anomaly pulsing indicator ──
        if (isAnomaly) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(8.dp)
                    .alpha(glowPulse)
                    .background(MeetColors.error, CircleShape)
            )
        }

        // ── FULLSCREEN SELECTION BADGE ──
        if (isSelectedForFullscreen) {
            // Darkened overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                val selInf = rememberInfiniteTransition(label = "selBadge")
                val selPulse by selInf.animateFloat(
                    0.85f, 1f,
                    infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "selScale"
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "FULLSCREEN",
                        modifier = Modifier.scale(selPulse),
                        color = Color(0xFF00E5FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 3.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "✓ SELECCIONADO",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Bright accent border
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(2.dp, Color(0xFF00E5FF).copy(alpha = glowPulse * 0.8f), RoundedCornerShape(16.dp))
            )
        }
    }
}

// ═══════════════════════════════════════
// FULLSCREEN GAUGE OVERLAY
// Cinematic staggered transition system
// ═══════════════════════════════════════

@Composable
private fun FullScreenGaugeOverlay(
    viewModel: ObdViewModel,
    gauge: GaugeConfig,
    gaugeStyle: GaugeStyleSet,
    isAnomaly: Boolean,
    onDismiss: () -> Unit
) {
    // Live data stream
    val currentValue by remember(viewModel, gauge.pid) {
        viewModel.liveData.map { it[gauge.pid] ?: 0f }.distinctUntilChanged()
    }.collectAsState(initial = 0f)

    // ══════════════════════════════════════════
    // STAGGERED ENTRANCE ANIMATION SYSTEM
    // ══════════════════════════════════════════
    var stage by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        delay(30)   // frame tick
        stage = 1   // background fades in
        delay(150)
        stage = 2   // gauge explodes in
        delay(200)
        stage = 5   // close button spins in
    }

    // ── Background fade ──
    val bgAlpha by animateFloatAsState(
        targetValue = if (stage >= 1) 1f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "bgAlpha"
    )

    // ── Gauge scale + rotation entrance ──
    val gaugeScale by animateFloatAsState(
        targetValue = if (stage >= 2) 1f else 0.15f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
        label = "gScale"
    )
    val gaugeAlpha by animateFloatAsState(
        targetValue = if (stage >= 2) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "gAlpha"
    )

    // ── Close button spin entrance ──
    val closeBtnScale by animateFloatAsState(
        targetValue = if (stage >= 5) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "closeS"
    )
    val closeBtnRotation by animateFloatAsState(
        targetValue = if (stage >= 5) 0f else 180f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "closeR"
    )

    // ── Ambient pulse effects (continuous) ──
    val inf = rememberInfiniteTransition(label = "fsGlow")
    val glowPulse by inf.animateFloat(
        0.4f, 1f,
        infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "fsGlowP"
    )
    val closeBtnGlow by inf.animateFloat(
        0.3f, 0.8f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "closeGlow"
    )
    val bgGlowScale by inf.animateFloat(
        0.92f, 1.08f,
        infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bgGlowS"
    )

    // Per-style accent color (consistent with StyledGauge)
    val accentColor = when (gaugeStyle) {
        GaugeStyleSet.ELITE      -> Color(0xFF00E5FF)
        GaugeStyleSet.CLASSIC    -> Color(0xFF4CAF50)
        GaugeStyleSet.CYBER      -> Color(0xFF00FFFF)
        GaugeStyleSet.RACING     -> Color(0xFFFF1744)
        GaugeStyleSet.RADIAL     -> Color(0xFF448AFF)
        GaugeStyleSet.THERMO     -> Color(0xFFFF6D00)
        GaugeStyleSet.HOLOGRAM   -> Color(0xFF00E5FF)
        GaugeStyleSet.NEON_RETRO -> Color(0xFFFF00FF)
        GaugeStyleSet.LAMBO      -> Color(0xFFFF9100)
        GaugeStyleSet.PLASMA     -> Color(0xFF7C4DFF)
        GaugeStyleSet.AURORA     -> Color(0xFF1DE9B6)
        GaugeStyleSet.FERRARI    -> Color(0xFFD50000)
        GaugeStyleSet.TOKYO      -> Color(0xFFFF4081)
        GaugeStyleSet.MILITARY   -> Color(0xFF76FF03)
        GaugeStyleSet.DIAMOND    -> Color(0xFF80D8FF)
        GaugeStyleSet.COCKPIT    -> Color(0xFFFFAB00)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Back button handler
        BackHandler(enabled = true) { onDismiss() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(bgAlpha)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0A0E1A),
                            Color(0xFF050810),
                            Color.Black
                        ),
                        radius = 1200f
                    )
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { /* consume taps on background */ },
            contentAlignment = Alignment.Center
        ) {
            // ── Breathing ambient glow behind gauge ──
            Box(
                modifier = Modifier
                    .size(500.dp)
                    .scale(bgGlowScale)
                    .alpha(glowPulse * 0.18f * gaugeAlpha)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.35f),
                                accentColor.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            radius = 600f
                        ),
                        CircleShape
                    )
            )

            // ═══ THE GAUGE (occupies maximum screen space) ═══
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                val gaugeSize = minOf(maxWidth, maxHeight) - 8.dp

                Box(
                    modifier = Modifier
                        .size(width = gaugeSize, height = gaugeSize)
                        .scale(gaugeScale)
                        .alpha(gaugeAlpha),
                    contentAlignment = Alignment.Center
                ) {
                    if (gauge.type == GaugeType.WAVE) {
                        WaveGraphWidget(
                            label = gauge.label,
                            currentValue = currentValue,
                            minVal = gauge.minVal,
                            maxVal = gauge.maxVal,
                            unit = gauge.unit,
                            isAnomaly = isAnomaly,
                            historyData = null
                        )
                    } else {
                        StyledGauge(
                            style = gaugeStyle,
                            label = gauge.label,
                            value = currentValue,
                            minVal = gauge.minVal,
                            maxVal = gauge.maxVal,
                            unit = gauge.unit,
                            warningThreshold = gauge.maxVal * 0.75f,
                            criticalThreshold = gauge.maxVal * 0.90f,
                            isAnomaly = isAnomaly,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // ═══ CLOSE (X) BUTTON — floating at top right ═══
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 28.dp, end = 28.dp)
                    .scale(closeBtnScale)
                    .graphicsLayer { rotationZ = closeBtnRotation }
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1A1A2E),
                                Color(0xFF0D0D1A)
                            )
                        )
                    )
                    .border(
                        2.dp,
                        accentColor.copy(alpha = closeBtnGlow),
                        CircleShape
                    )
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "✕",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// MULTI-GAUGE FULLSCREEN OVERLAY (1, 2, or 3 gauges)
// Designed for car head unit displays
// ═══════════════════════════════════════════════════════

@Composable
private fun MultiGaugeFullscreenOverlay(
    viewModel: ObdViewModel,
    gauges: List<GaugeConfig>,
    gaugeStyle: GaugeStyleSet,
    anomalousPids: List<com.elysium369.meet.core.ai.HealthAnomaly>,
    onDismiss: () -> Unit,
    onClearSelection: () -> Unit
) {
    // Per-style accent color
    val accentColor = when (gaugeStyle) {
        GaugeStyleSet.ELITE      -> Color(0xFF00E5FF)
        GaugeStyleSet.CLASSIC    -> Color(0xFF4CAF50)
        GaugeStyleSet.CYBER      -> Color(0xFF00FFFF)
        GaugeStyleSet.RACING     -> Color(0xFFFF1744)
        GaugeStyleSet.RADIAL     -> Color(0xFF448AFF)
        GaugeStyleSet.THERMO     -> Color(0xFFFF6D00)
        GaugeStyleSet.HOLOGRAM   -> Color(0xFF00E5FF)
        GaugeStyleSet.NEON_RETRO -> Color(0xFFFF00FF)
        GaugeStyleSet.LAMBO      -> Color(0xFFFF9100)
        GaugeStyleSet.PLASMA     -> Color(0xFF7C4DFF)
        GaugeStyleSet.AURORA     -> Color(0xFF1DE9B6)
        GaugeStyleSet.FERRARI    -> Color(0xFFD50000)
        GaugeStyleSet.TOKYO      -> Color(0xFFFF4081)
        GaugeStyleSet.MILITARY   -> Color(0xFF76FF03)
        GaugeStyleSet.DIAMOND    -> Color(0xFF80D8FF)
        GaugeStyleSet.COCKPIT    -> Color(0xFFFFAB00)
    }

    // Animations
    val inf = rememberInfiniteTransition(label = "multiFS")
    val glowPulse by inf.animateFloat(
        0.4f, 1f,
        infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "multiGlow"
    )

    // Stage-based entry
    var stage by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        delay(80); stage = 1   // Background
        delay(120); stage = 2  // Gauges
        delay(200); stage = 3  // Controls
    }
    val bgAlpha by animateFloatAsState(if (stage >= 1) 1f else 0f, tween(400), label = "mBg")
    val gaugesAlpha by animateFloatAsState(if (stage >= 2) 1f else 0f, tween(500), label = "mGauges")
    val gaugesScale by animateFloatAsState(
        if (stage >= 2) 1f else 0.85f,
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "mGScale"
    )
    val controlsAlpha by animateFloatAsState(if (stage >= 3) 1f else 0f, tween(400), label = "mCtrl")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Block back
        BackHandler { onDismiss() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(bgAlpha)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF0A0E1A), Color(0xFF050810), Color.Black),
                        radius = 1200f
                    )
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { /* consume background taps */ }
        ) {
            // ── TOP BAR with title and close ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .alpha(controlsAlpha)
                    .zIndex(10f)
            ) {
                Column(modifier = Modifier.align(Alignment.CenterStart)) {
                    Text(
                        "MODO MULTI-GAUGE",
                        color = MeetColors.textMuted,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${gauges.size} INSTRUMENTO${if (gauges.size > 1) "S" else ""}",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }

                // Close + Clear buttons
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Clear selection button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1A1A2E))
                            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable { onClearSelection() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "LIMPIAR",
                            color = accentColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                    // Close button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0xFF1A1A2E), Color(0xFF0D0D1A))
                                )
                            )
                            .border(1.5.dp, accentColor.copy(alpha = glowPulse * 0.4f), CircleShape)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── GAUGES AREA — Adaptive Layout ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 88.dp, bottom = 12.dp, start = 12.dp, end = 12.dp)
                    .scale(gaugesScale)
                    .alpha(gaugesAlpha),
                contentAlignment = Alignment.Center
            ) {
                when (gauges.size) {
                    1 -> {
                        // Single gauge — full screen
                        MultiGaugeCell(
                            viewModel = viewModel,
                            gauge = gauges[0],
                            gaugeStyle = gaugeStyle,
                            isAnomaly = anomalousPids.any { it.pid == gauges[0].pid },
                            accentColor = accentColor,
                            glowPulse = glowPulse,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    2 -> {
                        // Two gauges — side by side
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            gauges.forEach { gauge ->
                                MultiGaugeCell(
                                    viewModel = viewModel,
                                    gauge = gauge,
                                    gaugeStyle = gaugeStyle,
                                    isAnomaly = anomalousPids.any { it.pid == gauge.pid },
                                    accentColor = accentColor,
                                    glowPulse = glowPulse,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                        }
                    }
                    3 -> {
                        // Three gauges — 2 top + 1 bottom centered
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (i in 0..1) {
                                    MultiGaugeCell(
                                        viewModel = viewModel,
                                        gauge = gauges[i],
                                        gaugeStyle = gaugeStyle,
                                        isAnomaly = anomalousPids.any { it.pid == gauges[i].pid },
                                        accentColor = accentColor,
                                        glowPulse = glowPulse,
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                MultiGaugeCell(
                                    viewModel = viewModel,
                                    gauge = gauges[2],
                                    gaugeStyle = gaugeStyle,
                                    isAnomaly = anomalousPids.any { it.pid == gauges[2].pid },
                                    accentColor = accentColor,
                                    glowPulse = glowPulse,
                                    modifier = Modifier.fillMaxHeight().aspectRatio(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// MULTI-GAUGE CELL — Single gauge in multi-fullscreen
// ═══════════════════════════════════════════════════════

@Composable
private fun MultiGaugeCell(
    viewModel: ObdViewModel,
    gauge: GaugeConfig,
    gaugeStyle: GaugeStyleSet,
    isAnomaly: Boolean,
    accentColor: Color,
    glowPulse: Float,
    modifier: Modifier = Modifier
) {
    val currentValue by remember(viewModel, gauge.pid) {
        viewModel.liveData.map { it[gauge.pid] ?: 0f }.distinctUntilChanged()
    }.collectAsState(initial = 0f)

    val inf = rememberInfiniteTransition(label = "cell_${gauge.pid}")
    val bgGlowScale by inf.animateFloat(
        0.92f, 1.08f,
        infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "cellGlow_${gauge.pid}"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0A0E1A), Color(0xFF050810), Color.Black),
                    radius = 600f
                )
            )
            .border(1.dp, accentColor.copy(alpha = glowPulse * 0.25f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Ambient glow
        Box(
            modifier = Modifier
                .size(200.dp)
                .scale(bgGlowScale)
                .alpha(glowPulse * 0.12f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.30f),
                            accentColor.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        radius = 500f
                    ),
                    CircleShape
                )
        )

        // Gauge content
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            val gaugeSize = minOf(maxWidth, maxHeight) - 2.dp

            Box(
                modifier = Modifier.size(width = gaugeSize, height = gaugeSize),
                contentAlignment = Alignment.Center
            ) {
                if (gauge.type == GaugeType.WAVE) {
                    WaveGraphWidget(
                        label = gauge.label,
                        currentValue = currentValue,
                        minVal = gauge.minVal,
                        maxVal = gauge.maxVal,
                        unit = gauge.unit,
                        isAnomaly = isAnomaly,
                        historyData = null
                    )
                } else {
                    StyledGauge(
                        style = gaugeStyle,
                        label = gauge.label,
                        value = currentValue,
                        minVal = gauge.minVal,
                        maxVal = gauge.maxVal,
                        unit = gauge.unit,
                        warningThreshold = gauge.maxVal * 0.75f,
                        criticalThreshold = gauge.maxVal * 0.90f,
                        isAnomaly = isAnomaly,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Label overlay at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                gauge.label.uppercase(),
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }
    }
}

