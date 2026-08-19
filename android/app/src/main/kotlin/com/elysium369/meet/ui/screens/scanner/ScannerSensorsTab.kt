package com.elysium369.meet.ui.screens.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import com.elysium369.meet.core.obd.PidRegistry
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.components.EliteScrollContainer
import com.elysium369.meet.ui.components.WaveGraphWidget
import com.elysium369.meet.ui.components.eliteScrollbar
import com.elysium369.meet.ui.components.neonGlow

/**
 * Converts special calculated sensor codes to human-readable descriptive text.
 */
private fun getDescriptiveValue(pid: String, value: Float): String? {
    return when (pid) {
        "CALC_FUEL_STATUS_CODE" -> when (value.toInt()) {
            1 -> "Open loop, cold engine"
            2 -> "Closed loop, using O2 sensor feedback"
            4 -> "Open loop, driving conditions"
            8 -> "Open loop, system fault"
            16 -> "Closed loop, fault detected"
            else -> "Desconocido (${"%.0f".format(value)})"
        }
        "CALC_OBD_STANDARD" -> when (value.toInt()) {
            1 -> "OBD-II (CARB)"
            2 -> "OBD (EPA)"
            3 -> "OBD and OBD-II"
            4 -> "OBD-I"
            5 -> "No OBD"
            6 -> "EOBD"
            7 -> "EOBD and OBD-II"
            8 -> "EOBD and OBD"
            9 -> "EOBD, OBD and OBD-II"
            11 -> "JOBD"
            12 -> "JOBD and OBD-II"
            13 -> "JOBD and EOBD"
            14 -> "Euro IV B1"
            15 -> "Euro V B2"
            16 -> "Euro C"
            17 -> "EMD"
            else -> "Estándar #${"%.0f".format(value)}"
        }
        "CALC_MIL_STATUS" -> if (value > 0.5f) "MIL:ON ⚠️" else "MIL:OFF ✅"
        "CALC_FUEL_ECON" -> if (value > 0.5f) "Eco: Activo ✅" else "Eco: Inactivo"
        "CALC_CURRENT_TIME" -> {
            val hours = (value / 100f).toInt()
            val mins = (value % 100f).toInt()
            "%02d:%02d".format(hours, mins)
        }
        else -> null
    }
}

