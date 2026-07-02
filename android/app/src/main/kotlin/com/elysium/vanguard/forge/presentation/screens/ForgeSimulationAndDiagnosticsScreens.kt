package com.elysium.vanguard.forge.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.vanguard.forge.domain.DamageSeverity
import com.elysium.vanguard.forge.domain.DamageType
import com.elysium.vanguard.forge.domain.DiagnosticReport
import com.elysium.vanguard.forge.domain.ForgeManual
import com.elysium.vanguard.forge.domain.MaterialSpec
import com.elysium.vanguard.forge.domain.ManufacturingProcess
import com.elysium.vanguard.forge.presentation.components.NeonCard
import com.elysium.vanguard.forge.presentation.components.SectionHeader
import com.elysium.vanguard.forge.presentation.components.SeverityBadge
import com.elysium.vanguard.forge.presentation.components.TechLabel
import com.elysium.vanguard.forge.presentation.components.UiState
import com.elysium.vanguard.forge.presentation.theme.ForgeColors
import com.elysium.vanguard.forge.presentation.viewmodels.ForgeDiagnosticReportViewModel
import com.elysium.vanguard.forge.presentation.viewmodels.ForgeEngineRuntimeViewModel
import com.elysium.vanguard.forge.presentation.viewmodels.ForgeFailureLabViewModel
import com.elysium.vanguard.forge.presentation.viewmodels.ForgeManufacturingViewModel
import com.elysium.vanguard.forge.presentation.viewmodels.ForgeMaterialsViewModel
import com.elysium.vanguard.forge.presentation.viewmodels.ForgePhysicsSimulationViewModel
import com.elysium.vanguard.forge.presentation.viewmodels.ForgeRepairManualViewModel

// ─────────── 1f. Physics Simulation ───────────

@Composable
fun ForgePhysicsSimulationScreen(
    viewModel: ForgePhysicsSimulationViewModel,
    onBack: () -> Unit = {}
) {
    val assembly by viewModel.assembly.collectAsState()
    val simState by viewModel.simState.collectAsState()
    val speed by viewModel.speedMultiplier.collectAsState()
    val gravity by viewModel.gravityEnabled.collectAsState()
    val jointStates by viewModel.jointStates.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TopBar(onBack = onBack, title = "SIMULACIÓN FÍSICA")
        when (val s = assembly) {
            is UiState.Loading -> Center("Cargando ensamble…", ForgeColors.Primary)
            is UiState.Empty -> Center("Sin ensamble para simular", ForgeColors.OnSurface)
            is UiState.Error -> Center(s.message, ForgeColors.Error)
            is UiState.Ready -> {
                NeonCard(modifier = Modifier.fillMaxWidth().height(180.dp), accentColor = ForgeColors.Accent) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${jointStates.size} joints activos",
                                color = ForgeColors.Accent,
                                fontSize = 24.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(Modifier.height(8.dp))
                            TechLabel("MODE: ${simState.name} · SPEED: ${"%.1fx".format(speed)}")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (simState == ForgePhysicsSimulationViewModel.SimulationState.STOPPED) {
                        Button(
                            onClick = { viewModel.play() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ForgeColors.Success.copy(alpha = 0.2f),
                                contentColor = ForgeColors.Success
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("PLAY")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.pause() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ForgeColors.Warning.copy(alpha = 0.2f),
                                contentColor = ForgeColors.Warning
                            )
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("PAUSE")
                        }
                    }
                    Button(
                        onClick = { viewModel.stop() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ForgeColors.Error.copy(alpha = 0.2f),
                            contentColor = ForgeColors.Error
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("STOP")
                    }
                }
                Spacer(Modifier.height(16.dp))
                SectionHeader("VELOCIDAD")
                Slider(
                    value = speed.toFloat(),
                    onValueChange = { viewModel.setSpeed(it.toDouble()) },
                    valueRange = 0.1f..5f
                )
                Spacer(Modifier.height(8.dp))
                SectionHeader("GRAVEDAD")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.setGravity(true) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (gravity) ForgeColors.Primary.copy(alpha = 0.3f) else ForgeColors.SurfaceVariant,
                            contentColor = if (gravity) ForgeColors.Primary else ForgeColors.OnSurface
                        ),
                        modifier = Modifier.weight(1f)
                    ) { Text("ON") }
                    Button(
                        onClick = { viewModel.setGravity(false) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!gravity) ForgeColors.Primary.copy(alpha = 0.3f) else ForgeColors.SurfaceVariant,
                            contentColor = if (!gravity) ForgeColors.Primary else ForgeColors.OnSurface
                        ),
                        modifier = Modifier.weight(1f)
                    ) { Text("OFF") }
                }
                Spacer(Modifier.height(16.dp))
                SectionHeader("OVERLAYS")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag("FORCES", ForgeColors.Accent)
                    Tag("TORQUE", ForgeColors.Secondary)
                    Tag("JOINTS", ForgeColors.Primary)
                    Tag("COLLISIONS", ForgeColors.Warning)
                    Tag("DAMAGE", ForgeColors.Error)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ─────────── 1g. Engine Runtime ───────────

