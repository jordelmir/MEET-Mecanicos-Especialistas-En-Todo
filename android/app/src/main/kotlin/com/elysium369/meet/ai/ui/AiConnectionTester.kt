package com.elysium369.meet.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AiConnectionTester(
    testing: Boolean,
    testResult: Result<Unit>?,
    onTestClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onTestClick,
                enabled = !testing,
                modifier = Modifier.weight(1f)
            ) {
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Probando...")
                } else {
                    Text("Probar conexión")
                }
            }
        }

        if (testResult != null) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (testResult.isSuccess) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (testResult.isSuccess) {
                        "Conexión exitosa. El motor responde correctamente."
                    } else {
                        val errorMsg = testResult.exceptionOrNull()?.message ?: "Error de conexión"
                        "Error: $errorMsg"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (testResult.isSuccess) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
