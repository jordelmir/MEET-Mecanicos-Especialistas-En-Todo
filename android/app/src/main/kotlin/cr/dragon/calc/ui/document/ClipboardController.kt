package cr.dragon.calc.ui.document

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * ClipboardController: Controlador profesional para el portapapeles del sistema.
 * Sincroniza datos entre DragonCalc y Android OS.
 */
class ClipboardController(private val context: Context) {
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    /** Copia un texto al portapapeles con una etiqueta descriptiva */
    fun copy(text: String, label: String = "DragonCalc Formula") {
        val clip = ClipData.newPlainText(label, text)
        clipboardManager.setPrimaryClip(clip)
    }

    /** Recupera el texto del portapapeles si existe */
    fun paste(): String? {
        val clip = clipboardManager.primaryClip
        if (clip != null && clip.itemCount > 0) {
            return clip.getItemAt(0).text?.toString()
        }
        return null
    }

    /** Verifica si el portapapeles tiene contenido de texto */
    fun hasContent(): Boolean {
        return clipboardManager.hasPrimaryClip() &&
               clipboardManager.primaryClipDescription?.hasMimeType("text/plain") == true
    }
}

@Composable
fun rememberClipboardController(): ClipboardController {
    val context = LocalContext.current
    return remember { ClipboardController(context) }
}
