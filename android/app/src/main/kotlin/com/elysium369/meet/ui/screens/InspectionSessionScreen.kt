package com.elysium369.meet.ui.screens

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.elysium369.meet.core.export.CertifiedReportPdfRenderer
import com.elysium369.meet.core.reports.EvidenceType
import com.elysium369.meet.core.reports.QrPayload
import com.elysium369.meet.core.reports.ReportIntegrityCard
import com.elysium369.meet.core.reports.ReportType
import com.elysium369.meet.core.reports.rememberReportHashingService
import com.elysium369.meet.data.local.CertifiedReportRepository
import com.elysium369.meet.data.local.ReportMappers
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.elysium369.meet.data.local.dao.CertifiedReportDao
import com.elysium369.meet.data.local.dao.DiagnosticSnapshotDao
import com.elysium369.meet.data.local.dao.RepairActionDao
import com.elysium369.meet.data.local.dao.ReportEvidenceDao
import com.elysium369.meet.data.local.dao.ReportSignatureDao
import com.elysium369.meet.data.local.entities.RepairActionEntity
import com.elysium369.meet.data.local.entities.ReportEvidenceEntity
import com.elysium369.meet.diagnostic.DiagnosticProvenance
import com.elysium369.meet.diagnostic.DiagnosticSnapshot
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * V2 Inspection Session — the entry point for the certified-report
 * pipeline.
 *
 * Replaces the old monolithic `ReportScreen.kt` with a chrome that
 * surfaces:
 *   - Active vehicle header (make / model / year / VIN / odometer / OBD
 *     status)
 *   - 5-chip type selector (Pre-Scan / Post-Scan / Repair Evidence /
 *     Peritaje / DVIR)
 *   - Per-type sub-flow composables. Only Pre-Scan is fully wired in
 *     this commit; the other 4 render an honest "Próximamente" card so
 *     the surface is honest about what is and isn't ready, instead of
 *     silently pretending to capture data we have no logic for.
 *
 * The 4-line ReportIntegrityCard addition on the legacy `ReportScreen.kt`
 * (commit `e1076723`) is preserved exactly — this new screen is a
 * parallel surface, not a replacement of the legacy one. Phase 5 of the
 * spec wires Garage → Vehicle → Historial to call this screen directly.
 */
@Composable
fun InspectionSessionScreen(
    vehicleId: String,
    vehicleLabel: String,
    vehicleVin: String?,
    vehicleOdometerKm: Long?,
    obdConnected: Boolean,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as Application
    val scope = rememberCoroutineScope()
    val repo = remember(app) { entryPoint(app).certifiedReportRepository() }
    val hashing = rememberReportHashingService()

    var selectedType by remember { mutableStateOf(ReportType.PRE_SCAN_REPORT) }
    var lastSignedReportId by remember { mutableStateOf<String?>(null) }
    var lastSignedHash by remember { mutableStateOf<String?>(null) }
    var lastQr by remember { mutableStateOf<String?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── header ────────────────────────────────────────────────────
        InspectionHeader(
            vehicleLabel = vehicleLabel,
            vehicleVin = vehicleVin,
            vehicleOdometerKm = vehicleOdometerKm,
            obdConnected = obdConnected,
            onClose = onClose,
        )

        // ── type selector ────────────────────────────────────────────
        Text(
            "Tipo de reporte",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ReportType.values().toList()) { type ->
                TypeChip(
                    label = typeChipLabel(type),
                    selected = selectedType == type,
                    onClick = { selectedType = type },
                )
            }
        }

        Divider()

        // ── sub-flow dispatcher ──────────────────────────────────────
        when (selectedType) {
            ReportType.PRE_SCAN_REPORT -> PreScanSubFlow(
                repo = repo,
                vehicleId = vehicleId,
                obdConnected = obdConnected,
                isWorking = isWorking,
                errorMsg = errorMsg,
                onWorking = { isWorking = it },
                onError = { errorMsg = it },
                onSigned = { reportId, hash, qr ->
                    lastSignedReportId = reportId
                    lastSignedHash = hash
                    lastQr = qr
                },
            )
            ReportType.POST_SCAN_REPORT,
            ReportType.REPAIR_EVIDENCE_REPORT,
            ReportType.PRE_PURCHASE_INSPECTION_REPORT,
            ReportType.DVIR_REPORT -> ComingSoonCard(type = selectedType)
        }

        // ── signed report panel (only after a successful sign) ───────
        if (lastSignedReportId != null && lastSignedHash != null) {
            SignedReportPanel(
                reportId = lastSignedReportId!!,
                integrityHash = lastSignedHash!!,
                qrPayload = lastQr,
                hashing = hashing,
                repo = repo,
            )
        }

        errorMsg?.let { msg ->
            ErrorBanner(msg)
        }
    }
}

