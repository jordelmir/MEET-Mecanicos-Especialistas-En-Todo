package com.elysium369.meet.ui.screens

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
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
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE) }
    val userProfile = remember { prefs.getString("user_profile", "owner").orEmpty() }
    val activeVehicle by viewModel.selectedVehicle.collectAsState()
    val obdState by viewModel.connectionState.collectAsState()
    val activeDtcs by viewModel.activeDtcs.collectAsState()
    val pendingDtcs by viewModel.pendingDtcs.collectAsState()
    val permanentDtcs by viewModel.permanentDtcs.collectAsState()
    val readiness by viewModel.readinessMonitors.collectAsState()
    val healthScore by viewModel.healthScore.collectAsState()
    val liveData by viewModel.liveData.collectAsState()
    val protocol by viewModel.detectedProtocol.collectAsState()
    val adapterVer by viewModel.adapterVersion.collectAsState()
    val isClone by viewModel.isCloneAdapter.collectAsState()

    val totalDtcs = activeDtcs.size + pendingDtcs.size + permanentDtcs.size
    val readyCount = readiness?.monitors?.count { it.complete } ?: 0
    val monitorCount = readiness?.monitors?.size ?: 0
    val batteryVoltage = liveData["0142"] ?: liveData["42"] ?: liveData["Voltaje ECU"] ?: liveData["VOLTAGE"]
    val commandState = remember(
        activeVehicle,
        obdState,
        totalDtcs,
        healthScore,
        batteryVoltage,
        readyCount,
        monitorCount
    ) {
        buildHomeCommandState(
            hasVehicle = activeVehicle != null,
            obdState = obdState,
            totalDtcs = totalDtcs,
            healthScore = healthScore,
            batteryVoltage = batteryVoltage,
            readyCount = readyCount,
            monitorCount = monitorCount
        )
    }

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 0..11 -> "Buenos días"
        in 12..18 -> "Buenas tardes"
        else -> "Buenas noches"
    }

    val scrollState = rememberScrollState()
    var showThemeCustomizer by remember { mutableStateOf(false) }

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                greeting,
                                style = MaterialTheme.typography.titleMedium,
                                color = MeetColors.textSecondary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 28.sp)) {
                                        append("ELYSIUM")
                                    }
                                    append(" ")
                                    withStyle(SpanStyle(color = MeetColors.electricBlue, fontWeight = FontWeight.Bold, fontSize = 20.sp)) {
                                        append("VANGUARD")
                                    }
                                }
                            )
                        }
                        // Premium theme customizer & settings buttons in home header
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MeetColors.cardBackground)
                                    .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.3f), CircleShape)
                                    .clickable { showThemeCustomizer = true }
                                    .then(Modifier.pulseOnHover()),
                                contentAlignment = Alignment.Center
                            ) {
                                ElysiumSectionIcon(
                                    key = "theme",
                                    contentDescription = "Personalizar tema",
                                    tint = MeetColors.neonGreen,
                                    size = 24.dp,
                                    fallbackGlyph = "🎨"
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MeetColors.cardBackground)
                                    .border(1.dp, MeetColors.electricBlue.copy(alpha = 0.3f), CircleShape)
                                    .clickable { navController.navigate("settings") }
                                    .then(Modifier.pulseOnHover()),
                                contentAlignment = Alignment.Center
                            ) {
                                ElysiumSectionIcon(
                                    key = "settings",
                                    contentDescription = "Ajustes",
                                    tint = MeetColors.electricBlue,
                                    size = 24.dp,
                                    fallbackGlyph = "⚙️"
                                )
                            }
                        }
                    }
                    Text(
                        "DIAGNÓSTICO PROFESIONAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MeetColors.textMuted,
                        letterSpacing = 3.sp
                    )
                }
            }

            AnimatedEntrance(1) {
                CommandCenterCard(
                    profile = userProfile,
                    state = commandState,
                    onPrimaryAction = { navController.navigate(commandState.primaryRoute) }
                )
            }

            // ── DTC Alert Banner ──
            if (activeDtcs.isNotEmpty()) {
                AnimatedEntrance(2) {
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
            AnimatedEntrance(3) {
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
            AnimatedEntrance(4) {
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
            AnimatedEntrance(5) {
                VehicleIdentificationCard(viewModel = viewModel)
            }

            // ── Quick Actions Grid ──
            PhantomSectionHeader("Acciones Rápidas")

            val actions = listOf(
                Triple("⚡", "Scanner", MeetColors.neonGreen) to "scanner",
                Triple("⚠️", "DTCs", MeetColors.error) to "dtc",
                Triple("🛡️", "Vanguard Perito", MeetColors.neonGreen) to "meet_perito",
                Triple("🧬", "Vanguard DNA", MeetColors.cyberCyan) to "meet_dna",
                Triple("📦", "Motor 3D", MeetColors.cyberCyan) to "component_locator",
                Triple("⚙️", "Ajustes", MeetColors.textSecondary) to "settings",
                Triple("🔍", "Hallazgos", MeetColors.neonGreen) to "findings",
                Triple("🚗", "Garage", MeetColors.cyberCyan) to "garage",
                Triple("🤖", "IA", MeetColors.electricBlue) to "ai",
                Triple("🔧", "Terminal", MeetColors.cyberCyan) to "terminal",
                Triple("💬", "Soporte", MeetColors.warning) to "support_chat",
                Triple("💬", "Chat Flota", MeetColors.electricBlue) to "fleet_chat_list/b1",
                Triple("📄", "Reportes", MeetColors.electricBlue) to "reports",
                Triple("🔮", "HUD Reflejo", MeetColors.neonGreen) to "hud",
                Triple("📹", "Cámara HUD", MeetColors.electricBlue) to "dashcam",
                Triple("📋", "DVIR Diario", MeetColors.cyberCyan) to "dvir",
                Triple("🩺", "Salud AI", MeetColors.electricBlue) to "health_score",
                Triple("📅", "Mantenimiento", MeetColors.warning) to "maintenance",
                Triple("🍃", "Eco Viajes", MeetColors.neonGreen) to "trips",
                Triple("📡", "Live Link", MeetColors.neonGreen) to "live_link",
                Triple("🔬", "Pro Hub", MeetColors.hotMagenta) to "pro_hub"
            )

            actions.chunked(2).forEachIndexed { rowIdx, row ->
                AnimatedEntrance(6 + rowIdx) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row.forEach { (meta, route) ->
                            QuickActionCard(
                                iconKey = route,
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

    if (showThemeCustomizer) {
        com.elysium369.meet.ui.components.SystemThemeCustomizerDialog(
            onDismiss = { showThemeCustomizer = false }
        )
    }
}

private data class HomeCommandState(
    val title: String,
    val recommendation: String,
    val primaryAction: String,
    val primaryRoute: String,
    val severityColor: Color,
    val statusLine: String
)

@Composable
private fun CommandCenterCard(
    profile: String,
    state: HomeCommandState,
    onPrimaryAction: () -> Unit
) {
    EliteCard(
        glowColor = state.severityColor,
        borderColor = state.severityColor.copy(alpha = 0.35f),
        backgroundColor = MeetColors.cardBackground,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(profileLabel(profile), MeetColors.electricBlue)
                StatusPill("REAL", MeetColors.neonGreen)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                state.title,
                color = Color.White,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(6.dp))
            Text(
                state.statusLine,
                color = state.severityColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                state.recommendation,
                color = MeetColors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(14.dp))
            EliteButton(
                text = state.primaryAction,
                onClick = onPrimaryAction,
                color = state.severityColor,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    iconKey: String,
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
            ElysiumSectionIcon(
                key = iconKey,
                contentDescription = label,
                tint = accentColor,
                size = 28.dp,
                fallbackGlyph = icon
            )
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

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.38f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

private fun buildHomeCommandState(
    hasVehicle: Boolean,
    obdState: ObdState,
    totalDtcs: Int,
    healthScore: Int,
    batteryVoltage: Float?,
    readyCount: Int,
    monitorCount: Int
): HomeCommandState {
    return when {
        !hasVehicle -> HomeCommandState(
            title = "Configura tu primer vehículo",
            recommendation = "Agrega marca, modelo, año, motor y VIN si lo tienes. Eso mejora guías, reportes, IA y mantenimiento.",
            primaryAction = "AGREGAR VEHÍCULO",
            primaryRoute = "vehicle_form",
            severityColor = MeetColors.electricBlue,
            statusLine = "Garaje pendiente"
        )
        obdState != ObdState.CONNECTED -> HomeCommandState(
            title = "Conecta el adaptador OBD",
            recommendation = "Enciende el switch, conecta el adaptador y deja que Elysium Vanguard detecte protocolo, latencia y capacidades antes del escaneo.",
            primaryAction = "CONECTAR ADAPTADOR",
            primaryRoute = "connect",
            severityColor = MeetColors.cyberCyan,
            statusLine = "Sin enlace físico"
        )
        totalDtcs > 0 -> HomeCommandState(
            title = "Diagnóstico prioritario disponible",
            recommendation = "Hay $totalDtcs código(s). Revisa severidad, freeze frame, pruebas de confirmación y piezas relacionadas antes de borrar o cambiar repuestos.",
            primaryAction = "VER DIAGNÓSTICO PRIORITARIO",
            primaryRoute = "dtc",
            severityColor = MeetColors.error,
            statusLine = "Check engine / DTC detectado"
        )
        batteryVoltage != null && batteryVoltage < 12.4f -> HomeCommandState(
            title = "Voltaje bajo detectado",
            recommendation = "Valida batería, alternador, masas y caída de voltaje. Un voltaje bajo puede contaminar lecturas y pruebas bidireccionales.",
            primaryAction = "ABRIR MOTOR 3D",
            primaryRoute = "component_locator",
            severityColor = MeetColors.warning,
            statusLine = "Batería/sistema de carga: %.2f V".format(batteryVoltage)
        )
        healthScore < 80 -> HomeCommandState(
            title = "Salud del vehículo requiere revisión",
            recommendation = "El score bajó por telemetría o anomalías. Revisa Elysium Vanguard DNA para ver tendencia, sistema afectado y próxima prueba recomendada.",
            primaryAction = "VER Elysium Vanguard DNA",
            primaryRoute = "meet_dna",
            severityColor = MeetColors.warning,
            statusLine = "Score: $healthScore/100"
        )
        else -> HomeCommandState(
            title = "Vehículo listo para monitoreo",
            recommendation = if (monitorCount > 0) {
                "Monitores listos: $readyCount/$monitorCount. Puedes grabar sesión, generar reporte o revisar mantenimiento predictivo."
            } else {
                "No hay fallas críticas ahora. Graba una sesión de manejo para construir historial DNA y detectar cambios temprano."
            },
            primaryAction = "INICIAR SCANNER",
            primaryRoute = "scanner",
            severityColor = MeetColors.neonGreen,
            statusLine = "Sistema estable"
        )
    }
}

private fun profileLabel(profile: String): String {
    return when (profile) {
        "mechanic" -> "MECÁNICO"
        "workshop" -> "TALLER"
        "fleet" -> "FLOTA"
        else -> "USUARIO"
    }
}
