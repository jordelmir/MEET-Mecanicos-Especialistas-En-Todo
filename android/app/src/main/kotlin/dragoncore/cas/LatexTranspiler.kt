package dragoncore.cas

import dragoncore.lexer.TokenType
import dragoncore.parser.Expr

/**
 * Elysium Vanguard — LatexTranspiler
 *
 * Transpila el AST de DragonScript a código LaTeX profesional.
 * Utilizado para el renderizado visual en celdas y el Modo Tutor.
 */
class LatexTranspiler {

    /**
     * Punto de entrada: convierte una expresión AST a LaTeX.
     */
    fun transpile(expr: Expr): String {
        return toLatex(expr)
    }

    private fun toLatex(expr: Expr): String = when (expr) {
        is Expr.NumberExpr -> {
            val v = expr.value
            if (v == Math.floor(v) && Math.abs(v) < 1e15) {
                v.toLong().toString()
            } else {
                "%.10f".format(v).trimEnd('0').trimEnd('.')
            }
        }

        is Expr.VariableExpr -> expr.name

        is Expr.ConstantExpr -> when (expr.name.lowercase()) {
            "pi" -> "\\pi"
            "e" -> "e"
            "phi" -> "\\phi"
            "inf" -> "\\infty"
            else -> expr.name
        }

        is Expr.UnaryExpr -> when (expr.operator.type) {
            TokenType.MINUS -> "-${wrap(expr.operand)}"
            TokenType.FACTORIAL -> "${toLatex(expr.operand)}!"
            else -> toLatex(expr.operand)
        }

        is Expr.BinaryExpr -> when (expr.operator.type) {
            TokenType.PLUS -> "${toLatex(expr.left)} + ${toLatex(expr.right)}"
            TokenType.MINUS -> "${toLatex(expr.left)} - ${toLatex(expr.right)}"
            TokenType.MULTIPLY -> {
                // Multiplicación implícita o con \cdot
                if (expr.left is Expr.NumberExpr && (expr.right is Expr.VariableExpr || expr.right is Expr.FunctionExpr)) {
                    "${toLatex(expr.left)}${toLatex(expr.right)}"
                } else {
                    "${toLatex(expr.left)} \\cdot ${toLatex(expr.right)}"
                }
            }
            TokenType.DIVIDE -> "\\frac{${toLatex(expr.left)}}{${toLatex(expr.right)}}"
            TokenType.POWER -> "{${toLatex(expr.left)}}^{${toLatex(expr.right)}}"
            else -> "(${toLatex(expr.left)} ${expr.operator.value} ${toLatex(expr.right)})"
        }

        is Expr.FunctionExpr -> {
            val name = expr.name.value.lowercase()
            val args = expr.arguments.joinToString(", ") { toLatex(it) }
            when (expr.name.type) {
                TokenType.SIN -> "\\sin(${toLatex(expr.arguments[0])})"
                TokenType.COS -> "\\cos(${toLatex(expr.arguments[0])})"
                TokenType.TAN -> "\\tan(${toLatex(expr.arguments[0])})"
                TokenType.LN -> "\\ln(${toLatex(expr.arguments[0])})"
                TokenType.LOG -> "\\log_{10}(${toLatex(expr.arguments[0])})"
                TokenType.EXP -> "e^{${toLatex(expr.arguments[0])}}"
                TokenType.SQRT -> "\\sqrt{${toLatex(expr.arguments[0])}}"
                else -> "\\text{$name}($args)"
            }
        }

        is Expr.AbsExpr -> "\\left| ${toLatex(expr.inner)} \\right|"

        is Expr.DerivativeExpr -> {
            "\\frac{d}{d${expr.variable.name}} \\left( ${toLatex(expr.expression)} \\right)"
        }

        is Expr.IntegralExpr -> {
            "\\int ${toLatex(expr.expression)} \\, d${expr.variable.name}"
        }

        is Expr.MatrixExpr -> {
            "\\begin{pmatrix} " + expr.rows.joinToString(" \\\\ ") { r ->
                r.joinToString(" & ") { toLatex(it) }
            } + " \\end{pmatrix}"
        }

        // DragonScript nodes (básico)
        is Expr.AssignExpr -> "${expr.name} = ${toLatex(expr.value)}"
        else -> "\\text{...}"
    }

    /** Envuelve en paréntesis si es necesario. */
    private fun wrap(expr: Expr): String {
        val s = toLatex(expr)
        return if (expr is Expr.BinaryExpr && (expr.operator.type == TokenType.PLUS || expr.operator.type == TokenType.MINUS)) {
            "($s)"
        } else s
    }
}
