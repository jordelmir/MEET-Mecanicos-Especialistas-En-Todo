package com.elysium369.meet.core.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.content.Intent
import android.graphics.pdf.PdfDocument
import com.elysium369.meet.data.supabase.Trip
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
        vehicleDetails: String,
        telemetryHistory: Map<String, List<Float>> = emptyMap(),
        anomalies: List<com.elysium369.meet.core.ai.HealthAnomaly> = emptyList(),
        healthScore: Int = 100,
        maintenanceAlerts: List<com.elysium369.meet.data.local.entities.MaintenanceAlertEntity> = emptyList()
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        var pageNumber = 1
        var page = document.startPage(pageInfo)
        var canvas: Canvas = page.canvas
        
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            isAntiAlias = true
        }
        val titlePaint = Paint().apply {
            color = Color.parseColor("#00FFCC") // MEET Cyan
            textSize = 24f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val sectionPaint = Paint().apply {
            color = Color.parseColor("#1A1A1A")
            textSize = 14f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 8f
            isAntiAlias = true
        }

        var y = 0f
        val x = 50f
        val contentWidth = 495f

        // --- Header Section ---
        val headerHeight = 100f
        canvas.drawRect(0f, 0f, 595f, headerHeight, Paint().apply { color = Color.BLACK })
        
        y = 55f
        canvas.drawText("MEET", x, y, titlePaint)
        titlePaint.textSize = 12f
        canvas.drawText("ELITE DIAGNOSTIC", x + 75f, y - 4f, titlePaint)
        
        y = 80f
        paint.color = Color.parseColor("#00FFCC")
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        canvas.drawText("CERTIFICADO DE SALUD VEHICULAR - ${sdf.format(Date(trip.started_at))}", x, y, paint)
        
        // Health Score Gauge
        val scoreColor = when {
            healthScore > 85 -> Color.parseColor("#00FFCC")
            healthScore > 60 -> Color.parseColor("#FFD700")
            else -> Color.parseColor("#FF3366")
        }
        
        canvas.drawArc(480f, 20f, 560f, 100f, 135f, 270f, false, Paint().apply {
            color = Color.parseColor("#333333")
            style = Paint.Style.STROKE
            strokeWidth = 6f
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        })
        
        canvas.drawArc(480f, 20f, 560f, 100f, 135f, 270f * (healthScore / 100f), false, Paint().apply {
            color = scoreColor
            style = Paint.Style.STROKE
            strokeWidth = 6f
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        })
        
        paint.color = Color.WHITE
        paint.textSize = 20f
        paint.isFakeBoldText = true
        val scoreText = "$healthScore%"
        canvas.drawText(scoreText, 520f - (paint.measureText(scoreText) / 2f), 65f, paint)
        
        // --- Body Content ---
        y = headerHeight + 40f
        paint.color = Color.BLACK
        paint.textSize = 11f
        paint.isFakeBoldText = true
        canvas.drawText("DETALLES DEL VEHÍCULO:", x, y, paint)
        y += 20f
        paint.isFakeBoldText = false
        canvas.drawText("Marca/Modelo: $vehicleDetails", x, y, paint)
        y += 15f
        canvas.drawText("ID de Vehículo/VIN: ${trip.vehicle_id}", x, y, paint)
        
        y += 35f
        // Stats Grid
        canvas.drawRect(x, y, x + contentWidth, y + 60f, Paint().apply { color = Color.parseColor("#F9F9F9") })
        val gridY = y + 25f
        paint.isFakeBoldText = true
        canvas.drawText("VEL. MÁXIMA", x + 20, gridY, paint)
        canvas.drawText("RPM MÁXIMA", x + 180, gridY, paint)
        canvas.drawText("TEMP. MÁXIMA", x + 340, gridY, paint)
        
        paint.isFakeBoldText = false
        paint.textSize = 14f
        canvas.drawText("${trip.max_speed_kmh} km/h", x + 20, gridY + 20f, paint)
        canvas.drawText("${trip.max_rpm} RPM", x + 180, gridY + 20f, paint)
        canvas.drawText("${trip.max_temp_c} °C", x + 340, gridY + 20f, paint)
        
        y += 90f
        
        // Summary Executive Section
        canvas.drawText("RESUMEN EJECUTIVO DE DIAGNÓSTICO", x, y, sectionPaint)
        y += 25f
        val summaryBoxPaint = Paint().apply { color = Color.parseColor("#F0F0F0") }
        canvas.drawRect(x, y, x + contentWidth, y + 70f, summaryBoxPaint)
        
        paint.textSize = 10f
        paint.color = Color.DKGRAY
        canvas.drawText("ESTADO GENERAL:", x + 15f, y + 25f, paint)
        
        val statusText = when {
            healthScore > 85 -> "SISTEMA ÓPTIMO"
            healthScore > 60 -> "MANTENIMIENTO REQUERIDO"
            else -> "ALERTA CRÍTICA"
        }
        paint.color = scoreColor
        paint.isFakeBoldText = true
        paint.textSize = 12f
        canvas.drawText(statusText, x + 120f, y + 25f, paint)
        
        paint.color = Color.BLACK
        paint.isFakeBoldText = false
        paint.textSize = 10f
        canvas.drawText("DTCs ACTIVOS: ${dtcs.size}", x + 15f, y + 45f, paint)
        canvas.drawText("ANOMALÍAS AI: ${anomalies.size}", x + 120f, y + 45f, paint)
        
        y += 100f

        // DTCs Section
        canvas.drawText("SISTEMAS ELECTRÓNICOS Y DTCs:", x, y, sectionPaint)
        y += 25f
        if (dtcs.isEmpty()) {
            paint.color = Color.parseColor("#007A63")
            canvas.drawText("✓ Análisis completo: No se detectaron códigos de falla activos en la ECU.", x, y, paint)
            y += 20f
        } else {
            paint.color = Color.parseColor("#D32F2F")
            dtcs.forEach { dtc ->
                canvas.drawText("• ERROR $dtc: ${com.elysium369.meet.core.obd.DtcDecoder.getLocalDescription(dtc)}", x, y, paint)
                y += 20f
            }
        }
        y += 20f

        // Maintenance Alerts Section
        if (maintenanceAlerts.isNotEmpty()) {
            canvas.drawText("ALERTAS DE MANTENIMIENTO PREVENTIVO:", x, y, sectionPaint)
            y += 25f
            paint.color = Color.BLACK
            maintenanceAlerts.forEach { alert ->
                if (y > 780f) {
                    page = createNewPage(document, page, ++pageNumber, footerPaint)
                    canvas = page.canvas
                    y = 60f
                }
                canvas.drawText("• [${alert.type.uppercase()}] Vencimiento a los ${alert.nextDueKm} km ${if (alert.notes != null) "- ${alert.notes}" else ""}", x, y, paint)
                y += 15f
            }
            y += 20f
        }

        // Anomalies Table
        if (anomalies.isNotEmpty()) {
            canvas.drawText("DETECTED ANOMALIES & PREDICTIVE INSIGHTS:", x, y, sectionPaint)
            y += 25f
            
            canvas.drawRect(x, y - 15f, x + contentWidth, y + 5f, Paint().apply { color = Color.parseColor("#1A1A1A") })
            val tableTextPaint = Paint().apply {
                color = Color.WHITE; textSize = 9f; isFakeBoldText = true; isAntiAlias = true
            }
            canvas.drawText("SENSOR / PID", x + 10f, y - 2f, tableTextPaint)
            canvas.drawText("ANÁLISIS DE INTELIGENCIA ARTIFICIAL", x + 120f, y - 2f, tableTextPaint)
            
            y += 20f
            tableTextPaint.color = Color.BLACK; tableTextPaint.isFakeBoldText = false
            
            anomalies.forEach { anomaly ->
                if (y > 780f) {
                    page = createNewPage(document, page, ++pageNumber, footerPaint)
                    canvas = page.canvas
                    y = 60f
                }
                
                val pidName = com.elysium369.meet.core.obd.PidRegistry.getPid("01", anomaly.pid)?.name ?: anomaly.pid
                canvas.drawText(pidName.uppercase(), x + 10f, y, tableTextPaint)
                
                val insightLines = wrapText(anomaly.insight, tableTextPaint, 360f)
                insightLines.forEachIndexed { i, line ->
                    canvas.drawText(line, x + 120f, y + (i * 12f), tableTextPaint)
                }
                
                y += (insightLines.size * 12f).coerceAtLeast(20f) + 10f
                canvas.drawLine(x, y - 5f, x + contentWidth, y - 5f, Paint().apply { color = Color.parseColor("#EEEEEE") })
            }
            y += 20f
        }

        // Telemetry Graphs
        if (telemetryHistory.isNotEmpty()) {
            canvas.drawText("TELEMETRY WAVEFORM ANALYSIS:", x, y, sectionPaint)
            y += 30f
            
            val prioritizedPids = (anomalies.map { it.pid } + telemetryHistory.keys).distinct().take(8)
            prioritizedPids.forEach { pid ->
                val data = telemetryHistory[pid] ?: return@forEach
                if (data.isEmpty()) return@forEach
                
                if (y > 680f) {
                    page = createNewPage(document, page, ++pageNumber, footerPaint)
                    canvas = page.canvas
                    y = 60f
                }
                
                val anomaly = anomalies.find { it.pid == pid }
                drawWaveform(canvas, x, y, contentWidth, 110f, pid, data, insight = anomaly?.insight)
                y += 145f
            }
        }

        // AI Final Verdict Section
        if (aiAnalysis != null) {
            if (y > 550f) {
                page = createNewPage(document, page, ++pageNumber, footerPaint)
                canvas = page.canvas
                y = 60f
            }
            drawAiSection(canvas, x, y, aiAnalysis)
        }
        
        // --- Save and Return ---
        document.finishPage(page)
        
        val directory = File(context.getExternalFilesDir(null), "Reports")
        if (!directory.exists()) directory.mkdirs()
        
        val fileName = "MEET_Report_${trip.id}_${System.currentTimeMillis()}.pdf"
        val file = File(directory, fileName)
        
        try {
            document.writeTo(FileOutputStream(file))
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            document.close()
        }
        
        return file
    }

    private fun createNewPage(doc: PdfDocument, oldPage: PdfDocument.Page, num: Int, footerPaint: Paint): PdfDocument.Page {
        doc.finishPage(oldPage)
        val info = PdfDocument.PageInfo.Builder(595, 842, num).create()
        val newPage = doc.startPage(info)
        newPage.canvas.drawText("Página $num | MEET Elite Diagnostic Report", 250f, 820f, footerPaint)
        return newPage
    }

    private fun drawAiSection(canvas: Canvas, x: Float, startY: Float, text: String) {
        var y = startY
        val aiHeaderPaint = Paint().apply { color = Color.parseColor("#001A1A") }
        canvas.drawRect(x, y - 18f, x + 495f, y + 12f, aiHeaderPaint)
        
        val aiTitlePaint = Paint().apply {
            color = Color.parseColor("#00FFCC"); textSize = 14f; isFakeBoldText = true; isAntiAlias = true
        }
        canvas.drawText("✨ CONCLUSIÓN Y RECOMENDACIONES (MEET AI ELITE PREDICTOR):", x + 10f, y, aiTitlePaint)
        y += 40f
        
        val textPaint = Paint().apply { color = Color.BLACK; textSize = 10f; isAntiAlias = true }
        val lines = wrapText(text, textPaint, 465f)
        val boxHeight = (lines.size * 18f) + 30f
        
        canvas.drawRoundRect(x, y - 10f, x + 495f, y + boxHeight - 10f, 10f, 10f, Paint().apply { color = Color.parseColor("#F0FFFF") })
        
        y += 15f
        lines.forEach { l ->
            canvas.drawText(l, x + 15f, y, textPaint)
            y += 18f
        }
    }

    private fun drawWaveform(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, label: String, data: List<Float>, insight: String? = null) {
        val isAnomalous = insight != null
        val framePaint = Paint().apply {
            color = if (isAnomalous) Color.parseColor("#FFF0F0") else Color.parseColor("#F5F5F7")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(x, y, x + width, y + height, 8f, 8f, framePaint)
        
        if (isAnomalous) {
            canvas.drawRoundRect(x, y, x + width, y + height, 8f, 8f, Paint().apply {
                color = Color.parseColor("#FF3366"); style = Paint.Style.STROKE; strokeWidth = 1.5f; isAntiAlias = true
            })
            
            canvas.drawRoundRect(x + width - 70f, y + 10f, x + width - 10f, y + 25f, 4f, 4f, Paint().apply {
                color = Color.parseColor("#FF3366"); isAntiAlias = true
            })
            canvas.drawText("AI DETECTED", x + width - 65f, y + 20f, Paint().apply {
                color = Color.WHITE; textSize = 7f; isFakeBoldText = true; isAntiAlias = true
            })
        }

        val labelPaint = Paint().apply {
            color = if (isAnomalous) Color.parseColor("#FF3366") else Color.BLACK; textSize = 10f; isFakeBoldText = true
        }
        val sensorName = com.elysium369.meet.core.obd.PidRegistry.getPid("01", label)?.name ?: label
        canvas.drawText(sensorName.uppercase(), x + 10f, y + 18f, labelPaint)
        
        if (isAnomalous) {
            labelPaint.textSize = 8f; labelPaint.isFakeBoldText = false
            canvas.drawText("ANÁLISIS PREDICTIVO: $insight", x + 10f, y + 32f, labelPaint)
        }

        if (data.size < 2) return
        
        val maxVal = data.maxOrNull() ?: 1f; val minVal = data.minOrNull() ?: 0f; val range = (maxVal - minVal).coerceAtLeast(0.1f)
        val mainColor = if (isAnomalous) Color.parseColor("#FF3366") else Color.parseColor("#00FFCC")
        
        val path = android.graphics.Path()
        val fillPath = android.graphics.Path()
        val stepX = (width - 20f) / (data.size - 1); val startX = x + 10f; val baselineY = y + height - 10f
        
        data.forEachIndexed { index, value ->
            val normY = (value - minVal) / range
            val px = startX + (index * stepX); val py = baselineY - (normY * (height - 40f))
            if (index == 0) { path.moveTo(px, py); fillPath.moveTo(px, baselineY); fillPath.lineTo(px, py) }
            else { path.lineTo(px, py); fillPath.lineTo(px, py) }
            if (index == data.size - 1) { fillPath.lineTo(px, baselineY); fillPath.close() }
        }
        
        canvas.drawPath(fillPath, Paint().apply {
            shader = android.graphics.LinearGradient(0f, y + 20f, 0f, baselineY, mainColor and 0x40FFFFFF, Color.TRANSPARENT, android.graphics.Shader.TileMode.CLAMP)
            style = Paint.Style.FILL; isAntiAlias = true
        })
        canvas.drawPath(path, Paint().apply { color = mainColor; strokeWidth = 2f; style = Paint.Style.STROKE; isAntiAlias = true; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND })
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""
        words.forEach { word ->
            if (paint.measureText("$currentLine $word") < maxWidth) {
                currentLine += if (currentLine.isEmpty()) word else " $word"
            } else {
                lines.add(currentLine); currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)
        return lines
    }

    fun shareReport(pdfFile: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", pdfFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Diagnóstico MEET ELITE — ${pdfFile.nameWithoutExtension}")
            putExtra(Intent.EXTRA_TEXT, "Adjunto el reporte de diagnóstico generado por MEET ELITE AI.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Compartir diagnóstico")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun exportSessionAsCsv(sessionId: String, liveDataPoints: List<Pair<Long, Pair<String, Float>>>): File {
        val csv = StringBuilder(); csv.appendLine("Timestamp,PID,Valor")
        liveDataPoints.forEach { point -> csv.appendLine("${point.first},${point.second.first},${point.second.second}") }
        val dir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "CSV")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "MEET_session_${sessionId}_data.csv")
        file.writeText(csv.toString())
        return file
    }
}
