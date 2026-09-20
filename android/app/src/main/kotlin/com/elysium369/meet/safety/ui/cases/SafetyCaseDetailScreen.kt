package com.elysium369.meet.safety.ui.cases

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elysium369.meet.R
import com.elysium369.meet.safety.domain.SafetyChallengeKind
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
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.safety_public_case_title)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.safety_public_back)) }
        }, actions = { TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.safety_public_refresh)) } })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.safety_public_loading)) }
            state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            val case = state.case
            if (case == null && !state.loading) item { Text(stringResource(R.string.safety_public_case_missing)) }
            if (case != null) {
                item { Text(case.title, style = MaterialTheme.typography.headlineSmall) }
                item { Text(case.publicSummary) }
                item { Text(stringResource(R.string.safety_public_claim_state, case.lifecycle)) }
                item { Text(stringResource(R.string.safety_public_counts, case.sourceCount, case.evidenceCount)) }
                item { Text(stringResource(R.string.safety_public_reviewed, safetyPublicDate(case.lastUpdatedAt))) }
                item { Text(stringResource(R.string.safety_public_epistemic_notice), style = MaterialTheme.typography.bodySmall) }
                item {
                    OutlinedButton(onClick = { challengeOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.safety_challenge_action))
                    }
                }
                if (state.challengeQueued) item { Text(stringResource(R.string.safety_challenge_queued)) }
                item { Text(stringResource(R.string.safety_public_timeline), style = MaterialTheme.typography.titleLarge) }
                if (state.timeline.isEmpty()) item { Text(stringResource(R.string.safety_public_no_timeline)) }
                items(state.timeline, key = { "timeline:${it.milestoneId}" }) { event ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(event.eventType, style = MaterialTheme.typography.titleMedium)
                            Text(event.publicSummary)
                            Text(stringResource(R.string.safety_public_occurred, safetyPublicDate(event.occurredAt)))
                            Text(stringResource(R.string.safety_public_recorded, safetyPublicDate(event.recordedAt)))
                            Text(stringResource(R.string.safety_public_counts, event.sourceCount, event.evidenceCount))
                        }
                    }
                }
                item { Text(stringResource(R.string.safety_public_claims), style = MaterialTheme.typography.titleLarge) }
                if (state.claims.isEmpty()) item { Text(stringResource(R.string.safety_public_no_claims)) }
                items(state.claims, key = { "claim:${it.claimId}" }) { claim ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(claim.predicate, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.safety_public_claim_state, claim.claimState))
                            Text(stringResource(R.string.safety_public_counts, claim.independentSourceCount, claim.evidenceCount))
                            Text(stringResource(R.string.safety_public_source_mix, claim.civilSourceCount, claim.journalisticSourceCount, claim.publicRecordSourceCount, claim.documentarySourceCount, claim.institutionalSourceCount))
                        }
                    }
                }
            }
        }
    }
    if (challengeOpen) {
        AlertDialog(
            onDismissRequest = { if (!state.challengeSubmitting) challengeOpen = false },
            title = { Text(stringResource(R.string.safety_challenge_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SafetyChallengeKind.entries.forEach { option ->
                        Row {
                            RadioButton(selected = kind == option, onClick = { kind = option })
                            Text(
                                when (option) {
                                    SafetyChallengeKind.REPORT_ERROR -> stringResource(R.string.safety_challenge_error)
                                    SafetyChallengeKind.CONTRARY_EVIDENCE -> stringResource(R.string.safety_challenge_contrary)
                                    SafetyChallengeKind.RECTIFICATION -> stringResource(R.string.safety_challenge_rectification)
                                },
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                    OutlinedTextField(narrative, { narrative = it.take(5_000) }, label = { Text(stringResource(R.string.safety_challenge_explanation)) })
                    OutlinedTextField(sourceUrl, { sourceUrl = it.take(2_000) }, label = { Text(stringResource(R.string.safety_challenge_source_optional)) })
                    Text(stringResource(R.string.safety_challenge_notice), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = narrative.trim().length >= 10 && !state.challengeSubmitting,
                    onClick = {
                        viewModel.submitChallenge(kind, null, narrative, sourceUrl.ifBlank { null })
                        challengeOpen = false
                    },
                ) { Text(stringResource(R.string.safety_challenge_save)) }
            },
            dismissButton = { TextButton(onClick = { challengeOpen = false }) { Text(stringResource(R.string.safety_challenge_cancel)) } },
        )
    }
}