// ── header ─────────────────────────────────────────────────────────────────

@Composable
private fun InspectionHeader(
    vehicleLabel: String,
    vehicleVin: String?,
    vehicleOdometerKm: Long?,
    obdConnected: Boolean,
    onClose: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(vehicleLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onClose) { Text("Cerrar") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (obdConnected) Icons.Filled.CheckCircle else Icons.Filled.Error,
                    contentDescription = null,
                    tint = if (obdConnected) Color(0xFF00C853) else Color(0xFFFF6F00),
                )
                Text(
                    text = if (obdConnected) "OBD conectado" else "OBD no disponible. Reporte basado en datos manuales/offline.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            vehicleVin?.let { Text("VIN: $it", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
            vehicleOdometerKm?.let { Text("Odómetro: %,d km".format(it), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

// ── type chip ──────────────────────────────────────────────────────────────

@Composable
private fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    OutlinedButton(
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = bg, contentColor = fg),
        shape = RoundedCornerShape(20.dp),
    ) {
        Text(label, fontWeight = FontWeight.Medium)
    }
}

private fun typeChipLabel(type: ReportType): String = when (type) {
    ReportType.PRE_SCAN_REPORT -> "Pre-Scan"
    ReportType.POST_SCAN_REPORT -> "Post-Scan"
    ReportType.REPAIR_EVIDENCE_REPORT -> "Reparación"
    ReportType.PRE_PURCHASE_INSPECTION_REPORT -> "Peritaje"
    ReportType.DVIR_REPORT -> "DVIR"
}

// ── Pre-Scan sub-flow ──────────────────────────────────────────────────────

@Composable
private fun PreScanSubFlow(
    repo: CertifiedReportRepository,
    vehicleId: String,
    obdConnected: Boolean,
    isWorking: Boolean,
    errorMsg: String?,
    onWorking: (Boolean) -> Unit,
    onError: (String?) -> Unit,
    onSigned: (reportId: String, hash: String, qr: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val deviceId = remember { "android-${android.os.Build.SERIAL ?: UUID.randomUUID().toString()}" }
    val userId = remember { "u-local" }

    var dtcsText by remember { mutableStateOf("") }
    var signerName by remember { mutableStateOf("") }
    var signerRole by remember { mutableStateOf("mecánico") }
    var notesText by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pre-Scan — Captura inicial del estado del vehículo",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            if (!obdConnected) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFF6F00))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Snapshot OBD no disponible.", fontWeight = FontWeight.SemiBold)
                            Text(
                                "El reporte se basará en datos manuales/offline. " +
                                    "Los DTCs que captures aquí deben venir de otra fuente verificable.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = dtcsText,
                onValueChange = { dtcsText = it.uppercase() },
                label = { Text("DTCs (separados por coma)") },
                placeholder = { Text("P0230, P1709") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                label = { Text("Observaciones") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            OutlinedTextField(
                value = signerName,
                onValueChange = { signerName = it },
                label = { Text("Nombre del firmante") },
                modifier = Modifier.fillMaxWidth(),
                isError = signerName.isBlank() && errorMsg != null,
            )

            OutlinedTextField(
                value = signerRole,
                onValueChange = { signerRole = it },
                label = { Text("Rol") },
                placeholder = { Text("mecánico, perito, supervisor…") },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !isWorking && signerName.isNotBlank() && dtcsText.isNotBlank(),
                    onClick = {
                        onWorking(true)
                        onError(null)
                        scope.launch {
                            try {
                                val reportId = "r-${UUID.randomUUID()}"
                                val dtcs = dtcsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                                // Build a synthetic DiagnosticSnapshot. If OBD
                                // is disconnected, the snapshot carries no
                                // live PIDs — only the DTCs the operator
                                // entered manually — and provenance is set
                                // to MANUAL so the renderer must show "no
                                // disponible". This is the explicit honest-
                                // phrases rule: never fabricate live PIDs.
                                val snap = DiagnosticSnapshot(
                                    id = "snap-${reportId}",
                                    vehicleId = vehicleId,
                                    sessionId = null,
                                    createdAtMs = System.currentTimeMillis(),
                                    dtcsActive = if (obdConnected) dtcs else emptyList(),
                                    dtcsPending = if (obdConnected) emptyList() else dtcs,
                                    dtcsPermanent = emptyList(),
                                    freezeFramePidValues = emptyMap(),
                                    readiness = emptyMap(),
                                    provenance = if (obdConnected) DiagnosticProvenance.Real else DiagnosticProvenance.ManualEntry(authorId = userId),
                                    notes = notesText,
                                )

                                val evidences = mutableListOf<ReportEvidenceEntity>()
                                if (notesText.isNotBlank()) {
                                    evidences += ReportMappers.evidenceToEntity(
                                        evidenceId = "ev-notes-${reportId}",
                                        reportId = reportId,
                                        type = EvidenceType.REPAIR_NOTE,
                                        label = "Observaciones del operador",
                                        description = notesText,
                                        uri = "",
                                        hash = com.elysium369.meet.core.reports.HashEngine.sha256Hex(notesText),
                                        capturedAt = System.currentTimeMillis(),
                                        lat = null,
                                        lng = null,
                                    )
                                }
                                // DTCs also become evidence rows so the
                                // chain captures them even when no snapshot
                                // is live. The hash is over the canonical
                                // "DTC1,DTC2,..." string.
                                if (dtcs.isNotEmpty()) {
                                    evidences += ReportMappers.evidenceToEntity(
                                        evidenceId = "ev-dtcs-${reportId}",
                                        reportId = reportId,
                                        type = EvidenceType.OBD_SNAPSHOT,
                                        label = "DTCs capturados",
                                        description = "Active=${if (obdConnected) dtcs.joinToString(",") else "(ninguno, OBD offline)"}; " +
                                            "Pending=${if (obdConnected) "" else dtcs.joinToString(",")}",
                                        uri = "",
                                        hash = com.elysium369.meet.core.reports.HashEngine.sha256Hex(dtcs.sorted().joinToString(",")),
                                        capturedAt = System.currentTimeMillis(),
                                        lat = null,
                                        lng = null,
                                    )
                                }

                                repo.createDraft(
                                    reportId = reportId,
                                    vehicleId = vehicleId,
                                    userId = userId,
                                    reportType = ReportType.PRE_SCAN_REPORT,
                                    title = "Pre-Scan ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())}",
                                    odometerKm = null,
                                    vin = null,
                                    plate = null,
                                    snapshot = snap,
                                    evidence = evidences,
                                    repairActions = emptyList(),
                                    notes = notesText,
                                )

                                val signed = repo.sign(
                                    reportId = reportId,
                                    signerName = signerName,
                                    signerRole = signerRole,
                                    signatureImageUri = "inline://signature-${reportId}",
                                    deviceId = deviceId,
                                )

                                val qr = QrPayload(
                                    reportId = signed.reportId,
                                    integrityHash = signed.integrityHash,
                                    vehicleId = signed.vehicleId,
                                    generatedAt = signed.generatedAt,
                                    reportType = ReportType.PRE_SCAN_REPORT,
                                    verifierUrl = null,
                                ).encode()

                                onSigned(signed.reportId, signed.integrityHash, qr)
                            } catch (e: Exception) {
                                onError("No se pudo firmar el reporte: ${e.message ?: e::class.simpleName}")
                            } finally {
                                onWorking(false)
                            }
                        }
                    },
                ) {
                    if (isWorking) {
                        CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Firmar y generar reporte")
                }
            }
        }
    }
}

// ── signed report panel ────────────────────────────────────────────────────

@Composable
private fun SignedReportPanel(
    reportId: String,
    integrityHash: String,
    qrPayload: String?,
    hashing: com.elysium369.meet.core.reports.ReportHashingService,
    repo: CertifiedReportRepository? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var exportSuccessMsg by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF00C853))
                Text("Reporte firmado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            Text("ID: $reportId", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            Text("Hash: $integrityHash", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)

            // The 4-line addition from commit `e1076723` lives here too:
            // the existing ReportIntegrityCard renders the parity demo
            // alongside the freshly signed report.
            ReportIntegrityCard(service = hashing)

            qrPayload?.let { qr ->
                Divider()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.QrCode2, contentDescription = null)
                    Text("QR de verificación forense (ZXing)", fontWeight = FontWeight.SemiBold)
                }

                val qrBitmap = remember(qr) {
                    try {
                        CertifiedReportPdfRenderer.renderQr(qr, 320)
                    } catch (e: Exception) {
                        null
                    }
                }

                if (qrBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Código QR de verificación forense",
                            modifier = Modifier
                                .size(180.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(8.dp)
                        )
                    }
                }

                Text(qr, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }

            if (repo != null) {
                Divider()
                Button(
                    onClick = {
                        scope.launch {
                            isExporting = true
                            exportError = null
                            exportSuccessMsg = null
                            try {
                                val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
                                val target = File(reportsDir, "reporte_${reportId.take(8)}.pdf")
                                val resultFile = withContext(Dispatchers.IO) {
                                    repo.exportPdf(reportId, target)
                                }
                                exportSuccessMsg = "PDF generado con éxito: ${resultFile.name}"
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    resultFile
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Reporte Certificado MEET - $reportId")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Compartir Reporte PDF"))
                            } catch (e: Exception) {
                                exportError = "Error al exportar PDF: ${e.localizedMessage ?: e.message}"
                            } finally {
                                isExporting = false
                            }
                        }
                    },
                    enabled = !isExporting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Generando PDF certificado...")
                    } else {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Exportar y Compartir PDF Certificado")
                    }
                }

                exportSuccessMsg?.let {
                    Text(it, color = Color(0xFF00897B), style = MaterialTheme.typography.bodySmall)
                }
                exportError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ── error banner ───────────────────────────────────────────────────────────

