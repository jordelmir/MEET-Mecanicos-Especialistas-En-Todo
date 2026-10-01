package com.elysium.nexus.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.nexus.ui.theme.ElysiumColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Elysium Nexus — Premium Startup Animation
 *
 * A control-themed splash screen that establishes the
 * brand identity on every cold start. The animation
 * sequence:
 *
 *  1. **Ring Ignition** (0–600ms): Two concentric neon
 *     rings (cyan outer, magenta inner) scale from 0
 *     to full size with a spring-back overshoot.
 *  2. **Controller Reveal** (300–900ms): A stylized
 *     D-pad cross + action buttons + analog sticks
 *     fade in at the center. The cross rotates ±5°
 *     with a breathing motion.
 *  3. **Orbital Triggers** (500–1200ms): Two arc
 *     segments orbit the rings (left trigger cyan,
 *     right trigger magenta), referencing the L/R
 *     triggers of a real gamepad.
 *  4. **Particle Burst** (800–1500ms): Tiny neon
 *     particles eject radially from the center,
 *     simulating a "power on" burst.
 *  5. **Brand Title** (1200–2000ms): "ELYSIUM NEXUS"
 *     slides up from below and fades in with a
 *     subtitle "UNIVERSAL CONTROLLER".
 *  6. **Hold & Fade** (2000–2800ms): Everything
 *     holds for a beat, then the entire screen
 *     fades to transparent, revealing the Hub
 *     behind it.
 *
 * Total duration: ~2800ms.
 *
 * The splash is entirely GPU-driven (Canvas + Compose
 * animations). No bitmaps, no Lottie, no external
 * dependencies. Works on every device from API 26+.
 */
