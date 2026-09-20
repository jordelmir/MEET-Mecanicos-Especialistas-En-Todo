package com.elysium369.meet.safety.ui.accountability

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.TextButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.elysium369.meet.safety.data.PublicAccountabilityEvent
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyAccountabilityScreen(
    onBack: () -> Unit = {},
    viewModel: SafetyAccountabilityViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ACCOUNTABILITY", fontWeight = FontWeight.Black, fontSize = 17.sp, color = Color.White)
                        Text(
                            if (uiState.isLoading) "Cargando…"
                            else "${uiState.totalEvents} evento(s) registrado(s)",
                            fontSize = 11.sp,
                            color = MeetColors.cyberCyan,
                        )
                    }
                },
                actions = { TextButton(onClick = viewModel::refresh) { Text("Actualizar") } },
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
            uiState.isLoading -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = MeetColors.cyberCyan, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Cargando eventos…", fontSize = 13.sp, color = MeetColors.textSecondary)
                }
            }

            uiState.error != null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                ) {
                    Text("ERROR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.error, letterSpacing = 1.2.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(uiState.error!!, fontSize = 14.sp, color = MeetColors.textSecondary)
                }
            }

            uiState.events.isEmpty() -> {
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
                                Text("SIN EVENTOS REGISTRADOS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.2.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "El ledger de accountability mostrará eventos observables: envío de reporte, confirmación de recepción, respuesta documentada, acción pública encontrada.",
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
                    item { Text("Sin información pública no significa que no hubo acción.", color = MeetColors.textSecondary) }
                    uiState.events.groupBy { it.case_id to it.institution_ref }.forEach { (_, events) ->
                        item { AccountabilityClockCard(events) }
                    }
                    items(uiState.events, key = { it.event_id }) { event ->
                        AccountabilityEventCard(event = event)
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun AccountabilityEventCard(event: PublicAccountabilityEvent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.size(10.dp),
                shape = CircleShape,
                color = when (event.event_type) {
                    "REPORT_SENT" -> MeetColors.cyberCyan
                    "DELIVERY_CONFIRMED" -> MeetColors.neonGreen
                    "PUBLIC_ACTION_FOUND" -> MeetColors.warning
                    "RESPONSE_DOCUMENTED" -> MeetColors.neonGreen
                    else -> MeetColors.textSecondary
                },
            ) {}
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    event.event_type.replace("_", " "),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MeetColors.textPrimary,
                )
                if (event.case_title != null) {
                    Text(
                        event.case_title,
                        fontSize = 12.sp,
                        color = MeetColors.cyberCyan,
                    )
                }
                Text(
                    event.institution_ref,
                    fontSize = 12.sp,
                    color = MeetColors.textSecondary,
                )
                Text(
                    event.occurred_at.take(10),
                    fontSize = 11.sp,
                    color = MeetColors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun AccountabilityClockCard(events: List<PublicAccountabilityEvent>) {
    val metrics = AccountabilityClock.calculate(events, java.time.Instant.now())
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(events.first().case_title ?: "Caso documentado", fontWeight = FontWeight.Bold)
            Text(events.first().institution_ref)
            Text("Días desde envío: ${metrics.daysSinceReport ?: "Dato no capturado"}")
            Text("Horas hasta recepción: ${metrics.timeToReceipt?.toHours() ?: "Sin recepción documentada"}")
            Text("Horas hasta respuesta: ${metrics.timeToFirstResponse?.toHours() ?: "Sin respuesta documentada"}")
            Text("Días desde última acción documentada: ${metrics.daysSinceLastAction ?: "Dato no capturado"}")
        }
    }
}
