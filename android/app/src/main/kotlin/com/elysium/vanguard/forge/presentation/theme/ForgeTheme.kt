package com.elysium.vanguard.forge.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Forge Theme — identidad ELYSIUM Vanguard.
 *
 * Reglas:
 * - Dark por defecto (nocturno/tecnológico).
 * - Teal neón primario + Purple neón secundario.
 * - Status colors (success/warning/error) para provenance labels.
 * - Cards con borde sutil neón.
 *
 * No blanco genérico. No Material default.
 */
object ForgeColors {

    // Base dark
    val Background = Color(0xFF0A0E1A)
    val Surface = Color(0xFF121826)
    val SurfaceVariant = Color(0xFF1B2235)
    val OnBackground = Color(0xFFE5EAF2)
    val OnSurface = Color(0xFFD1D6E0)

    // Brand neón
    val Primary = Color(0xFF00E5D0)         // Teal neón
    val OnPrimary = Color(0xFF003B36)
    val PrimaryContainer = Color(0xFF00524B)
    val Secondary = Color(0xFF9B6BFF)        // Purple neón
    val OnSecondary = Color(0xFF1F0F4D)
    val SecondaryContainer = Color(0xFF3B2A8C)

    // Accents
    val Tertiary = Color(0xFFFFB347)        // Amber
    val Accent = Color(0xFF00B4FF)

    // Status
    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFFBBF24)
    val Error = Color(0xFFEF4444)
    val Info = Color(0xFF60A5FA)

    // Provenance colors (Phase B integration)
    val ProvenanceReal = Color(0xFF22C55E)
    val ProvenanceOffline = Color(0xFF60A5FA)
    val ProvenanceSimulated = Color(0xFFF59E0B)
    val ProvenanceSinEnlace = Color(0xFF9CA3AF)
    val ProvenanceInferred = Color(0xFFA78BFA)
    val ProvenanceManual = Color(0xFFEC4899)

    // Severity (Forge damage)
    val SeverityNone = Color(0xFF22C55E)
    val SeverityLow = Color(0xFFFACC15)
    val SeverityMedium = Color(0xFFFB923C)
    val SeverityHigh = Color(0xFFEF4444)
    val SeverityCritical = Color(0xFF991B1B)

    val Outline = Color(0xFF2A3349)
    val OutlineVariant = Color(0xFF1F2738)
}

private val DarkColorScheme = darkColorScheme(
    primary = ForgeColors.Primary,
    onPrimary = ForgeColors.OnPrimary,
    primaryContainer = ForgeColors.PrimaryContainer,
    secondary = ForgeColors.Secondary,
    onSecondary = ForgeColors.OnSecondary,
    secondaryContainer = ForgeColors.SecondaryContainer,
    tertiary = ForgeColors.Tertiary,
    background = ForgeColors.Background,
    onBackground = ForgeColors.OnBackground,
    surface = ForgeColors.Surface,
    onSurface = ForgeColors.OnSurface,
    surfaceVariant = ForgeColors.SurfaceVariant,
    outline = ForgeColors.Outline,
    outlineVariant = ForgeColors.OutlineVariant,
    error = ForgeColors.Error
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    secondary = Color(0xFF6D28D9),
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    error = Color(0xFFDC2626)
)

/**
 * Tipografía Forge — escala técnica, sin decoraciones.
 */
object ForgeTypography {
    val DisplayLarge: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = 1.2.sp
    )
    val HeadlineLarge: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp
    )
    val TitleMedium: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = 0.4.sp
    )
    val BodyLarge: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    )
    val LabelSmall: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.0.sp
    )
}

private val ForgeTypographyImpl = Typography(
    displayLarge = ForgeTypography.DisplayLarge,
    headlineLarge = ForgeTypography.HeadlineLarge,
    titleMedium = ForgeTypography.TitleMedium,
    bodyLarge = ForgeTypography.BodyLarge,
    labelSmall = ForgeTypography.LabelSmall
)

@Composable
fun ForgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ForgeTypographyImpl,
        content = content
    )
}