@Composable
fun ForgeEngineRuntimeScreen(
    viewModel: ForgeEngineRuntimeViewModel,
    onBack: () -> Unit = {}
) {
    val vehicle by viewModel.vehicle.collectAsState()
    val rpm by viewModel.rpm.collectAsState()
    val coolant by viewModel.coolantC.collectAsState()
    val throttle by viewModel.throttle.collectAsState()
    val running by viewModel.running.collectAsState()
    val warnings by viewModel.warnings.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TopBar(onBack = onBack, title = "ENGINE RUNTIME")
        when (val v = vehicle) {
            is UiState.Loading -> Center("Cargando vehículo…", ForgeColors.Primary)
            is UiState.Empty -> Center("Sin vehículo con powertrain", ForgeColors.OnSurface)
            is UiState.Error -> Center(v.message, ForgeColors.Error)
            is UiState.Ready -> {
                NeonCard(modifier = Modifier.fillMaxWidth(), accentColor = ForgeColors.Primary) {
                    Column {
                        Text("RPM", color = ForgeColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "%.0f".format(rpm),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text("COOLANT: ${"%.1f".format(coolant)}°C", color = ForgeColors.OnSurface.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!running) {
                        Button(
                            onClick = { viewModel.engageStarter() },
                            modifier = Modifier.weight(1f).height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ForgeColors.Success.copy(alpha = 0.2f),
                                contentColor = ForgeColors.Success
                            )
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("IGNITION + START", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.stopEngine() },
                            modifier = Modifier.weight(1f).height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ForgeColors.Error.copy(alpha = 0.2f),
                                contentColor = ForgeColors.Error
                            )
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("STOP", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                SectionHeader("THROTTLE")
                Slider(value = throttle.toFloat(), onValueChange = { viewModel.setThrottle(it.toDouble()) }, valueRange = 0f..1f)
                Spacer(Modifier.height(16.dp))
                if (warnings.isNotEmpty()) {
                    SectionHeader("WARNINGS")
                    warnings.forEach { TechLabel("• $it") }
                }
                Spacer(Modifier.height(16.dp))
                SectionHeader("GAUGES")
                GaugeRow("RPM", rpm, 0.0, 7000.0, "rpm")
                GaugeRow("COOLANT", coolant, 0.0, 130.0, "°C")
                GaugeRow("THROTTLE", throttle * 100.0, 0.0, 100.0, "%")
            }
        }
    }
}

@Composable
private fun GaugeRow(label: String, value: Double, min: Double, max: Double, unit: String) {
    val pct = ((value - min) / (max - min)).toFloat().coerceIn(0f, 1f)
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row {
            Text(label, color = ForgeColors.OnSurface, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("%.1f %s".format(value, unit), color = ForgeColors.Primary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = if (pct > 0.85f) ForgeColors.Error else ForgeColors.Primary,
            trackColor = ForgeColors.SurfaceVariant
        )
    }
}

// ─────────── 1h. Failure Lab ───────────

@Composable
fun ForgeFailureLabScreen(
    viewModel: ForgeFailureLabViewModel,
    onBack: () -> Unit = {}
) {
    val assembly by viewModel.assembly.collectAsState()
    val diagnostic by viewModel.diagnostic.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TopBar(onBack = onBack, title = "FAILURE LAB")
        when (val s = assembly) {
            is UiState.Loading -> Center("Cargando…", ForgeColors.Primary)
            is UiState.Empty -> Center("Sin ensamble", ForgeColors.OnSurface)
            is UiState.Error -> Center(s.message, ForgeColors.Error)
            is UiState.Ready -> {
                val asm = s.data
                SectionHeader("INSTANCIAS")
                if (asm.instances.isEmpty()) {
                    TechLabel("Vacío. Agrega piezas al ensamble primero.")
                } else {
                    asm.instances.forEach { inst ->
                        NeonCard(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            accentColor = if (inst.damageState.severity.ordinal >= 2) ForgeColors.Error else ForgeColors.SurfaceVariant
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${inst.partId} · ${inst.id}",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )
                                    SeverityBadge(
                                        "HP ${inst.damageState.healthPercent.toInt()}%",
                        if (inst.damageState.healthPercent >= 80) ForgeColors.Success
                        else if (inst.damageState.healthPercent >= 40) ForgeColors.Warning
                        else ForgeColors.Error
                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text("Aplicar daño:", color = ForgeColors.OnSurface, fontSize = 11.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(
                                        DamageType.WEAR to "WEAR",
                                        DamageType.CRACK to "CRACK",
                                        DamageType.BROKEN to "BROKEN",
                                        DamageType.LEAK to "LEAK",
                                        DamageType.SEIZED to "SEIZED"
                                    ).forEach { (type, label) ->
                                        Button(
                                            onClick = {
                                                viewModel.injectDamage(inst.id, type, DamageSeverity.HIGH)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = ForgeColors.Error.copy(alpha = 0.15f),
                                                contentColor = ForgeColors.Error
                                            )
                                        ) { Text(label, fontSize = 10.sp) }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { viewModel.repair(inst.id) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ForgeColors.Success.copy(alpha = 0.2f),
                                            contentColor = ForgeColors.Success
                                        )
                                    ) {
                                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.width(14.dp).height(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Repair", fontSize = 11.sp)
                                    }
                                    Button(
                                        onClick = { viewModel.replace(inst.id) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ForgeColors.Accent.copy(alpha = 0.2f),
                                            contentColor = ForgeColors.Accent
                                        )
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.width(14.dp).height(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Replace", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.diagnose() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ForgeColors.Secondary.copy(alpha = 0.2f),
                        contentColor = ForgeColors.Secondary
                    )
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Diagnosticar ensamble")
                }
                diagnostic?.let { report ->
                    Spacer(Modifier.height(16.dp))
                    DiagnosticPanel(report)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticPanel(report: DiagnosticReport) {
    NeonCard(modifier = Modifier.fillMaxWidth(), accentColor = if (report.severity.ordinal >= 2) ForgeColors.Error else ForgeColors.Warning) {
        Column {
            Text("Diagnóstico", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            TechLabel("Pieza: ${report.affectedPartName}")
            TechLabel("Probable falla: ${report.probableFailure}")
            TechLabel("Confianza: ${(report.confidence * 100).toInt()}%")
            Spacer(Modifier.height(6.dp))
            report.safetyWarnings.take(3).forEach { TechLabel("⚠ $it") }
        }
    }
}

// ─────────── 1i. Diagnostic Report ───────────

@Composable
fun ForgeDiagnosticReportScreen(
    viewModel: ForgeDiagnosticReportViewModel,
    onBack: () -> Unit = {}
) {
    val state by viewModel.report.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TopBar(onBack = onBack, title = "DIAGNOSTIC REPORT")
        when (val s = state) {
            is UiState.Loading -> Center("Cargando…", ForgeColors.Primary)
            is UiState.Empty -> Center("Sin reporte", ForgeColors.OnSurface)
            is UiState.Error -> Center(s.message, ForgeColors.Error)
            is UiState.Ready -> ReportContent(s.data)
        }
    }
}

@Composable
private fun ReportContent(report: DiagnosticReport) {
    LazyColumn {
        item {
            SectionHeader("Pieza afectada")
            TechLabel(report.affectedPartName)
        }
        item {
            SectionHeader("Falla probable")
            NeonCard(modifier = Modifier.fillMaxWidth(), accentColor = ForgeColors.Warning) {
                Text(report.probableFailure, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
            }
        }
        item {
            SectionHeader("Confianza")
            TechLabel("${(report.confidence * 100).toInt()}%")
        }
        item {
            SectionHeader("Síntomas observados")
            report.observedSymptoms.forEach { TechLabel("• $it") }
        }
        item {
            SectionHeader("Causas probables")
            report.likelyCauses.forEach { TechLabel("• $it") }
        }
        item {
            SectionHeader("Advertencias de seguridad")
            report.safetyWarnings.forEach { TechLabel("⚠ $it") }
        }
        item {
            SectionHeader("DTCs relacionados")
            report.relatedDtcCodes.forEach { TechLabel(it) }
        }
    }
}

// ─────────── 1j. Repair Manual ───────────

@Composable
fun ForgeRepairManualScreen(
    viewModel: ForgeRepairManualViewModel,
    onBack: () -> Unit = {}
) {
    val state by viewModel.manual.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TopBar(onBack = onBack, title = "REPAIR MANUAL")
        when (val s = state) {
            is UiState.Loading -> Center("Cargando…", ForgeColors.Primary)
            is UiState.Empty -> Center("Sin manual", ForgeColors.OnSurface)
            is UiState.Error -> Center(s.message, ForgeColors.Error)
            is UiState.Ready -> ManualContent(s.data)
        }
    }
}

@Composable
private fun ManualContent(manual: ForgeManual) {
    LazyColumn {
        item { Text(manual.artifact.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
        item { TechLabel("Tipo: ${manual.manualType.name}") }
        item {
            SectionHeader("ALCANCE")
            TechLabel(manual.scope.ifBlank { "(no definido)" })
        }
        item {
            SectionHeader("ADVERTENCIAS DE SEGURIDAD")
            manual.safetyWarnings.forEach { TechLabel("⚠ $it") }
        }
        item {
            SectionHeader("PASOS")
            manual.steps.forEach { step ->
                NeonCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), accentColor = ForgeColors.Primary) {
                    Column {
                        Row {
                            Text("#${step.order}", color = ForgeColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
                            Text(step.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(step.description, color = ForgeColors.OnSurface.copy(alpha = 0.7f), fontSize = 12.sp)
                        if (step.warnings.isNotEmpty()) {
                            TechLabel("⚠ ${step.warnings.joinToString("; ")}")
                        }
                    }
                }
            }
        }
        item {
            SectionHeader("INSPECCIÓN FINAL")
            manual.inspectionChecklist.forEach { TechLabel("☐ $it") }
        }
        item {
            SectionHeader("VALIDACIÓN FINAL")
            manual.finalValidationSteps.forEach { TechLabel("✓ $it") }
        }
        item {
            SectionHeader("TORQUE SPECS")
            manual.torqueSpecs.forEach { ts ->
                TechLabel("${ts.fastenerName}: ${ts.torqueNm} Nm${ts.note?.let { " — $it" } ?: ""}")
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

// ─────────── 1k. Materials ───────────

@Composable
fun ForgeMaterialsScreen(
    viewModel: ForgeMaterialsViewModel,
    onBack: () -> Unit = {}
) {
    val state by viewModel.materials.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TopBar(onBack = onBack, title = "MATERIALES")
        when (val s = state) {
            is UiState.Loading -> Center("Cargando…", ForgeColors.Primary)
            is UiState.Empty -> Center("Vacío", ForgeColors.OnSurface)
            is UiState.Error -> Center(s.message, ForgeColors.Error)
            is UiState.Ready -> MaterialsList(s.data)
        }
    }
}

@Composable
private fun MaterialsList(materials: List<MaterialSpec>) {
    LazyColumn {
        items(count = materials.size) { i ->
            val m = materials[i]
            NeonCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                accentColor = ForgeColors.Accent
            ) {
                Column {
                    Text(m.displayName, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    TechLabel(m.category)
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        SpecCell("Densidad", "${m.densityKgM3.toInt()}", "kg/m³", Modifier.weight(1f))
                        SpecCell("Yield", "${m.yieldStrengthMPa.toInt()}", "MPa", Modifier.weight(1f))
                        SpecCell("Max T", "${m.maxOperatingTempC.toInt()}", "°C", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        SpecCell("Costo", "${m.costLevel}/5", "", Modifier.weight(1f))
                        SpecCell("Corrosión", m.corrosionResistance, "", Modifier.weight(1f))
                        SpecCell("Fatiga", m.fatigueResistance, "", Modifier.weight(1f))
                    }
                    if (m.warnings.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        m.warnings.forEach { TechLabel("⚠ $it") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun SpecCell(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(2.dp)) {
        Text(label.uppercase(), color = ForgeColors.OnSurface.copy(alpha = 0.5f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.5.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = ForgeColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            if (unit.isNotBlank()) {
                Spacer(Modifier.width(2.dp))
                Text(unit, color = ForgeColors.OnSurface.copy(alpha = 0.6f), fontSize = 9.sp)
            }
        }
    }
}

// ─────────── 1l. Manufacturing ───────────

@Composable
fun ForgeManufacturingScreen(
    viewModel: ForgeManufacturingViewModel,
    onBack: () -> Unit = {}
) {
    val state by viewModel.processes.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TopBar(onBack = onBack, title = "PROCESOS DE FABRICACIÓN")
        when (val s = state) {
            is UiState.Loading -> Center("Cargando…", ForgeColors.Primary)
            is UiState.Empty -> Center("Vacío", ForgeColors.OnSurface)
            is UiState.Error -> Center(s.message, ForgeColors.Error)
            is UiState.Ready -> ProcessesList(s.data)
        }
    }
}

@Composable
private fun ProcessesList(processes: List<ManufacturingProcess>) {
    LazyColumn {
        items(count = processes.size) { i ->
            val p = processes[i]
            NeonCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                accentColor = ForgeColors.Secondary
            ) {
                Column {
                    Row {
                        Text(p.displayName, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        SeverityBadge("±${p.typicalPrecisionMm}mm", ForgeColors.Secondary)
                    }
                    TechLabel(p.category)
                    Spacer(Modifier.height(6.dp))
                    Text(p.description, color = ForgeColors.OnSurface.copy(alpha = 0.7f), fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Pasos:", color = ForgeColors.Secondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    p.steps.take(5).forEach { TechLabel("• $it") }
                    if (p.risks.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Riesgos:", color = ForgeColors.Warning, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        p.risks.forEach { TechLabel("⚠ $it") }
                    }
                    if (p.commonDefects.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Defectos comunes:", color = ForgeColors.Error, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        p.commonDefects.forEach { TechLabel("• $it") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

// ─────────── Shared composables ───────────

@Composable
private fun TopBar(onBack: () -> Unit, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(
                containerColor = ForgeColors.SurfaceVariant,
                contentColor = ForgeColors.OnSurface
            )
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("BACK")
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Center(text: String, color: Color) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun Tag(label: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}