package com.elysium369.meet.ride.driver.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DriverArrivalSlider(
    enabled: Boolean,
    label: String,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MeetColors.neonGreen,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val thumbSizeDp = 52.dp
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }

    var containerWidthPx by remember { mutableIntStateOf(0) }
    val maxDragPx = (containerWidthPx - thumbSizePx).coerceAtLeast(0f)

    val dragOffsetAnim = remember { Animatable(0f) }
    var committed by remember { mutableStateOf(false) }

    LaunchedEffect(enabled) {
        if (!enabled) {
            committed = false
        } else if (committed) {
            // Reset when re-enabled
            committed = false
            dragOffsetAnim.snapTo(0f)
        }
    }

    // Safety auto-reset: if committed but command is rejected or unhandled,
    // automatically animate back and unfreeze after 1.8s so driver can slide again without blocking!
    LaunchedEffect(committed) {
        if (committed) {
            delay(1800)
            if (committed) {
                dragOffsetAnim.animateTo(0f, tween(250))
                committed = false
            }
        }
    }

    val dragProgress = if (maxDragPx > 0f) {
        (dragOffsetAnim.value / maxDragPx).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(MeetColors.backgroundDark)
            .border(
                width = 1.5.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.5f),
                        MeetColors.cyberCyan.copy(alpha = 0.5f),
                    ),
                ),
                shape = RoundedCornerShape(30.dp),
            )
            .onSizeChanged { containerWidthPx = it.width }
            .pointerInput(enabled, maxDragPx) {
                if (!enabled || maxDragPx <= 0f) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var isDrag = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            // Touch released!
                            if (!isDrag && down.position.x > containerWidthPx * 0.45f && !committed) {
                                // Direct tap on right side triggers commit
                                committed = true
                                scope.launch {
                                    dragOffsetAnim.animateTo(maxDragPx, tween(140))
                                    onConfirmed()
                                }
                            } else {
                                val progress = if (maxDragPx > 0f) dragOffsetAnim.value / maxDragPx else 0f
                                if (progress >= 0.40f && !committed) {
                                    committed = true
                                    scope.launch {
                                        dragOffsetAnim.animateTo(maxDragPx, tween(120))
                                        onConfirmed()
                                    }
                                } else {
                                    scope.launch {
                                        dragOffsetAnim.animateTo(0f, tween(200))
                                    }
                                }
                            }
                            break
                        }

                        val dragAmount = change.position.x - change.previousPosition.x
                        if (kotlin.math.abs(dragAmount) > 0.5f) {
                            isDrag = true
                            change.consume()
                            if (!committed) {
                                val next = (dragOffsetAnim.value + dragAmount).coerceIn(0f, maxDragPx)
                                scope.launch { dragOffsetAnim.snapTo(next) }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // Active Progress Fill
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction = (dragProgress + 0.12f).coerceAtMost(1f))
                .height(58.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.3f),
                            MeetColors.cyberCyan.copy(alpha = 0.45f),
                        ),
                    ),
                ),
        )

        // Center Label
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (!enabled && committed) "Confirmando…" else label,
                color = if (enabled) MeetColors.textPrimary else MeetColors.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
        }

        // Draggable Thumb
        Box(
            modifier = Modifier
                .offset { IntOffset(dragOffsetAnim.value.roundToInt(), 0) }
                .padding(3.dp)
                .size(thumbSizeDp)
                .shadow(8.dp, CircleShape, spotColor = accentColor)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = if (enabled) {
                            listOf(accentColor, MeetColors.cyberCyan)
                        } else {
                            listOf(Color(0xFF374151), Color(0xFF1F2937))
                        },
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = ">>>",
                color = Color.Black,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
            )
        }
    }
}
