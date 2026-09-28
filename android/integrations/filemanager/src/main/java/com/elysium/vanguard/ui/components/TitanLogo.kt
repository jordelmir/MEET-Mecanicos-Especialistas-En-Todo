package com.elysium.vanguard.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.elysium.vanguard.R
import com.elysium.vanguard.ui.theme.TitanColors

enum class TitanLogoStyle {
    HERO, // Large, pulsing, for Dashboard/Splash
    ICON  // Small, for top bars
}

@Composable
fun TitanLogo(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    style: TitanLogoStyle = TitanLogoStyle.HERO
) {
    when (style) {
        TitanLogoStyle.HERO -> TitanHeroLogo(modifier, size)
        TitanLogoStyle.ICON -> TitanIconLogo(modifier, size)
    }
}

@Composable
private fun TitanHeroLogo(modifier: Modifier, size: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "hero_logo")

    // Scale pulse
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hero_scale"
    )

    // Glow pulsating
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hero_glow"
    )

    Box(
        modifier = modifier.size(size).scale(scale),
        contentAlignment = Alignment.Center
    ) {
        // Outer Glow (Cyan/Violet mix)
        Box(
            modifier = Modifier
                .size(size * 0.9f)
                .background(TitanColors.NeonCyan.copy(alpha = glowAlpha * 0.3f), CircleShape)
                .blur(30.dp)
        )

        // Inner Core Glow (Using UltraViolet for depth)
        Box(
            modifier = Modifier
                .size(size * 0.7f)
                .background(TitanColors.UltraViolet.copy(alpha = glowAlpha * 0.4f), CircleShape)
                .blur(15.dp)
        )

        // The Vector Drawable
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = "Titan Vanguard Logo",
            modifier = Modifier.size(size)
        )
    }
}

@Composable
private fun TitanIconLogo(modifier: Modifier, size: Dp) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Subtle static glow
        Box(
            modifier = Modifier
                .size(size * 0.8f)
                .background(TitanColors.NeonCyan.copy(alpha = 0.2f), CircleShape)
                .blur(8.dp)
        )

        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = "Titan Vanguard Logo",
            modifier = Modifier.size(size)
        )
    }
}
