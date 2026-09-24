package com.elysium369.meet.safety.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.safety.domain.RemoteAvailability
import com.elysium369.meet.safety.ui.common.PulseState
import com.elysium369.meet.safety.ui.common.SafetyPulse
import com.elysium369.meet.ui.theme.MeetColors

@Composable
fun SafetyHomeCard(
    pendingLocalReports: Int,
    remoteAvailability: RemoteAvailability,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulseState = when {
        pendingLocalReports > 0 -> PulseState.PENDING
        remoteAvailability == RemoteAvailability.OFFLINE -> PulseState.ERROR
        else -> PulseState.NOMINAL
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SafetyPulse(state = pulseState, size = 36.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "ELYSIUM SEGURIDAD",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = Color.White,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            "Evidencia, prevención y rendición de cuentas",
                            fontSize = 11.sp,
                            color = MeetColors.cyberCyan,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MeetColors.cyberCyan,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Status bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MeetColors.backgroundDeep)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    pendingLocalReports > 0 -> MeetColors.warning
                                    remoteAvailability == RemoteAvailability.OFFLINE -> MeetColors.error
                                    else -> MeetColors.neonGreen
                                },
                            ),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val statusText = when {
                        pendingLocalReports > 0 -> "$pendingLocalReports reporte(s) por sincronizar"
                        remoteAvailability == RemoteAvailability.UNKNOWN -> "Conectividad no comprobada"
                        remoteAvailability == RemoteAvailability.OFFLINE -> "Sin conexión remota"
                        remoteAvailability == RemoteAvailability.DEGRADED -> "Servicio degradado"
                        else -> "Red de seguridad activa"
                    }
                    Text(
                        statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MeetColors.textSecondary,
                    )
                }

                if (pendingLocalReports > 0) {
                    Text(
                        "PENDIENTE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = MeetColors.warning,
                        letterSpacing = 1.sp,
                    )
                } else {
                    Text(
                        "ONLINE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = MeetColors.neonGreen,
                        letterSpacing = 1.sp,
                    )
                }
            }
        }
    }
}
