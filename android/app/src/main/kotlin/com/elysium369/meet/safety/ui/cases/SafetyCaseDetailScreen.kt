package com.elysium369.meet.safety.ui.cases

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elysium369.meet.R
import com.elysium369.meet.safety.domain.SafetyChallengeKind
import com.elysium369.meet.safety.ui.common.SafetyEmptyState
import com.elysium369.meet.safety.ui.common.SafetyShimmer
import com.elysium369.meet.safety.ui.common.SafetyTimeline
import com.elysium369.meet.safety.ui.common.TimelineNode
import com.elysium369.meet.ui.theme.MeetColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

fun safetyPublicDate(value: Long?): String = value?.takeIf { it > 0 }?.let {
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it))
} ?: "Dato no capturado"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyCaseDetailScreen(onBack: () -> Unit, viewModel: SafetyCaseDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var challengeOpen by rememberSaveable { mutableStateOf(false) }
    var narrative by rememberSaveable { mutableStateOf("") }
    var sourceUrl by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(SafetyChallengeKind.REPORT_ERROR) }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.safety_public_case_title),
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = Color.White,
                        )
                        Text(
                            if (state.loading) stringResource(R.string.safety_loading) else "Expediente público auditado",
                            fontSize = 11.sp,
                            color = MeetColors.cyberCyan,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.safety_public_back),
                            tint = Color.White,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::refresh) {
                        Text(stringResource(R.string.safety_public_refresh), color = MeetColors.cyberCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.loading && state.case == null) {
                item {
                    SafetyShimmer(Modifier.fillMaxWidth())
                }
            }

            state.error?.let { error ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                        border = BorderStroke(1.dp, MeetColors.error.copy(alpha = 0.6f)),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = MeetColors.error)
                            Spacer(Modifier.width(10.dp))
                            Text(error, color = MeetColors.error, fontSize = 13.sp)
                        }
                    }
                }
            }

            val case = state.case
            if (case == null && !state.loading) {
                item {
                    SafetyEmptyState(
                        icon = Icons.Filled.Shield,
                        title = stringResource(R.string.safety_public_case_missing),
                        message = "El expediente solicitado no está disponible o requiere verificación de autoridad.",
                        actionLabel = stringResource(R.string.safety_retry),
                        onAction = viewModel::refresh,
                    )
                }
            }

            if (case != null) {
                // Header Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.45f)),
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    case.lifecycle,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (case.lifecycle) {
                                        "OPEN" -> MeetColors.cyberCyan
                                        "CLOSED" -> MeetColors.neonGreen
                                        "UNDER_REVIEW" -> MeetColors.warning
                                        else -> MeetColors.textSecondary
                                    },
                                )
                                Text(
                                    stringResource(R.string.safety_public_reviewed, safetyPublicDate(case.lastUpdatedAt)),
                                    fontSize = 11.sp,
                                    color = MeetColors.textSecondary,
                                )
                            }

                            Text(
                                case.title,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = Color.White,
                            )

                            Text(
                                case.publicSummary,
                                fontSize = 13.sp,
                                color = MeetColors.textPrimary,
                                lineHeight = 19.sp,
                            )

                            HorizontalDivider(color = MeetColors.borderSubtle, thickness = 1.dp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text("FUENTES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.sp)
                                    Text("${case.sourceCount}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MeetColors.cyberCyan)
                                }
                                Column {
                                    Text("EVIDENCIAS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.sp)
                                    Text("${case.evidenceCount}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MeetColors.neonGreen)
                                }
                                Column {
                                    Text("CONFIANZA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.sp)
                                    Text("${(case.confidenceScore * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }

                // Epistemic Disclosure Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground.copy(alpha = 0.6f)),
                        border = BorderStroke(1.dp, MeetColors.borderSubtle),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.Balance, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.safety_public_epistemic_notice),
                                fontSize = 11.sp,
                                color = MeetColors.textSecondary,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }

                // Challenge Button & status
                item {
                    OutlinedButton(
                        onClick = { challengeOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MeetColors.cyberCyan),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MeetColors.cyberCyan),
                    ) {
                        Icon(Icons.Filled.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.safety_challenge_action), fontWeight = FontWeight.Bold)
                    }
                }

                if (state.challengeQueued) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MeetColors.neonGreen.copy(alpha = 0.1f)),
                            border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.4f)),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MeetColors.neonGreen)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.safety_challenge_queued), color = MeetColors.neonGreen, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Visual Timeline
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.safety_public_timeline),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                if (state.timeline.isEmpty()) {
                    item {
                        Text(stringResource(R.string.safety_public_no_timeline), color = MeetColors.textSecondary, fontSize = 13.sp)
                    }
                } else {
                    item {
                        val timelineNodes = state.timeline.map { event ->
                            TimelineNode(
                                id = event.milestoneId,
                                title = event.eventType.replace("_", " "),
                                subtitle = event.publicSummary,
                                timestamp = "Ocurrió: ${safetyPublicDate(event.occurredAt)} • Registrado: ${safetyPublicDate(event.recordedAt)}",
                                icon = Icons.Filled.EventNote,
                                color = when (event.eventType) {
                                    "INCIDENT_OCCURRED" -> Color(0xFFFF4444)
                                    "REPORT_SUBMITTED" -> MeetColors.cyberCyan
                                    "PUBLIC_ACTION_FOUND" -> MeetColors.warning
                                    "ARREST_DOCUMENTED" -> MeetColors.neonGreen
                                    else -> MeetColors.cyberCyan
                                },
                                detail = "Fuentes: ${event.sourceCount} • Evidencias anexas: ${event.evidenceCount}",
                            )
                        }
                        SafetyTimeline(nodes = timelineNodes)
                    }
                }

                // Claims Section
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.safety_public_claims),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                if (state.claims.isEmpty()) {
                    item {
                        Text(stringResource(R.string.safety_public_no_claims), color = MeetColors.textSecondary, fontSize = 13.sp)
                    }
                } else {
                    items(state.claims, key = { "claim:${it.claimId}" }) { claim ->
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                            border = BorderStroke(1.dp, MeetColors.borderSubtle),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        claim.predicate,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        claim.claimState,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (claim.claimState) {
                                            "CORROBORATED", "STRONGLY_CORROBORATED" -> MeetColors.neonGreen
                                            "ALLEGED", "OBSERVED" -> MeetColors.cyberCyan
                                            "DISPUTED", "CONTRADICTED" -> MeetColors.error
                                            else -> MeetColors.warning
                                        },
                                    )
                                }
                                Text(
                                    stringResource(R.string.safety_public_counts, claim.independentSourceCount, claim.evidenceCount),
                                    fontSize = 12.sp,
                                    color = MeetColors.textSecondary,
                                )
                                Text(
                                    stringResource(
                                        R.string.safety_public_source_mix,
                                        claim.civilSourceCount,
                                        claim.journalisticSourceCount,
                                        claim.publicRecordSourceCount,
                                        claim.documentarySourceCount,
                                        claim.institutionalSourceCount,
                                    ),
                                    fontSize = 11.sp,
                                    color = MeetColors.cyberCyan.copy(alpha = 0.8f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (challengeOpen) {
        AlertDialog(
            onDismissRequest = { if (!state.challengeSubmitting) challengeOpen = false },
            containerColor = MeetColors.cardBackground,
            title = {
                Text(
                    stringResource(R.string.safety_challenge_title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SafetyChallengeKind.entries.forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = kind == option,
                                onClick = { kind = option },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MeetColors.cyberCyan,
                                    unselectedColor = MeetColors.textSecondary,
                                ),
                            )
                            Text(
                                when (option) {
                                    SafetyChallengeKind.REPORT_ERROR -> stringResource(R.string.safety_challenge_error)
                                    SafetyChallengeKind.CONTRARY_EVIDENCE -> stringResource(R.string.safety_challenge_contrary)
                                    SafetyChallengeKind.RECTIFICATION -> stringResource(R.string.safety_challenge_rectification)
                                },
                                color = Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }

                    OutlinedTextField(
                        value = narrative,
                        onValueChange = { narrative = it.take(5_000) },
                        label = { Text(stringResource(R.string.safety_challenge_explanation)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeetColors.cyberCyan,
                            unfocusedBorderColor = MeetColors.borderSubtle,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = MeetColors.cyberCyan,
                            unfocusedLabelColor = MeetColors.textSecondary,
                        ),
                    )

                    OutlinedTextField(
                        value = sourceUrl,
                        onValueChange = { sourceUrl = it.take(2_000) },
                        label = { Text(stringResource(R.string.safety_challenge_source_optional)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeetColors.cyberCyan,
                            unfocusedBorderColor = MeetColors.borderSubtle,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = MeetColors.cyberCyan,
                            unfocusedLabelColor = MeetColors.textSecondary,
                        ),
                    )

                    Text(
                        stringResource(R.string.safety_challenge_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MeetColors.textSecondary,
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = narrative.trim().length >= 10 && !state.challengeSubmitting,
                    onClick = {
                        viewModel.submitChallenge(kind, null, narrative, sourceUrl.ifBlank { null })
                        challengeOpen = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeetColors.cyberCyan,
                        contentColor = MeetColors.backgroundDeep,
                    ),
                ) {
                    Text(stringResource(R.string.safety_challenge_save), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { challengeOpen = false }) {
                    Text(stringResource(R.string.safety_challenge_cancel), color = MeetColors.textSecondary)
                }
            },
        )
    }
}
