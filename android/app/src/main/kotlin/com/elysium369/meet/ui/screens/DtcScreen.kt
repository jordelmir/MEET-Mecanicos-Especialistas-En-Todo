package com.elysium369.meet.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.EliteScrollContainer
import com.elysium369.meet.ui.components.eliteScrollbar
import kotlinx.coroutines.launch
import com.elysium369.meet.data.local.entities.DtcDefinitionEntity

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
    val language by viewModel.language.collectAsState()
    var isSpanish by remember(language) { mutableStateOf(language == "es") } 
    val isScanning by viewModel.isScanning.collectAsState()
    val isClearing by viewModel.isClearing.collectAsState()

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(if(isSpanish) "⚠️ Borrar Códigos" else "⚠️ Clear Codes", color = Color.White) },
            text = { Text(if(isSpanish) "Esto enviará Mode 04 al vehículo. Se borrarán TODOS los DTCs activos y pendientes, se apagará la luz MIL (Check Engine), y se resetearán los monitores de emisiones.\n\n¿Continuar?" else "This will send Mode 04 to the vehicle. ALL active and pending DTCs will be cleared, the MIL (Check Engine) light will be turned off, and emission monitors will be reset.\n\nContinue?", color = Color.Gray) },
            confirmButton = { TextButton(onClick = { showClearDialog = false; coroutineScope.launch { viewModel.clearDtcs() } }) { Text(if(isSpanish) "BORRAR" else "CLEAR", color = Color(0xFFFF003C), fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text(if(isSpanish) "Cancelar" else "Cancel", color = Color.Gray) } },
            containerColor = Color(0xFF0A0E1A)
        )
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(if(isSpanish) "Diagnóstico DTC" else "DTC Diagnostics", color = Color.White, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0E1A)),
                    actions = {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                            Text("EN", color = if(isSpanish) Color.Gray else Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            Switch(
                                checked = isSpanish,
                                onCheckedChange = { 
                                    isSpanish = it
                                    viewModel.setLanguage(if(it) "es" else "en")
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF39FF14),
                                    checkedTrackColor = Color(0xFF39FF14).copy(alpha = 0.3f),
                                    uncheckedThumbColor = Color(0xFF00AAFF),
                                    uncheckedTrackColor = Color(0xFF00AAFF).copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.padding(horizontal = 4.dp).height(24.dp)
                            )
                            Text("ES", color = if(isSpanish) Color.White else Color.Gray, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTab, containerColor = Color(0xFF0A0E1A), contentColor = Color(0xFF39FF14)) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(if(isSpanish) "Activos" else "Active", color = if (selectedTab == 0) Color(0xFFFF003C) else Color.Gray, modifier = Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(if(isSpanish) "Pend." else "Pend.", color = if (selectedTab == 1) Color(0xFFFFD700) else Color.Gray, modifier = Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text(if(isSpanish) "Perm." else "Perm.", color = if (selectedTab == 2) Color(0xFF00AAFF) else Color.Gray, modifier = Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) })
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text(if(isSpanish) "Monitores" else "Monitors", color = if (selectedTab == 3) Color(0xFF39FF14) else Color.Gray, modifier = Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) })
                    Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }, text = { Text("Manual", color = if (selectedTab == 4) Color.White else Color.Gray, modifier = Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) })
                }
            }
        },
        containerColor = Color(0xFF0A0E1A)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            EliteScrollContainer(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .eliteScrollbar(listState),
                    contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { coroutineScope.launch { viewModel.refreshDiagnostics() } },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .border(1.dp, if(isScanning) Color.Transparent else Color(0xFF39FF14), RoundedCornerShape(8.dp))
                                .background(
                                    if(isScanning) Brush.horizontalGradient(listOf(Color(0xFF39FF14).copy(alpha = 0.1f), Color.Transparent))
                                    else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                                    RoundedCornerShape(8.dp)
                                ),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isScanning && !isClearing
                        ) {
                            if (isScanning) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val infiniteTransition = rememberInfiniteTransition(label = "scan")
                                    val alpha by infiniteTransition.animateFloat(
                                        initialValue = 0.3f,
                                        targetValue = 1f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(600, easing = LinearEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "alpha"
                                    )
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF39FF14).copy(alpha = alpha), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if(isSpanish) "ESCANEANDO..." else "SCANNING...", color = Color(0xFF39FF14).copy(alpha = alpha), fontWeight = FontWeight.Black, fontSize = 12.sp)
                                }
                            } else {
                                Text(if(isSpanish) "ESCANEAR" else "SCAN", color = Color(0xFF39FF14), fontWeight = FontWeight.Bold)
                            }
                        }
                        Button(
                            onClick = { showClearDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .border(1.dp, if(isClearing) Color.Transparent else Color(0xFFFF003C), RoundedCornerShape(8.dp))
                                .background(
                                    if(isClearing) Brush.horizontalGradient(listOf(Color(0xFFFF003C).copy(alpha = 0.1f), Color.Transparent))
                                    else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                                    RoundedCornerShape(8.dp)
                                ),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isScanning && !isClearing
                        ) {
                            if (isClearing) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val infiniteTransition = rememberInfiniteTransition(label = "clear")
                                    val scale by infiniteTransition.animateFloat(
                                        initialValue = 0.8f,
                                        targetValue = 1.2f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(400, easing = FastOutSlowInEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "scale"
                                    )
                                    Box(modifier = Modifier.size(12.dp).graphicsLayer(scaleX = scale, scaleY = scale).background(Color(0xFFFF003C), CircleShape))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if(isSpanish) "BORRANDO..." else "CLEARING...", color = Color(0xFFFF003C), fontWeight = FontWeight.Black, fontSize = 12.sp)
                                }
                            } else {
                                Text(if(isSpanish) "BORRAR DTCs" else "CLEAR DTCs", color = Color(0xFFFF003C), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (clearResult != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E1A)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF39FF14).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        ) {
                            Text(clearResult.orEmpty(), color = Color.White, modifier = Modifier.padding(12.dp))
                        }
                    }
                }

                when (selectedTab) {
                    0 -> { // Active DTCs (Mode 03)
                        if (activeDtcs.isEmpty()) {
                            item { EmptyDtcState(if(isSpanish) "No hay códigos de falla activos" else "No active fault codes", Color(0xFF39FF14)) }
                        } else {
                            items(activeDtcs) { dtc -> DtcCard(dtc, if(isSpanish) "ACTIVO" else "ACTIVE", Color(0xFFFF003C), navController, viewModel, isSpanish) }
                        }
                    }
                    1 -> { // Pending DTCs (Mode 07)
                        if (pendingDtcs.isEmpty()) {
                            item { EmptyDtcState(if(isSpanish) "No hay códigos pendientes.\nEstos son códigos que aún no encendieron la luz MIL." else "No pending codes.\nThese codes have not yet turned on the MIL.", Color(0xFFFFD700)) }
                        } else {
                            items(pendingDtcs) { dtc -> DtcCard(dtc, if(isSpanish) "PENDIENTE" else "PENDING", Color(0xFFFFD700), navController, viewModel, isSpanish) }
                        }
                    }
                    2 -> { // Permanent DTCs (Mode 0A)
                        if (permanentDtcs.isEmpty()) {
                            item { EmptyDtcState(if(isSpanish) "No hay códigos permanentes.\nEstos son códigos que NO se pueden borrar manualmente." else "No permanent codes.\nThese codes CANNOT be manually cleared.", Color(0xFF00AAFF)) }
                        } else {
                            items(permanentDtcs) { dtc -> DtcCard(dtc, if(isSpanish) "PERMANENTE" else "PERMANENT", Color(0xFF00AAFF), navController, viewModel, isSpanish) }
                        }
                    }
                    3 -> { // Readiness Monitors
                        item { ReadinessMonitorsCard(readiness, coroutineScope, viewModel, isSpanish) }
                    }
                    4 -> { // Manual Search
                        item { ManualSearchTab(navController, viewModel, isSpanish) }
                    }
                }
                }
            }

            // --- Creative Scanning Overlay ---
            if (isScanning) {
                EliteScanningAnimation(isSpanish)
            }

            // --- Creative Clearing Overlay ---
            if (isClearing) {
                EliteClearingAnimation(isSpanish)
            }
        }
    }
}

