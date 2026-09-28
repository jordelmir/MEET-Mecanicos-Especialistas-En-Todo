package dragoncore.parser

import dragoncore.lexer.Token

/**
 * Dragon Core - ASTNodes
 *
 * Definición de los nodos del Árbol de Sintaxis Abstracta (AST).
 * Cada nodo representa una constructo matemático o de programación.
 */
sealed class Expr {

    /** Numeros: 1.23, 100, etc. */
    data class NumberExpr(val value: Double) : Expr()

    /** Variables: x, y, DragonScore, etc. */
    data class VariableExpr(val name: String) : Expr()

    /** Constantes predefinidas: PI, E, PHI, i, INF */
    data class ConstantExpr(val name: String, val token: Token) : Expr()

    /** Operaciones Binarias: 1 + 2, x * y, a ^ b */
    data class BinaryExpr(
        val left: Expr,
        val operator: Token,
        val right: Expr
    ) : Expr()

    /** Operaciones Unarias: -x, +x, x! (factorial), ~x */
    data class UnaryExpr(
        val operator: Token,
        val operand: Expr,
        val isPostfix: Boolean = false
    ) : Expr()

    /** Llamadas a Funciones: sin(x), log(10, 2), myFunc(a, b) */
    data class FunctionExpr(
        val name: Token,
        val arguments: List<Expr>
    ) : Expr()

    /** Valor Absoluto: |x| */
    data class AbsExpr(val inner: Expr) : Expr()

    /** Matrices: [1,2; 3,4] */
    data class MatrixExpr(val rows: List<List<Expr>>) : Expr()

    /** Derivada Simbólica: d(y, x) */
    data class DerivativeExpr(
        val expression: Expr,
        val variable: VariableExpr
    ) : Expr()

    /** Integral Simbólica: int(y, x) */
    data class IntegralExpr(
        val expression: Expr,
        val variable: VariableExpr
    ) : Expr()

    // ── DRAGONSCRIPT (Sentencias de Control) ──

    /** Asignación: x = 10 */
    data class AssignExpr(val name: String, val value: Expr) : Expr()

    /** Bloque de sentencias: { a=1; b=2; a+b } */
    data class BlockExpr(val statements: List<Expr>) : Expr()

    /** Sentencia Condicional: if(cond) then [else else] */
    data class IfExpr(
        val condition: Expr,
        val thenBranch: Expr,
        val elseBranch: Expr? = null
    ) : Expr()

    /** Bucle: while(cond) body */
    data class WhileExpr(
        val condition: Expr,
        val body: Expr
    ) : Expr()

    /** Definición de Función: def add(a, b) { a + b } */
    data class FunctionDefExpr(
        val name: String,
        val params: List<String>,
        val body: Expr
    ) : Expr()

    /** Retorno de valor: return x */
    data class ReturnExpr(val value: Expr) : Expr()

    /** Convierte el AST de vuelta a una cadena de DragonScript ejecutable. */
    fun toDragonScript(): String = when (this) {
        is NumberExpr -> {
            val s = value.toString()
            if (s.endsWith(".0")) s.substring(0, s.length - 2) else s
        }
        is VariableExpr -> name
        is ConstantExpr -> name
        is BinaryExpr -> "(${left.toDragonScript()} ${operator.value} ${right.toDragonScript()})"
        is UnaryExpr -> if (isPostfix) "${operand.toDragonScript()}${operator.value}" else "${operator.value}${operand.toDragonScript()}"
        is FunctionExpr -> "${name.value}(${arguments.joinToString(", ") { it.toDragonScript() }})"
        is AbsExpr -> "|${inner.toDragonScript()}|"
        is MatrixExpr -> "[${rows.joinToString("; ") { row -> row.joinToString(", ") { it.toDragonScript() } }}]"
        is DerivativeExpr -> "d(${expression.toDragonScript()}, ${variable.name})"
        is IntegralExpr -> "int(${expression.toDragonScript()}, ${variable.name})"
        is AssignExpr -> "$name = ${value.toDragonScript()}"
        is BlockExpr -> "{ ${statements.joinToString("; ") { it.toDragonScript() }} }"
        is IfExpr -> "if(${condition.toDragonScript()}) ${thenBranch.toDragonScript()}" + (elseBranch?.let { " else ${it.toDragonScript()}" } ?: "")
        is WhileExpr -> "while(${condition.toDragonScript()}) ${body.toDragonScript()}"
        is FunctionDefExpr -> "def $name(${params.joinToString(", ")}) ${body.toDragonScript()}"
        is ReturnExpr -> "return ${value.toDragonScript()}"
    }
}
