package cr.dragon.calc.export

import android.content.Context
import cr.dragon.calc.ui.document.Cell
import cr.dragon.calc.ui.document.CellType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object HtmlExporter {

    suspend fun generateHtml(
        context: Context,
        title: String,
        cells: List<Cell>
    ): File = withContext(Dispatchers.IO) {
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

        val html = """
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>$title - DragonDocs Report</title>
                <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css">
                <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"></script>
                <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/contrib/auto-render.min.js"
                    onload="renderMathInElement(document.body);"></script>
                <style>
                    :root {
                        --dragon-cyan: #00E5FF;
                        --dragon-green: #00FF88;
                        --dragon-black: #050505;
                        --dragon-dark: #121212;
                        --white: #F5F5F7;
                    }
                    body {
                        background: var(--dragon-black);
                        color: var(--white);
                        font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        margin: 0;
                        padding: 40px 20px;
                        line-height: 1.6;
                    }
                    .container {
                        max-width: 900px;
                        margin: 0 auto;
                        padding: 30px;
                        background: var(--dragon-dark);
                        border-radius: 12px;
                        box-shadow: 0 10px 30px rgba(0,0,0,0.5);
                        border-top: 5px solid var(--dragon-cyan);
                    }
                    h1 {
                        color: var(--dragon-cyan);
                        font-size: 32px;
                        margin-bottom: 5px;
                        letter-spacing: -0.5px;
                    }
                    .meta {
                        color: #777;
                        font-size: 13px;
                        margin-bottom: 40px;
                        font-style: italic;
                    }
                    .cell {
                        margin-bottom: 30px;
                        padding: 20px;
                        background: rgba(255,255,255,0.03);
                        border-radius: 10px;
                        border-left: 3px solid #333;
                        transition: border-color 0.3s;
                    }
                    .cell:hover { border-left-color: var(--dragon-cyan); }
                    .cell-type {
                        color: var(--dragon-green);
                        font-size: 11px;
                        font-weight: 800;
                        text-transform: uppercase;
                        margin-bottom: 12px;
                        letter-spacing: 1px;
                    }
                    .content {
                        font-family: 'JetBrains Mono', Consolas, monospace;
                        font-size: 15px;
                        white-space: pre-wrap;
                        color: #CCC;
                        background: #000;
                        padding: 12px;
                        border-radius: 6px;
                    }
                    .result {
                        margin-top: 15px;
                        padding-top: 15px;
                        border-top: 1px solid #222;
                        color: var(--white);
                        font-size: 16px;
                    }
                    .katex { font-size: 1.1em !important; }
                    .footer {
                        margin-top: 60px;
                        text-align: center;
                        font-size: 12px;
                        color: #444;
                        padding-top: 20px;
                        border-top: 1px solid #222;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>$title</h1>
                    <div class="meta">Generado por Elysium Vanguard Artificial Intelligence // $dateStr</div>

                    ${cells.joinToString("\n") { cell ->
                        val resultHtml = if (cell.result.isNotEmpty()) {
                            "<div class=\"result\">${cell.result.replace("\n", "<br>")}</div>"
                        } else ""

                        """
                        <div class="cell">
                            <div class="cell-type">${cell.type}</div>
                            <div class="content">${cell.content.text}</div>
                            $resultHtml
                        </div>
                        """
                    }}

                    <div class="footer">DragonDocs Scientific Engine V7.5 // Publication Grade Export</div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val safeName = title.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80)
        val file = File(File(context.cacheDir, "DragonCalcExports").apply { mkdirs() }, "$safeName.html")
        file.writeText(html)
        file
    }
}
