package dragoncore.cas

import dragoncore.lexer.Token
import dragoncore.lexer.TokenType
import dragoncore.parser.Expr
import kotlin.math.*

/**
 * Elysium Vanguard — SymbolicIntegrator
 *
 * Motor de integración simbólica analítica.
 * Busca la primitiva F(x) tal que d/dx F(x) = f(x).
 */
class SymbolicIntegrator {

    private val optimizer = SymbolicOptimizer()
    val evaluationSteps = mutableListOf<String>()

    /**
     * Intenta encontrar la integral indefinida de [expr] respecto a [wrt].
     * Retorna F(x) + C.
     */
    fun integrate(expr: Expr, wrt: Expr.VariableExpr): Expr {
        evaluationSteps.clear()
        val result = integrateRecursive(expr, wrt)

        // Agregar constante de integración + C
        return Expr.BinaryExpr(
            optimizer.simplify(result),
            tok(TokenType.PLUS),
            Expr.VariableExpr("C")
        )
    }

    private fun integrateRecursive(expr: Expr, v: Expr.VariableExpr): Expr {
        // Reglas básicas de integración
        return when (expr) {
            is Expr.NumberExpr -> {
                evaluationSteps.add("Integral de una constante: \\int ${expr.value} \\, d${v.name} = ${expr.value}${v.name}")
                Expr.BinaryExpr(expr, tok(TokenType.MULTIPLY), v)
            }

            is Expr.VariableExpr -> {
                if (expr.name == v.name) {
                    evaluationSteps.add("Regla de la potencia (n=1): \\int ${v.name} \\, d${v.name} = \\frac{${v.name}^2}{2}")
                    Expr.BinaryExpr(
                        Expr.BinaryExpr(v, tok(TokenType.POWER), Expr.NumberExpr(2.0)),
                        tok(TokenType.DIVIDE),
                        Expr.NumberExpr(2.0)
                    )
                } else {
                    evaluationSteps.add("Integral de variable independiente: \\int ${expr.name} \\, d${v.name} = ${expr.name}${v.name}")
                    Expr.BinaryExpr(expr, tok(TokenType.MULTIPLY), v)
                }
            }

            is Expr.BinaryExpr -> when (expr.operator.type) {
                TokenType.PLUS -> {
                    evaluationSteps.add("Regla de la suma: \\int (u+v)dx = \\int u dx + \\int v dx")
                    Expr.BinaryExpr(
                        integrateRecursive(expr.left, v),
                        tok(TokenType.PLUS),
                        integrateRecursive(expr.right, v)
                    )
                }
                TokenType.MINUS -> {
                    evaluationSteps.add("Regla de la resta: \\int (u-v)dx = \\int u dx - \\int v dx")
                    Expr.BinaryExpr(
                        integrateRecursive(expr.left, v),
                        tok(TokenType.MINUS),
                        integrateRecursive(expr.right, v)
                    )
                }
                TokenType.MULTIPLY -> handleProduct(expr, v)
                TokenType.DIVIDE -> handleDivide(expr, v)
                TokenType.POWER -> handlePower(expr, v)
                else -> Expr.IntegralExpr(expr, v) // No se puede integrar analíticamente (por ahora)
            }

            is Expr.FunctionExpr -> handleFunction(expr, v)

            is Expr.UnaryExpr -> {
                if (expr.operator.type == TokenType.MINUS) {
                    Expr.UnaryExpr(tok(TokenType.MINUS), integrateRecursive(expr.operand, v))
                } else {
                    Expr.IntegralExpr(expr, v)
                }
            }

            else -> Expr.IntegralExpr(expr, v)
        }
    }

