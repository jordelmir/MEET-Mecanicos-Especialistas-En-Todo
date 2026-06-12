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
        maintenanceAlerts: List<com.elysium369.meet.data.local.entities.MaintenanceAlertEntity> = emptyList(),
        predictiveReport: com.elysium369.meet.core.health.PredictiveHealthReport? = null,
        themeName: String = "ELYSIUM_CYAN",
        includeDtcs: Boolean = true,
        includeAi: Boolean = true,
        includeGraphs: Boolean = true,
        includePredictive: Boolean = true,
        includeBranding: Boolean = true
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        var pageNumber = 1
        var page = document.startPage(pageInfo)
        var canvas: Canvas = page.canvas
        
        // --- Theme Color Mapping ---
        val theme = themeName.uppercase()
        val headerBg = if (theme == "PRINTER_FRIENDLY") Color.WHITE else when (theme) {
            "CARBON_RED" -> Color.parseColor("#1A0505")
            "CLASSIC_DARK" -> Color.parseColor("#1C1A17")
            else -> Color.parseColor("#0A0A0A")
        }
        val headerTextColor = if (theme == "PRINTER_FRIENDLY") Color.BLACK else Color.WHITE
        val accentColor = when (theme) {
            "CARBON_RED" -> Color.parseColor("#FF3333")
            "CLASSIC_DARK" -> Color.parseColor("#FFB300")
            "PRINTER_FRIENDLY" -> Color.BLACK
            else -> Color.parseColor("#00FFCC")
        }
        val sectionTitleColor = when (theme) {
            "CARBON_RED" -> Color.parseColor("#2A1212")
            "CLASSIC_DARK" -> Color.parseColor("#2A2722")
            "PRINTER_FRIENDLY" -> Color.BLACK
            else -> Color.parseColor("#1A1A1A")
        }
        val cardBg = when (theme) {
            "CARBON_RED" -> Color.parseColor("#FFF5F5")
            "CLASSIC_DARK" -> Color.parseColor("#FFFDF6")
            "PRINTER_FRIENDLY" -> Color.WHITE
            else -> Color.parseColor("#F9F9F9")
        }
        val cardBorder = when (theme) {
            "CARBON_RED" -> Color.parseColor("#FFD6D6")
            "CLASSIC_DARK" -> Color.parseColor("#FFE8A3")
            "PRINTER_FRIENDLY" -> Color.LTGRAY
            else -> Color.parseColor("#EEEEEE")
        }
        
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            isAntiAlias = true
        }
        val titlePaint = Paint().apply {
            color = accentColor
            textSize = 24f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val sectionPaint = Paint().apply {
            color = sectionTitleColor
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
        val prefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
        val workshopName = prefs.getString("workshop_name", "")?.takeIf { it.isNotBlank() }
        val workshopAddress = prefs.getString("workshop_address", "") ?: ""
        val workshopPhone = prefs.getString("workshop_phone", "") ?: ""
        val workshopEmail = prefs.getString("workshop_email", "") ?: ""

        val headerHeight = 115f
        canvas.drawRect(0f, 0f, 595f, headerHeight, Paint().apply { color = headerBg })
        if (theme == "PRINTER_FRIENDLY") {
            canvas.drawLine(0f, headerHeight, 595f, headerHeight, Paint().apply { color = Color.BLACK; strokeWidth = 1f })
        }
        
        y = 45f
        if (includeBranding && workshopName != null) {
            titlePaint.color = headerTextColor
            canvas.drawText(workshopName.uppercase(), x, y, titlePaint)
            
            paint.color = if (theme == "PRINTER_FRIENDLY") Color.DKGRAY else Color.LTGRAY
            paint.textSize = 9f
            paint.isFakeBoldText = false
            var subY = y + 15f
            if (workshopAddress.isNotBlank()) {
                canvas.drawText(workshopAddress, x, subY, paint)
                subY += 12f
            }
            if (workshopPhone.isNotBlank() || workshopEmail.isNotBlank()) {
                val separator = if (workshopPhone.isNotBlank() && workshopEmail.isNotBlank()) "  |  " else ""
                canvas.drawText("$workshopPhone$separator$workshopEmail", x, subY, paint)
            }
            
            paint.color = accentColor
            paint.textSize = 8f
            paint.isFakeBoldText = true
            canvas.drawText("POWERED BY ELYSIUM VANGUARD", 335f, 25f, paint)
        } else {
            titlePaint.color = accentColor
            canvas.drawText("ELYSIUM", x, y, titlePaint)
            titlePaint.textSize = 12f
            titlePaint.color = headerTextColor
            canvas.drawText("VANGUARD DIAGNOSTIC", x + 95f, y - 4f, titlePaint)
        }
        
        y = headerHeight - 15f
        paint.color = accentColor
        paint.textSize = 10f
        paint.isFakeBoldText = true
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        canvas.drawText("CERTIFICADO DE SALUD VEHICULAR - ${sdf.format(Date(trip.started_at))}", x, y, paint)
        
        // Health Score Gauge
        val scoreColor = when {
            healthScore > 85 -> if (theme == "PRINTER_FRIENDLY") Color.parseColor("#333333") else Color.parseColor("#00FFCC")
            healthScore > 60 -> if (theme == "PRINTER_FRIENDLY") Color.parseColor("#666666") else Color.parseColor("#FFD700")
            else -> if (theme == "PRINTER_FRIENDLY") Color.parseColor("#999999") else Color.parseColor("#FF3366")
        }
        
        canvas.drawArc(480f, 20f, 560f, 100f, 135f, 270f, false, Paint().apply {
            color = if (theme == "PRINTER_FRIENDLY") Color.LTGRAY else Color.parseColor("#333333")
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
        
        paint.color = headerTextColor
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
        canvas.drawRect(x, y, x + contentWidth, y + 60f, Paint().apply { color = cardBg })
        canvas.drawRect(x, y, x + contentWidth, y + 60f, Paint().apply { color = cardBorder; style = Paint.Style.STROKE; strokeWidth = 1f })
        
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
        canvas.drawRect(x, y, x + contentWidth, y + 70f, Paint().apply { color = cardBg })
        canvas.drawRect(x, y, x + contentWidth, y + 70f, Paint().apply { color = cardBorder; style = Paint.Style.STROKE; strokeWidth = 1f })
        
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
        
        y += 85f

        // Predictive Health Section
        if (includePredictive && predictiveReport != null) {
            if (y > 650f) {
                page = createNewPage(document, page, ++pageNumber, footerPaint)
                canvas = page.canvas
                y = 60f
            }
            
            canvas.drawText("SALUD PREDICTIVA DE SUBSISTEMAS", x, y, sectionPaint)
            y += 25f
            
            val subsystems = listOf(
                "MOTOR" to predictiveReport.engineScore,
                "COMBUSTIBLE" to predictiveReport.fuelScore,
                "ENFRIAMIENTO" to predictiveReport.coolingScore,
                "ELÉCTRICO" to predictiveReport.electricalScore,
                "EMISIONES" to predictiveReport.emissionsScore
            )
            
            val barX = x + 100f
            val maxBarWidth = 350f
            
            subsystems.forEach { (name, score) ->
                paint.color = Color.BLACK
                paint.textSize = 10f
                paint.isFakeBoldText = true
                canvas.drawText(name, x, y + 8f, paint)
                
                // Background bar
                canvas.drawRoundRect(barX, y, barX + maxBarWidth, y + 12f, 6f, 6f, Paint().apply { color = Color.parseColor("#E0E0E0") })
                
                // Filled bar
                val barColor = when {
                    score > 85 -> if (theme == "PRINTER_FRIENDLY") Color.parseColor("#444444") else Color.parseColor("#00FFCC")
                    score > 60 -> if (theme == "PRINTER_FRIENDLY") Color.parseColor("#888888") else Color.parseColor("#FFD700")
                    else -> if (theme == "PRINTER_FRIENDLY") Color.parseColor("#AAAAAA") else Color.parseColor("#FF3366")
                }
                val fillWidth = maxBarWidth * (score / 100f)
                canvas.drawRoundRect(barX, y, barX + fillWidth, y + 12f, 6f, 6f, Paint().apply { color = barColor })
                
                // Score text
                paint.color = Color.DKGRAY
                paint.isFakeBoldText = false
                paint.textSize = 9f
                canvas.drawText("$score%", barX + maxBarWidth + 15f, y + 9f, paint)
                
                y += 25f
            }
            
            y += 10f

            // Electrical Diagnosis Sub-section
            if (predictiveReport.electricalDiagnosis != null) {
                val diag = predictiveReport.electricalDiagnosis
                paint.color = Color.DKGRAY
                paint.isFakeBoldText = true
                paint.textSize = 10f
                canvas.drawText("Diagnóstico Eléctrico Detallado:", x, y, paint)
                y += 15f
                
                paint.isFakeBoldText = false
                paint.color = Color.BLACK
                canvas.drawText("Alternador: ${diag.alternatorState}", x + 10f, y, paint)
                y += 12f
                canvas.drawText("Batería: ${diag.batteryState}", x + 10f, y, paint)
                y += 12f
                
                paint.color = if (theme == "PRINTER_FRIENDLY") Color.BLACK else Color.parseColor("#007A63")
                val recLines = wrapText("Recomendación: ${diag.recommendation}", paint, contentWidth - 20f)
                recLines.forEach { line ->
                    canvas.drawText(line, x + 10f, y, paint)
                    y += 12f
                }
                y += 15f
            }

            // Predictive Alerts
            if (predictiveReport.alerts.isNotEmpty()) {
                canvas.drawText("ALERTAS PREDICTIVAS TEMPRANAS:", x, y, sectionPaint)
                y += 25f
                
                predictiveReport.alerts.forEach { alert ->
                    if (y > 780f) {
                        page = createNewPage(document, page, ++pageNumber, footerPaint)
                        canvas = page.canvas
                        y = 60f
                    }
                    
                    val alertColor = when (alert.severity) {
                        com.elysium369.meet.core.health.AlertSeverity.CRITICAL -> if (theme == "PRINTER_FRIENDLY") Color.BLACK else Color.parseColor("#D32F2F")
                        com.elysium369.meet.core.health.AlertSeverity.HIGH -> if (theme == "PRINTER_FRIENDLY") Color.DKGRAY else Color.parseColor("#F57C00")
                        com.elysium369.meet.core.health.AlertSeverity.MODERATE -> if (theme == "PRINTER_FRIENDLY") Color.GRAY else Color.parseColor("#FBC02D")
                    }
                    
                    // Card background
                    canvas.drawRoundRect(x, y, x + contentWidth, y + 45f, 6f, 6f, Paint().apply { color = cardBg })
                    canvas.drawRoundRect(x, y, x + contentWidth, y + 45f, 6f, 6f, Paint().apply { color = cardBorder; style = Paint.Style.STROKE; strokeWidth = 1f })
                    
                    // Severity border strip
                    canvas.drawRect(x, y, x + 5f, y + 45f, Paint().apply { color = alertColor })
                    
                    paint.color = alertColor
                    paint.textSize = 10f
                    paint.isFakeBoldText = true
                    canvas.drawText("[${alert.severity.name}] ${alert.label}", x + 15f, y + 15f, paint)
                    
                    paint.color = Color.DKGRAY
                    paint.isFakeBoldText = false
                    paint.textSize = 9f
                    canvas.drawText("Días estimados para fallo crítico: ${if (alert.predictedDaysToFailure == 0) "INMINENTE" else "~${alert.predictedDaysToFailure} días"}", x + 15f, y + 28f, paint)
                    
                    paint.color = Color.BLACK
                    val lines = wrapText(alert.message, paint, contentWidth - 25f)
                    canvas.drawText(lines.firstOrNull() ?: "", x + 15f, y + 40f, paint)
                    
                    y += 55f
                }
                y += 15f
            }
        }

        // DTCs Section
        if (includeDtcs) {
            if (y > 750f) {
                page = createNewPage(document, page, ++pageNumber, footerPaint)
                canvas = page.canvas
                y = 60f
            }
            canvas.drawText("SISTEMAS ELECTRÓNICOS Y DTCs:", x, y, sectionPaint)
            y += 25f
            if (dtcs.isEmpty()) {
                paint.color = if (theme == "PRINTER_FRIENDLY") Color.BLACK else Color.parseColor("#007A63")
                canvas.drawText("✓ Análisis completo: No se detectaron códigos de falla activos en la ECU.", x, y, paint)
                y += 20f
            } else {
                paint.color = if (theme == "PRINTER_FRIENDLY") Color.BLACK else Color.parseColor("#D32F2F")
                dtcs.forEach { dtc ->
                    canvas.drawText("• ERROR $dtc: Código de diagnóstico automotriz", x, y, paint)
                    y += 20f
                }
            }
            y += 20f
        }

        // Maintenance Alerts Section
        if (maintenanceAlerts.isNotEmpty()) {
            if (y > 750f) {
                page = createNewPage(document, page, ++pageNumber, footerPaint)
                canvas = page.canvas
                y = 60f
            }
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
        if (includeAi && anomalies.isNotEmpty()) {
            if (y > 720f) {
                page = createNewPage(document, page, ++pageNumber, footerPaint)
                canvas = page.canvas
                y = 60f
            }
            canvas.drawText("ANOMALÍAS DETECTADAS (IA):", x, y, sectionPaint)
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
                canvas.drawLine(x, y - 5f, x + contentWidth, y - 5f, Paint().apply { color = cardBorder })
            }
            y += 20f
        }

        // Telemetry Graphs
        if (includeGraphs && telemetryHistory.isNotEmpty()) {
            if (y > 720f) {
                page = createNewPage(document, page, ++pageNumber, footerPaint)
                canvas = page.canvas
                y = 60f
            }
            canvas.drawText("ANÁLISIS DE GRÁFICAS DE TELEMETRÍA:", x, y, sectionPaint)
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
                drawWaveform(canvas, x, y, contentWidth, 110f, pid, data, insight = anomaly?.insight, themeName = themeName)
                y += 145f
            }
        }

        // AI Final Verdict Section
        if (includeAi && aiAnalysis != null) {
            if (y > 550f) {
                page = createNewPage(document, page, ++pageNumber, footerPaint)
                canvas = page.canvas
                y = 60f
            }
            drawAiSection(canvas, x, y, aiAnalysis, themeName)
        }
        
        // --- Save and Return ---
        document.finishPage(page)
        
        val directory = File(context.getExternalFilesDir(null), "Reports")
        if (!directory.exists()) directory.mkdirs()
        
        val fileName = "EV_Report_${trip.id}_${System.currentTimeMillis()}.pdf"
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
        newPage.canvas.drawText("Página $num | Elysium Vanguard Diagnostic Report", 250f, 820f, footerPaint)
        return newPage
    }

    private fun drawAiSection(canvas: Canvas, x: Float, startY: Float, text: String, themeName: String) {
        val theme = themeName.uppercase()
        val accentColor = when (theme) {
            "CARBON_RED" -> Color.parseColor("#FF3333")
            "CLASSIC_DARK" -> Color.parseColor("#FFB300")
            "PRINTER_FRIENDLY" -> Color.BLACK
            else -> Color.parseColor("#00FFCC")
        }
        val aiHeaderBg = if (theme == "PRINTER_FRIENDLY") Color.BLACK else when (theme) {
            "CARBON_RED" -> Color.parseColor("#2A0505")
            "CLASSIC_DARK" -> Color.parseColor("#2D1D00")
            else -> Color.parseColor("#001A1A")
        }
        val aiBoxColor = when (theme) {
            "CARBON_RED" -> Color.parseColor("#FFF0F0")
            "CLASSIC_DARK" -> Color.parseColor("#FFFDF0")
            "PRINTER_FRIENDLY" -> Color.WHITE
            else -> Color.parseColor("#F0FFFF")
        }
        val aiBoxBorder = when (theme) {
            "CARBON_RED" -> Color.parseColor("#FFCCCC")
            "CLASSIC_DARK" -> Color.parseColor("#FFEFA3")
            "PRINTER_FRIENDLY" -> Color.LTGRAY
            else -> Color.parseColor("#B2FFFF")
        }
        
        var y = startY
        val aiHeaderPaint = Paint().apply { color = aiHeaderBg }
        canvas.drawRect(x, y - 18f, x + 495f, y + 12f, aiHeaderPaint)
        
        val aiTitlePaint = Paint().apply {
            color = if (theme == "PRINTER_FRIENDLY") Color.WHITE else accentColor
            textSize = 12f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText("✨ CONCLUSIÓN Y RECOMENDACIONES (ELYSIUM AI PREDICTOR):", x + 10f, y, aiTitlePaint)
        y += 40f
        
        val textPaint = Paint().apply { color = Color.BLACK; textSize = 10f; isAntiAlias = true }
        val lines = wrapText(text, textPaint, 465f)
        val boxHeight = (lines.size * 18f) + 30f
        
        canvas.drawRoundRect(x, y - 10f, x + 495f, y + boxHeight - 10f, 10f, 10f, Paint().apply { color = aiBoxColor })
        canvas.drawRoundRect(x, y - 10f, x + 495f, y + boxHeight - 10f, 10f, 10f, Paint().apply { color = aiBoxBorder; style = Paint.Style.STROKE; strokeWidth = 1f })
        
        y += 15f
        lines.forEach { l ->
            canvas.drawText(l, x + 15f, y, textPaint)
            y += 18f
        }
    }

    private fun drawWaveform(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, label: String, data: List<Float>, insight: String? = null, themeName: String = "ELYSIUM_CYAN") {
        val theme = themeName.uppercase()
        val isAnomalous = insight != null
        
        val normalWaveColor = when (theme) {
            "CARBON_RED" -> Color.parseColor("#FF3333")
            "CLASSIC_DARK" -> Color.parseColor("#FFB300")
            "PRINTER_FRIENDLY" -> Color.BLACK
            else -> Color.parseColor("#00FFCC")
        }
        val normalFrameColor = when (theme) {
            "CARBON_RED" -> Color.parseColor("#FFF5F5")
            "CLASSIC_DARK" -> Color.parseColor("#FFFDF6")
            "PRINTER_FRIENDLY" -> Color.WHITE
            else -> Color.parseColor("#F5F5F7")
        }
        val normalBorderColor = when (theme) {
            "CARBON_RED" -> Color.parseColor("#FFD6D6")
            "CLASSIC_DARK" -> Color.parseColor("#FFE8A3")
            "PRINTER_FRIENDLY" -> Color.LTGRAY
            else -> Color.parseColor("#EEEEEE")
        }
        
        val framePaint = Paint().apply {
            color = if (isAnomalous) Color.parseColor("#FFF0F0") else normalFrameColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(x, y, x + width, y + height, 8f, 8f, framePaint)
        
        val borderPaint = Paint().apply {
            color = if (isAnomalous) Color.parseColor("#FF3366") else normalBorderColor
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }
        canvas.drawRoundRect(x, y, x + width, y + height, 8f, 8f, borderPaint)
        
        if (isAnomalous) {
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
        val mainColor = if (isAnomalous) Color.parseColor("#FF3366") else normalWaveColor
        
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
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Diagnóstico Elysium Vanguard — ${pdfFile.nameWithoutExtension}")
            putExtra(Intent.EXTRA_TEXT, "Adjunto el reporte de diagnóstico generado por Elysium Vanguard AI.")
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
        val file = File(dir, "EV_session_${sessionId}_data.csv")
        file.writeText(csv.toString())
        return file
    }

    fun generateVehicleHistoryReport(
        vehicle: com.elysium369.meet.data.local.entities.VehicleEntity,
        maintenanceLogs: List<com.elysium369.meet.data.local.entities.MaintenanceLogEntity>,
        repairs: List<com.elysium369.meet.data.local.entities.RepairHistoryEntity>,
        themeName: String = "ELYSIUM_CYAN",
        includeMaint: Boolean = true,
        includeRepairs: Boolean = true,
        includeSummary: Boolean = true,
        includeBranding: Boolean = true,
        includeExpert: Boolean = false,
        expertProcedures: List<com.elysium369.meet.core.obd.ExpertDiagnosticProcedure> = emptyList()
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var pageNumber = 1
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        // --- Theme Color Mapping ---
        val theme = themeName.uppercase()
        val headerBg = if (theme == "PRINTER_FRIENDLY") Color.WHITE else when (theme) {
            "CARBON_RED" -> Color.parseColor("#1A0505")
            "CLASSIC_DARK" -> Color.parseColor("#1C1A17")
            else -> Color.parseColor("#0A0A0A")
        }
        val headerTextColor = if (theme == "PRINTER_FRIENDLY") Color.BLACK else Color.WHITE
        val accentColor = when (theme) {
            "CARBON_RED" -> Color.parseColor("#FF3333")
            "CLASSIC_DARK" -> Color.parseColor("#FFB300")
            "PRINTER_FRIENDLY" -> Color.BLACK
            else -> Color.parseColor("#00FFCC")
        }
        val sectionTitleColor = when (theme) {
            "CARBON_RED" -> Color.parseColor("#2A1212")
            "CLASSIC_DARK" -> Color.parseColor("#2A2722")
            "PRINTER_FRIENDLY" -> Color.BLACK
            else -> Color.parseColor("#1A1A1A")
        }
        val cardBg = when (theme) {
            "CARBON_RED" -> Color.parseColor("#FFF5F5")
            "CLASSIC_DARK" -> Color.parseColor("#FFFDF6")
            "PRINTER_FRIENDLY" -> Color.WHITE
            else -> Color.parseColor("#F9F9F9")
        }
        val cardBorder = when (theme) {
            "CARBON_RED" -> Color.parseColor("#FFD6D6")
            "CLASSIC_DARK" -> Color.parseColor("#FFE8A3")
            "PRINTER_FRIENDLY" -> Color.LTGRAY
            else -> Color.parseColor("#EEEEEE")
        }

        val paint = Paint().apply {
            color = Color.BLACK; textSize = 10f; isAntiAlias = true
        }
        val titlePaint = Paint().apply {
            color = accentColor; textSize = 24f; isFakeBoldText = true; isAntiAlias = true
        }
        val sectionPaint = Paint().apply {
            color = sectionTitleColor; textSize = 14f; isFakeBoldText = true; isAntiAlias = true
        }
        val footerPaint = Paint().apply {
            color = Color.GRAY; textSize = 8f; isAntiAlias = true
        }

        var y = 0f
        val x = 50f
        val contentWidth = 495f

        // --- Header Section ---
        val prefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
        val workshopName = prefs.getString("workshop_name", "")?.takeIf { it.isNotBlank() }
        val workshopAddress = prefs.getString("workshop_address", "") ?: ""
        val workshopPhone = prefs.getString("workshop_phone", "") ?: ""
        val workshopEmail = prefs.getString("workshop_email", "") ?: ""

        val headerHeight = 115f
        canvas.drawRect(0f, 0f, 595f, headerHeight, Paint().apply { color = headerBg })
        if (theme == "PRINTER_FRIENDLY") {
            canvas.drawLine(0f, headerHeight, 595f, headerHeight, Paint().apply { color = Color.BLACK; strokeWidth = 1f })
        }
        
        y = 45f
        if (includeBranding && workshopName != null) {
            titlePaint.color = headerTextColor
            canvas.drawText(workshopName.uppercase(), x, y, titlePaint)
            
            paint.color = if (theme == "PRINTER_FRIENDLY") Color.DKGRAY else Color.LTGRAY
            paint.textSize = 9f
            paint.isFakeBoldText = false
            var subY = y + 15f
            if (workshopAddress.isNotBlank()) {
                canvas.drawText(workshopAddress, x, subY, paint)
                subY += 12f
            }
            if (workshopPhone.isNotBlank() || workshopEmail.isNotBlank()) {
                val separator = if (workshopPhone.isNotBlank() && workshopEmail.isNotBlank()) "  |  " else ""
                canvas.drawText("$workshopPhone$separator$workshopEmail", x, subY, paint)
            }
            
            paint.color = accentColor
            paint.textSize = 8f
            paint.isFakeBoldText = true
            canvas.drawText("POWERED BY ELYSIUM VANGUARD", 335f, 25f, paint)
        } else {
            titlePaint.color = accentColor
            canvas.drawText("MEET CLINIC", x, y, titlePaint)
            titlePaint.textSize = 12f
            titlePaint.color = headerTextColor
            canvas.drawText("HISTORIAL DE SALUD VEHICULAR", x + 165f, y - 4f, titlePaint)
        }
        
        y = headerHeight - 15f
        paint.color = accentColor
        paint.textSize = 10f
        paint.isFakeBoldText = true
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        canvas.drawText("HISTORIAL CLÍNICO DEL VEHÍCULO - ${sdf.format(Date())}", x, y, paint)

        // --- Body Content ---
        y = headerHeight + 40f
        paint.color = Color.BLACK
        paint.textSize = 11f
        paint.isFakeBoldText = true
        canvas.drawText("DETALLES DEL VEHÍCULO:", x, y, paint)
        y += 20f
        paint.isFakeBoldText = false
        canvas.drawText("Vehículo: ${vehicle.make} ${vehicle.model} ${vehicle.year}", x, y, paint)
        y += 15f
        canvas.drawText("Placa/VIN: ${vehicle.plate} / ${vehicle.vin}", x, y, paint)
        y += 15f
        canvas.drawText("Motor: ${vehicle.engine} ${vehicle.displacementCc}cc", x, y, paint)
        y += 15f
        canvas.drawText("Odómetro Actual: ${vehicle.odometerKm} km", x, y, paint)

        y += 40f

        // Cost Summary
        val totalMaintenanceCost = maintenanceLogs.sumOf { it.cost.toDouble() }
        val totalRepairCost = repairs.sumOf { it.totalCost.toDouble() }
        val grandTotal = totalMaintenanceCost + totalRepairCost

        if (includeSummary) {
            canvas.drawText("RESUMEN DE INVERSIÓN (HISTÓRICO)", x, y, sectionPaint)
            y += 25f
            
            canvas.drawRect(x, y, x + contentWidth, y + 60f, Paint().apply { color = cardBg })
            canvas.drawRect(x, y, x + contentWidth, y + 60f, Paint().apply { color = cardBorder; style = Paint.Style.STROKE; strokeWidth = 1f })
            
            val gridY = y + 25f
            paint.isFakeBoldText = true
            canvas.drawText("MANTENIMIENTOS", x + 20, gridY, paint)
            canvas.drawText("REPARACIONES", x + 180, gridY, paint)
            canvas.drawText("INVERSIÓN TOTAL", x + 340, gridY, paint)
            
            paint.isFakeBoldText = false
            paint.textSize = 14f
            paint.color = if (theme == "PRINTER_FRIENDLY") Color.BLACK else Color.parseColor("#007A63")
            canvas.drawText("$${String.format("%.2f", totalMaintenanceCost)}", x + 20, gridY + 20f, paint)
            paint.color = if (theme == "PRINTER_FRIENDLY") Color.BLACK else Color.parseColor("#D32F2F")
            canvas.drawText("$${String.format("%.2f", totalRepairCost)}", x + 180, gridY + 20f, paint)
            paint.color = Color.BLACK
            paint.isFakeBoldText = true
            canvas.drawText("$${String.format("%.2f", grandTotal)}", x + 340, gridY + 20f, paint)

            y += 90f
        }

        // Maintenance Logs Table
        if (includeMaint) {
            canvas.drawText("HISTORIAL DE MANTENIMIENTOS PREVENTIVOS:", x, y, sectionPaint)
            y += 25f
            
            if (maintenanceLogs.isEmpty()) {
                paint.isFakeBoldText = false; paint.textSize = 10f; paint.color = Color.DKGRAY
                canvas.drawText("No hay mantenimientos registrados.", x, y, paint)
                y += 20f
            } else {
                val tableTextPaint = Paint().apply { color = Color.WHITE; textSize = 9f; isFakeBoldText = true; isAntiAlias = true }
                canvas.drawRect(x, y - 12f, x + contentWidth, y + 8f, Paint().apply { color = Color.parseColor("#1A1A1A") })
                canvas.drawText("FECHA", x + 5f, y, tableTextPaint)
                canvas.drawText("ODÓMETRO", x + 70f, y, tableTextPaint)
                canvas.drawText("CATEGORÍA / DESCRIPCIÓN", x + 140f, y, tableTextPaint)
                canvas.drawText("COSTO", x + 440f, y, tableTextPaint)
                
                y += 20f
                tableTextPaint.color = Color.BLACK; tableTextPaint.isFakeBoldText = false
                
                maintenanceLogs.sortedByDescending { it.datePerformed }.forEach { log ->
                    if (y > 780f) {
                        page = createNewPage(document, page, ++pageNumber, footerPaint)
                        canvas = page.canvas
                        y = 60f
                    }
                    canvas.drawText(SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(log.datePerformed)), x + 5f, y, tableTextPaint)
                    canvas.drawText("${log.odometerAtService} km", x + 70f, y, tableTextPaint)
                    canvas.drawText("${log.category} - ${log.description}", x + 140f, y, tableTextPaint)
                    canvas.drawText("$${log.cost}", x + 440f, y, tableTextPaint)
                    y += 15f
                    canvas.drawLine(x, y - 5f, x + contentWidth, y - 5f, Paint().apply { color = cardBorder })
                }
            }
            y += 20f
        }

        // Repairs History Table
        if (includeRepairs) {
            canvas.drawText("HISTORIAL DE REPARACIONES CORRECTIVAS:", x, y, sectionPaint)
            y += 25f

            if (repairs.isEmpty()) {
                paint.isFakeBoldText = false; paint.textSize = 10f; paint.color = Color.DKGRAY
                canvas.drawText("No hay reparaciones registradas.", x, y, paint)
            } else {
                val tableTextPaint = Paint().apply { color = Color.WHITE; textSize = 9f; isFakeBoldText = true; isAntiAlias = true }
                if (y > 750f) {
                    page = createNewPage(document, page, ++pageNumber, footerPaint)
                    canvas = page.canvas
                    y = 60f
                }
                canvas.drawRect(x, y - 12f, x + contentWidth, y + 8f, Paint().apply { color = Color.parseColor("#1A1A1A") })
                canvas.drawText("FECHA", x + 5f, y, tableTextPaint)
                canvas.drawText("ODÓMETRO", x + 70f, y, tableTextPaint)
                canvas.drawText("PIEZA DAÑADA / REPARACIÓN", x + 140f, y, tableTextPaint)
                canvas.drawText("COSTO", x + 440f, y, tableTextPaint)
                
                y += 20f
                tableTextPaint.color = Color.BLACK; tableTextPaint.isFakeBoldText = false
                
                repairs.sortedByDescending { it.datePerformed }.forEach { repair ->
                    if (y > 780f) {
                        page = createNewPage(document, page, ++pageNumber, footerPaint)
                        canvas = page.canvas
                        y = 60f
                    }
                    canvas.drawText(SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(repair.datePerformed)), x + 5f, y, tableTextPaint)
                    canvas.drawText("${repair.odometerAtRepair} km", x + 70f, y, tableTextPaint)
                    canvas.drawText("${repair.partName} (${repair.partCategory})", x + 140f, y, tableTextPaint)
                    canvas.drawText("$${repair.totalCost}", x + 440f, y, tableTextPaint)
                    y += 15f
                    canvas.drawLine(x, y - 5f, x + contentWidth, y - 5f, Paint().apply { color = cardBorder })
                }
            }
        }

        if (includeExpert && expertProcedures.isNotEmpty()) {
            page = createNewPage(document, page, ++pageNumber, footerPaint)
            canvas = page.canvas
            y = 60f

            canvas.drawText("DIAGNÓSTICO EXPERTO LOCAL (HEURÍSTICA OBD-II):", x, y, sectionPaint)
            y += 25f

            expertProcedures.forEach { proc ->
                val titleLines = wrapText(proc.title, paint.apply { textSize = 11f; isFakeBoldText = true }, contentWidth - 40f)
                val descLines = wrapText(proc.description, paint.apply { textSize = 9f; isFakeBoldText = false }, contentWidth - 40f)
                
                val causesTextOnly = proc.probableCauses.joinToString(", ")
                val causesLinesOnly = wrapText(causesTextOnly, paint.apply { textSize = 9f; isFakeBoldText = false }, contentWidth - 40f)
                
                var stepsLinesCount = 0
                val stepsTextOnly = proc.testSteps.joinToString("\n") { "- $it" }
                stepsTextOnly.split("\n").forEach { stepLine ->
                    stepsLinesCount += wrapText(stepLine, paint.apply { textSize = 9f; isFakeBoldText = false }, contentWidth - 40f).size
                }

                // Total height of the card
                val totalLinesHeight = (titleLines.size * 14f) + 15f + (descLines.size * 12f) + 10f + 12f + (causesLinesOnly.size * 12f) + 10f + 12f + (stepsLinesCount * 12f) + 15f

                if (y + totalLinesHeight > 780f) {
                    page = createNewPage(document, page, ++pageNumber, footerPaint)
                    canvas = page.canvas
                    y = 60f
                }

                val cardTop = y
                val cardBottom = y + totalLinesHeight
                
                val sevColor = when (proc.severity) {
                    com.elysium369.meet.core.obd.DiagnosticSeverity.CRITICAL -> Color.parseColor("#FF3333")
                    com.elysium369.meet.core.obd.DiagnosticSeverity.HIGH -> Color.parseColor("#E65100")
                    com.elysium369.meet.core.obd.DiagnosticSeverity.MODERATE -> Color.parseColor("#F57C00")
                    com.elysium369.meet.core.obd.DiagnosticSeverity.INFO -> Color.parseColor("#007A63")
                }

                canvas.drawRect(x, cardTop, x + contentWidth, cardBottom, Paint().apply { color = cardBg })
                canvas.drawRect(x, cardTop, x + contentWidth, cardBottom, Paint().apply { color = cardBorder; style = Paint.Style.STROKE; strokeWidth = 1f })
                canvas.drawRect(x, cardTop, x + 6f, cardBottom, Paint().apply { color = sevColor })

                var currentCardY = cardTop + 20f
                
                // Title
                paint.color = Color.BLACK
                paint.textSize = 11f
                paint.isFakeBoldText = true
                titleLines.forEach { line ->
                    canvas.drawText(line, x + 20f, currentCardY, paint)
                    currentCardY += 14f
                }

                // Severity
                paint.color = sevColor
                paint.textSize = 9f
                paint.isFakeBoldText = true
                canvas.drawText("SEVERIDAD: ${proc.severity.name}", x + 20f, currentCardY, paint)
                currentCardY += 15f

                // Description
                paint.color = Color.DKGRAY
                paint.textSize = 9f
                paint.isFakeBoldText = false
                descLines.forEach { line ->
                    canvas.drawText(line, x + 20f, currentCardY, paint)
                    currentCardY += 12f
                }
                currentCardY += 5f

                // Causes
                paint.color = Color.BLACK
                paint.isFakeBoldText = true
                canvas.drawText("Causas Probables:", x + 20f, currentCardY, paint)
                currentCardY += 12f
                paint.isFakeBoldText = false
                paint.color = Color.DKGRAY
                causesLinesOnly.forEach { line ->
                    canvas.drawText(line, x + 20f, currentCardY, paint)
                    currentCardY += 12f
                }
                currentCardY += 5f

                // Steps
                paint.color = Color.BLACK
                paint.isFakeBoldText = true
                canvas.drawText("Pasos de Prueba Recomendados:", x + 20f, currentCardY, paint)
                currentCardY += 12f
                paint.isFakeBoldText = false
                paint.color = Color.DKGRAY
                stepsTextOnly.split("\n").forEach { stepLine ->
                    val wrappedStepLines = wrapText(stepLine, paint, contentWidth - 40f)
                    wrappedStepLines.forEach { line ->
                        canvas.drawText(line, x + 20f, currentCardY, paint)
                        currentCardY += 12f
                    }
                }

                y = cardBottom + 20f
            }
        }

        document.finishPage(page)
        
        val directory = File(context.getExternalFilesDir(null), "Reports")
        if (!directory.exists()) directory.mkdirs()
        
        val fileName = "Historial_Clinico_${vehicle.vin}_${System.currentTimeMillis()}.pdf"
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

    fun generatePrePurchaseReport(
        result: com.elysium369.meet.core.obd.PrePurchaseInspection.InspectionResult,
        vin: String?,
        manufacturer: String,
        themeName: String = "ELYSIUM_CYAN"
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        var pageNumber = 1
        var page = document.startPage(pageInfo)
        var canvas: Canvas = page.canvas

        // --- Theme Color Mapping ---
        val theme = themeName.uppercase()
        val headerBg = if (theme == "PRINTER_FRIENDLY") Color.WHITE else when (theme) {
            "CARBON_RED" -> Color.parseColor("#1A0505")
            "CLASSIC_DARK" -> Color.parseColor("#1C1A17")
            else -> Color.parseColor("#0A0A0A")
        }
        val headerTextColor = if (theme == "PRINTER_FRIENDLY") Color.BLACK else Color.WHITE
        val accentColor = when (theme) {
            "CARBON_RED" -> Color.parseColor("#FF3333")
            "CLASSIC_DARK" -> Color.parseColor("#FFB300")
            "PRINTER_FRIENDLY" -> Color.BLACK
            else -> Color.parseColor("#00FFCC")
        }
        val sectionTitleColor = when (theme) {
            "CARBON_RED" -> Color.parseColor("#2A1212")
            "CLASSIC_DARK" -> Color.parseColor("#2A2722")
            "PRINTER_FRIENDLY" -> Color.BLACK
            else -> Color.parseColor("#1A1A1A")
        }
        val cardBg = when (theme) {
            "CARBON_RED" -> Color.parseColor("#FFF5F5")
            "CLASSIC_DARK" -> Color.parseColor("#FFFDF6")
            "PRINTER_FRIENDLY" -> Color.WHITE
            else -> Color.parseColor("#F9F9F9")
        }
        val cardBorder = when (theme) {
            "CARBON_RED" -> Color.parseColor("#FFD6D6")
            "CLASSIC_DARK" -> Color.parseColor("#FFE8A3")
            "PRINTER_FRIENDLY" -> Color.LTGRAY
            else -> Color.parseColor("#EEEEEE")
        }

        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            isAntiAlias = true
        }
        val titlePaint = Paint().apply {
            color = accentColor
            textSize = 24f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val sectionPaint = Paint().apply {
            color = sectionTitleColor
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
        val prefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
        val workshopName = prefs.getString("workshop_name", "")?.takeIf { it.isNotBlank() }
        val workshopAddress = prefs.getString("workshop_address", "") ?: ""
        val workshopPhone = prefs.getString("workshop_phone", "") ?: ""
        val workshopEmail = prefs.getString("workshop_email", "") ?: ""

        val headerHeight = 115f
        canvas.drawRect(0f, 0f, 595f, headerHeight, Paint().apply { color = headerBg })
        if (theme == "PRINTER_FRIENDLY") {
            canvas.drawLine(0f, headerHeight, 595f, headerHeight, Paint().apply { color = Color.BLACK; strokeWidth = 1f })
        }

        y = 45f
        if (workshopName != null) {
            titlePaint.color = headerTextColor
            canvas.drawText(workshopName.uppercase(), x, y, titlePaint)

            paint.color = if (theme == "PRINTER_FRIENDLY") Color.DKGRAY else Color.LTGRAY
            paint.textSize = 9f
            paint.isFakeBoldText = false
            var subY = y + 15f
            if (workshopAddress.isNotBlank()) {
                canvas.drawText(workshopAddress, x, subY, paint)
                subY += 12f
            }
            if (workshopPhone.isNotBlank() || workshopEmail.isNotBlank()) {
                val separator = if (workshopPhone.isNotBlank() && workshopEmail.isNotBlank()) "  |  " else ""
                canvas.drawText("$workshopPhone$separator$workshopEmail", x, subY, paint)
            }

            paint.color = accentColor
            paint.textSize = 8f
            paint.isFakeBoldText = true
            canvas.drawText("POWERED BY ELYSIUM VANGUARD", 335f, 25f, paint)
        } else {
            titlePaint.color = accentColor
            canvas.drawText("MEET CLINIC", x, y, titlePaint)
            titlePaint.textSize = 12f
            titlePaint.color = headerTextColor
            canvas.drawText("REPORTE DE INSPECCIÓN PRE-COMPRA", x + 165f, y - 4f, titlePaint)
        }

        y = headerHeight - 15f
        paint.color = accentColor
        paint.textSize = 10f
        paint.isFakeBoldText = true
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        canvas.drawText("CERTIFICADO DE INSPECCIÓN PRE-COMPRA - ${sdf.format(Date())}", x, y, paint)

        // Pre-purchase score gauge
        val scoreColor = when {
            result.overallScore > 80 -> if (theme == "PRINTER_FRIENDLY") Color.parseColor("#333333") else Color.parseColor("#00FFCC")
            result.overallScore > 55 -> if (theme == "PRINTER_FRIENDLY") Color.parseColor("#666666") else Color.parseColor("#FFD700")
            else -> if (theme == "PRINTER_FRIENDLY") Color.parseColor("#999999") else Color.parseColor("#FF3366")
        }

        canvas.drawArc(480f, 20f, 560f, 100f, 135f, 270f, false, Paint().apply {
            color = if (theme == "PRINTER_FRIENDLY") Color.LTGRAY else Color.parseColor("#333333")
            style = Paint.Style.STROKE
            strokeWidth = 6f
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        })

        canvas.drawArc(480f, 20f, 560f, 100f, 135f, 270f * (result.overallScore / 100f), false, Paint().apply {
            color = scoreColor
            style = Paint.Style.STROKE
            strokeWidth = 6f
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        })

        paint.color = headerTextColor
        paint.textSize = 20f
        paint.isFakeBoldText = true
        val scoreText = "${result.overallScore}"
        canvas.drawText(scoreText, 520f - (paint.measureText(scoreText) / 2f), 65f, paint)

        // --- Body Content ---
        y = headerHeight + 35f
        paint.color = Color.BLACK
        paint.textSize = 11f
        paint.isFakeBoldText = true
        canvas.drawText("INFORMACIÓN DEL VEHÍCULO:", x, y, paint)
        y += 18f
        paint.isFakeBoldText = false
        canvas.drawText("Fabricante / Marca: ${manufacturer.uppercase()}", x, y, paint)
        y += 15f
        canvas.drawText("Identificación VIN: ${vin ?: "No Detectado"}", x, y, paint)

        y += 30f

        // Veredicto Card
        val verdictColor = when (result.verdict) {
            com.elysium369.meet.core.obd.PrePurchaseInspection.Verdict.APPROVED -> if (theme == "PRINTER_FRIENDLY") Color.BLACK else Color.parseColor("#00FFCC")
            com.elysium369.meet.core.obd.PrePurchaseInspection.Verdict.CAUTION -> if (theme == "PRINTER_FRIENDLY") Color.DKGRAY else Color.parseColor("#FFD700")
            com.elysium369.meet.core.obd.PrePurchaseInspection.Verdict.REJECT -> if (theme == "PRINTER_FRIENDLY") Color.GRAY else Color.parseColor("#FF3366")
        }

        canvas.drawRoundRect(x, y, x + contentWidth, y + 65f, 8f, 8f, Paint().apply { color = cardBg })
        canvas.drawRoundRect(x, y, x + contentWidth, y + 65f, 8f, 8f, Paint().apply { color = cardBorder; style = Paint.Style.STROKE; strokeWidth = 1f })
        canvas.drawRect(x, y, x + 6f, y + 65f, Paint().apply { color = verdictColor })

        paint.color = verdictColor
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText(result.verdictText, x + 20f, y + 22f, paint)

        paint.color = Color.BLACK
        paint.textSize = 9.5f
        paint.isFakeBoldText = false
        val verdictLines = wrapText(result.verdictExplanation, paint, contentWidth - 40f)
        var tempY = y + 38f
        verdictLines.forEach { line ->
            canvas.drawText(line, x + 20f, tempY, paint)
            tempY += 13f
        }

        y += 85f

        // Banderas Rojas Section
        if (result.redFlags.isNotEmpty()) {
            if (y > 720f) {
                page = createNewPage(document, page, ++pageNumber, footerPaint)
                canvas = page.canvas
                y = 60f
            }

            canvas.drawText("ALERTAS CRÍTICAS Y RIESGOS (BANDERAS ROJAS):", x, y, sectionPaint)
            y += 20f

            result.redFlags.forEach { flag ->
                val lines = wrapText(flag, paint.apply { color = Color.parseColor("#D32F2F"); isFakeBoldText = true; textSize = 9f }, contentWidth - 20f)
                val flagHeight = lines.size * 13f + 10f
                if (y + flagHeight > 780f) {
                    page = createNewPage(document, page, ++pageNumber, footerPaint)
                    canvas = page.canvas
                    y = 60f
                }
                
                canvas.drawRoundRect(x, y, x + contentWidth, y + flagHeight, 4f, 4f, Paint().apply { color = Color.parseColor("#FFF5F5") })
                canvas.drawRoundRect(x, y, x + contentWidth, y + flagHeight, 4f, 4f, Paint().apply { color = Color.parseColor("#FFD6D6"); style = Paint.Style.STROKE; strokeWidth = 0.5f })
                canvas.drawRect(x, y, x + 4f, y + flagHeight, Paint().apply { color = Color.parseColor("#FF3366") })

                var lineY = y + 13f
                lines.forEach { line ->
                    canvas.drawText(line, x + 12f, lineY, paint)
                    lineY += 13f
                }
                y += flagHeight + 8f
            }
            y += 15f
        }

        // Categorías Section
        if (y > 700f) {
            page = createNewPage(document, page, ++pageNumber, footerPaint)
            canvas = page.canvas
            y = 60f
        }

        canvas.drawText("DESGLOSE DE SALUD POR SISTEMAS:", x, y, sectionPaint)
        y += 20f

        result.categories.forEach { category ->
            val findingsHeight = category.findings.size * 12f
            val cardHeight = 35f + findingsHeight + 10f

            if (y + cardHeight > 780f) {
                page = createNewPage(document, page, ++pageNumber, footerPaint)
                canvas = page.canvas
                y = 60f
            }

            canvas.drawRoundRect(x, y, x + contentWidth, y + cardHeight, 6f, 6f, Paint().apply { color = cardBg })
            canvas.drawRoundRect(x, y, x + contentWidth, y + cardHeight, 6f, 6f, Paint().apply { color = cardBorder; style = Paint.Style.STROKE; strokeWidth = 1f })

            // Icon and Name
            paint.color = Color.BLACK
            paint.textSize = 10f
            paint.isFakeBoldText = true
            canvas.drawText("${category.icon}  ${category.name.uppercase()}", x + 15f, y + 20f, paint)

            // Linear Progress Bar
            val barX = x + 240f
            val barWidth = 180f
            canvas.drawRoundRect(barX, y + 10f, barX + barWidth, y + 18f, 4f, 4f, Paint().apply { color = Color.parseColor("#E0E0E0") })
            
            val pct = category.score.toFloat() / category.maxScore.toFloat()
            val fillWidth = barWidth * pct
            val catColor = when (category.severity) {
                com.elysium369.meet.core.obd.DiagnosticSeverity.CRITICAL -> Color.parseColor("#FF3366")
                com.elysium369.meet.core.obd.DiagnosticSeverity.HIGH -> Color.parseColor("#FF5722")
                com.elysium369.meet.core.obd.DiagnosticSeverity.MODERATE -> Color.parseColor("#FFB300")
                com.elysium369.meet.core.obd.DiagnosticSeverity.INFO -> Color.parseColor("#00FFCC")
            }
            canvas.drawRoundRect(barX, y + 10f, barX + fillWidth, y + 18f, 4f, 4f, Paint().apply { color = catColor })

            // Score text representation
            paint.textSize = 8.5f
            paint.color = Color.DKGRAY
            paint.isFakeBoldText = false
            canvas.drawText("${category.score}/${category.maxScore}", barX + barWidth + 10f, y + 17f, paint)

            // Findings list
            var findingY = y + 34f
            paint.color = Color.BLACK
            paint.textSize = 9f
            category.findings.forEach { finding ->
                val lines = wrapText(finding, paint, contentWidth - 30f)
                lines.forEach { line ->
                    canvas.drawText(line, x + 15f, findingY, paint)
                    findingY += 12f
                }
            }

            y += cardHeight + 10f
        }

        y += 10f

        // Recommendations Section
        if (result.recommendations.isNotEmpty()) {
            if (y > 700f) {
                page = createNewPage(document, page, ++pageNumber, footerPaint)
                canvas = page.canvas
                y = 60f
            }

            canvas.drawText("RECOMENDACIONES CLINICAS DE MEET:", x, y, sectionPaint)
            y += 20f

            result.recommendations.forEach { rec ->
                if (y > 780f) {
                    page = createNewPage(document, page, ++pageNumber, footerPaint)
                    canvas = page.canvas
                    y = 60f
                }
                paint.color = Color.BLACK
                paint.textSize = 9.5f
                paint.isFakeBoldText = false
                val lines = wrapText("• $rec", paint, contentWidth - 20f)
                lines.forEach { line ->
                    canvas.drawText(line, x + 10f, y, paint)
                    y += 13f
                }
                y += 3f
            }
        }

        // --- Save and Return ---
        document.finishPage(page)

        val directory = File(context.getExternalFilesDir(null), "Reports")
        if (!directory.exists()) directory.mkdirs()

        val fileName = "Inspeccion_PreCompra_${vin ?: "TEMP"}_${System.currentTimeMillis()}.pdf"
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

    fun generateDvirReport(
        report: com.elysium369.meet.data.local.entities.DvirReportEntity,
        vehicleInfo: String
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            color = Color.parseColor("#00FFD4") // Elysium Cyan accent
            isAntiAlias = true
        }

        // Draw Header bar
        canvas.drawRect(0f, 0f, 595f, 90f, Paint().apply { color = Color.parseColor("#050B15") })
        canvas.drawRect(0f, 90f, 595f, 95f, headerPaint) // Cyan accent separator

        // Title
        paint.color = Color.WHITE
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("MEET INSPECCIÓN DIARIA DVIR", 30f, 50f, paint)
        
        paint.textSize = 10f
        paint.isFakeBoldText = false
        paint.color = Color.parseColor("#7A8BA5")
        canvas.drawText("REPORTE DE SEGURIDAD PRE-VIAJE", 30f, 75f, paint)

        // Workshop Branding Info
        paint.color = Color.WHITE
        paint.textSize = 9f
        canvas.drawText("ELYSIUM VANGUARD SYSTEMS", 420f, 40f, paint)
        canvas.drawText("OBD2 ADVANCED INTELLIGENCE", 420f, 55f, paint)
        canvas.drawText("Fecha: " + SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(report.timestamp)), 420f, 70f, paint)

        var y = 140f
        val x = 40f
        val contentWidth = 515f

        // Vehicle & Driver Details Card
        canvas.drawRoundRect(x, y, x + contentWidth, y + 80f, 8f, 8f, Paint().apply {
            color = Color.parseColor("#F9F9F9")
            style = Paint.Style.FILL
        })
        canvas.drawRoundRect(x, y, x + contentWidth, y + 80f, 8f, 8f, Paint().apply {
            color = Color.parseColor("#3300FFD4")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        })

        paint.color = Color.BLACK
        paint.textSize = 11f
        paint.isFakeBoldText = true
        canvas.drawText("DETALLES DE LA INSPECCIÓN", x + 15f, y + 25f, paint)

        paint.textSize = 10f
        paint.isFakeBoldText = false
        paint.color = Color.DKGRAY
        canvas.drawText("Vehículo: $vehicleInfo", x + 15f, y + 45f, paint)
        canvas.drawText("Operador: ${report.driverId}", x + 15f, y + 60f, paint)
        canvas.drawText("ID Inspección: ${report.id}", x + 300f, y + 45f, paint)

        y += 110f

        // Checklist Status Table Header
        paint.textSize = 12f
        paint.isFakeBoldText = true
        paint.color = Color.BLACK
        canvas.drawText("ESTADO DE COMPONENTES CRÍTICOS:", x, y, paint)
        y += 20f

        // Draw Checklist Grid
        val items = listOf(
            "Sistema de Frenos" to report.brakesOk,
            "Luces y Señaladores" to report.lightsOk,
            "Llantas y Neumáticos" to report.tiresOk,
            "Fluidos y Aceites" to report.fluidsOk,
            "Batería y Alternador" to report.batteryOk
        )

        items.forEach { (name, ok) ->
            canvas.drawRoundRect(x, y, x + contentWidth, y + 30f, 4f, 4f, Paint().apply {
                color = Color.parseColor("#F5F5F5")
            })
            paint.color = Color.BLACK
            paint.textSize = 10f
            paint.isFakeBoldText = false
            canvas.drawText(name, x + 15f, y + 18f, paint)

            // Status Indicator
            val statusColor = if (ok) Color.parseColor("#00C4A3") else Color.parseColor("#FF1744")
            val statusText = if (ok) "✔ APROBADO (OK)" else "✘ EN REVISIÓN (FALLA)"
            
            val statusPaint = Paint().apply {
                color = statusColor
                textSize = 9.5f
                isFakeBoldText = true
                isAntiAlias = true
            }
            canvas.drawText(statusText, x + 350f, y + 18f, statusPaint)

            y += 36f
        }

        y += 10f

        // Remarks Section
        paint.textSize = 11f
        paint.isFakeBoldText = true
        paint.color = Color.BLACK
        canvas.drawText("OBSERVACIONES / COMENTARIOS DEL CONDUCTOR:", x, y, paint)
        y += 15f

        canvas.drawRoundRect(x, y, x + contentWidth, y + 60f, 6f, 6f, Paint().apply {
            color = Color.parseColor("#FBFBFB")
            style = Paint.Style.FILL
        })
        canvas.drawRoundRect(x, y, x + contentWidth, y + 60f, 6f, 6f, Paint().apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 1f
        })

        paint.color = Color.BLACK
        paint.textSize = 9.5f
        paint.isFakeBoldText = false
        val remarksText = if (report.remarks.isNullOrEmpty()) {
            "Ninguna observación registrada. El vehículo se encuentra en condiciones óptimas para circular."
        } else {
            report.remarks
        }
        val lines = wrapText(remarksText, paint, contentWidth - 30f)
        var textY = y + 18f
        lines.forEach { line ->
            canvas.drawText(line, x + 15f, textY, paint)
            textY += 14f
        }

        y += 90f

        // Signature Section
        paint.textSize = 11f
        paint.isFakeBoldText = true
        canvas.drawText("FIRMA DIGITAL OPERADOR:", x, y, paint)
        y += 15f

        // Draw Signature Box
        val sigBoxHeight = 80f
        canvas.drawRoundRect(x, y, x + 250f, y + sigBoxHeight, 6f, 6f, Paint().apply {
            color = Color.parseColor("#FCFCFC")
            style = Paint.Style.FILL
        })
        canvas.drawRoundRect(x, y, x + 250f, y + sigBoxHeight, 6f, 6f, Paint().apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 1f
        })

        // Draw the saved signature Bitmap if path exists
        if (!report.signaturePath.isNullOrEmpty()) {
            val sigFile = File(report.signaturePath)
            if (sigFile.exists()) {
                val bitmap = android.graphics.BitmapFactory.decodeFile(report.signaturePath)
                if (bitmap != null) {
                    val srcRect = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
                    val dstRect = android.graphics.RectF(x + 10f, y + 10f, x + 240f, y + sigBoxHeight - 10f)
                    canvas.drawBitmap(bitmap, srcRect, dstRect, Paint().apply { isFilterBitmap = true })
                }
            }
        }

        paint.color = Color.DKGRAY
        paint.textSize = 8.5f
        paint.isFakeBoldText = false
        canvas.drawText("Certificado MEET Pre-Trip Checklist. Válido por 24 horas.", x, y + sigBoxHeight + 20f, paint)

        // Page footer
        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 8f
            isAntiAlias = true
        }
        canvas.drawText("Página 1 de 1", 500f, 810f, footerPaint)

        document.finishPage(page)

        val directory = File(context.getExternalFilesDir(null), "Reports")
        if (!directory.exists()) directory.mkdirs()

        val dvirFileName = "DVIR_Report_${report.id}.pdf"
        val dvirFile = File(directory, dvirFileName)

        try {
            document.writeTo(FileOutputStream(dvirFile))
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            document.close()
        }

        return dvirFile
    }
}
