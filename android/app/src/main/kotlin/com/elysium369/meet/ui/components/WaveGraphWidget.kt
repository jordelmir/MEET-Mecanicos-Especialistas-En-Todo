package com.elysium369.meet.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
    // Fallback history if not provided externally
    val localHistory = remember { mutableStateListOf<Float>() }
    
    // Use external history if provided, otherwise use local
    val displayHistory = historyData ?: localHistory

    // Animation for AI Alert
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = if (riskLevel > 0.7f) 1.0f else 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (riskLevel > 0.7f) 400 else 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Determine the color based on thresholds
    val lineColor = when {
        isAnomaly -> Color(0xFFFF3366) // Vibrant Red for AI Anomaly
        criticalThreshold != null && currentValue >= criticalThreshold -> Color(0xFFFF3366) // Red
        warningThreshold != null && currentValue >= warningThreshold -> Color(0xFFFFB300) // Orange/Amber
        else -> Color(0xFF00FFCC) // Neon Cyan (Safe)
    }

    // Update local history if no external history is provided
    if (historyData == null) {
        LaunchedEffect(currentValue) {
            localHistory.add(currentValue)
            if (localHistory.size > 100) {
                localHistory.removeAt(0)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(label, color = Color.Gray, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${String.format("%.1f", currentValue)}",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = " $unit",
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
            if (isAnomaly) {
                Surface(
                    color = Color(0xFFFF3366).copy(alpha = pulseAlpha),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                        .border(1.dp, Color(0xFFFF3366), RoundedCornerShape(4.dp))
                ) {
                    Text(
                        "AI ALERT", 
                        color = Color.White, 
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp
                    )
                }
            } else {
                // Percentage indicator (subtle)
                val percent = ((currentValue - minVal) / (maxVal - minVal)).coerceIn(0f, 1f) * 100
                Surface(
                    color = lineColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.border(0.5.dp, lineColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                ) {
                    Text(
                        "${percent.toInt()}%", 
                        color = lineColor, 
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
            val width = size.width
            val height = size.height
            
            // 1. Draw background grid lines (improved)
            for (i in 0..4) {
                val y = (height / 4) * i
                drawLine(
                    color = Color.White.copy(alpha = 0.03f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
            }
            
            // Vertical grid lines
            for (i in 0..10) {
                val x = (width / 10) * i
                drawLine(
                    color = Color.White.copy(alpha = 0.02f),
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1f
                )
            }
            
            if (displayHistory.isEmpty()) return@Canvas
            
            val path = Path()
            val maxPoints = if (historyData != null) 200 else 100
            val currentPoints = displayHistory.size
            val stepX = width / (maxPoints - 1).coerceAtLeast(1).toFloat()
            
            val range = (maxVal - minVal).coerceAtLeast(0.1f)
            
            displayHistory.forEachIndexed { index, value ->
                val normalizedValue = ((value - minVal) / range).coerceIn(0f, 1f)
                val x = width - ((currentPoints - 1 - index) * stepX)
                val y = height - (normalizedValue * height)
                
                if (index == 0 || x < 0) {
                    path.moveTo(x.coerceAtLeast(0f), y)
                } else {
                    path.lineTo(x, y)
                }
            }
            
            // 2. Fill area with gradient
            val fillPath = Path().apply {
                addPath(path)
                val lastX = width
                val firstX = (width - ((currentPoints - 1) * stepX)).coerceAtLeast(0f)
                lineTo(lastX, height)
                lineTo(firstX, height)
                close()
            }
            
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.2f), Color.Transparent),
                    startY = 0f,
                    endY = height
                )
            )

            // 3. Glowing Path (Outer Glow - Dynamic based on Anomaly)
            val glowWidth = if (isAnomaly) 16f else 8f
            drawPath(
                path = path,
                color = lineColor.copy(alpha = if (isAnomaly) pulseAlpha * 0.4f else 0.15f),
                style = Stroke(width = glowWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // 4. Core Path with Gradient (LASER EFFECT)
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(lineColor.copy(alpha = 0.7f), lineColor, lineColor.copy(alpha = 0.7f)),
                    start = Offset(0f, 0f),
                    end = Offset(width, height)
                ),
                style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            
            // 5. Dynamic Dot
            if (displayHistory.isNotEmpty()) {
                val lastValue = displayHistory.last()
                val normalizedLast = ((lastValue - minVal) / range).coerceIn(0f, 1f)
                val lastX = width
                val lastY = height - (normalizedLast * height)
                
                // Outer ring pulse for anomalies
                if (isAnomaly) {
                    drawCircle(
                        color = lineColor.copy(alpha = pulseAlpha * 0.5f), 
                        radius = 12f * pulseScale, 
                        center = Offset(lastX, lastY)
                    )
                }

                drawCircle(color = lineColor.copy(alpha = 0.5f), radius = 8f, center = Offset(lastX, lastY))
                drawCircle(color = Color.White, radius = 3.5f, center = Offset(lastX, lastY))
            }
        }
    }
}

