package com.elysium369.meet.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import com.elysium369.meet.ui.theme.MeetColors
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════
// ELITE ANIMATIONS V2 — Phantom Carbon Edition
// ═══════════════════════════════════════════════════════════════

/**
 * Neon glow modifier — pulsating OUTER glow that renders OUTSIDE the container.
 * Uses drawBehind to paint softened concentric glow rings behind the composable,
 * ensuring the light emanates outward from the border, never clipped inside.
 */
fun Modifier.neonGlow(
    color: Color,
    shape: Shape = RoundedCornerShape(12.dp),
    minElevation: Float = 8f,
    maxElevation: Float = 24f,
    minAlpha: Float = 0.3f,
    maxAlpha: Float = 0.8f,
    durationMs: Int = 1500,
    isEnabled: Boolean = true
): Modifier = composed {
    if (!isEnabled) return@composed this

    val infiniteTransition = rememberInfiniteTransition(label = "neon_glow_transition")

    val glowIntensity by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowIntensity"
    )

    val currentAlpha = minAlpha + (maxAlpha - minAlpha) * glowIntensity
    val currentSpread = minElevation + (maxElevation - minElevation) * glowIntensity

    this.drawBehind {
        val cornerRadius = when (shape) {
            is RoundedCornerShape -> {
                val resolved = shape.topStart.toPx(size, this)
                CornerRadius(resolved, resolved)
            }
            else -> CornerRadius(12.dp.toPx(), 12.dp.toPx())
        }

        // Layer 1: Wide diffuse outer glow
        val spread1 = currentSpread * 1.2f
        drawRoundRect(
            color = color.copy(alpha = currentAlpha * 0.15f),
            topLeft = Offset(-spread1, -spread1),
            size = Size(size.width + spread1 * 2, size.height + spread1 * 2),
            cornerRadius = CornerRadius(cornerRadius.x + spread1, cornerRadius.y + spread1)
        )

        // Layer 2: Medium glow halo
        val spread2 = currentSpread * 0.7f
        drawRoundRect(
            color = color.copy(alpha = currentAlpha * 0.25f),
            topLeft = Offset(-spread2, -spread2),
            size = Size(size.width + spread2 * 2, size.height + spread2 * 2),
            cornerRadius = CornerRadius(cornerRadius.x + spread2, cornerRadius.y + spread2)
        )

        // Layer 3: Tight border glow (hugs the edge)
        val spread3 = currentSpread * 0.3f
        drawRoundRect(
            color = color.copy(alpha = currentAlpha * 0.4f),
            topLeft = Offset(-spread3, -spread3),
            size = Size(size.width + spread3 * 2, size.height + spread3 * 2),
            cornerRadius = CornerRadius(cornerRadius.x + spread3, cornerRadius.y + spread3)
        )
    }
}

/**
 * Elite Scanner Animation — Full aerospace radar with hex grid,
 * pulsing rings, corner targeting marks, and rotating sweep beam.
 */
@Composable
fun EliteScannerAnimation(
    modifier: Modifier = Modifier,
    scanText: String = "SISTEMAS"
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "rotation"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val scanLine by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "scanLine"
    )

    val primaryColor = MeetColors.neonGreen
    val secondaryColor = MeetColors.electricBlue

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2 * 0.8f

                // Hex grid background
                drawHexagon(center, radius * 1.1f, primaryColor.copy(alpha = 0.08f))
                drawHexagon(center, radius * 0.85f, secondaryColor.copy(alpha = 0.05f))

                // Grid rings with gradient opacity
                for (i in 1..5) {
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.03f + (i * 0.01f)),
                        radius = radius * (i / 5f),
                        style = Stroke(width = 0.5.dp.toPx())
                    )
                }

                // Pulsing outer ring
                drawCircle(
                    color = primaryColor.copy(alpha = 0.2f),
                    radius = radius * pulse,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Radar sweep
                withTransform({ rotate(rotation, center) }) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0f to Color.Transparent,
                            0.3f to secondaryColor.copy(alpha = 0.15f),
                            0.7f to primaryColor.copy(alpha = 0.25f),
                            1f to primaryColor.copy(alpha = 0.4f)
                        ),
                        startAngle = 0f, sweepAngle = 90f, useCenter = true,
                        size = Size(radius * 2, radius * 2),
                        topLeft = Offset(center.x - radius, center.y - radius)
                    )
                    drawLine(
                        color = primaryColor,
                        start = center,
                        end = Offset(center.x + radius * cos(0f), center.y + radius * sin(0f)),
                        strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round
                    )
                }

                // Scanning horizontal line
                val lineY = center.y - radius + (radius * 2 * scanLine)
                drawLine(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.3f to primaryColor.copy(alpha = 0.5f),
                        0.7f to primaryColor.copy(alpha = 0.5f),
                        1f to Color.Transparent
                    ),
                    start = Offset(center.x - radius, lineY),
                    end = Offset(center.x + radius, lineY),
                    strokeWidth = 1.5.dp.toPx()
                )

                // Corner targeting marks
                drawCornerMarkers(center, radius * 1.15f, primaryColor.copy(alpha = 0.7f))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SCAN", color = primaryColor, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black, letterSpacing = 6.sp)
                Text(scanText, color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
            }
        }
    }
}

