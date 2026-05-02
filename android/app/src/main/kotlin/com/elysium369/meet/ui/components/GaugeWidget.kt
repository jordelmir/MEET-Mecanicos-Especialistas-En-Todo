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
import androidx.compose.ui.text.font.FontWeight
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

    // Animation for AI Alert pulse
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Color logic
    val activeColor = when {
        isAnomaly -> Color(0xFFFF003C)
        criticalThreshold != null && animatedValue >= criticalThreshold -> Color(0xFFFF003C)
        warningThreshold != null && animatedValue >= warningThreshold -> Color(0xFFFFD700)
        else -> Color(0xFF39FF14)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2 - 20.dp.toPx()
            val strokeWidth = 8.dp.toPx()
            
            // 1. Background Arc
            drawArc(
                color = Color(0xFF060612),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 2. Ticks
            val tickCount = 40
            for (i in 0..tickCount) {
                val angle = startAngle + (i.toFloat() / tickCount) * sweepAngle
                val angleRad = Math.toRadians(angle.toDouble())
                val isMajor = i % 5 == 0
                val tickLength = if (isMajor) 15.dp.toPx() else 8.dp.toPx()
                val tickWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx()
                
                val start = Offset(
                    (center.x + (radius + 5.dp.toPx()) * cos(angleRad)).toFloat(),
                    (center.y + (radius + 5.dp.toPx()) * sin(angleRad)).toFloat()
                )
                val end = Offset(
                    (center.x + (radius + 5.dp.toPx() + tickLength) * cos(angleRad)).toFloat(),
                    (center.y + (radius + 5.dp.toPx() + tickLength) * sin(angleRad)).toFloat()
                )
                
                val tickColor = if (startAngle + currentSweep >= angle) activeColor else Color.DarkGray
                drawLine(
                    color = tickColor.copy(alpha = if (isMajor) 0.8f else 0.4f),
                    start = start,
                    end = end,
                    strokeWidth = tickWidth
                )
            }

            // 3. Value Arc GLOW
            drawArc(
                brush = Brush.sweepGradient(
                    0f to activeColor.copy(alpha = 0f),
                    0.5f to activeColor.copy(alpha = 0.3f),
                    1f to activeColor.copy(alpha = 0.6f)
                ),
                startAngle = startAngle,
                sweepAngle = currentSweep,
                useCenter = false,
                style = Stroke(width = strokeWidth * 2f, cap = StrokeCap.Round)
            )

            // 4. Value Arc CORE
            drawArc(
                color = activeColor,
                startAngle = startAngle,
                sweepAngle = currentSweep,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 5. Needle / Pointer
            val needleAngle = startAngle + currentSweep
            val needleRad = Math.toRadians(needleAngle.toDouble())
            val needleLength = radius + 10.dp.toPx()
            
            drawLine(
                color = activeColor,
                start = center,
                end = Offset(
                    (center.x + needleLength * cos(needleRad)).toFloat(),
                    (center.y + needleLength * sin(needleRad)).toFloat()
                ),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            
            drawCircle(color = activeColor, radius = 4.dp.toPx(), center = center)
            drawCircle(color = Color.Black, radius = 2.dp.toPx(), center = center)
        }

        // 6. Digital Readout
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = 30.dp)
        ) {
            Text(
                text = label.uppercase(),
                color = Color.Gray,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format("%.0f", animatedValue),
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = unit.lowercase(),
                    color = activeColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                )
            }
            
            if (isAnomaly) {
                Text(
                    "ANOMALY",
                    color = Color(0xFFFF003C).copy(alpha = pulseAlpha),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}
