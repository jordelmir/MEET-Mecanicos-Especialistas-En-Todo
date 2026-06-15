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

/**
 * Neon Retrowave Style: 80s synthwave aesthetic with hot pink, cyan, and purple.
 * Extreme glow/bloom effects, perspective grid hints, retro-futuristic typography.
 */
@Composable
fun GaugeNeonRetroWidget(
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
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 140f),
        label = "neonGauge"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "neonPulse")
    val neonGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "neonGlow"
    )
    val gridScroll by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 20f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "gridScroll"
    )

    val textMeasurer = rememberTextMeasurer()
    val hasData = value != 0f || label.contains("Temp", true)

    // Synthwave color palette
    val neonPink = Color(0xFFFF006E)
    val neonCyan = Color(0xFF00F5FF)
    val neonPurple = Color(0xFFBB00FF)
    val neonYellow = Color(0xFFFFE600)

    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(4.dp)
            .drawWithCache {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width / 2f - 20.dp.toPx()
                val sweepAngle = 240f
                val startAngle = 150f
                val arcWidth = 8.dp.toPx()

                // Pre-measure tick labels
                val tickCount = 20
                val majorInterval = 5
                val ticks = List(tickCount + 1) { i ->
                    val angle = startAngle + (i.toFloat() / tickCount) * sweepAngle
                    val angleRad = Math.toRadians(angle.toDouble())
                    val isMajor = i % majorInterval == 0
                    val outerR = radius - arcWidth / 2f - 2.dp.toPx()
                    val tickLen = if (isMajor) 10.dp.toPx() else 5.dp.toPx()
                    Triple(
                        Offset((center.x + outerR * cos(angleRad)).toFloat(), (center.y + outerR * sin(angleRad)).toFloat()),
                        Offset((center.x + (outerR - tickLen) * cos(angleRad)).toFloat(), (center.y + (outerR - tickLen) * sin(angleRad)).toFloat()),
                        isMajor
                    )
                }

                val labelMeasured = textMeasurer.measure(
                    label.uppercase(),
                    TextStyle(color = neonCyan.copy(alpha = 0.6f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace)
                )

                onDrawBehind {
                    val activeColor = when {
                        !hasData -> MeetColors.textMuted
                        isAnomaly -> neonPink
                        criticalThreshold != null && animatedValue >= criticalThreshold -> neonPink
                        warningThreshold != null && animatedValue >= warningThreshold -> neonYellow
                        else -> neonCyan
                    }
                    val progress = if (maxVal == minVal) 0f else ((animatedValue - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                    val arcRect = Size(radius * 2f, radius * 2f)
                    val arcOffset = Offset(center.x - radius, center.y - radius)

                    // Perspective grid background (subtle)
                    val gridStep = 20.dp.toPx()
                    val gridOffset = gridScroll.dp.toPx()
                    for (i in -2..12) {
                        val y = size.height * 0.7f + i * gridStep - gridOffset
                        if (y > size.height * 0.6f && y < size.height) {
                            val alpha = 0.04f * (1f - (y - size.height * 0.6f) / (size.height * 0.4f))
                            drawLine(neonPurple.copy(alpha = alpha.coerceAtLeast(0f)), Offset(0f, y), Offset(size.width, y), 1f)
                        }
                    }

                    // Outer glow ring (neon bloom)
                    drawArc(
                        color = activeColor.copy(alpha = neonGlow * 0.15f),
                        startAngle = startAngle, sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = arcWidth * 4f, cap = StrokeCap.Round),
                        topLeft = arcOffset, size = arcRect
                    )

                    // Background arc — dark neon outline
                    drawArc(
                        color = neonPurple.copy(alpha = 0.12f),
                        startAngle = startAngle, sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = arcWidth, cap = StrokeCap.Round),
                        topLeft = arcOffset, size = arcRect
                    )

                    if (hasData) {
                        // Neon progress arc — triple layer for bloom
                        // Layer 1: Wide glow
                        drawArc(
                            color = activeColor.copy(alpha = neonGlow * 0.2f),
                            startAngle = startAngle, sweepAngle = progress * sweepAngle,
                            useCenter = false,
                            style = Stroke(width = arcWidth * 3f, cap = StrokeCap.Round),
                            topLeft = arcOffset, size = arcRect
                        )
                        // Layer 2: Medium glow
                        drawArc(
                            color = activeColor.copy(alpha = neonGlow * 0.5f),
                            startAngle = startAngle, sweepAngle = progress * sweepAngle,
                            useCenter = false,
                            style = Stroke(width = arcWidth * 1.5f, cap = StrokeCap.Round),
                            topLeft = arcOffset, size = arcRect
                        )
                        // Layer 3: Core bright
                        drawArc(
                            brush = Brush.sweepGradient(
                                0f to neonCyan,
                                0.5f to neonPink,
                                1f to neonPurple
                            ),
                            startAngle = startAngle, sweepAngle = progress * sweepAngle,
                            useCenter = false,
                            style = Stroke(width = arcWidth, cap = StrokeCap.Round),
                            topLeft = arcOffset, size = arcRect
                        )
                    }

                    // Tick marks (neon styled)
                    ticks.forEach { (start, end, isMajor) ->
                        drawLine(
                            color = if (isMajor) neonCyan.copy(alpha = 0.4f) else neonPurple.copy(alpha = 0.15f),
                            start = start, end = end,
                            strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx()
                        )
                    }

                    // Center value with extreme neon glow
                    val valueText = if (hasData) String.format("%.0f", animatedValue) else "---"
                    // Glow layer
                    val glowMeasured = textMeasurer.measure(
                        valueText,
                        TextStyle(color = activeColor.copy(alpha = neonGlow * 0.3f), fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp, fontFamily = FontFamily.Monospace)
                    )
                    drawText(glowMeasured, topLeft = Offset(center.x - glowMeasured.size.width / 2f, center.y - glowMeasured.size.height / 2f - 4.dp.toPx()))
                    // Core layer
                    val valueMeasured = textMeasurer.measure(
                        valueText,
                        TextStyle(color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp, fontFamily = FontFamily.Monospace)
                    )
                    drawText(valueMeasured, topLeft = Offset(center.x - valueMeasured.size.width / 2f, center.y - valueMeasured.size.height / 2f - 4.dp.toPx()))

                    // Unit with neon color
                    val unitMeasured = textMeasurer.measure(
                        unit.lowercase(),
                        TextStyle(color = neonPink, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    )
                    drawText(unitMeasured, topLeft = Offset(center.x - unitMeasured.size.width / 2f, center.y + valueMeasured.size.height / 2f))

                    // Label
                    drawText(labelMeasured, topLeft = Offset(center.x - labelMeasured.size.width / 2f, center.y + 30.dp.toPx()))

                    // Decorative neon lines at bottom
                    val lineY = size.height - 10.dp.toPx()
                    drawLine(neonPink.copy(alpha = 0.2f), Offset(20.dp.toPx(), lineY), Offset(size.width / 2f - 30.dp.toPx(), lineY), 1.dp.toPx())
                    drawLine(neonCyan.copy(alpha = 0.2f), Offset(size.width / 2f + 30.dp.toPx(), lineY), Offset(size.width - 20.dp.toPx(), lineY), 1.dp.toPx())
                }
            }
    )
}
