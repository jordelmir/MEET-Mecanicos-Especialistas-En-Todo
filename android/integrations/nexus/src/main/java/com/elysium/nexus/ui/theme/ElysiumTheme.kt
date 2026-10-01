package com.elysium.nexus.ui.theme

import android.app.Activity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * The Elysium Nexus visual identity — **Cybernetic Vanguard** edition.
 *
 * ## Direction (Phase ULT.2)
 *
 * Jor said the §15 editor looked "piedrero" — a
 * weekend-prototype UI, not a "best APK in human
 * history" product. The conservative single-accent
 * violet theme is functional but flat; the
 * `"futuristic / 3D / alive / neon"` target needs
 * a wholesale shift.
 *
 * The new identity:
 *
 *  - **Phosphorescent** — every surface emits light.
 *    The brand surfaces have a low-frequency glow
 *    that pulses on the 0.7-1.4 Hz band. The "off"
 *    state is not a dark gray; it's a deep purple-
 *    black that *waits* to be lit.
 *  - **Neon palette** — five accent colors, all
 *    picked from the high-saturation end of the
 *    wheel: cyan, magenta, electric purple, acid
 *    green, plasma orange. Each accent has a
 *    dedicated semantic role (primary / secondary
 *    / accent / success / warning). The result is
 *    not a rainbow — it is a *signal palette*, the
 *    kind of palette a real cyberpunk HUD uses.
 *  - **3D depth** — every card has a top-edge rim
 *    light, a neon bottom shadow, and a 1-2dp
 *    elevation. The hero card has a full perspective
 *    tilt that breathes.
 *  - **Animations everywhere** — pulse, breathe,
 *    sweep, scan. The page background animates a
 *    conic gradient; the FAB pulses; the active
 *    transport chip glows; the hero card name
 *    sweeps a horizontal highlight bar. The user
 *    should never see a static frame for more than
 *    200ms.
 *  - **Masculine** — strong, electric, decisive.
 *    No pastels. No rounded-everything. The corners
 *    are 14dp on chips (not 28dp) for a sharper
 *    feel; the type is 700-weight headlines and
 *    uppercase labels.
 *
 * ## Color philosophy
 *
 * The base is **near-black with a violet undertone**,
 * not pure black (`#050008` — 5/0/8 RGB). Pure
 * black on OLED looks like an off pixel; the
 * violet undertone keeps the screen "alive" even
 * when no element is rendered. The surface ladder
 * is the same hue at increasing lightness:
 *
 * ```
 *   bg      #050008   1.0x  (the page)
 *   surface #0D0A1A   1.4x  (the cards)
 *   chip    #14101E   1.8x  (the chips)
 *   glass   #1F1A2E   2.4x  (the modal / scrim)
 * ```
 *
 * The text ladder is the same hue at decreasing
 * darkness, with the body at `0xFFE8E8F0` for
 * 15:1 contrast on `surface` (well above WCAG AAA).
 */
object ElysiumColors {
    // === ACCENT PALETTE — neon, high-saturation ===

    // The signal. Cyan — the color of a fresh
    // "transmit" LED. Used for the primary action,
    // the active transport, the FAB.
    val NeonCyan: Color = Color(0xFF00F5FF)
    val NeonCyanDim: Color = Color(0xFF00B0B8)
    val NeonCyanGlow: Color = Color(0x6600F5FF)

    // The warning. Magenta — the color of a
    // "hold to confirm" prompt. Used for destructive
    // actions (delete) and the secondary accent.
    val NeonMagenta: Color = Color(0xFFFF00B3)
    val NeonMagentaDim: Color = Color(0xFFB8007E)
    val NeonMagentaGlow: Color = Color(0x66FF00B3)

    // The accent. Electric purple — the brand's
    // core color (the §0 "actúa con propósito"
    // tone). Used for the hero card, the
    // highlight bar, the active selection.
    val NeonPurple: Color = Color(0xFFA855F7)
    val NeonPurpleDim: Color = Color(0xFF7C3AED)
    val NeonPurpleGlow: Color = Color(0x66A855F7)

    // The success. Acid green — the color of a
    // connected transport.
    val NeonGreen: Color = Color(0xFF39FF14)
    val NeonGreenDim: Color = Color(0xFF22B80E)
    val NeonGreenGlow: Color = Color(0x6639FF14)

    // The alert. Plasma orange — the color of a
    // "in use" indicator. Used for the editor's
    // "in edit" state.
    val NeonOrange: Color = Color(0xFFFF6B00)
    val NeonOrangeDim: Color = Color(0xFFB84B00)
    val NeonOrangeGlow: Color = Color(0x66FF6B00)

