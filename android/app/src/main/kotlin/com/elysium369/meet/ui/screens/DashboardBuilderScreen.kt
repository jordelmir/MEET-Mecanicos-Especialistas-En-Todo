package com.elysium369.meet.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.elysium369.meet.core.obd.PidRegistry
import com.elysium369.meet.data.local.entities.DashboardEntity
import com.elysium369.meet.data.local.entities.DashboardWidgetEntity
import com.elysium369.meet.ui.DashboardViewModel
import com.elysium369.meet.ui.components.GaugeWidget
import com.elysium369.meet.ui.components.WaveGraphWidget
import com.elysium369.meet.ui.components.EliteScrollContainer
import com.elysium369.meet.ui.components.eliteScrollbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardBuilderScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit
) {
    val dashboards by viewModel.allDashboards.collectAsState()
    val currentDashboardId by viewModel.currentDashboardId.collectAsState()
    val widgets by viewModel.currentWidgets.collectAsState()
    val customPids by viewModel.customPids.collectAsState()
    val widgetStates by viewModel.widgetStates.collectAsState()
    
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    var showAddWidgetDialog by remember { mutableStateOf(false) }
    var showDashboardList by remember { mutableStateOf(false) }
    var showTemplatesDialog by remember { mutableStateOf(false) }
    var editingWidget by remember { mutableStateOf<DashboardWidgetEntity?>(null) }
    var previewMode by remember { mutableStateOf(false) }
    var isMoveMode by remember { mutableStateOf(false) }
    
    val aiInsight by viewModel.aiInsight.collectAsState()

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF050505))
            ) {
                // ── Main Header ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 12.dp, start = 8.dp, end = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text(
                                "CONFIGURADOR MAESTRO",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showDashboardList = true }
                            ) {
                                Text(
                                    dashboards.find { it.id == currentDashboardId }?.name ?: "SELECCIONAR DASHBOARD",
                                    color = Color(0xFF39FF14),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black
                                )
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color(0xFF39FF14),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                val layout = viewModel.exportCurrentLayout()
                                clipboardManager.setText(AnnotatedString(layout))
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Exportar", tint = Color(0xFF39FF14))
                            }

                            IconButton(onClick = {
                                val clipboardData = clipboardManager.getText()?.text
                                if (clipboardData != null && clipboardData.startsWith("MEET_LAYOUT")) {
                                    viewModel.importLayout(clipboardData)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Importar", tint = Color(0xFF39FF14))
                            }

                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showTemplatesDialog = true
                            }) {
                                Icon(Icons.Default.DashboardCustomize, contentDescription = "Plantillas", tint = Color.Yellow)
                            }

                            Box(modifier = Modifier.width(1.dp).height(24.dp).padding(horizontal = 8.dp).background(Color.DarkGray))

                            Text(
                                "LIVE",
                                color = if (previewMode) Color(0xFF39FF14) else Color.Gray,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Switch(
                                checked = previewMode,
                                onCheckedChange = {
                                    previewMode = it
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF39FF14),
                                    checkedTrackColor = Color(0xFF39FF14).copy(alpha = 0.3f),
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color.DarkGray
                                )
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showAddWidgetDialog = true
                                },
                                color = Color(0xFF39FF14).copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF39FF14).copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF39FF14), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("WIDGET", color = Color(0xFF39FF14), fontWeight = FontWeight.Black, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // ── Neon Separator ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color(0xFF39FF14).copy(alpha = 0.3f), Color.Transparent)
                            )
                        )
                )

                // ── Terminal Status Bar ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF080808))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val statusPulse = rememberInfiniteTransition(label = "statusPulse")
                        val statusAlpha by statusPulse.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
                            label = "statusAlpha"
                        )
                        Box(modifier = Modifier.size(6.dp).background(
                            if (previewMode) Color(0xFF39FF14).copy(alpha = statusAlpha) else Color.Gray,
                            CircleShape
                        ))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (previewMode) "SYSTEM: LIVE DIAGNOSTICS" else "SYSTEM: STANDBY MODE",
                            color = if (previewMode) Color(0xFF39FF14).copy(alpha = 0.7f) else Color.Gray,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            fontSize = 8.sp,
                            letterSpacing = 1.2.sp
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "BUFFER: 1024ms • CORE: v3.1.0 • LOAD: 14%",
                            color = Color.Gray.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "W:${widgets.size} [GRID:DYNAMIC]",
                            color = Color(0xFF39FF14).copy(alpha = 0.4f),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // ── AI INSIGHT TICKER (WORLD-CLASS FEATURE) ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF39FF14).copy(alpha = 0.05f))
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    val tickerOffset = rememberInfiniteTransition().animateFloat(
                        initialValue = 1f,
                        targetValue = -1f,
                        animationSpec = infiniteRepeatable(tween(15000, easing = LinearEasing)),
                        label = "ticker"
                    )
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFF39FF14), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "AI INSIGHT: $aiInsight",
                            color = Color(0xFF39FF14),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            fontSize = 9.sp,
                            maxLines = 1,
                            modifier = Modifier.graphicsLayer(translationX = 0f) // Can be animated if text is too long
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFF0A0E1A)
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            CyberBackground()
            GlobalScreenOverlay()

            if (widgets.isEmpty()) {
                EmptyDashboardPlaceholder(onAdd = { showAddWidgetDialog = true })
            } else {
                val sortedWidgets = widgets.sortedBy { it.gridY }
                DashboardGrid(
                    widgets = sortedWidgets,
                    widgetStates = widgetStates,
                    previewMode = previewMode,
                    isMoveMode = isMoveMode,
                    onDelete = { viewModel.deleteWidget(it) },
                    onEdit = { editingWidget = it },
                    onMoveUp = { w -> val idx = sortedWidgets.indexOf(w); if (idx > 0) viewModel.swapWidgets(w, sortedWidgets[idx - 1]) },
                    onMoveDown = { w -> val idx = sortedWidgets.indexOf(w); if (idx < sortedWidgets.lastIndex) viewModel.swapWidgets(w, sortedWidgets[idx + 1]) },
                    onToggleMoveMode = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isMoveMode = !isMoveMode 
                    }
                )
            }
            
            if (showAddWidgetDialog || editingWidget != null) {
                AddWidgetDialog(
                    customPids = customPids,
                    editingWidget = editingWidget,
                    onAdd = { name, pid, type, min, max, unit, w, h, color ->
                        val currentEditing = editingWidget
                        if (currentEditing != null) {
                            viewModel.updateWidget(currentEditing.copy(
                                name = name, pid = pid, type = type, 
                                minVal = min, maxVal = max, unit = unit,
                                gridW = w, gridH = h, color = color
                            ))
                            editingWidget = null
                        } else {
                            viewModel.addWidget(name, pid, type, min, max, unit, w, h, color)
                        }
                        showAddWidgetDialog = false
                    },
                    onDismiss = { 
                        showAddWidgetDialog = false
                        editingWidget = null
                    }
                )
            }
            if (showDashboardList) {
                DashboardSelectionDialog(
                    dashboards = dashboards,
                    currentId = currentDashboardId,
                    onSelect = { 
                        viewModel.selectDashboard(it)
                        showDashboardList = false 
                    },
                    onCreate = { 
                        viewModel.createDashboard(it)
                        showDashboardList = false
                    },
                    onClone = { id, name -> 
                        viewModel.cloneDashboard(id, name)
                        showDashboardList = false
                    },
                    onDelete = { viewModel.deleteDashboard(it) },
                    onDismiss = { showDashboardList = false }
                )
            }

            if (showTemplatesDialog) {
                TemplateSelectorDialog(
                    onSelect = { 
                        viewModel.applyTemplate(it)
                        showTemplatesDialog = false
                    },
                    onDismiss = { showTemplatesDialog = false }
                )
            }
        }
    }
}

