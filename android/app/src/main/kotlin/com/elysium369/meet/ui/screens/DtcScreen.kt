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
import com.elysium369.meet.ui.components.neonGlow
import kotlinx.coroutines.launch
import com.elysium369.meet.data.local.entities.DtcDefinitionEntity
import com.elysium369.meet.data.local.KnowledgeBaseRepository
import com.elysium369.meet.ui.components.EliteDialog
import com.elysium369.meet.ui.theme.MeetColors

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
        EliteDialog(
            title = if(isSpanish) "⚠️ Borrar Códigos" else "⚠️ Clear Codes",
            message = if(isSpanish) "Esto enviará Mode 04 al vehículo. Se borrarán TODOS los DTCs activos y pendientes, se apagará la luz MIL (Check Engine), y se resetearán los monitores de emisiones.\n\n¿Continuar?" else "This will send Mode 04 to the vehicle. ALL active and pending DTCs will be cleared, the MIL (Check Engine) light will be turned off, and emission monitors will be reset.\n\nContinue?",
            onDismiss = { showClearDialog = false },
            onConfirm = { showClearDialog = false; coroutineScope.launch { viewModel.clearDtcs() } },
            confirmText = if(isSpanish) "BORRAR" else "CLEAR",
            isDestructive = true
        )
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(if(isSpanish) "Diagnóstico DTC" else "DTC Diagnostics", color = Color.White, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark),
                    actions = {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                            Text("EN", color = if(isSpanish) MeetColors.textSecondary else Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            Switch(
                                checked = isSpanish,
                                onCheckedChange = { 
                                    isSpanish = it
                                    viewModel.setLanguage(if(it) "es" else "en")
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                                    checkedTrackColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.3f),
                                    uncheckedThumbColor = com.elysium369.meet.ui.theme.MeetColors.electricBlue,
                                    uncheckedTrackColor = com.elysium369.meet.ui.theme.MeetColors.electricBlue.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.padding(horizontal = 4.dp).height(24.dp)
                            )
                            Text("ES", color = if(isSpanish) Color.White else MeetColors.textSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTab, containerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark, contentColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(if(isSpanish) "Activos" else "Active", color = if (selectedTab == 0) com.elysium369.meet.ui.theme.MeetColors.error else MeetColors.textSecondary, modifier = Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(if(isSpanish) "Pend." else "Pend.", color = if (selectedTab == 1) MeetColors.warning else MeetColors.textSecondary, modifier = Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text(if(isSpanish) "Perm." else "Perm.", color = if (selectedTab == 2) com.elysium369.meet.ui.theme.MeetColors.electricBlue else MeetColors.textSecondary, modifier = Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) })
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text(if(isSpanish) "Monitores" else "Monitors", color = if (selectedTab == 3) com.elysium369.meet.ui.theme.MeetColors.neonGreen else MeetColors.textSecondary, modifier = Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) })
                    Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }, text = { Text("Manual", color = if (selectedTab == 4) Color.White else MeetColors.textSecondary, modifier = Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) })
                }
            }
        },
        containerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark
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
                                .border(1.dp, if(isScanning) Color.Transparent else com.elysium369.meet.ui.theme.MeetColors.neonGreen, RoundedCornerShape(8.dp))
                                .background(
                                    if(isScanning) Brush.horizontalGradient(listOf(com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.1f), Color.Transparent))
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
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = alpha), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if(isSpanish) "ESCANEANDO..." else "SCANNING...", color = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = alpha), fontWeight = FontWeight.Black, fontSize = 12.sp)
                                }
                            } else {
                                Text(if(isSpanish) "ESCANEAR" else "SCAN", color = com.elysium369.meet.ui.theme.MeetColors.neonGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                        Button(
                            onClick = { showClearDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .border(1.dp, if(isClearing) Color.Transparent else com.elysium369.meet.ui.theme.MeetColors.error, RoundedCornerShape(8.dp))
                                .background(
                                    if(isClearing) Brush.horizontalGradient(listOf(com.elysium369.meet.ui.theme.MeetColors.error.copy(alpha = 0.1f), Color.Transparent))
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
                                    Box(modifier = Modifier.size(12.dp).graphicsLayer(scaleX = scale, scaleY = scale).background(com.elysium369.meet.ui.theme.MeetColors.error, CircleShape))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if(isSpanish) "BORRANDO..." else "CLEARING...", color = com.elysium369.meet.ui.theme.MeetColors.error, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                }
                            } else {
                                Text(if(isSpanish) "BORRAR DTCs" else "CLEAR DTCs", color = com.elysium369.meet.ui.theme.MeetColors.error, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (clearResult != null) {
                    item {
                        com.elysium369.meet.ui.components.EliteCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(clearResult.orEmpty(), color = Color.White, modifier = Modifier.padding(12.dp))
                        }
                    }
                }

                val allDtcs = activeDtcs + pendingDtcs + permanentDtcs
                when (selectedTab) {
                    0 -> { // Active DTCs (Mode 03)
                        if (activeDtcs.isEmpty()) {
                            item { EmptyDtcState(if(isSpanish) "No hay códigos de falla activos" else "No active fault codes", com.elysium369.meet.ui.theme.MeetColors.neonGreen) }
                        } else {
                            items(activeDtcs) { dtc -> DtcCard(dtc, if(isSpanish) "ACTIVO" else "ACTIVE", com.elysium369.meet.ui.theme.MeetColors.error, navController, viewModel, isSpanish, allDtcs) }
                        }
                    }
                    1 -> { // Pending DTCs (Mode 07)
                        if (pendingDtcs.isEmpty()) {
                            item { EmptyDtcState(if(isSpanish) "No hay códigos pendientes.\nEstos son códigos que aún no encendieron la luz MIL." else "No pending codes.\nThese codes have not yet turned on the MIL.", MeetColors.warning) }
                        } else {
                            items(pendingDtcs) { dtc -> DtcCard(dtc, if(isSpanish) "PENDIENTE" else "PENDING", MeetColors.warning, navController, viewModel, isSpanish, allDtcs) }
                        }
                    }
                    2 -> { // Permanent DTCs (Mode 0A)
                        if (permanentDtcs.isEmpty()) {
                            item { EmptyDtcState(if(isSpanish) "No hay códigos permanentes.\nEstos son códigos que NO se pueden borrar manualmente." else "No permanent codes.\nThese codes CANNOT be manually cleared.", com.elysium369.meet.ui.theme.MeetColors.electricBlue) }
                        } else {
                            items(permanentDtcs) { dtc -> DtcCard(dtc, if(isSpanish) "PERMANENTE" else "PERMANENT", com.elysium369.meet.ui.theme.MeetColors.electricBlue, navController, viewModel, isSpanish, allDtcs) }
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
                        com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.3f),
                        com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.05f),
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
                color = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
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
                modifier = Modifier.border(1.dp, com.elysium369.meet.ui.theme.MeetColors.neonGreen, RoundedCornerShape(24.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        if(isSpanish) "ESCANEANDO SISTEMAS..." else "SCANNING SYSTEMS...",
                        color = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
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
                .border(2.dp, com.elysium369.meet.ui.theme.MeetColors.error.copy(alpha = pulseAlpha), CircleShape)
        )
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Search, 
                contentDescription = null,
                tint = com.elysium369.meet.ui.theme.MeetColors.error,
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
                color = com.elysium369.meet.ui.theme.MeetColors.error,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun DtcCard(dtc: String, severity: String, color: Color, navController: NavController, viewModel: ObdViewModel, isSpanish: Boolean, allDtcs: List<String> = emptyList()) {
    val definitions by viewModel.dtcDefinitions.collectAsState()
    val dtcInfo = definitions[dtc]
    
    val fallbackDesc = if (isSpanish) "Definición no encontrada o consultando base de datos..." else "Definition not found or querying database..."
    val desc = if (isSpanish) {
        dtcInfo?.let { translateDtcText(it.descriptionEs) } ?: fallbackDesc
    } else {
        dtcInfo?.descriptionEs ?: fallbackDesc
    }
    
    val vehicle by viewModel.selectedVehicle.collectAsState()
    val knowledgeGuide = com.elysium369.meet.data.local.KnowledgeBaseRepository.getGuideForDtc(
        dtc = dtc, 
        description = dtcInfo?.descriptionEs, 
        isSpanish = isSpanish,
        vehicleMake = vehicle?.make,
        vehicleModel = vehicle?.model
    )
    
    com.elysium369.meet.ui.components.EliteCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = color
    ) {
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
            
            if (dtc == "P0300") {
                val misfireCodes = allDtcs.filter { it.matches(Regex("P030[1-9]|P031[0-2]")) }
                if (misfireCodes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val cylinders = misfireCodes.joinToString(", ")
                    Surface(color = MeetColors.warning.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().border(1.dp, MeetColors.warning.copy(alpha = 0.5f), RoundedCornerShape(8.dp))) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(if(isSpanish) "⚠️ CILINDROS ESPECÍFICOS DETECTADOS:" else "⚠️ SPECIFIC CYLINDERS DETECTED:", color = MeetColors.warning, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                            Text(cylinders, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(color = MeetColors.warning.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().border(1.dp, MeetColors.warning.copy(alpha = 0.5f), RoundedCornerShape(8.dp))) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(if(isSpanish) "⚠️ CILINDROS ESPECÍFICOS NO ENCONTRADOS:" else "⚠️ SPECIFIC CYLINDERS NOT FOUND:", color = MeetColors.warning, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                            Text(if(isSpanish) "La computadora (ECU) de este vehículo no registró códigos individuales. ESTO ES NORMAL en esta marca. Siga la técnica manual de 'balance de cilindros' desconectando cada bobina una a la vez para ubicar la falla." else "ECU hasn't logged specific codes. Normal for this make. Perform manual cylinder drop test.", color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ═══ URGENCY + DRIVABILITY BANNER ═══
            val urgColor = when(knowledgeGuide.urgency) {
                "inmediata" -> MeetColors.error
                "pronto" -> MeetColors.warning
                else -> MeetColors.neonGreen
            }
            val urgText = when(knowledgeGuide.urgency) {
                "inmediata" -> if(isSpanish) "🚨 ATENCIÓN INMEDIATA" else "🚨 IMMEDIATE ATTENTION"
                "pronto" -> if(isSpanish) "⚠️ REPARAR PRONTO" else "⚠️ REPAIR SOON"
                else -> if(isSpanish) "✅ RUTINARIA" else "✅ ROUTINE"
            }
            val driveText = if(knowledgeGuide.canDrive) {
                if(isSpanish) "✅ Puede conducir con precaución" else "✅ Can drive with caution"
            } else {
                if(isSpanish) "🚫 NO CONDUZCA — Riesgo de daño mayor" else "🚫 DO NOT DRIVE — Risk of further damage"
            }
            val driveColor = if(knowledgeGuide.canDrive) MeetColors.neonGreen else MeetColors.error

            Surface(color = urgColor.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().border(1.dp, urgColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(urgText, color = urgColor, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.weight(1f))
                        if (knowledgeGuide.sourcesCount > 0) {
                            Text("📚 ${knowledgeGuide.sourcesCount} fuentes", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(driveText, color = driveColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ═══ COST + TIME BAR ═══
            if (knowledgeGuide.costEstimate != null) {
                Surface(color = Color(0xFF1A1A2E), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF2D2D44), RoundedCornerShape(8.dp))) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if(isSpanish) "💰 COSTO ESTIMADO" else "💰 ESTIMATED COST", color = MeetColors.neonGreen, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                            Text("$${knowledgeGuide.costEstimate.minCost.toInt()} — $${knowledgeGuide.costEstimate.maxCost.toInt()} USD", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(if(isSpanish) "⏱️ TIEMPO" else "⏱️ TIME", color = MeetColors.electricBlue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                            Text("${knowledgeGuide.timeHours}h", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ═══ SYSTEM + STANDARD ═══
            Surface(color = MeetColors.electricBlue.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().border(1.dp, MeetColors.electricBlue.copy(alpha = 0.3f), RoundedCornerShape(8.dp))) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row {
                        Text(if(isSpanish) "⚙️ SISTEMA: " else "⚙️ SYSTEM: ", color = MeetColors.electricBlue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                        Text(knowledgeGuide.systemAffected, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(knowledgeGuide.standard, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ═══ SYMPTOMS ═══
            if (knowledgeGuide.symptoms.isNotEmpty()) {
                Surface(color = MeetColors.warning.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().border(1.dp, MeetColors.warning.copy(alpha = 0.3f), RoundedCornerShape(8.dp))) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(if(isSpanish) "🔍 SÍNTOMAS" else "🔍 SYMPTOMS", color = MeetColors.warning, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.height(6.dp))
                        knowledgeGuide.symptoms.forEach { symptom ->
                            Text("  • " + (if(isSpanish) translateDtcText(symptom) else symptom), color = Color.White, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ═══ RANKED CAUSES ═══
            Surface(color = Color(0xFF1A1A2E), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF2D2D44), RoundedCornerShape(8.dp))) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(if(isSpanish) "🎯 CAUSAS PROBABLES" else "🎯 PROBABLE CAUSES", color = Color(0xFFFF6B6B), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(6.dp))
                    knowledgeGuide.possibleCauses.forEach { cause ->
                        Text("  " + (if(isSpanish) translateDtcText(cause) else cause), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(3.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ═══ DIAGNOSTIC STEPS ═══
            Surface(color = MeetColors.neonGreen.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().border(1.dp, MeetColors.neonGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(if(isSpanish) "🛠️ PROCEDIMIENTO DE DIAGNÓSTICO" else "🛠️ DIAGNOSTIC PROCEDURE", color = MeetColors.neonGreen, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                    Text(if(isSpanish) "(Ordenado de menor a mayor costo)" else "(Ordered from lowest to highest cost)", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    // Split solution into steps and render each
                    val solutionText = if(isSpanish) translateDtcText(knowledgeGuide.recommendedSolution) else knowledgeGuide.recommendedSolution
                    val lines = solutionText.split("\n").filter { it.isNotBlank() }
                    // Find lines that look like steps (start with number or contain diagnostic info)
                    val stepLines = lines.filter { line ->
                        val t = line.trim()
                        t.startsWith("1.") || t.startsWith("2.") || t.startsWith("3.") || t.startsWith("4.") || t.startsWith("5.") || t.startsWith("6.") || t.startsWith("7.")
                    }
                    val headerLines = lines.filter { line ->
                        val t = line.trim()
                        t.startsWith("━") || t.startsWith("═") || t.contains("GUÍA") || t.contains("Sistema:") || t.contains("Descripción:") || t.contains("Urgencia:") || t.contains("Puede conducir") || t.contains("Costo estimado") || t.contains("Tiempo estimado") || t.contains("📚") || t.startsWith("•")
                    }
                    if (stepLines.isNotEmpty()) {
                        stepLines.forEachIndexed { idx, step ->
                            val stepColor = if (step.contains("⚠️") || step.contains("SEGURIDAD") || step.contains("PRECAUCIÓN")) MeetColors.error else Color.White
                            Surface(color = if(idx % 2 == 0) Color.White.copy(alpha = 0.03f) else Color.Transparent, shape = RoundedCornerShape(4.dp)) {
                                Text(step.trim(), color = stepColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp))
                            }
                        }
                    } else {
                        Text(solutionText, color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            val freezeFrame by viewModel.freezeFrameData.collectAsState()
            
            // Filter freeze frame entries scoped to THIS specific DTC code
            val scopedFrame = freezeFrame.filter { it.key.startsWith("$dtc:") }
            
            if (scopedFrame.isNotEmpty()) {
                Text(if(isSpanish) "❄️ DATOS DE CUADRO CONGELADO:" else "❄️ FREEZE FRAME DATA:", color = com.elysium369.meet.ui.theme.MeetColors.electricBlue, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
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
                com.elysium369.meet.ui.components.EliteTextButton(
                    text = if(isSpanish) "🤖 CONSULTAR IA" else "🤖 CONSULT AI",
                    onClick = { navController.navigate("ai/$dtc") },
                    modifier = Modifier.weight(1f),
                    color = com.elysium369.meet.ui.theme.MeetColors.neonGreen
                )
                
                val coroutineScope = rememberCoroutineScope()
                com.elysium369.meet.ui.components.EliteTextButton(
                    text = "❄️ FF DATA",
                    onClick = { coroutineScope.launch { viewModel.refreshFreezeFrame(dtc) } },
                    modifier = Modifier.weight(1f),
                    color = com.elysium369.meet.ui.theme.MeetColors.electricBlue
                )
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
                Text(if(isSpanish) "Monitores de emisiones no leídos aún." else "Emission monitors not read yet.", color = com.elysium369.meet.ui.theme.MeetColors.textSecondary)
                Spacer(modifier = Modifier.height(16.dp))
                com.elysium369.meet.ui.components.EliteTextButton(
                    text = if(isSpanish) "LEER MONITORES" else "READ MONITORS",
                    onClick = { coroutineScope.launch { viewModel.refreshDiagnostics() } },
                    color = com.elysium369.meet.ui.theme.MeetColors.neonGreen
                )
            }
        }
    } else {
        // MIL Status
        com.elysium369.meet.ui.components.EliteCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = if (readiness.milOn) com.elysium369.meet.ui.theme.MeetColors.error else com.elysium369.meet.ui.theme.MeetColors.neonGreen
        ) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(if(isSpanish) "LUZ MIL (CHECK ENGINE)" else "MIL (CHECK ENGINE)", color = com.elysium369.meet.ui.theme.MeetColors.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    val encendida = if(isSpanish) "🔴 ENCENDIDA" else "🔴 ON"
                    val apagada = if(isSpanish) "🟢 APAGADA" else "🟢 OFF"
                    Text(if (readiness.milOn) encendida else apagada, color = if (readiness.milOn) com.elysium369.meet.ui.theme.MeetColors.error else com.elysium369.meet.ui.theme.MeetColors.neonGreen, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                }
                Text("${readiness.dtcCount} DTCs", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        // Monitors
        Text(if(isSpanish) "MONITORES DE EMISIÓN" else "EMISSION MONITORS", color = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        val passedCount = readiness.monitors.count { it.complete }
        val totalCount = readiness.monitors.size
        val completados = if(isSpanish) "completados" else "completed"
        Text("$passedCount / $totalCount $completados", color = if (passedCount == totalCount) com.elysium369.meet.ui.theme.MeetColors.neonGreen else MeetColors.warning, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(8.dp))
        readiness.monitors.forEach { monitor ->
            com.elysium369.meet.ui.components.EliteCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                borderColor = if (monitor.complete) com.elysium369.meet.ui.theme.MeetColors.neonGreen else MeetColors.warning
            ) {
                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(monitor.name, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    val listo = if(isSpanish) "✅ Listo" else "✅ Ready"
                    val inc = if(isSpanish) "⏳ Incompleto" else "⏳ Inc."
                    Text(if (monitor.complete) listo else inc, color = if (monitor.complete) com.elysium369.meet.ui.theme.MeetColors.neonGreen else MeetColors.warning, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
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
            label = { Text(if(isSpanish) "Ingresar Código (Ej. P0300)" else "Enter Code (e.g. P0300)", color = com.elysium369.meet.ui.theme.MeetColors.textSecondary) },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                unfocusedBorderColor = MeetColors.textSecondary,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (searchQuery.isNotEmpty() && searchResults.isEmpty()) {
            com.elysium369.meet.ui.components.EliteTextButton(
                text = if(isSpanish) "BUSCAR" else "SEARCH",
                onClick = { viewModel.searchDtcManual(searchQuery) },
                modifier = Modifier.fillMaxWidth(),
                color = com.elysium369.meet.ui.theme.MeetColors.neonGreen
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (searchResults.isNotEmpty() || searchQuery.isNotEmpty()) {
            if (searchResults.isEmpty() && searchQuery.length >= 3) {
                EmptyDtcState(if(isSpanish) "No se encontró el código en la base de datos." else "Code not found in the database.", MeetColors.textSecondary)
            } else {
                Text(if(isSpanish) "Resultados (${searchResults.size})" else "Results (${searchResults.size})", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                searchResults.forEach { dtc ->
                    val color = when (dtc.severity.uppercase()) {
                        "HIGH" -> com.elysium369.meet.ui.theme.MeetColors.error
                        "MODERATE" -> MeetColors.warning
                        else -> com.elysium369.meet.ui.theme.MeetColors.neonGreen
                    }
                    
                    val desc = if (isSpanish) translateDtcText(dtc.descriptionEs) else dtc.descriptionEs
                    
                    com.elysium369.meet.ui.components.EliteCard(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        borderColor = color
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

                            // ═══ LOAD ELITE GUIDE ═══
                            val guide = com.elysium369.meet.data.local.KnowledgeBaseRepository.getGuideForDtc(dtc.code, dtc.descriptionEs, isSpanish)

                            // ═══ URGENCY BANNER ═══
                            val urgColor2 = when(guide.urgency) {
                                "inmediata" -> MeetColors.error
                                "pronto" -> MeetColors.warning
                                else -> MeetColors.neonGreen
                            }
                            val urgText2 = when(guide.urgency) {
                                "inmediata" -> if(isSpanish) "🚨 ATENCIÓN INMEDIATA" else "🚨 IMMEDIATE"
                                "pronto" -> if(isSpanish) "⚠️ REPARAR PRONTO" else "⚠️ REPAIR SOON"
                                else -> if(isSpanish) "✅ RUTINARIA" else "✅ ROUTINE"
                            }
                            val driveText2 = if(guide.canDrive) {
                                if(isSpanish) "✅ Puede conducir" else "✅ Can drive"
                            } else {
                                if(isSpanish) "🚫 NO CONDUZCA" else "🚫 DO NOT DRIVE"
                            }
                            Surface(color = urgColor2.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().border(1.dp, urgColor2.copy(alpha = 0.5f), RoundedCornerShape(6.dp))) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(urgText2, color = urgColor2, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                                        Spacer(modifier = Modifier.weight(1f))
                                        if (guide.sourcesCount > 0) Text("📚 ${guide.sourcesCount}", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(driveText2, color = if(guide.canDrive) MeetColors.neonGreen else MeetColors.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                            }

                            // ═══ COST + TIME ═══
                            if (guide.costEstimate != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(color = Color(0xFF1A1A2E), shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("💰 $${guide.costEstimate.minCost.toInt()}–$${guide.costEstimate.maxCost.toInt()} USD", color = MeetColors.neonGreen, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black)
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text("⏱️ ${guide.timeHours}h", color = MeetColors.electricBlue, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // ═══ SYMPTOMS ═══
                            if (guide.symptoms.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(if(isSpanish) "🔍 SÍNTOMAS:" else "🔍 SYMPTOMS:", color = MeetColors.warning, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                                guide.symptoms.forEach { s ->
                                    Text("  • $s", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            // ═══ RANKED CAUSES ═══
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(if(isSpanish) "🎯 CAUSAS PROBABLES:" else "🎯 PROBABLE CAUSES:", color = Color(0xFFFF6B6B), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                            guide.possibleCauses.forEach { c ->
                                Text("  $c", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                            }

                            // ═══ DIAGNOSTIC STEPS ═══
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(if(isSpanish) "🛠️ DIAGNÓSTICO (menor→mayor costo):" else "🛠️ DIAGNOSIS (low→high cost):", color = MeetColors.neonGreen, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                            val steps = guide.recommendedSolution.split("\n").filter { line ->
                                val t = line.trim()
                                t.startsWith("1.") || t.startsWith("2.") || t.startsWith("3.") || t.startsWith("4.") || t.startsWith("5.") || t.startsWith("6.") || t.startsWith("7.")
                            }
                            if (steps.isNotEmpty()) {
                                steps.forEach { step ->
                                    val sColor = if (step.contains("⚠️") || step.contains("PRECAUCIÓN")) MeetColors.error else Color.White.copy(alpha = 0.85f)
                                    Text(step.trim(), color = sColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            com.elysium369.meet.ui.components.EliteTextButton(
                                text = if(isSpanish) "🤖 CONSULTAR IA" else "🤖 CONSULT AI",
                                onClick = { navController.navigate("ai/${dtc.code}") },
                                modifier = Modifier.fillMaxWidth(),
                                color = com.elysium369.meet.ui.theme.MeetColors.neonGreen
                            )
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