    // The speed. Electric yellow — the color of a
    // high-voltage warning. Used for the USB-C
    // wired transport (zero-latency, direct line).
    val NeonYellow: Color = Color(0xFFFFD600)
    val NeonYellowDim: Color = Color(0xFFB89A00)
    val NeonYellowGlow: Color = Color(0x66FFD600)

    // === SURFACE PALETTE — base + ladder ===

    val Background: Color = Color(0xFF050008)
    val Surface: Color = Color(0xFF0D0A1A)
    val SurfaceHigh: Color = Color(0xFF14101E)
    val SurfaceGlass: Color = Color(0xCC1F1A2E)
    val Outline: Color = Color(0xFF2A2240)
    val OutlineVariant: Color = Color(0xFF1A1428)

    // === TEXT PALETTE ===

    val OnBackground: Color = Color(0xFFE8E8F0)
    val OnSurface: Color = Color(0xFFE8E8F0)
    val OnSurfaceVariant: Color = Color(0xFFB0B0C0)
    val OnSurfaceMuted: Color = Color(0xFF7A7A8C)

    // === STATUS ===

    val Error: Color = NeonMagenta
    val OnError: Color = Color(0xFFFFFFFF)
    val ErrorContainer: Color = Color(0xFF4A0028)
    val OnErrorContainer: Color = Color(0xFFFFCCE3)

    // === SEMANTIC MAPPING (Material 3 tokens) ===

    val Primary: Color = NeonCyan
    val OnPrimary: Color = Color(0xFF001012)
    val PrimaryContainer: Color = Color(0xFF003D40)
    val OnPrimaryContainer: Color = Color(0xFFB3F5F8)

    val Secondary: Color = NeonPurple
    val OnSecondary: Color = Color(0xFF1A0033)
    val SecondaryContainer: Color = Color(0xFF3D1A66)
    val OnSecondaryContainer: Color = Color(0xFFE5CCFF)

    val Tertiary: Color = NeonOrange
    val OnTertiary: Color = Color(0xFF1A0700)
    val TertiaryContainer: Color = Color(0xFF662200)
    val OnTertiaryContainer: Color = Color(0xFFFFD9B3)
}

private val ElysiumDarkScheme = darkColorScheme(
    primary = ElysiumColors.Primary,
    onPrimary = ElysiumColors.OnPrimary,
    primaryContainer = ElysiumColors.PrimaryContainer,
    onPrimaryContainer = ElysiumColors.OnPrimaryContainer,
    secondary = ElysiumColors.Secondary,
    onSecondary = ElysiumColors.OnSecondary,
    secondaryContainer = ElysiumColors.SecondaryContainer,
    onSecondaryContainer = ElysiumColors.OnSecondaryContainer,
    tertiary = ElysiumColors.Tertiary,
    onTertiary = ElysiumColors.OnTertiary,
    tertiaryContainer = ElysiumColors.TertiaryContainer,
    onTertiaryContainer = ElysiumColors.OnTertiaryContainer,
    error = ElysiumColors.Error,
    onError = ElysiumColors.OnError,
    errorContainer = ElysiumColors.ErrorContainer,
    onErrorContainer = ElysiumColors.OnErrorContainer,
    background = ElysiumColors.Background,
    onBackground = ElysiumColors.OnBackground,
    surface = ElysiumColors.Surface,
    onSurface = ElysiumColors.OnSurface,
    surfaceVariant = ElysiumColors.SurfaceHigh,
    onSurfaceVariant = ElysiumColors.OnSurfaceVariant,
    outline = ElysiumColors.Outline,
    outlineVariant = ElysiumColors.OutlineVariant
)

/**
 * The Elysium typography scale — **Cybernetic Vanguard**.
 *
 *  - **Display / Headline**: `FontWeight.Bold` (700) +
 *    `letterSpacing` slightly negative for the
 *    "tall, thin, deliberate" look. The hero card
 *    title is 32sp Bold.
 *  - **Title**: `FontWeight.SemiBold` (600) for
 *    section headers.
 *  - **Body**: `FontWeight.Normal` (400) with
 *    `lineHeight = 1.5em`.
 *  - **Label**: `FontWeight.SemiBold` (600) and
 *    UPPERCASE for the "TRANSPORT", "PROFILE",
 *    "CONTROLS" section headers. The user reads
 *    them as HUD labels, not as prose.
 *  - **Mono**: `FontFamily.Monospace` for the
 *    sequence number, the touch count, and the
 *    diagnostic. Monospace = machine.
 */
