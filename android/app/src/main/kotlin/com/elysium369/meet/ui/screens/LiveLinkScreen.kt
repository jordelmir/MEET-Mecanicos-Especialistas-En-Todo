package com.elysium369.meet.ui.screens

import com.elysium369.meet.ui.navigation.backOrHome

import com.elysium369.meet.ui.components.AnimatedNeonIcon

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.core.livelink.LiveLinkAccessCredentials
import com.elysium369.meet.core.livelink.LiveLinkChatMessage
import com.elysium369.meet.core.livelink.LiveLinkEvent
import com.elysium369.meet.core.livelink.LiveLinkPermission
import com.elysium369.meet.core.livelink.LiveLinkRemoteRequest
import com.elysium369.meet.core.livelink.LiveLinkReport
import com.elysium369.meet.core.livelink.LiveLinkServer
import com.elysium369.meet.core.livelink.LiveLinkSession
import com.elysium369.meet.core.livelink.LiveLinkTelemetryPacket
import com.elysium369.meet.core.livelink.LiveLinkRequestStatus
import com.elysium369.meet.core.livelink.LiveLinkSourceQuality
import com.elysium369.meet.core.share.QrCodeImage
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.neonGlow
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.delay
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
    val coreSession by viewModel.liveLinkProCoreSession.collectAsState()
    val credentials by viewModel.liveLinkProCredentials.collectAsState()
    val permissions by viewModel.liveLinkProPermissions.collectAsState()
    val events by viewModel.liveLinkProEvents.collectAsState()
    val requests by viewModel.liveLinkProRequests.collectAsState()
    val chatMessages by viewModel.liveLinkProMessages.collectAsState()
    val latestPacket by viewModel.liveLinkProLatestPacket.collectAsState()
    val report by viewModel.liveLinkProReport.collectAsState()
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
                onClick = { navController.backOrHome() },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MeetColors.cardBackground)
                    .border(1.dp, MeetColors.borderSubtle, CircleShape)
            ) {
                AnimatedNeonIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
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
                coreSession = coreSession,
                credentials = credentials,
                permissions = permissions,
                latestPacket = latestPacket,
                events = events,
                requests = requests,
                chatMessages = chatMessages,
                report = report,
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
                onRevokeSession = {
                    viewModel.revokeLiveLinkPro()
                },
                onRequestSnapshot = {
                    viewModel.requestLiveLinkSnapshot()
                },
                onRequestCriticalAudit = {
                    viewModel.requestLiveLinkClearDtcForAudit()
                },
                onApproveRequest = { requestId ->
                    viewModel.approveLiveLinkRequest(requestId)
                },
                onDenyRequest = { requestId ->
                    viewModel.denyLiveLinkRequest(requestId)
                },
                onSendChat = { body ->
                    viewModel.sendLiveLinkChat(body)
                },
                onGenerateReport = {
                    viewModel.generateLiveLinkReport()
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
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Elysium Vanguard Live Link", serverUrl))
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
                        AnimatedNeonIcon(Icons.Default.ContentCopy, "Copy", tint = MeetColors.cyberCyan, modifier = Modifier.size(16.dp))
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
@OptIn(ExperimentalLayoutApi::class)
private fun RemoteTelemetryPanel(
    isRunning: Boolean,
    session: com.elysium369.meet.data.local.entities.LiveSessionEntity?,
    coreSession: LiveLinkSession?,
    credentials: LiveLinkAccessCredentials?,
    permissions: LiveLinkPermission?,
    latestPacket: LiveLinkTelemetryPacket?,
    events: List<LiveLinkEvent>,
    requests: List<LiveLinkRemoteRequest>,
    chatMessages: List<LiveLinkChatMessage>,
    report: LiveLinkReport?,
    notes: List<com.elysium369.meet.data.local.entities.MechanicNoteEntity>,
    duration: Int,
    onDurationChange: (Int) -> Unit,
    readOnly: Boolean,
    onReadOnlyChange: (Boolean) -> Unit,
    videoCall: Boolean,
    onVideoCallChange: (Boolean) -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onRevokeSession: () -> Unit,
    onRequestSnapshot: () -> Unit,
    onRequestCriticalAudit: () -> Unit,
    onApproveRequest: (String) -> Unit,
    onDenyRequest: (String) -> Unit,
    onSendChat: (String) -> Unit,
    onGenerateReport: () -> Unit,
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
                Text("CONFIGURACION LIVE LINK PRO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)

                // Duration Selector
                Column {
                    Text("Duracion de la sesion", color = MeetColors.textSecondary, fontSize = 12.sp)
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
                                Text("${mins}m", color = if (duration == mins) MeetColors.cyberCyan else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = readOnly, onCheckedChange = onReadOnlyChange, colors = CheckboxDefaults.colors(checkedColor = MeetColors.cyberCyan))
                    Text("Solo lectura", color = Color.White, fontSize = 12.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = videoCall, onCheckedChange = onVideoCallChange, colors = CheckboxDefaults.colors(checkedColor = MeetColors.cyberCyan))
                    Text("Video opcional, audio apagado", color = Color.White, fontSize = 12.sp)
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiveLinkChip("DTC", true)
                    LiveLinkChip("PIDs", true)
                    LiveLinkChip("Chat", true)
                    LiveLinkChip("VIN parcial", true)
                    LiveLinkChip("Ubicacion", false)
                    LiveLinkChip("Control ECU", false)
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
        var nowTick by remember(coreSession?.sessionId) { mutableStateOf(System.currentTimeMillis()) }
        var chatDraft by remember(session.sessionId) { mutableStateOf("") }
        LaunchedEffect(coreSession?.sessionId, coreSession?.state) {
            while (coreSession?.isOpen == true) {
                delay(1000L)
                nowTick = System.currentTimeMillis()
            }
        }

        val secureShareUrl = credentials?.shareUrl ?: session.shareUrl
        val sessionState = coreSession?.state?.name ?: session.status
        val remainingMs = coreSession?.timeRemainingMs(nowTick)
        val sourceQuality = latestPacket?.sourceQuality ?: LiveLinkSourceQuality.NO_REAL_OBD

        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            EliteCard(
                glowColor = if (sessionState == "REVOKED" || sessionState == "EXPIRED") MeetColors.error else MeetColors.neonGreen,
                backgroundColor = MeetColors.cardBackground,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "SESION $sessionState",
                        color = if (sessionState == "REVOKED" || sessionState == "EXPIRED") MeetColors.error else MeetColors.neonGreen,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )

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
                            letterSpacing = 0.sp
                        )
                    }

                    QrCodeImage(
                        text = secureShareUrl,
                        modifier = Modifier.size(158.dp),
                        backgroundColor = Color.White,
                        qrColor = Color.Black
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        LiveLinkChip(coreSession?.mode?.name ?: "REMOTE", true)
                        LiveLinkChip("Expira ${remainingMs?.let { formatRemainingMs(it) } ?: "--"}", remainingMs != 0L)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val shareIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, "Elysium Vanguard LiveLink PRO: $secureShareUrl")
                                    type = "text/plain"
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Compartir LiveLink PRO"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan),
                            modifier = Modifier.weight(1f)
                        ) {
                            AnimatedNeonIcon(Icons.Default.Share, "Share", tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Compartir", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("LiveLink PRO", secureShareUrl))
                                Toast.makeText(context, "Enlace copiado", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.backgroundDeep),
                            modifier = Modifier.weight(1f)
                        ) {
                            AnimatedNeonIcon(Icons.Default.ContentCopy, "Copy", tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copiar", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            EliteCard(
                glowColor = if (sourceQuality == LiveLinkSourceQuality.REAL_OBD) MeetColors.neonGreen else MeetColors.warning,
                backgroundColor = MeetColors.cardBackground,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TELEMETRIA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        LiveLinkChip(sourceQuality.name, sourceQuality == LiveLinkSourceQuality.REAL_OBD)
                    }
                    Text(
                        latestPacket?.degradedReason ?: "${latestPacket?.connectionState ?: "OBD"} / ${latestPacket?.adapterQuality ?: "--"}",
                        color = if (sourceQuality == LiveLinkSourceQuality.REAL_OBD) MeetColors.neonGreen else MeetColors.warning,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    val samples = latestPacket?.samples.orEmpty().take(8)
                    if (samples.isEmpty()) {
                        Text("OBD sin enlace real: DTC/chat/reportes siguen disponibles.", color = MeetColors.textSecondary, fontSize = 12.sp)
                    } else {
                        samples.chunked(2).forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { sample ->
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MeetColors.backgroundDeep)
                                            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                    ) {
                                        Text(sample.name.take(18), color = MeetColors.textSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            sample.value?.let { "${trimLiveLinkNumber(it)} ${sample.unit}".trim() } ?: "--",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(sample.quality.name, color = MeetColors.textMuted, fontSize = 9.sp)
                                    }
                                }
                                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            EliteCard(
                glowColor = MeetColors.cyberCyan,
                backgroundColor = MeetColors.cardBackground,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("PERMISOS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiveLinkChip("OBD", permissions?.canReadObdState == true)
                        LiveLinkChip("DTC", permissions?.canReadDtc == true)
                        LiveLinkChip("PIDs", permissions?.canReadLivePids == true)
                        LiveLinkChip("Chat", permissions?.canUseChat == true)
                        LiveLinkChip("Video", permissions?.canUseVideo == true)
                        LiveLinkChip("Audio", permissions?.canUseAudio == true)
                        LiveLinkChip("Ubicacion exacta", permissions?.canReadExactLocation == true)
                        LiveLinkChip("VIN completo", permissions?.canSeeFullVin == true)
                        LiveLinkChip("Clear DTC", permissions?.canClearDtcs == true)
                    }
                }
            }

            EliteCard(
                glowColor = MeetColors.warning,
                backgroundColor = MeetColors.cardBackground,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("SOLICITUDES", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("${requests.count { it.status == LiveLinkRequestStatus.PENDING_LOCAL_APPROVAL }} pendientes", color = MeetColors.textSecondary, fontSize = 11.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onRequestSnapshot,
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Snapshot", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Button(
                            onClick = onRequestCriticalAudit,
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.backgroundDeep),
                            modifier = Modifier.weight(1f)
                        ) {
                            AnimatedNeonIcon(Icons.Default.Warning, "Audit", tint = MeetColors.warning, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Clear DTC", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    requests.take(5).forEach { request ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MeetColors.backgroundDeep)
                                .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(request.type.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text(request.status.name, color = if (request.status == LiveLinkRequestStatus.BLOCKED) MeetColors.error else MeetColors.cyberCyan, fontSize = 10.sp)
                            }
                            Text(request.reason, color = MeetColors.textSecondary, fontSize = 11.sp)
                            if (request.status == LiveLinkRequestStatus.PENDING_LOCAL_APPROVAL) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { onDenyRequest(request.requestId) }, modifier = Modifier.weight(1f)) {
                                        Text("Denegar", color = MeetColors.error)
                                    }
                                    TextButton(onClick = { onApproveRequest(request.requestId) }, modifier = Modifier.weight(1f)) {
                                        Text("Aprobar", color = MeetColors.neonGreen)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            EliteCard(
                glowColor = null,
                backgroundColor = MeetColors.cardBackground,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("CHAT Y NOTAS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = chatDraft,
                            onValueChange = { chatDraft = it.take(240) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("Mensaje", color = MeetColors.textMuted) },
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 13.sp)
                        )
                        Button(
                            onClick = {
                                onSendChat(chatDraft)
                                chatDraft = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen)
                        ) {
                            Text("Enviar", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    val combinedNotes = chatMessages.take(6)
                    if (combinedNotes.isEmpty() && notes.isEmpty()) {
                        Text("Sin mensajes.", color = MeetColors.textMuted, fontSize = 12.sp)
                    } else {
                        combinedNotes.forEach { message ->
                            Text(
                                "${message.authorRole.name}: ${message.body}",
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (combinedNotes.isEmpty()) {
                            notes.take(3).forEach { note ->
                                Text(note.content, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            EliteCard(
                glowColor = MeetColors.electricBlue,
                backgroundColor = MeetColors.cardBackground,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onGenerateReport,
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.electricBlue),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reporte", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onRevokeSession,
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Revocar", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onStopSession,
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.backgroundDeep),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Finalizar", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    report?.let {
                        Text("Hash evidencia: ${it.evidenceHash.take(16)}...", color = MeetColors.cyberCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    Text("EVENTOS", color = MeetColors.textSecondary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    events.take(6).forEach { event ->
                        Text(
                            "${formatShortTime(event.createdAtMs)}  ${event.type.name}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveLinkChip(label: String, enabled: Boolean) {
    val color = if (enabled) MeetColors.neonGreen else MeetColors.textMuted
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = if (enabled) 0.14f else 0.10f))
            .border(1.dp, color.copy(alpha = if (enabled) 0.45f else 0.25f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            label,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatRemainingMs(value: Long): String {
    val totalSeconds = value / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}

private fun formatShortTime(value: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(value))
}

private fun trimLiveLinkNumber(value: Double): String {
    return if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(Locale.US, value)
}
