package dragoncore.lexer

/**
 * 🐉 Dragon Core — DragonLexer
 *
 * Primera capa del pipeline de compilación matemático.
 * Convierte un string de entrada crudo en una lista tipada de [Token]s.
 *
 * Pipeline: Input String → [DragonLexer.tokenize] → List<Token> → Parser (Vector [2])
 *
 * Capacidades:
 * - Números enteros y decimales (3, 3.14, .5)
 * - Literales Base-N (0xFF, 0b1010, 0o77)
 * - Operadores simples y compuestos (+, -, <=, >>, ==, !=)
 * - Funciones multi-letra (sin, cos, sqrt, derivative, etc.)
 * - Constantes matemáticas (pi, e, phi, inf)
 * - Variables simbólicas (x, y, z, t, n, theta)
 * - Manejo robusto de errores con posición exacta
 *
 * @author Dragon Core Team
 */
class DragonLexer {

    // ─────────────────────────────────────────────
    // MAPA DE PALABRAS CLAVE (funciones + constantes)
    // ─────────────────────────────────────────────
    // Todas las keywords se buscan en lowercase.
    // Este mapa se consulta cuando el lexer lee un identificador multi-letra.
    private val keywords: Map<String, TokenType> = mapOf(
        // Trigonometría
        "sin"       to TokenType.SIN,
        "cos"       to TokenType.COS,
        "tan"       to TokenType.TAN,
        "asin"      to TokenType.ASIN,
        "acos"      to TokenType.ACOS,
        "atan"      to TokenType.ATAN,
        "atan2"     to TokenType.ATAN2,
        "sinh"      to TokenType.SINH,
        "cosh"      to TokenType.COSH,
        "tanh"      to TokenType.TANH,
        "asinh"     to TokenType.ASINH,
        "acosh"     to TokenType.ACOSH,
        "atanh"     to TokenType.ATANH,
        "deg"       to TokenType.DEG,
        "rad"       to TokenType.RAD,

        // Logarítmicas / Exponenciales
        "ln"        to TokenType.LN,
        "log"       to TokenType.LOG,
        "log2"      to TokenType.LOG2,
        "exp"       to TokenType.EXP,
        "sqrt"      to TokenType.SQRT,
        "cbrt"      to TokenType.CBRT,
        "abs"       to TokenType.ABS,
        "ceil"      to TokenType.CEIL,
        "floor"     to TokenType.FLOOR,
        "round"     to TokenType.ROUND,
        "sign"      to TokenType.SIGN,
        "nrt"       to TokenType.NRT,

        // Combinatoria
        "npr"       to TokenType.NPR,
        "ncr"       to TokenType.NCR,
        "gamma"     to TokenType.GAMMA,
        "gcd"       to TokenType.GCD,
        "lcm"       to TokenType.LCM,

        // Cálculo CAS
        "integral"  to TokenType.INTEGRAL,
        "integrate" to TokenType.INTEGRAL,
        "derivative" to TokenType.DERIVATIVE,
        "diff"      to TokenType.DERIVATIVE,
        "limit"     to TokenType.LIMIT,
        "lim"       to TokenType.LIMIT,
        "sum"       to TokenType.SUMMATION,
        "summation" to TokenType.SUMMATION,
        "prod"      to TokenType.PRODUCT_FUNC,
        "product"   to TokenType.PRODUCT_FUNC,
        "taylor"    to TokenType.TAYLOR,
        "solve"     to TokenType.SOLVE,
        "simplify"  to TokenType.SIMPLIFY,

        // Álgebra Lineal
        "det"       to TokenType.DETERMINANT,
        "trans"     to TokenType.TRANSPOSE,
        "inv"       to TokenType.INVERSE,
        "rref"      to TokenType.RREF,
        "cross"     to TokenType.CROSS,
        "dot"       to TokenType.DOT,
        "eigen"     to TokenType.EIGEN,

        // DragonScript — Control de flujo
        "if"        to TokenType.IF,
        "else"      to TokenType.ELSE,
        "while"     to TokenType.WHILE,
        "def"       to TokenType.DEF,
        "return"    to TokenType.RETURN,

        // Constantes
        "pi"        to TokenType.PI,
        "e"         to TokenType.E,
        "phi"       to TokenType.PHI,
        "inf"       to TokenType.INF,
        "infinity"  to TokenType.INF,
        "i"         to TokenType.IMAGINARY
    )

    // ─────────────────────────────────────────────
    // ESTADO DE ESCANEO
    // ─────────────────────────────────────────────
    private var input: String = ""
    private var pos: Int = 0
    private var tokens: MutableList<Token> = mutableListOf()

