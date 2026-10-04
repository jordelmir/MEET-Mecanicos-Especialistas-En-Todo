package com.elysium369.meet.core.agentstore.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.*

/**
 * ══════════════════════════════════════════════════════════════════════
 *  A G E N T   3 D   A V A T A R   C A N V A S  ( L I V I N G   3 D )
 *  ──────────────────────────────────────────────────────────────
 *  Renderizador de Personajes 3D/Pseudo-volumétricos vivos:
 *  - 🐉 Dragón Carmesí Místico (Draco Ignis): Cuernos 3D, alas, ojos flamígeros, llamas.
 *  - ⚡ Espíritu Voltaico (Volt Aether): Orejas cinemáticas, núcleo electrodinámico y chispa continua.
 *  - 🛡️ Titan Vanguard (Guardián Primordial): Aura cinemática de impacto, armadura carmesí y óptica dorada.
 *  - 🤖 Cyber Mecha Titan: Casco acorazado con visor LED y hombreras blindadas.
 *  - 🌌 Laya Valquiria Celestial: Halo holográfico, cabello fluido y tiara cuántica.
 *  - ✨ EVAIR Living Spirit: Espíritu guía con rostro expresivo y alitas de plasma.
 * ══════════════════════════════════════════════════════════════════════
 */
@Composable
fun Agent3dAvatarCanvas(
    avatarVisualType: String,
    themeColor: Color,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    isPulsing: Boolean = true,
) {
    var azimuthDeg by remember { mutableFloatStateOf(0f) }
    var elevationDeg by remember { mutableFloatStateOf(10f) }

    val infiniteTransition = rememberInfiniteTransition(label = "Agent3dAnimation")

    val idleBobbing by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleBobbing"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val autoSway by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "autoSway"
    )

    val currentAzimuth = azimuthDeg + (if (isInteractive) 0f else autoSway)

    Box(
        modifier = modifier
            .then(
                if (isInteractive) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            azimuthDeg = (azimuthDeg + dragAmount.x * 0.4f).coerceIn(-60f, 60f)
                            elevationDeg = (elevationDeg - dragAmount.y * 0.3f).coerceIn(-30f, 30f)
                        }
                    }
                } else Modifier
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f + idleBobbing)
            val baseRadius = min(size.width, size.height) * 0.38f
            val dynamicRadius = if (isPulsing) baseRadius * pulseScale else baseRadius

            val azimuthRad = Math.toRadians(currentAzimuth.toDouble()).toFloat()
            val elevationRad = Math.toRadians(elevationDeg.toDouble()).toFloat()

            // 1. Energetic Ambient Aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        themeColor.copy(alpha = 0.38f),
                        themeColor.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = dynamicRadius * 1.55f
                ),
                radius = dynamicRadius * 1.55f,
                center = center
            )

            // 2. Character Archetype Renderer
            when (avatarVisualType.uppercase()) {
                "DRAGON", "DRACO", "DRAGON_IGNIS" -> {
                    drawDragonCharacter(center, dynamicRadius, themeColor, azimuthRad, elevationRad, wavePhase)
                }
                "VOLT_AETHER", "VOLT", "SPARKY" -> {
                    drawVoltAetherCharacter(center, dynamicRadius, themeColor, azimuthRad, elevationRad, wavePhase)
                }
                "TITAN_VANGUARD", "TITAN", "VANGUARD_SENTINEL", "TACTICAL_SHIELD" -> {
                    drawTitanVanguardCharacter(center, dynamicRadius, themeColor, azimuthRad, elevationRad, wavePhase)
                }
                "CYBER_MECHA", "TITAN", "TITAN_EXOSKELETON", "MECHA" -> {
                    drawCyberMechaCharacter(center, dynamicRadius, themeColor, azimuthRad, elevationRad, wavePhase)
                }
                "LAYA_VALKYRIE", "VALKYRIE", "METROLOGY_PRISM" -> {
                    drawLayaValkyrieCharacter(center, dynamicRadius, themeColor, azimuthRad, elevationRad, wavePhase)
                }
                else -> {
                    // Default: EVAIR living spirit
                    drawEvairSpiritCharacter(center, dynamicRadius, themeColor, azimuthRad, elevationRad, wavePhase)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// 1. DRAGÓN CARMESÍ MÍSTICO (DRACO IGNIS)
// ══════════════════════════════════════════════════════════════════════
private fun DrawScope.drawDragonCharacter(
    center: Offset,
    radius: Float,
    themeColor: Color,
    azimuthRad: Float,
    elevationRad: Float,
    wavePhase: Float,
) {
    val headOffset = Offset(
        center.x + sin(azimuthRad) * radius * 0.25f,
        center.y - sin(elevationRad) * radius * 0.20f
    )

    // A. Dragon Wings flapping behind
    val wingFlap = sin(wavePhase * 2.5f) * 0.2f
    val leftWing = Path().apply {
        moveTo(headOffset.x - radius * 0.2f, headOffset.y + radius * 0.1f)
        cubicTo(
            headOffset.x - radius * 0.8f, headOffset.y - radius * (0.8f + wingFlap),
            headOffset.x - radius * 1.3f, headOffset.y - radius * (0.4f + wingFlap),
            headOffset.x - radius * 0.9f, headOffset.y + radius * 0.4f
        )
        close()
    }
    val rightWing = Path().apply {
        moveTo(headOffset.x + radius * 0.2f, headOffset.y + radius * 0.1f)
        cubicTo(
            headOffset.x + radius * 0.8f, headOffset.y - radius * (0.8f + wingFlap),
            headOffset.x + radius * 1.3f, headOffset.y - radius * (0.4f + wingFlap),
            headOffset.x + radius * 0.9f, headOffset.y + radius * 0.4f
        )
        close()
    }
    drawPath(leftWing, Brush.linearGradient(listOf(Color(0xFF880015), Color(0xFFFF4500))))
    drawPath(rightWing, Brush.linearGradient(listOf(Color(0xFF880015), Color(0xFFFF4500))))
    drawPath(leftWing, Color(0xFFFFD700), style = Stroke(1.5f))
    drawPath(rightWing, Color(0xFFFFD700), style = Stroke(1.5f))

    // B. Dragon Horns (3D swept back)
    val hornSpread = cos(azimuthRad) * radius * 0.35f
    val hornTipY = headOffset.y - radius * 0.95f
    val leftHorn = Path().apply {
        moveTo(headOffset.x - radius * 0.15f, headOffset.y - radius * 0.35f)
        cubicTo(
            headOffset.x - radius * 0.4f - hornSpread, headOffset.y - radius * 0.7f,
            headOffset.x - radius * 0.5f - hornSpread, hornTipY + radius * 0.1f,
            headOffset.x - radius * 0.35f - hornSpread, hornTipY
        )
        lineTo(headOffset.x - radius * 0.05f, headOffset.y - radius * 0.4f)
        close()
    }
    val rightHorn = Path().apply {
        moveTo(headOffset.x + radius * 0.15f, headOffset.y - radius * 0.35f)
        cubicTo(
            headOffset.x + radius * 0.4f + hornSpread, headOffset.y - radius * 0.7f,
            headOffset.x + radius * 0.5f + hornSpread, hornTipY + radius * 0.1f,
            headOffset.x + radius * 0.35f + hornSpread, hornTipY
        )
        lineTo(headOffset.x + radius * 0.05f, headOffset.y - radius * 0.4f)
        close()
    }
    drawPath(leftHorn, Brush.verticalGradient(listOf(Color(0xFFFFD700), Color(0xFF4A0000))))
    drawPath(rightHorn, Brush.verticalGradient(listOf(Color(0xFFFFD700), Color(0xFF4A0000))))
    drawPath(leftHorn, Color.White.copy(alpha = 0.8f), style = Stroke(1.5f))
    drawPath(rightHorn, Color.White.copy(alpha = 0.8f), style = Stroke(1.5f))

    // C. Dragon Head & Snout
    val headPath = Path().apply {
        moveTo(headOffset.x, headOffset.y - radius * 0.45f)
        lineTo(headOffset.x + radius * 0.45f, headOffset.y - radius * 0.15f)
        lineTo(headOffset.x + radius * 0.35f, headOffset.y + radius * 0.35f)
        lineTo(headOffset.x + radius * 0.20f, headOffset.y + radius * 0.65f) // Snout right
        lineTo(headOffset.x - radius * 0.20f, headOffset.y + radius * 0.65f) // Snout left
        lineTo(headOffset.x - radius * 0.35f, headOffset.y + radius * 0.35f)
        lineTo(headOffset.x - radius * 0.45f, headOffset.y - radius * 0.15f)
        close()
    }
    drawPath(headPath, Brush.radialGradient(listOf(Color(0xFFFF3333), Color(0xFF7A0000)), headOffset, radius * 0.6f))
    drawPath(headPath, Color(0xFFFF8800), style = Stroke(2.2f))

    // D. Fierce Golden Dragon Eyes (Slit pupils)
    val eyeY = headOffset.y - radius * 0.05f
    val eyeSpread = radius * 0.22f
    // Left eye
    drawOval(Color(0xFFFFD700), Offset(headOffset.x - eyeSpread - 10f, eyeY - 8f), Size(20f, 16f))
    drawOval(Color.Black, Offset(headOffset.x - eyeSpread - 2f, eyeY - 8f), Size(4f, 16f)) // Slit
    // Right eye
    drawOval(Color(0xFFFFD700), Offset(headOffset.x + eyeSpread - 10f, eyeY - 8f), Size(20f, 16f))
    drawOval(Color.Black, Offset(headOffset.x + eyeSpread - 2f, eyeY - 8f), Size(4f, 16f)) // Slit

    // E. Snout Nostrils & Smoke
    drawCircle(Color(0xFF220000), 3.5f, Offset(headOffset.x - radius * 0.08f, headOffset.y + radius * 0.48f))
    drawCircle(Color(0xFF220000), 3.5f, Offset(headOffset.x + radius * 0.08f, headOffset.y + radius * 0.48f))

    // F. Rising Ember / Fire Particles
    for (i in 0 until 5) {
        val emberPhase = wavePhase + i * 1.3f
        val emberX = headOffset.x + sin(emberPhase) * radius * 0.7f
        val emberY = headOffset.y + radius * 0.5f - (emberPhase % (2 * PI.toFloat())) * radius * 0.5f
        drawCircle(Color(0xFFFF8800), 3f + (i % 3), Offset(emberX, emberY))
        drawCircle(Color.White, 1.5f, Offset(emberX, emberY))
    }
}

// ══════════════════════════════════════════════════════════════════════
// 2. ESPÍRITU VOLTAICO (VOLT AETHER) — Diseño Propietario Elysium
//    Criatura eléctrica viva: sprite de plasma con antenas de pararrayos,
//    cuerpo teal redondeado, ojos expresivos, y brazos diminutos.
//    Rodeado por su PODER: anillos orbitales de electrones, tentáculos
//    de plasma, escudo hexagonal y descargas de rayos.
// ══════════════════════════════════════════════════════════════════════
private fun DrawScope.drawVoltAetherCharacter(
    center: Offset,
    radius: Float,
    themeColor: Color,
    azimuthRad: Float,
    elevationRad: Float,
    wavePhase: Float,
) {
    val bodyCenter = Offset(
        center.x + sin(azimuthRad) * radius * 0.15f,
        center.y - sin(elevationRad) * radius * 0.12f
    )

    // ═══════════════════════════════════════════════════════
    // CAPA 1: PODER ENVOLVENTE (aura, anillos, plasma)
    // ═══════════════════════════════════════════════════════

    // A. Plasma Tendrils radiating from the creature (organic energy veins)
    for (i in 0 until 6) {
        val baseAngle = (i.toFloat() / 6f) * 2f * PI.toFloat() + wavePhase * 0.3f
        val tendrilWave = sin(wavePhase * 2.5f + i * 1.1f) * radius * 0.10f
        val tendrilPath = Path().apply {
            val startX = bodyCenter.x + cos(baseAngle) * radius * 0.40f
            val startY = bodyCenter.y + sin(baseAngle) * radius * 0.35f
            val endX = bodyCenter.x + cos(baseAngle) * radius * (0.92f + sin(wavePhase + i) * 0.12f)
            val endY = bodyCenter.y + sin(baseAngle) * radius * (0.78f + sin(wavePhase + i) * 0.10f)
            val ctrlX = bodyCenter.x + cos(baseAngle + 0.25f) * radius * 0.62f + tendrilWave
            val ctrlY = bodyCenter.y + sin(baseAngle + 0.25f) * radius * 0.52f + tendrilWave
            moveTo(startX, startY)
            quadraticTo(ctrlX, ctrlY, endX, endY)
        }
        val tendrilAlpha = 0.35f + 0.25f * sin(wavePhase * 3f + i * 0.8f)
        drawPath(tendrilPath, Color(0xFF00E5FF).copy(alpha = tendrilAlpha), style = Stroke(width = 2f + sin(wavePhase + i) * 0.6f))
        // Energy nodes at tips
        val tipDist = radius * (0.92f + sin(wavePhase + i) * 0.12f)
        val tipX = bodyCenter.x + cos(baseAngle) * tipDist
        val tipY = bodyCenter.y + sin(baseAngle) * tipDist * 0.85f
        drawCircle(Color(0xFFFFD700), 3f + sin(wavePhase * 4f + i) * 1.2f, Offset(tipX, tipY))
    }

    // B. Hexagonal Energy Shield (slowly rotating around creature)
    val hexRotation = wavePhase * 0.4f
    val hexPath = Path().apply {
        for (i in 0..5) {
            val angle = hexRotation + (i.toFloat() / 6f) * 2f * PI.toFloat()
            val hx = bodyCenter.x + cos(angle) * radius * 0.80f
            val hy = bodyCenter.y + sin(angle) * radius * 0.68f
            if (i == 0) moveTo(hx, hy) else lineTo(hx, hy)
        }
        close()
    }
    drawPath(hexPath, Color(0xFF00E5FF).copy(alpha = 0.12f + 0.08f * sin(wavePhase * 2f)))
    drawPath(hexPath, Color(0xFF00E5FF).copy(alpha = 0.40f), style = Stroke(
        width = 1.5f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f), phase = wavePhase * 18f)
    ))

    // C. Orbiting Electron Rings (3 tilted ellipses orbiting the body)
    for (ringIdx in 0..2) {
        val ringTilt = ringIdx * 60f
        val ringPhase = wavePhase * (1.5f + ringIdx * 0.4f)
        val electronCount = 6
        for (e in 0 until electronCount) {
            val eAngle = ringPhase + (e.toFloat() / electronCount) * 2f * PI.toFloat()
            val orbitRx = radius * 0.55f
            val orbitRy = radius * 0.16f
            val tiltRad = Math.toRadians(ringTilt.toDouble()).toFloat()
            val rawX = cos(eAngle) * orbitRx
            val rawY = sin(eAngle) * orbitRy
            val ex = bodyCenter.x + rawX * cos(tiltRad) - rawY * sin(tiltRad)
            val ey = bodyCenter.y + rawX * sin(tiltRad) + rawY * cos(tiltRad)
            val dotSize = 1.8f + 1.2f * (0.5f + 0.5f * sin(eAngle))
            val ringColor = when (ringIdx) {
                0 -> Color(0xFF00E5FF)
                1 -> Color(0xFFFFD700)
                else -> Color(0xFF76FF03)
            }
            drawCircle(ringColor.copy(alpha = 0.5f + 0.3f * sin(eAngle)), dotSize, Offset(ex, ey))
        }
    }

    // D. Lightning Arc Discharges crackling outward from body
    if (sin(wavePhase * 5f) > 0.2f) {
        val arcAngle = wavePhase * 2f
        val arcPath = Path().apply {
            val sx = bodyCenter.x + cos(arcAngle) * radius * 0.40f
            val sy = bodyCenter.y + sin(arcAngle) * radius * 0.35f
            moveTo(sx, sy)
            lineTo(sx + 14f, sy - 10f)
            lineTo(sx + 7f, sy - 20f)
            lineTo(sx + 20f, sy - 32f)
        }
        drawPath(arcPath, Color(0xFF00E5FF), style = Stroke(2f))
    }
    if (sin(wavePhase * 4f + 1.5f) > 0.3f) {
        val arcAngle2 = wavePhase * 1.7f + PI.toFloat()
        val arcPath2 = Path().apply {
            val sx = bodyCenter.x + cos(arcAngle2) * radius * 0.40f
            val sy = bodyCenter.y + sin(arcAngle2) * radius * 0.35f
            moveTo(sx, sy)
            lineTo(sx - 12f, sy + 8f)
            lineTo(sx - 6f, sy + 20f)
            lineTo(sx - 18f, sy + 30f)
        }
        drawPath(arcPath2, Color(0xFFFFD700), style = Stroke(1.6f))
    }

    // ═══════════════════════════════════════════════════════
    // CAPA 2: EL SER VIVO (criatura eléctrica Volt)
    // ═══════════════════════════════════════════════════════

    // E. Body Glow (inner aura, soft halo around creature)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF00E5FF).copy(alpha = 0.22f),
                Color(0xFF0091EA).copy(alpha = 0.06f),
                Color.Transparent
            ),
            center = bodyCenter,
            radius = radius * 0.52f
        ),
        radius = radius * 0.52f,
        center = bodyCenter
    )

    // F. Lightning-Rod Antennae (two asymmetric conductors on top)
    val antennaWiggle = sin(wavePhase * 3f) * 0.05f
    // Left antenna — taller, bends left
    val leftAntenna = Path().apply {
        moveTo(bodyCenter.x - radius * 0.12f, bodyCenter.y - radius * 0.30f)
        cubicTo(
            bodyCenter.x - radius * 0.18f, bodyCenter.y - radius * (0.55f + antennaWiggle),
            bodyCenter.x - radius * 0.28f, bodyCenter.y - radius * (0.72f + antennaWiggle),
            bodyCenter.x - radius * 0.22f, bodyCenter.y - radius * (0.82f + antennaWiggle)
        )
    }
    drawPath(leftAntenna, Color(0xFF00BCD4), style = Stroke(width = 3.5f))
    // Antenna tip spark ball
    drawCircle(
        brush = Brush.radialGradient(listOf(Color.White, Color(0xFF00E5FF), Color.Transparent)),
        radius = radius * 0.06f + sin(wavePhase * 6f) * radius * 0.02f,
        center = Offset(bodyCenter.x - radius * 0.22f, bodyCenter.y - radius * (0.82f + antennaWiggle))
    )
    // Right antenna — shorter, bends right
    val rightAntenna = Path().apply {
        moveTo(bodyCenter.x + radius * 0.10f, bodyCenter.y - radius * 0.30f)
        cubicTo(
            bodyCenter.x + radius * 0.16f, bodyCenter.y - radius * (0.48f - antennaWiggle),
            bodyCenter.x + radius * 0.25f, bodyCenter.y - radius * (0.58f - antennaWiggle),
            bodyCenter.x + radius * 0.20f, bodyCenter.y - radius * (0.65f - antennaWiggle)
        )
    }
    drawPath(rightAntenna, Color(0xFF00BCD4), style = Stroke(width = 3f))
    drawCircle(
        brush = Brush.radialGradient(listOf(Color.White, Color(0xFFFFD700), Color.Transparent)),
        radius = radius * 0.05f + sin(wavePhase * 5f + 1f) * radius * 0.015f,
        center = Offset(bodyCenter.x + radius * 0.20f, bodyCenter.y - radius * (0.65f - antennaWiggle))
    )

    // G. Main Body — rounded teal sprite (NOT yellow, NOT round face like Pikachu)
    //    Slightly pear-shaped: wider bottom, narrower top = unique silhouette
    val bodyPath = Path().apply {
        // Top of head
        moveTo(bodyCenter.x, bodyCenter.y - radius * 0.33f)
        // Right side of head curving into wider body
        cubicTo(
            bodyCenter.x + radius * 0.28f, bodyCenter.y - radius * 0.33f,
            bodyCenter.x + radius * 0.35f, bodyCenter.y - radius * 0.15f,
            bodyCenter.x + radius * 0.38f, bodyCenter.y + radius * 0.05f
        )
        // Right side belly (wider)
        cubicTo(
            bodyCenter.x + radius * 0.40f, bodyCenter.y + radius * 0.22f,
            bodyCenter.x + radius * 0.32f, bodyCenter.y + radius * 0.38f,
            bodyCenter.x, bodyCenter.y + radius * 0.42f
        )
        // Left side belly
        cubicTo(
            bodyCenter.x - radius * 0.32f, bodyCenter.y + radius * 0.38f,
            bodyCenter.x - radius * 0.40f, bodyCenter.y + radius * 0.22f,
            bodyCenter.x - radius * 0.38f, bodyCenter.y + radius * 0.05f
        )
        // Left side of head
        cubicTo(
            bodyCenter.x - radius * 0.35f, bodyCenter.y - radius * 0.15f,
            bodyCenter.x - radius * 0.28f, bodyCenter.y - radius * 0.33f,
            bodyCenter.x, bodyCenter.y - radius * 0.33f
        )
        close()
    }
    // Body fill: teal gradient (Elysium brand, NOT yellow)
    drawPath(bodyPath, Brush.radialGradient(
        colors = listOf(Color(0xFF4DD0E1), Color(0xFF00ACC1), Color(0xFF00838F)),
        center = Offset(bodyCenter.x - radius * 0.05f, bodyCenter.y - radius * 0.08f),
        radius = radius * 0.45f
    ))
    // Body outline
    drawPath(bodyPath, Color(0xFF006064), style = Stroke(2.2f))

    // H. Chest Energy Core — small glowing circle in the chest (like an arc reactor)
    val coreY = bodyCenter.y + radius * 0.08f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White, Color(0xFF00E5FF), Color(0xFF0091EA).copy(alpha = 0.3f)),
            center = Offset(bodyCenter.x, coreY),
            radius = radius * 0.10f
        ),
        radius = radius * 0.10f,
        center = Offset(bodyCenter.x, coreY)
    )
    drawCircle(Color(0xFF00E5FF), radius * 0.10f, Offset(bodyCenter.x, coreY), style = Stroke(1.2f))

    // I. Eyes — big expressive round eyes with electric-blue irises (NOT anime Pikachu style)
    val eyeY = bodyCenter.y - radius * 0.10f
    val eyeSpread = radius * 0.17f
    val eyeRadius = radius * 0.11f
    // Left eye — white sclera
    drawCircle(Color.White, eyeRadius, Offset(bodyCenter.x - eyeSpread, eyeY))
    // Left iris — electric blue
    drawCircle(Color(0xFF0091EA), eyeRadius * 0.65f, Offset(bodyCenter.x - eyeSpread + sin(azimuthRad) * 2f, eyeY))
    // Left pupil
    drawCircle(Color(0xFF0D1B2A), eyeRadius * 0.32f, Offset(bodyCenter.x - eyeSpread + sin(azimuthRad) * 2f, eyeY))
    // Left specular
    drawCircle(Color.White.copy(alpha = 0.9f), eyeRadius * 0.18f, Offset(bodyCenter.x - eyeSpread - 1.5f, eyeY - 2f))

    // Right eye
    drawCircle(Color.White, eyeRadius, Offset(bodyCenter.x + eyeSpread, eyeY))
    drawCircle(Color(0xFF0091EA), eyeRadius * 0.65f, Offset(bodyCenter.x + eyeSpread + sin(azimuthRad) * 2f, eyeY))
    drawCircle(Color(0xFF0D1B2A), eyeRadius * 0.32f, Offset(bodyCenter.x + eyeSpread + sin(azimuthRad) * 2f, eyeY))
    drawCircle(Color.White.copy(alpha = 0.9f), eyeRadius * 0.18f, Offset(bodyCenter.x + eyeSpread - 1.5f, eyeY - 2f))

    // J. Happy Mouth — simple arc smile
    val smilePath = Path().apply {
        moveTo(bodyCenter.x - radius * 0.08f, bodyCenter.y + radius * 0.08f)
        quadraticTo(bodyCenter.x, bodyCenter.y + radius * 0.16f, bodyCenter.x + radius * 0.08f, bodyCenter.y + radius * 0.08f)
    }
    drawPath(smilePath, Color(0xFF004D40), style = Stroke(2f))

    // K. Tiny Arms/Hands — stubby little appendages waving
    val armWave = sin(wavePhase * 2.5f) * 0.08f
    // Left arm
    val leftArm = Path().apply {
        moveTo(bodyCenter.x - radius * 0.34f, bodyCenter.y + radius * 0.05f)
        cubicTo(
            bodyCenter.x - radius * 0.48f, bodyCenter.y + radius * (0.0f - armWave),
            bodyCenter.x - radius * 0.52f, bodyCenter.y + radius * (-0.08f - armWave),
            bodyCenter.x - radius * 0.48f, bodyCenter.y + radius * (-0.14f - armWave)
        )
    }
    drawPath(leftArm, Color(0xFF00ACC1), style = Stroke(width = radius * 0.08f))
    // Left hand — tiny circle
    drawCircle(Color(0xFF4DD0E1), radius * 0.05f, Offset(bodyCenter.x - radius * 0.48f, bodyCenter.y + radius * (-0.14f - armWave)))

    // Right arm
    val rightArm = Path().apply {
        moveTo(bodyCenter.x + radius * 0.34f, bodyCenter.y + radius * 0.05f)
        cubicTo(
            bodyCenter.x + radius * 0.48f, bodyCenter.y + radius * (0.0f + armWave),
            bodyCenter.x + radius * 0.52f, bodyCenter.y + radius * (-0.05f + armWave),
            bodyCenter.x + radius * 0.50f, bodyCenter.y + radius * (-0.10f + armWave)
        )
    }
    drawPath(rightArm, Color(0xFF00ACC1), style = Stroke(width = radius * 0.08f))
    drawCircle(Color(0xFF4DD0E1), radius * 0.05f, Offset(bodyCenter.x + radius * 0.50f, bodyCenter.y + radius * (-0.10f + armWave)))

    // L. Tiny Feet — two small bumps at bottom
    drawOval(
        color = Color(0xFF00838F),
        topLeft = Offset(bodyCenter.x - radius * 0.20f, bodyCenter.y + radius * 0.36f),
        size = Size(radius * 0.16f, radius * 0.10f)
    )
    drawOval(
        color = Color(0xFF00838F),
        topLeft = Offset(bodyCenter.x + radius * 0.04f, bodyCenter.y + radius * 0.36f),
        size = Size(radius * 0.16f, radius * 0.10f)
    )
}

