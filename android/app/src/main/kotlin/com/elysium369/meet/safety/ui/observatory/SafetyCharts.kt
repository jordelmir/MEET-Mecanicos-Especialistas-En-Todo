package com.elysium369.meet.safety.ui.observatory

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors

// ═══════════════════════════════════════════════════════════════════
// Observatory Palette
// ═══════════════════════════════════════════════════════════════════

object ObservatoryColors {
    val background = Color(0xFF0D0D1A)
    val cardSurface = Color(0xFF14142B)
    val cardBorder = Color(0xFF232342)

    val accentRed = Color(0xFFFF4444)
    val accentAmber = Color(0xFFFFB74D)
    val accentPurple = Color(0xFFBA68C8)
    val accentYellow = Color(0xFFFFD54F)
    val accentBlue = Color(0xFF4FC3F7)
    val accentGreen = Color(0xFF81C784)
    val accentCyan = Color(0xFF00E5FF)

    val femaleColor = Color(0xFFF06292)
    val maleColor = Color(0xFF64B5F6)
    val unknownColor = Color(0xFF9E9E9E)

    val chartColors = listOf(
        accentRed, accentAmber, accentPurple,
        accentYellow, accentBlue, accentGreen, accentCyan,
    )
}

// ═══════════════════════════════════════════════════════════════════
// KPI Card
// ═══════════════════════════════════════════════════════════════════

