package com.elysium369.meet.ride.domain

import kotlin.math.round

object RideFareBidPolicy {
    const val CRC_STEP = 300.0
    const val CRC_MINIMUM = 900.0
    const val CRC_MAXIMUM = 30_000.0
    const val USD_STEP = 1.0

    fun step(currency: String): Double =
        if (currency.equals("CRC", ignoreCase = true)) CRC_STEP else USD_STEP

    fun normalize(amount: Double, currency: String): Double {
        val step = step(currency)
        val snapped = Math.round(amount / step).toDouble() * step
        return if (currency.equals("CRC", ignoreCase = true)) {
            snapped.coerceIn(CRC_MINIMUM, CRC_MAXIMUM)
        } else {
            snapped.coerceAtLeast(step)
        }
    }

    fun adjust(amount: Double, currency: String, direction: Int): Double =
        normalize(amount + step(currency) * direction.coerceIn(-1, 1), currency)
}
