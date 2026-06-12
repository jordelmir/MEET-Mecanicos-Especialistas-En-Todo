package com.elysium369.meet.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.core.obd.ObdState
import java.util.Calendar
import com.elysium369.meet.ui.components.*
import com.elysium369.meet.ui.theme.MeetColors

// ═══════════════════════════════════════════════════════════════
// HOME SCREEN V2 — Phantom Carbon Edition
// ═══════════════════════════════════════════════════════════════

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: ObdViewModel
) {
    val activeVehicle by viewModel.selectedVehicle.collectAsState()
    val obdState by viewModel.connectionState.collectAsState()
    val activeDtcs by viewModel.activeDtcs.collectAsState()
    val protocol by viewModel.detectedProtocol.collectAsState()
    val adapterVer by viewModel.adapterVersion.collectAsState()
    val isClone by viewModel.isCloneAdapter.collectAsState()

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 0..11 -> "Buenos días"
        in 12..18 -> "Buenas tardes"
        else -> "Buenas noches"
    }

    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Hero Header ──
            AnimatedEntrance(0) {
                Column {
                    Text(
                        greeting,
                        style = MaterialTheme.typography.titleMedium,
                        color = MeetColors.textSecondary
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            "ELYSIUM",
                            style = MaterialTheme.typography.displayMedium,
                            color = MeetColors.neonGreen,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "VANGUARD",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MeetColors.electricBlue,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    Text(
                        "DIAGNÓSTICO PROFESIONAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MeetColors.textMuted,
                        letterSpacing = 3.sp
                    )
                }
            }

            // ── DTC Alert Banner ──
            if (activeDtcs.isNotEmpty()) {
                AnimatedEntrance(1) {
                    EliteCard(
                        glowColor = MeetColors.neonGreen,
                        borderColor = MeetColors.neonGreen.copy(alpha = 0.4f),
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(14.dp),
                        onClick = { navController.navigate("dtc") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(MeetColors.neonGreen, CircleShape)
                                        .then(Modifier.pulseOnHover())
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "${activeDtcs.size} FALLAS DETECTADAS",
                                        color = MeetColors.neonGreen,
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(
                                        "Toque para ver detalles",
                                        color = MeetColors.neonGreen.copy(alpha = 0.5f),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Text("→", color = MeetColors.neonGreen, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            // ── Vehicle Card ──
            AnimatedEntrance(2) {
                EliteCard(
                    glowColor = MeetColors.neonGreen,
                    borderColor = MeetColors.neonGreen.copy(alpha = 0.15f),
                    backgroundColor = MeetColors.cardBackground,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                        PhantomSectionHeader("Vehículo Activo")
                        Spacer(Modifier.height(12.dp))
                        if (activeVehicle != null) {
                            Text(
                                "${activeVehicle?.make} ${activeVehicle?.model}",
                                color = Color.White,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${activeVehicle?.year}",
                                    color = MeetColors.electricBlue,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "VIN: ${activeVehicle?.vin ?: "N/A"}",
                                    color = MeetColors.textMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            EliteButton(
                                text = "IR AL SCANNER",
                                onClick = { navController.navigate("scanner") },
                                color = MeetColors.neonGreen,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text("Sin vehículo seleccionado", color = MeetColors.textMuted,
                                style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(12.dp))
                            EliteOutlinedButton(
                                text = "SELECCIONAR VEHÍCULO",
                                onClick = { navController.navigate("garage") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // ── Connection Status ──
            AnimatedEntrance(3) {
                EliteCard(
                    glowColor = when (obdState) {
                        ObdState.CONNECTED -> MeetColors.neonGreen
                        ObdState.ERROR -> MeetColors.error
                        else -> null
                    },
                    borderColor = when (obdState) {
                        ObdState.CONNECTED -> MeetColors.neonGreen.copy(alpha = 0.2f)
                        ObdState.ERROR -> MeetColors.error.copy(alpha = 0.2f)
                        else -> MeetColors.borderSubtle
                    },
                    backgroundColor = MeetColors.cardBackground,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            // Animated status dot
                            val statusColor = when (obdState) {
                                ObdState.CONNECTED -> MeetColors.neonGreen
                                ObdState.ERROR -> MeetColors.neonGreen
                                ObdState.CONNECTING, ObdState.NEGOTIATING -> MeetColors.warning
                                else -> MeetColors.textMuted
                            }
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(statusColor, CircleShape)
                                    .then(
                                        if (obdState == ObdState.CONNECTED) Modifier.pulseOnHover()
                                        else Modifier
                                    )
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    when (obdState) {
                                        ObdState.CONNECTED -> "CONECTADO"
                                        ObdState.DISCONNECTED -> "DESCONECTADO"
                                        ObdState.CONNECTING -> "CONECTANDO..."
                                        ObdState.NEGOTIATING -> "NEGOCIANDO..."
                                        ObdState.ERROR -> "ERROR"
                                    },
                                    color = statusColor,
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                if (obdState == ObdState.CONNECTED && protocol.isNotBlank()) {
                                    Text(protocol, color = MeetColors.textMuted, fontSize = 10.sp, maxLines = 1)
                                }
                                if (obdState == ObdState.CONNECTED && isClone && adapterVer.isNotBlank()) {
                                    Text("⚠ Clon ELM327", color = MeetColors.neonGreen, fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        if (obdState == ObdState.CONNECTED) {
                            EliteOutlinedButton(text = "OFF", onClick = { viewModel.disconnect() },
                                color = MeetColors.neonGreen)
                        } else if (obdState != ObdState.CONNECTING && obdState != ObdState.NEGOTIATING) {
                            EliteOutlinedButton(text = "CONECTAR",
                                onClick = { navController.navigate("connect") })
                        }
                    }
                }
            }

            // Vehicle Identification Card
            AnimatedEntrance(4) {
                VehicleIdentificationCard(viewModel = viewModel)
            }

            // ── Quick Actions Grid ──
            PhantomSectionHeader("Acciones Rápidas")

            val actions = listOf(
                Triple("⚡", "Scanner", MeetColors.neonGreen) to "scanner",
                Triple("⚠️", "DTCs", MeetColors.error) to "dtc",
                Triple("🚗", "Garage", MeetColors.cyberCyan) to "garage",
                Triple("🤖", "IA", MeetColors.electricBlue) to "ai",
                Triple("🔧", "Terminal", MeetColors.cyberCyan) to "terminal",
                Triple("💬", "Soporte", MeetColors.warning) to "support_chat",
                Triple("💬", "Chat Flota", MeetColors.electricBlue) to "fleet_chat_list/b1",
                Triple("📄", "Reportes", MeetColors.electricBlue) to "reports",
                Triple("🔮", "HUD Reflejo", MeetColors.neonGreen) to "hud",
                Triple("📋", "DVIR Diario", MeetColors.cyberCyan) to "dvir",
                Triple("🩺", "Salud AI", MeetColors.electricBlue) to "health_score",
                Triple("📅", "Mantenimiento", MeetColors.warning) to "maintenance",
                Triple("🍃", "Eco Viajes", MeetColors.neonGreen) to "trips",
                Triple("📡", "Live Link", MeetColors.neonGreen) to "live_link",
                Triple("🔬", "Pro Hub", MeetColors.hotMagenta) to "pro_hub"
            )

            actions.chunked(2).forEachIndexed { rowIdx, row ->
                AnimatedEntrance(5 + rowIdx) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row.forEach { (meta, route) ->
                            QuickActionCard(
                                icon = meta.first,
                                label = meta.second,
                                accentColor = meta.third,
                                modifier = Modifier.weight(1f)
                            ) { navController.navigate(route) }
                        }
                        // Pad odd row
                        if (row.size < 2) Spacer(Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: String,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    EliteCard(
        glowColor = accentColor,
        borderColor = accentColor.copy(alpha = 0.15f),
        backgroundColor = MeetColors.cardBackground,
        shape = RoundedCornerShape(14.dp),
        onClick = onClick,
        modifier = modifier.height(68.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp).fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(icon, fontSize = 22.sp)
            Text(
                label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                letterSpacing = 0.3.sp
            )
        }
    }
}
