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
 *  - ⚡ Criatura Eléctrica Estilo Pokémon (Volt Sparky): Orejitas, mejillas rojas eléctricas, cola rayo.
 *  - 💥 Guerrero Saiyajin SSJ4 (Ki Carmesí): Cabello salvaje, delineado rojo, ojos dorados, aura de Ki.
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
                "POKEMON_VOLT", "VOLT", "POKEMON", "SPARKY" -> {
                    drawPokemonVoltCharacter(center, dynamicRadius, themeColor, azimuthRad, elevationRad, wavePhase)
                }
                "SAIYAN_SSJ4", "SSJ4", "GOKU", "SAIYAN", "TACTICAL_SHIELD" -> {
                    drawSaiyanSsj4Character(center, dynamicRadius, themeColor, azimuthRad, elevationRad, wavePhase)
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
// 2. CRIATURA ELÉCTRICA ESTILO POKÉMON (VOLT SPARKY)
// ══════════════════════════════════════════════════════════════════════
private fun DrawScope.drawPokemonVoltCharacter(
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

    // A. Zigzag Lightning Tail (Wagging behind)
    val tailWag = sin(wavePhase * 3f) * radius * 0.25f
    val tailPath = Path().apply {
        moveTo(headOffset.x + radius * 0.35f, headOffset.y + radius * 0.4f)
        lineTo(headOffset.x + radius * 0.75f + tailWag, headOffset.y + radius * 0.2f)
        lineTo(headOffset.x + radius * 0.65f + tailWag, headOffset.y - radius * 0.05f)
        lineTo(headOffset.x + radius * 1.05f + tailWag, headOffset.y - radius * 0.35f)
        lineTo(headOffset.x + radius * 0.95f + tailWag, headOffset.y - radius * 0.15f)
        lineTo(headOffset.x + radius * 0.55f + tailWag, headOffset.y + radius * 0.15f)
        close()
    }
    drawPath(tailPath, Brush.linearGradient(listOf(Color(0xFF8B4513), Color(0xFFFFD700))))
    drawPath(tailPath, Color(0xFFFFA500), style = Stroke(2f))

    // B. Pointy Long Ears with Black Tips (Springy physics)
    val earWiggle = sin(wavePhase * 2f) * 0.1f
    val leftEar = Path().apply {
        moveTo(headOffset.x - radius * 0.4f, headOffset.y - radius * 0.3f)
        cubicTo(
            headOffset.x - radius * 0.7f, headOffset.y - radius * (0.8f + earWiggle),
            headOffset.x - radius * 0.85f, headOffset.y - radius * (1.1f + earWiggle),
            headOffset.x - radius * 0.55f, headOffset.y - radius * (1.15f + earWiggle)
        )
        cubicTo(
            headOffset.x - radius * 0.45f, headOffset.y - radius * 0.8f,
            headOffset.x - radius * 0.25f, headOffset.y - radius * 0.5f,
            headOffset.x - radius * 0.2f, headOffset.y - radius * 0.4f
        )
        close()
    }
    val rightEar = Path().apply {
        moveTo(headOffset.x + radius * 0.4f, headOffset.y - radius * 0.3f)
        cubicTo(
            headOffset.x + radius * 0.7f, headOffset.y - radius * (0.8f - earWiggle),
            headOffset.x + radius * 0.85f, headOffset.y - radius * (1.1f - earWiggle),
            headOffset.x + radius * 0.55f, headOffset.y - radius * (1.15f - earWiggle)
        )
        cubicTo(
            headOffset.x + radius * 0.45f, headOffset.y - radius * 0.8f,
            headOffset.x + radius * 0.25f, headOffset.y - radius * 0.5f,
            headOffset.x + radius * 0.2f, headOffset.y - radius * 0.4f
        )
        close()
    }
    drawPath(leftEar, Color(0xFFFFEB3B))
    drawPath(rightEar, Color(0xFFFFEB3B))
    drawPath(leftEar, Color(0xFFF57F17), style = Stroke(2f))
    drawPath(rightEar, Color(0xFFF57F17), style = Stroke(2f))

    // Black tips of ears
    drawCircle(Color(0xFF212121), radius * 0.18f, Offset(headOffset.x - radius * 0.65f, headOffset.y - radius * 1.05f))
    drawCircle(Color(0xFF212121), radius * 0.18f, Offset(headOffset.x + radius * 0.65f, headOffset.y - radius * 1.05f))

    // C. Chubby Round Yellow Face
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFF176), Color(0xFFFFD600)),
            center = headOffset,
            radius = radius * 0.6f
        ),
        topLeft = Offset(headOffset.x - radius * 0.55f, headOffset.y - radius * 0.45f),
        size = Size(radius * 1.1f, radius * 0.95f)
    )
    drawOval(
        color = Color(0xFFF57F17),
        topLeft = Offset(headOffset.x - radius * 0.55f, headOffset.y - radius * 0.45f),
        size = Size(radius * 1.1f, radius * 0.95f),
        style = Stroke(2.2f)
    )

    // D. Big Anime Eyes with Double Highlight
    val eyeY = headOffset.y - radius * 0.1f
    val eyeDist = radius * 0.24f
    // Left eye
    drawCircle(Color(0xFF212121), radius * 0.13f, Offset(headOffset.x - eyeDist, eyeY))
    drawCircle(Color.White, radius * 0.05f, Offset(headOffset.x - eyeDist - 2f, eyeY - 3f))
    drawCircle(Color.White, radius * 0.025f, Offset(headOffset.x - eyeDist + 3f, eyeY + 2f))
    // Right eye
    drawCircle(Color(0xFF212121), radius * 0.13f, Offset(headOffset.x + eyeDist, eyeY))
    drawCircle(Color.White, radius * 0.05f, Offset(headOffset.x + eyeDist - 2f, eyeY - 3f))
    drawCircle(Color.White, radius * 0.025f, Offset(headOffset.x + eyeDist + 3f, eyeY + 2f))

    // E. Red Electric Cheeks (Glowing with Sparks)
    val cheekY = headOffset.y + radius * 0.12f
    val cheekDist = radius * 0.38f
    drawCircle(Color(0xFFFF1744), radius * 0.15f, Offset(headOffset.x - cheekDist, cheekY))
    drawCircle(Color(0xFFFF1744), radius * 0.15f, Offset(headOffset.x + cheekDist, cheekY))
    // Electric sparks from cheeks
    if (sin(wavePhase * 5f) > 0.3f) {
        drawLine(Color.White, Offset(headOffset.x - cheekDist, cheekY), Offset(headOffset.x - cheekDist - 15f, cheekY - 10f), 2f)
        drawLine(Color.White, Offset(headOffset.x + cheekDist, cheekY), Offset(headOffset.x + cheekDist + 15f, cheekY - 10f), 2f)
    }

    // F. Cute Nose & Smile
    drawCircle(Color(0xFF424242), 2.5f, Offset(headOffset.x, headOffset.y + radius * 0.02f))
    val mouthPath = Path().apply {
        moveTo(headOffset.x - 10f, headOffset.y + radius * 0.18f)
        quadraticTo(headOffset.x - 5f, headOffset.y + radius * 0.25f, headOffset.x, headOffset.y + radius * 0.20f)
        quadraticTo(headOffset.x + 5f, headOffset.y + radius * 0.25f, headOffset.x + 10f, headOffset.y + radius * 0.18f)
    }
    drawPath(mouthPath, Color(0xFFB71C1C), style = Stroke(2f))
}

// ══════════════════════════════════════════════════════════════════════
// 3. GUERRERO DEL KI CARMESÍ (GOKU SSJ4)
// ══════════════════════════════════════════════════════════════════════
private fun DrawScope.drawSaiyanSsj4Character(
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

    // A. Super Saiyan 4 Ki Aura Flames (Double Layer: Red + Gold)
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

    // B. Brown Saiyan Tail wagging on the side
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

    // C. Crimson Fur Shoulders (SSJ4 iconic body)
    drawOval(
        brush = Brush.verticalGradient(listOf(Color(0xFFD50000), Color(0xFF880E4F))),
        topLeft = Offset(headOffset.x - radius * 0.55f, headOffset.y + radius * 0.35f),
        size = Size(radius * 1.1f, radius * 0.45f)
    )

    // D. Wild Spiky SSJ4 Black & Crimson Hair (Front and Back Layers)
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

    // F. SSJ4 Crimson Eye-Shadow Masks & Golden Eyes
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