@Composable
fun KpiCard(
    label: String,
    value: String,
    accentColor: Color = ObservatoryColors.accentGreen,
    modifier: Modifier = Modifier,
) {
    val numericValue = value.toIntOrNull()
    val animatedCount by animateIntAsState(
        targetValue = numericValue ?: 0,
        animationSpec = tween(durationMillis = 800),
        label = "kpi-counter",
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = ObservatoryColors.cardSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ObservatoryColors.cardBorder),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (numericValue != null) "$animatedCount" else value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = accentColor,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = MeetColors.textSecondary,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            // Mini Sparkline / Accent bar
            Canvas(modifier = Modifier.fillMaxWidth().height(3.dp)) {
                drawRoundRect(
                    color = accentColor.copy(alpha = 0.6f),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Pie Chart (Donut with center count)
// ═══════════════════════════════════════════════════════════════════

@Composable
fun ObservatoryPieChart(
    data: List<Pair<String, Long>>,
    title: String,
    modifier: Modifier = Modifier,
) {
    if (data.isEmpty()) return
    val total = data.sumOf { it.second }.toFloat().coerceAtLeast(1f)
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(data) { animProgress.animateTo(1f, tween(800)) }
    val textMeasurer = rememberTextMeasurer()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ObservatoryColors.cardSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ObservatoryColors.cardBorder),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White)
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Donut Chart with Center Total
                Box(
                    modifier = Modifier.size(140.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 26f
                        val diameter = size.minDimension - strokeWidth
                        val topLeft = Offset(
                            (size.width - diameter) / 2f,
                            (size.height - diameter) / 2f,
                        )

                        // Background track circle
                        drawArc(
                            color = ObservatoryColors.cardBorder.copy(alpha = 0.5f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(diameter, diameter),
                            style = Stroke(width = strokeWidth),
                        )

                        var startAngle = -90f
                        data.forEachIndexed { index, (_, value) ->
                            val sweep = (value / total) * 360f * animProgress.value
                            drawArc(
                                color = ObservatoryColors.chartColors[index % ObservatoryColors.chartColors.size],
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = Size(diameter, diameter),
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                            )
                            startAngle += sweep
                        }
                    }

                    // Center label
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${total.toLong()}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                        )
                        Text(
                            text = "TOTAL",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = MeetColors.textSecondary,
                            letterSpacing = 1.sp,
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                // Legend
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    data.forEachIndexed { index, (label, value) ->
                        val pct = ((value / total) * 100).toInt()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .background(
                                        ObservatoryColors.chartColors[index % ObservatoryColors.chartColors.size],
                                        CircleShape,
                                    ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                label,
                                fontSize = 11.sp,
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "$value ($pct%)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MeetColors.cyberCyan,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Horizontal Bar Chart
// ═══════════════════════════════════════════════════════════════════

@Composable
fun ObservatoryBarChart(
    data: List<Pair<String, Long>>,
    title: String,
    colors: List<Color> = ObservatoryColors.chartColors,
    modifier: Modifier = Modifier,
) {
    if (data.isEmpty()) return
    val maxValue = data.maxOf { it.second }.toFloat().coerceAtLeast(1f)
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(data) { animProgress.animateTo(1f, tween(600)) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ObservatoryColors.cardSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ObservatoryColors.cardBorder),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White)
            Spacer(Modifier.height(14.dp))
            data.forEachIndexed { index, (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        fontSize = 11.sp,
                        color = MeetColors.textSecondary,
                        modifier = Modifier.width(96.dp),
                        fontWeight = FontWeight.Medium,
                    )
                    val barColor = colors[index % colors.size]
                    val fraction = (value / maxValue) * animProgress.value
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(18.dp)
                            .background(ObservatoryColors.cardBorder, RoundedCornerShape(6.dp)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(18.dp)
                                .background(barColor, RoundedCornerShape(6.dp)),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        value.toString(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Resolution Time Comparison Chart (Vertical bars)
// ═══════════════════════════════════════════════════════════════════

@Composable
fun ResolutionTimeChart(
    avgAll: Double,
    avgFemale: Double,
    avgMale: Double,
    title: String,
    modifier: Modifier = Modifier,
) {
    val hasData = avgAll >= 0 || avgFemale >= 0 || avgMale >= 0
    if (!hasData) return

    val bars = listOf(
        Triple("General", avgAll, ObservatoryColors.accentCyan),
        Triple("Mujeres", avgFemale, ObservatoryColors.femaleColor),
        Triple("Hombres", avgMale, ObservatoryColors.maleColor),
    ).filter { it.second >= 0 }

    val maxDays = bars.maxOfOrNull { it.second }?.toFloat()?.coerceAtLeast(1f) ?: return
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(bars) { animProgress.animateTo(1f, tween(800)) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ObservatoryColors.cardSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ObservatoryColors.cardBorder),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(
                "Promedio de días desde reporte hasta resolución documentada",
                fontSize = 11.sp,
                color = MeetColors.textSecondary,
            )
            Spacer(Modifier.height(16.dp))

            val barHeight = 150.dp
            Canvas(modifier = Modifier.fillMaxWidth().height(barHeight)) {
                val barCount = bars.size
                val spacing = 28f
                val barWidth = (size.width - spacing * (barCount + 1)) / barCount
                val chartHeight = size.height - 32f

                bars.forEachIndexed { index, (label, days, color) ->
                    val x = spacing + index * (barWidth + spacing)
                    val fraction = ((days / maxDays) * animProgress.value).toFloat()
                    val barH = chartHeight * fraction

                    // Background track for the bar
                    drawRoundRect(
                        color = ObservatoryColors.cardBorder.copy(alpha = 0.5f),
                        topLeft = Offset(x, 0f),
                        size = Size(barWidth, chartHeight),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                    )

                    // Rounded vertical bar
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, chartHeight - barH),
                        size = Size(barWidth, barH),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                    )

                    // Value label
                    drawContext.canvas.nativeCanvas.drawText(
                        "%.0f d".format(days),
                        x + barWidth / 2,
                        (chartHeight - barH - 8f).coerceAtLeast(24f),
                        android.graphics.Paint().apply {
                            this.color = android.graphics.Color.WHITE
                            textSize = 28f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        },
                    )

                    // Label
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        x + barWidth / 2,
                        size.height - 2f,
                        android.graphics.Paint().apply {
                            this.color = android.graphics.Color.parseColor("#9E9E9E")
                            textSize = 24f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        },
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Gender Victim Bar Chart
// ═══════════════════════════════════════════════════════════════════

@Composable
fun VictimGenderChart(
    data: List<Pair<String, Long>>,
    title: String,
    modifier: Modifier = Modifier,
) {
    val genderColors = listOf(
        ObservatoryColors.femaleColor,
        ObservatoryColors.maleColor,
        ObservatoryColors.unknownColor,
    )
    ObservatoryBarChart(
        data = data,
        title = title,
        colors = genderColors,
        modifier = modifier,
    )
}
