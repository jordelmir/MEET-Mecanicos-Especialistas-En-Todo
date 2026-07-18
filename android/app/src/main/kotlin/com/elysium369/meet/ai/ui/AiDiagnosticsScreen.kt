package com.elysium369.meet.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.elysium369.meet.ai.domain.AiResponse
import com.elysium369.meet.ui.ObdViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDiagnosticsScreen(
    dtcCode: String,
    viewModel: ObdViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var loading by remember { mutableStateOf(false) }
    var response by remember { mutableStateOf<AiResponse?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    
    val selectedVehicle by viewModel.selectedVehicle.collectAsState()
    val isObdConnected = viewModel.telemetrySamples.value.values.any { it.hasRealValue }

    LaunchedEffect(dtcCode) {
        if (dtcCode.isNotBlank()) {
            loading = true
            error = null
            try {
                val resultText = viewModel.consultAi(
                    apiKey = null,
                    endpointUrl = null,
                    dtcList = listOf(dtcCode)
                )
                response = AiResponse(
                    text = resultText,
                    usage = null,
                    providerId = "minimax",
                    model = "MiniMax-M1"
                )
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
                title = { Text("Elysium AI Diagnostics") },
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
            if (!isObdConnected) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Alerta",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Sin enlace real. Diagnóstico preliminar únicamente.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Text(
                text = "Análisis para el código: $dtcCode",
                style = MaterialTheme.typography.headlineSmall
            )

            if (loading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            error?.let { err ->
                AiErrorBanner(error = err)
            }

            response?.let { res ->
                AiDiagnosticJsonRenderer(rawText = res.text)
            }
        }
    }
}
