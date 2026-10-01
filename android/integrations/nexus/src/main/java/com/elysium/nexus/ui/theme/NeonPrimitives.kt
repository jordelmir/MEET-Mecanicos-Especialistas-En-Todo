package com.elysium.nexus.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Elysium **Neon Primitives** — the 3D, glowing,
 * animated building blocks the rest of the UI is
 * built on.
 *
 * Every primitive in this file has three things
 * that the §15 editor lacked in its previous
 * ("piedrero") form:
 *
 *  1. **3D depth** — at least two layered shadows:
 *     a tight dark shadow for contact, a wide
 *     colored shadow for glow. The card "lifts"
 *     off the background instead of sitting flat.
 *  2. **Neon glow** — every card / chip / FAB has
 *     a colored halo on the bottom edge, sized
 *     to be subtle but unmistakable.
 *  3. **Animation** — pulse, breathe, sweep, scale.
 *
 * The palette is the [ElysiumColors] from
 * [ElysiumTheme]. The primitives are **stateless**
 * composables — they take a color and a content
 * lambda. State lives in the caller.
 */

// =====================================================================
// === NeonCard — the 3D layered card =================================
// =====================================================================

/**
 * The 3D card primitive.
 *
 * A [NeonCard] is a rounded surface with:
 *
 *  - A thin top-edge rim light (a highlight
 *    gradient on the upper 1.5dp of the card).
 *  - A 1.5dp neon bottom border (the bottom edge
 *    has a colored stroke).
 *  - Two layered shadows:
 *     * a 12dp dark contact shadow (the "lift")
 *     * a 24dp colored glow (the "halo")
 *  - A 0.985x press scale + a brighter border
 *    on tap.
 *
 * @param accent the neon color of the bottom
 *   border + the halo.
 */
@Composable
fun NeonCard(
    modifier: Modifier = Modifier,
    accent: Color = ElysiumColors.NeonCyan,
    accentGlow: Color = accent.copy(alpha = 0.5f),
    cornerRadius: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val targetScale = if (isPressed) 0.985f else 1f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "neon_card_scale"
    )
    val shape = RoundedCornerShape(cornerRadius)

    val cardModifier = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawBehind {
            val rimHeight = 1.5.dp.toPx()
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0f)
                    ),
                    startY = 0f,
                    endY = rimHeight
                ),
                topLeft = Offset(0f, 0f),
                size = Size(size.width, rimHeight * 2.5f),
                cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
            )
            val bottomY = size.height - 1.5.dp.toPx()
            drawLine(
                color = accent,
                start = Offset(0f, bottomY),
                end = Offset(size.width, bottomY),
                strokeWidth = 1.5.dp.toPx()
            )
            val haloInset = 18.dp.toPx()
            drawRoundRect(
                color = accentGlow,
                topLeft = Offset(-haloInset, size.height - 4.dp.toPx()),
                size = Size(size.width + haloInset * 2, 28.dp.toPx()),
                cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx())
            )
        }
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    ElysiumColors.SurfaceHigh,
                    ElysiumColors.Surface
                )
            )
        )
        .border(
            width = 1.dp,
            color = if (isPressed) accent.copy(alpha = 0.8f) else ElysiumColors.Outline,
            shape = shape
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )

    Box(modifier = cardModifier) {
        Column(
            modifier = Modifier.padding(contentPadding)
        ) {
            content()
        }
    }
}

// =====================================================================
// === NeonChip — the 3D pill button ==================================
// =====================================================================

/**
 * The 3D chip primitive.
 *
 * A [NeonChip] is a small [NeonCard] with a
 * 10dp corner radius, a horizontal layout
 * (icon + label), and three states:
 *
 *  - **Default** — dark surface, dim accent
 *    border, light icon and label.
 *  - **Active** — full-strength accent border,
 *    gradient surface, accent icon and label.
 *  - **Destructive** — magenta accent, white
 *    icon and label.
 *  - **Press** — the chip scales to 0.94x and
 *    the accent border thickens.
 *
 * The chip's label is **UPPERCASE** for the HUD
 * feel.
 */
