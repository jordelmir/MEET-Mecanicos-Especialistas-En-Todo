package com.elysium369.meet.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.elysium369.meet.ui.theme.MeetColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin


// ═══════════════════════════════════════════════════════════════
// ELITE UI V2 — Phantom Carbon Components
// ═══════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════
// HOLOGRAPHIC BACKGROUND FOR ALL SCREENS
// ═══════════════════════════════════════════════════════════════

private data class HoloParticleShared(
    val xSeed: Float,
    val ySeed: Float,
    val speed: Float,
    val size: Float,
    val colorAlpha: Float,
    val horizontalDrift: Float
)

private val sharedBackgroundParticles = List(30) { index ->
    HoloParticleShared(
        xSeed = (index * 0.17f) % 1.0f,
        ySeed = (index * 0.23f) % 1.0f,
        speed = 0.012f + (index * 0.005f) % 0.025f,
        size = 1f + (index % 3) * 0.8f,
        colorAlpha = 0.06f + (index % 4) * 0.03f,
        horizontalDrift = -0.05f + (index * 0.03f) % 0.1f
    )
}

@Composable
fun HolographicBackgroundShared(
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    var phase by remember { mutableFloatStateOf(0f) }
    var glowPulse by remember { mutableFloatStateOf(0.42f) }

    LaunchedEffect(animated) {
        if (!animated) {
            phase = 0f
            glowPulse = 0.42f
            return@LaunchedEffect
        }
        val startedAt = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - startedAt
            phase = (elapsed % 24000L) / 24000f
            val wave = ((sin(((elapsed % 6000L) / 6000f) * 2f * PI.toFloat()) + 1f) / 2f)
            glowPulse = 0.30f + (wave * 0.25f)
            kotlinx.coroutines.delay(220L)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Ambient glow orbs
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    MeetColors.neonGreen.copy(alpha = 0.04f * glowPulse),
                    Color.Transparent
                ),
                center = Offset(w * (0.3f + phase * 0.1f), h * 0.25f),
                radius = w * 0.6f
            ),
            radius = w * 0.6f,
            center = Offset(w * (0.3f + phase * 0.1f), h * 0.25f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    MeetColors.electricBlue.copy(alpha = 0.03f * glowPulse),
                    Color.Transparent
                ),
                center = Offset(w * (0.7f - phase * 0.1f), h * 0.7f),
                radius = w * 0.5f
            ),
            radius = w * 0.5f,
            center = Offset(w * (0.7f - phase * 0.1f), h * 0.7f)
        )

        // Subtle grid lines
        val gridSpacing = 45.dp.toPx()
        val gridAlpha = 0.015f
        val gridColor = MeetColors.neonGreen.copy(alpha = gridAlpha)
        var y = 0f
        while (y < h) {
            drawLine(gridColor, Offset(0f, y), Offset(w, y), 0.5f)
            y += gridSpacing
        }
        var x = 0f
        while (x < w) {
            drawLine(gridColor, Offset(x, 0f), Offset(x, h), 0.5f)
            x += gridSpacing
        }

        // Drifting particles
        sharedBackgroundParticles.take(10).forEach { p ->
            val px = ((p.xSeed * w) + (phase * p.speed * w) + (p.horizontalDrift * w * sin(phase * 2 * PI.toFloat()))) % w
            val py = ((p.ySeed * h) - (phase * p.speed * h)) % h
            
            val finalX = if (px < 0) px + w else px
            val finalY = if (py < 0) py + h else py

            drawCircle(
                color = MeetColors.neonGreen.copy(alpha = p.colorAlpha * glowPulse * 1.5f),
                radius = p.size.dp.toPx(),
                center = Offset(finalX, finalY)
            )
        }

        // Horizontal scan line
        val scanY = h * phase
        drawLine(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.2f to MeetColors.cyberCyan.copy(alpha = 0.04f),
                0.5f to MeetColors.cyberCyan.copy(alpha = 0.10f),
                0.8f to MeetColors.cyberCyan.copy(alpha = 0.04f),
                1f to Color.Transparent
            ),
            start = Offset(0f, scanY),
            end = Offset(w, scanY),
            strokeWidth = 1.5f
        )
    }
}

/**
 * Elite Card — Premium glassmorphism card with dynamic 3D tilt, sweep borders, and corner brackets.
 */
