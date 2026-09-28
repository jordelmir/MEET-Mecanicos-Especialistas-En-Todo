package cr.dragon.calc.graphing

import dragoncore.evaluator.DragonContext
import dragoncore.evaluator.DragonEvaluator
import dragoncore.evaluator.EvaluatorException
import dragoncore.lexer.DragonLexer
import dragoncore.parser.DragonParser

/**
 * Elysium Vanguard - GraphEngine
 *
 * Calcula puntos (x, y) evaluando una funcion matematica sobre un rango,
 * mapeando coordenadas cartesianas a pixeles de pantalla.
 *
 * Uso:
 *   val engine = GraphEngine()
 *   val points = engine.computePoints("sin(x)", xMin=-10, xMax=10, width=1080, height=720)
 */
class GraphEngine {

    private val lexer = DragonLexer()
    private val context = DragonContext()

    /**
     * Punto mapeado a coordenadas de pixel.
     *
     * @param px  Coordenada X en pixeles (para Canvas)
     * @param py  Coordenada Y en pixeles (para Canvas)
     * @param x   Coordenada X en el espacio matematico
     * @param y   Coordenada Y en el espacio matematico
     * @param valid  false si la evaluacion fallo (division por cero, dominio, etc.)
     */
    data class GraphPoint(
        val px: Float,
        val py: Float,
        val x: Double,
        val y: Double,
        val valid: Boolean = true
    )

    /**
     * Evalua una expresion en funcion de x y devuelve puntos mapeados a pixeles.
     *
     * @param expression  La expresion como string (ej. "sin(x)", "x^2 - 3")
     * @param xMin        Limite izquierdo del rango visible
     * @param xMax        Limite derecho del rango visible
     * @param yMin        Limite inferior del rango visible
     * @param yMax        Limite superior del rango visible
     * @param widthPx     Ancho del Canvas en pixeles
     * @param heightPx    Alto del Canvas en pixeles
     * @param numPoints   Cantidad de puntos a evaluar (mas = mas suave)
     * @return Lista de GraphPoint mapeados a pixeles
     */
    fun computePoints(
        expression: String,
        xMin: Double,
        xMax: Double,
        yMin: Double,
        yMax: Double,
        widthPx: Int,
        heightPx: Int,
        numPoints: Int = widthPx  // 1 punto por pixel = maxima resolucion
    ): List<GraphPoint> {
        if (numPoints <= 0 || xMax <= xMin) return emptyList()

        val dx = (xMax - xMin) / numPoints
        val xRange = xMax - xMin
        val yRange = yMax - yMin

        val points = mutableListOf<GraphPoint>()

        // Pre-parsear la expresion una vez para reutilizar el AST
        val tokens = try { lexer.tokenize(expression) } catch (e: Exception) { return emptyList() }
        val ast = try { DragonParser(tokens).parse() } catch (e: Exception) { return emptyList() }
        val evaluator = DragonEvaluator()

        for (i in 0..numPoints) {
            val x = xMin + i * dx

            // Asignar x en el contexto
            context.setVariable("x", x)

            try {
                val y = evaluator.evaluate(ast, context)

                // Saltar NaN e infinitos
                if (y.isNaN() || y.isInfinite()) {
                    points.add(GraphPoint(0f, 0f, x, y, valid = false))
                    continue
                }

                // Mapear coordenadas matematicas a pixeles
                val px = ((x - xMin) / xRange * widthPx).toFloat()
                val py = ((yMax - y) / yRange * heightPx).toFloat()  // Y invertido

                points.add(GraphPoint(px, py, x, y, valid = true))
            } catch (e: Exception) {
                points.add(GraphPoint(0f, 0f, x, Double.NaN, valid = false))
            }
        }

        return points
    }

    /** Sugiere limites Y basados en los valores evaluados. */
    fun autoYRange(
        expression: String,
        xMin: Double,
        xMax: Double,
        samples: Int = 200
    ): Pair<Double, Double> {
        val dx = (xMax - xMin) / samples
        val tokens = try { lexer.tokenize(expression) } catch (e: Exception) { return -10.0 to 10.0 }
        val ast = try { DragonParser(tokens).parse() } catch (e: Exception) { return -10.0 to 10.0 }
        val evaluator = DragonEvaluator()

        var yMinFound = Double.MAX_VALUE
        var yMaxFound = -Double.MAX_VALUE

        for (i in 0..samples) {
            val x = xMin + i * dx
            context.setVariable("x", x)
            try {
                val y = evaluator.evaluate(ast, context)
                if (y.isFinite()) {
                    if (y < yMinFound) yMinFound = y
                    if (y > yMaxFound) yMaxFound = y
                }
            } catch (_: Exception) {}
        }

        if (yMinFound >= yMaxFound) return -10.0 to 10.0

        // Agregar 10% de margen
        val margin = (yMaxFound - yMinFound) * 0.1
        return (yMinFound - margin) to (yMaxFound + margin)
    }

    /** Actualiza el modo angular. */
    fun setAngleMode(mode: DragonContext.AngleMode) {
        context.angleMode = mode
    }
}
