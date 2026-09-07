package com.elysium369.meet.education.presentation

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
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
import com.elysium369.meet.education.data.CurriculumTrack
import com.elysium369.meet.education.data.TaskType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElysiumLearningScreen(
    viewModel: ElysiumLearningViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ELYSIUM LEARNING OS",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                            ),
                        )
                        Text(
                            text = "MEP 2026 · Calendario y Programa Oficial",
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
                actions = {
                    IconButton(onClick = { viewModel.refreshFrontier() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Recargar frontera",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

            // 1. Child Privacy & Constitutional Safeguards Banner
            item {
                SafeguardsBanner()
            }

            // 2. Cycle and Track Selector
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Cycle Filter Chips
                    val cycles = listOf("Todos", "I Ciclo", "II Ciclo", "III Ciclo", "Diversificada")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(cycles) { cycle ->
                            val isSelected = cycle == state.selectedCycle
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectCycle(cycle) },
                                label = {
                                    Text(
                                        text = when (cycle) {
                                            "I Ciclo" -> "I Ciclo (1.º-3.º)"
                                            "II Ciclo" -> "II Ciclo (4.º-6.º)"
                                            "III Ciclo" -> "III Ciclo (7.º-9.º)"
                                            "Diversificada" -> "Bachillerato (BxM)"
                                            else -> "Todos los Ciclos"
                                        },
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp,
                                    )
                                },
                            )
                        }
                    }

                    // Track selector chips
                    val filteredTracks = if (state.selectedCycle == "Todos") {
                        CurriculumTrack.values().toList()
                    } else {
                        CurriculumTrack.values().filter { it.cycleName.contains(state.selectedCycle) || state.selectedCycle.contains(it.cycleName) }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(filteredTracks) { track ->
                            val isSelected = track == state.track
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectTrack(track) },
                                label = {
                                    Text(
                                        text = track.displayName,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp,
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // 3. Official MEP 2026 Academic Calendar Card
            item {
                MepCalendarRibbon(track = state.track)
            }

            // 4. Monthly Progression Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (state.track.isPrimary && state.track.subjectName == "MATEMATICA") {
                            "DISTRIBUCIÓN MENSUAL OFICIAL MEP (10 MESES)"
                        } else if (state.track.isDiversifiedOrAdult) {
                            "TEMARIOS OFICIALES DGEC (BACHILLERATO POR MADUREZ)"
                        } else {
                            "UNIDADES Y TALLERES FORMATIVOS OFICIALES"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(state.units) { unit ->
                            val isSelected = unit.id == state.selectedUnitId
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectUnit(unit.id) },
                                label = {
                                    Text(
                                        text = "${unit.monthName} (U${unit.unitNumber})",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                leadingIcon = if (unit.concepts.isNotEmpty()) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                } else null,
                            )
                        }
                    }
                }
            }

            // 5. Personal Learning Frontier Card (ZDP)
            item {
                LearningFrontierCard(
                    frontier = state.frontierConcepts,
                    selectedConceptId = state.selectedConceptId,
                    onSelectConcept = { viewModel.selectConcept(it) },
                )
            }

            // 6. Interactive Task Arena
            item {
                state.activeTask?.let { task ->
                    InteractiveTaskArena(
                        task = task,
                        state = state,
                        onSelectOption = { viewModel.selectOption(it) },
                        onAddColones = { viewModel.addColones(it) },
                        onResetColones = { viewModel.resetColones() },
                        onSubmit = { viewModel.submitAnswer() },
                    )
                } ?: run {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Selecciona una unidad con ejercicios para comenzar la práctica.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            // 7. Economic Bridge Card (for technical / vocational tracks)
            if (state.track == CurriculumTrack.FONTANERIA_7 ||
                state.track == CurriculumTrack.DIBUJO_TECNICO_8 ||
                state.track == CurriculumTrack.ELECTRICIDAD_9) {
                item {
                    EconomicBridgeCard(track = state.track)
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // Feedback & Cryptographic Proof Dialog
    if (state.isEvidenceModalVisible) {
        EvidenceProofDialog(
            isSuccess = state.feedbackSuccess == true,
            feedback = state.feedbackMessage ?: "",
            hash = state.lastEvidenceHash ?: "",
            mastery = state.currentMasteryEstimate,
            confidence = state.currentConfidence,
            isTransferUnlocked = state.isTransferUnlocked,
            onDismiss = { viewModel.dismissFeedback() },
        )
    }
}

@Composable
private fun SafeguardsBanner() {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🛡️", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "PROTECCIÓN DE DATOS DE MENORES (LEY 8968)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Identidad disociada, evidencia SHA-256 determinista. Sin venta de datos, sin algoritmos de adicción y con preservación estricta de privacidad.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun MepCalendarRibbon(track: CurriculumTrack) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (track.isDiversifiedOrAdult) {
                        "🇨🇷 DGEC BxM CONVOCATORIAS 2026"
                    } else if (track.isSecondaryBasic) {
                        "🇨🇷 CALENDARIO MEP 2026 (III CICLO)"
                    } else {
                        "🇨🇷 CALENDARIO ESCOLAR MEP 2026"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                )
                Text(
                    text = if (track.isDiversifiedOrAdult) "Ed. Abierta" else "39 Semanas",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (track.isDiversifiedOrAdult) {
                    CalendarPill(title = "Conv. 01-2026", range = "Insc: Ene · Ex: Abr", count = "Fase 1")
                    CalendarPill(title = "Pruebas", range = "Sábados y Domingos", count = "DGEC")
                    CalendarPill(title = "Conv. 02-2026", range = "Insc: Jun · Ex: Sep", count = "Fase 2")
                } else {
                    CalendarPill(title = "Periodo 1", range = "23 Feb – 3 Jul", count = "19 sem")
                    CalendarPill(title = "Receso", range = "6 Jul – 17 Jul", count = "2 sem")
                    CalendarPill(title = "Periodo 2", range = "20 Jul – 9 Dic", count = "20 sem")
                }
            }
        }
    }
}

@Composable
private fun CalendarPill(title: String, range: String, count: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = range,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
        )
        Text(
            text = count,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
private fun LearningFrontierCard(
    frontier: List<com.elysium369.meet.education.domain.FrontierConcept>,
    selectedConceptId: String?,
    onSelectConcept: (String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "🎯 FRONTERA DE APRENDIZAJE (ZDP)",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                )
                Text(
                    text = "${frontier.size} conceptos listos",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.outline,
                    ),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Conceptos accesibles hoy según tu grafo de prerequisitos satisfechos:",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (frontier.isEmpty()) {
                Text(
                    text = "¡Felicidades! Has completado los conceptos prioritarios de este ciclo.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary),
                )
            } else {
                frontier.forEach { item ->
                    val isSelected = item.conceptId == selectedConceptId
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSelectConcept(item.conceptId) },
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    )
                                    if (item.needsTransfer) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "TRANSFERENCIA",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.error,
                                            ),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${item.unitTitle} · Código: ${item.conceptCode}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.outline,
                                    ),
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${(item.currentMastery * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.currentMastery >= 0.75) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    ),
                                )
                                Text(
                                    text = "${item.evidenceCount} pruebas",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveTaskArena(
    task: com.elysium369.meet.education.data.InteractiveTaskData,
    state: ElysiumLearningUiState,
    onSelectOption: (Int) -> Unit,
    onAddColones: (Int) -> Unit,
    onResetColones: () -> Unit,
    onSubmit: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = task.title.uppercase(),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.weight(1f),
                )
                if (task.isTransferTask) {
                    AssistChip(
                        onClick = {},
                        label = { Text("DESAFÍO > 75%", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Prompt
            Text(
                text = task.prompt,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp,
                ),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Specific Task Arenas
            when (task.type) {
                TaskType.SPATIAL_PLACEMENT -> {
                    SpatialPlacementArena(
                        options = task.options,
                        selectedIndex = state.selectedOptionIndex,
                        onSelectOption = onSelectOption,
                    )
                }

                TaskType.CURRENCY_CALCULATOR -> {
                    CurrencyCalculatorArena(
                        targetAmountCrc = task.targetAmountCrc,
                        currentAmountCrc = state.accumulatedColones,
                        onAddColones = onAddColones,
                        onReset = onResetColones,
                    )
                }

                TaskType.MULTIPLE_CHOICE,
                TaskType.TECHNICAL_SEQUENCE -> {
                    MultipleChoiceArena(
                        options = task.options,
                        selectedIndex = state.selectedOptionIndex,
                        onSelectOption = onSelectOption,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Submit Button
            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                enabled = !state.isLoading && when (task.type) {
                    TaskType.CURRENCY_CALCULATOR -> state.accumulatedColones > 0
                    else -> state.selectedOptionIndex != null
                },
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        text = "REGISTRAR EVIDENCIA CRIPTOGRÁFICA",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SpatialPlacementArena(
    options: List<String>,
    selectedIndex: Int?,
    onSelectOption: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Visual Diagram Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "🐱 🏡", fontSize = 40.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Don Bigotes y la casita (Simulador Espacial 3D)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.outline,
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        options.forEachIndexed { index, optionText ->
            val isSelected = selectedIndex == index
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = if (isSelected) {
                    androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectOption(index) },
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelectOption(index) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = optionText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrencyCalculatorArena(
    targetAmountCrc: Int,
    currentAmountCrc: Int,
    onAddColones: (Int) -> Unit,
    onReset: () -> Unit,
) {
    val denominations = listOf(5, 10, 25, 50, 100, 500)
    val isExactMatch = currentAmountCrc == targetAmountCrc

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Display Totals
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isExactMatch) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "PRECIO A PAGAR",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "₡$targetAmountCrc",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "TU MONEDERO ACTUAL",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "₡$currentAmountCrc",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        color = if (isExactMatch) {
                            MaterialTheme.colorScheme.primary
                        } else if (currentAmountCrc > targetAmountCrc) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    ),
                )
            }
        }

        // Quick Coins Pad
        Text(
            text = "TOCA LAS MONEDAS PARA SUMAR:",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            denominations.take(3).forEach { coin ->
                FilledTonalButton(
                    onClick = { onAddColones(coin) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("+₡$coin", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            denominations.drop(3).forEach { coin ->
                FilledTonalButton(
                    onClick = { onAddColones(coin) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("+₡$coin", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Reset Button
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("Limpiar Monedas", fontSize = 12.sp)
        }
    }
}

@Composable
private fun MultipleChoiceArena(
    options: List<String>,
    selectedIndex: Int?,
    onSelectOption: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, optionText ->
            val isSelected = selectedIndex == index
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = if (isSelected) {
                    androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectOption(index) },
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelectOption(index) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = optionText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun EconomicBridgeCard(track: CurriculumTrack) {
    val (iscoCode, occupationTitle, serviceVertical) = when (track) {
        CurriculumTrack.DIBUJO_TECNICO_8 -> Triple("3118", "Delineantes y dibujantes técnicos CAD", "ARCHITECTURAL_AND_TECHNICAL_CAD")
        CurriculumTrack.ELECTRICIDAD_9 -> Triple("7411", "Electricistas de obras y afines", "RESIDENTIAL_ELECTRICAL_SERVICES")
        else -> Triple("7126", "Fontaneros y montadores de tuberías", "RESIDENTIAL_PLUMBING")
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "💼", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PUENTE EDUCATIVO-ECONÓMICO (${track.displayName} → SERVICIO)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Ocupación ISCO-08: $iscoCode ($occupationTitle)",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = "Vertical de Servicio: $serviceVertical · Estado: DEMONSTRATED",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "⚖️ Invariante Constitucional: La culminación de prácticas en simulación certifica competencia técnica demostrada, pero jamás sustituye una colegiatura o licencia legal profesional obligatoria.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun EvidenceProofDialog(
    isSuccess: Boolean,
    feedback: String,
    hash: String,
    mastery: Double,
    confidence: Double,
    isTransferUnlocked: Boolean,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSuccess) "EVIDENCIA REGISTRADA" else "DIAGNÓSTICO PEDAGÓGICO",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = feedback, style = MaterialTheme.typography.bodyMedium)

                Divider()

                Text(
                    text = "Dominio de Concepto: ${(mastery * 100).toInt()}% · Confianza: ${(confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                )

                if (isTransferUnlocked) {
                    Text(
                        text = "🏆 ¡TRANSFERENCIA DEMOSTRADA! Concepto dominado (> 85%).",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }

                if (hash.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "HASH CRIPTOGRÁFICO DE PRUEBA (SHA-256):",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = hash,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.outline,
                        ),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("CONTINUAR")
            }
        },
    )
}
