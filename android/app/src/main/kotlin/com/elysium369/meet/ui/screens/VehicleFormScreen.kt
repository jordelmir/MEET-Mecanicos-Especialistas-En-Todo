package com.elysium369.meet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleFormScreen(
    navController: NavController,
    viewModel: ObdViewModel,
    vehicleId: String? = null
) {
    val lang by viewModel.language.collectAsState()
    
    // Translation helper
    fun t(es: String, en: String): String = if (lang == "es") es else en

    // Basic Info
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    
    // Detailed Spec
    var engineDisplacement by remember { mutableStateOf("") }
    var engineTech by remember { mutableStateOf("") }
    var transmission by remember { mutableStateOf("") }
    var transmissionType by remember { mutableStateOf("") }
    var fuelType by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var vin by remember { mutableStateOf("") }
    
    // Dropdown state
    var transmissionExpanded by remember { mutableStateOf(false) }
    val transmissionOptions = listOf(
        t("Automática (AT)", "Automatic (AT)"),
        t("Manual (MT)", "Manual (MT)"),
        t("Doble Embrague (DSG/DCT)", "Dual Clutch (DSG/DCT)"),
        t("Continua (CVT)", "Continuous (CVT)"),
        t("Automatizada (AMT)", "Automated Manual (AMT)"),
        t("Tiptronic / Shiftable", "Tiptronic / Shiftable")
    )
    
    var fuelTypeExpanded by remember { mutableStateOf(false) }
    val fuelTypeOptions = listOf(
        t("Gasolina / Bencina", "Gasoline"),
        t("Diésel / Gasóleo", "Diesel"),
        t("Híbrido (HEV/PHEV)", "Hybrid"),
        t("Eléctrico (EV)", "Electric (EV)"),
        t("GNC (Gas Natural)", "CNG"),
        t("GLP (Gas Licuado)", "LPG"),
        t("Flex Fuel (E85)", "Flex Fuel")
    )

    var engineTechExpanded by remember { mutableStateOf(false) }
    val engineTechOptions = listOf(
        "NATURAL ASPIRATED",
        "TURBOCHARGED",
        "SUPERCHARGED",
        "VVT / VTEC / VANOS",
        "GDI / DIRECT INJECTION",
        "HYBRID DRIVE",
        "V6 / V8 / W12"
    )

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var isSaving by remember { mutableStateOf(false) }
    var saveSuccess by remember { mutableStateOf(false) }

    // Brand Colors
    val neonCyan = Color(0xFF00E5FF)
    val neonMagenta = Color(0xFFD500F9)
    val darkBackground = Color(0xFF070B14)
    val surfaceColor = Color(0xFF111726)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            text = if (vehicleId == null) t("REGISTRAR VEHÍCULO", "REGISTER VEHICLE") else t("EDITAR VEHÍCULO", "EDIT VEHICLE"),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = t("PERFIL DE INGENIERÍA MAESTRA", "MASTER ENGINEERING PROFILE"),
                            color = neonCyan.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = neonCyan)
                    }
                },
                actions = {
                    // Language Toggle
                    Surface(
                        onClick = { viewModel.setLanguage(if (lang == "es") "en" else "es") },
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 12.dp).border(1.dp, neonCyan.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = lang.uppercase(),
                                color = neonCyan,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = darkBackground)
            )
        },
        containerColor = darkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Hero Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(neonCyan.copy(alpha = 0.05f))
                    .border(1.dp, neonCyan.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(neonCyan.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🏎️", fontSize = 24.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            t("ANÁLISIS DE PRECISIÓN OBD2", "OBD2 PRECISION ANALYSIS"),
                            color = neonCyan,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            t("Cada detalle permite a la IA MEET ajustar los algoritmos de diagnóstico para tu motor específico.", 
                              "Every detail allows MEET AI to tune diagnostic algorithms for your specific engine."),
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Section: DNA del Vehículo
            FormSectionHeader(t("ADN DEL VEHÍCULO", "VEHICLE DNA"), neonCyan)
            
            OutlinedTextField(
                value = make,
                onValueChange = { make = it },
                label = { Text(t("Marca (Ej. BMW, Toyota, Ford)", "Make (e.g. BMW, Toyota, Ford)")) },
                modifier = Modifier.fillMaxWidth(),
                colors = cyberpunkTextFieldColors(neonCyan),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text(t("Modelo", "Model")) },
                    modifier = Modifier.weight(1f),
                    colors = cyberpunkTextFieldColors(neonCyan),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = year,
                    onValueChange = { if (it.length <= 4) year = it },
                    label = { Text(t("Año", "Year")) },
                    modifier = Modifier.weight(0.6f),
                    colors = cyberpunkTextFieldColors(neonCyan),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            // Section: Configuración del Tren Motriz
            FormSectionHeader(t("CONFIGURACIÓN TREN MOTRIZ", "POWERTRAIN CONFIGURATION"), neonMagenta)

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = engineDisplacement,
                    onValueChange = { engineDisplacement = it },
                    label = { Text(t("Cilindrada (cc)", "Displacement (cc)")) },
                    placeholder = { Text("1600") },
                    modifier = Modifier.weight(1f),
                    colors = cyberpunkTextFieldColors(neonMagenta),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                
                ExposedDropdownMenuBox(
                    expanded = engineTechExpanded,
                    onExpandedChange = { engineTechExpanded = !engineTechExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = engineTech,
                        onValueChange = { engineTech = it },
                        label = { Text(t("Tecnología", "Technology")) },
                        placeholder = { Text("TURBO / VVT") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = engineTechExpanded) },
                        colors = cyberpunkTextFieldColors(neonMagenta),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = engineTechExpanded,
                        onDismissRequest = { engineTechExpanded = false },
                        modifier = Modifier.background(surfaceColor)
                    ) {
                        engineTechOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option, color = Color.White) },
                                onClick = {
                                    engineTech = option
                                    engineTechExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Transmission Dropdown
                ExposedDropdownMenuBox(
                    expanded = transmissionExpanded,
                    onExpandedChange = { transmissionExpanded = !transmissionExpanded },
                    modifier = Modifier.weight(1.2f)
                ) {
                    OutlinedTextField(
                        value = transmission,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(t("Transmisión", "Transmission")) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = transmissionExpanded) },
                        colors = cyberpunkTextFieldColors(neonMagenta),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = transmissionExpanded,
                        onDismissRequest = { transmissionExpanded = false },
                        modifier = Modifier.background(surfaceColor)
                    ) {
                        transmissionOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option, color = Color.White) },
                                onClick = {
                                    transmission = option
                                    transmissionExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = transmissionType,
                    onValueChange = { transmissionType = it.uppercase() },
                    label = { Text(t("Sub-Tipo", "Sub-Type")) },
                    placeholder = { Text("7DSG / 6AT") },
                    modifier = Modifier.weight(0.8f),
                    colors = cyberpunkTextFieldColors(neonMagenta),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            // Fuel Type
            ExposedDropdownMenuBox(
                expanded = fuelTypeExpanded,
                onExpandedChange = { fuelTypeExpanded = !fuelTypeExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = fuelType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(t("Tipo de Combustible", "Fuel Type")) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fuelTypeExpanded) },
                    colors = cyberpunkTextFieldColors(neonMagenta),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = fuelTypeExpanded,
                    onDismissRequest = { fuelTypeExpanded = false },
                    modifier = Modifier.background(surfaceColor)
                ) {
                    fuelTypeOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, color = Color.White) },
                            onClick = {
                                fuelType = option
                                fuelTypeExpanded = false
                            }
                        )
                    }
                }
            }

            // Identifiers
            FormSectionHeader(t("IDENTIFICADORES LEGALES", "LEGAL IDENTIFIERS"), Color.White)

            OutlinedTextField(
                value = plate,
                onValueChange = { plate = it.uppercase() },
                label = { Text(t("Placa / Matrícula / Patente", "License Plate")) },
                modifier = Modifier.fillMaxWidth(),
                colors = cyberpunkTextFieldColors(Color.White),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = vin,
                    onValueChange = { vin = it.uppercase() },
                    label = { Text(t("VIN (Número de Chasis)", "VIN (Chassis Number)")) },
                    modifier = Modifier.weight(1f),
                    colors = cyberpunkTextFieldColors(Color.White),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val readVin = viewModel.readVin()
                            if (readVin != null) vin = readVin
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(56.dp)
                        .border(1.dp, neonCyan, RoundedCornerShape(12.dp))
                ) {
                    Text("VIN SCAN", color = neonCyan, fontWeight = FontWeight.Black, fontSize = 10.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val isFormValid = make.isNotBlank() && model.isNotBlank() && year.length >= 4

            Button(
                onClick = {
                    if (isFormValid && !isSaving) {
                        isSaving = true
                        viewModel.saveVehicle(make, model, year, engineDisplacement, engineTech, transmission, transmissionType, fuelType, plate, vin)
                        coroutineScope.launch {
                            saveSuccess = true
                            delay(1200) // Show success animation
                            navController.popBackStack()
                        }
                    }
                },
                enabled = isFormValid && !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .border(
                        1.dp, 
                        if (isFormValid) neonCyan else Color.White.copy(alpha = 0.1f), 
                        RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFormValid) neonCyan.copy(alpha = 0.15f) else Color.Transparent,
                    disabledContainerColor = Color.Transparent
                )
            ) {
                Text(
                    t("GUARDAR EN GARAGE PROFESIONAL", "SAVE TO PROFESSIONAL GARAGE"),
                    color = if (isFormValid) neonCyan else Color.Gray,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }

        // ─── Save Success Overlay ───
        AnimatedVisibility(
            visible = saveSuccess,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                val pulseScale by rememberInfiniteTransition(label = "successPulse").animateFloat(
                    initialValue = 0.9f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse"
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "✅",
                        fontSize = 64.sp,
                        modifier = Modifier.scale(pulseScale)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "$make $model",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "AGREGADO AL GARAGE",
                        color = Color(0xFF39FF14),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}

@Composable
fun FormSectionHeader(title: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = color,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f).height(1.dp).background(color.copy(alpha = 0.2f)))
    }
}

@Composable
fun cyberpunkTextFieldColors(accentColor: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = accentColor,
    unfocusedBorderColor = accentColor.copy(alpha = 0.3f),
    focusedLabelColor = accentColor,
    unfocusedLabelColor = Color.Gray,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = accentColor
)

