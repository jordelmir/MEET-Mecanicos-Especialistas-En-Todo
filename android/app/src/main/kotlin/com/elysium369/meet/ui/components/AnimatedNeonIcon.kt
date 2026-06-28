package com.elysium369.meet.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class AnimatedIconPreset(val label: String) {
    AUTO("Auto por icono"),
    AXIAL_SPIN("Giro axial 3D"),
    ORBITAL_SCANNER("Orbita scanner"),
    PISTON_PULSE("Pulso piston"),
    HOLO_SCAN("Escaneo holografico"),
    IGNITION_GLITCH("Ignicion glitch")
}

@Immutable
data class AnimatedIconStyle(
    val enabled: Boolean = true,
    val preset: AnimatedIconPreset = AnimatedIconPreset.AUTO,
    val intensity: Float = 1f,
    val clockIntervalMs: Long = 96L
)

@Immutable
data class AnimatedIconClock(val phase: Float = 0f)

val LocalAnimatedIconStyle = compositionLocalOf { AnimatedIconStyle() }
val LocalAnimatedIconClock = compositionLocalOf { AnimatedIconClock() }

@Composable
fun rememberAnimatedIconStyle(context: Context): State<AnimatedIconStyle> {
    val prefs = remember(context) { context.getSharedPreferences("elysium_visual_prefs", Context.MODE_PRIVATE) }
    val state = remember { mutableStateOf(prefs.readAnimatedIconStyle()) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == ICON_PRESET_KEY || key == ICON_ENABLED_KEY || key == ICON_INTENSITY_KEY) {
                state.value = prefs.readAnimatedIconStyle()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    return state
}

@Composable
fun rememberAnimatedIconClock(style: AnimatedIconStyle): AnimatedIconClock {
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(style.enabled, style.clockIntervalMs) {
        if (!style.enabled) {
            phase = 0f
            return@LaunchedEffect
        }
        val startedAt = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - startedAt
            phase = (elapsed % 6_000L) / 6_000f
            kotlinx.coroutines.delay(style.clockIntervalMs.coerceAtLeast(64L))
        }
    }

    return AnimatedIconClock(phase)
}

fun setAnimatedIconPreset(context: Context, preset: AnimatedIconPreset) {
    context.getSharedPreferences("elysium_visual_prefs", Context.MODE_PRIVATE)
        .edit()
        .putString(ICON_PRESET_KEY, preset.name)
        .apply()
}

fun setAnimatedIconEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences("elysium_visual_prefs", Context.MODE_PRIVATE)
        .edit()
        .putBoolean(ICON_ENABLED_KEY, enabled)
        .apply()
}

fun setAnimatedIconIntensity(context: Context, intensity: Float) {
    context.getSharedPreferences("elysium_visual_prefs", Context.MODE_PRIVATE)
        .edit()
        .putFloat(ICON_INTENSITY_KEY, intensity.coerceIn(0.35f, 1.6f))
        .apply()
}

@Composable
fun AnimatedNeonIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    preset: AnimatedIconPreset = AnimatedIconPreset.AUTO
) {
    AnimatedIconFrame(
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
        preset = preset
    ) { iconModifier, iconTint ->
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = iconModifier
        )
    }
}

@Composable
fun AnimatedNeonIcon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    preset: AnimatedIconPreset = AnimatedIconPreset.AUTO
) {
    AnimatedIconFrame(
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
        preset = preset
    ) { iconModifier, iconTint ->
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = iconModifier
        )
    }
}

@Composable
fun AnimatedNeonGlyph(
    glyph: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    preset: AnimatedIconPreset = AnimatedIconPreset.AUTO,
    fontSize: TextUnit = 20.sp,
    fontWeight: FontWeight = FontWeight.Black
) {
    AnimatedIconFrame(
        contentDescription = contentDescription ?: glyph,
        modifier = modifier,
        tint = tint,
        preset = preset
    ) { iconModifier, iconTint ->
        Text(
            text = glyph,
            color = iconTint,
            fontSize = fontSize,
            fontWeight = fontWeight,
            modifier = iconModifier
        )
    }
}

