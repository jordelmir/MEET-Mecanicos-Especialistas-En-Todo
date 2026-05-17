package com.elysium369.meet.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.ui.components.EliteScrollContainer
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteDialog
import com.elysium369.meet.ui.components.eliteScrollbar
import com.elysium369.meet.ui.components.neonGlow
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceResetsScreen(navController: NavController, viewModel: com.elysium369.meet.ui.ObdViewModel) {
    val scope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf<ResetOption?>(null) }
    val connectionState by viewModel.connectionState.collectAsState()

    val resetOptions = listOf(
        ResetOption("oil", "Reinicio de Aceite", "Restablece el contador de vida útil del aceite.", "🛢️"),
        ResetOption("brake", "Reinicio de Frenos", "Restablece el sensor de desgaste de pastillas.", "🛑"),
        ResetOption("battery", "Registro de Batería", "Informa a la ECU sobre una batería nueva.", "🔋"),
        ResetOption("sas", "Calibración de Dirección", "Restablece el sensor de ángulo de dirección (SAS).", "🎡"),
        ResetOption("throttle", "Adaptación Mariposa", "Ajusta la posición del cuerpo de aceleración.", "⚙️"),
        ResetOption("dpf", "Regeneración DPF", "Inicia limpieza forzada del filtro de partículas.", "💨"),
        ResetOption("tpms", "Reinicio de TPMS", "Sincroniza los sensores de presión de neumáticos.", "🚗")
    )

    // ═══ Phantom Carbon Confirmation Dialog ═══
    if (showConfirmDialog != null) {
        EliteDialog(
            title = "CONFIRMACIÓN ELITE",
            message = "¿Estás seguro de iniciar: ${showConfirmDialog?.title}?\n\n" +
                    "Asegúrate de que el vehículo cumpla con las condiciones de seguridad (Motor apagado/encendido según corresponda, batería > 12V).",
            onDismiss = { showConfirmDialog = null },
            onConfirm = {
                val option = showConfirmDialog
                if (option != null) {
                    showConfirmDialog = null
                    scope.launch {
                        isRunning = true
                        resultMessage = ""
                        
                        val success = when (option.id) {
                            "oil" -> viewModel.resetOilService()
                            "battery" -> viewModel.registerBattery(80)
                            "brake" -> viewModel.resetEPB(true)
                            "sas" -> viewModel.calibrateSAS()
                            "throttle" -> viewModel.relearnThrottle()
                            "dpf" -> viewModel.regenerateDPF()
                            "tpms" -> viewModel.resetTPMS()
                            else -> false
                        }
                        
                        isRunning = false
                        resultMessage = if (success) {
                            "ÉXITO: ${option.title} completado en ${viewModel.manufacturer.value}."
                        } else {
                            "ERROR: Fallo al ejecutar ${option.title}. Verifica las condiciones de seguridad y compatibilidad."
                        }
                    }
                }
            },
            confirmText = "INICIAR"
        )
    }

    Scaffold(
        topBar = {
            EliteTopAppBar(
                title = "SERVICE RESETS ELITE",
                onBackClick = { navController.popBackStack() },
                backgroundColor = MeetColors.backgroundDark
            )
        },
        containerColor = MeetColors.backgroundDeep
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MeetColors.carbonGradient)
                .padding(16.dp)
        ) {
            
            // Running indicator
            if (isRunning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp), 
                    color = MeetColors.neonGreen,
                    trackColor = MeetColors.neonGreen.copy(alpha = 0.1f)
                )
                Text(
                    "EJECUTANDO RUTINA PROFESIONAL...", 
                    color = MeetColors.neonGreen, 
                    modifier = Modifier.padding(vertical = 12.dp), 
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            // Result message
            if (resultMessage.isNotEmpty()) {
                val isSuccess = resultMessage.contains("ÉXITO")
                EliteCard(
                    backgroundColor = if (isSuccess) MeetColors.neonGreen.copy(alpha = 0.08f) else MeetColors.error.copy(alpha = 0.08f),
                    borderColor = if (isSuccess) MeetColors.neonGreen.copy(alpha = 0.4f) else MeetColors.error.copy(alpha = 0.4f),
                    glowColor = if (isSuccess) MeetColors.neonGreen else MeetColors.error,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                ) {
                    Text(
                        resultMessage, 
                        modifier = Modifier.padding(16.dp), 
                        color = Color.White, 
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            val listState = rememberLazyListState()
            
            EliteScrollContainer(modifier = Modifier.weight(1f), fadeColor = MeetColors.backgroundDeep) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp)
                        .eliteScrollbar(listState)
                ) {
                    items(resetOptions) { option ->
                        EliteCard(
                            backgroundColor = MeetColors.cardBackground,
                            borderColor = MeetColors.borderSubtle,
                            glowColor = MeetColors.neonGreen.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Icon container
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .neonGlow(
                                            MeetColors.electricBlue,
                                            RoundedCornerShape(16.dp),
                                            minElevation = 1f, maxElevation = 4f,
                                            minAlpha = 0.05f, maxAlpha = 0.15f
                                        )
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(
                                                    MeetColors.electricBlue.copy(alpha = 0.15f),
                                                    MeetColors.backgroundDeep
                                                )
                                            )
                                        )
                                        .border(1.dp, MeetColors.electricBlue.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(option.icon, style = MaterialTheme.typography.headlineMedium)
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        option.title,
                                        color = MeetColors.textPrimary,
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        option.description,
                                        color = MeetColors.textSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))

                                EliteButton(
                                    text = "RUN",
                                    onClick = { showConfirmDialog = option },
                                    isEnabled = !isRunning && connectionState == com.elysium369.meet.core.obd.ObdState.CONNECTED,
                                    color = MeetColors.neonGreen
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

data class ResetOption(val id: String, val title: String, val description: String, val icon: String)
