package dragoncore.cas

import kotlin.math.*

/**
 * Elysium Vanguard — ComplexNumber
 *
 * Representación de números complejos z = a + bi.
 * Implementa aritmética completa + funciones trascendentales
 * usando identidades de Euler.
 *
 * Diseño inmutable: todas las operaciones retornan nuevas instancias.
 */
data class ComplexNumber(val real: Double, val imag: Double) {

    // ════════════════════════════════════════════
    // ARITMÉTICA BÁSICA
    // ════════════════════════════════════════════

    operator fun plus(other: ComplexNumber) =
        ComplexNumber(real + other.real, imag + other.imag)

    operator fun minus(other: ComplexNumber) =
        ComplexNumber(real - other.real, imag - other.imag)

    operator fun times(other: ComplexNumber) =
        ComplexNumber(
            real * other.real - imag * other.imag,
            real * other.imag + imag * other.real
        )

    operator fun div(other: ComplexNumber): ComplexNumber {
        val denom = other.real * other.real + other.imag * other.imag
        if (denom == 0.0) throw ArithmeticException("División compleja por cero")
        return ComplexNumber(
            (real * other.real + imag * other.imag) / denom,
            (imag * other.real - real * other.imag) / denom
        )
    }

    /** Escalado por un real. */
    operator fun times(scalar: Double) =
        ComplexNumber(real * scalar, imag * scalar)

    operator fun div(scalar: Double): ComplexNumber {
        if (scalar == 0.0) throw ArithmeticException("División compleja por cero")
        return ComplexNumber(real / scalar, imag / scalar)
    }

    /** Negación. */
    operator fun unaryMinus() = ComplexNumber(-real, -imag)

    // ════════════════════════════════════════════
    // PROPIEDADES
    // ════════════════════════════════════════════

    /** Conjugado: a - bi */
    fun conjugate() = ComplexNumber(real, -imag)

    /** Módulo |z| = sqrt(a² + b²) */
    fun modulus(): Double = sqrt(real * real + imag * imag)

    /** Fase arg(z) en radianes [-π, π] */
    fun phase(): Double = atan2(imag, real)

    /** ¿Es puramente real? (parte imaginaria ≈ 0) */
    fun isReal(tol: Double = 1e-12): Boolean = abs(imag) < tol

    /** ¿Es cero? */
    fun isZero(tol: Double = 1e-12): Boolean = abs(real) < tol && abs(imag) < tol

    // ════════════════════════════════════════════
    // FUNCIONES TRASCENDENTALES (Euler)
    // ════════════════════════════════════════════

    companion object {
        val ZERO = ComplexNumber(0.0, 0.0)
        val ONE = ComplexNumber(1.0, 0.0)
        val I = ComplexNumber(0.0, 1.0)

        /** Crea desde forma polar: r * e^(iθ) */
        fun fromPolar(r: Double, theta: Double) =
            ComplexNumber(r * cos(theta), r * sin(theta))

        /** Crea desde un real. */
        fun fromReal(v: Double) = ComplexNumber(v, 0.0)

        // ── Funciones trascendentales complejas ──

        /** exp(z) = e^a * (cos(b) + i*sin(b)) */
        fun exp(z: ComplexNumber): ComplexNumber {
            val ea = kotlin.math.exp(z.real)
            return ComplexNumber(ea * cos(z.imag), ea * sin(z.imag))
        }

        /** ln(z) = ln|z| + i*arg(z) */
        fun ln(z: ComplexNumber): ComplexNumber {
            val r = z.modulus()
            if (r == 0.0) throw ArithmeticException("ln(0) no está definido")
            return ComplexNumber(kotlin.math.ln(r), z.phase())
        }

        /** sin(z) = (e^(iz) - e^(-iz)) / (2i) */
        fun sin(z: ComplexNumber): ComplexNumber {
            val iz = ComplexNumber(-z.imag, z.real) // i * z
            val eiz = exp(iz)
            val emiz = exp(-iz)
            return (eiz - emiz) / ComplexNumber(0.0, 2.0)
        }

        /** cos(z) = (e^(iz) + e^(-iz)) / 2 */
        fun cos(z: ComplexNumber): ComplexNumber {
            val iz = ComplexNumber(-z.imag, z.real) // i * z
            val eiz = exp(iz)
            val emiz = exp(-iz)
            return (eiz + emiz) / 2.0
        }

        /** sqrt(z) — raíz cuadrada principal */
        fun sqrt(z: ComplexNumber): ComplexNumber {
            if (z.isZero()) return ZERO
            val r = z.modulus()
            val theta = z.phase()
            return fromPolar(kotlin.math.sqrt(r), theta / 2.0)
        }

        /** z^w — potencia compleja general: z^w = exp(w * ln(z)) */
        fun pow(base: ComplexNumber, exponent: ComplexNumber): ComplexNumber {
            if (base.isZero()) {
                return if (exponent.real > 0) ZERO
                else throw ArithmeticException("0^(non-positive) no definido")
            }
            return exp(exponent * ln(base))
        }

        /** tan(z) = sin(z) / cos(z) */
        fun tan(z: ComplexNumber): ComplexNumber = sin(z) / cos(z)
    }

    // ════════════════════════════════════════════
    // FORMATO
    // ════════════════════════════════════════════

    override fun toString(): String {
        val re = formatNum(real)
        val im = formatNum(abs(imag))
        return when {
            abs(imag) < 1e-12 -> re
            abs(real) < 1e-12 && abs(imag - 1.0) < 1e-12 -> "i"
            abs(real) < 1e-12 && abs(imag + 1.0) < 1e-12 -> "-i"
            abs(real) < 1e-12 -> "${formatNum(imag)}i"
            imag > 0 -> "$re + ${im}i"
            else -> "$re - ${im}i"
        }
    }

    private fun formatNum(v: Double): String {
        return if (v == floor(v) && abs(v) < 1e15) v.toLong().toString()
        else "%.6g".format(v).trimEnd('0').trimEnd('.')
    }
}
