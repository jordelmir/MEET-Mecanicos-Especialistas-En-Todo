package com.elysium369.meet.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteOutlinedButton
import com.elysium369.meet.ui.components.EliteTextButton
import com.elysium369.meet.ui.components.neonGlow
import com.elysium369.meet.ui.theme.MeetColors

// ═══════════════════════════════════════════════════════════════
// ONBOARDING V2 — Cinematic Entry Experience
// ═══════════════════════════════════════════════════════════════

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(1) }

    // Step transition animation
    var animateContent by remember { mutableStateOf(true) }
    LaunchedEffect(step) {
        animateContent = false
        kotlinx.coroutines.delay(50)
        animateContent = true
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (animateContent) 1f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing), label = "ca"
    )
    val contentOffset by animateFloatAsState(
        targetValue = if (animateContent) 0f else 50f,
        animationSpec = tween(500, easing = FastOutSlowInEasing), label = "co"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MeetColors.backgroundDeep)
    ) {
        // Ambient background orbs
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(MeetColors.electricBlue.copy(alpha = 0.06f), Color.Transparent),
                    center = Offset(size.width * 0.7f, size.height * 0.15f),
                    radius = size.width * 0.5f
                ),
                center = Offset(size.width * 0.7f, size.height * 0.15f),
                radius = size.width * 0.5f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(MeetColors.neonGreen.copy(alpha = 0.04f), Color.Transparent),
                    center = Offset(size.width * 0.3f, size.height * 0.8f),
                    radius = size.width * 0.5f
                ),
                center = Offset(size.width * 0.3f, size.height * 0.8f),
                radius = size.width * 0.5f
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.weight(0.3f))

            Box(
                modifier = Modifier
                    .alpha(contentAlpha)
                    .graphicsLayer { translationY = contentOffset }
            ) {
                when (step) {
                    1 -> OnboardingStep1()
                    2 -> OnboardingStep2 { step = 3 }
                    3 -> OnboardingStep3()
                }
            }

            Spacer(Modifier.weight(1f))

            // Step indicators
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                (1..3).forEach { s ->
                    val isActive = s == step
                    val width by animateDpAsState(
                        if (isActive) 24.dp else 8.dp,
                        tween(300), label = "sw"
                    )
                    val color = if (isActive) MeetColors.neonGreen
                    else MeetColors.neonGreen.copy(alpha = 0.15f)

                    Surface(
                        color = color,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(8.dp)
                            .width(width)
                    ) {}
                }
            }

            Spacer(Modifier.height(28.dp))

            EliteButton(
                onClick = { if (step < 3) step++ else onFinish() },
                modifier = Modifier.fillMaxWidth(),
                text = if (step < 3) "SIGUIENTE →" else "INICIAR ⚡",
                color = if (step == 3) MeetColors.neonGreen else MeetColors.electricBlue
            )

            if (step < 3) {
                EliteTextButton(
                    onClick = onFinish,
                    text = "Saltar por ahora",
                    color = MeetColors.textMuted
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun OnboardingStep1() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Glow badge
        Box(
            modifier = Modifier
                .size(90.dp)
                .neonGlow(
                    MeetColors.neonGreen, CircleShape,
                    minElevation = 8f, maxElevation = 30f,
                    minAlpha = 0.3f, maxAlpha = 0.7f
                )
                .background(
                    Brush.radialGradient(
                        listOf(MeetColors.neonGreen.copy(alpha = 0.2f), Color.Transparent)
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("⚡", fontSize = 40.sp)
        }
        Spacer(Modifier.height(32.dp))
        Text(
            "ELYSIUM",
            style = MaterialTheme.typography.displayLarge,
            color = MeetColors.neonGreen,
            fontWeight = FontWeight.Black
        )
        Text(
            "VANGUARD",
            style = MaterialTheme.typography.headlineLarge,
            color = MeetColors.electricBlue,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "El diagnóstico que tu taller merece.",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Conéctate a cualquier vehículo y obtén\nmétricas profesionales en segundos.",
            color = MeetColors.textSecondary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 22.sp
        )
    }
}

@Composable
private fun OnboardingStep2(onSelect: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "TIPO DE ADAPTADOR",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Text(
            "Selecciona cómo te conectarás",
            color = MeetColors.textSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(28.dp))

        listOf(
            Triple("📡", "Bluetooth", "Recomendado"),
            Triple("🔵", "BLE", "Bajo consumo"),
            Triple("📶", "WiFi", "Alta velocidad")
        ).forEachIndexed { idx, (icon, title, desc) ->
            Spacer(Modifier.height(if (idx == 0) 0.dp else 10.dp))
            EliteOutlinedButton(
                onClick = onSelect,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                text = "$icon  $title — $desc",
                color = MeetColors.cyberCyan
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "¿No sabes cuál tienes? Elige Bluetooth.",
            color = MeetColors.textMuted,
            textAlign = TextAlign.Center,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun OnboardingStep3() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .neonGlow(
                    MeetColors.neonGreen, CircleShape,
                    minElevation = 10f, maxElevation = 35f,
                    minAlpha = 0.3f, maxAlpha = 0.7f
                )
                .background(
                    Brush.radialGradient(
                        listOf(MeetColors.neonGreen.copy(alpha = 0.25f), Color.Transparent)
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("✅", fontSize = 36.sp)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "¡TODO LISTO!",
            style = MaterialTheme.typography.headlineMedium,
            color = MeetColors.neonGreen,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Ya puedes escanear vehículos\ncomo un profesional.",
            color = MeetColors.textSecondary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 24.sp
        )
    }
}
