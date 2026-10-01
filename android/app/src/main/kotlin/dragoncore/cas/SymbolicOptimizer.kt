package dragoncore.cas

import dragoncore.lexer.Token
import dragoncore.lexer.TokenType
import dragoncore.parser.Expr

/**
 * Elysium Vanguard — SymbolicOptimizer (CAS Kernel)
 *
 * Motor de simplificación algebraica con Pattern Matching recursivo.
 *
 * Capacidades:
 *   1. Constant Folding avanzado (colapsa nodos constantes)
 *   2. Identidades aditivas/multiplicativas (neutralización)
 *   3. Reducción de términos semejantes (x+x → 2x, nx+mx → (n+m)x)
 *   4. Identidades trigonométricas (sin²(x)+cos²(x) → 1)
 *   5. Potencias canónicas (x^0→1, x^1→x, 0^n→0, 1^n→1)
 *   6. Negación simplificada (--x → x)
 *   7. Hash-consing vía DragonIR para deduplicación
 *
 * El optimizer aplica múltiples pasadas hasta alcanzar punto fijo
 * (la expresión ya no cambia).
 */
class SymbolicOptimizer {

    /** Número máximo de pasadas para alcanzar punto fijo. */
    private val maxPasses = 5

    /** Tutor Mode: Rastro de pasos de simplificación/derivación */
    val evaluationSteps = mutableListOf<String>()

    /**
     * Punto de entrada: simplifica una expresión hasta punto fijo.
     */
    fun simplify(expr: Expr): Expr {
        DragonIR.clear()
        var current = expr
        for (pass in 0 until maxPasses) {
            val next = simplifyPass(current)
            if (DragonIR.structuralEquals(current, next)) break
            current = next
        }
        return current
    }

    /**
     * Una pasada completa de simplificación bottom-up.
     */
    private fun simplifyPass(expr: Expr): Expr {
        val simplified = when (expr) {
            is Expr.NumberExpr -> expr
            is Expr.VariableExpr -> expr
            is Expr.ConstantExpr -> expr
            is Expr.MatrixExpr -> expr

            is Expr.UnaryExpr -> simplifyUnary(expr)
            is Expr.BinaryExpr -> simplifyBinary(expr)
            is Expr.FunctionExpr -> simplifyFunction(expr)
            is Expr.AbsExpr -> simplifyAbs(expr)
            is Expr.AssignExpr -> Expr.AssignExpr(expr.name, simplifyPass(expr.value))
            is Expr.DerivativeExpr -> simplifyDerivative(expr)
            is Expr.IntegralExpr -> simplifyIntegral(expr)
            // DragonScript nodes: pass through sin simplificar
            is Expr.BlockExpr -> Expr.BlockExpr(expr.statements.map { simplifyPass(it) })
            is Expr.IfExpr -> Expr.IfExpr(simplifyPass(expr.condition), simplifyPass(expr.thenBranch), expr.elseBranch?.let { simplifyPass(it) })
            is Expr.WhileExpr -> Expr.WhileExpr(simplifyPass(expr.condition), simplifyPass(expr.body))
            is Expr.FunctionDefExpr -> Expr.FunctionDefExpr(expr.name, expr.params, simplifyPass(expr.body))
            is Expr.ReturnExpr -> Expr.ReturnExpr(simplifyPass(expr.value))
        }
        return DragonIR.intern(simplified)
    }

    /**
     * Resuelve una derivada simbólica d(f, x).
     */
    private fun simplifyDerivative(expr: Expr.DerivativeExpr): Expr {
        val wrt = expr.variable
        // Primero simplificamos la expresión interna
        val inner = simplifyPass(expr.expression)
        // Calculamos la derivada analítica
        val result = diff(inner, wrt)
        // Simplificamos el resultado final
        return simplifyPass(result)
    }

    /**
     * Resuelve o simplifica una integral simbólica int(f, x).
     */
    private fun simplifyIntegral(expr: Expr.IntegralExpr): Expr {
        val wrt = expr.variable
        // Primero simplificamos la expresión interna
        val inner = simplifyPass(expr.expression)

        // Intentamos integración simbólica
        val integrator = SymbolicIntegrator()
        val result = try {
            val res = integrator.integrate(inner, wrt)
            evaluationSteps.addAll(integrator.evaluationSteps)
            res
        } catch (e: Exception) {
            Expr.IntegralExpr(inner, wrt)
        }

        // Si el resultado sigue siendo una IntegralExpr (no pudo resolverla)
        // pero la parte interna cambió (se simplificó), retornamos la nueva IntegralExpr.
        // Si se resolvió (no es IntegralExpr), retornamos el resultado simplificado.
        return if (result is Expr.IntegralExpr) {
            Expr.IntegralExpr(inner, wrt)
        } else {
            simplifyPass(result)
        }
    }

