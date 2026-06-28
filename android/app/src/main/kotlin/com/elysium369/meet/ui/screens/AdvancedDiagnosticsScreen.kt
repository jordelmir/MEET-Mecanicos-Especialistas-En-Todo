package com.elysium369.meet.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.core.obd.*
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.*
import com.elysium369.meet.ui.theme.MeetColors

/**
 * AdvancedDiagnosticsScreen — Master diagnostic hub providing access to ALL
 * supported OBD-II modes ($01–$0A) and UDS services ($10–$3F, $7F, $81–$85)
 * plus manufacturer-specific modes ($B0–$BF, $D0–$DF, $EA–$FE).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedDiagnosticsScreen(
    navController: NavController,
    viewModel: ObdViewModel
) {
    // Collect all relevant states
    val o2Tests by viewModel.o2SensorTests.collectAsState()
    val isReadingO2 by viewModel.isReadingO2Tests.collectAsState()
    val categorizedDtcs by viewModel.categorizedDtcs.collectAsState()
    val vehicleInfo by viewModel.vehicleInfoExtended.collectAsState()
    val udsCapabilities by viewModel.udsCapabilities.collectAsState()
    val ecuInfo by viewModel.ecuInfo.collectAsState()
    val lastUdsOp by viewModel.lastUdsOperation.collectAsState()
    val manufacturerModes by viewModel.manufacturerModes.collectAsState()
    val mode06Results by viewModel.mode06Results.collectAsState()
    val isReadingMode06 by viewModel.isReadingMode06.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        "OBD-II" to Icons.Filled.DirectionsCar,
        "O₂ Tests" to Icons.Filled.Sensors,
        "DTCs" to Icons.Filled.Warning,
        "Vehicle" to Icons.Filled.Info,
        "UDS" to Icons.Filled.Terminal,
        "OEM" to Icons.Filled.Build
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedNeonIcon(
                            Icons.Filled.BugReport,
                            contentDescription = null,
                            tint = MeetColors.neonGreen,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Diagnóstico Avanzado",
                                color = MeetColors.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "OBD-II / UDS / OEM",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        AnimatedNeonIcon(Icons.Filled.ArrowBack, "Volver", tint = MeetColors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MeetColors.backgroundDeep
                )
            )
        },
        containerColor = MeetColors.backgroundDeep
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Status bar
            if (lastUdsOp.isNotEmpty()) {
                StatusBanner(lastUdsOp)
            }

            // Tab row
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MeetColors.backgroundDark,
                contentColor = MeetColors.neonGreen,
                edgePadding = 8.dp,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MeetColors.neonGreen,
                            height = 3.dp
                        )
                    }
                },
                divider = {}
            ) {
                tabs.forEachIndexed { index, (title, icon) ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = {
                            AnimatedNeonIcon(icon, title, modifier = Modifier.size(18.dp))
                        },
                        selectedContentColor = MeetColors.neonGreen,
                        unselectedContentColor = MeetColors.textSecondary
                    )
                }
            }

            // Content
            when (selectedTab) {
                0 -> ObdModesTab(viewModel)
                1 -> O2SensorTestsTab(o2Tests, isReadingO2, onRefresh = { viewModel.readO2SensorTests() })
                2 -> CategorizedDtcsTab(categorizedDtcs, viewModel)
                3 -> VehicleInfoTab(vehicleInfo, onRefresh = { viewModel.readExtendedVehicleInfo() })
                4 -> UdsServicesTab(udsCapabilities, ecuInfo, viewModel)
                5 -> ManufacturerModesTab(manufacturerModes, viewModel)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// STATUS BANNER
// ═══════════════════════════════════════════════════════════

@Composable
private fun StatusBanner(message: String) {
    val isError = message.startsWith("Error")
    val bgColor = if (isError) MeetColors.error.copy(alpha = 0.15f) else MeetColors.neonGreen.copy(alpha = 0.1f)
    val textColor = if (isError) MeetColors.error else MeetColors.neonGreen

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedNeonIcon(
            if (isError) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
            null,
            tint = textColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(message, color = textColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ═══════════════════════════════════════════════════════════
// TAB 0: OBD-II STANDARD MODES OVERVIEW
// ═══════════════════════════════════════════════════════════

@Composable
private fun ObdModesTab(viewModel: ObdViewModel) {
    val obdModes = remember {
        listOf(
            ObdModeInfo("\$01", "Live Data", "Datos en tiempo real", Icons.Filled.Speed, true),
            ObdModeInfo("\$02", "Freeze Frame", "Datos congelados en DTC", Icons.Filled.AcUnit, true),
            ObdModeInfo("\$03", "Stored DTCs", "Códigos de falla almacenados", Icons.Filled.Warning, true),
            ObdModeInfo("\$04", "Clear DTCs", "Borrar códigos y apagar MIL", Icons.Filled.Delete, true),
            ObdModeInfo("\$05", "O₂ Monitor Tests", "Monitoreo de sensores de O₂", Icons.Filled.Sensors, true),
            ObdModeInfo("\$06", "On-Board Monitors", "Resultados de monitores no continuos", Icons.Filled.MonitorHeart, true),
            ObdModeInfo("\$07", "Pending DTCs", "Códigos pendientes (último ciclo)", Icons.Filled.HourglassBottom, true),
            ObdModeInfo("\$08", "Control Tests", "Solicitar prueba de actuador", Icons.Filled.SettingsRemote, true),
            ObdModeInfo("\$09", "Vehicle Info", "VIN, ECU Name, Calibración", Icons.Filled.Info, true),
            ObdModeInfo("\$0A", "Permanent DTCs", "DTCs permanentes (no borrables)", Icons.Filled.Lock, true)
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SectionHeader("Modos OBD-II Estándar", "SAE J1979 / ISO 15031-5")
        }

        items(obdModes) { mode ->
            ObdModeCard(mode)
        }

        item { Spacer(Modifier.height(8.dp)) }

        item {
            SectionHeader("Modos UDS Avanzados", "ISO 14229 / ISO 15765")
        }

        items(udsServicesList) { svc ->
            UdsServiceCard(svc)
        }
    }
}

private data class ObdModeInfo(
    val sid: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val supported: Boolean
)

@Composable
private fun ObdModeCard(mode: ObdModeInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // SID badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MeetColors.neonGreen.copy(alpha = 0.2f),
                                MeetColors.cyberCyan.copy(alpha = 0.1f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    mode.sid,
                    color = MeetColors.neonGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    mode.name,
                    color = MeetColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    mode.description,
                    color = MeetColors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Status
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (mode.supported) MeetColors.success else MeetColors.textMuted)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// TAB 1: O₂ SENSOR TESTS (MODE $05)
// ═══════════════════════════════════════════════════════════

@Composable
private fun O2SensorTestsTab(
    tests: List<O2SensorTestResult>,
    isReading: Boolean,
    onRefresh: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader("O₂ Sensor Monitor", "Mode \$05 — SAE J1979")
                Button(
                    onClick = onRefresh,
                    enabled = !isReading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeetColors.neonGreen.copy(alpha = 0.15f),
                        contentColor = MeetColors.neonGreen
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isReading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MeetColors.neonGreen
                        )
                    } else {
                        AnimatedNeonIcon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    AnimatedNeonGlyph("Leer", contentDescription = null, fontSize = 12.sp)
                }
            }
        }

        if (tests.isEmpty() && !isReading) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.Sensors,
                    title = "Sin datos de O₂",
                    subtitle = "Presiona \"Leer\" para ejecutar Mode \$05"
                )
            }
        }

        items(tests) { result ->
            O2TestResultCard(result)
        }
    }
}

@Composable
private fun O2TestResultCard(result: O2SensorTestResult) {
    val passColor = MeetColors.success
    val failColor = MeetColors.error
    val statusColor = if (result.passed) passColor else failColor

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header with pass/fail
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedNeonIcon(
                            if (result.passed) Icons.Filled.Check else Icons.Filled.Close,
                            null,
                            tint = statusColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            result.testDescriptionEs,
                            color = MeetColors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${result.sensorId} • TID ${String.format("%02X", result.testId)}",
                            color = MeetColors.textSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Pass/Fail Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        if (result.passed) "PASS" else "FAIL",
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Value bar
            O2ValueBar(
                current = result.value,
                min = result.minLimit,
                max = result.maxLimit,
                unit = result.unit,
                passed = result.passed
            )
        }
    }
}

@Composable
private fun O2ValueBar(
    current: Float,
    min: Float?,
    max: Float?,
    unit: String,
    passed: Boolean
) {
    val effectiveMin = min ?: 0f
    val effectiveMax = max ?: (current * 2f).coerceAtLeast(1f)
    val range = (effectiveMax - effectiveMin).coerceAtLeast(0.001f)
    val progress = ((current - effectiveMin) / range).toFloat().coerceIn(0f, 1f)
    val barColor = if (passed) MeetColors.neonGreen else MeetColors.error

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Min: ${min?.let { String.format("%.3f", it) } ?: "—"} $unit",
                color = MeetColors.textMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "Actual: ${String.format("%.3f", current)} $unit",
                color = MeetColors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "Max: ${max?.let { String.format("%.3f", it) } ?: "—"} $unit",
                color = MeetColors.textMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(4.dp))

        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MeetColors.backgroundDark)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(barColor.copy(alpha = 0.6f), barColor)
                        )
                    )
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// TAB 2: CATEGORIZED DTCs ($03 / $07 / $0A)
// ═══════════════════════════════════════════════════════════

@Composable
private fun CategorizedDtcsTab(dtcs: CategorizedDtcs, viewModel: ObdViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader("Códigos de Falla", "Clasificación por fuente")
                Button(
                    onClick = { viewModel.readCategorizedDtcs() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeetColors.neonGreen.copy(alpha = 0.15f),
                        contentColor = MeetColors.neonGreen
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    AnimatedNeonIcon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Escanear", fontSize = 12.sp)
                }
            }
        }

        // Summary cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DtcCountBadge(
                    modifier = Modifier.weight(1f),
                    count = dtcs.confirmed.size,
                    label = "Almacenados",
                    mode = "\$03",
                    color = MeetColors.error
                )
                DtcCountBadge(
                    modifier = Modifier.weight(1f),
                    count = dtcs.pending.size,
                    label = "Pendientes",
                    mode = "\$07",
                    color = MeetColors.warning
                )
                DtcCountBadge(
                    modifier = Modifier.weight(1f),
                    count = dtcs.permanent.size,
                    label = "Permanentes",
                    mode = "\$0A",
                    color = Color(0xFFFF5252)
                )
            }
        }

        // Stored DTCs
        if (dtcs.confirmed.isNotEmpty()) {
            item { DtcCategoryHeader("Almacenados (Mode \$03)", MeetColors.error) }
            items(dtcs.confirmed) { dtc -> DtcRow(dtc, MeetColors.error) }
        }

        // Pending DTCs
        if (dtcs.pending.isNotEmpty()) {
            item { DtcCategoryHeader("Pendientes (Mode \$07)", MeetColors.warning) }
            items(dtcs.pending) { dtc -> DtcRow(dtc, MeetColors.warning) }
        }

        // Permanent DTCs
        if (dtcs.permanent.isNotEmpty()) {
            item { DtcCategoryHeader("Permanentes (Mode \$0A)", Color(0xFFFF5252)) }
            items(dtcs.permanent) { dtc -> DtcRow(dtc, Color(0xFFFF5252)) }
        }

        if (dtcs.confirmed.isEmpty() && dtcs.pending.isEmpty() && dtcs.permanent.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.CheckCircle,
                    title = "Sin DTCs",
                    subtitle = "No se encontraron códigos de falla"
                )
            }
        }
    }
}

@Composable
private fun DtcCountBadge(
    modifier: Modifier,
    count: Int,
    label: String,
    mode: String,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "$count",
                color = if (count > 0) color else MeetColors.success,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(label, color = MeetColors.textSecondary, fontSize = 10.sp)
            Text(mode, color = MeetColors.textMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun DtcCategoryHeader(title: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(title, color = MeetColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DtcRow(dtc: Pair<String, String>, color: Color) {
    val (code, description) = dtc
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = color.copy(alpha = 0.15f)
            ) {
                Text(
                    code,
                    color = color,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                val type = when {
                    code.startsWith("P0") || code.startsWith("P2") || code.startsWith("P34") || code.startsWith("P35") -> "Genérico (SAE)"
                    code.startsWith("P1") || code.startsWith("P30") || code.startsWith("P31") || code.startsWith("P32") || code.startsWith("P33") -> "Fabricante"
                    code.startsWith("C") -> "Chasis"
                    code.startsWith("B") -> "Carrocería"
                    code.startsWith("U") -> "Comunicación"
                    else -> "Desconocido"
                }
                Text(type, color = MeetColors.textSecondary, fontSize = 11.sp)
                Text(
                    description,
                    color = MeetColors.textMuted,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// TAB 3: VEHICLE INFO (MODE $09 EXTENDED)
// ═══════════════════════════════════════════════════════════

@Composable
private fun VehicleInfoTab(
    info: Map<String, String>,
    onRefresh: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader("Información del Vehículo", "Mode \$09 — Extendido")
                Button(
                    onClick = onRefresh,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeetColors.neonGreen.copy(alpha = 0.15f),
                        contentColor = MeetColors.neonGreen
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    AnimatedNeonIcon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    AnimatedNeonGlyph("Leer", contentDescription = null, fontSize = 12.sp)
                }
            }
        }

        if (info.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.DirectionsCar,
                    title = "Sin información",
                    subtitle = "Presiona \"Leer\" para obtener datos Mode \$09"
                )
            }
        } else {
            // VIN card (special treatment)
            info["VIN"]?.let { vin ->
                item { VinCard(vin) }
            }

            // Other info
            val otherInfo = info.filterKeys { it != "VIN" }
            items(otherInfo.entries.toList()) { (key, value) ->
                InfoRow(key, value)
            }
        }
    }
}

@Composable
private fun VinCard(vin: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MeetColors.neonGreen.copy(alpha = 0.1f),
                            MeetColors.electricBlue.copy(alpha = 0.08f)
                        )
                    )
                )
                .border(
                    1.dp,
                    MeetColors.neonGreen.copy(alpha = 0.3f),
                    RoundedCornerShape(14.dp)
                )
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedNeonIcon(
                        Icons.Filled.Fingerprint,
                        null,
                        tint = MeetColors.neonGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Vehicle Identification Number",
                        color = MeetColors.textSecondary,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    vin,
                    color = MeetColors.neonGreen,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )

                // VIN decode
                if (vin.length >= 17) {
                    Spacer(Modifier.height(10.dp))
                    Divider(color = MeetColors.borderSubtle, thickness = 1.dp)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        VinDetail("WMI", vin.substring(0, 3))
                        VinDetail("VDS", vin.substring(3, 9))
                        VinDetail("VIS", vin.substring(9))
                        VinDetail("Año", decodeVinYear(vin[9]))
                    }
                }
            }
        }
    }
}

@Composable
private fun VinDetail(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = MeetColors.textMuted, fontSize = 9.sp)
        Text(
            value,
            color = MeetColors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun decodeVinYear(c: Char): String = when (c) {
    'A' -> "2010"; 'B' -> "2011"; 'C' -> "2012"; 'D' -> "2013"; 'E' -> "2014"
    'F' -> "2015"; 'G' -> "2016"; 'H' -> "2017"; 'J' -> "2018"; 'K' -> "2019"
    'L' -> "2020"; 'M' -> "2021"; 'N' -> "2022"; 'P' -> "2023"; 'R' -> "2024"
    'S' -> "2025"; 'T' -> "2026"; 'V' -> "2027"; 'W' -> "2028"; 'X' -> "2029"
    'Y' -> "2030"
    '1' -> "2001"; '2' -> "2002"; '3' -> "2003"; '4' -> "2004"; '5' -> "2005"
    '6' -> "2006"; '7' -> "2007"; '8' -> "2008"; '9' -> "2009"
    else -> "?"
}

@Composable
private fun InfoRow(key: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                key,
                color = MeetColors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.weight(0.4f)
            )
            Text(
                value,
                color = MeetColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.6f)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// TAB 4: UDS SERVICES
// ═══════════════════════════════════════════════════════════

private data class UdsServiceInfo(
    val sid: String,
    val name: String,
    val description: String,
    val category: String
)

private val udsServicesList = listOf(
    UdsServiceInfo("\$10", "DiagnosticSessionControl", "Cambiar sesión de diagnóstico", "Session"),
    UdsServiceInfo("\$11", "ECUReset", "Reiniciar ECU (hard/soft/key-off)", "Control"),
    UdsServiceInfo("\$12", "SubFunctionServer", "Sub-funciones del servidor (legacy)", "Legacy"),
    UdsServiceInfo("\$14", "ClearDTC", "Borrar información de DTC", "DTC"),
    UdsServiceInfo("\$19", "ReadDTCInformation", "Leer DTCs con subfunciones", "DTC"),
    UdsServiceInfo("\$1A", "ReadECUIdentification", "Identificación ECU (KWP2000)", "Legacy"),
    UdsServiceInfo("\$22", "ReadDataByIdentifier", "Leer datos por DID", "Data"),
    UdsServiceInfo("\$23", "ReadMemoryByAddress", "Leer memoria por dirección", "Data"),
    UdsServiceInfo("\$24", "ReadScalingDataByIdentifier", "Leer escalado por DID", "Data"),
    UdsServiceInfo("\$27", "SecurityAccess", "Autenticación de seguridad", "Security"),
    UdsServiceInfo("\$28", "CommunicationControl", "Control de comunicación CAN/LIN", "Control"),
    UdsServiceInfo("\$29", "Authentication", "Autenticación PKI (ISO 14229-1:2020)", "Security"),
    UdsServiceInfo("\$2A", "ReadDataByPeriodicIdentifier", "Lectura periódica rápida", "Data"),
    UdsServiceInfo("\$2C", "DynamicallyDefineDataIdentifier", "Definir DIDs dinámicos", "Data"),
    UdsServiceInfo("\$2E", "WriteDataByIdentifier", "Escribir datos por DID", "Data"),
    UdsServiceInfo("\$2F", "InputOutputControlByIdentifier", "Control I/O actuadores", "Control"),
    UdsServiceInfo("\$31", "RoutineControl", "Ejecutar rutinas de diagnóstico", "Control"),
    UdsServiceInfo("\$34", "RequestDownload", "Solicitar descarga a ECU", "Flash"),
    UdsServiceInfo("\$35", "RequestUpload", "Solicitar carga desde ECU", "Flash"),
    UdsServiceInfo("\$36", "TransferData", "Transferir bloques de datos", "Flash"),
    UdsServiceInfo("\$37", "RequestTransferExit", "Finalizar transferencia", "Flash"),
    UdsServiceInfo("\$38", "RequestFileTransfer", "Transferencia de archivos", "Flash"),
    UdsServiceInfo("\$3D", "WriteMemoryByAddress", "Escribir memoria por dirección", "Flash"),
    UdsServiceInfo("\$3E", "TesterPresent", "Mantener sesión activa", "Session"),
    UdsServiceInfo("\$3F", "NegativeResponse", "Código de respuesta negativa (NRC)", "System"),
    UdsServiceInfo("\$7F", "NRC Handler", "Decodificador de errores NRC", "System"),
    UdsServiceInfo("\$81", "StartCommunication", "Inicio comunicación KWP2000", "KWP"),
    UdsServiceInfo("\$82", "StopCommunication", "Detener comunicación KWP2000", "KWP"),
    UdsServiceInfo("\$83", "AccessTimingParameter", "Parámetros de temporización", "KWP"),
    UdsServiceInfo("\$85", "ControlDTCSetting", "Activar/Desactivar DTCs", "DTC")
)

@Composable
private fun UdsServicesTab(
    capabilities: UdsCapabilities?,
    ecuInfo: List<UdsReadResult>,
    viewModel: ObdViewModel
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeader("Servicios UDS", "ISO 14229 Unified Diagnostic Services")
        }

        // Quick action buttons
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionChip("Descubrir", Icons.Filled.Search) { viewModel.discoverUdsCapabilities() }
                QuickActionChip("Leer ECU", Icons.Filled.Memory) { viewModel.readEcuInfo() }
                QuickActionChip("DTCs UDS", Icons.Filled.Warning) { viewModel.readDtcUds() }
                QuickActionChip("TesterPresent", Icons.Filled.Favorite) { viewModel.readDataByIdentifier("3E") }
            }
        }

        // Capabilities card
        capabilities?.let { caps ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "Capacidades Detectadas",
                            color = MeetColors.neonGreen,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        val services = listOfNotNull(
                            "10".takeIf { caps.supportsExtendedSession },
                            "22".takeIf { caps.supportsReadByIdentifier },
                            "2F".takeIf { caps.supportsIOControl },
                            "31".takeIf { caps.supportsRoutineControl },
                            "27".takeIf { caps.supportsSecurityAccess },
                            "28".takeIf { caps.supportsCommunicationControl }
                        )
                        CapabilityRow("Sesión extendida", if (caps.supportsExtendedSession) "Soportada" else "No detectada")
                        CapabilityRow("Servicios", services.joinToString().ifBlank { "No detectados" })
                        CapabilityRow("Security Access", if (caps.supportsSecurityAccess) "Soportado" else "No detectado")
                        CapabilityRow("DIDs", caps.discoveredDids.joinToString().ifBlank { "Ninguno" })
                    }
                }
            }
        }

        // ECU Info results
        if (ecuInfo.isNotEmpty()) {
            item {
                Text(
                    "Datos ECU (${ecuInfo.size} DIDs)",
                    color = MeetColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            items(ecuInfo) { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(0.4f)) {
                            Text(result.didName, color = MeetColors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("DID: ${result.did}", color = MeetColors.textMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                        Text(
                            result.decodedValue.ifBlank { result.rawHex },
                            color = MeetColors.cyberCyan,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(0.6f)
                        )
                    }
                }
            }
        }

        // Service reference table
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "Referencia de Servicios",
                color = MeetColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        items(udsServicesList) { svc ->
            UdsServiceCard(svc)
        }
    }
}

@Composable
private fun QuickActionChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MeetColors.neonGreen.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedNeonIcon(icon, null, tint = MeetColors.neonGreen, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = MeetColors.neonGreen, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun CapabilityRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MeetColors.textSecondary, fontSize = 11.sp)
        Text(
            value,
            color = MeetColors.textPrimary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun UdsServiceCard(svc: UdsServiceInfo) {
    val categoryColor = when (svc.category) {
        "Session" -> MeetColors.cyberCyan
        "Control" -> MeetColors.warning
        "DTC" -> MeetColors.error
        "Data" -> MeetColors.neonGreen
        "Security" -> Color(0xFFFF5252)
        "Flash" -> MeetColors.electricBlue
        "KWP" -> MeetColors.hotMagenta
        "System" -> MeetColors.textSecondary
        else -> MeetColors.textMuted
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // SID badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = categoryColor.copy(alpha = 0.15f)
            ) {
                Text(
                    svc.sid,
                    color = categoryColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(svc.name, color = MeetColors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(svc.description, color = MeetColors.textSecondary, fontSize = 10.sp)
            }
            // Category badge
            Text(
                svc.category,
                color = categoryColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// TAB 5: MANUFACTURER / OEM MODES ($B0-$BF, $D0-$DF, $EA-$FE)
// ═══════════════════════════════════════════════════════════

@Composable
private fun ManufacturerModesTab(
    modes: Map<String, Boolean>,
    viewModel: ObdViewModel
) {
    var customSid by remember { mutableStateOf("") }
    var customSub by remember { mutableStateOf("") }
    var customResult by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader("Modos OEM / Fabricante", "Propietarios del fabricante")
                Button(
                    onClick = { viewModel.probeManufacturerModes() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeetColors.electricBlue.copy(alpha = 0.15f),
                        contentColor = MeetColors.electricBlue
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    AnimatedNeonIcon(Icons.Filled.Radar, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sondear", fontSize = 12.sp)
                }
            }
        }

        // Custom command input
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "Comando Manual OEM",
                        color = MeetColors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customSid,
                            onValueChange = { if (it.length <= 2) customSid = it.uppercase().filter { c -> c in "0123456789ABCDEF" } },
                            label = { Text("SID (hex)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = MeetColors.neonGreen
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MeetColors.neonGreen,
                                unfocusedBorderColor = MeetColors.borderBlue,
                                cursorColor = MeetColors.neonGreen
                            )
                        )
                        OutlinedTextField(
                            value = customSub,
                            onValueChange = { customSub = it.uppercase().filter { c -> c in "0123456789ABCDEF " } },
                            label = { Text("Sub (hex)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = MeetColors.cyberCyan
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MeetColors.neonGreen,
                                unfocusedBorderColor = MeetColors.borderBlue,
                                cursorColor = MeetColors.neonGreen
                            )
                        )
                        IconButton(
                            onClick = {
                                if (customSid.length == 2) {
                                    val resultFlow = viewModel.sendManufacturerCommand(customSid, customSub)
                                    // Note: in real impl, collect the flow
                                    customResult = "Enviando \$$customSid ${customSub}..."
                                }
                            }
                        ) {
                            AnimatedNeonIcon(Icons.Filled.Send, null, tint = MeetColors.neonGreen)
                        }
                    }

                    customResult?.let { result ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            result,
                            color = MeetColors.cyberCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MeetColors.backgroundDeep,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(8.dp)
                        )
                    }
                }
            }
        }

        // Mode ranges
        item { ModeRangeHeader("\$B0–\$BF", "OEM Diagnostic Range 1") }
        items((0xB0..0xBF).toList()) { sid ->
            val hex = String.format("%02X", sid)
            val supported = modes[hex] ?: false
            ManufacturerModeRow(hex, supported)
        }

        item { ModeRangeHeader("\$D0–\$DF", "OEM Diagnostic Range 2") }
        items((0xD0..0xDF).toList()) { sid ->
            val hex = String.format("%02X", sid)
            val supported = modes[hex] ?: false
            ManufacturerModeRow(hex, supported)
        }

        item { ModeRangeHeader("\$EA–\$FE", "OEM Extended / Proprietary") }
        items((0xEA..0xFE).toList()) { sid ->
            val hex = String.format("%02X", sid)
            val supported = modes[hex] ?: false
            ManufacturerModeRow(hex, supported)
        }
    }
}

@Composable
private fun ModeRangeHeader(range: String, description: String) {
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(range, color = MeetColors.electricBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(8.dp))
        Text(description, color = MeetColors.textSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun ManufacturerModeRow(sid: String, supported: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (supported) MeetColors.neonGreen.copy(alpha = 0.05f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "\$$sid",
            color = if (supported) MeetColors.neonGreen else MeetColors.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (supported) MeetColors.success else MeetColors.textMuted.copy(alpha = 0.3f))
        )
    }
}

// ═══════════════════════════════════════════════════════════
// SHARED COMPOSABLES
// ═══════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(
            title,
            color = MeetColors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            subtitle,
            color = MeetColors.textMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun EmptyStateCard(icon: ImageVector, title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedNeonIcon(icon, null, tint = MeetColors.textMuted, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, color = MeetColors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = MeetColors.textMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}
