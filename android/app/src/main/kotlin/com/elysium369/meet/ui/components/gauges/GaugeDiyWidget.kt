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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
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
    diyConfig: com.elysium369.meet.data.local.entities.SavedGaugeEntity? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val gaugeStyleManager = remember { GaugeStyleManager(context) }

    // Listen to changes in the DIY designer settings
    val trigger = GaugeStyleManager.diyUpdateTrigger

    val diyBgType = diyConfig?.bgType ?: remember(trigger) { gaugeStyleManager.getDiyBgType() }
    val diyBgPreset = diyConfig?.bgPresetIndex ?: remember(trigger) { gaugeStyleManager.getDiyBgPresetIndex() }
    val diyBgUri = diyConfig?.bgImageUri ?: remember(trigger) { gaugeStyleManager.getDiyBgImageUri() }
    val diyBezel = diyConfig?.bezelStyle ?: remember(trigger) { gaugeStyleManager.getDiyBezelStyle() }
    val diyNeedle = diyConfig?.needleStyle ?: remember(trigger) { gaugeStyleManager.getDiyNeedleStyle() }
    val diyTicks = diyConfig?.ticksStyle ?: remember(trigger) { gaugeStyleManager.getDiyTicksStyle() }
    val diyAccentColor = diyConfig?.let { Color(it.accentColor) } ?: remember(trigger) { Color(gaugeStyleManager.getDiyAccentColor()) }
    val diyAccentColor2 = diyConfig?.let { Color(it.accentColor2) } ?: remember(trigger) { Color(gaugeStyleManager.getDiyAccentColor2()) }
    val diyGlowIntensity = diyConfig?.glowIntensity ?: remember(trigger) { gaugeStyleManager.getDiyGlowIntensity() }
    val diyImageOpacity = diyConfig?.imageOpacity ?: remember(trigger) { gaugeStyleManager.getDiyImageOpacity() }
    val diyGaugeName = diyConfig?.name ?: remember(trigger) { gaugeStyleManager.getDiyGaugeName() }
    val diyTypography = diyConfig?.typographyIndex ?: remember(trigger) { gaugeStyleManager.getDiyTypography() }
    val diyAnimation = diyConfig?.animationIndex ?: remember(trigger) { gaugeStyleManager.getDiyAnimation() }

    val infiniteTransition = rememberInfiniteTransition(label = "diyAnimation")
    val animTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "animTime"
    )

    val colorScheme = LocalGaugeColorScheme.current

    val animatedValue by animateFloatAsState(
        targetValue = value.coerceIn(minVal, maxVal),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 120f),
        label = "diyGauge"
    )

    // Load custom user image from URI if selected
    val userBitmap = remember(diyBgUri, diyBgType) {
        if (diyBgType == 2 && diyBgUri.isNotEmpty() && !diyBgUri.endsWith(".gif", true) && !diyBgUri.contains("gif", true)) {
            try {
                if (diyBgUri.startsWith("/")) {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(diyBgUri)
                    bitmap?.asImageBitmap()
                } else {
                    val uri = android.net.Uri.parse(diyBgUri)
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    bitmap?.asImageBitmap()
                }
            } catch (e: Exception) {
                android.util.Log.e("GaugeDiyWidget", "Error loading DIY background image: ${e.message}")
                null
            }
        } else {
            null
        }
    }

    val userMovie = remember(diyBgUri, diyBgType) {
        if (diyBgType == 2 && diyBgUri.isNotEmpty() && (diyBgUri.endsWith(".gif", true) || diyBgUri.contains("gif", true))) {
            try {
                if (diyBgUri.startsWith("/")) {
                    val file = java.io.File(diyBgUri)
                    val bytes = file.readBytes()
                    android.graphics.Movie.decodeByteArray(bytes, 0, bytes.size)
                } else {
                    val uri = android.net.Uri.parse(diyBgUri)
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    inputStream?.close()
                    if (bytes != null) {
                        android.graphics.Movie.decodeByteArray(bytes, 0, bytes.size)
                    } else null
                }
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val labelText = if (diyGaugeName.isNotEmpty()) diyGaugeName else label
    val normalizedUnit = unit.trim()
    val normalizedLabel = labelText.trim()
    val hasCustomArtwork = diyBgType == 2 && diyBgUri.isNotBlank()
    val sameMetricName = normalizedUnit.isNotBlank() && areDiyGaugeTextDuplicates(normalizedLabel, normalizedUnit)
    val displayUnit = if (hasCustomArtwork) "" else normalizedUnit
    val displayLabel = when {
        sameMetricName -> ""
        hasCustomArtwork -> ""
        else -> normalizedLabel
    }
    val drawGeneratedTicks = !hasCustomArtwork
    val drawGeneratedBezel = !hasCustomArtwork
    val drawGeneratedNeedle = !hasCustomArtwork

    // Typography customization mapping
    val diyFontFamily = when (diyTypography) {
        0 -> FontFamily.Monospace
        1 -> FontFamily.SansSerif
        2 -> FontFamily.Serif
        3 -> FontFamily.Cursive
        4 -> FontFamily.SansSerif
        5 -> FontFamily.Monospace
        6 -> FontFamily.Monospace
        7 -> FontFamily.Serif
        8 -> FontFamily.SansSerif
        9 -> FontFamily.SansSerif
        else -> FontFamily.Monospace
    }

    val diyFontWeight = when (diyTypography) {
        0 -> FontWeight.Bold
        1 -> FontWeight.Normal
        2 -> FontWeight.Medium
        3 -> FontWeight.Bold
        4 -> FontWeight.ExtraBold
        5 -> FontWeight.Bold
        6 -> FontWeight.Black
        7 -> FontWeight.SemiBold
        8 -> FontWeight.ExtraBold
        9 -> FontWeight.Light
        else -> FontWeight.Black
    }

    val diyFontStyle = if (diyTypography == 5) FontStyle.Italic else FontStyle.Normal
    val diyLetterSpacingVal = if (diyTypography == 9) 2f else 0f
    val diyLetterSpacing = diyLetterSpacingVal.sp

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
                    displayLabel.uppercase(),
                    TextStyle(
                        color = colorScheme.labelColor.copy(alpha = 0.7f),
                        fontSize = labelFontSize.sp,
                        fontWeight = diyFontWeight,
                        fontFamily = diyFontFamily,
                        fontStyle = diyFontStyle,
                        letterSpacing = diyLetterSpacing
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
                            1 -> { // 60 Preset Backgrounds
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
                                        val crackColor = Color(0xFFFF4500).copy(alpha = 0.3f)
                                        val crackWidth = 2.dp.toPx()
                                        drawLine(crackColor, Offset(w * 0.2f, h * 0.2f), Offset(w * 0.5f, h * 0.4f), crackWidth)
                                        drawLine(crackColor, Offset(w * 0.5f, h * 0.4f), Offset(w * 0.4f, h * 0.7f), crackWidth)
                                        drawLine(crackColor, Offset(w * 0.4f, h * 0.7f), Offset(w * 0.8f, h * 0.8f), crackWidth)
                                        drawLine(crackColor, Offset(w * 0.5f, h * 0.4f), Offset(w * 0.7f, h * 0.3f), crackWidth)
                                        drawLine(crackColor, Offset(w * 0.7f, h * 0.3f), Offset(w * 0.9f, h * 0.5f), crackWidth)
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
                                        drawLine(traceColor, Offset(w * 0.1f, h * 0.3f), Offset(w * 0.4f, h * 0.3f), traceWidth)
                                        drawLine(traceColor, Offset(w * 0.4f, h * 0.3f), Offset(w * 0.5f, h * 0.2f), traceWidth)
                                        drawCircle(nodeColor, nodeRadius, Offset(w * 0.1f, h * 0.3f))
                                        drawCircle(nodeColor, nodeRadius, Offset(w * 0.5f, h * 0.2f))
                                        drawLine(traceColor, Offset(w * 0.8f, h * 0.4f), Offset(w * 0.6f, h * 0.4f), traceWidth)
                                        drawLine(traceColor, Offset(w * 0.6f, h * 0.4f), Offset(w * 0.5f, h * 0.5f), traceWidth)
                                        drawCircle(nodeColor, nodeRadius, Offset(w * 0.8f, h * 0.4f))
                                        drawCircle(nodeColor, nodeRadius, Offset(w * 0.5f, h * 0.5f))
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
                                    7 -> { // Nebulosa Galáctica
                                        drawRect(Color(0xFF060312))
                                        drawRect(Brush.radialGradient(colors = listOf(Color(0xFF4A148C).copy(alpha = 0.5f), Color.Transparent), center = Offset(w * 0.3f, h * 0.4f), radius = radius * 0.8f))
                                        drawRect(Brush.radialGradient(colors = listOf(Color(0xFFE91E63).copy(alpha = 0.3f), Color.Transparent), center = Offset(w * 0.7f, h * 0.5f), radius = radius * 0.7f))
                                        drawRect(Brush.radialGradient(colors = listOf(Color(0xFF1A237E).copy(alpha = 0.4f), Color.Transparent), center = Offset(w * 0.5f, h * 0.8f), radius = radius * 0.9f))
                                    }
                                    // 8..15: Gradients
                                    8 -> drawRect(Brush.linearGradient(colors = listOf(Color(0xFF8E5D3F), Color(0xFF4C2A1A), Color(0xFF8E5D3F)), start = Offset.Zero, end = Offset(w, h)))
                                    9 -> drawRect(Brush.verticalGradient(colors = listOf(Color(0xFFFF5722), Color(0xFFE91E63), Color(0xFF3F51B5))))
                                    10 -> drawRect(Brush.radialGradient(colors = listOf(Color(0xFF00E676).copy(alpha = 0.6f), Color(0xFF00B0FF).copy(alpha = 0.2f), Color(0xFF0A0E1A)), center = center, radius = radius))
                                    11 -> drawRect(Brush.sweepGradient(colors = listOf(Color(0xFFD4AF37), Color(0xFF8A640F), Color(0xFFD4AF37)), center = center))
                                    12 -> drawRect(Brush.sweepGradient(colors = listOf(Color(0xFF78909C), Color(0xFF37474F), Color(0xFF78909C)), center = center))
                                    13 -> drawRect(Brush.radialGradient(colors = listOf(Color(0xFF1B5E20), Color(0xFF002400)), center = center, radius = radius))
                                    14 -> drawRect(Brush.verticalGradient(colors = listOf(Color(0xFFE0F7FA), Color(0xFF006064))))
                                    15 -> drawRect(Brush.radialGradient(colors = listOf(Color(0xFF0091EA), Color(0xFF0D47A1)), center = center, radius = radius))
                                    // 16..23: Radials
                                    16 -> {
                                        drawRect(Color(0xFF050E0C))
                                        drawCircle(activeColor.copy(alpha = 0.05f), radius * 0.5f, style = Stroke(1f))
                                        drawCircle(activeColor.copy(alpha = 0.05f), radius * 0.8f, style = Stroke(1f))
                                        val sweepRad = Math.toRadians((animTime * 360f).toDouble())
                                        drawLine(activeColor.copy(alpha = 0.3f), center, Offset(center.x + radius * cos(sweepRad).toFloat(), center.y + radius * sin(sweepRad).toFloat()), 2f)
                                    }
                                    17 -> {
                                        drawRect(Color(0xFF0A0E14))
                                        drawCircle(activeColor.copy(alpha = 0.15f), radius * 0.3f, style = Stroke(1.5f))
                                        drawCircle(activeColor.copy(alpha = 0.15f), radius * 0.6f, style = Stroke(1.5f))
                                        drawCircle(activeColor.copy(alpha = 0.15f), radius * 0.9f, style = Stroke(1.5f))
                                        drawLine(activeColor.copy(alpha = 0.15f), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1f)
                                        drawLine(activeColor.copy(alpha = 0.15f), Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1f)
                                    }
                                    18 -> {
                                        drawRect(Color(0xFF04060A))
                                        val spiralPath = Path()
                                        for (theta in 0..720 step 5) {
                                            val radS = Math.toRadians(theta.toDouble())
                                            val rVal = (radius * 0.9f) * (theta / 720f)
                                            val sx = center.x + rVal * cos(radS + animTime * 6.28f).toFloat()
                                            val sy = center.y + rVal * sin(radS + animTime * 6.28f).toFloat()
                                            if (theta == 0) spiralPath.moveTo(sx, sy) else spiralPath.lineTo(sx, sy)
                                        }
                                        drawPath(spiralPath, activeColor.copy(alpha = 0.1f), style = Stroke(2f))
                                    }
                                    19 -> {
                                        drawRect(Color(0xFF0A0E14))
                                        for (i in 0..7) {
                                            val angleRad = Math.toRadians(i * 45.0)
                                            drawLine(activeColor.copy(alpha = 0.08f), center, Offset(center.x + radius * cos(angleRad).toFloat(), center.y + radius * sin(angleRad).toFloat()), 1f)
                                        }
                                        drawCircle(diyAccentColor2.copy(alpha = 0.1f), radius * 0.7f, style = Stroke(2f))
                                    }
                                    20 -> {
                                        drawRect(Color(0xFF05050A))
                                        for (i in 0..11) {
                                            val angleRad = Math.toRadians(i * 30.0 + animTime * 60f)
                                            drawLine(activeColor.copy(alpha = 0.1f), center, Offset(center.x + radius * cos(angleRad).toFloat() + 20f, center.y + radius * sin(angleRad).toFloat() - 20f), 1.5f)
                                        }
                                    }
                                    21 -> {
                                        drawRect(Color(0xFF0A0A10))
                                        drawCircle(activeColor.copy(alpha = 0.08f), radius * 0.4f, center = Offset(center.x - 10.dp.toPx(), center.y - 5.dp.toPx()), style = Stroke(1f))
                                        drawCircle(diyAccentColor2.copy(alpha = 0.08f), radius * 0.6f, center = Offset(center.x + 15.dp.toPx(), center.y + 10.dp.toPx()), style = Stroke(1f))
                                    }
                                    22 -> {
                                        drawRect(Color(0xFF0A0E14))
                                        for (rVal in (20.dp.toPx().toInt())..(radius.toInt()) step (15.dp.toPx().toInt())) {
                                            drawCircle(activeColor.copy(alpha = 0.06f), rVal.toFloat(), style = Stroke(1f))
                                        }
                                    }
                                    23 -> {
                                        drawRect(Color(0xFF020206))
                                        val points = listOf(Offset(w*0.3f, h*0.3f), Offset(w*0.4f, h*0.5f), Offset(w*0.6f, h*0.4f), Offset(w*0.7f, h*0.6f), Offset(w*0.5f, h*0.7f))
                                        for (i in 0 until points.size - 1) {
                                            drawLine(activeColor.copy(alpha = 0.1f), points[i], points[i+1], 1f)
                                        }
                                        points.forEach { drawCircle(Color.White.copy(alpha = 0.6f), 3f, it) }
                                    }
                                    // 24..31: Geometric
                                    24 -> {
                                        drawRect(Color(0xFF0D0F12))
                                        val gSize = 25.dp.toPx()
                                        for (x in 0..(w / gSize).toInt()) {
                                            for (y in 0..(h / gSize).toInt()) {
                                                drawLine(activeColor.copy(alpha = 0.04f), Offset(x*gSize, y*gSize), Offset((x+1)*gSize, (y+1)*gSize), 1f)
                                                drawLine(activeColor.copy(alpha = 0.04f), Offset((x+1)*gSize, y*gSize), Offset(x*gSize, (y+1)*gSize), 1f)
                                            }
                                        }
                                    }
                                    25 -> {
                                        drawRect(Color(0xFF0D0F12))
                                        val gSize = 20.dp.toPx()
                                        for (x in -5..(w / gSize).toInt() + 5) {
                                            drawLine(activeColor.copy(alpha = 0.04f), Offset(x*gSize, 0f), Offset(x*gSize + h*0.57f, h), 1f)
                                            drawLine(activeColor.copy(alpha = 0.04f), Offset(x*gSize, 0f), Offset(x*gSize - h*0.57f, h), 1f)
                                        }
                                    }
                                    26 -> {
                                        drawRect(Color(0xFF070B11))
                                        val rSize = 16.dp.toPx()
                                        for (x in 0..(w / rSize).toInt() + 1) {
                                            for (y in 0..(h / rSize).toInt() + 1) {
                                                val cx = x * rSize + (if (y % 2 == 0) 0f else rSize/2)
                                                val cy = y * rSize
                                                drawCircle(diyAccentColor2.copy(alpha = 0.05f), 2f, Offset(cx, cy))
                                            }
                                        }
                                    }
                                    27 -> {
                                        drawRect(Color(0xFF0D1117))
                                        val cSize = w / 8f
                                        for (row in 0..7) {
                                            for (col in 0..7) {
                                                if ((row + col) % 2 == 0) {
                                                    drawRect(Color.White.copy(alpha = 0.03f), Offset(col * cSize, row * cSize), Size(cSize, cSize))
                                                }
                                            }
                                        }
                                    }
                                    28 -> {
                                        drawRect(Color(0xFF0D1117))
                                        val stepY = 16.dp.toPx()
                                        for (y in 0..(h / stepY).toInt()) {
                                            val cy = y * stepY
                                            val path = Path().apply {
                                                moveTo(0f, cy)
                                                lineTo(w*0.5f, cy + 8.dp.toPx())
                                                lineTo(w, cy)
                                            }
                                            drawPath(path, activeColor.copy(alpha = 0.05f), style = Stroke(1.5f))
                                        }
                                    }
                                    29 -> {
                                        drawRect(Color(0xFF0A0D14))
                                        val bH = 10.dp.toPx()
                                        val bW = 20.dp.toPx()
                                        for (y in 0..(h / bH).toInt()) {
                                            drawLine(activeColor.copy(alpha = 0.04f), Offset(0f, y*bH), Offset(w, y*bH), 1f)
                                            val xOffset = if (y % 2 == 0) 0f else bW/2
                                            for (x in 0..(w / bW).toInt() + 1) {
                                                drawLine(activeColor.copy(alpha = 0.04f), Offset(x*bW + xOffset, y*bH), Offset(x*bW + xOffset, (y+1)*bH), 1f)
                                            }
                                        }
                                    }
                                    30 -> {
                                        drawRect(Color(0xFF090B0E))
                                        val gSize = 15.dp.toPx()
                                        for (x in 0..(w / gSize).toInt()) {
                                            for (y in 0..(h / gSize).toInt()) {
                                                drawCircle(activeColor.copy(alpha = 0.06f), 1.5f.dp.toPx(), Offset(x*gSize, y*gSize))
                                            }
                                        }
                                    }
                                    31 -> {
                                        drawRect(Color(0xFF0C0E12))
                                        val space = 12.dp.toPx()
                                        for (i in -20..40) {
                                            drawLine(activeColor.copy(alpha = 0.04f), Offset(i * space, 0f), Offset(i * space + h, h), 1.5f.dp.toPx())
                                        }
                                    }
                                    // 32..39: Waves
                                    32 -> {
                                        drawRect(Color(0xFF080B10))
                                        val path = Path()
                                        for (x in 0..w.toInt() step 5) {
                                            val yVal = center.y + 15.dp.toPx() * sin((x / w * 2 * Math.PI) + animTime * 2 * Math.PI).toFloat()
                                            if (x == 0) path.moveTo(x.toFloat(), yVal) else path.lineTo(x.toFloat(), yVal)
                                        }
                                        drawPath(path, activeColor.copy(alpha = 0.15f), style = Stroke(2.dp.toPx()))
                                    }
                                    33 -> {
                                        drawRect(Color(0xFF080B10))
                                        val waveRadius = radius * 0.8f * (0.2f + 0.8f * animTime)
                                        drawCircle(activeColor.copy(alpha = 0.2f * (1f - animTime)), waveRadius, style = Stroke(3.dp.toPx()))
                                        drawCircle(diyAccentColor2.copy(alpha = 0.1f * animTime), waveRadius * 0.5f, style = Stroke(2.dp.toPx()))
                                    }
                                    34 -> {
                                        drawRect(Color(0xFF0A0E14))
                                        val path1 = Path()
                                        val path2 = Path()
                                        for (x in 0..w.toInt() step 4) {
                                            val y1 = center.y + 12.dp.toPx() * sin((x/w * 4 * Math.PI) + animTime * 2 * Math.PI).toFloat()
                                            val y2 = center.y + 12.dp.toPx() * sin((x/w * 4 * Math.PI) - animTime * 2 * Math.PI).toFloat()
                                            if (x == 0) {
                                                path1.moveTo(x.toFloat(), y1)
                                                path2.moveTo(x.toFloat(), y2)
                                            } else {
                                                path1.lineTo(x.toFloat(), y1)
                                                path2.lineTo(x.toFloat(), y2)
                                            }
                                        }
                                        drawPath(path1, activeColor.copy(alpha = 0.1f), style = Stroke(1.5.dp.toPx()))
                                        drawPath(path2, diyAccentColor2.copy(alpha = 0.1f), style = Stroke(1.5.dp.toPx()))
                                    }
                                    35 -> {
                                        drawRect(Color(0xFF05080E))
                                        drawArc(color = activeColor.copy(alpha = 0.1f), startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = Offset(center.x - innerRadius, center.y - innerRadius), size = Size(innerRadius*2, innerRadius*2), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)))
                                    }
                                    36 -> {
                                        drawRect(Color(0xFF0A0E14))
                                        val infPath = Path()
                                        for (t in 0..360 step 5) {
                                            val radT = Math.toRadians(t.toDouble())
                                            val scale = radius * 0.5f / (3 - cos(2*radT)).toFloat()
                                            val ix = center.x + scale * cos(radT).toFloat()
                                            val iy = center.y + scale * sin(2*radT).toFloat() / 2f
                                            if (t == 0) infPath.moveTo(ix, iy) else infPath.lineTo(ix, iy)
                                        }
                                        drawPath(infPath, activeColor.copy(alpha = 0.1f), style = Stroke(2.dp.toPx()))
                                    }
                                    37 -> {
                                        drawRect(Color(0xFF070A0F))
                                        val bars = 8
                                        val barW = w / (bars * 2)
                                        for (i in 0 until bars) {
                                            val barH = (h * 0.3f) * (0.2f + 0.8f * sin((i + animTime * 10f)).let { if (it < 0) -it else it })
                                            drawRect(activeColor.copy(alpha = 0.12f), Offset(w * 0.2f + i * barW * 2f, h * 0.7f - barH), Size(barW, barH))
                                        }
                                    }
                                    38 -> {
                                        drawRect(Color(0xFF06090D))
                                        drawLine(activeColor.copy(alpha = 0.08f), Offset(w*0.1f, h*0.5f), Offset(w*0.9f, h*0.5f), 1f)
                                        val waves = 3
                                        for (wIdx in 0 until waves) {
                                            val path = Path()
                                            for (x in (w*0.1f).toInt()..(w*0.9f).toInt() step 5) {
                                                val yVal = center.y + (10.dp.toPx() / (wIdx+1)) * sin((x/w * 8 * Math.PI) + animTime * 4 * Math.PI).toFloat()
                                                if (x == (w*0.1f).toInt()) path.moveTo(x.toFloat(), yVal) else path.lineTo(x.toFloat(), yVal)
                                            }
                                            drawPath(path, activeColor.copy(alpha = 0.08f / (wIdx+1)), style = Stroke(1.dp.toPx()))
                                        }
                                    }
                                    39 -> {
                                        drawRect(Color(0xFF080C14))
                                        for (i in 0..2) {
                                            val rVal = radius * 0.8f * ((i + animTime) % 3) / 3f
                                            val alpha = 0.15f * (1f - ((i + animTime) % 3) / 3f)
                                            drawCircle(activeColor.copy(alpha = alpha), rVal, style = Stroke(2.dp.toPx()))
                                        }
                                    }
                                    // 40..47: Space
                                    40 -> {
                                        drawRect(Color(0xFF020409))
                                        val stars = 30
                                        for (i in 0 until stars) {
                                            val angleRad = Math.toRadians((i * (360f / stars) + animTime * 30f).toDouble())
                                            val dist = (radius * 0.8f) * (i.toFloat() / stars)
                                            val sx = center.x + dist * cos(angleRad).toFloat()
                                            val sy = center.y + dist * sin(angleRad).toFloat()
                                            drawCircle(Color.White.copy(alpha = 0.6f), 1.dp.toPx(), Offset(sx, sy))
                                        }
                                    }
                                    41 -> {
                                        drawRect(Brush.radialGradient(colors = listOf(Color.White.copy(alpha = 0.2f), Color.Transparent), center = center, radius = radius * 0.3f))
                                        drawRect(Brush.radialGradient(colors = listOf(activeColor.copy(alpha = 0.3f), Color.Transparent), center = center, radius = radius * 0.8f))
                                    }
                                    42 -> {
                                        drawRect(Color(0xFF05050C))
                                        val tOffset = animTime * radius * 1.5f
                                        drawLine(activeColor.copy(alpha = 0.2f), Offset(w * 0.8f - tOffset, h * 0.2f + tOffset), Offset(w * 0.9f - tOffset, h * 0.1f + tOffset), 1.5f.dp.toPx(), cap = StrokeCap.Round)
                                        drawLine(diyAccentColor2.copy(alpha = 0.15f), Offset(w * 0.3f - tOffset, h * 0.3f + tOffset), Offset(w * 0.4f - tOffset, h * 0.2f + tOffset), 1.dp.toPx(), cap = StrokeCap.Round)
                                    }
                                    43 -> {
                                        drawRect(Color(0xFF020204))
                                        drawCircle(Color.Black, radius * 0.3f)
                                        drawCircle(activeColor.copy(alpha = 0.2f), radius * 0.35f, style = Stroke(2.dp.toPx()))
                                        drawCircle(diyAccentColor2.copy(alpha = 0.1f), radius * 0.5f, style = Stroke(1.dp.toPx()))
                                    }
                                    44 -> {
                                        drawRect(Color(0xFF03030A))
                                        val points = listOf(Offset(w*0.15f, h*0.2f), Offset(w*0.85f, h*0.3f), Offset(w*0.3f, h*0.75f), Offset(w*0.7f, h*0.8f), Offset(w*0.5f, h*0.15f), Offset(w*0.25f, h*0.5f))
                                        points.forEach { drawCircle(Color.White.copy(alpha = 0.6f * animTime), 1.5f.dp.toPx(), it) }
                                    }
                                    45 -> {
                                        drawRect(Color(0xFF0A0D14))
                                        drawCircle(activeColor.copy(alpha = 0.08f), radius * 0.6f, style = Stroke(1.dp.toPx()))
                                        val sRad = Math.toRadians((animTime * 360f).toDouble())
                                        drawCircle(activeColor, 4.dp.toPx(), Offset(center.x + radius * 0.6f * cos(sRad).toFloat(), center.y + radius * 0.6f * sin(sRad).toFloat()))
                                    }
                                    46 -> {
                                        drawRect(Color(0xFF0A0E14))
                                        val sRad = Math.toRadians(45.0)
                                        val cDist = radius * 0.8f * animTime
                                        drawLine(activeColor.copy(alpha = 0.3f), Offset(center.x - cDist, center.y - cDist), Offset(center.x - cDist + 15.dp.toPx(), center.y - cDist + 15.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
                                    }
                                    47 -> {
                                        drawRect(Color(0xFF030308))
                                        drawCircle(Color.Black, radius * 0.4f, center = Offset(center.x - 10f, center.y - 10f))
                                        drawCircle(activeColor.copy(alpha = 0.2f), radius * 0.42f, center = Offset(center.x - 10f, center.y - 10f), style = Stroke(3.dp.toPx()))
                                    }
                                    // 48..55: Auto
                                    48 -> {
                                        drawRect(Color(0xFF0A0E14))
                                        val path = Path().apply {
                                            moveTo(center.x - 20f, center.y - 40f)
                                            lineTo(center.x + 20f, center.y - 40f)
                                            lineTo(center.x + 20f, center.y - 10f)
                                            lineTo(center.x + 10f, center.y - 10f)
                                            lineTo(center.x + 10f, center.y + 40f)
                                            lineTo(center.x - 10f, center.y + 40f)
                                            lineTo(center.x - 10f, center.y - 10f)
                                            lineTo(center.x - 20f, center.y - 10f)
                                            close()
                                        }
                                        drawPath(path, activeColor.copy(alpha = 0.08f))
                                    }
                                    49 -> {
                                        drawRect(Color(0xFF111116))
                                        drawCircle(Color.Black, innerRadius, style = Stroke(12.dp.toPx()))
                                        drawCircle(activeColor.copy(alpha = 0.2f), innerRadius, style = Stroke(1.dp.toPx()))
                                    }
                                    50 -> {
                                        drawRect(Color(0xFF0B0E14))
                                        val teeth = 16
                                        val path = Path()
                                        for (i in 0 until teeth) {
                                            val a1 = i * (360f / teeth)
                                            val a2 = a1 + (180f / teeth)
                                            val rad1 = Math.toRadians(a1.toDouble())
                                            val rad2 = Math.toRadians(a2.toDouble())
                                            val outerR = radius * 0.4f
                                            val innerR = radius * 0.3f
                                            if (i == 0) {
                                                path.moveTo(center.x + outerR * cos(rad1).toFloat(), center.y + outerR * sin(rad1).toFloat())
                                            } else {
                                                path.lineTo(center.x + outerR * cos(rad1).toFloat(), center.y + outerR * sin(rad1).toFloat())
                                            }
                                            path.lineTo(center.x + innerR * cos(rad1).toFloat(), center.y + innerR * sin(rad1).toFloat())
                                            path.lineTo(center.x + innerR * cos(rad2).toFloat(), center.y + innerR * sin(rad2).toFloat())
                                            path.lineTo(center.x + outerR * cos(rad2).toFloat(), center.y + outerR * sin(rad2).toFloat())
                                        }
                                        path.close()
                                        drawPath(path, activeColor.copy(alpha = 0.08f))
                                    }
                                    51 -> {
                                        drawRect(Color(0xFF0F1318))
                                        val sqSize = w / 20f
                                        for (x in 0..20) {
                                            for (y in 0..20) {
                                                if ((x + y) % 2 == 0) {
                                                    drawRect(activeColor.copy(alpha = 0.03f), Offset(x * sqSize, y * sqSize), Size(sqSize, sqSize))
                                                }
                                            }
                                        }
                                    }
                                    52 -> {
                                        drawRect(Color(0xFF0A0808))
                                        val flamePath = Path().apply {
                                            moveTo(center.x - 30.dp.toPx(), h)
                                            quadraticBezierTo(center.x - 15.dp.toPx(), h - 40.dp.toPx(), center.x - 20.dp.toPx(), h - 80.dp.toPx())
                                            quadraticBezierTo(center.x, h - 30.dp.toPx(), center.x + 10.dp.toPx(), h - 90.dp.toPx())
                                            quadraticBezierTo(center.x + 15.dp.toPx(), h - 40.dp.toPx(), center.x + 30.dp.toPx(), h)
                                            close()
                                        }
                                        drawPath(flamePath, Color(0xFFFF5722).copy(alpha = 0.08f))
                                    }
                                    53 -> {
                                        drawRect(Color(0xFF0A0E14))
                                        for (i in 0..7) {
                                            val a = i * 45f + animTime * 360f
                                            drawArc(color = activeColor.copy(alpha = 0.08f), startAngle = a, sweepAngle = 25f, useCenter = true, topLeft = Offset(center.x - radius/2, center.y - radius/2), size = Size(radius, radius))
                                        }
                                    }
                                    54 -> {
                                        drawRect(Color(0xFF0D0F12))
                                        drawArc(color = Color(0xFFFF1744).copy(alpha = 0.1f), startAngle = 225f, sweepAngle = 45f, useCenter = false, topLeft = Offset(center.x - innerRadius, center.y - innerRadius), size = Size(innerRadius * 2, innerRadius * 2), style = Stroke(width = 10.dp.toPx()))
                                    }
                                    55 -> {
                                        drawRect(Color(0xFF1E2124))
                                        drawCircle(Color.DarkGray, radius * 0.75f, style = Stroke(width = 12.dp.toPx()))
                                        for (i in 0..11) {
                                            val rRad = Math.toRadians((i * 30.0).toDouble())
                                            drawCircle(Color.Black.copy(alpha = 0.3f), 3.dp.toPx(), Offset(center.x + radius * 0.55f * cos(rRad).toFloat(), center.y + radius * 0.55f * sin(rRad).toFloat()))
                                        }
                                    }
                                    // 56..59: Synthwave
                                    56 -> {
                                        drawRect(Color(0xFF12031B))
                                        val step = w / 10f
                                        for (i in 0..10) {
                                            drawLine(Color(0xFFFF007F).copy(alpha = 0.15f), Offset(i * step, h * 0.6f), Offset(w*0.5f + (i - 5)*step * 3f, h), 1.5f)
                                        }
                                        for (y in (h*0.6f).toInt()..h.toInt() step 12) {
                                            val ratio = (y - h*0.6f) / (h*0.4f)
                                            drawLine(Color(0xFFFF007F).copy(alpha = 0.15f * ratio), Offset(0f, y.toFloat()), Offset(w, y.toFloat()), 1f)
                                        }
                                    }
                                    57 -> {
                                        drawRect(Color(0xFF090312))
                                        drawRect(Color(0xFF00FFCC).copy(alpha = 0.08f), Offset(w*0.2f, h*0.4f), Size(w*0.6f, 15.dp.toPx()))
                                        drawRect(Color(0xFFFF007F).copy(alpha = 0.08f), Offset(w*0.15f, h*0.6f), Size(w*0.7f, 10.dp.toPx()))
                                    }
                                    58 -> {
                                        drawRect(Color(0xFF010602))
                                        val cols = 12
                                        val colW = w / cols
                                        for (c in 0 until cols) {
                                            val tY = h * ((c * 0.13f + animTime) % 1f)
                                            drawCircle(Color(0xFF00FF66).copy(alpha = 0.15f), 2.dp.toPx(), Offset(c * colW + colW/2, tY))
                                            drawCircle(Color(0xFF00FF66).copy(alpha = 0.08f), 2.dp.toPx(), Offset(c * colW + colW/2, tY - 15.dp.toPx()))
                                        }
                                    }
                                    else -> {
                                        drawRect(Color(0xFF0D021A))
                                        drawCircle(Color(0xFF00FFFF).copy(alpha = 0.1f), radius * 0.8f, style = Stroke(width = 2.dp.toPx()))
                                        drawCircle(Color(0xFFFF00FF).copy(alpha = 0.05f), radius * 0.6f, style = Stroke(width = 1.dp.toPx()))
                                    }
                                }
                            }
                            2 -> { // User Custom Image with GIF decoder support
                                val movie = userMovie

                                drawRect(Color(0xFF02060D))

                                if (movie != null) {
                                    drawIntoCanvas { canvas ->
                                        val movieCanvas = canvas.nativeCanvas
                                        val duration = movie.duration().let { if (it == 0) 1000 else it }
                                        val timeMs = (System.currentTimeMillis() % duration).toInt()
                                        movie.setTime(timeMs)
                                        val movieW = movie.width().coerceAtLeast(1)
                                        val movieH = movie.height().coerceAtLeast(1)
                                        val scaleX = w / movieW.toFloat()
                                        val scaleY = h / movieH.toFloat()
                                        movieCanvas.save()
                                        movieCanvas.scale(scaleX, scaleY)
                                        movie.draw(movieCanvas, 0f, 0f)
                                        movieCanvas.restore()
                                    }
                                } else if (userBitmap != null) {
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

                        // ── 1B. DRAW ANIMATION LAYERS (1..9) ──
                        if (diyAnimation > 0) {
                            when (diyAnimation) {
                                1 -> { // Fuego (Flame particles)
                                    val flames = 12
                                    for (i in 0 until flames) {
                                        val particleRatio = (i.toFloat() / flames + animTime) % 1f
                                        val px = w * 0.2f + (i * (w * 0.6f / flames))
                                        val py = h - (h * 0.4f * particleRatio)
                                        val alpha = 0.25f * (1f - particleRatio)
                                        val sizeP = 15.dp.toPx() * (1f - particleRatio)
                                        drawCircle(Color(0xFFFF3D00).copy(alpha = alpha), sizeP, Offset(px, py))
                                    }
                                }
                                2 -> { // Rayos (Lightning strikes)
                                    val strokeW = 2.dp.toPx()
                                    // Lightning happens occasionally based on system clock
                                    if ((System.currentTimeMillis() / 250) % 6 == 0L) {
                                        val path = Path().apply {
                                            moveTo(center.x, center.y - radius * 0.8f)
                                            lineTo(center.x - 10.dp.toPx(), center.y - 20.dp.toPx())
                                            lineTo(center.x + 15.dp.toPx(), center.y + 10.dp.toPx())
                                            lineTo(center.x, center.y + radius * 0.6f)
                                        }
                                        drawPath(path, Color.White, style = Stroke(width = strokeW))
                                        drawPath(path, activeColor.copy(alpha = 0.3f), style = Stroke(width = strokeW * 3f))
                                    }
                                }
                                3 -> { // Nieve (Snowflakes)
                                    val snowflakes = 15
                                    for (i in 0 until snowflakes) {
                                        val fallRatio = (i.toFloat() / snowflakes + animTime) % 1f
                                        val px = w * 0.1f + (i * w * 0.8f / snowflakes)
                                        val py = h * 0.1f + (h * 0.8f * fallRatio)
                                        drawCircle(Color.White.copy(alpha = 0.6f), 2.dp.toPx(), Offset(px, py))
                                    }
                                }
                                4 -> { // Lluvia (Rain drops)
                                    val drops = 20
                                    for (i in 0 until drops) {
                                        val fallRatio = (i.toFloat() / drops + animTime) % 1f
                                        val px = w * 0.1f + (i * w * 0.8f / drops)
                                        val py = h * 0.1f + (h * 0.8f * fallRatio)
                                        drawLine(Color(0xFF00E5FF).copy(alpha = 0.4f), Offset(px, py), Offset(px - 4.dp.toPx(), py + 10.dp.toPx()), 1.5.dp.toPx())
                                    }
                                }
                                5 -> { // Engranajes (Gears rotating)
                                    val teeth = 12
                                    val gearRad = radius * 0.35f
                                    val radOffset = (animTime * 360f) * (Math.PI / 180f)
                                    withTransform({
                                        rotate(animTime * 360f, pivot = Offset(w * 0.3f, h * 0.3f))
                                    }) {
                                        drawCircle(activeColor.copy(alpha = 0.05f), gearRad, center = Offset(w * 0.3f, h * 0.3f), style = Stroke(2.dp.toPx()))
                                    }
                                    withTransform({
                                        rotate(-animTime * 360f, pivot = Offset(w * 0.7f, h * 0.7f))
                                    }) {
                                        drawCircle(diyAccentColor2.copy(alpha = 0.05f), gearRad * 0.8f, center = Offset(w * 0.7f, h * 0.7f), style = Stroke(2.dp.toPx()))
                                    }
                                }
                                6 -> { // Galaxia (Galaxy lines)
                                    withTransform({
                                        rotate(animTime * 360f, pivot = center)
                                    }) {
                                        val galColor = activeColor.copy(alpha = 0.12f)
                                        drawArc(color = galColor, startAngle = 0f, sweepAngle = 120f, useCenter = false, topLeft = Offset(center.x - innerRadius/2, center.y - innerRadius/2), size = Size(innerRadius, innerRadius), style = Stroke(width = 2.dp.toPx()))
                                        drawArc(color = diyAccentColor2.copy(alpha = 0.08f), startAngle = 180f, sweepAngle = 120f, useCenter = false, topLeft = Offset(center.x - innerRadius*0.65f, center.y - innerRadius*0.65f), size = Size(innerRadius * 1.3f, innerRadius * 1.3f), style = Stroke(width = 2.dp.toPx()))
                                    }
                                }
                                7 -> { // Radar sweep
                                    val sweepRad = Math.toRadians((animTime * 360f).toDouble())
                                    val sweepX = center.x + radius * cos(sweepRad).toFloat()
                                    val sweepY = center.y + radius * sin(sweepRad).toFloat()
                                    drawLine(activeColor.copy(alpha = 0.3f), center, Offset(sweepX, sweepY), 2.dp.toPx())
                                    drawArc(
                                        brush = Brush.sweepGradient(colors = listOf(Color.Transparent, activeColor.copy(alpha = 0.12f), Color.Transparent), center = center),
                                        startAngle = animTime * 360f - 45f,
                                        sweepAngle = 45f,
                                        useCenter = true,
                                        size = Size(radius * 2, radius * 2),
                                        topLeft = Offset(center.x - radius, center.y - radius)
                                    )
                                }
                                8 -> { // Matriz (Matrix Rain columns)
                                    val matrixColumns = 10
                                    val charWidth = w / matrixColumns
                                    for (i in 0 until matrixColumns) {
                                        val dropProgress = (i * 0.17f + animTime * 1.5f) % 1f
                                        val px = i * charWidth + charWidth / 2f
                                        val py = h * dropProgress
                                        drawCircle(Color(0xFF00FF66).copy(alpha = 0.25f), 3.dp.toPx(), Offset(px, py))
                                        drawCircle(Color(0xFF00FF66).copy(alpha = 0.12f), 2.dp.toPx(), Offset(px, py - 12.dp.toPx()))
                                        drawCircle(Color(0xFF00FF66).copy(alpha = 0.05f), 1.5.dp.toPx(), Offset(px, py - 24.dp.toPx()))
                                    }
                                }
                                9 -> { // Aurora wave gradient
                                    val path = Path()
                                    val waveH = 20.dp.toPx()
                                    for (x in 0..w.toInt() step 10) {
                                        val yVal = center.y + waveH * sin((x/w * 2 * Math.PI) + animTime * 2 * Math.PI).toFloat()
                                        if (x == 0) path.moveTo(x.toFloat(), yVal) else path.lineTo(x.toFloat(), yVal)
                                    }
                                    drawPath(path, activeColor.copy(alpha = 0.08f), style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round))
                                    drawPath(path, diyAccentColor2.copy(alpha = 0.05f), style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round))
                                }
                            }
                        }
                    }

                    // 2. DRAW TICKS / SCALES (15 Styles)
                    if (drawGeneratedTicks) {
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
                        8 -> { // Double Arc
                            val strokeW = 4.dp.toPx()
                            val gap = 6.dp.toPx()
                            drawArc(
                                color = Color.White.copy(alpha = 0.1f),
                                startAngle = startAngle,
                                sweepAngle = angleRange,
                                useCenter = false,
                                style = Stroke(width = strokeW),
                                size = Size((innerRadius - gap) * 2, (innerRadius - gap) * 2),
                                topLeft = Offset(center.x - innerRadius + gap, center.y - innerRadius + gap)
                            )
                            drawArc(
                                color = activeColor,
                                startAngle = startAngle,
                                sweepAngle = progress * angleRange,
                                useCenter = false,
                                style = Stroke(width = strokeW),
                                size = Size((innerRadius - gap) * 2, (innerRadius - gap) * 2),
                                topLeft = Offset(center.x - innerRadius + gap, center.y - innerRadius + gap)
                            )
                            drawArc(
                                color = diyAccentColor2.copy(alpha = 0.1f),
                                startAngle = startAngle,
                                sweepAngle = angleRange,
                                useCenter = false,
                                style = Stroke(width = strokeW),
                                size = Size(innerRadius * 2, innerRadius * 2),
                                topLeft = Offset(center.x - innerRadius, center.y - innerRadius)
                            )
                            drawArc(
                                color = diyAccentColor2,
                                startAngle = startAngle,
                                sweepAngle = progress * angleRange,
                                useCenter = false,
                                style = Stroke(width = strokeW),
                                size = Size(innerRadius * 2, innerRadius * 2),
                                topLeft = Offset(center.x - innerRadius, center.y - innerRadius)
                            )
                        }
                        9 -> { // Segmented Ring (5 sectors)
                            val sectors = 5
                            val gapDegrees = 4f
                            val sectorDegrees = (angleRange - (sectors - 1) * gapDegrees) / sectors
                            val strokeW = 8.dp.toPx()
                            for (i in 0 until sectors) {
                                val sStart = startAngle + i * (sectorDegrees + gapDegrees)
                                val sMid = sStart + sectorDegrees / 2f
                                val isLit = sMid <= activeAngle
                                val color = if (isLit) activeColor else Color.White.copy(alpha = 0.1f)
                                drawArc(
                                    color = color,
                                    startAngle = sStart,
                                    sweepAngle = sectorDegrees,
                                    useCenter = false,
                                    style = Stroke(width = strokeW),
                                    size = Size(innerRadius * 2, innerRadius * 2),
                                    topLeft = Offset(center.x - innerRadius, center.y - innerRadius)
                                )
                            }
                        }
                        10 -> { // Semi-Circle Ticks
                            val step = 15f
                            for (a in 0..(180f / step).toInt()) {
                                val currentAngle = 180f + (a * step)
                                val rad = Math.toRadians(currentAngle.toDouble())
                                val startOffset = Offset(
                                    center.x + (innerRadius - 6.dp.toPx()) * cos(rad).toFloat(),
                                    center.y + (innerRadius - 6.dp.toPx()) * sin(rad).toFloat()
                                )
                                val endOffset = Offset(
                                    center.x + innerRadius * cos(rad).toFloat(),
                                    center.y + innerRadius * sin(rad).toFloat()
                                )
                                val tickColor = if (currentAngle <= activeAngle) activeColor else Color.White.copy(alpha = 0.15f)
                                drawLine(tickColor, startOffset, endOffset, 1.5.dp.toPx())
                            }
                        }
                        11 -> { // JDM Sport with Redline
                            val step = 8f
                            for (a in 0..(angleRange / step).toInt()) {
                                val currentAngle = startAngle + (a * step)
                                val rad = Math.toRadians(currentAngle.toDouble())
                                val isRedline = (a * step) / angleRange >= 0.75f
                                val tickColor = when {
                                    currentAngle <= activeAngle -> if (isRedline) Color(0xFFFF1744) else activeColor
                                    else -> if (isRedline) Color(0xFFFF1744).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f)
                                }
                                val tickLen = if (a % 5 == 0) 12.dp.toPx() else 6.dp.toPx()
                                val startOffset = Offset(
                                    center.x + (innerRadius - tickLen) * cos(rad).toFloat(),
                                    center.y + (innerRadius - tickLen) * sin(rad).toFloat()
                                )
                                val endOffset = Offset(
                                    center.x + innerRadius * cos(rad).toFloat(),
                                    center.y + innerRadius * sin(rad).toFloat()
                                )
                                drawLine(tickColor, startOffset, endOffset, if (a % 5 == 0) 2.5.dp.toPx() else 1.2.dp.toPx())
                            }
                        }
                        12 -> { // Tachometer Blocks
                            val blocks = 18
                            val step = angleRange / blocks
                            val strokeW = 10.dp.toPx()
                            for (i in 0..blocks) {
                                val currentAngle = startAngle + i * step
                                val isLit = currentAngle <= activeAngle
                                val color = if (isLit) activeColor else Color.White.copy(alpha = 0.08f)
                                drawArc(
                                    color = color,
                                    startAngle = currentAngle - step / 2.5f,
                                    sweepAngle = step / 1.5f,
                                    useCenter = false,
                                    style = Stroke(width = strokeW),
                                    size = Size(innerRadius * 2, innerRadius * 2),
                                    topLeft = Offset(center.x - innerRadius, center.y - innerRadius)
                                )
                            }
                        }
                        13 -> { // Minimal Dashes
                            val step = 6f
                            for (a in 0..(angleRange / step).toInt()) {
                                val currentAngle = startAngle + (a * step)
                                val rad = Math.toRadians(currentAngle.toDouble())
                                val startOffset = Offset(
                                    center.x + (innerRadius - 3.dp.toPx()) * cos(rad).toFloat(),
                                    center.y + (innerRadius - 3.dp.toPx()) * sin(rad).toFloat()
                                )
                                val endOffset = Offset(
                                    center.x + innerRadius * cos(rad).toFloat(),
                                    center.y + innerRadius * sin(rad).toFloat()
                                )
                                val tickColor = if (currentAngle <= activeAngle) activeColor else Color.White.copy(alpha = 0.08f)
                                drawLine(tickColor, startOffset, endOffset, 1.dp.toPx())
                            }
                        }
                        else -> { // Cyber Dash (angled segments pointing inwards)
                            val step = 15f
                            for (a in 0..(angleRange / step).toInt()) {
                                val currentAngle = startAngle + (a * step)
                                val rad = Math.toRadians(currentAngle.toDouble())
                                val startOffset = Offset(
                                    center.x + (innerRadius - 10.dp.toPx()) * cos(rad).toFloat(),
                                    center.y + (innerRadius - 10.dp.toPx()) * sin(rad).toFloat()
                                )
                                val endOffset = Offset(
                                    center.x + innerRadius * cos(rad + Math.toRadians(8.0)).toFloat(),
                                    center.y + innerRadius * sin(rad + Math.toRadians(8.0)).toFloat()
                                )
                                val tickColor = if (currentAngle <= activeAngle) activeColor else Color.White.copy(alpha = 0.12f)
                                drawLine(tickColor, startOffset, endOffset, 1.5.dp.toPx())
                            }
                        }
                    }
                    }

                    // 3. DRAW BEZEL / BORDER (20 Styles)
                    if (drawGeneratedBezel) {
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
                        8 -> { // Holographic Ring
                            drawCircle(activeColor.copy(alpha = 0.05f), radius - 2.dp.toPx(), style = Stroke(width = 1.dp.toPx()))
                            drawCircle(diyAccentColor2.copy(alpha = 0.1f), radius - 6.dp.toPx(), style = Stroke(width = 1.dp.toPx()))
                            drawCircle(activeColor.copy(alpha = 0.15f), radius - 10.dp.toPx(), style = Stroke(width = 1.5f.dp.toPx()))
                        }
                        9 -> { // Dotted Laser
                            val dots = 48
                            for (i in 0 until dots) {
                                val currentAngle = i * (360f / dots)
                                val rad = Math.toRadians(currentAngle.toDouble())
                                val cx = center.x + (radius - 5.dp.toPx()) * cos(rad).toFloat()
                                val cy = center.y + (radius - 5.dp.toPx()) * sin(rad).toFloat()
                                drawCircle(activeColor.copy(alpha = 0.8f * diyGlowIntensity), 1.5.dp.toPx(), Offset(cx, cy))
                            }
                        }
                        10 -> { // Heavy Metal
                            drawCircle(Color(0xFF555555), radius - 5.dp.toPx(), style = Stroke(width = 8.dp.toPx()))
                            drawCircle(Color(0xFF888888), radius - 2.dp.toPx(), style = Stroke(width = 1.5f.dp.toPx()))
                            drawCircle(Color(0xFF222222), radius - 9.dp.toPx(), style = Stroke(width = 1.dp.toPx()))
                        }
                        11 -> { // Carbon Thread
                            drawCircle(Color(0xFF1E2022), radius - 6.dp.toPx(), style = Stroke(width = 10.dp.toPx()))
                            for (a in 0..72) {
                                val currentAngle = a * 5
                                val rad = Math.toRadians(currentAngle.toDouble())
                                val start = Offset(center.x + (radius - 11.dp.toPx()) * cos(rad).toFloat(), center.y + (radius - 11.dp.toPx()) * sin(rad).toFloat())
                                val end = Offset(center.x + (radius - 1.dp.toPx()) * cos(rad + Math.toRadians(12.0)).toFloat(), center.y + (radius - 1.dp.toPx()) * sin(rad + Math.toRadians(12.0)).toFloat())
                                drawLine(Color.Black.copy(alpha = 0.4f), start, end, 1.dp.toPx())
                            }
                        }
                        12 -> { // Dual Glow
                            drawCircle(activeColor.copy(alpha = 0.2f * diyGlowIntensity), radius - 3.dp.toPx(), style = Stroke(width = 4.dp.toPx()))
                            drawCircle(diyAccentColor2.copy(alpha = 0.2f * diyGlowIntensity), radius - 9.dp.toPx(), style = Stroke(width = 4.dp.toPx()))
                        }
                        13 -> { // Quad Segmented
                            val strokeW = 4.dp.toPx()
                            val bRad = radius - 4.dp.toPx()
                            for (i in 0..3) {
                                drawArc(color = activeColor, startAngle = i * 90f + 5f, sweepAngle = 80f, useCenter = false, topLeft = Offset(center.x - bRad, center.y - bRad), size = Size(bRad * 2, bRad * 2), style = Stroke(width = strokeW))
                            }
                        }
                        14 -> { // Tachometer Ticks
                            for (a in 0..11) {
                                val currentAngle = a * 30f
                                val rad = Math.toRadians(currentAngle.toDouble())
                                val start = Offset(center.x + (radius - 8.dp.toPx()) * cos(rad).toFloat(), center.y + (radius - 8.dp.toPx()) * sin(rad).toFloat())
                                val end = Offset(center.x + radius * cos(rad).toFloat(), center.y + radius * sin(rad).toFloat())
                                drawLine(activeColor, start, end, 3.dp.toPx())
                            }
                        }
                        15 -> { // Shimmer Ring
                            drawCircle(activeColor.copy(alpha = 0.1f), radius - 4.dp.toPx(), style = Stroke(width = 6.dp.toPx()))
                            drawArc(
                                brush = Brush.sweepGradient(colors = listOf(Color.Transparent, activeColor, Color.Transparent), center = center),
                                startAngle = 0f, sweepAngle = 360f, useCenter = false, style = Stroke(width = 4.dp.toPx()),
                                size = Size((radius - 4.dp.toPx()) * 2, (radius - 4.dp.toPx()) * 2),
                                topLeft = Offset(center.x - radius + 4.dp.toPx(), center.y - radius + 4.dp.toPx())
                            )
                        }
                        16 -> { // Hex Bezel
                            val hexPath = Path().apply {
                                for (i in 0..5) {
                                    val angleRad = Math.toRadians(i * 60.0)
                                    val px = center.x + (radius - 2.dp.toPx()) * cos(angleRad).toFloat()
                                    val py = center.y + (radius - 2.dp.toPx()) * sin(angleRad).toFloat()
                                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                                }
                                close()
                            }
                            drawPath(hexPath, activeColor, style = Stroke(width = 2.dp.toPx()))
                            drawPath(hexPath, activeColor.copy(alpha = 0.1f * diyGlowIntensity), style = Stroke(width = 6.dp.toPx()))
                        }
                        17 -> { // Square Bezel
                            val rectSize = (radius - 3.dp.toPx()) * 2
                            drawRoundRect(activeColor, Offset(center.x - rectSize/2, center.y - rectSize/2), Size(rectSize, rectSize), CornerRadius(16.dp.toPx()), Stroke(width = 2.dp.toPx()))
                            drawRoundRect(activeColor.copy(alpha = 0.1f * diyGlowIntensity), Offset(center.x - rectSize/2, center.y - rectSize/2), Size(rectSize, rectSize), CornerRadius(16.dp.toPx()), Stroke(width = 6.dp.toPx()))
                        }
                        18 -> { // Steampunk Cog
                            val teeth = 24
                            val outerR = radius
                            val innerR = radius - 8.dp.toPx()
                            val cogPath = Path()
                            for (i in 0 until teeth) {
                                val a1 = i * (360f / teeth)
                                val a2 = a1 + (180f / teeth)
                                val rad1 = Math.toRadians(a1.toDouble())
                                val rad2 = Math.toRadians(a2.toDouble())
                                if (i == 0) {
                                    cogPath.moveTo(center.x + outerR * cos(rad1).toFloat(), center.y + outerR * sin(rad1).toFloat())
                                } else {
                                    cogPath.lineTo(center.x + outerR * cos(rad1).toFloat(), center.y + outerR * sin(rad1).toFloat())
                                }
                                cogPath.lineTo(center.x + innerR * cos(rad1).toFloat(), center.y + innerR * sin(rad1).toFloat())
                                cogPath.lineTo(center.x + innerR * cos(rad2).toFloat(), center.y + innerR * sin(rad2).toFloat())
                                cogPath.lineTo(center.x + outerR * cos(rad2).toFloat(), center.y + outerR * sin(rad2).toFloat())
                            }
                            cogPath.close()
                            drawPath(cogPath, Color(0xFFC5A059), style = Stroke(width = 2.dp.toPx()))
                        }
                        else -> { // Cyber Grid Rim
                            drawCircle(activeColor.copy(alpha = 0.1f), radius - 6.dp.toPx(), style = Stroke(width = 8.dp.toPx()))
                            for (i in 0..11) {
                                val rad = Math.toRadians(i * 30.0)
                                val start = Offset(center.x + (radius - 10.dp.toPx()) * cos(rad).toFloat(), center.y + (radius - 10.dp.toPx()) * sin(rad).toFloat())
                                val end = Offset(center.x + radius * cos(rad).toFloat(), center.y + radius * sin(rad).toFloat())
                                drawLine(activeColor.copy(alpha = 0.4f), start, end, 1.dp.toPx())
                            }
                        }
                    }
                    }

                    // 4. DRAW NEEDLE (15 Styles)
                    if (drawGeneratedNeedle) {
                    val needleRad = Math.toRadians(activeAngle.toDouble())

                    // Native drop shadow support layer if glowIntensity > 0
                    if (diyGlowIntensity > 0f) {
                        val shadowOffset = 3.dp.toPx()
                        val shadowPaint = Paint().asFrameworkPaint().apply {
                            color = activeColor.copy(alpha = 0.15f * diyGlowIntensity).toArgb()
                            setShadowLayer(8.dp.toPx(), shadowOffset, shadowOffset, activeColor.toArgb())
                        }
                        // Draw shadow layer behind the needle using standard canvas
                        drawIntoCanvas { canvas ->
                            val radCos = cos(needleRad).toFloat()
                            val radSin = sin(needleRad).toFloat()
                            val tipX = center.x + (innerRadius * 0.9f) * radCos
                            val tipY = center.y + (innerRadius * 0.9f) * radSin
                            canvas.nativeCanvas.drawLine(center.x, center.y, tipX, tipY, shadowPaint)
                        }
                    }

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
                            drawLine(colorScheme.needleColor, Offset(backX, backY), Offset(tipX, tipY), 3.dp.toPx(), cap = StrokeCap.Round)
                            drawCircle(colorScheme.needleColor, 10.dp.toPx(), center)
                            drawCircle(Color.White, 3.dp.toPx(), center)
                        }
                        2 -> { // Plasma/Luz de Plasma (Fading laser ray)
                            val needleLen = innerRadius * 0.85f
                            val tipX = center.x + needleLen * cos(needleRad).toFloat()
                            val tipY = center.y + needleLen * sin(needleRad).toFloat()
                            drawLine(
                                brush = Brush.linearGradient(colors = listOf(activeColor.copy(alpha = 0.1f), activeColor), start = center, end = Offset(tipX, tipY)),
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
                            drawCircle(activeColor.copy(alpha = 0.25f), 9.dp.toPx(), Offset(orbX, orbY))
                            drawCircle(activeColor, 6.dp.toPx(), Offset(orbX, orbY))
                            drawCircle(Color.White, 2.dp.toPx(), Offset(orbX, orbY))
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
                            val cgDist = innerRadius * 0.18f
                            val cgX = center.x + cgDist * cos(needleRad).toFloat()
                            val cgY = center.y + cgDist * sin(needleRad).toFloat()
                            drawCircle(diyAccentColor2, 4.dp.toPx(), Offset(cgX, cgY), style = Stroke(width = 1.5.dp.toPx()))
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
                                withTransform({ rotate(activeAngle, pivot = Offset(rx, ry)) }) {
                                    drawRoundRect(color = finalColor, topLeft = Offset(rx - rectW / 2f, ry - rectH / 2f), size = Size(rectW, rectH), cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()))
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
                                val cx = center.x + needleLen * cos(cRad).toFloat()
                                val cy = center.y + needleLen * sin(cRad).toFloat()
                                val trailSize = (6 - i).dp.toPx()
                                val trailAlpha = 0.4f / i
                                drawCircle(activeColor.copy(alpha = trailAlpha), trailSize, Offset(cx, cy))
                            }
                        }
                        8 -> { // Retro Chrono
                            val needleLen = innerRadius * 0.95f
                            val tipX = center.x + needleLen * cos(needleRad).toFloat()
                            val tipY = center.y + needleLen * sin(needleRad).toFloat()
                            val backRad = needleRad + Math.toRadians(180.0)
                            val backX = center.x + (radius * 0.2f) * cos(backRad).toFloat()
                            val backY = center.y + (radius * 0.2f) * sin(backRad).toFloat()
                            drawLine(activeColor, Offset(backX, backY), Offset(tipX, tipY), 1.5.dp.toPx())
                            drawCircle(activeColor, 8.dp.toPx(), Offset(backX, backY), style = Stroke(2.dp.toPx()))
                            drawCircle(Color(0xFFC5A059), 4.dp.toPx(), center)
                        }
                        9 -> { // Sword
                            val needleLen = innerRadius * 0.9f
                            val tipX = center.x + needleLen * cos(needleRad).toFloat()
                            val tipY = center.y + needleLen * sin(needleRad).toFloat()
                            val leftRad = needleRad - Math.toRadians(5.0)
                            val leftX = center.x + 6.dp.toPx() * cos(leftRad).toFloat()
                            val leftY = center.y + 6.dp.toPx() * sin(leftRad).toFloat()
                            val rightRad = needleRad + Math.toRadians(5.0)
                            val rightX = center.x + 6.dp.toPx() * cos(rightRad).toFloat()
                            val rightY = center.y + 6.dp.toPx() * sin(rightRad).toFloat()
                            val swordPath = Path().apply {
                                moveTo(center.x, center.y)
                                lineTo(leftX, leftY)
                                lineTo(tipX, tipY)
                                lineTo(rightX, rightY)
                                close()
                            }
                            drawPath(swordPath, activeColor)
                            drawLine(Color.White.copy(alpha = 0.5f), center, Offset(tipX, tipY), 1.dp.toPx())
                            drawCircle(Color.Black, 6.dp.toPx(), center)
                            drawCircle(diyAccentColor2, 3.dp.toPx(), center)
                        }
                        10 -> { // Glowing Fiber
                            val needleLen = innerRadius * 0.9f
                            val tipX1 = center.x + needleLen * cos(needleRad - Math.toRadians(2.0)).toFloat()
                            val tipY1 = center.y + needleLen * sin(needleRad - Math.toRadians(2.0)).toFloat()
                            val tipX2 = center.x + needleLen * cos(needleRad + Math.toRadians(2.0)).toFloat()
                            val tipY2 = center.y + needleLen * sin(needleRad + Math.toRadians(2.0)).toFloat()

                            drawLine(diyAccentColor2.copy(alpha = 0.2f * diyGlowIntensity), center, Offset(tipX1, tipY1), 4.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(diyAccentColor2.copy(alpha = 0.2f * diyGlowIntensity), center, Offset(tipX2, tipY2), 4.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(activeColor, center, Offset(tipX1, tipY1), 1.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(activeColor, center, Offset(tipX2, tipY2), 1.dp.toPx(), cap = StrokeCap.Round)
                            drawCircle(activeColor, 5.dp.toPx(), center)
                        }
                        11 -> { // Dash Line
                            val segments = 6
                            val needleLen = innerRadius * 0.9f
                            for (i in 0 until segments) {
                                val sDist = needleLen * (i.toFloat() / segments)
                                val eDist = needleLen * ((i + 0.6f) / segments)
                                val sx = center.x + sDist * cos(needleRad).toFloat()
                                val sy = center.y + sDist * sin(needleRad).toFloat()
                                val ex = center.x + eDist * cos(needleRad).toFloat()
                                val ey = center.y + eDist * sin(needleRad).toFloat()
                                drawLine(activeColor, Offset(sx, sy), Offset(ex, ey), 3.dp.toPx(), cap = StrokeCap.Round)
                            }
                            drawCircle(activeColor, 6.dp.toPx(), center)
                        }
                        12 -> { // Hexagon Pointer
                            val needleLen = innerRadius * 0.82f
                            val pointerX = center.x + needleLen * cos(needleRad).toFloat()
                            val pointerY = center.y + needleLen * sin(needleRad).toFloat()
                            drawLine(activeColor, center, Offset(pointerX, pointerY), 2.dp.toPx())
                            val hexSize = 5.dp.toPx()
                            val hexPath = Path().apply {
                                for (i in 0..5) {
                                    val angleRad = Math.toRadians(i * 60.0)
                                    val px = pointerX + hexSize * cos(angleRad).toFloat()
                                    val py = pointerY + hexSize * sin(angleRad).toFloat()
                                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                                }
                                close()
                            }
                            drawPath(hexPath, activeColor)
                            drawPath(hexPath, Color.White, style = Stroke(width = 1.dp.toPx()))
                            drawCircle(activeColor, 5.dp.toPx(), center)
                        }
                        13 -> { // Dual Segmented Needles
                            val needleLen = innerRadius * 0.88f
                            val tipX1 = center.x + needleLen * cos(needleRad - Math.toRadians(5.0)).toFloat()
                            val tipY1 = center.y + needleLen * sin(needleRad - Math.toRadians(5.0)).toFloat()
                            val tipX2 = center.x + needleLen * cos(needleRad + Math.toRadians(5.0)).toFloat()
                            val tipY2 = center.y + needleLen * sin(needleRad + Math.toRadians(5.0)).toFloat()
                            drawLine(activeColor, center, Offset(tipX1, tipY1), 2.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(diyAccentColor2.copy(alpha = 0.7f), center, Offset(tipX2, tipY2), 1.5.dp.toPx(), cap = StrokeCap.Round)
                            drawCircle(activeColor, 8.dp.toPx(), center)
                        }
                        else -> { // Virtual HUD
                            val arcR = innerRadius + 2.dp.toPx()
                            drawArc(
                                color = activeColor.copy(alpha = 0.2f * diyGlowIntensity),
                                startAngle = activeAngle - 10f,
                                sweepAngle = 20f,
                                useCenter = false,
                                topLeft = Offset(center.x - arcR, center.y - arcR),
                                size = Size(arcR * 2, arcR * 2),
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = activeColor,
                                startAngle = activeAngle - 2f,
                                sweepAngle = 4f,
                                useCenter = false,
                                topLeft = Offset(center.x - arcR, center.y - arcR),
                                size = Size(arcR * 2, arcR * 2),
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawCircle(activeColor.copy(alpha = 0.1f), 10.dp.toPx(), center)
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
                            fontWeight = diyFontWeight,
                            fontStyle = diyFontStyle,
                            fontFamily = diyFontFamily,
                            letterSpacing = diyLetterSpacing
                        )
                    )

                    val unitMeasured = textMeasurer.measure(
                        displayUnit.uppercase(),
                        TextStyle(
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = unitFontSize.sp,
                            fontWeight = diyFontWeight,
                            fontStyle = diyFontStyle,
                            fontFamily = diyFontFamily,
                            letterSpacing = (diyLetterSpacingVal + 1f).sp
                        )
                    )

                    val textGap = (h * 0.018f).coerceIn(4.dp.toPx(), 10.dp.toPx())
                    val hasUnit = displayUnit.isNotBlank()
                    val unitHeight = if (hasUnit) unitMeasured.size.height.toFloat() else 0f
                    val stackHeight = valueMeasured.size.height + if (hasUnit) unitHeight + textGap * 0.35f else 0f
                    val stackTop = center.y - stackHeight / 2f - if (hasCustomArtwork) radius * 0.02f else 0f
                    val valueTop = stackTop
                    val unitTop = valueTop + valueMeasured.size.height + textGap * 0.35f

                    drawText(
                        valueMeasured,
                        topLeft = Offset(center.x - valueMeasured.size.width / 2f, valueTop)
                    )
                    if (displayUnit.isNotBlank()) {
                        drawText(
                            unitMeasured,
                            topLeft = Offset(center.x - unitMeasured.size.width / 2f, unitTop)
                        )
                    }
                    if (displayLabel.isNotBlank()) {
                        val labelTop = (h - radius * 0.32f - labelMeasured.size.height)
                            .coerceAtLeast(unitTop + unitHeight + textGap)
                            .coerceAtMost(h - radius * 0.14f - labelMeasured.size.height)
                        drawText(
                            labelMeasured,
                            topLeft = Offset(center.x - labelMeasured.size.width / 2f, labelTop)
                        )
                    }
                }
            }
    )
}

private fun areDiyGaugeTextDuplicates(label: String, unit: String): Boolean {
    val labelToken = normalizeDiyGaugeToken(label)
    val unitToken = normalizeDiyGaugeToken(unit)
    if (labelToken.isBlank() || unitToken.isBlank()) return false
    if (labelToken == unitToken) return true

    val labelMetric = diyGaugeMetricKey(labelToken)
    val unitMetric = diyGaugeMetricKey(unitToken)
    return labelMetric.isNotBlank() && labelMetric == unitMetric
}

private fun normalizeDiyGaugeToken(value: String): String {
    return value
        .trim()
        .lowercase()
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace("°", "")
        .replace(Regex("[^a-z0-9%]+"), "")
}

private fun diyGaugeMetricKey(token: String): String {
    return when {
        token in setOf("kmh", "kph", "kmhr", "mph", "mihr") -> "vehicle_speed"
        token.contains("velocidad") || token == "speed" -> "vehicle_speed"
        token in setOf("rpm", "revmin", "revminuto") -> "engine_rpm"
        token.contains("revolucion") || token.contains("tacometro") -> "engine_rpm"
        else -> ""
    }
}
