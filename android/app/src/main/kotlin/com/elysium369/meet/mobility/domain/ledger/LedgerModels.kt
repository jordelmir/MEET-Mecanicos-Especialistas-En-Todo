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
        require(entries.isNotEmpty()) { "LedgerTransaction requires at least two entries" }
        val sum = entries.sumOf { it.amount.minorUnits }
        require(sum == 0L) { "Double-entry ledger must be balanced: sum of amounts must be 0, got $sum" }
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
        require(grossFare.currency == platformFee.currency) { "Currency mismatch in settlement" }
        require(grossFare.currency == driverEarnings.currency) { "Currency mismatch in settlement" }
        // grossFare must equal platformFee + driverEarnings + tax + toll
        val calculatedGross = platformFee.minorUnits + driverEarnings.minorUnits + tax.minorUnits + toll.minorUnits
        require(grossFare.minorUnits == calculatedGross) {
            "Gross fare (${grossFare.minorUnits}) must equal sum of components ($calculatedGross)"
        }
    }
}