// ══════════════════════════════════════════════════════════════════════
// 3. TITAN VANGUARD (GUARDIÁN PRIMORDIAL)
// ══════════════════════════════════════════════════════════════════════
private fun DrawScope.drawTitanVanguardCharacter(
    center: Offset,
    radius: Float,
    themeColor: Color,
    azimuthRad: Float,
    elevationRad: Float,
    wavePhase: Float,
) {
    val headOffset = Offset(
        center.x + sin(azimuthRad) * radius * 0.18f,
        center.y - sin(elevationRad) * radius * 0.12f
    )

    // A. Kinetic Inherent Shield Flames (Double Layer: Red + Gold)
    for (layer in 0..1) {
        val auraColor = if (layer == 0) Color(0xFFFFD700).copy(alpha = 0.45f) else Color(0xFFFF1744).copy(alpha = 0.65f)
        val auraScale = if (layer == 0) 1.25f else 1.10f
        val auraPath = Path()
        val spikes = 12
        for (i in 0..spikes) {
            val theta = (i.toFloat() / spikes) * 2 * PI.toFloat()
            val spikeLength = radius * auraScale * (1f + 0.18f * sin(theta * 3f + wavePhase * 4f))
            val x = center.x + spikeLength * cos(theta)
            val y = center.y + spikeLength * sin(theta) * 0.85f - (radius * 0.15f)
            if (i == 0) auraPath.moveTo(x, y) else auraPath.lineTo(x, y)
        }
        auraPath.close()
        drawPath(auraPath, auraColor)
    }

    // B. Kinetic Anchor Tail wagging on the side
    val tailPath = Path().apply {
        val tailBob = sin(wavePhase * 2f) * radius * 0.15f
        moveTo(headOffset.x - radius * 0.35f, headOffset.y + radius * 0.55f)
        cubicTo(
            headOffset.x - radius * 0.85f, headOffset.y + radius * 0.85f + tailBob,
            headOffset.x - radius * 1.15f, headOffset.y + radius * 0.45f + tailBob,
            headOffset.x - radius * 0.95f, headOffset.y + radius * 0.25f + tailBob
        )
    }
    drawPath(tailPath, Color(0xFF6D4C41), style = Stroke(width = radius * 0.16f))

    // C. Crimson Vanguard Armor Plates
    drawOval(
        brush = Brush.verticalGradient(listOf(Color(0xFFD50000), Color(0xFF880E4F))),
        topLeft = Offset(headOffset.x - radius * 0.55f, headOffset.y + radius * 0.35f),
        size = Size(radius * 1.1f, radius * 0.45f)
    )

    // D. Wild Spiky Cybernetic Crest (Front and Back Layers)
    val hairSpikes = listOf(
        Pair(Offset(-0.45f, -0.65f), Offset(-0.85f, -0.95f)),
        Pair(Offset(-0.25f, -0.75f), Offset(-0.45f, -1.25f)),
        Pair(Offset(0.00f, -0.80f), Offset(0.00f, -1.35f)), // Top central spike
        Pair(Offset(0.25f, -0.75f), Offset(0.45f, -1.25f)),
        Pair(Offset(0.45f, -0.65f), Offset(0.85f, -0.95f)),
        Pair(Offset(-0.55f, -0.25f), Offset(-0.95f, -0.45f)), // Long shoulder locks
        Pair(Offset(0.55f, -0.25f), Offset(0.95f, -0.45f))
    )
    hairSpikes.forEach { (base, tip) ->
        val spikePath = Path().apply {
            moveTo(headOffset.x + base.x * radius, headOffset.y + base.y * radius)
            lineTo(headOffset.x + tip.x * radius, headOffset.y + tip.y * radius)
            lineTo(headOffset.x + (base.x + 0.15f) * radius, headOffset.y + (base.y + 0.15f) * radius)
            close()
        }
        drawPath(spikePath, Brush.verticalGradient(listOf(Color(0xFF1A1A1A), Color(0xFFB71C1C))))
        drawPath(spikePath, Color(0xFFFF1744), style = Stroke(1.2f))
    }

    // E. Warrior Face (Skin Tone)
    val facePath = Path().apply {
        moveTo(headOffset.x - radius * 0.32f, headOffset.y - radius * 0.25f)
        lineTo(headOffset.x + radius * 0.32f, headOffset.y - radius * 0.25f)
        lineTo(headOffset.x + radius * 0.26f, headOffset.y + radius * 0.22f)
        lineTo(headOffset.x, headOffset.y + radius * 0.42f) // Chiseled chin
        lineTo(headOffset.x - radius * 0.26f, headOffset.y + radius * 0.22f)
        close()
    }
    drawPath(facePath, Color(0xFFFFCC80))
    drawPath(facePath, Color(0xFF8D6E63), style = Stroke(1.8f))

    // F. Crimson Optical Mask & Golden Sensory Lenses
    val eyeSpread = radius * 0.16f
    val eyeY = headOffset.y - radius * 0.02f
    // Crimson eye surrounds
    drawOval(Color(0xFFD50000), Offset(headOffset.x - eyeSpread - 12f, eyeY - 8f), Size(24f, 16f))
    drawOval(Color(0xFFD50000), Offset(headOffset.x + eyeSpread - 12f, eyeY - 8f), Size(24f, 16f))
    // Fierce angular eyes (white sclera)
    drawOval(Color.White, Offset(headOffset.x - eyeSpread - 9f, eyeY - 5f), Size(18f, 10f))
    drawOval(Color.White, Offset(headOffset.x + eyeSpread - 9f, eyeY - 5f), Size(18f, 10f))
    // Golden amber irises
    drawCircle(Color(0xFFFFD700), 4.5f, Offset(headOffset.x - eyeSpread, eyeY))
    drawCircle(Color(0xFFFFD700), 4.5f, Offset(headOffset.x + eyeSpread, eyeY))
    drawCircle(Color.Black, 2f, Offset(headOffset.x - eyeSpread, eyeY))
    drawCircle(Color.Black, 2f, Offset(headOffset.x + eyeSpread, eyeY))

    // G. Intense Brows & Confident Grin
    drawLine(Color.Black, Offset(headOffset.x - eyeSpread - 12f, eyeY - 9f), Offset(headOffset.x - 2f, eyeY - 4f), 2.5f)
    drawLine(Color.Black, Offset(headOffset.x + 2f, eyeY - 4f), Offset(headOffset.x + eyeSpread + 12f, eyeY - 9f), 2.5f)
    drawLine(Color(0xFF3E2723), Offset(headOffset.x - 8f, headOffset.y + radius * 0.24f), Offset(headOffset.x + 8f, headOffset.y + radius * 0.22f), 2.2f)

    // H. Bio-electric Ki lightning arcs crackling
    if (sin(wavePhase * 6f) > 0.4f) {
        val spark1 = Path().apply {
            moveTo(headOffset.x - radius * 0.8f, headOffset.y)
            lineTo(headOffset.x - radius * 0.55f, headOffset.y - radius * 0.3f)
            lineTo(headOffset.x - radius * 0.7f, headOffset.y - radius * 0.6f)
        }
        drawPath(spark1, Color(0xFF00E5FF), style = Stroke(2f))
    }
}

