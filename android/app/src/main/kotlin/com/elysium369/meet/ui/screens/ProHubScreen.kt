package com.elysium369.meet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.elysium369.meet.ui.theme.MeetColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.elysium369.meet.R
import com.elysium369.meet.ui.components.neonGlow
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteButton

data class ProFeature(val id: String, val title: String, val icon: String, val color: Color, val route: String)

@Composable
fun ProHubScreen(navController: NavController, viewModel: com.elysium369.meet.ui.ObdViewModel) {
    val isPro by viewModel.isAdapterPro.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Removed manual infinite transition to use reusable neonGlow modifier
    
    val proFeatures = listOf(
        ProFeature("topology", "Mapeo\nTopológico", "🕸️", com.elysium369.meet.ui.theme.MeetColors.neonGreen, "topology"),
        ProFeature("active_tests", "Pruebas\nActivas", "⚙️", com.elysium369.meet.ui.theme.MeetColors.error, "active_tests"),
        ProFeature("oscilloscope", "Osciloscopio\nAlta Frecuencia", "📈", MeetColors.cyberCyan, "oscilloscope"),
        ProFeature("resets", "Service\nResets", "🛠️", MeetColors.warning, "service_resets"),
        ProFeature("reports", "Reportes\nPDF", "📄", MeetColors.electricBlue, "reports"),
        ProFeature("expert_diagnostic", "Experto\nLocal", "👨‍🔧", MeetColors.warning, "expert_diagnostic"),
        ProFeature("ai", "IA\nDiagnóstico", "🧠", MeetColors.electricBlue, "ai"),
        ProFeature("support_chat", "Soporte\nAI Chat", "💬", com.elysium369.meet.ui.theme.MeetColors.neonGreen, "support_chat"),
        ProFeature("dashboard", "Dashboards\nElite", "📊", com.elysium369.meet.ui.theme.MeetColors.neonGreen, "custom_pid")
    )

    Scaffold(
        topBar = {
            EliteTopAppBar(
                title = "MEET PRO ELITE",
                backgroundColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark
            )
        },
        containerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            
            // ELITE LOGO
            Image(
                painter = painterResource(id = R.drawable.meet_elite_logo),
                contentDescription = "Meet Elite Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(120.dp)
                    .neonGlow(color = com.elysium369.meet.ui.theme.MeetColors.neonGreen, minElevation = 8f, maxElevation = 24f, minAlpha = 0.3f, maxAlpha = 0.8f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, com.elysium369.meet.ui.theme.MeetColors.neonGreen, RoundedCornerShape(16.dp))
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            if (!isPro) {
                EliteCard(
                    backgroundColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.08f),
                    borderColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.3f),
                    glowColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(
                        "ADVERTENCIA: Adaptador CLON detectado. Las funciones avanzadas UDS/OEM están limitadas. Para una experiencia profesional completa, usa un adaptador Vgate vLinker o OBDLink.",
                        color = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                EliteCard(
                    backgroundColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.1f),
                    borderColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                    glowColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(
                        "✓ ADAPTADOR PROFESIONAL DETECTADO. Acceso Total Concedido.",
                        color = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(12.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text("Funciones Nivel Agencia", color = com.elysium369.meet.ui.theme.MeetColors.neonGreen, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Herramientas de diagnóstico avanzado, controles bidireccionales y reportes de nivel mundial.", color = com.elysium369.meet.ui.theme.MeetColors.textSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(24.dp))

            // QoS MONITOR
            val qos by viewModel.qosMetrics.collectAsState()
            EliteCard(
                backgroundColor = MeetColors.backgroundDark,
                borderColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.2f),
                glowColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    QosStat("Muestreo", "${"%.1f".format(qos.cmdsPerSecond)} Hz", if (qos.cmdsPerSecond > 10) com.elysium369.meet.ui.theme.MeetColors.neonGreen else com.elysium369.meet.ui.theme.MeetColors.warning)
                    QosStat("Latencia", "${qos.latencyMs}ms", if (qos.latencyMs < 100) com.elysium369.meet.ui.theme.MeetColors.neonGreen else com.elysium369.meet.ui.theme.MeetColors.error)
                    QosStat("Estado", if (qos.isStable) "ESTABLE" else "INESTABLE", if (qos.isStable) com.elysium369.meet.ui.theme.MeetColors.neonGreen else com.elysium369.meet.ui.theme.MeetColors.error)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            
            // SMART SCAN BUTTON
            EliteButton(
                text = "EJECUTAR ESCANEO INTELIGENTE",
                onClick = { scope.launch { viewModel.runSmartScan() } },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            )
            
            val syncState by viewModel.cloudSyncState.collectAsState()
            if (syncState.isNotEmpty()) {
                Text(syncState, color = com.elysium369.meet.ui.theme.MeetColors.neonGreen, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 600.dp)
            ) {
                items(proFeatures) { feature ->
                    EliteCard(
                        backgroundColor = MeetColors.backgroundDeep,
                        borderColor = feature.color.copy(alpha = 0.3f),
                        glowColor = feature.color,
                        shape = RoundedCornerShape(20.dp),
                        onClick = { navController.navigate(feature.route) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(feature.icon, style = MaterialTheme.typography.displayMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                feature.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QosStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = com.elysium369.meet.ui.theme.MeetColors.textSecondary, style = MaterialTheme.typography.labelSmall)
        Text(value, color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
    }
}
