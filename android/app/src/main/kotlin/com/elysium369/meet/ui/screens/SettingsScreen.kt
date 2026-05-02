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
            TopAppBar(
                title = {
                    Column {
                        Text("Ajustes Avanzados", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Configuración del Sistema", color = Color(0xFF39FF14), fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Text("←", color = Color(0xFF39FF14), style = MaterialTheme.typography.titleLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0E1A))
            )
        },
        containerColor = Color(0xFF0A0E1A)
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
                CyberpunkSettingsSection("ADAPTADOR OBD2", Color(0xFF39FF14)) {
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
                                color = if (forceCloneMode) Color(0xFFFFAA00) else Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = forceCloneMode,
                            onCheckedChange = { viewModel.setForceCloneMode(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFFFAA00),
                                checkedTrackColor = Color(0xFFFFAA00).copy(alpha = 0.3f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.Gray.copy(alpha = 0.2f)
                            )
                        )
                    }

                    // Clone mode info
                    AnimatedVisibility(visible = forceCloneMode) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFAA00).copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFFFAA00).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                "⚠️ El sistema usará protocolos de compatibilidad para adaptadores genéricos ELM327. " +
                                "Funciones avanzadas (STN, OBDLink) estarán deshabilitadas.",
                                color = Color(0xFFFFAA00),
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
                CyberpunkSettingsSection("INTELIGENCIA ARTIFICIAL", Color(0xFFCC00FF)) {

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
                                focusedBorderColor = Color(0xFFCC00FF),
                                unfocusedBorderColor = Color(0xFFCC00FF).copy(alpha = 0.3f),
                                focusedContainerColor = Color(0xFF060612),
                                unfocusedContainerColor = Color(0xFF060612)
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
                        placeholder = { Text("sk-... / AIza... / tu-api-key", color = Color.Gray, fontSize = 13.sp) },
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle visibility",
                                    tint = Color(0xFFCC00FF)
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFCC00FF),
                            unfocusedBorderColor = Color(0xFFCC00FF).copy(alpha = 0.3f),
                            focusedContainerColor = Color(0xFF060612),
                            unfocusedContainerColor = Color(0xFF060612),
                            cursorColor = Color(0xFFCC00FF)
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
                                placeholder = { Text("https://api.example.com/v1/chat", color = Color.Gray, fontSize = 13.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFCC00FF),
                                    unfocusedBorderColor = Color(0xFFCC00FF).copy(alpha = 0.3f),
                                    focusedContainerColor = Color(0xFF060612),
                                    unfocusedContainerColor = Color(0xFF060612),
                                    cursorColor = Color(0xFFCC00FF)
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
                                placeholder = { Text("gpt-4o, llama3, mistral...", color = Color.Gray, fontSize = 13.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFCC00FF),
                                    unfocusedBorderColor = Color(0xFFCC00FF).copy(alpha = 0.3f),
                                    focusedContainerColor = Color(0xFF060612),
                                    unfocusedContainerColor = Color(0xFF060612),
                                    cursorColor = Color(0xFFCC00FF)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- Save Button ---
                    Button(
                        onClick = {
                            viewModel.saveAiConfig(selectedProvider, apiKeyInput, endpointInput, modelNameInput)
                            showSavedBanner = true
                            Toast.makeText(context, "✅ Configuración IA guardada", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFCC00FF)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        enabled = apiKeyInput.isNotBlank() || selectedProvider == "ollama"
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Guardar Configuración IA", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    // --- Saved confirmation ---
                    AnimatedVisibility(visible = showSavedBanner) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF39FF14).copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .border(1.dp, Color(0xFF39FF14).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                "✅ Motor IA configurado: ${providers.find { it.first == selectedProvider }?.second}. " +
                                "Los cambios se aplican inmediatamente en la sección de chat IA.",
                                color = Color(0xFF39FF14),
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
                        color = Color.Gray,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            // ============================================================
            //  SECCIÓN 3: UNIDADES
            // ============================================================
            item {
                CyberpunkSettingsSection("UNIDADES", Color(0xFF39FF14)) {
                    SettingsRow("Velocidad", "km/h")
                    SettingsRow("Temperatura", "Celsius (°C)")
                }
            }

            // ============================================================
            //  SECCIÓN 4: DEBUG
            // ============================================================
            item {
                CyberpunkSettingsSection("DEBUG", Color(0xFFFF003C)) {
                    Button(
                        onClick = {
                            context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
                                .edit().remove("onboarding_completed").apply()
                            Toast.makeText(context, "Onboarding reseteado", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0E1A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .border(1.dp, Color(0xFFFF003C), RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Resetear Onboarding", color = Color(0xFFFF003C), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ============================================================
            //  SECCIÓN 5: CUENTA
            // ============================================================
            item {
                CyberpunkSettingsSection("CUENTA", Color(0xFF39FF14)) {
                    SettingsRow("Estado", "MEET Pro Premium", valueColor = Color(0xFF39FF14))
                    Button(
                        onClick = { },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0E1A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .border(1.dp, Color(0xFFCC00FF), RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Gestionar Suscripción", color = Color(0xFFCC00FF), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Bottom spacer
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun CyberpunkSettingsSection(title: String, accentColor: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E1A)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
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
fun SettingsRow(label: String, value: String, isToggle: Boolean = false, valueColor: Color = Color.Gray) {
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
                    checkedThumbColor = Color(0xFF39FF14),
                    checkedTrackColor = Color(0xFF39FF14).copy(alpha = 0.3f)
                )
            )
        } else {
            Text(value, color = valueColor)
        }
    }
}
