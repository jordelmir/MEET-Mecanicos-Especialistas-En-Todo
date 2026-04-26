package com.elysium369.meet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDiagnosticScreen(
    dtcCode: String,
    onBack: () -> Unit,
    viewModel: com.elysium369.meet.ui.ObdViewModel
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPrefs = context.getSharedPreferences("meet_prefs", android.content.Context.MODE_PRIVATE)
    
    var provider by remember { mutableStateOf(sharedPrefs.getString("ai_provider", "Google Gemini") ?: "Google Gemini") }
    var apiKey by remember { mutableStateOf(sharedPrefs.getString("ai_api_key", "") ?: "") }
    var baseUrl by remember { mutableStateOf(sharedPrefs.getString("ai_base_url", "") ?: "") }
    var isConfigOpen by remember { mutableStateOf(false) }

    var aiResponse by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(dtcCode) {
        if (dtcCode.isNotEmpty() && (apiKey.isNotEmpty() || provider == "Local/Ollama")) {
            isLoading = true
            aiResponse = viewModel.consultAi(apiKey.takeIf { it.isNotBlank() }, baseUrl.takeIf { it.isNotBlank() }, listOf(dtcCode))
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MEET AI", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    IconButton(onClick = { isConfigOpen = !isConfigOpen }) {
                        Text("⚙️", style = MaterialTheme.typography.titleMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0A))
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)
        ) {
            if (isConfigOpen) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).border(1.dp, Color(0xFFCC00FF).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Configuración de Motor IA", color = Color(0xFFCC00FF), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        
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
                                label = { Text("Proveedor IA", color = Color.Gray) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                providers.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p) },
                                        onClick = {
                                            provider = p
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("API Key (Vacío para Local)", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (provider == "Local/Ollama" || provider == "OpenAI") {
                            OutlinedTextField(
                                value = baseUrl,
                                onValueChange = { baseUrl = it },
                                label = { Text("Custom Base URL (Ej. http://192.168.1.100:11434)", color = Color.Gray) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
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
                                Text("Limpiar", color = Color(0xFFFF003C))
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
                                    // Trigger AI if we already have a DTC
                                    if (dtcCode.isNotEmpty() && (apiKey.isNotEmpty() || provider == "Local/Ollama")) {
                                        coroutineScope.launch {
                                            isLoading = true
                                            aiResponse = viewModel.consultAi(apiKey.takeIf { it.isNotBlank() }, baseUrl.takeIf { it.isNotBlank() }, listOf(dtcCode))
                                            isLoading = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC00FF))
                            ) {
                                Text("Guardar API", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (dtcCode.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF00FFCC).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Text("Código a analizar", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                        Text(dtcCode, color = Color(0xFF00FFCC), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            } else {
                Text("Chat Libre con IA", color = Color.Gray, modifier = Modifier.padding(bottom = 16.dp))
            }
            
            // AI Response
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().weight(1f).border(1.dp, Color(0xFFCC00FF).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
                    Text("🤖 IA Preparada ($provider)", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (apiKey.isEmpty() && provider != "Local/Ollama") {
                        Text("⚠️ No has configurado tu API Key. Toca el engranaje arriba a la derecha para configurarla.", color = Color(0xFFFFD700))
                    } else if (isLoading) {
                        CircularProgressIndicator(color = Color(0xFFCC00FF))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Consultando IA con el Freeze Frame actual. Por favor espera unos segundos...", color = Color.LightGray)
                    } else if (aiResponse != null) {
                        Text(aiResponse!!, color = Color.White)
                    } else if (dtcCode.isEmpty()) {
                        Text("¿En qué te puedo ayudar con el diagnóstico de tu vehículo hoy?", color = Color.LightGray)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { /* Export PDF logic */ },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0A0A)),
                modifier = Modifier.fillMaxWidth().height(50.dp).border(1.dp, Color(0xFF00FFCC), RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("EXPORTAR REPORTE IA (PDF)", color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold)
            }
        }
    }
}
