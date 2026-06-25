package com.elysium369.meet.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.*
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════
// HOLOGRAPHIC PARTICLES DEFINITION
// ═══════════════════════════════════════════════════════════════

private data class HoloParticle(
    val xSeed: Float,
    val ySeed: Float,
    val speed: Float,
    val size: Float,
    val colorAlpha: Float,
    val horizontalDrift: Float
)

private val backgroundParticles = List(30) { index ->
    HoloParticle(
        xSeed = (index * 0.17f) % 1.0f,
        ySeed = (index * 0.23f) % 1.0f,
        speed = 0.02f + (index * 0.008f) % 0.04f,
        size = 1f + (index % 3) * 1f,
        colorAlpha = 0.08f + (index % 4) * 0.04f,
        horizontalDrift = -0.05f + (index * 0.03f) % 0.1f
    )
}

// ═══════════════════════════════════════════════════════════════
// HOLOGRAPHIC BACKGROUND — Animated ambient light + grid + particles
// ═══════════════════════════════════════════════════════════════

@Composable
private fun HolographicBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "holoBg")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing)),
        label = "bgPhase"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bgGlow"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Ambient glow orbs
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    MeetColors.neonGreen.copy(alpha = 0.05f * glowPulse),
                    Color.Transparent
                ),
                center = Offset(w * (0.3f + phase * 0.1f), h * 0.25f),
                radius = w * 0.6f
            ),
            radius = w * 0.6f,
            center = Offset(w * (0.3f + phase * 0.1f), h * 0.25f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    MeetColors.electricBlue.copy(alpha = 0.04f * glowPulse),
                    Color.Transparent
                ),
                center = Offset(w * (0.7f - phase * 0.1f), h * 0.7f),
                radius = w * 0.5f
            ),
            radius = w * 0.5f,
            center = Offset(w * (0.7f - phase * 0.1f), h * 0.7f)
        )

        // Subtle grid lines
        val gridSpacing = 45.dp.toPx()
        val gridAlpha = 0.025f
        val gridColor = MeetColors.neonGreen.copy(alpha = gridAlpha)
        var y = 0f
        while (y < h) {
            drawLine(gridColor, Offset(0f, y), Offset(w, y), 0.5f)
            y += gridSpacing
        }
        var x = 0f
        while (x < w) {
            drawLine(gridColor, Offset(x, 0f), Offset(x, h), 0.5f)
            x += gridSpacing
        }

        // Drifting particles
        backgroundParticles.forEach { p ->
            val px = ((p.xSeed * w) + (phase * p.speed * w) + (p.horizontalDrift * w * sin(phase * 2 * PI.toFloat()))) % w
            val py = ((p.ySeed * h) - (phase * p.speed * h)) % h
            
            val finalX = if (px < 0) px + w else px
            val finalY = if (py < 0) py + h else py

            drawCircle(
                color = MeetColors.neonGreen.copy(alpha = p.colorAlpha * glowPulse * 1.5f),
                radius = p.size.dp.toPx(),
                center = Offset(finalX, finalY)
            )
        }

        // Horizontal scan line
        val scanY = h * phase
        drawLine(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.2f to MeetColors.cyberCyan.copy(alpha = 0.08f),
                0.5f to MeetColors.cyberCyan.copy(alpha = 0.15f),
                0.8f to MeetColors.cyberCyan.copy(alpha = 0.08f),
                1f to Color.Transparent
            ),
            start = Offset(0f, scanY),
            end = Offset(w, scanY),
            strokeWidth = 2f
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// HOLOGRAPHIC CARD — 3D float, glow borders, inner light, corner marks
// ═══════════════════════════════════════════════════════════════

@Composable
private fun HoloCard(
    modifier: Modifier = Modifier,
    accentColor: Color = MeetColors.neonGreen,
    glowIntensity: Float = 0.15f,
    content: @Composable ColumnScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "holoCard")
    val borderPhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)),
        label = "borderPhase"
    )

    // Gentle 3D float oscillation
    val rotX by infiniteTransition.animateFloat(
        initialValue = -2.5f, targetValue = 2.5f,
        animationSpec = infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "rotX"
    )
    val rotY by infiniteTransition.animateFloat(
        initialValue = -3f, targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(5000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "rotY"
    )
    val translationYAnim by infiniteTransition.animateFloat(
        initialValue = -4f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(4500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "transY"
    )

    Box(
        modifier = modifier
            // Apply 3D rotation and translation
            .graphicsLayer {
                rotationX = rotX
                rotationY = rotY
                translationY = translationYAnim
                cameraDistance = 12f * density
            }
            // Shadow layer 1 — deep ambient glow
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = accentColor.copy(alpha = 0.25f),
                spotColor = accentColor.copy(alpha = 0.15f)
            )
            // Shadow layer 2 — tight glow
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = accentColor.copy(alpha = 0.4f),
                spotColor = accentColor.copy(alpha = 0.3f)
            )
            .clip(RoundedCornerShape(16.dp))
            // Glassmorphism background
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D1A2E).copy(alpha = 0.90f),
                        Color(0xFF081428).copy(alpha = 0.93f),
                        Color(0xFF060F20).copy(alpha = 0.96f)
                    )
                )
            )
            // Animated gradient border + HUD corner marks
            .drawBehind {
                val cornerRadius = CornerRadius(16.dp.toPx())
                val borderWidth = 1.5f

                // Animated sweep gradient for the border
                val sweep = borderPhase * 360f
                val colors = listOf(
                    accentColor.copy(alpha = 0.8f),
                    accentColor.copy(alpha = 0.2f),
                    Color.Transparent,
                    Color.Transparent,
                    accentColor.copy(alpha = 0.2f),
                    accentColor.copy(alpha = 0.8f)
                )

                drawRoundRect(
                    brush = Brush.sweepGradient(
                        colors = colors,
                        center = Offset(size.width / 2, size.height / 2)
                    ),
                    cornerRadius = cornerRadius,
                    style = Stroke(width = borderWidth)
                )

                // Inner glow at top
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to accentColor.copy(alpha = glowIntensity * 0.4f),
                        0.20f to Color.Transparent
                    ),
                    cornerRadius = cornerRadius,
                    size = Size(size.width, size.height * 0.3f)
                )

                // Sci-fi corner brackets
                val markerLen = 10.dp.toPx()
                val pad = 2.dp.toPx()
                val w = size.width
                val h = size.height

                // Top-Left
                drawLine(accentColor.copy(alpha = 0.7f), Offset(pad, pad), Offset(pad + markerLen, pad), strokeWidth = 2f)
                drawLine(accentColor.copy(alpha = 0.7f), Offset(pad, pad), Offset(pad, pad + markerLen), strokeWidth = 2f)

                // Top-Right
                drawLine(accentColor.copy(alpha = 0.7f), Offset(w - pad, pad), Offset(w - pad - markerLen, pad), strokeWidth = 2f)
                drawLine(accentColor.copy(alpha = 0.7f), Offset(w - pad, pad), Offset(w - pad, pad + markerLen), strokeWidth = 2f)

                // Bottom-Left
                drawLine(accentColor.copy(alpha = 0.7f), Offset(pad, h - pad), Offset(pad + markerLen, h - pad), strokeWidth = 2f)
                drawLine(accentColor.copy(alpha = 0.7f), Offset(pad, h - pad), Offset(pad, h - pad - markerLen), strokeWidth = 2f)

                // Bottom-Right
                drawLine(accentColor.copy(alpha = 0.7f), Offset(w - pad, h - pad), Offset(w - pad - markerLen, h - pad), strokeWidth = 2f)
                drawLine(accentColor.copy(alpha = 0.7f), Offset(w - pad, h - pad), Offset(w - pad, h - pad - markerLen), strokeWidth = 2f)
            }
            .border(
                width = 0.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.3f),
                        accentColor.copy(alpha = 0.08f),
                        accentColor.copy(alpha = 0.3f)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(content = content)
    }
}

