package dragoncore.parser

import dragoncore.lexer.Token
import dragoncore.lexer.TokenType

/**
 * Dragon Core - DragonParser
 *
 * Recursive Descent Parser que transforma una List<Token> (del DragonLexer)
 * en un arbol de sintaxis abstracta (AST) representado por nodos [Expr].
 *
 * Pipeline: String -> DragonLexer -> List<Token> -> DragonParser -> Expr (AST)
 *
 * Precedencia de operadores (de menor a mayor, estilo PEMDAS):
 *
 *   1. Asignacion        =           (menor precedencia)
 *   2. Comparacion        ==, !=, <, >, <=, >=
 *   3. Bitwise OR         |
 *   4. Bitwise XOR        @
 *   5. Bitwise AND        &
 *   6. Bitwise Shift      <<, >>
 *   7. Suma/Resta         +, -
 *   8. Multiplicacion     *, /, %
 *   9. Potencia           ^           (asociatividad derecha)
 *  10. Unarios            -x, ~x, +x
 *  11. Postfijos          n!
 *  12. Primarios          numeros, variables, funciones, parentesis, matrices, |abs|
 *
 * Cada nivel de precedencia tiene su propio metodo privado.
 */
class DragonParser(private val tokens: List<Token>) {

    private var pos: Int = 0

    // ================================================================
    // PUNTO DE ENTRADA PUBLICO
    // ================================================================

    /**
     * Parsea la secuencia completa de tokens.
     * Soporta múltiples sentencias separadas por ';' o NEWLINE.
     */
    fun parse(): Expr {
        if (tokens.isEmpty() || (tokens.size == 1 && tokens[0].type == TokenType.EOF)) {
            throw ParserException(
                tokens.firstOrNull() ?: Token(TokenType.EOF, "", 0),
                "Expresion vacia"
            )
        }

        // Consumir newlines iniciales
        skipNewlines()

        val statements = mutableListOf<Expr>()
        statements.add(parseStatement())

        while (!isAtEnd()) {
            // Consume separadores de sentencias
            if (check(TokenType.SEMICOLON) || check(TokenType.NEWLINE)) {
                while (check(TokenType.SEMICOLON) || check(TokenType.NEWLINE)) advance()
                if (!isAtEnd()) {
                    statements.add(parseStatement())
                }
            } else {
                break
            }
        }

        if (!isAtEnd()) {
            throw ParserException(current(), "Token inesperado despues de la expresion")
        }

        return if (statements.size == 1) statements[0]
        else Expr.BlockExpr(statements)
    }

    /** Consume tokens NEWLINE silenciosamente. */
    private fun skipNewlines() {
        while (check(TokenType.NEWLINE)) advance()
    }

    // ================================================================
    // DRAGONSCRIPT: SENTENCIAS DE CONTROL
    // ================================================================

    /**
     * Despacha una sentencia de control de flujo o cae a parseAssignment().
     */
    private fun parseStatement(): Expr {
        skipNewlines()
        return when {
            check(TokenType.IF)     -> parseIf()
            check(TokenType.WHILE)  -> parseWhile()
            check(TokenType.DEF)    -> parseDef()
            check(TokenType.RETURN) -> parseReturn()
            check(TokenType.LBRACE) -> parseBlock()
            else -> parseAssignment()
        }
    }

    /** if(cond) stmt [else stmt] */
    private fun parseIf(): Expr {
        advance() // consumir 'if'
        expect(TokenType.LPAREN, "Se esperaba '(' después de 'if'")
        val condition = parseAssignment()
        expect(TokenType.RPAREN, "Se esperaba ')' después de la condición")
        skipNewlines()
        val thenBranch = parseStatement()
        skipNewlines()
        val elseBranch = if (check(TokenType.ELSE)) {
            advance() // consumir 'else'
            skipNewlines()
            parseStatement()
        } else null
        return Expr.IfExpr(condition, thenBranch, elseBranch)
    }

