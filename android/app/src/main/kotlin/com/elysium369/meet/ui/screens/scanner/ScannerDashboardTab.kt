package com.elysium369.meet.ui.screens.scanner

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.GaugeWidget
import com.elysium369.meet.ui.components.WaveGraphWidget

@Composable
fun ScannerDashboardTab(
    viewModel: ObdViewModel,
    isLandscape: Boolean,
    defaultGauges: List<GaugeConfig>
) {
    val liveData by viewModel.liveData.collectAsState()
    val pinnedPids by viewModel.pinnedPids.collectAsState()
    val telemetryHistory by viewModel.telemetryHistory.collectAsState()
    val anomalousPids by viewModel.anomalousPids.collectAsState()
    val healthScore by viewModel.healthScore.collectAsState()

    LazyVerticalGrid(
        columns = GridCells.Fixed(if (isLandscape) 3 else 2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. VEHICLE HEALTH INDEX CARD (Spans full width)
        item(span = { GridItemSpan(if (isLandscape) 3 else 2) }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                if (healthScore > 80) Color(0xFF00FFCC) else if (healthScore > 50) Color(0xFFFFD700) else Color(0xFFFF003C),
                                Color.Transparent
                            )
                        ),
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "ÍNDICE DE SALUD VEHICULAR",
                                color = Color.Gray,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when {
                                    healthScore > 90 -> "SISTEMA ÓPTIMO"
                                    healthScore > 75 -> "ESTADO BUENO"
                                    healthScore > 50 -> "ALERTA DE SERVICIO"
                                    else -> "RIESGO CRÍTICO"
                                },
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black
                            )
                        }

                        // Circular Progress for Health
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(70.dp)) {
                            val scoreColor = if (healthScore > 80) Color(0xFF00FFCC) else if (healthScore > 50) Color(0xFFFFD700) else Color(0xFFFF003C)
                            CircularProgressIndicator(
                                progress = healthScore / 100f,
                                modifier = Modifier.fillMaxSize(),
                                color = scoreColor,
                                strokeWidth = 6.dp,
                                trackColor = scoreColor.copy(alpha = 0.1f)
                            )
                            Text("$healthScore%", color = scoreColor, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        }
                    }

                    if (anomalousPids.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = Color(0xFFFF3366).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFFF3366).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("⚠️", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "IA detectó ${anomalousPids.size} anomalías preventivas. Revisa los sensores en rojo.",
                                    color = Color(0xFFFF3366),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        items(defaultGauges) { gauge ->
            val currentValue = liveData[gauge.pid] ?: 0f
            val isAnomaly = anomalousPids.any { it.pid == gauge.pid }
            val isPinned = pinnedPids.contains(gauge.pid)
            
            val infiniteTransition = rememberInfiniteTransition(label = "borderPulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 0.8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseAlpha"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black, RoundedCornerShape(12.dp))
                    .border(
                        1.dp,
                        if (isAnomaly) Color(0xFFFF3366).copy(alpha = pulseAlpha)
                        else if (isPinned) Color(0xFF00FFCC)
                        else Color(0xFF00FFCC).copy(alpha = 0.3f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(2.dp)
            ) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        IconButton(
                            onClick = { if (isPinned) viewModel.unpinPid(gauge.pid) else viewModel.pinPid(gauge.pid) },
                            modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                        ) {
                            Text(if (isPinned) "📌" else "📍", fontSize = 12.sp)
                        }
                    }
                    if (gauge.type == GaugeType.WAVE) {
                        WaveGraphWidget(
                            label = gauge.label,
                            currentValue = currentValue,
                            minVal = gauge.minVal,
                            maxVal = gauge.maxVal,
                            unit = gauge.unit,
                            isAnomaly = isAnomaly,
                            historyData = telemetryHistory[gauge.pid]
                        )
                    } else {
                        GaugeWidget(
                            label = gauge.label,
                            value = currentValue,
                            minVal = gauge.minVal,
                            maxVal = gauge.maxVal,
                            unit = gauge.unit,
                            warningThreshold = gauge.maxVal * 0.75f,
                            criticalThreshold = gauge.maxVal * 0.90f,
                            isAnomaly = isAnomaly
                        )
                    }
                }
            }
        }
    }
}
