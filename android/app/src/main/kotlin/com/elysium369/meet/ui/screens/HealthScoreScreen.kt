package com.elysium369.meet.ui.screens

import com.elysium369.meet.ui.navigation.backOrHome

import com.elysium369.meet.ui.components.AnimatedNeonGlyph

import com.elysium369.meet.ui.components.AnimatedNeonIcon

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.core.health.*
import com.elysium369.meet.data.local.entities.HealthSnapshotEntity
import com.elysium369.meet.data.local.entities.PredictionEventEntity
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.PhantomSectionHeader
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.components.neonGlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    
    val healthHistory by viewModel.healthHistory.collectAsState()
    val predictionEvents by viewModel.predictionEvents.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: Análisis, 1: Timeline, 2: Eléctrico
    
    // Comparación state
    var selectedSnapshotToCompare1 by remember { mutableStateOf<HealthSnapshotEntity?>(null) }
    var selectedSnapshotToCompare2 by remember { mutableStateOf<HealthSnapshotEntity?>(null) }
    var showComparisonDialog by remember { mutableStateOf(false) }

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

    LaunchedEffect(Unit) { 
        viewModel.runPredictiveAnalysis() 
    }

    Scaffold(
        containerColor = MeetColors.backgroundDark,
        topBar = {
            EliteTopAppBar(
                title = "SALUD PREDICTIVA",
                subtitle = "EXPEDIENTE MÉDICO VIVO",
                onBackClick = { navController.backOrHome() },
                backgroundColor = MeetColors.backgroundDark
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MeetColors.backgroundDeep,
                contentColor = MeetColors.neonGreen,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MeetColors.neonGreen
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("ANÁLISIS", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("TIMELINE", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("SISTEMA ELÉCTRICO", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
            ) {
                if (selectedTab == 0) {
                    // ── Main Score Gauge ──
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val scoreColor = getScoreColor(animatedScore)
                            Canvas(modifier = Modifier.size(220.dp)) {
                                drawHealthGauge(animatedScore, scoreColor, glowAlpha, scanAngle)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "$animatedScore",
                                    fontSize = 58.sp, fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = getScoreColor(animatedScore)
                                )
                                Text(
                                    getScoreLabel(animatedScore),
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    color = getScoreColor(animatedScore).copy(alpha = 0.8f)
                                )
                                Text(
                                    "RIESGO: " + when {
                                        animatedScore >= 80 -> "BAJO"
                                        animatedScore >= 60 -> "MODERADO"
                                        else -> "ALTO"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = scoreColor,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }

                    // Subsystems list
                    item {
                        healthReport?.let { report ->
                            SubsystemCard(report)
                        }
                    }

                    // Alerts
                    val alerts = healthReport?.alerts ?: emptyList()
                    if (alerts.isNotEmpty()) {
                        item {
                            PhantomSectionHeader(label = "Alertas de Desgaste", accentColor = MeetColors.warning)
                        }
                        items(alerts) { alert -> AlertCard(alert) }
                    }

                    // Trends
                    val trends = healthReport?.trends?.filter { it.direction != TrendDirection.STABLE } ?: emptyList()
                    if (trends.isNotEmpty()) {
                        item {
                            PhantomSectionHeader(label = "Tendencias de Sensores", accentColor = MeetColors.cyberCyan)
                        }
                        items(trends) { trend -> TrendCard(trend) }
                    }

                    // Statistics card
                    item {
                        healthReport?.let { report ->
                            DataStatsCard(report)
                        }
                    }

                    // Re-analyze action
                    item {
                        EliteButton(
                            text = if (isAnalyzing) "Analizando..." else "Ejecutar Diagnóstico Predictivo",
                            onClick = { viewModel.runPredictiveAnalysis() },
                            modifier = Modifier.fillMaxWidth(),
                            isEnabled = !isAnalyzing
                        )
                    }

                } else if (selectedTab == 1) {
                    // LINE CHART of health history
                    if (healthHistory.size >= 2) {
                        item {
                            EliteCard(
                                modifier = Modifier.fillMaxWidth(),
                                glowColor = MeetColors.cyberCyan,
                                backgroundColor = MeetColors.backgroundDeep,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "TRAYECTORIA DE SALUD",
                                        color = MeetColors.cyberCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    HealthScoreTimelineChart(
                                        snapshots = healthHistory.sortedBy { it.timestamp },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(150.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Comparison Setup Row
                    if (healthHistory.size >= 2) {
                        item {
                            EliteCard(
                                modifier = Modifier.fillMaxWidth(),
                                glowColor = MeetColors.neonGreen,
                                backgroundColor = MeetColors.backgroundDeep,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        "COMPARACIÓN HISTÓRICA",
                                        color = MeetColors.neonGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        "Selecciona dos momentos de inspección del historial para comparar su evolución.",
                                        color = MeetColors.textSecondary,
                                        fontSize = 11.sp
                                    )

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { showComparisonDialog = true },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen.copy(alpha = 0.15f)),
                                            border = BorderStroke(1.dp, MeetColors.neonGreen)
                                        ) {
                                            AnimatedNeonIcon(Icons.Default.CompareArrows, contentDescription = "Comparar", tint = MeetColors.neonGreen)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Comparar Inspecciones", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // History list
                    item {
                        PhantomSectionHeader(label = "Historial Completo", accentColor = MeetColors.electricBlue)
                    }

                    if (healthHistory.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    "No hay registros de salud guardados.\nEjecuta análisis de sensores para empezar a construir el historial.",
                                    color = MeetColors.textSecondary,
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        items(healthHistory) { snapshot ->
                            val isSelected1 = selectedSnapshotToCompare1?.id == snapshot.id
                            val isSelected2 = selectedSnapshotToCompare2?.id == snapshot.id
                            
                            EliteCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected1) {
                                            selectedSnapshotToCompare1 = null
                                        } else if (isSelected2) {
                                            selectedSnapshotToCompare2 = null
                                        } else if (selectedSnapshotToCompare1 == null) {
                                            selectedSnapshotToCompare1 = snapshot
                                        } else if (selectedSnapshotToCompare2 == null) {
                                            selectedSnapshotToCompare2 = snapshot
                                        } else {
                                            selectedSnapshotToCompare1 = snapshot
                                        }
                                    },
                                enableHolo3D = false,
                                glowColor = if (isSelected1 || isSelected2) MeetColors.neonGreen else MeetColors.borderSubtle,
                                backgroundColor = MeetColors.backgroundDeep,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(snapshot.timestamp)),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "DTCs: ${snapshot.activeDtcCount} | Anomalías: ${snapshot.anomalyCount}",
                                            color = MeetColors.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (isSelected1) {
                                            Text("A", color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                        }
                                        if (isSelected2) {
                                            Text("B", color = MeetColors.cyberCyan, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                        }
                                        Text(
                                            text = "${snapshot.overallScore} pts",
                                            color = getScoreColor(snapshot.overallScore),
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                } else if (selectedTab == 2) {
                    // Electrical Subsystem Diagnosis details
                    val diagnosis = healthReport?.electricalDiagnosis
                    if (diagnosis == null) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No hay datos del sistema eléctrico. Encienda el escáner.", color = MeetColors.textSecondary)
                            }
                        }
                    } else {
                        item {
                            EliteCard(
                                modifier = Modifier.fillMaxWidth(),
                                glowColor = MeetColors.cyberCyan,
                                backgroundColor = MeetColors.backgroundDeep,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        "DIAGNÓSTICO DEL SISTEMA ELÉCTRICO",
                                        color = MeetColors.cyberCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    
                                    Divider(color = MeetColors.borderSubtle)

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Estado Alternador:", color = MeetColors.textSecondary, fontSize = 13.sp)
                                        Text(
                                            text = diagnosis.alternatorState,
                                            color = if (diagnosis.alternatorState.contains("Óptimo")) MeetColors.neonGreen else MeetColors.error,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Estado Batería:", color = MeetColors.textSecondary, fontSize = 13.sp)
                                        Text(
                                            text = diagnosis.batteryState,
                                            color = if (diagnosis.batteryState.contains("Óptima") || diagnosis.batteryState.contains("Cargando")) MeetColors.neonGreen else MeetColors.warning,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "RECOMENDACIÓN TÉCNICA",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MeetColors.backgroundDark, RoundedCornerShape(8.dp))
                                            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = diagnosis.recommendation,
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Battery Voltage live card
                        val voltage = liveData["0142"] ?: 0f
                        item {
                            EliteCard(
                                modifier = Modifier.fillMaxWidth(),
                                enableHolo3D = false,
                                glowColor = MeetColors.neonGreen,
                                backgroundColor = MeetColors.backgroundDeep,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Voltaje Actual OBD2:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(
                                            text = if (voltage > 0) "${String.format("%.2f", voltage)} V" else "Sin lectura",
                                            color = MeetColors.neonGreen,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Comparison Dialog
    if (showComparisonDialog) {
        AlertDialog(
            onDismissRequest = { showComparisonDialog = false },
            containerColor = MeetColors.backgroundDeep,
            title = {
                Text(
                    "COMPARACIÓN DE INSPECCIONES",
                    color = MeetColors.neonGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            },
            text = {
                val select1 = selectedSnapshotToCompare1
                val select2 = selectedSnapshotToCompare2
                
                if (select1 == null || select2 == null) {
                    Text(
                        "Por favor, selecciona dos inspecciones del historial (tocándolas en la lista) antes de comparar.",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("", modifier = Modifier.weight(1f))
                            Text("Inspección A", color = MeetColors.neonGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                            Text("Inspección B", color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                        }

                        Divider(color = MeetColors.borderSubtle)

                        ComparisonRow("Score General", "${select1.overallScore}", "${select2.overallScore}", select1.overallScore >= select2.overallScore)
                        ComparisonRow("Score Motor", "${select1.engineScore}", "${select2.engineScore}", select1.engineScore >= select2.engineScore)
                        ComparisonRow("Score Combust.", "${select1.fuelScore}", "${select2.fuelScore}", select1.fuelScore >= select2.fuelScore)
                        ComparisonRow("Score Refrig.", "${select1.coolingScore}", "${select2.coolingScore}", select1.coolingScore >= select2.coolingScore)
                        ComparisonRow("Score Eléctr.", "${select1.electricalScore}", "${select2.electricalScore}", select1.electricalScore >= select2.electricalScore)
                        ComparisonRow("Score Emis.", "${select1.emissionsScore}", "${select2.emissionsScore}", select1.emissionsScore >= select2.emissionsScore)
                        ComparisonRow("DTCs Activos", "${select1.activeDtcCount}", "${select2.activeDtcCount}", select1.activeDtcCount <= select2.activeDtcCount)
                        ComparisonRow("Anomalías", "${select1.anomalyCount}", "${select2.anomalyCount}", select1.anomalyCount <= select2.anomalyCount)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showComparisonDialog = false }) {
                    Text("CERRAR", color = MeetColors.neonGreen, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun ComparisonRow(label: String, valA: String, valB: String, isABetter: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MeetColors.textSecondary, fontSize = 11.sp, modifier = Modifier.weight(1.2f))
        Text(
            text = valA,
            color = if (isABetter) MeetColors.neonGreen else Color.White,
            fontWeight = if (isABetter) FontWeight.Bold else FontWeight.Normal,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = valB,
            color = if (!isABetter) MeetColors.cyberCyan else Color.White,
            fontWeight = if (!isABetter) FontWeight.Bold else FontWeight.Normal,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ── Custom Line Graph for Timeline ──
@Composable
fun HealthScoreTimelineChart(
    snapshots: List<HealthSnapshotEntity>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padding = 16.dp.toPx()

        val graphW = w - padding * 2
        val graphH = h - padding * 2

        if (snapshots.isEmpty()) return@Canvas

        val maxVal = 100f
        val minVal = 0f

        val pointsCount = snapshots.size
        val stepX = if (pointsCount > 1) graphW / (pointsCount - 1) else graphW

        val path = Path()
        val fillPath = Path()

        snapshots.forEachIndexed { index, snapshot ->
            val score = snapshot.overallScore.toFloat()
            val pctY = (score - minVal) / (maxVal - minVal)
            val px = padding + index * stepX
            val py = padding + graphH * (1f - pctY)

            if (index == 0) {
                path.moveTo(px, py)
                fillPath.moveTo(px, h - padding)
                fillPath.lineTo(px, py)
            } else {
                path.lineTo(px, py)
                fillPath.lineTo(px, py)
            }

            if (index == pointsCount - 1) {
                fillPath.lineTo(px, h - padding)
                fillPath.close()
            }

            // Draw dot
            drawCircle(
                color = getScoreColor(snapshot.overallScore),
                radius = 4.dp.toPx(),
                center = Offset(px, py)
            )
        }

        // Draw line
        drawPath(
            path = path,
            color = MeetColors.cyberCyan,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw gradient fill
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(MeetColors.cyberCyan.copy(alpha = 0.2f), Color.Transparent)
            )
        )

        // Draw horizontal grid lines (e.g. 50 pts, 80 pts)
        val line50Y = padding + graphH * (1f - 0.5f)
        val line80Y = padding + graphH * (1f - 0.8f)
        drawLine(
            color = MeetColors.borderSubtle.copy(alpha = 0.3f),
            start = Offset(padding, line50Y),
            end = Offset(w - padding, line50Y),
            strokeWidth = 1f
        )
        drawLine(
            color = MeetColors.borderSubtle.copy(alpha = 0.3f),
            start = Offset(padding, line80Y),
            end = Offset(w - padding, line80Y),
            strokeWidth = 1f
        )
    }
}

// ── Canvas Gauge Drawing ──
private fun DrawScope.drawHealthGauge(score: Int, color: Color, glowAlpha: Float, scanAngle: Float) {
    val strokeWidth = 14f
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
        useCenter = false, style = Stroke(width = strokeWidth * 2.5f, cap = StrokeCap.Round),
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
        val innerR = radius - 10f
        val outerR = radius + 3f
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
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("SUBSISTEMAS CLÍNICOS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            SubsystemBar("🔧 Motor (30%)", report.engineScore)
            SubsystemBar("⛽ Combustible (25%)", report.fuelScore)
            SubsystemBar("🌡️ Refrigeración (20%)", report.coolingScore)
            SubsystemBar("⚡ Eléctrico (15%)", report.electricalScore)
            SubsystemBar("💨 Emisiones (10%)", report.emissionsScore)
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MeetColors.textSecondary, fontSize = 11.sp,
            modifier = Modifier.width(130.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MeetColors.cardBackground)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedWidth)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color)))
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("$score", color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp,
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
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when (alert.severity) {
                    AlertSeverity.CRITICAL -> "🔴"
                    AlertSeverity.HIGH -> "🟡"
                    AlertSeverity.MODERATE -> "🔵"
                }
                AnimatedNeonGlyph(icon, contentDescription = null, fontSize = 15.sp)
                Spacer(Modifier.width(8.dp))
                Text(alert.label.uppercase(), color = borderColor, fontWeight = FontWeight.Black, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                if (alert.predictedDaysToFailure > 0) {
                    Text("~${alert.predictedDaysToFailure}d", color = borderColor,
                        fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
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
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(12.dp))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(trendIcon, fontSize = 18.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(trend.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(
                    "${String.format("%.2f", trend.slopePerDay)}${trend.unit}/día • ${trend.dataPoints} muestras",
                    color = MeetColors.textSecondary, fontSize = 10.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    String.format("%.1f", trend.currentValue) + trend.unit,
                    color = trendColor, fontWeight = FontWeight.Bold, fontSize = 13.sp,
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
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(12.dp))
    ) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
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
        AnimatedNeonGlyph(icon, contentDescription = null, fontSize = 18.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
        Text(label, color = MeetColors.textMuted, fontSize = 9.sp)
    }
}

// ── Helpers ──
private fun getScoreColor(score: Int): Color = when {
    score >= 80 -> MeetColors.neonGreen
    score >= 60 -> MeetColors.warning
    score >= 40 -> MeetColors.warning
    else -> MeetColors.error
}

private fun getScoreLabel(score: Int): String = when {
    score >= 90 -> "EXCELENTE"
    score >= 80 -> "MUY BUENO"
    score >= 60 -> "ACEPTABLE"
    score >= 40 -> "ATENCIÓN REQUERIDA"
    score >= 20 -> "ESTADO CRÍTICO"
    else -> "EMERGENCIA"
}
