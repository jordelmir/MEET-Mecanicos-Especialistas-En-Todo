package com.elysium369.meet.ride.payment

/**
 * TruthProof payment lifecycle states.
 * Only BANK_CONFIRMED constitutes strong financial proof.
 * Selecting "SINPE" does NOT mean "Paid".
 */
enum class RidePaymentStatus {
    PAYMENT_METHOD_SELECTED,
    PAYMENT_REQUESTED,
    USER_MARKED_SENT,
    DRIVER_MARKED_RECEIVED,
    EXTERNAL_SETTLEMENT_ATTESTED,
    BANK_CONFIRMED,
    DISPUTED;

    val isSettled: Boolean
        get() = this == BANK_CONFIRMED

    val isDisputed: Boolean
        get() = this == DISPUTED

    val displayLabelEs: String
        get() = when (this) {
            PAYMENT_METHOD_SELECTED -> "Método seleccionado"
            PAYMENT_REQUESTED -> "Pago solicitado"
            USER_MARKED_SENT -> "Pasajero marcó como enviado"
            DRIVER_MARKED_RECEIVED -> "Conductor marcó como recibido"
            EXTERNAL_SETTLEMENT_ATTESTED -> "Liquidación externa atestiguada"
            BANK_CONFIRMED -> "Confirmado por banco"
            DISPUTED -> "En disputa"
        }

    companion object {
        fun fromString(value: String?): RidePaymentStatus {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: PAYMENT_METHOD_SELECTED
        }
    }
}
