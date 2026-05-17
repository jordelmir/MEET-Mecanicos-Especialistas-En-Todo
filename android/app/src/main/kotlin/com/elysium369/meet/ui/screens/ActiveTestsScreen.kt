package com.elysium369.meet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.elysium369.meet.ui.theme.MeetColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.elysium369.meet.core.obd.ActiveTest
import com.elysium369.meet.core.obd.ActiveTestStatus
import com.elysium369.meet.ui.components.*

@Composable
fun ActiveTestsScreen(navController: NavController, viewModel: com.elysium369.meet.ui.ObdViewModel) {
    val status by viewModel.activeTestStatus.collectAsState()
    val availableTests = viewModel.availableActiveTests
    
    var selectedTest by remember { mutableStateOf<ActiveTest?>(null) }
    var showSafetyWarning by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            EliteTopAppBar(
                title = "PRUEBAS ACTIVAS ELITE",
                onBackClick = { navController.popBackStack() },
                backgroundColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark
            )
        },
        containerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            
            // Current Active Test Status
            if (status.isActive) {
                ActiveTestProgressCard(status, onStop = { viewModel.stopActiveTest() })
                Spacer(modifier = Modifier.height(24.dp))
            }

            Text(
                "Controles Bidireccionales",
                color = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Activa componentes del vehículo para verificar su funcionamiento físico.",
                color = MeetColors.textSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(availableTests) { test ->
                    ActiveTestItem(
                        test = test,
                        isEnabled = !status.isActive,
                        onClick = {
                            selectedTest = test
                            showSafetyWarning = true
                        }
                    )
                }
            }
        }

        if (showSafetyWarning && selectedTest != null) {
            EliteDialog(
                title = "ADVERTENCIA DE SEGURIDAD",
                message = "Estás por iniciar: ${selectedTest?.name}\n\nAsegúrate de que el vehículo esté en condiciones seguras. MEET verificará automáticamente el voltaje y estado del motor antes de proceder.",
                onDismiss = { showSafetyWarning = false },
                onConfirm = {
                    selectedTest?.let { viewModel.runActiveTest(it) }
                    showSafetyWarning = false
                },
                confirmText = "CONFIRMAR Y EJECUTAR",
                dismissText = "CANCELAR",
                isDestructive = true
            )
        }
    }
}

@Composable
fun ActiveTestItem(test: ActiveTest, isEnabled: Boolean, onClick: () -> Unit) {
    val borderColor = if (isEnabled) com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.05f)
    val glow = if (isEnabled) com.elysium369.meet.ui.theme.MeetColors.neonGreen else null
    
    EliteCard(
        backgroundColor = if (isEnabled) MeetColors.backgroundDark else com.elysium369.meet.ui.theme.MeetColors.backgroundDark,
        borderColor = borderColor,
        glowColor = glow,
        shape = RoundedCornerShape(12.dp),
        onClick = if (isEnabled) onClick else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(test.name, color = Color.White, fontWeight = FontWeight.Bold)
                Text(test.description, color = MeetColors.textSecondary, style = MaterialTheme.typography.bodySmall)
                if (test.manufacturer != null) {
                    Text(
                        "Específico: ${test.manufacturer}",
                        color = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Text("▶", color = if (isEnabled) com.elysium369.meet.ui.theme.MeetColors.neonGreen else MeetColors.borderBlue)
        }
    }
}

@Composable
fun ActiveTestProgressCard(status: ActiveTestStatus, onStop: () -> Unit) {
    val statusColor = if (status.message.contains("Completado", ignoreCase = true) || status.progress >= 1f) MeetColors.cyberCyan else com.elysium369.meet.ui.theme.MeetColors.neonGreen
    EliteCard(
        backgroundColor = statusColor.copy(alpha = 0.1f),
        borderColor = statusColor,
        glowColor = statusColor,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    progress = status.progress,
                    color = statusColor,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(status.message, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                
                EliteIconButton(
                    icon = { Text("⏹", color = com.elysium369.meet.ui.theme.MeetColors.error) },
                    onClick = onStop,
                    glowColor = com.elysium369.meet.ui.theme.MeetColors.error,
                    modifier = Modifier.background(com.elysium369.meet.ui.theme.MeetColors.error.copy(alpha = 0.1f), CircleShape)
                )
            }
            
            if (status.currentValues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    status.currentValues.forEach { (name, value) ->
                        Column(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(name, color = Color.LightGray, style = MaterialTheme.typography.labelMedium)
                            Text("${value}", color = statusColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = status.progress,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = statusColor,
                trackColor = Color.White.copy(alpha = 0.05f)
            )
        }
    }
}
