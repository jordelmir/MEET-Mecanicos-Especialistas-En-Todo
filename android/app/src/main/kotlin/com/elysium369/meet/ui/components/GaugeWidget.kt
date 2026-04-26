package com.elysium369.meet.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GaugeWidget(
    label: String,
    value: Float,
    minVal: Float = 0f,
    maxVal: Float = 100f,
    unit: String,
    warningThreshold: Float? = null,
    criticalThreshold: Float? = null,
    modifier: Modifier = Modifier
) {
    // Spring animation for smooth needle movement
    val animatedValue by animateFloatAsState(
        targetValue = value.coerceIn(minVal, maxVal),
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "gaugeAnimation"
    )

    val progress = if (animatedValue <= minVal) 0f else ((animatedValue - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
    val sweepAngle = 270f
    val startAngle = 135f
    val currentSweep = progress * sweepAngle

    // Color logic
    val activeColor = when {
        criticalThreshold != null && animatedValue >= criticalThreshold -> Color(0xFFFF003C) // Neon Red
        warningThreshold != null && animatedValue >= warningThreshold -> Color(0xFFFFD700) // Neon Yellow
        else -> Color(0xFF00FFCC) // Neon Cyan
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(160.dp)
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 12.dp.toPx()
            val size = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            // Background Arc (Dark)
            drawArc(
                color = Color(0xFF1E1E1E),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = size,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Value Arc GLOW
            drawArc(
                color = activeColor.copy(alpha = 0.3f),
                startAngle = startAngle,
                sweepAngle = currentSweep,
                useCenter = false,
                topLeft = topLeft,
                size = size,
                style = Stroke(width = strokeWidth * 2.5f, cap = StrokeCap.Round)
            )

            // Value Arc CORE
            drawArc(
                color = activeColor,
                startAngle = startAngle,
                sweepAngle = currentSweep,
                useCenter = false,
                topLeft = topLeft,
                size = size,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label.uppercase(),
                color = Color.LightGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = String.format("%.1f", animatedValue),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = unit,
                color = activeColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
