package com.elysium369.meet.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
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
import androidx.compose.ui.geometry.Size
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
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.neonGlow
import com.elysium369.meet.ui.theme.MeetColors
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun LiveLinkScreen(
    navController: NavController,
    liveLinkServer: LiveLinkServer,
    viewModel: ObdViewModel
) {
    val context = LocalContext.current
    val isRunningLocal by liveLinkServer.isRunning.collectAsState()
    val connectedClientsLocal by liveLinkServer.connectedClients.collectAsState()
    val serverUrlLocal by liveLinkServer.serverUrl.collectAsState()

    // Remote PRO State
    val remoteSession by viewModel.liveLinkProSession.collectAsState()
    val mechanicNotes by viewModel.mechanicNotes.collectAsState()
    val isRunningRemote = remoteSession != null

    // Oscilloscope Animation
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

    val statusPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "statusPulse"
    )

    var activeTab by remember { mutableStateOf("wifi") } // "wifi" or "remote"
    var durationSelected by remember { mutableStateOf(30) } // 15, 30, 60, 120
    var isReadOnly by remember { mutableStateOf(true) }
    var isVideoCallOptional by remember { mutableStateOf(true) }

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
                        "Live Link PRO",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isRunningLocal || isRunningRemote) MeetColors.neonGreen.copy(alpha = 0.15f)
                                else MeetColors.textMuted.copy(alpha = 0.2f)
                            )
                            .border(
                                1.dp,
                                if (isRunningLocal || isRunningRemote) MeetColors.neonGreen.copy(alpha = 0.5f)
                                else MeetColors.textMuted,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "LIVE",
                            color = if (isRunningLocal || isRunningRemote) MeetColors.neonGreen else MeetColors.textSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Text(
                    "Compartir telemetría vehicular remota",
                    color = MeetColors.textSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // ── Tabs ──
        TabRow(
            selectedTabIndex = if (activeTab == "wifi") 0 else 1,
            containerColor = Color.Transparent,
            contentColor = MeetColors.neonGreen,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Tab(
                selected = activeTab == "wifi",
                onClick = { activeTab = "wifi" },
                text = { Text("Local (WiFi)", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = activeTab == "remote",
                onClick = { activeTab = "remote" },
                text = { Text("Remoto (PRO)", fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(Modifier.height(12.dp))

        if (activeTab == "wifi") {
            // ── Local WiFi Panel ──
            WiFiTelemetryPanel(
                isRunning = isRunningLocal,
                connectedClients = connectedClientsLocal,
                serverUrl = serverUrlLocal,
                phase = phase,
                statusPulseAlpha = statusPulseAlpha,
                liveLinkServer = liveLinkServer,
                context = context
            )
        } else {
            // ── Remote PRO Panel ──
            RemoteTelemetryPanel(
                isRunning = isRunningRemote,
                session = remoteSession,
                notes = mechanicNotes,
                duration = durationSelected,
                onDurationChange = { durationSelected = it },
                readOnly = isReadOnly,
                onReadOnlyChange = { isReadOnly = it },
                videoCall = isVideoCallOptional,
                onVideoCallChange = { isVideoCallOptional = it },
                onStartSession = {
                    viewModel.startLiveLinkPro(durationSelected, isReadOnly, isVideoCallOptional)
                },
                onStopSession = {
                    viewModel.stopLiveLinkPro()
                },
                context = context
            )
        }
    }
}

@Composable
private fun WiFiTelemetryPanel(
    isRunning: Boolean,
    connectedClients: Int,
    serverUrl: String?,
    phase: Float,
    statusPulseAlpha: Float,
    liveLinkServer: LiveLinkServer,
    context: Context
) {
    EliteCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        glowColor = if (isRunning) MeetColors.neonGreen else null,
        backgroundColor = MeetColors.cardBackground,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MeetColors.backgroundDeep)
                    .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val gridSpacing = 20.dp.toPx()
                    val gridColor = MeetColors.borderSubtle.copy(alpha = 0.4f)
                    var x = 0f
                    while (x < size.width) {
                        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.8f)
                        x += gridSpacing
                    }
                    var y = 0f
                    while (y < size.height) {
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.8f)
                        y += gridSpacing
                    }
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val points = 120
                    val path = Path()
                    val amplitude = if (isRunning) 28f else 4f
                    val frequency = if (isRunning) 4.5f else 1.2f
                    for (i in 0..points) {
                        val x = i * size.width / points
                        val fraction = i.toFloat() / points
                        val angle = fraction * 2f * PI.toFloat() * frequency + phase
                        val fadeFactor = sin(fraction * PI.toFloat()).toFloat()
                        val y = size.height / 2f + sin(angle) * amplitude * fadeFactor
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        brush = if (isRunning) {
                            Brush.horizontalGradient(listOf(MeetColors.neonGreen, MeetColors.cyberCyan, MeetColors.electricBlue))
                        } else {
                            Brush.horizontalGradient(listOf(MeetColors.textMuted, MeetColors.textSecondary))
                        },
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (isRunning) MeetColors.neonGreen.copy(alpha = statusPulseAlpha) else MeetColors.textMuted, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isRunning) "TRANSMITIENDO EN VIVO" else "MODO ESPERA (WIFI)",
                        color = if (isRunning) MeetColors.neonGreen else MeetColors.textSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (isRunning) {
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

            if (isRunning && serverUrl != null) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Text("DIRECCIÓN DE ENLACE:", color = MeetColors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MeetColors.backgroundDeep)
                            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("MEET Live Link", serverUrl))
                                Toast.makeText(context, "Enlace copiado al portapapeles", Toast.LENGTH_SHORT).show()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            serverUrl,
                            color = MeetColors.cyberCyan,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ContentCopy, "Copy", tint = MeetColors.cyberCyan, modifier = Modifier.size(16.dp))
                    }
                }
            }

            EliteButton(
                text = if (isRunning) "Detener Transmisión" else "Iniciar Transmisión Local",
                onClick = {
                    if (isRunning) liveLinkServer.stop()
                    else liveLinkServer.start()
                },
                color = if (isRunning) MeetColors.error else MeetColors.neonGreen,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun RemoteTelemetryPanel(
    isRunning: Boolean,
    session: com.elysium369.meet.data.local.entities.LiveSessionEntity?,
    notes: List<com.elysium369.meet.data.local.entities.MechanicNoteEntity>,
    duration: Int,
    onDurationChange: (Int) -> Unit,
    readOnly: Boolean,
    onReadOnlyChange: (Boolean) -> Unit,
    videoCall: Boolean,
    onVideoCallChange: (Boolean) -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    context: Context
) {
    if (!isRunning || session == null) {
        // Configuration View
        EliteCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            glowColor = MeetColors.cyberCyan,
            backgroundColor = MeetColors.cardBackground,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("CONFIGURACIÓN DE TELEMEDICINA PRO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)

                // Duration Selector
                Column {
                    Text("Duración de la Sesión", color = MeetColors.textSecondary, fontSize = 12.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 60, 120).forEach { mins ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (duration == mins) MeetColors.cyberCyan.copy(alpha = 0.2f) else MeetColors.backgroundDeep)
                                    .border(1.dp, if (duration == mins) MeetColors.cyberCyan else MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                    .clickable { onDurationChange(mins) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${mins} Min", color = if (duration == mins) MeetColors.cyberCyan else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = readOnly, onCheckedChange = onReadOnlyChange, colors = CheckboxDefaults.colors(checkedColor = MeetColors.cyberCyan))
                    Text("Acceso de Solo Lectura (Recomendado)", color = Color.White, fontSize = 12.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = videoCall, onCheckedChange = onVideoCallChange, colors = CheckboxDefaults.colors(checkedColor = MeetColors.cyberCyan))
                    Text("Activar arquitectura de Videollamada (WebRTC)", color = Color.White, fontSize = 12.sp)
                }

                EliteButton(
                    text = "Iniciar Sesión LiveLink PRO",
                    onClick = onStartSession,
                    color = MeetColors.cyberCyan,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else {
        // Active Session View
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            EliteCard(
                glowColor = MeetColors.neonGreen,
                backgroundColor = MeetColors.cardBackground,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("SESIÓN DE DIAGNÓSTICO ACTIVA", color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 13.sp)

                    // Large Monospace Code Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MeetColors.backgroundDeep)
                            .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val formattedCode = if (session.sessionCode.length == 6) {
                            "${session.sessionCode.substring(0, 3)} ${session.sessionCode.substring(3, 6)}"
                        } else session.sessionCode

                        Text(
                            formattedCode,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 32.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        )
                    }

                    // QR Code Drawing
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(130.dp)) {
                            // Draw 3 finder patterns in corners
                            val stroke = 3.dp.toPx()
                            val cornerSize = 25.dp.toPx()
                            val innerSize = 9.dp.toPx()

                            drawRect(Color.Black, Offset(0f, 0f), Size(cornerSize, cornerSize), style = Stroke(stroke))
                            drawRect(Color.Black, Offset(6.dp.toPx(), 6.dp.toPx()), Size(innerSize, innerSize))

                            drawRect(Color.Black, Offset(size.width - cornerSize, 0f), Size(cornerSize, cornerSize), style = Stroke(stroke))
                            drawRect(Color.Black, Offset(size.width - cornerSize + 6.dp.toPx(), 6.dp.toPx()), Size(innerSize, innerSize))

                            drawRect(Color.Black, Offset(0f, size.height - cornerSize), Size(cornerSize, cornerSize), style = Stroke(stroke))
                            drawRect(Color.Black, Offset(6.dp.toPx(), size.height - cornerSize + 6.dp.toPx()), Size(innerSize, innerSize))

                            // Draw mock matrix blocks based on session code hash
                            val random = java.util.Random(session.sessionCode.hashCode().toLong())
                            for (i in 0..15) {
                                val rx = random.nextInt(size.width.toInt() - 40) + 20
                                val ry = random.nextInt(size.height.toInt() - 40) + 20
                                drawRect(Color.Black, Offset(rx.toFloat(), ry.toFloat()), Size(6.dp.toPx(), 6.dp.toPx()))
                            }
                        }
                    }

                    Text("Pide al mecánico escanear este QR o ingresar el código", color = MeetColors.textSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val shareIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, "MEET LiveLink PRO — Conéctate a mi telemetría: ${session.shareUrl}")
                                    type = "text/plain"
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Compartir LiveLink PRO"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, "Share", tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Compartir", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onStopSession,
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Finalizar", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Real-time Notes
            Text(
                "RECOMENDACIONES DEL MECÁNICO EN VIVO",
                color = MeetColors.cyberCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            if (notes.isEmpty()) {
                EliteCard(
                    glowColor = null,
                    backgroundColor = MeetColors.cardBackground,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Esperando notas del mecánico...", color = MeetColors.textMuted, fontSize = 12.sp)
                    }
                }
            } else {
                notes.forEach { note ->
                    EliteCard(
                        glowColor = MeetColors.neonGreen,
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(note.content, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text("Enviado: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(note.createdAt))}", color = MeetColors.textMuted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
