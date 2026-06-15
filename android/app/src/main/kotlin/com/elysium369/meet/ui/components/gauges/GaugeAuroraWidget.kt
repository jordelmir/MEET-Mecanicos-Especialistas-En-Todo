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
 * Midnight Aurora Style: Deep purple/violet aurora borealis effect.
 * Colors flow like northern lights — deep purple (#2D0050), magenta (#FF00AA),
 * teal (#00FFD4), with flowing gradient that shifts.
 */
@Composable
fun GaugeAuroraWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal),
        spring(dampingRatio = 0.8f, stiffness = 100f), label = "aurora")
    val inf = rememberInfiniteTransition(label = "ap")
    val colorShift by inf.animateFloat(0f, 1f,
        infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "acs")
    val breathe by inf.animateFloat(0.5f, 1f,
        infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ab")
    val tm = rememberTextMeasurer()

    val auroraPurple = Color(0xFF6600CC)
    val auroraMagenta = Color(0xFFFF00AA)
    val auroraTeal = Color(0xFF00FFD4)
    val auroraDeep = Color(0xFF1A0033)

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(4.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 16.dp.toPx()
        val sweep = 240f; val start = 150f
        val ringW = 16.dp.toPx()
        val lbl = tm.measure(label.uppercase(), TextStyle(color = auroraTeal.copy(alpha = 0.5f), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp))

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            // Deep space background circle
            drawCircle(auroraDeep.copy(alpha = 0.3f), r + 8.dp.toPx(), Offset(cx, cy))

            // Aurora shimmer behind arc (wide, flowing)
            val shimmerColors = listOf(
                auroraPurple.copy(alpha = breathe * 0.15f),
                auroraMagenta.copy(alpha = breathe * 0.1f),
                auroraTeal.copy(alpha = breathe * 0.12f),
                auroraPurple.copy(alpha = breathe * 0.08f)
            )
            drawArc(Brush.sweepGradient(shimmerColors), start - 10f, sweep + 20f, false,
                topLeft = aO, size = aS, style = Stroke(ringW * 4f, cap = StrokeCap.Round))

            // Background track
            drawArc(Color.White.copy(alpha = 0.04f), start, sweep, false,
                topLeft = aO, size = aS, style = Stroke(ringW, cap = StrokeCap.Round))

            // Main aurora arc — shifting gradient
            val shift = colorShift
            val gradColors = listOf(
                auroraTeal,
                auroraPurple,
                auroraMagenta,
                if (isAnomaly) Color(0xFFFF0044) else auroraTeal
            )
            // Outer glow
            drawArc(Brush.sweepGradient(gradColors), start, prog * sweep, false,
                topLeft = aO, size = aS, style = Stroke(ringW * 2.5f, cap = StrokeCap.Round))
            // Core arc
            drawArc(Brush.sweepGradient(gradColors), start, prog * sweep, false,
                topLeft = aO, size = aS, style = Stroke(ringW, cap = StrokeCap.Round))
            // Bright inner edge
            drawArc(Color.White.copy(alpha = 0.15f), start, prog * sweep, false,
                topLeft = Offset(cx - r + ringW / 2, cy - r + ringW / 2),
                size = Size((r - ringW / 2) * 2, (r - ringW / 2) * 2),
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))

            // Aurora particles floating
            for (i in 0 until 12) {
                val phase = colorShift * 360f + i * 30f
                val pA = Math.toRadians(phase.toDouble())
                val pR = r * (0.4f + 0.5f * sin(colorShift * 6.28 + i).toFloat())
                val pAlpha = (0.05f + 0.1f * sin(colorShift * 6.28 + i * 0.7).toFloat()).coerceIn(0f, 0.15f)
                val pColor = if (i % 3 == 0) auroraTeal else if (i % 3 == 1) auroraMagenta else auroraPurple
                drawCircle(pColor.copy(alpha = pAlpha), 3.dp.toPx(),
                    Offset((cx + pR * cos(pA)).toFloat(), (cy + pR * sin(pA)).toFloat()))
            }

            // Tick marks — soft, aurora-colored
            for (i in 0..20) {
                val a = Math.toRadians((start + i.toFloat() / 20f * sweep).toDouble())
                val isMaj = i % 5 == 0
                val oR = r + 2.dp.toPx()
                val len = if (isMaj) 8.dp.toPx() else 4.dp.toPx()
                drawLine(auroraTeal.copy(alpha = if (isMaj) 0.4f else 0.12f),
                    Offset((cx + oR * cos(a)).toFloat(), (cy + oR * sin(a)).toFloat()),
                    Offset((cx + (oR + len) * cos(a)).toFloat(), (cy + (oR + len) * sin(a)).toFloat()),
                    if (isMaj) 2.dp.toPx() else 1.dp.toPx())
            }

            // Value
            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
            val ut = tm.measure(unit.lowercase(), TextStyle(color = auroraTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold))
            drawText(ut, topLeft = Offset(cx - ut.size.width / 2f, cy + 14.dp.toPx()))
            drawText(lbl, topLeft = Offset(cx - lbl.size.width / 2f, cy + 30.dp.toPx()))
        }
    })
}