    /**
     * Punto de entrada principal.
     * Convierte [input] en una lista de [Token]s, terminando siempre con [TokenType.EOF].
     *
     * @param input Expresión matemática cruda (ej. "sin(3.14) + 2^3")
     * @return Lista inmutable de tokens
     * @throws LexerException si se encuentra un carácter no reconocido
     */
    fun tokenize(input: String): List<Token> {
        this.input = input
        this.pos = 0
        this.tokens = mutableListOf()

        while (pos < input.length) {
            val ch = current()

            when {
                // ── Espacios en blanco ──
                ch.isWhitespace() -> advance()

                // ── Números (incluye decimales como .5) ──
                ch.isDigit() || (ch == '.' && peekNext()?.isDigit() == true) -> readNumber()

                // ── Identificadores multi-letra (funciones, constantes, variables) ──
                ch.isLetter() || ch == '_' -> readIdentifier()

                // ── Operadores compuestos y simples ──
                else -> readOperatorOrDelimiter()
            }
        }

        // Siempre terminar con EOF
        tokens.add(Token(TokenType.EOF, "", pos))
        return tokens.toList()
    }

    // ═══════════════════════════════════════════════
    // MÉTODOS DE LECTURA ESPECIALIZADOS
    // ═══════════════════════════════════════════════

    /**
     * Lee un número entero, decimal, o literal Base-N.
     * Soporta: 42, 3.14, .5, 0xFF, 0b1010, 0o77
     */
    private fun readNumber() {
        val startPos = pos

        // ── Detección de literales Base-N ──
        if (current() == '0' && pos + 1 < input.length) {
            when (input[pos + 1].lowercaseChar()) {
                'x' -> { readBaseNLiteral(startPos, "0x", TokenType.HEX_LITERAL) { it.isHexDigit() }; return }
                'b' -> { readBaseNLiteral(startPos, "0b", TokenType.BIN_LITERAL) { it == '0' || it == '1' }; return }
                'o' -> { readBaseNLiteral(startPos, "0o", TokenType.OCT_LITERAL) { it in '0'..'7' }; return }
            }
        }

        // ── Número decimal estándar ──
        val sb = StringBuilder()
        var hasDecimalPoint = false

        // Parte entera (puede estar vacía si empieza con '.')
        while (pos < input.length && current().isDigit()) {
            sb.append(current())
            advance()
        }

        // Punto decimal
        if (pos < input.length && current() == '.' && peekNext()?.isDigit() == true) {
            hasDecimalPoint = true
            sb.append('.')
            advance()

            // Parte fraccionaria
            while (pos < input.length && current().isDigit()) {
                sb.append(current())
                advance()
            }
        } else if (pos < input.length && current() == '.' && sb.isEmpty()) {
            // Caso: ".5" sin parte entera
            hasDecimalPoint = true
            sb.append('.')
            advance()
            while (pos < input.length && current().isDigit()) {
                sb.append(current())
                advance()
            }
        }

        // Notación científica: 1.5e10, 2E-3, 3e+7
        if (pos < input.length && (current() == 'e' || current() == 'E')) {
            sb.append(current())
            advance()
            // Signo opcional del exponente
            if (pos < input.length && (current() == '+' || current() == '-')) {
                sb.append(current())
                advance()
            }
            // Dígitos del exponente (al menos uno requerido)
            if (pos >= input.length || !current().isDigit()) {
                throw LexerException(pos, if (pos < input.length) current() else ' ',
                    "Se esperaban dígitos después del exponente en posición $pos")
            }
            while (pos < input.length && current().isDigit()) {
                sb.append(current())
                advance()
            }
        }

        tokens.add(Token(TokenType.NUMBER, sb.toString(), startPos))
    }

    /**
     * Lee un literal Base-N (hex, bin, oct) después del prefijo.
     */
    private fun readBaseNLiteral(
        startPos: Int,
        prefix: String,
        tokenType: TokenType,
        isValidDigit: (Char) -> Boolean
    ) {
        val sb = StringBuilder(prefix)
        advance() // saltar '0'
        advance() // saltar 'x'/'b'/'o'

        if (pos >= input.length || !isValidDigit(current())) {
            throw LexerException(pos, if (pos < input.length) current() else ' ',
                "Se esperaban dígitos válidos después de '$prefix' en posición $pos")
        }

        while (pos < input.length && (isValidDigit(current()) || current() == '_')) {
            if (current() != '_') { // permitir separadores visuales: 0b1010_1100
                sb.append(current())
            }
            advance()
        }

        tokens.add(Token(tokenType, sb.toString(), startPos))
    }

    /**
     * Lee un identificador multi-letra y lo clasifica como:
     * - Función conocida (sin, cos, sqrt, etc.) → TokenType específico
     * - Constante conocida (pi, e, phi) → TokenType específico
     * - Variable simbólica (x, y, z, theta) → TokenType.VARIABLE
     */
    private fun readIdentifier() {
        val startPos = pos
        val sb = StringBuilder()

        // Consumir letras, dígitos y underscores
        while (pos < input.length && (current().isLetterOrDigit() || current() == '_')) {
            sb.append(current())
            advance()
        }

        val word = sb.toString()
        val lowercase = word.lowercase()

        // Buscar en el mapa de keywords
        val tokenType = keywords[lowercase] ?: TokenType.VARIABLE

        tokens.add(Token(tokenType, word, startPos))
    }

