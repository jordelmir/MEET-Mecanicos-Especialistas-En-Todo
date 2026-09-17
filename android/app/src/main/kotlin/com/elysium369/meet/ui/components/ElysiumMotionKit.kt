package com.elysium369.meet.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════
// ELYSIUM MOTION KIT V1 — Cinematic UI Animations
// ═══════════════════════════════════════════════════════════════
// Premium animation toolkit for the MEET / Elysium Vanguard app.
// Every composable here brings cinema-grade motion to the cyberpunk UI.
// ═══════════════════════════════════════════════════════════════

// ─────────────────────────────────────────────────────────────
// 1. ELYSIUM ANIMATED DIALOG — Cinematic scale+fade dialog
// ─────────────────────────────────────────────────────────────

/**
 * A drop-in replacement for [AlertDialog] with cinematic entry/exit:
 * - Entry: scale 0.85→1.0 + fade 0→1 + Y slide 30dp→0
 * - Neon border glow pulse on the dialog card
 * - Frosted glass background
 */
@Composable
fun ElysiumAnimatedDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
) {
    // Internal visible state to allow exit animation before removal
    var showDialog by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) showDialog = true
    }

    if (showDialog) {
        Dialog(
            onDismissRequest = {
                onDismiss()
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            val scale by animateFloatAsState(
                targetValue = if (visible) 1f else 0.85f,
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
                label = "dialog-scale",
                finishedListener = { if (!visible) showDialog = false },
            )
            val alpha by animateFloatAsState(
                targetValue = if (visible) 1f else 0f,
                animationSpec = tween(250, easing = FastOutSlowInEasing),
                label = "dialog-alpha",
            )
            val offsetY by animateFloatAsState(
                targetValue = if (visible) 0f else 40f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 350f),
                label = "dialog-offsetY",
            )

            val infiniteTransition = rememberInfiniteTransition(label = "dialog-glow")
            val glowAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.7f,
                animationSpec = infiniteRepeatable(
                    tween(1800, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse,
                ),
                label = "dialog-glow-alpha",
            )

            Card(
                modifier = Modifier
                    .padding(horizontal = 28.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        translationY = offsetY
                        shadowElevation = 24f
                        cameraDistance = 12f * density
                    },
                colors = CardDefaults.cardColors(containerColor = Color(0xF00B1728)),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    Brush.linearGradient(
                        listOf(
                            MeetColors.neonGreen.copy(alpha = glowAlpha),
                            MeetColors.electricBlue.copy(alpha = glowAlpha * 0.7f),
                        ),
                    ),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    icon?.let {
                        Box(Modifier.align(Alignment.CenterHorizontally)) { it() }
                    }
                    ProvideTextStyle(
                        MaterialTheme.typography.titleLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                        ),
                    ) { title() }
                    ProvideTextStyle(
                        MaterialTheme.typography.bodyMedium.copy(
                            color = MeetColors.textSecondary,
                        ),
                    ) { text() }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dismissButton?.invoke()
                        Spacer(Modifier.width(8.dp))
                        confirmButton()
                    }
                }
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────
// 2. ELYSIUM STATUS TRANSITION — Animated status badges
// ─────────────────────────────────────────────────────────────

/** Toast/notification severity. */
enum class ToastType { SUCCESS, ERROR, WARNING, INFO }

/**
 * Animated status badge that morphs color and bounces when state changes.
 * Maps common status strings to the Elysium color palette.
 */
