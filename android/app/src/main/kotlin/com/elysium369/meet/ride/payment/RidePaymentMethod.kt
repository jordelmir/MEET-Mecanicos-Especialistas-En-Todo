package com.elysium369.meet.ride.payment

/**
 * Payment method enumeration for ride fare settlement.
 * Each method has different settlement verification guarantees.
 */
enum class RidePaymentMethod {
    CASH,
    SINPE_MOVIL,
    CARD,
    WALLET;

    val requiresExternalAttestation: Boolean
        get() = this == SINPE_MOVIL || this == CASH

    val displayLabelEs: String
        get() = when (this) {
            CASH -> "Efectivo"
            SINPE_MOVIL -> "SINPE Móvil"
            CARD -> "Tarjeta"
            WALLET -> "Billetera digital"
        }

    companion object {
        fun fromString(value: String?): RidePaymentMethod {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CASH
        }
    }
}
