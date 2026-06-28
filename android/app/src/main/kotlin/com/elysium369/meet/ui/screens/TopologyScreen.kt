package com.elysium369.meet.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.core.obd.NetworkModule
import com.elysium369.meet.core.obd.NetworkType
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.*
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopologyScreen(navController: NavController, viewModel: ObdViewModel) {
    val modules by viewModel.networkTopology.collectAsState()
    val isScanning by viewModel.isScanningTopology.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    
    val coroutineScope = rememberCoroutineScope()
    
    var activeTab by remember { mutableStateOf(0) }
    var selectedNetworkType by remember { mutableStateOf<NetworkType?>(null) }
    var aiDiagnosticResult by remember { mutableStateOf<com.elysium369.meet.core.ai.DiagnosticResult?>(null) }
    var isAnalyzingNetwork by remember { mutableStateOf(false) }
    val isConnected = connectionState == ObdState.CONNECTED

    // Animation for the "Scanning" radar effect
    val infiniteTransition = rememberInfiniteTransition(label = "Radar")
    val radarRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing)
        ),
        label = "RadarRotation"
    )

    Scaffold(
        topBar = {
            EliteTopAppBar(
                title = "MAPA TÁCTICO DE NODOS",
                onBackClick = { navController.popBackStack() },
                backgroundColor = MeetColors.backgroundDark
            )
        },
        containerColor = MeetColors.backgroundDeep
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // View Selector Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TabButton(text = "RADAR DE RED", isActive = activeTab == 0, onClick = { activeTab = 0 })
                TabButton(text = "CONECTOR OBD-II", isActive = activeTab == 1, onClick = { activeTab = 1 })
            }

            // Tactical Visualization Header
            EliteCard(
                backgroundColor = MeetColors.backgroundDark,
                glowColor = if (isScanning) MeetColors.electricBlue else MeetColors.neonGreen,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (activeTab == 0) {
                        // Background Grid Pattern
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val step = 40.dp.toPx()
                            for (x in 0..(size.width / step).toInt()) {
                                drawLine(Color.White.copy(alpha = 0.05f), Offset(x * step, 0f), Offset(x * step, size.height))
                            }
                            for (y in 0..(size.height / step).toInt()) {
                                drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, y * step), Offset(size.width, y * step))
                            }
                        }

                        if (modules.isEmpty() && !isScanning) {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AnimatedNeonIcon(
                                    Icons.Default.Radar,
                                    contentDescription = null,
                                    tint = MeetColors.textMuted,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    if (isConnected) "LISTO PARA ESCANEO REAL" else "CONECTA OBD PARA MAPEAR",
                                    color = if (isConnected) MeetColors.textSecondary else MeetColors.warning,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    if (isConnected) {
                                        "Se mostrarán solo ECUs que respondan por CAN/UDS."
                                    } else {
                                        "Elysium Vanguard no dibuja módulos simulados sin enlace físico."
                                    },
                                    color = MeetColors.textMuted,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        } else {
                            // Tactical Node Map
                            TacticalNodeMap(
                                modules = modules,
                                isScanning = isScanning,
                                radarRotation = radarRotation,
                                onNodeClick = { 
                                    selectedNetworkType = it
                                    activeTab = 1 // Switch to pinout to inspect physically
                                }
                            )
                        }

                        // Scan Overlay Info
                        if (isScanning) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "SONDEO REAL: direcciones físicas CAN/UDS, DTCs por módulo e identificación ECU...",
                                    color = MeetColors.electricBlue,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(8.dp),
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    } else {
                        ObdPinoutConnector(
                            selectedNetworkType = selectedNetworkType
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isConnected) "MÓDULOS REALES DETECTADOS: ${modules.size}" else "SIN ENLACE OBD: 0 MÓDULOS REALES",
                    color = if (isConnected) MeetColors.textSecondary else MeetColors.warning,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (modules.isNotEmpty() && !isScanning) {
                        EliteButton(
                            text = if (isAnalyzingNetwork) "ANALIZANDO..." else "🧠 ANALIZAR RED",
                            onClick = {
                                isAnalyzingNetwork = true
                                coroutineScope.launch {
                                    try {
                                        val result = viewModel.analyzeNetworkTopology(
                                            vehicleInfo = viewModel.ecuName.value ?: "Vehículo Genérico OBD-II",
                                            modules = modules
                                        )
                                        aiDiagnosticResult = result
                                    } catch (e: Exception) {
                                        aiDiagnosticResult = com.elysium369.meet.core.ai.DiagnosticResult("Error al contactar con la IA de Elysium: ${e.message}")
                                    } finally {
                                        isAnalyzingNetwork = false
                                    }
                                }
                            },
                            isEnabled = !isAnalyzingNetwork,
                            color = MeetColors.electricBlue
                        )
                    }

                    if (isScanning) {
                        EliteButton(
                            text = "🛑 DETENER",
                            onClick = { viewModel.cancelNetworkTopologyScan() },
                            color = MeetColors.error
                        )
                    } else {
                        val scanButtonText = if (isConnected) "ESCANEAR ECUS REALES" else "CONECTA OBD PRIMERO"
                        val scanButtonColor = if (isConnected) MeetColors.neonGreen else MeetColors.textMuted
                        
                        EliteButton(
                            text = scanButtonText,
                            onClick = { viewModel.scanNetworkTopology() },
                            color = scanButtonColor,
                            isEnabled = isConnected
                        )
                    }
                }
            }

            if (!isConnected) {
                Spacer(modifier = Modifier.height(12.dp))
                EliteCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = MeetColors.warning,
                    backgroundColor = MeetColors.backgroundDark
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "MAPEO TOPOLÓGICO REAL",
                            color = MeetColors.warning,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Conecta BT clásico, BLE, WiFi ELM o DoIP; pon ignición en ON; luego Elysium Vanguard sondea ECUs físicas y solo dibuja módulos que contestan.",
                            color = MeetColors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // AI Diagnostic Report Display
            AnimatedVisibility(
                visible = aiDiagnosticResult != null,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                aiDiagnosticResult?.let { result ->
                    EliteCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        glowColor = MeetColors.electricBlue,
                        backgroundColor = MeetColors.backgroundDark
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "ANÁLISIS DE RED ELYSIUM AI",
                                    color = MeetColors.electricBlue,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp
                                )
                                IconButton(
                                    onClick = { aiDiagnosticResult = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    AnimatedNeonIcon(
                                        Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = MeetColors.textMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Format paragraphs beautifully
                            val paragraphs = result.analysisText.split("\n\n")
                            paragraphs.forEach { paragraph ->
                                val lines = paragraph.trim().split("\n")
                                if (lines.isNotEmpty()) {
                                    val firstLine = lines.first().trim()
                                    val isHeader = firstLine.startsWith("#") || (firstLine.startsWith("*") && firstLine.endsWith(":")) || (firstLine.contains(":") && firstLine.length < 40)
                                    
                                    if (isHeader) {
                                        val headerText = firstLine.replace(Regex("[#*:]"), "").trim()
                                        Text(
                                            text = headerText.uppercase(),
                                            color = MeetColors.neonGreen,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                            letterSpacing = 1.sp
                                        )
                                        
                                        if (lines.size > 1) {
                                            val rest = lines.drop(1).joinToString("\n")
                                            Text(
                                                text = rest.replace("- ", "• ").replace("* ", "• "),
                                                color = Color.White.copy(alpha = 0.85f),
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(bottom = 6.dp),
                                                lineHeight = 18.sp
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = paragraph.replace("- ", "• ").replace("* ", "• "),
                                            color = Color.White.copy(alpha = 0.85f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(bottom = 6.dp),
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Module List
            val listState = rememberLazyListState()
            EliteScrollContainer(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().eliteScrollbar(listState)
                ) {
                    items(modules) { module ->
                        ModuleItem(module = module, onSelect = { selectedNetworkType = it })
                    }
                }
            }
        }
    }
}

@Composable
fun TacticalNodeMap(
    modules: List<NetworkModule>,
    isScanning: Boolean,
    radarRotation: Float,
    onNodeClick: (NetworkType) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val centerX = maxWidth / 2
        val centerY = maxHeight / 2
        
        // Define system positions for a "Tactical Bubble" layout
        val systemPositions = mapOf(
            NetworkType.CAN_HS to Offset(centerX.value, centerY.value - 80f), // Top
            NetworkType.CAN_LS to Offset(centerX.value + 100f, centerY.value + 60f), // Bottom Right
            NetworkType.LIN to Offset(centerX.value - 100f, centerY.value + 60f), // Bottom Left
            NetworkType.K_LINE to Offset(centerX.value, centerY.value + 120f), // Bottom Center
            NetworkType.ETHERNET to Offset(centerX.value - 120f, centerY.value - 70f) // Upper Left
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            
            // ── Draw Network Backbone ──
            systemPositions.forEach { (type, pos) ->
                val target = Offset(pos.x.dp.toPx(), pos.y.dp.toPx())
                val color = when(type) {
                    NetworkType.CAN_HS -> MeetColors.electricBlue
                    NetworkType.CAN_LS -> MeetColors.neonGreen
                    NetworkType.LIN -> MeetColors.warning
                    NetworkType.ETHERNET -> MeetColors.cyberCyan
                    else -> MeetColors.textSecondary
                }
                
                // Main bus line
                drawLine(
                    color = color.copy(alpha = 0.2f),
                    start = center,
                    end = target,
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
                
                // Backbone node
                drawCircle(color = color.copy(alpha = 0.4f), radius = 8.dp.toPx(), center = target)
            }

            // ── Draw Module Nodes ──
            modules.forEachIndexed { index, module ->
                val basePos = systemPositions[module.networkType] ?: Offset(centerX.value, centerY.value)
                val angle = (index * (360.0 / maxOf(1, modules.count { it.networkType == module.networkType }))) * (Math.PI / 180.0)
                val offsetRadius = 45.dp.toPx()
                
                val nodePos = Offset(
                    (basePos.x.dp.toPx() + offsetRadius * cos(angle)).toFloat(),
                    (basePos.y.dp.toPx() + offsetRadius * sin(angle)).toFloat()
                )

                val nodeColor = if (!module.isAlive) MeetColors.error 
                               else if (module.dtcs.isNotEmpty()) MeetColors.warning 
                               else MeetColors.neonGreen

                // Stubs to backbone
                drawLine(
                    color = nodeColor.copy(alpha = 0.3f),
                    start = Offset(basePos.x.dp.toPx(), basePos.y.dp.toPx()),
                    end = nodePos,
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = if (!module.isAlive) PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) else null
                )

                // Node Point
                drawCircle(
                    color = nodeColor,
                    radius = 8.dp.toPx(),
                    center = nodePos
                )
            }

            // Central Gateway (Elysium Vanguard VCI)
            drawCircle(color = MeetColors.electricBlue.copy(alpha = 0.3f), radius = 25.dp.toPx(), center = center)
            drawCircle(color = MeetColors.electricBlue, radius = 20.dp.toPx(), center = center, style = Stroke(width = 2.dp.toPx()))
        }

        // ── Node Label Overlays with Glitch Effect ──
        modules.forEachIndexed { index, module ->
            val basePos = systemPositions[module.networkType] ?: Offset(centerX.value, centerY.value)
            val angle = (index * (360.0 / maxOf(1, modules.count { it.networkType == module.networkType }))) * (Math.PI / 180.0)
            val offsetRadiusDp = 45f

            val labelX = basePos.x.dp + (offsetRadiusDp * cos(angle).toFloat()).dp
            val labelY = basePos.y.dp + (offsetRadiusDp * sin(angle).toFloat()).dp

            Surface(
                color = if (!module.isAlive) MeetColors.error.copy(alpha = 0.1f) else MeetColors.backgroundDark.copy(alpha = 0.8f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .offset(x = labelX - 15.dp, y = labelY + 10.dp)
                    .border(0.5.dp, if (!module.isAlive) MeetColors.error else Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .interferenceGlitch(enabled = !module.isAlive)
                    .clickable { onNodeClick(module.networkType) }
            ) {
                Text(
                    module.id,
                    color = if (!module.isAlive) MeetColors.error else Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                )
            }
        }
        
        // Scanning Radar Overlay
        if (isScanning) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                withTransform({ rotate(radarRotation, center) }) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0f to Color.Transparent,
                            0.5f to MeetColors.electricBlue.copy(alpha = 0.1f),
                            1f to MeetColors.electricBlue.copy(alpha = 0.4f)
                        ),
                        startAngle = 0f,
                        sweepAngle = 60f,
                        useCenter = true,
                        size = Size(size.minDimension, size.minDimension),
                        topLeft = Offset((size.width - size.minDimension)/2, (size.height - size.minDimension)/2)
                    )
                }
            }
        }
    }
}

@Composable
fun ModuleItem(module: NetworkModule, onSelect: (NetworkType) -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    val statusColor = if (!module.isAlive) MeetColors.error 
                      else if (module.dtcs.isNotEmpty()) MeetColors.warning 
                      else MeetColors.neonGreen
    val idLabel = if (module.networkType == NetworkType.ETHERNET) "ADDR: ${module.id.uppercase()}" else "CAN ID: 0x${module.id.uppercase()}"

    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    EliteCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { 
                onSelect(module.networkType)
                if (module.dtcs.isNotEmpty()) isExpanded = !isExpanded 
            },
        glowColor = statusColor.copy(alpha = if (module.isAlive) pulseAlpha else 1f),
        backgroundColor = MeetColors.backgroundDark
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Indicator
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(statusColor, CircleShape)
                        .neonGlow(statusColor)
                        .interferenceGlitch(enabled = !module.isAlive)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        module.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        idLabel,
                        color = MeetColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!module.protocolDetected.isNullOrBlank()) {
                        Text(
                            module.protocolDetected,
                            color = MeetColors.textMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (module.dtcs.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedNeonIcon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MeetColors.warning,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "${module.dtcs.size} DTCs",
                            color = MeetColors.warning,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(16.dp)
                ) {
                    Text(
                        "CÓDIGOS DE FALLA DETECTADOS:",
                        color = MeetColors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    module.dtcs.forEach { dtc ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            AnimatedNeonIcon(Icons.Default.Info, null, tint = MeetColors.error, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(dtc, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TabButton(text: String, isActive: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) MeetColors.electricBlue.copy(alpha = 0.2f) else Color.Transparent)
            .border(1.dp, if (isActive) MeetColors.electricBlue else Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isActive) Color.White else MeetColors.textSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@Composable
fun ObdPinoutConnector(
    selectedNetworkType: NetworkType?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "DISTRIBUCIÓN DE PINES FISICOS OBD-II",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                
                // Outer trapezoid dimensions
                val topW = w * 0.85f
                val botW = w * 0.70f
                val trapHeight = h * 0.75f
                val startXTop = (w - topW) / 2
                val startXBot = (w - botW) / 2
                val startY = (h - trapHeight) / 2
                
                // Draw OBD trapezoid shell path
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(startXTop, startY)
                    lineTo(startXTop + topW, startY)
                    lineTo(startXBot + botW, startY + trapHeight)
                    lineTo(startXBot, startY + trapHeight)
                    close()
                }
                
                // Fill & Stroke of trapezoid shell
                drawPath(
                    path = path,
                    color = Color(0xFF1E222A),
                    style = androidx.compose.ui.graphics.drawscope.Fill
                )
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.2f),
                    style = Stroke(width = 2.dp.toPx())
                )
                
                // Highlighted pins mapping
                // Pin positions relative to trapezoid:
                // Row 0 (pins 1 to 8)
                // Row 1 (pins 9 to 16)
                val pinRadius = 6.dp.toPx()
                
                for (row in 0..1) {
                    val y = startY + (if (row == 0) trapHeight * 0.3f else trapHeight * 0.7f)
                    val rowW = if (row == 0) topW else botW
                    val rowStartX = if (row == 0) startXTop else startXBot
                    
                    for (col in 0..7) {
                        val pinNum = if (row == 0) col + 1 else col + 9
                        val x = rowStartX + rowW * (col + 0.5f) / 8f
                        
                        // Determine glow color for pins
                        val glowColor = getPinGlowColor(pinNum, selectedNetworkType)
                        
                        // Draw socket background
                        drawCircle(
                            color = if (glowColor != null) glowColor.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.6f),
                            radius = pinRadius + 4f,
                            center = Offset(x, y)
                        )
                        
                        // Draw socket contact
                        drawCircle(
                            color = glowColor ?: Color.Gray.copy(alpha = 0.5f),
                            radius = if (glowColor != null) pinRadius * 0.7f else pinRadius * 0.4f,
                            center = Offset(x, y),
                            style = if (glowColor != null) androidx.compose.ui.graphics.drawscope.Fill else Stroke(width = 1.dp.toPx())
                        )
                        
                        // Draw pin outer ring when glowing
                        if (glowColor != null) {
                            drawCircle(
                                color = glowColor,
                                radius = pinRadius + 4f,
                                center = Offset(x, y),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Interactive Pin Description Legend
        val activeDesc = when (selectedNetworkType) {
            NetworkType.CAN_HS, NetworkType.CAN_LS -> {
                "Red CAN Bus activa: Pines 6 (CAN H) y 14 (CAN L) bajo análisis. Masa en pines 4/5."
            }
            NetworkType.LIN -> {
                "Bus LIN activo: Pin 7 utilizado para transmisión LIN / Confort. Alimentación 12V en pin 16."
            }
            NetworkType.K_LINE -> {
                "Línea K activa: Pin 7 (ISO 9141-2 / K-Line) para diagnóstico directo."
            }
            NetworkType.ETHERNET -> {
                "Gateway DoIP/Ethernet detectado: la ruta física depende del gateway y del OEM. Elysium Vanguard valida el servidor UDS real, no un nodo CAN simulado."
            }
            else -> {
                "Toque un módulo o bus para ver la asignación de pines físicos en el puerto OBD-II."
            }
        }
        
        Text(
            text = activeDesc,
            color = if (selectedNetworkType != null) MeetColors.electricBlue else MeetColors.textMuted,
            fontSize = 10.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

fun getPinGlowColor(pinNum: Int, selectedNetworkType: NetworkType?): Color? {
    // Standard power / ground pins (always slightly lit for reference if a network is selected)
    if (selectedNetworkType != null && (pinNum == 4 || pinNum == 5)) {
        return Color.White.copy(alpha = 0.6f) // Masa
    }
    if (selectedNetworkType != null && pinNum == 16) {
        return MeetColors.error.copy(alpha = 0.6f) // +12V
    }
    
    return when (selectedNetworkType) {
        NetworkType.CAN_HS -> {
            if (pinNum == 6) MeetColors.electricBlue
            else if (pinNum == 14) MeetColors.neonGreen.copy(alpha = 0.5f) // highlight CAN L slightly
            else null
        }
        NetworkType.CAN_LS -> {
            if (pinNum == 14) MeetColors.neonGreen
            else if (pinNum == 6) MeetColors.electricBlue.copy(alpha = 0.5f) // highlight CAN H slightly
            else null
        }
        NetworkType.LIN, NetworkType.K_LINE -> {
            if (pinNum == 7) MeetColors.warning
            else null
        }
        else -> null
    }
}
