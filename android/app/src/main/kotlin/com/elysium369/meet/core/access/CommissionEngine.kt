package com.elysium369.meet.core.access

/** Contract-bound commission calculation for marketplace transactions. */

enum class TransactionKind {
    REPAIR_SERVICE,
    TOW_SERVICE,
    PART_SALE,
    INSPECTION,
    CONSULTING,
    OTHER
}

data class CommissionDecision(
    val transactionKind: TransactionKind,
    val grossCents: Long,
    val commissionCents: Long,
    val netCents: Long,
    val platformFeeBps: Int = 0,
    val notes: String = "",
    val rateBps: Int = platformFeeBps,
    val policyCode: String = "DEFAULT",
    val platformCommissionCents: Long = commissionCents,
    val providerPayoutCents: Long = netCents,
    val authorized: Boolean = false,
)

object CommissionEngine {
    /**
     * A marketplace commission exists only when a versioned accepted contract
     * supplies both `contractRateBps` and `contractId`. Missing policy data is
     * returned as unavailable instead of inventing a platform percentage.
     */
    fun decide(
        transactionKind: TransactionKind,
        grossCents: Long,
        overrides: Map<String, Any> = emptyMap()
    ): CommissionDecision {
        require(grossCents >= 0L) { "grossCents cannot be negative" }
        val bps = (overrides["contractRateBps"] as? Number)?.toInt()
        val contractId = overrides["contractId"] as? String
        if (bps == null || contractId.isNullOrBlank()) {
            return CommissionDecision(
                transactionKind = transactionKind,
                grossCents = grossCents,
                commissionCents = 0L,
                netCents = grossCents,
                platformFeeBps = 0,
                notes = "No existe contrato de comisión aceptado y versionado.",
                rateBps = 0,
                policyCode = "POLICY_UNAVAILABLE",
                platformCommissionCents = 0L,
                providerPayoutCents = grossCents,
                authorized = false,
            )
        }
        require(bps in 0..10_000) { "contractRateBps must be between 0 and 10000" }
        val commissionCents = multiplyBasisPointsHalfUp(grossCents, bps)
        return CommissionDecision(
            transactionKind = transactionKind,
            grossCents = grossCents,
            commissionCents = commissionCents,
            netCents = grossCents - commissionCents,
            platformFeeBps = bps,
            notes = "Contrato de comisión explícito: $contractId",
            rateBps = bps,
            policyCode = contractId,
            platformCommissionCents = commissionCents,
            providerPayoutCents = grossCents - commissionCents,
            authorized = true,
        )
    }

    private fun multiplyBasisPointsHalfUp(amount: Long, basisPoints: Int): Long {
        val product = Math.multiplyExact(amount, basisPoints.toLong())
        return Math.addExact(product, 5_000L) / 10_000L
    }
}
