package com.elysium369.meet.education.presentation.sandboxes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Simulador Táctil Interactivo de Geometría Analítica (MEP 11.º Año / Bachillerato por Madurez).
 * Permite explorar de forma constructivista:
 * - Ecuación ordinaria de la circunferencia: (x - h)² + (y - k)² = r²
 * - Ecuación general: x² + y² + Dx + Ey + F = 0
 * - Relaciones de rectas secantes, tangentes y exteriores basadas en distancia y discriminante Δ.
 */
@Composable
fun AnalyticalGeometrySandbox(
    modifier: Modifier = Modifier,
    initialH: Float = 0f,
    initialK: Float = 0f,
    initialR: Float = 3f,
    onEvidenceProduced: ((h: Float, k: Float, r: Float, isTangent: Boolean) -> Unit)? = null
) {
    var h by remember { mutableFloatStateOf(initialH) }
    var k by remember { mutableFloatStateOf(initialK) }
    var r by remember { mutableFloatStateOf(initialR) }

    // Posición de la recta horizontal y = lineY
    var lineY by remember { mutableFloatStateOf(3f) }

    // Distancia del centro a la recta y = lineY: d = |k - lineY|
    val distance = abs(k - lineY)
    val tolerance = 0.08f
    val isTangent = abs(distance - r) < tolerance
    val isSecant = distance < r - tolerance
    val isExterior = distance > r + tolerance

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📐 LABORATORIO GEOMÉTRICO 2D",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = when {
                                isTangent -> "🎯 RECTA TANGENTE (Δ = 0)"
                                isSecant -> "✂️ RECTA SECANTE (Δ > 0)"
                                else -> "🪐 RECTA EXTERIOR (Δ < 0)"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isTangent -> MaterialTheme.colorScheme.primary
                                isSecant -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.outline
                            }
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Canvas Interactivo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val scale = 20f // pixels per unit
                            h = (h + dragAmount.x / scale).coerceIn(-6f, 6f)
                            k = (k - dragAmount.y / scale).coerceIn(-6f, 6f)
                            onEvidenceProduced?.invoke(h, k, r, isTangent)
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val originX = canvasWidth / 2f
                    val originY = canvasHeight / 2f
                    val scale = 18f // 1 unidad = 18 px

                    // 1. Grid cartesiano
                    val gridColor = Color.Gray.copy(alpha = 0.2f)
                    for (i in -10..10) {
                        val gx = originX + i * scale
                        drawLine(gridColor, Offset(gx, 0f), Offset(gx, canvasHeight))
                        val gy = originY + i * scale
                        drawLine(gridColor, Offset(0f, gy), Offset(canvasWidth, gy))
                    }

                    // 2. Ejes principales X e Y
                    val axisColor = Color.Gray.copy(alpha = 0.7f)
                    drawLine(axisColor, Offset(0f, originY), Offset(canvasWidth, originY), strokeWidth = 2f)
                    drawLine(axisColor, Offset(originX, 0f), Offset(originX, canvasHeight), strokeWidth = 2f)

                    // 3. Circunferencia (x - h)² + (y - k)² = r²
                    val centerPxX = originX + h * scale
                    val centerPxY = originY - k * scale
                    val radiusPx = r * scale

                    drawCircle(
                        color = Color(0xFF2196F3).copy(alpha = 0.15f),
                        radius = radiusPx,
                        center = Offset(centerPxX, centerPxY)
                    )
                    drawCircle(
                        color = Color(0xFF2196F3),
                        radius = radiusPx,
                        center = Offset(centerPxX, centerPxY),
                        style = Stroke(width = 3f)
                    )

                    // Centro (h, k)
                    drawCircle(
                        color = Color(0xFFFF5722),
                        radius = 6f,
                        center = Offset(centerPxX, centerPxY)
                    )

                    // 4. Recta horizontal y = lineY
                    val linePyY = originY - lineY * scale
                    val lineColor = when {
                        isTangent -> Color(0xFF4CAF50)
                        isSecant -> Color(0xFFFF9800)
                        else -> Color(0xFF9E9E9E)
                    }
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, linePyY),
                        end = Offset(canvasWidth, linePyY),
                        strokeWidth = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }

                Text(
                    text = "Arrastra en el plano para mover el centro (h, k)",
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline),
                    modifier = Modifier.padding(8.dp).align(Alignment.BottomStart)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Fórmulas matemáticas en vivo
            val hRound = (h * 10).roundToInt() / 10f
            val kRound = (k * 10).roundToInt() / 10f
            val rRound = (r * 10).roundToInt() / 10f
            val rSq = (rRound * rRound * 10).roundToInt() / 10f

            val hSign = if (hRound >= 0) "- $hRound" else "+ ${abs(hRound)}"
            val kSign = if (kRound >= 0) "- $kRound" else "+ ${abs(kRound)}"

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Ecuación Canónica:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "(x $hSign)² + (y $kSign)² = $rSq",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Centro: ($hRound, $kRound) | Radio: $rRound | Dist. a recta y=$lineY: ${(distance * 10).roundToInt() / 10f}",
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Controles de Radio y Recta
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Radio (r): $rRound", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = r,
                        onValueChange = {
                            r = it
                            onEvidenceProduced?.invoke(h, k, r, isTangent)
                        },
                        valueRange = 1f..6f
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Recta y = $lineY", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = lineY,
                        onValueChange = {
                            lineY = (it * 10).roundToInt() / 10f
                            onEvidenceProduced?.invoke(h, k, r, isTangent)
                        },
                        valueRange = -5f..5f
                    )
                }
            }
        }
    }
}
