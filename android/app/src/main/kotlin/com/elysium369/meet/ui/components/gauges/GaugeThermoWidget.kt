package com.elysium369.meet.ui.components.gauges

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
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

/**
 * Industrial Thermometer Style: Vertical bar gauge with color zones.
 * Green → Yellow → Red zones, scale markings, industrial factory panel look.
 */
@Composable
fun GaugeThermoWidget(
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
    val animatedValue by animateFloatAsState(
        targetValue = value.coerceIn(minVal, maxVal),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 80f),
        label = "thermoGauge"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "thermoPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "thermoPulse"
    )

    val textMeasurer = rememberTextMeasurer()
    val hasData = value != 0f || label.contains("Temp", true)

    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(4.dp)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val barWidth = 28.dp.toPx()
                val barLeft = w * 0.4f
                val barTop = 30.dp.toPx()
                val barBottom = h - 40.dp.toPx()
                val barHeight = barBottom - barTop
                val cornerR = 6.dp.toPx()

                val warnFrac = if (warningThreshold != null && maxVal > minVal) ((warningThreshold - minVal) / (maxVal - minVal)).coerceIn(0f, 1f) else 0.75f
                val critFrac = if (criticalThreshold != null && maxVal > minVal) ((criticalThreshold - minVal) / (maxVal - minVal)).coerceIn(0f, 1f) else 0.9f

                val tickCount = 10
                val scaleX = barLeft - 8.dp.toPx()

                // Pre-measure scale labels
                val scaleLabels = List(tickCount + 1) { i ->
                    val labelVal = minVal + (i.toFloat() / tickCount) * (maxVal - minVal)
                    val text = if (labelVal >= 1000) "${(labelVal / 1000).toInt()}k"
                               else if (labelVal == labelVal.toInt().toFloat()) "${labelVal.toInt()}"
                               else String.format("%.0f", labelVal)
                    val measured = textMeasurer.measure(text, TextStyle(color = MeetColors.textSecondary, fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
                    val y = barBottom - (i.toFloat() / tickCount) * barHeight
                    Pair(measured, y)
                }

                val labelMeasured = textMeasurer.measure(
                    label.uppercase(),
                    TextStyle(color = MeetColors.textSecondary, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
                )

                onDrawBehind {
                    val progress = if (maxVal == minVal) 0f else ((animatedValue - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                    val activeColor = when {
                        !hasData -> MeetColors.textMuted
                        isAnomaly -> MeetColors.error
                        progress >= critFrac -> MeetColors.error
                        progress >= warnFrac -> MeetColors.warning
                        else -> Color(0xFF00E676) // Industrial green
                    }
                    val fillHeight = progress * barHeight

                    // Bar track (dark background)
                    val trackPath = Path().apply {
                        addRoundRect(RoundRect(Rect(barLeft, barTop, barLeft + barWidth, barBottom), CornerRadius(cornerR)))
                    }
                    drawPath(trackPath, Color.White.copy(alpha = 0.05f))
                    drawPath(trackPath, Color.White.copy(alpha = 0.1f), style = Stroke(1.dp.toPx()))

                    // Color zone backgrounds
                    val warnY = barBottom - warnFrac * barHeight
                    val critY = barBottom - critFrac * barHeight
                    // Green zone
                    drawRect(Color(0xFF00E676).copy(alpha = 0.04f), Offset(barLeft, warnY), Size(barWidth, barBottom - warnY))
                    // Yellow zone
                    drawRect(MeetColors.warning.copy(alpha = 0.04f), Offset(barLeft, critY), Size(barWidth, warnY - critY))
                    // Red zone
                    drawRect(MeetColors.error.copy(alpha = 0.04f), Offset(barLeft, barTop), Size(barWidth, critY - barTop))

                    if (hasData) {
                        // Filled bar with gradient
                        val fillTop = barBottom - fillHeight
                        val fillBrush = Brush.verticalGradient(
                            colors = listOf(activeColor, activeColor.copy(alpha = 0.6f)),
                            startY = fillTop,
                            endY = barBottom
                        )
                        val clipPath = Path().apply {
                            addRoundRect(RoundRect(Rect(barLeft + 2.dp.toPx(), fillTop, barLeft + barWidth - 2.dp.toPx(), barBottom - 2.dp.toPx()), CornerRadius(cornerR - 2.dp.toPx())))
                        }
                        drawPath(clipPath, fillBrush)

                        // Glow behind fill
                        drawRect(
                            activeColor.copy(alpha = 0.15f),
                            Offset(barLeft - 4.dp.toPx(), fillTop - 2.dp.toPx()),
                            Size(barWidth + 8.dp.toPx(), fillHeight + 4.dp.toPx())
                        )

                        // Current value indicator line
                        drawLine(
                            color = Color.White,
                            start = Offset(barLeft - 2.dp.toPx(), fillTop),
                            end = Offset(barLeft + barWidth + 2.dp.toPx(), fillTop),
                            strokeWidth = 2.dp.toPx()
                        )

                        // Value readout next to indicator
                        val valueText = String.format("%.0f", animatedValue)
                        val valueMeasured = textMeasurer.measure(
                            valueText,
                            TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                        )
                        val unitMeasured = textMeasurer.measure(
                            unit.lowercase(),
                            TextStyle(color = activeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        )
                        val readoutX = barLeft + barWidth + 14.dp.toPx()
                        val readoutY = fillTop - valueMeasured.size.height / 2f
                        drawText(valueMeasured, topLeft = Offset(readoutX, readoutY))
                        drawText(unitMeasured, topLeft = Offset(readoutX, readoutY + valueMeasured.size.height))

                        // Arrow pointer
                        val arrowPath = Path().apply {
                            moveTo(barLeft + barWidth + 4.dp.toPx(), fillTop)
                            lineTo(barLeft + barWidth + 10.dp.toPx(), fillTop - 4.dp.toPx())
                            lineTo(barLeft + barWidth + 10.dp.toPx(), fillTop + 4.dp.toPx())
                            close()
                        }
                        drawPath(arrowPath, Color.White)
                    }

                    // Scale ticks and labels
                    scaleLabels.forEach { (measured, y) ->
                        // Tick mark
                        drawLine(
                            color = MeetColors.textMuted.copy(alpha = 0.3f),
                            start = Offset(scaleX - 6.dp.toPx(), y),
                            end = Offset(scaleX, y),
                            strokeWidth = 1.dp.toPx()
                        )
                        // Label
                        drawText(measured, topLeft = Offset(scaleX - 6.dp.toPx() - measured.size.width - 2.dp.toPx(), y - measured.size.height / 2f))
                    }

                    // Zone markers (W and C)
                    val warnMarker = textMeasurer.measure("W", TextStyle(color = MeetColors.warning.copy(alpha = 0.5f), fontSize = 7.sp, fontWeight = FontWeight.Black))
                    val critMarker = textMeasurer.measure("C", TextStyle(color = MeetColors.error.copy(alpha = 0.5f), fontSize = 7.sp, fontWeight = FontWeight.Black))
                    drawLine(MeetColors.warning.copy(alpha = 0.3f), Offset(barLeft, warnY), Offset(barLeft + barWidth, warnY), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
                    drawLine(MeetColors.error.copy(alpha = 0.3f), Offset(barLeft, critY), Offset(barLeft + barWidth, critY), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))

                    // Label at bottom center
                    drawText(labelMeasured, topLeft = Offset(w / 2f - labelMeasured.size.width / 2f, h - 24.dp.toPx()))

                    // No data state
                    if (!hasData) {
                        val noData = textMeasurer.measure("---", TextStyle(color = MeetColors.textMuted.copy(alpha = pulseAlpha), fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace))
                        drawText(noData, topLeft = Offset(barLeft + barWidth + 14.dp.toPx(), h / 2f - noData.size.height / 2f))
                    }
                }
            }
    )
}
