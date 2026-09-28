package cr.dragon.calc.graphing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import dragoncore.evaluator.DragonContext
import dragoncore.evaluator.DragonEvaluator
import dragoncore.parser.Expr

/**
 * Motor de renderizado de superficies 3D (z = f(x, y)).
 *
 * Pipeline:
 *  1. Genera mesh grid evaluando la expresión AST en cada punto (xi, yi)
 *  2. Forma cuadriláteros (quads) desde 4 puntos adyacentes de la malla
 *  3. Proyecta cada quad a coordenadas de pantalla via Graph3DViewport
 *  4. Ordena por profundidad (Painter's Algorithm)
 *  5. Asigna color basado en altura Z (gradiente: azul → teal → verde → naranja)
 */
object SurfaceRenderer {

    /** Representa un polígono (quad) proyectado listo para dibujar. */
    data class ProjectedQuad(
        val points: List<Offset>,    // 4 vértices en coordenadas de pantalla
        val depth: Double,           // Profundidad promedio respecto a la cámara
        val fillColor: Color,        // Color basado en altura Z
        val strokeColor: Color       // Color del wireframe
    )

    /**
     * Genera la lista de quads proyectados y ordenados (back-to-front)
     * para una superficie z = f(x, y).
     *
     * @param expr      La expresión AST que define f(x, y)
     * @param viewport  Estado de la cámara 3D
     * @param evaluator Instancia del DragonEvaluator
     * @param context   Contexto con modo angular (se inyectarán x, y como variables)
     * @param screenW   Ancho del Canvas en píxeles
     * @param screenH   Alto del Canvas en píxeles
     * @param gridSteps Número de subdivisiones en cada eje (más = más detalle)
     */
    fun buildSurface(
        expr: Expr,
        viewport: Graph3DViewport,
        evaluator: DragonEvaluator,
        context: DragonContext,
        screenW: Float,
        screenH: Float,
        gridSteps: Int = 40
    ): List<ProjectedQuad> {
        if (screenW <= 0f || screenH <= 0f) return emptyList()

        val xRange = viewport.xMax - viewport.xMin
        val yRange = viewport.yMax - viewport.yMin
        val stepX = xRange / gridSteps
        val stepY = yRange / gridSteps

        // 1. Evaluar la malla de puntos Z
        val zGrid = Array(gridSteps + 1) { DoubleArray(gridSteps + 1) }
        var zMinGlobal = Double.MAX_VALUE
        var zMaxGlobal = -Double.MAX_VALUE

        for (i in 0..gridSteps) {
            val xi = viewport.xMin + i * stepX
            for (j in 0..gridSteps) {
                val yj = viewport.yMin + j * stepY
                context.setVariable("x", xi)
                context.setVariable("y", yj)
                val z = try {
                    evaluator.evaluate(expr, context)
                } catch (_: Exception) {
                    Double.NaN
                }
                zGrid[i][j] = z
                if (!z.isNaN() && !z.isInfinite()) {
                    if (z < zMinGlobal) zMinGlobal = z
                    if (z > zMaxGlobal) zMaxGlobal = z
                }
            }
        }

        // Prevenir rango nulo
        if (zMinGlobal >= zMaxGlobal) {
            zMaxGlobal = zMinGlobal + 1.0
        }

        // 2. Formar quads y proyectar
        val quads = mutableListOf<ProjectedQuad>()
        for (i in 0 until gridSteps) {
            val x0 = viewport.xMin + i * stepX
            val x1 = x0 + stepX
            for (j in 0 until gridSteps) {
                val y0 = viewport.yMin + j * stepY
                val y1 = y0 + stepY

                val z00 = zGrid[i][j]
                val z10 = zGrid[i + 1][j]
                val z11 = zGrid[i + 1][j + 1]
                val z01 = zGrid[i][j + 1]

                // Saltar quads con valores inválidos
                if (z00.isNaN() || z00.isInfinite() ||
                    z10.isNaN() || z10.isInfinite() ||
                    z11.isNaN() || z11.isInfinite() ||
                    z01.isNaN() || z01.isInfinite()) continue

                // Limitar Z extremos para estabilidad visual
                val clampedZ = listOf(z00, z10, z11, z01).map { it.coerceIn(-20.0, 20.0) }

                // Proyectar los 4 vértices
                val p00 = viewport.project(x0, y0, clampedZ[0], screenW, screenH)
                val p10 = viewport.project(x1, y0, clampedZ[1], screenW, screenH)
                val p11 = viewport.project(x1, y1, clampedZ[2], screenW, screenH)
                val p01 = viewport.project(x0, y1, clampedZ[3], screenW, screenH)

                if (p00 == null || p10 == null || p11 == null || p01 == null) continue

                // Profundidad promedio de la cara
                val avgZ = (clampedZ[0] + clampedZ[1] + clampedZ[2] + clampedZ[3]) / 4.0
                val depth = viewport.cameraDepth(
                    (x0 + x1) / 2.0,
                    (y0 + y1) / 2.0,
                    avgZ
                )

                // Color basado en altura (gradiente multi-stop)
                val fillColor = heightToColor(avgZ, zMinGlobal, zMaxGlobal)
                val strokeColor = Color(0x30FFFFFF) // Wireframe tenue

                quads.add(ProjectedQuad(
                    points = listOf(
                        Offset(p00.first, p00.second),
                        Offset(p10.first, p10.second),
                        Offset(p11.first, p11.second),
                        Offset(p01.first, p01.second)
                    ),
                    depth = depth,
                    fillColor = fillColor,
                    strokeColor = strokeColor
                ))
            }
        }

        // 3. Painter's Algorithm: ordenar de lejos a cerca
        quads.sortBy { it.depth }

        return quads
    }

    // ================================================================
    // GRADIENTE DE ALTURA (Z → Color)
    // ================================================================

    /**
     * Mapea un valor Z normalizado [0, 1] a un gradiente de color:
     *   0.0 → Azul Profundo (#1A237E)
     *   0.25 → Teal (#00897B)
     *   0.50 → Verde (#4CAF50)
     *   0.75 → Amarillo (#FFB300)
     *   1.0 → Naranja (#FF5722)
     */
    private fun heightToColor(z: Double, zMin: Double, zMax: Double): Color {
        val t = ((z - zMin) / (zMax - zMin)).coerceIn(0.0, 1.0).toFloat()
        return when {
            t < 0.25f -> lerpColor(Color(0xFF1A237E), Color(0xFF00897B), t / 0.25f)
            t < 0.50f -> lerpColor(Color(0xFF00897B), Color(0xFF4CAF50), (t - 0.25f) / 0.25f)
            t < 0.75f -> lerpColor(Color(0xFF4CAF50), Color(0xFFFFB300), (t - 0.50f) / 0.25f)
            else      -> lerpColor(Color(0xFFFFB300), Color(0xFFFF5722), (t - 0.75f) / 0.25f)
        }
    }

    private fun lerpColor(a: Color, b: Color, t: Float): Color {
        return Color(
            red   = a.red   + (b.red - a.red) * t,
            green = a.green + (b.green - a.green) * t,
            blue  = a.blue  + (b.blue - a.blue) * t,
            alpha = 0.88f
        )
    }
}