@Composable
private fun AnimatedIconFrame(
    contentDescription: String?,
    modifier: Modifier,
    tint: Color,
    preset: AnimatedIconPreset,
    content: @Composable (Modifier, Color) -> Unit
) {
    val style = LocalAnimatedIconStyle.current
    val clock = LocalAnimatedIconClock.current
    val resolvedPreset = resolvePreset(
        requested = if (preset == AnimatedIconPreset.AUTO) style.preset else preset,
        identity = contentDescription.orEmpty()
    )
    val phase = if (style.enabled) ((clock.phase + seedOffset(contentDescription.orEmpty())) % 1f) else 0f
    val intensity = if (style.enabled) style.intensity else 0f
    val effectAlpha = if (style.enabled) style.intensity.coerceAtLeast(0.35f) else 0f
    val twoPi = (2f * PI).toFloat()
    val wave = ((sin(phase * twoPi) + 1f) / 2f)
    val sharp = ((sin(phase * twoPi * 3f) + 1f) / 2f)

    val rotationZ = when (resolvedPreset) {
        AnimatedIconPreset.AXIAL_SPIN -> phase * 360f
        AnimatedIconPreset.ORBITAL_SCANNER -> sin(phase * twoPi) * 7f * intensity
        AnimatedIconPreset.PISTON_PULSE -> 0f
        AnimatedIconPreset.HOLO_SCAN -> sin(phase * twoPi) * 3f * intensity
        AnimatedIconPreset.IGNITION_GLITCH -> sin(phase * twoPi * 10f) * 4f * intensity
        AnimatedIconPreset.AUTO -> 0f
    }
    val rotationY = when (resolvedPreset) {
        AnimatedIconPreset.AXIAL_SPIN -> sin(phase * twoPi) * 34f * intensity
        AnimatedIconPreset.ORBITAL_SCANNER -> cos(phase * twoPi) * 18f * intensity
        AnimatedIconPreset.HOLO_SCAN -> sin(phase * twoPi) * 14f * intensity
        else -> 0f
    }
    val translationY = when (resolvedPreset) {
        AnimatedIconPreset.PISTON_PULSE -> -2.8f * sharp * intensity
        AnimatedIconPreset.IGNITION_GLITCH -> sin(phase * twoPi * 12f) * 1.2f * intensity
        else -> 0f
    }
    val translationX = if (resolvedPreset == AnimatedIconPreset.IGNITION_GLITCH) {
        cos(phase * twoPi * 9f) * 1.5f * intensity
    } else {
        0f
    }
    val scale = when (resolvedPreset) {
        AnimatedIconPreset.PISTON_PULSE -> 0.92f + (0.16f * wave * intensity)
        AnimatedIconPreset.HOLO_SCAN -> 0.98f + (0.05f * wave * intensity)
        else -> 1f
    }
    val baseTint = if (tint == Color.Unspecified) LocalContentColor.current else tint
    val hotTint = if (style.enabled) neonTint(baseTint, phase, resolvedPreset) else baseTint

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val accent = hotTint.copy(alpha = (0.18f + wave * 0.16f) * effectAlpha)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent, Color.Transparent),
                    center = center,
                    radius = radius * 1.45f
                ),
                radius = radius * 1.45f,
                center = center
            )

            when (resolvedPreset) {
                AnimatedIconPreset.ORBITAL_SCANNER -> {
                    val orbitAngle = phase * twoPi
                    drawCircle(
                        color = hotTint.copy(alpha = 0.42f),
                        radius = radius * 0.92f,
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                    drawCircle(
                        color = MeetColors.neonGreen.copy(alpha = 0.85f),
                        radius = 2.4.dp.toPx(),
                        center = Offset(
                            center.x + cos(orbitAngle) * radius * 0.92f,
                            center.y + sin(orbitAngle) * radius * 0.92f
                        )
                    )
                }
                AnimatedIconPreset.HOLO_SCAN -> {
                    val scanY = size.height * wave
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                hotTint.copy(alpha = 0.85f),
                                Color.Transparent
                            )
                        ),
                        start = Offset(0f, scanY),
                        end = Offset(size.width, scanY),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
                AnimatedIconPreset.IGNITION_GLITCH -> {
                    drawLine(
                        color = MeetColors.warning.copy(alpha = 0.42f * sharp),
                        start = Offset(center.x - radius * 0.7f, center.y + radius * 0.7f),
                        end = Offset(center.x + radius * 0.72f, center.y - radius * 0.72f),
                        strokeWidth = 1.2.dp.toPx()
                    )
                }
                else -> Unit
            }
        }

        content(
            Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.rotationZ = rotationZ
                    this.rotationY = rotationY
                    this.translationX = translationX
                    this.translationY = translationY
                    cameraDistance = 18f * density
                    shadowElevation = 6f * intensity
                    ambientShadowColor = hotTint.copy(alpha = 0.6f)
                    spotShadowColor = hotTint.copy(alpha = 0.7f)
                },
            hotTint
        )
    }
}

