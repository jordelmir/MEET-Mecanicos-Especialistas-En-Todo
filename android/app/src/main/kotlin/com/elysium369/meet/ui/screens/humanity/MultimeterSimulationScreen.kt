package com.elysium369.meet.ui.screens.humanity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.humanity.EvidenceItem
import com.elysium369.meet.core.humanity.EvidenceType
import com.elysium369.meet.core.humanity.ExecutionTruthState
import java.security.MessageDigest
import java.util.UUID

enum class MultimeterDialSetting(val displayName: String) {
    OFF("OFF"),
    DC_20V("20V ⎓"),
    DC_200V("200V ⎓"),
    OHM_200("200 Ω"),
    CONTINUITY("🔊 ·>|"),
}

enum class ProbeTestPoint(val displayName: String, val voltageVsGround: Double, val resistanceVsGround: Double) {
    BATTERY_POS("+ Batería (12V)", 12.65, 0.05),
    BATTERY_NEG("- Batería (Poste)", 0.00, 0.01),
    CHASSIS_GROUND("Masa Chasis", 0.00, 0.02),
    CORRODED_GROUND("Tierra Sulfatada", 0.85, 45.0),
    SENSOR_5V_REF("Ref Sensor 5V", 5.00, 120.0),
    OPEN_WIRE("Cable Cortado", 0.00, 999999.0),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultimeterSimulationScreen(
    onBack: () -> Unit,
    onEvidenceCaptured: (EvidenceItem) -> Unit = {},
) {
    var dialSetting by remember { mutableStateOf(MultimeterDialSetting.DC_20V) }
    var redProbePoint by remember { mutableStateOf<ProbeTestPoint?>(ProbeTestPoint.BATTERY_POS) }
    var blackProbePoint by remember { mutableStateOf<ProbeTestPoint?>(ProbeTestPoint.BATTERY_NEG) }
    var lastCapturedEvidence by remember { mutableStateOf<EvidenceItem?>(null) }

    val measuredValueDisplay = remember(dialSetting, redProbePoint, blackProbePoint) {
        if (dialSetting == MultimeterDialSetting.OFF) {
            " "
        } else if (redProbePoint == null || blackProbePoint == null) {
            "0.00"
        } else {
            when (dialSetting) {
                MultimeterDialSetting.OFF -> " "
                MultimeterDialSetting.DC_20V -> {
                    val vRed = redProbePoint?.voltageVsGround ?: 0.0
                    val vBlack = blackProbePoint?.voltageVsGround ?: 0.0
                    val diff = vRed - vBlack
                    if (diff.isNaN() || diff > 20.0) "OL" else String.format("%.2f V", diff)
                }
                MultimeterDialSetting.DC_200V -> {
                    val vRed = redProbePoint?.voltageVsGround ?: 0.0
                    val vBlack = blackProbePoint?.voltageVsGround ?: 0.0
                    val diff = vRed - vBlack
                    String.format("%.1f V", diff)
                }
                MultimeterDialSetting.OHM_200 -> {
                    val r = redProbePoint?.resistanceVsGround ?: 0.0
                    if (r >= 200.0) "OL" else String.format("%.1f Ω", r)
                }
                MultimeterDialSetting.CONTINUITY -> {
                    val r = redProbePoint?.resistanceVsGround ?: 0.0
                    if (r < 5.0 && redProbePoint != ProbeTestPoint.OPEN_WIRE) "BEEP 0.01" else "OL"
                }
            }
        }
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "MULTIMETER LAB",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                            ),
                        )
                        Text(
                            text = "Simulador Determinista de Circuitos y Mediciones",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Multimeter Body
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E232A),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, Color(0xFFFFB300), RoundedCornerShape(20.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // LCD Screen
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF7FA37C))
                            .border(2.dp, Color(0xFF5A7558), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = measuredValueDisplay,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1A2619),
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Rotary Dial Selector
                    Text(
                        text = "SELECTOR ROTATIVO DE ESCALA",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFB300),
                        ),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        MultimeterDialSetting.entries.forEach { setting ->
                            val isSelected = dialSetting == setting
                            FilterChip(
                                selected = isSelected,
                                onClick = { dialSetting = setting },
                                label = { Text(setting.displayName, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFFB300),
                                    selectedLabelColor = Color.Black,
                                ),
                            )
                        }
                    }
                }
            }

            // Probe Placement Controls
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "POSICIONAMIENTO DE SONDAS",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Red Probe (Positive / Measurement)
                    Text(
                        text = "🔴 Sonda Roja (V / Ω): ${redProbePoint?.displayName ?: "Sin Conectar"}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ProbeTestPoint.entries.take(3).forEach { pt ->
                            SuggestionChip(
                                onClick = { redProbePoint = pt },
                                label = { Text(pt.displayName, fontSize = 10.sp) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ProbeTestPoint.entries.drop(3).forEach { pt ->
                            SuggestionChip(
                                onClick = { redProbePoint = pt },
                                label = { Text(pt.displayName, fontSize = 10.sp) },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Black Probe (Common / Ground)
                    Text(
                        text = "⚫ Sonda Negra (COM): ${blackProbePoint?.displayName ?: "Sin Conectar"}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(ProbeTestPoint.BATTERY_NEG, ProbeTestPoint.CHASSIS_GROUND).forEach { pt ->
                            SuggestionChip(
                                onClick = { blackProbePoint = pt },
                                label = { Text(pt.displayName, fontSize = 10.sp) },
                            )
                        }
                    }
                }
            }

            // Evidence Capture Action
            Button(
                onClick = {
                    val payload = "SIM_MEASURE|${dialSetting.name}|${redProbePoint?.name}|${blackProbePoint?.name}|$measuredValueDisplay"
                    val hash = MessageDigest.getInstance("SHA-256")
                        .digest(payload.toByteArray())
                        .joinToString("") { "%02x".format(it) }

                    val item = EvidenceItem(
                        id = "evi_sim_" + UUID.randomUUID().toString().take(8),
                        userId = "current_user",
                        skillId = "automotive.measure_voltage",
                        missionId = "mission.battery_test_multimeter",
                        evidenceType = EvidenceType.SIMULATION,
                        executionTruth = ExecutionTruthState.SIMULATED,
                        evidencePayloadHash = hash,
                        metadata = mapOf(
                            "dial" to dialSetting.name,
                            "redProbe" to (redProbePoint?.name ?: ""),
                            "blackProbe" to (blackProbePoint?.name ?: ""),
                            "value" to measuredValueDisplay,
                        ),
                    )
                    lastCapturedEvidence = item
                    onEvidenceCaptured(item)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("REGISTRAR EVIDENCIA DE SIMULACIÓN (SHA-256)")
            }

            // Captured Evidence Feedback
            lastCapturedEvidence?.let { evi ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Evidencia Registrada con Éxito",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Hash: ${evi.evidencePayloadHash.take(16)}...",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        )
                        Text(
                            text = "Estado de Verdad: ${evi.executionTruth.name}",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }
    }
}
