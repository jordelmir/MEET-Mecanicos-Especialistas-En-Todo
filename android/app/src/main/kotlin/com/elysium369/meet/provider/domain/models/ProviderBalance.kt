package com.elysium369.meet.provider.domain.models

import java.time.Instant

data class ProviderBalance(
    val currencyCode: String,
    val withdrawableMinor: Long,
    val pendingMinor: Long,
    val lockedMinor: Long,
    val lastPayoutMinor: Long,
    val lastPayoutAt: Instant?,
    val totalSettledMinor: Long,
) {
    init {
        require(currencyCode.matches(Regex("^[A-Z]{3}$"))) { "Currency code must be 3 uppercase letters, got $currencyCode" }
        require(withdrawableMinor >= 0) { "Withdrawable minor must be non-negative" }
        require(pendingMinor >= 0) { "Pending minor must be non-negative" }
        require(lockedMinor >= 0) { "Locked minor must be non-negative" }
        require(lastPayoutMinor >= 0) { "Last payout minor must be non-negative" }
        require(totalSettledMinor >= 0) { "Total settled minor must be non-negative" }
    }
}
