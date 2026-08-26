package com.elysium369.meet.ui.screens.humanity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.humanity.AutomotiveCapabilitySeed
import com.elysium369.meet.core.humanity.Mission
import com.elysium369.meet.ui.ObdViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningHubScreen(
    viewModel: ObdViewModel,
    onBack: () -> Unit,
    onOpenDrivingTheory: () -> Unit,
    onOpenMissionDetail: (String) -> Unit = {},
    onOpenMultimeterSimulation: () -> Unit = {},
    onOpenCapabilityPassport: () -> Unit = {},
) {
    val activeDtcs by viewModel.detectedDtcs.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = connectionState == com.elysium369.meet.core.obd.ObdState.CONNECTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "MEET APRENDE",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                            ),
                        )
                        Text(
                            text = "Sistema de Capacidad y Evidencia Real",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // 1. Hero Banner
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "🏛️ HUMAN CAPABILITY PLATFORM",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            ),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Convierte Conocimiento en Capacidad Real",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Aprende, practica con simulaciones deterministas, registra evidencia y demuestra habilidades verificadas para el mundo real.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }

            // 2. Contextual DTC Learning Action (if active faults exist)
            if (activeDtcs.isNotEmpty()) {
                val primaryDtc = activeDtcs.first()
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "⚠️", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "APRENDER DE ESTE DIAGNÓSTICO",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    ),
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Código detectado: $primaryDtc",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                            )
                            Text(
                                text = "Aprende el método de diagnóstico diferencial para aislar este fallo sin comprar piezas al azar.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { onOpenMissionDetail("mission.misfire_isolation_p0301") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                ),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("INICIAR MISIÓN DE AISLAMIENTO")
                            }
                        }
                    }
                }
            }

            // 3. Certified Driving Theory Track (Reference Case)
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "🚦 TEÓRICO DE MANEJO",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            AssistChip(
                                onClick = onOpenDrivingTheory,
                                label = { Text("ESTUDIAR") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Manuales oficiales 2026, repetición espaciada y simulacros de 40 preguntas para Licencia Clase B y A.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }

            // Quick Access: Multimeter Lab & Passport
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenMultimeterSimulation() },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "⚡ MULTIMETER LAB",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Simulador de mediciones y caídas de voltaje.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            )
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenCapabilityPassport() },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "🎖️ PASAPORTE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Tus credenciales y evidencias SHA-256.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            )
                        }
                    }
                }
            }

            // 4. Practical Missions
            item {
                Text(
                    text = "MISIONES FORMATIVAS CANÓNICAS",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                )
            }

            items(AutomotiveCapabilitySeed.missions) { mission ->
                MissionCard(
                    mission = mission,
                    onClick = { onOpenMissionDetail(mission.id) },
                )
            }

            // 5. Capability Domains
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "DOMINIOS DE CAPACIDAD AUTOMOTRIZ",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                )
            }

            items(AutomotiveCapabilitySeed.domains) { domain ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = domain.iconGlyph, fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = domain.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            Text(
                                text = domain.description,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MissionCard(
    mission: Mission,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = mission.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${mission.steps.size} pasos",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = mission.goal,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Seguridad: ${mission.safetyLevel.name}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.outline,
                    ),
                )
                FilledTonalButton(
                    onClick = onClick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("VER MISIÓN", fontSize = 12.sp)
                }
            }
        }
    }
}
