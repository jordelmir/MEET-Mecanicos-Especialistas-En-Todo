package com.elysium369.meet.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.core.health.*
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.components.neonGlow
import com.elysium369.meet.ui.theme.MeetColors
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScoreScreen(
    navController: NavController,
    viewModel: ObdViewModel
) {
    val healthReport by viewModel.predictiveHealthReport.collectAsState()
    val isAnalyzing by viewModel.isAnalyzingHealth.collectAsState()
    val liveData by viewModel.liveData.collectAsState()

    // Animated score
    val animatedScore by animateIntAsState(
        targetValue = healthReport?.overallScore ?: 0,
        animationSpec = tween(1500, easing = FastOutSlowInEasing), label = "score"
    )

    // Pulsing glow
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "alpha"
    )
    val scanAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "scan"
    )

    LaunchedEffect(Unit) { viewModel.runPredictiveAnalysis() }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            EliteTopAppBar(
                title = "HEALTH SCORE",
                subtitle = "Motor Predictivo AI",
                onBackClick = { navController.popBackStack() }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // ── Main Score Gauge ──
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val scoreColor = getScoreColor(animatedScore)
                    Canvas(modifier = Modifier.size(240.dp)) {
                        drawHealthGauge(animatedScore, scoreColor, glowAlpha, scanAngle)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$animatedScore",
                            fontSize = 64.sp, fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = getScoreColor(animatedScore)
                        )
                        Text(
                            getScoreLabel(animatedScore),
                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = getScoreColor(animatedScore).copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // ── Subsystem Radar ──
            item {
                val report = healthReport
                if (report != null) {
                    SubsystemCard(report)
                }
            }

            // ── Predictive Alerts ──
            val alerts = healthReport?.alerts ?: emptyList()
            if (alerts.isNotEmpty()) {
                item {
                    Text("⚠️ ALERTAS PREDICTIVAS", color = MeetColors.warning,
                        fontWeight = FontWeight.Black, fontSize = 16.sp,
                        modifier = Modifier.padding(top = 8.dp))
                }
                items(alerts) { alert -> AlertCard(alert) }
            }

            // ── Sensor Trends ──
            val trends = healthReport?.trends?.filter { it.direction != TrendDirection.STABLE } ?: emptyList()
            if (trends.isNotEmpty()) {
                item {
                    Text("📈 TENDENCIAS DETECTADAS", color = MeetColors.electricBlue,
                        fontWeight = FontWeight.Black, fontSize = 16.sp,
                        modifier = Modifier.padding(top = 8.dp))
                }
                items(trends) { trend -> TrendCard(trend) }
            }

            // ── Data Stats ──
            item {
                val report = healthReport
                if (report != null) {
                    DataStatsCard(report)
                }
            }

            // ── Analyze Button ──
            item {
                Button(
                    onClick = { viewModel.runPredictiveAnalysis() },
                    enabled = !isAnalyzing,
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                        .neonGlow(MeetColors.neonGreen, minElevation = 4f, maxElevation = 16f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("ANALIZANDO...", color = Color.Black, fontWeight = FontWeight.Black)
                    } else {
                        Text("🔄 RE-ANALIZAR", color = Color.Black, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

// ── Canvas Gauge Drawing ──
private fun DrawScope.drawHealthGauge(score: Int, color: Color, glowAlpha: Float, scanAngle: Float) {
    val strokeWidth = 16f
    val radius = size.minDimension / 2 - strokeWidth
    val center = Offset(size.width / 2, size.height / 2)

    // Background ring
    drawCircle(color = MeetColors.cardBackground, radius = radius, center = center,
        style = Stroke(width = strokeWidth))

    // Score arc (270° sweep max)
    val sweepAngle = (score / 100f) * 270f
    drawArc(
        color = color, startAngle = 135f, sweepAngle = sweepAngle,
        useCenter = false, style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2)
    )

    // Glow arc overlay
    drawArc(
        color = color.copy(alpha = glowAlpha * 0.3f), startAngle = 135f, sweepAngle = sweepAngle,
        useCenter = false, style = Stroke(width = strokeWidth * 3, cap = StrokeCap.Round),
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2)
    )

    // Rotating scanner line
    rotate(scanAngle, pivot = center) {
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(color.copy(alpha = 0f), color.copy(alpha = 0.4f)),
                start = center, end = Offset(center.x, center.y - radius)
            ),
            start = center, end = Offset(center.x, center.y - radius),
            strokeWidth = 2f
        )
    }

    // Tick marks
    for (i in 0..10) {
        val angle = Math.toRadians((135.0 + i * 27.0))
        val innerR = radius - 12f
        val outerR = radius + 4f
        drawLine(
            color = MeetColors.cardBackgroundLighter,
            start = Offset(center.x + innerR * cos(angle).toFloat(), center.y + innerR * sin(angle).toFloat()),
            end = Offset(center.x + outerR * cos(angle).toFloat(), center.y + outerR * sin(angle).toFloat()),
            strokeWidth = if (i % 5 == 0) 3f else 1.5f
        )
    }
}

// ── Subsystem Card with Bar Chart ──
@Composable
private fun SubsystemCard(report: PredictiveHealthReport) {
    Surface(
        color = MeetColors.cardBackground.copy(alpha = 0.8f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("SUBSISTEMAS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            SubsystemBar("🔧 Motor", report.engineScore)
            SubsystemBar("⛽ Combustible", report.fuelScore)
            SubsystemBar("🌡️ Refrigeración", report.coolingScore)
            SubsystemBar("⚡ Eléctrico", report.electricalScore)
            SubsystemBar("💨 Emisiones", report.emissionsScore)
        }
    }
}

@Composable
private fun SubsystemBar(label: String, score: Int) {
    val color = getScoreColor(score)
    val animatedWidth by animateFloatAsState(
        targetValue = score / 100f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing), label = "bar"
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MeetColors.textSecondary, fontSize = 12.sp,
            modifier = Modifier.width(120.dp))
        Box(
            modifier = Modifier.weight(1f).height(8.dp)
                .clip(RoundedCornerShape(4.dp)).background(MeetColors.cardBackground)
        ) {
            Box(
                modifier = Modifier.fillMaxHeight().fillMaxWidth(animatedWidth)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color)))
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("$score", color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp,
            fontFamily = FontFamily.Monospace, modifier = Modifier.width(28.dp))
    }
}

