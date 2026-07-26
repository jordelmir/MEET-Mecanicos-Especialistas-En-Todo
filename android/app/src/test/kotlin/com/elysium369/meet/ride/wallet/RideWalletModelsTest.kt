package com.elysium369.meet.ride.wallet

import com.elysium369.meet.ride.domain.RideMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RideWalletModelsTest {

    @Test
    fun `costa rica driver receives one non withdrawable promotional grant`() {
        val grant = RidePromotionalGrant.costaRicaPilot(
            driverId = "driver-1",
            createdAtEpochMs = 1_000,
        )
        val duplicate = RidePromotionalGrant.costaRicaPilot(
            driverId = "driver-1",
            createdAtEpochMs = 1_000,
        )

        val projection = RideWalletLedger.project(listOf(grant, duplicate))

        assertEquals(RideMoney.of(100_000, "CRC"), projection.posted)
        assertEquals(RideMoney.of(100_000, "CRC"), projection.available)
        assertEquals("promo:cr-pilot-2026:driver-1", grant.idempotencyKey)
        assertEquals(false, grant.withdrawable)
    }

    @Test
    fun `reservation reduces available balance without deducting posted balance`() {
        val projection = RideWalletLedger.project(
            listOf(
                grant(),
                entry(
                    id = "reserve-1",
                    key = "trip-1:commission:reserve",
                    type = RideLedgerEntryType.COMMISSION_RESERVED,
                    amount = 5_000,
                    tripId = "trip-1",
                ),
            ),
        )

        assertEquals(RideMoney.of(100_000, "CRC"), projection.posted)
        assertEquals(RideMoney.of(5_000, "CRC"), projection.reserved)
        assertEquals(RideMoney.of(95_000, "CRC"), projection.available)
    }

    @Test
    fun `completion captures commission exactly once`() {
        val capture = entry(
            id = "capture-1",
            key = "trip-1:commission:capture",
            type = RideLedgerEntryType.COMMISSION_CAPTURED,
            amount = 5_000,
            tripId = "trip-1",
        )

        val projection = RideWalletLedger.project(
            listOf(
                grant(),
                reserve(),
                capture,
                capture,
            ),
        )

        assertEquals(RideMoney.of(95_000, "CRC"), projection.posted)
        assertEquals(RideMoney.of(0, "CRC"), projection.reserved)
        assertEquals(RideMoney.of(95_000, "CRC"), projection.available)
    }

    @Test
    fun `cancellation releases reserved commission without a charge`() {
        val projection = RideWalletLedger.project(
            listOf(
                grant(),
                reserve(),
                entry(
                    id = "release-1",
                    key = "trip-1:commission:release",
                    type = RideLedgerEntryType.COMMISSION_RELEASED,
                    amount = 5_000,
                    tripId = "trip-1",
                ),
            ),
        )

        assertEquals(RideMoney.of(100_000, "CRC"), projection.posted)
        assertEquals(RideMoney.of(0, "CRC"), projection.reserved)
        assertEquals(RideMoney.of(100_000, "CRC"), projection.available)
    }

    @Test
    fun `conflicting duplicate key is rejected`() {
        val first = reserve()
        val conflict = first.copy(
            id = "reserve-conflict",
            amount = RideMoney.of(4_000, "CRC"),
        )

        assertThrows(IllegalArgumentException::class.java) {
            RideWalletLedger.project(listOf(grant(), first, conflict))
        }
    }

    @Test
    fun `insufficient balance and mixed currencies are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            RideWalletLedger.project(
                listOf(
                    grant(),
                    entry(
                        id = "reserve-too-large",
                        key = "trip-2:commission:reserve",
                        type = RideLedgerEntryType.COMMISSION_RESERVED,
                        amount = 100_001,
                        tripId = "trip-2",
                    ),
                ),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            RideWalletLedger.project(
                listOf(
                    grant(),
                    RideLedgerEntry(
                        id = "usd-topup",
                        idempotencyKey = "purchase-usd",
                        type = RideLedgerEntryType.TOP_UP_CONFIRMED,
                        amount = RideMoney.of(10, "USD"),
                        tripId = null,
                        createdAtEpochMs = 2_000,
                    ),
                ),
            )
        }
    }

    private fun grant(): RideLedgerEntry =
        RidePromotionalGrant.costaRicaPilot("driver-1", 1_000)

    private fun reserve(): RideLedgerEntry =
        entry(
            id = "reserve-1",
            key = "trip-1:commission:reserve",
            type = RideLedgerEntryType.COMMISSION_RESERVED,
            amount = 5_000,
            tripId = "trip-1",
        )

    private fun entry(
        id: String,
        key: String,
        type: RideLedgerEntryType,
        amount: Long,
        tripId: String?,
    ): RideLedgerEntry =
        RideLedgerEntry(
            id = id,
            idempotencyKey = key,
            type = type,
            amount = RideMoney.of(amount, "CRC"),
            tripId = tripId,
            createdAtEpochMs = 2_000,
        )
}