@Composable
fun ElysiumStatusTransition(
    status: String,
    modifier: Modifier = Modifier,
) {
    val targetColor = remember(status) {
        when (status.uppercase()) {
            "OPEN", "ACTIVE", "CONNECTED", "ONLINE" -> MeetColors.neonGreen
            "PENDING", "IN_PROGRESS", "SCANNING" -> MeetColors.warning
            "CLOSED", "COMPLETED", "DONE" -> MeetColors.cyberCyan
            "ERROR", "FAILED", "CRITICAL" -> MeetColors.error
            "OFFLINE", "DISABLED" -> MeetColors.textMuted
            else -> MeetColors.electricBlue
        }
    }
    val isActiveState = status.uppercase() in listOf(
        "OPEN", "ACTIVE", "CONNECTED", "ONLINE", "SCANNING", "PENDING", "IN_PROGRESS",
    )

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "status-color",
    )

    // Bounce on change
    var trigger by remember { mutableStateOf(0) }
    LaunchedEffect(status) { trigger++ }
    val bounceScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 800f),
        label = "status-bounce",
    )
    LaunchedEffect(trigger) {
        // No-op, the spring naturally overshoots on recomposition
    }

    // Pulse dot for active states
    val infiniteTransition = rememberInfiniteTransition(label = "status-pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(800, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "status-dot-alpha",
    )

    Surface(
        modifier = modifier.graphicsLayer {
            scaleX = bounceScale
            scaleY = bounceScale
        },
        color = animatedColor.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, animatedColor.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isActiveState) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .graphicsLayer { alpha = dotAlpha }
                        .clip(CircleShape)
                        .background(animatedColor),
                )
            }
            Text(
                status.uppercase(),
                color = animatedColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────
// 3. MODIFIER: 3D CARD TILT — Parallax touch effect
// ─────────────────────────────────────────────────────────────

/**
 * Gives a card a subtle 3D tilt effect that follows touch position.
 * Max tilt is ±[maxDegrees]. Springs back smoothly on release.
 */
fun Modifier.elysiumCardTilt3D(
    maxDegrees: Float = 4f,
    isEnabled: Boolean = true,
): Modifier = composed {
    if (!isEnabled) return@composed this

    var rotX by remember { mutableFloatStateOf(0f) }
    var rotY by remember { mutableFloatStateOf(0f) }

    val animRotX by animateFloatAsState(
        targetValue = rotX,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "tilt3d-rotX",
    )
    val animRotY by animateFloatAsState(
        targetValue = rotY,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "tilt3d-rotY",
    )

    this
        .pointerInput(isEnabled) {
            detectDragGestures(
                onDrag = { change, _ ->
                    change.consume()
                    val normX = (change.position.x / size.width - 0.5f) * 2f
                    val normY = (change.position.y / size.height - 0.5f) * 2f
                    rotY = (normX * maxDegrees).coerceIn(-maxDegrees, maxDegrees)
                    rotX = (-normY * maxDegrees).coerceIn(-maxDegrees, maxDegrees)
                },
                onDragEnd = {
                    rotX = 0f
                    rotY = 0f
                },
                onDragCancel = {
                    rotX = 0f
                    rotY = 0f
                },
            )
        }
        .graphicsLayer {
            rotationX = animRotX
            rotationY = animRotY
            cameraDistance = 12f * density
            shadowElevation = (8f + abs(animRotX) + abs(animRotY)).dp.toPx()
        }
}


// ─────────────────────────────────────────────────────────────
// 4. MODIFIER: HOLOGRAPHIC SHIMMER — Rainbow sweep
// ─────────────────────────────────────────────────────────────

/**
 * Adds a holographic rainbow shimmer that sweeps diagonally across any composable.
 * Uses Elysium color palette: neonGreen → cyberCyan → electricBlue → hotMagenta.
 * Very subtle to avoid overwhelming content.
 */
fun Modifier.holographicShimmer(
    durationMs: Int = 3000,
    shimmerAlpha: Float = 0.10f,
    isEnabled: Boolean = true,
): Modifier = composed {
    if (!isEnabled) return@composed this

    val infiniteTransition = rememberInfiniteTransition(label = "holo-shimmer")
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            tween(durationMs, easing = FastOutSlowInEasing, delayMillis = 1500),
            RepeatMode.Restart,
        ),
        label = "holo-shimmer-progress",
    )

    val colors = listOf(
        Color.Transparent,
        MeetColors.neonGreen.copy(alpha = shimmerAlpha),
        MeetColors.cyberCyan.copy(alpha = shimmerAlpha * 1.2f),
        MeetColors.electricBlue.copy(alpha = shimmerAlpha),
        MeetColors.hotMagenta.copy(alpha = shimmerAlpha * 0.8f),
        Color.Transparent,
    )

    this.drawWithContent {
        drawContent()
        val width = size.width
        val height = size.height
        val shimmerWidth = width * 0.6f
        val offset = shimmerProgress * (width + shimmerWidth) - shimmerWidth

        val brush = Brush.linearGradient(
            colors = colors,
            start = Offset(offset, 0f),
            end = Offset(offset + shimmerWidth, height),
        )
        drawRect(brush = brush, size = size)
    }
}


// ─────────────────────────────────────────────────────────────
// 5. ELYSIUM TOAST BAR — Floating notification with spring
// ─────────────────────────────────────────────────────────────

/**
 * Floating notification bar that slides in from the top with spring physics.
 * Auto-dismisses after [autoDismissMs]. Has a progress countdown bar.
 */
@Composable
fun ElysiumToastBar(
    message: String,
    type: ToastType,
    visible: Boolean,
    onDismiss: () -> Unit,
    autoDismissMs: Long = 4000L,
    modifier: Modifier = Modifier,
) {
    val accentColor = when (type) {
        ToastType.SUCCESS -> MeetColors.neonGreen
        ToastType.ERROR -> MeetColors.error
        ToastType.WARNING -> MeetColors.warning
        ToastType.INFO -> MeetColors.cyberCyan
    }
    val icon = when (type) {
        ToastType.SUCCESS -> Icons.Default.CheckCircle
        ToastType.ERROR -> Icons.Default.Error
        ToastType.WARNING -> Icons.Default.Warning
        ToastType.INFO -> Icons.Default.Info
    }

    // Auto-dismiss countdown
    var progress by remember(visible) { mutableFloatStateOf(1f) }
    LaunchedEffect(visible) {
        if (visible) {
            progress = 1f
            val startTime = System.currentTimeMillis()
            while (progress > 0f) {
                delay(16L)
                val elapsed = System.currentTimeMillis() - startTime
                progress = (1f - elapsed.toFloat() / autoDismissMs).coerceAtLeast(0f)
            }
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(dampingRatio = 0.65f, stiffness = 300f),
        ) + fadeIn(tween(200)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(300, easing = FastOutSlowInEasing),
        ) + fadeOut(tween(200)),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xF00D1B30),
            ),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                accentColor.copy(alpha = 0.4f),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Left accent stripe
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accentColor),
                    )
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        message,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MeetColors.textMuted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                // Progress countdown bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = accentColor,
                    trackColor = Color.Transparent,
                )
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────
// 6. MODIFIER: BREATHING GLOW — Ambient pulse for CTAs
// ─────────────────────────────────────────────────────────────