    /**
     * Motor de Diferenciación Analítica (Term-Rewriting).
     * Aplica reglas de cálculo simbólico recursivamente.
     */
    fun diff(expr: Expr, wrt: Expr.VariableExpr): Expr = when (expr) {
        // d(C, x) = 0
        is Expr.NumberExpr -> {
            evaluationSteps.add("Derivada de una constante: \\frac{d}{d${wrt.name}} (${exprToString(expr)}) = 0")
            num(0.0)
        }
        is Expr.ConstantExpr -> {
            evaluationSteps.add("Derivada de una constante: \\frac{d}{d${wrt.name}} (${exprToString(expr)}) = 0")
            num(0.0)
        }

        // d(x, x) = 1, d(y, x) = 0
        is Expr.VariableExpr -> {
            if (expr.name == wrt.name) {
                evaluationSteps.add("Derivada de la propia variable: \\frac{d}{d${wrt.name}} ${expr.name} = 1")
                num(1.0)
            } else {
                evaluationSteps.add("Derivada de variable independiente: \\frac{d}{d${wrt.name}} ${expr.name} = 0")
                num(0.0)
            }
        }

        // d(-u, x) = -d(u, x)
        is Expr.UnaryExpr -> {
            if (expr.operator.type == TokenType.MINUS) {
                evaluationSteps.add("Regla de la constante (factor -1): \\frac{d}{dx}(-u) = -\\frac{d}{dx}u")
                Expr.UnaryExpr(tok(TokenType.MINUS), diff(expr.operand, wrt))
            } else {
                num(0.0)
            }
        }

        is Expr.BinaryExpr -> {
            val u = expr.left
            val v = expr.right
            val op = expr.operator.type

            when (op) {
                TokenType.PLUS -> {
                    evaluationSteps.add("Regla de la suma: \\frac{d}{dx}(u + v) = \\frac{d}{dx}u + \\frac{d}{dx}v")
                    Expr.BinaryExpr(diff(u, wrt), tok(TokenType.PLUS), diff(v, wrt))
                }

                TokenType.MINUS -> {
                    evaluationSteps.add("Regla de la resta: \\frac{d}{dx}(u - v) = \\frac{d}{dx}u - \\frac{d}{dx}v")
                    Expr.BinaryExpr(diff(u, wrt), tok(TokenType.MINUS), diff(v, wrt))
                }

                TokenType.MULTIPLY -> {
                    evaluationSteps.add("Regla del producto: \\frac{d}{dx}(u \\cdot v) = u'v + uv'")
                    Expr.BinaryExpr(
                        Expr.BinaryExpr(diff(u, wrt), tok(TokenType.MULTIPLY), v),
                        tok(TokenType.PLUS),
                        Expr.BinaryExpr(u, tok(TokenType.MULTIPLY), diff(v, wrt))
                    )
                }

                TokenType.DIVIDE -> {
                    evaluationSteps.add("Regla del cociente: \\frac{d}{dx}(\\frac{u}{v}) = \\frac{u'v - uv'}{v^2}")
                    Expr.BinaryExpr(
                        Expr.BinaryExpr(
                            Expr.BinaryExpr(diff(u, wrt), tok(TokenType.MULTIPLY), v),
                            tok(TokenType.MINUS),
                            Expr.BinaryExpr(u, tok(TokenType.MULTIPLY), diff(v, wrt))
                        ),
                        tok(TokenType.DIVIDE),
                        Expr.BinaryExpr(v, tok(TokenType.POWER), num(2.0))
                    )
                }

                TokenType.POWER -> {
                    if (v is Expr.NumberExpr) {
                        evaluationSteps.add("Regla de la potencia: \\frac{d}{dx}(u^n) = n \\cdot u^{n-1} \\cdot u'")
                        val n = v.value
                        Expr.BinaryExpr(
                            num(n),
                            tok(TokenType.MULTIPLY),
                            Expr.BinaryExpr(
                                Expr.BinaryExpr(u, tok(TokenType.POWER), num(n - 1)),
                                tok(TokenType.MULTIPLY),
                                diff(u, wrt)
                            )
                        )
                    } else {
                        evaluationSteps.add("Regla de la potencia general: d(u^v) = u^v·(v'ln u + v u'/u)")
                        val lnU = Expr.FunctionExpr(tok(TokenType.LN), listOf(u))
                        val term1 = Expr.BinaryExpr(diff(v, wrt), tok(TokenType.MULTIPLY), lnU)
                        val term2 = Expr.BinaryExpr(
                            v,
                            tok(TokenType.MULTIPLY),
                            Expr.BinaryExpr(diff(u, wrt), tok(TokenType.DIVIDE), u)
                        )
                        Expr.BinaryExpr(
                            expr, // u^v
                            tok(TokenType.MULTIPLY),
                            Expr.BinaryExpr(term1, tok(TokenType.PLUS), term2)
                        )
                    }
                }
                else -> num(0.0)
            }
        }

        is Expr.FunctionExpr -> {
            val u = expr.arguments[0] // Asumimos un argumento para sin, cos, ln, exp
            val du = diff(u, wrt)

            when (expr.name.type) {
                TokenType.SIN -> {
                    evaluationSteps.add("Regla de la cadena (seno): \\frac{d}{dx} \\sin(u) = \\cos(u) \\cdot u'")
                    Expr.BinaryExpr(
                        Expr.FunctionExpr(tok(TokenType.COS), listOf(u)),
                        tok(TokenType.MULTIPLY),
                        du
                    )
                }
                TokenType.COS -> {
                    evaluationSteps.add("Regla de la cadena (coseno): \\frac{d}{dx} \\cos(u) = -\\sin(u) \\cdot u'")
                    Expr.BinaryExpr(
                        Expr.UnaryExpr(tok(TokenType.MINUS), Expr.FunctionExpr(tok(TokenType.SIN), listOf(u))),
                        tok(TokenType.MULTIPLY),
                        du
                    )
                }
                TokenType.EXP -> {
                    evaluationSteps.add("Regla de la cadena (exponencial): \\frac{d}{dx} e^u = e^u \\cdot u'")
                    Expr.BinaryExpr(expr, tok(TokenType.MULTIPLY), du)
                }

                TokenType.LN -> {
                    evaluationSteps.add("Regla de la cadena (logaritmo): \\frac{d}{dx} \\ln(u) = \\frac{1}{u} \\cdot u'")
                    Expr.BinaryExpr(
                        Expr.BinaryExpr(num(1.0), tok(TokenType.DIVIDE), u),
                        tok(TokenType.MULTIPLY),
                        du
                    )
                }

                TokenType.SQRT -> {
                    evaluationSteps.add("Regla de la cadena (raíz): \\frac{d}{dx} \\sqrt{u} = \\frac{1}{2\\sqrt{u}} \\cdot u'")
                    Expr.BinaryExpr(
                        Expr.BinaryExpr(
                            num(1.0),
                            tok(TokenType.DIVIDE),
                            Expr.BinaryExpr(num(2.0), tok(TokenType.MULTIPLY), expr)
                        ),
                        tok(TokenType.MULTIPLY),
                        du
                    )
                }

                else -> num(0.0)
            }
        }

        else -> num(0.0)
    }

