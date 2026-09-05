package com.elysium369.meet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors

enum class AccessLevel {
    NOT_REGISTERED,
    PENDING_APPROVAL,
    LOCAL_ONLY,
    APPROVED,
}

data class AccessStep(
    val number: Int,
    val label: String,
    val done: Boolean,
)

@Composable
fun AccessStatusCard(
    serviceName: String,
    serviceIcon: String,
    accessLevel: AccessLevel,
    steps: List<AccessStep>,
    accentColor: Color = MeetColors.cyberCyan,
) {
    val (statusColor, statusText, statusIcon) = when (accessLevel) {
        AccessLevel.APPROVED -> Triple(MeetColors.neonGreen, "ACCESO ACTIVO", Icons.Default.CheckCircle)
        AccessLevel.PENDING_APPROVAL -> Triple(MeetColors.warning, "EN REVISIÓN", Icons.Default.Info)
        AccessLevel.LOCAL_ONLY -> Triple(MeetColors.warning, "MODO LOCAL", Icons.Default.Warning)
        AccessLevel.NOT_REGISTERED -> Triple(MeetColors.textSecondary, "SIN REGISTRO", Icons.Default.Info)
    }

    EliteCard(
        glowColor = statusColor,
        borderColor = statusColor.copy(alpha = 0.3f),
        backgroundColor = MeetColors.cardBackground,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(serviceIcon, fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        serviceName,
                        color = MeetColors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(statusText, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Steps
            if (steps.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    steps.forEach { step ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(
                                        if (step.done) accentColor.copy(alpha = 0.2f) else Color.Transparent,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (step.done) accentColor else MeetColors.textSecondary.copy(alpha = 0.3f),
                                        RoundedCornerShape(10.dp)
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (step.done) "✓" else "${step.number}",
                                    color = if (step.done) accentColor else MeetColors.textSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                step.label,
                                color = if (step.done) MeetColors.textPrimary else MeetColors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (step.done) FontWeight.Medium else FontWeight.Normal,
                            )
                        }
                    }
                }
            }

            // Guidance message
            when (accessLevel) {
                AccessLevel.NOT_REGISTERED -> {
                    Text(
                        "Regístrate para comenzar a recibir solicitudes. El proceso es gratuito y toma menos de 5 minutos.",
                        color = MeetColors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
                AccessLevel.PENDING_APPROVAL -> {
                    Text(
                        "Tu solicitud fue enviada al Centro de Confianza. La revisión es manual y puede tardar hasta 24 horas. No necesitas reenviarla.",
                        color = MeetColors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
                AccessLevel.LOCAL_ONLY -> {
                    Text(
                        "Tu expediente está listo localmente. El servidor aún no lo revisa. Mientras tanto, puedes usar funciones básicas.",
                        color = MeetColors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
                AccessLevel.APPROVED -> {
                    Text(
                        "Tu acceso está activo. Puedes recibir y completar solicitudes de servicio.",
                        color = MeetColors.neonGreen,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
}
