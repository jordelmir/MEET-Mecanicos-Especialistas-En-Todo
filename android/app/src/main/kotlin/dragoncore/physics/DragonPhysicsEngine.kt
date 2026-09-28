package dragoncore.physics

import dragoncore.evaluator.DragonContext
import dragoncore.parser.Expr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Elysium Vanguard — DragonPhysicsEngine
 *
 * Orquestador de simulaciones físicas en tiempo real.
 * Gestiona el ciclo de vida del ODESolver y la emisión de estados dinámicos
 * para alimentar la interfaz reactiva (PhysicsCell).
 */
class DragonPhysicsEngine(private val solver: ODESolver = ODESolver()) {

    /**
     * Inicia una simulación continua del sistema de ecuaciones diferenciales.
     *
     * @param initialState Mapa de Nombre -> Valor inicial.
     * @param system Mapa de Nombre -> Expresión dy/dt.
     * @param context Contexto de DragonDocs (contiene constantes como G, L, etc.)
     * @param dt Paso de tiempo para la integración (0.016s ≈ 60 FPS).
     */
    fun startSimulation(
        initialState: Map<String, Double>,
        system: Map<String, Expr>,
        context: DragonContext,
        dt: Double = 0.016
    ): Flow<Map<String, Double>> = flow {
        var currentTime = 0.0
        var currentState = initialState

        // Loop infinito de simulación
        while (true) {
            val t1 = System.currentTimeMillis()

            // Calculamos el siguiente paso de integración
            currentState = solver.solveStep(currentTime, currentState, system, dt, context)
            currentTime += dt

            // Emitimos el nuevo estado al recolector (UI)
            emit(currentState)

            // Compensamos el tiempo de cálculo para mantener el ritmo de FPS
            val t2 = System.currentTimeMillis()
            val remainingDelay = ((dt * 1000) - (t2 - t1)).toLong().coerceAtLeast(1)
            delay(remainingDelay)
        }
    }.flowOn(Dispatchers.Default) // Ejecutar en el pool de hilos de background
}
