package com.elysium369.meet.ui.screens.scanner

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import android.widget.Toast
import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.shadow
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
import com.elysium369.meet.ui.components.gauges.GaugeColorScheme
import com.elysium369.meet.ui.components.gauges.GaugeCustomizerDialog
import com.elysium369.meet.ui.components.gauges.StyledGauge
import com.elysium369.meet.ui.components.gauges.FullscreenAnimatedBg
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.min
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController

@Composable
@Suppress("UNUSED_PARAMETER")
fun ScannerDashboardTab(
    viewModel: ObdViewModel,
    isLandscape: Boolean,
    defaultGauges: List<GaugeConfig>,
    navController: NavController? = null
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
    var showDashboardCustomizer by remember { mutableStateOf(false) }

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
        // ── RESPONSIVE LAYOUT (animates out when fullscreen opens) ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = gridScale
                    scaleY = gridScale
                    alpha = gridAlpha
                }
        ) {
            if (isLandscape) {
                // ─── WIDESCREEN AUTO/CARPLAY DASHBOARD VIEW ───
                var activePrimaryGauge by remember(sortedGauges) {
                    mutableStateOf(sortedGauges.firstOrNull() ?: GaugeConfig("1", "RPM", "010C", 0f, 8000f, "rpm"))
                }
                val primaryValue by remember(viewModel, activePrimaryGauge.pid) {
                    viewModel.liveData.map { it[activePrimaryGauge.pid] ?: 0f }.distinctUntilChanged()
                }.collectAsState(initial = 0f)

                Row(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF070B14)).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // LEFT COLUMN: Large Gauge Panel & Customizer Row
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.42f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0C101F))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Title HUD Card
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "MONITOR PRINCIPAL",
                                color = MeetColors.textSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MeetColors.neonGreen.copy(alpha = 0.15f))
                                    .clickable { showDashboardCustomizer = true }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("🎨 AJUSTAR", color = MeetColors.neonGreen, fontSize = 9.sp, fontWeight = FontWeight.Black)
                            }
                        }

                        // Large focal Gauge
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            StyledGauge(
                                style = currentStyle,
                                label = activePrimaryGauge.label,
                                value = primaryValue,
                                minVal = activePrimaryGauge.minVal,
                                maxVal = activePrimaryGauge.maxVal,
                                unit = activePrimaryGauge.unit,
                                isAnomaly = anomalousPids.any { it.pid == activePrimaryGauge.pid },
                                modifier = Modifier.fillMaxSize(0.95f)
                            )
                        }

                        // Horizontal Style Carousel
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GaugeStyleSet.entries.forEach { styleItem ->
                                val isSelected = styleItem == currentStyle
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) MeetColors.neonGreen.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                                        .border(1.dp, if (isSelected) MeetColors.neonGreen else Color.Transparent, RoundedCornerShape(8.dp))
                                        .clickable { gaugeStyleManager.selectStyle(styleItem) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "${styleItem.icon} ${styleItem.displayName}",
                                        color = if (isSelected) Color.White else MeetColors.textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // RIGHT COLUMN: Widescreen Telemetry Grid
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.58f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Title HUD Card
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TELEMETRÍA DE VEHÍCULO (TOCA PARA ENFOCAR / MANTÉN PARA TPMS 3D)",
                                color = MeetColors.textSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }

                        // Scrollable grid of telemetry cards
                        val gridStateLandscape = rememberLazyGridState()
                        LazyVerticalGrid(
                            state = gridStateLandscape,
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize().eliteScrollbar(gridStateLandscape)
                        ) {
                            items(sortedGauges.size, key = { index -> "land_${sortedGauges[index].pid}" }) { index ->
                                val gauge = sortedGauges[index]
                                val isAnomaly = anomalousPids.any { it.pid == gauge.pid }
                                val isPinned = pinnedPids.contains(gauge.pid)
                                val isSelectedForFs = multiSelected.contains(gauge)

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
                                    onTap = {
                                        activePrimaryGauge = gauge
                                    },
                                    onLongPress = {
                                        selectedGauge = gauge
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                // ─── PORTRAIT VIEW (Standard Vertical Scroll Grid) ───
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val cols = when {
                        maxWidth < 360.dp  -> 1
                        maxWidth < 600.dp  -> 2
                        maxWidth < 840.dp  -> 3
                        else               -> 4
                    }
                    val gridPadding = if (maxWidth < 360.dp) 8.dp else if (maxWidth < 600.dp) 12.dp else 16.dp
                    val itemSpacing = if (maxWidth < 360.dp) 6.dp else if (maxWidth < 600.dp) 10.dp else 14.dp

                    EliteScrollContainer(modifier = Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(cols),
                            contentPadding = PaddingValues(gridPadding),
                            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                            verticalArrangement = Arrangement.spacedBy(itemSpacing),
                            modifier = Modifier.fillMaxSize().eliteScrollbar(gridState)
                        ) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                AnimatedEntryItem(index = 0, visibleCount = visibleCount) {
                                    HealthIndexCard(healthScore = healthScore, anomalousPids = anomalousPids)
                                }
                            }
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                AnimatedEntryItem(index = 1, visibleCount = visibleCount) {
                                    LiveAITerminal(anomalousPids = anomalousPids)
                                }
                            }
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                AnimatedEntryItem(index = 2, visibleCount = visibleCount) {
                                    GaugeStyleSwitcher(
                                        currentStyle = currentStyle,
                                        onCycleNext = { gaugeStyleManager.cycleNext() },
                                        onCyclePrevious = { gaugeStyleManager.cyclePrevious() },
                                        onCustomize = { showDashboardCustomizer = true },
                                        onDiyCreate = {
                                            gaugeStyleManager.selectStyle(GaugeStyleSet.CUSTOM_DIY)
                                            showDashboardCustomizer = true
                                        }
                                    )
                                }
                            }
                            items(sortedGauges.size, key = { index -> sortedGauges[index].pid }) { index ->
                                val gauge = sortedGauges[index]
                                val isAnomaly = anomalousPids.any { it.pid == gauge.pid }
                                val isPinned = pinnedPids.contains(gauge.pid)
                                val isSelectedForFs = multiSelected.contains(gauge)

                                AnimatedEntryItem(index = index + 3, visibleCount = visibleCount) {
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
                    onDismiss = { selectedGauge = null },
                    navController = navController
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

        // ── DASHBOARD COLOR CUSTOMIZER DIALOG ──
        if (showDashboardCustomizer) {
            val trigger = GaugeStyleManager.colorSchemeUpdateTrigger
            val dashColorScheme = remember(currentStyle, trigger) {
                gaugeStyleManager.getColorScheme(currentStyle)
            }
            GaugeCustomizerDialog(
                currentStyle = currentStyle,
                currentScheme = dashColorScheme,
                onSchemeChange = { gaugeStyleManager.saveColorScheme(currentStyle, it) },
                onReset = { gaugeStyleManager.resetColorScheme(currentStyle) },
                onDismiss = { showDashboardCustomizer = false },
                navController = navController
            )
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
    onCyclePrevious: () -> Unit,
    onCustomize: () -> Unit = {},
    onDiyCreate: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "styleSwitcherGlow")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "switcherBorder"
    )
    // DIY button pulsing glow
    val diyGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "diyGlow"
    )
    val isDiyActive = currentStyle == GaugeStyleSet.CUSTOM_DIY

    Column(modifier = Modifier.fillMaxWidth()) {
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

                // Palette/Customize button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MeetColors.cyberCyan.copy(alpha = 0.12f))
                        .border(0.5.dp, MeetColors.cyberCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .clickable { onCustomize() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎨", fontSize = 14.sp)
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

        Spacer(Modifier.height(6.dp))

        // ── PROMINENT DIY BUTTON ──
        val diyBorderColor = if (isDiyActive) Color(0xFF00FFCC) else Color(0xFFFF6F00).copy(alpha = diyGlow)
        val diyBgColor = if (isDiyActive) Color(0xFF00FFCC).copy(alpha = 0.15f) else Color(0xFFFF6F00).copy(alpha = 0.08f + diyGlow * 0.07f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            diyBgColor,
                            if (isDiyActive) Color(0xFF00FFCC).copy(alpha = 0.08f) else Color(0xFFFF8F00).copy(alpha = 0.05f),
                            diyBgColor
                        )
                    )
                )
                .border(1.dp, diyBorderColor, RoundedCornerShape(10.dp))
                .clickable { onDiyCreate() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("🛠️", fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        if (isDiyActive) "✦ MODO DIY ACTIVO — TOCA PARA EDITAR" else "CREAR TU RELOJ PERSONALIZADO",
                        color = if (isDiyActive) Color(0xFF00FFCC) else Color(0xFFFFAB40),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        "Diseña agujas, bordes, marcas y fondo a tu gusto",
                        color = if (isDiyActive) Color(0xFF00FFCC).copy(alpha = 0.6f) else Color(0xFFFFAB40).copy(alpha = 0.5f),
                        fontSize = 7.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
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
    val context = LocalContext.current
    val gaugeStyleManager = remember { GaugeStyleManager(context) }
    val trigger = GaugeStyleManager.colorSchemeUpdateTrigger
    val colorScheme = remember(gaugeStyle, trigger) {
        gaugeStyleManager.getColorScheme(gaugeStyle)
    }

    val currentValue by remember(viewModel, gauge.pid) {
        viewModel.liveData.map { it[gauge.pid] ?: 0f }.distinctUntilChanged()
    }.collectAsState(initial = 0f)

    val telemetryHistory by remember(viewModel, gauge.pid) {
        viewModel.telemetryHistory.map { it[gauge.pid] }.distinctUntilChanged()
    }.collectAsState(initial = null)

    // ── Per-style accent color (identical to fullscreen) ──
    val accentColor = colorScheme.specialColor

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
                    historyData = telemetryHistory,
                    style = gaugeStyle,
                    colorScheme = colorScheme
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
    onDismiss: () -> Unit,
    navController: NavController? = null
) {
    // ── COLOR CUSTOMIZATION STATE ──
    val context = LocalContext.current
    val fsStyleManager = remember { GaugeStyleManager(context) }
    val fsTrigger = GaugeStyleManager.colorSchemeUpdateTrigger
    val fsColorScheme = remember(gaugeStyle, fsTrigger) {
        fsStyleManager.getColorScheme(gaugeStyle)
    }
    var showCustomizer by remember { mutableStateOf(false) }
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

    // Per-style accent color — now derived from dynamic color scheme
    val accentColor = fsColorScheme.specialColor

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
            // ── Dynamic Fullscreen Animated Background (Matrix, Lightning, Fire, etc.) ──
            FullscreenAnimatedBg(style = gaugeStyle, modifier = Modifier.fillMaxSize())

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
                    if (gauge.pid == "0133") {
                        CarTpmsChassisView(
                            viewModel = viewModel,
                            colorScheme = fsColorScheme
                        )
                    } else if (gauge.type == GaugeType.WAVE) {
                        WaveGraphWidget(
                            label = gauge.label,
                            currentValue = currentValue,
                            minVal = gauge.minVal,
                            maxVal = gauge.maxVal,
                            unit = gauge.unit,
                            isAnomaly = isAnomaly,
                            historyData = null,
                            style = gaugeStyle,
                            colorScheme = fsColorScheme
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

            // ═══ TOP RIGHT CONTROLS — Palette + Close ═══
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 28.dp, end = 28.dp)
                    .scale(closeBtnScale)
                    .graphicsLayer { rotationZ = closeBtnRotation },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 🎨 Palette Button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF1A1A2E), Color(0xFF0D0D1A))
                            )
                        )
                        .border(1.5.dp, accentColor.copy(alpha = closeBtnGlow * 0.6f), CircleShape)
                        .clickable { showCustomizer = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎨", fontSize = 20.sp)
                }

                // ✕ Close Button
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF1A1A2E), Color(0xFF0D0D1A))
                            )
                        )
                        .border(2.dp, accentColor.copy(alpha = closeBtnGlow), CircleShape)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }

            // ── CUSTOMIZER DIALOG ──
            if (showCustomizer) {
                GaugeCustomizerDialog(
                    currentStyle = gaugeStyle,
                    currentScheme = fsColorScheme,
                    onSchemeChange = { fsStyleManager.saveColorScheme(gaugeStyle, it) },
                    onReset = { fsStyleManager.resetColorScheme(gaugeStyle) },
                    onDismiss = { showCustomizer = false },
                    navController = navController
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
    // Per-style accent color — from dynamic color scheme
    val multiContext = LocalContext.current
    val multiStyleManager = remember { GaugeStyleManager(multiContext) }
    val multiTrigger = GaugeStyleManager.colorSchemeUpdateTrigger
    val multiColorScheme = remember(gaugeStyle, multiTrigger) {
        multiStyleManager.getColorScheme(gaugeStyle)
    }
    val accentColor = multiColorScheme.specialColor

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
            // ── Dynamic Fullscreen Animated Background (Matrix, Lightning, Fire, etc.) ──
            FullscreenAnimatedBg(style = gaugeStyle, modifier = Modifier.fillMaxSize())

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
    val context = LocalContext.current
    val gaugeStyleManager = remember { GaugeStyleManager(context) }
    val trigger = GaugeStyleManager.colorSchemeUpdateTrigger
    val colorScheme = remember(gaugeStyle, trigger) {
        gaugeStyleManager.getColorScheme(gaugeStyle)
    }

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
                        historyData = null,
                        style = gaugeStyle,
                        colorScheme = colorScheme
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

// ═══════════════════════════════════════════════════════
// CAR TPMS SKELETON CHASSIS TELEMETRY VIEW
// ═══════════════════════════════════════════════════════

@Composable
fun CarTpmsChassisView(
    viewModel: ObdViewModel,
    colorScheme: GaugeColorScheme
) {
    val liveData by viewModel.liveData.collectAsState()
    val hasLiveTpms = false

    // Real Barometric Pressure (PID 0133) in kPa
    val rawBaro = liveData["0133"]
    val ecuBaro = rawBaro ?: 0.0f
    val isBaroSupported = rawBaro != null && rawBaro > 0f

    // Generic OBD does not expose individual tyre pressures. Keep the chassis view honest.
    val pFL = 0.0f
    val pFR = 0.0f
    val pRL = 0.0f
    val pRR = 0.0f

    // Animated dash phase for flowing holographic data packet lines
    val dashPhase by rememberInfiniteTransition(label = "dashPhase").animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "dashPhase"
    )
    val dashEffect = remember(dashPhase) {
        androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 15f), dashPhase)
    }

    // Leader pulse animation (animate as dp Float value, converted inside DrawScope)
    val pulseRadiusDp by rememberInfiniteTransition(label = "pulseRadius").animateFloat(
        initialValue = 3f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseRadius"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SISTEMA DE MONITOREO TPMS PRO",
            color = colorScheme.specialColor,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = if (isBaroSupported) "TELEMETRÍA AUXILIAR REAL DESDE LA ECU" else "OBD GENÉRICO NO REPORTA PRESIONES TPMS INDIVIDUALES",
            color = if (isBaroSupported) MeetColors.textSecondary else MeetColors.warning.copy(alpha = 0.8f),
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        // 🚀 ECU BAROMETRIC PRESSURE HUD PANEL
        val baroBar = ecuBaro / 100f
        val baroPsi = ecuBaro * 0.145038f
        
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xBB080F25), Color(0xBB020510))
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = if (isBaroSupported) {
                            listOf(colorScheme.specialColor, colorScheme.specialColor.copy(alpha = 0.2f))
                        } else {
                            listOf(Color(0x335A6E85), Color(0x115A6E85))
                        }
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(12.dp)
        ) {
            // Futuristic scanner corners
            Box(modifier = Modifier.align(Alignment.TopStart).size(8.dp).border(1.dp, colorScheme.specialColor.copy(alpha = 0.4f), RoundedCornerShape(topStart = 3.dp)))
            Box(modifier = Modifier.align(Alignment.BottomEnd).size(8.dp).border(1.dp, colorScheme.specialColor.copy(alpha = 0.4f), RoundedCornerShape(bottomEnd = 3.dp)))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "PRESIÓN BAROMÉTRICA DE LA ECU",
                    color = if (isBaroSupported) MeetColors.textSecondary else Color(0xFF5A6E85),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isBaroSupported) "${String.format("%.1f", ecuBaro)} kPa" else "0.0 kPa",
                            color = if (isBaroSupported) Color.White else Color(0xFF5A6E85),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "PRESIÓN ABSOLUTA",
                            color = if (isBaroSupported) colorScheme.specialColor.copy(alpha = 0.7f) else Color(0x445A6E85),
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(modifier = Modifier.size(1.dp, 24.dp).background(Color(0x225A6E85)))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isBaroSupported) "${String.format("%.2f", baroBar)} BAR" else "0.00 BAR",
                            color = if (isBaroSupported) Color.White.copy(alpha = 0.8f) else Color(0xFF5A6E85),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Box(modifier = Modifier.size(3.dp).background(Color(0x335A6E85), CircleShape))
                        Text(
                            text = if (isBaroSupported) "${String.format("%.1f", baroPsi)} PSI" else "0.0 PSI",
                            color = if (isBaroSupported) Color.White.copy(alpha = 0.8f) else Color(0xFF5A6E85),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Tilted 3D Holographic Chassis Container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationX = 35f
                        rotationZ = -15f
                        cameraDistance = 12f * density
                    },
                contentAlignment = Alignment.Center
            ) {
                // Draw 3D wireframe car chassis skeleton and holographic grid
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2
                    val cy = size.height / 2
                    
                    // 1. Holographic Floor Radial base glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colorScheme.specialColor.copy(alpha = 0.12f),
                                colorScheme.specialColor.copy(alpha = 0.03f),
                                Color.Transparent
                            )
                        ),
                        radius = 240.dp.toPx(),
                        center = Offset(cx, cy)
                    )

                    // 3D perspective floor grid
                    val vpX = cx
                    val vpY = cy - 280.dp.toPx()
                    val gridColor = colorScheme.specialColor.copy(alpha = 0.08f)
                    
                    // Radial lines
                    val numRadial = 12
                    for (i in -numRadial..numRadial) {
                        val angleOffset = i * 14.dp.toPx()
                        drawLine(
                            color = gridColor,
                            start = Offset(vpX + angleOffset * 0.1f, vpY),
                            end = Offset(vpX + angleOffset * 3f, cy + 250.dp.toPx()),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    
                    // Horizontal perspective grid lines
                    var gridY = cy - 180.dp.toPx()
                    var spacing = 12.dp.toPx()
                    while (gridY < cy + 250.dp.toPx()) {
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, gridY),
                            end = Offset(size.width, gridY),
                            strokeWidth = 1.dp.toPx()
                        )
                        spacing *= 1.2f
                        gridY += spacing
                    }

                    // 2. Holographic drop shadows projected on the floor grid (Offset downward)
                    val shadowOffset = Offset(10.dp.toPx(), 20.dp.toPx())
                    val shadowColor = Color.Black.copy(alpha = 0.5f)

                    // Axle shadows
                    drawLine(shadowColor, Offset(cx - 80.dp.toPx(), cy - 100.dp.toPx()) + shadowOffset, Offset(cx + 80.dp.toPx(), cy - 100.dp.toPx()) + shadowOffset, strokeWidth = 5.dp.toPx())
                    drawLine(shadowColor, Offset(cx - 80.dp.toPx(), cy + 100.dp.toPx()) + shadowOffset, Offset(cx + 80.dp.toPx(), cy + 100.dp.toPx()) + shadowOffset, strokeWidth = 5.dp.toPx())
                    // Spine shadow
                    drawLine(shadowColor, Offset(cx, cy - 130.dp.toPx()) + shadowOffset, Offset(cx, cy + 130.dp.toPx()) + shadowOffset, strokeWidth = 8.dp.toPx())

                    // Chassis cage shadow
                    val shadowPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(cx - 45.dp.toPx() + shadowOffset.x, cy - 140.dp.toPx() + shadowOffset.y)
                        quadraticBezierTo(cx + shadowOffset.x, cy - 150.dp.toPx() + shadowOffset.y, cx + 45.dp.toPx() + shadowOffset.x, cy - 140.dp.toPx() + shadowOffset.y)
                        lineTo(cx + 60.dp.toPx() + shadowOffset.x, cy - 90.dp.toPx() + shadowOffset.y)
                        lineTo(cx + 65.dp.toPx() + shadowOffset.x, cy + 90.dp.toPx() + shadowOffset.y)
                        lineTo(cx + 50.dp.toPx() + shadowOffset.x, cy + 140.dp.toPx() + shadowOffset.y)
                        quadraticBezierTo(cx + shadowOffset.x, cy + 145.dp.toPx() + shadowOffset.y, cx - 50.dp.toPx() + shadowOffset.x, cy + 140.dp.toPx() + shadowOffset.y)
                        lineTo(cx - 65.dp.toPx() + shadowOffset.x, cy + 90.dp.toPx() + shadowOffset.y)
                        lineTo(cx - 60.dp.toPx() + shadowOffset.x, cy - 90.dp.toPx() + shadowOffset.y)
                        close()
                    }
                    drawPath(shadowPath, shadowColor)

                    // 3. Real 3D Chassis wireframe lines (With animated marching ants dash effect)
                    val chassisColorDark = colorScheme.specialColor.copy(alpha = 0.2f)
                    val chassisColorLight = colorScheme.specialColor.copy(alpha = 0.65f)
                    
                    // Axles
                    // Front axle
                    drawLine(chassisColorLight, Offset(cx - 80.dp.toPx(), cy - 100.dp.toPx()), Offset(cx + 80.dp.toPx(), cy - 100.dp.toPx()), strokeWidth = 3.dp.toPx())
                    // Rear axle
                    drawLine(chassisColorLight, Offset(cx - 80.dp.toPx(), cy + 100.dp.toPx()), Offset(cx + 80.dp.toPx(), cy + 100.dp.toPx()), strokeWidth = 3.dp.toPx())
                    // Central spine
                    drawLine(chassisColorDark, Offset(cx, cy - 130.dp.toPx()), Offset(cx, cy + 130.dp.toPx()), strokeWidth = 5.dp.toPx())

                    // 3D wireframe cage lower
                    val lowerY = 15.dp.toPx()
                    val lowerPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(cx - 45.dp.toPx(), cy - 140.dp.toPx() + lowerY)
                        quadraticBezierTo(cx, cy - 150.dp.toPx() + lowerY, cx + 45.dp.toPx(), cy - 140.dp.toPx() + lowerY)
                        lineTo(cx + 60.dp.toPx(), cy - 90.dp.toPx() + lowerY)
                        lineTo(cx + 65.dp.toPx(), cy + 90.dp.toPx() + lowerY)
                        lineTo(cx + 50.dp.toPx(), cy + 140.dp.toPx() + lowerY)
                        quadraticBezierTo(cx, cy + 145.dp.toPx() + lowerY, cx - 50.dp.toPx(), cy + 140.dp.toPx() + lowerY)
                        lineTo(cx - 65.dp.toPx(), cy + 90.dp.toPx() + lowerY)
                        lineTo(cx - 60.dp.toPx(), cy - 90.dp.toPx() + lowerY)
                        close()
                    }
                    drawPath(lowerPath, chassisColorDark, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))

                    // 3D wireframe cage upper (Animating data flow)
                    val upperY = -15.dp.toPx()
                    val upperPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(cx - 35.dp.toPx(), cy - 120.dp.toPx() + upperY)
                        quadraticBezierTo(cx, cy - 130.dp.toPx() + upperY, cx + 35.dp.toPx(), cy - 120.dp.toPx() + upperY)
                        lineTo(cx + 45.dp.toPx(), cy - 80.dp.toPx() + upperY)
                        lineTo(cx + 48.dp.toPx(), cy + 80.dp.toPx() + upperY)
                        lineTo(cx + 38.dp.toPx(), cy + 120.dp.toPx() + upperY)
                        quadraticBezierTo(cx, cy + 125.dp.toPx() + upperY, cx - 38.dp.toPx(), cy + 120.dp.toPx() + upperY)
                        lineTo(cx - 48.dp.toPx(), cy + 80.dp.toPx() + upperY)
                        lineTo(cx - 45.dp.toPx(), cy - 80.dp.toPx() + upperY)
                        close()
                    }
                    drawPath(
                        path = upperPath,
                        color = chassisColorLight,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = dashEffect
                        )
                    )

                    // Pillars
                    drawLine(chassisColorLight.copy(alpha = 0.3f), Offset(cx - 45.dp.toPx(), cy - 140.dp.toPx() + lowerY), Offset(cx - 35.dp.toPx(), cy - 120.dp.toPx() + upperY), strokeWidth = 1.5f.dp.toPx())
                    drawLine(chassisColorLight.copy(alpha = 0.3f), Offset(cx + 45.dp.toPx(), cy - 140.dp.toPx() + lowerY), Offset(cx + 35.dp.toPx(), cy - 120.dp.toPx() + upperY), strokeWidth = 1.5f.dp.toPx())
                    drawLine(chassisColorLight.copy(alpha = 0.3f), Offset(cx - 60.dp.toPx(), cy - 90.dp.toPx() + lowerY), Offset(cx - 45.dp.toPx(), cy - 80.dp.toPx() + upperY), strokeWidth = 1.5f.dp.toPx())
                    drawLine(chassisColorLight.copy(alpha = 0.3f), Offset(cx + 60.dp.toPx(), cy - 90.dp.toPx() + lowerY), Offset(cx + 45.dp.toPx(), cy - 80.dp.toPx() + upperY), strokeWidth = 1.5f.dp.toPx())
                    drawLine(chassisColorLight.copy(alpha = 0.3f), Offset(cx - 50.dp.toPx(), cy + 140.dp.toPx() + lowerY), Offset(cx - 38.dp.toPx(), cy + 120.dp.toPx() + upperY), strokeWidth = 1.5f.dp.toPx())
                    drawLine(chassisColorLight.copy(alpha = 0.3f), Offset(cx + 50.dp.toPx(), cy + 140.dp.toPx() + lowerY), Offset(cx + 38.dp.toPx(), cy + 120.dp.toPx() + upperY), strokeWidth = 1.5f.dp.toPx())
                }

                // 3D Tyres (Rotated inside the tilted container)
                val tireWidth = 32.dp
                val tireHeight = 56.dp

                // FL Tyre
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = (-80).dp, y = (-100).dp)
                        .size(tireWidth, tireHeight)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    getTireColor(pFL, hasLiveTpms).copy(alpha = 0.45f),
                                    Color.Black,
                                    getTireColor(pFL, hasLiveTpms).copy(alpha = 0.25f)
                                )
                            )
                        )
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                listOf(getTireColor(pFL, hasLiveTpms), getTireColor(pFL, hasLiveTpms).copy(alpha = 0.3f))
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp, 24.dp)
                            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                    )
                }

                // FR Tyre
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = 80.dp, y = (-100).dp)
                        .size(tireWidth, tireHeight)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    getTireColor(pFR, hasLiveTpms).copy(alpha = 0.45f),
                                    Color.Black,
                                    getTireColor(pFR, hasLiveTpms).copy(alpha = 0.25f)
                                )
                            )
                        )
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                listOf(getTireColor(pFR, hasLiveTpms), getTireColor(pFR, hasLiveTpms).copy(alpha = 0.3f))
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp, 24.dp)
                            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                    )
                }

                // RL Tyre
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = (-80).dp, y = 100.dp)
                        .size(tireWidth, tireHeight)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    getTireColor(pRL, hasLiveTpms).copy(alpha = 0.45f),
                                    Color.Black,
                                    getTireColor(pRL, hasLiveTpms).copy(alpha = 0.25f)
                                )
                            )
                        )
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                listOf(getTireColor(pRL, hasLiveTpms), getTireColor(pRL, hasLiveTpms).copy(alpha = 0.3f))
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp, 24.dp)
                            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                    )
                }

                // RR Tyre
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = 80.dp, y = 100.dp)
                        .size(tireWidth, tireHeight)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    getTireColor(pRR, hasLiveTpms).copy(alpha = 0.45f),
                                    Color.Black,
                                    getTireColor(pRR, hasLiveTpms).copy(alpha = 0.25f)
                                )
                            )
                        )
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                listOf(getTireColor(pRR, hasLiveTpms), getTireColor(pRR, hasLiveTpms).copy(alpha = 0.3f))
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp, 24.dp)
                            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                    )
                }
            }

            // 2D HUD Callout Leader Lines (Overlay)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2
                val cy = size.height / 2
                val lineCol = if (hasLiveTpms) colorScheme.specialColor.copy(alpha = 0.35f) else Color(0x335A6E85)
                val dotCol = if (hasLiveTpms) colorScheme.specialColor else Color(0xFF5A6E85)
                val strokeW = 1.5f.dp.toPx()
                val pulseRadius = pulseRadiusDp.dp.toPx()

                // FL Leader
                val flStart = Offset(cx - 180.dp.toPx() + 67.5.dp.toPx(), cy - 100.dp.toPx())
                val flMid = Offset(cx - 130.dp.toPx(), cy - 100.dp.toPx())
                val flEnd = Offset(cx - 96.dp.toPx(), cy - 90.dp.toPx())
                drawLine(color = lineCol, start = flStart, end = flMid, strokeWidth = strokeW)
                drawLine(color = lineCol, start = flMid, end = flEnd, strokeWidth = strokeW)
                drawCircle(color = dotCol.copy(alpha = 0.2f), radius = pulseRadius, center = flEnd)
                drawCircle(color = dotCol, radius = 3.dp.toPx(), center = flEnd)

                // FR Leader
                val frStart = Offset(cx + 180.dp.toPx() - 67.5.dp.toPx(), cy - 100.dp.toPx())
                val frMid = Offset(cx + 130.dp.toPx(), cy - 100.dp.toPx())
                val frEnd = Offset(cx + 96.dp.toPx(), cy - 90.dp.toPx())
                drawLine(color = lineCol, start = frStart, end = frMid, strokeWidth = strokeW)
                drawLine(color = lineCol, start = frMid, end = frEnd, strokeWidth = strokeW)
                drawCircle(color = dotCol.copy(alpha = 0.2f), radius = pulseRadius, center = frEnd)
                drawCircle(color = dotCol, radius = 3.dp.toPx(), center = frEnd)

                // RL Leader
                val rlStart = Offset(cx - 180.dp.toPx() + 67.5.dp.toPx(), cy + 100.dp.toPx())
                val rlMid = Offset(cx - 130.dp.toPx(), cy + 100.dp.toPx())
                val rlEnd = Offset(cx - 96.dp.toPx(), cy + 110.dp.toPx())
                drawLine(color = lineCol, start = rlStart, end = rlMid, strokeWidth = strokeW)
                drawLine(color = lineCol, start = rlMid, end = rlEnd, strokeWidth = strokeW)
                drawCircle(color = dotCol.copy(alpha = 0.2f), radius = pulseRadius, center = rlEnd)
                drawCircle(color = dotCol, radius = 3.dp.toPx(), center = rlEnd)

                // RR Leader
                val rrStart = Offset(cx + 180.dp.toPx() - 67.5.dp.toPx(), cy + 100.dp.toPx())
                val rrMid = Offset(cx + 130.dp.toPx(), cy + 100.dp.toPx())
                val rrEnd = Offset(cx + 96.dp.toPx(), cy + 110.dp.toPx())
                drawLine(color = lineCol, start = rrStart, end = rrMid, strokeWidth = strokeW)
                drawLine(color = lineCol, start = rrMid, end = rrEnd, strokeWidth = strokeW)
                drawCircle(color = dotCol.copy(alpha = 0.2f), radius = pulseRadius, center = rrEnd)
                drawCircle(color = dotCol, radius = 3.dp.toPx(), center = rrEnd)
            }

            // 2D flat HUD cards (Flat glassmorphic overlay, extremely clear)
            // FRONT LEFT CARD
            TirePressureBox(
                pressure = pFL,
                label = "DELANTERO IZQ (FL)",
                hasLiveTpms = hasLiveTpms,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = (-180).dp, y = (-100).dp)
            )

            // FRONT RIGHT CARD
            TirePressureBox(
                pressure = pFR,
                label = "DELANTERO DER (FR)",
                hasLiveTpms = hasLiveTpms,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = 180.dp, y = (-100).dp)
            )

            // REAR LEFT CARD
            TirePressureBox(
                pressure = pRL,
                label = "TRASERO IZQ (RL)",
                hasLiveTpms = hasLiveTpms,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = (-180).dp, y = 100.dp)
            )

            // REAR RIGHT CARD
            TirePressureBox(
                pressure = pRR,
                label = "TRASERO DER (RR)",
                hasLiveTpms = hasLiveTpms,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = 180.dp, y = 100.dp)
            )

            // Center Holographic Core Card (Temperature)
            val animatedGlow = rememberInfiniteTransition(label = "coreGlow").animateFloat(
                initialValue = 0.2f,
                targetValue = 0.7f,
                animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "glow"
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(135.dp, 84.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xDD101730),
                                Color(0xDD060B18)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = if (hasLiveTpms) {
                                listOf(colorScheme.specialColor.copy(alpha = animatedGlow.value), colorScheme.specialColor.copy(alpha = 0.1f))
                            } else {
                                listOf(Color(0x335A6E85), Color(0x115A6E85))
                            }
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(14.dp),
                        ambientColor = if (hasLiveTpms) colorScheme.specialColor.copy(alpha = 0.2f) else Color.Transparent,
                        spotColor = if (hasLiveTpms) colorScheme.specialColor.copy(alpha = 0.2f) else Color.Transparent
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "TEMP. LLANTAS",
                        color = if (hasLiveTpms) MeetColors.textSecondary else Color(0x885A6E85),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (hasLiveTpms) "34°C" else "--°C",
                        color = if (hasLiveTpms) Color.White else Color(0xFF5A6E85),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (hasLiveTpms) "SISTEMA: NOMINAL" else "SIN MEDICIÓN",
                        color = if (hasLiveTpms) MeetColors.success else Color(0xFF5A6E85),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bottom Color Legend Card
        EliteCard(
            modifier = Modifier.fillMaxWidth(0.9f),
            borderColor = MeetColors.borderSubtle
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(MeetColors.success, CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("30 - 36 PSI (Óptimo)", color = Color.White, fontSize = 9.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(MeetColors.warning, CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Precaución", color = Color.White, fontSize = 9.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(MeetColors.error, CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Crítico / Exceso", color = Color.White, fontSize = 9.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF5A6E85), CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("N/A (Inactivo)", color = Color.White, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
fun TirePressureBox(
    pressure: Float,
    label: String,
    hasLiveTpms: Boolean,
    modifier: Modifier = Modifier
) {
    val isMeasured = hasLiveTpms && pressure > 0f
    val barVal = pressure * 0.0689476f
    
    val shadowColor = if (isMeasured) getTireColor(pressure, hasLiveTpms) else Color.Transparent

    Column(
        modifier = modifier
            .width(135.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xDD0C101F))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        getTireColor(pressure, hasLiveTpms).copy(alpha = 0.5f),
                        getTireColor(pressure, hasLiveTpms).copy(alpha = 0.15f)
                    )
                ),
                shape = RoundedCornerShape(10.dp)
            )
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(10.dp),
                ambientColor = shadowColor.copy(alpha = 0.15f),
                spotColor = shadowColor.copy(alpha = 0.15f)
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = if (isMeasured) MeetColors.textSecondary else Color(0xFF5A6E85),
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (isMeasured) "${String.format("%.1f", pressure)} PSI" else "0.0 PSI",
            color = getTireColor(pressure, hasLiveTpms),
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = if (isMeasured) "${String.format("%.2f", barVal)} BAR" else "0.00 BAR",
            color = if (isMeasured) Color.White.copy(alpha = 0.6f) else Color(0x665A6E85),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        if (!isMeasured) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "SIN CONEXIÓN TPMS",
                color = Color(0x995A6E85),
                fontSize = 6.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

fun getTireColor(pressure: Float, hasLiveTpms: Boolean): Color {
    if (!hasLiveTpms || pressure <= 0f) return Color(0xFF5A6E85)
    return when {
        pressure < 27f || pressure > 39f -> MeetColors.error
        pressure < 30f || pressure > 36f -> MeetColors.warning
        else -> MeetColors.success
    }
}

