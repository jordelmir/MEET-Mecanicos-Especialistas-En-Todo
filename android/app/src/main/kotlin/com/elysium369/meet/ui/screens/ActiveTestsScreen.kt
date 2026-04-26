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
fun ActiveTestsScreen(navController: NavController) {
    val tests = listOf(
        "Apagar Inyector Cilindro 1",
        "Apagar Inyector Cilindro 2",
        "Prueba de Bomba de Combustible",
        "Test Electroventilador (Alta velocidad)",
        "Test Electroventilador (Baja velocidad)",
        "Compresor A/C Clutch Relay",
        "Prueba de Válvula EVAP Purge",
        "Prueba de Motores de Ventana (BCM)"
    )
    
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Controles Bi-Direccionales", color = Color.White) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Text("←", color = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0A))
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("PRUEBAS ACTIVAS (OEM)", color = Color(0xFFFF003C).copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Activa o desactiva actuadores del vehículo enviando comandos directos a la ECU. Precaución al usar.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(tests) { test ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFFF003C).copy(alpha = 0.3f), RoundedCornerShape(12.dp)).clickable {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Ejecutando: $test... (Simulado para demostración)", duration = SnackbarDuration.Short)
                        }
                    }) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(test, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("▶", color = Color(0xFFFF003C))
                        }
                    }
                }
            }
        }
    }
}
