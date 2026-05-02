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
 * MEET Brand Colors — Extracted from official MEET promotional material.
 * Neon Green + Electric Blue on deep navy/black backgrounds.
 */
object MeetColors {
    // ── Primary Accent: Neon Green Lima ──
    val neonGreen = Color(0xFF39FF14)
    val neonGreenDim = Color(0xFF2ECC10)
    val neonGreenSubtle = Color(0xFF1A8A0A)

    // ── Secondary Accent: Electric Blue ──
    val electricBlue = Color(0xFF00AAFF)
    val electricBlueDim = Color(0xFF0077CC)
    val electricBlueSubtle = Color(0xFF004C8A)

    // ── Backgrounds ──
    val backgroundDeep = Color(0xFF060612)        // Deepest background
    val backgroundDark = Color(0xFF0A0E1A)        // Main background
    val cardBackground = Color(0xFF0A0E1A)        // Card/panel surfaces
    val cardBackgroundLighter = Color(0xFF122240)  // Elevated card surface

    // ── Borders ──
    val borderBlue = Color(0xFF1A4070)            // Default border
    val borderGlow = Color(0xFF0066CC)            // Active/hover border
    val borderSubtle = Color(0xFF1A2A40)          // Subtle separators

    // ── Text ──
    val textPrimary = Color(0xFFE8E8E8)
    val textSecondary = Color(0xFF8899AA)
    val textMuted = Color(0xFF556677)

    // ── Status ──
    val error = Color(0xFFFF003C)
    val warning = Color(0xFFFFD700)
    val success = Color(0xFF39FF14)               // Same as neonGreen

    // ── Gradients ──
    val neonGreenGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF39FF14), Color(0xFF00CC44))
    )
    val electricBlueGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF00AAFF), Color(0xFF0066CC))
    )
    val cardBorderGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF1A4070),
            Color(0xFF00AAFF).copy(alpha = 0.5f),
            Color(0xFF1A4070)
        )
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = MeetColors.neonGreen,
    onPrimary = MeetColors.backgroundDeep,
    primaryContainer = MeetColors.neonGreenSubtle,
    secondary = MeetColors.electricBlue,
    onSecondary = MeetColors.backgroundDeep,
    background = MeetColors.backgroundDark,
    surface = MeetColors.cardBackground,
    surfaceVariant = MeetColors.cardBackgroundLighter,
    error = MeetColors.error,
    onBackground = MeetColors.textPrimary,
    onSurface = MeetColors.textPrimary,
    outline = MeetColors.borderBlue
)

val MeetTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
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
