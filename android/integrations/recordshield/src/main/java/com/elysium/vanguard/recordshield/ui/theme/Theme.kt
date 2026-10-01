package com.elysium.vanguard.recordshield.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * ============================================================================
 * Record Shield Theme — Material3 Dark Theme Override
 * ============================================================================
 *
 * Why Material3 with custom colors: Material3 provides the structural
 * foundation (dynamic layouts, component library, motion system) while
 * we override every color to achieve the neo-futuristic aesthetic.
 * Pure custom Compose would miss out on M3's accessibility features.
 * ============================================================================
 */

private val RecordShieldDarkColors = darkColorScheme(
    primary = MatrixGreen,
    onPrimary = DeepBlack,
    primaryContainer = MatrixGreenSubtle,
    onPrimaryContainer = MatrixGreen,

    secondary = ElectricBlue,
    onSecondary = DeepBlack,
    secondaryContainer = Color(0x1A00D4FF),
    onSecondaryContainer = ElectricBlue,

    tertiary = DeepPurple,
    onTertiary = DeepBlack,
    tertiaryContainer = Color(0x1ABB86FC),
    onTertiaryContainer = DeepPurple,

    background = DeepBlack,
    onBackground = TextPrimary,

    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = CardSurface,
    onSurfaceVariant = TextSecondary,

    error = RecordingRed,
    onError = Color.White,

    outline = SubtleBorder,
    outlineVariant = Color(0xFF1F1F2E)
)

// Why: Using system default for now. In production, import JetBrains Mono
// or a custom monospace font for the futuristic terminal aesthetic.
val RecordShieldTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        letterSpacing = (-1).sp,
        color = TextPrimary
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = (-0.5).sp,
        color = TextPrimary
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        color = TextPrimary
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        letterSpacing = 0.15.sp,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = 0.1.sp,
        color = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp,
        color = TextSecondary
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.25.sp,
        color = TextSecondary
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 1.sp,
        color = MatrixGreen
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
        color = TextTertiary
    )
)

@Composable
fun RecordShieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RecordShieldDarkColors,
        typography = RecordShieldTypography,
        content = content
    )
}
