package dragoncore.stats

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.random.Random

/**
 * MonteCarloEngine: Motor de simulaciones probabilísticas distribuidas.
 * Permite ejecutar miles de iteraciones en paralelo para modelado de riesgo y física.
 */
class MonteCarloEngine {

    /**
     * Ejecuta una simulación de Monte Carlo distribuida.
     * @param iterations Número de iteraciones (ej. 10,000)
     * @param simulationBloc Bloque que define una sola iteración
     */
    suspend fun runSimulation(
        iterations: Int,
        simulationBloc: suspend (iteration: Int) -> Double
    ): List<Double> = coroutineScope {
        val cores = Runtime.getRuntime().availableProcessors()
        val batchSize = iterations / cores

        (0 until cores).map { coreId ->
            async(Dispatchers.Default) {
                val start = coreId * batchSize
                val end = if (coreId == cores - 1) iterations else (coreId + 1) * batchSize
                (start until end).map { simulationBloc(it) }
            }
        }.awaitAll().flatten()
    }

    /**
     * Analiza los resultados de una simulación para generar estadísticas.
     */
    fun analyze(results: List<Double>): SimulationSummary {
        val sorted = results.sorted()
        return SimulationSummary(
            mean = results.average(),
            p5 = sorted[(results.size * 0.05).toInt()],
            p50 = sorted[(results.size * 0.5).toInt()],
            p95 = sorted[(results.size * 0.95).toInt()],
            min = sorted.first(),
            max = sorted.last()
        )
    }

    data class SimulationSummary(
        val mean: Double,
        val p5: Double,
        val p50: Double,
        val p95: Double,
        val min: Double,
        val max: Double
    )
}
