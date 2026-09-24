package com.elysium369.meet.safety.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.safety.ui.accountability.AccountabilityMetrics
import com.elysium369.meet.ui.theme.MeetColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated gauge for the AccountabilityClock.
 *
 * Arc of 270° showing institutional response time:
 *   - Green zone (0–72h): On-time response
 *   - Yellow zone (72h–7d): Delayed response
 *   - Red zone (>7d): No response
 *
 * Milestone dots placed on the arc for each known event.
 * Center label shows "X días" with color matching urgency.
 */
@Composable
fun AccountabilityGauge(
    metrics: AccountabilityMetrics,
    milestoneTypes: List<String>,
    modifier: Modifier = Modifier,
    maxDays: Int = 30,
) {
    val days = metrics.daysSinceReport ?: 0L
    val fraction = (days.toFloat() / maxDays).coerceIn(0f, 1f)

    val animatedFraction = remember { Animatable(0f) }
    LaunchedEffect(fraction) {
        animatedFraction.animateTo(
            targetValue = fraction,
            animationSpec = tween(durationMillis = 1200),
        )
    }

    val urgencyColor = when {
        days <= 3 -> GaugeColors.green
        days <= 7 -> GaugeColors.yellow
        else -> GaugeColors.red
    }

    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.3f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize().padding(16.dp)) {
            val strokeWidth = 16.dp.toPx()
            val arcSize = Size(
                width = size.width - strokeWidth,
                height = size.width - strokeWidth,
            )
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            val startAngle = 135f  // Bottom-left
            val totalSweep = 270f  // 270° arc

            // Background track
            drawArc(
                color = MeetColors.borderSubtle.copy(alpha = 0.3f),
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // Zone colors (green → yellow → red)
            val greenEnd = (3f / maxDays) * totalSweep
            val yellowEnd = (7f / maxDays) * totalSweep

            // Green zone
            drawArc(
                color = GaugeColors.green.copy(alpha = 0.2f),
                startAngle = startAngle,
                sweepAngle = greenEnd.coerceAtMost(totalSweep),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
            )

            // Yellow zone
            if (yellowEnd > greenEnd) {
                drawArc(
                    color = GaugeColors.yellow.copy(alpha = 0.2f),
                    startAngle = startAngle + greenEnd,
                    sweepAngle = (yellowEnd - greenEnd).coerceAtMost(totalSweep - greenEnd),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                )
            }

            // Red zone
            if (totalSweep > yellowEnd) {
                drawArc(
                    color = GaugeColors.red.copy(alpha = 0.15f),
                    startAngle = startAngle + yellowEnd,
                    sweepAngle = totalSweep - yellowEnd,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                )
            }

            // Active progress arc
            val activeSweep = animatedFraction.value * totalSweep
            drawArc(
                color = urgencyColor,
                startAngle = startAngle,
                sweepAngle = activeSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // Tick marks every 24h
            val arcCenter = Offset(
                x = topLeft.x + arcSize.width / 2f,
                y = topLeft.y + arcSize.height / 2f,
            )
            val arcRadius = arcSize.width / 2f

            for (day in 0..maxDays step 7) {
                val tickFraction = day.toFloat() / maxDays
                val tickAngle = Math.toRadians((startAngle + tickFraction * totalSweep).toDouble())
                val innerR = arcRadius - strokeWidth / 2f - 6.dp.toPx()
                val outerR = arcRadius - strokeWidth / 2f + 2.dp.toPx()
                drawLine(
                    color = MeetColors.textSecondary.copy(alpha = 0.5f),
                    start = Offset(
                        x = arcCenter.x + (innerR * cos(tickAngle)).toFloat(),
                        y = arcCenter.y + (innerR * sin(tickAngle)).toFloat(),
                    ),
                    end = Offset(
                        x = arcCenter.x + (outerR * cos(tickAngle)).toFloat(),
                        y = arcCenter.y + (outerR * sin(tickAngle)).toFloat(),
                    ),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }

            // Milestone dots on the arc
            val milestoneColors = mapOf(
                "REPORT_SENT" to MeetColors.cyberCyan,
                "DELIVERY_CONFIRMED" to MeetColors.neonGreen,
                "PUBLIC_ACTION_FOUND" to MeetColors.warning,
                "RESPONSE_DOCUMENTED" to MeetColors.neonGreen,
            )
            milestoneTypes.forEachIndexed { index, type ->
                val milestoneFraction = ((index + 1).toFloat() / (milestoneTypes.size + 1)).coerceIn(0f, 1f)
                val dotAngle = Math.toRadians((startAngle + milestoneFraction * activeSweep).toDouble())
                val dotRadius = arcRadius
                drawCircle(
                    color = milestoneColors[type] ?: MeetColors.textSecondary,
                    radius = 5.dp.toPx(),
                    center = Offset(
                        x = arcCenter.x + (dotRadius * cos(dotAngle)).toFloat(),
                        y = arcCenter.y + (dotRadius * sin(dotAngle)).toFloat(),
                    ),
                )
            }

            // Center label
            val labelText = if (days > 0) "${days}d" else "—"
            val labelStyle = TextStyle(
                color = urgencyColor,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            val measuredLabel = textMeasurer.measure(labelText, labelStyle)
            drawText(
                textLayoutResult = measuredLabel,
                topLeft = Offset(
                    x = arcCenter.x - measuredLabel.size.width / 2f,
                    y = arcCenter.y - measuredLabel.size.height / 2f,
                ),
            )

            // Sub-label
            val subText = when {
                days <= 3 -> "respuesta a tiempo"
                days <= 7 -> "respuesta demorada"
                metrics.timeToFirstResponse == null -> "sin respuesta"
                else -> "monitoreo activo"
            }
            val subStyle = TextStyle(
                color = MeetColors.textSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
            val measuredSub = textMeasurer.measure(subText, subStyle)
            drawText(
                textLayoutResult = measuredSub,
                topLeft = Offset(
                    x = arcCenter.x - measuredSub.size.width / 2f,
                    y = arcCenter.y + measuredLabel.size.height / 2f + 4.dp.toPx(),
                ),
            )
        }
    }
}

private object GaugeColors {
    val green = Color(0xFF4CAF50)
    val yellow = Color(0xFFFFD600)
    val red = Color(0xFFFF4444)
}
