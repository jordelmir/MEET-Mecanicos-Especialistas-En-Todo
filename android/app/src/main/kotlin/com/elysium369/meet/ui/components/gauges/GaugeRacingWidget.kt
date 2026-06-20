package com.elysium369.meet.ui.components.gauges

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors
import kotlin.math.cos
import kotlin.math.sin
import com.elysium369.meet.ui.components.gauges.LocalGaugeColorScheme

/**
 * Racing F1 Style: Segmented LED tachometer like an F1 dashboard.
 * Semi-arc divided into discrete blocks that light up sequentially.
 * Green → Yellow → Red progression with shift light flash at critical.
 */
@Composable
fun GaugeRacingWidget(
    label: String,
    value: Float,
    minVal: Float = 0f,
    maxVal: Float = 100f,
    unit: String,
    warningThreshold: Float? = null,
    criticalThreshold: Float? = null,
    isAnomaly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colorScheme = LocalGaugeColorScheme.current
    val animatedValue by animateFloatAsState(
        targetValue = value.coerceIn(minVal, maxVal),
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 200f),
        label = "racingGauge"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "racingPulse")
    val shiftFlash by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(200, easing = LinearEasing), RepeatMode.Reverse),
        label = "shiftFlash"
    )

    val textMeasurer = rememberTextMeasurer()
    val hasData = value != 0f || label.contains("Temp", true)
    val totalSegments = 24

    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(4.dp)
            .drawWithCache {
                val center = Offset(size.width / 2f, size.height / 2f)
                val outerRadius = size.width / 2f - 14.dp.toPx()
                val innerRadius = outerRadius - 22.dp.toPx()
                val sweepAngle = 240f
                val startAngle = 150f
                val gapAngle = 2f
                val segSweep = (sweepAngle - (totalSegments - 1) * gapAngle) / totalSegments

                // Pre-calculate segment positions
                val segAngles = List(totalSegments) { i ->
                    startAngle + i * (segSweep + gapAngle)
                }

                val labelMeasured = textMeasurer.measure(
                    label.uppercase(),
                    TextStyle(color = colorScheme.labelColor.copy(alpha = 0.5f), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
                )

                onDrawBehind {
                    val progress = if (maxVal == minVal) 0f else ((animatedValue - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                    val filledSegments = (progress * totalSegments).toInt()
                    val warnFrac = if (warningThreshold != null && maxVal > minVal) ((warningThreshold - minVal) / (maxVal - minVal)).coerceIn(0f, 1f) else 0.75f
                    val critFrac = if (criticalThreshold != null && maxVal > minVal) ((criticalThreshold - minVal) / (maxVal - minVal)).coerceIn(0f, 1f) else 0.9f
                    val isRedline = hasData && criticalThreshold != null && animatedValue >= criticalThreshold

                    // Draw each segment
                    segAngles.forEachIndexed { i, angle ->
                        val segFrac = i.toFloat() / totalSegments
                        val isFilled = hasData && i < filledSegments

                        val segColor = when {
                            !isFilled -> Color.White.copy(alpha = 0.06f)
                            isAnomaly -> MeetColors.error
                            segFrac >= critFrac -> if (isRedline) MeetColors.error.copy(alpha = shiftFlash) else MeetColors.error
                            segFrac >= warnFrac -> MeetColors.warning
                            segFrac >= 0.5f -> colorScheme.specialColor
                            else -> colorScheme.internalColor
                        }

                        // LED segment as thick arc
                        drawArc(
                            color = segColor,
                            startAngle = angle,
                            sweepAngle = segSweep,
                            useCenter = false,
                            style = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Butt),
                            topLeft = Offset(center.x - outerRadius + 10.dp.toPx(), center.y - outerRadius + 10.dp.toPx()),
                            size = Size((outerRadius - 10.dp.toPx()) * 2f, (outerRadius - 10.dp.toPx()) * 2f)
                        )

                        // Glow for filled segments
                        if (isFilled) {
                            drawArc(
                                color = segColor.copy(alpha = 0.2f),
                                startAngle = angle,
                                sweepAngle = segSweep,
                                useCenter = false,
                                style = Stroke(width = 28.dp.toPx(), cap = StrokeCap.Butt),
                                topLeft = Offset(center.x - outerRadius + 10.dp.toPx(), center.y - outerRadius + 10.dp.toPx()),
                                size = Size((outerRadius - 10.dp.toPx()) * 2f, (outerRadius - 10.dp.toPx()) * 2f)
                            )
                        }
                    }

                    // Inner ring decoration
                    val decoRadius = innerRadius - 8.dp.toPx()
                    drawArc(
                        color = Color.White.copy(alpha = 0.04f),
                        startAngle = startAngle, sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = 1.dp.toPx()),
                        topLeft = Offset(center.x - decoRadius, center.y - decoRadius),
                        size = Size(decoRadius * 2f, decoRadius * 2f)
                    )

                    // Shift light indicators at top (3 circles)
                    if (isRedline) {
                        for (dx in listOf(-18f, 0f, 18f)) {
                            drawCircle(
                                color = MeetColors.error.copy(alpha = shiftFlash),
                                radius = 4.dp.toPx(),
                                center = Offset(center.x + dx.dp.toPx(), 20.dp.toPx())
                            )
                        }
                    } else {
                        for (dx in listOf(-18f, 0f, 18f)) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.08f),
                                radius = 4.dp.toPx(),
                                center = Offset(center.x + dx.dp.toPx(), 20.dp.toPx())
                            )
                        }
                    }

                    // Large centered digital value
                    val valueText = if (hasData) String.format("%.0f", animatedValue) else "---"
                    val valueMeasured = textMeasurer.measure(
                        valueText,
                        TextStyle(
                            color = if (isRedline) MeetColors.error.copy(alpha = shiftFlash) else colorScheme.textColor,
                            fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp, fontFamily = FontFamily.Monospace
                        )
                    )
                    drawText(valueMeasured, topLeft = Offset(center.x - valueMeasured.size.width / 2f, center.y - valueMeasured.size.height / 2f - 2.dp.toPx()))

                    // Unit
                    val unitMeasured = textMeasurer.measure(
                        unit.uppercase(),
                        TextStyle(color = colorScheme.unitColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    )
                    drawText(unitMeasured, topLeft = Offset(center.x - unitMeasured.size.width / 2f, center.y + valueMeasured.size.height / 2f))

                    // Label
                    drawText(labelMeasured, topLeft = Offset(center.x - labelMeasured.size.width / 2f, center.y + 30.dp.toPx()))
                }
            }
    )
}