    private fun tok(type: TokenType, value: String = "") = Token(type, if(value.isEmpty()) type.name else value, 0)

    // ================================================================
    // UNARIOS
    // ================================================================

    private fun simplifyUnary(expr: Expr.UnaryExpr): Expr {
        val operand = simplifyPass(expr.operand)

        // --x → x (doble negación)
        if (expr.operator.type == TokenType.MINUS &&
            operand is Expr.UnaryExpr &&
            operand.operator.type == TokenType.MINUS) {
            return operand.operand
        }

        // -0 → 0
        if (expr.operator.type == TokenType.MINUS &&
            operand is Expr.NumberExpr && operand.value == 0.0) {
            return num(0.0)
        }

        // -(constante) → fold
        if (expr.operator.type == TokenType.MINUS && operand is Expr.NumberExpr) {
            return num(-operand.value)
        }

        return Expr.UnaryExpr(expr.operator, operand)
    }

    // ================================================================
    // BINARIOS (el corazón del pattern matching)
    // ================================================================

    private fun simplifyBinary(expr: Expr.BinaryExpr): Expr {
        val left = simplifyPass(expr.left)
        val right = simplifyPass(expr.right)
        val op = expr.operator.type
        val l = (left as? Expr.NumberExpr)?.value
        val r = (right as? Expr.NumberExpr)?.value

        // ── 1. Constant Folding Avanzado ──
        if (l != null && r != null) {
            val result = foldConstants(op, l, r)
            if (result != null) return num(result)
        }

        // ── 2. Identidades Aditivas ──
        if (op == TokenType.PLUS) {
            if (l == 0.0) return right
            if (r == 0.0) return left

            // x + x → 2*x
            if (DragonIR.structuralEquals(left, right)) {
                return Expr.BinaryExpr(num(2.0), tok(TokenType.MULTIPLY), left)
            }

            // nx + mx → (n+m)x (like terms)
            val likeResult = combineLikeTerms(left, right, TokenType.PLUS)
            if (likeResult != null) return likeResult

            // sin²(u) + cos²(u) → 1 (busca en ambas direcciones)
            val trigResult = matchPythagoreanIdentity(left, right)
            if (trigResult != null) return trigResult
        }

        // ── 3. Identidades Sustractivas ──
        if (op == TokenType.MINUS) {
            if (r == 0.0) return left
            if (l == 0.0) return Expr.UnaryExpr(tok(TokenType.MINUS), right)

            // x - x → 0
            if (DragonIR.structuralEquals(left, right)) return num(0.0)

            // nx - mx → (n-m)x
            val likeResult = combineLikeTerms(left, right, TokenType.MINUS)
            if (likeResult != null) return likeResult
        }

        // ── 4. Identidades Multiplicativas ──
        if (op == TokenType.MULTIPLY) {
            if (l == 0.0 || r == 0.0) return num(0.0)
            if (l == 1.0) return right
            if (r == 1.0) return left
            if (l == -1.0) return Expr.UnaryExpr(tok(TokenType.MINUS), right)
            if (r == -1.0) return Expr.UnaryExpr(tok(TokenType.MINUS), left)

            // x * x → x^2
            if (DragonIR.structuralEquals(left, right)) {
                return Expr.BinaryExpr(left, tok(TokenType.POWER), num(2.0))
            }
        }

        // ── 5. Identidades Divisivas ──
        if (op == TokenType.DIVIDE) {
            if (l == 0.0 && r != null && r != 0.0) return num(0.0)
            if (r == 1.0) return left

            // x / x → 1
            if (DragonIR.structuralEquals(left, right)) return num(1.0)
        }

        // ── 6. Potencias ──
        if (op == TokenType.POWER) {
            if (r == 0.0) return num(1.0)
            if (r == 1.0) return left
            if (l == 0.0 && r != null && r > 0) return num(0.0)
            if (l == 1.0) return num(1.0)
        }

        return Expr.BinaryExpr(left, expr.operator, right)
    }

