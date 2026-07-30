package com.elysium369.meet.ride.wallet

import com.elysium369.meet.ride.domain.BasisPoints
import com.elysium369.meet.ride.domain.CurrencyCode
import com.elysium369.meet.ride.domain.RideCommissionPolicy
import com.elysium369.meet.ride.domain.RideId
import com.elysium369.meet.ride.domain.RideIdempotencyKey
import com.elysium369.meet.ride.domain.RideMoney

enum class RideLedgerAccountKind {
    DRIVER_AVAILABLE,
    DRIVER_RESERVED,
    DRIVER_RECEIVABLE,
    PLATFORM_COMMISSION_REVENUE,
    PLATFORM_PROMOTION_EXPENSE,
    TENANT_REVENUE_SHARE,
    COOPERATIVE_REVENUE_SHARE,
    REFERRAL_PARTNER_REVENUE,
    PROMOTION_POOL,
    PAYMENT_CLEARING,
    REFUND_CLEARING,
    DISPUTE_HOLD,
    PROCESSOR_FEE_EXPENSE,
}

data class RideLedgerAccount(
    val kind: RideLedgerAccountKind,
    val ownerId: String? = null,
) {
    init {
        if (
            kind in setOf(
                RideLedgerAccountKind.DRIVER_AVAILABLE,
                RideLedgerAccountKind.DRIVER_RESERVED,
                RideLedgerAccountKind.DRIVER_RECEIVABLE,
                RideLedgerAccountKind.TENANT_REVENUE_SHARE,
                RideLedgerAccountKind.COOPERATIVE_REVENUE_SHARE,
                RideLedgerAccountKind.REFERRAL_PARTNER_REVENUE,
            )
        ) {
            require(!ownerId.isNullOrBlank()) {
                "$kind requires an account owner"
            }
        }
    }
}

enum class RidePostingDirection {
    DEBIT,
    CREDIT,
    ;

    fun opposite(): RidePostingDirection =
        when (this) {
            DEBIT -> CREDIT
            CREDIT -> DEBIT
        }
}

data class RideLedgerPosting(
    val entryId: String,
    val account: RideLedgerAccount,
    val direction: RidePostingDirection,
    val amount: RideMoney,
) {
    init {
        require(entryId.isNotBlank()) { "Ledger entry ID is required" }
        require(amount.minorUnits > 0) { "Ledger posting amount must be positive" }
    }
}

enum class RideLedgerEventType {
    PROMOTIONAL_GRANT,
    COMMISSION_RESERVED,
    COMMISSION_RELEASED,
    COMMISSION_CAPTURED,
    REFUND,
    DISPUTE_HOLD,
    REVERSAL,
}

data class RideLedgerJournal(
    val transactionId: String,
    val idempotencyKey: RideIdempotencyKey,
    val eventType: RideLedgerEventType,
    val tripId: RideId?,
    val createdAtEpochMs: Long,
    val postings: List<RideLedgerPosting>,
    val reversalOf: String? = null,
) {
    val currency: CurrencyCode = postings.firstOrNull()?.amount?.currency
        ?: throw IllegalArgumentException("Journal requires postings")
    val debitTotal: RideMoney = totalFor(RidePostingDirection.DEBIT)
    val creditTotal: RideMoney = totalFor(RidePostingDirection.CREDIT)
    val isBalanced: Boolean = debitTotal == creditTotal

    init {
        require(transactionId.isNotBlank()) { "Transaction ID is required" }
        require(createdAtEpochMs >= 0) { "Journal timestamp cannot be negative" }
        require(postings.size >= 2) { "Journal requires at least two postings" }
        require(postings.map(RideLedgerPosting::entryId).distinct().size == postings.size) {
            "Journal entry IDs must be unique"
        }
        require(postings.all { it.amount.currency == currency }) {
            "Every posting in a journal must use one currency"
        }
        require(isBalanced) { "Ledger journal debits and credits must balance" }
        require(reversalOf == null || reversalOf.isNotBlank()) {
            "Reversal reference cannot be blank"
        }
        require(reversalOf != transactionId) {
            "A transaction cannot reverse itself"
        }
    }

    private fun totalFor(direction: RidePostingDirection): RideMoney {
        val total = postings
            .asSequence()
            .filter { it.direction == direction }
            .fold(0L) { sum, posting ->
                Math.addExact(sum, posting.amount.minorUnits)
            }
        return RideMoney(total, currency)
    }
}

