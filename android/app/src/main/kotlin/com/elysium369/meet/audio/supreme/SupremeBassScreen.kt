package com.elysium369.meet.audio.supreme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.theme.MeetColors
import kotlin.math.roundToInt

@Composable
fun SupremeBassScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val state by SupremeBassController.state.collectAsState()
    var percent by remember { mutableIntStateOf(SupremeBassController.percentage(context)) }
    fun commit(value: Int) {
        percent = SupremeBoostPolicy.clamp(value)
        SupremeBassController.save(context, percent)
        if (state.active) SupremeBassController.start(context, percent)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Text("← Volver") }
        Text("SupremeBass Neon", style = MaterialTheme.typography.headlineMedium, color = MeetColors.textPrimary)
        Text("Audio · boost simplificado", color = MeetColors.textSecondary)
        EliteCard(glowColor = MeetColors.cyberCyan, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("$percent%", style = MaterialTheme.typography.displayMedium, color = MeetColors.cyberCyan)
                Text("Ganancia solicitada", color = MeetColors.textSecondary)
                Slider(value = percent.toFloat(), onValueChange = { percent = it.roundToInt() }, valueRange = 100f..400f, onValueChangeFinished = { commit(percent) })
                SupremeBoostPolicy.presets.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { value ->
                            OutlinedButton(onClick = { commit(value) }, modifier = Modifier.weight(1f)) { Text("$value%") }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Button(onClick = { if (state.active) SupremeBassController.stop(context) else SupremeBassController.start(context, percent) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.active) "Desactivar" else "Activar boost")
                }
                Text(state.message, color = if (state.connected) MeetColors.success else MeetColors.textSecondary)
                Text("100% conserva la ganancia original. Android puede limitar o rechazar los efectos sobre otras aplicaciones. La cifra elegida no mide el volumen real.", color = MeetColors.textSecondary, style = MaterialTheme.typography.bodySmall)
                if (percent > 200) Text("Una ganancia alta puede saturar el audio. Reduce el ajuste si escuchas distorsión.", color = MeetColors.warning, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { commit(100); SupremeBassController.stop(context) }) { Text("Restablecer y apagar") }
            }
        }
        Text("Basado en SupremeBass-Neon v1.2.0 · integración nativa en Elysium", color = MeetColors.textMuted, style = MaterialTheme.typography.bodySmall)
    }
}
