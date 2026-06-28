package com.elysium369.meet.ui.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * ═══════════════════════════════════════════════════════════════
 * Elysium Vanguard V2 — PHANTOM CARBON Design System
 * ═══════════════════════════════════════════════════════════════
 * 
 * Masculine, futuristic, PlayStore-tier design language.
 * Deep carbon blacks + turquoise/neon purple phosphorescent accents.
 * Inspired by exotic car dashboards and aerospace HUDs.
 */
object MeetColors {

    // ═══════════ PRIMARY: Turquoise Plasma ═══════════
    var neonGreen by mutableStateOf(Color(0xFF00FFD4))           // Primary accent — turquoise plasma
    var neonGreenDim by mutableStateOf(Color(0xFF00C4A3))
    var neonGreenSubtle by mutableStateOf(Color(0xFF006B5A))

    // ═══════════ SECONDARY: Neon Purple Phosphorescent ═══════════
    var electricBlue by mutableStateOf(Color(0xFFBB00FF))         // Now neon purple
    var electricBlueDim by mutableStateOf(Color(0xFF8800CC))
    var electricBlueSubtle by mutableStateOf(Color(0xFF440066))

    // ═══════════ TERTIARY: Cyan Electric ═══════════
    var cyberCyan by mutableStateOf(Color(0xFF00E5FF))
    var cyberCyanDim by mutableStateOf(Color(0xFF00ACC1))
    
    // ═══════════ QUATERNARY: Hot Magenta ═══════════
    var hotMagenta by mutableStateOf(Color(0xFFFF00AA))
    var hotMagentaDim by mutableStateOf(Color(0xFFCC0088))

    // ═══════════ BACKGROUNDS: Deep Navy Carbon ═══════════
    val backgroundDeep = Color(0xFF050B15)        // Deepest — navy void
    val backgroundDark = Color(0xFF081222)        // Main surface — navy carbon
    val cardBackground = Color(0xFF0F1B30)        // Elevated cards — visible navy tint
    val cardBackgroundLighter = Color(0xFF152640)  // Hover/active state — steel navy

    // ═══════════ BORDERS ═══════════
    val borderBlue = Color(0xFF1E3355)
    val borderGlow: Color get() = neonGreen.copy(alpha = 0.3f)
    val borderSubtle = Color(0xFF182A42)

    // ═══════════ TEXT ═══════════
    val textPrimary = Color(0xFFF0F2F5)
    val textSecondary = Color(0xFF7A8BA5)
    val textMuted = Color(0xFF3D4E63)

    // ═══════════ STATUS ═══════════
    val error = Color(0xFFFF1744)
    val warning = Color(0xFFFFAA00)
    val success: Color get() = neonGreen

    // ═══════════ GRADIENTS ═══════════
    val neonGreenGradient: Brush get() = Brush.linearGradient(
        colors = listOf(neonGreen, cyberCyan)
    )
    val electricBlueGradient: Brush get() = Brush.linearGradient(
        colors = listOf(electricBlue, Color(0xFF7700FF))
    )
    val phantomGradient: Brush get() = Brush.linearGradient(
        colors = listOf(neonGreen, electricBlue)
    )
    val carbonGradient: Brush get() = Brush.verticalGradient(
        colors = listOf(Color(0xFF050B15), Color(0xFF0F1B30), Color(0xFF081222))
    )
    val cardBorderGradient: Brush get() = Brush.linearGradient(
        colors = listOf(
            neonGreen.copy(alpha = 0.15f),
            electricBlue.copy(alpha = 0.3f),
            neonGreen.copy(alpha = 0.15f)
        )
    )
    val heroGradient: Brush get() = Brush.verticalGradient(
        colors = listOf(
            electricBlue.copy(alpha = 0.08f),
            Color.Transparent,
            neonGreen.copy(alpha = 0.04f)
        )
    )

    // ── SYSTEM THEME SETTINGS LOADER & PERSISTENCE ──
    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences("meet_system_theme_prefs", Context.MODE_PRIVATE)
        neonGreen = Color(prefs.getInt("neonGreen", Color(0xFF00FFD4).toArgb()))
        neonGreenDim = Color(prefs.getInt("neonGreenDim", Color(0xFF00C4A3).toArgb()))
        neonGreenSubtle = Color(prefs.getInt("neonGreenSubtle", Color(0xFF006B5A).toArgb()))

