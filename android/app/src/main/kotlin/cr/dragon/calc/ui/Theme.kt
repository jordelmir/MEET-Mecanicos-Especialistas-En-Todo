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

private val DragonColorScheme = darkColorScheme(
    primary = DragonTeal,
    secondary = DragonCyan,
    tertiary = DragonOrange,
    background = DragonBlack,
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

@Composable
fun DragonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DragonColorScheme,
        typography = DragonTypography,  // Importado de Type.kt (JetBrains Mono)
        content = content
    )
}
