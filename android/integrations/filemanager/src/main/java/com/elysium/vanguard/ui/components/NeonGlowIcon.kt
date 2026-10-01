package com.elysium.vanguard.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * NEON GLOW ICON
 * Every icon rendered with an animated backlight halo.
 * The glow is a blurred copy behind the sharp icon, pulsing with life.
 */
@Composable
fun NeonGlowIcon(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    glowRadius: Dp = 16.dp,
    pulseEnabled: Boolean = true,
    contentDescription: String? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "neon_pulse")

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (pulseEnabled) 0.8f else 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (pulseEnabled) 1.3f else 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_scale"
    )

    Box(
        modifier = modifier.size(size + glowRadius),
        contentAlignment = Alignment.Center
    ) {
        // LAYER 1: Outer halo (large blur, low alpha)
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color.copy(alpha = glowAlpha * 0.5f),
            modifier = Modifier
                .size(size * 1.2f)
                .blur(glowRadius)
                .scale(glowScale * 1.1f)
        )

        // LAYER 2: Inner halo (tighter blur, higher alpha)
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color.copy(alpha = glowAlpha * 0.8f),
            modifier = Modifier
                .size(size)
                .blur(glowRadius / 2)
                .scale(glowScale)
        )

        // LAYER 3: Sharp foreground icon
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = color,
            modifier = Modifier.size(size)
        )
    }
}
