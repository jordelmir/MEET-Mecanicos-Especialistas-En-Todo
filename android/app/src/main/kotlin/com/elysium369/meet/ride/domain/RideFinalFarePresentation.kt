package com.elysium369.meet.ride.domain

import java.math.BigDecimal

/** A quote is never promoted to a final total when the receipt is missing. */
data class RideFinalFarePresentation(val offered: String, val difference: String, val total: String) {
    companion object {
        fun from(offeredMinor: Long, finalMinor: Long?, currency: String, serverState: String?, serverVersion: Long): RideFinalFarePresentation {
            fun format(amount: Long): String =
                BigDecimal.valueOf(amount).movePointLeft(if (currency == "CRC") 0 else 2).toPlainString() + " " + currency
            val confirmed = finalMinor?.takeIf { it >= 0 && serverState == "COMPLETED" && serverVersion > 0 }
            val offered = offeredMinor.takeIf { it > 0 }
            return RideFinalFarePresentation(
                offered = offered?.let(::format) ?: "Dato no capturado",
                difference = if (confirmed != null && offered != null) {
                    // This is only the net difference; it does not assert absence of offsetting adjustments.
                    format(confirmed - offered)
                } else "Pendiente de validación",
                total = confirmed?.let(::format) ?: "Pendiente de validación",
            )
        }
    }
}
