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

    val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
    val ledPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (state == ObdState.CONNECTED) 2000 else 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "ledPulse"
    )

    val stateColor = when (state) {
        ObdState.CONNECTED -> com.elysium369.meet.ui.theme.MeetColors.neonGreen
        ObdState.CONNECTING, ObdState.NEGOTIATING -> com.elysium369.meet.ui.theme.MeetColors.warning
        ObdState.ERROR -> com.elysium369.meet.ui.theme.MeetColors.error
        else -> MeetColors.textMuted
    }

    val bgBrush = Brush.horizontalGradient(
        listOf(stateColor.copy(alpha = 0.08f), com.elysium369.meet.ui.theme.MeetColors.backgroundDark, stateColor.copy(alpha = 0.04f))
    )

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
                            ObdState.DISCONNECTED -> "DESCONECTADO"
                            ObdState.CONNECTING -> if (statusMessage.contains("Reintentando") || statusMessage.contains("Intento")) "RECONECTANDO..." else "CONECTANDO..."
                            ObdState.NEGOTIATING -> "NEGOCIANDO PROTOCOLO"
                            ObdState.CONNECTED -> "ENLACE ACTIVO"
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

                if (state == ObdState.CONNECTING || state == ObdState.NEGOTIATING) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = com.elysium369.meet.ui.theme.MeetColors.warning, strokeWidth = 2.dp)
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
                    val elmConnected = state == ObdState.NEGOTIATING || state == ObdState.CONNECTED
                    val elmColor = if (elmConnected) com.elysium369.meet.ui.theme.MeetColors.neonGreen else if (state == ObdState.CONNECTING) com.elysium369.meet.ui.theme.MeetColors.warning else com.elysium369.meet.ui.theme.MeetColors.error
                    val elmText = when {
                        state == ObdState.CONNECTED || state == ObdState.NEGOTIATING -> "Conectado"
                        state == ObdState.CONNECTING -> "Conectando"
                        state == ObdState.ERROR -> "Error"
                        else -> "—"
                    }
                    DualStatusItem("ELM:", elmText, elmColor, adapterVer.ifBlank { null })

                    // ECU Connection Status
                    val ecuConnected = state == ObdState.CONNECTED
                    val ecuColor = when {
                        ecuConnected -> com.elysium369.meet.ui.theme.MeetColors.neonGreen
                        state == ObdState.NEGOTIATING -> com.elysium369.meet.ui.theme.MeetColors.warning
                        state == ObdState.CONNECTING -> MeetColors.textMuted
                        state == ObdState.ERROR -> com.elysium369.meet.ui.theme.MeetColors.error
                        else -> MeetColors.textMuted
                    }
                    val ecuText = when {
                        ecuConnected -> "Conectada"
                        state == ObdState.NEGOTIATING -> "Negociando"
                        state == ObdState.ERROR -> "Error"
                        else -> "Esperando"
                    }
                    DualStatusItem("ECU:", ecuText, ecuColor, if (protocol.isNotBlank() && ecuConnected) protocol else null)
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
