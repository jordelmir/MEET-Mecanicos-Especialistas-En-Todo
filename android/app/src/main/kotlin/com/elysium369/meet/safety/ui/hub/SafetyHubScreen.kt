package com.elysium369.meet.safety.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elysium369.meet.ui.theme.MeetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyHubScreen(
    onNavigateToMap: () -> Unit = {},
    onNavigateToReport: () -> Unit = {},
    onNavigateToMyReports: () -> Unit = {},
    onNavigateToCases: () -> Unit = {},
    onNavigateToTimelines: () -> Unit = {},
    onNavigateToAccountability: () -> Unit = {},
    onNavigateToObservatory: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: SafetyHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Elysium SEGURIDAD",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = Color.White,
                        )
                        Text(
                            "Evidencia · Memoria · Prevención",
                            fontSize = 11.sp,
                            color = MeetColors.cyberCyan,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = .55f)),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("CENTRO CIUDADANO GLOBAL", color = MeetColors.cyberCyan, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                        Text("Documenta · preserva evidencia · sigue la respuesta pública", color = Color.White, fontSize = 15.sp)
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SafetyHubMetric("MIS REPORTES", uiState.totalReportCount.toString(), Modifier.weight(1f))
                            SafetyHubMetric("PENDIENTES", uiState.pendingLocalReports.toString(), Modifier.weight(1f))
                            SafetyHubMetric("RED", if (uiState.error == null && !uiState.isLoading) "ACTIVA" else "REVISAR", Modifier.weight(1f))
                        }
                        if (uiState.isLoading) Text("Verificando Supabase y funciones activas…", color = MeetColors.textSecondary, fontSize = 12.sp)
                        uiState.error?.let { Text("No se confirmó la red. Los reportes locales permanecen protegidos.", color = MeetColors.warning, fontSize = 12.sp) }
                        Button(onClick = viewModel::refresh, colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan, contentColor = MeetColors.backgroundDeep)) {
                            Text("SINCRONIZAR ESTADO", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                SafetyHubCard("MAPA", "Puntos públicos de seguridad en el mapa", onNavigateToMap, enabled = uiState.featureGates["safety_foundation"] == true && uiState.featureGates["safety_public_map"] == true)
            }
            item {
                SafetyHubCard("REPORTAR", "Crear un nuevo reporte de seguridad", onNavigateToReport, enabled = uiState.featureGates["safety_foundation"] == true && uiState.featureGates["safety_reporting"] == true)
            }
            item {
                SafetyHubCard("MIS REPORTES", "Consulta el estado de tus reportes", onNavigateToMyReports)
            }
            item {
                SafetyHubCard("CASOS PÚBLICOS", "Casos documentados y su evolución", onNavigateToCases, enabled = uiState.featureGates["safety_foundation"] == true && uiState.featureGates["safety_public_cases"] == true)
            }
            item {
                SafetyHubCard("CRONOLOGÍAS", "Línea de tiempo pública por caso", onNavigateToTimelines, enabled = uiState.featureGates["safety_foundation"] == true && uiState.featureGates["safety_public_cases"] == true)
            }
            item {
                SafetyHubCard("ACCOUNTABILITY", "Seguimiento a respuestas institucionales", onNavigateToAccountability, enabled = uiState.featureGates["safety_foundation"] == true && uiState.featureGates["safety_accountability"] == true)
            }
            item {
                SafetyHubCard("OBSERVATORIO", "Métricas agregadas y tendencias", onNavigateToObservatory, enabled = uiState.featureGates["safety_foundation"] == true && uiState.featureGates["safety_observatory"] == true)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.borderSubtle),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "RED GUARDIAN",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MeetColors.textSecondary,
                            letterSpacing = 1.2.sp,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Próximamente / piloto cerrado",
                            fontSize = 12.sp,
                            color = MeetColors.textSecondary,
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SafetyHubMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(MeetColors.backgroundDeep, RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text(value, color = MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 19.sp)
        Text(label, color = MeetColors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SafetyHubCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.borderSubtle),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MeetColors.neonGreen,
                    letterSpacing = 1.2.sp,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color = MeetColors.textSecondary,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MeetColors.cyberCyan,
            )
        }
    }
}
