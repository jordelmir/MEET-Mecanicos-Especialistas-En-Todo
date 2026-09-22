package com.elysium369.meet.safety.ui.report

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.elysium369.meet.core.geo.CommonMapState
import com.elysium369.meet.core.geo.GeoMarker
import com.elysium369.meet.core.geo.GeoMarkerRole
import com.elysium369.meet.core.geo.GeoPoint
import com.elysium369.meet.core.geo.MapCameraIntent
import com.elysium369.meet.core.geo.runtime.CommonMapPanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyReportScreen(
    onBack: () -> Unit = {},
    onReportSubmitted: (String) -> Unit = {},
    locationEntryMode: String? = null,
    viewModel: SafetyReportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(locationEntryMode) {
        if (locationEntryMode == "search" || locationEntryMode == "map") viewModel.goToStep(0)
    }

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
                    0 -> StepWhere(state, viewModel, locationEntryMode == "map")
                    1 -> StepCategory(state, viewModel)
                    2 -> StepSourceRelation(state, viewModel)
                    3 -> StepNarrative(state, viewModel)
                    4 -> StepWhen(state, viewModel)
                    5 -> StepEvidence(state, viewModel)
                    6 -> StepReview(state)
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
                    0 -> true
                    1 -> state.category != null
                    2 -> state.sourceRelation != null
                    3 -> state.narrative.trim().length >= 10
                    4 -> true
                    5 -> !state.staging
                    6 -> true
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
                color = if (state.narrative.trim().length >= 10) MeetColors.neonGreen else MeetColors.textSecondary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.narrative,
                onValueChange = { viewModel.updateNarrative(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Mínimo 10 caracteres...", color = MeetColors.textSecondary) },
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
            Text("Opcional", fontSize = 11.sp, color = MeetColors.textSecondary)
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
                        viewModel.updateOccurredAt(java.time.Instant.now().toString())
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
                placeholder = { Text("Ej: 2026-09-18T14:30:00", color = MeetColors.textSecondary) },
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
private fun StepWhere(state: SafetyReportUiState, viewModel: SafetyReportViewModel, openMapInitially: Boolean = false) {
    var showMap by remember(openMapInitially) { mutableStateOf(openMapInitially) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.any { it }) viewModel.requestLocation() else viewModel.locationPermissionError()
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("¿Dónde?", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.2.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Opcional. La ubicación exacta nunca se publica.", fontSize = 11.sp, color = MeetColors.textSecondary)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.locationQuery,
                onValueChange = viewModel::updateLocationQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar lugar, dirección o referencia") },
                placeholder = { Text("Ej: Escuela Los Pinos, San José") },
                singleLine = true,
            )
            if (state.searchingLocation) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.locationSuggestions.forEach { place ->
                androidx.compose.material3.TextButton(onClick = { viewModel.selectPlace(place); showMap = true }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(place.primaryLabel, color = MeetColors.textPrimary, fontWeight = FontWeight.Bold)
                        Text(place.secondaryLabel, color = MeetColors.textSecondary, fontSize = 11.sp)
                    }
                }
            }
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
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeetColors.cyberCyan,
                        contentColor = Color.Black,
                    ),
                ) {
                    if (state.locating) CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.LocationOn, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Usar mi ubicación")
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Se solicita una lectura reciente solo cuando pulsas el botón. Puedes continuar sin ubicación.",
                    fontSize = 11.sp,
                    color = MeetColors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.OutlinedButton(onClick = { showMap = !showMap }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.LocationOn, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (showMap) "OCULTAR MAPA" else "FIJAR UBICACIÓN EN EL MAPA")
            }
            if (showMap) {
                Spacer(Modifier.height(8.dp))
                Text("Mantén presionado el punto exacto del incidente.", color = MeetColors.cyberCyan, fontSize = 12.sp)
                val selected = state.location?.let { GeoPoint(it.latitude, it.longitude, it.accuracyMeters) }
                val mapState = CommonMapState(
                    markers = selected?.let { listOf(GeoMarker("safety-draft", GeoMarkerRole.PRIVATE_INCIDENT_PIN, it, "Ubicación del reporte", "Selección privada")) } ?: emptyList(),
                    cameraIntent = selected?.let { MapCameraIntent.CenterOn(it, 16.0) } ?: MapCameraIntent.FollowUser,
                    showRecenterButton = true,
                )
                Card(Modifier.fillMaxWidth().height(320.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MeetColors.cyberCyan)) {
                    CommonMapPanel(
                        state = mapState,
                        modifier = Modifier.fillMaxSize(),
                        userLocation = selected,
                        onMapLongClick = { point -> viewModel.selectMapPoint(point.latitude, point.longitude) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StepEvidence(state: SafetyReportUiState, viewModel: SafetyReportViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::attachEvidence)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Evidencia opcional", fontWeight = FontWeight.Bold, color = MeetColors.textPrimary)
            Text("El original queda cifrado localmente y solo se entrega al almacenamiento privado.", color = MeetColors.textSecondary)
            state.evidence.forEach { item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${item.mimeType} · ${item.byteCount / 1024} KB", Modifier.weight(1f), color = MeetColors.textPrimary)
                    Button(onClick = { viewModel.removeEvidence(item.evidenceId) }, enabled = !state.staging) { Text("Quitar") }
                }
            }
            Button(
                onClick = { picker.launch(arrayOf("image/*", "video/*", "audio/*", "application/pdf")) },
                enabled = !state.staging && state.evidence.size < 5,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.staging) "Protegiendo archivo…" else "Adjuntar archivo") }
        }
    }
}

@Composable
private fun StepReview(state: SafetyReportUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Revisar y guardar", fontWeight = FontWeight.Bold, color = MeetColors.textPrimary)
            Text("Categoría: ${state.category?.label() ?: "Pendiente"}", color = MeetColors.textPrimary)
            Text("Relación con la fuente: ${state.sourceRelation?.label() ?: "Pendiente"}", color = MeetColors.textPrimary)
            Text("Fecha: ${state.occurredAtIso ?: "Dato no capturado"}", color = MeetColors.textSecondary)
            Text(
                if (state.location == null) "Ubicación: Dato no capturado" else "Ubicación privada capturada · precisión ±${state.location.accuracyMeters?.toInt()} m",
                color = MeetColors.textSecondary,
            )
            Text("Evidencias cifradas: ${state.evidence.size}", color = MeetColors.textSecondary)
            Text("Al guardar, el estado inicial es local. La entrega, revisión y publicación requieren confirmación del servidor.", color = MeetColors.textSecondary)
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
                    Text("REPORTE ID", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.2.sp)
                    Text(reportId.take(8) + "...", fontSize = 14.sp, color = MeetColors.textPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("ESTADO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.2.sp)
                    Text("LOCAL_ONLY", fontSize = 14.sp, color = MeetColors.warning)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("CUANDO RED VUELVA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.2.sp)
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
