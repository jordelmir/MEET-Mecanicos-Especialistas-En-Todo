package com.elysium369.meet.ai.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.elysium369.meet.ai.domain.AiError

@Composable
fun AiErrorBanner(
    error: Throwable?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = error != null, modifier = modifier) {
        if (error == null) return@AnimatedVisibility

        val errorText = when (error) {
            is AiError.InvalidApiKey -> "API key inválida o revocada. Verifica proveedor y clave."
            is AiError.HttpFailure -> when (error.code) {
                401 -> "API key inválida o revocada. Verifica proveedor y clave."
                403 -> "Tu cuenta no tiene permiso para este modelo o endpoint."
                429 -> "Límite de uso alcanzado. Espera, cambia de proveedor o usa backend PRO."
                else -> error.message ?: "Error del proveedor"
            }
            is AiError.RateLimited -> "Límite de uso alcanzado. Espera, cambia de proveedor o usa backend PRO."
            is AiError.Timeout -> "El proveedor tardó demasiado. Baja tokens o revisa conexión."
            is AiError.NetworkUnavailable -> "Sin internet. Usa modo offline limitado o conecta red."
            is AiError.MalformedResponse -> "El proveedor respondió texto no estructurado. Se mostrará como respuesta libre."
            is AiError.PolicyBlocked -> "Bloqueado por seguridad: ${error.reason}"
            else -> error.message ?: "Error desconocido en motor de IA"
        }

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Alerta",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
