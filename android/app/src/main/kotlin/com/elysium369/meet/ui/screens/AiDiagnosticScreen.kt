package com.elysium369.meet.ui.screens

import com.elysium369.meet.ui.components.AnimatedNeonIcon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteIconButton
import com.elysium369.meet.ui.components.EliteScrollContainer
import com.elysium369.meet.ui.components.eliteScrollbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDiagnosticScreen(
    dtcCode: String,
    onBack: () -> Unit,
    viewModel: com.elysium369.meet.ui.ObdViewModel
) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("meet_prefs", android.content.Context.MODE_PRIVATE)
    
    var provider by remember { mutableStateOf(sharedPrefs.getString("ai_provider", "Google Gemini") ?: "Google Gemini") }
    var apiKey by remember { mutableStateOf(sharedPrefs.getString("ai_api_key", "") ?: "") }
    var baseUrl by remember { mutableStateOf(sharedPrefs.getString("ai_base_url", "") ?: "") }
    var isConfigOpen by remember { mutableStateOf(false) }

    var aiResponse by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val anomalousPids by viewModel.anomalousPids.collectAsState()

    val severity = remember(dtcCode) {
        when {
            dtcCode.startsWith("P03") || dtcCode.startsWith("P02") || dtcCode.startsWith("P00") -> "CRITICAL"
            dtcCode.startsWith("P01") || dtcCode.startsWith("P07") -> "HIGH"
            dtcCode.startsWith("P04") || dtcCode.startsWith("P05") -> "MODERATE"
            dtcCode.isEmpty() -> "INFORMATIVE"
            else -> "HIGH"
        }
    }

    val severityScore = when (severity) {
        "CRITICAL" -> 0.95f
        "HIGH" -> 0.75f
        "MODERATE" -> 0.50f
        else -> 0.25f
    }

    LaunchedEffect(dtcCode) {
        if (dtcCode.isNotEmpty() && (apiKey.isNotEmpty() || provider == "Local/Ollama")) {
            isLoading = true
            aiResponse = viewModel.consultAi(apiKey.takeIf { it.isNotBlank() }, baseUrl.takeIf { it.isNotBlank() }, listOf(dtcCode))
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            EliteTopAppBar(
                title = "Elysium AI Diagnostics",
                onBackClick = onBack,
                backgroundColor = MeetColors.backgroundDark,
                actions = {
                    EliteIconButton(
                        icon = { AnimatedNeonIcon(Icons.Default.Settings, contentDescription = "Configuración", tint = MeetColors.electricBlue) },
                        onClick = { isConfigOpen = !isConfigOpen }
                    )
                }
            )
        },
        containerColor = MeetColors.backgroundDark
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MeetColors.carbonGradient)
                .padding(horizontal = 16.dp)
        ) {
            // Configuration panel
            AnimatedVisibility(visible = isConfigOpen) {
                EliteCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "CONFIGURACIÓN DE MOTOR IA", 
                            color = MeetColors.electricBlue, 
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Providers
                        val providers = listOf("Google Gemini", "OpenAI", "Anthropic", "Local/Ollama")
                        var expanded by remember { mutableStateOf(false) }
                        
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = provider,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Proveedor de IA", color = MeetColors.textSecondary) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = MeetColors.electricBlue,
                                    unfocusedBorderColor = MeetColors.borderSubtle
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                providers.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p, color = Color.White) },
                                        onClick = {
                                            provider = p
                                            expanded = false
                                        },
                                        modifier = Modifier.background(MeetColors.backgroundDark)
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("API Key (En blanco para Local)", color = MeetColors.textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MeetColors.electricBlue,
                                unfocusedBorderColor = MeetColors.borderSubtle
                            )
                        )
                        
                        if (provider == "Local/Ollama" || provider == "OpenAI") {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = baseUrl,
                                onValueChange = { baseUrl = it },
                                label = { Text("URL Base Customizada (Ollama/Custom)", color = MeetColors.textSecondary) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = MeetColors.electricBlue,
                                    unfocusedBorderColor = MeetColors.borderSubtle
                                )
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { 
                                apiKey = ""
                                baseUrl = ""
                                sharedPrefs.edit()
                                    .remove("ai_api_key")
                                    .remove("ai_base_url")
                                    .apply()
                            }) {
                                Text("Limpiar", color = MeetColors.error)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { 
                                    sharedPrefs.edit()
                                        .putString("ai_provider", provider)
                                        .putString("ai_api_key", apiKey)
                                        .putString("ai_base_url", baseUrl)
                                        .apply()
                                    isConfigOpen = false
                                    if (dtcCode.isNotEmpty() && (apiKey.isNotEmpty() || provider == "Local/Ollama")) {
                                        coroutineScope.launch {
                                            isLoading = true
                                            aiResponse = viewModel.consultAi(apiKey.takeIf { it.isNotBlank() }, baseUrl.takeIf { it.isNotBlank() }, listOf(dtcCode))
                                            isLoading = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.electricBlue)
                            ) {
                                Text("Guardar", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            val scrollState = rememberScrollState()

            Box(modifier = Modifier.weight(1f)) {
                EliteScrollContainer(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .eliteScrollbar(scrollState)
                            .verticalScroll(scrollState)
                            .padding(vertical = 12.dp)
                    ) {
                        if (isLoading) {
                            Spacer(modifier = Modifier.height(24.dp))
                            CyberConsoleProgressStepper()
                        } else {
                            if (dtcCode.isNotEmpty()) {
                                // Gauge & Severity Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UrgencyGauge(
                                        severity = severity,
                                        score = severityScore,
                                        dtcCode = dtcCode,
                                        modifier = Modifier.size(130.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "CÓDIGO DE FALLA ACTIVADO",
                                            color = MeetColors.textSecondary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            dtcCode,
                                            color = Color.White,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        val severityLabel = when (severity) {
                                            "CRITICAL" -> "CRÍTICO - DETENER CONDUCCIÓN"
                                            "HIGH" -> "ALTO - REVISIÓN RECOMENDADA"
                                            "MODERATE" -> "MODERADO - ANÁLISIS PREVENTIVO"
                                            else -> "INFORMATIVO - NOMINAL"
                                        }
                                        val severityColor = when (severity) {
                                            "CRITICAL" -> MeetColors.error
                                            "HIGH" -> Color(0xFFFFB300)
                                            "MODERATE" -> MeetColors.electricBlue
                                            else -> MeetColors.neonGreen
                                        }

                                        Text(
                                            severityLabel,
                                            color = severityColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }

                                // Anomalous PIDs (Suspected Components) Section
                                if (anomalousPids.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "COMPONENTES BAJO SOSPECHA (TELEMETRÍA)",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        anomalousPids.forEach { anomaly ->
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color(0xFF0F0E13))
                                                    .border(1.dp, MeetColors.error.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                                    .padding(12.dp)
                                            ) {
                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        AnimatedNeonIcon(Icons.Default.Info, contentDescription = null, tint = MeetColors.error, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("PID ${anomaly.pid}", color = MeetColors.error, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(anomaly.insight, color = Color.White, fontSize = 10.sp, lineHeight = 14.sp)
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                            }

                            // AI Response Box
                            EliteCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "VEREDICTO CLÍNICO DE IA", 
                                            color = Color.White, 
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            letterSpacing = 0.5.sp
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MeetColors.electricBlue.copy(alpha = 0.2f))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                provider.uppercase(),
                                                color = MeetColors.electricBlue,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    if (apiKey.isEmpty() && provider != "Local/Ollama") {
                                        Text(
                                            "⚠️ No has configurado tu API Key. Toca el engranaje superior para ingresar las credenciales de tu proveedor de IA.",
                                            color = MeetColors.warning,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    } else if (aiResponse != null) {
                                        Text(
                                            text = aiResponse.orEmpty(),
                                            color = MeetColors.textPrimary,
                                            fontSize = 14.sp,
                                            lineHeight = 22.sp
                                        )
                                    } else if (dtcCode.isEmpty()) {
                                        Text(
                                            "Ningún código DTC proporcionado para análisis profundo. Ve a la sección de fallas en vivo y selecciona un código para iniciar el análisis.",
                                            color = MeetColors.textSecondary,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // PDF Export button
            Button(
                onClick = { viewModel.generateFullReport(aiResponse) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .border(
                        1.dp,
                        if (aiResponse != null) MeetColors.neonGreen else MeetColors.textSecondary.copy(alpha = 0.3f),
                        RoundedCornerShape(12.dp)
                    ),
                shape = RoundedCornerShape(12.dp),
                enabled = aiResponse != null
            ) {
                Text(
                    "EXPORTAR REPORTE IA (PDF) 📄",
                    color = if (aiResponse != null) MeetColors.neonGreen else MeetColors.textSecondary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun UrgencyGauge(
    severity: String,
    score: Float,
    dtcCode: String,
    modifier: Modifier = Modifier
) {
    val sweepAngle = 240f
    val startAngle = 150f

    val strokeColor = when (severity) {
        "CRITICAL" -> MeetColors.error
        "HIGH" -> Color(0xFFFFB300)
        "MODERATE" -> MeetColors.electricBlue
        else -> MeetColors.neonGreen
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val strokeWidth = 10.dp.toPx()
            val arcSize = Size(width - strokeWidth * 2, height - strokeWidth * 2)

            // Draw track
            drawArc(
                color = Color.White.copy(alpha = 0.05f),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(strokeWidth, strokeWidth),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Draw filled arc
            drawArc(
                color = strokeColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle * score,
                useCenter = false,
                topLeft = Offset(strokeWidth, strokeWidth),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Inside text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = dtcCode,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp
            )
            Text(
                text = "GRAVEDAD",
                color = MeetColors.textSecondary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun CyberConsoleProgressStepper() {
    var stepIndex by remember { mutableIntStateOf(0) }
    
    val steps = listOf(
        "[01/04] Sincronizando datos de DTC y Freeze Frame...",
        "[02/04] Analizando correlación con PIDs en vivo...",
        "[03/04] Consultando base de datos de causas comunes...",
        "[04/04] Formateando veredicto clínico..."
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            if (stepIndex < steps.size - 1) {
                stepIndex++
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF040A12))
            .border(1.dp, MeetColors.electricBlue.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MeetColors.electricBlue
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SISTEMA DE ANÁLISIS DE FALLAS ELYSIUM ACTIVE",
                color = MeetColors.electricBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        for (i in 0..stepIndex) {
            val isCurrent = i == stepIndex
            Text(
                text = if (isCurrent) "${steps[i]} ▮" else "${steps[i]} ✓",
                color = if (isCurrent) MeetColors.neonGreen else Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

