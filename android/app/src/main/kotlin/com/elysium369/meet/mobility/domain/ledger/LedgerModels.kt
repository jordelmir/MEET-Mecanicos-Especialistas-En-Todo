package com.elysium369.meet.mobility.domain.ledger

import com.elysium369.meet.mobility.domain.models.CurrencyCode
import com.elysium369.meet.mobility.domain.models.Money
import com.elysium369.meet.mobility.domain.models.SignedMoney
import java.time.Instant
import java.util.UUID

enum class LedgerAccountType {
    RIDER_RECEIVABLE,
    DRIVER_PAYABLE,
    PLATFORM_REVENUE,
    TAX_ESCROW,
    TOLL_ESCROW,
}

enum class LedgerReferenceType {
    TRIP_SETTLEMENT,
    PAYMENT_CAPTURE,
    PAYMENT_REFUND,
    DISPUTE_ADJUSTMENT,
}

data class LedgerEntry(
    val entryId: UUID,
    val accountId: UUID,
    val amount: SignedMoney,
)

data class LedgerTransaction(
    val transactionId: UUID,
    val referenceType: LedgerReferenceType,
    val referenceId: UUID,
    val currency: CurrencyCode,
    val entries: List<LedgerEntry>,
    val createdAt: Instant,
) {
    init {
        require(entries.size >= 2) {
            "Double-entry transaction requires >= 2 entries"
        }

        require(entries.all { it.amount.currency == currency }) {
            "Every ledger entry must use transaction currency=$currency"
        }

        val balance = entries.fold(0L) { accumulator, entry ->
            Math.addExact(
                accumulator,
                entry.amount.minorUnits,
            )
        }

        require(balance == 0L) {
            "Unbalanced ledger transaction: balance=$balance"
        }

        require(entries.any { it.amount.minorUnits > 0L }) {
            "Ledger requires at least one debit"
        }

        require(entries.any { it.amount.minorUnits < 0L }) {
            "Ledger requires at least one credit"
        }
    }
}

data class TripSettlement(
    val settlementId: UUID,
    val tripId: UUID,
    val grossFare: Money,
    val platformFee: Money,
    val driverEarnings: Money,
    val tax: Money,
    val toll: Money,
    val pricingPolicyVersion: Long,
    val ledgerTransactionId: UUID,
    val createdAt: Instant,
) {
    init {
        val expectedCurrency = grossFare.currency

        require(
            listOf(
                platformFee,
                driverEarnings,
                tax,
                toll,
            ).all { it.currency == expectedCurrency }
        ) {
            "Settlement currency mismatch"
        }

        val components = listOf(
            platformFee.minorUnits,
            driverEarnings.minorUnits,
            tax.minorUnits,
            toll.minorUnits,
        )

        val calculated = components.fold(0L, Math::addExact)

        require(calculated == grossFare.minorUnits) {
            "Settlement invariant violated: gross=${grossFare.minorUnits}, components=$calculated"
        }
    }
}
