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
 * Military Night Ops Style: Night vision phosphor green on pure black.
 * Tactical grid overlay, crosshair center, compass-style tick marks.
 * Looks like looking through night vision goggles at a heads-up display.
 */
@Composable
fun GaugeMilitaryWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val colorScheme = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal),
        spring(dampingRatio = 0.8f, stiffness = 150f), label = "military")
    val inf = rememberInfiniteTransition(label = "mp")
    val scanRot by inf.animateFloat(0f, 360f,
        infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart), label = "ms")
    val flicker by inf.animateFloat(0.7f, 1f,
        infiniteRepeatable(tween(100, easing = LinearEasing), RepeatMode.Reverse), label = "mf")
    val tm = rememberTextMeasurer()

    val nvGreen = colorScheme.internalColor
    val nvDarkGreen = colorScheme.bezelColor

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(4.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 14.dp.toPx()
        val sweep = 270f; val start = 135f
        val lbl = tm.measure(label.uppercase(), TextStyle(color = colorScheme.labelColor.copy(alpha = 0.4f), fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace))

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val col = when {
                isAnomaly -> colorScheme.needleColor
                criticalThreshold != null && animVal >= criticalThreshold -> colorScheme.needleColor
                warningThreshold != null && animVal >= warningThreshold -> colorScheme.specialColor
                else -> nvGreen
            }
            val textCol = when {
                isAnomaly -> colorScheme.needleColor
                criticalThreshold != null && animVal >= criticalThreshold -> colorScheme.needleColor
                warningThreshold != null && animVal >= warningThreshold -> colorScheme.specialColor
                else -> colorScheme.textColor
            }
            val alpha = flicker // Slight NV flicker

            // NV vignette background
            drawCircle(nvDarkGreen.copy(alpha = 0.15f), r + 10.dp.toPx(), Offset(cx, cy))

            // Tactical grid
            val gridStep = 16.dp.toPx()
            for (x in 0..(size.width / gridStep).toInt()) {
                drawLine(nvGreen.copy(alpha = 0.03f * alpha), Offset(x * gridStep, 0f), Offset(x * gridStep, size.height), 1f)
            }
            for (y in 0..(size.height / gridStep).toInt()) {
                drawLine(nvGreen.copy(alpha = 0.03f * alpha), Offset(0f, y * gridStep), Offset(size.width, y * gridStep), 1f)
            }

            // Crosshair at center
            val chLen = 12.dp.toPx()
            val chGap = 6.dp.toPx()
            drawLine(col.copy(alpha = 0.3f * alpha), Offset(cx - chLen - chGap, cy), Offset(cx - chGap, cy), 1.dp.toPx())
            drawLine(col.copy(alpha = 0.3f * alpha), Offset(cx + chGap, cy), Offset(cx + chLen + chGap, cy), 1.dp.toPx())
            drawLine(col.copy(alpha = 0.3f * alpha), Offset(cx, cy - chLen - chGap), Offset(cx, cy - chGap), 1.dp.toPx())
            drawLine(col.copy(alpha = 0.3f * alpha), Offset(cx, cy + chGap), Offset(cx, cy + chLen + chGap), 1.dp.toPx())

            // Rotating radar sweep
            val sweepA = Math.toRadians(scanRot.toDouble())
            val sweepEnd = Offset((cx + r * 0.6f * cos(sweepA)).toFloat(), (cy + r * 0.6f * sin(sweepA)).toFloat())
            drawLine(nvGreen.copy(alpha = 0.08f), Offset(cx, cy), sweepEnd, 1.dp.toPx())

            // Compass ticks (outside the main arc)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)
            for (i in 0..36) {
                val a = Math.toRadians((start + i.toFloat() / 36 * sweep).toDouble())
                val isMaj = i % 6 == 0
                val isMid = i % 3 == 0
                val oR = r + 2.dp.toPx()
                val len = when { isMaj -> 12.dp.toPx(); isMid -> 7.dp.toPx(); else -> 3.dp.toPx() }
                drawLine(col.copy(alpha = (if (isMaj) 0.6f else if (isMid) 0.3f else 0.12f) * alpha),
                    Offset((cx + oR * cos(a)).toFloat(), (cy + oR * sin(a)).toFloat()),
                    Offset((cx + (oR + len) * cos(a)).toFloat(), (cy + (oR + len) * sin(a)).toFloat()),
                    if (isMaj) 2.dp.toPx() else 1.dp.toPx())
            }

            // Main arc — phosphor green dashed
            drawArc(col.copy(alpha = 0.06f * alpha), start, sweep, false,
                topLeft = aO, size = aS, style = Stroke(8.dp.toPx(), cap = StrokeCap.Butt))

            // Progress arc
            drawArc(col.copy(alpha = 0.1f * alpha), start, prog * sweep, false,
                topLeft = aO, size = aS, style = Stroke(16.dp.toPx(), cap = StrokeCap.Butt))
            drawArc(col.copy(alpha = 0.7f * alpha), start, prog * sweep, false,
                topLeft = aO, size = aS,
                style = Stroke(4.dp.toPx(), cap = StrokeCap.Butt,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 3f))))

            // Needle — thin military line
            val nA = Math.toRadians((start + prog * sweep).toDouble())
            val nLen = r - 2.dp.toPx()
            drawLine(col.copy(alpha = alpha), Offset(cx, cy),
                Offset((cx + nLen * cos(nA)).toFloat(), (cy + nLen * sin(nA)).toFloat()),
                1.5f.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(col.copy(alpha = 0.5f), 2.dp.toPx(), Offset(cx, cy))

            // Status line top
            val statusText = if (isAnomaly) "⚠ THREAT DETECTED" else "STATUS: NOMINAL"
            val st = tm.measure(statusText, TextStyle(color = col.copy(alpha = 0.5f * alpha), fontSize = 6.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp))
            drawText(st, topLeft = Offset(cx - st.size.width / 2f, 8.dp.toPx()))

            // Value in center
            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = textCol.copy(alpha = alpha), fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy + 16.dp.toPx()))
            val activeUnitColor = when {
                isAnomaly -> colorScheme.needleColor
                criticalThreshold != null && animVal >= criticalThreshold -> colorScheme.needleColor
                warningThreshold != null && animVal >= warningThreshold -> colorScheme.specialColor
                else -> colorScheme.unitColor
            }
            val ut = tm.measure(unit.uppercase(), TextStyle(color = activeUnitColor.copy(alpha = 0.6f * alpha), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
            drawText(ut, topLeft = Offset(cx - ut.size.width / 2f, cy + 16.dp.toPx() + vt.size.height))
            drawText(lbl, topLeft = Offset(cx - lbl.size.width / 2f, cy + 16.dp.toPx() + vt.size.height + ut.size.height + 2.dp.toPx()))
        }
    })
}