// ── Alert Card ──
@Composable
private fun AlertCard(alert: PredictiveAlert) {
    val (borderColor, bgColor) = when (alert.severity) {
        AlertSeverity.CRITICAL -> Pair(MeetColors.error, MeetColors.error.copy(alpha = 0.1f))
        AlertSeverity.HIGH -> Pair(MeetColors.warning, MeetColors.warning.copy(alpha = 0.08f))
        AlertSeverity.MODERATE -> Pair(MeetColors.electricBlue, MeetColors.electricBlue.copy(alpha = 0.08f))
    }
    Surface(
        color = bgColor, shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when (alert.severity) {
                    AlertSeverity.CRITICAL -> "🔴"
                    AlertSeverity.HIGH -> "🟡"
                    AlertSeverity.MODERATE -> "🔵"
                }
                Text(icon, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(alert.label.uppercase(), color = borderColor, fontWeight = FontWeight.Black, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                if (alert.predictedDaysToFailure > 0) {
                    Text("~${alert.predictedDaysToFailure}d", color = borderColor,
                        fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(alert.message, color = MeetColors.textPrimary, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

// ── Trend Card ──
@Composable
private fun TrendCard(trend: SensorTrend) {
    val trendIcon = when (trend.direction) {
        TrendDirection.RISING -> "📈"
        TrendDirection.FALLING -> "📉"
        TrendDirection.STABLE -> "➡️"
    }
    val trendColor = when (trend.direction) {
        TrendDirection.RISING -> MeetColors.warning
        TrendDirection.FALLING -> MeetColors.electricBlue
        TrendDirection.STABLE -> MeetColors.textMuted
    }
    Surface(
        color = MeetColors.cardBackground.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(12.dp))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(trendIcon, fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(trend.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    "${String.format("%.2f", trend.slopePerDay)}${trend.unit}/día • ${trend.dataPoints} muestras",
                    color = MeetColors.textSecondary, fontSize = 11.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    String.format("%.1f", trend.currentValue) + trend.unit,
                    color = trendColor, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "avg: ${String.format("%.1f", trend.historicalAverage)}",
                    color = MeetColors.textMuted, fontSize = 10.sp
                )
            }
        }
    }
}

// ── Data Stats Card ──
@Composable
private fun DataStatsCard(report: PredictiveHealthReport) {
    Surface(
        color = MeetColors.cardBackground.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(12.dp))
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem("📊", "${report.dataPointCount}", "Registros")
            StatItem("🔧", "${report.recordedPidCount}", "Sensores")
            StatItem("⚠️", "${report.alerts.size}", "Alertas")
            StatItem("📈", "${report.trends.count { it.direction != TrendDirection.STABLE }}", "Tendencias")
        }
    }
}

@Composable
private fun StatItem(icon: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 20.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
        Text(label, color = MeetColors.textMuted, fontSize = 10.sp)
    }
}

// ── Helpers ──
private fun getScoreColor(score: Int): Color = when {
    score >= 80 -> MeetColors.neonGreen  // Green
    score >= 60 -> MeetColors.warning  // Yellow
    score >= 40 -> MeetColors.warning  // Orange
    else -> MeetColors.error          // Red
}

private fun getScoreLabel(score: Int): String = when {
    score >= 90 -> "EXCELENTE"
    score >= 80 -> "MUY BUENO"
    score >= 60 -> "ACEPTABLE"
    score >= 40 -> "ATENCIÓN REQUERIDA"
    score >= 20 -> "ESTADO CRÍTICO"
    else -> "EMERGENCIA"
}
