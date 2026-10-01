package dragoncore.evaluator

/**
 * Dragon Core - DragonContext
 *
 * Estado de ejecucion en tiempo real de la calculadora.
 * Almacena variables del usuario, modo angular, y constantes fisicas.
 *
 * Cada sesion de evaluacion comparte un contexto que persiste
 * entre calculos sucesivos (ej. x = 5 y luego x + 3 da 8).
 */
class DragonContext {

    // ================================================================
    // MODO ANGULAR
    // ================================================================

    enum class AngleMode {
        /** Radianes (por defecto, estandar matematico) */
        RADIAN,
        /** Grados sexagesimales (360 grados = vuelta completa) */
        DEGREE,
        /** Gradianes (400 gradianes = vuelta completa) */
        GRADIAN
    }

    var angleMode: AngleMode = AngleMode.RADIAN

    // ================================================================
    // VARIABLES DEL USUARIO (con pila de scopes)
    // ================================================================

    /** Variables globales del usuario */
    private val globalVariables: MutableMap<String, Double> = mutableMapOf()

    /** Pila de scopes locales (para funciones de usuario) */
    private val scopeStack: MutableList<MutableMap<String, Double>> = mutableListOf()

    /** User-Defined Functions: nombre → (params, bodyAST) */
    private val userFunctions: MutableMap<String, Pair<List<String>, dragoncore.parser.Expr>> = mutableMapOf()

    /** Empuja un nuevo scope local (al entrar a una función) */
    fun pushScope() {
        scopeStack.add(mutableMapOf())
    }

    /** Destruye el scope local actual (al salir de una función) */
    fun popScope() {
        if (scopeStack.isNotEmpty()) scopeStack.removeAt(scopeStack.size - 1)
    }

    /** Asigna un valor. Si hay un scope local, escribe ahí; si no, en global. */
    fun setVariable(name: String, value: Double) {
        if (scopeStack.isNotEmpty()) {
            scopeStack.last()[name] = value
        } else {
            globalVariables[name] = value
        }
    }

    /** Busca primero en scopes locales (LIFO), luego en global. */
    fun getVariable(name: String): Double? {
        // Buscar en scopes locales de arriba a abajo
        for (i in scopeStack.indices.reversed()) {
            scopeStack[i][name]?.let { return it }
        }
        return globalVariables[name]
    }

    /** Verifica si una variable está definida en cualquier scope. */
    fun hasVariable(name: String): Boolean =
        scopeStack.any { name in it } || name in globalVariables

    /** Elimina una variable del scope actual o global. */
    fun clearVariable(name: String) {
        if (scopeStack.isNotEmpty()) {
            scopeStack.last().remove(name)
        } else {
            globalVariables.remove(name)
        }
    }

    /** Limpia todas las variables globales y scopes. */
    fun clearAllVariables() {
        globalVariables.clear()
        scopeStack.clear()
    }

    /** Retorna copia de todas las variables visibles (global + scopes). */
    fun getAllVariables(): Map<String, Double> {
        val merged = globalVariables.toMutableMap()
        for (scope in scopeStack) merged.putAll(scope)
        return merged
    }

    // ================================================================
    // USER-DEFINED FUNCTIONS
    // ================================================================

    /** Registra una función de usuario (UDF). */
    fun registerFunction(name: String, params: List<String>, body: dragoncore.parser.Expr) {
        userFunctions[name] = params to body
    }

    /** Obtiene una función de usuario registrada. */
    fun getFunction(name: String): Pair<List<String>, dragoncore.parser.Expr>? = userFunctions[name]

    /** ¿Existe una función de usuario con este nombre? */
    fun hasFunction(name: String): Boolean = name in userFunctions

    // ================================================================
    // CONSTANTES MATEMATICAS
    // ================================================================

    /** Constantes predefinidas que no pueden ser sobreescritas. */
    companion object {
        val CONSTANTS: Map<String, Double> = mapOf(
            "pi"       to Math.PI,
            "e"        to Math.E,
            "phi"      to 1.6180339887498948482,  // Golden ratio
            "inf"      to Double.POSITIVE_INFINITY,
            "infinity" to Double.POSITIVE_INFINITY,
            "nan"      to Double.NaN
        )
    }

    // ================================================================
    // CONVERSION ANGULAR
    // ================================================================

    /**
     * Convierte un angulo desde el modo actual del contexto a radianes.
     * Las funciones de kotlin.math siempre esperan radianes.
     */
    fun toRadians(angle: Double): Double = when (angleMode) {
        AngleMode.RADIAN  -> angle
        AngleMode.DEGREE  -> Math.toRadians(angle)
        AngleMode.GRADIAN -> angle * Math.PI / 200.0
    }

    /**
     * Convierte un resultado en radianes al modo actual del contexto.
     * Usado para las funciones inversas (asin, acos, atan).
     */
    fun fromRadians(radians: Double): Double = when (angleMode) {
        AngleMode.RADIAN  -> radians
        AngleMode.DEGREE  -> Math.toDegrees(radians)
        AngleMode.GRADIAN -> radians * 200.0 / Math.PI
    }

    // ================================================================
    // RESET
    // ================================================================

    /** Restaura el contexto al estado inicial. */
    fun reset() {
        globalVariables.clear()
        scopeStack.clear()
        userFunctions.clear()
        angleMode = AngleMode.RADIAN
    }

    /**
     * Realiza una copia profunda del contexto.
     * Útil para evaluaciones paralelas o simulaciones que no deben
     * alterar el estado global de las variables.
     */
    fun clone(): DragonContext {
        val newCtx = DragonContext()
        newCtx.angleMode = this.angleMode
        newCtx.globalVariables.putAll(this.globalVariables)
        this.scopeStack.forEach { scope ->
            newCtx.scopeStack.add(scope.toMutableMap())
        }
        newCtx.userFunctions.putAll(this.userFunctions)
        return newCtx
    }
}
