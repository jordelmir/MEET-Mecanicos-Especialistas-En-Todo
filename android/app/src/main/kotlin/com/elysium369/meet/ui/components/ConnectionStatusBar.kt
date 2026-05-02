package com.elysium369.meet.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.ui.ObdViewModel

@Composable
fun ConnectionStatusBar(viewModel: ObdViewModel) {
    val state by viewModel.connectionState.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val bgColor by animateColorAsState(
        when(state) {
            ObdState.CONNECTED -> Color(0xFF39FF14).copy(alpha = 0.15f)
            ObdState.CONNECTING, ObdState.NEGOTIATING -> Color(0xFFFFD700).copy(alpha = 0.15f)
            ObdState.ERROR -> Color(0xFFFF003C).copy(alpha = 0.15f)
            else -> Color.Transparent
        },
        label = "statusBarColor"
    )

    AnimatedVisibility(visible = state != ObdState.DISCONNECTED) {
        Surface(color = bgColor, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                when(state) {
                    ObdState.CONNECTING, ObdState.NEGOTIATING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color(0xFFFFD700),
                            strokeWidth = 2.dp
                        )
                    }
                    ObdState.CONNECTED -> {
                        Text("●", color = Color(0xFF39FF14), style = MaterialTheme.typography.labelSmall)
                    }
                    ObdState.ERROR -> {
                        Text("●", color = Color(0xFFFF003C), style = MaterialTheme.typography.labelSmall)
                    }
                    else -> {
                        Text("○", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(
                    text = statusMessage.ifEmpty {
                        when(state) {
                            ObdState.CONNECTING -> "Conectando..."
                            ObdState.NEGOTIATING -> "Negociando protocolo..."
                            ObdState.CONNECTED -> "Conectado"
                            ObdState.ERROR -> "Error de conexión"
                            else -> "Desconectado"
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
