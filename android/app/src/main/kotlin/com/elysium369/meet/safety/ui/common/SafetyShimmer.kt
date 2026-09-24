package com.elysium369.meet.safety.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.elysium369.meet.ui.theme.MeetColors

/**
 * Shimmer loading skeleton that replaces generic CircularProgressIndicators.
 * Replicates card structure for visual continuity during loading.
 */
@Composable
fun SafetyShimmer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-translate",
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            MeetColors.cardBackground,
            MeetColors.borderSubtle.copy(alpha = 0.5f),
            MeetColors.cardBackground,
        ),
        start = Offset(translateAnim - 400f, 0f),
        end = Offset(translateAnim, 0f),
    )

    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        repeat(3) { index ->
            ShimmerCard(brush = shimmerBrush)
            if (index < 2) Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ShimmerCard(brush: Brush) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MeetColors.cardBackground)
            .padding(16.dp),
    ) {
        Column {
            // Title placeholder
            ShimmerRect(brush = brush, width = 180.dp, height = 14.dp)
            Spacer(modifier = Modifier.height(10.dp))
            // Subtitle placeholder
            ShimmerRect(brush = brush, width = 260.dp, height = 10.dp)
            Spacer(modifier = Modifier.height(14.dp))
            // Metric row
            Row {
                ShimmerRect(brush = brush, width = 60.dp, height = 24.dp)
                Spacer(modifier = Modifier.width(12.dp))
                ShimmerRect(brush = brush, width = 60.dp, height = 24.dp)
                Spacer(modifier = Modifier.width(12.dp))
                ShimmerRect(brush = brush, width = 60.dp, height = 24.dp)
            }
        }
    }
}

@Composable
private fun ShimmerRect(brush: Brush, width: Dp, height: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(brush),
    )
}

/**
 * KPI-specific shimmer: large number + small label.
 */
@Composable
fun KpiShimmer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "kpi-shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 800f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "kpi-translate",
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            MeetColors.cardBackground,
            MeetColors.borderSubtle.copy(alpha = 0.4f),
            MeetColors.cardBackground,
        ),
        start = Offset(translateAnim - 300f, 0f),
        end = Offset(translateAnim, 0f),
    )
    Column(modifier = modifier.padding(10.dp)) {
        ShimmerRect(brush = brush, width = 48.dp, height = 22.dp)
        Spacer(modifier = Modifier.height(4.dp))
        ShimmerRect(brush = brush, width = 36.dp, height = 8.dp)
    }
}
