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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.components.gauges.LocalGaugeColorScheme

/**
 * Cyber HUD Style: Futuristic videogame-like display.
 * Large holographic number with horizontal segmented neon bar, grid background,
 * and scanline effects. Think Cyberpunk 2077 / Halo HUD.
 */
@Composable
fun GaugeCyberWidget(
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
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 150f),
        label = "cyberGauge"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "cyberPulse")
    val scanX by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart),
        label = "cyberScan"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "cyberPulse"
    )
    val glitchOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "cyberGlitch"
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
                val inset = w * 0.12f // Inward padding to avoid bezel clipping
                val segmentCount = 24
                val segmentGap = 1.5f.dp.toPx()
                val barHeight = h * 0.06f
                val barTop = h * 0.64f
                val barLeft = inset
                val barRight = w - inset
                val totalBarWidth = barRight - barLeft
                val segWidth = (totalBarWidth - (segmentCount - 1) * segmentGap) / segmentCount

                // Adaptive font sizes based on gauge size
                val labelFontSize = (w * 0.04f).coerceIn(6f, 10f)
                val valueFontSize = (w * 0.18f).coerceIn(16f, 42f)
                val unitFontSize = (w * 0.055f).coerceIn(7f, 13f)
                val statusFontSize = (w * 0.035f).coerceIn(5f, 9f)
                val percentFontSize = (w * 0.05f).coerceIn(7f, 12f)
                val minMaxFontSize = (w * 0.035f).coerceIn(5f, 9f)

                val labelMeasured = textMeasurer.measure(
                    label.uppercase(),
                    TextStyle(color = colorScheme.labelColor.copy(alpha = 0.8f), fontSize = labelFontSize.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
                )

                onDrawBehind {
                    val activeColor = when {
                        !hasData -> MeetColors.textMuted
                        isAnomaly -> MeetColors.error
                        criticalThreshold != null && animatedValue >= criticalThreshold -> MeetColors.error
                        warningThreshold != null && animatedValue >= warningThreshold -> MeetColors.warning
                        else -> colorScheme.internalColor
                    }
                    val themeTextColor = if (activeColor == colorScheme.internalColor) colorScheme.textColor else activeColor
                    val progress = if (maxVal == minVal) 0f else ((animatedValue - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)

                    // Grid background
                    val gridStep = 20.dp.toPx()
                    for (x in 0..(w / gridStep).toInt()) {
                        drawLine(Color.White.copy(alpha = 0.03f), Offset(x * gridStep, 0f), Offset(x * gridStep, h), 1f)
                    }
                    for (y in 0..(h / gridStep).toInt()) {
                        drawLine(Color.White.copy(alpha = 0.03f), Offset(0f, y * gridStep), Offset(w, y * gridStep), 1f)
                    }

                    // Scanning vertical line
                    val scanLineX = w * scanX
                    drawLine(
                        brush = Brush.verticalGradient(listOf(Color.Transparent, activeColor.copy(alpha = 0.15f), Color.Transparent)),
                        start = Offset(scanLineX, 0f), end = Offset(scanLineX, h),
                        strokeWidth = 30.dp.toPx()
                    )

                    // Corner brackets (HUD frame) — moved inward
                    val bracketLen = w * 0.08f
                    val bracketStroke = 1.5f.dp.toPx()
                    val bracketColor = activeColor.copy(alpha = 0.3f)
                    val bp = inset * 0.5f
                    // Top-left
                    drawLine(bracketColor, Offset(bp, bp), Offset(bp + bracketLen, bp), bracketStroke)
                    drawLine(bracketColor, Offset(bp, bp), Offset(bp, bp + bracketLen), bracketStroke)
                    // Top-right
                    drawLine(bracketColor, Offset(w - bp, bp), Offset(w - bp - bracketLen, bp), bracketStroke)
                    drawLine(bracketColor, Offset(w - bp, bp), Offset(w - bp, bp + bracketLen), bracketStroke)
                    // Bottom-left
                    drawLine(bracketColor, Offset(bp, h - bp), Offset(bp + bracketLen, h - bp), bracketStroke)
                    drawLine(bracketColor, Offset(bp, h - bp), Offset(bp, h - bp - bracketLen), bracketStroke)
                    // Bottom-right
                    drawLine(bracketColor, Offset(w - bp, h - bp), Offset(w - bp - bracketLen, h - bp), bracketStroke)
                    drawLine(bracketColor, Offset(w - bp, h - bp), Offset(w - bp, h - bp - bracketLen), bracketStroke)

                    // Label — centered at top
                    drawText(labelMeasured, topLeft = Offset(w / 2f - labelMeasured.size.width / 2f, inset))

                    // Status indicator below label
                    val statusText = if (hasData) "● ONLINE" else "○ OFFLINE"
                    val statusMeasured = textMeasurer.measure(
                        statusText,
                        TextStyle(
                            color = if (hasData) activeColor.copy(alpha = pulseAlpha) else MeetColors.textMuted,
                            fontSize = statusFontSize.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace
                        )
                    )
                    drawText(statusMeasured, topLeft = Offset(w / 2f - statusMeasured.size.width / 2f, inset + labelMeasured.size.height + 2.dp.toPx()))

                    // Large centered value with glow — vertically centered between label and bar
                    val valueText = if (hasData) String.format("%.0f", animatedValue) else "---"
                    val valueMeasured = textMeasurer.measure(
                        valueText,
                        TextStyle(color = themeTextColor, fontSize = valueFontSize.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp, fontFamily = FontFamily.Monospace)
                    )
                    val glowMeasured = textMeasurer.measure(
                        valueText,
                        TextStyle(color = activeColor.copy(alpha = 0.2f), fontSize = valueFontSize.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp, fontFamily = FontFamily.Monospace)
                    )
                    val textZoneTop = inset + labelMeasured.size.height + statusMeasured.size.height + 4.dp.toPx()
                    val textZoneBottom = barTop - 4.dp.toPx()
                    val valX = w / 2f - valueMeasured.size.width / 2f
                    val valY = textZoneTop + (textZoneBottom - textZoneTop) / 2f - valueMeasured.size.height / 2f
                    drawText(glowMeasured, topLeft = Offset(valX + 1.dp.toPx(), valY + 1.dp.toPx()))
                    drawText(valueMeasured, topLeft = Offset(valX, valY))

                    // Unit below value, centered
                    val activeUnitColor = if (activeColor == colorScheme.internalColor) colorScheme.unitColor else activeColor
                    val unitMeasured = textMeasurer.measure(
                        unit.uppercase(),
                        TextStyle(color = activeUnitColor, fontSize = unitFontSize.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    )
                    drawText(unitMeasured, topLeft = Offset(w / 2f - unitMeasured.size.width / 2f, valY + valueMeasured.size.height + 1.dp.toPx()))

                    // Segmented horizontal bar
                    val filledSegments = (progress * segmentCount).toInt()
                    val warnFrac = if (warningThreshold != null && maxVal > minVal) ((warningThreshold - minVal) / (maxVal - minVal)).coerceIn(0f, 1f) else 0.75f
                    val critFrac = if (criticalThreshold != null && maxVal > minVal) ((criticalThreshold - minVal) / (maxVal - minVal)).coerceIn(0f, 1f) else 0.9f

                    for (i in 0 until segmentCount) {
                        val segFrac = i.toFloat() / segmentCount
                        val x = barLeft + i * (segWidth + segmentGap)

                        val segColor = when {
                            i >= filledSegments || !hasData -> Color.White.copy(alpha = 0.05f)
                            segFrac >= critFrac -> MeetColors.error
                            segFrac >= warnFrac -> MeetColors.warning
                            else -> activeColor
                        }

                        val alpha = if (i < filledSegments && hasData) 1f else 0.15f
                        val path = Path().apply {
                            addRoundRect(RoundRect(Rect(x, barTop, x + segWidth, barTop + barHeight), CornerRadius(2.dp.toPx())))
                        }
                        drawPath(path, segColor.copy(alpha = alpha))

                        if (i < filledSegments && hasData) {
                            drawPath(path, segColor.copy(alpha = 0.3f))
                        }
                    }

                    // Percent centered below bar
                    val percentText = if (hasData) "${(progress * 100).toInt()}%" else "--%"
                    val percentMeasured = textMeasurer.measure(
                        percentText,
                        TextStyle(color = activeColor, fontSize = percentFontSize.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    )
                    drawText(percentMeasured, topLeft = Offset(w / 2f - percentMeasured.size.width / 2f, barTop + barHeight + 3.dp.toPx()))

                    // Min/Max labels at bar edges
                    val minText = textMeasurer.measure("${minVal.toInt()}", TextStyle(color = MeetColors.textMuted, fontSize = minMaxFontSize.sp, fontFamily = FontFamily.Monospace))
                    val maxText = textMeasurer.measure("${maxVal.toInt()}", TextStyle(color = MeetColors.textMuted, fontSize = minMaxFontSize.sp, fontFamily = FontFamily.Monospace))
                    drawText(minText, topLeft = Offset(barLeft, barTop + barHeight + 3.dp.toPx()))
                    drawText(maxText, topLeft = Offset(barRight - maxText.size.width, barTop + barHeight + 3.dp.toPx()))

                    // Horizontal scanline effect
                    val scanY = (h * glitchOffset) % h
                    drawLine(
                        color = activeColor.copy(alpha = 0.06f),
                        start = Offset(0f, scanY), end = Offset(w, scanY),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
    )
}
