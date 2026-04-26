package com.elysium369.meet.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay

enum class ModuleStatus { OK, FAULT, NO_COMM }
data class CarModule(val name: String, val fullName: String, val status: ModuleStatus)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopologyScreen(navController: NavController) {
    var isScanning by remember { mutableStateOf(true) }
    
    val modules = remember {
        listOf(
            CarModule("ECM", "Engine Control Module", ModuleStatus.FAULT),
            CarModule("TCM", "Transmission Control", ModuleStatus.OK),
            CarModule("ABS", "Anti-lock Braking", ModuleStatus.OK),
            CarModule("SRS", "Airbag System", ModuleStatus.OK),
            CarModule("BCM", "Body Control Module", ModuleStatus.NO_COMM),
            CarModule("IPC", "Instrument Panel", ModuleStatus.OK),
            CarModule("HVAC", "Climate Control", ModuleStatus.OK)
        )
    }

    LaunchedEffect(Unit) {
        delay(3000L) // Simulate network scan
        isScanning = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mapeo Topológico", color = Color.White) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Text("←", color = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0A))
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (isScanning) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF00FFCC))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Escaneando Red CAN...", color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Text("ARQUITECTURA DE RED DE MÓDULOS", color = Color(0xFF00FFCC).copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                // Simulated Visual Topology
                Box(modifier = Modifier.fillMaxWidth().height(250.dp).background(Color(0xFF0A0A0A), RoundedCornerShape(12.dp)).border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        
                        // Gateway Node (Center)
                        val gateway = Offset(canvasWidth / 2, canvasHeight / 2)
                        
                        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        
                        modules.forEachIndexed { index, module ->
                            val angle = (index * (360f / modules.size)) * (Math.PI / 180f)
                            val radius = canvasHeight / 2.5f
                            val nodePos = Offset(
                                (gateway.x + radius * Math.cos(angle)).toFloat(),
                                (gateway.y + radius * Math.sin(angle)).toFloat()
                            )
                            
                            val lineColor = when (module.status) {
                                ModuleStatus.OK -> Color(0xFF00FFCC)
                                ModuleStatus.FAULT -> Color(0xFFFF003C)
                                ModuleStatus.NO_COMM -> Color.Gray
                            }
                            
                            drawLine(
                                color = lineColor,
                                start = gateway,
                                end = nodePos,
                                strokeWidth = 3f,
                                pathEffect = if (module.status == ModuleStatus.NO_COMM) pathEffect else null
                            )
                            
                            drawCircle(color = lineColor, radius = 24f, center = nodePos)
                        }
                        
                        drawCircle(color = Color.White, radius = 32f, center = gateway)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                Text("ESTADO DE MÓDULOS", color = Color(0xFF00FFCC).copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(modules) { mod ->
                        val color = when (mod.status) {
                            ModuleStatus.OK -> Color(0xFF00FFCC)
                            ModuleStatus.FAULT -> Color(0xFFFF003C)
                            ModuleStatus.NO_COMM -> Color.Gray
                        }
                        val statusText = when (mod.status) {
                            ModuleStatus.OK -> "NORMAL"
                            ModuleStatus.FAULT -> "DTC DETECTADO"
                            ModuleStatus.NO_COMM -> "SIN COMUNICACIÓN"
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))) {
                            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(mod.name, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(mod.fullName, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                                }
                                Surface(color = color.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp), modifier = Modifier.border(1.dp, color, RoundedCornerShape(4.dp))) {
                                    Text(statusText, color = color, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