// ═══════════════════════════════════════════════════════════════
// MAIN DTC SCREEN
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DtcScreen(navController: NavController, viewModel: ObdViewModel) {
    val activeDtcs by viewModel.activeDtcs.collectAsState()
    val pendingDtcs by viewModel.pendingDtcs.collectAsState()
    val permanentDtcs by viewModel.permanentDtcs.collectAsState()
    val historicalDtcs by viewModel.historicalDtcs.collectAsState()
    val readiness by viewModel.readinessMonitors.collectAsState()
    val clearResult by viewModel.clearDtcResult.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val terminalOutput by viewModel.terminalSessionLogs.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val lastScanReport by viewModel.lastDtcScanReport.collectAsState()
    val isDemoMode by viewModel.isDemoMode.collectAsState()
    val demoDescription by viewModel.demoScenarioDescription.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        EliteDialog(
            title = "⚠️ Borrar Códigos",
            message = "Esto enviará el Comando de Diagnóstico Mode 04 al vehículo. Se borrarán TODOS los DTCs activos y pendientes, se apagará la luz MIL (Check Engine) y se resetearán los monitores de emisiones.\n\n¿Deseas continuar?",
            onDismiss = { showClearDialog = false },
            onConfirm = {
                showClearDialog = false
                coroutineScope.launch { viewModel.clearDtcs() }
            },
            confirmText = "BORRAR MEMORIA",
            dismissText = "CANCELAR",
            isDestructive = true
        )
    }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val isCompact = screenWidth < 400

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            // Custom holographic top bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0A1628),
                                Color(0xFF070E1C),
                                MeetColors.backgroundDeep
                            )
                        )
                    )
                    .drawBehind {
                        // Bottom edge glow
                        drawLine(
                            brush = Brush.horizontalGradient(
                                0f to Color.Transparent,
                                0.3f to MeetColors.neonGreen.copy(alpha = 0.3f),
                                0.7f to MeetColors.electricBlue.copy(alpha = 0.2f),
                                1f to Color.Transparent
                            ),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.5f
                        )
                    }
                    .statusBarsPadding()
                    .padding(horizontal = if (isCompact) 12.dp else 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = buildAnnotatedString {
                                if (isCompact) {
                                    withStyle(SpanStyle(color = MeetColors.neonGreen)) {
                                        append("DTC ")
                                    }
                                    withStyle(SpanStyle(color = MeetColors.electricBlue)) {
                                        append("SCAN")
                                    }
                                } else {
                                    withStyle(SpanStyle(color = MeetColors.neonGreen)) {
                                        append("DIAGNÓSTICO ")
                                    }
                                    withStyle(SpanStyle(color = MeetColors.electricBlue)) {
                                        append("DTC")
                                    }
                                }
                            },
                            fontWeight = FontWeight.Black,
                            fontSize = if (isCompact) 18.sp else 22.sp,
                            letterSpacing = 1.sp,
                            style = LocalTextStyle.current.copy(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = MeetColors.neonGreen.copy(alpha = 0.4f),
                                    offset = Offset(0f, 0f),
                                    blurRadius = 8f
                                )
                            )
                        )
                        Text(
                            if (isCompact) "Escáner OBD-II" else "Escáner Avanzado de Códigos OBD-II",
                            color = MeetColors.neonGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            style = LocalTextStyle.current.copy(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = MeetColors.neonGreen.copy(alpha = 0.4f),
                                    offset = Offset(0f, 0f),
                                    blurRadius = 4f
                                )
                            )
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // SCAN button with glow
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            MeetColors.neonGreen.copy(alpha = if (isScanning) 0.1f else 0.15f),
                                            MeetColors.neonGreen.copy(alpha = if (isScanning) 0.05f else 0.08f)
                                        )
                                    )
                                )
                                .border(
                                    1.dp,
                                    MeetColors.neonGreen.copy(alpha = if (isScanning) 0.2f else 0.4f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable(enabled = !isScanning) {
                                    coroutineScope.launch { viewModel.refreshDiagnostics() }
                                }
                                .padding(horizontal = if (isCompact) 10.dp else 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                if (isCompact) "SCAN" else "ESCANEAR",
                                color = if (isScanning) MeetColors.textMuted else MeetColors.neonGreen,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                fontSize = if (isCompact) 10.sp else 12.sp,
                                letterSpacing = 1.sp
                            )
                        }
                        // BORRAR button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MeetColors.error.copy(alpha = 0.08f))
                                .border(1.dp, MeetColors.error.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .clickable(enabled = !isScanning) { showClearDialog = true }
                                .padding(horizontal = if (isCompact) 8.dp else 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "BORRAR",
                                color = if (isScanning) MeetColors.textMuted else MeetColors.error,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                fontSize = if (isCompact) 10.sp else 12.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        },
        containerColor = MeetColors.backgroundDeep
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Holographic background layer
            HolographicBackground()

            Column(modifier = Modifier.fillMaxSize()) {
                if (isDemoMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MeetColors.warning.copy(alpha = 0.14f))
                            .border(1.dp, MeetColors.warning.copy(alpha = 0.35f), RoundedCornerShape(0.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("DEMO", color = MeetColors.warning, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        Text(
                            demoDescription.ifBlank { "Datos de entrenamiento; no provienen de un vehículo real." },
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                // ═══════════ HOLOGRAPHIC TAB ROW ═══════════
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            // Glow under selected tab
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    1f to when (selectedTab) {
                                        0 -> MeetColors.error.copy(alpha = 0.05f)
                                        1 -> MeetColors.warning.copy(alpha = 0.05f)
                                        2 -> MeetColors.cyberCyan.copy(alpha = 0.05f)
                                        3 -> MeetColors.electricBlue.copy(alpha = 0.05f)
                                        4 -> MeetColors.neonGreen.copy(alpha = 0.05f)
                                        else -> Color.White.copy(alpha = 0.03f)
                                    }
                                )
                            )
                        }
                ) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = MeetColors.neonGreen,
                        edgePadding = if (isCompact) 6.dp else 12.dp,
                        divider = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            0f to Color.Transparent,
                                            0.2f to MeetColors.borderSubtle,
                                            0.8f to MeetColors.borderSubtle,
                                            1f to Color.Transparent
                                        )
                                    )
                            )
                        },
                        indicator = { tabPositions ->
                            if (selectedTab < tabPositions.size) {
                                Box(
                                    Modifier
                                        .tabIndicatorOffset(tabPositions[selectedTab])
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    Color.Transparent,
                                                    when (selectedTab) {
                                                        0 -> MeetColors.error
                                                        1 -> MeetColors.warning
                                                        2 -> MeetColors.cyberCyan
                                                        3 -> MeetColors.electricBlue
                                                        4 -> MeetColors.neonGreen
                                                        else -> Color.White
                                                    },
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )
                            }
                        }
                    ) {
                        val tabFontSize = if (isCompact) 10.sp else 12.sp
                        val tabData = listOf(
                            Triple("ACTIVOS (${activeDtcs.size})", MeetColors.error, 0),
                            Triple("PEND. (${pendingDtcs.size})", MeetColors.warning, 1),
                            Triple("PERM. (${permanentDtcs.size})", MeetColors.cyberCyan, 2),
                            Triple("HALLAZGOS (${historicalDtcs.size + (lastScanReport?.records?.size ?: 0)})", MeetColors.electricBlue, 3),
                            Triple("MONITORES", MeetColors.neonGreen, 4),
                            Triple("BÚSQUEDA", Color.White, 5)
                        )
                        tabData.forEach { (label, color, index) ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        label,
                                        color = if (selectedTab == index) color else MeetColors.textMuted,
                                        fontWeight = if (selectedTab == index) FontWeight.Black else FontWeight.Medium,
                                        fontSize = tabFontSize,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 0.5.sp,
                                        maxLines = 1
                                    )
                                }
                            )
                        }
                    }
                }

                // ═══════════ CONTENT AREA ═══════════
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val listState = rememberLazyListState()
                    val pad = if (isCompact) 12.dp else 16.dp

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = pad, end = pad, top = pad, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isCompact) 12.dp else 16.dp)
                    ) {
                        if (clearResult != null) {
                            item { ClearResultBanner(clearResult.orEmpty()) }
                        }

                        when (selectedTab) {
                            0 -> {
                                if (activeDtcs.isEmpty()) {
                                    item { HolographicEmptyState("SISTEMA OK", "No hay códigos de falla activos detectados.", MeetColors.neonGreen, isCompact) }
                                } else {
                                    itemsIndexed(activeDtcs) { index, dtc ->
                                        StaggeredEntrance(index) {
                                            HoloDtcCard(dtc, "ACTIVO", MeetColors.error, navController, viewModel, isCompact)
                                        }
                                    }
                                }
                            }
                            1 -> {
                                if (pendingDtcs.isEmpty()) {
                                    item { HolographicEmptyState("SIN PENDIENTES", "No hay anomalías en proceso de confirmación.", MeetColors.warning, isCompact) }
                                } else {
                                    itemsIndexed(pendingDtcs) { index, dtc ->
                                        StaggeredEntrance(index) {
                                            HoloDtcCard(dtc, "PENDIENTE", MeetColors.warning, navController, viewModel, isCompact)
                                        }
                                    }
                                }
                            }
                            2 -> {
                                if (permanentDtcs.isEmpty()) {
                                    item { HolographicEmptyState("HISTORIAL LIMPIO", "No se encontraron códigos permanentes.", MeetColors.cyberCyan, isCompact) }
                                } else {
                                    itemsIndexed(permanentDtcs) { index, dtc ->
                                        StaggeredEntrance(index) {
                                            HoloDtcCard(dtc, "PERMANENTE", MeetColors.cyberCyan, navController, viewModel, isCompact)
                                        }
                                    }
                                }
                            }
                            3 -> { item { DtcFindingsTab(lastScanReport, activeDtcs, pendingDtcs, permanentDtcs, historicalDtcs, navController, isCompact) } }
                            4 -> { item { ReadinessMonitorsView(readiness, coroutineScope, viewModel, screenWidth, isCompact) } }
                            5 -> { item { ManualSearchTab(navController, viewModel, isCompact) } }
                        }
                    }

                    // Scanning overlay
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isScanning,
                        enter = fadeIn(tween(400)),
                        exit = fadeOut(tween(300))
                    ) {
                        HolographicScanOverlay(statusMessage, terminalOutput, isCompact)
                    }
                }
            }
        }
    }
}