        electricBlue = Color(prefs.getInt("electricBlue", Color(0xFFBB00FF).toArgb()))
        electricBlueDim = Color(prefs.getInt("electricBlueDim", Color(0xFF8800CC).toArgb()))
        electricBlueSubtle = Color(prefs.getInt("electricBlueSubtle", Color(0xFF440066).toArgb()))

        cyberCyan = Color(prefs.getInt("cyberCyan", Color(0xFF00E5FF).toArgb()))
        cyberCyanDim = Color(prefs.getInt("cyberCyanDim", Color(0xFF00ACC1).toArgb()))

        hotMagenta = Color(prefs.getInt("hotMagenta", Color(0xFFFF00AA).toArgb()))
        hotMagentaDim = Color(prefs.getInt("hotMagentaDim", Color(0xFFCC0088).toArgb()))
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences("meet_system_theme_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("neonGreen", neonGreen.toArgb())
            .putInt("neonGreenDim", neonGreenDim.toArgb())
            .putInt("neonGreenSubtle", neonGreenSubtle.toArgb())
            .putInt("electricBlue", electricBlue.toArgb())
            .putInt("electricBlueDim", electricBlueDim.toArgb())
            .putInt("electricBlueSubtle", electricBlueSubtle.toArgb())
            .putInt("cyberCyan", cyberCyan.toArgb())
            .putInt("cyberCyanDim", cyberCyanDim.toArgb())
            .putInt("hotMagenta", hotMagenta.toArgb())
            .putInt("hotMagentaDim", hotMagentaDim.toArgb())
            .apply()
    }

    fun reset(context: Context) {
        neonGreen = Color(0xFF00FFD4)
        neonGreenDim = Color(0xFF00C4A3)
        neonGreenSubtle = Color(0xFF006B5A)
        electricBlue = Color(0xFFBB00FF)
        electricBlueDim = Color(0xFF8800CC)
        electricBlueSubtle = Color(0xFF440066)
        cyberCyan = Color(0xFF00E5FF)
        cyberCyanDim = Color(0xFF00ACC1)
        hotMagenta = Color(0xFFFF00AA)
        hotMagentaDim = Color(0xFFCC0088)
        save(context)
    }

    fun updateNeonGreen(color: Color, context: Context) {
        neonGreen = color
        neonGreenDim = Color(
            red = color.red * 0.77f,
            green = color.green * 0.77f,
            blue = color.blue * 0.77f,
            alpha = color.alpha
        )
        neonGreenSubtle = Color(
            red = color.red * 0.42f,
            green = color.green * 0.42f,
            blue = color.blue * 0.42f,
            alpha = color.alpha
        )
        save(context)
    }

    fun updateElectricBlue(color: Color, context: Context) {
        electricBlue = color
        electricBlueDim = Color(
            red = color.red * 0.73f,
            green = color.green * 0.73f,
            blue = color.blue * 0.73f,
            alpha = color.alpha
        )
        electricBlueSubtle = Color(
            red = color.red * 0.36f,
            green = color.green * 0.36f,
            blue = color.blue * 0.36f,
            alpha = color.alpha
        )
        save(context)
    }

    fun updateCyberCyan(color: Color, context: Context) {
        cyberCyan = color
        cyberCyanDim = Color(
            red = color.red * 0.75f,
            green = color.green * 0.75f,
            blue = color.blue * 0.75f,
            alpha = color.alpha
        )
        save(context)
    }

    fun updateHotMagenta(color: Color, context: Context) {
        hotMagenta = color
        hotMagentaDim = Color(
            red = color.red * 0.80f,
            green = color.green * 0.80f,
            blue = color.blue * 0.80f,
            alpha = color.alpha
        )
        save(context)
    }
}

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
    val dynamicColorScheme = darkColorScheme(
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
    MaterialTheme(
        colorScheme = dynamicColorScheme,
        typography = MeetTypography,
        content = content
    )
}
