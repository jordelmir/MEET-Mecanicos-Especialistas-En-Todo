package com.elysium.vanguard.core.util

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

object ConversionEngine {

    // A4 dimensions in points (72 DPI)
    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842
    private const val MARGIN = 50f
    private const val LINE_HEIGHT = 16f
    private const val FONT_SIZE = 11f

    /**
     * Convert one or more images to a single PDF, each image on its own page.
     * Images are scaled to fit A4 while maintaining aspect ratio.
     */
    fun imagesToPdf(imageFiles: List<File>, outputFile: File): Result<File> {
        return try {
            val pdfDocument = PdfDocument()

            for ((index, file) in imageFiles.withIndex()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    ?: continue

                // Scale to fit A4 while keeping aspect ratio
                val scale = minOf(
                    A4_WIDTH.toFloat() / bitmap.width,
                    A4_HEIGHT.toFloat() / bitmap.height
                )
                val scaledWidth = (bitmap.width * scale).toInt()
                val scaledHeight = (bitmap.height * scale).toInt()

                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = page.canvas

                // Center the image on the page
                val left = (A4_WIDTH - scaledWidth) / 2
                val top = (A4_HEIGHT - scaledHeight) / 2
                val destRect = Rect(left, top, left + scaledWidth, top + scaledHeight)
                canvas.drawBitmap(bitmap, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG))

                pdfDocument.finishPage(page)
                bitmap.recycle()
            }

            FileOutputStream(outputFile).use { pdfDocument.writeTo(it) }
            pdfDocument.close()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Convert plain text to a multi-page PDF with proper line wrapping.
     */
    fun textToPdf(text: String, outputFile: File): Result<File> {
        return try {
            val pdfDocument = PdfDocument()
            val paint = Paint().apply {
                textSize = FONT_SIZE
                color = android.graphics.Color.BLACK
                isAntiAlias = true
            }

            val usableWidth = A4_WIDTH - (MARGIN * 2)
            val allLines = mutableListOf<String>()

            // Break text into lines that actually fit the page width
            for (paragraph in text.split("\n")) {
                if (paragraph.isBlank()) {
                    allLines.add("")
                    continue
                }
                var remaining = paragraph
                while (remaining.isNotEmpty()) {
                    val count = paint.breakText(remaining, true, usableWidth, null)
                    if (count <= 0) break
                    allLines.add(remaining.substring(0, count))
                    remaining = remaining.substring(count)
                }
            }

            // Calculate lines per page
            val linesPerPage = ((A4_HEIGHT - MARGIN * 2) / LINE_HEIGHT).toInt()
            val totalPages = maxOf(1, (allLines.size + linesPerPage - 1) / linesPerPage)

            for (pageNum in 0 until totalPages) {
                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNum + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = page.canvas

                var y = MARGIN + LINE_HEIGHT
                val startLine = pageNum * linesPerPage
                val endLine = minOf(startLine + linesPerPage, allLines.size)

                for (i in startLine until endLine) {
                    canvas.drawText(allLines[i], MARGIN, y, paint)
                    y += LINE_HEIGHT
                }

                pdfDocument.finishPage(page)
            }

            FileOutputStream(outputFile).use { pdfDocument.writeTo(it) }
            pdfDocument.close()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Convert a DOCX file to PDF by extracting text and rendering it.
     * Uses ZipInputStream to parse word/document.xml
     */
    fun docxToPdf(docxFile: File, outputFile: File): Result<File> {
        return try {
            val text = extractDocxText(docxFile)
            if (text.isBlank()) {
                return Result.failure(Exception("Empty DOCX or corrupted structure"))
            }
            textToPdf(text, outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Convert a text/code file to PDF.
     */
    fun fileToPdf(inputFile: File, outputFile: File): Result<File> {
        return try {
            val text = inputFile.readText()
            textToPdf(text, outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractDocxText(file: File): String {
        val sb = StringBuilder()
        java.util.zip.ZipInputStream(file.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zis.readBytes().decodeToString()
                    // Extract text from <w:t> tags
                    val textPattern = Regex("<w:t[^>]*>([^<]*)</w:t>")
                    val matches = textPattern.findAll(xml)
                    for (match in matches) {
                        sb.append(match.groupValues[1])
                    }
                    // Detect paragraph breaks for readability
                    if (sb.isNotEmpty()) {
                        val paragraphPattern = Regex("</w:p>")
                        val paragraphXml = xml.replace(paragraphPattern, "\n")
                        val textInParagraphs = Regex("<w:t[^>]*>([^<]*)</w:t>")
                            .findAll(paragraphXml)
                            .map { it.groupValues[1] }
                            .joinToString("")
                        if (textInParagraphs.isNotBlank()) {
                            sb.clear()
                            sb.append(textInParagraphs)
                        }
                    }
                    break
                }
                entry = zis.nextEntry
            }
        }
        return sb.toString()
    }
}
