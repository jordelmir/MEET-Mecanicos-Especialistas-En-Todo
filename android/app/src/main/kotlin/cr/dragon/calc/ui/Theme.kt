package cr.dragon.calc.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Elysium Vanguard — Dragon Theme
 *
 * Sistema de diseño Chroma para Jetpack Compose.
 * Integra el color scheme oscuro OLED + tipografía JetBrains Mono
 * definida en Type.kt.
 */

@Composable
fun DragonTheme(content: @Composable () -> Unit) {
    val isWhite = com.elysium369.meet.ui.theme.MeetColors.isWhiteTheme
    val scheme = darkColorScheme(
        primary = DragonTeal,
        secondary = DragonCyan,
        tertiary = DragonOrange,
        background = if (isWhite) androidx.compose.ui.graphics.Color.White else DragonBlack,
        surface = DragonDarkGray,
        surfaceVariant = DragonMidGray,
        onPrimary = DragonWhite,
        onSecondary = DragonBlack,
        onBackground = DragonWhite,
        onSurface = DragonWhite,
        onSurfaceVariant = DragonGray,
        error = DragonOrange,
        onError = DragonWhite
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = DragonTypography,  // Importado de Type.kt (JetBrains Mono)
        content = content
    )
}
