package com.elysium369.meet.ui.screens.humanity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.humanity.AutomotiveCapabilitySeed
import com.elysium369.meet.core.humanity.CapabilityLevel
import com.elysium369.meet.core.humanity.CapabilityRecord
import com.elysium369.meet.core.humanity.Skill
import com.elysium369.meet.ui.ObdViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapabilityPassportScreen(
    viewModel: ObdViewModel,
    onBack: () -> Unit,
) {
    // Sample initial capability records based on seed
    val capabilities = remember {
        listOf(
            CapabilityRecord(
                userId = "current_user",
                skillId = "automotive.measure_voltage",
                currentLevel = CapabilityLevel.L4_GUIDED_PRACTICE,
                demonstratedEvidenceCount = 4,
                lastDemonstratedEpochMs = System.currentTimeMillis() - 86400000L,
                verifiedByExpert = true,
            ),
            CapabilityRecord(
                userId = "current_user",
                skillId = "automotive.scan_dtc",
                currentLevel = CapabilityLevel.L5_DEMONSTRATED,
                demonstratedEvidenceCount = 6,
                lastDemonstratedEpochMs = System.currentTimeMillis() - 3600000L,
                verifiedByExpert = true,
            ),
            CapabilityRecord(
                userId = "current_user",
                skillId = "automotive.isolate_misfire_p0301",
                currentLevel = CapabilityLevel.L3_SIMULATED,
                demonstratedEvidenceCount = 2,
                lastDemonstratedEpochMs = System.currentTimeMillis() - 172800000L,
                verifiedByExpert = false,
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "PASAPORTE DE CAPACIDAD",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                            ),
                        )
                        Text(
                            text = "Credenciales Demostrables con Evidencia Criptográfica",
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
                // Passport Identity Banner
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "PASSPORT ID: MEP-2026-8841",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                ),
                            )
                            AssistChip(
                                onClick = {},
                                label = { Text("VERIFICADO", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                },
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Perfil Técnico de Competencias",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Text(
                            text = "Tus niveles son calculados en base a pruebas prácticas, escaneos OBD reales y simulaciones deterministas verificadas.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }

            // Skills Summary Header
            item {
                Text(
                    text = "HABILIDADES DEMOSTRADAS EN PLATAFORMA",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                )
            }

            // Skills List
            items(capabilities) { cap ->
                val skill = AutomotiveCapabilitySeed.skills.find { it.id == cap.skillId }
                PassportSkillCard(capability = cap, skill = skill)
            }

            // Evidence Ledger Section
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "LEDGER DE EVIDENCIAS Y CERTIFICACIONES",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Integridad de Evidencia SHA-256",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Cada diagnóstico completado, borrado verificado pre/post scan y simulación de taller genera un bloque inmutable firmado que respalda tu nivel ante clientes y talleres.",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        )
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
private fun PassportSkillCard(
    capability: CapabilityRecord,
    skill: Skill?,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
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
                    text = skill?.name ?: capability.skillId,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Badge(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = capability.currentLevel.name,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = skill?.description.orEmpty(),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Evidencias: ${capability.demonstratedEvidenceCount}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.outline,
                    ),
                )
                Text(
                    text = if (capability.verifiedByExpert) "✓ Avalado por Perito" else "En Evaluación",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (capability.verifiedByExpert) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }
    }
}