    // ================================================================
    // FUNCIONES
    // ================================================================

    private fun simplifyFunction(expr: Expr.FunctionExpr): Expr {
        val args = expr.arguments.map { simplifyPass(it) }

        // Constant folding para funciones de un argumento
        if (args.size == 1 && args[0] is Expr.NumberExpr) {
            val v = (args[0] as Expr.NumberExpr).value
            val result = foldFunction(expr.name.type, v)
            if (result != null) return num(result)
        }

        // sin(0) → 0, cos(0) → 1, etc.
        if (args.size == 1) {
            val zeroResult = foldFunctionAtZero(expr.name.type, args[0])
            if (zeroResult != null) return zeroResult
        }

        return Expr.FunctionExpr(expr.name, args)
    }

    private fun simplifyAbs(expr: Expr.AbsExpr): Expr {
        val inner = simplifyPass(expr.inner)
        // |c| → c  (si c >= 0)
        if (inner is Expr.NumberExpr && inner.value >= 0) return inner
        // |c| → -c  (si c < 0)
        if (inner is Expr.NumberExpr && inner.value < 0) return num(-inner.value)
        return Expr.AbsExpr(inner)
    }

    // ================================================================
    // PATTERN: TÉRMINOS SEMEJANTES (Like Terms)
    // ================================================================

