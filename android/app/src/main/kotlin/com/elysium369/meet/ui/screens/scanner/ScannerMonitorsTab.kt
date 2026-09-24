package com.elysium369.meet.ui.screens.scanner

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.core.emissions.analysis.*
import com.elysium369.meet.core.emissions.domain.*
import com.elysium369.meet.core.emissions.physics.*
import com.elysium369.meet.core.emissions.physics.CO2Outputs
import com.elysium369.meet.core.emissions.preitv.*
import com.elysium369.meet.core.emissions.regulations.RegulatoryVehicleProfile
import com.elysium369.meet.core.obd.DecodeStatus
import com.elysium369.meet.core.obd.DiagnosticSeverity
import com.elysium369.meet.core.obd.Mode06TestResult
import com.elysium369.meet.core.obd.Mode06Verdict
import com.elysium369.meet.core.obd.O2SensorTestResult
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.core.obd.ReadinessResult
import com.elysium369.meet.ui.ObdViewModel
import kotlinx.coroutines.delay
import com.elysium369.meet.ui.components.AnimatedNeonGlyph
import com.elysium369.meet.ui.components.AnimatedNeonIcon
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.launch

enum class MonitorsSubTab {
    ECU_MONITORS,
    EMISSIONS_LAB
}

@Composable
fun ScannerMonitorsTab(
    viewModel: ObdViewModel,
    isSpanish: Boolean,
    snackbarHostState: SnackbarHostState? = null,
    navController: NavController? = null
) {
    val mode06Results by viewModel.mode06Results.collectAsState()
    val isReading by viewModel.isReadingMode06.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = connectionState == ObdState.CONNECTED
    val liveData by viewModel.liveData.collectAsState()
    val telemetrySamples by viewModel.telemetrySamples.collectAsState()
    val readinessMonitors by viewModel.readinessMonitors.collectAsState()
    val o2SensorTests by viewModel.o2SensorTests.collectAsState()
    val isReadingO2Tests by viewModel.isReadingO2Tests.collectAsState()
    val activeDtcs by viewModel.activeDtcs.collectAsState()
    val detectedProtocol by viewModel.detectedProtocol.collectAsState()
    val qosMetrics by viewModel.qosMetrics.collectAsState()

    var activeSubTab by remember { mutableStateOf(MonitorsSubTab.ECU_MONITORS) }
    var selectedCategory by remember { mutableStateOf("ALL") }

    // Pre-ITV State Machine instance
    val preItvMachine = remember { PreItvStateMachine() }
    val preItvPhase by preItvMachine.phase.collectAsState()

    // Sliding waveform history for O2 sensors (up to 40 samples)
    val o2UpstreamSamples = remember { mutableStateListOf<OxygenSample>() }
    val o2DownstreamSamples = remember { mutableStateListOf<OxygenSample>() }
    var lastO2S1Monotonic by remember { mutableStateOf(0L) }
    var lastO2S2Monotonic by remember { mutableStateOf(0L) }

    LaunchedEffect(isConnected) {
        if (!isConnected) {
            o2UpstreamSamples.clear()
            o2DownstreamSamples.clear()
            lastO2S1Monotonic = 0L
            lastO2S2Monotonic = 0L
        }
    }

    // Only add O2 samples when a fresh physical frame arrives from the hardware
    LaunchedEffect(telemetrySamples) {
        val s1Sample = telemetrySamples["0114"] ?: telemetrySamples["14"]
        if (s1Sample != null && s1Sample.timestampMonotonicMs > lastO2S1Monotonic) {
            lastO2S1Monotonic = s1Sample.timestampMonotonicMs
            val voltage = s1Sample.value
            if (voltage != null) {
                o2UpstreamSamples.add(OxygenSample(System.currentTimeMillis(), voltage))
                if (o2UpstreamSamples.size > 40) o2UpstreamSamples.removeAt(0)
            }
        }

        val s2Sample = telemetrySamples["0115"] ?: telemetrySamples["15"]
        if (s2Sample != null && s2Sample.timestampMonotonicMs > lastO2S2Monotonic) {
            lastO2S2Monotonic = s2Sample.timestampMonotonicMs
            val voltage = s2Sample.value
            if (voltage != null) {
                o2DownstreamSamples.add(OxygenSample(System.currentTimeMillis(), voltage))
                if (o2DownstreamSamples.size > 40) o2DownstreamSamples.removeAt(0)
            }
        }
    }

    // Update telemetry into state machine on each live tick
    LaunchedEffect(liveData, readinessMonitors, mode06Results, isConnected) {
        val rpm = (liveData["010C"] ?: liveData["RPM"])?.toDouble()
        val ect = (liveData["0105"] ?: liveData["COOLANT"])?.toDouble()
        val speed = (liveData["010D"] ?: liveData["SPEED"])?.toDouble()
        val stft = (liveData["0106"] ?: liveData["STFT1"] ?: liveData["06"])?.toDouble()
        val ltft = (liveData["0107"] ?: liveData["LTFT1"] ?: liveData["07"])?.toDouble()
        val lambda = (liveData["LAMBDA"] ?: liveData["0124"] ?: liveData["0134"])?.toDouble()

        preItvMachine.setConnectionState(isConnected)
        preItvMachine.setMode06Evidence(mode06Results)
        preItvMachine.setReadinessEvidence(readinessMonitors)

        preItvMachine.tickTelemetry(
            rpm = rpm,
            ectC = ect,
            speedKmh = speed,
            stftPct = stft,
            ltftPct = ltft,
            lambda = lambda,
            transmissionConfirmedParkOrNeutral = true
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeetColors.backgroundDark)
    ) {
        // Sub-Tab Switcher
        SubTabSelector(
            activeTab = activeSubTab,
            onTabSelected = { activeSubTab = it },
            isSpanish = isSpanish
        )

        when (activeSubTab) {
            MonitorsSubTab.ECU_MONITORS -> {
                EcuMonitorsView(
                    viewModel = viewModel,
                    mode06Results = mode06Results,
                    isReading = isReading,
                    isConnected = isConnected,
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it },
                    isSpanish = isSpanish,
                    snackbarHostState = snackbarHostState,
                    navController = navController
                )
            }
            MonitorsSubTab.EMISSIONS_LAB -> {
                EmissionsLabView(
                    viewModel = viewModel,
                    liveData = liveData,
                    detectedProtocol = detectedProtocol,
                    qosMetrics = qosMetrics,
                    isConnected = isConnected,
                    mode06Results = mode06Results,
                    readinessResult = readinessMonitors,
                    o2SensorTests = o2SensorTests,
                    isReadingO2Tests = isReadingO2Tests,
                    activeDtcs = activeDtcs,
                    preItvMachine = preItvMachine,
                    preItvPhase = preItvPhase,
                    o2UpstreamSamples = o2UpstreamSamples,
                    o2DownstreamSamples = o2DownstreamSamples,
                    isSpanish = isSpanish
                )
            }
        }
    }
}

