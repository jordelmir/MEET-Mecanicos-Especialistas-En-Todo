package com.elysium.vanguard.features.telemetry

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elysium.vanguard.ui.theme.TitanColors
import com.elysium.vanguard.ui.theme.reactorGlass
import com.elysium.vanguard.ui.theme.pulsingNeonBorder
import androidx.compose.animation.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

// --- UI LAYER ---
@Composable
fun TelemetryHUD(
    modifier: Modifier = Modifier
) {
    val viewModel: TelemetryViewModel = hiltViewModel()
    val metrics by viewModel.monitor.metrics.collectAsState(initial = SystemMetrics())

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MetricWidget(
            label = "CORE_TMP",
            value = "${metrics.cpuTemp}°C",
            color = if (metrics.cpuTemp > 45) TitanColors.NeonRed else TitanColors.NeonCyan
        )
        MetricWidget(
            label = "SYST_FPS",
            value = "${metrics.fps}",
            color = TitanColors.NeonGreen
        )
        MetricWidget(
            label = "MEM_LOAD",
            value = "${metrics.ramUsagePercent}%",
            color = TitanColors.NeonCyan,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MetricWidget(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "metric_$label")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "metric_glow"
    )

    Box(
        modifier = modifier
            .pulsingNeonBorder(
                cornerRadius = 12.dp,
                glowColor = color,
                glassAlpha = 0.15f
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Text(
                text = value,
                color = color.copy(alpha = glowAlpha),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
