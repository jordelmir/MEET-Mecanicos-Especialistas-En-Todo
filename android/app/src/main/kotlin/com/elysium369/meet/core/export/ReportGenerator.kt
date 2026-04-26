package com.elysium369.meet.core.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.content.Intent
import android.graphics.pdf.PdfDocument
import com.elysium369.meet.core.trips.Trip
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportGenerator(private val context: Context) {

    fun generatePdfReport(
        trip: Trip,
        dtcs: List<String>,
        aiAnalysis: String?,
        vehicleDetails: String
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
        }
        val titlePaint = Paint().apply {
            color = Color.parseColor("#FF6B35") // MEET Orange
            textSize = 24f
            isFakeBoldText = true
        }

        var y = 50f
        val x = 50f

        // Header
        canvas.drawText("MEET Workshop - Reporte de Diagnóstico OBD2", x, y, titlePaint)
        y += 40f
        
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        canvas.drawText("Fecha: ${sdf.format(Date(trip.startTime))}", x, y, paint)
        y += 20f
        canvas.drawText("Vehículo: $vehicleDetails", x, y, paint)
        y += 40f

        // Resumen del Viaje
        paint.isFakeBoldText = true
        canvas.drawText("Estadísticas del Ciclo de Conducción:", x, y, paint)
        paint.isFakeBoldText = false
        y += 20f
        canvas.drawText("Velocidad Máx: ${trip.maxSpeed} km/h", x, y, paint)
        y += 20f
        canvas.drawText("RPM Máx: ${trip.maxRpm}", x, y, paint)
        y += 20f
        canvas.drawText("Temp Máx Motor: ${trip.maxTemp} °C", x, y, paint)
        y += 40f

        // DTCs
        paint.isFakeBoldText = true
        canvas.drawText("Códigos de Falla (DTCs):", x, y, paint)
        paint.isFakeBoldText = false
        y += 20f
        if (dtcs.isEmpty()) {
            canvas.drawText("No se encontraron fallas.", x, y, paint)
            y += 20f
        } else {
            dtcs.forEach { dtc ->
                canvas.drawText("• $dtc", x, y, paint)
                y += 20f
            }
        }
        y += 20f

        // Análisis IA
        if (aiAnalysis != null) {
            paint.isFakeBoldText = true
            canvas.drawText("Análisis de Inteligencia Artificial (MEET AI):", x, y, paint)
            paint.isFakeBoldText = false
            y += 20f
            
            // Simple text wrapping logic for PDF
            val words = aiAnalysis.split(" ")
            var line = ""
            words.forEach { word ->
                if (paint.measureText("$line $word") < 495f) {
                    line += "$word "
                } else {
                    canvas.drawText(line, x, y, paint)
                    y += 20f
                    line = "$word "
                }
            }
            canvas.drawText(line, x, y, paint)
        }

        document.finishPage(page)

        val dir = File(context.getExternalFilesDir(null), "Reports")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "MEET_Report_${trip.id}.pdf")
        
        document.writeTo(FileOutputStream(file))
        document.close()
        
        return file
    }

    fun shareReport(pdfFile: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Diagnóstico MEET — ${pdfFile.nameWithoutExtension}")
            putExtra(Intent.EXTRA_TEXT, "Adjunto el reporte de diagnóstico generado por MEET OBD2")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Compartir diagnóstico")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun exportSessionAsCsv(sessionId: String, liveDataPoints: List<Pair<Long, Pair<String, Float>>>): File {
        val csv = StringBuilder()
        csv.appendLine("Timestamp,PID,Valor")
        liveDataPoints.forEach { point ->
            csv.appendLine("${point.first},${point.second.first},${point.second.second}")
        }
        val dir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "CSV")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "MEET_session_${sessionId}_data.csv")
        file.writeText(csv.toString())
        return file
    }
}
