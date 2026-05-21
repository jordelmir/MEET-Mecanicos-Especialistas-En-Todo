package com.elysium369.meet.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.*
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.launch
import com.elysium369.meet.data.local.entities.DtcDefinitionEntity
import com.elysium369.meet.data.local.KnowledgeBaseRepository
import androidx.hilt.navigation.compose.hiltViewModel
import com.elysium369.meet.ui.FleetChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DtcScreen(navController: NavController, viewModel: ObdViewModel) {
    val chatViewModel: FleetChatViewModel = hiltViewModel()
    val vehicle by viewModel.selectedVehicle.collectAsState()
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
            title = if(isSpanish) "BORRAR CÓDIGOS DTC" else "CLEAR DTC CODES",
            message = if(isSpanish) "Esto enviará el comando OBD Mode 04. Se borrarán TODOS los DTCs activos y pendientes, se apagará la luz MIL (Check Engine), y se resetearán los monitores de emisiones.\n\n¿Desea continuar con la purga del sistema?" else "This will transmit OBD Mode 04. ALL active and pending DTCs will be cleared, the MIL (Check Engine) light extinguished, and emission monitors reset.\n\nProceed with system purge?",
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
                EliteTopAppBar(
                    title = if(isSpanish) "DIAGNÓSTICO DTC" else "DTC DIAGNOSTICS",
                    subtitle = if(isSpanish) "ANÁLISIS DE CÓDIGOS OBD-II" else "OBD-II CODE ANALYSIS",
                    onBackClick = { navController.popBackStack() },
                    actions = {
                        // Sleek Bilingual Selector
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MeetColors.cardBackground)
                                .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                .padding(2.dp)
                        ) {
                            val enBg = if (!isSpanish) MeetColors.neonGreen else Color.Transparent
                            val enText = if (!isSpanish) MeetColors.backgroundDeep else MeetColors.textSecondary
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(enBg)
                                    .clickable {
                                        if (isSpanish) {
                                            isSpanish = false
                                            viewModel.setLanguage("en")
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text("EN", color = enText, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                            }
                            val esBg = if (isSpanish) MeetColors.neonGreen else Color.Transparent
                            val esText = if (isSpanish) MeetColors.backgroundDeep else MeetColors.textSecondary
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(esBg)
                                    .clickable {
                                        if (!isSpanish) {
                                            isSpanish = true
                                            viewModel.setLanguage("es")
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text("ES", color = esText, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                )
                
                // Professional HUD selector tab bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MeetColors.backgroundDark)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf(
                        if(isSpanish) "Activos" else "Active",
                        if(isSpanish) "Pend." else "Pending",
                        if(isSpanish) "Perm." else "Permanent",
                        if(isSpanish) "Monitores" else "Monitors",
                        if(isSpanish) "Manual" else "Manual"
                    )
                    val tabColors = listOf(
                        MeetColors.error,
                        MeetColors.warning,
                        MeetColors.electricBlue,
                        MeetColors.neonGreen,
                        Color.White
                    )
                    tabs.forEachIndexed { index, title ->
                        val selected = selectedTab == index
                        val accentColor = tabColors[index]
                        Box(
                            modifier = Modifier
                                .weight(1.5f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) accentColor.copy(alpha = 0.15f) else Color.Transparent)
                                .border(
                                    width = 1.dp,
                                    color = if (selected) accentColor.copy(alpha = 0.4f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedTab = index }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title.uppercase(),
                                color = if (selected) accentColor else MeetColors.textSecondary,
                                fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        },
        containerColor = MeetColors.backgroundDark
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
                        // Premium Control Buttons Dock
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            EliteButton(
                                text = if (isScanning) (if(isSpanish) "ESCANEANDO..." else "SCANNING...") else (if(isSpanish) "ESCANEAR" else "SCAN"),
                                onClick = { coroutineScope.launch { viewModel.refreshDiagnostics() } },
                                color = MeetColors.neonGreen,
                                textColor = MeetColors.backgroundDeep,
                                isEnabled = !isScanning && !isClearing,
                                modifier = Modifier.weight(1f)
                            )
                            EliteOutlinedButton(
                                text = if (isClearing) (if(isSpanish) "BORRANDO..." else "CLEARING...") else (if(isSpanish) "BORRAR DTCs" else "CLEAR DTCs"),
                                onClick = { showClearDialog = true },
                                color = MeetColors.error,
                                isEnabled = !isScanning && !isClearing,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (activeDtcs.isNotEmpty()) {
                        item {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            EliteButton(
                                text = if(isSpanish) "📢 REPORTAR CÓDIGOS A FLOTA" else "📢 REPORT DTCs TO FLEET",
                                onClick = {
                                    val vehicleName = "${vehicle?.make ?: ""} ${vehicle?.model ?: ""}".trim()
                                    val displayVehicle = if (vehicleName.isBlank()) "Vehículo Activo" else vehicleName
                                    chatViewModel.selectBusiness("b1")
                                    val dummyPartner = com.elysium369.meet.data.local.entities.FleetMemberEntity(
                                        id = "d1",
                                        businessId = "b1",
                                        userId = "Chofer_Juan",
                                        role = "CONDUCTOR",
                                        email = "juan@fleet.com",
                                        inviteStatus = "ACCEPTED",
                                        joinedAt = System.currentTimeMillis()
                                    )
                                    chatViewModel.selectPartner(dummyPartner)
                                    chatViewModel.sendDtcAlertMessage(displayVehicle, activeDtcs)
                                    android.widget.Toast.makeText(context, if(isSpanish) "Reporte enviado a la flota con éxito" else "DTC report sent to fleet", android.widget.Toast.LENGTH_LONG).show()
                                },
                                color = MeetColors.cyberCyan,
                                textColor = MeetColors.backgroundDeep,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (clearResult != null) {
                        item {
                            EliteCard(
                                modifier = Modifier.fillMaxWidth(),
                                borderColor = MeetColors.error
                            ) {
                                Text(
                                    text = clearResult.orEmpty(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    val allDtcs = activeDtcs + pendingDtcs + permanentDtcs
                    when (selectedTab) {
                        0 -> { // Active DTCs (Mode 03)
                            if (activeDtcs.isEmpty()) {
                                item { EmptyDtcState(if(isSpanish) "No hay códigos de falla activos detectados en la ECU." else "No active fault codes detected in the ECU.", MeetColors.neonGreen) }
                            } else {
                                itemsIndexed(activeDtcs) { index, dtc ->
                                    AnimatedEntrance(index = index) {
                                        DtcCard(dtc, if(isSpanish) "ACTIVO" else "ACTIVE", MeetColors.error, navController, viewModel, isSpanish, allDtcs)
                                    }
                                }
                            }
                        }
                        1 -> { // Pending DTCs (Mode 07)
                            if (pendingDtcs.isEmpty()) {
                                item { EmptyDtcState(if(isSpanish) "No hay códigos pendientes. Estos códigos no han encendido la luz MIL aún." else "No pending codes. These codes have not triggered the MIL yet.", MeetColors.warning) }
                            } else {
                                itemsIndexed(pendingDtcs) { index, dtc ->
                                    AnimatedEntrance(index = index) {
                                        DtcCard(dtc, if(isSpanish) "PENDIENTE" else "PENDING", MeetColors.warning, navController, viewModel, isSpanish, allDtcs)
                                    }
                                }
                            }
                        }
                        2 -> { // Permanent DTCs (Mode 0A)
                            if (permanentDtcs.isEmpty()) {
                                item { EmptyDtcState(if(isSpanish) "No hay códigos permanentes. Estos no se pueden borrar de forma manual." else "No permanent codes. These cannot be cleared manually.", MeetColors.cyberCyan) }
                            } else {
                                itemsIndexed(permanentDtcs) { index, dtc ->
                                    AnimatedEntrance(index = index) {
                                        DtcCard(dtc, if(isSpanish) "PERMANENTE" else "PERMANENT", MeetColors.cyberCyan, navController, viewModel, isSpanish, allDtcs)
                                    }
                                }
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
            AnimatedVisibility(
                visible = isScanning,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                EliteScanningAnimation(isSpanish)
            }

            // --- Creative Clearing Overlay ---
            AnimatedVisibility(
                visible = isClearing,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
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
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanLine"
    )
    
    val consoleLines = remember { mutableStateListOf<String>() }
    val fullLogs = listOf(
        "[INIT] OBD-II connection established.",
        "[SCAN] Querying ECU Address 0x7E8 (Engine control)...",
        "[SCAN] Loading PID configurations...",
        "[SCAN] Sweeping Active DTC registers (Mode 03)...",
        "[SCAN] Sweep successful. Reading transmission...",
        "[SCAN] Querying Transmission ECU Address 0x7E9...",
        "[SCAN] Sweeping Pending DTC registers (Mode 07)...",
        "[SCAN] Checking Permanent DTC registers (Mode 0A)...",
        "[SCAN] Accessing Readiness Monitor byte maps...",
        "[SUCCESS] Diagnostic sweep complete. Refreshing UI."
    )
    
    LaunchedEffect(Unit) {
        consoleLines.clear()
        for (line in fullLogs) {
            consoleLines.add(line)
            if (consoleLines.size > 5) {
                consoleLines.removeAt(0)
            }
            kotlinx.coroutines.delay(400)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center
    ) {
        // Futuristic Matrix HUD Lines
        Canvas(modifier = Modifier.fillMaxSize()) {
            val height = size.height
            val width = size.width
            val currentY = height * scanLinePos
            
            // Draw digital matrix grid
            val gridColor = MeetColors.neonGreen.copy(alpha = 0.05f)
            val gridStep = 40.dp.toPx()
            var x = 0f
            while (x < width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, height), 1f)
                x += gridStep
            }
            var y = 0f
            while (y < height) {
                drawLine(gridColor, Offset(0f, y), Offset(width, y), 1f)
                y += gridStep
            }
            
            // Scanning Beam
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        MeetColors.neonGreen.copy(alpha = 0.25f),
                        MeetColors.neonGreen.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    startY = currentY - 150f,
                    endY = currentY + 10f
                ),
                topLeft = Offset(0f, currentY - 150f),
                size = Size(width, 160f)
            )
            
            // Bright scanner line
            drawLine(
                color = MeetColors.neonGreen,
                start = Offset(0f, currentY),
                end = Offset(width, currentY),
                strokeWidth = 2.dp.toPx()
            )
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
        ) {
            // Glowing Radar Target
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .drawBehind {
                        drawCircle(
                            color = MeetColors.neonGreen.copy(alpha = 0.1f),
                            radius = size.minDimension / 2f
                        )
                        drawCircle(
                            color = MeetColors.neonGreen.copy(alpha = 0.3f),
                            radius = size.minDimension / 3f,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                        drawCircle(
                            color = MeetColors.neonGreen.copy(alpha = 0.5f),
                            radius = size.minDimension / 4f,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                        // Crosshairs
                        drawLine(
                            color = MeetColors.neonGreen.copy(alpha = 0.4f),
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = MeetColors.neonGreen.copy(alpha = 0.4f),
                            start = Offset(size.width / 2f, 0f),
                            end = Offset(size.width / 2f, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Heartbeat pulse dot
                val pulseScale = infiniteTransition.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 1.4f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseScale"
                )
                val pulseAlpha = infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 0.9f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer(scaleX = pulseScale.value, scaleY = pulseScale.value, alpha = pulseAlpha.value)
                        .background(MeetColors.neonGreen, CircleShape)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = if(isSpanish) "ESCANEANDO ECU..." else "SCANNING ECU...",
                color = Color.White,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.headlineSmall,
                letterSpacing = 1.sp
            )
            
            Text(
                text = if(isSpanish) "CONEXIÓN OBD ESTABLE" else "OBD CONNECTION STABLE",
                color = MeetColors.neonGreen,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
            
            // Console display box
            Surface(
                color = Color(0xFF020612),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    consoleLines.forEach { line ->
                        Text(
                            text = line,
                            color = if (line.contains("[SUCCESS]")) MeetColors.neonGreen else MeetColors.textSecondary,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    val consoleLines = remember { mutableStateListOf<String>() }
    val fullLogs = listOf(
        "[INIT] Requesting ECU write permission...",
        "[CLEAR] Sending Mode 04 reset command...",
        "[CLEAR] Broadasting clear command to 0x7E8...",
        "[CLEAR] Broadasting clear command to 0x7E9...",
        "[CLEAR] Deactivating MIL engine lamp dashboard relay...",
        "[CLEAR] Resetting temporary diagnostic values...",
        "[SUCCESS] Fault code memory purge completed."
    )
    
    LaunchedEffect(Unit) {
        consoleLines.clear()
        for (line in fullLogs) {
            consoleLines.add(line)
            if (consoleLines.size > 5) {
                consoleLines.removeAt(0)
            }
            kotlinx.coroutines.delay(350)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        // Red Pulsing Ring
        Box(
            modifier = Modifier
                .size(160.dp)
                .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                .border(2.dp, MeetColors.error.copy(alpha = pulseAlpha), CircleShape)
        )
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MeetColors.error.copy(alpha = 0.15f))
                    .border(1.dp, MeetColors.error, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚠️",
                    fontSize = 32.sp,
                    color = MeetColors.error
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = if(isSpanish) "BORRANDO REGISTROS DTC..." else "PURGING DTC MEMORY...",
                color = Color.White,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.headlineSmall
            )
            
            Text(
                text = if(isSpanish) "TRANSMITIENDO MODO 04" else "TRANSMITTING MODE 04",
                color = MeetColors.error,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Console display box
            Surface(
                color = Color(0xFF0C0202),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .border(1.dp, MeetColors.error.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    consoleLines.forEach { line ->
                        Text(
                            text = line,
                            color = if (line.contains("[SUCCESS]")) MeetColors.neonGreen else MeetColors.textSecondary,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DtcCard(
    dtc: String,
    severity: String,
    color: Color,
    navController: NavController,
    viewModel: ObdViewModel,
    isSpanish: Boolean,
    allDtcs: List<String> = emptyList()
) {
    val definitions by viewModel.dtcDefinitions.collectAsState()
    val dtcInfo = definitions[dtc]
    val coroutineScope = rememberCoroutineScope()
    
    val fallbackDesc = com.elysium369.meet.ui.components.DtcUtils.getDynamicDtcFallbackDescription(dtc, isSpanish)
    val desc = if (isSpanish) {
        dtcInfo?.let {
            val raw = it.descriptionEs
            if (raw.isNullOrBlank() || raw.contains("no disponible localmente") || raw.contains("no disponible offline") || raw.contains("no encontrada")) {
                fallbackDesc
            } else {
                translateDtcText(raw)
            }
        } ?: fallbackDesc
    } else {
        dtcInfo?.let {
            val raw = it.descriptionEn
            val d = if (raw.isNullOrBlank() || raw.contains("not available locally") || raw.contains("offline") || raw.contains("not found")) {
                fallbackDesc
            } else {
                raw
            }
            d
        } ?: fallbackDesc
    }
    
    val vehicle by viewModel.selectedVehicle.collectAsState()
    val knowledgeGuide = com.elysium369.meet.data.local.KnowledgeBaseRepository.getGuideForDtc(
        dtc = dtc, 
        description = if (isSpanish) dtcInfo?.descriptionEs else dtcInfo?.descriptionEn, 
        isSpanish = isSpanish,
        vehicleMake = vehicle?.make,
        vehicleModel = vehicle?.model
    )
    
    val gridColor = color.copy(alpha = 0.03f)

    EliteCard(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // Background digital matrix grid matching severity color
                val spacing = 20.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 0.5.dp.toPx())
                    x += spacing
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 0.5.dp.toPx())
                    y += spacing
                }
            },
        borderColor = color
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            // Header Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(color.copy(alpha = 0.15f))
                        .border(1.dp, color, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = severity,
                        color = color,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = dtc,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = desc,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Black
            )
            
            // P0300 Misfire Sub-alert panel
            if (dtc == "P0300") {
                val misfireCodes = allDtcs.filter { it.matches(Regex("P030[1-9]|P031[0-2]")) }
                if (misfireCodes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val cylinders = misfireCodes.joinToString(", ")
                    Surface(
                        color = MeetColors.warning.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MeetColors.warning.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = if(isSpanish) "⚠️ CILINDROS DETECTADOS CON FALLO:" else "⚠️ MISFIRING CYLINDERS DETECTED:",
                                color = MeetColors.warning,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = cylinders,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MeetColors.warning.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MeetColors.warning.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = if(isSpanish) "⚠️ ERROR COMPLETO DE CONTROL DE MISFIRE:" else "⚠️ FULL MISFIRE SYSTEM REPORT:",
                                color = MeetColors.warning,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if(isSpanish) "La computadora (ECU) reporta fallos generalizados pero sin códigos individuales aún. Realice prueba física o visualice contadores de cilindro en Mode 06 para ubicar la falla." else "ECU reports random misfires but hasn't logged individual cylinders. Check cylinder drop tests or Mode 06 counters to locate the issue.",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
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
                "inmediata" -> if(isSpanish) "🚨 RIESGO INMEDIATO" else "🚨 IMMEDIATE DANGER"
                "pronto" -> if(isSpanish) "⚠️ REPARACIÓN RECOMENDADA" else "⚠️ REPAIR RECOMMENDED"
                else -> if(isSpanish) "✅ CHEQUEO RUTINARIO" else "✅ ROUTINE CHECK"
            }
            val driveText = if(knowledgeGuide.canDrive) {
                if(isSpanish) "✅ Seguro para conducir con precaución" else "✅ Safe to drive with caution"
            } else {
                if(isSpanish) "🚫 NO CONDUZCA - Riesgo de fallo grave en motor" else "🚫 DO NOT DRIVE - Risk of severe powertrain damage"
            }
            val driveColor = if(knowledgeGuide.canDrive) MeetColors.neonGreen else MeetColors.error

            Surface(
                color = urgColor.copy(alpha = 0.08f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, urgColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = urgText,
                            color = urgColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (knowledgeGuide.sourcesCount > 0) {
                            Text(
                                text = "📚 ${knowledgeGuide.sourcesCount} " + (if(isSpanish) "fuentes" else "sources"),
                                color = MeetColors.textSecondary,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = driveText,
                        color = driveColor,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ═══ COST + TIME BAR ═══
            if (knowledgeGuide.costEstimate != null) {
                Surface(
                    color = MeetColors.cardBackgroundLighter.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(10.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if(isSpanish) "💰 COSTO ESTIMADO" else "💰 ESTIMATED COST",
                                color = MeetColors.neonGreen,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$${knowledgeGuide.costEstimate.minCost.toInt()} — $${knowledgeGuide.costEstimate.maxCost.toInt()} USD",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(MeetColors.borderSubtle)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if(isSpanish) "⏱️ TIEMPO" else "⏱️ TIME",
                                color = MeetColors.electricBlue,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${knowledgeGuide.timeHours}h",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // ═══ SYSTEM + STANDARD ═══
            Surface(
                color = MeetColors.electricBlue.copy(alpha = 0.05f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MeetColors.electricBlue.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if(isSpanish) "⚙️ SISTEMA: " else "⚙️ SYSTEM: ",
                        color = MeetColors.electricBlue,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = knowledgeGuide.systemAffected.uppercase(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = knowledgeGuide.standard,
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ═══ SYMPTOMS ═══
            if (knowledgeGuide.symptoms.isNotEmpty()) {
                Surface(
                    color = MeetColors.warning.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MeetColors.warning.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if(isSpanish) "🔍 SÍNTOMAS REGISTRADOS" else "🔍 LOGGED SYMPTOMS",
                            color = MeetColors.warning,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        knowledgeGuide.symptoms.forEach { symptom ->
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .background(MeetColors.warning, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if(isSpanish) translateDtcText(symptom) else symptom,
                                    color = Color.White.copy(alpha = 0.9f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // ═══ CAUSES ═══
            Surface(
                color = MeetColors.cardBackgroundLighter.copy(alpha = 0.2f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if(isSpanish) "🎯 CAUSAS PROBABLES" else "🎯 PROBABLE CAUSES",
                        color = Color(0xFFFF6B6B),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    knowledgeGuide.possibleCauses.forEach { cause ->
                        Row(
                            modifier = Modifier.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(Color(0xFFFF6B6B), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if(isSpanish) translateDtcText(cause) else cause,
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ═══ DIAGNOSTIC STEPS ═══
            Surface(
                color = MeetColors.neonGreen.copy(alpha = 0.03f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = if(isSpanish) "🛠️ PROCEDIMIENTO DE DIAGNÓSTICO" else "🛠️ DIAGNOSTIC PROCEDURE",
                        color = MeetColors.neonGreen,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = if(isSpanish) "(Ordenado de menor a mayor costo)" else "(Ordered from lowest to highest cost)",
                        color = MeetColors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val solutionText = if(isSpanish) translateDtcText(knowledgeGuide.recommendedSolution) else knowledgeGuide.recommendedSolution
                    val lines = solutionText.split("\n").filter { it.isNotBlank() }
                    val stepLines = lines.filter { line ->
                        val t = line.trim()
                        t.startsWith("1.") || t.startsWith("2.") || t.startsWith("3.") || t.startsWith("4.") || t.startsWith("5.") || t.startsWith("6.") || t.startsWith("7.")
                    }
                    
                    if (stepLines.isNotEmpty()) {
                        stepLines.forEach { step ->
                            val stepNum = step.substringBefore(".").trim()
                            val stepDesc = step.substringAfter(".").trim()
                            val isAlert = step.contains("⚠️") || step.contains("SEGURIDAD") || step.contains("PRECAUCIÓN")
                            val stepColor = if (isAlert) MeetColors.error else Color.White
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(if (isAlert) MeetColors.error.copy(alpha = 0.15f) else MeetColors.neonGreen.copy(alpha = 0.12f))
                                        .border(1.dp, if (isAlert) MeetColors.error else MeetColors.neonGreen, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stepNum,
                                        color = if (isAlert) MeetColors.error else MeetColors.neonGreen,
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stepDesc,
                                    color = stepColor,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = solutionText,
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            val freezeFrame by viewModel.freezeFrameData.collectAsState()
            val scopedFrame = freezeFrame.filter { it.key.startsWith("$dtc:") }
            
            if (scopedFrame.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                // Monospace Cyber Terminal for Freeze Frame
                Surface(
                    color = Color(0xFF02050B),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MeetColors.cyberCyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(MeetColors.cyberCyan, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if(isSpanish) "❄️ DATOS DE CUADRO CONGELADO (FF):" else "❄️ FREEZE FRAME DATA (FF):",
                                color = MeetColors.cyberCyan,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "> ${if(isSpanish) pidNameEs else pidNameEn}",
                                    color = MeetColors.textSecondary,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = valStr,
                                    color = Color.White,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EliteButton(
                    text = if(isSpanish) "🤖 IA" else "🤖 AI",
                    onClick = { navController.navigate("ai/$dtc") },
                    color = MeetColors.neonGreen,
                    textColor = MeetColors.backgroundDeep,
                    modifier = Modifier.weight(1f)
                )
                EliteOutlinedButton(
                    text = "❄️ FF DATA",
                    onClick = { coroutineScope.launch { viewModel.refreshFreezeFrame(dtc) } },
                    color = MeetColors.electricBlue,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EmptyDtcState(message: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            // Glowing Concentric Radar target
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .drawBehind {
                        drawCircle(
                            color = color.copy(alpha = 0.08f),
                            radius = size.minDimension / 2f
                        )
                        drawCircle(
                            color = color.copy(alpha = 0.15f),
                            radius = size.minDimension / 3f,
                            style = Stroke(width = 1.dp.toPx())
                        )
                        drawCircle(
                            color = color.copy(alpha = 0.35f),
                            radius = size.minDimension / 4f,
                            style = Stroke(width = 1.dp.toPx())
                        )
                        drawLine(
                            color = color.copy(alpha = 0.25f),
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = color.copy(alpha = 0.25f),
                            start = Offset(size.width / 2f, 0f),
                            end = Offset(size.width / 2f, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Pulser
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 1.3f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1400, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1400, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
                        .background(color, CircleShape)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "STATUS SECURE",
                color = color,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = MeetColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ReadinessMonitorsCard(
    readiness: com.elysium369.meet.core.obd.ReadinessResult?,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    viewModel: ObdViewModel,
    isSpanish: Boolean
) {
    if (readiness == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .drawBehind {
                            drawCircle(
                                color = MeetColors.neonGreen.copy(alpha = 0.08f),
                                radius = size.minDimension / 2f
                            )
                            drawCircle(
                                color = MeetColors.neonGreen.copy(alpha = 0.15f),
                                radius = size.minDimension / 3f,
                                style = Stroke(width = 1.dp.toPx())
                            )
                            drawCircle(
                                color = MeetColors.neonGreen.copy(alpha = 0.35f),
                                radius = size.minDimension / 4f,
                                style = Stroke(width = 1.dp.toPx())
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("📊", fontSize = 42.sp)
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = if(isSpanish) "Monitores de emisiones no leídos aún." else "Emission monitors not read yet.",
                    color = MeetColors.textSecondary,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(24.dp))
                EliteButton(
                    text = if(isSpanish) "LEER MONITORES" else "READ MONITORS",
                    onClick = { coroutineScope.launch { viewModel.refreshDiagnostics() } },
                    color = MeetColors.neonGreen,
                    textColor = MeetColors.backgroundDeep,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else {
        // MIL Status Panel
        val milColor = if (readiness.milOn) MeetColors.error else MeetColors.neonGreen
        EliteCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = milColor
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if(isSpanish) "LUZ MIL (CHECK ENGINE)" else "MIL (CHECK ENGINE LAMP)",
                        color = MeetColors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val statusText = if (readiness.milOn) {
                        if(isSpanish) "🚨 ENCENDIDA / SECURE ERROR" else "🚨 ACTIVATED"
                    } else {
                        if(isSpanish) "🟢 APAGADA / SECURE" else "🟢 DEACTIVATED"
                    }
                    Text(
                        text = statusText,
                        color = milColor,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MeetColors.cardBackgroundLighter)
                        .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${readiness.dtcCount} DTCs",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Circular Readiness Progress ring
        val passedCount = readiness.monitors.count { it.complete }
        val totalCount = readiness.monitors.size
        val ratio = if (totalCount > 0) passedCount.toFloat() / totalCount else 0f
        val progressColor = if (passedCount == totalCount) MeetColors.neonGreen else MeetColors.warning
        
        Surface(
            color = MeetColors.cardBackground.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(12.dp))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(90.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(
                            color = progressColor.copy(alpha = 0.1f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 6.dp.toPx())
                        )
                        drawArc(
                            color = progressColor,
                            startAngle = -90f,
                            sweepAngle = ratio * 360f,
                            useCenter = false,
                            style = Stroke(width = 6.dp.toPx())
                        )
                        
                        // Draw radial high-tech marker ticks
                        val tickCount = 28
                        val radius = size.minDimension / 2f
                        for (i in 0 until tickCount) {
                            val angleDegrees = -90f + (i * 360f / tickCount)
                            val angleRad = Math.toRadians(angleDegrees.toDouble()).toFloat()
                            val innerR = radius - 12.dp.toPx()
                            val outerR = radius - 6.dp.toPx()
                            val startPoint = Offset(
                                center.x + innerR * kotlin.math.cos(angleRad),
                                center.y + innerR * kotlin.math.sin(angleRad)
                            )
                            val endPoint = Offset(
                                center.x + outerR * kotlin.math.cos(angleRad),
                                center.y + outerR * kotlin.math.sin(angleRad)
                            )
                            
                            val isPassed = (i * 360f / tickCount) <= (ratio * 360f)
                            val tickColor = if (isPassed) progressColor else MeetColors.borderSubtle.copy(alpha = 0.3f)
                            
                            drawLine(
                                color = tickColor,
                                start = startPoint,
                                end = endPoint,
                                strokeWidth = 1.5.dp.toPx()
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(ratio * 100).toInt()}%",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if(isSpanish) "LISTO" else "READY",
                            color = progressColor,
                            fontWeight = FontWeight.Black,
                            fontSize = 8.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(20.dp))
                Column {
                    Text(
                        text = if(isSpanish) "MONITORES DE EMISIÓN" else "EMISSION MONITORS",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$passedCount / $totalCount " + (if(isSpanish) "completados" else "completed"),
                        color = progressColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        
        readiness.monitors.forEachIndexed { index, monitor ->
            var expanded by remember { mutableStateOf(false) }
            val guide = if (!monitor.complete) getDriveCycleGuide(monitor.name) else null
            val hasGuide = guide != null
            val itemBorder = if (monitor.complete) MeetColors.neonGreen else MeetColors.warning
            
            val localizedName = if (isSpanish) {
                val n = monitor.name.uppercase()
                when {
                    "CATALYST" in n || "CATALIZADOR" in n -> "Monitor de Catalizador"
                    "EVAP" in n -> "Monitor EVAP (Emisiones)"
                    "O2 SENSOR HEATER" in n -> "Calentador del Sensor O2"
                    "O2 SENSOR" in n -> "Sensor de Oxígeno"
                    "EGR" in n -> "Sistema EGR / VVT"
                    "MISFIRE" in n -> "Fallas de Encendido"
                    "FUEL SYSTEM" in n -> "Sistema de Combustible"
                    "COMPREHENSIVE COMPONENT" in n -> "Componentes Globales (CCM)"
                    "A/C SYSTEM" in n -> "Aire Acondicionado"
                    "SECONDARY AIR SYSTEM" in n -> "Aire Secundario"
                    "HEATED CATALYST" in n -> "Catalizador Calentado"
                    else -> monitor.name
                }
            } else {
                monitor.name
            }

            AnimatedEntrance(index = index) {
                EliteCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    borderColor = itemBorder,
                    onClick = if (hasGuide) { { expanded = !expanded } } else null
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = getMonitorIcon(monitor.name),
                                    fontSize = 18.sp,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = localizedName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            val badgeText = if (monitor.complete) {
                                if(isSpanish) "✅ LISTO" else "✅ READY"
                            } else {
                                if(isSpanish) "⏳ INC." else "⏳ INC."
                            }
                            Text(
                                text = badgeText,
                                color = if (monitor.complete) MeetColors.neonGreen else MeetColors.warning,
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp
                            )
                        }
                        
                        if (expanded) {
                            guide?.let { activeGuide ->
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = MeetColors.borderSubtle)
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                Text(
                                    text = if(isSpanish) "📋 ¿QUÉ ES ESTE MONITOR?" else "📋 ABOUT THIS MONITOR",
                                    color = MeetColors.warning,
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = activeGuide.first,
                                    color = MeetColors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if(isSpanish) "🚗 CICLO DE MANEJO REQUERIDO:" else "🚗 REQUIRED DRIVE CYCLE:",
                                    color = MeetColors.neonGreen,
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    activeGuide.second.forEachIndexed { stepIdx, step ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(MeetColors.neonGreen.copy(alpha = 0.12f))
                                                    .border(1.dp, MeetColors.neonGreen, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${stepIdx + 1}",
                                                    color = MeetColors.neonGreen,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 8.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = step,
                                                color = Color.White.copy(alpha = 0.9f),
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        if (!monitor.complete && hasGuide && !expanded) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if(isSpanish) "▼ TOCA PARA VER CICLO DE MANEJO" else "▼ TAP TO VIEW DRIVE CYCLE GUIDE",
                                color = MeetColors.warning.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getMonitorIcon(name: String): String {
    val n = name.uppercase()
    return when {
        "CATALYST" in n || "CATALIZADOR" in n || "CAT" in n -> "🧪"
        "EVAP" in n -> "💨"
        "O2" in n || "OXYGEN" in n || "OXÍGENO" in n -> "⚡"
        "EGR" in n -> "⚙️"
        "MISFIRE" in n || "ENCENDIDO" in n || "FALLO" in n -> "🔥"
        "FUEL" in n || "COMBUSTIBLE" in n -> "⛽"
        "HEATED" in n || "CALENTADOR" in n || "HTR" in n -> "🔌"
        "A/C" in n || "REFRIG" in n || "AC" in n -> "❄️"
        "SECONDARY" in n || "AIR" in n || "SECUNDARIO" in n -> "🌀"
        else -> "📊"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualSearchTab(navController: NavController, viewModel: ObdViewModel, isSpanish: Boolean) {
    var searchQuery by remember { mutableStateOf("") }
    val searchResults by viewModel.manualSearchResults.collectAsState()

    // Debounce Live Search
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            kotlinx.coroutines.delay(300)
            viewModel.searchDtcManual(searchQuery)
        } else if (searchQuery.isEmpty()) {
            viewModel.searchDtcManual("")
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it.uppercase().trim() },
            label = { Text(if(isSpanish) "Ingresar Código (Ej. P0300)" else "Enter Code (e.g. P0300)", color = MeetColors.textSecondary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Text("✕", color = Color.White)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MeetColors.neonGreen,
                unfocusedBorderColor = MeetColors.borderSubtle,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = MeetColors.neonGreen,
                focusedLabelColor = MeetColors.neonGreen,
                unfocusedLabelColor = MeetColors.textSecondary,
                focusedContainerColor = MeetColors.cardBackground.copy(alpha = 0.5f),
                unfocusedContainerColor = MeetColors.cardBackground.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // OBD Quick Access Keypad
        Text(
            text = if(isSpanish) "TECLADO DE ACCESO RÁPIDO OBD" else "OBD QUICK ACCESS KEYPAD",
            color = MeetColors.textSecondary,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        val obdKeys = listOf("P", "B", "C", "U")
        val numKeys = listOf("0", "1", "2")
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            obdKeys.forEach { key ->
                val keyColor = when(key) {
                    "P" -> MeetColors.error
                    "B" -> MeetColors.electricBlue
                    "C" -> MeetColors.cyberCyan
                    "U" -> MeetColors.warning
                    else -> MeetColors.neonGreen
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MeetColors.cardBackground)
                        .border(1.dp, keyColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable {
                            if (searchQuery.length < 5) {
                                searchQuery = if (searchQuery.isEmpty()) key else key + searchQuery.drop(1)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = key, color = keyColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            numKeys.forEach { key ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MeetColors.cardBackground)
                        .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable {
                            if (searchQuery.length < 5) {
                                if (searchQuery.isNotEmpty()) {
                                    searchQuery = if (searchQuery.length == 1) searchQuery + key else searchQuery.take(1) + key + searchQuery.drop(2)
                                } else {
                                    searchQuery = "P" + key
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = key, color = MeetColors.neonGreen, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                }
            }
            
            // Backspace key
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MeetColors.cardBackground)
                    .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                    .clickable {
                        if (searchQuery.isNotEmpty()) {
                            searchQuery = searchQuery.dropLast(1)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "⌫", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Popular Codes Chips (Only show if search query is empty)
        if (searchQuery.isEmpty()) {
            Text(
                text = if(isSpanish) "CÓDIGOS DTC COMUNES" else "POPULAR DTC CODES",
                color = MeetColors.textSecondary,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            val popularCodes = listOf(
                Pair("P0300", if(isSpanish) "Fallo Encendido" else "Misfire"),
                Pair("P0420", if(isSpanish) "Catalizador" else "Catalyst"),
                Pair("P0171", if(isSpanish) "Mezcla Pobre" else "System Lean"),
                Pair("P0442", if(isSpanish) "Fuga EVAP" else "EVAP Leak"),
                Pair("P0113", if(isSpanish) "Sensor IAT" else "IAT Sensor"),
                Pair("P0505", if(isSpanish) "Control Ralentí" else "Idle Control")
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0 until 2) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        for (col in 0 until 3) {
                            val index = row * 3 + col
                            if (index < popularCodes.size) {
                                val item = popularCodes[index]
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MeetColors.cardBackground)
                                        .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                        .clickable {
                                            searchQuery = item.first
                                        }
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = item.first,
                                            color = MeetColors.cyberCyan,
                                            fontWeight = FontWeight.Black,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = item.second,
                                            color = MeetColors.textSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (searchQuery.isNotEmpty() && searchResults.isEmpty() && searchQuery.length < 3) {
            Spacer(modifier = Modifier.height(16.dp))
            EliteButton(
                text = if(isSpanish) "BUSCAR" else "SEARCH",
                onClick = { viewModel.searchDtcManual(searchQuery) },
                modifier = Modifier.fillMaxWidth(),
                color = MeetColors.neonGreen,
                textColor = MeetColors.backgroundDeep
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (searchResults.isNotEmpty() || searchQuery.isNotEmpty()) {
            if (searchResults.isEmpty() && searchQuery.length >= 3) {
                EmptyDtcState(if(isSpanish) "Código no encontrado en la base de datos local." else "Code not found in local database.", MeetColors.textSecondary)
            } else if (searchResults.isNotEmpty()) {
                Text(
                    text = (if(isSpanish) "RESULTADOS DE BÚSQUEDA (${searchResults.size})" else "SEARCH RESULTS (${searchResults.size})"),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                searchResults.forEachIndexed { index, dtc ->
                    val color = when (dtc.severity.uppercase()) {
                        "HIGH" -> MeetColors.error
                        "MODERATE" -> MeetColors.warning
                        else -> MeetColors.neonGreen
                    }
                    
                    AnimatedEntrance(index = index) {
                        DtcCard(
                            dtc = dtc.code,
                            severity = dtc.severity.uppercase(),
                            color = color,
                            navController = navController,
                            viewModel = viewModel,
                            isSpanish = isSpanish
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

private fun translateDtcText(englishText: String): String {
    var es = englishText
    val dict = mapOf(
        "Catalytic Converter" to "Convertidor catalítico",
        "Oxygen Sensor" to "Sensor de oxígeno",
        "Camshaft Position" to "Posición del árbol de levas",
        "Crankshaft Position" to "Posición del cigüeñal",
        "Mass Air Flow" to "Flujo de masa de aire (MAF)",
        "Manifold Absolute Pressure" to "Presión absoluta del colector (MAP)",
        "Engine Coolant Temperature" to "Temperatura del refrigerante del motor (ECT)",
        "Intake Air Temperature" to "Temperatura del aire de admisión (IAT)",
        "Throttle Position" to "Posición del acelerador (TPS)",
        "Idle Air Control" to "Control de aire de ralentí (IAC)",
        "Exhaust Gas Recirculation" to "Recirculación de gases de escape (EGR)",
        "Evaporative Emission" to "Emisión evaporativa (EVAP)",
        "Fuel Trim" to "Ajuste de combustible",
        "Misfire Detected" to "Fallo de encendido detectado",
        "System Rich" to "Mezcla rica",
        "System Lean" to "Mezcla pobre",
        "Circuit Malfunction" to "Mal funcionamiento del circuito",
        "Circuit Range" to "Rango del circuito",
        "Circuit Low" to "Circuito bajo",
        "Circuit High" to "Circuito alto",
        "Circuit Open" to "Circuito abierto",
        "Circuit Intermittent" to "Circuito intermitente",
        "Purge Valve" to "Válvula de purga",
        "Solenoid Valve" to "Válvula solenoide",
        "Fuel Pump" to "Bomba de combustible",
        
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
    
    val desc = if (isSpanish) translateDtcText(dtc.descriptionEs) else dtc.descriptionEn

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

/**
 * Returns a Pair<Description, List<DriveSteps>> for each readiness monitor.
 * Professional-grade explanations based on SAE J1979 standard and OEM calibration knowledge.
 */
private fun getDriveCycleGuide(monitorName: String): Pair<String, List<String>>? {
    val name = monitorName.uppercase()
    return when {
        "CATALYST" in name || "CATALIZADOR" in name || "CAT" in name -> Pair(
            "El monitor de Catalizador verifica que el convertidor catalítico convierta eficientemente HC, CO y NOx " +
                "en gases inertes (H₂O, CO₂, N₂). La PCM compara la actividad del sensor O2 pre-cat (B1S1) con " +
                "el post-cat (B1S2). Si ambos oscilan igual, el catalizador NO está funcionando.",
            listOf(
                "Arranque en frío (motor debe estar <50°C / 8hrs sin usar).",
                "Deje en ralentí 2 minutos para estabilizar sensores O2.",
                "Conduzca a velocidad constante entre 40-65 km/h por 4-5 minutos (NO acelere bruscamente).",
                "Suelte el acelerador y deje desacelerar en motor (engine brake) hasta 30 km/h — NO pise el freno.",
                "Repita el ciclo de aceleración-desaceleración 2-3 veces.",
                "TOTAL: ~15-20 minutos. Si no completa, el catalizador puede estar degradado."
            )
        )
        "EVAP" in name || "EVAPORAT" in name -> Pair(
            "El monitor EVAP verifica la integridad del sistema de control de emisiones evaporativas — " +
                "el tanque de gasolina, líneas de vapor, cánister de carbón y válvulas de purga/ventilación. " +
                "Busca fugas tan pequeñas como 0.020\" (0.5mm). Es el monitor MÁS difícil de completar.",
            listOf(
                "El tanque debe estar entre 15-85% de capacidad (NO lleno ni casi vacío).",
                "Arranque en frío (motor <50°C, idealmente primera hora de la mañana).",
                "Conduzca a velocidad constante entre 45-65 km/h por 10 minutos.",
                "Estacione y deje en ralentí por 5 minutos (la PCM presuriza el sistema y busca caída de presión).",
                "Apague el motor y NO abra la tapa de gasolina por al menos 8 horas.",
                "⚠️ IMPORTANTE: Si la tapa de gasolina no sella bien, este monitor NUNCA completará."
            )
        )
        "O2" in name || "OXYGEN" in name || "OXÍGENO" in name -> Pair(
            "El monitor de Sensores de Oxígeno verifica que los sensores O2 (lambda) respondan correctamente " +
                "a cambios en la mezcla aire/combustible. La PCM evalúa el tiempo de respuesta, la amplitud de " +
                "la señal (debe oscilar 0.1-0.9V) y la frecuencia de oscilación (cross-counts).",
            listOf(
                "Arranque el motor y deje en ralentí hasta alcanzar temperatura operativa (>75°C).",
                "Acelere de 0 a 90 km/h de forma gradual (aceleración moderada) en 30 segundos.",
                "Mantenga velocidad constante entre 55-100 km/h por 3 minutos.",
                "Suelte el acelerador y deje desacelerar en motor hasta 30 km/h (NO pise freno).",
                "Repita 3 veces. La PCM necesita ver múltiples transiciones rico→pobre→rico.",
                "TOTAL: ~10-15 minutos. Falla si un sensor O2 tarda >1s en cambiar de rico a pobre."
            )
        )
        "EGR" in name -> Pair(
            "El monitor EGR verifica que la válvula de Recirculación de Gases de Escape abra y cierre correctamente. " +
                "La PCM comanda la EGR abierta y observa cambios en MAF, MAP o sensores DPFE para confirmar " +
                "que realmente fluyen gases de escape hacia la admisión.",
            listOf(
                "Motor a temperatura operativa (>75°C).",
                "Conduzca a velocidad constante entre 45-65 km/h durante 1 minuto.",
                "Acelere de 50 a 90 km/h gradualmente — la EGR debe abrir a carga media.",
                "Suelte el acelerador para desacelerar en motor — la EGR debe cerrar.",
                "Mantenga velocidad entre 65-90 km/h por 5 minutes adicionales.",
                "TOTAL: ~8 minutos. Si no completa, los pasajes de EGR pueden estar tapados con carbón."
            )
        )
        "MISFIRE" in name || "ENCENDIDO" in name || "FALLO" in name -> Pair(
            "El monitor de Misfire evalúa continuamente la variación de velocidad del cigüeñal (CKP) " +
                "para detectar cilindros que no contribuyen. Puede identificar el cilindro exacto " +
                "y distinguir entre misfire Tipo A (daña catalizador) y Tipo B (excede umbral de emisiones).",
            listOf(
                "Este monitor es CONTINUO — se ejecuta siempre que el motor está en marcha.",
                "Para resetear: conduzca normalmente por 10 minutos variando entre ralentí, ciudad y carretera.",
                "Incluya aceleraciones desde parado hasta 70 km/h al menos 3 veces.",
                "Si no completa, hay misfire activo — revise bujías, bobinas e inyectores.",
                "Verifique contadores en Mode 06 para identificar el cilindro afectado."
            )
        )
        "FUEL" in name || "COMBUSTIBLE" in name -> Pair(
            "El monitor del Sistema de Combustible verifica que la PCM pueda mantener la mezcla aire/combustible " +
                "en el rango ideal (lambda = 1.0 / 14.7:1 AFR) usando los Fuel Trims como indicador. " +
                "Si los Fuel Trims combinados exceden ±25%, la PCM enciende el MIL.",
            listOf(
                "Motor a temperatura operativa (>75°C).",
                "Conduzca en ciudad con paradas y arranques por 5 minutos.",
                "Luego conduzca a velocidad constante 55-80 km/h por 5 minutos.",
                "La PCM necesita ver condiciones de ralentí, parte de carga y desaceleración.",
                "TOTAL: ~10 minutos. Si no completa, hay un problema significativo de mezcla."
            )
        )
        "HEATED" in name || "CALENTADOR" in name || "HTR" in name -> Pair(
            "El monitor del Calentador del Catalizador (si existe) verifica que el calentador eléctrico " +
                "integrado acelere el calentamiento del catalizador. Esto es común en vehículos con " +
                "catalizadores muy alejados del motor (underfloor cats).",
            listOf(
                "Arranque en frío (motor <50°C).",
                "Deje en ralentí 3 minutos.",
                "Conduzca normalmente 10 minutos.",
                "La PCM verifica que la temperatura del catalizador suba más rápido que sin calentador."
            )
        )
        "A/C" in name || "REFRIG" in name || "AC" in name -> Pair(
            "El monitor del A/C verifica que el sistema de aire acondicionado no afecte negativamente " +
                "las emisiones cuando se activa (la PCM debe compensar la carga extra del compresor).",
            listOf(
                "Motor a temperatura operativa.",
                "Encienda el A/C al máximo por 2 minutos en ralentí.",
                "Conduzca con A/C encendido a 50-80 km/h por 5 minutos.",
                "La PCM verifica que Fuel Trims se mantengan estables con la carga del compresor."
            )
        )
        "SECONDARY" in name || "AIR" in name || "SECUNDARIO" in name -> Pair(
            "El monitor de Aire Secundario verifica que la bomba de aire inyecte aire fresco en el " +
                "colector de escape durante arranques en frío para calentar el catalizador más rápido " +
                "y reducir emisiones HC en los primeros 60 segundos de operación.",
            listOf(
                "Arranque en frío obligatorio (motor <40°C).",
                "Al arrancar, la bomba debe activarse automáticamente por 30-120 segundos.",
                "NO toque el acelerador durante los primeros 2 minutos.",
                "La PCM verifica que los sensores O2 lean 'pobre' cuando la bomba inyecta aire.",
                "Si la bomba no se escucha al arrancar en frío, verifique relé y motor de la bomba."
            )
        )
        else -> Pair(
            "Este monitor verifica un subsistema de emisiones del vehículo. " +
                "Requiere condiciones específicas de manejo para que la PCM pueda ejecutar su prueba interna.",
            listOf(
                "Arranque en frío si es posible (motor <50°C).",
                "Conduzca normalmente por 15-20 minutos incluyendo ralentí, ciudad y carretera.",
                "Incluya al menos 3 ciclos de aceleración y desaceleración en motor (sin freno).",
                "Si persiste incompleto después de 3 ciclos de manejo, puede haber una falla asociada."
            )
        )
    }
}
