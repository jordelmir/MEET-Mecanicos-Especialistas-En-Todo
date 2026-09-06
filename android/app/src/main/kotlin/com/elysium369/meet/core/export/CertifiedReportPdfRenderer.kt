package com.elysium369.meet.core.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import com.elysium369.meet.core.reports.HashEngine
import com.elysium369.meet.core.reports.ReportType
import com.elysium369.meet.core.reports.QrPayload
import com.elysium369.meet.core.reports.ReportPrivacyPolicy
import com.elysium369.meet.data.local.entities.CertifiedReportEntity
import com.elysium369.meet.data.local.entities.DiagnosticSnapshotEntity
import com.elysium369.meet.data.local.entities.RepairActionEntity
import com.elysium369.meet.data.local.entities.ReportEvidenceEntity
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * V2 Certified Report PDF renderer.
 *
 * Produces the 6-page layout specified in
 *   docs/reports/V2-CERTIFIED-PDF-AND-HISTORY.md § Phase 6.
 *
 *   p1 — Cover (logo, type, vehicle, VIN/plate/odo, datetime, score, QR)
 *   p2 — Executive summary (DTCs, severity, recommendation)
 *   p3 — Per-DTC detail (description, causes, tests, components)
 *   p4 — Telemetry (PID tables, freeze frame, sensor graphs)
 *   p5 — Repair actions (parts, costs, warranty, mechanic)
 *   p6 — Evidence (photos, signatures, disclaimer, hash footer)
 *
 * Each page footer carries
 *   "Verified by Elysium Vanguard · <integrity_hash>"
 * in 8pt mono. The QR on page 1 carries the [QrPayload] from Phase 3.
 *
 * Honest-phrases rule: if the report has no snapshot, page 4 must show
 * "Snapshot OBD no disponible. Reporte basado en datos manuales/offline."
 * — never invent PID values.
 */
class CertifiedReportPdfRenderer(private val context: Context) {

    data class PageContent(
        val report: CertifiedReportEntity,
        val evidence: List<ReportEvidenceEntity>,
        val repairs: List<RepairActionEntity>,
        val snapshots: List<DiagnosticSnapshotEntity>,
        val vehicleLabel: String,
        val vehicleOdometerKm: Long?,
        val vehicleScore: Int? = null,
        val peritajeVerdict: String? = null,
        val privacyPolicy: ReportPrivacyPolicy = ReportPrivacyPolicy.OWNER_COPY,
    )

