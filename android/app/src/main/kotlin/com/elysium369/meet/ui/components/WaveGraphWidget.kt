package com.elysium369.meet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun WaveGraphWidget(
    label: String,
    currentValue: Float,
    minVal: Float,
    maxVal: Float,
    unit: String,
    lineColor: Color = Color(0xFF00FFCC) // Neon Cyan
) {
    // Keep a rolling history of the last 50 data points
    val history = remember { mutableStateListOf<Float>() }
    
    // Add new value to history when it changes
    LaunchedEffect(currentValue) {
        history.add(currentValue)
        if (history.size > 50) {
            history.removeAt(0)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(label, color = Color.Gray, style = MaterialTheme.typography.labelMedium)
            Text(
                text = "${String.format("%.1f", currentValue)} $unit",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            val width = size.width
            val height = size.height
            
            // Draw background grid lines
            drawLine(
                color = Color.DarkGray.copy(alpha = 0.3f),
                start = Offset(0f, height / 2),
                end = Offset(width, height / 2),
                strokeWidth = 1f
            )
            drawLine(
                color = Color.DarkGray.copy(alpha = 0.3f),
                start = Offset(0f, height),
                end = Offset(width, height),
                strokeWidth = 1f
            )
            
            if (history.isEmpty()) return@Canvas
            
            val path = Path()
            val stepX = width / 49f // 50 points means 49 intervals
            
            val range = maxVal - minVal
            
            history.forEachIndexed { index, value ->
                // Normalize value between 0 and 1
                val normalizedValue = ((value - minVal) / range).coerceIn(0f, 1f)
                
                // Invert Y axis (0 is top in Canvas, but we want 0 at bottom)
                val x = index * stepX
                val y = height - (normalizedValue * height)
                
                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            
            // Glow Path
            drawPath(
                path = path,
                color = lineColor.copy(alpha = 0.3f),
                style = Stroke(
                    width = 12f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Core Path
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(
                    width = 4f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            
            // Draw a dot at the end
            if (history.isNotEmpty()) {
                val lastValue = history.last()
                val normalizedLast = ((lastValue - minVal) / range).coerceIn(0f, 1f)
                val lastX = (history.size - 1) * stepX
                val lastY = height - (normalizedLast * height)
                
                drawCircle(
                    color = Color.White,
                    radius = 6f,
                    center = Offset(lastX, lastY)
                )
            }
        }
    }
}
