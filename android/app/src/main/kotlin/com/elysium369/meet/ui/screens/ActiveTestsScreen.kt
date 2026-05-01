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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.elysium369.meet.core.obd.ActiveTest
import com.elysium369.meet.core.obd.ActiveTestStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveTestsScreen(navController: NavController, viewModel: com.elysium369.meet.ui.ObdViewModel) {
    val status by viewModel.activeTestStatus.collectAsState()
    val availableTests = viewModel.availableActiveTests
    
    var selectedTest by remember { mutableStateOf<ActiveTest?>(null) }
    var showSafetyWarning by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PRUEBAS ACTIVAS ELITE", color = Color.White, fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Text("←", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            
            // Current Active Test Status
            if (status.isActive) {
                ActiveTestProgressCard(status, onStop = { viewModel.stopActiveTest() })
                Spacer(modifier = Modifier.height(24.dp))
            }

            Text(
                "Controles Bidireccionales",
                color = Color(0xFF00FFCC),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Activa componentes del vehículo para verificar su funcionamiento físico.",
                color = Color.Gray,
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
            AlertDialog(
                onDismissRequest = { showSafetyWarning = false },
                containerColor = Color(0xFF1A1A1A),
                title = { Text("ADVERTENCIA DE SEGURIDAD", color = Color(0xFFFF003C), fontWeight = FontWeight.Black) },
                text = {
                    Column {
                        Text(
                            "Estás por iniciar: ${selectedTest?.name}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Asegúrate de que el vehículo esté en condiciones seguras. MEET verificará automáticamente el voltaje y estado del motor antes de proceder.",
                            color = Color.LightGray
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            selectedTest?.let { viewModel.runActiveTest(it) }
                            showSafetyWarning = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC))
                    ) {
                        Text("CONFIRMAR Y EJECUTAR", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSafetyWarning = false }) {
                        Text("CANCELAR", color = Color.Gray)
                    }
                }
            )
        }
    }
}

@Composable
fun ActiveTestItem(test: ActiveTest, isEnabled: Boolean, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (isEnabled) Color(0xFF111111) else Color(0xFF0A0A0A)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp, 
                if (isEnabled) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f), 
                RoundedCornerShape(12.dp)
            )
            .clickable(enabled = isEnabled) { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(test.name, color = Color.White, fontWeight = FontWeight.Bold)
                Text(test.description, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                if (test.manufacturer != null) {
                    Text(
                        "Específico: ${test.manufacturer}",
                        color = Color(0xFF00FFCC),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Text("▶", color = if (isEnabled) Color(0xFF00FFCC) else Color.DarkGray)
        }
    }
}

@Composable
fun ActiveTestProgressCard(status: ActiveTestStatus, onStop: () -> Unit) {
    Surface(
        color = Color(0xFF00FFCC).copy(alpha = 0.1f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().border(2.dp, Color(0xFF00FFCC), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    progress = status.progress,
                    color = Color(0xFF00FFCC),
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(status.message, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                
                IconButton(onClick = onStop) {
                    Text("⏹", color = Color(0xFFFF003C))
                }
            }
            
            if (status.currentValues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    status.currentValues.forEach { (name, value) ->
                        Column {
                            Text(name, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                            Text("${value}", color = Color(0xFF00FFCC), fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = status.progress,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = Color(0xFF00FFCC),
                trackColor = Color.White.copy(alpha = 0.1f)
            )
        }
    }
}
