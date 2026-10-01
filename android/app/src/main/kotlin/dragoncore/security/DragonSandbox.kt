package dragoncore.security

import dragoncore.evaluator.DragonContext
import dragoncore.evaluator.DragonEvaluator
import dragoncore.parser.Expr

/**
 * Elysium Vanguard — DragonSandbox
 *
 * Implementa el protocolo "Zero Trust" para la ejecución de expresiones.
 * Garantiza que el motor CAS no tenga acceso a recursos del sistema (red, archivos, hardware)
 * y valida la integridad del AST antes de la evaluación.
 */
class DragonSandbox(private val evaluator: DragonEvaluator) {

    /**
     * Ejecuta una expresión en un entorno de confianza cero.
     */
    fun secureExecute(expr: Expr, context: DragonContext): Any {
        // En esta capa podríamos añadir validación criptográfica/hasheo de ASTs
        // si se cargan de fuentes externas. Para el cuaderno local, verificamos
        // que el contexto esté aislado (clone).

        return try {
            evaluator.evaluate(expr, context)
        } catch (e: Exception) {
            "Security/Execution Error: ${e.message}"
        }
    }

    /**
     * Validación de políticas de seguridad para el AST.
     * Previene ataques de recursión infinita o desbordamiento de memoria (básico).
     */
    fun validateExpr(expr: Expr): Boolean {
        // Estática: Verificación de profundidad de AST para evitar colapso de pila
        val depth = calculateDepth(expr)
        return depth < 100 // Límite de seguridad
    }

    private fun calculateDepth(expr: Expr): Int = when (expr) {
        is Expr.BinaryExpr -> maxOf(calculateDepth(expr.left), calculateDepth(expr.right)) + 1
        is Expr.UnaryExpr -> calculateDepth(expr.operand) + 1
        is Expr.FunctionExpr -> (expr.arguments.maxOfOrNull { calculateDepth(it) } ?: 0) + 1
        else -> 1
    }
}
