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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Shield
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
    locationEntryMode: String? = null,
    viewModel: SafetyReportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = LocalView.current

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
                        Text(stringResource(R.string.safety_report_header_title), fontWeight = FontWeight.Black, fontSize = 17.sp, color = Color.White)
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.safety_back), tint = Color.White)
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
                Card(
                    onClick = {
                        SafetyHaptics.selectionTick(view)
                        viewModel.selectSourceRelation(relation)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MeetColors.cyberCyan.copy(alpha = 0.15f) else MeetColors.backgroundDeep,
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MeetColors.cyberCyan else MeetColors.borderSubtle,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${relation.emoji()} ${relation.label()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else MeetColors.textPrimary)
                            Spacer(Modifier.height(2.dp))
                            Text(relation.description(), fontSize = 11.sp, color = MeetColors.textSecondary)
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MeetColors.cyberCyan,
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
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = MeetColors.cyberCyan,
            unfocusedBorderColor = MeetColors.borderSubtle,
            focusedLabelColor = MeetColors.cyberCyan,
            unfocusedLabelColor = MeetColors.textSecondary,
        ),
    )
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
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = MeetColors.neonGreen,
                ),
            )
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
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
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
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
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
                        Text(place.primaryLabel, color = Color.White, fontWeight = FontWeight.Bold)
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
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            SafetyHaptics.evidenceAttached(view)
            viewModel.attachEvidence(it)
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.safety_report_step_evidence_title), fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.White)
            Text(stringResource(R.string.safety_report_step_evidence_desc), color = MeetColors.textSecondary, fontSize = 12.sp)

            state.evidence.forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MeetColors.backgroundDeep)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.safety_report_step_evidence_item, item.mimeType, item.byteCount / 1024),
                        Modifier.weight(1f),
                        color = Color.White,
                        fontSize = 12.sp,
                    )
                    IconButton(
                        onClick = { viewModel.removeEvidence(item.evidenceId) },
                        enabled = !state.staging,
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MeetColors.error, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Button(
                onClick = { picker.launch(arrayOf("image/*", "video/*", "audio/*", "application/pdf")) },
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
                    if (state.staging) stringResource(R.string.safety_report_step_evidence_protecting) else stringResource(R.string.safety_report_step_evidence_attach),
                    fontWeight = FontWeight.Bold,
                )
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
            Text(stringResource(R.string.safety_report_step_review_title), fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.White)

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
        Text(value, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SafetyReportReceiptScreen(
    reportId: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.safety_report_receipt_title), fontWeight = FontWeight.Black, fontSize = 17.sp, color = Color.White)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.safety_back), tint = Color.White)
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
            SafetyPulse(state = PulseState.NOMINAL, size = 88.dp)

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                stringResource(R.string.safety_report_receipt_saved_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                stringResource(R.string.safety_report_receipt_saved_desc),
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
                border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f)),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(stringResource(R.string.safety_report_receipt_id), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary)
                            Text(reportId.take(12) + "...", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
                        Text(stringResource(R.string.safety_report_receipt_status_local), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MeetColors.warning)
                    }

                    HorizontalDivider(color = MeetColors.borderSubtle, thickness = 1.dp)

                    Text(
                        stringResource(R.string.safety_report_receipt_network_desc),
                        fontSize = 11.sp,
                        color = MeetColors.textSecondary,
                        lineHeight = 16.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

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
