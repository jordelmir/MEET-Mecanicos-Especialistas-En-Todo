package com.elysium369.meet.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.evair.agent.AutomotiveAgentGateway
import com.elysium369.meet.core.evair.bridge.EvairHealthSummary
import com.elysium369.meet.core.evair.bridge.VehicleToolFacade
import com.elysium369.meet.core.evair.domain.DiagnosticAgentRequest
import com.elysium369.meet.core.evair.domain.DiagnosticHypothesis
import com.elysium369.meet.core.evair.domain.DiagnosticResult
import com.elysium369.meet.core.evair.domain.DiagnosticTrigger
import com.elysium369.meet.core.evair.domain.EvairResult
import com.elysium369.meet.core.evair.state.VehicleStateEngine
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElysiumAiScreen(
    facade: VehicleToolFacade,
    gateway: AutomotiveAgentGateway,
    stateEngine: VehicleStateEngine,
    onBack: () -> Unit,
    onNavigateToTerminal: () -> Unit,
    onNavigateToLiveTelemetry: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snapshot by stateEngine.snapshot.collectAsState()
    var healthSummary by remember { mutableStateOf<EvairHealthSummary?>(null) }
    var diagnosticResult by remember { mutableStateOf<DiagnosticResult?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var expandedHypothesisId by remember { mutableStateOf<String?>(null) }

    // Load initial health on mount
    LaunchedEffect(snapshot.timestampMs) {
        try {
            healthSummary = facade.healthSummary()
        } catch (_: Exception) {}
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Scaffold(
        topBar = {
            EliteTopAppBar(
                title = "Elysium AI",
                subtitle = "Automotive Intelligence Runtime",
                onBackClick = onBack,
                actions = {
                    IconButton(onClick = onNavigateToTerminal) {
                        Icon(Icons.Default.Terminal, contentDescription = "Terminal", tint = MeetColors.cyberCyan)
                    }
                }
            )
        },
        containerColor = MeetColors.backgroundDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. Connection & Status Banner ──
            EliteCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (snapshot.connection.hasRealEcuLink)
                                            MeetColors.neonGreen.copy(alpha = pulseAlpha)
                                        else
                                            Color.Gray
                                    )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (snapshot.connection.hasRealEcuLink) "VEHÍCULO CONECTADO" else "MODO OFFLINE",
                                color = if (snapshot.connection.hasRealEcuLink) MeetColors.neonGreen else Color.Gray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = snapshot.vehicle.label ?: "Vehículo Principal",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "VIN: ${snapshot.vehicle.vin ?: "Detectado por bus"}",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Surface(
                        color = MeetColors.cyberCyan.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.cyberCyan)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("EVAIR V1", color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            // ── 2. Holistic Vehicle Health Score Card ──
            healthSummary?.let { health ->
                EliteCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "SALUD INTEGRAL DEL VEHÍCULO",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "${health.overallScore} / 100",
                                color = when {
                                    health.overallScore >= 85 -> MeetColors.neonGreen
                                    health.overallScore >= 65 -> MeetColors.warning
                                    else -> MeetColors.error
                                },
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // Subsystem Breakdown
                        SubsystemScoreBar("Motor", health.engineScore, 0.30f)
                        SubsystemScoreBar("Combustible", health.fuelScore, 0.25f)
                        SubsystemScoreBar("Refrigeración", health.coolingScore, 0.20f)
                        SubsystemScoreBar("Eléctrico", health.electricalScore, 0.15f)
                        SubsystemScoreBar("Emisiones", health.emissionsScore, 0.10f)

                        health.electricalDiagnosis?.let { diag ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Diagnóstico Eléctrico: $diag",
                                color = MeetColors.cyberCyan,
                                fontSize = 12.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }
            }

            // ── 3. Diagnostic Action Center ──
            Button(
                onClick = {
                    isAnalyzing = true
                    scope.launch {
                        try {
                            val req = DiagnosticAgentRequest(
                                requestId = "diag_${System.currentTimeMillis()}",
                                vehicleId = snapshot.vehicle.vehicleId,
                                trigger = DiagnosticTrigger.USER_REQUEST,
                                snapshot = snapshot
                            )
                            val res = gateway.diagnose(req)
                            if (res is EvairResult.Success) {
                                diagnosticResult = res.value
                            }
                        } finally {
                            isAnalyzing = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeetColors.electricBlue
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = !isAnalyzing
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Razonando con EVAIR...", color = Color.White, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Analizar Vehículo con IA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            // ── 4. Diagnostic Hypotheses & Evidence Inspector ──
            diagnosticResult?.let { diag ->
                EliteCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("DICTAMEN DIAGNÓSTICO", color = MeetColors.neonGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(diag.summary, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)

                        Spacer(Modifier.height(16.dp))
                        Text("HIPÓTESIS RANKED POR EVIDENCIA:", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)

                        Spacer(Modifier.height(8.dp))
                        diag.hypotheses.forEach { hyp ->
                            HypothesisCard(
                                hypothesis = hyp,
                                isExpanded = expandedHypothesisId == hyp.id,
                                onToggle = {
                                    expandedHypothesisId = if (expandedHypothesisId == hyp.id) null else hyp.id
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        if (diag.recommendedTests.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("SIGUIENTES PRUEBAS DISCRIMINANTES RECOMENDADAS:", color = MeetColors.cyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Spacer(Modifier.height(6.dp))
                            diag.recommendedTests.forEach { test ->
                                Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "${test.testId}: ${test.reason}",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 5. Quick Access Navigation ──
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onNavigateToLiveTelemetry,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Telemetría", color = Color.White, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onNavigateToTerminal,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Terminal", color = MeetColors.cyberCyan, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SubsystemScoreBar(name: String, score: Int, weight: Float) {
    val scoreColor by animateColorAsState(
        targetValue = when {
            score >= 85 -> MeetColors.neonGreen
            score >= 65 -> MeetColors.warning
            else -> MeetColors.error
        },
        label = "color"
    )

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("$name (${(weight * 100).toInt()}%)", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            Text("$score%", color = scoreColor, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (score / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = scoreColor,
            trackColor = Color.White.copy(alpha = 0.1f),
        )
    }
}

@Composable
private fun HypothesisCard(
    hypothesis: DiagnosticHypothesis,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    val confidencePct = (hypothesis.confidence * 100).toInt()
    Surface(
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isExpanded) MeetColors.cyberCyan else Color.White.copy(alpha = 0.1f)
        ),
        modifier = Modifier.fillMaxWidth().clickable { onToggle() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$confidencePct%",
                        color = if (confidencePct >= 60) MeetColors.cyberCyan else Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(44.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = hypothesis.cause,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    if (hypothesis.supportingEvidence.isNotEmpty()) {
                        Text("EVIDENCIA DE SOPORTE:", color = MeetColors.neonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        hypothesis.supportingEvidence.forEach { ev ->
                            Text("• [${ev.source}] ${ev.key}: ${ev.value}", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }

                    if (hypothesis.missingEvidence.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("EVIDENCIA PENDIENTE / FALTANTE:", color = MeetColors.warning, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        hypothesis.missingEvidence.forEach { m ->
                            Text("• $m", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}
