package com.elysium369.meet.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.screens.scanner.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(navController: NavController, viewModel: ObdViewModel) {
    val liveData by viewModel.liveData.collectAsState()
    val state by viewModel.connectionState.collectAsState()
    val isLogging by viewModel.isLogging.collectAsState()
    val dataLog by viewModel.dataLog.collectAsState()
    val cloudSyncState by viewModel.cloudSyncState.collectAsState()
    val highSpeedMode by viewModel.highSpeedMode.collectAsState()
    val qosMetrics by viewModel.qosMetrics.collectAsState()
    val anomalousPids by viewModel.anomalousPids.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val defaultGauges = remember {
        listOf(
            GaugeConfig("1", "RPM", "010C", 0f, 8000f, "rpm"),
            GaugeConfig("2", "Velocidad", "010D", 0f, 255f, "km/h"),
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

    // AI Anomaly Snackbar Notification
    LaunchedEffect(anomalousPids) {
        if (anomalousPids.isNotEmpty()) {
            val sensorNames = anomalousPids.map { it.pid }.joinToString(", ")
            snackbarHostState.showSnackbar(
                message = "🚨 IA detectó anomalía en sensores: $sensorNames",
                actionLabel = "VER",
                duration = SnackbarDuration.Long
            )
        }
    }

    // HUD Mode Overlay
    if (hudMode) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp), 
                horizontalAlignment = Alignment.CenterHorizontally, 
                verticalArrangement = Arrangement.Center
            ) {
                val speed = liveData["010D"] ?: 0f
                val rpm = liveData["010C"] ?: 0f
                Text("${speed.toInt()}", color = Color(0xFF39FF14), fontSize = 120.sp, fontWeight = FontWeight.Black)
                Text("km/h", color = Color(0xFF39FF14).copy(alpha = 0.5f), fontSize = 24.sp)
                Spacer(modifier = Modifier.height(32.dp))
                Text("${rpm.toInt()} RPM", color = Color(0xFF00AAFF), fontSize = 48.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { hudMode = false }) { 
                    Text("SALIR HUD", color = Color.Gray) 
                }
            }
        }
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Scanner en Vivo", color = Color.White, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0E1A)),
                    actions = {
                        // High Speed Mode Indicator
                        if (highSpeedMode) {
                            Surface(
                                color = Color(0xFF39FF14).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.border(1.dp, Color(0xFF39FF14), RoundedCornerShape(4.dp))
                            ) {
                                Text(
                                    "HIGH-SPEED: ${qosMetrics.cmdsPerSecond.toInt()}Hz", 
                                    color = Color(0xFF39FF14), 
                                    fontWeight = FontWeight.Black, 
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), 
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        
                        // Logging indicator
                        if (isLogging) {
                            Surface(
                                color = Color(0xFFFF003C).copy(alpha = 0.2f), 
                                shape = RoundedCornerShape(4.dp), 
                                modifier = Modifier.border(1.dp, Color(0xFFFF003C), RoundedCornerShape(4.dp))
                            ) {
                                Text(
                                    "● REC ${dataLog.size}", 
                                    color = Color(0xFFFF003C), 
                                    fontWeight = FontWeight.Black, 
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), 
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        if (state == ObdState.DISCONNECTED) {
                            TextButton(onClick = { navController.navigate("connect") }) { 
                                Text("CONECTAR", color = Color(0xFF39FF14), fontWeight = FontWeight.Bold) 
                            }
                        }
                    }
                )
                
                // CLOUD SYNC INDICATOR
                if (cloudSyncState.isNotBlank() && cloudSyncState != "Desconectado") {
                    val bgColor = if (cloudSyncState.contains("❌")) Color(0xFFFF003C).copy(alpha = 0.2f) else Color(0xFF39FF14).copy(alpha = 0.1f)
                    val textColor = if (cloudSyncState.contains("❌")) Color(0xFFFF003C) else Color(0xFF39FF14)
                    Surface(
                        color = bgColor,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).border(1.dp, textColor, RoundedCornerShape(4.dp)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = cloudSyncState,
                            color = textColor,
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                TabRow(
                    selectedTabIndex = selectedTab, 
                    containerColor = Color(0xFF0A0E1A), 
                    contentColor = Color(0xFF39FF14), 
                    indicator = { tabPositions -> 
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]), 
                            color = Color(0xFF39FF14), 
                            height = 3.dp
                        ) 
                    }
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("DASHBOARD", color = if (selectedTab == 0) Color(0xFF39FF14) else Color.Gray, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("DIAGNÓSTICO", color = if (selectedTab == 1) Color(0xFF39FF14) else Color.Gray, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("SENSORES", color = if (selectedTab == 2) Color(0xFF39FF14) else Color.Gray, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) })
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("HERRAM.", color = if (selectedTab == 3) Color(0xFF39FF14) else Color.Gray, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    navController.navigate("custom_pid")
                },
                containerColor = Color(0xFF0A0E1A), 
                shape = RoundedCornerShape(12.dp), 
                modifier = Modifier.border(1.dp, Color(0xFF39FF14), RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar", tint = Color(0xFF39FF14))
            }
        },
        containerColor = Color(0xFF0A0E1A)
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> ScannerDashboardTab(viewModel, isLandscape, defaultGauges)
                1 -> ScannerDiagnosticTab(viewModel, snackbarHostState)
                2 -> ScannerSensorsTab(viewModel, defaultGauges)
                3 -> ScannerToolsTab(viewModel, navController, onHudModeToggle = { hudMode = it })
            }
        }
    }
}
