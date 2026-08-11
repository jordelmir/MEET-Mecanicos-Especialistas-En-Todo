package com.elysium369.meet.ui.screens

import com.elysium369.meet.ui.components.AnimatedNeonIcon
import com.elysium369.meet.core.diagnostics.DiagnosticHypothesisEngine
import com.elysium369.meet.core.diagnostics.GuidedDiagnosisMode
import com.elysium369.meet.core.diagnostics.DiagnosticReasoningInput
import com.elysium369.meet.core.diagnostics.DiagnosticReasoningResult
import com.elysium369.meet.core.diagnostics.PartRecommendationState
import com.elysium369.meet.core.diagnostics.TestStatus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteIconButton
import com.elysium369.meet.ui.components.EliteScrollContainer
import com.elysium369.meet.ui.components.eliteScrollbar
import com.elysium369.meet.ai.ProprietaryGroundedContextBuilder
import com.elysium369.meet.ui.knowledge.RepairKnowledgeEvidencePanel
import com.elysium369.meet.ui.knowledge.RepairKnowledgeUiState
import com.elysium369.meet.ui.knowledge.rememberRepairKnowledgeUiState
import com.elysium369.meet.ui.knowledge.toActiveVehicleIdentity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDiagnosticScreen(
    dtcCode: String,
    initialGroundedContext: String? = null,
    onBack: () -> Unit,
    viewModel: com.elysium369.meet.ui.ObdViewModel,
    onNavigateToSettings: () -> Unit = {},
    onRequestMechanic: (String) -> Unit = {},
    onRequestPart: (String) -> Unit = {},
    onOpenComponent3d: () -> Unit = {}
) {
    val context = LocalContext.current
    // Read AI config from the global secure settings (same store as AiSettingsScreen)
    val globalPrefs = remember { context.getSharedPreferences("meet_ai_settings_prefs", android.content.Context.MODE_PRIVATE) }
    val provider by remember { derivedStateOf { globalPrefs.getString("selected_provider_id", "minimax") ?: "minimax" } }
    val modelName by remember { derivedStateOf { globalPrefs.getString("model_$provider", "") ?: "" } }
    val baseUrl by remember { derivedStateOf { globalPrefs.getString("base_url_$provider", "") ?: "" } }

    var aiResponse by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val anomalousPids by viewModel.anomalousPids.collectAsState()
    val activeDtcs by viewModel.activeDtcs.collectAsState()
    val telemetrySamples by viewModel.telemetrySamples.collectAsState()
    val freezeFrameData by viewModel.freezeFrameData.collectAsState()
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    val dtcsForKnowledge = remember(dtcCode, activeDtcs) {
        (listOf(dtcCode) + activeDtcs)
            .map { it.trim().uppercase() }
            .filter(String::isNotBlank)
            .distinct()
    }
    val repairKnowledgeState by rememberRepairKnowledgeUiState(
        vehicle = selectedVehicle,
        dtcs = dtcsForKnowledge
    )
    val groundedContextBuilder = remember { ProprietaryGroundedContextBuilder() }
    val groundedRepairContext = remember(repairKnowledgeState, selectedVehicle) {
        (repairKnowledgeState as? RepairKnowledgeUiState.Ready)?.let { ready ->
            groundedContextBuilder.build(
                bundle = ready.bundle,
                vehicle = selectedVehicle?.toActiveVehicleIdentity()
            )
        }
    }
    val combinedGroundedContext = remember(groundedRepairContext, initialGroundedContext) {
        listOfNotNull(
            initialGroundedContext?.takeIf(String::isNotBlank),
            groundedRepairContext?.takeIf(String::isNotBlank),
        ).joinToString("\n\n").takeIf(String::isNotBlank)
    }
    val hypothesisEngine = remember { DiagnosticHypothesisEngine() }
    var completedLocalTests by remember(dtcCode) { mutableStateOf<Set<String>>(emptySet()) }
    val localDecision = remember(
        dtcCode,
        activeDtcs,
        telemetrySamples,
        freezeFrameData,
        selectedVehicle,
        completedLocalTests
    ) {
        val selectedCode = dtcCode.trim().uppercase().takeIf { it.isNotBlank() }
        val dtcsForCase = (listOfNotNull(selectedCode) + activeDtcs)
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .distinct()
        val vehicleLabel = selectedVehicle?.let { vehicle ->
            listOf(vehicle.make, vehicle.model, vehicle.year.toString(), vehicle.engine)
                .filter { it.isNotBlank() && it != "0" }
                .joinToString(" ")
        }
        val hasRealObd = telemetrySamples.values.any { it.hasRealValue }
        hypothesisEngine.analyze(
            DiagnosticReasoningInput(
                vehicleId = selectedVehicle?.id,
                vehicleLabel = vehicleLabel,
                primaryComplaint = selectedCode?.let { "Analisis tecnico para $it" },
                dtcCodes = dtcsForCase,
                obdConnected = hasRealObd || dtcsForCase.any { it in activeDtcs },
                freezeFrame = freezeFrameData,
                livePids = telemetrySamples,
                completedTests = completedLocalTests.toList()
            )
        )
    }
    val localReasoning = localDecision.result
    val hasLocalCase = localReasoning.case.hypotheses.isNotEmpty()

    val severity = remember(dtcCode) {
        when {
            dtcCode.startsWith("P03") || dtcCode.startsWith("P02") || dtcCode.startsWith("P00") -> "CRITICAL"
            dtcCode.startsWith("P01") || dtcCode.startsWith("P07") -> "HIGH"
            dtcCode.startsWith("P04") || dtcCode.startsWith("P05") -> "MODERATE"
            dtcCode.isEmpty() -> "INFORMATIVE"
            else -> "HIGH"
        }
    }

    val severityScore = when (severity) {
        "CRITICAL" -> 0.95f
        "HIGH" -> 0.75f
        "MODERATE" -> 0.50f
        else -> 0.25f
    }

    LaunchedEffect(dtcCode, repairKnowledgeState, initialGroundedContext) {
        if (
            (dtcCode.isNotEmpty() || !initialGroundedContext.isNullOrBlank()) &&
            repairKnowledgeState !is RepairKnowledgeUiState.Loading
        ) {
            isLoading = true
            // Use null for apiKey/baseUrl so consultAi falls through to global config
            aiResponse = viewModel.consultAi(
                null,
                null,
                listOf(dtcCode),
                provider.takeIf { it != "minimax" },
                modelName.takeIf { it.isNotBlank() },
                combinedGroundedContext
            )
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            EliteTopAppBar(
                title = "Elysium AI Diagnostics",
                onBackClick = onBack,
                backgroundColor = MeetColors.backgroundDark,
                actions = {
                    EliteIconButton(
                        icon = { AnimatedNeonIcon(Icons.Default.Settings, contentDescription = "Configuración", tint = MeetColors.electricBlue) },
                        onClick = onNavigateToSettings
                    )
                }
            )
        },
        containerColor = MeetColors.backgroundDark
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MeetColors.carbonGradient)
                .padding(horizontal = 16.dp)
        ) {
            // AI Config is now managed globally via AiSettingsScreen
            // Tap the gear icon in the top bar to navigate there

            val scrollState = rememberScrollState()

            Box(modifier = Modifier.weight(1f)) {
                EliteScrollContainer(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .eliteScrollbar(scrollState)
                            .verticalScroll(scrollState)
                            .padding(vertical = 12.dp)
                    ) {
                        if (isLoading) {
                            Spacer(modifier = Modifier.height(24.dp))
                            CyberConsoleProgressStepper()
                        } else {
                            if (dtcCode.isNotEmpty()) {
                                // Gauge & Severity Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UrgencyGauge(
                                        severity = severity,
                                        score = severityScore,
                                        dtcCode = dtcCode,
                                        modifier = Modifier.size(130.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "CÓDIGO DE FALLA ACTIVADO",
                                            color = MeetColors.textSecondary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            dtcCode,
                                            color = Color.White,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        val severityLabel = when (severity) {
                                            "CRITICAL" -> "CRÍTICO - DETENER CONDUCCIÓN"
                                            "HIGH" -> "ALTO - REVISIÓN RECOMENDADA"
                                            "MODERATE" -> "MODERADO - ANÁLISIS PREVENTIVO"
                                            else -> "INFORMATIVO - NOMINAL"
                                        }
                                        val severityColor = when (severity) {
                                            "CRITICAL" -> MeetColors.error
                                            "HIGH" -> Color(0xFFFFB300)
                                            "MODERATE" -> MeetColors.electricBlue
                                            else -> MeetColors.neonGreen
                                        }

                                        Text(
                                            severityLabel,
                                            color = severityColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }

                                // Anomalous PIDs (Suspected Components) Section
                                if (anomalousPids.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "COMPONENTES BAJO SOSPECHA (TELEMETRÍA)",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        anomalousPids.forEach { anomaly ->
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color(0xFF0F0E13))
                                                    .border(1.dp, MeetColors.error.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                                    .padding(12.dp)
                                            ) {
                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        AnimatedNeonIcon(Icons.Default.Info, contentDescription = null, tint = MeetColors.error, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("PID ${anomaly.pid}", color = MeetColors.error, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(anomaly.insight, color = Color.White, fontSize = 10.sp, lineHeight = 14.sp)
                                                }
                                            }
                                        }
                                    }
                                }

                                if (hasLocalCase) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    RepairKnowledgeEvidencePanel(
                                        state = repairKnowledgeState,
                                        accentColor = MeetColors.cyberCyan
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    LocalDiagnosticReasoningPanel(
                                        result = localReasoning,
                                        diagnosisMode = localDecision.mode,
                                        completedTests = completedLocalTests,
                                        onToggleTest = { testId ->
                                            completedLocalTests = if (testId in completedLocalTests) {
                                                completedLocalTests - testId
                                            } else {
                                                completedLocalTests + testId
                                            }
                                        },
                                        onRequestMechanic = onRequestMechanic,
                                        onRequestPart = onRequestPart,
                                        onOpenComponent3d = onOpenComponent3d
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                            }

                            // AI Response Box
                            EliteCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "VEREDICTO CLÍNICO DE IA", 
                                            color = Color.White, 
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            letterSpacing = 0.5.sp
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MeetColors.electricBlue.copy(alpha = 0.2f))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                provider.uppercase(),
                                                color = MeetColors.electricBlue,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    if (provider == "minimax") {
                                        Text(
                                            "💡 Usando motor de respaldo integrado (MiniMax) debido a que no has configurado tu propia API Key.",
                                            color = MeetColors.neonGreen,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    } else if (aiResponse != null) {
                                        Text(
                                            text = aiResponse.orEmpty(),
                                            color = MeetColors.textPrimary,
                                            fontSize = 14.sp,
                                            lineHeight = 22.sp
                                        )
                                    } else if (dtcCode.isEmpty()) {
                                        Text(
                                            "Ningún código DTC proporcionado para análisis profundo. Ve a la sección de fallas en vivo y selecciona un código para iniciar el análisis.",
                                            color = MeetColors.textSecondary,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // PDF Export button
            Button(
                onClick = { viewModel.generateFullReport(aiResponse ?: localReasoning.reportSummary) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .border(
                        1.dp,
                        if (aiResponse != null || hasLocalCase) MeetColors.neonGreen else MeetColors.textSecondary.copy(alpha = 0.3f),
                        RoundedCornerShape(12.dp)
                    ),
                shape = RoundedCornerShape(12.dp),
                enabled = aiResponse != null || hasLocalCase
            ) {
                Text(
                    "EXPORTAR REPORTE IA (PDF) 📄",
                    color = if (aiResponse != null || hasLocalCase) MeetColors.neonGreen else MeetColors.textSecondary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LocalDiagnosticReasoningPanel(
    result: DiagnosticReasoningResult,
    diagnosisMode: GuidedDiagnosisMode,
    completedTests: Set<String>,
    onToggleTest: (String) -> Unit,
    onRequestMechanic: (String) -> Unit,
    onRequestPart: (String) -> Unit,
    onOpenComponent3d: () -> Unit
) {
    EliteCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "MOTOR LOCAL VANGUARD",
                        color = MeetColors.neonGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        if (diagnosisMode == GuidedDiagnosisMode.CALIBRATED) "MODO CALIBRADO" else "MODO HEURÍSTICO · SIN PROBABILIDADES",
                        color = MeetColors.electricBlue,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        result.case.dtcCodes.joinToString().ifBlank { "Caso por sintomas" },
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MeetColors.electricBlue.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        result.confidenceScore.band.label.uppercase(),
                        color = MeetColors.electricBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Siguiente accion: ${result.nextBestAction}",
                color = MeetColors.textPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(14.dp))
            SectionLabel("HIPOTESIS PRIORIZADAS")
            result.case.hypotheses.take(4).forEach { hypothesis ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.045f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                hypothesisPriorityLabel(hypothesis.heuristicPriorityScore),
                                color = hypothesisPriorityColor(hypothesis.heuristicPriorityScore),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.width(70.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    hypothesis.title,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 17.sp
                                )
                                Text(
                                    hypothesis.reasoning,
                                    color = MeetColors.textSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                        if (hypothesis.contradictingEvidence.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Evidencia en contra: ${hypothesis.contradictingEvidence.joinToString()}",
                                color = MeetColors.warning,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                        if (hypothesis.relatedComponents3d.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onOpenComponent3d,
                                modifier = Modifier.height(34.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.electricBlue)
                            ) {
                                Text("Abrir pieza 3D", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionLabel("PRUEBAS RECOMENDADAS")
            result.case.recommendedTests.take(6).forEach { test ->
                val done = test.id in completedTests || test.status == TestStatus.PASSED
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.035f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(test.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${test.toolRequired} · Esperado: ${test.expectedResult}",
                            color = MeetColors.textSecondary,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onToggleTest(test.id) },
                        modifier = Modifier.height(34.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (done) MeetColors.neonGreen else MeetColors.electricBlue
                        )
                    ) {
                        Text(
                            if (done) "Agregada" else "Agregar",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            if (result.missingData.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                SectionLabel("DATOS FALTANTES CRITICOS")
                Text(
                    result.missingData.take(8).joinToString(separator = "\n") { "- $it" },
                    color = MeetColors.warning,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionLabel("ARBOL CAUSAL")
            result.causalTree.children.take(3).forEach { system ->
                Text(
                    "• ${result.causalTree.title} > ${system.title}",
                    color = MeetColors.textPrimary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                system.children.take(3).forEach { hypothesis ->
                    Text(
                        "   - ${hypothesis.probabilityPercent?.let { "$it% CALIBRADO" } ?: "PRIORIDAD CUALITATIVA"} ${hypothesis.title}",
                        color = MeetColors.textSecondary,
                        fontSize = 10.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            if (result.serviceRecommendations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                SectionLabel("SERVICIOS SUGERIDOS")
                result.serviceRecommendations.take(3).forEach { service ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(service.serviceName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(service.reason, color = MeetColors.textSecondary, fontSize = 10.sp, lineHeight = 14.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                onRequestMechanic(
                                    "${result.case.dtcCodes.joinToString()} | ${service.serviceName} | ${result.nextBestAction}"
                                )
                            },
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Pedir mecanico", fontSize = 10.sp)
                        }
                    }
                }
            }

            if (result.partRecommendations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                SectionLabel("REPUESTOS POSIBLES")
                result.partRecommendations.take(5).forEach { part ->
                    val blocked = part.state == PartRecommendationState.DO_NOT_BUY_YET
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.035f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(part.partName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                part.state.name.replace("_", " ") + " · " + part.requiredEvidence.firstOrNull().orEmpty(),
                                color = partStateColor(part.state),
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = {
                                onRequestPart(
                                    "${result.case.dtcCodes.joinToString()} | ${part.partName} | ${part.state.name}"
                                )
                            },
                            enabled = !blocked,
                            modifier = Modifier.height(34.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen)
                        ) {
                            Text("Pedir repuesto", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            if (result.warnings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    result.warnings.joinToString(separator = "\n") { "- $it" },
                    color = MeetColors.warning,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = MeetColors.electricBlue,
        fontWeight = FontWeight.Black,
        fontSize = 10.sp,
        letterSpacing = 1.sp
    )
}

private fun hypothesisPriorityLabel(score: Int): String = when {
    score >= 65 -> "ALTA"
    score >= 35 -> "MEDIA"
    else -> "BAJA"
}

private fun hypothesisPriorityColor(score: Int): Color = when {
    score >= 65 -> MeetColors.warning
    score >= 35 -> MeetColors.electricBlue
    else -> MeetColors.textSecondary
}

private fun partStateColor(state: PartRecommendationState): Color = when (state) {
    PartRecommendationState.CONFIRMED_NEEDED -> MeetColors.neonGreen
    PartRecommendationState.MAY_BE_NEEDED -> MeetColors.electricBlue
    PartRecommendationState.INVESTIGATE_ONLY -> MeetColors.warning
    PartRecommendationState.DO_NOT_BUY_YET -> MeetColors.error
}

@Composable
fun UrgencyGauge(
    severity: String,
    score: Float,
    dtcCode: String,
    modifier: Modifier = Modifier
) {
    val sweepAngle = 240f
    val startAngle = 150f

    val strokeColor = when (severity) {
        "CRITICAL" -> MeetColors.error
        "HIGH" -> Color(0xFFFFB300)
        "MODERATE" -> MeetColors.electricBlue
        else -> MeetColors.neonGreen
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val strokeWidth = 10.dp.toPx()
            val arcSize = Size(width - strokeWidth * 2, height - strokeWidth * 2)

            // Draw track
            drawArc(
                color = Color.White.copy(alpha = 0.05f),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(strokeWidth, strokeWidth),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Draw filled arc
            drawArc(
                color = strokeColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle * score,
                useCenter = false,
                topLeft = Offset(strokeWidth, strokeWidth),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Inside text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = dtcCode,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp
            )
            Text(
                text = "GRAVEDAD",
                color = MeetColors.textSecondary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun CyberConsoleProgressStepper() {
    var stepIndex by remember { mutableIntStateOf(0) }
    
    val steps = listOf(
        "[01/04] Sincronizando datos de DTC y Freeze Frame...",
        "[02/04] Analizando correlación con PIDs en vivo...",
        "[03/04] Consultando base de datos de causas comunes...",
        "[04/04] Formateando veredicto clínico..."
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            if (stepIndex < steps.size - 1) {
                stepIndex++
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF040A12))
            .border(1.dp, MeetColors.electricBlue.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MeetColors.electricBlue
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SISTEMA DE ANÁLISIS DE FALLAS ELYSIUM ACTIVE",
                color = MeetColors.electricBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        for (i in 0..stepIndex) {
            val isCurrent = i == stepIndex
            Text(
                text = if (isCurrent) "${steps[i]} ▮" else "${steps[i]} ✓",
                color = if (isCurrent) MeetColors.neonGreen else Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}
