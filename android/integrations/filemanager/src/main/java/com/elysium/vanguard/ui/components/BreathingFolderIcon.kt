package com.elysium.vanguard.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.elysium.vanguard.ui.theme.TitanColors

@Composable
fun BreathingFolderIcon(
    baseColor: Color = TitanColors.NeonCyan,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")

    // Breathing scale
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Breathing alpha
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    // Color cycling hue offset
    val hueShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hue_shift"
    )

    // 3D rotation
    val rotationX by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotationX"
    )
    val rotationY by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotationY"
    )

    // Color-cycling between base and shifted color
    val cycledColor = remember(hueShift, baseColor) {
        shiftHue(baseColor, hueShift * 0.1f) // subtle shift
    }

    Box(
        modifier = modifier.size(64.dp),
        contentAlignment = Alignment.Center
    ) {
        // LAYER 1: Deep outer halo (wide diffuse glow)
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = cycledColor.copy(alpha = 0.25f * alpha),
            modifier = Modifier
                .size(68.dp)
                .blur(20.dp)
                .scale(scale * 1.3f)
        )

        // LAYER 2: Inner halo (tighter glow)
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = baseColor.copy(alpha = 0.4f * alpha),
            modifier = Modifier
                .size(60.dp)
                .blur(12.dp)
                .scale(scale * 1.1f)
        )

        // LAYER 3: Sharp foreground folder
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = baseColor,
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    this.rotationX = rotationX
                    this.rotationY = rotationY
                    this.cameraDistance = 12f * density
                }
                .scale(scale)
        )
    }
}

// Simple hue shift approximation via color channel rotation
private fun shiftHue(color: Color, degrees: Float): Color {
    val shift = (degrees % 360f) / 120f
    return when {
        shift < 1f -> Color(
            red = color.red * (1 - shift) + color.green * shift,
            green = color.green * (1 - shift) + color.blue * shift,
            blue = color.blue * (1 - shift) + color.red * shift,
            alpha = color.alpha
        )
        shift < 2f -> {
            val s = shift - 1f
            Color(
                red = color.green * (1 - s) + color.blue * s,
                green = color.blue * (1 - s) + color.red * s,
                blue = color.red * (1 - s) + color.green * s,
                alpha = color.alpha
            )
        }
        else -> {
            val s = shift - 2f
            Color(
                red = color.blue * (1 - s) + color.red * s,
                green = color.red * (1 - s) + color.green * s,
                blue = color.green * (1 - s) + color.blue * s,
                alpha = color.alpha
            )
        }
    }
}
