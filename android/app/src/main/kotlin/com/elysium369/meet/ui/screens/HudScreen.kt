package com.elysium369.meet.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.components.hud.HudData
import com.elysium369.meet.ui.components.hud.HudFaceManager
import com.elysium369.meet.ui.components.hud.HudFaceRenderer
import com.elysium369.meet.ui.components.hud.HudFaceSelector
import com.elysium369.meet.ui.theme.MeetColors
import kotlin.math.*

// ════════════════════════════════════════════════════════════════════════════
//  HUD SCREEN — PREMIUM WINDSHIELD HOLOGRAPHIC PROJECTION
//  Pure black background with neon green/cyan holographic elements
//  Mirrors horizontally for windshield reflection
// ════════════════════════════════════════════════════════════════════════════

// ── Particle data class for background system ──
private data class HudParticle(
    val xRatio: Float,
    val yRatio: Float,
    val speed: Float,
    val size: Float,
    val alpha: Float,
    val drift: Float,
    val colorIndex: Int
)

private val hudParticles = List(45) { i ->
    HudParticle(
        xRatio = (i * 0.197f) % 1f,
        yRatio = (i * 0.283f) % 1f,
        speed = 0.006f + (i * 0.004f) % 0.018f,
        size = 0.6f + (i % 4) * 0.5f,
        alpha = 0.03f + (i % 5) * 0.02f,
        drift = -0.03f + (i * 0.022f) % 0.06f,
        colorIndex = i % 3
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HudScreen(
    navController: NavController,
    viewModel: ObdViewModel
) {
    val liveData by viewModel.liveData.collectAsState()
    val obdState by viewModel.connectionState.collectAsState()
    val activeDtcs by viewModel.activeDtcs.collectAsState()

    // HUD Face manager
    val context = LocalContext.current
    val hudFaceManager = remember { HudFaceManager(context) }
    val currentFace by hudFaceManager.currentFace.collectAsState()

    // States
    var isMirrored by remember { mutableStateOf(true) }

    // Fetch live parameters
    val rpm = liveData["010C"]?.toInt() ?: 0
    val speed = liveData["010D"]?.toInt() ?: 0
    val coolantTemp = liveData["0105"]?.toInt() ?: 0
    val throttle = liveData["0111"]?.toInt() ?: 0
    val engineLoad = liveData["0104"]?.toInt() ?: 0
    val voltage = liveData["0142"] ?: liveData["42"] ?: 12.4f
    val fuelLevel = liveData["012F"] ?: 0f
    val intakeTemp = liveData["010F"] ?: 0f

    // Build HudData for face renderer
    val hudData = HudData(
        speed = speed.toFloat(),
        rpm = rpm.toFloat(),
        coolantTemp = coolantTemp.toFloat(),
        throttle = throttle.toFloat(),
        engineLoad = engineLoad.toFloat(),
        voltage = voltage,
        fuelLevel = fuelLevel,
        intakeTemp = intakeTemp
    )

    // Pulsing warning if DTCs are present
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val warningAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "warning"
    )

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            // HUD controls are rendered normal (not mirrored) so the driver can easily toggle options
            Column(modifier = Modifier.background(Color.Black)) {
                EliteTopAppBar(
                    title = "HUD REFLEJO",
                    subtitle = "Proyección para Parabrisas",
                    onBackClick = { navController.popBackStack() },
                    backgroundColor = Color.Black
                )
                HudMirrorToggleBar(isMirrored = isMirrored, onToggle = { isMirrored = !isMirrored })
                // ── HUD Face Selector ──
                HudFaceSelector(
                    currentFace = currentFace,
                    onFaceSelected = { hudFaceManager.selectFace(it) }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            // ── Layer 1: Holographic background particles ──
            HudBackground()

            // ── Layer 2: Deep ambient glow blobs ──
            HudAmbientGlowBlobs()

            // ── Layer 3: Content (mirrored) ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = if (isMirrored) -1f else 1f
                    )
            ) {
                // ── DTC Status overlay at top ──
                HudStatusBar(
                    obdState = obdState,
                    activeDtcs = activeDtcs,
                    warningAlpha = warningAlpha
                )

                // ── Selected HUD Face Layout fills the screen ──
                HudFaceRenderer(
                    face = currentFace,
                    data = hudData,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  MIRROR TOGGLE BAR — Holographic styled toggle
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun HudMirrorToggleBar(isMirrored: Boolean, onToggle: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "toggleGlow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "togglePulse"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // Bottom glow line
                drawLine(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.3f to MeetColors.neonGreen.copy(alpha = 0.4f * glowPulse),
                        0.7f to MeetColors.cyberCyan.copy(alpha = 0.3f * glowPulse),
                        1f to Color.Transparent
                    ),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.5f
                )
            }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Pulsing status dot
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            if (isMirrored) MeetColors.neonGreen.copy(alpha = glowPulse)
                            else MeetColors.textMuted.copy(alpha = 0.4f),
                            CircleShape
                        )
                        .then(
                            if (isMirrored) Modifier.shadow(4.dp, CircleShape, ambientColor = MeetColors.neonGreen)
                            else Modifier
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isMirrored) "MODO ESPEJO: ACTIVADO" else "MODO ESPEJO: DESACTIVADO",
                    color = if (isMirrored) MeetColors.neonGreen else MeetColors.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp
                )
            }

            Box(
                modifier = Modifier
                    .shadow(
                        elevation = if (isMirrored) 8.dp else 2.dp,
                        shape = RoundedCornerShape(8.dp),
                        ambientColor = if (isMirrored) MeetColors.neonGreen.copy(alpha = 0.3f) else Color.Transparent,
                        spotColor = if (isMirrored) MeetColors.neonGreen.copy(alpha = 0.4f) else Color.Transparent
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isMirrored) MeetColors.neonGreen.copy(alpha = 0.15f)
                        else Color.White.copy(alpha = 0.05f)
                    )
                    .border(
                        1.dp,
                        if (isMirrored) MeetColors.neonGreen.copy(alpha = 0.6f)
                        else Color.White.copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onToggle() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    "REFLEJAR SCREEN",
                    color = if (isMirrored) MeetColors.neonGreen else Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  STATUS BAR — Holographic top info with glow indicators
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun HudStatusBar(
    obdState: ObdState,
    activeDtcs: List<String>,
    warningAlpha: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "statusGlow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "statusPulse"
    )
    val sweepPhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "statusSweep"
    )

    val statusColor = when (obdState) {
        ObdState.CONNECTED -> MeetColors.neonGreen
        ObdState.CONNECTING, ObdState.NEGOTIATING -> MeetColors.warning
        else -> MeetColors.error
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(14.dp),
                ambientColor = statusColor.copy(alpha = 0.15f),
                spotColor = statusColor.copy(alpha = 0.2f)
            )
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF050505).copy(alpha = 0.85f))
            .drawBehind {
                val cr = CornerRadius(14.dp.toPx())
                // Animated sweep border
                drawRoundRect(
                    brush = Brush.sweepGradient(
                        sweepPhase to statusColor.copy(alpha = 0.6f * glowPulse),
                        (sweepPhase + 0.25f) % 1f to MeetColors.cyberCyan.copy(alpha = 0.2f),
                        (sweepPhase + 0.5f) % 1f to Color.Transparent,
                        (sweepPhase + 0.75f) % 1f to statusColor.copy(alpha = 0.15f)
                    ),
                    cornerRadius = cr,
                    style = Stroke(width = 1.2f)
                )
                // Top glow band
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to statusColor.copy(alpha = 0.08f * glowPulse),
                        0.5f to Color.Transparent
                    ),
                    cornerRadius = cr
                )
                // Bottom pulsing glow line
                drawLine(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.2f to statusColor.copy(alpha = 0.3f * glowPulse),
                        0.5f to statusColor.copy(alpha = 0.6f * glowPulse),
                        0.8f to statusColor.copy(alpha = 0.3f * glowPulse),
                        1f to Color.Transparent
                    ),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.5f
                )
                // Corner markers
                val m = 10.dp.toPx(); val p = 3.dp.toPx()
                val mc = statusColor.copy(alpha = 0.5f)
                val w = size.width; val h = size.height
                drawLine(mc, Offset(p, p), Offset(p + m, p), 1.2f)
                drawLine(mc, Offset(p, p), Offset(p, p + m), 1.2f)
                drawLine(mc, Offset(w - p, p), Offset(w - p - m, p), 1.2f)
                drawLine(mc, Offset(w - p, p), Offset(w - p, p + m), 1.2f)
                drawLine(mc, Offset(p, h - p), Offset(p + m, h - p), 1.2f)
                drawLine(mc, Offset(p, h - p), Offset(p, h - p - m), 1.2f)
                drawLine(mc, Offset(w - p, h - p), Offset(w - p - m, h - p), 1.2f)
                drawLine(mc, Offset(w - p, h - p), Offset(w - p, h - p - m), 1.2f)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // OBD Status
            Column {
                Text(
                    text = "OBD STATUS",
                    color = MeetColors.textMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Glow dot indicator
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(statusColor.copy(alpha = glowPulse), CircleShape)
                            .shadow(4.dp, CircleShape, ambientColor = statusColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when (obdState) {
                            ObdState.CONNECTED -> "ONLINE"
                            ObdState.CONNECTING -> "LINKING..."
                            ObdState.NEGOTIATING -> "NEGOTIATING..."
                            else -> "OFFLINE"
                        },
                        color = statusColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // DTC Alert or Systems OK
            if (activeDtcs.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(8.dp),
                            ambientColor = MeetColors.error.copy(alpha = 0.3f * warningAlpha),
                            spotColor = MeetColors.error.copy(alpha = 0.4f * warningAlpha)
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(MeetColors.error.copy(alpha = warningAlpha * 0.2f))
                        .border(1.dp, MeetColors.error.copy(alpha = warningAlpha * 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "⚠ DTC ALERT: ${activeDtcs.size}",
                        color = MeetColors.error,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(MeetColors.neonGreen.copy(alpha = 0.5f * glowPulse), CircleShape)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "SYSTEMS OK",
                        color = MeetColors.neonGreen.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  CENTER GAUGE — Premium holographic RPM arc with glow layers
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun HudCenterGauge(
    animatedSpeed: Float,
    animatedRpm: Float,
    rpm: Int,
    modifier: Modifier = Modifier
) {
    val maxRpm = 8000f
    val progress = (animatedRpm / maxRpm).coerceIn(0f, 1f)

    val infiniteTransition = rememberInfiniteTransition(label = "gaugeHolo")
    val rotX by infiniteTransition.animateFloat(
        initialValue = -1.5f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(5200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "gRotX"
    )
    val rotY by infiniteTransition.animateFloat(
        initialValue = -2f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(6100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "gRotY"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "gGlow"
    )
    val sweepPhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)),
        label = "gSweep"
    )

    val glowColor = MeetColors.neonGreen

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer {
                rotationX = rotX
                rotationY = rotY
                cameraDistance = 16f * density
            },
        contentAlignment = Alignment.Center
    ) {
        // Outer container with glow and sweep border
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 24.dp,
                    shape = CircleShape,
                    ambientColor = glowColor.copy(alpha = 0.15f),
                    spotColor = glowColor.copy(alpha = 0.25f)
                )
                .drawBehind {
                    // Animated sweep border ring
                    drawCircle(
                        brush = Brush.sweepGradient(
                            sweepPhase to glowColor.copy(alpha = 0.6f * glowPulse),
                            (sweepPhase + 0.25f) % 1f to MeetColors.cyberCyan.copy(alpha = 0.2f),
                            (sweepPhase + 0.5f) % 1f to Color.Transparent,
                            (sweepPhase + 0.75f) % 1f to glowColor.copy(alpha = 0.15f)
                        ),
                        radius = size.minDimension / 2f,
                        style = Stroke(width = 1.5f)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Arc gauge canvas
            Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = (size.minDimension / 2f) * 0.85f
                val stroke = 12.dp.toPx()
                val startAngle = 135f
                val totalSweep = 270f

                // ── Outer ambient glow ring ──
                drawArc(
                    color = glowColor.copy(alpha = 0.06f * glowPulse),
                    startAngle = startAngle,
                    sweepAngle = totalSweep,
                    useCenter = false,
                    style = Stroke(width = stroke * 4f, cap = StrokeCap.Round),
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2f, radius * 2f)
                )

                // ── Background track ──
                drawArc(
                    color = Color.White.copy(alpha = 0.04f),
                    startAngle = startAngle,
                    sweepAngle = totalSweep,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2f, radius * 2f)
                )

                // ── Tick marks ──
                val numTicks = 8
                for (i in 0..numTicks) {
                    val tickAngleDeg = startAngle + (i.toFloat() / numTicks) * totalSweep
                    val tickAngleRad = Math.toRadians(tickAngleDeg.toDouble())
                    val innerR = radius - stroke * 0.6f
                    val outerR = radius + stroke * 0.6f
                    val isMajor = i % 2 == 0
                    val tickAlpha = if (isMajor) 0.25f else 0.1f
                    val tickW = if (isMajor) 1.5f else 0.8f
                    drawLine(
                        color = glowColor.copy(alpha = tickAlpha),
                        start = Offset(
                            cx + innerR * cos(tickAngleRad).toFloat(),
                            cy + innerR * sin(tickAngleRad).toFloat()
                        ),
                        end = Offset(
                            cx + outerR * cos(tickAngleRad).toFloat(),
                            cy + outerR * sin(tickAngleRad).toFloat()
                        ),
                        strokeWidth = tickW
                    )
                }

                // ── Value arc glow layer (wide, diffused) ──
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to Color.Transparent,
                        progress * 0.6f to glowColor.copy(alpha = 0.25f),
                        progress to glowColor.copy(alpha = 0.6f)
                    ),
                    startAngle = startAngle,
                    sweepAngle = progress * totalSweep,
                    useCenter = false,
                    style = Stroke(width = stroke * 2.8f, cap = StrokeCap.Round),
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2f, radius * 2f)
                )

                // ── Value arc core layer ──
                val arcGradient = Brush.sweepGradient(
                    0.0f to MeetColors.neonGreen,
                    0.5f to MeetColors.cyberCyan,
                    0.8f to MeetColors.electricBlue,
                    1.0f to MeetColors.error
                )
                drawArc(
                    brush = arcGradient,
                    startAngle = startAngle,
                    sweepAngle = progress * totalSweep,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2f, radius * 2f)
                )

                // ── Redline indicator ──
                val redlineProgress = 6000f / maxRpm
                val redlineAngle = startAngle + redlineProgress * totalSweep
                val redlineRad = Math.toRadians(redlineAngle.toDouble())
                drawLine(
                    color = MeetColors.error.copy(alpha = 0.8f),
                    start = Offset(
                        cx + (radius - stroke) * cos(redlineRad).toFloat(),
                        cy + (radius - stroke) * sin(redlineRad).toFloat()
                    ),
                    end = Offset(
                        cx + (radius + stroke) * cos(redlineRad).toFloat(),
                        cy + (radius + stroke) * sin(redlineRad).toFloat()
                    ),
                    strokeWidth = 2.5f
                )

                // ── Needle tip dot with glow halo ──
                val needleAngleDeg = startAngle + progress * totalSweep
                val needleAngleRad = Math.toRadians(needleAngleDeg.toDouble())
                val needleTip = Offset(
                    (cx + radius * cos(needleAngleRad)).toFloat(),
                    (cy + radius * sin(needleAngleRad)).toFloat()
                )
                // Outer glow halo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = 0.4f * glowPulse),
                            Color.Transparent
                        ),
                        center = needleTip,
                        radius = 16.dp.toPx()
                    ),
                    radius = 16.dp.toPx(),
                    center = needleTip
                )
                // Mid glow ring
                drawCircle(
                    color = glowColor.copy(alpha = 0.35f),
                    radius = 8.dp.toPx(),
                    center = needleTip
                )
                // Core dot
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = needleTip
                )

                // ── Center hub ──
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            Color.White.copy(alpha = 0.12f),
                            glowColor.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = 28.dp.toPx()
                    ),
                    radius = 28.dp.toPx(),
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = glowColor.copy(alpha = 0.7f),
                    radius = 4.dp.toPx(),
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = Color.Black,
                    radius = 2.5f.dp.toPx(),
                    center = Offset(cx, cy)
                )
            }

            // Digital value overlay
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = animatedSpeed.toInt().toString(),
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = MeetColors.neonGreen,
                    letterSpacing = (-2).sp
                )
                Text(
                    text = "km/h",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MeetColors.cyberCyan.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(
                                if (rpm > 6000) MeetColors.error.copy(alpha = glowPulse)
                                else MeetColors.cyberCyan.copy(alpha = 0.4f),
                                CircleShape
                            )
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "RPM ${animatedRpm.toInt()}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = if (rpm > 6000) MeetColors.error else MeetColors.textSecondary,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  TELEMETRY PANEL — Glassmorphic metric cards with glow borders
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun HudTelemetryPanel(
    coolantTemp: Int,
    engineLoad: Int,
    throttle: Int,
    voltage: Float
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val tempColor = when {
            coolantTemp > 105 -> MeetColors.error
            coolantTemp > 95 -> MeetColors.warning
            else -> MeetColors.cyberCyan
        }
        HudTelemetryCard(
            label = "TEMP",
            value = "$coolantTemp°C",
            progress = (coolantTemp / 130f).coerceIn(0f, 1f),
            glowColor = tempColor,
            modifier = Modifier.weight(1f)
        )
        HudTelemetryCard(
            label = "LOAD",
            value = "$engineLoad%",
            progress = (engineLoad / 100f).coerceIn(0f, 1f),
            glowColor = MeetColors.neonGreen,
            modifier = Modifier.weight(1f)
        )
        HudTelemetryCard(
            label = "TPS",
            value = "$throttle%",
            progress = (throttle / 100f).coerceIn(0f, 1f),
            glowColor = MeetColors.electricBlue,
            modifier = Modifier.weight(1f)
        )
        HudTelemetryCard(
            label = "VOLT",
            value = String.format("%.1fV", voltage),
            progress = (voltage / 16f).coerceIn(0f, 1f),
            glowColor = if (voltage < 11.8f) MeetColors.error else MeetColors.warning,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HudTelemetryCard(
    label: String,
    value: String,
    progress: Float,
    glowColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "telemetry_$label")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            tween(2200 + (label.length * 200), easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "tGlow_$label"
    )
    val animProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 60f),
        label = "tProg_$label"
    )

    Box(
        modifier = modifier
            .height(90.dp)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(12.dp),
                ambientColor = glowColor.copy(alpha = 0.15f * glowPulse),
                spotColor = glowColor.copy(alpha = 0.25f * glowPulse)
            )
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF050505).copy(alpha = 0.85f))
            .drawBehind {
                val cr = CornerRadius(12.dp.toPx())
                // Glow border
                drawRoundRect(
                    color = glowColor.copy(alpha = 0.4f * glowPulse),
                    cornerRadius = cr,
                    style = Stroke(width = 1.2f)
                )
                // Top glow band
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to glowColor.copy(alpha = 0.08f * glowPulse),
                        0.4f to Color.Transparent
                    ),
                    cornerRadius = cr
                )
                // Corner bracket marks
                val m = 8.dp.toPx(); val p = 2.dp.toPx()
                val mc = glowColor.copy(alpha = 0.5f)
                val w = size.width; val h = size.height
                drawLine(mc, Offset(p, p), Offset(p + m, p), 1f)
                drawLine(mc, Offset(p, p), Offset(p, p + m), 1f)
                drawLine(mc, Offset(w - p, p), Offset(w - p - m, p), 1f)
                drawLine(mc, Offset(w - p, p), Offset(w - p, p + m), 1f)
                drawLine(mc, Offset(p, h - p), Offset(p + m, h - p), 1f)
                drawLine(mc, Offset(p, h - p), Offset(p, h - p - m), 1f)
                drawLine(mc, Offset(w - p, h - p), Offset(w - p - m, h - p), 1f)
                drawLine(mc, Offset(w - p, h - p), Offset(w - p, h - p - m), 1f)
            }
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Label
            Text(
                text = label,
                color = glowColor.copy(alpha = 0.6f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp
            )

            // Value
            Text(
                text = value,
                color = glowColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )

            // Progress bar with glow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.04f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(2.dp),
                            ambientColor = glowColor.copy(alpha = 0.4f),
                            spotColor = glowColor
                        )
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    glowColor.copy(alpha = 0.4f),
                                    glowColor
                                )
                            )
                        )
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  HUD BACKGROUND — Deep holographic particle system (green-dominant)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun HudBackground() {
    val colors = listOf(MeetColors.neonGreen, MeetColors.cyberCyan, MeetColors.neonGreen)
    val infiniteTransition = rememberInfiniteTransition(label = "hudBg")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing)),
        label = "hudBgPhase"
    )
    val glow by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hudBgGlow"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // ── Ambient orbs (very subtle on black) ──
        listOf(
            Triple(Offset(w * (0.2f + phase * 0.06f), h * 0.12f), MeetColors.neonGreen, w * 0.5f),
            Triple(Offset(w * (0.8f - phase * 0.05f), h * 0.7f), MeetColors.cyberCyan, w * 0.4f),
            Triple(Offset(w * (0.5f + sin(phase * 2 * PI.toFloat()) * 0.08f), h * 0.4f), MeetColors.neonGreen, w * 0.35f)
        ).forEach { (center, color, radius) ->
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.025f * glow), Color.Transparent),
                    center = center, radius = radius
                ),
                radius = radius, center = center
            )
        }

        // ── Grid (very faint, green-tinted) ──
        val gridSpacing = 45.dp.toPx()
        val gridAlpha = 0.008f
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

        // ── Particles ──
        hudParticles.forEach { p ->
            val px = ((p.xRatio * w) + phase * p.speed * w + p.drift * w * sin(phase * 2 * PI.toFloat()).toFloat()) % w
            val py = ((p.yRatio * h) - phase * p.speed * h) % h
            val fx = if (px < 0) px + w else px
            val fy = if (py < 0) py + h else py
            drawCircle(
                color = colors[p.colorIndex].copy(alpha = p.alpha * glow * 1.2f),
                radius = p.size.dp.toPx(),
                center = Offset(fx, fy)
            )
        }

        // ── Horizontal scan line ──
        val scanY = h * phase
        drawLine(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.2f to MeetColors.neonGreen.copy(alpha = 0.04f),
                0.5f to MeetColors.neonGreen.copy(alpha = 0.10f),
                0.8f to MeetColors.neonGreen.copy(alpha = 0.04f),
                1f to Color.Transparent
            ),
            start = Offset(0f, scanY),
            end = Offset(w, scanY),
            strokeWidth = 1.5f
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  HUD AMBIENT GLOW BLOBS — Blurred, behind content
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun HudAmbientGlowBlobs() {
    val infiniteTransition = rememberInfiniteTransition(label = "hudBlobs")
    val blobPhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing)),
        label = "hudBlobPhase"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .blur(90.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val s = sin(blobPhase * 2 * PI.toFloat()).toFloat()
            val c = cos(blobPhase * 2 * PI.toFloat()).toFloat()

            drawCircle(
                brush = Brush.radialGradient(
                    listOf(MeetColors.neonGreen.copy(alpha = 0.06f), Color.Transparent),
                    center = Offset(w * (0.3f + s * 0.04f), h * 0.18f),
                    radius = w * 0.35f
                ),
                radius = w * 0.35f,
                center = Offset(w * (0.3f + s * 0.04f), h * 0.18f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(MeetColors.cyberCyan.copy(alpha = 0.04f), Color.Transparent),
                    center = Offset(w * (0.7f + c * 0.03f), h * 0.75f),
                    radius = w * 0.3f
                ),
                radius = w * 0.3f,
                center = Offset(w * (0.7f + c * 0.03f), h * 0.75f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(MeetColors.neonGreen.copy(alpha = 0.03f), Color.Transparent),
                    center = Offset(w * (0.5f + s * 0.05f), h * 0.5f),
                    radius = w * 0.25f
                ),
                radius = w * 0.25f,
                center = Offset(w * (0.5f + s * 0.05f), h * 0.5f)
            )
        }
    }
}
