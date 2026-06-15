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
 * Tokyo Drift Style: Japanese street racing aesthetic.
 * Deep midnight blue (#000033) + sakura pink (#FF69B4) + ice white accents.
 * Inspired by JDM culture with clean lines and sharp angular elements.
 */
@Composable
fun GaugeTokyoWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal),
        spring(dampingRatio = 0.7f, stiffness = 160f), label = "tokyo")
    val inf = rememberInfiniteTransition(label = "tp")
    val scanLine by inf.animateFloat(0f, 1f,
        infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "ts")
    val glow by inf.animateFloat(0.5f, 1f,
        infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "tg")
    val tm = rememberTextMeasurer()

    val midnightBlue = Color(0xFF000844)
    val sakuraPink = Color(0xFFFF69B4)
    val iceWhite = Color(0xFFE0E8FF)
    val neonBlue = Color(0xFF4466FF)

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(4.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 16.dp.toPx()
        val sweep = 240f; val start = 150f

        val lbl = tm.measure(label.uppercase(), TextStyle(color = sakuraPink.copy(alpha = 0.5f), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace))

        // Tick marks pre-calc
        val ticks = List(31) { i ->
            val a = Math.toRadians((start + i.toFloat() / 30 * sweep).toDouble())
            val isMaj = i % 5 == 0
            val innerR = r - if (isMaj) 16.dp.toPx() else 8.dp.toPx()
            Pair(
                Offset((cx + r * cos(a)).toFloat(), (cy + r * sin(a)).toFloat()),
                Offset((cx + innerR * cos(a)).toFloat(), (cy + innerR * sin(a)).toFloat())
            ) to isMaj
        }

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val col = when {
                isAnomaly -> sakuraPink
                criticalThreshold != null && animVal >= criticalThreshold -> sakuraPink
                warningThreshold != null && animVal >= warningThreshold -> Color(0xFFFFAA00)
                else -> neonBlue
            }

            // Deep midnight background
            drawCircle(midnightBlue.copy(alpha = 0.4f), r + 8.dp.toPx(), Offset(cx, cy))

            // Diagonal rain lines (JDM aesthetic)
            for (i in 0 until 8) {
                val x = size.width * (i / 8f + scanLine * 0.125f) % size.width
                drawLine(neonBlue.copy(alpha = 0.03f),
                    Offset(x, 0f), Offset(x - 20.dp.toPx(), size.height), 1.dp.toPx())
            }

            // Background arc — double line
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)
            drawArc(Color.White.copy(alpha = 0.04f), start, sweep, false,
                style = Stroke(10.dp.toPx(), cap = StrokeCap.Round), topLeft = aO, size = aS)
            drawArc(Color.White.copy(alpha = 0.08f), start, sweep, false,
                style = Stroke(1.dp.toPx(), cap = StrokeCap.Round), topLeft = aO, size = aS)

            // Inner reference ring
            val iR = r - 20.dp.toPx()
            drawArc(neonBlue.copy(alpha = 0.05f), start, sweep, false,
                style = Stroke(1.dp.toPx()), topLeft = Offset(cx - iR, cy - iR), size = Size(iR * 2, iR * 2))

            // Progress — dual-tone gradient
            drawArc(col.copy(alpha = glow * 0.12f), start, prog * sweep, false,
                style = Stroke(24.dp.toPx(), cap = StrokeCap.Round), topLeft = aO, size = aS)
            drawArc(Brush.sweepGradient(0f to neonBlue, 0.7f to sakuraPink, 1f to neonBlue),
                start, prog * sweep, false,
                style = Stroke(6.dp.toPx(), cap = StrokeCap.Round), topLeft = aO, size = aS)
            // White hot core
            drawArc(iceWhite.copy(alpha = 0.3f), start, prog * sweep, false,
                style = Stroke(1.5f.dp.toPx(), cap = StrokeCap.Round), topLeft = aO, size = aS)

            // Ticks — inner style (JDM clean)
            ticks.forEach { (pair, isMaj) ->
                val (outer, inner) = pair
                val tickProg = ((outer.x - cx) / r).let { prog } // approximate
                drawLine(
                    if (isMaj) iceWhite.copy(alpha = 0.5f) else neonBlue.copy(alpha = 0.15f),
                    outer, inner,
                    if (isMaj) 2.dp.toPx() else 1.dp.toPx()
                )
            }

            // Kanji-style decorative marks at cardinal positions
            val decoTexts = listOf("低", "中", "高")
            val decoPositions = listOf(0.0f, 0.5f, 1.0f)
            decoTexts.zip(decoPositions).forEach { (text, frac) ->
                val a = Math.toRadians((start + frac * sweep).toDouble())
                val tR = r + 14.dp.toPx()
                val pos = Offset((cx + tR * cos(a)).toFloat(), (cy + tR * sin(a)).toFloat())
                val m = tm.measure(text, TextStyle(color = sakuraPink.copy(alpha = 0.3f), fontSize = 8.sp, fontWeight = FontWeight.Bold))
                drawText(m, topLeft = Offset(pos.x - m.size.width / 2f, pos.y - m.size.height / 2f))
            }

            // Needle — sharp, thin, sakura pink tip
            val needleA = Math.toRadians((start + prog * sweep).toDouble())
            val needleLen = r - 4.dp.toPx()
            val tip = Offset((cx + needleLen * cos(needleA)).toFloat(), (cy + needleLen * sin(needleA)).toFloat())
            val mid = Offset((cx + 15.dp.toPx() * cos(needleA)).toFloat(), (cy + 15.dp.toPx() * sin(needleA)).toFloat())
            drawLine(neonBlue.copy(alpha = 0.4f), Offset(cx, cy), mid, 3.dp.toPx(), cap = StrokeCap.Round)
            drawLine(sakuraPink, mid, tip, 2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(neonBlue, 4.dp.toPx(), Offset(cx, cy))
            drawCircle(midnightBlue, 2.dp.toPx(), Offset(cx, cy))

            // Value
            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = iceWhite, fontSize = 26.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy + 22.dp.toPx()))
            val ut = tm.measure(unit.lowercase(), TextStyle(color = sakuraPink, fontSize = 10.sp, fontWeight = FontWeight.Bold))
            drawText(ut, topLeft = Offset(cx - ut.size.width / 2f, cy + 22.dp.toPx() + vt.size.height))
            drawText(lbl, topLeft = Offset(cx - lbl.size.width / 2f, cy + 22.dp.toPx() + vt.size.height + ut.size.height + 2.dp.toPx()))
        }
    })
}
