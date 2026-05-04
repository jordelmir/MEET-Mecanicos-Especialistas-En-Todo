package com.elysium369.meet.ui.screens.scanner

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.ConnectionStatusBar
import com.elysium369.meet.ui.components.EliteScrollContainer
import com.elysium369.meet.ui.components.eliteScrollbar
import com.elysium369.meet.ui.components.WaveGraphWidget
import com.elysium369.meet.ui.components.GaugeWidget
import kotlinx.coroutines.delay

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

    val gridState = rememberLazyGridState()
    val cols = if (isLandscape) 3 else 2

    // Staggered entry animation control
    var visibleCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        for (i in 0..defaultGauges.size + 1) {
            delay(60L)
            visibleCount = i
        }
    }

    EliteScrollContainer(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(cols),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().eliteScrollbar(gridState)
        ) {
            // ─── CONNECTION STATUS BAR (Full width) ───
            item(span = { GridItemSpan(cols) }) {
                AnimatedEntryItem(index = 0, visibleCount = visibleCount) {
                    ConnectionStatusBar(viewModel = viewModel, showQos = true)
                }
            }

            // ─── VEHICLE HEALTH INDEX CARD (Full width) ───
            item(span = { GridItemSpan(cols) }) {
                AnimatedEntryItem(index = 1, visibleCount = visibleCount) {
                    HealthIndexCard(healthScore = healthScore, anomalousPids = anomalousPids)
                }
            }

            // ─── GAUGE / WAVE WIDGETS ───
            items(defaultGauges.size) { index ->
                val gauge = defaultGauges[index]
                val currentValue = liveData[gauge.pid] ?: 0f
                val isAnomaly = anomalousPids.any { it.pid == gauge.pid }
                val isPinned = pinnedPids.contains(gauge.pid)

                AnimatedEntryItem(index = index + 2, visibleCount = visibleCount) {
                    GaugeCard(
                        gauge = gauge,
                        currentValue = currentValue,
                        isAnomaly = isAnomaly,
                        isPinned = isPinned,
                        telemetryHistory = telemetryHistory[gauge.pid],
                        onTogglePin = {
                            if (isPinned) viewModel.unpinPid(gauge.pid)
                            else viewModel.pinPid(gauge.pid)
                        }
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// ANIMATED ENTRY WRAPPER
// ═══════════════════════════════════════

@Composable
private fun AnimatedEntryItem(
    index: Int,
    visibleCount: Int,
    content: @Composable () -> Unit
) {
    val isVisible = index <= visibleCount
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "entryAlpha$index"
    )
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.85f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "entryScale$index"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 30f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "entryOffset$index"
    )

    Box(
        modifier = Modifier
            .alpha(alpha)
            .scale(scale)
            .offset(y = offsetY.dp)
    ) {
        content()
    }
}

// ═══════════════════════════════════════
// HEALTH INDEX CARD
// ═══════════════════════════════════════

@Composable
private fun HealthIndexCard(
    healthScore: Int,
    anomalousPids: List<com.elysium369.meet.core.ai.HealthAnomaly>
) {
    val scoreColor = if (healthScore > 80) Color(0xFF39FF14) else if (healthScore > 50) Color(0xFFFFD700) else Color(0xFFFF003C)

    val infiniteTransition = rememberInfiniteTransition(label = "healthPulse")
    val borderGlow by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "borderGlow"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E1A)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, Brush.linearGradient(listOf(scoreColor.copy(alpha = borderGlow), Color.Transparent)), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("ÍNDICE DE SALUD VEHICULAR", color = Color.Gray, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            healthScore > 90 -> "SISTEMA ÓPTIMO"
                            healthScore > 75 -> "ESTADO BUENO"
                            healthScore > 50 -> "ALERTA DE SERVICIO"
                            else -> "RIESGO CRÍTICO"
                        },
                        color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black
                    )
                }

                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                    CircularProgressIndicator(
                        progress = healthScore / 100f,
                        modifier = Modifier.fillMaxSize(),
                        color = scoreColor,
                        strokeWidth = 6.dp,
                        trackColor = scoreColor.copy(alpha = 0.08f)
                    )
                    Text("$healthScore%", color = scoreColor, fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
            }

            if (anomalousPids.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = Color(0xFFFF3366).copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFFF3366).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "IA detectó ${anomalousPids.size} anomalía${if (anomalousPids.size > 1) "s" else ""} preventiva${if (anomalousPids.size > 1) "s" else ""}",
                            color = Color(0xFFFF3366), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// GAUGE CARD WITH OVERLAY PIN
// ═══════════════════════════════════════

@Composable
private fun GaugeCard(
    gauge: GaugeConfig,
    currentValue: Float,
    isAnomaly: Boolean,
    isPinned: Boolean,
    telemetryHistory: List<Float>?,
    onTogglePin: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cardPulse")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "borderAlpha"
    )

    val borderColor = when {
        isAnomaly -> Color(0xFFFF003C).copy(alpha = borderAlpha)
        isPinned -> Color(0xFF39FF14).copy(alpha = 0.6f)
        else -> Color(0xFF39FF14).copy(alpha = 0.15f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF06060F), RoundedCornerShape(14.dp))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(2.dp)
    ) {
        if (gauge.type == GaugeType.WAVE) {
            WaveGraphWidget(
                label = gauge.label,
                currentValue = currentValue,
                minVal = gauge.minVal,
                maxVal = gauge.maxVal,
                unit = gauge.unit,
                isAnomaly = isAnomaly,
                historyData = telemetryHistory
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

        // Pin overlay
        IconButton(
            onClick = onTogglePin,
            modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
        ) {
            Text(if (isPinned) "📌" else "📍", fontSize = 10.sp)
        }

        // Pinned badge
        if (isPinned) {
            Box(
                modifier = Modifier.align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(Color(0xFF39FF14).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .border(0.5.dp, Color(0xFF39FF14).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("HI-FREQ", color = Color(0xFF39FF14), fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
            }
        }
    }
}
