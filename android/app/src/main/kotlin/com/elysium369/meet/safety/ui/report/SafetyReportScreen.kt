package com.elysium369.meet.safety.ui.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elysium369.meet.safety.domain.LocationSource
import com.elysium369.meet.safety.domain.SafetyReportCategory
import com.elysium369.meet.safety.domain.SourceRelation
import com.elysium369.meet.safety.domain.label
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyReportScreen(
    onBack: () -> Unit = {},
    onReportSubmitted: (String) -> Unit = {},
    viewModel: SafetyReportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.createdReportId != null) {
        SafetyReportReceiptScreen(
            reportId = state.createdReportId!!,
            onBack = onBack,
            onDone = { onReportSubmitted(state.createdReportId!!) },
        )
        return
    }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("REPORTAR INCIDENTE", fontWeight = FontWeight.Black, fontSize = 17.sp, color = Color.White)
                        Text(
                            "Paso ${state.step + 1} / ${state.totalSteps + 1}",
                            fontSize = 11.sp,
                            color = MeetColors.cyberCyan,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.step > 0) viewModel.previousStep() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            LinearProgressIndicator(
                progress = { (state.step + 1).toFloat() / (state.totalSteps + 1) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                color = MeetColors.neonGreen,
                trackColor = MeetColors.borderSubtle,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (state.step) {
                    0 -> StepCategory(state, viewModel)
                    1 -> StepSourceRelation(state, viewModel)
                    2 -> StepNarrative(state, viewModel)
                    3 -> StepWhen(state, viewModel)
                    4 -> StepWhere(state, viewModel)
                }
            }

            state.error?.let { error ->
                Text(
                    error,
                    color = MeetColors.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            Button(
                onClick = {
                    if (state.step == state.totalSteps) viewModel.submit() else viewModel.nextStep()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(bottom = 16.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = when (state.step) {
                    0 -> state.category != null
                    1 -> state.sourceRelation != null
                    2 -> state.narrative.trim().length >= 10
                    3 -> true
                    4 -> true
                    else -> true
                } && !state.submitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeetColors.neonGreen,
                    contentColor = Color.Black,
                    disabledContainerColor = MeetColors.cardBackground,
                    disabledContentColor = MeetColors.textSecondary,
                ),
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp).width(20.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    if (state.step == state.totalSteps) {
                        if (state.submitting) "GUARDANDO..." else "ENVIAR REPORTE"
                    } else "CONTINUAR",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun StepCategory(state: SafetyReportUiState, viewModel: SafetyReportViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("¿Qué ocurrió?", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.2.sp)
            Spacer(modifier = Modifier.height(8.dp))
            SafetyReportCategory.entries.forEach { cat ->
                FilterChip(
                    selected = state.category == cat,
                    onClick = { viewModel.selectCategory(cat) },
                    label = { Text(cat.label(), fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MeetColors.neonGreen.copy(alpha = 0.15f),
                        selectedLabelColor = MeetColors.neonGreen,
                    ),
                )
            }
        }
    }
}

@Composable
private fun StepSourceRelation(state: SafetyReportUiState, viewModel: SafetyReportViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("¿Cómo conoces esta información?", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.2.sp)
            Spacer(modifier = Modifier.height(8.dp))
            SourceRelation.entries.forEach { relation ->
                FilterChip(
                    selected = state.sourceRelation == relation,
                    onClick = { viewModel.selectSourceRelation(relation) },
                    label = { Text(relation.label(), fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MeetColors.cyberCyan.copy(alpha = 0.15f),
                        selectedLabelColor = MeetColors.cyberCyan,
                    ),
                )
            }
        }
    }
}

@Composable
private fun StepNarrative(state: SafetyReportUiState, viewModel: SafetyReportViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Describe lo ocurrido", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.2.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${state.narrative.trim().length} / 10,000 caracteres",
                fontSize = 11.sp,
                color = if (state.narrative.trim().length >= 10) MeetColors.neonGreen else MeetColors.textMuted,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.narrative,
                onValueChange = { viewModel.updateNarrative(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Mínimo 10 caracteres...", color = MeetColors.textMuted) },
                minLines = 4,
                maxLines = 10,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MeetColors.neonGreen,
                    unfocusedBorderColor = MeetColors.borderSubtle,
                    focusedTextColor = MeetColors.textPrimary,
                    unfocusedTextColor = MeetColors.textPrimary,
                    cursorColor = MeetColors.neonGreen,
                ),
            )
        }
    }
}

@Composable
private fun StepWhen(state: SafetyReportUiState, viewModel: SafetyReportViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("¿Cuándo ocurrió?", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.2.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Opcional", fontSize = 11.sp, color = MeetColors.textMuted)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.occurredAtIso == null,
                    onClick = { viewModel.updateOccurredAt(null) },
                    label = { Text("No especificar") },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MeetColors.neonGreen.copy(alpha = 0.15f),
                        selectedLabelColor = MeetColors.neonGreen,
                    ),
                )
                FilterChip(
                    selected = state.occurredAtIso != null,
                    onClick = {
                        val now = java.time.Instant.now().toString().substring(0, 19)
                        viewModel.updateOccurredAt(now)
                    },
                    label = { Text("Ahora") },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MeetColors.cyberCyan.copy(alpha = 0.15f),
                        selectedLabelColor = MeetColors.cyberCyan,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.occurredAtIso ?: "",
                onValueChange = { viewModel.updateOccurredAt(it.ifBlank { null }) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Fecha/hora (ISO)", color = MeetColors.textSecondary) },
                placeholder = { Text("Ej: 2026-09-18T14:30:00", color = MeetColors.textMuted) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MeetColors.neonGreen,
                    unfocusedBorderColor = MeetColors.borderSubtle,
                    focusedTextColor = MeetColors.textPrimary,
                    unfocusedTextColor = MeetColors.textPrimary,
                    cursorColor = MeetColors.neonGreen,
                ),
            )
        }
    }
}

