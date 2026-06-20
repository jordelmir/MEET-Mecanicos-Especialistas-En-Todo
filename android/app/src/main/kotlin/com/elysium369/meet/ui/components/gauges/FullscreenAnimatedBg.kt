package com.elysium369.meet.ui.components.gauges

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.*
import kotlin.random.Random

/**
 * Animated fullscreen background effect tied to the gauge style.
 * Renders immersive, living backgrounds behind the gauge:
 *   MATRIX_RAIN → ELITE, CYBER, MILITARY
 *   LIGHTNING   → RACING, FERRARI, TOKYO
 *   FIRE_EMBERS → THERMO, LAMBO
 *   PLASMA      → PLASMA, AURORA, HOLOGRAM
 *   DIAMOND     → DIAMOND, CLASSIC
 *   NEON_GRID   → NEON_RETRO, COCKPIT, RADIAL
 */
@Composable
fun FullscreenAnimatedBg(
    style: GaugeStyleSet,
    modifier: Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "fsBg")

    val time by inf.animateFloat(
        0f, 1000f,
        infiniteRepeatable(tween(100_000, easing = LinearEasing)),
        label = "bgTime"
    )
    val pulse by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bgPulse"
    )
    val fastCycle by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "bgFast"
    )
    val slowCycle by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(20000, easing = LinearEasing)),
        label = "bgSlow"
    )

    val context = androidx.compose.ui.platform.LocalContext.current
    val accent = remember(style) {
        val manager = GaugeStyleManager(context)
        manager.getColorScheme(style).specialColor
    }

    // Precompute random particles (deterministic via seeded random per style)
    val particles = remember(style) {
        val rng = Random(style.ordinal * 7919L)
        List(60) {
            BgParticle(
                x = rng.nextFloat(),
                y = rng.nextFloat(),
                speed = 0.15f + rng.nextFloat() * 0.85f,
                size = 1f + rng.nextFloat() * 3f,
                phase = rng.nextFloat() * 360f,
                brightness = 0.3f + rng.nextFloat() * 0.7f
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        when (style) {
            GaugeStyleSet.ELITE, GaugeStyleSet.CYBER, GaugeStyleSet.MILITARY, GaugeStyleSet.CYBER_STORM ->
                drawMatrixRain(time, pulse, accent, particles)

            GaugeStyleSet.RACING, GaugeStyleSet.FERRARI, GaugeStyleSet.TOKYO, GaugeStyleSet.LIGHTNING, GaugeStyleSet.THUNDER_CLOUD ->
                drawLightningStorm(time, pulse, fastCycle, accent, particles)

            GaugeStyleSet.THERMO, GaugeStyleSet.LAMBO, GaugeStyleSet.VOLCANO, GaugeStyleSet.WILD_FIRE ->
                drawFireEmbers(time, pulse, fastCycle, accent, particles)

            GaugeStyleSet.PLASMA, GaugeStyleSet.AURORA, GaugeStyleSet.HOLOGRAM, GaugeStyleSet.AURORA_AUTO, GaugeStyleSet.COSMIC_DUST, GaugeStyleSet.GALAXY_CORE, GaugeStyleSet.BLACK_HOLE, GaugeStyleSet.SUPERNOVA ->
                drawPlasmaNebula(time, pulse, slowCycle, accent, particles)

            GaugeStyleSet.DIAMOND, GaugeStyleSet.CLASSIC, GaugeStyleSet.SOLAR_FLARE, GaugeStyleSet.METEOR_SHOWER, GaugeStyleSet.COMET_TAIL, GaugeStyleSet.ECLIPSE ->
                drawDiamondSparkle(time, pulse, fastCycle, accent, particles)

            GaugeStyleSet.NEON_RETRO, GaugeStyleSet.COCKPIT, GaugeStyleSet.RADIAL, GaugeStyleSet.TORNADO, GaugeStyleSet.SANDSTORM, GaugeStyleSet.TSUNAMI, GaugeStyleSet.BLIZZARD, GaugeStyleSet.EARTHQUAKE, GaugeStyleSet.HURRICANE, GaugeStyleSet.FOGGY_MIST, GaugeStyleSet.OCEAN_DEPTH, GaugeStyleSet.RAINBOW_RAIN, GaugeStyleSet.SAND_GLOW, GaugeStyleSet.ICY_FROST, GaugeStyleSet.MONSOON, GaugeStyleSet.ACID_RAIN, GaugeStyleSet.WIND_TUNNEL, GaugeStyleSet.RAIN, GaugeStyleSet.SNOW, GaugeStyleSet.CUSTOM_DIY ->
                drawNeonGrid(time, pulse, slowCycle, accent, particles)
        }
    }
}

private data class BgParticle(
    val x: Float, val y: Float,
    val speed: Float, val size: Float,
    val phase: Float, val brightness: Float
)

// ═══════════════════════════════════════════════════════
// EFFECT 1: MATRIX RAIN — falling digital rain columns
// ═══════════════════════════════════════════════════════
private fun DrawScope.drawMatrixRain(
    time: Float, pulse: Float, accent: Color, particles: List<BgParticle>
) {
    val w = size.width; val h = size.height
    val columns = 30
    val colWidth = w / columns

    // Vertical dim lines (column guides)
    for (i in 0 until columns) {
        val x = i * colWidth + colWidth / 2f
        drawLine(
            accent.copy(alpha = 0.015f),
            Offset(x, 0f), Offset(x, h),
            1.dp.toPx()
        )
    }

    // Falling rain drops
    particles.forEach { p ->
        val col = (p.x * columns).toInt().coerceIn(0, columns - 1)
        val x = col * colWidth + colWidth / 2f
        val dropSpeed = p.speed * 0.4f + 0.1f
        val y = ((time * dropSpeed * 50f + p.phase * h) % (h * 1.3f)) - h * 0.15f
        val alpha = p.brightness * 0.6f * (1f - (y / h).coerceIn(0f, 1f))

        // Glow behind
        drawCircle(
            accent.copy(alpha = alpha * 0.15f * pulse),
            p.size.dp.toPx() * 6f,
            Offset(x, y)
        )
        // Core drop
        drawCircle(
            accent.copy(alpha = alpha * 0.7f),
            p.size.dp.toPx() * 1.2f,
            Offset(x, y)
        )
        // Bright center
        drawCircle(
            Color.White.copy(alpha = alpha * 0.5f),
            p.size.dp.toPx() * 0.5f,
            Offset(x, y)
        )

        // Trail
        val trailLen = p.speed * 40.dp.toPx()
        drawLine(
            accent.copy(alpha = alpha * 0.3f),
            Offset(x, y),
            Offset(x, (y - trailLen).coerceAtLeast(0f)),
            1.dp.toPx()
        )
    }

    // Horizontal scan lines (CRT effect)
    val scanCount = 8
    for (i in 0 until scanCount) {
        val scanY = ((time * 15f + i * h / scanCount) % h)
        drawLine(
            accent.copy(alpha = 0.03f * pulse),
            Offset(0f, scanY), Offset(w, scanY),
            1.dp.toPx()
        )
    }
}

// ═══════════════════════════════════════════════════════
// EFFECT 2: LIGHTNING STORM — electrical arcs & flashes
// ═══════════════════════════════════════════════════════
private fun DrawScope.drawLightningStorm(
    time: Float, pulse: Float, fastCycle: Float,
    accent: Color, particles: List<BgParticle>
) {
    val w = size.width; val h = size.height
    val cx = w / 2f; val cy = h / 2f

    // Background flash (intermittent)
    val flashIntensity = (sin(time * 3.7f) * sin(time * 7.1f))
        .coerceIn(0f, 1f).let { it * it * it }
    if (flashIntensity > 0.5f) {
        drawRect(accent.copy(alpha = (flashIntensity - 0.5f) * 0.06f))
    }

    // Lightning bolts (3 per frame, seeded by time)
    val boltCount = 3
    for (b in 0 until boltCount) {
        val seed = ((time * 2f + b * 137.5f) % 360f)
        val boltAlpha = (sin(Math.toRadians(seed.toDouble() * 5.0)) * 0.5 + 0.5).toFloat()
        if (boltAlpha < 0.3f) continue

        val startX = w * (0.1f + particles[b * 3].x * 0.8f)
        var px = startX
        var py = 0f
        val segments = 8 + (particles[b * 3].speed * 12).toInt()

        for (s in 0 until segments) {
            val nx = px + (Random(((time * 10).toInt() + b * 100 + s).toLong()).nextFloat() - 0.5f) * 40.dp.toPx()
            val ny = py + h / segments
            val segAlpha = boltAlpha * (1f - s.toFloat() / segments) * 0.4f

            // Glow
            drawLine(
                accent.copy(alpha = segAlpha * 0.3f),
                Offset(px, py), Offset(nx, ny),
                6.dp.toPx()
            )
            // Core
            drawLine(
                Color.White.copy(alpha = segAlpha * 0.6f),
                Offset(px, py), Offset(nx, ny),
                2.dp.toPx()
            )
            // Bright core
            drawLine(
                accent.copy(alpha = segAlpha),
                Offset(px, py), Offset(nx, ny),
                1.dp.toPx()
            )
            px = nx; py = ny
        }
    }

    // Spark particles
    particles.take(20).forEach { p ->
        val angle = Math.toRadians((fastCycle * p.speed + p.phase).toDouble())
        val dist = (h * 0.3f + p.x * h * 0.4f)
        val px = cx + (dist * cos(angle)).toFloat() * (0.5f + p.y * 0.5f)
        val py = cy + (dist * sin(angle)).toFloat() * 0.6f
        val a = p.brightness * 0.4f * pulse

        drawCircle(accent.copy(alpha = a * 0.2f), p.size.dp.toPx() * 4f, Offset(px, py))
        drawCircle(Color.White.copy(alpha = a * 0.5f), p.size.dp.toPx() * 0.8f, Offset(px, py))
    }
}

// ═══════════════════════════════════════════════════════
// EFFECT 3: FIRE EMBERS — rising sparks & heat glow
// ═══════════════════════════════════════════════════════
private fun DrawScope.drawFireEmbers(
    time: Float, pulse: Float, fastCycle: Float,
    accent: Color, particles: List<BgParticle>
) {
    val w = size.width; val h = size.height

    // Bottom heat glow
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            0.6f to Color.Transparent,
            0.85f to accent.copy(alpha = 0.04f * pulse),
            1f to accent.copy(alpha = 0.12f * pulse)
        )
    )

    // Secondary glow (core orange-red)
    val coreColor = Color(0xFFFF3D00)
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            0.75f to Color.Transparent,
            0.95f to coreColor.copy(alpha = 0.06f * pulse),
            1f to coreColor.copy(alpha = 0.10f * pulse)
        )
    )

    // Rising embers
    particles.forEach { p ->
        val riseSpeed = p.speed * 0.3f + 0.05f
        val baseX = p.x * w
        val drift = sin(time * 0.5f + p.phase) * 20.dp.toPx()
        val x = baseX + drift
        val rawY = h - ((time * riseSpeed * 40f + p.phase * h) % (h * 1.4f))
        val y = rawY

        if (y in -20f..h + 20f) {
            val life = 1f - ((h - y) / h).coerceIn(0f, 1f)
            val fadeIn = (y / (h * 0.2f)).coerceIn(0f, 1f)
            val alpha = p.brightness * life * fadeIn * 0.55f

            // Size shrinks as ember rises
            val eSize = p.size * (0.5f + life * 0.5f)

            // Warm outer glow
            drawCircle(
                accent.copy(alpha = alpha * 0.15f),
                eSize.dp.toPx() * 8f,
                Offset(x, y)
            )
            // Core ember
            val emberColor = Color(
                red = 1f,
                green = 0.3f + life * 0.5f,
                blue = life * 0.15f,
                alpha = alpha
            )
            drawCircle(emberColor, eSize.dp.toPx() * 2f, Offset(x, y))
            // Hot white center
            drawCircle(
                Color.White.copy(alpha = alpha * 0.6f * life),
                eSize.dp.toPx() * 0.6f,
                Offset(x, y)
            )
        }
    }

    // Heat distortion lines
    for (i in 0 until 5) {
        val lineY = h - h * 0.1f * i - (time * 5f % (h * 0.1f))
        val waveAmplitude = 5.dp.toPx() * sin(time + i.toFloat())
        if (lineY in 0f..h) {
            drawLine(
                accent.copy(alpha = 0.03f * (1f - i * 0.15f)),
                Offset(0f, lineY + waveAmplitude),
                Offset(w, lineY - waveAmplitude),
                1.dp.toPx()
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// EFFECT 4: PLASMA NEBULA — swirling cosmic energy
// ═══════════════════════════════════════════════════════
private fun DrawScope.drawPlasmaNebula(
    time: Float, pulse: Float, slowCycle: Float,
    accent: Color, particles: List<BgParticle>
) {
    val w = size.width; val h = size.height
    val cx = w / 2f; val cy = h / 2f
    val maxR = maxOf(w, h) * 0.6f

    // Swirling nebula clouds (layered rotated gradients)
    val secondary = Color(
        red = accent.red * 0.5f + 0.3f,
        green = accent.green * 0.3f + 0.2f,
        blue = accent.blue * 0.5f + 0.5f,
        alpha = 1f
    )

    for (layer in 0 until 3) {
        val layerAngle = slowCycle + layer * 120f
        val layerAlpha = 0.03f + layer * 0.01f
        val layerScale = 0.8f + layer * 0.15f
        val offsetX = cos(Math.toRadians(layerAngle.toDouble())).toFloat() * maxR * 0.15f
        val offsetY = sin(Math.toRadians(layerAngle.toDouble())).toFloat() * maxR * 0.1f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    (if (layer % 2 == 0) accent else secondary).copy(alpha = layerAlpha * pulse),
                    Color.Transparent
                ),
                center = Offset(cx + offsetX, cy + offsetY),
                radius = maxR * layerScale
            ),
            radius = maxR * layerScale,
            center = Offset(cx + offsetX, cy + offsetY)
        )
    }

    // Orbiting plasma particles
    particles.take(35).forEach { p ->
        val orbitR = maxR * (0.15f + p.x * 0.55f)
        val speed = p.speed * 0.6f + 0.2f
        val angle = Math.toRadians((slowCycle * speed + p.phase).toDouble())
        val px = cx + (orbitR * cos(angle)).toFloat()
        val py = cy + (orbitR * sin(angle) * 0.7f).toFloat() // slight vertical compression

        val dist = sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy))
        if (dist < maxR * 0.9f) {
            val a = p.brightness * 0.35f * pulse
            // Plasma glow
            drawCircle(accent.copy(alpha = a * 0.12f), p.size.dp.toPx() * 6f, Offset(px, py))
            drawCircle(secondary.copy(alpha = a * 0.25f), p.size.dp.toPx() * 2.5f, Offset(px, py))
            drawCircle(Color.White.copy(alpha = a * 0.4f), p.size.dp.toPx() * 0.6f, Offset(px, py))
        }
    }

    // Energy tendrils (rotating lines from center)
    for (t in 0 until 6) {
        val tAngle = Math.toRadians((slowCycle * 0.5f + t * 60f).toDouble())
        val len = maxR * (0.3f + pulse * 0.2f)
        drawLine(
            accent.copy(alpha = 0.03f * pulse),
            Offset(cx, cy),
            Offset(cx + (len * cos(tAngle)).toFloat(), cy + (len * sin(tAngle)).toFloat()),
            2.dp.toPx()
        )
    }
}

