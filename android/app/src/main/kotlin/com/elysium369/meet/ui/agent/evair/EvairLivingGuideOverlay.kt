package com.elysium369.meet.ui.agent.evair

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.agent.anchor.AgentAnchorId
import com.elysium369.meet.core.agent.anchor.AgentAnchorRegistry
import com.elysium369.meet.core.agent.anchor.AnchorBounds
import com.elysium369.meet.core.agent.evair.*
import com.elysium369.meet.ui.theme.MeetColors
import kotlin.math.roundToInt

/**
 * ══════════════════════════════════════════════════════════════════════
 *  E V A I R   L I V I N G   G U I D E   O V E R L A Y
 *  ──────────────────────────────────────────────────────────────
 *  Embodied, living guide for the MEET / Elysium application.
 *  In accordance with Master Order Omega §5, §10, §11, §12 (Teach Mode):
 *
 *  - Persistent embodied entity that observes, speaks, points and teaches.
 *  - VISIBLE TOUCH INDICATOR: Shows exactly where EVAIR points and taps
 *    buttons with expanding ripples and finger motions so humans learn.
 *  - ZERO synthetic accessibility clicks. Visual guidance only.
 * ══════════════════════════════════════════════════════════════════════
 */
@Composable
fun EvairLivingGuideOverlay(
    modifier: Modifier = Modifier,
    engine: EvairTutorialEngine = EvairTutorialEngine.default,
    registry: AgentAnchorRegistry = AgentAnchorRegistry.default,
    onNavigateToAnchor: ((AgentAnchorId) -> Unit)? = null,
) {
    val guideState by engine.state.collectAsState()
    val anchors by registry.anchorsFlow.collectAsState()

    val targetAnchorBounds: AnchorBounds? = guideState.activeAnchor?.let { anchors[it] }

    Box(modifier = modifier.fillMaxSize()) {
        // ── 1. Target Button Highlight & Living Touch Pointer ──
        if (guideState.isVisible && targetAnchorBounds != null && targetAnchorBounds.isVisible) {
            EvairTargetButtonHighlight(bounds = targetAnchorBounds.boundsInRoot)

            EvairLivingTouchIndicator(
                bounds = targetAnchorBounds.boundsInRoot,
                isCelebration = guideState.avatarState == AvatarState.CELEBRATING,
                actionLabel = guideState.currentStep?.buttonActionLabel ?: "¡Toca aquí!",
            )
        }

        // ── 2. Dialogue & Speech Guidance Card ──
        if (guideState.isVisible && (guideState.speechText.isNotBlank() || guideState.celebrationText != null)) {
            val isButtonAtTop = targetAnchorBounds?.centerY?.let { it < 600f } ?: false

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                contentAlignment = if (isButtonAtTop) Alignment.BottomCenter else Alignment.TopCenter,
            ) {
                EvairDialogueCard(
                    guideState = guideState,
                    engine = engine,
                    onNext = { engine.advanceStep() },
                    onPrev = { engine.previousStep() },
                    onDismiss = { engine.dismiss() },
                )
            }
        }

        // ── 3. Floating Living EVAIR Avatar Entity ──
        EvairFloatingAvatarEntity(
            guideState = guideState,
            targetBounds = targetAnchorBounds?.boundsInRoot,
            onAvatarClick = {
                if (!guideState.isVisible) {
                    engine.startTutorial(StandardTutorials.OBD_SCANNER_GUIDE)
                } else if (guideState.avatarState == AvatarState.SLEEPING) {
                    engine.showGuideOrb()
                }
            },
        )
    }
}

/**
 * Pulsing holographic highlight around the button being taught.
 */
