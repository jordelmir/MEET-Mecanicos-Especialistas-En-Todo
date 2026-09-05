package com.elysium369.meet.ui.screens

import com.elysium369.meet.ui.navigation.backOrHome
import com.elysium369.meet.ui.navigation.navigateTopLevel

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.core.obd.ObdState
import com.elysium369.meet.core.vanguard.DiagnosticTelemetryConsent
import com.elysium369.meet.core.vanguard.VanguardPrivacyGuard
import com.elysium369.meet.data.remote.SupabaseModule
import com.elysium369.meet.identity.PrincipalProvisioningStore
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.AnimatedIconPreset
import com.elysium369.meet.ui.components.AnimatedNeonIcon
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.components.PhantomSectionHeader
import com.elysium369.meet.ui.components.rememberAnimatedIconStyle
import com.elysium369.meet.ui.components.setAnimatedIconEnabled
import com.elysium369.meet.ui.components.setAnimatedIconIntensity
import com.elysium369.meet.ui.components.setAnimatedIconPreset
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.jan.supabase.gotrue.auth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: ObdViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showThemeCustomizer by remember { mutableStateOf(false) }
    val diagnosticPrivacyGuard = remember { VanguardPrivacyGuard() }
    var diagnosticTelemetryConsent by remember {
        mutableStateOf(diagnosticPrivacyGuard.diagnosticTelemetryConsent(context))
    }

    // --- Live state from ViewModel ---
    val forceCloneMode by viewModel.forceCloneMode.collectAsState()
    val fusedSpeedEnabled by viewModel.fusedSpeedEnabled.collectAsState()
    val aiConfig by viewModel.aiConfig.collectAsState()

    // --- Local AI config editing state ---
    var selectedProvider by remember { mutableStateOf(aiConfig.provider) }
    var apiKeyInput by remember { mutableStateOf(aiConfig.apiKey) }
    var endpointInput by remember { mutableStateOf(aiConfig.endpoint) }
    var modelNameInput by remember { mutableStateOf(aiConfig.modelName) }
    var showApiKey by remember { mutableStateOf(false) }
    var showSavedBanner by remember { mutableStateOf(false) }
    var providerExpanded by remember { mutableStateOf(false) }

    // --- Local Workshop Profile state ---
    val prefs = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
    var workshopName by remember { mutableStateOf(prefs.getString("workshop_name", "") ?: "") }
    var workshopAddress by remember { mutableStateOf(prefs.getString("workshop_address", "") ?: "") }
    var workshopPhone by remember { mutableStateOf(prefs.getString("workshop_phone", "") ?: "") }
    var workshopEmail by remember { mutableStateOf(prefs.getString("workshop_email", "") ?: "") }
    var showWorkshopSaved by remember { mutableStateOf(false) }
    var voiceFeedbackEnabled by remember {
        mutableStateOf(prefs.getBoolean("voice_feedback_enabled", true))
    }
    val voiceCopilotEnabled by viewModel.voiceCopilotEnabled.collectAsState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleVoiceCopilot(true)
            Toast.makeText(context, "Copiloto por voz activado", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.toggleVoiceCopilot(false)
            Toast.makeText(context, "Permiso de micrófono denegado", Toast.LENGTH_LONG).show()
        }
    }

    // Auto-dismiss banners
    LaunchedEffect(showSavedBanner) {
        if (showSavedBanner) {
            delay(4000)
            showSavedBanner = false
        }
    }
    LaunchedEffect(showWorkshopSaved) {
        if (showWorkshopSaved) {
            delay(4000)
            showWorkshopSaved = false
        }
    }

    // Sync when aiConfig changes externally
    LaunchedEffect(aiConfig) {
        selectedProvider = aiConfig.provider
        apiKeyInput = aiConfig.apiKey
        endpointInput = aiConfig.endpoint
        modelNameInput = aiConfig.modelName
    }

    val providers = listOf(
        "gemini" to "Google Gemini",
        "openai" to "OpenAI (GPT)",
        "anthropic" to "Anthropic (Claude)",
        "ollama" to "Ollama (Local)",
        "mavis" to "Mavis / OpenAI-compatible",
        "custom" to "Custom Endpoint"
    )

    // Validations
    val isApiKeyInputValid = remember(selectedProvider, apiKeyInput) {
        isApiKeyValid(selectedProvider, apiKeyInput)
    }
    val isEndpointInputValid = remember(selectedProvider, endpointInput) {
        isEndpointValid(selectedProvider, endpointInput)
    }
    val isAiConfigSaveEnabled = remember(selectedProvider, apiKeyInput, endpointInput, isApiKeyInputValid, isEndpointInputValid) {
        isApiKeyInputValid && isEndpointInputValid
    }

    val isWorkshopNameInputValid = remember(workshopName) {
        workshopName.isBlank() || workshopName.trim().length >= 3
    }
    val isWorkshopPhoneInputValid = remember(workshopPhone) {
        isPhoneValid(workshopPhone)
    }
    val isWorkshopEmailInputValid = remember(workshopEmail) {
        isEmailValid(workshopEmail)
    }
    val isWorkshopSaveEnabled = remember(workshopName, workshopAddress, workshopPhone, workshopEmail, isWorkshopNameInputValid, isWorkshopPhoneInputValid, isWorkshopEmailInputValid) {
        isWorkshopNameInputValid && isWorkshopPhoneInputValid && isWorkshopEmailInputValid &&
                (workshopName.isNotBlank() || workshopAddress.isNotBlank() || workshopPhone.isNotBlank() || workshopEmail.isNotBlank())
    }

    Scaffold(
        topBar = {
            EliteTopAppBar(
                title = "Ajustes Avanzados\nConfiguración del Sistema",
                onBackClick = { navController.backOrHome() },
                backgroundColor = MeetColors.backgroundDark
            )
        },
        containerColor = MeetColors.backgroundDark
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ============================================================
            //  SECCIÓN 1: ADAPTADOR OBD2 & CONECTIVIDAD
            // ============================================================
            item {
                Column {
                    PhantomSectionHeader(label = "ADAPTADOR OBD2 Y CONEXIÓN", accentColor = MeetColors.neonGreen)
                    Spacer(modifier = Modifier.height(8.dp))
                    EliteCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = MeetColors.neonGreen,
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SettingsRow("Tipo de Conexión", "WiFi (192.168.0.10:35000)")

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Velocidad Ultra-Fluida (Waze)", color = Color.White, fontWeight = FontWeight.Medium)
                                    Text(
                                        if (fusedSpeedEnabled) "Activo — Fusión de Sensores (GPS + Acelerómetro)"
                                        else "Desactivado — lectura directa",
                                        color = if (fusedSpeedEnabled) MeetColors.neonGreen else MeetColors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = fusedSpeedEnabled,
                                    onCheckedChange = { viewModel.setFusedSpeedEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MeetColors.neonGreen,
                                        checkedTrackColor = MeetColors.neonGreen.copy(alpha = 0.3f),
                                        uncheckedThumbColor = MeetColors.textSecondary,
                                        uncheckedTrackColor = MeetColors.textSecondary.copy(alpha = 0.2f)
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Modo Clon Forzado", color = Color.White, fontWeight = FontWeight.Medium)
                                    Text(
                                        if (forceCloneMode) "Activo — adaptador tratado como clon"
                                        else "Desactivado — detección automática",
                                        color = if (forceCloneMode) MeetColors.warning else MeetColors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = forceCloneMode,
                                    onCheckedChange = { viewModel.setForceCloneMode(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MeetColors.warning,
                                        checkedTrackColor = MeetColors.warning.copy(alpha = 0.3f),
                                        uncheckedThumbColor = MeetColors.textSecondary,
                                        uncheckedTrackColor = MeetColors.textSecondary.copy(alpha = 0.2f)
                                    )
                                )
                            }

                            AnimatedVisibility(visible = forceCloneMode) {
                                EliteCard(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    backgroundColor = MeetColors.warning.copy(alpha = 0.1f),
                                    glowColor = MeetColors.warning,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        "⚠️ El sistema usará protocolos de compatibilidad para adaptadores genéricos ELM327. " +
                                        "Funciones avanzadas (STN, OBDLink) estarán deshabilitadas.",
                                        color = MeetColors.warning,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(12.dp),
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column {
                    PhantomSectionHeader(label = "PRIVACIDAD DIAGNÓSTICA", accentColor = MeetColors.electricBlue)
                    Spacer(modifier = Modifier.height(8.dp))
                    EliteCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = MeetColors.electricBlue,
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "La evidencia OBD permanece local. El envío remoto está desactivado por defecto y solo cambia con consentimiento explícito.",
                                color = MeetColors.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            )
                            DiagnosticTelemetryConsent.entries.forEach { option ->
                                val label = when (option) {
                                    DiagnosticTelemetryConsent.DISABLED -> "Solo local · no enviar"
                                    DiagnosticTelemetryConsent.ANONYMOUS_REDACTED -> "Telemetría anónima y redactada"
                                    DiagnosticTelemetryConsent.CONSENTED_REDACTED -> "Compartir diagnóstico redactado"
                                }
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            diagnosticPrivacyGuard.setDiagnosticTelemetryConsent(context, option)
                                            diagnosticTelemetryConsent = option
                                        }
                                        .border(
                                            1.dp,
                                            if (diagnosticTelemetryConsent == option) MeetColors.electricBlue else MeetColors.borderSubtle,
                                            RoundedCornerShape(8.dp),
                                        )
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = diagnosticTelemetryConsent == option,
                                        onClick = {
                                            diagnosticPrivacyGuard.setDiagnosticTelemetryConsent(context, option)
                                            diagnosticTelemetryConsent = option
                                        },
                                    )
                                    Text(label, color = Color.White, fontSize = 12.sp)
                                }
                            }
                            Text(
                                "VIN, MAC e IP se redactan o transforman antes de cualquier transmisión permitida.",
                                color = MeetColors.warning,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }

            // ============================================================
            //  SECCIÓN 1B: CONFIGURACIÓN DE AUDIO Y VOZ (ASISTENTE / COPILOTO)
            // ============================================================
            item {
                Column {
                    PhantomSectionHeader(label = "ASISTENTE Y AUDIO DE VOZ", accentColor = MeetColors.cyberCyan)
                    Spacer(modifier = Modifier.height(8.dp))
                    EliteCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = MeetColors.cyberCyan,
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Asistente de Voz Interactivo", color = Color.White, fontWeight = FontWeight.Medium)
                                    Text(
                                        if (voiceFeedbackEnabled) "Activo — Guías de voz en tiempo real"
                                        else "Desactivado — silencioso",
                                        color = if (voiceFeedbackEnabled) MeetColors.neonGreen else MeetColors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = voiceFeedbackEnabled,
                                    onCheckedChange = {
                                        voiceFeedbackEnabled = it
                                        prefs.edit().putBoolean("voice_feedback_enabled", it).apply()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MeetColors.neonGreen,
                                        checkedTrackColor = MeetColors.neonGreen.copy(alpha = 0.3f),
                                        uncheckedThumbColor = MeetColors.textSecondary,
                                        uncheckedTrackColor = MeetColors.textSecondary.copy(alpha = 0.2f)
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Copiloto AI por Voz (Manos Libres)", color = Color.White, fontWeight = FontWeight.Medium)
                                    Text(
                                        if (voiceCopilotEnabled) "Activo — Escuchando comandos de voz offline"
                                        else "Desactivado — comandos apagados",
                                        color = if (voiceCopilotEnabled) MeetColors.neonGreen else MeetColors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = voiceCopilotEnabled,
                                    onCheckedChange = { isChecked ->
                                        if (isChecked) {
                                            val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                                context,
                                                android.Manifest.permission.RECORD_AUDIO
                                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                            
                                            if (hasMicPermission) {
                                                viewModel.toggleVoiceCopilot(true)
                                            } else {
                                                micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                            }
                                        } else {
                                            viewModel.toggleVoiceCopilot(false)
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MeetColors.neonGreen,
                                        checkedTrackColor = MeetColors.neonGreen.copy(alpha = 0.3f),
                                        uncheckedThumbColor = MeetColors.textSecondary,
                                        uncheckedTrackColor = MeetColors.textSecondary.copy(alpha = 0.2f)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // ============================================================
            //  SECCIÓN 1C: ICONOS 3D ANIMADOS
            // ============================================================
            item {
                Column {
                    PhantomSectionHeader(label = "ICONOS 3D ANIMADOS", accentColor = MeetColors.hotMagenta)
                    Spacer(modifier = Modifier.height(8.dp))
                    AnimatedIconSettingsCard(context = context)
                }
            }

            // ============================================================
            //  SECCIÓN 2: INTELIGENCIA ARTIFICIAL
            // ============================================================
            item {
                Column {
                    PhantomSectionHeader(label = "MOTOR DE INTELIGENCIA ARTIFICIAL", accentColor = MeetColors.electricBlue)
                    Spacer(modifier = Modifier.height(8.dp))
                    EliteCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = MeetColors.electricBlue,
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // --- Provider Selector ---
                            Text("Proveedor IA", color = Color.White, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(4.dp))
                            ExposedDropdownMenuBox(
                                expanded = providerExpanded,
                                onExpandedChange = { providerExpanded = !providerExpanded }
                            ) {
                                OutlinedTextField(
                                    value = providers.find { it.first == selectedProvider }?.second ?: "Seleccionar",
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = MeetColors.electricBlue,
                                        unfocusedBorderColor = MeetColors.electricBlue.copy(alpha = 0.3f),
                                        focusedContainerColor = MeetColors.backgroundDeep,
                                        unfocusedContainerColor = MeetColors.backgroundDeep
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = providerExpanded,
                                    onDismissRequest = { providerExpanded = false }
                                ) {
                                    providers.forEach { (key, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                selectedProvider = key
                                                providerExpanded = false
                                                // Auto-fill default endpoints
                                                when (key) {
                                                    "openai" -> if (endpointInput.isBlank()) endpointInput = "https://api.openai.com/v1/chat/completions"
                                                    "anthropic" -> if (endpointInput.isBlank()) endpointInput = "https://api.anthropic.com/v1/messages"
                                                    "ollama" -> if (endpointInput.isBlank()) endpointInput = "http://localhost:11434/v1/chat/completions"
                                                    "mavis" -> if (endpointInput.isBlank()) endpointInput = "https://api.tu-proveedor-mavis.com/v1/chat/completions"
                                                    "gemini" -> endpointInput = ""
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // --- API Key Input ---
                            Text("API Key", color = Color.White, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("sk-... / AIza... / tu-api-key", color = MeetColors.textSecondary, fontSize = 13.sp) },
                                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showApiKey = !showApiKey }) {
                                        AnimatedNeonIcon(
                                            if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle visibility",
                                            tint = MeetColors.electricBlue
                                        )
                                    }
                                },
                                isError = apiKeyInput.isNotBlank() && !isApiKeyInputValid,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = MeetColors.electricBlue,
                                    unfocusedBorderColor = MeetColors.electricBlue.copy(alpha = 0.3f),
                                    focusedContainerColor = MeetColors.backgroundDeep,
                                    unfocusedContainerColor = MeetColors.backgroundDeep,
                                    cursorColor = MeetColors.electricBlue
                                ),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                            if (apiKeyInput.isNotBlank() && !isApiKeyInputValid) {
                                Text(
                                    text = when (selectedProvider) {
                                        "gemini" -> "⚠️ La clave de Gemini suele empezar con AIzaSy y tener al menos 20 caracteres."
                                        "openai" -> "⚠️ La clave de OpenAI suele empezar con sk-."
                                        "anthropic" -> "⚠️ La clave de Anthropic suele empezar con sk-ant- o sk-."
                                        else -> "⚠️ Clave de API demasiado corta o inválida."
                                    },
                                    color = MeetColors.error,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            // --- Custom Endpoint (visible for non-Gemini) ---
                            AnimatedVisibility(visible = selectedProvider != "gemini") {
                                Column {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Endpoint URL", color = Color.White, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = endpointInput,
                                        onValueChange = { endpointInput = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("https://api.example.com/v1/chat", color = MeetColors.textSecondary, fontSize = 13.sp) },
                                        isError = endpointInput.isNotBlank() && !isEndpointInputValid,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = MeetColors.electricBlue,
                                            unfocusedBorderColor = MeetColors.electricBlue.copy(alpha = 0.3f),
                                            focusedContainerColor = MeetColors.backgroundDeep,
                                            unfocusedContainerColor = MeetColors.backgroundDeep,
                                            cursorColor = MeetColors.electricBlue
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true
                                    )
                                    if (endpointInput.isNotBlank() && !isEndpointInputValid) {
                                        Text(
                                            text = "⚠️ Formato de URL inválido. Debe comenzar con http:// o https://",
                                            color = MeetColors.error,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }

                            // --- Model Name ---
                            AnimatedVisibility(visible = selectedProvider in listOf("openai", "ollama", "mavis", "custom")) {
                                Column {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Modelo (opcional)", color = Color.White, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = modelNameInput,
                                        onValueChange = { modelNameInput = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("gpt-4o, llama3, mistral...", color = MeetColors.textSecondary, fontSize = 13.sp) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = MeetColors.electricBlue,
                                            unfocusedBorderColor = MeetColors.electricBlue.copy(alpha = 0.3f),
                                            focusedContainerColor = MeetColors.backgroundDeep,
                                            unfocusedContainerColor = MeetColors.backgroundDeep,
                                            cursorColor = MeetColors.electricBlue
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // --- Save Button ---
                            EliteButton(
                                onClick = {
                                    viewModel.saveAiConfig(selectedProvider, apiKeyInput, endpointInput, modelNameInput)
                                    showSavedBanner = true
                                    Toast.makeText(context, "✅ Configuración IA guardada", Toast.LENGTH_SHORT).show()
                                },
                                text = "Guardar Configuración IA",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                color = MeetColors.electricBlue,
                                isEnabled = isAiConfigSaveEnabled
                            )

                            // --- Saved confirmation banner with micro-animations ---
                            AnimatedVisibility(
                                visible = showSavedBanner,
                                enter = fadeIn(animationSpec = tween(300)) + slideInVertically(initialOffsetY = { -it }, animationSpec = tween(300)),
                                exit = fadeOut(animationSpec = tween(250)) + slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(250))
                            ) {
                                EliteCard(
                                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                    backgroundColor = MeetColors.neonGreen.copy(alpha = 0.1f),
                                    glowColor = MeetColors.neonGreen,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        "✅ Motor IA configurado: ${providers.find { it.first == selectedProvider }?.second}. " +
                                        "Los cambios se aplican inmediatamente en la sección de chat IA.",
                                        color = MeetColors.neonGreen,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(12.dp),
                                        lineHeight = 16.sp
                                    )
                                }
                            }

                            // --- Provider hint ---
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                when (selectedProvider) {
                                    "gemini" -> "🔑 Usa tu API key de Google AI Studio (aistudio.google.com)"
                                    "openai" -> "🔑 Usa tu API key de platform.openai.com"
                                    "anthropic" -> "🔑 Usa tu API key de console.anthropic.com"
                                    "ollama" -> "🏠 Ollama corre localmente. Asegúrate de que el servidor esté activo."
                                    "mavis" -> "🌐 Usa el endpoint Mavis que exponga formato /v1/chat/completions o compatible OpenAI."
                                    "custom" -> "🌐 Ingresa la URL de cualquier API compatible con OpenAI."
                                    else -> ""
                                },
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }

            // --- Botón de acceso al Motor IA Avanzado (Multi-Proveedor) ---
            item {
                EliteCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate("ai_settings") },
                    glowColor = MeetColors.electricBlue,
                    backgroundColor = MeetColors.backgroundDeep,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "⚡ Motor IA Avanzado (Multi-Proveedor)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                "Configura MiniMax, OpenAI, Gemini, Anthropic, Groq, DeepSeek, Ollama y más. BYOK seguro con cifrado Keystore.",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                        AnimatedNeonIcon(
                            Icons.Default.ChevronRight,
                            contentDescription = "Ir a Motor IA Avanzado",
                            tint = MeetColors.electricBlue
                        )
                    }
                }
            }

            // ============================================================
            //  SECCIÓN 3: PERFIL DEL TALLER (Reportes Marca Blanca)
            // ============================================================
            item {
                Column {
                    PhantomSectionHeader(label = "PERFIL DEL TALLER (REPORTES PDF)", accentColor = MeetColors.neonGreen)
                    Spacer(modifier = Modifier.height(8.dp))
                    EliteCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = MeetColors.neonGreen,
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Configura los datos que aparecerán en la cabecera de tus reportes PDF.", color = MeetColors.textSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = workshopName,
                                onValueChange = { workshopName = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Nombre del Taller", color = MeetColors.textSecondary) },
                                isError = workshopName.isNotBlank() && !isWorkshopNameInputValid,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                    focusedBorderColor = MeetColors.neonGreen, unfocusedBorderColor = MeetColors.neonGreen.copy(alpha = 0.3f),
                                    focusedContainerColor = MeetColors.backgroundDeep, unfocusedContainerColor = MeetColors.backgroundDeep
                                ),
                                singleLine = true, shape = RoundedCornerShape(8.dp)
                            )
                            if (workshopName.isNotBlank() && !isWorkshopNameInputValid) {
                                Text(
                                    text = "⚠️ El nombre debe tener al menos 3 caracteres.",
                                    color = MeetColors.error,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = workshopAddress,
                                onValueChange = { workshopAddress = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Dirección", color = MeetColors.textSecondary) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                    focusedBorderColor = MeetColors.neonGreen, unfocusedBorderColor = MeetColors.neonGreen.copy(alpha = 0.3f),
                                    focusedContainerColor = MeetColors.backgroundDeep, unfocusedContainerColor = MeetColors.backgroundDeep
                                ),
                                singleLine = true, shape = RoundedCornerShape(8.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = workshopPhone,
                                        onValueChange = { workshopPhone = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Teléfono", color = MeetColors.textSecondary) },
                                        isError = workshopPhone.isNotBlank() && !isWorkshopPhoneInputValid,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                            focusedBorderColor = MeetColors.neonGreen, unfocusedBorderColor = MeetColors.neonGreen.copy(alpha = 0.3f),
                                            focusedContainerColor = MeetColors.backgroundDeep, unfocusedContainerColor = MeetColors.backgroundDeep
                                        ),
                                        singleLine = true, shape = RoundedCornerShape(8.dp)
                                    )
                                    if (workshopPhone.isNotBlank() && !isWorkshopPhoneInputValid) {
                                        Text(
                                            text = "⚠️ Teléfono inválido.",
                                            color = MeetColors.error,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = workshopEmail,
                                        onValueChange = { workshopEmail = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Email / Web", color = MeetColors.textSecondary) },
                                        isError = workshopEmail.isNotBlank() && !isWorkshopEmailInputValid,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                            focusedBorderColor = MeetColors.neonGreen, unfocusedBorderColor = MeetColors.neonGreen.copy(alpha = 0.3f),
                                            focusedContainerColor = MeetColors.backgroundDeep, unfocusedContainerColor = MeetColors.backgroundDeep
                                        ),
                                        singleLine = true, shape = RoundedCornerShape(8.dp)
                                    )
                                    if (workshopEmail.isNotBlank() && !isWorkshopEmailInputValid) {
                                        Text(
                                            text = "⚠️ Email inválido.",
                                            color = MeetColors.error,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            EliteButton(
                                onClick = {
                                    prefs.edit().apply {
                                        putString("workshop_name", workshopName)
                                        putString("workshop_address", workshopAddress)
                                        putString("workshop_phone", workshopPhone)
                                        putString("workshop_email", workshopEmail)
                                        apply()
                                    }
                                    showWorkshopSaved = true
                                    Toast.makeText(context, "✅ Perfil de taller guardado", Toast.LENGTH_SHORT).show()
                                },
                                text = "Guardar Perfil de Taller",
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                color = MeetColors.neonGreen,
                                isEnabled = isWorkshopSaveEnabled
                            )

                            // --- Saved confirmation banner with micro-animations ---
                            AnimatedVisibility(
                                visible = showWorkshopSaved,
                                enter = fadeIn(animationSpec = tween(300)) + slideInVertically(initialOffsetY = { -it }, animationSpec = tween(300)),
                                exit = fadeOut(animationSpec = tween(250)) + slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(250))
                            ) {
                                EliteCard(
                                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                    backgroundColor = MeetColors.neonGreen.copy(alpha = 0.1f),
                                    glowColor = MeetColors.neonGreen,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        "✅ Los próximos reportes PDF generados llevarán tu marca personal.",
                                        color = MeetColors.neonGreen, fontSize = 11.sp, modifier = Modifier.padding(12.dp), lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

             // ============================================================
             //  SECCIÓN: COPIAS DE SEGURIDAD (Google Drive Backup)
             // ============================================================
             item {
                 Column {
                     PhantomSectionHeader(label = "COPIAS DE SEGURIDAD", accentColor = MeetColors.electricBlue)
                     Spacer(modifier = Modifier.height(8.dp))
                     EliteCard(
                         modifier = Modifier.fillMaxWidth(),
                         glowColor = MeetColors.electricBlue,
                         backgroundColor = MeetColors.backgroundDeep,
                         shape = RoundedCornerShape(12.dp)
                     ) {
                         Column(modifier = Modifier.padding(16.dp)) {
                             Text(
                                 "Respalda tus vehículos, reportes y configuraciones directamente en tu cuenta de Google Drive de forma segura.",
                                 color = MeetColors.textSecondary,
                                 fontSize = 12.sp
                             )
                             Spacer(modifier = Modifier.height(12.dp))
                             EliteButton(
                                 onClick = { navController.navigate("backup_settings") },
                                 text = "Configurar Copia en la Nube",
                                 modifier = Modifier.fillMaxWidth().height(48.dp),
                                 color = MeetColors.electricBlue
                             )
                         }
                     }
                 }
             }

             // ============================================================
             //  SECCIÓN 4: UNIDADES
             // ============================================================
             item {
                Column {
                    PhantomSectionHeader(label = "UNIDADES DE MEDIDA", accentColor = MeetColors.neonGreen)
                    Spacer(modifier = Modifier.height(8.dp))
                    EliteCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = MeetColors.neonGreen,
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SettingsRow("Velocidad", "km/h")
                            SettingsRow("Temperatura", "Celsius (°C)")
                        }
                    }
                }
            }

            // ============================================================
            //  SECCIÓN 5: DEBUG / DIAGNÓSTICO
            // ============================================================
            item {
                Column {
                    PhantomSectionHeader(label = "DEBUG Y DIAGNÓSTICO", accentColor = MeetColors.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    EliteCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = MeetColors.error,
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            EliteButton(
                                onClick = {
                                    context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
                                        .edit().remove("onboarding_completed").apply()
                                    Toast.makeText(context, "Onboarding reseteado", Toast.LENGTH_SHORT).show()
                                },
                                text = "Resetear Onboarding",
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                color = MeetColors.error
                            )
                        }
                    }
                }
            }

            // ============================================================
            //  SECCIÓN: PANTALLA DE INICIO (EXPERIENCIA DUAL HOME)
            // ============================================================
            item {
                val homeExperienceRepo = remember {
                    com.elysium369.meet.ui.home.DefaultHomeExperienceRepository(context)
                }
                val currentHomeExp by homeExperienceRepo.selectedExperience.collectAsState()

                Column {
                    PhantomSectionHeader(label = "PANTALLA DE INICIO (EXPERIENCIA)", accentColor = MeetColors.neonGreen)
                    Spacer(modifier = Modifier.height(8.dp))
                    EliteCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = MeetColors.neonGreen,
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Elige cómo deseas visualizar y navegar en la pantalla de inicio de MEET:",
                                color = MeetColors.textSecondary,
                                fontSize = 12.sp
                            )

                            // Classic Option
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (currentHomeExp == com.elysium369.meet.ui.home.HomeExperience.CLASSIC) MeetColors.neonGreen.copy(alpha = 0.08f) else Color.Transparent)
                                    .clickable { homeExperienceRepo.setExperience(com.elysium369.meet.ui.home.HomeExperience.CLASSIC) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = currentHomeExp == com.elysium369.meet.ui.home.HomeExperience.CLASSIC,
                                    onClick = { homeExperienceRepo.setExperience(com.elysium369.meet.ui.home.HomeExperience.CLASSIC) },
                                    colors = RadioButtonDefaults.colors(selectedColor = MeetColors.neonGreen)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Vanguard Classic", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Todos los módulos y accesos directos visibles en cuadrícula.", color = MeetColors.textMuted, fontSize = 11.sp)
                                }
                            }

                            // Adaptive Option
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (currentHomeExp == com.elysium369.meet.ui.home.HomeExperience.ADAPTIVE) MeetColors.neonGreen.copy(alpha = 0.08f) else Color.Transparent)
                                    .clickable { homeExperienceRepo.setExperience(com.elysium369.meet.ui.home.HomeExperience.ADAPTIVE) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = currentHomeExp == com.elysium369.meet.ui.home.HomeExperience.ADAPTIVE,
                                    onClick = { homeExperienceRepo.setExperience(com.elysium369.meet.ui.home.HomeExperience.ADAPTIVE) },
                                    colors = RadioButtonDefaults.colors(selectedColor = MeetColors.neonGreen)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Vanguard Command", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Inicio inteligente: prioriza vehículo activo, fallas y acciones AHORA.", color = MeetColors.textMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ============================================================
            //  SECCIÓN: PERSONALIZACIÓN DEL TEMA (System Theme Customizer)
            // ============================================================
            item {
                Column {
                    PhantomSectionHeader(label = "PERSONALIZACIÓN DEL TEMA", accentColor = MeetColors.neonGreen)
                    Spacer(modifier = Modifier.height(8.dp))
                    EliteCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = MeetColors.neonGreen,
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Personaliza los colores acentuados, textos, iconos y bordes de todo el sistema Elysium Vanguard.",
                                color = MeetColors.textSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            EliteButton(
                                onClick = { showThemeCustomizer = true },
                                text = "Personalizar Colores",
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                color = MeetColors.neonGreen
                            )
                        }
                    }
                }
            }

            // ============================================================
            //  SECCIÓN 6: CUENTA / LICENCIA
            // ============================================================
            item {
                Column {
                    PhantomSectionHeader(label = "SUSCRIPCIÓN Y CUENTA", accentColor = MeetColors.cyberCyan)
                    Spacer(modifier = Modifier.height(8.dp))
                    EliteCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = MeetColors.cyberCyan,
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SettingsRow("Estado", "Elysium Pro Premium", valueColor = MeetColors.neonGreen)
                            EliteButton(
                                onClick = { },
                                text = "Gestionar Suscripción",
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                color = MeetColors.electricBlue
                            )
                        }
                    }
                }
            }

            // ============================================================
            //  SECCIÓN 8: INFORMACIÓN Y VERSIÓN DEL SISTEMA
            // ============================================================
            item {
                Column {
                    PhantomSectionHeader(label = "SISTEMA Y VERSIÓN", accentColor = MeetColors.cyberCyan)
                    Spacer(modifier = Modifier.height(8.dp))
                    EliteCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = MeetColors.cyberCyan,
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SettingsRow(
                                label = "Versión del Sistema (App)",
                                value = "MEET v${com.elysium369.meet.BuildConfig.VERSION_NAME} (Build ${com.elysium369.meet.BuildConfig.VERSION_CODE})",
                                valueColor = MeetColors.cyberCyan
                            )
                            SettingsRow(
                                label = "Arquitectura de Enlace",
                                value = "Continuous IO Reader + K-Line Targeted",
                                valueColor = MeetColors.neonGreen
                            )
                            SettingsRow(
                                label = "Motor de Telemetría",
                                value = "Global Multi-Platform OEM Suite",
                                valueColor = MeetColors.warning
                            )
                            SettingsRow(
                                label = "Integridad Criptográfica",
                                value = "TS ≡ Kotlin SHA-256 Verified",
                                valueColor = MeetColors.neonGreen
                            )
                        }
                    }
                }
            }

            // Bottom spacer
            item {
                Column {
                    PhantomSectionHeader(label = "CUENTA MEET", accentColor = MeetColors.warning)
                    Spacer(modifier = Modifier.height(8.dp))
                    EliteCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = MeetColors.warning,
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                SupabaseModule.client.auth.currentUserOrNull()?.email
                                    ?: "Sin sesión remota activa",
                                color = MeetColors.textSecondary,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        runCatching { SupabaseModule.client.auth.signOut() }
                                        PrincipalProvisioningStore.clear(context)
                                        navController.navigateTopLevel("home")
                                    }
                                },
                                enabled = SupabaseModule.client.auth.currentUserOrNull() != null,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("CERRAR SESIÓN")
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    if (showThemeCustomizer) {
        com.elysium369.meet.ui.components.SystemThemeCustomizerDialog(
            onDismiss = { showThemeCustomizer = false }
        )
    }
}

@Composable
private fun AnimatedIconSettingsCard(context: Context) {
    val iconStyle by rememberAnimatedIconStyle(context)
    val presets = listOf(
        AnimatedIconPreset.AUTO,
        AnimatedIconPreset.AXIAL_SPIN,
        AnimatedIconPreset.ORBITAL_SCANNER,
        AnimatedIconPreset.PISTON_PULSE,
        AnimatedIconPreset.HOLO_SCAN,
        AnimatedIconPreset.IGNITION_GLITCH
    )

    EliteCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = MeetColors.hotMagenta,
        backgroundColor = MeetColors.backgroundDeep,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    AnimatedNeonIcon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Iconos animados",
                        tint = MeetColors.hotMagenta,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Movimiento global", color = Color.White, fontWeight = FontWeight.Medium)
                        Text(iconStyle.preset.label, color = MeetColors.textSecondary, fontSize = 11.sp)
                    }
                }
                Switch(
                    checked = iconStyle.enabled,
                    onCheckedChange = { setAnimatedIconEnabled(context, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MeetColors.hotMagenta,
                        checkedTrackColor = MeetColors.hotMagenta.copy(alpha = 0.3f),
                        uncheckedThumbColor = MeetColors.textSecondary,
                        uncheckedTrackColor = MeetColors.textSecondary.copy(alpha = 0.2f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("Preset", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            presets.forEach { preset ->
                val selected = iconStyle.preset == preset
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) MeetColors.hotMagenta.copy(alpha = 0.16f)
                            else Color.White.copy(alpha = 0.035f)
                        )
                        .border(
                            1.dp,
                            if (selected) MeetColors.hotMagenta.copy(alpha = 0.72f)
                            else Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { setAnimatedIconPreset(context, preset) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedNeonIcon(
                        imageVector = if (preset == AnimatedIconPreset.IGNITION_GLITCH) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = preset.label,
                        tint = if (selected) MeetColors.hotMagenta else MeetColors.cyberCyan,
                        preset = preset,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        preset.label,
                        color = if (selected) Color.White else MeetColors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Intensidad", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("${(iconStyle.intensity * 100).toInt()}%", color = MeetColors.hotMagenta, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
            Slider(
                value = iconStyle.intensity,
                onValueChange = { setAnimatedIconIntensity(context, it) },
                valueRange = 0.35f..1.6f,
                steps = 4,
                colors = SliderDefaults.colors(
                    thumbColor = MeetColors.hotMagenta,
                    activeTrackColor = MeetColors.hotMagenta,
                    inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                )
            )
        }
    }
}

@Composable
fun SettingsRow(label: String, value: String, isToggle: Boolean = false, valueColor: Color = MeetColors.textSecondary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White)
        if (isToggle) {
            Switch(
                checked = false,
                onCheckedChange = {},
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MeetColors.neonGreen,
                    checkedTrackColor = MeetColors.neonGreen.copy(alpha = 0.3f)
                )
            )
        } else {
            Text(value, color = valueColor)
        }
    }
}

private fun isApiKeyValid(provider: String, key: String): Boolean {
    if (provider == "ollama") return true
    if (key.isBlank()) return false
    return when (provider) {
        "gemini" -> key.startsWith("AIzaSy") || key.length >= 20
        "openai" -> key.startsWith("sk-") && key.length >= 20
        "anthropic" -> (key.startsWith("sk-ant-") || key.startsWith("sk-")) && key.length >= 20
        else -> key.length >= 10
    }
}

private fun isEndpointValid(provider: String, url: String): Boolean {
    if (provider == "gemini") return true
    if (url.isBlank()) return false
    return android.util.Patterns.WEB_URL.matcher(url).matches() && (url.startsWith("http://") || url.startsWith("https://"))
}

private fun isPhoneValid(phone: String): Boolean {
    if (phone.isBlank()) return true
    return phone.matches(Regex("""^\+?[0-9\s\-()]{7,15}$"""))
}

private fun isEmailValid(email: String): Boolean {
    if (email.isBlank()) return true
    return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
}
