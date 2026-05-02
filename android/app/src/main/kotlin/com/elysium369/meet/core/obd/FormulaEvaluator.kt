package com.elysium369.meet.core.obd

import java.util.*
import kotlin.math.pow

/**
 * FormulaEvaluator — High-performance math engine for OBD2 formulas.
 * Evaluates string formulas like "(A*256+B)/4" or "A*0.0625-40".
 * Supports variables A, B, C, D (bytes) and standard operators.
 */
object FormulaEvaluator {

    fun evaluate(formula: String, bytes: List<Int>): Float {
        if (formula.isBlank()) return 0f
        
        // Replace variables A, B, C, D with their values
        var expression = formula.uppercase()
        val vars = mapOf(
            "A" to (bytes.getOrNull(0) ?: 0),
            "B" to (bytes.getOrNull(1) ?: 0),
            "C" to (bytes.getOrNull(2) ?: 0),
            "D" to (bytes.getOrNull(3) ?: 0),
            "E" to (bytes.getOrNull(4) ?: 0),
            "F" to (bytes.getOrNull(5) ?: 0)
        )

        for ((name, value) in vars) {
            expression = expression.replace(Regex("\\b$name\\b"), value.toString())
        }

        return try {
            eval(expression).toFloat()
        } catch (e: Exception) {
            0f
        }
    }

    private fun eval(str: String): Double {
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
                        x = if (divisor == 0.0) 0.0 else x / divisor // zero-safe division
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