enum class RevenueBeneficiary {
    PLATFORM,
    TENANT,
    COOPERATIVE,
    REFERRAL_PARTNER,
    PROMOTION_POOL,
}

data class RevenueSplitRule(
    val beneficiary: RevenueBeneficiary,
    val basisPoints: BasisPoints,
    val ownerId: String? = null,
) {
    init {
        require(basisPoints.value > 0) { "Split basis points must be positive" }
        if (
            beneficiary in setOf(
                RevenueBeneficiary.TENANT,
                RevenueBeneficiary.COOPERATIVE,
                RevenueBeneficiary.REFERRAL_PARTNER,
            )
        ) {
            require(!ownerId.isNullOrBlank()) {
                "$beneficiary split requires an owner"
            }
        }
    }
}

data class RevenueSplitAllocation(
    val beneficiary: RevenueBeneficiary,
    val ownerId: String?,
    val basisPoints: BasisPoints,
    val amount: RideMoney,
) {
    fun ledgerAccount(): RideLedgerAccount =
        when (beneficiary) {
            RevenueBeneficiary.PLATFORM -> RideLedgerAccount(
                RideLedgerAccountKind.PLATFORM_COMMISSION_REVENUE,
            )
            RevenueBeneficiary.TENANT -> RideLedgerAccount(
                RideLedgerAccountKind.TENANT_REVENUE_SHARE,
                ownerId,
            )
            RevenueBeneficiary.COOPERATIVE -> RideLedgerAccount(
                RideLedgerAccountKind.COOPERATIVE_REVENUE_SHARE,
                ownerId,
            )
            RevenueBeneficiary.REFERRAL_PARTNER -> RideLedgerAccount(
                RideLedgerAccountKind.REFERRAL_PARTNER_REVENUE,
                ownerId,
            )
            RevenueBeneficiary.PROMOTION_POOL -> RideLedgerAccount(
                RideLedgerAccountKind.PROMOTION_POOL,
                ownerId,
            )
        }
}

