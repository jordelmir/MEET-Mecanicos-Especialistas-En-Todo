package com.elysium369.meet.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.core.livelink.LiveLinkServer
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.neonGlow
import com.elysium369.meet.ui.theme.MeetColors

// ═══════════════════════════════════════════════════════════════
// LIVE LINK SCREEN — Optional Real-Time Telemetry Sharing
// ═══════════════════════════════════════════════════════════════

@Composable
fun LiveLinkScreen(
    navController: NavController,
    liveLinkServer: LiveLinkServer
) {
    val isRunning by liveLinkServer.isRunning.collectAsState()
    val connectedClients by liveLinkServer.connectedClients.collectAsState()
    val serverUrl by liveLinkServer.serverUrl.collectAsState()

    // Pulse animation when broadcasting
    val pulseAnim = rememberInfiniteTransition(label = "broadcast")
    val pulseAlpha by pulseAnim.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = EaseInOut), RepeatMode.Reverse),
        label = "pa"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeetColors.backgroundDeep)
            .padding(bottom = 24.dp)
    ) {
        // ── Top Bar ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Live Link", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Compartir telemetría en tiempo real", color = MeetColors.textSecondary, fontSize = 12.sp)
            }
            Icon(Icons.Default.Share, "Share", tint = MeetColors.electricBlue, modifier = Modifier.size(24.dp))
        }

        // ── Privacy Notice ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MeetColors.electricBlue.copy(alpha = 0.08f))
                .border(1.dp, MeetColors.electricBlue.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Default.Warning, "Info", tint = MeetColors.electricBlue, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Esta función es completamente OPCIONAL. Solo se comparte datos cuando usted lo activa. " +
                "La transmisión se realiza por su red WiFi local y puede detenerla en cualquier momento.",
                color = MeetColors.electricBlue, fontSize = 11.sp, lineHeight = 16.sp
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Status Card ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MeetColors.cardBackground)
                .border(
                    1.dp,
                    if (isRunning) MeetColors.neonGreen.copy(alpha = 0.4f) else MeetColors.borderSubtle,
                    RoundedCornerShape(16.dp)
                )
                .then(
                    if (isRunning) Modifier.neonGlow(MeetColors.neonGreen, RoundedCornerShape(16.dp), 2f, 10f)
                    else Modifier
                )
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                // Status indicator
                Box(contentAlignment = Alignment.Center) {
                    // Outer pulse
                    if (isRunning) {
                        Canvas(modifier = Modifier.size(80.dp)) {
                            drawCircle(
                                color = MeetColors.neonGreen.copy(alpha = pulseAlpha * 0.2f),
                                radius = size.minDimension / 2
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (isRunning) MeetColors.neonGreen.copy(alpha = 0.2f)
                                else MeetColors.textMuted.copy(alpha = 0.15f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(
                                    if (isRunning) MeetColors.neonGreen else MeetColors.textMuted,
                                    CircleShape
                                )
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    if (isRunning) "TRANSMITIENDO" else "INACTIVO",
                    color = if (isRunning) MeetColors.neonGreen else MeetColors.textMuted,
                    fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 2.sp
                )

                Spacer(Modifier.height(8.dp))

                if (isRunning && serverUrl != null) {
                    Text("Dirección del servidor:", color = MeetColors.textSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        serverUrl ?: "",
                        color = MeetColors.electricBlue, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MeetColors.electricBlue.copy(alpha = 0.1f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    // Connected clients
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(8.dp)
                                .background(if (connectedClients > 0) MeetColors.neonGreen else MeetColors.warning, CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "$connectedClients ${if (connectedClients == 1) "navegador conectado" else "navegadores conectados"}",
                            color = MeetColors.textSecondary, fontSize = 13.sp
                        )
                    }
                } else {
                    Text(
                        "Active la transmisión para compartir datos de diagnóstico con su navegador web",
                        color = MeetColors.textMuted, fontSize = 13.sp, textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(20.dp))

                // ── Main Toggle Button ──
                EliteButton(
                    text = if (isRunning) "⏹ Detener Transmisión" else "▶ Iniciar Transmisión",
                    onClick = {
                        if (isRunning) liveLinkServer.stop()
                        else liveLinkServer.start()
                    },
                    color = if (isRunning) MeetColors.error else MeetColors.neonGreen,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Instructions ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MeetColors.cardBackground)
                .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text("Cómo usar Live Link", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))

            val steps = listOf(
                "1" to "Conecte su teléfono y computadora a la misma red WiFi",
                "2" to "Presione \"Iniciar Transmisión\" arriba",
                "3" to "En su navegador web, abra el panel MEET y vaya a \"Live Link\"",
                "4" to "Ingrese la dirección IP mostrada arriba",
                "5" to "Los datos de diagnóstico aparecerán en tiempo real"
            )

            steps.forEach { (num, text) ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(MeetColors.electricBlue.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(num, color = MeetColors.electricBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(text, color = MeetColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Data Being Shared ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MeetColors.cardBackground)
                .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text("Datos compartidos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))

            val dataPoints = listOf(
                "RPM del motor", "Velocidad", "Temperatura refrigerante",
                "Posición acelerador", "Carga del motor", "Voltaje batería",
                "Trim de combustible", "Health Score", "Códigos DTC activos"
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    dataPoints.take(5).forEach { point ->
                        Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(4.dp).background(MeetColors.neonGreen, CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text(point, color = MeetColors.textSecondary, fontSize = 12.sp)
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    dataPoints.drop(5).forEach { point ->
                        Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(4.dp).background(MeetColors.neonGreen, CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text(point, color = MeetColors.textSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
