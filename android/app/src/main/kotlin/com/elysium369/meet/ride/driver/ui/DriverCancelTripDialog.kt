package com.elysium369.meet.ride.driver.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.ui.theme.MeetColors

data class DriverCancellationOption(
    val code: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val requiresDetail: Boolean = false,
)

val DRIVER_CANCELLATION_OPTIONS = listOf(
    DriverCancellationOption(
        code = "SAFETY_CONCERN",
        title = "Razón de seguridad personal",
        description = "Riesgo en el trayecto, entorno hostil o situación insegura para continuar.",
        icon = Icons.Default.Security,
    ),
    DriverCancellationOption(
        code = "UNSAFE_VEHICLE_CONDITION",
        title = "Avería o falla mecánica",
        description = "Neumático pinchado, sobrecalentamiento, falla eléctrica o problema mecánico.",
        icon = Icons.Default.CarCrash,
    ),
    DriverCancellationOption(
        code = "DANGEROUS_PICKUP",
        title = "Punto de recogida inaccesible",
        description = "Calle cerrada, acceso denegado, derrumbe o paso intransitable.",
        icon = Icons.Default.LocationOff,
    ),
    DriverCancellationOption(
        code = "PASSENGER_NO_SHOW",
        title = "Pasajero no responde / no se presenta",
        description = "Tiempo de espera agotado o pasajero ilocalizable en el punto.",
        icon = Icons.Default.PersonOff,
    ),
    DriverCancellationOption(
        code = "MEDICAL_EMERGENCY",
        title = "Emergencia médica o personal",
        description = "Problema de salud urgente o situación familiar crítica que impide conducir.",
        icon = Icons.Default.MedicalServices,
    ),
    DriverCancellationOption(
        code = "CHANGE_OF_PLANS",
        title = "Tráfico severo o bloqueo vial",
        description = "Congestión extrema, manifestación o ruta cortada sin desvío viable.",
        icon = Icons.Default.Traffic,
    ),
    DriverCancellationOption(
        code = "OTHER",
        title = "Otro motivo justificado",
        description = "Indica la razón específica para la auditoría operacional de MEET.",
        icon = Icons.Default.Edit,
        requiresDetail = true,
    ),
)

@Composable
fun DriverCancelTripDialog(
    onConfirm: (reasonCode: String, detail: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isAfterArrival: Boolean = false,
) {
    var selectedCode by remember { mutableStateOf("SAFETY_CONCERN") }
    var customDetail by remember { mutableStateOf("") }
    val selectedOption = DRIVER_CANCELLATION_OPTIONS.firstOrNull { it.code == selectedCode }
    val isOtherSelected = selectedOption?.requiresDetail == true
    val canConfirm = enabled && (!isOtherSelected || customDetail.trim().isNotBlank())

    AlertDialog(
        onDismissRequest = { if (enabled) onDismiss() },
        containerColor = MeetColors.backgroundDark,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth(),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MeetColors.error.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MeetColors.error,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp),
                    )
                }
                Column {
                    Text(
                        text = "Cancelar viaje en camino",
                        color = MeetColors.textPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                    )
                    Text(
                        text = "Selecciona el motivo justificado",
                        color = MeetColors.textSecondary,
                        fontSize = 11.sp,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (isAfterArrival) {
                    Surface(
                        color = MeetColors.error.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, MeetColors.error),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("⚠️", fontSize = 16.sp)
                            Text(
                                text = "Penalización del 5%: Al cancelar habiendo llegado al lugar o iniciado el viaje, se rebajará la comisión del 5% de tu billetera.",
                                color = Color(0xFFFF8A80),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(DRIVER_CANCELLATION_OPTIONS, key = { it.code }) { option ->
                        val isSelected = option.code == selectedCode
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MeetColors.cardBackground else MeetColors.cardBackground.copy(alpha = 0.5f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MeetColors.neonGreen else MeetColors.borderBlue.copy(alpha = 0.4f),
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = enabled) { selectedCode = option.code },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) MeetColors.neonGreen.copy(alpha = 0.2f) else MeetColors.backgroundDark,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = null,
                                        tint = if (isSelected) MeetColors.neonGreen else MeetColors.textSecondary,
                                        modifier = Modifier
                                            .padding(6.dp)
                                            .size(18.dp),
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.title,
                                        color = if (isSelected) MeetColors.neonGreen else MeetColors.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    )
                                    Text(
                                        text = option.description,
                                        color = MeetColors.textSecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp,
                                    )
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Seleccionado",
                                        tint = MeetColors.neonGreen,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                if (isOtherSelected) {
                    OutlinedTextField(
                        value = customDetail,
                        onValueChange = { customDetail = it.take(280) },
                        label = { Text("Detalle del motivo *", fontSize = 12.sp) },
                        placeholder = { Text("Describe la razón de cancelación...", fontSize = 12.sp) },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeetColors.neonGreen,
                            unfocusedBorderColor = MeetColors.borderBlue,
                            focusedTextColor = MeetColors.textPrimary,
                            unfocusedTextColor = MeetColors.textPrimary,
                        ),
                    )
                }

                Text(
                    text = "La cancelación se registra en la auditoría inmutable de MEET. Si el motivo es de seguridad, se activan protocolos preventivos.",
                    color = MeetColors.textMuted,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalDetail = if (isOtherSelected) {
                        customDetail.trim()
                    } else {
                        selectedOption?.title ?: "Cancelado por el conductor"
                    }
                    onConfirm(selectedCode, finalDetail)
                },
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeetColors.error,
                    contentColor = Color.White,
                    disabledContainerColor = MeetColors.error.copy(alpha = 0.3f),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "Confirmar Cancelación",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = enabled,
            ) {
                Text(
                    text = "Volver al Viaje",
                    color = MeetColors.textSecondary,
                    fontSize = 13.sp,
                )
            }
        },
    )
}
