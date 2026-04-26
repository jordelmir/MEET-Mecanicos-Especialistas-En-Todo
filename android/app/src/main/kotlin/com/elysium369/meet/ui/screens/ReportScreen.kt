package com.elysium369.meet.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(navController: NavController, viewModel: ObdViewModel) {
    var isGenerating by remember { mutableStateOf(false) }
    var reportUrl by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    val activeDtcs by viewModel.activeDtcs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reportes Pre/Post Scan", color = Color.White) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Text("←", color = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0A))
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            
            if (isGenerating) {
                CircularProgressIndicator(color = Color(0xFFCC00FF))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Generando Reporte Profesional...", color = Color(0xFFCC00FF), fontWeight = FontWeight.Bold)
                Text("Renderizando PDF con Cyberpunk Styling", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            } else if (reportUrl != null) {
                Text("✅ REPORTE GENERADO", color = Color(0xFF00FFCC), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(16.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF00FFCC).copy(alpha = 0.3f), RoundedCornerShape(12.dp))) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("MEET_Scan_Report_2026.pdf", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Guardado en /Downloads", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { /* Share Intent */ }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0A0A)), modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp)) {
                            Text("COMPARTIR POR WHATSAPP", color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(onClick = { reportUrl = null }) {
                    Text("Generar otro reporte", color = Color.Gray)
                }
            } else {
                Text("📄", style = MaterialTheme.typography.displayLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Diagnóstico Oficial", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Genera un reporte PDF profesional con el logo del taller, códigos DTC encontrados (${activeDtcs.size}), estado de emisiones y telemetría.", color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = { 
                        coroutineScope.launch {
                            isGenerating = true
                            delay(2500L) // Simulate PDF rendering
                            isGenerating = false
                            reportUrl = "content://downloads/MEET_Scan_Report_2026.pdf"
                        }
                    }, 
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC00FF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("GENERAR REPORTE PDF", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