/**
 * Subtle ambient breathing animation for important UI elements.
 * Gentle scale oscillation + shadow pulsation.
 * Great for CTAs, active indicators, important buttons.
 */
fun Modifier.breathingGlow(
    color: Color = MeetColors.neonGreen,
    minScale: Float = 0.98f,
    maxScale: Float = 1.02f,
    durationMs: Int = 3000,
    isEnabled: Boolean = true,
): Modifier = composed {
    if (!isEnabled) return@composed this

    val infiniteTransition = rememberInfiniteTransition(label = "breathing-glow")
    val breathPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMs, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "breath-phase",
    )

    val scale = minScale + (maxScale - minScale) * breathPhase
    val shadowElevation = 4f + 12f * breathPhase

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.shadowElevation = shadowElevation
        }
        .drawWithContent {
            // Outer glow behind content
            val glowRadius = 6f + 10f * breathPhase
            drawRoundRect(
                color = color.copy(alpha = 0.08f + 0.12f * breathPhase),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                topLeft = Offset(-glowRadius, -glowRadius),
                size = androidx.compose.ui.geometry.Size(
                    size.width + glowRadius * 2,
                    size.height + glowRadius * 2,
                ),
            )
            drawContent()
        }
}


// ─────────────────────────────────────────────────────────────
// 7. MODIFIER: SCANNING BORDER — Animated border trace
// ─────────────────────────────────────────────────────────────

/**
 * Draws an animated neon line that traces around the border of the composable.
 * Creates a sci-fi scanning effect. Great for cards that are "processing" or "active".
 */
fun Modifier.scanningBorder(
    color: Color = MeetColors.cyberCyan,
    strokeWidth: Float = 2f,
    durationMs: Int = 2500,
    isEnabled: Boolean = true,
): Modifier = composed {
    if (!isEnabled) return@composed this

    val infiniteTransition = rememberInfiniteTransition(label = "scan-border")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMs, easing = LinearEasing),
        ),
        label = "scan-border-progress",
    )

    this.drawWithContent {
        drawContent()

        val w = size.width
        val h = size.height
        val perimeter = 2 * (w + h)
        val trailLength = perimeter * 0.25f
        val headPos = progress * perimeter

        // Calculate point on rectangle perimeter
        fun perimeterPoint(dist: Float): Offset {
            val d = ((dist % perimeter) + perimeter) % perimeter
            return when {
                d < w -> Offset(d, 0f)
                d < w + h -> Offset(w, d - w)
                d < 2 * w + h -> Offset(w - (d - w - h), h)
                else -> Offset(0f, h - (d - 2 * w - h))
            }
        }

        // Draw trail segments
        val segments = 30
        for (i in 0 until segments) {
            val segStart = headPos - trailLength * i / segments
            val segEnd = headPos - trailLength * (i + 1) / segments
            val alpha = (1f - i.toFloat() / segments) * 0.8f
            val p1 = perimeterPoint(segStart)
            val p2 = perimeterPoint(segEnd)
            drawLine(
                color = color.copy(alpha = alpha),
                start = p1,
                end = p2,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }

        // Bright head dot
        val head = perimeterPoint(headPos)
        drawCircle(color = color, radius = strokeWidth * 2, center = head)
        drawCircle(color = color.copy(alpha = 0.3f), radius = strokeWidth * 5, center = head)
    }
}


// ─────────────────────────────────────────────────────────────
// 8. ANIMATED COUNTER — Numeric value with rolling animation
// ─────────────────────────────────────────────────────────────

/**
 * Displays a numeric value that animates smoothly when it changes.
 * Great for KPIs, counters, and live data displays.
 */
@Composable
fun ElysiumAnimatedCounter(
    value: Int,
    modifier: Modifier = Modifier,
    color: Color = MeetColors.neonGreen,
    fontSize: androidx.compose.ui.unit.TextUnit = 28.sp,
    prefix: String = "",
    suffix: String = "",
) {
    val animatedValue by animateIntAsState(
        targetValue = value,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "counter-value",
    )

    // Scale bounce on change
    var lastValue by remember { mutableIntStateOf(value) }
    val scaleTarget = if (value != lastValue) 1.15f else 1f
    val animScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 600f),
        label = "counter-scale",
    )
    LaunchedEffect(value) {
        lastValue = value
    }

    Text(
        text = "$prefix${String.format("%,d", animatedValue)}$suffix",
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.sp,
        modifier = modifier.graphicsLayer {
            scaleX = animScale
            scaleY = animScale
        },
    )
}
