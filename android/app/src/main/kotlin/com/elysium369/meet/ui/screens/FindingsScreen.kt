package com.elysium369.meet.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.domain.diagnostics.DiagnosticFindingSummary
import com.elysium369.meet.data.local.entities.DtcDefinitionEntity
import com.elysium369.meet.ui.components.*
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindingsScreen(
    navController: NavController,
    viewModel: ObdViewModel
) {
    val activeVehicle by viewModel.selectedVehicle.collectAsState()
    val activeEvents by viewModel.canonicalActiveFindingSummaries.collectAsState()
    val pendingEvents by viewModel.canonicalPendingFindingSummaries.collectAsState()
    val permanentEvents by viewModel.canonicalPermanentFindingSummaries.collectAsState()
    val historicalEvents by viewModel.canonicalHistoricalFindingSummaries.collectAsState()

    // Combine all events
    val allEvents = remember(activeEvents, pendingEvents, permanentEvents, historicalEvents) {
        activeEvents + pendingEvents + permanentEvents + historicalEvents
    }

    var selectedFilter by remember { mutableStateOf("TODOS") }

    val filteredEvents = remember(allEvents, selectedFilter) {
        when (selectedFilter) {
            "ACTIVOS" -> allEvents.filter { it.status == "ACTIVE" }
            "PENDIENTES" -> allEvents.filter { it.status == "PENDING" }
            "HISTÓRICOS" -> allEvents.filter { it.status in setOf("HISTORY", "INTERMITTENT", "PERMANENT") }
            else -> allEvents
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            EliteTopAppBar(
                title = "HALLAZGOS DIAGNÓSTICOS",
                subtitle = activeVehicle?.let { "${it.make} ${it.model} (${it.year})" } ?: "Vehículo Genérico",
                onBackClick = { navController.popBackStack() }
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // ═══════════ OVERVIEW CARD ═══════════
            EliteCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = MeetColors.cyberCyan,
                enableHolo3D = true
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "RESUMEN DE SALUD ELECTRÓNICA",
                        color = MeetColors.cyberCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${allEvents.size} CÓDIGOS",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Detectados en el último escaneo",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp
                            )
                        }

                        // Mini summary pills
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SummaryPill(count = activeEvents.size, label = "Activos", color = MeetColors.error)
                            SummaryPill(count = pendingEvents.size, label = "Pend.", color = MeetColors.warning)
                            SummaryPill(count = (permanentEvents.size + historicalEvents.size), label = "Hist.", color = MeetColors.electricBlue)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ═══════════ FILTER CHIPS ═══════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = selectedFilter == "TODOS", label = "Todos (${allEvents.size})", onClick = { selectedFilter = "TODOS" })
                FilterChip(selected = selectedFilter == "ACTIVOS", label = "Activos (${activeEvents.size})", color = MeetColors.error, onClick = { selectedFilter = "ACTIVOS" })
                FilterChip(selected = selectedFilter == "PENDIENTES", label = "Pendientes (${pendingEvents.size})", color = MeetColors.warning, onClick = { selectedFilter = "PENDIENTES" })
                FilterChip(selected = selectedFilter == "HISTÓRICOS", label = "Historial (${permanentEvents.size + historicalEvents.size})", color = MeetColors.electricBlue, onClick = { selectedFilter = "HISTÓRICOS" })
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ═══════════ LIST OF FINDINGS ═══════════
            if (filteredEvents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedNeonGlyph("🔍", contentDescription = null, fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No se encontraron hallazgos en este filtro.",
                            color = MeetColors.textSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredEvents) { event ->
                        FindingItemCard(
                            event = event,
                            viewModel = viewModel,
                            navController = navController
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryPill(
    count: Int,
    label: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = count.toString(),
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = label.uppercase(),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun FilterChip(
    selected: Boolean,
    label: String,
    color: Color = MeetColors.cyberCyan,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) color.copy(alpha = 0.15f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) color else MeetColors.borderSubtle,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else MeetColors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun FindingItemCard(
    event: DiagnosticFindingSummary,
    viewModel: ObdViewModel,
    navController: NavController
) {
    var isExpanded by remember { mutableStateOf(false) }
    var definition by remember { mutableStateOf<DtcDefinitionEntity?>(null) }

    LaunchedEffect(event.code) {
        definition = viewModel.getDtcDefinition(event.code)
    }

    val statusColor = when (event.status) {
        "ACTIVE" -> MeetColors.error
        "PENDING" -> MeetColors.warning
        else -> MeetColors.electricBlue
    }

    val severityColor = when (definition?.severity?.uppercase()) {
        "HIGH", "CRITICAL" -> MeetColors.error
        "MEDIUM", "WARNING" -> MeetColors.warning
        else -> MeetColors.neonGreen
    }

    EliteCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = if (isExpanded) severityColor else null,
        borderColor = if (isExpanded) severityColor.copy(alpha = 0.4f) else MeetColors.borderSubtle,
        backgroundColor = MeetColors.cardBackground,
        enableHolo3D = false
    ) {
        Column(modifier = Modifier.clickable { isExpanded = !isExpanded }.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    // Code
                    Text(
                        text = event.code,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(10.dp))

                    // Status pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusColor.copy(alpha = 0.12f))
                            .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = when (event.status) {
                                "ACTIVE" -> "ACTIVO"
                                "PENDING" -> "PENDIENTE"
                                else -> "HISTÓRICOS"
                            },
                            color = statusColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Expand/Collapse icon
                AnimatedNeonIcon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.ArrowDropDown,
                    contentDescription = "Toggle",
                    tint = MeetColors.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Short Description
            val desc = definition?.descriptionEs ?: event.description
            Text(
                text = desc,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = if (isExpanded) 10 else 2
            )

            // Expanded Details Area
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Divider(color = MeetColors.borderSubtle, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // System / Subsystem
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DetailItem(label = "Sistema", value = definition?.system ?: "Motor", modifier = Modifier.weight(1f))
                        val subSystem = definition?.subSystem
                        if (!subSystem.isNullOrEmpty() && subSystem != "null") {
                            DetailItem(label = "Subsistema", value = subSystem, modifier = Modifier.weight(1f))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Severity & Urgency
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DetailItem(
                            label = "Severidad",
                            value = definition?.severity ?: "MEDIA",
                            valueColor = severityColor,
                            modifier = Modifier.weight(1f)
                        )
                        DetailItem(
                            label = "Urgencia",
                            value = definition?.urgency ?: "MODERADA",
                            valueColor = severityColor,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Symptoms
                    val symptomsList = remember(definition) { parseJsonArray(definition?.symptoms) }
                    if (symptomsList.isNotEmpty()) {
                        BulletSection("Síntomas Clave", symptomsList)
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Possible Causes
                    val causesList = remember(definition) {
                        val pc = definition?.possibleCauses
                        if (!pc.isNullOrEmpty()) {
                            pc.split(Regex("[|]")).map { it.trim() }.filter { it.isNotEmpty() }
                        } else {
                            emptyList()
                        }
                    }
                    if (causesList.isNotEmpty()) {
                        BulletSection("Posibles Causas", causesList)
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Cascade Risk
                    val cascadeRisk = remember(definition) { parseCascadeRisk(definition?.cascadeRisk) }
                    if (cascadeRisk != null) {
                        val riskColor = when (cascadeRisk.level.uppercase()) {
                            "HIGH", "CRITICAL" -> MeetColors.error
                            "MEDIUM" -> MeetColors.warning
                            else -> MeetColors.neonGreen
                        }
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Riesgo en Cascada (Daño Secundario)",
                                color = MeetColors.cyberCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(riskColor.copy(alpha = 0.12f))
                                        .border(1.dp, riskColor, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        cascadeRisk.level,
                                        color = riskColor,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    cascadeRisk.description,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Estimated cost & complexity
                    val costEstimate = remember(definition) { parseCostEstimate(definition?.repairCostUSD) }
                    if (costEstimate != null || !definition?.repairComplexity.isNullOrEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val complexity = definition?.repairComplexity
                            if (!complexity.isNullOrEmpty() && complexity != "null") {
                                DetailItem(
                                    label = "Complejidad Reparación",
                                    value = when (complexity.uppercase()) {
                                        "BASIC" -> "BÁSICA"
                                        "MODERATE" -> "MODERADA"
                                        "ADVANCED" -> "AVANZADA"
                                        else -> complexity
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (costEstimate != null && costEstimate.max > 0) {
                                DetailItem(
                                    label = "Costo Estimado (Repuestos)",
                                    value = "$${costEstimate.min} - $${costEstimate.max} USD",
                                    valueColor = MeetColors.neonGreen,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        EliteOutlinedButton(
                            text = "PREGUNTAR IA",
                            onClick = { navController.navigate("ai/${event.code}") },
                            color = MeetColors.electricBlue,
                            modifier = Modifier.weight(1f)
                        )
                        EliteButton(
                            text = "VER GUÍA",
                            onClick = {
                                navController.navigate(
                                    "repair/${event.code}?findingId=${java.net.URLEncoder.encode(event.id, "UTF-8")}",
                                )
                            },
                            color = MeetColors.neonGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailItem(
    label: String,
    value: String,
    valueColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(
            text = label.uppercase(),
            color = MeetColors.textSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun BulletSection(
    title: String,
    items: List<String>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            color = MeetColors.cyberCyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "•",
                    color = MeetColors.cyberCyan,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = item,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

// Helpers
private fun parseJsonArray(jsonStr: String?): List<String> {
    if (jsonStr.isNullOrEmpty()) return emptyList()
    return try {
        if (jsonStr.trim().startsWith("[")) {
            val arr = org.json.JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            list
        } else {
            jsonStr.split(Regex("[,|\n]")).map { it.trim() }.filter { it.isNotEmpty() }
        }
    } catch (e: Exception) {
        listOf(jsonStr)
    }
}

private data class CostEstimate(val min: Int, val max: Int, val note: String)

private fun parseCostEstimate(jsonStr: String?): CostEstimate? {
    if (jsonStr.isNullOrEmpty()) return null
    return try {
        if (jsonStr.trim().startsWith("{")) {
            val obj = org.json.JSONObject(jsonStr)
            CostEstimate(
                min = obj.optInt("minUSD", 0),
                max = obj.optInt("maxUSD", 0),
                note = obj.optString("note", "")
            )
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

private data class CascadeRiskInfo(val level: String, val description: String)

private fun parseCascadeRisk(jsonStr: String?): CascadeRiskInfo? {
    if (jsonStr.isNullOrEmpty()) return null
    return try {
        if (jsonStr.trim().startsWith("{")) {
            val obj = org.json.JSONObject(jsonStr)
            CascadeRiskInfo(
                level = obj.optString("level", "LOW"),
                description = obj.optString("description", "")
            )
        } else {
            CascadeRiskInfo("UNKNOWN", jsonStr)
        }
    } catch (e: Exception) {
        null
    }
}