// ═══════════════════════════════════════════════════════
// EFFECT 5: DIAMOND SPARKLE — prismatic light refractions
// ═══════════════════════════════════════════════════════
private fun DrawScope.drawDiamondSparkle(
    time: Float, pulse: Float, fastCycle: Float,
    accent: Color, particles: List<BgParticle>
) {
    val w = size.width; val h = size.height
    val cx = w / 2f; val cy = h / 2f

    // Prismatic light beams from center
    val beamCount = 12
    for (i in 0 until beamCount) {
        val angle = Math.toRadians((fastCycle * 0.3f + i * 360f / beamCount).toDouble())
        val len = maxOf(w, h) * 0.7f
        val beamAlpha = 0.02f + sin(time * 0.3f + i.toFloat()) * 0.015f

        drawLine(
            accent.copy(alpha = beamAlpha.toFloat().coerceAtLeast(0f) * pulse),
            Offset(cx, cy),
            Offset(cx + (len * cos(angle)).toFloat(), cy + (len * sin(angle)).toFloat()),
            1.5f.dp.toPx()
        )
    }

    // Floating diamond facets
    particles.take(25).forEach { p ->
        val px = p.x * w
        val py = ((p.y * h + time * p.speed * 8f) % (h * 1.2f)) - h * 0.1f
        val a = p.brightness * 0.4f

        // Rainbow prismatic colors
        val hueShift = (fastCycle + p.phase) % 360f
        val facetColor = Color.hsl(hueShift, 0.7f, 0.7f)

        // Diamond shape (rotated square)
        val dSize = p.size.dp.toPx() * 2f

        rotate(fastCycle * p.speed + p.phase, Offset(px, py)) {
            drawRect(
                color = facetColor.copy(alpha = a * 0.15f),
                topLeft = Offset(px - dSize, py - dSize),
                size = Size(dSize * 2, dSize * 2)
            )
        }

        // Sparkle point
        drawCircle(
            Color.White.copy(alpha = a * 0.5f * pulse),
            p.size.dp.toPx() * 0.8f,
            Offset(px, py)
        )
        // Glow
        drawCircle(
            accent.copy(alpha = a * 0.08f),
            p.size.dp.toPx() * 5f,
            Offset(px, py)
        )
    }
}

