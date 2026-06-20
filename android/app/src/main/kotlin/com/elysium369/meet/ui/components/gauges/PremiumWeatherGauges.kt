package com.elysium369.meet.ui.components.gauges

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*
import kotlin.random.Random

// ═══════════════════════════════════════════════════════════════
// SHARED UTILITIES & PARTICLE TEMPLATES FOR HIGH-SPEED RENDERING
// ═══════════════════════════════════════════════════════════════

private data class GaugeParticle(
    val angle: Float,
    val distanceRatio: Float,
    val size: Float,
    val speed: Float,
    val waveOffset: Float
)

@Composable
private fun rememberGaugeParticles(seed: Int, count: Int = 18): List<GaugeParticle> {
    return remember(seed, count) {
        val rng = Random(seed * 31L)
        List(count) {
            GaugeParticle(
                angle = rng.nextFloat() * 360f,
                distanceRatio = 0.2f + rng.nextFloat() * 0.7f,
                size = 1.5f + rng.nextFloat() * 4f,
                speed = 0.5f + rng.nextFloat() * 1.5f,
                waveOffset = rng.nextFloat() * PI.toFloat() * 2f
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 1. LIGHTNING WIDGET (⚡)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeLightningWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(0.75f, 120f), label = "val")
    val inf = rememberInfiniteTransition(label = "lightning")
    val flash by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "f")
    val tm = rememberTextMeasurer()

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            // Draw track
            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))

            // Lightning discharge
            val showFlash = flash > 0.85f || isAnomaly
            if (showFlash) {
                val path = Path().apply {
                    moveTo(cx, cy - r * 0.8f)
                    lineTo(cx + r * 0.1f * sin(flash * 50f), cy - r * 0.4f)
                    lineTo(cx - r * 0.15f * cos(flash * 30f), cy - r * 0.1f)
                    lineTo(cx + r * 0.05f, cy + r * 0.2f)
                }
                drawPath(path, cs.specialColor, style = Stroke(2.5f.dp.toPx(), cap = StrokeCap.Round))
                drawPath(path, Color.White, style = Stroke(1.dp.toPx(), cap = StrokeCap.Round))
            }

            // Draw dynamic spark needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawCircle(cs.needleColor, 7.dp.toPx(), Offset(nx, ny))
            drawCircle(Color.White, 3.dp.toPx(), Offset(nx, ny))

            // Text Rendering
            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 2. RAIN WIDGET (🌧️)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeRainWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(0.8f, 150f), label = "val")
    val inf = rememberInfiniteTransition(label = "rain")
    val cycle by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "c")
    val tm = rememberTextMeasurer()
    val particles = rememberGaugeParticles(seed = 12)

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Draw rain particles sliding down
            particles.forEach { p ->
                val pYRatio = (p.distanceRatio + cycle * p.speed) % 1f
                val py = cy - r * 0.7f + pYRatio * r * 1.4f
                val px = cx - r * 0.6f + p.angle % (r * 1.2f)
                val alpha = (1f - abs(pYRatio - 0.5f) * 2f).coerceIn(0f, 1f)
                if (sqrt((px - cx).pow(2) + (py - cy).pow(2)) < r - 4.dp.toPx()) {
                    drawLine(cs.specialColor.copy(alpha = alpha * 0.4f), Offset(px, py), Offset(px - 1.dp.toPx(), py + p.size), 1.5f.dp.toPx())
                }
            }

            // Standard needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(cs.needleColor, 5.dp.toPx(), Offset(cx, cy))

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 3. SNOW WIDGET (❄️)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeSnowWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "snow")
    val cycle by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "c")
    val tm = rememberTextMeasurer()
    val particles = rememberGaugeParticles(seed = 44)

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Falling snow circles
            particles.forEach { p ->
                val pYRatio = (p.distanceRatio + cycle * 0.4f * p.speed) % 1f
                val py = cy - r * 0.8f + pYRatio * r * 1.6f
                val drift = sin(cycle * 2 * PI.toFloat() + p.waveOffset) * 12.dp.toPx()
                val px = cx - r * 0.6f + (p.angle * 7) % (r * 1.2f) + drift
                val alpha = (1f - abs(pYRatio - 0.5f) * 2f).coerceIn(0f, 1f)
                if (sqrt((px - cx).pow(2) + (py - cy).pow(2)) < r - 4.dp.toPx()) {
                    drawCircle(cs.specialColor.copy(alpha = alpha * 0.5f), p.size * 0.8f, Offset(px, py))
                }
            }

            // Needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 4. TORNADO WIDGET (🌪️)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeTornadoWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "tornado")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "r")
    val tm = rememberTextMeasurer()

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Swirling wind funnel
            for (i in 0 until 6) {
                val scale = 0.15f + i * 0.12f
                val tr = r * scale
                val twAngle = rot + i * 40f
                val path = Path().apply {
                    addArc(
                        Rect(cx - tr, cy - tr, cx + tr, cy + tr),
                        twAngle,
                        180f
                    )
                }
                drawPath(path, cs.specialColor.copy(alpha = 0.25f - i * 0.03f), style = Stroke(1.5f.dp.toPx()))
            }

            // Needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 5. SANDSTORM WIDGET (🏜️)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeSandstormWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "sand")
    val cycle by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2500, easing = LinearEasing)), label = "c")
    val tm = rememberTextMeasurer()
    val particles = rememberGaugeParticles(seed = 88)

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Sandstorms blowing horizontally
            particles.forEach { p ->
                val pXRatio = (p.distanceRatio + cycle * 1.5f * p.speed) % 1f
                val px = cx - r * 0.8f + pXRatio * r * 1.6f
                val py = cy - r * 0.5f + p.angle % (r * 1f) + sin(cycle * 3f + p.waveOffset) * 6.dp.toPx()
                val alpha = (1f - abs(pXRatio - 0.5f) * 2f).coerceIn(0f, 1f)
                if (sqrt((px - cx).pow(2) + (py - cy).pow(2)) < r - 4.dp.toPx()) {
                    drawCircle(cs.specialColor.copy(alpha = alpha * 0.35f), p.size * 0.7f, Offset(px, py))
                }
            }

            // Needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 6. VOLCANO WIDGET (🌋)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeVolcanoWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "volc")
    val cycle by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "c")
    val tm = rememberTextMeasurer()
    val particles = rememberGaugeParticles(seed = 99)

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Burning embers rising
            particles.forEach { p ->
                val pYRatio = 1f - ((p.distanceRatio + cycle * p.speed * 0.8f) % 1f)
                val py = cy - r * 0.7f + pYRatio * r * 1.4f
                val px = cx - r * 0.5f + (p.angle * 13) % (r * 1f)
                val alpha = (1f - abs(pYRatio - 0.5f) * 2f).coerceIn(0f, 1f)
                if (sqrt((px - cx).pow(2) + (py - cy).pow(2)) < r - 4.dp.toPx()) {
                    drawCircle(cs.specialColor.copy(alpha = alpha * 0.6f), p.size * 0.9f, Offset(px, py))
                }
            }

            // Needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 7. TSUNAMI WIDGET (🌊)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeTsunamiWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "tsu")
    val wave by inf.animateFloat(0f, 2 * PI.toFloat(), infiniteRepeatable(tween(2500, easing = LinearEasing)), label = "w")
    val tm = rememberTextMeasurer()

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Wave peaks at bottom
            clipPath(Path().apply {
                addOval(Rect(center = Offset(cx, cy), radius = r - 4.dp.toPx()))
            }) {
                val wavePath = Path().apply {
                    moveTo(cx - r, cy + r)
                    for (x in 0..(2 * r).toInt()) {
                        val px = cx - r + x
                        val py = cy + r * 0.4f + sin(x * 0.08f + wave) * 8.dp.toPx()
                        lineTo(px, py)
                    }
                    lineTo(cx + r, cy + r)
                    close()
                }
                drawPath(wavePath, cs.specialColor.copy(alpha = 0.35f))
            }

            // Needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 8. BLIZZARD WIDGET (🌬️)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeBlizzardWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "bliz")
    val cycle by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "c")
    val tm = rememberTextMeasurer()
    val particles = rememberGaugeParticles(seed = 101)

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Swirling blizzard snow streaks
            particles.forEach { p ->
                val pXRatio = (p.distanceRatio + cycle * 2.2f * p.speed) % 1f
                val px = cx - r * 0.8f + pXRatio * r * 1.6f
                val py = cy - r * 0.7f + p.angle % (r * 1.4f)
                val alpha = (1f - abs(pXRatio - 0.5f) * 2f).coerceIn(0f, 1f)
                if (sqrt((px - cx).pow(2) + (py - cy).pow(2)) < r - 4.dp.toPx()) {
                    drawLine(cs.specialColor.copy(alpha = alpha * 0.5f), Offset(px, py), Offset(px - 10.dp.toPx(), py - 3.dp.toPx()), 2.dp.toPx())
                }
            }

            // Needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 9. AURORA AUTO WIDGET (🌌)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeAuroraAutoWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "auroraPro")
    val wave by inf.animateFloat(0f, 2 * PI.toFloat(), infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "w")
    val tm = rememberTextMeasurer()

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Aurora glow curtains
            clipPath(Path().apply {
                addOval(Rect(center = Offset(cx, cy), radius = r - 4.dp.toPx()))
            }) {
                for (j in 0 until 3) {
                    val aPath = Path().apply {
                        moveTo(cx - r, cy - r * 0.3f)
                        for (x in 0..(2 * r).toInt()) {
                            val px = cx - r + x
                            val py = cy - r * 0.4f + j * 12.dp.toPx() + sin(x * 0.05f + wave + j) * 15.dp.toPx()
                            lineTo(px, py)
                        }
                        lineTo(cx + r, cy + r)
                        lineTo(cx - r, cy + r)
                        close()
                    }
                    drawPath(
                        aPath,
                        Brush.verticalGradient(
                            listOf(cs.specialColor.copy(alpha = 0.15f - j * 0.04f), Color.Transparent)
                        )
                    )
                }
            }

            // Needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 10. SOLAR FLARE WIDGET (☀️)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeSolarFlareWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "solar")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "r")
    val tm = rememberTextMeasurer()

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Radial solar flares
            for (i in 0 until 12) {
                val flareAngle = Math.toRadians((rot + i * 30f).toDouble())
                val fStart = r * 0.25f
                val fLength = r * 0.45f + sin(rot * 0.05f + i).absoluteValue * r * 0.15f
                drawLine(
                    cs.specialColor.copy(alpha = 0.35f),
                    Offset((cx + fStart * cos(flareAngle)).toFloat(), (cy + fStart * sin(flareAngle)).toFloat()),
                    Offset((cx + fLength * cos(flareAngle)).toFloat(), (cy + fLength * sin(flareAngle)).toFloat()),
                    2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 11. COSMIC DUST WIDGET (☄️)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeCosmicDustWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "cosmic")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "r")
    val tm = rememberTextMeasurer()
    val particles = rememberGaugeParticles(seed = 123)

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Spiral orbiting dust particles
            particles.forEach { p ->
                val pAngle = Math.toRadians((p.angle + rot * p.speed).toDouble())
                val pDist = r * p.distanceRatio
                val px = cx + (pDist * cos(pAngle)).toFloat()
                val py = cy + (pDist * sin(pAngle)).toFloat()
                drawCircle(cs.specialColor.copy(alpha = 0.45f), p.size * 0.7f, Offset(px, py))
            }

            // Needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 12. EARTHQUAKE WIDGET (🪨)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeEarthquakeWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "quake")
    val shake by inf.animateFloat(-2f, 2f, infiniteRepeatable(tween(80, easing = LinearEasing), RepeatMode.Reverse), label = "s")
    val tm = rememberTextMeasurer()

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val baseCx = size.width / 2f; val baseCy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            // Shake scales with value intensity
            val intensity = prog * 2.5f
            val cx = baseCx + shake * intensity
            val cy = baseCy + shake * intensity

            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Fault crack lines
            val crack = Path().apply {
                moveTo(cx - r * 0.4f, cy + r * 0.3f)
                lineTo(cx - r * 0.1f, cy + r * 0.1f)
                lineTo(cx + r * 0.05f, cy + r * 0.3f)
                lineTo(cx + r * 0.3f, cy + r * 0.2f)
            }
            drawPath(crack, cs.specialColor.copy(alpha = 0.35f), style = Stroke(2.dp.toPx()))

            // Needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 13. METEOR SHOWER WIDGET (🌠)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeMeteorShowerWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "meteor")
    val drift by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "d")
    val tm = rememberTextMeasurer()

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Falling meteors with trails
            for (i in 0 until 2) {
                val progress = (drift + i * 0.5f) % 1f
                val mx = cx - r * 0.7f + progress * r * 1.4f
                val my = cy - r * 0.7f + progress * r * 1.2f
                val alpha = (1f - abs(progress - 0.5f) * 2f).coerceIn(0f, 1f)
                if (sqrt((mx - cx).pow(2) + (my - cy).pow(2)) < r - 4.dp.toPx()) {
                    drawLine(
                        cs.specialColor.copy(alpha = alpha * 0.5f),
                        Offset(mx, my),
                        Offset(mx - 12.dp.toPx(), my - 9.dp.toPx()),
                        1.5f.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // Needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 14. HURRICANE WIDGET (🌀)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeHurricaneWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "hurr")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "r")
    val tm = rememberTextMeasurer()

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Swirling hurricane bands
            rotate(rot) {
                for (i in 0 until 3) {
                    val p = Path().apply {
                        moveTo(cx, cy)
                        cubicTo(
                            cx + r * 0.3f * cos(i * 2.09f), cy + r * 0.3f * sin(i * 2.09f),
                            cx + r * 0.6f * cos(i * 2.09f + 0.8f), cy + r * 0.6f * sin(i * 2.09f + 0.8f),
                            cx + r * 0.8f * cos(i * 2.09f + 1.5f), cy + r * 0.8f * sin(i * 2.09f + 1.5f)
                        )
                    }
                    drawPath(p, cs.specialColor.copy(alpha = 0.25f), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                }
            }

            // Needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 15. FOGGY MIST WIDGET (🌫️)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeFoggyMistWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "fog")
    val drift by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(10000, easing = LinearEasing)), label = "d")
    val tm = rememberTextMeasurer()

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Drifting misty cloud layers
            clipPath(Path().apply { addOval(Rect(center = Offset(cx, cy), radius = r - 4.dp.toPx())) }) {
                for (i in 0 until 3) {
                    val mAngle = Math.toRadians((drift + i * 120f).toDouble())
                    val mx = cx + (r * 0.2f * cos(mAngle)).toFloat()
                    val my = cy + (r * 0.1f * sin(mAngle)).toFloat()
                    drawCircle(
                        Brush.radialGradient(
                            listOf(cs.specialColor.copy(alpha = 0.2f), Color.Transparent),
                            Offset(mx, my), r * 0.6f
                        ),
                        r * 0.6f, Offset(mx, my)
                    )
                }
            }

            // Needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 16. WILD FIRE WIDGET (🔥)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeWildFireWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "fire")
    val cycle by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "c")
    val tm = rememberTextMeasurer()

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Flame peaks at the base
            clipPath(Path().apply { addOval(Rect(center = Offset(cx, cy), radius = r - 4.dp.toPx())) }) {
                val fPath = Path().apply {
                    moveTo(cx - r, cy + r)
                    for (x in 0..(2 * r).toInt()) {
                        val px = cx - r + x
                        val fHeight = 15.dp.toPx() + sin(x * 0.12f + cycle * 10f) * 6.dp.toPx() + cos(x * 0.05f - cycle * 8f) * 4.dp.toPx()
                        lineTo(px, cy + r - fHeight)
                    }
                    lineTo(cx + r, cy + r)
                    close()
                }
                drawPath(
                    fPath,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.2f), cs.specialColor.copy(alpha = 0.6f), Color.Transparent),
                        startY = cy + r - 25.dp.toPx(), endY = cy + r
                    )
                )
            }

            // Needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 17. OCEAN DEPTH WIDGET (⚓)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeOceanDepthWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "ocean")
    val radarRot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "r")
    val tm = rememberTextMeasurer()

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Sonar line
            val radRad = Math.toRadians(radarRot.toDouble())
            drawLine(
                cs.specialColor.copy(alpha = 0.25f),
                Offset(cx, cy),
                Offset((cx + r * 0.9f * cos(radRad)).toFloat(), (cy + r * 0.9f * sin(radRad)).toFloat()),
                2.dp.toPx()
            )

            // Dynamic needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 18. ECLIPSE WIDGET (🌑)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeEclipseWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "eclipse")
    val wave by inf.animateFloat(0.9f, 1.1f, infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "w")
    val tm = rememberTextMeasurer()

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Solar corona behind central disk
            drawCircle(
                Brush.radialGradient(
                    listOf(cs.specialColor.copy(alpha = 0.6f), Color.Transparent),
                    Offset(cx, cy), r * 0.55f * wave
                ),
                r * 0.55f * wave, Offset(cx, cy)
            )

            // Black moon circle
            drawCircle(Color.Black, r * 0.42f, Offset(cx, cy))
            drawCircle(cs.bezelColor.copy(alpha = 0.4f), r * 0.42f, Offset(cx, cy), style = Stroke(1.5f.dp.toPx()))

            // Needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 19. RAINBOW RAIN WIDGET (🌈)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeRainbowRainWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "rainbow")
    val cycle by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2500, easing = LinearEasing)), label = "c")
    val tm = rememberTextMeasurer()
    val particles = rememberGaugeParticles(seed = 150)

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Multicolored raindrops
            particles.forEachIndexed { idx, p ->
                val pYRatio = (p.distanceRatio + cycle * p.speed) % 1f
                val py = cy - r * 0.7f + pYRatio * r * 1.4f
                val px = cx - r * 0.6f + p.angle % (r * 1.2f)
                val alpha = (1f - abs(pYRatio - 0.5f) * 2f).coerceIn(0f, 1f)

                val hue = (idx * 20f + cycle * 360f) % 360f
                val dropColor = Color.hsv(hue, 0.8f, 0.95f)

                if (sqrt((px - cx).pow(2) + (py - cy).pow(2)) < r - 4.dp.toPx()) {
                    drawLine(dropColor.copy(alpha = alpha * 0.5f), Offset(px, py), Offset(px, py + p.size), 2.dp.toPx())
                }
            }

            // Needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 20. SAND GLOW WIDGET (🐪)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeSandGlowWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "sandGlow")
    val shimmer by inf.animateFloat(0f, 2 * PI.toFloat(), infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "s")
    val tm = rememberTextMeasurer()

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Shimmering sand wave lines
            for (j in 0 until 2) {
                val path = Path().apply {
                    moveTo(cx - r, cy + r * 0.4f + j * 15f)
                    for (x in 0..(2 * r).toInt()) {
                        val px = cx - r + x
                        val py = cy + r * 0.5f + j * 12.dp.toPx() + sin(x * 0.04f + shimmer + j) * 5.dp.toPx()
                        lineTo(px, py)
                    }
                }
                drawPath(path, cs.specialColor.copy(alpha = 0.25f), style = Stroke(1.5f.dp.toPx()))
            }

            // Needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 21. THUNDER CLOUD WIDGET (☁️)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeThunderCloudWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "thunder")
    val flash by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2500, easing = LinearEasing)), label = "f")
    val tm = rememberTextMeasurer()

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Thunder cloud shapes drawing
            val isFlashing = flash > 0.9f || isAnomaly
            val cloudColor = if (isFlashing) cs.specialColor.copy(alpha = 0.35f) else cs.bezelColor.copy(alpha = 0.25f)
            drawCircle(cloudColor, r * 0.35f, Offset(cx - r * 0.2f, cy - r * 0.3f))
            drawCircle(cloudColor, r * 0.4f, Offset(cx + r * 0.1f, cy - r * 0.35f))
            drawCircle(cloudColor, r * 0.3f, Offset(cx + r * 0.35f, cy - r * 0.25f))

            // Needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 22. ICY FROST WIDGET (🧊)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeIcyFrostWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "frost")
    val pulse by inf.animateFloat(0.95f, 1.05f, infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    val tm = rememberTextMeasurer()

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Frost needles growing from borders
            for (i in 0 until 8) {
                val fAngle = Math.toRadians((i * 45f).toDouble())
                val fOuter = r
                val fInner = r * 0.85f * pulse
                drawLine(
                    cs.specialColor.copy(alpha = 0.35f),
                    Offset((cx + fOuter * cos(fAngle)).toFloat(), (cy + fOuter * sin(fAngle)).toFloat()),
                    Offset((cx + fInner * cos(fAngle)).toFloat(), (cy + fInner * sin(fAngle)).toFloat()),
                    1.5f.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 23. CYBER STORM WIDGET (📟)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeCyberStormWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "matrix")
    val cycle by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "c")
    val tm = rememberTextMeasurer()
    val particles = rememberGaugeParticles(seed = 777)

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Matrix rain drops
            particles.forEach { p ->
                val pYRatio = (p.distanceRatio + cycle * p.speed) % 1f
                val py = cy - r * 0.7f + pYRatio * r * 1.4f
                val px = cx - r * 0.6f + p.angle % (r * 1.2f)
                val alpha = (1f - abs(pYRatio - 0.5f) * 2f).coerceIn(0f, 1f)
                if (sqrt((px - cx).pow(2) + (py - cy).pow(2)) < r - 4.dp.toPx()) {
                    drawRect(
                        cs.specialColor.copy(alpha = alpha * 0.6f),
                        Offset(px, py),
                        Size(1.5f.dp.toPx(), p.size)
                    )
                }
            }

            // Needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 24. BLACK HOLE WIDGET (🕳️)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeBlackHoleWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "hole")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(3500, easing = LinearEasing)), label = "r")
    val tm = rememberTextMeasurer()

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Accretion disk
            rotate(rot) {
                drawCircle(
                    Brush.sweepGradient(
                        listOf(Color.Transparent, cs.specialColor.copy(alpha = 0.4f), Color.Transparent)
                    ),
                    r * 0.6f, Offset(cx, cy), style = Stroke(10.dp.toPx())
                )
            }

            // Central black singularity
            drawCircle(Color.Black, r * 0.35f, Offset(cx, cy))

            // Needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 25. MONSOON WIDGET (🌧️)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeMonsoonWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "monsoon")
    val cycle by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "c")
    val tm = rememberTextMeasurer()
    val particles = rememberGaugeParticles(seed = 52)

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Torrential wind-slanted rain sheets
            particles.forEach { p ->
                val pYRatio = (p.distanceRatio + cycle * 2.5f * p.speed) % 1f
                val py = cy - r * 0.7f + pYRatio * r * 1.4f
                val px = cx - r * 0.6f + p.angle % (r * 1.2f) - pYRatio * 20.dp.toPx()
                val alpha = (1f - abs(pYRatio - 0.5f) * 2f).coerceIn(0f, 1f)
                if (sqrt((px - cx).pow(2) + (py - cy).pow(2)) < r - 4.dp.toPx()) {
                    drawLine(cs.specialColor.copy(alpha = alpha * 0.45f), Offset(px, py), Offset(px - 6.dp.toPx(), py + p.size * 1.5f), 2.dp.toPx())
                }
            }

            // Needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 26. COMET TAIL WIDGET (💫)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeCometTailWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "comet")
    val pulse by inf.animateFloat(0.8f, 1.2f, infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    val tm = rememberTextMeasurer()

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Comet needle head with trailing tail arc
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()

            // Tail
            drawArc(
                Brush.sweepGradient(
                    listOf(Color.Transparent, cs.specialColor.copy(alpha = 0.5f), Color.Transparent)
                ),
                start + prog * sweep - 30f, 30f, false,
                topLeft = aO, size = aS,
                style = Stroke(8.dp.toPx())
            )

            // Comet Head
            drawCircle(cs.needleColor, 8.dp.toPx() * pulse, Offset(nx, ny))
            drawCircle(Color.White, 3.dp.toPx(), Offset(nx, ny))

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 27. GALAXY CORE WIDGET (✨)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeGalaxyCoreWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "galaxy")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(10000, easing = LinearEasing)), label = "r")
    val tm = rememberTextMeasurer()

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Pulsar jets
            rotate(rot) {
                drawLine(cs.specialColor.copy(alpha = 0.3f), Offset(cx, cy - r * 0.8f), Offset(cx, cy + r * 0.8f), 3.dp.toPx())
                drawCircle(cs.specialColor.copy(alpha = 0.5f), r * 0.12f, Offset(cx, cy))
            }

            // Needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 28. ACID RAIN WIDGET (🧪)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeAcidRainWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "acid")
    val cycle by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "c")
    val tm = rememberTextMeasurer()
    val particles = rememberGaugeParticles(seed = 66)

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Glowing green toxic rain drops
            particles.forEach { p ->
                val pYRatio = (p.distanceRatio + cycle * 1.8f * p.speed) % 1f
                val py = cy - r * 0.7f + pYRatio * r * 1.4f
                val px = cx - r * 0.6f + p.angle % (r * 1.2f)
                val alpha = (1f - abs(pYRatio - 0.5f) * 2f).coerceIn(0f, 1f)
                if (sqrt((px - cx).pow(2) + (py - cy).pow(2)) < r - 4.dp.toPx()) {
                    drawLine(cs.specialColor.copy(alpha = alpha * 0.6f), Offset(px, py), Offset(px, py + p.size), 2.dp.toPx())
                }
            }

            // Needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 29. SUPERNOVA WIDGET (💥)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeSupernovaWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "supernova")
    val shockwave by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2500, easing = LinearEasing)), label = "s")
    val tm = rememberTextMeasurer()

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Expanding shockwave rings
            val sRadius = r * 0.15f + shockwave * r * 0.7f
            val sAlpha = (1f - shockwave).coerceIn(0f, 1f) * 0.45f
            drawCircle(cs.specialColor.copy(alpha = sAlpha), sRadius, Offset(cx, cy), style = Stroke(2.dp.toPx()))

            // Needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}

