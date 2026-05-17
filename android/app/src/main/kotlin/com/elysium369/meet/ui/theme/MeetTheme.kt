package com.elysium369.meet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * ═══════════════════════════════════════════════════════════════
 * MEET V2 — PHANTOM CARBON Design System
 * ═══════════════════════════════════════════════════════════════
 * 
 * Masculine, futuristic, PlayStore-tier design language.
 * Deep carbon blacks + turquoise/neon purple phosphorescent accents.
 * Inspired by exotic car dashboards and aerospace HUDs.
 */
object MeetColors {

    // ═══════════ PRIMARY: Turquoise Plasma ═══════════
    val neonGreen = Color(0xFF00FFD4)           // Primary accent — turquoise plasma
    val neonGreenDim = Color(0xFF00C4A3)
    val neonGreenSubtle = Color(0xFF006B5A)

    // ═══════════ SECONDARY: Neon Purple Phosphorescent ═══════════
    val electricBlue = Color(0xFFBB00FF)         // Now neon purple
    val electricBlueDim = Color(0xFF8800CC)
    val electricBlueSubtle = Color(0xFF440066)

    // ═══════════ TERTIARY: Cyan Electric ═══════════
    val cyberCyan = Color(0xFF00E5FF)
    val cyberCyanDim = Color(0xFF00ACC1)
    
    // ═══════════ QUATERNARY: Hot Magenta ═══════════
    val hotMagenta = Color(0xFFFF00AA)
    val hotMagentaDim = Color(0xFFCC0088)

    // ═══════════ BACKGROUNDS: Deep Navy Carbon ═══════════
    val backgroundDeep = Color(0xFF050B15)        // Deepest — navy void
    val backgroundDark = Color(0xFF081222)        // Main surface — navy carbon
    val cardBackground = Color(0xFF0F1B30)        // Elevated cards — visible navy tint
    val cardBackgroundLighter = Color(0xFF152640)  // Hover/active state — steel navy

    // ═══════════ BORDERS ═══════════
    val borderBlue = Color(0xFF1E3355)
    val borderGlow = Color(0xFF00FFD4).copy(alpha = 0.3f)
    val borderSubtle = Color(0xFF182A42)

    // ═══════════ TEXT ═══════════
    val textPrimary = Color(0xFFF0F2F5)
    val textSecondary = Color(0xFF7A8BA5)
    val textMuted = Color(0xFF3D4E63)

    // ═══════════ STATUS ═══════════
    val error = Color(0xFFFF1744)
    val warning = Color(0xFFFFAA00)
    val success = Color(0xFF00FFD4)

    // ═══════════ GRADIENTS ═══════════
    val neonGreenGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF00FFD4), Color(0xFF00E5FF))
    )
    val electricBlueGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFBB00FF), Color(0xFF7700FF))
    )
    val phantomGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF00FFD4), Color(0xFFBB00FF))
    )
    val carbonGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF050B15), Color(0xFF0F1B30), Color(0xFF081222))
    )
    val cardBorderGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF00FFD4).copy(alpha = 0.15f),
            Color(0xFFBB00FF).copy(alpha = 0.3f),
            Color(0xFF00FFD4).copy(alpha = 0.15f)
        )
    )
    val heroGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFBB00FF).copy(alpha = 0.08f),
            Color.Transparent,
            Color(0xFF00FFD4).copy(alpha = 0.04f)
        )
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = MeetColors.neonGreen,
    onPrimary = MeetColors.backgroundDeep,
    primaryContainer = MeetColors.neonGreenSubtle,
    secondary = MeetColors.electricBlue,
    onSecondary = MeetColors.backgroundDeep,
    background = Color(0xFF050B15),
    surface = Color(0xFF0F1B30),
    surfaceVariant = Color(0xFF152640),
    surfaceContainerHighest = Color(0xFF1A3050),
    surfaceContainerHigh = Color(0xFF152B48),
    surfaceContainer = Color(0xFF112240),
    surfaceContainerLow = Color(0xFF0D1C35),
    surfaceContainerLowest = Color(0xFF08142A),
    error = MeetColors.error,
    errorContainer = Color(0xFF3D0012),
    onBackground = MeetColors.textPrimary,
    onSurface = MeetColors.textPrimary,
    onSurfaceVariant = MeetColors.neonGreen,
    outline = Color(0xFF1E3355),
    outlineVariant = Color(0xFF152640),
    inverseSurface = MeetColors.neonGreen,
    inverseOnSurface = MeetColors.backgroundDeep
)

val MeetTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 48.sp,
        letterSpacing = (-1.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 0.15.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = 0.15.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.25.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 1.25.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 1.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 2.sp
    )
)

@Composable
fun MeetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = MeetTypography,
        content = content
    )
}
