package cr.dragon.calc.export

import dragoncore.lexer.TokenType
import dragoncore.parser.Expr

object LatexGenerator {

    fun toLatex(expr: Expr): String {
        return when (expr) {
            is Expr.NumberExpr -> {
                val v = expr.value
                if (v == v.toLong().toDouble() && !v.isInfinite()) v.toLong().toString() else v.toString()
            }
            is Expr.VariableExpr -> expr.name
            is Expr.ConstantExpr -> {
                when (expr.token.type) {
                    TokenType.PI -> "\\pi"
                    TokenType.E -> "e"
                    TokenType.PHI -> "\\phi"
                    TokenType.INF -> "\\infty"
                    TokenType.IMAGINARY -> "i"
                    else -> expr.name
                }
            }
            is Expr.UnaryExpr -> {
                val op = expr.operator.value
                val inner = toLatex(expr.operand)
                if (expr.isPostfix) "{$inner}{$op}" else "{$op}{$inner}"
            }
            is Expr.BinaryExpr -> {
                val l = toLatex(expr.left)
                val r = toLatex(expr.right)
                when (expr.operator.type) {
                    TokenType.DIVIDE -> "\\frac{$l}{$r}"
                    TokenType.POWER -> "{$l}^{$r}"
                    TokenType.MULTIPLY -> "{$l} \\cdot {$r}"
                    TokenType.LESS_EQUAL -> "{$l} \\le {$r}"
                    TokenType.GREATER_EQUAL -> "{$l} \\ge {$r}"
                    TokenType.NOT_EQUALS -> "{$l} \\neq {$r}"
                    else -> "{$l} ${expr.operator.value} {$r}"
                }
            }
            is Expr.FunctionExpr -> {
                val funcType = expr.name.type
                val args = expr.arguments.map { toLatex(it) }
                val argString = args.joinToString(", ")
                when (funcType) {
                    TokenType.INTEGRAL -> {
                        if (args.size == 1) "\\int {${args[0]}} \\, dx"
                        else if (args.size >= 2) "\\int {${args[0]}} \\, d${args[1]}"
                        else "\\int {$argString} \\, dx"
                    }
                    TokenType.DERIVATIVE -> {
                        if (args.size == 1) "\\frac{d}{dx}\\left( ${args[0]} \\right)"
                        else if (args.size >= 2) "\\frac{d}{d${args[1]}}\\left( ${args[0]} \\right)"
                        else "\\frac{d}{dx}\\left( $argString \\right)"
                    }
                    TokenType.SIN -> "\\sin\\left($argString\\right)"
                    TokenType.COS -> "\\cos\\left($argString\\right)"
                    TokenType.TAN -> "\\tan\\left($argString\\right)"
                    TokenType.ASIN -> "\\arcsin\\left($argString\\right)"
                    TokenType.ACOS -> "\\arccos\\left($argString\\right)"
                    TokenType.ATAN -> "\\arctan\\left($argString\\right)"
                    TokenType.SINH -> "\\sinh\\left($argString\\right)"
                    TokenType.COSH -> "\\cosh\\left($argString\\right)"
                    TokenType.TANH -> "\\tanh\\left($argString\\right)"
                    TokenType.LN -> "\\ln\\left($argString\\right)"
                    TokenType.LOG -> "\\log_{10}\\left($argString\\right)"
                    TokenType.LOG2 -> "\\log_{2}\\left($argString\\right)"
                    TokenType.SQRT -> "\\sqrt{$argString}"
                    TokenType.CBRT -> "\\sqrt[3]{$argString}"
                    TokenType.ABS -> "\\left| $argString \\right|"
                    else -> "\\text{${expr.name.value}}\\left($argString\\right)"
                }
            }
            is Expr.MatrixExpr -> {
                val rowsLatex = expr.rows.joinToString(" \\\\ ") { row ->
                    row.joinToString(" & ") { toLatex(it) }
                }
                "\\begin{bmatrix} $rowsLatex \\end{bmatrix}"
            }
            is Expr.AssignExpr -> "${expr.name} = ${toLatex(expr.value)}"
            is Expr.AbsExpr -> "\\left| ${toLatex(expr.inner)} \\right|"
            is Expr.BlockExpr -> {
                "\\begin{cases} " + expr.statements.joinToString(" \\\\ ") { toLatex(it) } + " \\end{cases}"
            }
            is Expr.IfExpr -> {
                val base = "\\text{if } ${toLatex(expr.condition)} \\text{ then } ${toLatex(expr.thenBranch)}"
                if (expr.elseBranch != null) "$base \\text{ else } ${toLatex(expr.elseBranch)}" else base
            }
            is Expr.WhileExpr -> "\\text{while } ${toLatex(expr.condition)} \\text{ do } ${toLatex(expr.body)}"
            is Expr.FunctionDefExpr -> {
                val params = expr.params.joinToString(", ")
                "\\text{def } ${expr.name}($params) = ${toLatex(expr.body)}"
            }
            is Expr.ReturnExpr -> "\\text{return } ${toLatex(expr.value)}"
            else -> expr.toString()
        }
    }
}
