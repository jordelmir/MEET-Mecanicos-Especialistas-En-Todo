package cr.dragon.calc.ui.document

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 🎨 LaTeXRenderer [KaTeX V10]
 *
 * Motor de renderizado matemático de élite para DragonDocs.
 * Usa KaTeX via WebView para calidad de publicación científica.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LaTeXRenderer(latexText: String, modifier: Modifier = Modifier) {
    val htmlContent = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css">
            <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"></script>
            <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/auto-render/auto-render.min.js"
                onload="renderMathInElement(document.body, {
                    delimiters: [
                        {left: '$$', right: '$$', display: true},
                        {left: '$', right: '$', display: false},
                        {left: '\\\\[', right: '\\\\]', display: true},
                        {left: '\\\\(', right: '\\\\)', display: false}
                    ]
                });"></script>
            <style>
                body {
                    background-color: transparent;
                    color: #FFFFFF;
                    font-family: 'Roboto', sans-serif;
                    font-size: 16px;
                    padding: 4px;
                    margin: 0;
                }
                .katex { font-size: 1.1em; color: #66FCF1; /* Dragon Cyan para la ciencia */ }
            </style>
        </head>
        <body>
            ${latexText.replace("\n", "<br>")}
        </body>
        </html>
    """.trimIndent()

    AndroidView(
        modifier = modifier.fillMaxWidth().wrapContentHeight(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
        }
    )
}
