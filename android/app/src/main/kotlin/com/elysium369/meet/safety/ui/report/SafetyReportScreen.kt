package com.elysium369.meet.safety.ui.report

import android.Manifest
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.elysium369.meet.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elysium369.meet.safety.domain.LocationSource
import com.elysium369.meet.safety.domain.SafetyReportCategory
import com.elysium369.meet.safety.domain.SourceRelation
import com.elysium369.meet.safety.domain.label
import com.elysium369.meet.safety.ui.common.PulseState
import com.elysium369.meet.safety.ui.common.SafetyCategoryIcons
import com.elysium369.meet.safety.ui.common.SafetyHaptics
import com.elysium369.meet.safety.ui.common.SafetyPulse
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
    onNavigateToResearch: () -> Unit = {},
    locationEntryMode: String? = null,
    viewModel: SafetyReportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val impunityStore = remember(context) { com.elysium369.meet.safety.drugimpunity.DrugMarketImpunityStore(context.applicationContext) }
    var showInitialRegistration by remember { mutableStateOf(!impunityStore.isInitialRegistered()) }

    if (showInitialRegistration) {
        SafetyInitialRegistrationScreen(
            onBack = onBack,
            onSaved = { role, name, org, cred ->
                impunityStore.saveInitialRegistration(role, name, org, cred)
                val detail = when (role) {
                    "JOURNALIST" -> "Rol autodeclarado: periodista | Medio declarado: $org | Nombre declarado: $name | Acreditación declarada: $cred"
                    "INSTITUTION" -> "Rol autodeclarado: institución | Entidad declarada: $org | Unidad o identificador declarado: $cred"
                    else -> if (name.isBlank()) "Rol autodeclarado: ciudadano | Alias no aportado" else "Rol autodeclarado: ciudadano | Alias declarado: $name"
                }
                viewModel.updateParticipantDetails(detail)
                showInitialRegistration = false
            }
        )
        return
    }

    androidx.compose.runtime.LaunchedEffect(locationEntryMode) {
        if (locationEntryMode == "search" || locationEntryMode == "map") viewModel.goToStep(0)
    }

    if (state.createdReportId != null) {
        val createdReport by viewModel.createdReport.collectAsStateWithLifecycle()
        SafetyReportReceiptScreen(
            reportId = state.createdReportId!!,
            report = createdReport,
            onBack = onBack,
            onDone = { onReportSubmitted(state.createdReportId!!) },
            onNavigateToResearch = onNavigateToResearch,
            hasScientificHypothesis = state.enableScientificAnalysis &&
                state.scientificHypothesis.isNotBlank(),
            localProjectionWarning = state.error,
        )
        return
    }

    val animatedProgress by animateFloatAsState(
        targetValue = (state.step + 1).toFloat() / (state.totalSteps + 1),
        animationSpec = tween(400),
        label = "progress-anim",
    )

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.safety_report_header_title), fontWeight = FontWeight.Black, fontSize = 17.sp, color = MeetColors.textPrimary)
                        Text(
                            stringResource(R.string.safety_report_header_step, state.step + 1, state.totalSteps + 1),
                            fontSize = 11.sp,
                            color = MeetColors.cyberCyan,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        SafetyHaptics.selectionTick(view)
                        if (state.step > 0) viewModel.previousStep() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.safety_back), tint = MeetColors.textPrimary)
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
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(4.dp)),
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
                val activeCategory = state.category
                if (activeCategory != null) {
                    val visual = SafetyCategoryIcons.of(activeCategory)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = visual.color.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, visual.color.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                visual.icon,
                                contentDescription = null,
                                tint = visual.color,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "TIPOLOGÍA ACTIVA",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = visual.color,
                                    letterSpacing = 1.sp,
                                )
                                Text(
                                    activeCategory.label(),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MeetColors.cardBackground,
                            ) {
                                Text(
                                    "Paso ${state.step + 1} de ${state.totalSteps + 1}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MeetColors.cyberCyan,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }

                AnimatedContent(
                    targetState = state.step,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width / 3 } + fadeIn(tween(300))) togetherWith
                                (slideOutHorizontally { width -> -width / 3 } + fadeOut(tween(300)))
                        } else {
                            (slideInHorizontally { width -> -width / 3 } + fadeIn(tween(300))) togetherWith
                                (slideOutHorizontally { width -> width / 3 } + fadeOut(tween(300)))
                        }
                    },
                    label = "wizard-step-transition",
                ) { currentStep ->
                    when (currentStep) {
                        0 -> StepSourceRelation(state, viewModel, view)
                        1 -> StepWhere(state, viewModel, locationEntryMode == "map", view)
                        2 -> StepCategory(state, viewModel, view)
                        3 -> StepVictimDemographics(state, viewModel)
                        4 -> StepNarrative(state, viewModel)
                        5 -> StepWhen(state, viewModel, view)
                        6 -> StepEvidence(state, viewModel, view)
                        7 -> StepReview(state)
                    }
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
                    if (state.step == state.totalSteps) {
                        SafetyHaptics.reportSubmitted(view)
                        viewModel.submit()
                    } else {
                        SafetyHaptics.stepCompleted(view)
                        viewModel.nextStep()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = when (state.step) {
                    0 -> state.sourceRelation != null
                    1 -> true
                    2 -> state.category != null
                    3 -> true
                    4 -> state.narrative.trim().length >= 10
                    5 -> true
                    6 -> !state.staging
                    7 -> true
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
                        if (state.submitting) stringResource(R.string.safety_report_button_saving) else stringResource(R.string.safety_report_button_submit)
                    } else stringResource(R.string.safety_report_button_continue),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun StepCategory(
    state: SafetyReportUiState,
    viewModel: SafetyReportViewModel,
    view: View,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                stringResource(R.string.safety_report_step_category_title),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MeetColors.cyberCyan,
                letterSpacing = 1.2.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            SafetyReportCategory.entries.forEach { cat ->
                val visual = SafetyCategoryIcons.of(cat)
                val isSelected = state.category == cat

                Card(
                    onClick = {
                        SafetyHaptics.selectionTick(view)
                        viewModel.selectCategory(cat)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) visual.color.copy(alpha = 0.15f) else MeetColors.backgroundDeep,
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) visual.color else MeetColors.borderSubtle,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(visual.color.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = visual.icon,
                                contentDescription = null,
                                tint = visual.color,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            cat.label(),
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else MeetColors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = visual.color,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepSourceRelation(
    state: SafetyReportUiState,
    viewModel: SafetyReportViewModel,
    view: View,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(stringResource(R.string.safety_report_identify), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MeetColors.cyberCyan, letterSpacing = 1.2.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.safety_report_identify_body), fontSize = 12.sp, color = MeetColors.textSecondary)
            Spacer(modifier = Modifier.height(12.dp))

            SourceRelation.entries.forEach { relation ->
                val isSelected = state.sourceRelation == relation
                val borderColor = if (isSelected) {
                    if (relation == SourceRelation.INSTITUTIONAL) Color(0xFF7C4DFF) else MeetColors.cyberCyan
                } else MeetColors.borderSubtle
                val bgColor = if (isSelected) {
                    if (relation == SourceRelation.INSTITUTIONAL) Color(0xFF7C4DFF).copy(alpha = 0.12f) else MeetColors.cyberCyan.copy(alpha = 0.12f)
                } else MeetColors.backgroundDeep

                Card(
                    onClick = {
                        SafetyHaptics.selectionTick(view)
                        viewModel.selectSourceRelation(relation)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = bgColor,
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = borderColor,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${relation.emoji()} ${relation.label()}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else MeetColors.textPrimary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                relation.description(),
                                fontSize = 11.sp,
                                color = MeetColors.textSecondary
                            )
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = if (relation == SourceRelation.INSTITUTIONAL) Color(0xFFB388FF) else MeetColors.cyberCyan,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun SourceRelation.emoji(): String = when (this) {
    SourceRelation.DIRECT_WITNESS -> "🔴"
    SourceRelation.FAMILY_OR_NEIGHBOR -> "🟠"
    SourceRelation.SECOND_HAND -> "🟡"
    SourceRelation.DOCUMENTARY -> "📄"
    SourceRelation.JOURNALISTIC -> "📰"
    SourceRelation.PUBLIC_RECORD -> "🏛️"
    SourceRelation.INSTITUTIONAL -> "🏢"
    SourceRelation.UNKNOWN -> "❓"
}

@Composable
private fun SourceRelation.description(): String = when (this) {
    SourceRelation.DIRECT_WITNESS -> stringResource(R.string.safety_report_source_witness_desc)
    SourceRelation.FAMILY_OR_NEIGHBOR -> stringResource(R.string.safety_report_source_family_desc)
    SourceRelation.SECOND_HAND -> stringResource(R.string.safety_report_source_secondhand_desc)
    SourceRelation.DOCUMENTARY -> stringResource(R.string.safety_report_source_documentary_desc)
    SourceRelation.JOURNALISTIC -> stringResource(R.string.safety_report_source_journalistic_desc)
    SourceRelation.PUBLIC_RECORD -> stringResource(R.string.safety_report_source_public_desc)
    SourceRelation.INSTITUTIONAL -> "Reporte emitido desde institución, autoridad pública o cuerpo de seguridad."
    SourceRelation.UNKNOWN -> stringResource(R.string.safety_report_source_unknown_desc)
}

@Composable
private fun StepVictimDemographics(state: SafetyReportUiState, viewModel: SafetyReportViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.safety_report_victims_title), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.2.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.safety_report_victims_body), fontSize = 12.sp, color = MeetColors.textSecondary)
            Spacer(modifier = Modifier.height(16.dp))

            VictimNumberField(
                label = stringResource(R.string.safety_report_victim_count),
                value = state.victimCount,
                onValueChange = viewModel::updateVictimCount,
            )

            Spacer(modifier = Modifier.height(12.dp))

            VictimNumberField(
                label = stringResource(R.string.safety_report_victim_female),
                value = state.victimFemale,
                onValueChange = viewModel::updateVictimFemale,
            )

            Spacer(modifier = Modifier.height(12.dp))

            VictimNumberField(
                label = stringResource(R.string.safety_report_victim_male),
                value = state.victimMale,
                onValueChange = viewModel::updateVictimMale,
            )

            val total = state.victimCount ?: 0
            val sumGender = (state.victimFemale ?: 0) + (state.victimMale ?: 0)
            if (total > 0 && sumGender > total) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.safety_report_victim_sum_error, sumGender, total),
                    fontSize = 11.sp,
                    color = MeetColors.error,
                )
            }
        }
    }
}

