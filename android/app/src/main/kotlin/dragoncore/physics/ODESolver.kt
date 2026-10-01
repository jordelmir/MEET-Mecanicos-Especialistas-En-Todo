package dragoncore.physics

import dragoncore.evaluator.DragonContext
import dragoncore.evaluator.DragonEvaluator
import dragoncore.parser.Expr

/**
 * Elysium Vanguard — ODESolver
 *
 * Kernel de resolución de Ecuaciones Diferenciales Ordinarias (ODEs).
 * Implementa el algoritmo Runge-Kutta de 4to Orden (RK4) para sistemas dinámicos.
 *
 * El solver permite integrar sistemas de la forma dy/dt = f(t, y) donde
 * f se define mediante expresiones simbólicas del DragonParser.
 */
class ODESolver(private val evaluator: DragonEvaluator = DragonEvaluator()) {

    /**
     * Realiza un paso de integración RK4 para un vector de estado.
     *
     * k1 = f(t, y)
     * k2 = f(t + dt/2, y + k1 * dt/2)
     * k3 = f(t + dt/2, y + k2 * dt/2)
     * k4 = f(t + dt, y + k3 * dt)
     * y_next = y + (dt/6) * (k1 + 2k2 + 2k3 + k4)
     */
    private fun rk4Step(
        t: Double,
        y: DoubleArray,
        dt: Double,
        f: (Double, DoubleArray) -> DoubleArray
    ): DoubleArray {
        val n = y.size
        val k1 = f(t, y)

        val y2 = DoubleArray(n) { i -> y[i] + k1[i] * (dt / 2.0) }
        val k2 = f(t + dt / 2.0, y2)

        val y3 = DoubleArray(n) { i -> y[i] + k2[i] * (dt / 2.0) }
        val k3 = f(t + dt / 2.0, y3)

        val y4 = DoubleArray(n) { i -> y[i] + k3[i] * dt }
        val k4 = f(t + dt, y4)

        return DoubleArray(n) { i ->
            y[i] + (dt / 6.0) * (k1[i] + 2.0 * k2[i] + 2.0 * k3[i] + k4[i])
        }
    }

    /**
     * Resuelve un paso del sistema dinámico basado en nombres de variables.
     *
     * @param t Tiempo actual.
     * @param state Mapa de Variable -> Valor actual.
     * @param system Mapa de Variable -> Expresión de su derivada (dy/dt).
     * @param dt Incremento de tiempo.
     * @param context Contexto de DragonDocs con parámetros (L, g, m, etc.)
     * @return Nuevo mapa de estado actualizado.
     */
    fun solveStep(
        t: Double,
        state: Map<String, Double>,
        system: Map<String, Expr>,
        dt: Double,
        context: DragonContext
    ): Map<String, Double> {
        val varNames = system.keys.toList()
        val y = DoubleArray(varNames.size) { i -> state[varNames[i]] ?: 0.0 }

        // Definimos f(t, y) evaluando las expresiones en el contexto actual
        val f: (Double, DoubleArray) -> DoubleArray = { currentTime, currentY ->
            // Creamos un sub-contexto para no contaminar el global,
            // inyectando t y los valores actuales del vector de estado.
            val stepContext = context.clone()
            stepContext.setVariable("t", currentTime)
            for (i in varNames.indices) {
                stepContext.setVariable(varNames[i], currentY[i])
            }

            DoubleArray(varNames.size) { i ->
                try {
                    evaluator.evaluate(system[varNames[i]]!!, stepContext)
                } catch (e: Exception) {
                    0.0 // Fail-safe en caso de error de evaluacion durante simulación
                }
            }
        }

        val nextY = rk4Step(t, y, dt, f)

        // Retornamos el estado mapeado de vuelta a sus nombres originales
        val nextState = mutableMapOf<String, Double>()
        for (i in varNames.indices) {
            nextState[varNames[i]] = nextY[i]
        }
        return nextState
    }
}
