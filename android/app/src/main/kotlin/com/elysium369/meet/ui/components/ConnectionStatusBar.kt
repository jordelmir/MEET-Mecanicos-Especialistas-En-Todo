package com.elysium369.meet.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.ui.ObdViewModel

@Composable
fun ConnectionStatusBar(viewModel: ObdViewModel) {
    val state by viewModel.connectionState.collectAsState()
    val latencyMs = 45
    val voltage = 13.8f
    val protocol = "ISO 15765-4 CAN"
    val adapterName = "OBDII v1.5"

    AnimatedVisibility(visible = state != ObdState.DISCONNECTED) {
        Surface(color = when(state) {
            ObdState.CONNECTED -> Color(0xFF00FFCC).copy(alpha = 0.15f)
            ObdState.CONNECTING, ObdState.NEGOTIATING -> Color(0xFFFFD700).copy(alpha = 0.15f)
            ObdState.ERROR -> Color(0xFFFF003C).copy(alpha = 0.15f)
            else -> Color.Transparent
        }, modifier = Modifier.fillMaxWidth().height(28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.padding(horizontal = 12.dp)) {
                when(state) {
                    ObdState.CONNECTING, ObdState.NEGOTIATING -> { CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color(0xFFFFD700), strokeWidth = 2.dp) }
                    ObdState.CONNECTED -> { Text("●", color = Color(0xFF00FFCC), style = MaterialTheme.typography.labelSmall) }
                    ObdState.ERROR -> { Text("●", color = Color(0xFFFF003C), style = MaterialTheme.typography.labelSmall) }
                    else -> { Text("BT", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                }
                Text(adapterName, style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Text(protocol, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text("${latencyMs}ms", style = MaterialTheme.typography.labelSmall, color = Color(0xFF00FFCC))
                Text("${voltage}V", style = MaterialTheme.typography.labelSmall, color = if (voltage < 11.5f) Color(0xFFFF003C) else Color(0xFF00FFCC))
            }
        }
    }
}
