package dragoncore.parser

import dragoncore.lexer.Token

/**
 * Dragon Core - ParserException
 *
 * Excepcion tipada lanzada por el DragonParser cuando encuentra
 * un error de sintaxis en la secuencia de tokens.
 *
 * Incluye el Token problematico para poder reportar la posicion
 * exacta en el input original (usado por la UI para resaltar errores).
 *
 * @property token   El token donde se detecto el error de sintaxis.
 * @property detail  Descripcion legible del error.
 */
class ParserException(
    val token: Token,
    val detail: String,
    message: String = "Error de sintaxis en posicion ${token.position}: $detail [token: ${token.value.ifEmpty { token.type.name }}]"
) : RuntimeException(message) {

    /**
     * Genera un indicador visual del error sobre el input original.
     * Ejemplo:
     *   Input:  2 + * 3
     *   Error:      ^ Se esperaba una expresion, se encontro '*'
     */
    fun formatErrorPointer(input: String): String {
        val safePos = token.position.coerceIn(0, input.length)
        val pointer = " ".repeat(safePos) + "^"
        val tokenDesc = if (token.value.isNotEmpty()) "'${token.value}'" else token.type.name
        return "  $input\n  $pointer $detail (token: $tokenDesc)"
    }
}
