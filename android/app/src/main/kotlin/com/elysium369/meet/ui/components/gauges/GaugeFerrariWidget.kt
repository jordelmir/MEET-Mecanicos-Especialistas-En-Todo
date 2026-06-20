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
 * Ferrari Rosso Style: Bold Italian red with yellow shift indicators.
 * Inspired by Ferrari's Manettino dial and prancing horse instruments.
 * Deep rosso corsa red (#DC0000) + giallo Modena yellow (#FFD700) + carbon black.
 */
@Composable
fun GaugeFerrariWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val colorScheme = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal),
        spring(dampingRatio = 0.6f, stiffness = 200f), label = "ferrari")
    val inf = rememberInfiniteTransition(label = "fp")
    val shiftFlash by inf.animateFloat(0.2f, 1f,
        infiniteRepeatable(tween(250, easing = LinearEasing), RepeatMode.Reverse), label = "ff")
    val tm = rememberTextMeasurer()

    val rossoCorsa = colorScheme.needleColor
    val gialloModena = colorScheme.specialColor
    val carbonBlack = Color(0xFF0A0A0A)

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(4.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 14.dp.toPx()
        val sweep = 250f; val start = 145f
        val totalSegs = 25
        val segGap = 3f
        val segSweep = (sweep - (totalSegs - 1) * segGap) / totalSegs

        val lbl = tm.measure(label.uppercase(), TextStyle(color = colorScheme.labelColor, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace))

        // Speedometer dual scale calculation
        val isSpeedGauge = unit.equals("km/h", ignoreCase = true) || unit.equals("mph", ignoreCase = true)
        val isKmH = unit.equals("km/h", ignoreCase = true)
        val secondaryUnit = if (isKmH) "mph" else "km/h"
        val speedConversion = if (isKmH) 0.621371f else (1f / 0.621371f)
        val secondaryMax = maxVal * speedConversion

        // Number labels for major ticks (every 5 segments)
        val numLabels = List(6) { i ->
            val v = minVal + (i.toFloat() / 5f) * (maxVal - minVal)
            val text = if (v >= 1000) "${(v / 1000).toInt()}" else String.format("%.0f", v)
            val a = Math.toRadians((start + (i.toFloat() / 5f) * sweep).toDouble())
            val labelR = r + 18.dp.toPx()
            Pair(
                tm.measure(text, TextStyle(color = Color.White.copy(alpha = 0.5f), fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)),
                Offset((cx + labelR * cos(a)).toFloat(), (cy + labelR * sin(a)).toFloat())
            )
        }

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val filledSegs = (prog * totalSegs).toInt()
            val isRedline = criticalThreshold != null && animVal >= criticalThreshold

            // Carbon fiber circle background
            drawCircle(carbonBlack, r + 10.dp.toPx(), Offset(cx, cy))
            drawCircle(Color.White.copy(alpha = 0.03f), r + 10.dp.toPx(), Offset(cx, cy), style = Stroke(1.dp.toPx()))

            // Segment arcs
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)
            for (i in 0 until totalSegs) {
                val segStart = start + i * (segSweep + segGap)
                val frac = i.toFloat() / totalSegs
                val isFilled = i < filledSegs

                val segColor = when {
                    !isFilled -> Color.White.copy(alpha = 0.06f)
                    isAnomaly -> rossoCorsa
                    frac >= 0.85f -> if (isRedline) rossoCorsa.copy(alpha = shiftFlash) else rossoCorsa
                    frac >= 0.7f -> gialloModena
                    else -> colorScheme.internalColor
                }

                // Segment
                drawArc(segColor, segStart, segSweep, false,
                    topLeft = aO, size = aS, style = Stroke(14.dp.toPx(), cap = StrokeCap.Butt))

                // Glow for filled
                if (isFilled) {
                    drawArc(segColor.copy(alpha = 0.2f), segStart, segSweep, false,
                        topLeft = aO, size = aS, style = Stroke(22.dp.toPx(), cap = StrokeCap.Butt))
                }
            }

            // Number labels
            numLabels.forEach { (measured, pos) ->
                drawText(measured, topLeft = Offset(pos.x - measured.size.width / 2f, pos.y - measured.size.height / 2f))
            }

            // Speedometer dual scale (inner concentric)
            if (isSpeedGauge) {
                val innerR = r - 16.dp.toPx()
                // Inner concentric line
                drawArc(
                    color = Color.White.copy(alpha = 0.1f),
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = 1.dp.toPx()),
                    topLeft = Offset(cx - innerR, cy - innerR),
                    size = Size(innerR * 2f, innerR * 2f)
                )

                val secondaryStep = if (secondaryMax > 200f) 40f else if (secondaryMax > 120f) 20f else 10f
                var vSec = 0f
                while (vSec <= secondaryMax) {
                    val angleFraction = vSec / secondaryMax
                    val angle = start + angleFraction * sweep
                    val angleRad = Math.toRadians(angle.toDouble())

                    val startPt = Offset(
                        (cx + innerR * cos(angleRad)).toFloat(),
                        (cy + innerR * sin(angleRad)).toFloat()
                    )
                    val endPt = Offset(
                        (cx + (innerR - 4.dp.toPx()) * cos(angleRad)).toFloat(),
                        (cy + (innerR - 4.dp.toPx()) * sin(angleRad)).toFloat()
                    )

                    drawLine(gialloModena.copy(alpha = 0.3f), startPt, endPt, 1.dp.toPx())

                    val labelR = innerR - 10.dp.toPx()
                    val labelMeasured = tm.measure(
                        text = String.format("%.0f", vSec),
                        style = TextStyle(color = gialloModena.copy(alpha = 0.4f), fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    )
                    drawText(
                        labelMeasured,
                        topLeft = Offset(
                            (cx + labelR * cos(angleRad)).toFloat() - labelMeasured.size.width / 2f,
                            (cy + labelR * sin(angleRad)).toFloat() - labelMeasured.size.height / 2f
                        )
                    )
                    vSec += secondaryStep
                }
            }

            // Shift lights at top (5 circles: 2 green, 1 yellow, 2 red)
            val lightColors = listOf(Color(0xFF00FF44), Color(0xFF00FF44), gialloModena, rossoCorsa, rossoCorsa)
            val shiftThreshold = listOf(0.6f, 0.7f, 0.8f, 0.9f, 0.95f)
            for (i in lightColors.indices) {
                val lx = cx + (i - 2) * 16.dp.toPx()
                val ly = 14.dp.toPx()
                val isLit = prog >= shiftThreshold[i]
                drawCircle(if (isLit) lightColors[i].copy(alpha = if (isRedline) shiftFlash else 1f) else Color.White.copy(alpha = 0.06f),
                    4.dp.toPx(), Offset(lx, ly))
                if (isLit) drawCircle(lightColors[i].copy(alpha = 0.3f), 8.dp.toPx(), Offset(lx, ly))
            }

            // Needle — thin, long, red
            val needleA = Math.toRadians((start + prog * sweep).toDouble())
            val needleLen = r - 6.dp.toPx()
            val needleTip = Offset((cx + needleLen * cos(needleA)).toFloat(), (cy + needleLen * sin(needleA)).toFloat())
            drawLine(rossoCorsa, Offset(cx, cy), needleTip, 2.5f.dp.toPx(), cap = StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.5f), Offset(cx, cy), needleTip, 1.dp.toPx(), cap = StrokeCap.Round)
            // Center cap
            drawCircle(rossoCorsa, 5.dp.toPx(), Offset(cx, cy))
            drawCircle(carbonBlack, 3.dp.toPx(), Offset(cx, cy))

            // Digital value
            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(
                color = if (isRedline) rossoCorsa.copy(alpha = shiftFlash) else colorScheme.textColor,
                fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy + 20.dp.toPx()))
            
            val ut = tm.measure(unit.uppercase(), TextStyle(color = colorScheme.unitColor, fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace))
            drawText(ut, topLeft = Offset(cx - ut.size.width / 2f, cy + 20.dp.toPx() + vt.size.height))
            
            var currentOffset = cy + 20.dp.toPx() + vt.size.height + ut.size.height
            if (isSpeedGauge) {
                val secValText = String.format("%.0f %s", animVal * speedConversion, secondaryUnit.uppercase())
                val secMeasured = tm.measure(secValText, TextStyle(color = colorScheme.needleColor.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
                drawText(secMeasured, topLeft = Offset(cx - secMeasured.size.width / 2f, currentOffset + 2.dp.toPx()))
                currentOffset += secMeasured.size.height + 2.dp.toPx()
            }

            drawText(lbl, topLeft = Offset(cx - lbl.size.width / 2f, currentOffset + 2.dp.toPx()))
        }
    })
}