    /** while(cond) stmt */
    private fun parseWhile(): Expr {
        advance() // consumir 'while'
        expect(TokenType.LPAREN, "Se esperaba '(' después de 'while'")
        val condition = parseAssignment()
        expect(TokenType.RPAREN, "Se esperaba ')' después de la condición")
        skipNewlines()
        val body = parseStatement()
        return Expr.WhileExpr(condition, body)
    }

    /** def nombre(param1, param2, ...) { body } */
    private fun parseDef(): Expr {
        advance() // consumir 'def'
        if (!check(TokenType.VARIABLE)) {
            throw ParserException(current(), "Se esperaba el nombre de la función después de 'def'")
        }
        val name = advance().value
        expect(TokenType.LPAREN, "Se esperaba '(' después del nombre de función")

        val params = mutableListOf<String>()
        if (!check(TokenType.RPAREN)) {
            if (!check(TokenType.VARIABLE)) {
                throw ParserException(current(), "Se esperaba nombre de parámetro")
            }
            params.add(advance().value)
            while (check(TokenType.COMMA)) {
                advance()
                if (!check(TokenType.VARIABLE)) {
                    throw ParserException(current(), "Se esperaba nombre de parámetro")
                }
                params.add(advance().value)
            }
        }
        expect(TokenType.RPAREN, "Se esperaba ')' después de los parámetros")
        skipNewlines()
        val body = parseStatement()
        return Expr.FunctionDefExpr(name, params, body)
    }

    /** return expr */
    private fun parseReturn(): Expr {
        advance() // consumir 'return'
        val value = parseAssignment()
        return Expr.ReturnExpr(value)
    }

    /** { stmt; stmt; ... } → BlockExpr */
    private fun parseBlock(): Expr {
        advance() // consumir '{'
        skipNewlines()

        val statements = mutableListOf<Expr>()
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            statements.add(parseStatement())
            // Consumir separadores opcionales
            while (check(TokenType.SEMICOLON) || check(TokenType.NEWLINE)) advance()
        }

