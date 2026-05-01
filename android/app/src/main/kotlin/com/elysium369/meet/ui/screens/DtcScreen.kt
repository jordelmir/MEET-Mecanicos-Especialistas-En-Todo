package com.elysium369.meet.ui.screens

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
import com.elysium369.meet.ui.ObdViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DtcScreen(navController: NavController, viewModel: ObdViewModel) {
    val activeDtcs by viewModel.activeDtcs.collectAsState()
    val pendingDtcs by viewModel.pendingDtcs.collectAsState()
    val permanentDtcs by viewModel.permanentDtcs.collectAsState()
    val readiness by viewModel.readinessMonitors.collectAsState()
    val clearResult by viewModel.clearDtcResult.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("⚠️ Borrar Códigos", color = Color.White) },
            text = { Text("Esto enviará Mode 04 al vehículo. Se borrarán TODOS los DTCs activos y pendientes, se apagará la luz MIL (Check Engine), y se resetearán los monitores de emisiones.\n\n¿Continuar?", color = Color.Gray) },
            confirmButton = { TextButton(onClick = { showClearDialog = false; coroutineScope.launch { viewModel.clearDtcs() } }) { Text("BORRAR", color = Color(0xFFFF003C), fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancelar", color = Color.Gray) } },
            containerColor = Color(0xFF0A0A0A)
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Diagnóstico DTC", color = Color.White, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0A)),
                    actions = {
                        TextButton(onClick = { coroutineScope.launch { viewModel.refreshDiagnostics() } }) {
                            Text("ESCANEAR", color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { showClearDialog = true }) {
                            Text("BORRAR", color = Color(0xFFFF003C), fontWeight = FontWeight.Bold)
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTab, containerColor = Color(0xFF0A0A0A), contentColor = Color(0xFF00FFCC)) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Activos (${activeDtcs.size})", color = if (selectedTab == 0) Color(0xFFFF003C) else Color.Gray, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Pending (${pendingDtcs.size})", color = if (selectedTab == 1) Color(0xFFFFD700) else Color.Gray, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold) })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Perm (${permanentDtcs.size})", color = if (selectedTab == 2) Color(0xFF00BFFF) else Color.Gray, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold) })
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Monitores", color = if (selectedTab == 3) Color(0xFF00FFCC) else Color.Gray, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold) })
                    Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }, text = { Text("Manual", color = if (selectedTab == 4) Color.White else Color.Gray, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold) })
                }
            }
        },
        containerColor = Color.Black
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Clear result banner
            if (clearResult != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF00FFCC).copy(alpha = 0.5f), RoundedCornerShape(8.dp))) {
                        Text(clearResult!!, color = Color.White, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            when (selectedTab) {
                0 -> { // Active DTCs (Mode 03)
                    if (activeDtcs.isEmpty()) {
                        item { EmptyDtcState("No hay códigos de falla activos", Color(0xFF00FFCC)) }
                    } else {
                        items(activeDtcs) { dtc -> DtcCard(dtc, "ACTIVO", Color(0xFFFF003C), navController, viewModel) }
                    }
                }
                1 -> { // Pending DTCs (Mode 07)
                    if (pendingDtcs.isEmpty()) {
                        item { EmptyDtcState("No hay códigos pendientes.\nEstos son códigos que aún no encendieron la luz MIL.", Color(0xFFFFD700)) }
                    } else {
                        items(pendingDtcs) { dtc -> DtcCard(dtc, "PENDIENTE", Color(0xFFFFD700), navController, viewModel) }
                    }
                }
                2 -> { // Permanent DTCs (Mode 0A)
                    if (permanentDtcs.isEmpty()) {
                        item { EmptyDtcState("No hay códigos permanentes.\nEstos son códigos que NO se pueden borrar manualmente.", Color(0xFF00BFFF)) }
                    } else {
                        items(permanentDtcs) { dtc -> DtcCard(dtc, "PERMANENTE", Color(0xFF00BFFF), navController, viewModel) }
                    }
                }
                3 -> { // Readiness Monitors
                    item { ReadinessMonitorsCard(readiness, coroutineScope, viewModel) }
                }
                4 -> { // Manual Search
                    item { ManualSearchTab(navController) }
                }
            }
        }
    }
}

