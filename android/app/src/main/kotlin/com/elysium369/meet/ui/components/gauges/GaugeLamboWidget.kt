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
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.components.gauges.LocalGaugeColorScheme

/**
 * Lamborghini Style: Angular hexagonal gauge inspired by the Reventón cockpit.
 * Electric orange/amber on pitch black with sharp angular tick marks, dual unit speedometer,
 * and diamond-shaped pointer.
 */
@Composable
fun GaugeLamboWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val colorScheme = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal),
        spring(dampingRatio = 0.65f, stiffness = 180f), label = "lambo")
    val inf = rememberInfiniteTransition(label = "lp")
    val glow by inf.animateFloat(0.4f, 0.9f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "lg")
    val tm = rememberTextMeasurer()

    val lamboOrange = colorScheme.internalColor
    val lamboAmber = colorScheme.specialColor
    val lamboRed = colorScheme.needleColor

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(4.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 16.dp.toPx()
        val sweep = 260f; val start = 140f
        val tickCount = 30
        
        // Speedometer dual scale calculation
        val isSpeedGauge = unit.equals("km/h", ignoreCase = true) || unit.equals("mph", ignoreCase = true)
        val isKmH = unit.equals("km/h", ignoreCase = true)
        val secondaryUnit = if (isKmH) "mph" else "km/h"
        val speedConversion = if (isKmH) 0.621371f else (1f / 0.621371f)
        val secondaryMax = maxVal * speedConversion

        val ticks = List(tickCount + 1) { i ->
            val a = Math.toRadians((start + i.toFloat() / tickCount * sweep).toDouble())
            val isMaj = i % 5 == 0
            val oR = r + 4.dp.toPx()
            val len = if (isMaj) 14.dp.toPx() else 6.dp.toPx()
            
            // Major tick labels for primary scale
            val labelResult = if (isMaj) {
                val labelVal = minVal + (i.toFloat() / tickCount) * (maxVal - minVal)
                val text = if (labelVal >= 1000) "${(labelVal / 1000).toInt()}"
                           else if (labelVal == labelVal.toInt().toFloat()) "${labelVal.toInt()}"
                           else String.format("%.0f", labelVal)
                val labelR = r + len + 10.dp.toPx()
                val labelOffset = Offset(
                    (cx + labelR * cos(a)).toFloat(),
                    (cy + labelR * sin(a)).toFloat()
                )
                val measured = tm.measure(
                    text = text,
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                )
                Pair(measured, labelOffset)
            } else null

            Triple(
                Offset((cx + oR * cos(a)).toFloat(), (cy + oR * sin(a)).toFloat()),
                Offset((cx + (oR + len) * cos(a)).toFloat(), (cy + (oR + len) * sin(a)).toFloat()),
                labelResult
            )
        }
        val lbl = tm.measure(label.uppercase(), TextStyle(color = colorScheme.labelColor.copy(alpha = 0.5f), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace))

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

            // Angular ticks & primary numbers
            ticks.forEachIndexed { i, (s, e, labelResult) ->
                val isMaj = i % 5 == 0
                drawLine(if (isMaj) col.copy(alpha = 0.8f) else col.copy(alpha = 0.25f), s, e,
                    if (isMaj) 2.5f.dp.toPx() else 1.dp.toPx())

                labelResult?.let { (measured, pos) ->
                    drawText(measured, topLeft = Offset(pos.x - measured.size.width / 2f, pos.y - measured.size.height / 2f))
                }
            }

            // Speedometer dual scale (inner concentric)
            if (isSpeedGauge) {
                val innerR = r - 12.dp.toPx()
                // Inner concentric line
                drawArc(
                    color = col.copy(alpha = 0.15f),
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

                    drawLine(col.copy(alpha = 0.3f), startPt, endPt, 1.dp.toPx())

                    val labelR = innerR - 10.dp.toPx()
                    val labelMeasured = tm.measure(
                        text = String.format("%.0f", vSec),
                        style = TextStyle(color = col.copy(alpha = 0.4f), fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
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
            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = colorScheme.textColor, fontSize = 26.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy + 18.dp.toPx()))
            
            val activeUnitColor = when {
                isAnomaly -> lamboRed
                criticalThreshold != null && animVal >= criticalThreshold -> lamboRed
                warningThreshold != null && animVal >= warningThreshold -> lamboAmber
                else -> colorScheme.unitColor
            }
            val ut = tm.measure(unit.uppercase(), TextStyle(color = activeUnitColor, fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace))
            drawText(ut, topLeft = Offset(cx - ut.size.width / 2f, cy + 18.dp.toPx() + vt.size.height))
            
            var currentOffset = cy + 18.dp.toPx() + vt.size.height + ut.size.height
            if (isSpeedGauge) {
                val secValText = String.format("%.0f %s", animVal * speedConversion, secondaryUnit.uppercase())
                val secMeasured = tm.measure(secValText, TextStyle(color = col.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
                drawText(secMeasured, topLeft = Offset(cx - secMeasured.size.width / 2f, currentOffset + 2.dp.toPx()))
                currentOffset += secMeasured.size.height + 2.dp.toPx()
            }
            
            drawText(lbl, topLeft = Offset(cx - lbl.size.width / 2f, currentOffset + 2.dp.toPx()))
        }
    })
}
