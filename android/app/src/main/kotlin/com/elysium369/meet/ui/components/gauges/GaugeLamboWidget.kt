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

/**
 * Lamborghini Style: Angular hexagonal gauge inspired by the Reventón fighter-jet cockpit.
 * Electric orange/amber on pitch black with sharp angular tick marks and diamond-shaped pointer.
 */
@Composable
fun GaugeLamboWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal),
        spring(dampingRatio = 0.65f, stiffness = 180f), label = "lambo")
    val inf = rememberInfiniteTransition(label = "lp")
    val glow by inf.animateFloat(0.4f, 0.9f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "lg")
    val tm = rememberTextMeasurer()

    val lamboOrange = Color(0xFFFF6B00)
    val lamboAmber = Color(0xFFFFAA00)
    val lamboRed = Color(0xFFFF1744)

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(4.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 16.dp.toPx()
        val sweep = 260f; val start = 140f
        val tickCount = 30
        val ticks = List(tickCount + 1) { i ->
            val a = Math.toRadians((start + i.toFloat() / tickCount * sweep).toDouble())
            val isMaj = i % 5 == 0
            val oR = r + 4.dp.toPx()
            val len = if (isMaj) 14.dp.toPx() else 6.dp.toPx()
            Triple(
                Offset((cx + oR * cos(a)).toFloat(), (cy + oR * sin(a)).toFloat()),
                Offset((cx + (oR + len) * cos(a)).toFloat(), (cy + (oR + len) * sin(a)).toFloat()),
                isMaj
            )
        }
        val lbl = tm.measure(label.uppercase(), TextStyle(color = lamboOrange.copy(alpha = 0.5f), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace))

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val col = when {
                isAnomaly -> lamboRed
                criticalThreshold != null && animVal >= criticalThreshold -> lamboRed
                warningThreshold != null && animVal >= warningThreshold -> lamboAmber
                else -> lamboOrange
            }
            val arcS = Size(r * 2, r * 2); val arcO = Offset(cx - r, cy - r)

            // Hexagonal background marks (6 lines radiating from center)
            for (i in 0 until 6) {
                val a = Math.toRadians((i * 60.0) + 30.0)
                drawLine(Color.White.copy(alpha = 0.03f), Offset(cx, cy),
                    Offset((cx + r * 1.2f * cos(a)).toFloat(), (cy + r * 1.2f * sin(a)).toFloat()), 1.dp.toPx())
            }

            // Background arc — angular dashes
            drawArc(Color.White.copy(alpha = 0.05f), start, sweep, false,
                topLeft = arcO, size = arcS,
                style = Stroke(6.dp.toPx(), cap = StrokeCap.Butt, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))))

            // Progress arc — triple glow layer
            drawArc(col.copy(alpha = glow * 0.15f), start, prog * sweep, false,
                topLeft = arcO, size = arcS, style = Stroke(20.dp.toPx(), cap = StrokeCap.Butt))
            drawArc(col.copy(alpha = glow * 0.4f), start, prog * sweep, false,
                topLeft = arcO, size = arcS, style = Stroke(10.dp.toPx(), cap = StrokeCap.Butt))
            drawArc(col, start, prog * sweep, false,
                topLeft = arcO, size = arcS, style = Stroke(4.dp.toPx(), cap = StrokeCap.Butt))

            // Angular ticks
            ticks.forEach { (s, e, maj) ->
                drawLine(if (maj) col.copy(alpha = 0.8f) else col.copy(alpha = 0.25f), s, e,
                    if (maj) 2.5f.dp.toPx() else 1.dp.toPx())
            }

            // Diamond pointer
            val needleA = Math.toRadians((start + prog * sweep).toDouble())
            val needleR = r - 8.dp.toPx()
            val tip = Offset((cx + needleR * cos(needleA)).toFloat(), (cy + needleR * sin(needleA)).toFloat())
            val perpA = needleA + Math.PI / 2
            val baseR = 20.dp.toPx()
            val baseL = Offset((cx + baseR * cos(perpA)).toFloat(), (cy + baseR * sin(perpA)).toFloat())
            val baseRt = Offset((cx + baseR * cos(perpA + Math.PI)).toFloat(), (cy + baseR * sin(perpA + Math.PI)).toFloat())
            val path = Path().apply { moveTo(tip.x, tip.y); lineTo(baseL.x, baseL.y); lineTo(cx, cy); lineTo(baseRt.x, baseRt.y); close() }
            drawPath(path, col.copy(alpha = 0.6f))
            drawPath(path, col, style = Stroke(1.5f.dp.toPx()))
            drawCircle(col, 3.dp.toPx(), Offset(cx, cy))

            // Value
            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy + 18.dp.toPx()))
            val ut = tm.measure(unit.uppercase(), TextStyle(color = col, fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace))
            drawText(ut, topLeft = Offset(cx - ut.size.width / 2f, cy + 18.dp.toPx() + vt.size.height))
            drawText(lbl, topLeft = Offset(cx - lbl.size.width / 2f, cy + 18.dp.toPx() + vt.size.height + ut.size.height + 2.dp.toPx()))
        }
    })
}
