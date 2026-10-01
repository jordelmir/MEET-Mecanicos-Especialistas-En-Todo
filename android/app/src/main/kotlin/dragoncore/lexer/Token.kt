package dragoncore.lexer

/**
 * 🐉 Dragon Core — Token
 *
 * Representa una unidad léxica individual producida por el DragonLexer.
 *
 * @property type     Tipo semántico del token (ver [TokenType]).
 * @property value    Texto original extraído del input (ej. "3.14", "sin", "+").
 * @property position Índice (0-based) en el string de entrada donde comienza este token.
 *                    Usado para mensajes de error con ubicación precisa.
 */
data class Token(
    val type: TokenType,
    val value: String,
    val position: Int
) {
    /**
     * Representación legible para debugging y logging (DRAGON_OUT).
     * Formato: Token(NUMBER, "3.14", pos=5)
     */
    override fun toString(): String =
        "Token($type, \"$value\", pos=$position)"
}
