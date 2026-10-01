package dragoncore.cas

import kotlin.math.abs

/**
 * Elysium Vanguard — MatrixCore
 *
 * Motor de álgebra lineal para matrices NxM de Double.
 * Operaciones de alto rendimiento:
 *   - Suma, Resta, Multiplicación
 *   - Transpuesta
 *   - Determinante (LU Decomposition, O(n³))
 *   - Inversa (Gauss-Jordan, O(n³))
 *   - RREF (Forma escalonada reducida)
 *   - Traza, LU Decomposition
 *
 * Representación: Array<DoubleArray> row-major.
 */
class MatrixCore(val data: Array<DoubleArray>) {

    val rows: Int = data.size
    val cols: Int = if (data.isNotEmpty()) data[0].size else 0

    init {
        require(rows > 0 && cols > 0) { "Matrix vacía no permitida" }
        require(data.all { it.size == cols }) { "Todas las filas deben tener igual longitud" }
    }

    /** Acceso: matrix[i, j] */
    operator fun get(i: Int, j: Int): Double = data[i][j]

    /** Escritura: matrix[i, j] = v */
    operator fun set(i: Int, j: Int, v: Double) { data[i][j] = v }

    /** ¿Es cuadrada? */
    fun isSquare(): Boolean = rows == cols

    // ════════════════════════════════════════════
    // ARITMÉTICA MATRICIAL
    // ════════════════════════════════════════════

    operator fun plus(other: MatrixCore): MatrixCore {
        require(rows == other.rows && cols == other.cols) {
            "Dimensiones incompatibles: ${rows}x$cols + ${other.rows}x${other.cols}"
        }
        return MatrixCore(Array(rows) { i ->
            DoubleArray(cols) { j -> data[i][j] + other.data[i][j] }
        })
    }

    operator fun minus(other: MatrixCore): MatrixCore {
        require(rows == other.rows && cols == other.cols) {
            "Dimensiones incompatibles: ${rows}x$cols - ${other.rows}x${other.cols}"
        }
        return MatrixCore(Array(rows) { i ->
            DoubleArray(cols) { j -> data[i][j] - other.data[i][j] }
        })
    }

    /** Multiplicación matricial A(m×n) × B(n×p) → C(m×p) */
    operator fun times(other: MatrixCore): MatrixCore {
        require(cols == other.rows) {
            "Dimensiones incompatibles: ${rows}x$cols * ${other.rows}x${other.cols}"
        }
        return MatrixCore(Array(rows) { i ->
            DoubleArray(other.cols) { j ->
                var sum = 0.0
                for (k in 0 until cols) {
                    sum += data[i][k] * other.data[k][j]
                }
                sum
            }
        })
    }

    /** Multiplicación por escalar. */
    operator fun times(scalar: Double): MatrixCore {
        return MatrixCore(Array(rows) { i ->
            DoubleArray(cols) { j -> data[i][j] * scalar }
        })
    }

    // ════════════════════════════════════════════
    // OPERACIONES FUNDAMENTALES
    // ════════════════════════════════════════════

    /** Transpuesta A^T */
    fun transpose(): MatrixCore {
        return MatrixCore(Array(cols) { j ->
            DoubleArray(rows) { i -> data[i][j] }
        })
    }

    /** Traza (solo matrices cuadradas) */
    fun trace(): Double {
        require(isSquare()) { "Traza solo definida para matrices cuadradas" }
        var sum = 0.0
        for (i in 0 until rows) sum += data[i][i]
        return sum
    }

    /** Crea la matriz identidad n×n */
    companion object {
        fun identity(n: Int): MatrixCore {
            return MatrixCore(Array(n) { i ->
                DoubleArray(n) { j -> if (i == j) 1.0 else 0.0 }
            })
        }

        fun fromRows(vararg rows: DoubleArray): MatrixCore {
            return MatrixCore(arrayOf(*rows))
        }
    }

    // ════════════════════════════════════════════
    // DETERMINANTE (LU Decomposition, O(n³))
    // ════════════════════════════════════════════

    /**
     * Calcula el determinante usando descomposición LU con pivoteo parcial.
     * Complejidad: O(n³) — mucho mejor que la expansión por cofactores O(n!).
     */
    fun determinant(): Double {
        require(isSquare()) { "Determinante solo definido para matrices cuadradas" }
        val n = rows

        // Caso base rápido
        if (n == 1) return data[0][0]
        if (n == 2) return data[0][0] * data[1][1] - data[0][1] * data[1][0]

        // Copia de trabajo para LU in-place
        val lu = Array(n) { i -> data[i].copyOf() }
        var sign = 1.0

        for (col in 0 until n) {
            // Pivoteo parcial: busca el mayor elemento en la columna
            var maxVal = abs(lu[col][col])
            var maxRow = col
            for (row in col + 1 until n) {
                if (abs(lu[row][col]) > maxVal) {
                    maxVal = abs(lu[row][col])
                    maxRow = row
                }
            }

            // Singular (o casi)
            if (maxVal < 1e-14) return 0.0

            // Swap filas si es necesario
            if (maxRow != col) {
                val temp = lu[col]
                lu[col] = lu[maxRow]
                lu[maxRow] = temp
                sign *= -1.0
            }

            // Eliminación
            for (row in col + 1 until n) {
                val factor = lu[row][col] / lu[col][col]
                lu[row][col] = factor // Almacena L debajo de diagonal
                for (j in col + 1 until n) {
                    lu[row][j] -= factor * lu[col][j]
                }
            }
        }

        // det = sign * producto de diagonal de U
        var det = sign
        for (i in 0 until n) det *= lu[i][i]
        return det
    }

