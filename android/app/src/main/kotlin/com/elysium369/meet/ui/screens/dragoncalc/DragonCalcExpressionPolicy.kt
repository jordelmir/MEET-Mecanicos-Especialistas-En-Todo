package com.elysium369.meet.ui.screens.dragoncalc

import dragoncore.parser.Expr

/** Limits the imported DragonScript engine to bounded, interactive calculator expressions. */
internal object DragonCalcExpressionPolicy {
    fun requireInteractive(expr: Expr) {
        var nodes = 0
        fun visit(node: Expr, depth: Int) {
            require(depth <= 32 && ++nodes <= 256) { "Expresión demasiado compleja" }
            when (node) {
                is Expr.NumberExpr, is Expr.VariableExpr, is Expr.ConstantExpr -> Unit
                is Expr.BinaryExpr -> {
                    visit(node.left, depth + 1)
                    visit(node.right, depth + 1)
                }
                is Expr.UnaryExpr -> visit(node.operand, depth + 1)
                is Expr.FunctionExpr -> node.arguments.forEach { visit(it, depth + 1) }
                is Expr.AbsExpr -> visit(node.inner, depth + 1)
                is Expr.MatrixExpr -> node.rows.forEach { row -> row.forEach { visit(it, depth + 1) } }
                is Expr.AssignExpr -> visit(node.value, depth + 1)
                is Expr.DerivativeExpr -> visit(node.expression, depth + 1)
                is Expr.IntegralExpr -> throw IllegalArgumentException("Usa límites para calcular una integral")
                is Expr.BlockExpr, is Expr.IfExpr, is Expr.WhileExpr,
                is Expr.FunctionDefExpr, is Expr.ReturnExpr ->
                    throw IllegalArgumentException("Esta vista admite expresiones, no programas")
            }
        }
        visit(expr, 0)
    }
}
