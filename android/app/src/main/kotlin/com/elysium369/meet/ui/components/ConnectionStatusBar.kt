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

    val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
    val ledPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (state == ObdState.CONNECTED) 2000 else 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "ledPulse"
    )

    val stateColor = when (state) {
        ObdState.CONNECTED -> Color(0xFF39FF14)
        ObdState.CONNECTING, ObdState.NEGOTIATING -> Color(0xFFFFD700)
        ObdState.ERROR -> Color(0xFFFF003C)
        else -> Color(0xFF555577)
    }

    val bgBrush = Brush.horizontalGradient(
        listOf(stateColor.copy(alpha = 0.08f), Color(0xFF0A0E1A), stateColor.copy(alpha = 0.04f))
    )

    Surface(
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth().background(bgBrush)
            .border(0.5.dp, Brush.horizontalGradient(listOf(stateColor.copy(alpha = 0.3f), Color.Transparent, stateColor.copy(alpha = 0.15f))), RoundedCornerShape(0.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
                        ObdState.CONNECTING -> "CONECTANDO..."
                        ObdState.NEGOTIATING -> "NEGOCIANDO PROTOCOLO"
                        ObdState.CONNECTED -> "ENLACE ACTIVO"
                        ObdState.ERROR -> "ERROR DE CONEXIÓN"
                    },
                    color = stateColor, fontSize = 10.sp, fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (statusMessage.isNotEmpty()) {
                    Text(statusMessage, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            if (showQos && state == ObdState.CONNECTED) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    QosMetric(String.format("%.0f", qos.cmdsPerSecond), "cmd/s", Color.White)
                    QosMetric("${qos.latencyMs}", "ms", if (qos.latencyMs < 200) Color(0xFF39FF14) else if (qos.latencyMs < 500) Color(0xFFFFD700) else Color(0xFFFF003C))
                    QosMetric(String.format("%.0f%%", qos.reliability), "fiab.", if (qos.reliability > 95) Color(0xFF39FF14) else if (qos.reliability > 80) Color(0xFFFFD700) else Color(0xFFFF003C))
                }
            }

            if (state == ObdState.CONNECTING || state == ObdState.NEGOTIATING) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFFFFD700), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun QosMetric(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Text(label, color = Color.Gray, fontSize = 7.sp, fontWeight = FontWeight.Bold)
    }
}