// ═══════════════════════════════════════════════════════
// EFFECT 6: NEON GRID — 80s retrowave perspective grid
// ═══════════════════════════════════════════════════════
private fun DrawScope.drawNeonGrid(
    time: Float, pulse: Float, slowCycle: Float,
    accent: Color, particles: List<BgParticle>
) {
    val w = size.width; val h = size.height
    val horizonY = h * 0.4f

    // Sky gradient (dark to accent tint)
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color(0xFF050510),
            0.35f to accent.copy(alpha = 0.03f),
            0.4f to accent.copy(alpha = 0.06f * pulse)
        ),
        size = Size(w, horizonY)
    )

    // Horizon glow line
    drawLine(
        accent.copy(alpha = 0.35f * pulse),
        Offset(0f, horizonY),
        Offset(w, horizonY),
        2.dp.toPx()
    )
    drawLine(
        accent.copy(alpha = 0.08f * pulse),
        Offset(0f, horizonY),
        Offset(w, horizonY),
        8.dp.toPx()
    )

    // Perspective grid lines (horizontal — recede to horizon)
    val horizLineCount = 15
    for (i in 1..horizLineCount) {
        val fraction = i.toFloat() / horizLineCount
        val perspY = horizonY + (h - horizonY) * fraction * fraction // quadratic perspective
        val scrollOffset = (time * 3f * fraction) % (fraction * 30.dp.toPx())
        val lineY = perspY + scrollOffset
        if (lineY in horizonY..h) {
            val lineAlpha = 0.06f * fraction * pulse
            drawLine(
                accent.copy(alpha = lineAlpha),
                Offset(0f, lineY),
                Offset(w, lineY),
                (0.5f + fraction).dp.toPx()
            )
        }
    }

    // Perspective grid lines (vertical — converge to vanishing point)
    val vertLineCount = 20
    val vanishX = w / 2f
    for (i in 0 until vertLineCount) {
        val fraction = (i - vertLineCount / 2f) / (vertLineCount / 2f) // -1 to 1
        val bottomX = vanishX + fraction * w * 0.8f
        val lineAlpha = 0.05f * (1f - abs(fraction) * 0.5f) * pulse

        drawLine(
            accent.copy(alpha = lineAlpha),
            Offset(vanishX, horizonY),
            Offset(bottomX, h),
            (0.5f + abs(fraction)).dp.toPx()
        )
    }

    // Grid intersection glows
    particles.take(15).forEach { p ->
        val px = p.x * w
        val py = horizonY + (h - horizonY) * p.y * p.y
        val a = p.brightness * 0.2f * pulse

        drawCircle(accent.copy(alpha = a * 0.15f), p.size.dp.toPx() * 4f, Offset(px, py))
        drawCircle(Color.White.copy(alpha = a * 0.3f), p.size.dp.toPx() * 0.5f, Offset(px, py))
    }

    // Neon sun/moon at horizon
    val sunR = 40.dp.toPx()
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = 0.15f * pulse),
                accent.copy(alpha = 0.05f * pulse),
                Color.Transparent
            ),
            center = Offset(vanishX, horizonY),
            radius = sunR * 2f
        ),
        radius = sunR * 2f,
        center = Offset(vanishX, horizonY)
    )
    // Sun lines
    for (i in 0 until 5) {
        val lineY = horizonY - sunR + i * sunR * 0.4f
        if (lineY < horizonY) {
            drawLine(
                Color(0xFF050510),
                Offset(vanishX - sunR, lineY),
                Offset(vanishX + sunR, lineY),
                3.dp.toPx()
            )
        }
    }
}
