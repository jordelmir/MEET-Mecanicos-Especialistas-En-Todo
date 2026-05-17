package com.elysium369.meet.ui.screens.scanner

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.obd.DiagnosticSeverity
import com.elysium369.meet.core.obd.Mode06TestResult
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.theme.MeetColors

@Composable
fun ScannerMonitorsTab(viewModel: ObdViewModel) {
    val mode06Results by viewModel.mode06Results.collectAsState()
    val isReading by viewModel.isReadingMode06.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = connectionState == ObdState.CONNECTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeetColors.backgroundDark)
    ) {
        // Header with Premium Action
        HeaderSection(isConnected, isReading) { viewModel.readMode06() }

        // Health Summary Index
        if (mode06Results.isNotEmpty()) {
            HealthSummarySection(mode06Results)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Results list
        if (mode06Results.isEmpty() && !isReading) {
            EmptyMonitorsState(isConnected)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(mode06Results) { result ->
                    ExpertMonitorCard(result)
                }
            }
        }
    }
}

@Composable
private fun HeaderSection(isConnected: Boolean, isReading: Boolean, onRead: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "Módulos No-Continuos",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "Análisis Profundo de Diagnóstico (Mode $06)",
                color = MeetColors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }

        EliteCard(
            onClick = if (isConnected && !isReading) onRead else null,
            backgroundColor = if (isConnected) MeetColors.neonGreen.copy(alpha = 0.1f) else MeetColors.borderBlue.copy(alpha = 0.2f),
            borderColor = if (isConnected) MeetColors.neonGreen.copy(alpha = 0.5f) else MeetColors.textSecondary.copy(alpha = 0.3f),
            shape = RoundedCornerShape(12.dp),
            glowColor = if (isConnected) MeetColors.neonGreen.copy(alpha = 0.3f) else Color.Transparent
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isReading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MeetColors.neonGreen,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ESCANEO...", color = MeetColors.neonGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                } else {
                    Text("FULL SCAN", color = if (isConnected) MeetColors.neonGreen else MeetColors.textSecondary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun HealthSummarySection(results: List<Mode06TestResult>) {
    val total = results.size
    val passed = results.count { it.passed }
    val failed = results.count { !it.passed }
    val healthIndex = if (total > 0) (passed.toFloat() / total.toFloat() * 100).toInt() else 100
    
    val healthColor = when {
        healthIndex > 90 -> MeetColors.neonGreen
        healthIndex > 70 -> MeetColors.warning
        else -> MeetColors.error
    }

    EliteCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        backgroundColor = MeetColors.cardBackground,
        borderColor = healthColor.copy(alpha = 0.3f),
        glowColor = healthColor.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(60.dp)) {
                CircularProgressIndicator(
                    progress = healthIndex / 100f,
                    color = healthColor,
                    strokeWidth = 6.dp,
                    trackColor = healthColor.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxSize()
                )
                Text("$healthIndex%", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text("Health Index", color = MeetColors.textSecondary, style = MaterialTheme.typography.labelSmall)
                Text(
                    if (failed == 0) "SISTEMAS ÓPTIMOS" else "ATENCIÓN REQUERIDA",
                    color = healthColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "Se analizaron $total parámetros de monitoreo.",
                    color = MeetColors.textSecondary.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Row(verticalAlignment = Alignment.Bottom) {
                SummaryStat(passed.toString(), "OK", MeetColors.neonGreen)
                Spacer(modifier = Modifier.width(12.dp))
                SummaryStat(failed.toString(), "FAIL", if (failed > 0) MeetColors.error else MeetColors.textMuted)
            }
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(label, color = color.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
    }
}

@Composable
private fun ExpertMonitorCard(result: Mode06TestResult) {
    val statusColor = if (result.passed) MeetColors.neonGreen else MeetColors.error
    val cardGlow = if (!result.passed) statusColor.copy(alpha = 0.2f) else Color.Transparent

    EliteCard(
        backgroundColor = MeetColors.cardBackground,
        borderColor = statusColor.copy(alpha = if (result.passed) 0.1f else 0.4f),
        glowColor = cardGlow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Component & Status
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (result.passed) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    result.componentName.uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (result.passed) "PASSED" else "FAILED",
                    color = statusColor,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Text(
                result.testName,
                color = MeetColors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Values Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ValueItem("VALOR", String.format("%.3f %s", result.value, result.unit), if (result.passed) Color.White else statusColor)
                ValueItem("MIN", result.minLimit?.let { String.format("%.3f", it) } ?: "---", MeetColors.textSecondary)
                ValueItem("MAX", result.maxLimit?.let { String.format("%.3f", it) } ?: "---", MeetColors.textSecondary)
            }

            // Pro Tip Section (Only if failed or critical)
            if (!result.passed && result.proTip != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .border(1.dp, statusColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = statusColor, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CONSEJO DEL EXPERTO MEET", color = statusColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            result.proTip ?: "",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ValueItem(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = MeetColors.textSecondary.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EmptyMonitorsState(isConnected: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("🔍", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                if (isConnected) "Sistemas Listos para Análisis" else "Escáner Desconectado",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (isConnected) "El Mode \$06 permite ver los resultados de las pruebas internas que realiza el ECU. Presiona FULL SCAN para iniciar."
                else "Conecta el adaptador OBD2 para acceder a los datos de ingeniería profunda del vehículo.",
                color = MeetColors.textSecondary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