// ══════════════════════════════════════════════════════════════════════
// 4. CYBER MECHA TITAN (ROBOT ACORAZADO)
// ══════════════════════════════════════════════════════════════════════
private fun DrawScope.drawCyberMechaCharacter(
    center: Offset,
    radius: Float,
    themeColor: Color,
    azimuthRad: Float,
    elevationRad: Float,
    wavePhase: Float,
) {
    val headOffset = Offset(
        center.x + sin(azimuthRad) * radius * 0.2f,
        center.y - sin(elevationRad) * radius * 0.15f
    )

    // Antenna fins
    drawLine(themeColor, Offset(headOffset.x - radius * 0.45f, headOffset.y - radius * 0.2f), Offset(headOffset.x - radius * 0.75f, headOffset.y - radius * 0.75f), 4f)
    drawLine(themeColor, Offset(headOffset.x + radius * 0.45f, headOffset.y - radius * 0.2f), Offset(headOffset.x + radius * 0.75f, headOffset.y - radius * 0.75f), 4f)

    // Angular Helmet
    val helmet = Path().apply {
        moveTo(headOffset.x, headOffset.y - radius * 0.6f)
        lineTo(headOffset.x + radius * 0.5f, headOffset.y - radius * 0.2f)
        lineTo(headOffset.x + radius * 0.4f, headOffset.y + radius * 0.4f)
        lineTo(headOffset.x, headOffset.y + radius * 0.65f)
        lineTo(headOffset.x - radius * 0.4f, headOffset.y + radius * 0.4f)
        lineTo(headOffset.x - radius * 0.5f, headOffset.y - radius * 0.2f)
        close()
    }
    drawPath(helmet, Brush.verticalGradient(listOf(Color(0xFF263238), Color(0xFF0D1B2A))))
    drawPath(helmet, themeColor, style = Stroke(2.5f))

    // Moving Visor LED Scan Bar
    val scanOffset = sin(wavePhase * 3f) * radius * 0.25f
    drawRect(
        color = Color(0xFF00E5FF),
        topLeft = Offset(headOffset.x - radius * 0.35f, headOffset.y - radius * 0.05f),
        size = Size(radius * 0.7f, 14f)
    )
    drawCircle(
        color = Color.White,
        radius = 8f,
        center = Offset(headOffset.x + scanOffset, headOffset.y + 2f)
    )
}

