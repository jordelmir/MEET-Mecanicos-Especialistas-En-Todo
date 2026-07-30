package com.elysium369.meet.ride.wallet

import com.elysium369.meet.ride.domain.BasisPoints
import com.elysium369.meet.ride.domain.RideId
import com.elysium369.meet.ride.domain.RideIdempotencyKey
import com.elysium369.meet.ride.domain.RideMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RideDoubleEntryLedgerTest {

    @Test
    fun `reservation moves commission from available to reserved and remains balanced`() {
        val journal = RideLedgerJournalFactory.reserveCommission(
            transactionId = "tx-reserve-1",
            idempotencyKey = key("trip-1:commission:reserve"),
            tripId = RideId.of("trip-1"),
            driverId = "driver-1",
            amount = RideMoney.of(230, "CRC"),
            createdAtEpochMs = 1_000,
        )

        assertTrue(journal.isBalanced)
        assertEquals(230, journal.debitTotal.minorUnits)
        assertEquals(230, journal.creditTotal.minorUnits)
        assertEquals(
            setOf(
                RideLedgerAccountKind.DRIVER_AVAILABLE,
                RideLedgerAccountKind.DRIVER_RESERVED,
            ),
            journal.postings.map { it.account.kind }.toSet(),
        )
    }

    @Test
    fun `capture allocates the complete five percent without changing public commission`() {
        val rules = RevenueSplitRuleSet(
            tenantId = "tenant-cr-1",
            jurisdiction = "CR",
            contractVersion = "contract-v1",
            effectiveFromEpochMs = 0,
            rules = listOf(
                RevenueSplitRule(
                    beneficiary = RevenueBeneficiary.PLATFORM,
                    basisPoints = BasisPoints.of(300),
                ),
                RevenueSplitRule(
                    beneficiary = RevenueBeneficiary.TENANT,
                    basisPoints = BasisPoints.of(100),
                    ownerId = "tenant-cr-1",
                ),
                RevenueSplitRule(
                    beneficiary = RevenueBeneficiary.COOPERATIVE,
                    basisPoints = BasisPoints.of(100),
                    ownerId = "coop-1",
                ),
            ),
        )

        val journal = RideLedgerJournalFactory.captureCommission(
            transactionId = "tx-capture-1",
            idempotencyKey = key("trip-1:commission:capture"),
            tripId = RideId.of("trip-1"),
            driverId = "driver-1",
            commission = RideMoney.of(230, "CRC"),
            splitRules = rules,
            createdAtEpochMs = 2_000,
        )

        assertTrue(journal.isBalanced)
        assertEquals(230, journal.creditTotal.minorUnits)
        assertEquals(
            mapOf(
                RideLedgerAccountKind.PLATFORM_COMMISSION_REVENUE to 138L,
                RideLedgerAccountKind.TENANT_REVENUE_SHARE to 46L,
                RideLedgerAccountKind.COOPERATIVE_REVENUE_SHARE to 46L,
            ),
            journal.postings
                .filter { it.direction == RidePostingDirection.CREDIT }
                .associate { it.account.kind to it.amount.minorUnits },
        )
    }

    @Test
    fun `split allocation always preserves the full commission after rounding`() {
        val rules = RevenueSplitRuleSet(
            tenantId = "tenant-cr-1",
            jurisdiction = "CR",
            contractVersion = "contract-v1",
            effectiveFromEpochMs = 0,
            rules = listOf(
                RevenueSplitRule(
                    RevenueBeneficiary.PLATFORM,
                    BasisPoints.of(167),
                ),
                RevenueSplitRule(
                    RevenueBeneficiary.TENANT,
                    BasisPoints.of(167),
                    "tenant-cr-1",
                ),
                RevenueSplitRule(
                    RevenueBeneficiary.COOPERATIVE,
                    BasisPoints.of(166),
                    "coop-1",
                ),
            ),
        )

        val allocations = rules.allocate(RideMoney.of(7, "CRC"))

        assertEquals(7, allocations.sumOf { it.amount.minorUnits })
        assertEquals(500, allocations.sumOf { it.basisPoints.value })
    }

    @Test
    fun `split rules reject totals below or above five hundred basis points`() {
        listOf(499, 501).forEach { total ->
            assertThrows(IllegalArgumentException::class.java) {
                RevenueSplitRuleSet(
                    tenantId = "tenant-cr-1",
                    jurisdiction = "CR",
                    contractVersion = "contract-v1",
                    effectiveFromEpochMs = 0,
                    rules = listOf(
                        RevenueSplitRule(
                            RevenueBeneficiary.PLATFORM,
                            BasisPoints.of(total),
                        ),
                    ),
                )
            }
        }
    }

    @Test
    fun `reversal mirrors every posting and references immutable original`() {
        val original = RideLedgerJournalFactory.reserveCommission(
            transactionId = "tx-reserve-1",
            idempotencyKey = key("trip-1:commission:reserve"),
            tripId = RideId.of("trip-1"),
            driverId = "driver-1",
            amount = RideMoney.of(230, "CRC"),
            createdAtEpochMs = 1_000,
        )

        val reversal = RideLedgerJournalFactory.reverse(
            original = original,
            transactionId = "tx-reserve-reversal-1",
            idempotencyKey = key("trip-1:commission:reverse"),
            createdAtEpochMs = 2_000,
        )

        assertTrue(reversal.isBalanced)
        assertEquals(original.transactionId, reversal.reversalOf)
        original.postings.zip(reversal.postings).forEach { (before, after) ->
            assertEquals(before.account, after.account)
            assertEquals(before.amount, after.amount)
            assertEquals(before.direction.opposite(), after.direction)
        }
    }

    @Test
    fun `journal rejects imbalance mixed currencies and conflicting idempotency`() {
        assertThrows(IllegalArgumentException::class.java) {
            RideLedgerJournal(
                transactionId = "tx-invalid",
                idempotencyKey = key("trip-1:invalid:journal"),
                eventType = RideLedgerEventType.COMMISSION_CAPTURED,
                tripId = RideId.of("trip-1"),
                createdAtEpochMs = 1_000,
                postings = listOf(
                    posting("one", RidePostingDirection.DEBIT, 100, "CRC"),
                    posting("two", RidePostingDirection.CREDIT, 99, "CRC"),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RideLedgerJournal(
                transactionId = "tx-mixed",
                idempotencyKey = key("trip-1:mixed:currency"),
                eventType = RideLedgerEventType.COMMISSION_CAPTURED,
                tripId = RideId.of("trip-1"),
                createdAtEpochMs = 1_000,
                postings = listOf(
                    posting("one", RidePostingDirection.DEBIT, 100, "CRC"),
                    posting("two", RidePostingDirection.CREDIT, 100, "USD"),
                ),
            )
        }

        val first = RideLedgerJournalFactory.reserveCommission(
            transactionId = "tx-first",
            idempotencyKey = key("trip-1:same:key:0001"),
            tripId = RideId.of("trip-1"),
            driverId = "driver-1",
            amount = RideMoney.of(230, "CRC"),
            createdAtEpochMs = 1_000,
        )
        val conflict = first.copy(transactionId = "tx-conflict")

        assertThrows(IllegalArgumentException::class.java) {
            RideJournalBook.deduplicate(listOf(first, conflict))
        }
    }

    private fun key(value: String): RideIdempotencyKey =
        RideIdempotencyKey.of(value)

    private fun posting(
        id: String,
        direction: RidePostingDirection,
        amount: Long,
        currency: String,
    ) = RideLedgerPosting(
        entryId = id,
        account = RideLedgerAccount(
            kind = RideLedgerAccountKind.PLATFORM_COMMISSION_REVENUE,
        ),
        direction = direction,
        amount = RideMoney.of(amount, currency),
    )
}