@Composable
private fun ErrorBanner(msg: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Error, contentDescription = null, tint = Color(0xFFC62828))
            Spacer(Modifier.width(8.dp))
            Text(msg, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ── placeholder for sub-flows not yet implemented ─────────────────────────

@Composable
private fun ComingSoonCard(type: ReportType) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                typeChipLabel(type),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Este flujo se entrega en una próxima release. " +
                    "La selección ya está wireada — solo falta la lógica de captura específica. " +
                    "Mientras tanto, generá un Pre-Scan y un Post-Scan para tener el par mínimo firmable.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// ── LazyRow helper shim ────────────────────────────────────────────────────

// Compose's foundation.lazy.items takes a List<T> + content lambda; we
// inline the import below to keep the screen file self-contained.

private fun <T> androidx.compose.foundation.lazy.LazyListScope.items(
    items: List<T>,
    itemContent: @Composable (T) -> Unit,
) {
    items(items.size) { idx -> itemContent(items[idx]) }
}

// ── Hilt entry point shim ──────────────────────────────────────────────────

@EntryPoint
@InstallIn(SingletonComponent::class)
interface InspectionSessionEntryPoint {
    fun certifiedReportRepository(): CertifiedReportRepository
}

private fun entryPoint(app: Application): InspectionSessionEntryPoint =
    EntryPointAccessors.fromApplication(app, InspectionSessionEntryPoint::class.java)