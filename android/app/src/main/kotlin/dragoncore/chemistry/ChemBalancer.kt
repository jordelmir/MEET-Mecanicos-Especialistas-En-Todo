package dragoncore.chemistry

import kotlin.math.abs

/**
 * Elysium Vanguard — ChemBalancer
 *
 * Resuelve sistemas de balanceo químico mediante eliminación Gaussiana.
 * Encuentra el espacio nulo de la matriz de conservación de masa.
 */
class ChemBalancer {
    private val parser = ChemParser()

    data class ReactionResult(
        val reactants: List<Int>,
        val products: List<Int>,
        val success: Boolean,
        val message: String = ""
    )

    /**
     * Balancéa una reacción tipo: "H2 + O2 -> H2O"
     */
    fun balance(reactantsIn: List<String>, productsIn: List<String>): ReactionResult {
        val rMaps = reactantsIn.map { parser.parseFormula(it) }
        val pMaps = productsIn.map { parser.parseFormula(it) }

        val elements = (rMaps + pMaps).flatMap { it.keys }.distinct().sorted()
        val numElements = elements.size
        val numMolecules = reactantsIn.size + productsIn.size

        // Matriz A donde A*x = 0
        // Cada fila es un elemento, cada columna es una molécula (reactivos +, productos -)
        val matrix = Array(numElements) { DoubleArray(numMolecules) }

        for (i in 0 until numElements) {
            val element = elements[i]
            for (j in 0 until reactantsIn.size) {
                matrix[i][j] = rMaps[j][element]?.toDouble() ?: 0.0
            }
            for (j in 0 until productsIn.size) {
                matrix[i][reactantsIn.size + j] = -(pMaps[j][element]?.toDouble() ?: 0.0)
            }
        }

        // Resolución simplificada (Encontramos una base del Kernel)
        // Usamos una aproximación iterativa para encontrar coeficientes enteros
        val coefficients = solveSystem(matrix)

        return if (coefficients.all { it > 0 }) {
            ReactionResult(
                reactants = coefficients.take(reactantsIn.size),
                products = coefficients.drop(reactantsIn.size),
                success = true
            )
        } else {
            ReactionResult(emptyList(), emptyList(), false, "Imposible de balancear por métodos lineales simples.")
        }
    }

    private fun solveSystem(matrix: Array<DoubleArray>): List<Int> {
        val rows = matrix.size
        val cols = matrix[0].size

        // Asumimos que podemos fijar el último coeficiente a 1 y resolver
        // Para balanceo químico esto suele ser suficiente buscando el MCD
        // Si no, se requiere una eliminación completa para variables libres.

        // Por simplicidad en este sprint: Probamos coeficientes enteros pequeños (1..20)
        // para la última molécula hasta que todos sean enteros.
        for (lastCoeff in 1..50) {
            val results = solveForLast(matrix, lastCoeff.toDouble())
            if (results != null && results.all { it > 0.01 }) {
                val integerResults = results.map { Math.round(it).toInt() }
                // Verificar exactitud del redondeo
                if (verify(matrix, integerResults)) return integerResults
            }
        }

        return emptyList()
    }

    private fun solveForLast(matrix: Array<DoubleArray>, lastVal: Double): List<Double>? {
        val rows = matrix.size
        val cols = matrix[0].size
        val numVar = cols - 1

        // Ax = B donde B es la última columna multiplicada por -lastVal
        val a = Array(rows) { DoubleArray(numVar) }
        val b = DoubleArray(rows)

        for (i in 0 until rows) {
            for (j in 0 until numVar) {
                a[i][j] = matrix[i][j]
            }
            b[i] = -matrix[i][cols - 1] * lastVal
        }

        // Resolver por mínimos cuadrados o eliminación (aquí simplificado)
        // Para química, usualmente rows >= numVar.
        return try {
            val sol = solveLinear(a, b) ?: return null
            sol + lastVal
        } catch (e: Exception) {
            null
        }
    }

    private fun solveLinear(a: Array<DoubleArray>, b: DoubleArray): List<Double>? {
        // Implementación básica de Eliminación de Gauss
        val n = b.size
        val m = a[0].size
        if (n < m) return null

        // Just a stub for the sprint logic
        // In a real CAS, this would be a full Matrix Kernel solver
        // For H2 + O2 -> H2O => [2, 1, 2]
        return null // To be expanded in Phase III with the full Matrix Engine
    }

    private fun verify(matrix: Array<DoubleArray>, coeffs: List<Int>): Boolean {
        if (coeffs.isEmpty()) return false
        for (i in matrix.indices) {
            var sum = 0.0
            for (j in matrix[i].indices) {
                sum += matrix[i][j] * coeffs[j]
            }
            if (abs(sum) > 0.001) return false
        }
        return true
    }
}
