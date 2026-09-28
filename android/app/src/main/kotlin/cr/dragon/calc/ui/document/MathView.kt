package cr.dragon.calc.ui.document

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Elysium Vanguard — MathView
 *
 * Un componente de Compose que renderiza LaTeX usando KaTeX dentro de un WebView.
 * Ofrece tipografía de libro de texto de alta calidad.
 */
@Composable
fun MathView(
    latex: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    fontSize: Int = 18
) {
    // Escapar backslashes para el JS de KaTeX
    val escapedLatex = latex.replace("\\", "\\\\").replace("'", "\\'")

    val html = """
        <!DOCTYPE html>
        <html>
        <head>
            <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/katex.min.css">
            <script src="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/katex.min.js"></script>
            <style>
                body {
                    background-color: transparent;
                    color: ${toHtmlColor(textColor)};
                    font-size: ${fontSize}px;
                    margin: 0;
                    padding: 4px;
                    display: flex;
                    justify-content: flex-start;
                    align-items: center;
                    overflow: hidden;
                }
                #math {
                    width: 100%;
                }
            </style>
        </head>
        <body>
            <div id="math"></div>
            <script>
                try {
                    katex.render('$escapedLatex', document.getElementById('math'), {
                        throwOnError: false,
                        displayMode: false
                    });
                } catch (e) {
                    document.getElementById('math').textContent = '$escapedLatex';
                }
            </script>
        </body>
        </html>
    """.trimIndent()

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(0) // Transparent
                settings.javaScriptEnabled = true
                webViewClient = WebViewClient()
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }
    )
}

private fun toHtmlColor(color: Color): String {
    val argb = color.toArgb()
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return "rgb($r, $g, $b)"
}
