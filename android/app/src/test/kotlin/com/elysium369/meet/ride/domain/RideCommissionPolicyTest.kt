package com.elysium369.meet.ride.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RideCommissionPolicyTest {

    private val crc = CurrencyCode.of("CRC")

    @Test
    fun `public commission is exactly five hundred basis points`() {
        assertEquals(500, RideCommissionPolicy.platformRate.value)
        assertEquals("ride-commission-v1", RideCommissionPolicy.version)
    }

    @Test
    fun `five percent rounding is deterministic at minor unit boundaries`() {
        val fixtures = mapOf(
            0L to 0L,
            1L to 0L,
            9L to 0L,
            10L to 1L,
            99L to 5L,
            100L to 5L,
            2_399L to 120L,
            2_400L to 120L,
            2_450L to 123L,
            Long.MAX_VALUE to 461_168_601_842_738_790L,
        )

        fixtures.forEach { (base, expected) ->
            assertEquals(
                "Unexpected commission for $base",
                expected,
                CommissionCalculator.calculate(
                    base = AmountMinor.of(base),
                    rate = RideCommissionPolicy.platformRate,
                ).value,
            )
        }
    }

    @Test
    fun `commissionable base includes only driver-earned approved components`() {
        val amounts = CommissionableRideAmounts(
            currency = crc,
            transportFare = AmountMinor.of(100_000),
            approvedWait = AmountMinor.of(2_000),
            approvedStops = AmountMinor.of(3_000),
            approvedSurcharges = AmountMinor.of(1_000),
            collectedCancellationFee = AmountMinor.zero,
            driverFundedDiscount = AmountMinor.of(500),
            refundedCommissionable = AmountMinor.of(1_000),
            tip = AmountMinor.of(15_000),
            tolls = AmountMinor.of(5_000),
            taxes = AmountMinor.of(13_000),
            platformFundedPromotion = AmountMinor.of(10_000),
            processorFees = AmountMinor.of(500),
        )

        val result = RideCommissionPolicy.calculate(amounts)

        assertEquals(RideMoney.of(104_500, "CRC"), result.commissionableBase)
        assertEquals(RideMoney.of(5_225, "CRC"), result.platformCommission)
        assertEquals("ride-commission-v1", result.policyVersion)
    }

    @Test
    fun `excluded components never increase commission`() {
        val onlyExcluded = CommissionableRideAmounts(
            currency = crc,
            tip = AmountMinor.of(10_000),
            tolls = AmountMinor.of(10_000),
            taxes = AmountMinor.of(10_000),
            platformFundedPromotion = AmountMinor.of(10_000),
            processorFees = AmountMinor.of(10_000),
        )

        val result = RideCommissionPolicy.calculate(onlyExcluded)

        assertEquals(RideMoney.zero(crc), result.commissionableBase)
        assertEquals(RideMoney.zero(crc), result.platformCommission)
    }

    @Test
    fun `refunds and driver-funded discounts floor the base at zero`() {
        val result = RideCommissionPolicy.calculate(
            CommissionableRideAmounts(
                currency = crc,
                transportFare = AmountMinor.of(1_000),
                driverFundedDiscount = AmountMinor.of(600),
                refundedCommissionable = AmountMinor.of(600),
            ),
        )

        assertEquals(RideMoney.zero(crc), result.commissionableBase)
        assertEquals(RideMoney.zero(crc), result.platformCommission)
    }

    @Test
    fun `amount and basis point types reject invalid values`() {
        assertThrows(IllegalArgumentException::class.java) {
            AmountMinor.of(-1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BasisPoints.of(-1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BasisPoints.of(10_001)
        }
    }

    @Test
    fun `included component overflow is rejected instead of wrapping`() {
        assertThrows(ArithmeticException::class.java) {
            RideCommissionPolicy.calculate(
                CommissionableRideAmounts(
                    currency = crc,
                    transportFare = AmountMinor.of(Long.MAX_VALUE),
                    approvedWait = AmountMinor.of(1),
                ),
            )
        }
    }

    @Test
    fun `canonical final fare fixture charges 230 and excludes tip toll and promo`() {
        val result = RideCommissionPolicy.calculate(
            CommissionableRideAmounts(
                currency = crc,
                transportFare = AmountMinor.of(4_600),
                tip = AmountMinor.of(500),
                tolls = AmountMinor.of(700),
                platformFundedPromotion = AmountMinor.of(300),
            ),
        )

        assertEquals(RideMoney.of(4_600, "CRC"), result.commissionableBase)
        assertEquals(RideMoney.of(230, "CRC"), result.platformCommission)
    }
}
