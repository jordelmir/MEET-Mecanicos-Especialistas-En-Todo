package com.elysium369.meet.safety.ui.report

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyMyReportsScreen(
    viewModel: SafetyMyReportsViewModel,
    onBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    var reportToWithdraw by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("MIS REPORTES", fontWeight = FontWeight.Black, fontSize = 17.sp, color = Color.White)
                        Text("${state.totalReports} reporte(s) · ${state.pendingCount} pendiente(s)", fontSize = 11.sp, color = MeetColors.cyberCyan)
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
            state.isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = MeetColors.cyberCyan, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Cargando reportes…", fontSize = 13.sp, color = MeetColors.textSecondary)
                }
            }

            state.error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(32.dp),
                ) {
                    Text("ERROR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.error, letterSpacing = 1.2.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(state.error!!, fontSize = 14.sp, color = MeetColors.textSecondary)
                }
            }

            state.reports.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(32.dp),
                ) {
                    Text("SIN REPORTES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MeetColors.textSecondary, letterSpacing = 1.2.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tus reportes de seguridad aparecerán aquí.", fontSize = 14.sp, color = MeetColors.textSecondary)
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.reports) { report ->
                        Card(
                            report = report,
                            withdrawing = state.withdrawingReportId == report.reportId,
                            onWithdraw = { reportToWithdraw = report.reportId },
                        )
                    }
                }
            }
        }
    }
    reportToWithdraw?.let { reportId ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { reportToWithdraw = null },
            title = { Text("Retirar reporte") },
            text = { Text("Se retirará del servidor, desaparecerá del mapa y se borrará el contenido privado. La constancia mínima de retiro se conserva para auditoría.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { reportToWithdraw = null; viewModel.withdraw(reportId) }) { Text("RETIRAR") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { reportToWithdraw = null }) { Text("CANCELAR") } },
        )
    }
}

@Composable
private fun Card(report: com.elysium369.meet.safety.data.local.SafetyReportEntity, withdrawing: Boolean, onWithdraw: () -> Unit) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.borderSubtle),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    report.category.replace("_", " "),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MeetColors.textPrimary,
                )
                Text(
                    report.syncState,
                    fontSize = 12.sp,
                    color = when (report.syncState) {
                        "SYNCED" -> MeetColors.neonGreen
                        "FAILED" -> MeetColors.error
                        else -> MeetColors.textSecondary
                    },
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(report.localState, fontSize = 12.sp, color = MeetColors.textSecondary)
            if (report.serverVersion > 0) {
                Text("v${report.serverVersion}", fontSize = 11.sp, color = MeetColors.textSecondary)
            }
            androidx.compose.material3.TextButton(onClick = onWithdraw, enabled = !withdrawing) {
                Text(if (withdrawing) "RETIRANDO…" else "QUITAR REPORTE", color = MeetColors.error)
            }
        }
    }
}

private object CardDefaults {
    @Composable
    fun cardColors(containerColor: Color) = androidx.compose.material3.CardDefaults.cardColors(containerColor = containerColor)
}
