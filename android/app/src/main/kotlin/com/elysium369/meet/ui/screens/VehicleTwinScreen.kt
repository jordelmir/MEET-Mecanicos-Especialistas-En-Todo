package com.elysium369.meet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.elysium369.meet.core.twin.TwinUiState
import com.elysium369.meet.core.twin.VehicleTwinViewModel
import com.elysium369.meet.diagnostic.DiagnosticProvenance
import com.elysium369.meet.diagnostic.DiagnosticValue
import com.elysium369.meet.data.local.entities.TwinAnomalyEntity
import com.elysium369.meet.data.local.entities.VehicleTwinProfileEntity
import com.elysium369.meet.ui.theme.MeetColors

/**
 * Vehicle Digital Twin — dedicated screen.
 *
 * The inline Twin section that lived inside MeetDnaScreen is being migrated here.
 * The single most important rule on this screen: every numeric value MUST be
 * visually labeled with its provenance. We render a sticky banner at the top
 * that shows the active source (REAL / SIMULATED / SIN ENLACE) and the UI
 * never lets simulated data masquerade as real.
 */
@Composable
fun VehicleTwinScreen(
    navController: NavController,
    viewModel: VehicleTwinViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeetColors.backgroundDark)
            .padding(16.dp)
    ) {
        // ── Top bar ───────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = MeetColors.textPrimary
                )
            }
            Text(
                "GEMELO DIGITAL",
                color = MeetColors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── Provenance banner (mandatory) ─────────────────────────────
        ProvenanceBanner(state.activeProvenance)

        Spacer(Modifier.height(12.dp))

        // ── Profile card ──────────────────────────────────────────────
        ProfileCard(state.profile)

        Spacer(Modifier.height(12.dp))

        // ── Demo controls ─────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.injectNormalFrame() },
                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan),
                modifier = Modifier.weight(1f)
            ) {
                Text("Frame normal", color = MeetColors.backgroundDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { viewModel.injectDemoFrame(pid = "0142", anomalousValue = 11.8f) },
                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.warning),
                modifier = Modifier.weight(1f)
            ) {
                Text("⚡ Voltaje bajo", color = MeetColors.backgroundDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.injectDemoFrame(pid = "0105", anomalousValue = 145f) },
                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.error),
                modifier = Modifier.weight(1f)
            ) {
                Text("🔥 Coolant spike", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = { viewModel.clearAnomalies() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Limpiar", color = MeetColors.textSecondary, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Source toggle ─────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.useRealSource() },
                enabled = state.activeProvenance !is DiagnosticProvenance.Real,
                modifier = Modifier.weight(1f)
            ) {
                Text("Usar OBD real", fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = { viewModel.useSimulatedSource() },
                enabled = state.activeProvenance !is DiagnosticProvenance.Simulated,
                modifier = Modifier.weight(1f)
            ) {
                Text("Usar simulación", fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Anomalies timeline ────────────────────────────────────────
        Text(
            "ANOMALÍAS DETECTADAS",
            color = MeetColors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(8.dp))

        val allAnomalies = (state.liveAnomalies + state.persistedAnomalies)
            .sortedByDescending { it.value.timestamp }

        if (allAnomalies.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MeetColors.cardBackground),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Sin anomalías. Inyecta un frame de prueba.",
                    color = MeetColors.textMuted,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(allAnomalies) { anomalyValue ->
                    AnomalyRow(anomalyValue)
                }
            }
        }
    }
}

@Composable
private fun ProvenanceBanner(provenance: DiagnosticProvenance) {
    val (bg, fg, label) = when (provenance) {
        is DiagnosticProvenance.Real -> Triple(MeetColors.neonGreen.copy(alpha = 0.15f), MeetColors.neonGreen, "REAL — DATOS DEL ADAPTADOR OBD")
        is DiagnosticProvenance.Offline -> Triple(MeetColors.cyberCyan.copy(alpha = 0.15f), MeetColors.cyberCyan, "OFFLINE — DATOS PERSISTIDOS")
        is DiagnosticProvenance.Simulated -> Triple(MeetColors.warning.copy(alpha = 0.15f), MeetColors.warning, "SIMULADO — DATOS DE DEMO, NO SON REALES")
        is DiagnosticProvenance.SinEnlace -> Triple(MeetColors.error.copy(alpha = 0.15f), MeetColors.error, "SIN ENLACE — CONECTA ADAPTADOR")
        is DiagnosticProvenance.RequiereHardware -> Triple(MeetColors.warning.copy(alpha = 0.15f), MeetColors.warning, "REQUIERE ${provenance.toolName}")
        is DiagnosticProvenance.NoSoportado -> Triple(MeetColors.error.copy(alpha = 0.15f), MeetColors.error, "NO SOPORTADO: ${provenance.reason}")
        is DiagnosticProvenance.Inferred -> Triple(MeetColors.electricBlue.copy(alpha = 0.15f), MeetColors.electricBlue, "INFERIDO (${provenance.source})")
        is DiagnosticProvenance.ManualEntry -> Triple(MeetColors.cyberCyan.copy(alpha = 0.15f), MeetColors.cyberCyan, "ENTRADA MANUAL")
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(fg)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = label,
                color = fg,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun ProfileCard(profile: DiagnosticValue<VehicleTwinProfileEntity?>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val p = profile.value
            if (p == null) {
                Text(
                    "Sin perfil entrenado todavía.",
                    color = MeetColors.textMuted,
                    fontSize = 12.sp
                )
                Text(
                    "El gemelo se entrena automáticamente al abrir la pantalla.",
                    color = MeetColors.textMuted,
                    fontSize = 10.sp
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricColumn("SALUD", "${p.healthScore}/100", colorForScore(p.healthScore))
                    MetricColumn("CONFIANZA", "${"%.0f".format(p.confidence)}%", MeetColors.cyberCyan)
                    MetricColumn("ANOMALÍAS", "${p.anomalyCount}", MeetColors.warning)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Última actualización: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(p.lastTrainingDate))}",
                    color = MeetColors.textMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String, color: Color) {
    Column {
        Text(label, color = MeetColors.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun AnomalyRow(value: DiagnosticValue<TwinAnomalyEntity>) {
    val a = value.value
    val severityColor = when (a.severity) {
        "HIGH" -> MeetColors.error
        "MEDIUM" -> MeetColors.warning
        else -> MeetColors.cyberCyan
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(a.parameter.uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(a.severity, color = severityColor, fontWeight = FontWeight.Black, fontSize = 10.sp)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "Esperado: ${"%.1f".format(a.expectedValue)}  Actual: ${"%.1f".format(a.actualValue)}  Δ: ${"%.1f".format(a.deviation)}",
                color = MeetColors.textSecondary,
                fontSize = 10.sp
            )
            Text(
                "Confianza ${"%.0f".format(a.confidence)}% • ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(a.timestamp))} • ${value.provenance.displayLabel}",
                color = MeetColors.textMuted,
                fontSize = 9.sp
            )
        }
    }
}

private fun colorForScore(score: Int): Color = when {
    score >= 80 -> MeetColors.neonGreen
    score >= 60 -> MeetColors.warning
    else -> MeetColors.error
}