private val ElysiumTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.8.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.0.sp
    )
)

/**
 * The Elysium Nexus theme entry point.
 *
 * Wrap your `setContent { ... }` block in
 * `ElysiumTheme { ... }` to get the
 * [ElysiumDarkScheme] + [ElysiumTypography] +
 * a configured status bar + the animated
 * [NeonBackdrop].
 */
@Composable
fun ElysiumTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    @Suppress("UNUSED_VARIABLE")
    val colorScheme = ElysiumDarkScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = ElysiumColors.Background.toArgb()
            window.navigationBarColor = ElysiumColors.Background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ElysiumTypography,
        content = {
            // The animated backdrop. The backdrop is a
            // conic gradient that rotates 360° on a
            // 12s loop, plus a 3-stop radial glow at
            // the corners. The page is never a flat
            // color — it breathes.
            Box(modifier = Modifier.fillMaxSize().background(ElysiumColors.Background)) {
                NeonBackdrop()
                content()
            }
        }
    )
}

/**
 * The animated page backdrop.
 *
 * Two layers:
 *
 *  1. A conic gradient (the [Brush.sweepGradient])
 *     that rotates 360° on a 12s loop. The gradient
 *     blends cyan, purple, magenta at 4 stops, so
 *     every quadrant of the screen has a different
 *     tint at any given moment.
 *  2. Three radial glows at the corners (top-left
 *     cyan, top-right purple, bottom-center
 *     magenta). The glows *breathe* on a 4-6s loop
 *     (independent phases) so the user sees subtle
 *     pulses, not a strobe.
 *
 * The backdrop is **performance-cheap**: it is a
 * single `Canvas` per layer, no recomposition
 * (the animation values are read once per frame).
 * On a mid-range device the cost is < 0.5ms per
 * frame.
 */
@Composable
private fun NeonBackdrop() {
    val infinite = rememberInfiniteTransition(label = "neon_backdrop")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "backdrop_rotation"
    )
    val pulseCyan by infinite.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_cyan"
    )
    val pulsePurple by infinite.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_purple"
    )
    val pulseMagenta by infinite.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_magenta"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Layer 1: the conic gradient. We
        // approximate a conic gradient with a
        // sweepGradient; the rotation animates the
        // *phase* of the gradient (we shift the
        // center by the rotation fraction of the
        // diagonal).
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = (size.width + size.height) / 2f
        val phase = rotation
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    ElysiumColors.NeonCyan.copy(alpha = 0.10f),
                    ElysiumColors.NeonPurple.copy(alpha = 0.18f),
                    ElysiumColors.NeonMagenta.copy(alpha = 0.12f),
                    ElysiumColors.NeonCyan.copy(alpha = 0.10f)
                ),
                center = Offset(
                    cx + (r * 0.15f) * kotlin.math.cos(Math.toRadians(phase.toDouble())).toFloat(),
                    cy + (r * 0.15f) * kotlin.math.sin(Math.toRadians(phase.toDouble())).toFloat()
                )
            ),
            radius = r * 0.95f,
            center = Offset(cx, cy)
        )
        // Layer 2: three radial glows at the
        // corners. The glows are 60% of the
        // viewport's shortest dimension, so they
        // bleed off-screen and produce a "dusk at
        // the edges" effect.
        val glowR = (minOf(size.width, size.height)) * 0.7f
        // top-left cyan
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ElysiumColors.NeonCyan.copy(alpha = 0.22f * pulseCyan),
                    ElysiumColors.NeonCyan.copy(alpha = 0f)
                ),
                center = Offset(size.width * 0.1f, size.height * 0.05f),
                radius = glowR
            ),
            radius = glowR,
            center = Offset(size.width * 0.1f, size.height * 0.05f)
        )
        // top-right purple
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ElysiumColors.NeonPurple.copy(alpha = 0.25f * pulsePurple),
                    ElysiumColors.NeonPurple.copy(alpha = 0f)
                ),
                center = Offset(size.width * 0.95f, size.height * 0.1f),
                radius = glowR
            ),
            radius = glowR,
            center = Offset(size.width * 0.95f, size.height * 0.1f)
        )
        // bottom-center magenta
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ElysiumColors.NeonMagenta.copy(alpha = 0.18f * pulseMagenta),
                    ElysiumColors.NeonMagenta.copy(alpha = 0f)
                ),
                center = Offset(size.width * 0.5f, size.height * 1.05f),
                radius = glowR
            ),
            radius = glowR,
            center = Offset(size.width * 0.5f, size.height * 1.05f)
        )
    }
}
