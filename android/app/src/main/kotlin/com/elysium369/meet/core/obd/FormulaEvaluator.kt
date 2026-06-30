package com.elysium369.meet.core.obd

import java.util.*
import kotlin.math.pow

/**
 * FormulaEvaluator — High-performance math engine for OBD2 formulas.
 * Evaluates string formulas like "(A*256+B)/4" or "A*0.0625-40".
 * Supports variables A, B, C, D (bytes) and standard operators.
 */
object FormulaEvaluator {

    private val PATTERN_A = Regex("\\bA\\b")
    private val PATTERN_B = Regex("\\bB\\b")
    private val PATTERN_C = Regex("\\bC\\b")
    private val PATTERN_D = Regex("\\bD\\b")
    private val PATTERN_E = Regex("\\bE\\b")
    private val PATTERN_F = Regex("\\bF\\b")

    fun evaluate(formula: String, bytes: List<Int>): Float {
        if (formula.isBlank()) return 0f
        return evaluateInternal(formula, bytes, zeroSafeDivision = true) ?: 0f
    }

    fun evaluateOrNull(formula: String, bytes: List<Int>): Float? {
        if (formula.isBlank()) return null
        return evaluateInternal(formula, bytes, zeroSafeDivision = false)
    }

    private fun evaluateInternal(formula: String, bytes: List<Int>, zeroSafeDivision: Boolean): Float? {
        // Replace variables A, B, C, D, E, F with their values using precompiled patterns
        var expression = formula.uppercase()
        
        expression = PATTERN_A.replace(expression, (bytes.getOrNull(0) ?: 0).toString())
        expression = PATTERN_B.replace(expression, (bytes.getOrNull(1) ?: 0).toString())
        expression = PATTERN_C.replace(expression, (bytes.getOrNull(2) ?: 0).toString())
        expression = PATTERN_D.replace(expression, (bytes.getOrNull(3) ?: 0).toString())
        expression = PATTERN_E.replace(expression, (bytes.getOrNull(4) ?: 0).toString())
        expression = PATTERN_F.replace(expression, (bytes.getOrNull(5) ?: 0).toString())

        return try {
            eval(expression, zeroSafeDivision).toFloat()
        } catch (e: Exception) {
            null
        }
    }

    private fun eval(str: String, zeroSafeDivision: Boolean): Double {
        return object : Any() {
            var pos = -1
            var ch = 0

            fun nextChar() {
                ch = if (++pos < str.length) str[pos].toInt() else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.toInt()) nextChar()
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
                    if (eat('+'.toInt())) x += parseTerm() // addition
                    else if (eat('-'.toInt())) x -= parseTerm() // subtraction
                    else return x
                }
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    if (eat('*'.toInt())) x *= parseFactor() // multiplication
                    else if (eat('/'.toInt())) {
                        val divisor = parseFactor()
                        x = if (divisor == 0.0) {
                            if (zeroSafeDivision) 0.0 else throw ArithmeticException("Division by zero")
                        } else {
                            x / divisor
                        }
                    }
                    else return x
                }
            }

            fun parseFactor(): Double {
                if (eat('+'.toInt())) return parseFactor() // unary plus
                if (eat('-'.toInt())) return -parseFactor() // unary minus

                var x: Double
                val startPos = pos
                if (eat('('.toInt())) { // parentheses
                    x = parseExpression()
                    eat(')'.toInt())
                } else if (ch >= '0'.toInt() && ch <= '9'.toInt() || ch == '.'.toInt()) { // numbers
                    while (ch >= '0'.toInt() && ch <= '9'.toInt() || ch == '.'.toInt()) nextChar()
                    x = java.lang.Double.parseDouble(str.substring(startPos, pos))
                } else {
                    throw RuntimeException("Unexpected: " + ch.toChar())
                }

                if (eat('^'.toInt())) x = x.pow(parseFactor()) // exponentiation

                return x
            }
        }.parse()
    }
}