data class RevenueSplitRuleSet(
    val tenantId: String,
    val jurisdiction: String,
    val contractVersion: String,
    val effectiveFromEpochMs: Long,
    val effectiveToEpochMs: Long? = null,
    val rules: List<RevenueSplitRule>,
) {
    init {
        require(tenantId.isNotBlank()) { "Tenant ID is required" }
        require(jurisdiction.matches(Regex("[A-Z]{2}"))) {
            "Jurisdiction must be an uppercase ISO country code"
        }
        require(contractVersion.isNotBlank()) { "Contract version is required" }
        require(effectiveFromEpochMs >= 0) { "Effective from cannot be negative" }
        require(effectiveToEpochMs == null || effectiveToEpochMs > effectiveFromEpochMs) {
            "Effective to must be after effective from"
        }
        require(rules.isNotEmpty()) { "At least one revenue split is required" }
        require(
            rules.sumOf { it.basisPoints.value } ==
                RideCommissionPolicy.PLATFORM_RATE_BASIS_POINTS,
        ) {
            "Revenue splits must total exactly " +
                "${RideCommissionPolicy.PLATFORM_RATE_BASIS_POINTS} basis points"
        }
        require(
            rules.map { it.beneficiary to it.ownerId }.distinct().size == rules.size,
        ) {
            "A beneficiary account can appear only once"
        }
    }

    fun allocate(commission: RideMoney): List<RevenueSplitAllocation> {
        if (commission.minorUnits == 0L) {
            return rules.map { rule ->
                RevenueSplitAllocation(
                    beneficiary = rule.beneficiary,
                    ownerId = rule.ownerId,
                    basisPoints = rule.basisPoints,
                    amount = RideMoney.zero(commission.currency),
                )
            }
        }

        val denominator = RideCommissionPolicy.PLATFORM_RATE_BASIS_POINTS.toLong()
        val whole = commission.minorUnits / denominator
        val remainder = commission.minorUnits % denominator
        val floors = rules.map { rule ->
            val wholeShare = Math.multiplyExact(
                whole,
                rule.basisPoints.value.toLong(),
            )
            val remainderProduct = remainder * rule.basisPoints.value.toLong()
            SplitFloor(
                amountMinor = Math.addExact(
                    wholeShare,
                    remainderProduct / denominator,
                ),
                remainder = remainderProduct % denominator,
            )
        }.toMutableList()

        var undistributed = commission.minorUnits -
            floors.sumOf(SplitFloor::amountMinor)
        val priority = rules.indices.sortedWith(
            compareByDescending<Int> { floors[it].remainder }
                .thenBy { rules[it].beneficiary.name }
                .thenBy { rules[it].ownerId.orEmpty() },
        )
        var priorityIndex = 0
        while (undistributed > 0) {
            val index = priority[priorityIndex]
            floors[index] = floors[index].copy(
                amountMinor = Math.addExact(floors[index].amountMinor, 1),
            )
            undistributed--
            priorityIndex = (priorityIndex + 1) % priority.size
        }

        return rules.mapIndexed { index, rule ->
            RevenueSplitAllocation(
                beneficiary = rule.beneficiary,
                ownerId = rule.ownerId,
                basisPoints = rule.basisPoints,
                amount = RideMoney(
                    minorUnits = floors[index].amountMinor,
                    currency = commission.currency,
                ),
            )
        }
    }

    private data class SplitFloor(
        val amountMinor: Long,
        val remainder: Long,
    )
}

object RideLedgerJournalFactory {
    fun reserveCommission(
        transactionId: String,
        idempotencyKey: RideIdempotencyKey,
        tripId: RideId,
        driverId: String,
        amount: RideMoney,
        createdAtEpochMs: Long,
    ): RideLedgerJournal {
        require(amount.minorUnits > 0) { "Reserved commission must be positive" }
        return journal(
            transactionId = transactionId,
            idempotencyKey = idempotencyKey,
            eventType = RideLedgerEventType.COMMISSION_RESERVED,
            tripId = tripId,
            createdAtEpochMs = createdAtEpochMs,
            postings = listOf(
                posting(
                    transactionId,
                    0,
                    RideLedgerAccount(
                        RideLedgerAccountKind.DRIVER_AVAILABLE,
                        driverId,
                    ),
                    RidePostingDirection.DEBIT,
                    amount,
                ),
                posting(
                    transactionId,
                    1,
                    RideLedgerAccount(
                        RideLedgerAccountKind.DRIVER_RESERVED,
                        driverId,
                    ),
                    RidePostingDirection.CREDIT,
                    amount,
                ),
            ),
        )
    }

    fun releaseCommission(
        transactionId: String,
        idempotencyKey: RideIdempotencyKey,
        tripId: RideId,
        driverId: String,
        amount: RideMoney,
        createdAtEpochMs: Long,
    ): RideLedgerJournal {
        require(amount.minorUnits > 0) { "Released commission must be positive" }
        return journal(
            transactionId = transactionId,
            idempotencyKey = idempotencyKey,
            eventType = RideLedgerEventType.COMMISSION_RELEASED,
            tripId = tripId,
            createdAtEpochMs = createdAtEpochMs,
            postings = listOf(
                posting(
                    transactionId,
                    0,
                    RideLedgerAccount(
                        RideLedgerAccountKind.DRIVER_RESERVED,
                        driverId,
                    ),
                    RidePostingDirection.DEBIT,
                    amount,
                ),
                posting(
                    transactionId,
                    1,
                    RideLedgerAccount(
                        RideLedgerAccountKind.DRIVER_AVAILABLE,
                        driverId,
                    ),
                    RidePostingDirection.CREDIT,
                    amount,
                ),
            ),
        )
    }

