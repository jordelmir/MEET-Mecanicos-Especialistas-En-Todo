package com.elysium.vanguard.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ELYSIUM GLASS EFFECTS SUITE
 * Premium glass, reactor glass, neon glass, holographic glass, and pulsing neon borders.
 */

// ── PREMIUM GLASS ──
fun Modifier.premiumGlass(
    cornerRadius: Dp = 16.dp,
    borderColor: Color = Color.White.copy(alpha = 0.2f),
    glassAlpha: Float = 0.3f,
    glowRadius: Dp = 0.dp
): Modifier = composed {
    val shape = RoundedCornerShape(cornerRadius)
    this
        .then(
            if (glowRadius > 0.dp) {
                Modifier.shadow(
                    elevation = glowRadius,
                    shape = shape,
                    spotColor = borderColor.copy(alpha = 0.5f),
                    ambientColor = borderColor.copy(alpha = 0.5f),
                    clip = false
                )
            } else Modifier
        )
        .clip(shape)
        .background(TitanColors.CarbonGray.copy(alpha = glassAlpha))
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    borderColor.copy(alpha = 0.5f),
                    Color.Transparent,
                    borderColor.copy(alpha = 0.2f)
                )
            ),
            shape = shape
        )
}

// ── REACTOR GLASS ──
fun Modifier.reactorGlass(
    cornerRadius: Dp = 20.dp,
    glowColor: Color = TitanColors.NeonCyan,
    alpha: Float = 0.45f,
    glowRadius: Dp = 12.dp
): Modifier = composed {
    val shape = RoundedCornerShape(cornerRadius)
    this
        .shadow(
            elevation = glowRadius,
            shape = shape,
            spotColor = glowColor,
            ambientColor = glowColor,
            clip = false
        )
        .clip(shape)
        .background(TitanColors.AbsoluteBlack.copy(alpha = alpha))
        .border(
            width = 1.2.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.3f),
                    Color.Transparent,
                    Color.White.copy(alpha = 0.1f)
                )
            ),
            shape = shape
        )
}

// ── NEON GLASS ──
fun Modifier.neonGlass(
    cornerRadius: Dp = 16.dp,
    glowColor: Color = TitanColors.NeonCyan,
    strokeWidth: Dp = 1.dp
): Modifier = composed {
    val shape = RoundedCornerShape(cornerRadius)
    this
        .shadow(
            elevation = 16.dp,
            shape = shape,
            spotColor = glowColor,
            ambientColor = glowColor,
            clip = false
        )
        .clip(shape)
        .background(TitanColors.CarbonGray.copy(alpha = 0.6f))
        .border(
            width = strokeWidth,
            brush = Brush.verticalGradient(
                colors = listOf(
                    glowColor.copy(alpha = 0.8f),
                    Color.Transparent,
                    glowColor.copy(alpha = 0.3f)
                )
            ),
            shape = shape
        )
}

// ── HOLOGRAPHIC GLASS ── (Multi-color shifting border)
fun Modifier.holographicGlass(
    cornerRadius: Dp = 20.dp,
    glassAlpha: Float = 0.25f,
    glowRadius: Dp = 16.dp
): Modifier = composed {
    val shape = RoundedCornerShape(cornerRadius)
    val infiniteTransition = rememberInfiniteTransition(label = "holo")

    val hueShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hue"
    )

    val borderColors = listOf(
        TitanColors.NeonCyan,
        TitanColors.PlasmaPurple,
        TitanColors.QuantumPink,
        TitanColors.NeonOrange,
        TitanColors.NeonYellow,
        TitanColors.RadioactiveGreen,
        TitanColors.NeonCyan
    )

    val startIndex = (hueShift * (borderColors.size - 1)).toInt().coerceIn(0, borderColors.size - 2)
    val fraction = (hueShift * (borderColors.size - 1)) - startIndex
    val currentColor = lerp(borderColors[startIndex], borderColors[startIndex + 1], fraction)

    this
        .shadow(
            elevation = glowRadius,
            shape = shape,
            spotColor = currentColor.copy(alpha = 0.6f),
            ambientColor = currentColor.copy(alpha = 0.4f),
            clip = false
        )
        .clip(shape)
        .background(TitanColors.DeepVoid.copy(alpha = glassAlpha))
        .border(
            width = 1.5.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    currentColor.copy(alpha = 0.9f),
                    currentColor.copy(alpha = 0.2f),
                    currentColor.copy(alpha = 0.7f)
                )
            ),
            shape = shape
        )
}

// ── PULSING NEON BORDER ── (Alive breathing border)
fun Modifier.pulsingNeonBorder(
    cornerRadius: Dp = 20.dp,
    glowColor: Color = TitanColors.NeonCyan,
    glassAlpha: Float = 0.2f,
    glowRadius: Dp = 14.dp
): Modifier = composed {
    val shape = RoundedCornerShape(cornerRadius)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border_alpha"
    )

    val glowElevation by infiniteTransition.animateFloat(
        initialValue = glowRadius.value * 0.5f,
        targetValue = glowRadius.value * 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_elevation"
    )

    this
        .shadow(
            elevation = glowElevation.dp,
            shape = shape,
            spotColor = glowColor.copy(alpha = borderAlpha * 0.6f),
            ambientColor = glowColor.copy(alpha = borderAlpha * 0.4f),
            clip = false
        )
        .clip(shape)
        .background(TitanColors.AbsoluteBlack.copy(alpha = glassAlpha))
        .border(
            width = 1.5.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    glowColor.copy(alpha = borderAlpha),
                    glowColor.copy(alpha = borderAlpha * 0.3f),
                    glowColor.copy(alpha = borderAlpha * 0.8f)
                )
            ),
            shape = shape
        )
}

// ── COLOR LERP HELPER ──
private fun lerp(start: Color, stop: Color, fraction: Float): Color {
    return Color(
        red = start.red + (stop.red - start.red) * fraction,
        green = start.green + (stop.green - start.green) * fraction,
        blue = start.blue + (stop.blue - start.blue) * fraction,
        alpha = start.alpha + (stop.alpha - start.alpha) * fraction
    )
}
