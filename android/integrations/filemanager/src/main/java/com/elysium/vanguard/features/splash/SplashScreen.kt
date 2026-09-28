package com.elysium.vanguard.features.splash

import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.vanguard.ui.components.MatrixRain
import com.elysium.vanguard.ui.components.TitanLogo
import com.elysium.vanguard.ui.components.TitanLogoStyle
import com.elysium.vanguard.ui.theme.TitanColors
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SplashScreen(onNavigateToDashboard: () -> Unit) {

    // ── PHASE CONTROL ──
    var phase by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        delay(300)   // Initial darkness
        phase = 1     // Matrix rain fades in
        delay(600)
        phase = 2     // Logo scales in
        delay(400)
        phase = 3     // Boot text starts
        delay(1200)
        phase = 4     // App name appears
        delay(800)
        phase = 5     // Flash out
        delay(500)
        onNavigateToDashboard()
    }

    // ── ANIMATIONS ──
    val infiniteTransition = rememberInfiniteTransition(label = "splash")

    val rainAlpha by animateFloatAsState(
        targetValue = if (phase >= 1) 0.5f else 0f,
        animationSpec = tween(800), label = "rain"
    )

    val logoScale by animateFloatAsState(
        targetValue = if (phase >= 2) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 300f),
        label = "logoScale"
    )

    val logoAlpha by animateFloatAsState(
        targetValue = if (phase >= 2) 1f else 0f,
        animationSpec = tween(500), label = "logoAlpha"
    )

    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring"
    )

    val ringAlpha by animateFloatAsState(
        targetValue = if (phase in 2..4) 1f else 0f,
        animationSpec = tween(400), label = "ringAlpha"
    )

    val flashAlpha by animateFloatAsState(
        targetValue = if (phase >= 5) 1f else 0f,
        animationSpec = tween(300), label = "flash"
    )

    // Boot text sequence
    val bootLines = listOf(
        "INITIALIZING NEURAL CORE...",
        "LOADING TITAN MODULES...",
        "CALIBRATING QUANTUM ENGINE...",
        "SYSTEM READY"
    )
    var visibleLines by remember { mutableIntStateOf(0) }

    LaunchedEffect(phase) {
        if (phase >= 3) {
            for (i in 1..bootLines.size) {
                delay(250)
                visibleLines = i
            }
        }
    }

    // App name typewriter
    val appName = "ELYSIUM VANGUARD"
    var visibleChars by remember { mutableIntStateOf(0) }

    LaunchedEffect(phase) {
        if (phase >= 4) {
            for (i in 1..appName.length) {
                delay(40)
                visibleChars = i
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TitanColors.AbsoluteBlack),
        contentAlignment = Alignment.Center
    ) {
        // ── LAYER 1: MATRIX RAIN ──
        if (phase >= 1) {
            MatrixRain(
                color = TitanColors.RadioactiveGreen.copy(alpha = rainAlpha),
                speed = 50L,
                trailLength = 18,
                alpha = rainAlpha * 0.7f
            )
        }

        // ── LAYER 2: NEON PROGRESS RING ──
        Canvas(
            modifier = Modifier
                .size(220.dp)
                .alpha(ringAlpha)
                .graphicsLayer { rotationZ = ringRotation }
        ) {
            val strokeWidth = 3.dp.toPx()
            val radius = size.minDimension / 2f - strokeWidth

            // Arc sweep
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        TitanColors.NeonCyan.copy(alpha = 0f),
                        TitanColors.NeonCyan,
                        TitanColors.QuantumPink,
                        TitanColors.NeonCyan.copy(alpha = 0f)
                    )
                ),
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = Offset(strokeWidth, strokeWidth),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
            )

            // Orbiting dots
            for (i in 0..2) {
                val angle = Math.toRadians((ringRotation.toDouble() + i * 120.0))
                val dotX = center.x + (radius + 8.dp.toPx()) * cos(angle).toFloat()
                val dotY = center.y + (radius + 8.dp.toPx()) * sin(angle).toFloat()
                drawCircle(
                    color = TitanColors.NeonCyan,
                    radius = 3.dp.toPx(),
                    center = Offset(dotX, dotY)
                )
            }
        }

        // ── LAYER 3: LOGO ──
        Box(
            modifier = Modifier
                .scale(logoScale)
                .alpha(logoAlpha)
        ) {
            TitanLogo(style = TitanLogoStyle.HERO, size = 160.dp)
        }

        // ── LAYER 4: BOOT TEXT + APP NAME ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Boot lines
            bootLines.forEachIndexed { index, line ->
                AnimatedVisibility(
                    visible = index < visibleLines,
                    enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 2 }
                ) {
                    Text(
                        text = line,
                        color = if (line == "SYSTEM READY") TitanColors.RadioactiveGreen
                        else TitanColors.NeonCyan.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (line == "SYSTEM READY") FontWeight.Bold else FontWeight.Normal,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Typewriter app name
            if (visibleChars > 0) {
                Text(
                    text = appName.take(visibleChars),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 6.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }

        // ── LAYER 5: FLASH TRANSITION ──
        if (phase >= 5) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(flashAlpha)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                TitanColors.NeonCyan.copy(alpha = 0.8f),
                                Color.White.copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}