@Composable
fun NeonChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    accent: Color = ElysiumColors.NeonCyan,
    active: Boolean = false,
    destructive: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val targetScale = if (isPressed) 0.94f else 1f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 800f),
        label = "neon_chip_scale"
    )
    val effectiveAccent = when {
        destructive -> ElysiumColors.NeonMagenta
        else -> accent
    }
    val labelColor = when {
        destructive -> Color.White
        active -> effectiveAccent
        else -> ElysiumColors.OnSurface
    }
    val shape = RoundedCornerShape(10.dp)

    val surfaceBrush = if (active) {
        Brush.verticalGradient(
            colors = listOf(
                effectiveAccent.copy(alpha = 0.25f),
                ElysiumColors.SurfaceHigh
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                ElysiumColors.SurfaceHigh,
                ElysiumColors.Surface
            )
        )
    }

    val chipModifier = modifier
        .defaultMinSize(minHeight = 36.dp)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawBehind {
            val bottomY = size.height - 1.dp.toPx()
            val lineColor = if (active) effectiveAccent
                else effectiveAccent.copy(alpha = 0.5f)
            drawLine(
                color = lineColor,
                start = Offset(0f, bottomY),
                end = Offset(size.width, bottomY),
                strokeWidth = if (active) 2.dp.toPx() else 1.2.dp.toPx()
            )
            if (active) {
                val haloInset = 8.dp.toPx()
                drawRoundRect(
                    color = effectiveAccent.copy(alpha = 0.4f),
                    topLeft = Offset(-haloInset, size.height - 2.dp.toPx()),
                    size = Size(size.width + haloInset * 2, 18.dp.toPx()),
                    cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx())
                )
            }
        }
        .clip(shape)
        .background(surfaceBrush)
        .border(
            width = if (active) 1.2.dp else 1.dp,
            color = if (active) effectiveAccent.copy(alpha = 0.7f)
                else ElysiumColors.Outline,
            shape = shape
        )
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
        .padding(horizontal = 12.dp, vertical = 6.dp)

    Row(
        modifier = chipModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) {
            Box(modifier = Modifier.size(16.dp)) {
                CompositionLocalProvider(LocalContentColor provides labelColor) {
                    icon()
                }
            }
        }
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = labelColor
            )
        )
    }
}

// =====================================================================
// === NeonFab — the pulsing action button =============================
// =====================================================================

/**
 * The floating action button.
 *
 * A [NeonFab] is a 60dp circle with:
 *
 *  - A radial gradient (center = `accent` at 100%
 *    alpha, edge = `accent` at 70% alpha).
 *  - A pulsing scale animation (0.95x ↔ 1.08x on
 *    a 1.4s loop).
 *  - A pulsing halo behind the circle (alpha
 *    0.3x ↔ 0.7x on the same loop).
 *  - A 0.92x press scale.
 *  - A top-edge rim light arc.
 */
@Composable
fun NeonFab(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = ElysiumColors.NeonCyan,
    fabSize: Dp = 60.dp
) {
    val infinite = rememberInfiniteTransition(label = "neon_fab_pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fab_pulse"
    )
    val haloPulse by infinite.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fab_halo"
    )
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale = if (isPressed) 0.92f else 1f
    val pressScaleAnim by animateFloatAsState(
        targetValue = pressScale,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "neon_fab_press"
    )
    val shape = RoundedCornerShape(50)

    val fabModifier = modifier
        .size(fabSize)
        .graphicsLayer {
            scaleX = pulse * pressScaleAnim
            scaleY = pulse * pressScaleAnim
        }
        .drawBehind {
            val haloWidth = 18.dp.toPx()
            val canvasSize = this.size
            val radius = (canvasSize.width / 2f)
            drawCircle(
                color = accent.copy(alpha = haloPulse),
                radius = radius + haloWidth
            )
            val cx = canvasSize.width / 2f
            val cy = canvasSize.height / 2f
            val r = canvasSize.width / 2f
            drawArc(
                color = Color.White.copy(alpha = 0.4f),
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = 2.dp.toPx())
            )
        }
        .clip(shape)
        .background(
            brush = Brush.radialGradient(
                colors = listOf(
                    accent.copy(alpha = 1f),
                    accent.copy(alpha = 0.7f)
                )
            )
        )
        .border(
            width = 1.5.dp,
            color = Color.White.copy(alpha = 0.4f),
            shape = shape
        )
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )

    Box(
        modifier = fabModifier,
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.Black) {
            icon()
        }
    }
}

