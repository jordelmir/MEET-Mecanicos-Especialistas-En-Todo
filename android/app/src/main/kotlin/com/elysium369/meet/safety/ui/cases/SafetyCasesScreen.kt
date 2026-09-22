package com.elysium369.meet.safety.ui.cases

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elysium369.meet.safety.data.local.SafetyPublicCaseEntity
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyCasesScreen(
    onBack: () -> Unit = {},
    onCaseClick: (String) -> Unit = {},
    viewModel: SafetyCasesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("CASOS PÚBLICOS", fontWeight = FontWeight.Black, fontSize = 17.sp, color = Color.White)
                        Text(
                            if (uiState.isLoading) "Cargando…"
                            else "${uiState.totalCases} caso(s) documentado(s)",
                            fontSize = 11.sp,
                            color = MeetColors.cyberCyan,
                        )
                    }
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
        when {
            uiState.isLoading && uiState.cases.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = MeetColors.cyberCyan, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Cargando casos…", fontSize = 13.sp, color = MeetColors.textSecondary)
                }
            }

            uiState.error != null && uiState.cases.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                ) {
                    Text("ERROR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.error, letterSpacing = 1.2.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(uiState.error!!, fontSize = 14.sp, color = MeetColors.textSecondary)
                    androidx.compose.material3.TextButton(onClick = viewModel::refresh) { Text("Reintentar") }
                }
            }

            uiState.cases.isEmpty() -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                            border = BorderStroke(1.dp, MeetColors.borderSubtle),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("SIN CASOS PUBLICADOS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.2.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Los casos se publican solo tras revisión editorial. Ninguna acusación ciudadana se convierte en caso público automáticamente.",
                                    fontSize = 13.sp,
                                    color = MeetColors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    uiState.error?.let { message -> item { Text(message, color = MeetColors.warning) } }
                    item { androidx.compose.material3.TextButton(onClick = viewModel::refresh) { Text("Actualizar") } }
                    items(uiState.cases, key = { it.caseId }) { case ->
                        CaseCard(case = case, onClick = { onCaseClick(case.caseId) })
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
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
        shape = RoundedCornerShape(16.dp),
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
                    fontSize = 14.sp,
                    color = MeetColors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
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
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column {
                    Text("EVENTOS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.2.sp)
                    Text("${case.eventCount}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MeetColors.textPrimary)
                }
                Column {
                    Text("RECLAMOS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.2.sp)
                    Text("${case.claimCount}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MeetColors.textPrimary)
                }
                Column {
                    Text("CONFIANZA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.2.sp)
                    Text("${(case.confidenceScore * 100).toInt()}%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MeetColors.textPrimary)
                }
            }
        }
    }
}
