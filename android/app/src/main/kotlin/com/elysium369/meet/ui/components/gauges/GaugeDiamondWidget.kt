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
 * Diamond Luxury Style: Premium chrome/silver with ice blue accents.
 * Inspired by Bugatti's mechanical watch instrument cluster.
 * Brushed silver ring, fine engraved tick marks, elegant thin needle, ice blue glow.
 */
@Composable
fun GaugeDiamondWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal),
        spring(dampingRatio = 0.85f, stiffness = 90f), label = "diamond")
    val inf = rememberInfiniteTransition(label = "dp")
    val shimmer by inf.animateFloat(0.6f, 1f,
        infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ds")
    val tm = rememberTextMeasurer()

    val iceBlue = Color(0xFF88CCFF)
    val chrome = Color(0xFFCCCCCC)
    val silver = Color(0xFF999999)
    val gold = Color(0xFFD4AF37)

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(4.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 14.dp.toPx()
        val sweep = 250f; val start = 145f

        val lbl = tm.measure(label.uppercase(), TextStyle(color = silver.copy(alpha = 0.5f), fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp))

        // Number labels at major ticks
        val numLabels = List(6) { i ->
            val v = minVal + (i.toFloat() / 5f) * (maxVal - minVal)
            val text = if (v >= 1000) "${(v / 1000).toInt()}" else String.format("%.0f", v)
            val a = Math.toRadians((start + (i.toFloat() / 5f) * sweep).toDouble())
            val labelR = r - 22.dp.toPx()
            Pair(
                tm.measure(text, TextStyle(color = chrome.copy(alpha = 0.6f), fontSize = 7.sp, fontWeight = FontWeight.Bold)),
                Offset((cx + labelR * cos(a)).toFloat(), (cy + labelR * sin(a)).toFloat())
            )
        }

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val col = when {
                isAnomaly -> Color(0xFFFF4444)
                criticalThreshold != null && animVal >= criticalThreshold -> Color(0xFFFF4444)
                warningThreshold != null && animVal >= warningThreshold -> gold
                else -> iceBlue
            }

            // Outer chrome bezel ring — brushed metal look
            drawCircle(Color.White.copy(alpha = 0.06f), r + 8.dp.toPx(), Offset(cx, cy))
            drawCircle(chrome.copy(alpha = 0.12f), r + 8.dp.toPx(), Offset(cx, cy), style = Stroke(2.dp.toPx()))
            drawCircle(chrome.copy(alpha = 0.06f), r + 5.dp.toPx(), Offset(cx, cy), style = Stroke(0.5f.dp.toPx()))

            // Inner dial face
            drawCircle(Color(0xFF0A0A12), r - 2.dp.toPx(), Offset(cx, cy))

            // Fine engraved tick marks
            for (i in 0..50) {
                val a = Math.toRadians((start + i.toFloat() / 50 * sweep).toDouble())
                val isMaj = i % 10 == 0
                val isMid = i % 5 == 0
                val oR = r
                val len = when { isMaj -> 14.dp.toPx(); isMid -> 9.dp.toPx(); else -> 4.dp.toPx() }
                val tickW = when { isMaj -> 1.5f.dp.toPx(); isMid -> 1.dp.toPx(); else -> 0.5f.dp.toPx() }
                val tickAlpha = when { isMaj -> 0.7f; isMid -> 0.4f; else -> 0.15f }
                drawLine(chrome.copy(alpha = tickAlpha * shimmer),
                    Offset((cx + oR * cos(a)).toFloat(), (cy + oR * sin(a)).toFloat()),
                    Offset((cx + (oR - len) * cos(a)).toFloat(), (cy + (oR - len) * sin(a)).toFloat()),
                    tickW)
            }

            // Number labels
            numLabels.forEach { (measured, pos) ->
                drawText(measured, topLeft = Offset(pos.x - measured.size.width / 2f, pos.y - measured.size.height / 2f))
            }

            // Ice blue glow arc for progress
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)
            if (prog > 0) {
                drawArc(col.copy(alpha = shimmer * 0.08f), start, prog * sweep, false,
                    topLeft = aO, size = aS, style = Stroke(20.dp.toPx(), cap = StrokeCap.Round))
                drawArc(col.copy(alpha = 0.15f), start, prog * sweep, false,
                    topLeft = Offset(cx - r + 14.dp.toPx(), cy - r + 14.dp.toPx()),
                    size = Size((r - 14.dp.toPx()) * 2, (r - 14.dp.toPx()) * 2),
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }

            // Elegant thin needle — silver with white tip
            val nA = Math.toRadians((start + prog * sweep).toDouble())
            val needleLen = r - 8.dp.toPx()
            val counterLen = 15.dp.toPx()
            val tip = Offset((cx + needleLen * cos(nA)).toFloat(), (cy + needleLen * sin(nA)).toFloat())
            val counter = Offset((cx - counterLen * cos(nA)).toFloat(), (cy - counterLen * sin(nA)).toFloat())
            // Counter-weight
            drawLine(silver.copy(alpha = 0.4f), Offset(cx, cy), counter, 3.dp.toPx(), cap = StrokeCap.Round)
            // Main needle body
            drawLine(silver.copy(alpha = 0.7f), Offset(cx, cy), tip, 1.5f.dp.toPx(), cap = StrokeCap.Round)
            // White tip highlight
            val tipHL = Offset((cx + (needleLen - 10.dp.toPx()) * cos(nA)).toFloat(), (cy + (needleLen - 10.dp.toPx()) * sin(nA)).toFloat())
            drawLine(Color.White, tipHL, tip, 1.5f.dp.toPx(), cap = StrokeCap.Round)
            // Center jewel
            drawCircle(gold, 4.dp.toPx(), Offset(cx, cy))
            drawCircle(Color(0xFF1A1A20), 2.5f.dp.toPx(), Offset(cx, cy))
            drawCircle(gold.copy(alpha = shimmer * 0.3f), 8.dp.toPx(), Offset(cx, cy))

            // Value below
            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy + 22.dp.toPx()))
            val ut = tm.measure(unit.lowercase(), TextStyle(color = col, fontSize = 9.sp, fontWeight = FontWeight.Bold))
            drawText(ut, topLeft = Offset(cx - ut.size.width / 2f, cy + 22.dp.toPx() + vt.size.height))
            drawText(lbl, topLeft = Offset(cx - lbl.size.width / 2f, cy + 22.dp.toPx() + vt.size.height + ut.size.height + 2.dp.toPx()))

            // Brand mark
            val brand = tm.measure("MEET", TextStyle(color = silver.copy(alpha = 0.2f), fontSize = 6.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp))
            drawText(brand, topLeft = Offset(cx - brand.size.width / 2f, cy - 28.dp.toPx()))
        }
    })
}
