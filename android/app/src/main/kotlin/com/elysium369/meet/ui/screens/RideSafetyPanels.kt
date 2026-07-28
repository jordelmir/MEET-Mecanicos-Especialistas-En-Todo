package com.elysium369.meet.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.BuildConfig
import com.elysium369.meet.data.local.entities.DtcEventEntity
import com.elysium369.meet.data.supabase.Vehicle
import com.elysium369.meet.ride.domain.RideCancellationPolicy
import com.elysium369.meet.ride.domain.RideCancellationReason
import com.elysium369.meet.ride.domain.RideActorRole
import com.elysium369.meet.ride.domain.RideConsentPolicy
import com.elysium369.meet.ride.domain.RideShareCategory
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.theme.MeetColors
import java.text.DateFormat
import java.util.Date

@Composable
fun RideWalletStatusCard(
    modifier: Modifier = Modifier,
    onRequestTopUp: () -> Unit = {},
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MeetColors.backgroundDeep),
        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = "BILLETERA DE TRABAJO",
                color = MeetColors.cyberCyan,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
            )
            Text(
                text = "Regalía inicial configurada: ₡100.000",
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Saldo disponible: pendiente de sincronización segura",
                color = MeetColors.warning,
                fontSize = 12.sp,
            )
            Text(
                text = "MEET reserva el 5% al aceptar y solo lo debita cuando el viaje se completa. Cancelar no genera cobro automático durante el piloto.",
                color = MeetColors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            Button(
                onClick = onRequestTopUp,
                enabled = BuildConfig.RIDE_PLAY_BILLING_POLICY_APPROVED,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
            ) {
                Text(
                    if (BuildConfig.RIDE_PLAY_BILLING_POLICY_APPROVED) {
                        "RECARGAR SALDO"
                    } else {
                        "RECARGA EN REVISIÓN DE POLÍTICA"
                    },
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            if (!BuildConfig.RIDE_PLAY_BILLING_POLICY_APPROVED) {
                Text(
                    text = "Google Play Billing no está activado para pagar transporte físico. La arquitectura admite un proveedor de recarga autorizado sin alterar el ledger.",
                    color = MeetColors.textMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}

@Composable
fun RideSharingCenter(
    enabledCategories: Set<RideShareCategory>,
    vehicle: Vehicle?,
    activeDtcs: List<DtcEventEntity>,
    historicalDtcs: List<DtcEventEntity>,
    currentGps: ObdViewModel.GpsLocationInfo?,
    onCategoryChanged: (RideShareCategory, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "CENTRO DE CONFIANZA VEHICULAR",
                color = MeetColors.neonGreen,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
            )
            Text(
                text = "Tú decides qué evidencia ve el pasajero durante este viaje. Los datos mecánicos empiezan apagados y se revocan al finalizar.",
                color = MeetColors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            vehicle?.let {
                Text(
                    text = "${it.make} ${it.model} ${it.year} · ${it.engine}",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
            } ?: Text(
                text = "Vehículo seleccionado: dato no capturado",
                color = MeetColors.warning,
                fontSize = 12.sp,
            )

            RideShareCategory.entries.forEach { category ->
                val enabled = category in enabledCategories
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = category.rideLabel(),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        )
                        Text(
                            text = category.preview(vehicle, activeDtcs, historicalDtcs, currentGps),
                            color = if (enabled) MeetColors.textSecondary else MeetColors.textMuted,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { onCategoryChanged(category, it) },
                    )
                }
            }
            Text(
                text = "Estado: consentimiento local listo; publicación remota requiere una sesión autenticada y sincronización activa.",
                color = MeetColors.warning,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
fun RidePassengerTrustCard(
    vehicleDescription: String?,
    sharedCategories: Set<RideShareCategory>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
        border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "TRANSPARENCIA DEL VEHÍCULO",
                color = MeetColors.cyberCyan,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
            )
            Text(
                text = vehicleDescription?.takeIf { it.isNotBlank() } ?: "Vehículo: dato no capturado",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            )
            if (sharedCategories.none { it in RideConsentPolicy.mechanicalCategories }) {
                Text(
                    text = "El conductor no ha compartido evidencia mecánica en este viaje.",
                    color = MeetColors.textMuted,
                    fontSize = 11.sp,
                )
            } else {
                sharedCategories
                    .filter { it in RideConsentPolicy.mechanicalCategories }
                    .sortedBy { it.ordinal }
                    .forEach { category ->
                        Text(
                            text = "• ${category.rideLabel()}: autorizado; esperando evidencia sincronizada",
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
            }
            HorizontalDivider(color = MeetColors.borderSubtle)
            Text(
                text = "Compartir no certifica que el vehículo sea seguro. Verifica fuente, fecha y evidencia; si no aparecen, el dato no fue capturado.",
                color = MeetColors.warning,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
fun RideCancellationDialog(
    actorRole: RideActorRole,
    onDismiss: () -> Unit,
    onConfirm: (RideCancellationReason, String?) -> Unit,
) {
    var selected by remember { mutableStateOf<RideCancellationReason?>(null) }
    var detail by remember { mutableStateOf("") }
    val isValid = selected?.let { RideCancellationPolicy.isDetailValid(it, detail) } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancelar viaje de forma segura") },
        text = {
            Column(
                modifier = Modifier
                    .height(420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${if (actorRole == RideActorRole.DRIVER) "Conductor" else "Pasajero"}: selecciona el motivo real. Los casos de seguridad se señalan para revisión; el piloto no aplica cargos automáticos.",
                    fontSize = 12.sp,
                )
                RideCancellationPolicy.reasonsFor(actorRole).forEach { reason ->
                    OutlinedButton(
                        onClick = { selected = reason },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(
                            1.dp,
                            when {
                                selected == reason -> MeetColors.cyberCyan
                                reason.safetyRelated -> MeetColors.warning.copy(alpha = 0.7f)
                                else -> MeetColors.borderSubtle
                            },
                        ),
                    ) {
                        Text(
                            text = reason.cancellationLabel(),
                            fontSize = 11.sp,
                            color = if (reason.safetyRelated) MeetColors.warning else Color.White,
                        )
                    }
                }
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it.take(500) },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            if (selected == RideCancellationReason.OTHER) {
                                "Detalle obligatorio"
                            } else {
                                "Detalle opcional"
                            },
                        )
                    },
                    supportingText = { Text("${detail.length}/500") },
                    minLines = 2,
                )
                selected?.let { reason ->
                    val decision = RideCancellationPolicy.evaluate(reason)
                    Text(
                        text = if (decision.requiresSafetyReview) {
                            "Este caso requiere revisión de seguridad."
                        } else {
                            "La cancelación quedará registrada en el historial."
                        },
                        color = if (decision.requiresSafetyReview) MeetColors.warning else MeetColors.textSecondary,
                        fontSize = 11.sp,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selected?.let {
                        onConfirm(it, detail.trim().takeIf(String::isNotEmpty))
                    }
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
            ) {
                Text("CONFIRMAR CANCELACIÓN", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("VOLVER")
            }
        },
    )
}

private fun RideShareCategory.rideLabel(): String =
    when (this) {
        RideShareCategory.EXACT_LOCATION -> "Ubicación exacta del conductor"
        RideShareCategory.BASIC_TELEMETRY -> "Telemetría básica"
        RideShareCategory.ACTIVE_DTCS -> "DTC activos"
        RideShareCategory.DTC_HISTORY -> "Historial DTC"
        RideShareCategory.MAINTENANCE -> "Mantenimiento registrado"
        RideShareCategory.INSTALLED_PARTS -> "Piezas instaladas"
        RideShareCategory.CERTIFIED_REPORTS -> "Reportes certificados"
    }

private fun RideShareCategory.preview(
    vehicle: Vehicle?,
    activeDtcs: List<DtcEventEntity>,
    historicalDtcs: List<DtcEventEntity>,
    currentGps: ObdViewModel.GpsLocationInfo?,
): String =
    when (this) {
        RideShareCategory.EXACT_LOCATION ->
            currentGps?.let { "GPS del dispositivo · precisión ±${it.accuracy.toInt()} m" }
                ?: "GPS no disponible"
        RideShareCategory.BASIC_TELEMETRY ->
            currentGps?.let { "GPS del dispositivo · ${(it.speed * 3.6f).toInt()} km/h · no es OBD" }
                ?: "OBD no disponible"
        RideShareCategory.ACTIVE_DTCS ->
            if (vehicle == null) "Sin vehículo seleccionado" else {
                activeDtcs.takeIf { it.isNotEmpty() }
                    ?.joinToString(limit = 3) { "${it.code} (${it.severity})" }
                    ?: "Sin DTC activos capturados"
            }
        RideShareCategory.DTC_HISTORY ->
            historicalDtcs.maxByOrNull { it.lastSeenAt }?.let {
                "${it.code} · ${DateFormat.getDateTimeInstance().format(Date(it.lastSeenAt))}"
            } ?: "Historial DTC no capturado"
        RideShareCategory.MAINTENANCE -> "Requiere eventos de servicio verificados"
        RideShareCategory.INSTALLED_PARTS -> "Requiere registro de pieza y evidencia"
        RideShareCategory.CERTIFIED_REPORTS -> "Requiere reporte con hash y QR verificable"
    }

private fun RideCancellationReason.cancellationLabel(): String =
    when (this) {
        RideCancellationReason.SAFETY_CONCERN -> "Me siento en riesgo"
        RideCancellationReason.UNACCOMPANIED_MINOR -> "Menor sin acompañante"
        RideCancellationReason.CHILD_SEAT_REQUIRED -> "Falta silla infantil requerida"
        RideCancellationReason.TOO_MANY_PASSENGERS -> "Demasiados pasajeros"
        RideCancellationReason.IDENTITY_MISMATCH -> "La identidad no coincide"
        RideCancellationReason.VEHICLE_MISMATCH -> "El vehículo no coincide"
        RideCancellationReason.HARASSMENT -> "Acoso o conducta inapropiada"
        RideCancellationReason.PROHIBITED_ITEM_OR_ACTIVITY -> "Objeto o actividad prohibida"
        RideCancellationReason.DANGEROUS_PICKUP -> "Punto de recogida peligroso"
        RideCancellationReason.MEDICAL_EMERGENCY -> "Emergencia médica"
        RideCancellationReason.UNSAFE_VEHICLE_CONDITION -> "Condición insegura del vehículo"
        RideCancellationReason.PASSENGER_NO_SHOW -> "El pasajero no llegó"
        RideCancellationReason.DRIVER_NO_SHOW -> "El conductor no llegó"
        RideCancellationReason.EXCESSIVE_WAIT -> "Espera excesiva"
        RideCancellationReason.INCORRECT_PICKUP -> "Punto de recogida incorrecto"
        RideCancellationReason.INCORRECT_DESTINATION -> "Destino incorrecto"
        RideCancellationReason.CHANGE_OF_PLANS -> "Cambio de planes"
        RideCancellationReason.DUPLICATE_OR_ACCIDENTAL -> "Solicitud duplicada o accidental"
        RideCancellationReason.OTHER -> "Otro motivo"
    }