        expect(TokenType.RBRACE, "Se esperaba '}' para cerrar el bloque")
        return if (statements.size == 1) statements[0]
        else Expr.BlockExpr(statements)
    }

    // ================================================================
    // NIVEL 1: ASIGNACION (menor precedencia)
    // variable = expresion
    // ================================================================

    private fun parseAssignment(): Expr {
        val expr = parseLogicalOr()

        // Si es una variable seguida de '=', es una asignacion
        if (check(TokenType.ASSIGN)) {
            if (expr is Expr.VariableExpr) {
                advance() // consumir '='
                val value = parseAssignment() // asociatividad derecha
                return Expr.AssignExpr(expr.name, value)
            }
            throw ParserException(current(), "Solo se puede asignar a una variable")
        }

        return expr
    }

    // ================================================================
    // NIVEL 1.5: LOGICAL OR (||) y LOGICAL AND (&&)
    // ================================================================

    private fun parseLogicalOr(): Expr {
        var left = parseLogicalAnd()
        while (check(TokenType.OR)) {
            val op = advance()
            val right = parseLogicalAnd()
            left = Expr.BinaryExpr(left, op, right)
        }
        return left
    }

    private fun parseLogicalAnd(): Expr {
        var left = parseComparison()
        while (check(TokenType.AND)) {
            val op = advance()
            val right = parseComparison()
            left = Expr.BinaryExpr(left, op, right)
        }
        return left
    }

    // ================================================================
    // NIVEL 2: COMPARACION
    // ==, !=, <, >, <=, >=
    // ================================================================

    private fun parseComparison(): Expr {
        var left = parseBitwiseOr()

        while (checkAny(
                TokenType.EQUALS, TokenType.NOT_EQUALS,
                TokenType.LESS_THAN, TokenType.GREATER_THAN,
                TokenType.LESS_EQUAL, TokenType.GREATER_EQUAL
            )) {
            val op = advance()
            val right = parseBitwiseOr()
            left = Expr.BinaryExpr(left, op, right)
        }

        return left
    }

    // ================================================================
    // NIVEL 3: BITWISE OR
    // | (cuando se usa como operador binario entre expresiones numericas)
    // ================================================================

    private fun parseBitwiseOr(): Expr {
        var left = parseBitwiseXor()

        while (check(TokenType.BIT_OR)) {
            val op = advance()
            val right = parseBitwiseXor()
            left = Expr.BinaryExpr(left, op, right)
        }

        return left
    }

    // ================================================================
    // NIVEL 4: BITWISE XOR
    // @
    // ================================================================

    private fun parseBitwiseXor(): Expr {
        var left = parseBitwiseAnd()

        while (check(TokenType.BIT_XOR)) {
            val op = advance()
            val right = parseBitwiseAnd()
            left = Expr.BinaryExpr(left, op, right)
        }

        return left
    }

    // ================================================================
    // NIVEL 5: BITWISE AND
    // &
    // ================================================================

    private fun parseBitwiseAnd(): Expr {
        var left = parseBitwiseShift()

        while (check(TokenType.BIT_AND)) {
            val op = advance()
            val right = parseBitwiseShift()
            left = Expr.BinaryExpr(left, op, right)
        }

        return left
    }

    // ================================================================
    // NIVEL 6: BITWISE SHIFT
    // <<, >>
    // ================================================================

    private fun parseBitwiseShift(): Expr {
        var left = parseExpression()

        while (checkAny(TokenType.BIT_SHIFT_LEFT, TokenType.BIT_SHIFT_RIGHT)) {
            val op = advance()
            val right = parseExpression()
            left = Expr.BinaryExpr(left, op, right)
        }

        return left
    }

    // ================================================================
    // NIVEL 3: SUMA Y RESTA (parseExpression)
    // +, -
    // ================================================================

    private fun parseExpression(): Expr {
        var left = parseTerm()

        while (checkAny(TokenType.PLUS, TokenType.MINUS)) {
            val op = advance()
            val right = parseTerm()
            left = Expr.BinaryExpr(left, op, right)
        }

        return left
    }

    // ================================================================
    // NIVEL 4: MULTIPLICACION, DIVISION, MODULO (parseTerm)
    // *, /, %
    // ================================================================

    private fun parseTerm(): Expr {
        var left = parsePower()

        while (checkAny(TokenType.MULTIPLY, TokenType.DIVIDE, TokenType.MODULO)) {
            val op = advance()
            val right = parsePower()
            left = Expr.BinaryExpr(left, op, right)
        }

        return left
    }

    // ================================================================
    // NIVEL 5: POTENCIA (parsePower)
    // ^ (asociatividad de DERECHA a izquierda: 2^3^4 = 2^(3^4))
    // ================================================================

    private fun parsePower(): Expr {
        val base = parseUnary()

        if (check(TokenType.POWER)) {
            val op = advance()
            val exponent = parsePower() // RECURSION DERECHA -> asociatividad derecha
            return Expr.BinaryExpr(base, op, exponent)
        }

        return base
    }

    // ================================================================
    // NIVEL 6: OPERADORES UNARIOS PREFIJOS (parseUnary)
    // -x, +x, ~x
    // ================================================================

    private fun parseUnary(): Expr {
        if (checkAny(TokenType.MINUS, TokenType.PLUS, TokenType.BIT_NOT)) {
            val op = advance()
            val operand = parseUnary() // permite encadenar: --x, -+x
            // Optimizacion: +x simplemente devuelve x
            if (op.type == TokenType.PLUS) return operand
            return Expr.UnaryExpr(op, operand, isPostfix = false)
        }

        return parsePostfix()
    }

    // ================================================================
    // NIVEL 7: OPERADORES POSTFIJOS (parsePostfix)
    // n! (factorial)
    // ================================================================

    private fun parsePostfix(): Expr {
        var expr = parsePrimary()

        while (check(TokenType.FACTORIAL)) {
            val op = advance()
            expr = Expr.UnaryExpr(op, expr, isPostfix = true)
        }

        return expr
    }

    // ================================================================
    // NIVEL 8: PRIMARIOS (parsePrimary) — mayor precedencia
    // Numeros, variables, constantes, funciones, parentesis, matrices, |abs|
    // ================================================================

    private fun parsePrimary(): Expr {
        val token = current()

        return when (token.type) {

            // -- Numeros --
            TokenType.NUMBER -> {
                advance()
                Expr.NumberExpr(token.value.toDouble())
            }

            // -- Constantes matematicas --
            TokenType.PI, TokenType.E, TokenType.PHI,
            TokenType.INF, TokenType.IMAGINARY -> {
                advance()
                Expr.ConstantExpr(token.value, token)
            }

            // -- Variables o User-Defined Function calls --
            TokenType.VARIABLE -> {
                // Si una variable va seguida de '(', es una llamada a función del usuario
                if (pos + 1 < tokens.size && tokens[pos + 1].type == TokenType.LPAREN) {
                    parseFunction(token)
                } else {
                    advance()
                    Expr.VariableExpr(token.value)
                }
            }

            // -- Funciones conocidas --
            TokenType.SIN, TokenType.COS, TokenType.TAN,
            TokenType.ASIN, TokenType.ACOS, TokenType.ATAN, TokenType.ATAN2,
            TokenType.SINH, TokenType.COSH, TokenType.TANH,
            TokenType.ASINH, TokenType.ACOSH, TokenType.ATANH,
            TokenType.DEG, TokenType.RAD,
            TokenType.LN, TokenType.LOG, TokenType.LOG2,
            TokenType.EXP, TokenType.SQRT, TokenType.CBRT,
            TokenType.ABS, TokenType.CEIL, TokenType.FLOOR,
            TokenType.ROUND, TokenType.SIGN, TokenType.NRT,
            TokenType.NPR, TokenType.NCR, TokenType.GAMMA,
            TokenType.GCD, TokenType.LCM,
            TokenType.INTEGRAL, TokenType.DERIVATIVE, TokenType.LIMIT,
            TokenType.SUMMATION, TokenType.PRODUCT_FUNC, TokenType.TAYLOR, TokenType.SOLVE,
            TokenType.SIMPLIFY,
            TokenType.DETERMINANT, TokenType.TRANSPOSE, TokenType.INVERSE,
            TokenType.RREF, TokenType.CROSS, TokenType.DOT, TokenType.EIGEN -> {
                parseFunction(token)
            }

            // -- Parentesis de agrupacion --
            TokenType.LPAREN -> {
                advance() // consumir '('
                val expr = parseAssignment()
                expect(TokenType.RPAREN, "Se esperaba ')' para cerrar el parentesis")
                expr
            }

            // -- Valor absoluto: |expr| --
            TokenType.PIPE -> {
                advance() // consumir '|'
                val inner = parseAssignment()
                expect(TokenType.PIPE, "Se esperaba '|' para cerrar el valor absoluto")
                Expr.AbsExpr(inner)
            }

            // -- Matrices: [1,2; 3,4] --
            TokenType.MATRIX_START -> {
                parseMatrix()
            }

            // -- Literales base-N (se evaluan como numero) --
            TokenType.HEX_LITERAL -> {
                advance()
                val numericValue = token.value.removePrefix("0x").removePrefix("0X")
                    .toLong(16).toDouble()
                Expr.NumberExpr(numericValue)
            }

            TokenType.BIN_LITERAL -> {
                advance()
                val numericValue = token.value.removePrefix("0b").removePrefix("0B")
                    .toLong(2).toDouble()
                Expr.NumberExpr(numericValue)
            }

            TokenType.OCT_LITERAL -> {
                advance()
                val numericValue = token.value.removePrefix("0o").removePrefix("0O")
                    .toLong(8).toDouble()
                Expr.NumberExpr(numericValue)
            }

            // -- Operadores bitwise como funciones unarias si aparecen en posicion primaria --
            TokenType.BIT_NOT -> {
                val op = advance()
                val operand = parseUnary()
                Expr.UnaryExpr(op, operand, isPostfix = false)
            }

            // -- Token inesperado --
            else -> {
                throw ParserException(token,
                    "Se esperaba un numero, variable o funcion, se encontro '${token.value.ifEmpty { token.type.name }}'")
            }
        }
    }

    // ================================================================
    // PARSEO DE FUNCIONES
    // name(arg1, arg2, ..., argN)
    // ================================================================

    private fun parseFunction(nameToken: Token): Expr {
        advance() // consumir el nombre de la funcion

        // Las funciones DEBEN ir seguidas de parentesis
        expect(TokenType.LPAREN, "Se esperaba '(' despues de la funcion '${nameToken.value}'")

        val args = mutableListOf<Expr>()

        // Manejar funciones sin argumentos: func()
        if (!check(TokenType.RPAREN)) {
            args.add(parseAssignment())

            while (check(TokenType.COMMA)) {
                advance() // consumir ','
                args.add(parseAssignment())
            }
        }

        expect(TokenType.RPAREN, "Se esperaba ')' para cerrar la funcion '${nameToken.value}'")

        // Especial para Derivada Simbolica: d(expr, var)
        if (nameToken.type == TokenType.DERIVATIVE) {
            if (args.size != 2) {
                throw ParserException(nameToken, "La funcion d(expr, var) requiere exactamente 2 argumentos")
            }
            val variable = args[1]
            if (variable !is Expr.VariableExpr) {
                throw ParserException(nameToken, "El segundo argumento de d(expr, var) debe ser una variable")
            }
            return Expr.DerivativeExpr(args[0], variable)
        }

        return Expr.FunctionExpr(nameToken, args)
    }

    // ================================================================
    // PARSEO DE MATRICES
    // [row1_elem1, row1_elem2; row2_elem1, row2_elem2]
    // ================================================================

    private fun parseMatrix(): Expr {
        advance() // consumir '['

        val rows = mutableListOf<List<Expr>>()
        val firstRow = mutableListOf<Expr>()

        if (!check(TokenType.MATRIX_END)) {
            firstRow.add(parseAssignment())

            while (check(TokenType.COMMA)) {
                advance()
                firstRow.add(parseAssignment())
            }
        }
        rows.add(firstRow)

        // Filas adicionales separadas por ';'
        while (check(TokenType.SEMICOLON)) {
            advance() // consumir ';'
            val row = mutableListOf<Expr>()
            row.add(parseAssignment())

            while (check(TokenType.COMMA)) {
                advance()
                row.add(parseAssignment())
            }
            rows.add(row)
        }

        expect(TokenType.MATRIX_END, "Se esperaba ']' para cerrar la matriz")

        return Expr.MatrixExpr(rows)
    }

    // ================================================================
    // UTILIDADES DE NAVEGACION
    // ================================================================

    /** Retorna el token actual sin consumirlo. */
    private fun current(): Token = tokens[pos]

    /** Consume el token actual y retorna el token consumido. */
    private fun advance(): Token {
        val token = tokens[pos]
        if (!isAtEnd()) pos++
        return token
    }

    /** Verifica si el token actual es del tipo esperado sin consumirlo. */
    private fun check(type: TokenType): Boolean =
        !isAtEnd() && current().type == type

    /** Verifica si el token actual es alguno de los tipos dados. */
    private fun checkAny(vararg types: TokenType): Boolean =
        !isAtEnd() && current().type in types

    /** Verifica si hemos llegado al final de los tokens. */
    private fun isAtEnd(): Boolean =
        pos >= tokens.size || tokens[pos].type == TokenType.EOF

    /**
     * Consume un token del tipo esperado o lanza ParserException.
     * Usado para validar tokens estructurales como parentesis y comas.
     */
    private fun expect(type: TokenType, errorMessage: String): Token {
        if (check(type)) return advance()
        throw ParserException(current(), errorMessage)
    }
}
