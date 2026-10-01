package dragoncore.symbolic

import dragoncore.lexer.Token
import dragoncore.lexer.TokenType
import dragoncore.parser.Expr

/**
 * SymbolicIntegrator: Motor analítico para el cálculo de primitivas F(x) + C.
 * Implementa Vector [∫] — SÍNTESIS SIMBÓLICA.
 */
class SymbolicIntegrator {

    /**
     * Intenta encontrar la antiderivada de una expresión respecto a 'x'.
     */
    fun integrate(expr: Expr, variable: String = "x"): Expr {
        return when (expr) {
            is Expr.NumberExpr -> Expr.BinaryExpr(expr, Token(TokenType.MULTIPLY, "*", 0), Expr.VariableExpr(variable)) // int c dx = cx
            is Expr.VariableExpr -> {
                if (expr.name == variable) {
                    // Power Rule: int x dx = x^2 / 2
                    Expr.BinaryExpr(
                        Expr.BinaryExpr(expr, Token(TokenType.POWER, "^", 0), Expr.NumberExpr(2.0)),
                        Token(TokenType.DIVIDE, "/", 0),
                        Expr.NumberExpr(2.0)
                    )
                } else {
                    Expr.BinaryExpr(expr, Token(TokenType.MULTIPLY, "*", 0), Expr.VariableExpr(variable)) // int y dx = yx
                }
            }
            is Expr.BinaryExpr -> {
                when (expr.operator.value) {
                    "+" -> Expr.BinaryExpr(integrate(expr.left, variable), Token(TokenType.PLUS, "+", 0), integrate(expr.right, variable))
                    "-" -> Expr.BinaryExpr(integrate(expr.left, variable), Token(TokenType.MINUS, "-", 0), integrate(expr.right, variable))
                    "^" -> {
                        // Power Rule General: int x^n dx = x^(n+1) / (n+1)
                        if (expr.left is Expr.VariableExpr && expr.left.name == variable && expr.right is Expr.NumberExpr) {
                            val n = expr.right.value
                            if (n == -1.0) {
                                // int x^-1 dx = ln|x|
                                Expr.FunctionExpr(Token(TokenType.LN, "ln", 0), listOf(expr.left))
                            } else {
                                Expr.BinaryExpr(
                                    Expr.BinaryExpr(expr.left, Token(TokenType.POWER, "^", 0), Expr.NumberExpr(n + 1)),
                                    Token(TokenType.DIVIDE, "/", 0),
                                    Expr.NumberExpr(n + 1)
                                )
                            }
                        } else {
                            // Heurística de sustitución u (Stub)
                            integrateBySubstitution(expr, variable)
                        }
                    }
                    else -> integrateBySubstitution(expr, variable)
                }
            }
            is Expr.FunctionExpr -> integrateFunction(expr, variable)
            else -> expr // Fallback
        }
    }

    private fun integrateFunction(expr: Expr.FunctionExpr, variable: String): Expr {
        return when (expr.name.value) {
            "sin" -> Expr.UnaryExpr(Token(TokenType.MINUS, "-", 0), Expr.FunctionExpr(Token(TokenType.COS, "cos", 0), expr.arguments))
            "cos" -> Expr.FunctionExpr(Token(TokenType.SIN, "sin", 0), expr.arguments)
            "exp" -> expr // assuming arg is x
            else -> integrateBySubstitution(expr, variable)
        }
    }

    private fun integrateBySubstitution(expr: Expr, variable: String): Expr {
        // En un motor real, aquí buscaríamos f(u) * u'
        // Por ahora, retornamos un símbolo de "Integral Pendiente" o el original simplificado
        return Expr.FunctionExpr(Token(TokenType.INTEGRAL, "∫", 0), listOf(expr, Expr.VariableExpr("d$variable")))
    }
}
