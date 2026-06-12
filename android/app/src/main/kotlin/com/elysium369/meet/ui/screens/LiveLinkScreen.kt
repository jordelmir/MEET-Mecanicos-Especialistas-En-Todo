package com.elysium369.meet.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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
import kotlin.math.PI
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════
// LIVE LINK SCREEN — Optional Real-Time Telemetry Sharing (Elysium V2)
// ═══════════════════════════════════════════════════════════════

@Composable
fun LiveLinkScreen(
    navController: NavController,
    liveLinkServer: LiveLinkServer
) {
    val context = LocalContext.current
    val isRunning by liveLinkServer.isRunning.collectAsState()
    val connectedClients by liveLinkServer.connectedClients.collectAsState()
    val serverUrl by liveLinkServer.serverUrl.collectAsState()

    // Phase shift animation for telemetry oscilloscope wave
    val infiniteTransition = rememberInfiniteTransition(label = "telemetryWave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // Pulse alpha animation for status indicator
    val statusPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "statusPulse"
    )

    // Scroll state for the screen
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeetColors.backgroundDeep)
            .verticalScroll(scrollState)
            .padding(bottom = 32.dp)
    ) {
        // ── Top Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MeetColors.cardBackground)
                    .border(1.dp, MeetColors.borderSubtle, CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Live Link",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(Modifier.width(8.dp))
                    // Live Glow Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isRunning) MeetColors.neonGreen.copy(alpha = 0.15f)
                                else MeetColors.textMuted.copy(alpha = 0.2f)
                            )
                            .border(
                                1.dp,
                                if (isRunning) MeetColors.neonGreen.copy(alpha = 0.5f)
                                else MeetColors.textMuted,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "LIVE",
                            color = if (isRunning) MeetColors.neonGreen else MeetColors.textSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Text(
                    "Compartir telemetría en tiempo real",
                    color = MeetColors.textSecondary,
                    fontSize = 12.sp
                )
            }
            IconButton(
                onClick = {
                    if (isRunning && serverUrl != null) {
                        val shareIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
	                            putExtra(android.content.Intent.EXTRA_TEXT, "Conéctate al Live Link de mi escáner MEET con este enlace seguro: $serverUrl")
                            type = "text/plain"
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Compartir Live Link"))
                    } else {
                        Toast.makeText(context, "Inicia la transmisión primero", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MeetColors.cardBackground)
                    .border(1.dp, MeetColors.borderSubtle, CircleShape)
            ) {
                Icon(
                    Icons.Default.Share,
                    "Share",
                    tint = if (isRunning) MeetColors.cyberCyan else MeetColors.textSecondary
                )
            }
        }

        // ── Info Alert ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MeetColors.cardBackground)
                .border(1.dp, MeetColors.electricBlue.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Info,
                "Info",
                tint = MeetColors.electricBlue,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Función opcional. La transmisión se realiza únicamente a través de su red WiFi local y puede ser apagada inmediatamente.",
                color = MeetColors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Dynamic Oscilloscope Wave & Telemetry Control ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MeetColors.cardBackground)
                .border(
                    1.dp,
                    if (isRunning) MeetColors.neonGreen.copy(alpha = 0.5f) else MeetColors.borderSubtle,
                    RoundedCornerShape(16.dp)
                )
                .then(
                    if (isRunning) Modifier.neonGlow(MeetColors.neonGreen, RoundedCornerShape(16.dp), 2f, 12f)
                    else Modifier
                )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Real-time Oscilloscope Grid & Line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MeetColors.backgroundDeep)
                        .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                ) {
                    // Grid pattern background
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val gridSpacing = 20.dp.toPx()
                        val gridColor = MeetColors.borderSubtle.copy(alpha = 0.4f)
                        
                        // Vertical lines
                        var x = 0f
                        while (x < size.width) {
                            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.8f)
                            x += gridSpacing
                        }
                        // Horizontal lines
                        var y = 0f
                        while (y < size.height) {
                            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.8f)
                            y += gridSpacing
                        }
                    }

                    // Oscilloscope telemetry sine wave
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val points = 120
                        val path = Path()
                        val amplitude = if (isRunning) 28f else 4f
                        val frequency = if (isRunning) 4.5f else 1.2f
                        
                        for (i in 0..points) {
                            val x = i * size.width / points
                            val fraction = i.toFloat() / points
                            val angle = fraction * 2f * PI.toFloat() * frequency + phase
                            // Apply fading at boundaries
                            val fadeFactor = sin(fraction * PI.toFloat()).toFloat()
                            val y = size.height / 2f + sin(angle) * amplitude * fadeFactor
                            
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        
                        drawPath(
                            path = path,
                            brush = if (isRunning) {
                                Brush.horizontalGradient(
                                    colors = listOf(MeetColors.neonGreen, MeetColors.cyberCyan, MeetColors.electricBlue)
                                )
                            } else {
                                Brush.horizontalGradient(
                                    colors = listOf(MeetColors.textMuted, MeetColors.textSecondary)
                                )
                            },
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Status Badge & Client Counter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Glowing status label
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (isRunning) MeetColors.neonGreen.copy(alpha = statusPulseAlpha)
                                    else MeetColors.textMuted,
                                    CircleShape
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isRunning) "TRANSMITIENDO EN VIVO" else "MODO ESPERA (STANDBY)",
                            color = if (isRunning) MeetColors.neonGreen else MeetColors.textSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (isRunning) {
                        // Connected clients badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MeetColors.electricBlue.copy(alpha = 0.15f))
                                .border(1.dp, MeetColors.electricBlue.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "CLI: ${String.format("%02d", connectedClients)}",
                                color = MeetColors.electricBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Server Address Panel (Copyable)
                AnimatedVisibility(
                    visible = isRunning && serverUrl != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Text(
                            "DIRECCIÓN DE ENLACE:",
                            color = MeetColors.textSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MeetColors.backgroundDeep)
                                .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                .clickable {
                                    serverUrl?.let { url ->
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("MEET Live Link", url)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Enlace copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                serverUrl ?: "",
                                color = MeetColors.cyberCyan,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Default.ContentCopy,
                                "Copy",
                                tint = MeetColors.cyberCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Main Action Button
                EliteButton(
                    text = if (isRunning) "Detener Transmisión" else "Iniciar Transmisión",
                    onClick = {
                        if (isRunning) liveLinkServer.stop()
                        else liveLinkServer.start()
                    },
                    color = if (isRunning) MeetColors.error else MeetColors.neonGreen,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Step-by-Step Interactive Guide ──
        Text(
            "GUÍA DE CONEXIÓN",
            color = MeetColors.textSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MeetColors.cardBackground)
                .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            val instructions = listOf(
                "Conecte el móvil y su ordenador a la misma red Wi-Fi." to "Punto de Acceso Local",
                "Pulse el botón \"Iniciar Transmisión\" superior." to "Lanzamiento del Servidor",
	                "Abra el panel MEET web en su ordenador." to "Navegador Compatible",
	                "Acceda a la sección \"Live Link\" en la web." to "Panel Remoto",
	                "Pegue el enlace completo con token en el panel web." to "Sincronización Segura"
	            )

            instructions.forEachIndexed { index, (desc, title) ->
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (isRunning && index < 2) MeetColors.neonGreenSubtle
                                else MeetColors.backgroundDeep
                            )
                            .border(
                                1.dp,
                                if (isRunning && index < 2) MeetColors.neonGreen
                                else MeetColors.borderSubtle,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${index + 1}",
                            color = if (isRunning && index < 2) MeetColors.neonGreen else MeetColors.textSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            title,
                            color = if (isRunning && index < 2) MeetColors.textPrimary else MeetColors.textSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            desc,
                            color = if (isRunning && index < 2) MeetColors.textSecondary else MeetColors.textMuted,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
                if (index < instructions.size - 1) {
                    HorizontalDivider(
                        color = MeetColors.borderSubtle.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 36.dp, top = 2.dp, bottom = 2.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Broadcasted Telemetry Channels ──
        Text(
            "CANALES DE DATOS COMPARTIDOS",
            color = MeetColors.textSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MeetColors.cardBackground)
                .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            val telemetryPoints = listOf(
                "RPM Motor" to "Frecuencia rotacional en tiempo real",
                "Velocidad" to "Lectura del velocímetro del ECU",
                "Temp Refrigerante" to "Temperatura interna de operación",
                "Acelerador %" to "Posición del pedal de aceleración",
                "Carga Motor" to "Demanda del tren de potencia",
                "Voltaje Batería" to "Potencial eléctrico del sistema ELM",
                "Fuel Trims" to "Correcciones de inyección a corto/largo plazo",
                "Códigos DTC" to "Monitoreo continuo de fallas activas"
            )

            telemetryPoints.chunked(2).forEach { rowPoints ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    rowPoints.forEachIndexed { colIndex, (point, desc) ->
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MeetColors.backgroundDeep)
                                .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        if (isRunning) MeetColors.neonGreen else MeetColors.textMuted,
                                        CircleShape
                                    )
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    point,
                                    color = if (isRunning) MeetColors.textPrimary else MeetColors.textSecondary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    desc,
                                    color = MeetColors.textMuted,
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp
                                )
                            }
                        }
                        if (rowPoints.size == 2 && colIndex == 0) {
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }
            }
        }
    }
}
