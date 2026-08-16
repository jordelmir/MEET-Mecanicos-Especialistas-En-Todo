package com.elysium369.meet.ui.screens

import com.elysium369.meet.ui.components.AnimatedNeonGlyph

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteOutlinedButton
import com.elysium369.meet.ui.components.EliteTextButton
import com.elysium369.meet.ui.components.HolographicBackgroundShared
import com.elysium369.meet.ui.components.neonGlow
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.identity.OnboardingUsageProfile
import java.util.Locale

// ═══════════════════════════════════════════════════════════════
// ONBOARDING V2 — Cinematic Entry Experience
// ═══════════════════════════════════════════════════════════════

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(1) }
    var selectedProfile by remember { mutableStateOf(OnboardingUsageProfile.OWNER) }
    var selectedAdapter by remember { mutableStateOf("bt_classic") }
    val detectedLanguage = remember { Locale.getDefault().language.takeIf { it == "es" || it == "en" } ?: "es" }

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
    val stepAccent = when (step) {
        2 -> MeetColors.cyberCyan
        3 -> MeetColors.electricBlue
        else -> MeetColors.neonGreen
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MeetColors.backgroundDeep)
    ) {
        HolographicBackgroundShared()

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
                EliteCard(
                    glowColor = stepAccent,
                    borderColor = stepAccent.copy(alpha = 0.32f),
                    backgroundColor = MeetColors.cardBackground,
                    shape = RoundedCornerShape(20.dp),
                    enableHolo3D = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (step) {
                            1 -> OnboardingStep1()
                            2 -> OnboardingStep2(selectedProfile) { selectedProfile = it }
                            3 -> OnboardingStep3(selectedAdapter) { selectedAdapter = it }
                            4 -> OnboardingStep4(selectedProfile, selectedAdapter, detectedLanguage)
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Step indicators
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                (1..4).forEach { s ->
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
                onClick = {
                    if (step < 4) {
                        step++
                    } else {
                        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putString("user_profile", selectedProfile.storageId)
                            .putString("usage_profile_sync_state", "PENDING")
                            .putString("preferred_adapter", selectedAdapter)
                            .putString("app_language", detectedLanguage)
                            .putBoolean("real_adapter_hint_seen", false)
                            .apply()
                        onFinish()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                text = if (step < 4) "SIGUIENTE →" else "INICIAR ⚡",
                color = if (step == 4) MeetColors.neonGreen else MeetColors.electricBlue
            )

            if (step < 4) {
                EliteTextButton(
                    onClick = {
                        context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putString("user_profile", selectedProfile.storageId)
                            .putString("usage_profile_sync_state", "PENDING")
                            .putString("preferred_adapter", selectedAdapter)
                            .putString("app_language", detectedLanguage)
                            .apply()
                        onFinish()
                    },
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
            AnimatedNeonGlyph("⚡", contentDescription = null, fontSize = 40.sp)
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
private fun OnboardingStep2(
    selectedProfile: OnboardingUsageProfile,
    onSelect: (OnboardingUsageProfile) -> Unit,
) {
    Column(
        modifier = Modifier
            .heightIn(max = 500.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "¿CÓMO USARÁS ELYSIUM VANGUARD?",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Text(
            "Ajustaremos la experiencia inicial; proveedor queda opcional",
            color = MeetColors.textSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(28.dp))

        OnboardingUsageProfile.entries.forEachIndexed { idx, profile ->
            Spacer(Modifier.height(if (idx == 0) 0.dp else 8.dp))
            EliteOutlinedButton(
                onClick = { onSelect(profile) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                text = "${if (selectedProfile == profile) "✓" else "•"}  ${profile.displayLabel} — ${profile.description}",
                color = if (selectedProfile == profile) MeetColors.neonGreen else MeetColors.cyberCyan
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Puedes cambiarlo luego desde ajustes. Esto no registra proveedor todavía.",
            color = MeetColors.textMuted,
            textAlign = TextAlign.Center,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun OnboardingStep3(selectedAdapter: String, onSelect: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "TIPO DE ADAPTADOR",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Text(
            "Elysium Vanguard buscará por todos, pero prioriza tu opción",
            color = MeetColors.textSecondary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(28.dp))

        listOf(
            Triple("bt_classic", "Bluetooth clásico", "ELM327/STN por SPP"),
            Triple("ble", "BLE", "adaptadores modernos"),
            Triple("wifi", "WiFi", "ELM por TCP/IP")
        ).forEachIndexed { idx, (id, title, desc) ->
            Spacer(Modifier.height(if (idx == 0) 0.dp else 10.dp))
            EliteOutlinedButton(
                onClick = { onSelect(id) },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                text = "${if (selectedAdapter == id) "✓" else "•"}  $title — $desc",
                color = if (selectedAdapter == id) MeetColors.neonGreen else MeetColors.cyberCyan
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Para lecturas reales necesitas un adaptador OBD-II físico y el switch en ON.",
            color = MeetColors.textMuted,
            textAlign = TextAlign.Center,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun OnboardingStep4(
    profile: OnboardingUsageProfile,
    adapter: String,
    language: String,
) {
    val profileLabel = profile.displayLabel
    val adapterLabel = when (adapter) {
        "ble" -> "BLE"
        "wifi" -> "WiFi ELM"
        else -> "Bluetooth clásico"
    }

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
            AnimatedNeonGlyph("✅", contentDescription = null, fontSize = 36.sp)
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
            "Perfil: $profileLabel\nAdaptador: $adapterLabel\nIdioma: ${language.uppercase()}",
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 24.sp
        )
        Spacer(Modifier.height(14.dp))
        Text(
            when (profile) {
                OnboardingUsageProfile.RIDE_PASSENGER ->
                    "Siguiente paso: inicia sesión y completa tu perfil para solicitar viajes en línea."
                OnboardingUsageProfile.RIDE_DRIVER ->
                    "Siguiente paso: inicia sesión y completa la verificación de conductor y vehículo. Elegir este perfil no te aprueba automáticamente."
                else ->
                    "Siguiente paso: agrega tu vehículo y conecta un adaptador OBD-II físico. Si quieres ofrecer servicios, activa proveedor después desde Proveedores."
            },
            color = MeetColors.textSecondary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 24.sp
        )
    }
}