// =====================================================================
// === NeonHeroCard — the top "profile" card ===========================
// =====================================================================

/**
 * The hero card. The big, prominent card at the
 * top of the main screen.
 *
 * The [NeonHeroCard] is a [NeonCard] with:
 *
 *  - A 1.025x scale on the breathing pulse
 *    (1.00x ↔ 1.025x on a 3.4s loop, gentle).
 *  - A horizontal highlight bar that sweeps
 *    across the card on a 4.2s loop. The bar is
 *    a translucent white gradient that moves
 *    from left to right.
 *  - A 1.5° rotateY tilt on press.
 *  - A small "ELYSIUM NEXUS" eyebrow text + the
 *    title + an optional subtitle + a row of
 *    status chips.
 */
@Composable
fun NeonHeroCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    accent: Color = ElysiumColors.NeonPurple,
    statusChips: @Composable (RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val infinite = rememberInfiniteTransition(label = "neon_hero")
    val breathe by infinite.animateFloat(
        initialValue = 1.00f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hero_breathe"
    )
    val sweep by infinite.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hero_sweep"
    )
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressTilt by animateFloatAsState(
        targetValue = if (isPressed) 1.5f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "neon_hero_tilt"
    )

    val heroShape = RoundedCornerShape(20.dp)
    val heroModifier = modifier
        .graphicsLayer {
            scaleX = breathe
            scaleY = breathe
            rotationY = pressTilt
        }
        .drawBehind {
            val bottomY = size.height - 2.dp.toPx()
            drawLine(
                color = accent,
                start = Offset(0f, bottomY),
                end = Offset(size.width, bottomY),
                strokeWidth = 2.5.dp.toPx()
            )
            val haloInset = 24.dp.toPx()
            drawRoundRect(
                color = accent.copy(alpha = 0.45f),
                topLeft = Offset(-haloInset, size.height - 6.dp.toPx()),
                size = Size(size.width + haloInset * 2, 40.dp.toPx()),
                cornerRadius = CornerRadius(40.dp.toPx(), 40.dp.toPx())
            )
            val rimHeight = 2.5.dp.toPx()
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.42f),
                        Color.White.copy(alpha = 0f)
                    ),
                    startY = 0f,
                    endY = rimHeight
                ),
                topLeft = Offset(0f, 0f),
                size = Size(size.width, rimHeight * 3f),
                cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx())
            )
            val sweepX = size.width * sweep
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0f),
                        Color.White.copy(alpha = 0.18f),
                        Color.White.copy(alpha = 0f)
                    ),
                    startX = sweepX - 30.dp.toPx(),
                    endX = sweepX + 30.dp.toPx()
                ),
                topLeft = Offset(0f, 0f),
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx())
            )
        }
        .clip(heroShape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    accent.copy(alpha = 0.18f),
                    ElysiumColors.SurfaceHigh,
                    ElysiumColors.Surface
                )
            )
        )
        .border(
            width = 1.5.dp,
            color = accent.copy(alpha = 0.6f),
            shape = heroShape
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )
        .padding(horizontal = 16.dp, vertical = 12.dp)

    Column(modifier = heroModifier) {
        Text(
            text = "ELYSIUM NEXUS",
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                color = accent,
                shadow = Shadow(
                    color = accent.copy(alpha = 0.8f),
                    blurRadius = 12f
                )
            )
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            style = TextStyle(
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp,
                color = ElysiumColors.OnSurface,
                shadow = Shadow(
                    color = accent.copy(alpha = 0.6f),
                    blurRadius = 14f
                )
            ),
            maxLines = 1
        )
        if (subtitle.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                ),
                color = ElysiumColors.OnSurfaceVariant,
                maxLines = 1
            )
        }
        if (statusChips != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                statusChips()
            }
        }
    }
}

