package com.elysium369.meet.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.ObdViewModel
import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: ObdViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // --- Live state from ViewModel ---
    val forceCloneMode by viewModel.forceCloneMode.collectAsState()
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

    Scaffold(
        topBar = {
            com.elysium369.meet.ui.components.EliteTopAppBar(
                title = "Ajustes Avanzados\nConfiguración del Sistema", // we can improve this
                onBackClick = { navController.popBackStack() },
                backgroundColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark
            )
        },
        containerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ============================================================
            //  SECCIÓN 1: ADAPTADOR OBD2 & MODO CLON FORZADO
            // ============================================================
            item {
                CyberpunkSettingsSection("ADAPTADOR OBD2", com.elysium369.meet.ui.theme.MeetColors.neonGreen) {
                    SettingsRow("Tipo de Conexión", "WiFi (192.168.0.10:35000)")

                    // --- FORCE CLONE MODE: FUNCTIONAL TOGGLE ---
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

                    // Clone mode info
                    AnimatedVisibility(visible = forceCloneMode) {
                        com.elysium369.meet.ui.components.EliteCard(
                            modifier = Modifier.fillMaxWidth(),
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

            // ============================================================
            //  SECCIÓN 2: INTELIGENCIA ARTIFICIAL — FULLY FUNCTIONAL
            // ============================================================
            item {
                CyberpunkSettingsSection("INTELIGENCIA ARTIFICIAL", MeetColors.electricBlue) {

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
                                focusedContainerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDeep,
                                unfocusedContainerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDeep
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MeetColors.electricBlue,
                            unfocusedBorderColor = MeetColors.electricBlue.copy(alpha = 0.3f),
                            focusedContainerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDeep,
                            unfocusedContainerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDeep,
                            cursorColor = MeetColors.electricBlue
                        ),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

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
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = MeetColors.electricBlue,
                                    unfocusedBorderColor = MeetColors.electricBlue.copy(alpha = 0.3f),
                                    focusedContainerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDeep,
                                    unfocusedContainerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDeep,
                                    cursorColor = MeetColors.electricBlue
                                ),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                        }
                    }

                    // --- Model Name (optional) ---
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
                                    focusedContainerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDeep,
                                    unfocusedContainerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDeep,
                                    cursorColor = MeetColors.electricBlue
                                ),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- Save Button ---
                    com.elysium369.meet.ui.components.EliteButton(
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
                            isEnabled = apiKeyInput.isNotBlank() || selectedProvider == "ollama"
                        )

                    // --- Saved confirmation ---
                    AnimatedVisibility(visible = showSavedBanner) {
                        com.elysium369.meet.ui.components.EliteCard(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            backgroundColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.1f),
                            glowColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "✅ Motor IA configurado: ${providers.find { it.first == selectedProvider }?.second}. " +
                                "Los cambios se aplican inmediatamente en la sección de chat IA.",
                                color = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(12.dp),
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // --- Provider hint ---
                    Spacer(modifier = Modifier.height(8.dp))
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

            // ============================================================
            //  SECCIÓN 2.5: PERFIL DEL TALLER (Reportes Marca Blanca)
            // ============================================================
            item {
                CyberpunkSettingsSection("PERFIL DEL TALLER (REMPORTES PDF)", MeetColors.neonGreen) {
                    Text("Configura los datos que aparecerán en la cabecera de tus reportes PDF.", color = MeetColors.textSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = workshopName,
                        onValueChange = { workshopName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nombre del Taller", color = MeetColors.textSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = MeetColors.neonGreen, unfocusedBorderColor = MeetColors.neonGreen.copy(alpha = 0.3f),
                            focusedContainerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDeep, unfocusedContainerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDeep
                        ),
                        singleLine = true, shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = workshopAddress,
                        onValueChange = { workshopAddress = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Dirección", color = MeetColors.textSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = MeetColors.neonGreen, unfocusedBorderColor = MeetColors.neonGreen.copy(alpha = 0.3f),
                            focusedContainerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDeep, unfocusedContainerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDeep
                        ),
                        singleLine = true, shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = workshopPhone,
                            onValueChange = { workshopPhone = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Teléfono", color = MeetColors.textSecondary) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedBorderColor = MeetColors.neonGreen, unfocusedBorderColor = MeetColors.neonGreen.copy(alpha = 0.3f),
                                focusedContainerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDeep, unfocusedContainerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDeep
                            ),
                            singleLine = true, shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = workshopEmail,
                            onValueChange = { workshopEmail = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Email / Web", color = MeetColors.textSecondary) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedBorderColor = MeetColors.neonGreen, unfocusedBorderColor = MeetColors.neonGreen.copy(alpha = 0.3f),
                                focusedContainerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDeep, unfocusedContainerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDeep
                            ),
                            singleLine = true, shape = RoundedCornerShape(8.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    com.elysium369.meet.ui.components.EliteButton(
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
                        color = MeetColors.neonGreen
                    )

                    AnimatedVisibility(visible = showWorkshopSaved) {
                        com.elysium369.meet.ui.components.EliteCard(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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

            // ============================================================
            //  SECCIÓN 3: UNIDADES
            // ============================================================
            item {
                CyberpunkSettingsSection("UNIDADES", com.elysium369.meet.ui.theme.MeetColors.neonGreen) {
                    SettingsRow("Velocidad", "km/h")
                    SettingsRow("Temperatura", "Celsius (°C)")
                }
            }

            // ============================================================
            //  SECCIÓN 4: DEBUG
            // ============================================================
            item {
                CyberpunkSettingsSection("DEBUG", com.elysium369.meet.ui.theme.MeetColors.error) {
                    com.elysium369.meet.ui.components.EliteButton(
                        onClick = {
                            context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
                                .edit().remove("onboarding_completed").apply()
                            Toast.makeText(context, "Onboarding reseteado", Toast.LENGTH_SHORT).show()
                        },
                        text = "Resetear Onboarding",
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        color = com.elysium369.meet.ui.theme.MeetColors.error
                    )
                }
            }

            // ============================================================
            //  SECCIÓN 5: CUENTA
            // ============================================================
            item {
                CyberpunkSettingsSection("CUENTA", com.elysium369.meet.ui.theme.MeetColors.neonGreen) {
                    SettingsRow("Estado", "MEET Pro Premium", valueColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen)
                    com.elysium369.meet.ui.components.EliteButton(
                        onClick = { },
                        text = "Gestionar Suscripción",
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        color = MeetColors.electricBlue
                    )
                }
            }

            // Bottom spacer
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun CyberpunkSettingsSection(title: String, accentColor: Color, content: @Composable ColumnScope.() -> Unit) {
    com.elysium369.meet.ui.components.EliteCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = accentColor,
        backgroundColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
        ) {
            Text(title, color = accentColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(12.dp))
            content()
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
                    checkedThumbColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen,
                    checkedTrackColor = com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.3f)
                )
            )
        } else {
            Text(value, color = valueColor)
        }
    }
}
