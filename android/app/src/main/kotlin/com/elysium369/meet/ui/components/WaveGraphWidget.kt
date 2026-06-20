package com.elysium369.meet.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import com.elysium369.meet.ui.theme.MeetColors
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.components.gauges.GaugeStyleSet
import com.elysium369.meet.ui.components.gauges.GaugeColorScheme
import com.elysium369.meet.ui.components.gauges.LocalGaugeColorScheme

@Composable
fun WaveGraphWidget(
    label: String,
    currentValue: Float,
    minVal: Float,
    maxVal: Float,
    unit: String,
    warningThreshold: Float? = null,
    criticalThreshold: Float? = null,
    isAnomaly: Boolean = false,
    riskLevel: Float = 0.0f,
    historyData: List<Float>? = null,
    style: GaugeStyleSet = GaugeStyleSet.ELITE,
    colorScheme: GaugeColorScheme? = null
) {
    val currentScheme = colorScheme ?: LocalGaugeColorScheme.current
    val localHistory = remember { mutableStateListOf<Float>() }
    val displayHistory = historyData ?: localHistory
    val hasData = displayHistory.isNotEmpty()
    val textMeasurer = rememberTextMeasurer()

    val infiniteTransition = rememberInfiniteTransition(label = "wavePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    val scanOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "scanOffset"
    )
    val noDataPulse by infiniteTransition.animateFloat(
        initialValue = 0.1f, targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "noDataPulse"
    )

    val activeColor = when {
        isAnomaly -> MeetColors.error
        criticalThreshold != null && currentValue >= criticalThreshold -> MeetColors.error
        warningThreshold != null && currentValue >= warningThreshold -> MeetColors.warning
        else -> currentScheme.internalColor
    }

    if (historyData == null) {
        LaunchedEffect(currentValue) {
            localHistory.add(currentValue)
            if (localHistory.size > 100) localHistory.removeAt(0)
        }
    }

    // Stats
    val dataMin = if (displayHistory.isNotEmpty()) displayHistory.min() else minVal
    val dataMax = if (displayHistory.isNotEmpty()) displayHistory.max() else maxVal
    val dataAvg = if (displayHistory.isNotEmpty()) displayHistory.average().toFloat() else 0f

    val cardBg = when (style) {
        GaugeStyleSet.FERRARI, GaugeStyleSet.RACING -> Color(0xFF0C0A0A)
        GaugeStyleSet.NEON_RETRO -> Color(0xFF130022)
        GaugeStyleSet.MILITARY -> Color(0xFF030A05)
        GaugeStyleSet.DIAMOND -> Color(0xFF0A0E17)
        else -> MeetColors.backgroundDark
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(cardBg, RoundedCornerShape(16.dp))
            .border(1.dp, activeColor.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(label.uppercase(), color = currentScheme.textColor.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (hasData) String.format("%.1f", currentValue) else "---",
                            color = if (hasData) currentScheme.textColor else MeetColors.textMuted,
                            fontSize = 24.sp, fontWeight = FontWeight.Black
                        )
                        Text(
                            text = " $unit",
                            color = if (hasData) activeColor else MeetColors.textMuted,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                        )
                    }
                }

                // Stats or badge
                if (isAnomaly) {
                    Box(
                        modifier = Modifier
                            .background(MeetColors.error.copy(alpha = 0.1f * pulseAlpha), RoundedCornerShape(4.dp))
                            .border(1.dp, MeetColors.error.copy(alpha = pulseAlpha), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("CRITICAL", color = MeetColors.error, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                } else if (hasData && displayHistory.size > 5) {
                    // Mini stats
                    Column(horizontalAlignment = Alignment.End) {
                        Text("▲ ${String.format("%.0f", dataMax)}", color = MeetColors.error.copy(alpha = 0.6f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text("μ ${String.format("%.0f", dataAvg)}", color = currentScheme.textColor.copy(alpha = 0.6f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text("▼ ${String.format("%.0f", dataMin)}", color = currentScheme.internalColor.copy(alpha = 0.6f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Graph Canvas
            Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                val width = size.width
                val height = size.height
                val range = (maxVal - minVal).coerceAtLeast(0.1f)

                // ── STYLE-SPECIFIC GRAPH BACKDROP DECORATIONS ──
                when (style) {
                    GaugeStyleSet.CYBER -> {
                        // Drawing corner brackets in the plot area
                        val bLen = 10.dp.toPx()
                        val bStroke = 1.5f.dp.toPx()
                        val bCol = activeColor.copy(alpha = 0.35f)
                        // Top-left
                        drawLine(bCol, Offset(0f, 0f), Offset(bLen, 0f), bStroke)
                        drawLine(bCol, Offset(0f, 0f), Offset(0f, bLen), bStroke)
                        // Top-right
                        drawLine(bCol, Offset(width, 0f), Offset(width - bLen, 0f), bStroke)
                        drawLine(bCol, Offset(width, 0f), Offset(width, bLen), bStroke)
                        // Bottom-left
                        drawLine(bCol, Offset(0f, height), Offset(bLen, height), bStroke)
                        drawLine(bCol, Offset(0f, height), Offset(0f, height - bLen), bStroke)
                        // Bottom-right
                        drawLine(bCol, Offset(width, height), Offset(width - bLen, height), bStroke)
                        drawLine(bCol, Offset(width, height), Offset(width, height - bLen), bStroke)

                        // Cyber grid
                        clipRect {
                            val gridStep = 16.dp.toPx()
                            for (x in 0..(width / gridStep).toInt()) {
                                drawLine(Color.White.copy(alpha = 0.02f), Offset(x * gridStep, 0f), Offset(x * gridStep, height), 1f)
                            }
                            for (y in 0..(height / gridStep).toInt()) {
                                drawLine(Color.White.copy(alpha = 0.02f), Offset(0f, y * gridStep), Offset(width, y * gridStep), 1f)
                            }
                        }
                    }
                    GaugeStyleSet.NEON_RETRO -> {
                        // Synthwave Horizon & perspective grid
                        clipRect {
                            val horizon = height * 0.2f
                            // Glowing sunset background
                            drawRect(
                                Brush.verticalGradient(
                                    listOf(currentScheme.specialColor.copy(alpha = 0.08f), Color.Transparent),
                                    startY = horizon, endY = height
                                )
                            )
                            // Perspective lines
                            val gridStep = 18.dp.toPx()
                            val lineCount = (width / gridStep).toInt()
                            val cx = width / 2f
                            for (i in -lineCount/2..lineCount/2) {
                                val startX = cx + i * gridStep * 0.3f
                                val endX = cx + i * gridStep * 1.5f
                                drawLine(
                                    currentScheme.specialColor.copy(alpha = 0.05f),
                                    Offset(startX, horizon), Offset(endX, height),
                                    1.5f.dp.toPx()
                                )
                            }
                            // Horizontal scrolling lines
                            for (y in 0..(height / 15.dp.toPx()).toInt()) {
                                val ly = horizon + (y * 15.dp.toPx() - scanOffset * 15.dp.toPx())
                                if (ly in horizon..height) {
                                    val alpha = 0.06f * (ly - horizon) / (height - horizon)
                                    drawLine(currentScheme.specialColor.copy(alpha = alpha), Offset(0f, ly), Offset(width, ly), 1.dp.toPx())
                                }
                            }
                        }
                    }
                    GaugeStyleSet.MILITARY -> {
                        // Crosshair rings (radar look)
                        clipRect {
                            val cx = width / 2f
                            val cy = height / 2f
                            drawCircle(activeColor.copy(alpha = 0.03f), height * 0.4f, Offset(cx, cy), style = Stroke(1.dp.toPx()))
                            drawCircle(activeColor.copy(alpha = 0.015f), height * 0.8f, Offset(cx, cy), style = Stroke(1.dp.toPx()))
                            // Crosshair ticks
                            drawLine(activeColor.copy(alpha = 0.1f), Offset(cx - 15.dp.toPx(), cy), Offset(cx + 15.dp.toPx(), cy), 1.dp.toPx())
                            drawLine(activeColor.copy(alpha = 0.1f), Offset(cx, cy - 15.dp.toPx()), Offset(cx, cy + 15.dp.toPx()), 1.dp.toPx())
                        }
                    }
                    GaugeStyleSet.FERRARI, GaugeStyleSet.RACING -> {
                        // Carbon fiber diagonal lines
                        clipRect {
                            val lineStep = 8.dp.toPx()
                            for (i in 0..((width + height) / lineStep).toInt()) {
                                val offset = i * lineStep
                                drawLine(
                                    color = Color.White.copy(alpha = 0.015f),
                                    start = Offset(offset, 0f),
                                    end = Offset(offset - height, height),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        }
                    }
                    GaugeStyleSet.PLASMA, GaugeStyleSet.AURORA -> {
                        // Flowing background nebula/plasma gradients
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    currentScheme.specialColor.copy(alpha = 0.06f),
                                    currentScheme.internalColor.copy(alpha = 0.02f),
                                    Color.Transparent
                                ),
                                center = Offset(width * scanOffset, height * 0.5f),
                                radius = width * 0.5f
                            )
                        )
                    }
                    else -> {
                        // Default clean grid
                        clipRect {
                            val gridStep = 20.dp.toPx()
                            for (x in 0..(width / gridStep).toInt()) {
                                drawLine(Color.White.copy(alpha = 0.03f), Offset(x * gridStep, 0f), Offset(x * gridStep, height), 1f)
                            }
                            for (y in 0..(height / gridStep).toInt()) {
                                drawLine(Color.White.copy(alpha = 0.03f), Offset(0f, y * gridStep), Offset(width, y * gridStep), 1f)
                            }
                        }
                    }
                }

                // Warning threshold line
                if (warningThreshold != null) {
                    val warnY = height - ((warningThreshold - minVal) / range) * height
                    drawLine(
                        color = MeetColors.warning.copy(alpha = 0.3f),
                        start = Offset(0f, warnY), end = Offset(width, warnY),
                        strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                    )
                    val warnLabel = textMeasurer.measure("⚠ ${warningThreshold.toInt()}", TextStyle(color = MeetColors.warning.copy(alpha = 0.5f), fontSize = 7.sp, fontWeight = FontWeight.Bold))
                    drawText(warnLabel, topLeft = Offset(4.dp.toPx(), warnY - warnLabel.size.height - 2.dp.toPx()))
                }

                // Critical threshold line
                if (criticalThreshold != null) {
                    val critY = height - ((criticalThreshold - minVal) / range) * height
                    drawLine(
                        color = MeetColors.error.copy(alpha = 0.3f),
                        start = Offset(0f, critY), end = Offset(width, critY),
                        strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                    )
                    val critLabel = textMeasurer.measure("🔴 ${criticalThreshold.toInt()}", TextStyle(color = MeetColors.error.copy(alpha = 0.5f), fontSize = 7.sp, fontWeight = FontWeight.Bold))
                    drawText(critLabel, topLeft = Offset(4.dp.toPx(), critY - critLabel.size.height - 2.dp.toPx()))
                }

                // Scan line
                drawLine(
                    brush = Brush.verticalGradient(listOf(Color.Transparent, activeColor.copy(alpha = 0.1f), Color.Transparent)),
                    start = Offset(width * scanOffset, 0f), end = Offset(width * scanOffset, height),
                    strokeWidth = 40.dp.toPx()
                )

                if (hasData) {
                    val path = Path()
                    val maxPoints = 100
                    val stepX = width / (maxPoints - 1)
                    val points = displayHistory.toList()

                    points.forEachIndexed { index, value ->
                        val normX = width - (points.size - 1 - index) * stepX
                        val normY = height - ((value - minVal) / range) * height
                        if (index == 0) path.moveTo(normX, normY) else path.lineTo(normX, normY)
                    }

                    // Area fill
                    val fillPath = Path().apply {
                        addPath(path)
                        lineTo(width, height)
                        lineTo(width - (points.size - 1) * stepX, height)
                        close()
                    }
                    drawPath(fillPath, Brush.verticalGradient(listOf(activeColor.copy(alpha = 0.2f), Color.Transparent)))

                    // Glow
                    drawPath(path, activeColor.copy(alpha = 0.2f), style = Stroke(8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    // Core line
                    drawPath(path, activeColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

                    // Live dot
                    val lastVal = points.last()
                    val lastY = height - ((lastVal - minVal) / range) * height
                    drawCircle(activeColor.copy(alpha = 0.3f), 8.dp.toPx(), Offset(width, lastY))
                    drawCircle(Color.White, 3.dp.toPx(), Offset(width, lastY))
                } else {
                    // No data: flat dashed line + pulse
                    val midY = height / 2f
                    drawLine(
                        color = MeetColors.textMuted.copy(alpha = noDataPulse),
                        start = Offset(0f, midY), end = Offset(width, midY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                    )
                    val noDataLabel = textMeasurer.measure("ESPERANDO DATOS...", TextStyle(color = MeetColors.textMuted.copy(alpha = noDataPulse), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp))
                    drawText(noDataLabel, topLeft = Offset(width / 2 - noDataLabel.size.width / 2, midY - noDataLabel.size.height - 8.dp.toPx()))
                }

                // Y-axis labels (min/max)
                val minLabel = textMeasurer.measure("${minVal.toInt()}", TextStyle(color = currentScheme.textColor.copy(alpha = 0.4f), fontSize = 7.sp))
                val maxLabel = textMeasurer.measure("${maxVal.toInt()}", TextStyle(color = currentScheme.textColor.copy(alpha = 0.4f), fontSize = 7.sp))
                drawText(minLabel, topLeft = Offset(width - minLabel.size.width - 2.dp.toPx(), height - minLabel.size.height))
                drawText(maxLabel, topLeft = Offset(width - maxLabel.size.width - 2.dp.toPx(), 0f))
            }
        }
    }
}
