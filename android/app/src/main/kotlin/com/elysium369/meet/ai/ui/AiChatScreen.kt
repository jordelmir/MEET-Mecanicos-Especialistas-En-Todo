package com.elysium369.meet.ai.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.elysium369.meet.ai.data.AiConversationStore
import com.elysium369.meet.ai.domain.*
import com.elysium369.meet.ui.ObdViewModel
import kotlinx.coroutines.launch
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    conversationId: String,
    viewModel: ObdViewModel,
    conversationStore: AiConversationStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var promptInput by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    
    val messages = remember { mutableStateListOf<AiMessage>() }
    
    LaunchedEffect(conversationId) {
        messages.clear()
        messages.addAll(conversationStore.getMessages(conversationId))
    }

    val chips = listOf(
        "Batería", "Ralentí", "Temperatura ECT", "P0230", "Fuel Pump",
        "Transmisión AT", "DTCs pendientes", "Sensores live", 
        "Explicar al cliente", "Crear checklist"
    )

    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val localContext = LocalContext.current

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        coroutineScope.launch {
            val userMsg = AiMessage(AiRole.USER, text)
            messages.add(userMsg)
            conversationStore.addMessage(conversationId, userMsg)
            
            promptInput = ""
            loading = true
            error = null

            try {
                val resultText = viewModel.consultAi(
                    apiKey = null,
                    endpointUrl = null,
                    dtcList = emptyList()
                )
                
                val aiMsg = AiMessage(AiRole.ASSISTANT, resultText)
                messages.add(aiMsg)
                conversationStore.addMessage(conversationId, aiMsg)
            } catch (e: Exception) {
                error = e
            } finally {
                loading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Copiloto IA & Soporte") },
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
                .padding(16.dp)
        ) {
            Text("Ayudas rápidas:", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chips.forEach { chipText ->
                    InputChip(
                        selected = false,
                        onClick = {
                            val vehiclePrefix = selectedVehicle?.let { "${it.make} ${it.model} ${it.year}: " } ?: ""
                            sendMessage("${vehiclePrefix}Analiza $chipText")
                        },
                        label = { Text(chipText) }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    messages.forEach { msg ->
                        val bubbleColor = if (msg.role == AiRole.USER) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                        
                        val align = if (msg.role == AiRole.USER) Alignment.End else Alignment.Start

                        Column(horizontalAlignment = align, modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = bubbleColor,
                                modifier = Modifier.widthIn(max = 320.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = msg.content,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (msg.role == AiRole.ASSISTANT) {
                                        Spacer(Modifier.height(8.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString(msg.content))
                                                    Toast.makeText(localContext, "Texto copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.ContentCopy,
                                                    contentDescription = "Copiar",
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text("Copiar", style = MaterialTheme.typography.labelSmall)
                                            }
                                            TextButton(
                                                onClick = {
                                                    Toast.makeText(localContext, "Respuesta reportada como incorrecta", Toast.LENGTH_SHORT).show()
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Warning,
                                                    contentDescription = "Reportar",
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text("Reportar", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }

                    error?.let { err ->
                        AiErrorBanner(error = err)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    placeholder = { Text("Escribe una consulta...") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { sendMessage(promptInput) }, enabled = promptInput.isNotBlank()) {
                    Icon(Icons.Default.Send, contentDescription = "Enviar")
                }
            }
        }
    }
}
