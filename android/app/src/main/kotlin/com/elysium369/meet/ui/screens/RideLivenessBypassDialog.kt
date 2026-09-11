package com.elysium369.meet.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ride.domain.RidePresenceBypassWrapper

/**
 * Dev/QA bypass dialog replacing camera liveness check.
 * ONLY visible when BuildConfig.DEBUG == true.
 */
@Composable
fun RideLivenessBypassDialog(
    onVerified: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var isProcessing by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onCancel() },
        title = {
            Text(
                text = "MODO PRUEBA - SALTO DE VERIFICACION",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Version de prueba: la verificacion facial sera saltada.",
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
                Text(
                    text = "En produccion se requiere parpadeo real frente a la camara.",
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isProcessing = true
                    val hash = RidePresenceBypassWrapper.completeChallenge()
                    RidePresenceBypassWrapper.enableBypass(context)
                    onVerified(hash)
                },
                enabled = !isProcessing
            ) {
                Text("EMPEZAR A CONDUCIR (BYPASS)")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                enabled = !isProcessing
            ) {
                Text("VOLVER A PASAJERO")
            }
        }
    )
}
