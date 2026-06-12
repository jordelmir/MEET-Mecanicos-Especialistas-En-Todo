package com.elysium369.meet.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.components.PhantomSectionHeader
import com.elysium369.meet.ui.theme.MeetColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: ObdViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

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
                onBackClick = { navController.popBackStack() },
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
            //  SECCIÓN 1: ADAPTADOR OBD2 & MODO CLON FORZADO
            // ============================================================
            item {
                Column {
                    PhantomSectionHeader(label = "ADAPTADOR OBD2", accentColor = MeetColors.neonGreen)
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
                                        Icon(
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
                            AnimatedVisibility(visible = selectedProvider in listOf("openai", "ollama", "custom")) {
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

            // Bottom spacer
            item { Spacer(modifier = Modifier.height(32.dp)) }
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