@Composable
private fun EvairTargetButtonHighlight(bounds: Rect) {
    val density = LocalDensity.current
    val infiniteTransition = rememberInfiniteTransition(label = "target_highlight")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    with(density) {
        val leftDp = bounds.left.toDp() - 4.dp
        val topDp = bounds.top.toDp() - 4.dp
        val widthDp = bounds.width.toDp() + 8.dp
        val heightDp = bounds.height.toDp() + 8.dp

        Box(
            modifier = Modifier
                .offset { IntOffset(leftDp.roundToPx(), topDp.roundToPx()) }
                .size(widthDp, heightDp)
                .border(
                    width = 2.dp,
                    color = MeetColors.cyberCyan.copy(alpha = pulseAlpha),
                    shape = RoundedCornerShape(10.dp),
                )
                .background(
                    color = MeetColors.cyberCyan.copy(alpha = pulseAlpha * 0.12f),
                    shape = RoundedCornerShape(10.dp),
                )
        )
    }
}

/**
 * Living Touch Indicator: Visibly demonstrates the touch/tap action right on top
 * of the target button with animated hand tap motions and expanding ripples!
 */
@Composable
private fun EvairLivingTouchIndicator(
    bounds: Rect,
    isCelebration: Boolean,
    actionLabel: String,
) {
    val density = LocalDensity.current
    val infiniteTransition = rememberInfiniteTransition(label = "touch_indicator")

    // Rhythmic tap animation (moves down to simulate tapping the button)
    val tapOffsetY by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tap_y",
    )

    // Expanding touch ripple wave
    val rippleScale by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ripple_scale",
    )
    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ripple_alpha",
    )

    with(density) {
        val centerX = bounds.left + bounds.width / 2f
        val centerY = bounds.top + bounds.height / 2f

        Box(
            modifier = Modifier
                .offset { IntOffset((centerX - 24.dp.toPx()).roundToInt(), (centerY - 24.dp.toPx()).roundToInt()) }
                .size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Ripple 1
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .scale(rippleScale)
                    .border(
                        width = 2.dp,
                        color = (if (isCelebration) MeetColors.neonGreen else MeetColors.electricBlue).copy(alpha = rippleAlpha),
                        shape = CircleShape,
                    )
            )

            // Ripple 2 (staggered)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .scale(rippleScale * 0.7f)
                    .background(
                        color = (if (isCelebration) MeetColors.neonGreen else MeetColors.cyberCyan).copy(alpha = rippleAlpha * 0.4f),
                        shape = CircleShape,
                    )
            )

            // The Animated Tapping Finger / Hand
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .graphicsLayer { translationY = tapOffsetY }
            ) {
                // Floating Action Tag
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF0F1E36),
                    border = BorderStroke(1.dp, if (isCelebration) MeetColors.neonGreen else MeetColors.cyberCyan),
                    shadowElevation = 8.dp,
                ) {
                    Text(
                        text = actionLabel,
                        color = if (isCelebration) MeetColors.neonGreen else Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                Spacer(Modifier.height(2.dp))

                // Living Holographic Pointer Icon
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = "Indicador de toque",
                    tint = if (isCelebration) MeetColors.neonGreen else MeetColors.cyberCyan,
                    modifier = Modifier
                        .size(36.dp)
                        .scale(1.1f),
                )
            }
        }
    }
}

/**
 * Dialogue Speech Card presenting EVAIR's step-by-step guidance.
 */
