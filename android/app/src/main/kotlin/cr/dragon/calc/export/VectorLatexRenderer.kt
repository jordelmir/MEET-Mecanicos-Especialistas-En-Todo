package cr.dragon.calc.export

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import dragoncore.parser.Expr
import dragoncore.lexer.TokenType

/**
 * La Imprenta — VectorLatexRenderer
 *
 * Motor de trazado vectorial para fórmulas matemáticas sobre Android Canvas.
 * Renderiza el AST directamente en el contexto del PDF sin usar mapas de bits.
 */
class VectorLatexRenderer(private val paint: Paint) {

    /**
     * Dibuja una expresión en las coordenadas dadas y retorna el ancho ocupado.
     */
    fun drawExpr(canvas: Canvas, expr: Expr, x: Float, y: Float, size: Float): Float {
        val originalSize = paint.textSize
        paint.textSize = size
        val width = render(canvas, expr, x, y)
        paint.textSize = originalSize
        return width
    }

    private fun render(canvas: Canvas, expr: Expr, x: Float, y: Float): Float = when (expr) {
        is Expr.NumberExpr -> {
            val text = formatNumber(expr.value)
            canvas.drawText(text, x, y, paint)
            paint.measureText(text)
        }

        is Expr.VariableExpr -> {
            canvas.drawText(expr.name, x, y, paint)
            paint.measureText(expr.name)
        }

        is Expr.ConstantExpr -> {
            val sym = when (expr.name.lowercase()) {
                "pi" -> "π"
                "phi" -> "φ"
                "inf", "infinity" -> "∞"
                else -> expr.name
            }
            canvas.drawText(sym, x, y, paint)
            paint.measureText(sym)
        }

        is Expr.BinaryExpr -> {
            when (expr.operator.type) {
                TokenType.DIVIDE -> {
                    // Fracción Vectorial
                    val topWidth = measure(expr.left)
                    val bottomWidth = measure(expr.right)
                    val fracWidth = maxOf(topWidth, bottomWidth) + 10f

                    val lineY = y - (paint.textSize * 0.3f)
                    canvas.drawLine(x, lineY, x + fracWidth, lineY, paint)

                    val oldSize = paint.textSize
                    paint.textSize = oldSize * 0.8f

                    render(canvas, expr.left, x + (fracWidth - topWidth) / 2, y - (oldSize * 0.5f) - 5f)
                    render(canvas, expr.right, x + (fracWidth - bottomWidth) / 2, y + (oldSize * 0.5f) + 2f)

                    paint.textSize = oldSize
                    fracWidth
                }
                TokenType.POWER -> {
                    val baseWidth = render(canvas, expr.left, x, y)
                    val oldSize = paint.textSize
                    paint.textSize = oldSize * 0.6f
                    render(canvas, expr.right, x + baseWidth + 2f, y - (oldSize * 0.4f))
                    paint.textSize = oldSize
                    baseWidth + paint.measureText(" ") * 2f // Espaciado heurístico
                }
                TokenType.PLUS, TokenType.MINUS -> {
                    val w1 = render(canvas, expr.left, x, y)
                    val op = " ${expr.operator.value} "
                    canvas.drawText(op, x + w1, y, paint)
                    val wOp = paint.measureText(op)
                    val w2 = render(canvas, expr.right, x + w1 + wOp, y)
                    w1 + wOp + w2
                }
                TokenType.MULTIPLY -> {
                    val w1 = render(canvas, expr.left, x, y)
                    val op = " · "
                    canvas.drawText(op, x + w1, y, paint)
                    val wOp = paint.measureText(op)
                    val w2 = render(canvas, expr.right, x + w1 + wOp, y)
                    w1 + wOp + w2
                }
                else -> {
                    val text = expr.toString()
                    canvas.drawText(text, x, y, paint)
                    paint.measureText(text)
                }
            }
        }

        is Expr.FunctionExpr -> {
            val name = expr.name.value
            canvas.drawText("$name(", x, y, paint)
            var currentX = x + paint.measureText("$name(")
            expr.arguments.forEachIndexed { i, arg ->
                currentX += render(canvas, arg, currentX, y)
                if (i < expr.arguments.size - 1) {
                    canvas.drawText(", ", currentX, y, paint)
                    currentX += paint.measureText(", ")
                }
            }
            canvas.drawText(")", currentX, y, paint)
            currentX + paint.measureText(")") - x
        }

        is Expr.IntegralExpr -> {
            // Símbolo de Integral Vectorial
            val oldSize = paint.textSize
            paint.textSize = oldSize * 1.5f
            canvas.drawText("∫", x, y + 2f, paint)
            val symWidth = paint.measureText("∫")
            paint.textSize = oldSize

            val exprWidth = render(canvas, expr.expression, x + symWidth + 5f, y)
            val dVar = " d${expr.variable.name}"
            canvas.drawText(dVar, x + symWidth + 5f + exprWidth, y, paint)

            symWidth + 5f + exprWidth + paint.measureText(dVar)
        }

        is Expr.DerivativeExpr -> {
            // d/dx Vectorial (como fracción)
            val oldSize = paint.textSize
            paint.textSize = oldSize * 0.8f

            val topText = "d"
            val bottomText = "d${expr.variable.name}"
            val topW = paint.measureText(topText)
            val botW = paint.measureText(bottomText)
            val fracW = maxOf(topW, botW) + 4f

            val lineY = y - (oldSize * 0.3f)
            canvas.drawLine(x, lineY, x + fracW, lineY, paint)

            canvas.drawText(topText, x + (fracW - topW)/2, y - (oldSize * 0.5f), paint)
            canvas.drawText(bottomText, x + (fracW - botW)/2, y + (oldSize * 0.4f), paint)

            paint.textSize = oldSize
            val innerW = render(canvas, expr.expression, x + fracW + 10f, y)
            fracW + 10f + innerW
        }

        else -> {
            val text = expr.toString()
            canvas.drawText(text, x, y, paint)
            paint.measureText(text)
        }
    }

    private fun measure(expr: Expr): Float {
        val originalSize = paint.textSize
        val w = when (expr) {
            is Expr.NumberExpr -> paint.measureText(formatNumber(expr.value))
            is Expr.VariableExpr -> paint.measureText(expr.name)
            is Expr.ConstantExpr -> paint.measureText("π") // Heurística para símbolos
            is Expr.BinaryExpr -> {
                when (expr.operator.type) {
                    TokenType.DIVIDE -> {
                        val top = measure(expr.left)
                        val bot = measure(expr.right)
                        maxOf(top, bot) + 10f
                    }
                    TokenType.POWER -> {
                        val base = measure(expr.left)
                        paint.textSize = originalSize * 0.6f
                        val p = measure(expr.right)
                        paint.textSize = originalSize
                        base + p + 2f
                    }
                    else -> measure(expr.left) + paint.measureText(" + ") + measure(expr.right)
                }
            }
            is Expr.FunctionExpr -> paint.measureText(expr.name.value + "(...)")
            else -> paint.measureText(expr.toString())
        }
        paint.textSize = originalSize
        return w
    }

    private fun formatNumber(v: Double): String {
        return if (v == Math.floor(v)) v.toLong().toString()
        else "%.4g".format(v).trimEnd('0').trimEnd('.')
    }
}
