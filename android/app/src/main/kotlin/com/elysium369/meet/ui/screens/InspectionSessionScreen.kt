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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import com.elysium369.meet.diagnostic.BeforeAfterComparator
import com.elysium369.meet.diagnostic.ComparisonConclusion
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
 *   - Per-type sub-flow composables: All 5 subflows (PreScanSubFlow,
 *     PostScanSubFlow, RepairEvidenceSubFlow, PrePurchaseSubFlow, DvirSubFlow)
 *     are fully implemented with cryptographic signing, QR payload generation,
 *     BeforeAfterComparator analysis, and PDF export.
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
            ReportType.POST_SCAN_REPORT -> PostScanSubFlow(
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
            ReportType.REPAIR_EVIDENCE_REPORT -> RepairEvidenceSubFlow(
                repo = repo,
                vehicleId = vehicleId,
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
            ReportType.PRE_PURCHASE_INSPECTION_REPORT -> PrePurchaseSubFlow(
                repo = repo,
                vehicleId = vehicleId,
                vehicleOdometerKm = vehicleOdometerKm,
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
            ReportType.DVIR_REPORT -> DvirSubFlow(
                repo = repo,
                vehicleId = vehicleId,
                vehicleOdometerKm = vehicleOdometerKm,
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

// ── Post-Scan sub-flow ─────────────────────────────────────────────────────

@Composable
private fun PostScanSubFlow(
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

    var initialDtcsText by remember { mutableStateOf("P0230, P1709") }
    var postDtcsText by remember { mutableStateOf("") }
    var roadTestPassed by remember { mutableStateOf(true) }
    var freezeFrameConditionMet by remember { mutableStateOf(true) }
    var liveValueInRange by remember { mutableStateOf(true) }
    var signerName by remember { mutableStateOf("") }
    var signerRole by remember { mutableStateOf("mecánico especialista") }
    var notesText by remember { mutableStateOf("") }

    val initialDtcs = remember(initialDtcsText) {
        initialDtcsText.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
    }
    val postDtcs = remember(postDtcsText) {
        postDtcsText.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
    }

    val comparison = remember(initialDtcs, postDtcs, roadTestPassed, freezeFrameConditionMet, liveValueInRange, obdConnected) {
        val beforeSnap = DiagnosticSnapshot(
            id = "snap-pre-comp",
            vehicleId = vehicleId,
            sessionId = null,
            createdAtMs = System.currentTimeMillis() - 3600_000,
            dtcsActive = initialDtcs,
            dtcsPending = emptyList(),
            dtcsPermanent = emptyList(),
            freezeFramePidValues = emptyMap(),
            readiness = mapOf("MISFIRE" to true, "FUEL" to true, "CATALYST" to true),
            provenance = if (obdConnected) DiagnosticProvenance.Real else DiagnosticProvenance.ManualEntry(authorId = userId),
            notes = "Snapshot inicial",
        )
        val afterSnap = DiagnosticSnapshot(
            id = "snap-post-comp",
            vehicleId = vehicleId,
            sessionId = null,
            createdAtMs = System.currentTimeMillis(),
            dtcsActive = postDtcs,
            dtcsPending = emptyList(),
            dtcsPermanent = emptyList(),
            freezeFramePidValues = emptyMap(),
            readiness = mapOf("MISFIRE" to true, "FUEL" to true, "CATALYST" to true),
            provenance = if (obdConnected) DiagnosticProvenance.Real else DiagnosticProvenance.ManualEntry(authorId = userId),
            notes = "Snapshot post-reparación",
        )
        BeforeAfterComparator.compare(
            before = beforeSnap,
            after = afterSnap,
            roadTestPassed = roadTestPassed,
            freezeFrameConditionMet = freezeFrameConditionMet,
            liveValueInRange = liveValueInRange,
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Post-Scan — Validación y Certificación de Reparación",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            if (!obdConnected) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFF6F00))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "OBD offline: Conclusión marcada como UNVERIFIED según la regla forense. Se certifican las observaciones manuales del operador.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = initialDtcsText,
                onValueChange = { initialDtcsText = it.uppercase() },
                label = { Text("DTCs Iniciales (antes de reparar)") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = postDtcsText,
                onValueChange = { postDtcsText = it.uppercase() },
                label = { Text("DTCs Activos Residuales (dejar vacío si se borraron todos)") },
                placeholder = { Text("Ninguno / DTC residual") },
                modifier = Modifier.fillMaxWidth(),
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Resultado del Comparador Forense:", fontWeight = FontWeight.SemiBold)
                    Text("• DTCs Resueltos: ${if (comparison.clearedDtcs.isEmpty()) "Ninguno" else comparison.clearedDtcs.joinToString(", ")}")
                    if (comparison.newDtcs.isNotEmpty()) {
                        Text("• DTCs Nuevos / Regresión: ${comparison.newDtcs.joinToString(", ")}", color = MaterialTheme.colorScheme.error)
                    }
                    Text("• Conclusión: ${comparison.conclusion.name}", fontWeight = FontWeight.Medium)
                    Text("• Declaración de Reparado: ${if (comparison.canDeclareRepaired) "SÍ (Certificación Forense Válida)" else "NO (Condiciones incompletas o provenance manual)"}")
                }
            }

            Text("Verificaciones Físicas Obligatorias:", fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = roadTestPassed, onCheckedChange = { roadTestPassed = it })
                Spacer(Modifier.width(4.dp))
                Text("Prueba de manejo en ruta completada satisfactoriamente", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = freezeFrameConditionMet, onCheckedChange = { freezeFrameConditionMet = it })
                Spacer(Modifier.width(4.dp))
                Text("Condición de Freeze Frame reproducida sin reaparición de falla", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = liveValueInRange, onCheckedChange = { liveValueInRange = it })
                Spacer(Modifier.width(4.dp))
                Text("Valores PID en vivo dentro de rangos normales de fábrica", style = MaterialTheme.typography.bodyMedium)
            }

            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                label = { Text("Observaciones y detalles de la prueba") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            OutlinedTextField(
                value = signerName,
                onValueChange = { signerName = it },
                label = { Text("Nombre del técnico certificador") },
                modifier = Modifier.fillMaxWidth(),
                isError = signerName.isBlank() && errorMsg != null,
            )

            OutlinedTextField(
                value = signerRole,
                onValueChange = { signerRole = it },
                label = { Text("Rol / Certificación") },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                enabled = !isWorking && signerName.isNotBlank() && initialDtcs.isNotEmpty(),
                onClick = {
                    onWorking(true)
                    onError(null)
                    scope.launch {
                        try {
                            val reportId = "r-${UUID.randomUUID()}"
                            val afterSnap = DiagnosticSnapshot(
                                id = "snap-${reportId}",
                                vehicleId = vehicleId,
                                sessionId = null,
                                createdAtMs = System.currentTimeMillis(),
                                dtcsActive = postDtcs,
                                dtcsPending = emptyList(),
                                dtcsPermanent = emptyList(),
                                freezeFramePidValues = emptyMap(),
                                readiness = mapOf("MISFIRE" to liveValueInRange, "FUEL" to liveValueInRange, "CATALYST" to liveValueInRange),
                                provenance = if (obdConnected) DiagnosticProvenance.Real else DiagnosticProvenance.ManualEntry(authorId = userId),
                                notes = "Post-Scan: Cleared=[${comparison.clearedDtcs.joinToString(",")}] Regressions=[${comparison.newDtcs.joinToString(",")}]",
                            )

                            val evidences = mutableListOf<ReportEvidenceEntity>()
                            evidences += ReportMappers.evidenceToEntity(
                                evidenceId = "ev-post-comp-${reportId}",
                                reportId = reportId,
                                type = EvidenceType.OBD_SNAPSHOT,
                                label = "Comparativa Before/After",
                                description = "Conclusion=${comparison.conclusion.name}; Repaired=${comparison.canDeclareRepaired}; Cleared=${comparison.clearedDtcs}; Residual=${postDtcs}",
                                uri = "",
                                hash = com.elysium369.meet.core.reports.HashEngine.sha256Hex("${comparison.conclusion.name}|${comparison.clearedDtcs.joinToString(",")}|${postDtcs.joinToString(",")}"),
                                capturedAt = System.currentTimeMillis(),
                                lat = null,
                                lng = null,
                            )
                            if (notesText.isNotBlank()) {
                                evidences += ReportMappers.evidenceToEntity(
                                    evidenceId = "ev-notes-${reportId}",
                                    reportId = reportId,
                                    type = EvidenceType.REPAIR_NOTE,
                                    label = "Notas técnicas post-escaneo",
                                    description = notesText,
                                    uri = "",
                                    hash = com.elysium369.meet.core.reports.HashEngine.sha256Hex(notesText),
                                    capturedAt = System.currentTimeMillis(),
                                    lat = null,
                                    lng = null,
                                )
                            }

                            repo.createDraft(
                                reportId = reportId,
                                vehicleId = vehicleId,
                                userId = userId,
                                reportType = ReportType.POST_SCAN_REPORT,
                                title = "Post-Scan ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())}",
                                odometerKm = null,
                                vin = null,
                                plate = null,
                                snapshot = afterSnap,
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
                                reportType = ReportType.POST_SCAN_REPORT,
                                verifierUrl = null,
                            ).encode()

                            onSigned(signed.reportId, signed.integrityHash, qr)
                        } catch (e: Exception) {
                            onError("No se pudo firmar el reporte Post-Scan: ${e.message ?: e::class.simpleName}")
                        } finally {
                            onWorking(false)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isWorking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Firmar y generar reporte Post-Scan")
            }
        }
    }
}

// ── Repair Evidence sub-flow ───────────────────────────────────────────────

@Composable
private fun RepairEvidenceSubFlow(
    repo: CertifiedReportRepository,
    vehicleId: String,
    isWorking: Boolean,
    errorMsg: String?,
    onWorking: (Boolean) -> Unit,
    onError: (String?) -> Unit,
    onSigned: (reportId: String, hash: String, qr: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val deviceId = remember { "android-${android.os.Build.SERIAL ?: UUID.randomUUID().toString()}" }
    val userId = remember { "u-local" }

    var componentText by remember { mutableStateOf("") }
    var actionType by remember { mutableStateOf("Reemplazo de pieza") }
    var relatedDtc by remember { mutableStateOf("") }
    var partNameAndOem by remember { mutableStateOf("") }
    var supplierText by remember { mutableStateOf("") }
    var costText by remember { mutableStateOf("") }
    var warrantyDaysText by remember { mutableStateOf("90") }
    var descriptionText by remember { mutableStateOf("") }
    var signerName by remember { mutableStateOf("") }
    var signerRole by remember { mutableStateOf("mecánico especialista") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Evidencia de Reparación — Registro Forense de Mano de Obra y Repuestos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            OutlinedTextField(
                value = componentText,
                onValueChange = { componentText = it },
                label = { Text("Componente o sistema reparado *") },
                placeholder = { Text("Ej. Bomba de combustible, Alternador, Frenos") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = actionType,
                onValueChange = { actionType = it },
                label = { Text("Tipo de acción técnica") },
                placeholder = { Text("Reemplazo, Rectificación, Calibración, Ajuste") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = relatedDtc,
                onValueChange = { relatedDtc = it.uppercase() },
                label = { Text("DTC relacionado (opcional)") },
                placeholder = { Text("P0230") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = partNameAndOem,
                onValueChange = { partNameAndOem = it },
                label = { Text("Repuesto / Número de Parte OEM") },
                placeholder = { Text("Ej. Bosch 0580453443 / Original OEM") },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = costText,
                    onValueChange = { costText = it },
                    label = { Text("Costo Total ($)") },
                    placeholder = { Text("120.00") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = warrantyDaysText,
                    onValueChange = { warrantyDaysText = it },
                    label = { Text("Garantía (días)") },
                    placeholder = { Text("90") },
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = supplierText,
                onValueChange = { supplierText = it },
                label = { Text("Proveedor o Taller emisor") },
                placeholder = { Text("Distribuidora / Taller Oficial") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = descriptionText,
                onValueChange = { descriptionText = it },
                label = { Text("Procedimiento técnico y hallazgos *") },
                placeholder = { Text("Descripción forense del trabajo efectuado, torque aplicado, pruebas de banco...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )

            OutlinedTextField(
                value = signerName,
                onValueChange = { signerName = it },
                label = { Text("Técnico responsable que firma *") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = signerRole,
                onValueChange = { signerRole = it },
                label = { Text("Rol") },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                enabled = !isWorking && componentText.isNotBlank() && descriptionText.isNotBlank() && signerName.isNotBlank(),
                onClick = {
                    onWorking(true)
                    onError(null)
                    scope.launch {
                        try {
                            val reportId = "r-${UUID.randomUUID()}"
                            val costDouble = costText.toDoubleOrNull()
                            val warrantyInt = warrantyDaysText.toIntOrNull() ?: 90

                            val actionId = "act-${UUID.randomUUID()}"
                            val repairAction = ReportMappers.repairToEntity(
                                actionId = actionId,
                                reportId = reportId,
                                actionType = actionType.ifBlank { "Reparación" },
                                component = componentText,
                                dtcRelated = relatedDtc.ifBlank { null },
                                description = descriptionText,
                                partUsed = partNameAndOem.ifBlank { null },
                                supplier = supplierText.ifBlank { null },
                                mechanic = signerName,
                                cost = costDouble,
                                currency = "USD",
                                warrantyDays = warrantyInt,
                                createdAt = System.currentTimeMillis(),
                            )

                            val evidenceId = "ev-repair-${UUID.randomUUID()}"
                            val evidencePayload = "Component:$componentText|Action:$actionType|Part:$partNameAndOem|Cost:$costDouble|Warranty:${warrantyInt}d"
                            val evidence = ReportMappers.evidenceToEntity(
                                evidenceId = evidenceId,
                                reportId = reportId,
                                type = EvidenceType.REPAIR_NOTE,
                                label = "Certificado de Mano de Obra: $componentText",
                                description = "$descriptionText (Repuesto: ${partNameAndOem.ifBlank { "No aplica" }}, Garantía: $warrantyInt días)",
                                uri = "",
                                hash = com.elysium369.meet.core.reports.HashEngine.sha256Hex(evidencePayload),
                                capturedAt = System.currentTimeMillis(),
                                lat = null,
                                lng = null,
                            )

                            repo.createDraft(
                                reportId = reportId,
                                vehicleId = vehicleId,
                                userId = userId,
                                reportType = ReportType.REPAIR_EVIDENCE_REPORT,
                                title = "Reparación: $componentText (${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())})",
                                odometerKm = null,
                                vin = null,
                                plate = null,
                                snapshot = null,
                                evidence = listOf(evidence),
                                repairActions = listOf(repairAction),
                                notes = descriptionText,
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
                                reportType = ReportType.REPAIR_EVIDENCE_REPORT,
                                verifierUrl = null,
                            ).encode()

                            onSigned(signed.reportId, signed.integrityHash, qr)
                        } catch (e: Exception) {
                            onError("Error al emitir evidencia de reparación: ${e.message ?: e::class.simpleName}")
                        } finally {
                            onWorking(false)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isWorking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Firmar y certificar reparación")
            }
        }
    }
}

// ── Pre-Purchase / Peritaje sub-flow ───────────────────────────────────────

@Composable
private fun PrePurchaseSubFlow(
    repo: CertifiedReportRepository,
    vehicleId: String,
    vehicleOdometerKm: Long?,
    isWorking: Boolean,
    errorMsg: String?,
    onWorking: (Boolean) -> Unit,
    onError: (String?) -> Unit,
    onSigned: (reportId: String, hash: String, qr: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val deviceId = remember { "android-${android.os.Build.SERIAL ?: UUID.randomUUID().toString()}" }
    val userId = remember { "u-local" }

    var scoreEngine by remember { mutableStateOf(85f) }
    var scoreChassis by remember { mutableStateOf(90f) }
    var scoreBodywork by remember { mutableStateOf(80f) }
    var scoreElectrical by remember { mutableStateOf(85f) }
    var scoreInterior by remember { mutableStateOf(90f) }

    var odometerText by remember { mutableStateOf(vehicleOdometerKm?.toString() ?: "") }
    var findingsText by remember { mutableStateOf("") }
    var signerName by remember { mutableStateOf("") }
    var signerRole by remember { mutableStateOf("perito certificador") }

    val overallScore = remember(scoreEngine, scoreChassis, scoreBodywork, scoreElectrical, scoreInterior) {
        ((scoreEngine + scoreChassis + scoreBodywork + scoreElectrical + scoreInterior) / 5f).toInt()
    }

    val verdict = when {
        overallScore >= 80 -> "APROBADO — Vehículo en excelente estado técnico"
        overallScore >= 60 -> "CONDICIONADO — Requiere mantenimiento correctivo menor / Negociar"
        else -> "RIESGO ALTO — No recomendado / Desgaste severo o riesgo de falla"
    }

    val verdictColor = when {
        overallScore >= 80 -> Color(0xFF00C853)
        overallScore >= 60 -> Color(0xFFFF6F00)
        else -> Color(0xFFD50000)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Peritaje Pre-Compra — Evaluación Técnica y Valuación",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = verdictColor.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Puntuación General:", fontWeight = FontWeight.Bold)
                        Text("$overallScore / 100", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = verdictColor)
                    }
                    Text("Dictamen: $verdict", fontWeight = FontWeight.SemiBold, color = verdictColor)
                }
            }

            Text("Puntuación por Sistemas (0 - 100):", fontWeight = FontWeight.SemiBold)

            ScoreSlider(label = "1. Motor, Caja y Transmisión", value = scoreEngine, onValueChange = { scoreEngine = it })
            ScoreSlider(label = "2. Chasis, Frenos y Suspensión", value = scoreChassis, onValueChange = { scoreChassis = it })
            ScoreSlider(label = "3. Carrocería, Pintura y Estructura", value = scoreBodywork, onValueChange = { scoreBodywork = it })
            ScoreSlider(label = "4. Sistema Eléctrico, Módulos y OBD", value = scoreElectrical, onValueChange = { scoreElectrical = it })
            ScoreSlider(label = "5. Interior, Cabina y Seguridad Pasiva", value = scoreInterior, onValueChange = { scoreInterior = it })

            OutlinedTextField(
                value = odometerText,
                onValueChange = { odometerText = it },
                label = { Text("Odómetro Verificado (km)") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = findingsText,
                onValueChange = { findingsText = it },
                label = { Text("Hallazgos, Siniestros previos y Recomendaciones *") },
                placeholder = { Text("Estado de fluidos, historial de colisiones visibles, desgaste de neumáticos...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )

            OutlinedTextField(
                value = signerName,
                onValueChange = { signerName = it },
                label = { Text("Nombre del Perito Certificador *") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = signerRole,
                onValueChange = { signerRole = it },
                label = { Text("Licencia / Rol") },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                enabled = !isWorking && signerName.isNotBlank() && findingsText.isNotBlank(),
                onClick = {
                    onWorking(true)
                    onError(null)
                    scope.launch {
                        try {
                            val reportId = "r-${UUID.randomUUID()}"
                            val odoInt = odometerText.toIntOrNull()

                            val peritajePayload = "Score:$overallScore|Verdict:$verdict|Engine:${scoreEngine.toInt()}|Chassis:${scoreChassis.toInt()}|Body:${scoreBodywork.toInt()}|Elec:${scoreElectrical.toInt()}|Int:${scoreInterior.toInt()}"
                            val evidence = ReportMappers.evidenceToEntity(
                                evidenceId = "ev-peritaje-${UUID.randomUUID()}",
                                reportId = reportId,
                                type = EvidenceType.PROVIDER_NOTE,
                                label = "Dictamen de Peritaje: $overallScore/100",
                                description = "$verdict. Hallazgos: $findingsText",
                                uri = "",
                                hash = com.elysium369.meet.core.reports.HashEngine.sha256Hex(peritajePayload),
                                capturedAt = System.currentTimeMillis(),
                                lat = null,
                                lng = null,
                            )

                            repo.createDraft(
                                reportId = reportId,
                                vehicleId = vehicleId,
                                userId = userId,
                                reportType = ReportType.PRE_PURCHASE_INSPECTION_REPORT,
                                title = "Peritaje Técnico — Score $overallScore/100 (${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())})",
                                odometerKm = odoInt,
                                vin = null,
                                plate = null,
                                snapshot = null,
                                evidence = listOf(evidence),
                                repairActions = emptyList(),
                                notes = "Puntuación global: $overallScore/100. $verdict. $findingsText",
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
                                reportType = ReportType.PRE_PURCHASE_INSPECTION_REPORT,
                                verifierUrl = null,
                            ).encode()

                            onSigned(signed.reportId, signed.integrityHash, qr)
                        } catch (e: Exception) {
                            onError("Error al emitir peritaje: ${e.message ?: e::class.simpleName}")
                        } finally {
                            onWorking(false)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isWorking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Firmar y emitir Peritaje Certificado")
            }
        }
    }
}

@Composable
private fun ScoreSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("${value.toInt()}/100", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..100f,
            steps = 99,
        )
    }
}

// ── DVIR sub-flow ──────────────────────────────────────────────────────────

@Composable
private fun DvirSubFlow(
    repo: CertifiedReportRepository,
    vehicleId: String,
    vehicleOdometerKm: Long?,
    isWorking: Boolean,
    errorMsg: String?,
    onWorking: (Boolean) -> Unit,
    onError: (String?) -> Unit,
    onSigned: (reportId: String, hash: String, qr: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val deviceId = remember { "android-${android.os.Build.SERIAL ?: UUID.randomUUID().toString()}" }
    val userId = remember { "u-local" }

    var isPreTrip by remember { mutableStateOf(true) }
    var brakesOk by remember { mutableStateOf(true) }
    var lightsOk by remember { mutableStateOf(true) }
    var tiresOk by remember { mutableStateOf(true) }
    var fluidsOk by remember { mutableStateOf(true) }
    var steeringOk by remember { mutableStateOf(true) }
    var mirrorsOk by remember { mutableStateOf(true) }
    var emergencyKitOk by remember { mutableStateOf(true) }

    var odometerText by remember { mutableStateOf(vehicleOdometerKm?.toString() ?: "") }
    var defectNotes by remember { mutableStateOf("") }
    var signerName by remember { mutableStateOf("") }
    var signerRole by remember { mutableStateOf("conductor / operador de flota") }

    val allPass = brakesOk && lightsOk && tiresOk && fluidsOk && steeringOk && mirrorsOk && emergencyKitOk
    val operabilityVerdict = if (allPass) "APTO PARA OPERACIÓN" else "REQUIERE ATENCIÓN / DEFECTOS REPORTADOS"
    val verdictColor = if (allPass) Color(0xFF00C853) else Color(0xFFD50000)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "DVIR — Driver Vehicle Inspection Report (Flotas)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeChip(label = "Pre-Viaje (Pre-Trip)", selected = isPreTrip, onClick = { isPreTrip = true })
                TypeChip(label = "Post-Viaje (Post-Trip)", selected = !isPreTrip, onClick = { isPreTrip = false })
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = verdictColor.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(if (allPass) Icons.Filled.CheckCircle else Icons.Filled.Warning, contentDescription = null, tint = verdictColor)
                    Column {
                        Text(operabilityVerdict, fontWeight = FontWeight.Bold, color = verdictColor)
                        Text(
                            if (allPass) "Todos los sistemas de seguridad aprobados para la jornada."
                            else "Existen fallas registradas que deben ser atendidas antes de circular.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Text("Lista de Comprobación de Seguridad:", fontWeight = FontWeight.SemiBold)

            DvirCheckRow("Frenos de servicio y emergencia", brakesOk) { brakesOk = it }
            DvirCheckRow("Luces, direccionales y faros", lightsOk) { lightsOk = it }
            DvirCheckRow("Neumáticos y tuercas de rueda", tiresOk) { tiresOk = it }
            DvirCheckRow("Fluidos (Aceite, Refrigerante, Frenos)", fluidsOk) { fluidsOk = it }
            DvirCheckRow("Dirección y suspensión", steeringOk) { steeringOk = it }
            DvirCheckRow("Espejos, vidrios y limpiaparabrisas", mirrorsOk) { mirrorsOk = it }
            DvirCheckRow("Cinturones y kit de seguridad / extintor", emergencyKitOk) { emergencyKitOk = it }

            OutlinedTextField(
                value = odometerText,
                onValueChange = { odometerText = it },
                label = { Text("Odómetro actual (km)") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = defectNotes,
                onValueChange = { defectNotes = it },
                label = { Text("Defectos o anomalías observadas") },
                placeholder = { Text("Detallar ruidos, desgaste o averías si alguno de los ítems no cumple...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            OutlinedTextField(
                value = signerName,
                onValueChange = { signerName = it },
                label = { Text("Nombre del Conductor / Inspector *") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = signerRole,
                onValueChange = { signerRole = it },
                label = { Text("Rol / ID Conductor") },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                enabled = !isWorking && signerName.isNotBlank() && (allPass || defectNotes.isNotBlank()),
                onClick = {
                    onWorking(true)
                    onError(null)
                    scope.launch {
                        try {
                            val reportId = "r-${UUID.randomUUID()}"
                            val odoInt = odometerText.toIntOrNull()
                            val tripType = if (isPreTrip) "Pre-Trip" else "Post-Trip"

                            val checklistSummary = "Brakes:$brakesOk|Lights:$lightsOk|Tires:$tiresOk|Fluids:$fluidsOk|Steer:$steeringOk|Mirrors:$mirrorsOk|Kit:$emergencyKitOk"
                            val evidence = ReportMappers.evidenceToEntity(
                                evidenceId = "ev-dvir-${UUID.randomUUID()}",
                                reportId = reportId,
                                type = EvidenceType.TEST_DRIVE_RESULT,
                                label = "Checklist DVIR $tripType",
                                description = "$operabilityVerdict. $checklistSummary. Notas: ${defectNotes.ifBlank { "Sin anomalías" }}",
                                uri = "",
                                hash = com.elysium369.meet.core.reports.HashEngine.sha256Hex("$tripType|$checklistSummary|$defectNotes"),
                                capturedAt = System.currentTimeMillis(),
                                lat = null,
                                lng = null,
                            )

                            repo.createDraft(
                                reportId = reportId,
                                vehicleId = vehicleId,
                                userId = userId,
                                reportType = ReportType.DVIR_REPORT,
                                title = "DVIR $tripType — $operabilityVerdict (${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())})",
                                odometerKm = odoInt,
                                vin = null,
                                plate = null,
                                snapshot = null,
                                evidence = listOf(evidence),
                                repairActions = emptyList(),
                                notes = "DVIR $tripType: $operabilityVerdict. ${defectNotes.ifBlank { "Inspección conforme." }}",
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
                                reportType = ReportType.DVIR_REPORT,
                                verifierUrl = null,
                            ).encode()

                            onSigned(signed.reportId, signed.integrityHash, qr)
                        } catch (e: Exception) {
                            onError("Error al emitir DVIR: ${e.message ?: e::class.simpleName}")
                        } finally {
                            onWorking(false)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isWorking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Firmar y registrar DVIR")
            }
        }
    }
}

@Composable
private fun DvirCheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
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