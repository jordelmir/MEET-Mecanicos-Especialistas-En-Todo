package com.elysium369.meet.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.elysium369.meet.ui.theme.MeetColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.ui.ObdViewModel

@Composable
fun ConnectionStatusBar(
    viewModel: ObdViewModel,
    modifier: Modifier = Modifier,
    showQos: Boolean = false
) {
    val state by viewModel.connectionState.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val qos by viewModel.qosMetrics.collectAsState()
    val protocol by viewModel.detectedProtocol.collectAsState()
    val adapterVer by viewModel.adapterVersion.collectAsState()
    val isClone by viewModel.isCloneAdapter.collectAsState()
    val activeOperations by viewModel.activeOperations.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
    val ledPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (state == ObdState.CONNECTED) 2000 else 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "ledPulse"
    )

    val truth by viewModel.connectionTruth.collectAsState()
    var showDiagnosticsSheet by remember { mutableStateOf(false) }

    val stateColor = when {
        truth.isDemoSession && state == ObdState.CONNECTED -> com.elysium369.meet.ui.theme.MeetColors.electricBlue
        state == ObdState.CONNECTED && truth.isSessionReady -> com.elysium369.meet.ui.theme.MeetColors.neonGreen
        state == ObdState.CONNECTED && truth.ecuState == com.elysium369.meet.core.obd.EcuLinkState.NO_RESPONSE -> com.elysium369.meet.ui.theme.MeetColors.warning
        state == ObdState.CONNECTING || state == ObdState.NEGOTIATING -> com.elysium369.meet.ui.theme.MeetColors.warning
        state == ObdState.ERROR -> com.elysium369.meet.ui.theme.MeetColors.error
        else -> MeetColors.textMuted
    }

    val bgBrush = Brush.horizontalGradient(
        listOf(stateColor.copy(alpha = 0.08f), com.elysium369.meet.ui.theme.MeetColors.backgroundDark, stateColor.copy(alpha = 0.04f))
    )

    if (showDiagnosticsSheet) {
        ConnectionDiagnosticsSheet(viewModel = viewModel, onDismiss = { showDiagnosticsSheet = false })
    }

    Surface(
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth().background(bgBrush)
            .border(0.5.dp, Brush.horizontalGradient(listOf(stateColor.copy(alpha = 0.3f), Color.Transparent, stateColor.copy(alpha = 0.15f))), RoundedCornerShape(0.dp))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            // ── Row 1: Main status + QoS ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // LED
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(20.dp)) {
                    Box(modifier = Modifier.size((14 + 6 * ledPulse).dp).clip(CircleShape).background(stateColor.copy(alpha = 0.15f * ledPulse)))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(stateColor.copy(alpha = 0.4f + 0.6f * ledPulse)))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (state) {
                            ObdState.DISCONNECTED -> if (truth.disconnectReason == com.elysium369.meet.core.obd.DisconnectReason.REMOTE_CLOSED || truth.disconnectReason == com.elysium369.meet.core.obd.DisconnectReason.IO_FAILURE) "ESCÁNER DESCONECTADO" else "DESCONECTADO"
                            ObdState.CONNECTING -> if (statusMessage.contains("Reintentando") || statusMessage.contains("Intento")) "RECONECTANDO..." else "CONECTANDO..."
                            ObdState.NEGOTIATING -> "NEGOCIANDO PROTOCOLO"
                            ObdState.CONNECTED -> when {
                                truth.isDemoSession -> "DEMO / SIMULACIÓN"
                                truth.isSessionReady -> "SESIÓN OBD VERIFICADA"
                                truth.ecuState == com.elysium369.meet.core.obd.EcuLinkState.NO_RESPONSE -> "ENLACE DEGRADADO (ECU EN SILENCIO)"
                                else -> "ENLACE NO VERIFICADO"
                            }
                            ObdState.ERROR -> "ERROR DE CONEXIÓN"
                        },
                        color = stateColor, fontSize = 10.sp, fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (statusMessage.isNotEmpty()) {
                        Text(statusMessage, color = com.elysium369.meet.ui.theme.MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                if (showQos && state == ObdState.CONNECTED) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        QosMetric(String.format("%.0f", qos.cmdsPerSecond), "cmd/s", Color.White)
                        QosMetric("${qos.latencyMs}", "ms", if (qos.latencyMs < 200) com.elysium369.meet.ui.theme.MeetColors.neonGreen else if (qos.latencyMs < 500) com.elysium369.meet.ui.theme.MeetColors.warning else com.elysium369.meet.ui.theme.MeetColors.neonGreen)
                        QosMetric(String.format("%.0f%%", qos.reliability), "fiab.", if (qos.reliability > 95) com.elysium369.meet.ui.theme.MeetColors.neonGreen else if (qos.reliability > 80) com.elysium369.meet.ui.theme.MeetColors.warning else com.elysium369.meet.ui.theme.MeetColors.neonGreen)
                    }
                }

                if (activeOperations.size > 1) {
                    Text(
                        text = "${activeOperations.size} OPERACIONES",
                        color = MeetColors.electricBlue,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                    )
                }

                if (state == ObdState.CONNECTING || state == ObdState.NEGOTIATING) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = com.elysium369.meet.ui.theme.MeetColors.warning, strokeWidth = 2.dp)
                    com.elysium369.meet.ui.components.EliteTextButton(
                        text = "CANCELAR",
                        onClick = { viewModel.cancelConnection() },
                        color = com.elysium369.meet.ui.theme.MeetColors.warning
                    )
                } else if (state == ObdState.CONNECTED) {
                    com.elysium369.meet.ui.components.EliteTextButton(
                        text = "DESCONECTAR",
                        onClick = { viewModel.disconnect() },
                        color = com.elysium369.meet.ui.theme.MeetColors.error
                    )
                } else if (state == ObdState.ERROR) {
                    com.elysium369.meet.ui.components.EliteTextButton(
                        text = "REINICIAR",
                        onClick = { viewModel.forceResetConnection() },
                        color = com.elysium369.meet.ui.theme.MeetColors.hotMagenta
                    )
                } else if (state == ObdState.DISCONNECTED &&
                    (truth.disconnectReason == com.elysium369.meet.core.obd.DisconnectReason.REMOTE_CLOSED ||
                        truth.disconnectReason == com.elysium369.meet.core.obd.DisconnectReason.IO_FAILURE)
                ) {
                    com.elysium369.meet.ui.components.EliteTextButton(
                        text = "REINTENTAR",
                        onClick = { viewModel.retryLastAdapterByUserAction() },
                        color = com.elysium369.meet.ui.theme.MeetColors.warning
                    )
                }
            }

            // ── Row 2: Dual Connection Status (ELM + ECU) — Only when connecting or connected ──
            if (state != ObdState.DISCONNECTED) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ELM Connection Status
                    val elmConnected = truth.elmState == com.elysium369.meet.core.obd.ElmLinkState.READY ||
                        truth.elmState == com.elysium369.meet.core.obd.ElmLinkState.NOT_APPLICABLE
                    val elmColor = if (elmConnected) com.elysium369.meet.ui.theme.MeetColors.neonGreen else if (state == ObdState.CONNECTING) com.elysium369.meet.ui.theme.MeetColors.warning else com.elysium369.meet.ui.theme.MeetColors.error
                    val elmText = when {
                        truth.elmState == com.elysium369.meet.core.obd.ElmLinkState.READY -> "Listo"
                        truth.elmState == com.elysium369.meet.core.obd.ElmLinkState.NOT_APPLICABLE -> "No aplica (DoIP)"
                        truth.elmState == com.elysium369.meet.core.obd.ElmLinkState.SYNCING -> "Sincronizando"
                        state == ObdState.CONNECTING -> "Conectando"
                        state == ObdState.ERROR -> "Error"
                        else -> "—"
                    }
                    DualStatusItem("ELM:", elmText, elmColor, adapterVer.ifBlank { null })

                    // ECU Connection Status
                    val ecuResponsive = truth.ecuState == com.elysium369.meet.core.obd.EcuLinkState.RESPONSIVE
                    val ecuColor = when {
                        ecuResponsive -> com.elysium369.meet.ui.theme.MeetColors.neonGreen
                        truth.ecuState == com.elysium369.meet.core.obd.EcuLinkState.NO_RESPONSE -> com.elysium369.meet.ui.theme.MeetColors.warning
                        state == ObdState.NEGOTIATING -> com.elysium369.meet.ui.theme.MeetColors.warning
                        state == ObdState.CONNECTING -> MeetColors.textMuted
                        state == ObdState.ERROR -> com.elysium369.meet.ui.theme.MeetColors.error
                        else -> MeetColors.textMuted
                    }
                    val ecuText = when {
                        ecuResponsive -> "Conectada"
                        truth.ecuState == com.elysium369.meet.core.obd.EcuLinkState.NO_RESPONSE -> "Sin respuesta"
                        state == ObdState.NEGOTIATING -> "Negociando"
                        state == ObdState.ERROR -> "Error"
                        else -> "Esperando"
                    }
                    DualStatusItem("ECU:", ecuText, ecuColor, if (protocol.isNotBlank() && (ecuResponsive || state == ObdState.CONNECTED)) protocol else null)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val transportConnected = truth.transportState is com.elysium369.meet.core.obd.TransportLinkState.Connected
                    DualStatusItem(
                        "TRANSPORTE:",
                        if (truth.isDemoSession) "Demo" else if (transportConnected) "Conectado" else "No conectado",
                        if (truth.isDemoSession) MeetColors.electricBlue else if (transportConnected) MeetColors.neonGreen else MeetColors.textMuted,
                        null
                    )
                    val telemetryLive = truth.telemetryState == com.elysium369.meet.core.obd.TelemetryLinkState.ACTIVE
                    DualStatusItem(
                        "DATOS:",
                        if (telemetryLive) "En vivo" else if (truth.telemetryState == com.elysium369.meet.core.obd.TelemetryLinkState.STALE) "Pausados" else "Inactivos",
                        if (telemetryLive) MeetColors.neonGreen else if (truth.telemetryState == com.elysium369.meet.core.obd.TelemetryLinkState.STALE) MeetColors.warning else MeetColors.textMuted,
                        null
                    )
                }

                // ── Row 3: Clone Warning ──
                if (isClone && (state == ObdState.CONNECTED || state == ObdState.NEGOTIATING) && adapterVer.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "⚠ ELM327 clon detectado",
                        color = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
                if (showQos) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Recuperaciones ${truth.softRecoveryCount}/${truth.protocolRecoveryCount} · Pérdidas ${truth.physicalLinkLossCount}" +
                                (truth.disconnectReason?.let { " · Última: $it" } ?: ""),
                            color = MeetColors.textMuted,
                            fontSize = 8.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            com.elysium369.meet.ui.components.EliteTextButton(
                                text = "PANEL FORENSE",
                                onClick = { showDiagnosticsSheet = true },
                                color = MeetColors.neonGreen
                            )
                            com.elysium369.meet.ui.components.EliteTextButton(
                                text = "COPIAR TRACE",
                                onClick = { viewModel.copyRedactedConnectionTrace() },
                                color = MeetColors.electricBlue
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DualStatusItem(label: String, status: String, color: Color, detail: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = com.elysium369.meet.ui.theme.MeetColors.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(status, color = color, fontSize = 9.sp, fontWeight = FontWeight.Black)
        if (detail != null) {
            Text("($detail)", color = com.elysium369.meet.ui.theme.MeetColors.textMuted, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun QosMetric(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Text(label, color = com.elysium369.meet.ui.theme.MeetColors.textMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
    }
}
