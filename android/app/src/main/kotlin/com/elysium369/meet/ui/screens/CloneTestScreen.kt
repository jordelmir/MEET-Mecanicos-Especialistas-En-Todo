package com.elysium369.meet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class TestResult(val name: String, val result: String, val color: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloneTestScreen(
    onRunTest: suspend () -> List<TestResult>
) {
    var isRunning by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<TestResult>>(emptyList()) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnóstico de Adaptador", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E))
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)
        ) {
            Text(
                "Modo de Prueba de Campo",
                color = Color(0xFFFF6B35),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Esta herramienta verifica la calidad de tu adaptador OBD2. Los clones baratos tienen problemas de estabilidad. Haz clic en Iniciar para comprobar.",
                color = Color.LightGray
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    isRunning = true
                    results = emptyList()
                    scope.launch {
                        results = onRunTest()
                        isRunning = false
                    }
                },
                enabled = !isRunning,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRunning) "EJECUTANDO PRUEBAS..." else "INICIAR DIAGNÓSTICO", fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(results) { res ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                            Text(res.name, color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(res.result, color = res.color)
                        }
                    }
                }
            }
        }
    }
}
