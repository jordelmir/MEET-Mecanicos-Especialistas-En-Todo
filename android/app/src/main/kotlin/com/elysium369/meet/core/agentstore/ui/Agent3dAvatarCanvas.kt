package com.elysium369.meet.core.agentstore.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.elysium369.meet.R
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

    // Check if this is a bitmap-based alien agent
    val bitmapResId: Int? = when (avatarVisualType.uppercase()) {
        "REPTILIAN" -> R.drawable.avatar_reptilian
        "NORDIC" -> R.drawable.avatar_nordic
        "GREY" -> R.drawable.avatar_grey
        else -> null
    }

    if (bitmapResId != null) {
        // ── Bitmap Avatar with animated glow ──
        val glowAlpha = (0.4f + 0.4f * sin(wavePhase)).coerceIn(0f, 0.8f)
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            // Pulsing glow behind the image
            Box(
                modifier = Modifier
                    .fillMaxSize(0.85f * pulseScale)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(
                                    themeColor.copy(alpha = glowAlpha * 0.6f),
                                    themeColor.copy(alpha = glowAlpha * 0.2f),
                                    Color.Transparent
                                )
                            ),
                            radius = size.minDimension * 0.75f
                        )
                    }
            )
            // The actual image
            Image(
                painter = painterResource(id = bitmapResId),
                contentDescription = avatarVisualType,
                modifier = Modifier
                    .fillMaxSize(0.70f)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        brush = Brush.sweepGradient(
                            listOf(
                                themeColor.copy(alpha = glowAlpha),
                                themeColor.copy(alpha = 0.2f),
                                Color.White.copy(alpha = 0.3f),
                                themeColor.copy(alpha = glowAlpha),
                            )
                        ),
                        shape = CircleShape
                    ),
                contentScale = ContentScale.Crop,
            )
        }
    } else {
        // ── Canvas-drawn avatar ──
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
                    "CYBER_MECHA", "TITAN_EXOSKELETON", "MECHA" -> {
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
}

// ══════════════════════════════════════════════════════════════════════
// 1. DRACO IGNIS — SERPIENTE DE MAGMA VOLCÁNICA
//    Diseño Propietario Elysium: Criatura serpentina de obsidiana
//    enrollada sobre sí misma, con venas de lava incandescente
//    recorriendo su cuerpo, corona de cristales volcánicos,
//    ojos de brasa con pupilas en cruz, corazón de magma visible,
//    restos volcánicos flotantes y campo de ceniza.
// ══════════════════════════════════════════════════════════════════════
private fun DrawScope.drawDragonCharacter(
    center: Offset,
    radius: Float,
    themeColor: Color,
    azimuthRad: Float,
    elevationRad: Float,
    wavePhase: Float,
) {
    val bodyCenter = Offset(
        center.x + sin(azimuthRad) * radius * 0.12f,
        center.y - sin(elevationRad) * radius * 0.10f
    )
    val breathe = 1f + sin(wavePhase * 2f) * 0.03f

    // A. ASH PARTICLE FIELD — volcanic debris floating upward
    for (i in 0..9) {
        val ashPhase = wavePhase * 0.8f + i * 0.63f
        val ashX = bodyCenter.x + sin(ashPhase * 1.3f + i) * radius * 0.85f
        val ashY = bodyCenter.y + radius * 0.6f - (ashPhase % (PI.toFloat() * 2f)) * radius * 0.35f
        val ashAlpha = (0.5f - (ashPhase % (PI.toFloat() * 2f)) * 0.06f).coerceIn(0f, 0.5f)
        val ashSize = 1f + (i % 3)
        drawCircle(Color(0xFF424242).copy(alpha = ashAlpha), ashSize, Offset(ashX, ashY))
    }

    // B. HEAT SHIMMER AURA — distorted radial glow
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFF3D00).copy(alpha = 0.12f), Color(0xFFBF360C).copy(alpha = 0.05f), Color.Transparent),
            center = bodyCenter,
            radius = radius * 1.0f * breathe
        ),
        radius = radius * 1.0f * breathe,
        center = bodyCenter
    )

    // C. COILED SERPENT BODY — S-curve obsidian body with lava veins
    // Lower coil (tail end)
    val coilPath = Path().apply {
        moveTo(bodyCenter.x + radius * 0.35f, bodyCenter.y + radius * 0.45f)
        cubicTo(
            bodyCenter.x + radius * 0.55f, bodyCenter.y + radius * 0.55f,
            bodyCenter.x + radius * 0.45f, bodyCenter.y + radius * 0.65f,
            bodyCenter.x + radius * 0.15f, bodyCenter.y + radius * 0.55f
        )
        cubicTo(
            bodyCenter.x - radius * 0.20f, bodyCenter.y + radius * 0.42f,
            bodyCenter.x - radius * 0.45f, bodyCenter.y + radius * 0.50f,
            bodyCenter.x - radius * 0.35f, bodyCenter.y + radius * 0.35f
        )
    }
    drawPath(coilPath, Color(0xFF1A1A1A), style = Stroke(width = radius * 0.18f))
    // Lava veins on coil
    drawPath(coilPath, Color(0xFFFF3D00).copy(alpha = 0.5f), style = Stroke(width = radius * 0.04f))

    // Main body trunk (rising)
    val trunkPath = Path().apply {
        moveTo(bodyCenter.x - radius * 0.18f, bodyCenter.y + radius * 0.40f)
        cubicTo(
            bodyCenter.x - radius * 0.30f, bodyCenter.y + radius * 0.15f,
            bodyCenter.x - radius * 0.25f * breathe, bodyCenter.y - radius * 0.10f,
            bodyCenter.x - radius * 0.15f * breathe, bodyCenter.y - radius * 0.22f
        )
        lineTo(bodyCenter.x + radius * 0.15f * breathe, bodyCenter.y - radius * 0.22f)
        cubicTo(
            bodyCenter.x + radius * 0.25f * breathe, bodyCenter.y - radius * 0.10f,
            bodyCenter.x + radius * 0.30f, bodyCenter.y + radius * 0.15f,
            bodyCenter.x + radius * 0.18f, bodyCenter.y + radius * 0.40f
        )
        close()
    }
    drawPath(trunkPath, Brush.verticalGradient(listOf(Color(0xFF212121), Color(0xFF0D0D0D), Color(0xFF1A1A1A))))
    drawPath(trunkPath, Color(0xFF4A0000).copy(alpha = 0.4f), style = Stroke(1.5f))

    // D. LAVA VEINS — glowing cracks running through the body
    for (i in 0..4) {
        val veinY = bodyCenter.y + radius * (0.30f - i * 0.12f)
        val veinPulse = (0.4f + 0.6f * sin(wavePhase * 3f + i * 0.8f)).coerceIn(0f, 1f)
        val veinPath = Path().apply {
            moveTo(bodyCenter.x - radius * 0.12f, veinY)
            cubicTo(
                bodyCenter.x - radius * 0.05f, veinY - radius * 0.03f,
                bodyCenter.x + radius * 0.05f, veinY + radius * 0.02f,
                bodyCenter.x + radius * 0.12f, veinY - radius * 0.01f
            )
        }
        drawPath(veinPath, Color(0xFFFF6D00).copy(alpha = veinPulse), style = Stroke(2f))
        drawPath(veinPath, Color(0xFFFFAB00).copy(alpha = veinPulse * 0.5f), style = Stroke(4f))
    }

    // E. MAGMA HEART — visible through the chest, pulsing
    val heartPulse = 1f + sin(wavePhase * 4f) * 0.25f
    val heartY = bodyCenter.y + radius * 0.08f
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White, Color(0xFFFF6D00), Color(0xFFBF360C), Color(0xFF4A0000).copy(alpha = 0.3f)),
            center = Offset(bodyCenter.x, heartY),
            radius = radius * 0.10f * heartPulse
        ),
        radius = radius * 0.10f * heartPulse,
        center = Offset(bodyCenter.x, heartY)
    )
    // Magma cracks radiating from heart
    for (i in 0..4) {
        val crackAngle = i * (PI.toFloat() * 2f / 5) + wavePhase * 0.3f
        val crackEndX = bodyCenter.x + cos(crackAngle) * radius * 0.18f
        val crackEndY = heartY + sin(crackAngle) * radius * 0.15f
        drawLine(Color(0xFFFF3D00).copy(alpha = 0.6f), Offset(bodyCenter.x, heartY), Offset(crackEndX, crackEndY), 1.5f)
    }

    // F. HEAD — angular obsidian skull with pronounced jaw
    val headY = bodyCenter.y - radius * 0.30f
    val headPath = Path().apply {
        moveTo(bodyCenter.x, headY - radius * 0.25f) // Crown
        lineTo(bodyCenter.x + radius * 0.28f, headY - radius * 0.08f) // Right temple
        lineTo(bodyCenter.x + radius * 0.25f, headY + radius * 0.12f) // Right jaw hinge
        lineTo(bodyCenter.x + radius * 0.15f, headY + radius * 0.25f) // Right jaw point
        lineTo(bodyCenter.x, headY + radius * 0.30f) // Chin
        lineTo(bodyCenter.x - radius * 0.15f, headY + radius * 0.25f)
        lineTo(bodyCenter.x - radius * 0.25f, headY + radius * 0.12f)
        lineTo(bodyCenter.x - radius * 0.28f, headY - radius * 0.08f)
        close()
    }
    drawPath(headPath, Brush.radialGradient(
        listOf(Color(0xFF303030), Color(0xFF1A1A1A), Color(0xFF0D0D0D)),
        center = Offset(bodyCenter.x, headY),
        radius = radius * 0.30f
    ))
    drawPath(headPath, Color(0xFFFF3D00).copy(alpha = 0.4f), style = Stroke(1.8f))

    // G. VOLCANIC CRYSTAL CROWN — sharp prismatic formations
    val crystalData = listOf(
        Triple(-0.18f, -0.35f, 0.20f), // left
        Triple(-0.08f, -0.42f, 0.28f), // inner left
        Triple(0.0f, -0.48f, 0.32f),   // center (tallest)
        Triple(0.08f, -0.42f, 0.28f),  // inner right
        Triple(0.18f, -0.35f, 0.20f),  // right
    )
    for ((cx, baseY, height) in crystalData) {
        val crystalPath = Path().apply {
            moveTo(bodyCenter.x + cx * radius, headY + baseY * radius)
            lineTo(bodyCenter.x + (cx + 0.04f) * radius, headY + (baseY + height) * radius * -1f + headY)
            // Actually just do simple crystal spikes
            moveTo(bodyCenter.x + cx * radius - radius * 0.03f, headY - radius * 0.22f)
            lineTo(bodyCenter.x + cx * radius, headY - radius * (0.22f + height))
            lineTo(bodyCenter.x + cx * radius + radius * 0.03f, headY - radius * 0.22f)
            close()
        }
        val crystalGlow = sin(wavePhase * 2f + cx * 10f) * 0.3f + 0.7f
        drawPath(crystalPath, Brush.verticalGradient(
            listOf(Color(0xFFFFAB00).copy(alpha = crystalGlow), Color(0xFFBF360C), Color(0xFF4A0000))
        ))
        drawPath(crystalPath, Color(0xFFFF6D00).copy(alpha = 0.5f), style = Stroke(1f))
    }

    // H. EYES — ember eyes with cross-shaped pupils
    val eyeY = headY + radius * 0.02f
    val eyeSpread = radius * 0.14f
    for (side in listOf(-1f, 1f)) {
        val ex = bodyCenter.x + side * eyeSpread
        // Ember glow behind eye
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFFFF6D00).copy(alpha = 0.6f), Color.Transparent),
                center = Offset(ex, eyeY),
                radius = radius * 0.10f
            ),
            radius = radius * 0.10f,
            center = Offset(ex, eyeY)
        )
        // Eye shape — almond
        val eyePath = Path().apply {
            moveTo(ex - radius * 0.10f, eyeY)
            quadraticTo(ex, eyeY - radius * 0.07f, ex + radius * 0.10f, eyeY)
            quadraticTo(ex, eyeY + radius * 0.07f, ex - radius * 0.10f, eyeY)
        }
        drawPath(eyePath, Brush.radialGradient(
            listOf(Color(0xFFFFD600), Color(0xFFFF6D00)),
            center = Offset(ex, eyeY),
            radius = radius * 0.08f
        ))
        // Cross pupil
        val px = ex + sin(azimuthRad) * 2f
        drawLine(Color.Black, Offset(px, eyeY - radius * 0.05f), Offset(px, eyeY + radius * 0.05f), 2.5f)
        drawLine(Color.Black, Offset(px - radius * 0.03f, eyeY), Offset(px + radius * 0.03f, eyeY), 1.5f)
        // Specular
        drawCircle(Color.White.copy(alpha = 0.7f), 1.5f, Offset(ex - 2f, eyeY - 2f))
    }

    // I. NOSTRIL SLITS with heat distortion
    for (side in listOf(-1f, 1f)) {
        drawOval(
            Color(0xFF4A0000),
            Offset(bodyCenter.x + side * radius * 0.06f - radius * 0.02f, headY + radius * 0.18f),
            Size(radius * 0.04f, radius * 0.025f)
        )
    }
    // Heat wisps from nostrils
    if (sin(wavePhase * 3f) > -0.3f) {
        val wispAlpha = (0.3f + 0.3f * sin(wavePhase * 3f)).coerceIn(0f, 0.6f)
        for (side in listOf(-1f, 1f)) {
            val wispPath = Path().apply {
                moveTo(bodyCenter.x + side * radius * 0.06f, headY + radius * 0.20f)
                quadraticTo(
                    bodyCenter.x + side * radius * (0.10f + sin(wavePhase) * 0.04f), headY + radius * 0.28f,
                    bodyCenter.x + side * radius * (0.08f + sin(wavePhase * 1.5f) * 0.06f), headY + radius * 0.35f
                )
            }
            drawPath(wispPath, Color(0xFFFF6D00).copy(alpha = wispAlpha), style = Stroke(1.5f))
        }
    }

    // J. FLOATING VOLCANIC DEBRIS — 3 obsidian shards orbiting slowly
    for (i in 0..2) {
        val shardAngle = wavePhase * 0.7f + i * (2 * PI.toFloat() / 3)
        val shardDist = radius * 0.72f
        val sx = bodyCenter.x + cos(shardAngle) * shardDist
        val sy = bodyCenter.y + sin(shardAngle) * shardDist * 0.55f
        val shardPath = Path().apply {
            moveTo(sx, sy - radius * 0.04f)
            lineTo(sx + radius * 0.03f, sy)
            lineTo(sx, sy + radius * 0.03f)
            lineTo(sx - radius * 0.025f, sy - radius * 0.01f)
            close()
        }
        drawPath(shardPath, Color(0xFF212121))
        drawPath(shardPath, Color(0xFFFF3D00).copy(alpha = 0.4f), style = Stroke(1f))
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
// 3. TITAN VANGUARD — ROBOT ACORAZADO
//    Diseño Propietario Elysium: Robot acorazado viviente con torso
//    blindado, cañones de hombro, visor panorámico con escaneo LED,
//    reactor central, brazos mecánicos y propulsores de escape.
// ══════════════════════════════════════════════════════════════════════
private fun DrawScope.drawTitanVanguardCharacter(
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

    // A. Radar Sweep Field
    val sweepAngle = wavePhase * 1.5f
    for (ring in 1..3) {
        val ringRadius = radius * (0.65f + ring * 0.12f)
        drawCircle(
            color = Color(0xFF00E5FF).copy(alpha = 0.08f / ring),
            radius = ringRadius,
            center = bodyCenter,
            style = Stroke(1f)
        )
    }
    val sweepX = bodyCenter.x + cos(sweepAngle) * radius * 0.90f
    val sweepY = bodyCenter.y + sin(sweepAngle) * radius * 0.75f
    drawLine(Color(0xFF00E5FF).copy(alpha = 0.3f), bodyCenter, Offset(sweepX, sweepY), 1.5f)
    drawCircle(Color(0xFF00E5FF), 3f, Offset(sweepX, sweepY))

    // B. Hip Thrusters
    for (side in listOf(-1f, 1f)) {
        val thrusterX = bodyCenter.x + side * radius * 0.22f
        val thrusterY = bodyCenter.y + radius * 0.52f
        val flameLen = radius * 0.15f + sin(wavePhase * 6f + side) * radius * 0.05f
        drawRect(Color(0xFF37474F), Offset(thrusterX - radius * 0.06f, thrusterY), Size(radius * 0.12f, radius * 0.06f))
        val flamePath = Path().apply {
            moveTo(thrusterX - radius * 0.04f, thrusterY + radius * 0.06f)
            lineTo(thrusterX, thrusterY + radius * 0.06f + flameLen)
            lineTo(thrusterX + radius * 0.04f, thrusterY + radius * 0.06f)
        }
        drawPath(flamePath, Brush.verticalGradient(listOf(Color(0xFF00E5FF), Color(0xFF0D47A1).copy(alpha = 0.3f))))
    }

    // C. Mechanical Arms with claw grippers
    val armSwing = sin(wavePhase * 2f) * 0.06f
    for (side in listOf(-1f, 1f)) {
        val shoulderX = bodyCenter.x + side * radius * 0.42f
        val shoulderY = bodyCenter.y - radius * 0.10f
        val elbowX = bodyCenter.x + side * radius * 0.55f
        val elbowY = bodyCenter.y + radius * (0.12f + armSwing * side)
        val handX = bodyCenter.x + side * radius * 0.50f
        val handY = bodyCenter.y + radius * (0.30f + armSwing * side)
        drawLine(Color(0xFF455A64), Offset(shoulderX, shoulderY), Offset(elbowX, elbowY), radius * 0.07f)
        drawCircle(Color(0xFF546E7A), radius * 0.04f, Offset(elbowX, elbowY))
        drawLine(Color(0xFF455A64), Offset(elbowX, elbowY), Offset(handX, handY), radius * 0.06f)
        for (prong in -1..1) {
            val prongAngle = (prong * 25f + 90f) * (PI.toFloat() / 180f)
            val px = handX + cos(prongAngle) * radius * 0.06f * side
            val py = handY + sin(prongAngle) * radius * 0.06f
            drawLine(themeColor, Offset(handX, handY), Offset(px, py), 2f)
        }
    }

    // D. Shoulder Cannons
    for (side in listOf(-1f, 1f)) {
        val cannonX = bodyCenter.x + side * radius * 0.48f
        val cannonY = bodyCenter.y - radius * 0.28f
        drawRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF37474F), Color(0xFF263238))),
            topLeft = Offset(cannonX - radius * 0.04f, cannonY - radius * 0.12f),
            size = Size(radius * 0.08f, radius * 0.18f)
        )
        if (sin(wavePhase * 4f + side * 2f) > 0.6f) {
            drawCircle(Color(0xFFFF6D00), radius * 0.03f, Offset(cannonX, cannonY - radius * 0.12f))
        }
    }

    // E. Armored Torso
    val torsoPath = Path().apply {
        moveTo(bodyCenter.x, bodyCenter.y - radius * 0.42f)
        lineTo(bodyCenter.x + radius * 0.38f, bodyCenter.y - radius * 0.20f)
        lineTo(bodyCenter.x + radius * 0.35f, bodyCenter.y + radius * 0.15f)
        lineTo(bodyCenter.x + radius * 0.25f, bodyCenter.y + radius * 0.45f)
        lineTo(bodyCenter.x, bodyCenter.y + radius * 0.52f)
        lineTo(bodyCenter.x - radius * 0.25f, bodyCenter.y + radius * 0.45f)
        lineTo(bodyCenter.x - radius * 0.35f, bodyCenter.y + radius * 0.15f)
        lineTo(bodyCenter.x - radius * 0.38f, bodyCenter.y - radius * 0.20f)
        close()
    }
    drawPath(torsoPath, Brush.verticalGradient(listOf(Color(0xFF263238), Color(0xFF0D1B2A))))
    drawPath(torsoPath, themeColor.copy(alpha = 0.7f), style = Stroke(2f))
    for (i in 0..2) {
        val slitY = bodyCenter.y + radius * (0.0f + i * 0.10f)
        drawLine(Color(0xFF37474F), Offset(bodyCenter.x - radius * 0.18f, slitY), Offset(bodyCenter.x + radius * 0.18f, slitY), 1.5f)
    }

    // F. Chest Reactor
    val reactorPath = Path().apply {
        moveTo(bodyCenter.x, bodyCenter.y - radius * 0.12f)
        lineTo(bodyCenter.x + radius * 0.10f, bodyCenter.y + radius * 0.05f)
        lineTo(bodyCenter.x - radius * 0.10f, bodyCenter.y + radius * 0.05f)
        close()
    }
    drawPath(reactorPath, Brush.radialGradient(
        listOf(Color.White, Color(0xFF00E5FF), Color(0xFF0D47A1).copy(alpha = 0.4f)),
        center = Offset(bodyCenter.x, bodyCenter.y - radius * 0.03f),
        radius = radius * 0.12f
    ))
    drawPath(reactorPath, Color(0xFF00E5FF), style = Stroke(1.5f))

    // G. Status LEDs
    drawCircle(Color(0xFF76FF03), 2.5f, Offset(bodyCenter.x - radius * 0.12f, bodyCenter.y - radius * 0.18f))
    drawCircle(Color(0xFF76FF03), 2.5f, Offset(bodyCenter.x + radius * 0.12f, bodyCenter.y - radius * 0.18f))
    if (sin(wavePhase * 3f) > 0f) {
        drawCircle(Color(0xFFFFD700), 2f, Offset(bodyCenter.x, bodyCenter.y + radius * 0.10f))
    }

    // H. Angular Helmet
    val helmetPath = Path().apply {
        moveTo(bodyCenter.x, bodyCenter.y - radius * 0.42f)
        lineTo(bodyCenter.x + radius * 0.32f, bodyCenter.y - radius * 0.28f)
        lineTo(bodyCenter.x + radius * 0.28f, bodyCenter.y - radius * 0.05f)
        lineTo(bodyCenter.x + radius * 0.15f, bodyCenter.y + radius * 0.02f)
        lineTo(bodyCenter.x, bodyCenter.y + radius * 0.05f)
        lineTo(bodyCenter.x - radius * 0.15f, bodyCenter.y + radius * 0.02f)
        lineTo(bodyCenter.x - radius * 0.28f, bodyCenter.y - radius * 0.05f)
        lineTo(bodyCenter.x - radius * 0.32f, bodyCenter.y - radius * 0.28f)
        close()
    }
    drawPath(helmetPath, Brush.radialGradient(
        listOf(Color(0xFF37474F), Color(0xFF1A2430)),
        center = bodyCenter, radius = radius * 0.4f
    ))
    drawPath(helmetPath, themeColor, style = Stroke(2f))

    // I. Wide Visor with sweeping LED
    val visorPath = Path().apply {
        moveTo(bodyCenter.x - radius * 0.26f, bodyCenter.y - radius * 0.18f)
        lineTo(bodyCenter.x + radius * 0.26f, bodyCenter.y - radius * 0.18f)
        lineTo(bodyCenter.x + radius * 0.22f, bodyCenter.y - radius * 0.08f)
        lineTo(bodyCenter.x - radius * 0.22f, bodyCenter.y - radius * 0.08f)
        close()
    }
    drawPath(visorPath, Color(0xFF0D1B2A))
    drawPath(visorPath, Color(0xFF00E5FF).copy(alpha = 0.4f), style = Stroke(1.5f))
    val scanX = bodyCenter.x + sin(wavePhase * 3f) * radius * 0.20f
    val scanY = bodyCenter.y - radius * 0.13f
    drawCircle(Color.White, radius * 0.03f, Offset(scanX, scanY))
    drawCircle(Color(0xFF00E5FF), radius * 0.05f, Offset(scanX, scanY), style = Stroke(1f))

    // J. Antenna fins with blinking tips
    for (side in listOf(-1f, 1f)) {
        val baseX = bodyCenter.x + side * radius * 0.30f
        val baseY = bodyCenter.y - radius * 0.30f
        val tipX = bodyCenter.x + side * radius * 0.55f
        val tipY = bodyCenter.y - radius * 0.65f
        drawLine(Color(0xFF546E7A), Offset(baseX, baseY), Offset(tipX, tipY), 3f)
        val blinkAlpha = if (sin(wavePhase * 4f + side * 1.5f) > 0.3f) 1f else 0.3f
        drawCircle(themeColor.copy(alpha = blinkAlpha), 3f, Offset(tipX, tipY))
    }
}
// ══════════════════════════════════════════════════════════════════════
// ══════════════════════════════════════════════════════════════════════
// 4. CYBER MECHA — SENTINEL CUÁNTICO
//    Diseño Propietario Elysium: Entidad geométrica no-humanoide
//    compuesta de placas hexagonales flotantes que orbitan un
//    core de consciencia cuántica. Flujos holográficos de datos,
//    nubes de probabilidad, y reformación modular constante.
// ══════════════════════════════════════════════════════════════════════
private fun DrawScope.drawCyberMechaCharacter(
    center: Offset,
    radius: Float,
    themeColor: Color,
    azimuthRad: Float,
    elevationRad: Float,
    wavePhase: Float,
) {
    val coreCenter = Offset(
        center.x + sin(azimuthRad) * radius * 0.10f,
        center.y - sin(elevationRad) * radius * 0.08f
    )

    // A. PROBABILITY CLOUD — faint quantum field
    for (i in 0..5) {
        val cloudAngle = wavePhase * 0.4f + i * 1.05f
        val cloudDist = radius * (0.55f + sin(cloudAngle * 2f + i) * 0.20f)
        val cx = coreCenter.x + cos(cloudAngle) * cloudDist
        val cy = coreCenter.y + sin(cloudAngle) * cloudDist * 0.7f
        val cloudAlpha = (0.06f + 0.04f * sin(wavePhase * 2f + i.toFloat())).coerceIn(0f, 0.1f)
        drawCircle(Color(0xFF00E5FF).copy(alpha = cloudAlpha), radius * 0.15f, Offset(cx, cy))
    }

    // B. HOLOGRAPHIC DATA STREAMS — vertical flowing lines
    for (i in 0..3) {
        val streamX = coreCenter.x + (i - 1.5f) * radius * 0.22f
        val streamPhase = wavePhase * 2f + i * 0.7f
        val segments = 5
        for (s in 0 until segments) {
            val sy = coreCenter.y - radius * 0.50f + s * radius * 0.20f
            val segAlpha = (0.15f + 0.15f * sin(streamPhase + s * 0.5f)).coerceIn(0f, 0.3f)
            val segLen = radius * (0.06f + 0.04f * sin(streamPhase + s))
            drawLine(
                Color(0xFF00E5FF).copy(alpha = segAlpha),
                Offset(streamX, sy),
                Offset(streamX, sy + segLen),
                1.5f
            )
        }
    }

    // C. ORBITING HEXAGONAL PLATES — 6 modular plates forming a loose constellation
    for (i in 0..5) {
        val plateAngle = wavePhase * 0.6f + i * (PI.toFloat() / 3f)
        val plateDist = radius * (0.38f + sin(wavePhase * 1.5f + i * 0.9f) * 0.08f)
        val px = coreCenter.x + cos(plateAngle) * plateDist
        val py = coreCenter.y + sin(plateAngle) * plateDist * 0.75f
        val plateSize = radius * (0.10f + (i % 2) * 0.03f)

        // Hexagonal plate
        val hexPath = Path().apply {
            for (v in 0..5) {
                val va = v * (PI.toFloat() / 3f) + plateAngle * 0.3f
                val hx = px + cos(va) * plateSize
                val hy = py + sin(va) * plateSize
                if (v == 0) moveTo(hx, hy) else lineTo(hx, hy)
            }
            close()
        }
        drawPath(hexPath, Brush.radialGradient(
            listOf(Color(0xFF37474F), Color(0xFF1A2430)),
            center = Offset(px, py),
            radius = plateSize
        ))
        drawPath(hexPath, themeColor.copy(alpha = 0.5f), style = Stroke(1.2f))

        // Energy line connecting plate to core
        drawLine(
            Color(0xFF00E5FF).copy(alpha = 0.15f),
            coreCenter,
            Offset(px, py),
            0.8f
        )
    }

    // D. INNER CONSCIOUSNESS SHIELD — rotating inner hexagon
    val innerRot = wavePhase * 1.2f
    val innerPath = Path().apply {
        for (v in 0..5) {
            val va = v * (PI.toFloat() / 3f) + innerRot
            val hx = coreCenter.x + cos(va) * radius * 0.22f
            val hy = coreCenter.y + sin(va) * radius * 0.22f
            if (v == 0) moveTo(hx, hy) else lineTo(hx, hy)
        }
        close()
    }
    drawPath(innerPath, Color(0xFF0D1B2A).copy(alpha = 0.6f))
    drawPath(innerPath, Color(0xFF00E5FF).copy(alpha = 0.4f), style = Stroke(1.5f))

    // E. CONSCIOUSNESS CORE — pulsing radiant sphere
    val corePulse = 1f + sin(wavePhase * 3f) * 0.15f
    // Outer glow
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFF00E5FF).copy(alpha = 0.4f), Color(0xFF0D47A1).copy(alpha = 0.1f), Color.Transparent),
            center = coreCenter,
            radius = radius * 0.20f * corePulse
        ),
        radius = radius * 0.20f * corePulse,
        center = coreCenter
    )
    // Core body
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White, Color(0xFF00E5FF), Color(0xFF0091EA)),
            center = coreCenter,
            radius = radius * 0.10f * corePulse
        ),
        radius = radius * 0.10f * corePulse,
        center = coreCenter
    )

    // F. AWARENESS OPTICS — two floating eye-sensors above the core
    val opticY = coreCenter.y - radius * 0.12f
    val opticSpread = radius * 0.10f
    for (side in listOf(-1f, 1f)) {
        val ox = coreCenter.x + side * opticSpread
        // Sensor housing
        drawCircle(Color(0xFF263238), radius * 0.05f, Offset(ox, opticY))
        drawCircle(Color(0xFF00E5FF), radius * 0.05f, Offset(ox, opticY), style = Stroke(1f))
        // Sensor iris
        drawCircle(Color(0xFF00E5FF), radius * 0.03f, Offset(ox + sin(azimuthRad) * 1.5f, opticY))
        // Pupil
        drawCircle(Color.White, radius * 0.012f, Offset(ox + sin(azimuthRad) * 1.5f, opticY))
    }

    // G. SCANNING BEAM — rotating detection line
    val beamAngle = wavePhase * 2f
    val beamEndX = coreCenter.x + cos(beamAngle) * radius * 0.65f
    val beamEndY = coreCenter.y + sin(beamAngle) * radius * 0.50f
    drawLine(Color(0xFF00E5FF).copy(alpha = 0.25f), coreCenter, Offset(beamEndX, beamEndY), 1f)
    drawCircle(themeColor.copy(alpha = 0.6f), 2.5f, Offset(beamEndX, beamEndY))

    // H. STATUS NODES — 3 small indicators floating below
    for (i in 0..2) {
        val nodeX = coreCenter.x + (i - 1) * radius * 0.15f
        val nodeY = coreCenter.y + radius * 0.30f + sin(wavePhase * 2f + i.toFloat()) * radius * 0.03f
        val nodeColor = when (i) {
            0 -> Color(0xFF76FF03) // Online
            1 -> if (sin(wavePhase * 3f) > 0f) Color(0xFFFFD700) else Color(0xFF37474F) // Blinking
            else -> Color(0xFF00E5FF) // Active
        }
        drawCircle(nodeColor, 2.5f, Offset(nodeX, nodeY))
    }
}

