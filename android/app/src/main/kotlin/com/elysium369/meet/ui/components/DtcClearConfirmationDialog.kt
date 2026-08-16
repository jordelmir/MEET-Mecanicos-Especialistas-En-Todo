package com.elysium369.meet.ui.components

import androidx.compose.runtime.Composable
import com.elysium369.meet.data.supabase.Vehicle
import com.elysium369.meet.domain.diagnostics.ClearDtcAuthorization
import com.elysium369.meet.domain.diagnostics.ClearDtcBlockReason

@Composable
fun DtcClearConfirmationDialog(
    authorization: ClearDtcAuthorization,
    selectedVehicle: Vehicle?,
    onDismiss: () -> Unit,
    onConfirmVehicle: (Vehicle) -> Unit,
    onExecuteClear: () -> Unit,
) {
    val vehicleLabel = selectedVehicle?.let { "${it.year} ${it.make} ${it.model}" }
    val blockedMessage = (authorization as? ClearDtcAuthorization.Blocked)?.let { blocked ->
        when (blocked.reason) {
            ClearDtcBlockReason.NO_SELECTED_VEHICLE ->
                "Selecciona primero el vehículo del Garage que está físicamente conectado. No se enviará ningún comando."
            ClearDtcBlockReason.VIN_CONFLICT ->
                "El VIN leído de la ECU no coincide con el vehículo seleccionado. Corrige la selección; el borrado permanece bloqueado."
            ClearDtcBlockReason.OBSERVED_VIN_NOT_LINKED ->
                "La ECU entregó un VIN que todavía no está vinculado al vehículo seleccionado. Selecciona o registra el vehículo correspondiente."
            ClearDtcBlockReason.BINDING_MISMATCH ->
                "La sesión física está vinculada a otro vehículo. Selecciona el vehículo correcto antes de continuar."
        }
    }
    val requiresConfirmation = authorization == ClearDtcAuthorization.RequiresUserConfirmation
    val isBlocked = authorization is ClearDtcAuthorization.Blocked
    val message = when {
        blockedMessage != null -> blockedMessage
        requiresConfirmation ->
            "La ECU no entregó VIN. Confirma que el vehículo conectado es ${vehicleLabel ?: "el vehículo seleccionado"}. " +
                "MEET vinculará únicamente esta sesión física, repetirá el pre-scan y construirá el plan desde evidencia nueva.\n\n" +
                destructiveClearWarning()
        else -> destructiveClearWarning()
    }

    EliteDialog(
        title = when {
            isBlocked -> "Borrado bloqueado"
            requiresConfirmation -> "Confirmar vehículo y borrar DTC"
            else -> "Acción avanzada: borrar memoria DTC"
        },
        message = message,
        onDismiss = onDismiss,
        onConfirm = {
            onDismiss()
            when (authorization) {
                ClearDtcAuthorization.Authorized -> onExecuteClear()
                ClearDtcAuthorization.RequiresUserConfirmation -> selectedVehicle?.let {
                    onConfirmVehicle(it)
                    onExecuteClear()
                }
                is ClearDtcAuthorization.Blocked -> Unit
            }
        },
        confirmText = when {
            isBlocked -> "ENTENDIDO"
            requiresConfirmation -> "VINCULAR Y BORRAR"
            else -> "CONFIRMAR BORRADO"
        },
        dismissText = if (isBlocked) "CERRAR" else "CANCELAR",
        isDestructive = !isBlocked,
    )
}

private fun destructiveClearWarning(): String =
    "Mode 04 se limitará a hallazgos SAE OBD y UDS Service 14 a ECU físicas demostradas por evidencia. " +
        "Puede apagar la MIL, borrar freeze frame y reiniciar monitores; no demuestra que la falla fue reparada. " +
        "Después se repetirá el escaneo y cualquier resultado parcial conservará los hallazgos sin resolver.\n\n" +
        "Usa contacto ON, motor apagado y voltaje estable. ¿Confirmas esta acción irreversible?"
