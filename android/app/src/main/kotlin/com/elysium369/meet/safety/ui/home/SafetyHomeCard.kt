package com.elysium369.meet.safety.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.safety.domain.RemoteAvailability

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyHomeCard(
    pendingLocalReports: Int,
    remoteAvailability: RemoteAvailability,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                "🛡 Elysium Seguridad",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Evidencia, prevención y rendición de cuentas",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(8.dp))

            val statusText = when {
                pendingLocalReports > 0 -> "$pendingLocalReports reporte(s) pendiente(s) de envío"
                remoteAvailability == RemoteAvailability.UNKNOWN -> "Conectividad remota no comprobada"
                remoteAvailability == RemoteAvailability.OFFLINE -> "Sin conexión"
                remoteAvailability == RemoteAvailability.DEGRADED -> "Servicio remoto degradado"
                else -> "Servicio remoto confirmado"
            }

            Text(
                statusText,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
    }
}
