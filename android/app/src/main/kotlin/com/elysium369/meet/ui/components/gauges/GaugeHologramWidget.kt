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
 * Holographic Sci-Fi Style: Multiple concentric translucent rings with particle effects.
 * Iron Man / JARVIS aesthetic with floating data readouts and shimmer effects.
 */
@Composable
fun GaugeHologramWidget(
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
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 100f),
        label = "holoGauge"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "holoPulse")
    val rotateAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart),
        label = "holoRotate"
    )
    val innerRotate by infiniteTransition.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label = "holoInnerRotate"
    )
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "holoShimmer"
    )
    val particlePhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 6.28f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart),
        label = "holoParticles"
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
                val maxRadius = size.width / 2f - 12.dp.toPx()
                val sweepAngle = 270f
                val startAngle = 135f

                val labelMeasured = textMeasurer.measure(
                    label.uppercase(),
                    TextStyle(color = Color(0xFF00DDFF).copy(alpha = 0.5f), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace)
                )

                // Particle positions (pre-calculated angles)
                val particleCount = 8
                val particleAngles = List(particleCount) { i -> (360f / particleCount) * i }

                onDrawBehind {
                    val holoColor = when {
                        !hasData -> Color(0xFF334455)
                        isAnomaly -> Color(0xFFFF4444)
                        criticalThreshold != null && animatedValue >= criticalThreshold -> Color(0xFFFF4444)
                        warningThreshold != null && animatedValue >= warningThreshold -> Color(0xFFFFAA00)
                        else -> Color(0xFF00DDFF) // Holographic cyan
                    }
                    val holoSecondary = when {
                        !hasData -> Color(0xFF223344)
                        isAnomaly -> Color(0xFFFF8888)
                        else -> Color(0xFF00FF99)
                    }
                    val progress = if (maxVal == minVal) 0f else ((animatedValue - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)

                    // Outer ring 1 (rotating, dashed)
                    val ring1R = maxRadius
                    val ring1Start = rotateAngle
                    for (i in 0 until 12) {
                        val a = ring1Start + i * 30f
                        drawArc(
                            color = holoColor.copy(alpha = 0.08f),
                            startAngle = a, sweepAngle = 15f,
                            useCenter = false,
                            style = Stroke(width = 1.5f.dp.toPx()),
                            topLeft = Offset(center.x - ring1R, center.y - ring1R),
                            size = Size(ring1R * 2, ring1R * 2)
                        )
                    }

                    // Outer ring 2 (counter-rotating, thin)
                    val ring2R = maxRadius - 8.dp.toPx()
                    for (i in 0 until 8) {
                        val a = innerRotate + i * 45f
                        drawArc(
                            color = holoSecondary.copy(alpha = 0.06f),
                            startAngle = a, sweepAngle = 20f,
                            useCenter = false,
                            style = Stroke(width = 1.dp.toPx()),
                            topLeft = Offset(center.x - ring2R, center.y - ring2R),
                            size = Size(ring2R * 2, ring2R * 2)
                        )
                    }

                    // Main progress ring (thick, translucent)
                    val mainR = maxRadius - 18.dp.toPx()
                    val mainWidth = 10.dp.toPx()
                    // Background arc
                    drawArc(
                        color = holoColor.copy(alpha = 0.06f),
                        startAngle = startAngle, sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = mainWidth, cap = StrokeCap.Round),
                        topLeft = Offset(center.x - mainR, center.y - mainR),
                        size = Size(mainR * 2, mainR * 2)
                    )

                    if (hasData) {
                        // Progress glow
                        drawArc(
                            color = holoColor.copy(alpha = shimmer * 0.2f),
                            startAngle = startAngle, sweepAngle = progress * sweepAngle,
                            useCenter = false,
                            style = Stroke(width = mainWidth * 3f, cap = StrokeCap.Round),
                            topLeft = Offset(center.x - mainR, center.y - mainR),
                            size = Size(mainR * 2, mainR * 2)
                        )
                        // Progress arc
                        drawArc(
                            brush = Brush.sweepGradient(
                                0f to holoColor.copy(alpha = 0.3f),
                                0.5f to holoColor,
                                1f to holoSecondary
                            ),
                            startAngle = startAngle, sweepAngle = progress * sweepAngle,
                            useCenter = false,
                            style = Stroke(width = mainWidth, cap = StrokeCap.Round),
                            topLeft = Offset(center.x - mainR, center.y - mainR),
                            size = Size(mainR * 2, mainR * 2)
                        )
                    }

                    // Inner decorative ring
                    val innerR = mainR - 16.dp.toPx()
                    drawCircle(holoColor.copy(alpha = 0.04f), innerR, center, style = Stroke(1.dp.toPx()))

                    // Floating particles
                    val particleR = mainR + 4.dp.toPx()
                    particleAngles.forEachIndexed { i, baseAngle ->
                        val angle = baseAngle + rotateAngle * 0.3f
                        val rad = Math.toRadians(angle.toDouble())
                        val wobble = sin(particlePhase + i * 0.8) * 4.dp.toPx()
                        val px = (center.x + (particleR + wobble) * cos(rad)).toFloat()
                        val py = (center.y + (particleR + wobble) * sin(rad)).toFloat()
                        val pAlpha = (0.15f + 0.2f * sin(particlePhase + i * 1.2).toFloat()).coerceIn(0f, 1f)
                        drawCircle(holoColor.copy(alpha = pAlpha), 2.dp.toPx(), Offset(px, py))
                    }

                    // Central value with holographic shimmer
                    val valueText = if (hasData) String.format("%.0f", animatedValue) else "---"
                    val valueMeasured = textMeasurer.measure(
                        valueText,
                        TextStyle(color = Color.White.copy(alpha = shimmer), fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
                    )
                    // Ghost duplicate for holographic effect
                    val ghostMeasured = textMeasurer.measure(
                        valueText,
                        TextStyle(color = holoColor.copy(alpha = 0.15f), fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
                    )
                    drawText(ghostMeasured, topLeft = Offset(center.x - ghostMeasured.size.width / 2f + 1.dp.toPx(), center.y - ghostMeasured.size.height / 2f - 6.dp.toPx()))
                    drawText(valueMeasured, topLeft = Offset(center.x - valueMeasured.size.width / 2f, center.y - valueMeasured.size.height / 2f - 8.dp.toPx()))

                    // Unit
                    val unitMeasured = textMeasurer.measure(
                        unit.lowercase(),
                        TextStyle(color = holoColor.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    )
                    drawText(unitMeasured, topLeft = Offset(center.x - unitMeasured.size.width / 2f, center.y + 12.dp.toPx()))

                    // Label
                    drawText(labelMeasured, topLeft = Offset(center.x - labelMeasured.size.width / 2f, center.y + 28.dp.toPx()))

                    // Center dot
                    drawCircle(holoColor.copy(alpha = 0.2f), 3.dp.toPx(), center)
                }
            }
    )
}
