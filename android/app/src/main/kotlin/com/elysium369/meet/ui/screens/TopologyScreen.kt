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
import com.elysium369.meet.core.obd.ObdState
import kotlinx.coroutines.launch

enum class ModuleStatus { OK, FAULT, NO_COMM }
data class CarModule(val name: String, val fullName: String, val status: ModuleStatus)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopologyScreen(navController: NavController, viewModel: com.elysium369.meet.ui.ObdViewModel) {
    var isScanning by remember { mutableStateOf(true) }
    var scanError by remember { mutableStateOf<String?>(null) }
    val isPro by viewModel.isAdapterPro.collectAsState()
    val obdState by viewModel.connectionState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val modules = remember { mutableStateListOf<CarModule>() }

    // Escaneo real de módulos al abrir la pantalla
    LaunchedEffect(Unit) {
        if (obdState != ObdState.CONNECTED) {
            // Sin conexión OBD2, mostrar error
            scanError = "Conecta el adaptador OBD2 para escanear la red CAN del vehículo."
            isScanning = false
            return@LaunchedEffect
        }
        
        try {
            // Usar el escaneo real de ObdSession que busca en direcciones CAN físicas
            val results = viewModel.scanModules()
            modules.clear()
            
            if (results.isEmpty()) {
                // Si no se detectan módulos específicos, al menos mostrar el ECM genérico si estamos conectados
                modules.add(CarModule("ECM", "Engine Control Module", ModuleStatus.OK))
            } else {
                for ((name, alive) in results) {
                    val status = if (alive) ModuleStatus.OK else ModuleStatus.NO_COMM
                    // Mapear nombres cortos a largos para mejor UI
                    val fullName = when {
                        name.contains("ECM") -> "Engine Control Module"
                        name.contains("TCM") -> "Transmission Control"
                        name.contains("ABS") -> "Anti-lock Braking"
                        name.contains("SRS") -> "Airbag System"
                        name.contains("BCM") -> "Body Control Module"
                        name.contains("IPC") -> "Instrument Panel"
                        name.contains("HVAC") -> "Climate Control"
                        else -> "Módulo de Control ($name)"
                    }
                    modules.add(CarModule(name.split(" ")[0], fullName, status))
                }
            }
            
            // Verificar DTCs activos para marcar FAULT en el módulo correspondiente (ECM por defecto en OBD2 genérico)
            val dtcs = viewModel.activeDtcs.value
            if (dtcs.isNotEmpty()) {
                val ecmIdx = modules.indexOfFirst { it.name.contains("ECM") }
                if (ecmIdx >= 0) {
                    modules[ecmIdx] = modules[ecmIdx].copy(status = ModuleStatus.FAULT)
                }
            }
        } catch (e: Exception) {
            scanError = "Error al escanear red CAN: ${e.message}"
        }
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
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Enviando requests a módulos de control", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else if (scanError != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Text("⚠️", style = MaterialTheme.typography.displayMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(scanError!!, color = Color(0xFFFFD700), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { navController.navigate("connect") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0A0A)),
                            modifier = Modifier.border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(8.dp)),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("CONECTAR ADAPTADOR", color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold) }
                    }
                }
            } else {
                Text("ARQUITECTURA DE RED DE MÓDULOS", color = Color(0xFF00FFCC).copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                // Visual Topology Canvas
                Box(modifier = Modifier.fillMaxWidth().height(250.dp).background(Color(0xFF0A0A0A), RoundedCornerShape(12.dp)).border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
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
                            drawLine(color = lineColor, start = gateway, end = nodePos, strokeWidth = 3f, pathEffect = if (module.status == ModuleStatus.NO_COMM) pathEffect else null)
                            drawCircle(color = lineColor, radius = 24f, center = nodePos)
                        }
                        drawCircle(color = Color.White, radius = 32f, center = gateway)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                Text("ESTADO DE MÓDULOS", color = Color(0xFF00FFCC).copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(modules.toList()) { mod ->
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