@Composable
private fun DtcFindingsTab(
    report: com.elysium369.meet.core.obd.DtcScanReport?,
    activeDtcs: List<String>,
    pendingDtcs: List<String>,
    permanentDtcs: List<String>,
    historicalDtcs: List<String>,
    navController: NavController,
    isCompact: Boolean
) {
    val totalCodes = (activeDtcs + pendingDtcs + permanentDtcs + historicalDtcs).distinct().size
    if (report == null && totalCodes == 0) {
        HolographicEmptyState(
            "SIN HALLAZGOS",
            "Toca ESCANEAR para capturar DTCs, módulos que respondieron y evidencia técnica del último barrido.",
            MeetColors.electricBlue,
            isCompact
        )
        return
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HoloCard(
            modifier = Modifier.fillMaxWidth(),
            accentColor = MeetColors.electricBlue,
            glowIntensity = 0.18f
        ) {
            Column(Modifier.padding(if (isCompact) 14.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "RESUMEN DEL ÚLTIMO ESCANEO REAL",
                    color = MeetColors.electricBlue,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FindingMetric("Activos", activeDtcs.size, MeetColors.error, Modifier.weight(1f))
                    FindingMetric("Pend.", pendingDtcs.size, MeetColors.warning, Modifier.weight(1f))
                    FindingMetric("Perm.", permanentDtcs.size, MeetColors.cyberCyan, Modifier.weight(1f))
                    FindingMetric("Hist.", historicalDtcs.size, MeetColors.textSecondary, Modifier.weight(1f))
                }
                Text(
                    "Los códigos se muestran aunque aún no hayas seleccionado un vehículo. Para historial permanente, selecciona un vehículo en Garage antes o después del escaneo.",
                    color = MeetColors.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        val reportRecords = report?.records.orEmpty()
        if (reportRecords.isNotEmpty()) {
            HoloCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = MeetColors.neonGreen,
                glowIntensity = 0.12f
            ) {
                Column(Modifier.padding(if (isCompact) 14.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "CÓDIGOS CAPTURADOS",
                        color = MeetColors.neonGreen,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                    reportRecords
                        .distinctBy { "${it.code}|${it.bucket}|${it.moduleName}|${it.responseAddress}" }
                        .take(12)
                        .forEach { record ->
                            val bucketLabel = when (record.bucket) {
                                com.elysium369.meet.core.obd.DtcBucket.ACTIVE -> "ACTIVO"
                                com.elysium369.meet.core.obd.DtcBucket.PENDING -> "PENDIENTE"
                                com.elysium369.meet.core.obd.DtcBucket.PERMANENT -> "PERMANENTE"
                                com.elysium369.meet.core.obd.DtcBucket.HISTORY -> "HISTÓRICO"
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF050C18), RoundedCornerShape(8.dp))
                                    .border(0.5.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(record.code, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                    Text(
                                        "${record.moduleName ?: "Módulo OBD-II"} · ${record.sourceService} · $bucketLabel",
                                        color = MeetColors.textSecondary,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                EliteButton(
                                    text = "GUÍA",
                                    onClick = { navController.navigate("repair/${record.code}") },
                                    color = MeetColors.neonGreen,
                                    modifier = Modifier.width(76.dp).height(30.dp)
                                )
                            }
                        }
                }
            }
        }

        val aliveModules = report?.modules.orEmpty().filter { it.isAlive }
        if (aliveModules.isNotEmpty()) {
            HoloCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = MeetColors.cyberCyan,
                glowIntensity = 0.12f
            ) {
                Column(Modifier.padding(if (isCompact) 14.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "MÓDULOS QUE RESPONDIERON",
                        color = MeetColors.cyberCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                    aliveModules.take(10).forEach { module ->
                        Text(
                            "• ${module.moduleName}: ${module.dtcs.map { it.code }.distinct().joinToString().ifBlank { "sin DTC" }}",
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

@Composable
private fun FindingMetric(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value.toString(), color = color, fontWeight = FontWeight.Black, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
        Text(label, color = MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

// ═══════════════════════════════════════════════════════════════
// HOLOGRAPHIC EMPTY STATE — Rotating rings + floating particles
// ═══════════════════════════════════════════════════════════════

@Composable
private fun HolographicEmptyState(title: String, subtitle: String, color: Color, isCompact: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "holoEmpty")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "emptyRot"
    )
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "emptyInnerRot"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "emptyPulse"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "emptyGlow"
    )
    val floatY by infiniteTransition.animateFloat(
        initialValue = -5f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "emptyFloat"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isCompact) 32.dp else 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .graphicsLayer { translationY = floatY }
        ) {
            // Holographic orb with rotating rings
            val orbSize = if (isCompact) 100.dp else 130.dp
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(orbSize)
            ) {
                // Outer glow
                Box(
                    modifier = Modifier
                        .size(orbSize)
                        .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale; alpha = glowAlpha * 0.3f }
                        .background(
                            Brush.radialGradient(
                                colors = listOf(color.copy(alpha = 0.3f), Color.Transparent)
                            ),
                            CircleShape
                        )
                )

                // Rotating rings with sweep trail
                Canvas(
                    modifier = Modifier
                        .size(orbSize)
                        .graphicsLayer { rotationZ = rotation }
                ) {
                    val c = Offset(size.width / 2, size.height / 2)
                    val r = size.minDimension / 2 * 0.9f
                    
                    // Sweeping gradient trail
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                color.copy(alpha = 0.5f),
                                color.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        ),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    
                    // Dashes on outer ring
                    for (i in 0 until 12) {
                        val angle = (i * 30f) * (PI / 180f).toFloat()
                        val startR = r * 0.88f
                        val endR = r * 1f
                        drawLine(
                            color.copy(alpha = 0.5f),
                            Offset(c.x + startR * cos(angle), c.y + startR * sin(angle)),
                            Offset(c.x + endR * cos(angle), c.y + endR * sin(angle)),
                            strokeWidth = 2f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Inner counter-rotating ring
                Canvas(
                    modifier = Modifier
                        .size(orbSize * 0.65f)
                        .graphicsLayer { rotationZ = innerRotation }
                ) {
                    val c = Offset(size.width / 2, size.height / 2)
                    val r = size.minDimension / 2 * 0.85f
                    
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                color.copy(alpha = 0.1f),
                                color.copy(alpha = 0.4f)
                            )
                        ),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    
                    // Dots on inner ring
                    for (i in 0 until 8) {
                        val angle = (i * 45f) * (PI / 180f).toFloat()
                        drawCircle(
                            color.copy(alpha = 0.7f),
                            2.5f,
                            Offset(c.x + r * cos(angle), c.y + r * sin(angle))
                        )
                    }
                }

                // Center icon
                Box(
                    modifier = Modifier
                        .size(if (isCompact) 44.dp else 56.dp)
                        .shadow(8.dp, CircleShape, ambientColor = color.copy(alpha = 0.4f), spotColor = color.copy(alpha = 0.3f))
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    color.copy(alpha = 0.2f),
                                    color.copy(alpha = 0.05f)
                                )
                            )
                        )
                        .border(1.dp, color.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "✓", color = color,
                        fontSize = if (isCompact) 22.sp else 28.sp,
                        fontWeight = FontWeight.Black,
                        style = LocalTextStyle.current.copy(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = color.copy(alpha = 0.8f),
                                offset = Offset(0f, 0f),
                                blurRadius = 8f
                            )
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Title with glow effect
            Text(
                text = title,
                color = Color.White,
                fontSize = if (isCompact) 14.sp else 17.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
                style = LocalTextStyle.current.copy(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = color.copy(alpha = 0.8f),
                        offset = Offset(0f, 0f),
                        blurRadius = 10f
                    )
                ),
                modifier = Modifier.drawBehind {
                    drawRoundRect(
                        color = color.copy(alpha = glowAlpha * 0.08f),
                        cornerRadius = CornerRadius(8f),
                        topLeft = Offset(-12f, -4f),
                        size = Size(size.width + 24f, size.height + 8f)
                    )
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                color = MeetColors.textSecondary,
                fontSize = if (isCompact) 11.sp else 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = if (isCompact) 15.sp else 18.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// HOLOGRAPHIC DTC CARD — 3D with shadows + animated glow
// ═══════════════════════════════════════════════════════════════

@Composable
private fun HoloDtcCard(
    dtc: String,
    severity: String,
    accentColor: Color,
    navController: NavController,
    viewModel: ObdViewModel,
    isCompact: Boolean
) {
    val dtcDefinitions by viewModel.dtcDefinitions.collectAsState()
    val definition = dtcDefinitions[dtc]
    val desc = if (definition != null) {
        com.elysium369.meet.ui.components.DtcUtils.getSpanishDescription(definition, dtc)
    } else {
        com.elysium369.meet.ui.components.DtcUtils.getSpanishDescriptionFromRaw(
            dtc,
            com.elysium369.meet.core.obd.DtcDecoder.getLocalDescription(dtc)
        )
    }
    val causes = com.elysium369.meet.ui.components.DtcUtils.getSpanishPossibleCauses(dtc, definition?.possibleCauses)
    var expanded by remember { mutableStateOf(false) }

    HoloCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        accentColor = accentColor,
        glowIntensity = if (expanded) 0.25f else 0.12f
    ) {
        Column(
            modifier = Modifier
                .padding(if (isCompact) 14.dp else 18.dp)
                .fillMaxWidth()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Severity badge with glow
                    Box(
                        modifier = Modifier
                            .shadow(4.dp, RoundedCornerShape(6.dp), ambientColor = accentColor.copy(alpha = 0.3f), spotColor = accentColor.copy(alpha = 0.2f))
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(accentColor.copy(alpha = 0.2f), accentColor.copy(alpha = 0.08f))
                                )
                            )
                            .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            severity,
                            color = accentColor,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    // DTC Code with real glow shadow
                    Text(
                        text = dtc,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (isCompact) 20.sp else 24.sp,
                        style = LocalTextStyle.current.copy(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = accentColor.copy(alpha = 0.8f),
                                offset = Offset(0f, 0f),
                                blurRadius = 12f
                            )
                        ),
                        modifier = Modifier.drawBehind {
                            drawRoundRect(
                                color = accentColor.copy(alpha = 0.06f),
                                cornerRadius = CornerRadius(4f),
                                topLeft = Offset(-6f, -2f),
                                size = Size(size.width + 12f, size.height + 4f)
                            )
                        }
                    )
                }
                // Expand indicator
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MeetColors.cardBackgroundLighter.copy(alpha = 0.5f))
                        .border(0.5.dp, MeetColors.borderSubtle, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.Warning else Icons.Default.Search,
                        contentDescription = null,
                        tint = MeetColors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = desc,
                color = MeetColors.textPrimary,
                fontSize = if (isCompact) 13.sp else 14.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = if (isCompact) 18.sp else 20.sp,
                softWrap = true
            )

            // Expanded content
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(300)) + expandVertically(tween(350)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(250))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(14.dp))
                    // Gradient divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                Brush.horizontalGradient(
                                    0f to Color.Transparent,
                                    0.3f to accentColor.copy(alpha = 0.3f),
                                    0.7f to accentColor.copy(alpha = 0.3f),
                                    1f to Color.Transparent
                                )
                            )
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (!causes.isNullOrBlank()) {
                        Text(
                            "▸ POSIBLES CAUSAS",
                            color = MeetColors.neonGreen,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(4.dp, RoundedCornerShape(10.dp), ambientColor = Color.Black.copy(alpha = 0.4f))
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF060D1A))
                                .border(
                                    0.5.dp,
                                    Brush.linearGradient(
                                        listOf(MeetColors.borderSubtle.copy(alpha = 0.5f), MeetColors.borderSubtle.copy(alpha = 0.2f))
                                    ),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Text(causes, color = MeetColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp, softWrap = true)
                        }
                    }

                    // Freeze Frame
                    val freezeFrame by viewModel.freezeFrameData.collectAsState()
                    val scopedFrame = freezeFrame.filter { it.key.startsWith("$dtc:") }
                    if (scopedFrame.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "▸ CUADRO CONGELADO",
                            color = MeetColors.cyberCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(4.dp, RoundedCornerShape(10.dp))
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF040A14))
                                .border(0.5.dp, MeetColors.borderSubtle.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                scopedFrame.forEach { (k, v) ->
                                    val pid = k.substringAfter(":")
                                    val name = when (pid) {
                                        "04" -> "Carga Motor"; "05" -> "Temp. Refrig."; "0C" -> "RPM"
                                        "0D" -> "Velocidad"; "11" -> "Acelerador"; else -> "PID $pid"
                                    }
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text(name, color = MeetColors.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                        Text(v, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Buttons
                    if (isCompact) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            EliteButton("🛠️ CÓMO REPARAR (PASO A PASO)", { navController.navigate("repair/$dtc") }, color = MeetColors.neonGreen, modifier = Modifier.fillMaxWidth())
                            EliteButton("🤖 ANALIZAR CON IA", { navController.navigate("ai/$dtc") }, color = MeetColors.electricBlue, textColor = Color.White, modifier = Modifier.fillMaxWidth())
                            val cs = rememberCoroutineScope()
                            EliteOutlinedButton("❄️ RE-LEER FF", { cs.launch { viewModel.refreshFreezeFrame(dtc) } }, color = MeetColors.cyberCyan, modifier = Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            EliteButton("🛠️ CÓMO REPARAR", { navController.navigate("repair/$dtc") }, color = MeetColors.neonGreen, modifier = Modifier.weight(1f))
                            EliteButton("🤖 ANALIZAR CON IA", { navController.navigate("ai/$dtc") }, color = MeetColors.electricBlue, textColor = Color.White, modifier = Modifier.weight(1f))
                            val cs = rememberCoroutineScope()
                            EliteOutlinedButton("❄️ RE-LEER FF", { cs.launch { viewModel.refreshFreezeFrame(dtc) } }, color = MeetColors.cyberCyan, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// HOLOGRAPHIC SCAN OVERLAY
// ═══════════════════════════════════════════════════════════════

// Radar Blip definition
private data class RadarBlip(
    val xOffset: Float,
    val yOffset: Float,
    val color: Color,
    val size: Float,
    val label: String
)

private val radarBlips = listOf(
    RadarBlip(-0.35f, -0.4f, MeetColors.neonGreen, 4f, "ECU"),
    RadarBlip(0.4f, -0.3f, MeetColors.neonGreen, 4f, "TCU"),
    RadarBlip(-0.5f, 0.35f, MeetColors.neonGreen, 4f, "ABS"),
    RadarBlip(0.35f, 0.45f, MeetColors.error, 5f, "MIL"),
    RadarBlip(-0.1f, 0.5f, MeetColors.warning, 4f, "SRS")
)

@Composable
private fun HolographicScanOverlay(statusMessage: String, terminalOutput: List<TerminalLine>, isCompact: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanOvr")
    val rotation by infiniteTransition.animateFloat(0f, 360f, infiniteRepeatable(tween(2500, easing = LinearEasing)), label = "sr")
    val pulseAlpha by infiniteTransition.animateFloat(0.4f, 1f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sp")
    val scanY by infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "sy")
    val glowPulse by infiniteTransition.animateFloat(0.7f, 1.3f, infiniteRepeatable(tween(1200, easing = FastOutLinearInEasing), RepeatMode.Reverse), label = "sg")
    val phase by infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(5000, easing = LinearEasing)), label = "phase")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF020610).copy(alpha = 0.97f),
                        Color(0xFF040C18).copy(alpha = 0.98f),
                        Color(0xFF020610).copy(alpha = 0.97f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Background grid with scanning crosshairs
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridSpacing = 40.dp.toPx()
            val gridColor = MeetColors.neonGreen.copy(alpha = 0.02f)
            var gy = 0f; while (gy < size.height) { drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), 0.5f); gy += gridSpacing }
            var gx = 0f; while (gx < size.width) { drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), 0.5f); gx += gridSpacing }
            
            // Scan line
            val lineYPos = size.height * scanY
            drawLine(
                brush = Brush.horizontalGradient(0f to Color.Transparent, 0.3f to MeetColors.neonGreen.copy(alpha = 0.15f), 0.7f to MeetColors.neonGreen.copy(alpha = 0.15f), 1f to Color.Transparent),
                start = Offset(0f, lineYPos), end = Offset(size.width, lineYPos), strokeWidth = 2f
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = if (isCompact) 16.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Radar
            val radarSize = if (isCompact) 110.dp else 150.dp
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(radarSize)) {
                // Outer glow orb
                Box(
                    modifier = Modifier
                        .size(radarSize)
                        .graphicsLayer { scaleX = glowPulse; scaleY = glowPulse; alpha = 0.15f }
                        .background(Brush.radialGradient(listOf(MeetColors.neonGreen.copy(alpha = 0.2f), Color.Transparent)), CircleShape)
                )
                
                Canvas(modifier = Modifier.size(radarSize)) {
                    val c = Offset(size.width / 2, size.height / 2)
                    val r = size.minDimension / 2 * 0.8f
                    
                    // Radar concentric rings
                    for (i in 1..4) {
                        drawCircle(MeetColors.neonGreen.copy(alpha = 0.03f + i * 0.015f), r * (i / 4f), c, style = Stroke(0.8f))
                    }
                    drawCircle(MeetColors.neonGreen.copy(alpha = 0.15f * pulseAlpha), r * glowPulse, c, style = Stroke(1.5f))
                    
                    // Sweeping radar arc (analog phosphor sweep)
                    val sweepStartAngle = rotation - 90f
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                MeetColors.neonGreen.copy(alpha = 0.4f),
                                MeetColors.neonGreen.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            center = c
                        ),
                        startAngle = sweepStartAngle,
                        sweepAngle = 90f,
                        useCenter = true
                    )
                    
                    // Sweeping ray line
                    val angle = rotation * (PI / 180f).toFloat()
                    drawLine(MeetColors.neonGreen.copy(alpha = 0.8f), c, Offset(c.x + r * cos(angle), c.y + r * sin(angle)), 2f, cap = StrokeCap.Round)
                    
                    // Digital corner crosshair markings
                    val markSize = 12f
                    listOf(
                        Pair(Offset(c.x - r, c.y - r), Pair(1f, 1f)),
                        Pair(Offset(c.x + r, c.y - r), Pair(-1f, 1f)),
                        Pair(Offset(c.x - r, c.y + r), Pair(1f, -1f)),
                        Pair(Offset(c.x + r, c.y + r), Pair(-1f, -1f))
                    ).forEach { (pos, dir) ->
                        drawLine(MeetColors.neonGreen.copy(alpha = 0.5f), pos, Offset(pos.x + markSize * dir.first, pos.y), 1.5f)
                        drawLine(MeetColors.neonGreen.copy(alpha = 0.5f), pos, Offset(pos.x, pos.y + markSize * dir.second), 1.5f)
                    }

                    // Render decaying radar blips
                    radarBlips.forEach { blip ->
                        val bx = c.x + r * blip.xOffset
                        val by = c.y + r * blip.yOffset
                        
                        // Angle of blip relative to center (in degrees, 0 to 360)
                        val dx = blip.xOffset
                        val dy = blip.yOffset
                        var blipAngleRad = kotlin.math.atan2(dy, dx)
                        if (blipAngleRad < 0) blipAngleRad += (2 * PI).toFloat()
                        val blipAngleDeg = blipAngleRad * (180f / PI.toFloat())
                        
                        // Calculate sweep angle difference to establish decay
                        val currentSweepDeg = rotation % 360f
                        var angleDiff = currentSweepDeg - blipAngleDeg
                        if (angleDiff < 0) angleDiff += 360f
                        
                        // Decaying brightness based on sweep pass
                        val decay = if (angleDiff < 90f) {
                            1f - (angleDiff / 90f) // brightest right after sweep, fading over 90 degrees
                        } else {
                            0.15f // base ambient glow
                        }
                        
                        // Draw glowing halo around blip
                        drawCircle(
                            color = blip.color.copy(alpha = 0.45f * decay),
                            radius = (blip.size * 2.2f).dp.toPx() * (0.8f + 0.2f * sin(phase * 10f)),
                            center = Offset(bx, by)
                        )
                        // Draw core point
                        drawCircle(
                            color = blip.color.copy(alpha = 0.9f * decay + 0.1f),
                            radius = blip.size.dp.toPx(),
                            center = Offset(bx, by)
                        )
                    }
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SCAN", color = MeetColors.neonGreen.copy(alpha = pulseAlpha), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = if (isCompact) 14.sp else 18.sp, letterSpacing = 6.sp, style = LocalTextStyle.current.copy(shadow = androidx.compose.ui.graphics.Shadow(color = MeetColors.neonGreen.copy(alpha = 0.8f), offset = Offset(0f,0f), blurRadius = 8f)))
                    Text("ECU", color = Color.White.copy(alpha = 0.3f), fontFamily = FontFamily.Monospace, fontSize = 8.sp, letterSpacing = 4.sp)
                }
            }

            Spacer(Modifier.height(if (isCompact) 14.dp else 20.dp))
            val displayMessage = if (statusMessage.isNotBlank()) statusMessage.uppercase() else "ESCANEO EN PROGRESO"
            Text(
                text = displayMessage,
                color = MeetColors.neonGreen.copy(alpha = pulseAlpha),
                fontSize = if (isCompact) 11.sp else 14.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(if (isCompact) 0.8f else 0.6f).height(3.dp).clip(RoundedCornerShape(2.dp)), color = MeetColors.neonGreen, trackColor = MeetColors.borderSubtle)
            Spacer(Modifier.height(if (isCompact) 14.dp else 20.dp))

            // Terminal
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isCompact) 150.dp else 190.dp)
                    .shadow(8.dp, RoundedCornerShape(12.dp), ambientColor = MeetColors.neonGreen.copy(alpha = 0.1f))
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF020810).copy(alpha = 0.9f))
                    .border(1.dp, Brush.linearGradient(listOf(MeetColors.neonGreen.copy(alpha = 0.15f), MeetColors.borderSubtle.copy(alpha = 0.3f))), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                val termState = rememberLazyListState()
                val lastLogs = terminalOutput.takeLast(12)
                LaunchedEffect(lastLogs.size) { if (lastLogs.isNotEmpty()) termState.animateScrollToItem(lastLogs.size - 1) }
                LazyColumn(state = termState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(lastLogs) { _, log ->
                        val c = when (log.type) { TerminalLineType.ERROR -> MeetColors.error; TerminalLineType.WARNING -> MeetColors.warning; TerminalLineType.COMMAND -> MeetColors.electricBlue; else -> MeetColors.neonGreen.copy(alpha = 0.8f) }
                        Text("> ${log.text}", color = c, fontFamily = FontFamily.Monospace, fontSize = if (isCompact) 9.sp else 11.sp, lineHeight = if (isCompact) 13.sp else 16.sp, modifier = Modifier.padding(vertical = 1.dp), softWrap = true)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// STAGGERED ENTRANCE ANIMATION
// ═══════════════════════════════════════════════════════════════

@Composable
private fun StaggeredEntrance(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(index.toLong() * 100); visible = true }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(500, easing = FastOutSlowInEasing), label = "sa")
    val offsetY by animateFloatAsState(if (visible) 0f else 50f, tween(500, easing = FastOutSlowInEasing), label = "so")
    val scale by animateFloatAsState(if (visible) 1f else 0.9f, tween(500, easing = FastOutSlowInEasing), label = "ss")
    Box(Modifier.alpha(alpha).graphicsLayer { translationY = offsetY; scaleX = scale; scaleY = scale }) { content() }
}

// ═══════════════════════════════════════════════════════════════
// CLEAR RESULT BANNER
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ClearResultBanner(result: String) {
    val isSuccess = result.contains("✅")
    val borderColor = if (isSuccess) MeetColors.neonGreen else MeetColors.error
    HoloCard(accentColor = borderColor, modifier = Modifier.fillMaxWidth()) {
        Text(result, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, softWrap = true, modifier = Modifier.padding(14.dp).fillMaxWidth())
    }
}

// ═══════════════════════════════════════════════════════════════
// READINESS MONITORS
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ReadinessMonitorsView(readiness: com.elysium369.meet.core.obd.ReadinessResult?, coroutineScope: kotlinx.coroutines.CoroutineScope, viewModel: ObdViewModel, screenWidth: Int, isCompact: Boolean) {
    if (readiness == null) {
        HolographicEmptyState("SIN DATOS", "Toca ESCANEAR para leer los monitores.", MeetColors.neonGreen, isCompact)
    } else {
        Column(Modifier.fillMaxWidth()) {
            // MIL card
            HoloCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = if (readiness.milOn) MeetColors.error else MeetColors.neonGreen,
                glowIntensity = 0.2f
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("ESTADO LUZ MIL", color = MeetColors.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (readiness.milOn) "🔴 ENCENDIDA" else "🟢 APAGADA",
                            color = if (readiness.milOn) MeetColors.error else MeetColors.neonGreen,
                            fontWeight = FontWeight.Black, fontSize = if (isCompact) 15.sp else 18.sp, fontFamily = FontFamily.Monospace
                        )
                    }
                    Box(
                        modifier = Modifier
                            .shadow(4.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF040A14))
                            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("${readiness.dtcCount} DTCs", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            PhantomSectionHeader(label = "MONITORES DE EMISIÓN")
            Spacer(Modifier.height(6.dp))

            val passed = readiness.monitors.count { it.complete }
            Text("$passed de ${readiness.monitors.size} completados", color = if (passed == readiness.monitors.size) MeetColors.neonGreen else MeetColors.warning, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))

            val cols = when { screenWidth >= 700 -> 3; screenWidth >= 420 -> 2; else -> 1 }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                readiness.monitors.chunked(cols).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { mon ->
                            val c = if (mon.complete) MeetColors.neonGreen else MeetColors.warning
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .shadow(4.dp, RoundedCornerShape(10.dp), ambientColor = c.copy(alpha = 0.1f))
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Brush.verticalGradient(listOf(Color(0xFF0D1A2E), Color(0xFF081428))))
                                    .border(0.5.dp, c.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                    .padding(if (isCompact) 10.dp else 12.dp)
                            ) {
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                    Text(mon.name, color = Color.White, fontSize = if (isCompact) 11.sp else 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), softWrap = true)
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .shadow(2.dp, RoundedCornerShape(4.dp), ambientColor = c.copy(alpha = 0.3f))
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(c.copy(alpha = 0.12f))
                                            .border(1.dp, c, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(if (mon.complete) "OK" else "INC", color = c, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 8.sp)
                                    }
                                }
                            }
                        }
                        if (row.size < cols) repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// MANUAL SEARCH TAB
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualSearchTab(navController: NavController, viewModel: ObdViewModel, isCompact: Boolean) {
    var searchQuery by remember { mutableStateOf("") }
    val manualResults by viewModel.manualSearchResults.collectAsState()
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = searchQuery, onValueChange = { searchQuery = it.uppercase() },
            label = { Text(if (isCompact) "Código DTC" else "Ingresar Código DTC (Ej. P0300)", color = MeetColors.textSecondary) },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = MeetColors.neonGreen, unfocusedBorderColor = MeetColors.borderSubtle, cursorColor = MeetColors.neonGreen, focusedContainerColor = Color(0xFF0A1220), unfocusedContainerColor = Color(0xFF0A1220)),
            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(12.dp))
        EliteButton(if (isCompact) "BUSCAR" else "BUSCAR EN BASE DE DATOS", { val q = searchQuery.trim(); if (q.isNotEmpty()) viewModel.searchDtcManual(q) }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        if (manualResults.isNotEmpty()) {
            Text("RESULTADOS (${manualResults.size})", color = MeetColors.neonGreen, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                manualResults.forEachIndexed { index, dtc ->
                    StaggeredEntrance(index) {
                        val c = when (dtc.severity.uppercase()) { "HIGH", "CRITICAL" -> MeetColors.error; "MODERATE" -> MeetColors.warning; else -> MeetColors.neonGreen }
                        HoloCard(modifier = Modifier.fillMaxWidth(), accentColor = c) {
                            Column(Modifier.padding(if (isCompact) 14.dp else 18.dp).fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.shadow(2.dp, RoundedCornerShape(6.dp), ambientColor = c.copy(alpha = 0.3f)).clip(RoundedCornerShape(6.dp)).background(c.copy(alpha = 0.15f)).border(1.dp, c, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                        Text(dtc.severity.uppercase(), color = c, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(dtc.code, color = Color.White, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = if (isCompact) 16.sp else 20.sp)
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    com.elysium369.meet.ui.components.DtcUtils.getSpanishDescription(dtc, dtc.code),
                                    color = Color.White,
                                    fontSize = if (isCompact) 12.sp else 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    softWrap = true,
                                    lineHeight = 18.sp
                                )
                                Spacer(Modifier.height(10.dp))
                                Text("▸ POSIBLES CAUSAS", color = MeetColors.cyberCyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                Spacer(Modifier.height(4.dp))
                                Box(Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(Color(0xFF060D1A)).border(0.5.dp, MeetColors.borderSubtle.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(10.dp)) {
                                    Text(
                                        com.elysium369.meet.ui.components.DtcUtils.getSpanishPossibleCauses(dtc.code, dtc.possibleCauses),
                                        color = MeetColors.textSecondary,
                                        fontSize = if (isCompact) 10.sp else 11.sp,
                                        lineHeight = 15.sp,
                                        softWrap = true
                                    )
                                }
                                Spacer(Modifier.height(14.dp))
                                if (isCompact) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        EliteButton("🛠️ CÓMO REPARAR (PASO A PASO)", { navController.navigate("repair/${dtc.code}") }, color = MeetColors.neonGreen, modifier = Modifier.fillMaxWidth())
                                        EliteButton("🤖 ANALIZAR CON IA", { navController.navigate("ai/${dtc.code}") }, color = MeetColors.electricBlue, textColor = Color.White, modifier = Modifier.fillMaxWidth())
                                    }
                                } else {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        EliteButton("🛠️ CÓMO REPARAR", { navController.navigate("repair/${dtc.code}") }, color = MeetColors.neonGreen, modifier = Modifier.weight(1f))
                                        EliteButton("🤖 ANALIZAR CON IA", { navController.navigate("ai/${dtc.code}") }, color = MeetColors.electricBlue, textColor = Color.White, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (searchQuery.isNotBlank()) {
            HolographicEmptyState("SIN COINCIDENCIAS", "No se encontraron códigos en la base de datos local.", MeetColors.textSecondary, isCompact)
        }
    }
}