@Composable
private fun StepWhere(state: SafetyReportUiState, viewModel: SafetyReportViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("¿Dónde?", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.2.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Opcional. La ubicación exacta nunca se publica.", fontSize = 11.sp, color = MeetColors.textMuted)
            Spacer(modifier = Modifier.height(8.dp))

            if (state.location != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.neonGreen.copy(alpha = 0.08f)),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MeetColors.neonGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Ubicación capturada", fontSize = 13.sp, color = MeetColors.neonGreen, fontWeight = FontWeight.Bold)
                            Text(
                                "±${"%.0f".format(state.location.accuracyMeters ?: 0f)}m",
                                fontSize = 11.sp,
                                color = MeetColors.textSecondary,
                            )
                        }
                        Text(
                            "BORRAR",
                            fontSize = 11.sp,
                            color = MeetColors.error,
                            modifier = Modifier.clickable { viewModel.updateLocation(null) },
                        )
                    }
                }
            } else {
                Button(
                    onClick = {
                        viewModel.updateLocation(
                            SafetyDraftLocation(
                                latitude = 0.0,
                                longitude = 0.0,
                                accuracyMeters = 0f,
                                source = LocationSource.DEVICE,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeetColors.cyberCyan,
                        contentColor = Color.Black,
                    ),
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Usar mi ubicación")
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "GPS no es verdad absoluta. La ubicación es±18m.",
                    fontSize = 11.sp,
                    color = MeetColors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SafetyReportReceiptScreen(
    reportId: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Text("RECIBO DE REPORTE", fontWeight = FontWeight.Black, fontSize = 17.sp, color = Color.White)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MeetColors.neonGreen,
                modifier = Modifier.height(64.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Reporte guardado", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Guardado localmente. Entrega remota pendiente.",
                fontSize = 14.sp,
                color = MeetColors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                border = BorderStroke(1.dp, MeetColors.borderSubtle),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("REPORTE ID", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textMuted, letterSpacing = 1.2.sp)
                    Text(reportId.take(8) + "...", fontSize = 14.sp, color = MeetColors.textPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("ESTADO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textMuted, letterSpacing = 1.2.sp)
                    Text("LOCAL_ONLY", fontSize = 14.sp, color = MeetColors.warning)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("CUANDO RED VUELVA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textMuted, letterSpacing = 1.2.sp)
                    Text("Worker enviará al servidor", fontSize = 14.sp, color = MeetColors.textSecondary)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeetColors.neonGreen,
                    contentColor = Color.Black,
                ),
            ) {
                Text("VER MIS REPORTES", fontWeight = FontWeight.Bold)
            }
        }
    }
}
