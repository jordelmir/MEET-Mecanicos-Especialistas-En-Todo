package dragoncore.cas

import dragoncore.lexer.Token
import dragoncore.lexer.TokenType
import dragoncore.parser.Expr
import dragoncore.evaluator.DragonContext
import dragoncore.evaluator.DragonEvaluator

/**
 * Elysium Vanguard — CalculusEngine
 *
 * Motor de cálculo avanzado:
 *   - Derivación simbólica (cadena, producto, cociente)
 *   - Integración numérica (Gauss-Kronrod G7-K15 adaptativo)
 *   - Soporte para límites infinitos
 */
class CalculusEngine {

    private val optimizer = SymbolicOptimizer()

    // ================================================================
    // DERIVACIÓN SIMBÓLICA
    // ================================================================

    fun derive(expr: Expr, variable: String): Expr {
        return optimizer.simplify(deriveRaw(expr, variable))
    }

    private fun deriveRaw(expr: Expr, v: String): Expr {
        return when (expr) {
            is Expr.NumberExpr -> Expr.NumberExpr(0.0)
            is Expr.ConstantExpr -> Expr.NumberExpr(0.0)
            is Expr.VariableExpr -> if (expr.name == v) Expr.NumberExpr(1.0) else Expr.NumberExpr(0.0)
            is Expr.UnaryExpr -> Expr.UnaryExpr(expr.operator, deriveRaw(expr.operand, v))
            is Expr.BinaryExpr -> deriveBinary(expr, v)
            is Expr.FunctionExpr -> deriveFunction(expr, v)
            is Expr.AbsExpr -> {
                val df = deriveRaw(expr.inner, v)
                Expr.BinaryExpr(
                    Expr.BinaryExpr(expr.inner, tok(TokenType.DIVIDE), Expr.AbsExpr(expr.inner)),
                    tok(TokenType.MULTIPLY), df
                )
            }
            is Expr.AssignExpr -> deriveRaw(expr.value, v)
            is Expr.MatrixExpr -> Expr.NumberExpr(0.0)
            // DragonScript nodes: no se derivan
            is Expr.BlockExpr, is Expr.IfExpr, is Expr.WhileExpr,
            is Expr.FunctionDefExpr, is Expr.ReturnExpr -> Expr.NumberExpr(0.0)
            is Expr.DerivativeExpr -> {
                // d(d(f,x), y) -> derivar el resultado de d(f,x) respecto a y
                val firstDeriv = deriveRaw(expr.expression, expr.variable.name)
                deriveRaw(firstDeriv, v)
            }
            is Expr.IntegralExpr -> {
                // d/dx [ int(f, y) dy ]
                if (expr.variable.name == v) {
                    expr.expression // Teorema Fundamental del Calculo
                } else {
                    // Diferenciacion bajo el signo integral (Regla de Leibniz simplificada)
                    Expr.IntegralExpr(deriveRaw(expr.expression, v), expr.variable)
                }
            }
        }
    }

    private fun deriveBinary(expr: Expr.BinaryExpr, v: String): Expr {
        val l = expr.left; val r = expr.right
        val dl = deriveRaw(l, v); val dr = deriveRaw(r, v)

        return when (expr.operator.type) {
            TokenType.PLUS -> Expr.BinaryExpr(dl, tok(TokenType.PLUS), dr)
            TokenType.MINUS -> Expr.BinaryExpr(dl, tok(TokenType.MINUS), dr)

            // Producto: f'g + fg'
            TokenType.MULTIPLY -> Expr.BinaryExpr(
                Expr.BinaryExpr(l, tok(TokenType.MULTIPLY), dr),
                tok(TokenType.PLUS),
                Expr.BinaryExpr(dl, tok(TokenType.MULTIPLY), r)
            )

            // Cociente: (f'g - fg') / g²
            TokenType.DIVIDE -> Expr.BinaryExpr(
                Expr.BinaryExpr(
                    Expr.BinaryExpr(dl, tok(TokenType.MULTIPLY), r),
                    tok(TokenType.MINUS),
                    Expr.BinaryExpr(l, tok(TokenType.MULTIPLY), dr)
                ),
                tok(TokenType.DIVIDE),
                Expr.BinaryExpr(r, tok(TokenType.POWER), Expr.NumberExpr(2.0))
            )

            // Potencia
            TokenType.POWER -> {
                val constExp = !containsVar(r, v)
                val constBase = !containsVar(l, v)
                when {
                    // x^n → n*x^(n-1)*dx
                    constExp -> Expr.BinaryExpr(
                        Expr.BinaryExpr(r, tok(TokenType.MULTIPLY),
                            Expr.BinaryExpr(l, tok(TokenType.POWER),
                                Expr.BinaryExpr(r, tok(TokenType.MINUS), Expr.NumberExpr(1.0)))),
                        tok(TokenType.MULTIPLY), dl
                    )
                    // a^f → a^f * ln(a) * df
                    constBase -> Expr.BinaryExpr(
                        Expr.BinaryExpr(
                            Expr.BinaryExpr(l, tok(TokenType.POWER), r),
                            tok(TokenType.MULTIPLY),
                            Expr.FunctionExpr(tok(TokenType.LN), listOf(l))
                        ),
                        tok(TokenType.MULTIPLY), dr
                    )
                    // f^g general
                    else -> Expr.BinaryExpr(
                        Expr.BinaryExpr(l, tok(TokenType.POWER), r),
                        tok(TokenType.MULTIPLY),
                        Expr.BinaryExpr(
                            Expr.BinaryExpr(dr, tok(TokenType.MULTIPLY), Expr.FunctionExpr(tok(TokenType.LN), listOf(l))),
                            tok(TokenType.PLUS),
                            Expr.BinaryExpr(Expr.BinaryExpr(r, tok(TokenType.MULTIPLY), dl), tok(TokenType.DIVIDE), l)
                        )
                    )
                }
            }
            else -> Expr.NumberExpr(0.0)
        }
    }

