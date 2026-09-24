package com.elysium369.meet.safety.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.elysium369.meet.ui.theme.MeetColors

/**
 * Animated pulse rings around a shield icon.
 * Color reflects system health state:
 *   - Green: all systems nominal
 *   - Yellow/Warning: pending operations
 *   - Red/Error: sync failure or offline
 */
@Composable
fun SafetyPulse(
    state: PulseState = PulseState.NOMINAL,
    size: Dp = 72.dp,
    modifier: Modifier = Modifier,
) {
    val pulseColor = when (state) {
        PulseState.NOMINAL -> MeetColors.neonGreen
        PulseState.PENDING -> MeetColors.warning
        PulseState.ERROR -> MeetColors.error
    }

    val transition = rememberInfiniteTransition(label = "safety-pulse")

    val ring1Scale by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring1",
    )
    val ring1Alpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring1-alpha",
    )

    val ring2Scale by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, delayMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring2",
    )
    val ring2Alpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, delayMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring2-alpha",
    )

    val ring3Scale by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, delayMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring3",
    )
    val ring3Alpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, delayMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring3-alpha",
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = this.center
            val baseRadius = this.size.minDimension / 3f

            // Three concentric expanding rings
            drawCircle(
                color = pulseColor.copy(alpha = ring1Alpha),
                radius = baseRadius * ring1Scale,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
            drawCircle(
                color = pulseColor.copy(alpha = ring2Alpha),
                radius = baseRadius * ring2Scale,
                center = center,
                style = Stroke(width = 1.5.dp.toPx()),
            )
            drawCircle(
                color = pulseColor.copy(alpha = ring3Alpha),
                radius = baseRadius * ring3Scale,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
        }

        Icon(
            imageVector = Icons.Filled.Shield,
            contentDescription = when (state) {
                PulseState.NOMINAL -> "Sistema de seguridad activo"
                PulseState.PENDING -> "Operaciones pendientes de sincronización"
                PulseState.ERROR -> "Error en el sistema de seguridad"
            },
            modifier = Modifier.size(size * 0.45f),
            tint = pulseColor,
        )
    }
}

enum class PulseState {
    NOMINAL,
    PENDING,
    ERROR,
}