private fun resolvePreset(requested: AnimatedIconPreset, identity: String): AnimatedIconPreset {
    if (requested != AnimatedIconPreset.AUTO) return requested
    val presets = listOf(
        AnimatedIconPreset.AXIAL_SPIN,
        AnimatedIconPreset.ORBITAL_SCANNER,
        AnimatedIconPreset.PISTON_PULSE,
        AnimatedIconPreset.HOLO_SCAN,
        AnimatedIconPreset.IGNITION_GLITCH
    )
    val index = kotlin.math.abs(identity.hashCode()).rem(presets.size)
    return presets[index]
}

private fun seedOffset(identity: String): Float {
    val raw = kotlin.math.abs(identity.hashCode()).rem(997)
    return raw / 997f
}

private fun neonTint(base: Color, phase: Float, preset: AnimatedIconPreset): Color {
    val pulse = ((sin(phase * 2f * PI.toFloat()) + 1f) / 2f)
    val accent = when (preset) {
        AnimatedIconPreset.AXIAL_SPIN -> MeetColors.cyberCyan
        AnimatedIconPreset.ORBITAL_SCANNER -> MeetColors.neonGreen
        AnimatedIconPreset.PISTON_PULSE -> MeetColors.warning
        AnimatedIconPreset.HOLO_SCAN -> MeetColors.electricBlue
        AnimatedIconPreset.IGNITION_GLITCH -> Color(0xFFFF3D00)
        AnimatedIconPreset.AUTO -> MeetColors.neonGreen
    }
    return Color(
        red = (base.red * 0.62f + accent.red * (0.38f + pulse * 0.18f)).coerceIn(0f, 1f),
        green = (base.green * 0.62f + accent.green * (0.38f + pulse * 0.18f)).coerceIn(0f, 1f),
        blue = (base.blue * 0.62f + accent.blue * (0.38f + pulse * 0.18f)).coerceIn(0f, 1f),
        alpha = base.alpha
    )
}

private fun SharedPreferences.readAnimatedIconStyle(): AnimatedIconStyle {
    val presetName = getString(ICON_PRESET_KEY, AnimatedIconPreset.AUTO.name)
    val preset = runCatching { AnimatedIconPreset.valueOf(presetName ?: AnimatedIconPreset.AUTO.name) }
        .getOrDefault(AnimatedIconPreset.AUTO)
    return AnimatedIconStyle(
        enabled = getBoolean(ICON_ENABLED_KEY, true),
        preset = preset,
        intensity = getFloat(ICON_INTENSITY_KEY, 1f).coerceIn(0.35f, 1.6f)
    )
}

private const val ICON_PRESET_KEY = "animated_icon_preset"
private const val ICON_ENABLED_KEY = "animated_icon_enabled"
private const val ICON_INTENSITY_KEY = "animated_icon_intensity"
