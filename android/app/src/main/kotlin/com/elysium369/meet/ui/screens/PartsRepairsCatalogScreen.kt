package com.elysium369.meet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.core.catalog.CatalogPart
import com.elysium369.meet.core.catalog.RepairProcedure
import com.elysium369.meet.core.catalog.RepairProgress
import com.elysium369.meet.core.catalog.RepairProgressEngine
import com.elysium369.meet.core.catalog.RepairProgressStore
import com.elysium369.meet.core.catalog.UniversalPartsCatalogEngine
import com.elysium369.meet.core.catalog.UniversalPartsCatalogRepository
import com.elysium369.meet.ui.theme.MeetColors

@Composable
fun PartsRepairsCatalogScreen(
    navController: NavController,
    initialPartId: String? = null
) {
    val context = LocalContext.current
    val packResult = remember(context) { runCatching { UniversalPartsCatalogRepository(context).load() } }
    val pack = packResult.getOrNull()
    val progressStore = remember(context) { RepairProgressStore(context) }
    var query by remember { mutableStateOf("") }
    var selectedSystem by remember { mutableStateOf<String?>(null) }
    var selectedPart by remember(pack, initialPartId) {
        mutableStateOf(pack?.parts?.firstOrNull { it.id == initialPartId })
    }
    var selectedProcedure by remember { mutableStateOf<RepairProcedure?>(null) }
    var progress by remember { mutableStateOf<RepairProgress?>(null) }
    var evidenceIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var gateMessage by remember { mutableStateOf<String?>(null) }
    var showGuidedPilot by remember { mutableStateOf(false) }

    Surface(color = MeetColors.backgroundDeep, modifier = Modifier.fillMaxSize()) {
        if (!showGuidedPilot) {
            ProprietaryPartsBrowser(
                navController = navController,
                initialPartId = initialPartId,
                onOpenGuidedPilot = { showGuidedPilot = true }
            )
        } else Column(Modifier.fillMaxSize()) {
            CatalogTopBar(
                title = selectedProcedure?.title ?: selectedPart?.nameEs ?: "Piezas y reparaciones",
                subtitle = when {
                    selectedProcedure != null -> "Entrenamiento · revisión técnica requerida"
                    selectedPart != null -> "Fuente y compatibilidad verificables"
                    else -> pack?.let { "${it.parts.size} piezas · ${it.procedures.size} procedimientos" } ?: "Cargando catálogo"
                },
                onBack = {
                    when {
                        selectedProcedure != null -> {
                            selectedProcedure = null
                            progress = null
                            evidenceIds = emptySet()
                            gateMessage = null
                        }
                        selectedPart != null -> selectedPart = null
                        else -> showGuidedPilot = false
                    }
                }
            )

            when {
                packResult.isFailure -> CatalogFailure(packResult.exceptionOrNull()?.message.orEmpty())
                pack == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Cargando catálogo...", color = MeetColors.textSecondary)
                }
                selectedProcedure != null && progress != null -> ProcedurePlayer(
                    procedure = selectedProcedure!!,
                    progress = progress!!,
                    evidenceIds = evidenceIds,
                    gateMessage = gateMessage,
                    onToggleEvidence = { evidence ->
                        evidenceIds = if (evidence in evidenceIds) evidenceIds - evidence else evidenceIds + evidence
                        gateMessage = null
                    },
                    onToggleStep = { stepId ->
                        val transition = RepairProgressEngine.toggleStep(
                            progress = progress!!,
                            procedure = selectedProcedure!!,
                            stepId = stepId,
                            evidenceIds = evidenceIds,
                            hasVerifiedTechnicalClaim = false
                        )
                        progress = transition.progress
                        gateMessage = transition.reason
                        progressStore.save(transition.progress)
                    },
                    onOpen3d = { nodeId -> navController.navigate("component_locator?partId=$nodeId") }
                )
                selectedPart != null -> PartDetail(
                    part = selectedPart!!,
                    procedures = pack.procedures.filter { selectedPart!!.id in it.targetPartIds },
                    onOpen3d = { navController.navigate("component_locator?partId=${selectedPart!!.id}") },
                    onOpenProcedure = { procedure ->
                        selectedProcedure = procedure
                        progress = progressStore.load(procedure.id, pack.packVersion)
                        evidenceIds = emptySet()
                        gateMessage = null
                    }
                )
                else -> CatalogBrowser(
                    parts = pack.parts,
                    query = query,
                    selectedSystem = selectedSystem,
                    onQueryChanged = { query = it },
                    onSystemChanged = { selectedSystem = it },
                    onPartSelected = { selectedPart = it }
                )
            }
        }
    }
}

