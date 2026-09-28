package dragoncore.lexer

/**
 * 🐉 Dragon Core — LexerException
 *
 * Excepción tipada lanzada cuando el Lexer encuentra un carácter
 * o secuencia no reconocida en el input.
 *
 * Incluye la posición exacta y el carácter problemático para que
 * la capa UI pueda resaltar el error con precisión (Naranja #FF5722).
 *
 * @property position    Índice (0-based) del carácter inválido en el input.
 * @property invalidChar El carácter que causó el error.
 */
class LexerException(
    val position: Int,
    val invalidChar: Char,
    message: String = "Carácter no reconocido '$invalidChar' en posición $position"
) : RuntimeException(message) {

    /**
     * Genera un indicador visual de la posición del error.
     * Ejemplo:
     *   Input:  2 + @3
     *   Error:      ^
     */
    fun formatErrorPointer(input: String): String {
        val safePos = position.coerceIn(0, input.length)
        val pointer = " ".repeat(safePos) + "^"
        return "  $input\n  $pointer Error: carácter inesperado '$invalidChar'"
    }
}
