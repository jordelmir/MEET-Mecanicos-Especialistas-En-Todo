package com.elysium369.meet.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.*
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.*

// ════════════════════════════════════════════════════════════════════════════
//  HOLO LOCAL READ SCREEN — MAXIMUM VISUAL FIDELITY
//  Full holographic 3D dashboard with particles, glows, and real shadows
// ════════════════════════════════════════════════════════════════════════════

private fun getMetricFromMap(map: Map<String, Float>, key: String, containsKeyword: String? = null): Float {
    return map[key] ?: if (containsKeyword != null) {
        map.entries.firstOrNull { it.key.contains(containsKeyword, true) }?.value ?: 0f
    } else {
        map.entries.firstOrNull { it.key.contains(key, true) }?.value ?: 0f
    }
}

@Composable
fun HoloLocalReadScreen(
    viewModel: ObdViewModel,
    navController: NavController
) {
    Box(modifier = Modifier.fillMaxSize().background(MeetColors.backgroundDeep)) {
        // ── Layer 1: Holographic background particles ──
        HoloLocalBackground()

        // ── Layer 2: Deep 3D ambient glow blobs ──
        AmbientGlowBlobs()

        // ── Layer 3: Content ──
        Column(modifier = Modifier.fillMaxSize()) {

            // ── HEADER ──
            HoloHeader(onBack = { navController.popBackStack() })

            // ── MAIN CONTENT GRID ──
            val gridState = rememberLazyGridState()
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // ── Full-width Live Status Banner ──
                item(span = { GridItemSpan(2) }) {
                    LiveStatusBanner(viewModel)
                }

                // ── Primary gauges (full arc dial) ──
                item {
                    HoloGaugeCard(
                        viewModel = viewModel,
                        pidKey = "010C",
                        label = "RPM",
                        maxVal = 8000f,
                        unit = "rpm",
                        glowColor = MeetColors.neonGreen,
                        icon = "⚡"
                    )
                }
                item {
                    HoloGaugeCard(
                        viewModel = viewModel,
                        pidKey = "010D",
                        label = "VELOCIDAD",
                        maxVal = 260f,
                        unit = "km/h",
                        glowColor = MeetColors.electricBlue,
                        icon = "🏎"
                    )
                }

                // ── Secondary metrics ──
                item {
                    HoloMetricCard(
                        viewModel = viewModel,
                        pidKey = "0105",
                        label = "COOLANT",
                        unit = "°C",
                        maxVal = 130f,
                        warningAt = 95f,
                        glowColor = MeetColors.cyberCyan,
                        icon = "🌡"
                    )
                }
                item {
                    HoloMetricCard(
                        viewModel = viewModel,
                        pidKey = "0111",
                        label = "THROTTLE",
                        unit = "%",
                        maxVal = 100f,
                        warningAt = 85f,
                        glowColor = MeetColors.neonGreen,
                        icon = "🎛"
                    )
                }
                item {
                    HoloMetricCard(
                        viewModel = viewModel,
                        pidKey = "012F",
                        label = "COMBUSTIBLE",
                        unit = "%",
                        maxVal = 100f,
                        warningAt = 20f,
                        glowColor = Color(0xFFFF9500),
                        icon = "⛽"
                    )
                }
                item {
                    HoloMetricCard(
                        viewModel = viewModel,
                        pidKey = "CALC_VOLTAGE",
                        containsKeyword = "VOLT",
                        label = "VOLTAJE",
                        unit = "V",
                        maxVal = 16f,
                        warningAt = 11.5f,
                        glowColor = Color(0xFFFFD700),
                        icon = "🔋"
                    )
                }

                // ── Full-width Waveform ──
                item(span = { GridItemSpan(2) }) {
                    HoloWavePanel(viewModel)
                }

                // ── Engine Load + MAF ──
                item {
                    HoloMetricCard(
                        viewModel = viewModel,
                        pidKey = "0104",
                        label = "CARGA MOTOR",
                        unit = "%",
                        maxVal = 100f,
                        warningAt = 90f,
                        glowColor = MeetColors.warning,
                        icon = "⚙️"
                    )
                }
                item {
                    HoloMetricCard(
                        viewModel = viewModel,
                        pidKey = "0110",
                        label = "FLUJO DE AIRE",
                        unit = "g/s",
                        maxVal = 200f,
                        warningAt = 180f,
                        glowColor = MeetColors.electricBlue,
                        icon = "💨"
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  HEADER — Holographic top bar with animated scan line
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun HoloHeader(onBack: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "headerGlow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowPulse"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .drawBehind {
                // Bottom glow line
                drawLine(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.3f to MeetColors.neonGreen.copy(alpha = 0.5f * glowPulse),
                        0.7f to MeetColors.cyberCyan.copy(alpha = 0.4f * glowPulse),
                        1f to Color.Transparent
                    ),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.5f
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button with glow
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        ambientColor = MeetColors.neonGreen.copy(alpha = 0.3f),
                        spotColor = MeetColors.neonGreen.copy(alpha = 0.4f)
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MeetColors.neonGreen.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("←", color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }

            // Title block
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "LECTURA EN VIVO",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(MeetColors.neonGreen.copy(alpha = glowPulse), CircleShape)
                            .shadow(4.dp, CircleShape, ambientColor = MeetColors.neonGreen)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "OBD2 • MODO LOCAL • ACTIVO",
                        color = MeetColors.neonGreen.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Pulsing radar icon
            HoloRadarIcon(glowPulse = glowPulse)
        }
    }
}

@Composable
private fun HoloRadarIcon(glowPulse: Float) {
    Box(
        modifier = Modifier.size(44.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            // Outer ring
            drawCircle(
                color = MeetColors.neonGreen.copy(alpha = 0.2f),
                radius = size.minDimension / 2f,
                center = Offset(cx, cy),
                style = Stroke(1.dp.toPx())
            )
            // Inner ring pulsing
            drawCircle(
                color = MeetColors.neonGreen.copy(alpha = 0.4f * glowPulse),
                radius = size.minDimension / 3f,
                center = Offset(cx, cy),
                style = Stroke(1.dp.toPx())
            )
            // Center dot
            drawCircle(
                color = MeetColors.neonGreen.copy(alpha = glowPulse),
                radius = 3.dp.toPx(),
                center = Offset(cx, cy)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  LIVE STATUS BANNER — Full-width animated RPM + Speed banner
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun LiveStatusBanner(viewModel: ObdViewModel) {
    val rpmFlow = remember(viewModel.liveData) {
        viewModel.liveData.map { getMetricFromMap(it, "010C") }.distinctUntilChanged()
    }
    val speedFlow = remember(viewModel.liveData) {
        viewModel.liveData.map { getMetricFromMap(it, "010D") }.distinctUntilChanged()
    }
    val rpm by rpmFlow.collectAsState(initial = 0f)
    val speed by speedFlow.collectAsState(initial = 0f)
    val infiniteTransition = rememberInfiniteTransition(label = "bannerAnim")
    val sweepPhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "sweep"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    val animRpm by animateFloatAsState(
        targetValue = rpm,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 200f),
        label = "animRpm"
    )
    val animSpeed by animateFloatAsState(
        targetValue = speed,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 200f),
        label = "animSpeed"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = MeetColors.neonGreen.copy(alpha = 0.2f),
                spotColor = MeetColors.electricBlue.copy(alpha = 0.3f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF0A1A0A).copy(alpha = 0.95f),
                        Color(0xFF070F1E).copy(alpha = 0.95f)
                    )
                )
            )
            .drawBehind {
                // Animated sweep gradient border
                drawRoundRect(
                    brush = Brush.sweepGradient(
                        sweepPhase to MeetColors.neonGreen.copy(alpha = 0.6f),
                        (sweepPhase + 0.3f) % 1f to MeetColors.electricBlue.copy(alpha = 0.4f),
                        (sweepPhase + 0.7f) % 1f to Color.Transparent,
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 1.5f)
                )
                // Top glow
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to MeetColors.neonGreen.copy(alpha = 0.12f),
                        0.5f to Color.Transparent
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // RPM column
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    "RPM",
                    color = MeetColors.neonGreen.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Text(
                    "${animRpm.toInt()}",
                    color = MeetColors.neonGreen,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-1).sp
                )
            }

            // Divider bar
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(48.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                MeetColors.neonGreen.copy(alpha = glowAlpha),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Speed column
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "VELOCIDAD",
                    color = MeetColors.electricBlue.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${animSpeed.toInt()}",
                        color = MeetColors.electricBlue,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-1).sp
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "km/h",
                        color = MeetColors.electricBlue.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  HOLO GAUGE CARD — Circular arc gauge with 3D depth and glow
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun HoloGaugeCard(
    viewModel: ObdViewModel,
    pidKey: String,
    label: String,
    maxVal: Float,
    unit: String,
    glowColor: Color,
    icon: String
) {
    val valueFlow = remember(viewModel.liveData, pidKey) {
        viewModel.liveData.map { getMetricFromMap(it, pidKey) }.distinctUntilChanged()
    }
    val value by valueFlow.collectAsState(initial = 0f)
    val animValue by animateFloatAsState(
        targetValue = value.coerceIn(0f, maxVal),
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 180f),
        label = "gaugeVal"
    )
    val progress = (animValue / maxVal).coerceIn(0f, 1f)

    val infiniteTransition = rememberInfiniteTransition(label = "gaugeHolo")
    val rotX by infiniteTransition.animateFloat(
        initialValue = -2f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "rotX"
    )
    val rotY by infiniteTransition.animateFloat(
        initialValue = -3f, targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(5100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "rotY"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val sweepPhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)),
        label = "sweep"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .graphicsLayer {
                rotationX = rotX
                rotationY = rotY
                cameraDistance = 14f * density
            }
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = glowColor.copy(alpha = 0.25f),
                spotColor = glowColor.copy(alpha = 0.4f)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0C1A0C).copy(alpha = 0.95f),
                        Color(0xFF060E18).copy(alpha = 0.92f)
                    )
                )
            )
            .drawBehind {
                // Animated sweep border
                drawRoundRect(
                    brush = Brush.sweepGradient(
                        sweepPhase to glowColor.copy(alpha = 0.8f * glowPulse),
                        (sweepPhase + 0.25f) % 1f to glowColor.copy(alpha = 0.15f),
                        (sweepPhase + 0.5f) % 1f to Color.Transparent,
                        (sweepPhase + 0.75f) % 1f to glowColor.copy(alpha = 0.15f)
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                    style = Stroke(width = 1.5f)
                )
                // Top radial glow
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to glowColor.copy(alpha = 0.15f * glowPulse),
                        0.4f to Color.Transparent
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx())
                )
                // Corner markers (cyber style)
                val m = 12.dp.toPx(); val p = 3.dp.toPx()
                val w = size.width; val h = size.height
                val mc = glowColor.copy(alpha = 0.5f)
                drawLine(mc, Offset(p, p), Offset(p + m, p), 1.5f)
                drawLine(mc, Offset(p, p), Offset(p, p + m), 1.5f)
                drawLine(mc, Offset(w - p, p), Offset(w - p - m, p), 1.5f)
                drawLine(mc, Offset(w - p, p), Offset(w - p, p + m), 1.5f)
                drawLine(mc, Offset(p, h - p), Offset(p + m, h - p), 1.5f)
                drawLine(mc, Offset(p, h - p), Offset(p, h - p - m), 1.5f)
                drawLine(mc, Offset(w - p, h - p), Offset(w - p - m, h - p), 1.5f)
                drawLine(mc, Offset(w - p, h - p), Offset(w - p, h - p - m), 1.5f)
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon + label header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    color = glowColor.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }
            Spacer(Modifier.height(8.dp))

            // Arc Gauge Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val radius = (size.minDimension / 2f) * 0.82f
                    val stroke = 10.dp.toPx()
                    val startAngle = 135f
                    val sweepAngle = 270f

                    // Outer ambient glow ring
                    drawArc(
                        color = glowColor.copy(alpha = 0.07f * glowPulse),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = stroke * 3.5f, cap = StrokeCap.Round),
                        topLeft = Offset(cx - radius, cy - radius),
                        size = Size(radius * 2f, radius * 2f)
                    )
                    // Background track
                    drawArc(
                        color = Color.White.copy(alpha = 0.04f),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                        topLeft = Offset(cx - radius, cy - radius),
                        size = Size(radius * 2f, radius * 2f)
                    )
                    // Value arc glow (wide)
                    drawArc(
                        brush = Brush.sweepGradient(
                            0f to Color.Transparent,
                            progress * 0.7f to glowColor.copy(alpha = 0.3f),
                            progress to glowColor.copy(alpha = 0.7f)
                        ),
                        startAngle = startAngle,
                        sweepAngle = progress * sweepAngle,
                        useCenter = false,
                        style = Stroke(width = stroke * 2.5f, cap = StrokeCap.Round),
                        topLeft = Offset(cx - radius, cy - radius),
                        size = Size(radius * 2f, radius * 2f)
                    )
                    // Value arc core
                    drawArc(
                        color = glowColor,
                        startAngle = startAngle,
                        sweepAngle = progress * sweepAngle,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                        topLeft = Offset(cx - radius, cy - radius),
                        size = Size(radius * 2f, radius * 2f)
                    )

                    // Needle tip dot
                    val needleAngleDeg = startAngle + progress * sweepAngle
                    val needleAngleRad = Math.toRadians(needleAngleDeg.toDouble())
                    val needleTip = Offset(
                        (cx + radius * cos(needleAngleRad)).toFloat(),
                        (cy + radius * sin(needleAngleRad)).toFloat()
                    )
                    drawCircle(
                        color = glowColor,
                        radius = 6.dp.toPx(),
                        center = needleTip
                    )
                    drawCircle(
                        color = glowColor.copy(alpha = 0.3f),
                        radius = 10.dp.toPx(),
                        center = needleTip
                    )

                    // Center hub
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.15f), Color.Transparent),
                            center = Offset(cx, cy), radius = 20.dp.toPx()
                        ),
                        radius = 20.dp.toPx(), center = Offset(cx, cy)
                    )
                    drawCircle(color = glowColor.copy(alpha = 0.8f), radius = 4.dp.toPx(), center = Offset(cx, cy))
                    drawCircle(color = Color.Black, radius = 2.5f.dp.toPx(), center = Offset(cx, cy))
                }

                // Digital value overlay
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${animValue.toInt()}",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        unit,
                        color = glowColor.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  HOLO METRIC CARD — Compact sensor card with linear bar + glow
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun HoloMetricCard(
    viewModel: ObdViewModel,
    pidKey: String,
    containsKeyword: String? = null,
    label: String,
    unit: String,
    maxVal: Float,
    warningAt: Float,
    glowColor: Color,
    icon: String
) {
    val valueFlow = remember(viewModel.liveData, pidKey, containsKeyword) {
        viewModel.liveData.map { getMetricFromMap(it, pidKey, containsKeyword) }.distinctUntilChanged()
    }
    val value by valueFlow.collectAsState(initial = 0f)
    val animValue by animateFloatAsState(
        targetValue = value.coerceIn(0f, maxVal),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 90f),
        label = "metricAnim"
    )
    val progress = (animValue / maxVal).coerceIn(0f, 1f)
    val isWarning = value >= warningAt
    val activeColor = if (isWarning) MeetColors.error else glowColor

    val infiniteTransition = rememberInfiniteTransition(label = "metricHolo")
    val rotX by infiniteTransition.animateFloat(
        initialValue = -1.5f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(4500 + (label.length * 200), easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "mRotX"
    )
    val rotY by infiniteTransition.animateFloat(
        initialValue = -2f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(5200 + (label.length * 150), easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "mRotY"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = if (isWarning) 1f else 0.65f,
        animationSpec = infiniteRepeatable(
            tween(if (isWarning) 600 else 2000, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "mGlow"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .graphicsLayer {
                rotationX = rotX
                rotationY = rotY
                cameraDistance = 12f * density
            }
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = activeColor.copy(alpha = 0.2f * glowPulse),
                spotColor = activeColor.copy(alpha = 0.35f * glowPulse)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        MeetColors.backgroundDark.copy(alpha = 0.9f),
                        MeetColors.backgroundDeep.copy(alpha = 0.95f)
                    )
                )
            )
            .drawBehind {
                val cr = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
                // Animated border
                drawRoundRect(
                    color = activeColor.copy(alpha = 0.4f * glowPulse),
                    cornerRadius = cr,
                    style = Stroke(width = 1.2f)
                )
                // Top glow band
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to activeColor.copy(alpha = 0.1f * glowPulse),
                        0.35f to Color.Transparent
                    ),
                    cornerRadius = cr
                )
                // Corner marks
                val m = 10.dp.toPx(); val p = 2.dp.toPx()
                val mc = activeColor.copy(alpha = 0.5f)
                val w = size.width; val h = size.height
                drawLine(mc, Offset(p, p), Offset(p + m, p), 1.2f)
                drawLine(mc, Offset(p, p), Offset(p, p + m), 1.2f)
                drawLine(mc, Offset(w - p, p), Offset(w - p - m, p), 1.2f)
                drawLine(mc, Offset(w - p, p), Offset(w - p, p + m), 1.2f)
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 12.sp)
                Spacer(Modifier.width(5.dp))
                Text(
                    label,
                    color = activeColor.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
            }

            // Value
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${animValue.toInt()}",
                    color = if (isWarning) MeetColors.error else Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    unit,
                    color = activeColor.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // Progress bar with glow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.05f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(3.dp),
                            ambientColor = activeColor.copy(alpha = 0.4f),
                            spotColor = activeColor
                        )
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    activeColor.copy(alpha = 0.5f),
                                    activeColor
                                )
                            )
                        )
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  HOLO WAVE PANEL — Full-width waveform visualization
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun HoloWavePanel(
    viewModel: ObdViewModel
) {
    val rpmFlow = remember(viewModel.liveData) {
        viewModel.liveData.map { getMetricFromMap(it, "010C") }.distinctUntilChanged()
    }
    val mafFlow = remember(viewModel.liveData) {
        viewModel.liveData.map { getMetricFromMap(it, "0110") }.distinctUntilChanged()
    }
    val loadFlow = remember(viewModel.liveData) {
        viewModel.liveData.map { getMetricFromMap(it, "0104") }.distinctUntilChanged()
    }
    val rpmValue by rpmFlow.collectAsState(initial = 0f)
    val mafValue by mafFlow.collectAsState(initial = 0f)
    val loadValue by loadFlow.collectAsState(initial = 0f)
    // Accumulate history
    val historySize = 60
    val rpmHistory = remember { mutableStateListOf<Float>().also { l -> repeat(historySize) { l.add(0f) } } }
    val mafHistory = remember { mutableStateListOf<Float>().also { l -> repeat(historySize) { l.add(0f) } } }
    val loadHistory = remember { mutableStateListOf<Float>().also { l -> repeat(historySize) { l.add(0f) } } }

    val currentRpm by rememberUpdatedState(rpmValue)
    val currentMaf by rememberUpdatedState(mafValue)
    val currentLoad by rememberUpdatedState(loadValue)

    LaunchedEffect(Unit) {
        while (true) {
            if (rpmHistory.size >= historySize) rpmHistory.removeAt(0)
            if (mafHistory.size >= historySize) mafHistory.removeAt(0)
            if (loadHistory.size >= historySize) loadHistory.removeAt(0)
            rpmHistory.add(currentRpm)
            mafHistory.add(currentMaf)
            loadHistory.add(currentLoad)
            delay(200L)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "waveHolo")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wGlow"
    )
    val scanX by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "scan"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = MeetColors.cyberCyan.copy(alpha = 0.15f),
                spotColor = MeetColors.cyberCyan.copy(alpha = 0.25f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF060E18).copy(alpha = 0.97f))
            .drawBehind {
                drawRoundRect(
                    color = MeetColors.cyberCyan.copy(alpha = 0.3f * glowPulse),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 1.2f)
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to MeetColors.cyberCyan.copy(alpha = 0.08f * glowPulse),
                        0.4f to Color.Transparent
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "TELEMETRÍA EN VIVO",
                    color = MeetColors.cyberCyan.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WaveLegend("RPM", MeetColors.neonGreen)
                    WaveLegend("MAF", MeetColors.electricBlue)
                    WaveLegend("CARGA", MeetColors.warning)
                }
            }

            // Waveform canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                val w = size.width
                val h = size.height

                // Grid lines
                val cols = 6
                repeat(cols + 1) { i ->
                    val x = w * i.toFloat() / cols
                    drawLine(
                        color = MeetColors.cyberCyan.copy(alpha = 0.06f),
                        start = Offset(x, 0f), end = Offset(x, h),
                        strokeWidth = 0.5f
                    )
                }
                val rows = 3
                repeat(rows + 1) { i ->
                    val y = h * i.toFloat() / rows
                    drawLine(
                        color = MeetColors.cyberCyan.copy(alpha = 0.06f),
                        start = Offset(0f, y), end = Offset(w, y),
                        strokeWidth = 0.5f
                    )
                }

                // Draw waveform helper
                fun drawWave(history: List<Float>, maxVal: Float, color: Color) {
                    if (history.size < 2) return
                    val path = Path()
                    history.forEachIndexed { i, v ->
                        val x = w * i.toFloat() / (history.size - 1)
                        val y = h - (h * (v / maxVal).coerceIn(0f, 1f))
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    // Glow
                    drawPath(path, color.copy(alpha = 0.15f), style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
                    // Core
                    drawPath(path, color, style = Stroke(width = 1.5f.dp.toPx(), cap = StrokeCap.Round))
                }

                drawWave(rpmHistory.toList(), 8000f, MeetColors.neonGreen)
                drawWave(mafHistory.toList(), 200f, MeetColors.electricBlue)
                drawWave(loadHistory.toList(), 100f, MeetColors.warning)

                // Animated vertical scan line
                val scanLineX = w * scanX
                drawLine(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, MeetColors.cyberCyan.copy(alpha = 0.4f), Color.Transparent)
                    ),
                    start = Offset(scanLineX, 0f),
                    end = Offset(scanLineX, h),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }
}

