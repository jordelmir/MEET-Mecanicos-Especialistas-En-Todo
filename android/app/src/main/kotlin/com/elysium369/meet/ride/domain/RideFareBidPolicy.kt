package com.elysium369.meet.ride.domain

object RideFareBidPolicy {
    const val CRC_STEP_MINOR = 300L
    const val CRC_MINIMUM_MINOR = 900L
    const val CRC_MAXIMUM_MINOR = 30_000L
    const val USD_STEP_MINOR = 100L

    @Deprecated("Use stepMinor instead", replaceWith = ReplaceWith("stepMinor(currency)"))
    const val CRC_STEP = 300.0
    @Deprecated("Use minimumMinor instead", replaceWith = ReplaceWith("minimumMinor(currency)"))
    const val CRC_MINIMUM = 900.0
    @Deprecated("Use maximumMinor instead", replaceWith = ReplaceWith("maximumMinor(currency)"))
    const val CRC_MAXIMUM = 30_000.0
    @Deprecated("Use stepMinor instead", replaceWith = ReplaceWith("stepMinor(currency)"))
    const val USD_STEP = 1.0

    fun stepMinor(currency: String): Long =
        if (currency.equals("CRC", ignoreCase = true)) CRC_STEP_MINOR else USD_STEP_MINOR

    fun normalizeMinor(amountMinor: Long, currency: String): Long {
        val step = stepMinor(currency)
        val snapped = (amountMinor / step) * step
        return when {
            currency.equals("CRC", ignoreCase = true) -> snapped.coerceIn(CRC_MINIMUM_MINOR, CRC_MAXIMUM_MINOR)
            else -> snapped.coerceAtLeast(step)
        }
    }

    fun adjustMinor(amountMinor: Long, currency: String, direction: Int): Long =
        normalizeMinor(amountMinor + stepMinor(currency) * direction.coerceIn(-1, 1), currency)

    @Deprecated("Use stepMinor instead", replaceWith = ReplaceWith("stepMinor(currency)"))
    fun step(currency: String): Double =
        if (currency.equals("CRC", ignoreCase = true)) CRC_STEP else USD_STEP

    @Deprecated("Use normalizeMinor instead", replaceWith = ReplaceWith("normalizeMinor(amountMinor.toLong(), currency)"))
    fun normalize(amount: Double, currency: String): Double {
        val step = step(currency)
        val snapped = Math.round(amount / step).toDouble() * step
        return if (currency.equals("CRC", ignoreCase = true)) {
            snapped.coerceIn(CRC_MINIMUM, CRC_MAXIMUM)
        } else {
            snapped.coerceAtLeast(step)
        }
    }

    @Deprecated("Use adjustMinor instead", replaceWith = ReplaceWith("adjustMinor(amountMinor.toLong(), currency, direction)"))
    fun adjust(amount: Double, currency: String, direction: Int): Double =
        normalize(amount + step(currency) * direction.coerceIn(-1, 1), currency)
}
