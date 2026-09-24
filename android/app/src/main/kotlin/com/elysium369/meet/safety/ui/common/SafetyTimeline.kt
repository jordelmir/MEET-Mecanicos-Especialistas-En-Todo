package com.elysium369.meet.safety.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.delay

/**
 * Vertical visual timeline for case milestones.
 *
 * Each node shows a colored dot with connector line, icon, title, and
 * timestamp. Future expected milestones appear with a dashed connector.
 * Tap to expand detail section.
 */
data class TimelineNode(
    val id: String,
    val title: String,
    val subtitle: String,
    val timestamp: String,
    val icon: ImageVector? = null,
    val color: Color = MeetColors.cyberCyan,
    val isFuture: Boolean = false,
    val detail: String? = null,
)

@Composable
fun SafetyTimeline(
    nodes: List<TimelineNode>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        nodes.forEachIndexed { index, node ->
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(index * 120L) // Staggered reveal
                visible = true
            }

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400)) + expandVertically(tween(400)),
            ) {
                TimelineItem(
                    node = node,
                    isLast = index == nodes.lastIndex,
                    hasNext = index < nodes.lastIndex,
                    nextIsFuture = nodes.getOrNull(index + 1)?.isFuture == true,
                )
            }
        }
    }
}

@Composable
private fun TimelineItem(
    node: TimelineNode,
    isLast: Boolean,
    hasNext: Boolean,
    nextIsFuture: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(enabled = node.detail != null) { expanded = !expanded },
    ) {
        // Left rail: dot + connector line
        Column(
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Dot
            Canvas(modifier = Modifier.size(16.dp)) {
                val nodeColor = if (node.isFuture) node.color.copy(alpha = 0.3f) else node.color

                // Outer ring
                drawCircle(
                    color = nodeColor.copy(alpha = 0.3f),
                    radius = size.minDimension / 2f,
                )
                // Inner fill
                drawCircle(
                    color = nodeColor,
                    radius = size.minDimension / 3f,
                )
            }

            // Connector line
            if (hasNext) {
                Canvas(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f),
                ) {
                    val lineColor = if (nextIsFuture || node.isFuture)
                        MeetColors.borderSubtle.copy(alpha = 0.4f)
                    else
                        MeetColors.cyberCyan.copy(alpha = 0.5f)

                    val pathEffect = if (nextIsFuture || node.isFuture)
                        PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                    else
                        null

                    drawLine(
                        color = lineColor,
                        start = Offset(size.width / 2f, 0f),
                        end = Offset(size.width / 2f, size.height),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = pathEffect,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Right content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (node.icon != null) {
                    Icon(
                        imageVector = node.icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (node.isFuture) node.color.copy(alpha = 0.4f) else node.color,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Text(
                    text = node.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (node.isFuture) MeetColors.textSecondary else MeetColors.textPrimary,
                )
            }

            Text(
                text = node.subtitle,
                fontSize = 12.sp,
                color = MeetColors.textSecondary,
            )

            Text(
                text = node.timestamp,
                fontSize = 10.sp,
                color = if (node.isFuture)
                    MeetColors.textSecondary.copy(alpha = 0.5f)
                else
                    MeetColors.cyberCyan.copy(alpha = 0.7f),
            )

            // Expandable detail
            if (node.detail != null) {
                AnimatedVisibility(visible = expanded) {
                    Text(
                        text = node.detail,
                        fontSize = 11.sp,
                        color = MeetColors.textSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
}
