package com.elysium369.meet.ui.elysium.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.elysium369.meet.ui.theme.MeetColors

/** Typed visual tokens. Brand energy changes independently of safety semantics and metal. */
@Immutable
data class ElysiumPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val quaternary: Color,
    val background: Color,
    val backgroundElevated: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
)

/** Canonical semantic API; MeetColors remains the compatibility bridge for existing renderers. */
object ElysiumTheme {
    val colors: ElysiumPalette
        @Composable get() = ElysiumPalette(
            primary = MeetColors.primary,
            secondary = MeetColors.secondary,
            tertiary = MeetColors.tertiary,
            quaternary = MeetColors.quaternary,
            background = MeetColors.backgroundDeep,
            backgroundElevated = MeetColors.backgroundDark,
            surface = MeetColors.cardBackground,
            surfaceElevated = MeetColors.cardBackgroundLighter,
            textPrimary = MeetColors.textPrimary,
            textSecondary = MeetColors.textSecondary,
            textMuted = MeetColors.textMuted,
            success = MeetColors.success,
            warning = MeetColors.warning,
            error = MeetColors.error,
            info = Color(0xFF00D9FF),
        )
}
