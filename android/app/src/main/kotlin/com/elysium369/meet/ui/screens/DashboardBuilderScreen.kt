package com.elysium369.meet.ui.screens

import com.elysium369.meet.ui.components.AnimatedNeonIcon

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
import com.elysium369.meet.ui.theme.MeetColors
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
import com.elysium369.meet.ui.components.gauges.StyledGauge
import com.elysium369.meet.ui.components.gauges.GaugeStyleManager
import com.elysium369.meet.ui.components.gauges.GaugeStyleSet
import com.elysium369.meet.data.local.entities.SavedGaugeEntity
import com.elysium369.meet.ui.components.WaveGraphWidget
import com.elysium369.meet.ui.components.EliteScrollContainer
import com.elysium369.meet.ui.components.eliteScrollbar
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory

data class DiyBgConfig(
    val bgType: Int,
    val bgPreset: Int,
    val bgImageUri: String,
    val accentColor: Color,
    val accentColor2: Color,
    val glowIntensity: Float,
    val imageOpacity: Float
)

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
    val savedGauges by viewModel.savedGauges.collectAsState()
    val widgetStates by viewModel.widgetStates.collectAsState()

    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    var showAddWidgetDialog by remember { mutableStateOf(false) }
    var showDashboardList by remember { mutableStateOf(false) }
    var showTemplatesDialog by remember { mutableStateOf(false) }
    var editingWidget by remember { mutableStateOf<DashboardWidgetEntity?>(null) }
    var previewMode by remember { mutableStateOf(false) }
    var isMoveMode by remember { mutableStateOf(false) }

    val aiInsight by viewModel.aiInsight.collectAsState()

    val context = LocalContext.current
    val styleManager = remember { GaugeStyleManager(context) }
    val diyTrigger = GaugeStyleManager.diyUpdateTrigger

    val bgConfig = remember(widgets, savedGauges, diyTrigger) {
        val targetWidget = widgets.firstOrNull { it.widgetStyle == "CUSTOM_DIY" && it.savedStyleId != null }
        if (targetWidget != null) {
            val savedStyle = savedGauges.find { it.id == targetWidget.savedStyleId }
            if (savedStyle != null) {
                DiyBgConfig(
                    bgType = savedStyle.bgType,
                    bgPreset = savedStyle.bgPresetIndex,
                    bgImageUri = savedStyle.bgImageUri ?: "",
                    accentColor = Color(savedStyle.accentColor),
                    accentColor2 = Color(savedStyle.accentColor2),
                    glowIntensity = savedStyle.glowIntensity,
                    imageOpacity = savedStyle.imageOpacity
                )
            } else {
                DiyBgConfig(
                    bgType = styleManager.getDiyBgType(),
                    bgPreset = styleManager.getDiyBgPresetIndex(),
                    bgImageUri = styleManager.getDiyBgImageUri(),
                    accentColor = Color(styleManager.getDiyAccentColor()),
                    accentColor2 = Color(styleManager.getDiyAccentColor2()),
                    glowIntensity = styleManager.getDiyGlowIntensity(),
                    imageOpacity = styleManager.getDiyImageOpacity()
                )
            }
        } else {
            DiyBgConfig(
                bgType = styleManager.getDiyBgType(),
                bgPreset = styleManager.getDiyBgPresetIndex(),
                bgImageUri = styleManager.getDiyBgImageUri(),
                accentColor = Color(styleManager.getDiyAccentColor()),
                accentColor2 = Color(styleManager.getDiyAccentColor2()),
                glowIntensity = styleManager.getDiyGlowIntensity(),
                imageOpacity = styleManager.getDiyImageOpacity()
            )
        }
    }

    // Shift Flash & RPM Critical Alerts
    var prevRpm by remember { mutableStateOf(0f) }
    val shiftFlashAlpha = remember { Animatable(0f) }

    val rpmWidget = remember(widgets) { widgets.find { it.pid == "RPM" || it.name.uppercase() == "RPM" } }
    val rpmValue = if (rpmWidget != null) widgetStates[rpmWidget.pid] ?: 0f else 0f
    val rpmMax = rpmWidget?.maxVal ?: 8000f
    val isRpmCritical = rpmWidget != null && rpmValue > rpmMax * 0.9f

    LaunchedEffect(rpmValue) {
        if (prevRpm > rpmMax * 0.6f && rpmValue < prevRpm - 1200f) {
            shiftFlashAlpha.snapTo(0.6f)
            shiftFlashAlpha.animateTo(0f, animationSpec = tween(350, easing = LinearEasing))
        }
        prevRpm = rpmValue
    }

    val rpmPulseTransition = rememberInfiniteTransition(label = "rpmPulse")
    val rpmPulseAlpha by if (isRpmCritical) {
        rpmPulseTransition.animateFloat(
            initialValue = 0.1f,
            targetValue = 0.6f,
            animationSpec = infiniteRepeatable(tween(250, easing = LinearEasing), RepeatMode.Reverse),
            label = "pulse"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(com.elysium369.meet.ui.theme.MeetColors.backgroundDeep)
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
                            AnimatedNeonIcon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.White)
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
                                    color = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black
                                )
                                AnimatedNeonIcon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
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
                                AnimatedNeonIcon(Icons.Default.Share, contentDescription = "Exportar", tint = com.elysium369.meet.ui.theme.MeetColors.neonGreen)
                            }

                            IconButton(onClick = {
                                val clipboardData = clipboardManager.getText()?.text
                                if (clipboardData != null && clipboardData.startsWith("ELYSIUM_VANGUARD_LAYOUT")) {
                                    viewModel.importLayout(clipboardData)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }) {
                                AnimatedNeonIcon(Icons.Default.ContentPaste, contentDescription = "Importar", tint = com.elysium369.meet.ui.theme.MeetColors.neonGreen)
                            }

                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showTemplatesDialog = true
                            }) {
                                AnimatedNeonIcon(Icons.Default.DashboardCustomize, contentDescription = "Plantillas", tint = com.elysium369.meet.ui.theme.MeetColors.warning)
                            }

                            Box(modifier = Modifier.width(1.dp).height(24.dp).padding(horizontal = 8.dp).background(MeetColors.borderBlue))

                            Text(
                                "LIVE",
                                color = if (previewMode) com.elysium369.meet.ui.theme.MeetColors.neonGreen else com.elysium369.meet.ui.theme.MeetColors.textSecondary,
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
                                    checkedThumbColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                                    checkedTrackColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.3f),
                                    uncheckedThumbColor = MeetColors.textMuted,
                                    uncheckedTrackColor = MeetColors.borderBlue
                                )
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showAddWidgetDialog = true
                                },
                                color = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AnimatedNeonIcon(Icons.Default.Add, contentDescription = null, tint = com.elysium369.meet.ui.theme.MeetColors.neonGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("WIDGET", color = com.elysium369.meet.ui.theme.MeetColors.neonGreen, fontWeight = FontWeight.Black, fontSize = 12.sp)
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
                                listOf(Color.Transparent, com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.3f), Color.Transparent)
                            )
                        )
                )

                // ── Terminal Status Bar ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(com.elysium369.meet.ui.theme.MeetColors.backgroundDark)
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
                            if (previewMode) com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = statusAlpha) else com.elysium369.meet.ui.theme.MeetColors.textSecondary,
                            CircleShape
                        ))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (previewMode) "SYSTEM: LIVE DIAGNOSTICS" else "SYSTEM: STANDBY MODE",
                            color = if (previewMode) com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.7f) else com.elysium369.meet.ui.theme.MeetColors.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            fontSize = 8.sp,
                            letterSpacing = 1.2.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "BUFFER: 1024ms • CORE: v3.1.0 • LOAD: 14%",
                            color = com.elysium369.meet.ui.theme.MeetColors.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "W:${widgets.size} [GRID:DYNAMIC]",
                            color = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.4f),
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
                        .background(com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.05f))
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    val tickerOffset = rememberInfiniteTransition().animateFloat(
                        initialValue = 1f,
                        targetValue = -1f,
                        animationSpec = infiniteRepeatable(tween(15000, easing = LinearEasing)),
                        label = "ticker"
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedNeonIcon(Icons.Default.Psychology, contentDescription = null, tint = com.elysium369.meet.ui.theme.MeetColors.neonGreen, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "AI INSIGHT: $aiInsight",
                            color = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
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
        containerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            CyberBackground(config = bgConfig)
            GlobalScreenOverlay()

            // RPM critical alert border/vignette overlay
            if (rpmPulseAlpha > 0f) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Transparent, Color.Red.copy(alpha = rpmPulseAlpha)),
                            center = center,
                            radius = size.maxDimension / 1.2f
                        ),
                        size = size
                    )
                }
            }

            // Shift Flash overlay
            if (shiftFlashAlpha.value > 0f) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        color = Color.White.copy(alpha = shiftFlashAlpha.value),
                        size = size
                    )
                }
            }

            if (widgets.isEmpty()) {
                EmptyDashboardPlaceholder(onAdd = { showAddWidgetDialog = true })
            } else {
                val sortedWidgets = widgets.sortedBy { it.gridY }
                DashboardGrid(
                    widgets = sortedWidgets,
                    widgetStates = widgetStates,
                    previewMode = previewMode,
                    isMoveMode = isMoveMode,
                    savedGauges = savedGauges,
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
                    savedGauges = savedGauges,
                    editingWidget = editingWidget,
                    onAdd = { name, pid, type, min, max, unit, w, h, color, wStyle, sStyleId ->
                        val currentEditing = editingWidget
                        if (currentEditing != null) {
                            viewModel.updateWidget(currentEditing.copy(
                                name = name, pid = pid, type = type,
                                minVal = min, maxVal = max, unit = unit,
                                gridW = w, gridH = h, color = color,
                                widgetStyle = wStyle, savedStyleId = sStyleId
                            ))
                            editingWidget = null
                        } else {
                            viewModel.addWidget(name, pid, type, min, max, unit, w, h, color, wStyle, sStyleId)
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
            colors = CardDefaults.cardColors(containerColor = com.elysium369.meet.ui.theme.MeetColors.cardBackground),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp).border(1.dp, com.elysium369.meet.ui.theme.MeetColors.warning.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
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
                        color = com.elysium369.meet.ui.theme.MeetColors.backgroundDeep,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            AnimatedNeonIcon(Icons.Default.AutoAwesome, contentDescription = null, tint = com.elysium369.meet.ui.theme.MeetColors.warning)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(title, color = Color.White, fontWeight = FontWeight.Black)
                                Text(desc, color = com.elysium369.meet.ui.theme.MeetColors.textSecondary, fontSize = 10.sp)
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
                    .background(com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.02f), CircleShape)
                    .border(1.dp, com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = alpha), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer(scaleX = scale * 0.8f, scaleY = scale * 0.8f)
                    .background(com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.05f), CircleShape)
                    .border(1.dp, com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = alpha * 1.5f), CircleShape)
            )
            AnimatedNeonIcon(
                Icons.Default.Dashboard,
                contentDescription = null,
                tint = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
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
            color = com.elysium369.meet.ui.theme.MeetColors.textSecondary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onAdd,
            colors = ButtonDefaults.buttonColors(containerColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .height(56.dp)
                .fillMaxWidth(0.8f)
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
        ) {
            AnimatedNeonIcon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
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
    savedGauges: List<SavedGaugeEntity>,
    onDelete: (DashboardWidgetEntity) -> Unit,
    onEdit: (DashboardWidgetEntity) -> Unit,
    onMoveUp: (DashboardWidgetEntity) -> Unit,
    onMoveDown: (DashboardWidgetEntity) -> Unit,
    onToggleMoveMode: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Responsive columns based on available width
        val cols = when {
            maxWidth < 360.dp  -> 1
            maxWidth < 600.dp  -> 2
            maxWidth < 840.dp  -> 3
            else               -> 4
        }

        val gridPadding = when {
            maxWidth < 360.dp  -> 8.dp
            maxWidth < 600.dp  -> 12.dp
            else               -> 16.dp
        }
        val itemSpacing = when {
            maxWidth < 360.dp  -> 6.dp
            maxWidth < 600.dp  -> 10.dp
            else               -> 14.dp
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val gridState = rememberLazyGridState()
            EliteScrollContainer(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(cols),
                    contentPadding = PaddingValues(gridPadding, gridPadding, gridPadding, 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                    verticalArrangement = Arrangement.spacedBy(itemSpacing),
                    modifier = Modifier.fillMaxSize().eliteScrollbar(gridState)
                ) {
                    items(
                        widgets,
                        key = { it.id },
                        span = { widget -> GridItemSpan(widget.gridW.coerceIn(1, cols)) }
                    ) { widget ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            WidgetCard(
                                widget = widget,
                                liveValueExt = widgetStates[widget.pid],
                                previewMode = previewMode,
                                isMoveMode = isMoveMode,
                                savedGauges = savedGauges,
                                onDelete = { onDelete(widget) },
                                onEdit = { onEdit(widget) },
                                onMoveUp = { onMoveUp(widget) },
                                onMoveDown = { onMoveDown(widget) }
                            )
                        }
                    }
                }
            }

            // Move Mode Toggle FAB
            ExtendedFloatingActionButton(
                onClick = onToggleMoveMode,
                containerColor = if (isMoveMode) com.elysium369.meet.ui.theme.MeetColors.warning else com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                contentColor = Color.Black,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                AnimatedNeonIcon(
                    if (isMoveMode) Icons.Default.Check else Icons.Default.OpenWith,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isMoveMode) "FINALIZAR" else "REORDENAR", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun WidgetCard(
    widget: DashboardWidgetEntity,
    liveValueExt: Float? = null,
    previewMode: Boolean,
    isMoveMode: Boolean,
    savedGauges: List<SavedGaugeEntity>,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val gaugeStyleManager = remember { GaugeStyleManager(context) }
    val currentStyle by gaugeStyleManager.currentStyle.collectAsState()
    val widgetColor = try { Color(android.graphics.Color.parseColor(widget.color)) } catch(e: Exception) { com.elysium369.meet.ui.theme.MeetColors.neonGreen }
    val previewValue = remember(widget.id, widget.minVal, widget.maxVal) {
        (widget.minVal + widget.maxVal) / 2f
    }

    // Live Value Arbitration: External Live > static editor preview > default.
    val liveValue = if (liveValueExt != null && !previewMode) {
        liveValueExt
    } else if (previewMode) {
        previewValue
    } else {
        (widget.minVal + widget.maxVal) / 2f
    }

    // Check for sensor characteristics to apply customized premium spring dynamics
    val isFastSensor = widget.pid == "010D" || widget.pid == "010C" ||
                       widget.unit.equals("km/h", ignoreCase = true) ||
                       widget.unit.equals("rpm", ignoreCase = true) ||
                       widget.unit.contains("hp", ignoreCase = true) ||
                       widget.unit.equals("bar", ignoreCase = true) ||
                       widget.unit.equals("%", ignoreCase = true) ||
                       widget.name.contains("velocidad", ignoreCase = true) ||
                       widget.name.contains("speed", ignoreCase = true) ||
                       widget.name.contains("rpm", ignoreCase = true) ||
                       widget.name.contains("boost", ignoreCase = true) ||
                       widget.name.contains("carga", ignoreCase = true) ||
                       widget.name.contains("load", ignoreCase = true) ||
                       widget.name.contains("acelerador", ignoreCase = true) ||
                       widget.name.contains("throttle", ignoreCase = true)

    val animatedValue by animateFloatAsState(
        targetValue = liveValue,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy, // Clear readability, no overshoot
            stiffness = if (isFastSensor) 35f else 60f // Fluid visual sweep sintonizado
        ),
        label = "widgetAnimation"
    )

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
            .background(com.elysium369.meet.ui.theme.MeetColors.backgroundDark)
            .border(
                1.dp,
                if (isMoveMode) com.elysium369.meet.ui.theme.MeetColors.warning.copy(alpha = glowAlpha)
                else if (anomalyActive) com.elysium369.meet.ui.theme.MeetColors.error.copy(alpha = anomalyAlpha)
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
                        Box(modifier = Modifier.size(4.dp).background(if (anomalyActive) com.elysium369.meet.ui.theme.MeetColors.error else widgetColor, CircleShape))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "ID: ${widget.pid} • ${widget.unit}${if (anomalyActive) " • [CRITICAL]" else ""}",
                            color = if (anomalyActive) com.elysium369.meet.ui.theme.MeetColors.error.copy(alpha = 0.8f) else widgetColor.copy(alpha = 0.6f),
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
                            AnimatedNeonIcon(Icons.Default.ArrowUpward, contentDescription = null, tint = com.elysium369.meet.ui.theme.MeetColors.warning)
                        }
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onMoveDown()
                        }, modifier = Modifier.size(28.dp)) {
                            AnimatedNeonIcon(Icons.Default.ArrowDownward, contentDescription = null, tint = com.elysium369.meet.ui.theme.MeetColors.warning)
                        }
                    }
                } else {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDelete()
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .border(1.dp, com.elysium369.meet.ui.theme.MeetColors.error.copy(alpha = 0.6f), CircleShape)
                    ) {
                        AnimatedNeonIcon(
                            Icons.Default.Delete,
                            contentDescription = "Eliminar",
                            tint = com.elysium369.meet.ui.theme.MeetColors.error,
                            modifier = Modifier.size(14.dp)
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
                            currentValue = animatedValue,
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
                                String.format("%.1f", animatedValue),
                                color = (if (anomalyActive) com.elysium369.meet.ui.theme.MeetColors.error else widgetColor).copy(alpha = digitAlpha),
                                fontSize = if (widget.gridH > 1) 72.sp else 42.sp,
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.headlineLarge
                            )
                            Text(
                                widget.unit.uppercase(),
                                color = com.elysium369.meet.ui.theme.MeetColors.textSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 3.sp
                            )
                            // Percentage readout
                            val range = (widget.maxVal - widget.minVal).let { if (it == 0f) 1f else it }
                            val pct = ((animatedValue - widget.minVal) / range * 100).coerceIn(0f, 100f)
                            Text(
                                "${String.format("%.0f", pct)}% OF RANGE",
                                color = com.elysium369.meet.ui.theme.MeetColors.textMuted,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    else -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val wStyle = remember(widget.widgetStyle, currentStyle) {
                                if (!widget.widgetStyle.isNullOrEmpty()) {
                                    try { GaugeStyleSet.valueOf(widget.widgetStyle) } catch (e: Exception) { currentStyle }
                                } else {
                                    currentStyle
                                }
                            }
                            val diyConfig = remember(widget.savedStyleId, savedGauges) {
                                savedGauges.find { it.id == widget.savedStyleId }
                            }
                            StyledGauge(
                                style = wStyle,
                                label = "",
                                value = animatedValue,
                                minVal = widget.minVal,
                                maxVal = widget.maxVal,
                                unit = widget.unit,
                                isAnomaly = anomalyActive,
                                diyConfig = diyConfig,
                                modifier = Modifier.size(if (widget.gridH > 1) 220.dp else 140.dp)
                            )
                            // Min/Max Bracket
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "MIN ${String.format("%.0f", widget.minVal)}",
                                    color = com.elysium369.meet.ui.theme.MeetColors.textMuted,
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    "MAX ${String.format("%.0f", widget.maxVal)}",
                                    color = com.elysium369.meet.ui.theme.MeetColors.textMuted,
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
                val progress = ((animatedValue - widget.minVal) / progressRange).coerceIn(0f, 1f)

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
                                        if (anomalyActive) com.elysium369.meet.ui.theme.MeetColors.error else widgetColor,
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
                            AnimatedNeonIcon(Icons.Default.Warning, contentDescription = null, tint = com.elysium369.meet.ui.theme.MeetColors.error, modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "ANOMALÍA CRÍTICA",
                                color = com.elysium369.meet.ui.theme.MeetColors.error,
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
                        "VAL: ${String.format("%.2f", animatedValue)} ${widget.unit}",
                        color = com.elysium369.meet.ui.theme.MeetColors.textSecondary,
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
    savedGauges: List<SavedGaugeEntity>,
    editingWidget: DashboardWidgetEntity? = null,
    onAdd: (String, String, String, Float, Float, String, Int, Int, String, String?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val gaugeStyleManager = remember { GaugeStyleManager(context) }
    val currentStyle by gaugeStyleManager.currentStyle.collectAsState()
    var name by remember { mutableStateOf(editingWidget?.name ?: "") }
    var selectedPid by remember { mutableStateOf<String?>(editingWidget?.pid) }
    var type by remember { mutableStateOf(editingWidget?.type ?: "GAUGE") }
    var minVal by remember { mutableStateOf(editingWidget?.minVal?.toInt()?.toString() ?: "0") }
    var maxVal by remember { mutableStateOf(editingWidget?.maxVal?.toInt()?.toString() ?: "100") }
    var unit by remember { mutableStateOf(editingWidget?.unit ?: "") }
    var gridW by remember { mutableIntStateOf(editingWidget?.gridW ?: 2) }
    var gridH by remember { mutableIntStateOf(editingWidget?.gridH ?: 1) }
    var selectedColor by remember { mutableStateOf(editingWidget?.color ?: "#00FFCC") }
    var widgetStyle by remember { mutableStateOf(editingWidget?.widgetStyle) }
    var savedStyleId by remember { mutableStateOf(editingWidget?.savedStyleId) }

    val colors = listOf("#00FFCC", "#FF00FF", "#0088FF", "#FF8800", "#FF0000", "#AAFF00")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDeep),
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
                        else -> {
                            val wStyle = remember(widgetStyle, currentStyle) {
                                if (!widgetStyle.isNullOrEmpty()) {
                                    try { GaugeStyleSet.valueOf(widgetStyle!!) } catch (e: Exception) { currentStyle }
                                } else {
                                    currentStyle
                                }
                            }
                            val diyConfig = remember(savedStyleId, savedGauges) {
                                savedGauges.find { it.id == savedStyleId }
                            }
                            StyledGauge(
                                style = wStyle,
                                label = "",
                                value = previewValue,
                                minVal = minVal.toFloatOrNull() ?: 0f,
                                maxVal = maxVal.toFloatOrNull() ?: 100f,
                                unit = unit,
                                isAnomaly = previewValue > (maxVal.toFloatOrNull() ?: 100f) * 0.9f,
                                diyConfig = diyConfig,
                                modifier = Modifier.size(140.dp)
                            )
                        }
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
                            color = com.elysium369.meet.ui.theme.MeetColors.backgroundDeep,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.borderBlue)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (selectedPid == null) "Seleccionar Sensor..."
                                           else (PidRegistry.STANDARD_PIDS.find { "${it.mode}${it.pid}" == selectedPid }?.name
                                                 ?: customPids.find { it.id == selectedPid }?.name ?: "Desconocido"),
                                    color = if (selectedPid == null) MeetColors.textMuted else Color.White,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                AnimatedNeonIcon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(selectedColor.toColor()))
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(com.elysium369.meet.ui.theme.MeetColors.backgroundDark).fillMaxWidth(0.7f).heightIn(max = 300.dp)
                    ) {
                        PidRegistry.STANDARD_PIDS.forEach { pid ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(8.dp).background(if(pid.isPremium) com.elysium369.meet.ui.theme.MeetColors.warning else com.elysium369.meet.ui.theme.MeetColors.textSecondary, CircleShape))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(pid.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("${pid.mode}${pid.pid} • ${pid.unit}", color = com.elysium369.meet.ui.theme.MeetColors.textSecondary, fontSize = 9.sp)
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

                if (type == "GAUGE") {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("ESTILO DE GAUGE", color = Color(selectedColor.toColor()), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    var styleExpanded by remember { mutableStateOf(false) }
                    val activeStyleText = if (widgetStyle == null) "ESTILO PREESTABLECIDO POR DEFECTO" else widgetStyle
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            onClick = { styleExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            color = com.elysium369.meet.ui.theme.MeetColors.backgroundDeep,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.borderBlue)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = activeStyleText ?: "Por defecto",
                                    color = Color.White,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                AnimatedNeonIcon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(selectedColor.toColor()))
                            }
                        }

                        DropdownMenu(
                            expanded = styleExpanded,
                            onDismissRequest = { styleExpanded = false },
                            modifier = Modifier.background(com.elysium369.meet.ui.theme.MeetColors.backgroundDark).fillMaxWidth(0.7f).heightIn(max = 280.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text("DASHBOARD DEFAULT (GLOBAL)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                onClick = {
                                    widgetStyle = null
                                    styleExpanded = false
                                }
                            )
                            GaugeStyleSet.values().forEach { styleOption ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(styleOption.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            if (styleOption == GaugeStyleSet.CUSTOM_DIY) {
                                                Text("Usa tus diseños personalizados guardados", color = com.elysium369.meet.ui.theme.MeetColors.textSecondary, fontSize = 9.sp)
                                            }
                                        }
                                    },
                                    onClick = {
                                        widgetStyle = styleOption.name
                                        if (styleOption != GaugeStyleSet.CUSTOM_DIY) {
                                            savedStyleId = null
                                        }
                                        styleExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (widgetStyle == GaugeStyleSet.CUSTOM_DIY.name) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("DISEÑO PERSONALIZADO DIY", color = Color(selectedColor.toColor()), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        var diyExpanded by remember { mutableStateOf(false) }
                        val activeDiyText = if (savedStyleId == null) "SELECCIONAR DISEÑO GUARDADO..."
                                            else (savedGauges.find { it.id == savedStyleId }?.name ?: "Diseño Desconocido")
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                onClick = { diyExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                color = com.elysium369.meet.ui.theme.MeetColors.backgroundDeep,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MeetColors.borderBlue)
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = activeDiyText,
                                        color = if (savedStyleId == null) MeetColors.textMuted else Color.White,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    AnimatedNeonIcon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(selectedColor.toColor()))
                                }
                            }

                            DropdownMenu(
                                expanded = diyExpanded,
                                onDismissRequest = { diyExpanded = false },
                                modifier = Modifier.background(com.elysium369.meet.ui.theme.MeetColors.backgroundDark).fillMaxWidth(0.7f).heightIn(max = 200.dp)
                            ) {
                                if (savedGauges.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No tienes diseños DIY guardados", color = MeetColors.textMuted, fontSize = 12.sp) },
                                        onClick = { diyExpanded = false }
                                    )
                                } else {
                                    savedGauges.forEach { savedGauge ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(savedGauge.name.ifEmpty { "Sin nombre" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    Text("Creado: ${savedGauge.createdAt}", color = com.elysium369.meet.ui.theme.MeetColors.textSecondary, fontSize = 9.sp)
                                                }
                                            },
                                            onClick = {
                                                savedStyleId = savedGauge.id
                                                diyExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
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
                            onAdd(
                                name, pid, type,
                                minVal.toFloatOrNull() ?: 0f,
                                maxVal.toFloatOrNull() ?: 100f,
                                unit, gridW, gridH, selectedColor,
                                widgetStyle, savedStyleId
                            )
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
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Color.White else MeetColors.borderBlue)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) Color.White else com.elysium369.meet.ui.theme.MeetColors.textSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) color else MeetColors.borderBlue)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            AnimatedNeonIcon(icon, contentDescription = null, tint = if (selected) color else com.elysium369.meet.ui.theme.MeetColors.textSecondary, modifier = Modifier.size(16.dp))
            Text(label, color = if (selected) color else com.elysium369.meet.ui.theme.MeetColors.textSecondary, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
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
            colors = CardDefaults.cardColors(containerColor = com.elysium369.meet.ui.theme.MeetColors.cardBackground),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(1.dp, com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(4.dp, 20.dp).background(com.elysium369.meet.ui.theme.MeetColors.neonGreen))
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
                            color = if (isSelected) com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.1f) else com.elysium369.meet.ui.theme.MeetColors.backgroundDeep,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) com.elysium369.meet.ui.theme.MeetColors.neonGreen else Color.Transparent
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
                                    AnimatedNeonIcon(
                                        if (db.isDefault) Icons.Default.Lock else Icons.Default.Dashboard,
                                        contentDescription = null,
                                        tint = if (isSelected) com.elysium369.meet.ui.theme.MeetColors.neonGreen else com.elysium369.meet.ui.theme.MeetColors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        db.name.uppercase(),
                                        color = if (isSelected) com.elysium369.meet.ui.theme.MeetColors.neonGreen else Color.White,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                IconButton(onClick = { onClone(db.id, "${db.name} (CLON)") }, modifier = Modifier.size(24.dp)) {
                                    AnimatedNeonIcon(Icons.Default.ContentCopy, contentDescription = "Clonar", tint = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                                }
                                if (!db.isDefault) {
                                    IconButton(onClick = { onDelete(db) }, modifier = Modifier.size(24.dp)) {
                                        AnimatedNeonIcon(Icons.Default.Delete, contentDescription = "Eliminar", tint = com.elysium369.meet.ui.theme.MeetColors.error.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
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
                            focusedBorderColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { if (newDashboardName.isNotEmpty()) onCreate(newDashboardName) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("INICIALIZAR", color = Color.Black, fontWeight = FontWeight.Black)
                    }
                } else {
                    OutlinedButton(
                        onClick = { isCreating = true },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, com.elysium369.meet.ui.theme.MeetColors.neonGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        AnimatedNeonIcon(Icons.Default.Add, contentDescription = null, tint = com.elysium369.meet.ui.theme.MeetColors.neonGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("NUEVO DASHBOARD", color = com.elysium369.meet.ui.theme.MeetColors.neonGreen, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun CyberBackground(config: DiyBgConfig) {
    val context = LocalContext.current
    val bitmap = remember(config.bgImageUri) {
        if (config.bgType == 2 && config.bgImageUri.isNotEmpty()) {
            try {
                if (config.bgImageUri.startsWith("/")) {
                    BitmapFactory.decodeFile(config.bgImageUri)?.asImageBitmap()
                } else {
                    val uri = android.net.Uri.parse(config.bgImageUri)
                    val stream = context.contentResolver.openInputStream(uri)
                    val bmp = BitmapFactory.decodeStream(stream)
                    stream?.close()
                    bmp?.asImageBitmap()
                }
            } catch (e: Exception) {
                null
            }
        } else null
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        when (config.bgType) {
            1 -> { // Presets
                when (config.bgPreset) {
                    0 -> { // Metal cepillado
                        drawRect(
                            brush = Brush.sweepGradient(
                                colors = listOf(Color(0xFF2C3E50), Color(0xFFBDC3C7), Color(0xFF2C3E50), Color(0xFFBDC3C7), Color(0xFF2C3E50)),
                                center = center
                            ),
                            size = size
                        )
                    }
                    1 -> { // Fibra de carbono
                        drawRect(Color(0xFF15181E))
                        val carbonGrid = 16.dp.toPx()
                        for (x in 0..size.width.toInt() step carbonGrid.toInt()) {
                            drawLine(Color.Black.copy(alpha = 0.4f), androidx.compose.ui.geometry.Offset(x.toFloat(), 0f), androidx.compose.ui.geometry.Offset(x.toFloat(), size.height), 1f)
                            drawLine(Color.White.copy(alpha = 0.04f), androidx.compose.ui.geometry.Offset(x.toFloat() + carbonGrid/2, 0f), androidx.compose.ui.geometry.Offset(x.toFloat() + carbonGrid/2, size.height), 0.5f)
                        }
                        for (y in 0..size.height.toInt() step carbonGrid.toInt()) {
                            drawLine(Color.Black.copy(alpha = 0.4f), androidx.compose.ui.geometry.Offset(0f, y.toFloat()), androidx.compose.ui.geometry.Offset(size.width, y.toFloat()), 1f)
                            drawLine(Color.White.copy(alpha = 0.04f), androidx.compose.ui.geometry.Offset(0f, y.toFloat() + carbonGrid/2), androidx.compose.ui.geometry.Offset(size.width, y.toFloat() + carbonGrid/2), 0.5f)
                        }
                    }
                    2 -> { // Rejilla Cyber
                        drawRect(Color(0xFF030A16))
                        val gridSize = 40.dp.toPx()
                        val cyberColor = config.accentColor.copy(alpha = 0.05f)
                        for (x in 0..size.width.toInt() step gridSize.toInt()) {
                            drawLine(cyberColor, androidx.compose.ui.geometry.Offset(x.toFloat(), 0f), androidx.compose.ui.geometry.Offset(x.toFloat(), size.height), 1.5f)
                        }
                        for (y in 0..size.height.toInt() step gridSize.toInt()) {
                            drawLine(cyberColor, androidx.compose.ui.geometry.Offset(0f, y.toFloat()), androidx.compose.ui.geometry.Offset(size.width, y.toFloat()), 1.5f)
                        }
                    }
                    3 -> { // Espacio cósmico
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF1E0D35), Color(0xFF060312)),
                                center = center,
                                radius = size.maxDimension
                            )
                        )
                        val rand = java.util.Random(12345)
                        for (i in 0..24) {
                            val sx = rand.nextFloat() * size.width
                            val sy = rand.nextFloat() * size.height
                            val r = rand.nextFloat() * 1.5f + 1f
                            drawCircle(Color.White.copy(alpha = rand.nextFloat() * 0.7f + 0.3f), r, androidx.compose.ui.geometry.Offset(sx, sy))
                        }
                    }
                    4 -> { // Lava volcánica
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF4A0000), Color(0xFF1A0000), Color(0xFF0A0000)),
                                center = center,
                                radius = size.maxDimension / 1.2f
                            )
                        )
                        val lavaRand = java.util.Random(6789)
                        val crackColor = Color(0xFFFF4500).copy(alpha = 0.25f)
                        for (i in 0..6) {
                            val sx = lavaRand.nextFloat() * size.width
                            val sy = lavaRand.nextFloat() * size.height
                            val ex = lavaRand.nextFloat() * size.width
                            val ey = lavaRand.nextFloat() * size.height
                            drawLine(crackColor, androidx.compose.ui.geometry.Offset(sx, sy), androidx.compose.ui.geometry.Offset(ex, ey), 1.5f)
                        }
                    }
                    5 -> { // Placa de circuito
                        drawRect(Color(0xFF0A0E14))
                        val circuitColor = config.accentColor.copy(alpha = 0.07f)
                        val circRand = java.util.Random(4321)
                        for (i in 0..12) {
                            val startY = circRand.nextFloat() * size.height
                            val startX = 0f
                            val midX = circRand.nextFloat() * size.width * 0.6f
                            val endX = size.width
                            val angleY = startY + (if (circRand.nextBoolean()) 60f else -60f)

                            drawLine(circuitColor, androidx.compose.ui.geometry.Offset(startX, startY), androidx.compose.ui.geometry.Offset(midX, startY), 2f)
                            drawLine(circuitColor, androidx.compose.ui.geometry.Offset(midX, startY), androidx.compose.ui.geometry.Offset(midX + 60f, angleY), 2f)
                            drawLine(circuitColor, androidx.compose.ui.geometry.Offset(midX + 60f, angleY), androidx.compose.ui.geometry.Offset(endX, angleY), 2f)

                            drawCircle(config.accentColor.copy(alpha = 0.2f), 4.dp.toPx(), androidx.compose.ui.geometry.Offset(midX, startY))
                            drawCircle(config.accentColor.copy(alpha = 0.2f), 4.dp.toPx(), androidx.compose.ui.geometry.Offset(midX + 60f, angleY))
                        }
                    }
                    6 -> { // Panal de abeja
                        drawRect(Color(0xFF0D1117))
                        val hexColor = config.accentColor.copy(alpha = 0.05f)
                        val hexRadius = 24.dp.toPx()
                        val wGrid = hexRadius * kotlin.math.sqrt(3f)
                        val hHeight = hexRadius * 1.5f
                        for (row in 0..(size.height / hHeight).toInt() + 1) {
                            for (col in 0..(size.width / wGrid).toInt() + 1) {
                                val cx = col * wGrid + (if (row % 2 == 1) wGrid / 2f else 0f)
                                val cy = row * hHeight
                                val path = androidx.compose.ui.graphics.Path()
                                for (j in 0..5) {
                                    val angle = j * (kotlin.math.PI / 3f)
                                    val px = cx + hexRadius * kotlin.math.cos(angle).toFloat()
                                    val py = cy + hexRadius * kotlin.math.sin(angle).toFloat()
                                    if (j == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                }
                                path.close()
                                drawPath(path, hexColor, style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
                            }
                        }
                    }
                    else -> { // Nebulosa galáctica
                        drawRect(Color(0xFF060312))
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF4A148C).copy(alpha = 0.3f), Color.Transparent),
                                center = androidx.compose.ui.geometry.Offset(0f, 0f),
                                radius = size.maxDimension / 1.2f
                            ),
                            size = size
                        )
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFE91E63).copy(alpha = 0.2f), Color.Transparent),
                                center = androidx.compose.ui.geometry.Offset(size.width, size.height * 0.4f),
                                radius = size.maxDimension / 1.2f
                            ),
                            size = size
                        )
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF1A237E).copy(alpha = 0.3f), Color.Transparent),
                                center = androidx.compose.ui.geometry.Offset(size.width * 0.3f, size.height),
                                radius = size.maxDimension / 1.2f
                            ),
                            size = size
                        )
                    }
                }
            }
            2 -> { // User image background
                bitmap?.let { bmp ->
                    drawImage(
                        image = bmp,
                        dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
                    )
                    drawRect(
                        color = Color.Black.copy(alpha = (1f - config.imageOpacity) * 0.95f),
                        size = size
                    )
                } ?: run {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(config.accentColor.copy(alpha = 0.08f), Color(0xFF1F2937), Color(0xFF111827)),
                            center = center,
                            radius = size.maxDimension / 1.1f
                        ),
                        size = size
                    )
                }
            }
            else -> { // Gradient
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(config.accentColor.copy(alpha = 0.08f), Color(0xFF1F2937), Color(0xFF111827)),
                        center = center,
                        radius = size.maxDimension / 1.1f
                    ),
                    size = size
                )
            }
        }

        // Vignette
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                center = center,
                radius = size.maxDimension / 1.4f
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
            color = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.05f),
            start = androidx.compose.ui.geometry.Offset(0f, size.height * scanlineY),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height * scanlineY),
            strokeWidth = 2.dp.toPx()
        )
    }
}
