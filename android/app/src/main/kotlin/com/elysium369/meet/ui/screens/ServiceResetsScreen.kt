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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.elysium369.meet.ui.components.EliteScrollContainer
import com.elysium369.meet.ui.components.eliteScrollbar
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
                    }
                ) {
                    Text("INICIAR", color = Color(0xFF39FF14), fontWeight = FontWeight.Bold)
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0E1A))
            )
        },
        containerColor = Color(0xFF0A0E1A)
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            
            if (isRunning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp), 
                    color = Color(0xFF39FF14),
                    trackColor = Color(0xFF39FF14).copy(alpha = 0.1f)
                )
                Text("EJECUTANDO RUTINA PROFESIONAL...", color = Color(0xFF39FF14), modifier = Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.Bold)
            }

            if (resultMessage.isNotEmpty()) {
                Surface(
                    color = if (resultMessage.contains("ÉXITO")) Color(0xFF39FF14).copy(alpha = 0.15f) else Color(0xFFFF003C).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (resultMessage.contains("ÉXITO")) Color(0xFF39FF14) else Color(0xFFFF003C)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                ) {
                    Text(resultMessage, modifier = Modifier.padding(16.dp), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            val listState = rememberLazyListState()
            
            EliteScrollContainer(modifier = Modifier.weight(1f), fadeColor = Color(0xFF0A0E1A)) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp) // Space for scrollbar
                        .eliteScrollbar(listState)
                ) {
                    items(resetOptions) { option ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E1A)),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF39FF14).copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = Color.Black,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .size(60.dp)
                                        .border(1.dp, Color(0xFF39FF14).copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(option.icon, style = MaterialTheme.typography.headlineMedium)
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        option.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        option.description,
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodySmall,
                                        lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Button(
                                    onClick = { showConfirmDialog = option },
                                    enabled = !isRunning && connectionState == com.elysium369.meet.core.obd.ObdState.CONNECTED,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF39FF14),
                                        contentColor = Color.Black,
                                        disabledContainerColor = Color.DarkGray
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text("RUN", fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class ResetOption(val id: String, val title: String, val description: String, val icon: String)
