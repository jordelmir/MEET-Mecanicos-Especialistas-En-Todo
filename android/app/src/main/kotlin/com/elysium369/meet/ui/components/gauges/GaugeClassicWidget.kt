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

private data class ClassicTickInfo(
    val start: Offset,
    val end: Offset,
    val isMajor: Boolean,
    val i: Int,
    val labelTextResult: Pair<androidx.compose.ui.text.TextLayoutResult, Offset>?
)

/**
 * Classic V2.5 Style: Faithful reproduction of the MEET v2.5 gauge with premium upgrades.
 * Uses high-performance Canvas rendering with tick numbers, visible background tracks,
 * concentric dual speedometer scale, and a premium mechanical center hub needle.
 */
@Composable
fun GaugeClassicWidget(
    label: String,
    value: Float,
    minVal: Float = 0f,
    maxVal: Float = 100f,
    unit: String,
    warningThreshold: Float? = null,
    criticalThreshold: Float? = null,
    isAnomaly: Boolean = false,
    customLabelColor: Color? = null,
    customUnitColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = LocalGaugeColorScheme.current
    val hasData = value != 0f || label.contains("Temp", true)

    val animatedValue by animateFloatAsState(
        targetValue = value.coerceIn(minVal, maxVal),
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 120f),
        label = "classicGaugeAnimation"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "classicPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "classicPulseAlpha"
    )

    val textMeasurer = rememberTextMeasurer()

    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(4.dp)
            .drawWithCache {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width / 2f - 24.dp.toPx()
                val strokeWidth = 6.dp.toPx()

                val sweepAngle = 240f
                val startAngle = 150f

                // Pre-measure min, max and label texts
                val minText = if (minVal == minVal.toInt().toFloat()) "${minVal.toInt()}" else String.format("%.0f", minVal)
                val maxText = if (maxVal >= 1000) "${(maxVal / 1000).toInt()}k" 
                              else if (maxVal == maxVal.toInt().toFloat()) "${maxVal.toInt()}" 
                              else String.format("%.0f", maxVal)

                val minMeasured = textMeasurer.measure(minText, TextStyle(color = MeetColors.textSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
                val maxMeasured = textMeasurer.measure(maxText, TextStyle(color = MeetColors.textSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
                val labelMeasured = textMeasurer.measure(
                    label.uppercase(),
                    TextStyle(
                        color = customLabelColor ?: colorScheme.labelColor.copy(alpha = 0.8f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )

                // Layout positions
                val labelTop = center.y + radius * 0.35f
                val valueTop = labelTop + labelMeasured.size.height + 4.dp.toPx()
                val anomalyTop = center.y - radius * 0.5f

                // Pre-calculate tick positions and major tick labels
                val tickCount = 40
                val majorInterval = 10
                val ticks = List(tickCount + 1) { i ->
                    val angle = startAngle + (i.toFloat() / tickCount) * sweepAngle
                    val angleRad = Math.toRadians(angle.toDouble())
                    val isMajor = i % majorInterval == 0
                    val tickLength = if (isMajor) 12.dp.toPx() else if (i % 5 == 0) 8.dp.toPx() else 4.dp.toPx()

                    val outerR = radius
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
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        Pair(measured, labelOffset)
                    } else null

                    ClassicTickInfo(start = start, end = end, isMajor = isMajor, i = i, labelTextResult = labelResult)
                }

                // Speedometer dual scale calculation
                val isSpeedGauge = unit.equals("km/h", ignoreCase = true) || unit.equals("mph", ignoreCase = true)
                val isKmH = unit.equals("km/h", ignoreCase = true)
                val secondaryUnit = if (isKmH) "mph" else "km/h"
                val speedConversion = if (isKmH) 0.621371f else (1f / 0.621371f)
                val secondaryMax = maxVal * speedConversion

                // Generate clean major tick values for the inner scale
                val secondaryStep = if (secondaryMax > 200f) 40f else if (secondaryMax > 120f) 20f else 10f
                val innerTicks = if (isSpeedGauge) {
                    val list = mutableListOf<ClassicTickInfo>()
                    var vSec = 0f
                    while (vSec <= secondaryMax) {
                        val angleFraction = vSec / secondaryMax
                        val angle = startAngle + angleFraction * sweepAngle
                        val angleRad = Math.toRadians(angle.toDouble())

                        val innerR = radius - strokeWidth - 14.dp.toPx()
                        val tickLength = 5.dp.toPx()

                        val start = Offset(
                            (center.x + innerR * cos(angleRad)).toFloat(),
                            (center.y + innerR * sin(angleRad)).toFloat()
                        )
                        val end = Offset(
                            (center.x + (innerR + tickLength) * cos(angleRad)).toFloat(),
                            (center.y + (innerR + tickLength) * sin(angleRad)).toFloat()
                        )

                        val labelR = innerR - 8.dp.toPx()
                        val labelOffset = Offset(
                            (center.x + labelR * cos(angleRad)).toFloat(),
                            (center.y + labelR * sin(angleRad)).toFloat()
                        )

                        val measured = textMeasurer.measure(
                            text = String.format("%.0f", vSec),
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )

                        list.add(ClassicTickInfo(start = start, end = end, isMajor = true, i = vSec.toInt(), labelTextResult = Pair(measured, labelOffset)))
                        vSec += secondaryStep
                    }
                    list
                } else emptyList()

                // Endpoints labels positions
                val minAngleRad = Math.toRadians((startAngle - 10).toDouble())
                val maxAngleRad = Math.toRadians((startAngle + sweepAngle + 10).toDouble())
                val labelEndR = radius + 8.dp.toPx()
                val minLabelPos = Offset(
                    (center.x + labelEndR * cos(minAngleRad)).toFloat() - minMeasured.size.width / 2f,
                    (center.y + labelEndR * sin(minAngleRad)).toFloat() - minMeasured.size.height / 2f
                )
                val maxLabelPos = Offset(
                    (center.x + labelEndR * cos(maxAngleRad)).toFloat() - maxMeasured.size.width / 2f,
                    (center.y + labelEndR * sin(maxAngleRad)).toFloat() - maxMeasured.size.height / 2f
                )

                onDrawBehind {
                    val animVal = animatedValue
                    val activeColor = when {
                        !hasData -> MeetColors.textMuted
                        isAnomaly -> MeetColors.error
                        criticalThreshold != null && animVal >= criticalThreshold -> MeetColors.error
                        warningThreshold != null && animVal >= warningThreshold -> MeetColors.warning
                        else -> colorScheme.internalColor
                    }

                    val themeGlowColor = if (activeColor == colorScheme.internalColor) colorScheme.specialColor else activeColor
                    val themeNeedleColor = if (activeColor == colorScheme.internalColor) colorScheme.needleColor else activeColor
                    val themeTextColor = if (activeColor == colorScheme.internalColor) colorScheme.textColor else activeColor

                    // ── 0. OUTER BEZEL RING ──
                    drawCircle(
                        color = Color.White.copy(alpha = 0.04f),
                        radius = radius + 10.dp.toPx(),
                        center = center,
                        style = Stroke(1.5f.dp.toPx())
                    )

                    // ── 1. BACKGROUND TRACK ──
                    drawArc(
                        color = Color.White.copy(alpha = 0.08f),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2f, radius * 2f)
                    )

                    val progress = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                    val currentSweep = progress * sweepAngle

                    if (hasData) {
                        // ── 2. VALUE ARC GLOW ──
                        drawArc(
                            brush = Brush.sweepGradient(
                                0f to themeGlowColor.copy(alpha = 0f),
                                0.5f to themeGlowColor.copy(alpha = 0.2f),
                                1f to themeGlowColor.copy(alpha = 0.5f)
                            ),
                            startAngle = startAngle,
                            sweepAngle = currentSweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth * 1.8f, cap = StrokeCap.Round),
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2f, radius * 2f)
                        )

                        // ── 3. VALUE ARC CORE ──
                        drawArc(
                            color = activeColor,
                            startAngle = startAngle,
                            sweepAngle = currentSweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2f, radius * 2f)
                        )
                    }

                    // ── 4. TICK MARKS WITH LABELS ──
                    ticks.forEach { tick ->
                        val tickFraction = tick.i.toFloat() / tickCount
                        val tickColor = when {
                            !hasData -> Color.White.copy(alpha = 0.1f)
                            tickFraction <= progress -> activeColor
                            else -> Color.White.copy(alpha = 0.15f)
                        }

                        drawLine(
                            color = tickColor.copy(alpha = if (tick.isMajor) 0.8f else 0.3f),
                            start = tick.start,
                            end = tick.end,
                            strokeWidth = if (tick.isMajor) 1.5f.dp.toPx() else 1.dp.toPx()
                        )

                        tick.labelTextResult?.let { (measured, pos) ->
                            drawText(measured, topLeft = Offset(pos.x - measured.size.width / 2f, pos.y - measured.size.height / 2f))
                        }
                    }

                    // ── 5. SPEEDOMETER DUAL SCALE (concentric) ──
                    if (isSpeedGauge) {
                        val innerR = radius - strokeWidth - 14.dp.toPx()
                        // Inner arc line
                        drawArc(
                            color = Color.White.copy(alpha = 0.08f),
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = Stroke(width = 1.dp.toPx()),
                            topLeft = Offset(center.x - innerR, center.y - innerR),
                            size = Size(innerR * 2f, innerR * 2f)
                        )

                        // Inner ticks & labels
                        innerTicks.forEach { tick ->
                            drawLine(
                                color = Color.White.copy(alpha = 0.2f),
                                start = tick.start,
                                end = tick.end,
                                strokeWidth = 1.dp.toPx()
                            )
                            tick.labelTextResult?.let { (measured, pos) ->
                                drawText(measured, topLeft = Offset(pos.x - measured.size.width / 2f, pos.y - measured.size.height / 2f))
                            }
                        }
                    }

                    // ── 6. PREMIUM MECHANICAL NEEDLE ──
                    if (hasData) {
                        val needleAngle = startAngle + currentSweep
                        val needleRad = Math.toRadians(needleAngle.toDouble())
                        val needleLen = radius - 4.dp.toPx()
                        val needleTip = Offset(
                            (center.x + needleLen * cos(needleRad)).toFloat(),
                            (center.y + needleLen * sin(needleRad)).toFloat()
                        )

                        // Shadow
                        drawLine(color = Color.Black.copy(alpha = 0.3f), start = center, end = needleTip, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                        // Core
                        drawLine(color = themeNeedleColor, start = center, end = needleTip, strokeWidth = 2.5f.dp.toPx(), cap = StrokeCap.Round)
                        // Tip highlight
                        drawLine(color = Color.White, start = Offset((center.x + (needleLen - 15.dp.toPx()) * cos(needleRad)).toFloat(), (center.y + (needleLen - 15.dp.toPx()) * sin(needleRad)).toFloat()), end = needleTip, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)

                        // Central Hub Cap
                        drawCircle(color = themeNeedleColor, radius = 7.dp.toPx(), center = center)
                        drawCircle(color = Color.Black, radius = 4.dp.toPx(), center = center)
                        drawCircle(color = themeNeedleColor.copy(alpha = 0.4f), radius = 10.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
                    }

                    // ── 7. DIGITAL DISPLAY ──
                    drawText(labelMeasured, topLeft = Offset(center.x - labelMeasured.size.width / 2f, labelTop))

                    if (hasData) {
                        val valueText = String.format("%.0f", animVal)
                        val valueMeasured = textMeasurer.measure(
                            text = valueText,
                            style = TextStyle(color = themeTextColor, fontSize = 26.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                        )
                        val activeUnitColor = when {
                            !hasData -> MeetColors.textMuted
                            isAnomaly -> MeetColors.error
                            criticalThreshold != null && animVal >= criticalThreshold -> MeetColors.error
                            warningThreshold != null && animVal >= warningThreshold -> MeetColors.warning
                            else -> customUnitColor ?: colorScheme.unitColor
                        }
                        val unitMeasured = textMeasurer.measure(
                            text = unit.lowercase(),
                            style = TextStyle(color = activeUnitColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        )
                        val textW = valueMeasured.size.width + unitMeasured.size.width + 2.dp.toPx()
                        val textStart = center.x - textW / 2f

                        drawText(valueMeasured, topLeft = Offset(textStart, valueTop))
                        drawText(unitMeasured, topLeft = Offset(textStart + valueMeasured.size.width + 2.dp.toPx(), valueTop + (valueMeasured.size.height - unitMeasured.size.height - 2.dp.toPx())))

                        if (isSpeedGauge) {
                            val secValText = String.format("%.0f %s", animVal * speedConversion, secondaryUnit)
                            val secMeasured = textMeasurer.measure(
                                text = secValText,
                                style = TextStyle(
                                    color = MeetColors.textSecondary.copy(alpha = 0.5f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            drawText(secMeasured, topLeft = Offset(center.x - secMeasured.size.width / 2f, valueTop + valueMeasured.size.height + 2.dp.toPx()))
                        }
                    } else {
                        val offlineText = "SIN SEÑAL"
                        val offlineMeasured = textMeasurer.measure(
                            text = offlineText,
                            style = TextStyle(
                                color = Color.White.copy(alpha = 0.3f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        drawText(offlineMeasured, topLeft = Offset(center.x - offlineMeasured.size.width / 2f, valueTop))
                    }

                    // Endpoint labels
                    drawText(minMeasured, topLeft = minLabelPos)
                    drawText(maxMeasured, topLeft = maxLabelPos)

                    // Anomaly Warning
                    if (isAnomaly && hasData) {
                        val anomalyText = "⚠ FALLA"
                        val anomalyMeasured = textMeasurer.measure(
                            text = anomalyText,
                            style = TextStyle(color = MeetColors.error.copy(alpha = pulseAlpha), fontSize = 8.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                        )
                        drawText(anomalyMeasured, topLeft = Offset(center.x - anomalyMeasured.size.width / 2f, anomalyTop))
                    }
                }
            }
    )
}