@Composable
fun ScannerSensorsTab(
    viewModel: ObdViewModel,
    defaultGauges: List<GaugeConfig>
) {
    val liveData by viewModel.liveData.collectAsState()
    val pinnedPids by viewModel.pinnedPids.collectAsState()
    val telemetryHistory by viewModel.telemetryHistory.collectAsState()
    val anomalousPids by viewModel.anomalousPids.collectAsState()
    
    val gridState = rememberLazyGridState()
    var expandedPid by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Filter gauges based on search
    val filteredGauges = remember(searchQuery, defaultGauges) {
        if (searchQuery.isBlank()) defaultGauges
        else defaultGauges.filter { 
            it.label.contains(searchQuery, ignoreCase = true) || 
            it.pid.contains(searchQuery, ignoreCase = true) ||
            it.unit.contains(searchQuery, ignoreCase = true)
        }
    }
    
    EliteScrollContainer(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .eliteScrollbar(gridState),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── HEADER: Title + Reset Trip Button ──
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "TODOS LOS SENSORES", 
                        color = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.5f), 
                        style = MaterialTheme.typography.labelSmall, 
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Reset Trip Button (like Car Scanner's "Reset distance, fuel used...")
                    com.elysium369.meet.ui.components.EliteCard(
                        backgroundColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(6.dp),
                        borderColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.3f),
                        onClick = { viewModel.resetTrip() }
                    ) {
                        Text(
                            "🔄 Reset Viaje",
                            color = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            
            // ── SEARCH / FILTER BAR ──
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("🔍 Buscar sensor...", color = MeetColors.textMuted) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                        unfocusedBorderColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.2f),
                        cursorColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }
            
            // ── SENSOR COUNT ──
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "${filteredGauges.size} sensores",
                    color = MeetColors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            // ── SENSOR LIST ──
            items(filteredGauges.size, span = { index -> 
                val isExpanded = expandedPid == filteredGauges[index].pid
                if (isExpanded) GridItemSpan(maxLineSpan) else GridItemSpan(1)
            }) { index ->
                val gauge = filteredGauges[index]
                val currentValue = liveData.resolveGaugeValue(gauge.pid) ?: (liveData[gauge.pid] ?: 0f)
                val isPinned = pinnedPids.contains(gauge.pid)
                val isExpanded = expandedPid == gauge.pid
                val isAnomalous = anomalousPids.any { it.pid == gauge.pid }
                
                // Determine dynamic spring stiffness based on PID type
                // Fast-changing PIDs (RPM, Speed, Boost) get smooth low stiffness
                // Slow PIDs (Temp, Voltage, Fuel Level) get higher stiffness for quick settle
                val pidLower = gauge.pid.lowercase()
                val labelLower = gauge.label.lowercase()
                val isFastPid = pidLower.contains("010d") || pidLower.contains("010c") ||
                    labelLower.contains("rpm") || labelLower.contains("velocidad") || 
                    labelLower.contains("speed") || labelLower.contains("boost") ||
                    labelLower.contains("maf") || labelLower.contains("presión") ||
                    labelLower.contains("pressure")
                val springStiffness = if (isFastPid) 35f else 60f
                
                // Ultra-smooth animated value — sweeps through every integer
                val animatedValue by animateFloatAsState(
                    targetValue = currentValue,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = springStiffness
                    ),
                    label = "sensorAnim_${gauge.pid}"
                )
                
                // Check if this sensor has a descriptive text value
                val descriptiveText = getDescriptiveValue(gauge.pid, currentValue)
                
                com.elysium369.meet.ui.components.EliteCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(if (isExpanded) 16.dp else 8.dp),
                    backgroundColor = MeetColors.backgroundDark,
                    borderColor = if (isExpanded) com.elysium369.meet.ui.theme.MeetColors.neonGreen 
                                  else if (isPinned) com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.4f) 
                                  else com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.15f),
                    glowColor = if (isExpanded || isPinned) (if (isAnomalous) com.elysium369.meet.ui.theme.MeetColors.error else com.elysium369.meet.ui.theme.MeetColors.neonGreen) else null,
                    onClick = { expandedPid = if (isExpanded) null else gauge.pid }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) { 
                            Text(
                                gauge.label, 
                                color = Color.White, 
                                style = MaterialTheme.typography.bodyMedium, 
                                fontWeight = if (isExpanded) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                if (gauge.pid.startsWith("CALC_")) "Calculado" else "PID: ${gauge.pid}", 
                                color = MeetColors.textMuted, 
                                style = MaterialTheme.typography.labelSmall
                            ) 
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Show descriptive text OR numeric value
                            if (descriptiveText != null) {
                                Text(
                                    descriptiveText, 
                                    color = if (isExpanded) Color.White else com.elysium369.meet.ui.theme.MeetColors.neonGreen, 
                                    style = MaterialTheme.typography.bodySmall, 
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.widthIn(max = 150.dp)
                                )
                            } else {
                                Text(
                                    "${String.format("%.${if (animatedValue % 1 == 0f) "0" else "2"}f", animatedValue)} ${gauge.unit}", 
                                    color = if (isExpanded) Color.White else com.elysium369.meet.ui.theme.MeetColors.neonGreen, 
                                    style = MaterialTheme.typography.titleMedium, 
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            com.elysium369.meet.ui.components.EliteIconButton(
                                onClick = { if (isPinned) viewModel.unpinPid(gauge.pid) else viewModel.pinPid(gauge.pid) },
                                modifier = Modifier.size(24.dp),
                                icon = { Text(if (isPinned) "📌" else "📍", fontSize = 16.sp) }
                            )
                        }
                    }
                    
                    // ── EXPANDED: Full-screen style graph ──
                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                            val pidDef = PidRegistry.getPid("01", gauge.pid.removePrefix("01"))
                            
                            WaveGraphWidget(
                                label = "HISTORIAL ${gauge.label}",
                                currentValue = currentValue,
                                minVal = gauge.minVal,
                                maxVal = gauge.maxVal,
                                unit = gauge.unit,
                                warningThreshold = pidDef?.warningThreshold,
                                criticalThreshold = pidDef?.criticalThreshold,
                                isAnomaly = isAnomalous,
                                historyData = telemetryHistory[gauge.pid]
                            )
                        }
                        
                        if (!isPinned) {
                            Text(
                                "Pinna este PID para activar telemetría de alta velocidad",
                                color = MeetColors.textMuted,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                    }
                }
            }
        }
    }
}