@Composable
private fun EvairDialogueCard(
    guideState: EvairGuideState,
    engine: EvairTutorialEngine,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isCelebration = guideState.avatarState == AvatarState.CELEBRATING
    val borderColor = if (isCelebration) MeetColors.neonGreen else MeetColors.cyberCyan

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xF00A1628)),
        border = BorderStroke(1.5.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Title & Step Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (isCelebration) MeetColors.neonGreen else MeetColors.cyberCyan, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isCelebration) "¡OBJETIVO LOGRADO!" else "EVAIR — GUÍA EN VIVO",
                        color = if (isCelebration) MeetColors.neonGreen else MeetColors.cyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                    )
                }

                // Step count indicator
                guideState.currentStep?.let { step ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.1f),
                    ) {
                        Text(
                            text = "Paso ${step.stepNumber} de ${step.totalSteps}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Title
            Text(
                text = guideState.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(6.dp))

            // Body message
            Text(
                text = guideState.celebrationText ?: guideState.speechText,
                color = if (isCelebration) MeetColors.neonGreen else Color(0xFFD1D5DB),
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )

            Spacer(Modifier.height(14.dp))

            // Controls & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Mode Toggle Button (Teach Me vs Do It)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.clickable { engine.toggleInteractionMode() },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (guideState.interactionMode == AgentInteractionMode.TEACH_ME) Icons.Default.School else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MeetColors.cyberCyan,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (guideState.interactionMode == AgentInteractionMode.TEACH_ME) "Modo: Enseñar" else "Modo: Automático",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                // Navigation Buttons (Prev / Next / Close)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (guideState.currentStepIndex > 0) {
                        IconButton(
                            onClick = onPrev,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Anterior", tint = Color.White)
                        }
                    }

                    Button(
                        onClick = onNext,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCelebration) MeetColors.neonGreen else MeetColors.electricBlue,
                            contentColor = Color.Black,
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp),
                    ) {
                        Text(
                            text = if (isCelebration) "Continuar" else "Siguiente",
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = MeetColors.textSecondary)
                    }
                }
            }
        }
    }
}

/**
 * Living EVAIR Avatar Entity:
 * Floats gracefully with subtle sine-wave hovering, animated glowing core,
 * and reactive eyes that look towards the target button!
 */
@Composable
private fun EvairFloatingAvatarEntity(
    guideState: EvairGuideState,
    targetBounds: Rect?,
    onAvatarClick: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "evair_living_anim")

    // Smooth hover float
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "hover_y",
    )

    // Breathing pulse
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath_scale",
    )

    // Eye blink animation: blinks every ~3.5 seconds
    val blinkProgress by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3500
                1f at 0
                1f at 3300
                0.05f at 3400
                1f at 3500
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "blink_progress",
    )

    // Eyes horizontal glance: look towards the target anchor if one exists
    val eyeLookX = remember(targetBounds) {
        if (targetBounds != null) {
            // Normalize look offset between -4f and 4f
            (targetBounds.left - 400f).coerceIn(-4f, 4f)
        } else {
            0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationY = floatOffset
                    scaleX = breathScale
                    scaleY = breathScale
                }
                .size(62.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onAvatarClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Glowing Cyber Aura Rings
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                (if (guideState.avatarState == AvatarState.CELEBRATING) MeetColors.neonGreen else MeetColors.cyberCyan).copy(alpha = 0.35f),
                                Color.Transparent,
                            )
                        ),
                        shape = CircleShape,
                    )
            )

            // Outer Core Shell
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = Color(0xFF071220),
                border = BorderStroke(
                    width = 2.dp,
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            MeetColors.cyberCyan,
                            MeetColors.electricBlue,
                            MeetColors.neonGreen,
                            MeetColors.cyberCyan,
                        )
                    ),
                ),
                shadowElevation = 10.dp,
            ) {
                // Living Face / Expressive Eyes
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.graphicsLayer { translationX = eyeLookX },
                    ) {
                        // Left Eye
                        Box(
                            modifier = Modifier
                                .size(width = 6.dp, height = (8 * blinkProgress).dp)
                                .background(
                                    color = if (guideState.avatarState == AvatarState.CELEBRATING) MeetColors.neonGreen else MeetColors.cyberCyan,
                                    shape = CircleShape,
                                )
                        )

                        // Right Eye
                        Box(
                            modifier = Modifier
                                .size(width = 6.dp, height = (8 * blinkProgress).dp)
                                .background(
                                    color = if (guideState.avatarState == AvatarState.CELEBRATING) MeetColors.neonGreen else MeetColors.cyberCyan,
                                    shape = CircleShape,
                                )
                        )
                    }

                    // Cheerful mouth indicator when celebrating
                    if (guideState.avatarState == AvatarState.CELEBRATING) {
                        Box(
                            modifier = Modifier
                                .offset(y = 8.dp)
                                .size(width = 8.dp, height = 3.dp)
                                .background(MeetColors.neonGreen, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
    }
}