    /**
     * Extrae coeficiente y base de un término.
     * 3*x → (3.0, x),  x → (1.0, x),  -x → (-1.0, x)
     */
    private fun extractCoeffAndBase(expr: Expr): Pair<Double, Expr>? {
        return when {
            // n * base
            expr is Expr.BinaryExpr && expr.operator.type == TokenType.MULTIPLY
                    && expr.left is Expr.NumberExpr ->
                (expr.left as Expr.NumberExpr).value to expr.right

            // base * n
            expr is Expr.BinaryExpr && expr.operator.type == TokenType.MULTIPLY
                    && expr.right is Expr.NumberExpr ->
                (expr.right as Expr.NumberExpr).value to expr.left

            // -base → (-1, base)
            expr is Expr.UnaryExpr && expr.operator.type == TokenType.MINUS ->
                -1.0 to expr.operand

            // bare term → (1, term)
            else -> 1.0 to expr
        }
    }

    /**
     * Combina términos semejantes: nx ± mx → (n±m)x
     */
    private fun combineLikeTerms(a: Expr, b: Expr, op: TokenType): Expr? {
        val (ca, baseA) = extractCoeffAndBase(a) ?: return null
        val (cb, baseB) = extractCoeffAndBase(b) ?: return null

        if (!DragonIR.structuralEquals(baseA, baseB)) return null

        val newCoeff = when (op) {
            TokenType.PLUS -> ca + cb
            TokenType.MINUS -> ca - cb
            else -> return null
        }

        return when {
            newCoeff == 0.0 -> num(0.0)
            newCoeff == 1.0 -> baseA
            newCoeff == -1.0 -> Expr.UnaryExpr(tok(TokenType.MINUS), baseA)
            else -> Expr.BinaryExpr(num(newCoeff), tok(TokenType.MULTIPLY), baseA)
        }
    }

    // ================================================================
    // PATTERN: IDENTIDAD PITAGÓRICA  sin²(u) + cos²(u) → 1
    // ================================================================

    /**
     * Detecta sin²(u) + cos²(u) en cualquier orden.
     * Patrón: func(u)^2 donde func es sin/cos.
     */
    private fun matchPythagoreanIdentity(a: Expr, b: Expr): Expr? {
        val sinSq = extractTrigSquared(a, TokenType.SIN)
        val cosSq = extractTrigSquared(b, TokenType.COS)
        if (sinSq != null && cosSq != null && DragonIR.structuralEquals(sinSq, cosSq)) {
            return num(1.0)
        }

        // Orden inverso: cos² + sin²
        val cosSq2 = extractTrigSquared(a, TokenType.COS)
        val sinSq2 = extractTrigSquared(b, TokenType.SIN)
        if (cosSq2 != null && sinSq2 != null && DragonIR.structuralEquals(cosSq2, sinSq2)) {
            return num(1.0)
        }

        return null
    }

    /**
     * Si expr es func(u)^2, retorna u. Sino null.
     * Detecta: BinaryExpr(FunctionExpr(func, [u]), ^, 2)
     */
    private fun extractTrigSquared(expr: Expr, funcType: TokenType): Expr? {
        if (expr !is Expr.BinaryExpr) return null
        if (expr.operator.type != TokenType.POWER) return null
        if (expr.right !is Expr.NumberExpr || (expr.right as Expr.NumberExpr).value != 2.0) return null
        if (expr.left !is Expr.FunctionExpr) return null
        val func = expr.left as Expr.FunctionExpr
        if (func.name.type != funcType) return null
        if (func.arguments.size != 1) return null
        return func.arguments[0]
    }

    // ================================================================
    // CONSTANT FOLDING
    // ================================================================

    private fun foldConstants(op: TokenType, l: Double, r: Double): Double? = when (op) {
        TokenType.PLUS     -> l + r
        TokenType.MINUS    -> l - r
        TokenType.MULTIPLY -> l * r
        TokenType.DIVIDE   -> if (r != 0.0) l / r else null
        TokenType.POWER    -> Math.pow(l, r)
        TokenType.MODULO   -> if (r != 0.0) l % r else null
        else -> null
    }

    private fun foldFunction(type: TokenType, v: Double): Double? = when (type) {
        TokenType.SIN  -> Math.sin(v)
        TokenType.COS  -> Math.cos(v)
        TokenType.TAN  -> if (Math.cos(v) != 0.0) Math.tan(v) else null
        TokenType.SQRT -> if (v >= 0) Math.sqrt(v) else null
        TokenType.ABS  -> Math.abs(v)
        TokenType.LN   -> if (v > 0) Math.log(v) else null
        TokenType.EXP  -> Math.exp(v)
        TokenType.LOG  -> if (v > 0) Math.log10(v) else null
        TokenType.CEIL -> Math.ceil(v)
        TokenType.FLOOR -> Math.floor(v)
        TokenType.SIGN -> Math.signum(v)
        else -> null
    }

