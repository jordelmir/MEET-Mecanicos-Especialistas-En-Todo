package com.elysium369.meet.ui.screens.dragoncalc

import dragoncore.evaluator.DragonContext
import dragoncore.evaluator.DragonEvaluator
import dragoncore.lexer.DragonLexer
import dragoncore.parser.DragonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DragonCalcEngineIntegrationTest {
    private fun evaluate(input: String, context: DragonContext = DragonContext()): Double =
        DragonEvaluator().evaluate(DragonParser(DragonLexer().tokenize(input)).parse(), context)

    @Test fun precedenceAndScientificFunctionsUseOriginalDragonCore() {
        assertEquals(14.0, evaluate("2 + 3 * 4"), 1e-9)
        val degrees = DragonContext().apply { angleMode = DragonContext.AngleMode.DEGREE }
        assertEquals(0.5, evaluate("sin(30)", degrees), 1e-9)
    }

    @Test fun variableStateRemainsInOneCalculatorSession() {
        val context = DragonContext()
        assertEquals(5.0, evaluate("x = 5", context), 1e-9)
        assertEquals(8.0, evaluate("x + 3", context), 1e-9)
    }

    @Test fun interactiveInputRejectsDragonScriptControlFlow() {
        val parsed = DragonParser(DragonLexer().tokenize("while(1){1}")).parse()
        assertThrows(IllegalArgumentException::class.java) {
            DragonCalcExpressionPolicy.requireInteractive(parsed)
        }
    }
}