@Composable
private fun DtcCard(dtc: String, severity: String, color: Color, navController: NavController, viewModel: ObdViewModel) {
    val dtcInfo = com.elysium369.meet.core.obd.DtcDatabaseHelper.getDtcInfo(dtc)
    val desc = dtcInfo?.descriptionEs ?: com.elysium369.meet.core.obd.DtcDecoder.getLocalDescription(dtc)
    val causes = dtcInfo?.possibleCauses
    
    Card(colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp), modifier = Modifier.border(1.dp, color, RoundedCornerShape(4.dp))) {
                    Text(severity, color = color, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(dtc, color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(desc, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            if (causes != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(causes, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(12.dp))
            
            val freezeFrame by viewModel.freezeFrameData.collectAsState()
            
            if (freezeFrame.isNotEmpty() && freezeFrame["02"]?.contains(dtc) == true) {
                Text("❄️ DATOS DE CUADRO CONGELADO:", color = Color(0xFF00BFFF), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                freezeFrame.filter { it.key != "02" }.forEach { (pid, valStr) ->
                    val pidName = when(pid) {
                        "05" -> "Temp. Refrigerante"
                        "0C" -> "RPM Motor"
                        "0D" -> "Velocidad"
                        "04" -> "Carga Motor"
                        else -> "PID $pid"
                    }
                    Text("$pidName: $valStr", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { navController.navigate("ai/$dtc") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0A0A)),
                    modifier = Modifier.weight(1f).border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(8.dp)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("🤖 IA", color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold)
                }
                
                val coroutineScope = rememberCoroutineScope()
                Button(
                    onClick = { coroutineScope.launch { viewModel.refreshFreezeFrame(dtc) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0A0A)),
                    modifier = Modifier.weight(1f).border(1.dp, Color(0xFF00BFFF), RoundedCornerShape(8.dp)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("❄️ FF DATA", color = Color(0xFF00BFFF), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EmptyDtcState(message: String, color: Color) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("✅", style = MaterialTheme.typography.displayMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = color, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun ReadinessMonitorsCard(readiness: com.elysium369.meet.core.obd.ReadinessResult?, coroutineScope: kotlinx.coroutines.CoroutineScope, viewModel: ObdViewModel) {
    if (readiness == null) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📊", style = MaterialTheme.typography.displayMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Monitores de emisiones no leídos aún.", color = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { coroutineScope.launch { viewModel.refreshDiagnostics() } }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0A0A)), modifier = Modifier.border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp)) {
                    Text("LEER MONITORES", color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        // MIL Status
        Card(colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().border(1.dp, if (readiness.milOn) Color(0xFFFF003C) else Color(0xFF00FFCC), RoundedCornerShape(12.dp))) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("LUZ MIL (CHECK ENGINE)", color = Color.Gray, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(if (readiness.milOn) "🔴 ENCENDIDA" else "🟢 APAGADA", color = if (readiness.milOn) Color(0xFFFF003C) else Color(0xFF00FFCC), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                }
                Text("${readiness.dtcCount} DTCs", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        // Monitors
        Text("MONITORES DE EMISIÓN", color = Color(0xFF00FFCC).copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        val passedCount = readiness.monitors.count { it.complete }
        val totalCount = readiness.monitors.size
        Text("$passedCount / $totalCount completados", color = if (passedCount == totalCount) Color(0xFF00FFCC) else Color(0xFFFFD700), style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(8.dp))
        readiness.monitors.forEach { monitor ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).border(1.dp, (if (monitor.complete) Color(0xFF00FFCC) else Color(0xFFFFD700)).copy(alpha = 0.3f), RoundedCornerShape(8.dp))) {
                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(monitor.name, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    Text(if (monitor.complete) "✅ Listo" else "⏳ Incompleto", color = if (monitor.complete) Color(0xFF00FFCC) else Color(0xFFFFD700), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualSearchTab(navController: NavController) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<List<com.elysium369.meet.core.obd.DtcInfo>?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it.uppercase().trim() },
            label = { Text("Ingresar Código (Ej. P0300)", color = Color.Gray) },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFF00FFCC),
                unfocusedBorderColor = Color.Gray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color(0xFF00FFCC)
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                if (searchQuery.isNotEmpty()) {
                    searchResult = com.elysium369.meet.core.obd.DtcDatabaseHelper.searchDtc(searchQuery)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0A0A)),
            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("BUSCAR", color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (searchResult != null) {
            if (searchResult!!.isEmpty()) {
                EmptyDtcState("No se encontró el código en la base de datos.", Color.Gray)
            } else {
                Text("Resultados (${searchResult!!.size})", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                searchResult!!.forEach { dtc ->
                    val color = when (dtc.severity.uppercase()) {
                        "HIGH" -> Color(0xFFFF003C)
                        "MODERATE" -> Color(0xFFFFD700)
                        else -> Color(0xFF00FFCC)
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp), modifier = Modifier.border(1.dp, color, RoundedCornerShape(4.dp))) {
                                    Text(dtc.severity.uppercase(), color = color, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(dtc.code, color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(dtc.descriptionEs, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            if (dtc.descriptionEn.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(dtc.descriptionEn, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Posibles causas / Recomendación:", color = Color(0xFF00FFCC).copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(dtc.possibleCauses, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { navController.navigate("ai/${dtc.code}") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0A0A)),
                                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(8.dp)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("🤖 CONSULTAR IA", color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
