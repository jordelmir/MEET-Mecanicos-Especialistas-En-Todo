package com.elysium369.meet.core.money

import org.junit.Assert.*
import org.junit.Test

class MoneyTest {

    @Test
    fun testZeroDoubleArithmetic() {
        val priceA = Money(1550L, CurrencyCode.USD) // $15.50
        val priceB = Money(450L, CurrencyCode.USD)  // $4.50

        val total = priceA + priceB
        assertEquals(2000L, total.amountMinor)
        assertEquals("$20.00", total.formatted())

        val diff = priceA - priceB
        assertEquals(1100L, diff.amountMinor)
        assertEquals("$11.00", diff.formatted())

        val multiplied = priceB * 3
        assertEquals(1350L, multiplied.amountMinor)
        assertEquals("$13.50", multiplied.formatted())

        val divided = total / 4
        assertEquals(500L, divided.amountMinor)
        assertEquals("$5.00", divided.formatted())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCurrencyMismatchThrowsException() {
        val usd = Money(100L, CurrencyCode.USD)
        val crc = Money(50000L, CurrencyCode.CRC)
        val ignored = usd + crc
    }

    @Test
    fun testFormattingCrcZeroDecimals() {
        val crc = Money(25000L, CurrencyCode.CRC)
        assertEquals("₡25,000", crc.formatted())
    }
}
