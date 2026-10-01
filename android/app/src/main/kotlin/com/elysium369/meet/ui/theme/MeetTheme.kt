package com.elysium369.meet.ui.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * ═══════════════════════════════════════════════════════════════
 * Elysium Vanguard — configurable energy design system
 * ═══════════════════════════════════════════════════════════════
 * 
 * Dark titanium surfaces with four persistent brand channels.
 * Existing saved palettes are preserved; factory defaults follow the EV artwork.
 * Semantic warning, error and success remain independent of brand customization.
 */
object MeetColors {

    // ═══════════ PRIMARY: Brand Energy ═══════════
    var neonGreen by mutableStateOf(Color(0xFF39FF66))           // Primary brand channel
    var neonGreenDim by mutableStateOf(Color(0xFF2CC44F))
    var neonGreenSubtle by mutableStateOf(Color(0xFF186B2B))

    // ═══════════ SECONDARY: Brand Energy ═══════════
    var electricBlue by mutableStateOf(Color(0xFF00D9FF))         // Secondary brand channel
    var electricBlueDim by mutableStateOf(Color(0xFF009EB9))
    var electricBlueSubtle by mutableStateOf(Color(0xFF00505E))

    // ═══════════ TERTIARY: Brand Energy ═══════════
    var cyberCyan by mutableStateOf(Color(0xFF1677FF))
    var cyberCyanDim by mutableStateOf(Color(0xFF105CBF))
    
    // ═══════════ QUATERNARY: Brand Energy ═══════════
    var hotMagenta by mutableStateOf(Color(0xFF8255FF))
    var hotMagentaDim by mutableStateOf(Color(0xFF6844CC))

    var isWhiteTheme by mutableStateOf(false)

    // ═══════════ BACKGROUNDS ═══════════
    val backgroundDeep: Color get() = if (isWhiteTheme) Color.White else Color(0xFF050B15)
    val backgroundDark: Color get() = if (isWhiteTheme) Color(0xFFF6F8FA) else Color(0xFF081222)
    val cardBackground = Color(0xFF0F1B30)
    val cardBackgroundLighter = Color(0xFF152640)

    // ═══════════ BORDERS ═══════════
    val borderBlue = Color(0xFF1E3355)
    val borderGlow: Color get() = neonGreen.copy(alpha = 0.3f)
    val borderSubtle = Color(0xFF182A42)

    // ═══════════ TEXT ═══════════
    val textPrimary = Color(0xFFF0F2F5)
    val textSecondary = Color(0xFFA5B5C8)
    val textMuted = Color(0xFF8999AF)

    // ═══════════ STATUS ═══════════
    val error = Color(0xFFFF1744)
    val warning = Color(0xFFFFAA00)
    val success = Color(0xFF39FF66)

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

    // Semantic brand aliases keep historical renderers source-compatible.
    val primary: Color get() = neonGreen
    val secondary: Color get() = electricBlue
    val tertiary: Color get() = cyberCyan
    val quaternary: Color get() = hotMagenta

    /** Applies a resolved or preview palette without writing preferences. */
    fun applyPalette(palette: com.elysium369.meet.ui.elysium.theme.ElysiumPaletteOverride) {
        neonGreen = Color(requireNotNull(palette.primaryArgb).toInt())
        electricBlue = Color(requireNotNull(palette.secondaryArgb).toInt())
        cyberCyan = Color(requireNotNull(palette.tertiaryArgb).toInt())
        hotMagenta = Color(requireNotNull(palette.quaternaryArgb).toInt())
        fun dim(color: Color, factor: Float) = Color(color.red * factor, color.green * factor, color.blue * factor, color.alpha)
        neonGreenDim = dim(neonGreen, 0.77f)
        neonGreenSubtle = dim(neonGreen, 0.42f)
        electricBlueDim = dim(electricBlue, 0.73f)
        electricBlueSubtle = dim(electricBlue, 0.37f)
        cyberCyanDim = dim(cyberCyan, 0.75f)
        hotMagentaDim = dim(hotMagenta, 0.80f)
    }

    // ── SYSTEM THEME SETTINGS LOADER & PERSISTENCE ──
    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences("meet_system_theme_prefs", Context.MODE_PRIVATE)
        neonGreen = Color(prefs.getInt("neonGreen", Color(0xFF39FF66).toArgb()))
        neonGreenDim = Color(prefs.getInt("neonGreenDim", Color(0xFF2CC44F).toArgb()))
        neonGreenSubtle = Color(prefs.getInt("neonGreenSubtle", Color(0xFF186B2B).toArgb()))