// =====================================================================
// === NeonStatusDot — the breathing status dot ========================
// =====================================================================

/**
 * A small (8dp) circle that pulses opacity
 * 0.5x ↔ 1.0x on a 1.6s loop. Used for the
 * "this transport is connected" indicator.
 */
@Composable
fun NeonStatusDot(
    modifier: Modifier = Modifier,
    color: Color = ElysiumColors.NeonGreen,
    dotSize: Dp = 8.dp
) {
    val infinite = rememberInfiniteTransition(label = "neon_status_dot")
    val alpha by infinite.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "status_dot_alpha"
    )
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "status_dot_glow"
    )
    Box(
        modifier = modifier
            .size(dotSize + 8.dp)
            .drawBehind {
                val canvasSize = this.size
                val radius = (canvasSize.width / 2f) - 4.dp.toPx()
                drawCircle(
                    color = color.copy(alpha = glowAlpha),
                    radius = radius + 4.dp.toPx()
                )
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = radius
                )
            }
    )
}

// =====================================================================
// === NeonSectionHeader — the UPPERCASE section label =================
// =====================================================================

/**
 * The section header. A small UPPERCASE label
 * with a 2px colored underline.
 */
@Composable
fun NeonSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = ElysiumColors.NeonCyan
) {
    Column(modifier = modifier) {
        Text(
            text = text.uppercase(),
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = accent
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(2.dp)
                .background(accent)
        )
    }
}

// =====================================================================
// === NeonEmptyState — the "0 controls" call to action =================
// =====================================================================

/**
 * The empty state. A large call-to-action shown
 * when the profile has zero controls.
 */
@Composable
fun NeonEmptyState(
    title: String,
    body: String,
    cta: String,
    onCta: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = ElysiumColors.NeonCyan
) {
    val infinite = rememberInfiniteTransition(label = "neon_empty_state")
    val pulse by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "empty_pulse"
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer { scaleX = pulse; scaleY = pulse }
                .drawBehind {
                    val haloR = 60.dp.toPx()
                    drawCircle(
                        color = accent.copy(alpha = 0.35f),
                        radius = haloR
                    )
                    drawCircle(
                        color = accent.copy(alpha = 0.18f),
                        radius = haloR * 1.5f
                    )
                }
                .clip(RoundedCornerShape(50))
                .background(accent.copy(alpha = 0.15f))
                .border(
                    width = 2.dp,
                    color = accent,
                    shape = RoundedCornerShape(50)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(48.dp)
            )
        }
        Text(
            text = title.uppercase(),
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                color = ElysiumColors.OnSurface
            ),
            textAlign = TextAlign.Center
        )
        Text(
            text = body,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.3.sp,
                color = ElysiumColors.OnSurfaceVariant
            ),
            textAlign = TextAlign.Center
        )
        NeonChip(
            label = cta,
            onClick = onCta,
            accent = accent,
            active = true,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) }
        )
    }
}

// =====================================================================
// === NeonStatusPill — the small inline status indicator =============
// =====================================================================

/**
 * A small inline status pill: a 3dp dot + an
 * UPPERCASE label, wrapped in a thin border.
 * Used in the hero card's status row.
 */
@Composable
fun NeonStatusPill(
    label: String,
    color: Color = ElysiumColors.NeonGreen,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.4f), shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        NeonStatusDot(color = color, dotSize = 6.dp)
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = color
            )
        )
    }
}

// =====================================================================
// === NeonIcon — the colored icon helper ==============================
// =====================================================================

/**
 * A 16-24dp icon. Used inside chips, status
 * indicators, the hero card, the transport
 * list. The icon is tinted to the
 * [LocalContentColor] (set by the parent).
 */
@Composable
fun NeonIcon(
    imageVector: ImageVector,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    iconSize: Dp = 18.dp
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(iconSize)
    )
}
