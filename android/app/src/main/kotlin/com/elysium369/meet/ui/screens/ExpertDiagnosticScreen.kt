package com.elysium369.meet.ui.screens

import com.elysium369.meet.ui.components.AnimatedNeonIcon

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.obd.DiagnosticSeverity
import com.elysium369.meet.core.obd.ExpertDiagnosticProcedure
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.theme.MeetColors
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.components.EliteButton

@Composable
fun ExpertDiagnosticScreen(
    viewModel: ObdViewModel,
    navController: NavController,
    onNavigateBack: () -> Unit
) {
    val diagnostics by viewModel.localDiagnostics.collectAsState()
    val isExpertActive by viewModel.isLocalExpertActive.collectAsState()

    // Pulse animation for the active indicator
    val infiniteTransition = rememberInfiniteTransition(label = "expertPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Scaffold(
        topBar = {
            EliteTopAppBar(
                title = "DIAGNÓSTICO EXPERTO LOCAL",
                onBackClick = onNavigateBack,
                backgroundColor = MeetColors.backgroundDark
            )
        },
        containerColor = MeetColors.backgroundDark
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // ── Control Panel: Start/Stop Button ──
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MeetColors.cardBackground)
                        .border(
                            1.dp,
                            if (isExpertActive) MeetColors.neonGreen.copy(alpha = 0.4f) else MeetColors.borderSubtle,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                ) {
                    // Status Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isExpertActive) MeetColors.neonGreen.copy(alpha = pulseAlpha)
                                        else MeetColors.textSecondary.copy(alpha = 0.4f)
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isExpertActive) "EXPERTO ACTIVO" else "EXPERTO DETENIDO",
                                color = if (isExpertActive) MeetColors.neonGreen else MeetColors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = if (isExpertActive) "${diagnostics.size} diagnósticos" else "—",
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Start/Stop Button
                    Button(
                        onClick = { viewModel.toggleLocalExpert() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isExpertActive) MeetColors.error else MeetColors.neonGreen
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        AnimatedNeonIcon(
                            imageVector = if (isExpertActive) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (isExpertActive) Color.White else Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isExpertActive) "DETENER EXPERTO LOCAL" else "INICIAR EXPERTO LOCAL",
                            color = if (isExpertActive) Color.White else Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    if (!isExpertActive) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "El motor de diagnóstico experto está en pausa. Pulse «Iniciar» para analizar la telemetría en tiempo real.",
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // ── Diagnostic Results ──
            if (!isExpertActive) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MeetColors.cardBackground)
                            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AnimatedNeonIcon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MeetColors.textSecondary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Experto Local en pausa",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Active el experto para recibir diagnósticos en tiempo real basados en la telemetría del vehículo y los códigos de falla activos.",
                                color = MeetColors.textSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else if (diagnostics.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MeetColors.cardBackground)
                            .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AnimatedNeonIcon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MeetColors.neonGreen,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Sin anomalías detectadas",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "No hay datos de telemetría disponibles o el motor funciona a nivel óptimo.\nConecte el escáner y encienda el motor.",
                                color = MeetColors.textSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(diagnostics) { procedure ->
                    DiagnosticProcedureCard(procedure, navController)
                }
            }
        }
    }
}

@Composable
fun DiagnosticProcedureCard(
    procedure: ExpertDiagnosticProcedure,
    navController: NavController
) {
    val severityColor = when (procedure.severity) {
        DiagnosticSeverity.INFO -> MeetColors.success
        DiagnosticSeverity.MODERATE -> MeetColors.warning
        DiagnosticSeverity.HIGH -> MeetColors.warning // Naranja intenso
        DiagnosticSeverity.CRITICAL -> MeetColors.error
    }

    val icon = when (procedure.severity) {
        DiagnosticSeverity.INFO -> Icons.Default.Info
        DiagnosticSeverity.CRITICAL, DiagnosticSeverity.HIGH -> Icons.Default.Warning
        else -> Icons.Default.Build
    }

    EliteCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MeetColors.cardBackground,
        borderColor = severityColor.copy(alpha = 0.3f),
        glowColor = severityColor,
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedNeonIcon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = severityColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = procedure.title,
                    color = severityColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = procedure.description,
                color = MeetColors.textPrimary,
                fontSize = 14.sp
            )

            if (procedure.probableCauses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Causas Probables:",
                    color = MeetColors.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                procedure.probableCauses.forEach { cause ->
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        Text("•", color = MeetColors.textSecondary, modifier = Modifier.padding(end = 6.dp))
                        Text(text = cause, color = MeetColors.textPrimary, fontSize = 14.sp)
                    }
                }
            }

            if (procedure.testSteps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Procedimiento de Prueba:",
                    color = MeetColors.neonGreen,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                procedure.testSteps.forEach { step ->
                    Text(
                        text = step,
                        color = MeetColors.textPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            val dtcCodeMatch = remember(procedure.title) {
                Regex("Código\\s+([PBUC][0-9A-F]{4})", RegexOption.IGNORE_CASE).find(procedure.title)
            }
            val dtcCode = dtcCodeMatch?.groupValues?.getOrNull(1)?.uppercase()

            if (dtcCode != null) {
                Spacer(modifier = Modifier.height(16.dp))
                EliteButton(
                    text = "🛠️ CÓMO REPARAR (PASO A PASO)",
                    onClick = {
                        navController.navigate("repair/$dtcCode")
                    },
                    color = MeetColors.neonGreen,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