@Composable
private fun WaveLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(12.dp)
                .height(2.dp)
                .background(color, RoundedCornerShape(1.dp))
        )
        Spacer(Modifier.width(3.dp))
        Text(label, color = color.copy(alpha = 0.7f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  BACKGROUND: Deep holographic particle system
// ════════════════════════════════════════════════════════════════════════════

private data class LocalReadParticle(
    val xRatio: Float,
    val yRatio: Float,
    val speed: Float,
    val size: Float,
    val alpha: Float,
    val drift: Float,
    val colorIndex: Int
)

private val localReadParticles = List(50) { i ->
    LocalReadParticle(
        xRatio = (i * 0.193f) % 1f,
        yRatio = (i * 0.277f) % 1f,
        speed = 0.008f + (i * 0.003f) % 0.02f,
        size = 0.8f + (i % 4) * 0.6f,
        alpha = 0.04f + (i % 5) * 0.025f,
        drift = -0.04f + (i * 0.025f) % 0.08f,
        colorIndex = i % 3
    )
}

@Composable
private fun HoloLocalBackground() {
    val colors = listOf(MeetColors.neonGreen, MeetColors.electricBlue, MeetColors.cyberCyan)
    val infiniteTransition = rememberInfiniteTransition(label = "localBg")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing)),
        label = "bgPhase"
    )
    val glow by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bgGlow"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Ambient orbs
        listOf(
            Triple(Offset(w * (0.2f + phase * 0.08f), h * 0.15f), MeetColors.neonGreen, w * 0.55f),
            Triple(Offset(w * (0.8f - phase * 0.06f), h * 0.65f), MeetColors.electricBlue, w * 0.45f),
            Triple(Offset(w * (0.5f + sin(phase * 2 * PI.toFloat()) * 0.1f), h * 0.4f), MeetColors.cyberCyan, w * 0.35f)
        ).forEach { (center, color, radius) ->
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.035f * glow), Color.Transparent),
                    center = center, radius = radius
                ),
                radius = radius, center = center
            )
        }

        // Grid
        val gridSpacing = 40.dp.toPx()
        val gridAlpha = 0.012f
        var y = 0f
        while (y < h) {
            drawLine(MeetColors.neonGreen.copy(alpha = gridAlpha), Offset(0f, y), Offset(w, y), 0.5f)
            y += gridSpacing
        }
        var x = 0f
        while (x < w) {
            drawLine(MeetColors.neonGreen.copy(alpha = gridAlpha), Offset(x, 0f), Offset(x, h), 0.5f)
            x += gridSpacing
        }

        // Particles
        localReadParticles.forEach { p ->
            val px = ((p.xRatio * w) + phase * p.speed * w + p.drift * w * sin(phase * 2 * PI.toFloat()).toFloat()) % w
            val py = ((p.yRatio * h) - phase * p.speed * h) % h
            val fx = if (px < 0) px + w else px
            val fy = if (py < 0) py + h else py
            drawCircle(
                color = colors[p.colorIndex].copy(alpha = p.alpha * glow * 1.4f),
                radius = p.size.dp.toPx(),
                center = Offset(fx, fy)
            )
        }

        // Horizontal scan line
        val scanY = h * phase
        drawLine(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.2f to MeetColors.cyberCyan.copy(alpha = 0.05f),
                0.5f to MeetColors.cyberCyan.copy(alpha = 0.12f),
                0.8f to MeetColors.cyberCyan.copy(alpha = 0.05f),
                1f to Color.Transparent
            ),
            start = Offset(0f, scanY), end = Offset(w, scanY),
            strokeWidth = 1.5f
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  AMBIENT GLOW BLOBS (blurred, layer behind content)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun AmbientGlowBlobs() {
    val infiniteTransition = rememberInfiniteTransition(label = "blobs")
    val blobPhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "blobPhase"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .blur(80.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val s = sin(blobPhase * 2 * PI.toFloat()).toFloat()
            val c = cos(blobPhase * 2 * PI.toFloat()).toFloat()

            drawCircle(
                brush = Brush.radialGradient(
                    listOf(MeetColors.neonGreen.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(w * (0.3f + s * 0.05f), h * 0.2f),
                    radius = w * 0.4f
                ),
                radius = w * 0.4f,
                center = Offset(w * (0.3f + s * 0.05f), h * 0.2f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(MeetColors.electricBlue.copy(alpha = 0.06f), Color.Transparent),
                    center = Offset(w * (0.7f + c * 0.04f), h * 0.7f),
                    radius = w * 0.35f
                ),
                radius = w * 0.35f,
                center = Offset(w * (0.7f + c * 0.04f), h * 0.7f)
            )
        }
    }
}