    private fun deriveFunction(expr: Expr.FunctionExpr, v: String): Expr {
        if (expr.arguments.isEmpty()) return Expr.NumberExpr(0.0)
        val u = expr.arguments[0]
        val du = deriveRaw(u, v)

        val fprime = when (expr.name.type) {
            TokenType.SIN -> Expr.FunctionExpr(tok(TokenType.COS), listOf(u))
            TokenType.COS -> Expr.UnaryExpr(tok(TokenType.MINUS), Expr.FunctionExpr(tok(TokenType.SIN), listOf(u)))
            TokenType.TAN -> Expr.BinaryExpr(
                Expr.NumberExpr(1.0), tok(TokenType.DIVIDE),
                Expr.BinaryExpr(Expr.FunctionExpr(tok(TokenType.COS), listOf(u)), tok(TokenType.POWER), Expr.NumberExpr(2.0))
            )
            TokenType.LN -> Expr.BinaryExpr(Expr.NumberExpr(1.0), tok(TokenType.DIVIDE), u)
            TokenType.LOG -> Expr.BinaryExpr(
                Expr.NumberExpr(1.0), tok(TokenType.DIVIDE),
                Expr.BinaryExpr(u, tok(TokenType.MULTIPLY), Expr.FunctionExpr(tok(TokenType.LN), listOf(Expr.NumberExpr(10.0))))
            )
            TokenType.EXP -> Expr.FunctionExpr(tok(TokenType.EXP), listOf(u))
            TokenType.SQRT -> Expr.BinaryExpr(
                Expr.NumberExpr(1.0), tok(TokenType.DIVIDE),
                Expr.BinaryExpr(Expr.NumberExpr(2.0), tok(TokenType.MULTIPLY), Expr.FunctionExpr(tok(TokenType.SQRT), listOf(u)))
            )
            TokenType.ASIN -> Expr.BinaryExpr(Expr.NumberExpr(1.0), tok(TokenType.DIVIDE),
                Expr.FunctionExpr(tok(TokenType.SQRT), listOf(
                    Expr.BinaryExpr(Expr.NumberExpr(1.0), tok(TokenType.MINUS), Expr.BinaryExpr(u, tok(TokenType.POWER), Expr.NumberExpr(2.0)))
                )))
            TokenType.ACOS -> Expr.UnaryExpr(tok(TokenType.MINUS), Expr.BinaryExpr(Expr.NumberExpr(1.0), tok(TokenType.DIVIDE),
                Expr.FunctionExpr(tok(TokenType.SQRT), listOf(
                    Expr.BinaryExpr(Expr.NumberExpr(1.0), tok(TokenType.MINUS), Expr.BinaryExpr(u, tok(TokenType.POWER), Expr.NumberExpr(2.0)))
                ))))
            TokenType.ATAN -> Expr.BinaryExpr(Expr.NumberExpr(1.0), tok(TokenType.DIVIDE),
                Expr.BinaryExpr(Expr.NumberExpr(1.0), tok(TokenType.PLUS), Expr.BinaryExpr(u, tok(TokenType.POWER), Expr.NumberExpr(2.0))))
            TokenType.SINH -> Expr.FunctionExpr(tok(TokenType.COSH), listOf(u))
            TokenType.COSH -> Expr.FunctionExpr(tok(TokenType.SINH), listOf(u))
            TokenType.ABS -> Expr.FunctionExpr(tok(TokenType.SIGN), listOf(u))
            else -> return Expr.NumberExpr(0.0)
        }
        // Chain rule: f'(u) * u'
        return Expr.BinaryExpr(fprime, tok(TokenType.MULTIPLY), du)
    }