    fun captureCommission(
        transactionId: String,
        idempotencyKey: RideIdempotencyKey,
        tripId: RideId,
        driverId: String,
        commission: RideMoney,
        splitRules: RevenueSplitRuleSet,
        createdAtEpochMs: Long,
    ): RideLedgerJournal {
        require(commission.minorUnits > 0) { "Captured commission must be positive" }
        val allocations = splitRules.allocate(commission)
            .filter { it.amount.minorUnits > 0 }
        return journal(
            transactionId = transactionId,
            idempotencyKey = idempotencyKey,
            eventType = RideLedgerEventType.COMMISSION_CAPTURED,
            tripId = tripId,
            createdAtEpochMs = createdAtEpochMs,
            postings = buildList {
                add(
                    posting(
                        transactionId,
                        0,
                        RideLedgerAccount(
                            RideLedgerAccountKind.DRIVER_RESERVED,
                            driverId,
                        ),
                        RidePostingDirection.DEBIT,
                        commission,
                    ),
                )
                allocations.forEachIndexed { index, allocation ->
                    add(
                        posting(
                            transactionId,
                            index + 1,
                            allocation.ledgerAccount(),
                            RidePostingDirection.CREDIT,
                            allocation.amount,
                        ),
                    )
                }
            },
        )
    }

    fun reverse(
        original: RideLedgerJournal,
        transactionId: String,
        idempotencyKey: RideIdempotencyKey,
        createdAtEpochMs: Long,
    ): RideLedgerJournal =
        RideLedgerJournal(
            transactionId = transactionId,
            idempotencyKey = idempotencyKey,
            eventType = RideLedgerEventType.REVERSAL,
            tripId = original.tripId,
            createdAtEpochMs = createdAtEpochMs,
            postings = original.postings.mapIndexed { index, entry ->
                entry.copy(
                    entryId = "$transactionId:$index",
                    direction = entry.direction.opposite(),
                )
            },
            reversalOf = original.transactionId,
        )

    private fun journal(
        transactionId: String,
        idempotencyKey: RideIdempotencyKey,
        eventType: RideLedgerEventType,
        tripId: RideId,
        createdAtEpochMs: Long,
        postings: List<RideLedgerPosting>,
    ) = RideLedgerJournal(
        transactionId = transactionId,
        idempotencyKey = idempotencyKey,
        eventType = eventType,
        tripId = tripId,
        createdAtEpochMs = createdAtEpochMs,
        postings = postings,
    )

    private fun posting(
        transactionId: String,
        index: Int,
        account: RideLedgerAccount,
        direction: RidePostingDirection,
        amount: RideMoney,
    ) = RideLedgerPosting(
        entryId = "$transactionId:$index",
        account = account,
        direction = direction,
        amount = amount,
    )
}

object RideJournalBook {
    fun deduplicate(journals: List<RideLedgerJournal>): List<RideLedgerJournal> {
        val byKey = linkedMapOf<RideIdempotencyKey, RideLedgerJournal>()
        journals.forEach { journal ->
            val previous = byKey[journal.idempotencyKey]
            require(previous == null || previous == journal) {
                "Conflicting journals share idempotency key " +
                    journal.idempotencyKey.value
            }
            if (previous == null) {
                byKey[journal.idempotencyKey] = journal
            }
        }
        return byKey.values.toList()
    }
}
