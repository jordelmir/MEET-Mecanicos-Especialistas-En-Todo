package com.elysium369.meet.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import com.elysium369.meet.ui.theme.MeetColors
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

private data class TickInfo(
    val start: Offset,
    val end: Offset,
    val isMajor: Boolean,
    val i: Int,
    val labelTextResult: Pair<androidx.compose.ui.text.TextLayoutResult, Offset>?
)

@Composable
fun GaugeWidget(
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
    val hasData = value != 0f || label.contains("Temp", true) // Temp can legitimately be 0
    
    // Check for fast-changing sensors to apply snappy spring dynamics
    val isFastSensor = unit.equals("km/h", ignoreCase = true) || 
                       unit.equals("rpm", ignoreCase = true) || 
                       unit.contains("hp", ignoreCase = true) ||
                       unit.equals("bar", ignoreCase = true) ||
                       unit.equals("%", ignoreCase = true) ||
                       label.contains("velocidad", ignoreCase = true) || 
                       label.contains("speed", ignoreCase = true) ||
                       label.contains("rpm", ignoreCase = true) ||
                       label.contains("boost", ignoreCase = true) ||
                       label.contains("carga", ignoreCase = true) ||
                       label.contains("load", ignoreCase = true) ||
                       label.contains("acelerador", ignoreCase = true) ||
                       label.contains("throttle", ignoreCase = true)

    // ULTRA-SMOOTH & RESPONSIVE: Snappy spring animation to prevent gauge lag
    // Damping ratio of 0.78f provides a sporty slight overshoot/settle visual sweep.
    val animatedValueState = animateFloatAsState(
        targetValue = value.coerceIn(minVal, maxVal),
        animationSpec = spring(
            dampingRatio = if (isFastSensor) 0.78f else Spring.DampingRatioNoBouncy,
            stiffness = if (isFastSensor) 180f else 90f
        ),
        label = "gaugeAnimation"
    )

    // Infinite transitions for glow breathe, scanning line, and warnings
    val infiniteTransition = rememberInfiniteTransition(label = "gaugePulse")
    
    val pulseAlphaState = infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    val scanAngleState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 240f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanAngle"
    )
    
    val glowAlphaState = infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowBreath"
    )

    val textMeasurer = rememberTextMeasurer()

    val warnFraction = if (warningThreshold != null && maxVal > minVal) 
        ((warningThreshold - minVal) / (maxVal - minVal)).coerceIn(0f, 1f) else 0.75f
    val critFraction = if (criticalThreshold != null && maxVal > minVal) 
        ((criticalThreshold - minVal) / (maxVal - minVal)).coerceIn(0f, 1f) else 0.90f

    // ── HIGH-PERFORMANCE RENDERER ──
    // Caches static background, ticks, and text layouts to eliminate allocations and CPU calculations per frame.
    // Dynamic values (needle, arc progress, digital readout) read state variables ONLY during the Draw phase,
    // bypassing Compose Recomposition and Layout entirely for a locked 120 FPS.
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(4.dp)
            .drawWithCache {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width / 2f - 24.dp.toPx()
                val strokeWidth = 8.dp.toPx()
                
                val sweepAngle = 240f
                val startAngle = 150f

                // Pre-measure min, max and label texts
                val minText = if (minVal == minVal.toInt().toFloat()) "${minVal.toInt()}" else String.format("%.0f", minVal)
                val maxText = if (maxVal >= 1000) "${(maxVal / 1000).toInt()}k" 
                              else if (maxVal == maxVal.toInt().toFloat()) "${maxVal.toInt()}" 
                              else String.format("%.0f", maxVal)

                val minMeasured = textMeasurer.measure(minText, TextStyle(color = MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold))
                val maxMeasured = textMeasurer.measure(maxText, TextStyle(color = MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold))
                val labelMeasured = textMeasurer.measure(label.uppercase(), TextStyle(color = MeetColors.textSecondary.copy(alpha = 0.7f), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp))

                // Pre-calculate tick positions and major tick labels (cos/sin math cached here!)
                val tickCount = 40
                val majorInterval = 10
                val ticks = List(tickCount + 1) { i ->
                    val angle = startAngle + (i.toFloat() / tickCount) * sweepAngle
                    val angleRad = Math.toRadians(angle.toDouble())
                    val isMajor = i % majorInterval == 0
                    val tickLength = if (isMajor) 14.dp.toPx() else if (i % 5 == 0) 10.dp.toPx() else 6.dp.toPx()
                    
                    val outerR = radius - strokeWidth / 2f - 3.dp.toPx()
                    val start = Offset(
                        (center.x + outerR * cos(angleRad)).toFloat(),
                        (center.y + outerR * sin(angleRad)).toFloat()
                    )
                    val end = Offset(
                        (center.x + (outerR - tickLength) * cos(angleRad)).toFloat(),
                        (center.y + (outerR - tickLength) * sin(angleRad)).toFloat()
                    )

                    val labelResult = if (isMajor) {
                        val labelVal = minVal + (i.toFloat() / tickCount) * (maxVal - minVal)
                        val text = if (labelVal >= 1000) "${(labelVal / 1000).toInt()}k"
                                   else if (labelVal == labelVal.toInt().toFloat()) "${labelVal.toInt()}"
                                   else String.format("%.0f", labelVal)
                        val labelR = outerR - tickLength - 10.dp.toPx()
                        val labelOffset = Offset(
                            (center.x + labelR * cos(angleRad)).toFloat(),
                            (center.y + labelR * sin(angleRad)).toFloat()
                        )
                        val measured = textMeasurer.measure(
                            text = text,
                            style = TextStyle(
                                color = MeetColors.textSecondary.copy(alpha = 0.6f),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Pair(measured, labelOffset)
                    } else null

                    TickInfo(start = start, end = end, isMajor = isMajor, i = i, labelTextResult = labelResult)
                }

                // Pre-calculate min/max label positions
                val minAngleRad = Math.toRadians((startAngle + sweepAngle + 12).toDouble())
                val maxAngleRad = Math.toRadians((startAngle - 12).toDouble())
                val endLabelR = radius + 10.dp.toPx()
                val minLabelPos = Offset(
                    (center.x + endLabelR * cos(minAngleRad)).toFloat() - minMeasured.size.width / 2f,
                    (center.y + endLabelR * sin(minAngleRad)).toFloat() - minMeasured.size.height / 2f
                )
                val maxLabelPos = Offset(
                    (center.x + endLabelR * cos(maxAngleRad)).toFloat() - maxMeasured.size.width / 2f,
                    (center.y + endLabelR * sin(maxAngleRad)).toFloat() - maxMeasured.size.height / 2f
                )

                onDrawBehind {
                    // ── RENDER PHASE (Executed on GPU draw ticks) ──

                    // Read animated states in DrawScope ONLY to prevent Compose recompositions
                    val animValue = animatedValueState.value
                    val glowAlpha = glowAlphaState.value
                    val pulseAlpha = pulseAlphaState.value
                    val scanAngle = scanAngleState.value

                    // Determine active color dynamically
                    val activeColor = when {
                        !hasData -> MeetColors.textMuted
                        isAnomaly -> MeetColors.error
                        criticalThreshold != null && animValue >= criticalThreshold -> MeetColors.error
                        warningThreshold != null && animValue >= warningThreshold -> MeetColors.warning
                        else -> MeetColors.neonGreen
                    }

                    // ── 0. OUTER GLOW RING ──
                    drawArc(
                        color = activeColor.copy(alpha = glowAlpha),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = strokeWidth * 3f, cap = StrokeCap.Round),
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2f, radius * 2f)
                    )

                    // ── 1. BACKGROUND ARC ──
                    drawArc(
                        color = MeetColors.backgroundDeep,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2f, radius * 2f)
                    )
                    
                    // ── 1b. WARNING & CRITICAL ZONE ARCS ──
                    val warnStart = startAngle + warnFraction * sweepAngle
                    val warnSweep = (critFraction - warnFraction) * sweepAngle
                    val critStart = startAngle + critFraction * sweepAngle
                    val critSweep = (1f - critFraction) * sweepAngle
                    
                    drawArc(
                        color = MeetColors.warning.copy(alpha = 0.12f),
                        startAngle = warnStart,
                        sweepAngle = warnSweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2f, radius * 2f)
                    )
                    drawArc(
                        color = MeetColors.error.copy(alpha = 0.12f),
                        startAngle = critStart,
                        sweepAngle = critSweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2f, radius * 2f)
                    )

                    // ── 2. TICK MARKS WITH NUMBERED LABELS ──
                    val progress = if (maxVal == minVal) 0f else ((animValue - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                    ticks.forEach { tick ->
                        val tickFraction = tick.i.toFloat() / tickCount
                        val tickColor = when {
                            !hasData -> MeetColors.textMuted
                            tickFraction <= progress -> activeColor
                            tickFraction >= critFraction -> MeetColors.error.copy(alpha = 0.25f)
                            tickFraction >= warnFraction -> MeetColors.warning.copy(alpha = 0.2f)
                            else -> MeetColors.borderBlue
                        }
                        
                        drawLine(
                            color = tickColor.copy(alpha = if (tick.isMajor) 0.9f else if (tick.i % 5 == 0) 0.5f else 0.3f),
                            start = tick.start,
                            end = tick.end,
                            strokeWidth = if (tick.isMajor) 2.dp.toPx() else 1.dp.toPx()
                        )
                        
                        tick.labelTextResult?.let { (measured, pos) ->
                            drawText(
                                textLayoutResult = measured,
                                topLeft = Offset(
                                    pos.x - measured.size.width / 2f,
                                    pos.y - measured.size.height / 2f
                                )
                            )
                        }
                    }

                    if (hasData) {
                        // ── 3. VALUE ARC GLOW ──
                        val currentSweep = progress * sweepAngle
                        drawArc(
                            brush = Brush.sweepGradient(
                                0f to activeColor.copy(alpha = 0f),
                                0.5f to activeColor.copy(alpha = 0.3f),
                                1f to activeColor.copy(alpha = 0.6f)
                            ),
                            startAngle = startAngle,
                            sweepAngle = currentSweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth * 2f, cap = StrokeCap.Round),
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2f, radius * 2f)
                        )

                        // ── 4. VALUE ARC CORE ──
                        drawArc(
                            color = activeColor,
                            startAngle = startAngle,
                            sweepAngle = currentSweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2f, radius * 2f)
                        )

                        // ── 5. NEEDLE / POINTER ──
                        val needleAngle = startAngle + currentSweep
                        val needleRad = Math.toRadians(needleAngle.toDouble())
                        val needleLength = radius - strokeWidth / 2f - 3.dp.toPx()
                        val needleEnd = Offset(
                            (center.x + needleLength * cos(needleRad)).toFloat(),
                            (center.y + needleLength * sin(needleRad)).toFloat()
                        )
                        
                        // Needle shadow
                        drawLine(color = activeColor.copy(alpha = 0.15f), start = center, end = needleEnd, strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
                        // Needle glow
                        drawLine(color = activeColor.copy(alpha = 0.5f), start = center, end = needleEnd, strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
                        // Needle core
                        drawLine(color = activeColor, start = center, end = needleEnd, strokeWidth = 2.5f.dp.toPx(), cap = StrokeCap.Round)
                        
                        // Center hub
                        drawCircle(color = activeColor, radius = 5.dp.toPx(), center = center)
                        drawCircle(color = Color.Black, radius = 3.dp.toPx(), center = center)
                        drawCircle(color = activeColor.copy(alpha = 0.5f), radius = 8.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))

                        // ── 6. DYNAMIC DIGITAL READOUT ──
                        val valueText = String.format("%.0f", animValue)
                        val valueMeasured = textMeasurer.measure(
                            text = valueText,
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-1).sp
                            )
                        )
                        val unitMeasured = textMeasurer.measure(
                            text = unit.lowercase(),
                            style = TextStyle(
                                color = activeColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )

                        val textWidth = valueMeasured.size.width + unitMeasured.size.width + 2.dp.toPx()
                        val textStart = center.x - textWidth / 2f
                        val textY = center.y + 38.dp.toPx()

                        drawText(valueMeasured, topLeft = Offset(textStart, textY))
                        drawText(unitMeasured, topLeft = Offset(textStart + valueMeasured.size.width + 2.dp.toPx(), textY + 13.dp.toPx()))

                    } else {
                        // ── NO DATA: Scanning arc animation ──
                        val scanColor = MeetColors.neonGreen.copy(alpha = 0.4f * pulseAlpha)
                        drawArc(
                            brush = Brush.sweepGradient(
                                0f to Color.Transparent,
                                0.3f to scanColor,
                                0.5f to scanColor.copy(alpha = 0.1f),
                                1f to Color.Transparent
                            ),
                            startAngle = startAngle + scanAngle,
                            sweepAngle = 40f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2f, radius * 2f)
                        )
                        
                        // Center pulsing dot
                        drawCircle(
                            color = MeetColors.neonGreen.copy(alpha = pulseAlpha * 0.3f),
                            radius = 12.dp.toPx() * pulseAlpha,
                            center = center
                        )
                        drawCircle(color = MeetColors.neonGreen.copy(alpha = 0.4f), radius = 3.dp.toPx(), center = center)

                        // Digital readout warning
                        val warningText = "SIN SEÑAL"
                        val warningMeasured = textMeasurer.measure(
                            text = warningText,
                            style = TextStyle(
                                color = MeetColors.textMuted.copy(alpha = pulseAlpha),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            )
                        )
                        drawText(warningMeasured, topLeft = Offset(center.x - warningMeasured.size.width / 2f, center.y + 40.dp.toPx()))
                    }
                    
                    // ── 7. MIN / MAX LABELS at arc endpoints ──
                    drawText(minMeasured, topLeft = minLabelPos)
                    drawText(maxMeasured, topLeft = maxLabelPos)

                    // ── 8. GAUGE CENTRAL LABEL ──
                    drawText(labelMeasured, topLeft = Offset(center.x - labelMeasured.size.width / 2f, center.y + 26.dp.toPx()))

                    // Anomaly label on Canvas
                    if (isAnomaly && hasData) {
                        val anomalyText = "⚠ ANOMALÍA"
                        val anomalyMeasured = textMeasurer.measure(
                            text = anomalyText,
                            style = TextStyle(
                                color = MeetColors.error.copy(alpha = pulseAlpha),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        )
                        drawText(anomalyMeasured, topLeft = Offset(center.x - anomalyMeasured.size.width / 2f, center.y + 66.dp.toPx()))
                    }
                }
            }
    )
}
