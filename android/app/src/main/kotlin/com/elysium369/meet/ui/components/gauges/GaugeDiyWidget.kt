package com.elysium369.meet.ui.components.gauges

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom DIY Gauge Widget: Form your own clock design.
 * Selectable borders, ticks/scales, needle styles, and user-uploaded background images.
 */
@Composable
fun GaugeDiyWidget(
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
    val context = LocalContext.current
    val gaugeStyleManager = remember { GaugeStyleManager(context) }
    
    // Listen to changes in the DIY designer settings
    val trigger = GaugeStyleManager.diyUpdateTrigger
    
    val diyBgType = remember(trigger) { gaugeStyleManager.getDiyBgType() }
    val diyBgPreset = remember(trigger) { gaugeStyleManager.getDiyBgPresetIndex() }
    val diyBgUri = remember(trigger) { gaugeStyleManager.getDiyBgImageUri() }
    val diyBezel = remember(trigger) { gaugeStyleManager.getDiyBezelStyle() }
    val diyNeedle = remember(trigger) { gaugeStyleManager.getDiyNeedleStyle() }
    val diyTicks = remember(trigger) { gaugeStyleManager.getDiyTicksStyle() }
    val diyAccentColor = remember(trigger) { Color(gaugeStyleManager.getDiyAccentColor()) }
    val diyAccentColor2 = remember(trigger) { Color(gaugeStyleManager.getDiyAccentColor2()) }
    val diyGlowIntensity = remember(trigger) { gaugeStyleManager.getDiyGlowIntensity() }
    val diyImageOpacity = remember(trigger) { gaugeStyleManager.getDiyImageOpacity() }
    val diyGaugeName = remember(trigger) { gaugeStyleManager.getDiyGaugeName() }

    val colorScheme = LocalGaugeColorScheme.current

    val animatedValue by animateFloatAsState(
        targetValue = value.coerceIn(minVal, maxVal),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 120f),
        label = "diyGauge"
    )

    // Load custom user image from URI if selected
    val userBitmap = remember(diyBgUri, diyBgType) {
        if (diyBgType == 2 && diyBgUri.isNotEmpty()) {
            try {
                val uri = android.net.Uri.parse(diyBgUri)
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                bitmap?.asImageBitmap()
            } catch (e: Exception) {
                android.util.Log.e("GaugeDiyWidget", "Error loading DIY background image: ${e.message}")
                null
            }
        } else {
            null
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val labelText = if (diyGaugeName.isNotEmpty()) diyGaugeName else label

    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(8.dp)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val center = Offset(w / 2f, h / 2f)
                val radius = w / 2f
                val innerRadius = radius * 0.85f

                // Font sizing based on widget diameter
                val valueFontSize = (w * 0.14f).coerceIn(16f, 32f)
                val labelFontSize = (w * 0.05f).coerceIn(8f, 12f)
                val unitFontSize = (w * 0.045f).coerceIn(7f, 10f)

                val labelMeasured = textMeasurer.measure(
                    labelText.uppercase(),
                    TextStyle(
                        color = colorScheme.labelColor.copy(alpha = 0.7f),
                        fontSize = labelFontSize.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                )

                onDrawBehind {
                    val activeColor = when {
                        isAnomaly -> MeetColors.error
                        criticalThreshold != null && animatedValue >= criticalThreshold -> MeetColors.error
                        warningThreshold != null && animatedValue >= warningThreshold -> MeetColors.warning
                        else -> diyAccentColor
                    }
                    val themeTextColor = if (activeColor == diyAccentColor) colorScheme.textColor else activeColor
                    val angleRange = 270f
                    val startAngle = 135f
                    val progress = if (maxVal == minVal) 0f else ((animatedValue - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                    val activeAngle = startAngle + progress * angleRange

                    // 1. DRAW BACKGROUND
                    val clipPath = Path().apply {
                        addOval(androidx.compose.ui.geometry.Rect(0f, 0f, w, h))
                    }

                    clipPath(clipPath) {
                        when (diyBgType) {
                            1 -> { // Preset Backgrounds
                                when (diyBgPreset) {
                                    0 -> { // Metal Cepillado
                                        drawRect(
                                            Brush.sweepGradient(
                                                colors = listOf(Color(0xFF2C3E50), Color(0xFFBDC3C7), Color(0xFF2C3E50), Color(0xFFBDC3C7), Color(0xFF2C3E50)),
                                                center = center
                                            )
                                        )
                                    }
                                    1 -> { // Fibra de Carbono
                                        drawRect(Color(0xFF15181E))
                                        val gridSize = 12.dp.toPx()
                                        for (x in 0..(w / gridSize).toInt()) {
                                            drawLine(Color.Black.copy(alpha = 0.4f), Offset(x * gridSize, 0f), Offset(x * gridSize, h), 2f)
                                            drawLine(Color.White.copy(alpha = 0.05f), Offset(x * gridSize + gridSize/2, 0f), Offset(x * gridSize + gridSize/2, h), 1f)
                                        }
                                        for (y in 0..(h / gridSize).toInt()) {
                                            drawLine(Color.Black.copy(alpha = 0.4f), Offset(0f, y * gridSize), Offset(w, y * gridSize), 2f)
                                            drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, y * gridSize + gridSize/2), Offset(w, y * gridSize + gridSize/2), 1f)
                                        }
                                    }
                                    2 -> { // Rejilla Cyber
                                        drawRect(Color(0xFF030A16))
                                        val cSize = 20.dp.toPx()
                                        for (x in 0..(w / cSize).toInt()) {
                                            drawLine(activeColor.copy(alpha = 0.05f), Offset(x * cSize, 0f), Offset(x * cSize, h), 1f)
                                        }
                                        for (y in 0..(h / cSize).toInt()) {
                                            drawLine(activeColor.copy(alpha = 0.05f), Offset(0f, y * cSize), Offset(w, y * cSize), 1f)
                                        }
                                    }
                                    3 -> { // Espacio Cósmico
                                        drawRect(
                                            Brush.radialGradient(
                                                colors = listOf(Color(0xFF1E0D35), Color(0xFF060312)),
                                                center = center,
                                                radius = radius
                                            )
                                        )
                                        // Tiny stars (fixed coordinates)
                                        val starPoints = listOf(
                                            Offset(w * 0.2f, h * 0.3f), Offset(w * 0.8f, h * 0.25f),
                                            Offset(w * 0.3f, h * 0.75f), Offset(w * 0.75f, h * 0.7f),
                                            Offset(w * 0.5f, h * 0.15f), Offset(w * 0.15f, h * 0.6f),
                                            Offset(w * 0.45f, h * 0.55f), Offset(w * 0.6f, h * 0.4f)
                                        )
                                        starPoints.forEach { pos ->
                                            drawCircle(Color.White.copy(alpha = 0.7f), 1.5.dp.toPx(), pos)
                                        }
                                    }
                                    4 -> { // Lava Volcánica
                                        drawRect(
                                            Brush.radialGradient(
                                                colors = listOf(Color(0xFF4A0000), Color(0xFF1A0000), Color(0xFF0A0000)),
                                                center = center,
                                                radius = radius
                                            )
                                        )
                                        // Crack lines
                                        val crackColor = Color(0xFFFF4500).copy(alpha = 0.3f)
                                        val crackWidth = 2.dp.toPx()
                                        // Simple fixed crack coordinates
                                        drawLine(crackColor, Offset(w * 0.2f, h * 0.2f), Offset(w * 0.5f, h * 0.4f), crackWidth)
                                        drawLine(crackColor, Offset(w * 0.5f, h * 0.4f), Offset(w * 0.4f, h * 0.7f), crackWidth)
                                        drawLine(crackColor, Offset(w * 0.4f, h * 0.7f), Offset(w * 0.8f, h * 0.8f), crackWidth)
                                        drawLine(crackColor, Offset(w * 0.5f, h * 0.4f), Offset(w * 0.7f, h * 0.3f), crackWidth)
                                        drawLine(crackColor, Offset(w * 0.7f, h * 0.3f), Offset(w * 0.9f, h * 0.5f), crackWidth)
                                        
                                        // Glow intersections
                                        drawCircle(Color(0xFFFF4500).copy(alpha = 0.4f), 8.dp.toPx(), Offset(w * 0.5f, h * 0.4f))
                                        drawCircle(Color(0xFFFF4500).copy(alpha = 0.4f), 6.dp.toPx(), Offset(w * 0.4f, h * 0.7f))
                                        drawCircle(Color(0xFFFF4500).copy(alpha = 0.4f), 6.dp.toPx(), Offset(w * 0.7f, h * 0.3f))
                                    }
                                    5 -> { // Placa de Circuito
                                        drawRect(Color(0xFF0A0E14))
                                        val traceColor = activeColor.copy(alpha = 0.08f)
                                        val traceWidth = 1.5.dp.toPx()
                                        val nodeColor = activeColor.copy(alpha = 0.15f)
                                        val nodeRadius = 2.dp.toPx()
                                        
                                        // Trace 1
                                        drawLine(traceColor, Offset(w * 0.1f, h * 0.3f), Offset(w * 0.4f, h * 0.3f), traceWidth)
                                        drawLine(traceColor, Offset(w * 0.4f, h * 0.3f), Offset(w * 0.5f, h * 0.2f), traceWidth)
                                        drawCircle(nodeColor, nodeRadius, Offset(w * 0.1f, h * 0.3f))
                                        drawCircle(nodeColor, nodeRadius, Offset(w * 0.5f, h * 0.2f))
                                        
                                        // Trace 2
                                        drawLine(traceColor, Offset(w * 0.8f, h * 0.4f), Offset(w * 0.6f, h * 0.4f), traceWidth)
                                        drawLine(traceColor, Offset(w * 0.6f, h * 0.4f), Offset(w * 0.5f, h * 0.5f), traceWidth)
                                        drawCircle(nodeColor, nodeRadius, Offset(w * 0.8f, h * 0.4f))
                                        drawCircle(nodeColor, nodeRadius, Offset(w * 0.5f, h * 0.5f))
                                        
                                        // Trace 3
                                        drawLine(traceColor, Offset(w * 0.2f, h * 0.8f), Offset(w * 0.5f, h * 0.8f), traceWidth)
                                        drawLine(traceColor, Offset(w * 0.5f, h * 0.8f), Offset(w * 0.6f, h * 0.7f), traceWidth)
                                        drawCircle(nodeColor, nodeRadius, Offset(w * 0.2f, h * 0.8f))
                                        drawCircle(nodeColor, nodeRadius, Offset(w * 0.6f, h * 0.7f))
                                    }
                                    6 -> { // Panal de Abeja
                                        drawRect(Color(0xFF0D1117))
                                        val hexColor = activeColor.copy(alpha = 0.06f)
                                        val hexWidth = 1.dp.toPx()
                                        val sizeH = 14.dp.toPx()
                                        val hDist = sizeH * 1.5f
                                        val vDist = sizeH * Math.sqrt(3.0).toFloat()
                                        
                                        for (row in 0..(h / vDist).toInt() + 1) {
                                            val xOffset = if (row % 2 == 0) 0f else hDist * 0.5f
                                            for (col in 0..(w / hDist).toInt() + 1) {
                                                val cx = col * hDist + xOffset
                                                val cy = row * vDist
                                                
                                                val path = Path().apply {
                                                    for (i in 0..5) {
                                                        val angleRad = Math.toRadians(i * 60.0)
                                                        val px = cx + sizeH * cos(angleRad).toFloat()
                                                        val py = cy + sizeH * sin(angleRad).toFloat()
                                                        if (i == 0) moveTo(px, py) else lineTo(px, py)
                                                    }
                                                    close()
                                                }
                                                drawPath(path, hexColor, style = Stroke(width = hexWidth))
                                            }
                                        }
                                    }
                                    else -> { // Nebulosa Galáctica
                                        drawRect(Color(0xFF060312))
                                        drawRect(
                                            Brush.radialGradient(
                                                colors = listOf(Color(0xFF4A148C).copy(alpha = 0.5f), Color.Transparent),
                                                center = Offset(w * 0.3f, h * 0.4f),
                                                radius = radius * 0.8f
                                            )
                                        )
                                        drawRect(
                                            Brush.radialGradient(
                                                colors = listOf(Color(0xFFE91E63).copy(alpha = 0.3f), Color.Transparent),
                                                center = Offset(w * 0.7f, h * 0.5f),
                                                radius = radius * 0.7f
                                            )
                                        )
                                        drawRect(
                                            Brush.radialGradient(
                                                colors = listOf(Color(0xFF1A237E).copy(alpha = 0.4f), Color.Transparent),
                                                center = Offset(w * 0.5f, h * 0.8f),
                                                radius = radius * 0.9f
                                            )
                                        )
                                    }
                                }
                            }
                            2 -> { // User Custom Image
                                if (userBitmap != null) {
                                    drawImage(
                                        image = userBitmap,
                                        dstSize = IntSize(w.toInt(), h.toInt())
                                    )
                                } else {
                                    drawRect(Color(0xFF1E293B))
                                }
                                val darkOpacity = (1f - diyImageOpacity) * 0.9f
                                if (darkOpacity > 0f) {
                                    drawRect(Color.Black.copy(alpha = darkOpacity))
                                }
                            }
                            else -> { // Standard Gradient
                                drawRect(
                                    Brush.radialGradient(
                                        colors = listOf(diyAccentColor.copy(alpha = 0.08f), Color(0xFF1F2937), Color(0xFF111827)),
                                        center = center,
                                        radius = radius
                                    )
                                )
                            }
                        }
                    }

                    // 2. DRAW TICKS / SCALES
                    when (diyTicks) {
                        0 -> { // Corona Completa (Radial Ticks)
                            val step = 10f
                            for (a in 0..(angleRange / step).toInt()) {
                                val currentAngle = startAngle + (a * step)
                                val rad = Math.toRadians(currentAngle.toDouble())
                                val startOffset = Offset(
                                    center.x + (innerRadius - 8.dp.toPx()) * cos(rad).toFloat(),
                                    center.y + (innerRadius - 8.dp.toPx()) * sin(rad).toFloat()
                                )
                                val endOffset = Offset(
                                    center.x + innerRadius * cos(rad).toFloat(),
                                    center.y + innerRadius * sin(rad).toFloat()
                                )
                                val tickColor = if (currentAngle <= activeAngle) activeColor else Color.White.copy(alpha = 0.15f)
                                drawLine(tickColor, startOffset, endOffset, 2.dp.toPx())
                            }
                        }
                        1 -> { // Segmentos JDM (Arcs)
                            val strokeW = 6.dp.toPx()
                            drawArc(
                                color = Color.White.copy(alpha = 0.1f),
                                startAngle = startAngle,
                                sweepAngle = angleRange,
                                useCenter = false,
                                style = Stroke(width = strokeW, cap = StrokeCap.Round),
                                size = Size(innerRadius * 2, innerRadius * 2),
                                topLeft = Offset(center.x - innerRadius, center.y - innerRadius)
                            )
                            drawArc(
                                color = activeColor,
                                startAngle = startAngle,
                                sweepAngle = progress * angleRange,
                                useCenter = false,
                                style = Stroke(width = strokeW, cap = StrokeCap.Round),
                                size = Size(innerRadius * 2, innerRadius * 2),
                                topLeft = Offset(center.x - innerRadius, center.y - innerRadius)
                            )
                        }
                        2 -> { // Puntos Cardinales (Major dots)
                            val angles = listOf(135f, 180f, 225f, 270f, 315f, 360f, 405f)
                            angles.forEach { currentAngle ->
                                val rad = Math.toRadians(currentAngle.toDouble())
                                val pos = Offset(
                                    center.x + (innerRadius - 4.dp.toPx()) * cos(rad).toFloat(),
                                    center.y + (innerRadius - 4.dp.toPx()) * sin(rad).toFloat()
                                )
                                val dotColor = if (currentAngle <= activeAngle) activeColor else Color.White.copy(alpha = 0.2f)
                                drawCircle(dotColor, 4.dp.toPx(), pos)
                            }
                        }
                        3 -> { // Sin Marcas
                            // Nothing drawn
                        }
                        4 -> { // Barra Gradiente
                            val strokeW = 10.dp.toPx()
                            drawArc(
                                color = Color.White.copy(alpha = 0.08f),
                                startAngle = startAngle,
                                sweepAngle = angleRange,
                                useCenter = false,
                                style = Stroke(width = strokeW, cap = StrokeCap.Round),
                                size = Size(innerRadius * 2, innerRadius * 2),
                                topLeft = Offset(center.x - innerRadius, center.y - innerRadius)
                            )
                            clipPath(Path().apply {
                                addArc(
                                    androidx.compose.ui.geometry.Rect(center.x - innerRadius, center.y - innerRadius, center.x + innerRadius, center.y + innerRadius),
                                    startAngle,
                                    progress * angleRange
                                )
                            }) {
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        colors = listOf(diyAccentColor2, activeColor),
                                        center = center
                                    ),
                                    startAngle = startAngle,
                                    sweepAngle = progress * angleRange,
                                    useCenter = false,
                                    style = Stroke(width = strokeW, cap = StrokeCap.Round),
                                    size = Size(innerRadius * 2, innerRadius * 2),
                                    topLeft = Offset(center.x - innerRadius, center.y - innerRadius)
                                )
                            }
                        }
                        5 -> { // LED Bar
                            val totalLEDs = 27
                            val ledWidth = 3.dp.toPx()
                            val ledHeight = 6.dp.toPx()
                            val step = angleRange / (totalLEDs - 1)
                            for (i in 0 until totalLEDs) {
                                val currentAngle = startAngle + i * step
                                val isLit = currentAngle <= activeAngle
                                val rad = Math.toRadians(currentAngle.toDouble())
                                val cx = center.x + (innerRadius - ledHeight / 2) * cos(rad).toFloat()
                                val cy = center.y + (innerRadius - ledHeight / 2) * sin(rad).toFloat()
                                
                                val color = if (isLit) activeColor else Color.White.copy(alpha = 0.05f)
                                withTransform({
                                    rotate(currentAngle + 90f, pivot = Offset(cx, cy))
                                }) {
                                    drawRoundRect(
                                        color = color,
                                        topLeft = Offset(cx - ledWidth / 2f, cy - ledHeight / 2f),
                                        size = Size(ledWidth, ledHeight),
                                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                                    )
                                }
                            }
                        }
                        6 -> { // Triángulos
                            val step = 30f
                            val numMarks = (angleRange / step).toInt()
                            for (i in 0..numMarks) {
                                val currentAngle = startAngle + (i * step)
                                val rad = Math.toRadians(currentAngle.toDouble())
                                
                                val isLit = currentAngle <= activeAngle
                                val color = if (isLit) activeColor else Color.White.copy(alpha = 0.12f)
                                
                                val baseCenterRadius = innerRadius
                                val apexRadius = innerRadius - 8.dp.toPx()
                                
                                val apexX = center.x + apexRadius * cos(rad).toFloat()
                                val apexY = center.y + apexRadius * sin(rad).toFloat()
                                
                                val leftRad = rad - Math.toRadians(4.0)
                                val leftX = center.x + baseCenterRadius * cos(leftRad).toFloat()
                                val leftY = center.y + baseCenterRadius * sin(leftRad).toFloat()
                                
                                val rightRad = rad + Math.toRadians(4.0)
                                val rightX = center.x + baseCenterRadius * cos(rightRad).toFloat()
                                val rightY = center.y + baseCenterRadius * sin(rightRad).toFloat()
                                
                                val triPath = Path().apply {
                                    moveTo(apexX, apexY)
                                    lineTo(leftX, leftY)
                                    lineTo(rightX, rightY)
                                    close()
                                }
                                drawPath(triPath, color)
                            }
                        }
                        7 -> { // Reloj 12H
                            val majorStep = 22.5f
                            val totalMajor = (angleRange / majorStep).toInt()
                            for (i in 0..totalMajor) {
                                val currentAngle = startAngle + i * majorStep
                                val isLit = currentAngle <= activeAngle
                                val majorRad = Math.toRadians(currentAngle.toDouble())
                                
                                val majorColor = if (isLit) activeColor else Color.White.copy(alpha = 0.3f)
                                val mStart = Offset(
                                    center.x + (innerRadius - 10.dp.toPx()) * cos(majorRad).toFloat(),
                                    center.y + (innerRadius - 10.dp.toPx()) * sin(majorRad).toFloat()
                                )
                                val mEnd = Offset(
                                    center.x + innerRadius * cos(majorRad).toFloat(),
                                    center.y + innerRadius * sin(majorRad).toFloat()
                                )
                                drawLine(majorColor, mStart, mEnd, 2.dp.toPx())
                                
                                if (i < totalMajor) {
                                    val minorStep = majorStep / 4f
                                    for (j in 1..3) {
                                        val minorAngle = currentAngle + j * minorStep
                                        val isMinorLit = minorAngle <= activeAngle
                                        val minorRad = Math.toRadians(minorAngle.toDouble())
                                        val minorColor = if (isMinorLit) activeColor else Color.White.copy(alpha = 0.15f)
                                        val miStart = Offset(
                                            center.x + (innerRadius - 4.dp.toPx()) * cos(minorRad).toFloat(),
                                            center.y + (innerRadius - 4.dp.toPx()) * sin(minorRad).toFloat()
                                        )
                                        val miEnd = Offset(
                                            center.x + innerRadius * cos(minorRad).toFloat(),
                                            center.y + innerRadius * sin(minorRad).toFloat()
                                        )
                                        drawLine(minorColor, miStart, miEnd, 0.5.dp.toPx())
                                    }
                                }
                            }
                        }
                    }

                    // 3. DRAW BEZEL / BORDER
                    when (diyBezel) {
                        0 -> { // Láser Neón
                            drawCircle(
                                color = activeColor.copy(alpha = 0.12f * diyGlowIntensity),
                                radius = radius - 2.dp.toPx(),
                                style = Stroke(width = 8.dp.toPx())
                            )
                            drawCircle(
                                color = activeColor,
                                radius = radius - 4.dp.toPx(),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                        1 -> { // Cronógrafo Rígido
                            drawCircle(
                                color = Color.White.copy(alpha = 0.6f),
                                radius = radius - 4.dp.toPx(),
                                style = Stroke(width = 3.dp.toPx())
                            )
                            for (a in 0..59) {
                                val currentAngle = a * 6
                                val rad = Math.toRadians(currentAngle.toDouble())
                                val tickLen = if (a % 5 == 0) 10.dp.toPx() else 4.dp.toPx()
                                val startOffset = Offset(
                                    center.x + (radius - 4.dp.toPx() - tickLen) * cos(rad).toFloat(),
                                    center.y + (radius - 4.dp.toPx() - tickLen) * sin(rad).toFloat()
                                )
                                val endOffset = Offset(
                                    center.x + (radius - 4.dp.toPx()) * cos(rad).toFloat(),
                                    center.y + (radius - 4.dp.toPx()) * sin(rad).toFloat()
                                )
                                drawLine(Color.White.copy(alpha = 0.25f), startOffset, endOffset, 1.5.dp.toPx())
                            }
                        }
                        2 -> { // Carbono Premium
                            drawCircle(
                                color = Color(0xFF2C353F),
                                radius = radius - 6.dp.toPx(),
                                style = Stroke(width = 12.dp.toPx())
                            )
                            drawCircle(
                                color = Color.Black,
                                radius = radius - 12.dp.toPx(),
                                style = Stroke(width = 1.dp.toPx())
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.15f),
                                radius = radius - 1.dp.toPx(),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                        3 -> { // Minimalista
                            drawCircle(
                                color = Color.White.copy(alpha = 0.1f),
                                radius = radius - 2.dp.toPx(),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                        4 -> { // Doble Anillo
                            drawCircle(
                                color = activeColor,
                                radius = radius - 3.dp.toPx(),
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawCircle(
                                color = diyAccentColor2,
                                radius = radius - 10.dp.toPx(),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                        5 -> { // Diamante Facetado
                            val totalDiamonds = 16
                            val diamondSize = 4.dp.toPx()
                            val dRad = radius - 5.dp.toPx()
                            for (i in 0 until totalDiamonds) {
                                val currentAngle = i * (360f / totalDiamonds)
                                val rad = Math.toRadians(currentAngle.toDouble())
                                val cx = center.x + dRad * cos(rad).toFloat()
                                val cy = center.y + dRad * sin(rad).toFloat()
                                val color = if (i % 2 == 0) activeColor else diyAccentColor2
                                
                                val path = Path().apply {
                                    moveTo(cx, cy - diamondSize)
                                    lineTo(cx + diamondSize, cy)
                                    lineTo(cx, cy + diamondSize)
                                    lineTo(cx - diamondSize, cy)
                                    close()
                                }
                                withTransform({
                                    rotate(currentAngle, pivot = Offset(cx, cy))
                                }) {
                                    drawPath(path, color)
                                }
                            }
                        }
                        6 -> { // Pulso Cardíaco
                            val totalDots = 36
                            val bRad = radius - 4.dp.toPx()
                            for (i in 0 until totalDots) {
                                val currentAngle = i * (360f / totalDots)
                                val rad = Math.toRadians(currentAngle.toDouble())
                                val cx = center.x + bRad * cos(rad).toFloat()
                                val cy = center.y + bRad * sin(rad).toFloat()
                                val dotSize = if (i % 6 == 0) 3.5.dp.toPx() else 1.5.dp.toPx()
                                drawCircle(activeColor.copy(alpha = diyGlowIntensity), dotSize, Offset(cx, cy))
                            }
                        }
                        7 -> { // Racing Stripe
                            val strokeW = 2.dp.toPx()
                            val innerStrokeW = 1.dp.toPx()
                            val bRad = radius - 3.dp.toPx()
                            val bRad2 = radius - 7.dp.toPx()
                            val bRad3 = radius - 11.dp.toPx()
                            
                            drawArc(
                                color = activeColor,
                                startAngle = 150f,
                                sweepAngle = 240f,
                                useCenter = false,
                                style = Stroke(width = strokeW),
                                size = Size(bRad * 2, bRad * 2),
                                topLeft = Offset(center.x - bRad, center.y - bRad)
                            )
                            drawArc(
                                color = diyAccentColor2,
                                startAngle = 150f,
                                sweepAngle = 240f,
                                useCenter = false,
                                style = Stroke(width = innerStrokeW),
                                size = Size(bRad2 * 2, bRad2 * 2),
                                topLeft = Offset(center.x - bRad2, center.y - bRad2)
                            )
                            drawArc(
                                color = activeColor,
                                startAngle = 150f,
                                sweepAngle = 240f,
                                useCenter = false,
                                style = Stroke(width = innerStrokeW),
                                size = Size(bRad3 * 2, bRad3 * 2),
                                topLeft = Offset(center.x - bRad3, center.y - bRad3)
                            )
                        }
                    }

                    // 4. DRAW NEEDLE (Aguja)
                    val needleRad = Math.toRadians(activeAngle.toDouble())
                    when (diyNeedle) {
                        0 -> { // Flecha Cyber (Triangular arrow)
                            val needleLen = innerRadius * 0.9f
                            val tipX = center.x + needleLen * cos(needleRad).toFloat()
                            val tipY = center.y + needleLen * sin(needleRad).toFloat()

                            val leftRad = needleRad - Math.toRadians(8.0)
                            val leftX = center.x + 12.dp.toPx() * cos(leftRad).toFloat()
                            val leftY = center.y + 12.dp.toPx() * sin(leftRad).toFloat()

                            val rightRad = needleRad + Math.toRadians(8.0)
                            val rightX = center.x + 12.dp.toPx() * cos(rightRad).toFloat()
                            val rightY = center.y + 12.dp.toPx() * sin(rightRad).toFloat()

                            val p = Path().apply {
                                moveTo(tipX, tipY)
                                lineTo(leftX, leftY)
                                lineTo(rightX, rightY)
                                close()
                            }
                            drawPath(p, activeColor)
                            drawCircle(Color.Black, 6.dp.toPx(), center)
                            drawCircle(activeColor, 3.dp.toPx(), center)
                        }
                        1 -> { // Línea Deportiva (Classic needle with counterweight)
                            val needleLen = innerRadius * 0.95f
                            val tipX = center.x + needleLen * cos(needleRad).toFloat()
                            val tipY = center.y + needleLen * sin(needleRad).toFloat()

                            val backRad = needleRad + Math.toRadians(180.0)
                            val backX = center.x + (radius * 0.15f) * cos(backRad).toFloat()
                            val backY = center.y + (radius * 0.15f) * sin(backRad).toFloat()

                            drawLine(
                                color = colorScheme.needleColor,
                                start = Offset(backX, backY),
                                end = Offset(tipX, tipY),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            drawCircle(colorScheme.needleColor, 10.dp.toPx(), center)
                            drawCircle(Color.White, 3.dp.toPx(), center)
                        }
                        2 -> { // Plasma/Luz de Plasma (Fading laser ray)
                            val needleLen = innerRadius * 0.85f
                            val tipX = center.x + needleLen * cos(needleRad).toFloat()
                            val tipY = center.y + needleLen * sin(needleRad).toFloat()

                            drawLine(
                                brush = Brush.linearGradient(
                                    colors = listOf(activeColor.copy(alpha = 0.1f), activeColor),
                                    start = center,
                                    end = Offset(tipX, tipY)
                                ),
                                start = center,
                                end = Offset(tipX, tipY),
                                strokeWidth = 5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            drawCircle(activeColor, 8.dp.toPx(), center)
                        }
                        3 -> { // Cuenta Flotante (Floating sphere pointer)
                            val orbDistance = innerRadius * 0.88f
                            val orbX = center.x + orbDistance * cos(needleRad).toFloat()
                            val orbY = center.y + orbDistance * sin(needleRad).toFloat()

                            drawCircle(
                                color = activeColor.copy(alpha = 0.25f),
                                radius = 9.dp.toPx(),
                                center = Offset(orbX, orbY)
                            )
                            drawCircle(
                                color = activeColor,
                                radius = 6.dp.toPx(),
                                center = Offset(orbX, orbY)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 2.dp.toPx(),
                                center = Offset(orbX, orbY)
                            )
                        }
                        4 -> { // Katana
                            val needleLen = innerRadius * 0.92f
                            val tipX = center.x + needleLen * cos(needleRad).toFloat()
                            val tipY = center.y + needleLen * sin(needleRad).toFloat()
                            
                            val baseLeftRad = needleRad - Math.toRadians(90.0)
                            val baseRightRad = needleRad + Math.toRadians(90.0)
                            
                            val leftX = center.x + 1.dp.toPx() * cos(baseLeftRad).toFloat()
                            val leftY = center.y + 1.dp.toPx() * sin(baseLeftRad).toFloat()
                            val rightX = center.x + 1.dp.toPx() * cos(baseRightRad).toFloat()
                            val rightY = center.y + 1.dp.toPx() * sin(baseRightRad).toFloat()
                            
                            val bladePath = Path().apply {
                                moveTo(leftX, leftY)
                                lineTo(tipX, tipY)
                                lineTo(rightX, rightY)
                                close()
                            }
                            drawPath(bladePath, activeColor)
                            
                            // Crossguard at 18%
                            val cgDist = innerRadius * 0.18f
                            val cgX = center.x + cgDist * cos(needleRad).toFloat()
                            val cgY = center.y + cgDist * sin(needleRad).toFloat()
                            drawCircle(
                                color = diyAccentColor2,
                                radius = 4.dp.toPx(),
                                center = Offset(cgX, cgY),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                            
                            // Highlight line
                            val hlRad = needleRad - Math.toRadians(3.0)
                            val hlTipX = center.x + needleLen * cos(hlRad).toFloat()
                            val hlTipY = center.y + needleLen * sin(hlRad).toFloat()
                            drawLine(Color.White.copy(alpha = 0.4f), center, Offset(hlTipX, hlTipY), 0.5.dp.toPx())
                            
                            drawCircle(Color.DarkGray, 5.dp.toPx(), center)
                            drawCircle(activeColor, 2.dp.toPx(), center)
                        }
                        5 -> { // Rayo Eléctrico
                            val needleLen = innerRadius * 0.85f
                            
                            val r1Angle = needleRad - Math.toRadians(15.0)
                            val r1X = center.x + (needleLen * 0.3f) * cos(r1Angle).toFloat()
                            val r1Y = center.y + (needleLen * 0.3f) * sin(r1Angle).toFloat()
                            
                            val r2Angle = needleRad + Math.toRadians(10.0)
                            val r2X = center.x + (needleLen * 0.65f) * cos(r2Angle).toFloat()
                            val r2Y = center.y + (needleLen * 0.65f) * sin(r2Angle).toFloat()
                            
                            val tipX = center.x + needleLen * cos(needleRad).toFloat()
                            val tipY = center.y + needleLen * sin(needleRad).toFloat()
                            
                            val path = Path().apply {
                                moveTo(center.x, center.y)
                                lineTo(r1X, r1Y)
                                lineTo(r2X, r2Y)
                                lineTo(tipX, tipY)
                            }
                            drawPath(path, activeColor.copy(alpha = 0.2f), style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                            drawPath(path, activeColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        6 -> { // Digital LED
                            val totalRects = 8
                            val rectW = 4.dp.toPx()
                            val rectH = 8.dp.toPx()
                            for (i in 0 until totalRects) {
                                val rDist = innerRadius * (0.2f + i * (0.65f / (totalRects - 1)))
                                val rx = center.x + rDist * cos(needleRad).toFloat()
                                val ry = center.y + rDist * sin(needleRad).toFloat()
                                
                                val alpha = 0.1f + 0.9f * (i + 1) / totalRects
                                val isHigh = rDist <= innerRadius * progress
                                val finalColor = if (isHigh) activeColor.copy(alpha = alpha) else Color.White.copy(alpha = 0.1f)
                                
                                withTransform({
                                    rotate(activeAngle, pivot = Offset(rx, ry))
                                }) {
                                    drawRoundRect(
                                        color = finalColor,
                                        topLeft = Offset(rx - rectW / 2f, ry - rectH / 2f),
                                        size = Size(rectW, rectH),
                                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                                    )
                                }
                            }
                        }
                        7 -> { // Cometa
                            val needleLen = innerRadius * 0.9f
                            val tipX = center.x + needleLen * cos(needleRad).toFloat()
                            val tipY = center.y + needleLen * sin(needleRad).toFloat()
                            
                            drawLine(activeColor, center, Offset(tipX, tipY), 2.dp.toPx(), cap = StrokeCap.Round)
                            
                            for (i in 1..5) {
                                val angleOffset = Math.toRadians(-5.0 * i)
                                val cRad = needleRad + angleOffset
                                val cDist = needleLen
                                val cx = center.x + cDist * cos(cRad).toFloat()
                                val cy = center.y + cDist * sin(cRad).toFloat()
                                
                                val trailSize = (6 - i).dp.toPx()
                                val trailAlpha = 0.4f / i
                                drawCircle(activeColor.copy(alpha = trailAlpha), trailSize, Offset(cx, cy))
                            }
                        }
                    }

                    // 5. DRAW VALUE TEXT AND LABEL
                    val valueText = String.format("%.1f", animatedValue)
                    val valueMeasured = textMeasurer.measure(
                        valueText,
                        TextStyle(
                            color = themeTextColor,
                            fontSize = valueFontSize.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    )

                    val unitMeasured = textMeasurer.measure(
                        unit.uppercase(),
                        TextStyle(
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = unitFontSize.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 1.sp
                        )
                    )

                    drawText(valueMeasured, topLeft = Offset(center.x - valueMeasured.size.width / 2f, center.y - valueMeasured.size.height / 2f - 4.dp.toPx()))
                    drawText(unitMeasured, topLeft = Offset(center.x - unitMeasured.size.width / 2f, center.y + valueMeasured.size.height / 2f - 8.dp.toPx()))
                    drawText(labelMeasured, topLeft = Offset(center.x - labelMeasured.size.width / 2f, h - innerRadius * 0.65f))
                }
            }
    )
}
