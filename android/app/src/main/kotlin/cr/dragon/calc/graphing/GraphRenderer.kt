package cr.dragon.calc.graphing

import android.graphics.Path as AndroidPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import dragoncore.evaluator.DragonContext
import dragoncore.evaluator.DragonEvaluator
import dragoncore.parser.Expr

object GraphRenderer {

    fun buildGraphPath(
        expr: Expr,
        viewport: GraphViewport,
        screenWidth: Float,
        screenHeight: Float,
        evaluator: DragonEvaluator,
        context: DragonContext
    ): Path {
        val androidPath = AndroidPath()
        if (screenWidth <= 0f || screenHeight <= 0f) return androidPath.asComposePath()

        var isFirstPoint = true
        var previousPixelY = Float.NaN

        // Evaluate per pixel on X axis (step 2 for optimization)
        for (pixelX in 0..screenWidth.toInt() step 2) {
            val mathX = viewport.toMathX(pixelX.toFloat(), screenWidth)

            // Re-use context and evaluate
            context.setVariable("x", mathX)
            val mathY = try {
                evaluator.evaluate(expr, context)
            } catch (e: Exception) {
                Double.NaN
            }

            if (mathY.isNaN() || mathY.isInfinite()) {
                isFirstPoint = true
                continue
            }

            val pixelY = viewport.toPixelY(mathY, screenHeight)

            // Asymptote detection: if the delta Y between adjacent pixels is huge, break the path
            if (!previousPixelY.isNaN() && Math.abs(pixelY - previousPixelY) > screenHeight * 2) {
                isFirstPoint = true
            }

            if (isFirstPoint) {
                androidPath.moveTo(pixelX.toFloat(), pixelY)
                isFirstPoint = false
            } else {
                androidPath.lineTo(pixelX.toFloat(), pixelY)
            }
            previousPixelY = pixelY
        }

        return androidPath.asComposePath()
    }
}
