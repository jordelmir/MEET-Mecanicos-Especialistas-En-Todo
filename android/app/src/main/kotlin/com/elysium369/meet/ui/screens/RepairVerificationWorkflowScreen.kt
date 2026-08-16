package com.elysium369.meet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.ui.theme.MeetColors

/**
 * Explicit entry point for repair verification. It deliberately never turns a completed
 * procedure into a resolved finding. Resolution remains owned by RepairVerificationEngine
 * after same-vehicle, same-binding evidence has been captured.
 */
@Composable
fun RepairVerificationWorkflowScreen(
    navController: NavController,
    findingId: String,
) {
    val hasCanonicalFinding = findingId.isNotBlank()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeetColors.backgroundDeep)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = MeetColors.cyberCyan)
            }
            Text(
                "VERIFICACIÓN DE REPARACIÓN",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
            )
        }
        VerificationStage("1", "PROCEDIMIENTO COMPLETADO", true)
        VerificationStage("2", "POST-SCAN DEL MISMO VEHÍCULO", false)
        VerificationStage("3", "COMPARACIÓN DE SEÑALES", false)
        VerificationStage("4", "MODE 06 / READINESS / CICLO", false)

        Text(
            if (hasCanonicalFinding) {
                "Finding canónico: $findingId"
            } else {
                "No existe una identidad canónica resoluble. El escaneo será exploratorio."
            },
            color = if (hasCanonicalFinding) MeetColors.textSecondary else MeetColors.warning,
            fontSize = 11.sp,
        )
        Text(
            "Completar el procedimiento no significa que la falla esté resuelta. Solo una prueba posterior con cobertura, evidencia antes/después y el mismo binding puede emitir VERIFICADO RESUELTO.",
            color = MeetColors.textSecondary,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = { navController.navigate("scanner") { launchSingleTop = true } },
            enabled = hasCanonicalFinding,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MeetColors.neonGreen,
                contentColor = Color.Black,
                disabledContainerColor = MeetColors.cardBackground,
            ),
        ) {
            Text("INICIAR POST-SCAN CON EVIDENCIA", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun VerificationStage(number: String, label: String, complete: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MeetColors.cardBackground, RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (complete) MeetColors.neonGreen else MeetColors.borderBlue,
                RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(number, color = if (complete) MeetColors.neonGreen else MeetColors.textMuted, fontWeight = FontWeight.Black)
        Text(label, color = if (complete) Color.White else MeetColors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
