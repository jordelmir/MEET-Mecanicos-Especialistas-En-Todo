package com.elysium369.meet.core.agentstore.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.R
import kotlin.math.sin

/**
 * ══════════════════════════════════════════════════════════════════════
 *  O V N I s   y   R A Z A S  — Xenología Elysium
 *  ──────────────────────────────────────────────────────────────
 *  Pantalla de galería: razas extraterrestres con sus naves asignadas,
 *  avistamientos de plasmoides, e infografía de referencia.
 * ══════════════════════════════════════════════════════════════════════
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UapXenologyScreen(
    onBack: () -> Unit = {},
) {
    val scrollState = rememberScrollState()

    val infiniteTransition = rememberInfiniteTransition(label = "xenoGlow")
    val glowPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glowPhase"
    )

    Scaffold(
        containerColor = Color(0xFF0A0A0F),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "🛸 OVNIs y Razas",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D0D15)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── Section: Razas Extraterrestres ──
            SectionHeader("👽 Razas Extraterrestres", "Cada raza con su nave asignada", glowPhase)

            // Reptiliano + Tic-Tac
            RaceWithShipCard(
                raceName = "Reptiliano",
                raceDescription = "Apariencia reptiliana. Asociados a programas militares secretos.",
                raceImageRes = R.drawable.avatar_reptilian,
                raceColor = Color(0xFF4CAF50),
                shipName = "Tic-Tac UAP",
                shipImageRes = R.drawable.uap_tictac,
                glowPhase = glowPhase,
            )

            // Nórdico + Crescent/Wedge
            RaceWithShipCard(
                raceName = "Nórdico",
                raceDescription = "Apariencia humanoide alta, cabello platino. Intención benevolente.",
                raceImageRes = R.drawable.avatar_nordic,
                raceColor = Color(0xFF42A5F5),
                shipName = "Crescent / Wedge UAP",
                shipImageRes = R.drawable.uap_crescent_wedge,
                glowPhase = glowPhase,
            )

            // Gris + Lenticular
            RaceWithShipCard(
                raceName = "Gris",
                raceDescription = "Apariencia delgada, ojos negros enormes. Abducción y experimentación.",
                raceImageRes = R.drawable.avatar_grey,
                raceColor = Color(0xFF78909C),
                shipName = "Lenticular UAP",
                shipImageRes = R.drawable.uap_lenticular,
                glowPhase = glowPhase,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Section: Plasmoides ──
            SectionHeader("🔴 Plasmoides", "Avistamientos de energía luminosa", glowPhase)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlasmoideCard("Rojo", R.drawable.plasmoide_rojo, Color(0xFFFF1744), Modifier.weight(1f))
                PlasmoideCard("Blanco", R.drawable.plasmoide_blanco_1, Color(0xFFFFCDD2), Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlasmoideCard("Rosa", R.drawable.plasmoide_blanco_2, Color(0xFFFF80AB), Modifier.weight(1f))
                PlasmoideCard("Verde", R.drawable.plasmoide_verde, Color(0xFF69F0AE), Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Section: Infografía ──
            SectionHeader("📊 Infografía Completa", "Razas, naves y tipología UAP", glowPhase)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.infografia_ovnis_razas),
                    contentDescription = "Infografía OVNIs y Razas",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.FillWidth,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, glowPhase: Float) {
    val glowAlpha = (0.5f + 0.5f * sin(glowPhase)).coerceIn(0f, 1f)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.9f + glowAlpha * 0.1f),
        )
        Text(
            text = subtitle,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun RaceWithShipCard(
    raceName: String,
    raceDescription: String,
    raceImageRes: Int,
    raceColor: Color,
    shipName: String,
    shipImageRes: Int,
    glowPhase: Float,
) {
    val glowAlpha = (0.3f + 0.4f * sin(glowPhase)).coerceIn(0f, 0.7f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        raceColor.copy(alpha = glowAlpha),
                        raceColor.copy(alpha = 0.1f),
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF12121E)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Race face
                Image(
                    painter = painterResource(id = raceImageRes),
                    contentDescription = raceName,
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .border(2.dp, raceColor.copy(alpha = glowAlpha), CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = raceName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = raceColor,
                    )
                    Text(
                        text = raceDescription,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        lineHeight = 16.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Linked ship
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A18)),
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🛸 Nave asignada: $shipName",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = raceColor.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Image(
                        painter = painterResource(id = shipImageRes),
                        contentDescription = shipName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlasmoideCard(
    label: String,
    imageRes: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF12121E)),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = "Plasmoide $label",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = accentColor,
            )
        }
    }
}
