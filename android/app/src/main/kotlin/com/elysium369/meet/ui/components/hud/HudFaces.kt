package com.elysium369.meet.ui.components.hud

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.components.AnimatedNeonGlyph
import com.elysium369.meet.ui.theme.MeetColors
import kotlin.math.*

/**
 * Data class holding all OBD2 live values for HUD faces.
 */
data class HudData(
    val speed: Float = 0f,
    val rpm: Float = 0f,
    val coolantTemp: Float = 0f,
    val throttle: Float = 0f,
    val engineLoad: Float = 0f,
    val voltage: Float = 12.4f,
    val fuelLevel: Float = 0f,
    val intakeTemp: Float = 0f
)

/**
 * Renders the selected HUD face layout.
 */
@Composable
fun HudFaceRenderer(
    face: HudFaceType,
    data: HudData,
    modifier: Modifier = Modifier
) {
    when (face) {
        HudFaceType.NEON_DIGITAL -> NeonDigitalFace(data, modifier)
        HudFaceType.PREMIUM_COCKPIT -> PremiumCockpitFace(data, modifier)
        HudFaceType.MULTI_GAUGE -> MultiGaugeFace(data, modifier)
        HudFaceType.MINIMAL_HUD -> MinimalHudFace(data, modifier)
        HudFaceType.RACING_F1 -> RacingF1Face(data, modifier)
        HudFaceType.DUAL_RING -> DualRingFace(data, modifier)
        HudFaceType.MILITARY_OPS -> MilitaryOpsFace(data, modifier)
        HudFaceType.CYBER_MATRIX -> CyberMatrixFace(data, modifier)
        HudFaceType.RETRO_ANALOG -> RetroAnalogFace(data, modifier)
        HudFaceType.TESLA_CLEAN -> TeslaCleanFace(data, modifier)
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  FACE 1: NEON DIGITAL — Green neon RPM arc + big speed + fuel/temp bars
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun NeonDigitalFace(data: HudData, modifier: Modifier) {
    val green = Color(0xFF00FF66)
    val red = Color(0xFFFF2244)
    val cyan = Color(0xFF00E5FF)

    val animSpeed by animateFloatAsState(data.speed, spring(stiffness = 35f), label = "s")
    val animRpm by animateFloatAsState(data.rpm, spring(stiffness = 35f), label = "r")

    val inf = rememberInfiniteTransition(label = "nd")
    val pulse by inf.animateFloat(0.6f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "p")

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height * 0.42f
            val radius = size.minDimension * 0.35f
            val stroke = 8.dp.toPx()

            // RPM Arc background
            drawArc(green.copy(alpha = 0.08f), 180f, 180f, false,
                Offset(cx - radius, cy - radius), Size(radius * 2, radius * 2),
                style = Stroke(stroke, cap = StrokeCap.Round))

            // RPM Arc filled
            val rpmSweep = (animRpm / 8000f).coerceIn(0f, 1f) * 180f
            drawArc(
                brush = Brush.sweepGradient(
                    0f to green, 0.6f to cyan, 0.85f to red
                ),
                startAngle = 180f, sweepAngle = rpmSweep, useCenter = false,
                topLeft = Offset(cx - radius, cy - radius), size = Size(radius * 2, radius * 2),
                style = Stroke(stroke, cap = StrokeCap.Round))

            // RPM glow
            drawArc(green.copy(alpha = 0.15f * pulse), 180f, rpmSweep, false,
                Offset(cx - radius, cy - radius), Size(radius * 2, radius * 2),
                style = Stroke(stroke * 3f, cap = StrokeCap.Round))

            // Tick marks
            for (i in 0..8) {
                val angle = Math.toRadians((180.0 + i * 22.5))
                val r1 = radius + 8.dp.toPx()
                val r2 = radius + 16.dp.toPx()
                drawLine(
                    green.copy(alpha = 0.5f),
                    Offset(cx + r1 * cos(angle).toFloat(), cy + r1 * sin(angle).toFloat()),
                    Offset(cx + r2 * cos(angle).toFloat(), cy + r2 * sin(angle).toFloat()),
                    2f)
            }

            // Fuel bar (left side)
            val barH = size.height * 0.3f
            val barW = 6.dp.toPx()
            val fuelFill = (data.fuelLevel / 100f).coerceIn(0f, 1f)
            val barX = size.width * 0.08f
            val barY = size.height * 0.55f
            drawRoundRect(green.copy(alpha = 0.1f), Offset(barX, barY), Size(barW, barH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            drawRoundRect(green.copy(alpha = 0.8f), Offset(barX, barY + barH * (1f - fuelFill)), Size(barW, barH * fuelFill), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))

            // Temp bar (right side)
            val tempFill = (data.coolantTemp / 130f).coerceIn(0f, 1f)
            val tempColor = if (data.coolantTemp > 95) red else cyan
            val barX2 = size.width * 0.92f - barW
            drawRoundRect(tempColor.copy(alpha = 0.1f), Offset(barX2, barY), Size(barW, barH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            drawRoundRect(tempColor.copy(alpha = 0.8f), Offset(barX2, barY + barH * (1f - tempFill)), Size(barW, barH * tempFill), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        }

        // Digital readouts
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(20.dp))
            Text("x1000r/min", color = green.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(4.dp))
            Text("${animSpeed.toInt()}", color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Text("km/h", color = cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(8.dp))
            Text("RPM ${animRpm.toInt()}", color = if (data.rpm > 6000) red else green.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }

        // F and H labels
        Text("F", color = green, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp))
        Text("H", color = cyan, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp))

        // Bottom telemetry row
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MiniReadout("TEMP", "${data.coolantTemp.toInt()}°C", if (data.coolantTemp > 95) red else cyan)
            MiniReadout("LOAD", "${data.engineLoad.toInt()}%", green)
            MiniReadout("TPS", "${data.throttle.toInt()}%", cyan)
            MiniReadout("VOLT", String.format("%.1f", data.voltage), Color(0xFFFFD700))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  FACE 2: PREMIUM COCKPIT — Blue theme, speed center, RPM sweep
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun PremiumCockpitFace(data: HudData, modifier: Modifier) {
    val blue = Color(0xFF2979FF)
    val lightBlue = Color(0xFF80D8FF)
    val white = Color.White

    val animSpeed by animateFloatAsState(data.speed, spring(stiffness = 35f), label = "s")
    val animRpm by animateFloatAsState(data.rpm, spring(stiffness = 35f), label = "r")
    val inf = rememberInfiniteTransition(label = "pc")
    val glow by inf.animateFloat(0.5f, 1f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "g")

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height * 0.45f
            val r = size.minDimension * 0.32f
            val stroke = 10.dp.toPx()

            // Outer RPM ring (270° arc)
            drawArc(blue.copy(alpha = 0.08f), 135f, 270f, false,
                Offset(cx - r, cy - r), Size(r * 2, r * 2),
                style = Stroke(stroke, cap = StrokeCap.Round))

            val rpmSweep = (animRpm / 8000f).coerceIn(0f, 1f) * 270f
            drawArc(
                brush = Brush.sweepGradient(0f to lightBlue, 0.7f to blue, 1f to Color(0xFFFF1744)),
                135f, rpmSweep, false,
                Offset(cx - r, cy - r), Size(r * 2, r * 2),
                style = Stroke(stroke, cap = StrokeCap.Round))

            // Glow layer
            drawArc(blue.copy(alpha = 0.12f * glow), 135f, rpmSweep, false,
                Offset(cx - r, cy - r), Size(r * 2, r * 2),
                style = Stroke(stroke * 3f, cap = StrokeCap.Round))

            // Inner speed ring
            val r2 = r * 0.7f
            val stroke2 = 6.dp.toPx()
            drawArc(lightBlue.copy(alpha = 0.06f), 135f, 270f, false,
                Offset(cx - r2, cy - r2), Size(r2 * 2, r2 * 2),
                style = Stroke(stroke2, cap = StrokeCap.Round))
            val speedSweep = (animSpeed / 260f).coerceIn(0f, 1f) * 270f
            drawArc(lightBlue, 135f, speedSweep, false,
                Offset(cx - r2, cy - r2), Size(r2 * 2, r2 * 2),
                style = Stroke(stroke2, cap = StrokeCap.Round))

            // Scale ticks (outer)
            for (i in 0..8) {
                val angle = Math.toRadians(135.0 + i * 33.75)
                val rInner = r + 6.dp.toPx()
                val rOuter = r + 14.dp.toPx()
                drawLine(blue.copy(alpha = 0.4f),
                    Offset(cx + rInner * cos(angle).toFloat(), cy + rInner * sin(angle).toFloat()),
                    Offset(cx + rOuter * cos(angle).toFloat(), cy + rOuter * sin(angle).toFloat()), 1.5f)
            }

            // Horizontal data bar at bottom
            val barY = size.height * 0.82f
            val barH = 3.dp.toPx()
            drawRoundRect(blue.copy(alpha = 0.15f), Offset(size.width * 0.1f, barY), Size(size.width * 0.8f, barH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            val throttleFill = (data.throttle / 100f).coerceIn(0f, 1f)
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(lightBlue, blue)),
                topLeft = Offset(size.width * 0.1f, barY),
                size = Size(size.width * 0.8f * throttleFill, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
        }

        // Center speed display
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-20).dp)) {
            Text("${animSpeed.toInt()}", color = white, fontSize = 58.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Text("KM/H", color = lightBlue, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        }

        // Side data readouts
        Column(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).offset(y = (-10).dp), horizontalAlignment = Alignment.End) {
            SideReadout("⚡", "RPM", "${animRpm.toInt()}", blue)
            Spacer(Modifier.height(6.dp))
            SideReadout("🌡", "°C", "${data.coolantTemp.toInt()}", lightBlue)
            Spacer(Modifier.height(6.dp))
            SideReadout("🔋", "V", String.format("%.1f", data.voltage), Color(0xFFFFD700))
        }

        // Bottom telemetry
        Row(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            MiniReadout("LOAD", "${data.engineLoad.toInt()}%", blue)
            MiniReadout("TPS", "${data.throttle.toInt()}%", lightBlue)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  FACE 3: MULTI GAUGE — Multiple circular gauges + bars
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun MultiGaugeFace(data: HudData, modifier: Modifier) {
    val animSpeed by animateFloatAsState(data.speed, spring(stiffness = 35f), label = "s")
    val animRpm by animateFloatAsState(data.rpm, spring(stiffness = 35f), label = "r")

    Box(modifier = modifier.fillMaxSize()) {
        // Main layout: Speed left big, RPM right, small gauges below
        Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Left: Speed gauge
            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                MiniArcGauge(
                    value = animSpeed, maxVal = 260f, label = "KM/H",
                    color = Color(0xFF2979FF), size = 1f
                )
            }
            // Right column: RPM + small gauges
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MiniArcGauge(value = animRpm, maxVal = 8000f, label = "RPM", color = Color(0xFF00E676), size = 0.8f)
                }
                Row(modifier = Modifier.weight(0.5f).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MiniBarGauge("TEMP", data.coolantTemp, 130f, if (data.coolantTemp > 95) Color(0xFFFF1744) else Color(0xFF00E5FF))
                    MiniBarGauge("FUEL", data.fuelLevel, 100f, Color(0xFFFF9500))
                    MiniBarGauge("LOAD", data.engineLoad, 100f, Color(0xFF76FF03))
                    MiniBarGauge("TPS", data.throttle, 100f, Color(0xFF2979FF))
                }
            }
        }

        // Clock at bottom right
        val time = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()) }
        Text(time, color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  FACE 4: MINIMAL HUD — Ultra clean, max readability
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun MinimalHudFace(data: HudData, modifier: Modifier) {
    val animSpeed by animateFloatAsState(data.speed, spring(stiffness = 35f), label = "s")
    val animRpm by animateFloatAsState(data.rpm, spring(stiffness = 35f), label = "r")
    val white = Color.White
    val dim = Color.White.copy(alpha = 0.3f)

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Giant speed
            Text("${animSpeed.toInt()}", color = white, fontSize = 120.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = (-4).sp)
            Text("KM/H", color = dim, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
            Spacer(Modifier.height(24.dp))

            // RPM bar
            Box(modifier = Modifier.fillMaxWidth(0.7f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(dim.copy(alpha = 0.1f))) {
                Box(modifier = Modifier.fillMaxWidth((animRpm / 8000f).coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(2.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF00E676), Color(0xFFFF1744)))))
            }
            Spacer(Modifier.height(4.dp))
            Text("${animRpm.toInt()} RPM", color = dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }

        // Minimal corners info
        Text("${data.coolantTemp.toInt()}°", color = dim, fontSize = 16.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.TopEnd).padding(20.dp))
        Text("${String.format("%.1f", data.voltage)}V", color = dim, fontSize = 16.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp))
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  FACE 5: RACING F1 — Horizontal RPM bar + shift lights
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun RacingF1Face(data: HudData, modifier: Modifier) {
    val animSpeed by animateFloatAsState(data.speed, spring(stiffness = 35f), label = "s")
    val animRpm by animateFloatAsState(data.rpm, spring(stiffness = 35f), label = "r")
    val rpmFrac = (animRpm / 8000f).coerceIn(0f, 1f)

    val inf = rememberInfiniteTransition(label = "f1")
    val shiftFlash by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(200), RepeatMode.Reverse), label = "sf")

    val red = Color(0xFFFF1744)
    val green = Color(0xFF00E676)
    val blue = Color(0xFF2979FF)

    Box(modifier = modifier.fillMaxSize()) {
        // Shift lights row at top
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.Center) {
            repeat(15) { i ->
                val lit = rpmFrac > (i / 15f)
                val color = when {
                    i < 5 -> green
                    i < 10 -> Color(0xFFFFD700)
                    else -> red
                }
                val alpha = if (lit) {
                    if (rpmFrac > 0.9f && i >= 10) shiftFlash else 1f
                } else 0.15f
                Box(modifier = Modifier.padding(horizontal = 2.dp).size(width = 14.dp, height = 8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = alpha))
                    .then(if (lit) Modifier.shadow(4.dp, RoundedCornerShape(2.dp), ambientColor = color) else Modifier))
            }
        }

        // Center area
        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            // Gear indicator (estimated)
            val gear = when {
                data.speed < 1 -> "N"
                data.rpm > 100 && data.speed > 0 -> "${((data.speed / (data.rpm / 1000f)) * 1.2f).toInt().coerceIn(1, 6)}"
                else -> "—"
            }
            Text(gear, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(8.dp))
            Text("${animSpeed.toInt()}", color = Color.White, fontSize = 80.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = (-3).sp)
            Text("KM/H", color = blue, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
        }

        // RPM horizontal bar at bottom
        Canvas(modifier = Modifier.fillMaxWidth().height(24.dp).align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 8.dp)) {
            // Background
            drawRoundRect(Color.White.copy(alpha = 0.05f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            // Filled
            val w = size.width * rpmFrac
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(green, Color(0xFFFFD700), red)),
                size = Size(w, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            // Segment dividers
            repeat(8) { i ->
                val x = size.width * (i + 1) / 9f
                drawLine(Color.Black.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height), 1.5f)
            }
        }

        // Side data
        Column(modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp), horizontalAlignment = Alignment.Start) {
            F1DataItem("TEMP", "${data.coolantTemp.toInt()}°C", if (data.coolantTemp > 95) red else blue)
            Spacer(Modifier.height(8.dp))
            F1DataItem("VOLT", String.format("%.1f", data.voltage), Color(0xFFFFD700))
        }
        Column(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp), horizontalAlignment = Alignment.End) {
            F1DataItem("RPM", "${animRpm.toInt()}", green)
            Spacer(Modifier.height(8.dp))
            F1DataItem("LOAD", "${data.engineLoad.toInt()}%", blue)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  FACE 6: DUAL RING — Concentric rings
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun DualRingFace(data: HudData, modifier: Modifier) {
    val animSpeed by animateFloatAsState(data.speed, spring(stiffness = 35f), label = "s")
    val animRpm by animateFloatAsState(data.rpm, spring(stiffness = 35f), label = "r")

    val inf = rememberInfiniteTransition(label = "dr")
    val glow by inf.animateFloat(0.5f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "g")

    val outer = Color(0xFFFF6D00)
    val inner = Color(0xFF00E5FF)

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height * 0.45f
            val rOuter = size.minDimension * 0.38f
            val rInner = size.minDimension * 0.25f

            // Outer ring - RPM
            drawArc(outer.copy(alpha = 0.06f), 135f, 270f, false,
                Offset(cx - rOuter, cy - rOuter), Size(rOuter * 2, rOuter * 2),
                style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
            val rpmSweep = (animRpm / 8000f).coerceIn(0f, 1f) * 270f
            drawArc(outer, 135f, rpmSweep, false,
                Offset(cx - rOuter, cy - rOuter), Size(rOuter * 2, rOuter * 2),
                style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
            drawArc(outer.copy(alpha = 0.15f * glow), 135f, rpmSweep, false,
                Offset(cx - rOuter, cy - rOuter), Size(rOuter * 2, rOuter * 2),
                style = Stroke(24.dp.toPx(), cap = StrokeCap.Round))

            // Inner ring - Speed
            drawArc(inner.copy(alpha = 0.06f), 135f, 270f, false,
                Offset(cx - rInner, cy - rInner), Size(rInner * 2, rInner * 2),
                style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
            val speedSweep = (animSpeed / 260f).coerceIn(0f, 1f) * 270f
            drawArc(inner, 135f, speedSweep, false,
                Offset(cx - rInner, cy - rInner), Size(rInner * 2, rInner * 2),
                style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-20).dp)) {
            Text("${animSpeed.toInt()}", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Text("KM/H", color = inner, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        }

        // Ring labels
        Text("RPM ${animRpm.toInt()}", color = outer, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp))

        Row(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            MiniReadout("TEMP", "${data.coolantTemp.toInt()}°C", inner)
            MiniReadout("LOAD", "${data.engineLoad.toInt()}%", outer)
            MiniReadout("TPS", "${data.throttle.toInt()}%", inner)
            MiniReadout("VOLT", String.format("%.1f", data.voltage), Color(0xFFFFD700))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  FACE 7: MILITARY OPS — Green phosphor night vision
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun MilitaryOpsFace(data: HudData, modifier: Modifier) {
    val green = Color(0xFF76FF03)
    val animSpeed by animateFloatAsState(data.speed, spring(stiffness = 35f), label = "s")
    val animRpm by animateFloatAsState(data.rpm, spring(stiffness = 35f), label = "r")
    val inf = rememberInfiniteTransition(label = "mil")
    val scanY by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "scan")

    Box(modifier = modifier.fillMaxSize()) {
        // Grid + scan line
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val spacing = 30.dp.toPx()
            var y = 0f
            while (y < h) { drawLine(green.copy(alpha = 0.04f), Offset(0f, y), Offset(w, y), 0.5f); y += spacing }
            var x = 0f
            while (x < w) { drawLine(green.copy(alpha = 0.04f), Offset(x, 0f), Offset(x, h), 0.5f); x += spacing }

            // Scan line
            val sY = h * scanY
            drawLine(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, green.copy(alpha = 0.15f), Color.Transparent)),
                start = Offset(0f, sY), end = Offset(w, sY), strokeWidth = 2f)

            // Crosshair
            val cx = w / 2f; val cy = h * 0.4f; val cr = 60.dp.toPx()
            drawCircle(green.copy(alpha = 0.08f), cr, Offset(cx, cy), style = Stroke(1.dp.toPx()))
            drawCircle(green.copy(alpha = 0.05f), cr * 0.6f, Offset(cx, cy), style = Stroke(0.5f.dp.toPx()))
            drawLine(green.copy(alpha = 0.1f), Offset(cx - cr, cy), Offset(cx + cr, cy), 0.5f)
            drawLine(green.copy(alpha = 0.1f), Offset(cx, cy - cr), Offset(cx, cy + cr), 0.5f)
        }

        // Data
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            // Header
            Text("◼ TACTICAL DISPLAY v3.2", color = green.copy(alpha = 0.5f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(0.1f))

            // Center speed
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SPD", color = green.copy(alpha = 0.4f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text("${animSpeed.toInt()}", color = green, fontSize = 72.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Text("KM/H", color = green.copy(alpha = 0.5f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }

            Spacer(Modifier.weight(0.1f))

            // Data rows
            MilDataRow("RPM ", "${animRpm.toInt()}", green)
            MilDataRow("TEMP", "${data.coolantTemp.toInt()}°C", green)
            MilDataRow("LOAD", "${data.engineLoad.toInt()}%", green)
            MilDataRow("TPS ", "${data.throttle.toInt()}%", green)
            MilDataRow("VOLT", String.format("%.1fV", data.voltage), green)

            Spacer(Modifier.weight(0.05f))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  FACE 8: CYBER MATRIX — Cyberpunk data floating
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun CyberMatrixFace(data: HudData, modifier: Modifier) {
    val cyan = Color(0xFF00FFFF)
    val magenta = Color(0xFFFF00FF)
    val animSpeed by animateFloatAsState(data.speed, spring(stiffness = 35f), label = "s")
    val animRpm by animateFloatAsState(data.rpm, spring(stiffness = 35f), label = "r")
    val inf = rememberInfiniteTransition(label = "cm")
    val phase by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "ph")
    val pulse by inf.animateFloat(0.5f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "pu")

    Box(modifier = modifier.fillMaxSize()) {
        // Matrix rain particles
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            // Rain drops
            for (i in 0 until 30) {
                val x = (i * 37.7f) % w
                val y = ((i * 53.3f + phase * h * (0.5f + (i % 3) * 0.3f)) % h)
                val alpha = 0.05f + (i % 5) * 0.03f
                val len = 8.dp.toPx() + (i % 4) * 4.dp.toPx()
                drawLine(cyan.copy(alpha = alpha), Offset(x, y), Offset(x, y + len), 1f)
            }
            // Hex grid
            for (i in 0 until 8) {
                for (j in 0 until 12) {
                    val hx = w * i / 8f + if (j % 2 == 0) 0f else w / 16f
                    val hy = h * j / 12f
                    drawCircle(cyan.copy(alpha = 0.02f), 2.dp.toPx(), Offset(hx, hy))
                }
            }
        }

        // Floating data blocks
        Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceEvenly) {
            // Top: RPM
            CyberDataBlock("REVOLUTIONS", "${animRpm.toInt()}", "RPM", cyan, pulse)

            // Center: Speed (BIG)
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("「 VELOCITY 」", color = magenta.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("${animSpeed.toInt()}", color = Color.White, fontSize = 80.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = (-3).sp)
                    Text("▸ KM/H ◂", color = cyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }

            // Bottom row: small data
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                CyberMiniBlock("THERM", "${data.coolantTemp.toInt()}°", cyan)
                CyberMiniBlock("PWR%", "${data.engineLoad.toInt()}", magenta)
                CyberMiniBlock("THRTL", "${data.throttle.toInt()}%", cyan)
                CyberMiniBlock("ELEC", String.format("%.1f", data.voltage), Color(0xFFFFD700))
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  FACE 9: RETRO ANALOG — Classic needle gauges
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun RetroAnalogFace(data: HudData, modifier: Modifier) {
    val animSpeed by animateFloatAsState(data.speed, spring(stiffness = 50f), label = "s")
    val animRpm by animateFloatAsState(data.rpm, spring(stiffness = 50f), label = "r")

    val cream = Color(0xFFFFF8E1)
    val red = Color(0xFFD32F2F)
    val orange = Color(0xFFFF6D00)

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height

            // Left gauge: Speed
            val cx1 = w * 0.32f; val cy1 = h * 0.45f; val r1 = size.minDimension * 0.28f
            drawAnalogDial(cx1, cy1, r1, animSpeed, 260f, "KM/H", cream, orange, listOf("0","40","80","120","160","200","240","260"))

            // Right gauge: RPM
            val cx2 = w * 0.68f; val cy2 = h * 0.45f; val r2 = size.minDimension * 0.28f
            drawAnalogDial(cx2, cy2, r2, animRpm, 8000f, "x1000", cream, red, listOf("0","1","2","3","4","5","6","7","8"))
        }

        // Bottom data
        Row(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            MiniReadout("TEMP", "${data.coolantTemp.toInt()}°C", cream)
            MiniReadout("LOAD", "${data.engineLoad.toInt()}%", cream)
            MiniReadout("TPS", "${data.throttle.toInt()}%", orange)
            MiniReadout("VOLT", String.format("%.1f", data.voltage), cream)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  FACE 10: TESLA CLEAN — Modern minimalist
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun TeslaCleanFace(data: HudData, modifier: Modifier) {
    val animSpeed by animateFloatAsState(data.speed, spring(stiffness = 35f), label = "s")
    val animRpm by animateFloatAsState(data.rpm, spring(stiffness = 35f), label = "r")
    val white = Color.White
    val gray = Color(0xFF9E9E9E)
    val blue = Color(0xFF448AFF)

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
            // Speed section
            Column {
                Text("VELOCIDAD", color = gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${animSpeed.toInt()}", color = white, fontSize = 96.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = (-4).sp)
                    Text("km/h", color = gray, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp, start = 8.dp))
                }
            }

            // Horizontal bars section
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TeslaBar("RPM", animRpm, 8000f, blue)
                TeslaBar("TEMPERATURA", data.coolantTemp, 130f, if (data.coolantTemp > 95) Color(0xFFFF1744) else Color(0xFF00E5FF))
                TeslaBar("CARGA MOTOR", data.engineLoad, 100f, Color(0xFF00E676))
                TeslaBar("ACELERADOR", data.throttle, 100f, Color(0xFFFF6D00))
            }

            // Bottom info row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${animRpm.toInt()} RPM", color = gray, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Text("${String.format("%.1f", data.voltage)}V", color = gray, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                val time = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()) }
                Text(time, color = gray, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}


// ════════════════════════════════════════════════════════════════════════════
//  SHARED HELPER COMPOSABLES
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun MiniReadout(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = color.copy(alpha = 0.5f), fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun SideReadout(icon: String, label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedNeonGlyph(icon, contentDescription = null, fontSize = 12.sp)
        Spacer(Modifier.width(4.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(label, color = color.copy(alpha = 0.4f), fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text(value, color = color, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun F1DataItem(label: String, value: String, color: Color) {
    Column {
        Text(label, color = color.copy(alpha = 0.4f), fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Text(value, color = color, fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun MilDataRow(label: String, value: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("▸ $label", color = color.copy(alpha = 0.4f), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Text(value, color = color, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun CyberDataBlock(label: String, value: String, unit: String, color: Color, pulse: Float) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .border(0.5.dp, color.copy(alpha = 0.2f * pulse), RoundedCornerShape(4.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("⟨ $label ⟩", color = color.copy(alpha = 0.5f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(4.dp))
                Text(unit, color = color.copy(alpha = 0.6f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

@Composable
private fun CyberMiniBlock(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = color.copy(alpha = 0.4f), fontSize = 7.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(value, color = color, fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun MiniArcGauge(value: Float, maxVal: Float, label: String, color: Color, size: Float) {
    val animVal by animateFloatAsState(value, spring(stiffness = 35f), label = "mag")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.fillMaxWidth(size).aspectRatio(1f), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = this.size.width / 2f
                val cy = this.size.height / 2f
                val r = this.size.minDimension / 2f * 0.8f
                val stroke = 6.dp.toPx()

                drawArc(color.copy(alpha = 0.08f), 135f, 270f, false,
                    Offset(cx - r, cy - r), Size(r * 2, r * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round))
                val sweep = (animVal / maxVal).coerceIn(0f, 1f) * 270f
                drawArc(color, 135f, sweep, false,
                    Offset(cx - r, cy - r), Size(r * 2, r * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${animVal.toInt()}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Text(label, color = color.copy(alpha = 0.6f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MiniBarGauge(label: String, value: Float, maxVal: Float, color: Color) {
    val fill = (value / maxVal).coerceIn(0f, 1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = color.copy(alpha = 0.5f), fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Box(modifier = Modifier.width(16.dp).height(50.dp).clip(RoundedCornerShape(3.dp)).background(color.copy(alpha = 0.08f)), contentAlignment = Alignment.BottomCenter) {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(fill).clip(RoundedCornerShape(3.dp))
                .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.5f)))))
        }
        Spacer(Modifier.height(2.dp))
        Text("${value.toInt()}", color = color, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TeslaBar(label: String, value: Float, maxVal: Float, color: Color) {
    val animVal by animateFloatAsState(value, spring(stiffness = 50f), label = "tb")
    val fill = (animVal / maxVal).coerceIn(0f, 1f)
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color(0xFF757575), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("${animVal.toInt()}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.06f))) {
            Box(modifier = Modifier.fillMaxWidth(fill).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  CANVAS EXTENSION: Draw analog dial
// ════════════════════════════════════════════════════════════════════════════

@Suppress("UNUSED_PARAMETER")
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAnalogDial(
    cx: Float, cy: Float, radius: Float,
    value: Float, maxVal: Float,
    unit: String, faceColor: Color, needleColor: Color,
    labels: List<String>
) {
    val startAngle = 135f
    val sweep = 270f
    val stroke = 3.dp.toPx()

    // Face circle
    drawCircle(faceColor.copy(alpha = 0.03f), radius, Offset(cx, cy))
    drawCircle(faceColor.copy(alpha = 0.15f), radius, Offset(cx, cy), style = Stroke(1.dp.toPx()))

    // Scale arc
    drawArc(faceColor.copy(alpha = 0.12f), startAngle, sweep, false,
        Offset(cx - radius, cy - radius), Size(radius * 2, radius * 2),
        style = Stroke(stroke, cap = StrokeCap.Round))

    // Tick marks and labels
    labels.forEachIndexed { i, _ ->
        val angle = Math.toRadians((startAngle + i * sweep / (labels.size - 1)).toDouble())
        val rInner = radius * 0.82f
        val rOuter = radius * 0.95f
        drawLine(faceColor.copy(alpha = 0.4f),
            Offset(cx + rInner * cos(angle).toFloat(), cy + rInner * sin(angle).toFloat()),
            Offset(cx + rOuter * cos(angle).toFloat(), cy + rOuter * sin(angle).toFloat()), 1.5f)
    }

    // Needle
    val needleFrac = (value / maxVal).coerceIn(0f, 1f)
    val needleAngle = Math.toRadians((startAngle + needleFrac * sweep).toDouble())
    val needleLen = radius * 0.75f
    drawLine(needleColor, Offset(cx, cy),
        Offset(cx + needleLen * cos(needleAngle).toFloat(), cy + needleLen * sin(needleAngle).toFloat()),
        3.dp.toPx(), cap = StrokeCap.Round)

    // Center pivot
    drawCircle(needleColor, 5.dp.toPx(), Offset(cx, cy))
    drawCircle(Color.Black, 2.5f.dp.toPx(), Offset(cx, cy))
}
