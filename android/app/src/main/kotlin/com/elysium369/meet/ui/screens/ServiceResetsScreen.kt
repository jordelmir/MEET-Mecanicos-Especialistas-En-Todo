package com.elysium369.meet.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceResetsScreen(navController: NavController, viewModel: com.elysium369.meet.ui.ObdViewModel) {
    val scope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf<ResetOption?>(null) }

    val resetOptions = listOf(
        ResetOption("oil", "Reinicio de Aceite", "Restablece el contador de vida útil del aceite.", "🛢️"),
        ResetOption("brake", "Reinicio de Frenos", "Restablece el sensor de desgaste de pastillas.", "🛑"),
        ResetOption("battery", "Registro de Batería", "Informa a la ECU sobre una batería nueva.", "🔋"),
        ResetOption("sas", "Calibración de Dirección", "Restablece el sensor de ángulo de dirección (SAS).", "🎡"),
        ResetOption("throttle", "Adaptación Mariposa", "Ajusta la posición del cuerpo de aceleración.", "⚙️"),
        ResetOption("dpf", "Regeneración DPF", "Inicia limpieza forzada del filtro de partículas.", "💨"),
        ResetOption("tpms", "Reinicio de TPMS", "Sincroniza los sensores de presión de neumáticos.", "🚗")
    )

    if (showConfirmDialog != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = null },
            containerColor = Color(0xFF111111),
            title = { Text("CONFIRMACIÓN ELITE", color = Color.White, fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "¿Estás seguro de iniciar: ${showConfirmDialog?.title}?\n\n" +
                    "Asegúrate de que el vehículo cumpla con las condiciones de seguridad (Motor apagado/encendido según corresponda, batería > 12V).",
                    color = Color.Gray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val option = showConfirmDialog!!
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
                                else -> {
                                    kotlinx.coroutines.delay(1000)
                                    false
                                }
                            }
                            
                            isRunning = false
                            resultMessage = if (success) {
                                "ÉXITO: ${option.title} completado en ${viewModel.manufacturer.value}."
                            } else {
                                "ERROR: Fallo al ejecutar ${option.title}. Verifica las condiciones de seguridad y compatibilidad."
                            }
                        }
                    }
                ) {
                    Text("INICIAR", color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = null }) {
                    Text("CANCELAR", color = Color.White.copy(alpha = 0.6f))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SERVICE RESETS ELITE", color = Color.White, fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Text("←", color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            
            if (isRunning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp), 
                    color = Color(0xFF00FFCC),
                    trackColor = Color(0xFF00FFCC).copy(alpha = 0.1f)
                )
                Text("EJECUTANDO RUTINA PROFESIONAL...", color = Color(0xFF00FFCC), modifier = Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.Bold)
            }

            if (resultMessage.isNotEmpty()) {
                Surface(
                    color = if (resultMessage.contains("ÉXITO")) Color(0xFF00FFCC).copy(alpha = 0.15f) else Color(0xFFFF003C).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (resultMessage.contains("ÉXITO")) Color(0xFF00FFCC) else Color(0xFFFF003C)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                ) {
                    Text(resultMessage, modifier = Modifier.padding(16.dp), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(resetOptions) { option ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
                    ) {
                        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color.Black,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(56.dp).border(1.dp, Color(0xFF00FFCC).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(option.icon, style = MaterialTheme.typography.headlineSmall)
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(option.title, color = Color.White, fontWeight = FontWeight.ExtraBold)
                                Text(option.description, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = { showConfirmDialog = option },
                                enabled = !isRunning && viewModel.connectionState.value == com.elysium369.meet.core.obd.ObdState.CONNECTED,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00FFCC),
                                    contentColor = Color.Black,
                                    disabledContainerColor = Color.DarkGray
                                )
                            ) {
                                Text("RESETEAR", fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

data class ResetOption(val id: String, val title: String, val description: String, val icon: String)