    private fun handlePower(expr: Expr.BinaryExpr, v: Expr.VariableExpr): Expr {
        val base = expr.left
        val expo = expr.right

        // Caso base: x^n
        if (base is Expr.VariableExpr && base.name == v.name && !containsVar(expo, v.name)) {
            // int(x^n) = x^(n+1)/(n+1)  EXCEPTO n = -1
            if (expo is Expr.NumberExpr && expo.value == -1.0) {
                evaluationSteps.add("Integral logarítmica: \\int \\frac{1}{${v.name}} \\, d${v.name} = \\ln|${v.name}|")
                return Expr.FunctionExpr(tok(TokenType.LN), listOf(base))
            }
            evaluationSteps.add("Regla de la potencia: \\int ${v.name}^n \\, d${v.name} = \\frac{${v.name}^{n+1}}{n+1}")
            val plusOne = Expr.BinaryExpr(expo, tok(TokenType.PLUS), Expr.NumberExpr(1.0))
            return Expr.BinaryExpr(
                Expr.BinaryExpr(base, tok(TokenType.POWER), plusOne),
                tok(TokenType.DIVIDE),
                plusOne
            )
        }

        // Sustitución u simple: (ax + b)^n
        // TODO: Implementar heurística u-substitution general

        return Expr.IntegralExpr(expr, v)
    }

    private fun handleFunction(expr: Expr.FunctionExpr, v: Expr.VariableExpr): Expr {
        if (expr.arguments.size != 1) return Expr.IntegralExpr(expr, v)
        val u = expr.arguments[0]

        // Caso simple: f(x)
        if (u is Expr.VariableExpr && u.name == v.name) {
            return when (expr.name.type) {
                TokenType.SIN -> {
                    evaluationSteps.add("Integral del seno: \\int \\sin(${v.name}) \\, d${v.name} = -\\cos(${v.name})")
                    Expr.UnaryExpr(tok(TokenType.MINUS), Expr.FunctionExpr(tok(TokenType.COS), listOf(u)))
                }
                TokenType.COS -> {
                    evaluationSteps.add("Integral del coseno: \\int \\cos(${v.name}) \\, d${v.name} = \\sin(${v.name})")
                    Expr.FunctionExpr(tok(TokenType.SIN), listOf(u))
                }
                TokenType.EXP -> {
                    evaluationSteps.add("Integral de la exponencial: \\int e^{${v.name}} \\, d${v.name} = e^{${v.name}}")
                    Expr.FunctionExpr(tok(TokenType.EXP), listOf(u))
                }
                TokenType.SEC -> { /* tan(x) */ Expr.IntegralExpr(expr, v) } // Requiere mas reglas
                else -> Expr.IntegralExpr(expr, v)
            }
        }

        return Expr.IntegralExpr(expr, v)
    }

    private fun handleProduct(expr: Expr.BinaryExpr, v: Expr.VariableExpr): Expr {
        val l = expr.left
        val r = expr.right

        // Constante por funcion: int(k*f) = k * int(f)
        if (!containsVar(l, v.name)) return Expr.BinaryExpr(l, tok(TokenType.MULTIPLY), integrateRecursive(r, v))
        if (!containsVar(r, v.name)) return Expr.BinaryExpr(r, tok(TokenType.MULTIPLY), integrateRecursive(l, v))

        // U-Substitution Heuristica: int( g'(x) * f(g(x)) )
        // Buscamos si l es (aproximadamente) la derivada de una parte de r, o viceversa.

        return Expr.IntegralExpr(expr, v)
    }

    private fun handleDivide(expr: Expr.BinaryExpr, v: Expr.VariableExpr): Expr {
        val l = expr.left
        val r = expr.right

        // int(f/k) = (1/k) * int(f)
        if (!containsVar(r, v.name)) return Expr.BinaryExpr(integrateRecursive(l, v), tok(TokenType.DIVIDE), r)

        // Logaritmica: int( u'/u ) = ln|u|
        // TODO: Implementar verificacion de u'/u

        return Expr.IntegralExpr(expr, v)
    }

    private fun containsVar(expr: Expr, varName: String): Boolean = when (expr) {
        is Expr.NumberExpr, is Expr.ConstantExpr -> false
        is Expr.VariableExpr -> expr.name == varName
        is Expr.UnaryExpr -> containsVar(expr.operand, varName)
        is Expr.BinaryExpr -> containsVar(expr.left, varName) || containsVar(expr.right, varName)
        is Expr.FunctionExpr -> expr.arguments.any { containsVar(it, varName) }
        is Expr.AbsExpr -> containsVar(expr.inner, varName)
        is Expr.DerivativeExpr -> containsVar(expr.expression, varName) || expr.variable.name == varName
        is Expr.IntegralExpr -> containsVar(expr.expression, varName) || expr.variable.name == varName
        else -> false
    }

    private fun tok(type: TokenType) = Token(type, type.name, 0)
}
