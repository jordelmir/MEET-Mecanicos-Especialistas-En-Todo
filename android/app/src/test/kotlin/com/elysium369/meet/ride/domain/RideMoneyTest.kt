package com.elysium369.meet.ride.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RideMoneyTest {

    @Test
    fun `costa rica launch grant charges exactly five percent`() {
        val grant = RideMoney.of(minorUnits = 100_000, currency = "CRC")

        assertEquals(RideMoney.of(5_000, "CRC"), grant.commission())
        assertEquals(RideMoney.of(100_000, "CRC"), CostaRicaRidePolicy.promotionalGrant)
    }

    @Test
    fun `commission rounds half up in minor units`() {
        assertEquals(RideMoney.of(10, "USD"), RideMoney.of(199, "USD").commission())
        assertEquals(RideMoney.of(50, "KWD"), RideMoney.of(1_001, "KWD").commission())
        assertEquals(RideMoney.of(1, "CRC"), RideMoney.of(10, "CRC").commission())
        assertEquals(RideMoney.of(0, "CRC"), RideMoney.of(9, "CRC").commission())
    }

    @Test
    fun `commission is deterministic and retains currency`() {
        val fare = RideMoney.of(8_765_432, "crc")

        val first = fare.commission()
        val second = fare.commission()

        assertEquals(first, second)
        assertEquals(CurrencyCode.of("CRC"), first.currency)
    }

    @Test
    fun `money rejects negative values and malformed currency`() {
        assertThrows(IllegalArgumentException::class.java) {
            RideMoney.of(-1, "CRC")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RideMoney.of(1, "COLONES")
        }
    }

    @Test
    fun `arithmetic rejects mixed currencies and negative results`() {
        val crc = RideMoney.of(1_000, "CRC")
        val usd = RideMoney.of(1_000, "USD")

        assertThrows(IllegalArgumentException::class.java) {
            crc + usd
        }
        assertThrows(IllegalArgumentException::class.java) {
            crc - RideMoney.of(1_001, "CRC")
        }
    }

    @Test
    fun `commission handles the largest supported fare without multiplication overflow`() {
        val fare = RideMoney.of(Long.MAX_VALUE, "CRC")

        assertEquals(461_168_601_842_738_790L, fare.commission().minorUnits)
    }
}