@Composable
private fun CatalogTopBar(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = MeetColors.cyberCyan, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.border(1.dp, MeetColors.warning, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 4.dp)
        ) {
            Text("REVIEW", color = MeetColors.warning, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
    }
    HorizontalDivider(color = MeetColors.borderSubtle)
}

@Composable
private fun CatalogBrowser(
    parts: List<CatalogPart>,
    query: String,
    selectedSystem: String?,
    onQueryChanged: (String) -> Unit,
    onSystemChanged: (String?) -> Unit,
    onPartSelected: (CatalogPart) -> Unit
) {
    val systems = remember(parts) { parts.map { it.system }.distinct().sorted() }
    val filtered = remember(parts, query, selectedSystem) {
        UniversalPartsCatalogEngine.search(parts, query).filter { selectedSystem == null || it.system == selectedSystem }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (query.isNotBlank()) {{
                IconButton(onClick = { onQueryChanged("") }) { Icon(Icons.Default.Close, contentDescription = "Limpiar") }
            }} else null,
            placeholder = { Text("Tijereta, trapecio, ABS, freno...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SystemChip("Todos", selectedSystem == null) { onSystemChanged(null) }
            systems.forEach { system -> SystemChip(system, selectedSystem == system) { onSystemChanged(system) } }
        }
        Text("${filtered.size} resultados", color = MeetColors.textMuted, fontSize = 10.sp, modifier = Modifier.padding(12.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered, key = { it.id }) { part ->
                Column(
                    Modifier.fillMaxWidth().clickable { onPartSelected(part) }.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(part.nameEs, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("${part.system} / ${part.subsystem}", color = MeetColors.textSecondary, fontSize = 10.sp)
                        }
                        Text("?", color = MeetColors.warning, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                    if (part.aliases.isNotEmpty()) {
                        Text(part.aliases.take(3).joinToString(" · "), color = MeetColors.textMuted, fontSize = 9.sp, maxLines = 1)
                    }
                }
                HorizontalDivider(color = MeetColors.borderSubtle.copy(alpha = 0.55f))
            }
        }
    }
}

@Composable
private fun SystemChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .border(1.dp, if (selected) MeetColors.cyberCyan else MeetColors.borderSubtle, RoundedCornerShape(6.dp))
            .background(if (selected) MeetColors.cyberCyan.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(label, color = if (selected) MeetColors.cyberCyan else MeetColors.textSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun PartDetail(
    part: CatalogPart,
    procedures: List<RepairProcedure>,
    onOpen3d: () -> Unit,
    onOpenProcedure: (RepairProcedure) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("${part.system} / ${part.subsystem} / ${part.assembly}", color = MeetColors.cyberCyan, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            Text(part.description, color = MeetColors.textSecondary, fontSize = 13.sp)
        }
        item {
            Column(Modifier.border(1.dp, MeetColors.warning.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MeetColors.warning, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("COMPATIBILIDAD REQUIERE VERIFICACIÓN", color = MeetColors.warning, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(part.compatibilityMessage, color = MeetColors.textSecondary, fontSize = 11.sp)
                Text("OEM, torque, material y dimensiones: no confirmados.", color = MeetColors.textMuted, fontSize = 10.sp)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpen3d, colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan, contentColor = Color.Black)) {
                    Text("Ver en 3D", fontWeight = FontWeight.Bold)
                }
            }
            Text("Esquema genérico, no dimensional ni OEM.", color = MeetColors.textMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
        }
        item { Text("Fuente", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        items(part.sourceRefs, key = { "${it.sourceDocumentSha256}:${it.sourceBlockId}" }) { source ->
            Column(Modifier.border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(6.dp)).padding(10.dp)) {
                Text("${source.sourceFileName} · ${source.sourceBlockId}", color = MeetColors.cyberCyan, fontSize = 10.sp)
                Text("SHA-256 ${source.sourceDocumentSha256.take(16)}...", color = MeetColors.textMuted, fontSize = 9.sp)
                Text(source.reviewStatus, color = MeetColors.warning, fontSize = 9.sp)
            }
        }
        item { Text("Procedimientos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        if (procedures.isEmpty()) {
            item { Text("No hay procedimiento revisable para esta pieza.", color = MeetColors.textMuted, fontSize = 11.sp) }
        } else {
            items(procedures, key = { it.id }) { procedure ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenProcedure(procedure) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(procedure.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("${procedure.steps.size} pasos · ${procedure.executionPolicy}", color = MeetColors.textMuted, fontSize = 9.sp)
                    }
                    Text("Abrir", color = MeetColors.neonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = MeetColors.borderSubtle)
            }
        }
    }
}

@Composable
private fun ProcedurePlayer(
    procedure: RepairProcedure,
    progress: RepairProgress,
    evidenceIds: Set<String>,
    gateMessage: String?,
    onToggleEvidence: (String) -> Unit,
    onToggleStep: (String) -> Unit,
    onOpen3d: (String) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Estado: ${progress.state} · ${progress.completedStepIds.size}/${procedure.steps.size}", color = MeetColors.neonGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("No certifica una reparación real. Cada gate conserva su evidencia.", color = MeetColors.textMuted, fontSize = 10.sp)
        }
        gateMessage?.let { message ->
            item {
                Row(Modifier.border(1.dp, MeetColors.error, RoundedCornerShape(6.dp)).padding(10.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MeetColors.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(message, color = MeetColors.error, fontSize = 10.sp)
                }
            }
        }
        items(procedure.steps, key = { it.id }) { step ->
            val completed = step.id in progress.completedStepIds
            val blocked = progress.blockedStepId == step.id
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, when { blocked -> MeetColors.error; completed -> MeetColors.neonGreen; else -> MeetColors.borderSubtle }, RoundedCornerShape(6.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${step.order}", color = MeetColors.cyberCyan, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    Spacer(Modifier.size(8.dp))
                    Text(step.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    if (completed) Icon(Icons.Default.Check, contentDescription = "Completado", tint = MeetColors.neonGreen)
                }
                Spacer(Modifier.height(6.dp))
                Text(step.instruction, color = MeetColors.textSecondary, fontSize = 11.sp)
                step.warning?.let { Text(it, color = MeetColors.warning, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp)) }
                if (step.completionGate == "VERIFIED_TORQUE_REQUIRED") {
                    Text(step.technicalValueMessage ?: "No confirmado para esta variante", color = MeetColors.warning, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                }
                step.requiredEvidence.forEach { evidence ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = evidence in evidenceIds, onCheckedChange = { onToggleEvidence(evidence) })
                        Text(evidence, color = MeetColors.textSecondary, fontSize = 10.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { onOpen3d(step.targetNodeId) }) { Text("3D") }
                    Button(
                        onClick = { onToggleStep(step.id) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (completed) MeetColors.cardBackground else MeetColors.neonGreen,
                            contentColor = if (completed) Color.White else Color.Black
                        )
                    ) {
                        Text(if (completed) "Reabrir" else "Completar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogFailure(message: String) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = MeetColors.error, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(10.dp))
        Text("El catálogo no superó la validación", color = Color.White, fontWeight = FontWeight.Bold)
        Text(message.ifBlank { "Error desconocido" }, color = MeetColors.error, fontSize = 10.sp)
    }
}
