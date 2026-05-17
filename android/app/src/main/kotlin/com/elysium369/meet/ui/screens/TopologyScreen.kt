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
import androidx.compose.ui.platform.LocalDensity
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
            // Tactical Visualization Header
            EliteCard(
                backgroundColor = MeetColors.backgroundDark,
                glowColor = if (isScanning) MeetColors.electricBlue else MeetColors.neonGreen,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
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
                            Icon(
                                Icons.Default.Radar,
                                contentDescription = null,
                                tint = MeetColors.textMuted,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "RED NO ESCANEADA",
                                color = MeetColors.textSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // Tactical Node Map
                        TacticalNodeMap(
                            modules = modules,
                            isScanning = isScanning,
                            radarRotation = radarRotation
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
                                "ESCANEO ACTIVO: PINGING CAN IDs 0x7E0-0x7EB...",
                                color = MeetColors.electricBlue,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(8.dp),
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "MÓDULOS DETECTADOS: ${modules.size}",
                    color = MeetColors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                
                EliteButton(
                    text = if (isScanning) "ESCANEANDO..." else "INICIAR ESCANEO",
                    onClick = { viewModel.scanNetworkTopology() },
                    isEnabled = !isScanning && connectionState == ObdState.CONNECTED,
                    color = if (connectionState == ObdState.CONNECTED) MeetColors.neonGreen else Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Module List
            val listState = rememberLazyListState()
            EliteScrollContainer(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().eliteScrollbar(listState)
                ) {
                    items(modules) { module ->
                        ModuleItem(module)
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
    radarRotation: Float
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val centerX = maxWidth / 2
        val centerY = maxHeight / 2
        
        // Define system positions for a "Tactical Bubble" layout
        val systemPositions = mapOf(
            NetworkType.CAN_HIGH to Offset(centerX.value, centerY.value - 80f), // Top
            NetworkType.CAN_LOW to Offset(centerX.value + 100f, centerY.value + 60f), // Bottom Right
            NetworkType.LIN to Offset(centerX.value - 100f, centerY.value + 60f), // Bottom Left
            NetworkType.K_LINE to Offset(centerX.value, centerY.value + 120f) // Bottom Center
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            
            // ── Draw Network Backbone ──
            systemPositions.forEach { (type, pos) ->
                val target = Offset(pos.x.dp.toPx(), pos.y.dp.toPx())
                val color = when(type) {
                    NetworkType.CAN_HIGH -> MeetColors.electricBlue
                    NetworkType.CAN_LOW -> MeetColors.neonGreen
                    NetworkType.LIN -> MeetColors.warning
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

            // Central Gateway (MEET VCI)
            drawCircle(color = MeetColors.electricBlue.copy(alpha = 0.3f), radius = 25.dp.toPx(), center = center)
            drawCircle(color = MeetColors.electricBlue, radius = 20.dp.toPx(), center = center, style = Stroke(width = 2.dp.toPx()))
        }

        // ── Node Label Overlays with Glitch Effect ──
        val density = LocalDensity.current
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
fun ModuleItem(module: NetworkModule) {
    var isExpanded by remember { mutableStateOf(false) }
    val statusColor = if (!module.isAlive) MeetColors.error 
                      else if (module.dtcs.isNotEmpty()) MeetColors.warning 
                      else MeetColors.neonGreen

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
            .clickable { if (module.dtcs.isNotEmpty()) isExpanded = !isExpanded },
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
                        "CAN ID: 0x${module.id.uppercase()}",
                        color = MeetColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (module.dtcs.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
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
                            Icon(Icons.Default.Info, null, tint = MeetColors.error, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(dtc, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