/**
 * Deletion animation with glitch chromatic aberration.
 */
@Composable
fun EliteDeletionAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "deletion")
    val glitchOffset by infiniteTransition.animateFloat(
        initialValue = -5f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(50, easing = LinearEasing), RepeatMode.Reverse),
        label = "glitch"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha"
    )

    val deleteColor = MeetColors.error

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2 * 0.7f
                drawCircle(deleteColor.copy(alpha = 0.1f * alpha), radius * 1.5f)
                withTransform({ translate(left = glitchOffset) }) {
                    drawDtcIcon(center, radius, deleteColor.copy(alpha = alpha))
                }
                withTransform({ translate(left = -glitchOffset * 0.5f, top = 2f) }) {
                    drawDtcIcon(center, radius, MeetColors.neonGreen.copy(alpha = 0.3f * alpha))
                }
            }
        }
    }
}

// ── Drawing helpers ──

private fun DrawScope.drawHexagon(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        for (i in 0..5) {
            val angle = i * PI / 3
            val x = center.x + radius * cos(angle).toFloat()
            val y = center.y + radius * sin(angle).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawPath(path, color, style = Stroke(width = 1.dp.toPx()))
}

private fun DrawScope.drawCornerMarkers(center: Offset, radius: Float, color: Color) {
    val size = 20f
    val corners = listOf(
        Pair(Offset(center.x - radius, center.y - radius), Pair(1f, 1f)),
        Pair(Offset(center.x + radius, center.y - radius), Pair(-1f, 1f)),
        Pair(Offset(center.x - radius, center.y + radius), Pair(1f, -1f)),
        Pair(Offset(center.x + radius, center.y + radius), Pair(-1f, -1f))
    )
    corners.forEach { (pos, dir) ->
        drawLine(color, pos, Offset(pos.x + size * dir.first, pos.y), 2f)
        drawLine(color, pos, Offset(pos.x, pos.y + size * dir.second), 2f)
    }
}

private fun DrawScope.drawDtcIcon(center: Offset, radius: Float, color: Color) {
    val thickness = 10f
    val s = radius * 0.8f
    withTransform({ rotate(45f, center) }) {
        drawRect(color, Offset(center.x - thickness / 2, center.y - s), Size(thickness, s * 2))
        drawRect(color, Offset(center.x - s, center.y - thickness / 2), Size(s * 2, thickness))
    }
}

/**
 * Staggered fade-in-up entrance animation for list items.
 */
@Composable
fun AnimatedEntrance(
    index: Int,
    staggerDelayMs: Int = 60,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index.toLong() * staggerDelayMs)
        visible = true
    }
    val animatedAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing), label = "ea"
    )
    val animatedOffset by animateFloatAsState(
        targetValue = if (visible) 0f else 30f,
        animationSpec = tween(400, easing = FastOutSlowInEasing), label = "eo"
    )
    Box(modifier = Modifier
        .alpha(animatedAlpha)
        .graphicsLayer { translationY = animatedOffset }
    ) {
        content()
    }
}

/**
 * Scale-pulse modifier for interactive elements on press.
 */
fun Modifier.pulseOnHover(
    enabled: Boolean = true,
    minScale: Float = 0.97f,
    maxScale: Float = 1.02f
): Modifier = composed {
    if (!enabled) return@composed this
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_hover")
    val scale by infiniteTransition.animateFloat(
        initialValue = minScale, targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ), label = "ps"
    )
    this.scale(scale)
}
