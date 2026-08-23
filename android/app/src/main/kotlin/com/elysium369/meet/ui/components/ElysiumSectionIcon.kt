package com.elysium369.meet.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors

@Composable
fun ElysiumSectionIcon(
    key: String,
    contentDescription: String?,
    tint: Color = MeetColors.cyberCyan,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    fallbackGlyph: String? = null,
    preset: AnimatedIconPreset = AnimatedIconPreset.AUTO
) {
    val iconVector = remember(key) { elysiumIconVector(key) }
    if (iconVector != null) {
        AnimatedNeonIcon(
            imageVector = iconVector,
            contentDescription = contentDescription,
            tint = tint,
            preset = preset,
            modifier = modifier.size(size)
        )
    } else {
        AnimatedNeonGlyph(
            glyph = fallbackGlyph ?: "◆",
            contentDescription = contentDescription,
            tint = tint,
            preset = preset,
            fontSize = (size.value * 0.82f).sp,
            modifier = modifier.size(size)
        )
    }
}

private fun elysiumIconVector(rawKey: String): ImageVector? {
    val key = rawKey.lowercase().substringBefore("/")
    return when (key) {
        "home", "inicio" -> Icons.Default.Home
        "scanner", "terminal", "active_tests", "resets", "service_resets", "tools" -> Icons.Default.Build
        "dtc", "dtcs", "warning", "alert", "alerts" -> Icons.Default.Warning
        "meet_perito", "perito", "pre_purchase", "shield", "verified" -> Icons.Default.CheckCircle
        "meet_dna", "dna", "holo_local_read", "theme" -> Icons.Default.AutoAwesome
        "component_locator", "motor_3d", "dashboard", "custom_pid" -> Icons.Default.Dashboard
        "settings", "ajustes" -> Icons.Default.Settings
        "findings", "search" -> Icons.Default.Search
        "garage", "vehicle", "vehicles", "car" -> Icons.Default.List
        "ai", "health_score", "expert_diagnostic" -> Icons.Default.Psychology
        "messages", "support_chat", "support", "fleet_chat_list", "chat" -> Icons.Default.Chat
        "reports", "dvir", "manuals", "vehicle_manuals", "provider_registration" -> Icons.Default.AssignmentInd
        "hud", "performance", "speed" -> Icons.Default.Speed
        "dashcam", "oscilloscope" -> Icons.Default.Tune
        "maintenance" -> Icons.Default.CalendarMonth
        "trips", "topology" -> Icons.Default.Timeline
        "live_link", "repair_network" -> Icons.Default.CloudQueue
        "pro_hub", "pro" -> Icons.Default.Star
        else -> null
    }
}
