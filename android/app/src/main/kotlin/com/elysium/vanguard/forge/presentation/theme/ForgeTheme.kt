package com.elysium.vanguard.forge.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.elysium369.meet.ui.theme.MeetColors
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Forge Theme — identidad ELYSIUM Vanguard.
 *
 * Reglas:
 * - Dark por defecto (nocturno/tecnológico).
 * - Cuatro canales reactivos de MeetColors: verde, cian, azul y violeta.
 * - Status colors (success/warning/error) para provenance labels.
 * - Cards con borde sutil neón.
 *
 * No blanco genérico. No Material default.
 */
object ForgeColors {

    // Base dark
    val Background: Color get() = MeetColors.backgroundDeep
    val Surface: Color get() = MeetColors.cardBackground
    val SurfaceVariant: Color get() = MeetColors.cardBackgroundLighter
    val OnBackground: Color get() = MeetColors.textPrimary
    val OnSurface: Color get() = MeetColors.textPrimary

    // Brand neón
    val Primary: Color get() = MeetColors.neonGreen
    val OnPrimary: Color get() = brandContent(Primary)
    val PrimaryContainer: Color get() = MeetColors.neonGreenSubtle
    val Secondary: Color get() = MeetColors.electricBlue
    val OnSecondary: Color get() = brandContent(Secondary)
    val SecondaryContainer: Color get() = MeetColors.electricBlueSubtle

    // Accents
    val Tertiary: Color get() = MeetColors.cyberCyan
    val Accent: Color get() = MeetColors.hotMagenta

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

    val Outline: Color get() = MeetColors.borderBlue
    val OutlineVariant: Color get() = MeetColors.borderSubtle
}

private fun brandContent(color: Color): Color =
    if (color.luminance() > 0.179f) MeetColors.backgroundDeep else MeetColors.textPrimary

private fun forgeDarkColorScheme() = darkColorScheme(
    primary = ForgeColors.Primary,
    onPrimary = ForgeColors.OnPrimary,
    primaryContainer = ForgeColors.PrimaryContainer,
    secondary = ForgeColors.Secondary,
    onSecondary = ForgeColors.OnSecondary,
    secondaryContainer = ForgeColors.SecondaryContainer,
    tertiary = ForgeColors.Tertiary,
    onTertiary = brandContent(ForgeColors.Tertiary),
    onPrimaryContainer = MeetColors.textPrimary,
    onSecondaryContainer = MeetColors.textPrimary,
    background = ForgeColors.Background,
    onBackground = ForgeColors.OnBackground,
    surface = ForgeColors.Surface,
    onSurface = ForgeColors.OnSurface,
    surfaceVariant = ForgeColors.SurfaceVariant,
    onSurfaceVariant = MeetColors.textSecondary,
    outline = ForgeColors.Outline,
    outlineVariant = ForgeColors.OutlineVariant,
    error = ForgeColors.Error
)

private fun forgeLightColorScheme() = lightColorScheme(
    primary = ForgeColors.Primary,
    onPrimary = ForgeColors.OnPrimary,
    secondary = ForgeColors.Secondary,
    onSecondary = ForgeColors.OnSecondary,
    tertiary = ForgeColors.Tertiary,
    onTertiary = brandContent(ForgeColors.Tertiary),
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
    val colorScheme = if (darkTheme) forgeDarkColorScheme() else forgeLightColorScheme()
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ForgeTypographyImpl,
        content = content
    )
}