@Composable
fun EliteCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = MeetColors.cardBackground,
    borderColor: Color = MeetColors.borderSubtle,
    glowColor: Color? = null,
    enableHolo3D: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val accentColor = glowColor ?: MeetColors.neonGreen.copy(alpha = 0.5f)

    var rotX = 0f
    var rotY = 0f
    var translationYAnim = 0f
    if (enableHolo3D) {
        val infiniteTransition = rememberInfiniteTransition(label = "eliteCardHolo")
        val animatedRotX by infiniteTransition.animateFloat(
            initialValue = -1.0f, targetValue = 1.0f,
            animationSpec = infiniteRepeatable(tween(6200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "rotX"
        )
        val animatedRotY by infiniteTransition.animateFloat(
            initialValue = -1.2f, targetValue = 1.2f,
            animationSpec = infiniteRepeatable(tween(7600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "rotY"
        )
        val animatedTranslationY by infiniteTransition.animateFloat(
            initialValue = -1.5f, targetValue = 1.5f,
            animationSpec = infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "transY"
        )
        rotX = animatedRotX
        rotY = animatedRotY
        translationYAnim = animatedTranslationY
    }

    val cardModifier = modifier
        .then(
            if (enableHolo3D) Modifier.graphicsLayer {
                rotationX = rotX
                rotationY = rotY
                translationY = translationYAnim
                cameraDistance = 12f * density
            } else Modifier
        )
        // Ambient shadows for 3D depth
        .shadow(
            elevation = 12.dp,
            shape = shape,
            ambientColor = accentColor.copy(alpha = 0.2f),
            spotColor = accentColor.copy(alpha = 0.1f)
        )
        .shadow(
            elevation = 4.dp,
            shape = shape,
            ambientColor = accentColor.copy(alpha = 0.3f),
            spotColor = accentColor.copy(alpha = 0.2f)
        )
        .clip(shape)
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    backgroundColor.copy(alpha = 0.88f),
                    backgroundColor.copy(alpha = 0.75f),
                    backgroundColor.copy(alpha = 0.65f)
                )
            )
        )
        .drawBehind {
            // Draw custom corner markings and animated border sweep
            val resolvedCornerRadius = when (shape) {
                is RoundedCornerShape -> {
                    val resolved = shape.topStart.toPx(size, this)
                    CornerRadius(resolved, resolved)
                }
                else -> CornerRadius(16.dp.toPx(), 16.dp.toPx())
            }
            
            // Sweep border gradient
            val sweepColors = listOf(
                accentColor.copy(alpha = 0.6f),
                accentColor.copy(alpha = 0.15f),
                Color.Transparent,
                Color.Transparent,
                accentColor.copy(alpha = 0.15f),
                accentColor.copy(alpha = 0.6f)
            )
            drawRoundRect(
                brush = Brush.sweepGradient(
                    colors = sweepColors,
                    center = Offset(size.width / 2, size.height / 2)
                ),
                cornerRadius = resolvedCornerRadius,
                style = Stroke(width = 1.2f)
            )

            // Inner gradient top-glow
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0f to accentColor.copy(alpha = 0.15f),
                    0.25f to Color.Transparent
                ),
                cornerRadius = resolvedCornerRadius,
                size = Size(size.width, size.height * 0.3f)
            )

            // Cyber corner markings (only if 3D hologram style is active)
            if (enableHolo3D) {
                val markerLen = 8.dp.toPx()
                val pad = 2.dp.toPx()
                val w = size.width
                val h = size.height

                // Top-Left
                drawLine(accentColor.copy(alpha = 0.6f), Offset(pad, pad), Offset(pad + markerLen, pad), strokeWidth = 1.5f)
                drawLine(accentColor.copy(alpha = 0.6f), Offset(pad, pad), Offset(pad, pad + markerLen), strokeWidth = 1.5f)

                // Top-Right
                drawLine(accentColor.copy(alpha = 0.6f), Offset(w - pad, pad), Offset(w - pad - markerLen, pad), strokeWidth = 1.5f)
                drawLine(accentColor.copy(alpha = 0.6f), Offset(w - pad, pad), Offset(w - pad, pad + markerLen), strokeWidth = 1.5f)

                // Bottom-Left
                drawLine(accentColor.copy(alpha = 0.6f), Offset(pad, h - pad), Offset(pad + markerLen, h - pad), strokeWidth = 1.5f)
                drawLine(accentColor.copy(alpha = 0.6f), Offset(pad, h - pad), Offset(pad, h - pad - markerLen), strokeWidth = 1.5f)

                // Bottom-Right
                drawLine(accentColor.copy(alpha = 0.6f), Offset(w - pad, h - pad), Offset(w - pad - markerLen, h - pad), strokeWidth = 1.5f)
                drawLine(accentColor.copy(alpha = 0.6f), Offset(w - pad, h - pad), Offset(w - pad, h - pad - markerLen), strokeWidth = 1.5f)
            }
        }
        .border(
            width = 0.5.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.2f),
                    accentColor.copy(alpha = 0.05f),
                    accentColor.copy(alpha = 0.2f)
                )
            ),
            shape = shape
        )
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)

    Box(
        modifier = cardModifier,
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}


/**
 * Elite Button — Solid premium button with glow and gradient.
 */
