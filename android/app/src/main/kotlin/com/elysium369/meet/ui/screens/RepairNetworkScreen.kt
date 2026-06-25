package com.elysium369.meet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.data.local.MechanicalKnowledgeBundle
import com.elysium369.meet.ui.RepairNetworkViewModel
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.components.neonGlow
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepairNetworkScreen(navController: NavController, viewModel: RepairNetworkViewModel) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val casesList by viewModel.casesList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val knowledgeBundle by viewModel.knowledgeBundle.collectAsState()
    
    // Local filter display state
    var showFilters by remember { mutableStateOf(false) }
    val makeFilter by viewModel.makeFilter.collectAsState()
    val modelFilter by viewModel.modelFilter.collectAsState()
    val dtcFilter by viewModel.dtcFilter.collectAsState()
    val countryFilter by viewModel.countryFilter.collectAsState()
    val sortByFilter by viewModel.sortByFilter.collectAsState()
    val onlyVerifiedFilter by viewModel.onlyVerifiedFilter.collectAsState()

    Scaffold(
        containerColor = MeetColors.backgroundDark,
        topBar = {
            EliteTopAppBar(
                title = "MEET REPAIR NETWORK\nStackOverflow Mecánico",
                onBackClick = { navController.popBackStack() },
                backgroundColor = MeetColors.backgroundDark,
                actions = {
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "Filtros",
                            tint = if (showFilters) MeetColors.neonGreen else Color.White
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("contribute_case") },
                containerColor = MeetColors.backgroundDark,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .border(1.dp, MeetColors.neonGreen, RoundedCornerShape(12.dp))
                    .neonGlow(MeetColors.neonGreen, RoundedCornerShape(12.dp), minElevation = 4f, maxElevation = 12f)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Contribuir Caso", tint = MeetColors.neonGreen)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Search Input Header
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Buscar DTC, Síntomas, Repuestos o Marcas...", color = MeetColors.textSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar", tint = MeetColors.neonGreen) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = MeetColors.textSecondary)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MeetColors.neonGreen,
                    unfocusedBorderColor = MeetColors.neonGreen.copy(alpha = 0.3f),
                    focusedContainerColor = MeetColors.backgroundDeep,
                    unfocusedContainerColor = MeetColors.backgroundDeep
                ),
                shape = RoundedCornerShape(12.dp)
            )

            RepairNetworkWorkflowGuide(
                currentQuery = searchQuery,
                onQuickSearch = { viewModel.setSearchQuery(it) },
                onContribute = { navController.navigate("contribute_case") },
                onOpenMarketplace = { navController.navigate("marketplace") }
            )

            if (searchQuery.isNotBlank() || dtcFilter.isNotBlank()) {
                RepairKnowledgePanel(bundle = knowledgeBundle)
            }

            // Advanced Filters Panel (Expandable)
            if (showFilters) {
                EliteCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = MeetColors.cyberCyan,
                    backgroundColor = MeetColors.backgroundDeep,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "FILTROS AVANZADOS DE BÚSQUEDA",
                            color = MeetColors.cyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = makeFilter,
                                onValueChange = { viewModel.setMakeFilter(it) },
                                label = { Text("Marca", fontSize = 10.sp, color = MeetColors.textSecondary) },
                                modifier = Modifier.weight(1f),
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.cyberCyan)
                            )
                            OutlinedTextField(
                                value = modelFilter,
                                onValueChange = { viewModel.setModelFilter(it) },
                                label = { Text("Modelo", fontSize = 10.sp, color = MeetColors.textSecondary) },
                                modifier = Modifier.weight(1f),
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.cyberCyan)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = dtcFilter,
                                onValueChange = { viewModel.setDtcFilter(it) },
                                label = { Text("Código DTC", fontSize = 10.sp, color = MeetColors.textSecondary) },
                                modifier = Modifier.weight(1f),
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.cyberCyan)
                            )
                            OutlinedTextField(
                                value = countryFilter,
                                onValueChange = { viewModel.setCountryFilter(it) },
                                label = { Text("País", fontSize = 10.sp, color = MeetColors.textSecondary) },
                                modifier = Modifier.weight(1f),
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MeetColors.cyberCyan)
                            )
                        }

                        // Order & Verified filters
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = onlyVerifiedFilter,
                                    onCheckedChange = { viewModel.setOnlyVerifiedFilter(it) },
                                    colors = CheckboxDefaults.colors(checkedColor = MeetColors.cyberCyan)
                                )
                                Text("Solo Verificados", color = Color.White, fontSize = 12.sp)
                            }
                            
                            // Simple sort selector
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MeetColors.cyberCyan.copy(alpha = 0.15f))
                                    .clickable {
                                        val nextSort = if (sortByFilter == "votes") "success_rate" else if (sortByFilter == "success_rate") "date" else "votes"
                                        viewModel.setSortByFilter(nextSort)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ORDEN: " + when(sortByFilter) {
                                        "success_rate" -> "ÉXITO"
                                        "date" -> "FECHA"
                                        else -> "VOTOS"
                                    },
                                    color = MeetColors.cyberCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Results List
            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MeetColors.neonGreen)
                }
            } else if (casesList.isEmpty()) {
                RepairNetworkEmptyState(
                    modifier = Modifier.weight(1f),
                    hasQuery = searchQuery.isNotBlank() || dtcFilter.isNotBlank() || makeFilter.isNotBlank() || modelFilter.isNotBlank() || countryFilter.isNotBlank(),
                    onClearFilters = { viewModel.clearFilters() },
                    onContribute = { navController.navigate("contribute_case") },
                    onOpenScanner = { navController.navigate("scanner") }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(casesList) { case ->
                        EliteCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectCase(case.id)
                                    navController.navigate("repair_case_detail/${case.id}")
                                },
                            glowColor = if (case.verified) MeetColors.neonGreen else MeetColors.electricBlue,
                            backgroundColor = MeetColors.backgroundDeep,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = case.dtc_code.uppercase(),
                                        color = if (case.verified) MeetColors.neonGreen else MeetColors.electricBlue,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (case.verified) {
                                            Text("✔️ VERIFICADO", color = MeetColors.neonGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Icon(Icons.Default.ThumbUp, contentDescription = "Votos", tint = MeetColors.textSecondary, modifier = Modifier.size(12.dp))
                                        Text(text = case.votes.toString(), color = Color.White, fontSize = 11.sp)
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${case.vehicle_make} ${case.vehicle_model} (${case.year}) — ${case.engine}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Síntomas: ${case.symptoms}",
                                    color = MeetColors.textSecondary,
                                    maxLines = 2,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Costo: $${case.cost.toInt()} USD",
                                        color = MeetColors.cyberCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Éxito: ${case.success_rate.toInt()}%",
                                        color = MeetColors.neonGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "📍 ${case.country}",
                                        color = MeetColors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepairNetworkWorkflowGuide(
    currentQuery: String,
    onQuickSearch: (String) -> Unit,
    onContribute: () -> Unit,
    onOpenMarketplace: () -> Unit
) {
    EliteCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = MeetColors.neonGreen,
        backgroundColor = MeetColors.backgroundDeep,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "FLUJO CLIENTE → TALLER → CASO VERIFICADO",
                color = MeetColors.neonGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
            Text(
                "Busca por síntoma, DTC o componente. Si ya tienes evidencia, publícala como caso; si necesitas cotización, abre una solicitud a talleres.",
                color = MeetColors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionPill("Fuga de aceite", isActive = currentQuery == "fuga de aceite", modifier = Modifier.weight(1f)) { onQuickSearch("fuga de aceite") }
                QuickActionPill("Alternador", isActive = currentQuery == "alternador no carga", modifier = Modifier.weight(1f)) { onQuickSearch("alternador no carga") }
                QuickActionPill("Arranque lento", isActive = currentQuery == "arranque lento", modifier = Modifier.weight(1f)) { onQuickSearch("arranque lento") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EliteButton(
                    text = "PUBLICAR CASO",
                    onClick = onContribute,
                    color = MeetColors.neonGreen,
                    modifier = Modifier.weight(1f)
                )
                EliteButton(
                    text = "PEDIR COTIZACIÓN",
                    onClick = onOpenMarketplace,
                    color = MeetColors.electricBlue,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QuickActionPill(text: String, isActive: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (isActive) MeetColors.neonGreen.copy(alpha = 0.16f) else MeetColors.backgroundDark)
            .border(1.dp, if (isActive) MeetColors.neonGreen else MeetColors.borderSubtle, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (isActive) Color.White else MeetColors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RepairKnowledgePanel(bundle: MechanicalKnowledgeBundle) {
    val cards = buildList {
        bundle.symptomGuides.firstOrNull()?.let {
            add(Triple("GUÍA POR SÍNTOMA", it.title, firstPayloadHint(it.payloadJson, listOf("first_checks", "diagnostic_tree"))))
        }
        bundle.procedures.firstOrNull()?.let {
            add(Triple("PROCEDIMIENTO", it.title, firstPayloadHint(it.payloadJson, listOf("removal_steps", "installation_steps", "post_install_tests"))))
        }
        bundle.rebuildGuides.firstOrNull()?.let {
            add(Triple("RECONSTRUCCIÓN", it.componentId.replace('_', ' ').uppercase(), firstPayloadHint(it.payloadJson, listOf("bench_tests", "failure_signatures", "rebuild_steps"))))
        }
        bundle.trenchKnowledge.firstOrNull()?.let {
            add(Triple("TACTICA DE TALLER", it.title, firstPayloadHint(it.payloadJson, listOf("escalation_ladder", "thread_repair_options"))))
        }
        bundle.chemicals.firstOrNull()?.let {
            add(Triple("QUÍMICA AUTOMOTRIZ", it.name, firstPayloadHint(it.payloadJson, listOf("use_cases", "safe_materials", "unsafe_materials"))))
        }
        bundle.matrixLinks.firstOrNull()?.let {
            add(Triple("MATRIZ DTC + COMPONENTE", it.componentName ?: (it.dtcCode ?: "Sin componente"), firstMatrixHint(it.layerDiagnosticsJson, it.layerTrenchKnowledgeJson)))
        }
    }

    if (cards.isEmpty()) return

    EliteCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = MeetColors.cyberCyan,
        backgroundColor = MeetColors.backgroundDeep,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "INTELIGENCIA OFFLINE MEET",
                color = MeetColors.cyberCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
            cards.forEach { (label, title, hint) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MeetColors.backgroundDark, RoundedCornerShape(10.dp))
                        .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(label, color = MeetColors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    if (hint.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(hint, color = MeetColors.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RepairNetworkEmptyState(
    modifier: Modifier = Modifier,
    hasQuery: Boolean,
    onClearFilters: () -> Unit,
    onContribute: () -> Unit,
    onOpenScanner: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        EliteCard(
            modifier = Modifier.fillMaxWidth(),
            glowColor = MeetColors.warning,
            backgroundColor = MeetColors.backgroundDeep,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "AÚN NO HAY UN CASO COMUNITARIO QUE COINCIDA",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Puedes afinar la búsqueda, capturar evidencia desde el escáner o sembrar el primer caso verificado para la comunidad.",
                    color = MeetColors.textSecondary,
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                if (hasQuery) {
                    EliteButton(
                        text = "LIMPIAR FILTROS",
                        onClick = onClearFilters,
                        color = MeetColors.electricBlue,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                EliteButton(
                    text = "ABRIR ESCÁNER",
                    onClick = onOpenScanner,
                    color = MeetColors.neonGreen,
                    modifier = Modifier.fillMaxWidth()
                )
                EliteButton(
                    text = "PUBLICAR CASO",
                    onClick = onContribute,
                    color = MeetColors.warning,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun firstMatrixHint(diagnosticsJson: String, trenchJson: String): String {
    val diag = firstPayloadHint(diagnosticsJson, listOf("diagnostic_steps", "confirmation_tests", "related_symptom_guides"))
    if (diag.isNotBlank()) return diag
    return firstPayloadHint(trenchJson, listOf("common_shop_notes", "related_trench_knowledge"))
}

private fun firstPayloadHint(payloadJson: String, keys: List<String>): String {
    return runCatching {
        val obj = JSONObject(payloadJson)
        keys.firstNotNullOfOrNull { key ->
            when (val value = obj.opt(key)) {
                is JSONArray -> value.optString(0).takeIf { it.isNotBlank() }
                is String -> value.takeIf { it.isNotBlank() }
                is JSONObject -> value.keys().asSequence().firstOrNull()?.let { childKey ->
                    value.opt(childKey)?.toString()?.takeIf { it.isNotBlank() }?.let { "$childKey: $it" }
                }
                else -> null
            }
        } ?: ""
    }.getOrDefault("")
}
