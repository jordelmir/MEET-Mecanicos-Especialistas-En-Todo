package com.elysium369.meet.ride.domain

import com.elysium369.meet.core.money.CurrencyCode
import com.elysium369.meet.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RideMoneyTest {

    private fun Money.commission(basisPoints: Int = RideCommissionPolicy.PLATFORM_RATE_BASIS_POINTS): Money {
        val result = CommissionCalculator.calculate(
            base = AmountMinor.of(amountMinor),
            rate = BasisPoints.of(basisPoints),
        )
        return Money(result.value, currency)
    }

    @Test
    fun `costa rica launch grant charges exactly five percent`() {
        val grant = Money.of(amountMinor = 100_000, currency = "CRC")

        assertEquals(Money.of(5_000, "CRC"), grant.commission())
        assertEquals(Money.of(100_000, "CRC"), CostaRicaRidePolicy.promotionalGrant)
    }

    @Test
    fun `commission rounds half up in minor units`() {
        assertEquals(Money.of(10, "USD"), Money.of(199, "USD").commission())
        assertEquals(Money.of(50, "USD"), Money.of(1_001, "USD").commission())
        assertEquals(Money.of(1, "CRC"), Money.of(10, "CRC").commission())
        assertEquals(Money.of(0, "CRC"), Money.of(9, "CRC").commission())
    }

    @Test
    fun `commission is deterministic and retains currency`() {
        val fare = Money.of(8_765_432, "crc")

        val first = fare.commission()
        val second = fare.commission()

        assertEquals(first, second)
        assertEquals(CurrencyCode.fromString("CRC"), first.currency)
    }

    @Test
    fun `money rejects negative values and unsupported currency`() {
        assertThrows(IllegalArgumentException::class.java) {
            Money.of(-1, "CRC")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Money.of(1, "XYZ")
        }
    }

    @Test
    fun `arithmetic rejects mixed currencies and negative results`() {
        val crc = Money.of(1_000, "CRC")
        val usd = Money.of(1_000, "USD")

        assertThrows(IllegalArgumentException::class.java) {
            crc + usd
        }
        assertThrows(IllegalArgumentException::class.java) {
            crc - Money.of(1_001, "CRC")
        }
    }

    @Test
    fun `commission handles the largest supported fare without multiplication overflow`() {
        val fare = Money.of(Long.MAX_VALUE, "CRC")

        assertEquals(461_168_601_842_738_790L, fare.commission().amountMinor)
    }
}
