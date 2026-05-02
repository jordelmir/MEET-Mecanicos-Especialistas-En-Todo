package com.elysium369.meet.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    historyData: List<Float>? = null
) {
    val localHistory = remember { mutableStateListOf<Float>() }
    val displayHistory = historyData ?: localHistory

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    val scanOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanOffset"
    )

    val activeColor = when {
        isAnomaly -> Color(0xFFFF003C)
        criticalThreshold != null && currentValue >= criticalThreshold -> Color(0xFFFF003C)
        warningThreshold != null && currentValue >= warningThreshold -> Color(0xFFFFD700)
        else -> Color(0xFF39FF14)
    }

    if (historyData == null) {
        LaunchedEffect(currentValue) {
            localHistory.add(currentValue)
            if (localHistory.size > 100) {
                localHistory.removeAt(0)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(Color(0xFF0A0E1A), RoundedCornerShape(16.dp))
            .border(1.dp, activeColor.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        label.uppercase(), 
                        color = Color.Gray, 
                        fontSize = 10.sp, 
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format("%.1f", currentValue),
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = " $unit",
                            color = activeColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                        )
                    }
                }
                
                if (isAnomaly) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFF003C).copy(alpha = 0.1f * pulseAlpha), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFFFF003C).copy(alpha = pulseAlpha), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("CRITICAL", color = Color(0xFFFF003C), fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                val width = size.width
                val height = size.height
                val range = (maxVal - minVal).coerceAtLeast(0.1f)

                // 1. Cyber Grid
                clipRect {
                    val gridStep = 20.dp.toPx()
                    for (x in 0..(width / gridStep).toInt()) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.03f),
                            start = Offset(x * gridStep, 0f),
                            end = Offset(x * gridStep, height),
                            strokeWidth = 1f
                        )
                    }
                    for (y in 0..(height / gridStep).toInt()) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.03f),
                            start = Offset(0f, y * gridStep),
                            end = Offset(width, y * gridStep),
                            strokeWidth = 1f
                        )
                    }
                }

                // 2. Scan Line Effect
                drawLine(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to activeColor.copy(alpha = 0.1f),
                        1f to Color.Transparent
                    ),
                    start = Offset(width * scanOffset, 0f),
                    end = Offset(width * scanOffset, height),
                    strokeWidth = 40.dp.toPx()
                )

                if (displayHistory.isNotEmpty()) {
                    val path = Path()
                    val maxPoints = 100
                    val stepX = width / (maxPoints - 1)
                    val points = displayHistory.toList()
                    
                    points.forEachIndexed { index, value ->
                        val normX = width - (points.size - 1 - index) * stepX
                        val normY = height - ((value - minVal) / range) * height
                        
                        if (index == 0) path.moveTo(normX, normY)
                        else path.lineTo(normX, normY)
                    }

                    // 3. Area Fill
                    val fillPath = Path().apply {
                        addPath(path)
                        lineTo(width, height)
                        lineTo(width - (points.size - 1) * stepX, height)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(activeColor.copy(alpha = 0.2f), Color.Transparent),
                            startY = 0f,
                            endY = height
                        )
                    )

                    // 4. Glowing Path
                    drawPath(
                        path = path,
                        color = activeColor.copy(alpha = 0.2f),
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    
                    // 5. Core Laser Path
                    drawPath(
                        path = path,
                        color = activeColor,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // 6. Current Point Dot
                    val lastValue = points.last()
                    val lastX = width
                    val lastY = height - ((lastValue - minVal) / range) * height
                    
                    drawCircle(color = activeColor.copy(alpha = 0.3f), radius = 8.dp.toPx(), center = Offset(lastX, lastY))
                    drawCircle(color = Color.White, radius = 3.dp.toPx(), center = Offset(lastX, lastY))
                }
            }
        }
    }
}

