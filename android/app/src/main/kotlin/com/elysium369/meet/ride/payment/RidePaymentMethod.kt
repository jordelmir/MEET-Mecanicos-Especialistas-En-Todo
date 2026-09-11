package com.elysium369.meet.ride.payment

/**
 * Payment method enumeration for ride fare settlement.
 * Each method has different settlement verification guarantees.
 *
 * UNKNOWN: Server did not provide a payment method. Never default to CASH
 * when the server value is null/blank — unknown must remain visible so the
 * driver/passenger is aware the method is not yet confirmed.
 */
enum class RidePaymentMethod {
    UNKNOWN,
    CASH,
    SINPE_MOVIL,
    CARD,
    WALLET;

    val requiresExternalAttestation: Boolean
        get() = this == SINPE_MOVIL || this == CASH

    val displayLabelEs: String
        get() = when (this) {
            UNKNOWN -> "Método de pago no confirmado"
            CASH -> "Efectivo"
            SINPE_MOVIL -> "SINPE Móvil"
            CARD -> "Tarjeta"
            WALLET -> "Billetera digital"
        }

    companion object {
        fun fromString(value: String?): RidePaymentMethod {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
        }
    }
}
