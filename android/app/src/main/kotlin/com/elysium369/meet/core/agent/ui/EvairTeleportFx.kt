package com.elysium369.meet.core.agent.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.elysium369.meet.ui.theme.MeetColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * ══════════════════════════════════════════════════════════════════════
 *  E V A I R   T E L E P O R T   F X   (INSTANT SHIFT)
 *  ──────────────────────────────────────────────────────────────
 *  Efecto visual propio y original de Elysium Vanguard:
 *  - Distorsión de energía cuántica cyan / neón.
 *  - Anillo expansivo de impacto sobre el botón seleccionado.
 *  - Partículas radiales de teleportación efímeras de bajo costo GPU.
 * ══════════════════════════════════════════════════════════════════════
 */
@Composable
fun EvairTargetHighlightFx(
    targetBounds: Rect,
    isPulsing: Boolean = true,
    highlightColor: Color = MeetColors.neonGreen,
    modifier: Modifier = Modifier,
) {
    if (targetBounds.isEmpty || targetBounds.width <= 0f || targetBounds.height <= 0f) return

    val transition = rememberInfiniteTransition(label = "target_highlight")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val ringScale by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringScale"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(targetBounds.left + targetBounds.width / 2f, targetBounds.top + targetBounds.height / 2f)
        val radius = (maxOf(targetBounds.width, targetBounds.height) / 2f) * ringScale

        // Anillo de impacto exterior
        drawCircle(
            color = highlightColor.copy(alpha = (1.15f - ringScale).coerceIn(0f, 1f) * 0.8f),
            center = center,
            radius = radius + 8.dp.toPx(),
            style = Stroke(width = 2.dp.toPx())
        )

        // Resplandor del botón
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(highlightColor.copy(alpha = pulseAlpha * 0.35f), Color.Transparent),
                center = center,
                radius = maxOf(targetBounds.width, targetBounds.height) * 1.2f
            ),
            topLeft = Offset(targetBounds.left - 4.dp.toPx(), targetBounds.top - 4.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(
                targetBounds.width + 8.dp.toPx(),
                targetBounds.height + 8.dp.toPx()
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx(), 12.dp.toPx())
        )

        // Borde vivo del botón
        drawRoundRect(
            color = highlightColor.copy(alpha = pulseAlpha),
            topLeft = Offset(targetBounds.left - 2.dp.toPx(), targetBounds.top - 2.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(
                targetBounds.width + 4.dp.toPx(),
                targetBounds.height + 4.dp.toPx()
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx()),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

/**
 * Destello de partículas cuánticas en el punto de partida o llegada de la teletransportación.
 */
@Composable
fun EvairQuantumBurstFx(
    origin: Offset,
    progress: Float, // 0.0f a 1.0f
    color: Color = com.elysium369.meet.ui.theme.MeetColors.secondary,
    modifier: Modifier = Modifier,
) {
    if (progress <= 0f || progress >= 1f) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val particleCount = 18
        val maxRadius = 60.dp.toPx()
        val currentRadius = maxRadius * progress
        val alpha = (1f - progress).coerceIn(0f, 1f)

        for (i in 0 until particleCount) {
            val angle = (i.toDouble() / particleCount) * 2.0 * Math.PI
            val px = origin.x + (currentRadius * cos(angle)).toFloat()
            val py = origin.y + (currentRadius * sin(angle)).toFloat()
            val particleSize = (3.5f * (1f - progress * 0.5f)).dp.toPx()

            drawCircle(
                color = color.copy(alpha = alpha),
                center = Offset(px, py),
                radius = particleSize
            )
        }
    }
}
