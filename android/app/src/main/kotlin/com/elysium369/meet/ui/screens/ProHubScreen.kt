package com.elysium369.meet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.foundation.Image
import com.elysium369.meet.R
import com.elysium369.meet.ui.components.*
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.launch

data class ProFeature(
    val id: String,
    val titleEs: String,
    val titleEn: String,
    val descEs: String,
    val descEn: String,
    val icon: String,
    val color: Color,
    val route: String
)

@Composable
fun ProHubScreen(navController: NavController, viewModel: com.elysium369.meet.ui.ObdViewModel) {
    val isPro by viewModel.isAdapterPro.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val syncState by viewModel.cloudSyncState.collectAsState()
    val qos by viewModel.qosMetrics.collectAsState()
    val language by viewModel.language.collectAsState()
    val isSpanish = language == "es"
    val scope = rememberCoroutineScope()

    val proFeatures = remember(isSpanish) {
        listOf(
            ProFeature(
                "topology",
                "Mapeo Topológico", "Topology Mapping",
                "Ver topología de red CAN del vehículo", "View CAN-bus network topology map",
                "🕸️", MeetColors.neonGreen, "topology"
            ),
            ProFeature(
                "active_tests",
                "Pruebas Activas", "Active Tests",
                "Control bidireccional de actuadores", "Bidirectional actuator control",
                "⚙️", MeetColors.error, "active_tests"
            ),
            ProFeature(
                "oscilloscope",
                "Osciloscopio", "Oscilloscope",
                "Gráficos de ondas a alta frecuencia", "Real-time high-speed waveforms",
                "📈", MeetColors.cyberCyan, "oscilloscope"
            ),
            ProFeature(
                "resets",
                "Service Resets", "Service Resets",
                "Reinicio de aceite, frenos y mantenimiento", "Oil, EPB, SAS & service reinits",
                "🛠️", MeetColors.warning, "service_resets"
            ),
            ProFeature(
                "reports",
                "Reportes PDF", "PDF Reports",
                "Exportación de informes mecánicos", "Export professional reports to PDF",
                "📄", MeetColors.electricBlue, "reports"
            ),
            ProFeature(
                "expert_diagnostic",
                "Experto Local", "Local Expert",
                "Asistencia técnica presencial", "On-site mechanical assistance",
                "👨‍🔧", MeetColors.warning, "expert_diagnostic"
            ),
            ProFeature(
                "manuals",
                "Manuales de Taller", "Workshop Manuals",
                "Descarga y consulta de manuales offline", "Download & view offline vehicle manuals",
                "📚", MeetColors.cyberCyan, "vehicle_manuals"
            ),
            ProFeature(
                "holo_local_read",
                "Lectura Holográfica", "Holographic Live Read",
                "Dashboard 3D holográfico en tiempo real", "Real-time 3D holographic OBD2 dashboard",
                "🔮", MeetColors.neonGreen, "holo_local_read"
            ),
            ProFeature(
                "ai",
                "IA Diagnóstico", "AI Diagnostics",
                "Análisis inteligente de fallas y datos", "Deep AI analysis of codes & data",
                "🧠", MeetColors.electricBlue, "ai"
            ),
            ProFeature(
                "support_chat",
                "Soporte AI Chat", "AI Support Chat",
                "Consulta interactiva con asistente experto", "Interactive expert mechanic chatbot",
                "💬", MeetColors.neonGreen, "support_chat"
            ),
            ProFeature(
                "dashboard",
                "Dashboards Elite", "Elite Dashboards",
                "Telemetría a color personalizable", "Custom color telemetry overlays",
                "📊", MeetColors.neonGreen, "custom_pid"
            ),
            ProFeature(
                "pre_purchase",
                "Inspección Pre-Compra", "Pre-Purchase Check",
                "Verificación de odómetro y salud general", "Verify odometer & vehicle health",
                "🛡️", Color(0xFF00E5FF), "meet_perito"
            ),
            ProFeature(
                "meet_dna",
                "Firma Elysium Vanguard DNA", "Elysium Vanguard DNA Signature",
                "Firma matemática y anomalías de comportamiento", "Mathematical vehicle signature and anomaly tracking",
                "🧬", MeetColors.cyberCyan, "meet_dna"
            ),
            ProFeature(
                "performance",
                "Calculadora HP/Torque", "HP/Torque Calc",
                "Estimación de potencia y rendimiento", "Live dynamometer performance calculation",
                "🏎️", Color(0xFFFF6D00), "scanner"
            ),
            ProFeature(
                "hud",
                "HUD Reflejo", "Windshield HUD",
                "Reflejo de velocidad y RPM en parabrisas", "Project speed & RPM indicators onto windshield",
                "🔮", MeetColors.neonGreen, "hud"
            ),
            ProFeature(
                "dvir",
                "DVIR Diario", "DVIR Pre-Trip",
                "Inspección diaria de seguridad con firma", "Daily safety checklist with digital signature",
                "📋", MeetColors.cyberCyan, "dvir"
            ),
            ProFeature(
                "health_score",
                "Salud AI", "Predictive Health",
                "Puntuación de salud del motor con IA", "AI engine health status and failure forecast",
                "🩺", MeetColors.electricBlue, "health_score"
            ),
            ProFeature(
                "maintenance",
                "Mantenimiento Pro", "Pro Maintenance",
                "Control y alertas de revisiones", "Log & schedule mechanical service intervals",
                "📅", MeetColors.warning, "maintenance"
            ),
            ProFeature(
                "trips",
                "Eco Viajes", "Eco Trips Log",
                "Historial de rutas y puntajes eco", "Trip logs, fuel efficiency & eco-scoring",
                "🍃", MeetColors.neonGreen, "trips"
            ),
            ProFeature(
                "repair_network",
                "Red de Reparación", "Repair Network",
                "Base de conocimiento de casos reales", "Offline/online community repair database",
                "🌐", MeetColors.neonGreen, "repair_network"
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                EliteTopAppBar(
                    title = buildAnnotatedString {
                        withStyle(SpanStyle(color = MeetColors.neonGreen)) {
                            append("ELYSIUM VANGUARD ")
                        }
                        withStyle(SpanStyle(color = MeetColors.electricBlue)) {
                            append("PRO")
                        }
                    },
                    subtitle = if (isSpanish) "SISTEMAS AVANZADOS OEM" else "OEM ADVANCED SYSTEMS",
                    backgroundColor = MeetColors.backgroundDark
                )
            },
            containerColor = MeetColors.backgroundDark
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ELITE LOGO with pulsating neon glow
                Image(
                    painter = painterResource(id = R.drawable.meet_elite_logo),
                    contentDescription = "Elysium Vanguard Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(110.dp)
                        .neonGlow(
                            color = if (isPro) MeetColors.neonGreen else MeetColors.warning,
                            minElevation = 8f,
                            maxElevation = 24f,
                            minAlpha = 0.25f,
                            maxAlpha = 0.75f
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            2.dp,
                            if (isPro) MeetColors.neonGreen else MeetColors.warning,
                            RoundedCornerShape(16.dp)
                        )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Adapter Cryptographic/Integrity Status Card
                if (!isPro) {
                    EliteCard(
                        backgroundColor = MeetColors.error.copy(alpha = 0.04f),
                        borderColor = MeetColors.warning,
                        glowColor = MeetColors.warning.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ElysiumSectionIcon(
                                key = "warning",
                                contentDescription = if (isSpanish) "Advertencia" else "Warning",
                                tint = MeetColors.warning,
                                size = 32.dp,
                                fallbackGlyph = "⚠️"
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isSpanish) "ADVERTENCIA: HARDWARE LIMITADO" else "WARNING: LIMITED HARDWARE",
                                    color = MeetColors.warning,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isSpanish) {
                                        "Se detectó un adaptador OBD genérico/clon. Las funciones avanzadas UDS/OEM y el escaneo de múltiples módulos CAN podrían estar restringidos. Para acceso total nivel agencia, use Vgate vLinker o OBDLink."
                                    } else {
                                        "Generic/clone adapter detected. Advanced UDS/OEM functions and multi-module CAN scan may be restricted. For full agency access, please use a Vgate vLinker or OBDLink adapter."
                                    },
                                    color = MeetColors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                } else {
                    EliteCard(
                        backgroundColor = MeetColors.neonGreen.copy(alpha = 0.03f),
                        borderColor = MeetColors.neonGreen,
                        glowColor = MeetColors.neonGreen.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ElysiumSectionIcon(
                                key = "shield",
                                contentDescription = if (isSpanish) "Adaptador validado" else "Validated adapter",
                                tint = MeetColors.neonGreen,
                                size = 32.dp,
                                fallbackGlyph = "🛡️"
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isSpanish) "ADAPTADOR PROFESIONAL VALIDADO" else "PROFESSIONAL ADAPTER VALIDATED",
                                    color = MeetColors.neonGreen,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isSpanish) {
                                        "Conexión de alta velocidad establecida. Protocolos propietarios y codificación de módulos OEM habilitados."
                                    } else {
                                        "High-speed channel established. Proprietary protocols and OEM coding module features fully unlocked."
                                    },
                                    color = MeetColors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                PhantomSectionHeader(
                    label = if (isSpanish) "Monitoreo de Telemetría" else "Telemetry Monitoring",
                    accentColor = MeetColors.cyberCyan
                )

                // QoS STAT MONITOR
                EliteCard(
                    backgroundColor = MeetColors.backgroundDeep,
                    borderColor = MeetColors.borderSubtle,
                    glowColor = MeetColors.neonGreen.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        QosStat(
                            label = if (isSpanish) "Muestreo" else "Sampling",
                            value = "${"%.1f".format(qos.cmdsPerSecond)} Hz",
                            color = if (qos.cmdsPerSecond > 10) MeetColors.neonGreen else MeetColors.warning
                        )
                        QosStat(
                            label = if (isSpanish) "Latencia" else "Latency",
                            value = "${qos.latencyMs} ms",
                            color = if (qos.latencyMs < 100) MeetColors.neonGreen else MeetColors.error
                        )
                        QosStat(
                            label = if (isSpanish) "Estado" else "Status",
                            value = if (qos.isStable) (if (isSpanish) "ESTABLE" else "STABLE") else (if (isSpanish) "INHABIL" else "UNSTABLE"),
                            color = if (qos.isStable) MeetColors.neonGreen else MeetColors.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // SMART SCAN LAUNCHER BUTTON
                EliteButton(
                    text = if (isSpanish) "Ejecutar Escaneo Inteligente" else "Run Smart Scan",
                    onClick = { scope.launch { viewModel.runSmartScan() } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                PhantomSectionHeader(
                    label = if (isSpanish) "Herramientas Avanzadas" else "Advanced Tools",
                    accentColor = MeetColors.neonGreen
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Optimized features grid layout (Using manual Rows to avoid LazyVerticalGrid scroll conflict)
                val chunks = remember(proFeatures) { proFeatures.chunked(2) }
                chunks.forEachIndexed { rowIndex, pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEachIndexed { itemIndex, feature ->
                            val index = rowIndex * 2 + itemIndex
                            Box(modifier = Modifier.weight(1f)) {
                                AnimatedEntrance(index = index) {
                                    EliteCard(
                                        backgroundColor = MeetColors.cardBackground,
                                        borderColor = feature.color.copy(alpha = 0.3f),
                                        glowColor = feature.color.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(16.dp),
                                        onClick = { navController.navigate(feature.route) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1.05f)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            ElysiumSectionIcon(
                                                key = feature.id,
                                                contentDescription = if (isSpanish) feature.titleEs else feature.titleEn,
                                                tint = feature.color,
                                                size = 46.dp,
                                                fallbackGlyph = feature.icon
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = if (isSpanish) feature.titleEs else feature.titleEn,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontSize = 13.sp
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = if (isSpanish) feature.descEs else feature.descEn,
                                                color = MeetColors.textSecondary,
                                                textAlign = TextAlign.Center,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 9.sp,
                                                lineHeight = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (pair.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Full Screen Scanning HUD Overlay
        if (isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    EliteScannerAnimation(
                        modifier = Modifier.size(220.dp),
                        scanText = if (syncState.isNotEmpty()) syncState.uppercase() else (if (isSpanish) "PROCESANDO" else "PROCESSING")
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = if (isSpanish) "POR FAVOR NO DESCONECTE EL ADAPTADOR" else "PLEASE DO NOT DISCONNECT THE ADAPTER",
                        color = MeetColors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun QosStat(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(4.dp)
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0.05f))
                ),
                RoundedCornerShape(8.dp)
            )
            .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label.uppercase(),
            color = MeetColors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = color,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontSize = 15.sp
        )
    }
}