@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit
) {
    // === ANIMATION DRIVERS ===
    val ringScale = remember { Animatable(0f) }
    val innerRingScale = remember { Animatable(0f) }
    val controllerAlpha = remember { Animatable(0f) }
    val controllerScale = remember { Animatable(0.3f) }
    val orbitalProgress = remember { Animatable(0f) }
    val particleBurst = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleOffset = remember { Animatable(40f) }
    val fadeOut = remember { Animatable(1f) }

    // Continuous breathing rotation for the D-pad
    val infinite = rememberInfiniteTransition(label = "splash_breathe")
    val breatheRotation by infinite.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dpad_breathe"
    )
    val glowPulse by infinite.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )
    // Orbital continuous rotation
    val orbitalRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbital_spin"
    )

    // Generate random particles once
    val particles = remember {
        List(24) {
            Particle(
                angle = (it * 15f) + (-7..7).random(),
                speed = (0.6f + Math.random().toFloat() * 0.8f),
                size = 2f + Math.random().toFloat() * 3f,
                color = when (it % 3) {
                    0 -> ElysiumColors.NeonCyan
                    1 -> ElysiumColors.NeonMagenta
                    else -> ElysiumColors.NeonPurple
                }
            )
        }
    }

    // === ORCHESTRATED TIMELINE ===
    LaunchedEffect(Unit) {
        // Phase 1: Ring ignition
        ringScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(600, easing = EaseOutBack)
        )
    }
    LaunchedEffect(Unit) {
        delay(150)
        innerRingScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(550, easing = EaseOutBack)
        )
    }
    LaunchedEffect(Unit) {
        // Phase 2: Controller reveal
        delay(300)
        controllerAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(600, easing = EaseOutCubic)
        )
    }
    LaunchedEffect(Unit) {
        delay(300)
        controllerScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(700, easing = EaseOutBack)
        )
    }
    LaunchedEffect(Unit) {
        // Phase 3: Orbital triggers
        delay(500)
        orbitalProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(700, easing = EaseOutCubic)
        )
    }
    LaunchedEffect(Unit) {
        // Phase 4: Particle burst
        delay(800)
        particleBurst.animateTo(
            targetValue = 1f,
            animationSpec = tween(700, easing = EaseOutQuart)
        )
    }
    LaunchedEffect(Unit) {
        // Phase 5: Brand title
        delay(1200)
        titleAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(600, easing = EaseOutCubic)
        )
    }
    LaunchedEffect(Unit) {
        delay(1200)
        titleOffset.animateTo(
            targetValue = 0f,
            animationSpec = tween(600, easing = EaseOutCubic)
        )
    }
    LaunchedEffect(Unit) {
        // Phase 6: Hold & fade
        delay(2400)
        fadeOut.animateTo(
            targetValue = 0f,
            animationSpec = tween(400, easing = FastOutSlowInEasing)
        )
        onSplashComplete()
    }

    // === RENDER ===
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ElysiumColors.Background)
            .alpha(fadeOut.value),
        contentAlignment = Alignment.Center
    ) {
        // Background radial glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = minOf(size.width, size.height) * 0.6f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ElysiumColors.NeonCyan.copy(alpha = 0.12f * glowPulse),
                        ElysiumColors.NeonPurple.copy(alpha = 0.06f * glowPulse),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = r
                ),
                radius = r,
                center = Offset(cx, cy)
            )
        }

        // Main controller canvas
        Canvas(
            modifier = Modifier
                .size(280.dp)
                .scale(controllerScale.value)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val baseR = minOf(size.width, size.height) * 0.42f

            // === OUTER RING (Cyan) ===
            val outerR = baseR * ringScale.value
            drawCircle(
                color = ElysiumColors.NeonCyan.copy(alpha = 0.15f * glowPulse),
                radius = outerR + 8f,
                center = Offset(cx, cy),
                style = Stroke(width = 16f)
            )
            drawCircle(
                color = ElysiumColors.NeonCyan,
                radius = outerR,
                center = Offset(cx, cy),
                style = Stroke(width = 2.5f)
            )

            // === INNER RING (Magenta) ===
            val innerR = baseR * 0.7f * innerRingScale.value
            drawCircle(
                color = ElysiumColors.NeonMagenta.copy(alpha = 0.12f * glowPulse),
                radius = innerR + 6f,
                center = Offset(cx, cy),
                style = Stroke(width = 12f)
            )
            drawCircle(
                color = ElysiumColors.NeonMagenta,
                radius = innerR,
                center = Offset(cx, cy),
                style = Stroke(width = 2f)
            )

            // === D-PAD CROSS ===
            if (controllerAlpha.value > 0.01f) {
                rotate(degrees = breatheRotation, pivot = Offset(cx, cy)) {
                    drawDpadCross(
                        cx = cx,
                        cy = cy,
                        armLength = baseR * 0.28f,
                        armWidth = baseR * 0.09f,
                        color = ElysiumColors.NeonCyan.copy(alpha = controllerAlpha.value),
                        glowColor = ElysiumColors.NeonCyanGlow.copy(
                            alpha = controllerAlpha.value * 0.5f * glowPulse
                        )
                    )
                }

                // === ACTION BUTTONS (right side) ===
                val btnR = baseR * 0.055f
                val btnDist = baseR * 0.52f
                val actionColors = listOf(
                    ElysiumColors.NeonGreen,   // top (△)
                    ElysiumColors.NeonMagenta,  // right (○)
                    ElysiumColors.NeonCyan,     // bottom (✕)
                    ElysiumColors.NeonOrange    // left (□)
                )
                val actionAngles = listOf(-90f, 0f, 90f, 180f)
                val actionCx = cx + baseR * 0.35f
                val actionCy = cy
                actionAngles.forEachIndexed { i, angle ->
                    val rad = Math.toRadians(angle.toDouble())
                    val bx = actionCx + cos(rad).toFloat() * btnDist * 0.25f
                    val by = actionCy + sin(rad).toFloat() * btnDist * 0.25f
                    // Glow
                    drawCircle(
                        color = actionColors[i].copy(
                            alpha = controllerAlpha.value * 0.35f * glowPulse
                        ),
                        radius = btnR * 2.5f,
                        center = Offset(bx, by)
                    )
                    // Solid
                    drawCircle(
                        color = actionColors[i].copy(alpha = controllerAlpha.value),
                        radius = btnR,
                        center = Offset(bx, by)
                    )
                }

                // === ANALOG STICKS (stylized circles) ===
                val stickR = baseR * 0.10f
                // Left stick (below D-pad)
                val lsx = cx - baseR * 0.22f
                val lsy = cy + baseR * 0.38f
                drawCircle(
                    color = ElysiumColors.NeonPurple.copy(
                        alpha = controllerAlpha.value * 0.2f
                    ),
                    radius = stickR * 1.8f,
                    center = Offset(lsx, lsy)
                )
                drawCircle(
                    color = ElysiumColors.NeonPurple.copy(alpha = controllerAlpha.value),
                    radius = stickR,
                    center = Offset(lsx, lsy),
                    style = Stroke(width = 2f)
                )
                drawCircle(
                    color = ElysiumColors.NeonPurple.copy(alpha = controllerAlpha.value * 0.5f),
                    radius = stickR * 0.35f,
                    center = Offset(lsx, lsy)
                )
                // Right stick
                val rsx = cx + baseR * 0.22f
                val rsy = cy + baseR * 0.38f
                drawCircle(
                    color = ElysiumColors.NeonCyan.copy(
                        alpha = controllerAlpha.value * 0.2f
                    ),
                    radius = stickR * 1.8f,
                    center = Offset(rsx, rsy)
                )
                drawCircle(
                    color = ElysiumColors.NeonCyan.copy(alpha = controllerAlpha.value),
                    radius = stickR,
                    center = Offset(rsx, rsy),
                    style = Stroke(width = 2f)
                )
                drawCircle(
                    color = ElysiumColors.NeonCyan.copy(alpha = controllerAlpha.value * 0.5f),
                    radius = stickR * 0.35f,
                    center = Offset(rsx, rsy)
                )
            }

            // === ORBITAL TRIGGER ARCS ===
            if (orbitalProgress.value > 0.01f) {
                val arcR = outerR + 16f
                val sweep = 60f * orbitalProgress.value
                // Left trigger (cyan) — orbits at current rotation
                drawArc(
                    color = ElysiumColors.NeonCyan.copy(alpha = 0.8f * orbitalProgress.value),
                    startAngle = orbitalRotation + 180f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(cx - arcR, cy - arcR),
                    size = Size(arcR * 2f, arcR * 2f),
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )
                // Glow
                drawArc(
                    color = ElysiumColors.NeonCyanGlow.copy(alpha = 0.3f * orbitalProgress.value * glowPulse),
                    startAngle = orbitalRotation + 180f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(cx - arcR - 4f, cy - arcR - 4f),
                    size = Size((arcR + 4f) * 2f, (arcR + 4f) * 2f),
                    style = Stroke(width = 10f, cap = StrokeCap.Round)
                )
                // Right trigger (magenta) — opposite side
                drawArc(
                    color = ElysiumColors.NeonMagenta.copy(alpha = 0.8f * orbitalProgress.value),
                    startAngle = orbitalRotation,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(cx - arcR, cy - arcR),
                    size = Size(arcR * 2f, arcR * 2f),
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = ElysiumColors.NeonMagentaGlow.copy(alpha = 0.3f * orbitalProgress.value * glowPulse),
                    startAngle = orbitalRotation,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(cx - arcR - 4f, cy - arcR - 4f),
                    size = Size((arcR + 4f) * 2f, (arcR + 4f) * 2f),
                    style = Stroke(width = 10f, cap = StrokeCap.Round)
                )
            }

            // === PARTICLE BURST ===
            if (particleBurst.value > 0.01f) {
                particles.forEach { p ->
                    val rad = Math.toRadians(p.angle.toDouble())
                    val dist = baseR * 0.3f + (baseR * 0.9f * p.speed * particleBurst.value)
                    val px = cx + cos(rad).toFloat() * dist
                    val py = cy + sin(rad).toFloat() * dist
                    val alpha = (1f - particleBurst.value * 0.8f) * 0.9f
                    if (alpha > 0.01f) {
                        // Glow
                        drawCircle(
                            color = p.color.copy(alpha = alpha * 0.4f),
                            radius = p.size * 3f,
                            center = Offset(px, py)
                        )
                        // Core
                        drawCircle(
                            color = p.color.copy(alpha = alpha),
                            radius = p.size,
                            center = Offset(px, py)
                        )
                    }
                }
            }
        }

        // === BRAND TITLE ===
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 180.dp + titleOffset.value.dp)
                .alpha(titleAlpha.value)
        ) {
            Text(
                text = "ELYSIUM NEXUS",
                color = ElysiumColors.NeonCyan,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 6.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "UNIVERSAL CONTROLLER",
                color = ElysiumColors.NeonMagenta.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 4.sp
            )
        }
    }
}

