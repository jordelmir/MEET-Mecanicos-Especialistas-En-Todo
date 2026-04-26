package com.elysium369.meet.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.GaugeWidget
import com.elysium369.meet.ui.components.WaveGraphWidget
import kotlinx.coroutines.launch

enum class GaugeType { CIRCULAR, WAVE }
data class GaugeConfig(val id: String, val label: String, val pid: String, val minVal: Float, val maxVal: Float, val unit: String, val type: GaugeType = GaugeType.CIRCULAR)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(navController: NavController, viewModel: ObdViewModel) {
    val liveData by viewModel.liveData.collectAsState()
    val state by viewModel.connectionState.collectAsState()
    val isLogging by viewModel.isLogging.collectAsState()
    val dataLog by viewModel.dataLog.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val defaultGauges = remember {
        listOf(
            GaugeConfig("1", "RPM", "010C", 0f, 8000f, "rpm"),
            GaugeConfig("2", "Velocidad", "010D", 0f, 220f, "km/h"),
            GaugeConfig("3", "Temp Motor", "0105", -40f, 150f, "°C"),
            GaugeConfig("4", "Carga", "0104", 0f, 100f, "%"),
            GaugeConfig("5", "Voltaje", "AT RV", 10f, 16f, "V", GaugeType.WAVE),
            GaugeConfig("6", "Presión MAP", "010B", 0f, 255f, "kPa"),
            GaugeConfig("7", "Flujo MAF", "0110", 0f, 655f, "g/s", GaugeType.WAVE),
            GaugeConfig("8", "Acelerador", "0111", 0f, 100f, "%", GaugeType.WAVE),
            GaugeConfig("9", "Temp Admisión", "010F", -40f, 150f, "°C"),
            GaugeConfig("10", "Avance Enc.", "010E", -64f, 64f, "°", GaugeType.WAVE),
            GaugeConfig("11", "Combustible", "012F", 0f, 100f, "%"),
            GaugeConfig("12", "Pres. Comb.", "0123", 0f, 6550f, "kPa")
        )
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var selectedTab by remember { mutableIntStateOf(0) }
    var hudMode by remember { mutableStateOf(false) }

    // HUD Mode — full-screen speed+RPM mirrored for windshield
    if (hudMode) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                val speed = liveData["010D"] ?: 0f
                val rpm = liveData["010C"] ?: 0f
                Text("${speed.toInt()}", color = Color(0xFF00FFCC), fontSize = 120.sp, fontWeight = FontWeight.Black)
                Text("km/h", color = Color(0xFF00FFCC).copy(alpha = 0.5f), fontSize = 24.sp)
                Spacer(modifier = Modifier.height(32.dp))
                Text("${rpm.toInt()} RPM", color = Color(0xFFCC00FF), fontSize = 48.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { hudMode = false }) { Text("SALIR HUD", color = Color.Gray) }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Scanner en Vivo", color = Color.White, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0A)),
                    actions = {
                        // Logging indicator
                        if (isLogging) {
                            Surface(color = Color(0xFFFF003C).copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp), modifier = Modifier.border(1.dp, Color(0xFFFF003C), RoundedCornerShape(4.dp))) {
                                Text("● REC ${dataLog.size}", color = Color(0xFFFF003C), fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        if (state == ObdState.DISCONNECTED) {
                            TextButton(onClick = { navController.navigate("connect") }) { Text("CONECTAR", color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold) }
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTab, containerColor = Color(0xFF0A0A0A), contentColor = Color(0xFF00FFCC), indicator = { tabPositions -> TabRowDefaults.Indicator(Modifier.tabIndicatorOffset(tabPositions[selectedTab]), color = Color(0xFF00FFCC), height = 3.dp) }) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("DASHBOARD", color = if (selectedTab == 0) Color(0xFF00FFCC) else Color.Gray, fontWeight = FontWeight.Bold) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("SENSORES", color = if (selectedTab == 1) Color(0xFF00FFCC) else Color.Gray, fontWeight = FontWeight.Bold) })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("TOOLS", color = if (selectedTab == 2) Color(0xFF00FFCC) else Color.Gray, fontWeight = FontWeight.Bold) })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    coroutineScope.launch { 
                        snackbarHostState.showSnackbar("Dashboard Builder (Pro) - Próximamente", duration = SnackbarDuration.Short) 
                    } 
                },
                containerColor = Color.Black, 
                shape = RoundedCornerShape(12.dp), 
                modifier = Modifier.border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar", tint = Color(0xFF00FFCC))
            }
        },
        containerColor = Color.Black
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> { // DASHBOARD ELITE
                    LazyVerticalGrid(columns = GridCells.Fixed(if (isLandscape) 3 else 2), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                        items(defaultGauges) { gauge ->
                            val currentValue = liveData[gauge.pid] ?: 0f
                            Box(modifier = Modifier.fillMaxWidth().background(Color.Black, RoundedCornerShape(12.dp)).border(1.dp, Color(0xFF00FFCC).copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(2.dp)) {
                                if (gauge.type == GaugeType.WAVE) { WaveGraphWidget(label = gauge.label, currentValue = currentValue, minVal = gauge.minVal, maxVal = gauge.maxVal, unit = gauge.unit, lineColor = Color(0xFF00FFCC)) }
                                else { GaugeWidget(label = gauge.label, value = currentValue, minVal = gauge.minVal, maxVal = gauge.maxVal, unit = gauge.unit, warningThreshold = gauge.maxVal * 0.75f, criticalThreshold = gauge.maxVal * 0.90f) }
                            }
                        }
                    }
                }
                1 -> { // ALL SENSORS RAW
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                        item { Text("TELEMETRÍA EN TIEMPO REAL", color = Color(0xFF00FFCC).copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp)) }
                        items(defaultGauges.size) { index ->
                            val gauge = defaultGauges[index]
                            val currentValue = liveData[gauge.pid] ?: 0f
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(Color.Black, RoundedCornerShape(8.dp)).border(1.dp, Color(0xFF00FFCC).copy(alpha = 0.15f), RoundedCornerShape(8.dp)).padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column { Text(gauge.label, color = Color.White, style = MaterialTheme.typography.bodyMedium); Text("PID: ${gauge.pid}", color = Color.DarkGray, style = MaterialTheme.typography.labelSmall) }
                                Text("${String.format("%.1f", currentValue)} ${gauge.unit}", color = Color(0xFF00FFCC), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                2 -> { // TOOLS
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { Text("HERRAMIENTAS PRO", color = Color(0xFF00FFCC).copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                        // Demo Mode (inject fake data for testing without adapter)
                        item {
                            var demoActive by remember { mutableStateOf(false) }
                            ToolCard("🧪", if (demoActive) "Demo Activo — Simulando datos" else "Modo Demo", "Inyectar datos simulados para probar sin adaptador OBD2", Color(0xFFFFD700)) {
                                demoActive = !demoActive
                                if (demoActive) {
                                    coroutineScope.launch {
                                        while (demoActive) {
                                            val rpm = (800f + (Math.random() * 4000f)).toFloat()
                                            val speed = (Math.random() * 180f).toFloat()
                                            val temp = (70f + (Math.random() * 40f)).toFloat()
                                            val throttle = (Math.random() * 100f).toFloat()
                                            viewModel.updateLiveData("010C", rpm)
                                            viewModel.updateLiveData("010D", speed)
                                            viewModel.updateLiveData("0105", temp)
                                            viewModel.updateLiveData("0104", throttle)
                                            viewModel.updateLiveData("010B", (50f + (Math.random() * 80f)).toFloat())
                                            viewModel.updateLiveData("0110", (2f + (Math.random() * 30f)).toFloat())
                                            viewModel.updateLiveData("0111", throttle)
                                            viewModel.updateLiveData("010F", (20f + (Math.random() * 30f)).toFloat())
                                            kotlinx.coroutines.delay(300L)
                                        }
                                    }
                                }
                            }
                        }
                        // HUD Mode
                        item {
                            ToolCard("🖥️", "HUD Mode", "Velocímetro para parabrisas", Color(0xFF00FFCC)) { hudMode = true }
                        }
                        // Data Logging
                        item {
                            ToolCard(if (isLogging) "⏹️" else "⏺️", if (isLogging) "Detener Grabación (${dataLog.size} pts)" else "Iniciar Grabación", "Grabar datos de sensores en tiempo real (cada 500ms)", Color(0xFFFF003C)) { if (isLogging) viewModel.stopDataLogging() else viewModel.startDataLogging() }
                        }
                        // CSV Export
                        item {
                            var exportResult by remember { mutableStateOf<String?>(null) }
                            Column {
                                ToolCard("📄", "Exportar CSV (${dataLog.size} puntos)", "Exportar datos grabados a archivo CSV", Color(0xFFCC00FF)) {
                                    val path = viewModel.saveCsvToFile()
                                    exportResult = if (path != null) "✅ Guardado en: $path" else "⚠️ No hay datos grabados. Inicia la grabación primero."
                                }
                                if (exportResult != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Card(colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF00FFCC).copy(alpha = 0.3f), RoundedCornerShape(8.dp))) {
                                        Text(exportResult!!, color = Color(0xFF00FFCC), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        // Fuel Economy
                        item {
                            val maf = liveData["0110"] ?: 0f
                            val speed = liveData["010D"] ?: 0f
                            val lPer100km = if (speed > 0 && maf > 0) (maf * 3600f) / (speed * 14.7f * 710f) * 100f else 0f
                            Card(colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF00FFCC).copy(alpha = 0.3f), RoundedCornerShape(12.dp))) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("⛽ CONSUMO EN TIEMPO REAL", color = Color(0xFF00FFCC).copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("${String.format("%.1f", lPer100km)} L/100km", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                                    Text("MAF: ${String.format("%.1f", maf)} g/s • Speed: ${speed.toInt()} km/h", color = Color.DarkGray, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        // Performance Test 0-100
                        item {
                            val speed = liveData["010D"] ?: 0f
                            var isTesting by remember { mutableStateOf(false) }
                            var time0to100 by remember { mutableStateOf<Long?>(null) }
                            var startTime by remember { mutableStateOf<Long?>(null) }
                            
                            // Auto-stop the timer if we hit 100
                            LaunchedEffect(speed) {
                                if (isTesting) {
                                    if (speed > 0f && startTime == null) {
                                        startTime = System.currentTimeMillis()
                                    }
                                    if (speed >= 100f && startTime != null) {
                                        time0to100 = System.currentTimeMillis() - startTime!!
                                        isTesting = false
                                    }
                                }
                            }
                            
                            Card(colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFFFD700).copy(alpha = 0.3f), RoundedCornerShape(12.dp))) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text("🏁 TEST 0-100 KM/H", color = Color(0xFFFFD700).copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        Button(onClick = { 
                                            isTesting = !isTesting
                                            if (isTesting) { time0to100 = null; startTime = null }
                                        }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0A0A)), modifier = Modifier.border(1.dp, Color(0xFFFFD700), RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp)) {
                                            Text(if (isTesting) "CANCELAR" else "INICIAR", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (time0to100 != null) {
                                        Text("${String.format("%.2f", time0to100!! / 1000f)} segundos", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                                    } else if (isTesting) {
                                        if (startTime != null) {
                                            val currentRunTime = System.currentTimeMillis() - startTime!!
                                            Text("${String.format("%.2f", currentRunTime / 1000f)} s...", color = Color(0xFFFFD700), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                                        } else {
                                            Text("Acelera para comenzar...", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    } else {
                                        Text("Presiona iniciar y acelera", color = Color.DarkGray, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Text("Speed: ${speed.toInt()} km/h", color = Color.DarkGray, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        // Battery Health
                        item {
                            val voltage = liveData["AT RV"] ?: (liveData["0142"] ?: 0f)
                            val status = when {
                                voltage < 10f -> "BATERÍA MUERTA" to Color(0xFFFF003C)
                                voltage in 10f..11.8f -> "CARGA BAJA" to Color(0xFFFFD700)
                                voltage in 11.9f..12.8f -> "BATERÍA OK (Motor apagado)" to Color(0xFF00FFCC)
                                voltage in 12.9f..14.8f -> "ALTERNADOR OK (Cargando)" to Color(0xFF00FFCC)
                                voltage > 14.8f -> "SOBRECARGA" to Color(0xFFFF003C)
                                else -> "LEYENDO..." to Color.Gray
                            }
                            Card(colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().border(1.dp, status.second.copy(alpha = 0.3f), RoundedCornerShape(12.dp))) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("🔋 SALUD DE BATERÍA", color = status.second.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("${String.format("%.1f", voltage)} V", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                                    Text(status.first, color = status.second, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ToolCard(icon: String, title: String, desc: String, color: Color, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp)), onClick = onClick) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(desc, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
