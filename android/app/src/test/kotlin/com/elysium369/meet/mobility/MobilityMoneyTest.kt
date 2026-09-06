package com.elysium369.meet.mobility

import com.elysium369.meet.mobility.domain.models.CurrencyCode
import com.elysium369.meet.mobility.domain.models.Money
import com.elysium369.meet.mobility.domain.models.SignedMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MobilityMoneyTest {

    @Test
    fun validCurrencyCodeNormalizesToUppercase() {
        val crc = CurrencyCode.of("crc")
        assertEquals("CRC", crc.value)
        val usd = CurrencyCode.of("USD")
        assertEquals("USD", usd.value)
    }

    @Test
    fun invalidCurrencyCodeThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            CurrencyCode.of("CR")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CurrencyCode.of("123")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CurrencyCode.of("CRCC")
        }
    }

    @Test
    fun negativeMoneyThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            Money(-1L, CurrencyCode.of("CRC"))
        }
    }

    @Test
    fun moneyArithmeticWorksAndChecksCurrency() {
        val crc = CurrencyCode.of("CRC")
        val usd = CurrencyCode.of("USD")

        val m1 = Money(2500L, crc)
        val m2 = Money(1500L, crc)

        val sum = m1 + m2
        assertEquals(4000L, sum.minorUnits)

        val diff = m1 - m2
        assertEquals(1000L, diff.minorUnits)

        val mUsd = Money(100L, usd)
        assertThrows(IllegalArgumentException::class.java) {
            m1 + mUsd
        }
    }

    @Test
    fun signedMoneyAllowsNegativeForLedger() {
        val sm = SignedMoney(-500L, CurrencyCode.of("CRC"))
        assertEquals(-500L, sm.minorUnits)
    }
}
