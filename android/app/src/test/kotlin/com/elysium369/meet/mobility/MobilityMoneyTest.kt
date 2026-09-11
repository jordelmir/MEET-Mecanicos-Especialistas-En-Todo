package com.elysium369.meet.mobility

import com.elysium369.meet.core.money.CurrencyCode
import com.elysium369.meet.core.money.Money
import com.elysium369.meet.core.money.SignedMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MobilityMoneyTest {

    @Test
    fun validCurrencyCodeNormalizesToUppercase() {
        val crc = CurrencyCode.fromString("crc")
        assertEquals("CRC", crc.name)
        val usd = CurrencyCode.fromString("USD")
        assertEquals("USD", usd.name)
    }

    @Test
    fun invalidCurrencyCodeThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            CurrencyCode.fromString("CR")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CurrencyCode.fromString("123")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CurrencyCode.fromString("CRCC")
        }
    }

    @Test
    fun negativeMoneyThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            Money(-1L, CurrencyCode.fromString("CRC"))
        }
    }

    @Test
    fun moneyArithmeticWorksAndChecksCurrency() {
        val crc = CurrencyCode.fromString("CRC")
        val usd = CurrencyCode.fromString("USD")

        val m1 = Money(2500L, crc)
        val m2 = Money(1500L, crc)

        val sum = m1 + m2
        assertEquals(4000L, sum.amountMinor)

        val diff = m1 - m2
        assertEquals(1000L, diff.amountMinor)

        val mUsd = Money(100L, usd)
        assertThrows(IllegalArgumentException::class.java) {
            m1 + mUsd
        }
    }

    @Test
    fun signedMoneyAllowsNegativeForLedger() {
        val sm = SignedMoney(-500L, CurrencyCode.fromString("CRC"))
        assertEquals(-500L, sm.amountMinor)
    }
}
