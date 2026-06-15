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
 * Carbon Cockpit Style: Aircraft instrument panel with amber/green phosphor.
 * Inspired by Boeing/Airbus glass cockpit instruments.
 * Amber (#FF8800) primary with green (#00FF00) secondary. Altitude-indicator style.
 */
@Composable
fun GaugeCockpitWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal),
        spring(dampingRatio = 0.8f, stiffness = 120f), label = "cockpit")
    val inf = rememberInfiniteTransition(label = "cp")
    val scanY by inf.animateFloat(0f, 1f,
        infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart), label = "csy")
    val tm = rememberTextMeasurer()

    val amber = Color(0xFFFF8800)
    val cockpitGreen = Color(0xFF00DD00)
    val cockpitBg = Color(0xFF060808)

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(4.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 14.dp.toPx()
        val sweep = 240f; val start = 150f

        val lbl = tm.measure(label.uppercase(), TextStyle(color = amber.copy(alpha = 0.5f), fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace))

        // Pre-calc scale labels
        val scaleLabels = List(7) { i ->
            val v = minVal + (i.toFloat() / 6f) * (maxVal - minVal)
            val text = if (v >= 1000) "${(v / 1000).toInt()}" else String.format("%.0f", v)
            val a = Math.toRadians((start + (i.toFloat() / 6f) * sweep).toDouble())
            val labelR = r + 16.dp.toPx()
            Pair(
                tm.measure(text, TextStyle(color = amber.copy(alpha = 0.7f), fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)),
                Offset((cx + labelR * cos(a)).toFloat(), (cy + labelR * sin(a)).toFloat())
            )
        }

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val col = when {
                isAnomaly -> Color(0xFFFF0000)
                criticalThreshold != null && animVal >= criticalThreshold -> Color(0xFFFF0000)
                warningThreshold != null && animVal >= warningThreshold -> amber
                else -> cockpitGreen
            }

            // Cockpit background
            drawCircle(cockpitBg, r + 10.dp.toPx(), Offset(cx, cy))

            // Horizontal scan line (CRT)
            val scanLineY = size.height * scanY
            drawLine(cockpitGreen.copy(alpha = 0.04f), Offset(0f, scanLineY), Offset(size.width, scanLineY), 2.dp.toPx())

            // Outer bezel — double ring (aircraft instrument)
            drawCircle(amber.copy(alpha = 0.15f), r + 4.dp.toPx(), Offset(cx, cy), style = Stroke(2.dp.toPx()))
            drawCircle(amber.copy(alpha = 0.06f), r + 7.dp.toPx(), Offset(cx, cy), style = Stroke(1.dp.toPx()))

            // Tick marks — aviation style (inside the ring)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)
            for (i in 0..36) {
                val a = Math.toRadians((start + i.toFloat() / 36 * sweep).toDouble())
                val isMaj = i % 6 == 0
                val len = if (isMaj) 12.dp.toPx() else 5.dp.toPx()
                val oR = r
                drawLine(amber.copy(alpha = if (isMaj) 0.7f else 0.2f),
                    Offset((cx + oR * cos(a)).toFloat(), (cy + oR * sin(a)).toFloat()),
                    Offset((cx + (oR - len) * cos(a)).toFloat(), (cy + (oR - len) * sin(a)).toFloat()),
                    if (isMaj) 2.dp.toPx() else 1.dp.toPx())
            }

            // Scale numbers
            scaleLabels.forEach { (measured, pos) ->
                drawText(measured, topLeft = Offset(pos.x - measured.size.width / 2f, pos.y - measured.size.height / 2f))
            }

            // Warning arc zone (amber)
            val warnFrac = if (warningThreshold != null && maxVal > minVal) ((warningThreshold - minVal) / (maxVal - minVal)).coerceIn(0f, 1f) else 0.75f
            val critFrac = if (criticalThreshold != null && maxVal > minVal) ((criticalThreshold - minVal) / (maxVal - minVal)).coerceIn(0f, 1f) else 0.9f
            val warnStart = start + warnFrac * sweep
            drawArc(amber.copy(alpha = 0.15f), warnStart, (critFrac - warnFrac) * sweep, false,
                topLeft = aO, size = aS, style = Stroke(4.dp.toPx(), cap = StrokeCap.Butt))
            // Critical arc zone (red)
            val critStart = start + critFrac * sweep
            drawArc(Color.Red.copy(alpha = 0.2f), critStart, (1f - critFrac) * sweep, false,
                topLeft = aO, size = aS, style = Stroke(4.dp.toPx(), cap = StrokeCap.Butt))

            // Progress arc — thin green/amber
            drawArc(col.copy(alpha = 0.08f), start, prog * sweep, false,
                topLeft = aO, size = aS, style = Stroke(12.dp.toPx(), cap = StrokeCap.Butt))

            // Needle — aircraft-style white with black outline
            val nA = Math.toRadians((start + prog * sweep).toDouble())
            val nLen = r - 6.dp.toPx()
            val tip = Offset((cx + nLen * cos(nA)).toFloat(), (cy + nLen * sin(nA)).toFloat())
            // Triangle needle
            val nPerp = nA + Math.PI / 2
            val baseW = 4.dp.toPx()
            val needlePath = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo((cx + baseW * cos(nPerp)).toFloat(), (cy + baseW * sin(nPerp)).toFloat())
                lineTo((cx - baseW * cos(nPerp)).toFloat(), (cy - baseW * sin(nPerp)).toFloat())
                close()
            }
            drawPath(needlePath, Color.White.copy(alpha = 0.9f))
            drawPath(needlePath, cockpitBg, style = Stroke(1.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.8f), 4.dp.toPx(), Offset(cx, cy))
            drawCircle(cockpitBg, 2.dp.toPx(), Offset(cx, cy))

            // Digital readout box at bottom
            val boxW = 60.dp.toPx(); val boxH = 20.dp.toPx()
            val boxY = cy + 28.dp.toPx()
            drawRect(cockpitBg, Offset(cx - boxW / 2, boxY), Size(boxW, boxH))
            drawRect(col.copy(alpha = 0.3f), Offset(cx - boxW / 2, boxY), Size(boxW, boxH), style = Stroke(1.dp.toPx()))

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = col, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, boxY + (boxH - vt.size.height) / 2f))

            val ut = tm.measure(unit.uppercase(), TextStyle(color = amber.copy(alpha = 0.6f), fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
            drawText(ut, topLeft = Offset(cx - ut.size.width / 2f, boxY + boxH + 4.dp.toPx()))
            drawText(lbl, topLeft = Offset(cx - lbl.size.width / 2f, boxY + boxH + 4.dp.toPx() + ut.size.height + 2.dp.toPx()))
        }
    })
}
