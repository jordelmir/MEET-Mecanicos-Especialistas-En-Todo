package com.elysium369.meet.ui.components.gauges

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import kotlin.math.*
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 3D holographic wrapper that adds premium depth effects around any gauge:
 * - Multi-layer outer glow bloom
 * - Metallic chrome bezel with rotating shimmer
 * - Drop shadow for 3D lift
 * - Per-style animated background (galaxy, gears, radar, rain, etc.)
 * - Inner vignette for depth
 * - Glass dome reflection overlay
 * - Specular highlight
 * - Holographic scanline sweep
 * - Floating luminous particles
 *
 * Usage: Wrap any gauge composable with Gauge3DWrapper { GaugeXxxWidget(...) }
 */
@Composable
fun Gauge3DWrapper(
    modifier: Modifier = Modifier,
    glowColor: Color = Color(0xFF00E5FF),
    style: GaugeStyleSet = GaugeStyleSet.ELITE,
    content: @Composable () -> Unit
) {
    val inf = rememberInfiniteTransition(label = "g3d")

    // Glow pulse — breathing effect
    val glowPulse by inf.animateFloat(
        0.55f, 1f,
        infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "gPulse"
    )

    // Bezel shimmer rotation
    val shimmerAngle by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(12000, easing = LinearEasing)),
        label = "shimmer"
    )

    // Particle orbit
    val particleOrbit by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(25000, easing = LinearEasing)),
        label = "pOrbit"
    )

    // Scanline sweep
    val scanPhase by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "scan"
    )

    // Secondary pulse (offset from main)
    val pulse2 by inf.animateFloat(
        0.4f, 0.9f,
        infiniteRepeatable(tween(3100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "p2"
    )

    // ── Animation values for style-specific backgrounds ──
    val slowRot by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(50000, easing = LinearEasing)),
        label = "slowRot"
    )
    val medRot by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(18000, easing = LinearEasing)),
        label = "medRot"
    )
    val fastRot by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(7000, easing = LinearEasing)),
        label = "fastRot"
    )
    val bgPulse by inf.animateFloat(
        0.3f, 1f,
        infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bgPulse"
    )
    val bgSweep by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "bgSweep"
    )
    val bgWave by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(10000, easing = LinearEasing)),
        label = "bgWave"
    )
    val bgDrift by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "bgDrift"
    )

    val animParams = GaugeAnimParams(
        slowRot = slowRot, medRot = medRot, fastRot = fastRot,
        pulse = bgPulse, sweep = bgSweep, wave = bgWave, drift = bgDrift
    )

    // Precompute particle positions (deterministic — golden angle distribution)
    val particles = remember {
        List(24) { i ->
            val golden = 137.508f
            ParticleData(
                angle = (i * golden) % 360f,
                distance = 0.55f + (i % 7) * 0.07f,
                size = 0.8f + (i % 4) * 0.7f,
                speed = 0.3f + (i % 5) * 0.25f,
                phase = i * 47f % 360f
            )
        }
    }

    // ── 3D TILT FLOAT EFFECT ──
    val tiltX by inf.animateFloat(
        initialValue = -5f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(4700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tiltX"
    )
    val tiltY by inf.animateFloat(
        initialValue = -6f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(6100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tiltY"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                rotationX = tiltX
                rotationY = tiltY
                cameraDistance = 15f * density
            }
            .shadow(
                elevation = 20.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.5f),
                spotColor = glowColor.copy(alpha = 0.5f)
            )
    ) {
        // ═══════════════════════════════════════════
        // LAYER 1: 3D BASE (behind gauge content)
        // ═══════════════════════════════════════════
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxR = min(cx, cy)
            val outerR = maxR - 6.dp.toPx()
            val bezelW = 5.dp.toPx()

            // ── DEEP OUTER GLOW BLOOM ──
            val bloomLayers = listOf(1.35f, 1.25f, 1.18f, 1.10f)
            bloomLayers.forEachIndexed { idx, scale ->
                val alpha = glowPulse * 0.035f / (idx + 1)
                drawCircle(
                    Brush.radialGradient(
                        listOf(glowColor.copy(alpha = alpha), Color.Transparent),
                        Offset(cx, cy), outerR * scale
                    ),
                    outerR * scale, Offset(cx, cy)
                )
            }

            // ── DROP SHADOW (3D lift with dynamic tilt offset) ──
            val shadowOffX = cx - (tiltY * 1.2f).dp.toPx()
            val shadowOffY = cy + 4.dp.toPx() + (tiltX * 1.2f).dp.toPx()
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                    Offset(shadowOffX, shadowOffY), outerR * 1.05f
                ),
                outerR * 1.05f, Offset(shadowOffX, shadowOffY)
            )

            // ── METALLIC BEZEL RING ──
            drawCircle(
                Brush.sweepGradient(
                    0f to Color(0xFF1E1E1E),
                    0.15f to Color(0xFF4A4A4A),
                    0.3f to Color(0xFF2A2A2A),
                    0.45f to Color(0xFF5C5C5C),
                    0.6f to Color(0xFF1A1A1A),
                    0.75f to Color(0xFF3E3E3E),
                    0.9f to Color(0xFF2A2A2A),
                    1f to Color(0xFF1E1E1E),
                    center = Offset(cx, cy)
                ),
                outerR + bezelW / 2f,
                Offset(cx, cy),
                style = Stroke(bezelW)
            )

            // Bezel rotating shimmer highlight
            val shimArcR = outerR + bezelW / 2f
            val shimOff = Offset(cx - shimArcR, cy - shimArcR)
            val shimSz = Size(shimArcR * 2, shimArcR * 2)
            drawArc(
                Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.4f to Color.White.copy(alpha = 0.12f),
                    0.5f to Color.White.copy(alpha = 0.22f),
                    0.6f to Color.White.copy(alpha = 0.12f),
                    1f to Color.Transparent,
                    center = Offset(cx, cy)
                ),
                shimmerAngle, 50f, false,
                topLeft = shimOff, size = shimSz,
                style = Stroke(bezelW * 0.8f, cap = StrokeCap.Round)
            )

            // Outer rim bright edge
            drawCircle(
                Color.White.copy(alpha = 0.06f),
                outerR + bezelW,
                Offset(cx, cy),
                style = Stroke(0.5f.dp.toPx())
            )

            // Inner rim (depth line)
            drawCircle(
                Color.Black.copy(alpha = 0.7f),
                outerR - 0.5f.dp.toPx(),
                Offset(cx, cy),
                style = Stroke(1.5f.dp.toPx())
            )

            // Accent glow on inner rim
            drawCircle(
                glowColor.copy(alpha = glowPulse * 0.08f),
                outerR - 1.dp.toPx(),
                Offset(cx, cy),
                style = Stroke(2.dp.toPx())
            )
        }

        // ═══════════════════════════════════════════
        // LAYER 2: ANIMATED BACKGROUND (per-style)
        // ═══════════════════════════════════════════
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxR = min(cx, cy)
            val r = maxR - 14.dp.toPx()

            // Clip all animations to the gauge circle
            clipPath(Path().apply {
                addOval(Rect(center = Offset(cx, cy), radius = r))
            }) {
                when (style) {
                    GaugeStyleSet.ELITE    -> drawEliteGalaxy(cx, cy, r, animParams, glowColor)
                    GaugeStyleSet.CLASSIC  -> drawClassicGears(cx, cy, r, animParams, glowColor)
                    GaugeStyleSet.CYBER    -> drawCyberGrid(cx, cy, r, animParams, glowColor)
                    GaugeStyleSet.RACING   -> drawRacingSpeed(cx, cy, r, animParams, glowColor)
                    GaugeStyleSet.RADIAL   -> drawRadialSolarSystem(cx, cy, r, animParams, glowColor)
                    GaugeStyleSet.THERMO   -> drawThermoHeat(cx, cy, r, animParams, glowColor)
                    GaugeStyleSet.HOLOGRAM -> drawHologramSphere(cx, cy, r, animParams, glowColor)
                    GaugeStyleSet.NEON_RETRO -> drawNeonRetroScene(cx, cy, r, animParams, glowColor)
                    GaugeStyleSet.LAMBO    -> drawLamboTraces(cx, cy, r, animParams, glowColor)
                    GaugeStyleSet.PLASMA   -> drawPlasmaVortex(cx, cy, r, animParams, glowColor)
                    GaugeStyleSet.AURORA   -> drawAuroraCurtains(cx, cy, r, animParams, glowColor)
                    GaugeStyleSet.FERRARI  -> drawFerrariCheckered(cx, cy, r, animParams, glowColor)
                    GaugeStyleSet.TOKYO    -> drawTokyoRain(cx, cy, r, animParams, glowColor)
                    GaugeStyleSet.MILITARY -> drawMilitaryRadar(cx, cy, r, animParams, glowColor)
                    GaugeStyleSet.DIAMOND  -> drawDiamondPrism(cx, cy, r, animParams, glowColor)
                    GaugeStyleSet.COCKPIT  -> drawCockpitHorizon(cx, cy, r, animParams, glowColor)
                }
            }
        }

        // ═══════════════════════════════════════════
        // LAYER 3: ACTUAL GAUGE CONTENT
        // ═══════════════════════════════════════════
        content()

        // ═══════════════════════════════════════════
        // LAYER 4: OVERLAY (glass, particles, scanline)
        // ═══════════════════════════════════════════
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxR = min(cx, cy)
            val r = maxR - 12.dp.toPx()

            // ── INNER VIGNETTE (depth darkening at edges) ──
            drawCircle(
                Brush.radialGradient(
                    0f to Color.Transparent,
                    0.65f to Color.Transparent,
                    0.9f to Color.Black.copy(alpha = 0.15f),
                    1f to Color.Black.copy(alpha = 0.45f),
                    center = Offset(cx, cy),
                    radius = r
                ),
                r, Offset(cx, cy)
            )

            // ── GLASS DOME REFLECTION ──
            val glassR = r * 0.78f
            val glassOff = Offset(cx - glassR, cy - glassR - r * 0.12f)
            val glassSz = Size(glassR * 2, glassR * 2)
            drawArc(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.07f),
                        Color.White.copy(alpha = 0.03f),
                        Color.Transparent
                    )
                ),
                -155f, 130f, false,
                topLeft = glassOff, size = glassSz,
                style = Stroke(r * 0.2f, cap = StrokeCap.Round)
            )

            // Small secondary reflection arc
            val glass2R = r * 0.5f
            val glass2Off = Offset(cx - glass2R + r * 0.25f, cy - glass2R + r * 0.3f)
            val glass2Sz = Size(glass2R * 2, glass2R * 2)
            drawArc(
                Color.White.copy(alpha = 0.025f),
                30f, 60f, false,
                topLeft = glass2Off, size = glass2Sz,
                style = Stroke(r * 0.08f, cap = StrokeCap.Round)
            )

            // ── SPECULAR HIGHLIGHT (point light) ──
            val specX = cx - r * 0.28f
            val specY = cy - r * 0.32f
            drawCircle(
                Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.18f * pulse2),
                        Color.White.copy(alpha = 0.06f * pulse2),
                        Color.Transparent
                    ),
                    Offset(specX, specY), 10.dp.toPx()
                ),
                10.dp.toPx(), Offset(specX, specY)
            )
            drawCircle(Color.White.copy(alpha = 0.35f * pulse2), 1.5f.dp.toPx(), Offset(specX, specY))

            // ── HOLOGRAPHIC SCANLINE SWEEP ──
            val scanY = cy - r + scanPhase * r * 2
            if (scanY in (cy - r)..(cy + r)) {
                val distFromCenter = abs(scanY - cy)
                val halfChord = sqrt(max(0f, r * r - distFromCenter * distFromCenter))
                drawLine(
                    glowColor.copy(alpha = 0.07f * glowPulse),
                    Offset(cx - halfChord, scanY),
                    Offset(cx + halfChord, scanY),
                    1.5f.dp.toPx()
                )
                for (offset in listOf(-2.dp.toPx(), 2.dp.toPx())) {
                    val oY = scanY + offset
                    val oDist = abs(oY - cy)
                    if (oDist < r) {
                        val oHalf = sqrt(max(0f, r * r - oDist * oDist))
                        drawLine(
                            glowColor.copy(alpha = 0.02f),
                            Offset(cx - oHalf, oY),
                            Offset(cx + oHalf, oY),
                            1.dp.toPx()
                        )
                    }
                }
            }

            // ── FLOATING LUMINOUS PARTICLES ──
            particles.forEach { p ->
                val a = Math.toRadians(((particleOrbit * p.speed + p.phase) % 360f).toDouble())
                val dist = r * p.distance
                val px = cx + (dist * cos(a)).toFloat()
                val py = cy + (dist * sin(a)).toFloat()
                val pAlpha = (sin(a * 2.3 + p.phase * 0.05) * 0.35 + 0.35).toFloat()
                    .coerceIn(0f, 0.7f) * glowPulse

                val dx = px - cx; val dy = py - cy
                if (dx * dx + dy * dy < r * r * 0.92f) {
                    drawCircle(
                        glowColor.copy(alpha = pAlpha * 0.15f),
                        p.size.dp.toPx() * 3f,
                        Offset(px, py)
                    )
                    drawCircle(
                        glowColor.copy(alpha = pAlpha * 0.5f),
                        p.size.dp.toPx() * 1.2f,
                        Offset(px, py)
                    )
                    drawCircle(
                        Color.White.copy(alpha = pAlpha * 0.7f),
                        p.size.dp.toPx() * 0.4f,
                        Offset(px, py)
                    )
                }
            }

            // ── EDGE RIM GLOW ──
            val rimR = r + 4.dp.toPx()
            drawCircle(
                glowColor.copy(alpha = glowPulse * 0.04f),
                rimR,
                Offset(cx, cy),
                style = Stroke(3.dp.toPx())
            )
        }
    }
}

/**
 * Particle data for the floating luminous particles effect.
 */
private data class ParticleData(
    val angle: Float,
    val distance: Float,
    val size: Float,
    val speed: Float,
    val phase: Float
)