// ══════════════════════════════════════════════════════════════════════
// 5. LAYA — TEJEDORA DE NEBULOSA
//    Diseño Propietario Elysium: Entidad cósmica cuyo cuerpo es gas
//    interestelar arremolinado. Patrones de constelación forman sus
//    rasgos. Cabello de flujos estelares, alas de refracción lumínica,
//    ojos de estrellas binarias, y manto gravitacional de polvo cósmico.
// ══════════════════════════════════════════════════════════════════════
private fun DrawScope.drawLayaValkyrieCharacter(
    center: Offset,
    radius: Float,
    themeColor: Color,
    azimuthRad: Float,
    elevationRad: Float,
    wavePhase: Float,
) {
    val bodyCenter = Offset(
        center.x + sin(azimuthRad) * radius * 0.12f,
        center.y - sin(elevationRad) * radius * 0.10f
    )
    val breathe = 1f + sin(wavePhase * 1.5f) * 0.03f

    // A. COSMIC DUST FIELD
    for (i in 0..11) {
        val starAngle = wavePhase * 0.3f + i * 0.52f
        val starDist = radius * (0.60f + sin(starAngle * 1.7f + i) * 0.30f)
        val sx = bodyCenter.x + cos(starAngle) * starDist
        val sy = bodyCenter.y + sin(starAngle) * starDist * 0.65f
        val twinkle = (0.3f + 0.7f * sin(wavePhase * 4f + i * 1.1f)).coerceIn(0f, 1f)
        drawCircle(Color.White.copy(alpha = twinkle * 0.6f), 1f + (i % 3) * 0.5f, Offset(sx, sy))
    }

    // B. GRAVITATIONAL NEBULA BODY
    val nebulaPath = Path().apply {
        moveTo(bodyCenter.x, bodyCenter.y - radius * 0.45f * breathe)
        cubicTo(bodyCenter.x + radius * 0.35f * breathe, bodyCenter.y - radius * 0.40f, bodyCenter.x + radius * 0.42f * breathe, bodyCenter.y - radius * 0.10f, bodyCenter.x + radius * 0.30f * breathe, bodyCenter.y + radius * 0.15f)
        cubicTo(bodyCenter.x + radius * 0.25f, bodyCenter.y + radius * 0.40f, bodyCenter.x + radius * 0.10f, bodyCenter.y + radius * 0.55f, bodyCenter.x, bodyCenter.y + radius * 0.50f)
        cubicTo(bodyCenter.x - radius * 0.10f, bodyCenter.y + radius * 0.55f, bodyCenter.x - radius * 0.25f, bodyCenter.y + radius * 0.40f, bodyCenter.x - radius * 0.30f * breathe, bodyCenter.y + radius * 0.15f)
        cubicTo(bodyCenter.x - radius * 0.42f * breathe, bodyCenter.y - radius * 0.10f, bodyCenter.x - radius * 0.35f * breathe, bodyCenter.y - radius * 0.40f, bodyCenter.x, bodyCenter.y - radius * 0.45f * breathe)
        close()
    }
    drawPath(nebulaPath, Brush.radialGradient(listOf(Color(0xFFE040FB).copy(alpha = 0.8f), Color(0xFF7C4DFF).copy(alpha = 0.6f), Color(0xFF311B92).copy(alpha = 0.3f)), center = bodyCenter, radius = radius * 0.45f))
    for (swirl in 0..2) {
        val swirlPhase = wavePhase * 0.8f + swirl * 2.1f
        val swirlPath = Path().apply {
            moveTo(bodyCenter.x - radius * 0.20f, bodyCenter.y + radius * (swirl - 1) * 0.15f)
            cubicTo(bodyCenter.x - radius * 0.05f + sin(swirlPhase) * radius * 0.08f, bodyCenter.y + radius * ((swirl - 1) * 0.15f - 0.08f), bodyCenter.x + radius * 0.05f + cos(swirlPhase) * radius * 0.08f, bodyCenter.y + radius * ((swirl - 1) * 0.15f + 0.06f), bodyCenter.x + radius * 0.20f, bodyCenter.y + radius * (swirl - 1) * 0.15f)
        }
        drawPath(swirlPath, Color(0xFFCE93D8).copy(alpha = 0.3f), style = Stroke(1.5f))
    }

    // C. LIGHT REFRACTION WINGS
    val wingBreathe = sin(wavePhase * 1.8f) * 0.08f
    for (side in listOf(-1f, 1f)) {
        val arcPath = Path().apply {
            moveTo(bodyCenter.x + side * radius * 0.12f, bodyCenter.y - radius * 0.10f)
            cubicTo(bodyCenter.x + side * radius * 0.55f, bodyCenter.y - radius * (0.50f + wingBreathe), bodyCenter.x + side * radius * 0.75f, bodyCenter.y - radius * (0.15f + wingBreathe), bodyCenter.x + side * radius * 0.50f, bodyCenter.y + radius * 0.20f)
            cubicTo(bodyCenter.x + side * radius * 0.40f, bodyCenter.y + radius * 0.10f, bodyCenter.x + side * radius * 0.25f, bodyCenter.y, bodyCenter.x + side * radius * 0.12f, bodyCenter.y - radius * 0.10f)
            close()
        }
        drawPath(arcPath, Brush.radialGradient(listOf(Color.White.copy(alpha = 0.25f), Color(0xFFE040FB).copy(alpha = 0.15f), Color(0xFF7C4DFF).copy(alpha = 0.05f)), center = Offset(bodyCenter.x + side * radius * 0.40f, bodyCenter.y - radius * 0.20f), radius = radius * 0.35f))
        drawPath(arcPath, Color(0xFFE040FB).copy(alpha = 0.2f), style = Stroke(1f))
        val innerArc = Path().apply {
            moveTo(bodyCenter.x + side * radius * 0.10f, bodyCenter.y + radius * 0.02f)
            cubicTo(bodyCenter.x + side * radius * 0.38f, bodyCenter.y - wingBreathe * radius * 0.5f, bodyCenter.x + side * radius * 0.50f, bodyCenter.y + radius * 0.15f, bodyCenter.x + side * radius * 0.35f, bodyCenter.y + radius * 0.30f)
            cubicTo(bodyCenter.x + side * radius * 0.25f, bodyCenter.y + radius * 0.22f, bodyCenter.x + side * radius * 0.15f, bodyCenter.y + radius * 0.10f, bodyCenter.x + side * radius * 0.10f, bodyCenter.y + radius * 0.02f)
            close()
        }
        drawPath(innerArc, Color.White.copy(alpha = 0.12f))
    }

    // D. STELLAR STREAM HAIR
    val hairFlow = sin(wavePhase * 1.3f) * radius * 0.05f
    for (strand in 0..4) {
        val strandX = bodyCenter.x + (strand - 2) * radius * 0.09f
        val strandPath = Path().apply {
            moveTo(strandX, bodyCenter.y - radius * 0.38f)
            cubicTo(strandX - radius * 0.08f + hairFlow, bodyCenter.y - radius * 0.15f, strandX + radius * 0.06f + hairFlow * (if (strand % 2 == 0) 1f else -1f), bodyCenter.y + radius * 0.10f, strandX - radius * 0.04f, bodyCenter.y + radius * (0.35f + strand * 0.04f))
        }
        drawPath(strandPath, Brush.verticalGradient(listOf(Color(0xFFE040FB).copy(alpha = 0.7f - strand * 0.08f), Color(0xFF7C4DFF).copy(alpha = 0.35f - strand * 0.04f))), style = Stroke(width = radius * 0.04f - strand * radius * 0.004f))
    }

    // E. FACE GLOW
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE0B2).copy(alpha = 0.7f), Color(0xFFE1BEE7).copy(alpha = 0.3f), Color.Transparent), center = Offset(bodyCenter.x, bodyCenter.y - radius * 0.05f), radius = radius * 0.20f), radius * 0.20f, Offset(bodyCenter.x, bodyCenter.y - radius * 0.05f))

    // F. BINARY STAR EYES
    val eyeY = bodyCenter.y - radius * 0.08f
    val eyeSpread = radius * 0.10f
    for (side in listOf(-1f, 1f)) {
        val ex = bodyCenter.x + side * eyeSpread
        drawCircle(Brush.radialGradient(listOf(Color(0xFF00E5FF).copy(alpha = 0.5f), Color.Transparent), center = Offset(ex, eyeY), radius = radius * 0.07f), radius * 0.07f, Offset(ex, eyeY))
        drawCircle(Color.White, radius * 0.035f, Offset(ex + sin(azimuthRad) * 1f, eyeY))
        val compAngle = wavePhase * 2f + side * 1.5f
        drawCircle(Color(0xFF00E5FF), radius * 0.015f, Offset(ex + cos(compAngle) * radius * 0.025f, eyeY + sin(compAngle) * radius * 0.015f))
    }

    // G. CONSTELLATION SMILE
    val smileStars = listOf(-0.05f, -0.02f, 0.0f, 0.02f, 0.05f)
    val smileY = bodyCenter.y + radius * 0.06f
    for (i in smileStars.indices) {
        val sx = bodyCenter.x + smileStars[i] * radius
        val sy = smileY + if (i == 0 || i == 4) 0f else radius * 0.015f
        drawCircle(Color(0xFFCE93D8), 1.2f, Offset(sx, sy))
        if (i < smileStars.size - 1) {
            val nx = bodyCenter.x + smileStars[i + 1] * radius
            val ny = smileY + if (i + 1 == 0 || i + 1 == 4) 0f else radius * 0.015f
            drawLine(Color(0xFFCE93D8).copy(alpha = 0.4f), Offset(sx, sy), Offset(nx, ny), 0.8f)
        }
    }

    // H. GRAVITATIONAL MANTO
    val mantoPath = Path().apply {
        moveTo(bodyCenter.x - radius * 0.20f, bodyCenter.y + radius * 0.35f)
        cubicTo(bodyCenter.x - radius * 0.30f + hairFlow, bodyCenter.y + radius * 0.55f, bodyCenter.x - radius * 0.10f + hairFlow, bodyCenter.y + radius * 0.70f, bodyCenter.x, bodyCenter.y + radius * 0.65f)
        cubicTo(bodyCenter.x + radius * 0.10f - hairFlow, bodyCenter.y + radius * 0.70f, bodyCenter.x + radius * 0.30f - hairFlow, bodyCenter.y + radius * 0.55f, bodyCenter.x + radius * 0.20f, bodyCenter.y + radius * 0.35f)
    }
    drawPath(mantoPath, Color(0xFF7C4DFF).copy(alpha = 0.2f), style = Stroke(radius * 0.12f))

    // I. CONSTELLATION CONNECTIONS
    val pts = listOf(Offset(bodyCenter.x - radius * 0.15f, bodyCenter.y - radius * 0.25f), Offset(bodyCenter.x + radius * 0.12f, bodyCenter.y - radius * 0.30f), Offset(bodyCenter.x + radius * 0.20f, bodyCenter.y + radius * 0.05f), Offset(bodyCenter.x - radius * 0.18f, bodyCenter.y + radius * 0.10f), Offset(bodyCenter.x, bodyCenter.y + radius * 0.25f))
    for (i in pts.indices) {
        drawCircle(Color.White.copy(alpha = 0.5f), 1.5f, pts[i])
        if (i < pts.size - 1) drawLine(Color.White.copy(alpha = 0.12f), pts[i], pts[i + 1], 0.6f)
    }
}


