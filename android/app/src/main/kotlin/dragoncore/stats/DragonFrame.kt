package dragoncore.stats

/**
 * DragonFrame: Un motor de DataFrames columnar de alto rendimiento para DragonCalc.
 * Optimizado para análisis estadístico y simulaciones masivas.
 */
class DragonFrame(val columns: Map<String, List<Double>>) {
    val rowCount = columns.values.firstOrNull()?.size ?: 0
    val columnNames = columns.keys.toList()

    fun getColumn(name: String): List<Double>? = columns[name]

    fun mean(columnName: String): Double? {
        val col = columns[columnName] ?: return null
        if (col.isEmpty()) return 0.0
        return col.sum() / col.size
    }

    fun stdDev(columnName: String): Double? {
        val col = columns[columnName] ?: return null
        if (col.size < 2) return 0.0
        val avg = mean(columnName) ?: return null
        val variance = col.sumOf { (it - avg) * (it - avg) } / (col.size - 1)
        return kotlin.math.sqrt(variance)
    }

    fun describe(): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        columnNames.forEach { name ->
            result["$name.mean"] = mean(name) ?: 0.0
            result["$name.std"] = stdDev(name) ?: 0.0
            result["$name.min"] = columns[name]?.minOrNull() ?: 0.0
            result["$name.max"] = columns[name]?.maxOrNull() ?: 0.0
        }
        return result
    }

    /**
     * Filtra el DataFrame basado en un predicado simbólico (simplificado)
     */
    fun filter(columnName: String, predicate: (Double) -> Boolean): DragonFrame {
        val indices = columns[columnName]?.indices?.filter { predicate(columns[columnName]!![it]) } ?: emptyList()
        val newCols = columns.mapValues { (_, col) ->
            indices.map { col[it] }
        }
        return DragonFrame(newCols)
    }

    companion object {
        fun fromCsv(csv: String): DragonFrame {
            val lines = csv.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) return DragonFrame(emptyMap())

            val headers = lines[0].split(",").map { it.trim() }
            val data = headers.indices.map { mutableListOf<Double>() }

            lines.drop(1).forEach { line ->
                val parts = line.split(",").map { it.trim().toDoubleOrNull() ?: 0.0 }
                parts.forEachIndexed { i, d -> if (i < data.size) data[i].add(d) }
            }

            return DragonFrame(headers.zip(data).toMap())
        }
    }

    /**
     * T-Test de una muestra (vs valor esperado mu)
     */
    fun tTest(columnName: String, mu: Double): Double {
        val meanVal = mean(columnName) ?: return 0.0
        val stdVal = stdDev(columnName) ?: return 0.0
        val n = columns[columnName]?.size ?: return 0.0
        if (n < 2) return 0.0
        val t = (meanVal - mu) / (stdVal / kotlin.math.sqrt(n.toDouble()))
        return t // Retorna el T-valor para simplicidad
    }
}