@Composable
fun EliteScanningAnimation(isSpanish: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val scanLinePos by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanLine"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(top = 100.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val height = size.height
            val width = size.width
            val currentY = height * scanLinePos
            
            // Scanning Beam
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF39FF14).copy(alpha = 0.3f),
                        Color(0xFF39FF14).copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    startY = currentY - 100,
                    endY = currentY + 10
                ),
                topLeft = androidx.compose.ui.geometry.Offset(0f, currentY - 100),
                size = androidx.compose.ui.geometry.Size(width, 110f)
            )
            
            // Glow line
            drawLine(
                color = Color(0xFF39FF14),
                start = androidx.compose.ui.geometry.Offset(0f, currentY),
                end = androidx.compose.ui.geometry.Offset(width, currentY),
                strokeWidth = 2.dp.toPx()
            )
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 40.dp)
        ) {
            Surface(
                color = Color.Black,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.border(1.dp, Color(0xFF39FF14), RoundedCornerShape(24.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color(0xFF39FF14),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        if(isSpanish) "ESCANEANDO SISTEMAS..." else "SCANNING SYSTEMS...",
                        color = Color(0xFF39FF14),
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
fun EliteClearingAnimation(isSpanish: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        // Pulse circles
        Box(
            modifier = Modifier
                .size(200.dp)
                .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                .border(2.dp, Color(0xFFFF003C).copy(alpha = pulseAlpha), CircleShape)
        )
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Search, 
                contentDescription = null,
                tint = Color(0xFFFF003C),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                if(isSpanish) "RESETEANDO ECUs..." else "RESETTING ECUs...",
                color = Color.White,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                if(isSpanish) "POR FAVOR ESPERE" else "PLEASE WAIT",
                color = Color(0xFFFF003C),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun DtcCard(dtc: String, severity: String, color: Color, navController: NavController, viewModel: ObdViewModel, isSpanish: Boolean) {
    val definitions by viewModel.dtcDefinitions.collectAsState()
    val dtcInfo = definitions[dtc]
    
    val fallbackDesc = if (isSpanish) "Definición no encontrada o consultando base de datos..." else "Definition not found or querying database..."
    val desc = if (isSpanish) {
        dtcInfo?.let { translateDtcText(it.descriptionEs) } ?: fallbackDesc
    } else {
        dtcInfo?.descriptionEs ?: fallbackDesc
    }
    
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E1A)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))) {
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
            
            if (dtcInfo != null) {
                Spacer(modifier = Modifier.height(12.dp))
                val title = if (isSpanish) "🧠 SÍNTESIS EXPERTA (LOCAL):" else "🧠 EXPERT SYNTHESIS (LOCAL):"
                Text(title, color = Color(0xFF00AAFF).copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(generateExpertSynthesis(dtcInfo, isSpanish), color = Color(0xFF00AAFF).copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            val freezeFrame by viewModel.freezeFrameData.collectAsState()
            
            // Filter freeze frame entries scoped to THIS specific DTC code
            val scopedFrame = freezeFrame.filter { it.key.startsWith("$dtc:") }
            
            if (scopedFrame.isNotEmpty()) {
                Text(if(isSpanish) "❄️ DATOS DE CUADRO CONGELADO:" else "❄️ FREEZE FRAME DATA:", color = Color(0xFF00AAFF), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                scopedFrame.forEach { (scopedKey, valStr) ->
                    val pid = scopedKey.substringAfter(":")
                    val pidNameEs = when(pid) {
                        "03" -> "Estado Combustible"
                        "04" -> "Carga Motor"
                        "05" -> "Temp. Refrigerante"
                        "06" -> "Ajuste Comb. Corto"
                        "07" -> "Ajuste Comb. Largo"
                        "0C" -> "RPM Motor"
                        "0D" -> "Velocidad"
                        "11" -> "Pos. Acelerador"
                        else -> "PID $pid"
                    }
                    val pidNameEn = when(pid) {
                        "03" -> "Fuel System Status"
                        "04" -> "Engine Load"
                        "05" -> "Coolant Temp"
                        "06" -> "Short Term Fuel Trim"
                        "07" -> "Long Term Fuel Trim"
                        "0C" -> "Engine RPM"
                        "0D" -> "Vehicle Speed"
                        "11" -> "Throttle Position"
                        else -> "PID $pid"
                    }
                    Text("${if(isSpanish) pidNameEs else pidNameEn}: $valStr", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { navController.navigate("ai/$dtc") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0E1A)),
                    modifier = Modifier.weight(1f).border(1.dp, Color(0xFF39FF14), RoundedCornerShape(8.dp)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if(isSpanish) "🤖 CONSULTAR IA" else "🤖 CONSULT AI", color = Color(0xFF39FF14), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                }
                
                val coroutineScope = rememberCoroutineScope()
                Button(
                    onClick = { coroutineScope.launch { viewModel.refreshFreezeFrame(dtc) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0E1A)),
                    modifier = Modifier.weight(1f).border(1.dp, Color(0xFF00AAFF), RoundedCornerShape(8.dp)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("❄️ FF DATA", color = Color(0xFF00AAFF), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
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
private fun ReadinessMonitorsCard(readiness: com.elysium369.meet.core.obd.ReadinessResult?, coroutineScope: kotlinx.coroutines.CoroutineScope, viewModel: ObdViewModel, isSpanish: Boolean) {
    if (readiness == null) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📊", style = MaterialTheme.typography.displayMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text(if(isSpanish) "Monitores de emisiones no leídos aún." else "Emission monitors not read yet.", color = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { coroutineScope.launch { viewModel.refreshDiagnostics() } }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0E1A)), modifier = Modifier.border(1.dp, Color(0xFF39FF14), RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp)) {
                    Text(if(isSpanish) "LEER MONITORES" else "READ MONITORS", color = Color(0xFF39FF14), fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        // MIL Status
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E1A)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().border(1.dp, if (readiness.milOn) Color(0xFFFF003C) else Color(0xFF39FF14), RoundedCornerShape(12.dp))) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(if(isSpanish) "LUZ MIL (CHECK ENGINE)" else "MIL (CHECK ENGINE)", color = Color.Gray, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    val encendida = if(isSpanish) "🔴 ENCENDIDA" else "🔴 ON"
                    val apagada = if(isSpanish) "🟢 APAGADA" else "🟢 OFF"
                    Text(if (readiness.milOn) encendida else apagada, color = if (readiness.milOn) Color(0xFFFF003C) else Color(0xFF39FF14), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                }
                Text("${readiness.dtcCount} DTCs", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        // Monitors
        Text(if(isSpanish) "MONITORES DE EMISIÓN" else "EMISSION MONITORS", color = Color(0xFF39FF14).copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        val passedCount = readiness.monitors.count { it.complete }
        val totalCount = readiness.monitors.size
        val completados = if(isSpanish) "completados" else "completed"
        Text("$passedCount / $totalCount $completados", color = if (passedCount == totalCount) Color(0xFF39FF14) else Color(0xFFFFD700), style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(8.dp))
        readiness.monitors.forEach { monitor ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E1A)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).border(1.dp, (if (monitor.complete) Color(0xFF39FF14) else Color(0xFFFFD700)).copy(alpha = 0.3f), RoundedCornerShape(8.dp))) {
                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(monitor.name, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    val listo = if(isSpanish) "✅ Listo" else "✅ Ready"
                    val inc = if(isSpanish) "⏳ Incompleto" else "⏳ Inc."
                    Text(if (monitor.complete) listo else inc, color = if (monitor.complete) Color(0xFF39FF14) else Color(0xFFFFD700), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualSearchTab(navController: NavController, viewModel: ObdViewModel, isSpanish: Boolean) {
    var searchQuery by remember { mutableStateOf("") }
    val searchResults by viewModel.manualSearchResults.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Professional Live Search with Debounce
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            kotlinx.coroutines.delay(300) // 300ms debounce
            viewModel.searchDtcManual(searchQuery)
        } else if (searchQuery.isEmpty()) {
            viewModel.searchDtcManual("")
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it.uppercase().trim() },
            label = { Text(if(isSpanish) "Ingresar Código (Ej. P0300)" else "Enter Code (e.g. P0300)", color = Color.Gray) },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFF39FF14),
                unfocusedBorderColor = Color.Gray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color(0xFF39FF14)
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (searchQuery.isNotEmpty() && searchResults.isEmpty()) {
            Button(
                onClick = { viewModel.searchDtcManual(searchQuery) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0E1A)),
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF39FF14), RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if(isSpanish) "BUSCAR" else "SEARCH", color = Color(0xFF39FF14), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (searchResults.isNotEmpty() || searchQuery.isNotEmpty()) {
            if (searchResults.isEmpty() && searchQuery.length >= 3) {
                EmptyDtcState(if(isSpanish) "No se encontró el código en la base de datos." else "Code not found in the database.", Color.Gray)
            } else {
                Text(if(isSpanish) "Resultados (${searchResults.size})" else "Results (${searchResults.size})", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                searchResults.forEach { dtc ->
                    val color = when (dtc.severity.uppercase()) {
                        "HIGH" -> Color(0xFFFF003C)
                        "MODERATE" -> Color(0xFFFFD700)
                        else -> Color(0xFF39FF14)
                    }
                    
                    val desc = if (isSpanish) translateDtcText(dtc.descriptionEs) else dtc.descriptionEs
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E1A)),
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
                            Text(desc, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            val title = if (isSpanish) "🧠 SÍNTESIS EXPERTA (LOCAL):" else "🧠 EXPERT SYNTHESIS (LOCAL):"
                            Text(title, color = Color(0xFF00AAFF).copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(generateExpertSynthesis(dtc, isSpanish), color = Color(0xFF00AAFF).copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { navController.navigate("ai/${dtc.code}") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0E1A)),
                                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF39FF14), RoundedCornerShape(8.dp)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if(isSpanish) "🤖 CONSULTAR IA" else "🤖 CONSULT AI", color = Color(0xFF39FF14), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun translateDtcText(englishText: String): String {
    var es = englishText
    val dict = mapOf(
        "Cylinder" to "Cilindro", "Misfire" to "Fallo de encendido", "Detected" to "Detectado",
        "Random" to "Aleatorio", "Multiple" to "Múltiple", "Sensor" to "Sensor",
        "Circuit" to "Circuito", "Low" to "Bajo", "High" to "Alto", "Input" to "Entrada",
        "Output" to "Salida", "Bank" to "Banco", "Voltage" to "Voltaje",
        "Malfunction" to "Mal funcionamiento", "Performance" to "Rendimiento", "Range" to "Rango",
        "Heater" to "Calentador", "Control" to "Control", "Module" to "Módulo",
        "System" to "Sistema", "Emission" to "Emisión", "Evaporative" to "Evaporativo",
        "Leak" to "Fuga", "Pressure" to "Presión", "Temperature" to "Temperatura",
        "Engine" to "Motor", "Coolant" to "Refrigerante", "Speed" to "Velocidad",
        "Position" to "Posición", "Camshaft" to "Árbol de levas", "Crankshaft" to "Cigüeñal",
        "Exhaust" to "Escape", "Gas" to "Gas", "Recirculation" to "Recirculación",
        "Oxygen" to "Oxígeno", "O2" to "O2", "Catalyst" to "Catalizador",
        "Efficiency" to "Eficiencia", "Below" to "Por debajo", "Threshold" to "Umbral",
        "Fuel" to "Combustible", "Trim" to "Ajuste", "Lean" to "Pobre", "Rich" to "Rico",
        "Mass" to "Masa", "Air" to "Aire", "Flow" to "Flujo", "Volume" to "Volumen",
        "Throttle" to "Acelerador", "Pedal" to "Pedal", "Switch" to "Interruptor",
        "Relay" to "Relé", "Valve" to "Válvula", "Pump" to "Bomba", "Motor" to "Motor",
        "Signal" to "Señal", "Intermittent" to "Intermitente", "Erratic" to "Errático",
        "Open" to "Abierto", "Short" to "Corto", "Ground" to "Tierra", "Battery" to "Batería",
        "Ignition" to "Ignición", "Coil" to "Bobina", "Primary" to "Primario",
        "Secondary" to "Secundario", "Transmission" to "Transmisión", "Gear" to "Marcha",
        "Ratio" to "Relación", "Shift" to "Cambio", "Solenoid" to "Solenoide",
        "Fluid" to "Líquido", "Clutch" to "Embrague", "Torque" to "Torque",
        "Converter" to "Convertidor", "Brake" to "Freno", "ABS" to "ABS",
        "Steering" to "Dirección", "Wheel" to "Rueda", "Tire" to "Llanta",
        "Monitor" to "Monitor", "Internal" to "Interno", "Error" to "Error",
        "Memory" to "Memoria", "Keep" to "Mantener", "Alive" to "Vivo",
        "KAM" to "KAM", "ROM" to "ROM", "RAM" to "RAM", "EEPROM" to "EEPROM",
        "Programming" to "Programación", "Communication" to "Comunicación",
        "Lost" to "Perdida", "Bus" to "Bus", "Data" to "Datos", "Link" to "Enlace",
        "Network" to "Red", "Node" to "Nodo", "Invalid" to "Inválido",
        "Missing" to "Faltante", "Message" to "Mensaje", "Received" to "Recibido",
        "Expected" to "Esperado", "Actual" to "Real", "Limit" to "Límite",
        "Exceeded" to "Excedido", "Maximum" to "Máximo", "Minimum" to "Mínimo",
        "Value" to "Valor", "Out of" to "Fuera de", "Bounds" to "Límites",
        "Tolerance" to "Tolerancia", "Calibration" to "Calibración", "Not" to "No",
        "Learned" to "Aprendido", "Configured" to "Configurado", "Programmed" to "Programado",
        "Supported" to "Soportado", "Available" to "Disponible", "Ready" to "Listo",
        "Active" to "Activo", "Pending" to "Pendiente", "Permanent" to "Permanente",
        "History" to "Historia", "Stored" to "Almacenado", "Current" to "Actual",
        "Worn out" to "Desgastado", "spark plugs" to "bujías", "ignition wires" to "cables de ignición",
        "distributor cap" to "tapa de distribuidor", "rotor" to "rotor",
        "when applicable" to "cuando aplique", "Incorrect" to "Incorrecto",
        "timing" to "sincronización", "Vacuum" to "Vacío", "leak(s)" to "fuga(s)",
        "weak" to "débil", "Improperly functioning" to "Funcionamiento inadecuado",
        "Defective" to "Defectuoso", "Mechanical" to "Mecánico", "problems" to "problemas",
        "compression" to "compresión", "leaking" to "fuga", "head gasket(s)" to "junta(s) de culata",
        "or" to "o", "and" to "y"
    )

    // Order dict by length descending so longer phrases get replaced first
    dict.entries.sortedByDescending { it.key.length }.forEach { (en, esWord) ->
        es = es.replace(Regex("\\b$en\\b", RegexOption.IGNORE_CASE), esWord)
    }
    
    return es
}

private fun generateExpertSynthesis(dtc: DtcDefinitionEntity, isSpanish: Boolean): String {
    val rawCauses = if (dtc.descriptionEn.isNotBlank() && dtc.descriptionEn.contains(",")) dtc.descriptionEn else dtc.possibleCauses
    val causesList = rawCauses
        .split(Regex("[.,;\\n]"))
        .filter { it.isNotBlank() && it.length > 3 }
        .map { it.trim().replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() } }
        
    val prefix = dtc.code.firstOrNull()?.toString()?.uppercase() ?: ""
    
    val systemsInfo = if (isSpanish) {
        when(prefix) {
            "P" -> "Tren Motriz (Motor/Transmisión)"
            "B" -> "Carrocería (Habitáculo/Módulos)"
            "C" -> "Chasis (Frenos/Suspensión/Dirección)"
            "U" -> "Red de Comunicación (CAN Bus/Módulos)"
            else -> "Sistema General (${dtc.system})"
        }
    } else {
        when(prefix) {
            "P" -> "Powertrain (Engine/Transmission)"
            "B" -> "Body (Interior/Modules)"
            "C" -> "Chassis (Brakes/Suspension/Steering)"
            "U" -> "Network (CAN Bus/Modules)"
            else -> "General System (${dtc.system})"
        }
    }
    
    val severityText = if (isSpanish) {
        when(dtc.severity.uppercase()) {
            "HIGH" -> "ALTA - Acción Inmediata Requerida."
            "MODERATE" -> "MEDIA - Requiere atención a corto plazo."
            "LOW" -> "BAJA - Fallo informativo o intermitente."
            else -> "EVALUAR - Depende de los síntomas físicos."
        }
    } else {
        when(dtc.severity.uppercase()) {
            "HIGH" -> "HIGH - Immediate Action Required."
            "MODERATE" -> "MODERATE - Requires short-term attention."
            "LOW" -> "LOW - Informational or intermittent fault."
            else -> "EVALUATE - Depends on physical symptoms."
        }
    }
    
    val desc = if (isSpanish) translateDtcText(dtc.descriptionEs) else dtc.descriptionEs

    val causesText = if (causesList.isNotEmpty()) {
        causesList.take(4).mapIndexed { index, cause -> 
            val formattedCause = if (isSpanish) translateDtcText(cause) else cause
            "   ${index + 1}. $formattedCause" 
        }.joinToString("\n")
    } else {
        if (isSpanish) {
            "   1. Inspección visual de circuitos y conectores.\n   2. Revisar boletines técnicos (TSBs)."
        } else {
            "   1. Visual inspection of circuits and connectors.\n   2. Check manufacturer TSBs."
        }
    }

    if (isSpanish) {
        return """
            🇪🇸 ANÁLISIS TÉCNICO
            • Sistema: $systemsInfo
            • Riesgo: $severityText
            ⚙️ FALLO: "$desc"
            
            🛠️ RUTA DE DIAGNÓSTICO (CAUSAS):
$causesText
            
            💡 NOTA EXPERTA:
            Revisar "Cuadro Congelado" (FF Data) antes de reemplazar componentes.
        """.trimIndent()
    } else {
        return """
            🇺🇸 TECHNICAL ANALYSIS
            • System: $systemsInfo
            • Risk: $severityText
            ⚙️ FAULT: "$desc"
            
            🛠️ DIAGNOSTIC PATH (CAUSES):
$causesText
            
            💡 EXPERT NOTE:
            Check "Freeze Frame" data before replacing any components.
        """.trimIndent()
    }
}
