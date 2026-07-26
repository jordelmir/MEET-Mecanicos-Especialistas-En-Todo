package com.elysium369.meet.ride.wallet

import com.elysium369.meet.ride.domain.CostaRicaRidePolicy
import com.elysium369.meet.ride.domain.CurrencyCode
import com.elysium369.meet.ride.domain.RideMoney

enum class RideLedgerEntryType {
    PROMOTIONAL_GRANT,
    TOP_UP_PENDING,
    TOP_UP_CONFIRMED,
    COMMISSION_RESERVED,
    COMMISSION_CAPTURED,
    COMMISSION_RELEASED,
    REFUND,
    ADJUSTMENT,
}

enum class RideLedgerDirection {
    CREDIT,
    DEBIT,
}

data class RideLedgerEntry(
    val id: String,
    val idempotencyKey: String,
    val type: RideLedgerEntryType,
    val amount: RideMoney,
    val tripId: String?,
    val createdAtEpochMs: Long,
    val direction: RideLedgerDirection = RideLedgerDirection.CREDIT,
    val withdrawable: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "Ledger entry ID is required" }
        require(idempotencyKey.isNotBlank()) { "Idempotency key is required" }
        require(createdAtEpochMs >= 0) { "Ledger timestamp cannot be negative" }
        if (
            type in setOf(
                RideLedgerEntryType.COMMISSION_RESERVED,
                RideLedgerEntryType.COMMISSION_CAPTURED,
                RideLedgerEntryType.COMMISSION_RELEASED,
            )
        ) {
            require(!tripId.isNullOrBlank()) { "Commission entries require a trip ID" }
            require(amount.minorUnits > 0) { "Commission amount must be positive" }
        }
    }
}

data class RideWalletProjection(
    val posted: RideMoney,
    val reserved: RideMoney,
    val available: RideMoney,
)

object RidePromotionalGrant {
    private const val COSTA_RICA_PILOT_ID = "cr-pilot-2026"

    fun costaRicaPilot(
        driverId: String,
        createdAtEpochMs: Long,
    ): RideLedgerEntry {
        require(driverId.isNotBlank()) { "Driver ID is required" }
        return RideLedgerEntry(
            id = "grant:$COSTA_RICA_PILOT_ID:$driverId",
            idempotencyKey = "promo:$COSTA_RICA_PILOT_ID:$driverId",
            type = RideLedgerEntryType.PROMOTIONAL_GRANT,
            amount = CostaRicaRidePolicy.promotionalGrant,
            tripId = null,
            createdAtEpochMs = createdAtEpochMs,
            direction = RideLedgerDirection.CREDIT,
            withdrawable = false,
        )
    }
}

object RideWalletLedger {
    fun project(entries: List<RideLedgerEntry>): RideWalletProjection {
        require(entries.isNotEmpty()) { "At least one ledger entry is required" }
        val currency = entries.first().amount.currency
        val uniqueEntries = removeExactRetries(entries)
        var postedMinor = 0L
        var reservedMinor = 0L
        val activeReservations = mutableMapOf<String, Long>()

        uniqueEntries.forEach { entry ->
                require(entry.amount.currency == currency) {
                    "Wallet entries must use one currency"
                }
                when (entry.type) {
                    RideLedgerEntryType.PROMOTIONAL_GRANT,
                    RideLedgerEntryType.TOP_UP_CONFIRMED,
                    RideLedgerEntryType.REFUND,
                    -> {
                        postedMinor = applyDirection(
                            balance = postedMinor,
                            entry = entry,
                        )
                    }

                    RideLedgerEntryType.TOP_UP_PENDING -> Unit

                    RideLedgerEntryType.COMMISSION_RESERVED -> {
                        val tripId = requireNotNull(entry.tripId)
                        require(tripId !in activeReservations) {
                            "Trip already has an active commission reservation"
                        }
                        val availableMinor = postedMinor - reservedMinor
                        require(availableMinor >= entry.amount.minorUnits) {
                            "Insufficient available driver balance"
                        }
                        reservedMinor = Math.addExact(reservedMinor, entry.amount.minorUnits)
                        activeReservations[tripId] = entry.amount.minorUnits
                    }

                    RideLedgerEntryType.COMMISSION_CAPTURED -> {
                        val tripId = requireNotNull(entry.tripId)
                        consumeReservation(activeReservations, tripId, entry.amount.minorUnits)
                        reservedMinor -= entry.amount.minorUnits
                        require(postedMinor >= entry.amount.minorUnits) {
                            "Captured commission exceeds posted balance"
                        }
                        postedMinor -= entry.amount.minorUnits
                    }

                    RideLedgerEntryType.COMMISSION_RELEASED -> {
                        val tripId = requireNotNull(entry.tripId)
                        consumeReservation(activeReservations, tripId, entry.amount.minorUnits)
                        reservedMinor -= entry.amount.minorUnits
                    }

                    RideLedgerEntryType.ADJUSTMENT -> {
                        postedMinor = applyDirection(
                            balance = postedMinor,
                            entry = entry,
                        )
                    }
                }
            }

        val posted = money(postedMinor, currency)
        val reserved = money(reservedMinor, currency)
        return RideWalletProjection(
            posted = posted,
            reserved = reserved,
            available = posted - reserved,
        )
    }

    private fun removeExactRetries(entries: List<RideLedgerEntry>): List<RideLedgerEntry> {
        val byKey = linkedMapOf<String, RideLedgerEntry>()
        entries.forEach { entry ->
            val previous = byKey[entry.idempotencyKey]
            require(previous == null || previous == entry) {
                "Conflicting ledger entries share idempotency key ${entry.idempotencyKey}"
            }
            if (previous == null) {
                byKey[entry.idempotencyKey] = entry
            }
        }
        return byKey.values.toList()
    }

    private fun applyDirection(
        balance: Long,
        entry: RideLedgerEntry,
    ): Long =
        when (entry.direction) {
            RideLedgerDirection.CREDIT -> Math.addExact(balance, entry.amount.minorUnits)
            RideLedgerDirection.DEBIT -> {
                require(balance >= entry.amount.minorUnits) {
                    "Ledger debit exceeds posted balance"
                }
                balance - entry.amount.minorUnits
            }
        }

    private fun consumeReservation(
        reservations: MutableMap<String, Long>,
        tripId: String,
        amountMinor: Long,
    ) {
        val reservedForTrip = reservations[tripId]
            ?: throw IllegalArgumentException("Trip has no active commission reservation")
        require(reservedForTrip == amountMinor) {
            "Commission settlement must match its reservation"
        }
        reservations.remove(tripId)
    }

    private fun money(minorUnits: Long, currency: CurrencyCode): RideMoney =
        RideMoney(minorUnits = minorUnits, currency = currency)
}
