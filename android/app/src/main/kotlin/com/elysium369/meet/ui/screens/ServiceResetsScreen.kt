package com.elysium369.meet.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
    val isPro by viewModel.isAdapterPro.collectAsState()
    val resets = listOf(
        "Oil Maintenance Reset" to "Reseteo del contador de cambio de aceite y luz de mantenimiento.",
        "EPB Retract" to "Retracción de calipers electrónicos (Freno de mano eléctrico) para cambio de pastillas.",
        "DPF Regeneration" to "Regeneración forzada del filtro de partículas Diésel.",
        "SAS Calibration" to "Calibración del sensor del ángulo de giro (Steering Angle Sensor).",
        "Throttle Adaptation" to "Aprendizaje del cuerpo de aceleración (Idle relearn).",
        "BMS Reset" to "Reseteo del sistema de gestión de batería al reemplazarla.",
        "TPMS Reset" to "Reaprendizaje de sensores de presión de llantas."
    )
    
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Service Resets", color = Color.White) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Text("←", color = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0A))
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("FUNCIONES ESPECIALES", color = Color(0xFFFFD700).copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Procedimientos de taller mecánico para calibración y reseteo de módulos tras mantenimiento.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(resets) { reset ->
                    val isSupported = isPro
                    val cardColor = if (isSupported) Color(0xFFFFD700) else Color.DarkGray
                    Card(colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().border(1.dp, cardColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).clickable(enabled = isSupported) {
                        coroutineScope.launch {
                            val response = viewModel.sendRawCommand("31 01 FF 00") 
                            if(response.contains("71") || response.contains("OK")) { 
                                snackbarHostState.showSnackbar("Mantenimiento reseteado exitosamente")
                            } else {
                                snackbarHostState.showSnackbar("Respuesta ECU: $response", duration = SnackbarDuration.Short)
                            }
                        }
                    }) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(reset.first, color = if (isSupported) Color(0xFFFFD700) else Color.Gray, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                                if (!isSupported) Text("PRO ONLY", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(reset.second, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
