package com.elysium369.meet.ui.components.gauges

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import kotlin.math.*

/**
 * Animated background drawing functions for each gauge style.
 * Each renders unique animated elements INSIDE the gauge circle.
 * Called from Gauge3DWrapper's mid-layer Canvas.
 *
 * All functions receive:
 *  - cx, cy, r: gauge center and radius
 *  - anim: shared animation parameters from the wrapper
 *  - color: accent color for the style
 */
data class GaugeAnimParams(
    val slowRot: Float,   // 0-360 in ~50s
    val medRot: Float,    // 0-360 in ~18s
    val fastRot: Float,   // 0-360 in ~7s
    val pulse: Float,     // 0-1 breathing
    val sweep: Float,     // 0-1 linear 4s
    val wave: Float,      // 0-360 in ~10s
    val drift: Float      // 0-1 linear 6s
)

// ═══════════════════════════════════════════════════════════════
// 1. ELITE — Spiral Galaxy + Orbiting Planetary System + Meteor Shower (Concept 1 & 18 combined)
// ═══════════════════════════════════════════════════════════════
fun DrawScope.drawEliteGalaxy(cx: Float, cy: Float, r: Float, a: GaugeAnimParams, col: Color) {
    val rot = Math.toRadians(a.slowRot.toDouble())

    // ── Star Field & Twinkling ──
    for (i in 0 until 30) {
        val golden = 137.508f
        val sA = Math.toRadians((i * golden).toDouble())
        val sD = r * ((i * 37 % 100) / 120f + 0.1f)
        val sx = cx + (sD * cos(sA)).toFloat()
        val sy = cy + (sD * sin(sA)).toFloat()
        val twinkle = (sin(a.wave * 0.0175 + i * 0.7) * 0.5 + 0.5).toFloat()
        val starSize = if (i % 7 == 0) 1.8f else 0.8f
        drawCircle(Color.White.copy(alpha = twinkle * 0.25f), starSize.dp.toPx(), Offset(sx, sy))
    }

    // ── Dynamic Constellations (Connected Stars) ──
    val constPoints = List(6) { idx ->
        val cA = rot * 0.5 + idx * (2 * PI / 6)
        val cR = r * (0.3f + (idx % 3) * 0.12f)
        Offset(cx + (cR * cos(cA)).toFloat(), cy + (cR * sin(cA)).toFloat())
    }
    for (i in 0 until constPoints.size) {
        val nextIdx = (i + 1) % constPoints.size
        drawLine(
            col.copy(alpha = 0.05f * a.pulse),
            constPoints[i], constPoints[nextIdx],
            0.8f.dp.toPx()
        )
    }

    // ── Planetary System Orbits & Planets ──
    val planetOrbits = listOf(0.35f, 0.58f, 0.78f)
    planetOrbits.forEachIndexed { idx, orbitScale ->
        val oR = r * orbitScale
        // Draw orbital rings
        drawCircle(Color.White.copy(alpha = 0.02f), oR, Offset(cx, cy), style = Stroke(0.5f.dp.toPx()))
        // Orbiting planet position
        val pSpeed = 1.0f - idx * 0.25f
        val pAngle = rot * pSpeed + (idx * 2.094)
        val px = cx + (oR * cos(pAngle)).toFloat()
        val py = cy + (oR * sin(pAngle)).toFloat()
        val pCol = if (idx == 0) Color(0xFF00E5FF) else if (idx == 1) Color(0xFF80D8FF) else Color.White
        drawCircle(pCol.copy(alpha = 0.06f), 6.dp.toPx(), Offset(px, py))
        drawCircle(pCol.copy(alpha = 0.3f), 2.5f.dp.toPx(), Offset(px, py))
    }

    // ── Shooting Stars (Meteor Shower) ──
    for (i in 0 until 3) {
        val progress = (a.drift * 1.5f + i * 0.33f) % 1f
        val meteorX = cx - r * 0.8f + progress * r * 1.6f
        val meteorY = cy - r * 0.6f + progress * r * 1.2f
        val mAlpha = (1f - abs(progress - 0.5f) * 2f).coerceIn(0f, 1f) * 0.18f
        drawLine(
            Color.White.copy(alpha = mAlpha),
            Offset(meteorX, meteorY),
            Offset(meteorX - 12.dp.toPx(), meteorY - 9.dp.toPx()),
            1.2f.dp.toPx()
        )
    }

    // ── Nebula Spiral Arms ──
    for (arm in 0 until 2) {
        val armOffset = arm * PI
        for (j in 0 until 40) {
            val t = j / 40f
            val spiralA = rot + armOffset + t * 3.5
            val spiralR = r * 0.08f + t * r * 0.65f
            val sx = cx + (spiralR * cos(spiralA)).toFloat()
            val sy = cy + (spiralR * sin(spiralA)).toFloat()
            val alpha = (1f - t) * 0.1f * a.pulse
            val dotSize = (1f - t) * 2f + 0.5f
            drawCircle(col.copy(alpha = alpha), dotSize.dp.toPx(), Offset(sx, sy))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 2. CLASSIC — Mechanical Watch Gears + Crank & Piston (Concept 2 & 7 combined)
// ═══════════════════════════════════════════════════════════════
fun DrawScope.drawClassicGears(cx: Float, cy: Float, r: Float, a: GaugeAnimParams, col: Color) {
    val gold = Color(0xFFD4A843)
    val steel = Color(0xFFB0BEC5)

    // ── Central Crank & Piston Animation ──
    val pistonRot = a.fastRot * 1.2f
    val radPistonRot = Math.toRadians(pistonRot.toDouble())
    val crankR = r * 0.16f
    val rodLen = r * 0.4f
    
    val crankX = cx
    val crankY = cy + r * 0.25f
    val pinX = crankX + (crankR * cos(radPistonRot)).toFloat()
    val pinY = crankY + (crankR * sin(radPistonRot)).toFloat()
    
    // Slide piston wrist pin vertically
    val dx = pinX - crankX
    val wristPinY = pinY - sqrt(max(0f, rodLen * rodLen - dx * dx))
    val wristPinX = crankX

    // Draw cylinder chamber outline
    drawLine(steel.copy(alpha = 0.04f), Offset(crankX - r * 0.13f, crankY - r * 0.55f), Offset(crankX - r * 0.13f, crankY - r * 0.1f), 1.2f.dp.toPx())
    drawLine(steel.copy(alpha = 0.04f), Offset(crankX + r * 0.13f, crankY - r * 0.55f), Offset(crankX + r * 0.13f, crankY - r * 0.1f), 1.2f.dp.toPx())

    // Draw Crank Wheel & Rod
    drawCircle(steel.copy(alpha = 0.02f), crankR, Offset(crankX, crankY), style = Stroke(1.dp.toPx()))
    drawLine(steel.copy(alpha = 0.06f), Offset(crankX, crankY), Offset(pinX, pinY), 1.5f.dp.toPx())
    drawLine(steel.copy(alpha = 0.1f), Offset(pinX, pinY), Offset(wristPinX, wristPinY), 2.5f.dp.toPx())
    
    // Draw Piston Head
    drawRect(steel.copy(alpha = 0.12f), topLeft = Offset(wristPinX - r * 0.11f, wristPinY - r * 0.06f), size = Size(r * 0.22f, r * 0.1f))
    drawRect(gold.copy(alpha = 0.2f), topLeft = Offset(wristPinX - r * 0.11f, wristPinY - r * 0.06f), size = Size(r * 0.22f, r * 0.1f), style = Stroke(1.dp.toPx()))

    // ── Interlocking Watch Gears ──
    fun drawGear(gx: Float, gy: Float, gr: Float, teeth: Int, rotation: Float, gearCol: Color) {
        val rotRad = Math.toRadians(rotation.toDouble())
        drawCircle(gearCol.copy(alpha = 0.04f), gr * 0.6f, Offset(gx, gy), style = Stroke(0.8f.dp.toPx()))
        for (s in 0 until 4) {
            val sa = rotRad + s * Math.PI / 2
            drawLine(
                gearCol.copy(alpha = 0.04f),
                Offset(gx, gy),
                Offset(gx + (gr * 0.5f * cos(sa)).toFloat(), gy + (gr * 0.5f * sin(sa)).toFloat()),
                0.8f.dp.toPx()
            )
        }
        for (t in 0 until teeth) {
            val tA = rotRad + t * 2.0 * Math.PI / teeth
            val tA2 = tA + Math.PI / teeth * 0.5
            val inner = gr * 0.85f
            val outer = gr
            drawLine(
                gearCol.copy(alpha = 0.07f),
                Offset(gx + (inner * cos(tA)).toFloat(), gy + (inner * sin(tA)).toFloat()),
                Offset(gx + (outer * cos(tA)).toFloat(), gy + (outer * sin(tA)).toFloat()),
                1.5f.dp.toPx()
            )
            drawLine(
                gearCol.copy(alpha = 0.05f),
                Offset(gx + (outer * cos(tA)).toFloat(), gy + (outer * sin(tA)).toFloat()),
                Offset(gx + (outer * cos(tA2)).toFloat(), gy + (outer * sin(tA2)).toFloat()),
                1.dp.toPx()
            )
        }
        drawCircle(gearCol.copy(alpha = 0.05f), gr, Offset(gx, gy), style = Stroke(1.2f.dp.toPx()))
    }

    // 3 interlocking gears in background
    drawGear(cx - r * 0.38f, cy - r * 0.22f, r * 0.25f, 10, a.medRot, gold)
    drawGear(cx + r * 0.35f, cy - r * 0.2f, r * 0.2f, 8, -a.medRot * 1.25f, steel)
    drawGear(cx + r * 0.38f, cy + r * 0.28f, r * 0.16f, 7, a.medRot * 1.4f, gold.copy(alpha = 0.6f))

    // Center axle
    drawCircle(gold.copy(alpha = 0.04f * a.pulse), 3.dp.toPx(), Offset(cx, cy))
}

// ═══════════════════════════════════════════════════════════════
// 3. CYBER — Hex Grid + Circuit Board traces + Binary Rain (Concept 3 & 11 combined)
// ═══════════════════════════════════════════════════════════════
fun DrawScope.drawCyberGrid(cx: Float, cy: Float, r: Float, a: GaugeAnimParams, col: Color) {
    // ── Hexagonal Grid ──
    val hexSize = r * 0.18f
    val hexH = hexSize * sqrt(3f)
    for (row in -4..4) {
        for (c in -4..4) {
            val hx = cx + c * hexSize * 1.5f + (if (row % 2 != 0) hexSize * 0.75f else 0f)
            val hy = cy + row * hexH * 0.5f
            val dist = sqrt((hx - cx).pow(2) + (hy - cy).pow(2))
            if (dist < r * 0.85f) {
                val hexAlpha = (1f - dist / r) * 0.05f
                for (side in 0 until 6) {
                    val a1 = Math.toRadians((60.0 * side + 30))
                    val a2 = Math.toRadians((60.0 * (side + 1) + 30))
                    drawLine(
                        col.copy(alpha = hexAlpha),
                        Offset(hx + (hexSize * 0.4f * cos(a1)).toFloat(), hy + (hexSize * 0.4f * sin(a1)).toFloat()),
                        Offset(hx + (hexSize * 0.4f * cos(a2)).toFloat(), hy + (hexSize * 0.4f * sin(a2)).toFloat()),
                        0.5f.dp.toPx()
                    )
                }
                val nodePulse = (sin(a.wave * 0.0175 + c * 0.5 + row * 0.3) * 0.5 + 0.5).toFloat()
                if (nodePulse > 0.8f) {
                    drawCircle(col.copy(alpha = nodePulse * 0.12f), 1.5f.dp.toPx(), Offset(hx, hy))
                }
            }
        }
    }

    // ── Circuit Board Traces ──
    val traces = listOf(
        listOf(Offset(cx - r * 0.7f, cy - r * 0.2f), Offset(cx - r * 0.3f, cy - r * 0.2f), Offset(cx - r * 0.1f, cy - r * 0.4f)),
        listOf(Offset(cx + r * 0.7f, cy + r * 0.3f), Offset(cx + r * 0.3f, cy + r * 0.3f), Offset(cx + r * 0.2f, cy + r * 0.1f)),
        listOf(Offset(cx - r * 0.5f, cy + r * 0.4f), Offset(cx - r * 0.2f, cy + r * 0.4f), Offset(cx, cy + r * 0.2f))
    )
    traces.forEach { path ->
        for (i in 0 until path.size - 1) {
            drawLine(col.copy(alpha = 0.05f), path[i], path[i+1], 1.dp.toPx())
        }
        // Electron pulses moving on traces
        val tProgress = a.drift
        val numSegments = path.size - 1
        val segmentProgress = tProgress * numSegments
        val activeSeg = segmentProgress.toInt().coerceIn(0, numSegments - 1)
        val segRatio = segmentProgress % 1f
        val p1 = path[activeSeg]
        val p2 = path[activeSeg + 1]
        val curX = p1.x + (p2.x - p1.x) * segRatio
        val curY = p1.y + (p2.y - p1.y) * segRatio
        drawCircle(col.copy(alpha = 0.22f * a.pulse), 2.5f.dp.toPx(), Offset(curX, curY))
    }

    // ── Vertical Binary Rain / Data Streams ──
    for (i in 0 until 8) {
        val streamX = cx + (i - 3.5f) * r * 0.22f
        val streamY = cy - r + ((a.drift + i * 0.12f) % 1f) * r * 2f
        for (j in 0 until 6) {
            val sy = streamY - j * 10.dp.toPx()
            if (abs(sy - cy) < r * 0.8f) {
                val sAlpha = (1f - j / 6f) * 0.15f
                drawLine(
                    col.copy(alpha = sAlpha),
                    Offset(streamX, sy),
                    Offset(streamX, sy - 4.dp.toPx()),
                    1.2f.dp.toPx()
                )
                if (j == 0) {
                    drawCircle(Color.White.copy(alpha = sAlpha * 1.5f), 1.5f.dp.toPx(), Offset(streamX, sy))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 4. RACING — Speed Lines + Combustion Shockwaves (Concept 4 & 8 combined)
// ═══════════════════════════════════════════════════════════════
fun DrawScope.drawRacingSpeed(cx: Float, cy: Float, r: Float, a: GaugeAnimParams, col: Color) {
    // ── Radiating Speed Lines ──
    for (i in 0 until 24) {
        val lineA = Math.toRadians((i * 15f + a.fastRot * 0.3f).toDouble())
        val progress = (a.drift + i * 0.04f) % 1f
        val startD = r * (0.12f + progress * 0.55f)
        val endD = startD + r * 0.18f
        val lineAlpha = (1f - progress) * 0.14f
        drawLine(
            col.copy(alpha = lineAlpha),
            Offset(cx + (startD * cos(lineA)).toFloat(), cy + (startD * sin(lineA)).toFloat()),
            Offset(cx + (endD * cos(lineA)).toFloat(), cy + (endD * sin(lineA)).toFloat()),
            1.dp.toPx()
        )
    }

    // ── Tire track curves ──
    for (track in 0 until 3) {
        val trackR = r * (0.32f + track * 0.15f)
        drawArc(
            col.copy(alpha = 0.04f),
            a.fastRot + track * 30f, 40f, false,
            topLeft = Offset(cx - trackR, cy - trackR),
            size = Size(trackR * 2, trackR * 2),
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
        )
    }

    // ── Center Spark Plug & Combustion Chamber Shockwaves ──
    val sparkPulse = a.sweep
    val shockwaveR = sparkPulse * r * 0.85f
    val swAlpha = (1f - sparkPulse) * 0.15f
    drawCircle(
        col.copy(alpha = swAlpha),
        shockwaveR,
        Offset(cx, cy),
        style = Stroke(2.dp.toPx())
    )
    drawCircle(
        Color.White.copy(alpha = swAlpha * 0.3f),
        shockwaveR - 3.dp.toPx(),
        Offset(cx, cy),
        style = Stroke(1.dp.toPx())
    )

    // Center spark crackle
    if (a.pulse > 0.82f) {
        val sparkLen = 8.dp.toPx()
        for (i in 0 until 4) {
            val sA = Math.toRadians((i * 90f + a.wave).toDouble())
            drawLine(
                Color.White.copy(alpha = 0.8f),
                Offset(cx, cy),
                Offset(cx + (sparkLen * cos(sA)).toFloat(), cy + (sparkLen * sin(sA)).toFloat()),
                1.5f.dp.toPx()
            )
        }
    }
    drawCircle(Color.White.copy(alpha = 0.3f * a.pulse), 4.dp.toPx(), Offset(cx, cy))
}

// ═══════════════════════════════════════════════════════════════
// 5. RADIAL — Solar Orbits + Interference Wave Ripples (Concept 5 & 16 combined)
// ═══════════════════════════════════════════════════════════════
fun DrawScope.drawRadialSolarSystem(cx: Float, cy: Float, r: Float, a: GaugeAnimParams, col: Color) {
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFFFD54F).copy(alpha = 0.15f * a.pulse), Color.Transparent),
            Offset(cx, cy), r * 0.12f
        ),
        r * 0.12f, Offset(cx, cy)
    )
    drawCircle(Color(0xFFFFD54F).copy(alpha = 0.25f * a.pulse), 3.5f.dp.toPx(), Offset(cx, cy))

    data class Planet(val orbitR: Float, val size: Float, val speed: Float, val color: Color)
    val planets = listOf(
        Planet(r * 0.2f, 2f, 3.2f, Color(0xFF90A4AE)),
        Planet(r * 0.32f, 2.5f, 2.4f, Color(0xFFFFCC80)),
        Planet(r * 0.44f, 3f, 1.6f, col),
        Planet(r * 0.58f, 2.6f, 1.1f, Color(0xFFEF5350)),
        Planet(r * 0.72f, 4f, 0.6f, Color(0xFFFFB74D))
    )

    planets.forEachIndexed { idx, p ->
        drawCircle(Color.White.copy(alpha = 0.03f), p.orbitR, Offset(cx, cy), style = Stroke(0.5f.dp.toPx()))
        val pA = Math.toRadians((a.medRot * p.speed).toDouble())
        val px = cx + (p.orbitR * cos(pA)).toFloat()
        val py = cy + (p.orbitR * sin(pA)).toFloat()
        
        drawCircle(p.color.copy(alpha = 0.08f), p.size.dp.toPx() * 3.5f, Offset(px, py))
        drawCircle(p.color.copy(alpha = 0.3f), p.size.dp.toPx(), Offset(px, py))
        drawCircle(Color.White.copy(alpha = 0.2f), p.size.dp.toPx() * 0.4f, Offset(px, py))

        if (idx == 2 || idx == 4) {
            val rippleProgress = (a.drift * 1.5f + idx * 0.3f) % 1f
            val rippleR = p.size.dp.toPx() * 1.5f + rippleProgress * r * 0.22f
            val rippleAlpha = (1f - rippleProgress) * 0.1f * a.pulse
            drawCircle(
                p.color.copy(alpha = rippleAlpha),
                rippleR,
                Offset(px, py),
                style = Stroke(0.8f.dp.toPx())
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 6. THERMO — Rising Convection + Boiling Chemical Bubbles (Concept 6 & 17 combined)
// ═══════════════════════════════════════════════════════════════
fun DrawScope.drawThermoHeat(cx: Float, cy: Float, r: Float, a: GaugeAnimParams, col: Color) {
    for (i in 0 until 25) {
        val golden = 137.508f
        val baseX = cx + sin((i * golden * 0.0175).toDouble()).toFloat() * r * 0.7f
        val progress = (a.drift + i * 0.04f) % 1f
        val py = cy + r * 0.72f - progress * r * 1.44f
        val px = baseX + sin((progress * 3.5 + i).toDouble()).toFloat() * r * 0.08f
        val dist = sqrt((px - cx).pow(2) + (py - cy).pow(2))
        if (dist < r * 0.85f) {
            val heatAlpha = (1f - progress) * 0.16f
            val heatColor = when {
                progress < 0.3f -> Color(0xFFFF6D00)
                progress < 0.65f -> Color(0xFFFFAB00)
                else -> col
            }
            drawCircle(heatColor.copy(alpha = heatAlpha), (1.8f - progress * 0.5f).dp.toPx(), Offset(px, py))
            drawCircle(heatColor.copy(alpha = heatAlpha * 0.3f), (3f - progress).dp.toPx(), Offset(px, py))
        }
    }

    for (i in 0 until 12) {
        val bX = cx + ((i * 79 % 100) - 50) / 50f * r * 0.6f
        val progress = (a.drift * 0.8f + i * 0.08f) % 1f
        val bY = cy + r * 0.65f - progress * r * 1.3f
        val bDist = sqrt((bX - cx).pow(2) + (bY - cy).pow(2))
        if (bDist < r * 0.8f) {
            val bAlpha = (1f - progress) * 0.18f
            val bSize = (3.dp.toPx() + (i % 3) * 2.dp.toPx()) * (0.5f + progress * 0.5f)
            
            if (progress > 0.88f) {
                val burstScale = (progress - 0.88f) / 0.12f
                val burstR = bSize * (1f + burstScale * 0.8f)
                drawCircle(col.copy(alpha = bAlpha * (1f - burstScale)), burstR, Offset(bX, bY), style = Stroke(0.8f.dp.toPx()))
                for (t in 0 until 4) {
                    val sRad = Math.toRadians((t * 90.0 + i * 30).toDouble())
                    drawLine(
                        col.copy(alpha = bAlpha * (1f - burstScale)),
                        Offset(bX + (burstR * cos(sRad)).toFloat(), bY + (burstR * sin(sRad)).toFloat()),
                        Offset(bX + ((burstR + 3.dp.toPx()) * cos(sRad)).toFloat(), bY + ((burstR + 3.dp.toPx()) * sin(sRad)).toFloat()),
                        0.8f.dp.toPx()
                    )
                }
            } else {
                drawCircle(col.copy(alpha = bAlpha * 0.08f), bSize, Offset(bX, bY))
                drawCircle(col.copy(alpha = bAlpha), bSize, Offset(bX, bY), style = Stroke(1.dp.toPx()))
                drawCircle(Color.White.copy(alpha = bAlpha * 0.6f), bSize * 0.25f, Offset(bX - bSize * 0.35f, bY - bSize * 0.35f))
            }
        }
    }

    for (wave in 0 until 3) {
        val waveY = cy + r * 0.25f - wave * r * 0.22f
        val path = Path()
        var started = false
        for (x in -18..18) {
            val px = cx + x * r * 0.045f
            val py = waveY + sin((x * 0.32 + a.wave * 0.04 + wave * 1.8).toDouble()).toFloat() * r * 0.025f
            val dist = sqrt((px - cx).pow(2) + (py - cy).pow(2))
            if (dist < r * 0.8f) {
                if (!started) { path.moveTo(px, py); started = true }
                else path.lineTo(px, py)
            }
        }
        drawPath(path, col.copy(alpha = 0.04f), style = Stroke(1.dp.toPx()))
    }
}

// ═══════════════════════════════════════════════════════════════
// 7. HOLOGRAM — 3D Wireframe Sphere + Sonic Shockwaves (Concept 7 & 12 combined)
// ═══════════════════════════════════════════════════════════════
fun DrawScope.drawHologramSphere(cx: Float, cy: Float, r: Float, a: GaugeAnimParams, col: Color) {
    val sphereR = r * 0.52f
    val rotY = Math.toRadians(a.slowRot.toDouble())

    for (lat in -3..3) {
        val latAngle = lat * 25.0 * Math.PI / 180.0
        val latR = sphereR * cos(latAngle).toFloat()
        val latY = cy + (sphereR * sin(latAngle)).toFloat()
        drawOval(
            col.copy(alpha = 0.06f),
            topLeft = Offset(cx - latR, latY - latR * 0.28f),
            size = Size(latR * 2, latR * 0.56f),
            style = Stroke(0.8f.dp.toPx())
        )
    }

    for (lon in 0 until 6) {
        val lonAngle = rotY + lon * Math.PI / 6
        val path = Path()
        for (j in 0..36) {
            val theta = j * 10.0 * Math.PI / 180.0
            val x3d = sphereR * sin(theta) * cos(lonAngle)
            val y3d = sphereR * cos(theta)
            val px = cx + x3d.toFloat()
            val py = cy - y3d.toFloat()
            if (j == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, col.copy(alpha = 0.06f), style = Stroke(0.8f.dp.toPx()))
    }

    val scanA = Math.toRadians(a.fastRot.toDouble())
    for (j in 0..36) {
        val theta = j * 10.0 * Math.PI / 180.0
        val x3d = sphereR * sin(theta) * cos(scanA)
        val y3d = sphereR * cos(theta)
        val px = cx + x3d.toFloat()
        val py = cy - y3d.toFloat()
        drawCircle(col.copy(alpha = 0.15f * a.pulse), 1.dp.toPx(), Offset(px, py))
    }

    for (w in 0 until 2) {
        val wProgress = (a.drift * 1.2f + w * 0.5f) % 1f
        val rippleR = sphereR + wProgress * (r - sphereR) * 0.9f
        val wAlpha = (1f - wProgress) * 0.1f * a.pulse
        drawCircle(
            col.copy(alpha = wAlpha),
            rippleR,
            Offset(cx, cy),
            style = Stroke(1.dp.toPx())
        )
        for (i in 0 until 8) {
            val tickRad = Math.toRadians((i * 45f + a.wave * 0.2f).toDouble())
            drawCircle(
                col.copy(alpha = wAlpha * 1.5f),
                1.5f.dp.toPx(),
                Offset(cx + (rippleR * cos(tickRad)).toFloat(), cy + (rippleR * sin(tickRad)).toFloat())
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 8. NEON_RETRO — Perspective Grid + Giant Sun + Warp Starfield (Concept 8 & 20 combined)
// ═══════════════════════════════════════════════════════════════
fun DrawScope.drawNeonRetroScene(cx: Float, cy: Float, r: Float, a: GaugeAnimParams, col: Color) {
    val sunColor = Color(0xFFFF6D00)
    val gridColor = col

    for (i in 0 until 20) {
        val starA = Math.toRadians((i * 18f + a.slowRot * 0.05f).toDouble())
        val progress = (a.drift + i * 0.05f) % 1f
        val startD = r * 0.1f + progress * r * 0.65f
        val endD = startD + progress * r * 0.08f
        val starAlpha = progress * 0.2f
        drawLine(
            Color.White.copy(alpha = starAlpha),
            Offset(cx + (startD * cos(starA)).toFloat(), cy - r * 0.1f + (startD * sin(starA)).toFloat() * 0.5f),
            Offset(cx + (endD * cos(starA)).toFloat(), cy - r * 0.1f + (endD * sin(starA)).toFloat() * 0.5f),
            1.dp.toPx()
        )
    }

    val sunCy = cy + r * 0.08f
    val sunR = r * 0.24f
    drawCircle(
        Brush.radialGradient(
            listOf(sunColor.copy(alpha = 0.16f), Color(0xFFE91E63).copy(alpha = 0.08f), Color.Transparent),
            Offset(cx, sunCy), sunR
        ),
        sunR, Offset(cx, sunCy)
    )
    for (band in 0 until 5) {
        val bandY = sunCy + (band - 2) * sunR * 0.35f
        drawLine(Color.Black.copy(alpha = 0.35f), Offset(cx - sunR, bandY), Offset(cx + sunR, bandY), 2.2f.dp.toPx())
    }

    val horizonY = cy + r * 0.12f
    for (i in 1..6) {
        val lineProgress = (a.drift + i * 0.15f) % 1f
        val py = horizonY + lineProgress * r * 0.58f
        val spread = (py - horizonY) / (r * 0.58f)
        val halfW = r * 0.08f + spread * r * 0.72f
        drawLine(gridColor.copy(alpha = 0.08f * (1f - spread * 0.4f)),
            Offset(cx - halfW, py), Offset(cx + halfW, py), 0.6f.dp.toPx())
    }
    for (i in -4..4) {
        val topX = cx + i * r * 0.02f
        val botX = cx + i * r * 0.22f
        drawLine(gridColor.copy(alpha = 0.05f),
            Offset(topX, horizonY), Offset(botX, cy + r * 0.7f), 0.5f.dp.toPx())
    }
}

// ═══════════════════════════════════════════════════════════════
// 9. LAMBO — Jet Turbine + Radar Vector Sweep (Concept 9 & 14 combined)
// ═══════════════════════════════════════════════════════════════
fun DrawScope.drawLamboTraces(cx: Float, cy: Float, r: Float, a: GaugeAnimParams, col: Color) {
    val turbineRot = a.fastRot * 0.5f
    val numBlades = 12
    for (i in 0 until numBlades) {
        val bAngle = Math.toRadians((i * (360f / numBlades) + turbineRot).toDouble())
        val path = Path()
        val innerR = r * 0.12f
        val outerR = r * 0.55f
        
        val x1 = cx + (innerR * cos(bAngle)).toFloat()
        val y1 = cy + (innerR * sin(bAngle)).toFloat()
        val x2 = cx + (outerR * cos(bAngle)).toFloat()
        val y2 = cy + (outerR * sin(bAngle)).toFloat()
        
        val tiltAngle = bAngle + 0.16
        val x3 = cx + (outerR * cos(tiltAngle)).toFloat()
        val y3 = cy + (outerR * sin(tiltAngle)).toFloat()
        val x4 = cx + (innerR * cos(tiltAngle)).toFloat()
        val y4 = cy + (innerR * sin(tiltAngle)).toFloat()

        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        path.lineTo(x3, y3)
        path.lineTo(x4, y4)
        path.close()
        drawPath(path, col.copy(alpha = 0.035f * a.pulse))
    }
    drawCircle(col.copy(alpha = 0.05f), r * 0.12f, Offset(cx, cy), style = Stroke(1.dp.toPx()))

    val patterns = listOf(
        listOf(Offset(cx, cy - r * 0.55f), Offset(cx, cy)),
        listOf(Offset(cx, cy), Offset(cx - r * 0.38f, cy + r * 0.45f)),
        listOf(Offset(cx, cy), Offset(cx + r * 0.38f, cy + r * 0.45f)),
        listOf(Offset(cx - r * 0.48f, cy - r * 0.25f), Offset(cx, cy - r * 0.55f)),
        listOf(Offset(cx + r * 0.48f, cy - r * 0.25f), Offset(cx, cy - r * 0.55f)),
        listOf(Offset(cx - r * 0.58f, cy + r * 0.1f), Offset(cx - r * 0.48f, cy - r * 0.25f)),
        listOf(Offset(cx + r * 0.58f, cy + r * 0.1f), Offset(cx + r * 0.48f, cy - r * 0.25f)),
    )
    patterns.forEach { line ->
        drawLine(col.copy(alpha = 0.04f), line[0], line[1], 1.dp.toPx())
    }

    for (i in patterns.indices) {
        val progress = (a.drift + i * 0.14f) % 1f
        val p1 = patterns[i][0]
        val p2 = patterns[i][1]
        val tx = p1.x + (p2.x - p1.x) * progress
        val ty = p1.y + (p2.y - p1.y) * progress
        drawCircle(col.copy(alpha = 0.18f * a.pulse), 3.dp.toPx(), Offset(tx, ty))
        drawCircle(Color.White.copy(alpha = 0.25f * a.pulse), 1.dp.toPx(), Offset(tx, ty))
    }

    val sweepRad = Math.toRadians((a.slowRot * 1.5f).toDouble())
    drawLine(
        col.copy(alpha = 0.08f),
        Offset(cx, cy),
        Offset(cx + (r * 0.65f * cos(sweepRad)).toFloat(), cy + (r * 0.65f * sin(sweepRad)).toFloat()),
        1.2f.dp.toPx()
    )
}

// ═══════════════════════════════════════════════════════════════
// 10. PLASMA — Electric Vortex + Arc Reactor Core (Concept 10 & 13 combined)
// ═══════════════════════════════════════════════════════════════
fun DrawScope.drawPlasmaVortex(cx: Float, cy: Float, r: Float, a: GaugeAnimParams, col: Color) {
    for (arm in 0 until 3) {
        val armPhase = arm * 2.094
        for (j in 0 until 35) {
            val t = j / 35f
            val spiralA = Math.toRadians(a.medRot.toDouble()) + armPhase + t * 3.0
            val spiralR = t * r * 0.65f
            val sx = cx + (spiralR * cos(spiralA)).toFloat()
            val sy = cy + (spiralR * sin(spiralA)).toFloat()
            val alpha = t * (1f - t) * 0.22f * a.pulse
            drawCircle(col.copy(alpha = alpha), (1.5f + t * 2f).dp.toPx(), Offset(sx, sy))
        }
    }

    val arcR = r * 0.76f
    drawCircle(col.copy(alpha = 0.04f), arcR, Offset(cx, cy), style = Stroke(3.dp.toPx()))
    val sectors = 10
    for (s in 0 until sectors) {
        val sA = Math.toRadians((s * (360f / sectors) + a.slowRot * 0.2f).toDouble())
        val sx = cx + (arcR * cos(sA)).toFloat()
        val sy = cy + (arcR * sin(sA)).toFloat()
        
        val glow = (sin(a.wave * 0.025 + s * 1.5) * 0.5 + 0.5).toFloat()
        drawCircle(col.copy(alpha = 0.06f + glow * 0.12f), 6.dp.toPx(), Offset(sx, sy))
        drawCircle(Color.White.copy(alpha = (0.2f + glow * 0.4f) * a.pulse), 2.dp.toPx(), Offset(sx, sy))

        if (s % 2 == 0 && a.pulse > 0.82f) {
            drawLine(
                col.copy(alpha = 0.08f * glow),
                Offset(sx, sy),
                Offset(cx + (r * 0.2f * cos(sA)).toFloat(), cy + (r * 0.2f * sin(sA)).toFloat()),
                0.8f.dp.toPx()
            )
        }
    }

    for (arc in 0 until 4) {
        val arcA = Math.toRadians((a.fastRot + arc * 90f).toDouble())
        val startR = r * 0.12f
        val endR = r * 0.5f
        val segments = 5
        var prevX = cx + (startR * cos(arcA)).toFloat()
        var prevY = cy + (startR * sin(arcA)).toFloat()
        for (s in 1..segments) {
            val t = s.toFloat() / segments
            val segR = startR + (endR - startR) * t
            val jitter = sin((a.wave * 0.02 + arc * 2 + s * 1.5).toDouble()).toFloat() * r * 0.05f
            val nx = cx + (segR * cos(arcA + jitter * 0.05)).toFloat() + jitter
            val ny = cy + (segR * sin(arcA + jitter * 0.05)).toFloat()
            drawLine(col.copy(alpha = 0.1f * a.pulse), Offset(prevX, prevY), Offset(nx, ny), 1.dp.toPx())
            prevX = nx; prevY = ny
        }
    }

    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.15f * a.pulse), Color.Transparent), Offset(cx, cy), r * 0.18f), r * 0.18f, Offset(cx, cy))
}

// ═══════════════════════════════════════════════════════════════
// 11. AURORA — Wavy Aurora Curtains + Shooting Star Shower (Concept 11 & 14 combined)
// ═══════════════════════════════════════════════════════════════
fun DrawScope.drawAuroraCurtains(cx: Float, cy: Float, r: Float, a: GaugeAnimParams, col: Color) {
    val colors = listOf(
        Color(0xFF1DE9B6), // teal
        Color(0xFF7C4DFF), // purple
        Color(0xFFE040FB), // magenta
        Color(0xFF00E5FF), // cyan
        Color(0xFF69F0AE)  // green
    )

    for (i in 0 until 15) {
        val sx = cx + ((i * 47 % 100) - 50) / 50f * r * 0.75f
        val sy = cy + ((i * 31 % 100) - 50) / 50f * r * 0.75f
        val twinkle = (sin(a.wave * 0.015 + i) * 0.5 + 0.5).toFloat()
        drawCircle(Color.White.copy(alpha = twinkle * 0.18f), 0.8f.dp.toPx(), Offset(sx, sy))
    }

    for (band in 0 until 5) {
        val bandColor = colors[band]
        val path = Path()
        var started = false
        val baseY = cy - r * 0.38f + band * r * 0.16f
        val phaseOffset = band * 1.2f
        for (x in -25..25) {
            val px = cx + x * r * 0.035f
            val wave1 = sin((x * 0.14 + a.wave * 0.028 + phaseOffset).toDouble()).toFloat() * r * 0.075f
            val wave2 = sin((x * 0.08 + a.slowRot * 0.008 + band * 0.8).toDouble()).toFloat() * r * 0.045f
            val py = baseY + wave1 + wave2
            val dist = sqrt((px - cx).pow(2) + (py - cy).pow(2))
            if (dist < r * 0.85f) {
                if (!started) { path.moveTo(px, py); started = true }
                else path.lineTo(px, py)
            }
        }
        drawPath(path, bandColor.copy(alpha = 0.08f * a.pulse), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        drawPath(path, bandColor.copy(alpha = 0.03f), style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
    }

    for (i in 0 until 2) {
        val progress = (a.drift * 1.3f + i * 0.5f) % 1f
        val startX = cx - r * 0.6f + progress * r * 1.2f
        val startY = cy - r * 0.5f + progress * r * 0.8f
        val starAlpha = (1f - abs(progress - 0.5f) * 2f).coerceIn(0f, 1f) * 0.22f
        drawLine(
            Color.White.copy(alpha = starAlpha),
            Offset(startX, startY),
            Offset(startX - 15.dp.toPx(), startY - 7.dp.toPx()),
            1.dp.toPx()
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// 12. FERRARI — Perspective Checkered Flag + Rising Race Sparks (Concept 12 & 15 combined)
// ═══════════════════════════════════════════════════════════════
fun DrawScope.drawFerrariCheckered(cx: Float, cy: Float, r: Float, a: GaugeAnimParams, col: Color) {
    val tileSize = r * 0.09f
    for (row in -6..6) {
        for (c in -6..6) {
            val tx = cx + c * tileSize
            val wave = sin((c * 0.35 + a.wave * 0.035).toDouble()).toFloat() * r * 0.018f
            val ty = cy + row * tileSize + wave
            val dist = sqrt((tx - cx).pow(2) + (ty - cy).pow(2))
            if (dist < r * 0.75f && (row + c) % 2 == 0) {
                drawRect(
                    Color.White.copy(alpha = 0.035f),
                    topLeft = Offset(tx - tileSize / 2, ty - tileSize / 2),
                    size = Size(tileSize, tileSize)
                )
            }
        }
    }

    for (i in 0 until 20) {
        val progress = (a.drift + i * 0.05f) % 1f
        val sparkX = cx + ((i * 57 % 100) - 50) / 50f * r * 0.6f + sin((progress * 4 + i).toDouble()).toFloat() * r * 0.06f
        val sparkY = cy + r * 0.75f - progress * r * 1.35f
        val dist = sqrt((sparkX - cx).pow(2) + (sparkY - cy).pow(2))
        if (dist < r * 0.85f) {
            val sparkAlpha = (1f - progress) * 0.25f
            val sparkColor = if (i % 2 == 0) Color(0xFFFFCC00) else col
            drawCircle(sparkColor.copy(alpha = sparkAlpha), (1.6f - progress).dp.toPx(), Offset(sparkX, sparkY))
            if (i % 5 == 0) {
                drawCircle(Color.White.copy(alpha = sparkAlpha * 0.6f), (0.8f - progress * 0.4f).dp.toPx(), Offset(sparkX, sparkY))
            }
        }
    }

    drawCircle(col.copy(alpha = 0.04f * a.pulse), r * 0.12f, Offset(cx, cy))
}

// ═══════════════════════════════════════════════════════════════
// 13. TOKYO — Neon Rain + Floating Bokeh + Connected Constellations (Concept 13 & 15 & 19 combined)
// ═══════════════════════════════════════════════════════════════
fun DrawScope.drawTokyoRain(cx: Float, cy: Float, r: Float, a: GaugeAnimParams, col: Color) {
    val pink = Color(0xFFFF4081)
    val cyan = Color(0xFF00E5FF)

    val bokehList = List(8) { idx ->
        val bx = cx + ((idx * 71 % 100) - 50) / 50f * r * 0.58f
        val by = cy + ((idx * 47 % 80) - 40) / 40f * r * 0.45f
        val size = (5 + idx % 3) * 1.2f
        val color = when (idx % 3) { 0 -> pink; 1 -> cyan; else -> Color(0xFFFFD54F) }
        val bPulse = (sin(a.wave * 0.015 + idx * 1.3) * 0.5 + 0.5).toFloat()
        Pair(Offset(bx, by), color.copy(alpha = 0.035f * bPulse) to size)
    }
    bokehList.forEach { (pos, data) ->
        val dist = sqrt((pos.x - cx).pow(2) + (pos.y - cy).pow(2))
        if (dist < r * 0.72f) {
            drawCircle(data.first, data.second.dp.toPx(), pos)
            drawCircle(data.first.copy(alpha = data.first.alpha * 0.5f), data.second.dp.toPx() * 1.8f, pos)
        }
    }

    for (i in 0 until bokehList.size) {
        val p1 = bokehList[i].first
        val nextIdx = (i + 2) % bokehList.size
        val p2 = bokehList[nextIdx].first
        val dist1 = sqrt((p1.x - cx).pow(2) + (p1.y - cy).pow(2))
        val dist2 = sqrt((p2.x - cx).pow(2) + (p2.y - cy).pow(2))
        if (dist1 < r * 0.7f && dist2 < r * 0.7f) {
            drawLine(
                Color.White.copy(alpha = 0.03f * a.pulse),
                p1, p2,
                0.8f.dp.toPx()
            )
        }
    }

    for (i in 0 until 20) {
        val baseX = cx + ((i * 59 % 100) - 50) / 50f * r * 0.8f
        val progress = (a.drift * (1f + i % 3 * 0.25f) + i * 0.05f) % 1f
        val dropY = cy - r * 0.78f + progress * r * 1.56f
        val dist = sqrt((baseX - cx).pow(2) + (dropY - cy).pow(2))
        if (dist < r * 0.85f) {
            val dropColor = if (i % 2 == 0) pink else cyan
            val dropAlpha = (1f - abs(progress - 0.5f) * 2f) * 0.16f
            drawLine(
                dropColor.copy(alpha = dropAlpha),
                Offset(baseX, dropY),
                Offset(baseX, dropY - 8.dp.toPx()),
                1.dp.toPx()
            )
            drawCircle(dropColor.copy(alpha = dropAlpha * 1.4f), 1.dp.toPx(), Offset(baseX, dropY))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 14. MILITARY — Sonar Waves + Sweeping Radar Grid + Targets (Concept 14 & 18 combined)
// ═══════════════════════════════════════════════════════════════
fun DrawScope.drawMilitaryRadar(cx: Float, cy: Float, r: Float, a: GaugeAnimParams, col: Color) {
    drawLine(col.copy(alpha = 0.04f), Offset(cx, cy - r * 0.7f), Offset(cx, cy + r * 0.7f), 0.5f.dp.toPx())
    drawLine(col.copy(alpha = 0.04f), Offset(cx - r * 0.7f, cy), Offset(cx + r * 0.7f, cy), 0.5f.dp.toPx())

    for (ring in 1..4) {
        val ringR = ring * r * 0.18f
        drawCircle(col.copy(alpha = 0.03f), ringR, Offset(cx, cy), style = Stroke(0.5f.dp.toPx()))
    }

    for (w in 0 until 2) {
        val wProgress = (a.drift + w * 0.5f) % 1f
        val rippleR = wProgress * r * 0.75f
        val wAlpha = (1f - wProgress) * 0.06f * a.pulse
        drawCircle(
            col.copy(alpha = wAlpha),
            rippleR,
            Offset(cx, cy),
            style = Stroke(1.dp.toPx())
        )
    }

    val sweepA = Math.toRadians(a.fastRot.toDouble())
    val sweepEnd = Offset(
        cx + (r * 0.7f * cos(sweepA)).toFloat(),
        cy + (r * 0.7f * sin(sweepA)).toFloat()
    )
    drawLine(col.copy(alpha = 0.22f), Offset(cx, cy), sweepEnd, 1.5f.dp.toPx())

    for (trail in 1..18) {
        val trailA = a.fastRot - trail * 2.5f
        val trailRad = Math.toRadians(trailA.toDouble())
        val trailEnd = Offset(
            cx + (r * 0.7f * cos(trailRad)).toFloat(),
            cy + (r * 0.7f * sin(trailRad)).toFloat()
        )
        drawLine(col.copy(alpha = 0.14f * (1f - trail / 18f)), Offset(cx, cy), trailEnd, 0.5f.dp.toPx())
    }

    for (i in 0 until 6) {
        val blipA = (i * 60f + 15f) % 360f
        val blipD = r * (0.22f + (i * 29 % 45) / 100f)
        val blipRad = Math.toRadians(blipA.toDouble())
        val bx = cx + (blipD * cos(blipRad)).toFloat()
        val by = cy + (blipD * sin(blipRad)).toFloat()
        
        val angleDiff = ((a.fastRot - blipA) % 360f + 360f) % 360f
        if (angleDiff < 90f) {
            val blipAlpha = (1f - angleDiff / 90f) * 0.3f
            drawCircle(col.copy(alpha = blipAlpha), 2.5f.dp.toPx(), Offset(bx, by))
            drawCircle(col.copy(alpha = blipAlpha * 0.3f), 6.dp.toPx(), Offset(bx, by), style = Stroke(0.5f.dp.toPx()))
            
            val tick = 2.dp.toPx()
            drawLine(col.copy(alpha = blipAlpha * 0.6f), Offset(bx - tick, by), Offset(bx + tick, by), 0.5f.dp.toPx())
            drawLine(col.copy(alpha = blipAlpha * 0.6f), Offset(bx, by - tick), Offset(bx, by + tick), 0.5f.dp.toPx())
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// 15. DIAMOND — Faceted Prism + Concentric Kaleidoscope Fractals (Concept 15 & 18 combined)
// ═══════════════════════════════════════════════════════════════
fun DrawScope.drawDiamondPrism(cx: Float, cy: Float, r: Float, a: GaugeAnimParams, col: Color) {
    val prismColors = listOf(
        Color(0xFFFF1744), Color(0xFFFF9100), Color(0xFFFFEA00),
        Color(0xFF00E676), Color(0xFF00B0FF), Color(0xFF651FFF), Color(0xFFD500F9)
    )

    val kRot = a.slowRot * 0.3f
    for (k in 0 until 2) {
        val kRadius = r * (0.22f + k * 0.28f)
        val kAlpha = 0.035f * a.pulse / (k + 1)
        val dir = if (k % 2 == 0) 1 else -1
        val radOffset = Math.toRadians((kRot * dir).toDouble())
        
        val path = Path()
        var started = false
        val corners = 8
        for (i in 0..corners) {
            val theta = radOffset + i * (2 * PI / corners)
            val px = cx + (kRadius * cos(theta)).toFloat()
            val py = cy + (kRadius * sin(theta)).toFloat()
            if (!started) { path.moveTo(px, py); started = true }
            else path.lineTo(px, py)
        }
        path.close()
        drawPath(path, col.copy(alpha = kAlpha), style = Stroke(0.8f.dp.toPx()))
    }

    for (i in prismColors.indices) {
        val rayA = Math.toRadians((a.slowRot * 0.8f + i * 51.4f).toDouble())
        val rayEndR = r * 0.72f
        val ray2A = rayA + 0.02
        drawLine(
            prismColors[i].copy(alpha = 0.05f * a.pulse),
            Offset(cx + (r * 0.05f * cos(rayA)).toFloat(), cy + (r * 0.05f * sin(rayA)).toFloat()),
            Offset(cx + (rayEndR * cos(rayA)).toFloat(), cy + (rayEndR * sin(rayA)).toFloat()),
            1.5f.dp.toPx()
        )
        drawLine(
            prismColors[i].copy(alpha = 0.03f),
            Offset(cx + (r * 0.05f * cos(ray2A)).toFloat(), cy + (r * 0.05f * sin(ray2A)).toFloat()),
            Offset(cx + (rayEndR * cos(ray2A)).toFloat(), cy + (rayEndR * sin(ray2A)).toFloat()),
            1.dp.toPx()
        )
    }

    for (i in 0 until 15) {
        val golden = 137.508f
        val sA = Math.toRadians((i * golden + a.medRot * 0.3f).toDouble())
        val sD = r * (0.2f + (i * 31 % 60) / 100f)
        val sx = cx + (sD * cos(sA)).toFloat()
        val sy = cy + (sD * sin(sA)).toFloat()
        val twinkle = (sin(a.wave * 0.0175 + i * 2.1) * 0.5 + 0.5).toFloat()
        if (twinkle > 0.62f) {
            val sSize = 4.dp.toPx() * twinkle
            drawLine(Color.White.copy(alpha = twinkle * 0.2f), Offset(sx - sSize, sy), Offset(sx + sSize, sy), 0.5f.dp.toPx())
            drawLine(Color.White.copy(alpha = twinkle * 0.2f), Offset(sx, sy - sSize), Offset(sx, sy + sSize), 0.5f.dp.toPx())
            drawCircle(Color.White.copy(alpha = twinkle * 0.15f), 1.dp.toPx(), Offset(sx, sy))
        }
    }

    for (i in 0 until 6) {
        val fA = Math.toRadians((i * 60f + a.slowRot * 0.2f).toDouble())
        val fA2 = Math.toRadians(((i + 1) * 60f + a.slowRot * 0.2f).toDouble())
        val fR = r * 0.38f
        drawLine(
            col.copy(alpha = 0.05f),
            Offset(cx + (fR * cos(fA)).toFloat(), cy + (fR * sin(fA)).toFloat()),
            Offset(cx + (fR * cos(fA2)).toFloat(), cy + (fR * sin(fA2)).toFloat()),
            0.8f.dp.toPx()
        )
        drawLine(
            col.copy(alpha = 0.03f),
            Offset(cx, cy),
            Offset(cx + (fR * cos(fA)).toFloat(), cy + (fR * sin(fA)).toFloat()),
            0.5f.dp.toPx()
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// 16. COCKPIT — Gyro Horizon + Magnetic Field Flow (Concept 16 & 12 combined)
// ═══════════════════════════════════════════════════════════════
fun DrawScope.drawCockpitHorizon(cx: Float, cy: Float, r: Float, a: GaugeAnimParams, col: Color) {
    val amber = Color(0xFFFFAB00)
    val sky = Color(0xFF1565C0)

    val bankAngle = sin(a.slowRot * 0.0175).toFloat() * 12f
    val pitchOffset = sin(a.wave * 0.0175 * 0.5).toFloat() * r * 0.08f
    val bankRad = Math.toRadians(bankAngle.toDouble())

    val horizY = cy + pitchOffset
    drawCircle(
        Brush.verticalGradient(
            listOf(sky.copy(alpha = 0.04f), Color.Transparent),
            startY = cy - r * 0.7f, endY = horizY
        ),
        r * 0.7f, Offset(cx, cy)
    )

    val hLen = r * 0.6f
    val hx1 = cx - hLen * cos(bankRad).toFloat()
    val hy1 = horizY + hLen * sin(bankRad).toFloat()
    val hx2 = cx + hLen * cos(bankRad).toFloat()
    val hy2 = horizY - hLen * sin(bankRad).toFloat()
    drawLine(amber.copy(alpha = 0.15f), Offset(hx1, hy1), Offset(hx2, hy2), 1.5f.dp.toPx())

    for (pitch in listOf(-20f, -10f, 10f, 20f)) {
        val py = horizY - pitch * r * 0.015f
        val pw = r * (0.15f - abs(pitch) * 0.003f)
        drawLine(amber.copy(alpha = 0.06f), Offset(cx - pw, py), Offset(cx + pw, py), 0.5f.dp.toPx())
    }

    for (mark in listOf(-45f, -30f, -15f, 0f, 15f, 30f, 45f)) {
        val mA = Math.toRadians((-90f + mark).toDouble())
        val mR1 = r * 0.55f
        val mR2 = r * 0.6f
        drawLine(amber.copy(alpha = if (mark == 0f) 0.12f else 0.05f),
            Offset(cx + (mR1 * cos(mA)).toFloat(), cy + (mR1 * sin(mA)).toFloat()),
            Offset(cx + (mR2 * cos(mA)).toFloat(), cy + (mR2 * sin(mA)).toFloat()),
            if (mark == 0f) 1.5f.dp.toPx() else 0.8f.dp.toPx())
    }

    drawLine(amber.copy(alpha = 0.1f), Offset(cx - r * 0.15f, cy), Offset(cx - r * 0.04f, cy), 1.5f.dp.toPx())
    drawLine(amber.copy(alpha = 0.1f), Offset(cx + r * 0.04f, cy), Offset(cx + r * 0.15f, cy), 1.5f.dp.toPx())
    drawCircle(amber.copy(alpha = 0.12f), 2.dp.toPx(), Offset(cx, cy))

    for (side in listOf(-1, 1)) {
        val path = Path()
        var started = false
        for (i in 0..20) {
            val progress = i / 20f
            val angle = Math.toRadians((-90f + progress * 180f * side).toDouble())
            val pathR = r * (0.4f + sin(progress * PI).toFloat() * 0.35f)
            val px = cx + (pathR * cos(angle)).toFloat()
            val py = cy + (pathR * sin(angle)).toFloat()
            
            val dist = sqrt((px - cx).pow(2) + (py - cy).pow(2))
            if (dist < r * 0.82f) {
                if (!started) { path.moveTo(px, py); started = true }
                else path.lineTo(px, py)
            }
        }
        drawPath(path, amber.copy(alpha = 0.035f * a.pulse), style = Stroke(0.8f.dp.toPx()))
    }
}