        electricBlue = Color(prefs.getInt("electricBlue", Color(0xFF00D9FF).toArgb()))
        electricBlueDim = Color(prefs.getInt("electricBlueDim", Color(0xFF009EB9).toArgb()))
        electricBlueSubtle = Color(prefs.getInt("electricBlueSubtle", Color(0xFF00505E).toArgb()))

        cyberCyan = Color(prefs.getInt("cyberCyan", Color(0xFF1677FF).toArgb()))
        cyberCyanDim = Color(prefs.getInt("cyberCyanDim", Color(0xFF105CBF).toArgb()))

        hotMagenta = Color(prefs.getInt("hotMagenta", Color(0xFF8255FF).toArgb()))
        hotMagentaDim = Color(prefs.getInt("hotMagentaDim", Color(0xFF6844CC).toArgb()))
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
        neonGreen = Color(0xFF39FF66)
        neonGreenDim = Color(0xFF2CC44F)
        neonGreenSubtle = Color(0xFF186B2B)
        electricBlue = Color(0xFF00D9FF)
        electricBlueDim = Color(0xFF009EB9)
        electricBlueSubtle = Color(0xFF00505E)
        cyberCyan = Color(0xFF1677FF)
        cyberCyanDim = Color(0xFF105CBF)
        hotMagenta = Color(0xFF8255FF)
        hotMagentaDim = Color(0xFF6844CC)
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
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val repository = androidx.compose.runtime.remember(context) {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            context, com.elysium369.meet.ui.home.HomeVisualThemeEntryPoint::class.java
        ).homeExperienceRepository()
    }
    val experience = repository.selectedExperience.collectAsState().value
    val isWhite = experience == com.elysium369.meet.ui.home.HomeExperience.MAIKEL_BLANCO
    MeetColors.isWhiteTheme = isWhite

    val dynamicColorScheme = darkColorScheme(
        primary = MeetColors.neonGreen,
        onPrimary = if (MeetColors.neonGreen.luminance() > 0.179f) MeetColors.backgroundDeep else MeetColors.textPrimary,
        primaryContainer = MeetColors.neonGreenSubtle,
        secondary = MeetColors.electricBlue,
        onSecondary = if (MeetColors.electricBlue.luminance() > 0.179f) MeetColors.backgroundDeep else MeetColors.textPrimary,
        tertiary = MeetColors.cyberCyan,
        onTertiary = if (MeetColors.cyberCyan.luminance() > 0.179f) MeetColors.backgroundDeep else MeetColors.textPrimary,
        background = if (isWhite) Color.White else Color(0xFF050B15),
        surface = Color(0xFF0F1B30),
        surfaceVariant = Color(0xFF152640),
        surfaceContainerHighest = Color(0xFF1A3050),
        surfaceContainerHigh = Color(0xFF152B48),
        surfaceContainer = Color(0xFF112240),
        surfaceContainerLow = Color(0xFF0D1C35),
        surfaceContainerLowest = Color(0xFF08142A),
        error = MeetColors.error,
        errorContainer = Color(0xFF3D0012),
        onBackground = if (isWhite) Color(0xFF050B15) else MeetColors.textPrimary,
        onSurface = MeetColors.textPrimary,
        onSurfaceVariant = MeetColors.textSecondary,
        outline = Color(0xFF1E3355),
        outlineVariant = Color(0xFF152640),
        surfaceTint = MeetColors.secondary,
        secondaryContainer = MeetColors.secondary.copy(alpha = 0.20f),
        onSecondaryContainer = MeetColors.textPrimary,
        tertiaryContainer = MeetColors.tertiary.copy(alpha = 0.20f),
        onTertiaryContainer = MeetColors.textPrimary,
        inversePrimary = MeetColors.primary,
        inverseSurface = MeetColors.neonGreen,
        inverseOnSurface = MeetColors.backgroundDeep
    )
    CompositionLocalProvider(
        LocalOverscrollFactory provides null,
        com.elysium369.meet.ui.home.LocalHomeExperience provides experience
    ) {
        MaterialTheme(
            colorScheme = dynamicColorScheme,
            typography = MeetTypography,
            content = content
        )
    }
}
