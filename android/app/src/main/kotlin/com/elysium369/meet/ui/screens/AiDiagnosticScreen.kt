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
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.components.EliteTopAppBar

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
            EliteTopAppBar(
                title = "MEET iA",
                onBackClick = onBack,
                backgroundColor = MeetColors.backgroundDark,
                actions = {
                    IconButton(onClick = { isConfigOpen = !isConfigOpen }) {
                        Text("⚙️", style = MaterialTheme.typography.titleMedium)
                    }
                }
            )
        },
        containerColor = MeetColors.backgroundDark
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)
        ) {
            if (isConfigOpen) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).border(1.dp, MeetColors.electricBlue.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Configuración de Motor IA", color = MeetColors.electricBlue, fontWeight = FontWeight.Bold)
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
                                label = { Text("Proveedor IA", color = MeetColors.textMuted) },
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
                            label = { Text("API Key (Vacío para Local)", color = MeetColors.textMuted) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (provider == "Local/Ollama" || provider == "OpenAI") {
                            OutlinedTextField(
                                value = baseUrl,
                                onValueChange = { baseUrl = it },
                                label = { Text("Custom Base URL (Ej. http://192.168.1.100:11434)", color = MeetColors.textMuted) },
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
                                Text("Limpiar", color = com.elysium369.meet.ui.theme.MeetColors.error)
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
                                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.electricBlue)
                            ) {
                                Text("Guardar API", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (dtcCode.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, com.elysium369.meet.ui.theme.MeetColors.neonGreen.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Text("Código a analizar", color = MeetColors.textMuted, style = MaterialTheme.typography.labelSmall)
                        Text(dtcCode, color = com.elysium369.meet.ui.theme.MeetColors.neonGreen, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            } else {
                Text("Chat Libre con IA", color = MeetColors.textSecondary, modifier = Modifier.padding(bottom = 16.dp))
            }
            
            // AI Response
            Card(
                colors = CardDefaults.cardColors(containerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().weight(1f).border(1.dp, MeetColors.electricBlue.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
                    Text("🤖 IA Preparada ($provider)", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (apiKey.isEmpty() && provider != "Local/Ollama") {
                        Text("⚠️ No has configurado tu API Key. Toca el engranaje arriba a la derecha para configurarla.", color = MeetColors.warning)
                    } else if (isLoading) {
                        CircularProgressIndicator(color = MeetColors.electricBlue)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Consultando IA con el Freeze Frame actual. Por favor espera unos segundos...", color = MeetColors.textSecondary)
                    } else if (aiResponse != null) {
                        Text(aiResponse.orEmpty(), color = Color.White)
                    } else if (dtcCode.isEmpty()) {
                        Text("¿En qué te puedo ayudar con el diagnóstico de tu vehículo hoy?", color = MeetColors.textSecondary)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { 
                    viewModel.generateFullReport(aiResponse)
                },
                colors = ButtonDefaults.buttonColors(containerColor = com.elysium369.meet.ui.theme.MeetColors.backgroundDark),
                modifier = Modifier.fillMaxWidth().height(50.dp).border(1.dp, com.elysium369.meet.ui.theme.MeetColors.neonGreen, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                enabled = aiResponse != null
            ) {
                Text("EXPORTAR REPORTE IA (PDF)", color = if (aiResponse != null) com.elysium369.meet.ui.theme.MeetColors.neonGreen else MeetColors.textMuted, fontWeight = FontWeight.Bold)
            }
        }
    }
}