@Composable
private fun VictimNumberField(
    label: String,
    value: Int?,
    onValueChange: (Int?) -> Unit,
) {
    OutlinedTextField(
        value = value?.toString() ?: "",
        onValueChange = { text ->
            onValueChange(text.filter { it.isDigit() }.take(3).toIntOrNull())
        },
        label = { Text(label, fontSize = 12.sp) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MeetColors.textPrimary,
            unfocusedTextColor = MeetColors.textPrimary,
            focusedBorderColor = MeetColors.cyberCyan,
            unfocusedBorderColor = MeetColors.borderSubtle,
            focusedLabelColor = MeetColors.cyberCyan,
            unfocusedLabelColor = MeetColors.textSecondary,
        ),
    )
}

@Composable
private fun StepNarrative(state: SafetyReportUiState, viewModel: SafetyReportViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
            border = BorderStroke(1.dp, MeetColors.borderSubtle),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.safety_report_step_narrative_title), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.2.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.safety_report_step_narrative_count, state.narrative.trim().length),
                    fontSize = 11.sp,
                    color = if (state.narrative.trim().length >= 10) MeetColors.neonGreen else MeetColors.textSecondary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.narrative,
                    onValueChange = { viewModel.updateNarrative(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.safety_report_step_narrative_placeholder), color = MeetColors.textSecondary) },
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

        FinancialIntelligenceSection(
            state = state,
            viewModel = viewModel,
            defaultExpanded = state.isFinancialCategory || state.sicopProcedureNumber.isNotBlank() || state.economicEntityName.isNotBlank(),
        )

        ScientificAnalysisSection(state = state, viewModel = viewModel)
    }
}