/**
 * Draws a stylized D-pad cross at (cx, cy).
 */
private fun DrawScope.drawDpadCross(
    cx: Float,
    cy: Float,
    armLength: Float,
    armWidth: Float,
    color: Color,
    glowColor: Color
) {
    // Vertical arm
    val vPath = Path().apply {
        moveTo(cx - armWidth, cy - armLength)
        lineTo(cx + armWidth, cy - armLength)
        lineTo(cx + armWidth, cy + armLength)
        lineTo(cx - armWidth, cy + armLength)
        close()
    }
    // Horizontal arm
    val hPath = Path().apply {
        moveTo(cx - armLength, cy - armWidth)
        lineTo(cx + armLength, cy - armWidth)
        lineTo(cx + armLength, cy + armWidth)
        lineTo(cx - armLength, cy + armWidth)
        close()
    }
    // Glow (wider, translucent)
    drawPath(vPath, glowColor, style = Stroke(width = 8f, join = StrokeJoin.Round))
    drawPath(hPath, glowColor, style = Stroke(width = 8f, join = StrokeJoin.Round))
    // Outline
    drawPath(vPath, color, style = Stroke(width = 2.5f, join = StrokeJoin.Round))
    drawPath(hPath, color, style = Stroke(width = 2.5f, join = StrokeJoin.Round))
    // Center dot
    drawCircle(color = color, radius = armWidth * 0.6f, center = Offset(cx, cy))
    // Arrow tips
    val tipLen = armWidth * 0.7f
    // Up arrow
    drawLine(color, Offset(cx, cy - armLength), Offset(cx - tipLen, cy - armLength + tipLen), strokeWidth = 2f)
    drawLine(color, Offset(cx, cy - armLength), Offset(cx + tipLen, cy - armLength + tipLen), strokeWidth = 2f)
    // Down arrow
    drawLine(color, Offset(cx, cy + armLength), Offset(cx - tipLen, cy + armLength - tipLen), strokeWidth = 2f)
    drawLine(color, Offset(cx, cy + armLength), Offset(cx + tipLen, cy + armLength - tipLen), strokeWidth = 2f)
    // Left arrow
    drawLine(color, Offset(cx - armLength, cy), Offset(cx - armLength + tipLen, cy - tipLen), strokeWidth = 2f)
    drawLine(color, Offset(cx - armLength, cy), Offset(cx - armLength + tipLen, cy + tipLen), strokeWidth = 2f)
    // Right arrow
    drawLine(color, Offset(cx + armLength, cy), Offset(cx + armLength - tipLen, cy - tipLen), strokeWidth = 2f)
    drawLine(color, Offset(cx + armLength, cy), Offset(cx + armLength - tipLen, cy + tipLen), strokeWidth = 2f)
}

private data class Particle(
    val angle: Float,
    val speed: Float,
    val size: Float,
    val color: Color
)
