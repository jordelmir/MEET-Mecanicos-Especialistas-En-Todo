package com.elysium369.meet.core.obd

import java.util.*
import kotlin.math.pow

sealed interface PidDecodeResult {
    data class Success(
        val value: Double,
        val unit: String? = null,
        val rawHex: String? = null,
    ) : PidDecodeResult

    data class InsufficientBytes(
        val requiredVariable: Char,
        val requiredIndex: Int,
        val availableBytes: Int,
    ) : PidDecodeResult

    data class InvalidFormula(
        val formula: String,
        val reason: String,
    ) : PidDecodeResult

    data class DivisionByZero(
        val expression: String,
    ) : PidDecodeResult

    data class NonFinite(
        val rawValue: Double,
    ) : PidDecodeResult

    data class OutOfPhysicalRange(
        val value: Double,
        val min: Double,
        val max: Double,
    ) : PidDecodeResult

    data class UnsupportedDefinition(
        val details: String,
    ) : PidDecodeResult
}

/**
 * FormulaEvaluator — High-performance mathematical engine for OBD2 formulas.
 * Evaluates string formulas like "(A*256+B)/4" or "A*0.0625-40".
 * Supports variables A, B, C, D, E, F (bytes) and standard operators with strict truth bounds.
 */
object FormulaEvaluator {

    private val PATTERN_A = Regex("\\bA\\b")
    private val PATTERN_B = Regex("\\bB\\b")
    private val PATTERN_C = Regex("\\bC\\b")
    private val PATTERN_D = Regex("\\bD\\b")
    private val PATTERN_E = Regex("\\bE\\b")
    private val PATTERN_F = Regex("\\bF\\b")

    fun decode(
        formula: String,
        bytes: List<Int>,
        minPhysical: Double? = null,
        maxPhysical: Double? = null,
        unit: String? = null,
    ): PidDecodeResult {
        if (formula.isBlank()) {
            return PidDecodeResult.InvalidFormula(formula, "Formula string cannot be blank")
        }

        val upper = formula.uppercase()

        // Strict variable presence & byte length validation
        if (PATTERN_A.containsMatchIn(upper) && bytes.isEmpty()) {
            return PidDecodeResult.InsufficientBytes('A', 0, bytes.size)
        }
        if (PATTERN_B.containsMatchIn(upper) && bytes.size < 2) {
            return PidDecodeResult.InsufficientBytes('B', 1, bytes.size)
        }
        if (PATTERN_C.containsMatchIn(upper) && bytes.size < 3) {
            return PidDecodeResult.InsufficientBytes('C', 2, bytes.size)
        }
        if (PATTERN_D.containsMatchIn(upper) && bytes.size < 4) {
            return PidDecodeResult.InsufficientBytes('D', 3, bytes.size)
        }
        if (PATTERN_E.containsMatchIn(upper) && bytes.size < 5) {
            return PidDecodeResult.InsufficientBytes('E', 4, bytes.size)
        }
        if (PATTERN_F.containsMatchIn(upper) && bytes.size < 6) {
            return PidDecodeResult.InsufficientBytes('F', 5, bytes.size)
        }

        var expression = upper
        if (PATTERN_A.containsMatchIn(expression)) expression = PATTERN_A.replace(expression, bytes[0].toString())
        if (PATTERN_B.containsMatchIn(expression)) expression = PATTERN_B.replace(expression, bytes[1].toString())
        if (PATTERN_C.containsMatchIn(expression)) expression = PATTERN_C.replace(expression, bytes[2].toString())
        if (PATTERN_D.containsMatchIn(expression)) expression = PATTERN_D.replace(expression, bytes[3].toString())
        if (PATTERN_E.containsMatchIn(expression)) expression = PATTERN_E.replace(expression, bytes[4].toString())
        if (PATTERN_F.containsMatchIn(expression)) expression = PATTERN_F.replace(expression, bytes[5].toString())

        val resultValue = try {
            eval(expression)
        } catch (e: ArithmeticException) {
            return PidDecodeResult.DivisionByZero(expression)
        } catch (e: Exception) {
            return PidDecodeResult.InvalidFormula(formula, e.message ?: "Evaluation parsing error")
        }

        if (resultValue.isNaN() || resultValue.isInfinite()) {
            return PidDecodeResult.NonFinite(resultValue)
        }

        if (minPhysical != null && resultValue < minPhysical) {
            return PidDecodeResult.OutOfPhysicalRange(resultValue, minPhysical, maxPhysical ?: Double.POSITIVE_INFINITY)
        }
        if (maxPhysical != null && resultValue > maxPhysical) {
            return PidDecodeResult.OutOfPhysicalRange(resultValue, minPhysical ?: Double.NEGATIVE_INFINITY, maxPhysical)
        }

        return PidDecodeResult.Success(
            value = resultValue,
            unit = unit,
            rawHex = bytes.joinToString("") { "%02X".format(it) },
        )
    }

    fun evaluateOrNull(formula: String, bytes: List<Int>): Float? {
        val result = decode(formula, bytes)
        return (result as? PidDecodeResult.Success)?.value?.toFloat()
    }

    @Deprecated("Use decode() to handle explicit truth states; evaluate() no longer returns synthetic 0f on failure.")
    fun evaluate(formula: String, bytes: List<Int>): Float {
        return evaluateOrNull(formula, bytes) ?: Float.NaN
    }

    private fun eval(str: String): Double {
        return object : Any() {
            var pos = -1
            var ch = 0

            fun nextChar() {
                ch = if (++pos < str.length) str[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < str.length) throw RuntimeException("Unexpected: " + ch.toChar())
                return x
            }

            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    if (eat('+'.code)) x += parseTerm()
                    else if (eat('-'.code)) x -= parseTerm()
                    else return x
                }
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    if (eat('*'.code)) x *= parseFactor()
                    else if (eat('/'.code)) {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        x /= divisor
                    }
                    else return x
                }
            }

            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor()
                if (eat('-'.code)) return -parseFactor()

                var x: Double
                val startPos = pos
                if (eat('('.code)) {
                    x = parseExpression()
                    eat(')'.code)
                } else if (ch in '0'.code..'9'.code || ch == '.'.code) {
                    while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar()
                    x = java.lang.Double.parseDouble(str.substring(startPos, pos))
                } else {
                    throw RuntimeException("Unexpected character: " + ch.toChar())
                }

                if (eat('^'.code)) x = x.pow(parseFactor())

                return x
            }
        }.parse()
    }
}

