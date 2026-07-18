package com.elysium369.meet.ai.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.elysium369.meet.ai.data.AiProviderRegistry
import com.elysium369.meet.ai.data.AiSecureKeyStore
import com.elysium369.meet.ai.domain.AiProviderConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    registry: AiProviderRegistry,
    keyStore: AiSecureKeyStore,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("meet_ai_settings_prefs", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()

    val providers = remember { registry.getAllProviders() }
    var selectedProviderId by remember { mutableStateOf(sharedPrefs.getString("selected_provider_id", "minimax") ?: "minimax") }
    val selectedProvider = remember(selectedProviderId) { registry.getProvider(selectedProviderId) }

    var apiKey by remember { mutableStateOf("") }
    var maskedKey by remember { mutableStateOf<String?>(null) }
    var baseUrl by remember { mutableStateOf(sharedPrefs.getString("base_url_$selectedProviderId", "") ?: "") }
    var model by remember { mutableStateOf(sharedPrefs.getString("model_$selectedProviderId", "") ?: "") }
    var temperature by remember { mutableFloatStateOf(sharedPrefs.getFloat("temperature_$selectedProviderId", 0.2f)) }
    var maxTokens by remember { mutableStateOf(sharedPrefs.getInt("max_tokens_$selectedProviderId", 1600).toString()) }
    var streamingEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("streaming_$selectedProviderId", true)) }
    var jsonModeEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("json_mode_$selectedProviderId", false)) }
    var useBackendProxy by remember { mutableStateOf(sharedPrefs.getBoolean("use_backend_proxy_$selectedProviderId", false)) }
    var safetyMode by remember { mutableStateOf(sharedPrefs.getBoolean("safety_mode_$selectedProviderId", true)) }

    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Result<Unit>?>(null) }

    LaunchedEffect(selectedProviderId) {
        baseUrl = sharedPrefs.getString("base_url_$selectedProviderId", selectedProvider?.defaultBaseUrl ?: "") ?: ""
        model = sharedPrefs.getString("model_$selectedProviderId", selectedProvider?.defaultModels?.firstOrNull()?.id ?: "") ?: ""
        temperature = sharedPrefs.getFloat("temperature_$selectedProviderId", 0.2f)
        maxTokens = sharedPrefs.getInt("max_tokens_$selectedProviderId", 1600).toString()
        streamingEnabled = sharedPrefs.getBoolean("streaming_$selectedProviderId", true)
        jsonModeEnabled = sharedPrefs.getBoolean("json_mode_$selectedProviderId", false)
        useBackendProxy = sharedPrefs.getBoolean("use_backend_proxy_$selectedProviderId", false)
        safetyMode = sharedPrefs.getBoolean("safety_mode_$selectedProviderId", true)
        
        apiKey = ""
        maskedKey = keyStore.getMaskedKey(selectedProviderId)
        testResult = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Motor de Inteligencia Artificial") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AiProviderPicker(
                providers = providers,
                selectedProviderId = selectedProviderId,
                onProviderSelected = { selectedProviderId = it }
            )

            AiKeyInput(
                apiKey = apiKey,
                onKeyChange = { apiKey = it },
                maskedKey = maskedKey,
                onDeleteKey = {
                    coroutineScope.launch {
                        keyStore.deleteApiKey(selectedProviderId)
                        maskedKey = null
                        Toast.makeText(context, "Clave API eliminada.", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            val isBaseUrlEditable = selectedProviderId in listOf("custom", "local_http", "ollama")
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { if (isBaseUrlEditable) baseUrl = it },
                label = { Text("Base URL") },
                readOnly = !isBaseUrlEditable,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Modelo") },
                placeholder = { Text("Ej. MiniMax-M1, gpt-4o") },
                modifier = Modifier.fillMaxWidth()
            )

            selectedProvider?.let { provider ->
                if (provider.defaultModels.isNotEmpty()) {
                    Text("Modelos recomendados:", style = MaterialTheme.typography.bodySmall)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        provider.defaultModels.forEach { defaultModel ->
                            SuggestionChip(
                                onClick = { model = defaultModel.id },
                                label = { Text(defaultModel.name) }
                            )
                        }
                    }
                }
            }

            Text("Parámetros del Modelo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Temperatura: ${"%.2f".format(temperature)}")
            }
            Slider(
                value = temperature,
                onValueChange = { temperature = it },
                valueRange = 0.0f..1.0f,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = maxTokens,
                onValueChange = { maxTokens = it },
                label = { Text("Máximo de Tokens") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Streaming habilitado")
                Switch(checked = streamingEnabled, onCheckedChange = { streamingEnabled = it })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Modo JSON estructurado")
                Switch(checked = jsonModeEnabled, onCheckedChange = { jsonModeEnabled = it })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Usar Backend Proxy (Planes PRO)")
                Switch(checked = useBackendProxy, onCheckedChange = { useBackendProxy = it })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Modo Seguro Automotriz (Bloquear comandos destructivos)")
                Switch(checked = safetyMode, onCheckedChange = { safetyMode = it })
            }

            AiConnectionTester(
                testing = testing,
                testResult = testResult,
                onTestClick = {
                    coroutineScope.launch {
                        testing = true
                        testResult = null
                        
                        if (apiKey.isNotEmpty()) {
                            keyStore.saveApiKey(selectedProviderId, apiKey)
                        }
                        
                        val config = AiProviderConfig(
                            providerId = selectedProviderId,
                            displayName = selectedProvider?.displayName ?: selectedProviderId,
                            apiKeyAlias = "user_${selectedProviderId}_key",
                            baseUrl = baseUrl,
                            model = model,
                            temperature = temperature.toDouble(),
                            maxTokens = maxTokens.toIntOrNull() ?: 1600,
                            streamingEnabled = streamingEnabled,
                            jsonModeEnabled = jsonModeEnabled,
                            useBackendProxy = useBackendProxy,
                            updatedAtMillis = System.currentTimeMillis()
                        )
                        
                        val providerInstance = registry.getProvider(selectedProviderId)
                        if (providerInstance != null) {
                            val res = providerInstance.testConnection(config)
                            testResult = res
                        } else {
                            testResult = Result.failure(Exception("Proveedor no disponible en el registro"))
                        }
                        testing = false
                    }
                }
            )

            Button(
                onClick = {
                    coroutineScope.launch {
                        if (apiKey.isNotEmpty()) {
                            val saveRes = keyStore.saveApiKey(selectedProviderId, apiKey)
                            if (saveRes.isFailure) {
                                Toast.makeText(context, "Error al cifrar clave: ${saveRes.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                return@launch
                            }
                            maskedKey = keyStore.getMaskedKey(selectedProviderId)
                            apiKey = ""
                        }

                        sharedPrefs.edit()
                            .putString("selected_provider_id", selectedProviderId)
                            .putString("base_url_$selectedProviderId", baseUrl)
                            .putString("model_$selectedProviderId", model)
                            .putFloat("temperature_$selectedProviderId", temperature)
                            .putInt("max_tokens_$selectedProviderId", maxTokens.toIntOrNull() ?: 1600)
                            .putBoolean("streaming_$selectedProviderId", streamingEnabled)
                            .putBoolean("json_mode_$selectedProviderId", jsonModeEnabled)
                            .putBoolean("use_backend_proxy_$selectedProviderId", useBackendProxy)
                            .putBoolean("safety_mode_$selectedProviderId", safetyMode)
                            .apply()

                        Toast.makeText(context, "Configuración guardada exitosamente.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text("Guardar Configuración")
            }
        }
    }
}
