package com.elysium369.meet.core.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.elysium369.meet.core.obd.VehicleInspectionReport
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class PeritoReportGenerator(private val context: Context) {

    fun generateReportPdf(
        report: VehicleInspectionReport,
        make: String,
        model: String,
        year: Int,
        odometer: Long
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // Palette setup (MEET premium colors)
        val primaryColor = Color.parseColor("#0A0A0A") // Deep black
        val accentNeonGreen = Color.parseColor("#00FFD4")
        val accentCyan = Color.parseColor("#00E5FF")
        val warningColor = Color.parseColor("#FFD700")
        val errorColor = Color.parseColor("#FF1744")
        
        val excelColor = Color.parseColor("#00FFD4")
        val goodColor = Color.parseColor("#00E5FF")
        val attentionColor = Color.parseColor("#FFD700")
        val riskColor = Color.parseColor("#FF1744")

        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
        }

        // 1. Draw Header
        canvas.drawRect(0f, 0f, 595f, 100f, Paint().apply {
            color = primaryColor
            isAntiAlias = true
        })

        // Top divider line
        canvas.drawRect(0f, 100f, 595f, 104f, Paint().apply {
            color = accentCyan
            isAntiAlias = true
        })

        // Branding
        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 22f
        canvas.drawText("MEET PERITO", 35f, 45f, paint)

        paint.textSize = 10f
        paint.color = accentNeonGreen
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        canvas.drawText("USED CAR CLINICAL EVALUATION SYSTEM [LATAM ELITE]", 35f, 62f, paint)

        // Date and Time
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val dateStr = sdf.format(Date(report.createdAt))
        paint.color = Color.WHITE
        paint.textSize = 10f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("FECHA: $dateStr", 400f, 42f, paint)
        canvas.drawText("REPORTE ID: #${report.inspectionId.take(8).uppercase()}", 400f, 58f, paint)

        // 2. Draw Main Vehicle Metadata Table
        var y = 135f
        paint.color = Color.parseColor("#F5F5F5")
        canvas.drawRoundRect(RectF(35f, y, 560f, y + 65f), 8f, 8f, paint)
        
        paint.color = Color.BLACK
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("VEHÍCULO:", 50f, y + 25f, paint)
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("$make $model ($year)", 130f, y + 25f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Nº VIN:", 50f, y + 45f, paint)
        paint.typeface = Typeface.DEFAULT
        canvas.drawText(report.vin, 130f, y + 45f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("KM TABLERO:", 320f, y + 25f, paint)
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("${odometer} KM", 420f, y + 25f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("COSTO REPARACIÓN:", 320f, y + 45f, paint)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = if (report.estimatedRepairCost > 0) errorColor else excelColor
        canvas.drawText("$${report.estimatedRepairCost} USD", 450f, y + 45f, paint)

        // 3. Draw Score Gauge (Circular Dial)
        y = 220f
        val cx = 130f
        val cy = y + 70f
        val radius = 55f
        
        // Background gauge arc (180 to 360 degrees)
        val rectF = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val arcPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeCap = Paint.Cap.ROUND
        }
        
        // Draw colored sectors of the gauge arc
        // 0-59 (High Risk): red
        arcPaint.color = Color.parseColor("#E0E0E0") // base track
        canvas.drawArc(rectF, 180f, 180f, false, arcPaint)
        
        // Active progress arc based on score
        val scorePercent = report.score0to100 / 100f
        val sweepAngle = scorePercent * 180f
        val scoreColor = when (report.score0to100) {
            in 90..100 -> excelColor
            in 80..89 -> goodColor
            in 60..79 -> attentionColor
            else -> riskColor
        }
        arcPaint.color = scoreColor
        canvas.drawArc(rectF, 180f, sweepAngle, false, arcPaint)
        
        // Needle drawing
        val needleLen = radius - 8f
        val angleRad = Math.toRadians((180f + sweepAngle).toDouble())
        val needleX = cx + needleLen * cos(angleRad).toFloat()
        val needleY = cy + needleLen * sin(angleRad).toFloat()
        val needlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.DKGRAY
            strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(cx, cy, needleX, needleY, needlePaint)
        canvas.drawCircle(cx, cy, 6f, Paint().apply { color = Color.BLACK; isAntiAlias = true })

        // Score text in the center
        paint.color = Color.BLACK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 24f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("${report.score0to100}", cx, cy + 28f, paint)
        paint.textSize = 9f
        paint.color = Color.GRAY
        canvas.drawText("PUNTAJE", cx, cy + 38f, paint)
        paint.textAlign = Paint.Align.LEFT

        // Draw Score classification banner next to the gauge
        paint.color = Color.BLACK
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("ESTADO: ${report.category.uppercase()}", 220f, y + 25f, paint)

        // Draw recommendation
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9f
        paint.color = Color.DKGRAY
        
        val recLines = splitText(report.recommendation, 55)
        var recY = y + 42f
        for (line in recLines) {
            canvas.drawText(line, 220f, recY, paint)
            recY += 12f
        }

        // 4. Draw 10 Dimensions Check Results
        y = 350f
        paint.color = primaryColor
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("DIMENSIONES ANALIZADAS (CHECKLIST OBD2 CLINIC)", 35f, y, paint)
        
        canvas.drawLine(35f, y + 5f, 560f, y + 5f, Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        })

        var dimY = y + 22f
        val colWidth = 250f
        val dims = report.dimensionsDetails.toList()
        for (i in 0 until 10) {
            val isSecondCol = i >= 5
            val posX = if (isSecondCol) 310f else 35f
            val posY = if (isSecondCol) dimY - (5 * 22f) else dimY
            
            if (i < dims.size) {
                val dim = dims[i]
                
                // Format name
                val nameFormatted = when (dim.first) {
                    "VIN" -> "1. Identificación VIN"
                    "DTC_ACTIVOS" -> "2. Códigos DTC Activos"
                    "DTC_PENDIENTES" -> "3. Códigos DTC Pendientes"
                    "FREEZE_FRAME" -> "4. Registro Freeze Frame"
                    "FUEL_TRIMS" -> "5. Ajustes de Mezcla (FT)"
                    "TEMPERATURA" -> "6. Sistema Térmico (ECT)"
                    "VOLTAJE" -> "7. Voltaje Alternador"
                    "SENSORES" -> "8. Sensores Críticos"
                    "KILOMETRAJE" -> "9. Consistencia Km OBD"
                    "ESTADO_GENERAL" -> "10. Preparación OBD2"
                    else -> dim.first
                }
                
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 9.5f
                paint.color = Color.BLACK
                canvas.drawText(nameFormatted, posX, posY, paint)
                
                // Draw details text
                paint.typeface = Typeface.DEFAULT
                paint.textSize = 8.5f
                paint.color = Color.DKGRAY
                val textLines = splitText(dim.second, 48)
                var dy = posY + 10f
                for (line in textLines) {
                    canvas.drawText(line, posX + 8f, dy, paint)
                    dy += 10f
                }
            }
            dimY += 22f
        }

        // 5. Draw Critical Issues & Warnings
        y = 515f
        paint.color = primaryColor
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("FALLAS DETECTADAS Y ADVERTENCIAS CLINICAS", 35f, y, paint)
        
        canvas.drawLine(35f, y + 5f, 560f, y + 5f, Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        })

        var issueY = y + 20f
        
        // Critical Issues
        if (report.criticalIssues.isEmpty() && report.warnings.isEmpty()) {
            paint.color = Color.parseColor("#00E676")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 10f
            canvas.drawText("✅ ¡EXCELENTE! No se encontraron problemas críticos ni alertas.", 45f, issueY, paint)
        } else {
            // Draw Criticals
            paint.textSize = 9f
            for (issue in report.criticalIssues) {
                if (issueY > 800f) break
                paint.color = riskColor
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("● CRÍTICO:", 45f, issueY, paint)
                
                paint.color = Color.BLACK
                paint.typeface = Typeface.DEFAULT
                val lines = splitText(issue, 85)
                var first = true
                for (line in lines) {
                    if (first) {
                        canvas.drawText(line, 105f, issueY, paint)
                        first = false
                    } else {
                        issueY += 11f
                        canvas.drawText(line, 105f, issueY, paint)
                    }
                }
                issueY += 14f
            }
            
            // Draw Warnings
            for (warn in report.warnings) {
                if (issueY > 800f) break
                paint.color = attentionColor
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("● ALERTA:", 45f, issueY, paint)
                
                paint.color = Color.BLACK
                paint.typeface = Typeface.DEFAULT
                val lines = splitText(warn, 85)
                var first = true
                for (line in lines) {
                    if (first) {
                        canvas.drawText(line, 105f, issueY, paint)
                        first = false
                    } else {
                        issueY += 11f
                        canvas.drawText(line, 105f, issueY, paint)
                    }
                }
                issueY += 14f
            }
        }

        // Draw Footer on A4 page
        canvas.drawRect(0f, 815f, 595f, 842f, Paint().apply {
            color = primaryColor
            isAntiAlias = true
        })
        paint.color = Color.WHITE
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        canvas.drawText("MEET ELITE PERITO VEHICLE REPORT CERTIFICATION SYSTEM", 35f, 831f, paint)
        canvas.drawText("PÁGINA 1 DE 1", 500f, 831f, paint)

        document.finishPage(page)

        // Write PDF to a file
        val dir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "PeritoReports")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val pdfFile = File(dir, "Reporte_Perito_${report.inspectionId.take(8).uppercase()}.pdf")
        document.writeTo(FileOutputStream(pdfFile))
        document.close()

        return pdfFile
    }

    private fun splitText(text: String, limit: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""
        for (word in words) {
            if (currentLine.length + word.length + 1 > limit) {
                lines.add(currentLine)
                currentLine = word
            } else {
                currentLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }
        return lines
    }
}
