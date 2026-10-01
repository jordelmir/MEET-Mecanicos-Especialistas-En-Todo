package cr.dragon.calc.vision

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Handles ML Kit Text Recognition and OCR error heuristics.
 * Optimized for mathematical expressions via custom noise filtering.
 */
object MathRecognizer {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Processes an InputImage synchronously (suspend) and returns the sanitized math string.
     */
    suspend fun recognizeMath(image: InputImage): String {
        return try {
            val result = textRecognizer.process(image).await()
            val validBlocks = mutableListOf<String>()

            val noiseKeywords = listOf("google", "search", "http", "www", "visit", "pestaña")
            val mathKeywords = listOf("lim", "sqrt", "sin", "cos", "tan", "log", "ln", "exp")

            for (block in result.textBlocks) {
                val text = block.text.lowercase()

                // Reject singular letters that are not typical variables
                if (text.length <= 1 && !text.matches(Regex("[xyzabc0-9]"))) continue

                // Reject blocks with pure UI noise keywords
                if (noiseKeywords.any { text.contains(it) }) continue

                val hasMathKeywords = mathKeywords.any { text.contains(it) }
                val hasDigitsOrSymbols = text.any { it.isDigit() || it in "+-*/^=()[]{}<>×÷" || it == 'x' || it == 'y' || it == 'z' }

                if (!hasDigitsOrSymbols && !hasMathKeywords) {
                    continue // Drop purely textual blocks like "Example"
                }

                validBlocks.add(block.text)
            }

            val rawText = validBlocks.joinToString(" ").replace("\n", "").trim()
            sanitizeOcr(rawText)
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Heuristic filter: Fixes common OCR misreads when scanning math (Human-in-the-Loop friendly).
     */
    fun sanitizeOcr(raw: String): String {
        // Aggressive web-noise stripping before removing spaces
        var clean = raw.replace(Regex("(http|www|search|google|visit|delete|pestaña|profesor|dematesa|youtube|video)[a-zA-Z0-9]*", RegexOption.IGNORE_CASE), "")

        clean = clean.replace(" ", "") // Remove all spaces

        // Priority structural fixes
        clean = clean.replace("limx", "lim(x")
        clean = clean.replace("X", "*")
        clean = clean.replace("×", "*")
        clean = clean.replace("÷", "/")

        // Fix typical letter/number confusion in math functions
        clean = clean.replace(Regex("s1n|sln", RegexOption.IGNORE_CASE), "sin")
        clean = clean.replace(Regex("c0s", RegexOption.IGNORE_CASE), "cos")
        clean = clean.replace(Regex("t4n", RegexOption.IGNORE_CASE), "tan")
        clean = clean.replace(Regex("l_n|1n|In", RegexOption.IGNORE_CASE), "ln")
        clean = clean.replace(Regex("l0g", RegexOption.IGNORE_CASE), "log")
        clean = clean.replace("e^", "exp(")

        // Fix missing superscripts read as adjacent numbers (like x2 instead of x^2)
        clean = clean.replace(Regex("([xyz])([0-9]+)") ) { matchResult ->
            "${matchResult.groupValues[1]}^${matchResult.groupValues[2]}"
        }

        // Parentheses fixes
        clean = clean.replace("{", "(").replace("}", ")")
        clean = clean.replace("[", "(").replace("]", ")")

        // Common visual operator miss-reads
        clean = clean.replace("= =", "==")
        clean = clean.replace("=!", "!=")

        // Pi detection
        clean = clean.replace("TT", "pi")
        clean = clean.replace("π", "pi")

        return clean
    }
}
