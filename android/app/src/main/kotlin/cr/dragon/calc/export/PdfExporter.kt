package cr.dragon.calc.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import cr.dragon.calc.graphing.GraphViewport
import cr.dragon.calc.ui.document.Cell
import cr.dragon.calc.ui.document.CellType
import dragoncore.evaluator.DragonContext
import dragoncore.evaluator.DragonEvaluator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfExporter {

    suspend fun generatePdf(
        context: Context,
        cells: List<Cell>,
        bitmaps: Map<String, Bitmap> = emptyMap()
    ): File = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        val evaluator = DragonEvaluator()
        val dragonContext = DragonContext()
        val latexRenderer = VectorLatexRenderer(Paint().apply { isAntiAlias = true })

        val pageWidth = 595
        val pageHeight = 842
        var pageNum = 1

        val colors = object {
            val cyan = Color.parseColor("#00E5FF")
            val green = Color.parseColor("#00FF88")
            val background = Color.parseColor("#0A0A0A")
            val text = Color.parseColor("#EEEEEE")
            val grey = Color.parseColor("#888888")
        }

        fun startNewPage(): PdfDocument.Page {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum++).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            // Draw Sidebar accent
            val sidebarPaint = Paint().apply { color = colors.cyan }
            canvas.drawRect(0f, 0f, 5f, pageHeight.toFloat(), sidebarPaint)

            // Draw Header strip
            val headerPaint = Paint().apply { color = Color.parseColor("#111111") }
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 40f, headerPaint)

            val titlePaint = Paint().apply {
                color = Color.WHITE
                textSize = 10f
                isFakeBoldText = true
            }
            canvas.drawText("ELYSIUM VANGUARD // DRAGONDOCS", 20f, 25f, titlePaint)

            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            val datePaint = Paint().apply {
                color = colors.grey
                textSize = 8f
            }
            canvas.drawText("Exported on $dateStr - Page ${pageNum - 1}", pageWidth - 180f, 25f, datePaint)

            return page
        }

        var page = startNewPage()
        var canvas = page.canvas
        var yPos = 70f
        val margin = 40f
        val maxWidth = pageWidth - (margin * 2)

        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }

        for (cell in cells) {
            // Space check
            if (yPos > pageHeight - 60f) {
                document.finishPage(page)
                page = startNewPage()
                canvas = page.canvas
                yPos = 70f
            }

            when (cell.type) {
                CellType.MATH, CellType.PROMPT -> {
                    // Update context if it's an assignment or calculation
                    cell.ast?.let { expr ->
                        try { evaluator.evaluate(expr, dragonContext) } catch (e: Exception) {}
                    }

                    // Input
                    paint.color = colors.cyan
                    paint.isFakeBoldText = true
                    canvas.drawText("IN:", margin, yPos, paint)

                    paint.color = Color.BLACK
                    paint.isFakeBoldText = false

                    if (cell.ast != null) {
                        yPos += 10f
                        yPos += latexRenderer.drawExpr(canvas, cell.ast!!, margin + 40, yPos, 14f)
                    } else {
                        val exprStr = cell.content.text
                        val inputLines = wrapText(exprStr, paint, maxWidth - 40)
                        for (line in inputLines) {
                            canvas.drawText(line, margin + 40, yPos, paint)
                            yPos += 18f
                        }
                    }
                    yPos += 20f

                    // Output
                    if (cell.isError) {
                        paint.color = Color.RED
                        canvas.drawText("ERR:", margin, yPos, paint)
                        val errLines = wrapText(cell.result, paint, maxWidth - 40)
                        for (line in errLines) {
                            canvas.drawText(line, margin + 40, yPos, paint)
                            yPos += 18f
                        }
                    } else if (cell.result.isNotEmpty()) {
                        paint.color = colors.green
                        paint.isFakeBoldText = true
                        canvas.drawText("OUT:", margin, yPos, paint)

                        paint.color = Color.BLACK
                        paint.isFakeBoldText = false
                        val resLines = wrapText(cell.result, paint, maxWidth - 40)
                        for (line in resLines) {
                            canvas.drawText(line, margin + 40, yPos, paint)
                            yPos += 18f
                        }
                    }
                    yPos += 15f
                }
                CellType.GRAPH -> {
                    paint.color = Color.DKGRAY
                    canvas.drawText("FUNCTION GRAPH: ${cell.content.text}", margin, yPos, paint)
                    yPos += 10f

                    val graphHeight = 200f
                    if (yPos + graphHeight > pageHeight - 50f) {
                        document.finishPage(page)
                        page = startNewPage()
                        canvas = page.canvas
                        yPos = 70f
                    }

                    // Vector Core: Render directly to PDF Canvas
                    val viewport = GraphViewport() // Default view
                    val graphWidth = maxWidth

                    // Draw Grid
                    val gridPaint = Paint().apply {
                        color = Color.LTGRAY
                        strokeWidth = 0.5f
                        pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
                    }
                    canvas.drawRect(margin, yPos, margin + graphWidth, yPos + graphHeight, Paint().apply { color = Color.parseColor("#F9F9F9"); style = Paint.Style.FILL })
                    canvas.drawLine(margin + graphWidth/2, yPos, margin + graphWidth/2, yPos + graphHeight, gridPaint)
                    canvas.drawLine(margin, yPos + graphHeight/2, margin + graphWidth, yPos + graphHeight/2, gridPaint)

                    // Draw Function Path
                    val pathPaint = Paint().apply {
                        color = colors.cyan
                        style = Paint.Style.STROKE
                        strokeWidth = 2f
                        isAntiAlias = true
                    }

                    cell.ast?.let { expr ->
                        var isFirst = true
                        val androidPath = android.graphics.Path()

                        // Local sampling for PDF resolution
                        for (px in 0..graphWidth.toInt() step 2) {
                            val mathX = viewport.toMathX(px.toFloat(), graphWidth)
                            dragonContext.setVariable("x", mathX)
                            val mathY = try { evaluator.evaluate(expr, dragonContext) } catch (e: Exception) { Double.NaN }

                            if (mathY.isNaN() || mathY.isInfinite()) {
                                isFirst = true
                                continue
                            }

                            val py = viewport.toPixelY(mathY, graphHeight)
                            if (py < 0 || py > graphHeight) {
                                isFirst = true
                                continue
                            }

                            if (isFirst) {
                                androidPath.moveTo(margin + px, yPos + py)
                                isFirst = false
                            } else {
                                androidPath.lineTo(margin + px, yPos + py)
                            }
                        }
                        canvas.drawPath(androidPath, pathPaint)
                    }

                    yPos += graphHeight + 30f
                }
                CellType.GRAPH_3D -> {
                    // 3D still needs Bitmaps as vector projection is too complex for this sweep
                    val bitmap = bitmaps[cell.id.toString()]
                    if (bitmap != null) {
                        val targetWidth = maxWidth
                        val scale = targetWidth / bitmap.width.toFloat()
                        val targetHeight = bitmap.height * scale

                        if (yPos + targetHeight > pageHeight - 50f) {
                            document.finishPage(page)
                            page = startNewPage()
                            canvas = page.canvas
                            yPos = 70f
                        }

                        val rect = RectF(margin, yPos, margin + targetWidth, yPos + targetHeight)
                        canvas.drawBitmap(bitmap, null, rect, paint)
                        yPos += targetHeight + 20f
                    }
                }
                CellType.MARKDOWN -> {
                    paint.color = Color.parseColor("#444444")
                    paint.isFakeBoldText = false
                    val lines = wrapText(cell.content.text, paint, maxWidth)
                    for (line in lines) {
                        canvas.drawText(line, margin, yPos, paint)
                        yPos += 18f
                    }
                    yPos += 15f
                }
                CellType.PHYSICS -> {
                    paint.color = colors.cyan
                    paint.isFakeBoldText = true
                    canvas.drawText("PHYSICS SIMULATION:", margin, yPos, paint)
                    yPos += 20f

                    cell.ast?.let { expr ->
                        yPos += latexRenderer.drawExpr(canvas, expr, margin + 20, yPos, 12f)
                        yPos += 15f
                    }

                    // Render Snapshot of physics state if available
                    paint.color = Color.BLACK
                    paint.isFakeBoldText = false
                    cell.physicsState.forEach { (name, value) ->
                        canvas.drawText("$name = ${"%.4f".format(value)}", margin + 20, yPos, paint)
                        yPos += 15f
                    }
                    yPos += 10f
                }
                CellType.CHEMISTRY -> {
                    paint.color = colors.cyan
                    paint.isFakeBoldText = true
                    canvas.drawText("CHEMISTRY EQUATION:", margin, yPos, paint)
                    yPos += 20f

                    paint.color = Color.BLACK
                    paint.isFakeBoldText = false
                    val resLines = wrapText(cell.result, paint, maxWidth - 20)
                    for (line in resLines) {
                        canvas.drawText(line, margin + 20, yPos, paint)
                        yPos += 18f
                    }
                    yPos += 10f
                }
                CellType.STATISTICS, CellType.DATAFRAME -> {
                    paint.color = colors.cyan
                    paint.isFakeBoldText = true
                    canvas.drawText("DATA ANALYSIS SUMMARY:", margin, yPos, paint)
                    yPos += 20f

                    paint.color = Color.BLACK
                    paint.isFakeBoldText = false
                    val resLines = cell.result.lines()
                    for (line in resLines) {
                        canvas.drawText(line, margin + 20, yPos, paint)
                        yPos += 15f
                    }
                    yPos += 10f
                }
            }
        }

        document.finishPage(page)

        val outputFile = File(File(context.cacheDir, "DragonCalcExports").apply { mkdirs() }, "DragonDocs_Export_${System.currentTimeMillis()}.pdf")
        val out = FileOutputStream(outputFile)
        document.writeTo(out)
        document.close()
        out.close()

        outputFile
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        val words = text.split(" ")
        var currentLine = ""
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) > maxWidth) {
                lines.add(currentLine)
                currentLine = word
            } else {
                currentLine = testLine
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }
        return lines
    }
}
