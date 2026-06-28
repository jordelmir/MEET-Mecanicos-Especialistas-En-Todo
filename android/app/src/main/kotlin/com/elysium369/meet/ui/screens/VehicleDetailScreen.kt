package com.elysium369.meet.ui.screens

import com.elysium369.meet.ui.components.AnimatedNeonIcon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elysium369.meet.core.obd.MaintenanceAdvisor
import com.elysium369.meet.data.local.entities.MaintenanceLogEntity
import com.elysium369.meet.data.local.entities.RepairHistoryEntity
import com.elysium369.meet.ui.VehicleDetailViewModel
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.*
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.elysium369.meet.core.export.ReportGenerator

import androidx.compose.foundation.BorderStroke
import com.elysium369.meet.ui.NhtsaRecallsState
import com.elysium369.meet.core.sync.RecallItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDetailScreen(
    vehicleId: String,
    vin: String,
    make: String,
    model: String,
    year: Int,
    onNavigateBack: () -> Unit,
    viewModel: VehicleDetailViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Mantenimiento", "Reparaciones", "Experto Local", "Alertas NHTSA")

    LaunchedEffect(vehicleId) {
        viewModel.loadVehicleData(vehicleId)
    }

    val maintenanceLogs by viewModel.maintenanceLogs.collectAsState()
    val repairHistory by viewModel.repairHistory.collectAsState()
    val totalMaintCost by viewModel.totalMaintenanceCost.collectAsState()
    val totalRepairCost by viewModel.totalRepairCost.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showReportCustomizer by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Preferences for Workshop branding
    val sharedPrefs = remember { context.getSharedPreferences("meet_prefs", android.content.Context.MODE_PRIVATE) }
    var workshopName by remember { mutableStateOf(sharedPrefs.getString("workshop_name", "") ?: "") }
    var workshopAddress by remember { mutableStateOf(sharedPrefs.getString("workshop_address", "") ?: "") }
    var workshopPhone by remember { mutableStateOf(sharedPrefs.getString("workshop_phone", "") ?: "") }
    var workshopEmail by remember { mutableStateOf(sharedPrefs.getString("workshop_email", "") ?: "") }

    var selectedTheme by remember { mutableStateOf("ELYSIUM_CYAN") }
    var includeMaint by remember { mutableStateOf(true) }
    var includeRepairs by remember { mutableStateOf(true) }
    var includeSummary by remember { mutableStateOf(true) }
    var includeBranding by remember { mutableStateOf(true) }
    var includeExpert by remember { mutableStateOf(false) }

    var isGenerating by remember { mutableStateOf(false) }
    var currentStepIndex by remember { mutableStateOf(0) }
    var reportFile by remember { mutableStateOf<java.io.File?>(null) }
    var showWorkshopEditor by remember { mutableStateOf(false) }

    val compilationSteps = listOf(
        "Cargando historial de la base de datos...",
        "Extrayendo registros de mantenimiento preventivo...",
        "Compilando historial de reparaciones correctivas...",
        "Calculando resumen de inversión histórica...",
        "Procesando telemetría del experto local OBD-II...",
        "Enlazando cabecera y metadatos de taller...",
        "Dibujando lienzo PDF con temática seleccionada...",
        "Compilando y guardando reporte clínico de garage..."
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("$make $model $year", fontWeight = FontWeight.Bold)
                        Text("VIN: $vin", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        AnimatedNeonIcon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                ),
                actions = {
                    IconButton(onClick = { 
                        showReportCustomizer = true
                    }) {
                        AnimatedNeonIcon(Icons.Default.Share, contentDescription = "Exportar Historial")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                AnimatedNeonIcon(Icons.Default.Add, contentDescription = "Añadir Registro", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Dashboard Summary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricCard(
                    title = "Inversión Mantenimiento",
                    value = "$${String.format("%.2f", totalMaintCost)}",
                    icon = Icons.Default.Build,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Inversión Reparaciones",
                    value = "$${String.format("%.2f", totalRepairCost)}",
                    icon = Icons.Default.Warning,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
            }

            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            when (selectedTab) {
                0 -> MaintenanceList(maintenanceLogs)
                1 -> RepairList(repairHistory)
                2 -> ExpertTelemetryPanel(viewModel)
                3 -> NhtsaRecallsPanel(viewModel)
            }
        }

        if (showAddDialog) {
            val vehicle = viewModel.vehicle.collectAsState().value
            val context = androidx.compose.ui.platform.LocalContext.current
            val avgDailyKm = viewModel.calculateAverageDailyKm()

            AddRecordDialog(
                vehicleId = vehicleId,
                initialOdometer = vehicle?.odometerKm?.toString() ?: "",
                isMaintenance = selectedTab == 0,
                avgDailyKm = avgDailyKm,
                onDismiss = { showAddDialog = false },
                onAddMaintenance = { uri, logBuilder ->
                    viewModel.copyImageAndSaveMaintenance(context, uri, logBuilder)
                    showAddDialog = false
                },
                onAddRepair = { uri, repairBuilder ->
                    viewModel.copyImageAndSaveRepair(context, uri, repairBuilder)
                    showAddDialog = false
                }
            )
        }

        if (showReportCustomizer) {
            Dialog(
                onDismissRequest = { 
                    if (!isGenerating) {
                        showReportCustomizer = false
                        reportFile = null
                    }
                },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = !isGenerating,
                    dismissOnClickOutside = !isGenerating
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MeetColors.backgroundDark
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MeetColors.backgroundDark)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PERSONALIZAR HISTORIAL",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (!isGenerating) {
                                IconButton(onClick = { 
                                    showReportCustomizer = false
                                    reportFile = null
                                }) {
                                    AnimatedNeonIcon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                                }
                            }
                        }

                        // Main Content
                        if (!isGenerating && reportFile == null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp)
                            ) {
                                Text(
                                    text = "Personaliza y exporta un informe clínico premium con el historial detallado de este vehículo.",
                                    color = MeetColors.textSecondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )

                                Text(
                                    text = "VISTA PREVIA EN TIEMPO REAL",
                                    color = MeetColors.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                VehicleDocumentPreviewCard(
                                    theme = selectedTheme,
                                    workshopName = if (includeBranding && workshopName.isNotBlank()) workshopName else "Elysium Vanguard Clinic",
                                    make = make,
                                    model = model,
                                    includeMaint = includeMaint,
                                    includeRepairs = includeRepairs,
                                    includeSummary = includeSummary,
                                    includeExpert = includeExpert
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                // Secciones a Incluir
                                Text(
                                    text = "SECCIONES A INCLUIR",
                                    color = MeetColors.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MeetColors.cardBackground)
                                        .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().clickable { includeSummary = !includeSummary }.padding(8.dp)
                                    ) {
                                        Checkbox(
                                            checked = includeSummary,
                                            onCheckedChange = { includeSummary = it },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = MeetColors.neonGreen,
                                                uncheckedColor = MeetColors.textSecondary
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("Resumen Financiero", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text("Consolidado general de inversión total en mantenimiento y repuestos.", color = MeetColors.textSecondary, fontSize = 11.sp)
                                        }
                                    }
                                    Divider(color = MeetColors.borderSubtle, modifier = Modifier.padding(horizontal = 8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().clickable { includeMaint = !includeMaint }.padding(8.dp)
                                    ) {
                                        Checkbox(
                                            checked = includeMaint,
                                            onCheckedChange = { includeMaint = it },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = MeetColors.neonGreen,
                                                uncheckedColor = MeetColors.textSecondary
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("Mantenimiento Preventivo", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text("Tabla detallada de registros de cambios de fluidos, filtros y revisiones de rutina.", color = MeetColors.textSecondary, fontSize = 11.sp)
                                        }
                                    }
                                    HorizontalDivider(color = MeetColors.borderSubtle, modifier = Modifier.padding(horizontal = 8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().clickable { includeRepairs = !includeRepairs }.padding(8.dp)
                                    ) {
                                        Checkbox(
                                            checked = includeRepairs,
                                            onCheckedChange = { includeRepairs = it },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = MeetColors.neonGreen,
                                                uncheckedColor = MeetColors.textSecondary
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("Historial de Reparaciones", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text("Detalle exhaustivo de componentes correctivos, piezas críticas sustituidas y fallos.", color = MeetColors.textSecondary, fontSize = 11.sp)
                                        }
                                    }
                                    HorizontalDivider(color = MeetColors.borderSubtle, modifier = Modifier.padding(horizontal = 8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().clickable { includeExpert = !includeExpert }.padding(8.dp)
                                    ) {
                                        Checkbox(
                                            checked = includeExpert,
                                            onCheckedChange = { includeExpert = it },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = MeetColors.neonGreen,
                                                uncheckedColor = MeetColors.textSecondary
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("Diagnóstico Experto Local", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text("Procedimientos clínicos, análisis de sensores y diagnóstico pericial de telemetría OBD-II.", color = MeetColors.textSecondary, fontSize = 11.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // Temas Visuales
                                Text(
                                    text = "TEMA VISUAL",
                                    color = MeetColors.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val themes = listOf(
                                        Triple("ELYSIUM_CYAN", "Cyan", Color(0xFF00FFD4)),
                                        Triple("CARBON_RED", "Rojo", Color(0xFFFF3333)),
                                        Triple("CLASSIC_DARK", "Oro", Color(0xFFFFB300)),
                                        Triple("PRINTER_FRIENDLY", "Eco", Color(0xFF888888))
                                    )
                                    themes.forEach { (id, name, color) ->
                                        val isSel = selectedTheme == id
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(45.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSel) color.copy(alpha = 0.15f) else MeetColors.cardBackground)
                                                .border(
                                                    1.dp,
                                                    if (isSel) color else MeetColors.borderSubtle,
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .clickable { selectedTheme = id }
                                                .padding(4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(name, color = if (isSel) Color.White else MeetColors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // Configuración del Taller (Branding)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = includeBranding,
                                            onCheckedChange = { includeBranding = it },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = MeetColors.neonGreen,
                                                uncheckedColor = MeetColors.textSecondary
                                            )
                                        )
                                        Text("Encabezado de Taller", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    TextButton(onClick = { showWorkshopEditor = !showWorkshopEditor }) {
                                        Text(
                                            if (showWorkshopEditor) "Ocultar Ajustes" else "Editar Taller",
                                            color = MeetColors.neonGreen,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                if (showWorkshopEditor) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MeetColors.cardBackground)
                                            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = workshopName,
                                            onValueChange = { 
                                                workshopName = it
                                                sharedPrefs.edit().putString("workshop_name", it).apply()
                                            },
                                            label = { Text("Nombre del Taller", color = MeetColors.textSecondary) },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = MeetColors.neonGreen,
                                                unfocusedBorderColor = MeetColors.borderSubtle
                                            )
                                        )
                                        OutlinedTextField(
                                            value = workshopAddress,
                                            onValueChange = { 
                                                workshopAddress = it
                                                sharedPrefs.edit().putString("workshop_address", it).apply()
                                            },
                                            label = { Text("Dirección", color = MeetColors.textSecondary) },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = MeetColors.neonGreen,
                                                unfocusedBorderColor = MeetColors.borderSubtle
                                            )
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = workshopPhone,
                                                onValueChange = { 
                                                    workshopPhone = it
                                                    sharedPrefs.edit().putString("workshop_phone", it).apply()
                                                },
                                                label = { Text("Teléfono", color = MeetColors.textSecondary) },
                                                modifier = Modifier.weight(1f),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White,
                                                    focusedBorderColor = MeetColors.neonGreen,
                                                    unfocusedBorderColor = MeetColors.borderSubtle
                                                )
                                            )
                                            OutlinedTextField(
                                                value = workshopEmail,
                                                onValueChange = { 
                                                    workshopEmail = it
                                                    sharedPrefs.edit().putString("workshop_email", it).apply()
                                                },
                                                label = { Text("Correo Electrónico", color = MeetColors.textSecondary) },
                                                modifier = Modifier.weight(1.2f),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White,
                                                    focusedBorderColor = MeetColors.neonGreen,
                                                    unfocusedBorderColor = MeetColors.borderSubtle
                                                )
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))
                            }

                            // Bottom Actions
                            Button(
                                onClick = {
                                    isGenerating = true
                                    currentStepIndex = 0
                                    coroutineScope.launch {
                                        for (i in 0 until compilationSteps.size - 1) {
                                            currentStepIndex = i
                                            delay(800)
                                        }
                                        
                                        val file = viewModel.generateHistoryPdf(
                                            context = context,
                                            themeName = selectedTheme,
                                            includeMaint = includeMaint,
                                            includeRepairs = includeRepairs,
                                            includeSummary = includeSummary,
                                            includeBranding = includeBranding,
                                            includeExpert = includeExpert
                                        )
                                        
                                        if (file != null) {
                                            reportFile = file
                                            currentStepIndex = compilationSteps.size - 1
                                        }
                                        isGenerating = false
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("COMPILAR REPORTE", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        } else if (isGenerating) {
                            // Compiler Animation Console
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = MeetColors.neonGreen)
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "COMPILANDO REPORTE HISTÓRICO",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Console Card
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF070A10))
                                        .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    for (idx in 0..currentStepIndex) {
                                        val isCurrent = idx == currentStepIndex
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (isCurrent) "> " else "✓ ",
                                                color = if (isCurrent) MeetColors.neonGreen else Color.Gray,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = compilationSteps[idx],
                                                color = if (isCurrent) Color.White else Color.Gray,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Success View
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(36.dp))
                                        .background(MeetColors.neonGreen.copy(alpha = 0.1f))
                                        .border(2.dp, MeetColors.neonGreen, RoundedCornerShape(36.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AnimatedNeonIcon(Icons.Default.Check, contentDescription = "Éxito", tint = MeetColors.neonGreen, modifier = Modifier.size(36.dp))
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "REPORTE COMPILADO",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "El informe clínico del vehículo se ha exportado correctamente en un formato PDF certificado.",
                                    color = MeetColors.textSecondary,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(32.dp))

                                Button(
                                    onClick = {
                                        reportFile?.let {
                                            ReportGenerator(context).shareReport(it)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    AnimatedNeonIcon(Icons.Default.Share, contentDescription = null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("COMPARTIR / ENVIAR REPORTE", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                TextButton(onClick = {
                                    reportFile = null
                                    showReportCustomizer = false
                                }) {
                                    Text("Cerrar", color = MeetColors.textSecondary)
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
fun VehicleDocumentPreviewCard(
    theme: String,
    workshopName: String,
    make: String,
    model: String,
    includeMaint: Boolean,
    includeRepairs: Boolean,
    includeSummary: Boolean,
    includeExpert: Boolean
) {
    val isLight = theme == "PRINTER_FRIENDLY"
    val docBg = if (isLight) Color.White else Color(0xFF111726)
    val headerBg = if (isLight) Color.White else when (theme) {
        "CARBON_RED" -> Color(0xFF1E0707)
        "CLASSIC_DARK" -> Color(0xFF1E190F)
        else -> Color(0xFF080C14)
    }
    val accentColor = when (theme) {
        "CARBON_RED" -> Color(0xFFFF3333)
        "CLASSIC_DARK" -> Color(0xFFFFB300)
        "PRINTER_FRIENDLY" -> Color.Black
        else -> Color(0xFF00FFD4)
    }
    val docText = if (isLight) Color.Black else Color.White
    val docSubText = if (isLight) Color.Gray else Color.LightGray

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MeetColors.cardBackground)
            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(160.dp)
                .fillMaxHeight()
                .padding(vertical = 12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(docBg)
                .border(1.dp, if (isLight) Color.LightGray else accentColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
        ) {
            // Document Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(headerBg)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = workshopName.take(12).uppercase(),
                        color = if (isLight) Color.Black else Color.White,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "HISTORIAL",
                        color = accentColor,
                        fontSize = 6.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isLight) {
                Spacer(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Black))
            }

            // Simulated document body
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Vehicle metadata mockup
                Text(
                    text = "$make $model".take(16).uppercase(),
                    color = docText,
                    fontSize = 6.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(modifier = Modifier.width(60.dp).height(3.dp).background(docSubText.copy(alpha = 0.5f)))

                Spacer(modifier = Modifier.height(4.dp))

                if (includeSummary) {
                    // Summary Box mockup
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .background(if (isLight) Color(0xFFF2F2F2) else Color(0xFF1B2336))
                            .border(0.5.dp, if (isLight) Color.LightGray else accentColor.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                            .padding(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.width(20.dp).height(4.dp).background(accentColor.copy(alpha = 0.5f)))
                            Box(modifier = Modifier.width(20.dp).height(4.dp).background(Color.Red.copy(alpha = 0.5f)))
                            Box(modifier = Modifier.width(20.dp).height(4.dp).background(docText.copy(alpha = 0.5f)))
                        }
                    }
                }

                if (includeMaint) {
                    // Maintenance rows mockup
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(modifier = Modifier.width(90.dp).height(3.dp).background(docSubText.copy(alpha = 0.4f)))
                        Box(modifier = Modifier.width(70.dp).height(2.dp).background(docSubText.copy(alpha = 0.2f)))
                        Box(modifier = Modifier.width(85.dp).height(2.dp).background(docSubText.copy(alpha = 0.2f)))
                    }
                }

                if (includeRepairs) {
                    // Repairs rows mockup
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(modifier = Modifier.width(80.dp).height(3.dp).background(Color.Red.copy(alpha = 0.4f)))
                        Box(modifier = Modifier.width(75.dp).height(2.dp).background(docSubText.copy(alpha = 0.2f)))
                    }
                }

                if (includeExpert) {
                    // Expert procedures mockup
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(modifier = Modifier.width(85.dp).height(3.dp).background(accentColor.copy(alpha = 0.4f)))
                        Box(modifier = Modifier.width(60.dp).height(2.dp).background(docSubText.copy(alpha = 0.2f)))
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AnimatedNeonIcon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun MaintenanceList(logs: List<MaintenanceLogEntity>) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (logs.isEmpty()) {
            item {
                Text("No hay mantenimientos registrados.", modifier = Modifier.padding(16.dp))
            }
        }
        items(logs) { log ->
            MaintenanceCard(log)
        }
    }
}

@Composable
fun RepairList(repairs: List<RepairHistoryEntity>) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (repairs.isEmpty()) {
            item {
                Text("No hay reparaciones registradas.", modifier = Modifier.padding(16.dp))
            }
        }
        items(repairs) { repair ->
            RepairCard(repair)
        }
    }
}

@Composable
fun MaintenanceCard(log: MaintenanceLogEntity) {
    val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(log.category, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("$${log.cost}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(log.description, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Realizado:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text("${log.odometerAtService} km", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(formatter.format(Date(log.datePerformed)), style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    val isOverdue = System.currentTimeMillis() > log.nextDueDate
                    val statusColor = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    Text(if (isOverdue) "Vencido:" else "Próximo:", style = MaterialTheme.typography.labelSmall, color = statusColor)
                    Text("${log.nextDueKm} km", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = statusColor)
                    Text(formatter.format(Date(log.nextDueDate)), style = MaterialTheme.typography.bodySmall, color = statusColor)
                }
            }
            if (log.receiptPhotoPath != null) {
                Spacer(modifier = Modifier.height(12.dp))
                coil.compose.AsyncImage(
                    model = java.io.File(log.receiptPhotoPath),
                    contentDescription = "Recibo/Foto",
                    modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
        }
    }
}

@Composable
fun RepairCard(repair: RepairHistoryEntity) {
    val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(repair.partCategory, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("$${repair.totalCost}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("${repair.partName} (${repair.brand})", style = MaterialTheme.typography.bodyMedium)
            if (repair.relatedDtc != null) {
                Text("DTC Relacionado: ${repair.relatedDtc}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Realizado:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text("${repair.odometerAtRepair} km", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(formatter.format(Date(repair.datePerformed)), style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Garantía:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    Text("${repair.warrantyMonths} Meses", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text("${repair.warrantyKm} km", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (repair.photoPath != null) {
                Spacer(modifier = Modifier.height(12.dp))
                coil.compose.AsyncImage(
                    model = java.io.File(repair.photoPath),
                    contentDescription = "Foto de Reparación",
                    modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddRecordDialog(
    vehicleId: String,
    initialOdometer: String,
    isMaintenance: Boolean,
    avgDailyKm: Float?,
    onDismiss: () -> Unit,
    onAddMaintenance: (android.net.Uri?, (String?) -> MaintenanceLogEntity) -> Unit,
    onAddRepair: (android.net.Uri?, (String?) -> RepairHistoryEntity) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("") }
    var selectedPart by remember { mutableStateOf("") }
    var selectedLocation by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var odometer by remember { mutableStateOf(initialOdometer) }
    
    var categoryExpanded by remember { mutableStateOf(false) }
    var partExpanded by remember { mutableStateOf(false) }

    var selectedImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        selectedImageUri = uri
    }

    val maintItems = MaintenanceAdvisor.STANDARD_MAINTENANCE_ITEMS
    val catalog = com.elysium369.meet.core.obd.VehiclePartsCatalog.CATALOG
    val locations = com.elysium369.meet.core.obd.VehiclePartsCatalog.LOCATIONS

    val currentCatalogCategory = catalog.find { it.name == selectedCategory }
    val currentPartDefinition = currentCatalogCategory?.parts?.find { it.name == selectedPart }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (isMaintenance) "Añadir Mantenimiento" else "Añadir Reparación",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoría") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        if (isMaintenance) {
                            maintItems.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.category) },
                                    onClick = {
                                        selectedCategory = item.category
                                        description = item.description
                                        categoryExpanded = false
                                    }
                                )
                            }
                        } else {
                            catalog.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = {
                                        selectedCategory = category.name
                                        selectedPart = ""
                                        selectedLocation = ""
                                        description = ""
                                        categoryExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (isMaintenance) {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Descripción") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else if (selectedCategory.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = partExpanded,
                        onExpandedChange = { partExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedPart,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Pieza Dañada/Cambiada") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = partExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = partExpanded,
                            onDismissRequest = { partExpanded = false }
                        ) {
                            currentCatalogCategory?.parts?.forEach { part ->
                                DropdownMenuItem(
                                    text = { Text(part.name) },
                                    onClick = {
                                        selectedPart = part.name
                                        selectedLocation = ""
                                        description = part.name
                                        partExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (!isMaintenance && currentPartDefinition?.hasLocation == true) {
                    Text("Ubicación de la Pieza", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        locations.forEach { loc ->
                            FilterChip(
                                selected = selectedLocation == loc,
                                onClick = { 
                                    selectedLocation = if (selectedLocation == loc) "" else loc
                                    description = if (selectedLocation.isEmpty()) selectedPart else "$selectedPart ($selectedLocation)"
                                },
                                label = { Text(loc) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = odometer,
                    onValueChange = { odometer = it },
                    label = { Text("Kilometraje Actual") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it },
                    label = { Text("Costo Total ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Photo Attachment
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        AnimatedNeonIcon(Icons.Default.Image, contentDescription = "Adjuntar Foto")
                        Spacer(Modifier.width(8.dp))
                        Text(if (selectedImageUri == null) "Adjuntar Foto" else "Cambiar Foto")
                    }
                    if (selectedImageUri != null) {
                        Spacer(Modifier.width(16.dp))
                        coil.compose.AsyncImage(
                            model = selectedImageUri,
                            contentDescription = "Miniatura",
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val odo = odometer.toLongOrNull() ?: 0L
                            val costVal = cost.toFloatOrNull() ?: 0f
                            val now = System.currentTimeMillis()
                            val id = UUID.randomUUID().toString()

                            if (isMaintenance) {
                                val item = maintItems.find { it.category == selectedCategory }
                                val intervalKm = item?.defaultIntervalKm ?: 10000
                                val intervalMonths = item?.defaultIntervalMonths ?: 12
                                val nextDue = MaintenanceAdvisor.getNextMaintenanceDue(odo.toInt(), now, intervalKm, intervalMonths, avgDailyKm)

                                onAddMaintenance(selectedImageUri) { photoPath ->
                                    MaintenanceLogEntity(
                                        id = id,
                                        vehicleId = vehicleId,
                                        category = selectedCategory.ifEmpty { "General" },
                                        description = description,
                                        brand = "",
                                        specification = "",
                                        datePerformed = now,
                                        odometerAtService = odo,
                                        intervalKm = intervalKm,
                                        intervalMonths = intervalMonths,
                                        nextDueKm = nextDue.first.toLong(),
                                        nextDueDate = nextDue.second,
                                        cost = costVal,
                                        currency = "USD",
                                        workshopName = "",
                                        notes = "",
                                        receiptPhotoPath = photoPath,
                                        createdAt = now
                                    )
                                }
                            } else {
                                val expKm = currentPartDefinition?.expectedLifeKm
                                val expMonths = currentPartDefinition?.expectedLifeMonths
                                val nextReplKm = if (expKm != null) odo + expKm else null

                                onAddRepair(selectedImageUri) { photoPath ->
                                    RepairHistoryEntity(
                                        id = id,
                                        vehicleId = vehicleId,
                                        partCategory = selectedCategory.ifEmpty { "General" },
                                        partName = description.ifEmpty { selectedPart },
                                        partNumber = "",
                                        brand = "",
                                        isOem = false,
                                        reason = "Daño / Reemplazo",
                                        relatedDtc = null,
                                        datePerformed = now,
                                        odometerAtRepair = odo,
                                        expectedLifeKm = expKm,
                                        expectedLifeMonths = expMonths,
                                        nextReplacementKm = nextReplKm,
                                        isPeriodic = currentPartDefinition?.isPeriodic ?: false,
                                        laborCost = 0f,
                                        partCost = costVal,
                                        totalCost = costVal,
                                        currency = "USD",
                                        workshopName = "",
                                        warrantyMonths = 0,
                                        warrantyKm = 0,
                                        notes = "",
                                        photoPath = photoPath,
                                        createdAt = now
                                    )
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("GUARDAR")
                    }
                }
            }
        }
    }
}

@Composable
fun TelemetryOscilloscope(
    active: Boolean,
    profile: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "oscilloscope")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val random = remember { java.util.Random() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF070B12))
            .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val gridSpacing = 20f

            // Draw grid
            for (x in 0..(width / gridSpacing).toInt()) {
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = androidx.compose.ui.geometry.Offset(x * gridSpacing, 0f),
                    end = androidx.compose.ui.geometry.Offset(x * gridSpacing, height),
                    strokeWidth = 1f
                )
            }
            for (y in 0..(height / gridSpacing).toInt()) {
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = androidx.compose.ui.geometry.Offset(0f, y * gridSpacing),
                    end = androidx.compose.ui.geometry.Offset(width, y * gridSpacing),
                    strokeWidth = 1f
                )
            }

            // Draw signal path
            val path = androidx.compose.ui.graphics.Path()
            if (active) {
                val waveColor = when (profile) {
                    "NORMAL" -> Color(0xFF00FFD4)
                    "VACUUM_LEAK" -> Color(0xFFFFB300)
                    "OVERHEATING" -> Color(0xFFFF3333)
                    else -> Color(0xFFFF3333)
                }

                for (x in 0..width.toInt() step 2) {
                    val xFloat = x.toFloat()
                    val normalizedX = xFloat / width
                    val angle = normalizedX * 4f * Math.PI.toFloat() + phase
                    
                    val yVal = when (profile) {
                        "NORMAL" -> {
                            val base = Math.sin(angle.toDouble()).toFloat() * 15f
                            val noise = (random.nextFloat() - 0.5f) * 2f
                            height / 2f + base + noise
                        }
                        "VACUUM_LEAK" -> {
                            val base = Math.sin(angle.toDouble() * 2.5).toFloat() * 25f
                            val noise = (random.nextFloat() - 0.5f) * 12f
                            height / 2f + base + noise
                        }
                        "OVERHEATING" -> {
                            val base = Math.sin(angle.toDouble() * 0.5).toFloat() * 10f
                            val drift = -15f
                            height / 2f + base + drift
                        }
                        "BATTERY_FAIL" -> {
                            val noise = (random.nextFloat() - 0.5f) * 1.5f
                            height * 0.8f + noise
                        }
                        else -> height / 2f
                    }

                    if (x == 0) {
                        path.moveTo(xFloat, yVal)
                    } else {
                        path.lineTo(xFloat, yVal)
                    }
                }

                drawPath(
                    path = path,
                    color = waveColor,
                    style = Stroke(width = 3f)
                )
            } else {
                drawLine(
                    color = Color.Gray.copy(alpha = 0.5f),
                    start = androidx.compose.ui.geometry.Offset(0f, height / 2f),
                    end = androidx.compose.ui.geometry.Offset(width, height / 2f),
                    strokeWidth = 2f
                )
            }
        }

        Text(
            text = if (active) "TELEMETRÍA EN VIVO: OSCILOSCOPIO DIGITAL" else "OSCILOSCOPIO DESACTIVADO",
            color = if (active) Color(0xFF00FFD4) else Color.Gray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        )
    }
}

@Composable
fun ExpertTelemetryPanel(
    viewModel: VehicleDetailViewModel
) {
    val expertProcedures by viewModel.expertProcedures.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "TELEMETRÍA REAL",
            color = MeetColors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MeetColors.cardBackground)
                .border(1.dp, MeetColors.borderSubtle, RoundedCornerShape(8.dp))
                .padding(14.dp)
        ) {
            Text(
                text = "Los valores en vivo se leen desde Scanner con un adaptador OBD-II conectado. Este panel no genera datos artificiales.",
                color = MeetColors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "PROCEDIMIENTOS Y RECOMENDACIONES CLÍNICAS",
            color = MeetColors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (expertProcedures.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MeetColors.cardBackground)
                    .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedNeonIcon(Icons.Default.CheckCircle, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No hay procedimientos pendientes con los DTCs guardados para este vehículo. Conecta el scanner para actualizar lecturas reales.",
                        color = Color.White,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                expertProcedures.forEach { proc ->
                    val color = when (proc.severity) {
                        com.elysium369.meet.core.obd.DiagnosticSeverity.CRITICAL -> MeetColors.error
                        com.elysium369.meet.core.obd.DiagnosticSeverity.HIGH -> MeetColors.warning
                        com.elysium369.meet.core.obd.DiagnosticSeverity.MODERATE -> MeetColors.warning
                        com.elysium369.meet.core.obd.DiagnosticSeverity.INFO -> MeetColors.neonGreen
                    }
                    
                    var expanded by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MeetColors.cardBackground)
                            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .clickable { expanded = !expanded }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = proc.title,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(color)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = proc.severity.name,
                                        color = color,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            AnimatedNeonIcon(
                                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MeetColors.textSecondary
                            )
                        }

                        if (expanded) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MeetColors.borderSubtle)
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Descripción Clínica:",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = proc.description,
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )

                            Text(
                                text = "Causas Probables:",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            proc.probableCauses.forEach { cause ->
                                Text(
                                    text = "• $cause",
                                    color = MeetColors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Pasos de Prueba Recomendados:",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            proc.testSteps.forEachIndexed { i, step ->
                                Text(
                                    text = "${i + 1}. $step",
                                    color = MeetColors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NhtsaRecallsPanel(viewModel: VehicleDetailViewModel) {
    val recallsState by viewModel.recallsState.collectAsState()
    val vehicle by viewModel.vehicle.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ALERTAS DE SEGURIDAD OFICIALES (NHTSA)",
                color = MeetColors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            
            IconButton(
                onClick = {
                    vehicle?.let { viewModel.fetchNhtsaRecalls(it.make, it.model, it.year) }
                }
            ) {
                AnimatedNeonIcon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refrescar Recalls",
                    tint = MeetColors.neonGreen
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        when (val state = recallsState) {
            is NhtsaRecallsState.Idle -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Preparando consulta de seguridad...",
                        color = MeetColors.textSecondary
                    )
                }
            }
            is NhtsaRecallsState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MeetColors.neonGreen)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Consultando base de datos federal de la NHTSA...",
                            color = MeetColors.textSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            is NhtsaRecallsState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedNeonIcon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MeetColors.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Error de conexión: ${state.message}",
                            color = Color.White,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                vehicle?.let { viewModel.fetchNhtsaRecalls(it.make, it.model, it.year) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen)
                        ) {
                            Text("REINTENTAR", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            is NhtsaRecallsState.Success -> {
                if (state.recalls.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MeetColors.cardBackground)
                            .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AnimatedNeonIcon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MeetColors.neonGreen,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "¡SIN RECALLS ACTIVOS!",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Este vehículo no cuenta con alertas de recalls ni defectos mecánicos registrados ante la NHTSA en este momento.",
                                color = MeetColors.textSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(state.recalls) { recall ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MeetColors.cardBackground)
                                    .border(1.dp, MeetColors.warning.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = MeetColors.error.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, MeetColors.error)
                                    ) {
                                        Text(
                                            text = "RECALL CRÍTICO",
                                            color = MeetColors.error,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = "Campaña: ${recall.campaignNumber}",
                                        color = MeetColors.textSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = recall.component.uppercase(),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = recall.summary,
                                    color = MeetColors.textSecondary,
                                    fontSize = 12.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = MeetColors.borderSubtle)
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "CONSECUENCIA",
                                            color = MeetColors.warning,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = recall.consequence,
                                            color = Color.LightGray,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "SOLUCIÓN / REMEDIO",
                                            color = MeetColors.neonGreen,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = recall.remedy,
                                            color = Color.LightGray,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
