package com.elysium369.meet.ride.location

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
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
 * Generates a forensic-grade PDF report of a GPS trail.
 * Includes: cover page with QR, GPS point table, route summary, integrity hash.
 * Designed for presentation to law enforcement or judicial authorities.
 */
object GpsTrailPdfExporter {

    data class ExportResult(
        val file: File,
        val integrityHash: String,
        val pointCount: Int,
    )

    suspend fun exportPdf(
        context: Context,
        trail: GpsForensicTrail,
    ): ExportResult? {
        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val fileName = "GPS_Forensic_${trail.rideId.take(12)}_${System.currentTimeMillis()}.pdf"
            val file = File(context.cacheDir, fileName)

            val doc = PdfDocument()
            val pageWidth = 595  // A4
            val pageHeight = 842

            // ── Page 1: Cover + QR ──
            val page1 = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
            drawCoverPage(page1.canvas, trail, dateFormat, pageWidth, pageHeight)
            doc.finishPage(page1)

            // ── Page 2: Route summary + stats ──
            val page2 = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create())
            drawSummaryPage(page2.canvas, trail, dateFormat, pageWidth, pageHeight)
            doc.finishPage(page2)

            // ── Page 3+: GPS point table ──
            val pointsPerPage = 40
            val totalPages = (trail.points.size + pointsPerPage - 1) / pointsPerPage
            for (pageIdx in 0 until totalPages) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 3 + pageIdx).create()
                val page = doc.startPage(pageInfo)
                drawPointsPage(page.canvas, trail, dateFormat, pageWidth, pageHeight, pageIdx, pointsPerPage)
                doc.finishPage(page)
            }

            FileOutputStream(file).use { out ->
                doc.writeTo(out)
            }
            doc.close()

            ExportResult(
                file = file,
                integrityHash = trail.integrityHash,
                pointCount = trail.points.size,
            )
        } catch (e: Exception) {
            android.util.Log.e("GpsTrailPdfExporter", "Failed to export PDF: ${e.message}", e)
            null
        }
    }

    private fun drawCoverPage(
        canvas: Canvas,
        trail: GpsForensicTrail,
        dateFormat: SimpleDateFormat,
        pageWidth: Int,
        pageHeight: Int,
    ) {
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 28f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 14f
            isAntiAlias = true
        }
        val monoPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            typeface = android.graphics.Typeface.MONOSPACE
            isAntiAlias = true
        }
        val labelPaint = Paint().apply {
            color = Color.GRAY
            textSize = 11f
            isAntiAlias = true
        }

        var y = 80f

        // Title
        canvas.drawText("REPORTE FORENSE GPS", 50f, y, titlePaint)
        y += 30f
        canvas.drawText("Elysium Vanguard · Evidencia de Recorrido", 50f, y, subtitlePaint)
        y += 50f

        // QR Code
        try {
            val qrContent = "v1|${trail.rideId}|${trail.integrityHash}|gps_forensic|${trail.completedAtEpochMs}"
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1,
            )
            val bits = QRCodeWriter().encode(qrContent, BarcodeFormat.QR_CODE, 200, 200, hints)
            val bmp = android.graphics.Bitmap.createBitmap(200, 200, android.graphics.Bitmap.Config.RGB_565)
            for (x in 0 until 200) for (dy in 0 until 200) {
                bmp.setPixel(x, dy, if (bits[x, dy]) Color.BLACK else Color.WHITE)
            }
            canvas.drawBitmap(bmp, (pageWidth - 200) / 2f, y, null)
            y += 220f
        } catch (_: Exception) {
            y += 30f
        }

        // Metadata
        val details = listOf(
            "ID del viaje" to trail.rideId,
            "Conductor" to trail.driverId.take(16) + "...",
            "Pasajero" to trail.passengerId.take(16) + "...",
            "Inicio" to dateFormat.format(Date(trail.startedAtEpochMs)),
            "Fin" to dateFormat.format(Date(trail.completedAtEpochMs)),
            "Duración" to "${trail.durationMinutes} minutos",
            "Puntos GPS" to "${trail.points.size}",
            "Distancia" to "${"%.2f".format(trail.totalDistanceMeters / 1000)} km",
            "Velocidad promedio" to "${"%.1f".format(trail.averageSpeedMps * 3.6)} km/h",
            "Velocidad máxima" to "${"%.1f".format(trail.maxSpeedMps * 3.6)} km/h",
        )

        details.forEach { (label, value) ->
            canvas.drawText("$label:", 50f, y, labelPaint)
            canvas.drawText(value, 200f, y, subtitlePaint)
            y += 20f
        }

        y += 30f
        canvas.drawText("INTEGRIDAD", 50f, y, titlePaint.apply { textSize = 16f })
        y += 25f
        canvas.drawText("Hash SHA-256:", 50f, y, labelPaint)
        y += 15f
        // Split hash into lines of 60 chars
        val hash = trail.integrityHash
        var hashOffset = 0
        while (hashOffset < hash.length) {
            val end = minOf(hashOffset + 60, hash.length)
            canvas.drawText(hash.substring(hashOffset, end), 50f, y, monoPaint)
            y += 14f
            hashOffset = end
        }

        y += 20f
        canvas.drawText("Este documento es evidencia forense generada por Elysium Vanguard.", 50f, y, labelPaint)
        y += 14f
        canvas.drawText("El hash SHA-256 garantiza la integridad del contenido.", 50f, y, labelPaint)
        y += 14f
        canvas.drawText("Cualquier modificación altera el hash y es detectable.", 50f, y, labelPaint)

        // Footer
        drawFooter(canvas, trail.integrityHash, pageWidth, pageHeight)
    }

    private fun drawSummaryPage(
        canvas: Canvas,
        trail: GpsForensicTrail,
        dateFormat: SimpleDateFormat,
        pageWidth: Int,
        pageHeight: Int,
    ) {
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 18f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            isAntiAlias = true
        }
        val labelPaint = Paint().apply {
            color = Color.GRAY
            textSize = 10f
            isAntiAlias = true
        }

        var y = 60f
        canvas.drawText("RESUMEN DE RUTA", 50f, y, titlePaint)
        y += 30f

        // First and last points
        if (trail.points.isNotEmpty()) {
            val first = trail.points.first()
            val last = trail.points.last()

            canvas.drawText("Punto de inicio:", 50f, y, labelPaint)
            y += 15f
            canvas.drawText("  Lat: ${first.latitude}  Lng: ${first.longitude}", 50f, y, textPaint)
            y += 15f
            canvas.drawText("  Precisión: ${"%.1f".format(first.accuracyMeters)}m  Hora: ${dateFormat.format(Date(first.capturedAtEpochMs))}", 50f, y, textPaint)
            y += 25f

            canvas.drawText("Punto final:", 50f, y, labelPaint)
            y += 15f
            canvas.drawText("  Lat: ${last.latitude}  Lng: ${last.longitude}", 50f, y, textPaint)
            y += 15f
            canvas.drawText("  Precisión: ${"%.1f".format(last.accuracyMeters)}m  Hora: ${dateFormat.format(Date(last.capturedAtEpochMs))}", 50f, y, textPaint)
            y += 30f
        }

        // Speed distribution
        canvas.drawText("DISTRIBUCIÓN DE VELOCIDAD", 50f, y, titlePaint)
        y += 25f
        val speedBuckets = IntArray(8) // 0-10, 10-20, ..., 70+
        trail.points.forEach { p ->
            val kmh = (p.speedMetersPerSecond ?: 0f) * 3.6f
            val bucket = (kmh / 10f).toInt().coerceIn(0, 7)
            speedBuckets[bucket]++
        }
        val barPaint = Paint().apply { color = Color.DKGRAY; isAntiAlias = true }
        val barFillPaint = Paint().apply { color = Color.parseColor("#00C853"); isAntiAlias = true }
        val maxCount = speedBuckets.max().coerceAtLeast(1)
        val barWidth = 350f
        val barHeight = 16f

        for (i in speedBuckets.indices) {
            val label = if (i < 7) "${i * 10}-${(i + 1) * 10} km/h" else "70+ km/h"
            canvas.drawText(label, 50f, y + 12f, labelPaint)
            val fillWidth = (speedBuckets[i].toFloat() / maxCount) * barWidth
            canvas.drawRect(200f, y, 200f + barWidth, y + barHeight, barPaint)
            canvas.drawRect(200f, y, 200f + fillWidth, y + barHeight, barFillPaint)
            canvas.drawText("${speedBuckets[i]}", 210f + barWidth, y + 12f, labelPaint)
            y += barHeight + 6f
        }

        y += 20f

        // Accuracy summary
        canvas.drawText("CALIDAD DEL GPS", 50f, y, titlePaint)
        y += 25f
        val accuracies = trail.points.map { it.accuracyMeters }
        val avgAccuracy = accuracies.average()
        val maxAccuracy = accuracies.maxOrNull() ?: 0f
        val minAccuracy = accuracies.minOrNull() ?: 0f
        canvas.drawText("Precisión promedio: ${"%.1f".format(avgAccuracy)}m", 50f, y, textPaint); y += 16f
        canvas.drawText("Mejor precisión: ${"%.1f".format(minAccuracy)}m", 50f, y, textPaint); y += 16f
        canvas.drawText("Peor precisión: ${"%.1f".format(maxAccuracy)}m", 50f, y, textPaint); y += 16f
        canvas.drawText("Puntos totales: ${trail.points.size}", 50f, y, textPaint); y += 16f
        canvas.drawText("Intervalo promedio: ${"%.0f".format(if (trail.points.size > 1) trail.durationMs.toDouble() / (trail.points.size - 1) / 1000 else 0.0)}s", 50f, y, textPaint)

        drawFooter(canvas, trail.integrityHash, pageWidth, pageHeight)
    }

    private fun drawPointsPage(
        canvas: Canvas,
        trail: GpsForensicTrail,
        dateFormat: SimpleDateFormat,
        pageWidth: Int,
        pageHeight: Int,
        pageIdx: Int,
        pointsPerPage: Int,
    ) {
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 9f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val cellPaint = Paint().apply {
            color = Color.BLACK
            textSize = 8f
            typeface = android.graphics.Typeface.MONOSPACE
            isAntiAlias = true
        }

        val startIdx = pageIdx * pointsPerPage
        val endIdx = minOf(startIdx + pointsPerPage, trail.points.size)

        var y = 50f
        canvas.drawText("PUNTOS GPS · Página ${pageIdx + 1} · Puntos ${startIdx + 1}-$endIdx de ${trail.points.size}", 50f, y, titlePaint)
        y += 25f

        // Header
        val cols = floatArrayOf(50f, 100f, 180f, 260f, 330f, 400f, 470f)
        val headers = listOf("#", "Latitud", "Longitud", "Precisión", "Velocidad", "Rumbo", "Hora")
        headers.forEachIndexed { i, h ->
            canvas.drawText(h, cols[i], y, headerPaint)
        }
        y += 15f
        canvas.drawLine(50f, y, pageWidth - 50f, y, Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f })
        y += 5f

        // Rows
        for (i in startIdx until endIdx) {
            val p = trail.points[i]
            val timeStr = dateFormat.format(Date(p.capturedAtEpochMs)).substring(11) // HH:mm:ss
            val speedStr = p.speedMetersPerSecond?.let { "${"%.1f".format(it * 3.6)}" } ?: "—"
            val hdgStr = p.headingDegrees?.let { "${it}°" } ?: "—"

            canvas.drawText("${i + 1}", cols[0], y, cellPaint)
            canvas.drawText("${"%.6f".format(p.latitude)}", cols[1], y, cellPaint)
            canvas.drawText("${"%.6f".format(p.longitude)}", cols[2], y, cellPaint)
            canvas.drawText("${"%.1f".format(p.accuracyMeters)}m", cols[3], y, cellPaint)
            canvas.drawText("${speedStr}km/h", cols[4], y, cellPaint)
            canvas.drawText(hdgStr, cols[5], y, cellPaint)
            canvas.drawText(timeStr, cols[6], y, cellPaint)
            y += 12f
        }

        drawFooter(canvas, trail.integrityHash, pageWidth, pageHeight)
    }

    private fun drawFooter(canvas: Canvas, hash: String, pageWidth: Int, pageHeight: Int) {
        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 7f
            typeface = android.graphics.Typeface.MONOSPACE
            isAntiAlias = true
        }
        canvas.drawText(
            "Elysium Vanguard · Hash: ${hash.take(32)}… · Generado ${System.currentTimeMillis()}",
            50f,
            pageHeight - 30f,
            footerPaint,
        )
    }

    fun sharePdf(context: Context, result: ExportResult) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            result.file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Reporte GPS Forense · ${result.integrityHash.take(16)}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir reporte GPS"))
    }
}