@Composable
fun TemplateSelectorDialog(onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF080808)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp).border(1.dp, Color.Yellow.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("DASHBOARD MASTER TEMPLATES", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                
                val templates = listOf(
                    "PERFORMANCE" to "Optimizado para telemetría de motor y velocidad.",
                    "DIAGNOSTIC" to "Focado en salud de sensores y ondas WAVE.",
                    "ECO" to "Eficiencia de combustible y carga híbrida/EV."
                )
                
                templates.forEach { (title, desc) ->
                    Surface(
                        onClick = { onSelect(title) },
                        color = Color(0xFF060612),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Yellow)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(title, color = Color.White, fontWeight = FontWeight.Black)
                                Text(desc, color = Color.Gray, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyDashboardPlaceholder(onAdd: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "alpha"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Futuristic Scan Circle
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .background(Color(0xFF39FF14).copy(alpha = 0.02f), CircleShape)
                    .border(1.dp, Color(0xFF39FF14).copy(alpha = alpha), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer(scaleX = scale * 0.8f, scaleY = scale * 0.8f)
                    .background(Color(0xFF39FF14).copy(alpha = 0.05f), CircleShape)
                    .border(1.dp, Color(0xFF39FF14).copy(alpha = alpha * 1.5f), CircleShape)
            )
            Icon(
                Icons.Default.Dashboard,
                contentDescription = null,
                tint = Color(0xFF39FF14),
                modifier = Modifier.size(56.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Text(
            "LIENZO VIRGEN DETECTADO",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            "Inicia la construcción de tu terminal de diagnóstico personalizada inyectando PIDs estándar o comandos OEM exclusivos.",
            color = Color.Gray,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onAdd,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF39FF14)),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .height(56.dp)
                .fillMaxWidth(0.8f)
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.width(12.dp))
            Text("DESPLEGAR PRIMER WIDGET", color = Color.Black, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
    }
}

@Composable
fun DashboardGrid(
    widgets: List<DashboardWidgetEntity>,
    widgetStates: Map<String, Float>,
    previewMode: Boolean,
    isMoveMode: Boolean,
    onDelete: (DashboardWidgetEntity) -> Unit,
    onEdit: (DashboardWidgetEntity) -> Unit,
    onMoveUp: (DashboardWidgetEntity) -> Unit,
    onMoveDown: (DashboardWidgetEntity) -> Unit,
    onToggleMoveMode: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val gridState = rememberLazyGridState()
        EliteScrollContainer(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 80.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().eliteScrollbar(gridState)
            ) {
                items(
                    widgets,
                    key = { it.id },
                    span = { widget -> GridItemSpan(widget.gridW.coerceIn(1, 2)) }
                ) { widget ->
                    WidgetCard(
                        widget = widget, 
                        liveValueExt = widgetStates[widget.pid],
                        previewMode = previewMode,
                        isMoveMode = isMoveMode,
                        onDelete = { onDelete(widget) },
                        onEdit = { onEdit(widget) },
                        onMoveUp = { onMoveUp(widget) },
                        onMoveDown = { onMoveDown(widget) }
                    )
                }
            }
        }

        // Move Mode Toggle FAB
        ExtendedFloatingActionButton(
            onClick = onToggleMoveMode,
            containerColor = if (isMoveMode) Color.Yellow else Color(0xFF39FF14),
            contentColor = Color.Black,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                if (isMoveMode) Icons.Default.Check else Icons.Default.OpenWith,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isMoveMode) "FINALIZAR" else "REORDENAR", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun WidgetCard(
    widget: DashboardWidgetEntity,
    liveValueExt: Float? = null,
    previewMode: Boolean,
    isMoveMode: Boolean,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val widgetColor = try { Color(android.graphics.Color.parseColor(widget.color)) } catch(e: Exception) { Color(0xFF39FF14) }
    var simValue by remember { mutableFloatStateOf((widget.minVal + widget.maxVal) / 2f) }
    
    // Live Value Arbitration: External Live > Preview Simulation > Default
    val liveValue = if (liveValueExt != null && !previewMode) {
        liveValueExt
    } else if (previewMode) {
        simValue
    } else {
        (widget.minVal + widget.maxVal) / 2f
    }
    
    // Simulation logic (only active in preview mode)
    if (previewMode) {
        LaunchedEffect(widget.pid) {
            val startTime = System.currentTimeMillis()
            while(true) {
                kotlinx.coroutines.delay(50)
                val elapsed = (System.currentTimeMillis() - startTime) / 1000f
                
                val value = when(widget.pid) {
                    "010C" -> 700f + (Math.sin(elapsed.toDouble() * 2).toFloat() * 100f) + (Math.random().toFloat() * 20f) // RPM
                    "0105" -> 90f + (Math.random().toFloat() * 2f) // Temp
                    "010D" -> 40f + (Math.sin(elapsed.toDouble()).toFloat() * 30f) // Speed
                    else -> {
                        val variance = (widget.maxVal - widget.minVal) * 0.05f
                        simValue + (Math.random().toFloat() - 0.5f) * variance
                    }
                }
                simValue = value.coerceIn(widget.minVal, widget.maxVal)
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "glow"
    )

    val anomalyActive = liveValue > widget.maxVal * 0.9f
    
    // Anomaly Glow Animation
    val anomalyInfinite = rememberInfiniteTransition(label = "anomaly")
    val anomalyAlpha by anomalyInfinite.animateFloat(
        initialValue = 0f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(300), RepeatMode.Reverse),
        label = "anomalyAlpha"
    )

    // CRT Jitter / Glitch
    val glitchTransition = rememberInfiniteTransition(label = "glitch")
    val jitterX by glitchTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 100
                0f at 0
                2f at 20
                -2f at 40
                0f at 60
                1f at 80
                0f at 100
            },
            repeatMode = RepeatMode.Reverse
        ),
        label = "jitter"
    )

    val glitchAlpha by glitchTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(50), RepeatMode.Reverse),
        label = "glitchAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (widget.gridH > 1) 452.dp else 220.dp)
            .graphicsLayer(
                translationX = if (anomalyActive) jitterX else 0f,
                alpha = if (anomalyActive) glitchAlpha else 1f
            )
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0A0E1A))
            .border(
                1.dp, 
                if (isMoveMode) Color.Yellow.copy(alpha = glowAlpha) 
                else if (anomalyActive) Color.Red.copy(alpha = anomalyAlpha)
                else widgetColor.copy(alpha = 0.15f), 
                RoundedCornerShape(16.dp)
            )
            .clickable { 
                if (!isMoveMode) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEdit() 
                }
            }
    ) {
        // Futuristic Scan-lines
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scanLineHeight = 2.dp.toPx()
            val spacing = 4.dp.toPx()
            for (y in 0..size.height.toInt() step (scanLineHeight + spacing).toInt()) {
                drawRect(
                    color = Color.White.copy(alpha = 0.01f),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, y.toFloat()),
                    size = androidx.compose.ui.geometry.Size(size.width, scanLineHeight)
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        widget.name.uppercase(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(4.dp).background(if (anomalyActive) Color.Red else widgetColor, CircleShape))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "ID: ${widget.pid} • ${widget.unit}${if (anomalyActive) " • [CRITICAL]" else ""}",
                            color = if (anomalyActive) Color.Red.copy(alpha = 0.8f) else widgetColor.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                if (isMoveMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onMoveUp()
                        }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color.Yellow)
                        }
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onMoveDown()
                        }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color.Yellow)
                        }
                    }
                } else {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDelete()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Eliminar",
                            tint = Color.Red.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (widget.type) {
                    "WAVE" -> {
                        WaveGraphWidget(
                            label = "",
                            currentValue = liveValue,
                            minVal = widget.minVal,
                            maxVal = widget.maxVal,
                            unit = widget.unit,
                            isAnomaly = anomalyActive
                        )
                    }
                    "DIGITAL" -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // CRT-style flicker on anomaly
                            val digitAlpha = if (anomalyActive) {
                                val flicker = rememberInfiniteTransition(label = "digitFlicker")
                                val a by flicker.animateFloat(
                                    initialValue = 0.7f, targetValue = 1f,
                                    animationSpec = infiniteRepeatable(tween(80), RepeatMode.Reverse),
                                    label = "digitAlpha"
                                )
                                a
                            } else 1f

                            Text(
                                String.format("%.1f", liveValue),
                                color = (if (anomalyActive) Color.Red else widgetColor).copy(alpha = digitAlpha),
                                fontSize = if (widget.gridH > 1) 72.sp else 42.sp,
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.headlineLarge
                            )
                            Text(
                                widget.unit.uppercase(),
                                color = Color.Gray,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 3.sp
                            )
                            // Percentage readout
                            val range = (widget.maxVal - widget.minVal).let { if (it == 0f) 1f else it }
                            val pct = ((liveValue - widget.minVal) / range * 100).coerceIn(0f, 100f)
                            Text(
                                "${String.format("%.0f", pct)}% OF RANGE",
                                color = Color.Gray.copy(alpha = 0.4f),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    else -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            GaugeWidget(
                                label = "",
                                value = liveValue,
                                minVal = widget.minVal,
                                maxVal = widget.maxVal,
                                unit = widget.unit,
                                isAnomaly = anomalyActive,
                                modifier = Modifier.size(if (widget.gridH > 1) 220.dp else 140.dp)
                            )
                            // Min/Max Bracket
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "MIN ${String.format("%.0f", widget.minVal)}",
                                    color = Color.Gray.copy(alpha = 0.3f),
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    "MAX ${String.format("%.0f", widget.maxVal)}",
                                    color = Color.Gray.copy(alpha = 0.3f),
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }

            // ── Pro Telemetry Bar (Bottom) ──
            Column(modifier = Modifier.fillMaxWidth()) {
                val progressRange = (widget.maxVal - widget.minVal).let { if (it == 0f) 1f else it }
                val progress = ((liveValue - widget.minVal) / progressRange).coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        widgetColor.copy(alpha = 0.3f),
                                        if (anomalyActive) Color.Red else widgetColor,
                                        if (anomalyActive) Color.White else widgetColor
                                    )
                                )
                            )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (anomalyActive) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red, modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "ANOMALÍA CRÍTICA",
                                color = Color.Red,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                    } else {
                        Text(
                            "STATUS: NOMINAL",
                            color = widgetColor.copy(alpha = 0.4f),
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Text(
                        "VAL: ${String.format("%.2f", liveValue)} ${widget.unit}",
                        color = Color.Gray.copy(alpha = 0.5f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWidgetDialog(
    customPids: List<com.elysium369.meet.data.local.entities.CustomPidEntity>,
    editingWidget: DashboardWidgetEntity? = null,
    onAdd: (String, String, String, Float, Float, String, Int, Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(editingWidget?.name ?: "") }
    var selectedPid by remember { mutableStateOf<String?>(editingWidget?.pid) }
    var type by remember { mutableStateOf(editingWidget?.type ?: "GAUGE") }
    var minVal by remember { mutableStateOf(editingWidget?.minVal?.toInt()?.toString() ?: "0") }
    var maxVal by remember { mutableStateOf(editingWidget?.maxVal?.toInt()?.toString() ?: "100") }
    var unit by remember { mutableStateOf(editingWidget?.unit ?: "") }
    var gridW by remember { mutableIntStateOf(editingWidget?.gridW ?: 2) }
    var gridH by remember { mutableIntStateOf(editingWidget?.gridH ?: 1) }
    var selectedColor by remember { mutableStateOf(editingWidget?.color ?: "#00FFCC") }

    val colors = listOf("#00FFCC", "#FF00FF", "#0088FF", "#FF8800", "#FF0000", "#AAFF00")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF050505)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .border(
                    1.dp, 
                    Brush.verticalGradient(listOf(Color(selectedColor.toColor()).copy(alpha = 0.5f), Color.Transparent)), 
                    RoundedCornerShape(24.dp)
                )
        ) {
            Column(modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Text(
                    if (editingWidget == null) "INYECTAR NUEVO MÓDULO" else "RECONFIGURAR MÓDULO", 
                    color = Color.White, 
                    fontWeight = FontWeight.Black, 
                    style = MaterialTheme.typography.titleMedium,
                    letterSpacing = 1.sp
                )
                
                Spacer(modifier = Modifier.height(20.dp))

                // ── REAL-TIME PREVIEW ──
                Text("VISTA PREVIA EN VIVO", color = Color(selectedColor.toColor()).copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.3f))
                        .border(1.dp, Color(selectedColor.toColor()).copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val previewValue by rememberInfiniteTransition().animateFloat(
                        initialValue = minVal.toFloatOrNull() ?: 0f,
                        targetValue = maxVal.toFloatOrNull() ?: 100f,
                        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse)
                    )
                    
                    when (type) {
                        "WAVE" -> WaveGraphWidget(label = "", currentValue = previewValue, minVal = minVal.toFloatOrNull() ?: 0f, maxVal = maxVal.toFloatOrNull() ?: 100f, unit = unit, isAnomaly = previewValue > (maxVal.toFloatOrNull() ?: 100f) * 0.9f)
                        "DIGITAL" -> Text(String.format("%.1f %s", previewValue, unit), color = Color(selectedColor.toColor()), fontSize = 32.sp, fontWeight = FontWeight.Black)
                        else -> GaugeWidget(label = "", value = previewValue, minVal = minVal.toFloatOrNull() ?: 0f, maxVal = maxVal.toFloatOrNull() ?: 100f, unit = unit, isAnomaly = previewValue > (maxVal.toFloatOrNull() ?: 100f) * 0.9f, modifier = Modifier.size(140.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // PID Selection
                var expanded by remember { mutableStateOf(false) }
                Box {
                    Column {
                        Text("SENSOR FUENTE", color = Color(selectedColor.toColor()), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF060612),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.DarkGray)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (selectedPid == null) "Seleccionar Sensor..." 
                                           else (PidRegistry.STANDARD_PIDS.find { "${it.mode}${it.pid}" == selectedPid }?.name 
                                                 ?: customPids.find { it.id == selectedPid }?.name ?: "Desconocido"),
                                    color = if (selectedPid == null) Color.Gray else Color.White,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(selectedColor.toColor()))
                            }
                        }
                    }
                    
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(Color(0xFF0A0E1A)).fillMaxWidth(0.7f).heightIn(max = 300.dp)
                    ) {
                        PidRegistry.STANDARD_PIDS.forEach { pid ->
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(8.dp).background(if(pid.isPremium) Color.Yellow else Color.Gray, CircleShape))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(pid.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("${pid.mode}${pid.pid} • ${pid.unit}", color = Color.Gray, fontSize = 9.sp)
                                        }
                                    }
                                },
                                onClick = {
                                    selectedPid = "${pid.mode}${pid.pid}"
                                    name = pid.name
                                    unit = pid.unit
                                    minVal = pid.minValue.toInt().toString()
                                    maxVal = pid.maxValue.toInt().toString()
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("ETIQUETA", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(selectedColor.toColor()),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                Text("ESTILO DE VISUALIZACIÓN", color = Color(selectedColor.toColor()), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetTypeButton(label = "GAUGE", selected = type == "GAUGE", icon = Icons.Default.Speed, color = Color(selectedColor.toColor()), onClick = { type = "GAUGE" }, modifier = Modifier.weight(1f))
                    WidgetTypeButton(label = "WAVE", selected = type == "WAVE", icon = Icons.Default.Timeline, color = Color(selectedColor.toColor()), onClick = { type = "WAVE" }, modifier = Modifier.weight(1f))
                    WidgetTypeButton(label = "DIGITAL", selected = type == "DIGITAL", icon = Icons.Default.Numbers, color = Color(selectedColor.toColor()), onClick = { type = "DIGITAL" }, modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("TAMAÑO DE CELDA", color = Color(selectedColor.toColor()), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SizeButton(label = "1x1", selected = gridW == 1 && gridH == 1, onClick = { gridW = 1; gridH = 1 }, modifier = Modifier.weight(1f))
                    SizeButton(label = "2x1", selected = gridW == 2 && gridH == 1, onClick = { gridW = 2; gridH = 1 }, modifier = Modifier.weight(1f))
                    SizeButton(label = "2x2", selected = gridW == 2 && gridH == 2, onClick = { gridW = 2; gridH = 2 }, modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("ACENTO NEÓN", color = Color(selectedColor.toColor()), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(hex.toColor()))
                                .border(2.dp, if (selectedColor == hex) Color.White else Color.Transparent, CircleShape)
                                .clickable { selectedColor = hex }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        val pid = selectedPid
                        if (pid != null && name.isNotEmpty()) {
                            onAdd(name, pid, type, minVal.toFloatOrNull() ?: 0f, maxVal.toFloatOrNull() ?: 100f, unit, gridW, gridH, selectedColor)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(selectedColor.toColor())),
                    shape = RoundedCornerShape(8.dp),
                    enabled = selectedPid != null && name.isNotEmpty()
                ) {
                    Text(if (editingWidget == null) "INYECTAR" else "ACTUALIZAR", color = Color.Black, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

// Extension to safely parse color
fun String.toColor(): Int = android.graphics.Color.parseColor(this)

@Composable
fun SizeButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color.White.copy(alpha = 0.1f) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Color.White else Color.DarkGray)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) Color.White else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
fun WidgetTypeButton(
    label: String,
    selected: Boolean,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) color.copy(alpha = 0.1f) else Color.Black,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) color else Color.DarkGray)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) color else Color.Gray, modifier = Modifier.size(16.dp))
            Text(label, color = if (selected) color else Color.Gray, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardSelectionDialog(
    dashboards: List<DashboardEntity>,
    currentId: String?,
    onSelect: (String) -> Unit,
    onCreate: (String) -> Unit,
    onClone: (String, String) -> Unit,
    onDelete: (DashboardEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var newDashboardName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF080808)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(1.dp, Color(0xFF39FF14).copy(alpha = 0.2f), RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(4.dp, 20.dp).background(Color(0xFF39FF14)))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "CENTRAL DE DASHBOARDS", 
                        color = Color.White, 
                        fontWeight = FontWeight.Black, 
                        style = MaterialTheme.typography.titleMedium,
                        letterSpacing = 1.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                LazyColumn(
                    modifier = Modifier.heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(dashboards) { db ->
                        val isSelected = db.id == currentId
                        Surface(
                            onClick = { onSelect(db.id) },
                            color = if (isSelected) Color(0xFF39FF14).copy(alpha = 0.1f) else Color(0xFF060612),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, 
                                if (isSelected) Color(0xFF39FF14) else Color.Transparent
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (db.isDefault) Icons.Default.Lock else Icons.Default.Dashboard,
                                        contentDescription = null,
                                        tint = if (isSelected) Color(0xFF39FF14) else Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        db.name.uppercase(),
                                        color = if (isSelected) Color(0xFF39FF14) else Color.White,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                
                                IconButton(onClick = { onClone(db.id, "${db.name} (CLON)") }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Clonar", tint = Color(0xFF39FF14).copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                                }
                                if (!db.isDefault) {
                                    IconButton(onClick = { onDelete(db) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.Red.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (isCreating) {
                    OutlinedTextField(
                        value = newDashboardName,
                        onValueChange = { newDashboardName = it },
                        label = { Text("IDENTIFICADOR DEL DASHBOARD", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF39FF14),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { if (newDashboardName.isNotEmpty()) onCreate(newDashboardName) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF39FF14)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("INICIALIZAR", color = Color.Black, fontWeight = FontWeight.Black)
                    }
                } else {
                    OutlinedButton(
                        onClick = { isCreating = true },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF39FF14)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF39FF14), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("NUEVO DASHBOARD", color = Color(0xFF39FF14), fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun CyberBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val gridSize = 40.dp.toPx()
        val color = Color(0xFF39FF14).copy(alpha = 0.03f)
        
        // Main Grid
        for (x in 0..size.width.toInt() step gridSize.toInt()) {
            drawLine(color, start = androidx.compose.ui.geometry.Offset(x.toFloat(), 0f), end = androidx.compose.ui.geometry.Offset(x.toFloat(), size.height), strokeWidth = 1f)
        }
        for (y in 0..size.height.toInt() step gridSize.toInt()) {
            drawLine(color, start = androidx.compose.ui.geometry.Offset(0f, y.toFloat()), end = androidx.compose.ui.geometry.Offset(size.width, y.toFloat()), strokeWidth = 1f)
        }

        // Radial Vignette
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                center = center,
                radius = size.maxDimension / 1.5f
            ),
            size = size
        )
    }
}

@Composable
fun GlobalScreenOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "scanline")
    val scanlineY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "scanlineY"
    )
    val flicker by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(50, easing = LinearEasing), RepeatMode.Reverse),
        label = "flicker"
    )

    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer(alpha = flicker)) {
        // Horizontal scanlines
        val scanLineHeight = 2.dp.toPx()
        val spacing = 4.dp.toPx()
        for (y in 0..size.height.toInt() step (scanLineHeight + spacing).toInt()) {
            drawRect(
                color = Color.White.copy(alpha = 0.005f),
                topLeft = androidx.compose.ui.geometry.Offset(0f, y.toFloat()),
                size = androidx.compose.ui.geometry.Size(size.width, scanLineHeight)
            )
        }

        // Moving scanline
        drawLine(
            color = Color(0xFF39FF14).copy(alpha = 0.05f),
            start = androidx.compose.ui.geometry.Offset(0f, size.height * scanlineY),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height * scanlineY),
            strokeWidth = 2.dp.toPx()
        )
    }
}

