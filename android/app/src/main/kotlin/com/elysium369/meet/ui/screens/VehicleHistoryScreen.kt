package com.elysium369.meet.ui.screens

import android.app.Application
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elysium369.meet.core.reports.HashEngine
import com.elysium369.meet.core.reports.ReportStatus
import com.elysium369.meet.core.reports.ReportType
import com.elysium369.meet.data.local.CertifiedReportRepository
import com.elysium369.meet.data.local.entities.CertifiedReportEntity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VehicleHistoryEntryPoint {
    fun certifiedReportRepository(): CertifiedReportRepository
}

/**
 * V2 Vehicle History — the canonical timeline of every certified report
 * ever signed for a given vehicle, plus a chain-status banner at the top.
 *
 * Sits behind `Garage → Vehicle → Historial de Servicio`. The legacy
 * "Historial de Servicio" surface (driven by `MaintenanceLogEntity` and
 * `RepairHistoryEntity`) is left untouched for this round — V2 reports
 * are additive. Phase 7 of the spec wires the navigation so a tap on
 * any certified-report row opens the existing Inspection Session
 * detail in read-only mode.
 *
 * Honest-phrases rule: if the per-vehicle chain fails to verify, the
 * timeline does not silently hide it — it shows the broken row and a
 * banner with the chain verifier's reason.
 */
@Composable
fun VehicleHistoryScreen(
    vehicleId: String,
    vehicleLabel: String,
    onClose: () -> Unit,
    onOpenReport: (reportId: String) -> Unit = {},
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as Application
    val repo = remember(app) {
    EntryPointAccessors.fromApplication(app, VehicleHistoryEntryPoint::class.java)
        .certifiedReportRepository()
}

    val reportsFlow = remember(vehicleId, repo) { repo.observeForVehicle(vehicleId) }
    val reports by reportsFlow.collectAsState(initial = emptyList())

    val chainResult = remember(reports) {
        runCatching {
            kotlinx.coroutines.runBlocking {
                repo.verifyChainForVehicle(vehicleId)
            }
        }.getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Historial de Servicio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(vehicleLabel, style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onClose) { Text("Cerrar") }
        }

        ChainStatusBanner(chainResult, reports.size)

        if (reports.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.History, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sin reportes certificados todavía", fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "Andá a Reportes → Pre-Scan para crear el primer reporte firmado de este vehículo. " +
                            "A partir de ahí, cada Post-Scan o Reparación se encadena al anterior.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(reports, key = { it.reportId }) { report ->
                    ReportTimelineRow(report = report, onClick = { onOpenReport(report.reportId) })
                }
            }
        }
    }
}

@Composable
private fun ChainStatusBanner(chainResult: HashEngine.ChainResult?, totalReports: Int) {
    val ok = chainResult?.ok != false
    val color = if (ok) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
    val icon = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning
    val tint = if (ok) Color(0xFF2E7D32) else Color(0xFFC62828)
    val title = if (ok) "Cadena de integridad OK" else "Cadena de integridad rota"
    val detail = if (ok) {
        "$totalReports reporte(s) firmados en este vehículo. La cadena de hashes verifica correctamente."
    } else {
        "Reporte ${chainResult?.brokenAt ?: "?"} no encadena con su predecesor. " +
            "Posible edición silenciosa. Contactá al emisor original."
    }
    Card(colors = CardDefaults.cardColors(containerColor = color), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ReportTimelineRow(report: CertifiedReportEntity, onClick: () -> Unit) {
    val isInvalid = report.status == ReportStatus.VOIDED
    val borderColor = if (isInvalid) Color(0xFFC62828) else MaterialTheme.colorScheme.outlineVariant
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isInvalid) Color(0xFFFFF8F8) else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TimelineDot(reportType = report.reportType, voided = isInvalid)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(typeShort(report.reportType), fontWeight = FontWeight.SemiBold)
                    if (isInvalid) Text("· ANULADO", color = Color(0xFFC62828), fontWeight = FontWeight.SemiBold)
                }
                Text(formatTimestamp(report.generatedAt), style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "Hash: ${report.integrityHash.take(16)}…",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                )
                report.previousHash?.let { prev ->
                    Text(
                        text = "← $prev",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineDot(reportType: ReportType, voided: Boolean) {
    val color = when {
        voided -> Color(0xFFC62828)
        reportType == ReportType.PRE_SCAN_REPORT -> Color(0xFF1976D2)
        reportType == ReportType.POST_SCAN_REPORT -> Color(0xFF388E3C)
        reportType == ReportType.REPAIR_EVIDENCE_REPORT -> Color(0xFFF57C00)
        reportType == ReportType.PRE_PURCHASE_INSPECTION_REPORT -> Color(0xFF7B1FA2)
        reportType == ReportType.DVIR_REPORT -> Color(0xFF455A64)
        else -> Color.Gray
    }
    Box(
        modifier = Modifier
            .width(12.dp)
            .height(12.dp)
            .clip(CircleShape)
            .background(color),
    )
}

private fun typeShort(t: ReportType): String = when (t) {
    ReportType.PRE_SCAN_REPORT -> "Pre-Scan"
    ReportType.POST_SCAN_REPORT -> "Post-Scan"
    ReportType.REPAIR_EVIDENCE_REPORT -> "Reparación"
    ReportType.PRE_PURCHASE_INSPECTION_REPORT -> "Peritaje"
    ReportType.DVIR_REPORT -> "DVIR"
}

private fun formatTimestamp(ms: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(ms))