    // ════════════════════════════════════════════
    // INVERSA (Gauss-Jordan, O(n³))
    // ════════════════════════════════════════════

    /**
     * Calcula la inversa usando eliminación de Gauss-Jordan.
     * Augmenta [A | I] y reduce a [I | A⁻¹].
     */
    fun inverse(): MatrixCore {
        require(isSquare()) { "Inversa solo definida para matrices cuadradas" }
        val n = rows

        // Augmentar [A | I]
        val aug = Array(n) { i ->
            DoubleArray(2 * n) { j ->
                if (j < n) data[i][j] else if (j - n == i) 1.0 else 0.0
            }
        }

        // Forward elimination con pivoteo parcial
        for (col in 0 until n) {
            // Pivoteo
            var maxVal = abs(aug[col][col])
            var maxRow = col
            for (row in col + 1 until n) {
                if (abs(aug[row][col]) > maxVal) {
                    maxVal = abs(aug[row][col])
                    maxRow = row
                }
            }

            if (maxVal < 1e-14) {
                throw ArithmeticException("Matriz singular, no tiene inversa")
            }

            if (maxRow != col) {
                val temp = aug[col]; aug[col] = aug[maxRow]; aug[maxRow] = temp
            }

            // Normalizar fila pivote
            val pivot = aug[col][col]
            for (j in 0 until 2 * n) aug[col][j] /= pivot

            // Eliminar en todas las demás filas
            for (row in 0 until n) {
                if (row == col) continue
                val factor = aug[row][col]
                for (j in 0 until 2 * n) {
                    aug[row][j] -= factor * aug[col][j]
                }
            }
        }

        // Extraer A⁻¹ de la parte derecha
        return MatrixCore(Array(n) { i ->
            DoubleArray(n) { j -> aug[i][j + n] }
        })
    }

    // ════════════════════════════════════════════
    // RREF (Forma Escalonada Reducida por Filas)
    // ════════════════════════════════════════════

    /**
     * Reduced Row Echelon Form.
     * Transforma la matriz a su forma escalonada reducida.
     */
    fun rref(): MatrixCore {
        val result = Array(rows) { i -> data[i].copyOf() }
        var lead = 0

        for (r in 0 until rows) {
            if (lead >= cols) break

            // Busca fila con pivot no-cero
            var i = r
            while (abs(result[i][lead]) < 1e-14) {
                i++
                if (i == rows) {
                    i = r
                    lead++
                    if (lead == cols) return MatrixCore(result)
                }
            }

            // Swap
            if (i != r) {
                val temp = result[i]; result[i] = result[r]; result[r] = temp
            }

            // Normalizar
            val div = result[r][lead]
            if (abs(div) > 1e-14) {
                for (j in 0 until cols) result[r][j] /= div
            }

            // Eliminar columna
            for (row in 0 until rows) {
                if (row == r) continue
                val factor = result[row][lead]
                for (j in 0 until cols) {
                    result[row][j] -= factor * result[r][j]
                }
            }

            lead++
        }

        return MatrixCore(result)
    }

    // ════════════════════════════════════════════
    // FORMATO
    // ════════════════════════════════════════════

    override fun toString(): String {
        return data.joinToString("; ") { row ->
            row.joinToString(", ") { v ->
                if (v == kotlin.math.floor(v) && abs(v) < 1e15) v.toLong().toString()
                else "%.6g".format(v).trimEnd('0').trimEnd('.')
            }
        }.let { "[$it]" }
    }

    fun toFormattedString(): String {
        return data.joinToString("\n") { row ->
            "│ " + row.joinToString("  ") { v ->
                val s = if (v == kotlin.math.floor(v) && abs(v) < 1e15) v.toLong().toString()
                else "%.4g".format(v)
                s.padStart(8)
            } + " │"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is MatrixCore) return false
        if (rows != other.rows || cols != other.cols) return false
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                if (abs(data[i][j] - other.data[i][j]) > 1e-10) return false
            }
        }
        return true
    }

    override fun hashCode(): Int {
        var result = rows
        result = 31 * result + cols
        for (row in data) for (v in row) result = 31 * result + v.hashCode()
        return result
    }
}
