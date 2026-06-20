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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.components.gauges.LocalGaugeColorScheme

/**
 * Radial Smartwatch Style: Thick donut ring with gradient, like Apple Watch Activity Rings.
 * Clean, elegant, and modern with a large centered value and subtle animations.
 */
@Composable
fun GaugeRadialWidget(
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
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 100f),
        label = "radialGauge"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "radialBreath")
    val breathAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "radialBreath"
    )

    val textMeasurer = rememberTextMeasurer()
    val hasData = value != 0f || label.contains("Temp", true)

    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(4.dp)
            .drawWithCache {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width / 2f - 18.dp.toPx()
                val ringWidth = 18.dp.toPx()
                val sweepAngle = 360f
                val startAngle = -90f  // Start from top (12 o'clock)

                val labelMeasured = textMeasurer.measure(
                    label.uppercase(),
                    TextStyle(color = colorScheme.labelColor.copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                )

                onDrawBehind {
                    val activeColor = when {
                        !hasData -> MeetColors.textMuted
                        isAnomaly -> MeetColors.error
                        criticalThreshold != null && animatedValue >= criticalThreshold -> MeetColors.error
                        warningThreshold != null && animatedValue >= warningThreshold -> MeetColors.warning
                        else -> colorScheme.internalColor
                    }
                    val secondaryColor = when {
                        !hasData -> MeetColors.textMuted
                        isAnomaly -> Color(0xFFFF6B6B)
                        criticalThreshold != null && animatedValue >= criticalThreshold -> Color(0xFFFF6B6B)
                        warningThreshold != null && animatedValue >= warningThreshold -> Color(0xFFFFBE76)
                        else -> colorScheme.specialColor
                    }
                    val themeTextColor = if (activeColor == colorScheme.internalColor) colorScheme.textColor else activeColor
                    val progress = if (maxVal == minVal) 0f else ((animatedValue - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                    val arcRect = Size(radius * 2f, radius * 2f)
                    val arcOffset = Offset(center.x - radius, center.y - radius)

                    // Background ring
                    drawArc(
                        color = activeColor.copy(alpha = 0.08f),
                        startAngle = 0f, sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = ringWidth, cap = StrokeCap.Round),
                        topLeft = arcOffset, size = arcRect
                    )

                    if (hasData) {
                        // Outer glow ring (breathing)
                        drawArc(
                            color = activeColor.copy(alpha = breathAlpha * 0.12f),
                            startAngle = startAngle, sweepAngle = progress * sweepAngle,
                            useCenter = false,
                            style = Stroke(width = ringWidth * 2.5f, cap = StrokeCap.Round),
                            topLeft = arcOffset, size = arcRect
                        )

                        // Progress ring with gradient
                        drawArc(
                            brush = Brush.sweepGradient(
                                0f to activeColor,
                                0.5f to secondaryColor,
                                1f to activeColor
                            ),
                            startAngle = startAngle, sweepAngle = progress * sweepAngle,
                            useCenter = false,
                            style = Stroke(width = ringWidth, cap = StrokeCap.Round),
                            topLeft = arcOffset, size = arcRect
                        )

                        // End cap glow dot
                        val endAngle = Math.toRadians((startAngle + progress * sweepAngle).toDouble())
                        val dotPos = Offset(
                            (center.x + radius * kotlin.math.cos(endAngle)).toFloat(),
                            (center.y + radius * kotlin.math.sin(endAngle)).toFloat()
                        )
                        drawCircle(color = Color.White, radius = 5.dp.toPx(), center = dotPos)
                        drawCircle(color = activeColor.copy(alpha = 0.5f), radius = 10.dp.toPx(), center = dotPos)
                    }

                    // Inner decorative ring
                    val innerR = radius - ringWidth - 6.dp.toPx()
                    drawCircle(
                        color = activeColor.copy(alpha = 0.04f),
                        radius = innerR,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )

                    // Centered value
                    val valueText = if (hasData) String.format("%.0f", animatedValue) else "---"
                    val valueMeasured = textMeasurer.measure(
                        valueText,
                        TextStyle(color = themeTextColor, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
                    )
                    drawText(valueMeasured, topLeft = Offset(center.x - valueMeasured.size.width / 2f, center.y - valueMeasured.size.height / 2f - 10.dp.toPx()))

                    // Unit below value
                    val activeUnitColor = when {
                        !hasData -> MeetColors.textMuted
                        isAnomaly -> MeetColors.error
                        criticalThreshold != null && animatedValue >= criticalThreshold -> MeetColors.error
                        warningThreshold != null && animatedValue >= warningThreshold -> MeetColors.warning
                        else -> colorScheme.unitColor
                    }
                    val unitMeasured = textMeasurer.measure(
                        unit.lowercase(),
                        TextStyle(color = activeUnitColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    )
                    drawText(unitMeasured, topLeft = Offset(center.x - unitMeasured.size.width / 2f, center.y + 8.dp.toPx()))

                    // Label below unit
                    drawText(labelMeasured, topLeft = Offset(center.x - labelMeasured.size.width / 2f, center.y + 26.dp.toPx()))

                    // Percentage in corner
                    if (hasData) {
                        val pctText = "${(progress * 100).toInt()}%"
                        val pctMeasured = textMeasurer.measure(
                            pctText,
                            TextStyle(color = activeColor.copy(alpha = 0.5f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        )
                        drawText(pctMeasured, topLeft = Offset(center.x - pctMeasured.size.width / 2f, center.y + 42.dp.toPx()))
                    }
                }
            }
    )
}
