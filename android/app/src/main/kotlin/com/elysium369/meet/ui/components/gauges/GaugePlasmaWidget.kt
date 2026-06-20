package com.elysium369.meet.ui.components.gauges

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import com.elysium369.meet.ui.components.gauges.LocalGaugeColorScheme

/**
 * Plasma Style: Electric blue/purple plasma energy field with lightning arc effects.
 * Deep electric blue (#0044FF) + violet (#8800FF) + white plasma core.
 * Multiple orbiting energy rings with pulsating plasma nodes.
 */
@Composable
fun GaugePlasmaWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val colorScheme = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal),
        spring(dampingRatio = 0.7f, stiffness = 120f), label = "plasma")
    val inf = rememberInfiniteTransition(label = "pp")
    val orbit by inf.animateFloat(0f, 360f,
        infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "po")
    val pulse by inf.animateFloat(0.3f, 1f,
        infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ppulse")
    val crackle by inf.animateFloat(0f, 6.28f,
        infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart), label = "pc")
    val tm = rememberTextMeasurer()

    val plasmaBlue = colorScheme.internalColor
    val plasmaViolet = colorScheme.specialColor
    val plasmaWhite = colorScheme.textColor

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(4.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val sweep = 270f; val start = 135f
        val lbl = tm.measure(label.uppercase(), TextStyle(color = colorScheme.labelColor.copy(alpha = 0.6f), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace))

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val col = when {
                isAnomaly -> colorScheme.needleColor
                criticalThreshold != null && animVal >= criticalThreshold -> colorScheme.needleColor
                warningThreshold != null && animVal >= warningThreshold -> colorScheme.specialColor
                else -> plasmaBlue
            }

            // Outer orbiting energy ring (dashed, rotating)
            val oR = r + 8.dp.toPx()
            for (i in 0 until 16) {
                val a = Math.toRadians((orbit + i * 22.5).toDouble())
                val dotR = 1.5f.dp.toPx()
                drawCircle(plasmaViolet.copy(alpha = 0.3f), dotR,
                    Offset((cx + oR * cos(a)).toFloat(), (cy + oR * sin(a)).toFloat()))
            }

            // Background ring
            drawArc(col.copy(alpha = 0.05f), start, sweep, false,
                topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2),
                style = Stroke(14.dp.toPx(), cap = StrokeCap.Round))

            // Plasma energy arc — 4 layers for extreme glow
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)
            drawArc(col.copy(alpha = pulse * 0.08f), start, prog * sweep, false,
                topLeft = aO, size = aS, style = Stroke(30.dp.toPx(), cap = StrokeCap.Round))
            drawArc(col.copy(alpha = pulse * 0.15f), start, prog * sweep, false,
                topLeft = aO, size = aS, style = Stroke(18.dp.toPx(), cap = StrokeCap.Round))
            drawArc(Brush.sweepGradient(0f to plasmaBlue, 0.5f to plasmaViolet, 1f to plasmaBlue),
                start, prog * sweep, false,
                topLeft = aO, size = aS, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
            drawArc(plasmaWhite.copy(alpha = 0.6f), start, prog * sweep, false,
                topLeft = aO, size = aS, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))

            // Plasma nodes at endpoints
            val endA = Math.toRadians((start + prog * sweep).toDouble())
            val endP = Offset((cx + r * cos(endA)).toFloat(), (cy + r * sin(endA)).toFloat())
            drawCircle(col.copy(alpha = pulse * 0.3f), 14.dp.toPx(), endP)
            drawCircle(plasmaWhite, 4.dp.toPx(), endP)

            // Inner energy ring (counter-rotating)
            val iR = r - 22.dp.toPx()
            for (i in 0 until 8) {
                val a = Math.toRadians((-orbit * 1.5f + i * 45f).toDouble())
                drawArc(plasmaViolet.copy(alpha = 0.06f), (a * 180 / Math.PI).toFloat(), 20f, false,
                    topLeft = Offset(cx - iR, cy - iR), size = Size(iR * 2, iR * 2), style = Stroke(2.dp.toPx()))
            }

            // Lightning crackle lines (3 random-ish arcs)
            for (i in 0 until 3) {
                val phase = crackle + i * 2.09f
                val a1 = Math.toRadians((start + (sin(phase).toFloat() * 0.5f + 0.5f) * prog * sweep).toDouble())
                val a2 = a1 + 0.15
                val p1 = Offset((cx + (r - 5.dp.toPx()) * cos(a1)).toFloat(), (cy + (r - 5.dp.toPx()) * sin(a1)).toFloat())
                val p2 = Offset((cx + (r - 18.dp.toPx()) * cos(a2)).toFloat(), (cy + (r - 18.dp.toPx()) * sin(a2)).toFloat())
                if (prog > 0.1f) {
                    drawLine(plasmaWhite.copy(alpha = pulse * 0.4f), p1, p2, 1.dp.toPx())
                }
            }

            // Center core
            drawCircle(col.copy(alpha = pulse * 0.08f), 30.dp.toPx(), Offset(cx, cy))
            drawCircle(plasmaViolet.copy(alpha = 0.04f), 20.dp.toPx(), Offset(cx, cy))

            // Value
            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = plasmaWhite, fontSize = 28.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 8.dp.toPx()))
            val activeUnitColor = when {
                isAnomaly -> colorScheme.needleColor
                criticalThreshold != null && animVal >= criticalThreshold -> colorScheme.needleColor
                warningThreshold != null && animVal >= warningThreshold -> colorScheme.specialColor
                else -> colorScheme.unitColor
            }
            val ut = tm.measure(unit.lowercase(), TextStyle(color = activeUnitColor, fontSize = 11.sp, fontWeight = FontWeight.Bold))
            drawText(ut, topLeft = Offset(cx - ut.size.width / 2f, cy + 10.dp.toPx()))
            drawText(lbl, topLeft = Offset(cx - lbl.size.width / 2f, cy + 26.dp.toPx()))
        }
    })
}