@Composable
private fun ScientificAnalysisSection(
    state: SafetyReportUiState,
    viewModel: SafetyReportViewModel,
) {
    var expanded by remember { mutableStateOf(state.enableScientificAnalysis || state.scientificHypothesis.isNotBlank() || state.factualClaim.isNotBlank()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, if (expanded) MeetColors.cyberCyan.copy(alpha = 0.8f) else MeetColors.borderSubtle),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        expanded = !expanded
                        viewModel.toggleScientificAnalysis(expanded)
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text("🔬", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            "DIMENSIÓN CIENTÍFICA & HIPÓTESIS FORENSE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MeetColors.cyberCyan,
                            letterSpacing = 0.8.sp,
                        )
                        Text(
                            if (expanded) "Criterios Popperianos de falsación y hechos fácticos" else "Toca para agregar hipótesis y proyectar al Hub Científico",
                            fontSize = 11.sp,
                            color = MeetColors.textSecondary,
                        )
                    }
                }
                Text(
                    if (expanded) "▲" else "▼",
                    color = MeetColors.cyberCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }

            if (expanded) {
                HorizontalDivider(color = MeetColors.borderSubtle, thickness = 1.dp)

                // 1. Factual Claim (Observación fáctica)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "1. AFIRMACIÓN FÁCTICA OBSERVACIONAL (CLAIM)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MeetColors.neonGreen,
                        letterSpacing = 0.8.sp,
                    )
                    OutlinedTextField(
                        value = state.factualClaim,
                        onValueChange = { viewModel.updateFactualClaim(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ej: Vehículo sospechoso sin placas descargando bultos a las 02:15...", fontSize = 12.sp, color = MeetColors.textMuted) },
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeetColors.neonGreen,
                            unfocusedBorderColor = MeetColors.borderSubtle,
                            focusedTextColor = MeetColors.textPrimary,
                            unfocusedTextColor = MeetColors.textPrimary,
                        ),
                    )
                }

                // 2. Scientific Hypothesis (H1)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "2. HIPÓTESIS CAUSAL / EXPLICATIVA (H₁)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MeetColors.cyberCyan,
                        letterSpacing = 0.8.sp,
                    )
                    OutlinedTextField(
                        value = state.scientificHypothesis,
                        onValueChange = { viewModel.updateScientificHypothesis(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ej: Operación logística de almacenamiento vinculada a banda territorial...", fontSize = 12.sp, color = MeetColors.textMuted) },
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeetColors.cyberCyan,
                            unfocusedBorderColor = MeetColors.borderSubtle,
                            focusedTextColor = MeetColors.textPrimary,
                            unfocusedTextColor = MeetColors.textPrimary,
                        ),
                    )
                }

                // 3. Null Hypothesis (H0)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "3. HIPÓTESIS NULA (H₀)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MeetColors.textSecondary,
                        letterSpacing = 0.8.sp,
                    )
                    OutlinedTextField(
                        value = state.nullHypothesis,
                        onValueChange = { viewModel.updateNullHypothesis(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ej: Descarga comercial ordinaria sin vinculación criminal...", fontSize = 12.sp, color = MeetColors.textMuted) },
                        maxLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeetColors.textSecondary,
                            unfocusedBorderColor = MeetColors.borderSubtle,
                            focusedTextColor = MeetColors.textPrimary,
                            unfocusedTextColor = MeetColors.textPrimary,
                        ),
                    )
                }

                // 4. Popperian Falsification Criteria
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "4. CRITERIO POPPERIANO DE FALSABILIDAD",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MeetColors.warning,
                        letterSpacing = 0.8.sp,
                    )
                    OutlinedTextField(
                        value = state.falsificationCriteria,
                        onValueChange = { viewModel.updateFalsificationCriteria(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("¿Qué prueba documental o peritaje oficial refutaría tajantemente tu hipótesis?", fontSize = 12.sp, color = MeetColors.textMuted) },
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeetColors.warning,
                            unfocusedBorderColor = MeetColors.borderSubtle,
                            focusedTextColor = MeetColors.textPrimary,
                            unfocusedTextColor = MeetColors.textPrimary,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun FinancialIntelligenceSection(
    state: SafetyReportUiState,
    viewModel: SafetyReportViewModel,
    defaultExpanded: Boolean,
) {
    var expanded by remember(defaultExpanded) {
        mutableStateOf(defaultExpanded || state.sicopProcedureNumber.isNotBlank() || state.economicEntityName.isNotBlank())
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, if (expanded) Color(0xFFFFB300).copy(alpha = 0.8f) else MeetColors.borderSubtle),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text("🏛️", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            "INTELIGENCIA FINANCIERA & SICOP",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFFB300),
                            letterSpacing = 0.8.sp,
                        )
                        Text(
                            if (expanded) "Contratación estatal, personas jurídicas y montos" else "Toca para registrar licitación SICOP o red corporativa",
                            fontSize = 11.sp,
                            color = MeetColors.textSecondary,
                        )
                    }
                }
                Text(
                    if (expanded) "▲" else "▼",
                    color = Color(0xFFFFB300),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }

            if (expanded) {
                HorizontalDivider(color = MeetColors.borderSubtle, thickness = 1.dp)

                // Mandatory Constitutional Safeguard Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFB300).copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.4f)),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text("⚖️", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "SALVAGUARDA CONSTITUCIONAL E INTELIGENCIA",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB300),
                                letterSpacing = 0.5.sp,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "La tenencia o exhibición de vehículos de alta gama, residencias u otros bienes de alto valor NO constituye por sí misma delito ni corrupción. Elysium Safety no tramita acusaciones basadas exclusivamente en ostentación. Se requiere sustento documental, número licitatorio o nexo contractual comprobable.",
                                fontSize = 11.sp,
                                color = MeetColors.textPrimary,
                                lineHeight = 15.sp,
                            )
                        }
                    }
                }

                // 1. Procedimiento SICOP
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "NÚMERO DE PROCEDIMIENTO SICOP / EXPEDIENTE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB300),
                        letterSpacing = 0.8.sp,
                    )
                    OutlinedTextField(
                        value = state.sicopProcedureNumber,
                        onValueChange = viewModel::updateSicopProcedureNumber,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ej: 2024LN-000015-0005900001...", fontSize = 12.sp, color = MeetColors.textMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFFB300),
                            unfocusedBorderColor = MeetColors.borderSubtle,
                            focusedTextColor = MeetColors.textPrimary,
                            unfocusedTextColor = MeetColors.textPrimary,
                        ),
                    )
                }

                // 2. Entidad / Empresa
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "ENTIDAD ECONÓMICA / EMPRESA ADJUDICATARIA",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MeetColors.cyberCyan,
                        letterSpacing = 0.8.sp,
                    )
                    OutlinedTextField(
                        value = state.economicEntityName,
                        onValueChange = viewModel::updateEconomicEntityName,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ej: Consorcio Vial del Pacífico S.A...", fontSize = 12.sp, color = MeetColors.textMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeetColors.cyberCyan,
                            unfocusedBorderColor = MeetColors.borderSubtle,
                            focusedTextColor = MeetColors.textPrimary,
                            unfocusedTextColor = MeetColors.textPrimary,
                        ),
                    )
                }

                // 3. Cédula Jurídica
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "CÉDULA JURÍDICA / IDENTIFICACIÓN FISCAL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MeetColors.textSecondary,
                        letterSpacing = 0.8.sp,
                    )
                    OutlinedTextField(
                        value = state.economicEntityTaxId,
                        onValueChange = viewModel::updateEconomicEntityTaxId,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ej: 3-101-987654...", fontSize = 12.sp, color = MeetColors.textMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeetColors.cyberCyan,
                            unfocusedBorderColor = MeetColors.borderSubtle,
                            focusedTextColor = MeetColors.textPrimary,
                            unfocusedTextColor = MeetColors.textPrimary,
                        ),
                    )
                }

                // 4. Monto y Moneda
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "MONTO ESTIMADO / CONTRATO",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MeetColors.neonGreen,
                        letterSpacing = 0.8.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = {
                                val next = if (state.contractCurrency == "CRC") "USD" else "CRC"
                                viewModel.updateContractCurrency(next)
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.contractCurrency == "CRC") Color(0xFF00B0FF) else MeetColors.neonGreen,
                                contentColor = Color.Black,
                            ),
                            modifier = Modifier.height(52.dp),
                        ) {
                            Text(state.contractCurrency, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        OutlinedTextField(
                            value = state.contractAmountMajor,
                            onValueChange = viewModel::updateContractAmountMajor,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Ej: 45000000", fontSize = 12.sp, color = MeetColors.textMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MeetColors.neonGreen,
                                unfocusedBorderColor = MeetColors.borderSubtle,
                                focusedTextColor = MeetColors.textPrimary,
                                unfocusedTextColor = MeetColors.textPrimary,
                            ),
                        )
                    }
                }

                // 5. Fuente oficial
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "FUENTE OFICIAL / ENLACE PÚBLICO",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MeetColors.textSecondary,
                        letterSpacing = 0.8.sp,
                    )
                    OutlinedTextField(
                        value = state.officialDocumentSource,
                        onValueChange = viewModel::updateOfficialDocumentSource,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ej: https://www.sicop.go.cr o Informe CGR...", fontSize = 12.sp, color = MeetColors.textMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeetColors.cyberCyan,
                            unfocusedBorderColor = MeetColors.borderSubtle,
                            focusedTextColor = MeetColors.textPrimary,
                            unfocusedTextColor = MeetColors.textPrimary,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepWhen(state: SafetyReportUiState, viewModel: SafetyReportViewModel, view: View) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.safety_report_step_when_title), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.2.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.safety_report_step_when_optional), fontSize = 11.sp, color = MeetColors.textSecondary)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.occurredAtIso == null,
                    onClick = {
                        SafetyHaptics.selectionTick(view)
                        viewModel.updateOccurredAt(null)
                    },
                    label = { Text(stringResource(R.string.safety_report_step_when_none)) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MeetColors.neonGreen.copy(alpha = 0.15f),
                        selectedLabelColor = MeetColors.neonGreen,
                    ),
                )
                FilterChip(
                    selected = state.occurredAtIso != null,
                    onClick = {
                        SafetyHaptics.selectionTick(view)
                        viewModel.updateOccurredAt(java.time.Instant.now().toString())
                    },
                    label = { Text(stringResource(R.string.safety_report_step_when_now)) },
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
                label = { Text(stringResource(R.string.safety_report_step_when_label), color = MeetColors.textSecondary) },
                placeholder = { Text(stringResource(R.string.safety_report_step_when_placeholder), color = MeetColors.textSecondary) },
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
private fun StepWhere(
    state: SafetyReportUiState,
    viewModel: SafetyReportViewModel,
    openMapInitially: Boolean = false,
    view: View,
) {
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
            Text(stringResource(R.string.safety_report_step_where_title), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.2.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.safety_report_step_where_optional), fontSize = 11.sp, color = MeetColors.textSecondary)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.locationQuery,
                onValueChange = viewModel::updateLocationQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.safety_report_step_where_search_label)) },
                placeholder = { Text(stringResource(R.string.safety_report_step_where_search_placeholder)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MeetColors.cyberCyan,
                    unfocusedBorderColor = MeetColors.borderSubtle,
                    focusedTextColor = MeetColors.textPrimary,
                    unfocusedTextColor = MeetColors.textPrimary,
                ),
            )
            if (state.searchingLocation) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp), color = MeetColors.cyberCyan)
            state.locationSuggestions.forEach { place ->
                androidx.compose.material3.TextButton(
                    onClick = {
                        SafetyHaptics.selectionTick(view)
                        viewModel.selectPlace(place)
                        showMap = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
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
                            Text(stringResource(R.string.safety_report_step_where_captured_title), fontSize = 13.sp, color = MeetColors.neonGreen, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.safety_report_step_where_captured_acc, "%.0f".format(state.location.accuracyMeters ?: 0f)),
                                fontSize = 11.sp,
                                color = MeetColors.textSecondary,
                            )
                        }
                        Text(
                            stringResource(R.string.safety_report_step_where_captured_clear),
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
                    if (state.locating) CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp, color = Color.Black)
                    else Icon(Icons.Filled.LocationOn, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.safety_report_step_where_use_location), fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.safety_report_step_where_location_desc),
                    fontSize = 11.sp,
                    color = MeetColors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.OutlinedButton(
                onClick = {
                    SafetyHaptics.selectionTick(view)
                    showMap = !showMap
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.cyberCyan),
                border = BorderStroke(1.dp, MeetColors.cyberCyan),
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (showMap) stringResource(R.string.safety_report_step_where_hide_map) else stringResource(R.string.safety_report_step_where_show_map), fontWeight = FontWeight.Bold)
            }
            if (showMap) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.safety_report_step_where_map_hint), color = MeetColors.cyberCyan, fontSize = 12.sp)
                val selected = state.location?.let { GeoPoint(it.latitude, it.longitude, it.accuracyMeters) }
                val mapState = CommonMapState(
                    markers = selected?.let { listOf(GeoMarker("safety-draft", GeoMarkerRole.PRIVATE_INCIDENT_PIN, it, stringResource(R.string.safety_report_step_where_marker_title), stringResource(R.string.safety_report_step_where_marker_desc))) } ?: emptyList(),
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
private fun StepEvidence(
    state: SafetyReportUiState,
    viewModel: SafetyReportViewModel,
    view: View,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            SafetyHaptics.evidenceAttached(view)
            viewModel.attachEvidence(uri)
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.safety_report_step_evidence_title), fontWeight = FontWeight.Black, fontSize = 15.sp, color = MeetColors.textPrimary)
            Text(
                "Adjunta fotografías, audios o documentos que respalden el hecho. Para videos, usa el enlace compartible de abajo; el video no se carga al servidor de Elysium.",
                color = MeetColors.textSecondary,
                fontSize = 12.sp,
            )

            state.evidence.forEach { item ->
                val evidenceIcon = when {
                    item.mimeType.startsWith("image/") -> Icons.Filled.AttachFile
                    item.mimeType.startsWith("video/") -> Icons.Filled.Videocam
                    item.mimeType.startsWith("audio/") -> Icons.Filled.AttachFile
                    else -> Icons.Filled.AttachFile
                }
                val evidenceLabel = when {
                    item.mimeType.startsWith("image/") -> "📷 Imagen"
                    item.mimeType.startsWith("video/") -> "🎬 Video"
                    item.mimeType.startsWith("audio/") -> "🎙️ Audio"
                    item.mimeType == "application/pdf" -> "📄 PDF"
                    item.mimeType.contains("word") || item.mimeType.contains("document") -> "📝 Documento"
                    item.mimeType.contains("text") -> "📝 Texto"
                    else -> "📎 ${item.mimeType.substringAfter("/")}"
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MeetColors.backgroundDeep)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(evidenceIcon, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            evidenceLabel,
                            color = MeetColors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${item.byteCount / 1024} KB · ${item.mimeType}",
                            color = MeetColors.textMuted,
                            fontSize = 10.sp,
                        )
                    }
                    IconButton(
                        onClick = { viewModel.removeEvidence(item.evidenceId) },
                        enabled = !state.staging,
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MeetColors.error, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Button(
                onClick = {
                    picker.launch(
                        arrayOf(
                            "image/*",
                            "audio/*",
                            "application/pdf",
                            "text/*",
                            "application/msword",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/vnd.ms-excel",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        )
                    )
                },
                enabled = !state.staging && state.evidence.size < 5,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeetColors.cyberCyan,
                    contentColor = MeetColors.backgroundDeep,
                ),
            ) {
                Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.staging) stringResource(R.string.safety_report_step_evidence_protecting) else "Adjuntar evidencia",
                    fontWeight = FontWeight.Bold,
                )
            }

            if (state.evidence.isNotEmpty()) {
                Text(
                    "${state.evidence.size}/5 archivos adjuntos",
                    fontSize = 10.sp,
                    color = MeetColors.textMuted,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MeetColors.borderSubtle, thickness = 1.dp)
            Spacer(Modifier.height(4.dp))

            // === BLOQUE A: FOTOS Y DOCUMENTOS LOCALES ===
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AttachFile, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "FOTOGRAFÍAS Y DOCUMENTOS (LOCAL)",
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    color = MeetColors.cyberCyan,
                    letterSpacing = 1.sp,
                )
            }
            Text(
                "Adjunta fotos fijas, audios o documentos PDF / actas directamente de tu dispositivo. Cada archivo se cifra con huella SHA-256.",
                color = MeetColors.textSecondary,
                fontSize = 11.sp,
            )

            state.evidence.forEach { item ->
                val evidenceIcon = when {
                    item.mimeType.startsWith("image/") -> Icons.Filled.AttachFile
                    item.mimeType.startsWith("audio/") -> Icons.Filled.AttachFile
                    else -> Icons.Filled.AttachFile
                }
                val evidenceLabel = when {
                    item.mimeType.startsWith("image/") -> "📷 Imagen"
                    item.mimeType.startsWith("audio/") -> "🎙️ Audio"
                    item.mimeType == "application/pdf" -> "📄 PDF"
                    item.mimeType.contains("word") || item.mimeType.contains("document") -> "📝 Documento"
                    item.mimeType.contains("text") -> "📝 Texto"
                    else -> "📎 ${item.mimeType.substringAfter("/")}"
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MeetColors.backgroundDeep)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(evidenceIcon, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            evidenceLabel,
                            color = MeetColors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${item.byteCount / 1024} KB · ${item.mimeType}",
                            color = MeetColors.textMuted,
                            fontSize = 10.sp,
                        )
                    }
                    IconButton(
                        onClick = { viewModel.removeEvidence(item.evidenceId) },
                        enabled = !state.staging,
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MeetColors.error, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Button(
                onClick = {
                    picker.launch(
                        arrayOf(
                            "image/*",
                            "audio/*",
                            "application/pdf",
                            "text/*",
                            "application/msword",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/vnd.ms-excel",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        )
                    )
                },
                enabled = !state.staging && state.evidence.size < 5,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeetColors.cyberCyan,
                    contentColor = MeetColors.backgroundDeep,
                ),
            ) {
                Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.staging) stringResource(R.string.safety_report_step_evidence_protecting) else "Adjuntar fotos o PDFs del dispositivo",
                    fontWeight = FontWeight.Bold,
                )
            }

            if (state.evidence.isNotEmpty()) {
                Text(
                    "${state.evidence.size}/5 archivos adjuntos",
                    fontSize = 10.sp,
                    color = MeetColors.textMuted,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MeetColors.borderSubtle, thickness = 1.dp)
            Spacer(Modifier.height(4.dp))

            // === BLOQUE B: VIDEOS ESTRICTAMENTE MEDIANTE ENLACE WEB / URL ===
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Videocam, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "VIDEOS (ESTRICTAMENTE MEDIANTE ENLACE)",
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    color = MeetColors.neonGreen,
                    letterSpacing = 1.sp,
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
                border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.35f)),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "🛡️ ¿Por qué mediante enlace? (Protección de servidores)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MeetColors.neonGreen,
                    )
                    Text(
                        "Los videos se comparten únicamente mediante enlaces; no se sube el archivo de video a Elysium. Para no saturar la memoria y el ancho de banda del servidor de seguridad ciudadana, usa un enlace accesible para el destinatario y revisa los permisos antes de enviarlo.",
                        color = MeetColors.textSecondary,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                    Text(
                        "Pasos recomendados:\n" +
                            "1. Sube tu video primero a YouTube (puedes marcarlo como 'Oculto / No listado' si deseas privacidad, o 'Público'), Google Drive, TikTok, X o Facebook.\n" +
                            "2. Copia el enlace web (URL) del video.\n" +
                            "3. Pégalo en el campo inferior y presiona 'Agregar Video'.\n" +
                            "✓ Las autoridades y peritos podrán abrir y reproducir el video al instante sin demoras ni pérdida de calidad.",
                        color = MeetColors.textPrimary,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            }

            // Added video URLs
            state.videoUrls.forEach { url ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MeetColors.backgroundDeep)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Videocam, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        url,
                        color = MeetColors.textPrimary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.removeVideoUrl(url) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = MeetColors.error, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.videoInputText,
                    onValueChange = { viewModel.updateVideoInputText(it) },
                    placeholder = { Text("Pega el enlace del video (YouTube, Drive, etc.)...", fontSize = 11.sp, color = MeetColors.textMuted) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MeetColors.neonGreen,
                        unfocusedBorderColor = MeetColors.borderSubtle,
                        focusedTextColor = MeetColors.textPrimary,
                        unfocusedTextColor = MeetColors.textPrimary,
                        cursorColor = MeetColors.neonGreen,
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                )
                Button(
                    onClick = { viewModel.commitVideoInput() },
                    enabled = state.videoInputText.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeetColors.neonGreen,
                        contentColor = MeetColors.backgroundDeep,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text("Agregar Video", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun StepReview(state: SafetyReportUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.safety_report_step_review_title), fontWeight = FontWeight.Black, fontSize = 16.sp, color = MeetColors.textPrimary)

            ReviewRow(
                label = "FUENTE",
                value = state.sourceRelation?.let { "${it.emoji()} ${it.label()}" } ?: stringResource(R.string.safety_report_step_review_source_pending),
            )

            ReviewRow(
                label = "CATEGORÍA",
                value = state.category?.label() ?: stringResource(R.string.safety_report_step_review_category_pending),
            )

            if (state.showVictimStep && (state.victimCount != null || state.victimFemale != null || state.victimMale != null)) {
                ReviewRow(
                    label = "VÍCTIMAS",
                    value = stringResource(
                        R.string.safety_report_step_review_victims,
                        state.victimCount?.toString() ?: stringResource(R.string.safety_report_step_review_victims_unknown),
                        state.victimFemale?.toString() ?: stringResource(R.string.safety_report_step_review_victims_unknown),
                        state.victimMale?.toString() ?: stringResource(R.string.safety_report_step_review_victims_unknown),
                    ),
                )
            }

            ReviewRow(
                label = "FECHA",
                value = state.occurredAtIso ?: stringResource(R.string.safety_report_step_review_date_missing),
            )

            ReviewRow(
                label = "UBICACIÓN",
                value = if (state.location == null) stringResource(R.string.safety_report_step_review_location_missing) else stringResource(R.string.safety_report_step_review_location, state.location.accuracyMeters?.toInt()?.toString() ?: ""),
            )

            ReviewRow(
                label = "EVIDENCIAS",
                value = stringResource(R.string.safety_report_step_review_evidence, state.evidence.size),
            )

            if (state.videoUrls.isNotEmpty()) {
                ReviewRow(
                    label = "VIDEOS (LINKS)",
                    value = "${state.videoUrls.size} enlace(s) adjunto(s)",
                )
            }

            if (state.scientificHypothesis.isNotBlank() || state.factualClaim.isNotBlank()) {
                ReviewRow(
                    label = "HIPÓTESIS / CIENCIA",
                    value = if (state.scientificHypothesis.isNotBlank()) state.scientificHypothesis.take(30) + "..." else "Falsable (Popper)",
                )
            }

            if (state.sicopProcedureNumber.isNotBlank() || state.economicEntityName.isNotBlank()) {
                ReviewRow(
                    label = "SICOP / ENTIDAD",
                    value = buildString {
                        if (state.economicEntityName.isNotBlank()) append(state.economicEntityName)
                        if (state.sicopProcedureNumber.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append("SICOP: ").append(state.sicopProcedureNumber)
                        }
                    },
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.safety_report_step_review_notice),
                color = MeetColors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MeetColors.backgroundDeep)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MeetColors.cyberCyan)
        Text(value, fontSize = 12.sp, color = MeetColors.textPrimary, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SafetyReportReceiptScreen(
    reportId: String,
    report: com.elysium369.meet.safety.data.local.SafetyReportEntity? = null,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onNavigateToResearch: () -> Unit = {},
    hasScientificHypothesis: Boolean = false,
    localProjectionWarning: String? = null,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    val receiptStatus = safetyReportReceiptStatus(report?.syncState)
    val serverConfirmed = receiptStatus == SafetyReportReceiptStatus.SERVER_CONFIRMED
    val isSyncing = receiptStatus == SafetyReportReceiptStatus.SYNCING
    val isFailed = receiptStatus == SafetyReportReceiptStatus.FAILED

    val statusText = when (receiptStatus) {
        SafetyReportReceiptStatus.CHECKING -> "CONSULTANDO ESTADO"
        SafetyReportReceiptStatus.SERVER_CONFIRMED -> "RECIBIDO POR SERVIDOR"
        SafetyReportReceiptStatus.SYNCING -> "SINCRONIZACIÓN EN CURSO"
        SafetyReportReceiptStatus.FAILED -> "NO CONFIRMADO · REVISAR"
        SafetyReportReceiptStatus.LOCAL_PENDING -> "GUARDADO LOCAL · PENDIENTE"
    }

    val statusColor = when (receiptStatus) {
        SafetyReportReceiptStatus.CHECKING,
        SafetyReportReceiptStatus.LOCAL_PENDING,
        SafetyReportReceiptStatus.FAILED -> MeetColors.warning
        SafetyReportReceiptStatus.SERVER_CONFIRMED -> MeetColors.neonGreen
        SafetyReportReceiptStatus.SYNCING -> MeetColors.cyberCyan
    }

    val titleText = when (receiptStatus) {
        SafetyReportReceiptStatus.CHECKING -> "Consultando el estado del reporte"
        SafetyReportReceiptStatus.SERVER_CONFIRMED -> "Reporte recibido por el servidor"
        SafetyReportReceiptStatus.SYNCING -> "Reporte guardado · sincronización en curso"
        SafetyReportReceiptStatus.FAILED -> "Reporte guardado localmente"
        SafetyReportReceiptStatus.LOCAL_PENDING -> stringResource(R.string.safety_report_receipt_saved_title)
    }

    val descText = when (receiptStatus) {
        SafetyReportReceiptStatus.CHECKING ->
            "Se está consultando el estado del reporte. No se declara un envío exitoso hasta que exista confirmación remota."
        SafetyReportReceiptStatus.SERVER_CONFIRMED ->
            "El servidor confirmó la recepción. Esto no significa que el incidente esté confirmado, que el reporte sea público ni que se haya remitido a una institución."
        SafetyReportReceiptStatus.SYNCING ->
            "El reporte permanece guardado y la sincronización está en curso. La recepción remota todavía no está confirmada."
        SafetyReportReceiptStatus.FAILED ->
            "No hay confirmación remota disponible. Conserva el identificador y revisa el estado en Mis reportes."
        SafetyReportReceiptStatus.LOCAL_PENDING ->
            stringResource(R.string.safety_report_receipt_saved_desc)
    }

    val networkDescText = when (receiptStatus) {
        SafetyReportReceiptStatus.CHECKING -> "Consultando el estado local y remoto."
        SafetyReportReceiptStatus.SERVER_CONFIRMED ->
            "Confirmación remota recibida · publicación y visibilidad sujetas a revisión y controles de divulgación."
        SafetyReportReceiptStatus.SYNCING ->
            "Sincronización pendiente · aún no hay confirmación final del servidor."
        SafetyReportReceiptStatus.FAILED ->
            "Error de sincronización · el reporte local y su identificador se conservan."
        SafetyReportReceiptStatus.LOCAL_PENDING ->
            "Guardado localmente · recibo remoto pendiente."
    }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.safety_report_receipt_title), fontWeight = FontWeight.Black, fontSize = 17.sp, color = MeetColors.textPrimary)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.safety_back), tint = MeetColors.textPrimary)
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SafetyPulse(state = if (serverConfirmed) PulseState.NOMINAL else PulseState.PENDING, size = 88.dp)

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                titleText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = MeetColors.textPrimary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                descText,
                fontSize = 13.sp,
                color = MeetColors.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                border = BorderStroke(1.dp, if (serverConfirmed) MeetColors.neonGreen.copy(alpha = 0.5f) else statusColor.copy(alpha = 0.55f)),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(stringResource(R.string.safety_report_receipt_id), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary)
                            Text(reportId.take(12) + "...", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MeetColors.textPrimary)
                        }
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(reportId))
                            copied = true
                        }) {
                            Icon(
                                if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                                contentDescription = "Copiar ID",
                                tint = if (copied) MeetColors.neonGreen else MeetColors.cyberCyan,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.safety_report_receipt_status), fontSize = 12.sp, color = MeetColors.textSecondary)
                        Text(statusText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = statusColor)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("ESTADO EPISTÉMICO", fontSize = 11.sp, color = MeetColors.textSecondary)
                        Text("OBSERVED (Registro)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MeetColors.cyberCyan)
                    }

                    if (report?.category in setOf("CORRUPTION_PUBLIC_PROCUREMENT", "CORPORATE_OPACITY_CONFLICT", "FINANCIAL_FRAUD")) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("AUDITORÍA FINANCIERA", fontSize = 11.sp, color = MeetColors.textSecondary)
                            Text("SICOP / Integridad", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB300))
                        }
                    }

                    HorizontalDivider(color = MeetColors.borderSubtle, thickness = 1.dp)

                    Text(
                        networkDescText,
                        fontSize = 11.sp,
                        color = MeetColors.textSecondary,
                        lineHeight = 16.sp,
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        "⚖️ Salvaguarda Institucional: Un reporte ciudadano constituye información fáctica estructurada; no equivale a condena ni imputación judicial probada. Preserva la verdad material y la cadena de custodia.",
                        fontSize = 10.sp,
                        color = MeetColors.textMuted,
                        lineHeight = 14.sp,
                    )
                }
            }

            localProjectionWarning?.takeIf { it.isNotBlank() }?.let { warning ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    border = BorderStroke(1.dp, MeetColors.warning.copy(alpha = 0.75f)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text("⚠", fontSize = 18.sp, color = MeetColors.warning)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = warning,
                            modifier = Modifier.weight(1f),
                            fontSize = 11.sp,
                            color = MeetColors.warning,
                            lineHeight = 16.sp,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f)),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔬", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "PLATAFORMA CIENTÍFICA & RESEARCH",
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            color = MeetColors.cyberCyan,
                            letterSpacing = 0.8.sp,
                        )
                    }
                    Text(
                        text = when {
                            localProjectionWarning != null ->
                                "El reporte se conservó, pero no se pudo completar la proyección científica local. La relación de evidencia requiere revisión; esto no confirma el hecho reportado."
                            hasScientificHypothesis ->
                                "La hipótesis que declaraste se guardó como propuesta en el índice científico local. No es una conclusión ni una confirmación del incidente; esta acción no acredita sincronización remota de la proyección."
                            else ->
                                "El reporte se registró como afirmación no corroborada. No se genera una hipótesis automática. La proyección científica de este flujo permanece local y no implica una confirmación remota."
                        },
                        fontSize = 11.sp,
                        color = MeetColors.textSecondary,
                        lineHeight = 16.sp,
                    )
                    Button(
                        onClick = onNavigateToResearch,
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MeetColors.cyberCyan.copy(alpha = 0.18f),
                            contentColor = MeetColors.cyberCyan,
                        ),
                        border = BorderStroke(1.dp, MeetColors.cyberCyan),
                    ) {
                        Text("🔬 Abrir en Plataforma Científica & Research", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeetColors.neonGreen,
                    contentColor = Color.Black,
                ),
            ) {
                Text(stringResource(R.string.safety_report_receipt_done), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SafetyInitialRegistrationScreen(
    onBack: () -> Unit,
    onSaved: (role: String, name: String, organization: String, credential: String) -> Unit,
) {
    val view = LocalView.current
    var selectedRole by remember { mutableStateOf("CIVILIAN") }
    var civilAlias by remember { mutableStateOf("") }
    var mediaOutlet by remember { mutableStateOf("") }
    var journalistName by remember { mutableStateOf("") }
    var pressCard by remember { mutableStateOf("") }
    var institutionName by remember { mutableStateOf("") }
    var institutionUnit by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "REGISTRO INICIAL",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = MeetColors.textPrimary
                        )
                        Text(
                            "Declara cómo deseas identificar tu participación en este dispositivo",
                            fontSize = 11.sp,
                            color = MeetColors.cyberCyan
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        SafetyHaptics.selectionTick(view)
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.safety_back), tint = MeetColors.textPrimary)
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                "DECLARA EL TIPO DE PARTICIPACIÓN",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MeetColors.cyberCyan,
                letterSpacing = 1.2.sp
            )
            Text(
                "Elige la categoría que describes para tu participación. Es un dato autodeclarado, no una validación de identidad, credencial ni autoridad.",
                fontSize = 12.sp,
                color = MeetColors.textSecondary,
            )
            Text(
                "El nombre, medio, institución o alias que aportes se asocia al reporte como declaración del usuario; no se autentica en este flujo.",
                fontSize = 11.sp,
                color = MeetColors.warning,
                lineHeight = 15.sp,
            )

            // 1. Civil
            val isCivil = selectedRole == "CIVILIAN"
            Card(
                onClick = {
                    SafetyHaptics.selectionTick(view)
                    selectedRole = "CIVILIAN"
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCivil) MeetColors.cyberCyan.copy(alpha = 0.12f) else MeetColors.cardBackground,
                ),
                border = BorderStroke(
                    width = if (isCivil) 1.5.dp else 1.dp,
                    color = if (isCivil) MeetColors.cyberCyan else MeetColors.borderSubtle,
                ),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🛡️ Civil", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (isCivil) Color.White else MeetColors.textPrimary)
                            Spacer(Modifier.height(2.dp))
                            Text("Civiles y testigos · Alias opcional; anonimato no garantizado", fontSize = 11.sp, color = MeetColors.textSecondary)
                        }
                        if (isCivil) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (isCivil) {
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = MeetColors.borderSubtle, thickness = 1.dp)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = civilAlias,
                            onValueChange = { civilAlias = it },
                            label = { Text("Seudónimo o Alias (100% Opcional)", fontSize = 11.sp) },
                            placeholder = { Text("Ej. Civil Vigilante, Anónimo", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(6.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MeetColors.neonGreen.copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.35f)),
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    "PRIVACIDAD Y ALCANCE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MeetColors.neonGreen,
                                    letterSpacing = 0.8.sp,
                                )
                                Text(
                                    "Los adjuntos preparados en este dispositivo se cifran localmente. El reporte puede incluir el texto que escribas, la ubicación que aportes y esta declaración de rol. No incluyas datos personales o domicilios de terceros si no son necesarios y legítimos. Esta pantalla no garantiza anonimato absoluto ni protección frente a todos los riesgos.",
                                    fontSize = 10.sp,
                                    color = MeetColors.textSecondary,
                                    lineHeight = 14.sp,
                                )
                            }
                        }
                    }
                }
            }

            // 2. Periodista / Medio
            val isJournalist = selectedRole == "JOURNALIST"
            Card(
                onClick = {
                    SafetyHaptics.selectionTick(view)
                    selectedRole = "JOURNALIST"
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isJournalist) MeetColors.cyberCyan.copy(alpha = 0.12f) else MeetColors.cardBackground,
                ),
                border = BorderStroke(
                    width = if (isJournalist) 1.5.dp else 1.dp,
                    color = if (isJournalist) MeetColors.cyberCyan else MeetColors.borderSubtle,
                ),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("📰 Perfil periodístico declarado", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (isJournalist) Color.White else MeetColors.textPrimary)
                            Spacer(Modifier.height(2.dp))
                            Text("Prensa, reporteros, agencias y medios de comunicación", fontSize = 11.sp, color = MeetColors.textSecondary)
                        }
                        if (isJournalist) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (isJournalist) {
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = MeetColors.borderSubtle, thickness = 1.dp)
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = mediaOutlet,
                                onValueChange = { mediaOutlet = it },
                                label = { Text("Medio de Comunicación / Agencia", fontSize = 11.sp) },
                                placeholder = { Text("Ej. Teletica, CRHoy, Diario Extra, Medio Digital", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = journalistName,
                                onValueChange = { journalistName = it },
                                label = { Text("Nombre del Periodista / Reportero", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = pressCard,
                                onValueChange = { pressCard = it },
                                label = { Text("Carné / Acreditación de Prensa (Opcional)", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            Text(
                                "Estos datos son autodeclarados. Este formulario no verifica tu identidad o acreditación, no otorga autoridad institucional y no certifica operativos ni hallazgos.",
                                fontSize = 10.sp,
                                color = MeetColors.neonGreen,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            // 3. Institución
            val isInstitution = selectedRole == "INSTITUTION"
            Card(
                onClick = {
                    SafetyHaptics.selectionTick(view)
                    selectedRole = "INSTITUTION"
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isInstitution) MeetColors.cyberCyan.copy(alpha = 0.12f) else MeetColors.cardBackground,
                ),
                border = BorderStroke(
                    width = if (isInstitution) 1.5.dp else 1.dp,
                    color = if (isInstitution) MeetColors.cyberCyan else MeetColors.borderSubtle,
                ),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🏢 Institución", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (isInstitution) Color.White else MeetColors.textPrimary)
                            Spacer(Modifier.height(2.dp))
                            Text("Perfil de una institución declarado por el usuario; no constituye una afiliación verificada", fontSize = 11.sp, color = MeetColors.textSecondary)
                        }
                        if (isInstitution) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (isInstitution) {
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = MeetColors.borderSubtle, thickness = 1.dp)
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = institutionName,
                                onValueChange = { institutionName = it },
                                label = { Text("Nombre del cuerpo o institución oficial", fontSize = 11.sp) },
                                placeholder = { Text("Ej. OIJ, Fuerza Pública, Bomberos", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = institutionUnit,
                                onValueChange = { institutionUnit = it },
                                label = { Text("Unidad / Identificador (Opcional)", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            Text(
                                "El nombre de la institución y la unidad se registran como datos declarados. Los permisos institucionales requieren validación independiente en el servidor.",
                                fontSize = 10.sp,
                                color = MeetColors.textSecondary,
                                lineHeight = 14.sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    SafetyHaptics.selectionTick(view)
                    when (selectedRole) {
                        "JOURNALIST" -> onSaved("JOURNALIST", journalistName, mediaOutlet, pressCard)
                        "INSTITUTION" -> onSaved("INSTITUTION", institutionName, institutionName, institutionUnit)
                        else -> onSaved("CIVILIAN", civilAlias, "", "")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeetColors.cyberCyan,
                    contentColor = Color.Black,
                ),
            ) {
                Text("GUARDAR Y CONTINUAR", fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
