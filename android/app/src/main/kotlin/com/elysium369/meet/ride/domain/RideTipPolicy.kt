package com.elysium369.meet.ride.domain

import java.math.BigDecimal

/** Uses the same currency scale as the ride command contract (whole CRC, USD cents). */
object RideTipPolicy {
    const val MAX_MINOR = 100_000L

    fun isValid(amount: Long, currency: String): Boolean =
        amount in 1..MAX_MINOR && currency in setOf("CRC", "USD")

    fun display(amount: Long, currency: String): String =
        BigDecimal.valueOf(amount, if (currency == "CRC") 0 else 2).toPlainString()

    fun parseMajor(value: String, currency: String): Long? = try {
        BigDecimal(value).movePointRight(if (currency == "CRC") 0 else 2)
            .longValueExact().takeIf { isValid(it, currency) }
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: ArithmeticException) {
        null
    }
}