// ══════════════════════════════════════════════════════════════════════
// 5. LAYA VALQUIRIA CELESTIAL
// ══════════════════════════════════════════════════════════════════════
private fun DrawScope.drawLayaValkyrieCharacter(
    center: Offset,
    radius: Float,
    themeColor: Color,
    azimuthRad: Float,
    elevationRad: Float,
    wavePhase: Float,
) {
    val headOffset = Offset(
        center.x + sin(azimuthRad) * radius * 0.2f,
        center.y - sin(elevationRad) * radius * 0.15f
    )

    // Holographic Halo floating above
    drawOval(
        color = themeColor,
        topLeft = Offset(headOffset.x - radius * 0.55f, headOffset.y - radius * 0.95f),
        size = Size(radius * 1.1f, 18f),
        style = Stroke(2f)
    )

    // Flowing Cyber Hair
    val hairPath = Path().apply {
        val hairWave = sin(wavePhase * 2f) * 12f
        moveTo(headOffset.x - radius * 0.45f, headOffset.y - radius * 0.2f)
        cubicTo(
            headOffset.x - radius * 0.8f + hairWave, headOffset.y + radius * 0.4f,
            headOffset.x - radius * 0.6f + hairWave, headOffset.y + radius * 0.85f,
            headOffset.x - radius * 0.3f, headOffset.y + radius * 0.7f
        )
        lineTo(headOffset.x + radius * 0.3f, headOffset.y + radius * 0.7f)
        cubicTo(
            headOffset.x + radius * 0.6f - hairWave, headOffset.y + radius * 0.85f,
            headOffset.x + radius * 0.8f - hairWave, headOffset.y + radius * 0.4f,
            headOffset.x + radius * 0.45f, headOffset.y - radius * 0.2f
        )
        close()
    }
    drawPath(hairPath, Brush.verticalGradient(listOf(Color(0xFFE040FB), Color(0xFF7C4DFF))))

    // Anime Face
    drawOval(
        color = Color(0xFFFFE0B2),
        topLeft = Offset(headOffset.x - radius * 0.32f, headOffset.y - radius * 0.3f),
        size = Size(radius * 0.64f, radius * 0.65f)
    )

    // Cyan Angelic Eyes
    val eyeDist = radius * 0.14f
    drawCircle(Color(0xFF00E5FF), 6f, Offset(headOffset.x - eyeDist, headOffset.y))
    drawCircle(Color(0xFF00E5FF), 6f, Offset(headOffset.x + eyeDist, headOffset.y))
    drawCircle(Color.White, 2.5f, Offset(headOffset.x - eyeDist - 1f, headOffset.y - 1f))
    drawCircle(Color.White, 2.5f, Offset(headOffset.x + eyeDist - 1f, headOffset.y - 1f))

    // Crystal Tiara
    drawLine(Color.White, Offset(headOffset.x - radius * 0.28f, headOffset.y - radius * 0.22f), Offset(headOffset.x + radius * 0.28f, headOffset.y - radius * 0.22f), 2.5f)
    drawCircle(themeColor, 4f, Offset(headOffset.x, headOffset.y - radius * 0.22f))
}