    /**
     * Identidades funcionales en x=0 y puntos especiales.
     */
    private fun foldFunctionAtZero(type: TokenType, arg: Expr): Expr? {
        if (arg !is Expr.NumberExpr) return null
        val v = arg.value
        return when {
            type == TokenType.SIN && v == 0.0 -> num(0.0)
            type == TokenType.COS && v == 0.0 -> num(1.0)
            type == TokenType.TAN && v == 0.0 -> num(0.0)
            type == TokenType.EXP && v == 0.0 -> num(1.0)
            type == TokenType.EXP && v == 1.0 -> Expr.ConstantExpr("e", tok(TokenType.E))
            type == TokenType.LN  && v == 1.0 -> num(0.0)
            type == TokenType.SQRT && v == 0.0 -> num(0.0)
            type == TokenType.SQRT && v == 1.0 -> num(1.0)
            else -> null
        }
    }

    // ================================================================
    // UTILIDADES
    // ================================================================

    private fun num(v: Double) = Expr.NumberExpr(v)
    private fun tok(type: TokenType) = Token(type, type.name, 0)

    companion object {
        /**
         * Convierte un Expr simplificado a string legible (para tests).
         */
        fun exprToString(expr: Expr): String = when (expr) {
            is Expr.NumberExpr -> {
                val v = expr.value
                if (v == Math.floor(v) && Math.abs(v) < 1e15) {
                    v.toLong().toString()
                } else {
                    // Formateo para evitar .0 en decimales exactos y notacion cientifica limpia
                    val s = "%.10f".format(v).trimEnd('0').trimEnd('.')
                    if (s == "-0") "0" else s
                }
            }
            is Expr.VariableExpr -> expr.name
            is Expr.ConstantExpr -> expr.name
            is Expr.UnaryExpr -> if (expr.operator.type == TokenType.MINUS) "(-${exprToString(expr.operand)})" else "${expr.operator.value}${exprToString(expr.operand)}"
            is Expr.BinaryExpr -> {
                val leftStr = exprToString(expr.left)
                val rightStr = exprToString(expr.right)

                when (expr.operator.type) {
                    TokenType.PLUS -> "($leftStr + $rightStr)"
                    TokenType.MINUS -> "($leftStr - $rightStr)"
                    TokenType.MULTIPLY -> {
                        // Multiplicación implícita: 2 * x -> 2x
                        if (expr.left is Expr.NumberExpr && expr.right is Expr.VariableExpr) {
                            "${exprToString(expr.left)}$rightStr"
                        } else if (expr.left is Expr.NumberExpr && expr.right is Expr.FunctionExpr) {
                            "${exprToString(expr.left)}$rightStr"
                        } else {
                            "($leftStr * $rightStr)"
                        }
                    }
                    TokenType.DIVIDE -> "($leftStr / $rightStr)"
                    TokenType.POWER -> "$leftStr^$rightStr"
                    else -> "($leftStr ${expr.operator.value} $rightStr)"
                }
            }
            is Expr.FunctionExpr -> "${expr.name.value}(${expr.arguments.joinToString(", ") { exprToString(it) }})"
            is Expr.DerivativeExpr -> "d/d${expr.variable.name}(${exprToString(expr.expression)})"
            is Expr.IntegralExpr -> "∫(${exprToString(expr.expression)}) d${expr.variable.name}"
            is Expr.AbsExpr -> "|${exprToString(expr.inner)}|"
            is Expr.AssignExpr -> "${expr.name} = ${exprToString(expr.value)}"
            is Expr.MatrixExpr -> "[matrix]"
            // DragonScript nodes
            is Expr.BlockExpr -> "{ ${expr.statements.joinToString("; ") { exprToString(it) }} }"
            is Expr.IfExpr -> "if(...) ... ${if (expr.elseBranch != null) "else ..." else ""}"
            is Expr.WhileExpr -> "while(...) ..."
            is Expr.FunctionDefExpr -> "def ${expr.name}(...) ..."
            is Expr.ReturnExpr -> "return ${exprToString(expr.value)}"
        }
    }
}
