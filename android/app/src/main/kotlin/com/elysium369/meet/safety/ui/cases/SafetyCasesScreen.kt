package com.elysium369.meet.safety.ui.cases

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elysium369.meet.R
import com.elysium369.meet.safety.data.local.SafetyPublicCaseEntity
import com.elysium369.meet.safety.ui.common.SafetyEmptyState
import com.elysium369.meet.safety.ui.common.SafetyShimmer
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyCasesScreen(
    onBack: () -> Unit = {},
    onCaseClick: (String) -> Unit = {},
    viewModel: SafetyCasesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedLifecycle by rememberSaveable { mutableStateOf<String?>(null) }

    val filteredCases = remember(uiState.cases, searchQuery, selectedLifecycle) {
        uiState.cases.filter { case ->
            val matchesQuery = searchQuery.isBlank() ||
                case.title.contains(searchQuery, ignoreCase = true) ||
                case.publicSummary.contains(searchQuery, ignoreCase = true)
            val matchesLifecycle = selectedLifecycle == null || case.lifecycle.equals(selectedLifecycle, ignoreCase = true)
            matchesQuery && matchesLifecycle
        }
    }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.safety_cases_title),
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = Color.White,
                        )
                        Text(
                            if (uiState.isLoading) stringResource(R.string.safety_loading)
                            else stringResource(R.string.safety_cases_count_subtitle, uiState.totalCases),
                            fontSize = 11.sp,
                            color = MeetColors.cyberCyan,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.safety_back),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Search and filter header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar casos públicos por título o resumen...", fontSize = 12.sp, color = MeetColors.textSecondary) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Limpiar", tint = MeetColors.textSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MeetColors.cyberCyan,
                        unfocusedBorderColor = MeetColors.borderSubtle,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                )

                // Lifecycle filter chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = selectedLifecycle == null,
                            onClick = { selectedLifecycle = null },
                            label = { Text("Todos", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MeetColors.cyberCyan.copy(alpha = 0.2f),
                                selectedLabelColor = MeetColors.cyberCyan,
                            ),
                        )
                    }
                    val lifecycles = listOf("OPEN" to "Abiertos", "UNDER_REVIEW" to "En revisión", "CLOSED" to "Cerrados")
                    items(lifecycles) { (key, label) ->
                        FilterChip(
                            selected = selectedLifecycle == key,
                            onClick = { selectedLifecycle = if (selectedLifecycle == key) null else key },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = when (key) {
                                    "OPEN" -> MeetColors.cyberCyan.copy(alpha = 0.2f)
                                    "CLOSED" -> MeetColors.neonGreen.copy(alpha = 0.2f)
                                    else -> MeetColors.warning.copy(alpha = 0.2f)
                                },
                                selectedLabelColor = when (key) {
                                    "OPEN" -> MeetColors.cyberCyan
                                    "CLOSED" -> MeetColors.neonGreen
                                    else -> MeetColors.warning
                                },
                            ),
                        )
                    }
                }
            }

            when {
                uiState.isLoading && uiState.cases.isEmpty() -> {
                    SafetyShimmer(modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                }

                uiState.error != null && uiState.cases.isEmpty() -> {
                    SafetyEmptyState(
                        icon = Icons.Filled.FolderOpen,
                        title = stringResource(R.string.safety_error),
                        message = uiState.error!!,
                        actionLabel = stringResource(R.string.safety_retry),
                        onAction = viewModel::refresh,
                    )
                }

                filteredCases.isEmpty() -> {
                    SafetyEmptyState(
                        icon = Icons.Filled.FolderOpen,
                        title = if (searchQuery.isNotBlank() || selectedLifecycle != null) "Sin resultados" else stringResource(R.string.safety_cases_empty_title),
                        message = if (searchQuery.isNotBlank() || selectedLifecycle != null) "No se encontraron casos con los filtros seleccionados." else stringResource(R.string.safety_cases_empty_desc),
                        actionLabel = if (searchQuery.isNotBlank() || selectedLifecycle != null) "Limpiar filtros" else null,
                        onAction = { searchQuery = ""; selectedLifecycle = null },
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item { Spacer(modifier = Modifier.height(4.dp)) }
                        uiState.error?.let { message ->
                            item {
                                Text(
                                    message,
                                    color = MeetColors.warning,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                        }

                        items(filteredCases, key = { it.caseId }) { case ->
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn() + slideInVertically { it / 4 },
                            ) {
                                CaseCard(case = case, onClick = { onCaseClick(case.caseId) })
                            }
                        }

                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaseCard(case: SafetyPublicCaseEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    case.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                val badgeColor = when (case.lifecycle) {
                    "OPEN" -> MeetColors.cyberCyan
                    "CLOSED" -> MeetColors.neonGreen
                    "UNDER_REVIEW" -> MeetColors.warning
                    else -> MeetColors.textSecondary
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        case.lifecycle,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = badgeColor,
                    )
                }
            }

            if (case.publicSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    case.publicSummary,
                    fontSize = 12.sp,
                    color = MeetColors.textSecondary,
                    maxLines = 2,
                    lineHeight = 16.sp,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Metrics Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MeetColors.backgroundDeep)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(stringResource(R.string.safety_cases_events), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary)
                    Text("${case.eventCount}", fontSize = 15.sp, fontWeight = FontWeight.Black, color = MeetColors.cyberCyan)
                }
                Column {
                    Text(stringResource(R.string.safety_cases_claims), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary)
                    Text("${case.claimCount}", fontSize = 15.sp, fontWeight = FontWeight.Black, color = MeetColors.neonGreen)
                }
                Column {
                    Text(stringResource(R.string.safety_cases_confidence), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary)
                    Text("${(case.confidenceScore * 100).toInt()}%", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
            }
        }
    }
}