@Composable
private fun SubTabSelector(
    activeTab: MonitorsSubTab,
    onTabSelected: (MonitorsSubTab) -> Unit,
    isSpanish: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(MeetColors.backgroundDeep, RoundedCornerShape(12.dp))
            .border(1.dp, MeetColors.borderBlue.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        val tab1Selected = activeTab == MonitorsSubTab.ECU_MONITORS
        val tab2Selected = activeTab == MonitorsSubTab.EMISSIONS_LAB

        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    if (tab1Selected) MeetColors.cyberCyan.copy(alpha = 0.2f) else Color.Transparent,
                    RoundedCornerShape(8.dp)
                )
                .clickable { onTabSelected(MonitorsSubTab.ECU_MONITORS) }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (isSpanish) "MONITORES ECU (MODE $06)" else "ECU MONITORS (MODE $06)",
                color = if (tab1Selected) Color.White else MeetColors.textSecondary,
                fontWeight = if (tab1Selected) FontWeight.Black else FontWeight.Bold,
                fontSize = 11.sp
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    if (tab2Selected) MeetColors.neonGreen.copy(alpha = 0.2f) else Color.Transparent,
                    RoundedCornerShape(8.dp)
                )
                .clickable { onTabSelected(MonitorsSubTab.EMISSIONS_LAB) }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (isSpanish) "LAB EMISIONES & PRE-ITV" else "EMISSIONS LAB & PRE-ITV",
                color = if (tab2Selected) MeetColors.neonGreen else MeetColors.textSecondary,
                fontWeight = if (tab2Selected) FontWeight.Black else FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// TAB 1: ECU MONITORS (MODE $06) VIEW
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun EcuMonitorsView(
    viewModel: ObdViewModel,
    mode06Results: List<Mode06TestResult>,
    isReading: Boolean,
    isConnected: Boolean,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    isSpanish: Boolean,
    snackbarHostState: SnackbarHostState?,
    navController: NavController?
) {
    val scope = rememberCoroutineScope()

    HeaderSection(
        isConnected = isConnected,
        isReading = isReading,
        isSpanish = isSpanish,
        onRead = { viewModel.readMode06() },
        onNotConnectedClick = {
            scope.launch {
                val result = snackbarHostState?.showSnackbar(
                    message = if (isSpanish) "OBD Desconectado. Conéctate a tu adaptador primero." else "OBD Disconnected. Connect your adapter first.",
                    actionLabel = if (isSpanish) "CONECTAR" else "CONNECT",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    navController?.navigate("connect")
                }
            }
        }
    )

    if (mode06Results.isNotEmpty()) {
        HonestHealthSummary(mode06Results, isSpanish)
        Spacer(modifier = Modifier.height(8.dp))
        CategoryFilterRow(mode06Results, selectedCategory, onCategorySelected, isSpanish)
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (mode06Results.isEmpty() && !isReading) {
        EmptyMonitorsState(isConnected, isSpanish)
    } else {
        val filtered = if (selectedCategory == "ALL") {
            mode06Results
        } else {
            mode06Results.filter { matchCategory(it.mid, selectedCategory) }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(filtered) { result ->
                ExpertMonitorCard(result, isSpanish)
            }
        }
    }
}

private fun matchCategory(mid: String, category: String): Boolean {
    val upper = mid.uppercase()
    return when (category) {
        "O2" -> upper.startsWith("\$0") && upper <= "\$0C"
        "CATALYST" -> upper in listOf("\$21", "\$22", "\$23", "\$24")
        "MISFIRE" -> upper.startsWith("\$A")
        "EVAP" -> upper.startsWith("\$35") || upper.startsWith("\$36") || upper.startsWith("\$39") || upper.startsWith("\$3A") || upper.startsWith("\$3B")
        "FUEL" -> upper.startsWith("\$5")
        "VVT_EGR" -> upper in listOf("\$31", "\$32", "\$91", "\$92")
        else -> true
    }
}

@Composable
private fun HonestHealthSummary(results: List<Mode06TestResult>, isSpanish: Boolean) {
    val passCount = results.count { it.verdict == Mode06Verdict.PASS }
    val failCount = results.count { it.verdict == Mode06Verdict.FAIL }
    val unknownCount = results.count { it.verdict == Mode06Verdict.UNKNOWN || it.decodeStatus != DecodeStatus.DECODED }
    val nearLimitCount = results.count { it.passed && it.severity == DiagnosticSeverity.MODERATE }

    EliteCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        backgroundColor = MeetColors.backgroundDeep,
        borderColor = if (failCount > 0) MeetColors.error.copy(alpha = 0.4f) else MeetColors.neonGreen.copy(alpha = 0.4f),
        glowColor = if (failCount > 0) MeetColors.error.copy(alpha = 0.15f) else MeetColors.neonGreen.copy(alpha = 0.15f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatPill("PASS", passCount.toString(), MeetColors.neonGreen)
            StatPill("FAIL", failCount.toString(), if (failCount > 0) MeetColors.error else MeetColors.textMuted)
            StatPill("LÍMITE PRÓXIMO", nearLimitCount.toString(), if (nearLimitCount > 0) MeetColors.warning else MeetColors.textMuted)
            StatPill("INDETERMINADO", unknownCount.toString(), if (unknownCount > 0) Color(0xFFB0B0B0) else MeetColors.textMuted)
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(label, color = color.copy(alpha = 0.8f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CategoryFilterRow(
    results: List<Mode06TestResult>,
    selected: String,
    onSelect: (String) -> Unit,
    isSpanish: Boolean
) {
    val categories = listOf(
        "ALL" to (if (isSpanish) "Todos" else "All"),
        "O2" to "Sensores O₂",
        "CATALYST" to (if (isSpanish) "Catalizador" else "Catalyst"),
        "MISFIRE" to "Misfires",
        "EVAP" to "EVAP",
        "FUEL" to (if (isSpanish) "Combustible" else "Fuel"),
        "VVT_EGR" to "EGR / VVT"
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { (id, label) ->
            val isSel = selected == id
            Box(
                modifier = Modifier
                    .background(
                        if (isSel) MeetColors.cyberCyan.copy(alpha = 0.25f) else MeetColors.backgroundDeep,
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        1.dp,
                        if (isSel) MeetColors.cyberCyan else MeetColors.borderBlue.copy(alpha = 0.3f),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelect(id) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    label,
                    color = if (isSel) Color.White else MeetColors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isSel) FontWeight.Black else FontWeight.Normal
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// TAB 2: EMISSIONS LAB & PRE-ITV VIEW
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun EmissionsLabView(
    viewModel: ObdViewModel,
    liveData: Map<String, Float>,
    detectedProtocol: String,
    qosMetrics: com.elysium369.meet.core.obd.QosMetrics,
    isConnected: Boolean,
    mode06Results: List<Mode06TestResult>,
    readinessResult: ReadinessResult?,
    o2SensorTests: List<O2SensorTestResult>,
    isReadingO2Tests: Boolean,
    activeDtcs: List<String>,
    preItvMachine: PreItvStateMachine,
    preItvPhase: PreItvPhase,
    o2UpstreamSamples: List<OxygenSample>,
    o2DownstreamSamples: List<OxygenSample>,
    isSpanish: Boolean
) {
    val scope = rememberCoroutineScope()
    val o2Analyzer = remember { OxygenSignalAnalyzer() }
    val catalystAnalyzer = remember { CatalystEfficiencyAnalyzer() }
    val emissionsEngine = remember { EmissionsEngine() }
    val co2Model = remember { CO2PhysicsModel() }

    var isBurstActive by remember { mutableStateOf(false) }
    var burstSecondsLeft by remember { mutableStateOf(0) }

    fun triggerO2Burst() {
        if (isBurstActive) return
        scope.launch {
            isBurstActive = true
            burstSecondsLeft = 15
            viewModel.pinPid("0114")
            viewModel.setHighSpeedMode(true)
            while (burstSecondsLeft > 0) {
                delay(1000L)
                burstSecondsLeft--
            }
            viewModel.unpinPid("0114")
            viewModel.setHighSpeedMode(false)
            isBurstActive = false
        }
    }

    val rpm = (liveData["010C"] ?: liveData["RPM"])?.toDouble()
    val ect = (liveData["0105"] ?: liveData["COOLANT"])?.toDouble()
    val speed = (liveData["010D"] ?: liveData["SPEED"])?.toDouble()
    val stft = (liveData["0106"] ?: liveData["STFT1"] ?: liveData["06"])?.toDouble()
    val ltft = (liveData["0107"] ?: liveData["LTFT1"] ?: liveData["07"])?.toDouble()
    val lambda = (liveData["LAMBDA"] ?: liveData["0124"] ?: liveData["0134"])?.toDouble()
    val maf = (liveData["0110"] ?: liveData["MAF"])?.toDouble()

    // Real-time Waveform analysis
    val upstreamFeatures = remember(o2UpstreamSamples.size) {
        o2Analyzer.analyze(o2UpstreamSamples)
    }
    val downstreamFeatures = remember(o2DownstreamSamples.size) {
        o2Analyzer.analyze(o2DownstreamSamples)
    }

    // Catalyst Health Assessment
    val catAssessment = remember(upstreamFeatures, downstreamFeatures, mode06Results) {
        catalystAnalyzer.assess(
            upstreamFeatures = upstreamFeatures,
            downstreamFeatures = downstreamFeatures,
            mode06CatalystResults = mode06Results.filter { it.mid.uppercase() in listOf("\$21", "\$22") }
        )
    }

    // Authoritative Emissions & Virtual Gas Evaluation via unified EmissionsEngine
    val firstReadiness = readinessResult
    val misfireCount = mode06Results.count { it.mid.startsWith("\$A") && it.verdict == Mode06Verdict.FAIL }
    val isAcceleratedRpm = (rpm ?: 0.0) >= 2200.0

    val engineInput = EmissionsEngineInput(
        rpm = rpm,
        ectC = ect,
        stftPct = stft,
        ltftPct = ltft,
        lambda = lambda,
        misfireCount = misfireCount,
        o2UpstreamFeatures = upstreamFeatures,
        catalystAssessment = catAssessment,
        mode06Results = mode06Results,
        readinessResult = firstReadiness,
        isAcceleratedRpm = isAcceleratedRpm,
        isConnected = isConnected,
        physicalSampleCount = o2UpstreamSamples.size
    )

    val engineOutput = remember(engineInput) {
        emissionsEngine.evaluate(engineInput)
    }
    val assessment = engineOutput.combustionAssessment

    // CO2 mass flow from estimated fuel rate
    val co2Outputs = remember(maf, lambda, speed) {
        val fuelGps = if (maf != null && maf > 0.0) maf / (14.7 * (lambda ?: 1.0)) else null
        co2Model.calculate(fuelRateGps = fuelGps, speedKmh = speed, lambda = lambda)
    }

    val hasDtcConflict = isConnected && firstReadiness != null &&
        (firstReadiness.milOn || firstReadiness.dtcCount > 0) && activeDtcs.isEmpty()

    val isLegacyProtocol = isConnected && (
        !detectedProtocol.contains("CAN", ignoreCase = true) ||
        detectedProtocol.contains("9141", ignoreCase = true) ||
        detectedProtocol.contains("14230", ignoreCase = true) ||
        detectedProtocol.contains("J1850", ignoreCase = true)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Live Status Banner
        item {
            EmissionsLiveBanner(
                detectedProtocol = detectedProtocol,
                cmdsPerSec = qosMetrics.cmdsPerSecond,
                isConnected = isConnected,
                rpm = rpm,
                ect = ect,
                isSpanish = isSpanish
            )
        }

        // DTC State Conflict Banner (if MIL is ON but Mode 03 returned 0 codes)
        if (hasDtcConflict && firstReadiness != null) {
            item {
                DtcStateConflictCard(
                    readiness = firstReadiness,
                    activeDtcCount = activeDtcs.size,
                    isSpanish = isSpanish
                )
            }
        }

        // 2. Gas Estimate Cards (Virtual Gas Analyzer)
        item {
            Text(
                if (isSpanish) "ANALIZADOR VIRTUAL DE GASES" else "VIRTUAL GAS ANALYZER",
                color = MeetColors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GasCard(
                    title = "CO",
                    gasName = "Monóxido Carbono",
                    estimate = assessment.coEstimate,
                    badgeLabel = "MODEL ESTIMATED",
                    badgeColor = Color(0xFFFF9100),
                    isConnected = isConnected,
                    modifier = Modifier.weight(1f)
                )
                GasCard(
                    title = "HC",
                    gasName = "Hidrocarburos",
                    estimate = assessment.hcEstimate,
                    badgeLabel = "MODEL ESTIMATED",
                    badgeColor = Color(0xFFFF9100),
                    isConnected = isConnected,
                    modifier = Modifier.weight(1f)
                )
                GasCard(
                    title = "CO₂",
                    gasName = "Dióxido Carbono",
                    estimate = assessment.co2Estimate,
                    badgeLabel = "MODEL ESTIMATED",
                    badgeColor = Color(0xFF00E5FF),
                    isConnected = isConnected,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 3. Mass Balance CO2 Rate
        if (co2Outputs != null && isConnected) {
            item {
                Co2MassCard(co2Outputs = co2Outputs, isSpanish = isSpanish)
            }
        }

        // 4. Oxygen Sensor Oscilloscope
        item {
            OxygenOscilloscopeCard(
                upstreamSamples = o2UpstreamSamples,
                downstreamSamples = o2DownstreamSamples,
                upstreamFeatures = upstreamFeatures,
                downstreamFeatures = downstreamFeatures,
                catalystAssessment = catAssessment,
                stftPct = stft,
                ltftPct = ltft,
                lambda = lambda,
                isConnected = isConnected,
                isBurstActive = isBurstActive,
                burstSecondsLeft = burstSecondsLeft,
                onTriggerBurst = { triggerO2Burst() },
                isSpanish = isSpanish
            )
        }

        // Mode $05 Card for Legacy / Pre-CAN Protocols (ISO 9141-2 / K-Line)
        if (isLegacyProtocol) {
            item {
                Mode05O2Card(
                    viewModel = viewModel,
                    o2SensorTests = o2SensorTests,
                    isReading = isReadingO2Tests,
                    isSpanish = isSpanish
                )
            }
        }

        // 5. Catalyst Health & Fuel Trims Hub
        item {
            CatalystAndTrimsCard(
                catAssessment = catAssessment,
                stft = stft,
                ltft = ltft,
                combinedTrim = assessment.combinedTrimPct,
                isSpanish = isSpanish
            )
        }

        // 6. Pre-ITV Costa Rica Guided Wizard
        item {
            PreItvWizardSection(
                preItvMachine = preItvMachine,
                phase = preItvPhase,
                currentRpm = rpm ?: 0.0,
                isSpanish = isSpanish
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// EMISSIONS UI COMPONENTS
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun EmissionsLiveBanner(
    detectedProtocol: String,
    cmdsPerSec: Float,
    isConnected: Boolean,
    rpm: Double?,
    ect: Double?,
    isSpanish: Boolean
) {
    EliteCard(
        backgroundColor = MeetColors.backgroundDeep,
        borderColor = MeetColors.cyberCyan.copy(alpha = 0.4f),
        glowColor = MeetColors.cyberCyan.copy(alpha = 0.15f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (isConnected) MeetColors.neonGreen else MeetColors.error, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "ELYSIUM EMISSIONS LAB",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .background(MeetColors.cyberCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (detectedProtocol.isNotBlank()) detectedProtocol else "ISO 15765-4 / CAN",
                        color = MeetColors.cyberCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BannerMetric("RPM", rpm?.let { String.format("%.0f", it) } ?: "---", MeetColors.neonGreen)
                BannerMetric("TEMP MOTOR", ect?.let { "${it.toInt()} °C" } ?: "---", Color.White)
                BannerMetric("FRECUENCIA", String.format("%.1f Hz", cmdsPerSec), MeetColors.cyberCyan)
            }
        }
    }
}

@Composable
private fun BannerMetric(label: String, value: String, color: Color) {
    Column {
        Text(label, color = MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun DtcStateConflictCard(
    readiness: ReadinessResult,
    activeDtcCount: Int,
    isSpanish: Boolean
) {
    EliteCard(
        backgroundColor = MeetColors.backgroundDeep,
        borderColor = MeetColors.warning.copy(alpha = 0.6f),
        glowColor = MeetColors.warning.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MeetColors.warning,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    if (isSpanish) "CONFLICTO DE ESTADO DTC" else "DTC STATE CONFLICT",
                    color = MeetColors.warning,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    if (isSpanish)
                        "La ECU reporta MIL encendida o ${readiness.dtcCount} falla(s) en PID 01, pero Mode \$03 reportó 0 códigos confirmados. Estado no concluyente para ITV. Inspeccione códigos pendientes (Mode \$07) o permanentes (Mode \$0A)."
                    else
                        "ECU reports MIL on or ${readiness.dtcCount} DTC(s) in PID 01, but Mode \$03 returned 0 stored DTCs. Inconclusive for ITV. Check pending (Mode \$07) or permanent (Mode \$0A) codes.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun Mode05O2Card(
    viewModel: ObdViewModel,
    o2SensorTests: List<O2SensorTestResult>,
    isReading: Boolean,
    isSpanish: Boolean
) {
    EliteCard(
        backgroundColor = MeetColors.backgroundDeep,
        borderColor = MeetColors.cyberCyan.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (isSpanish) "PRUEBAS DE SENSORES O₂ (MODE \$05)" else "O₂ SENSOR TESTS (MODE \$05)",
                        color = MeetColors.cyberCyan,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                    Text(
                        if (isSpanish) "Estándar SAE J1979 para protocolos pre-CAN (ISO 9141-2 / K-Line)"
                        else "SAE J1979 standard for pre-CAN protocols (ISO 9141-2 / K-Line)",
                        color = MeetColors.textSecondary,
                        fontSize = 9.sp
                    )
                }

                Button(
                    onClick = { viewModel.readO2SensorTests() },
                    enabled = !isReading,
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    if (isReading) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = MeetColors.cyberCyan, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        if (isReading) "LEYENDO..." else "LEER MODE \$05",
                        color = MeetColors.cyberCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (o2SensorTests.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    o2SensorTests.forEach { test ->
                        val pass = test.passed
                        val statusColor = if (pass) MeetColors.neonGreen else MeetColors.error
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1.5f)) {
                                Text(test.testDescription, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("Sensor ${test.sensorId}", color = MeetColors.textSecondary, fontSize = 8.sp)
                            }
                            Text(
                                String.format("%.3f %s", test.value, test.unit),
                                color = statusColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(if (pass) "PASS" else "FAIL", color = statusColor, fontSize = 8.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GasCard(
    title: String,
    gasName: String,
    estimate: GasEstimate,
    badgeLabel: String,
    badgeColor: Color,
    isConnected: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isDisconnected = !isConnected || estimate.modelVersion == "DISCONNECTED"
    val displayColor = if (isDisconnected) Color.Gray else badgeColor

    EliteCard(
        modifier = modifier,
        backgroundColor = MeetColors.backgroundDeep,
        borderColor = displayColor.copy(alpha = 0.35f),
        glowColor = if (isDisconnected) Color.Transparent else displayColor.copy(alpha = 0.10f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Box(
                    modifier = Modifier
                        .background(
                            if (isDisconnected) Color(0xFF333333) else badgeColor.copy(alpha = 0.15f),
                            RoundedCornerShape(3.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (isDisconnected) "DESCONECTADO" else badgeLabel,
                        color = if (isDisconnected) Color(0xFFBBBBBB) else badgeColor,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (isDisconnected) "---" else String.format("%.2f %s", estimate.pointEstimate, estimate.unit),
                color = if (isDisconnected) MeetColors.textSecondary else Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                if (isDisconnected) "Sin señal física OBD" else "95%: ${String.format("%.2f", estimate.lower95)}–${String.format("%.2f", estimate.upper95)}",
                color = MeetColors.textSecondary,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun Co2MassCard(co2Outputs: CO2Outputs, isSpanish: Boolean) {
    EliteCard(
        backgroundColor = MeetColors.backgroundDeep,
        borderColor = Color(0xFF00E5FF).copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("CO₂ TASA MÁSICA (BALANCE DE CARBONO)", color = MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${String.format("%.1f", co2Outputs.massRateGph)} g/h" +
                        (co2Outputs.gramsPerKm?.let { " (${String.format("%.1f", it)} g/km)" } ?: ""),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
            }
            Box(
                modifier = Modifier
                    .background(Color(0xFF00E5FF).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("PHYSICS DERIVED", color = Color(0xFF00E5FF), fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OxygenOscilloscopeCard(
    upstreamSamples: List<OxygenSample>,
    downstreamSamples: List<OxygenSample>,
    upstreamFeatures: OxygenSignalFeatures,
    downstreamFeatures: OxygenSignalFeatures,
    catalystAssessment: CatalystAssessment? = null,
    stftPct: Double? = null,
    ltftPct: Double? = null,
    lambda: Double? = null,
    isConnected: Boolean = true,
    isBurstActive: Boolean = false,
    burstSecondsLeft: Int = 0,
    onTriggerBurst: () -> Unit = {},
    isSpanish: Boolean
) {
    val diagnostician = remember { WaveformAutoDiagnostician() }
    val diagnosis = remember(
        upstreamFeatures,
        downstreamFeatures,
        catalystAssessment,
        stftPct,
        ltftPct,
        lambda,
        isConnected
    ) {
        diagnostician.diagnose(
            upstreamFeatures = upstreamFeatures,
            downstreamFeatures = downstreamFeatures,
            catalystAssessment = catalystAssessment,
            stftPct = stftPct,
            ltftPct = ltftPct,
            lambda = lambda,
            isConnected = isConnected
        )
    }

    EliteCard(
        backgroundColor = MeetColors.backgroundDeep,
        borderColor = MeetColors.neonGreen.copy(alpha = 0.4f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isSpanish) "OSCILOSCOPIO SENSORES O₂" else "OXYGEN SENSORS OSCILLOSCOPE",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp
                )

                // Ráfaga O2 Button for ISO 9141-2 / K-Line
                Button(
                    onClick = onTriggerBurst,
                    enabled = !isBurstActive,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBurstActive) MeetColors.neonGreen else MeetColors.cyberCyan.copy(alpha = 0.2f),
                        disabledContainerColor = MeetColors.neonGreen.copy(alpha = 0.8f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    if (isBurstActive) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "RÁFAGA (${burstSecondsLeft}s)",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp
                        )
                    } else {
                        Icon(
                            Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = MeetColors.cyberCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "RÁFAGA O2 (15s)",
                            color = MeetColors.cyberCyan,
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            if (isBurstActive) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "⚡ Modo Ráfaga activo: Bus OBD concentrado exclusivamente en sensor O2 (PID 0114) a máxima velocidad.",
                    color = MeetColors.neonGreen,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LegendItem("B1S1 Upstream", MeetColors.neonGreen)
                    LegendItem("B1S2 Downstream", Color(0xFFFF9100))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Waveform Canvas (0.0V to 1.0V)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Color(0xFF0D0D0D), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
                    val w = size.width
                    val h = size.height

                    // 0.45V Stoichiometric midpoint reference line
                    val yMid = h * (1.0f - 0.45f)
                    drawLine(
                        color = Color.White.copy(alpha = 0.25f),
                        start = Offset(0f, yMid),
                        end = Offset(w, yMid),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Draw B1S1 (Upstream) Trace
                    if (upstreamSamples.size >= 2) {
                        val path = Path()
                        val dx = w / (upstreamSamples.size - 1)
                        for (i in upstreamSamples.indices) {
                            val v = upstreamSamples[i].voltage.toFloat().coerceIn(0f, 1f)
                            val x = i * dx
                            val y = h * (1.0f - v)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, color = Color(0xFF00FF7F), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                    }

                    // Draw B1S2 (Downstream) Trace
                    if (downstreamSamples.size >= 2) {
                        val path = Path()
                        val dx = w / (downstreamSamples.size - 1)
                        for (i in downstreamSamples.indices) {
                            val v = downstreamSamples[i].voltage.toFloat().coerceIn(0f, 1f)
                            val x = i * dx
                            val y = h * (1.0f - v)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, color = Color(0xFFFF9100), style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Switch: ${upstreamFeatures.switchHz?.let { String.format("%.2f Hz", it) } ?: "N/D"}",
                    color = MeetColors.neonGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Cruces: ${upstreamFeatures.crossCount}",
                    color = Color.White,
                    fontSize = 10.sp
                )
                Text(
                    "Amplitud: ${upstreamFeatures.amplitude?.let { String.format("%.2f V", it) } ?: "---"}",
                    color = Color.White,
                    fontSize = 10.sp
                )
            }

            if (upstreamFeatures.insufficientSampleRate) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "⚠️ Tasa de muestreo limitada por protocolo OBD (< 2.0 Hz). Use [RÁFAGA O2] para captura en alta velocidad.",
                    color = MeetColors.warning,
                    fontSize = 9.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF222222)))
            Spacer(modifier = Modifier.height(10.dp))

            // AUTO-DIAGNÓSTICO CLÍNICO Y OBSERVABILIDAD MATEMÁTICA
            WaveformClinicalDiagnosisSection(
                diagnosis = diagnosis,
                isSpanish = isSpanish
            )
        }
    }
}

@Composable
private fun WaveformClinicalDiagnosisSection(
    diagnosis: WaveformClinicalDiagnosis,
    isSpanish: Boolean
) {
    val badgeColor = Color(diagnosis.badgeColorHex)
    val healthIndex = diagnosis.metrics.healthIndexPct
    val healthColor = when {
        healthIndex >= 80 -> MeetColors.neonGreen
        healthIndex >= 50 -> MeetColors.warning
        else -> MeetColors.error
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF101010), RoundedCornerShape(10.dp))
            .border(1.dp, badgeColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        // 1. Header with Diagnosis Badge and Health Index
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .background(badgeColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .border(1.dp, badgeColor.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = diagnosis.title,
                    color = badgeColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Health Index Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(healthColor.copy(alpha = 0.15f), CircleShape)
                    .border(1.dp, healthColor.copy(alpha = 0.5f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isSpanish) "Salud O₂: $healthIndex%" else "O₂ Health: $healthIndex%",
                    color = healthColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. Plain-Spanish Clinical Summary
        Text(
            text = diagnosis.summary,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 15.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 3. Technical Physical Explanation
        Text(
            text = "🔬 ${diagnosis.technicalExplanation}",
            color = MeetColors.textSecondary,
            fontSize = 10.sp,
            lineHeight = 14.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 4. Observability Telemetry Matrix (Mathematical Ranges)
        Text(
            text = if (isSpanish) "TELEMETRÍA Y RANGOS FÍSICOS (OBSERVABILIDAD)" else "TELEMETRY & PHYSICAL RANGES",
            color = MeetColors.cyberCyan,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val vpp = diagnosis.metrics.peakToPeakVolts
            val vppColor = when {
                vpp >= 0.55 -> MeetColors.neonGreen
                vpp >= 0.25 -> MeetColors.warning
                else -> MeetColors.error
            }
            ObservabilityMetricPill(
                title = "Vpp (Amplitud)",
                value = String.format("%.2f V", vpp),
                range = "0.60V - 0.90V",
                accentColor = vppColor,
                modifier = Modifier.weight(1f)
            )

            val vBias = diagnosis.metrics.centerBiasVolts
            val biasColor = when {
                vBias in 0.38..0.52 -> MeetColors.neonGreen
                vBias in 0.30..0.60 -> MeetColors.warning
                else -> MeetColors.error
            }
            ObservabilityMetricPill(
                title = "Vbias (Centro)",
                value = String.format("%.2f V", vBias),
                range = "0.40V - 0.50V",
                accentColor = biasColor,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val rich = diagnosis.metrics.richDwellPct.toInt()
            val lean = diagnosis.metrics.leanDwellPct.toInt()
            val dwellColor = if (rich in 35..65) MeetColors.neonGreen else MeetColors.warning

            ObservabilityMetricPill(
                title = "Dwell Rico/Pobre",
                value = "$rich% / $lean%",
                range = "Equil: 40-60%",
                accentColor = dwellColor,
                modifier = Modifier.weight(1f)
            )

            val slew = diagnosis.metrics.slewRateVPerSec
            val slewColor = when {
                slew == null -> MeetColors.textMuted
                slew >= 2.0 -> MeetColors.neonGreen
                slew >= 1.0 -> MeetColors.warning
                else -> MeetColors.error
            }
            ObservabilityMetricPill(
                title = "Velocidad dV/dt",
                value = slew?.let { String.format("%.1f V/s", it) } ?: "N/D",
                range = "> 2.0 V/s",
                accentColor = slewColor,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 5. Impact on ITV / DEKRA
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A), RoundedCornerShape(6.dp))
                .padding(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text("⚖️", fontSize = 11.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = if (isSpanish) "Impacto en Inspección Técnica (ITV / DEKRA):" else "Impact on ITV / Technical Inspection:",
                    color = MeetColors.warning,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = diagnosis.impactOnItv,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 6. Actionable Mechanical Recommendation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF14201A), RoundedCornerShape(6.dp))
                .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                .padding(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text("🔧", fontSize = 11.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = if (isSpanish) "Acción de Reparación Sugerida:" else "Recommended Mechanical Repair:",
                    color = MeetColors.neonGreen,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = diagnosis.recommendedAction,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun ObservabilityMetricPill(
    title: String,
    value: String,
    range: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF181818), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF282828), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column {
            Text(
                text = title,
                color = MeetColors.textMuted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = value,
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = range,
                    color = Color.Gray,
                    fontSize = 8.sp
                )
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CatalystAndTrimsCard(
    catAssessment: CatalystAssessment,
    stft: Double?,
    ltft: Double?,
    combinedTrim: Double?,
    isSpanish: Boolean
) {
    EliteCard(
        backgroundColor = MeetColors.backgroundDeep,
        borderColor = MeetColors.borderBlue.copy(alpha = 0.3f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                if (isSpanish) "CATALIZADOR & CORRECCIÓN DE COMBUSTIBLE" else "CATALYST & FUEL TRIMS",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Estado del Catalizador",
                        color = MeetColors.textSecondary,
                        fontSize = 9.sp
                    )
                    val catStatusColor = when (catAssessment.state) {
                        CatalystAssessmentState.NORMAL_EVIDENCE -> MeetColors.neonGreen
                        CatalystAssessmentState.DEGRADED_EVIDENCE -> MeetColors.warning
                        CatalystAssessmentState.STRONG_DEGRADATION_EVIDENCE -> MeetColors.error
                        CatalystAssessmentState.INCONCLUSIVE -> Color.Gray
                    }
                    Text(
                        catAssessment.state.name.replace("_", " "),
                        color = catStatusColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                }

                catAssessment.isolationRatio?.let { ratio ->
                    Text(
                        "Ratio Aislamiento: ${String.format("%.2f", ratio)}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Fuel Trims Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TrimPill("STFT", stft)
                TrimPill("LTFT", ltft)
                TrimPill("COMBINADO", combinedTrim)
            }
        }
    }
}

@Composable
private fun TrimPill(label: String, value: Double?) {
    val color = when {
        value == null -> MeetColors.textSecondary
        value > 10.0 -> MeetColors.warning
        value < -10.0 -> MeetColors.error
        else -> MeetColors.neonGreen
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = MeetColors.textSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(
            value?.let { "${if (it >= 0) "+" else ""}${String.format("%.1f", it)}%" } ?: "---",
            color = color,
            fontWeight = FontWeight.Black,
            fontSize = 12.sp
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// PRE-ITV COSTA RICA WIZARD
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PreItvWizardSection(
    preItvMachine: PreItvStateMachine,
    phase: PreItvPhase,
    currentRpm: Double,
    isSpanish: Boolean
) {
    EliteCard(
        backgroundColor = MeetColors.backgroundDeep,
        borderColor = Color(0xFFFFD600).copy(alpha = 0.4f),
        glowColor = Color(0xFFFFD600).copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "PROTOCOLO PRE-ITV COSTA RICA",
                        color = Color(0xFFFFD600),
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                    Text(
                        "Normativa COSEVI / MOPT (Gasolina 4T)",
                        color = MeetColors.textSecondary,
                        fontSize = 9.sp
                    )
                }

                if (phase is PreItvPhase.Idle) {
                    Button(
                        onClick = { preItvMachine.start(RegulatoryVehicleProfile(modelYear = 2005)) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD600)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("INICIAR", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    }
                } else if (phase !is PreItvPhase.Completed) {
                    OutlinedButton(
                        onClick = { preItvMachine.abort("Cancelado por usuario") },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.error),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("ABORTAR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (phase) {
                PreItvPhase.Idle -> {
                    Text(
                        "Ejecuta una evaluación guiada de emisiones simulando el procedimiento oficial de inspección vehicular (COSEVI RTV): calentamiento, prueba en ralentí y medición a 2500 RPM.",
                        color = MeetColors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
                PreItvPhase.Preconditions -> {
                    PhaseStatusBanner("VERIFICANDO PRECONDICIONES", "Motor en marcha, temperatura operativa (>65°C) y transmisión en P/N...")
                }
                is PreItvPhase.CatalystWarmup -> {
                    PhaseStatusBanner(
                        "CALENTANDO CATALIZADOR (2500–3000 RPM)",
                        "Mantenga acelerado. Tiempo: ${phase.elapsedSeconds}s / ${phase.targetSeconds}s"
                    )
                    TachometerBar(currentRpm = currentRpm, targetLow = 2500.0, targetHigh = 3000.0)
                }
                is PreItvPhase.IdleStabilization -> {
                    PhaseStatusBanner(
                        "FASE 1: ESTABILIZANDO RALENTÍ",
                        "Suelte el acelerador. Estabilidad matemática: ${phase.progressPct}%"
                    )
                    LinearProgressIndicator(
                        progress = phase.progressPct / 100f,
                        color = MeetColors.neonGreen,
                        modifier = Modifier.fillMaxWidth().height(6.dp)
                    )
                }
                is PreItvPhase.IdleCapture -> {
                    PhaseStatusBanner(
                        "FASE 1: CAPTURANDO EN RALENTÍ",
                        "Mantenga ralentí estable. Restante: ${phase.remainingSeconds}s"
                    )
                }
                is PreItvPhase.AcceleratedStabilization -> {
                    PhaseStatusBanner(
                        "FASE 2: ACELERE Y MANTENGA 2500 RPM",
                        "Estabilizando analizador virtual de gases..."
                    )
                    TachometerBar(currentRpm = currentRpm, targetLow = 2400.0, targetHigh = 2600.0)
                }
                is PreItvPhase.AcceleratedCapture -> {
                    PhaseStatusBanner(
                        "FASE 2: CAPTURANDO A 2500 RPM",
                        "Mantenga RPM constantes. Restante: ${phase.remainingSeconds}s"
                    )
                    TachometerBar(currentRpm = currentRpm, targetLow = 2400.0, targetHigh = 2600.0)
                }
                PreItvPhase.Analysis -> {
                    PhaseStatusBanner("SINTETIZANDO EVALUACIÓN", "Contrastando con límites oficiales de Costa Rica...")
                }
                is PreItvPhase.Completed -> {
                    PreItvResultCard(result = phase.result, onReset = { preItvMachine.reset() }, isSpanish = isSpanish)
                }
                is PreItvPhase.Aborted -> {
                    Text("Prueba abortada: ${phase.reason}", color = MeetColors.error, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { preItvMachine.reset() }) { Text("REINICIAR") }
                }
            }
        }
    }
}

@Composable
private fun PhaseStatusBanner(title: String, subtitle: String) {
    Column {
        Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(subtitle, color = MeetColors.textSecondary, fontSize = 10.sp)
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun TachometerBar(currentRpm: Double, targetLow: Double, targetHigh: Double) {
    val inRange = currentRpm in targetLow..targetHigh
    val barColor = if (inRange) MeetColors.neonGreen else Color(0xFFFF9100)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("RPM Actual: ${currentRpm.toInt()}", color = barColor, fontWeight = FontWeight.Black, fontSize = 12.sp)
            Text("Objetivo: ${targetLow.toInt()}–${targetHigh.toInt()} RPM", color = MeetColors.textSecondary, fontSize = 10.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = (currentRpm / 4000.0).toFloat().coerceIn(0f, 1f),
            color = barColor,
            trackColor = Color(0xFF222222),
            modifier = Modifier.fillMaxWidth().height(8.dp)
        )
    }
}

@Composable
private fun PreItvResultCard(result: PreItvResult, onReset: () -> Unit, isSpanish: Boolean) {
    val verdictColor = when (result.overall) {
        PreItvVerdict.LOW_RISK -> MeetColors.neonGreen
        PreItvVerdict.ELEVATED_RISK -> MeetColors.warning
        PreItvVerdict.HIGH_RISK -> MeetColors.error
        PreItvVerdict.INCONCLUSIVE -> Color.Gray
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Verdict Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(verdictColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                .border(1.dp, verdictColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when (result.overall) {
                        PreItvVerdict.LOW_RISK -> "BAJO RIESGO DE REPROBAR ITV"
                        PreItvVerdict.ELEVATED_RISK -> "RIESGO MODERADO DE REPROBAR"
                        PreItvVerdict.HIGH_RISK -> "ALTO RIESGO DE REPROBAR ITV"
                        PreItvVerdict.INCONCLUSIVE -> "RESULTADO INCONCLUSO"
                    },
                    color = verdictColor,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
                Text(
                    "Confianza estimada del modelo: ${(result.confidence?.times(100))?.toInt() ?: 75}%",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Table Ralentí vs 2500 RPM
        Text("DETALLE DE EVALUACIÓN SEGÚN NORMATIVA COSEVI:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(6.dp))

        PhaseEvaluationRow("Ralentí CO", "${String.format("%.2f", result.idle.coEstimate.pointEstimate)}%", "<= 0.50%", result.idle.coEvaluation)
        PhaseEvaluationRow("Ralentí HC", "${result.idle.hcEstimate.pointEstimate.toInt()} ppm", "<= 125 ppm", result.idle.hcEvaluation)
        PhaseEvaluationRow("Ralentí CO₂", "${String.format("%.1f", result.idle.co2Estimate.pointEstimate)}%", ">= 10.0%", result.idle.co2Evaluation)

        Divider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 4.dp))

        PhaseEvaluationRow("2500 RPM CO", "${String.format("%.2f", result.accelerated.coEstimate.pointEstimate)}%", "<= 0.30%", result.accelerated.coEvaluation)
        PhaseEvaluationRow("2500 RPM HC", "${result.accelerated.hcEstimate.pointEstimate.toInt()} ppm", "<= 100 ppm", result.accelerated.hcEvaluation)
        PhaseEvaluationRow("2500 RPM CO₂", "${String.format("%.1f", result.accelerated.co2Estimate.pointEstimate)}%", ">= 12.0%", result.accelerated.co2Evaluation)

        if (result.accelerated.lambdaEstimate != null) {
            PhaseEvaluationRow("Lambda", String.format("%.3f", result.accelerated.lambdaEstimate), "1.00 ± 0.07", result.accelerated.lambdaEvaluation)
        }

        Divider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 4.dp))
        PhaseEvaluationRow("Monitores OBD", if (result.readiness == Evaluation.PASS) "Completos" else "Incompletos / MIL", "Cero fallas", result.readiness)
        val m06Pass = result.mode06Evidence.none { it.verdict == Mode06Verdict.FAIL }
        val m06Eval = if (result.mode06Evidence.isEmpty()) Evaluation.INCONCLUSIVE else if (m06Pass) Evaluation.PASS else Evaluation.FAIL
        PhaseEvaluationRow("Mode \$06", if (m06Pass) "Sin fallas" else "Fallas detectadas", "0 Fails", m06Eval)

        Spacer(modifier = Modifier.height(10.dp))

        // Causal Explanations
        if (result.causalExplanations.isNotEmpty()) {
            Text("EVIDENCIAS Y OBSERVACIONES DETECTADAS:", color = Color(0xFFFFD600), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            result.causalExplanations.forEach { exp ->
                Text("• $exp", color = Color.White.copy(alpha = 0.9f), fontSize = 10.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Regulatory Disclaimer
        Text(
            "DISCLAIMER LEGAL: Pre-Inspección informativa desarrollada por MEET. No afiliado ni certificado por DEKRA, MOPT o COSEVI. No sustituye una inspección técnica oficial en estación autorizada. Los valores de gases son estimaciones matemáticas.",
            color = MeetColors.textSecondary.copy(alpha = 0.7f),
            fontSize = 8.sp,
            lineHeight = 12.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("REINICIAR PRUEBA PRE-ITV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PhaseEvaluationRow(label: String, value: String, limit: String, evaluation: Evaluation) {
    val (statusText, statusColor) = when (evaluation) {
        Evaluation.PASS -> "PASS" to MeetColors.neonGreen
        Evaluation.FAIL -> "FAIL" to MeetColors.error
        Evaluation.INCONCLUSIVE -> "INC" to MeetColors.warning
        Evaluation.NOT_APPLICABLE -> "N/A" to Color.Gray
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MeetColors.textSecondary, fontSize = 10.sp, modifier = Modifier.weight(1.2f))
        Text(value, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("Lím: $limit", color = MeetColors.textSecondary, fontSize = 9.sp, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp)
        ) {
            Text(statusText, color = statusColor, fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// EXPERT MONITOR CARD (MODE $06)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun HeaderSection(
    isConnected: Boolean,
    isReading: Boolean,
    isSpanish: Boolean,
    onRead: () -> Unit,
    onNotConnectedClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (isSpanish) "Módulos No Continuos" else "Non-Continuous Monitors",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                if (isSpanish) "Diagnóstico de Ingeniería Avanzado (Mode $06)" else "Advanced Engineering Diagnostics (Mode $06)",
                color = MeetColors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        EliteCard(
            onClick = if (isReading) null else {
                if (isConnected) onRead else onNotConnectedClick
            },
            backgroundColor = if (isConnected) MeetColors.neonGreen.copy(alpha = 0.1f) else MeetColors.borderBlue.copy(alpha = 0.15f),
            borderColor = if (isConnected) MeetColors.neonGreen.copy(alpha = 0.5f) else MeetColors.textSecondary.copy(alpha = 0.3f),
            shape = RoundedCornerShape(12.dp),
            glowColor = if (isConnected) MeetColors.neonGreen.copy(alpha = 0.3f) else Color.Transparent
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isReading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MeetColors.neonGreen,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    if (isReading) {
                        if (isSpanish) "LEYENDO..." else "READING..."
                    } else {
                        if (isSpanish) "LEER MODE $06" else "READ MODE $06"
                    },
                    color = if (isConnected) MeetColors.neonGreen else MeetColors.textSecondary,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun ExpertMonitorCard(result: Mode06TestResult, isSpanish: Boolean) {
    val isDecoded = result.decodeStatus == DecodeStatus.DECODED
    val statusColor = when (result.verdict) {
        Mode06Verdict.PASS -> MeetColors.neonGreen
        Mode06Verdict.FAIL -> MeetColors.error
        Mode06Verdict.UNKNOWN -> Color(0xFFB0B0B0)
        Mode06Verdict.NOT_APPLICABLE -> MeetColors.textSecondary
    }

    val statusBadgeText = when (result.verdict) {
        Mode06Verdict.PASS -> "PASSED"
        Mode06Verdict.FAIL -> "FAILED"
        Mode06Verdict.UNKNOWN -> if (result.decodeStatus == DecodeStatus.NO_LIMITS) "SIN LÍMITES" else "UNKNOWN"
        Mode06Verdict.NOT_APPLICABLE -> "N/A"
    }

    EliteCard(
        backgroundColor = MeetColors.backgroundDeep,
        borderColor = statusColor.copy(alpha = if (result.passed) 0.2f else 0.5f),
        glowColor = if (!result.passed && result.verdict == Mode06Verdict.FAIL) statusColor.copy(alpha = 0.2f) else Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedNeonIcon(
                    imageVector = when (result.verdict) {
                        Mode06Verdict.PASS -> Icons.Default.CheckCircle
                        Mode06Verdict.FAIL -> Icons.Default.Warning
                        else -> Icons.Default.Info
                    },
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    result.componentName.uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        statusBadgeText,
                        color = statusColor,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                result.testName,
                color = MeetColors.textSecondary,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Values Table Layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ValueItem(
                    if (isSpanish) "VALOR LEÍDO" else "CURRENT VALUE",
                    if (isDecoded && result.valueDouble != null) String.format("%.3f %s", result.value, result.unit) else "---",
                    if (result.passed) Color.White else statusColor
                )
                ValueItem(
                    if (isSpanish) "LÍM. MIN" else "MIN LIMIT",
                    result.minLimit?.let { String.format("%.3f", it) } ?: "---",
                    MeetColors.textSecondary
                )
                ValueItem(
                    if (isSpanish) "LÍM. MAX" else "MAX LIMIT",
                    result.maxLimit?.let { String.format("%.3f", it) } ?: "---",
                    MeetColors.textSecondary
                )
            }

            if (isDecoded && (result.minLimit != null || result.maxLimit != null)) {
                Spacer(modifier = Modifier.height(14.dp))
                Mode06VisualRange(
                    value = result.value,
                    minLimit = result.minLimit,
                    maxLimit = result.maxLimit,
                    passed = result.passed,
                    unit = result.unit,
                    isSpanish = isSpanish
                )
            }

            if (!result.passed && result.proTip != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(statusColor.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .border(1.dp, statusColor.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimatedNeonIcon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isSpanish) "CONSEJO TÉCNICO EXPERTO" else "EXPERT ADVISE SERVICE",
                                color = statusColor,
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            result.proTip ?: "",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ValueItem(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = MeetColors.textSecondary.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun Mode06VisualRange(
    value: Float,
    minLimit: Float?,
    maxLimit: Float?,
    passed: Boolean,
    unit: String,
    isSpanish: Boolean
) {
    val statusColor = if (passed) MeetColors.neonGreen else MeetColors.error

    val marginObservability: String? = remember(value, minLimit, maxLimit, passed) {
        if (minLimit != null && maxLimit != null) {
            val range = maxLimit - minLimit
            if (range > 0f) {
                val distToMin = value - minLimit
                val distToMax = maxLimit - value
                val minDist = kotlin.math.min(distToMin, distToMax)
                val marginPct = (minDist / range * 100f).coerceIn(-999f, 100f)
                if (passed) {
                    if (marginPct >= 20f) {
                        if (isSpanish) "✅ Margen seguro: ${String.format("%.0f%%", marginPct)} dentro de tolerancia"
                        else "✅ Safe margin: ${String.format("%.0f%%", marginPct)} within tolerance"
                    } else {
                        if (isSpanish) "⚠️ Límite crítico: a solo ${String.format("%.0f%%", marginPct)} de reprobar"
                        else "⚠️ Critical limit: only ${String.format("%.0f%%", marginPct)} from rejection"
                    }
                } else {
                    if (isSpanish) "❌ Fuera de rango por ${String.format("%.3f", kotlin.math.abs(minDist))} $unit"
                    else "❌ Out of range by ${String.format("%.3f", kotlin.math.abs(minDist))} $unit"
                }
            } else null
        } else if (maxLimit != null) {
            val diff = maxLimit - value
            val marginPct = if (maxLimit != 0f) (diff / kotlin.math.abs(maxLimit) * 100f) else 0f
            if (passed) {
                if (marginPct >= 20f) {
                    if (isSpanish) "✅ Margen seguro: ${String.format("%.0f%%", marginPct)} bajo el máximo"
                    else "✅ Safe margin: ${String.format("%.0f%%", marginPct)} below max"
                } else {
                    if (isSpanish) "⚠️ Próximo al máximo: margen ${String.format("%.0f%%", marginPct)}"
                    else "⚠️ Near max limit: margin ${String.format("%.0f%%", marginPct)}"
                }
            } else {
                if (isSpanish) "❌ Supera límite máximo por +${String.format("%.3f", -diff)} $unit"
                else "❌ Exceeds max limit by +${String.format("%.3f", -diff)} $unit"
            }
        } else if (minLimit != null) {
            val diff = value - minLimit
            val marginPct = if (minLimit != 0f) (diff / kotlin.math.abs(minLimit) * 100f) else 0f
            if (passed) {
                if (marginPct >= 20f) {
                    if (isSpanish) "✅ Margen seguro: ${String.format("%.0f%%", marginPct)} sobre el mínimo"
                    else "✅ Safe margin: ${String.format("%.0f%%", marginPct)} above min"
                } else {
                    if (isSpanish) "⚠️ Próximo al mínimo: margen ${String.format("%.0f%%", marginPct)}"
                    else "⚠️ Near min limit: margin ${String.format("%.0f%%", marginPct)}"
                }
            } else {
                if (isSpanish) "❌ Por debajo del mínimo por -${String.format("%.3f", -diff)} $unit"
                else "❌ Below min limit by -${String.format("%.3f", -diff)} $unit"
            }
        } else null
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cy = h / 2f

                drawRoundRect(
                    color = Color(0xFF161616),
                    size = Size(w, 6.dp.toPx()),
                    topLeft = Offset(0f, cy - 3.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )

                if (minLimit != null && maxLimit != null) {
                    val min = minLimit
                    val max = maxLimit
                    val valF = value

                    val range = max - min
                    val pad = if (range > 0f) range * 0.15f else 1f
                    val displayMin = min - pad
                    val displayMax = max + pad
                    val displayRange = displayMax - displayMin

                    val minX = ((min - displayMin) / displayRange) * w
                    val maxX = ((max - displayMin) / displayRange) * w
                    val valX = (((valF - displayMin) / displayRange) * w).coerceIn(0f, w)

                    drawRect(
                        color = MeetColors.neonGreen.copy(alpha = 0.12f),
                        topLeft = Offset(minX, cy - 3.dp.toPx()),
                        size = Size(maxX - minX, 6.dp.toPx())
                    )

                    drawLine(
                        color = Color.White.copy(alpha = 0.4f),
                        start = Offset(minX, cy - 6.dp.toPx()),
                        end = Offset(minX, cy + 6.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )

                    drawLine(
                        color = Color.White.copy(alpha = 0.4f),
                        start = Offset(maxX, cy - 6.dp.toPx()),
                        end = Offset(maxX, cy + 6.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )

                    drawCircle(
                        color = statusColor,
                        radius = 6.dp.toPx(),
                        center = Offset(valX, cy)
                    )
                    drawCircle(
                        color = statusColor.copy(alpha = 0.3f),
                        radius = 10.dp.toPx(),
                        center = Offset(valX, cy)
                    )
                } else if (maxLimit != null) {
                    val max = maxLimit
                    val valF = value
                    val displayMax = max * 1.2f
                    val maxX = (max / displayMax) * w
                    val valX = ((valF / displayMax) * w).coerceIn(0f, w)

                    drawRect(
                        color = MeetColors.neonGreen.copy(alpha = 0.12f),
                        topLeft = Offset(0f, cy - 3.dp.toPx()),
                        size = Size(maxX, 6.dp.toPx())
                    )

                    drawLine(
                        color = Color.White.copy(alpha = 0.4f),
                        start = Offset(maxX, cy - 6.dp.toPx()),
                        end = Offset(maxX, cy + 6.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )

                    drawCircle(
                        color = statusColor,
                        radius = 6.dp.toPx(),
                        center = Offset(valX, cy)
                    )
                } else if (minLimit != null) {
                    val min = minLimit
                    val valF = value
                    val displayMin = min * 0.8f
                    val displayMax = if (valF > min) valF * 1.2f else min * 1.5f
                    val range = displayMax - displayMin
                    val minX = ((min - displayMin) / range) * w
                    val valX = (((valF - displayMin) / range) * w).coerceIn(0f, w)

                    drawRect(
                        color = MeetColors.neonGreen.copy(alpha = 0.12f),
                        topLeft = Offset(minX, cy - 3.dp.toPx()),
                        size = Size(w - minX, 6.dp.toPx())
                    )

                    drawLine(
                        color = Color.White.copy(alpha = 0.4f),
                        start = Offset(minX, cy - 6.dp.toPx()),
                        end = Offset(minX, cy + 6.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )

                    drawCircle(
                        color = statusColor,
                        radius = 6.dp.toPx(),
                        center = Offset(valX, cy)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val minText = minLimit?.let { String.format("%.3f %s", it, unit) } ?: "---"
            val maxText = maxLimit?.let { String.format("%.3f %s", it, unit) } ?: "---"

            Text(
                text = "${if (isSpanish) "Lím. Mín: " else "Min Limit: "}$minText",
                color = MeetColors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp
            )

            Text(
                text = "${if (isSpanish) "Lím. Máx: " else "Max Limit: "}$maxText",
                color = MeetColors.textMuted,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp
            )
        }

        marginObservability?.let { text ->
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = text,
                color = if (passed) {
                    if (text.startsWith("⚠️")) MeetColors.warning else MeetColors.neonGreen
                } else MeetColors.error,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun EmptyMonitorsState(isConnected: Boolean, isSpanish: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            AnimatedNeonGlyph("🔬", contentDescription = null, fontSize = 64.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                if (isConnected) {
                    if (isSpanish) "Sistemas Listos para Diagnóstico" else "Monitors Ready for Scanning"
                } else {
                    if (isSpanish) "Escáner Desconectado" else "Scanner Disconnected"
                },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (isConnected) {
                    if (isSpanish) "El Mode \$06 permite evaluar los resultados de las auto-pruebas internas de la ECU. Pulsa LEER MODE \$06 para iniciar."
                    else "Mode \$06 allows you to review the ECU's self-test results. Press READ MODE \$06 to start the scanning cycle."
                } else {
                    if (isSpanish) "Conecta el adaptador OBD2 para interrogar la ECU y ver los datos internos de fallas."
                    else "Connect the OBD2 adapter to run system scans and pull deep engineering logs."
                },
                color = MeetColors.textSecondary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
