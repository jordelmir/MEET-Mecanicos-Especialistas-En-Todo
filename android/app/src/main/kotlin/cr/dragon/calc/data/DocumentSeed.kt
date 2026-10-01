package cr.dragon.calc.data

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import cr.dragon.calc.ui.document.Cell
import cr.dragon.calc.ui.document.CellType
import java.util.UUID

/**
 * DocumentSeed: Generates the "El Vuelo del Dragón" master notebook.
 * This is the premium onboarding experience for first-time users.
 */
object DocumentSeed {

    fun getElVueloDelDragon(): List<Cell> {
        return listOf(
            createCell(
                CellType.MARKDOWN,
                "# 🐉 El Vuelo del Dragón\n\nBienvenido a **Elysium Vanguard**. Este cuaderno demuestra el poder unificado de nuestros motores. Desliza, edita y evalúa (=) cada celda."
            ),
            createCell(
                CellType.MARKDOWN,
                "### 1. El Núcleo CAS (Matemática Simbólica)\nDerivadas e integrales se resuelven visualmente."
            ),
            createCell(
                CellType.MATH,
                "d/dx(x^2 * sin(x))"
            ),
            createCell(
                CellType.MARKDOWN,
                "### 2. Motor de Física RK4\nSimulaciones de estado con variables en tiempo real. Mantén presionado para copiar resultados."
            ),
            createCell(
                CellType.PHYSICS,
                "simulate(gravity, 10s)"
            ),
            createCell(
                CellType.MARKDOWN,
                "### 3. Motor Estequiométrico\nBalanceo de ecuaciones químicas avanzadas."
            ),
            createCell(
                CellType.CHEMISTRY,
                "C6H12O6 + O2 -> CO2 + H2O"
            )
        )
    }

    private fun createCell(type: CellType, text: String): Cell {
        return Cell(
            id = UUID.randomUUID().toString(),
            type = type,
            content = TextFieldValue(text = text, selection = TextRange(text.length))
        )
    }
}