    private fun containsVar(expr: Expr, v: String): Boolean = when (expr) {
        is Expr.NumberExpr, is Expr.ConstantExpr, is Expr.MatrixExpr -> false
        is Expr.VariableExpr -> expr.name == v
        is Expr.UnaryExpr -> containsVar(expr.operand, v)
        is Expr.BinaryExpr -> containsVar(expr.left, v) || containsVar(expr.right, v)
        is Expr.FunctionExpr -> expr.arguments.any { containsVar(it, v) }
        is Expr.AbsExpr -> containsVar(expr.inner, v)
        is Expr.AssignExpr -> containsVar(expr.value, v)
        // DragonScript nodes
        is Expr.BlockExpr -> expr.statements.any { containsVar(it, v) }
        is Expr.IfExpr -> containsVar(expr.condition, v) || containsVar(expr.thenBranch, v) || (expr.elseBranch != null && containsVar(expr.elseBranch, v))
        is Expr.WhileExpr -> containsVar(expr.condition, v) || containsVar(expr.body, v)
        is Expr.FunctionDefExpr -> containsVar(expr.body, v)
        is Expr.ReturnExpr -> containsVar(expr.value, v)
        is Expr.DerivativeExpr -> containsVar(expr.expression, v) || expr.variable.name == v
        is Expr.IntegralExpr -> containsVar(expr.expression, v) || expr.variable.name == v
    }

    // ================================================================
    // INTEGRACIÓN NUMÉRICA (Gauss-Kronrod G7-K15)
    // ================================================================

    fun integrate(expr: Expr, variable: String, a: Double, b: Double, context: DragonContext): Double {
        val evaluator = DragonEvaluator()
        val f: (Double) -> Double = { x ->
            context.setVariable(variable, x)
            evaluator.evaluate(expr, context)
        }
        return when {
            a.isInfinite() && b.isInfinite() -> intInf(f, -0.999, 0.0) + intInf(f, 0.0, 0.999)
            a.isInfinite() -> intInf(f, -0.999, mapFinite(b))
            b.isInfinite() -> intInf(f, mapFinite(a), 0.999)
            a == b -> 0.0
            a > b -> -adaptiveGK(f, b, a, 1e-8, 15)
            else -> adaptiveGK(f, a, b, 1e-8, 15)
        }
    }

    /** Integral con sustitución x = t/(1-t²) para límites infinitos. */
    private fun intInf(f: (Double) -> Double, tMin: Double, tMax: Double): Double {
        val g: (Double) -> Double = { t ->
            if (Math.abs(t) >= 1.0 - 1e-15) 0.0
            else {
                val x = t / (1.0 - t * t)
                val dx = (1.0 + t * t) / ((1.0 - t * t) * (1.0 - t * t))
                val fv = try { f(x) } catch (_: Exception) { 0.0 }
                if (fv.isFinite()) fv * dx else 0.0
            }
        }
        return adaptiveGK(g, tMin, tMax, 1e-8, 15)
    }

    private fun mapFinite(x: Double): Double {
        // Map finite x to t via x = t/(1-t²), solve for t ≈ clamp
        val d = Math.sqrt(1.0 + 4.0 * x * x)
        return (if (x >= 0) (-1.0 + d) / (2.0 * x + 1e-300)
                else (-1.0 - d) / (2.0 * x - 1e-300)).coerceIn(-0.999, 0.999)
    }

    private fun adaptiveGK(f: (Double) -> Double, a: Double, b: Double, tol: Double, depth: Int): Double {
        val (g7, k15) = gk15(f, a, b)
        return if (Math.abs(k15 - g7) < tol || depth <= 0) k15
        else {
            val m = (a + b) / 2.0
            adaptiveGK(f, a, m, tol / 2, depth - 1) + adaptiveGK(f, m, b, tol / 2, depth - 1)
        }
    }

    private fun gk15(f: (Double) -> Double, a: Double, b: Double): Pair<Double, Double> {
        val c = (a + b) / 2.0; val h = (b - a) / 2.0
        var g7 = 0.0; var k15 = 0.0
        for (i in XK.indices) {
            val f1 = try { f(c + h * XK[i]) } catch (_: Exception) { 0.0 }
            val f2 = if (XK[i] == 0.0) 0.0 else try { f(c - h * XK[i]) } catch (_: Exception) { 0.0 }
            val s = if (XK[i] == 0.0) f1 else f1 + f2
            k15 += WK[i] * s
            if (i % 2 == 1 && i / 2 < WG.size) g7 += WG[i / 2] * s
        }
        return (g7 * h) to (k15 * h)
    }

    private fun tok(type: TokenType) = Token(type, type.name, 0)

    companion object {
        private val XK = doubleArrayOf(0.0, 0.20778495500789847, 0.40584515137739716, 0.58608723546769113,
            0.74153118559939444, 0.86486442335976907, 0.94910791234275852, 0.99145537112081264)
        private val WK = doubleArrayOf(0.20948214108472783, 0.20443294007529889, 0.19035057806478541, 0.16900472663926790,
            0.14065325971552592, 0.10479001032225019, 0.06309209262997855, 0.02293532201052922)
        private val WG = doubleArrayOf(0.41795918367346939, 0.38183005050511894, 0.27970539148927664, 0.12948496616886969)
    }
}