// ═══════════════════════════════════════════════════════════════
// 30. WIND TUNNEL WIDGET (💨)
// ═══════════════════════════════════════════════════════════════
@Composable
fun GaugeWindTunnelWidget(
    label: String, value: Float, minVal: Float = 0f, maxVal: Float = 100f,
    unit: String, warningThreshold: Float? = null, criticalThreshold: Float? = null,
    isAnomaly: Boolean = false, modifier: Modifier = Modifier
) {
    val cs = LocalGaugeColorScheme.current
    val animVal by animateFloatAsState(value.coerceIn(minVal, maxVal), spring(), label = "val")
    val inf = rememberInfiniteTransition(label = "wind")
    val drift by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "d")
    val tm = rememberTextMeasurer()

    Spacer(modifier = modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp).drawWithCache {
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.width / 2f - 18.dp.toPx()
        val start = 140f; val sweep = 260f

        onDrawBehind {
            val prog = if (maxVal == minVal) 0f else ((animVal - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val aO = Offset(cx - r, cy - r); val aS = Size(r * 2, r * 2)

            drawArc(cs.bezelColor.copy(alpha = 0.15f), start, sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            drawArc(cs.internalColor, start, prog * sweep, false, aO, aS, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))

            // Aerodynamic flow lines
            clipPath(Path().apply { addOval(Rect(center = Offset(cx, cy), radius = r - 4.dp.toPx())) }) {
                for (i in 0 until 4) {
                    val lineY = cy - r * 0.5f + i * r * 0.35f
                    val linePath = Path().apply {
                        moveTo(cx - r, lineY)
                        cubicTo(
                            cx - r * 0.3f, lineY + sin(drift * 2 * PI.toFloat() + i) * 8.dp.toPx(),
                            cx + r * 0.3f, lineY - sin(drift * 2 * PI.toFloat() + i) * 8.dp.toPx(),
                            cx + r, lineY
                        )
                    }
                    drawPath(linePath, cs.specialColor.copy(alpha = 0.22f), style = Stroke(1.5f.dp.toPx()))
                }
            }

            // Needle
            val nAngle = Math.toRadians((start + prog * sweep).toDouble())
            val nx = cx + (r * cos(nAngle)).toFloat()
            val ny = cy + (r * sin(nAngle)).toFloat()
            drawLine(cs.needleColor, Offset(cx, cy), Offset(nx, ny), 3.dp.toPx(), cap = StrokeCap.Round)

            val vt = tm.measure(String.format("%.0f", animVal), TextStyle(color = cs.textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height / 2f - 4.dp.toPx()))
        }
    })
}