// ══════════════════════════════════════════════════════════════════════
// 6. EVAIR — SIMBIONTE BIOLUMINISCENTE
//    Diseño Propietario Elysium: Criatura de aguas abisales,
//    cuerpo translúcido de campana como medusa, red neuronal
//    visible como venas luminosas, órganos bioluminiscentes,
//    cromatóforos que pulsan color, tentáculos de luz trailing,
//    y un core de consciencia abisal.
// ══════════════════════════════════════════════════════════════════════
private fun DrawScope.drawEvairSpiritCharacter(
    center: Offset,
    radius: Float,
    themeColor: Color,
    azimuthRad: Float,
    elevationRad: Float,
    wavePhase: Float,
) {
    val bodyCenter = Offset(
        center.x + sin(azimuthRad) * radius * 0.10f,
        center.y - sin(elevationRad) * radius * 0.08f
    )
    val breathe = 1f + sin(wavePhase * 2f) * 0.05f

    // A. DEEP WATER AMBIENT — faint particles drifting upward
    for (i in 0..7) {
        val driftPhase = wavePhase * 0.5f + i * 0.8f
        val dx = bodyCenter.x + sin(driftPhase * 1.3f + i) * radius * 0.70f
        val dy = bodyCenter.y + radius * 0.50f - (driftPhase % (PI.toFloat() * 2f)) * radius * 0.25f
        val driftAlpha = (0.3f - (driftPhase % (PI.toFloat() * 2f)) * 0.04f).coerceIn(0f, 0.3f)
        drawCircle(themeColor.copy(alpha = driftAlpha), 1f + (i % 2), Offset(dx, dy))
    }

    // B. TRAILING LUMINOUS TENDRILS — 5 flowing tentacles below body
    for (t in 0..4) {
        val tendrilX = bodyCenter.x + (t - 2) * radius * 0.10f
        val tendrilSway = sin(wavePhase * 1.5f + t * 0.7f) * radius * 0.08f
        val tendrilPath = Path().apply {
            moveTo(tendrilX, bodyCenter.y + radius * 0.25f)
            cubicTo(
                tendrilX + tendrilSway, bodyCenter.y + radius * 0.40f,
                tendrilX - tendrilSway * 0.5f, bodyCenter.y + radius * 0.55f,
                tendrilX + tendrilSway * 0.3f, bodyCenter.y + radius * (0.65f + t * 0.03f)
            )
        }
        val tendrilAlpha = 0.5f - t * 0.06f
        drawPath(tendrilPath, Brush.verticalGradient(
            listOf(themeColor.copy(alpha = tendrilAlpha), themeColor.copy(alpha = tendrilAlpha * 0.2f))
        ), style = Stroke(width = radius * 0.025f - t * 0.002f))
        // Bioluminescent node at tendril tip
        if (sin(wavePhase * 3f + t.toFloat()) > 0.2f) {
            val tipX = tendrilX + tendrilSway * 0.3f
            val tipY = bodyCenter.y + radius * (0.65f + t * 0.03f)
            drawCircle(Color.White.copy(alpha = 0.6f), 1.5f, Offset(tipX, tipY))
        }
    }

    // C. JELLYFISH BELL BODY — translucent dome shape
    val bellPath = Path().apply {
        moveTo(bodyCenter.x - radius * 0.30f * breathe, bodyCenter.y + radius * 0.20f)
        cubicTo(
            bodyCenter.x - radius * 0.38f * breathe, bodyCenter.y - radius * 0.05f,
            bodyCenter.x - radius * 0.30f * breathe, bodyCenter.y - radius * 0.35f,
            bodyCenter.x, bodyCenter.y - radius * 0.42f * breathe
        )
        cubicTo(
            bodyCenter.x + radius * 0.30f * breathe, bodyCenter.y - radius * 0.35f,
            bodyCenter.x + radius * 0.38f * breathe, bodyCenter.y - radius * 0.05f,
            bodyCenter.x + radius * 0.30f * breathe, bodyCenter.y + radius * 0.20f
        )
        // Bell rim (scalloped edge)
        cubicTo(bodyCenter.x + radius * 0.20f, bodyCenter.y + radius * 0.28f, bodyCenter.x + radius * 0.10f, bodyCenter.y + radius * 0.22f, bodyCenter.x, bodyCenter.y + radius * 0.26f)
        cubicTo(bodyCenter.x - radius * 0.10f, bodyCenter.y + radius * 0.22f, bodyCenter.x - radius * 0.20f, bodyCenter.y + radius * 0.28f, bodyCenter.x - radius * 0.30f * breathe, bodyCenter.y + radius * 0.20f)
        close()
    }
    drawPath(bellPath, Brush.radialGradient(
        listOf(Color.White.copy(alpha = 0.5f), themeColor.copy(alpha = 0.35f), themeColor.copy(alpha = 0.15f)),
        center = Offset(bodyCenter.x, bodyCenter.y - radius * 0.10f),
        radius = radius * 0.40f
    ))
    drawPath(bellPath, themeColor.copy(alpha = 0.3f), style = Stroke(1.5f))

    // D. NEURAL NETWORK VEINS — glowing pathways inside the bell
    for (vein in 0..3) {
        val veinAngle = vein * (PI.toFloat() / 2f) + PI.toFloat() / 4f
        val veinPulse = (0.3f + 0.5f * sin(wavePhase * 2.5f + vein * 1.2f)).coerceIn(0f, 0.8f)
        val veinStartX = bodyCenter.x + cos(veinAngle) * radius * 0.05f
        val veinStartY = bodyCenter.y - radius * 0.05f
        val veinEndX = bodyCenter.x + cos(veinAngle) * radius * 0.25f
        val veinEndY = bodyCenter.y - radius * 0.05f + sin(veinAngle) * radius * 0.22f
        val veinPath = Path().apply {
            moveTo(veinStartX, veinStartY)
            cubicTo(
                veinStartX + cos(veinAngle) * radius * 0.12f, veinStartY + sin(veinAngle) * radius * 0.08f,
                veinEndX - cos(veinAngle) * radius * 0.05f, veinEndY - sin(veinAngle) * radius * 0.03f,
                veinEndX, veinEndY
            )
        }
        drawPath(veinPath, themeColor.copy(alpha = veinPulse * 0.5f), style = Stroke(1.2f))
        // Branch
        val branchX = veinEndX + cos(veinAngle + 0.5f) * radius * 0.08f
        val branchY = veinEndY + sin(veinAngle + 0.5f) * radius * 0.06f
        drawLine(themeColor.copy(alpha = veinPulse * 0.3f), Offset(veinEndX, veinEndY), Offset(branchX, branchY), 0.8f)
    }

    // E. ABYSSAL CONSCIOUSNESS CORE — pulsing inner light
    val corePulse = 1f + sin(wavePhase * 3f) * 0.25f
    val coreY = bodyCenter.y - radius * 0.03f
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White, themeColor, themeColor.copy(alpha = 0.2f)),
            center = Offset(bodyCenter.x, coreY),
            radius = radius * 0.09f * corePulse
        ),
        radius = radius * 0.09f * corePulse,
        center = Offset(bodyCenter.x, coreY)
    )

    // F. CHROMATOPHORE SPOTS — color-shifting bioluminescent dots
    val chromaData = listOf(
        Pair(-0.15f, -0.15f), Pair(0.12f, -0.18f), Pair(-0.10f, 0.08f),
        Pair(0.18f, 0.05f), Pair(0.0f, -0.25f), Pair(-0.20f, 0.0f)
    )
    for (i in chromaData.indices) {
        val (cx, cy) = chromaData[i]
        val chromaX = bodyCenter.x + cx * radius
        val chromaY = bodyCenter.y + cy * radius
        val chromaPhase = wavePhase * 2f + i * 0.9f
        val chromaAlpha = (0.3f + 0.4f * sin(chromaPhase)).coerceIn(0f, 0.7f)
        val chromaColor = if (sin(chromaPhase) > 0f) themeColor else Color(0xFF00E5FF)
        drawCircle(chromaColor.copy(alpha = chromaAlpha), radius * 0.025f, Offset(chromaX, chromaY))
    }

    // G. EYES — deep-sea bioluminescent eyes, round and hypnotic
    val eyeY = bodyCenter.y - radius * 0.13f
    val eyeSpread = radius * 0.11f
    for (side in listOf(-1f, 1f)) {
        val ex = bodyCenter.x + side * eyeSpread
        // Outer glow
        drawCircle(
            brush = Brush.radialGradient(
                listOf(themeColor.copy(alpha = 0.4f), Color.Transparent),
                center = Offset(ex, eyeY),
                radius = radius * 0.08f
            ),
            radius = radius * 0.08f,
            center = Offset(ex, eyeY)
        )
        // Eye body
        drawCircle(Color(0xFF001A33), radius * 0.06f, Offset(ex, eyeY))
        // Bioluminescent iris ring
        drawCircle(themeColor.copy(alpha = 0.7f), radius * 0.06f, Offset(ex, eyeY), style = Stroke(1.5f))
        // Bright pupil
        drawCircle(Color.White, radius * 0.025f, Offset(ex + sin(azimuthRad) * 1f, eyeY))
        // Secondary glow dot
        drawCircle(Color.White.copy(alpha = 0.5f), radius * 0.010f, Offset(ex - 1.5f, eyeY - 2f))
    }

    // H. GENTLE MOUTH — small curve
    val smilePath = Path().apply {
        moveTo(bodyCenter.x - radius * 0.05f, bodyCenter.y + radius * 0.04f)
        quadraticTo(bodyCenter.x, bodyCenter.y + radius * 0.08f, bodyCenter.x + radius * 0.05f, bodyCenter.y + radius * 0.04f)
    }
    drawPath(smilePath, themeColor.copy(alpha = 0.5f), style = Stroke(1.5f))

    // I. BIOLUMINESCENT RINGS — rotating halos around the bell
    for (ring in 0..1) {
        val ringAngle = wavePhase * (0.8f + ring * 0.4f)
        val ringY = bodyCenter.y - radius * (0.20f + ring * 0.12f)
        val ringW = radius * (0.25f - ring * 0.05f)
        drawOval(
            color = themeColor.copy(alpha = 0.15f + ring * 0.05f),
            topLeft = Offset(bodyCenter.x - ringW + sin(ringAngle) * 2f, ringY),
            size = Size(ringW * 2f, radius * 0.04f),
            style = Stroke(1f)
        )
    }
}
