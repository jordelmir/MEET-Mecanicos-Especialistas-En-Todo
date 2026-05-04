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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun EliteScannerAnimation(
    modifier: Modifier = Modifier,
    scanText: String = "SISTEMAS"
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing)
        ),
        label = "rotation"
    )
    
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val radarColor = Color(0xFF00E5FF)
    val accentColor = Color(0xFF39FF14)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2 * 0.8f

                // Outer decorative hex ring
                drawHexagon(center, radius * 1.1f, radarColor.copy(alpha = 0.1f))
                
                // Pulsing rings
                drawCircle(
                    color = radarColor.copy(alpha = 0.15f),
                    radius = radius * pulse,
                    style = Stroke(width = 2.dp.toPx())
                )
                
                // Static grid
                val gridAlpha = 0.05f
                for (i in 1..4) {
                    drawCircle(
                        color = radarColor.copy(alpha = gridAlpha),
                        radius = radius * (i / 4f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // Radar Sweep
                withTransform({
                    rotate(rotation, center)
                }) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0f to Color.Transparent,
                            0.5f to radarColor.copy(alpha = 0.3f),
                            1f to radarColor
                        ),
                        startAngle = 0f,
                        sweepAngle = 90f,
                        useCenter = true,
                        size = Size(radius * 2, radius * 2),
                        topLeft = Offset(center.x - radius, center.y - radius)
                    )
                    
                    // Leading line
                    drawLine(
                        color = radarColor,
                        start = center,
                        end = Offset(
                            center.x + radius * cos(0f),
                            center.y + radius * sin(0f)
                        ),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Decorative Corner Markers
                drawCornerMarkers(center, radius * 1.2f, accentColor.copy(alpha = 0.6f))
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "SCAN",
                    color = radarColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp
                )
                Text(
                    text = scanText,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun EliteDeletionAnimation(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "deletion")
    
    val glitchOffset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(50, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glitch"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val deleteColor = Color(0xFFFF003C)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2 * 0.7f

                // Outer Red Glow
                drawCircle(
                    color = deleteColor.copy(alpha = 0.1f * alpha),
                    radius = radius * 1.5f
                )

                // Glitchy X
                withTransform({
                    translate(left = glitchOffset, top = 0f)
                }) {
                    drawDtcIcon(center, radius, deleteColor.copy(alpha = alpha))
                }
                
                // Secondary Glitch Ghost
                withTransform({
                    translate(left = -glitchOffset * 0.5f, top = 2f)
                }) {
                    drawDtcIcon(center, radius, Color.Cyan.copy(alpha = 0.3f * alpha))
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHexagon(
    center: Offset,
    radius: Float,
    color: Color
) {
    val path = Path().apply {
        for (i in 0..5) {
            val angle = i * PI / 3
            val x = center.x + radius * cos(angle).toFloat()
            val y = center.y + radius * sin(angle).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawPath(path, color, style = Stroke(width = 1.dp.toPx()))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCornerMarkers(
    center: Offset,
    radius: Float,
    color: Color
) {
    val size = 20f
    // Top Left
    drawLine(color, Offset(center.x - radius, center.y - radius), Offset(center.x - radius + size, center.y - radius), 2f)
    drawLine(color, Offset(center.x - radius, center.y - radius), Offset(center.x - radius, center.y - radius + size), 2f)
    
    // Top Right
    drawLine(color, Offset(center.x + radius, center.y - radius), Offset(center.x + radius - size, center.y - radius), 2f)
    drawLine(color, Offset(center.x + radius, center.y - radius), Offset(center.x + radius, center.y - radius + size), 2f)
    
    // Bottom Left
    drawLine(color, Offset(center.x - radius, center.y + radius), Offset(center.x - radius + size, center.y + radius), 2f)
    drawLine(color, Offset(center.x - radius, center.y + radius), Offset(center.x - radius, center.y + radius - size), 2f)
    
    // Bottom Right
    drawLine(color, Offset(center.x + radius, center.y + radius), Offset(center.x + radius - size, center.y + radius), 2f)
    drawLine(color, Offset(center.x + radius, center.y + radius), Offset(center.x + radius, center.y + radius - size), 2f)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDtcIcon(
    center: Offset,
    radius: Float,
    color: Color
) {
    val thickness = 10f
    val size = radius * 0.8f
    
    // Draw X
    withTransform({
        rotate(45f, center)
    }) {
        drawRect(color, Offset(center.x - thickness / 2, center.y - size), Size(thickness, size * 2))
        drawRect(color, Offset(center.x - size, center.y - thickness / 2), Size(size * 2, thickness))
    }
}
