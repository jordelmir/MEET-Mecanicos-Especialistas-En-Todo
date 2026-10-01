package cr.dragon.calc.ui.document

import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import dragoncore.parser.Expr
import java.util.UUID

/**
 * Representa los diferentes tipos de celda que pueden existir en el documento.
 */
@Serializable
enum class CellType {
    MATH,       // Ejecuta matemática regular
    GRAPH,      // Evalúa y dibuja 2D
    GRAPH_3D,   // Renderiza superficie 3D `z = f(x,y)`
    MARKDOWN,   // Texto de documentación rico
    PROMPT,     // Natural language prompt for Neuro-Symbolic reasoning
    PHYSICS,    // Simulación física en tiempo real (Solver ODE + Canvas)
    CHEMISTRY,  // Balanceo químico y visualización molecular
    STATISTICS, // Análisis de datos y simulaciones Monte Carlo
    DATAFRAME   // Visualización y análisis de tablas de datos
}

/**
 * DragonDoc Cell [🧠V7]
 */
@Serializable
data class Cell(
    val id: String = UUID.randomUUID().toString(),
    val type: CellType = CellType.MATH,
    val rawText: String = "",
    @Transient val content: TextFieldValue = TextFieldValue(rawText),
    val result: String = "",
    val isError: Boolean = false,
    val isExecuting: Boolean = false,
    val translatedContent: String = "",
    val evaluationSteps: List<String> = emptyList(),
    val physicsState: Map<String, Double> = emptyMap(),
    val chemState: String = "",
    val aiExplanation: String? = null,
    val aiModuleType: String? = null,
    val aiDragonCode: String? = null,
    val sourceEngine: String? = null,
    @Transient val ast: Expr? = null
)

/**
 * Encapsula la coleccion completa, metadatos y titulo.
 */
@Serializable
data class DragonDocument(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "Untitled Document",
    val cells: List<Cell> = listOf(Cell(type = CellType.MATH)),
    val lastModified: Long = System.currentTimeMillis(),
    val angleMode: String = "RADIAN", // Persistent Mode
    val focusedCellId: String? = null
)
