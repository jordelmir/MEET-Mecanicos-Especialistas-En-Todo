package com.elysium.vanguard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * PROJECT TITAN: MASTER THEME
 * The design system orchestration layer for Elysium Vanguard.
 */
@Composable
fun TitanTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = TitanColors.NeonCyan,
        secondary = TitanColors.NeonGreen,
        tertiary = TitanColors.NeonRed,
        background = TitanColors.AbsoluteBlack,
        surface = TitanColors.CarbonGray,
        onPrimary = TitanColors.AbsoluteBlack,
        onSecondary = TitanColors.AbsoluteBlack,
        onBackground = TitanColors.GhostWhite,
        onSurface = TitanColors.GhostWhite
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TitanTypography,
        content = content
    )
}