// ══════════════════════════════════════════════════════════════════════
// 6. EVAIR LIVING SPIRIT (ESPÍRITU GUÍA)
// ══════════════════════════════════════════════════════════════════════
private fun DrawScope.drawEvairSpiritCharacter(
    center: Offset,
    radius: Float,
    themeColor: Color,
    azimuthRad: Float,
    elevationRad: Float,
    wavePhase: Float,
) {
    val headOffset = Offset(
        center.x + sin(azimuthRad) * radius * 0.2f,
        center.y - sin(elevationRad) * radius * 0.15f
    )

    // Crystal Fairy Wings
    val wingSway = sin(wavePhase * 3f) * 10f
    drawOval(
        brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.8f), themeColor.copy(alpha = 0.2f))),
        topLeft = Offset(headOffset.x - radius * 0.75f, headOffset.y - radius * 0.5f + wingSway),
        size = Size(radius * 0.55f, radius * 0.8f)
    )
    drawOval(
        brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.8f), themeColor.copy(alpha = 0.2f))),
        topLeft = Offset(headOffset.x + radius * 0.2f, headOffset.y - radius * 0.5f - wingSway),
        size = Size(radius * 0.55f, radius * 0.8f)
    )

    // Glowing Spirit Body
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White, themeColor, themeColor.copy(alpha = 0.6f)),
            center = headOffset,
            radius = radius * 0.55f
        ),
        radius = radius * 0.55f,
        center = headOffset
    )

    // Cute Anime Blinking Eyes
    val eyeDist = radius * 0.16f
    val eyeY = headOffset.y - radius * 0.05f
    drawCircle(Color(0xFF003366), 6f, Offset(headOffset.x - eyeDist, eyeY))
    drawCircle(Color(0xFF003366), 6f, Offset(headOffset.x + eyeDist, eyeY))
    drawCircle(Color.White, 2.5f, Offset(headOffset.x - eyeDist - 1.5f, eyeY - 2f))
    drawCircle(Color.White, 2.5f, Offset(headOffset.x + eyeDist - 1.5f, eyeY - 2f))

    // Happy Smile
    val smilePath = Path().apply {
        moveTo(headOffset.x - 7f, headOffset.y + radius * 0.12f)
        quadraticTo(headOffset.x, headOffset.y + radius * 0.20f, headOffset.x + 7f, headOffset.y + radius * 0.12f)
    }
    drawPath(smilePath, Color(0xFF003366), style = Stroke(2f))

    // Orbiting Satellites
    for (i in 0..2) {
        val angle = wavePhase + i * (2 * PI.toFloat() / 3)
        val sx = headOffset.x + radius * 0.85f * cos(angle)
        val sy = headOffset.y + radius * 0.45f * sin(angle)
        drawCircle(Color.White, 4f, Offset(sx, sy))
        drawCircle(themeColor, 7f, Offset(sx, sy), style = Stroke(1.5f))
    }
}
