package com.elysium369.meet.core.wallet

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SpecialistWalletMoneyTest {
    @Test fun commissionUsesConstitutionalRateAndHalfUp() {
        assertEquals(1L, JobCommissionSplit(10).platformFeeCrc)
        val split = JobCommissionSplit(100_000)
        assertEquals(5_000L, split.platformFeeCrc)
        assertEquals(95_000L, split.specialistNetCrc)
    }

    @Test fun largestAmountDoesNotOverflowCommissionIntermediate() {
        val split = JobCommissionSplit(Long.MAX_VALUE)
        assertEquals(461_168_601_842_738_790L, split.platformFeeCrc)
        assertEquals(Long.MAX_VALUE, Math.addExact(split.platformFeeCrc, split.specialistNetCrc))
    }

    @Test fun newCacheCannotInventGiftOrFunds() {
        val state = SpecialistWalletState("provider", "ALL")
        assertEquals(0L, state.balanceCrc)
        assertEquals(0L, state.starterGiftCrc)
    }

    @Test(expected = kotlinx.serialization.SerializationException::class)
    fun fractionalCacheAmountCannotBeSilentlyTruncated() {
        Json.decodeFromString<SpecialistWalletState>("""{"specialistId":"provider","serviceVertical":"ALL","balanceCrc":10.5}""")
    }
}