    /**
     * Lee operadores (simples y compuestos) y delimitadores.
     * Maneja correctamente operadores de 2 caracteres (==, !=, <=, >=, <<, >>).
     */
    private fun readOperatorOrDelimiter() {
        val startPos = pos
        val ch = current()

        when (ch) {
            // ── Operadores aritméticos simples ──
            '+' -> { tokens.add(Token(TokenType.PLUS, "+", startPos)); advance() }
            '-' -> { tokens.add(Token(TokenType.MINUS, "-", startPos)); advance() }
            '*' -> { tokens.add(Token(TokenType.MULTIPLY, "*", startPos)); advance() }
            '/' -> { tokens.add(Token(TokenType.DIVIDE, "/", startPos)); advance() }
            '^' -> { tokens.add(Token(TokenType.POWER, "^", startPos)); advance() }
            '%' -> { tokens.add(Token(TokenType.MODULO, "%", startPos)); advance() }

            // ── Factorial ──
            '!' -> {
                advance()
                if (pos < input.length && current() == '=') {
                    // != (not equals)
                    advance()
                    tokens.add(Token(TokenType.NOT_EQUALS, "!=", startPos))
                } else {
                    tokens.add(Token(TokenType.FACTORIAL, "!", startPos))
                }
            }

            // ── Comparación / Asignación ──
            '=' -> {
                advance()
                if (pos < input.length && current() == '=') {
                    advance()
                    tokens.add(Token(TokenType.EQUALS, "==", startPos))
                } else {
                    tokens.add(Token(TokenType.ASSIGN, "=", startPos))
                }
            }

            '<' -> {
                advance()
                when {
                    pos < input.length && current() == '=' -> {
                        advance()
                        tokens.add(Token(TokenType.LESS_EQUAL, "<=", startPos))
                    }
                    pos < input.length && current() == '<' -> {
                        advance()
                        tokens.add(Token(TokenType.BIT_SHIFT_LEFT, "<<", startPos))
                    }
                    else -> tokens.add(Token(TokenType.LESS_THAN, "<", startPos))
                }
            }

            '>' -> {
                advance()
                when {
                    pos < input.length && current() == '=' -> {
                        advance()
                        tokens.add(Token(TokenType.GREATER_EQUAL, ">=", startPos))
                    }
                    pos < input.length && current() == '>' -> {
                        advance()
                        tokens.add(Token(TokenType.BIT_SHIFT_RIGHT, ">>", startPos))
                    }
                    else -> tokens.add(Token(TokenType.GREATER_THAN, ">", startPos))
                }
            }

            // ── Delimitadores ──
            '(' -> { tokens.add(Token(TokenType.LPAREN, "(", startPos)); advance() }
            ')' -> { tokens.add(Token(TokenType.RPAREN, ")", startPos)); advance() }
            ',' -> { tokens.add(Token(TokenType.COMMA, ",", startPos)); advance() }
            ';' -> { tokens.add(Token(TokenType.SEMICOLON, ";", startPos)); advance() }

            // ── Matrices ──
            '[' -> { tokens.add(Token(TokenType.MATRIX_START, "[", startPos)); advance() }
            ']' -> { tokens.add(Token(TokenType.MATRIX_END, "]", startPos)); advance() }

            // ── Bloques DragonScript ──
            '{' -> { tokens.add(Token(TokenType.LBRACE, "{", startPos)); advance() }
            '}' -> { tokens.add(Token(TokenType.RBRACE, "}", startPos)); advance() }

            // ── Pipe / Valor absoluto / OR lógico ──
            '|' -> {
                advance()
                if (pos < input.length && current() == '|') {
                    advance()
                    tokens.add(Token(TokenType.OR, "||", startPos))
                } else {
                    tokens.add(Token(TokenType.PIPE, "|", startPos))
                }
            }

            // ── Operadores bit a bit / AND lógico ──
            '&' -> {
                advance()
                if (pos < input.length && current() == '&') {
                    advance()
                    tokens.add(Token(TokenType.AND, "&&", startPos))
                } else {
                    tokens.add(Token(TokenType.BIT_AND, "&", startPos))
                }
            }
            '~' -> { tokens.add(Token(TokenType.BIT_NOT, "~", startPos)); advance() }
            '@' -> { tokens.add(Token(TokenType.BIT_XOR, "@", startPos)); advance() }

            // ── Carácter no reconocido ──
            else -> throw LexerException(pos, ch)
        }
    }

    // ═══════════════════════════════════════════════
    // UTILIDADES DE NAVEGACIÓN
    // ═══════════════════════════════════════════════

    /** Retorna el carácter actual sin avanzar. */
    private fun current(): Char = input[pos]

    /** Avanza el puntero una posición. */
    private fun advance() { pos++ }

    /** Mira el siguiente carácter sin consumirlo (null si es fin de input). */
    private fun peekNext(): Char? = if (pos + 1 < input.length) input[pos + 1] else null

    /**
     * Extensión: determina si un Char es un dígito hexadecimal válido.
     */
    private fun Char.isHexDigit(): Boolean =
        this.isDigit() || this.lowercaseChar() in 'a'..'f'
}
