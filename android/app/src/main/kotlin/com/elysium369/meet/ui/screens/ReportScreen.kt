package com.elysium369.meet.ui.screens

import com.elysium369.meet.ui.components.AnimatedNeonIcon

import android.content.Context
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import com.elysium369.meet.core.print.BluetoothPrinterManager
import com.elysium369.meet.core.print.PrintReportData
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.core.export.ReportGenerator
import com.elysium369.meet.core.reports.ReportIntegrityCard
import com.elysium369.meet.core.reports.rememberReportHashingService
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteOutlinedButton
import com.elysium369.meet.ui.theme.MeetColors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ReportScreen(navController: NavController, viewModel: ObdViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val generator = remember { ReportGenerator(context) }
    val printerManager = remember { BluetoothPrinterManager(context) }
    var printerList by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var showPrinterPickerDialog by remember { mutableStateOf(false) }
    var isPrinting by remember { mutableStateOf(false) }
    var printingStatus by remember { mutableStateOf("") }
    
    val activeDtcs by viewModel.activeDtcs.collectAsState()
    val predictiveReport by viewModel.predictiveHealthReport.collectAsState()
    val healthScore by viewModel.healthScore.collectAsState()
    
    // Preferences for Workshop branding
    val sharedPrefs = remember { context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE) }
    var workshopName by remember { mutableStateOf(sharedPrefs.getString("workshop_name", "") ?: "") }
    var workshopAddress by remember { mutableStateOf(sharedPrefs.getString("workshop_address", "") ?: "") }
    var workshopPhone by remember { mutableStateOf(sharedPrefs.getString("workshop_phone", "") ?: "") }
    var workshopEmail by remember { mutableStateOf(sharedPrefs.getString("workshop_email", "") ?: "") }

    // Customization states
    var selectedTheme by remember { mutableStateOf("ELYSIUM_CYAN") }
    var scanMode by remember { mutableStateOf("PRE_SCAN") } // PRE_SCAN or POST_SCAN
    var includeDtcs by remember { mutableStateOf(true) }
    var includeAi by remember { mutableStateOf(true) }
    var includeGraphs by remember { mutableStateOf(true) }
    var includePredictive by remember { mutableStateOf(true) }
    var includeBranding by remember { mutableStateOf(true) }

    var isGenerating by remember { mutableStateOf(false) }
    var currentStepIndex by remember { mutableStateOf(0) }
    var reportFile by remember { mutableStateOf<File?>(null) }
    var showWorkshopEditor by remember { mutableStateOf(false) }

    val compilationSteps = listOf(
        "Estableciendo enlace de datos UDS ISO-14229...",
        "Extrayendo historial de telemetría de alta frecuencia...",
        "Corriendo algoritmos de salud predictiva de batería y motor...",
        "Calculando score de salud y anomalías por IA...",
        "Enlazando cabecera y metadatos de taller...",
        "Dibujando lienzo PDF con temática seleccionada...",
        "Compilando y guardando reporte certificado en descargas..."
    )

    Scaffold(
        topBar = {
            EliteTopAppBar(
                title = "REPORTES PRE/POST SCAN",
                onBackClick = { navController.popBackStack() },
                backgroundColor = MeetColors.backgroundDark
            )
        },
        containerColor = MeetColors.backgroundDark
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Main Content Scrollable Dashboard
            if (!isGenerating && reportFile == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "GENERADOR DE REPORTES CERTIFICADOS",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Text(
                        text = "Crea documentos PDF profesionales para clientes o registro técnico interno con diseño Elysium personalizado.",
                        color = MeetColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp).align(Alignment.Start)
                    )

                    // Vista previa en tiempo real
                    Text(
                        text = "VISTA PREVIA DEL DOCUMENTO",
                        color = MeetColors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
                    )

                    DocumentPreviewCard(
                        theme = selectedTheme,
                        scanMode = scanMode,
                        workshopName = if (includeBranding && workshopName.isNotBlank()) workshopName else "Elysium Vanguard CLINIC",
                        healthScore = healthScore,
                        includeGraphs = includeGraphs,
                        includePredictive = includePredictive,
                        includeDtcs = includeDtcs
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Scan Mode Selector
                    Text(
                        text = "TIPO DE ESCANEO / REPORTE",
                        color = MeetColors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScanModeButton(
                            text = "PRE-REPARACIÓN (PRE-SCAN)",
                            selected = scanMode == "PRE_SCAN",
                            onClick = { scanMode = "PRE_SCAN" },
                            modifier = Modifier.weight(1f)
                        )
                        ScanModeButton(
                            text = "POST-REPARACIÓN (POST-SCAN)",
                            selected = scanMode == "POST_SCAN",
                            onClick = { scanMode = "POST_SCAN" },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Themes selector
                    Text(
                        text = "TEMA VISUAL DE LA PLANTILLA",
                        color = MeetColors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeSelectorCard("ELYSIUM_CYAN", "Cyan", Color(0xFF00FFD4), selectedTheme == "ELYSIUM_CYAN", Modifier.weight(1f)) { selectedTheme = "ELYSIUM_CYAN" }
                        ThemeSelectorCard("CARBON_RED", "Rojo", Color(0xFFFF3333), selectedTheme == "CARBON_RED", Modifier.weight(1f)) { selectedTheme = "CARBON_RED" }
                        ThemeSelectorCard("CLASSIC_DARK", "Oro", Color(0xFFFFB300), selectedTheme == "CLASSIC_DARK", Modifier.weight(1f)) { selectedTheme = "CLASSIC_DARK" }
                        ThemeSelectorCard("PRINTER_FRIENDLY", "Tinta", Color.White, selectedTheme == "PRINTER_FRIENDLY", Modifier.weight(1f)) { selectedTheme = "PRINTER_FRIENDLY" }
                    }

                    // Customizable sections checkboxes
                    EliteCard(
                        backgroundColor = MeetColors.cardBackground,
                        borderColor = MeetColors.borderSubtle,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("SECCIONES A INCLUIR EN EL PDF", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            CheckboxRow(label = "Incluir códigos de error activos (DTCs)", checked = includeDtcs, onCheckedChange = { includeDtcs = it })
                            CheckboxRow(label = "Análisis IA y Conclusiones Predictivas", checked = includeAi, onCheckedChange = { includeAi = it })
                            CheckboxRow(label = "Gráficas de Telemetría OBD en tiempo real", checked = includeGraphs, onCheckedChange = { includeGraphs = it })
                            CheckboxRow(label = "Score de subsistemas y alertas predictivas", checked = includePredictive, onCheckedChange = { includePredictive = it })
                            CheckboxRow(label = "Cabecera personalizada del Taller", checked = includeBranding, onCheckedChange = { includeBranding = it })
                        }
                    }

                    // Workshop branding editor expandable card
                    EliteCard(
                        backgroundColor = MeetColors.cardBackground,
                        borderColor = if (showWorkshopEditor) MeetColors.electricBlue else MeetColors.borderSubtle,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { showWorkshopEditor = !showWorkshopEditor },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AnimatedNeonIcon(Icons.Default.Settings, contentDescription = "Taller", tint = MeetColors.electricBlue, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("PERSONALIZACIÓN DEL TALLER (CABECERA)", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                }
                                AnimatedNeonIcon(
                                    imageVector = if (showWorkshopEditor) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Expand",
                                    tint = Color.White
                                )
                            }

                            AnimatedVisibility(
                                visible = showWorkshopEditor,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(
                                        value = workshopName,
                                        onValueChange = { workshopName = it },
                                        label = { Text("Nombre del Taller") },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MeetColors.electricBlue,
                                            unfocusedBorderColor = MeetColors.borderSubtle,
                                            focusedLabelColor = MeetColors.electricBlue,
                                            unfocusedLabelColor = MeetColors.textSecondary,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = workshopAddress,
                                        onValueChange = { workshopAddress = it },
                                        label = { Text("Dirección") },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MeetColors.electricBlue,
                                            unfocusedBorderColor = MeetColors.borderSubtle,
                                            focusedLabelColor = MeetColors.electricBlue,
                                            unfocusedLabelColor = MeetColors.textSecondary,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = workshopPhone,
                                        onValueChange = { workshopPhone = it },
                                        label = { Text("Teléfono de Contacto") },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MeetColors.electricBlue,
                                            unfocusedBorderColor = MeetColors.borderSubtle,
                                            focusedLabelColor = MeetColors.electricBlue,
                                            unfocusedLabelColor = MeetColors.textSecondary,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = workshopEmail,
                                        onValueChange = { workshopEmail = it },
                                        label = { Text("Email de Contacto") },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MeetColors.electricBlue,
                                            unfocusedBorderColor = MeetColors.borderSubtle,
                                            focusedLabelColor = MeetColors.electricBlue,
                                            unfocusedLabelColor = MeetColors.textSecondary,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    }

                    EliteButton(
                        text = "GENERAR REPORTE CERTIFICADO",
                        onClick = {
                            coroutineScope.launch {
                                isGenerating = true
                                currentStepIndex = 0
                                for (i in 0 until compilationSteps.size) {
                                    delay(650L) // Wait slightly for step logging
                                    currentStepIndex = i
                                }
                                val generatedFile = withContext(Dispatchers.IO) {
                                    // Save changes to SharedPreferences
                                    sharedPrefs.edit().apply {
                                        putString("workshop_name", workshopName)
                                        putString("workshop_address", workshopAddress)
                                        putString("workshop_phone", workshopPhone)
                                        putString("workshop_email", workshopEmail)
                                        apply()
                                    }

                                    // Build mock trip or fetch current
                                    val currentTripEntity = viewModel.getCurrentTrip()
                                    val currentTrip = if (currentTripEntity != null) {
                                        com.elysium369.meet.data.supabase.Trip(
                                            id = currentTripEntity.id,
                                            user_id = "guest",
                                            vehicle_id = currentTripEntity.vehicleId,
                                            session_id = currentTripEntity.sessionId,
                                            started_at = currentTripEntity.startedAt,
                                            ended_at = currentTripEntity.endedAt ?: System.currentTimeMillis(),
                                            distance_km = currentTripEntity.distanceKm,
                                            duration_seconds = currentTripEntity.durationSeconds,
                                            avg_speed_kmh = currentTripEntity.avgSpeedKmh,
                                            max_speed_kmh = currentTripEntity.maxSpeedKmh,
                                            max_rpm = currentTripEntity.maxRpm,
                                            avg_rpm = currentTripEntity.avgRpm,
                                            max_temp_c = currentTripEntity.maxTempC,
                                            fuel_efficiency = currentTripEntity.fuelEfficiency,
                                            eco_score = currentTripEntity.ecoScore,
                                            gps_track_json = currentTripEntity.gpsTrackJson
                                        )
                                    } else {
                                        com.elysium369.meet.data.supabase.Trip(
                                            id = System.currentTimeMillis().toString(),
                                            user_id = "guest",
                                            vehicle_id = viewModel.selectedVehicle.value?.id ?: "unknown",
                                            session_id = "temp",
                                            started_at = System.currentTimeMillis(),
                                            ended_at = System.currentTimeMillis(),
                                            distance_km = 0f,
                                            duration_seconds = 0,
                                            avg_speed_kmh = 0f,
                                            max_speed_kmh = viewModel.liveData.value["010D"] ?: 0f,
                                            max_rpm = viewModel.liveData.value["010C"] ?: 0f,
                                            avg_rpm = 0f,
                                            max_temp_c = viewModel.liveData.value["0105"] ?: 0f,
                                            fuel_efficiency = 0f,
                                            eco_score = 0,
                                            gps_track_json = null
                                        )
                                    }
                                    
                                    val vehicleInfo = viewModel.selectedVehicle.value?.let {
                                        "${it.year} ${it.make} ${it.model} — VIN: ${it.vin}"
                                    } ?: "Vehículo sin identificar"

                                    generator.generatePdfReport(
                                        trip = currentTrip,
                                        dtcs = activeDtcs,
                                        aiAnalysis = if (activeDtcs.isEmpty()) "El sistema de a bordo no reporta fallas de motor activas. Análisis predictivo indica rendimiento estable." else "Se detectaron fallas de motor activas. Recomendación de diagnóstico exhaustivo inmediato.",
                                        vehicleDetails = vehicleInfo,
                                        telemetryHistory = viewModel.telemetryHistory.value,
                                        anomalies = viewModel.anomalousPids.value,
                                        healthScore = healthScore,
                                        maintenanceAlerts = viewModel.maintenanceAlerts.value,
                                        predictiveReport = predictiveReport,
                                        themeName = selectedTheme,
                                        includeDtcs = includeDtcs,
                                        includeAi = includeAi,
                                        includeGraphs = includeGraphs,
                                        includePredictive = includePredictive,
                                        includeBranding = includeBranding
                                    )
                                }
                                isGenerating = false
                                reportFile = generatedFile
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    )
                }
            } else if (isGenerating) {
                // Compile console animation screen
                Box(
                    modifier = Modifier.fillMaxSize().background(MeetColors.backgroundDark).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Glowing compiler animation
                        val infiniteTransition = rememberInfiniteTransition()
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 0.85f,
                            targetValue = 1.15f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            )
                        )
                        
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(RoundedCornerShape(45.dp))
                                .border(2.dp, MeetColors.neonGreen.copy(alpha = pulseScale.coerceIn(0f, 1f)), RoundedCornerShape(45.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedNeonIcon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Compiling",
                                tint = MeetColors.neonGreen,
                                modifier = Modifier.size(45.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            text = "COMPILANDO TELEMETRÍA Elysium Vanguard ELITE",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Generando PDF con temática ${selectedTheme.replace("_", " ")}",
                            color = MeetColors.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                        )

                        // Progress bar matching selected theme color
                        val barColor = when (selectedTheme) {
                            "CARBON_RED" -> Color(0xFFFF3333)
                            "CLASSIC_DARK" -> Color(0xFFFFB300)
                            else -> Color(0xFF00FFD4)
                        }
                        
                        LinearProgressIndicator(
                            progress = { (currentStepIndex + 1f) / compilationSteps.size },
                            color = barColor,
                            trackColor = MeetColors.borderSubtle,
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Console layout displaying steps
                        EliteCard(
                            backgroundColor = Color(0xFF04070E),
                            borderColor = MeetColors.borderSubtle,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(160.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp).fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (i in 0..currentStepIndex) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (i == currentStepIndex) "⚡" else "✓",
                                            color = if (i == currentStepIndex) barColor else MeetColors.neonGreen,
                                            fontSize = 11.sp,
                                            modifier = Modifier.width(16.dp)
                                        )
                                        Text(
                                            text = compilationSteps[i],
                                            color = if (i == currentStepIndex) Color.White else MeetColors.textSecondary,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (reportFile != null) {
                // Success screen
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AnimatedNeonIcon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = MeetColors.neonGreen,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "✅ REPORTE PDF GENERADO",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Documento firmado digitalmente por Elysium Vanguard AI y listo para enviar.",
                        color = MeetColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    EliteCard(
                        backgroundColor = MeetColors.cardBackground,
                        borderColor = MeetColors.neonGreen.copy(alpha = 0.3f),
                        glowColor = MeetColors.neonGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(reportFile?.name ?: "Report.pdf", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Guardado en descargas internas del dispositivo", color = MeetColors.textSecondary, style = MaterialTheme.typography.bodySmall)
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            EliteOutlinedButton(
                                text = "COMPARTIR POR WHATSAPP / CORREO",
                                onClick = { reportFile?.let { generator.shareReport(it) } },
                                color = MeetColors.neonGreen,
                                modifier = Modifier.fillMaxWidth()
                            )
                             
                            Spacer(modifier = Modifier.height(12.dp))
                             
                            EliteButton(
                                text = "IMPRIMIR REPORTE TÉRMICO (BT)",
                                onClick = {
                                    val list = printerManager.getPairedPrinters()
                                    if (list.isEmpty()) {
                                        android.widget.Toast.makeText(context, "No se encontraron impresoras Bluetooth vinculadas. Vincúlela en los ajustes del teléfono.", android.widget.Toast.LENGTH_LONG).show()
                                    } else {
                                        printerList = list
                                        showPrinterPickerDialog = true
                                    }
                                },
                                color = MeetColors.electricBlue,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Printer Picker Dialog
                    if (showPrinterPickerDialog) {
                        AlertDialog(
                            onDismissRequest = { showPrinterPickerDialog = false },
                            title = { Text("Seleccionar Impresora Bluetooth", color = Color.White, fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    printerList.forEach { device ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MeetColors.cardBackground)
                                                .clickable {
                                                    showPrinterPickerDialog = false
                                                    coroutineScope.launch {
                                                        isPrinting = true
                                                        printingStatus = "Iniciando..."
                                                        val vehicleInfo = viewModel.selectedVehicle.value?.let {
                                                            "${it.year} ${it.make} ${it.model} (VIN: ${it.vin})"
                                                        } ?: "Vehículo Sin Identificar"
                                                        
                                                        val summaryText = if (activeDtcs.isEmpty()) {
                                                            "El sistema de a bordo no reporta fallas de motor activas. Análisis predictivo indica rendimiento estable."
                                                        } else {
                                                            "Se detectaron fallas de motor activas. Recomendación de diagnóstico exhaustivo inmediato."
                                                        }
                                                        
                                                        val printData = com.elysium369.meet.core.print.PrintReportData(
                                                            workshopName = if (includeBranding && workshopName.isNotBlank()) workshopName else "Elysium Vanguard CLINIC",
                                                            workshopAddress = workshopAddress,
                                                            workshopPhone = workshopPhone,
                                                            workshopEmail = workshopEmail,
                                                            vehicleInfo = vehicleInfo,
                                                            dtcs = activeDtcs,
                                                            healthScore = healthScore,
                                                            summary = summaryText,
                                                            isPostScan = scanMode == "POST_SCAN"
                                                        )
                                                        
                                                        val success = printerManager.printReport(device, printData) { status ->
                                                            printingStatus = status
                                                        }
                                                        isPrinting = false
                                                        if (success) {
                                                            android.widget.Toast.makeText(context, "Reporte impreso con éxito!", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AnimatedNeonIcon(Icons.Default.Settings, contentDescription = null, tint = MeetColors.electricBlue)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                @SuppressLint("MissingPermission")
                                                val devName = device.name ?: "Impresora Genérica"
                                                Text(devName, color = Color.White, fontWeight = FontWeight.Bold)
                                                Text(device.address, color = MeetColors.textSecondary, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showPrinterPickerDialog = false }) {
                                    Text("Cancelar", color = MeetColors.textSecondary)
                                }
                            },
                            containerColor = MeetColors.backgroundDeep,
                            shape = RoundedCornerShape(16.dp)
                        )
                    }

                    // Printing Progress Dialog
                    if (isPrinting) {
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text("Imprimiendo Reporte", color = Color.White, fontWeight = FontWeight.Bold) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(color = MeetColors.electricBlue, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(printingStatus, color = Color.White)
                                }
                            },
                            confirmButton = {},
                            containerColor = MeetColors.backgroundDeep,
                            shape = RoundedCornerShape(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    ReportIntegrityCard(service = rememberReportHashingService())
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { reportFile = null }) {
                        Text("Configurar y generar otro reporte", color = MeetColors.electricBlue)
                    }
                }
            }
        }
    }
}

@Composable
fun ScanModeButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MeetColors.electricBlue.copy(alpha = 0.15f) else MeetColors.cardBackground)
            .border(1.dp, if (selected) MeetColors.electricBlue else MeetColors.borderSubtle, RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) MeetColors.electricBlue else MeetColors.textSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun ThemeSelectorCard(id: String, label: String, previewColor: Color, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MeetColors.cardBackground)
            .border(1.dp, if (selected) previewColor else MeetColors.borderSubtle, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(previewColor)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            )
            Text(
                text = label.uppercase(),
                color = if (selected) Color.White else MeetColors.textSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp
            )
        }
    }
}

@Composable
fun CheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp).clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MeetColors.electricBlue,
                uncheckedColor = MeetColors.textSecondary,
                checkmarkColor = Color.Black
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, color = MeetColors.textSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun DocumentPreviewCard(
    theme: String,
    scanMode: String,
    workshopName: String,
    healthScore: Int,
    includeGraphs: Boolean,
    includePredictive: Boolean,
    includeDtcs: Boolean
) {
    // Styling colors matching layout preview
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
        // Miniature page layout
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
                    
                    // Circular mini gauge
                    Canvas(modifier = Modifier.size(14.dp)) {
                        drawArc(
                            color = if (isLight) Color.LightGray else Color.DarkGray,
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawArc(
                            color = accentColor,
                            startAngle = 135f,
                            sweepAngle = 270f * (healthScore / 100f),
                            useCenter = false,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
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
                Box(modifier = Modifier.width(60.dp).height(4.dp).background(docSubText.copy(alpha = 0.5f)))
                Box(modifier = Modifier.width(80.dp).height(3.dp).background(docSubText.copy(alpha = 0.3f)))

                Spacer(modifier = Modifier.height(4.dp))
                // Mode text
                Text(
                    text = if (scanMode == "PRE_SCAN") "REPORT PRE-SCAN" else "REPORT POST-SCAN",
                    color = accentColor,
                    fontSize = 5.sp,
                    fontWeight = FontWeight.Bold
                )

                // Stats block mockup
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.weight(1f).height(12.dp).background(accentColor.copy(alpha = 0.08f)).border(0.5.dp, accentColor.copy(alpha = 0.2f)))
                    Box(modifier = Modifier.weight(1f).height(12.dp).background(accentColor.copy(alpha = 0.08f)).border(0.5.dp, accentColor.copy(alpha = 0.2f)))
                    Box(modifier = Modifier.weight(1f).height(12.dp).background(accentColor.copy(alpha = 0.08f)).border(0.5.dp, accentColor.copy(alpha = 0.2f)))
                }

                // Subsystem bars preview
                if (includePredictive) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(modifier = Modifier.width(40.dp).height(3.dp).background(docText.copy(alpha = 0.8f)))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.width(20.dp).height(2.dp).background(docSubText.copy(alpha = 0.4f)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(modifier = Modifier.width(50.dp).height(2.dp).background(accentColor))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.width(20.dp).height(2.dp).background(docSubText.copy(alpha = 0.4f)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(modifier = Modifier.width(42.dp).height(2.dp).background(accentColor))
                    }
                }

                // Waveform drawing mockup
                if (includeGraphs) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .background(if (isLight) Color(0xFFFAFAFA) else Color(0xFF0F1420))
                            .border(0.5.dp, if (isLight) Color.LightGray else Color.DarkGray)
                    ) {
                        val path = Path()
                        val stepX = size.width / 8f
                        path.moveTo(0f, size.height / 2f)
                        path.lineTo(stepX, size.height * 0.3f)
                        path.lineTo(stepX * 2, size.height * 0.7f)
                        path.lineTo(stepX * 3, size.height * 0.1f)
                        path.lineTo(stepX * 4, size.height * 0.8f)
                        path.lineTo(stepX * 5, size.height * 0.4f)
                        path.lineTo(stepX * 6, size.height * 0.6f)
                        path.lineTo(stepX * 7, size.height * 0.2f)
                        path.lineTo(size.width, size.height / 2f)
                        
                        drawPath(
                            path = path,
                            color = accentColor,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }

                // DTC lines
                if (includeDtcs) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(3.dp).background(Color.Red))
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(modifier = Modifier.width(65.dp).height(2.dp).background(docSubText.copy(alpha = 0.6f)))
                    }
                }
            }
        }
    }
}