@Composable
fun EliteButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MeetColors.neonGreen,
    textColor: Color = MeetColors.backgroundDeep,
    isEnabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = isEnabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .height(52.dp)
            .then(
                if (isEnabled) Modifier.neonGlow(
                    color, RoundedCornerShape(14.dp),
                    minElevation = 4f, maxElevation = 14f,
                    minAlpha = 0.3f, maxAlpha = 0.6f
                ) else Modifier
            )
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isEnabled) Brush.horizontalGradient(
                    colors = listOf(color, color.copy(alpha = 0.8f))
                ) else Brush.horizontalGradient(
                    colors = listOf(color.copy(alpha = 0.3f), color.copy(alpha = 0.2f))
                )
            ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            text = text.uppercase(),
            color = if (isEnabled) textColor else MeetColors.textSecondary,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/**
 * Elite Outlined Button — Ghost button with animated border glow.
 */
@Composable
fun EliteOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MeetColors.neonGreen,
    isEnabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = isEnabled,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = color,
            disabledContentColor = color.copy(alpha = 0.4f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isEnabled) Brush.horizontalGradient(
                listOf(color.copy(alpha = 0.8f), color.copy(alpha = 0.3f), color.copy(alpha = 0.8f))
            ) else Brush.horizontalGradient(
                listOf(color.copy(alpha = 0.3f), color.copy(alpha = 0.15f))
            )
        ),
        modifier = modifier
            .height(52.dp)
            .then(
                if (isEnabled) Modifier.neonGlow(
                    color.copy(alpha = 0.4f), RoundedCornerShape(14.dp),
                    minElevation = 1f, maxElevation = 6f,
                    minAlpha = 0.05f, maxAlpha = 0.2f
                ) else Modifier
            ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            text = text.uppercase(),
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
fun EliteTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MeetColors.electricBlue,
    isEnabled: Boolean = true
) {
    TextButton(onClick = onClick, enabled = isEnabled, modifier = modifier) {
        Text(text = text, color = if (isEnabled) color else MeetColors.textMuted, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EliteIconButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glowColor: Color? = null,
    isEnabled: Boolean = true
) {
    IconButton(
        onClick = onClick, enabled = isEnabled,
        modifier = modifier.then(
            if (glowColor != null && isEnabled) Modifier.neonGlow(
                glowColor, RoundedCornerShape(50),
                minElevation = 2f, maxElevation = 6f,
                minAlpha = 0.1f, maxAlpha = 0.25f
            ) else Modifier
        )
    ) { icon() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EliteTopAppBar(
    title: Any,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    backgroundColor: Color = MeetColors.backgroundDeep
) {
    TopAppBar(
        title = {
            Column {
                when (title) {
                    is AnnotatedString -> {
                        Text(title, fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleLarge)
                    }
                    is String -> {
                        Text(title, color = Color.White, fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleLarge)
                    }
                }
                if (subtitle != null) {
                    Text(subtitle, color = MeetColors.neonGreen, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        },
        navigationIcon = {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Text("←", color = MeetColors.neonGreen, style = MaterialTheme.typography.headlineMedium)
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
    )
}

@Composable
fun EliteDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = "ACEPTAR",
    dismissText: String = "CANCELAR",
    isDestructive: Boolean = false
) {
    val accentColor = if (isDestructive) MeetColors.error else MeetColors.neonGreen

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = MeetColors.backgroundDeep.copy(alpha = 0.97f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .neonGlow(accentColor, RoundedCornerShape(20.dp),
                        minElevation = 6f, maxElevation = 20f,
                        minAlpha = 0.15f, maxAlpha = 0.4f)
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            listOf(accentColor.copy(alpha = 0.5f), accentColor.copy(alpha = 0.15f))
                        ),
                        RoundedCornerShape(20.dp)
                    )
                    .clickable(enabled = false) {}
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(title.uppercase(), color = accentColor, fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleLarge, letterSpacing = 1.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(message, color = Color.White, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(32.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(
                            onClick = onDismiss, modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.textSecondary.copy(alpha = 0.5f))
                        ) { Text(dismissText, color = MeetColors.textSecondary, fontWeight = FontWeight.Bold) }
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f)
                                .neonGlow(accentColor, RoundedCornerShape(12.dp),
                                    minElevation = 4f, maxElevation = 10f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                        ) {
                            Text(confirmText, color = MeetColors.backgroundDeep, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Phantom Section Header — Consistent header across all screens.
 */
@Composable
fun PhantomSectionHeader(
    label: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MeetColors.neonGreen
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .background(
                    Brush.verticalGradient(listOf(accentColor, accentColor.copy(alpha = 0.2f))),
                    RoundedCornerShape(2.dp)
                )
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label.uppercase(),
            color = accentColor.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
    }
}