    fun render(content: PageContent, outputFile: File): File {
        val doc = PdfDocument()
        try {
            val qrPayload = QrPayload(
                reportId = content.report.reportId,
                integrityHash = content.report.integrityHash,
                vehicleId = content.report.vehicleId,
                generatedAt = content.report.generatedAt,
                reportType = content.report.reportType,
                verifierUrl = null,
            )

            val accent = Color.parseColor("#1976D2")
            val dark = Color.parseColor("#0A0A0A")
            val subtle = Color.parseColor("#666666")
            val danger = Color.parseColor("#C62828")
            val ok = Color.parseColor("#2E7D32")
            val warn = Color.parseColor("#F57C00")

            val pageSize = PdfDocument.PageInfo.Builder(595, 842, 1).create()

            // ── p1 cover ──────────────────────────────────────────────
            doc.startPage(pageSize).also { page ->
                drawCover(page.canvas, content, qrPayload.encode(), accent, dark, subtle)
                drawFooter(page.canvas, pageSize.pageWidth, pageSize.pageHeight, content.report.integrityHash, subtle)
                doc.finishPage(page)
            }

            // ── p2 executive summary ─────────────────────────────────
            doc.startPage(pageSize).also { page ->
                drawHeader(page.canvas, pageSize.pageWidth, "Resumen ejecutivo", accent, dark)
                drawExecutiveSummary(page.canvas, pageSize.pageWidth, content, danger, ok, warn, subtle)
                drawFooter(page.canvas, pageSize.pageWidth, pageSize.pageHeight, content.report.integrityHash, subtle)
                doc.finishPage(page)
            }

            // ── p3 per-DTC detail ────────────────────────────────────
            doc.startPage(pageSize).also { page ->
                drawHeader(page.canvas, pageSize.pageWidth, "Detalle por DTC", accent, dark)
                drawPerDtcDetail(page.canvas, pageSize.pageWidth, content, subtle)
                drawFooter(page.canvas, pageSize.pageWidth, pageSize.pageHeight, content.report.integrityHash, subtle)
                doc.finishPage(page)
            }

            // ── p4 telemetry ─────────────────────────────────────────
            doc.startPage(pageSize).also { page ->
                drawHeader(page.canvas, pageSize.pageWidth, "Telemetría / Snapshot", accent, dark)
                drawTelemetry(page.canvas, pageSize.pageWidth, content, subtle, warn)
                drawFooter(page.canvas, pageSize.pageWidth, pageSize.pageHeight, content.report.integrityHash, subtle)
                doc.finishPage(page)
            }

            // ── p5 repair actions ────────────────────────────────────
            doc.startPage(pageSize).also { page ->
                drawHeader(page.canvas, pageSize.pageWidth, "Reparación / Acciones", accent, dark)
                drawRepairActions(page.canvas, pageSize.pageWidth, content, subtle)
                drawFooter(page.canvas, pageSize.pageWidth, pageSize.pageHeight, content.report.integrityHash, subtle)
                doc.finishPage(page)
            }

            // ── p6 evidence + signature + disclaimer ─────────────────
            doc.startPage(pageSize).also { page ->
                drawHeader(page.canvas, pageSize.pageWidth, "Evidencia y firma", accent, dark)
                drawEvidenceAndSignature(page.canvas, pageSize.pageWidth, content, subtle, danger)
                drawFooter(page.canvas, pageSize.pageWidth, pageSize.pageHeight, content.report.integrityHash, subtle)
                doc.finishPage(page)
            }

            FileOutputStream(outputFile).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return outputFile
    }

    // ── page helpers ───────────────────────────────────────────────────

    private fun drawHeader(canvas: Canvas, pageWidth: Int, title: String, accent: Int, dark: Int) {
        val headerPaint = Paint().apply { color = dark; textSize = 18f; isAntiAlias = true; isFakeBoldText = true }
        val underlinePaint = Paint().apply { color = accent; strokeWidth = 2f }
        canvas.drawText("Elysium Vanguard · $title", 32f, 48f, headerPaint)
        canvas.drawLine(32f, 60f, (pageWidth - 32).toFloat(), 60f, underlinePaint)
    }

    private fun drawFooter(canvas: Canvas, pageWidth: Int, pageHeight: Int, hash: String, subtle: Int) {
        val footerPaint = Paint().apply { color = subtle; textSize = 8f; isAntiAlias = true; typeface = android.graphics.Typeface.MONOSPACE }
        canvas.drawText(
            "Verified by Elysium Vanguard · $hash",
            32f, (pageHeight - 18).toFloat(), footerPaint
        )
    }

    private fun drawCover(canvas: Canvas, content: PageContent, qrPayload: String, accent: Int, dark: Int, subtle: Int) {
        val big = Paint().apply { color = dark; textSize = 28f; isAntiAlias = true; isFakeBoldText = true }
        val label = Paint().apply { color = subtle; textSize = 11f; isAntiAlias = true }
        val value = Paint().apply { color = dark; textSize = 14f; isAntiAlias = true }
        canvas.drawText("Elysium Vanguard", 32f, 70f, big)
        canvas.drawText("Reporte Certificado V2", 32f, 95f, Paint().apply { color = accent; textSize = 14f; isAntiAlias = true; isFakeBoldText = true })

        val typeLabel = when (content.report.reportType) {
            ReportType.PRE_SCAN_REPORT -> "PRE-SCAN"
            ReportType.POST_SCAN_REPORT -> "POST-SCAN"
            ReportType.REPAIR_EVIDENCE_REPORT -> "EVIDENCIA DE REPARACIÓN"
            ReportType.PRE_PURCHASE_INSPECTION_REPORT -> "PERITAJE DE COMPRA"
            ReportType.DVIR_REPORT -> "DVIR (FLOTILLA)"
        }
        canvas.drawText(typeLabel, 32f, 130f, Paint().apply { color = dark; textSize = 22f; isAntiAlias = true; isFakeBoldText = true })

        var y = 175f
        canvas.drawText("Vehículo:", 32f, y, label); canvas.drawText(content.vehicleLabel, 130f, y, value); y += 22f
        content.privacyPolicy.displayVin(content.report.vin)?.let {
            canvas.drawText("VIN:", 32f, y, label); canvas.drawText(it, 130f, y, value); y += 22f
        }
        content.privacyPolicy.displayPlate(content.report.plate)?.let {
            canvas.drawText("Placa:", 32f, y, label); canvas.drawText(it, 130f, y, value); y += 22f
        }
        content.vehicleOdometerKm?.let {
            canvas.drawText("Odómetro:", 32f, y, label)
            canvas.drawText("%,d km".format(it), 130f, y, value); y += 22f
        }
        canvas.drawText("Fecha:", 32f, y, label)
        canvas.drawText(formatTimestamp(content.report.generatedAt), 130f, y, value); y += 22f
        canvas.drawText("ID reporte:", 32f, y, label)
        canvas.drawText(content.report.reportId, 130f, y, Paint().apply { color = dark; textSize = 11f; isAntiAlias = true; typeface = android.graphics.Typeface.MONOSPACE }); y += 22f
        canvas.drawText("Hash:", 32f, y, label)
        canvas.drawText(content.report.integrityHash.take(32) + "…", 130f, y, Paint().apply { color = dark; textSize = 11f; isAntiAlias = true; typeface = android.graphics.Typeface.MONOSPACE }); y += 22f
        content.vehicleScore?.let {
            canvas.drawText("Score:", 32f, y, label)
            canvas.drawText("$it / 100", 130f, y, value); y += 22f
        }

        // QR
        try {
            val qrBmp = renderQr(qrPayload, 180)
            canvas.drawBitmap(qrBmp, (canvas.width - 200).toFloat(), 110f, null)
            canvas.drawText("QR de verificación", (canvas.width - 200).toFloat(), 305f, Paint().apply { color = subtle; textSize = 9f; isAntiAlias = true })
        } catch (e: Exception) {
            // ZXing not available — render the payload text instead so
            // the verifier can still read it from the PDF.
            canvas.drawText("QR (texto):", (canvas.width - 200).toFloat(), 120f, label)
            val qrText = Paint().apply { color = dark; textSize = 7f; isAntiAlias = true; typeface = android.graphics.Typeface.MONOSPACE }
            qrPayload.chunked(28).forEachIndexed { i, line ->
                canvas.drawText(line, (canvas.width - 200).toFloat(), 140f + (i * 9), qrText)
            }
        }
    }

    private fun drawExecutiveSummary(
        canvas: Canvas, pageWidth: Int, content: PageContent,
        danger: Int, ok: Int, warn: Int, subtle: Int,
    ) {
        val title = Paint().apply { color = Color.BLACK; textSize = 13f; isAntiAlias = true; isFakeBoldText = true }
        val body = Paint().apply { color = Color.BLACK; textSize = 11f; isAntiAlias = true }
        val dtcs = content.evidence.flatMap { ev ->
            when (ev.evidenceType) {
                com.elysium369.meet.core.reports.EvidenceType.OBD_SNAPSHOT ->
                    ev.description.split(";").firstOrNull()?.substringAfter("=")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                else -> emptyList()
            }
        }.distinct().sorted()

        var y = 90f
        canvas.drawText("DTCs detectados: ${dtcs.size}", 32f, y, title); y += 22f
        if (dtcs.isEmpty()) {
            canvas.drawText("Sin códigos de diagnóstico reportados.", 32f, y, body); y += 18f
        } else {
            dtcs.take(20).forEach { dtc ->
                canvas.drawText("· $dtc", 48f, y, body); y += 16f
            }
        }
        y += 8f
        content.peritajeVerdict?.let { v ->
            canvas.drawText("Veredicto peritaje: $v", 32f, y, title); y += 22f
        }
        canvas.drawText("Severidad global:", 32f, y, title); y += 18f
        val severity = when {
            dtcs.isEmpty() -> "Sin DTCs activos — Confianza: OK si OBD real, limitada si offline."
            dtcs.any { it.startsWith("P0") } -> "ALTA — códigos powertrain presentes."
            dtcs.any { it.startsWith("P1") } -> "MEDIA — códigos manufacturer-specific."
            else -> "BAJA — códigos de chasis/body."
        }
        canvas.drawText(severity, 32f, y, body); y += 22f

        canvas.drawText("Recomendación técnica:", 32f, y, title); y += 18f
        val reco = if (dtcs.isEmpty()) {
            "No se detectaron DTCs. Si el scanner estaba desconectado, " +
                "este reporte no descarta fallas. Se recomienda captura OBD real antes de cerrar el diagnóstico."
        } else {
            "Validar cada DTC con prueba física (multímetro, osciloscopio, prueba de componentes) " +
                "antes de reemplazar piezas. No reemplazar sin confirmar causa raíz."
        }
        reco.chunked(70).forEach { line -> canvas.drawText(line, 32f, y, body); y += 16f }
    }

    private fun drawPerDtcDetail(canvas: Canvas, pageWidth: Int, content: PageContent, subtle: Int) {
        val title = Paint().apply { color = Color.BLACK; textSize = 13f; isAntiAlias = true; isFakeBoldText = true }
        val body = Paint().apply { color = Color.BLACK; textSize = 11f; isAntiAlias = true }
        val small = Paint().apply { color = subtle; textSize = 9f; isAntiAlias = true }

        val dtcs = content.evidence.flatMap { ev ->
            when (ev.evidenceType) {
                com.elysium369.meet.core.reports.EvidenceType.OBD_SNAPSHOT ->
                    ev.description.split(";").firstOrNull()?.substringAfter("=")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                else -> emptyList()
            }
        }.distinct().sorted()

        var y = 90f
        if (dtcs.isEmpty()) {
            canvas.drawText("Sin DTCs en este reporte.", 32f, y, body); y += 18f
            canvas.drawText("Si esperabas ver códigos, verificá que el snapshot OBD haya sido capturado.", 32f, y, small)
            return
        }
        dtcs.take(8).forEach { dtc ->
            canvas.drawText("· $dtc", 32f, y, title); y += 18f
            canvas.drawText("  Descripción: ver base DTC para texto canónico.", 32f, y, small); y += 14f
            canvas.drawText("  Causas probables: cableado, sensor, ECM, conexión a masa.", 32f, y, small); y += 14f
            canvas.drawText("  Pruebas recomendadas: multímetro en conector, osciloscopio en señal, prueba de componentes.", 32f, y, small); y += 14f
            canvas.drawText("  Componentes relacionados: revisar diagrama eléctrico del fabricante.", 32f, y, small); y += 14f
            canvas.drawText("  ADVERTENCIA: no reemplazar sin confirmar causa raíz.", 32f, y, Paint().apply { color = Color.parseColor("#C62828"); textSize = 9f; isAntiAlias = true; isFakeBoldText = true }); y += 22f
        }
        if (dtcs.size > 8) {
            canvas.drawText("… y ${dtcs.size - 8} códigos más (ver suplemento digital).", 32f, y, small)
        }
    }

    private fun drawTelemetry(canvas: Canvas, pageWidth: Int, content: PageContent, subtle: Int, warn: Int) {
        val body = Paint().apply { color = Color.BLACK; textSize = 11f; isAntiAlias = true }
        val mono = Paint().apply { color = Color.BLACK; textSize = 10f; isAntiAlias = true; typeface = android.graphics.Typeface.MONOSPACE }
        var y = 90f

        if (content.snapshots.isEmpty()) {
            canvas.drawText("Snapshot OBD no disponible.", 32f, y, Paint().apply { color = warn; textSize = 14f; isAntiAlias = true; isFakeBoldText = true }); y += 22f
            canvas.drawText("Reporte basado en datos manuales/offline.", 32f, y, body); y += 18f
            canvas.drawText("Para obtener telemetría en vivo, conectá el adaptador OBD y volvé a capturar el snapshot.", 32f, y, body)
            return
        }

        val snap = content.snapshots.first()
        canvas.drawText("Snapshot ID: ${snap.snapshotId}", 32f, y, mono); y += 18f
        canvas.drawText("Provenance: ${snap.provenanceLabel}", 32f, y, body); y += 18f
        canvas.drawText("Creado: ${formatTimestamp(snap.createdAtMs)}", 32f, y, body); y += 22f
        canvas.drawText("Hash del snapshot: ${snap.hashSha256.take(32)}…", 32f, y, mono); y += 22f

        canvas.drawText("PIDs principales:", 32f, y, Paint().apply { color = Color.BLACK; textSize = 12f; isAntiAlias = true; isFakeBoldText = true }); y += 18f
        val pidRows = listOf(
            "RPM" to snap.rpm?.toString(),
            "Velocidad (kph)" to snap.speedKph?.toString(),
            "ECT (°C)" to snap.coolantTempC?.toString(),
            "Voltaje ECU" to snap.ecuVoltage?.toString(),
            "Carga motor (%)" to snap.engineLoadPct?.toString(),
            "Fuel Trim STFT" to snap.fuelTrimStft?.toString(),
            "Fuel Trim LTFT" to snap.fuelTrimLtft?.toString(),
        )
        pidRows.forEach { (k, v) ->
            canvas.drawText("  $k: ${v ?: "n/d"}", 32f, y, mono); y += 14f
        }
    }

    private fun drawRepairActions(canvas: Canvas, pageWidth: Int, content: PageContent, subtle: Int) {
        val body = Paint().apply { color = Color.BLACK; textSize = 11f; isAntiAlias = true }
        val mono = Paint().apply { color = Color.BLACK; textSize = 10f; isAntiAlias = true; typeface = android.graphics.Typeface.MONOSPACE }
        var y = 90f

        if (content.repairs.isEmpty()) {
            canvas.drawText("Sin acciones de reparación registradas en este reporte.", 32f, y, body); y += 18f
            canvas.drawText("Este reporte no documenta piezas reemplazadas.", 32f, y, body); return
        }

        content.repairs.forEach { r ->
            canvas.drawText("· ${r.actionType} — ${r.component}", 32f, y, Paint().apply { color = Color.BLACK; textSize = 12f; isAntiAlias = true; isFakeBoldText = true }); y += 18f
            r.dtcRelated?.let { canvas.drawText("  DTC: $it", 32f, y, mono); y += 14f }
            canvas.drawText("  ${r.description}", 32f, y, body); y += 14f
            r.partUsed?.let { canvas.drawText("  Pieza: $it", 32f, y, mono); y += 14f }
            r.supplier?.let { canvas.drawText("  Proveedor: $it", 32f, y, mono); y += 14f }
            r.mechanic?.let { canvas.drawText("  Mecánico: $it", 32f, y, mono); y += 14f }
            r.cost?.let { canvas.drawText("  Costo: ${"%.2f".format(it)} ${r.currency}", 32f, y, mono); y += 14f }
            r.warrantyDays?.let { canvas.drawText("  Garantía: $it días", 32f, y, mono); y += 14f }
            y += 8f
        }
    }

    private fun drawEvidenceAndSignature(canvas: Canvas, pageWidth: Int, content: PageContent, subtle: Int, danger: Int) {
        val body = Paint().apply { color = Color.BLACK; textSize = 11f; isAntiAlias = true }
        val mono = Paint().apply { color = Color.BLACK; textSize = 10f; isAntiAlias = true; typeface = android.graphics.Typeface.MONOSPACE }
        var y = 90f

        canvas.drawText("Evidencias (${content.evidence.size})", 32f, y, Paint().apply { color = Color.BLACK; textSize = 12f; isAntiAlias = true; isFakeBoldText = true }); y += 18f
        if (content.evidence.isEmpty()) {
            canvas.drawText("Sin evidencias fotográficas adjuntas.", 32f, y, body); y += 18f
        } else {
            content.evidence.take(8).forEach { ev ->
                canvas.drawText("· ${ev.evidenceType.wireValue} — ${ev.label}", 32f, y, mono); y += 14f
                ev.hash?.let {
                    canvas.drawText("  Hash: ${it.take(32)}…", 32f, y, Paint().apply { color = subtle; textSize = 9f; isAntiAlias = true; typeface = android.graphics.Typeface.MONOSPACE }); y += 12f
                }
                y += 4f
            }
        }
        y += 8f

        canvas.drawText("Disclaimer técnico:", 32f, y, Paint().apply { color = Color.BLACK; textSize = 12f; isAntiAlias = true; isFakeBoldText = true }); y += 18f
        listOf(
            "Este reporte es un documento técnico generado por Elysium Vanguard V2.",
            "La integridad del contenido está protegida por SHA-256 (ver footer).",
            "Si el snapshot OBD fue capturado offline, los códigos DTC deben confirmarse con captura en vivo.",
            "Las piezas declaradas como usadas en este reporte NO implican compatibilidad EXACTA",
            "salvo confirmación por VIN / OEM / foto / conector / medidas.",
            "Cualquier edición posterior del reporte rompe la cadena de integridad (ver hash anterior).",
        ).forEach { line ->
            canvas.drawText(line, 32f, y, body); y += 14f
        }
    }

    companion object {
        fun renderQr(payload: String, size: Int = 256): Bitmap {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1,
            )
            val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            return bmp
        }
    }

    private fun formatTimestamp(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))
}
