package dragoncore.cas

import dragoncore.lexer.Token
import dragoncore.lexer.TokenType
import dragoncore.parser.Expr

/**
 * Elysium Vanguard — Dragon-IR (Intermediate Representation)
 *
 * Sistema de Hash-Consing para transformar el AST en un DAG
 * (Directed Acyclic Graph), eliminando nodos duplicados.
 *
 * Cada sub-expresión se identifica por un hash estructural.
 * Si dos sub-árboles son idénticos, comparten el mismo nodo →
 * reduce la huella de memoria y acelera comparaciones.
 */
object DragonIR {

    /**
     * Cache de hash-consing: hash → Expr canónico.
     * Se limpia entre sesiones de optimización.
     */
    private val cache = HashMap<String, Expr>()

    /** Limpia la cache de hash-consing. */
    fun clear() { cache.clear() }

    /**
     * Internaliza una expresión: si ya existe una idéntica en cache,
     * retorna la referencia cacheada. Si no, la almacena y retorna.
     */
    fun intern(expr: Expr): Expr {
        val hash = structuralHash(expr)
        return cache.getOrPut(hash) { expr }
    }

    /**
     * Hash estructural recursivo.
     * Dos expresiones con la misma estructura producen el mismo hash.
     */
    fun structuralHash(expr: Expr): String = when (expr) {
        is Expr.NumberExpr -> "N:${expr.value}"
        is Expr.ConstantExpr -> "C:${expr.name}"
        is Expr.VariableExpr -> "V:${expr.name}"
        is Expr.DerivativeExpr -> "d(${structuralHash(expr.expression)}, ${expr.variable.name})"
        is Expr.IntegralExpr -> "int(${structuralHash(expr.expression)}, ${expr.variable.name})"
        is Expr.UnaryExpr -> "${expr.operator.type}(${structuralHash(expr.operand)})"
        is Expr.BinaryExpr -> "B:${expr.operator.type}(${structuralHash(expr.left)},${structuralHash(expr.right)})"
        is Expr.FunctionExpr -> "F:${expr.name.type}(${expr.arguments.joinToString(",") { structuralHash(it) }})"
        is Expr.AbsExpr -> "A:(${structuralHash(expr.inner)})"
        is Expr.AssignExpr -> "S:${expr.name}=${structuralHash(expr.value)}"
        is Expr.MatrixExpr -> "M:[${expr.rows.joinToString(";") { r -> r.joinToString(",") { structuralHash(it) }}}]"
        // DragonScript nodes
        is Expr.BlockExpr -> "BLK:(${expr.statements.joinToString(",") { structuralHash(it) }})"
        is Expr.IfExpr -> "IF:(${structuralHash(expr.condition)},${structuralHash(expr.thenBranch)},${expr.elseBranch?.let { structuralHash(it) } ?: "null"})"
        is Expr.WhileExpr -> "WH:(${structuralHash(expr.condition)},${structuralHash(expr.body)})"
        is Expr.FunctionDefExpr -> "DEF:${expr.name}(${expr.params.joinToString(",")}){${structuralHash(expr.body)}}"
        is Expr.ReturnExpr -> "RET:(${structuralHash(expr.value)})"
    }

    /**
     * Compara dos expresiones por estructura (no por referencia).
     */
    fun structuralEquals(a: Expr, b: Expr): Boolean =
        structuralHash(a) == structuralHash(b)

    /**
     * Cuenta sub-expresiones únicas en un AST.
     */
    fun countUniqueNodes(expr: Expr): Int {
        val seen = mutableSetOf<String>()
        collectHashes(expr, seen)
        return seen.size
    }

    private fun collectHashes(expr: Expr, seen: MutableSet<String>) {
        val h = structuralHash(expr)
        if (h in seen) return
        seen.add(h)
        when (expr) {
            is Expr.NumberExpr, is Expr.ConstantExpr, is Expr.VariableExpr -> {}
            is Expr.DerivativeExpr -> collectHashes(expr.expression, seen)
            is Expr.IntegralExpr -> collectHashes(expr.expression, seen)
            is Expr.UnaryExpr -> collectHashes(expr.operand, seen)
            is Expr.BinaryExpr -> { collectHashes(expr.left, seen); collectHashes(expr.right, seen) }
            is Expr.FunctionExpr -> expr.arguments.forEach { collectHashes(it, seen) }
            is Expr.AbsExpr -> collectHashes(expr.inner, seen)
            is Expr.AssignExpr -> collectHashes(expr.value, seen)
            is Expr.MatrixExpr -> expr.rows.forEach { r -> r.forEach { collectHashes(it, seen) } }
            // DragonScript nodes
            is Expr.BlockExpr -> expr.statements.forEach { collectHashes(it, seen) }
            is Expr.IfExpr -> { collectHashes(expr.condition, seen); collectHashes(expr.thenBranch, seen); expr.elseBranch?.let { collectHashes(it, seen) } }
            is Expr.WhileExpr -> { collectHashes(expr.condition, seen); collectHashes(expr.body, seen) }
            is Expr.FunctionDefExpr -> collectHashes(expr.body, seen)
            is Expr.ReturnExpr -> collectHashes(expr.value, seen)
        }
    }
}
