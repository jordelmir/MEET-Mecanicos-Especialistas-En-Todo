package com.elysium.vanguard.features.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import com.elysium.vanguard.ui.theme.TitanColors
import com.elysium.vanguard.ui.theme.premiumGlass
import java.io.File

@Composable
fun IntegratedDocumentViewer(
    filePath: String,
    onBack: () -> Unit
) {
    val file = File(filePath)
    val fileName = file.name.lowercase()
    val extension = file.extension.lowercase()

    // Detect file type using multiple strategies
    val isDocxByExtension = extension == "docx"
    val isDocxByMagic = isDocxMagic(file)
    val isDocHeuristic = fileName.startsWith("doc-")
    val isDocx = isDocxByExtension || isDocxByMagic || isDocHeuristic

    val isCsv = extension == "csv" || fileName.endsWith(".csv")
    val isXlsx = extension == "xlsx" || fileName.endsWith(".xlsx")
    val isHtml = extension in listOf("html", "htm")
    val isTxt = extension == "txt"
    val isWebViewFriendly = isHtml || isTxt

    var content by remember { mutableStateOf("Loading Sovereign Viewer...") }

    LaunchedEffect(filePath) {
        content = when {
            isDocx -> {
                // Try DOCX parsing first (ZIP-based PK header files)
                val docxResult = extractTextFromDocx(file)
                if (docxResult.startsWith("VANGUARD ERROR") || docxResult.startsWith("VANGUARD: Empty")) {
                    // Fallback to raw text extraction
                    try {
                        file.readText().take(100000)
                    } catch (e: Exception) {
                        file.inputStream().use { input ->
                            input.readBytes().decodeToString()
                                .filter { c -> c.isLetterOrDigit() || c.isWhitespace() || c in ".,;:!?()-" }
                        }.take(50000)
                    }
                } else docxResult
            }
            isCsv -> {
                // CSV handled by WebView below, just load for fallback
                try { file.readText().take(100000) } catch (e: Exception) { "Error reading CSV: ${e.message}" }
            }
            isXlsx -> {
                val xlsxResult = extractTextFromXlsx(file)
                if (xlsxResult.startsWith("VANGUARD ERROR")) {
                    "Cannot parse XLSX natively: $xlsxResult"
                } else xlsxResult
            }
            isWebViewFriendly -> {
                // WebView will handle these
                ""
            }
            else -> {
                try {
                    file.readText().take(100000)
                } catch (e: Exception) {
                    try {
                        file.inputStream().use { input ->
                            input.readBytes().decodeToString()
                                .filter { c -> c.isLetterOrDigit() || c.isWhitespace() || c in ".,;:!?()-" }
                        }.take(50000)
                    } catch (e2: Exception) {
                        "VANGUARD ERROR: Cannot read file — ${e2.message}"
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.premiumGlass(cornerRadius = 12.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = file.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            isDocx -> "DOCX PARSER"
                            isXlsx -> "XLSX PARSER"
                            isCsv -> "CSV RENDERER"
                            isHtml -> "HTML ENGINE"
                            else -> "TEXT VIEWER"
                        },
                        color = TitanColors.NeonCyan.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        containerColor = TitanColors.AbsoluteBlack
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .premiumGlass(cornerRadius = 24.dp)
                .padding(8.dp)
        ) {
            when {
                isCsv -> {
                    // CSV: Render as HTML table in WebView
                    AndroidView(
                        factory = { ctx ->
                            android.webkit.WebView(ctx).apply {
                                settings.allowFileAccess = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                setBackgroundColor(0xFF111111.toInt())

                                val csvText = try { file.readText() } catch (e: Exception) { "Error: ${e.message}" }
                                val htmlContent = buildCsvHtml(csvText)
                                loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                isWebViewFriendly -> {
                    AndroidView(
                        factory = { ctx ->
                            android.webkit.WebView(ctx).apply {
                                settings.allowFileAccess = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                setBackgroundColor(0xFF111111.toInt())
                                loadUrl("file://${file.absolutePath}")
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    // Text-based rendering (DOCX parsed text, plain text, etc)
                    SelectionContainer {
                        Text(
                            text = content,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 20.sp,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun buildCsvHtml(csvText: String): String {
    val lines = csvText.split("\n").filter { it.isNotBlank() }
    if (lines.isEmpty()) return "<html><body style='color:white;background:#111;'><p>Empty CSV</p></body></html>"

    val sb = StringBuilder()
    sb.append("<html><head><style>")
    sb.append("body{background:#111;color:#eee;font-family:monospace;font-size:13px;margin:8px;}")
    sb.append("table{border-collapse:collapse;width:100%;}")
    sb.append("th{background:#1a1a2e;color:#00ffd5;padding:8px;border:1px solid #333;text-align:left;}")
    sb.append("td{padding:6px 8px;border:1px solid #222;}")
    sb.append("tr:nth-child(even){background:#1a1a1a;}")
    sb.append("tr:hover{background:#222;}")
    sb.append("</style></head><body><table>")

    for ((i, line) in lines.withIndex()) {
        val cells = line.split(",")
        sb.append("<tr>")
        for (cell in cells) {
            val tag = if (i == 0) "th" else "td"
            sb.append("<$tag>${cell.trim()}</$tag>")
        }
        sb.append("</tr>")
    }
    sb.append("</table></body></html>")
    return sb.toString()
}

private fun extractTextFromDocx(file: File): String {
    return try {
        val sb = StringBuilder()
        java.util.zip.ZipInputStream(file.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zis.readBytes().decodeToString()
                    // Extract text between <w:t> tags for better accuracy
                    val textPattern = Regex("<w:t[^>]*>([^<]*)</w:t>")
                    val matches = textPattern.findAll(xml)
                    for (match in matches) {
                        sb.append(match.groupValues[1])
                    }
                    // If regex didn't find anything, fallback to tag stripping
                    if (sb.isEmpty()) {
                        val text = xml.replace(Regex("<[^>]*>"), " ")
                            .replace(Regex("\\s+"), " ")
                            .trim()
                        sb.append(text)
                    }
                    break
                }
                entry = zis.nextEntry
            }
        }
        if (sb.isEmpty()) "VANGUARD: Empty DOCX or corrupted structure." else sb.toString()
    } catch (e: Exception) {
        "VANGUARD ERROR: Sovereign parsing failed - ${e.message}"
    }
}

private fun extractTextFromXlsx(file: File): String {
    return try {
        val sharedStrings = mutableListOf<String>()
        val result = StringBuilder()

        // Phase 1: Extract shared strings
        java.util.zip.ZipInputStream(file.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "xl/sharedStrings.xml") {
                    val xml = zis.readBytes().decodeToString()
                    Regex("<t[^>]*>([^<]*)</t>").findAll(xml).forEach {
                        sharedStrings.add(it.groupValues[1])
                    }
                }
                entry = zis.nextEntry
            }
        }

        // Phase 2: Extract cell data from sheets
        java.util.zip.ZipInputStream(file.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.startsWith("xl/worksheets/sheet")) {
                    val xml = zis.readBytes().decodeToString()
                    // This is a very basic parser: it finds <v> tags and <c t="s"> for shared strings
                    val cellMatches = Regex("<c[^>]*>.*?<v>([^<]*)</v>.*?</c>").findAll(xml)
                    for (match in cellMatches) {
                        val value = match.groupValues[1]
                        val isShared = match.value.contains("t=\"s\"")
                        if (isShared) {
                            val idx = value.toIntOrNull()
                            if (idx != null && idx < sharedStrings.size) {
                                result.append(sharedStrings[idx]).append("\t")
                            }
                        } else {
                            result.append(value).append("\t")
                        }
                    }
                    result.append("\n")
                }
                entry = zis.nextEntry
            }
        }
        if (result.isEmpty()) "VANGUARD: No extraction possible for this XLSX." else result.toString()
    } catch (e: Exception) {
        "VANGUARD ERROR: XLSX parsing failed - ${e.message}"
    }
}

private fun isDocxMagic(file: File): Boolean {
    return try {
        file.inputStream().use {
            val bytes = ByteArray(4)
            val read = it.read(bytes)
            // PK header = ZIP-based file (DOCX, XLSX, PPTX, etc.)
            read == 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
                bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
        }
    } catch (e: Exception) { false }
}
