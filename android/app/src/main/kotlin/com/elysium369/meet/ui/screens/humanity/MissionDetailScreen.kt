package com.elysium369.meet.ui.screens.humanity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
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
import com.elysium369.meet.core.humanity.CapabilityLevel
import com.elysium369.meet.core.humanity.Mission
import com.elysium369.meet.core.humanity.MissionStep
import com.elysium369.meet.core.humanity.MissionStepType
import com.elysium369.meet.core.humanity.safety.SafetyKernel
import com.elysium369.meet.ui.ObdViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionDetailScreen(
    missionId: String,
    viewModel: ObdViewModel,
    onBack: () -> Unit,
    onOpenSimulation: (String) -> Unit = {},
) {
    val mission = remember(missionId) {
        AutomotiveCapabilitySeed.missions.find { it.id == missionId }
            ?: AutomotiveCapabilitySeed.missions.first()
    }

    var completedSteps by remember { mutableStateOf(setOf<Int>()) }
    var currentStepIndex by remember { mutableIntStateOf(0) }
    var isMissionCompleted by remember { mutableStateOf(false) }

    val safetyDecision = remember(mission) {
        SafetyKernel.evaluateActionSafety(
            actionDescription = mission.title + ": " + mission.goal,
            nominalSafetyLevel = mission.safetyLevel,
            userLevel = CapabilityLevel.L4_GUIDED_PRACTICE,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "MISIÓN DE CAPACIDAD",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                            ),
                        )
                        Text(
                            text = mission.title,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            maxLines = 1,
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
                // Mission Goal Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "OBJETIVO DE LA MISIÓN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            ),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = mission.goal,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }
            }

            // Safety Clearance Card from Deterministic Safety Kernel
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (safetyDecision.isAllowed) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (safetyDecision.isAllowed) Icons.Default.Shield else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (safetyDecision.isAllowed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SAFETY KERNEL: ${safetyDecision.effectiveSafetyLevel.name}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = safetyDecision.reason,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            // Progress Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "PASOS DE EJECUCIÓN GUIADA",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    Text(
                        text = "${completedSteps.size} de ${mission.steps.size} completados",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }

            // Step List
            itemsIndexed(mission.steps) { index, step ->
                val isCompleted = index in completedSteps
                val isCurrent = index == currentStepIndex

                StepCard(
                    step = step,
                    isCompleted = isCompleted,
                    isCurrent = isCurrent,
                    onExecuteStep = {
                        completedSteps = completedSteps + index
                        if (currentStepIndex < mission.steps.size - 1) {
                            currentStepIndex = index + 1
                        } else {
                            isMissionCompleted = true
                        }
                    },
                    onOpenSimulation = { onOpenSimulation(mission.id) },
                )
            }

            // Mission Completion Card
            if (isMissionCompleted) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "¡Misión Demostrada con Éxito!",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Se ha generado un registro de evidencia demostrada en tu pasaporte de capacidades.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onBack,
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("REGRESAR AL HUB DE APRENDIZAJE")
                            }
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
private fun StepCard(
    step: MissionStep,
    isCompleted: Boolean,
    isCurrent: Boolean,
    onExecuteStep: () -> Unit,
    onOpenSimulation: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCompleted) MaterialTheme.colorScheme.primary else if (isCurrent) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Text(
                            text = "${step.stepNumber}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = "Tipo: ${step.stepType.name}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.outline,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = step.instruction,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )

            if (step.safetyCheckNote.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "⚠️ Seguridad: ${step.safetyCheckNote}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }

            if (!isCompleted) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (step.stepType == MissionStepType.SIMULATION || step.stepType == MissionStepType.PHYSICAL_MEASUREMENT) {
                        OutlinedButton(
                            onClick = onOpenSimulation,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text("ABRIR SIMULADOR", fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = onExecuteStep,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("COMPLETAR PASO", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
