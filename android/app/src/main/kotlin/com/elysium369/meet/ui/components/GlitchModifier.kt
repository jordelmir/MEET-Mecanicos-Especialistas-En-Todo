package com.elysium369.meet.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.elysium369.meet.ui.theme.MeetColors
import kotlin.random.Random

/**
 * Applies a "Digital Interference" glitch effect.
 * Perfect for showing communication errors or offline nodes in a tactical map.
 */
fun Modifier.interferenceGlitch(
    enabled: Boolean = true,
    intensity: Float = 1f
): Modifier = composed {
    if (!enabled) return@composed this

    val infiniteTransition = rememberInfiniteTransition(label = "glitch")
    
    // Rapid alpha flickering
    val alphaFlicker by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(Random.nextInt(50, 150), easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alphaFlicker"
    )

    // Random jitter/shake
    val jitterX by infiniteTransition.animateFloat(
        initialValue = -2f * intensity,
        targetValue = 2f * intensity,
        animationSpec = infiniteRepeatable(
            animation = tween(Random.nextInt(30, 80), easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "jitterX"
    )

    this
        .alpha(alphaFlicker)
        .offset(x = jitterX.dp)
        .drawBehind {
            // Draw occasional "noise" lines
            if (Random.nextFloat() > 0.8f) {
                val y = Random.nextFloat() * size.height
                drawLine(
                    color = MeetColors.error.copy(alpha = 0.5f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
}
