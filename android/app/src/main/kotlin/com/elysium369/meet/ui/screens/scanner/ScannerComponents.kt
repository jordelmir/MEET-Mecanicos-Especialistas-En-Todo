package com.elysium369.meet.ui.screens.scanner

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.elysium369.meet.ui.theme.MeetColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.obd.DtcDecoder

import com.elysium369.meet.ui.components.EliteCard

@Composable
fun DtcStatCard(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    EliteCard(
        backgroundColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark,
        borderColor = color.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        glowColor = color.copy(alpha = 0.2f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = color.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text("$count", color = color, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun DtcItemCard(
    code: String, 
    type: String, 
    color: Color, 
    description: String = "Consultando diagnóstico...",
    occurrenceCount: Int = 1,
    lastSeenAt: Long = 0L
) {
    val timeStr = if (lastSeenAt > 0) {
        val diff = System.currentTimeMillis() - lastSeenAt
        when {
            diff < 60000 -> "Detectado ahora"
            diff < 3600000 -> "Hace ${diff / 60000} min"
            diff < 86400000 -> "Hace ${diff / 3600000} h"
            else -> "Detectado hace ${diff / 86400000} d"
        }
    } else ""

    EliteCard(
        backgroundColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark,
        borderColor = color.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        glowColor = color.copy(alpha = 0.2f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(code, color = color, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    com.elysium369.meet.ui.components.EliteCard(backgroundColor = color.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                        Text(type.uppercase(), color = color, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    if (occurrenceCount > 1) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("x$occurrenceCount", color = MeetColors.textSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (timeStr.isNotEmpty()) {
                        Text(timeStr, color = MeetColors.textSecondary.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(description, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ToolCard(icon: String, title: String, desc: String, color: Color, onClick: () -> Unit) {
    EliteCard(
        backgroundColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark,
        borderColor = color.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        glowColor = color.copy(alpha = 0.5f)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(desc, color = MeetColors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
