package com.elysium369.meet.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
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
    
    // Spring animation for smooth needle movement
    val animatedValue by animateFloatAsState(
        targetValue = value.coerceIn(minVal, maxVal),
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessLow
        ),
        label = "gaugeAnimation"
    )

    val progress = if (maxVal == minVal) 0f else ((animatedValue - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
    val sweepAngle = 240f
    val startAngle = 150f
    val currentSweep = progress * sweepAngle

    // Pulse animation for anomaly / no-signal
    val infiniteTransition = rememberInfiniteTransition(label = "gaugePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    // Scanning animation for no-data state
    val scanAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 240f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanAngle"
    )
    
    // Outer glow breathe
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowBreath"
    )

    // Color logic
    val activeColor = when {
        !hasData -> Color(0xFF555577)
        isAnomaly -> Color(0xFFFF003C)
        criticalThreshold != null && animatedValue >= criticalThreshold -> Color(0xFFFF003C)
        warningThreshold != null && animatedValue >= warningThreshold -> Color(0xFFFFD700)
        else -> Color(0xFF39FF14)
    }
    
    val warnFraction = if (warningThreshold != null && maxVal > minVal) 
        ((warningThreshold - minVal) / (maxVal - minVal)).coerceIn(0f, 1f) else 0.75f
    val critFraction = if (criticalThreshold != null && maxVal > minVal) 
        ((criticalThreshold - minVal) / (maxVal - minVal)).coerceIn(0f, 1f) else 0.9f

    val textMeasurer = rememberTextMeasurer()

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2 - 24.dp.toPx()
            val strokeWidth = 8.dp.toPx()
            
            // ── 0. OUTER GLOW RING ──
            drawArc(
                color = activeColor.copy(alpha = glowAlpha),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth * 3f, cap = StrokeCap.Round),
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2)
            )

            // ── 1. BACKGROUND ARC ──
            drawArc(
                color = Color(0xFF060612),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2)
            )
            
            // ── 1b. WARNING & CRITICAL ZONE ARCS ──
            val warnStart = startAngle + warnFraction * sweepAngle
            val warnSweep = (critFraction - warnFraction) * sweepAngle
            val critStart = startAngle + critFraction * sweepAngle
            val critSweep = (1f - critFraction) * sweepAngle
            
            drawArc(
                color = Color(0xFFFFD700).copy(alpha = 0.12f),
                startAngle = warnStart,
                sweepAngle = warnSweep,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2)
            )
            drawArc(
                color = Color(0xFFFF003C).copy(alpha = 0.12f),
                startAngle = critStart,
                sweepAngle = critSweep,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2)
            )

            // ── 2. TICK MARKS WITH NUMBERED LABELS ──
            val tickCount = 40
            val majorInterval = 10
            for (i in 0..tickCount) {
                val angle = startAngle + (i.toFloat() / tickCount) * sweepAngle
                val angleRad = Math.toRadians(angle.toDouble())
                val isMajor = i % majorInterval == 0
                val tickLength = if (isMajor) 14.dp.toPx() else if (i % 5 == 0) 10.dp.toPx() else 6.dp.toPx()
                val tickWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx()
                
                val outerR = radius - strokeWidth / 2 - 3.dp.toPx()
                val start = Offset(
                    (center.x + outerR * cos(angleRad)).toFloat(),
                    (center.y + outerR * sin(angleRad)).toFloat()
                )
                val end = Offset(
                    (center.x + (outerR - tickLength) * cos(angleRad)).toFloat(),
                    (center.y + (outerR - tickLength) * sin(angleRad)).toFloat()
                )
                
                val tickFraction = i.toFloat() / tickCount
                val tickColor = when {
                    !hasData -> Color(0xFF333355)
                    tickFraction <= progress -> activeColor
                    tickFraction >= critFraction -> Color(0xFFFF003C).copy(alpha = 0.25f)
                    tickFraction >= warnFraction -> Color(0xFFFFD700).copy(alpha = 0.2f)
                    else -> Color.DarkGray
                }
                drawLine(
                    color = tickColor.copy(alpha = if (isMajor) 0.9f else if (i % 5 == 0) 0.5f else 0.3f),
                    start = start,
                    end = end,
                    strokeWidth = tickWidth
                )
                
                // Major tick number labels
                if (isMajor) {
                    val labelVal = minVal + tickFraction * (maxVal - minVal)
                    val labelText = if (labelVal >= 1000) "${(labelVal / 1000).toInt()}k"
                                    else if (labelVal == labelVal.toInt().toFloat()) "${labelVal.toInt()}"
                                    else String.format("%.0f", labelVal)
                    val labelR = outerR - tickLength - 10.dp.toPx()
                    val labelOffset = Offset(
                        (center.x + labelR * cos(angleRad)).toFloat(),
                        (center.y + labelR * sin(angleRad)).toFloat()
                    )
                    val measuredText = textMeasurer.measure(
                        text = labelText,
                        style = TextStyle(
                            color = Color.Gray.copy(alpha = 0.6f),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    drawText(
                        textLayoutResult = measuredText,
                        topLeft = Offset(
                            labelOffset.x - measuredText.size.width / 2f,
                            labelOffset.y - measuredText.size.height / 2f
                        )
                    )
                }
            }

            if (hasData) {
                // ── 3. VALUE ARC GLOW ──
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
                    size = Size(radius * 2, radius * 2)
                )

                // ── 4. VALUE ARC CORE ──
                drawArc(
                    color = activeColor,
                    startAngle = startAngle,
                    sweepAngle = currentSweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2)
                )

                // ── 5. NEEDLE / POINTER ──
                val needleAngle = startAngle + currentSweep
                val needleRad = Math.toRadians(needleAngle.toDouble())
                val needleLength = radius - strokeWidth / 2 - 3.dp.toPx()
                
                // Needle shadow
                drawLine(
                    color = activeColor.copy(alpha = 0.15f),
                    start = center,
                    end = Offset(
                        (center.x + needleLength * cos(needleRad)).toFloat(),
                        (center.y + needleLength * sin(needleRad)).toFloat()
                    ),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // Needle core
                drawLine(
                    color = activeColor,
                    start = center,
                    end = Offset(
                        (center.x + needleLength * cos(needleRad)).toFloat(),
                        (center.y + needleLength * sin(needleRad)).toFloat()
                    ),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                
                // Center hub
                drawCircle(color = activeColor, radius = 5.dp.toPx(), center = center)
                drawCircle(color = Color.Black, radius = 3.dp.toPx(), center = center)
                drawCircle(color = activeColor.copy(alpha = 0.5f), radius = 8.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
            } else {
                // ── NO DATA: Scanning arc animation ──
                val scanColor = Color(0xFF39FF14).copy(alpha = 0.4f * pulseAlpha)
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
                    size = Size(radius * 2, radius * 2)
                )
                
                // Center pulsing dot
                drawCircle(
                    color = Color(0xFF39FF14).copy(alpha = pulseAlpha * 0.3f),
                    radius = 12.dp.toPx() * pulseAlpha,
                    center = center
                )
                drawCircle(color = Color(0xFF39FF14).copy(alpha = 0.4f), radius = 3.dp.toPx(), center = center)
            }
            
            // ── 6. MIN / MAX LABELS at arc endpoints ──
            val minAngleRad = Math.toRadians((startAngle + sweepAngle + 12).toDouble())
            val maxAngleRad = Math.toRadians((startAngle - 12).toDouble())
            val labelR = radius + 10.dp.toPx()
            
            val minText = if (minVal == minVal.toInt().toFloat()) "${minVal.toInt()}" else String.format("%.0f", minVal)
            val maxText = if (maxVal >= 1000) "${(maxVal / 1000).toInt()}k" 
                          else if (maxVal == maxVal.toInt().toFloat()) "${maxVal.toInt()}" 
                          else String.format("%.0f", maxVal)
            
            val minMeasured = textMeasurer.measure(minText, TextStyle(color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold))
            val maxMeasured = textMeasurer.measure(maxText, TextStyle(color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold))
            
            drawText(
                textLayoutResult = minMeasured,
                topLeft = Offset(
                    (center.x + labelR * cos(minAngleRad)).toFloat() - minMeasured.size.width / 2f,
                    (center.y + labelR * sin(minAngleRad)).toFloat() - minMeasured.size.height / 2f
                )
            )
            drawText(
                textLayoutResult = maxMeasured,
                topLeft = Offset(
                    (center.x + labelR * cos(maxAngleRad)).toFloat() - maxMeasured.size.width / 2f,
                    (center.y + labelR * sin(maxAngleRad)).toFloat() - maxMeasured.size.height / 2f
                )
            )
        }

        // ── 7. DIGITAL READOUT ──
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = 28.dp)
        ) {
            Text(
                text = label.uppercase(),
                color = Color.Gray.copy(alpha = 0.7f),
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp
            )
            
            if (hasData) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format("%.0f", animatedValue),
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        text = unit.lowercase(),
                        color = activeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 5.dp, start = 2.dp)
                    )
                }
            } else {
                Text(
                    "SIN SEÑAL",
                    color = Color(0xFF555577).copy(alpha = pulseAlpha),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }
            
            if (isAnomaly && hasData) {
                Text(
                    "⚠ ANOMALÍA",
                    color = Color(0xFFFF003C).copy(alpha = pulseAlpha),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
