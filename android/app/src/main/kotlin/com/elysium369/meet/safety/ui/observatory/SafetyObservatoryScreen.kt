package com.elysium369.meet.safety.ui.observatory

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
import com.elysium369.meet.safety.data.SafetyObservatoryMetrics
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyObservatoryScreen(
    onBack: () -> Unit = {},
    viewModel: SafetyObservatoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("OBSERVATORIO", fontWeight = FontWeight.Black, fontSize = 17.sp, color = Color.White)
                        Text("Métricas y tendencias de seguridad", fontSize = 11.sp, color = MeetColors.cyberCyan)
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Filtros sobre ubicación pública aproximada. Fechas en UTC; el fin es exclusivo.", color = MeetColors.textSecondary) }
            item { OutlinedTextField(uiState.filters.from, { viewModel.setFilters(uiState.filters.copy(from = it)) }, label = { Text("Desde: 2026-09-01T00:00:00Z") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(uiState.filters.to, { viewModel.setFilters(uiState.filters.copy(to = it)) }, label = { Text("Hasta: 2026-10-01T00:00:00Z") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(uiState.filters.category, { viewModel.setFilters(uiState.filters.copy(category = it)) }, label = { Text("Código de categoría (vacío: todas)") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(uiState.filters.country, { viewModel.setFilters(uiState.filters.copy(country = it)) }, label = { Text("Código de país") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(uiState.filters.admin1, { viewModel.setFilters(uiState.filters.copy(admin1 = it)) }, label = { Text("Código de provincia / región") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(uiState.filters.admin2, { viewModel.setFilters(uiState.filters.copy(admin2 = it)) }, label = { Text("Código de cantón / distrito") }, modifier = Modifier.fillMaxWidth()) }
            item { Button(onClick = viewModel::refresh, enabled = !uiState.isLoading) { Text("Consultar / reintentar") } }
            if (uiState.isLoading) item { CircularProgressIndicator() }
            uiState.error?.let { error -> item { Text(error, color = MeetColors.error) } }
            uiState.stats?.let { stats -> item { ObservatoryStatsSection(stats) } }
            item { Text("Las sumas corresponden a fuentes por punto publicado; una fuente puede aparecer en varios puntos. No representan personas únicas ni incidencia total.", color = MeetColors.textSecondary) }
        }
    }
}

@Composable
private fun ObservatoryStatsSection(stats: SafetyObservatoryMetrics) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("PROYECCIÓN PÚBLICA", color = MeetColors.textPrimary)
        StatRow("Puntos publicados", stats.public_point_count)
        StatRow("Fuentes independientes por punto", stats.independent_source_count)
        StatRow("Fuentes civiles", stats.civil_source_count)
        StatRow("Fuentes periodísticas", stats.journalistic_source_count)
        StatRow("Registros públicos", stats.public_record_source_count)
        StatRow("Fuentes documentales", stats.documentary_source_count)
        StatRow("Fuentes institucionales", stats.institutional_source_count)
    }
}

@Composable
private fun StatRow(label: String, value: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = MeetColors.textSecondary)
        Text(
            value.toString(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MeetColors.textPrimary,
        )
    }
}
